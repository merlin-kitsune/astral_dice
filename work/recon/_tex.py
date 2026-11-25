
from PIL import Image
import os
base = r"D:/MCProject/astral_dice_multiloader/fabric-1.20.1/src/main/resources/assets/astral_dice/textures/gui"
names = ["attack_full","attack_half","defense_full","defense_half","defense_empty","attack_dot","defense_dot"]
imgs=[]
for n in names:
    p=os.path.join(base,n+".png")
    im=Image.open(p).convert("RGBA")
    imgs.append((n,im))
    cols={}
    for px in im.getdata():
        if px[3]>10: cols[px[:3]]=cols.get(px[:3],0)+1
    top=sorted(cols.items(), key=lambda kv:-kv[1])[:4]
    print(n, im.size, "opaque", sum(cols.values()), "top", top)
# montage: scale x12 on checker
SC=12
PAD=8
w=sum(i.width*SC+PAD for _,i in imgs)+PAD
h=max(i.height*SC for _,i in imgs)+PAD*2
canvas=Image.new("RGBA",(w,h),(40,40,40,255))
x=PAD
for n,i in imgs:
    s=i.resize((i.width*SC,i.height*SC), Image.NEAREST)
    canvas.alpha_composite(s,(x,PAD))
    x+=s.width+PAD
canvas.save(r"D:/MCProject/astral_dice_multiloader/work/recon/textures_montage.png")
print("saved")
