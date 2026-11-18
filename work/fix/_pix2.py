# -*- coding: utf-8 -*-
import sys, numpy as np
from PIL import Image
sys.path.insert(0, "F:/MCProject/astral_dice_multiloader/work/fix")
from _pix import load_mod, load_van

def scores(img, tex, S=4):
    a = tex[:, :, 3].astype(np.int16); rgb = tex[:, :, :3].astype(np.int16)
    mask = a > 200; n = int(mask.sum())
    H, W, _ = img.shape
    ny = (H - 0) // S; nx = (W - 0) // S
    sub = img[0:ny*S, 0:nx*S].reshape(ny, S, nx, S, 3).mean(axis=(1, 3))
    idx = np.argwhere(mask); aj, ai = idx[0]
    anc = np.argwhere(np.all(np.abs(sub - rgb[aj, ai]) < 12, axis=2))
    best = []
    for (gy, gx) in anc:
        y0 = gy - aj; x0 = gx - ai
        if y0 < 0 or x0 < 0 or y0 + 9 > ny or x0 + 9 > nx: continue
        win = sub[y0:y0+9, x0:x0+9]
        good = int(np.all(np.abs(win[mask] - rgb[mask]) < 10, axis=1).sum())
        best.append((good / n, x0*S, y0*S, good, n))
    best.sort(key=lambda t: -t[0])
    out=[]; seen=set()
    for b in best:
        k=(b[1]//8, b[2]//8)
        if k in seen: continue
        seen.add(k); out.append(b)
        if len(out)>=5: break
    return out

path = sys.argv[1]
img = np.array(Image.open(path).convert("RGB")).astype(np.int16)
for name in sys.argv[2].split(","):
    tex = load_mod(name) if name in ("attack_full","attack_half","defense_full","defense_half") else load_van(name)
    print("==", name, "opaque texels:", int((tex[:,:,3]>200).sum()))
    for (score, x, y, good, n) in scores(img, tex):
        print("   score=%.2f dev=(%4d,%4d) gui=(%6.2f,%6.2f) %d/%d" % (score, x, y, x/4, y/4, good, n))
