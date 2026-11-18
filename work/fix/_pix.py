# -*- coding: utf-8 -*-
import sys, numpy as np
from PIL import Image

MOD = "F:/MCProject/astral_dice_multiloader/fabric-1.20.1/src/main/resources/assets/astral_dice/textures/gui/"
ICONS = "F:/MCProject/astral_dice_multiloader/work/fix/vanilla/icons.png"

# mod textures (standalone 9x9 png)
MOD_TEX = {
    "attack_full": (MOD + "attack_full.png", None),
    "attack_half": (MOD + "attack_half.png", None),
    "defense_full": (MOD + "defense_full.png", None),
    "defense_half": (MOD + "defense_half.png", None),
}
# vanilla sprites inside icons.png: (col,row) 9x9
VAN = {
    "food_container": (16, 27),
    "food_full": (52, 27),
    "food_half": (61, 27),
    "air_full": (16, 18),
    "air_half": (25, 18),
    "heart_full": (52, 0),
    "heart_half": (61, 0),
    "armor_full": (34, 9),
    "heart_absorb_full": (160, 0),
    "heart_absorb_half": (169, 0),
}

def load_mod(name):
    return np.array(Image.open(MOD_TEX[name][0]).convert("RGBA"))

def load_van(name):
    sheet = np.array(Image.open(ICONS).convert("RGBA"))
    cx, cy = VAN[name]
    return sheet[cy:cy+9, cx:cx+9]

def find(img, tex, scale):
    H, W, _ = img.shape
    a = tex[:, :, 3].astype(np.int16)
    rgb = tex[:, :, :3].astype(np.int16)
    mask = a > 200
    n = int(mask.sum())
    if n < 8:
        return []
    idx = np.argwhere(mask)
    hits = []
    for S in ([scale] if scale else [2,3,4,5,6]):
        S = int(S)
        for oy in range(S):
            for ox in range(S):
                ny = (H - oy) // S
                nx = (W - ox) // S
                if ny < 9 or nx < 9:
                    continue
                sub = img[oy:oy+ny*S, ox:ox+nx*S].reshape(ny, S, nx, S, 3).mean(axis=(1, 3))
                # region where every masked texel matches within 10
                okmap = np.ones((ny, nx), dtype=bool)
                # pre-check anchor to reduce work
                aj, ai = idx[0]
                anc = np.argwhere(np.all(np.abs(sub - rgb[aj, ai]) < 6, axis=2))
                for (gy, gx) in anc:
                    y0 = gy - aj; x0 = gx - ai
                    if y0 < 0 or x0 < 0 or y0 + 9 > ny or x0 + 9 > nx:
                        continue
                    win = sub[y0:y0+9, x0:x0+9]
                    good = int(np.all(np.abs(win[mask] - rgb[mask]) < 10, axis=1).sum())
                    if good >= 0.9 * n:
                        hits.append((S, ox + x0*S, oy + y0*S, good, n))
    out, seen = [], set()
    for h in sorted(hits, key=lambda t: -t[3]):
        key = (h[1]//8, h[2]//8)
        if key in seen:
            continue
        seen.add(key); out.append(h)
    return out

def main(path, scale=4, only=None):
    img = np.array(Image.open(path).convert("RGB")).astype(np.int16)
    print("image", path, img.shape[1], "x", img.shape[0], "scale", scale)
    names = list(MOD_TEX) + list(VAN)
    if only:
        names = [n for n in names if n in only]
    res = {}
    for n in names:
        tex = load_mod(n) if n in MOD_TEX else load_van(n)
        hits = find(img, tex, scale)
        res[n] = hits
        if hits:
            for (S, x, y, good, tot) in hits[:14]:
                print("%-15s dev=(%4d,%4d) gui=(%6.2f,%6.2f) match=%d/%d" % (n, x, y, x/S, y/S, good, tot))
        else:
            print("%-15s NO MATCH" % n)
    return res

if __name__ == "__main__":
    p = sys.argv[1]
    s = int(sys.argv[2]) if len(sys.argv) > 2 else 4
    only = sys.argv[3].split(",") if len(sys.argv) > 3 else None
    main(p, s, only)
