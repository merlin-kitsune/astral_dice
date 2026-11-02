#!/usr/bin/env python3
"""mt_report —— 证据收集与报告生成（阶段 R）。

scripts/test/mt_report.ps1 的 python 原生等价实现（不启动 pwsh / powershell）。

子命令与 CLI 形态（与 ps1 逐字一致）:
    mt_report.py mark    --version <v> [--phase <name>] [--case <name>] --result <R>
    mt_report.py collect --version <v> [--verdict <R>]
    mt_report.py summary

产物:
    reports/<run_id>/<version>/report.md   单版本报告（含明确的通过/失败结论）
    reports/<run_id>/SUMMARY.md            总览（版本结论 + 综合结论）

退出码（Mt.Phase.psm1）: PASS=0 FAIL=1 ERROR=2 PREFLIGHT=10 BLOCKED=11 TIMEOUT=12
    mark    -> 0
    collect -> 0 / 2（无效运行 = 规则 14；生物 AI 审计不达标）
    summary -> 0 / 1（未全部通过）/ 2（无效运行）

刻意偏差（逐条在交付说明里列明）:
    --help / -h 打印用法并退出 0（ps1 无此开关；phases.sh 从不传它）。
"""
from __future__ import annotations

import json
import os
import re
import shutil
import subprocess
import sys
import time

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
LIB_DIR = os.path.join(SCRIPT_DIR, "lib")
if LIB_DIR not in sys.path:
    sys.path.insert(0, LIB_DIR)

import mt_compat as C  # noqa: E402

NL = C.NL
CR = C.CR

TEST_DIR = C.TEST_DIR
CASES_DIR = os.path.join(TEST_DIR, "cases")
STATE_FILE = os.path.join(CASES_DIR, ".mt_run_state.json")
SNAPSHOT_FILE = os.path.join(CASES_DIR, ".mt_snapshot.json")
AUTOJS_FILE = os.path.join(CASES_DIR, ".mt_autojs.json")
REPORTS_DIR = os.path.join(TEST_DIR, "reports")
BRANCH_FALLBACK = "unknown"
ROUND_HISTORY_MAX = 10

MARKER_KEYS = ("Astral Dice", "TargetSelection", "TS_COUNT", "TS_PRESENT", "SLOTCHECK")
STAGE_ORDER = ("preflight", "build", "env", "launch", "cases", "report")

NOAI_BEAT_RX = re.compile(
    "AP_NOAI:mobs=([0-9]+):noai=([0-9]+):radius=([0-9]+):forced=([0-9]+):total=([0-9]+)"
)
NOAI_FORCED_RX = re.compile("AP_NOAI_FORCED:new=([0-9]+):mobs=([0-9]+):noai=([0-9]+):total=([0-9]+)")


# ── 元数据 ─────────────────────────────────────────────────────────────────
def branch_name():
    try:
        result = subprocess.run(
            ["git", "-C", C.ROOT, "rev-parse", "--abbrev-ref", "HEAD"],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=30,
        )
        if result.returncode == 0:
            name = result.stdout.decode("utf-8", "replace").strip()
            if name:
                return name
    except (OSError, subprocess.SubprocessError):
        pass
    return BRANCH_FALLBACK


def now_stamp():
    return time.strftime("%Y-%m-%d %H:%M:%S", time.localtime())


def now_ms():
    return int(time.time() * 1000)


def fmt_value(value):
    if value is None:
        return ""
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, float) and value.is_integer():
        return str(int(value))
    return str(value)


# ── 状态文件（跨语言契约：phases.<n>.result/ts、cases.<n>=结果串） ─────────
def load_state():
    if os.path.isfile(STATE_FILE):
        try:
            with open(STATE_FILE, "rb") as fh:
                parsed = json.loads(fh.read().decode("utf-8-sig", "replace"))
            if isinstance(parsed, dict):
                return parsed
        except (OSError, ValueError):
            pass
    return {"run_id": C.active_run_id(), "started": now_ms() / 1000.0, "versions": {}}


def save_state(state):
    parent = os.path.dirname(STATE_FILE)
    if parent and not os.path.isdir(parent):
        os.makedirs(parent, exist_ok=True)
    with open(STATE_FILE, "w", encoding="utf-8", newline="") as fh:
        fh.write(C.json_dump(state))


def load_snapshot():
    if os.path.isfile(SNAPSHOT_FILE):
        try:
            with open(SNAPSHOT_FILE, "rb") as fh:
                parsed = json.loads(fh.read().decode("utf-8-sig", "replace"))
            if isinstance(parsed, dict):
                return parsed
        except (OSError, ValueError):
            pass
    return {}


