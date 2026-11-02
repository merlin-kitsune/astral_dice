#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""筹码「配方 / 获取途径」守门校验（只读）。

在 mc-recipe-manual-audit（物品→配方→手册三方对齐）之上补一层**获取途径可达性**：
配方存在 ≠ 做得出。本脚本对 astral_dice:chips 全部成员做**依赖传递闭包**，
并顺带核对 curios 装备槽 / 创造栏 / 双版本对等 / 手册页与获取说明。

可达性定义（迭代至不动点）：
  种子 = 有战利品表或赏金池来源 / 有代码发放（LootInjectionHandler 等）/ 配方不含任何本模组物品
  迭代 = 配方（任一）的全部本模组材料均已可达 → 该物品可达
  标签材料（如 #astral_dice:dice_t3）展开为其成员

八项检查（与 verify_chip_acquisition.ps1 逐项对齐）：
  ❌ 缺配方 no_rec / ❌ 传递不可达 unreach / ❌ 配方引用未注册物品 dangling /
  ❌ curios:chip 槽缺项 no_slot / ❌ 未进创造栏 no_tab / ⚠️ 手册未展示 crafting 页 no_page(告警) /
  ❌ 手册仍展示 crafting 页（配方默认关闭）stale_page / ❌ 手册未说明筹码获取途径 no_note

退出码：0 = 无致命问题（手册缺口 no_page 仅告警）；1 = 存在缺配方/不可达/悬空引用/槽缺项/
        未进创造栏/手册残留 crafting 页/手册未说明获取途径/对等性偏差；2 = 运行错误或无法判定。

⚠️ 反「假绿」：默认仓库根取本脚本位置上溯三层（与本 cwd 无关）；显式传入的根也照样校验。
   子项目数为 0、或某子项目 chips 标签成员为 0 ⇒ 一律判"无法判定"并 exit 2，绝不 vacuous PASS。

用法：
  python scripts/verify/verify_chip_acquisition.py [仓库根]
