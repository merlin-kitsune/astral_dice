import json
for f in ["_jobs144.json"]:
    d=json.load(open("work/recon/"+f,encoding="utf-8"))
    for j in d["jobs"]:
        print("job", j["id"], j["name"], "| status", j["status"], "| conclusion", j["conclusion"], "| started", j["started_at"], "| completed", j["completed_at"])
        for s in j["steps"]:
            print("  %2d %-70s %-10s %s" % (s["number"], s["name"][:70], s["status"], s["conclusion"]))