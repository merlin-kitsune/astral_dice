#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把构建产物上传到 Modrinth（纯标准库，无第三方依赖）。

=====================================================================
凭据
=====================================================================
按以下顺序解析（先命中者生效）：
  1. ``--token <token>``
  2. 环境变量 ``MODRINTH_TOKEN``（CI 里走 GitHub secret）
  3. ``<仓库根>/.modrinth/token``（**已被 .gitignore 排除**，本地开发用）

⚠️ token 是账号级凭据（形如 ``mrp_`` + 60 字符），**绝对不要**写进任何入库文件。

=====================================================================
用法
=====================================================================
    # 先看计划（不发请求；dry-run 不需要 token）
    python tools/modrinth_upload.py --jar neoforge-1.21.1/build/libs/astral_dice-1.3.5-hotfix+neoforge_1.21.1.jar --dry-run

    # 真上传（四条线一次传完）
    python tools/modrinth_upload.py \\
        --jar neoforge-1.21.1/build/libs/astral_dice-1.3.5-hotfix+neoforge_1.21.1.jar \\
        --jar forge-1.20.1/build/libs/astral_dice-1.3.5-hotfix+forge_1.20.1.jar \\
        --jar neoforge-26.1.2/build/libs/astral_dice-1.3.5-beta.2+neoforge_26.1.2.jar \\
        --jar fabric-1.20.1/build/libs/astral_dice-1.3.5-alpha.1+fabric_1.20.1.jar

    # 前置库产物（文件名前缀 starengine_lib- ⇒ **自动路由到库项目**，无需额外参数）
    python tools/modrinth_upload.py \\
        --jar ../starengine_lib_fabric/neoforge-1.21.1/build/libs/starengine_lib-neoforge-1.21.1-1.0.7.jar

    # 看某项目上已有的版本号（幂等核对 / 发布后复查）
    python tools/modrinth_upload.py --list --project-id 5xDtrJ8X

    # 统一已发布版本的**标题**（规则 = `<前缀> <基础版本>`，例：Astral Dice 1.3.6）；先看计划再执行
    python tools/modrinth_upload.py --rename-versions --dry-run
    python tools/modrinth_upload.py --rename-versions --proxy http://127.0.0.1:7897

    # 本机直连可通；若被网络策略拦，可加 --proxy http://127.0.0.1:7897
    python tools/modrinth_upload.py --jar <...> --proxy http://127.0.0.1:7897

=====================================================================
Modrinth API 要点（2026-10-01 实测 + 官方文档）
=====================================================================
* 认证头 = ``Authorization: <token>``（**裸 token，不要加 `Bearer`**；文档 TokenAuth 就是 apiKey 走
  Authorization 头）。载荷不含 ``mrp_`` 前缀会被判成别的凭据类型。
* ``User-Agent`` 是**硬要求**且必须能唯一识别调用方（官方原文：只写 ``okhttp/4.9.3`` 这类会被拦）。
* 建版本 = ``POST /v2/version``，``multipart/form-data``，至少两个字段：``data``（JSON 字符串）+
  至少一个**文件字段**（字段名任意，但必须出现在 ``data.file_parts`` 里）。成功返回版本对象（含 ``id``）。
  🚨 **``data`` 必须排在文件字段之前** —— 服务端取 multipart 的**第一个字段**当 ``data``，文件在前时
  它拿 jar 字节去 parse JSON，回的是 ``Error while parsing JSON: expected value at line 1 column 1``
  （完全看不出是顺序问题）。对照实验见 `_multipart()` 的 docstring。
* 版本类型 ``version_type`` ∈ {release, beta, alpha}；``loaders`` ∈ {neoforge, forge, fabric}
  （``/v2/tag/loader`` 动态解析，不硬编码）；``game_versions`` 是字符串数组（``/v2/tag/game_version`` 校验）。
* ``environment`` = ``client_and_server``（本模组 fabric.mod.json / mods.toml 均为双侧）。
* 依赖 = ``dependencies[]``，每项 ``{version_id?, project_id?, file_name?, dependency_type}``，
  ``dependency_type`` ∈ {required, optional, incompatible, embedded}（**没有** include/embeddedLibrary）。
  🚨 **`dependencies` 是必填字段**：缺了直接 400
  （``Error while parsing JSON: missing field `dependencies` ``，2026-10-01 实测）——
  官方文档把「没有依赖」写成「不传该字段」是**误导**，本脚本一律送数组（无依赖 = `[]`）。
