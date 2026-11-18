
import subprocess, sys, json, io, os

ROOT = r"F:/MCProject/astral_dice_multiloader"
KEY = "tooltip.astral_dice.sign.fanny_active"
rel_paths = [
    "neoforge-26.1.2/src/main/resources/assets/astral_dice/lang/zh_cn.json",
    "neoforge-26.1.2/src/main/resources/assets/astral_dice/lang/en_us.json",
    "neoforge-26.1.2/src/main/resources/assets/astral_dice/lang/ja_jp.json",
]

def head_blob(rel):
    out = subprocess.run(["git", "-C", ROOT, "show", "HEAD:" + rel], capture_output=True)
    if out.returncode != 0:
        raise SystemExit("git show failed for %s: %s" % (rel, out.stderr.decode("utf-8", "replace")))
    return out.stdout.decode("utf-8")

results = []
for rel in rel_paths:
    abs_p = os.path.join(ROOT, rel)
    raw = open(abs_p, "rb").read()
    if raw[:3] == b"\xef\xbb\xbf":
        raise SystemExit("BOM found in %s" % rel)
    cur = raw.decode("utf-8")
    if "\r" in cur:
        raise SystemExit("CR found in %s; refusing to touch" % rel)
    head = head_blob(rel)

    cur_lines = cur.split("\n")
    head_lines = head.split("\n")

    def find(lines):
        return [i for i, l in enumerate(lines) if l.startswith('  "%s":' % KEY)]

    ci = find(cur_lines); hi = find(head_lines)
    if len(ci) != 1 or len(hi) != 1:
        raise SystemExit("key match count cur=%s head=%s in %s" % (len(ci), len(hi), rel))
    ci = ci[0]; hi = hi[0]
    before = cur_lines[ci]; after = head_lines[hi]
    if before == after:
        results.append({"file": rel, "changed": False, "line": ci + 1})
        continue
    cur_lines[ci] = after
    new = "\n".join(cur_lines)
    # structural invariants vs HEAD-identical expectations
    open(abs_p, "wb").write(new.encode("utf-8"))
    results.append({
        "file": rel,
        "changed": True,
        "line": ci + 1,
        "before": before,
        "after": after,
        "bytes_before": len(raw),
        "bytes_after": len(new.encode("utf-8")),
        "keycount_cur": sum(1 for l in cur.split("\n") if l.lstrip().startswith('"')),
        "keycount_new": sum(1 for l in new.split("\n") if l.lstrip().startswith('"')),
    })

print(json.dumps(results, ensure_ascii=False, indent=1))
