# 严格复算检查器（只读）：RELEASE150 两线 27 条断言逐条重判
# 用法: python work/strict-recheck-checker.py   -> 写出 work/strict-recheck-raw.json
# 说明: 只读用例 JSON / 日志 / 截图 / jar / 状态文件；不写入任何被测目录。
import io, json, os, re, zipfile, datetime

ROOT = "F:/MCProject/astral_dice_multiloader"
OUT = {}

def jload(p):
    return json.load(io.open(os.path.join(ROOT, p), "r", encoding="utf-8"))

def load_lines(p):
    txt = io.open(p, "r", encoding="utf-8", errors="replace", newline="").read()
    lines = txt.split(chr(10))
    return [l[:-1] if l.endswith(chr(13)) else l for l in lines]

def win_start(p, offset):
    data = open(p, "rb").read()
    return data[:offset].count(b"\n")
# ---- 1) 逐行复判 log / absent ----
out = {}
for tag, case, log, offset in [
    ("1.21.1", "scripts/test/cases/RELEASE150-1.21.1.json", "run/1.21.1/logs/latest.log", 1051236),
    ("1.20.1", "scripts/test/cases/RELEASE150-1.20.1.json", "run/1.20.1/logs/latest.log", 6434375),
]:
    cj = jload(case)
    lp = os.path.join(ROOT, log)
    lines = load_lines(lp)
    ws = win_start(lp, offset)
    res = {"case": case, "log": log, "window_start_line": ws + 1, "total_lines": len(lines), "log_hits": [], "absent": []}
    for i, a in enumerate(cj["asserts"], 1):
        t = a.get("type")
        if t == "log":
            pat = a["pattern"]
            scope = a.get("scope") or "case"
            start = 0 if scope == "whole" else ws
            rx = re.compile(pat)
            hits = [n + 1 for n in range(start, len(lines)) if rx.search(lines[n])]
            res["log_hits"].append({"idx": i, "scope": scope, "pattern": pat, "hit_count": len(hits),
                                    "hit_lines": hits,
                                    "hit_platform": [lines[h-1].startswith(chr(91)) for h in hits],
                                    "hit_text": [lines[h-1] for h in hits[:4]]})
        elif t == "absent":
            rx = re.compile(a["pattern"])
            hits = [n + 1 for n in range(ws, len(lines)) if rx.search(lines[n])]
            res["absent"].append({"idx": i, "pattern": a["pattern"], "hit_count": len(hits), "hit_lines": hits[:10]})
    out[tag] = res
OUT["log"] = out
# ---- 2) eval 三分支复判 ----
OPS = {"min", "max", "gte", "lte", "regex", "set", "contains"}
aj = jload("scripts/test/cases/.mt_autojs.json")

def apply_op(op, spec, val):
    if op == "regex":
        if not isinstance(val, str):
            return (None, "regex 只能作用于字符串（实际 " + type(val).__name__ + "）")
        return (bool(re.search(spec, val)), "regex 作用于字符串")
    return (None, "未实现: " + op)

def eval_expect(expect, value):
    log = []
    if isinstance(expect, dict) and len(expect) == 1 and list(expect.keys())[0] in OPS:
        op = list(expect.keys())[0]
        ok, why = apply_op(op, expect[op], value)
        log.append({"branch": "A-顶层操作符", "op": op, "result": ok, "why": why})
        return ok, log
    if isinstance(expect, dict):
        if not isinstance(value, dict):
            log.append({"branch": "B-字段级", "result": False,
                        "why": "实际值非对象（" + type(value).__name__ + "），字段级断言不可求值"})
            return False, log
        allok = True
        for k, spec in expect.items():
            if k not in value:
                log.append({"branch": "B-字段级", "field": k, "result": False, "why": "缺字段"})
                allok = False
                continue
            v = value[k]
            if isinstance(spec, dict) and len(spec) == 1 and list(spec.keys())[0] in OPS:
                op = list(spec.keys())[0]
                ok, why = apply_op(op, spec[op], v)
            else:
                ok, why = (v == spec), "精确值比较"
            log.append({"branch": "B-字段级", "field": k, "result": ok, "why": why})
            allok = allok and bool(ok)
        return allok, log
    ok = (value == expect)
    log.append({"branch": "C-精确值", "result": ok, "why": "整体相等"})
    return ok, log

ev = []
for v in ["1.21.1", "1.20.1"]:
    for i, rec in enumerate(aj["versions"][v]["cases"]["RELEASE150-" + v]["evals"]):
        if rec.get("op") != "assert:eval":
            continue
        expect = json.loads(rec["expect"])
        value = json.loads(rec["value"])
        ok, log = eval_expect(expect, value)
        ev.append({"version": v, "slot": i, "from": rec.get("save_as"), "harness": rec.get("outcome"),
                   "expect": expect, "value": value, "value_type": type(value).__name__,
                   "strict": bool(ok), "branches": log})
