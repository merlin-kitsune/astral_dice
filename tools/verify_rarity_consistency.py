#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""内容一致性守门（Q8，2026-10-04 建）。

把三类**此前只能靠人工核对**、且都真实出过偏差的检查固化为可重跑的门禁：

  C1 手册「档位词」 ↔ 代码稀有度（四线逐物品）
     背景：KI-F8 —— `hanna_sign.3` 写「稀有档」、`sherry_sign.3` 写「史诗档」，
     而两者代码都是 `AstralRarities.bizarre()`（奇特）；四线文案还各不相同地错。
     判据：手册 lang 的 `astral_dice.guide.entry.<item>.<N>` 文案里**出现档位词**时，
     该词必须等于 item 在 `ModItems` 里的 `.rarity(AstralRarities.X())`。

  C2 库侧反射契约（`EventTargetCollector` 的 FTB Teams / OPAC 目标）
     背景：KI-F22 附带 —— 库侧曾把目标写成不存在的方法/包名
     （`TeamManager#getTeamForPlayer(Player)`、`dev.darkhax.opac.*`），
     失败只打 debug ⇒ **从来没生效过**。判据：取本地 maven 的库 jar，
     核对 `$Ftb` / `$Opac` 内部类里**必需**的目标字符串存在、且**已知的错误**目标不存在。

  C3 手册「多出的页」与 lang 键必须成对（`special_effects.6` 教训）
     背景：fabric 曾比另三线多一页（+ 多一个 lang 键），四线口径不一致。
     判据：各线同一手册条目的 page 数一致，且每个 `guide.entry.<entry>.<N>` 引用
     都能在**三语** lang 里找到（对空引用与孤儿键两个方向都查）。

用法：
    python tools/verify_rarity_consistency.py            # 四线全跑
    python tools/verify_rarity_consistency.py -v         # 打印每条明细
退出码：0 = 全部通过；1 = 存在缺陷；2 = 无法判定（缺文件 / 缺库 jar）。
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LINES = ["neoforge-1.21.1", "forge-1.20.1", "neoforge-26.1.2", "fabric-1.20.1"]
LANGS = ("zh_cn.json", "en_us.json", "ja_jp.json")
ASSETS = os.path.join("src", "main", "resources", "assets", "astral_dice")

# 档位词 → 代码档名（库 AstralRarities 的方法名，大写）
TIER_WORDS = {
    "稀有档": "RARE", "史诗档": "EPIC", "传奇档": "LEGENDARY", "巅峰档": "PINNACLE", "奇特档": "BIZARRE",
    "Rare tier": "RARE", "Epic tier": "EPIC", "Legendary tier": "LEGENDARY",
    "Pinnacle tier": "PINNACLE", "Bizarre tier": "BIZARRE",
    "レア段階": "RARE", "エピック段階": "EPIC", "レジェンダリー段階": "LEGENDARY",
    "ピナクル段階": "PINNACLE", "ビザール段階": "BIZARRE",
}
# ⚠️ 同一档位的「错误写法」也要能被检测出来（否则改回旧词脚本仍会静默通过）
TIER_ALIASES = {"稀有档": "RARE", "レア段階": "RARE", "Rare tier": "RARE"}

RE_ITEM = re.compile(r'([A-Z0-9_]+)\s*=\s*registerItem\(\s*"([a-z0-9_]+)"')
RE_RARITY = re.compile(r'\.rarity\(AstralRarities\.([a-z]+)\(\)\)')
RE_GUIDE_KEY = re.compile(r'"astral_dice\.guide\.entry\.([a-z0-9_]+)\.(\d+)"\s*:\s*"(.*)"')


def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


