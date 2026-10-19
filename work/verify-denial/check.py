import json,base64,io,os,sys
p='/mnt/f/mcproject/astral_dice_multiloader-next/models/强化拒止 - Converted.bbmodel'
d=json.load(open(p,encoding='utf-8'))
print('resolution',d.get('resolution'))
src=d['textures'][0]['source']
print('tex name',d['textures'][0].get('name'),'src prefix',src[:30])
raw=base64.b64decode(src.split(',',1)[1])
try:
    from PIL import Image
    im=Image.open(io.BytesIO(raw)).convert('RGBA')
except Exception as e:
    print('NO_PIL',e); sys.exit(3)
W,H=im.size; print('bitmap',W,H)
res=d.get('resolution',{}); uw=res.get('width',64); uh=res.get('height',64)
sx,sy=W/uw,H/uh; print('scale',sx,sy)
px=im.load()
def crop(u1,v1,u2,v2):
    return im.crop((round(u1*sx),round(v1*sy),round(u2*sx),round(v2*sy)))
def diff(a,b,mx=False,my=False):
    a=a.convert('RGBA'); b=b.convert('RGBA')
    if mx: b=b.transpose(Image.FLIP_LEFT_RIGHT)
    if my: b=b.transpose(Image.FLIP_TOP_BOTTOM)
    aa=list(a.getdata()); bb=list(b.getdata()); n=len(aa)
    return round(sum(abs(x0-y0)+abs(x1-y1)+abs(x2-y2)+abs(x3-y3) for (x0,x1,x2,x3),(y0,y1,y2,y3) in zip(aa,bb))/(4*n),2)
A=crop(2,5,14,17); B=crop(16,5,28,17)
A.resize((A.width*10,A.height*10),Image.NEAREST).save('/mnt/f/mcproject/astral_dice_multiloader-next/work/verify-denial/plateN_x10.png')
B.resize((B.width*10,B.height*10),Image.NEAREST).save('/mnt/f/mcproject/astral_dice_multiloader-next/work/verify-denial/plateS_x10.png')
print('sizes',A.size,B.size)
print('plate identity %.2f  mirrorX %.2f  mirrorY %.2f  rot180 %.2f'%(diff(A,B),diff(A,B,True,False),diff(A,B,False,True),diff(A,B,True,True)))
print('selfN mirrorX %.2f mirrorY %.2f'%(diff(A,A,True,False),diff(A,A,False,True)))
print('selfS mirrorX %.2f mirrorY %.2f'%(diff(B,B,True,False),diff(B,B,False,True)))
BA=crop(1,1,25,3); BB=crop(26,1,50,3)
print('bar  identity %.2f  mirrorX %.2f  mirrorY %.2f  rot180 %.2f'%(diff(BA,BB),diff(BA,BB,True,False),diff(BA,BB,False,True),diff(BA,BB,True,True)))