def round_mark_count(versions):
    if not isinstance(versions, dict):
        return 0
    total = 0
    for vs in versions.values():
        if not isinstance(vs, dict):
            continue
        phases = vs.get("phases")
        cases = vs.get("cases")
        if isinstance(phases, dict):
            total += len(phases)
        if isinstance(cases, dict):
            total += len(cases)
    return total


def start_round(state, reason):
    history = state.get("history")
    if not isinstance(history, list):
        history = [] if history is None else list(history)
    archived_gen = state.get("gen") or ""
    archived_marks = 0
    versions = state.get("versions")
    if isinstance(versions, dict) and versions:
        archived_marks = round_mark_count(versions)
        started = state.get("gen_started")
        if started is None:
            started = state.get("started")
        closed = state.get("closed_at")
        history = history + [
            {
                "gen": archived_gen,
                "started": started,
                "closed_at": 0 if closed is None else closed,
                "reason": reason,
                "versions": versions,
            }
        ]
        if len(history) > ROUND_HISTORY_MAX:
            history = history[-ROUND_HISTORY_MAX:]
    state["history"] = history
    state["versions"] = {}
    state["gen"] = time.strftime("%Y%m%d-%H%M%S", time.localtime())
    state["gen_started"] = int(time.time())
    state["closed_at"] = 0
    if "started" not in state:
        state["started"] = state["gen_started"]
    if "run_id" not in state:
        state["run_id"] = C.active_run_id()
    return {
        "gen": state["gen"],
        "archived_gen": archived_gen,
        "archived_marks": archived_marks,
        "history_count": len(history),
    }


# ── mark ───────────────────────────────────────────────────────────────────
def invoke_mark(version, phase, case_name, result):
    state = load_state()
    if not isinstance(state.get("versions"), dict):
        state["versions"] = {}
    versions = state["versions"]

    rotate_reason = ""
    if "gen" not in state:
        rotate_reason = "状态文件无世代标记（旧口径 / 换版首跑）⇒ 历史标记无法归属，开新一轮"
    elif (state.get("closed_at") or 0) > 0:
        rotate_reason = "上一轮已由 summary 结算（closed_at=" + fmt_value(state.get("closed_at")) + "）⇒ 开新一轮"
    elif phase == "preflight" and version in versions and isinstance(versions[version], dict):
        phases_before = versions[version].get("phases")
        if isinstance(phases_before, dict) and "preflight" in phases_before:
            rotate_reason = "本版本的 preflight 在本轮已记过一次（上一轮夭折 / 同版本被重跑）⇒ 开新一轮"

    if rotate_reason:
        info = start_round(state, rotate_reason)
        C.write_mt_line(
            "MT_ROUND: 新一轮 "
            + info["gen"]
            + " — "
            + rotate_reason
            + "；已归档历史轮次 "
            + str(info["history_count"])
            + " 个 / "
            + str(info["archived_marks"])
            + " 条标记（**不计入**本次判定）"
        )

    versions = state["versions"]
    if not isinstance(versions.get(version), dict):
        versions[version] = {"phases": {}, "cases": {}}
    entry = versions[version]
    if not isinstance(entry.get("phases"), dict):
        entry["phases"] = {}
    if not isinstance(entry.get("cases"), dict):
        entry["cases"] = {}
    if phase:
        entry["phases"][phase] = {"result": result, "ts": now_ms() / 1000.0}
    if case_name:
        entry["cases"][case_name] = result
    save_state(state)

    what = ("phase=" + phase) if phase else ("case=" + case_name)
    C.write_mt_line("MT_MARK: " + version + " " + what + " → " + result)
    return 0


# ── collect 的数据源 ──────────────────────────────────────────────────────
def copy_evidence(paths, dest):
    copied = []
    if not os.path.isdir(dest):
        os.makedirs(dest, exist_ok=True)
    sources = [
        (paths.latest_log, "latest.log"),
        (paths.debug_log, "debug.log"),
        (paths.kubejs_log, "kubejs_server.log"),
        (os.path.join(paths.run_dir, "runclient_launch.log"), "runclient_launch.log"),
    ]
    for src, name in sources:
        if not os.path.isfile(src):
            continue
        try:
            shutil.copyfile(src, os.path.join(dest, name))
        except OSError:
            continue
        copied.append(name)
    if os.path.isdir(paths.crash_dir):
        for name in sorted(os.listdir(paths.crash_dir)):
            src = os.path.join(paths.crash_dir, name)
            if not name.lower().endswith(".txt") or not os.path.isfile(src):
                continue
            target = "crash_" + name
            shutil.copyfile(src, os.path.join(dest, target))
            copied.append(target)
    shots = C.current_shots(paths, C.active_run_id())
    if shots:
        shot_dest = os.path.join(dest, "screenshots")
        if not os.path.isdir(shot_dest):
            os.makedirs(shot_dest, exist_ok=True)
        for src in shots:
            shutil.copyfile(src, os.path.join(shot_dest, os.path.basename(src)))
        copied.append("screenshots/" + str(len(shots)) + " 张（当前世代）")
    return copied


