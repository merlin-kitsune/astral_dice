# CurseForge 确定性 400 修复 + fabric hotfix 上传报告

- 仓库：`D:/MCProject/astral_dice_multiloader`，分支 `multi-main`，HEAD `9adfd9f3`
- 时间：2026-11-02（本地 Asia/Hong_Kong）
- 本轮**未提交任何东西**；他人未提交改动（`docs/agent/AGENTS-dsh-linux.md`、`docs/agent/AGENTS-full.md`、`docs/archive/card-recipes-2026-10-09/README.md`）未触碰。
- 唯一被修改的受版本管理文件：`tools/curseforge_upload.py`（`git status --porcelain tools/` 只有这一行 ` M`）

## 1. 改动清单（文件:行号）

| # | 位置（改后行号） | 性质 | 内容 |
|---|---|---|---|
| 1 | `tools/curseforge_upload.py:120-124` | 模块 docstring 订正 | 把「`changelog` 与 `changelogType` 两个字段都不出现」改成「不写内容 ≠ 不发字段：字段必填、值为空串」 |
| 2 | `tools/curseforge_upload.py:734-736` | `decide_changelog` docstring 订正 | 同上口径（该 docstring 第 4 条也写着同一句旧描述） |
| 3 | `tools/curseforge_upload.py:1119-1130` | **行为修复**（`upload_metadata`） | 不附 changelog 的分支从「两个键都不发」改为「照发 `changelogType` + `changelog: ""`」 |
| 4 | `tools/curseforge_upload.py:1120` | 注释更新 | 真附带分支的旧注释（「两个字段都不发」）改为「真附带：changelogType + 文件内容（字节不变）」 |

语义边界（与本轮要求一致）：
- 附 changelog 的分支**字节不变**（`changelogType` 后跟 `_read_text(job["_changelog"])`，两行原样）。
- 不附的分支**只发空串**，不塞任何生成文字（版本号 / 日期 / 「no changelog」占位符一律没有）。
- `changelogType` 走脚本既有默认 `--changelog-type`（默认值 `markdown`，见 `tools/curseforge_upload.py:1298` 与 `:1423` 的两个 parser）。
- 行尾：`git ls-files --eol tools/curseforge_upload.py` 改前/改后均为 `i/lf w/lf attr/text eol=lf`；Python 读写走 UTF-8 字节写回，无 CR 混入（`crlf=0, bare-cr=0`）。

## 2. 前后 diff（`git diff tools/curseforge_upload.py` 原文）

```diff
diff --git a/tools/curseforge_upload.py b/tools/curseforge_upload.py
index aecef8df..d97ce7c8 100644
--- a/tools/curseforge_upload.py
+++ b/tools/curseforge_upload.py
@@ -117,8 +117,11 @@ release 类型由版本号里的 ``-alpha`` / ``-beta`` / ``-rc`` 后缀决定
 ``-`` 后缀（即不是补丁 / hotfix / 预发布）时，自动取
 ``release/<基础版本>/PLAYER_CHANGELOG.md``（英文，面向 CurseForge 国际社区），
 不存在时退回 ``PLAYER_CHANGELOG_ZH.md``。**小版本更新（``1.5.1`` / ``1.5.0-hotfix`` /
-``-rc`` / ``-beta`` / ``-alpha`` 等）默认不在 CF 写 changelog** —— metadata 里
-``changelog`` 与 ``changelogType`` 两个字段都不出现。覆盖方式：``--changelog <文件>``
+``-rc`` / ``-beta`` / ``-alpha`` 等）默认不在 CF 写 changelog** —— 这里的「不写」指
+**不写内容**，不是**不发字段**：``changelog`` 是 CurseForge v1 上传接口的**必填**字段
+（漏发会确定性 400 ``Required properties are missing from object: changelog``），
+所以字段照发、值为空串（**不得**塞版本号 / 日期 / 占位文字），``changelogType``
+按脚本既有默认照发。覆盖方式：``--changelog <文件>``
 手动指定（任何版本都附）；``--no-changelog`` 强制不附（与 ``--changelog`` 互斥）。
 
 dry-run 会把这两条判定各打一行，便于核对：
@@ -728,8 +731,9 @@ def decide_changelog(ver, explicit, disabled=False):
       2. ``--changelog <文件>`` ⇒ 用户手动指定，任何版本都附（文件不存在仍报错）；
       3. 主线版本（见 is_mainline_version）⇒ 自动取
          ``release/<基础版本>/PLAYER_CHANGELOG*.md``；
-      4. 其它（小版本：补丁 / hotfix / 预发布）⇒ **不附** —— metadata 里
-         ``changelog`` 与 ``changelogType`` 两个字段都不出现。
+      4. 其它（小版本：补丁 / hotfix / 预发布）⇒ **不附内容** —— metadata 里
+         ``changelog`` 字段仍必发（CF v1 必填，漏发 400），值为空串 ``""``；
+         ``changelogType`` 按既有默认照发。
     """
     if disabled and explicit:
         raise SystemExit("[ERR] --no-changelog 与 --changelog 冲突：二者只能选一个")
@@ -1113,10 +1117,17 @@ def upload_metadata(args, job):
         "gameVersions": job["gameVersions"],
     }
     if job["_changelog"]:
-        # changelogType 只在**真附带** changelog 时出现：小版本不写 CF changelog
-        # ⇒ changelog 与 changelogType 两个字段都不发（2026-11-02 用户规则）。
+        # 真附带：changelogType + 文件内容（字节不变）。
         metadata["changelogType"] = args.changelog_type
         metadata["changelog"] = _read_text(job["_changelog"])
+    else:
+        # 小版本不写 CF changelog（2026-11-02 用户规则）= **不写内容**，不是**不发字段**：
+        # CurseForge v1 上传接口把 metadata.changelog 当必填字段，漏发会确定性 400
+        # （errorCode 1002 "Required properties are missing from object: changelog"，
+        #  2026-11-02 实测）⇒ 字段照发，内容必须是空串：不得塞版本号 / 日期 /
+        # "no changelog" 之类占位文字。
+        metadata["changelogType"] = args.changelog_type
+        metadata["changelog"] = ""
     if job.get("_relations"):
         metadata["relations"] = job["_relations"]
     if args.manual_release:
```

