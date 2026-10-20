# CurseForge 发布工具（`tools/curseforge_upload.py`）

> 零依赖（Python 标准库）。本文只描述**脚本事实**；端点与字段的确认级别见文末「API 依据」。
> 最近修订：2026-10-20（新增 `push` / `update-info`，并改为**默认 dry-run**）。

## 0. 最短上手

```bash
# 1) 只规划，不发请求（离线可跑）
python3 tools/curseforge_upload.py push --dry-run \
    --jar neoforge-1.21.1/build/libs/astral_dice-<版本>+neoforge_1.21.1.jar

# 2) 只改项目信息，先看将来 PUT 的 JSON
python3 tools/curseforge_upload.py update-info --dry-run \
    --summary "≤500 字符的一句话" --description-file README.md

# 3) 真执行（必须显式 --apply / --yes）
python3 tools/curseforge_upload.py push --apply --jar <jar> --update-info \
    --summary "..." --description-file README.md --category-id 4483
```

**默认 dry-run**：不给 `--apply`（别名 `--yes`）时**不发任何写请求**。旧式无子命令用法
（`--jar` / `--update-file`）同样要求 `--apply`，否则只打印计划 + 守卫提示。

## 1. 子命令

| 命令 | 作用 | 写请求 |
|---|---|---|
| `push` | 上传 jar → relations →（可选）更新项目信息 | `POST /projects/{id}/upload-file`（+ 可选 `PUT /v1/mods/{id}`） |
| `update-info` | 只改项目元数据 | `PUT /v1/mods/{id}` |
| （无子命令） | 旧式 `--jar` 上传 / `--update-file <fileID>` 补元数据 | 同上，兼容保留 |

### `push`

1. `POST /projects/{id}/upload-file`：multipart，`metadata`（JSON 字符串）+ `file`（jar）。
2. relations 随**同一次**上传 payload 提交（不额外调 `update-file` —— 该路径对 relations 有
   服务端 500 的实测记录，见脚本 docstring）。
3. `PUT /v1/mods/{id}`：仅当给了 `--update-info`，或给了 `--summary` / `--description-file` /
   `--category-id` 中任意一个时执行。

任一上传失败 ⇒ **立刻中止**（不再继续后面的 jar），并打印「步骤回放 + 回滚/修复提示」，退出码 1。

保持原语义的开关：`--jar`（可重复）、`--changelog`、`--display-name`、`--release-type`
（以及 `--mc-version` / `--loader` / `--manual-release` / `--line` / `--no-relations` /
`--refresh-versions`）。

### `update-info`

| 参数 | 说明 |
|---|---|
| `--summary TEXT` | 项目摘要；**≤500 字符**（按 code point 计，emoji 算 1），超长直接报错并给出需删字数 |
| `--description-file PATH` | 描述正文来源文件（UTF-8，容 BOM）；**原样发送**，不做 Markdown→HTML |
| `--category-id ID` | 单个分类 id（正整数；Minecraft 例如 `4483` = Adventure and RPG） |
| `--json-out PATH` | 把将要发送的 JSON 另存（便于复核；不含任何凭据） |

未给 `--summary` 时，默认取 `README.md` 的首个摘要行（跳过标题 / 语言切换 / 徽章 / 引用 /
分隔线，并剥离 `**`、反引号与 Markdown 链接语法）；取不到则报错要求显式给 `--summary`。

请求体只含三个字段的**交集**（缺省字段不发送）：

```json
{ "summary": "...", "description": "...", "categoryId": 4483 }
```

`classId` / `gameCategoryIds` **不暴露** —— 离线环境无法确认 Core API 的 ModUpdate 模型是否接受。

## 2. 凭据

上传（旧版 API）用 `X-Api-Token`，解析顺序：`--token` → `CURSEFORGE_TOKEN` → `.curseforge/token`
（`.gitignore` 已排除）。

`PUT /v1/mods/{id}`（Core API v1）用 `x-api-key`，解析顺序：`--api-key` →
`CURSEFORGE_API_KEY` → **回退**用上面解析到的旧 token（计划里会打印醒目告警）。
脚本两个头都会带：`X-Api-Token` 照旧发（服务端忽略），`x-api-key` 决定 Core API 是否放行。

**token 绝不写进任何入库文件**；`--json-out` 落盘的 payload 里没有凭据。

## 3. dry-run 的离线保证

`push` / `update-info` 的 dry-run 路径**不调用** `opener.open(...)`：

* 版本表只读 `.curseforge/versions.json` 缓存（不因过期重拉）；无缓存时 `gameVersions` 打印为空并告警。
* 找不到 token 也不中止（打印「dry-run：未找到 token」），因此无 token 环境可跑。
* 项目信息的当前值不去 GET（dry-run 不联网），回滚旧值只在 `--apply` 时读取。

## 4. API 依据

| 端点 | 依据 | 确认级别 |
|---|---|---|
| `POST https://minecraft.curseforge.com/api/projects/{id}/upload-file` | 脚本内既有实现 + 2026-09-30 实测（成功回 `{"id": <fileId>}`，multipart `metadata`+`file`） | **已实测（脚本历史）** |
| `POST .../projects/{id}/update-file` | 脚本内既有实现；`relations`/`releaseType`/`gameVersions` 实测 500，仅 `displayName` 200 | **已实测（脚本历史）** |
| `PUT https://api.curseforge.com/v1/mods/{modId}` | 官方 Core API v1 的 ModUpdate 模型；本脚本只发 `summary` / `description` / `categoryId` | **未实测**（沙箱无网络/凭据） |
| `GET https://api.curseforge.com/v1/mods/{modId}` | 官方 Core API v1；仅用于 `--apply` 时读旧值做回滚提示 | **未实测** |

`summary` 的 500 字符上限按 CurseForge 项目摘要字段限制实现；`description` 的 100000 字符是
**本地保守闸门**（`DESCRIPTION_MAX_CHARS`），不是官方公布值。

## 5. 已知风险 / 未验证项

1. **Core API 需要项目作者的 API Key**：`x-api-key` 不是上传用的项目 token；回退路径很可能 403。
2. **PUT 可能是「整体替换」而非「部分更新」**：若模型要求全字段，只发三字段可能清空其它字段。
   真实执行前脚本会 `GET` 并打印旧 `summary` / `description` 长度 / `categoryId` 作为回滚依据。
3. **description 的格式**：CF 站点按 HTML 渲染，`README.md` 是 Markdown ⇒ 语义会跑偏；脚本只在
   内容里没有 `<` 时告警，不做转换。
4. **上传体积限制**未在脚本内校验（由 CurseForge 侧拒绝）；`--timeout 600s` 内完成。
5. **没有删除文件的 API**：上传成功但后续步骤失败时，只能到站点后台人工删件（回滚提示里给链接）。

## 6. 自检（只读，不发请求）

```bash
python3 tools/curseforge_upload.py --help
python3 tools/curseforge_upload.py update-info --dry-run --summary "..." --description-file README.md
python3 tools/curseforge_upload.py push --dry-run --jar <某 jar 路径>
python3 -c "import ast;ast.parse(open('tools/curseforge_upload.py').read())"
```
