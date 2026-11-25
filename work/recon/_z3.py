
from PIL import Image
im = Image.open(r"D:/MCProject/astral_dice_multiloader/work/recon/user_screenshot.png").convert("RGB")
c = im.crop((0,0,788,150)); c=c.resize((c.width*2,c.height*2), Image.NEAREST)
c.save(r"D:/MCProject/astral_dice_multiloader/work/recon/zoom_top.png"); print(c.size)
