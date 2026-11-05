#!/usr/bin/env python3
"""mt_channel.py — AutoJS 命令通道（bash/python 侧）。

把 pwsh 版 scripts/test/lib/Mt.Channel.psm1 的 **命令注入通道**语义等价搬到 python：

  命令通道 = 三级探测链，逐级尝试、首级成功即记住（2026-11-05 Step 1 起**客户端优先**）：
    1) send_command      side=client  (function(){ Client.getConnection().sendCommand(L); return "send_command"; })()
       —— 玩家自身聊天通道：命令以**玩家身份**在服务端执行，@s/@p 正常解析，且完全不碰键鼠/窗口焦点。
    2) autojs_command    side=server  (function(){ AutoJS.command(L); return "autojs_command"; })()
       —— 服务端 console 源(createCommandSourceStack())：@s 解析成 console、没有玩家，只能写 @a。
    3) perform_prefixed  side=server  (function(){ var s = server; var c = s.getCommands();
                                         return String(c.performPrefixedCommand(s.createCommandSourceStack(), L)); })()
       —— 与 2) 同源（同一个 command source），保留只为兜住个别 API 差异。
  其中 L 是**合法的 JS 字符串字面量**（JSON 转义规则与 JS 一致），杜绝命令拼接注入。
  链序可用环境变量 MT_CHANNEL_ORDER=client-first|server-first 覆盖，默认 client-first：
  用例里 @s 共 667 处 / 225 个用例，server-first 会让它们静默失效（console 没有 @s）。

判据（与 pwsh 逐条对齐）：
  · 空命令（Trim 后）⇒ status=ERROR / command_empty
  · run/<...>/mods 里没有 AutoJS-*.jar 且 fabric 未注入 ⇒ status=DELEGATED（通道不可用，不是失败）
  · 成功 = ok 为真 **且** diagnostics 无错误证据（ok:true 不代表没出错）
  · 三级全败：错误文本像「端点不可用」⇒ status=ERROR + endpoint=unavailable（调用方记 BLOCKED）；
    否则 status=DELEGATED（端点可达但无一级被接受）

DELEGATED 的判定语义（契约 §1.3）：**不升级用例结论**、单独计数，并在用例级别另起一行
`MT_CHANNEL: delegated_count=N` —— 绝不混进 MT_CASE_RESULT 的取值域。
"""
from __future__ import annotations

import json
import os
import re

import mt_paths

TRANSPORT_RE = re.compile(
    r"(?i)传输层|initialize 失败|HTTP \d|connect|拒绝|连不上|refused|timed out|超时")
DIAG_KEY_RE = re.compile(r"(?i)error|exception|throwable|stack|fail")
DIAG_VAL_RE = re.compile(r"(?i)exception|error|at [a-z_$][\w$\.]*\(")

OK = "ok"
ERROR = "error"
DELEGATED = "delegated"


def js_literal(value) -> str:
    """JSON 转义 == 合法 JS 字符串字面量（与 ConvertTo-MtChannelJsLiteral 同义）。"""
    return json.dumps("" if value is None else str(value), ensure_ascii=True)


def diag_clean(diagnostics) -> bool:
    """diagnostics 是否「没有错误证据」。null/空/bool 一律不算证据（否则每条命令都会误降级）。"""
    if diagnostics is None:
        return True
    items = []
    if isinstance(diagnostics, dict):
        items = list(diagnostics.items())
    elif isinstance(diagnostics, str):
        if not diagnostics.strip():
            return True
        items = [("diagnostics", diagnostics)]
    elif isinstance(diagnostics, (list, tuple)):
        items = [("diagnostics", diagnostics)]
    else:
        items = [("diagnostics", diagnostics)]
    for name, val in items:
        if val is None:
            continue
        if isinstance(val, bool):
            if not val:
                continue
        s = str(val)
        if not s.strip():
            continue
        if DIAG_KEY_RE.search(str(name)):
            return False
        if DIAG_VAL_RE.search(s):
            return False
    return True


def eval_ok(result) -> bool:
    if not isinstance(result, dict):
        return False
    if not result.get("ok"):
        return False
    return diag_clean(result.get("diagnostics"))


def transport_error(text) -> bool:
    return bool(TRANSPORT_RE.search(str(text or "")))


def autojs_deployed(version: str, side: str = "server", root: str | None = None) -> bool:
    """本线 run 目录是否有 AutoJS jar，或 fabric 线本次启动是否真的注入了 AutoJS。"""
    try:
        sides = ["server", "client"] if mt_paths.is_fabric(version) else [side]
        for s in sides:
            mods = mt_paths.mods_dir(version, s, root)
            if os.path.isdir(mods):
                if any(f.startswith("AutoJS-") and f.endswith(".jar") for f in os.listdir(mods)):
                    return True
            flag = mt_paths.fabric_injected_flag(version, s, root)
            if mt_paths.is_fabric(version) and os.path.isfile(flag):
                return True
    except KeyError:
        return False
    return False


