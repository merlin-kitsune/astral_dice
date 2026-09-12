# -*- coding: utf-8 -*-
"""Bountiful 赏金联动一致性校验（只读守门）。

独立重算「按规则应进入赏金池的物品集合」，与双版本四份池文件逐项比对：
  1. 双版本 ModItems 注册物品 id 与品质完全一致；
  2. astral_objs = 非传奇骰子 + 货币(star_coin / star_coin_bag / star_plate / golden_star_plate)；
  3. astral_rews = astral_objs ∪ 卡牌(全部) ∪ 非传奇筹码 ∪ 非传奇立牌
     （**传奇=物品层 Rarity.UNCOMMON，数据层 LEGENDARY**：传奇骰子/筹码/立牌不进 rews；卡牌不受限）；
  4. 集合相等（0 缺失 / 0 多余），且数据层 rarity 与物品品质映射一致
     （RARE→RARE、EPIC→EPIC、UNCOMMON→LEGENDARY）；
  5. 双版本四份文件逐字节一致（md5）；
  6. 文件格式：UTF-8 / CRLF / Tab 缩进 / 末尾换行；
  7. 价值平衡式：objs 顶值(1 条, amount.max×unitWorth) ≥ rews 顶值(2 条之和) × 0.9
     （Bountiful 加载告警阈值，违反会刷不出匹配赏金）。

退出码：0 = 全部通过；1 = 存在致命偏差。只读，不修改任何工程文件。

用法: python scripts/verify/verify_bountiful_pools.py [--root .]
"""
import hashlib
import io
import json
import re
import sys

NS = "astral_dice"
VERSIONS = ["neoforge-1.21.1", "forge-1.20.1"]
MODITEMS_REL = "src/main/java/com/merlinkitsune/astral_dice/item/ModItems.java"
POOL_REL = "src/main/resources/data/bountiful/bounty_pools/bountiful/"
DECREE_REL = "src/main/resources/data/bountiful/bounty_decrees/bountiful/astral.json"

REG = re.compile(r'registerItem\("([a-z0-9_]+)"')
RAR = re.compile(r'rarity\(Rarity\.([A-Z_]+)\)')

DICE = {"dice", "golden_dice", "glass_dice", "netherrack_dice", "diamond_dice",
        "emerald_dice", "obsidian_dice", "weird_dice", "amethyst_dice",
        "netherite_dice", "crimson_dice", "ender_dice", "nether_star_dice"}
MONEY = {"star_coin", "star_coin_bag", "star_plate", "golden_star_plate"}
LEGEND = "UNCOMMON"                       # 本 mod「金 = 传奇」
RARITY_MAP = {"COMMON": "COMMON", "RARE": "RARE", "EPIC": "EPIC", "UNCOMMON": "LEGENDARY"}

errors, warnings = [], []


def err(m):
    errors.append(m)


def warn(m):
    warnings.append(m)


def read_text(p):
    return io.open(p, "r", encoding="utf-8", newline="").read()


def parse_items(path):
    text = read_text(path)
    hits = list(REG.finditer(text))
    out = {}
    for i, m in enumerate(hits):
        end = hits[i + 1].start() if i + 1 < len(hits) else len(text)
        r = RAR.search(text[m.end():end])
        out[m.group(1)] = r.group(1) if r else "COMMON"
    return out


def classify(items):
    c = {"dice": set(), "money": set(), "cards": set(), "signs": set(),
         "chips": set(), "materials": set()}
    for k in items:
        if k in DICE:
            c["dice"].add(k)
        elif k in MONEY:
            c["money"].add(k)
        elif k.startswith(("attack_card_", "defense_card_", "effect_card_")):
            c["cards"].add(k)
        elif k.endswith("_sign") and k != "blank_sign":
            c["signs"].add(k)
        elif k.endswith("_chip") and k != "blank_chip":
            c["chips"].add(k)
        else:
            c["materials"].add(k)
    return c


def expected(items, c):
    objs = {k for k in c["dice"] if items[k] != LEGEND} | c["money"]
    rews = set(objs) | c["cards"]
    rews |= {k for k in c["signs"] if items[k] != LEGEND}
    rews |= {k for k in c["chips"] if items[k] != LEGEND}
    return objs, rews


def load_pool(path):
    """返回 (content_id → 条目 dict) 与原始字节。"""
    raw = read_text(path)
    d = json.loads(raw, object_pairs_hook=dict)
    return {v["content"].split(":", 1)[1]: v for v in d["content"].values()}, raw


