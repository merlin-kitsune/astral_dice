# -*- coding: utf-8 -*-
import sys
from PIL import Image
im = Image.open(sys.argv[1]).convert("RGB")
x0,y0,x1,y1 = [int(v) for v in sys.argv[2].split(",")]
c = im.crop((x0,y0,x1,y1))
c = c.resize((c.width*2, c.height*2), Image.NEAREST)
c.save(sys.argv[3])
print("saved", c.size)