def chain_order() -> str:
    """探测链顺序：client-first（默认）| server-first。非法值按默认，不抛。"""
    raw = str(os.environ.get("MT_CHANNEL_ORDER") or "client-first").strip().lower()
    if raw in ("server-first", "server", "console"):
        return "server-first"
    return "client-first"


def plan(command: str, side: str = "server",
         order: str | None = None) -> list[tuple[str, str, str]]:
    """探测链（名字, 求值侧, 代码）。side=client 时只有客户端那一级。

    默认 client-first：客户端 sendCommand 以**玩家身份**执行，@s 有效、不占键鼠、不要求窗口前台。
    server-first 只留给「必须 console 源」的个别场景（此时 @s 解析成 console，用例得写 @a）。
    """
    # 客户端通道走 ServerboundChatCommand 包，包体是**不带前导 / 的命令文本**；
    # 带 / 会被服务端当成未知命令（2026-11-05 实测：/astraldst snap D9 → 未知或不完整的命令，
    # astraldst snap D8 → AP_D8_SNAP）。用例里的命令普遍带 /（旧键鼠通道要求），故此处归一化。
    cmd = str(command or "").strip()
    if cmd.startswith("/"):
        cmd = cmd[1:]
    lit = js_literal(cmd)
    client_tier = ("send_command", "client",
                   '(function(){ Client.getConnection().sendCommand(' + lit + '); return "send_command"; })()')
    server_tiers: list[tuple[str, str, str]] = []
    if side == "server":
        server_tiers = [
            ("autojs_command", "server",
             '(function(){ AutoJS.command(' + lit + '); return "autojs_command"; })()'),
            ("perform_prefixed", "server",
             '(function(){ var s = server; var c = s.getCommands(); '
             'return String(c.performPrefixedCommand(s.createCommandSourceStack(), ' + lit + ')); })()'),
        ]
    if (order or chain_order()) == "server-first":
        return server_tiers + [client_tier]
    return [client_tier] + server_tiers


class Injector:
    """一进程内复用：按 (version, side) 记住上次成功的探测级，下次直连。"""

    def __init__(self, client, version: str, root: str | None = None, emit=None):
        self.client = client
        self.version = version
        self.root = root
        self.emit = emit or (lambda line: None)
        self.probe_cache: dict[tuple[str, str, str], str] = {}

    def _run_line(self, status: str, detail: str):
        one = re.sub(r"\s+", " ", str(detail or "")).strip()
        if len(one) > 300:
            one = one[:299] + "…"
        self.emit(f"MT_CHANNEL: op=command channel=autojs status={status} detail={one}")

    def command(self, command, side: str = "server", timeout_ms: int = 20000,
                order: str | None = None) -> dict:
        cmd = str(command or "").strip()
        if not cmd:
            self._run_line(ERROR, "command_empty：命令为空")
            return {"status": ERROR, "detail": "command_empty：命令为空"}
        if not autojs_deployed(self.version, side, self.root):
            detail = (f"{self.version} 缺 autojs jar（run 目录 mods 里没有 AutoJS-*.jar），"
                      f"命令通道不可用")
            self._run_line(DELEGATED, detail)
            return {"status": DELEGATED, "detail": detail}

        order_key = order or chain_order()
        key = (self.version, side, order_key)
        cached = self.probe_cache.get(key)
        tiers = plan(cmd, side, order_key)
        if cached:
            tiers = sorted(tiers, key=lambda t: 0 if t[0] == cached else 1)
        errors = []
        for name, eval_side, code in tiers:
            res = self.client.eval_js(code, side=eval_side, mode="raw", timeout_ms=timeout_ms)
            if eval_ok(res):
                self.probe_cache[key] = name
                detail = f"cmd_via={name} cmd={cmd}"
                self._run_line(OK, detail)
                return {"status": OK, "detail": detail, "via": name}
            err = str(res.get("error") or "") or f"{name} 未被接受"
            errors.append(f"{name}: {err}")
        chain_error = " | ".join(errors)
        if transport_error(chain_error):
            detail = f"endpoint=unavailable {chain_error}"
            self._run_line(ERROR, detail)
            return {"status": ERROR, "detail": detail}
        detail = "探测链三级全部失败（端点可达但无一级被接受）：" + chain_error
        self._run_line(DELEGATED, detail)
        return {"status": DELEGATED, "detail": detail}
