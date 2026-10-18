#!/usr/bin/env python3
"""mt_case.py — AutoJS 用例执行器（WSL/bash 工具链）。

职责（与 scripts/test/mt_case.ps1 的 AutoJS 子集等价）：
  · validate：封闭原语表 + 断言类型 + 迁移纯度（channel=autojs 不得含废除原语）
  · run：执行 steps（note / mcp_status / eval_js / await_state），再跑 asserts
  · 分桶：PASS(0) / FAIL(1) / ERROR(2) / BLOCKED(11) / TIMEOUT(12)
  · 标记：MT_CASE_RESULT: <case_id> = PASS 等，逐条落 cases/.mt_results/<case>.json

断言口径：
  · state / eval —— 主判据，来自前序 eval_js 的 save_as 或内联 code；expect 支持
    标量 / {min,max,gte,lte,regex,set,contains,len} / 嵌套逐键递归
  · log / absent —— 旁证（日志通道）；source: latest | kubejs | client | <相对路径>
  · crash / mixin / kubejs —— 只能读文件层事实（端点随进程一起死），保留为日志通道
  · vision —— 截图 + 语义判定，WSL 下无等价物 ⇒ 记 BLOCKED（不是 FAIL）并在报告里点名

用法：
  python3 scripts/test/lib/mt_case.py validate --cases-dir scripts/test/cases
  python3 scripts/test/lib/mt_case.py run --case scripts/test/cases/X-1.21.1.json --run-dir run/1.21.1
  python3 scripts/test/lib/mt_case.py run --version 1.21.1 --cases-dir scripts/test/cases --run-dir run/1.21.1
"""
from __future__ import annotations

import argparse
import glob
import json
import os
import re
import sys
import time
from datetime import datetime

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mt_autojs import AutoJs  # noqa: E402

# ── 封闭原语表（与 pwsh 版同键集；active = AutoJS 通道允许的原语）────────────
PRIMITIVES = {
    "note": {"active": True, "fields": {"text"}},
    "mcp_status": {"active": True, "fields": {"timeout_ms", "save_as"}},
    "eval_js": {"active": True, "fields": {"side", "code", "script", "args", "save_as", "timeout_ms", "mode", "expect"}},
    "await_state": {"active": True, "fields": {"side", "code", "script", "args", "timeout_ms", "interval_ms"}},
    "assert": {"active": True, "fields": {"type", "pattern", "source", "image", "question", "from", "code", "side", "expect"}},
    "wait": {"active": True, "fields": {"ms"}, "deprecated": "固定睡眠已退役，仅作迁移期兼容"},
    # ↓ 废除面：channel=autojs 下出现即 ERROR
    "inject_command": {"active": False, "fields": {"command", "no_esc"}},
    "inject_key": {"active": False, "fields": {"key", "hold_ms", "no_esc"}},
    "inject_mouse": {"active": False, "fields": {"button", "shift", "hold_ms", "no_esc"}},
    "screenshot": {"active": False, "fields": {"tag", "mode", "crop"}},
    "kubejs_reload": {"active": False, "fields": set()},
}
ASSERT_TYPES = ("log", "absent", "crash", "kubejs", "mixin", "vision", "eval", "state")
SIDES = ("auto", "server", "client")


class Blocked(Exception):
    pass