def report_delta(paths):
    offset = 0
    anchor = None
    try:
        snapshot = load_snapshot()
        versions = snapshot.get("versions")
        entry = versions.get(paths.version) if isinstance(versions, dict) else None
        if isinstance(entry, dict):
            if isinstance(entry.get("launch_offsets"), dict):
                key, anchor_key = "launch_offsets", "launch_anchors"
            else:
                key, anchor_key = "offsets", "anchors"
            table = entry.get(key)
            if isinstance(table, dict) and "debug.log" in table:
                offset = int(table["debug.log"])
                anchors = entry.get(anchor_key)
                if isinstance(anchors, dict) and "debug.log" in anchors:
                    anchor = anchors["debug.log"]
    except (TypeError, ValueError, AttributeError):
        offset = 0
        anchor = None
    log = paths.debug_log if os.path.isfile(paths.debug_log) else paths.latest_log
    if not os.path.isfile(log):
        return ("", 0, "missing", "")
    anchor_for_read = anchor if os.path.basename(log) == "debug.log" else None
    window = C.read_log_window(log, offset, anchor_for_read, paths.logs_dir)
    return (window["text"], window["offset"], window["kind"], window["note"])


def log_markers(paths):
    text, offset, kind, note = report_delta(paths)
    hits = []
    for line in text.split(NL):
        line = line.rstrip(CR)
        if any(key in line for key in MARKER_KEYS):
            stripped = line.strip()
            if len(stripped) > 200:
                stripped = stripped[:200]
            hits.append(stripped)
            if len(hits) > 60:
                hits = hits[-60:]
    return (hits, offset, kind, note)


def crash_split(paths):
    if not os.path.isdir(paths.crash_dir):
        return ([], [])
    baseline = []
    try:
        snapshot = load_snapshot()
        versions = snapshot.get("versions")
        entry = versions.get(paths.version) if isinstance(versions, dict) else None
        if isinstance(entry, dict):
            raw = entry.get("crash_baseline")
            if raw is not None:
                baseline = list(raw) if isinstance(raw, (list, tuple)) else [raw]
    except (TypeError, AttributeError):
        baseline = []
    run_start = C.run_start_ts(C.active_run_id())
    old = []
    new = []
    for name in sorted(os.listdir(paths.crash_dir)):
        full = os.path.join(paths.crash_dir, name)
        if not name.lower().endswith(".txt") or not os.path.isfile(full):
            continue
        try:
            fresh = int(os.path.getmtime(full)) >= run_start
        except OSError:
            fresh = False
        if fresh and name not in baseline:
            new.append(name)
        else:
            old.append(name)
    return (old, new)


def noai_audit(paths):
    text = C.read_text_shared(paths.latest_log)
    beats = []
    forced = []
    errs = []
    for line in C.split_crlf(text):
        if "AP_NOAI:ERR:" in line:
            errs.append(line.strip())
            continue
        match = NOAI_BEAT_RX.search(line)
        if match:
            beats.append(
                {
                    "mobs": int(match.group(1)),
                    "noai": int(match.group(2)),
                    "forced": int(match.group(4)),
                    "total": int(match.group(5)),
                }
            )
            continue
        match = NOAI_FORCED_RX.search(line)
        if match:
            forced.append(
                {
                    "new": int(match.group(1)),
                    "mobs": int(match.group(2)),
                    "noai": int(match.group(3)),
                    "total": int(match.group(4)),
                }
            )
    bad = len([b for b in beats if b["mobs"] != b["noai"]])
    bad += len([f for f in forced if f["mobs"] != f["noai"]])
    return (beats, forced, errs, bad)


def report_field(mapping, key, default=None):
    if isinstance(mapping, dict):
        for name, value in mapping.items():
            if str(name) == key:
                return value
        return default
    if mapping is None:
        return default
    return default


# ── collect ───────────────────────────────────────────────────────────────
def load_autojs():
    if not os.path.isfile(AUTOJS_FILE):
        return None
    try:
        with open(AUTOJS_FILE, "rb") as fh:
            return json.loads(fh.read().decode("utf-8-sig", "replace"))
    except (OSError, ValueError):
        return None


