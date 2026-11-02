#!/usr/bin/env python3
"""mt_compat —— pwsh 测试台资产的 python 等价层。

只承载 mt_report.py / mt_preflight.py 需要的部分，函数名与 Mt.*.psm1 一一对应：

  Mt.Paths.psm1   -> VERSIONS / LOADER / SUBPROJECT / get_paths / run_label / active_run_id /
                     run_start_ts / current_shots / assert_version
  Mt.Conf.psm1    -> load_conf / CONF
  Mt.Phase.psm1   -> init_console / write_mt_line / write_mt_err_line / write_mt_warn /
                     write_mt_error_line / json_dump / MT_EXIT_*
  Mt.Proc.psm1    -> read_log_window / log_anchor / list_java_processes /
                     test_client_process / test_pipeline_process / stop_version_processes
  Mt.Channel.psm1 -> channel_mode

约束：
  · 纯标准库；不启动 pwsh / powershell 子进程，不依赖 psutil / wmic。
  · 一律 LF 与 UTF-8 无 BOM 输出（与 Initialize-MtConsole 同义）。
  · 源码里不出现反斜杠字面量：路径分隔与换行由 BSL / NL 常量给出，
    以免经 shell 或编辑器二次转义。
"""
from __future__ import annotations

import ctypes
import hashlib
import json
import os
import re
import subprocess
import sys
import time
from datetime import datetime
from types import SimpleNamespace

NL = chr(10)
CR = chr(13)
BSL = chr(92)

IS_WINDOWS = os.name == "nt"

LIB_DIR = os.path.dirname(os.path.abspath(__file__))
TEST_DIR = os.path.dirname(LIB_DIR)
ROOT = os.path.dirname(os.path.dirname(TEST_DIR))
CONF_FILE = os.path.join(TEST_DIR, "mt.conf")
RUNS_FILE = os.path.join(TEST_DIR, "cases", ".mt_active_run")

WORLD_NAME = "testworld"
SHOTS_MANIFEST = ".mt_shots.json"

VERSIONS = ("1.21.1", "1.20.1", "26.1.2", "fabric-1.20.1")

LOADER = {
    "1.21.1": "neoforge",
    "1.20.1": "forge",
    "26.1.2": "neoforge",
    "fabric-1.20.1": "fabric",
}

SUBPROJECT = {
    "1.21.1": "neoforge-1.21.1",
    "1.20.1": "forge-1.20.1",
    "26.1.2": "neoforge-26.1.2",
    "fabric-1.20.1": "fabric-1.20.1",
}

DEFAULT_PACK_MODS = {
    "1.21.1": "D:" + BSL + ".minecraft" + BSL + "versions" + BSL + "狐の航空学 Voxy Edition" + BSL + "mods",
    "1.20.1": "D:" + BSL + ".minecraft" + BSL + "versions" + BSL + "1.20.1-Forge 模组测试" + BSL + "mods",
    "26.1.2": "D:" + BSL + ".minecraft" + BSL + "versions" + BSL + "26.1.2-NeoForge 模组测试" + BSL + "mods",
    "fabric-1.20.1": "D:" + BSL + ".minecraft" + BSL + "versions" + BSL + "1.20.1-Fabric 模组测试" + BSL + "mods",
}

CONF_KEYS = {
    "1.21.1": "MT_PACK_MODS_NEOFORGE",
    "26.1.2": "MT_PACK_MODS_NEOFORGE_26_1_2",
    "fabric-1.20.1": "MT_PACK_MODS_FABRIC",
}
CONF_KEY_DEFAULT = "MT_PACK_MODS_FORGE"

MT_EXIT_PASS = 0
MT_EXIT_FAIL = 1
MT_EXIT_ERROR = 2
MT_EXIT_PREFLIGHT = 10
MT_EXIT_BLOCKED = 11
MT_EXIT_TIMEOUT = 12

ANCHOR_PREFIX_BYTES = 65536
ANCHOR_CTIME_TOLERANCE_MS = 2000
ANCHOR_MTIME_TOLERANCE_MS = 2000


# ── 控制台与输出原语（Mt.Phase.psm1） ──────────────────────────────────────
def init_console():
    """UTF-8 无 BOM、不做换行翻译（显式 LF）。"""
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8", errors="replace", newline="")
        except (AttributeError, OSError, ValueError):
            pass