diffstat：` tools/curseforge_upload.py | 23 +++++++++++++++++------ ` / ` 1 file changed, 17 insertions(+), 6 deletions(-) `（numstat 17/6）。
`python -m py_compile tools/curseforge_upload.py` ⇒ `py_compile OK`。

## 3. 验证（逐条原始输出）

### 验证 1：dry-run 仍打印 skip + metadata 确实带空串

**命令 1（改前基线，先取对照）**

```
$ python tools/curseforge_upload.py --jar 'fabric-1.20.1/build/libs/astral_dice-1.5.0-hotfix+fabric_1.20.1.jar' --proxy http://127.0.0.1:7897 --allow-p1 --dry-run
[WARN] P1 产物上传已放行（--allow-p1）：astral_dice-1.5.0-hotfix+fabric_1.20.1.jar | fabric | MC 1.20.1 —— 仅限用户逐次点名下达的任务
项目 projectId = 1662159（slug: astral-dice，来源 tools/curseforge.json）
凭据           = token: .curseforge\token ；api-key: 回退：用旧版 X-Api-Token 充当 x-api-key（未验证可过）
代理           = http://127.0.0.1:7897
版本表         = 离线缓存 .curseforge\versions.json（5.3 天前；dry-run 不联网）
写请求         = 否（显式 --dry-run）
------------------------------------------------------------------------
  astral_dice-1.5.0-hotfix+fabric_1.20.1.jar
    版本 1.5.0-hotfix | fabric | MC 1.20.1 | 类型 release
    DISPLAY: Astral Dice 1.5.0-hotfix (Fabric 1.20.1)
    CHANGELOG: skip (小版本更新不写 CF changelog: ver=1.5.0-hotfix, 用户 2026-11-02 规则)
    gameVersions = [9638, 9639, 9990, 7499]  {'Client': 9638, 'Server': 9639, '1.20.1': 9990, 'fabric': 7499}
    relations = fabric-api(306612)=requiredDependency, puzzles-lib(495476)=requiredDependency, trinkets(341284)=requiredDependency, accessories(938917)=optionalDependency, starengine(1755415)=requiredDependency  [fabric_1.20.1]
------------------------------------------------------------------------
DRY-RUN：未发送任何请求（默认行为；真上传需 --apply）。
[exit=0]
```

**命令 2（改后，同一命令）**

