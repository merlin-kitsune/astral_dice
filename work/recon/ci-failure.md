# Astral Dice CI 失败调查报告（只读）

| 项 | 值 |
|---|---|
| 仓库 | `F:/MCProject/astral_dice_multiloader`（远端 `https://github.com/merlin-kitsune/astral_dice`） |
| 唯一工作流 | `.github/workflows/build.yml`（437 行，1 job `build`，21 step） |
| 调查方式 | 只读取证；未改任何文件、未 commit/push、未建或移 tag、未触碰 Release / CurseForge、未启动游戏、未跑 Gradle |
| 取证时间 | 2026-11-02 18:57–19:10（Asia/Hong_Kong） |
| 出口 | 本地代理 `http://127.0.0.1:7897` |

## 0. 结论速览

| # | 结论 | 分级 |
|---|---|---|
| 1 | 当前 `multi-main` 上最近 3 次 run（#144 / #146 / #147）红，红线**全在同一个 step**：`Create/Update GitHub Release (four JARs, notes from release/<tag>/)` | 证实（CI annotation 原文） |
| 2 | #146 / #147 的根因：Release 步骤用**一条线**的版本号推导 glob，而 fabric 线被单独 bump 成 `1.5.0-hotfix` ⇒ 四线里 fabric 那条收集不到 jar ⇒ `exit 1` | 证实（CI annotation + 本地逐字复现） |
| 3 | #144 与 #145 同一 commit、均带 `[republish-assets]`，#145（tag run）绿而 #144（分支 run）红 ⇒ 与版本树无关，指向**同一 Release 上的并发写**（无 `concurrency:` 组） | step 级证实；具体失败命令**未核验**（日志不可得） |

---

## 1. 取证条件与方法（含限制）

### 1.1 常规路径不可用

- `gh` 2.103.3 已安装但**未登录**，环境无 `GH_TOKEN` / `GITHUB_TOKEN`；`~/.git-credentials` 按安全策略未读取。
- 匿名 REST 配额**已耗尽**。原始响应：

```
HTTP/1.1 403
X-RateLimit-Limit: 60
X-RateLimit-Remaining: 0
X-RateLimit-Used: 60
X-RateLimit-Reset: 1793617799
{"message":"API rate limit exceeded for 103.147.200.11. ..."}
```

  （Reset 1793617799 = 2026-11-02T11:09:59Z = 本地 19:09:59；本次取证在重置前完成主体部分。）

### 1.2 实际采用的免配额取证路径

1. **Actions 列表页 embedded 数据**：`https://github.com/merlin-kitsune/astral_dice/actions` → 页面内嵌 JSON（`payload.allWorkflowRunsRoute.workflowRuns`，25 条，`totalCount=147`），含 `id / runNumber / status.conclusion / createdAt.timestamp / branch.name / description.linkText(commit) / timing.text`。
2. **check-run annotations**：commit 的 `/<sha>/checks` 页与 `actions/runs/<id>` 页都以 `<annotation-message>` 元素回显该 step 输出的 `::error::` / `::notice::` / 行动告警 —— 这些就是 CI 的**原始报错文本**，且不需要配额、不需要登录。
3. **免配额旁证**：`badge.svg?branch=multi-main`（当前 `Build - failing`）、`releases.atom`（最新 Release `1.5.0`，updated `2026-11-02T05:12:44Z`）、Release 页附件清单。

### 1.3 限制（明确声明）

- **完整 step 日志（每条命令的 stdout/stderr）未取得 —— 已实测确认，不是推测**。配额重置后（2026-11-02T11:10:20Z 实测）再取 `GET /repos/merlin-kitsune/astral_dice/actions/jobs/125106569899/logs`：

```
HTTP=403  size=176
{"message":"Must have admin rights to Repository.",
 "documentation_url":"https://docs.github.com/rest/actions/workflow-jobs#download-job-logs-for-a-workflow-run",
 "status":403}
```

  ⇒ 匿名身份下**没有**可取 job 日志的路径。可用替代面是 `GET /actions/runs/<id>/jobs`（匿名 200，返回**每个 step 的 name/status/conclusion**）：本报告第 3 节据此把三次红全部定位到 **step 22**，但命令级 stdout/stderr 仍不可得。
- 本文所有「原因」均区分：CI annotation 直接给出的 = 证实；本地逐字复现得到的 = 证实；剩余推断 = 未核验。

---

## 2. Workflow run 列表（原始字段）

来源：`work/recon/_actions.html` 的内嵌 JSON（已落盘为 `work/recon/_emb_0.json`）。`branch.name` 为 `refs/heads/multi-main` 即分支 push，`refs/tags/1.5.0` 即 tag push。

