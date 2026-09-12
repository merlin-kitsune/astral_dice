#!/usr/bin/env python3
"""分析候选共享文件对 Astral Dice 自身注册表/业务类的反向依赖强度。

判据:文件 import 了 com.merlinkitsune.astral_dice.* 下的哪些类。
   - 若依赖 ModItems / ModEffects / ModCreativeTabs / ModDataComponents 等"注册表"
     → 该文件与基础模组强耦合,搬进独立库模组会造成反向依赖(库←模组),不可行。
   - 若只依赖 GameplayConstants / 工具类 → 可搬。
"""
import os
import re
import sys
from collections import defaultdict

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
A = os.path.join(ROOT, 'neoforge-1.21.1')
B = os.path.join(ROOT, 'forge-1.20.1')

PKG = 'com.merlinkitsune.astral_dice'
IMPORT_RE = re.compile(r'^\s*import\s+(?:static\s+)?(' + PKG.replace('.', r'\.') + r'\.[\w.]+)\s*;', re.M)

# 视为"注册表/根基类"的强耦合目标(简化判定:类名包含这些关键字)
REGISTRY_HINTS = (
    'ModItems', 'ModEffects', 'ModCreativeTabs', 'ModDataComponents', 'ModAttachments',
    'ModMenuTypes', 'ModRecipeSerializers', 'ModDamageTypes', 'ModSounds', 'ModEntities',
    'ModBlocks', 'ModPayloads', 'ModNetwork', 'AstralDiceMod', 'AstralEventSystem',
    'AstralEvents', 'CardRegistry', 'SpellDamageRegistry', 'DiceTierRegistry',
    'ModEffectEvents', 'ModEffectRemoval',
)


def collect(base):
    out = {}
    for dirpath, dirnames, filenames in os.walk(os.path.join(base, 'src')):
        dirnames[:] = [d for d in dirnames if d != 'generated']
        for fn in filenames:
            if not fn.endswith('.java'):
                continue
            full = os.path.join(dirpath, fn)
            rel = os.path.relpath(full, os.path.join(base, 'src')).replace('\\', '/')
            with open(full, encoding='utf-8', errors='replace') as f:
                out[rel] = f.read()
    return out


def main():
    a, b = collect(A), collect(B)
    identical = sorted(rel for rel in (set(a) & set(b)) if a[rel] == b[rel])

    rows = []
    for rel in identical:
        deps = set()
        for m in IMPORT_RE.finditer(a[rel]):
            deps.add(m.group(1).rsplit('.', 1)[-1])
        hard = {d for d in deps if any(h in d for h in REGISTRY_HINTS)}
        rows.append((rel, deps, hard))

    clean = [r for r in rows if not r[2]]
    coupled = [r for r in rows if r[2]]

    print('=' * 84)
    print(f'66 个逐字节相同文件的"反向依赖"分析')
    print(f'  可搬(不依赖基础模组注册表): {len(clean)}')
    print(f'  强耦合(依赖注册表,搬出即反向依赖): {len(coupled)}')
    print('=' * 84)

    print()
    print('--- [可搬] 无注册表依赖 ---')
    for rel, deps, _ in clean:
        extra = ', '.join(sorted(d for d in deps if d not in ('AstralDiceMod',))) or '(仅自身包内)'
        print(f'  {rel}')
        print(f'      项目内依赖: {extra}')

    print()
    print('--- [强耦合] 依赖基础模组注册表 ---')
    for rel, deps, hard in coupled:
        print(f'  {rel}')
        print(f'      强耦合: {", ".join(sorted(hard))}')

    # 再统计:全部 215 文件中,哪些只依赖少量"可提取的核心"
    print()
    print('=' * 84)
    print('全部文件的项目内依赖目标频次(前 30)')
    print('=' * 84)
    freq = defaultdict(int)
    for rel, s in a.items():
        for m in IMPORT_RE.finditer(s):
            freq[m.group(1).rsplit('.', 1)[-1]] += 1
    for k, v in sorted(freq.items(), key=lambda x: -x[1])[:30]:
        print(f'  {v:>4}  {k}')


if __name__ == '__main__':
    sys.exit(main())
