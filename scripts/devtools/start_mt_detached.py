#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""start_mt_detached —— 脱离式执行器（python 等价移植 scripts/devtools/Start-MtDetached.ps1）。

把任意阶段/命令放到**独立进程**里跑，输出落日志文件，调用方立即返回 pid。

为什么需要它（沿用 ps1 里 2026-09-12 的真机实测结论，非猜测）：
mt_build / mt_launch / mt_env(world) 会**故意留下长命子进程** —— Gradle 守护进程与 Minecraft
客户端必须活过本脚本（launch → cases 是分步执行的），这是工具链的正确设计。问题出在
**非交互式调用方**（代理/CI/管道捕获）：它按**整棵进程树**等待，只要还有后代活着调用就不返回，
表现为「脚本卡滞」，直到超时被强杀，而强杀会连带杀掉整棵树（客户端、守护一起死）。
实测对照：mt --phase build（需新起守护）4s 构建成功但调用 600s 超时；cmd /c "pwsh ... > file 2>&1"
无效（不是 stdout 管道问题，是等整树）；Start-Process -WindowStyle Hidden 立即返回且后代存活。

因此：凡会**新起长命后代**的阶段，一律用本脚本脱离执行，再按日志里的终态标记轮询。

机器行（逐字保留，供调用方/CI 解析）：
    MT_DETACHED: pid=<pid> script=<脚本路径> log=<日志绝对路径>
    MT_DETACHED_HINT: 轮询 <日志绝对路径> 里的终态标记；本进程树不会被调用方等待

用法：
    python scripts/devtools/start_mt_detached.py \
        --script scripts/test/mt.py --arg --phase launch --version 1.21.1 \
        --out temp/detached_launch_1211.log
    # 也接受 pwsh 风格：-Script / -Out / -WorkDir / -Merge / -ScriptArgs

参数面（与 ps1 的手写 argv 循环逐条对齐，**包括「未知 token 透传」这一条**）：
    -Script <path>      要脱离执行的脚本（必填）
    -Out <path>         日志路径（必填；相对路径按 -WorkDir 拼）；同时写 <Out>.err
    -WorkDir <dir>      子进程工作目录（缺省 = 当前目录）
    -Merge              把 stderr 并进 -Out（复刻 cmd 的 > f 2>&1）
    -Arg / -ScriptArgs  **之后的全部 token** 一次性透传给目标脚本并停止解析
    其它未识别的 token   按 ps1 语义追加到这个透传列表（不报错）—— 见下方「差异」

退出码（与 ps1 一致）：0 = 已起进程；2 = 缺参数 / 目标脚本不存在 / 无法确定解释器。

与 ps1 的差异（移植纪律要求逐条说明）：
  1. ps1 用 Start-Process 起「当前 pwsh + -NoProfile -File <script>」；本移植**不调用 pwsh**
     （.ps1 正在退役，新工具链不依赖它）。改为按目标脚本类型选解释器：
     .py → 当前 python、.sh → bash；其它扩展名（含 .ps1/.psm1）→ exit 2 并提示用 --interpreter
     显式指定解释器（唯一无法 1:1 保留的能力，理由见交付说明）。
  2. ps1 解析了 -Merge 却**从未使用**（$Merge 赋值后没有读者，属 ps1 的死开关）；这里按 ps1 头部
     文档声明的语义真正实现（stderr 并入 -Out，不再单独生成 <Out>.err）。
  3. --help/-h 是新增的（ps1 没有），用于自证参数面；ps1 会把 help 当透传 token。

与 ps1 一致、务必别「顺手修掉」的地方：
  · 未识别的 token **不报错**，而是进透传列表（这是 ps1 的既有契约，mt.ps1 依赖它透传 --phase 等）；
    这与「未知参数一律 rc=2」的通例相反，但等价性优先。
  · 本脚本只负责「起」，不负责「等」：等待交给调用方按日志标记轮询。
"""

from __future__ import annotations

import os
import subprocess
import sys
from typing import List, Optional, Tuple

HERE = os.path.dirname(os.path.abspath(__file__))

USAGE = """用法: python scripts/devtools/start_mt_detached.py [选项] [透传 token...]

  --script, -Script <path>       要脱离执行的脚本（必填）
  --out, -Out <path>             日志路径（必填）；stderr 默认同目录 <Out>.err
  --workdir, -WorkDir <dir>      子进程工作目录（缺省 = 当前目录）
  --merge, -Merge                stderr 并入 --out（相当于 cmd 的 > f 2>&1）
  --arg, --scriptargs, -Arg, -ScriptArgs
                                 其后的全部 token 原样透传给目标脚本
  --interpreter <exe>            显式指定解释器（缺省按扩展名：.py→python / .sh→bash）
  -h, --help                     显示本帮助

