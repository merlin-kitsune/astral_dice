#!/usr/bin/env python3
"""mt_ps.py — 宿主能力层：java 进程表（python 侧，JDK jps）。

定位
----
本文件**不是**测试资产，只包**宿主能力**：取 java 进程的 (pid, 主类与参数)，
供判线 / 残留核对使用。进程表来源只有 JDK 自带的 `jps -lvm`，不调用任何
外部进程查询工具，也不终止任何进程。

查找顺序（jps_exe）
------------------
a) 环境变量 MT_JPS_EXE 钉死（原样返回，不做存在性检查：便于反向判据自证
   「没有 jps 必须报错」）。
b) $JAVA_HOME/bin/jps 或 jps.exe。
c) PATH 上的 jps。

判线标记串（与 scripts/test/win/lib/common.sh 的 mt_cmdline_matches_version 同口径）
----------------------------------------------------------------------------------
":<子项目>:"（gradle 任务选择器）、"run\\<version>"、"run/<version>"；fabric 是双 run
目录（run/client、run/server），路径里不含版本号，故用「子项目 + run + client|server」
兜底。**刻意不用裸子项目名**：它同时是仓库目录名，会误命中 IDE 与索引进程。

输出形态
--------
java_procs() -> [{"pid": int, "cmdline": str, "marker": str, "version": str}]
  marker  = 该进程命令行命中的**第一条**标记串（未命中为 ""）
  version = 由 marker 反推的线名（未命中为 ""）
读不到进程表时抛 ProcessEnumerationUnavailable —— 调用方必须显式降级并点名，
绝不返回空表冒充「没有 java 进程」（那会让「无游戏在跑」判据假绿）。
"""
from __future__ import annotations

import os
import shutil
import subprocess

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
BSL = "\\\\"

# 裁判线顺序：两条 fabric 必须先判（"1.20.1"/"1.21.1" 都是 "fabric-x" 的子串），再判裸版本号。
_MARKER_ORDER = ("fabric-1.20.1", "fabric-1.21.1", "1.21.1", "26.1.2", "1.20.1")

# jps 自己的一行（不同 JDK 输出 jdk.jcmd/sun.tools.jps.Jps 或 sun.tools.jps.Jps）。
_JPS_SELF = "sun.tools.jps.Jps"


class ProcessEnumerationUnavailable(RuntimeError):
    """进程表读不到（jps 缺失或执行失败）。调用方必须显式点名降级。"""


# ── jps 发现 ──────────────────────────────────────────────────────────────
def jps_exe():
    """MT_JPS_EXE 钉死 > $JAVA_HOME/bin/jps[.exe] > PATH 的 jps；都没有返回 None。"""
    pinned = os.environ.get("MT_JPS_EXE", "")
    if pinned:
        return pinned
    java_home = os.environ.get("JAVA_HOME", "")
    if java_home:
        base = java_home.strip().rstrip("\\/")
        if base:
            for name in ("jps.exe", "jps"):
                candidate = os.path.join(base, "bin", name)
                if os.path.isfile(candidate):
                    return candidate
    return shutil.which("jps")


def _decode(raw):
    return (raw or b"").decode("utf-8", errors="replace")


def run_tolerant(cmd, timeout=60):
    """执行命令并容错解码输出：任何启动失败折叠为 (1, "")。"""
    try:
        result = subprocess.run(list(cmd), capture_output=True, timeout=timeout)
    except (OSError, subprocess.SubprocessError):
        return 1, ""
    return result.returncode, _decode(result.stdout)


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


# ── 进程表（jps）──────────────────────────────────────────────────────────
def java_rows():
    """java 进程的 [{pid, cmdline}]（未按线过滤）。

    读不到 ⇒ 抛 ProcessEnumerationUnavailable。输出每行 "<pid>\\t<主类与参数>"。
    """
    exe = jps_exe()
    if not exe:
        raise ProcessEnumerationUnavailable(
            "jps 不可用（未找到 $JAVA_HOME/bin/jps 或 PATH 的 jps）"
        )
    rc, out = run_tolerant([exe, "-lvm"])
    if rc != 0:
        raise ProcessEnumerationUnavailable("jps -lvm 执行失败（" + exe + "）")
    rows = []
    for line in out.splitlines():
        line = line.strip()
        if not line:
            continue
        parts = line.split(None, 1)
        if len(parts) != 2 or not parts[0].isdigit():
            continue
        if _JPS_SELF in parts[1]:
            continue
        rows.append({"pid": int(parts[0]), "cmdline": parts[1]})
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
    """当前 java 进程的 pid 集合（不做线判定）。"""
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


def stop_version_processes(version=""):
    """应收停的 PID 列表（本函数**不终止进程**）。

    终止由 Agent 通过 windows MCP 的 Process(mode:"kill") 执行。
    """
    return [row["pid"] for row in java_procs(version)]
