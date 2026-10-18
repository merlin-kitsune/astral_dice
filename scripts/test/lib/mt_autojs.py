#!/usr/bin/env python3
"""mt_autojs.py — AutoJS 脚本上下文 MCP 客户端（WSL/bash 工具链）。

与 scripts/test/lib/Mt.AutoJs.psm1 **语义等价**的 python3 标准库实现（无第三方依赖）：
  · 传输：HTTP POST <endpoint>，JSON-RPC 2.0；Accept 同时接受 application/json 与 text/event-stream
  · 握手：initialize → notifications/initialized → tools/call；会话 id 由响应头 Mcp-Session-Id 回带
  · 工具面：status / eval_js / kubejs_log / reload_scripts（side ∈ auto|server|client）
  · 错误归一：result.isError 或 payload.ok=false → {ok:false,error,stack,diagnostics}
  · 会话失效（HTTP 4xx）自动重握手重试一次
  · **端点没起来 ≠ 断言失败**：调用方据 probe() 记 BLOCKED

求值语义（实测坑，勿改）：
  · eval_js 只求值最后一个表达式；顶层 return 抛 invalid return ⇒ code 必须是 IIFE 表达式
  · 非标量返回值被序列化成 "[object Object]" ⇒ mode='json' 自动包 JSON.stringify(...)
  · 布尔用 mode='bool' 包 !!(...)，否则字符串 "false" 会被判真值
  · 代理：强制 no-proxy（系统代理会把 127.0.0.1 变成 502，假阴性极难查）

CLI（便于手工排查）：
  python3 scripts/test/lib/mt_autojs.py probe
  python3 scripts/test/lib/mt_autojs.py eval --side server --code "1+1"
"""
from __future__ import annotations

import argparse
import json
import os
import socket
import time
import urllib.error
import urllib.parse
import urllib.request

DEFAULT_ENDPOINT = "http://127.0.0.1:25585/mcp"
DEFAULT_TIMEOUT_MS = 15000
DEFAULT_POLL_MS = 500
PROTOCOL_VERSION = "2025-06-18"
CLIENT_NAME = "mt-autojs-python"
TOOLS = ("status", "eval_js", "kubejs_log", "reload_scripts")


def _read_conf(path: str) -> dict:
    conf = {}
    try:
        with open(path, "rb") as fh:
            raw = fh.read()
    except OSError:
        return conf
    text = raw.decode("utf-8-sig", "replace")
    for line in text.splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, _, val = line.partition("=")
        val = val.strip()
        if len(val) >= 2 and val[0] == val[-1] and val[0] in "\"'":
            val = val[1:-1]
        conf[key.strip()] = val
    return conf


def _looks_json(text: str) -> bool:
    t = text.strip()
    if not t:
        return False
    if (t.startswith("{") and t.endswith("}")) or (t.startswith("[") and t.endswith("]")):
        return True
    if t in ("true", "false", "null"):
        return True
    try:
        float(t)
        return True
    except ValueError:
        return False


