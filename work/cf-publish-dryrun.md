# CurseForge 上传链路：发布前 dry-run 体检（1.5.0）

- 仓库：`F:\MCProject\astral_dice_multiloader`（本机 shell 实为 MSYS bash；pwsh 7 位于 `C:\Program Files\PowerShell\7\pwsh`）
- 对象：`tools/curseforge_upload.py`（1446 行）+ `tools/curseforge.json`
- 本次**没有**：跑 Gradle、启动 Minecraft、占用 25565/25585/25575、删除或移动任何文件、修改任何配置、发任何 CurseForge 写请求、访问网络。
- dry-run 的离线性是脚本自述并已实测的：`PUSH_EPILOG` 写「dry-run 完全离线：只读 .curseforge/versions.json 缓存，不发任何请求」，实测输出头也印 `版本表 = 离线缓存 .curseforge\versions.json（4.6 天前；dry-run 不联网）`。
- token 只在 dry-run 里**被读取但不被校验**：输出 `凭据 = token: .curseforge\token`（36 B，mtime 2026-10-20 23:30）。有效与否本次无法判定（见 §9）。

---

## 0. 结论摘要

| # | 判定项 | 结论 |
|---|---|---|
| 1 | 四个 `astral_dice-1.5.0+<后缀>.jar` 的渠道 | **全部 release**（四线实测，无 -alpha/-beta/-rc/-pre 后缀） |
| 2 | 项目路由 | **全部 1662159（本体）**；库项目 1755415 只在「--jar 全是 starengine-* 库 jar」时才被选中，本次无库 jar |
| 3 | rc.7 会不会被自动一起传 | **不会**。脚本不扫描目录/不通配，只处理 `--jar` 逐个给出的路径；同批给两个 jar 才出两个 job；shell 通配会被 argparse 直接拒绝（exit 2） |
| 4 | 那"误收"风险在哪 | **在仓库另一处**：`.github/workflows/build.yml` 的 Release 步骤用 `<line>/build/libs/astral_dice-*.jar` 通配。CI 是全新检出所以安全；**本地复用同一通配会把 21 个 jar 全收进去**（其中 17 个是残留） |
| 5 | `curseforge.json` 的 changelog 引用 | **该文件根本没有 changelog 字段**；路径由 `pick_changelog()` 从版本号派生（`release/<base>/`），当前实测指向 `release\1.5.0\PLAYER_CHANGELOG.md`（54713 字符）——**已经是 1.5.0，不需要改** |
| 6 | `release/1.5.0/` 完整性 | **齐全**，三件都在、版本号一致、结构对等（详见 §6） |
| 7 | 必须清理的文件 | A 级 5 个（rc.* 共 5 个）；B 级 12 个（26.1.2 历史 beta）。清完每线正好剩 1 个 jar |
| 8 | 真正的阻塞项 | **P0 闸门**：`neoforge-26.1.2` 与 `fabric-1.20.1` 默认被 `[BLOCKED]`，需 `--allow-p1`；而脚本内该闸门的理由（"P1 从未获授权移植"）在 `.github/workflows/build.yml` 里已被 **2026-11-02 的四线发布裁决取代** —— 口径冲突，需裁决（§9.1） |

---

## 1. 写判定点与凭据（复核用）

- 唯一写判定点 `write_enabled()`（:841-847）：`--dry-run` → 不写；`--apply/--yes` → 写；**两者都不给 → 默认 dry-run**（"不发任何写请求"）。
- 凭据顺序 `resolve_token()`（:224）：`--token` > 环境变量 `CURSEFORGE_TOKEN` > `.curseforge/token`；dry-run 侧用 `resolve_token_safe()`（:831），找不到也不失败。
- 目录/通配：全文件 `grep -nE 'glob|iterdir|listdir|scandir|walk\(' ` 唯一命中是 :864 的 `global CURL_PROXY`（假阳性）⇒ **脚本从不枚举目录**。
- 上传 metadata（`upload_metadata()`，:989-1000）：
  - `displayName` 默认 = **jar 文件名**（`--display-name` 可覆盖）；
  - `releaseType` = `--release-type` 或 `infer_release_type(ver)`；
  - `changelogType` 默认 `markdown`，`changelog` = 文件**全文原样**（无截断、无转换）；
  - `relations` 随同一次 multipart 提交；`isMarkedForManualRelease` 仅在 `--manual-release` 时加。
