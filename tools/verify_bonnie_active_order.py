#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""秘密侦探立牌主动「隐匿调查 → +1 层标记 → 按施加后层数返币」的静态守门
（P0 两线：neoforge-1.21.1 / forge-1.20.1）。

## 要拦的回归
用户裁决：主动技能对目标施加时，除「隐匿调查」外**还施加 1 层标记**；返还星币必须在
**两种效果都施加之后**、按**当前（施加后）层数**结算。
会咬人的错误形态：
1. 仍用 `MarkManager.getLevel(target)` 先读层数 ⇒ 返还漏掉本次施加的 1 层；
2. 把 `markLevel * 2` 的返币包在 `if (markLevel > 0)` 里 ⇒ 原 0 层目标返币被吞；
3. 先返币、后加标记（顺序倒置）；
4. 裸写 `ModEffects.MARKED` 绕过 `MarkManager`（层数/上限/发光规则失效）；
5. 删掉「已带隐匿调查 ⇒ 早退」的服务端闸门。

## 判据（B1~B5，逐线）
- **B1** `apply` 内仍保留「目标已带 UNDERCOVER_INVESTIGATION ⇒ 记录日志并 return」的早退，
  且位于施加隐匿调查之前。
- **B2** 顺序：施加隐匿调查（`addEffect(new MobEffectInstance(ModEffects.UNDERCOVER_INVESTIGATION…`）
  < `MarkManager.apply(target)` < `new ItemStack(ModItems.STAR_COIN.get(), markLevel * 2)`。
- **B3** `markLevel` 必须由 `MarkManager.apply(target)` 的返回值赋值（施加后层数）；
  `apply` 内不得再出现 `MarkManager.getLevel(target)`（施加前读数）。
- **B4** 返币不得被 `if (markLevel > 0)` 包住（0 层也要按 1 层结算）。
- **B5** `apply` 内不得裸写 `ModEffects.MARKED`。

## 范围
只查 P0 两线（`neoforge-1.21.1` / `forge-1.20.1`）；P1（`neoforge-26.1.2` / `fabric-1.20.1`）
**不在本任务范围，明确不检查**。

退出码：0 全部通过 / 1 存在违规 / 2 无法判定（文件/apply 方法无法定位）。

## 本守门证明不了什么
文本/结构启发式：能拦「改主动逻辑忘了保持顺序/层数口径」，不能证明运行时目标真的获得标记、
星币真的入账（需要实机或单测）；也无法阻止刻意用死分支架空的混淆写法。