| run id | # | 创建时间 (UTC) | ref | commit | 结论 | 时长 | 提交标题 |
|---|---|---|---|---|---|---|---|
| 41430344936 | 147 | 2026-11-02T10:37:48Z | refs/heads/multi-main | be6bc10 | **FAILURE** | 2m 0s | docs(rules): 发布时序硬规则…… |
| 41429965923 | 146 | 2026-11-02T10:35:03Z | refs/heads/multi-main | 3d7a3d7 | **FAILURE** | 2m 50s | fix(fabric): gate chip recipes …; release 1.5.0-hotfix |
| 41392466985 | 145 | 2026-11-02T05:10:32Z | refs/tags/1.5.0 | 6fe3813 | SUCCESS | 2m 18s | docs(changelog): … [republish-assets] |
| 41392445400 | 144 | 2026-11-02T05:10:20Z | refs/heads/multi-main | 6fe3813 | **FAILURE** | 2m 43s | docs(changelog): … [republish-assets] |
| 41392306502 | 143 | 2026-11-02T05:08:51Z | refs/tags/1.5.0 | 72d2048 | SUCCESS | 1m 43s | fix(fabric): add tooltip … [republish-assets] |
| 41392300273 | 142 | 2026-11-02T05:08:47Z | refs/heads/multi-main | 72d2048 | SUCCESS | 3m 3s | fix(fabric): add tooltip … [republish-assets] |
| 41390429350 | 141 | 2026-11-02T04:47:50Z | refs/tags/1.5.0 | 276f5c2 | SUCCESS | 2m 0s | ci: [republish-assets] 一次性放行 |
| 41389992306 | 140 | 2026-11-02T04:42:57Z | refs/heads/multi-main | 276f5c2 | SUCCESS | 2m 30s | ci: [republish-assets] 一次性放行 |
| 41389556803 | 139 | 2026-11-02T04:38:03Z | refs/tags/1.5.0 | d1ebd5b | SUCCESS | — | — |
| 41386411435 | 137 | — | refs/tags/1.5.0 | f38b564 | SUCCESS | 7m 0s | — |

> #135 及更早（#123–#135，10-26 ~ 10-30）全部 SUCCESS。**红线只出现在 #144 / #146 / #147**，即 2026-11-02 05:10Z 之后。

### 2.1 原始 JSON（verbatim，关键字段）

#146（41429965923）：

```json
{"name":"Build","runNumber":146,"id":"41429965923",
 "workflowFilePath":".github/workflows/build.yml",
 "title":"fix(fabric): gate chip recipes via Fabric resource conditions; releas…",
 "state":{"status":"WORKFLOW_RUN_STATUS_COMPLETED","conclusion":"WORKFLOW_RUN_CONCLUSION_FAILURE"},
 "createdAt":{"timestamp":"2026-11-02T10:35:03Z"},
 "branch":{"name":"refs/heads/multi-main","title":"multi-main"},
 "timing":{"kind":"WORKFLOW_RUN_TIMING_KIND_DURATION","text":"2m 50s"},
 "description":{"primaryText":"Commit","linkText":"3d7a3d7",
   "linkUrl":"/merlin-kitsune/astral_dice/commit/3d7a3d7bd5f3a54e35a9e6cba974e1d00965c310","action":"pushed"}}
```

#147（41430344936）：

```json
{"runNumber":147,"id":"41430344936",
 "state":{"conclusion":"WORKFLOW_RUN_CONCLUSION_FAILURE"},
 "createdAt":{"timestamp":"2026-11-02T10:37:48Z"},
 "branch":{"name":"refs/heads/multi-main"},
 "timing":{"text":"2m 0s"},
 "description":{"linkText":"be6bc10",
   "linkUrl":"/merlin-kitsune/astral_dice/commit/be6bc10ea3906c0ff307baffd55be43f8b853e0e","action":"pushed"}}
```

#144 / #145（同 commit，结论相反）：

```json
{"runNumber":144,"id":"41392445400","branch":{"name":"refs/heads/multi-main"},
 "state":{"conclusion":"WORKFLOW_RUN_CONCLUSION_FAILURE"},"timing":{"text":"2m 43s"},
 "createdAt":{"timestamp":"2026-11-02T05:10:20Z"},"description":{"linkText":"6fe3813"}}
{"runNumber":145,"id":"41392466985","branch":{"name":"refs/tags/1.5.0"},
 "state":{"conclusion":"WORKFLOW_RUN_CONCLUSION_SUCCESS"},"timing":{"text":"2m 18s"},
 "createdAt":{"timestamp":"2026-11-02T05:10:32Z"},"description":{"linkText":"6fe3813"}}
```

---

## 3. 失败 step 与原始报错

### 3.1 run #146（41429965923，commit 3d7a3d7）—— 已定位到行级原因

job id `125225791294`（`GET /actions/runs/41429965923/jobs`，started `2026-11-02T10:35:06Z` / completed `2026-11-02T10:37:52Z`）：**step 1–21 全部 success**（含 `Build with Gradle (all targets)`、`Read base version`、`Create Git tag`），**唯一 non-success = step 22**。失败 step 名（annotation 前缀即 step 名）：

```
step: Create/Update GitHub Release (four JARs, notes from release/<tag>/)

ANN #1  Create/Update GitHub Release (four JARs, notes from release/&lt;tag&gt;/)
        Process completed with exit code 1.
ANN #2  Create/Update GitHub Release (four JARs, notes from release/&lt;tag&gt;/)
        aborting release: 1 line(s) without a usable jar (fabric-1.20.1)
ANN #3  Create/Update GitHub Release (four JARs, notes from release/&lt;tag&gt;/)
        missing jar for fabric-1.20.1: fabric-1.20.1/build/libs/astral_dice-1.5.0+*.jar
```

同页另有 5 条非致命 annotation（Node 20 弃用、`actions/setup-java@v4` 弃用 ×3、`ubuntu-latest` 标签迁移提醒），与本红无关。

### 3.2 run #147（41430344936，commit be6bc10e）—— 同一根因，继承所致

job id `125227038930`（started `2026-11-02T10:37:51Z` / completed `2026-11-02T10:39:47Z`）：同样**只有 step 22** 为 failure，其余 28 个 step（含 post）success。annotation 原文：

