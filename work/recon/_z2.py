
from PIL import Image
im = Image.open(r"F:/MCProject/astral_dice_multiloader/work/recon/user_screenshot.png").convert("RGB")
# single icon crops from right row (approx) and left heart
out = Image.new("RGB",(3*180+40, 200),(30,30,30))
subs = [(455,140,500,185),(500,140,545,185),(100,140,145,185)]
for i,box in enumerate(subs):
    c = im.crop(box); c = c.resize((160,180), Image.NEAREST)
    out.paste(c,(10+i*190,10))
out.save(r"F:/MCProject/astral_dice_multiloader/work/recon/icon_zoom.png")
print(out.size)
