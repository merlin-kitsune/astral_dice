
import os, json
base = "D:/MCProject/astral_dice_multiloader"
lines = {
 "fabric-1.20.1": ("recipes", "advancements"),
 "forge-1.20.1": ("recipes", "advancements"),
 "neoforge-1.21.1": ("recipe", "advancement"),
 "neoforge-26.1.2": ("recipe", "advancement"),
}
COND_KEYS = ("fabric:load_conditions", "neoforge:conditions", "forge:conditions", "conditions")

def walk(d):
    for root, dirs, files in os.walk(d):
        for f in files:
            if f.endswith(".json"):
                yield os.path.join(root, f)

def cond(obj):
    return any(k in obj for k in COND_KEYS)

res = {}
for line, (rdir, adir) in lines.items():
    rp = base + "/" + line + "/src/generated/resources/data/astral_dice/" + rdir
    ap = base + "/" + line + "/src/generated/resources/data/astral_dice/" + adir
    r_tot = r_cond = r_chip = r_chipc = 0
    r_chipset = set()
    for p in walk(rp):
        o = json.load(open(p, encoding="utf-8"))
        name = os.path.basename(p)[:-5]
        r_tot += 1
        c = cond(o)
        r_cond += c
        if name.endswith("_chip"):
            r_chip += 1; r_chipc += c; r_chipset.add(name)
    a_tot = a_cond = a_chip = a_chipc = 0
    a_chipset = set()
    for p in walk(ap):
        o = json.load(open(p, encoding="utf-8"))
        name = os.path.basename(p)[:-5]
        a_tot += 1
        c = cond(o)
        a_cond += c
        if name.endswith("_chip"):
            a_chip += 1; a_chipc += c; a_chipset.add(name)
    res[line] = dict(r_tot=r_tot, r_cond=r_cond, r_chip=r_chip, r_chipc=r_chipc,
                     a_tot=a_tot, a_cond=a_cond, a_chip=a_chip, a_chipc=a_chipc,
                     r_chips=r_chipset, a_chips=a_chipset)
    print(f"{line:18s} recipe {r_tot}/cond {r_cond} | chip {r_chip}/cond {r_chipc} || adv {a_tot}/cond {a_cond} | chipadv {a_chip}/cond {a_chipc}")

ref = res["neoforge-1.21.1"]
for line in res:
    if line == "neoforge-1.21.1":
        continue
    print("\n== diff vs neoforge-1.21.1:", line)
    print(" recipe chips only-here:", sorted(res[line]["r_chips"] - ref["r_chips"]))
    print(" recipe chips only-ref :", sorted(ref["r_chips"] - res[line]["r_chips"]))
    print(" adv chips only-here   :", sorted(res[line]["a_chips"] - ref["a_chips"]))
    print(" adv chips only-ref    :", sorted(ref["a_chips"] - res[line]["a_chips"]))
print("\n== chip count equality across lines:", {k: (v["r_chip"], v["a_chip"]) for k, v in res.items()})
