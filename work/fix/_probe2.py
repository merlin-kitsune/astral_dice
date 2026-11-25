# -*- coding: utf-8 -*-
import numpy as np
from PIL import Image
SHOTS = {"land":"D:/MCProject/astral_dice_multiloader/work/shots/fabric-attackbar/land_no_air.png",
         "water":"D:/MCProject/astral_dice_multiloader/work/shots/fabric-attackbar/underwater_air_row.png"}
BRIGHT = None
def block(im, gx, gy, S=4):
    y0, x0 = gy*S, gx*S
    return im[y0:y0+9*S, x0:x0+9*S].reshape(9,S,9,S,3).mean(axis=(1,3)).astype(int)
for name, p in SHOTS.items():
    im = np.array(Image.open(p).convert("RGB"))
    print("=====", name)
    for gy in (201, 211, 221, 231):
        b = block(im, 149, gy)
        lum = b.mean(axis=2)
        print("  gui(149,%d) mean=%.1f max=%.0f  bright_px(>150)=%d" % (gy, lum.mean(), lum.max(), int((lum>150).sum())))
    # search whole image for the defense icon's signature bright pixel (230,231,242)
    tgt = np.array([230,231,242])
    d = np.abs(im.astype(int) - tgt).sum(axis=2)
    ys, xs = np.where(d < 12)
    print("  pixels ~(230,231,242):", len(ys))
    if len(ys):
        import collections
        rows = collections.Counter((ys//4).tolist())
        print("  gui rows:", sorted(rows.items())[:20])
