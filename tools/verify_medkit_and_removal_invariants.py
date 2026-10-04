#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
verify_medkit_and_removal_invariants.py —— Q2 / Q3 的**静态代码不变量**守门
=========================================================================

对应 2026-10-04 用户 Q1~Q8 裁决里的 Q2 / Q3（fabric 为主体，Q2 为四线共有）。
**这是运行时用例（`FAB-Q2Q3-INVARIANTS`）的静态对偶**：
运行时证明「行为没变」，本脚本证明「导致该行为的调用/判据确实在/确实不在」。

检查项
------
M1（Q2 · 医疗箱可刷封堵，四线各 4 条）
  M1-a `PlayerLifecycleHandler#onPlayerLoggedInClearDiceBlessing`      **不得**引用
       `HealingManager.triggerMedkitOnEquip` / `refreshMedkitEquipSession`
       （原实现使「反复重登」= 反复完整触发治愈 ⇒ 无限刷血）
  M1-b `PlayerLifecycleHandler#onPlayerChangedDimensionTriggerMedkit`  **不得**引用同上两项
       （原实现使「反复过门」= 反复完整触发治愈 ⇒ 无限刷血）
  M1-c `PlayerLifecycleHandler#onPlayerRespawnMedkit`                  **必须**引用
       `triggerMedkitOnEquip`（装备触发点之一：死亡重生后）
  M1-d `MedkitEmergencyChipItem#onEquip` / `MedkitCompleteChipItem#onEquip`
       **必须**引用 `triggerMedkitOnEquip`（装备触发点之二：真的装上筹码）

M2（Q3 · 效果移除拦截在「目标已无该效果」时放行，fabric）
  M2-a `ModEffectEvents#onModEffectRemovalPrevented` **必须**含 `hasEffect` 早退判据
  M2-b `PuzzlesBridges` 的 `MobEffectEvents.REMOVE` 回调 **必须**含 `hasEffect` 早退判据
       （否则「无效移除」会被译成 `EventResult.INTERRUPT`，注入点不可取消时抛
        `CancellationException: The call removeEffect is not cancellable`，见 KI-F25②）

退出码
------
  0 = 全部通过；1 = 存在不满足的不变量；2 = 无法判定（文件缺失 / 方法未找到）

用法
----
  python tools/verify_medkit_and_removal_invariants.py [-v]
