#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""verify_content_library —— 内容库一致性校验（薄包装）。

本文件**不重新实现**任何校验逻辑，只是 scripts/verify/verify_content_library.py 的兼容入口：
原样透传 stdout 与退出码，让老调用点（旧脚本 -Root <dir>）
的迁移成本为零。

为什么是薄包装（判重结论，可复验）：
  1. 任务书里提到的 scripts/verify/verify_content_library 旧入口 **在 git 和工作树里都不存在**
     （git ls-files 只列到 scripts/devtools/ 下的旧入口）；
  2. scripts/verify/verify_content_library.py 是同一套逻辑的 python 版：docstring 自述
     「等价 scripts/devtools/verify_content_library 旧脚本」，常量逐个相同
     （START_COMMIT=68dbd59 / VERS 双版本 / BASE_TAG_COUNTS / BASE_SLOT_COUNTS / BLANK_ITEMS /
     NEW_EFFECTS 6 个），输出行 [OK] / WARN / FAIL / ALL OK / !! 缺少 … 与 ps1 同形；
  3. 反过来看 devtools 的旧脚本：它自己的头注写的是「移植版 1:1 对应
     scripts/audit 之外的 verify_content_library.py（原 .py 92fbeaf 删除）」——即两者本就是
     同一实现的两份壳子（历史里 python 与脚本双份，本分支收敛回 python）。
  ⇒ 再抄一份 456 行的实现只会产生「两份会各自漂移的真相」，故只做包装。

用法：
    python scripts/devtools/verify_content_library.py                 # 等价 ps1 的 -Root .
    python scripts/devtools/verify_content_library.py --root <dir>    # 也接受 -Root / 位置参数
    其余参数原样转交被包装脚本（由它的 argparse 判定，未知参数 rc=2）。

退出码（与 ps1 一致，由被包装脚本给出）：0 = 全部通过；1 = 存在偏差（含快照缺失）；
    2 = 无法判定（被包装脚本不存在 / 参数错误）。
"""

from __future__ import annotations

import os
import subprocess
import sys
from typing import List, Optional, Sequence

HERE = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.dirname(os.path.dirname(HERE))
DELEGATE = os.path.join(REPO_ROOT, "scripts", "verify", "verify_content_library.py")

USAGE = """用法: python scripts/devtools/verify_content_library.py [--root <目录>]

内容是 scripts/verify/verify_content_library.py 的薄包装：单破折号 -Root 会被翻成 --root，
其余参数原样转交；stdout 与退出码原样透传。

  --root, -Root <目录>   工程根目录（缺省 .）；也可用位置参数直接给
  -h, --help             显示本帮助（不透传给被包装脚本）

退出码：0 全部通过 / 1 存在偏差 / 2 无法判定（被包装脚本缺失或参数错误）
"""


def _enable_utf8_stdout() -> None:
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(  # type: ignore[attr-defined]
                encoding="utf-8", errors="replace", newline="\n",
            )
        except (AttributeError, ValueError):
            pass


def translate(argv: Sequence[str]) -> List[str]:
    """把旧式 -Root 折成 --root；--help 由本包装自己处理，不再下传。"""
    out: List[str] = []
    i = 0
    n = len(argv)
    while i < n:
        tok = argv[i]
        if tok in ("-Root", "-root"):
            if i + 1 < n:
                out.extend(["--root", argv[i + 1]])
                i += 2
                continue
            out.append("--root")
            i += 1
            continue
        out.append(tok)
        i += 1
    return out


def main(argv: Optional[List[str]] = None) -> int:
    _enable_utf8_stdout()
    raw = list(sys.argv[1:] if argv is None else argv)
    if any(t in ("-h", "--help", "-Help", "-?") for t in raw):
        sys.stdout.write(USAGE)
        sys.stdout.flush()
        return 0

    if not os.path.isfile(DELEGATE):
        print("无法判定：被包装脚本不存在: " + DELEGATE, file=sys.stderr)
        sys.stderr.flush()
        return 2

    cmd = [sys.executable or "python", DELEGATE] + translate(raw)
    # 不捕获 stdout/stderr：直接继承句柄，保证输出字节与被包装脚本完全一致。
    return subprocess.run(cmd).returncode


if __name__ == "__main__":
    sys.exit(main())
