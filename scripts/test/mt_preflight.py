#!/usr/bin/env python3
"""mt_preflight —— 阶段 P 前置检查（bash + python 原生实现）。

等价旧编排器的 `--version <v>` / `--all` / `--channel <x>` 三种形态
与退出码（0 / 10 / 11 / 2），供 `scripts/test/win/lib/phases.sh` 的 `--phase preflight` 调用。

设计口径（与 ps1 逐字对齐的部分见各函数注释）：
  * 机器行 / 检查行 / 汇总行 / 退出码照 ps1 复刻：`MT_CHANNEL: mode=…`、`  [OK  ] <名字>: <详情>`、
    `MT_PREFLIGHT_AUTOJS: <STATE> — <详情>`、`MT_PREFLIGHT: FAIL — …`（exit 10）、
    `MT_PREFLIGHT: OK — …`（exit 0）、参数错误 `MT_ERROR: …`（exit 2）。
  * 能力缺失一律记 **BLOCKED(11)** 并在输出里点名，绝不伪装 PASS、绝不静默跳过：
      - 模组来源：闸门本身是静态守门（阶段 P 与 TESTING-SPEC 第 9 节共用同一套规则），
        现由 tools/check_mod_sources.py 承担（ps1 已废弃）。本脚本按同源方式调用它
        （python <gate> -Root <root>）并解析其 MOD_SOURCE_GATE: 行，**不复制判定逻辑**；
        .py 不存在时直接 BLOCKED(11)，绝不放行。
      - 输入法（仅 native 通道）：走 lib/mt_ime.py（ps1 的 Mt.Win32 即由它移植而来，
        describe / KLID_EN_US 逐字一致）。
  * 进程枚举走 ctypes（Toolhelp32 + NtQueryInformationProcess + EnumWindows），不依赖 psutil 或任何外部进程工具；
    枚举 API 本身不可用（非 Windows）时记 BLOCKED(11) —— ps1 的非 win32 pgrep 分支随「测试链仅
    Windows」一并删除，python 侧没有等价实现，因此既不能判 OK 也不能静默跳过。
  * `MT_PREFLIGHT_TEST_PROCS` 钩子与 ps1 同义（JSON 数组替换真实进程表，非法 JSON 告警后回退）。

除「模组来源」闸门（由 tools/check_mod_sources.py 承担）外不启动外部脚本；其余只用标准库。
"""

import fnmatch
import hashlib
import json
import os
import re
import subprocess
import sys
import urllib.parse

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "lib"))

import mt_compat as mc  # noqa: E402  (路径注入必须在 import 前完成)

EXIT_ERROR = mc.MT_EXIT_ERROR
EXIT_PREFLIGHT = mc.MT_EXIT_PREFLIGHT
EXIT_BLOCKED = mc.MT_EXIT_BLOCKED
RELEASE_LINE_BRANCHES = ["multi-1.20.1-1.21.1"]
KLID_EN_US = "00000409"
TEST_DIR = mc.TEST_DIR
KEEP_ALIVE_LEGACY = os.path.join(os.path.join(TEST_DIR, "cases"), ".mt_keep_alive")
AUTOJS_WANTED = (
    ("1.21.1", "AutoJS-neoforge-1.21.1-1.0.0.jar"),
    ("26.1.2", "AutoJS-neoforge-26.1-1.0.0.jar"),
)
PROVENANCE_RX = re.compile("([A-Za-z0-9._-]+[.]jar).*?(?<![0-9a-fA-F])([0-9a-fA-F]{64})(?![0-9a-fA-F])")
WS = "[" + " " + chr(9) + "]"

_CHANNEL = {"mode": "", "error": ""}
_REQUESTED = {"channel": ""}


