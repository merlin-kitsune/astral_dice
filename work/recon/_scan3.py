
import re, os
BT = chr(96)
base = "F:/MCProject/astral_dice_multiloader/fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice"
src = open(base + "/datagen/ModRecipeProvider.java", encoding="utf-8").read().splitlines()
items = open(base + "/item/ModItems.java", encoding="utf-8").read()
const2id = {}
for m in re.finditer(r'RegistryObject<Item>\s+([A-Z0-9_]+)\s*=\s*registerItem\(\s*"([a-z0-9_]+)"', items):
    const2id[m.group(1)] = m.group(2)
blocks = []
cur = None
for i, line in enumerate(src, 1):
    m = re.search(r'(Shaped|Shapeless)RecipeBuilder\.(shaped|shapeless)\(\s*RecipeCategory\.[A-Z_]+,\s*ModItems\.([A-Z0-9_]+)\.get\(\)', line)
    if m:
        cur = {'line': i, 'builder': m.group(1), 'const': m.group(3)}
        continue
    if cur and '.save(' in line:
        cur['save_line'] = i
        cur['explicit'] = 'ResourceLocation' in line
        blocks.append(cur); cur = None
chips = [b for b in blocks if const2id.get(b['const'],'?').endswith('_chip')]
print("chip result blocks:", len(chips), "explicit:", [(const2id[b['const']], b['save_line']) for b in chips if b['explicit']])
rows = sorted((const2id[b['const']], b['line'], b['save_line'], b['builder']) for b in chips if not b['explicit'])
print("rows:", len(rows))
print("| # | 配方 id | 构建器起始行 | .save( 行 | 构建器 |")
print("|---|---|---|---|---|")
for n,(rid, bl, sl, bd) in enumerate(rows, 1):
    print("| " + str(n) + " | " + BT + rid + BT + " | :" + str(bl) + " | :" + str(sl) + " | " + bd + "RecipeBuilder |")
