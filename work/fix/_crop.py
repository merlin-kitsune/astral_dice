# -*- coding: utf-8 -*-
import sys
from PIL import Image
src = sys.argv[1]; out = sys.argv[2]
x0,y0,x1,y1 = [int(v) for v in sys.argv[3:7]]
im = Image.open(src).convert("RGB").crop((x0,y0,x1,y1))
im = im.resize((im.width*2, im.height*2), Image.NEAREST)
im.save(out)
print(out, im.size)