# ── 期望值匹配 ────────────────────────────────────────────────────────────
def match(actual, expect) -> tuple[bool, str]:
    if isinstance(expect, dict):
        ops = set(expect) & {"min", "max", "gte", "lte", "regex", "set", "contains", "len", "not", "type"}
        if ops and not isinstance(actual, dict):
            return _match_predicate(actual, expect, ops)
        if isinstance(actual, dict) or isinstance(expect, dict):
            if not isinstance(actual, dict):
                return False, f"期望对象，实际 {type(actual).__name__}={actual!r}"
            for key, sub in expect.items():
                if key not in actual:
                    return False, f"缺键 {key}（实际键：{sorted(actual)[:8]}）"
                ok, why = match(actual[key], sub)
                if not ok:
                    return False, f"{key}: {why}"
            return True, ""
    if isinstance(expect, list):
        if not isinstance(actual, list):
            return False, f"期望数组，实际 {type(actual).__name__}"
        if len(actual) != len(expect):
            return False, f"数组长度 {len(actual)} != {len(expect)}"
        for i, (a, e) in enumerate(zip(actual, expect)):
            ok, why = match(a, e)
            if not ok:
                return False, f"[{i}] {why}"
        return True, ""
    if isinstance(expect, bool) or actual is None or expect is None:
        return (actual is expect or actual == expect), f"{actual!r} != {expect!r}"
    if isinstance(actual, (int, float)) and isinstance(expect, (int, float)):
        return actual == expect, f"{actual!r} != {expect!r}"
    if str(actual) == str(expect) or actual == expect:
        return True, ""
    return False, f"{actual!r} != {expect!r}"


def _match_predicate(actual, expect: dict, ops) -> tuple[bool, str]:
    try:
        if "regex" in expect and not re.search(str(expect["regex"]), str(actual)):
            return False, f"{actual!r} 不匹配 /{expect['regex']}/"
        if "min" in expect and float(actual) < float(expect["min"]):
            return False, f"{actual} < min {expect['min']}"
        if "max" in expect and float(actual) > float(expect["max"]):
            return False, f"{actual} > max {expect['max']}"
        if "gte" in expect and float(actual) < float(expect["gte"]):
            return False, f"{actual} < gte {expect['gte']}"
        if "lte" in expect and float(actual) > float(expect["lte"]):
            return False, f"{actual} > lte {expect['lte']}"
        if "len" in expect and len(actual) != int(expect["len"]):
            return False, f"len {len(actual)} != {expect['len']}"
        if "set" in expect:
            if sorted(map(str, actual)) != sorted(map(str, expect["set"])):
                return False, f"集合 {actual!r} != {expect['set']!r}"
        if "contains" in expect and expect["contains"] not in actual:
            return False, f"{actual!r} 不含 {expect['contains']!r}"
        if "not" in expect and actual == expect["not"]:
            return False, f"{actual!r} 不应等于 {expect['not']!r}"
        if "type" in expect:
            want = {"bool": bool, "int": int, "float": float, "str": str, "list": list, "dict": dict}.get(expect["type"])
            if want and not isinstance(actual, want):
                return False, f"类型 {type(actual).__name__} != {expect['type']}"
    except (TypeError, ValueError) as exc:
        return False, f"判据不可比：{exc}"
    return True, ""


# ── 日志通道 ──────────────────────────────────────────────────────────────
def log_path(run_dir: str, source: str) -> str:
    src = (source or "latest").strip()
    if src in ("latest", "latest.log", "logs/latest.log"):
        path = os.path.join(run_dir, "logs", "latest.log")
    elif src in ("kubejs", "kubejs/server", "server"):
        path = os.path.join(run_dir, "logs", "kubejs", "server.log")
    elif src in ("kubejs/client", "client"):
        path = os.path.join(run_dir, "logs", "kubejs", "client.log")
    else:
        path = src if os.path.isabs(src) else os.path.join(run_dir, src)
    return path


def read_log(run_dir: str, source: str, offset: int = 0) -> str:
    path = log_path(run_dir, source)
    try:
        with open(path, "rb") as fh:
            if offset > 0:
                size = os.fstat(fh.fileno()).st_size
                fh.seek(offset if offset <= size else 0)   # 文件被轮转 ⇒ 退回整文件
            return fh.read().decode("utf-8", "replace")
    except OSError:
        return ""


def latest_crash_stamp(run_dir: str):
    d = os.path.join(run_dir, "crash-reports")
    try:
        files = [os.path.join(d, f) for f in os.listdir(d)]
    except OSError:
        return None
    return max((os.path.getmtime(f) for f in files), default=None)


