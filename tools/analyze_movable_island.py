#!/usr/bin/env python3
"""计算「可搬迁孤岛」: 传递闭包中不含任何注册表阻塞点的文件集合。

这是"能直接搬进独立库模组"的**真实**上限 —— 不是"逻辑相同"的文件数,
而是"逻辑相同 且 依赖可达闭包内不触碰基础模组注册表"的文件数。
"""
import os
import re
import sys
from collections import defaultdict

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
TREES = {
    'neoforge': os.path.join(ROOT, 'neoforge-1.21.1'),
    'forge': os.path.join(ROOT, 'forge-1.20.1'),
}
PKG = 'com.merlinkitsune.astral_dice'

IMPORT_RE = re.compile(r'^\s*import\s+(?:static\s+)?(' + PKG.replace('.', r'\.') +
                       r'\.([\w.]+))\s*;', re.M)

# 注册表/入口/网络/平台 —— 命中任一即视为"不可入独立库"
BLOCKERS = {
    'AstralDiceMod', 'ModItems', 'ModEffects', 'ModCreativeTabs', 'ModDataComponents',
    'ModAttachments', 'ModMenuTypes', 'ModRecipeSerializers', 'ModDamageTypes',
    'ModNetwork', 'ModEnchantments', 'AttachedDataKey', 'ClientAstralData',
    'CardRegistry', 'SpellDamageRegistry', 'DiceTierRegistry', 'ModEffectEvents',
    'ModEffectRemoval', 'AstralEventSystem', 'ModPayloads',
}


def collect(base):
    out = {}
    for dp, dn, fns in os.walk(os.path.join(base, 'src')):
        dn[:] = [d for d in dn if d != 'generated']
        for fn in fns:
            if not fn.endswith('.java'):
                continue
            full = os.path.join(dp, fn)
            rel = os.path.relpath(full, os.path.join(base, 'src')).replace('\\', '/')
            rel = rel.replace('main/java/' + PKG.replace('.', '/') + '/', '')[:-5]
            out[rel] = open(full, encoding='utf-8', errors='replace').read()
    return out


def main():
    trees = {k: collect(v) for k, v in TREES.items()}
    a, b = trees['neoforge'], trees['forge']
    allkeys = set(a) | set(b)

    def deps(rel):
        out = set()
        for t in (a, b):
            s = t.get(rel)
            if s is None:
                continue
            for m in IMPORT_RE.finditer(s):
                p = m.group(2).split('.')
                pkg = '.'.join(p[:-1])
                cand = (pkg.replace('.', '/') + '/' + p[-1]) if pkg else p[-1]
                if cand in allkeys:
                    out.add(cand)
        return out

    def closure(seed):
        seen, stack = set(), list(seed)
        while stack:
            c = stack.pop()
            if c in seen:
                continue
            seen.add(c)
            stack.extend(deps(c) - seen)
        return seen

    # 逐文件判定:其闭包内是否触碰阻塞点
    pure_island, tainted = [], []
    for rel in sorted(allkeys):
        cl = closure([rel])
        hit = sorted(x for x in cl if os.path.basename(x) in BLOCKERS)
        if hit:
            tainted.append((rel, hit))
        else:
            pure_island.append(rel)

    print('=' * 84)
    print('可搬迁孤岛分析(闭包内不触碰任何注册表阻塞点)')
    print('=' * 84)
    print(f'全部类单元          : {len(allkeys)}')
    print(f'  可搬迁孤岛        : {len(pure_island)}')
    print(f'  闭包触碰阻塞点    : {len(tainted)}')

    # 孤岛中两侧同名/相同的比例
    both = [r for r in pure_island if r in a and r in b]
    ident = [r for r in both if a[r] == b[r]]
    only_a = [r for r in pure_island if r in a and r not in b]
    only_b = [r for r in pure_island if r in b and r not in a]
    print()
    print(f'  孤岛中两侧同名      : {len(both)}  (逐字节相同 {len(ident)})')
    print(f'  孤岛中仅 1.21.1     : {len(only_a)}')
    print(f'  孤岛中仅 1.20.1     : {len(only_b)}')

    print()
    print('--- 孤岛成员 ---')
    for r in pure_island:
        tag = '同名同内容' if r in ident else ('同名有差异' if r in both else
                                        ('仅1.21.1' if r in only_a else '仅1.20.1'))
        print(f'  [{tag:>10}] {r}')

    print()
    print('=' * 84)
    print('孤岛连通分量(每个分量是独立的搬迁单元)')
    print('=' * 84)
    seen, comps = set(), []
    for r in pure_island:
        if r in seen:
            continue
        cl = closure([r]) & set(pure_island)
        comps.append(sorted(cl))
        seen |= set(cl)
    comps.sort(key=len, reverse=True)
    for i, c in enumerate(comps, 1):
        print(f'  分量 {i} ({len(c)} 个): {", ".join(c)}')


if __name__ == '__main__':
    sys.exit(main())