def write_mt_line(text=""):
    sys.stdout.write("" if text is None else str(text))
    sys.stdout.write(NL)
    sys.stdout.flush()


def write_mt_err_line(text=""):
    sys.stderr.write("" if text is None else str(text))
    sys.stderr.write(NL)
    sys.stderr.flush()


def write_mt_warn(text):
    write_mt_err_line("MT_WARN: " + str(text))


def write_mt_error_line(text):
    write_mt_err_line("MT_ERROR: " + str(text))


def json_dump(obj):
    """ConvertTo-MtJson：json.dumps(ensure_ascii=False, indent=2)，无末尾换行。"""
    return json.dumps(obj, ensure_ascii=False, indent=2)


def split_crlf(text):
    """PowerShell 的 -split 换行口径（CRLF / 单独 CR / LF）。"""
    return (text or "").replace(CR + NL, NL).replace(CR, NL).split(NL)


# ── 配置（Mt.Conf.psm1） ───────────────────────────────────────────────────
def load_conf(path=None):
    if path is None:
        path = CONF_FILE
    conf = {}
    try:
        with open(path, "rb") as fh:
            raw = fh.read()
    except OSError:
        return conf
    text = raw.decode("utf-8-sig", "replace")
    for line in split_crlf(text):
        line = line.strip()
        if not line:
            continue
        if line.startswith("#"):
            continue
        if "=" not in line:
            continue
        idx = line.index("=")
        key = line[:idx].strip()
        val = line[idx + 1:].strip()
        val = val.strip('"').strip("'")
        if key:
            conf[key] = val
    return conf


CONF = load_conf()


# ── 版本与路径（Mt.Paths.psm1） ────────────────────────────────────────────
def get_versions():
    return list(VERSIONS)


def assert_version(version):
    if version in VERSIONS:
        return True
    write_mt_err_line("MT_ERROR: 未知版本 " + str(version) + "（可选：" + " ".join(VERSIONS) + "）")
    return False


def resolve_side(version, side=""):
    if version != "fabric-1.20.1":
        return ""
    value = side if side else os.environ.get("MT_SIDE", "")
    if not value:
        value = "client"
    value = value.strip().lower()
    if value not in ("client", "server"):
        raise ValueError("未知 side " + value + "（" + version + " 只支持：client / server）")
    return value


def run_root(version, side=""):
    if version not in VERSIONS:
        raise ValueError("未知版本 " + str(version))
    if version == "fabric-1.20.1":
        return os.path.join(os.path.join(ROOT, SUBPROJECT[version]), "run", resolve_side(version, side))
    return os.path.join(os.path.join(ROOT, "run"), version)


def run_label(version, side=""):
    if version == "fabric-1.20.1":
        return SUBPROJECT[version] + "/run/" + resolve_side(version, side)
    return "run/" + version


def get_paths(version, side=""):
    if version not in VERSIONS:
        raise ValueError("未知版本 " + str(version))
    root = ROOT
    side_name = resolve_side(version, side)
    run_dir = run_root(version, side)
    logs_dir = os.path.join(run_dir, "logs")
    conf_key = CONF_KEYS.get(version, CONF_KEY_DEFAULT)
    if conf_key in CONF:
        raw_pack = CONF[conf_key]
    else:
        raw_pack = DEFAULT_PACK_MODS[version]
    subproject = SUBPROJECT[version]
    return SimpleNamespace(
        version=version,
        loader=LOADER[version],
        subproject=subproject,
        root=root,
        side=side_name,
        run_label=run_label(version, side),
        run_dir=run_dir,
        mods_dir=os.path.join(run_dir, "mods"),
        saves_dir=os.path.join(run_dir, "saves"),
        logs_dir=logs_dir,
        latest_log=os.path.join(logs_dir, "latest.log"),
        debug_log=os.path.join(logs_dir, "debug.log"),
        kubejs_log=os.path.join(logs_dir, "kubejs", "server.log"),
        kubejs_client_log=os.path.join(logs_dir, "kubejs", "client.log"),
        probe_log=os.path.join(run_dir, "astral_probe.log"),
        crash_dir=os.path.join(run_dir, "crash-reports"),
        shot_dir=os.path.join(run_dir, "screenshots"),
        client_world=os.path.join(run_dir, "saves", WORLD_NAME),
        server_world=os.path.join(run_dir, WORLD_NAME),
        task_build=":" + subproject + ":build",
        task_client=":" + subproject + ":runClient",
        task_server=":" + subproject + ":runServer",
        task_gametest=":" + subproject + ":runGameTestServer",
        pack_mods_dir=str(raw_pack).replace("/", BSL),
    )


