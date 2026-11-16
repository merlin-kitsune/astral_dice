
from PIL import Image
im = Image.open(r"F:/MCProject/astral_dice_multiloader/work/recon/user_screenshot.png").convert("RGB")
c = im.crop((720,70,790,115)).resize((70*8,45*8), Image.NEAREST)
tex = Image.open(r"F:/MCProject/astral_dice_multiloader/fabric-1.20.1/src/main/resources/assets/astral_dice/textures/gui/attack_full.png").convert("RGBA").resize((9*16,9*16), Image.NEAREST)
out = Image.new("RGB",(c.width+20+tex.width, max(c.height,tex.height)+20),(20,20,20))
out.paste(c,(10,10)); out.paste(tex,(c.width+20,10))
out.save(r"F:/MCProject/astral_dice_multiloader/work/recon/lone_sword_vs_tex.png"); print(out.size)