* 幂等：上传前先 ``GET /v2/project/{id}/version`` 取已有 ``version_number`` 集合，命中即跳过
  （``--force`` 可强制重传）。⚠️ 项目若处于 draft/unlisted/processing，**匿名**列版本会被 404 挡掉
  ⇒ 此时跳过检查并告警（带 token 就能看到；见下条「鉴权自检的坑」）。
* 🚨 **鉴权自检的坑**：``GET /v2/user`` 需要 ``USER_READ`` 权限域，缺它时服务端返回的也是
  ``401 Invalid Authentication Credentials`` —— 和「令牌本身就无效」**长得一模一样**（2026-10-01 实测：
  同一个令牌 ``/v2/user`` 恒 401，却能正常 200 读到未公开项目与其版本列表）。
  ⇒ **不要用 ``/v2/user`` 判断令牌是否可用**，用「能否带 token 读到目标项目」来判断。
  写入路由真正需要的权限域是 ``VERSION_CREATE``。
* 🚨 **项目声明的 ``loaders`` / ``game_versions`` 是硬边界**：版本里出现项目未声明的加载器或 MC 版本，
  服务端直接拒。本脚本在发请求前逐条预检（本项目真实踩到：库项目只声明了 forge/neoforge，
  而库的 fabric 产物需要 fabric）—— 先拦下来，避免「前三个成功、第四个失败」的半成品批次。
* 频控 300 req/min（按 IP，响应头 ``X-Ratelimit-*``）。本脚本每次运行最多十余个请求，无需限速。

⚠️ 与 CurseForge 的**两套 id 体系**不要混用：CurseForge 用数字 projectID（如 curios=309927），
Modrinth 用 base62 project id（如 curios=vvuO3ImH）。本脚本只读 ``tools/modrinth.json``。
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
import urllib.request
import uuid

API_BASE = "https://api.modrinth.com/v2"
DEFAULT_PROJECT_ID = "5xDtrJ8X"          # 兜底值；正式绑定见 tools/modrinth.json
BINDING_FILE = "tools/modrinth.json"
# User-Agent 必须能唯一识别调用方（官方要求），格式：github_username/project/version (联系人)
UA = "merlin-kitsune/astral-dice-publisher/1.0.0 (modrinth project: astral-dice)"
CACHE_DAYS = 7
DEP_TYPES = ("required", "optional", "incompatible", "embedded")

# 主模组产物：astral_dice-<版本>+<加载器>_<MC版本>.jar
MAIN_RE = re.compile(r"^astral_dice-(?P<ver>[^+]+)\+(?P<loader>[a-z]+)_(?P<mc>.+)\.jar$")
# 前置库产物：starengine_lib-<加载器>-<MC版本>-<版本>.jar
#   ⚠️ MC 版本段用 `\d+(?:\.\d+)+`（数字点号）而不是 `.+?`：后者在
#   `starengine_lib-neoforge-26.1.2-1.0.7.jar` 上会与版本段争抢，把 MC 切成 `26.1`、版本切成 `2-1.0.7`。
LIB_RE = re.compile(
    r"^starengine_lib-(?P<loader>neoforge|forge|fabric)-(?P<mc>\d+(?:\.\d+)+)-(?P<ver>\d[^/]*)\.jar$")

REPO_ROOT = pathlib.Path(__file__).resolve().parent.parent
CACHE_DIR = REPO_ROOT / ".modrinth"
TAGS_CACHE = CACHE_DIR / "tags.json"


