
from PIL import Image
import collections
im = Image.open(r"D:/MCProject/astral_dice_multiloader/work/recon/user_screenshot.png").convert("RGB")
W,H = im.size
px = im.load()
print("size", W, H)
# hearts: red-ish
def isheart(c):
    r,g,b = c
    return r>110 and r > g*2+20 and r > b*2+20
heart_rows = collections.Counter()
heart_cols = collections.Counter()
for y in range(H):
    for x in range(W):
        if isheart(px[x,y]):
            heart_rows[y]+=1; heart_cols[x]+=1
# print row histogram summary (rows with >=5 heart px)
rows = [(y,n) for y,n in sorted(heart_rows.items()) if n>=5]
print("heart rows:", rows[0] if rows else None, "..", rows[-1] if rows else None, "count rows", len(rows))
xs=[x for x,n in heart_cols.items() if n>=3]
print("heart x range", min(xs) if xs else None, max(xs) if xs else None)
# swords: near-white/gray bright pixels in right half, above hotbar
def isbright(c):
    r,g,b=c
    return r>150 and g>150 and b>150 and abs(r-g)<25 and abs(g-b)<25
sb_rows=collections.Counter(); sb_cols=collections.Counter()
for y in range(0, min(H,230)):
    for x in range(W//2, W):
        if isbright(px[x,y]):
            sb_rows[y]+=1; sb_cols[x]+=1
srows=[(y,n) for y,n in sorted(sb_rows.items()) if n>=5]
print("bright-right rows:", srows[:4], "...", srows[-4:] if srows else None)
sxs=[x for x,n in sb_cols.items() if n>=3]
print("bright-right x range", min(sxs) if sxs else None, max(sxs) if sxs else None, "total px", sum(sb_rows.values()))