def invoke_collect(version, verdict_arg=""):
    paths = C.get_paths(version)
    run_id = C.active_run_id()
    state = load_state()
    versions = state.get("versions")
    vs = versions.get(version) if isinstance(versions, dict) else None
    if not isinstance(vs, dict):
        vs = {}

    dest = os.path.join(REPORTS_DIR, run_id, version)
    copied = copy_evidence(paths, dest)

    markers, marker_offset, marker_window, marker_note = log_markers(paths)
    baseline_crashes, new_crashes = crash_split(paths)
    beats, forced, errs, bad = noai_audit(paths)
    with_mobs = [b for b in beats if b["mobs"] > 0]
    total = beats[-1]["total"] if beats else 0

    phases = vs.get("phases") if isinstance(vs.get("phases"), dict) else {}
    cases = vs.get("cases") if isinstance(vs.get("cases"), dict) else {}

    verdict = verdict_arg
    verdict_arg_text = "" if not verdict_arg else str(verdict_arg)
    ut_cases = sorted([str(k) for k, v in cases.items() if str(v) == "USER_TERMINATED"])
    ut_phases = sorted(
        [str(k) for k, v in phases.items() if isinstance(v, dict) and str(v.get("result")) == "USER_TERMINATED"]
    )
    invalid_run = bool(ut_cases) or bool(ut_phases)
    if invalid_run:
        verdict = "INVALID"
    elif not verdict:
        all_pass = len(cases) > 0
        for value in cases.values():
            if str(value) != "PASS":
                all_pass = False
        verdict = "PASS" if all_pass else "FAIL"

    round_gen = str(state.get("gen")) if "gen" in state else "（旧口径，未标世代）"
    history = state.get("history")
    if not isinstance(history, list):
        history = [] if history is None else list(history)
    hist_marks = 0
    for item in history:
        if isinstance(item, dict):
            hist_marks += round_mark_count(item.get("versions"))

    if ut_cases:
        ut_case_names = ", ".join(ut_cases)
    else:
        ut_case_names = "（阶段级：" + ", ".join(ut_phases) + "）"

    if verdict == "INVALID":
        verdict_text = "⛔ 无效运行（MT_USER_TERMINATED）"
    elif verdict == "PASS":
        verdict_text = "✅ PASS（通过）"
    else:
        verdict_text = "❌ FAIL（未通过）"

    lines = [
        "# 自动化测试报告 — " + version,
        "",
        "- **运行**: `" + run_id + "`",
        "- **本轮世代**: `" + round_gen + "`（条目聚合只统计本轮标记）",
        "- **历史轮次（不计入判定）**: 已归档 " + str(len(history)) + " 个 / " + str(hist_marks) + " 条标记",
        "- **版本/加载器**: " + version + " / " + paths.loader + "（子项目 `" + paths.subproject + "`）",
        "- **分支**: " + branch_name(),
        "- **环境**: `"
        + paths.run_dir
        + "`（dev 本体，quickplay="
        + os.path.basename(paths.client_world)
        + "）",
        "- **生成时间**: " + now_stamp(),
        "",
        "## 结论",
        "",
        "**" + version + ": " + verdict_text + "**",
        "",
    ]

    if invalid_run:
        lines += [
            "> ### ⛔ 无效运行（MT_USER_TERMINATED）",
            "> **本次测试结果全部无效，需重新执行测试验证。**",
            "> 依据（规则 14 收严版）：「被用户主动终止的游戏进程一律视为作废。此次测试产生的任何结果都必须视为无效。需要重新执行测试进行验证。」",
            "> 触发条目：`"
            + ut_case_names
            + "`（结论 `USER_TERMINATED` —— 疑似用户手动终止，判定见 `mt_case.ps1` 的 `MT_USER_TERMINATED`）。",
            "> 处置：**不计入通过率**、**不作为缺陷依据**，也不与产品 `❌ FAIL` 混淆；下方条目表逐条如实保留全部结果串（含其它失败串）供审计。",
            "> 退出码：沿用既有非 0 中断码 `MT_EXIT_ERROR(2)`（与用例级 `USER_TERMINATED` 同一数值，未新造）。",
            "",
        ]

    lines += ["## 阶段结果", "", "| 阶段 | 结果 |", "|---|---|"]
    for name in STAGE_ORDER:
        value = "未执行"
        entry = phases.get(name)
        if isinstance(entry, dict) and "result" in entry:
            value = str(entry["result"])
        lines.append("| " + name + " | " + value + " |")

    lines += ["", "## 条目结果", "", "| 条目 | 结果 |", "|---|---|"]
    if cases:
        for name, value in cases.items():
            lines.append("| " + str(name) + " | " + str(value) + " |")
    else:
        lines.append("| （无） | — |")

    aj_doc = load_autojs()
    aj_ver = None
    aj_cases = None
    if isinstance(aj_doc, dict):
        aj_versions = aj_doc.get("versions")
        if isinstance(aj_versions, dict) and version in aj_versions:
            aj_ver = aj_versions[version]
            aj_cases = report_field(aj_ver, "cases")

    lines += ["", "## AutoJS 通道（eval 通道证据）", ""]
    if aj_ver is None:
        lines.append("- 本轮无 AutoJS 记录（未装 autojs / 端点未就绪 / 没有 channel=autojs 的用例）")
        if os.path.isfile(AUTOJS_FILE):
            lines.append("- 记录文件存在但没有本版本段：" + AUTOJS_FILE)
    else:
        aj_url = str(report_field(aj_ver, "endpoint", "") or "")
        aj_proto = str(report_field(aj_ver, "protocol_version", "") or "")
        aj_ok = bool(report_field(aj_ver, "ok", False))
        aj_reason = str(report_field(aj_ver, "reason", "") or "")
        aj_status = report_field(aj_ver, "status")
        if not aj_url:
            aj_url = "（未记录）"
        if not aj_proto:
            aj_proto = "unknown"
        aj_ready = "OK"
        if not aj_ok:
            aj_ready = "FAILED"
            if aj_reason:
                aj_ready = "FAILED — " + aj_reason
        aj_status_text = "（未记录）"
        if aj_status is not None:
            aj_status_text = json.dumps(aj_status, ensure_ascii=False, separators=(",", ":"))
            if len(aj_status_text) > 400:
                aj_status_text = aj_status_text[:400] + "…（截断）"
        lines.append("- **端点**: " + aj_url)
        lines.append("- **protocolVersion**: " + aj_proto)
        lines.append("- **就绪**: " + aj_ready)
        lines.append("- **status 摘要**: " + aj_status_text)

        eval_steps = 0
        eval_failed = 0
        rows = []
        if isinstance(aj_cases, dict):
            for cid in sorted([str(k) for k in aj_cases.keys()]):
                record = aj_cases.get(cid)
                chan = str(report_field(record, "channel", "") or "")
                steps = int(report_field(record, "eval_steps", 0) or 0)
                failed = int(report_field(record, "eval_failed", 0) or 0)
                eval_steps += steps
                eval_failed += failed
                evals = report_field(record, "evals", [])
                if evals is None:
                    evals = []
                if not isinstance(evals, list):
                    evals = [evals]
                last = "—"
                if evals:
                    last_record = evals[-1]
                    last_outcome = str(report_field(last_record, "outcome", "") or "")
                    last_detail = str(report_field(last_record, "detail", "") or "")
                    if len(last_detail) > 160:
                        last_detail = last_detail[:160] + "…"
                    last = last_outcome
                    if last_detail:
                        last = last + " — " + last_detail
                rows.append("| " + cid + " | " + chan + " | " + str(steps) + " | " + str(failed) + " | " + last + " |")
        lines.append("- **本批 eval_js 步数 / 失败数**: " + str(eval_steps) + " / " + str(eval_failed))
        lines.append("")
        if rows:
            lines += [
                "| 用例 | 通道 | eval 步数 | 失败 | 最后一条 eval 结果 |",
                "|---|---|---|---|---|",
            ]
            lines += rows
        else:
            lines.append("- （本批没有 channel=autojs 的用例记录）")

    lines += [
        "",
        "## 关键日志标记（日志通道证据）",
        "",
        "（区间："
        + os.path.basename(paths.debug_log)
        + "@"
        + str(marker_offset)
        + "B 之后，即本次运行新增部分；窗口 "
        + marker_window
        + "）",
        "",
        "```",
    ]
    lines += markers if markers else ["（无）"]
    lines += ["```", ""]
    if marker_note:
        lines += ["- **窗口锚定**: " + marker_note, ""]

    lines += ["## 崩溃 / 异常（日志通道证据）", ""]
    if new_crashes:
        lines.append("- **本次新增崩溃报告 " + str(len(new_crashes)) + " 个**：" + ", ".join(new_crashes))
    else:
        lines.append("- 本次新增崩溃报告: 无")
    if baseline_crashes:
        lines.append(
            "- 基线（本次运行之前已存在，不计入判定）"
            + str(len(baseline_crashes))
            + " 个："
            + ", ".join(baseline_crashes)
        )
    lines.append("- kubejs server.log: " + ("存在" if os.path.isfile(paths.kubejs_log) else "缺失"))

    lines += ["", "## 生物 AI 禁用审计（测试环境硬性要求；日志通道证据）", ""]
    if not beats:
        lines.append(
            "- ❌ **未读到任何 `AP_NOAI:` 心跳** —— 脚本未同步 / 未生效（`mt_launch` 的进入世界闸门本应已拦下）"
        )
    else:
        lines.append(
            "- 心跳 "
            + str(len(beats))
            + " 行；其中 `mobs>0` 的 "
            + str(len(with_mobs))
            + " 行（半径内确实有生物）"
        )
        if forced:
            lines.append(
                "- **正面对照 ✅**：实际停住过带 AI 的自然刷怪 "
                + str(len(forced))
                + " 次，累计 `total="
                + str(total)
                + "`"
            )
        else:
            lines.append(
                "- **正面对照 ⚠️ 缺失**：本轮读数里 ``mobs`` 全为 0，未遇到带 AI 的生物 —— 只能证明「现场干净」，**不能**当作机制生效的证据"
            )
        lines.append(
            "- 心跳/对照行中 `mobs != noai` 的次数："
            + str(bad)
            + (" ❌（该半径内仍有带 AI 的 Mob）" if bad > 0 else " ✅")
        )
        if errs:
            lines.append(
                "- ❌ `AP_NOAI:ERR` 报错 " + str(len(errs)) + " 行，最后一行：`" + errs[-1] + "`"
            )

    lines += ["", "## 证据", ""]
    lines += ["- `" + item + "`" for item in copied] if copied else ["- （无）"]

    lines += ["", "## 复跑命令", "", "```bash"]
    lines.append("bash scripts/test/win/mt.sh --version " + version + " --phase cases --case scripts/test/cases/<条目>.json")
    lines.append("```")

    if not os.path.isdir(dest):
        os.makedirs(dest, exist_ok=True)
    report_path = os.path.join(dest, "report.md")
    with open(report_path, "w", encoding="utf-8", newline="") as fh:
        fh.write(NL.join(lines) + NL)
    C.write_mt_line("MT_REPORT: OK — " + report_path + "（结论 " + verdict + "）")

    if invalid_run:
        C.write_mt_line(
            "MT_INVALID_RUN: version="
            + version
            + " result=INVALID reason=USER_TERMINATED cases="
            + ut_case_names
            + " verdict_arg="
            + (verdict_arg_text if verdict_arg_text else "(none)")
            + " exit="
            + str(C.MT_EXIT_ERROR)
        )
        C.write_mt_err_line(
            "MT_INVALID_RUN: 本次测试结果全部无效，需重新执行测试验证 —— 无效运行（MT_USER_TERMINATED）："
            "不计入通过率、不作为缺陷依据、不与产品 FAIL 混淆（version=" + version + "）"
        )
        C.write_mt_line(
            "MT_NOAI_AUDIT: SKIPPED — 本轮为无效运行（MT_USER_TERMINATED）；生物 AI 审计结论一并作废，不作为缺陷依据"
        )
        return C.MT_EXIT_ERROR

    if not beats or bad > 0 or errs:
        C.write_mt_error_line(
            "MT_REPORT: ERROR — 生物 AI 禁用审计未通过（测试环境硬性要求）：心跳 "
            + str(len(beats))
            + " 行 / 失配 "
            + str(bad)
            + " 次 / AP_NOAI:ERR "
            + str(len(errs))
            + " 行；本轮读数可能已被生物 AI 污染"
        )
        return C.MT_EXIT_ERROR

    if forced:
        C.write_mt_line(
            "MT_NOAI_AUDIT: PASS — heartbeats="
            + str(len(beats))
            + " withMobs="
            + str(len(with_mobs))
            + " mismatch=0 正面对照=forced "
            + str(len(forced))
            + " 次（total="
            + str(total)
            + "）"
        )
    else:
        C.write_mt_warn(
            "MT_NOAI_AUDIT: PASS(无正面对照) — heartbeats="
            + str(len(beats))
            + " mismatch=0，但本轮 mobs 全为 0：只证明现场干净，未实测触发过强制逻辑"
        )
    return 0


