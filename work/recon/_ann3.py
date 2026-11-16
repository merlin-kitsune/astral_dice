import re,glob
def clean(x):
    x=re.sub(r"<[^>]+>"," ",x); return re.sub(r"\s+"," ",x).strip()
for p in sorted(glob.glob("work/recon/_checks_*.html")):
    s=open(p,encoding="utf-8",errors="replace").read()
    if len(s)<2000: continue
    ar=sorted(set(re.findall(r"/astral_dice/actions/runs/(\d+)", s)))
    cr=sorted(set(re.findall(r"/astral_dice/runs/(\d+)", s)))
    print(p.replace("work/recon/",""))
    print("   actions/runs:", ar)
    print("   check runs  :", cr)
    msgs=[clean(m)[:160] for m in re.findall(r"<annotation-message[^>]*>(.*?)</annotation-message>", s, re.S)]
    for m in msgs: print("     -", m)