退出码：0 = 已起进程；2 = 缺参数 / 目标脚本不存在 / 无法确定解释器
"""


def _enable_utf8_stdout() -> None:
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(  # type: ignore[attr-defined]
                encoding="utf-8", errors="replace", newline="\n",
            )
        except (AttributeError, ValueError):
            pass


def _error(msg: str) -> None:
    print(msg, file=sys.stderr)
    sys.stderr.flush()


def parse_args(argv: List[str]) -> Tuple[dict, Optional[int]]:
    """复刻 ps1 的手写循环：key = token.TrimStart('-').ToLowerInvariant()。"""
    opts = {
        "script": "", "out": "", "workdir": os.getcwd(),
        "merge": False, "script_args": [], "interpreter": None,
    }
    i = 0
    n = len(argv)
    while i < n:
        tok = argv[i]
        key = tok.lstrip("-").lower()
        if key == "script":
            if i + 1 >= n:
                _error("缺少 --script 的值")
                return opts, 2
            opts["script"] = argv[i + 1]
            i += 2
        elif key == "out":
            if i + 1 >= n:
                _error("缺少 --out 的值")
                return opts, 2
            opts["out"] = argv[i + 1]
            i += 2
        elif key == "workdir":
            if i + 1 >= n:
                _error("缺少 --workdir 的值")
                return opts, 2
            opts["workdir"] = argv[i + 1]
            i += 2
        elif key == "merge":
            opts["merge"] = True
            i += 1
        elif key == "interpreter":
            if i + 1 >= n:
                _error("缺少 --interpreter 的值")
                return opts, 2
            opts["interpreter"] = argv[i + 1]
            i += 2
        elif key in ("arg", "scriptargs"):
            # 其余位置参数一律透传给目标脚本
            opts["script_args"] = list(argv[i + 1:])
            break
        elif key in ("help", "h", "?"):
            sys.stdout.write(USAGE)
            sys.stdout.flush()
            return opts, 0
        else:
            opts["script_args"].append(tok)
            i += 1
    return opts, None


def pick_interpreter(script: str, explicit: Optional[str]) -> Optional[List[str]]:
    if explicit:
        return [explicit]
    ext = os.path.splitext(script)[1].lower()
    if ext == ".py":
        return [sys.executable or "python"]
    if ext in (".sh", ".bash"):
        return ["bash"]
    return None


def main(argv: Optional[List[str]] = None) -> int:
    _enable_utf8_stdout()
    argv = list(sys.argv[1:] if argv is None else argv)
    opts, early = parse_args(argv)
    if early is not None:
        return early

    script = opts["script"]
    out = opts["out"]
    work_dir = opts["workdir"]

    if not script:
        _error("必须指定 --script")
        return 2
    if not out:
        _error("必须指定 --out（日志路径）")
        return 2
    if not os.path.isfile(script):
        _error("目标脚本不存在: " + script)
        return 2

    out_full = out if os.path.isabs(out) else os.path.join(work_dir, out)
    out_dir = os.path.dirname(out_full)
    if out_dir and not os.path.isdir(out_dir):
        os.makedirs(out_dir, exist_ok=True)
    if os.path.exists(out_full):
        os.remove(out_full)
    err_full = out_full + ".err"
    if os.path.exists(err_full):
        os.remove(err_full)

    cmd = pick_interpreter(script, opts["interpreter"])
    if cmd is None:
        _error("无法确定解释器：目标脚本不是 .py/.sh（" + script + "）。"
               "本脚本不再调用 pwsh；请用 --interpreter 显式指定，或改用 .py 目标。")
        return 2
    cmd = list(cmd) + [script] + list(opts["script_args"])

    # stdout / stderr 各落一个**文件句柄**（不是管道），故长命后代写日志不会反向阻塞任何人。
    creationflags = 0
    if os.name == "nt":
        creationflags = 0x00000008 | 0x00000200  # DETACHED_PROCESS | CREATE_NEW_PROCESS_GROUP

    if opts["merge"]:
        with open(out_full, "wb") as fout:
            proc = subprocess.Popen(
                cmd, stdout=fout, stderr=fout, stdin=subprocess.DEVNULL, cwd=work_dir,
                creationflags=creationflags, close_fds=True)
    else:
        with open(out_full, "wb") as fout, open(err_full, "wb") as ferr:
            proc = subprocess.Popen(
                cmd, stdout=fout, stderr=ferr, stdin=subprocess.DEVNULL, cwd=work_dir,
                creationflags=creationflags, close_fds=True)

    print("MT_DETACHED: pid={0} script={1} log={2}".format(proc.pid, script, out_full))
    print("MT_DETACHED_HINT: 轮询 {0} 里的终态标记；本进程树不会被调用方等待".format(out_full))
    sys.stdout.flush()
    return 0


if __name__ == "__main__":
    sys.exit(main())
