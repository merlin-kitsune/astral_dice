#!/usr/bin/env python3
"""对比 neoforge-1.21.1 与 forge-1.20.1 两个自包含子项目的源码重复面。

输出:
  1. 总览计数
  2. 同相对路径且逐字节相同的文件(可直接共享候选)
  3. 同相对路径但内容不同的文件(需适配层)
  4. 仅单侧存在的文件
"""
import hashlib
import difflib
import os
import sys
from collections import defaultdict

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
A = os.path.join(ROOT, 'neoforge-1.21.1')  # 1.21.1 NeoForge
B = os.path.join(ROOT, 'forge-1.20.1')     # 1.20.1 Forge

EXTS = {'.java'}


def collect(base):
    out = {}
    for dirpath, dirnames, filenames in os.walk(os.path.join(base, 'src')):
        dirnames[:] = [d for d in dirnames if d not in ('generated',)]
        for fn in filenames:
            ext = os.path.splitext(fn)[1]
            if ext not in EXTS:
                continue
            full = os.path.join(dirpath, fn)
            rel = os.path.relpath(full, os.path.join(base, 'src')).replace('\\', '/')
            with open(full, 'rb') as f:
                data = f.read()
            out[rel] = (data, full)
    return out


def kind(rel):
    """按 top-level 包名归类。"""
    parts = rel.split('/')
    # src/main/java/com/merlinkitsune/astral_dice/<pkg>/...
    try:
        i = parts.index('astral_dice')
        return parts[i + 1] if i + 1 < len(parts) else '(root)'
    except ValueError:
        return '(other:' + '/'.join(parts[:3]) + ')'


def main():
    a = collect(A)
    b = collect(B)
    only_a = sorted(set(a) - set(b))
    only_b = sorted(set(b) - set(a))
    both = sorted(set(a) & set(b))

    identical, differing = [], []
    for rel in both:
        if a[rel][0] == b[rel][0]:
            identical.append(rel)
        else:
            differing.append(rel)

    print('=' * 78)
    print('总览')
    print('=' * 78)
    print(f'1.21.1 NeoForge java 文件: {len(a)}')
    print(f'1.20.1 Forge    java 文件: {len(b)}')
    print(f'  同名同内容 : {len(identical)}')
    print(f'  同名不同内容: {len(differing)}')
    print(f'  仅 1.21.1   : {len(only_a)}')
    print(f'  仅 1.20.1   : {len(only_b)}')

    # 按包归类统计
    print()
    print('=' * 78)
    print('按包统计(同名文件)')
    print('=' * 78)
    stat = defaultdict(lambda: [0, 0, 0])  # same, diff, onlyA
    for rel in identical:
        stat[kind(rel)][0] += 1
    for rel in differing:
        stat[kind(rel)][1] += 1
    for rel in only_a:
        stat[kind(rel)][2] += 1
    print(f'{"包名":<24} {"同内容":>6} {"不同内容":>8} {"仅A":>6}')
    for k in sorted(stat):
        s, d, o = stat[k]
        print(f'{k:<24} {s:>6} {d:>8} {o:>6}')

    print()
    print('=' * 78)
    print(f'同名且逐字节相同 ({len(identical)}) —— 共享库首选')
    print('=' * 78)
    for rel in identical:
        print('  ' + rel)

    print()
    print('=' * 78)
    print(f'同名但内容有差异 ({len(differing)}) —— 需评估适配成本')
    print('=' * 78)
    for rel in differing:
        da = a[rel][0].decode('utf-8', 'replace').splitlines()
        db = b[rel][0].decode('utf-8', 'replace').splitlines()
        sm = difflib.SequenceMatcher(None, da, db)
        changed = sum(max(i2 - i1, j2 - j1)
                      for tag, i1, i2, j1, j2 in sm.get_opcodes() if tag != 'equal')
        total = max(len(da), len(db))
        print(f'  {rel}')
        print(f'      A={len(da):>4}行 B={len(db):>4}行 差异行≈{changed} ({changed * 100 // max(total, 1)}%)')

    print()
    print('=' * 78)
    print(f'仅 1.21.1 NeoForge 存在 ({len(only_a)})')
    print('=' * 78)
    for rel in only_a:
        print('  ' + rel)

    print()
    print('=' * 78)
    print(f'仅 1.20.1 Forge 存在 ({len(only_b)})')
    print('=' * 78)
    for rel in only_b:
        print('  ' + rel)


if __name__ == '__main__':
    sys.exit(main())
