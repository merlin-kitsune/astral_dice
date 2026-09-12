#!/usr/bin/env python3
"""mt_case — 测试条目执行器（阶段 C）。

设计约束（AGENTS「子技能设计」）:
  1. 原语表封闭 —— 只接受 PRIMITIVES 中列出的 op，未知 op 立即 ERROR 并拒绝执行，
     不产生「半执行」的污染状态；
  2. 需要 stdio MCP 的步骤不在本脚本内直连，改为写「待执行清单」交会话层回填
     （.mcp-pending.json ↔ .mcp-results.json）；
  3. 不做视觉判断 —— vision 断言只输出提问请求，判定交视觉模型。

用法:
  mt_case.py run      --version 1.21.1 --case cases/xxx.json
  mt_case.py run-dir  --version 1.21.1 --dir cases/
  mt_case.py validate --case cases/xxx.json
"""
from __future__ import annotations

import argparse
import json
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent / "lib"))
from mt_paths import Paths, active_run_id  # noqa: E402
from mt_ps import run_py  # noqa: E402

TEST_DIR = Path(__file__).resolve().parent
EXIT_FAIL, EXIT_ERROR, EXIT_BLOCKED = 1, 2, 11

# ── 封闭原语表 ────────────────────────────────────────────────────────────
PRIMITIVES: dict[str, set[str]] = {
    "inject_key": {"key", "hold_ms", "no_esc"},
    "inject_command": {"command", "no_esc"},
    "kubejs_reload": set(),
    "wait": {"ms"},
    "screenshot": {"tag", "mode"},
    "assert": {"type", "pattern", "source", "image", "question", "tool", "args", "expect"},
    "mcp_call": {"tool", "args", "id"},
    "note": {"text"},
}
ASSERT_TYPES = {"log", "absent", "crash", "kubejs", "mixin", "mcp", "vision"}

PENDING = TEST_DIR / "cases" / ".mcp-pending.json"
RESULTS = TEST_DIR / "cases" / ".mcp-results.json"
# 失败取证标记：存在时，流程退出清理不杀游戏客户端（见 run_case 与 mt_cleanup.py）
KEEP_ALIVE = TEST_DIR / "cases" / ".mt_keep_alive"


# ── 校验 ──────────────────────────────────────────────────────────────────
def validate(case: dict) -> list[str]:
    errs: list[str] = []
    for key in ("case_id", "title", "version", "steps"):
        if not case.get(key):
            errs.append(f"缺少必填字段 {key}")
    if case.get("version") and case["version"] not in ("1.21.1", "1.20.1"):
        errs.append(f"version 非法：{case['version']}")
    if not case.get("asserts") and not any(
        s.get("op") == "assert" for s in case.get("steps", [])
    ):
        errs.append("条目没有任何断言（asserts 或内联 assert 步骤）")
    for i, step in enumerate(case.get("steps", [])):
        op = step.get("op")
        if op not in PRIMITIVES:
            errs.append(f"步骤 {i}: 未知原语 '{op}'（不允许自造 op）")
            continue
        unknown = set(step) - {"op"} - PRIMITIVES[op]
        if unknown:
            errs.append(f"步骤 {i}: '{op}' 含非法字段 {sorted(unknown)}")
    for i, a in enumerate(case.get("asserts", [])):
        if a.get("type") not in ASSERT_TYPES:
            errs.append(f"断言 {i}: 未知类型 '{a.get('type')}'")
        if a.get("type") == "log" and not a.get("pattern"):
            errs.append(f"断言 {i}: log 断言缺少 pattern")
    return errs


# ── 原语执行 ──────────────────────────────────────────────────────────────
def _py(script: str, *args: str) -> subprocess.CompletedProcess:
    # 经 mt_ps.run_py：子进程强制 UTF-8 I/O + 父进程容错解码，
    # 保证中文输出（如断言说明）不会因区域设置差异变成静默空串。
    rc, out, err = run_py([sys.executable, str(TEST_DIR / script), *args])
    return subprocess.CompletedProcess(args, rc, out, err)


def _verdict(rc: int, r) -> tuple[str, str]:
    """断言子进程返回码 → 结果。

    mt_assert 约定:0=PASS, 1=FAIL(标记未命中/出现), 2=ERROR(环境或引擎问题,
    例如断言通道缺失、正则非法)。**必须把 2 单列成 ERROR**:若与 FAIL 混为一谈,
    「日志文件不存在」「正则写错」这类测试链故障会被显示成「被测修复无效」。
    """
    if rc == 0:
        return "PASS", _tail(r.stdout or r.stderr)
    if rc == 2:
        return "ERROR", _tail(r.stderr or r.stdout)
    return "FAIL", _tail(r.stdout or r.stderr)


