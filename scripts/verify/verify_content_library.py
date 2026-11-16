#!/usr/bin/env python3
"""verify_content_library —— 内容库一致性校验（python 复原版，等价 scripts/devtools/旧版 verify_content_library 实现）。

对照物：docs/1.2.0-content.json（1.2.0 冻结快照）↔ 工程实际文件。

判定与退出码与 旧实现 逐条对齐：
  1) 新增物品闭集：HEAD 的 registerItem id 集合 − 68dbd59^ 的集合，必须与快照 items 的键集相等；
  2) 逐物品（双版本）：ModItems 注册 / 汇总标签 / curios 槽位 / 配方产物 / 手册条目；
  3) 计数基线：BASE_TAG_COUNTS 与 BASE_SLOT_COUNTS；
  4) 装备槽守门：汇总标签去掉空白基底（BLANK_ITEMS）后必须全部进对应 curios 槽标签；
  5) 新增效果：ModEffects 注册 + 双版本 × 中英 lang 键；
  6) 双版本对等：汇总标签 / curios 槽 / 手册条目。
输出行（[OK] / WARN / FAIL / ALL OK / !! 缺少 …）与 旧实现 同形；有偏差退出 1，全部通过退出 0。

与 旧实现 的两处**有意差异**（其余逐字对齐）：
  * 增加了 -h/--help（旧实现 只有位置参数 Root）：本仓约定每个 python 入口都必须有 --help 且退出 0。
  * --root <v> 与位置参数 Root 等价（历史 python 原件的参数是 --root；旧实现 是位置参数，两者都接）。

本脚本只读工程文件，不写任何文件；不调用任何 .旧实现 /  旧模块。
"""

import json
import os
import re
import subprocess
import sys

NS = "astral_dice"
START_COMMIT = "68dbd59"
VERS = [
    ("neoforge-1.21.1", "item", "recipe"),
    ("forge-1.20.1", "items", "recipes"),
]
SUMMARY_TAGS = ["chips", "signs", "dices", "materials"]
DICE_TIERS = ["dice_t0", "dice_t1", "dice_t2", "dice_t3", "dice_t4"]
BASE_SLOT_COUNTS = {"chip": 59, "dice": 13, "stand": 16}
BASE_TAG_COUNTS = {"chips": 60, "signs": 17, "dices": 13, "materials": 14}
BLANK_ITEMS = ["blank_chip", "blank_sign"]
# moses_ready 在 2026-09-19「立牌主动前置门控收口」中已随旧「待命等待器」一并删除；
# 现存 `msg.astral_dice.moses_ready` 是 MosesSignItem 的 actionbar 文案键，不是 MobEffect。
NEW_EFFECTS = ["charge", "empower", "weakness_reveal", "moses_broken", "pandaman_taunt"]

errors = []
warnings = []

REGISTER_RX = re.compile(r'registerItem\("(\w+)"')
EFFECT_RX = re.compile(r'EFFECTS\.register\("(\w+)"')


def out(text=""):
    """Write-Out：显式 LF，不随 os.linesep 翻译（与 Mt.Phase 的 Write-MtLine 同契约）。"""
    data = (text + chr(10)).encode("utf-8", "replace")
    buf = getattr(sys.stdout, "buffer", None)
    if buf is not None:
        buf.write(data)
        buf.flush()
    else:
        sys.stdout.write(text + chr(10))
        sys.stdout.flush()


def err(msg):
    errors.append(msg)


def warn(msg):
    warnings.append(msg)


def py_repr(value):
    """ConvertTo-PyRepr：None / True / False / str（转义反斜杠与单引号）/ dict / list。"""
    if value is None:
        return "None"
    if isinstance(value, bool):
        return "True" if value else "False"
    if isinstance(value, str):
        return "'" + value.replace("\\", "\\\\").replace("'", "\\'").replace(chr(10), "\\n").replace(chr(13), "\\r") + "'"
    if isinstance(value, dict):
        return "{" + ", ".join(py_repr(k) + ": " + py_repr(v) for k, v in value.items()) + "}"
    if isinstance(value, (list, tuple)):
        return "[" + ", ".join(py_repr(v) for v in value) + "]"
    return str(value)


def sort_ordinal(values):
    return sorted(values)


def set_equals(a, b):
    return set(a) == set(b)


def set_diff(a, b):
    return set(a) - set(b)


def rd(path):
    with open(path, "rb") as fh:
        return json.loads(fh.read().decode("utf-8-sig", "replace"))


def read_text(path):
    with open(path, "rb") as fh:
        text = fh.read().decode("utf-8-sig", "replace")
    return text.replace(chr(13) + chr(10), chr(10)).replace(chr(13), chr(10))


