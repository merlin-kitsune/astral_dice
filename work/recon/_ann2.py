import re,glob,io
def clean(x):
    x=re.sub(r"<[^>]+>"," ",x)
    return re.sub(r"\s+"," ",x).strip()
for p in sorted(glob.glob("work/recon/_checks_*.html")):
    s=open(p,encoding="utf-8",errors="replace").read()
    print("="*100)
    print("FILE",p,"len",len(s))
    if len(s)<2000:
        print("  (too small / error body)"); continue
    runs=sorted(set(re.findall(r"/merlin-kitsune/astral_dice/runs/(\d+)", s)))
    print("  check-run ids:", runs)
    msgs=re.findall(r"<annotation-message[^>]*>(.*?)</annotation-message>", s, re.S)
    print("  annotation count:", len(msgs))
    for m in msgs:
        print("   ANN:", clean(m)[:400])
    i=s.find("missing jar")
    if i>0:
        print("   CTX:", clean(s[max(0,i-700):i+300]))