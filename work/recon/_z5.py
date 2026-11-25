
import zipfile, io
from PIL import Image
jar = r"C:/Users/xmace/.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar"
z = zipfile.ZipFile(jar)
icons = Image.open(io.BytesIO(z.read("assets/minecraft/textures/gui/icons.png"))).convert("RGBA")
shot = Image.open(r"D:/MCProject/astral_dice_multiloader/work/recon/user_screenshot.png").convert("RGB")
items=[("V foodFull 52,27", icons.crop((52,27,61,36)).convert("RGBA"), 16),
       ("V foodHalf 61,27", icons.crop((61,27,70,36)).convert("RGBA"), 16),
       ("V armorFull 34,9", icons.crop((34,9,43,18)).convert("RGBA"), 16),
       ("SHOT right icon", shot.crop((452,140,492,182)).convert("RGBA"), 7),
       ("SHOT heart", shot.crop((98,142,138,184)).convert("RGBA"), 7)]
W=sum(i.width*sc+14 for _,i,sc in items)+14
H=max(i.height*sc for _,i,sc in items)+28
cv=Image.new("RGB",(W,H),(25,25,25)); x=14
for nm,i,sc in items:
    s=i.resize((i.width*sc,i.height*sc), Image.NEAREST).convert("RGBA")
    bg=Image.new("RGBA", s.size,(25,25,25,255)); bg.alpha_composite(s)
    cv.paste(bg.convert("RGB"),(x,14)); print(nm, i.size); x+=s.width+14
cv.save(r"D:/MCProject/astral_dice_multiloader/work/recon/vanilla_cmp.png"); print("ok", cv.size)