```
$ python tools/curseforge_upload.py --jar 'fabric-1.20.1/build/libs/astral_dice-1.5.0-hotfix+fabric_1.20.1.jar' --proxy http://127.0.0.1:7897 --allow-p1 --dry-run
[WARN] P1 产物上传已放行（--allow-p1）：astral_dice-1.5.0-hotfix+fabric_1.20.1.jar | fabric | MC 1.20.1 —— 仅限用户逐次点名下达的任务
项目 projectId = 1662159（slug: astral-dice，来源 tools/curseforge.json）
凭据           = token: .curseforge\token ；api-key: 回退：用旧版 X-Api-Token 充当 x-api-key（未验证可过）
代理           = http://127.0.0.1:7897
版本表         = 离线缓存 .curseforge\versions.json（5.3 天前；dry-run 不联网）
写请求         = 否（显式 --dry-run）
------------------------------------------------------------------------
  astral_dice-1.5.0-hotfix+fabric_1.20.1.jar
    版本 1.5.0-hotfix | fabric | MC 1.20.1 | 类型 release
    DISPLAY: Astral Dice 1.5.0-hotfix (Fabric 1.20.1)
    CHANGELOG: skip (小版本更新不写 CF changelog: ver=1.5.0-hotfix, 用户 2026-11-02 规则)
    gameVersions = [9638, 9639, 9990, 7499]  {'Client': 9638, 'Server': 9639, '1.20.1': 9990, 'fabric': 7499}
    relations = fabric-api(306612)=requiredDependency, puzzles-lib(495476)=requiredDependency, trinkets(341284)=requiredDependency, accessories(938917)=optionalDependency, starengine(1755415)=requiredDependency  [fabric_1.20.1]
------------------------------------------------------------------------
DRY-RUN：未发送任何请求（默认行为；真上传需 --apply）。
[exit=0]
```

`CHANGELOG: skip (…)` 行原样保留（dry-run 打印格式未改，diff 里也没有 `print_jobs_plan` 的行）。

**关于「dry-run 里能看到 metadata 将发送空串」**：`print_jobs_plan`（`tools/curseforge_upload.py:1096`）**不打印 metadata payload**，所以按代码层取证——直接用同一份模块代码调用 `upload_metadata`，把将要 `json.dumps` 进 multipart 的字典原样打印（`api_upload` 在 `tools/curseforge_upload.py:452` 起，第 454 行的 `meta = json.dumps(metadata, ensure_ascii=False).encode("utf-8")` 就是最终进 multipart 的字节，中间无任何过滤）：

```
$ python work/.tmp/dump_meta.py
[A] 小版本分支 -> 实际进 multipart 的 metadata（json.dumps 前后一致）:
{"displayName": "Astral Dice 1.5.0-hotfix (Fabric 1.20.1)", "releaseType": "release", "gameVersions": [9638, 9639, 9990, 7499], "changelogType": "markdown", "changelog": "", "relations": {"projects": [{"slug": "fabric-api", "projectID": 306612, "type": "requiredDependency"}]}}
    changelog repr = '' | changelogType = 'markdown'
[B] 主线分支 -> changelogType = 'markdown' | len(changelog) = 56234
[exit=0]
```

要点：`"changelog": ""` **在**（`repr == ''`，长度为 0，不含任何占位文字），`changelogType` 为 `markdown`（脚本既有默认）；主线分支仍读出 56234 字符正文。
（取证脚本 `work/.tmp/dump_meta.py` 为临时文件，未跟踪；原文件备份在 `work/.tmp/curseforge_upload.py.orig`。）

### 验证 2：`--apply` 真传