def usage():
    return chr(10).join([
        "用法: python scripts/test/mt_preflight.py [--version <版本>] [--all] [--channel <x>] [--json]",
        "",
        "阶段 P 前置检查。",
        "",
        "选项:",
        "  --version <v>  只检查指定版本：" + " ".join(mc.VERSIONS),
        "  --all          检查全部版本（默认）",
        "  --channel <x>  autojs（默认）| native",
        "  --json         兼容旧参数，当前无结构化输出（吞掉，不改变行为）",
        "  -h, --help     显示本帮助",
        "",
        "",
        "退出码: 0 = 全部满足 | 10 = 存在不满足项（前置失败，不触达游戏）",
        "        | 11 = 能力缺失 BLOCKED（本机无法判定，不放行）| 2 = 参数 / 通道错误",
        "",
        "能力缺失: 某项在本机无法判定（缺 python 跑不了模组来源闸门 / 非 Windows 枚举不了进程）时",
        "          记 MT_PREFLIGHT: BLOCKED — … 并退出 11，绝不静默跳过，也绝不伪装 PASS。",
    ])


# ── 通道 ──────────────────────────────────────────────────────────────────
def resolve_channel(requested):
    if _CHANNEL["mode"]:
        return _CHANNEL["mode"]
    try:
        _CHANNEL["mode"] = mc.channel_mode(requested)
    except ValueError as exc:
        _CHANNEL["error"] = str(exc)
        _CHANNEL["mode"] = "native"
    return _CHANNEL["mode"]


# ── 分支（信息性：回显 + 告警，不拦停）────────────────────────────────────
def branch_check(root):
    try:
        proc = subprocess.run(
            ["git", "-C", root, "rev-parse", "--abbrev-ref", "HEAD"],
            capture_output=True, timeout=60,
        )
    except (OSError, subprocess.SubprocessError) as exc:
        return (True, "无法读取分支（" + str(exc) + "）—— 信息性检查，不拦停")
    if proc.returncode != 0:
        return (True, "无法读取分支（" + proc.stderr.decode("utf-8", "replace").strip() + "）—— 信息性检查，不拦停")
    branch = proc.stdout.decode("utf-8", "replace").strip()
    if not branch:
        return (True, "分支名读出为空 —— 信息性检查，不拦停")
    if branch in RELEASE_LINE_BRANCHES:
        return (True, branch + "（发布线分支）")
    return (True, branch + " —— WARN: 非发布线分支（发布线 " + " / ".join(RELEASE_LINE_BRANCHES)
            + "）；按 2026-09-17 用户裁决放行，不拦停，仅告警")


# ── 输入语言 ──────────────────────────────────────────────────────────────
def ime_check():
    if resolve_channel(_REQUESTED["channel"]) != "native":
        return ("OK", "autojs 通道：不投递真实按键，en-US 扫描码布局与本检查无关（MT_CHANNEL: mode=autojs）")
    try:
        import mt_ime  # 惰性导入：autojs 通道一行 Win32 都不碰（与 ps1 的惰性导入同义）
    except Exception as exc:  # noqa: BLE001 —— 导入失败只可能是能力缺失
        return ("BLOCKED", "mt_ime 不可用（" + str(exc) + "）—— native 通道需要它读取当前线程的键盘布局（KLID）")
    try:
        current = mt_ime.langid_of_thread(0)
        available = mt_ime.en_us_available()
    except Exception as exc:  # noqa: BLE001
        return ("BLOCKED", "mt_ime 读取键盘布局失败（" + str(exc) + "）—— 本项无法判定")
    if not available:
        return ("FAIL", "en-US 键盘布局不可用 —— 请在系统设置安装「英语(美国)」键盘"
                        "（注入按美式扫描码投递，并在注入前自动切换目标窗口）")
    return ("OK", "en-US 布局可用（KLID " + mt_ime.KLID_EN_US + "）；当前系统输入法为 "
            + mt_ime.describe(current) + " —— 无需手动切换，注入前自动切目标窗口")


# ── 进程表 ────────────────────────────────────────────────────────────────
def process_table():
    raw = os.environ.get("MT_PREFLIGHT_TEST_PROCS", "")
    if raw is not None and raw.strip():
        try:
            parsed = json.loads(raw)
            if not isinstance(parsed, list):
                parsed = [parsed]
            out = []
            for item in parsed:
                if not isinstance(item, dict):
                    raise TypeError("进程表元素不是对象：" + repr(item))
                cmd = ""
                if "command_line" in item:
                    cmd = "" if item["command_line"] is None else str(item["command_line"])
                elif "cmdline" in item:
                    cmd = "" if item["cmdline"] is None else str(item["cmdline"])
                out.append({"pid": int(item["pid"]), "command_line": cmd})
            return out
        except (ValueError, TypeError, KeyError) as exc:
            mc.write_mt_warn("MT_PREFLIGHT_TEST_PROCS 解析失败（回退真实进程扫描）：" + str(exc))
    return mc.list_java_processes()