# ── summary ───────────────────────────────────────────────────────────────
def invoke_summary():
    run_id = C.active_run_id()
    state = load_state()
    dest = os.path.join(REPORTS_DIR, run_id)
    if not os.path.isdir(dest):
        os.makedirs(dest, exist_ok=True)

    round_gen = str(state.get("gen")) if "gen" in state else "（旧口径，未标世代）"
    history = state.get("history")
    if not isinstance(history, list):
        history = [] if history is None else list(history)
    hist_marks = 0
    for item in history:
        if isinstance(item, dict):
            hist_marks += round_mark_count(item.get("versions"))
    hist_detail = ""
    if history:
        parts = []
        for item in history:
            if not isinstance(item, dict):
                continue
            count = round_mark_count(item.get("versions"))
            gen_text = str(item.get("gen") or "")
            parts.append((gen_text if gen_text else "（未标世代）") + "=" + str(count))
        hist_detail = ", ".join(parts)

    lines = [
        "# 自动化测试总览",
        "",
        "- **运行**: `" + run_id + "`",
        "- **本轮世代**: `" + round_gen + "`（**只统计本轮标记**）",
        "- **历史轮次（不计入判定）**: 已归档 "
        + str(len(history))
        + " 个 / "
        + str(hist_marks)
        + " 条标记"
        + ("：" + hist_detail if hist_detail else ""),
        "- **分支**: " + branch_name(),
        "- **生成时间**: " + now_stamp(),
        "",
        "> 测试顺序：先 1.21.1，通过后才执行 1.20.1。两个版本都给出独立结论。",
        "> `mt.ps1 --version <V>` 指定单版本时只跑该版本、不做跨版本门控，总览也只列实际执行过的版本。",
        "> 条目判定口径（规则 14 收严版）：`PASS`/`SKIP` 之外（FAIL/TIMEOUT/BLOCKED/ERROR/GATED）一律计入 ❌ 并让退出码非 0；",
        "> 但 `USER_TERMINATED` 是**第三类 —— 无效运行**：该次运行的全部结果无效（不计入通过率、不作为缺陷依据），不与 ❌ FAIL 混淆，退出码沿用既有中断码 MT_EXIT_ERROR(2)。",
        "",
        "## 版本结论",
        "",
        "| 顺序 | 版本 | 结论 | 条目 |",
        "|---|---|---|---|",
    ]

    summary_versions = C.get_versions()
    versions_state = state.get("versions")
    if isinstance(versions_state, dict):
        present = [str(k) for k in versions_state.keys()]
        if present and len(present) < len(summary_versions):
            summary_versions = [v for v in summary_versions if v in present]

    overall_pass = True
    overall_invalid = False
    invalid_versions = []
    any_real_failure = False
    index = 0
    for version in summary_versions:
        index += 1
        cases = {}
        if isinstance(versions_state, dict) and isinstance(versions_state.get(version), dict):
            entry = versions_state[version]
            if isinstance(entry.get("cases"), dict):
                cases = entry["cases"]
        if not cases:
            verdict = "未执行"
            overall_pass = False
        else:
            bad = []
            timeouts = []
            ut = []
            for name, value in cases.items():
                res = str(value)
                if res not in ("PASS", "SKIP"):
                    bad.append(str(name) + "=" + res)
                    if res == "TIMEOUT":
                        timeouts.append(str(name))
                    if res == "USER_TERMINATED":
                        ut.append(str(name))
            if ut:
                verdict = "⛔ 无效运行（MT_USER_TERMINATED：" + ", ".join(bad) + "）"
                overall_invalid = True
                invalid_versions.append(version)
                if len([b for b in bad if not b.endswith("=USER_TERMINATED")]) > 0:
                    any_real_failure = True
            elif not bad:
                verdict = "✅ PASS"
            elif timeouts and len(timeouts) == len(bad):
                verdict = "⏱ TIMEOUT（" + ", ".join(bad) + "）"
            else:
                verdict = "❌ FAIL（" + ", ".join(bad) + "）"
            if bad and not ut:
                overall_pass = False
        lines.append("| " + str(index) + " | " + version + " | " + verdict + " | " + str(len(cases)) + " |")

    lines += ["", "## 综合结论", ""]
    if overall_invalid:
        lines.append("**⛔ 无效运行（MT_USER_TERMINATED）—— 本次测试结果全部无效，需重新执行测试验证**")
        lines.append("")
        lines.append(
            "> 涉及版本："
            + ", ".join(invalid_versions)
            + "。依据（规则 14 收严版）：「被用户主动终止的游戏进程一律视为作废。此次测试产生的任何结果都必须视为无效。需要重新执行测试进行验证。」"
        )
        lines.append(
            "> **不计入通过率**、**不作为缺陷依据**、不与产品 `❌ FAIL` 混淆；各版本 `report.md` 的条目表已逐条如实保留全部结果串（含其它失败串）供重新执行时对照。"
        )
        if any_real_failure:
            lines.append(
                "> 注：本轮**另有真实失败串**（见上方版本结论与各版本 `report.md` 条目表）——整轮仍按无效运行处置；这些失败项待重新执行后复验，**当前不作为缺陷依据**。"
            )
    else:
        lines.append("**" + ("✅ 全部通过" if overall_pass else "❌ 未全部通过") + "**")
    lines += ["", "各版本详细报告见对应子目录 `report.md`。"]

    summary_path = os.path.join(dest, "SUMMARY.md")
    with open(summary_path, "w", encoding="utf-8", newline="") as fh:
        fh.write(NL.join(lines) + NL)
    C.write_mt_line("MT_REPORT: OK — " + summary_path)

    round_text = (
        "无效运行（MT_USER_TERMINATED）"
        if overall_invalid
        else ("全部通过" if overall_pass else "未全部通过")
    )
    C.write_mt_line("MT_ROUND: 本轮 " + round_gen + " 结算（" + round_text + "）；汇总只统计本轮标记")
    if overall_invalid:
        C.write_mt_line(
            "MT_INVALID_RUN: 本轮 result=INVALID reason=USER_TERMINATED versions="
            + ",".join(invalid_versions)
            + " exit="
            + str(C.MT_EXIT_ERROR)
        )
        C.write_mt_err_line(
            "MT_INVALID_RUN: 本次测试结果全部无效，需重新执行测试验证 —— 无效运行（MT_USER_TERMINATED）："
            "不计入通过率、不作为缺陷依据、不与产品 FAIL 混淆"
        )

    state["closed_at"] = int(time.time())
    try:
        save_state(state)
    except OSError as exc:
        C.write_mt_warn(
            "MT_ROUND: 结算标记写盘失败（" + str(exc) + "）；下一轮仍会按世代/结算判据开新轮"
        )

    if overall_invalid:
        return C.MT_EXIT_ERROR
    if overall_pass:
        return 0
    return C.MT_EXIT_FAIL