OUT["eval"] = ev
# ---- 3) jar / crash / 截图 / 窗口 ----
def recount(p):
    z = zipfile.ZipFile(p)
    allnames = z.namelist()
    files = [n for n in allnames if not n.endswith("/")]
    models = len([n for n in files if n.startswith("assets/astral_dice/models/item/") and n.endswith(".json")])
    rec_pl = len([n for n in files if n.startswith("data/astral_dice/recipes/") and n.endswith(".json")])
    rec_sg = len([n for n in files if n.startswith("data/astral_dice/recipe/") and n.endswith(".json")])
    signs = len([n for n in files if re.search("item/sign/[A-Za-z]+SignItem[.]class$", n)])
    ver = {}
    for cand in ["META-INF/neoforge.mods.toml", "META-INF/mods.toml"]:
        if cand in files:
            t = z.read(cand).decode("utf-8", "replace")
            mm = re.search('version="([^"]+)"', t)
            ver[cand] = mm.group(1) if mm else None
    return {"jar": os.path.basename(p), "bytes": os.path.getsize(p),
            "entries_with_dirs": len(allnames), "entries_files_only": len(files),
            "models": models, "recipes_plural": rec_pl, "recipes_singular": rec_sg,
            "signs": signs, "toml_version": ver}

OUT["jar"] = {}
for tag, sub in [("1.21.1", "neoforge-1.21.1"), ("1.20.1", "forge-1.20.1")]:
    libdir = os.path.join(ROOT, sub, "build", "libs")
    cands = [os.path.join(libdir, n) for n in os.listdir(libdir)
             if n.startswith("astral_dice-") and n.endswith(".jar") and not re.search("-(sources|dev|slim)[.]jar$", n)]
    cands.sort(key=lambda p: os.path.getmtime(p))
    p = cands[-1]
    rec = recount(p)
    rec["candidates"] = [(os.path.basename(c), os.path.getsize(c),
                          datetime.datetime.fromtimestamp(os.path.getmtime(c)).strftime("%Y-%m-%d %H:%M:%S")) for c in cands]
    rec["picked_mtime"] = datetime.datetime.fromtimestamp(os.path.getmtime(p)).strftime("%Y-%m-%d %H:%M:%S")
    OUT["jar"][tag] = rec

OUT["crash"] = {}
for tag, ver in [("1.21.1", "1.21.1"), ("1.20.1", "1.20.1")]:
    d = os.path.join(ROOT, "run", ver, "crash-reports")
    if os.path.isdir(d):
        OUT["crash"][tag] = {"exists": True, "files": sorted([(n, os.path.getsize(os.path.join(d, n))) for n in os.listdir(d)])}
    else:
        OUT["crash"][tag] = {"exists": False}

OUT["shots"] = {}
for ver in ["1.21.1", "1.20.1"]:
    sd = os.path.join(ROOT, "run", ver, "screenshots")
    files = []
    for n in ["abyss_reagent_tooltip.png", "chip_shop_ui.png", "release150_guide.png"]:
        p = os.path.join(sd, n)
        if os.path.isfile(p):
            st = os.stat(p)
            files.append({"name": n, "bytes": st.st_size,
                          "mtime": datetime.datetime.fromtimestamp(st.st_mtime).strftime("%Y-%m-%d %H:%M:%S.%f")[:-3],
                          "mtime_ts": st.st_mtime})
        else:
            files.append({"name": n, "exists": False})
    mj = json.load(io.open(os.path.join(sd, ".mt_shots.json"), "r", encoding="utf-8"))
    entries = mj.get("shots") if isinstance(mj, dict) else mj
    rel = [e for e in entries if isinstance(e, dict) and str(e.get("tag", "")) in
           ("abyss_reagent_tooltip", "chip_shop_ui", "release150_guide")]
    OUT["shots"][ver] = {"files": files, "run_id": (mj.get("run_id") if isinstance(mj, dict) else None),
                         "entry_total": len(entries), "matching_entries": rel[-6:]}

snap = jload("scripts/test/cases/.mt_snapshot.json")
OUT["window"] = {}
for v in ["1.21.1", "1.20.1"]:
    e = snap["versions"][v]
    OUT["window"][v] = {"offset_case": e["offsets"]["latest.log"], "offset_launch": e["launch_offsets"]["latest.log"],
                        "anchor": e.get("anchors", {}).get("latest.log"),
                        "anchor_mtime": datetime.datetime.fromtimestamp(e["anchors"]["latest.log"]["mtime_ms"] / 1000.0).strftime("%Y-%m-%d %H:%M:%S.%f")[:-3],
                        "crash_baseline": e.get("crash_baseline")}

io.open(os.path.join(ROOT, "work/strict-recheck-raw.json"), "w", encoding="utf-8").write(
    json.dumps(OUT, ensure_ascii=False, indent=1))
print("ok: log+eval+jar+crash+shots+window ->", os.path.join(ROOT, "work/strict-recheck-raw.json"))