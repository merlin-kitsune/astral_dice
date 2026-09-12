#!/usr/bin/env python3
"""mt_report — 证据收集与报告生成（阶段 R）。

产物:
  reports/<run_id>/<version>/report.md    单版本报告（含明确的通过/失败结论）
  reports/<run_id>/SUMMARY.md             总览（1.21.1 与 1.20.1 各自结论 + 综合结论）

用法:
  mt_report.py mark    --version 1.21.1 --phase env --result PASS
  mt_report.py mark    --version 1.21.1 --case TC1 --result PASS
  mt_report.py collect --version 1.21.1 --verdict PASS
  mt_report.py summary
"""
from __future__ import annotations

import argparse
import json
import shutil
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent / "lib"))
from mt_paths import TEST_DIR, VERSIONS, Paths, active_run_id, run_start_ts  # noqa: E402

STATE_FILE = TEST_DIR / "cases" / ".mt_run_state.json"


def load_state() -> dict:
    if STATE_FILE.is_file():
        try:
            return json.loads(STATE_FILE.read_text(encoding="utf-8"))
        except (json.JSONDecodeError, OSError):
            pass
    return {"run_id": active_run_id(), "started": time.time(), "versions": {}}


def save_state(state: dict) -> None:
    STATE_FILE.parent.mkdir(parents=True, exist_ok=True)
    STATE_FILE.write_text(json.dumps(state, ensure_ascii=False, indent=2), encoding="utf-8")


def cmd_mark(args) -> int:
    state = load_state()
    v = state.setdefault("versions", {}).setdefault(args.version, {"phases": {}, "cases": {}})
    if args.phase:
        v["phases"][args.phase] = {"result": args.result, "ts": time.time()}
    if args.case:
        v["cases"][args.case] = args.result
    save_state(state)
    print(f"MT_MARK: {args.version} "
          f"{'phase=' + args.phase if args.phase else 'case=' + args.case} → {args.result}")
    return 0


def _copy_evidence(p: Paths, dest: Path) -> list[str]:
    dest.mkdir(parents=True, exist_ok=True)
    copied: list[str] = []
    for src, name in ((p.latest_log, "latest.log"), (p.debug_log, "debug.log"),
                      (p.kubejs_log, "kubejs_server.log"),
                      (p.run_dir / "runclient_launch.log", "runclient_launch.log")):
        if src.is_file():
            try:
                shutil.copy2(src, dest / name)
                copied.append(name)
            except OSError:
                pass
    if p.crash_dir.is_dir():
        for f in p.crash_dir.glob("*.txt"):
            shutil.copy2(f, dest / f"crash_{f.name}")
            copied.append(f"crash_{f.name}")
    run_id = active_run_id()
    shots = p.current_shots(run_id)
    if shots:
        sd = dest / "screenshots"
        sd.mkdir(exist_ok=True)
        for s in shots:
            shutil.copy2(s, sd / s.name)
        copied.append(f"screenshots/{len(shots)} 张（当前世代）")
    return copied


def _delta(p: Paths) -> tuple[str, int]:
    """返回 (增量文本, 起始偏移)。与 mt_assert 同源，保证报告与断言看到同一区间。"""
    try:
        import json as _json
        snap_file = TEST_DIR / "cases" / ".mt_snapshot.json"
        snap = _json.loads(snap_file.read_text(encoding="utf-8")) if snap_file.is_file() else {}
        offset = snap.get("versions", {}).get(p.version, {}).get("offsets", {}).get("debug.log", 0)
    except (OSError, ValueError):
        offset = 0
    log = p.debug_log if p.debug_log.is_file() else p.latest_log
    if not log.is_file():
        return "", 0
    if offset > log.stat().st_size:
        offset = 0
    with log.open("r", encoding="utf-8", errors="ignore") as f:
        f.seek(offset)
        return f.read(), offset


def _log_markers(p: Paths) -> tuple[list[str], int]:
    """只列增量区间内的关键标记（不复用上一世代内容）。"""
    text, offset = _delta(p)
    pats = ("Astral Dice", "TargetSelection", "TS_COUNT", "TS_PRESENT", "SLOTCHECK")
    hits = [ln.strip()[:200] for ln in text.splitlines() if any(k in ln for k in pats)]
    return hits[-60:], offset


def _crash_split(p: Paths) -> tuple[list[str], list[str]]:
    """区分「基线（本次运行之前就存在）」与「本次新增」崩溃报告。

    判据是双重的：既看快照基线，也看文件是否晚于本次运行开始时间。
    只靠基线不够——独立执行 collect（没有快照点）时，上一世代的旧崩溃报告
    会被误报成「本次新增」，那是会误导结论的假证据。
    """
    if not p.crash_dir.is_dir():
        return [], []
    try:
        snap_file = TEST_DIR / "cases" / ".mt_snapshot.json"
        snap = json.loads(snap_file.read_text(encoding="utf-8")) if snap_file.is_file() else {}
        baseline = set(snap.get("versions", {}).get(p.version, {}).get("crash_baseline", []))
    except (OSError, ValueError):
        baseline = set()

    run_start = run_start_ts(active_run_id())
    old: list[str] = []
    new: list[str] = []
    for f in sorted(p.crash_dir.glob("*.txt")):
        try:
            fresh = f.stat().st_mtime >= run_start
        except OSError:
            fresh = False
        (new if fresh and f.name not in baseline else old).append(f.name)
    return old, new