def exec_assert(p: Paths, a: dict, run_id: str) -> tuple[str, str]:
    """返回 (结果, 说明)。结果 ∈ PASS/FAIL/BLOCKED/ERROR/DELEGATED。"""
    t = a["type"]
    if t == "log":
        r = _py("mt_assert.py", "log", "--version", p.version,
                "--pattern", a["pattern"], "--source", a.get("source", "latest"))
        return _verdict(r.returncode, r)
    if t == "absent":
        r = _py("mt_assert.py", "absent", "--version", p.version,
                "--pattern", a["pattern"], "--source", a.get("source", "latest"))
        return _verdict(r.returncode, r)
    if t == "crash":
        r = _py("mt_assert.py", "crash", "--version", p.version)
        return _verdict(r.returncode, r)
    if t == "kubejs":
        r = _py("mt_assert.py", "kubejs", "--version", p.version)
        if r.returncode == 11:
            return "BLOCKED", _tail(r.stderr)
        return _verdict(r.returncode, r)
    if t == "mixin":
        r = _py("mt_assert.py", "mixin", "--version", p.version)
        return _verdict(r.returncode, r)
    if t == "mcp":
        res = _read_mcp_results().get(a.get("id", ""))
        if res is None:
            return "BLOCKED", f"会话层未回填 MCP 结果（id={a.get('id')}）"
        text = str(res.get("text", ""))
        if res.get("isError"):
            return "FAIL", text[:200]
        if a.get("expect") and a["expect"] not in text:
            return "FAIL", f"期望包含 {a['expect']!r}，实际 {text[:120]!r}"
        return "PASS", text[:200]
    if t == "vision":
        # image 可以是「截图文件名」,也可以是截图 op 登记过的 **tag** —— 用例普遍写 tag,
        # 早期实现直接拿 tag 去和文件名比对,导致所有 vision 断言恒为 BLOCKED(已在
        # 2026-09-12 真机暴露)。此处统一解析成当前世代内的真实文件,并把绝对路径
        # 回填进 detail,判定方才拿得到可读的图。
        want = a.get("image")
        names = {s.name for s in p.current_shots(run_id)}
        by_tag: dict[str, str] = {}
        for s in p.read_shots().get("shots", []):
            if s.get("tag") and s.get("file"):
                by_tag[s["tag"]] = s["file"]          # 清单按时间追加,后者覆盖前者 = 最新
        target = want if want in names else None
        if target is None and want in by_tag and by_tag[want] in names:
            target = by_tag[want]
        if want and target is None:
            return "BLOCKED", (f"截图 {want!r} 不属于当前世代 "
                               f"(世代内: {sorted(names)}, tag 表: {sorted(by_tag)})")
        if target is None:
            return "BLOCKED", "vision 断言未指定 image"
        return "DELEGATED", (f"VISION_PATH={p.shot_dir / target} | "
                             + a.get("question", "请判定截图中的视觉断言是否成立"))
    return "ERROR", f"未实现的断言类型 {t}"


def _tail(text: str, n: int = 240) -> str:
    return (text or "").strip().replace("\n", " ")[:n]


def _read_mcp_results() -> dict:
    if not RESULTS.is_file():
        return {}
    try:
        data = json.loads(RESULTS.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, OSError):
        return {}
    return {item["id"]: item for item in data}


