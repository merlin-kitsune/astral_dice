#!/usr/bin/env python3
"""mt_case.py — 用例执行 + 断言引擎（bash/python 原生，不依赖 pwsh）。

职责（判定语义与 scripts/test/mt_case.ps1 对齐；pwsh 资产删除后由其接替）：
  · validate：封闭原语表 + 断言类型 + 迁移纯度（channel=autojs 不得含废除原语）
  · run：执行 steps（note / mcp_status / eval_js / await_state / inject_command / jar_stats /
         screenshot / inject_key / inject_mouse / wait / assert），再跑 asserts
  · 分桶：PASS(0) / FAIL(1) / ERROR(2) / BLOCKED(11) / TIMEOUT(12)
  · 标记：MT_CASE_RESULT: <case_id> = PASS 等；通道降级另起 MT_CHANNEL: delegated_count=N
  · 逐条落 cases/.mt_results/<case>.json

断言口径：
  · state / eval —— 主判据，来自前序 eval_js 的 save_as 或内联 code；expect 支持
    标量 / {min,max,gte,lte,regex,set,contains,len} / 嵌套逐键递归
  · log / absent —— 旁证（日志通道）；source: latest | kubejs | client | <相对路径>
    窗口 scope: case（缺省，自本用例起）/ launch（自启动阶段起，基线取自 cases/.mt_snapshot.json
    的 launch_offsets；缺失则退化为 case 并告警）/ whole（整文件）；mixin 缺省窗口是 launch。
    窗口不可判（起点越过当前文件长度 = 内容确定丢失）⇒ INCONCLUSIVE，不计 FAIL 也不计 PASS。
  · crash / mixin / kubejs —— 只能读文件层事实（端点随进程一起死），保留为日志通道；
    crash-reports 目录不存在 ⇒ INCONCLUSIVE（无证据，不冒充 PASS）
  · jar —— 读 op=jar_stats 落下的离线包统计（models/recipes/trinkets/entries/signs 下限、
    bountiful/embed 上限、embed_lib 布尔），口径与 pwsh Get-MtCaseJarStats 逐字段一致
  · bytecode / bytecode_absent —— 在产物 jar 条目名上跑正则（数量下限/零命中）
  · vision —— 截图 + 语义判定，python 侧无等价物 ⇒ 记 BLOCKED(11)（不是 PASS，也不是 FAIL）

通道事实（不静默放行）：
  · channel=autojs 的 inject_command ⇒ 真实现（三级探测链，见 mt_channel.py）
  · channel=kubejs 的 inject_command / inject_key / inject_mouse / screenshot ⇒ BLOCKED：
    native 注入与截图通道（mt_inject.ps1 / mt_capture.ps1）随 pwsh 删除，python 侧无 Win32 通道
  · 产物 jar 缺失 ⇒ op=jar_stats 记 BLOCKED（工具链事实）；bytecode 断言按 pwsh 口径记 FAIL

用法：
  python3 scripts/test/lib/mt_case.py validate --cases-dir scripts/test/cases
  python3 scripts/test/lib/mt_case.py audit --cases-dir scripts/test/cases [--list]
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
import mt_channel  # noqa: E402
import mt_paths  # noqa: E402
from mt_autojs import AutoJs  # noqa: E402

# ── 封闭原语表（与 pwsh 版同键集；active = AutoJS 通道允许的原语）────────────
PRIMITIVES = {
    "note": {"active": True, "fields": {"text"}},
    "mcp_status": {"active": True, "fields": {"timeout_ms", "save_as"}},
    "eval_js": {"active": True, "fields": {"side", "code", "script", "args", "save_as", "timeout_ms", "mode", "expect"}},
    "await_state": {"active": True, "fields": {"side", "code", "script", "args", "timeout_ms", "interval_ms"}},
    "assert": {"active": True, "fields": {"type", "pattern", "source", "image", "question", "from", "code", "side", "expect"}},
    "wait": {"active": True, "fields": {"ms"}, "deprecated": "固定睡眠已退役，仅作迁移期兼容"},
    # ↓ 已迁移：AutoJS 通道下可用（pwsh 同口径；仅 kubejs_reload 退役）
    "inject_command": {"active": True, "fields": {"command", "no_esc"}},
    "inject_key": {"active": True, "fields": {"key", "hold_ms", "no_esc"}},
    "inject_mouse": {"active": True, "fields": {"button", "shift", "hold_ms", "no_esc"}},
    "screenshot": {"active": True, "fields": {"tag", "mode", "crop"}},
    "jar_stats": {"active": True, "fields": {"save_as"}},
    # ↓ 废除面：channel=autojs 下出现即报错（需求书 §5：改用 eval_js / await_state）
    "kubejs_reload": {"active": False, "fields": set()},
}
ASSERT_TYPES = ("log", "absent", "crash", "kubejs", "mixin", "vision", "eval", "state",
                "jar", "bytecode", "bytecode_absent")
SIDES = ("auto", "server", "client")
# 结构化判据口径（D4）：只有「从运行中的 JVM 取状态并与 expect 比对」才算判据。
# vision 不算（无截图语义通道），固定 wait 不算，mcp_status 不算（只是通道探活）。
JUDGMENT_OPS = ("eval_js", "await_state")
JUDGMENT_ASSERT_TYPES = ("eval", "state", "log", "absent", "crash", "kubejs", "mixin")

# jar 断言字段口径（pwsh Invoke-MtCaseAssert 的 min/max 两张表，逐字段同名）
JAR_MIN_MAP = {"models_min": "models", "recipes_min": "recipes", "trinkets_min": "trinkets",
               "entries_min": "entries", "signs_min": "signs"}
JAR_MAX_MAP = {"bountiful_max": "bountiful", "embed_max": "embed"}
JAR_CRITERIA = set(JAR_MIN_MAP) | set(JAR_MAX_MAP) | {"embed_lib"}


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


def has_crash_dir(run_dir: str) -> bool:
    """crash-reports 目录是否存在。不存在 ⇒ crash 断言无证据（pwsh 记 INCONCLUSIVE，不冒充 PASS）。"""
    return os.path.isdir(os.path.join(run_dir, "crash-reports"))


# ── 断言窗口（pwsh B7 的 case / launch / whole 三档）───────────────────────
# case   = 自本用例开始（Runner 起跑时的文件长度；默认，挡住前序用例/上一轮的污染）
# launch = 自 launch 阶段起（基线由 launch 阶段在 cases/.mt_snapshot.json 冻结；缺失则退化并告警，
#          绝不静默把 launch 断言降级成 case 断言 —— 那会凭空制造假 FAIL）
# whole  = 整文件（启动期事实；等价旧 --no-snapshot）
SNAPSHOT_NAME = ".mt_snapshot.json"
WINDOW_CHOICES = ("case", "launch", "whole")


def snapshot_path(cases_dir: str) -> str:
    return os.path.join(cases_dir, SNAPSHOT_NAME)


def load_snapshot(cases_dir: str | None) -> dict:
    if not cases_dir:
        return {}
    try:
        with open(snapshot_path(cases_dir), "r", encoding="utf-8") as fh:
            data = json.load(fh)
    except (OSError, ValueError):
        return {}
    return data if isinstance(data, dict) else {}


def launch_offsets(snap: dict, version: str | None) -> dict:
    """取出 <版本>.launch_offsets（只有 launch 阶段会冻结这一份）。"""
    versions = snap.get("versions") if isinstance(snap, dict) else None
    entry = (versions or {}).get(version or "")
    if not isinstance(entry, dict):
        return {}
    lo = entry.get("launch_offsets")
    return lo if isinstance(lo, dict) else {}


# ── 验证 ──────────────────────────────────────────────────────────────────
def validate_case(doc: dict, path: str) -> list[str]:
    return validate_case_strict(doc, path, require_judgment=False)


def has_judgment(doc: dict) -> tuple[bool, str]:
    """返回 (是否有明确判据, 判据种类)。判据必须是结构化状态/日志断言，vision 与 wait 不算。"""
    for s in doc.get("steps", []) or []:
        if not isinstance(s, dict):
            continue
        if s.get("op") in JUDGMENT_OPS and s.get("expect") is not None:
            return True, f"{s.get('op')}+expect"
        if s.get("op") == "assert" and s.get("type") in JUDGMENT_ASSERT_TYPES:
            return True, f"step.assert.{s.get('type')}"
    for a in doc.get("asserts", []) or []:
        if isinstance(a, dict) and a.get("type") in JUDGMENT_ASSERT_TYPES:
            return True, f"asserts.{a.get('type')}"
    return False, ""


def validate_case_strict(doc: dict, path: str, require_judgment: bool = False) -> list[str]:
    errs = []
    if not isinstance(doc, dict):
        return [f"{path}: 顶层不是 JSON 对象"]
    for key in ("case_id", "title", "version", "steps"):
        if key not in doc:
            errs.append(f"{path}: 缺字段 {key}")
    if errs:
        return errs
    channel = doc.get("channel", "kubejs")
    if channel not in ("kubejs", "autojs"):
        errs.append(f"{path}: 未知 channel={channel}")
    autojs = channel == "autojs"
    steps = doc.get("steps") or []
    if not isinstance(steps, list):
        return [f"{path}: steps 不是数组"]
    for i, step in enumerate(steps):
        if not isinstance(step, dict):
            errs.append(f"{path}: steps[{i}] 不是对象")
            continue
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
            if op == "eval_js" and str(step.get("code", "")).lstrip().startswith("return "):
                errs.append(f"{path}: steps[{i}] code 以顶层 return 开头（须改 IIFE）")
    asserts = doc.get("asserts") or []
    if not isinstance(asserts, list):
        return errs + [f"{path}: asserts 不是数组"]
    for j, a in enumerate(asserts):
        if not isinstance(a, dict):
            errs.append(f"{path}: asserts[{j}] 不是对象")
            continue
        t = a.get("type")
        if t not in ASSERT_TYPES:
            errs.append(f"{path}: asserts[{j}] 未知类型 {t!r}")
            continue
        if t in ("state", "eval") and "expect" not in a:
            errs.append(f"{path}: asserts[{j}] {t} 缺 expect")
        if t == "state" and "from" not in a:
            errs.append(f"{path}: asserts[{j}] state 缺 from")
        if t == "log" and "pattern" not in a:
            errs.append(f"{path}: asserts[{j}] log 缺 pattern")
        if t in ("bytecode", "bytecode_absent") and "pattern" not in a:
            errs.append(f"{path}: asserts[{j}] {t} 缺 pattern")
        if t in ("log", "absent", "mixin") and "scope" in a and a["scope"] not in WINDOW_CHOICES:
            errs.append(f"{path}: asserts[{j}] 未知 scope={a['scope']!r}（只接受 case / launch / whole）")
    if require_judgment:
        has, kind = has_judgment(doc)
        if not has:
            vision_only = any(isinstance(s, dict) and s.get("op") == "assert" and s.get("type") == "vision"
                              for s in steps)
            errs.append(f"{path}: 无明确判据（channel=autojs 要求至少 1 条带 expect 的 "
                        f"eval_js/await_state 或结构化 assert"
                        + ("；现有判据只有 vision 截图断言，须改为结构化状态" if vision_only else "") + "）")
    return errs


# ── 执行 ──────────────────────────────────────────────────────────────────
class Runner:
    def __init__(self, doc: dict, run_dir: str, endpoint: str | None = None,
                 case_timeout: float = 600.0, verbose: bool = True,
                 repo_root: str | None = None, results_dir: str | None = None,
                 cases_dir: str | None = None):
        self.doc = doc
        self.version = doc.get("version")
        self.run_dir = os.path.abspath(run_dir)
        self.repo_root = repo_root or mt_paths.repo_root()
        self.client = AutoJs(endpoint=endpoint,
                             conf_path=os.path.join(os.path.dirname(__file__), "..", "mt.conf"))
        self.case_timeout = case_timeout
        self.verbose = verbose
        self.vars: dict[str, object] = {}
        self.records: list[dict] = []
        self.failures: list[str] = []
        self.errors: list[str] = []
        self.blocked: list[str] = []
        self.warnings: list[str] = []
        self.delegated_steps = 0
        self.inconclusive: list[str] = []
        self.cases_dir = cases_dir or os.path.join(
            os.path.dirname(os.path.abspath(__file__)), "..", "cases")
        self.results_dir = results_dir or os.path.join(self.cases_dir, ".mt_results")
        self.injector = mt_channel.Injector(self.client, self.version, self.repo_root, emit=self.say)
        self.started_crash = latest_crash_stamp(self.run_dir)
        # 每条用例的日志窗口起点（字节）：absent 断言只看本窗口，避免被上一轮/上一条污染
        self.log_offsets: dict[str, int] = {}
        for src in ("latest", "kubejs", "client"):
            path = log_path(self.run_dir, src)
            try:
                self.log_offsets[path] = os.path.getsize(path)
            except OSError:
                self.log_offsets[path] = 0
        # launch 窗口基线：只有 launch 阶段冻结过才存在；缺失 ⇒ 断言侧退化并告警（不静默）
        self.launch_offsets = launch_offsets(load_snapshot(self.cases_dir), self.version)
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
            self.records.append({"op": "blocked", "outcome": "BLOCKED", "detail": str(exc)})
            # pwsh 的升级序：ERROR > TIMEOUT > FAIL > BLOCKED（BLOCKED 不覆盖已判定的 FAIL）
            if self.errors:
                self.blocked.append(str(exc))
                return self._finish("ERROR", 2, "; ".join(self.errors[:3]))
            if self.failures:
                self.blocked.append(str(exc))
                return self._finish("FAIL", 1, "; ".join(self.failures[:3]))
            return self._finish("BLOCKED", 11, str(exc))
        except KeyboardInterrupt:
            return self._finish("ERROR", 2, "被中断")
        if self.errors:
            return self._finish("ERROR", 2, "; ".join(self.errors[:3]))
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
        elif op == "jar_stats":
            self._do_jar_stats(step)
        elif op == "inject_command":
            self._do_inject_command(step)
        elif op == "screenshot":
            raise Blocked(
                "screenshot 通道未移植：AutoJS Screenshot.grab（Mt.Channel）与 native mt_capture.ps1 均随 pwsh "
                "删除，python 侧没有截图通道；vision 断言因此恒为 BLOCKED，需由截图/启动块补齐后再启用")
        elif op in ("inject_key", "inject_mouse"):
            raise Blocked(
                f"{op} 通道未移植：pwsh 走 mt_inject.ps1（Win32 SendInput）/ AutoJS 键鼠 JS，"
                f"python 侧无等价通道（需由启动块补齐）")
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

    # ── 离线 jar 统计（不启动游戏；与 pwsh op=jar_stats / bash fabric.sh 同一契约）──
    def _do_jar_stats(self, step: dict):
        save_as = step.get("save_as") or "jar"
        try:
            st = mt_paths.jar_stats(self.version, self.repo_root)
        except mt_paths.JarMissing as exc:
            # JarMissing 的消息已含「找不到产物 jar（先跑 --phase build）：<libs>」，此处不再重复前缀
            raise Blocked(str(exc))
        except KeyError as exc:
            raise Blocked(str(exc))
        self.vars[save_as] = st
        detail = ("save_as={0} jar={1} entries={2} models={3} recipes={4} trinkets={5} "
                  "bountiful={6} embed={7} signs={8}").format(
            save_as, st["jar"], st["entries"], st["models"], st["recipes"],
            st["trinkets"], st["bountiful"], st["embed"], st["signs"])
        self.records.append({"op": "jar_stats", "outcome": "NOTE", "detail": detail})
        self.say("      jar_stats: " + detail)

    # ── AutoJS 命令通道（三级探测链；pwsh Send-MtChannelCommand 等价）──────────────
    def _do_inject_command(self, step: dict):
        cmd = step.get("command", "")
        if self.doc.get("channel", "kubejs") != "autojs":
            raise Blocked(
                "inject_command 在 channel=kubejs 下走 native 注入通道（mt_inject.ps1 / Win32 SendInput），"
                "该通道随 pwsh 测试资产删除，python 侧无等价实现（需由启动块补齐）")
        res = self.injector.command(cmd, side=step.get("side", "server") or "server")
        status = res["status"]
        self.records.append({"op": "inject_command", "outcome": status.upper(), "detail": res["detail"]})
        if status == mt_channel.OK:
            self.say("      inject_command: PASS " + res["detail"])
            return
        if status == mt_channel.DELEGATED:
            # 通道降级 ≠ 失败：单独计数，不升级用例结论（契约 §1.3）
            self.delegated_steps += 1
            self.say("      inject_command: →VIS（通道降级，不参与判定）" + " " + res["detail"])
            return
        if "endpoint=unavailable" in res["detail"]:
            raise Blocked("inject_command 端点不可用：" + res["detail"][:200])
        self.errors.append("inject_command 失败：" + res["detail"][:200])

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
            # pwsh Invoke-MtCaseAssert：这两类断言只对 channel=autojs 有语义（值来自 AutoJS 求值）
            channel = self.doc.get("channel", "kubejs")
            if channel != "autojs":
                self.errors.append(f"{t} 断言要求用例 channel=autojs（当前 {channel}）")
                self.say(f"      assert {t} {label}: ERROR channel={channel} 不支持 {t} 断言")
                return
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
                    self.failures.append(f"断言 {label}：求值失败 {str(res.get('error',''))[:150]}")
                    return
                value = res.get("value_parsed")
            ok, why = match(value, a.get("expect"))
            self.say(f"      assert {t} {label}: {'PASS' if ok else 'FAIL ' + why}")
            if not ok:
                self.failures.append(f"断言 {label}：{why}")
        elif t in ("log", "absent"):
            scope = self._assert_scope(a, "case")
            if scope is None:
                return
            pattern = a.get("pattern", "")
            src = a.get("source", "latest")
            offset, degraded, unclear = self._window_offset(src, scope)
            text = read_log(self.run_dir, src, offset)
            hit = bool(re.search(pattern, text)) if pattern else False
            ok = hit if t == "log" else not hit
            detail = (f"{t} /{pattern}/ source={a.get('source','latest')} window={scope} hit={hit}"
                      + (f"；实际起点 {offset}B" if offset else "")
                      + (f"；{degraded}" if degraded else ""))
            if degraded:
                self.warnings.append(f"断言 {label}：{degraded}")
            if unclear and (ok if t == "log" else True):
                # 窗口不可判：log 的「未命中」不是产品失败；absent 的「未出现」只是弱结论
                # （pwsh mt_assert.ps1：MT_ASSERT_LOG / MT_ASSERT_ABSENT 的 INCONCLUSIVE 通道）
                tag = "INCONCLUSIVE" if t == "log" else "INCONCLUSIVE(弱通过)"
                self.inconclusive.append(f"{t} {label}：{unclear}")
                self.warnings.append(f"MT_ASSERT_INCONCLUSIVE: WARN — 窗口不可判（{unclear}）⇒ 本条不计 {'FAIL' if t == 'log' else 'PASS'}")
                self.say(f"MT_ASSERT_{t.upper()}: {tag} — /{pattern}/ ，但窗口不可判（{unclear}；{detail}）")
                self.say(f"      assert {detail}: {tag}")
                return
            self.say(f"      assert {detail}: {'PASS' if ok else 'FAIL'}")
            if not ok:
                self.failures.append(f"断言 {label}：{detail}")
        elif t == "crash":
            if not has_crash_dir(self.run_dir):
                # 无 crash-reports 目录 ⇒ 没有可比对的证据：pwsh（D4）记 INCONCLUSIVE，绝不冒充 PASS
                crash_dir = os.path.join(self.run_dir, "crash-reports")
                self.inconclusive.append(f"crash（{crash_dir} 目录不存在）")
                self.say(f"MT_ASSERT_CRASH: INCONCLUSIVE — crash-reports 目录不存在（{crash_dir}）")
                self.say("      assert crash: INCONCLUSIVE（不计 PASS / 不计 FAIL，需人工确认该线是否会写 crash-reports）")
                return
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
            # pwsh：mixin 子命令的默认窗口是 launch（Mixin 应用在启动期；跟随 case 窗口会退化恒真）
            scope = self._assert_scope(a, "launch")
            if scope is None:
                return
            src = a.get("source", "latest")
            offset, degraded, unclear = self._window_offset(src, scope)
            text = read_log(self.run_dir, src, offset)
            bad = re.search(r"(?i)(Mixin apply failed|InvalidInjectionException|MixinTransformerError|Attach error)", text)
            ok = bad is None
            detail = f"mixin window={scope}" + (f"；{degraded}" if degraded else "")
            if degraded:
                self.warnings.append(f"断言 {label}：{degraded}")
            if ok and unclear:
                # 窗口缺内容 ⇒「无 Mixin 失败」只是弱结论（pwsh：MT_ASSERT_MIXIN: INCONCLUSIVE(弱通过)）
                self.inconclusive.append(f"mixin {label}：{unclear}")
                self.say(f"MT_ASSERT_MIXIN: INCONCLUSIVE(弱通过) — 窗口不可判（{unclear}）"
                         f"⇒ 无 Mixin 失败为弱结论")
                return
            self.say(f"      assert mixin: {'PASS' if ok else 'FAIL ' + (bad.group(0) if bad else '')} ({detail})")
            if not ok:
                self.failures.append(f"断言 mixin：日志出现 mixin 注入失败（{detail}）")
        elif t == "jar":
            self._assert_jar(a, label)
        elif t in ("bytecode", "bytecode_absent"):
            self._assert_bytecode(a, label, t)
        elif t == "vision":
            self.say("      assert vision: BLOCKED（python 侧无截图语义判定通道）")
            self.blocked.append(f"断言 vision（{a.get('image','')}）在 python 侧无等价通道")
        else:
            self.blocked.append(f"断言 {label}：未知断言类型 {t!r}（不静默通过）")

    def _assert_jar(self, a: dict, label):
        key = a.get("from") or "jar"
        st = self.vars.get(key)
        if not isinstance(st, dict):
            self.failures.append(f"断言 {label}：jar-state-missing（用例里需先有 op=jar_stats 存成 '{key}'）")
            return
        unknown = set(a) - {"type", "from", "scope", "name"} - JAR_CRITERIA
        if unknown:
            self.blocked.append(f"断言 {label}：jar 断言字段未实现 {sorted(unknown)}（不静默通过）")
            return
        crit = {k: v for k, v in a.items() if k in JAR_CRITERIA}
        if not crit:
            self.blocked.append(f"断言 {label}：jar 断言没有任何阈值字段（拒绝无判据放行）")
            return
        good, bad_parts = [], []
        for field, want in crit.items():
            if field in JAR_MIN_MAP:
                real = JAR_MIN_MAP[field]
                have, need = int(st.get(real, 0)), int(want)
                (good if have >= need else bad_parts).append(f"{real}={have}{'>=' if have >= need else '<'}{need}")
            elif field in JAR_MAX_MAP:
                real = JAR_MAX_MAP[field]
                have, cap = int(st.get(real, 0)), int(want)
                (good if have <= cap else bad_parts).append(f"{real}={have}{'<=' if have <= cap else '>'}{cap}")
            else:  # embed_lib
                have, tout = bool(st.get("embed", 0)), bool(want)
                (good if have == tout else bad_parts).append(f"embed_lib={int(have)} != {int(tout)}")
        detail = f"jar={st.get('jar','?')} " + " ".join(good + bad_parts)
        ok = not bad_parts
        self.say(f"      assert jar {label}: {'PASS' if ok else 'FAIL'} {detail}".rstrip())
        if not ok:
            self.failures.append(f"断言 {label}：{detail}")

    def _assert_bytecode(self, a: dict, label, t: str):
        pattern = a.get("pattern", "")
        libs = mt_paths.libs_dir(self.version, self.repo_root)
        path, names = mt_paths.jar_entries(self.version, self.repo_root)
        if path is None:
            self.failures.append(f"断言 {label}：{t}:no-jar（先跑 --phase build；{libs} 下无 astral_dice-*.jar）")
            return
        try:
            hits = [n for n in names if re.search(pattern, n)]
        except re.error as exc:
            self.failures.append(f"断言 {label}：pattern 非法 /{pattern}/：{exc}")
            return
        ok = bool(hits) if t == "bytecode" else not hits
        detail = f"jar={os.path.basename(path)} pattern={pattern} hits={len(hits)}"
        if hits:
            detail += " [" + ", ".join(hits[:3]) + "]"
        self.say(f"      assert {t} {label}: {'PASS' if ok else 'FAIL'} {detail}")
        if not ok:
            self.failures.append(f"断言 {label}：{t} {detail}")

    def _window_offset(self, source: str, scope: str) -> tuple[int, str, str]:
        """返回 (起始字节偏移, 退化告警, 不可判原因)。

        不可判(w) 对应 pwsh Get-MtWindowFidelity 的可证缺口：
          · 请求起点 > 当前文件长度 ⇒ 起点被 clamp 到 0，起点之前的内容确定丢失（anchor-overrun）。
        不可判 ≠ FAIL 也 ≠ PASS：由调用方记 INCONCLUSIVE（见 mt_assert.ps1 的 INCONCLUSIVE 通道）。
        """
        path = log_path(self.run_dir, source)
        if scope == "whole":
            return 0, "", ""
        try:
            size = os.path.getsize(path)
        except OSError:
            size = 0
        base, degraded = self.log_offsets.get(path, 0), ""
        if scope == "launch":
            name = os.path.basename(path)
            if name in self.launch_offsets:
                try:
                    base = int(self.launch_offsets[name])
                except (TypeError, ValueError):
                    base = self.log_offsets.get(path, 0)
            else:
                degraded = ("launch_offsets 缺失（快照没有本条日志 / 本轮没走 launch 阶段）"
                            "⇒ 退化为 case 窗口")
        if base > size:
            return 0, degraded, (f"anchor-overrun（请求起点 {base}B > 当前文件长度 {size}B，"
                                 f"起点被 clamp 到 0 ⇒ 起点之前的内容确定丢失）")
        return base, degraded, ""

    def _assert_scope(self, a: dict, default: str) -> str | None:
        """校验 scope；非法值 ⇒ 记 ERROR（pwsh：未知 scope 返回 ERROR），返回 None 表示已登记。"""
        scope = (a.get("scope") or default)
        scope = str(scope).strip() or default
        if scope not in WINDOW_CHOICES:
            self.errors.append(
                f"断言 {a.get('type')}：未知 scope {scope!r}（只接受 {' / '.join(WINDOW_CHOICES)}）")
            self.say(f"      assert {a.get('type')}: ERROR 未知 scope {scope!r}")
            return None
        return scope

    def _finish(self, verdict: str, code: int, detail: str) -> tuple[str, int]:
        cid = self.doc["case_id"]
        # 机器行逐字对齐 pwsh（scripts/test/mt_case.ps1:3095 `"MT_CASE_RESULT: {0} = {1}"`）：
        # 取值域只有 PASS|FAIL|ERROR|BLOCKED|TIMEOUT，详情另起一行业，绝不混进取值域。
        self.say(f"MT_CASE_RESULT: {cid} = {verdict}")
        if detail:
            self.say(f"MT_CASE_DETAIL: {cid} — {detail}")
        # 通道降级计数（契约 §1.3）：另起一行，绝不混进 MT_CASE_RESULT 的取值域
        if self.delegated_steps > 0:
            self.say(f"MT_CHANNEL: delegated_count={self.delegated_steps}")
        rec = {
            "case_id": cid, "verdict": verdict, "detail": detail,
            "channel": self.doc.get("channel", "kubejs"),
            "delegated_steps": self.delegated_steps,
            "inconclusive": self.inconclusive,
            "at": datetime.now().isoformat(timespec="seconds"),
            "steps": self.records, "failures": self.failures, "errors": self.errors,
            "blocked": self.blocked, "warnings": self.warnings,
        }
        try:
            os.makedirs(self.results_dir, exist_ok=True)
            with open(os.path.join(self.results_dir, f"{cid}.json"), "w", encoding="utf-8") as fh:
                json.dump(rec, fh, ensure_ascii=False, indent=1)
        except OSError as exc:
            print(f"MT_CASE_WARN: 结果落盘失败 {self.results_dir}：{exc}", file=sys.stderr, flush=True)
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
    ap = argparse.ArgumentParser(description="AutoJS 用例执行器（bash/python 原生）")
    sub = ap.add_subparsers(dest="cmd", required=True)
    v = sub.add_parser("validate")
    v.add_argument("--cases-dir", default="scripts/test/cases")
    v.add_argument("--version", default=None)
    v.add_argument("--require-judgment", action="store_true",
                   help="缺少明确判据（带 expect 的 eval_js/await_state 或结构化 assert）即报错")
    au = sub.add_parser("audit")
    au.add_argument("--cases-dir", default="scripts/test/cases")
    au.add_argument("--version", default=None)
    au.add_argument("--list", action="store_true")
    r = sub.add_parser("run")
    r.add_argument("--case", default=None)
    r.add_argument("--cases-dir", default="scripts/test/cases")
    r.add_argument("--version", default=None)
    r.add_argument("--run-dir", required=True)
    r.add_argument("--endpoint", default=None)
    r.add_argument("--case-timeout", type=float, default=600.0)
    r.add_argument("--stop-on-fail", action="store_true")
    r.add_argument("--channel", default=None, choices=["kubejs", "autojs"],
                   help="命令行覆盖用例 JSON 的 channel（与 ps1 同义；不给则按用例自身 channel）")
    r.add_argument("--allow-auto", action="store_true",
                   help="兼容 ps1 的批量跑闸门标志；python 侧闸门在 mt.sh，本参数仅接受、不改变行为")
    r.add_argument("--repo-root", default=None, help="仓库根（默认由本文件位置推断）")
    r.add_argument("--results-dir", default=None, help="结果落盘目录（默认 scripts/test/cases/.mt_results）")
    a = ap.parse_args()

    files = load_cases(a)
    if a.cmd == "audit":
        rows = []
        for f in files:
            try:
                doc = json.load(open(f, encoding="utf-8"))
            except (json.JSONDecodeError, OSError) as exc:
                rows.append((f, "BROKEN", f"JSON 解析失败 {exc}"))
                continue
            try:
                if not isinstance(doc, dict) or "steps" not in doc:
                    rows.append((f, "SKIP", "非用例文件"))
                    continue
                has, kind = has_judgment(doc)
                steps = doc.get("steps") or []
                if not isinstance(steps, list):
                    steps = []
                steps = [s for s in steps if isinstance(s, dict)]
                vision = sum(1 for s in steps if s.get("op") == "assert" and s.get("type") == "vision")
                waits = sum(1 for s in steps if s.get("op") == "wait")
                evals = sum(1 for s in steps if s.get("op") in JUDGMENT_OPS)
                rows.append((doc.get("case_id", f), "JUDGED" if has else "NO-JUDGMENT",
                             f"evals={evals} waits={waits} vision={vision} {kind}".strip()))
            except Exception as exc:  # noqa: BLE001 — audit 绝不允许抛异常
                rows.append((f, "BROKEN", f"审计失败 {exc}"))
        if a.list:
            for cid, st, det in rows:
                print(f"MT_JUDGMENT_ITEM: {st} {cid} — {det}")
        judged = sum(1 for r in rows if r[1] == "JUDGED")
        usable = sum(1 for r in rows if r[1] in ("JUDGED", "NO-JUDGMENT"))
        vision_files = sum(1 for r in rows if "vision=" in r[2] and not r[2].split("vision=")[1].startswith("0"))
        waits_total = sum(int(r[2].split("waits=")[1].split()[0]) for r in rows if "waits=" in r[2])
        print(f"MT_JUDGMENT: files={usable} with_judgment={judged} without={usable - judged} "
              f"vision_files={vision_files} fixed_wait_steps={waits_total}")
        return 0 if judged == usable else 1
    if a.cmd == "validate":
        bad = 0
        judged = 0
        for f in files:
            try:
                doc = json.load(open(f, encoding="utf-8"))
            except (json.JSONDecodeError, OSError) as exc:
                print(f"MT_CASE_VALIDATE: ERROR {f} — JSON 解析失败 {exc}")
                bad += 1
                continue
            try:
                errs = validate_case_strict(doc, f, require_judgment=getattr(a, "require_judgment", False))
            except Exception as exc:  # noqa: BLE001 — 校验器自身异常也算 error，不许炸掉整轮
                errs = [f"{f}: 校验器异常 {exc}"]
            if errs:
                bad += 1
                for e in errs:
                    print("MT_CASE_VALIDATE: ERROR " + e)
            if isinstance(doc, dict) and "steps" in doc and has_judgment(doc)[0]:
                judged += 1
        print(f"MT_JUDGMENT: with_judgment={judged} without={max(0, len(files) - judged)}")
        print(f"MT_CASE_VALIDATE: files={len(files)} errors={bad}")
        return 0 if bad == 0 else 2

    results = []
    for f in files:
        doc = json.load(open(f, encoding="utf-8"))
        # 命令行 --channel 覆盖用例 JSON（ps1 同义：用例写成 kubejs 时也能被强行拉到 autojs 通道）
        if getattr(a, "channel", None):
            declared = doc.get("channel", "kubejs") if isinstance(doc, dict) else "kubejs"
            if declared != a.channel:
                print(f"MT_CASE_CHANNEL: {doc.get('case_id', f) if isinstance(doc, dict) else f}"
                      f" 命令行覆盖 channel {declared} → {a.channel}")
                doc = dict(doc)
                doc["channel"] = a.channel
        errs = validate_case(doc, f)
        if errs:
            print("MT_CASE_VALIDATE: ERROR " + "; ".join(errs[:3]))
            results.append((doc.get("case_id", f) if isinstance(doc, dict) else f, "ERROR", 2))
            if a.stop_on_fail:
                break
            continue
        runner = Runner(doc, a.run_dir, a.endpoint, a.case_timeout,
                        repo_root=getattr(a, "repo_root", None),
                        results_dir=getattr(a, "results_dir", None),
                        cases_dir=getattr(a, "cases_dir", None))
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