```
$ python tools/curseforge_upload.py --jar 'fabric-1.20.1/build/libs/astral_dice-1.5.0-hotfix+fabric_1.20.1.jar' --proxy http://127.0.0.1:7897 --allow-p1 --apply
[WARN] P1 产物上传已放行（--allow-p1）：astral_dice-1.5.0-hotfix+fabric_1.20.1.jar | fabric | MC 1.20.1 —— 仅限用户逐次点名下达的任务
项目 projectId = 1662159（slug: astral-dice，来源 tools/curseforge.json）
凭据           = token: .curseforge\token ；api-key: 回退：用旧版 X-Api-Token 充当 x-api-key（未验证可过）
代理           = http://127.0.0.1:7897
版本表         = 缓存 .curseforge\versions.json（126.2 小时前）
写请求         = 是（显式 --apply/--yes）
------------------------------------------------------------------------
  astral_dice-1.5.0-hotfix+fabric_1.20.1.jar
    版本 1.5.0-hotfix | fabric | MC 1.20.1 | 类型 release
    DISPLAY: Astral Dice 1.5.0-hotfix (Fabric 1.20.1)
    CHANGELOG: skip (小版本更新不写 CF changelog: ver=1.5.0-hotfix, 用户 2026-11-02 规则)
    gameVersions = [9638, 9639, 9990, 7499]  {'Client': 9638, 'Server': 9639, '1.20.1': 9990, 'fabric': 7499}
    relations = fabric-api(306612)=requiredDependency, puzzles-lib(495476)=requiredDependency, trinkets(341284)=requiredDependency, accessories(938917)=optionalDependency, starengine(1755415)=requiredDependency  [fabric_1.20.1]
------------------------------------------------------------------------
开始执行：
  [1] [OK  ] 上传 astral_dice-1.5.0-hotfix+fabric_1.20.1.jar  fileID=9406233  HTTP 200
  [2] [OK  ] relations（随上传 payload，同一次 POST）  fabric-api(306612)=requiredDependency, puzzles-lib(495476)=requiredDependency, trinkets(341284)=requiredDependency, accessories(938917)=optionalDependency, starengine(1755415)=requiredDependency
------------------------------------------------------------------------
完成 —— 执行摘要：
  [1] [OK  ] 上传 astral_dice-1.5.0-hotfix+fabric_1.20.1.jar  fileID=9406233  HTTP 200
  [2] [OK  ] relations（随上传 payload，同一次 POST）  fabric-api(306612)=requiredDependency, puzzles-lib(495476)=requiredDependency, trinkets(341284)=requiredDependency, accessories(938917)=optionalDependency, starengine(1755415)=requiredDependency
[exit=0]
```

**上传结果（记录）**

| 项 | 值 |
|---|---|
| HTTP | **200**（上一轮同一命令＝400 / errorCode 1002） |
| **fileID** | **9406233** |
| 帧内 displayName | `Astral Dice 1.5.0-hotfix (Fabric 1.20.1)` |
| type（releaseType） | `release` |
| gameVersions | `[9638, 9639, 9990, 7499]` = Client / Server / 1.20.1 / fabric |
| relations | 必需 4：fabric-api(306612)、puzzles-lib(495476)、trinkets(341284)、starengine(1755415)；可选 1：accessories(938917) |
| changelog（帧内） | `""`（空串，无占位文字） |
| changelogType（帧内） | `markdown` |

> 说明：任务书里写的「三必需两可选 + starengine」与实跑不一致——实跑是 **4 必需（含 starengine）+ 1 可选**，与 `tools/curseforge.json` 的 `fabric_1.20.1` 线配置及上一轮 dry-run 完全一致，本轮未改 relations。

### 验证 3：独立复核（尽力尝试，均不可用）

| 通道 | 命令要点 | 结果 |
|---|---|---|
| cfwidget | `GET https://api.cfwidget.com/1662159?limit=100`（经代理，两次） | `http=200 bytes=39068`，最新条目仍是 **9402802**（`Astral Dice 1.5.0 (Fabric 1.20.1)`，2026-11-02T05:11:05Z），**9406233 未出现** ⇒ 缓存滞后 |
| CF 站点 files API | `GET https://www.curseforge.com/api/v1/mods/1662159/files?...` | `http=403`（Cloudflare `Just a moment...`） |
| CF 旧版 `/api/projects/{id}/files` | 带 `X-Api-Token` + 浏览器 UA | `http=403`（同一 Cloudflare 挑战页） |
| CF Core API v1 | `GET https://api.curseforge.com/v1/mods/1662159/files`，用旧 token 冒充 `x-api-key` | `http=403 bytes=0`（无有效 Core API key） |
| Forge CDN | `edge.forgecdn.net/files/9406/233/astral_dice-1.5.0-hotfix+fabric_1.20.1.jar` | `302` → `mediafilez` `403`；**对照组**（不存在的 id 9999999）同样 `302` ⇒ CDN 302/403 均无判据价值（与任务书预判一致） |

小结：**无法独立复核，仅信任上传响应**。注意证据粒度：脚本只打印了 `HTTP 200` 与从响应体解析出的 `fileID=9406233`（`extract_file_id`，见 `tools/curseforge_upload.py:1138`），**原始响应体未打印**；要拿到站点侧快照，需等 cfwidget 缓存刷新后重查，或补一个带有效 Core API key 的 `GET /v1/mods/1662159/files`。

### 验证 4：主线 changelog 路径回归（只 dry-run，未真传）