class AutoJs:
    def __init__(self, endpoint: str | None = None, timeout_ms: int | None = None,
                 token: str | None = None, conf_path: str | None = None):
        conf = _read_conf(conf_path) if conf_path else {}
        self.endpoint = (endpoint or conf.get("MT_AUTOJS_ENDPOINT") or DEFAULT_ENDPOINT).strip()
        self.timeout_ms = int(timeout_ms or conf.get("MT_AUTOJS_TIMEOUT_MS") or DEFAULT_TIMEOUT_MS)
        self.token = (token if token is not None else conf.get("MT_AUTOJS_TOKEN", "")).strip()
        self.session_id = ""
        self.protocol = ""
        self._opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))

    # ── transport ────────────────────────────────────────────────────────
    def _post(self, payload: dict, extra_headers: dict | None = None, timeout_ms: int | None = None):
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        headers = {
            "Content-Type": "application/json",
            "Accept": "application/json, text/event-stream",
        }
        if self.token:
            headers["Authorization"] = "Bearer " + self.token
        if extra_headers:
            headers.update(extra_headers)
        req = urllib.request.Request(self.endpoint, data=body, headers=headers, method="POST")
        sec = max(1.0, (timeout_ms or self.timeout_ms) / 1000.0)
        try:
            with self._opener.open(req, timeout=sec) as resp:
                raw = resp.read().decode("utf-8", "replace")
                return {
                    "status": resp.status,
                    "body": raw,
                    "ctype": resp.headers.get("Content-Type", ""),
                    "session": resp.headers.get("Mcp-Session-Id", "") or "",
                    "error": "",
                }
        except urllib.error.HTTPError as exc:  # 4xx/5xx 也要拿到 body
            raw = exc.read().decode("utf-8", "replace") if exc.fp else ""
            return {"status": exc.code, "body": raw,
                    "ctype": exc.headers.get("Content-Type", "") if exc.headers else "",
                    "session": (exc.headers.get("Mcp-Session-Id", "") if exc.headers else "") or "",
                    "error": ""}
        except Exception as exc:  # noqa: BLE001 — 传输层任何异常都折成 error
            return {"status": 0, "body": "", "ctype": "", "session": "", "error": str(exc)}

    @staticmethod
    def _parse_rpc(body: str, ctype: str):
        """解析 JSON-RPC 消息：JSON 直接取；SSE 取最后一条带 result/error 的 data:。"""
        is_sse = "text/event-stream" in (ctype or "") or any(
            ln.strip().startswith("data:") for ln in (body or "").splitlines())
        texts = []
        if is_sse:
            for ln in (body or "").splitlines():
                t = ln.strip()
                if t.startswith("data:"):
                    texts.append(t[5:].strip())
        elif (body or "").strip():
            texts.append(body.strip())
        msgs = []
        for t in texts:
            if not t or t == "[DONE]":
                continue
            try:
                msgs.append(json.loads(t))
            except json.JSONDecodeError:
                pass
        if not msgs:
            return None, ("sse" if is_sse else "json")
        pick = None
        for m in reversed(msgs):
            if isinstance(m, dict) and ("result" in m or "error" in m):
                pick = m
                break
        return (pick if pick is not None else msgs[-1]), ("sse" if is_sse else "json")

    @staticmethod
    def _rpc(method: str, params=None, rid: int | None = 1) -> dict:
        obj = {"jsonrpc": "2.0"}
        if rid is not None:
            obj["id"] = rid
        obj["method"] = method
        if params is not None:
            obj["params"] = params
        return obj

    def _fail(self, error: str, **extra) -> dict:
        out = {"ok": False, "error": error, "stack": extra.pop("stack", ""),
               "diagnostics": extra.pop("diagnostics", {})}
        out.update(extra)
        return out

    # ── session ──────────────────────────────────────────────────────────
    def initialize(self, force: bool = False) -> dict:
        if not force and self.session_id and self.protocol:
            return {"ok": True, "session": self.session_id, "protocol": self.protocol, "cached": True}
        params = {"protocolVersion": PROTOCOL_VERSION, "capabilities": {},
                  "clientInfo": {"name": CLIENT_NAME, "version": "1.0.0"}}
        r = self._post(self._rpc("initialize", params, 1))
        if r["error"]:
            return self._fail("initialize 失败（传输层）：" + r["error"],
                              diagnostics={"phase": "initialize", "endpoint": self.endpoint})
        if r["status"] >= 400:
            return self._fail(f"initialize 失败：HTTP {r['status']}",
                              diagnostics={"phase": "initialize", "status": r["status"]})
        msg, _ = self._parse_rpc(r["body"], r["ctype"])
        if msg is None:
            return self._fail("initialize 应答里没有 JSON-RPC 消息",
                              diagnostics={"phase": "initialize", "body": r["body"][:500]})
        if "error" in msg:
            err = msg.get("error") or {}
            return self._fail("initialize 失败：JSON-RPC 错误 " + str(err.get("message", "")),
                              diagnostics={"phase": "initialize", "error": err})
        result = msg.get("result") or {}
        self.protocol = str(result.get("protocolVersion") or PROTOCOL_VERSION)
        self.session_id = r["session"] or self.session_id
        hdrs = {}
        if self.session_id:
            hdrs["Mcp-Session-Id"] = self.session_id
        if self.protocol:
            hdrs["MCP-Protocol-Version"] = self.protocol
        n = self._post(self._rpc("notifications/initialized", None, None), hdrs)
        if n["error"]:
            return self._fail("notifications/initialized 失败（传输层）：" + n["error"],
                              diagnostics={"phase": "notifications/initialized"})
        if n["status"] >= 400:
            return self._fail(f"notifications/initialized 失败：HTTP {n['status']}",
                              diagnostics={"phase": "notifications/initialized"})
        return {"ok": True, "session": self.session_id, "protocol": self.protocol,
                "server": result.get("serverInfo"), "cached": False}

    def reset(self):
        self.session_id = ""
        self.protocol = ""

    # ── tools ────────────────────────────────────────────────────────────
    def call(self, name: str, args: dict | None = None, timeout_ms: int | None = None) -> dict:
        if name not in TOOLS:
            return self._fail(f"未知工具 {name}（只支持 {', '.join(TOOLS)}）")
        args = args or {}
        for attempt in (1, 2):
            init = self.initialize(force=(attempt > 1))
            if not init.get("ok"):
                return self._fail(init.get("error", "initialize 失败"),
                                  diagnostics=init.get("diagnostics", {}))
            hdrs = {}
            if self.session_id:
                hdrs["Mcp-Session-Id"] = self.session_id
            if self.protocol:
                hdrs["MCP-Protocol-Version"] = self.protocol
            r = self._post(self._rpc("tools/call", {"name": name, "arguments": args}, 2),
                           hdrs, timeout_ms)
            if r["error"]:
                return self._fail(f"tools/call {name} 失败（传输层）：{r['error']}",
                                  diagnostics={"phase": "tools/call", "tool": name})
            if 400 <= r["status"] < 500:
                self.reset()
                if attempt == 1:
                    continue
                return self._fail(f"tools/call {name} 失败：HTTP {r['status']}（重新握手后仍被拒）",
                                  diagnostics={"phase": "tools/call", "tool": name,
                                               "status": r["status"]})
            if r["status"] >= 500:
                return self._fail(f"tools/call {name} 失败：HTTP {r['status']}",
                                  diagnostics={"phase": "tools/call", "tool": name,
                                               "status": r["status"]})
            msg, _ = self._parse_rpc(r["body"], r["ctype"])
            return self._normalize(name, msg)
        return self._fail(f"tools/call {name} 失败：重试后仍未拿到应答")

    def _normalize(self, tool: str, msg) -> dict:
        if msg is None:
            return self._fail(f"tools/call {tool} 的应答里没有 JSON-RPC 消息",
                              diagnostics={"phase": "tools/call", "tool": tool})
        if isinstance(msg, dict) and "error" in msg:
            err = msg.get("error") or {}
            return self._fail(f"tools/call {tool} 失败：JSON-RPC 错误 {err.get('message','')}",
                              stack=str(err.get("data", "")),
                              diagnostics={"phase": "tools/call", "tool": tool, "error": err})
        if not isinstance(msg, dict) or "result" not in msg:
            return self._fail(f"tools/call {tool} 的应答缺少 result",
                              diagnostics={"phase": "tools/call", "tool": tool})
        result = msg.get("result") or {}
        is_error = bool(result.get("isError"))
        texts = [str(c.get("text")) for c in (result.get("content") or [])
                 if isinstance(c, dict) and c.get("text") is not None]
        payload = None
        for t in texts:
            if not t.strip():
                continue
            try:
                payload = json.loads(t)
                break
            except json.JSONDecodeError:
                payload = None
        if payload is None and result.get("structuredContent") is not None:
            payload = result["structuredContent"]
        if payload is None:
            text = "\n".join(texts)
            if is_error:
                return self._fail(f"tools/call {tool} 报错（isError=true，且没有 JSON 负载）",
                                  stack=text, diagnostics={"phase": "tools/call", "tool": tool})
            return {"ok": True, "tool": tool, "type": "text", "value": text,
                    "value_parsed": json.loads(text) if _looks_json(text) else None,
                    "side": "", "elapsed_ms": 0, "payload": result, "diagnostics": None}
        ok = (not is_error) and bool(payload.get("ok", True)) if isinstance(payload, dict) else (not is_error)
        if not ok:
            p = payload if isinstance(payload, dict) else {}
            return self._fail(str(p.get("error") or f"tools/call {tool} 失败"),
                              stack=str(p.get("stack", "")),
                              diagnostics=p.get("diagnostics") or {"phase": "tools/call", "tool": tool})
        p = payload if isinstance(payload, dict) else {}
        value = p.get("value")
        vtype = str(p.get("type", ""))
        parsed = None
        if isinstance(value, str) and vtype in ("", "string") and _looks_json(value):
            try:
                parsed = json.loads(value)
            except json.JSONDecodeError:
                parsed = None
        return {"ok": True, "tool": tool, "side": str(p.get("side", "")),
                "elapsed_ms": int(p.get("elapsedMs") or 0), "type": vtype,
                "value": value, "value_parsed": parsed, "payload": p,
                "diagnostics": p.get("diagnostics")}

    def eval_js(self, code: str, side: str = "auto", mode: str = "raw",
                timeout_ms: int | None = None) -> dict:
        wrapped = code
        if mode == "json":
            wrapped = f"JSON.stringify(({code}))"
        elif mode == "bool":
            wrapped = f"!!({code})"
        args = {"code": wrapped, "side": side}
        if timeout_ms:
            args["timeout_ms"] = int(timeout_ms)
        return self.call("eval_js", args, timeout_ms)

    def probe(self, timeout_ms: int | None = None, poll_ms: int = DEFAULT_POLL_MS) -> dict:
        """TCP → initialize → status 三步闸门；轮询到超时。绝不抛。"""
        budget = timeout_ms or self.timeout_ms
        deadline = time.monotonic() + budget / 1000.0
        attempts = 0
        last = {"ok": False, "reason": "未探测", "tcp": False, "init": False, "status_ok": False}
        parts = urllib.parse.urlsplit(self.endpoint)
        host = parts.hostname or "127.0.0.1"
        port = parts.port or (443 if parts.scheme == "https" else 80)
        while True:
            attempts += 1
            try:
                with socket.create_connection((host, port), timeout=2.0):
                    last["tcp"] = True
            except OSError as exc:
                last.update(tcp=False, reason=f"TCP 连接失败（{host}:{port}）：{exc}")
            else:
                init = self.initialize(force=True)
                last["init"] = bool(init.get("ok"))
                if not init.get("ok"):
                    last["reason"] = init.get("error", "")
                else:
                    st = self.call("status", {}, min(5000, max(1000, int((deadline - time.monotonic()) * 1000))))
                    last["status_ok"] = bool(st.get("ok"))
                    if st.get("ok"):
                        last.update(ok=True, reason="", status=st, protocol=self.protocol)
                        break
                    last["reason"] = st.get("error", "")
            if time.monotonic() + poll_ms / 1000.0 >= deadline:
                break
            time.sleep(poll_ms / 1000.0)
        last.update(attempts=attempts, endpoint=self.endpoint, host=host, port=port,
                    timeout_ms=budget, protocol=self.protocol)
        return last


