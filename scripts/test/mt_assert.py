#!/usr/bin/env python3
"""一条一条读「自快照点起的增量日志」，把判据变成 PASS / FAIL / INCONCLUSIVE。

本文件是旧编排器断言器（1537 行）的 python 等价物，子命令、参数名、机器行、
退出码逐字对齐；ps1 侧即将物理删除，手动「读一条增量日志 / 判一条断言」的能力必须由它承接。

子命令：snapshot / log / absent / crash / kubejs / mixin / eval / state
机器行：MT_SNAPSHOT: / MT_ASSERT_LOG: / MT_ASSERT_ABSENT: / MT_ASSERT_CRASH: /
        MT_ASSERT_KUBEJS: / MT_ASSERT_MIXIN: / MT_ASSERT_EVAL: / MT_ASSERT_STATE:
退出码：PASS 0 / FAIL 1 / ERROR 2 / BLOCKED 11（端点不可用）；INCONCLUSIVE 一律 0 且不记 FAIL。

与 ps1 的唯一刻意差异：多了 --help / -h（ps1 把 --help 当未知参数报 MT_ERROR + 2）。
"""

import json
import os
import re
import sys
import time

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "lib"))

import mt_compat  # noqa: E402
from mt_compat import (  # noqa: E402
    MT_EXIT_PASS,
    MT_EXIT_FAIL,
    MT_EXIT_ERROR,
    MT_EXIT_BLOCKED,
    assert_version,
    get_paths,
    init_console,
    json_dump,
    log_anchor,
    read_log_window,
    write_mt_err_line,
    write_mt_error_line,
    write_mt_line,
    write_mt_warn,
    active_run_id,
)

TEST_DIR = os.path.dirname(os.path.abspath(__file__))
SNAPSHOT_FILE = os.path.join(TEST_DIR, "cases", ".mt_snapshot.json")
AUTOJS_RECORD_FILE = os.path.join(TEST_DIR, "cases", ".mt_autojs.json")

# latest —— 客户端游戏日志（探针标记的权威通道）
# debug  —— 客户端调试日志（Mixin 应用 / 渲染栈加载证据）
# kubejs_client —— KubeJS **客户端**脚本日志（客户端侧状态只能从这里读）
# 已移除的 probe 通道（run/<版本>/astral_probe.log）不再接回：该文件从未生成，
# 接回只会把「服务端通道不存在」伪装成「断言通过」。
LOG_SOURCES = {"latest": "latest_log", "debug": "debug_log", "kubejs_client": "kubejs_client_log"}

EXPECT_OPERATORS = ("min", "max", "gte", "lte", "regex", "set", "contains")
EXPECT_ECHO_MAX = 400


# ── 快照 ──────────────────────────────────────────────────────────────────
def get_snapshot():
    """读快照文件；不存在或损坏时返回 {}（与 ps1 Get-MtSnapshot 同义）。"""
    try:
        with open(SNAPSHOT_FILE, "r", encoding="utf-8") as handle:
            text = handle.read()
    except OSError:
        return {}
    try:
        value = json.loads(text)
    except ValueError:
        return {}
    return value if isinstance(value, dict) else {}


def save_snapshot(snapshot):
    os.makedirs(os.path.dirname(SNAPSHOT_FILE), exist_ok=True)
    with open(SNAPSHOT_FILE, "w", encoding="utf-8", newline="\n") as handle:
        handle.write(json_dump(snapshot))


def snap_for(version):
    snap = get_snapshot()
    versions = snap.get("versions")
    if not isinstance(versions, dict):
        return {}
    entry = versions.get(version)
    return entry if isinstance(entry, dict) else {}


# ── B7 窗口选择 ───────────────────────────────────────────────────────────
def window_tag(info):
    """窗口标签后缀：只在非普通情形（跨轮转重拼 / 窗口不可重建）时附加。"""
    text = str(info or "")
    if text.startswith("reassembled") or text == "unreconstructable":
        return "; " + text
    return ""


def window_offset(entry, log_name, window):
    """返回 (偏移, 实际生效的窗口名, 退化告警或空串, 锚点或 None)。"""
    if window == "whole":
        return 0, "whole", "", None
    key = "launch_offsets" if window == "launch" else "offsets"
    akey = "launch_anchors" if window == "launch" else "anchors"
    degraded = ""
    if not isinstance(entry.get(key), dict):
        if window == "launch" and isinstance(entry.get("offsets"), dict):
            key = "offsets"
            akey = "anchors"
            degraded = "launch_offsets 缺失（快照早于 B7 / 本条走的是 --phase cases 单步路线）⇒ 退化为当前窗口"
        else:
            return 0, window, "", None
    anchor = None
    anchors = entry.get(akey)
    if isinstance(anchors, dict):
        anchor = anchors.get(log_name)
    offsets = entry.get(key) or {}
    return int(offsets.get(log_name) or 0), window, degraded, anchor