def exec_op(p: Paths, step: dict, run_id: str, results: list[dict]) -> tuple[str, str]:
    op = step["op"]

    if op == "inject_key":
        args = ["key", "--key", step["key"], "--version", p.version]
        if step.get("hold_ms"):
            args += ["--hold-ms", str(step["hold_ms"])]
        r = _py("mt_inject.py", *args)
        return ("PASS" if r.returncode == 0 else "ERROR"), _tail(r.stdout or r.stderr)

    if op == "inject_command":
        args = ["cmd", "--command", step["command"], "--version", p.version]
        if step.get("no_esc"):
            args.append("--no-esc")
        r = _py("mt_inject.py", *args)
        time.sleep(0.6)
        return ("PASS" if r.returncode == 0 else "ERROR"), _tail(r.stdout or r.stderr)

    if op == "kubejs_reload":
        _py("mt_inject.py", "cmd", "--command", "/kubejs reload server-scripts",
            "--version", p.version)
        time.sleep(1.0)
        _py("mt_inject.py", "cmd", "--command", "/reload", "--version", p.version)
        time.sleep(2.0)
        return "PASS", "已热重载 KubeJS 脚本"

    if op == "wait":
        time.sleep(step.get("ms", 500) / 1000)
        return "PASS", f"等待 {step.get('ms', 500)}ms"

    if op == "screenshot":
        r = _py("mt_capture.py", "capture", "--version", p.version,
                "--tag", step["tag"], "--mode", step.get("mode", "f2"))
        return ("PASS" if r.returncode == 0 else "ERROR"), _tail(r.stdout or r.stderr)

    if op == "mcp_call":
        _queue_mcp(step)
        res = _await_mcp(step.get("id", ""), timeout=step.get("timeout", 120))
        if res is None:
            return "BLOCKED", f"MCP 握手超时（id={step.get('id')}）"
        return ("PASS" if not res.get("isError") else "FAIL"), _tail(str(res.get("text", "")))

    if op == "assert":
        return exec_assert(p, step, run_id)

    if op == "note":
        return "NOTE", step.get("text", "")

    return "ERROR", f"未知原语 {op}"


def _queue_mcp(step: dict) -> None:
    PENDING.parent.mkdir(parents=True, exist_ok=True)
    data = []
    if PENDING.is_file():
        try:
            data = json.loads(PENDING.read_text(encoding="utf-8"))
        except (json.JSONDecodeError, OSError):
            data = []
    data.append({"id": step.get("id") or f"m{len(data) + 1}",
                 "tool": step["tool"], "args": step.get("args", {})})
    PENDING.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"MT_MCP_PENDING: 已登记 {data[-1]['id']} → {step['tool']}"
          f"（等待会话层回填 {RESULTS.name}）")


def _await_mcp(key: str, timeout: float) -> dict | None:
    deadline = time.time() + timeout
    while time.time() < deadline:
        if RESULTS.is_file():
            res = _read_mcp_results().get(key)
            if res is not None:
                return res
        time.sleep(1.0)
    return None


# ── 条目执行 ──────────────────────────────────────────────────────────────
# ── 入口路径解析 ──────────────────────────────────────────────────────────
def _resolve_case(spec: str) -> Path:
    """把条目参数解析为路径，且与调用者 cwd 无关。

    早前直接用 Path(spec)，于是 `--case cases/X.json` 只在 cwd=scripts/test 时成立，
    从仓库根调用就会「找不到条目」。解析顺序:
      1. 绝对路径 → 原样；
      2. 相对 TEST_DIR 存在（cases/X.json）→ 采用；
      3. 补 .json 后在 TEST_DIR/cases 下存在，或只是裸名（SMOKE-TOOLCHAIN）→ 采用；
      4. 否则按 cwd 解释（保留旧行为，便于传仓库内任意路径）。
    """
    p = Path(spec)
    if p.is_absolute():
        return p
    if (TEST_DIR / p).is_file():
        return TEST_DIR / p
    name = spec if spec.lower().endswith(".json") else f"{spec}.json"
    cand = TEST_DIR / "cases" / name
    if cand.is_file():
        return cand
    return p


def _resolve_dir(spec: str) -> Path:
    """--dir 同理：相对路径优先按 TEST_DIR 解释。"""
    p = Path(spec)
    if p.is_absolute():
        return p
    cand = TEST_DIR / p
    return cand if cand.is_dir() else p


