# -*- coding: utf-8 -*-
"""内容库一致性校验：docs/1.2.0-content.json ↔ 工程实际文件。

独立重算工程事实（不依赖 temp/gen_content_library.py 的中间产物），逐项比对内容库：
  1. 新增物品闭集（git 差集 vs 内容库 items 键集）
  2. 逐物品：ModItems 注册 / 汇总标签 / curios 槽位 / 配方 / 手册条目（双版本）
  3. 新增效果：ModEffects 注册 + effect.<ns>.<id> lang 键（双版本 × 中英）
  4. 计数基线：chips / signs / dices / materials / curios 三槽
  5. 装备槽守门：非空白基底的装备类物品必须全部在对应 curios 槽标签中

退出码：0 = 全部通过；1 = 存在偏差。
只读操作，不修改任何工程文件。

用法: python scripts/verify/verify_content_library.py [--root .]
"""
import json, io, os, re, subprocess, sys

NS = "astral_dice"
START_COMMIT = "68dbd59"
VERS = [("neoforge-1.21.1", "item", "recipe"), ("forge-1.20.1", "items", "recipes")]
SUMMARY_TAGS = ["chips", "signs", "dices", "materials"]
DICE_TIERS = ["dice_t0", "dice_t1", "dice_t2", "dice_t3", "dice_t4"]
BASE_SLOT_COUNTS = {"chip": 59, "dice": 13, "stand": 16}
BASE_TAG_COUNTS = {"chips": 60, "signs": 17, "dices": 13, "materials": 14}
BLANK_ITEMS = {"blank_chip", "blank_sign"}
NEW_EFFECTS = ["charge", "empower", "weakness_reveal", "moses_broken", "moses_ready", "pandaman_taunt"]

errors, warnings = [], []


def err(msg):
    errors.append(msg)


def warn(msg):
    warnings.append(msg)


def rd(p):
    with io.open(p, encoding="utf-8") as f:
        return json.load(f)


def git(root, args):
    r = subprocess.run(["git"] + args, cwd=root, capture_output=True, text=True, encoding="utf-8")
    return r.stdout


def new_ids_from_git(root):
    """1.2.0 新增注册 id = HEAD 的 id 集合 − 起始提交的 id 集合。

    注意：不能按 diff 的 `+` 行统计 —— Java 常量名重写（如 LIVING_BOOK_PAGE →
    LIVING_PAGE，注册 id 不变）同样会产生 `+` 行，会误报为新增。
    """
    rel = "neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/item/ModItems.java"
    old = git(root, ["show", f"{START_COMMIT}^:{rel}"])
    new = git(root, ["show", f"HEAD:{rel}"])
    if not old.strip() or not new.strip():
        warn(f"git 读取失败（提交 {START_COMMIT} 是否可达？）")
        return set()
    pat = r'registerItem\("(\w+)"'
    return set(re.findall(pat, new)) - set(re.findall(pat, old))


def mod_items(root, sub):
    p = os.path.join(root, sub, "src/main/java/com/merlinkitsune/astral_dice/item/ModItems.java")
    return set(re.findall(r'registerItem\("(\w+)"', io.open(p, encoding="utf-8").read()))


def tag_values(root, sub, tagdir, name):
    p = os.path.join(root, sub, "src/main/resources/data", NS, "tags", tagdir, name + ".json")
    if not os.path.exists(p):
        return None
    return [v.split(":")[-1] for v in rd(p)["values"]]


def slot_values(root, sub, tagdir, name):
    p = os.path.join(root, sub, "src/main/resources/data/curios/tags", tagdir, name + ".json")
    if not os.path.exists(p):
        return None
    return [v.split(":")[-1] for v in rd(p)["values"]]


def recipe_results(root, sub, recipedir):
    out = set()
    for r in ("src/generated/resources", "src/main/resources"):
        base = os.path.join(root, sub, r, "data", NS, recipedir)
        if not os.path.isdir(base):
            continue
        for f in os.listdir(base):
            if not f.endswith(".json"):
                continue
            d = rd(os.path.join(base, f))
            res = d.get("result", {})
            rid = (res.get("id") or res.get("item") or "") if isinstance(res, dict) else str(res)
            if rid:
                out.add(rid.split(":")[-1])
    return out


def manual_ids(root, sub):
    base = os.path.join(root, sub, "src/main/resources/assets", NS,
                        "patchouli_books/astral_guide/en_us/entries")
    out = set()
    for cat in os.listdir(base):
        cdir = os.path.join(base, cat)
        if os.path.isdir(cdir):
            for f in os.listdir(cdir):
                if f.endswith(".json"):
                    out.add(f[:-5])
    return out


def mod_effects(root, sub):
    p = os.path.join(root, sub, "src/main/java/com/merlinkitsune/astral_dice/effect/ModEffects.java")
    if not os.path.exists(p):
        return None
    return set(re.findall(r'EFFECTS\.register\("(\w+)"', io.open(p, encoding="utf-8").read()))