# ── 增量读取 ──────────────────────────────────────────────────────────────
def universal_newlines(text):
    """复刻 python 文本模式的通用换行翻译：\r\n 与孤立 \r 一律折成 \n。"""
    return str(text).replace("\r\n", "\n").replace("\r", "\n")


def window_fidelity(window, requested_offset, logs_dir, anchor):
    """判定「本断言窗口是否确证缺内容」⇒ 缺内容时未命中不得记产品 FAIL。"""
    kind = str(window.get("kind") or "")
    if kind == "unreconstructable":
        return True, "unreconstructable（锚点文件身份丢失，窗口起点之前的内容确定丢失）"
    if requested_offset > 0 and int(window.get("offset") or 0) == 0 and kind in ("intact", "legacy"):
        return (
            True,
            "anchor-overrun（请求起点 %dB > 当前文件长度，起点被 clamp 到 0 ⇒ 起点之前的内容确定丢失）"
            % int(requested_offset),
        )
    if kind == "reassembled":
        # 「可证连续」判据：重拼后没有更晚的轮转段未并入 ⇒ 窗口完整，未命中仍记 FAIL。
        if "未并入" in str(window.get("note") or ""):
            return True, "gap-after-anchor（ROTATED：锚点段与当前文件之间另有更晚的轮转段未并入 ⇒ 窗口可能缺段）"
        return False, ""
    return False, ""


def read_log_delta(log_path, offset, anchor):
    """返回 (文本, 窗口种类标签, 实际起点, (inconclusive, reason))。"""
    logs_dir = os.path.dirname(log_path)
    window = read_log_window(log_path, offset=offset, anchor=anchor, logs_dir=logs_dir)
    kind = str(window.get("kind") or "")
    rotated = int(window.get("rotated") or 0)
    info = (kind + "+" + str(rotated)) if rotated > 0 else kind
    if kind == "reassembled":
        write_mt_line("MT_ASSERT_WINDOW: " + str(window.get("note") or ""))
    elif kind == "unreconstructable":
        write_mt_warn("MT_ASSERT_WINDOW: WARN — " + str(window.get("note") or ""))
    fid = window_fidelity(window, int(offset or 0), logs_dir, anchor)
    return str(window.get("text") or ""), info, int(window.get("offset") or 0), fid


def log_path_of(paths, source):
    key = LOG_SOURCES.get(source)
    if key is None:
        write_mt_err_line("MT_ASSERT: ERROR — 未知断言通道 " + str(source))
        raise SystemExit(MT_EXIT_ERROR)
    return str(getattr(paths, key))


def log_missing(source, log_path):
    """日志文件不存在属环境问题（游戏没起来/跑错目录），不是断言失败。"""
    if os.path.isfile(log_path):
        return False
    write_mt_err_line("MT_ASSERT_LOG: ERROR — 断言通道缺失(%s:%s);游戏未启动或日志目录不对" % (source, log_path))
    return True


# ── 强证据口径（S1/S2）─────────────────────────────────────────────────────
# 判据一（neo/forge）：行首 [时间戳] + [线程/LEVEL] + 可选 [来源] + 冒号
PLATFORM_RX_NEO = re.compile(r"^\ufeff?\[[^\]]+\]\s*\[[^\]]+/[A-Za-z]+\](\s*\[[^\]]*\])?\s*:")
# 判据二（fabric）：行首 [时间戳] + [线程/LEVEL] + 圆括号来源（无冒号也认）
PLATFORM_RX_FABRIC = re.compile(r"^\ufeff?\[[^\]]+\]\s*\[[^\]]+/[A-Za-z]+\]\s*\([^)]+\)")
# 判据三（FabricLoader 模组清单纯数据续行）：<TAB>- <modid> <version>
PLATFORM_RX_MODLIST = re.compile(r"^\t- [A-Za-z0-9_.\-]+ \S+\s*$")

ECHO_RX_NEO = re.compile(r"\[AutoJS/Eval/\]\s*:.*JSON\.stringify\(\(")
ECHO_RX_FABRIC = re.compile(r"\(AutoJS/Eval/?\)")


def is_platform_line(line):
    """平台输出行 = SLF4J/日志框架真的写出来的那一行（回显行/续行/栈帧都不是）。"""
    if not line:
        return False
    if PLATFORM_RX_NEO.search(line):
        return True
    if PLATFORM_RX_FABRIC.search(line):
        return True
    return bool(PLATFORM_RX_MODLIST.search(line))


def is_eval_source_echo(line):
    """平台行形状但载荷是 eval 源码（AutoJS-Mcp 的 [AutoJS/Eval/] 回显通道）。"""
    if not line:
        return False
    return bool(ECHO_RX_NEO.search(line) or ECHO_RX_FABRIC.search(line))


def show_hit_lines(hits, limit=10):
    for hit in list(hits)[:limit]:
        text = str(hit["text"]).strip()
        if len(text) > 160:
            text = text[:160]
        write_mt_line("    L%s [%s] %s" % (hit["line"], hit["kind"], text))


