# -*- coding: utf-8 -*-
import os, json, zipfile, re
os.chdir(r'F:/MCProject/astral_dice_multiloader')
jar = 'fabric-1.20.1/build/libs/astral_dice-1.5.0-hotfix+fabric_1.20.1.jar'
z = zipfile.ZipFile(jar)
names = z.namelist()

def bucket(prefix):
    return [n for n in names if n.startswith('data/') and n.split('/')[1] == prefix and n.endswith('.json')]

# path shape: data/<ns>/<dir>/...
rec = [n for n in names if re.match(r'^data/[^/]+/recipes/.+\.json$', n)]
adv = [n for n in names if re.match(r'^data/[^/]+/advancements/.+\.json$', n)]
print('recipes (data/<ns>/recipes/**):', len(rec))
print('advancements (data/<ns>/advancements/**):', len(adv))

def gated(ns):
    res = []
    for n in ns:
        t = z.read(n).decode('utf-8')
        if 'fabric:load_conditions' in t:
            res.append((n, 'astral_dice:chip_recipes_enabled' in t))
    return res

gr = gated(rec)
ga = gated(adv)
print('gated recipes     :', len(gr), '| with chip cond:', sum(1 for _, c in gr if c))
print('gated advancements:', len(ga), '| with chip cond:', sum(1 for _, c in ga if c))
print('non-chip gated recipes    :', [n for n, c in gr if not c])
print('non-chip gated advancements:', [n for n, c in ga if not c])
print('chip ADVANCEMENT-looking entries inside recipes dir:', [n for n, _ in gr if '/advancements/recipes/' in n][:3])
print()
print('chip recipe namespaces:', sorted({n.split('/')[1] for n, c in gr if c}))
print('first 3 chip recipes:', [n for n, c in gr if c][:3])
print()
# per-namespace gated chips
from collections import Counter
cc = Counter(n.split('/')[1] for n, c in gr if c)
ca = Counter(n.split('/')[1] for n, c in ga if c)
print('chip recipes by ns:', dict(cc))
print('chip advancements by ns:', dict(ca))
print()
print('=== all recipes naming chip ===')
chiprec = [n for n in rec if 'chip' in n]
print('recipes with chip in path:', len(chiprec))
print('  gated:', sum(1 for n in chiprec if 'fabric:load_conditions' in z.read(n).decode('utf-8')))
ung = [n for n in chiprec if 'fabric:load_conditions' not in z.read(n).decode('utf-8')]
print('  ungated:', ung)