def git(root, args):
    try:
        proc = subprocess.run(
            ["git"] + list(args),
            cwd=root,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
        )
    except OSError:
        return ""
    if proc.returncode != 0:
        return ""
    return proc.stdout.decode("utf-8", "replace").replace(chr(13) + chr(10), chr(10))


# ── 取值（逐个对齐 旧实现 的同名函数）───────────────────────────────────────
def new_ids_from_git(root):
    rel = "neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/item/ModItems.java"
    old = git(root, ["show", START_COMMIT + "^:" + rel])
    new = git(root, ["show", "HEAD:" + rel])
    if not old.strip() or not new.strip():
        warn("git 读取失败（提交 " + START_COMMIT + " 是否可达？）")
        return set()
    return set(REGISTER_RX.findall(new)) - set(REGISTER_RX.findall(old))


def mod_items(root, sub):
    path = os.path.join(root, sub, "src/main/java/com/merlinkitsune/astral_dice/item/ModItems.java")
    if not os.path.isfile(path):
        return set()
    return set(REGISTER_RX.findall(read_text(path)))


def _tag_last_segment(values):
    out = []
    for value in values:
        out.append(str(value).split(":")[-1])
    return out


def tag_values(root, sub, tagdir, name):
    path = os.path.join(root, sub, "src/main/resources/data", NS, "tags", tagdir, name + ".json")
    if not os.path.isfile(path):
        return None
    doc = rd(path)
    values = doc.get("values") if isinstance(doc, dict) else None
    if values is None:
        values = []
    if not isinstance(values, list):
        values = [values]
    return _tag_last_segment(values)


def slot_values(root, sub, tagdir, name):
    path = os.path.join(root, sub, "src/main/resources/data/curios/tags", tagdir, name + ".json")
    if not os.path.isfile(path):
        return None
    doc = rd(path)
    values = doc.get("values") if isinstance(doc, dict) else None
    if values is None:
        values = []
    if not isinstance(values, list):
        values = [values]
    return _tag_last_segment(values)


def recipe_results(root, sub, recipedir):
    found = set()
    for area in ("src/generated/resources", "src/main/resources"):
        base = os.path.join(root, sub, area, "data", NS, recipedir)
        if not os.path.isdir(base):
            continue
        for name in os.listdir(base):
            if not name.endswith(".json"):
                continue
            path = os.path.join(base, name)
            if not os.path.isfile(path):
                continue
            doc = rd(path)
            res = doc.get("result") if isinstance(doc, dict) else None
            rid = ""
            if isinstance(res, dict):
                if res.get("id"):
                    rid = str(res["id"])
                elif res.get("item"):
                    rid = str(res["item"])
            elif res is None:
                rid = "None"
            else:
                rid = str(res)
            if rid:
                found.add(rid.split(":")[-1])
    return found


def manual_ids(root, sub):
    base = os.path.join(root, sub, "src/main/resources/assets", NS, "patchouli_books/astral_guide/en_us/entries")
    found = set()
    if not os.path.isdir(base):
        return found
    for cat in sorted(os.listdir(base)):
        cat_path = os.path.join(base, cat)
        if not os.path.isdir(cat_path):
            continue
        for name in os.listdir(cat_path):
            if name.endswith(".json"):
                found.add(name[: -len(".json")])
    return found


def mod_effects(root, sub):
    path = os.path.join(root, sub, "src/main/java/com/merlinkitsune/astral_dice/effect/ModEffects.java")
    if not os.path.isfile(path):
        return None
    return set(EFFECT_RX.findall(read_text(path)))


# ── CLI ──────────────────────────────────────────────────────────────────
def usage():
    return chr(10).join([
        "用法: python scripts/verify/verify_content_library.py [--root <目录>]",
        "",
        "内容库一致性校验：docs/1.2.0-content.json（1.2.0 冻结快照）↔ 工程实际文件。",
        "等价 scripts/devtools/旧版 verify_content_library 实现（位置参数 Root）。",
        "",
        "选项:",
        "  --root <目录>  工程根目录（默认 '.'）；也可用位置参数直接给",
        "  -h, --help     显示本帮助",
        "",
        "退出码: 0 = 全部通过 | 1 = 存在偏差（含快照缺失）",
        "",
        "说明: 本脚本对照的是 1.2.0 冻结快照，工程推进后出现偏差属预期，不参与常规门禁；",
        "      快照 docs/1.2.0-content.json 被 .gitignore 排除，干净 clone 无法运行。",
    ])


