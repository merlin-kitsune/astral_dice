#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把构建产物上传到 CurseForge，并维护项目元数据（纯标准库，无第三方依赖）。

=====================================================================
子命令一览（与 ``--help`` / 各子命令 ``--help`` 的 epilog 同款）
=====================================================================
  push          一条命令完成「上传 jar → relations →（可选）更新项目信息」
  update-info   只改项目元数据：summary / description / categoryId
  （不带子命令） 旧式用法：``--jar`` 上传 / ``--update-file`` 补元数据（兼容保留）

⚠️ **默认 dry-run**（2026-10-20 起）：两个子命令只有显式给 ``--apply``（别名
   ``--yes``）才会发**写**请求；旧式用法同样要求显式 ``--apply`` 才真正上传
   （只给 ``--jar`` 而不给 ``--apply``/``--dry-run`` ⇒ 走「规划 + 守卫提示」）。

最小示例：

    # 只看将要 PUT 的 JSON（不发请求，也不需要 token）
    python3 tools/curseforge_upload.py update-info --dry-run \
        --summary "Dice combat, signs, chips and cards for survival" \
        --description-file README.md

    # 真改项目信息（需要项目作者的凭据）
    python3 tools/curseforge_upload.py update-info --apply --summary "..." \
        --description-file README.md --category-id 4483

    # 只看上传计划（离线可跑：读 .curseforge/versions.json 缓存，不联网）
    python3 tools/curseforge_upload.py push --dry-run \
        --jar neoforge-1.21.1/build/libs/astral_dice-1.5.0+neoforge_1.21.1.jar

    # 上传 + relations + 项目信息（一步到位）
    python3 tools/curseforge_upload.py push --apply \
        --jar neoforge-1.21.1/build/libs/astral_dice-1.5.0+neoforge_1.21.1.jar \
        --update-info --summary "..." --description-file README.md

=====================================================================
凭据
=====================================================================
按以下顺序解析（先命中者生效）：
  1. ``--token <token>``
  2. 环境变量 ``CURSEFORGE_TOKEN``（CI 里走 GitHub secret）
  3. ``<仓库根>/.curseforge/token``（**已被 .gitignore 排除**，本地开发用）

⚠️ token 是账号级凭据，**绝对不要**写进任何入库文件。

``PUT /v1/mods/{modId}`` 属于**官方 Core API v1**，鉴权头是 ``x-api-key``
（console.curseforge.com 的 API Key），与上传用的 ``X-Api-Token`` 不是同一种凭据。
本脚本两个都发：``x-api-key`` 取 ``--api-key`` / ``CURSEFORGE_API_KEY``，缺失时
**回退**用 ``resolve_token`` 解析出的旧版 token（会在计划里明确告警，未验证可过）。

=====================================================================
用法
=====================================================================
    # 先看计划（不发请求）
    python tools/curseforge_upload.py --jar build/libs/astral_dice-1.3.5+neoforge_1.21.1.jar --dry-run

    # 真上传（2026-10-20 起必须显式 --apply）
    python tools/curseforge_upload.py --jar build/libs/astral_dice-1.3.5+neoforge_1.21.1.jar --apply

    # 一次传多个（各线产物可混在一起传）
    python tools/curseforge_upload.py \
        --jar build/libs/astral_dice-1.3.5+neoforge_1.21.1.jar \
        --jar build/libs/astral_dice-1.3.5+forge_1.20.1.jar \
        --jar build/libs/astral_dice-1.3.7-beta.2+neoforge_26.1.2.jar --apply

    # 第四条线 fabric（2026-10-01 起并入 multi-main）：版本号带 -alpha ⇒ 自动走 alpha 渠道
    python tools/curseforge_upload.py \
        --jar build/libs/astral_dice-1.3.7-alpha.2+fabric_1.20.1.jar \
        --changelog <该线自备的发布说明.md> --apply

    # 本机直连会被 Cloudflare 拦（403）；走本地代理即可
    python tools/curseforge_upload.py --jar <...> --proxy http://127.0.0.1:7897 --apply

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

=====================================================================
项目信息（PUT /v1/mods/{modId}）—— 2026-10-20 新增
=====================================================================
* 端点：``PUT https://api.curseforge.com/v1/mods/{modId}``（官方 Core API v1；
  与上传用的 ``minecraft.curseforge.com/api`` 是**两套 API**）。
* 只暴露三个字段，均为 ``--summary`` / ``--description-file`` / ``--category-id``：

    { "summary": "...", "description": "...", "categoryId": 4483 }

* ``classId`` / ``gameCategoryIds`` **不暴露** —— 无法在离线环境确认 Core API 的
  ModUpdate 模型是否接受它们（按「未确认就不加」的口径处理）。
* ``description`` 按**原样**发送（不做 Markdown→HTML 转换）；CF 站点按 HTML 渲染，
  用 README.md 当描述时语义会跑偏 ⇒ 计划里会给出提示，见交付说明的「风险」。
* 长度校验：``summary`` ≤ 500 字符（CurseForge 项目摘要字符上限，按字符数计，
  emoji 也算 1 个 code point）；``description`` ≤ 100000 字符（本地保守闸门，
  **不是**官方公布上限）。
* ⚠️ 该端点**本次未实测**（沙箱无网络、无凭据）：字段语义来自 CurseForge
  API 文档的 ModUpdate 模型；若服务端是「整体替换」而非「部分更新」，只发
  这三个字段可能把其它字段清空 —— 因此真实执行前脚本会先 ``GET /v1/mods/{id}``
  打印旧值，并把它写进回滚提示。