def preview(text):
    if not text:
        return ""
    if len(text) <= 160:
        return text
    return text[:160]


def wrapper_procs(paths, processes):
    selector = ":" + str(paths.subproject) + ":"
    out = []
    for proc in processes:
        cmd = proc.get("command_line", "")
        if "gradle-wrapper.jar" not in cmd:
            continue
        if selector not in cmd:
            continue
        out.append(proc)
    return out


def leftover_scan(paths, processes):
    ours = []
    others = []
    for proc in processes:
        cmd = proc.get("command_line", "")
        if mc.test_pipeline_process(paths, int(proc.get("pid", 0)), cmd):
            ours.append(int(proc.get("pid", 0)))
        elif "minecraft" in cmd.lower():
            others.append(int(proc.get("pid", 0)))
    return ours, others


def keep_alive_reason(version):
    candidates = (os.path.join(os.path.join(TEST_DIR, "cases"), ".mt_keep_alive-" + version), KEEP_ALIVE_LEGACY)
    for path in candidates:
        if not os.path.isfile(path):
            continue
        why = "（未记录原因）"
        try:
            with open(path, "r", encoding="utf-8-sig", errors="replace") as fh:
                text = fh.read().strip()
            if text:
                why = text
        except OSError:
            why = "（读取标记失败）"
        return path + " ：" + why
    return None


def _pids_text(items):
    return ",".join(str(x) for x in items)


def leftover_check(version):
    paths = mc.get_paths(version)
    try:
        processes = process_table()
    except mc.ProcessEnumerationUnavailable as exc:
        return ("BLOCKED", "无法枚举 java 进程（" + str(exc) + "）—— 非 Windows 下 ps1 的 pgrep 分支在"
                           " python 侧没有等价实现，本项无法判定（不放行）")

    blocking = wrapper_procs(paths, processes)
    if blocking:
        pid_text = ", ".join(str(x["pid"]) for x in blocking)
        cmd_text = " | ".join(preview(x["command_line"]) for x in blocking[:2])
        return (False, "本线（" + version + "）有正在进行的 Gradle 包装器进程 PID=" + pid_text + "（"
                + cmd_text + "）—— 本线 build/run 包装器在跑，**只报告不杀**；等它结束，或先执行 "
                + "bash scripts/test/win/mt.sh --version " + version + " --phase stop 收停本线测试进程")

    ours, others = leftover_scan(paths, processes)
    if ours:
        reason = keep_alive_reason(version)
        if reason:
            return (False, "本线遗留 PID=" + _pids_text(ours) + "，但存在失败取证标记（" + reason
                    + "）——按设计保留现场；取证完成后执行 bash scripts/test/win/mt.sh --version "
                    + version + " --phase stop --force")
        stale = list(ours)
        mc.write_mt_line("  [ .. ] 遗留进程(" + version + "): 检出 " + str(len(stale))
                         + " 个本线遗留进程（PID=" + _pids_text(stale) + "）")
        try:
            pids = mc.stop_version_processes(paths, quiet=True)
        except mc.ProcessEnumerationUnavailable as exc:
            return ("BLOCKED", "清理遗留进程前无法枚举进程（" + str(exc) + "）—— 本项无法判定（不放行）")
        pending = ",".join(str(pid) for pid in pids) if pids else _pids_text(stale)
        mc.write_mt_line("  [ .. ] MT_PREFLIGHT: PENDING pid=" + pending
                         + "（脚本不终止进程；由 Agent 用 mcp__windows__Process(mode:\"kill\") 终止）")
        return ("BLOCKED", "本线遗留 PID=" + _pids_text(stale)
                + "；先由 Agent 用 windows MCP 终止（或 bash scripts/test/win/stop.sh --version "
                + version + " 拿 PID），再重跑前置检查")

    if others:
        return (True, "无本线（" + version + "）遗留；另有 " + str(len(others))
                + " 个其它 Minecraft 客户端在运行（PID=" + _pids_text(others) + "）——按线隔离放行，仅占用显存")
    return (True, "无本线（" + version + "）遗留进程")