def process_markers(paths):
    if paths.version == "fabric-1.20.1":
        return [
            ":" + paths.subproject + ":",
            SUBPROJECT[paths.version] + BSL + "run" + BSL + paths.side,
            paths.run_dir,
        ]
    return [":" + paths.subproject + ":", "run" + BSL + paths.version, paths.run_dir]


# ── 运行 id / 截图清单 ─────────────────────────────────────────────────────
def new_run_id():
    return datetime.now().strftime("%Y%m%d-%H%M%S")


def active_run_id():
    """Get-MtActiveRunId：粘性；读不到就现生成一个并落盘。"""
    try:
        with open(RUNS_FILE, "rb") as fh:
            text = fh.read().decode("utf-8", "replace").strip()
    except OSError:
        text = ""
    if text:
        return text
    rid = new_run_id()
    try:
        parent = os.path.dirname(RUNS_FILE)
        if not os.path.isdir(parent):
            os.makedirs(parent, exist_ok=True)
        with open(RUNS_FILE, "w", encoding="utf-8", newline="") as fh:
            fh.write(rid)
    except OSError:
        pass
    return rid


def run_start_ts(run_id):
    try:
        dt = datetime.strptime(str(run_id), "%Y%m%d-%H%M%S")
        ts = time.mktime(dt.timetuple()) + dt.microsecond / 1000000.0
        if ts < 0:
            return 0.0
        return float(ts)
    except (ValueError, OverflowError, OSError):
        return 0.0


def _normalize_shots(value):
    empty = {"run_id": "", "shots": []}
    if value is None:
        return empty
    if isinstance(value, list):
        if not value:
            return empty
        value = value[0]
        if value is None:
            return empty
    if not isinstance(value, dict):
        return empty
    out = {"run_id": "", "shots": []}
    rid = value.get("run_id")
    if rid is not None:
        out["run_id"] = str(rid)
    shots = value.get("shots")
    if shots is not None:
        out["shots"] = shots if isinstance(shots, list) else [shots]
    return out


def _read_shots(paths):
    manifest = os.path.join(paths.shot_dir, SHOTS_MANIFEST)
    if not os.path.isfile(manifest):
        return {"run_id": "", "shots": []}
    try:
        with open(manifest, "rb") as fh:
            parsed = json.loads(fh.read().decode("utf-8-sig", "replace"))
    except (OSError, ValueError):
        return {"run_id": "", "shots": []}
    return _normalize_shots(parsed)


def _mtime_seconds(path):
    try:
        return os.path.getmtime(path)
    except OSError:
        return float("-inf")


def current_shots(paths, run_id_value, since_unix=0.0):
    """Get-MtCurrentShots：先按 manifest（同名去重），否则回落「本次运行之后写入的 png」。"""
    data = _read_shots(paths)
    if str(data.get("run_id", "")) == str(run_id_value):
        seen = set()
        found = []
        for item in data.get("shots", []):
            name = ""
            if isinstance(item, dict):
                raw = item.get("file")
                name = "" if raw is None else str(raw)
            if name in seen:
                continue
            seen.add(name)
            full = os.path.join(paths.shot_dir, name)
            if not os.path.isfile(full):
                continue
            mtime = _mtime_seconds(full)
            if since_unix > 0 and mtime < since_unix:
                continue
            found.append((mtime, full))
        if found:
            found.sort(key=lambda pair: pair[0])
            return [full for (_m, full) in found]
    start = run_start_ts(run_id_value)
    if since_unix > 0 and since_unix > start:
        start = since_unix
    if not os.path.isdir(paths.shot_dir):
        return []
    fresh = []
    for name in sorted(os.listdir(paths.shot_dir)):
        if not name.lower().endswith(".png"):
            continue
        full = os.path.join(paths.shot_dir, name)
        if not os.path.isfile(full):
            continue
        mtime = _mtime_seconds(full)
        if mtime == float("-inf") or mtime < start:
            continue
        fresh.append((mtime, full))
    fresh.sort(key=lambda pair: pair[0])
    return [full for (_m, full) in fresh]