"""

import argparse
import json
import os
import pathlib
import re
import shutil
import ssl
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
# --- stdout/stderr 编码自愈（统一口径：python 侧强制 UTF-8；勿删）---
# 背景：Windows 默认代码页 936 下 python 子进程的 sys.stdout.encoding 为 gbk，
# 本脚本打印的 ⇒/⚠/✅/↔ 等符号 GBK 不可表示 ⇒ 直接 UnicodeEncodeError
# 退出码 1（“门会红但红的原因是崩溃”）。此块使脚本不依赖调用者环境与宿主编码。
# ⚠ 块内引用 sys ⇒ 必须位于**所有 import 之后**；也**不得**加 except-pass（会静默失效）。
def _astral_heal_stdio() -> None:  # pragma: no cover - 环境适配
    for _stream_name in ("stdout", "stderr"):
        _stream = getattr(sys, _stream_name, None)
        _reconf = getattr(_stream, "reconfigure", None)
        if _reconf is None:
            continue
        try:
            _reconf(encoding="utf-8", errors="replace")
        except Exception as _exc:  # noqa: BLE001 - 明确告警，绝不静默
            sys.stderr.write(
                "[astral-gate] WARN: %s.reconfigure(utf-8) failed: %r\n"
                % (_stream_name, _exc)
            )


_astral_heal_stdio()

API_BASE = "https://minecraft.curseforge.com/api"
# 官方 Core API v1（项目元数据 / 浏览类接口）：域与鉴权头都不同于上传用的 API_BASE。
CORE_API_BASE = "https://api.curseforge.com/v1"
DEFAULT_PROJECT_ID = "1662159"  # 兜底值；正式绑定见 tools/curseforge.json
BINDING_FILE = "tools/curseforge.json"
UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
JAR_RE = re.compile(r"^astral_dice-(?P<ver>[^+]+)\+(?P<loader>[a-z]+)_(?P<mc>.+)\.jar$")
# StarEngine 库产物: starengine-<平台>-<版本>.jar（平台含 MC 版本，如 neoforge-1.21.1）
LIB_JAR_RE = re.compile(r"^starengine-(?P<loader>neoforge|forge|fabric)-(?P<mc>\d+\.\d+(?:\.\d+)?)-(?P<ver>.+)\.jar$")
CACHE_DAYS = 7
# CurseForge 官方文档给出的 relation type 全集（没有 include 这一项）。
RELATION_TYPES = ("requiredDependency", "optionalDependency", "embeddedLibrary", "incompatible", "tool")

# 项目摘要字符上限（CurseForge 站点「Summary」字段）。按 code point 计数。
SUMMARY_MAX_CHARS = 500
# 描述长度**本地**保守闸门（不是官方公布上限；只是防止误传整份巨型文件）。
DESCRIPTION_MAX_CHARS = 100000
# 项目信息里允许出现的字段白名单（未确认的 classId / gameCategoryIds 故意不列）。
INFO_FIELDS = ("summary", "description", "categoryId")

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


# ---------------------------------------------------------------- curl 传输回退
# 背景（2026-10-23 实测）：Cloudflare 对 python urllib 的 TLS/HTTP 指纹直接回 403
# 「Just a moment...」（minecraft.curseforge.com/api/**），而系统 curl 直连可用（200）。
# 故 urllib 命中 Cloudflare 挑战页时自动改用 curl 重试：直连优先（代理出口同样被拦），
# 直连失败再试一次代理。写请求（上传 / update-file）与读请求（版本表）都要走这条路。
CURL_BIN = shutil.which("curl") or "curl"
CURL_PROXY = None            # 由 make_ctx 注入（--proxy / HTTPS_PROXY），供无 ctx 的 GET 回退使用
CF_CHALLENGE_MARKERS = ("just a moment", "cf-chl", "attention required", "cf-mitigated")


def _is_cf_challenge(status, body):
    """judge 一次响应是否 Cloudflare 挑战页（只有它才值得换 curl 重试）。"""
    if status not in (403, 503):
        return False
    head = (body or "")[:6000].lower()
    return any(m in head for m in CF_CHALLENGE_MARKERS)


def _curl_run(argv):
    """跑一次 curl，返回 (status, body)；status=0 表示未执行/未取到状态码。"""
    try:
        p = subprocess.run([CURL_BIN, "-sS", "-w", "\n__HTTP__%{http_code}"] + argv,
                           capture_output=True, text=True, encoding="utf-8", errors="replace")
    except OSError as e:  # pragma: no cover - 环境缺 curl
        return 0, "curl 启动失败：%s" % e
    out = p.stdout or ""
    m = re.search(r"\n__HTTP__(\d+)\s*$", out)
    if not m:
        return 0, (out + (p.stderr or "")).strip()
    return int(m.group(1)), out[: m.start()]


def _curl_attempts(proxy):
    tries = [["--noproxy", "*"]]
    if proxy:
        tries.append(["-x", proxy])
    return tries


def _curl_with_fallback(build_args, proxy):
    last = (0, "")
    for base in _curl_attempts(proxy):
        status, body = _curl_run(base + build_args())
        if status and not _is_cf_challenge(status, body):
            return status, body
        last = (status, body)
    return last


def _hdr_args(headers, drop_content_type=True):
    """把 headers 转 curl `-H` 参数。

    multipart 的 `-F` 自己带 boundary，调用方给的 `Content-Type` 必须丢掉；
    但 JSON 请求（`--data-binary`）**不能丢**，否则 curl 默认发
    `application/x-www-form-urlencoded`（2026-10-23 复核 P2）。
    """
    out = []
    for k, v in (headers or {}).items():
        low = k.lower()
        if low in ("user-agent", "content-length"):
            continue
        if low == "content-type" and drop_content_type:
            continue
        out += ["-H", "%s: %s" % (k, v)]
    return out


def _curl_multipart(url, headers, metadata, jar_path=None, proxy=None):
    """curl 版 multipart POST（metadata 走文件，见 2026-10-23 实测：内联会 400 Invalid JSON）。"""
    fd, tmp = tempfile.mkstemp(suffix=".json")
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as f:
            json.dump(metadata, f, ensure_ascii=False)

        def build():
            args = ["-X", "POST", "-A", UA] + _hdr_args(headers)
            args += ["-F", "metadata=<" + tmp]
            if jar_path is not None:
                args += ["-F", "file=@" + str(jar_path)]
            args.append(url)
            return args

        return _curl_with_fallback(build, proxy)
    finally:
        try:
            os.unlink(tmp)
        except OSError:
            pass


def _curl_json(url, method, headers, payload, proxy=None):
    """curl 版 JSON 请求（GET 无 body；PUT/POST 用 --data-binary 读临时文件）。"""
    tmp = None
    try:
        if payload is not None:
            fd, tmp = tempfile.mkstemp(suffix=".json")
            with os.fdopen(fd, "w", encoding="utf-8") as f:
                json.dump(payload, f, ensure_ascii=False)

        has_ct = any(k.lower() == "content-type" for k in (headers or {}))

        def build():
            args = ["-X", method, "-A", UA] + _hdr_args(headers, drop_content_type=False)
            if tmp and not has_ct:
                # --data-binary 的默认 content-type 是 urlencoded；JSON 端点必须显式声明。
                args += ["-H", "Content-Type: application/json"]
            if tmp:
                args += ["--data-binary", "@" + tmp]
            args.append(url)
            return args

        return _curl_with_fallback(build, proxy)
    finally:
        if tmp:
            try:
                os.unlink(tmp)
            except OSError:
                pass

def api_get(opener, path, token):
    return api_get_url(opener, API_BASE + path, {
        "X-Api-Token": token, "User-Agent": UA, "Accept": "application/json"})


def api_get_url(opener, url, headers):
    """GET 任意 URL 并解析 JSON（Core API v1 用；调用方自带鉴权头）。"""
    req = urllib.request.Request(url, headers=headers, method="GET")
    try:
        with opener.open(req, timeout=60) as r:
            return json.loads(r.read().decode("utf-8", "replace"))
    except urllib.error.HTTPError as e:
        status, body = e.code, e.read().decode("utf-8", "replace")
        if not _is_cf_challenge(status, body):
            raise
        print("[curl] Cloudflare 拦截 urllib（HTTP %s）⇒ 改用 curl 重试：%s" % (status, url))
        cstatus, cbody = _curl_json(url, "GET", headers, None, CURL_PROXY)
        if not cstatus or cstatus >= 400:
            raise RuntimeError("curl 回退失败（HTTP %s）：%s" % (cstatus, cbody[:400]))
        return json.loads(cbody)


def core_headers(ctx, json_body=False):
    """Core API v1 的请求头。

    * ``x-api-key``  = 官方 Core API Key（--api-key / CURSEFORGE_API_KEY，可能回退到旧 token）
    * ``X-Api-Token`` = 旧版项目 token（沿用 resolve_token；多带不伤害，服务端忽略）
    """
    h = {"User-Agent": UA, "Accept": "application/json"}
    if ctx.get("api_key"):
        h["x-api-key"] = ctx["api_key"]
    if ctx.get("token"):
        h["X-Api-Token"] = ctx["token"]
    if json_body:
        h["Content-Type"] = "application/json"
    return h


def api_put_json(opener, url, headers, payload):
    """PUT JSON（项目信息更新用）。返回 (status, body)，HTTPError 也折成返回值。"""
    data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers=headers, method="PUT")
    try:
        with opener.open(req, timeout=300) as r:
            return r.status, r.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        status, body = e.code, e.read().decode("utf-8", "replace")
        if not _is_cf_challenge(status, body):
            return status, body
        print("[curl] Cloudflare 拦截 urllib（HTTP %s）⇒ 改用 curl 重试：%s" % (status, url))
        return _curl_json(url, "PUT", headers, payload, CURL_PROXY)


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
        status, body = e.code, e.read().decode("utf-8", "replace")
        if not _is_cf_challenge(status, body):
            return status, body
        print("[curl] Cloudflare 拦截 urllib（HTTP %s）⇒ 改用 curl 上传：%s" % (status, jar_path.name))
        return _curl_multipart(f"{API_BASE}/projects/{project_id}/upload-file",
                               {"X-Api-Token": token}, metadata, jar_path, CURL_PROXY)


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
        status, body = e.code, e.read().decode("utf-8", "replace")
        if not _is_cf_challenge(status, body):
            return status, body
        print("[curl] Cloudflare 拦截 urllib（HTTP %s）⇒ 改用 curl 提交：%s" % (status, url))
        return _curl_multipart(url, {"X-Api-Token": token}, metadata, None, CURL_PROXY)


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
    if m:
        return m.group("ver"), m.group("loader"), m.group("mc")
    m = LIB_JAR_RE.match(jar.name)
    if m:
        return m.group("ver"), m.group("loader"), m.group("mc")
    raise SystemExit(
        "[ERR] jar 名无法解析。支持两种："
        "astral_dice-<版本>+<加载器>_<MC版本>.jar / starengine-<平台>-<版本>.jar："
        f"{jar.name}")


def is_lib_jar(name):
    return bool(LIB_JAR_RE.match(name))


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


# ---------------------------------------------------------------- 版本表（离线规划）
def load_catalog_offline():
    """dry-run 专用：只读本地缓存，**绝不联网**；没有缓存则返回 (None, 说明)。"""
    if not VERSIONS_CACHE.is_file():
        return None, f"（无本地缓存 {rel_display(VERSIONS_CACHE)}；dry-run 不联网，gameVersions 留空）"
    try:
        cat = json.loads(VERSIONS_CACHE.read_text(encoding="utf-8"))
    except Exception as e:  # noqa: BLE001 - 缓存坏了就按「无缓存」处理并说明
        return None, f"（本地缓存无法解析：{e}；dry-run 不联网）"
    age_days = (time.time() - VERSIONS_CACHE.stat().st_mtime) / 86400
    return cat, f"离线缓存 {rel_display(VERSIONS_CACHE)}（{age_days:.1f} 天前；dry-run 不联网）"


def plan_ids(cat, mc, loader, offline):
    """解析 gameVersion ids；离线规划时解析失败降级为告警而不是中止。"""
    if cat is None:
        return [], {"(离线)": "未解析"}
    try:
        return resolve_ids(cat, mc, loader)
    except SystemExit as e:
        if not offline:
            raise
        print(f"[WARN] dry-run 离线规划：{e}")
        return [], {"(未解析)": "见上方告警"}


# ---------------------------------------------------------------- 项目信息（元数据）
def _read_text(path):
    """读文本：容 BOM，正文一律 UTF-8。"""
    return path.read_text(encoding="utf-8-sig")


def normalize_summary(text):
    """摘要字段是单行短文本 ⇒ 折叠所有空白；返回 (规范化文本, 是否发生过折叠)。"""
    s = re.sub(r"\s+", " ", text).strip()
    return s, (s != text.strip())


def validate_summary(text):
    """长度 / 合法性校验。CurseForge 项目摘要上限 500 字符（按 code point 计）。"""
    if not text or not text.strip():
        raise SystemExit("[ERR] summary 不能为空（给 --summary \"...\"，或让 README.md 首个摘要行非空）")
    if "\x00" in text:
        raise SystemExit("[ERR] summary 含 NUL 字符（\\x00），无法提交")
    n = len(text)
    if n > SUMMARY_MAX_CHARS:
        raise SystemExit(
            f"[ERR] summary 超长：{n} 字符 > 上限 {SUMMARY_MAX_CHARS}"
            f"（CurseForge 项目摘要字符上限；需再删 {n - SUMMARY_MAX_CHARS} 个字符，"
            f"emoji 按 1 个字符计）")
    return text


def validate_description(text):
    """描述校验：非空、无 NUL、本地保守长度闸门（非官方上限）。"""
    if text is None or not text.strip():
        raise SystemExit("[ERR] description 为空（--description-file 指向的文件没有正文）")
    if "\x00" in text:
        raise SystemExit("[ERR] description 含 NUL 字符（\\x00），无法提交")
    n = len(text)
    if n > DESCRIPTION_MAX_CHARS:
        raise SystemExit(
            f"[ERR] description 超长：{n} 字符 > 本地闸门 {DESCRIPTION_MAX_CHARS}"
            f"（这是脚本的保守上限，非官方公布值；确认要传就调高 "
            f"tools/curseforge_upload.py 的 DESCRIPTION_MAX_CHARS）")
    return text


def validate_category_id(value):
    """categoryId 必须是正整数（单个分类；Minecraft 分类 id 例如 4483 = Adventure and RPG）。"""
    try:
        v = int(value)
    except (TypeError, ValueError):
        raise SystemExit(f"[ERR] --category-id 必须是整数，收到 {value!r}")
    if v <= 0:
        raise SystemExit(f"[ERR] --category-id 必须是正整数，收到 {v}")
    return v


_LANG_SWITCH_RE = re.compile(r"^\*\*(English|中文|简体中文|日本語|한국어)\*\*")


def default_summary():
    """从 README.md 取默认摘要行（跳过标题 / 语言切换 / 徽章 / 引用 / 分隔线）。

    返回 (文本, 来源说明)；取不到返回 (None, None)。
    """
    p = REPO_ROOT / "README.md"
    if not p.is_file():
        return None, None
    for raw in _read_text(p).splitlines():
        line = raw.strip()
        if not line or line.startswith(("#", ">", "|", "---", "![", "<")):
            continue
        if _LANG_SWITCH_RE.match(line):
            continue
        text = re.sub(r"\[([^\]]*)\]\([^)]*\)", r"\1", line)  # markdown 链接 → 文字
        text = text.replace("**", "").replace("__", "").replace("`", "")
        text = re.sub(r"\s+", " ", text).strip()
        if len(text) >= 20:
            return text, f"{rel_display(p)} 首个摘要行"
    return None, None


def build_info_payload(args):
    """组装 PUT /v1/mods/{id} 的请求体（只含 summary / description / categoryId）。

    返回 (payload, sources)；sources 记录每个字段的取值来源，供计划与交付说明核对。
    """
    payload, src = {}, {}
    if getattr(args, "summary", None) is not None:
        text, folded = normalize_summary(args.summary)
        validate_summary(text)
        payload["summary"] = text
        src["summary"] = "--summary" + ("（已折叠空白）" if folded else "")
    else:
        text, where = default_summary()
        if not text:
            raise SystemExit(
                "[ERR] 没有可用的 summary：README.md 里没找到摘要行，"
                "请显式给 --summary \"<≤500 字符的一句话>\"")
        text = validate_summary(text)
        payload["summary"] = text
        src["summary"] = where + "（默认）"

    if getattr(args, "description_file", None):
        p = pathlib.Path(args.description_file).resolve()
        if not p.is_file():
            raise SystemExit(f"[ERR] 指定的描述文件不存在：{p}")
        text = validate_description(_read_text(p))
        payload["description"] = text
        src["description"] = f"{rel_display(p)}（{len(text)} 字符，原样发送）"
        if "<" not in text:
            print("[WARN] description 里没有任何 '<'：CF 站点按 HTML 渲染，"
                  "这份内容看起来是纯文本/Markdown，脚本不做 Markdown→HTML 转换")

    if getattr(args, "category_id", None) is not None:
        payload["categoryId"] = validate_category_id(args.category_id)
        src["categoryId"] = "--category-id"

    if not payload:
        raise SystemExit("[ERR] 没有要更新的字段（给 --summary / --description-file / --category-id）")
    return payload, src


def write_json_out(path, payload):
    p = pathlib.Path(path).resolve()
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"JSON 已落盘 = {p}（只含项目信息，不含任何凭据）")


def print_info_plan(ctx, payload, src):
    """打印项目信息计划 + 将要发送的 JSON（dry-run 的验收依据）。"""
    mod_id = ctx["project_id"]
    print(f"项目 projectId = {mod_id}")
    print(f"端点           = PUT /v1/mods/{mod_id}  →  {CORE_API_BASE}/mods/{mod_id}（Core API v1）")
    print(f"凭据           = token: {ctx['token_src']} ；api-key: {ctx['api_key_src']}")
    print(f"代理           = {ctx['proxy'] or '（直连；若被 Cloudflare 403 请加 --proxy）'}")
    print("-" * 72)
    for k in INFO_FIELDS:
        if k not in payload:
            continue
        v = payload[k]
        if k == "summary":
            print(f"  summary     {len(v)}/{SUMMARY_MAX_CHARS} 字符   来源 {src[k]}")
        elif k == "description":
            print(f"  description {len(v)} 字符   来源 {src[k]}")
        else:
            print(f"  {k:<11} {v}   来源 {src[k]}")
    print("-" * 72)
    print("将要发送的 JSON：")
    print(json.dumps(payload, ensure_ascii=False, indent=2))


def resolve_api_key(explicit, legacy_token):
    """Core API v1 的 x-api-key：--api-key > 环境变量 > 回退旧 token（会告警）。"""
    key = (explicit or os.environ.get("CURSEFORGE_API_KEY") or "").strip()
    if key:
        return key, ("--api-key" if explicit else "环境变量 CURSEFORGE_API_KEY"), False
    if legacy_token:
        return legacy_token, "回退：用旧版 X-Api-Token 充当 x-api-key（未验证可过）", True
    return "", "（无）", False


def resolve_token_safe(explicit, write):
    """dry-run 允许没有 token（不发请求）；真实写入时沿用原 resolve_token 的硬失败。"""
    try:
        return resolve_token(explicit)
    except SystemExit:
        if write:
            raise
        return "", "（dry-run：未找到 token，已跳过解析）"


def write_enabled(args):
    """默认 dry-run 的唯一判定点。返回 (是否真发写请求, 原因)。"""
    if getattr(args, "dry_run", False):
        return False, "显式 --dry-run"
    if getattr(args, "apply", False):
        return True, "显式 --apply/--yes"
    return False, "未给 --apply/--yes ⇒ 默认 dry-run（不发任何写请求）"


def make_ctx(args, write):
    binding = load_binding()
    project_id = str(args.project_id or binding.get("projectId") or DEFAULT_PROJECT_ID)
    # 库 jar 默认推到库项目（tools/curseforge.json 的 lib.projectId）
    _jars = [os.path.basename(str(p)) for p in (getattr(args, "jar", None) or [])]
    if (_jars and all(is_lib_jar(j) for j in _jars)
            and not args.project_id and binding.get("lib", {}).get("projectId")):
        project_id = str(binding["lib"]["projectId"])
        # 库产物不声明本体那套 relations（库的 mods.toml 不要求 Curios/Mixin Booster）
        if hasattr(args, "no_relations"):
            args.no_relations = True
    token, token_src = resolve_token_safe(args.token, write)
    api_key, api_key_src, fallback = resolve_api_key(getattr(args, "api_key", None), token)
    proxy = (args.proxy or os.environ.get("HTTPS_PROXY") or os.environ.get("https_proxy"))
    global CURL_PROXY
    CURL_PROXY = proxy
    return {"binding": binding, "project_id": project_id,
            "token": token, "token_src": token_src,
            "api_key": api_key, "api_key_src": api_key_src, "key_fallback": fallback,
            "proxy": proxy, "opener": make_opener(proxy)}


# ---------------------------------------------------------------- 步骤记录 / 回滚提示
class StepLog:
    """按步记录执行结果；失败时整表回放（禁止静默继续）。"""

    def __init__(self):
        self.rows = []

    def _add(self, status, label, detail=""):
        self.rows.append((status, label, detail))
        print(f"  [{len(self.rows)}] [{status}] {label}" + (f"  {detail}" if detail else ""))
        return self.rows[-1]

    def done(self, label, detail=""):
        return self._add("OK  ", label, detail)

    def fail(self, label, detail=""):
        return self._add("FAIL", label, detail)

    def dump(self, title):
        print("-" * 72)
        print(f"{title}：")
        if not self.rows:
            print("  （没有已完成的步骤）")
        for i, (status, label, detail) in enumerate(self.rows, 1):
            print(f"  [{i}] [{status}] {label}" + (f"  {detail}" if detail else ""))


def report_abort(steps, slug, uploaded, info_attempted, old_info):
    """失败收尾：回放已完成步骤 + 给可执行的回滚/修复提示，然后返回 1 由调用方退出。"""
    steps.dump("失败中止 —— 步骤回放（含失败步骤）")
    print("-" * 72)
    print("回滚 / 修复提示：")
    hints = []
    for name, fid in uploaded:
        if fid:
            hints.append(
                f"{name} 已发布为 fileID={fid}；Core API 没有删除文件的接口 ⇒ "
                f"到 https://www.curseforge.com/minecraft/mc-mods/{slug or '<slug>'}/files "
                f"后台人工删除（或先用 update-file 改 displayName 打标）")
        else:
            hints.append(f"{name} 的上传回执里没解析出 fileID ⇒ 先到站点文件列表核对是否已建件，再决定重传")
    if info_attempted:
        if old_info:
            hints.append("项目信息可能已被改动：旧值 = summary={!r} / categoryId={!r}；"
                         "description 旧正文请用 GET /v1/mods/{id} 取回后用 --description-file 回滚"
                         .format(str(old_info.get("summary", ""))[:120],
                                 old_info.get("categoryId")))
        else:
            hints.append("项目信息 PUT 失败或旧值未知：先 GET /v1/mods/{id} 核对 summary/description "
                         "是否已被改动，再决定是否用旧值重发")
    if not hints:
        hints.append("没有任何写请求成功 ⇒ 无副作用，可直接修参数重跑")
    for h in hints:
        print(f"  - {h}")
    print("  - 参数改好后可先加 --dry-run 复核计划，再 --apply 重跑（已成功的文件不要重复传）")
    return 1


# ---------------------------------------------------------------- 上传计划（push / 旧式共用）
# ⛔ 2026-10-25 用户裁决（强制，优先级高于本文件其余说明）
# 默认只上传 P0 两线 = `neoforge-1.21.1` + `forge-1.20.1` 的产物；P1 两线
# （`neoforge-26.1.2` + `fabric-1.20.1`）从未获授权移植，其编译产物一律视为不可用。
# 仅在用户当次点名要求发布 P1 时才允许 `--allow-p1` 放行（逐次都要显式给）。
P0_UPLOAD_LINES = {("neoforge", "1.21.1"), ("forge", "1.20.1")}


def guard_p0_only(loader, mc, jar, allow_p1):
    """P0-only 闸门：拒绝上传 P1 线产物（2026-10-25 用户裁决）。"""
    if (str(loader), str(mc)) in P0_UPLOAD_LINES:
        return
    if allow_p1:
        print(f"[WARN] P1 产物上传已放行（--allow-p1）：{jar.name} | {loader} | MC {mc}"
              " —— 仅限用户逐次点名下达的任务")
        return
    raise SystemExit(
        f"[BLOCKED] {jar.name} 属于 P1 线（{loader} / MC {mc}），按 2026-10-25 用户裁决默认禁止上传：\n"
        "          P1 两线（neoforge-26.1.2 / fabric-1.20.1）从未获授权移植，其编译产物一律视为不可用。\n"
        "          允许上传的只有 P0 两线：neoforge-1.21.1 与 forge-1.20.1。\n"
        "          若本次任务确由用户点名要求发布 P1，请显式加 --allow-p1。")

def build_jobs(args, binding, cat, offline):
    jobs = []
    for raw in args.jar:
        jar = pathlib.Path(raw).resolve()
        if not jar.is_file():
            raise SystemExit(f"[ERR] 找不到 jar：{jar}")
        ver, loader, mc = parse_jar(jar)
        loader = args.loader or loader
        mc = args.mc_version or mc
        # 2026-10-25 rule: only P0 lines may be uploaded (P1 blocked by default)
        guard_p0_only(loader, mc, jar, getattr(args, 'allow_p1', False))
        rtype = args.release_type or infer_release_type(ver)
        ids, detail = plan_ids(cat, mc, loader, offline)
        line = find_line(binding, key=args.line, loader=loader, mc=mc)
        rel = relations_payload(line, disabled=args.no_relations)
        jobs.append({"jar": jar, "ver": ver, "loader": loader, "mc": mc,
                     "releaseType": rtype, "gameVersions": ids, "_detail": detail,
                     "_line": (line or {}).get("key"), "_relations": rel,
                     "_changelog": pick_changelog(ver, args.changelog)})
    return jobs


def print_jobs_plan(jobs):
    for j in jobs:
        cl = j["_changelog"]
        if cl:
            cl_desc = f"{rel_display(cl)}（{len(_read_text(cl))} 字符）"
        else:
            cl_desc = "（无，留空）"
        print(f"  {j['jar'].name}")
        print(f"    版本 {j['ver']} | {j['loader']} | MC {j['mc']} | 类型 {j['releaseType']}")
        print(f"    gameVersions = {j['gameVersions']}  {j['_detail']}")
        print(f"    更新日志 = {cl_desc}")
        print(f"    relations = {describe_relations(j['_relations'])}"
              + (f"  [{j['_line']}]" if j.get("_line") else "  [未匹配到该线]"))


def upload_metadata(args, job):
    metadata = {
        "displayName": args.display_name or job["jar"].name,
        "releaseType": job["releaseType"],
        "gameVersions": job["gameVersions"],
        "changelogType": args.changelog_type,
    }
    if job["_changelog"]:
        metadata["changelog"] = _read_text(job["_changelog"])
    if job.get("_relations"):
        metadata["relations"] = job["_relations"]
    if args.manual_release:
        metadata["isMarkedForManualRelease"] = True
    return metadata


def extract_file_id(body):
    try:
        d = json.loads(body)
    except Exception:  # noqa: BLE001 - 回执不是 JSON 时按「未知」处理
        return None
    if isinstance(d, dict):
        for k in ("id", "fileID", "fileId"):
            if d.get(k) is not None:
                return d[k]
        if isinstance(d.get("data"), dict):
            return d["data"].get("id")
    return None


def run_update_info(ctx, payload, src):
    """真发 PUT /v1/mods/{id}：先 GET 旧值（供回滚），再 PUT。返回 (ok, code, body, old)。"""
    url = f"{CORE_API_BASE}/mods/{ctx['project_id']}"
    print(f"  [..] GET  {url}（读旧值，供回滚）")
    old = {}
    try:
        cur = api_get_url(ctx["opener"], url, core_headers(ctx))
        old = (cur or {}).get("data") or {}
        if old:
            print("  [OK  ] 读取当前项目信息（用于回滚）：")
            print(f"         summary    = {str(old.get('summary', ''))[:120]!r}")
            print(f"         description = {len(str(old.get('description', '')))} 字符（正文略）")
            print(f"         categoryId = {old.get('categoryId')}")
    except Exception as e:  # noqa: BLE001 - 读不到旧值不阻断，但必须显式告警
        print(f"  [WARN] 读取当前项目信息失败：{e!r}（继续 PUT；回滚将缺少旧值）")
    print(f"  [..] PUT  {url}")
    print(f"       字段 = {', '.join(k for k in INFO_FIELDS if k in payload)}"
          f"（来源：{'; '.join(f'{k}←{src.get(k)}' for k in INFO_FIELDS if k in payload)}）")
    code, body = api_put_json(ctx["opener"], url, core_headers(ctx, json_body=True), payload)
    ok = 200 <= code < 300
    print(f"  [{'OK  ' if ok else 'FAIL'}] PUT /v1/mods/{ctx['project_id']}  HTTP {code}  {body[:300]}")
    return ok, code, body, old


# ---------------------------------------------------------------- 子命令：update-info
def add_auth_args(p):
    p.add_argument("--project-id", default=None,
                   help=f"覆盖项目 id（默认取 {BINDING_FILE} 的 projectId，兜底 {DEFAULT_PROJECT_ID}）")
    p.add_argument("--token", help="旧版项目 token（默认读 CURSEFORGE_TOKEN 或 .curseforge/token）")
    p.add_argument("--api-key",
                   help="Core API v1 的 x-api-key（PUT /v1/mods/{id} 用；"
                        "默认读 CURSEFORGE_API_KEY，缺失时回退到旧 token 并告警）")
    p.add_argument("--proxy", default=None,
                   help="本地代理，如 http://127.0.0.1:7897（也可用环境变量 HTTPS_PROXY）")


def add_write_args(p):
    p.add_argument("--apply", "--yes", dest="apply", action="store_true",
                   help="真的发写请求（不给则默认 dry-run）")
    p.add_argument("--dry-run", action="store_true",
                   help="只打印计划与将要发送的 JSON，不发任何请求（默认行为）")


def add_info_args(p, with_switch=False):
    if with_switch:
        p.add_argument("--update-info", action="store_true",
                       help="本次同时更新项目信息（summary/description/categoryId）")
    p.add_argument("--summary", metavar="TEXT",
                   help=f"项目摘要（≤{SUMMARY_MAX_CHARS} 字符；不给则取 README.md 首个摘要行）")
    p.add_argument("--description-file", metavar="PATH",
                   help="项目描述正文来源文件（原样发送，不做 Markdown→HTML 转换）")
    p.add_argument("--category-id", type=int, metavar="ID",
                   help="单个分类 id（Minecraft 例如 4483 = Adventure and RPG）")
    p.add_argument("--json-out", metavar="PATH",
                   help="把将要发送的项目信息 JSON 另存一份（便于复核；不含凭据）")


UPDATE_INFO_EPILOG = """\
子命令 update-info —— 只改项目元数据（默认 dry-run）

  PUT https://api.curseforge.com/v1/mods/{modId}   （官方 Core API v1，鉴权 x-api-key）

  支持字段（白名单，未确认的 classId / gameCategoryIds 不暴露）：
    --summary TEXT            项目摘要，≤500 字符；不给则取 README.md 首个摘要行
    --description-file PATH   项目描述正文（原样发送）
    --category-id ID          单个分类 id

