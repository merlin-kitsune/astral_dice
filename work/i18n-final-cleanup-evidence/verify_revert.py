
import subprocess, os, json
ROOT = r"F:/MCProject/astral_dice_multiloader"
KEY = '"tooltip.astral_dice.sign.fanny_active":'
out = []
for rel in ["neoforge-26.1.2/src/main/resources/assets/astral_dice/lang/%s.json" % x for x in ("zh_cn","en_us","ja_jp")]:
    head = subprocess.run(["git","-C",ROOT,"show","HEAD:"+rel], capture_output=True).stdout.decode("utf-8").split("\n")
    cur  = open(os.path.join(ROOT, rel), encoding="utf-8", newline="").read().split("\n")
    h = [l for l in head if l.startswith("  " + KEY)]
    c = [l for l in cur  if l.startswith("  " + KEY)]
    out.append({"file": rel, "head_hits": len(h), "cur_hits": len(c), "byte_identical": h == c})
print(json.dumps(out, ensure_ascii=False, indent=1))