# ── 验证 ──────────────────────────────────────────────────────────────────
def validate_case(doc: dict, path: str) -> list[str]:
    errs = []
    for key in ("case_id", "title", "version", "steps"):
        if key not in doc:
            errs.append(f"{path}: 缺字段 {key}")
    if errs:
        return errs
    channel = doc.get("channel", "kubejs")
    if channel not in ("kubejs", "autojs"):
        errs.append(f"{path}: 未知 channel={channel}")
    autojs = channel == "autojs"
    for i, step in enumerate(doc.get("steps", [])):
        op = step.get("op")
        if op not in PRIMITIVES:
            errs.append(f"{path}: steps[{i}] 未知原语 {op!r}")
            continue
        spec = PRIMITIVES[op]
        if autojs and not spec["active"]:
            errs.append(f"{path}: steps[{i}] channel=autojs 下出现废除原语 {op}（迁移未完成）")
        unknown = set(step) - spec["fields"] - {"op"}
        if unknown:
            errs.append(f"{path}: steps[{i}] {op} 多余字段 {sorted(unknown)}")
        if op in ("eval_js", "await_state"):
            if not step.get("code") and not step.get("script"):
                errs.append(f"{path}: steps[{i}] {op} 缺 code/script")
            if step.get("side", "auto") not in SIDES:
                errs.append(f"{path}: steps[{i}] 非法 side={step.get('side')}")
            if op == "eval_js" and step.get("code", "").lstrip().startswith("return "):
                errs.append(f"{path}: steps[{i}] code 以顶层 return 开头（须改 IIFE）")
    for j, a in enumerate(doc.get("asserts", [])):
        t = a.get("type")
        if t not in ASSERT_TYPES:
            errs.append(f"{path}: asserts[{j}] 未知类型 {t!r}")
        if t in ("state", "eval") and "expect" not in a:
            errs.append(f"{path}: asserts[{j}] {t} 缺 expect")
        if t == "state" and "from" not in a:
            errs.append(f"{path}: asserts[{j}] state 缺 from")
        if t == "log" and "pattern" not in a:
            errs.append(f"{path}: asserts[{j}] log 缺 pattern")
    return errs


