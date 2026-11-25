# -*- coding: utf-8 -*-
import sys, numpy as np
from PIL import Image
def block(path, gx, gy, S=4):
    im = np.array(Image.open(path).convert("RGB"))
    y0, x0 = gy*S, gx*S
    sub = im[y0:y0+9*S, x0:x0+9*S].reshape(9, S, 9, S, 3).mean(axis=(1,3)).astype(int)
    return sub
def show(name, b):
    print("--", name)
    cols = {}
    lines = []
    for r in range(9):
        row = []
        for c in range(9):
            t = tuple(b[r,c])
            if t not in cols:
                cols[t] = chr(ord('A')+len(cols)) if len(cols) < 26 else '?'
            row.append(cols[t])
        lines.append("".join(row))
    print("\n".join(lines))
    print("legend:", {v:k for k,v in cols.items()})
for p in ["D:/MCProject/astral_dice_multiloader/work/shots/fabric-attackbar/land_no_air.png",
          "D:/MCProject/astral_dice_multiloader/work/shots/fabric-attackbar/underwater_air_row.png"]:
    print("=====", p.split("/")[-1])
    b = block(p, 149, 221)
    show("gui(149,221) defense slot", b)
    b2 = block(p, 322, 221)
    show("gui(322,221) rightmost of row-221", b2)
