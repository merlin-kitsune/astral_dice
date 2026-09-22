#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
StarEngine-Lib 孤岛重算(严格阻断项)

背景: 早先 analyze_movable_island.py 只把 ModItems / AstralDiceMod / ModAttachments /
ModEffects / ModDataComponents 当阻断项, 漏了 ModCapabilities 等"注册所有者", 导致
component/AstralData 被误判为可搬。本脚本用"谁持有注册/谁是入口"的严格定义重算。

阻断项(Blocker)定义 —— 命中任一即为阻断, 其自身与任何依赖它的类都不可进库:
  B1  文件内含 DeferredRegister
  B2  文件内含 RegisterCapabilitiesEvent / CapabilityManager / registerConfig
  B3  文件内含 @Mod( (加载器入口)
  B4  文件内含 RegisterEvent (NeoForge 数据包注册事件)
  B5  文件位于 init/ 包(注册集中地)
  B6  文件内含 @Mixin (Mixin 配置随平台, 不能进 common)
  B7  文件定义 Capability<...> 常量数组 / AttachCapabilitiesEvent

孤岛 = { 该类自身的传递闭包 ∪ 自身 } ∩ 阻断项 = ∅

用法: python tools/recompute_island_strict.py
"""
import re
import sys
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PKG_REL = 'com/merlinkitsune/astral_dice'
TREES = {
    'neo': ROOT / 'neoforge-1.21.1' / 'src' / 'main' / 'java' / PKG_REL,
    'forge': ROOT / 'forge-1.20.1' / 'src' / 'main' / 'java' / PKG_REL,
}

BLOCKER_RULES = [
    ('B1', re.compile(r'\bDeferredRegister\b')),
    ('B2', re.compile(r'RegisterCapabilitiesEvent|CapabilityManager|registerConfig')),
    ('B3', re.compile(r'@Mod\s*\(')),
    ('B4', re.compile(r'\bRegisterEvent\b')),
    ('B5', None),  # 包路径规则, 单独处理
    ('B6', re.compile(r'@Mixin\b')),
    ('B7', re.compile(r'AttachCapabilitiesEvent|Capability<[^>]*>\s+[A-Z_]+\s*=')),
]

FQN_RE = re.compile(r'com\.merlinkitsune\.astral_dice\.([A-Za-z0-9_.$]+)')


def strip_comments(src: str) -> str:
    """剥离 // 与 /* */ 注释, 但完整保留字符串字面量。
    目的: javadoc 里的 {@link X} 不是真实编译依赖, 不应参与闭包计算。"""
    out = []
    i, n = 0, len(src)
    state = 'code'          # code | line | block | str | chr
    while i < n:
        c = src[i]
        nxt = src[i + 1] if i + 1 < n else ''
        if state == 'code':
            if c == '/' and nxt == '/':
                state = 'line'; i += 2; continue
            if c == '/' and nxt == '*':
                state = 'block'; i += 2; continue
            if c == '"':
                state = 'str'; out.append(c); i += 1; continue
            if c == "'":
                state = 'chr'; out.append(c); i += 1; continue
            out.append(c); i += 1; continue
        if state == 'line':
            if c == '\n':
                state = 'code'; out.append(c)
            i += 1; continue
        if state == 'block':
            if c == '*' and nxt == '/':
                state = 'code'; i += 2; continue
            if c == '\n':
                out.append('\n')       # 保行号
            i += 1; continue
        if state == 'str':
            out.append(c)
            if c == '\\':
                if i + 1 < n:
                    out.append(src[i + 1])
                i += 2; continue
            if c == '"':
                state = 'code'
            i += 1; continue
        if state == 'chr':
            out.append(c)
            if c == '\\':
                if i + 1 < n:
                    out.append(src[i + 1])
                i += 2; continue
            if c == "'":
                state = 'code'
            i += 1; continue
    return ''.join(out)


def load(tree: Path):
    files = {}
    if not tree.exists():
        return files
    for p in tree.rglob('*.java'):
        rel = p.relative_to(tree).as_posix()[:-len('.java')]
        files[rel] = p.read_text(encoding='utf-8', errors='replace')
    return files


def blockers_of(rel, src):
    hits = []
    for tag, rx in BLOCKER_RULES:
        if rx is None:
            if rel.startswith('init/') or '/init/' in rel:
                hits.append(tag)
            continue
        if rx.search(src):
            hits.append(tag)
    return hits


def resolve_fqn(raw, known):
    """把 com.merlinkitsune.astral_dice.<raw> 解析为已知 rel(去嵌套类)"""
    parts = raw.split('.')
    for i in range(len(parts), 0, -1):
        cand = '/'.join(parts[:i])
        if cand in known:
            return cand
    return None


def build(neo, forge):
    known = set(neo) | set(forge)
    # 同包简单名索引
    pkg_simple = defaultdict(set)
    for rel in known:
        pkg, _, simple = rel.rpartition('/')
        pkg_simple[pkg].add(rel)
    meta = {}
    for rel in known:
        raw = neo.get(rel) or forge.get(rel)
        src = strip_comments(raw)
        deps = set()
        for m in FQN_RE.finditer(src):
            r = resolve_fqn(m.group(1), known)
            if r:
                deps.add(r)
        pkg, _, simple = rel.rpartition('/')
        for other in pkg_simple.get(pkg, ()):
            if other == rel:
                continue
            osimple = other.rpartition('/')[2]
            if re.search(r'\b' + re.escape(osimple) + r'\b', src):
                deps.add(other)
        deps.discard(rel)
        meta[rel] = {
            'deps': deps,
            'blockers': blockers_of(rel, src),
            'neo': rel in neo,
            'forge': rel in forge,
            'same': rel in neo and rel in forge and neo[rel] == forge[rel],
        }
    return known, meta


def closure(rel, meta):
    seen, stack = set(), [rel]
    while stack:
        cur = stack.pop()
        if cur in seen or cur not in meta:
            continue
        seen.add(cur)
        stack.extend(meta[cur]['deps'])
    return seen


def main():
    neo = load(TREES['neo'])
    forge = load(TREES['forge'])
    print(f'载入: neo={len(neo)} forge={len(forge)} 个类文件')
    known, meta = build(neo, forge)

    blocker_set = {r for r in known if meta[r]['blockers']}
    print(f'\n阻断项类: {len(blocker_set)} 个')
    for r in sorted(blocker_set):
        print(f'    {r}   {meta[r]["blockers"]}')

    print('\n' + '=' * 100)
    print('严格孤岛(自身传递闭包不触碰任何阻断项)')
    print('=' * 100)
    island = []
    for rel in sorted(known):
        cl = closure(rel, meta)
        if cl & blocker_set:
            continue
        island.append(rel)

    same = [r for r in island if meta[r]['same']]
    only_neo = [r for r in island if meta[r]['neo'] and not meta[r]['forge']]
    only_forge = [r for r in island if meta[r]['forge'] and not meta[r]['neo']]
    cross = [r for r in island if meta[r]['neo'] and meta[r]['forge']]

    print(f'\n严格孤岛总数: {len(island)}')
    print(f'  其中 两侧字节相同 : {len(same)}')
    print(f'  其中 两侧都有但内容不同 : {len(cross) - len(same)}')
    print(f'  其中 仅 NeoForge 侧 : {len(only_neo)}')
    print(f'  其中 仅 Forge 侧   : {len(only_forge)}')

    print('\n--- 严格孤岛清单 ---')
    for r in island:
        f1 = 'N' if meta[r]['neo'] else '-'
        f2 = 'F' if meta[r]['forge'] else '-'
        s = '=' if meta[r]['same'] else '~'
        print(f'  [{f1}{f2}{s}] {r}')

    # 与旧分类对照
    OLD = set("""client/ClientDamageNumbers
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
event/SignActiveTriggeredEvent
item/BossEntityUtil
item/card/ChocolateCakeCardItem
item/card/EffectCardUtil
item/card/HamburgerCardItem
item/card/YouHaveIHaveCardItem
item/chip/PiercingGunChipItem
client/ActionBarManager
client/TargetSelectOverlay
component/WeaponEnhancement
config/ModCommonConfig
item/chip/MotoHelmetChipItem
item/chip/SandwichChipItem
item/chip/SpeedSkatesChipItem
component/AstralData
component/ItemDataKey
item/CuriosCompat""".split())

    new = set(island)
    print('\n' + '=' * 100)
    print('与旧分类对照')
    print('=' * 100)
    print(f'\n旧分类判定为可搬、严格规则下【不可搬】(必须剔除):')
    for r in sorted(OLD - new):
        reasons = sorted({b for other in sorted(closure(r, meta) & blocker_set)
                          for b in meta[other]['blockers']})
        via = sorted(closure(r, meta) & blocker_set)[:4]
        print(f'  ! {r}')
        print(f'      阻断路径: {via}  ({reasons})')

    print(f'\n严格规则下可搬、旧分类【漏掉】的:')
    for r in sorted(new - OLD):
        print(f'  + {r}')

    # 输出给后续脚本用的最终清单文件
    out = ROOT / 'docs' / 'starengine-lib' / 'island-strict.txt'
    out.parent.mkdir(parents=True, exist_ok=True)
    with out.open('w', encoding='utf-8') as f:
        f.write('# 严格孤岛清单  (N=有Neo侧 F=有Forge侧 ==两侧字节相同 ~=两侧内容不同)\n')
        for r in island:
            f1 = 'N' if meta[r]['neo'] else '-'
            f2 = 'F' if meta[r]['forge'] else '-'
            s = '=' if meta[r]['same'] else '~'
            f.write(f'{f1}{f2}{s} {r}\n')
    print(f'\n已写出: {out}')


if __name__ == '__main__':
    main()
