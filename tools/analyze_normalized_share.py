#!/usr/bin/env python3
"""归一化「已知的平台机械差异」后重新比对两版源码。

若某文件归一化后逐字节相同,说明它仅因平台样板(注解/import/事件类型/载荷类型)
而不同 —— 业务逻辑是同一份,可通过平台抽象层收敛到共享源码树。

归一化规则(逐条对应实测到的差异类别):
  R1 平台 import 行整体删除
  R2 @Mod.EventBusSubscriber → @EventBusSubscriber
  R3 LivingDamageEvent.Pre → LivingDamageEvent
  R4 PlayerTickEvent.Post → PlayerTickEvent ; event.getEntity() → event.player
  R5 ModNetwork.XxxMessage → XxxPayload ; ModNetwork.sendToPlayer( → PacketDistributor.sendToPlayer(
  R6 注册表句柄 .get() 差异
  R7 事件类型 FQN 中的包路径
"""
import os
import re
import sys
from collections import defaultdict

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
A = os.path.join(ROOT, 'neoforge-1.21.1')
B = os.path.join(ROOT, 'forge-1.20.1')

PLATFORM_IMPORT = re.compile(
    r'^\s*import\s+(?:static\s+)?(?:net\.minecraftforge|net\.neoforged|top\.theillusivec4)[\w.]*\s*;\s*$',
    re.M)


def normalize(src):
    # R1 删除平台 import
    s = PLATFORM_IMPORT.sub('', src)
    # 项目自身 import 中,网络层类名归一
    s = re.sub(r'\bModNetwork\.(\w+?)Message\b', r'\1Payload', s)
    s = re.sub(r'\bModNetwork\b', 'PacketDistributor', s)
    # R2 事件总线注解
    s = s.replace('@Mod.EventBusSubscriber', '@EventBusSubscriber')
    # R3 事件类型
    s = s.replace('LivingDamageEvent.Pre', 'LivingDamageEvent')
    # R4 tick 事件
    s = s.replace('PlayerTickEvent.Post', 'PlayerTickEvent')
    s = s.replace('event.getEntity()', 'event.player')
    # R6 注册表句柄解引用：(NeoForge DeferredHolder 直接传;Forge 需 .get())
    s = re.sub(r'(ModEffects\.\w+)\.get\(\)', r'\1', s)
    s = re.sub(r'(ModItems\.\w+)\.get\(\)', r'\1', s)
    s = re.sub(r'(ModDataComponents\.\w+)\.get\(\)', r'\1', s)
    # 折叠空行
    s = re.sub(r'\n{2,}', '\n', s)
    return s.strip()


def collect(base):
    out = {}
    for dirpath, dirnames, filenames in os.walk(os.path.join(base, 'src')):
        dirnames[:] = [d for d in dirnames if d != 'generated']
        for fn in filenames:
            if fn.endswith('.java'):
                full = os.path.join(dirpath, fn)
                rel = os.path.relpath(full, os.path.join(base, 'src')).replace('\\', '/')
                with open(full, encoding='utf-8', errors='replace') as f:
                    out[rel] = f.read()
    return out


def main():
    a, b = collect(A), collect(B)
    both = sorted(set(a) & set(b))
    na, nb = {}, {}
    for rel in both:
        na[rel] = normalize(a[rel])
        nb[rel] = normalize(b[rel])

    raw_same = [r for r in both if a[r] == b[r]]
    norm_same = [r for r in both if na[r] == nb[r]]
    newly_same = [r for r in norm_same if r not in raw_same]
    still_diff = [r for r in both if na[r] != nb[r]]

    print('=' * 84)
    print('归一化平台样板后的可共享性')
    print('=' * 84)
    print(f'两版同名文件             : {len(both)}')
    print(f'  原始逐字节相同         : {len(raw_same)}')
    print(f'  归一化后相同(纯样板差异): {len(newly_same)}')
    print(f'  → 合计可共享           : {len(norm_same)}  ({len(norm_same)*100//len(both)}%)')
    print(f'  归一化后仍不同(真逻辑差异): {len(still_diff)}')

    print()
    print('--- [新识别] 仅平台样板差异,逻辑同一份 ---')
    for rel in newly_same:
        print('  ' + rel)

    print()
    print('--- [仍不同] 归一化后依旧有实质差异,需逐个评估 ---')
    from difflib import SequenceMatcher
    rows = []
    for rel in still_diff:
        da, db = na[rel].splitlines(), nb[rel].splitlines()
        sm = SequenceMatcher(None, da, db)
        ch = sum(max(i2 - i1, j2 - j1)
                 for tag, i1, i2, j1, j2 in sm.get_opcodes() if tag != 'equal')
        rows.append((ch, len(da), len(db), rel))
    rows.sort(reverse=True)
    print(f'{"差异行":>6} {"A行":>6} {"B行":>6}  文件')
    for ch, la, lb, rel in rows:
        print(f'{ch:>6} {la:>6} {lb:>6}  {rel}')


if __name__ == '__main__':
    sys.exit(main())
