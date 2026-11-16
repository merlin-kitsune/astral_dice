#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""run_mixin_stack_sanitizer_test —— DamageStackSanitizer 纯 Java 语义测试运行器。

python 等价移植 scripts/../tools/run_mixin_stack_sanitizer_test.py（旧脚本已随退役删除）。

做什么（与 ps1 完全一致）：
  1. 只编译**真实源文件** neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/fixes/
     DamageStackSanitizer.java（纯 JDK，无第三方依赖）与
     tools/mixin-stack-sanitizer-test/DamageStackSanitizerTest.java —— 不复制任何逻辑；
  2. 跑测试类，把结果折成机器行。
覆盖：未修复取消出口补 1 次 pop / 上游 backport 后 0 次 pop（不抛 EmptyStackException）/
嵌套 hurt 逐层配对 / null 与哨兵边界 / 两个正常出口 0 次 pop / 绝不弹到低于进入深度 /
配对失同步 no-op / 存活日志每 JVM 一次。

机器行（逐字保留，供 CI 解析）：
    javac = <路径>
    java  = <路径>
    out   = <目录>
    javac exit=<n>
    MSS_RESULT: FAIL (javac)
    MSS_RESULT: PASS (10/10)
    MSS_RESULT: FAIL (java exit=<n>)

用法：
    python tools/run_mixin_stack_sanitizer_test.py
    python tools/run_mixin_stack_sanitizer_test.py --outdir temp/mss_out     # 也接受 -OutDir

退出码（与 ps1 一致）：0 = 测试通过；1 = javac 失败；2 = 缺源文件 / 找不到 javac·java / 参数错误；
java 自身非 0 时**原样**透传它的退出码（ps1 的 exit $rc）。

与 ps1 的差异（移植纪律要求逐条说明）：
  · ps1 用 原生 shell 的 Get-Command / Get-ChildItem 找 JDK；这里用 JAVA_HOME → PATH（shutil.which）
    → 两个固定猜测目录（Zulu 21、Eclipse Adoptium jdk-21*），顺序与 ps1 相同。
  · ps1 的 Write-Error 会带 原生 shell 自己的错误装饰并写到 stderr；这里只写纯文本消息（同上 stderr）。
    「缺源文件 / 找不到工具」仍然 exit 2，未降级为 0。
"""

from __future__ import annotations

import argparse
import glob
import os
import shutil
import subprocess
import sys
from typing import List, Optional

HERE = os.path.dirname(os.path.abspath(__file__))


def _enable_utf8_stdout() -> None:
    """显式 UTF-8 + 显式 LF（本机控制台缺省 GBK；ps1 侧也是纯 LF 输出）。"""
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


def _normalize_dashes(argv: List[str], spec: dict) -> List[str]:
    """把 旧 shell 风格单破折号参数（-OutDir）折成 argparse 长选项（--outdir）。

    spec 形如 {"outdir": "outdir"}（小写名 -> 规范长选项名），大小写不敏感。
    不在 spec 里的 token 原样透传，交给 argparse 判 rc=2。
    """
    out: List[str] = []
    for tok in argv:
        if tok.startswith("-") and not tok.startswith("--") and len(tok) > 1:
            name = tok.lstrip("-").lower()
            if name in spec:
                out.append("--" + spec[name])
                continue
            if "=" in name:
                head, _, tail = name.partition("=")
                if head in spec:
                    out.append("--" + spec[head] + "=" + tail)
                    continue
        out.append(tok)
    return out


def resolve_jdk_tool(name: str) -> Optional[str]:
    """复刻 ps1 的 Resolve-JdkTool：JAVA_HOME/bin → PATH → 两个固定猜测目录。"""
    java_home = os.environ.get("JAVA_HOME")
    if java_home:
        cand = os.path.join(java_home, "bin", name + ".exe")
        if os.path.isfile(cand):
            return cand
    hit = shutil.which(name)
    if hit:
        return hit
    for guess in (r"C:\Program Files\Zulu\zulu-21\bin",
                  r"C:\Program Files\Eclipse Adoptium\jdk-21*\bin"):
        for path in sorted(glob.glob(os.path.join(guess, name + ".exe"))):
            return path
    return None


def main(argv: Optional[List[str]] = None) -> int:
    _enable_utf8_stdout()
    argv = list(sys.argv[1:] if argv is None else argv)
    argv = _normalize_dashes(argv, {"outdir": "outdir"})

    ap = argparse.ArgumentParser(
        prog="run_mixin_stack_sanitizer_test.py",
        description="编译并运行 DamageStackSanitizer 的纯 Java 语义测试（等价 run_mixin_stack_sanitizer_test.py）。",
        epilog="退出码：0 通过 / 1 javac 失败 / 2 缺源文件或找不到 JDK / 其它 = java 自身退出码",
    )
    ap.add_argument("--outdir", "-OutDir", default=None,
                    help="编译产物目录（缺省 %%TEMP%%\\astral_mss_out，会先清空）")
    args = ap.parse_args(argv)

    root = os.path.dirname(HERE)  # ps1: Split-Path -Parent $PSScriptRoot
    sanitizer_src = os.path.join(
        root, "neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/fixes/DamageStackSanitizer.java")
    test_src = os.path.join(root, "tools/mixin-stack-sanitizer-test/DamageStackSanitizerTest.java")

    for path in (sanitizer_src, test_src):
        if not os.path.isfile(path):
            _error("缺少源文件: " + path)
            return 2

    javac = resolve_jdk_tool("javac")
    java = resolve_jdk_tool("java")
    if not javac or not java:
        _error("找不到 javac/java（设置 JAVA_HOME 或加入 PATH）")
        return 2

    if args.outdir:
        out_dir = args.outdir
    else:
        out_dir = os.path.join(os.environ.get("TEMP") or os.environ.get("TMP") or ".", "astral_mss_out")
    if os.path.isdir(out_dir):
        shutil.rmtree(out_dir, ignore_errors=True)
    elif os.path.exists(out_dir):
        os.remove(out_dir)
    os.makedirs(out_dir, exist_ok=True)

    print("javac = " + javac)
    print("java  = " + java)
    print("out   = " + out_dir)
    print("")
    sys.stdout.flush()

    jc = subprocess.run([javac, "-encoding", "UTF-8", "-d", out_dir, sanitizer_src, test_src]).returncode
    print("javac exit=" + str(jc))
    sys.stdout.flush()
    if jc != 0:
        print("MSS_RESULT: FAIL (javac)")
        return 1

    rc = subprocess.run([java, "-cp", out_dir, "DamageStackSanitizerTest"]).returncode
    print("")
    if rc == 0:
        print("MSS_RESULT: PASS (10/10)")
    else:
        print("MSS_RESULT: FAIL (java exit=" + str(rc) + ")")
    return rc


if __name__ == "__main__":
    sys.exit(main())