def main():
    root = "."
    if "--root" in sys.argv:
        root = sys.argv[sys.argv.index("--root") + 1]
    out_json = os.path.join(root, "docs/1.2.0-content.json")
    if not os.path.exists(out_json):
        print("!! 缺少 docs/1.2.0-content.json，请先运行 temp/gen_content_library.py")
        return 1
    lib = rd(out_json)
    lib_items = set(lib["items"].keys())

    # ---- 1. 新增物品闭集 ----
    git_new = new_ids_from_git(root)
    if git_new:
        if git_new != lib_items:
            err(f"[闭集] git 新增 id 与内容库 items 不一致：git-only={sorted(git_new - lib_items)} lib-only={sorted(lib_items - git_new)}")
        else:
            print(f"[OK] 闭集：{len(lib_items)} 个新增物品与 git 差集一致")

    # ---- 2. 逐物品（双版本） ----
    for sub, tagdir, recipedir in VERS:
        reg = mod_items(root, sub)
        recipes = recipe_results(root, sub, recipedir)
        manual = manual_ids(root, sub)
        summary_maps = {t: (tag_values(root, sub, tagdir, t) or []) for t in SUMMARY_TAGS + DICE_TIERS}
        slot_maps = {s: (slot_values(root, sub, tagdir, s) or []) for s in BASE_SLOT_COUNTS}
        missing_reg = sorted(lib_items - reg)
        if missing_reg:
            err(f"[{sub}] 内容库物品未在 ModItems 注册：{missing_reg}")
        for iid, e in lib["items"].items():
            if iid not in reg:
                continue
            # 汇总标签
            want = set(e["tags"])
            got = {t for t, ids in summary_maps.items() if iid in ids}
            if want != got:
                err(f"[{sub}] {iid} 汇总标签不一致：内容库={sorted(want)} 实际={sorted(got)}")
            # 槽位
            wslot = set(e["slots"])
            gslot = {s for s, ids in slot_maps.items() if iid in ids}
            if wslot != gslot:
                err(f"[{sub}] {iid} 槽位不一致：内容库={sorted(wslot)} 实际={sorted(gslot)}")
            # 配方 / 手册
            if iid not in recipes:
                err(f"[{sub}] {iid} 无配方")
            if iid not in manual:
                err(f"[{sub}] {iid} 无手册条目")
    print(f"[OK] 逐物品检查完成：{len(lib_items)} × {len(VERS)} 版本（注册/标签/槽位/配方/手册）")

    # ---- 3. 计数基线 ----
    for sub, tagdir, _ in VERS:
        for t, base in BASE_TAG_COUNTS.items():
            v = tag_values(root, sub, tagdir, t)
            if v is None:
                err(f"[{sub}] 缺少汇总标签 {t}")
            elif len(v) != base:
                err(f"[{sub}] 汇总标签 {t} 计数 {len(v)} ≠ 基线 {base}")
        for s, base in BASE_SLOT_COUNTS.items():
            v = slot_values(root, sub, tagdir, s)
            if v is None:
                err(f"[{sub}] 缺少 curios 槽标签 {s}")
            elif len(v) != base:
                err(f"[{sub}] curios 槽 {s} 计数 {len(v)} ≠ 基线 {base}")
    print(f"[OK] 计数基线：汇总标签 {BASE_TAG_COUNTS} / curios 槽 {BASE_SLOT_COUNTS}")

    # ---- 4. 装备槽守门（非空白基底装备物品必须入槽） ----
    for sub, tagdir, _ in VERS:
        for slot, summary in (("chip", "chips"), ("stand", "signs"), ("dice", "dices")):
            sg = set(tag_values(root, sub, tagdir, summary) or [])
            sl = set(slot_values(root, sub, tagdir, slot) or [])
            gap = sorted((sg - BLANK_ITEMS) - sl)
            if gap:
                err(f"[{sub}] {summary} 中 {gap} 未进 curios:{slot} 槽标签（装备槽守门）")
    print("[OK] 装备槽守门（空白基底材料已排除）")

    # ---- 5. 新增效果 ----
    for sub, _, _ in VERS:
        eff = mod_effects(root, sub)
        if eff is None:
            warn(f"[{sub}] 未找到 ModEffects.java")
            continue
        miss = [e for e in NEW_EFFECTS if e not in eff]
        if miss:
            err(f"[{sub}] 新增效果未注册：{miss}")
        for lg in ("zh_cn", "en_us"):
            z = rd(os.path.join(root, sub, "src/main/resources/assets", NS, "lang", lg + ".json"))
            nolang = [e for e in NEW_EFFECTS if f"effect.{NS}.{e}" not in z]
            if nolang:
                err(f"[{sub}/{lg}] 效果缺 lang 键 effect.{NS}.*：{nolang}")
    print(f"[OK] 新增效果 {len(NEW_EFFECTS)} 个：ModEffects 注册 + 双版本×中英 lang 键")

    # ---- 6. 双版本对等（标签/槽位/手册） ----
    for t in SUMMARY_TAGS + DICE_TIERS:
        a = set(tag_values(root, VERS[0][0], VERS[0][1], t) or [])
        b = set(tag_values(root, VERS[1][0], VERS[1][1], t) or [])
        if a != b:
            err(f"[对等] 汇总标签 {t} 双版本不一致：neo-only={sorted(a-b)} forge-only={sorted(b-a)}")
    for s in BASE_SLOT_COUNTS:
        a = set(slot_values(root, VERS[0][0], VERS[0][1], s) or [])
        b = set(slot_values(root, VERS[1][0], VERS[1][1], s) or [])
        if a != b:
            err(f"[对等] curios 槽 {s} 双版本不一致：neo-only={sorted(a-b)} forge-only={sorted(b-a)}")
    ma, mb = manual_ids(root, VERS[0][0]), manual_ids(root, VERS[1][0])
    if ma != mb:
        err(f"[对等] 手册条目双版本不一致：neo-only={sorted(ma-mb)} forge-only={sorted(mb-ma)}")
    print("[OK] 双版本对等：汇总标签 / curios 槽 / 手册条目")

    # ---- 汇总输出 ----
    print()
    for w in warnings:
        print("WARN", w)
    if errors:
        print(f"FAIL —— {len(errors)} 项偏差：")
        for e in errors:
            print("  -", e)
        return 1
    print(f"ALL OK —— 内容库与工程实际一致（{len(lib_items)} 物品 / {len(NEW_EFFECTS)} 效果 / 双版本）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
