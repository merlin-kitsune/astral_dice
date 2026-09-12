#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""筹码「配方 / 获取途径」守门校验（只读）。

在 mc-recipe-manual-audit（物品→配方→手册三方对齐）之上补一层**获取途径可达性**：
配方存在 ≠ 做得出。本脚本对 astral_dice:chips 全部成员做**依赖传递闭包**，
并顺带核对 curios 装备槽 / 创造栏 / 双版本对等。

可达性定义（迭代至不动点）：
  种子 = 有战利品表或赏金池来源 / 有代码发放（LootInjectionHandler 等）/ 配方不含任何本模组物品
  迭代 = 配方（任一）的全部本模组材料均已可达 → 该物品可达
  标签材料（如 #astral_dice:dice_t3）展开为其成员

退出码：0 = 无致命问题（手册缺口仅告警）；1 = 存在缺配方/不可达/悬空引用/槽缺项/对等性偏差；2 = 运行错误。

用法：
  python scripts/verify/verify_chip_acquisition.py [仓库根]
"""
import json, os, re, sys, glob
from collections import defaultdict

ROOT = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else ".")
NS = "astral_dice"
# 有意排除 curios 槽的纯基底材料（非 ICurioItem，本就不该装备）
SLOT_EXEMPT = {"blank_chip", "blank_sign"}


def rj(p):
    try:
        return json.load(open(p, encoding="utf-8"))
    except Exception:
        return None


def subs():
    return [d for d in ("neoforge-1.21.1", "forge-1.20.1")
            if os.path.isdir(os.path.join(ROOT, d, "src/main/java"))]


def moditems(sub):
    src = glob.glob(os.path.join(ROOT, sub, "src/main/java/**/ModItems.java"), recursive=True)
    if not src:
        raise RuntimeError(f"{sub}: 找不到 ModItems.java")
    txt = open(src[0], encoding="utf-8").read()
    c2i, i2c = {}, {}
    for m in re.finditer(r'\b(\w+)\s*=\s*registerItem\(\s*"([a-z0-9_]+)"', txt):
        c2i.setdefault(m.group(1), m.group(2))
        i2c.setdefault(m.group(2), m.group(1))
    return c2i, i2c


def tag_members(sub, rel, ns=NS):
    """rel 形如 'tags/item/chips.json'；1.20.1 自动尝试 'tags/items/'。"""
    base = os.path.join(ROOT, sub, "src/main/resources/data", ns)
    for cand in (rel, rel.replace("tags/item/", "tags/items/")):
        p = os.path.join(base, cand)
        if os.path.isfile(p):
            j = rj(p) or {}
            vals = j.get("values", []) if isinstance(j, dict) else []
            return {v if isinstance(v, str) else v.get("id", "") for v in vals}
    return set()


def all_tags(sub, ns=NS):
    """{tag_name: set(id)}，覆盖 tags/item(s)/**。"""
    out = defaultdict(set)
    for tdir in ("tags/item", "tags/items"):
        base = os.path.join(ROOT, sub, "src/main/resources/data", ns, tdir)
        if not os.path.isdir(base):
            continue
        for dp, _, fns in os.walk(base):
            for fn in fns:
                if not fn.endswith(".json"):
                    continue
                name = os.path.relpath(os.path.join(dp, fn), base)[:-5].replace("\\", "/")
                for v in (rj(os.path.join(dp, fn)) or {}).get("values", []):
                    s = v if isinstance(v, str) else v.get("id", "")
                    if s.startswith(NS + ":"):
                        out[name].add(s.split(":")[-1])
    return out


def recipes(sub):
    """{result_id: [(file, type, json)]}"""
    out = defaultdict(list)
    for d in ("src/generated/resources/data", "src/main/resources/data"):
        for rdir in ("recipe", "recipes"):
            base = os.path.join(ROOT, sub, d, NS, rdir)
            if not os.path.isdir(base):
                continue
            for fn in sorted(os.listdir(base)):
                if not fn.endswith(".json"):
                    continue
                j = rj(os.path.join(base, fn))
                if not isinstance(j, dict):
                    continue
                r = j.get("result")
                rid = (r.get("id") or r.get("item")) if isinstance(r, dict) else r
                if rid:
                    out[rid.split(":")[-1]].append((fn, j.get("type", "?"), j))
    return out


def _ingredients(j):
    mods, tgs = set(), set()

    def walk(o):
        if isinstance(o, str):
            if o.startswith("#" + NS + ":"):
                tgs.add(o[1:].split(":")[-1])
            elif o.startswith(NS + ":"):
                mods.add(o.split(":")[-1])
        elif isinstance(o, dict):
            for k in ("item", "id"):
                if isinstance(o.get(k), str):
                    walk(o[k])
            t = o.get("tag")
            if isinstance(t, str):
                if t.startswith("#" + NS + ":"):
                    tgs.add(t[1:].split(":")[-1])
                elif t.startswith(NS + ":"):
                    tgs.add(t.split(":")[-1])
            for k in ("items", "ingredients", "components"):
                if k in o:
                    walk(o[k])
        elif isinstance(o, list):
            for x in o:
                walk(x)

    for k in ("key", "ingredients"):
        if k in j:
            walk(j[k])
    return mods, tgs


def loot_sources(sub):
    hits = set()
    bases = [os.path.join(ROOT, sub, "src/main/resources/data", d)
             for d in (f"{NS}/loot_table", f"{NS}/loot_tables", "bountiful")]
    for base in bases:
        if not os.path.isdir(base):
            continue
        for dp, _, fns in os.walk(base):
            for fn in fns:
                if fn.endswith(".json"):
                    hits |= set(re.findall(r'"' + NS + r':([a-z0-9_]+)"',
                                           open(os.path.join(dp, fn), encoding="utf-8", errors="ignore").read()))
    return hits


def code_gives(sub, i2c):
    ids = set()
    for dp, _, fns in os.walk(os.path.join(ROOT, sub, "src/main/java")):
        for fn in fns:
            if fn.endswith(".java"):
                t = open(os.path.join(dp, fn), encoding="utf-8", errors="ignore").read()
                ids |= {i2c.get(c, c) for c in re.findall(r'ModItems\.([A-Z0-9_]+)\.get\(\)', t)}
    return ids


def manual_crafting(sub):
    """{item_id: 该手册条目是否有 crafting 页}"""
    base = os.path.join(ROOT, sub, "src/main/resources/assets", NS,
                        "patchouli_books/astral_guide/en_us/entries")
    res = {}
    for dp, _, fns in os.walk(base):
        for fn in fns:
            if fn.endswith(".json"):
                iid = os.path.basename(fn)[:-5]
                res[iid] = any("crafting" in str(p.get("type", ""))
                               for p in (rj(os.path.join(dp, fn)) or {}).get("pages", []))
    return res


def closure(sub, i2c, rec, tags, loot, gives):
    reach = set()
    for iid in i2c:
        if iid in loot or iid in gives:
            reach.add(iid)
    for iid, rlist in rec.items():
        for _, _, j in rlist:
            mods, tgs = _ingredients(j)
            need = set(mods)
            for t in tgs:
                need |= tags.get(t, set())
            if not need:
                reach.add(iid)
                break
    while True:
        grew = False
        for iid, rlist in rec.items():
            if iid in reach:
                continue
            for _, _, j in rlist:
                mods, tgs = _ingredients(j)
                need = set(mods)
                for t in tgs:
                    need |= tags.get(t, set())
                if need <= reach:
                    reach.add(iid)
                    grew = True
                    break
        if not grew:
            return reach


def main():
    fatal, warn = [], []
    print("=" * 78)
    print("筹码「配方 / 获取途径」守门校验")
    print("=" * 78)
    summary = {}
    for sub in subs():
        c2i, i2c = moditems(sub)
        chips = sorted(x.split(":")[-1] for x in tag_members(sub, "tags/item/chips.json"))
        rec = recipes(sub)
        tags = all_tags(sub)
        loot = loot_sources(sub)
        gives = code_gives(sub, i2c)
        reach = closure(sub, i2c, rec, tags, loot, gives)
        slot = {x.split(":")[-1] for x in tag_members(sub, "tags/item/chip.json", ns="curios")}
        craft = manual_crafting(sub)
        ct = glob.glob(os.path.join(ROOT, sub, "src/main/java/**/ModCreativeTabs.java"), recursive=True)
        creative = set(re.findall(r'output\.accept\(ModItems\.([A-Z0-9_]+)\.get\(\)\)',
                                  open(ct[0], encoding="utf-8").read())) if ct else set()
        creative_ids = {c2i.get(c, c) for c in creative}

        no_rec, unreach, dangling, no_slot, no_tab, no_page = [], [], defaultdict(list), [], [], []
        for cid in chips:
            rlist = rec.get(cid)
            if not rlist:
                no_rec.append(cid)
            else:
                for _, _, j in rlist:
                    mods, tgs = _ingredients(j)
                    need = set(mods)
                    for t in tgs:
                        need |= tags.get(t, set())
                    for m in sorted(need):
                        if m not in i2c:
                            dangling[cid].append(m)
                if cid not in reach:
                    unreach.append(cid)
            if cid not in slot and cid not in SLOT_EXEMPT:
                no_slot.append(cid)
            if cid not in creative_ids:
                no_tab.append(cid)
            if craft.get(cid) is False:
                no_page.append(cid)

        print(f"\n### {sub}")
        print(f"  筹码 {len(chips)} | 有配方 {len(chips)-len(no_rec)} | 传递可达 {len(chips)-len(unreach)}")
        print(f"  curios:chip 槽 {len(slot)} | 创造栏覆盖 {len(chips)-len(no_tab)}/{len(chips)}")
        for label, lst, f in (("❌ 缺配方", no_rec, True), ("❌ 传递不可达", unreach, True),
                              ("❌ 配方引用未注册物品", {k: v for k, v in dangling.items()}, True),
                              ("❌ curios:chip 槽缺项", no_slot, True),
                              ("❌ 未进创造栏", no_tab, True),
                              ("⚠️ 手册未展示 crafting 页", no_page, False)):
            if lst:
                (fatal if f else warn).append(f"[{sub}] {label}: {lst}")
                print(f"    {label} {len(lst)}: {lst}")
        if not (no_rec or unreach or dangling or no_slot or no_tab):
            print("    ✅ 无致命问题")
        summary[sub] = dict(chips=chips, no_rec=no_rec, unreach=unreach, no_page=no_page)

    if len(summary) == 2:
        a, b = list(summary)
        print("\n" + "=" * 78)
        print("双版本对等性")
        print("=" * 78)
        for key in ("chips", "no_rec", "unreach", "no_page"):
            sa, sb = set(summary[a][key]), set(summary[b][key])
            flag = "✅" if sa == sb else "❌"
            print(f"  {flag} {key}: 一致={sa == sb} 差异={sorted(sa ^ sb)}")
            if sa != sb:
                fatal.append(f"[parity] {key} 双版本不一致: {sorted(sa ^ sb)}")

    print("\n" + "=" * 78)
    if fatal:
        print("RESULT: FAIL")
        for f in fatal:
            print("  " + f)
        return 1
    print("RESULT: PASS（致命项 0）")
    for w in warn:
        print("  " + w)
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as e:
        print("ERROR:", e)
        sys.exit(2)
