import re
def clean(x):
    x=re.sub(r"<[^>]+>"," ",x); return re.sub(r"\s+"," ",x).strip()
s=open("work/recon/_run144.html",encoding="utf-8",errors="replace").read()
pat=re.compile(r"<annotation-message[^>]*>(.*?)</annotation-message>", re.S)
for m in pat.finditer(s):
    t=clean(m.group(1))
    ctx=clean(s[max(0,m.start()-1200):m.start()])
    print("CTX:", ctx[-200:])
    print("   ANN:", t[:400])
    print()