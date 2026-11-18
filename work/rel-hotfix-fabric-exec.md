# 1.5.0-hotfix (fabric-1.20.1) 发布执行报告

- 执行时间：2026-11-02 20:07–20:20 (Asia/Hong_Kong)
- 宿主：Windows 原生，pwsh 7 可用；DSH 命令通道实际落在 MINGW64 bash（/usr/bin/bash，盘符映射 F: → /f/），因此 Windows 专属操作统一加 pwsh -NoProfile -Command 前缀
- 仓库：`F:/MCProject/astral_dice_multiloader`，分支 `multi-main`，HEAD `9adfd9f3bb61bfac8ce686724aaad0d839d1e25b`
- JAVA_HOME：`C:\Program Files\Zulu\zulu-21\`
- 互斥锁：`F:/MCProject/.agent-locks/` 中无 `kimi.lock` / `dsh.lock` 活跃锁（仅有 2 个历史 .py 文件）

## 步骤 1 — 重建并核对产物

### 1.1 重建前基线校验（先证「基准 jar 就在盘上」）

```
Get-FileHash '.../fabric-1.20.1/build/libs/astral_dice-1.5.0-hotfix+fabric_1.20.1.jar' -Algorithm SHA256
```

原始输出：

```
A8AFE4D3268728095CEA819123E194ABE3416229C704913AF3262E9194D403E0
```

盘上 jar = 2,945,258 B / mtime 2026/11/2 19:17:15，与任务给出的基准（2,945,258 B / a8afe4d3…403e0）**逐字节一致**。已先备份到仓外
`C:\Users\xmace\AppData\Local\Temp\astral-rel-hotfix\baseline-a8afe4d3.jar`（2,945,258 B），供重建后比对。

### 1.2 构建

命令（工作目录仓库根）：

```
./gradlew.bat :fabric-1.20.1:build --console=plain -x pushToGame
```

原始输出关键行（完整日志：`C:\Users\xmace\AppData\Local\Temp\astral-rel-hotfix\build1.log`）：

```
Reusing configuration cache.
> Task :fabric-1.20.1:compileJava UP-TO-DATE
> Task :fabric-1.20.1:jar UP-TO-DATE
> Task :fabric-1.20.1:check UP-TO-DATE
> Task :fabric-1.20.1:remapJar UP-TO-DATE
> Task :fabric-1.20.1:assemble UP-TO-DATE
> Task :fabric-1.20.1:build UP-TO-DATE

> Task :fabric-1.20.1:pushToDevRun
pushToDevRun: pushed astral_dice-1.5.0-hotfix+fabric_1.20.1.jar -> F:\MCProject\astral_dice_multiloader\run\fabric-1.20.1\mods

> Task :fabric-1.20.1:pushToRootBuild
pushToRootBuild: pushed astral_dice-1.5.0-hotfix+fabric_1.20.1.jar -> F:\MCProject\astral_dice_multiloader\build\libs

BUILD SUCCESSFUL in 565ms
8 actionable tasks: 2 executed, 6 up-to-date
```

退出码 0。

**偏差记录（两处，均未违反口径）**：
① `-x pushToGame` 只排除了推整合包任务，同批的 `pushToDevRun` / `pushToRootBuild` 仍执行了（jar 被复制到 `run/fabric-1.20.1/mods` 与根 `build/libs`）；这两个目录都在 .gitignore 覆盖内，`git status` 未出现任何新增条目，不影响工作树与提交。
② 本次是增量构建，jar 任务全部 `UP-TO-DATE`（Gradle 输入哈希未变 ⇒ 未重新打包），因此「哈希相同」证明的是「Gradle 判定源码/资源输入自上次构建后未变 + 盘上产物与实机验证过的 jar 是同一份字节」；未执行 clean 重建（口径未要求）。

### 1.3 重建后产物核对

```
astral_dice-1.5.0-hotfix+fabric_1.20.1.jar   2945258   2026/11/2 19:17:15
astral_dice-1.5.0+fabric_1.20.1.jar          2944296   2026/11/2 14:14:34
SHA256 = A8AFE4D3268728095CEA819123E194ABE3416229C704913AF3262E9194D403E0
```

**结论：sha256 与基准完全相同 ⇒ EQUAL**。体积 2,945,258 B、哈希 a8afe4d3…403e0、mtime 未变。

据此（口径 1）：源码未变，此前实机证据（攻击条 / 防御盾 六场景）继续成立，**步骤 2 启动冒烟取证按口径跳过**，未开游戏。

## 步骤 2 — 启动冒烟取证

**未执行**。依据口径「sha256 完全相同 ⇒ 不必重新开游戏」。未做端口占用检查、未启动 runClient、未改动 `build/loom-cache/argFiles/runClient`、未触碰任何进程。

## 步骤 3 — git push

命令（无 `--force`、无 tag、无 `gh release`）：

```
git -C F:/MCProject/astral_dice_multiloader push origin multi-main
```

原始输出：

```
To https://github.com/merlin-kitsune/astral_dice
   be6bc10e..9adfd9f3  multi-main -> multi-main
