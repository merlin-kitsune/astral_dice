#!/usr/bin/env python3
"""mt_ps — 外部命令执行助手（Windows 中文环境容错）。

背景（本机实测到的真问题）:
  Windows 上 PowerShell / 命令行输出常混入非 UTF-8 字节——中文路径按 GBK 编码、
  或控制台 OEM 码页。若用 subprocess(text=True) 交给默认解码器，会抛
  UnicodeDecodeError，而且异常发生在读取线程里：外部命令明明有输出，调用方却
  拿到「空结果」——表现为静默失败，比直接报错更危险
  （典型后果：mt_stop 报告「已停止 0 个进程」，实际进程还在跑）。

对策:
  统一以 bytes 取回输出，再用 UTF-8 errors="replace" 解码。所有需要匹配的标记
  （子项目名、run 目录、PID）都是 ASCII，替换字符不影响判定。
"""
from __future__ import annotations

import os
import subprocess
import sys
from typing import Sequence


def run_tolerant(cmd: Sequence[str], *, timeout: float = 60) -> tuple[int, str]:
    """执行命令并容错解码输出。返回 (returncode, stdout_text)。

    任何启动失败都折叠为 (1, "")——调用方按「无输出」处理，不需要 try。
    """
    try:
        r = subprocess.run(list(cmd), capture_output=True, timeout=timeout)
    except (OSError, subprocess.SubprocessError):
        return 1, ""
    return r.returncode, (r.stdout or b"").decode("utf-8", errors="replace")


def py_env() -> dict[str, str]:
    """子 python 进程的环境变量：强制 UTF-8 I/O。

    配合 run_py 的 utf-8 解码，使「父进程读子进程输出」在任何机器区域设置下
    都成立——不依赖调用方是否已设 PYTHONUTF8 / PYTHONIOENCODING。
    """
    env = dict(os.environ)
    env["PYTHONIOENCODING"] = "utf-8"
    env["PYTHONUTF8"] = "1"
    return env


def run_py(args: Sequence[str], *, timeout: float = 600) -> tuple[int, str, str]:
    """执行子 python 脚本并容错解码。返回 (returncode, stdout, stderr)。"""
    try:
        r = subprocess.run(list(args), capture_output=True, timeout=timeout,
                           env=py_env())
    except (OSError, subprocess.SubprocessError) as exc:
        return 1, "", str(exc)
    return (
        r.returncode,
        (r.stdout or b"").decode("utf-8", errors="replace"),
        (r.stderr or b"").decode("utf-8", errors="replace"),
    )


def run_ps(script: str, *, timeout: float = 60) -> tuple[int, str]:
    """执行一段 PowerShell 脚本；非 Windows 平台返回 (1, "")。

    会在脚本前统一把控制台输出编码设为 UTF-8，减少乱码；再用容错解码兜底。
    """
    if sys.platform != "win32":
        return 1, ""
    wrapped = "[Console]::OutputEncoding=[Text.Encoding]::UTF8; " + script
    return run_tolerant(
        ["powershell", "-NoProfile", "-NonInteractive", "-Command", wrapped],
        timeout=timeout,
    )


def java_procs() -> list[tuple[int, str]]:
    """当前 java 进程的 (pid, cmdline) 列表（跨平台）。"""
    if sys.platform == "win32":
        _, out = run_ps(
            "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | "
            "ForEach-Object { \"$($_.ProcessId)`t$($_.CommandLine)\" }"
        )
        procs: list[tuple[int, str]] = []
        for line in out.splitlines():
            pid_s, tab, cmd = line.partition("\t")
            if tab and pid_s.strip().isdigit():
                procs.append((int(pid_s.strip()), cmd))
        return procs

    _, out = run_tolerant(["ps", "-eo", "pid=,args="])
    procs = []
    for line in out.splitlines():
        line = line.strip()
        if not line:
            continue
        pid_s, _, args = line.partition(" ")
        if "java" in args:
            try:
                procs.append((int(pid_s), args))
            except ValueError:
                pass
    return procs