```
step: Create/Update GitHub Release (four JARs, notes from release/&lt;tag&gt;/)
        missing jar for fabric-1.20.1: fabric-1.20.1/build/libs/astral_dice-1.5.0+*.jar
```

#147 的提交 `be6bc10e` 只改 `AGENTS.md` + `docs/agent/AGENTS-dsh.md`（`git show --stat`：9 insertions, 1 deletion），**没有**修版本树 ⇒ 与 #146 完全同因。

### 3.3 run #144（41392445400，commit 6fe3813）—— 只到 step 级，报错文本不可得

job id `125106569899`，run 页 annotation 原文：

```
Annotations: 1 error, 4 warnings, and 2 notices
error  build  Process completed with exit code 1.
       href="/merlin-kitsune/astral_dice/actions/runs/41392445400/job/125106569899#step:22:203"
notice build  republish marker found in commit message - replacing existing assets
```

读数：
- `#step:22` = UI 第 22 个 step；workflow 自 21 个 step，UI 第 1 个是 `Set up job` ⇒ **`step:22` 就是 workflow 的第 21 个 step = `Create/Update GitHub Release`**，与 #146/#147 同一个 step。
- 但**没有**任何 `::error::` 类 annotation，只有 `Process completed with exit code 1.` ⇒ 失败来自**某条命令自身非零退出**（`run:` 默认 `bash -e`，`set -e` 已生效），而非脚本里显式 `echo ::error::` 的分支。
- `notice: republish marker found in commit message - replacing existing assets` ⇒ 该 run **确实**进入了 `[republish-assets]` 通道（L405-L415）。
### 3.3.1 #144 的 step 级台账（`GET /actions/runs/41392445400/jobs`，匿名 200）

job `125106569899`，started `2026-11-02T05:10:24Z` / completed `2026-11-02T05:13:02Z`，`conclusion: failure`：

| step | 名称 | 结果 |
|---|---|---|
| 1–8 | Set up job / Checkout / Setup JDK 21·17·25 / Setup Gradle / Checkout StarEngine / Publish StarEngine | success |
| 9–12 | Check lang sync（四线） | success |
| 13–16 | Setup Python / CHANGELOG parity / chip acquisition / event-choice & combat gates | success |
| 17 | `Build with Gradle (all targets)` | **success** |
| 18–19 | Upload artifact（三线 / fabric） | success |
| 20 | `Read base version` | success |
| 21 | `Create Git tag (release branch push only)` | success |
| **22** | **`Create/Update GitHub Release (four JARs, notes from release/<tag>/)`** | **failure** |
| 38–45 | post 步骤 / Complete job | success（`Post Setup Python` 为 skipped） |

读数：
- 构建、版本读取、打 tag **全部成功** ⇒ #144 的失败**不**在 jar 收集（`build.yml:326`）也不在版本树——与 #146/#147 的机理**不同**；
- 失败必定发生在该 step 的 `gh` 调用段（`build.yml:396`–`build.yml:432`），而这正是 `[republish-assets]` 通道（annotation 已确认该 run 进入了该通道）；
- **该 step 的具体命令级日志未取得**（§1.3，`logs` 端点实测 403 `Must have admin rights to Repository.`）；因此「是 `delete-asset` 还是 `upload` 挂的」= **未核验**。

### 3.4 本地复现（把 CI 里那段 shell 原样搬下来跑）

复现脚本 `work/recon/_repro_release_step.sh` 由 `work/recon/_extract.py` **从 `build.yml` L285–L350 逐字抽取**（唯一改动：`$proj/build/libs` → `$SANDBOX/$proj/build/libs`），不跑 Gradle、不调 `gh`、不联网；`bash -n` 通过。

CASE A（`3d7a3d7b` / `be6bc10e` 的版本树：三线 `1.5.0+…`，fabric `1.5.0-hotfix+fabric_1.20.1`）：

```
jar ok (P0) neoforge-1.21.1: astral_dice-1.5.0+neoforge_1.21.1.jar
jar ok (P0) forge-1.20.1: astral_dice-1.5.0+forge_1.20.1.jar
jar ok (P1) neoforge-26.1.2: astral_dice-1.5.0+neoforge_26.1.2.jar
::error::missing jar for fabric-1.20.1: fabric-1.20.1/build/libs/astral_dice-1.5.0+*.jar
    present: astral_dice-1.5.0-hotfix+fabric_1.20.1.jar
::error::aborting release: 1 line(s) without a usable jar (fabric-1.20.1)
exit=1
```

CASE B（`6fe38138` / `72d2048` 的版本树：四线全 `1.5.0+…`）：

```
jar ok (P0) neoforge-1.21.1: astral_dice-1.5.0+neoforge_1.21.1.jar
jar ok (P0) forge-1.20.1: astral_dice-1.5.0+forge_1.20.1.jar
jar ok (P1) neoforge-26.1.2: astral_dice-1.5.0+neoforge_26.1.2.jar
jar ok (P1) fabric-1.20.1: astral_dice-1.5.0+fabric_1.20.1.jar
exit=0
```

CASE A 输出的三行 error 与 CI #146 的 annotation **逐字一致**（CI 未取到 `present:` 行，那是同一条报错分支的下一行）。

### 3.5 根因链条（逐跳都有实物）

