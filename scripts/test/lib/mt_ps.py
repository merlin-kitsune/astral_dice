#!/usr/bin/env python3
"""mt_ps.py — 宿主能力层：PowerShell 解释器桥 + java 进程表（python 侧）。

定位
----
本文件**不是**测试资产，只包**宿主能力**：Windows 上取 java.exe 的完整命令行
（CIM Win32_Process），这是 bash / ctypes 之外的唯一来源。

硬边界（2026-11 迁移纪律）
--------------------------
* 允许调用 PowerShell **解释器**（pwsh 7 或系统自带 Windows PowerShell 5.1）。
* **禁止**调用任何 .ps1 / .psm1 文件，禁止 Import-Module —— scripts/ 下的 pwsh
  资产正在物理删除，Python 侧一旦依赖它们就等于把「能力丢失」固化回来。
* 只做「取进程命令行」这一件事；注入 / 断言 / 判定都不在本文件。

取命令行口径（按顺序，第一个有产出即用）
----------------------------------------
a) wmic process where name='java.exe' get ProcessId,CommandLine /format:csv
   —— 老系统仍在，最快且无解释器冷启动；wmic 已弃用，缺失时静默跳过。
b) PowerShell 解释器：先 pwsh，否则系统自带
   /c/Windows/System32/WindowsPowerShell/v1.0/powershell.exe（5.1，Win10/11 恒有），
   命令形如 Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
   Select-Object ProcessId,CommandLine | ConvertTo-Json。

判线标记串（与 scripts/test/win/stop.sh:59-60、scripts/test/win/lib/common.sh:256-279
以及 Mt.Paths.psm1 的 Get-MtProcessMarkers 同口径）
-------------------------------------------------
"<:子项目:>"（gradle 任务选择器）、"run\\<version>"、"run/<version>"；fabric-1.20.1
是双 run 目录（run/client、run/server），路径里不含版本号，故用「子项目 + run +
client|server」兜底。**刻意不用裸子项目名**：它同时是仓库目录名，会误命中 IDE
与索引进程。

输出形态
--------
java_procs() -> [{"pid": int, "cmdline": str, "marker": str, "version": str}]
  marker  = 该进程命令行命中的**第一条**标记串（未命中为 ""）
  version = 由 marker 反推的线名（未命中为 ""）
读不到进程表时抛 ProcessEnumerationUnavailable —— 调用方必须显式降级并点名，
绝不返回空表冒充「没有 java 进程」（那会让「无游戏在跑」判据假绿）。
"""
from __future__ import annotations

import json
import os
import shutil
import subprocess
import sys

VERSIONS = ("1.21.1", "1.20.1", "26.1.2", "fabric-1.20.1", "fabric-1.21.1")

SUBPROJECT = {
    "1.21.1": "neoforge-1.21.1",
    "1.20.1": "forge-1.20.1",
    "26.1.2": "neoforge-26.1.2",
    "fabric-1.20.1": "fabric-1.20.1",
    "fabric-1.21.1": "fabric-1.21.1",
}

FABRIC_VERSIONS = ("fabric-1.20.1", "fabric-1.21.1")


def is_fabric(version):
    return str(version or "").strip().startswith("fabric-")
BSL = "\\"

# 裁判线顺序：两条 fabric 必须先判（"1.20.1"/"1.21.1" 都是 "fabric-x" 的子串），再判裸版本号。
_MARKER_ORDER = ("fabric-1.20.1", "fabric-1.21.1", "1.21.1", "26.1.2", "1.20.1")

CIM_SCRIPT = (
    "$ErrorActionPreference = 'Stop'; "
    "try { $p = @(Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" -ErrorAction Stop) } "
    "catch { [Console]::Error.WriteLine('CIM_ERROR ' + $_.Exception.Message); exit 3 }; "
    "@($p | Select-Object ProcessId,CommandLine) | ConvertTo-Json -Compress"
)