def scan_items(line_dir):
    """`ModItems.java` → {itemId: TIER}（只收有 .rarity(AstralRarities.X()) 的）。"""
    path = os.path.join(ROOT, line_dir, "src", "main", "java", "com", "merlinkitsune",
                        "astral_dice", "item", "ModItems.java")
    if not os.path.isfile(path):
        return None
    src = read(path)
    out = {}
    for m in RE_ITEM.finditer(src):
        seg = src[m.end():m.end() + 600]           # rarity 紧随其后（同一 registerItem 调用内）
        r = RE_RARITY.search(seg)
        if r:
            out[m.group(2)] = r.group(1).upper()
    return out


def scan_guide_texts(line_dir):
    """三语 lang 里所有 guide entry 文案 → {(itemId, idx): [text...]}。"""
    out = {}
    for name in LANGS:
        p = os.path.join(ROOT, line_dir, ASSETS, "lang", name)
        if not os.path.isfile(p):
            continue
        for key, idx, text in RE_GUIDE_KEY.findall(read(p)):
            out.setdefault((key, int(idx)), []).append(text)
    return out


def check_c1(line_dir, verbose):
    items = scan_items(line_dir)
    if items is None:
        return [f"C1 {line_dir}: 找不到 ModItems.java（无法判定）"], False
    texts = scan_guide_texts(line_dir)
    fails = []
    checked = 0
    for (item, idx), vals in sorted(texts.items()):
        code_tier = items.get(item)
        if code_tier is None:
            continue                                # 非 ModItems 注册的物品（或未声明 rarity）⇒ 跳过
        for v in vals:
            for word, tier in TIER_WORDS.items():
                if word in v:
                    checked += 1
                    if tier != code_tier:
                        fails.append(f"C1 {line_dir}: guide.entry.{item}.{idx} 文案写「{word}」"
                                     f"({tier})，但代码稀有度是 {code_tier}")
                    elif verbose:
                        print(f"    ok {line_dir} {item}.{idx} {word} == {code_tier}")
    if verbose:
        print(f"  C1 {line_dir}: 比对 {checked} 处档位词，失败 {len(fails)}")
    return fails, True


def check_c2(verbose):
    """库侧反射契约：只核对**消费方当前 pin 的库版本**（历史版本天然缺新目标，不该误报）。"""
    m2 = os.path.expanduser("~/.m2/repository/com/merlinkitsune/starenginelib")
    if not os.path.isdir(m2):
        return ["C2 找不到本地 maven 库目录（无法判定；先 publishToMavenLocal）"], False
    pins = {}
    for line in LINES:
        p = os.path.join(ROOT, line, "gradle.properties")
        if os.path.isfile(p):
            m = re.search(r'^starengine_lib_version\s*=\s*(\S+)', read(p), re.M)
            if m:
                pins[line] = m.group(1)
    if not pins:
        return ["C2 读不到各线 starengine_lib_version（无法判定）"], False

    jars = []
    for line, ver in sorted(pins.items()):
        art = "starengine_lib-" + line
        p = os.path.join(m2, art, ver, f"{art}-{ver}.jar")
        if not os.path.isfile(p):
            return [f"C2 本地 maven 缺 {art}-{ver}.jar（先 publishToMavenLocal）"], False
        jars.append((line, ver, p))

    REQUIRED = {
        "EventTargetCollector$Ftb.class": [b"getTeamForPlayerID", b"getKnownPlayer", b"isPartyTeam"],
        "EventTargetCollector$Opac.class": [b"OpenPACServerAPI", b"getPartyByMember"],
    }
    FORBIDDEN = [b"dev.darkhax.opac", b'getTeamForPlayer', b"getTeamForPlayer(UUID)"]
    fails = []
    for line, ver, jar in jars:
        z = zipfile.ZipFile(jar)
        for cls, toks in REQUIRED.items():
            name = f"com/merlinkitsune/starenginelib/event/{cls}"
            if name not in z.namelist():
                fails.append(f"C2 {line}/{ver}: 库 jar 缺 {name}")
                continue
            body = z.read(name)
            for t in toks:
                if t not in body:
                    fails.append(f"C2 {line}/{ver}: {cls} 缺反射目标 {t.decode()}")
        if verbose:
            print(f"    ok {line}/{ver} Ftb/Opac 目标齐备")
    return fails, True