"""

import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

LINES = ["neoforge-1.21.1", "forge-1.20.1", "neoforge-26.1.2", "fabric-1.20.1"]

PKG = "com/merlinkitsune/astral_dice"

# 医疗箱装备触发的两个入口名（Q2）
TRIGGER = "triggerMedkitOnEquip"
RELEASE_SESSION = "refreshMedkitEquipSession"


def read(path):
    with open(path, "rb") as fh:
        return fh.read().decode("utf-8", "replace")


def method_body(src, name):
    """按花括号配平抽取 `name(` 之后的第一个 `{ ... }` 方法体；找不到返回 None。

    会跳过字符串 / 字符字面量与行/块注释中的花括号（本仓这些文件里没有，但保持稳健）。
    """
    # 找到方法签名（忽略 @SubscribeEvent 等注解）
    m = re.search(r"\b" + re.escape(name) + r"\s*\(", src)
    if not m:
        return None
    i = src.find("{", m.end())
    if i < 0:
        return None
    depth = 0
    j = i
    n = len(src)
    while j < n:
        c = src[j]
        if c == '"' or c == "'":
            q = c
            j += 1
            while j < n:
                if src[j] == "\\":
                    j += 2
                    continue
                if src[j] == q:
                    break
                j += 1
        elif c == "/" and j + 1 < n and src[j + 1] == "/":
            j = src.find("\n", j)
            if j < 0:
                break
            continue
        elif c == "/" and j + 1 < n and src[j + 1] == "*":
            j = src.find("*/", j)
            if j < 0:
                break
            j += 1
        elif c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
            if depth == 0:
                return src[i:j + 1]
        j += 1
    return None


def strip_comments(src):
    """去掉行注释与块注释（保留字符串/字符字面量）。

    ⚠️ 必须做这一步：Q2 的修复注释里**逐字写了**被删掉的旧调用名
    （`原实现(refreshMedkitEquipSession + triggerMedkitOnEquip)把…`），
    不剥注释就会把「说明性提及」误判成「仍存在调用」⇒ 假 FAIL。
    """
    out = []
    i = 0
    n = len(src)
    while i < n:
        c = src[i]
        if c == '"' or c == "'":
            q = c
            out.append(c)
            i += 1
            while i < n:
                if src[i] == "\\":
                    out.append(src[i:i + 2])
                    i += 2
                    continue
                out.append(src[i])
                if src[i] == q:
                    i += 1
                    break
                i += 1
            continue
        if c == "/" and i + 1 < n and src[i + 1] == "/":
            j = src.find("\n", i)
            if j < 0:
                break
            i = j
            continue
        if c == "/" and i + 1 < n and src[i + 1] == "*":
            j = src.find("*/", i)
            if j < 0:
                break
            i = j + 2
            continue
        out.append(c)
        i += 1
    return "".join(out)


def check_method(path, method, must_have=(), must_not_have=(), label=""):
    """返回 (ok, message)。"""
    if not os.path.isfile(path):
        return None, "文件不存在: " + os.path.relpath(path, ROOT)
    src = read(path)
    body = method_body(src, method)
    if body is None:
        return None, "方法未找到: %s#%s" % (os.path.relpath(path, ROOT), method)
    code = strip_comments(body)
    rel = os.path.relpath(path, ROOT)
    for tok in must_have:
        if tok not in code:
            return False, "%s %s#%s 缺少必需调用/判据 `%s`" % (label, rel, method, tok)
    for tok in must_not_have:
        if tok in code:
            return False, "%s %s#%s 仍含已被移除的调用 `%s`" % (label, rel, method, tok)
    return True, ""


def file_contains(path, token, label=""):
    if not os.path.isfile(path):
        return None, "文件不存在: " + os.path.relpath(path, ROOT)
    src = read(path)
    if token not in src:
        return False, "%s %s 缺少 `%s`" % (label, os.path.relpath(path, ROOT), token)
    return True, ""


def main():
    verbose = "-v" in sys.argv
    fails = []
    unresolved = []
    checks = 0

    def run(ok, msg):
        nonlocal checks
        checks += 1
        if ok is None:
            unresolved.append(msg)
            print("  ?? " + msg)
        elif ok:
            if verbose:
                print("  ok " + (msg or "pass"))
        else:
            fails.append(msg)
            print("  FAIL " + msg)

    # ── M1：Q2 医疗箱可刷封堵（四线）──────────────────────────────────────
    print("[M1] Q2 医疗箱装备触发点收敛（四线）")
    for line in LINES:
        base = os.path.join(ROOT, line, "src/main/java", PKG)
        plh = os.path.join(base, "event", "PlayerLifecycleHandler.java")
        run(*check_method(plh, "onPlayerLoggedInClearDiceBlessing",
                          must_not_have=(TRIGGER, RELEASE_SESSION),
                          label="M1-a"))
        run(*check_method(plh, "onPlayerChangedDimensionTriggerMedkit",
                          must_not_have=(TRIGGER, RELEASE_SESSION),
                          label="M1-b"))
        run(*check_method(plh, "onPlayerRespawnMedkit",
                          must_have=(TRIGGER,),
                          label="M1-c"))
        for chip in ("MedkitEmergencyChipItem", "MedkitCompleteChipItem"):
            cp = os.path.join(base, "item", "chip", chip + ".java")
            run(*check_method(cp, "onEquip", must_have=(TRIGGER,), label="M1-d"))

    # ── M2：Q3 效果移除拦截的「无此效果 ⇒ 放行」判据（fabric）─────────────
    print("[M2] Q3 效果移除拦截判据（fabric）")
    fab = os.path.join(ROOT, "fabric-1.20.1", "src/main/java", PKG)
    run(*check_method(os.path.join(fab, "event", "ModEffectEvents.java"),
                      "onModEffectRemovalPrevented",
                      must_have=("hasEffect(",),
                      label="M2-a"))
    run(*file_contains(os.path.join(fab, "platform", "PuzzlesBridges.java"),
                       "hasEffect(",
                       label="M2-b"))

    print()
    if fails:
        print("GATE: FAIL  (%d 项不满足 / 共 %d 项)" % (len(fails), checks))
        for f in fails:
            print("  - " + f)
        return 1
    if unresolved:
        print("GATE: UNKNOWN  (%d 项无法判定 / 共 %d 项)" % (len(unresolved), checks))
        return 2
    print("GATE: PASS  (%d 项全部满足)" % checks)
    return 0


if __name__ == "__main__":
    sys.exit(main())
