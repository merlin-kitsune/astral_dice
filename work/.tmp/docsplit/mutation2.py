
import os, shutil, subprocess, sys
base = 'work/.tmp/docsplit/mut2'
if os.path.isdir(base):
    shutil.rmtree(base)
os.makedirs(os.path.join(base, 'docs', 'agent'))
for f in ['AGENTS-full.md', 'AGENTS-dsh.md', 'verify-split.py']:
    shutil.copy2(os.path.join('docs', 'agent', f), os.path.join(base, 'docs', 'agent', f))
shutil.copy2('AGENTS.md', os.path.join(base, 'AGENTS.md'))
pa = os.path.join(base, 'AGENTS.md')
a = open(pa, 'rb').read().decode('utf-8')
i = a.index('7. **没有详细测试就没有发布版本**')
j = a.index('\n', i) + 1
open(pa, 'wb').write((a[:j] + '8. **伪造红线**：用于变异测试。\n' + a[j:]).encode('utf-8'))
r = subprocess.run([sys.executable, os.path.join(base, 'docs', 'agent', 'verify-split.py')], capture_output=True)
out = r.stdout.decode('utf-8', 'replace')
print('exit', r.returncode)
for l in out.split('\n'):
    if '[FAIL]' in l or '[OK]' in l:
        print(l.strip()[:130])