# ── 执行 ──────────────────────────────────────────────────────────────────
class Runner:
    def __init__(self, doc: dict, run_dir: str, endpoint: str | None = None,
                 case_timeout: float = 600.0, verbose: bool = True):
        self.doc = doc
        self.run_dir = os.path.abspath(run_dir)
        self.client = AutoJs(endpoint=endpoint,
                             conf_path=os.path.join(os.path.dirname(__file__), "..", "mt.conf"))
        self.case_timeout = case_timeout
        self.verbose = verbose
        self.vars: dict[str, object] = {}
        self.records: list[dict] = []
        self.failures: list[str] = []
        self.blocked: list[str] = []
        self.warnings: list[str] = []
        self.started_crash = latest_crash_stamp(self.run_dir)
        # 每条用例的日志窗口起点（字节）：absent 断言只看本窗口，避免被上一轮/上一条污染
        self.log_offsets: dict[str, int] = {}
        for src in ("latest", "kubejs", "client"):
            path = log_path(self.run_dir, src)
            try:
                self.log_offsets[path] = os.path.getsize(path)
            except OSError:
                self.log_offsets[path] = 0
        self.deadline = time.monotonic() + case_timeout

    def say(self, line: str):
        if self.verbose:
            print(line, flush=True)

    def _time_left(self) -> float:
        return self.deadline - time.monotonic()

    # ── steps ──
    def run(self) -> tuple[str, int]:
        self.say(f"MT_CASE_BEGIN: {self.doc['case_id']} (channel={self.doc.get('channel','kubejs')})")
        try:
            for i, step in enumerate(self.doc.get("steps", [])):
                if self._time_left() <= 0:
                    return self._finish("TIMEOUT", 12, f"steps 超时（>{self.case_timeout}s）")
                self.say(f"  [{i+1}/{len(self.doc.get('steps', []))}] {step.get('op')}")
                self._step(step)
            self._asserts()
        except Blocked as exc:
            return self._finish("BLOCKED", 11, str(exc))
        except KeyboardInterrupt:
            return self._finish("ERROR", 2, "被中断")
        if self.failures:
            return self._finish("FAIL", 1, "; ".join(self.failures[:3]))
        if self.blocked:
            return self._finish("BLOCKED", 11, "; ".join(self.blocked[:3]))
        return self._finish("PASS", 0, "")

    def _step(self, step: dict):
        op = step["op"]
        if op == "note":
            self.say("      · " + str(step.get("text", "")).splitlines()[0][:100])
        elif op == "mcp_status":
            self._do_mcp_status(step)
        elif op == "eval_js":
            self._do_eval(step)
        elif op == "await_state":
            self._do_await(step)
        elif op == "assert":
            self._do_inline_assert(step)
        elif op == "wait":
            ms = int(step.get("ms", 0))
            self.warnings.append(f"wait {ms}ms（固定睡眠，建议改 await_state）")
            time.sleep(ms / 1000.0)
        else:  # validate 已挡，防御性
            raise Blocked(f"未实现原语 {op}")

    def _resolve_code(self, step: dict) -> str:
        if step.get("code"):
            return step["code"]
        script = step.get("script") or ""
        path = script if os.path.isabs(script) else os.path.join(self.run_dir, script)
        with open(path, "r", encoding="utf-8") as fh:
            return fh.read()

    def _do_mcp_status(self, step: dict):
        probe = self.client.probe(timeout_ms=int(step.get("timeout_ms") or 15000))
        val = {"Ok": bool(probe.get("ok")), "Reason": probe.get("reason", ""),
               "TcpOk": probe.get("tcp"), "InitializeOk": probe.get("init"),
               "StatusOk": probe.get("status_ok"), "Attempts": probe.get("attempts"),
               "EndpointUrl": probe.get("endpoint"), "ProtocolVersion": probe.get("protocol")}
        if step.get("save_as"):
            self.vars[step["save_as"]] = val
        self.records.append({"op": "mcp_status", "outcome": "PASS" if val["Ok"] else "BLOCKED"})
        if not val["Ok"]:
            raise Blocked("AutoJS 端点未就绪：" + (val["Reason"] or "未知"))

    def _do_eval(self, step: dict):
        code = self._resolve_code(step)
        side = step.get("side", "auto")
        mode = step.get("mode") or "json"
        res = self.client.eval_js(code, side=side, mode=mode,
                                  timeout_ms=int(step.get("timeout_ms") or 20000))
        if not res.get("ok"):
            err = str(res.get("error", ""))
            self.records.append({"op": "eval_js", "side": side, "outcome": "ERROR", "detail": err[:400]})
            if "端点" in err or "transport" in err or "HTTP 0" in err:
                raise Blocked("eval_js 端点不可用：" + err[:200])
            self.failures.append(f"eval_js 失败：{err[:200]}")
            return
        value = res.get("value_parsed") if mode == "json" else res.get("value")
        if mode == "bool":
            value = str(res.get("value")).strip() == "true"
        if step.get("save_as"):
            self.vars[step["save_as"]] = value
        self.records.append({"op": "eval_js", "side": res.get("side") or side,
                             "outcome": "PASS", "value": value})
        if step.get("expect") is not None:
            ok, why = match(value, step["expect"])
            if not ok:
                self.failures.append(f"eval_js expect 不满足：{why}")

    def _do_await(self, step: dict):
        code = self._resolve_code(step)
        side = step.get("side", "auto")
        timeout_ms = int(step.get("timeout_ms") or 30000)
        interval = max(100, int(step.get("interval_ms") or 500)) / 1000.0
        end = time.monotonic() + min(timeout_ms / 1000.0, max(0.0, self._time_left()))
        tries = 0
        last = None
        while time.monotonic() < end:
            tries += 1
            res = self.client.eval_js(code, side=side, mode="bool", timeout_ms=min(10000, timeout_ms))
            if not res.get("ok"):
                err = str(res.get("error", ""))
                if "端点" in err or "HTTP 0" in err:
                    raise Blocked("await_state 端点不可用：" + err[:200])
                last = err
            else:
                if str(res.get("value")).strip() == "true":
                    self.records.append({"op": "await_state", "side": side, "tries": tries, "outcome": "PASS"})
                    return
                last = f"条件为假（{res.get('value')!r}）"
            time.sleep(interval)
        self.records.append({"op": "await_state", "side": side, "tries": tries, "outcome": "FAIL"})
        self.failures.append(f"await_state 超时 {timeout_ms}ms：{last}")

    def _do_inline_assert(self, step: dict):
        self._assert_one(step, inline=True)

    # ── asserts ──
    def _asserts(self):
        for a in self.doc.get("asserts", []):
            self._assert_one(a, inline=False)

    def _assert_one(self, a: dict, inline: bool):
        t = a.get("type")
        label = (a.get("name") or a.get("pattern") or a.get("from") or t)
        if t in ("state", "eval"):
            src = a.get("from")
            if src is not None:
                if src not in self.vars:
                    self.failures.append(f"断言 {label}：save_as {src} 不存在")
                    return
                value = self.vars[src]
            else:
                res = self.client.eval_js(a.get("code", "null"), side=a.get("side", "auto"),
                                          mode="json", timeout_ms=15000)
                if not res.get("ok"):
                    self.failures.append(f"断言 {label}：求值失败 {res.get('error','')[:150]}")
                    return
                value = res.get("value_parsed")
            ok, why = match(value, a.get("expect"))
            self.say(f"      assert {t} {label}: {'PASS' if ok else 'FAIL ' + why}")
            if not ok:
                self.failures.append(f"断言 {label}：{why}")
        elif t in ("log", "absent"):
            pattern = a.get("pattern", "")
            src = a.get("source", "latest")
            text = read_log(self.run_dir, src, self.log_offsets.get(log_path(self.run_dir, src), 0))
            hit = bool(re.search(pattern, text)) if pattern else False
            ok = hit if t == "log" else not hit
            detail = f"{t} /{pattern}/ source={a.get('source','latest')} hit={hit}"
            self.say(f"      assert {detail}: {'PASS' if ok else 'FAIL'}")
            if not ok:
                self.failures.append(f"断言 {label}：{detail}")
        elif t == "crash":
            now = latest_crash_stamp(self.run_dir)
            ok = now is None or (self.started_crash is not None and now <= self.started_crash)
            self.say(f"      assert crash: {'PASS' if ok else 'FAIL (新增 crash-report)'}")
            if not ok:
                self.failures.append("断言 crash：本轮产生了新的 crash-report")
        elif t == "kubejs":
            text = read_log(self.run_dir, "kubejs", self.log_offsets.get(log_path(self.run_dir, "kubejs"), 0))
            bad = re.search(r"(?m)^\[[^\]]*\] \[(?:ERROR|FATAL)\]|\bjava\.[a-z.]*\w*Exception\b|\bScriptException\b", text)
            ok = bad is None
            self.say(f"      assert kubejs: {'PASS' if ok else 'FAIL ' + (bad.group(0) if bad else '')}")
            if not ok:
                self.failures.append("断言 kubejs：KubeJS 日志出现 error/exception/failed")
        elif t == "mixin":
            text = read_log(self.run_dir, "latest", self.log_offsets.get(log_path(self.run_dir, "latest"), 0))
            bad = re.search(r"(?i)(Mixin apply failed|InvalidInjectionException|MixinTransformerError|Attach error)", text)
            ok = bad is None
            self.say(f"      assert mixin: {'PASS' if ok else 'FAIL ' + (bad.group(0) if bad else '')}")
            if not ok:
                self.failures.append("断言 mixin：日志出现 mixin 注入失败")
        elif t == "vision":
            self.say("      assert vision: BLOCKED（WSL 无截图语义判定通道）")
            self.blocked.append(f"断言 vision（{a.get('image','')}）在 WSL 下无等价通道")
        else:
            self.failures.append(f"未知断言类型 {t}")

    def _finish(self, verdict: str, code: int, detail: str) -> tuple[str, int]:
        cid = self.doc["case_id"]
        self.say(f"MT_CASE_RESULT: {cid} = {verdict}" + (f" — {detail}" if detail else ""))
        rec = {
            "case_id": cid, "verdict": verdict, "detail": detail,
            "channel": self.doc.get("channel", "kubejs"),
            "at": datetime.now().isoformat(timespec="seconds"),
            "steps": self.records, "failures": self.failures,
            "blocked": self.blocked, "warnings": self.warnings,
        }
        out_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "cases", ".mt_results")
        os.makedirs(out_dir, exist_ok=True)
        with open(os.path.join(out_dir, f"{cid}.json"), "w", encoding="utf-8") as fh:
            json.dump(rec, fh, ensure_ascii=False, indent=1)
        return verdict, code


