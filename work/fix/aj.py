# -*- coding: utf-8 -*-
import json, sys, urllib.request

URL = "http://127.0.0.1:25585/mcp"

def rpc(payload):
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(URL, data=data, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=120) as r:
        return json.loads(r.read().decode("utf-8"))

def call(name, args):
    resp = rpc({"jsonrpc": "2.0", "id": 1, "method": "tools/call", "params": {"name": name, "arguments": args}})
    res = resp.get("result", {})
    content = res.get("content") or []
    for c in content:
        if c.get("type") == "text":
            try:
                return json.loads(c["text"])
            except Exception:
                return {"raw": c["text"]}
    return res

if __name__ == "__main__":
    mode = sys.argv[1]
    if mode == "status":
        print(json.dumps(call("status", {}), ensure_ascii=False, indent=1))
    elif mode == "eval":
        side = sys.argv[2] if len(sys.argv) > 2 else "client"
        code = sys.stdin.read()
        print(json.dumps(call("eval_js", {"side": side, "code": code}), ensure_ascii=False, indent=1))
    elif mode == "log":
        side = sys.argv[2] if len(sys.argv) > 2 else "client"
        print(json.dumps(call("kubejs_log", {"side": side, "kind": "all", "limit": 40}), ensure_ascii=False, indent=1))