# ── 日志窗口（Mt.Proc.psm1） ───────────────────────────────────────────────
def _read_bytes_shared(path):
    try:
        with open(path, "rb") as fh:
            return fh.read()
    except OSError:
        return None


def _read_bytes_head(path, count):
    if count is None or int(count) <= 0:
        return b""
    try:
        with open(path, "rb") as fh:
            return fh.read(int(count))
    except OSError:
        return b""


def _read_rotated_bytes(path):
    if not os.path.isfile(path):
        return None
    if path.lower().endswith(".gz"):
        import gzip

        try:
            with gzip.open(path, "rb") as fh:
                return fh.read()
        except (OSError, EOFError):
            return None
    return _read_bytes_shared(path)


def _prefix_hash(data, count):
    if not data or count is None or int(count) <= 0:
        return ""
    n = min(int(count), len(data))
    return hashlib.sha256(data[:n]).hexdigest()


def _decode_log_bytes(data, start=0):
    if not data:
        return ""
    begin = max(0, int(start or 0))
    if begin >= len(data):
        return ""
    text = data[begin:].decode("utf-8", "replace")
    return text.replace(CR + NL, NL).replace(CR, NL)


def _ctime_ms(path):
    return int(os.stat(path).st_ctime * 1000)


def log_anchor(path):
    """Get-MtLogAnchor：{len, ctime_ms, mtime_ms, prefix_len, prefix_sha}。"""
    if not os.path.isfile(path):
        return None
    st = os.stat(path)
    head = _read_bytes_head(path, ANCHOR_PREFIX_BYTES)
    return {
        "len": int(st.st_size),
        "ctime_ms": int(st.st_ctime * 1000),
        "mtime_ms": int(st.st_mtime * 1000),
        "prefix_len": len(head),
        "prefix_sha": _prefix_hash(head, len(head)),
    }


def _anchor_value(anchor, key):
    if isinstance(anchor, dict) and key in anchor:
        return anchor[key]
    return None


ROTATED_RX = re.compile("^[0-9]{4}-[0-9]{2}-[0-9]{2}-[0-9]+[.]log([.]gz)?$")


def _rotated_logs(logs_dir, since_mtime, max_rotated=4):
    if not logs_dir or not os.path.isdir(logs_dir):
        return []
    picked = []
    for name in os.listdir(logs_dir):
        if not ROTATED_RX.match(name):
            continue
        full = os.path.join(logs_dir, name)
        if not os.path.isfile(full):
            continue
        mtime = _mtime_seconds(full)
        if mtime == float("-inf") or mtime < since_mtime:
            continue
        picked.append((mtime, full))
    picked.sort(key=lambda pair: pair[0], reverse=True)
    picked = picked[: int(max_rotated)]
    picked.sort(key=lambda pair: pair[0])
    return [full for (_m, full) in picked]