```
$ python tools/curseforge_upload.py --jar 'neoforge-1.21.1/build/libs/astral_dice-1.5.0+neoforge_1.21.1.jar' --proxy http://127.0.0.1:7897 --allow-p1 --dry-run
项目 projectId = 1662159（slug: astral-dice，来源 tools/curseforge.json）
凭据           = token: .curseforge\token ；api-key: 回退：用旧版 X-Api-Token 充当 x-api-key（未验证可过）
代理           = http://127.0.0.1:7897
版本表         = 离线缓存 .curseforge\versions.json（5.3 天前；dry-run 不联网）
写请求         = 否（显式 --dry-run）
------------------------------------------------------------------------
  astral_dice-1.5.0+neoforge_1.21.1.jar
    版本 1.5.0 | neoforge | MC 1.21.1 | 类型 release
    DISPLAY: Astral Dice 1.5.0 (NeoForge 1.21.1)
    CHANGELOG: attach release/1.5.0/PLAYER_CHANGELOG.md (主线版本)
    更新日志 = release/1.5.0/PLAYER_CHANGELOG.md（56234 字符）
    gameVersions = [9638, 9639, 11779, 10150]  {'Client': 9638, 'Server': 9639, '1.21.1': 11779, 'neoforge': 10150}
    relations = curios(309927)=requiredDependency, starengine(1755415)=requiredDependency  [neoforge_1.21.1]
------------------------------------------------------------------------
DRY-RUN：未发送任何请求（默认行为；真上传需 --apply）。
[exit=0]
```

⇒ 主线分支未受影响（`CHANGELOG: attach release/1.5.0/PLAYER_CHANGELOG.md (主线版本)` 原样）。

### 验证 5：`git diff --stat` 与 `git status --porcelain tools/`

```
$ git diff --stat tools/curseforge_upload.py
 tools/curseforge_upload.py | 23 +++++++++++++++++------
 1 file changed, 17 insertions(+), 6 deletions(-)

$ git status --porcelain tools/
 M tools/curseforge_upload.py
```

（`tools/__pycache__/` 由 `py_compile` 产生但被 `.gitignore:49 tools/**/__pycache__/` 忽略，不影响工作树状态；该目录里另有一个 10-20 的既有 pyc，未删除。）

## 4. 未验证项

1. **站点侧可见性**：cfwidget / 站点 API / CDN 全部取不到 9406233 的可读证据（前三条 Cloudflare 403，cfwidget 缓存滞后，CDN 302 是无效判据）。⇒ 「文件已挂在项目 1662159 下」目前只有 CF 上传响应一个证据源。
2. **changelog 字段落库后的实际呈现**：CF 侧把空串 changelog 渲染成什么（空白区块 / 隐藏区块）未取得页面证据；本轮只验证了「帧内字段＝空串」与「HTTP 200」。
3. **关系是否真正生效**：relations 随同一次 POST 提交并被 CF 接收（200），但没有 `GET /v1/mods/1662159/files/9406233` 的读回（无 Core API key），未核对站点上的依赖展示。
4. **重复上传风险未验证**：脚本不做过期去重，若再次 `--apply` 同一 jar 会再生成一个文件；本轮只执行了一次 `--apply`（上一轮是 400 被拒，未产生文件）。
5. 文档侧同一句旧口径只改了脚本内的 3 处；仓内其它文档（如 `work/cf-displayname-changelog-policy.md`）若复述「两个字段都不发送」未在本轮一并订正（未 grep 澄清、未改动他人文档）。

## 5. 其它说明

- 根因复述：CurseForge v1 上传接口把 `metadata.changelog` 视作**必填**；旧代码「不写 changelog」实现成了「不发字段」⇒ 确定性 400（`errorCode 1002, Required properties are missing from object: changelog`）。本轮把「不写」还原成**空内容**，没有放宽用户 2026-11-02 的规则（仍然不写任何 changelog 文字）。
- 若 CF 后续用别的 errorCode 拒空串，按任务要求**不做任何绕过**（不填文字内容、不换通道），直接停下并把错误原文写回本报告——本轮未触发该情形（HTTP 200）。

结论：**CF 已上架** —— fabric 线 hotfix 已以 `fileID=9406233`、HTTP 200 上传成功（type=release，gameVersions [9638, 9639, 9990, 7499]，relations 4 必需 + 1 可选随同一 POST 提交）；站点侧独立复核因 Cloudflare 403 与 cfwidget 缓存滞后未能取得，属未验证项而非失败。