1. `be6bc10e` / `3d7a3d7b` 上四线 `mod_version`：`neoforge-1.21.1`= `1.5.0+neoforge_1.21.1`、`forge-1.20.1`= `1.5.0+forge_1.20.1`、`neoforge-26.1.2`= `1.5.0+neoforge_26.1.2`、**`fabric-1.20.1`= `1.5.0-hotfix+fabric_1.20.1`**。
2. `build.yml:198`：`VERSION=$(grep '^mod_version=' neoforge-1.21.1/gradle.properties | cut -d'=' -f2)` — 只读**一条线**。
3. `build.yml:203`：`BASE=${VERSION%%+*}` ⇒ `BASE=1.5.0`（fabric 的 `-hotfix` 在这条链上完全不可见）。
4. `build.yml:326`：`for j in "$proj"/build/libs/astral_dice-"$BASE"+*.jar` — 四线共用这一个 glob。
5. fabric 产物实际是 `astral_dice-1.5.0-hotfix+fabric_1.20.1.jar` ⇒ 不匹配 `astral_dice-1.5.0+*.jar` ⇒ `hits` 为 0。
6. `build.yml:329-333` 打 `::error::missing jar for fabric-1.20.1` 并 `MISSING+=(fabric-1.20.1)`。
7. `build.yml:344-347` `MISSING` 非空 ⇒ `::error::aborting release: 1 line(s) without a usable jar (fabric-1.20.1)` + `exit 1` ⇒ step 红 ⇒ run 红。

补充事实：该 `exit 1` 位于**任何 `gh` 调用之前**，所以这两次红**没有损坏 Release**；Release `1.5.0` 现存四个附件（`astral_dice-1.5.0+fabric_1.20.1.jar` / `+forge_1.20.1.jar` / `+neoforge_1.21.1.jar` / `+neoforge_26.1.2.jar`），与 `6fe38138` 的版本树一致。
---

## 4. `.github/workflows/build.yml` 静态复核表

复核项：YAML 结构、`if:` 条件、env 传递、`gh` 可用性（CI 是否装 `gh` / `GH_TOKEN` / `permissions: contents: write`）、`[republish-assets]` 分支、tag 触发与 `is_prerelease` 推导、产物 glob 是否覆盖四线、Java 版本矩阵。

