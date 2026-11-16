
import os, shutil, subprocess, sys
base = 'work/.tmp/docsplit/mut'
if os.path.isdir(base):
    shutil.rmtree(base)
os.makedirs(os.path.join(base, 'docs', 'agent'))
for f in ['AGENTS-full.md', 'AGENTS-dsh.md', 'verify-split.py']:
    shutil.copy2(os.path.join('docs', 'agent', f), os.path.join(base, 'docs', 'agent', f))
shutil.copy2('AGENTS.md', os.path.join(base, 'AGENTS.md'))

def run(tag):
    r = subprocess.run([sys.executable, os.path.join(base, 'docs', 'agent', 'verify-split.py')],
                       capture_output=True)
    out = r.stdout.decode('utf-8', 'replace')
    fails = [l for l in out.split('\n') if 'FAIL' in l]
    print('--- %s: exit=%d fails=%d' % (tag, r.returncode, len(fails)))
    for l in fails:
        print('    ', l.strip()[:120])
    return r.returncode

rc0 = run('M0 baseline copy (expect 0)')

# M1: 1-char perturbation inside the red line zone of the copy
p = os.path.join(base, 'docs', 'agent', 'AGENTS-full.md')
b = open(p, 'rb').read().decode('utf-8')
old = '构建 → 本地详细测试 → 实机冒烟取证'
assert b.count(old) == 1, b.count(old)
open(p, 'wb').write(b.replace(old, '构建 → 本地详细测试X → 实机冒烟取证').encode('utf-8'))
rc1 = run('M1 +1 char inside zone (expect 1 = only zone check FAIL)')

# M2: add a bogus 8th numbered red line to AGENTS.md copy
pa = os.path.join(base, 'AGENTS.md')
a = open(pa, 'rb').read().decode('utf-8')
anchor = '7. **没有详细测试就没有发布版本**'
i = a.index(anchor)
j = a.index('\n', i) + 1
open(pa, 'wb').write((a[:j] + '8. **伪造红线**：用于变异测试。\n' + a[j:]).encode('utf-8'))
rc2 = run('M2 bogus 8th red line (expect 1 = only numbering check FAIL)')

print('MUTATION SUMMARY baseline=%d m1=%d m2=%d' % (rc0, rc1, rc2))