def main():
    root = "."
    if "--root" in sys.argv:
        root = sys.argv[sys.argv.index("--root") + 1]
    root = root.rstrip("/\\")
    pre = (root + "/") if root not in (".", "") else ""

    # 1) 双版本物品清单一致
    items_by_ver = {}
    for ver in VERSIONS:
        p = pre + ver + "/" + MODITEMS_REL
        items_by_ver[ver] = parse_items(p)
    a, b = items_by_ver[VERSIONS[0]], items_by_ver[VERSIONS[1]]
    if a != b:
        only_a = set(a) - set(b)
        only_b = set(b) - set(a)
        diff_rar = {k for k in set(a) & set(b) if a[k] != b[k]}
        err("双版本 ModItems 不一致：仅 %s=%s；品质差异=%s"
            % (VERSIONS[0], sorted(only_a), sorted(diff_rar)))
        err("双版本 ModItems 不一致：仅 %s=%s" % (VERSIONS[1], sorted(only_b)))
    items = a
    c = classify(items)
    eo, er = expected(items, c)
    print("物品分类: %s 总 %d" % ({k: len(v) for k, v in c.items()}, len(items)))
    print("规则应含: objs %d / rews %d" % (len(eo), len(er)))
    excl = sorted(k for k in (c["chips"] | c["signs"] | c["dice"]) if items[k] == LEGEND)
    print("按规则排除(传奇筹码/立牌/骰子): %d 项" % len(excl))

    # 2/3/4) 逐版本逐池比对
    for ver in VERSIONS:
        for pool, exp in (("astral_objs.json", eo), ("astral_rews.json", er)):
            path = pre + ver + "/" + POOL_REL + pool
            content, raw = load_pool(path)
            actual = set(content)
            miss, extra = sorted(exp - actual), sorted(actual - exp)
            tag = "%s/%s" % (ver, pool)
            if miss:
                err("%s 缺失条目(%d): %s" % (tag, len(miss), miss))
            if extra:
                err("%s 多余/应排除条目(%d): %s" % (tag, len(extra), extra))
            if not miss and not extra:
                print("OK  %-40s 条目 %d 与规则一致" % (tag, len(actual)))

            # 数据层 rarity 映射（缺省字段 = COMMON，Bountiful 默认）
            bad_r = [(k, v.get("rarity"), RARITY_MAP.get(items[k]))
                     for k, v in content.items()
                     if k in items and v.get("rarity", "COMMON") != RARITY_MAP.get(items[k])]
            if bad_r:
                err("%s rarity 映射错误: %s" % (tag, bad_r[:8]))

            # 格式
            rawb = io.open(path, "rb").read()
            if rawb.count(b"\r\n") != rawb.count(b"\n"):
                err("%s 换行符非纯 CRLF" % tag)
            if not rawb.endswith(b"\r\n"):
                err("%s 末尾缺换行" % tag)
            if b"\r\n    " in rawb or b"\r\n  " in rawb:
                err("%s 存在空格缩进(应为 Tab)" % tag)

    # 5) 双版本逐字节一致
    for pool in ("astral_objs.json", "astral_rews.json", "astral.json"):
        path = [pre + v + "/" + (DECREE_REL if pool == "astral.json" else POOL_REL + pool)
                for v in VERSIONS]
        try:
            digests = [hashlib.md5(io.open(p, "rb").read()).hexdigest() for p in path]
        except OSError as e:
            err("双版本文件缺失: %s" % e)
            continue
        if len(set(digests)) != 1:
            err("双版本不一致: %s → %s" % (pool, digests))
        else:
            print("OK  双版本一致 %-24s md5 %s" % (pool, digests[0]))

    # 6) 价值平衡式（逐版本，取 neoforge 结果展示）
    for ver in VERSIONS:
        objs, _ = load_pool(pre + ver + "/" + POOL_REL + "astral_objs.json")
        rews, _ = load_pool(pre + ver + "/" + POOL_REL + "astral_rews.json")
        objs_top = max(v["amount"]["max"] * v["unitWorth"] for v in objs.values())
        top2 = sorted((v["amount"]["max"] * v["unitWorth"] for v in rews.values()), reverse=True)[:2]
        need = sum(top2) * 0.9
        ok = objs_top >= need
        print("%s 价值平衡: objs 顶值 %d ≥ rews 顶值2和 %d × 0.9 = %.0f → %s"
              % ("OK  " if ok else "FAIL", objs_top, sum(top2), need, "PASS" if ok else "FAIL"))
        if not ok:
            err("%s 价值平衡式不成立" % ver)

    # 7) decree 引用
    for ver in VERSIONS:
        p = pre + ver + "/" + DECREE_REL
        try:
            d = json.loads(read_text(p))
            if d.get("objectives") != ["astral_objs"] or d.get("rewards") != ["astral_rews"]:
                err("%s decree 引用异常: %s" % (ver, d))
            else:
                print("OK  %s decree 引用 astral_objs/astral_rews" % ver)
        except OSError as e:
            err("%s decree 缺失: %s" % (ver, e))

    print()
    for w in warnings:
        print("WARN", w)
    if errors:
        for e in errors:
            print("FAIL", e)
        print("\n结果: %d 项致命偏差" % len(errors))
        return 1
    print("结果: ALL OK" + ("（%d 条提示）" % len(warnings) if warnings else ""))
    return 0


if __name__ == "__main__":
    sys.exit(main())