- **无幂等保护**：`push` 全程没有任何"先查文件列表再决定"的调用（`/files` 唯一出现在 :909 的回滚提示文案里）⇒ 重复 `--apply` 会产生同名重复文件，而 Core API 没有删除接口（:907-910 自述）。

---

## 2. 四线产物盘点（`<line>/build/libs/`，bytes | mtime）

标记说明：**R = 发布物**；**S = 旧残留（预发布）**；**H = 历史残留（旧版本号）**。

### neoforge-1.21.1（3 个）
| 文件 | 字节 | mtime | 标记 |
|---|---|---|---|
| astral_dice-1.5.0+neoforge_1.21.1.jar | 2611856 | 2026-11-02 01:50:00 | **R** |
| astral_dice-1.5.0-rc.6+neoforge_1.21.1.jar | 2607086 | 2026-10-29 18:07:59 | S |
| astral_dice-1.5.0-rc.7+neoforge_1.21.1.jar | 2611872 | 2026-11-01 23:43:45 | S |

### forge-1.20.1（2 个）
| 文件 | 字节 | mtime | 标记 |
|---|---|---|---|
| astral_dice-1.5.0+forge_1.20.1.jar | 2660134 | 2026-11-02 01:50:06 | **R** |
| astral_dice-1.5.0-rc.7+forge_1.20.1.jar | 2660152 | 2026-11-01 23:43:54 | S |

### fabric-1.20.1（2 个）
| 文件 | 字节 | mtime | 标记 |
|---|---|---|---|
| astral_dice-1.5.0+fabric_1.20.1.jar | 3031074 | 2026-11-02 02:12:28 | **R** |
| astral_dice-1.5.0-rc.7+fabric_1.20.1.jar | 3027461 | 2026-11-01 23:16:45 | S |

### neoforge-26.1.2（14 个）
| 文件 | 字节 | mtime | 标记 |
|---|---|---|---|
| astral_dice-1.2.1-beta+neoforge_26.1.2.jar | 967109 | 2026-09-17 13:41:34 | H |
| astral_dice-1.2.1-beta.2+neoforge_26.1.2.jar | 967218 | 2026-09-18 18:59:26 | H |
| astral_dice-1.3.0-beta.1+neoforge_26.1.2.jar | 1238398 | 2026-09-24 15:43:47 | H |
| astral_dice-1.3.1-beta.1+neoforge_26.1.2.jar | 1439335 | 2026-09-25 19:05:52 | H |
| astral_dice-1.3.2-beta.1+neoforge_26.1.2.jar | 1505092 | 2026-09-29 15:11:04 | H |
| astral_dice-1.3.3-beta.1+neoforge_26.1.2.jar | 1506256 | 2026-09-29 19:53:43 | H |
| astral_dice-1.3.4-beta.1+neoforge_26.1.2.jar | 1508301 | 2026-09-30 02:30:21 | H |
| astral_dice-1.3.5-beta.1+neoforge_26.1.2.jar | 1531207 | 2026-10-01 09:51:20 | H |
| astral_dice-1.3.5-beta.2+neoforge_26.1.2.jar | 1533289 | 2026-10-01 10:40:50 | H |
| astral_dice-1.3.6-beta.1+neoforge_26.1.2.jar | 1552315 | 2026-10-02 10:14:19 | H |
| astral_dice-1.3.7-beta.1+neoforge_26.1.2.jar | 1556596 | 2026-10-04 18:51:40 | H |
| astral_dice-1.3.7-beta.2+neoforge_26.1.2.jar | 1556673 | 2026-10-05 11:55:29 | H |
| astral_dice-1.5.0+neoforge_26.1.2.jar | 2875237 | 2026-11-02 02:01:09 | **R** |
| astral_dice-1.5.0-rc.7+neoforge_26.1.2.jar | 2875210 | 2026-11-01 23:31:44 | S |

**补充事实**：仓库根 `build/libs/` 里已只有四个 1.5.0 发布物，且与各线内同名文件**逐字节相同**（sha256 见 §7.3）——根目录是 `pushToRootBuild` 的"先删后拷"目标，各线 `build/libs` 没有这个清理。

---

## 3. 上传器对每个 jar 的解析结果（实跑 `--dry-run` 原始输出整理）

共同前导（每次 dry-run 都一致）：项目 `1662159`（slug: astral-dice，来源 `tools/curseforge.json`）、凭据 `token: .curseforge\token`、代理 `http://127.0.0.1:7897`、版本表 = 离线缓存、写请求 = 否。