def show_hits(pattern, text, limit=3):
    """打印命中示例行（必须复用已编译正则，不要用字符串拼 .*pattern.* 再编译）。"""
    shown = 0
    for raw in str(text).split("\n"):
        line = raw.rstrip("\r")
        if not pattern.search(line):
            continue
        text_out = line.strip()
        if len(text_out) > 160:
            text_out = text_out[:160]
        write_mt_line("    " + text_out)
        shown += 1
        if shown >= limit:
            break


def new_regex(pattern_text):
    try:
        return re.compile(str(pattern_text))
    except re.error as exc:
        write_mt_err_line("MT_ASSERT: ERROR — 非法正则 %s：%s" % (pattern_text, exc))
        raise SystemExit(MT_EXIT_ERROR)


# ── 子命令 ────────────────────────────────────────────────────────────────
def invoke_snapshot(version, window):
    """写窗口起点。--window launch 额外冻结 launch_offsets；--window case 只改写 offsets。"""
    paths = get_paths(version)
    run_id = active_run_id()
    snap = get_snapshot()
    same_run = bool(snap.get("run_id")) and snap.get("run_id") == run_id
    if not same_run:
        snap = {"run_id": run_id, "versions": {}}
    if not isinstance(snap.get("versions"), dict):
        snap["versions"] = {}

    offsets = {}
    anchors = {}
    for log in (paths.latest_log, paths.debug_log, paths.kubejs_log, paths.probe_log):
        name = os.path.basename(log)
        try:
            size = os.path.getsize(log) if os.path.isfile(log) else 0
        except OSError:
            size = 0
        offsets[name] = int(size)
        # 窗口起点必须同时记住「是哪个文件」：日切后「偏移越界」才有证据可用来重建窗口。
        anchors[name] = log_anchor(log)

    crash_names = []
    if os.path.isdir(paths.crash_dir):
        crash_names = sorted(
            name for name in os.listdir(paths.crash_dir)
            if name.lower().endswith(".txt") and os.path.isfile(os.path.join(paths.crash_dir, name))
        )

    versions = snap["versions"]
    prev = versions.get(version) if same_run else None

    launch_warn = ""
    if window != "case":
        launch_offsets = offsets
        launch_anchors = anchors
    elif isinstance(prev, dict) and isinstance(prev.get("launch_offsets"), dict):
        launch_offsets = prev.get("launch_offsets")
        launch_anchors = prev.get("launch_anchors") if isinstance(prev.get("launch_anchors"), dict) else None
    else:
        launch_offsets = offsets
        launch_anchors = anchors
        launch_warn = "WARN — 无 launch 基线（--phase cases 单步路线 / 快照早于 B7），launch 窗口退化为当前起点"

    autojs = None
    try:
        with open(AUTOJS_RECORD_FILE, "r", encoding="utf-8") as handle:
            record = json.loads(handle.read())
        recorded = record.get("versions") if isinstance(record, dict) else None
        if isinstance(recorded, dict):
            autojs = recorded.get(version)
    except (OSError, ValueError):
        autojs = None
    if autojs is None and isinstance(prev, dict):
        autojs = prev.get("autojs")

    entry = {
        "offsets": offsets,
        "launch_offsets": launch_offsets,
        "anchors": anchors,
        "launch_anchors": launch_anchors,
        "window": window,
        "ts": time.time(),
        "crash_baseline": crash_names,
        "mixin_errors": [],
        "autojs": autojs,
    }
    versions[version] = entry
    snap["versions"] = versions
    save_snapshot(snap)

    anchor_count = sum(1 for value in anchors.values() if value is not None)
    write_mt_line(
        "MT_SNAPSHOT: OK — %s run=%s window=%s latest=%dB debug=%dB probe=%dB anchors=%d"
        % (
            version,
            run_id,
            window,
            int(offsets.get("latest.log") or 0),
            int(offsets.get("debug.log") or 0),
            int(offsets.get("astral_probe.log") or 0),
            anchor_count,
        )
    )
    if launch_warn:
        write_mt_warn("MT_SNAPSHOT: " + launch_warn)
    return MT_EXIT_PASS