# ── AutoJS（静态检查：vendor 哈希 / 端点 / 该线 mods 投放）─────────────────
def glob_ci(directory, pattern):
    if not os.path.isdir(directory):
        return []
    out = []
    try:
        names = os.listdir(directory)
    except OSError:
        return []
    for name in names:
        if not os.path.isfile(os.path.join(directory, name)):
            continue
        if fnmatch.fnmatch(name.lower(), pattern.lower()):
            out.append(name)
    out.sort()
    return out


def provenance_hashes(path):
    table = {}
    if not os.path.isfile(path):
        return table
    try:
        with open(path, "r", encoding="utf-8-sig", errors="replace") as fh:
            text = fh.read()
    except OSError:
        return table
    for line in mc.split_crlf(text):
        match = PROVENANCE_RX.search(line)
        if match:
            table[match.group(1)] = match.group(2).upper()
    return table


def sha256_upper(path):
    digest = hashlib.sha256()
    with open(path, "rb") as fh:
        while True:
            chunk = fh.read(1 << 20)
            if not chunk:
                break
            digest.update(chunk)
    return digest.hexdigest().upper()


def autojs_check(root):
    vendor_dir = os.path.join(os.path.join(TEST_DIR, "resources"), os.path.join("mods", "autojs"))
    prov_map = provenance_hashes(os.path.join(vendor_dir, "PROVENANCE.md"))
    issues = []
    warns = []
    vendor_ok = 0

    for _version, file_name in AUTOJS_WANTED:
        jar = os.path.join(vendor_dir, file_name)
        if not os.path.isfile(jar):
            issues.append("vendor jar 缺失：“" + file_name + "”")
            continue
        if file_name not in prov_map:
            issues.append("PROVENANCE 未登记 “" + file_name + "”（无法复核哈希）")
            continue
        real = sha256_upper(jar)
        if real != prov_map[file_name]:
            issues.append(file_name + " 哈希不符（实际 " + real[:16] + "，PROVENANCE " + prov_map[file_name][:16] + "）")
            continue
        vendor_ok += 1

    endpoint = ""
    if "MT_AUTOJS_ENDPOINT" in mc.CONF:
        endpoint = str(mc.CONF["MT_AUTOJS_ENDPOINT"]).strip()
    if not endpoint:
        issues.append("mt.conf 未配置 MT_AUTOJS_ENDPOINT")
    else:
        ok_url = False
        try:
            parts = urllib.parse.urlsplit(endpoint)
            ok_url = bool(parts.scheme in ("http", "https") and parts.netloc)
        except ValueError:
            ok_url = False
        if not ok_url:
            issues.append("MT_AUTOJS_ENDPOINT 不是合法 http(s) URL：" + endpoint)

    for version, _file_name in AUTOJS_WANTED:
        paths = mc.get_paths(version)
        present = glob_ci(paths.mods_dir, "AutoJS-*.jar")
        if not present:
            warns.append(version + " 的 mods 里还没有 autojs jar（冷启动前执行 bash scripts/test/win/mt.sh --version "
                         + version + " --phase env 装配 run/<版本>/mods）")
        elif len(present) > 1:
            issues.append(version + " 的 mods 里有 " + str(len(present))
                          + " 个 autojs jar（同族多份 ⇒ FML Duplicate mods）：" + ", ".join(present))

    state = "OK"
    if issues:
        state = "FAIL"
    elif warns:
        state = "WARN"

    detail = "端点 " + (endpoint if endpoint else "(未配置)") + "；vendor " + str(vendor_ok) + "/" + str(len(AUTOJS_WANTED)) + " 校验通过"
    if issues:
        detail += "；" + "；".join(issues)
    if warns:
        detail += "；" + "；".join(warns)

    mc.write_mt_line("MT_PREFLIGHT_AUTOJS: " + state + " — " + detail)
    return (state, detail)


