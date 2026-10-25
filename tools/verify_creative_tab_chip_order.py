#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""创造模式物品栏口径守门（2026-10-25 用户裁决）。

判据（P0 两线 neoforge-1.21.1 / forge-1.20.1，逐条独立断言）：

D1 强化拒止物品 repulsion 不在创造栏 displayItems 里（JEI 物品列表直接取自创造栏
   —— jei 的 ItemStackListFactory 迭代 CreativeModeTabs.allTabs() 的 getDisplayItems()，
   故这一条同时保证 JEI 侧隐藏；效果牌 effect_card_repulsion 必须仍在）。
D2 筹码分组顺序保持不变：星光类 → 治愈类 → 标记类 → 充能类 → 无流派。
D3 每组筹码按稀有度升序（COMMON < 稀有 < 史诗 < 传奇 < 巅峰 < 奇特），同档保持原相对顺序
   —— 判据只看是否非降序，同档内部顺序无法从代码判定，属评审项。
D4 筹码总数 = 67（增删筹码必须同步改这里的常量，防止静默丢项）。
D5 两线筹码段逐行等价（空白规范化后必须完全一致，防止单边漏改）。

用法：python -B tools/verify_creative_tab_chip_order.py [--quiet]
退出码：0 = 全通过；1 = 有失败项。
"""
import re
import sys
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LINES = ["neoforge-1.21.1", "forge-1.20.1"]

TAB = os.path.join("src", "main", "java", "com", "merlinkitsune", "astral_dice", "init", "ModCreativeTabs.java")
ITEMS = os.path.join("src", "main", "java", "com", "merlinkitsune", "astral_dice", "item", "ModItems.java")

EXPECT_GROUPS = ["星光类", "治愈类", "标记类", "充能类", "无流派"]
EXPECT_CHIPS = 67
RANK = {"COMMON": 0, "rare": 1, "epic": 2, "legendary": 3, "pinnacle": 4, "bizarre": 5}

failures = []
notes = []


def read(p):
    with open(p, encoding="utf-8") as f:
        return f.read()


def chip_segment(text, line):
    head = text.find("// 筹码")
    tail = text.find("// 任务S §4")
    if head < 0 or tail < 0:
        failures.append("%s: 筹码段标记缺失（// 筹码=%d, // 任务S 4=%d）" % (line, head, tail))
        return ""
    return text[head:tail]


def rarities(items_src):
    out = {}
    for m in re.finditer(r'Item>\s+([A-Z0-9_]+)\s*=\s*registerItem\(\s*"([a-z0-9_]+)"', items_src):
        seg = items_src[m.start():m.start() + 1400]
        r = re.search(r"AstralRarities\.(rare|epic|legendary|pinnacle|bizarre)\(\)|Rarity\.(COMMON|UNCOMMON|RARE|EPIC)", seg)
        out[m.group(1)] = (r.group(1) or r.group(2)) if r else "COMMON"
    return out


segs = {}
for line in LINES:
    tab_path = os.path.join(ROOT, line, TAB)
    items_path = os.path.join(ROOT, line, ITEMS)
    if not os.path.isfile(tab_path):
        failures.append("%s: 缺 ModCreativeTabs.java" % line)
        continue
    tab = read(tab_path)
    rar = rarities(read(items_path))

    if re.search(r"output\.accept\(ModItems\.REPULSION\.get\(\)\)", tab):
        failures.append("%s D1: 强化拒止物品仍在创造栏 displayItems 里" % line)
    if not re.search(r"output\.accept\(ModItems\.EFFECT_CARD_REPULSION\.get\(\)\)", tab):
        failures.append("%s D1: 效果牌 effect_card_repulsion 被误删" % line)

    seg = chip_segment(tab, line)
    segs[line] = seg
    if not seg:
        continue

    groups = re.findall(r"// === (.+?) ===", seg)
    if groups != EXPECT_GROUPS:
        failures.append("%s D2: 筹码分组顺序变了 -> %s（期望 %s）" % (line, groups, EXPECT_GROUPS))

    for part in re.split(r"// === ", seg)[1:]:
        gname = part.split(" ===")[0]
        names = re.findall(r"ModItems\.([A-Z0-9_]+)\.get\(\)", part)
        prev = -1
        for n in names:
            cur = RANK.get(rar.get(n, "COMMON"), 9)
            if cur < prev:
                failures.append("%s D3: 组「%s」稀有度乱序：%s 之前已出现更高档" % (line, gname, n))
                break
            prev = cur

    chips = re.findall(r"ModItems\.([A-Z0-9_]+)\.get\(\)", seg)
    if len(chips) != EXPECT_CHIPS:
        failures.append("%s D4: 筹码数 %d != %d" % (line, len(chips), EXPECT_CHIPS))
    if len(set(chips)) != len(chips):
        failures.append("%s D4: 筹码段有重复项" % line)

if len(segs) == 2:
    a, b = segs[LINES[0]], segs[LINES[1]]
    if re.sub(r"\s+", "", a) != re.sub(r"\s+", "", b):
        failures.append("D5: 两线筹码段不一致（空白规范化后仍不同）")

if not failures:
    for line in LINES:
        chips = re.findall(r"ModItems\.([A-Z0-9_]+)\.get\(\)", segs.get(line, ""))
        notes.append("%s: 筹码 %d 个 / 组序 %s / 稀有度升序 OK" % (line, len(chips), "→".join(EXPECT_GROUPS)))

if __name__ == "__main__":
    quiet = "--quiet" in sys.argv
    for n in notes:
        if not quiet:
            print("  " + n)
    if failures:
        print("GATE: FAIL (%d 项)" % len(failures))
        for f in failures:
            print("  - " + f)
        sys.exit(1)
    print("GATE: PASS (D1-D5 全部满足，两线)")
