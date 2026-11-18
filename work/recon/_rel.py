import re
s=open("work/recon/_rel150.html",encoding="utf-8",errors="replace").read()
# find asset rows: pattern of file name near relative-time datetime
names=re.findall(r"astral_dice-[A-Za-z0-9._+\-]+\.jar", s)
print("names in page:", sorted(set(names)))
for m in re.finditer(r"datetime=\"([0-9T:\-Z]+)\"", s):
    d=m.group(1)[:60]
    if d.startswith("2026-11-02") or d.startswith("2026-10"):
        print("dt:", d)
i=s.find("astral_dice-1.5.0+neoforge_1.21.1.jar")
print("--- ctx ---")
print(re.sub(r"\s+"," ", re.sub(r"<[^>]+>"," ", s[max(0,i-1200):i+600]))[:900])