def cmd_collect(args) -> int:
    p = Paths(args.version)
    run_id = active_run_id()
    state = load_state()
    vs = state.get("versions", {}).get(args.version, {})

    dest = TEST_DIR / "reports" / run_id / args.version
    copied = _copy_evidence(p, dest)
    markers, offset = _log_markers(p)
    baseline_crashes, new_crashes = _crash_split(p)

    phases = vs.get("phases", {})
    cases = vs.get("cases", {})
    verdict = args.verdict or ("PASS" if all(
        r == "PASS" for r in cases.values()) and cases else "FAIL")

    lines = [
        f"# 自动化测试报告 — {args.version}",
        "",
        f"- **运行**: `{run_id}`",
        f"- **版本/加载器**: {args.version} / {p.loader}（子项目 `{p.subproject}`）",
        f"- **分支**: multi-1.20.1-1.21.1",
        f"- **环境**: `{p.run_dir}`（dev 本体，quickplay={p.client_world.name}）",
        f"- **生成时间**: {time.strftime('%Y-%m-%d %H:%M:%S')}",
        "",
        "## 结论",
        "",
        f"**{args.version}: {'✅ PASS（通过）' if verdict == 'PASS' else '❌ FAIL（未通过）'}**",
        "",
        "## 阶段结果",
        "",
        "| 阶段 | 结果 |",
        "|---|---|",
    ]
    for name in ("preflight", "build", "env", "launch", "cases", "report"):
        r = phases.get(name, {}).get("result", "未执行")
        lines.append(f"| {name} | {r} |")

    lines += ["", "## 条目结果", "", "| 条目 | 结果 |", "|---|---|"]
    if cases:
        for k, r in cases.items():
            lines.append(f"| {k} | {r} |")
    else:
        lines.append("| （无） | — |")

    lines += ["", "## 关键日志标记", "",
              f"（区间：{p.debug_log.name}@{offset}B 之后，即本次运行新增部分）", "", "```"]
    lines += markers or ["（无）"]
    lines += ["```", "", "## 崩溃 / 异常", ""]
    if new_crashes:
        lines.append(f"- **本次新增崩溃报告 {len(new_crashes)} 个**：{', '.join(new_crashes)}")
    else:
        lines.append("- 本次新增崩溃报告: 无")
    if baseline_crashes:
        lines.append(f"- 基线（本次运行之前已存在，不计入判定）{len(baseline_crashes)} 个："
                     f"{', '.join(baseline_crashes)}")
    lines.append(f"- kubejs server.log: {'存在' if p.kubejs_log.is_file() else '缺失'}")

    lines += ["", "## 证据", ""]
    lines += [f"- `{c}`" for c in copied] or ["- （无）"]

    lines += ["", "## 复跑命令", "", "```bash"]
    lines.append(f"bash scripts/test/mt.sh --version {args.version} "
                 f"--case cases/<条目>.json")
    lines.append("```")

    (dest / "report.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"MT_REPORT: OK — {dest / 'report.md'}（结论 {verdict}）")
    return 0


def cmd_summary(args) -> int:
    run_id = active_run_id()
    state = load_state()
    dest = TEST_DIR / "reports" / run_id
    dest.mkdir(parents=True, exist_ok=True)

    lines = [
        "# 自动化测试总览",
        "",
        f"- **运行**: `{run_id}`",
        f"- **分支**: multi-1.20.1-1.21.1",
        f"- **生成时间**: {time.strftime('%Y-%m-%d %H:%M:%S')}",
        "",
        "> 测试顺序：先 1.21.1，通过后才执行 1.20.1。两个版本都给出独立结论。",
        "",
        "## 版本结论",
        "",
        "| 顺序 | 版本 | 结论 | 条目 |",
        "|---|---|---|---|",
    ]
    overall_pass = True
    for idx, v in enumerate(VERSIONS, 1):
        vs = state.get("versions", {}).get(v, {})
        cases = vs.get("cases", {})
        if not cases:
            verdict = "未执行" if idx > 1 else "未执行"
            overall_pass = False
        else:
            bad = [k for k, r in cases.items() if r not in ("PASS", "SKIP")]
            verdict = "✅ PASS" if not bad else f"❌ FAIL（{', '.join(bad)}）"
            overall_pass = overall_pass and not bad
        lines.append(f"| {idx} | {v} | {verdict} | {len(cases)} |")

    lines += ["", "## 综合结论", ""]
    lines.append(f"**{'✅ 全部通过' if overall_pass else '❌ 未全部通过'}**")
    lines += ["", "各版本详细报告见对应子目录 `report.md`。"]

    (dest / "SUMMARY.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"MT_REPORT: OK — {dest / 'SUMMARY.md'}")
    return 0 if overall_pass else 1


def main() -> int:
    ap = argparse.ArgumentParser(description="mt 报告")
    sub = ap.add_subparsers(dest="cmd", required=True)

    m = sub.add_parser("mark")
    m.add_argument("--version", required=True, choices=list(VERSIONS))
    m.add_argument("--phase")
    m.add_argument("--case")
    m.add_argument("--result", required=True)

    c = sub.add_parser("collect")
    c.add_argument("--version", required=True, choices=list(VERSIONS))
    c.add_argument("--verdict", choices=["PASS", "FAIL"])

    sub.add_parser("summary")

    args = ap.parse_args()
    if args.cmd == "mark":
        return cmd_mark(args)
    if args.cmd == "collect":
        return cmd_collect(args)
    return cmd_summary(args)


if __name__ == "__main__":
    raise SystemExit(main())