CIM_SCRIPT_TAB = (
    "$ErrorActionPreference = 'Stop'; "
    "try { $p = @(Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" -ErrorAction Stop) } "
    "catch { [Console]::Error.WriteLine('CIM_ERROR ' + $_.Exception.Message); exit 3 }; "
    "foreach ($x in $p) { '{0}`t{1}' -f [int]$x.ProcessId, ([string]$x.CommandLine) }"
)


class ProcessEnumerationUnavailable(RuntimeError):
    """进程表读不到（wmic 与 PowerShell 解释器都失败）。调用方必须显式点名降级。"""


# ── 解释器发现 ────────────────────────────────────────────────────────────
def pwsh_exe():
    """mt_pwsh_exe 同口径：MT_PWSH_EXE 钉死 > pwsh > PS7 默认路径 > 系统 5.1。

    MT_PWSH_EXE 一旦设置就**原样返回**（不做存在性检查）：这样「钉到一个不存在的
    路径」才能作为反向判据自证「没有解释器必须报错」。
    """
    pinned = os.environ.get("MT_PWSH_EXE", "")
    if pinned:
        return pinned
    found = shutil.which("pwsh")
    if found:
        return found
    program_files = os.environ.get("ProgramFiles", r"C:\Program Files")
    ps7 = os.path.join(program_files, "PowerShell", "7", "pwsh.exe")
    if os.path.isfile(ps7):
        return ps7
    system_root = os.environ.get("SystemRoot", r"C:\Windows")
    winps = os.path.join(system_root, "System32", "WindowsPowerShell", "v1.0", "powershell.exe")
    if os.path.isfile(winps):
        return winps
    return None


def wmic_exe():
    """wmic 位置（弃用组件；缺失返回 None）。"""
    found = shutil.which("wmic")
    if found:
        return found
    system_root = os.environ.get("SystemRoot", r"C:\Windows")
    candidate = os.path.join(system_root, "System32", "wbem", "WMIC.exe")
    if os.path.isfile(candidate):
        return candidate
    return None


def _decode(raw):
    return (raw or b"").decode("utf-8", errors="replace")


def run_tolerant(cmd, timeout=60):
    """执行命令并容错解码输出：任何启动失败折叠为 (1, "")。"""
    try:
        result = subprocess.run(list(cmd), capture_output=True, timeout=timeout)
    except (OSError, subprocess.SubprocessError):
        return 1, ""
    return result.returncode, _decode(result.stdout)


def run_ps(script, timeout=60):
    """执行一段 **内联** PowerShell 脚本，返回 (rc, stdout_text)。

    与原件的同名函数保持签名（mt_inject.py 的 pid 探测用它）。脚本前统一把控制台
    输出编码设为 UTF-8，减少中文路径导致的乱码；仍以 errors="replace" 兜底解码。
    非 Windows ⇒ (1, "")。**不接触任何 .ps1 / .psm1**。
    """
    if sys.platform != "win32":
        return 1, ""
    exe = pwsh_exe()
    if not exe:
        return 1, ""
    wrapped = "[Console]::OutputEncoding=[Text.Encoding]::UTF8; " + script
    return run_tolerant([exe, "-NoProfile", "-NonInteractive", "-Command", wrapped], timeout=timeout)


# ── 标记串 ────────────────────────────────────────────────────────────────
def markers_for(version, side=""):
    """某条线的判线标记串（小写）。side 只影响 fabric 的 run/client|run/server 细分。"""
    version = str(version or "").strip()
    if version not in SUBPROJECT:
        return []
    sub = SUBPROJECT[version]
    if is_fabric(version):
        out = [":" + sub + ":"]
        sides = [str(side).strip()] if str(side or "").strip() in ("client", "server", "both") else ["client", "server"]
        for one in sides:
            if one == "both":
                continue
            out.append(sub + BSL + "run" + BSL + one)
            out.append(sub + "/run/" + one)
        out.append(sub + "runclient")
        out.append(sub + "runserver")
        return [item.lower() for item in out]
    return [
        ":" + sub + ":",
        "run" + BSL + version,
        "run/" + version,
    ]


