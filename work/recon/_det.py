
import numpy as np
from PIL import Image
img = np.array(Image.open(r"F:/MCProject/astral_dice_multiloader/work/recon/user_screenshot.png").convert("RGB")).astype(np.int16)
H,W,_ = img.shape
base = r"F:/MCProject/astral_dice_multiloader/fabric-1.20.1/src/main/resources/assets/astral_dice/textures/gui/"
def detect(name):
    t = np.array(Image.open(base+name+".png").convert("RGBA")).astype(np.int16)
    a = t[:,:,3]
    rgb = t[:,:,:3]
    mask = a>200
    idx = np.argwhere(mask)
    if len(idx)==0: return []
    hits=[]
    for S in (2,3,4,5,6):
        for oy in range(S):
            for ox in range(S):
                # grid of 9x9 blocks starting at (oy,ox)
                ny=(H-oy)//S; nx=(W-ox)//S
                if ny<9 or nx<9: continue
                sub = img[oy:oy+ny*S, ox:ox+nx*S].reshape(ny,S,nx,S,3).mean(axis=(1,3))
                for j,i in idx:
                    pass
                # vectorize: compare all windows is expensive; use anchor texel
                # pick first opaque texel as anchor
                aj,ai = idx[0]
                cand = np.argwhere(np.all(np.abs(sub - rgb[aj,ai])<6, axis=2))
                for (gy,gx) in cand:
                    y0=gy-aj; x0=gx-ai
                    if y0<0 or x0<0 or y0+9>ny or x0+9>nx: continue
                    win = sub[y0:y0+9, x0:x0+9]
                    ok = np.all(np.abs(win[mask]-rgb[mask])<10, axis=1).sum()
                    if ok >= 0.9*mask.sum():
                        hits.append((S, ox+x0*S, oy+y0*S, int(ok), int(mask.sum())))
    # dedupe by position
    out=[]; seen=set()
    for h in sorted(hits, key=lambda t:-t[3]):
        key=(h[1]//8, h[2]//8)
        if key in seen: continue
        seen.add(key); out.append(h)
    return out
for n in ("attack_full","attack_half","defense_full","defense_half","defense_empty"):
    print(n, detect(n)[:12])