def load_cases(args) -> list[str]:
    if getattr(args, "case", None):
        return [args.case]
    files = sorted(glob.glob(os.path.join(args.cases_dir, "**", "*.json"), recursive=True))
    files = [f for f in files if not os.path.basename(f).startswith(".mt_")
             and ".mt_results" not in f]
    if getattr(args, "version", None):
        files = [f for f in files if f.endswith(f"-{args.version}.json")]
    return files


def main() -> int:
    ap = argparse.ArgumentParser(description="AutoJS 用例执行器")
    sub = ap.add_subparsers(dest="cmd", required=True)
    v = sub.add_parser("validate")
    v.add_argument("--cases-dir", default="scripts/test/cases")
    v.add_argument("--version", default=None)
    r = sub.add_parser("run")
    r.add_argument("--case", default=None)
    r.add_argument("--cases-dir", default="scripts/test/cases")
    r.add_argument("--version", default=None)
    r.add_argument("--run-dir", required=True)
    r.add_argument("--endpoint", default=None)
    r.add_argument("--case-timeout", type=float, default=600.0)
    r.add_argument("--stop-on-fail", action="store_true")
    a = ap.parse_args()

    files = load_cases(a)
    if a.cmd == "validate":
        bad = 0
        for f in files:
            try:
                doc = json.load(open(f, encoding="utf-8"))
            except json.JSONDecodeError as exc:
                print(f"MT_CASE_VALIDATE: ERROR {f} — JSON 解析失败 {exc}")
                bad += 1
                continue
            errs = validate_case(doc, f)
            if errs:
                bad += 1
                for e in errs:
                    print("MT_CASE_VALIDATE: ERROR " + e)
        print(f"MT_CASE_VALIDATE: files={len(files)} errors={bad}")
        return 0 if bad == 0 else 2

    results = []
    for f in files:
        doc = json.load(open(f, encoding="utf-8"))
        errs = validate_case(doc, f)
        if errs:
            print("MT_CASE_VALIDATE: ERROR " + "; ".join(errs[:3]))
            results.append((doc.get("case_id", f), "ERROR", 2))
            if a.stop_on_fail:
                break
            continue
        runner = Runner(doc, a.run_dir, a.endpoint, a.case_timeout)
        verdict, code = runner.run()
        results.append((doc["case_id"], verdict, code))
        if code not in (0,) and a.stop_on_fail:
            break
    fails = [x for x in results if x[2] != 0]
    print("MT_CASE_SUMMARY: total=%d pass=%d nonpass=%d" %
          (len(results), len(results) - len(fails), len(fails)))
    for cid, verdict, _ in fails:
        print(f"  - {cid}: {verdict}")
    return max((c for _, _, c in results), default=0)


if __name__ == "__main__":
    sys.exit(main())