| jar | 解析版本 | loader / MC | 渠道 | gameVersions | 更新日志 | relations |
|---|---|---|---|---|---|---|
| 1.5.0+neoforge_1.21.1 | 1.5.0 | neoforge / 1.21.1 | **release** | [9638, 9639, 11779, 10150] | release\1.5.0\PLAYER_CHANGELOG.md（54713 字符） | curios(309927)+starengine(1755415) |
| 1.5.0-rc.6+neoforge_1.21.1 | 1.5.0-rc.6 | neoforge / 1.21.1 | beta | 同上 | 同上（**注意：rc 也吃 1.5.0 那份正式版日志**） | 同上 |
| 1.5.0-rc.7+neoforge_1.21.1 | 1.5.0-rc.7 | neoforge / 1.21.1 | beta | 同上 | 同上 | 同上 |
| 1.5.0+forge_1.20.1 | 1.5.0 | forge / 1.20.1 | **release** | [9638, 9639, 9990, 7498] | release\1.5.0\PLAYER_CHANGELOG.md | curios+mixinbooster(980731)+starengine |
| 1.5.0-rc.7+forge_1.20.1 | 1.5.0-rc.7 | forge / 1.20.1 | beta | 同上 | 同上 | 同上 |
| 1.5.0+fabric_1.20.1 | 1.5.0 | fabric / 1.20.1 | **release** | [9638, 9639, 9990, 7499] | release\1.5.0\PLAYER_CHANGELOG.md（**生产线那份，见 §5.3**） | fabric-api(306612)+puzzles-lib(495476)+trinkets(341284) required / accessories(938917) optional |
| 1.5.0-rc.7+fabric_1.20.1 | 1.5.0-rc.7 | fabric / 1.20.1 | beta | 同上 | 同上 | 同上 |
| 1.5.0+neoforge_26.1.2 | 1.5.0 | neoforge / 26.1.2 | **release** | [9638, 9639, 16082, 10150] | release\1.5.0\PLAYER_CHANGELOG.md | curios |
| 1.5.0-rc.7+neoforge_26.1.2 | 1.5.0-rc.7 | neoforge / 26.1.2 | beta | 同上 | 同上 | curios |
| 1.2.1-beta+neoforge_26.1.2 | 1.2.1-beta | neoforge / 26.1.2 | beta | 同上 | release\1.2.1\PLAYER_CHANGELOG.md（42877） | curios |
| 1.2.1-beta.2+neoforge_26.1.2 | 1.2.1-beta.2 | neoforge / 26.1.2 | beta | 同上 | release\1.2.1（42877） | curios |
| 1.3.0-beta.1+neoforge_26.1.2 | 1.3.0-beta.1 | neoforge / 26.1.2 | beta | 同上 | release\1.3.0（381） | curios |
| 1.3.1-beta.1+neoforge_26.1.2 | 1.3.1-beta.1 | neoforge / 26.1.2 | beta | 同上 | release\1.3.1（381） | curios |
| 1.3.2-beta.1+neoforge_26.1.2 | 1.3.2-beta.1 | neoforge / 26.1.2 | beta | 同上 | release\1.3.2（20007） | curios |
| 1.3.3-beta.1+neoforge_26.1.2 | 1.3.3-beta.1 | neoforge / 26.1.2 | beta | 同上 | release\1.3.3（8482） | curios |
| 1.3.4-beta.1+neoforge_26.1.2 | 1.3.4-beta.1 | neoforge / 26.1.2 | beta | 同上 | release\1.3.4（3614） | curios |
| 1.3.5-beta.1+neoforge_26.1.2 | 1.3.5-beta.1 | neoforge / 26.1.2 | beta | 同上 | release\1.3.5（12641） | curios |
| 1.3.5-beta.2+neoforge_26.1.2 | 1.3.5-beta.2 | neoforge / 26.1.2 | beta | 同上 | release\1.3.5（12641） | curios |
| 1.3.6-beta.1+neoforge_26.1.2 | 1.3.6-beta.1 | neoforge / 26.1.2 | beta | 同上 | release\1.3.6（8062） | curios |
| 1.3.7-beta.1+neoforge_26.1.2 | 1.3.7-beta.1 | neoforge / 26.1.2 | beta | 同上 | release\1.3.7（17361） | curios |
| 1.3.7-beta.2+neoforge_26.1.2 | 1.3.7-beta.2 | neoforge / 26.1.2 | beta | 同上 | release\1.3.7（17361） | curios |

补充：`--line` 全都匹配到对应线 key（`neoforge_1.21.1` / `forge_1.20.1` / `neoforge_26.1.2` / `fabric_1.20.1`），没有 `[未匹配到该线]`。