def match_marker(cmdline):
    """命令行命中的 (version, marker)；未命中返回 ("", "")。大小写不敏感。"""
    text = (cmdline or "").lower()
    if not text:
        return "", ""
    for version in _MARKER_ORDER:
        for marker in markers_for(version):
            if marker and marker in text:
                return version, marker
    return "", ""


# ── 进程表 ────────────────────────────────────────────────────────────────
def _rows_from_wmic(timeout=60):
    exe = wmic_exe()
    if not exe:
        return None
    rc, out = run_tolerant(
        [exe, "process", "where", "name='java.exe'", "get", "ProcessId,CommandLine", "/format:csv"],
        timeout=timeout,
    )
    if rc != 0 or not out.strip():
        return None
    rows = []
    for line in out.splitlines():
        line = line.rstrip("\r")
        if not line.strip():
            continue
        head = line.split(",", 1)[0].strip().lower()
        if head == "node":
            continue
        _node, _sep, rest = line.partition(",")
        cmdline, _sep2, pid_text = rest.rpartition(",")
        pid_text = pid_text.strip()
        if not pid_text.isdigit():
            continue
        rows.append({"pid": int(pid_text), "cmdline": cmdline.strip()})
    return rows


def _rows_from_powershell(timeout=60):
    rc, out = run_ps(CIM_SCRIPT, timeout=timeout)
    if rc != 0:
        rc_tab, out_tab = run_ps(CIM_SCRIPT_TAB, timeout=timeout)
        if rc_tab != 0:
            raise ProcessEnumerationUnavailable(
                "CIM 取不到 java 进程表（rc=" + str(rc) + " / 回退 rc=" + str(rc_tab) + "）"
            )
        return _parse_tab_rows(out_tab)
    return _parse_json_rows(out)


def _parse_json_rows(text):
    body = (text or "").strip()
    if not body:
        return []
    try:
        data = json.loads(body)
    except ValueError:
        return _parse_tab_rows(text)
    if isinstance(data, dict):
        data = [data]
    rows = []
    for item in data or []:
        if not isinstance(item, dict):
            continue
        try:
            pid = int(item.get("ProcessId"))
        except (TypeError, ValueError):
            continue
        cmdline = item.get("CommandLine")
        rows.append({"pid": pid, "cmdline": "" if cmdline is None else str(cmdline)})
    return rows


def _parse_tab_rows(text):
    rows = []
    for line in (text or "").splitlines():
        line = line.rstrip("\r")
        pid_text, tab, cmdline = line.partition("\t")
        if not tab or not pid_text.strip().isdigit():
            continue
        rows.append({"pid": int(pid_text.strip()), "cmdline": cmdline})
    return rows


def java_rows():
    """java.exe 的 [{pid, cmdline}]（未按线过滤）。读不到 ⇒ 抛 ProcessEnumerationUnavailable。"""
    if sys.platform != "win32":
        raise ProcessEnumerationUnavailable("非 Windows 宿主：没有 wmic / CIM 等价物")
    rows = _rows_from_wmic()
    if rows is None:
        rows = _rows_from_powershell()
    return rows


def java_procs(version=""):
    """java 进程：[{pid, cmdline, marker, version}]。

    给了 version 就只返回该线的进程（按标记串判定）；未命中的进程不返回。
    """
    wanted = str(version or "").strip()
    if wanted and wanted not in SUBPROJECT:
        raise ValueError("未知版本 " + wanted + "（可选：" + " / ".join(VERSIONS) + "）")
    out = []
    for row in java_rows():
        hit_version, marker = match_marker(row["cmdline"])
        if wanted and hit_version != wanted:
            continue
        out.append(
            {
                "pid": row["pid"],
                "cmdline": row["cmdline"],
                "marker": marker,
                "version": hit_version,
            }
        )
    return out


def java_pids():
    """当前 java.exe 的 pid 集合（不做线判定）。"""
    return set(row["pid"] for row in java_rows())


def cmdline_of(pid):
    """某个 pid 的命令行（找不到返回 ""）。"""
    try:
        target = int(pid)
    except (TypeError, ValueError):
        return ""
    for row in java_rows():
        if row["pid"] == target:
            return row["cmdline"]
    return ""
