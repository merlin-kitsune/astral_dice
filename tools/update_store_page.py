#!/usr/bin/env python3
"""Print (and optionally push) the StarEngine store-page text for CurseForge.

CurseForge: PUT /v1/mods/{id} with {"summary", "description"} using a Core API key.

Modrinth publishing was removed from this repository on 2026-10-23 (user ruling:
permanently drop every Modrinth publish chain); this tool no longer touches it.

Default is dry-run. Pass --apply to send write requests.
"""

import argparse
import json
import os
import ssl
import sys
import urllib.error
import urllib.request

UA = "merlin-kitsune/starengine-publisher/1.0.0 (store page updater)"
CF_BASE = "https://api.curseforge.com/v1"
CF_SUMMARY_MAX = 500


def build_opener(proxy):
    ctx = ssl.create_default_context()
    handlers = [urllib.request.HTTPSHandler(context=ctx)]
    if proxy:
        handlers.insert(0, urllib.request.ProxyHandler({"https": proxy, "http": proxy}))
    return urllib.request.build_opener(*handlers)


def request(opener, method, url, headers, payload=None):
    data = json.dumps(payload).encode("utf-8") if payload is not None else None
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with opener.open(req, timeout=60) as resp:
            return resp.status, resp.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as exc:
        return exc.code, exc.read().decode("utf-8", "replace")[:400]
    except Exception as exc:  # network level
        return 0, "%s: %s" % (type(exc).__name__, exc)


def read_summary(path, explicit, limit):
    if explicit:
        text = explicit.strip()
    else:
        blocks = [b.strip() for b in open(path, encoding="utf-8").read().split("\n\n") if b.strip()]
        text = next((b for b in blocks if not b.startswith("#")), "")
        text = " ".join(text.split())
    if len(text) > limit:
        raise SystemExit("[ERR] summary 超长：%d > %d" % (len(text), limit))
    return text


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--body-file", default="docs/store/starengine-page-en.md")
    ap.add_argument("--summary", default=None)
    ap.add_argument("--curseforge-project", default="1755415")
    ap.add_argument("--curseforge-key", default=None)
    ap.add_argument("--try-cf", action="store_true",
                    help="仍然尝试 PUT /v1/mods/{id}（2026-10-20 实测 404，仅供未来重试）")
    ap.add_argument("--proxy", default=None)
    ap.add_argument("--apply", action="store_true")
    args = ap.parse_args(argv)

    body = open(args.body_file, encoding="utf-8").read()
    if "\u2014" in body:
        raise SystemExit("[ERR] body 含 em dash，请先清理")
    cf_summary = read_summary(args.body_file, args.summary, CF_SUMMARY_MAX)

    cf_key = args.curseforge_key or os.environ.get("CURSEFORGE_API_KEY")
    proxy = args.proxy or os.environ.get("HTTPS_PROXY")
    opener = build_opener(proxy)

    print("CurseForge project = %s  summary=%d/%d  body=%d 字符"
          % (args.curseforge_project, len(cf_summary), CF_SUMMARY_MAX, len(body)))
    print("代理 = %s | 写请求 = %s" % (proxy or "直连", "是" if args.apply else "否（dry-run）"))
    print(json.dumps({"curseforge": {"summary": cf_summary}}, ensure_ascii=False, indent=1))
    if not args.apply:
        print("DRY-RUN：未发送任何请求")
        return 0

    if not args.try_cf:
        # 2026-10-20 实测：Core API 对 /v1/mods/{id} 只有 GET，PUT/POST/PATCH/OPTIONS 全 404；
        # Upload API 也没有项目信息端点。公开 API 无法改项目简介/描述。
        print("[SKIP] CurseForge：公开 API 不支持修改项目信息（需作者后台手动粘贴或非公开端点）")
        return 2
    if not cf_key:
        print("[SKIP] CurseForge：缺少 API key（CURSEFORGE_API_KEY）")
        return 2
    status, text = request(opener, "PUT", "%s/mods/%s" % (CF_BASE, args.curseforge_project),
                           {"x-api-key": cf_key, "Accept": "application/json",
                            "Content-Type": "application/json", "User-Agent": UA},
                           {"summary": cf_summary, "description": body})
    print("[CurseForge] HTTP %s %s" % (status, "" if status == 200 else text[:200]))
    return 0 if status == 200 else 1


if __name__ == "__main__":
    sys.exit(main())