示例：
  python3 tools/curseforge_upload.py update-info --dry-run \\
      --summary "Dice combat, signs, chips and cards for survival" \\
      --description-file README.md
  python3 tools/curseforge_upload.py update-info --apply \\
      --summary "..." --description-file README.md --category-id 4483
"""


def build_update_info_parser():
    p = argparse.ArgumentParser(
        prog="curseforge_upload.py update-info",
        description="修改 CurseForge 项目信息（PUT /v1/mods/{modId}；默认 dry-run）",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=UPDATE_INFO_EPILOG)
    add_info_args(p, with_switch=False)
    add_auth_args(p)
    add_write_args(p)
    return p


def cmd_update_info(args):
    write, why = write_enabled(args)
    ctx = make_ctx(args, write)
    payload, src = build_info_payload(args)
    if args.json_out:
        write_json_out(args.json_out, payload)
    print_info_plan(ctx, payload, src)
    print(f"写请求         = {'是（%s）' % why if write else '否（%s）' % why}")
    if not write:
        print("-" * 72)
        print("DRY-RUN：未发送任何请求。确认无误后加 --apply 才会真发 PUT。")
        return 0
    if not ctx["api_key"]:
        raise SystemExit("[ERR] 真实 PUT 需要凭据：给 --api-key 或设 CURSEFORGE_API_KEY"
                         "（Core API v1 用 x-api-key，不是上传用的 X-Api-Token）")
    if ctx["key_fallback"]:
        print("[WARN] 未提供 --api-key / CURSEFORGE_API_KEY ⇒ 用旧版 token 充当 x-api-key（未验证）")
    ok, code, _body, old = run_update_info(ctx, payload, src)
    if ok:
        print("-" * 72)
        print("完成：项目信息已更新。回滚提示见上方打印的旧值。")
        return 0
    steps = StepLog()
    steps.fail(f"PUT /v1/mods/{ctx['project_id']}", f"HTTP {code}")
    return report_abort(steps, ctx["binding"].get("slug"), [], True, old)


# ---------------------------------------------------------------- 子命令：push
PUSH_EPILOG = """\
子命令 push —— 上传 jar → relations →（可选）更新项目信息（默认 dry-run）

  步骤（任一失败立刻中止，并打印已完成步骤 + 回滚提示）：
    1) POST /projects/{id}/upload-file     上传 jar（multipart：metadata + file）
    2) relations                           随**同一次**上传 payload 提交（不额外调 update-file）
    3) PUT  /v1/mods/{id}                  仅当给 --update-info（或给了 --summary 等）时执行

  dry-run 完全离线：只读 .curseforge/versions.json 缓存，不发任何请求。