def run_case(version: str, case_path: Path, run_id: str) -> tuple[str, list[dict]]:
    try:
        case = json.loads(case_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        print(f"MT_CASE: ERROR — 无法读取 {case_path}：{exc}", file=sys.stderr)
        return "ERROR", []
    errs = validate(case)
    if errs:
        print(f"MT_CASE: ERROR — {case_path.name} 校验失败：", file=sys.stderr)
        for e in errs:
            print(f"    {e}", file=sys.stderr)
        return "ERROR", []

    if case["version"] != version:
        print(f"MT_CASE: SKIP — {case['case_id']} 面向 {case['version']}，跳过 {version}")
        return "SKIP", []

    p = Paths(version)
    print(f"\n--- MT_CASE: {case['case_id']} — {case['title']} ---")
    steps: list[dict] = []
    if case.get("fixtures", {}).get("kubejs"):
        steps.append({"op": "kubejs_reload"})
    steps += case.get("steps", [])
    steps += [{"op": "assert", **a} for a in case.get("asserts", [])]

    timeline: list[dict] = []
    worst = "PASS"
    for i, step in enumerate(steps, 1):
        t0 = time.time()
        outcome, detail = exec_op(p, step, run_id, timeline)
        rec = {"i": i, "op": step["op"], "outcome": outcome,
               "detail": detail, "secs": round(time.time() - t0, 1)}
        timeline.append(rec)
        mark = {"PASS": "PASS", "FAIL": "FAIL", "BLOCKED": "BLOCK", "ERROR": "ERROR",
                "SKIP": "SKIP", "NOTE": "note", "DELEGATED": "→VIS"}.get(outcome, outcome)
        print(f"  [{mark:>5}] {i:>2}. {step['op']}"
              f"{' ' + str(step.get('key') or step.get('command') or step.get('tag') or '')}"
              f" — {detail}")
        if outcome == "ERROR":
            worst = "ERROR"
        elif outcome == "BLOCKED" and worst in ("PASS", "SKIP"):
            worst = "BLOCKED"
        elif outcome == "FAIL" and worst != "ERROR":
            worst = "FAIL"

    print(f"MT_CASE_RESULT: {case['case_id']} = {worst}")

    # on_fail=keep_game_running：失败时保留游戏现场供取证。
    # 落一个标记文件，供流程退出清理判断 —— 否则「自动杀进程」会在失败瞬间
    # 把现场销毁，让取证变成不可能。
    if worst in ("FAIL", "ERROR") and case.get("on_fail") == "keep_game_running":
        KEEP_ALIVE.parent.mkdir(parents=True, exist_ok=True)
        KEEP_ALIVE.write_text(
            f"{case['case_id']} ({worst}) @ {time.strftime('%Y-%m-%d %H:%M:%S')}\n",
            encoding="utf-8",
        )
        print(f"MT_KEEP_ALIVE: 已置位（{case['case_id']} 失败，退出时保留游戏现场）"
              f" —— 取证完成后执行 bash scripts/test/mt.sh --phase stop 收停")

    return worst, timeline


def _case_files(directory: Path) -> list[Path]:
    return sorted(directory.glob("*.json"))


def main() -> int:
    ap = argparse.ArgumentParser(description="mt 条目执行器")
    sub = ap.add_subparsers(dest="cmd", required=True)

    r = sub.add_parser("run")
    r.add_argument("--version", required=True, choices=["1.21.1", "1.20.1"])
    r.add_argument("--case", required=True)

    rd = sub.add_parser("run-dir")
    rd.add_argument("--version", required=True, choices=["1.21.1", "1.20.1"])
    rd.add_argument("--dir", default=str(TEST_DIR / "cases"))

    v = sub.add_parser("validate")
    v.add_argument("--case", required=True)

    args = ap.parse_args()

    if args.cmd == "validate":
        try:
            case = json.loads(_resolve_case(args.case).read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exc:
            print(f"MT_VALIDATE: ERROR — 无法读取 {args.case}：{exc}", file=sys.stderr)
            return EXIT_ERROR
        errs = validate(case)
        if errs:
            print(f"MT_VALIDATE: FAIL — {len(errs)} 项")
            for e in errs:
                print(f"    {e}")
            return EXIT_FAIL
        print(f"MT_VALIDATE: OK — {case['case_id']}")
        return 0

    run_id = active_run_id()
    files = ([_resolve_case(args.case)] if args.cmd == "run"
             else _case_files(_resolve_dir(args.dir)))
    if not files:
        print("MT_CASE: 无条目文件", file=sys.stderr)
        return EXIT_BLOCKED

    summary: list[tuple[str, str]] = []
    worst = "PASS"
    for f in files:
        try:
            outcome, _ = run_case(args.version, f, run_id)
        except (json.JSONDecodeError, OSError) as exc:
            print(f"MT_CASE: ERROR — {f.name}: {exc}", file=sys.stderr)
            outcome = "ERROR"
        summary.append((f.stem, outcome))
        if outcome == "ERROR":
            worst = "ERROR"
        elif outcome == "FAIL" and worst != "ERROR":
            worst = "FAIL"
        elif outcome == "BLOCKED" and worst in ("PASS", "SKIP"):
            worst = "BLOCKED"

    print(f"\nMT_CASES_SUMMARY: {', '.join(f'{k}={v}' for k, v in summary)}")
    return {"PASS": 0, "SKIP": 0, "FAIL": EXIT_FAIL,
            "BLOCKED": EXIT_BLOCKED, "ERROR": EXIT_ERROR}[worst]


if __name__ == "__main__":
    raise SystemExit(main())