def invoke_log(version, pattern_text, source, window):
    paths = get_paths(version)
    if window == "whole":
        write_mt_warn("窗口 = whole：对全文件求值（可能被历史标记污染）")
    log_path = log_path_of(paths, source)
    if log_missing(source, log_path):
        return MT_EXIT_ERROR

    entry = snap_for(version)
    name = os.path.basename(log_path)
    offset, win_used, degraded, anchor = window_offset(entry, name, window)
    if degraded:
        write_mt_warn("MT_ASSERT_LOG: " + degraded)

    text, win_info, offset_used, fid = read_log_delta(log_path, offset, anchor)
    pattern = new_regex(pattern_text)

    # S1/S2：逐物理行匹配，只有平台输出行才算强证据。
    hits = []
    strong = 0
    line_no = 0
    for raw in text.split("\n"):
        line_no += 1
        line = raw.rstrip("\r")
        if not pattern.search(line):
            continue
        source_echo = is_eval_source_echo(line)
        is_strong = (not source_echo) and is_platform_line(line)
        kind = "platform" if is_strong else ("platform-source-echo" if source_echo else "echo")
        if is_strong:
            strong += 1
        hits.append({"line": line_no, "text": line, "strong": is_strong, "kind": kind})

    label = "%s:%s@%dB(win=%s%s)" % (source, name, offset_used, win_used, window_tag(win_info))

    if hits and strong:
        write_mt_line(
            "MT_ASSERT_LOG: PASS — /%s/ 命中 %d 行（平台输出行 %d 行）（%s）"
            % (pattern_text, len(hits), strong, label)
        )
        show_hit_lines(hits)
        return MT_EXIT_PASS
    if hits:
        write_mt_line(
            "MT_ASSERT_LOG: FAIL(SOURCE_ECHO) — /%s/ 命中 %d 行但无一是平台输出行"
            "（回显/续行/平台行内的 eval 源码载荷都不构成证据）（%s）" % (pattern_text, len(hits), label)
        )
        show_hit_lines(hits)
        return MT_EXIT_FAIL
    if fid[0]:
        write_mt_line(
            "MT_ASSERT_LOG: INCONCLUSIVE — /%s/ 未命中，但**窗口不可判**（%s；%s）" % (pattern_text, fid[1], label)
        )
        write_mt_warn(
            "MT_ASSERT_INCONCLUSIVE: WARN — 窗口不可判（%s）⇒ 本条**不计 FAIL**；该断言需重新执行测试验证" % fid[1]
        )
        return MT_EXIT_PASS
    write_mt_line("MT_ASSERT_LOG: FAIL — /%s/ 未命中（%s）" % (pattern_text, label))
    return MT_EXIT_FAIL


def invoke_absent(version, pattern_text, source, window):
    """反向断言：窗口内不得出现某标记。"""
    paths = get_paths(version)
    log_path = log_path_of(paths, source)
    if log_missing(source, log_path):
        return MT_EXIT_ERROR

    entry = snap_for(version)
    name = os.path.basename(log_path)
    offset, win_used, degraded, anchor = window_offset(entry, name, window)
    if degraded:
        write_mt_warn("MT_ASSERT_ABSENT: " + degraded)

    text, win_info, offset_used, fid = read_log_delta(log_path, offset, anchor)
    pattern = new_regex(pattern_text)
    label = "%s:%s@%dB(win=%s%s)" % (source, name, offset_used, win_used, window_tag(win_info))

    if pattern.search(text):
        # 命中 = 可信证据（窗口里确有不该出现的东西），即使窗口不可判也仍记 FAIL。
        write_mt_line("MT_ASSERT_ABSENT: FAIL — 不应出现的 /%s/ 出现了（%s）" % (pattern_text, label))
        show_hits(pattern, text)
        return MT_EXIT_FAIL
    if fid[0]:
        write_mt_line(
            "MT_ASSERT_ABSENT: INCONCLUSIVE(弱通过) — /%s/ 未出现，但**窗口不可判**（%s；%s）"
            % (pattern_text, fid[1], label)
        )
        write_mt_warn(
            "MT_ASSERT_INCONCLUSIVE: WARN — 窗口不可判（%s）⇒ ABSENT 的「未出现」为**弱结论**，需重新执行测试验证" % fid[1]
        )
        return MT_EXIT_PASS
    write_mt_line("MT_ASSERT_ABSENT: PASS — /%s/ 未出现（%s）" % (pattern_text, label))
    return MT_EXIT_PASS


def invoke_crash(version):
    paths = get_paths(version)
    entry = snap_for(version)
    baseline = entry.get("crash_baseline")
    baseline = list(baseline) if isinstance(baseline, list) else []

    if not os.path.isdir(paths.crash_dir):
        # 「目录不存在」≠「目录存在且无新增」：取证面缺失不能断言无崩溃。
        write_mt_line("MT_ASSERT_CRASH: INCONCLUSIVE — crash-reports 目录不存在（%s）" % paths.crash_dir)
        write_mt_line("    「目录不存在」≠「目录存在且无新增」：本轮从未建立崩溃报告目录 ⇒ 无法断言无崩溃（不记 PASS）。")
        write_mt_warn(
            "MT_ASSERT_INCONCLUSIVE: WARN — crash-reports 目录不存在 ⇒ 本条记为不可判（不计 PASS / 不计 FAIL），"
            "需人工确认该线是否会写 crash-reports"
        )
        return MT_EXIT_PASS

    names = sorted(
        name for name in os.listdir(paths.crash_dir)
        if name.lower().endswith(".txt") and os.path.isfile(os.path.join(paths.crash_dir, name))
    )
    new = [name for name in names if name not in baseline]
    if new:
        write_mt_line("MT_ASSERT_CRASH: FAIL — 新增崩溃报告 " + ", ".join(new))
        return MT_EXIT_FAIL
    write_mt_line("MT_ASSERT_CRASH: PASS — 本次运行无新增崩溃报告（基线 %d 个）" % len(baseline))
    return MT_EXIT_PASS


