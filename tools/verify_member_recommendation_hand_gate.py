#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""会员推荐信「手牌 <5 才发牌」的静态守门（P0 两线：neoforge-1.21.1 / forge-1.20.1）。

## 要拦的回归
用户裁决：会员推荐信只在**玩家物品栏中的手牌数量 < 5 张**时发牌；降价效果不受此限制。
会咬人的错误形态：
1. 把数量门写进 `isEquipped` / `shopPriceReduction` ⇒ 降价连带失效（装备了却不再 -1）；
2. 阈值写成 4 / 6，或比较方向写成 `> 5` / `>= 4` ⇒ 边界（0、4 应发；5、6、36 不应发）错位；
3. 只数主背包不数副手，或用 `getContainerSize()` 当数量（那不是手牌数）；
4. 数量门与 `giveCardTo` 之间夹了别的分支，或根本没接在发牌分支上。

## 判据（M1~M5，逐线）
- **M1 阈值与方向**：从 `onBlessingStart` 中 `giveCardTo` **之前**的守卫条件里**提取**比较符与阈值
  （阈值允许是 `static final int` 常量），再对 `0/4/5/6/36` **求值**：预期 0、4 发牌；5、6、36 不发。
  ⚠️ 不允许只查注释或固定字符串 —— 期望值来自本文件的手写表 `GIVE_CASES`，判定来自对提取到的
  比较符/阈值求值。
- **M2 位置**：数量门必须位于 `isEquipped(player)` 判断**之后**、`giveCardTo` **之前**，
  且守卫分支必须真的决定是否发牌（早退 `return;` 或包住 `giveCardTo`）。
- **M3 计数口径**：计数 helper 必须同时遍历 `getInventory().items` 与 `getInventory().offhand`，
  用 `isCardItem(` 判定、`getCount()` 累加；**不得**出现 `getContainerSize`。
- **M4 降价解耦**：`isEquipped` 与 `shopPriceReduction` 方法体内**不得**出现手牌计数
  （helper 名 / `isCardItem` / `getCount`）；`shopPriceReduction` 仍必须是
  `return isEquipped(player) ? 1 : 0;`（降价只看装备）。
- **M5 依赖边界**：筹码文件不得 import `item.sign.*`（不引入立牌依赖）。

退出码：0 全部通过 / 1 存在违规 / 2 无法判定（文件缺失、方法或守卫形态无法解析）。
（有违规时优先返回 1：红灯必须先表达「功能没做对」，而不是「门坏了」。）

## 本守门证明不了什么
文本/结构启发式，不是 Java 语义分析：无法阻止「保留满足门禁的文本、却用死分支/短路把它架空」
这类刻意混淆；也不证明运行时发牌真的发生（那需要实机/单测）。它拦的是「改实现忘了同步数量门」
这一类真实回归。

用法：python tools/verify_member_recommendation_hand_gate.py [-v]
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

# P0 对等两线；P1（neoforge-26.1.2 / fabric-1.20.1）不在本任务范围，明确不查。
LINES = ("neoforge-1.21.1", "forge-1.20.1")

CHIP_REL = os.path.join(
    "src", "main", "java", "com", "merlinkitsune", "astral_dice",
    "item", "chip", "MemberRecommendationChipItem.java")

# 用户裁决：手牌 < 5 才发牌
THRESHOLD = 5
# 手写期望表（不依赖被测代码计算）：(手牌数, 是否应发牌)
GIVE_CASES = ((0, True), (4, True), (5, False), (6, False), (36, False))

CMP_RX = re.compile(r"^(?P<left>.+?)\s*(?P<op><=|>=|==|!=|<|>)\s*(?P<right>.+)$", re.S)
LEFT_CALL_RX = re.compile(r"^(?P<fn>[A-Za-z_$][\w$]*)\s*\((?P<args>[^()]*)\)$")
HAND_TOKENS = ("isCardItem", "getCount", "getContainerSize")
# 守卫缺失时的固定标记：用于把「功能未实现」归为违规（exit 1）而非「无法判定」（exit 2）
NO_GUARD_MARK = "找不到手牌计数阈值守卫"


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


def method_body(src, name):
    """按花括号配平抽取 `name(` 之后第一个 `{...}` 方法体；跳过字符串/字符/注释。找不到返回 None。"""
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


def resolve_int(token, src):
    """把阈值 token 解析成整数：十进制字面量或同文件内的 `int NAME = N;` 常量。"""
    token = token.strip()
    if re.fullmatch(r"\d+", token):
        return int(token)
    m = re.search(r"\bint\s+" + re.escape(token) + r"\s*=\s*(\d+)\s*;", src)
    if m:
        return int(m.group(1))
    return None