| 行号 | 原文（摘录） | 复核结论 |
|---|---|---|
| `build.yml:3-25` | `on: push: branches: [...] tags: ['[0-9]*']` + `pull_request` + `workflow_dispatch` | 正常。`tags: ['[0-9]*']` 能匹配 `1.5.0`；`1.5.0-rc.1` 也以数字开头，同样能触发（与「tag 只按 mod 版本」裁决一致） |
| `build.yml:30-31` | `permissions: contents: write` | 正常且**必要**——`gh release create/edit/upload/delete-asset` 都要写权限；run #142 成功执行了 `delete-asset`+`upload`，反证权限足够 |
| `build.yml:36-37` | `fetch-depth: 0` / `fetch-tags: true` | 正常。也正因如此 L303 的 `git cat-file -e`、L316 的子树判定才成立 |
| `build.yml:39-57` | 三步 `actions/setup-java@v4`：`java-version: '21'` / `'17'` / `'25'`，`distribution: 'temurin'` | **不是矩阵**，是三次顺序安装（靠 Gradle toolchain 选 JDK）。功能可跑（四个 tag run 都成功过），但每次 run 多装两个 JDK；annotation 里已提示 `setup-java@v4` 弃用。**与本次红无关** |
| `build.yml:62-110` | checkout `merlin-kitsune/StarEngine`，`ref: 16b48c413d92bcd830d97c0a80b0df4e0c47ba6c`，`path: .ci/StarEngine`；`./gradlew publishToMavenLocal` | 正常。库版本 1.1.0 与四线 `starengine_version=1.1.0` 对得上 |
| `build.yml:112-125` | 四条线 `pwsh -NoProfile -File tools/check_lang_sync.ps1 -LangDir <线>/.../lang`；fabric 步带 `if: hashFiles('fabric-1.20.1/gradle.properties') != ''` | 正常。`hashFiles` 守卫只用于 **fabric / 26.1.2 的历史兼容**（老 ref 无该子项目）；在当前 ref 上四线目录都在，守卫恒真 |
| `build.yml:127-140` | `actions/setup-python@v5` + `python tools/check_changelog_parity.py` + `pwsh … verify_chip_acquisition.ps1` | 正常。所引 10 个校验脚本在 `be6bc10e` 的树里**全部存在**（逐个 `git cat-file -e` 核过） |
| `build.yml:142-166` | 七个 `scripts/verify/*.py` 的硬红循环，`set -euo pipefail` + `::error::` + `exit $rc` | 正常；`set -euo pipefail` 只在这个 step 内显式出现 |
| `build.yml:168-172` | `run: ./gradlew build` | 正常（本次未跑，按要求） |
| `build.yml:174-193` | 两条 `actions/upload-artifact@v4`，`path: <线>/build/libs/astral_dice-*.jar` | **不影响 Release**：Release 步骤读的是磁盘上的 `build/libs`，不依赖 artifact。glob 本身够宽（`astral_dice-*.jar`），四线都被覆盖 |
| `build.yml:198` | `VERSION=$(grep '^mod_version=' neoforge-1.21.1/gradle.properties \| cut -d'=' -f2)` | ⛔ **根因所在**：全套版本推导只认 `neoforge-1.21.1` 一条线，四线版本分叉时**静默错** |
| `build.yml:203` | `BASE=${VERSION%%+*}` | 正常（在错误的输入上） |
| `build.yml:214-222` | `LOWER="${VERSION,,}"`；`if [[ "$BASE" == *-* ]]`；`if [[ "$LOWER" =~ (^\|[._+-])(alpha\|beta\|rc\|pre\|snapshot)([._+-]\|[0-9]\|$)` → `is_prerelease` | 逻辑本身正确；但同样只吃 `neoforge-1.21.1` 的版本，且 `-hotfix` 命中 ① 会被判 prerelease —— 与「按 mod 版本发正式版」的语义需要人工确认 |
| `build.yml:224-229` | `if [[ "$BASE" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z][0-9A-Za-z.-]*)?$ ]]` → `skip_tag=0` | 正则正确（`1.5.0` / `1.5.0-hotfix` / `1.5.0-rc.1` 都通过） |
| `build.yml:236-250` | `if: github.event_name == 'push' && github.ref == 'refs/heads/multi-main' && steps.version.outputs.skip_tag == '0'`；`TAG="${{ steps.version.outputs.base }}"`；已存在则复用 | 正常。因 `BASE=1.5.0`，**这次 hotfix 提交不会建出 `1.5.0-hotfix` tag**；`git push origin "$TAG"` 用 `GITHUB_TOKEN` 推送，不会二次触发 workflow（注释已说明） |
| `build.yml:266` | `if: startsWith(github.ref, 'refs/tags/') \|\| (github.event_name == 'push' && github.ref == 'refs/heads/multi-main' && steps.tag.outputs.tag != '')` | 正常。分支 run 与 tag run **都会**进这个 step ⇒ 同一个 Release 有两个写者 |
| `build.yml:267-270` | `env: GH_TOKEN: ${{ github.token }}`；`HEAD_COMMIT_MSG: ${{ github.event.head_commit.message }}` | 正常。`gh` 在 `ubuntu-latest` 上预装（#142 实际调用成功即为证据）；tag push 事件下 `HEAD_COMMIT_MSG` 为空，`${HEAD_COMMIT_MSG:-}` 的兜底写法正确，不会误触发重发通道 |
| `build.yml:272-276` | tag run 用 `${GITHUB_REF#refs/tags/}`，否则用 `steps.tag.outputs.tag` | 正常 |
| `build.yml:285-290` | `LINES=( "neoforge-1.21.1\|neoforge-1.21.1\|P0" … "fabric-1.20.1\|fabric-1.20.1\|P1" )` | 正常，四线齐 |
| `build.yml:302-307` | `SHA="${GITHUB_SHA:-$(git rev-parse HEAD)}"` + `git cat-file -e "${SHA}^{commit}"` | 正确且稳健（判不出就红，不猜） |
| `build.yml:316-319` | `git cat-file -e "${SHA}:${props}"` 否 ⇒ `::warning::skipping <line>` | 正确（历史 ref 豁免），本次四线都在，不触发 |
| `build.yml:326` | `for j in "$proj"/build/libs/astral_dice-"$BASE"+*.jar` | ⛔ **致命**：四线共用 `BASE` 收紧的 glob；任何一线版本段与 `neoforge-1.21.1` 不同 ⇒ 该线必然 0 命中 |
| `build.yml:329-333` | `::error::missing jar for $line: $proj/build/libs/astral_dice-$BASE+*.jar` | 报错信息本身**准确**（CI 原文一致），但**归因不指向版本分叉**，容易误判成「产物没打出来」 |
| `build.yml:344-347` | `::error::aborting release: ${#MISSING[@]} line(s) …` + `exit 1` | 正确且**在 gh 之前**——不会破坏 Release |
| `build.yml:349-352` | `::error::no jars collected at all` + `exit 1` | 正常兜底 |
| `build.yml:387-391` | `if [[ "${{ steps.version.outputs.is_prerelease }}" == '1' ]]` → `REL_KIND=(--prerelease --latest=false)` else `(--latest --prerelease=false)` | 语法可用（`gh release edit/create` 均支持这两个开关） |
| `build.yml:396-400` | `if gh release view "$TAG" >/dev/null 2>&1` → `gh release edit …`；`HAVE_ASSETS=$(gh release view "$TAG" --json assets --jq '.assets[].name')` | 命令写法正确（`$( )` 捕获而非进程替换，注释里已解释原因） |
| `build.yml:405-415` | `REPUBLISH=1` 当 `[[ "${HEAD_COMMIT_MSG:-}" == *"[republish-assets]"* ]]`；`while IFS= read -r name; do … gh release delete-asset "$TAG" "$name" --yes; done <<< "$HAVE_ASSETS"` | 语法正确、变量名正确、`--yes` 必填项已给；**但**：① 无并发保护，两个 run 同时删同一批附件时，后一个必然报「asset not found」；② 删除循环未做容错（`set -e` 下第一条失败即整 step 红）；③ 删完把 `HAVE_ASSETS` 清空 ⇒ 后续全部重传，与并发上传的 run 必然撞名 |
| `build.yml:416-425` | `if grep -Fxq -- "$name" <<< "$HAVE_ASSETS"` … else `gh release upload "$TAG" "$j"`（**无** `--clobber`） | 冻结语义正确；但在 `REPUBLISH=1` 通道下缺 `--clobber` 会放大并发撞名失败。`grep -Fxq` 放在 `if` 条件里不会被 `set -e` 误杀，写法正确 |
| `build.yml:427-432` | `gh release create "$TAG" "${JARS[@]}" --title … --notes-file … "${REL_KIND[@]}"` | 正确 |
| 全文 | **无 `concurrency:` 块**（`grep -n concurrency` 无命中；也无 `defaults: / shell:`） | ⛔ 结构性缺口：分支 push 与 tag push 会**并行**操作同一个 tag/Release；#142 / #144 / #145 三者在 05:08–05:13Z 完全重叠就是实例 |

