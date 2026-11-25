
from PIL import Image
im = Image.open(r"D:/MCProject/astral_dice_multiloader/work/recon/user_screenshot.png").convert("RGB")
crop = im.crop((0,120,788,215))
crop = crop.resize((crop.width*2, crop.height*2), Image.NEAREST)
crop.save(r"D:/MCProject/astral_dice_multiloader/work/recon/zoom_band.png")
print(crop.size)
