import json
d=json.load(open("work/recon/_emb_0.json",encoding="utf-8"))
def find(o,key,out):
    if isinstance(o,dict):
        for k,v in o.items():
            if k==key: out.append(v)
            find(v,key,out)
    elif isinstance(o,list):
        for v in o: find(v,key,out)
    return out
roots=find(d,"workflowRuns",[])
runs=None
for r in roots:
    cand=r if isinstance(r,list) else r.get("workflowRuns")
    if isinstance(cand,list) and cand and isinstance(cand[0],dict) and "runNumber" in cand[0]:
        runs=cand; break
print("runs found:", len(runs) if runs else None)
print("KEYS of first:", sorted(runs[0].keys()))
print()
target={"41430344936","41429965923","41392466985","41392445400","41392300273"}
for r in runs:
    if str(r.get("id")) in target:
        print(json.dumps(r, ensure_ascii=False)[:1200])
        print()