### 4.1 静态复核的旁证（顺手排掉的老结论）

- `gradlew` 索引模式 = `100755`（`git ls-files -s gradlew`），不是经典的 `Permission denied` 问题。
- YAML 可解析：`python -c "import yaml; yaml.safe_load(open('.github/workflows/build.yml'))"` → OK，`jobs=['build']`，`steps=21`。
- `git log`：`2bc97366`（本地未推送的 docs 提交）← `be6bc10e` ← `3d7a3d7b` ← `6fe38138`（= tag `1.5.0`）← `72d2048a` ← `276f5c2e` ← `d1ebd5ba`。
- `release/1.5.0-hotfix/` 目录在 `3d7a3d7b` 与 `be6bc10e` 的树里**都存在** ⇒ 作者意图是发一个名为 `1.5.0-hotfix` 的 Release；但 `build.yml:198/240` 使 `TAG` 恒等于 `neoforge-1.21.1` 的 `BASE`（`1.5.0`），**这套意图在当前流水线里无法表达**。

---

## 5. 判定分级

### 5.1 证实

| 编号 | 判定 | 证据 |
|---|---|---|
| V1 | #146 与 #147 的红发生在 `Create/Update GitHub Release (four JARs, notes from release/<tag>/)` step，原因是 fabric 线 jar 未命中 glob | CI annotation 原文：`missing jar for fabric-1.20.1: fabric-1.20.1/build/libs/astral_dice-1.5.0+*.jar` / `aborting release: 1 line(s) without a usable jar (fabric-1.20.1)` / `Process completed with exit code 1.` |
| V2 | 直接机理 = `build.yml:198` 单线取版本 + `build.yml:326` 四线共用 `BASE` glob + `build.yml:344-347` 硬红 | `build.yml` L198 / L203 / L326 / L329-333 / L344-347 原文（§4）；`3d7a3d7b` 的 `fabric-1.20.1/gradle.properties` 为 `mod_version=1.5.0-hotfix+fabric_1.20.1` |
| V3 | 上述链路可本地逐字复现，输出与 CI annotation 一致 | `work/recon/_repro_release_step.sh` CASE A：三行 error 与 CI 原文逐字相同，`exit=1`；CASE B（四线同版本）`exit=0` |
| V4 | #147 是**继承性**失败：其提交只改文档，未改版本树 | `git show --stat be6bc10e`：仅 `AGENTS.md` + `docs/agent/AGENTS-dsh.md`（9+/1-）；CI annotation 与 #146 同一条 |
| V5 | #144 的红也在同一个 step，且该 run **确实**进了 `[republish-assets]` 通道 | run 页 annotation：`error build Process completed with exit code 1.` @ `#step:22`；`notice build republish marker found in commit message - replacing existing assets` |
| V6 | 这两次红**没有破坏 Release**：`exit 1` 在任何 `gh` 调用之前 | `build.yml:344-347` 位置；Release `1.5.0` 现仍完整持有四个 `1.5.0+…` 附件 |
| V7 | 三次红的失败点被逐 step 台账确认：**#146 / #147 / #144 都是且只是第 22 个 workflow step（Release step）失败**，其余 step 全 success | `GET /actions/runs/{41429965923,41430344936,41392445400}/jobs`：`#146` job 125225791294 `success 28 / failure 仅 step 22`；`#147` job 125227038930 同为 28 success / 仅 step 22 failure；`#144` job 125106569899 step 1–21 全 success、step 22 failure |

### 5.2 修正

| 编号 | 被修正的说法 | 实际情况 |
|---|---|---|
| C1 | 「最新的 docs 提交把 CI 弄红了」 | 不是。`be6bc10e` 只动文档；红是 `3d7a3d7b` 的版本分叉遗留，docs 提交只是**继承了红** |
| C2 | 「tag `1.5.0` 的 run 是绿的，说明版本树没问题」 | tag run #145 绿是因为 `6fe38138` 上四线**都是** `1.5.0`；同样的 commit 上分支 run #144 红，`is_prerelease`/glob 都不是原因 ⇒ 绿/红之差不能用版本树解释 |
| C3 | 「`[republish-assets]` 通道写坏了」 | 通道本身是通的：`276f5c2e`/`72d2048a`/`6fe38138` 三处 run 都打出 `republish marker found … replacing existing assets`，且 #142/#143 成功完成 delete+upload |

### 5.3 证伪

| 编号 | 假设 | 证伪依据 |
|---|---|---|
| F1 | YAML 缩进/语法错误 | `yaml.safe_load` 通过；21 个 step 全部解析 |
| F2 | `gradlew` 无执行位（`Permission denied`） | `git ls-files -s gradlew` → `100755` |
| F3 | CI 没有 `gh` / 没有 `GH_TOKEN` / `permissions` 不足 | `ubuntu-latest` 预装 `gh`；`GH_TOKEN: ${{ github.token }}` 已注入；`contents: write` 已给；#142 `delete-asset`+`upload` 实际成功 |
| F4 | 引用的校验脚本缺失导致前面步骤红 | 10 个脚本在 `be6bc10e` 树里逐个 `git cat-file -e` 全部存在；且 annotation 明确指向 Release step |
| F5 | 产物 glob 漏掉某条线（`astral_dice-*.jar` 太窄/太宽） | glob 是 `astral_dice-$BASE+*.jar`，四线产物命名规则一致（`astral_dice-<mod_version>.jar`）；漏的原因是**版本段**不同，不是通配符形态 |
| F6 | Release 步骤的 `if:` 条件把 tag run / 分支 run 排除了 | 两次红都发生在该 step **内部**（step 已运行并 `exit 1`），说明 `if:` 判定为真 |