# ---------------------------------------------------------------- 项目绑定
def load_binding():
    """读 tools/modrinth.json（**项目 id 的正式落点**，2026-10-01 用户指定）。

    该文件**入库**，因此只放非敏感信息（projectId / libProjectId / 四线依赖映射），
    token 一律走 .modrinth/token 或环境变量。
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
    env = (os.environ.get("MODRINTH_TOKEN") or "").strip()
    if env:
        return env, "环境变量 MODRINTH_TOKEN"
    f = CACHE_DIR / "token"
    if f.is_file():
        return f.read_text(encoding="utf-8").strip(), str(f.relative_to(REPO_ROOT))
    raise SystemExit(
        "[ERR] 未找到 Modrinth token。三种方式任选：\n"
        "      --token <t>  |  环境变量 MODRINTH_TOKEN  |  写成 .modrinth/token")


# ---------------------------------------------------------------- HTTP
def make_opener(proxy):
    handlers = []
    if proxy:
        handlers.append(urllib.request.ProxyHandler({"http": proxy, "https": proxy}))
    handlers.append(urllib.request.HTTPSHandler(context=ssl.create_default_context()))
    return urllib.request.build_opener(*handlers)


def api_get(opener, token, path):
    """GET，返回 (status, 解析后的 JSON 或原始文本)。"""
    headers = {"User-Agent": UA, "Accept": "application/json"}
    if token:
        headers["Authorization"] = token
    req = urllib.request.Request(API_BASE + path, headers=headers)
    try:
        with opener.open(req, timeout=60) as r:
            return r.status, json.loads(r.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", "replace")
        try:
            return e.code, json.loads(body)
        except Exception:
            return e.code, body


def api_patch(opener, token, path, body):
    """PATCH（JSON 体），返回 (status, 解析后的 JSON 或原始文本)。"""
    headers = {"User-Agent": UA, "Accept": "application/json",
               "Content-Type": "application/json"}
    if token:
        headers["Authorization"] = token
    req = urllib.request.Request(API_BASE + path,
                                 data=json.dumps(body, ensure_ascii=False).encode("utf-8"),
                                 headers=headers, method="PATCH")
    try:
        with opener.open(req, timeout=60) as r:
            # 🚨 PATCH /version/{id} 成功时返回 **204 No Content（空体）** ⇒ 不能无条件 json.loads
            #    （POST 上传有 JSON 体，2026-10-02 改名才暴露）。
            raw = r.read().decode("utf-8", "replace")
            if not raw.strip():
                return r.status, ""
            try:
                return r.status, json.loads(raw)
            except Exception:
                return r.status, raw
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", "replace")
        try:
            return e.code, json.loads(raw)
        except Exception:
            return e.code, raw


def _multipart(field_name, meta, jar_path):
    """按 RFC 2388 拼 multipart：`data` 字段（JSON）+ 一个文件字段。

    🚨 **`data` 必须排在文件字段之前**（2026-10-01 实测）：Modrinth 的服务端把 multipart 里
    **第一个字段**当作 `data` 读 —— 文件在前时它拿 jar 的字节去 parse JSON，直接回
    ``400 invalid_input: Error while parsing JSON: expected value at line 1 column 1``，
    而这个报错完全看不出「顺序错了」。对照实验（同一份合法 JSON、只换顺序）：
      * `data` 在前 ⇒ 报错推进到 ``missing field `file_parts` ``（说明 JSON 已被正确解析）；
      * 文件在前 ⇒ 恒为 ``expected value at line 1 column 1``。
    """
    boundary = "----AstralDiceModrinth" + uuid.uuid4().hex
    payload = json.dumps(meta, ensure_ascii=False).encode("utf-8")
    chunks = [
        # —— 1) data（必须第一个）——
        f"--{boundary}\r\n".encode(),
        b'Content-Disposition: form-data; name="data"\r\n',
        b"Content-Type: application/json\r\n\r\n",
        payload, b"\r\n",
        # —— 2) 文件本体 ——
        f"--{boundary}\r\n".encode(),
        f'Content-Disposition: form-data; name="{field_name}"; filename="{jar_path.name}"\r\n'.encode(),
        b"Content-Type: application/java-archive\r\n\r\n",
        jar_path.read_bytes(), b"\r\n",
        f"--{boundary}--\r\n".encode(),
    ]
    return b"".join(chunks), boundary


def api_create_version(opener, token, meta, jar_path, field_name="file"):
    data, boundary = _multipart(field_name, meta, jar_path)
    req = urllib.request.Request(
        f"{API_BASE}/version", data=data, method="POST",
        headers={"Authorization": token, "User-Agent": UA,
                 "Content-Type": f"multipart/form-data; boundary={boundary}",
                 "Content-Length": str(len(data))})
    try:
        with opener.open(req, timeout=600) as r:
            return r.status, r.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")
    except Exception as e:                       # 网络层异常也要变成可读的一行
        return 0, repr(e)


# ---------------------------------------------------------------- 标签表
def load_tags(opener, force=False):
    """返回 (game_versions:set, loaders:set, 来源说明)。缓存 7 天。"""
    if not force and TAGS_CACHE.is_file():
        age = time.time() - TAGS_CACHE.stat().st_mtime
        if age < CACHE_DAYS * 86400:
            cat = json.loads(TAGS_CACHE.read_text(encoding="utf-8"))
            return (set(cat["game_versions"]), set(cat["loaders"]),
                    f"缓存 {TAGS_CACHE.relative_to(REPO_ROOT)}（{age / 3600:.1f} 小时前）")
    s, gv = api_get(opener, None, "/tag/game_version")
    if s != 200:
        raise SystemExit(f"[ERR] 取 /tag/game_version 失败：HTTP {s} {str(gv)[:200]}")
    s2, ld = api_get(opener, None, "/tag/loader")
    if s2 != 200:
        raise SystemExit(f"[ERR] 取 /tag/loader 失败：HTTP {s2} {str(ld)[:200]}")
    cat = {"fetchedAt": int(time.time()),
           "game_versions": [v["version"] for v in gv],
           "loaders": [x["name"] for x in ld]}
    CACHE_DIR.mkdir(exist_ok=True)
    TAGS_CACHE.write_text(json.dumps(cat, ensure_ascii=False), encoding="utf-8")
    return (set(cat["game_versions"]), set(cat["loaders"]),
            f"已从 Modrinth 拉取 → {TAGS_CACHE.relative_to(REPO_ROOT)}")


# ---------------------------------------------------------------- 元数据
def parse_jar(jar):
    """从文件名解析 (role, ver, loader, mc)。role ∈ {main, lib}。"""

    # 🚨 先挡掉构建的分类器产物(`-sources` / `-javadoc` / `-dev`)。
    #    它们**能通过**下面两个正则：库的版本段 `(\\d[^/]*)` 会把 `-sources` 吃进版本号
    #    （解析成 `1.0.7-sources`，mc/loader 都合法，项目级预检也照过）⇒ 会**静默上传一个垃圾版本**。
    #    主模组那条虽然会因为 mc 段变成 `1.21.1-sources` 被预检拦下，但报错信息指向「MC 版本不存在」，
    #    与真实原因（这是个 sources jar）相去甚远。这里统一给出一句能看懂的话。
    stem = jar.name[:-4] if jar.name.endswith(".jar") else jar.name
    for bad in ("-sources", "-javadoc", "-dev"):
        if stem.endswith(bad):
            raise SystemExit(f"[ERR] {jar.name} 是构建的附加产物（{bad}.jar），不是可分发的模组本体，"
                             "请只传主产物（astral_dice-*/*.jar 或 starengine_lib-*-*.jar 本体）")

    m = MAIN_RE.match(jar.name)
    if m:
        return "main", m.group("ver"), m.group("loader"), m.group("mc")
    m = LIB_RE.match(jar.name)
    if m:
        return "lib", m.group("ver"), m.group("loader"), m.group("mc")
    raise SystemExit(
        f"[ERR] 无法从文件名解析产物身份：{jar.name}\n"
        "      主模组应形如 astral_dice-<版本>+<加载器>_<MC版本>.jar；\n"
        "      前置库应形如 starengine_lib-<加载器>-<MC版本>-<版本>.jar")


def infer_version_type(ver):
    """渠道派生（与 tools/curseforge_upload.py 完全同口径）。"""
    v = ver.lower()
    if "-alpha" in v:
        return "alpha"
    if "-beta" in v or "-rc" in v or "-pre" in v:
        return "beta"
    return "release"


def version_number(ver, loader, mc):
    """版本号 = `<版本>+<加载器>_<MC版本>`。

    ⚠️ 刻意带上加载器/MC：一个项目下四条线并存，裸版本号在 1.21.1 与 1.20.1 上**同名**
    （都是 `1.3.5-hotfix`）—— 同名在 Modrinth 上虽不必然被拒，但版本列表会变得无法区分；
    而 `+加载器_MC` 正是本模组 jar 文件名与 GitHub 产物本来的写法 ⇒ 既唯一又不失真。
    """
    return f"{ver}+{loader}_{mc}"


_PRERELEASE_SUFFIX = re.compile(r"-(?:alpha|beta|rc|pre|snapshot|hotfix)(?:[.\-]?\d+)?$", re.I)


def version_display_name(vn, prefix):
    """版本**标题**统一口径（2026-10-02 用户裁决）：`<前缀> <基础版本>`。

    剥掉 `+<加载器>_<MC版本>` 与预发布后缀（`-alpha.N` / `-beta.N` / `-rc.N` / `-pre.N` / `-hotfix`）
    ⇒ 同一基础版本的四条线**标题完全一致**（例：`1.3.6+neoforge_1.21.1`、
    `1.3.6-beta.1+neoforge_26.1.2`、`1.3.6-alpha.1+fabric_1.20.1` 都叫 `Astral Dice 1.3.6`），
    四条线仍靠 `version_number` 与 Modrinth 的加载器/游戏版本标签区分。**version_number 不改**。

    旧写法（裸 `1.3.5-hotfix+forge_1.20.1`、`Astral Dice 1.2.1 (Forge 1.20.1)`、`… Hotfix` 等）
    已造成版本列表难以辨认 ⇒ 由 `--rename-versions` 全量归一。
    """
    base = vn.split("+", 1)[0]
    return f"{prefix} {_PRERELEASE_SUFFIX.sub('', base)}"


def rel_display(p):
    """展示用路径：在仓库内给相对路径，否则原样给出（相对路径未 resolve 时 relative_to 会抛）。"""
    try:
        return p.relative_to(REPO_ROOT)
    except ValueError:
        return p


def find_line(binding, key=None, loader=None, mc=None):
    """在 lines 里定位一条线：优先用 `key`，否则按 loader+mc 唯一匹配。"""
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


def dependencies_payload(line, lib_project_id=None, with_lib=False, disabled=False):
    """把该线声明的依赖规范化成上传 API 要的结构（list）；无声明或 --no-dependencies 时返回 []。

    ⚠️ schema 以 Modrinth 官方《Create a version》为准（见模块 docstring），不要凭记忆改写：
        dependencies: [ { project_id, dependency_type } ]，type ∈ required/optional/incompatible/embedded
    """
    if disabled or not line:
        return []
    out = []
    for d in (line.get("dependencies") or []):
        dtype = d.get("type")
        if dtype not in DEP_TYPES:
            raise SystemExit(f"[ERR] 未知的 dependency type {dtype!r}（允许：{', '.join(DEP_TYPES)}）")
        pid = d.get("projectID") or d.get("slug")
        if not pid:
            raise SystemExit("[ERR] dependency 至少要给 projectID 或 slug")
        out.append({"project_id": str(pid), "dependency_type": dtype})
    if with_lib and lib_project_id:
        # 库被四条线 JarJar **内嵌** ⇒ embedded（不是 required）：Modrinth 启动器不会因此去装它，
        # 页面上又能说明「本模组已内嵌该库」。⚠️ 库项目未公开时该引用会被服务端拒 ⇒ 故**默认不加**，
        # 需显式 --with-lib-dep。
        out.append({"project_id": str(lib_project_id), "dependency_type": "embedded"})
    return out


def pick_changelog(binding, line, lib_sec, role, ver, explicit):
    """解析更新日志文件（模板见 tools/modrinth.json 的 changelogDir）。"""
    if explicit:
        p = pathlib.Path(explicit).resolve()
        if not p.is_file():
            raise SystemExit(f"[ERR] 指定的更新日志不存在：{p}")
        return p
    base = ver.split("-")[0]
    if role == "lib":
        tmpl = (lib_sec or {}).get("changelogDir")
    else:
        tmpl = (line or {}).get("changelogDir", "release/{base}")
    if not tmpl:
        return None
    d = tmpl.replace("{base}", base).replace("{version}", ver).replace("{loader}", "")
    for name in ("PLAYER_CHANGELOG.md", "PLAYER_CHANGELOG_ZH.md"):
        p = REPO_ROOT / d / name
        if p.is_file():
            return p
    return None


def describe_dependencies(deps, titles=None):
    if not deps:
        return "（无）"
    parts = []
    for d in deps:
        t = (titles or {}).get(d["project_id"], "")
        parts.append(f"{d['project_id']}{f'({t})' if t else ''}={d['dependency_type']}")
    return ", ".join(parts)


def project_versions(opener, token, project_id):
    """返回 (version_number -> id 的字典, 说明)。拿不到时返回 ({}, 原因)。"""
    s, body = api_get(opener, token, f"/project/{project_id}/version")
    if s == 200:
        return {v.get("version_number"): v.get("id") for v in body}, f"{len(body)} 个已发布版本"
    if s == 404:
        return {}, ("HTTP 404（项目可能是 draft/unlisted，或 id 有误）—— 本次不做重复检查")
    return {}, f"HTTP {s} {str(body)[:160]}"


# ---------------------------------------------------------------- main
def main():
    ap = argparse.ArgumentParser(
        description="上传 Astral Dice / StarEngine Lib 构建产物到 Modrinth",
        formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--jar", action="append", metavar="PATH",
                    help="要上传的 jar（可重复；相对路径按当前目录解析）。"
                         "astral_dice-* ⇒ 主项目，starengine_lib-* ⇒ 库项目（自动路由）")
    ap.add_argument("--project-id", default=None,
                    help=f"覆盖主项目 id（默认取 {BINDING_FILE} 的 projectId，兜底 {DEFAULT_PROJECT_ID}）")
    ap.add_argument("--lib-project-id", default=None, help=f"覆盖库项目 id（默认取 {BINDING_FILE} 的 lib.projectId）")
    ap.add_argument("--token")
    ap.add_argument("--proxy", default=None,
                    help="本地代理，如 http://127.0.0.1:7897（也可用环境变量 HTTPS_PROXY）")
    ap.add_argument("--changelog", help="更新日志文件（覆盖按模板自动解析的结果）")
    ap.add_argument("--version-number", help="覆盖自动生成的 version_number（单 jar 时才有意义）")
    ap.add_argument("--version-name", help="覆盖版本标题（默认 = `<项目前缀> <基础版本>`，规则见 "
                                          "tools/modrinth.json 的 _nameNote）")
    ap.add_argument("--release-type", choices=["release", "beta", "alpha"],
                    help="覆盖按版本号推断的 version_type")
    ap.add_argument("--loader", help="覆盖从文件名解析出的加载器（如 neoforge / forge / fabric）")
    ap.add_argument("--mc-version", help="覆盖从文件名解析出的 MC 版本")
    ap.add_argument("--line", help=f"显式指定 {BINDING_FILE} 里的线 key（如 forge_1.20.1）")
    ap.add_argument("--with-lib-dep", action="store_true",
                    help="在依赖里附带前置库（dependency_type=embedded）；库项目未公开时会 400")
    ap.add_argument("--no-dependencies", action="store_true", help="本次不发送 binding 里声明的依赖")
    ap.add_argument("--allow-out-of-range", action="store_true",
                    help="跳过「项目未声明该加载器/MC 版本」的预检，直接发请求（服务端可能仍拒）")
    ap.add_argument("--draft", action="store_true", help="以 draft 状态上传（不公开；库项目未定稿时可先试传）")
    ap.add_argument("--force", action="store_true", help="即使该 version_number 已存在也照传")
    ap.add_argument("--list", action="store_true", help="只列出目标项目上已有的版本号，不上传")
    ap.add_argument("--rename-versions", action="store_true",
                    help="把两个项目上**已发布**版本的标题统一成现行规则（PATCH /version/{id}，"
                         "不动 version_number）；配 --dry-run 先看计划")
    ap.add_argument("--dry-run", action="store_true", help="只打印计划，不发请求")
    ap.add_argument("--refresh-tags", action="store_true", help="忽略缓存重新拉取标签表")
    args = ap.parse_args()

    if not args.jar and not (args.list or args.rename_versions):
        ap.error("--jar 至少给一个（或改用 --list / --rename-versions）")

    binding = load_binding()
    main_pid = str(args.project_id or binding.get("projectId") or DEFAULT_PROJECT_ID)
    lib_sec = binding.get("lib") or {}
    lib_pid = str(args.lib_project_id or lib_sec.get("projectId") or "")
    main_prefix = str(binding.get("namePrefix") or "Astral Dice")
    lib_prefix = str(lib_sec.get("namePrefix") or "StarEngine Lib")
    proxy = args.proxy or os.environ.get("HTTPS_PROXY") or os.environ.get("https_proxy")
    opener = make_opener(proxy)

    # ---------- 模式：列出版本号（不需要 token，但 draft 项目必须带 token） ----------
    if args.list:
        token, token_src = None, "（未提供；仅公开项目可列）"
        try:
            token, token_src = resolve_token(args.token)
        except SystemExit:
            pass
        for pid, label in ((main_pid, "主项目"), (lib_pid, "库项目")):
            if not pid:
                continue
            known, note = project_versions(opener, token, pid)
            print(f"{label} {pid}：{note}")
            for vn in sorted(known):
                print(f"    {vn}  (id {known[vn]})")
        return 0

    # ---------- 模式：统一已发布版本的标题（PATCH /version/{id}，不动 version_number） ----------
    if args.rename_versions:
        token, token_src = resolve_token(args.token)
        print(f"凭据   = {token_src} | 代理 = {proxy or '（直连）'}")
        plan = []
        for pid, label, prefix in ((main_pid, "主项目", main_prefix), (lib_pid, "库项目", lib_prefix)):
            if not pid:
                continue
            s, body = api_get(opener, token, f"/project/{pid}/version")
            if s != 200:
                print(f"[WARN] {label} {pid} 读不到版本列表（HTTP {s}）—— 跳过")
                continue
            print(f"{label} {pid} 已有 {len(body)} 个版本")
            for v in body:
                want = version_display_name(v["version_number"], prefix)
                if v["name"] != want:
                    plan.append((label, v["id"], v["version_number"], v["name"], want))
        if not plan:
            print("所有版本的标题都已符合现行规则，无需改动。")
            return 0
        print(f"待改标题 {len(plan)} 条：")
        for label, vid, vn, old, want in plan:
            print(f"  [{label}] {vn}\n      {old!r} -> {want!r}")
        if args.dry_run:
            print("-" * 78)
            print("DRY-RUN：未发送任何 PATCH。去掉 --dry-run 即真实改名。")
            return 0
        ok = fail = 0
        for label, vid, vn, old, want in plan:
            s, body = api_patch(opener, token, f"/version/{vid}", {"name": want})
            if 200 <= s < 300:
                print(f"[OK  ] {vn}  ->  {want}")
                ok += 1
            else:
                print(f"[FAIL] {vn}  ->  {want}  HTTP {s}  {body}")
                fail += 1
        print("-" * 78)
        print(f"改名成功 {ok} / 失败 {fail}")
        return 0 if fail == 0 else 1


    gvs, loaders, tag_src = load_tags(opener, force=args.refresh_tags)

    jobs, bad = [], []
    for raw in args.jar:
        jar = pathlib.Path(raw).resolve()
        if not jar.is_file():
            raise SystemExit(f"[ERR] 找不到 jar：{jar}")
        role, ver, loader, mc = parse_jar(jar)
        loader = args.loader or loader
        mc = args.mc_version or mc
        if loader not in loaders:
            bad.append(f"{jar.name}：Modrinth 上没有加载器 {loader!r}")
        if mc not in gvs:
            bad.append(f"{jar.name}：Modrinth 上没有 MC 版本 {mc!r}")
        line = find_line(binding, key=args.line, loader=loader, mc=mc) if role == "main" else None
        if role == "main" and line is None and not args.no_dependencies:
            # 🚨 未登记的组合**不能静默发布**：`dependencies_payload(None, …)` 会安静地返回 `[]`，
            #    结果是「本该带 Curios 硬前置的版本，以零依赖发布上线」——页面看起来正常，玩家却不知道要装什么。
            bad.append(f"{jar.name}：{BINDING_FILE} 里没有 loader={loader} mc={mc} 这条线"
                       "（新开一线时须先在 binding 里登记其 dependencies；确实想以零依赖发布请显式加 --no-dependencies）")
        deps = dependencies_payload(line, lib_pid, args.with_lib_dep, args.no_dependencies) \
            if role == "main" else []
        cl = pick_changelog(binding, line, lib_sec, role, ver, args.changelog)
        vn = args.version_number or version_number(ver, loader, mc)
        prefix = main_prefix if role == "main" else lib_prefix
        jobs.append({
            "jar": jar, "role": role, "ver": ver, "loader": loader, "mc": mc,
            "projectId": main_pid if role == "main" else lib_pid,
            "version_number": vn,
            "display_name": args.version_name or version_display_name(vn, prefix),
            "version_type": args.release_type or infer_version_type(ver),
            "game_versions": [mc], "loaders": [loader],
            "dependencies": deps, "changelog": cl,
            "_line": (line or {}).get("key"),
        })
    if bad:
        raise SystemExit("[ERR] 以下产物无法发布：\n      - " + "\n      - ".join(bad))
    if any(not j["projectId"] for j in jobs):
        raise SystemExit("[ERR] 库产物需要库项目 id（--lib-project-id 或 tools/modrinth.json 的 lib.projectId）")

    # 依赖项目名（**只作展示核对**，属公开只读接口，拿不到不算错）
    titles = {}
    for pid in sorted({d["project_id"] for j in jobs for d in j["dependencies"]}):
        s, body = api_get(opener, None, f"/project/{pid}")
        if s == 200:
            titles[pid] = body.get("title") or body.get("slug") or ""
        else:
            titles[pid] = f"?HTTP{s}"

    print(f"主项目 {main_pid}"
          + (f" | 库项目 {lib_pid}" if lib_pid else " | 库项目 （未配置）"))
    print(f"代理   = {proxy or '（直连）'}")
    print(f"标签表 = {tag_src}")
    print("-" * 78)
    for j in jobs:
        cl = j["changelog"]
        cl_desc = f"{rel_display(cl)}（{len(cl.read_bytes())} 字节）" if cl else "（无，留空）"
        print(f"  {j['jar'].name}")
        print(f"    [{j['role']}] 项目 {j['projectId']} | version_number {j['version_number']}")
        print(f"    版本标题 = {j['display_name']}")
        print(f"    渠道 {j['version_type']} | loaders {j['loaders']} | game_versions {j['game_versions']}")
        print(f"    更新日志 = {cl_desc}")
        tag = f"  [{j['_line']}]" if j.get("_line") else ("  [库产物]" if j["role"] == "lib" else "  [未匹配到线]")
        print(f"    依赖 = {describe_dependencies(j['dependencies'], titles)}{tag}")

    if args.dry_run:
        print("-" * 78)
        print("DRY-RUN：**不上传任何文件**（仅读取公开的标签表与依赖项目名；无需 token）。"
              "去掉 --dry-run 即真实上传。")
        return 0

    token, token_src = resolve_token(args.token)
    print(f"凭据   = {token_src}")

    # 幂等 + 项目级预检：按项目分批取已有 version_number 与项目声明的 loaders / game_versions
    known_by_pid, covered = {}, {}
    for pid in sorted({j["projectId"] for j in jobs}):
        known, note = project_versions(opener, token, pid)
        known_by_pid[pid] = known
        print(f"项目 {pid} 已有：{note}")
        s, pj = api_get(opener, token, f"/project/{pid}")
        covered[pid] = (set(pj.get("loaders") or []), set(pj.get("game_versions") or [])) if s == 200 else None
        if covered[pid]:
            print(f"    项目支持 loaders={sorted(covered[pid][0])} game_versions={sorted(covered[pid][1])}")

    # 🚨 项目级的 loaders / game_versions 是**硬边界**：版本里出现项目未声明的加载器（或 MC 版本），
    #    服务端会直接拒（本项目真实踩到：库项目只声明了 forge/neoforge，而库的 fabric 产物需要 fabric）
    #    ⇒ 在**发请求之前**逐条拦住，避免「传了三个成功、第四个失败」这种半成品批次。
    blocked = []
    for j in jobs:
        cov = covered.get(j["projectId"])
        if not cov:
            continue
        miss_l = [x for x in j["loaders"] if x not in cov[0]]
        miss_g = [x for x in j["game_versions"] if x not in cov[1]]
        if miss_l or miss_g:
            blocked.append(f"{j['jar'].name}：项目 {j['projectId']} 未声明 "
                           f"{('loaders=' + str(miss_l)) if miss_l else ''}"
                           f"{(' game_versions=' + str(miss_g)) if miss_g else ''}"
                           "（须先在 Modrinth 项目设置里勾上，再重跑）")
    if blocked and not args.allow_out_of_range:
        raise SystemExit("[ERR] 以下产物超出该项目声明的支持范围：\n      - " + "\n      - ".join(blocked)
                         + "\n      （须先在 Modrinth 项目设置里勾上对应加载器/MC 版本；"
                           "确认要硬试可加 --allow-out-of-range）")
    for line in blocked:
        print(f"[WARN] 越界放行（--allow-out-of-range）：{line}")

    fail, skip, ok = 0, 0, 0
    for j in jobs:
        if not args.force and j["version_number"] in known_by_pid.get(j["projectId"], {}):
            print(f"[SKIP] {j['jar'].name}  —— version_number {j['version_number']} 已存在"
                  f"（id {known_by_pid[j['projectId']][j['version_number']]}）；--force 可强制重传")
            skip += 1
            continue
        meta = {
            "project_id": j["projectId"],
            "name": j["display_name"],
            "version_number": j["version_number"],
            "version_type": j["version_type"],
            "loaders": j["loaders"],
            "game_versions": j["game_versions"],
            "environment": "client_and_server",
            "featured": False,
            "status": "draft" if args.draft else "listed",
            "file_parts": ["file"],
            "primary_file": "file",
            # 🚨 `dependencies` 是 Labrinth 的**必填字段**，缺了直接 400
            #    （实测原文：`Error while parsing JSON: missing field \`dependencies\``）。
            #    官方文档把「没有依赖」写成「不传该字段」是**误导** ⇒ 无依赖时也必须送**空数组**。
            "dependencies": j["dependencies"],
        }
        if j["changelog"]:
            meta["changelog"] = j["changelog"].read_text(encoding="utf-8")
        code, body = api_create_version(opener, token, meta, j["jar"])
        good = 200 <= code < 300
        try:
            vid = json.loads(body).get("id")
        except Exception:
            vid = None
        print(f"[{'OK  ' if good else 'FAIL'}] {j['jar'].name}  HTTP {code}"
              + (f"  id={vid}" if vid else f"  {body[:260]}"))
        if good:
            ok += 1
        else:
            fail += 1
    print("-" * 78)
    print(f"成功 {ok} / 跳过 {skip} / 失败 {fail}")
    return 1 if fail else 0


if __name__ == "__main__":
    sys.exit(main())