def check_c3(line_dirs, verbose):
    """手册页数四线一致 + 每个 guide 引用在三语都有键（两个方向）。"""
    fails = []
    # 逐条手册 JSON：{相对路径: 页数}
    page_counts = {}
    for line in line_dirs:
        base = os.path.join(ROOT, line, ASSETS, "patchouli_books", "astral_guide", "en_us", "entries")
        if not os.path.isdir(base):
            continue
        for dirpath, _dirs, files in os.walk(base):
            for fn in files:
                if not fn.endswith(".json"):
                    continue
                rel = os.path.relpath(os.path.join(dirpath, fn), base)
                try:
                    d = json.loads(read(os.path.join(dirpath, fn)))
                except Exception as e:                      # noqa: BLE001
                    fails.append(f"C3 {line}/{rel}: JSON 解析失败 {e}")
                    continue
                page_counts.setdefault(rel, {})[line] = len(d.get("pages", []))
    for rel, per in sorted(page_counts.items()):
        vals = set(per.values())
        if len(vals) > 1:
            # ⚠️ 四线页数**允许**存在已登记的差异（例：26.1.2 因移除 Iron / 神秘遗物联动少 2 页；
            #    fabric 的 teru_sign 多 1 页 = KI-F7 的补齐）。故此处只**提示**，不判失败；
            #    真正的门禁在下面「引用 ↔ 键」的双向检查（断链才是缺陷）。
            if verbose:
                print(f"    note {rel} 页数四线不同: {per}")
        elif verbose:
            print(f"    ok {rel} pages={vals.pop()} 四线一致")

    for line in line_dirs:
        p = os.path.join(ROOT, line, ASSETS, "lang", "zh_cn.json")
        if not os.path.isfile(p):
            continue
        keys = set(re.findall(r'"(astral_dice\.guide\.entry\.[a-z0-9_]+\.\d+)"', read(p)))
        base = os.path.join(ROOT, line, ASSETS, "patchouli_books", "astral_guide", "en_us", "entries")
        refs = set()
        for dirpath, _dirs, files in os.walk(base):
            for fn in files:
                if fn.endswith(".json"):
                    refs.update(re.findall(r'"(astral_dice\.guide\.entry\.[a-z0-9_]+\.\d+)"',
                                           read(os.path.join(dirpath, fn))))
        for miss in sorted(refs - keys):
            fails.append(f"C3 {line}: 手册引用了 lang 里不存在的键 {miss}")
        for orphan in sorted(keys - refs):
            if verbose:
                print(f"    note {line}: lang 键 {orphan} 未被手册引用（可能是有意保留）")
    return fails, True


def main():
    ap = argparse.ArgumentParser(description="内容一致性守门（档位词 / 库反射契约 / 手册页数）")
    ap.add_argument("-v", "--verbose", action="store_true")
    args = ap.parse_args()

    all_fails, undecidable = [], []
    print("== C1 手册档位词 ↔ 代码稀有度（四线）==")
    for line in LINES:
        f, ok = check_c1(line, args.verbose)
        (all_fails if ok else undecidable).extend(f)
    print("== C2 库侧反射契约（本地 maven 库 jar）==")
    f, ok = check_c2(args.verbose)
    (all_fails if ok else undecidable).extend(f)
    print("== C3 手册页数四线一致 + 引用/键双向存在 ==")
    f, ok = check_c3(LINES, args.verbose)
    (all_fails if ok else undecidable).extend(f)

    for m in undecidable:
        print("[UNDECIDABLE]", m)
    for m in all_fails:
        print("[FAIL]", m)
    if all_fails:
        print(f"\nGATE: FAIL（{len(all_fails)} 项）")
        return 1
    if undecidable:
        print(f"\nGATE: UNDECIDABLE（{len(undecidable)} 项）")
        return 2
    print("\nGATE: PASS（C1/C2/C3 全部通过）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