def invoke_kubejs(version):
    paths = get_paths(version)
    if not os.path.isfile(paths.kubejs_log):
        write_mt_line("MT_ASSERT_KUBEJS: BLOCKED — 未找到 KubeJS server.log（KubeJS 未加载或尚未进入世界）")
        return MT_EXIT_BLOCKED
    with open(paths.kubejs_log, "r", encoding="utf-8", errors="replace") as handle:
        text = universal_newlines(handle.read())
    errs = []
    for raw in text.split("\n"):
        line = raw.rstrip("\r")
        if re.search(r"\bERROR\b|\bException\b", line):
            errs.append(line)
    if errs:
        write_mt_line("MT_ASSERT_KUBEJS: FAIL — server.log 含 %d 条 error" % len(errs))
        for line in errs[:5]:
            text_out = str(line).strip()
            if len(text_out) > 160:
                text_out = text_out[:160]
            write_mt_line("    " + text_out)
        return MT_EXIT_FAIL
    write_mt_line("MT_ASSERT_KUBEJS: PASS — server.log 0 errors")
    return MT_EXIT_PASS


def invoke_mixin(version, window):
    """窗口内不得出现 Mixin 应用失败；默认窗口是 launch（Mixin 应用发生在启动期）。"""
    paths = get_paths(version)
    log_path = paths.debug_log if os.path.isfile(paths.debug_log) else paths.latest_log
    entry = snap_for(version)
    name = os.path.basename(log_path)
    offset, win_used, degraded, anchor = window_offset(entry, name, window)
    if degraded:
        write_mt_warn("MT_ASSERT_MIXIN: " + degraded)

    text, win_info, offset_used, fid = read_log_delta(log_path, offset, anchor)
    pattern = re.compile("Mixin apply failed|Mixin apply error|Failed to apply mixin")
    if pattern.search(text):
        write_mt_line("MT_ASSERT_MIXIN: FAIL — 检测到 Mixin 应用失败（兼容模组栈异常）")
        return MT_EXIT_FAIL
    label = "%s@%dB(win=%s%s)" % (name, offset_used, win_used, window_tag(win_info))
    if fid[0]:
        write_mt_line(
            "MT_ASSERT_MIXIN: INCONCLUSIVE(弱通过) — %s **窗口不可判**（%s）⇒ 无 Mixin 失败为弱结论" % (label, fid[1])
        )
        write_mt_warn(
            "MT_ASSERT_INCONCLUSIVE: WARN — 窗口不可判（%s）⇒ MIXIN 判据为弱结论，需重新执行测试验证" % fid[1]
        )
        return MT_EXIT_PASS
    write_mt_line("MT_ASSERT_MIXIN: PASS — %s 窗口内无 Mixin 失败" % label)
    return MT_EXIT_PASS


# ── expect 比较引擎 ───────────────────────────────────────────────────────
def plain(value):
    """JSON 反序列化结果本身就是 plain 形态；这里只做一次深拷贝式归一。"""
    if isinstance(value, dict):
        return {str(k): plain(v) for k, v in value.items()}
    if isinstance(value, list):
        return [plain(v) for v in value]
    return value


def compact_json(value, limit=0):
    text = json.dumps(value, ensure_ascii=False, separators=(",", ":"), default=str)
    if limit > 0 and len(text) > limit:
        return text[:limit] + "...（截断，原长 %d）" % len(text)
    return text


def format_value(value):
    if value is None:
        return "null"
    return compact_json(value, EXPECT_ECHO_MAX)


def kind_of(value):
    if value is None:
        return "scalar"
    if isinstance(value, dict):
        return "object"
    if isinstance(value, (str, int, float, bool)):
        return "scalar"
    if isinstance(value, (list, tuple)):
        return "array"
    return "object"


def as_number(value):
    """数值归一：数值类型直取；数值字符串按不变文化解析；其它返回 None（bool 不算数）。"""
    if value is None or isinstance(value, bool):
        return None
    if isinstance(value, (int, float)):
        return float(value)
    if isinstance(value, str):
        try:
            return float(value.strip())
        except ValueError:
            return None
    return None


def scalar_equal(expected, actual):
    if expected is None:
        return actual is None
    if isinstance(expected, bool):
        if isinstance(actual, bool):
            return expected == actual
        number = as_number(actual)
        if number is not None:
            return expected == (number != 0)
        if isinstance(actual, str):
            text = actual.strip().lower()
            if text == "true":
                return expected
            if text == "false":
                return not expected
        return False
    need = as_number(expected)
    if need is not None:
        have = as_number(actual)
        if have is None:
            return False
        tolerance = 1e-9 * max(1.0, abs(need))
        return abs(need - have) <= tolerance
    if isinstance(actual, bool):
        actual_text = "true" if actual else "false"
    elif actual is None:
        actual_text = ""
    elif isinstance(actual, str):
        actual_text = actual
    elif isinstance(actual, dict) or kind_of(actual) == "array":
        actual_text = format_value(actual)
    else:
        actual_text = str(actual)
    return str(expected).lower() == actual_text.lower()