---

## 4. 三项关键证明

### 4.1 `astral_dice-1.5.0+<后缀>.jar` 判为 release —— 代码路径 + 实测
- 代码：`parse_jar`（:530）用 `JAR_RE = ^astral_dice-(?P<ver>[^+]+)\+(?P<loader>[a-z]+)_(?P<mc>.+)\.jar$` 取 `ver`；对 `astral_dice-1.5.0+neoforge_1.21.1.jar` 得到 `ver = "1.5.0"`（`+` 是分隔符，不进版本）。
- 代码：`infer_release_type`（:547-553）`v = ver.lower()`，只有含 `-alpha` → alpha、含 `-beta`/`-rc`/`-pre` → beta，否则 release。`"1.5.0"` 三个子串全不命中 ⇒ **release**。
- 实测：四线四个 1.5.0 jar 全部打印 `类型 release`（§3 表第 1/4/6/8 行）。
- **推论（必须点明的差异）**：`tools/curseforge.json` 里 `lines[]` 写的 `neoforge_26.1.2 = beta`、`fabric_1.20.1 = alpha` 是**过时描述**，代码从不读取该字段（`find_line` 只用它匹配 key/relations）。所以 26.1.2 与 fabric 的 1.5.0 会以 **release** 上架，而不是配置里写的 beta/alpha。

### 4.2 同一个 `build/libs` 里有 rc.7，不会把 rc 版一起带上 —— 实测三条
1. **只给 1.5.0**：计划里只有 1 个 job（release）。同目录的 rc.6/rc.7 完全不出现。
2. **显式给两个**：`--jar 1.5.0 --jar rc.7` → 计划里**两个 job**（release + beta）⇒ 语义是"给谁传谁"，脚本既不补全兄弟文件，也不做去重/过滤。
3. **shell 通配**：`--jar neoforge-1.21.1/build/libs/astral_dice-*.jar` 未加引号时，bash 展开成多个词，argparse 只把第一个当 `--jar` 值，其余变位置参数 ⇒ `error: unrecognized arguments: ...rc.6....jar ...rc.7....jar`，**exit=2**（响亮失败，不会静默只传一半）。加引号则字面路径不存在 ⇒ `[ERR] 找不到 jar`。

结论：**在"给路径"这条链路上，残留 jar 不会被自动带上，误传只能由人显式给第二个 `--jar` 造成。**

### 4.3 但"误收"风险真实存在，只是不在上传器里
`.github/workflows/build.yml` 的 `Create/Update GitHub Release` 步骤：

```bash
for p in neoforge-1.21.1/build/libs/astral_dice-*.jar \
         forge-1.20.1/build/libs/astral_dice-*.jar \
         neoforge-26.1.2/build/libs/astral_dice-*.jar \
         fabric-1.20.1/build/libs/astral_dice-*.jar; do
  if [[ -f "$p" ]]; then JARS+=("$p"); fi
done
```

- CI 上是全新检出 ⇒ 每个 `build/libs` 只有刚构建的那一个 jar ⇒ 安全。
- **本地**复用同一段逻辑（或任何 `build/libs/*.jar` 通配）⇒ 会把 **21 个 jar 全部**收进来，其中 17 个是残留（含 12 个 1.2.x~1.3.x beta）。这就是清理的真实理由：**不是防上传器，是防一切 glob 消费者**。
- 历史佐证：`.workbuddy/memory/2026-09-22.md:236` 记过同一条教训——"附件按版本号收紧收集（`build/libs` 不清理历史产物，松散 glob 会传上历次版本的 jar）"。

---

## 5. `tools/curseforge.json` 核对（任务 4）

### 5.1 changelog 引用：**该配置项不存在**
全文没有 `changelog` 键。路径完全由脚本派生：
```
pick_changelog(ver, explicit)          # :630
  explicit -> resolve() 并要求存在
  else: base = ver.split("-")[0]       # "1.5.0-rc.7" -> "1.5.0"；"1.5.0" -> "1.5.0"
        release/<base>/PLAYER_CHANGELOG.md  ->  ..._ZH.md   # 英文优先
```
实测对应关系：`release\1.5.0\PLAYER_CHANGELOG.md`（54713 字符）。**已经指向 1.5.0，没有指向 rc.7 或其它路径 ⇒ 无需修改。**

