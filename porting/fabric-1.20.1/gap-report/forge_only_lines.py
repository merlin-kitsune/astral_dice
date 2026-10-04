"""精准滞后判据：forge 的「实质语句行」在 fabric 中是否存在。
已规范化平台标识符、剔除 import/注解/纯注释。
未命中者 = forge 独有语义 ⇒ 候选滞后（需人工确认是否平台特有 API）。
"""
import os, re, glob

ROOT = r'F:\MCProject\astral_dice_multiloader'
PKG = 'src/main/java/com/merlinkitsune/astral_dice'

PLAT_SUBS = [
    (r'import [^;]+;', ''),
    (r'@Mod\.EventBusSubscriber\([^)]*\)', ''),
    (r'@EventBusSubscriber\([^)]*\)', ''),
    (r'\b(?:top\.theillusivec4\.curios\.api\.|com\.merlinkitsune\.astral_dice\.compat\.curios\.|com\.merlinkitsune\.astral_dice\.platform\.event\.|net\.minecraftforge\.event\.|net\.neoforged\.event\.)', ''),
    (r'\b(?:net\.minecraftforge\.|net\.neoforged\.)', ''),
    (r'CuriosCompat\.getCuriosInventory', 'CuriosApi.getCuriosInventory'),
    (r'com\.merlinkitsune\.starenginelib\.item\.CuriosCompat', 'CuriosCompat'),
]


def canon(s):
    for pat, rep in PLAT_SUBS:
        s = re.sub(pat, rep, s)
    s = re.sub(r'\s+', ' ', s).strip()
    return s


def content_lines(p):
    t = open(p, 'rb').read().decode('utf-8').replace('\r\n', '\n')
    out = []
    for ln in t.split('\n'):
        s = ln.strip()
        if not s:
            continue
        if s.startswith(('package ', 'import ', '@Mod.', '@EventBusSubscriber')):
            continue
        if s.startswith(('//', '*', '/*', '*/')):
            continue
        out.append(canon(ln))
    return out


rows = []
for fp in sorted(glob.glob(os.path.join(ROOT, 'forge-1.20.1', PKG, '**', '*.java'), recursive=True)):
    rel = os.path.relpath(fp, os.path.join(ROOT, 'forge-1.20.1', PKG))
    bp = os.path.join(ROOT, 'fabric-1.20.1', PKG, rel)
    if not os.path.exists(bp):
        continue
    fa = content_lines(fp)
    fb = set(content_lines(bp))
    miss = [x for x in fa if x not in fb]
    if miss:
        rows.append((rel.replace('\\', '/'), miss))

print(f'=== forge 独有语义行命中文件 {len(rows)} 个 ===')
tot = sum(len(m) for _, m in rows)
print(f'合计未命中行 {tot}\n')
for rel, miss in sorted(rows, key=lambda x: -len(x[1])):
    print(f'--- {rel}  ({len(miss)} 行)')
    for m in miss[:14]:
        print('    ', m[:150])
    if len(miss) > 14:
        print(f'     … 另 {len(miss) - 14} 行')