def match_operator(name, operand, actual, path):
    """操作符判据：min/max/gte/lte/regex/set/contains。返回失配串数组（空 = 命中）。"""
    out = []
    op_text = "{" + name + ":" + format_value(operand) + "}"

    def mismatch(kind):
        return "路径 %s 期望 %s 实际 %s（%s）" % (path, op_text, format_value(actual), kind)

    if name in ("min", "max", "gte", "lte"):
        need = as_number(operand)
        if need is None:
            return ["路径 %s 期望 %s 实际 %s（判据不合法：%s 需要数字）" % (path, op_text, format_value(actual), name)]
        have = as_number(actual)
        if have is None:
            return [mismatch("非数值")]
        ok = (have >= need) if name in ("min", "gte") else (have <= need)
        if not ok:
            out.append(mismatch("数值越界"))
        return out
    if name == "regex":
        if not isinstance(operand, str):
            return ["路径 %s 期望 %s 实际 %s（判据不合法：regex 需要字符串）"
                    % (path, op_text, format_value(actual))]
        if isinstance(actual, str):
            subject = actual
        elif actual is None:
            subject = ""
        elif kind_of(actual) == "scalar" and not isinstance(actual, bool):
            subject = str(actual)
        else:
            return [mismatch("类型不符：regex 只能作用于字符串")]
        try:
            rx = re.compile(operand)
        except re.error as exc:
            return ["路径 %s 期望 %s 实际 %s（判据不合法：正则非法 %s）"
                    % (path, op_text, format_value(actual), exc)]
        if not rx.search(subject):
            out.append(mismatch("正则不匹配"))
        return out
    if not isinstance(operand, list):
        return ["路径 %s 期望 %s 实际 %s（判据不合法：%s 需要数组）"
                % (path, op_text, format_value(actual), name)]
    if not isinstance(actual, list):
        return [mismatch("类型不符：需要数组")]
    want = plain(operand)
    have = plain(actual)
    if name == "set":
        if len(want) != len(have):
            out.append(mismatch("集合元素数不符（期望 %d 实际 %d）" % (len(want), len(have))))
        pool = list(have)
        for item in want:
            hit = -1
            for index, candidate in enumerate(pool):
                if scalar_equal(item, candidate):
                    hit = index
                    break
            if hit >= 0:
                pool.pop(hit)
            else:
                out.append(mismatch("集合缺元素 " + format_value(item)))
        return out
    for item in want:
        if not any(scalar_equal(item, candidate) for candidate in have):
            out.append(mismatch("缺少子集元素 " + format_value(item)))
    return out


def match_node(expected, actual, path="<root>", present=True):
    """逐键递归比较；返回失配串数组（空 = 命中）。报文固定三段：路径 + 期望 + 实际。"""
    out = []
    if not present:
        return ["路径 %s 期望 %s 实际 <缺失>（缺失键）" % (path, format_value(expected))]
    kind = kind_of(expected)
    if kind == "object":
        keys = list(expected.keys())
        if len(keys) == 1 and str(keys[0]) in EXPECT_OPERATORS:
            name = str(keys[0])
            return match_operator(name, expected[keys[0]], actual, path)
        if kind_of(actual) != "object":
            return ["路径 %s 期望 <对象> 实际 %s（类型不符）" % (path, format_value(actual))]
        for key in keys:
            name = str(key)
            sub_path = name if path == "<root>" else path + "." + name
            if name in actual:
                out.extend(match_node(expected[key], actual[name], sub_path, True))
            else:
                out.extend(match_node(expected[key], None, sub_path, False))
        return out
    if kind == "array":
        if kind_of(actual) != "array":
            return ["路径 %s 期望 <数组> 实际 %s（类型不符）" % (path, format_value(actual))]
        if len(expected) != len(actual):
            out.append("路径 %s 期望 %s 实际 %s（长度不符）"
                       % (path, format_value(expected), format_value(actual)))
        for index in range(min(len(expected), len(actual))):
            out.extend(match_node(expected[index], actual[index], path + "[" + str(index) + "]", True))
        return out
    if not scalar_equal(expected, actual):
        out.append("路径 %s 期望 %s 实际 %s（标量不等）"
                   % (path, format_value(expected), format_value(actual)))
    return out


def expect_extras(expected, actual, path="<root>"):
    """收集「实际里有、期望里没写」的键路径（不参与判定，只回显证据）。"""
    out = []
    if kind_of(expected) != "object":
        return out
    keys = list(expected.keys())
    if len(keys) == 1 and str(keys[0]) in EXPECT_OPERATORS:
        return out
    if kind_of(actual) != "object":
        return out
    for key in actual.keys():
        name = str(key)
        sub_path = name if path == "<root>" else path + "." + name
        if name not in expected:
            out.append(sub_path)
            continue
        out.extend(expect_extras(expected[name], actual[name], sub_path))
    return out