"""
import json, os, re, sys, glob
from collections import defaultdict

try:  # 与 ps1 逐字节对比时避免 CRLF 翻译
    sys.stdout.reconfigure(encoding="utf-8", newline="\n")
    sys.stderr.reconfigure(encoding="utf-8", newline="\n")
except Exception:
    pass

NS = "astral_dice"
DEFAULT_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ROOT = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else DEFAULT_ROOT)
# 有意排除 curios 槽的纯基底材料（非 ICurioItem，本就不该装备）
SLOT_EXEMPT = {"blank_chip", "blank_sign"}
NOTE_KEY = "astral_dice.guide.chip_source.note"


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


def gated_recipes(sub):
    """受 chip_recipes_enabled 条件门禁的筹码配方（result 的 item id 集合）。

    默认 enable_chip_recipes=false ⇒ 这些配方整体不加载，手册不应展示 crafting 页。
    """
    out = set()
    for d in ("src/generated/resources/data", "src/main/resources/data"):
        for rdir in ("recipe", "recipes"):
            base = os.path.join(ROOT, sub, d, NS, rdir)
            if not os.path.isdir(base):
                continue
            for fn in sorted(os.listdir(base)):
                p = os.path.join(base, fn)
                if not fn.lower().endswith(".json"):
                    continue
                try:
                    txt = open(p, encoding="utf-8").read()
                except Exception:
                    continue
                if "chip_recipes_enabled" not in txt:
                    continue
                j = rj(p)
                if not isinstance(j, dict) or "result" not in j:
                    continue
                r = j["result"]
                rid = (r.get("id") or r.get("item")) if isinstance(r, dict) else r
                if not rid:
                    continue
                out.add(str(rid).split(":")[-1])
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


def manual_pages(sub):
    """{item_id: {'crafting': 该手册条目是否有 crafting 页, 'note': 是否有获取途径说明页}}"""
    base = os.path.join(ROOT, sub, "src/main/resources/assets", NS,
                        "patchouli_books/astral_guide/en_us/entries")
    res = {}
    for dp, _, fns in os.walk(base):
        for fn in fns:
            if fn.endswith(".json"):
                iid = os.path.basename(fn)[:-5]
                pages = (rj(os.path.join(dp, fn)) or {}).get("pages", [])
                res[iid] = {
                    "crafting": any("crafting" in str(p.get("type", ""))
                                    for p in pages if isinstance(p, dict)),
                    "note": any(isinstance(p, dict) and str(p.get("text", "")) == NOTE_KEY
                                for p in pages),
                }
    return res


def manual_note(sub):
    """{en_us 手册条目里带筹码获取说明页（text == astral_dice.guide.chip_source.note）的 item_id}"""
    return {iid for iid, v in manual_pages(sub).items() if v["note"]}


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

    # ---- 守卫 1：子项目数为 0 ⇒ 无法判定，绝不 vacuous PASS（对齐 ps1 的 throw + exit 2）----
    sub_list = subs()
    if not sub_list:
        print(f"\n❌ FAIL: 未发现任何目标子项目（ROOT={ROOT} 错或目录结构变化）⇒ 无法判定；不得判 PASS")
        return 2

    summary = {}
    for sub in sub_list:
        c2i, i2c = moditems(sub)
        chips = sorted(x.split(":")[-1] for x in tag_members(sub, "tags/item/chips.json"))
        # ---- 守卫 2：chips 标签成员为 0 ⇒ 无法判定，绝不 vacuous PASS ----
        if not chips:
            print(f"\n❌ FAIL: {sub}: chips 标签成员数为 0 ⇒ 无法判定"
                  f"（缺 data/*/tags/item/chips.json 或路径约定变化）；不得判 PASS")
            return 2
        rec = recipes(sub)
        tags = all_tags(sub)
        loot = loot_sources(sub)
        gives = code_gives(sub, i2c)
        reach = closure(sub, i2c, rec, tags, loot, gives)
        slot = {x.split(":")[-1] for x in tag_members(sub, "tags/item/chip.json", ns="curios")}
        pages = manual_pages(sub)
        gated = gated_recipes(sub)
        note = {iid for iid, v in pages.items() if v["note"]}
        ct = glob.glob(os.path.join(ROOT, sub, "src/main/java/**/ModCreativeTabs.java"), recursive=True)
        creative = set(re.findall(r'output\.accept\(ModItems\.([A-Z0-9_]+)\.get\(\)\)',
                                  open(ct[0], encoding="utf-8").read())) if ct else set()
        creative_ids = {c2i.get(c, c) for c in creative}

        no_rec, unreach, dangling, no_slot, no_tab = [], [], defaultdict(list), [], []
        no_page, stale_page, no_note = [], [], []
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
            has_page = pages.get(cid, {}).get("crafting") is True
            if cid in gated:
                # 配方受 chip_recipes_enabled 门禁（默认关闭）⇒ 手册不应展示 crafting 页，且必须说明获取途径
                if has_page:
                    stale_page.append(cid)
                if cid not in note:
                    no_note.append(cid)
            elif not has_page:
                no_page.append(cid)

        print(f"\n### {sub}")
        print(f"  筹码 {len(chips)} | 有配方 {len(chips)-len(no_rec)} | 传递可达 {len(chips)-len(unreach)}")
        print(f"  curios:chip 槽 {len(slot)} | 创造栏覆盖 {len(chips)-len(no_tab)}/{len(chips)}")
        for label, lst, f in (("❌ 缺配方", no_rec, True), ("❌ 传递不可达", unreach, True),
                              ("❌ 配方引用未注册物品", {k: v for k, v in dangling.items()}, True),
                              ("❌ curios:chip 槽缺项", no_slot, True),
                              ("❌ 未进创造栏", no_tab, True),
                              # 2026-10 裁决：筹码 crafting 页已统一改成 @spotlight + 获取途径说明 ⇒ no_page 属预期，保持告警，不得升致命。
                              ("⚠️ 手册未展示 crafting 页", no_page, False),
                              ("❌ 手册仍展示 crafting 页（配方默认关闭）", stale_page, True),
                              ("❌ 手册未说明筹码获取途径", no_note, True)):
            if lst:
                (fatal if f else warn).append(f"[{sub}] {label}: {lst}")
                print(f"    {label} {len(lst)}: {lst}")
        if not (no_rec or unreach or dangling or no_slot or no_tab or stale_page or no_note):
            print("    ✅ 无致命问题")
        summary[sub] = dict(chips=chips, no_rec=no_rec, unreach=unreach, no_page=no_page,
                            stale_page=stale_page, no_note=no_note)

    if len(summary) == 2:
        a, b = list(summary)
        print("\n" + "=" * 78)
        print("双版本对等性")
        print("=" * 78)
        for key in ("chips", "no_rec", "unreach", "no_page", "stale_page", "no_note"):
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
