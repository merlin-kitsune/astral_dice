# -*- coding: utf-8 -*-
import sys
from PIL import Image
path = sys.argv[1]; name = sys.argv[2]
x0,y0,x1,y1 = [int(v) for v in sys.argv[3].split(",")]
im = Image.open(path).convert("RGB")
c = im.crop((x0,y0,x1,y1))
c = c.resize((c.width*2, c.height*2), Image.NEAREST)
c.save("F:/MCProject/astral_dice_multiloader/work/fix/_crop_out.png")
print("saved", c.size)