示例：
  python3 tools/curseforge_upload.py push --dry-run \\
      --jar neoforge-1.21.1/build/libs/astral_dice-1.5.0+neoforge_1.21.1.jar
  python3 tools/curseforge_upload.py push --apply \\
      --jar neoforge-1.21.1/build/libs/astral_dice-1.5.0+neoforge_1.21.1.jar \\
      --update-info --summary "..." --description-file README.md
"""


def build_push_parser():
    p = argparse.ArgumentParser(
        prog="curseforge_upload.py push",
        description="一条命令完成「上传 jar → relations →（可选）更新项目信息」（默认 dry-run）",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=PUSH_EPILOG)
    p.add_argument("--jar", action="append", metavar="PATH", required=True,
                   help="要上传的 jar（可重复；相对路径按当前目录解析）")
    p.add_argument("--changelog", help="更新日志文件（默认自动取 release/<版本>/PLAYER_CHANGELOG*.md）")
    p.add_argument("--changelog-type", default="markdown", choices=["markdown", "html", "text"])
    p.add_argument("--display-name", help="站点显示名（默认 = jar 文件名）")
    p.add_argument("--mc-version", help="覆盖从文件名解析出的 MC 版本")
    p.add_argument("--loader", help="覆盖从文件名解析出的加载器（如 neoforge / forge）")
    p.add_argument("--release-type", choices=["release", "beta", "alpha"],
                   help="覆盖按版本号推断的发布类型")
    p.add_argument("--manual-release", action="store_true",
                   help="标记为「手动发布」（审核通过后不自动公开）")
    p.add_argument("--line", help=f"显式指定 {BINDING_FILE} 里的线 key（如 forge_1.20.1）")
    p.add_argument("--no-relations", action="store_true",
                   help="本次不发送 binding 里声明的 relations（默认发送）")
    p.add_argument("--refresh-versions", action="store_true",
                   help="忽略缓存重新拉取版本表（仅 --apply 时生效）")
    p.add_argument("--allow-p1", action="store_true",
                   help="放行 P1 两线产物上传（neoforge-26.1.2 / fabric-1.20.1）；"
                        "仅在用户当次点名要求发布 P1 时使用，默认禁止")
    add_info_args(p, with_switch=True)
    add_auth_args(p)
    add_write_args(p)
    return p


def cmd_push(args):
    write, why = write_enabled(args)
    ctx = make_ctx(args, write)
    want_info = bool(args.update_info or args.summary is not None
                     or args.description_file or args.category_id is not None)
    payload, src = (build_info_payload(args) if want_info else (None, None))
    if args.json_out and payload:
        write_json_out(args.json_out, payload)

    if write:
        cat, ver_src = load_catalog(ctx["opener"], ctx["token"], force=args.refresh_versions)
    else:
        cat, ver_src = load_catalog_offline()
    jobs = build_jobs(args, ctx["binding"], cat, offline=not write)

    src_binding = BINDING_FILE if ctx["binding"].get("projectId") else "脚本内置兜底值"
    print(f"项目 projectId = {ctx['project_id']}"
          + (f"（slug: {ctx['binding'].get('slug')}，来源 {src_binding}）" if ctx["binding"] else ""))
    print(f"凭据           = token: {ctx['token_src']} ；api-key: {ctx['api_key_src']}")
    print(f"代理           = {ctx['proxy'] or '（直连；若被 Cloudflare 403 请加 --proxy）'}")
    print(f"版本表         = {ver_src}")
    print(f"写请求         = {'是（%s）' % why if write else '否（%s）' % why}")
    print("-" * 72)
    print_jobs_plan(jobs)
    if payload:
        print("-" * 72)
        print("[步骤 3] 项目信息（可选）：")
        print_info_plan(ctx, payload, src)

    if not write:
        print("-" * 72)
        print("DRY-RUN：未发送任何请求。确认无误后加 --apply 才会真跑。")
        return 0
    if not ctx["token"]:
        raise SystemExit("[ERR] 真实上传需要 token：--token / CURSEFORGE_TOKEN / .curseforge/token")

    steps = StepLog()
    uploaded = []
    slug = ctx["binding"].get("slug")
    print("-" * 72)
    print("开始执行：")
    for j in jobs:
        metadata = upload_metadata(args, j)
        code, body = api_upload(ctx["opener"], ctx["project_id"], ctx["token"], metadata, j["jar"])
        ok = 200 <= code < 300
        if not ok:
            steps.fail(f"上传 {j['jar'].name}", f"HTTP {code}  {body[:200]}")
            # 上传阶段就失败 ⇒ 项目信息 PUT 根本没跑，不能提示「可能已改动」
            return report_abort(steps, slug, uploaded, False, {})
        fid = extract_file_id(body)
        uploaded.append((j["jar"].name, fid))
        steps.done(f"上传 {j['jar'].name}", f"fileID={fid if fid is not None else '?'}  HTTP {code}")
        steps.done("relations（随上传 payload，同一次 POST）", describe_relations(j["_relations"]))

    old_info = {}
    if payload:
        if not ctx["api_key"]:
            steps.fail("更新项目信息", "缺少凭据：需要 --api-key 或 CURSEFORGE_API_KEY")
            return report_abort(steps, slug, uploaded, True, {})
        if ctx["key_fallback"]:
            print("[WARN] 未提供 --api-key / CURSEFORGE_API_KEY ⇒ 用旧版 token 充当 x-api-key（未验证）")
        ok, code, _body, old_info = run_update_info(ctx, payload, src)
        if not ok:
            steps.fail(f"PUT /v1/mods/{ctx['project_id']}", f"HTTP {code}")
            return report_abort(steps, slug, uploaded, True, old_info)
        steps.done(f"PUT /v1/mods/{ctx['project_id']}", f"HTTP {code}")

    steps.dump("完成 —— 执行摘要")
    print("-" * 72)
    print("全部步骤成功。")
    return 0


# ---------------------------------------------------------------- 旧式用法（兼容）
LEGACY_EPILOG = """\
子命令（推荐）：
  push          上传 jar → relations →（可选）更新项目信息
  update-info   只改项目元数据：summary / description / categoryId

