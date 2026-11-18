# -*- coding: utf-8 -*-
import sys, os
sys.path.insert(0, r"F:/MCProject/astral_dice_multiloader/work/fix")
import numpy as np
from PIL import Image
import _pix

png = sys.argv[1]
scale = int(sys.argv[2]) if len(sys.argv) > 2 else 4
img = np.array(Image.open(png).convert("RGB")).astype(np.int16)

names = ["defense_full", "heart_full", "heart_absorb_full", "food_full", "air_full", "attack_half", "attack_full"]
found = {}
for n in names:
    tex = _pix.load_mod(n) if n in _pix.MOD_TEX else _pix.load_van(n)
    hits = _pix.find(img, tex, scale)
    found[n] = hits

print("== hits ==")
rects = {}
for n in names:
    if not found[n]:
        print("%-18s NONE" % n); continue
    ys, xs = [], []
    for (S, x, y, good, tot) in found[n]:
        ys.append(y); xs.append(x)
        print("%-18s dev=(%4d,%4d) gui=(%d,%d) %d/%d" % (n, x, y, x//S, y//S, good, tot))
    rects[n] = (min(xs), max(xs)+9*scale, min(ys), max(ys)+9*scale)

print()
print("== bands (device px, [y0,y1) inclusive-exclusive) ==")
for n, r in rects.items():
    print("%-18s x[%4d,%4d) y[%4d,%4d)  gui_y[%d,%d)" % (n, r[0], r[1], r[2], r[3], r[2]//scale, (r[3]+scale-1)//scale))

# overlap test between defense and every heart/absorption band
if "defense_full" in rects:
    d = rects["defense_full"]
    for n in ["heart_full", "heart_absorb_full"]:
        if n in rects:
            h = rects[n]
            ov = max(0, min(d[3], h[3]) - max(d[2], h[2]))
            xov = max(0, min(d[1], h[1]) - max(d[0], h[0]))
            print("defense vs %-18s y_overlap_dev=%d px  x_overlap_dev=%d px  -> %s" % (
                n, ov, xov, "ZERO-OVERLAP" if (ov == 0 or xov == 0) else "OVERLAP!"))