def read_log_window(path, offset=0, anchor=None, logs_dir="", max_rotated=16):
    """Read-MtLogWindow 的等价物：返回 {text, kind, offset, rotated, note}。"""
    cur = _read_bytes_shared(path)
    if cur is None:
        return {"text": "", "kind": "missing", "offset": 0, "rotated": 0, "note": ""}
    if offset is None or int(offset) <= 0:
        return {"text": _decode_log_bytes(cur), "kind": "from-zero", "offset": 0, "rotated": 0, "note": ""}
    offset = int(offset)
    anchor_ctime = _anchor_value(anchor, "ctime_ms")
    prefix_len = _anchor_value(anchor, "prefix_len")
    prefix_sha = _anchor_value(anchor, "prefix_sha")
    prefix_sha = "" if prefix_sha is None else str(prefix_sha)
    same_file = True
    if anchor_ctime is not None:
        same_file = False
        try:
            now_ctime = _ctime_ms(path)
            if abs(now_ctime - int(anchor_ctime)) <= ANCHOR_CTIME_TOLERANCE_MS:
                plen = int(prefix_len) if prefix_len is not None else 0
                same_file = _prefix_hash(cur, plen) == prefix_sha
        except OSError:
            same_file = False
    if same_file:
        off = offset
        if off > len(cur):
            off = 0
        kind = "legacy" if anchor_ctime is None else "intact"
        return {"text": _decode_log_bytes(cur, off), "kind": kind, "offset": off, "rotated": 0, "note": ""}

    since = float("-inf")
    anchor_mtime = _anchor_value(anchor, "mtime_ms")
    if anchor_mtime is not None:
        since = (int(anchor_mtime) - ANCHOR_MTIME_TOLERANCE_MS) / 1000.0
    rotated = _rotated_logs(logs_dir, since, max_rotated) if logs_dir else []
    matched = -1
    matched_bytes = None
    for index, candidate in enumerate(rotated):
        data = _read_rotated_bytes(candidate)
        if data is None:
            continue
        if matched >= 0:
            continue
        if len(data) < offset:
            continue
        plen = int(prefix_len) if prefix_len is not None else 0
        if len(data) < plen:
            continue
        if _prefix_hash(data, plen) == prefix_sha:
            matched = index
            matched_bytes = data
    if matched >= 0:
        text = _decode_log_bytes(matched_bytes[offset:] + cur)
        note = (
            "跨零点日切：锚点文件已轮转为 "
            + os.path.basename(rotated[matched])
            + "，窗口自其 "
            + str(offset)
            + "B 处 + 当前文件重拼"
        )
        later = max(0, len(rotated) - matched - 1)
        if later > 0:
            note += "；另有 " + str(later) + " 段更晚的轮转件**未并入**（身份不可验证）"
        return {"text": text, "kind": "reassembled", "offset": offset, "rotated": 1, "note": note}

    off = offset
    if off > len(cur):
        off = 0
    note = (
        "锚点文件已不在（候选轮转段 "
        + str(len(rotated))
        + " 个，均无匹配身份）⇒ 窗口退化为当前文件（起点 "
        + str(off)
        + "B）,可能是重登/重启删除了 latest.log，或轮转件已被清理"
    )
    return {"text": _decode_log_bytes(cur, off), "kind": "unreconstructable", "offset": off, "rotated": 0, "note": note}


def read_text_shared(path):
    data = _read_bytes_shared(path)
    if data is None:
        return ""
    return data.decode("utf-8", "replace")


# ── 进程枚举与分类（Mt.Proc.psm1） ─────────────────────────────────────────
class ProcessEnumerationUnavailable(RuntimeError):
    """进程表读不到（CIM 不可用、Toolhelp/查询失败）。调用方必须显式降级并点名。"""


_TITLE_CACHE = None


def _window_titles():
    global _TITLE_CACHE
    if _TITLE_CACHE is not None:
        return _TITLE_CACHE
    titles = {}
    if IS_WINDOWS:
        try:
            user32 = ctypes.WinDLL("user32", use_last_error=True)
            callback_type = ctypes.WINFUNCTYPE(ctypes.c_bool, ctypes.c_void_p, ctypes.c_void_p)
            buffer = ctypes.create_unicode_buffer(1024)
            user32.GetWindowTextLengthW.restype = ctypes.c_int
            user32.GetWindowTextW.restype = ctypes.c_int
            user32.GetWindowThreadProcessId.restype = ctypes.c_ulong

            def _collect(hwnd, _lparam):
                length = user32.GetWindowTextLengthW(hwnd)
                if length and length < 1024:
                    if user32.GetWindowTextW(hwnd, buffer, 1024):
                        pid = ctypes.c_ulong(0)
                        user32.GetWindowThreadProcessId(hwnd, ctypes.byref(pid))
                        if pid.value:
                            titles[int(pid.value)] = buffer.value
                return True

            user32.EnumWindows(callback_type(_collect), 0)
        except (OSError, AttributeError, TypeError, ValueError):
            titles = {}
    _TITLE_CACHE = titles
    return titles