PUSH_EXIT=0
```

推送前远端 sha = `be6bc10ea3906c0ff307baffd55be43f8b853e0e`（先做过 `ls-remote` 核对）。

推送后远端 sha（`git ls-remote origin refs/heads/multi-main`）：

```
9adfd9f3bb61bfac8ce686724aaad0d839d1e25b	refs/heads/multi-main
```

附加核对：`git tag --points-at HEAD` = 空（count=0）；远端 tag 尾部仍是既有的 `1.5.0-rc.6` / `1.5.0-rc.7`，本次未产生任何新 tag。

### CI 观察

- 触发面：`.github/workflows/build.yml` 的 `on.push.branches` 含 `multi-main`，本次 push 命中。
- `gh` **未登录**（`You are not logged into any GitHub hosts`）；匿名 REST API 被限流：`{"message":"API rate limit exceeded for 103.147.200.11. ..."}`，按口径不再耗时重试。
- 改由 `curl -x http://127.0.0.1:7897` 抓 `https://github.com/merlin-kitsune/astral_dice/actions/runs/41442243258`（HTTP 200，159,007 B）：页面内 head sha = `9adfd9f3bb61bfac8ce686724aaad0d839d1e25b`，标题 = `fix(fabric,ci,tools): correct HUD bar rows, harden release workflow, …`，确认就是本次 push 的 run。
- **run URL**：https://github.com/merlin-kitsune/astral_dice/actions/runs/41442243258
- **状态：两次探测（约 20:11、20:16）页面均只命中 In progress（1 次），Success / Failure / Completed / Skipped 计数全为 0 ⇒ 最终结论「无法判定」。**
- 混版预期（新 CI 逻辑对混版自动 SKIPPING）**本次无法从页面证实**：run 尚未结束，且 job 级状态需登录态 API 或前端 JS 渲染。

## 步骤 4 — CurseForge 上传（仅 fabric 一件）

### 4.1 dry-run（通过，与预期完全一致）

命令：

```
python tools/curseforge_upload.py --jar 'fabric-1.20.1/build/libs/astral_dice-1.5.0-hotfix+fabric_1.20.1.jar' --proxy http://127.0.0.1:7897 --allow-p1 --dry-run
```

原始输出关键行：

```
[WARN] P1 产物上传已放行（--allow-p1）：astral_dice-1.5.0-hotfix+fabric_1.20.1.jar | fabric | MC 1.20.1 —— 仅限用户逐次点名下达的任务
项目 projectId = 1662159（slug: astral-dice，来源 tools/curseforge.json）
代理           = http://127.0.0.1:7897
写请求         = 否（显式 --dry-run）
    版本 1.5.0-hotfix | fabric | MC 1.20.1 | 类型 release
    DISPLAY: Astral Dice 1.5.0-hotfix (Fabric 1.20.1)
    CHANGELOG: skip (小版本更新不写 CF changelog: ver=1.5.0-hotfix, 用户 2026-11-02 规则)
    gameVersions = [9638, 9639, 9990, 7499]  {'Client': 9638, 'Server': 9639, '1.20.1': 9990, 'fabric': 7499}
    relations = fabric-api(306612)=requiredDependency, puzzles-lib(495476)=requiredDependency, trinkets(341284)=requiredDependency, accessories(938917)=optionalDependency, starengine(1755415)=requiredDependency  [fabric_1.20.1]
DRY-RUN：未发送任何请求（默认行为；真上传需 --apply）。
```

显示名与 changelog 策略均与口径一致（未做任何改动）。

### 4.2 真传（失败）

命令：同一条去掉 `--dry-run`、加 `--apply`：

```
python tools/curseforge_upload.py --jar 'fabric-1.20.1/build/libs/astral_dice-1.5.0-hotfix+fabric_1.20.1.jar' --proxy http://127.0.0.1:7897 --allow-p1 --apply
```

