#!/usr/bin/env python3
"""按「平台 API 依赖面」给两个子项目的 java 文件分类。

目的:判断哪些文件是"纯业务逻辑"(可无痛共享),哪些必须走平台抽象层,
哪些天生绑定单一加载器。

平台 API 家族:
  FORGE_EVENTBUS  net.minecraftforge.*
  NEO_EVENTBUS    net.neoforged.*
  MC              net.minecraft.*            (跨版本 API 面本身不同)
  FORGE_BRIDGE    net.minecraftforge.common.capabilities / fml / ModLoader 等
  CURIOS          top.theillusivec4.curios.*
  MIXIN           org.spongepowered.asm.*
  PATCHOULI       vazkii.patchouli.*
  JEI             mezz.jei.*
  OTHER_MODS      (其余第三方)
"""
import os
import re
import sys
from collections import defaultdict

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
PROJECTS = {
    'neoforge': os.path.join(ROOT, 'neoforge-1.21.1'),
    'forge': os.path.join(ROOT, 'forge-1.20.1'),
}

IMPORT_RE = re.compile(r'^\s*import\s+(static\s+)?([\w.]+)\s*;', re.M)


def families(src):
    fams = set()
    for m in IMPORT_RE.finditer(src):
        fq = m.group(2)
        if fq.startswith('net.minecraftforge.'):
            fams.add('FORGE')
        elif fq.startswith('net.neoforged.'):
            fams.add('NEO')
        elif fq.startswith('net.minecraft.'):
            fams.add('MC')
        elif fq.startswith('top.theillusivec4.curios.'):
            fams.add('CURIOS')
        elif fq.startswith('org.spongepowered.asm.'):
            fams.add('MIXIN')
        elif fq.startswith('vazkii.patchouli.'):
            fams.add('PATCHOULI')
        elif fq.startswith('mezz.jei.'):
            fams.add('JEI')
        elif fq.startswith(('java.', 'javax.', 'com.mojang.', 'org.slf4j.', 'it.unimi')):
            pass
        elif fq.startswith('com.merlinkitsune.'):
            pass
        else:
            fams.add('OTHER:' + '.'.join(fq.split('.')[:3]))
    return fams


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
    data = {k: collect(v) for k, v in PROJECTS.items()}
    a, b = data['neoforge'], data['forge']

    # 平台相关文件占比
    print('=' * 84)
    print('平台 API 依赖分布')
    print('=' * 84)
    print(f'{"":<28}{"1.21.1 Neo":>14}{"1.20.1 Forge":>14}')
    keys = ['MC', 'NEO', 'FORGE', 'CURIOS', 'MIXIN', 'PATCHOULI', 'JEI']
    fa = defaultdict(int)
    fb = defaultdict(int)
    for rel, s in a.items():
        for x in families(s):
            fa[x] += 1
    for rel, s in b.items():
        for x in families(s):
            fb[x] += 1
    for k in keys:
        print(f'{k:<28}{fa[k]:>14}{fb[k]:>14}')
    others = sorted(set(x for x in fa if x.startswith('OTHER')) |
                    set(x for x in fb if x.startswith('OTHER')))
    for k in others:
        print(f'{k:<28}{fa[k]:>14}{fb[k]:>14}')

    print()
    print('=' * 84)
    print('不依赖任何平台事件总线/加载器 API 的文件(仅用 MC + 本项目)')
    print('=' * 84)
    plat = {'NEO', 'FORGE', 'CURIOS', 'MIXIN', 'PATCHOULI', 'JEI'}
    pure = []
    for rel in sorted(set(a) & set(b)):
        f = families(a[rel]) | families(b[rel])
        if not (f & plat):
            pure.append((rel, 'MC' in f))
    print(f'共 {len(pure)} 个:')
    for rel, uses_mc in pure:
        tag = 'MC' if uses_mc else 'POJO'
        print(f'  [{tag:>4}] {rel}')

    print()
    print('=' * 84)
    print('完全无 MC/平台依赖的纯 POJO(最安全的共享核心)')
    print('=' * 84)
    for rel, uses_mc in pure:
        if not uses_mc:
            print('  ' + rel)


if __name__ == '__main__':
    sys.exit(main())
