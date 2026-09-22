#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
StarEngine-Lib 搬迁前置校验(干跑)

对 plan_migration.py 判定的 48 个"可搬迁单元"逐个做四项体检:
  A. 是否引用 AstralDiceMod(消费方 mod 主类) — 搬走后必须改指向库自己的 MODID
  B. 是否引用了"非孤岛"的 astral_dice 类(import 或全限定名) — 命中即不可搬
  C. 是否出现 astral_dice 命名空间字面量(存档/资源归属) — 命中须逐条判断
  D. 是否引用平台专有类型(CuriosCompat/ItemDataKey/AstralData/ModNetwork/ActionBarPayload)

用法: python tools/preflight_lib_migration.py
"""
import os
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PKG = 'com/merlinkitsune/astral_dice'
NEODIR = ROOT / 'neoforge-1.21.1' / 'src' / 'main' / 'java' / PKG
FORGEDIR = ROOT / 'forge-1.20.1' / 'src' / 'main' / 'java' / PKG

# ---- 分类清单(与 plan_migration.py 输出一致) ----
COMMON = """client/ClientDamageNumbers
combat/AttackPowerModifier
combat/DefensePowerModifier
combat/SpellDamageModifier
component/GameplayConstants
effect/BerserkEffect
effect/CounterEffect
effect/CutterReadyEffect
effect/DiceBlessingEffect
effect/FateGuidanceEffect
effect/HealingEffect
effect/InvestigationBonusEffect
effect/KingPowerEffect
effect/LivingPageEffect
effect/MarkedEffect
effect/MisakiBurstEffect
effect/MosesBrokenEffect
effect/NancyLuHackEffect
effect/PandamanTauntEffect
effect/PaparaBiteEffect
effect/RevengeHalberdEffect
effect/UndercoverInvestigationEffect
effect/WeakMarkEffect
event/AmethystDiceHandler
event/AstralEventType
event/EventContext
event/EventEffect
event/EventTargetCollector
item/card/ChocolateCakeCardItem
item/card/EffectCardUtil
item/card/HamburgerCardItem
item/card/YouHaveIHaveCardItem
item/chip/PiercingGunChipItem
target/TargetSelectionAction
target/TargetSelectionRegistry
target/TargetType""".split()

COMMON_FIX = """event/SignActiveTriggeredEvent
item/BossEntityUtil""".split()

SPLIT = """client/ActionBarManager
client/TargetSelectOverlay
component/WeaponEnhancement
config/ModCommonConfig
item/chip/MotoHelmetChipItem
item/chip/SandwichChipItem
item/chip/SpeedSkatesChipItem""".split()

PLATFORM_FORGE = """component/AstralData
component/ItemDataKey
item/CuriosCompat""".split()

ISLAND = COMMON + COMMON_FIX + SPLIT + PLATFORM_FORGE
ISLAND_SET = set(ISLAND)

PLATFORM_TYPES = ['CuriosCompat', 'ItemDataKey', 'AstralData', 'ModNetwork', 'ActionBarPayload',
                  'ModClientEvents', 'TargetSelectPayloadShim']

FQ_RE = re.compile(r'com\.merlinkitsune\.astral_dice\.')
IMPORT_RE = re.compile(r'^\s*import\s+(static\s+)?com\.merlinkitsune\.astral_dice\.([A-Za-z0-9_.$]+)\s*;',
                       re.MULTILINE)


def cls_index(d: Path):
    """返回 {类全路径(相对 PKG, 去 .java): Path} 与 {简单类名: [相对路径...]}"""
    by_path, by_simple = {}, {}
    if not d.exists():
        return by_path, by_simple
    for p in d.rglob('*.java'):
        rel = p.relative_to(d).as_posix()[:-len('.java')]
        by_path[rel] = p
        by_simple.setdefault(rel.rsplit('/', 1)[-1], []).append(rel)
    return by_path, by_simple


def simple_of(rel):
    return rel.rsplit('/', 1)[-1]


def check(tag, tree, idx_path, idx_simple):
    print(f'\n{"=" * 96}\n[{tag}] tree={tree}\n{"=" * 96}')
    hits_total = 0
    for rel in ISLAND:
        p = idx_path.get(rel)
        if p is None:
            print(f'  [缺失] {rel}  — 该侧不存在此文件')
            continue
        src = p.read_text(encoding='utf-8', errors='replace')
        issues = []

        # A. AstralDiceMod
        if re.search(r'\bAstralDiceMod\b', src):
            lines = [i + 1 for i, l in enumerate(src.splitlines()) if 'AstralDiceMod' in l]
            issues.append(('A', f'引用 AstralDiceMod (行 {lines[:6]})'))

        # B. 引用非孤岛 astral_dice 类
        for m in IMPORT_RE.finditer(src):
            target = m.group(2)
            if '$' in target:
                target = target.split('$')[0]
            head = target.split('.')[0]
            # 简化:import 的最后一个标识符即类名
            parts = target.split('.')
            cls = parts[-1]
            # 判断是否是"包路径+类名"形式, 取前缀类名
            cand = None
            for i in range(len(parts)):
                if parts[i][:1].isupper():
                    cand = '/'.join(parts[:i + 1])
                    break
            if cand is None:
                cand = target.replace('.', '/')
            if cand not in ISLAND_SET:
                issues.append(('B', f'import 非孤岛类: {target}'))

        # C. 命名空间字面量
        for m in re.finditer(r'"([a-z0-9_.]*astral_dice[a-z0-9_.:/]*)"', src):
            issues.append(('C', f'命名空间字面量: "{m.group(1)}"'))

        # D. 平台专有类型
        for t in PLATFORM_TYPES:
            if t == simple_of(rel):
                continue
            if re.search(r'\b' + re.escape(t) + r'\b', src):
                issues.append(('D', f'引用平台专有类型: {t}'))

        if issues:
            hits_total += 1
            print(f'  [{rel}]')
            for k, msg in issues:
                print(f'      {k}: {msg}')
    print(f'\n>>> {tag} 命中文件数: {hits_total}/{len(ISLAND)}')
    return hits_total


def main():
    print('=' * 96)
    print('StarEngine-Lib 搬迁前置校验')
    print('=' * 96)
    print(f'孤岛单元总数: {len(ISLAND)}  (COMMON={len(COMMON)} COMMON_FIX={len(COMMON_FIX)} '
          f'SPLIT={len(SPLIT)} PLATFORM_forge={len(PLATFORM_FORGE)})')

    np_, ns_ = cls_index(NEODIR)
    fp_, fs_ = cls_index(FORGEDIR)
    n = check('NeoForge 1.21.1', NEODIR, np_, ns_)
    f = check('Forge 1.20.1', FORGEDIR, fp_, fs_)

    print('\n' + '=' * 96)
    print(f'汇总: NeoForge 命中 {n} 个, Forge 命中 {f} 个')
    print('=' * 96)


if __name__ == '__main__':
    main()