用法：python tools/verify_bonnie_active_order.py [-v]
"""

import os
import re
import sys


# --- stdout/stderr 编码自愈（统一口径：python 侧强制 UTF-8；勿删）---
# 背景：Windows 默认代码页 936 下 python 子进程的 sys.stdout.encoding 为 gbk，
# 本脚本打印的 ⇒/⚠/✅/↔ 等符号 GBK 不可表示 ⇒ 直接 UnicodeEncodeError
# 退出码 1（“门会红但红的原因是崩溃”）。此块使脚本不依赖调用者环境与宿主编码。
# ⚠ 块内引用 sys ⇒ 必须位于**所有 import 之后**；也**不得**加 except-pass（会静默失效）。
def _astral_heal_stdio() -> None:  # pragma: no cover - 环境适配
    for _stream_name in ("stdout", "stderr"):
        _stream = getattr(sys, _stream_name, None)
        _reconf = getattr(_stream, "reconfigure", None)
        if _reconf is None:
            continue
        try:
            _reconf(encoding="utf-8", errors="replace")
        except Exception as _exc:  # noqa: BLE001 - 明确告警，绝不静默
            sys.stderr.write(
                "[astral-gate] WARN: %s.reconfigure(utf-8) failed: %r\n"
                % (_stream_name, _exc)
            )


_astral_heal_stdio()

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# P0 对等两线；P1 明确不在本任务范围。
LINES = ("neoforge-1.21.1", "forge-1.20.1")

SIGN_REL = os.path.join(
    "src", "main", "java", "com", "merlinkitsune", "astral_dice",
    "item", "sign", "BonnieSignItem.java")

APPLY_SIG_RX = re.compile(
    r"public\s+void\s+apply\s*\(\s*ServerPlayer\s+player\s*,\s*LivingEntity\s+target\s*\)")

EARLY_REJECT_RX = re.compile(
    r"if\s*\(\s*target\s*\.\s*hasEffect\s*\(\s*ModEffects\s*\.\s*UNDERCOVER_INVESTIGATION"
    r"(?:\s*\.\s*get\s*\(\s*\))?\s*\)\s*\)\s*\{")

UNDERCOVER_APPLY_RX = re.compile(
    r"target\s*\.\s*addEffect\s*\(\s*new\s+MobEffectInstance\s*\(\s*"
    r"ModEffects\s*\.\s*UNDERCOVER_INVESTIGATION")

MARK_APPLY_RX = re.compile(r"\bMarkManager\s*\.\s*apply\s*\(\s*target\s*\)")

MARK_LEVEL_FROM_APPLY_RX = re.compile(
    r"int\s+markLevel\s*=\s*MarkManager\s*\.\s*apply\s*\(\s*target\s*\)\s*;")

MARK_GET_LEVEL_RX = re.compile(r"\bMarkManager\s*\.\s*getLevel\s*\(\s*target\s*\)")

COIN_RX = re.compile(
    r"new\s+ItemStack\s*\(\s*ModItems\s*\.\s*STAR_COIN\s*\.\s*get\s*\(\s*\)\s*,"
    r"\s*markLevel\s*\*\s*2\s*\)")

WRAPPED_RX = re.compile(r"if\s*\(\s*markLevel\s*>\s*0\s*\)")

BARE_MARKED_RX = re.compile(r"ModEffects\s*\.\s*MARKED\b")


def read(path):
    with open(path, "rb") as fh:
        return fh.read().decode("utf-8", "replace")


def strip_comments(src):
    """去掉行/块注释，保留字符串字面量（判据统一在剥注释后的文本上跑）。"""
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


def matching_close(src, open_idx):
    """open_idx 指向 `(` 或 `{`，返回配对的闭括号下标；失败返回 None。"""
    opener = src[open_idx]
    closer = ")" if opener == "(" else "}"
    depth = 0
    j = open_idx
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
                return None
            continue
        elif c == "/" and j + 1 < n and src[j + 1] == "*":
            j = src.find("*/", j)
            if j < 0:
                return None
            j += 1
        elif c == opener:
            depth += 1
        elif c == closer:
            depth -= 1
            if depth == 0:
                return j
        j += 1
    return None


def extract_apply_body(src):
    """抽取目标选择器注册里的 `public void apply(ServerPlayer, LivingEntity)` 方法体。"""
    m = APPLY_SIG_RX.search(src)
    if not m:
        return None
    i = src.find("{", m.end())
    if i < 0:
        return None
    j = matching_close(src, i)
    if j is None:
        return None
    return src[i:j + 1], i


def check_line(line, verbose, run):
    path = os.path.join(ROOT, line, SIGN_REL)
    if not os.path.isfile(path):
        run(None, "%s: 文件不存在 %s" % (line, SIGN_REL))
        return
    src = strip_comments(read(path))
    extracted = extract_apply_body(src)
    if extracted is None:
        run(None, "%s: 目标选择器 apply(ServerPlayer, LivingEntity) 方法无法定位" % line)
        return
    body, body_start = extracted

    # B1 早退闸门仍在，且在施加隐匿调查之前
    em = EARLY_REJECT_RX.search(body)
    undercover_pos = None
    um = UNDERCOVER_APPLY_RX.search(body)
    if um:
        undercover_pos = body_start + um.start()
    if not em:
        run(False, "B1 %s: 缺少「目标已带 UNDERCOVER_INVESTIGATION ⇒ return」早退闸门" % line)
    else:
        brace = body.find("{", em.end() - 1)
        bend = matching_close(body, brace)
        block = body[brace:bend + 1] if bend is not None else ""
        has_return = re.search(r"\breturn\s*;", block) is not None
        if not has_return:
            run(False, "B1 %s: 已带隐匿调查的早退分支内没有 return" % line)
        elif undercover_pos is not None and body_start + em.start() > undercover_pos:
            run(False, "B1 %s: 早退闸门被移到了施加隐匿调查之后" % line)
        else:
            run(True, "")
            if verbose:
                print("    ok B1 %s: 已带隐匿调查的早退闸门仍在施加之前" % line)

    # B2 顺序：隐匿调查 < MarkManager.apply < 返币
    mm = MARK_APPLY_RX.search(body)
    cm = COIN_RX.search(body)
    if um is None:
        run(False, "B2 %s: apply 内找不到施加隐匿调查的 addEffect(...UNDERCOVER_INVESTIGATION...)" % line)
    if mm is None:
        run(False, "B2 %s: apply 内找不到 MarkManager.apply(target)" % line)
    if cm is None:
        run(False, "B2 %s: apply 内找不到 `new ItemStack(ModItems.STAR_COIN.get(), markLevel * 2)`" % line)
    if um and mm and cm:
        apply_pos = body_start + mm.start()
        coin_pos = body_start + cm.start()
        if not (undercover_pos < apply_pos < coin_pos):
            run(False, "B2 %s: 顺序错误（隐匿调查=%d, MarkManager.apply=%d, 返币=%d）"
                       "—— 必须先施加隐匿调查、再加标记、最后返币"
                % (line, undercover_pos, apply_pos, coin_pos))
        else:
            run(True, "")
            if verbose:
                print("    ok B2 %s: 隐匿调查 → MarkManager.apply → 返币 顺序正确" % line)

    # B3 markLevel 必须取自 apply 的返回值（施加后层数）
    am = MARK_LEVEL_FROM_APPLY_RX.search(body)
    if not am:
        run(False, "B3 %s: 未用 `int markLevel = MarkManager.apply(target);` 取施加后层数" % line)
    elif cm and body_start + am.start() > body_start + cm.start():
        run(False, "B3 %s: markLevel 的赋值位于返币之后" % line)
    else:
        run(True, "")
        if verbose:
            print("    ok B3 %s: markLevel 取自 MarkManager.apply(target) 的返回" % line)
    if MARK_GET_LEVEL_RX.search(body):
        run(False, "B3 %s: apply 内仍有 MarkManager.getLevel(target) ⇒ 返币按施加前层数结算" % line)
    else:
        run(True, "")
        if verbose:
            print("    ok B3 %s: 无施加前 getLevel 读数" % line)

    # B4 返币不得被 markLevel > 0 包住
    if WRAPPED_RX.search(body):
        run(False, "B4 %s: 返币被 `if (markLevel > 0)` 包住 ⇒ 原 0 层目标拿不到本次 1 层的返还" % line)
    else:
        run(True, "")
        if verbose:
            print("    ok B4 %s: 返币未被 markLevel > 0 条件包住" % line)

    # B5 不得裸写 MARKED
    if BARE_MARKED_RX.search(body):
        run(False, "B5 %s: apply 内裸写 ModEffects.MARKED ⇒ 绕过 MarkManager 的层数/上限规则" % line)
    else:
        run(True, "")
        if verbose:
            print("    ok B5 %s: 未裸写 ModEffects.MARKED" % line)


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
            if verbose and msg:
                print("  ok " + msg)
        else:
            fails.append(msg)
            print("  FAIL " + msg)

    print("== 秘密侦探立牌主动顺序（P0 两线：neoforge-1.21.1 / forge-1.20.1；"
          "P1 neoforge-26.1.2 / fabric-1.20.1 明确不在检查范围）==")
    for line in LINES:
        print("[%s]" % line)
        check_line(line, verbose, run)

    print()
    if fails:
        print("GATE: FAIL  (%d 项不满足 / 共 %d 项)" % (len(fails), checks))
        for f in fails:
            print("  - " + f)
        return 1
    if unresolved:
        print("GATE: UNKNOWN  (%d 项无法判定 / 共 %d 项)" % (len(unresolved), checks))
        for u in unresolved:
            print("  - " + u)
        return 2
    print("GATE: PASS  (%d 项全部满足)" % checks)
    return 0


if __name__ == "__main__":
    sys.exit(main())