- 注意 1：`rc.7` 的 `base` 也被切成 `1.5.0`，所以**残留 rc jar 若被传，会挂着正式版日志**（这正是残留必须清的理由之一）。
- 注意 2：英文优先 ⇒ 上传的是 `PLAYER_CHANGELOG.md`（55952 B / 55127 chars），不是中文那份；站点上会显示英文日志。

### 5.2 其它字段与实现不符（**只报告，未修改**）
| 位置 | 现状 | 实际 |
|---|---|---|
| `_lineNote` | "脚本目前只读 projectId 与 slug，加载器/MC/版本一律从文件名解析" | 现在还会读 `lines[].relations` 与 `lib.projectId`（`relations_payload` / `make_ctx`） |
| `_channelNote` | "26.1.2=-beta.x⇒beta；fabric=-alpha.x⇒alpha" | 1.5.0 已无后缀 ⇒ 两条都变 **release**；且该字段从未被读取 |
| `lines[].channel` | 26.1.2=beta、fabric=alpha | 同上，纯文档性字段 |

**最小改法（若决定修）**：只改 `_channelNote` 两句描述 + 两条 `channel` 值，不动 `relations`/`lib`。本次按"只报告不改"处理。

### 5.3 fabric 的 changelog：配置注释与现状冲突
- `_fabricNote` 要求：fabric 必须用 `--changelog` 显式指定**本线自备**的文件（举例 `release/fabric-1.3.7-alpha.2/PLAYER_CHANGELOG.md`）。
- 现状：`release/fabric-*` 只有 `1.3.5-alpha.1` / `1.3.6-alpha.1` / `1.3.7-alpha.2` 三个旧目录，**没有 `release/fabric-1.5.0/`**；而 `pick_changelog` 只按版本号找 `release/1.5.0/`，所以实测 fabric 走的是**生产线那份**。
- 两选项（需裁决）：
  - **A（推荐，改动最小）**：接受 fabric 复用 `release/1.5.0/PLAYER_CHANGELOG.md`（该文档本就写"四条平台线同时正式发布"，语义一致），把 `_fabricNote` 更新为"1.5.0 起复用生产线说明"。
  - **B**：新建 `release/fabric-1.5.0/PLAYER_CHANGELOG.md`，真传 fabric 那次加 `--changelog release/fabric-1.5.0/PLAYER_CHANGELOG.md`。

---

## 6. `release/1.5.0/` 完整性核对（任务 5）

| 文件 | 字节 | 字符 | 行尾 | BOM | 首行 |
|---|---|---|---|---|---|
| PLAYER_CHANGELOG_ZH.md | 53855 | 23772 | CRLF 407 / 裸 LF 0 | 无 | `# 星之骰戏 (Astral Dice) 1.5.0 玩家更新日志` |
| PLAYER_CHANGELOG.md | 55952 | 55127 | CRLF 414 / 裸 LF 0 | 无 | `# Astral Dice 1.5.0 — Player Changelog` |
| PLAYER_CHANGELOG_MCMOD.html | 56397 | 26302 | 纯 LF | 无 | `[h1=星之骰戏 (Astral Dice) 1.5.0 玩家更新日志]` |

- 三件 mtime 均 `2026-11-02 02:22:09`，**晚于**四个 jar 的构建时间（01:50:00 / 01:50:06 / 02:01:09 / 02:12:28）⇒ 文档为定版后撰写，顺序正确。
- 版本号一致：三份都写 1.5.0，无 1.4.9 / rc.6 残留；第四行正文都声明"四条平台线同时正式发布"，与四个 1.5.0 jar 对得上。
- 结构对等：EN 与 ZH 各 **10 个 `##` 小节**，MCMOD 有 **10 个 `[h2=`】**（一…十），顺序与标题一致：本版相对 rc.7 的改动 / 安装与前置 / 新增内容 / 战斗系统重做 / 平衡与数值调整 / BUG 修复 / 平台差异 / 已知问题 / 文案与手册 / 许可变更。
- **一处提示（不定性为缺陷）**：第一节标题是「本版（1.5.0 正式版）相对 1.5.0-rc.7 的改动」/「What changed in 1.5.0 since 1.5.0-rc.7」（两份 md 都在第 18 行，MCMOD 在 `[h2=一、…]`）。按 2026-10-26 的玩家文档裁决，该类"相对上一 rc"的章节本不该出现（判据 `grep -rniE '相对 rc|since 1[._]5[._]0-rc'` 应为 0 命中；本目录命中 2 处）。但该裁决原文针对**预发布**版，且 1.5.0 同时写了「覆盖自 1.3.7 以来的全部改动」，属"全量 + 增量"双段结构 ⇒ 本次只标记，交由裁决。