### 5.4 未核验

| 编号 | 待核验项 | 缺什么证据 |
|---|---|---|
| U1 | #144 具体挂在哪条 `gh` 命令上 | 该 step（`#step:22`）的**命令级 stdout/stderr**。配额恢复后实测 `GET /actions/jobs/125106569899/logs` → 403，body = `Must have admin rights to Repository.`；匿名身份无此面，只能到「step 22 + 退出码 1」 |
| U2 | #144 的失败是否确为**并发竞态**（而非 gh 版本/网络抖动、API 抖动） | 同样缺 U1 的日志。间接证据已相当强：① 同 commit 的 tag run #145（05:10:32–05:12:50）**绿**而分支 run #144（05:10:20–05:13:02）**红**，两者同版本树、同 `[republish-assets]` 通道；② 三个 run（#142 05:08:47–05:11:50、#144 05:10:20–05:13:03、#145 05:10:32–05:12:50）时间窗完全重叠，操作同一个 tag `1.5.0`；③ `[republish-assets]` 通道先逐个 `delete-asset`（无容错）再 `upload`（无 `--clobber`）；④ 全文件无 `concurrency:` 组。**但**「哪条命令先挂」仍无逐字证据 |
| U3 | fabric 线 `1.5.0-hotfix` 是**有意**的独立版本，还是误改 | 仓内存在 `release/1.5.0-hotfix/`（有意），但流水线的 tag 推导完全不支持该意图（矛盾）。需用户裁决「hotfix 算不算独立版本」 |

---

## 6. 修复建议（未落地，只给方案）

> 前提：本次未改任何文件。下面每条都写清「改哪行 / 改什么 / 如何本地验证」。

### P0-a 让 #146/#147 这类红消失（二选一，取决于 U3 的裁决）

**方案 A（推荐，若 `1.5.0-hotfix` 只是一时笔误）——对齐四线版本：**

- 改：`fabric-1.20.1/gradle.properties` 的 `mod_version=1.5.0-hotfix+fabric_1.20.1` → `mod_version=1.5.0+fabric_1.20.1`。
- 为什么够：`BASE` 回到 `1.5.0`，`build.yml:326` 的 glob 立刻命中 fabric jar（CASE B 已验证 `exit=0`）。
- 本地验证：
  - `grep -n '^mod_version=' neoforge-1.21.1/gradle.properties forge-1.20.1/gradle.properties neoforge-26.1.2/gradle.properties fabric-1.20.1/gradle.properties` → 四行 `+` 前必须相同；
  - `BASE=1.5.0 SANDBOX=<fixture> bash work/recon/_repro_release_step.sh` → `exit=0`。
- 附带：`release/1.5.0-hotfix/` 目录要么删掉，要么留作说明（它当前没有任何步骤会读它）。

**方案 B（若确实要 per-line hotfix 后缀，不动版本号）——按各线自己的版本收紧 glob：**

- 改：`build.yml:325-328` 之前插入两行，把 glob 改成用**该线自己的** mod_version：

```bash
LV=$(grep '^mod_version=' "$props" | cut -d'=' -f2)
LB=${LV%%+*}
if [[ "$LB" != "$BASE" ]]; then
  echo "::warning::version skew: $line has base '$LB' while tag base is '$BASE'"
fi
for j in "$proj"/build/libs/astral_dice-"$LB"+*.jar; do
```

- 代价：Release 里会出现版本段不一致的四个附件（`1.5.0` ×3 + `1.5.0-hotfix` ×1），与「同一 Release 即同一版本」的直觉冲突；且 `is_prerelease`（`build.yml:214-222`）仍只看 neo 线。**建议不要单独采用**，要么 A，要么 C。

**方案 C（若 `1.5.0-hotfix` 是正式要发的独立版本）：**

- 改：四线 `mod_version` 统一 bump 成 `1.5.0-hotfix+<line>`（`BASE=1.5.0-hotfix` ⇒ tag `1.5.0-hotfix`，`is_prerelease=1` 走 `--prerelease`）。
- **需要用户裁决**：这与「tag 只按 mod 版本」的旧裁决不冲突（tag 仍是 mod 版本），但会把 hotfix 标成 prerelease。若不希望标 prerelease，需同时把 `build.yml:216` 的 `*-*` 判据排除 `hotfix`。

### P0-b 让同类问题在**构建之前**就红（建议无论选 A/B/C 都加）

- 改：在 `build.yml:168`（`./gradlew build`）**之前**插入一个 `Check mod_version consistency (four lines)` step：

```bash
for d in neoforge-1.21.1 forge-1.20.1 neoforge-26.1.2 fabric-1.20.1; do
  [[ -f "$d/gradle.properties" ]] || continue
  v=$(grep '^mod_version=' "$d/gradle.properties" | cut -d'=' -f2)
  echo "$d = ${v%%+*}"
done | tee /tmp/vers.txt
n=$(cut -d= -f2 /tmp/vers.txt | sed 's/ //g' | sort -u | wc -l)
if [[ "$n" -ne 1 ]]; then
  echo "::error::mod_version base mismatch across loader lines (see above) — tag/glob derivation assumes a single base"
  exit 1
fi
```