# ── 兼容栈 / 可写性 / 前置库配对 ──────────────────────────────────────────
def compat_check(version):
    paths = mc.get_paths(version)
    if not os.path.isdir(paths.pack_mods_dir):
        if version == "1.20.1":
            return (True, "生产环境目录不存在（仅影响兼容性人工验证）：" + paths.pack_mods_dir)
        return (False, "整合包 mods 目录不存在：" + paths.pack_mods_dir)
    if version == "1.21.1":
        found = {"sodium": False, "iris": False, "modernfix": False}
        for name in glob_ci(paths.pack_mods_dir, "*.jar"):
            low = name.lower()
            for key in found:
                if key in low:
                    found[key] = True
        missing = [key for key in ("sodium", "iris", "modernfix") if not found[key]]
        if missing:
            return (False, "整合包缺少兼容模组：" + ", ".join(missing))
    return (True, str(paths.pack_mods_dir))


def writable_check(version):
    paths = mc.get_paths(version)
    try:
        if not os.path.isdir(paths.run_dir):
            os.makedirs(paths.run_dir, exist_ok=True)
        probe = os.path.join(paths.run_dir, ".mt_write_probe")
        with open(probe, "w", encoding="utf-8", newline="") as fh:
            fh.write("ok")
        os.remove(probe)
    except OSError as exc:
        return (False, "run 目录不可写：" + str(exc))
    return (True, str(paths.run_dir))


def gradle_property(path, key):
    if not os.path.isfile(path):
        return None
    try:
        with open(path, "r", encoding="utf-8-sig", errors="replace") as fh:
            text = fh.read()
    except OSError:
        return None
    rx = re.compile("^" + WS + "*" + re.escape(key) + WS + "*=(.*)$")
    for line in mc.split_crlf(text):
        match = rx.match(line)
        if match:
            return match.group(1).strip()
    return None


def lib_pairing_check(version):
    paths = mc.get_paths(version)
    props = os.path.join(os.path.join(paths.root, paths.subproject), "gradle.properties")
    pin = gradle_property(props, "starengine_version")
    if not pin:
        return (False, props + " 里读不到 starengine_version")
    if not os.path.isdir(paths.mods_dir):
        return (True, "无 mods 目录（库由 Gradle runtimeClasspath 提供 " + pin + "）")
    libs = glob_ci(paths.mods_dir, "starengine-*.jar")
    if not libs:
        return (True, "mods 内无库 jar（由 Gradle runtimeClasspath 提供 " + pin + "）")
    if len(libs) > 1:
        return (False, "mods 内 starengine-*.jar 有 " + str(len(libs)) + " 份（应恰 1 份）：" + " / ".join(libs))
    name = libs[0]
    if not name.lower().endswith(("-" + pin + ".jar").lower()):
        return (False, "库 jar 与本线引脚不一致：实际 " + name + " ；期望 *-" + pin
                + ".jar ⟹ 照做：bash scripts/test/win/mt.sh --version " + version + " --phase env 重新装配本线运行环境")
    return (True, name + "（与 starengine_version=" + pin + " 一致）")


def mod_sources_check(root):
    tools_dir = os.path.join(root, "tools")
    gate_py = os.path.join(tools_dir, "check_mod_sources.py")
    if not os.path.isfile(gate_py):
        return ("BLOCKED", "找不到 " + gate_py + "（模组来源闸门 .py 缺失）—— 能力缺失，不放行")
    argv = [sys.executable, gate_py, "-Root", root]
    label = gate_py
    try:
        proc = subprocess.run(argv, capture_output=True, timeout=120)
    except (OSError, subprocess.SubprocessError) as exc:
        return ("FAIL", "运行模组来源闸门失败（" + str(exc) + "；" + label + "）")
    verdict = ""
    for line in mc.split_crlf(proc.stdout.decode("utf-8", "replace")):
        if "MOD_SOURCE_GATE:" in line:
            verdict = line
    if not verdict:
        return ("FAIL", "无结论行（exit=" + str(proc.returncode) + "；"
                + proc.stderr.decode("utf-8", "replace").strip() + "）")
    return ("OK" if proc.returncode == 0 else "FAIL", verdict.strip())


# ── 汇总 ──────────────────────────────────────────────────────────────────
def _pair(name, value):
    return {"name": name, "kind": "pair", "value": value}


def _state(name, value):
    return {"name": name, "kind": "state", "value": value}