---

## 7. 正式上传前的清理清单

> 前提：**清理不改变 dry-run 结果**（脚本不扫目录）。清理的目的是让"任何 glob 消费者"（CI 复用、人工 `ls`、发布脚本）都不会碰到残留。以下命令**均未执行**。

### 7.1 A 级 —— 必须挪走（预发布残留，5 个）
被误收后后果最重：它们会被判 beta，且**挂正式版日志**。

| 路径 | 字节 |
|---|---|
| neoforge-1.21.1/build/libs/astral_dice-1.5.0-rc.6+neoforge_1.21.1.jar | 2607086 |
| neoforge-1.21.1/build/libs/astral_dice-1.5.0-rc.7+neoforge_1.21.1.jar | 2611872 |
| forge-1.20.1/build/libs/astral_dice-1.5.0-rc.7+forge_1.20.1.jar | 2660152 |
| fabric-1.20.1/build/libs/astral_dice-1.5.0-rc.7+fabric_1.20.1.jar | 3027461 |
| neoforge-26.1.2/build/libs/astral_dice-1.5.0-rc.7+neoforge_26.1.2.jar | 2875210 |

### 7.2 B 级 —— 建议归档（26.1.2 历史 beta，12 个）
| 路径（`neoforge-26.1.2/build/libs/`） | 字节 |
|---|---|
| astral_dice-1.2.1-beta+neoforge_26.1.2.jar | 967109 |
| astral_dice-1.2.1-beta.2+neoforge_26.1.2.jar | 967218 |
| astral_dice-1.3.0-beta.1+neoforge_26.1.2.jar | 1238398 |
| astral_dice-1.3.1-beta.1+neoforge_26.1.2.jar | 1439335 |
| astral_dice-1.3.2-beta.1+neoforge_26.1.2.jar | 1505092 |
| astral_dice-1.3.3-beta.1+neoforge_26.1.2.jar | 1506256 |
| astral_dice-1.3.4-beta.1+neoforge_26.1.2.jar | 1508301 |
| astral_dice-1.3.5-beta.1+neoforge_26.1.2.jar | 1531207 |
| astral_dice-1.3.5-beta.2+neoforge_26.1.2.jar | 1533289 |
| astral_dice-1.3.6-beta.1+neoforge_26.1.2.jar | 1552315 |
| astral_dice-1.3.7-beta.1+neoforge_26.1.2.jar | 1556596 |
| astral_dice-1.3.7-beta.2+neoforge_26.1.2.jar | 1556673 |

### 7.3 清理后状态（每线正好 1 个 jar，且与根 `build/libs/` 逐字节相同）
| 线 | jar | 字节 | sha256 |
|---|---|---|---|
| neoforge-1.21.1 | astral_dice-1.5.0+neoforge_1.21.1.jar | 2611856 | `24220d06a07a2cc6155b981682a080bdd191e1e79a89574ed4c5b4404903b113` |
| forge-1.20.1 | astral_dice-1.5.0+forge_1.20.1.jar | 2660134 | `fd6d8d8205e290701a18af9cc489dd7efab6dc82d9a310f3079058efa4b2e9c4` |
| neoforge-26.1.2 | astral_dice-1.5.0+neoforge_26.1.2.jar | 2875237 | `dd22295c7c4036ae0cb95a2cc5b861de30f5cbbf4e7a8859211830874dac53a1` |
| fabric-1.20.1 | astral_dice-1.5.0+fabric_1.20.1.jar | 3031074 | `662e828024d8f1e0245a30b1a73c7d606c062d9be8b65e47fd163d6311b26c8e` |

