#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""守门：主动技能键的「归属」不变量（2026-10-05 用户裁决）。

背景
----
2026-10-03 的「按键收口」曾让**主动技能键（默认 J）**在目标选择会话期间充当「取消选择」；
2026-10-05 用户裁决**撤销**该口径：「完全把主动技能释放控制权交给玩家」—— 选择会话进行中按主动技能键
必须**照常触发立牌主动技能**。本脚本把该裁决固化为可复跑的判据，防止回退。

检查项（全部只读源码/资源；0 = 通过）
------------------------------------
K1 四线 `client/KeyBindingSetup.java` 的 `ACTIVATE_SIGN_KEY.consumeClick()` 语句块内**不得**出现
   `TargetSelectionClient`（即按键不再按会话状态分流）。
K2 四线源码全仓**不得**再出现「按键 = 取消选择」的**调用形态**（`logPrompt("j", …)` / `cancel("key")`）。
K3 四线三语 `msg.astral_dice.target_select.prompt.hold.*` 四键的值**不得**含 `J`（「按 J 收起」措辞已移除）。

用法
----
    python tools/verify_selector_key_ownership.py [-v]

退出码：0 = 全部通过；1 = 存在违规；2 = 无法判定（文件缺失等）。
"""

import argparse
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LINES = ["neoforge-1.21.1", "forge-1.20.1", "neoforge-26.1.2", "fabric-1.20.1"]
LANGS = ["zh_cn", "en_us", "ja_jp"]
HOLD_KEYS = ["no_target", "no_target_self", "rejected", "valid"]


def read(p):
    with open(p, "rb") as fh:
        return fh.read().decode("utf-8")


def rel(p):
    return os.path.relpath(p, ROOT).replace("\\", "/")


def check_k1(verbose):
    """按键分支内不得引用 TargetSelectionClient。"""
    fails = []
    for line in LINES:
        p = os.path.join(ROOT, line, "src/main/java/com/merlinkitsune/astral_dice/client/KeyBindingSetup.java")
        if not os.path.isfile(p):
            fails.append(f"K1 {line}: 找不到 KeyBindingSetup.java")
            continue
        src = read(p)
        m = re.search(r"while\s*\(\s*ACTIVATE_SIGN_KEY\.consumeClick\(\)\s*\)\s*\{(.*?)\n\s{12}\}", src, re.S)
        if not m:
            fails.append(f"K1 {line}: 定位不到 ACTIVATE_SIGN_KEY.consumeClick() 语句块（结构已变更 ⇒ 请更新本脚本的正则）")
            continue
        body = m.group(1)
        if "TargetSelectionClient" in body:
            fails.append(f"K1 {line}: 主动技能键分支内仍引用 TargetSelectionClient（按键仍在按会话分流）")
        elif verbose:
            print(f"    ok K1 {line}: 按键分支不含 TargetSelectionClient")
    return fails


def check_k2(verbose):
    """全仓不得再有「主动技能键 = 取消选择」的**调用形态**。

    ⚠️ 判据说明（2026-10-05 独立复核修正）：**不能**只扫 `key=j action=cancel` 字面串 —— 那段 DEBUG
    文本是**运行期**由 `LOGGER.debug("… key={} action={}", key, action)` 拼出来的，任何 `.java` 源里
    都不存在该字面串 ⇒ 那种判据**永远为真**（vacuous），防不住它宣称要防的回归。回归时会写回的是
    **调用**：`TargetSelectionClient.logPrompt("j", "cancel")`（键位分流）。故此处扫调用形态。
    """
    fails = []
    pats = [
        (re.compile(r'logPrompt\s*\(\s*"j"'), 'logPrompt("j", …)（按键 = 取消选择的旧分流）'),
        (re.compile(r'cancel\s*\(\s*"key"'), 'cancel("key")（按键触发的取消）'),
    ]
    for line in LINES:
        base = os.path.join(ROOT, line, "src/main/java")
        for dirpath, _dirnames, filenames in os.walk(base):
            for fn in filenames:
                if not fn.endswith(".java"):
                    continue
                p = os.path.join(dirpath, fn)
                src = read(p)
                for rx, desc in pats:
                    if rx.search(src):
                        fails.append(f"K2 {rel(p)}: 仍存在 {desc}")
        if verbose and not fails:
            print(f"    ok K2 {line}: 无 `logPrompt(\"j\"` / `cancel(\"key\")` 调用")
    return fails


def check_k3(verbose):
    """hold 提示四键不得出现「J」。"""
    fails = []
    for line in LINES:
        for lang in LANGS:
            p = os.path.join(ROOT, line, "src/main/resources/assets/astral_dice/lang", f"{lang}.json")
            if not os.path.isfile(p):
                fails.append(f"K3 {line}/{lang}: lang 文件缺失")
                continue
            src = read(p)
            for k in HOLD_KEYS:
                m = re.search(r'^( *"msg\.astral_dice\.target_select\.prompt\.hold\.%s":.*)$' % k, src, re.M)
                if not m:
                    fails.append(f"K3 {line}/{lang}: 找不到键 prompt.hold.{k}")
                    continue
                if re.search(r"(?<![A-Za-z])J(?![A-Za-z])", m.group(1)):
                    fails.append(f"K3 {line}/{lang}: prompt.hold.{k} 仍含按键名 `J` ⇒ {m.group(1).strip()[:110]}")
            if verbose:
                print(f"    ok K3 {line}/{lang}: hold 四键均无 `J`")
    return fails


def main():
    ap = argparse.ArgumentParser(description="主动技能键归属不变量守门（2026-10-05 裁决）")
    ap.add_argument("-v", "--verbose", action="store_true", help="打印逐项通过明细")
    args = ap.parse_args()

    if not os.path.isdir(ROOT):
        print("GATE: FAIL (2) 仓库根不存在")
        return 2

    print("=== 主动技能键归属守门（主动技能键不得参与目标选择器）===")
    fails = []
    fails += check_k1(args.verbose)
    fails += check_k2(args.verbose)
    fails += check_k3(args.verbose)

    if fails:
        print()
        for f in fails:
            print("  [FAIL]", f)
        print(f"\nGATE: FAIL ({len(fails)} 项)")
        return 1
    print("\nGATE: PASS（K1 按键分流 / K2 DEBUG 行 / K3 lang 措辞，四线全通过）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