def expect_match(expected, actual):
    expected_plain = plain(expected)
    actual_plain = plain(actual)
    mismatches = match_node(expected_plain, actual_plain, "<root>", True)
    extras = expect_extras(expected_plain, actual_plain, "<root>")
    return {"ok": not mismatches, "mismatches": mismatches, "extras": extras}


def parse_json_text(text):
    try:
        return True, json.loads(text), ""
    except ValueError as exc:
        return False, None, str(exc)


AUTOAJS_REASON_TIMEOUT = re.compile(r"^TCP 连接失败（(?P<where>[^）]+)）：.*(?:timed out|timeout).*$",
                                    re.IGNORECASE)


def autojs_timeout_reason(match):
    """mt_autojs.probe 每次 TCP 尝试的 2000ms 超时 ⇒ 与 ps1 Test-MtAutoJsTcpPort 同形文案。"""
    return "TCP 连接超时（%s，2000 ms）" % match.group("where")


def autojs_endpoint():
    """AutoJS 端点可用性：端点没起 = BLOCKED 的依据（不是 FAIL）。"""
    try:
        from mt_autojs import AutoJs
    except Exception as exc:  # noqa: BLE001
        return False, "AutoJS 模块不可用（scripts/test/lib/mt_autojs.py 导入失败：%s）" % exc
    try:
        client = AutoJs()
    except Exception as exc:  # noqa: BLE001
        return False, "AutoJS 客户端构造失败：%s" % exc
    result = client.probe()
    if result.get("ok"):
        return True, ""
    return False, AUTOAJS_REASON_TIMEOUT.sub(autojs_timeout_reason, str(result.get("reason") or "未知原因"))


def invoke_eval(version, kind, value_json, from_file, code, side, expect_json, label):
    """eval / state 断言：取到值 → 用 expect 引擎判定 → 打结论行。"""
    if kind not in ("eval", "state"):
        kind = "eval"
    tag = "MT_ASSERT_" + kind.upper()
    lab = ("（" + label + "）") if label else ""

    value = None
    have_value = False
    if from_file:
        if not os.path.isfile(from_file):
            write_mt_err_line(tag + ": ERROR — 值文件不存在：" + from_file)
            return MT_EXIT_ERROR
        with open(from_file, "r", encoding="utf-8") as handle:
            ok, parsed, err = parse_json_text(handle.read())
        if not ok:
            write_mt_err_line(tag + ": ERROR — 值文件 JSON 不合法：" + err)
            return MT_EXIT_ERROR
        value = parsed
        have_value = True
    elif value_json:
        ok, parsed, err = parse_json_text(value_json)
        if not ok:
            write_mt_err_line(tag + ": ERROR — --value-json 不合法：" + err)
            return MT_EXIT_ERROR
        value = parsed
        have_value = True
    elif code:
        ok, reason = autojs_endpoint()
        if not ok:
            write_mt_line(tag + ": BLOCKED — AutoJS 端点不可用（" + reason + "）=> 求值不可用，判定 BLOCKED（不是 FAIL）" + lab)
            return MT_EXIT_BLOCKED
        try:
            from mt_autojs import AutoJs
            client = AutoJs()
            result = client.eval_js(code, side=(side or "auto"), mode="json")
        except Exception as exc:  # noqa: BLE001
            write_mt_err_line(tag + ": ERROR — 调用 AutoJS eval_js 抛异常：" + str(exc))
            return MT_EXIT_ERROR
        if not result.get("ok"):
            err = str(result.get("error") or "脚本抛错（无 error 文本）")
            write_mt_line(tag + ": FAIL — 内联 code 求值失败：" + err + lab)
            return MT_EXIT_FAIL
        value = result.get("value_parsed")
        if value is None:
            value = result.get("value")
        value = plain(value)
        have_value = True
    if not have_value:
        write_mt_err_line(tag + ": ERROR — 需要 --value-json / --from-file / --code 之一（没有可判定的值）")
        return MT_EXIT_ERROR

    evidence = compact_json(value, EXPECT_ECHO_MAX)
    if not expect_json:
        if kind == "state":
            write_mt_line(tag + ": PASS — 证据 " + evidence + "（无判据）" + lab)
            return MT_EXIT_PASS
        write_mt_err_line(tag + ": ERROR — eval 断言缺少 expect（判据缺失不等于通过）")
        return MT_EXIT_ERROR
    ok, expected, err = parse_json_text(expect_json)
    if not ok:
        write_mt_err_line(tag + ": ERROR — expect JSON 不合法：" + err)
        return MT_EXIT_ERROR
    result = expect_match(expected, value)
    if not result["ok"]:
        write_mt_line(tag + ": FAIL — " + " | ".join(result["mismatches"]) + "（实际值 " + evidence + "）" + lab)
        return MT_EXIT_FAIL
    extra_text = ""
    if result["extras"]:
        extra_text = "；期望未覆盖的键已回显：" + ", ".join(result["extras"])
    write_mt_line(
        tag + ": PASS — 期望 " + compact_json(expected, EXPECT_ECHO_MAX) + " 实际 " + evidence + extra_text + lab
    )
    return MT_EXIT_PASS


