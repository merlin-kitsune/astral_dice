#!/usr/bin/env python3
"""计算「引擎层候选」的依赖闭包 —— 搬进库必须连带搬走哪些文件。

以 1.21.1 侧为准遍历(两侧同名文件视为同一单元)。
闭包中若出现"注册表/根基类"(ModItems 等),说明该种子集合无法独立搬出,
必须先做注册表反置。
"""
import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
A = os.path.join(ROOT, 'neoforge-1.21.1')
B = os.path.join(ROOT, 'forge-1.20.1')
PKG = 'com.merlinkitsune.astral_dice'

IMPORT_RE = re.compile(r'^\s*import\s+(?:static\s+)?(' + PKG.replace('.', r'\.') +
                       r'\.([\w.]+))\s*;', re.M)

REGISTRY_HINTS = (
    'ModItems', 'ModEffects', 'ModCreativeTabs', 'ModDataComponents', 'ModAttachments',
    'ModMenuTypes', 'ModRecipeSerializers', 'ModDamageTypes', 'AstralDiceMod',
    'ModNetwork', 'ModPayloads', 'SpellDamageRegistry', 'CardRegistry', 'DiceTierRegistry',
)

# 引擎层种子:模组无关的基础设施
SCENARIOS = {
    'P1 纯引擎核心': [
        'target/TargetSelectionAction',
        'target/TargetSelectionRegistry',
        'target/TargetType',
        'combat/AttackPowerModifier',
        'combat/DefensePowerModifier',
        'combat/SpellDamageModifier',
        'combat/SpellDamageContext',
    ],
    'P2 引擎+运行时基础设施': [
        'target/TargetSelectionAction',
        'target/TargetSelectionRegistry',
        'target/TargetType',
        'target/TargetSelectionManager',
        'combat/AttackPowerModifier',
        'combat/DefensePowerModifier',
        'combat/SpellDamageModifier',
        'combat/SpellDamageContext',
        'client/ActionBarManager',
        'item/ChargeManager',
        'event/EffectTimerGuard',
        'component/GameplayConstants',
        'config/ModCommonConfig',
        'event/AstralEventType',
        'event/EventEffect',
        'event/AstralEvents',
    ],
    'P3 引擎+内容层基类': [
        'target/TargetSelectionManager',
        'client/ActionBarManager',
        'item/ChargeManager',
        'event/EffectTimerGuard',
        'component/GameplayConstants',
        'config/ModCommonConfig',
        'item/chip/BaseChipItem',
        'item/card/CardItem',
        'item/card/BaseEffectCardItem',
        'item/sign/BaseSignItem',
        'item/dice/DiceCurioItem',
        'combat/DiceCombatModifiers',
        'combat/DiceCombatEvents',
    ],
}

SEED = SCENARIOS['P1 纯引擎核心']


def collect(base):
    out = {}
    for dirpath, dirnames, filenames in os.walk(os.path.join(base, 'src')):
        dirnames[:] = [d for d in dirnames if d != 'generated']
        for fn in filenames:
            if fn.endswith('.java'):
                full = os.path.join(dirpath, fn)
                rel = os.path.relpath(full, os.path.join(base, 'src')).replace('\\', '/')
                rel = rel.replace('main/java/' + PKG.replace('.', '/') + '/', '')
                rel = rel[:-len('.java')]  # 键统一为"无扩展名的类路径"
                with open(full, encoding='utf-8', errors='replace') as f:
                    out[rel] = f.read()
    return out


def main():
    a, b = collect(A), collect(B)

    def deps(rel):
        """返回 rel 在【两侧任一】树中的项目内依赖(类路径,无扩展名)。

        必须双侧取并集:例如 TargetSelectionManager 在 NeoForge 侧用
        PacketDistributor,在 Forge 侧用 ModNetwork —— 只看一侧会漏掉阻塞点。
        """
        out = set()
        for tree in (a, b):
            src = tree.get(rel)
            if src is None:
                continue
            for m in IMPORT_RE.finditer(src):
                name = m.group(2)          # 'target.GameplayConstants'
                parts = name.split('.')
                cls = parts[-1]
                pkg = '.'.join(parts[:-1])
                cand = (pkg.replace('.', '/') + '/' + cls) if pkg else cls
                if cand in a or cand in b:
                    out.add(cand)
        return out

    def closure(seed):
        seen, stack = set(), list(seed)
        changed = True
        while changed:
            changed = False
            for cur in list(stack):
                if cur in seen:
                    continue
                seen.add(cur)
                for d in deps(cur):
                    if d not in seen:
                        stack.append(d)
                        changed = True
        return seen

    for label, seed in SCENARIOS.items():
        seen = closure(seed)
        blockers = sorted(r for r in seen
                          if any(h in os.path.basename(r) for h in REGISTRY_HINTS))
        shared = sorted(r for r in seen if r in b)
        only_a = sorted(r for r in seen if r not in b)
        ident = sorted(r for r in seen if r in a and r in b and a[r] == b[r])

        print('=' * 80)
        print(f'场景 {label}')
        print('=' * 80)
        print(f'  种子                : {len(seed)}')
        print(f'  依赖闭包            : {len(seen)}')
        print(f'    两侧同名          : {len(shared)}  (其中逐字节相同 {len(ident)})')
        print(f'    仅 1.21.1 存在    : {len(only_a)}')
        print(f'  注册表阻塞点        : {len(blockers)}')
        if blockers:
            for r in blockers:
                print(f'      ⛔ {r}')
            print('  → 无法直接搬入独立库,需先做注册表反置')
        elif only_a:
            print('  → 可搬,但需为 Forge 侧补齐:')
            for r in only_a:
                print(f'      + {r}')
        else:
            print('  → ✅ 可无痛搬入独立库(两侧同名,无阻塞)')
        print()

    print('=' * 80)
    print('P1 场景闭包明细(逐字节相同)')
    print('=' * 80)
    seen = closure(SCENARIOS['P1 纯引擎核心'])
    for r in sorted(seen):
        same = "逐字节相同" if (r in a and r in b and a[r] == b[r]) else "有差异/单侧"
        print(f'  [{same}] {r}')


if __name__ == '__main__':
    sys.exit(main())