def eval_cmp(left_value, op, threshold):
    if op == "<":
        return left_value < threshold
    if op == "<=":
        return left_value <= threshold
    if op == ">":
        return left_value > threshold
    if op == ">=":
        return left_value >= threshold
    if op == "==":
        return left_value == threshold
    if op == "!=":
        return left_value != threshold
    raise ValueError("unsupported operator: " + op)


def guard_kind(body, close, give_pos):
    """判定守卫分支形态：early_return（条件成立就早退，否则发牌）/ allow（条件成立才进发牌块）。"""
    rest = body[close + 1:]
    if re.match(r"\s*return\s*;", rest):
        return "early_return"
    m = re.match(r"\s*\{", rest)
    if m:
        bstart = close + 1 + m.end() - 1
        bend = matching_close(body, bstart)
        if bend is None:
            return None
        block = body[bstart:bend + 1]
        if "giveCardTo" in block and give_pos > bstart:
            return "allow"
        if re.search(r"\breturn\s*;", block):
            return "early_return"
    return None


def parse_guard(body, give_pos, src):
    """在 giveCardTo 之前找到「手牌计数 比较 阈值」守卫。

    返回 (info, None) 或 (None, 原因)。info 含条件原文、比较符、阈值、计数 helper、守卫形态。
    """
    last = None
    for m in re.finditer(r"\bif\s*\(", body[:give_pos]):
        open_idx = body.index("(", m.start())
        close = matching_close(body, open_idx)
        if close is None or close >= give_pos:
            continue
        cond = body[open_idx + 1:close].strip()
        cm = CMP_RX.match(cond)
        if not cm:
            continue
        threshold = resolve_int(cm.group("right"), src)
        lm = LEFT_CALL_RX.match(cm.group("left").strip())
        # 只认「以 player 为参数的计数调用 比较 可解析整数」这一形态；
        # `player == null || ...` 这类非阈值条件必须排除，否则会把「没有门」误报成「无法判定」。
        if threshold is None or lm is None or "player" not in lm.group("args"):
            continue
        last = (m.start(), close, cond, cm, threshold, lm)
    if last is None:
        return None, NO_GUARD_MARK + "（giveCardTo 之前无「手牌计数 比较 阈值」判断）"
    start, close, cond, cm, threshold, lm = last
    kind = guard_kind(body, close, give_pos)
    if kind is None:
        return None, "无法判定守卫分支是否决定发牌：`%s`" % cond
    return {
        "start": start,
        "close": close,
        "cond": cond,
        "op": cm.group("op"),
        "threshold": threshold,
        "helper": lm.group("fn"),
        "give_pos": give_pos,
        "kind": kind,
    }, None