### 7.4 命令模板（**未执行**；用移动而非删除，保留回滚）
```bash
cd /f/MCProject/astral_dice_multiloader
mkdir -p .archive/1.5.0-prerelease
# A 级
mv "neoforge-1.21.1/build/libs/astral_dice-1.5.0-rc.6+neoforge_1.21.1.jar"  .archive/1.5.0-prerelease/
mv "neoforge-1.21.1/build/libs/astral_dice-1.5.0-rc.7+neoforge_1.21.1.jar"  .archive/1.5.0-prerelease/
mv "forge-1.20.1/build/libs/astral_dice-1.5.0-rc.7+forge_1.20.1.jar"        .archive/1.5.0-prerelease/
mv "fabric-1.20.1/build/libs/astral_dice-1.5.0-rc.7+fabric_1.20.1.jar"      .archive/1.5.0-prerelease/
mv "neoforge-26.1.2/build/libs/astral_dice-1.5.0-rc.7+neoforge_26.1.2.jar"  .archive/1.5.0-prerelease/
# B 级（只搬 26.1.2 的历史 beta；保留 1.5.0）
mv neoforge-26.1.2/build/libs/astral_dice-1.2.1-beta+neoforge_26.1.2.jar    .archive/1.5.0-prerelease/
mv neoforge-26.1.2/build/libs/astral_dice-1.2.1-beta.2+neoforge_26.1.2.jar  .archive/1.5.0-prerelease/
mv neoforge-26.1.2/build/libs/astral_dice-1.3.0-beta.1+neoforge_26.1.2.jar  .archive/1.5.0-prerelease/
mv neoforge-26.1.2/build/libs/astral_dice-1.3.1-beta.1+neoforge_26.1.2.jar  .archive/1.5.0-prerelease/
mv neoforge-26.1.2/build/libs/astral_dice-1.3.2-beta.1+neoforge_26.1.2.jar  .archive/1.5.0-prerelease/
mv neoforge-26.1.2/build/libs/astral_dice-1.3.3-beta.1+neoforge_26.1.2.jar  .archive/1.5.0-prerelease/
mv neoforge-26.1.2/build/libs/astral_dice-1.3.4-beta.1+neoforge_26.1.2.jar  .archive/1.5.0-prerelease/
mv neoforge-26.1.2/build/libs/astral_dice-1.3.5-beta.1+neoforge_26.1.2.jar  .archive/1.5.0-prerelease/
mv neoforge-26.1.2/build/libs/astral_dice-1.3.5-beta.2+neoforge_26.1.2.jar  .archive/1.5.0-prerelease/
mv neoforge-26.1.2/build/libs/astral_dice-1.3.6-beta.1+neoforge_26.1.2.jar  .archive/1.5.0-prerelease/
mv neoforge-26.1.2/build/libs/astral_dice-1.3.7-beta.1+neoforge_26.1.2.jar  .archive/1.5.0-prerelease/
mv neoforge-26.1.2/build/libs/astral_dice-1.3.7-beta.2+neoforge_26.1.2.jar  .archive/1.5.0-prerelease/
# 复核：每线应恰好 1 行
ls -1 neoforge-1.21.1/build/libs/*.jar forge-1.20.1/build/libs/*.jar \
      neoforge-26.1.2/build/libs/*.jar fabric-1.20.1/build/libs/*.jar
```
> 若要在 pwsh 7 里跑：`New-Item -ItemType Directory -Force .archive/1.5.0-prerelease` + `Move-Item -LiteralPath '<相对路径>'`（`+` 在 PowerShell 里不是特殊字符，但仍建议 `-LiteralPath`）。

---

## 8. 上传命令模板

### 8.1 dry-run（可随意重跑，零副作用）
```bash
cd /f/MCProject/astral_dice_multiloader

# P0 两线（无需 --allow-p1）
python tools/curseforge_upload.py push --dry-run \
  --jar "build/libs/astral_dice-1.5.0+neoforge_1.21.1.jar" \
  --jar "build/libs/astral_dice-1.5.0+forge_1.20.1.jar"

# P1 两线（必须 --allow-p1，否则 [BLOCKED] exit 1）
python tools/curseforge_upload.py push --dry-run --allow-p1 \
  --jar "build/libs/astral_dice-1.5.0+neoforge_26.1.2.jar" \
  --jar "build/libs/astral_dice-1.5.0+fabric_1.20.1.jar"
```
> 用根 `build/libs/` 而不是 `<line>/build/libs/`：根目录只有这四个文件且已证明与各线逐字节相同（§7.3），天然免疫残留。

### 8.2 真传（**仅模板，本次未执行**）
建议**一线一次调用**（不是一条命令塞四个 jar），理由：`--display-name` / `--changelog` 都是**全局参数**，多 jar 同批会被同一个值覆盖；且 P0/P1 闸门、失败隔离都要求拆开。

