"""全量语义差测绘：fabric vs forge（同为 MC 1.20.1）
剔除平台噪声（import / @Mod.EventBusSubscriber / Curios shim / platform.event 包）后，
列出仍有实质差异的文件与差异行数——这些才是真实滞后面。
"""
import os, re, glob, difflib

ROOT = r'F:\MCProject\astral_dice_multiloader'
PKG = 'src/main/java/com/merlinkitsune/astral_dice'

PLAT_SUBS = [
    (r'import net\.minecraftforge\.[^;]+;', ''),
    (r'import net\.neoforged\.[^;]+;', ''),
    (r'import com\.merlinkitsune\.astral_dice\.platform\.[^;]+;', ''),
    (r'import com\.merlinkitsune\.astral_dice\.compat\.curios\.[^;]+;', ''),
    (r'import top\.theillusivec4\.curios\.[^;]+;', ''),
    (r'import com\.merlinkitsune\.starenginelib\.item\.CuriosCompat;', ''),
    (r'@Mod\.EventBusSubscriber\([^)]*\)', ''),
    (r'@EventBusSubscriber\([^)]*\)', ''),
    (r'\b(?:top\.theillusivec4\.curios\.api\.|com\.merlinkitsune\.astral_dice\.compat\.curios\.|com\.merlinkitsune\.astral_dice\.platform\.event\.|net\.minecraftforge\.event\.|net\.neoforged\.event\.)', ''),
    (r'\b(?:net\.minecraftforge\.|net\.neoforged\.)', ''),
    (r'CuriosCompat\.getCuriosInventory', 'CuriosApi.getCuriosInventory'),
]


def norm(txt):
    t = txt.replace('\r\n', '\n')
    for pat, rep in PLAT_SUBS:
        t = re.sub(pat, rep, t)
    out = []
    for ln in t.split('\n'):
        s = ln.strip()
        if s == '':
            continue
        if s.startswith('import ') or s.startswith('package '):
            continue
        out.append(ln.rstrip())
    return out


def load(p):
    return open(p, 'rb').read().decode('utf-8')


rows = []
only_f, only_b = [], []
for fp in sorted(glob.glob(os.path.join(ROOT, 'forge-1.20.1', PKG, '**', '*.java'), recursive=True)):
    rel = os.path.relpath(fp, os.path.join(ROOT, 'forge-1.20.1', PKG))
    bp = os.path.join(ROOT, 'fabric-1.20.1', PKG, rel)
    if not os.path.exists(bp):
        only_f.append(rel)
        continue
    a, b = norm(load(fp)), norm(load(bp))
    if a == b:
        continue
    d = [x for x in difflib.unified_diff(a, b, lineterm='', n=0) if x[:1] in '+-' and not x.startswith(('+++', '---'))]
    rows.append((rel.replace('\\', '/'), len(d), a, b))

for bp in sorted(glob.glob(os.path.join(ROOT, 'fabric-1.20.1', PKG, '**', '*.java'), recursive=True)):
    rel = os.path.relpath(bp, os.path.join(ROOT, 'fabric-1.20.1', PKG))
    if not os.path.exists(os.path.join(ROOT, 'forge-1.20.1', PKG, rel)):
        only_b.append(rel.replace('\\', '/'))

print(f'=== 语义差文件 {len(rows)} 个（forge 有 fabric 无: {len(only_f)}; fabric 有 forge 无: {len(only_b)}）===')
for rel, n, a, b in sorted(rows, key=lambda x: -x[1]):
    print(f'{n:5d}  {rel}')
print()
print('--- forge 独有（fabric 缺文件）---')
for x in only_f:
    print('   -', x.replace('\\', '/'))
print('--- fabric 独有 ---')
for x in only_b:
    print('   +', x)