def _command_line_win32(pid):
    kernel32 = ctypes.WinDLL("kernel32", use_last_error=True)
    ntdll = ctypes.WinDLL("ntdll")
    kernel32.OpenProcess.restype = ctypes.c_void_p
    kernel32.OpenProcess.argtypes = [ctypes.c_ulong, ctypes.c_int, ctypes.c_ulong]
    kernel32.ReadProcessMemory.restype = ctypes.c_int
    kernel32.ReadProcessMemory.argtypes = [
        ctypes.c_void_p,
        ctypes.c_void_p,
        ctypes.c_void_p,
        ctypes.c_size_t,
        ctypes.POINTER(ctypes.c_size_t),
    ]
    kernel32.CloseHandle.argtypes = [ctypes.c_void_p]
    ntdll.NtQueryInformationProcess.restype = ctypes.c_long
    ntdll.NtQueryInformationProcess.argtypes = [
        ctypes.c_void_p,
        ctypes.c_ulong,
        ctypes.c_void_p,
        ctypes.c_ulong,
        ctypes.POINTER(ctypes.c_ulong),
    ]
    process_query_limited_information = 0x1000
    handle = kernel32.OpenProcess(process_query_limited_information, 0, int(pid))
    if not handle:
        return ""
    try:
        size = 4096
        for _attempt in range(6):
            buffer = ctypes.create_string_buffer(size)
            returned = ctypes.c_ulong(0)
            status = ntdll.NtQueryInformationProcess(
                ctypes.c_void_p(handle), 60, ctypes.byref(buffer), size, ctypes.byref(returned)
            )
            code = int(status) & 0xFFFFFFFF
            if code == 0:
                length = int.from_bytes(buffer.raw[0:2], "little")
                pointer = int.from_bytes(buffer.raw[8:16], "little")
                if length <= 0 or pointer == 0:
                    return ""
                memory = ctypes.create_string_buffer(length)
                read = ctypes.c_size_t(0)
                ok = kernel32.ReadProcessMemory(
                    ctypes.c_void_p(handle),
                    ctypes.c_void_p(pointer),
                    ctypes.byref(memory),
                    length,
                    ctypes.byref(read),
                )
                if not ok:
                    return ""
                return memory.raw[: read.value].decode("utf-16-le", "replace")
            if code == 0xC0000004:
                size *= 2
                continue
            return ""
        return ""
    finally:
        kernel32.CloseHandle(ctypes.c_void_p(handle))


def list_java_processes():
    """Get-JavaProcesses：java.exe 的 [{pid, command_line}]。

    读不到进程表时抛 ProcessEnumerationUnavailable（调用方必须显式 SKIP/BLOCKED 并点名，
    不得回落到「空表 = 无遗留进程」）。
    """
    if not IS_WINDOWS:
        raise ProcessEnumerationUnavailable("非 Windows 宿主：没有 CIM/Toolhelp 进程表等价物")
    try:
        kernel32 = ctypes.WinDLL("kernel32", use_last_error=True)
        kernel32.CreateToolhelp32Snapshot.restype = ctypes.c_void_p
        kernel32.CreateToolhelp32Snapshot.argtypes = [ctypes.c_ulong, ctypes.c_ulong]
        kernel32.Process32FirstW.argtypes = [ctypes.c_void_p, ctypes.c_void_p]
        kernel32.Process32NextW.argtypes = [ctypes.c_void_p, ctypes.c_void_p]
        kernel32.CloseHandle.argtypes = [ctypes.c_void_p]

        class PROCESSENTRY32W(ctypes.Structure):
            _fields_ = [
                ("dwSize", ctypes.c_ulong),
                ("cntUsage", ctypes.c_ulong),
                ("th32ProcessID", ctypes.c_ulong),
                ("th32DefaultHeapID", ctypes.c_void_p),
                ("th32ModuleID", ctypes.c_ulong),
                ("cntThreads", ctypes.c_ulong),
                ("th32ParentProcessID", ctypes.c_ulong),
                ("pcPriClassBase", ctypes.c_long),
                ("dwFlags", ctypes.c_ulong),
                ("szExeFile", ctypes.c_wchar * 260),
            ]

        snapshot = kernel32.CreateToolhelp32Snapshot(2, 0)
        if not snapshot or snapshot == ctypes.c_void_p(-1).value:
            raise ProcessEnumerationUnavailable("CreateToolhelp32Snapshot 失败")
        try:
            entry = PROCESSENTRY32W()
            entry.dwSize = ctypes.sizeof(PROCESSENTRY32W)
            found = []
            ok = kernel32.Process32FirstW(ctypes.c_void_p(snapshot), ctypes.byref(entry))
            while ok:
                if str(entry.szExeFile).lower() == "java.exe":
                    found.append(
                        {
                            "pid": int(entry.th32ProcessID),
                            "command_line": _command_line_win32(entry.th32ProcessID),
                        }
                    )
                ok = kernel32.Process32NextW(ctypes.c_void_p(snapshot), ctypes.byref(entry))
            return found
        finally:
            kernel32.CloseHandle(ctypes.c_void_p(snapshot))
    except ProcessEnumerationUnavailable:
        raise
    except (OSError, AttributeError, TypeError, ValueError) as exc:
        raise ProcessEnumerationUnavailable(str(exc))


