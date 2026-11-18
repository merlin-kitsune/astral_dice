import re,sys,io
p="work/recon/_checks_be6.html"
s=open(p,encoding="utf-8",errors="replace").read()
i=s.find("missing jar")
print("HTML len",len(s),"idx",i)
win=s[max(0,i-1500):i+900]
txt=re.sub(r"<[^>]+>"," ",win)
txt=re.sub(r"\s+"," ",txt)
print("--- context ---")
print(txt)
print("--- annotation markers ---")
for m in re.finditer(r"annotations", s):
    print(s[max(0,m.start()-120):m.start()+160].replace("\n"," ")[:280])
    print("~~~~")