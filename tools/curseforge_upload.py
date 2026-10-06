#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把构建产物上传到 CurseForge（纯标准库，无第三方依赖）。

=====================================================================
凭据
=====================================================================
按以下顺序解析（先命中者生效）：
  1. ``--token <token>``
  2. 环境变量 ``CURSEFORGE_TOKEN``（CI 里走 GitHub secret）
  3. ``<仓库根>/.curseforge/token``（**已被 .gitignore 排除**，本地开发用）

⚠️ token 是账号级凭据，**绝对不要**写进任何入库文件。

=====================================================================
用法
=====================================================================
    # 先看计划（不发请求）
    python tools/curseforge_upload.py --jar build/libs/astral_dice-1.3.5+neoforge_1.21.1.jar --dry-run

    # 真上传
    python tools/curseforge_upload.py --jar build/libs/astral_dice-1.3.5+neoforge_1.21.1.jar

    # 一次传多个（各线产物可混在一起传）
    python tools/curseforge_upload.py \
        --jar build/libs/astral_dice-1.3.5+neoforge_1.21.1.jar \
        --jar build/libs/astral_dice-1.3.5+forge_1.20.1.jar \
        --jar build/libs/astral_dice-1.3.7-beta.2+neoforge_26.1.2.jar

    # 第四条线 fabric（2026-10-01 起并入 multi-main）：版本号带 -alpha ⇒ 自动走 alpha 渠道
    python tools/curseforge_upload.py \
        --jar build/libs/astral_dice-1.3.7-alpha.2+fabric_1.20.1.jar \
        --changelog <该线自备的发布说明.md>

    # 本机直连会被 Cloudflare 拦（403）；走本地代理即可
    python tools/curseforge_upload.py --jar <...> --proxy http://127.0.0.1:7897

=====================================================================
必需依赖关系（relations）—— 2026-10-01 新增
=====================================================================
发布的文件**默认会带上** `tools/curseforge.json` 里该线声明的 `relations`（按 loader + MC 版本匹配该线的
`key`）。schema 权威 = CurseForge 官方《Upload API》文档：

    relations: { projects: [ { slug: "mantle", projectID: "74924", type: "requiredDependency" } ] }

* `type` 只能取 5 个值：`embeddedLibrary` / `incompatible` / `optionalDependency` /
  `requiredDependency` / `tool`（**没有** `include`）。
* `slug` 与 `projectID` 二者至少给一个；同时给时 `projectID` 用于**精确匹配**。
  🚨 **`projectID` 必须是 Integer** —— 官方文档的示例把它写成字符串（`projectID: "74924"`）是**错的**，
  照抄会 400：`Invalid type. Expected Integer but got String. Path 'relations.projects[0].projectID'`
  （2026-10-01 实测）。本项目：配置里写**数字**，代码再统一转 int（并容忍配置写成字符串）。
* 声明 `requiredDependency` 后，CurseForge 启动器会在安装时**自动带上**该前置 —— 这正是
  「硬前置缺失」在玩家侧的真实表现（模组自己会拒绝启动，但玩家不知道要装什么）。

给**已发布**的文件补关系（无需重传、不会产生同名重复）：

    python tools/curseforge_upload.py --update-file 9024345 --line forge_1.20.1 --proxy http://127.0.0.1:7897

`--update-file` 走官方《Project File Management API》的 `POST /projects/{id}/update-file`
（metadata 带 `fileID`，**同样支持 relations**；「不含任何要改的字段会被拒」）。
⚠️ 这条路径是给「文件已发布但漏了 relations」用的；**新版本照常走 `--jar` 上传**即可，无需两次调用。

版本号 / 加载器 / MC 版本一律**从 jar 文件名解析**（``astral_dice-<版本>+<加载器>_<MC版本>.jar``），
release 类型由版本号里的 ``-alpha`` / ``-beta`` / ``-rc`` 后缀决定（无后缀 ⇒ release）。
可用 ``--mc-version`` / ``--loader`` / ``--release-type`` / ``--display-name`` 显式覆盖。

发布说明默认取 ``release/<基础版本>/PLAYER_CHANGELOG.md``（英文，面向 CurseForge 国际社区）；
不存在时退回 ``PLAYER_CHANGELOG_ZH.md``；也可用 ``--changelog <文件>`` 显式指定。