def test_client_process(paths, pid, command_line):
    command_line = "" if command_line is None else str(command_line)
    title = _window_titles().get(int(pid), "")
    if title and paths.version in title:
        return True
    if (
        "-Dfabric.dli.env=client" in command_line
        and "fabric-1.20.1" in command_line
        and (
            "net.fabricmc.devlaunchinjector.Main" in command_line
            or "net.fabricmc.loader.impl.launch.knot.KnotClient" in command_line
        )
    ):
        return True
    is_client_entry = (
        "net.minecraft.client.main.Main" in command_line
        or "bootstraplauncher" in command_line
        or "net.neoforged.fml.startup.Client" in command_line
    )
    if not is_client_entry:
        return False
    for marker in process_markers(paths):
        if marker and marker in command_line:
            return True
    if paths.version in command_line and paths.subproject in command_line:
        return True
    return False


def test_pipeline_process(paths, pid, command_line):
    if test_client_process(paths, pid, command_line):
        return True
    command_line = "" if command_line is None else str(command_line)
    evidence = any(marker and marker in command_line for marker in process_markers(paths))
    if not evidence:
        evidence = paths.version in command_line and paths.subproject in command_line
    if not evidence:
        return False
    if "gradle-wrapper.jar" in command_line and (":" + paths.subproject + ":run") in command_line:
        return True
    if "net.neoforged.devlaunch.Main" in command_line:
        return True
    return False


def stop_version_processes(paths, quiet=True):
    """Stop-MtVersionProcesses 的最小等价：taskkill /PID <pid> /T /F（不打印，preflight 用 Quiet）。"""
    killed = 0
    for proc in list_java_processes():
        if not test_pipeline_process(paths, proc["pid"], proc["command_line"]):
            continue
        try:
            subprocess.run(
                ["taskkill", "/PID", str(proc["pid"]), "/T", "/F"],
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
                timeout=30,
                check=False,
            )
        except (OSError, subprocess.SubprocessError):
            continue
        killed += 1
    return killed


# ── 通道模式（Mt.Channel.psm1） ────────────────────────────────────────────
_CHANNEL_STATE = {"mode": "", "emitted": False, "value": ""}


def _has_nonspace(text):
    return any(ch not in (" ", chr(9), CR, NL) for ch in (text or ""))


def channel_mode(requested=""):
    """Get-MtChannelMode：--channel > MT_CHANNEL > autojs；非法值抛 ValueError。

    成功时写一行 stdout，形如 MT_CHANNEL: mode=<mode>（本进程首次或值变化时）。
    """
    if requested and requested.strip():
        raw = requested.strip()
    elif os.environ.get("MT_CHANNEL") and _has_nonspace(os.environ.get("MT_CHANNEL", "")):
        raw = os.environ["MT_CHANNEL"].strip()
    else:
        raw = "autojs"
    mode = raw.lower()
    if mode not in ("autojs", "native"):
        raise ValueError("MT_CHANNEL 取值非法：'" + raw + "'（只接受 autojs / native）")
    _CHANNEL_STATE["mode"] = mode
    if (not _CHANNEL_STATE["emitted"]) or _CHANNEL_STATE["value"] != mode:
        write_mt_line("MT_CHANNEL: mode=" + mode)
        _CHANNEL_STATE["emitted"] = True
        _CHANNEL_STATE["value"] = mode
    return mode


if __name__ == "__main__":
    init_console()
    write_mt_line("mt_compat self-check")
    write_mt_line("ROOT=" + ROOT)
    write_mt_line("TEST_DIR=" + TEST_DIR)
    write_mt_line("VERSIONS=" + " ".join(VERSIONS))
    for _v in VERSIONS:
        _p = get_paths(_v)
        write_mt_line(_v + " | " + _p.loader + " | " + _p.run_label + " | " + _p.pack_mods_dir)
