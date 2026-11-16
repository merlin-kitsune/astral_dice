import json
for f,r in [("_jobs_41429965923.json","#146"),("_jobs_41430344936.json","#147")]:
    d=json.load(open("work/recon/"+f,encoding="utf-8"))
    for j in d["jobs"]:
        print(r,"job", j["id"], "| conclusion", j["conclusion"], "| started", j["started_at"], "| completed", j["completed_at"])
        bad=[s for s in j["steps"] if s["conclusion"] not in ("success","skipped",None)]
        ok=len([s for s in j["steps"] if s["conclusion"]=="success"])
        print("   steps success:", ok, "| non-success:", [(s["number"],s["name"][:60],s["conclusion"]) for s in bad])