# ── 入口 ───────────────────────────────────────────────────────────────────
USAGE = """mt_report —— 证据收集与报告生成（阶段 R）

用法:
  python scripts/test/mt_report.py mark    --version <v> [--phase <name>] [--case <name>] --result <R>
  python scripts/test/mt_report.py collect --version <v> [--verdict <R>]
  python scripts/test/mt_report.py summary

版本: 1.21.1 / 1.20.1 / 26.1.2 / fabric-1.20.1
退出码: 0 通过 / 1 未全部通过 / 2 错误或无效运行（MT_USER_TERMINATED）
"""

VALUE_KEYS = ("version", "phase", "case", "result", "verdict")


def main(argv):
    C.init_console()
    command = ""
    version = ""
    phase = ""
    case_name = ""
    result = ""
    verdict = ""

    index = 0
    while index < len(argv):
        token = str(argv[index])
        if token in ("--help", "-h"):
            C.write_mt_line(USAGE.rstrip(NL))
            return 0
        key = token.lstrip("-").lower()
        if not token.startswith("-"):
            if command:
                C.write_mt_error_line("未知参数 " + token)
                return C.MT_EXIT_ERROR
            command = key
            index += 1
        elif key in VALUE_KEYS:
            if index + 1 >= len(argv):
                C.write_mt_error_line("缺少 --" + key + " 的值")
                return C.MT_EXIT_ERROR
            value = str(argv[index + 1])
            if key == "version":
                version = value
            elif key == "phase":
                phase = value
            elif key == "case":
                case_name = value
            elif key == "result":
                result = value
            else:
                verdict = value
            index += 2
        else:
            C.write_mt_error_line("未知参数 " + token)
            return C.MT_EXIT_ERROR

    if command not in ("mark", "collect", "summary"):
        C.write_mt_error_line("必须指定子命令 mark / collect / summary")
        return C.MT_EXIT_ERROR
    if command != "summary":
        if not version:
            C.write_mt_error_line("必须指定 --version")
            return C.MT_EXIT_ERROR
        if not C.assert_version(version):
            return C.MT_EXIT_ERROR
    if command == "mark":
        if not result:
            C.write_mt_error_line("mark 子命令必须指定 --result")
            return C.MT_EXIT_ERROR
        if not phase and not case_name:
            C.write_mt_error_line("mark 子命令必须指定 --phase 或 --case")
            return C.MT_EXIT_ERROR

    if command == "mark":
        return invoke_mark(version, phase, case_name, result)
    if command == "collect":
        return invoke_collect(version, verdict)
    return invoke_summary()


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