def check_line(line, verbose, run):
    path = os.path.join(ROOT, line, CHIP_REL)
    if not os.path.isfile(path):
        run(None, "%s: 文件不存在 %s" % (line, CHIP_REL))
        return
    src = strip_comments(read(path))
    body = method_body(src, "onBlessingStart")
    if body is None:
        run(None, "%s: onBlessingStart 方法未找到" % line)
        return
    give_pos = body.find("RandomCardHandler.giveCardTo")
    if give_pos < 0:
        give_pos = body.find("giveCardTo")
    if give_pos < 0:
        run(False, "%s: onBlessingStart 内没有 giveCardTo 调用" % line)
        return

    info, why = parse_guard(body, give_pos, src)
    if info is None:
        # 守卫缺失 = 功能尚未实现/被回退 ⇒ 明确违规（不是「无法判定」）
        if NO_GUARD_MARK in why:
            run(False, "M1 %s: onBlessingStart 未实现手牌数量门 —— %s" % (line, why))
        else:
            run(None, "M1 %s: 无法判定手牌数量门 —— %s" % (line, why))
    else:
        if info["threshold"] != THRESHOLD:
            run(False, "M1 %s: 提取到的阈值为 %d，裁决要求恰为 %d（条件 `%s`）"
                % (line, info["threshold"], THRESHOLD, info["cond"]))
        else:
            bad = []
            rows = []
            for count, expected in GIVE_CASES:
                cond_value = eval_cmp(count, info["op"], info["threshold"])
                gives = (not cond_value) if info["kind"] == "early_return" else cond_value
                rows.append((count, expected, cond_value, gives))
                if gives != expected:
                    bad.append("count=%d 期望%s、求值%s" % (
                        count, "发" if expected else "不发", "发" if gives else "不发"))
            if bad:
                run(False, "M1 %s: 数量门语义不是「<5 发牌」（extracted: `%s` ⇒ 发牌条件 = %s%s%s）：%s"
                    % (line, info["cond"],
                       "not(" if info["kind"] == "early_return" else "",
                       info["op"] + " " + str(info["threshold"]),
                       ")" if info["kind"] == "early_return" else "",
                       "；".join(bad)))
            else:
                run(True, "")
                if verbose:
                    print("    ok M1 %s: `%s` ⇒ 发牌条件 = %s(%s %s %d)；求值 %s"
                          % (line, info["cond"],
                             "not " if info["kind"] == "early_return" else "",
                             "count", info["op"], info["threshold"],
                             ", ".join("%d→%s" % (c, "发" if g else "不发") for c, _, _, g in rows)))

        # M2 位置：装备判断之后、giveCardTo 之前，且守卫真的决定发牌
        equip_pos = body.find("isEquipped(player)")
        if equip_pos < 0:
            run(False, "M2 %s: onBlessingStart 内找不到 isEquipped(player) 装备判断" % line)
        elif not (equip_pos < info["start"] < info["close"] < give_pos):
            run(False, "M2 %s: 数量门未位于 isEquipped 之后、giveCardTo 之前"
                       "（equip=%d, guard=%d..%d, give=%d）"
                % (line, equip_pos, info["start"], info["close"], give_pos))
        else:
            run(True, "")
            if verbose:
                print("    ok M2 %s: 数量门位于装备判断之后、giveCardTo 之前（%s）"
                      % (line, info["kind"]))

        # M3 计数口径
        helper_body = method_body(src, info["helper"])
        if helper_body is None:
            run(False, "M3 %s: 计数 helper `%s` 方法体未找到" % (line, info["helper"]))
        else:
            problems = []
            if not re.search(r"getInventory\s*\(\s*\)\s*\.\s*items\b", helper_body):
                problems.append("未遍历主背包 getInventory().items")
            if not re.search(r"getInventory\s*\(\s*\)\s*\.\s*offhand\b", helper_body):
                problems.append("未遍历副手 getInventory().offhand")
            if "isCardItem(" not in helper_body:
                problems.append("未用 ModItems.isCardItem 判定卡牌")
            if not re.search(r"\+=\s*[^;]*getCount\s*\(\s*\)", helper_body):
                problems.append("未按 getCount() 累加")
            if "getContainerSize" in helper_body:
                problems.append("使用了 getContainerSize()（不是手牌数量）")
            if problems:
                run(False, "M3 %s: 计数 helper `%s` 口径不符 —— %s"
                    % (line, info["helper"], "；".join(problems)))
            else:
                run(True, "")
                if verbose:
                    print("    ok M3 %s: `%s` 遍历 items+offhand、isCardItem+getCount 累加"
                          % (line, info["helper"]))

        # M4 降价解耦
        for meth in ("isEquipped", "shopPriceReduction"):
            mbody = method_body(src, meth)
            if mbody is None:
                run(None, "M4 %s: %s 方法未找到" % (line, meth))
                continue
            bad_tokens = [t for t in HAND_TOKENS if t in mbody] + (
                [info["helper"]] if info["helper"] in mbody else [])
            if bad_tokens:
                run(False, "M4 %s: %s 方法体内混入手牌数量门（%s）⇒ 降价会被连带限制"
                    % (line, meth, ", ".join(sorted(set(bad_tokens)))))
            else:
                run(True, "")
                if verbose:
                    print("    ok M4 %s: %s 不含手牌数量门" % (line, meth))
        spr = method_body(src, "shopPriceReduction")
        if spr is not None:
            if re.search(r"return\s+isEquipped\s*\(\s*player\s*\)\s*\?\s*1\s*:\s*0\s*;", spr):
                run(True, "")
                if verbose:
                    print("    ok M4 %s: shopPriceReduction 仍只看 isEquipped ⇒ 1/0" % line)
            else:
                run(False, "M4 %s: shopPriceReduction 不再是 `return isEquipped(player) ? 1 : 0;`"
                    % line)

    # M5 依赖边界（与守卫是否解析成功无关，独立检查）
    if re.search(r"import\s+com\.merlinkitsune\.astral_dice\.item\.sign\.", src):
        run(False, "M5 %s: 筹码文件 import 了 item.sign.* ⇒ 引入了立牌依赖" % line)
    else:
        run(True, "")
        if verbose:
            print("    ok M5 %s: 无 item.sign.* 依赖" % line)


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

    print("== 会员推荐信手牌门（P0 两线：neoforge-1.21.1 / forge-1.20.1；P1 不查）==")
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
