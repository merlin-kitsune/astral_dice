from PIL import Image
p=r'C:/Users/xmace/.dsh/attachments/v1/objects/95/95c9a42b6c2f97ed0754c1bb0540868eb5e6e261400ce3926fd8b29c59970314'
im=Image.open(p).convert('RGB'); W,H=im.size; print('size',W,H)
px=im.load()
def redys(x0,x1):
    ys=[]
    for y in range(H):
        c=0
        for x in range(x0,min(x1,W)):
            r,g,b=px[x,y]
            if r>140 and g<80 and b<80: c+=1
        if c>=6: ys.append((y,c))
    return ys
h=redys(40,400); s=redys(430,740)
print('hearts rows', h[:4], '...', h[-4:] if h else None)
print('sword rows', s[:4], '...', s[-4:] if s else None)
def span(rs):
    if not rs: return None
    return (rs[0][0], rs[-1][0], sum(y*c for y,c in rs)/sum(c for y,c in rs))
print('hearts span(first,last,centroid)', span(h))
print('sword span(first,last,centroid)', span(s))