```bash
# ---- P0 ----
python tools/curseforge_upload.py push --apply \
  --jar "build/libs/astral_dice-1.5.0+neoforge_1.21.1.jar" \
  --changelog "release/1.5.0/PLAYER_CHANGELOG.md" \
  --display-name "Astral Dice 1.5.0 (NeoForge 1.21.1)"

python tools/curseforge_upload.py push --apply \
  --jar "build/libs/astral_dice-1.5.0+forge_1.20.1.jar" \
  --changelog "release/1.5.0/PLAYER_CHANGELOG.md" \
  --display-name "Astral Dice 1.5.0 (Forge 1.20.1)"

# ---- P1（逐次显式点名才用 --allow-p1）----
python tools/curseforge_upload.py push --apply --allow-p1 \
  --jar "build/libs/astral_dice-1.5.0+neoforge_26.1.2.jar" \
  --changelog "release/1.5.0/PLAYER_CHANGELOG.md" \
  --display-name "Astral Dice 1.5.0 (NeoForge 26.1.2)"

python tools/curseforge_upload.py push --apply --allow-p1 \
  --jar "build/libs/astral_dice-1.5.0+fabric_1.20.1.jar" \
  --changelog "release/1.5.0/PLAYER_CHANGELOG.md" \
  --display-name "Astral Dice 1.5.0 (Fabric 1.20.1)"
```

要点：
- `--changelog` 显式写死路径（默认派生虽然也是它，但显式写死后任何 `release/` 目录变动都不会改变上传内容）。
- `--display-name` 不写的话，站点显示名会是带 `+neoforge_1.21.1` 的**文件名**（`upload_metadata` :991）。
- 真传需要 token 有效；dry-run 只读不验，**不能**用 dry-run 绿来推定 token 可用。
- `--refresh-versions` 只在 `--apply` 时生效（版本表缓存 4.6 天，仍在 7 天 TTL 内）。
- **重复 `--apply` 会产生重复文件**，而 Core API 无删除接口（:907-910）⇒ 每线只跑一次，跑前先看一遍 dry-run 输出的 4 行 job。

---

## 9. 风险与待裁决（按优先级）

### 9.1 P0 闸门 vs 2026-11-02 四线发布裁决（**阻塞项**）
- 脚本侧：`P0_UPLOAD_LINES = {("neoforge","1.21.1"),("forge","1.20.1")}`，理由注释写「P1 两线从未获授权移植，其编译产物一律视为不可用」（2026-10-25 裁决），实测 26.1.2 / fabric 的 dry-run **也会**被 `[BLOCKED]`（exit 1）。
- workflow 侧：`.github/workflows/build.yml` 已按 **2026-11-02 用户裁决**改成四线同批发布（artifact 四线、Release 附件四线），并明确写「旧『只发 P0 两线』不再有效」。
- ⇒ 同一仓库里两条口径并存。本次**不改代码**，只提示：真传 26.1.2 / fabric 必须逐次 `--allow-p1`，且脚本里那段理由属于历史陈述。

### 9.2 真传无幂等
无预检、无删除接口（§1 末条）。同版本重跑 = 站点上出现同名重复文件，只能后台人工删。⇒ 每线一次，先 dry-run 复核。

### 9.3 changelog 体积
54713 字符的 markdown 会被**原样**塞进 `metadata.changelog`（脚本无截断/无转换）。CF 网页端对超长日志的呈现与是否有接口级上限，本仓未做过真传验证 —— 属**未验证项**，不宜假定可过。

### 9.4 前置库 starengine 未在本次链路里出现
`relations` 把 `starengine(1755415)` 标成 `requiredDependency`（P0 两线与 26.1.2），但本仓四线 `build/libs` 里**没有任何 `starengine-*.jar`**，`push` 也没有"传库"的步骤。⇒ 需另行确认 CF 上 1755415 已发布满足 1.5.0 依赖区间的构建，否则玩家装了会缺前置。本次未验证（不联网）。

### 9.5 fabric 的 changelog 归属
见 §5.3，需在 A/B 之间裁决。

### 9.6 token 有效性
`.curseforge/token` 36 B（mtime 2026-10-20）；dry-run 不校验。真传前建议单条最小写入验证，或直接看真传第一步回执。

---

## 10. 复现命令（本次实跑的全部动作）

```bash
cd /f/MCProject/astral_dice_multiloader
# 盘点
ls -l --time-style=long-iso neoforge-1.21.1/build/libs/ forge-1.20.1/build/libs/ \
                             neoforge-26.1.2/build/libs/ fabric-1.20.1/build/libs/
# 逐 jar 解析（四组，P1 组加 --allow-p1）
python tools/curseforge_upload.py push --dry-run [--allow-p1] --jar <path> [--jar <path> ...]
# 静态复核
grep -nE 'glob|iterdir|listdir|scandir|walk\(' tools/curseforge_upload.py
grep -n 'changelog\|displayName' tools/curseforge_upload.py
sha256sum build/libs/*.jar */build/libs/astral_dice-1.5.0+*.jar
```