def main():
    ap = argparse.ArgumentParser(description="AutoJS MCP 客户端（WSL）")
    sub = ap.add_subparsers(dest="cmd", required=True)
    p = sub.add_parser("probe")
    p.add_argument("--endpoint", default=None)
    p.add_argument("--timeout-ms", type=int, default=None)
    e = sub.add_parser("eval")
    e.add_argument("--side", default="auto", choices=("auto", "server", "client"))
    e.add_argument("--mode", default="raw", choices=("raw", "json", "bool"))
    e.add_argument("--code", required=True)
    e.add_argument("--endpoint", default=None)
    s = sub.add_parser("status")
    s.add_argument("--endpoint", default=None)
    sub.add_parser("tools")
    a = ap.parse_args()
    client = AutoJs(endpoint=getattr(a, "endpoint", None),
                    conf_path=os.path.join(os.path.dirname(__file__), "..", "mt.conf"))
    if a.cmd == "probe":
        print(json.dumps(client.probe(getattr(a, "timeout_ms", None)), ensure_ascii=False, indent=1))
    elif a.cmd == "eval":
        print(json.dumps(client.eval_js(a.code, a.side, a.mode), ensure_ascii=False, indent=1))
    elif a.cmd == "status":
        print(json.dumps(client.call("status", {}), ensure_ascii=False, indent=1))
    else:
        print("\n".join(TOOLS))


if __name__ == "__main__":
    main()