=====================================================================
CurseForge 上传 API 要点（2026-09-30 实测）
=====================================================================
* **站点域必须是 ``minecraft.curseforge.com``** —— 换成 ``www.curseforge.com`` 会拿到
  **另一个游戏**的版本列表（实测返回 200 但内容是别的游戏）。
* 认证头 = ``X-Api-Token``（也可用 ``?token=`` 查询参数，本脚本用头）。
* 上传端点 = ``POST /api/projects/{projectId}/upload-file``，``multipart/form-data``，
  字段 = ``metadata``（JSON 字符串）+ ``file``（jar 本体）。成功返回 ``{"id": <fileId>}``。
* 版本 id 一律**动态解析**（``/api/game/version-types`` + ``/api/game/versions``），
  不要硬编码 —— CF 侧会新增/调整；解析结果缓存到 ``.curseforge/versions.json``。
* ``gameVersions`` 提交四件套：``Client`` + ``Server`` + MC 版本 + 加载器（与项目已有文件口径一致）。
* ⚠️ 请求会被 Cloudflare 拦代理/脚本 UA ⇒ 必须带浏览器 User-Agent（脚本已内置）。
"""

import argparse
import json
import os
import pathlib
import re
import ssl
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

API_BASE = "https://minecraft.curseforge.com/api"
DEFAULT_PROJECT_ID = "1662159"  # 兜底值；正式绑定见 tools/curseforge.json
BINDING_FILE = "tools/curseforge.json"
UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
JAR_RE = re.compile(r"^astral_dice-(?P<ver>[^+]+)\+(?P<loader>[a-z]+)_(?P<mc>.+)\.jar$")
CACHE_DAYS = 7
# CurseForge 官方文档给出的 relation type 全集（没有 include 这一项）。
RELATION_TYPES = ("requiredDependency", "optionalDependency", "embeddedLibrary", "incompatible", "tool")

REPO_ROOT = pathlib.Path(__file__).resolve().parent.parent
CACHE_DIR = REPO_ROOT / ".curseforge"
VERSIONS_CACHE = CACHE_DIR / "versions.json"


# ---------------------------------------------------------------- 项目绑定
def load_binding():
    """读 tools/curseforge.json（**项目 id 的正式落点**，2026-10-01 用户指定）。

    该文件**入库**，因此只放非敏感信息（projectId / slug / 站点 / 四线映射），
    token 一律走 .curseforge/token 或环境变量。
    ⚠️ 四线（2026-10-01 起）= neoforge-1.21.1 / forge-1.20.1 / neoforge-26.1.2 / **fabric-1.20.1**；
    fabric 那条带 `-alpha.x` ⇒ `infer_release_type` 自动判为 **alpha 渠道**
    （与 26.1.2 的 `-beta.x` ⇒ beta 渠道同构）。
    """
    p = REPO_ROOT / BINDING_FILE
    if not p.is_file():
        return {}
    try:
        return json.loads(p.read_text(encoding="utf-8"))
    except Exception as e:
        raise SystemExit(f"[ERR] {BINDING_FILE} 解析失败：{e}")


# ---------------------------------------------------------------- 凭据
def resolve_token(explicit):
    if explicit:
        return explicit.strip(), "--token"
    env = (os.environ.get("CURSEFORGE_TOKEN") or "").strip()
    if env:
        return env, "环境变量 CURSEFORGE_TOKEN"
    f = CACHE_DIR / "token"
    if f.is_file():
        return f.read_text(encoding="utf-8").strip(), str(f.relative_to(REPO_ROOT))
    raise SystemExit(
        "[ERR] 未找到 CurseForge token。三种方式任选：\n"
        "      --token <t>  |  环境变量 CURSEFORGE_TOKEN  |  写成 .curseforge/token")


# ---------------------------------------------------------------- HTTP
def make_opener(proxy):
    handlers = []
    if proxy:
        handlers.append(urllib.request.ProxyHandler({"http": proxy, "https": proxy}))
    ctx = ssl.create_default_context()
    handlers.append(urllib.request.HTTPSHandler(context=ctx))
    return urllib.request.build_opener(*handlers)


def api_get(opener, path, token):
    req = urllib.request.Request(API_BASE + path, headers={
        "X-Api-Token": token, "User-Agent": UA, "Accept": "application/json"})
    with opener.open(req, timeout=60) as r:
        return json.loads(r.read().decode("utf-8"))


def api_upload(opener, project_id, token, metadata, jar_path):
    boundary = "----AstralDiceBoundary" + uuid.uuid4().hex
    meta = json.dumps(metadata, ensure_ascii=False).encode("utf-8")
    data = b"".join([
        f"--{boundary}\r\n".encode(),
        b'Content-Disposition: form-data; name="metadata"\r\n',
        b"Content-Type: application/json\r\n\r\n",
        meta, b"\r\n",
        f"--{boundary}\r\n".encode(),
        f'Content-Disposition: form-data; name="file"; filename="{jar_path.name}"\r\n'.encode(),
        b"Content-Type: application/java-archive\r\n\r\n",
        jar_path.read_bytes(), b"\r\n",
        f"--{boundary}--\r\n".encode(),
    ])
    req = urllib.request.Request(
        f"{API_BASE}/projects/{project_id}/upload-file", data=data, method="POST",
        headers={"X-Api-Token": token, "User-Agent": UA,
                 "Content-Type": f"multipart/form-data; boundary={boundary}",
                 "Content-Length": str(len(data))})
    try:
        with opener.open(req, timeout=600) as r:
            return r.status, r.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")


def api_post_metadata(opener, token, url, metadata):
    """只带 `metadata` 字段的 multipart POST（update-file 用；不带 file）。"""
    boundary = "----AstralDiceBoundary" + uuid.uuid4().hex
    meta = json.dumps(metadata, ensure_ascii=False).encode("utf-8")
    data = b"".join([
        f"--{boundary}\r\n".encode(),
        b'Content-Disposition: form-data; name="metadata"\r\n',
        b"Content-Type: application/json\r\n\r\n",
        meta, b"\r\n",
        f"--{boundary}--\r\n".encode(),
    ])
    req = urllib.request.Request(url, data=data, method="POST", headers={
        "X-Api-Token": token, "User-Agent": UA,
        "Content-Type": f"multipart/form-data; boundary={boundary}",
        "Content-Length": str(len(data))})
    try:
        with opener.open(req, timeout=600) as r:
            return r.status, r.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")


# ---------------------------------------------------------------- 版本 id
def load_catalog(opener, token, force=False):
    """返回 (catalog, 来源说明)。"""
    if not force and VERSIONS_CACHE.is_file():
        age = time.time() - VERSIONS_CACHE.stat().st_mtime
        if age < CACHE_DAYS * 86400:
            return (json.loads(VERSIONS_CACHE.read_text(encoding="utf-8")),
                    f"缓存 {VERSIONS_CACHE.relative_to(REPO_ROOT)}（{age / 3600:.1f} 小时前）")
    types = {t["id"]: t["name"] for t in api_get(opener, "/game/version-types", token)}
    versions = api_get(opener, "/game/versions", token)
    # ⚠️ 刚从 API 拿到的 types 是 **int 键**，而读缓存回来的是 str 键 ⇒ 统一成 str，
    #    否则首次运行（无缓存）时按 str 查找永远落空、报「没有该 MC 版本」。
    cat = {"fetchedAt": int(time.time()),
           "types": {str(k): v for k, v in types.items()},
           "versions": versions}
    CACHE_DIR.mkdir(exist_ok=True)
    VERSIONS_CACHE.write_text(json.dumps(cat, ensure_ascii=False), encoding="utf-8")
    return cat, f"已从 CurseForge 拉取 → {VERSIONS_CACHE.relative_to(REPO_ROOT)}"


def resolve_ids(cat, mc, loader):
    """返回 (gameVersionIds, 明细)。四件套 = Client + Server + MC 版本 + 加载器。"""
    types = cat["types"]
    out, detail = [], {}

    def pick(type_name_exact=None, type_name_prefix=None, version_name=None, ci=False):
        for v in cat["versions"]:
            tname = types.get(str(v["gameVersionTypeID"]), "")
            if type_name_exact is not None and tname != type_name_exact:
                continue
            if type_name_prefix is not None and not tname.startswith(type_name_prefix):
                continue
            a, b = (v["name"].lower(), version_name.lower()) if ci else (v["name"], version_name)
            if a == b:
                return v
        return None

    for env in ("Client", "Server"):
        v = pick(type_name_exact="Environment", version_name=env)
        if v:
            out.append(v["id"]); detail[env] = v["id"]
    v = pick(type_name_prefix="Minecraft ", version_name=mc)
    if not v:
        raise SystemExit(f"[ERR] CurseForge 上没有 MC 版本 {mc!r}（可用 --refresh-versions 重取后再试）")
    out.append(v["id"]); detail[mc] = v["id"]
    v = pick(type_name_exact="Modloader", version_name=loader, ci=True)
    if not v:
        raise SystemExit(f"[ERR] CurseForge 上没有加载器 {loader!r}")
    out.append(v["id"]); detail[loader] = v["id"]
    return out, detail


# ---------------------------------------------------------------- 元数据
def parse_jar(jar):
    m = JAR_RE.match(jar.name)
    if not m:
        raise SystemExit(f"[ERR] jar 名不符合 astral_dice-<版本>+<加载器>_<MC版本>.jar：{jar.name}")
    return m.group("ver"), m.group("loader"), m.group("mc")


def infer_release_type(ver):
    v = ver.lower()
    if "-alpha" in v:
        return "alpha"
    if "-beta" in v or "-rc" in v or "-pre" in v:
        return "beta"
    return "release"


def rel_display(p):
    """展示用路径：在仓库内则给相对路径，否则原样给出。

    ⚠️ 不能无条件 `p.relative_to(REPO_ROOT)`：`--changelog` 传的是**相对路径**时
    （脚本自身文档示例就是这种写法），未 resolve 的 Path 会让 relative_to 抛
    ValueError，而该打印发生在**上传之前** ⇒ 整条命令（含 `--dry-run`）在「打印计划」
    阶段就崩掉，表现为「`--changelog` 这个功能是坏的」。绝对路径之所以没暴露它，
    只是因为仓库内的绝对路径恰好能满足 relative_to。
    """
    try:
        return p.relative_to(REPO_ROOT)
    except ValueError:
        return p


def find_line(binding, key=None, loader=None, mc=None):
    """在 `tools/curseforge.json` 的 lines 里定位一条线：优先用 `key`，否则按 loader+mc 唯一匹配。"""
    lines = binding.get("lines") or []
    if key:
        for ln in lines:
            if ln.get("key") == key:
                return ln
        raise SystemExit(f"[ERR] {BINDING_FILE} 的 lines 里没有 key={key!r}")
    if loader and mc:
        hit = [ln for ln in lines if ln.get("loader") == loader and ln.get("mc") == mc]
        if len(hit) == 1:
            return hit[0]
        if len(hit) > 1:
            raise SystemExit(f"[ERR] loader={loader} mc={mc} 命中 {len(hit)} 条线，请用 --line 指定其 key")
    return None


def relations_payload(line, disabled=False):
    """把该线声明的 relations 规范化成上传 API 要的结构；无声明或 `--no-relations` 时返回 None。

    ⚠️ schema 以 CurseForge 官方《Upload API》文档为准（见模块 docstring），不要凭记忆改写：
        relations: { projects: [ { slug, projectID(字符串), type } ] }
    """
    if disabled or not line:
        return None
    rels = line.get("relations") or []
    if not rels:
        return None
    projects = []
    for r in rels:
        rtype = r.get("type")
        if rtype not in RELATION_TYPES:
            raise SystemExit(f"[ERR] 未知的 relation type {rtype!r}（允许：{', '.join(RELATION_TYPES)}）")
        item = {}
        if r.get("slug"):
            item["slug"] = r["slug"]
        if r.get("projectID") is not None:
            # 🚨 服务端 schema 要 **Integer**（官方文档示例写成字符串是错的，照抄会 400：
            #    "Invalid type. Expected Integer but got String. Path 'relations.projects[n].projectID'"）
            #    ⇒ 这里统一转 int；配置若写成字符串也照转，只有非数字才原样透传（由服务端报错）。
            pid = str(r["projectID"]).strip()
            item["projectID"] = int(pid) if pid.isdigit() else r["projectID"]
        if len(item) == 0:
            raise SystemExit("[ERR] relation 至少要给 slug 或 projectID")
        item["type"] = rtype
        projects.append(item)
    return {"projects": projects}


def describe_relations(payload):
    if not payload:
        return "（无）"
    return ", ".join(
        "%s%s=%s" % (p.get("slug", "?"),
                     "(%s)" % p["projectID"] if p.get("projectID") else "",
                     p["type"])
        for p in payload["projects"])


def pick_changelog(ver, explicit):
    if explicit:
        # ⚠️ 必须 resolve()：把相对路径按**当前工作目录**解析成绝对路径，
        #    否则上面 rel_display 会退化（且与 --jar 的处理口径不一致）。
        p = pathlib.Path(explicit).resolve()
        if not p.is_file():
            raise SystemExit(f"[ERR] 指定的更新日志不存在：{p}")
        return p
    base = ver.split("-")[0]
    for name in ("PLAYER_CHANGELOG.md", "PLAYER_CHANGELOG_ZH.md"):
        p = REPO_ROOT / "release" / base / name
        if p.is_file():
            return p
    return None


# ---------------------------------------------------------------- main
def main():
    ap = argparse.ArgumentParser(
        description="上传 Astral Dice 构建产物到 CurseForge",
        formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--jar", action="append", metavar="PATH",
                    help="要上传的 jar（可重复；相对路径按当前目录解析）。--update-file 模式下可省略")
    ap.add_argument("--project-id", default=None,
                    help=f"覆盖项目 id（默认取 {BINDING_FILE} 的 projectId，兜底 {DEFAULT_PROJECT_ID}）")
    ap.add_argument("--token")
    ap.add_argument("--proxy", default=None,
                    help="本地代理，如 http://127.0.0.1:7897（也可用环境变量 HTTPS_PROXY）")
    ap.add_argument("--changelog", help="更新日志文件（默认自动取 release/<版本>/PLAYER_CHANGELOG*.md）")
    ap.add_argument("--changelog-type", default="markdown", choices=["markdown", "html", "text"])
    ap.add_argument("--display-name", help="站点显示名（默认 = jar 文件名）")
    ap.add_argument("--mc-version", help="覆盖从文件名解析出的 MC 版本")
    ap.add_argument("--loader", help="覆盖从文件名解析出的加载器（如 neoforge / forge）")
    ap.add_argument("--release-type", choices=["release", "beta", "alpha"],
                    help="覆盖按版本号推断的发布类型")
    ap.add_argument("--manual-release", action="store_true",
                    help="标记为「手动发布」（审核通过后不自动公开）")
    ap.add_argument("--update-file", type=int, metavar="FILEID",
                    help="不重传，改为**更新已发布文件**的元数据（当前只用来补 relations）；"
                         "需配合 --line（或用 --loader/--mc-version 唯一匹配）")
    ap.add_argument("--line", help=f"显式指定 {BINDING_FILE} 里的线 key（如 forge_1.20.1）")
    ap.add_argument("--no-relations", action="store_true",
                    help="本次不发送 binding 里声明的 relations（默认发送）")
    ap.add_argument("--dry-run", action="store_true", help="只打印计划，不发请求")
    ap.add_argument("--refresh-versions", action="store_true", help="忽略缓存重新拉取版本表")
    args = ap.parse_args()

    if not args.jar and args.update_file is None:
        ap.error("--jar 至少给一个（或改用 --update-file <fileID>）")

    binding = load_binding()
    project_id = str(args.project_id or binding.get("projectId") or DEFAULT_PROJECT_ID)

    import os
    proxy = args.proxy or os.environ.get("HTTPS_PROXY") or os.environ.get("https_proxy")
    token, token_src = resolve_token(args.token)
    opener = make_opener(proxy)

    # ---------- 模式 B：更新已发布文件的元数据（补 relations），不重传 ----------
    if args.update_file is not None:
        line = find_line(binding, key=args.line, loader=args.loader, mc=args.mc_version)
        if line is None:
            raise SystemExit("[ERR] --update-file 需要 --line <key>，"
                             "或用 --loader/--mc-version 唯一匹配一条线")
        payload = relations_payload(line, disabled=args.no_relations)
        metadata = {"fileID": args.update_file}
        if payload:
            metadata["relations"] = payload
        if args.display_name:
            metadata["displayName"] = args.display_name
        if len(metadata) == 1:
            raise SystemExit(
                "[ERR] update-file 至少要有一个要改的字段：给 --display-name，或让该线声明 relations"
                "（CurseForge 会以 1014 'You must specify something to update' 拒绝空更新）")
        print(f"项目 projectId = {project_id}（slug: {binding.get('slug')}，来源 {BINDING_FILE}）")
        print(f"凭据           = {token_src}")
        print(f"代理           = {proxy or '（直连；若被 Cloudflare 403 请加 --proxy）'}")
        print("-" * 72)
        print(f"  模式 = 更新已发布文件（不重传）  fileID = {args.update_file}")
        print(f"  线 key = {line.get('key')}（loader={line.get('loader')} mc={line.get('mc')}）")
        print(f"  relations = {describe_relations(payload)}")
        if payload:
            print("  🚨 实测（2026-10-01）：CurseForge 的 update-file 处理 `relations` 时**服务端 500**"
                  "（同批 releaseType / gameVersions 也 500；只有 displayName 更新返回 200）"
                  " ⇒ 想给已发布文件补 relations **只能重传**（upload-file，会产生同名新文件，"
                  "旧件只能在 CF 后台人工删）。本模式仍保留：等 CF 修好后可直接用。")
        if args.dry_run:
            print("-" * 72)
            print("DRY-RUN：未发送任何请求。去掉 --dry-run 即真实更新。")
            return 0
        code, body = api_post_metadata(
            opener, token, f"{API_BASE}/projects/{project_id}/update-file", metadata)
        ok = 200 <= code < 300
        print(f"[{'OK ' if ok else 'FAIL'}] fileID={args.update_file}  HTTP {code}  {body[:300]}")
        return 0 if ok else 1

    cat, ver_src = load_catalog(opener, token, force=args.refresh_versions)

    jobs = []
    for raw in args.jar:
        jar = pathlib.Path(raw).resolve()
        if not jar.is_file():
            raise SystemExit(f"[ERR] 找不到 jar：{jar}")
        ver, loader, mc = parse_jar(jar)
        loader = args.loader or loader
        mc = args.mc_version or mc
        rtype = args.release_type or infer_release_type(ver)
        ids, detail = resolve_ids(cat, mc, loader)
        line = find_line(binding, key=args.line, loader=loader, mc=mc)
        rel = relations_payload(line, disabled=args.no_relations)
        jobs.append({"jar": jar, "ver": ver, "loader": loader, "mc": mc,
                     "releaseType": rtype, "gameVersions": ids, "_detail": detail,
                     "_line": (line or {}).get("key"), "_relations": rel})

    # 更新日志：同一批次内同版本只读一次
    cache = {}
    for j in jobs:
        if j["ver"] not in cache:
            cache[j["ver"]] = pick_changelog(j["ver"], args.changelog)
        j["_changelog"] = cache[j["ver"]]

    slug = binding.get("slug")
    src = BINDING_FILE if binding.get("projectId") else "脚本内置兜底值"
    print(f"项目 projectId = {project_id}" + (f"（slug: {slug}，来源 {src}）" if binding else ""))
    print(f"凭据           = {token_src}")
    print(f"代理           = {proxy or '（直连；若被 Cloudflare 403 请加 --proxy）'}")
    print(f"版本表         = {ver_src}")
    print("-" * 72)
    for j in jobs:
        cl = j["_changelog"]
        if cl:
            cl_desc = f"{rel_display(cl)}（{len(cl.read_text(encoding='utf-8'))} 字符）"
        else:
            cl_desc = "（无，留空）"
        print(f"  {j['jar'].name}")
        print(f"    版本 {j['ver']} | {j['loader']} | MC {j['mc']} | 类型 {j['releaseType']}")
        print(f"    gameVersions = {j['gameVersions']}  {j['_detail']}")
        print(f"    更新日志 = {cl_desc}")
        print(f"    relations = {describe_relations(j['_relations'])}"
              + (f"  [{j['_line']}]" if j.get("_line") else "  [未匹配到该线]"))

    if args.dry_run:
        print("-" * 72)
        print("DRY-RUN：未发送任何请求。去掉 --dry-run 即真实上传。")
        return 0

    fail = 0
    for j in jobs:
        cl = j["_changelog"]
        metadata = {
            "displayName": args.display_name or j["jar"].name,
            "releaseType": j["releaseType"],
            "gameVersions": j["gameVersions"],
            "changelogType": args.changelog_type,
        }
        if cl:
            metadata["changelog"] = cl.read_text(encoding="utf-8")
        if j.get("_relations"):
            metadata["relations"] = j["_relations"]
        if args.manual_release:
            metadata["isMarkedForManualRelease"] = True
        code, body = api_upload(opener, project_id, token, metadata, j["jar"])
        ok = 200 <= code < 300
        print(f"[{'OK ' if ok else 'FAIL'}] {j['jar'].name}  HTTP {code}  {body[:200]}")
        if not ok:
            fail += 1
    return 1 if fail else 0


if __name__ == "__main__":
    sys.exit(main())