# ── 入口 ──────────────────────────────────────────────────────────────────
USAGE = """用法：
  mt_assert.py snapshot --version <ver> [--window launch|case|whole]
  mt_assert.py log      --version <ver> --pattern <正则> [--source latest|debug|kubejs_client]
                        [--window case|launch|whole] [--since-snapshot] [--no-snapshot]
  mt_assert.py absent   --version <ver> --pattern <正则> [--source ...] [--window ...]
  mt_assert.py crash    --version <ver>
  mt_assert.py kubejs   --version <ver>
  mt_assert.py mixin    --version <ver> [--window launch|case|whole]
  mt_assert.py eval     --version <ver> [--value-json <json> | --from-file <path> | --code <js>]
                        [--side auto|server|client] [--expect <json>] [--label <文本>]
  mt_assert.py state    同上（缺 --expect 时只打印证据）

窗口：case=自本用例起（log/absent 缺省）/ launch=自 launch 起（snapshot/mixin 缺省）/
      whole=整文件（--no-snapshot）；--since-snapshot 等价 --window case。
退出码：PASS 0 / FAIL 1 / ERROR 2 / BLOCKED 11；INCONCLUSIVE 一律 0 且不记 FAIL。
"""


def parse_args(argv):
    args = {
        "cmd": "",
        "version": "",
        "pattern": "",
        "source": "latest",
        "window": "",
        "value_json": "",
        "from_file": "",
        "code": "",
        "side": "auto",
        "expect": "",
        "label": "",
    }
    index = 0
    total = len(argv)
    while index < total:
        token = str(argv[index])
        if token in ("-h", "--help") or (not token.startswith("-") and token.lower() == "help" and not args["cmd"]):
            sys.stdout.write(USAGE)
            raise SystemExit(MT_EXIT_PASS)
        key = token.lstrip("-").lower()
        if not token.startswith("-"):
            if args["cmd"]:
                write_mt_error_line("未知参数 " + token)
                raise SystemExit(MT_EXIT_ERROR)
            args["cmd"] = key
            index += 1
            continue
        if key in ("version", "pattern", "source", "window", "value-json", "from-file", "code",
                   "side", "expect", "label"):
            if index + 1 >= total:
                write_mt_error_line("缺少 --%s 的值" % key)
                raise SystemExit(MT_EXIT_ERROR)
            value = str(argv[index + 1])
            args[key.replace("-", "_")] = value.lower() if key in ("window", "side") else value
            index += 2
            continue
        if key == "since-snapshot":
            args["window"] = "case"
            index += 1
            continue
        if key == "no-snapshot":
            args["window"] = "whole"
            index += 1
            continue
        write_mt_error_line("未知参数 " + token)
        raise SystemExit(MT_EXIT_ERROR)
    return args


def main(argv=None):
    init_console()
    argv = list(sys.argv[1:] if argv is None else argv)
    args = parse_args(argv)
    cmd = args["cmd"]

    if cmd not in ("snapshot", "log", "absent", "crash", "kubejs", "mixin", "eval", "state"):
        write_mt_error_line("必须指定子命令 snapshot / log / absent / crash / kubejs / mixin / eval / state")
        return MT_EXIT_ERROR
    if cmd in ("eval", "state") and args["side"] not in ("auto", "server", "client"):
        write_mt_error_line("--side 只接受 auto / server / client（收到 %s）" % args["side"])
        return MT_EXIT_ERROR
    if not args["version"]:
        write_mt_error_line("必须指定 --version")
        return MT_EXIT_ERROR
    if not assert_version(args["version"]):
        return MT_EXIT_ERROR
    if cmd in ("log", "absent") and not args["pattern"]:
        write_mt_error_line("%s 子命令必须指定 --pattern" % cmd)
        return MT_EXIT_ERROR
    if args["source"] not in LOG_SOURCES:
        write_mt_error_line("未知断言通道 " + args["source"])
        return MT_EXIT_ERROR
    window = args["window"]
    if window and window not in ("case", "launch", "whole"):
        write_mt_error_line("--window 只接受 case / launch / whole（收到 %s）" % window)
        return MT_EXIT_ERROR

    version = args["version"]
    if cmd == "snapshot":
        return invoke_snapshot(version, window or "launch")
    if cmd == "log":
        return invoke_log(version, args["pattern"], args["source"], window or "case")
    if cmd == "absent":
        return invoke_absent(version, args["pattern"], args["source"], window or "case")
    if cmd == "crash":
        return invoke_crash(version)
    if cmd == "kubejs":
        return invoke_kubejs(version)
    if cmd == "mixin":
        return invoke_mixin(version, window or "launch")
    return invoke_eval(
        version, cmd, args["value_json"], args["from_file"], args["code"],
        args["side"], args["expect"], args["label"],
    )


if __name__ == "__main__":
    sys.exit(main())
