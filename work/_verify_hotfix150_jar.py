# -*- coding: utf-8 -*-
import os, sys, json, hashlib, zipfile
os.chdir(r'F:/MCProject/astral_dice_multiloader')
libs = 'fabric-1.20.1/build/libs'
print('=== libs listing ===')
for f in sorted(os.listdir(libs)):
    p = os.path.join(libs, f)
    print('  %-55s %10d B' % (f, os.path.getsize(p)))
jar = os.path.join(libs, 'astral_dice-1.5.0-hotfix+fabric_1.20.1.jar')
print('=== target jar ===')
print('exists:', os.path.exists(jar))
data = open(jar, 'rb').read()
print('bytes :', len(data))
print('sha256:', hashlib.sha256(data).hexdigest())
z = zipfile.ZipFile(jar)
names = z.namelist()
print('entries:', len(names))
print('=== 1) fabric.mod.json ===')
fmj = z.read('fabric.mod.json').decode('utf-8')
d = json.loads(fmj)
for k in ('id','name','version'):
    print('  %-8s = %r' % (k, d.get(k)))
print('  raw version line:', [l.strip() for l in fmj.splitlines() if 'version' in l][:3])
print('=== 2) version resource (VersionGate readVersionResource target) ===')
cands = [n for n in names if 'version' in n.lower() and n.endswith(('.properties','.json','.txt'))]
print('  candidate entries:', cands)
tpl = [n for n in names if 'astral_dice_version' in n]
print('  astral_dice_version* entries:', tpl)
print('=== 3) load_conditions counts ===')
rec = [n for n in names if n.startswith('data/') and '/recipes/' in n and n.endswith('.json')]
adv = [n for n in names if n.startswith('data/') and '/advancements/' in n and n.endswith('.json')]
def gated(ns):
    out = []
    for n in ns:
        try:
            t = z.read(n).decode('utf-8')
        except Exception:
            continue
        if 'fabric:load_conditions' in t:
            out.append(n)
    return out
gr = gated(rec)
ga = gated(adv)
print('  recipes total      :', len(rec))
print('  recipes gated      :', len(gr))
print('  advancements total :', len(adv))
print('  advancements gated :', len(ga))
print('  chip_recipes_enabled in recipes:', sum(1 for n in gr if 'astral_dice:chip_recipes_enabled' in z.read(n).decode('utf-8')))
print('  sample gated recipe:', gr[0] if gr else None)
if gr:
    print('  --- sample body ---')
    print(z.read(gr[0]).decode('utf-8').strip()[:400])
print('=== 4) embedded jars ===')
emb = [n for n in names if n.startswith('META-INF/jars/')]
print('  META-INF/jars entries:', len(emb), emb[:5])
print('=== extra: mod metadata dirs ===')
print('  META-INF:', sorted({n.split('/')[1] for n in names if n.startswith('META-INF/') and '/' in n[9:]})[:10])