def run_all(versions):
    root = mc.get_paths(versions[0]).root
    gradlew_exists = os.path.isfile(os.path.join(root, "gradlew"))

    resolve_channel(_REQUESTED["channel"])
    if _CHANNEL["error"]:
        mc.write_mt_err_line("MT_CHANNEL: ERROR — " + _CHANNEL["error"])
        return EXIT_ERROR

    checks = []
    checks.append(_pair("分支", branch_check(root)))
    checks.append(_state("输入法", ime_check()))
    checks.append(_state("AutoJS 通道", autojs_check(root)))
    checks.append(_state("模组来源", mod_sources_check(root)))
    checks.append(_pair("Gradle 包装器", (gradlew_exists, "gradlew 存在" if gradlew_exists else "缺少 gradlew")))
    for version in versions:
        checks.append(_pair("遗留进程 " + version, leftover_check(version)))
        checks.append(_pair("兼容栈 " + version, compat_check(version)))
        checks.append(_pair("run 可写 " + version, writable_check(version)))
        checks.append(_pair("前置库配对 " + version, lib_pairing_check(version)))

    failed = []
    warned = []
    blocked = []
    for check in checks:
        if check["kind"] == "pair":
            ok = bool(check["value"][0])
            detail = str(check["value"][1])
            mark = "OK  " if ok else "FAIL"
            warn = False
            is_blocked = False
        else:
            mark = str(check["value"][0])
            if mark not in ("OK", "WARN", "FAIL", "BLOCKED"):
                mark = "FAIL"
            detail = str(check["value"][1])
            ok = mark == "OK"
            warn = mark == "WARN"
            is_blocked = mark == "BLOCKED"
        mark_disp = "OK  " if ok else mark
        mc.write_mt_line("  [" + mark_disp + "] " + check["name"] + ": " + detail)
        entry = check["name"] + "（" + detail + "）"
        if is_blocked:
            blocked.append(entry)
        elif warn:
            warned.append(entry)
        elif not ok:
            failed.append(entry)

    if failed:
        mc.write_mt_err_line("")
        mc.write_mt_err_line("MT_PREFLIGHT: FAIL — " + str(len(failed)) + " 项不满足：" + "; ".join(failed))
        mc.write_mt_err_line("修复后重跑本阶段；前置失败不触达游戏，不作为功能缺陷。")
        return EXIT_PREFLIGHT

    mc.write_mt_line("")
    if blocked:
        mc.write_mt_err_line("MT_PREFLIGHT: BLOCKED — " + str(len(blocked)) + " 项能力缺失（本机无法判定，不放行）："
                             + "; ".join(blocked))
        mc.write_mt_err_line("补齐上述能力后重跑；本阶段失败不触达游戏，不作为功能缺陷。")
        return EXIT_BLOCKED
    if warned:
        mc.write_mt_line("MT_PREFLIGHT: WARN — " + str(len(warned)) + " 项为提示（不阻塞）：" + "; ".join(warned))
    mc.write_mt_line("MT_PREFLIGHT: OK — " + str(len(checks)) + " 项全部满足（" + ", ".join(versions) + "）")
    return 0


# ── 入口 ──────────────────────────────────────────────────────────────────
def main(argv):
    mc.init_console()
    version = ""
    all_flag = False
    requested = ""

    index = 0
    while index < len(argv):
        token = str(argv[index])
        key = token.lstrip("-").lower()
        if key == "version":
            if index + 1 >= len(argv):
                mc.write_mt_error_line("缺少 --version 的值")
                return EXIT_ERROR
            version = str(argv[index + 1])
            index += 2
        elif key == "all":
            all_flag = True
            index += 1
        elif key == "channel":
            if index + 1 >= len(argv):
                mc.write_mt_error_line("缺少 --channel 的值")
                return EXIT_ERROR
            requested = str(argv[index + 1])
            index += 2
        elif key == "json":
            index += 1
        elif token.startswith("-") and key in ("help", "h"):
            mc.write_mt_line(usage())
            return 0
        else:
            mc.write_mt_error_line("未知参数 " + token)
            return EXIT_ERROR

    _REQUESTED["channel"] = requested
    versions = list(mc.VERSIONS) if (all_flag or not version) else [version]
    for item in versions:
        if not mc.assert_version(item):
            return EXIT_ERROR

    return run_all(versions)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
