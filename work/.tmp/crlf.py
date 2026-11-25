
p = r"D:/MCProject/astral_dice_multiloader/work/fix-fabric-defensebar-position.md"
raw = open(p, 'r', encoding='utf-8', newline='').read()
t = raw.replace('\r\n','\n').replace('\r','\n')
out = t.replace('\n','\r\n')
open(p,'w',encoding='utf-8',newline='').write(out)
rest = out.replace('\r\n','')
print("bytes", len(out.encode('utf-8')), "CRLF", out.count('\r\n'), "bareLF", rest.count('\n'), "loneCR", rest.count('\r'))