def main(argv):
    root = "."
    positional = []
    index = 0
    while index < len(argv):
        token = argv[index]
        key = token.lstrip("-").lower()
        if token.startswith("-") and key in ("h", "help"):
            out(usage())
            return 0
        if token.startswith("-") and key == "root":
            index += 1
            if index >= len(argv):
                out("错误: 缺少 --root 的值")
                return 2
            root = argv[index]
            index += 1
            continue
        if token.startswith("-") and token != "-":
            out("错误: 未知参数 " + token)
            return 2
        positional.append(token)
        index += 1
    if positional:
        if len(positional) > 1:
            out("错误: 只能给一个位置参数（Root），收到 " + py_repr(positional))
            return 2
        root = positional[0]

    out("[NOTE] 本脚本是 1.2.0 冻结期一次性验收工具，**不属常规守门清单**；")
    out("       它对照的是 1.2.0 的冻结快照，工程推进后出现偏差属预期、不代表回归（见脚本头部与 TESTING-SPEC.md §9）。")
    out("")

    snapshot = os.path.join(root, "docs/1.2.0-content.json")
    if not os.path.isfile(snapshot):
        out("!! 缺少 docs/1.2.0-content.json（内容库快照，被 .gitignore 排除，其生成脚本已于 2026-09-15 随 temp/ 清理移除）"
            " —— 请先从备份恢复该文件，详见 scripts/test/TESTING-SPEC.md §11")
        return 1

    lib = rd(snapshot)
    lib_items_doc = lib.get("items") or {}
    lib_items = set(lib_items_doc.keys())

    # ---- 1. 新增物品闭集 ----
    git_new = new_ids_from_git(root)
    if git_new:
        if not set_equals(git_new, lib_items):
            err("[闭集] git 新增 id 与内容库 items 不一致：git-only="
                + py_repr(sort_ordinal(set_diff(git_new, lib_items)))
                + " lib-only=" + py_repr(sort_ordinal(set_diff(lib_items, git_new))))
        else:
            out("[OK] 闭集：" + str(len(lib_items)) + " 个新增物品与 git 差集一致")

    # ---- 2. 逐物品（双版本）----
    for ver in VERS:
        sub, tagdir, recipedir = ver[0], ver[1], ver[2]
        reg = mod_items(root, sub)
        recipes = recipe_results(root, sub, recipedir)
        manual = manual_ids(root, sub)
        summary_maps = {}
        for tag in SUMMARY_TAGS + DICE_TIERS:
            summary_maps[tag] = tag_values(root, sub, tagdir, tag) or []
        slot_maps = {}
        for slot in BASE_SLOT_COUNTS:
            slot_maps[slot] = slot_values(root, sub, tagdir, slot) or []

        missing_reg = sort_ordinal(set_diff(lib_items, reg))
        if missing_reg:
            err("[" + sub + "] 内容库物品未在 ModItems 注册：" + py_repr(missing_reg))

        for iid in lib_items_doc:
            entry = lib_items_doc[iid] or {}
            if iid not in reg:
                continue
            want = set(str(x) for x in (entry.get("tags") or []))
            got = set()
            for tag, values in summary_maps.items():
                if iid in values:
                    got.add(tag)
            if not set_equals(want, got):
                err("[" + sub + "] " + iid + " 汇总标签不一致：内容库=" + py_repr(sort_ordinal(want))
                    + " 实际=" + py_repr(sort_ordinal(got)))
            wslot = set(str(x) for x in (entry.get("slots") or []))
            gslot = set()
            for slot, values in slot_maps.items():
                if iid in values:
                    gslot.add(slot)
            if not set_equals(wslot, gslot):
                err("[" + sub + "] " + iid + " 槽位不一致：内容库=" + py_repr(sort_ordinal(wslot))
                    + " 实际=" + py_repr(sort_ordinal(gslot)))
            if iid not in recipes:
                err("[" + sub + "] " + iid + " 无配方")
            if iid not in manual:
                err("[" + sub + "] " + iid + " 无手册条目")
    out("[OK] 逐物品检查完成：" + str(len(lib_items)) + " × " + str(len(VERS)) + " 版本（注册/标签/槽位/配方/手册）")

    # ---- 3. 计数基线 ----
    for ver in VERS:
        sub, tagdir = ver[0], ver[1]
        for tag, base_count in BASE_TAG_COUNTS.items():
            values = tag_values(root, sub, tagdir, tag)
            if values is None:
                err("[" + sub + "] 缺少汇总标签 " + tag)
            elif len(values) != base_count:
                err("[" + sub + "] 汇总标签 " + tag + " 计数 " + str(len(values)) + " ≠ 基线 " + str(base_count))
        for slot, base_count in BASE_SLOT_COUNTS.items():
            values = slot_values(root, sub, tagdir, slot)
            if values is None:
                err("[" + sub + "] 缺少 curios 槽标签 " + slot)
            elif len(values) != base_count:
                err("[" + sub + "] curios 槽 " + slot + " 计数 " + str(len(values)) + " ≠ 基线 " + str(base_count))
    out("[OK] 计数基线：汇总标签 " + py_repr(BASE_TAG_COUNTS) + " / curios 槽 " + py_repr(BASE_SLOT_COUNTS))

    # ---- 4. 装备槽守门 ----
    for ver in VERS:
        sub, tagdir = ver[0], ver[1]
        for slot, summary in (("chip", "chips"), ("stand", "signs"), ("dice", "dices")):
            sg = set(tag_values(root, sub, tagdir, summary) or [])
            sl = set(slot_values(root, sub, tagdir, slot) or [])
            for blank in BLANK_ITEMS:
                sg.discard(blank)
            gap = sort_ordinal(sg - sl)
            if gap:
                err("[" + sub + "] " + summary + " 中 " + py_repr(gap)
                    + " 未进 curios:" + slot + " 槽标签（装备槽守门）")
    out("[OK] 装备槽守门（空白基底材料已排除）")

    # ---- 5. 新增效果 ----
    for ver in VERS:
        sub = ver[0]
        eff = mod_effects(root, sub)
        if eff is None:
            warn("[" + sub + "] 未找到 ModEffects.java")
            continue
        missing = [e for e in NEW_EFFECTS if e not in eff]
        if missing:
            err("[" + sub + "] 新增效果未注册：" + py_repr(missing))
        for lang in ("zh_cn", "en_us"):
            path = os.path.join(root, sub, "src/main/resources/assets", NS, "lang", lang + ".json")
            doc = rd(path) if os.path.isfile(path) else {}
            keys = doc if isinstance(doc, dict) else {}
            nolang = [e for e in NEW_EFFECTS if ("effect." + NS + "." + e) not in keys]
            if nolang:
                err("[" + sub + "/" + lang + "] 效果缺 lang 键 effect." + NS + ".*：" + py_repr(nolang))
    out("[OK] 新增效果 " + str(len(NEW_EFFECTS)) + " 个：ModEffects 注册 + 双版本×中英 lang 键")

    # ---- 6. 双版本对等 ----
    for tag in SUMMARY_TAGS + DICE_TIERS:
        a = set(tag_values(root, VERS[0][0], VERS[0][1], tag) or [])
        b = set(tag_values(root, VERS[1][0], VERS[1][1], tag) or [])
        if not set_equals(a, b):
            err("[对等] 汇总标签 " + tag + " 双版本不一致：neo-only=" + py_repr(sort_ordinal(set_diff(a, b)))
                + " forge-only=" + py_repr(sort_ordinal(set_diff(b, a))))
    for slot in BASE_SLOT_COUNTS:
        a = set(slot_values(root, VERS[0][0], VERS[0][1], slot) or [])
        b = set(slot_values(root, VERS[1][0], VERS[1][1], slot) or [])
        if not set_equals(a, b):
            err("[对等] curios 槽 " + slot + " 双版本不一致：neo-only=" + py_repr(sort_ordinal(set_diff(a, b)))
                + " forge-only=" + py_repr(sort_ordinal(set_diff(b, a))))
    ma = manual_ids(root, VERS[0][0])
    mb = manual_ids(root, VERS[1][0])
    if not set_equals(ma, mb):
        err("[对等] 手册条目双版本不一致：neo-only=" + py_repr(sort_ordinal(set_diff(ma, mb)))
            + " forge-only=" + py_repr(sort_ordinal(set_diff(mb, ma))))
    out("[OK] 双版本对等：汇总标签 / curios 槽 / 手册条目")

    # ---- 汇总输出 ----
    out("")
    for item in warnings:
        out("WARN " + item)
    if errors:
        out("FAIL —— " + str(len(errors)) + " 项偏差：")
        for item in errors:
            out("  - " + item)
        out("")
        out("（提示：本脚本对照的是 **1.2.0 冻结快照**，工程已推进时出现偏差属预期 —— 见脚本头部与 TESTING-SPEC.md §9。")
        out("  要判定「是否真的漏登记」，须先确认内容库快照是否已按当前版本重建；本脚本不参与常规门禁。）")
        return 1
    out("ALL OK —— 内容库与工程实际一致（" + str(len(lib_items)) + " 物品 / " + str(len(NEW_EFFECTS)) + " 效果 / 双版本）")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
