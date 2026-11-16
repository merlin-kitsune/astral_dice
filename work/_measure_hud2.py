from PIL import Image
p=r'C:/Users/xmace/.dsh/attachments/v1/objects/95/95c9a42b6c2f97ed0754c1bb0540868eb5e6e261400ce3926fd8b29c59970314'
im=Image.open(p).convert('RGB'); W,H=im.size; px=im.load()
def rows(pred, x0, x1, need=4):
    out=[]
    for y in range(H):
        c=0
        for x in range(x0,min(x1,W)):
            r,g,b=px[x,y]
            if pred(r,g,b): c+=1
        if c>=need: out.append(y)
    return out
red=lambda r,g,b: r>140 and g<80 and b<80
white=lambda r,g,b: r>190 and g>190 and b>190
print('hearts(left, red) rows:', rows(red,40,410,6))
print('swords(right, red) rows:', rows(red,430,740,6))
print('white-ish x40-120 rows:', rows(white,40,120,3)[:20])
def xrange(pred,y0,y1,need=3):
    xs=[]
    for x in range(W):
        c=0
        for y in range(y0,y1):
            r,g,b=px[x,y]
            if pred(r,g,b): c+=1
        if c>=need: xs.append(x)
    return (xs[0],xs[-1]) if xs else None
print('swords x extent:', xrange(red,162,175))
print('hearts x extent:', xrange(red,162,175))