不带子命令 = 旧式用法（兼容保留）：--jar 上传 / --update-file 补元数据。
⚠️ 默认 dry-run：旧式用法也必须显式 --apply 才会真正上传（2026-10-20 起）。

示例：
  python3 tools/curseforge_upload.py --jar <jar> --dry-run
  python3 tools/curseforge_upload.py --jar <jar> --apply
  python3 tools/curseforge_upload.py --update-file 9024345 --line forge_1.20.1 --apply
"""


def build_legacy_parser():
    ap = argparse.ArgumentParser(
        description="上传 Astral Dice 构建产物到 CurseForge（默认 dry-run；子命令见下）",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=LEGACY_EPILOG)
    ap.add_argument("--jar", action="append", metavar="PATH",
                    help="要上传的 jar（可重复；相对路径按当前目录解析）。--update-file 模式下可省略")
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
    ap.add_argument("--refresh-versions", action="store_true", help="忽略缓存重新拉取版本表")
    ap.add_argument("--allow-p1", action="store_true",
                    help="放行 P1 两线产物上传（neoforge-26.1.2 / fabric-1.20.1）；"
                         "仅在用户当次点名要求发布 P1 时使用，默认禁止")
    add_info_args(ap, with_switch=True)
    add_auth_args(ap)
    add_write_args(ap)
    return ap


def legacy_main(args, ap):
    if not args.jar and args.update_file is None:
        ap.error("--jar 至少给一个（或改用 --update-file <fileID>）")

    write, why = write_enabled(args)
    ctx = make_ctx(args, write)
    binding, project_id = ctx["binding"], ctx["project_id"]
    token, opener = ctx["token"], ctx["opener"]
    want_info = bool(args.update_info or args.summary is not None
                     or args.description_file or args.category_id is not None)
    payload, src = (build_info_payload(args) if want_info else (None, None))
    if args.json_out and payload:
        write_json_out(args.json_out, payload)

    # ---------- 模式 B：更新已发布文件的元数据（补 relations），不重传 ----------
    if args.update_file is not None:
        line = find_line(binding, key=args.line, loader=args.loader, mc=args.mc_version)
        if line is None:
            raise SystemExit("[ERR] --update-file 需要 --line <key>，"
                             "或用 --loader/--mc-version 唯一匹配一条线")
        rel = relations_payload(line, disabled=args.no_relations)
        metadata = {"fileID": args.update_file}
        if rel:
            metadata["relations"] = rel
        if args.display_name:
            metadata["displayName"] = args.display_name
        if len(metadata) == 1 and not payload:
            raise SystemExit(
                "[ERR] update-file 至少要有一个要改的字段：给 --display-name，或让该线声明 relations"
                "（CurseForge 会以 1014 'You must specify something to update' 拒绝空更新）")
        print(f"项目 projectId = {project_id}（slug: {binding.get('slug')}，来源 {BINDING_FILE}）")
        print(f"凭据           = token: {ctx['token_src']} ；api-key: {ctx['api_key_src']}")
        print(f"代理           = {ctx['proxy'] or '（直连；若被 Cloudflare 403 请加 --proxy）'}")
        print(f"写请求         = {'是（%s）' % why if write else '否（%s）' % why}")
        print("-" * 72)
        print(f"  模式 = 更新已发布文件（不重传）  fileID = {args.update_file}")
        print(f"  线 key = {line.get('key')}（loader={line.get('loader')} mc={line.get('mc')}）")
        print(f"  relations = {describe_relations(rel)}")
        if rel:
            print("  🚨 实测（2026-10-01）：CurseForge 的 update-file 处理 `relations` 时**服务端 500**"
                  "（同批 releaseType / gameVersions 也 500；只有 displayName 更新返回 200）"
                  " ⇒ 想给已发布文件补 relations **只能重传**（upload-file，会产生同名新文件，"
                  "旧件只能在 CF 后台人工删）。本模式仍保留：等 CF 修好后可直接用。")
        if payload:
            print("-" * 72)
            print("[步骤 2] 项目信息：")
            print_info_plan(ctx, payload, src)
        print("-" * 72)
        print(json.dumps(metadata, ensure_ascii=False, indent=2))
        if not write:
            print("-" * 72)
            print("DRY-RUN：未发送任何请求。确认无误后加 --apply 才会真发。")
            return 0
        steps = StepLog()
        code, body = api_post_metadata(
            opener, token, f"{API_BASE}/projects/{project_id}/update-file", metadata)
        ok = 200 <= code < 300
        print(f"[{'OK ' if ok else 'FAIL'}] fileID={args.update_file}  HTTP {code}  {body[:300]}")
        if not ok:
            steps.fail(f"update-file {args.update_file}", f"HTTP {code}")
            return report_abort(steps, binding.get("slug"), [], False, {})
        steps.done(f"update-file {args.update_file}", f"HTTP {code}")
        if payload:
            if not ctx["api_key"]:
                steps.fail("更新项目信息", "缺少凭据：需要 --api-key 或 CURSEFORGE_API_KEY")
                return report_abort(steps, binding.get("slug"), [], True, {})
            ok2, code2, _b2, old2 = run_update_info(ctx, payload, src)
            if not ok2:
                steps.fail(f"PUT /v1/mods/{project_id}", f"HTTP {code2}")
                return report_abort(steps, binding.get("slug"), [], True, old2)
            steps.done(f"PUT /v1/mods/{project_id}", f"HTTP {code2}")
        return 0

    cat, ver_src = (load_catalog(opener, token, force=args.refresh_versions) if write
                    else load_catalog_offline())
    jobs = build_jobs(args, binding, cat, offline=not write)

    slug = binding.get("slug")
    src_binding = BINDING_FILE if binding.get("projectId") else "脚本内置兜底值"
    print(f"项目 projectId = {project_id}" + (f"（slug: {slug}，来源 {src_binding}）" if binding else ""))
    print(f"凭据           = token: {ctx['token_src']} ；api-key: {ctx['api_key_src']}")
    print(f"代理           = {ctx['proxy'] or '（直连；若被 Cloudflare 403 请加 --proxy）'}")
    print(f"版本表         = {ver_src}")
    print(f"写请求         = {'是（%s）' % why if write else '否（%s）' % why}")
    print("-" * 72)
    print_jobs_plan(jobs)
    if payload:
        print("-" * 72)
        print("[附加步骤] 项目信息：")
        print_info_plan(ctx, payload, src)

    if not write:
        print("-" * 72)
        print("DRY-RUN：未发送任何请求（默认行为；真上传需 --apply）。")
        return 0

    steps = StepLog()
    uploaded = []
    print("-" * 72)
    print("开始执行：")
    for j in jobs:
        code, body = api_upload(opener, project_id, token, upload_metadata(args, j), j["jar"])
        ok = 200 <= code < 300
        if not ok:
            steps.fail(f"上传 {j['jar'].name}", f"HTTP {code}  {body[:200]}")
            return report_abort(steps, slug, uploaded, False, {})
        fid = extract_file_id(body)
        uploaded.append((j["jar"].name, fid))
        steps.done(f"上传 {j['jar'].name}", f"fileID={fid if fid is not None else '?'}  HTTP {code}")
        steps.done("relations（随上传 payload，同一次 POST）", describe_relations(j["_relations"]))

    old_info = {}
    if payload:
        if not ctx["api_key"]:
            steps.fail("更新项目信息", "缺少凭据：需要 --api-key 或 CURSEFORGE_API_KEY")
            return report_abort(steps, slug, uploaded, True, {})
        ok2, code2, _b2, old_info = run_update_info(ctx, payload, src)
        if not ok2:
            steps.fail(f"PUT /v1/mods/{project_id}", f"HTTP {code2}")
            return report_abort(steps, slug, uploaded, True, old_info)
        steps.done(f"PUT /v1/mods/{project_id}", f"HTTP {code2}")
    steps.dump("完成 —— 执行摘要")
    return 0


# ---------------------------------------------------------------- main
SUBCOMMANDS = ("push", "update-info")


def main(argv=None):
    argv = list(sys.argv[1:] if argv is None else argv)
    if argv and argv[0] in SUBCOMMANDS:
        sub = argv.pop(0)
        if sub == "push":
            return cmd_push(build_push_parser().parse_args(argv))
        return cmd_update_info(build_update_info_parser().parse_args(argv))
    ap = build_legacy_parser()
    return legacy_main(ap.parse_args(argv), ap)
if __name__ == "__main__":
    sys.exit(main())