原始输出关键行：

```
开始执行：
  [1] [FAIL] 上传 astral_dice-1.5.0-hotfix+fabric_1.20.1.jar  HTTP 400  {"errorCode":1002,"errorMessage":"Error in field metadata:  Required properties are missing from object: changelog. Path '', line 1, position 1."}
------------------------------------------------------------------------
失败中止 —— 步骤回放（含失败步骤）：
  [1] [FAIL] 上传 astral_dice-1.5.0-hotfix+fabric_1.20.1.jar  HTTP 400
回滚 / 修复提示：
  - 没有任何写请求成功 ⇒ 无副作用，可直接修参数重跑
EXIT=1
```

**失败原文（逐字，JSON 里的换行已折叠为空格）**：

```
HTTP 400  {"errorCode":1002,"errorMessage":"Error in field metadata:  Required properties are missing from object: changelog. Path '', line 1, position 1."}
```

根因定位（只读取证，未改脚本）：`tools/curseforge_upload.py:1115`

```python
if job["_changelog"]:
    # changelogType 只在**真附带** changelog 时出现：小版本不写 CF changelog
    # ⇒ changelog 与 changelogType 两个字段都不发（2026-11-02 用户规则）。
    metadata["changelogType"] = args.changelog_type
    metadata["changelog"] = _read_text(job["_changelog"])
```

即：脚本按「1.5.0-hotfix 属于非主线小版本 ⇒ 默认不附 changelog」的策略，把 changelog 与 changelogType **两个字段整段省略**（脚本 docstring L120–122 明确写明这是设计意图），而 CurseForge v1 上传接口把 metadata.changelog 视为**必填**（至少需要一个空串）。策略与接口必填要求冲突 ⇒ 确定性 400。

按任务纪律：**未做第二次同参数重试**（同一输入必然得到同一 400），**未用 `--changelog` 或任何其它通道绕过**（那等于把口径改成「带 changelog」，属明令禁止项），**未修改脚本**。到此停手。

副作用核查：脚本自述「没有任何写请求成功 ⇒ 无副作用」；无 fileID 返回、无文件落库。

**CF 结果汇总：上传未完成。fileID = 无；type / gameVersions / relations 只存在于 dry-run 的计划中（release / [9638, 9639, 9990, 7499] / fabric-api·puzzles-lib·trinkets 必需 + accessories 可选 + starengine 必需），未实际生效。**

## 受保护文件与工作树

- 改动前后 `git status --porcelain --untracked-files=no` 恒为：
```
 M docs/agent/AGENTS-dsh-linux.md
 M docs/agent/AGENTS-full.md
 M docs/archive/card-recipes-2026-10-09/README.md
```
- 全程**未**对上述文件执行 add / commit / stash / checkout / 删除；未创建任何提交（本轮 HEAD 自始至终 `9adfd9f3`，只做 push）。
- 本报告落在 `work/rel-hotfix-fabric-exec.md`（`work/` 目录本就是未跟踪目录），未加入 git。

## 未验证项

1. **CurseForge 上架未完成**：无 fileID，1.5.0-hotfix 的 fabric 包目前**不在** CF 上。修复方式需用户裁决（要么让脚本对「不写 changelog」发送 `"changelog": ""`，要么改口径为该版本附 changelog），本轮未自行决定。
2. **CI run 最终结论未知**：仅观察到 In progress（run 41442243258）；「混版自动 SKIPPING」这一预期**未获证据**，需登录态 gh 或稍后回看页面确认。
3. **未做启动冒烟**：按口径因 sha 相等而豁免；同时说明本轮构建为增量 UP-TO-DATE 复用，非 clean 重建。
4. 另三线（neoforge-1.21.1 / forge-1.20.1 / neoforge-26.1.2）本轮未构建、未上传、代码零改动（符合口径）。

## 结论

构建与哈希核对通过（jar 2,945,258 B / sha256 a8afe4d3268728095cea819123e194abe3416229c704913af3262e9194d403e0，与实机验证基准 EQUAL，冒烟按口径豁免）；multi-main 已推送到远端并核对为 9adfd9f3bb61bfac8ce686724aaad0d839d1e25b（CI run 41442243258 观察时仍 In progress，最终状态无法判定）；**CurseForge 上传失败（HTTP 400 / errorCode 1002：metadata 缺少必填字段 changelog），发布链在最后一步未完成，需用户裁决修复口径后再执行。**