- 收益：失败点从「跑完 2m50s 的最后一 step」前移到构建前，报错直接指向版本分叉，而不是误导性的 `missing jar`。
- 本地验证：临时把 fabric 的 `mod_version` 改回 `1.5.0-hotfix+…`，跑这段应 `exit 1`；改回 `1.5.0+…` 应 `exit 0`。

### P1 消掉 #144 这类并发红（与本仓现存的「分支 push + tag push 双写」结构直接相关）

1. **加 job 级 `concurrency`**（`build.yml` 顶层 `jobs.build:` 之下）：

```yaml
    concurrency:
      group: release-astral-dice
      cancel-in-progress: false
```

   说明：`group` 不能用 `${{ github.ref }}`——分支 run 与 tag run 的 ref 不同，仍会并行。要按 Release 串行，用固定组名（或 `release-${{ github.repository }}`）代价是整条流水线串行；若怕影响 PR 反馈，可把并发约束放在「tag push 与 multi-main push」两类事件上（GitHub 的 `concurrency` 不支持按事件分流，需拆成两个 job 或改用表达式 `${{ github.event_name == 'push' && ... }}` 拼组名）。

2. **给重发通道加容错**（只改 `REPUBLISH=1` 分支内，冻结语义不变）：
   - `build.yml:412`：`gh release delete-asset "$TAG" "$name" --yes` → 后面补 `|| echo "::warning::asset already gone: $name"`（并发下被别的 run 删掉属正常）；
   - `build.yml:423`：`gh release upload "$TAG" "$j"` → 加 `--clobber`（仅在该分支内），避免「同秒被另一 run 传上去」导致撞名失败。
   - 注意：这两条只解决**竞态**，不解决「两个 run 谁的内容是最终态」——最终态仍取决于谁最后写完；要确定性就必须串行（第 1 条）。

### P2 与本次红无关、但值得顺手收的告警

- `actions/checkout@v4`、`actions/setup-java@v4`、`actions/setup-python@v5`、`actions/upload-artifact@v4`、`gradle/actions/setup-gradle@v4` 均触发 **Node 20 弃用** annotation（强制跑在 Node 24 上）；`setup-java@v4` 另有独立弃用提示，官方建议迁 `@v5`。
- `ubuntu-latest` 将于 2026-10-19 起迁到 Ubuntu 26（runner 侧公告）。
- Java 版本可用 `matrix:` 表达（21/17/25 三步 setup-java 的装法本身能跑，只是更慢）。

### 本地验证工具箱（全部只读，可直接复用）

```bash
# 1) YAML 结构
python -c "import yaml;yaml.safe_load(open('.github/workflows/build.yml'))"
# 2) 只抽 Release 步骤的 jar 收集段（不跑 Gradle / 不调 gh / 不联网）
python work/recon/_extract.py            # 生成 work/recon/_repro_release_step.sh
bash -n work/recon/_repro_release_step.sh
# 3) 两种版本树的对照（fixture 在 work/recon/_repro/A、/B）
BASE=1.5.0 GITHUB_SHA=be6bc10ea3906c0ff307baffd55be43f8b853e0e SANDBOX=$PWD/work/recon/_repro/A bash work/recon/_repro_release_step.sh   # expect exit=1
BASE=1.5.0 GITHUB_SHA=6fe38138efff047e63516391140742fff6b032c3 SANDBOX=$PWD/work/recon/_repro/B bash work/recon/_repro_release_step.sh   # expect exit=0
# 4) 四线版本一致性
grep -H '^mod_version=' neoforge-1.21.1/gradle.properties forge-1.20.1/gradle.properties neoforge-26.1.2/gradle.properties fabric-1.20.1/gradle.properties
```

（`actionlint` 未安装、未使用——本机没有该二进制，且安装需联网。）

---

## 7. 一句话结论

**CI 红的原因是：发布流水线用 `neoforge-1.21.1` 一条线的 `mod_version` 推导 tag 与四线 jar 的收集 glob（`build.yml:198`、`build.yml:326`），而 `3d7a3d7b` 把 `fabric-1.20.1` 单独 bump 成 `1.5.0-hotfix`，导致 Release 步骤收集不到 fabric jar、按设计 `exit 1`（#146/#147，已由 CI annotation + 本地逐字复现证实）；至于 #144 那次红，失败点同在该 Release 步骤但具体 `gh` 命令报错文本不可得（日志需鉴权），现有取证只能指出最可能的结构性原因——同一 Release 上三个 run 并发 delete/upload 且工作流无 `concurrency:` 保护，缺的那一项证据就是 #144 该 step 的命令级日志（配额恢复后实测该端点仍返回 403 `Must have admin rights to Repository.`，匿名身份取不到）。**

---

### 附：本报告用到的取证文件（均在 `work/recon/`，可复算）

| 文件 | 内容 |
|---|---|
| `_actions.html` / `_emb_0.json` | Actions 列表页与内嵌 JSON（25 条 run 原始字段，`totalCount=147`） |
| `_checks_<sha>.html` / `_checks_be6.html` | commit checks 页（annotation 原文来源） |
| `_run144.html` | run #144 页面（`step:22` + 1 error / 4 warnings / 2 notices） |
| `_rel150.html` | Release 1.5.0 页（四个附件名） |
| `_extract.py` → `_repro_release_step.sh` | 从 `build.yml` L285-350 逐字抽取的 jar 收集段（本地复现） |
| `_repro/A` / `_repro/B` | 两套版本树的 fixture（空 jar 文件） |
| `_ann*.py` / `_emb_dump.py` | annotation 与 run 字段提取脚本 |
