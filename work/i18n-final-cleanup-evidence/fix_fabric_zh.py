
import json, os, shutil, sys

ROOT = r"D:/MCProject/astral_dice_multiloader"
rel = "fabric-1.20.1/src/main/resources/assets/astral_dice/lang/zh_cn.json"
abs_p = os.path.join(ROOT, rel)
bak = abs_p + ".bak-i18n-final"
shutil.copyfile(abs_p, bak)

raw = open(abs_p, "rb").read()
if raw[:3] == b"\xef\xbb\xbf":
    raise SystemExit("BOM present; abort")
text = raw.decode("utf-8")
lines = text.split("\n")

edits = [
    ("tooltip.astral_dice.sign.parunan.active", "套现", "网络购物"),
    ("tooltip.astral_dice.sign.fanny.active", "麻烦制造者", "事件选择"),
]
report = []
for key, old_v, new_v in edits:
    idx = [i for i, l in enumerate(lines) if l.startswith('  "%s":' % key)]
    if len(idx) != 1:
        raise SystemExit("key %s matched %d lines" % (key, len(idx)))
    i = idx[0]
    cur = lines[i]
    token = '"%s",' % old_v
    if cur.count(token) != 1:
        raise SystemExit("value %r not found exactly once in: %r" % (old_v, cur))
    if old_v in new_v or new_v in cur:
        raise SystemExit("unexpected overlap")
    new_line = cur.replace(token, '"%s",' % new_v, 1)
    lines[i] = new_line
    report.append({
        "key": key, "line": i + 1,
        "before": cur.rstrip("\r"),
        "after": new_line.rstrip("\r"),
        "had_cr": cur.endswith("\r"),
    })

new_text = "\n".join(lines)
new_bytes = new_text.encode("utf-8")
# invariants
assert b"\r\n" in new_bytes
old_crlf = raw.count(b"\r\n"); new_crlf = new_bytes.count(b"\r\n")
old_bare = raw.count(b"\n") - old_crlf; new_bare = new_bytes.count(b"\n") - new_crlf
prev = json.loads(raw.decode("utf-8")); newj = json.loads(new_text)
assert set(prev) == set(newj), "key set changed"
changed = [k for k in prev if prev[k] != newj[k]]
print(json.dumps({
    "edits": report,
    "crlf_before": old_crlf, "crlf_after": new_crlf,
    "bare_lf_before": old_bare, "bare_lf_after": new_bare,
    "keycount": len(prev),
    "changed_keys": changed,
    "trailing": repr(new_text[-6:]),
}, ensure_ascii=False, indent=1))
open(abs_p, "wb").write(new_bytes)
