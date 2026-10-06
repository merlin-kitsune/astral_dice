#!/usr/bin/env python3
"""生成 StarEngine-Lib 的迁移计划(干跑,不改动任何文件)。

对 45 个可搬迁孤岛文件逐一判定:
  COMMON    两侧逐字节相同,直接进 common/
  COMMON_FIX 两侧仅样板差异,归一化后进 common/,但需确认差异可被 shim 吸收
  SPLIT     两侧有实质差异,需拆成 common 接口 + 平台实现
  PLATFORM  仅单侧存在(平台 shim),进对应平台目录

并对每个文件检测是否依赖 Forge/NeoForge 专有类型,给出处置建议。
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

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
A = os.path.join(ROOT, 'neoforge-1.21.1')
B = os.path.join(ROOT, 'forge-1.20.1')
PKG = 'com.merlinkitsune.astral_dice'

# 孤岛清单(来自 tools/analyze_movable_island.py 实测输出)
ISLAND = """
client/ActionBarManager
client/ClientDamageNumbers
client/TargetSelectOverlay
combat/AttackPowerModifier
combat/DefensePowerModifier
combat/SpellDamageModifier
component/AstralData
component/GameplayConstants
component/ItemDataKey
component/WeaponEnhancement
config/ModCommonConfig
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
item/CuriosCompat
item/card/ChocolateCakeCardItem
item/card/EffectCardUtil
item/card/HamburgerCardItem
item/card/YouHaveIHaveCardItem
item/chip/MotoHelmetChipItem
item/chip/PiercingGunChipItem
item/chip/SandwichChipItem
item/chip/SpeedSkatesChipItem
target/TargetSelectionAction
target/TargetSelectionRegistry
target/TargetType
""".split()

# 平台专有类型 → 出现即需特殊处置
PLATFORM_TYPES = {
    'CuriosCompat': 'Forge 专有 shim(Neo 侧直调 CuriosApi)',
    'ItemDataKey': 'Forge 专有 shim(Neo 侧为 DataComponentType)',
    'AstralData': 'Forge 专有 shim(Neo 侧为 AttachmentType)',
    'ModNetwork': 'Forge 专有网络层',
    'ActionBarPayload': 'Neo 专有网络载荷',
}

IMPORT_RE = re.compile(r'^\s*import\s+(?:static\s+)?([\w.]+)\s*;', re.M)
REL_IMPORT_RE = re.compile(r'^\s*import\s+(?:static\s+)?(' + PKG.replace('.', r'\.') +
                           r'\.([\w.]+))\s*;', re.M)


def read(base, rel):
    p = os.path.join(base, 'src', 'main', 'java', PKG.replace('.', '/'), rel + '.java')
    if not os.path.exists(p):
        return None
    with open(p, encoding='utf-8', errors='replace') as f:
        return f.read()


def fam(src):
    out = set()
    for m in IMPORT_RE.finditer(src):
        fq = m.group(1)
        if fq.startswith('net.minecraftforge.'):
            out.add('FORGE')
        elif fq.startswith('net.neoforged.'):
            out.add('NEO')
        elif fq.startswith('net.minecraft.'):
            out.add('MC')
    return out


def norm(src):
    s = re.sub(r'^\s*import\s+(?:static\s+)?(?:net\.minecraftforge|net\.neoforged)[\w.]*\s*;\s*$',
               '', src, flags=re.M)
    s = s.replace('@Mod.EventBusSubscriber', '@EventBusSubscriber')
    s = s.replace('LivingDamageEvent.Pre', 'LivingDamageEvent')
    s = s.replace('PlayerTickEvent.Post', 'PlayerTickEvent')
    s = re.sub(r'\n{2,}', '\n', s)
    return s.strip()


def main():
    rows = []
    for rel in ISLAND:
        sa, sb = read(A, rel), read(B, rel)
        if sa is not None and sb is not None:
            kind = 'COMMON' if sa == sb else ('COMMON_FIX' if norm(sa) == norm(sb) else 'SPLIT')
        elif sa is not None:
            kind = 'PLATFORM_neo'
        else:
            kind = 'PLATFORM_forge'
        src = sa or sb
        touched = sorted({t for t in PLATFORM_TYPES if re.search(r'\b' + t + r'\b', src)})
        fams = sorted(fam(src))
        rows.append((kind, rel, fams, touched))

    order = {'COMMON': 0, 'COMMON_FIX': 1, 'SPLIT': 2, 'PLATFORM_forge': 3, 'PLATFORM_neo': 4}
    rows.sort(key=lambda r: (order[r[0]], r[1]))

    print('=' * 96)
    print('StarEngine-Lib 迁移计划(干跑)')
    print('=' * 96)
    print(f'{"类别":<16}{"平台":<12}{"文件":<42}依赖的平台专有类型')
    for kind, rel, fams, touched in rows:
        dep = ', '.join(touched) if touched else '-'
        print(f'{kind:<16}{"+".join(fams):<12}{rel:<42}{dep}')

    print()
    print('=' * 96)
    print('分类汇总')
    print('=' * 96)
    from collections import Counter
    c = Counter(r[0] for r in rows)
    for k in ('COMMON', 'COMMON_FIX', 'SPLIT', 'PLATFORM_forge', 'PLATFORM_neo'):
        if c[k]:
            print(f'  {k:<16}{c[k]:>4}')

    print()
    print('--- 可直接进 common/ ---')
    for kind, rel, _, touched in rows:
        if kind == 'COMMON' and not touched:
            print(f'  {rel}')
    print()
    print('--- 进 common/ 但需先摘除平台专有依赖 ---')
    for kind, rel, _, touched in rows:
        if kind in ('COMMON', 'COMMON_FIX') and touched:
            print(f'  {rel}  ← {", ".join(touched)}')
    print()
    print('--- 需拆分为 common + 平台实现 ---')
    for kind, rel, _, touched in rows:
        if kind == 'SPLIT':
            print(f'  {rel}  {("← " + ", ".join(touched)) if touched else ""}')
    print()
    print('--- 平台专有(shim),进对应平台目录 ---')
    for kind, rel, _, touched in rows:
        if kind.startswith('PLATFORM'):
            print(f'  {rel}  [{kind}]')


if __name__ == '__main__':
    sys.exit(main())
