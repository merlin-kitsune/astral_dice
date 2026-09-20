# Agent Instructions

> **子项目默认规则(必须遵守)**:所有功能/修复默认**同步修改两个版本**(`neoforge-1.21.1` + `forge-1.20.1`),两侧保持功能对等;每次改动完成后由代理**自动本地提交**并**自动部署到整合包**(随 `gradlew build` 触发),但**默认不执行 `git push`**。

项目基线:
- 主线子项目 `neoforge-1.21.1`:MC 1.21.1 / NeoForge 21.1.235 / Java 21 / ModDevGradle(`net.neoforged.moddev` 2.0.141)
- 移植子项目 `forge-1.20.1`:MC 1.20.1 / Forge 1.20.1-47.4.10 / Java 17 / ModDevGradle LegacyForge(`net.neoforged.moddev.legacyforge` 2.0.144)
- 第三条线 `neoforge-26.1.2`:MC 26.1.2 / NeoForge 26.1.2.109 / Java 25 / ModDevGradle 2.0.147;由 1.21.1 源码整体迁移,差异见 `docs/compat-26.1.2-neoforge.md`。**2026-09-17 起已并入主线目录**(原独立 worktree `F:\MCProject\astral_dice_multiloader-26.1.2` / 分支 `multi-26.1.2-neoforge` 已合并进 `multi-1.20.1-1.21.1`,现行工作目录即主线根目录,三子项目同树)。
- Base package/group: com.merlinkitsune.astral_dice;各子项目产物名均为 `astral_dice-<版本>.jar`,版本号自带加载器后缀(1.21.1 带 `+neoforge_1.21.1`,1.20.1 带 `+forge_1.20.1`,26.1.2 带 `+neoforge_26.1.2`,下划线分隔)。

When extending this workspace:
- Prefer editing the existing Gradle configuration before creating new files.
- Keep Forge/NeoForge and Minecraft version properties synchronized in each subproject's gradle.properties and build.gradle.
- New external Java libraries should be added as Gradle Maven dependencies in the dependencies block and, if needed, a corresponding repository should be declared in the repositories block (in the affected subproject's build.gradle).
- 新增第三方**模组**依赖:只能经 **Curse Maven**(`curse.maven:`)或 **Modrinth Maven**(`maven.modrinth:`)获取,并纳入同一套闸门——完整口径见下方「## 模组依赖添加规则(统一口径,1.20.1 + 1.21.1)— 必须遵守」;本地 jar(`fileTree`/`files`)不得作为模组来源。
- Reuse the existing MDK-style structure instead of scaffolding a different mod layout.
- When new source/resources are added, keep the mod metadata generation task and resource declaration intact.

## 工作树与路径纪律（多 worktree 仓库）— 必须遵守

本仓存在**同级的多个 git worktree**，它们的磁盘路径与「会话工作目录」经常不一致，踩过一次真实事故（改动被写到另一个工作树），故固化两条硬规则：

1. **分支归属纪律（禁止镜像/回填）**：任务只属于哪个分支，就**只在那个工作树的磁盘路径**上处理，**禁止**把改动「镜像/回填/同步」到另一个工作树。
   - 当前两个常驻工作树：**主线** `F:\MCProject\astral_dice_multiloader`（分支 `multi-1.20.1-1.21.1`，含三条发布/移植线，属**封包**状态）与 **2.0.0 开发线** `F:\MCProject\astral_dice_multiloader-next`（分支 `multi-dev-next`）。
   - dev 分支的任务（如目标选择器改造与其工具链改动）**只能**在 `-next` 工作树落地；主线的 `scripts/test/**` 等文件**必须保持封包版本**（改动若必须进主线，须由用户在主线工作树内单独裁决，不由 dev 任务顺带推送）。
   - 核验方式（收尾必做）：`git -C F:\MCProject\astral_dice_multiloader status --short --untracked-files=all` 除 `?? .agent-teams/**` 外**必须无输出**；`git -C F:\MCProject\astral_dice_multiloader rev-parse --short HEAD` 必须仍是收尾前的封包提交。**所有 git 命令一律带 `-C <工作树绝对路径>`**，不要依赖 cwd。
2. **相对路径陷阱（必须用绝对路径）**：.NET / `System.IO` / 多数文件 API 的**相对路径按进程 cwd 解析**，而本会话的进程 cwd 往往是**主线工作树** —— 于是「写一个 `scripts/test/cases/xxx.json`」会静默落到**主线**（实测事故：一条 0 字节用例文件落在主线，事后删除复原）。
   - 规则：跨工作树的任务**写文件一律用绝对路径**（`F:\MCProject\astral_dice_multiloader-next\...`），或先 `Set-Location` 到目标工作树再操作；`git` 命令用 `-C` 显式指定工作树；脚本内的输出路径同理（相对路径只在「已知 cwd 就是目标工作树」时可用）。
   - 自查：改动后对**另一个**工作树跑一次 `git status --short`，确认没有意外文件。

## 命令执行规范（Shell）— 必须遵守（2026-09-19 用户裁决）

**一律使用 PowerShell 7（`pwsh`）；禁止使用 Windows PowerShell 5.1。**

- **背景（实测）**：本代理工具的默认 shell 是 **Windows PowerShell 5.1**（`$PSVersionTable.PSVersion` = 5.1.x），而本仓全部工具链脚本与 AGENTS 示例均按 **pwsh 7**（当前 7.6.6）编写。5.1 下的差异会**静默改变行为或直接报错**：① `if` / `switch` **不能作表达式**（`(if ($x) { … } else { … })` 报「术语 'if' 不会被识别为 cmdlet…」，实测踩中）；② 不支持三元 `? :`、`??`、`?.`；③ `ConvertFrom-Json -AsHashtable`、`-Depth`、`Test-Json` 等参数缺失或语义不同；④ 默认编码、`$PSNativeCommandUseErrorActionPreference`、`&&`/`||` 等行为不同。
- **规则**：
  1. 多行或含 `if` 表达式 / 三元 / PS7 专用参数的命令，**一律落盘为 `.ps1` 再用 pwsh 跑**：`pwsh -NoProfile -File <绝对路径>`（脚本放 `temp/tNN/`，绝对路径，见「工作树与路径纪律」）。
  2. 单行命令用 `pwsh -NoProfile -Command '…'`。
  3. **禁止**直接用工具默认 shell 跑 PowerShell 代码（尤其上述语法）。
  4. 调用本仓脚本（`scripts/test/*.ps1`、`tools/*.ps1`、`scripts/devtools/*.ps1`、`scripts/maintenance/*.ps1`）**必须**显式写 `pwsh -NoProfile -File …`；文档与交付说明里的示例同样写 `pwsh`。
- **自查**：报错里出现「术语 'if' 不会被识别为 cmdlet / 函数 / 脚本文件」＝ 用了 5.1，按本条改用 pwsh 重跑；发现任何 AGENTS/文档示例写裸 `powershell` 或省略 `pwsh` 前缀的，一并改正。

## Git 代理规范（Git Proxy）— 必须遵守

访问远程 Git 仓库（fetch/pull/push/clone 等任何网络操作）**必须经本地代理服务器**：

- 代理地址：`http://127.0.0.1:7897`（HTTP/HTTPS 均使用该地址）。
- 仓库级配置（已在仓库内执行）：
  ```bash
  git config http.proxy http://127.0.0.1:7897
  git config https.proxy http://127.0.0.1:7897
  ```
- 若远程操作因代理失败，先检查代理进程（如 Clash/`127.0.0.1:7897` 端口）是否在运行，再排查网络；不要绕过代理直连 GitHub。
- **默认不执行 `git push`**(见「编译产物上传规则」第 6 条);每次更新由代理自动完成本地提交,推送仅在用户明确要求时执行,且须经上述代理。

## 第三方模组源码核验规则（必须遵守）

核验任何**第三方模组**（Curios / KubeJS / Sodium / Iris / ModernFix / Patchouli 等）的**行为**时，必须以**该模组自己的 GitHub 源码**为准，并**自行前往其 GitHub 页面查找** —— 不要等用户提供，也不要凭记忆、凭缓存产物或凭反汇编下结论：

1. **取源**：先在该模组的 GitHub 仓库按版本定位 tag / commit（版本号取自本仓 `gradle.properties`、或目标 jar 内 `META-INF/mods.toml` / `neoforge.mods.toml` / `MANIFEST.MF`），再经上方 Git 代理克隆到**本模组仓库之外**，例如：
   `git -c http.proxy=http://127.0.0.1:7897 -c https.proxy=http://127.0.0.1:7897 clone https://github.com/TheIllusiveC4/Curios F:\MCProject\temp_curios\v9`
   克隆目录**不得**放进模组仓库（避免污染/误提交），也**不得** `git push`。
2. **不得替代**：Gradle 缓存里的编译产物（`javap` 反汇编）、**旧版本或其它 MC 版本的源码副本**都不能代替目标版本的源码。`javap` 只可用于确认方法签名/常量，**不得**据此推断逻辑。
3. **取不到就说取不到**：仓库不可达或没有对应 tag 时，取最近 tag 并**显式写出偏差**，把「未能验证」的部分标出来，不得用推测填空。
4. **结论形式**：每条结论附 `文件:行号 + 原文片段`，并写明**精确版本号**（如 `Curios 5.14.1+1.20.1` / `9.5.1+1.21.1`），使他人可独立复算。
5. **现有克隆（只读，可直接复用）**：`F:\MCProject\temp_curios\v5` = Curios **1.20.1 线**（`origin/1.20.x` @ `a91da6ba`，提交主题 `5.14.1`，`gradle.properties: version_mc=1.20.1`）；`F:\MCProject\temp_curios\v9` = Curios **1.21.1 线**（`origin/1.21.1` @ `6b122c8a`，提交主题 `9.5.1`，`gradle.properties: version=9.5.1+1.21.1`）；`F:\MCProject\astral_dice_multiloader\temp\curios_src` = Curios **26.1.2 分支**（另含本仓提交的上游补丁分支 `fix/26.1.2-loadinv-size`）。⚠️ 实测该仓库这些版本**没有打 tag**，克隆点落在**分支尖端**上 —— 引用时必须写成「分支 + commit + 提交主题/版本号」三者，不能只说 tag。Curios 仓库地址：<https://github.com/TheIllusiveC4/Curios>。
6. **现有克隆（只读，可直接复用）— 优化/渲染类**：Iris 仓库 <https://github.com/IrisShaders/Iris> ⇒ 克隆点 `F:\MCProject\temp_iris\26.1`（**目标版本 = 本仓 `run\26.1.2\mods` 内 `iris-neoforge-1.11.4+mc26.1.2.jar`**）；ImmediatelyFast 仓库 <https://github.com/RaphiMC/ImmediatelyFast> ⇒ 克隆点 `F:\MCProject\temp_immediatelyfast\26.1`（**已钉 tag `v1.15.3` / commit `c010c5d5`**，对应 `ImmediatelyFast-NeoForge-1.15.3+26.1.jar`）。引用时一律写「分支/tag + commit + 二进制版本」。这两处是为定位 **KNOWN-ISSUES §7 KI-D1**（26.1.2 + 光影的 `Missing sampler Sampler1` 崩溃）而取源，规则同第 1–4 条（克隆在仓库之外、经代理、按版本、结论带 `文件:行号 + 原文片段`）。⚠️ **主体已由 A/B 实测锁定为 Iris**（2026-09-19：IF 关 + 光影开仍崩且该组日志内 `ImmediatelyFast`/`BatchableBufferSource` 命中为 0；IF 关 + 光影关不崩）⇒ **IF 侧分析已降级为附录，不得作为崩溃成因引用**；克隆该仓库只用于「A 组栈里为何多一层批处理帧」这类旁证。

## 嵌套松散引用“消失”（Loose Ref Disappearance）— 结论已修订，见下

**现象**:`git` 刚写完 `.git/refs/heads/<含斜杠的分支名>/<引用>` 之后,该松散引用**连同其父目录**查不到;随后 `HEAD` 看起来无法解析,分支像“消失”。

> ⚠️ **2026-09-11 二次调查结论(以此为准)**:先前的“外部删除 / 内核过滤驱动 / 卡巴斯基”判断**不成立**,已作废。当天在卡巴斯基实时防护暂停、Defender 停用的条件下**无法复现任何删除**:在 `F:\`(仓库内/外)、`C:\`、`D:\`、`%TEMP%` 五处新建嵌套引用与普通嵌套目录,**全部 5/5 存活**(沙箱模式与被绕过模式均存活)。原“仅 `%TEMP%` 豁免”的现象,是 **WorkBuddy 自身沙箱的路径白名单**(见下)造成的,并非外部清理程序。

**已证实的两个“伪删除”来源(足以解释全部历史症状)**:

| 机制 | 实测 |
|---|---|
| **`git pack-refs`(由 `git gc --auto` 触发)会删除松散引用文件并顺带移除其空父目录**,引用改由 `.git/packed-refs` 承载 | 本机已复现:`pack-refs` 后 `refs/heads/nested/deep` 文件与 `nested/` 目录双双消失,但 `git rev-parse` 仍可解析 → **裸 `ls .git/refs/heads/<a>/` 报 “No such file or directory” 不代表引用丢失** |
| 父目录不存在时,`echo "$sha" > .git/refs/heads/<a>/<b>` 直接失败(`No such file or directory`) | 2026-09-10 23:09 会话日志原文即为此失败,**并非写入后被删** |

**其它证据**:
- 被“删”文件出现在回收站(`$RECYCLE.BIN`)、且删除语义为用户态——是本机 **WorkBuddy 沙箱自带 `modify_backup` 机制**(`reason=a/m/d` 快照,存于 `~/.workbuddy/workspace/sessions/<sid>/modify_backup/`,配置 `recyclebin_backup: true`)。卡巴斯基/Defender 均已排除。
- WorkBuddy 沙箱策略 `tsbx_rules.json`:`default_action=deny_write`,白名单含 `%LOCALAPPDATA%\Temp\**`、`**\$RECYCLE.BIN\**`、各语言缓存目录 —— 解释了“只有 `%TEMP%` 存活”。
- 与 git 版本、`core.fsync*`、reftable 均无关(先前“reftable 可规避”的结论不作数)。

**判定引用是否真的丢了的正确姿势(禁止再用 `ls`)**:用 `git rev-parse --verify HEAD`、`git show-ref`、`git branch`**三重确认**;只要 `git rev-parse` 能解析,引用就是好的(可能在 `packed-refs`)。

**保留的廉价保险(修复脚本本身随仓库入库;仅 `.git/hooks/*` 天然不入库)**:
- `scripts/maintenance/repair-loose-refs.ps1` — 以**幸存的 reflog** 为准补回真正丢失的松散引用;仍可解析的引用会跳过;git 有意删分支时 reflog 一并删除,故不会误复活。⚠️ **作用范围仅限 `refs/heads/*`**(2026-09-11 修正):早先版本会扫描 `logs/refs` 全部条目,导致 `git fetch --prune` 清理掉的陈旧 `refs/remotes/*` 被从 reflog 复活(产生幻影分支、抵消 `--prune`)。远端跟踪引用一律交给 `git fetch` 管理,脚本不得触碰。
- `.git/hooks/{post-commit,post-checkout,post-merge,post-rewrite}` — 提交/切换后自动调用该脚本(已端到端验证)。

**处置流程**:
1. 提交/切换分支后复核用 `git rev-parse --verify HEAD`(不要只看松散文件是否存在)。
2. **确实**丢失时:**不要**重做提交、不要 `git reset`——先跑 `pwsh -NoProfile -File scripts/maintenance/repair-loose-refs.ps1`。
3. 应急手工恢复:`mkdir -p .git/refs/heads/<a>`(目录必须先存在!)再从 `.git/logs/refs/heads/<a>/<b>` 末行取新值写入。
4. 可选:长期工作分支避免名称含 `/`(如 `multi-1.20.1-1.21.1`),减少“空目录被清理”造成的误判。

> ✅ **根治已实施(2026-09-11)**:工作分支已从 `multi-1.20.1/1.21.1` 改名为 **`multi-1.20.1-1.21.1`**(无斜杠),本地松散引用改为单段文件、不再有嵌套父目录,从源头消除“空父目录被清理 + 引用丢失”。4 个自愈钩子(`post-commit/post-checkout/post-merge/post-rewrite`)内的 `sh "$s"` 已改为绝对路径 **`"C:/Program Files/Git/bin/sh.exe" "$s"`**(此前 `sh` 在沙箱 PATH 里不可用导致钩子报 `sh: command not found`、自愈失效)。今后默认在此无斜杠分支上工作;远端旧分支 `origin/multi-1.20.1/1.21.1` 为 GitHub 默认分支,删除需先在网页把默认分支改到新名(本机无 token/gh 无法代劳)。

## Gradle 构建守护规则(Gradlew Watchdog)— 必须遵守

本项目 Gradle 存在已知 bug:**任务执行完成后守护进程不自行退出**(构建已结束但 gradlew 挂起)。代理执行构建时必须遵守:

1. `gradlew` 命令一律后台运行,并设置 **60 秒超时**。
2. 等待期内正常结束的,按退出码/输出判定结果。
3. **超过 60 秒未退出**:立即强制终止整个 Gradle 进程树(`taskkill /T /F` 或 `./gradlew --stop`),随后**必须验证构建结果**:
   - 检查日志(输出/日志文件)中是否存在 `BUILD SUCCESS` / `BUILD FAILED` / `error:` 字样;
   - 检查产物是否生成/更新(如 `*/build/libs/*.jar`、`*/build/classes` 的时间戳);
   - 日志有 `BUILD SUCCESS` 或产物已按预期生成 → 视为通过;否则判为失败并排查报错。
4. 禁止因挂起而无限等待;也禁止在未完成「日志 + 产物」双重验证的情况下把超时直接判为失败或通过。
5. 推荐使用本地脚本 `scripts/test/mt_build.ps1` 执行(已内置超时强退与「日志 + 产物」双重验证):
   `pwsh -NoProfile -File scripts/test/mt_build.ps1 --version 1.21.1 [--timeout 60] [--retries 3]`。
   该脚本同时是「自动化测试流程」阶段 B 的实现；单独构建时也可直接调用。
6. 常用构建入口(在仓库根目录执行;**三条线规则完全一致**,26.1.2 与另两线同规则、同产物去向):
   - `./gradlew :neoforge-1.21.1:build` — 仅构建 1.21.1 NeoForge;
   - `./gradlew :forge-1.20.1:build` — 仅构建 1.20.1 Forge;
   - `./gradlew :neoforge-26.1.2:build` — 仅构建 26.1.2 NeoForge;
   - `./gradlew build` — 三个子项目全部构建(部署任务随各子项目 build 触发)。
7. **数据生成(runData)前必须移开 run 目录中的旧产物 jar**:`run/1.21.1/mods/astral_dice-*.jar`(forge 同理 `run/1.20.1/mods/`,26.1.2 同理 `run/26.1.2/mods/`)会与 `build/classes/java/main` 同时被加载,**遮蔽刚编译的 dev 类**,导致 `runData` 用旧代码生成资源却**不报任何错误**(表现为"改了 Provider 但生成结果没变")。执行 `:neoforge-1.21.1:runData` / `:forge-1.20.1:runData`(26.1.2 为**两段式**:`:neoforge-26.1.2:runClientData :neoforge-26.1.2:runServerData`,且 `run/26.1.2/mods` 里的探针运行时 KubeJS/Rhino/BAT 也要一并移出,见下方 26.1.2 速记第 6 条)前先把该 jar 移出(如 `temp/shadow_jars/`),生成后再 `gradlew build` 重新推回。判定是否被遮蔽:比对 `build/classes/.../ModRecipeProvider.class` 中是否含新增字面量(如新筹码模式串),同时确认生成文件时间戳已更新(不要只看日志的 `written: N`)。
   - **`run/<版本>/mods` 里的 jar 是 dev/未重混淆产物,与 `build/libs` 的发货 jar 不同字节但**是同一程序**(2026-09-15 实测取证;禁止改动产物来源)**:1.20.1 侧 `build/libs/astral_dice-*+forge_1.20.1.jar` 是 **reobf(SRG 名,如 `m_82127_`)** 产物,而 `run/1.20.1/mods/` 里那份是 **dev/未 reobf(Mojmap 名,如 `literal`/`players`)** 产物(与 `forge-1.20.1/build/devlibs/` 那份**哈希完全相同**)。两者条目清单 1118:1118**零增删**、`META-INF/mods.toml` 与 `MANIFEST.MF` 逐字节相同;266 个 class 虽全部字节不同,但按方法切分并把分支目标/异常表偏移换算为指令序号后 **266/266 控制流+操作数完全等价**,差异仅为 173 处 `ldc`↔`ldc_w` 宽度与由此的偏移重编号。**这是有意设计**:`forge-1.20.1/build.gradle` 的 `pushToDevRun` 明确推 dev jar(SRG 版会让 `runClient` 报 `NoSuchFieldError(f_256929_)` **直接崩**)⇒ **禁止**把该任务的产物来源改成 reobfJar。**1.21.1 无 reobf 环节**,只有一个 `jar` 产物,故三处同哈希。**两个整合包目标与 CI 均正确命中 reobfJar**,发货产物不受影响。⚠️ **测试可信度**:`runClient` 实测**确定性加载 `run/<版本>/mods` 的 jar 并遮蔽 `build/classes`**(FML `UniqueModListBuilder` 只按版本号排序,版本相同则靠 locator 顺序,mods 目录先于 exploded dir)⇒ 游戏内测试跑的是 **dev jar 而非发货字节码**(因已证同程序,故 B1/B2 结论有效);**但「只 compileJava/classes 而未重新 jar 就跑 runClient」会跑旧代码且不报错** —— 与本规则第 7 条是同一类陷阱。取证全文见本地文档 `docs/batch3/forge-run-jar-investigation.md`。
8. **`fileHashes.lock` 拒绝访问(守护进程占锁)**:构建报 `Could not create service of type FileHasher ... .gradle/<ver>/fileHashes/fileHashes.lock (拒绝访问)` 并非编译错误,而是**上一个 Gradle 守护进程仍占锁**(日志首行常见 `1 busy and N stopped Daemons`;`./gradlew --stop` 可能停不掉 busy 守护进程)。处置:列出 java 进程,只终止 `gradlew` wrapper(`-Dorg.gradle.appname=gradlew`)与 Gradle daemon(`--add-opens=java.base/...`)两类,**绝不可误杀 Minecraft 客户端**(`net.minecraft.client.main.Main`)或用户其它 Java 程序,然后重跑构建。

9. **「构建很快、命令却不返回」≠ 构建慢（2026-09-17 实测取证；**禁止**据此提高超时）**：若日志已出现 BUILD SUCCESSFUL / MT_BUILD: OK (Ns) 而调用方仍在等，那是**进程收尾阻塞** —— `Start-Process -NoNewWindow` 会让 `cmd → gradlew → Gradle 守护进程 / 游戏客户端` 与父 pwsh **共用同一个控制台**，长驻子进程持有它 ⇒ 父 pwsh 执行完脚本 `exit` 后阻塞在**控制台拆卸**（取证：脚本已打印 `MT_BUILD: OK (4s)`、父进程 CPU 仅 0.44s 却存活 20+ 分钟，子进程里挂着一个 `conhost.exe`）。已修：`lib/Mt.Proc.psm1` 的 `Start-MtProcessToFile` 两个分支改用 **`-WindowStyle Hidden`**（新建隐藏控制台），日志落盘口径不变。**判据**：同一条管道调用 `mt_build` 修复后 **1.6s** 返回（修复前 90s 超时 / 20+ 分钟）。**调用方约定**：长驻阶段（build / launch）不要用 `| Select-Object` 之类管道捕获输出，改 `*> 文件`（见「全局测试规则」第 4 条）。
## 多版本子项目矩阵(Multi-Version Subproject Matrix)— 必须遵守

本仓库以**子项目承载版本**;迁移背景与目录结构见 `docs/multiloader-layout.md`,两子项目的完整 API 差异见 `docs/compat-1.20.1-forge.md`。

| 子项目 | 来源分支(原 astra_dice 仓库) | MC | 加载器 | Java | 当前版本 | 版本号格式 |
|---|---|---|---|---|---|---|
| `neoforge-1.21.1` | `1.21.1-main` | 1.21.1 | NeoForge | 21 | `1.2.1-hotfix+neoforge_1.21.1` | `x.y.z[-rcN|hotfix]+neoforge_1.21.1` |
| `forge-1.20.1` | `1.20.1-forge` | 1.20.1 | Forge | 17 | `1.2.1-hotfix+forge_1.20.1` | `x.y.z[-rcN|preN|hotfix]+forge_1.20.1` |
| `neoforge-26.1.2` | 本仓 `multi-26.1.2-neoforge` 分支新增（基线 = 主线 `1.2.1`/`fda8ca9` 的 `neoforge-1.21.1` 源码）；**2026-09-17 已合并进 `multi-1.20.1-1.21.1`**（与主线同目录同树，原独立 worktree 已移除） | 26.1.2 | NeoForge | 25 | `1.2.1-beta.2+neoforge_26.1.2` | `x.y.z[-rcN]+neoforge_26.1.2` |

> 版本号各 git 分支独立（AGENTS.md 自 2026-09-15 起**已纳入版本库**，各分支各自维护一份）：`multi-1.20.1-1.21.1` 当前 = `1.2.1-hotfix`（`1.2.1` 的修补版，2026-09-18 筹码栏修复；按发布规范 tag 仍解析为裸版本 **`1.2.1`**，故 Release 1.2.1 为**刷新**而非新建）；`multi-dev-next` 当前 = **`2.0.0-SNAPSHOT.10`**（2026-09-17 用户裁决：`2.0.0-SNAPSHOT.5` 封包，版本号升至 `.10`；后续改动一律记入两个 CHANGELOG 顶部的 `未发布（2.0.0-SNAPSHOT.10）` 小节）；`neoforge-26.1.2` 子项目当前 = **`1.2.1-beta.2`**（2026-09-17 用户裁决 + **2026-09-19 修订：26.1.2 已纳入主线、三线同步（不再是低优先级线）**，版本号仍单独加 `-beta` 与主线 `1.2.1` 的发布态区分；2026-09-18 随同批修复升为 `-beta.2`；`multi-26.1.2-neoforge` 分支自此只作为合并前历史，不再单独开发）。上表「当前版本」以主线工作分支 `multi-1.20.1-1.21.1` 为准。
> ⚠️ 历史上另有一条 dev 分支 **`wt/2.0.0-vnext`**（连带独立 worktree `C:/Users/xmace/.dsh/worktrees/astral_dice_multiloader-a03b2df2/2.0.0-vnext`）——2026-09-17 用户裁决「移除 wt/2.0.0-vnext 分支，仅保留当前分支」后**已删除**：worktree 与分支一并移除，`git branch -d` 成功即证明其 tip **`d7e4ac8f4f1c31484bf4366caa4e144aec45979f`** 的全部提交都已被 `multi-dev-next` 包含（`multi-dev-next..wt/2.0.0-vnext` 为空）⇒ **未丢失任何提交**；该分支从未推到远端（`origin` 只有 `multi-1.20.1-1.21.1` 与 `multi-dev-next`），故无需远端清理。`multi-26.1.2-neoforge` 作为合并前历史分支**保留**（未在本次裁决范围内）。

> **第三条线(26.1.2)的规则边界(2026-09-19 用户裁决修订 —— 26.1.2 已纳入主线,必须遵守)**:自本裁决起「同步修改」约束**三个版本**(`neoforge-1.21.1` + `forge-1.20.1` + `neoforge-26.1.2`):任何功能/修复/平衡/文案改动一律**三线同批实施**(实施方式见下方「### 子项目修改默认规则」与「### 模组内容更新规则(三线同步)」),26.1.2 **不再**是「发布线完成后再迁移」的低优先级移植线。三条线各自按 `docs/compat-26.1.2-neoforge.md`(26.1.2 相对 1.21.1)、`docs/compat-1.20.1-forge.md`(1.20.1 相对 1.21.1)的差异映射实现,**平台差异必须逐条登记**;三线落地后按 `scripts/test/TESTING-SPEC.md` §13.2 做一致性测试。三子项目的 `mod_version`/`mods.toml` 门槛仍各自独立。⛔ **2026-09-19 补充裁决：暂停 26.1.2 版本更新**（原话「暂停26.1.2版本更新」）—— 本段的「三线同批实施」对 `neoforge-26.1.2` **暂不适用**，该线**冻结在已提交状态 `127fabd5`**，不得再落地任何改动（只读取证不受限），恢复需用户明确指示；详见下方「### 模组内容更新规则(三线同步)」段首的裁决记录。

### neoforge-26.1.2 关键差异速记(相对 neoforge-1.21.1)

完整清单见 `docs/compat-26.1.2-neoforge.md`;以下 5 条是**踩过坑、必须照做**的硬约束:

1. **物品注册必须走 `registerItem(name, props -> new XxxItem(props…))`(2026-09-16 实测)**:26.1.2 起 `Item` 构造器经 `Item.Properties#itemIdOrThrow` 推导默认描述 id,而 `DeferredRegister.Items#register(String, Supplier)` **不注入 id** ⇒ 旧写法(`ITEMS.register(name, () -> new X(new Item.Properties()…))`)会在注册阶段直接
   `NullPointerException: Item id not set` 让模组加载失败。属性链一律挂在传入的 `props` 上;也**不要**用 `XxxItem::new` 方法引用形式(构造器带额外参数时 `p -> …` 链会被逗号截断)。
2. **数据生成是两段式,且两条运行的 `--output` 必须不同(2026-09-16 实测)**:26.1.2 **没有** `data` 运行类型,只有 `runClientData`(`GatherDataEvent.Client` → `assets/` 物品模型)与 `runServerData`(`GatherDataEvent.Server` → `data/` 配方/进度)。原版 `HashCache#purgeStaleAndWrite()` 会**删除输出根下不属于本次运行 provider 的一切文件**,故共用输出目录时**后跑的那次会清空前一次的全部产物**(实测 246 个物品模型 / 217 个 data 文件被互删)。本仓库因此用**双输出根**:`src/generated/resources`(server)+ `src/generated/clientResources`(client),两者都作为 `sourceSets.main.resources.srcDir`。改动资源后必须
   `gradlew :neoforge-26.1.2:runClientData :neoforge-26.1.2:runServerData` **两个任务一起跑**,且**禁止**把两者指向同一 `--output`。
3. **`GatherDataEvent` 在 26.1.2 是抽象类**:监听器必须注册在 `GatherDataEvent.Client` / `GatherDataEvent.Server` 上;注册到抽象父类会让模组构造期直接失败(`Cannot register listeners for abstract class …`)。客户端 provider(继承原版 `net.minecraft.client.data.models.ModelProvider`)与服务端 provider **必须分属两个类**,客户端那个还要加 `@EventBusSubscriber(value = Dist.CLIENT, …)`(服务端数据生成运行的 classpath 不含客户端类)。
4. **`@EventBusSubscriber` 只剩 `value()`/`modid()`**(`bus = Bus.MOD` 已删除);`ExistingFileHelper`、NeoForge 的 `client.model.generators.ItemModelProvider`、Parchment 的 26.1.x 数据**均不存在**;原版 `ModelProvider` 对重复登记**抛异常**(1.21.1 侧 `ModItemModelProvider` 里 `STAR_COIN` 重复登记在旧 API 下被静默覆盖,26.1.2 已删重复行)。
5. **Mixin 目标字符串不受编译器保护**:每次换 26.1.2.x 小版本都要按 `docs/compat-26.1.2-neoforge.md` §3 逐条对目标版本源码复核(已发现 `PiglinAi#isWearingGold→isWearingSafeArmor`、`Gui#renderEffects→extractEffects`、`EffectRenderingInventoryScreen→EffectsInInventory`;`MerchantOffer` 复制构造仍在,无需改)。该线**已删除** NeoForge 伤害容器泄漏修复 mixin(26.1.2 上游已修),该线**无 Iron's Spells 'n Spellbooks 联动**(上游无 26.1.x 构建)。
6. **26.1.2 的游戏内验证必须走本仓库 `scripts/test` 工具链,且已按 26.1.2 适配(2026-09-17 实跑 PASS)**:`computer_control` 的合成点击**进不去 GLFW 窗口**(实测主菜单点不动),键鼠注入只能用 `mt_inject`。四条已固化的事实:
   - **就绪判据**:26.1.2 **没有** `Total time to load game and open world was`;`mt_launch` 改用 `logged in with entity id` **且** `Loaded <N> advancements`(都取英文原版行,禁用本地化文案如「加入了游戏」)。
   - **探针运行时装装**:26.1.2 整合包**没有** KubeJS,由 `mt_env.ps1 mods --version 26.1.2` 从 KubeJS maven 拉 `kubejs-neoforge`(版本取 `gradle.properties:kubejs_version`,注意 maven 版本**不带** `+neoforge` 后缀)+ `rhino` + `better-advanced-tooltips`(后者是**硬前置**,即使只跑服务端;缺它 runServer 在 RegisterEvent 阶段 `NoClassDefFoundError`),缓存于 `temp/probe_mods/26.1.2/`;**`tiny-java-server` 是纯 Java 库,禁止放进 `run/mods`**(FML 会弹「不是有效的模组文件」并停在警告屏)。**不**把 KubeJS 写进 build.gradle 依赖(否则 datagen 也会装载它)。
   - **探针 API 形态**(详见 `compat-26.1.2-neoforge.md` §7.2):权限用 `permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)`;`Level#dimension` 在 Rhino 下是**属性**;**禁止**用 Curios `findFirstCurio(...)`(KubeJS 8 的 `ItemWrapper` 会按 `findFirstCurio(Item)` 重载解析并抛 `KubeRuntimeException`,**连 `guard` 都捕不到** ⇒ 读数全无)。
   - **Brigadier 的 `executes` 处理器必须返回 `int`(2026-09-17 实测)**:26.1.2 的 `Commands#performPrefixedCommand` 返回 **void**(1.21.1 返回 int),移植后的探针用 `execP()` 返回 `"rc=ok"`/`"ERR:…"` 字符串 ⇒ 凡是把该字符串 `return` 给 Brigadier 的处理器(如 `dumpState` 末尾的 `return rc;`)都会抛
     `dev.latvian.mods.rhino.EvaluatorException: Cannot convert rc=ok to int`,**该异常同样逃出 `guard`/`try-catch`**,日志里只有 Brigadier 一行 `threw an exception`,表现为该子命令**后续读数整段缺失**(实测 `APDUMP|LOCKRAW|` 永不出现)。移植时凡「命令体末尾 return 读数变量」都要改成 `return 1;`,读数只用于消息文本。
   - **`runData` 前必须移出 `run/26.1.2/mods` 里的探针运行时**(KubeJS/Rhino/BAT):它们会与 `build/classes` 同载并干扰两段式数据生成,与「移出旧产物 jar」是同一类陷阱。
   - **优化类模组（ImmediatelyFast + ModernFix，2026-09-17 用户要求「进一步验证优化类模组兼容性」）**：由 `mt_env.ps1 mods --version 26.1.2` 从 Modrinth Maven 装 `immediatelyfast:adbrNJLm`（**1.15.3+26.1-neoforge**，纯客户端）与 `modernfix:j7EoxpYe`（**5.27.22+mc26.1.2**，两侧皆可），缓存 `temp/probe_mods/26.1.2/`。⚠️ **侧别不同、处理必须分开**：ImmediatelyFast 是 `client_only` ⇒ 已加入 `Invoke-MtEnvWorld` 的「纯客户端模组移出」名单（子串 `immediatelyfast`），生成世界后自动恢复（实测无 `.disabled` 残留）；ModernFix 两侧皆可 ⇒ 生成世界时保留（服务端也拿到启动期优化）。`mt_launch` 回显 `IMMEDIATELYFAST_LOADED` / `MODERNFIX_LOADED`（判据同样取**已加载列表行**里的括号 modId `(immediatelyfast)`/`(modernfix)`，避免把存档里的 `… -> MISSING` 误判成已加载）；这两条**不**做硬失败（与 Sodium/Iris 一致），但缺装载会给 WARN，避免「兼容性验证」静默地什么都没验证。**实测（2026-09-17，两个模组均已装载）**：`MIGRATION-SMOKE` 27/27、`PORTED-PROBE-SMOKE` 41/41、`CRAFT-SMOKE` 21/21、`SHADER-VISION` 11/11 **全 PASS**，无新增崩溃报告 ⇒ 与本模组、Sodium 0.9.1、Iris 1.11.4、Complementary 光影**无冲突**。⚠️ 换版本时注意 `Install-MtSpecList` 的**按族旧版本清理**（前缀必须按调用传入：`sodium-`/`iris-`、`immediatelyfast-`/`modernfix-`、`superflatworldnoslimes-`/`collective-`）——用一张全局前缀表会让「装史莱姆压制」那趟把刚装好的 Sodium/Iris 删掉（实测踩坑）。
   - **【测试环境硬性要求】必须装「Superflat World No Slimes」**(2026-09-17 用户要求,原话「否则因为超平坦世界生成的史莱姆会严重干扰测试流程」):测试世界是超平坦的,玩家常驻 y≈-60 且难度 EASY ⇒ y<40 的史莱姆区块持续刷怪,实测**同一位置 128 格内 103 只史莱姆**(mobs 总数 115);装上该模组后同样条件 **slimes=0**(mobs 14,EASY)——即压制的是史莱姆而**不是**整体刷怪,故不能用「关掉刷怪」代替。由 `mt_env.ps1 mods --version 26.1.2` 从 Modrinth Maven 装 `superflat-world-no-slimes`(`Onb8latt`,26.1.2-3.6)**及其 required 前置** `collective`(`iXqgYZEw`,26.1.2-8.32),缓存 `temp/probe_mods/26.1.2/`,与渲染栈同一套「尺寸 + sha1」幂等校验;两者都是**服务端/运行期**模组 ⇒ `mt_env world` 的「纯客户端模组移出」名单**不得**包含它们(文件名不含 imblocker/sodium/iris/embeddium/oculus 子串,天然安全)。`mt_launch` 在进入世界后做**硬闸门**:日志里没有已加载列表行 `(superflatworldnoslimes)` 即 `MT_LAUNCH: ERROR` 并拒绝继续(确需「无该模组」的对照实验时设 `MT_ALLOW_NO_SLIMEGUARD=1`,会留 WARN)。⚠️ **判据必须匹配带括号的 modId**:存档 `level.dat` 记着上次带这些模组跑过,缺失时 NeoForge 会打印 `<modId> (version X -> MISSING)`,只搜名字会把「缺失」误判成「已加载」(实测踩坑,那次把对照实验误报成 `SLIMEGUARD_LOADED=true`)。取证命令:`/astralprobe slimecheck <tag>` → `AP_<tag>_SLIME:slimes=n:mobs=m:radius=128:difficulty=…:y=…`(与 mobs/难度一并报出,避免「peaceful 或整体不刷怪」造成的假阴性)。
   - **玩家 bot（Carpet: NeoForged，2026-09-27 用户要求）**：单人测试世界此前**没有第二名真实玩家**（teru 降神只能用 FakePlayer 或「自身脚手架」）⇒ 引入 Carpet 的 `/player <name> spawn` 造**真 ServerPlayer** bot（进玩家列表、可被选为目标、可被 `/kill` 真实击杀）。装配**两条线机制不同**：1.21.1 由 `mt_env` 从 Modrinth Maven 装进 `run/1.21.1/mods`；**1.20.1 由 `forge-1.20.1/build.gradle` 的 `modImplementation` 提供**（生产 SRG jar + SRG refmap 必须经 MDG 重映射，手工放 run/mods 会让 mixin 静默失效）；**26.1.2 不装**（用户裁决：该线用 Mojang 自带 bot 管理指令，后续再学）。`mt_launch` 对两条发布线做 **CARPET_LOADED 硬闸门**（`MT_ALLOW_NO_CARPET=1` 可放行对照实验）。探针命令（`terubot/teruequipbot/terublessreal/terucastreal/terureadreal/teruendreal/teruhitreal`）、用例 `scripts/test/cases/BOT-2P-<版本>.json` 与 6 条实测踩坑见文末《Carpet: NeoForged（玩家 bot / 双人联动测试）》段。
   - **【测试环境硬性要求】必须禁用生物 AI**（2026-09-18 用户裁决，原话「测试流程未禁用生物AI,这是严重失误。立即重新测试」）：此前工具链只有「进入世界后清场一次（`/kill @e[type=!player,distance=..128]`）＋ 探针靶子自设 `setNoAi(true)`」，**自然刷新的生物仍带 AI** —— 会主动接近/攻击/推挤玩家、投掷弹射物、踩压力板、引爆苦力怕，污染「世界级差值」读数（`self` 施放者 HP、`bolt_delta` 全局雷击计数、实体计数）甚至把玩家打死，属**流程缺陷**而非被测行为。落地：`scripts/test/resources/kubejs/<版本>/server_scripts/astral_test_noai.js`（由 `mt_env.ps1 kubejs` 同步到 `run/<版本>/kubejs/server_scripts/`，**改动需冷启动才生效**）—— 每 2 tick 横扫每个玩家周围 128 格内的 Mob 强制 `setNoAi(true)`（按 UUID 去重、幂等），并在 `EntityEvents.spawned` 上即时生效（新刷生物第一次行动前就被停住）。`mt_launch` 进入世界后做**硬闸门**：30 秒内须读到 `AP_NOAI:` 行且 `mobs == noai`，否则 `MT_LAUNCH: ERROR` 拒绝继续（确需带 AI 的对照实验时设 `MT_ALLOW_MOB_AI=1`，会留 WARN）。⚠️ **本闸门只禁 AI、不禁刷怪**：史莱姆由上一条的模组负责，**禁止**用 `/gamerule doMobSpawning false` 代替 —— 那会让 `/astralprobe slimecheck` 的对照读数恒为 0，把「刷怪压制」硬闸门变成假证。
     ⚠️ **【2026-09-19 实测缺陷：该闸门当前空转，绿灯不等于「AI 已禁用」】** 判据 `mobs == noai` 在 `mobs = 0` 时**恒真**，而实测该脚本的实体枚举**恒返回 0**：同一会话、同一半径下 `/astralprobe slimecheck` 读到 `mobs=83`，且在玩家 **20 格外**留住一只被标记的僵尸时心跳仍报 `mobs=0`（排除「半径算小一半」解释）；脚本本身零报错，成因疑为 `AABB.ofSize(p.position(), R, R, R)` 的口径与「异常被 `catch` 吞掉后静默 `continue`」（查询失败与「真没 Mob」不可区分）⇒ 正面对照行 `AP_NOAI_FORCED:` **从不出现**。**故引用该闸门绿灯时必须同时给出独立证据**：可靠判据 = **行为学** —— 召唤一只 Mob 后用原版 `/data get entity @e[tag=…,limit=1] Pos` 间隔读两次，**AI 已禁用时坐标逐字不变**（2026-09-19 实测：`[20.5,-60,0.5]` 的僵尸 25 秒内两次读数完全相同）。修法建议（枚举改用探针已验证的 `p.level.getEntitiesOfClass(MobClass, p.getBoundingBox().inflate(R))`；`catch` 改为吐 `AP_NOAI:ERR:…`；闸门要求窗口内至少一次 `mobs > 0` 采样）见 `scripts/test/TESTING-SPEC.md` 附录 A 同日条目。
7. **26.1.2 的 `items/*.json` 物品模型定义是硬需求**:1.21.4+ 起物品必须有模型定义文件 `assets/<ns>/items/<id>.json`(datagen 生成的 123 个由 `ModItemModelProvider` 负责);**非 `ModItems` 物品**(如帕秋莉手册 `astral_guide`)必须**手工**补一份 —— 缺失时客户端只在日志里留一行 `Missing item model for location …`,游戏照常运行,属「不崩但玩家可见」的静默回归(2026-09-17 实跑发现并修复)。
8. **Curios 15 的饰品栏尺寸只能经「槽位修饰符 → `update()`」改写,禁止直接 `getStacks().grow/shrink`(2026-09-17 实测崩服,必须遵守)**:1.21.1 用的是 `ICurioStacksHandler#grow/shrink`(由 `CurioStacksHandler` **同时**调整 `stacks` 与 `cosmeticStacks`);Curios 15 已把这两个方法从接口移除(只留在 `IDynamicStackHandler` 上),移植时若图省事写成
   `handler.getStacks().grow(n)`,**只改 `stackHandler`**,而 Curios 自身 tick 循环(`CuriosCommonEvents`,行 599)以 `getSlots()`(= `stackHandler` 尺寸)为循环上界、却**无保护地**读 `getCosmeticStacks().getStackInSlot(i)` ⇒ 两者不等时玩家每 tick 抛 `Slot 0 not in valid range - [0,0)` 并**崩服**(实测:装备 2★ 骰子使筹码栏 0→2 后立即崩);同一失配在**每次登录**还会让 `CurioInventory.loadInventoryConfiguration`(行 161/162,同样是 `getSlots()` 上界 + 读 cosmetic)抛同错,表现为 `Couldn't place player in world`、玩家弹回主菜单(工具链看着像 `MT_preflight-op: ERROR` 卡住),且**该存档此后每次登录都崩**——修代码救不回来,只能删 `saves/<world>/players/data/<uuid>.dat(.dat_old)`(26.1.2 玩家数据在 `players/data/`,不是旧版 `playerdata/`;地形与 `level.dat` 不动)。
   正确写法(`DiceCurioItem#applySlotCount`):`removeModifier(id)` → **`addPermanentModifier`**(new `AttributeModifier(id, target, Operation.ADD_VALUE)`;它内部先 `addTransientModifier`(会 `flagUpdate()`)再登记到 `persistentModifiers`)→ `handler.update()`(`CuriosCommonEvents` 同级的 `CurioStacksHandler#update()` 会按 `baseSize + Σ ADD_VALUE` 重算并调用私有 `resize()`,`resize()` 同步 `stacks`/`cosmeticStacks`/`renderHandler`/`activeStates`);**归零时也要保留 0 值修饰符**(移除后无修饰符可 `flagUpdate`,尺寸会停在旧值)。`getSlots()` 的取值只由 `baseSize + 修饰符` 决定,故**直接改 NBT/直接 grow 都会被下一次 `update()` 抹掉或造成失配**。用 permanent 而非 transient 的理由:存档里带 `PermanentModifiers`(`CurioStacksHandler#deserialize` 逐条 `addPermanentModifier`),登录第一刻槽数即目标值,不必等 `curioTick` 20 tick 补回、也不让登录迁移看到新旧槽数不一致。
   ⚠️ **【已修复(2026-09-17,用户裁决采用 ①自管迁移 + ③上游补丁)】26.1.2 上筹码在重登后被移出栏位 = Curios 15 的登录迁移只搬「数据包原始尺寸」内的槽位,而本模组 chip 槽的原始尺寸是 0**(2026-09-17 取得源码级 + 实测双证据;源码 = Curios 26.1.2 分支 8f2f132(15.0.0),已 clone 到本地 temp/curios_src):
   - **机制**:`CurioInventory#loadInventoryConfiguration()`(每次进入世界由 `OnDatapackSyncEvent` 触发)先 `createDefaultInventory()` 造一批**新** `CurioStacksHandler`,再逐槽搬移旧内容;搬移循环的上界是 `curioStacksHandler.getSlots()`,而 `CurioStacksHandler#getSlots()` → `update()` 首行是 `if (this.dataLoaded)`,且 `setDataLoaded()` 只在**整个方法末尾**(行 206)才调用 ⇒ 循环期间 `getSlots()` 返回的是**构造函数里那个原始 `stackHandler` 尺寸**(= 数据包 base size),与刚 `copyModifiers()` 复制过来的修饰符**无关**。本模组 chip 槽数据包写死 `"size": 0`(`SlotType.Builder` 只在 size **缺省**时才默认 1,显式 0 就是 0),尺寸完全由骰子的 permanent 修饰符给出 ⇒ 循环上界 `min(0, 旧尺寸)` = **0**,**一次都不搬**;紧接的 `while (index < 旧尺寸)` 把槽内物品全部塞进 `invalidStacks`(行 160-164),再由 `CurioInventoryCapability#handleInvalidStacks()` → `ItemHandlerHelper.giveItemToPlayer()` **交还玩家背包**。
   - **实测证据**(插桩 + 探针,均只读):`AP_CURIOTRACE||SYNC_BEFORE|…|chipSlots=2|chipBase=0|chipMods=[astral_dice:chip_slots=2.0:ADD_VALUE]|chipStacks=[0:astral_dice:flashlight_chipx1,…]` → 迁移期间**只出现 `slot=dice` 的 `CAN_EQUIP`,没有任何 `slot=chip` 的校验调用**(⇒ 搬运循环根本没执行,不是校验器/标签挡下;同一批证据里 `stackIsStackValid=true`、`inChipTag=true`、`itemSlotTypes=[chip,curio]`)→ `SYNC_AFTER|chipSlots=4|chipMods=[astral_dice:chip_slots=2.0,curios:size_shift=2.0]|chipStacks=[0:empty,1:empty,2:empty,3:empty]`,同时 `invChips=[2:astral_dice:flashlight_chipx1]`、`offhand=empty`、`groundChips=[]` ⇒ **物品既没掉地上也没被销毁,而是进了玩家背包 2 号位**(与「不丢」的旧结论一致;但旧结论「槽位数仍正确」是**错的**)。
   - **同源副作用(尺寸瞬变)**:`defaultSize` 在 `copyModifiers()` **之前**读取(行 121)⇒ `旧尺寸(2) != 数据包尺寸(0)` 又补一个 `curios:size_shift=+2`(行 125-129),登录瞬间筹码栏变 **4** 格;随后 Curios 自己的每 tick 清理 `CuriosCommonEvents:613 clearCachedSlotModifiers()`(删 `curios:size_shift`,`CurioInventoryCapability:636`)把它抹掉,几 tick 后回到 2(插桩 4→2 完整可见)⇒ 玩家在登录瞬间会看到筹码栏多两格。
   - **本模组侧还有一条独立弹出路径(本轮新发现)**:插桩记录到迁移当拍 `NOTE|applySlotCount|target=0`(迁移期间骰子槽内容短暂为空 ⇒ `DiceTierRegistry.get()` 为 null ⇒ 目标 0)⇒ `setSlotCount` 走「防御式收缩」把 chip 栏**缩到 0**;若此刻槽内有筹码,`resize() → loseStacks()` 会把它一并交给背包。⇒ 26.1.2 上「筹码掉出」有**两条**成因,修复必须一起处理。
   - **候选修法(已裁决:采用 ① 自管迁移 + ③ 上游补丁(2026-09-17,已落地))**:①**自管迁移(stash & restore,推荐)**——在 `OnDatapackSyncEvent` 的 `HIGHEST` 把 chip 槽内容快照并清空(Curios 迁移因此看不到任何待搬出物品),在 `LOWEST`(Curios 之后)按骰子重算尺寸再把快照放回,并给 `curioTick` 加「迁移窗口内不得收缩」保护(保持 base 0 的现有设计,且覆盖槽位 1..N);②**数据包 base 改 1**——最小改动,但 Curios 只搬 base 尺寸内的槽位 ⇒ 槽位 1..N 的筹码仍会回背包,且老存档 BaseSize=0 会引入 size_shift 补偿差;③**上游修复**——`loadInventoryConfiguration` 的搬移上界应取「复制修饰符后的尺寸」(或先 `setDataLoaded()`),本模组侧再用 ① 兜底。
   - 复现/取证全文见 `docs/compat-26.1.2-neoforge.md` §7.6 与用例 `CHIP-RELOG-A/B-26.1.2`(两条用例现已全绿,见下方「验证」);定位期用的只读插桩(已在定位完成后删除)见 `debug/CurioSlotTrace`(`AP_CURIOTRACE|` 前缀,开发环境默认开、生产默认关、`-Dastral_dice.curioTrace=true` 可强开;只读、不抛异常、不改任何状态);探针新增只读命令 `/astralprobe invdump <tag>`(报主物品栏 + 副手 + 地面掉落物里的筹码)。⚠️ 探针 `equipslot` 是**直接写栏位、绕过校验**,测筹码必须用 `curios:chip` 标签内的物品(用 `blank_chip` 这类材料会造出假缺陷)。
    - **①已实现(产品侧兜底)**:`event/ChipSlotMigrationHandler` —— `OnDatapackSyncEvent` **HIGHEST**(先于 Curios 的 NORMAL 处理器)把 chip 槽(功能槽 + 装饰槽)内容快照为 `ItemStack#copy()` 并**清空**(Curios 的搬移因此既看不到待搬出物品、也不会塞进 `invalidStacks`);**LOWEST**(晚于 Curios)先 `DiceCurioItem.refreshChipSlotCount(player)` 按当前骰子重算尺寸、再按**原索引**放回;放不下或不再通过校验的交还玩家背包,**绝不静默丢弃**;快照取在清空之前、恢复按索引覆盖写、残留快照在下次 HIGHEST 先交还玩家 ⇒ 不复制也不丢失;整段 try/catch(Throwable) 只记日志,绝不让登录失败。配套:`setSlotCount` 的非强制分支不再在「目标瞬时读成 0」时收缩,而是把目标**抬到「最靠后的非空槽位 + 1」**(根除第二条弹出路径,无需额外的迁移窗口标记)。
    - **③已实现(上游)**:`temp/curios_src` 分支 `fix/26.1.2-loadinv-size`(提交 `9704c4c`)——在 `copyModifiers()` 之后、读取 `defaultSize` 之前调用 `setDataLoaded()`,并把 `defaultSize` 的读取移到 `copyModifiers()` 之后(后者同时修掉 `size_shift` 与已复制修饰符的重复计算);补丁 `temp/curios-fix-26.1.2-loadinv-size.patch`,缺陷报告 + 推送/PR 命令见 `docs/upstream/curios-26.1.2-loadinventoryconfiguration.md`(本机无 token/gh,**未推送**)。
    - **验证(不含插桩的最终口径)**:`CHIP-RELOG-A-26.1.2`(21/21 PASS:装 2★ 骰子 → 0 号位 `flashlight_chip`、**1 号位 `cutter_chip`** → `readstate RB1` 断言 `chipSlots=2:chipCosmetic=2:chipItems=[0:…,1:…]` → `saveall`)→ `mt.ps1 --phase stop --force`(保留存档)→ `mt.ps1 --phase launch` → `CHIP-RELOG-B-26.1.2`(**10/10 PASS**:尺寸 2/2、0/1 号位筹码都在、无异常行、无新增崩溃报告)⇒ 两个槽位跨重登完整留存。插桩 `debug/CurioSlotTrace` 已在定位完成后按用户要求**移除**,判据全部改由用例断言给出;`readstate` 增补 `chipItems=[索引:id,…]`,`equipslotat` 支持往指定索引写入。
     - **该两阶段用例必须走分阶段入口(2026-09-18 实测踩坑)**:`--phase env` → `--phase launch` → `--phase cases --case …-A` → `--phase stop` → `--phase launch` → `--phase cases --case …-B`;**不得**用 `mt.ps1 --case CHIP-RELOG-A-…`(全流程)跑 A —— 全流程退出清理会带 `--purge-saves` 把刚 `saveall` 的存档清掉,随后的 `--phase launch` 直接 `MT_launch: BLOCKED — 测试世界不存在`。
     - **本线不做「值未变就早退」的尺寸写入优化(2026-09-18 实测,勿回退)**:1.21.1/1.20.1 侧的 `applySlotCount` 在「修饰符值未变且 `getSlots()` 已等于目标」时直接返回;本线**必须无条件写**(`removeModifier` → `addPermanentModifier` → `update()`)。原因:实测经 `/curios reset` 重建后的 chip handler 会进入「修饰符已写入(`mods=[astral_dice:chip_slots=2]`)但 `getStacks().getSlots()`/`getSlots()` 停在 0」的失配态(`adds` 与 `mods` 一致、`base=0`),此时只有再写一次(→ `flagUpdate()` → `update()` → `resize()`)才能拉回一致;早退会把失配**永久冻结**在该会话里。
     - **上游限制:Curios 15 上 `/curios reset` 之后该会话的槽位尺寸不再增长(2026-09-18 实测)**:复位后 `getSlots()` 恒 0、`putInSlot` 报 `slot_overflow:chip:0`,即使重装骰子并写入目标修饰符也不恢复(属上游 Curios 缺陷,非本模组缺陷;疑与 `CurioInventory#init()` 的重建路径有关)。⇒ **本线禁止用 `/curios reset` 做用例基线**(1.21.1/1.20.1 可以,发布线用例 `CHIP-EQUIP-1.21.1/-1.20.1` 正是用它确定化);需要干净基线时用「清空 dice 栏 + `--purge-saves` 后的全新世界」。探针 `/astralprobe chipreset` 仅保留作取证。
     - **本线的「装备即生效」判据是时序而非修饰符归属(2026-09-18)**:本线测试脚手架 `applyChipSlotCount` 与产品 `applySlotCount` **共用同一个修饰符 id**(`astral_dice:chip_slots`),故 `mods=[…]` 在 `onEquip` 参数误用修复前后相同、不具区分度。判据改用探针 `/astralprobe chiptiming <item> <tag>`:等 `tickCount % 20 == 1`(该 tick 的 `curioTick` 维护刚过、下一次在 19 tick 之后)才写 dice 栏,再在 +2 tick 与 +22 tick 各读一次 ⇒ 修复后 `AP_*_T2` 已是 2 格,修复前该相位下必为 0 格(用例 `CHIP-EQUIP-26.1.2`)。
9. **数据包格式随 26.1 变了三处(2026-09-17 实测,配方因此曾静默全灭)**:
   - **配方 ingredient 必须写成字符串**。26.1 起 vanilla ingredient = `"minecraft:dirt"`(物品)/`"#c:ingots"`(标签);带 `neoforge:ingredient_type` 的对象才表示**自定义** ingredient(见 NeoForge 26.1 文档 Resources/Server/Recipes/Ingredients)。1.21.1 的 `{"item":"X"}` / `{"tag":"T"}` 在 26.1 **解析失败** ⇒ 整份配方文件被丢弃、**只在日志留一行 `Couldn't parse data file …`**(不崩、不影响启动,极易漏)。本仓 13 份手写配方(12 张骰子升级 + 手册)已改写;datagen 侧(`ShapedRecipeBuilder`/`ShapelessRecipeBuilder`)本就产出字符串形式,**无需改**。**判据**:`RecipeManager#getRecipes()` 里 `astral_dice:` 命名空间的配方数必须等于磁盘上 `data/astral_dice/recipe/*.json` 的文件数(2026-09-19 复核并更新:**26.1.2 = 122** = 生成 **109** + 手写 **13**;对照 **1.21.1 = 123** = 生成 109 + 手写 14,差的这 1 份是**已按平台差异删除**的 `suspicious_stew.json`——见下一条;forge-1.20.1 侧手写配方在 `data/astral_dice/recipes/`(**复数**目录名,1.20.1 布局),共 14 份)。**注意这条数会随内容变化**:2026-09-19 移植「游戏大师(ren)立牌」时新增 `ren_sign` 生成配方,26.1.2 的生成数由 108 → **109** ⇒ 旧文里的「121 = 生成 108 + 手写 13」已过期。凡改配方都请同步 `scripts/test/cases/CRAFT-SMOKE-26.1.2.json` 的 `AP_RC1_RC_MOD` 期望值,并在该用例 note 里写清算式(生成 N + 手写 M = 总数),避免变成魔法数字。
   - **`data/neoforge/loot_modifiers/global_loot_modifiers.json` 索引已废除**:26.1 的 `LootModifierManager` 是扫描目录的 `SimpleJsonResourceReloadListener`(FOLDER=`loot_modifiers`,类里**没有** `global_loot_modifiers`/`entries`/`replace` 字面量)⇒ 旧的索引文件会被**当成一个 GLM 去解析**并报 `No key type in MapLike[{"replace":false,"entries":[…]}]`。**必须删除该索引文件**,14 个 `data/astral_dice/loot_modifiers/*.json` 仍会被自动扫描加载。
   - **`minecraft:crafting_special_suspiciousstew` 序列化器已不存在**:26.1 把「特殊合成」全部数据化(vanilla 现以 `suspicious_stew_from_<花>` 的 shapeless 配方 + 结果组件 `minecraft:suspicious_stew_effects` 表达)⇒ 旧的 special 配方文件及其配方解锁 advancement 一并删除。
10. **26.1.2 新增「长矛」已纳入骰神赐福的近战武器判定(2026-09-17)**:26.1.2 新增 7 种长矛(木/石/铜/铁/金/钻石/下界合金),**没有独立物品类**,是 `Item.Properties#spear(...)` 参数化的普通 `Item`,故只能按 vanilla 物品标签 `net.minecraft.tags.ItemTags.SPEARS`(=`minecraft:spears`,7 项)判定;`DiceCombatEvents#isMeleeWeaponAttack` 现为「剑标签 + **长矛标签** + `AxeItem` + `MaceItem` + `TridentItem`」。用标签而非逐个 Item,可自动覆盖后续新增与其它模组的长矛。⚠️ 该判定只在 26.1.2 线存在(`ItemTags.SPEARS` 是 26.1.2 才有的常量),**不得**回移到 1.21.1/1.20.1 线。
11. **26.1.2 已接入前置库 starengine_lib(2026-09-17)**:该线原为「完全未接入库」的独立移植线,现与另两线一致消费库 —— `gradle.properties` 新增 `starengine_lib_version=1.0.0-SNAPSHOT.5` / `starengine_lib_group` / `starengine_lib_version_range=[1.0.0-SNAPSHOT.5,2.0)`;`build.gradle` 首位 `mavenLocal()` + `implementation "${starengine_lib_group}:starengine_lib-neoforge-26.1.2:${starengine_lib_version}"` + `generateModMetadata` 增模板占位符;`neoforge.mods.toml` 增 `starengine_lib` required 段。**库已提供的 26 个同名类已删本地副本并改指库包**(18 个 `effect/*Effect`、`client/ClientDamageNumbers`|`ActionBarManager`、`component/GameplayConstants`、`event/AmethystDiceHandler`|`EventTargetCollector`|`ModEffectRemoval`|`SignActiveTriggeredEvent`、`item/BossEntityUtil`);配置缝改为 `GameplayConstants.applyConfig(ModCommonConfig.snapshot())`(库不读配置文件)。**该线原有的本地重复类 `effect/ReadyEffect` 已于 2026-09-19 删除**（⛔ 原文「唯一保留的本地副本是 `effect/ReadyEffect`…该线的旧「待命等待器」机制与 **33 个**效果注册**行为未变**」**已过期，就地更正**）—— 「立牌主动前置门控收口」移植把该线的立牌主动改成发布线同款形态（`BaseSignItem.handleUse` 由 `abstract` 改为「默认实现 + WARN」、删 `isSkillWaiting`/`tickSignReadyTimeout` 及其调用点），随该机制存在的本地 `ReadyEffect` 与 `ModEffects` 的 `haiqing_ready`/`bonnie_ready`/`moses_ready` 三处注册**一并删除**：效果注册 **33 → 32**、id 集合与两个发布线逐项一致；同时删掉仅该线多出的 3 个 `effect.astral_dice.*_ready` lang 键与 3 张 `mob_effect/*_ready.png`（lang **686 → 683**、`textures/mob_effect/` **35 → 32**）。该线现**无**任何「库已删而本地保留」的重复类；`KNOWN-ISSUES` **KI-M5②** 已随之标记**关闭**（其中「禁止删本地副本」一条失效，另三条库侧政策仍适用）。取证见 `scripts/test/TESTING-SPEC.md` 续 18。**库版本沿革（2026-09-19 补记）**：该线的 `starengine_lib_version` 已随批次提升过多次（上文的 `.5` 只是 2026-09-17 接入时的值）—— 充能封顶批次升到 `.11`，`allow_firearm_damage` 批次升到 **`.12`**。⚠️ **当前两条发布线为 `.12`，而 26.1.2 线因用户冻结（同日裁决）仍钉在 `.11`**；解冻迁移时必须同步到 `.12`（`.11` 的库里没有 `ALLOW_FIREARM_DAMAGE` 字段）。三条线的 `starengine_lib_version` 与区间下限一贯**同批同步**。
12. **gamerule 族的存放位置与命名随 26.1 全变(2026-09-19 实测,踩过一次假失败)**:① **键名一律 snake_case** —— `naturalRegeneration` → `natural_health_regeneration`、`keepInventory` → `keep_inventory`;`doFireTick` **已被删除**,火势改由整数规则 `fire_spread_radius_around_player`(置 **0** 即等价于旧的 `doFireTick=false`;原版 datafix `GameRuleRegistryFix` 就是这么折算的)表达。⇒ 旧名在 26.1.2 上被 Brigadier **静默拒绝**,`CARD-SELECTOR-26.1.2` 的 4 条 HP 基线断言因此假失败(`naturalRegeneration false` 未生效、自然回血照常,`FoodData` 的分数回血让血量出现 `17.3` 这类小数;修用例、产品未动,复跑后与 1.21.1 读数逐条相同);探针里凡执行该族的命令都按此映射(1.20.1/1.21.1 用 `doFireTick false`,26.1.2 用 `fire_spread_radius_around_player 0`)。② **存储位置**改为 `saves/<world>/data/minecraft/game_rules.dat`(gzip NBT,键带 `minecraft:` 前缀、布尔值是 **TAG_Byte** 0/1 而非旧版 TAG_String),**不再放在 `level.dat` 里**(26.1.2 的 file fix `LevelDatToSavedDataFileFix` 只认 `level.dat` 的 `game_rules` 键,不认旧版 `GameRules`)。⇒ 该线的环境写入一律走该文件(`mt_env.ps1` 的 `Get/Read/Test/Set-MtGameRule*File` 系列;只有 26.1.2 分支会写它,另两线的 `level.dat` 路径与文案逐字不变),且**必须从真实文件读回复核**(读回通过才回显 `MT_WORLD: keepInventory 落地于 …game_rules.dat`),禁止「写进去再读自己刚写的数据」式的自我复核。引用 gamerule 读数时**必须写明观测时刻**——`mt_env world` 重建世界会把规则恢复默认。⚠️ 相关未决项:`AGENTS` 要求的 `mobGriefing=false` 目前**没有代码执行方**(详见 `TESTING-SPEC.md` 续 21),当前靠 2026-09-18 的 noai 硬闸门与本模组用例自带的取证前清理覆盖。

## 模组依赖添加规则(统一口径,1.20.1 + 1.21.1)— 必须遵守

**来源只剩两个**:任何第三方**模组**依赖必须经 **Curse Maven**(`https://www.cursemaven.com` → `curse.maven:<slug>-<projectId>:<fileId>`)
或 **Modrinth Maven**(`https://api.modrinth.com/maven` → `maven.modrinth:<slug>:<versionId>`)获取,且这两个仓库必须在
**三条线各自的 `build.gradle`** 里声明(发布线 `forge-1.20.1` / `neoforge-1.21.1` 与 `neoforge-26.1.2` 均已声明;Gradle 的项目级仓库是累加的,同一文件内不得重复声明)。
**禁止**用本地 jar(`fileTree`/`files`)、作者的官方 maven 或其它站点作为**模组**来源;**非模组库**(mixin / gson / guava / asm / sponge-mixin 等)不受此限。
**Gradle 侧同时强制(2026-09-17 用户要求)**:三个子项目的 `repositories` 均以 `exclusiveContent` 把 `curse.maven` / `maven.modrinth` 两个 group **独占**给这两个 Maven —— 其它任何仓库(含本地镜像 `.oss-basemod-repo`)都不得解析这两个 group,故模组坐标不可能从别处拿到;第三方模组 jar **一律不入库**(`base-mod-libs/`、`base-mod-compile-libs/` 等目录已写入 `.gitignore`)。

1. **坐标写全、版本钉死**:Curse 用 `<slug>-<projectId>:<fileId>`(fileId = CF 文件 id),Modrinth 用 `<slug>:<versionId>`(versionId 是 Modrinth 的版本 id,不是版本号字符串);**禁止** `latest.release`/`+` 之类浮动版本。
2. **同一模组跨线同源**:同一模组在 1.20.1 与 1.21.1 必须来自同一来源(现状:Curios / Patchouli / Iron's Spellbooks / mixinbooster / modernfix / **Jade → Modrinth Maven**;**JEI / Architectury API / KubeJS / Rhino / Collective / Superflat No Slimes / Embeddium / Oculus → Curse Maven**)。只有目标 MC 版本在某来源确实没有构建时才允许分叉,并在 build.gradle 注释里写明理由。
3. **dev 编译 vs 生产运行**:build.gradle 的声明只服务**开发/编译**(1.20.1 走 `modImplementation`/`modCompileOnly` 由 MDG 重映射;1.21.1 / 26.1.2 走 `implementation`/`compileOnly`,NeoForge 侧无 reobf);生产由整合包提供同一模组,**不得**把整合包 jar 复制进仓库或 `run/mods` 当依赖。
4. **硬前置必须进同一套闸门**(与下方「加载器版本门槛」「版本互通门槛」同一体系):若某模组是**运行期硬前置**(缺失会让本模组不报错却静默失效,如 1.20.1 的 `mixinbooster`),必须
   ① 在对应 `mods.toml`/`neoforge.mods.toml` 的依赖段声明 `mandatory=true` + **钉死 `versionRange`**(现状:1.20.1 `forge`/`minecraft`/`curios [5,6)`/`mixinbooster [0.1.3,)`;1.21.1 `neoforge [21.1,21.2)`/`curios [9,)`;26.1.2 `neoforge [26.1.0.0,26.2)`/`curios [15,)`),**并且** ② 纳入**独立的离线加载器门槛守门脚本** —— **现行唯一执行体 = `scripts/verify/verify_forge_loader_gate.ps1`**(2026-09-19 用户裁决「将 1.20.1 加载器版本门槛 / Mixin Booster 硬前置**单独置于独立脚本**」);它**不挂 `scripts/test` 用例链**:零 `Import-Module scripts/test/lib/*`、不读写 `run/**`、不读 `cases/*.json`。旧形态(`scripts/test/mt_loadergate.ps1` + `cases/LOADER-GATE-FORGE-1.20.1.json`,读数落 `run/1.20.1/logs/loadergate.log`)已按用户指令**废弃并删除**,取证与恢复口径见 `scripts/test/TESTING-SPEC.md` 附录 A 续 28。
   ⚠️ **当前只有 1.20.1 有该独立门槛脚本**(`scripts/verify/verify_forge_loader_gate.ps1`):1.21.1 / 26.1.2 侧**新增硬前置前必须先补同形态独立脚本**(同一写法:门控声明三段一致 + 离线判定器复刻 FML 实际比对实现 + `AP_*` 读数 + 退出码 0/1/2),不得只改 toml 了事。
   ⚠️ 门槛必须在 **FML 依赖排序阶段**拒绝不合格环境,不得依赖「先加载再在代码里检查」——mixin 变换早于 mod 构造器。
5. **守门(唯一实现)**:`pwsh -NoProfile -File tools/check_mod_sources.ps1` —— 同时挂在阶段 P(`scripts/test/mt_preflight.ps1` 的「模组来源」一项)与 `scripts/test/TESTING-SPEC.md` §9 静态守门。它检查:**R1** 两个发布线都声明了 Curse + Modrinth 两个仓库且同一文件内不得重复声明;**R1b(对三条线全部生效)** 用了 `curse.maven:` / `maven.modrinth:` 坐标就必须声明对应仓库(2026-09-17 实测踩坑:26.1.2 缺 Curse Maven 仓库时用它取 JEI 直接 `BUILD FAILED`);**R2** 模组坐标只能是这两个来源、**实际命中**的本地 jar 兜底一律 FAIL、库按 `$LibraryGroups` 白名单放行;**R3** 未被引用的本地 jar 只回显。**官方 maven 的模组例外当前为空**(`$ModExceptions = @{}`;原 `mezz.jei` / `dev.architectury` 已于 2026-09-17 迁到 Curse Maven);将来若必须临时用官方 maven,须在 `$ModExceptions` 登记(每轮以 `[EXC]` 行回显,不允许静默),新增库同理登记到 `$LibraryGroups` 并写明理由。
6. **换源必须给等价性证据**:把模组从其它来源改到 Curse/Modrinth Maven 时,必须给出**可核验的等价性证据**并真跑一次依赖解析/编译:
   - 官方 maven **与 CF/Modrinth 提供同一个文件**时 → 直接比 `SHA1`(逐字节相同)。先例:JEI 三个版本(1.20.1 / 1.21.1 / 26.1.2)改 Curse Maven 后解析到的 jar sha1 与官方 maven 逐一相同(`7f64b7f8…` / `6e703a82…` / `2c3ee3d2…`);1.21.1 的 Iron's Spellbooks 由 `base-mod-compile-libs/irons_spellbooks.jar` 换成 `maven.modrinth:irons-spells-n-spellbooks:RtvqnbKi`(sha1 `09907e3b4bfdabd7f1f44bfd25aa6432183f39bb`、13874139 字节,同哈希)。
   - 双方提供的是**不同形态的产物**(如 CF 生产 jar ↔ 官方 maven dev jar)时 → 必须证明 **MDG 重映射后的产物等价**:Architectury API 由 `dev.architectury:architectury-forge:9.2.14` 改为 `curse.maven:architectury-api-419699:5137938` 后,两个来源经 MDG 重映射得到的 jar **482/482 条目逐条 CRC32 相同、382 个 class 名集合零差异**,且 `:forge-1.20.1:compileJava` 保持 `UP-TO-DATE`(编译期 ABI 指纹未变)。

**加载器版本门槛(必须遵守)**:
- **1.20.1(Forge)**:由 `forge-1.20.1/build.gradle` 从 `gradle.properties` 的 `forge_version`(形如 `1.20.1-47.4.10`)**自动派生两个区间**,分别写入两处:
  ① `loaderVersion` = **javafml 语言主系列区间** `[<major>,<major+1>)`(当前 `[47,48)`);② `modId="forge"` 强制依赖 = **精确区间** `[<forge_version>,<major+1>)`(当前 `[47.4.10,48)`)。
  ⚠️ **精确区间不得写进 `loaderVersion`(2026-09-14 实机验证)**:javafml 语言提供者的版本号**就是 Forge 主系列号**(47),与完整版本号不同构;写 `[47.4.10,48)` 会让 FML 以
  `Missing language javafml version [47.4.10,48) wanted by astral_dice-1.2.1+forge_1.20.1.jar, found 47` **直接拒绝加载本模组**(Forge 自身的 `mods.toml` 也只写 `loaderVersion="[24,]"`)。
  精确下限放在依赖上即达到同一门槛:低于 47.4.10 的环境在 FML 依赖排序阶段报 `Missing or unsupported mandatory dependencies:`。**禁止**再引入 `loader_version_range` 之类的独立属性或硬编码区间。
  曾因 `[47,)` 只卡到 47.0.0,导致 47.0.0~47.4.9 环境照常加载后才在 Mixin 变换阶段报错(2026-09-14 修复)。改动 `forge_version` 时两个区间自动跟随,无需手工同步。
  **同一门槛体系里的第二道硬前置 = Mixin Booster**(2026-09-15 固化,详见下方「forge-1.20.1 子项目关键差异速记」首条):Forge 1.20.1 的 FML 没有 Mixin 集成,
  缺 `mixinbooster` 时本模组**不报错、全部 Mixin 静默失效**,故必须声明为 `mandatory=true` + `versionRange="[0.1.3,)"`,未安装即在同一阶段拒绝启动。
  ⚠️ **模板里的注释也参与 Groovy 展开(2026-09-15 实测踩坑)**:`forge-1.20.1/src/main/templates/META-INF/mods.toml` 由 Groovy `SimpleTemplateEngine` 展开,**不得出现「美元符号 + 标识符」的裸写法**(只有「美元符号 + 花括号 + 键名」合法,即 `${key}` 形式),
  否则 `:forge-1.20.1:generateModMetadata` 以 `Missing property (xxx) for Groovy template expansion` 失败,并把 `build/generated/sources/modMetadata/META-INF/mods.toml` **截断为 0 字节**——随后 `runClient` 直接起不来(表现与"前置门槛"无关,极易误判)。改任何模板注释后**必须真跑一次 `:forge-1.20.1:build` 验证**。
- **1.21.1(NeoForge)**:`neoforge.mods.toml` 的 `neoforge` 依赖声明为 `[21.1,21.2)`(二号位 band);`loaderVersion` 仍用 `loader_version_range=[1,)`(FML 主版本)。
- 两侧门槛都必须在 **mods.toml 解析 / 依赖排序阶段**拒绝不合格环境(FML 会给出可读提示:语言提供者版本不符 = `fml.language.missingversion`;
  强制依赖不满足 = `Missing or unsupported mandatory dependencies:`),**不得**依赖"先加载、再在代码里检查"——mixin 变换早于 mod 构造器,那样只会得到 mixin 报错。

发布规范:GitHub **Release tag 使用无后缀的基础版本号**(如 `1.1.3`,禁止 `v` 前缀与 `+加载器` 后缀),tag 推送即触发 CI 自动构建并发布**三个 jar**(1.21.1 + 1.20.1 + 26.1.2,第三个是 26.1.2 的低优先级 `-beta` jar);发布线分支 `multi-1.20.1-1.21.1` 推送时 CI 会从 `mod_version` 剥离 `-rc/-pre` 与后缀自动打 tag。⚠️ **剥离规则是「先剥 `+后缀`,再剥第一个 `-` 之后的一切」**(`BASE=${VERSION%%+*}; BASE=${BASE%%-*}`)⇒ **`1.2.1-hotfix` 打出的 tag 是裸版本 `1.2.1`**(2026-09-18 实测确认),tag 已存在时 CI 会走 `gh release edit` + `gh release upload --clobber` **刷新同一个 Release**,不会再建新 tag/Release。**26.1.2 永远只作为附件随发布线 Release 发布,不生成自己的 tag/Release**(其 `1.2.1-beta.2` 不是裸 `x.y.z`);CI 侧实现见 `.github/workflows/build.yml` 的 `Create/Update GitHub Release (three JARs, notes from release/<tag>/)`。
**Release 正文取自玩家侧发布说明**(2026-09-17 起,用户要求):`release/<tag>/PLAYER_CHANGELOG_ZH.md` + `release/<tag>/PLAYER_CHANGELOG.md`,中文在前、中间插 `---`、英文在后,经 `gh release ... --notes-file` 整文件传入(不再用内联单行 `--notes`);两份文件都不存在时只打 `::warning::` 并退回「附件清单」兜底,**不阻断发布**。⇒ 发布前必须确认该版本目录的两份文件已存在且与 `CHANGELOG_(ZH|EN).md` 同步(见「更新日志约定」)。
**CI / Actions 状态由用户自行观察(2026-09-17 用户裁决,必须遵守)**:本机无 GitHub token、不安装 `gh`,因此**代理不得监视、轮询或尝试查询** GitHub Actions / Release 状态(不跑 `gh run view|list`、不装 CLI、不改用 API 轮询)。推送后代理只在交付说明里列明**预期结果**与失败时的排查入口(远端 job 日志),由用户到 Actions 页面自行核对;禁止把「本机看不到 CI」写成未完成事项反复追问。

## 版本互通门槛（Version Gate）— 必须遵守

**规则**:客户端与服务端只有在 `mod_version` 的**二号位（major.minor）相同**时才允许互联。

| 组合 | 结果 |
|---|---|
| `1.2.x` 客户端 ↔ `1.2.y` 服务端 | **必须放行**（同二号位） |
| `1.1.x` 客户端 ↔ `1.2.0` 服务端 | **必须拒绝**（跨二号位） |

- **判据自动派生，禁止硬编码**：判据链 = `gradle.properties` 的 `mod_version` →（构建期 `build.gradle` 的 `generateModMetadata` 任务把模板 `src/main/templates/astral_dice_version.properties` 展开成资源 `astral_dice_version.properties`，随 jar 发布）→（运行期 `com.merlinkitsune.astral_dice.network.VersionGate`）→ 互通号 = 前两段数字（`1.2.1+neoforge_1.21.1` / `1.2.1+forge_1.20.1` → `1.2`；`1.1.3` → `1.1`）。**任何代码/配置里都不得出现硬编码的具体版本号（如 `"1.2"`）**；`VersionGate.majorMinor(String)` 是唯一解析入口，两个子项目各有一份（源码不共享）。
- **升级二号位（1.2 → 1.3）无需改任何代码**：只改两个子项目 `gradle.properties` 的 `mod_version`，互通号随之变为 `1.3`，旧二号位客户端（互通号 `1.2`）自动被拒；同二号位的补丁版（`1.3.0` ↔ `1.3.4`）自动互通。
- **实现机制（按各平台原生机制，两子项目均已接入）**：
  - `neoforge-1.21.1`：`network/ModPayloads` 把互通号交给 `RegisterPayloadHandlersEvent#registrar(String)`。NeoForge 在**配置阶段**做通道协商，逐通道用 `String.equals` 比较两端版本号字符串（`NetworkComponentNegotiator#validateComponent`），不等即协商失败：服务端先下发 `ModdedNetworkSetupFailedPayload`（含失配原因）再断开连接。
  - `forge-1.20.1`：① `network/ModNetwork` 把互通号作为 `SimpleChannel` 的通道协议版本号，`clientAcceptedVersions`/`serverAcceptedVersions` 均为 `VersionGate::accepts`——FML 在**登录握手**中经 `S2CModList`/`C2SModListReply` 交换各通道版本并各自校验（`validateServerChannels`/`validateClientChannels`），不匹配即断开；② `AstralDiceMod` 经 `ModLoadingContext#registerDisplayTest` 用同一判据注册 `IExtensionPoint.DisplayTest`，**取代 Forge 默认的 `MATCH_VERSION`**——默认值比较的是**完整版本号**（`1.2.0+forge_1.20.1`），比本规则更严，会把 `1.2.0` ↔ `1.2.1` 这类同二号位组合在多人服务器列表里误标为不兼容。该 DisplayTest 只影响服务器列表的「兼容」标记，不构成连接门槛（硬门槛是 SimpleChannel）。
- **拒绝时的用户可见表现（不是静默握手失败）**：
  - `1.21.1`：客户端进入 NeoForge 的「连接已丢失 / Connection Lost」**模组版本不匹配**屏幕，表格「通道名称」列出本模组的载荷 id、「原因」为「模组网络通道 "Astral Dice" 连接失败：…版本…」（同一失配原因的多条通道会合并为一行 + `[+N more]`）。⚠️ **已知 NeoForge 自身文案缺陷**：`NetworkComponentNegotiator` 传参为 `(服务端版本, 客户端版本)`，而语言文件把第一个占位符写成「客户端期望」——两个版本号本身正确，但角色标签是反的；这是上游措辞问题，不要为此放弃原生门槛。
  - `1.20.1`：客户端显示 Forge 的**模组不匹配**屏幕（`fml.modmismatchscreen.mismatchedmods` + 表格「模组名称 / 你拥有 / 服务端拥有」），列出模组名与**两端完整 `mod_version`**，并明确提示「安装与服务端相同版本的这些模组以加入此服务器」。
  - 两侧服务端日志都会留痕（NeoForge 协商失败原因 / Forge `Channels [...] rejected their client side version number`）。
- **不得破坏单机与开发环境**：单人游戏的「内嵌服务端」与客户端来自**同一构建**，互通号必然相同，照常可玩；`scripts/test` 的自动化流程依赖该能力。改动本规则后必须复跑**三线**构建与冒烟流程(版本互通门槛在三个子项目各有一份实现,26.1.2 与另两线同规则)。
- **兜底顺序**：`VersionGate` 先读构建期资源，读不到才回退到加载器元数据（`ModList` → `IModInfo#getVersion()`）；两者都失败时互通号退化为固定值并打印 ERROR 日志（只为不崩游戏，正常构建绝不会走到）。改动本规则时**必须保留**「读不到就退化」的行为，不得改成抛异常启动失败。

## 更新日志约定 — 必须遵守
- **按语言拆分为两个文件**:中文更新日志 = `CHANGELOG_ZH.md`,英文更新日志 = `CHANGELOG.md`。两文件均为**纯单一语言**,禁止再出现另一种语言(不得内联双语)。
- **严格一一对应**:同一版本号必须在两文件各出现一次,且**条目数量与顺序完全一致**(英文为中文的逐条对应译文);每次改动必须**同时更新两份**,禁止只改一侧。
- 每版本按需含 新内容/内容与平衡性调整/已修复BUG 子节(英文对应 New Content/Content & Balance/Bug Fixes);同一子节在两文件的相对位置必须一致。
- **两个 CHANGELOG 是「玩家侧」文档(2026-09-16 用户裁决,必须遵守)**:只收录**玩家可见**的内容 —— 新内容 / 内容与平衡性调整 / 已修复BUG(英 New Content / Content & Balance / Bug Fixes)。**工程 / 工具 / CI / 脚本 / 文档口径类条目一律不进 CHANGELOG**,改写入 `scripts/test/TESTING-SPEC.md` 的「**附录 A：工具链与发布工程变更记录（自 CHANGELOG 移出）**」;英文侧同步删除对应 `### Project` 小节(工程记录以中文归档,避免在英文玩家侧文档里出现开发者条目)。历史版本已有的 `### 工程`/`### Project` 小节**保持原样、不回改**。
- **条目分类(2026-09-16 起)**:版本小节内按内容用四级标题 `#### <分类名>` 分组,固定 5 类、只出现有条目的类、顺序固定 —— `伤害与结算` / `立牌与技能` / `物品、筹码与交易` / `状态、存档与同步` / `文案与手册`(英 `Damage & Resolution` / `Signs & Skills` / `Items, Chips & Trading` / `State, Persistence & Sync` / `Text & Handbook`)。**只调归属与顺序,不改条目文字**;两份日志每个分类的条目数与顺序必须**逐类一一对应**。
- **版本小节标题只写版本号**:`## 1.2.1`(不带 `未发布`/`Unreleased` 前缀)。开新版本时按上一条先建 `未发布(<版本号>)` 小节;**一旦用户裁决锁版本/发布,必须把该前缀清除为 `## <版本号>`,并同时清掉小节内的「约定」提示块(该约定上移到文件顶部说明区)**。
- **玩家侧发布说明（Release Notes）— 必须遵守**:每个发布版本另出一对文件
  `release/<版本>/PLAYER_CHANGELOG_ZH.md`（中）与 `release/<版本>/PLAYER_CHANGELOG.md`（英，**无 `.en` 后缀**），
  从该版本 CHANGELOG 的**玩家侧小节**提炼成可读文案。**格式基准 = 上一个版本的那两份文件**
  （emoji `##` 小节:`✨ 新增内容 / ⚖️ 平衡与体验调整 / 🐛 问题修复 / 📌 安装要求`;英文 `✨ New Content / ⚖️ Balance & Quality-of-Life / 🐛 Bug Fixes / 📌 Requirements`;内部用**话题式 `###` 分组**）。
  要求:① 条目一条不少（与 CHANGELOG 该版本玩家侧条目数一致）;② 数字、时长、层数、范围、概率、条件**逐字不改**;
  ③ 去掉实现细节（类名/方法名/附件键/文件路径/mixin/NBT/commit 编号）;④ **不含**工程与测试条目;
  ⑤ 中英两份逐条一一对应;⑥ 安装要求里 **1.20.1 的 Mixin Booster 硬前置必须在列**。现有先例:`release/1.2.0/`、`release/1.2.1/`(两版均含 26.1.2 第三附件与 Curios 15+ 说明)。**CI 直接以这一对文件作为 GitHub Release 正文**(整文件 `--notes-file`,中文在前 + `---` + 英文在后),故缺文件时 Release 只会退化为「附件清单」——发布前必须核对本目录。
- **合并约定(1.2.0-rc1 起)**:对当前(未发布)版本已记录条目的后续改动,直接合并进原条目,仅保留改动后的最终版本;禁止追加“再次修改/后续调整”类条目。
- **自动更新约定**:每次增加/修改任何新内容(物品/配方/机制/平衡/修复/版本号等)都必须同步更新两个 CHANGELOG 文件的对应小节;若后续存在重复修改、回滚或删除(如恢复被删物品、配方回滚、数值改回旧值),对应条目也必须按**最新变化**合并改写或删除,禁止保留已失效的旧描述。
- 开新版本:先升 gradle.properties 的 `mod_version`,再在两个文件顶部各新建 `未发布(<版本号>)` / `Unreleased (<版本号>)` 小节,后续改动记录到该节。
- **同步自检**:改动后按版本号比对两文件的 `- ` 条目数,同版本条目数必须相等(不等即为漏改一侧)。

### 子项目修改默认规则 — 必须遵守
- **功能/修复默认同步修改三个版本（2026-09-19 起 26.1.2 纳入主线）**:所有功能、修复、平衡性调整一律在 `neoforge-1.21.1`、`forge-1.20.1`、`neoforge-26.1.2` **同时实施**,三侧保持功能对等。**实施方式**:以 `neoforge-1.21.1` 的实现为**语义基准**,按 `docs/compat-1.20.1-forge.md` / `docs/compat-26.1.2-neoforge.md` 的差异映射在三线各自落地;平台差异逐条登记(不写「看起来一致」的实现)。
- **实施必须走 subagent(2026-09-19 用户裁决)**:每次代码修改**必须创建 subagent**,**三个版本各一个 subagent 并行实施**(复杂批次再按子系统拆分);同一文件同一时间只能归**一个** subagent 所有(共享文件由主 agent 预先指派 owner,跨文件接口由主 agent 先固定)。详见「### 模组内容更新规则(三线同步)」第 1-2 条。
- **每次改动完成后自动执行下述收尾(无需用户逐项指示)**:① 同步两个 CHANGELOG 文件 → ② 构建**三个**版本 → ③ **自动部署到整合包**(随 `gradlew build` 触发的 `pushToGame`;**仅发布线分支 `multi-1.20.1-1.21.1` 会真正推送,其它分支严格执行「不推整合包」——见「编译产物上传规则」的分支口径**)→ ④ **自动本地提交**。全程**不执行 `git push`**。
- **单侧改动仅限用户明确要求**:只有当用户明确说“只改 1.21.1 / 先不移植”时,才允许只改一个版本;禁止擅自只改单侧或长期让三版本功能不对等。
- 平台差异按各子项目规范实现(1.21.1 / 26.1.2 用数据组件/附件,1.20.1 用 `component/*DataKey` 与 Capability;事件、Curios 注册、Mixin、数据包目录均不同),**不得为了“看起来一致”而破坏目标平台的正确写法**。
- 用户在任一版本测试时报告的 BUG/需求,默认在**三个版本同步修复**。

### 模组内容更新规则(三线同步)— 必须遵守（2026-09-19 用户裁决修订）

自 2026-09-19 用户裁决起，`neoforge-26.1.2` **纳入主线**：三条线（`neoforge-1.21.1` + `forge-1.20.1` + `neoforge-26.1.2`）**同批同步实施**，原「发布线先落地、26.1.2 之后再迁移」的优先顺序**作废**（`neoforge-26.1.2` 不再是低优先级线）。三线同处一个工作目录（26.1.2 已并入主线）。

⛔ **2026-09-19 用户裁决（覆盖上段）：暂停 26.1.2 版本更新** —— 原话「暂停26.1.2版本更新」。即：在用户另行通知前，**不得**再向 `neoforge-26.1.2` 落地任何内容/修复/迁移（含该线代码、资源、数据、探针与用例），该线**冻结在当前已提交状态**（`127fabd5`，三线功能对等已按 `TESTING-SPEC.md` 附录 A 续 20/22 的实测表达成）。本文其余各处「三线同批」「三线同规则」的要求对 26.1.2 **暂不适用**；`neoforge-1.21.1` + `forge-1.20.1` 两个发布线照常推进（仍按「先 1.21.1、再同步 1.20.1」）。**恢复需用户明确指示**，不得以「顺手对齐」为由自行恢复。⚠️ 冻结期间对 26.1.2 的**只读**取证（构建、冒烟、结构比对）不受限；受限的是**写改动**。

**⛔ 冻结期间（2026-09-19 起）发布线新增、待解冻后迁移到 26.1.2 的清单**（此后每次在发布线落地新内容都必须在此追加一行；迁移完成后标注「已迁移 + 日期」）：
1. **`allow_firearm_damage` 公共配置项**：两发布线 `config/ModCommonConfig` 新增 `BooleanValue`、`CONFIG_VERSION` **2 → 3**、`snapshot()` 末尾追加第 7 实参 `ALLOW_FIREARM_DAMAGE.get()`。
2. **库侧承载**：`GameplayConstants.ALLOW_FIREARM_DAMAGE`（`public static boolean = false`）与 `GameplayConfigValues` 末尾第 7 分量 `allowFirearmDamage`（**库 `1.0.0-SNAPSHOT.12` 起**；库侧 `applyConfig` 已写入该字段）。
3. **消费点**：`combat/SpellDamageRegistry.isSpellDamage()` 改为 `if (!GameplayConstants.ALLOW_FIREARM_DAMAGE && isFirearmDamage(source)) return false;`。
4. **依赖坐标**：两条发布线的 `starengine_lib_version` 与 `starengine_lib_version_range` 同时提到 `1.0.0-SNAPSHOT.12`；**26.1.2 线因冻结仍钉在 `.11`** ⇒ 解冻迁移时必须一并提，否则该线编译期拿不到新字段。
5. **文档/日志**：两份 CHANGELOG 的该条目是三线共用的一对文件（不需要为 26.1.2 另写），但**功能上 26.1.2 目前没有这个开关** —— 该线的 `ModCommonConfig` 里根本没有 `allow_firearm_damage` 键，配置文件里不会出现该项，玩家改不了。`AGENTS.md` 的法伤口径条目（见「效果牌」小节）在 26.1.2 上暂不成立。
6. **风水师立牌(zhao)+ 符卡-福/符卡-祸(2026-09-26 本批;26.1.2 侧**未改动任何文件**,以下为解冻后待迁移项)**:① **物品三件套** —— `zhao_sign` / `fu_card` / `huo_card` 的 `item/ModItems` 注册(传奇 = `Rarity.UNCOMMON`)、`init/ModCreativeTabs` 创造栏、两段式 datagen 产物(1.21.1/1.20.1 是 `src/generated/resources` 单根 ⇒ 26.1.2 必须**另出** `src/generated/clientResources` 的 `items/*.json` 物品模型定义 + `models/item/*.json`,否则客户端只留一行 `Missing item model`);② **标签四处**:`data/astral_dice/tags/item/{signs,effect_cards,is_exclusive}.json` + `data/curios/tags/item/stand.json`(注意 1.20.1 侧是 `tags/items` 目录名,26.1.2 与 1.21.1 同为 `tags/item`);③ **贴图五张**:`textures/item/{zhao_sign,fu_card,huo_card}.png` 与 `textures/mob_effect/{zhao_blessing,misfortune}.png`,必须与发布线**逐字节相同**(取自仓库根 `images/`);④ **配方与进度**:`ModRecipeProvider` 的形状配方 + 生成物 `recipe/zhao_sign.json`、`advancement/recipes/misc/zhao_sign.json`,并同步 `scripts/test/cases/CRAFT-SMOKE-26.1.2.json` 的配方数判据(生成 109 → 110、总数 122 → 123;⚠️ 该用例已随 2026-09-26 用例清理删除 —— 见 `scripts/test/TESTING-SPEC.md` 附录 A 续 27,迁移时如需回归配方数须先用 `git checkout` 恢复该用例)。
7. **两个新效果**:`zhao_blessing`(白泽赐福,`effect/ZhaoBlessingEffect`,BENEFICIAL、常驻 `Integer.MAX_VALUE`、移除走前置库 `ModEffectRemoval`)/`misfortune`(厄运,`effect/MisfortuneEffect`,HARMFUL、层数镜像)—— 26.1.2 侧效果注册 **32 → 34**;`ModEffects` 两条注册照 1.21.1 同形抄写(该线包装类型与 1.21.1 相同)。
8. **7 个玩家附件键**(与 1.21.1 逐字同名同型):`zhao_blessing_active` / `zhao_blessing_skip_cycles` / `zhao_prev_blessing` / `zhao_overflow_bonus`(Int) / `zhao_overflow_remainder`(Float) / `huo_card_next_damage_tick`(Long) / `fu_card_cycle_bonus`(**唯一需 `.sync`**)。26.1.2 与 1.21.1 同用 `AttachmentType`,写法照 1.21.1 内联 `.sync` 即可。
9. **行为代码(10 个文件,按 1.21.1 为语义基准抄写 + 差异映射)**:`item/sign/ZhaoSignItem`(主动 `TargetSelectionAction` + `SelfTargetable`、下降沿状态机、溢出回收、福祸相倚、心意相连)、`item/card/FuCardItem` / `HuoCardItem`、`item/sign/FenSignItem`(`addRecharge` + 心意相连调用)、`event/PlayerTickEvents`(每 tick `tickBlessing` + 每 20 tick `HuoCardItem#tick`)、`event/PlayerLifecycleHandler`(死亡段 / 重登段)、`event/ModTooltipHandler`(三块 tooltip)、`combat/DiceCombatEvents`(骰点定稿后的挂点 + `tryClaimDiceJudgment` 守卫)、`combat/DiceCombatModifiers`(溢出攻击力加算项)、`item/card/EffectCardPeriod`(`grantFuCardBonusPlay` + `getMaxAllowed` 的 `extra` + `clearRoundBonuses` 归零)、`item/card/{RandomCardHandler,ExclusiveCardUtil}`(专属注册与拒绝)。
10. **数据与文案**:lang 新增键(**每条发布线各 32 键 × 2 语言** = 本批功能 31 键 + 1 条探针辅助文案 `command.astral_dice.astralparty.dump.summary`;31 键的分布 = `item.*` 3 / `effect.*` 4(含 2 条 `.description`) / `msg.*` 9(含 3 条 `target_select.skill.*` 技能名) / `tooltip.*` 8 / `astral_dice.guide.entry.*` 7)、帕秋莉手册 `entries/{signs/zhao_sign,cards_effect/{fu_card,huo_card}}.json`、以及**赏金池同口径**(`astral_rews.json` 两张既有专属牌出池、`zhao_sign`/`fu_card`/`huo_card` 不入池 —— 迁移后必须复跑 `scripts/verify/verify_bountiful_pools.ps1 -Root <该线工作树>`)。
11. **测试资产**:探针 `zhao*` 段 **25 条命令**(`zhauprep/zhauread/zhaureg/zhaogive/zhaosethuo/zhaoclear/zhaocast/zhaogate/zhaobless/zhaosign/zhaodice/zhaodedup/zhaoheal/zhaoseq/zhaofu/zhaohuotick/zhaohuobox/zhaodrop/zhaocard/zhaoplay/zhaohand/zhaofx/zhaolife/zhaolink/zhaonuclear`;两条发布线的该段**逐字节相同**,迁移时照抄即可)+ 用例 26.1.2 twin(发布线本批为两条:**`ZHAO-SIGN` = 142 步/50 断言**、**`FUHUO-CARD` = 137 步/46 断言**;按 §13.2 做三侧读数 diff)。⚠️ 26.1.2 侧适配三件事(详见 `AGENTS.md` 的《风水师立牌（zhao）…双人测试流程》段踩坑 1–3):① 凡命令体末尾 `return` 读数变量的处理器都要改成 `return 1;`,否则 `Cannot convert rc=ok to int` 且异常逃出 `guard`;② 顶层 `var`/`function` 不得与既有声明重名(重复声明 ⇒ 整脚本加载失败、命令全无);③ 效果判定用 `findEffect(p, "<id>")` 字符串匹配,禁用 `hasEffect(ModEffects.X)`(该线注册表强转异常连 `guard` 都捕不到);④ 实例方法必须调在实例上(`stack.getItem().playFromSelector(...)`)。
12. **未对齐项一并带上**:t4 的 VER-01(两线英文卡名分歧:冻结值 = `Blessing / Misfortune Talisman`,1.20.1 现为 `Fortune / Misfortune Talisman Card`)、VER-02(1.20.1 手册缺半句)、VER-03(`countFu` 是否含副手)、VER-04(1.20.1 丢弃被拒无 actionbar 反馈)在解冻前若未在发布线统一,**迁移时必须按统一后的口径落线**;若仍不统一,则按平台差异逐条登记理由(见 `AGENTS.md` 实现契约第 12 条与附录 A 续 25)。

1. **实施方式：代码修改一律走 subagent，三个版本并行（用户 2026-09-19 裁决，必须遵守）**：① 每次代码修改**必须创建 subagent**；② **三个版本各一个 subagent**（`v121` / `v120` / `v2612`）**并行实施**同一语义改动；③ 批次较大时再在**每条线内**按子系统拆更多 subagent；④ **同一文件同一时间只能有一个 owner**：共享文件（`lang/*.json`、`ModTooltipHandler`、注册表类、探针脚本等）由主 agent 预先指派唯一 owner，其余 subagent 只**报告**自己需要的改动，不得同时写同一文件；⑤ 跨文件接口（新方法签名、新 lang 键、新探针读数格式）由主 agent 在派活前**先固定并写进各 subagent 的任务描述**。
2. **主 agent（队长）的职责（必须遵守）**：① 侦察/定范围、固定接口与文件 ownership、派活；② **构建与冒烟测试一律由主 agent 亲自执行**（`scripts/test` 阶段 P/B/E/L/C/R、`mt_build`、`gradlew build`、`--phase launch`、`--phase cases`、`mt.ps1` 全流程）——**禁止**把这些放进 subagent，以保证改动与测试过程**对用户可见**；③ 汇总报告、解决冲突、跑静态闸门、同步 CHANGELOG、本地提交。构建/冒烟失败时**优先**把错误回报给该文件所属 subagent 修；其已结束或连续两次未修好时主 agent 可接手并在交付说明里记明。
3. **平台差异**：按 `docs/compat-26.1.2-neoforge.md`（26.1.2 相对 1.21.1）与 `docs/compat-1.20.1-forge.md`（1.20.1 相对 1.21.1）的差异映射实现，允许并**必须逐条登记**平台差异（如 26.1.2 无 Iron's Spells 联动、`ItemTags.SPEARS` 只在 26.1.2 存在、26.1.2 的 KubeJS 探针 API 形态不同等）。**禁止**用「能启动/跑通了」代替一致性结论。
4. **一致性测试**：三线落地后按 `scripts/test/TESTING-SPEC.md` §13.2 的方法（**同探针 + 同用例 + 三侧读数 diff**）在 26.1.2 上复跑 1.21.1 已通过的用例，逐条比对读数；差异要么修掉、要么作为**平台差异**登记并写明原因。
5. **版本号与发布**：26.1.2 子项目当前版本号 = **`1.2.1-beta.2+neoforge_26.1.2`**（保留 `-beta` 以与发布线 `1.2.1` 的发布态区分；改动只动 `neoforge-26.1.2/gradle.properties`，**不得**连带改 1.20.1 / 1.21.1）；26.1.2 **不单独发版** —— CI 对其跑 lang 同步 + 三子项目构建，并把**已构建出的 jar 作为第三个附件附到发布线的 Release**（2026-09-17 用户要求「将 26.1.2 加入 Action 和 Release」），但**不打自己的 tag、不建自己的 Release**（版本号不是裸 `x.y.z`，不满足打 tag 门槛）；tag/Release 的创建仍只在发布线分支触发。
6. **一次内容更新的验收口径**：① 三线均已落地且各自构建通过；② 核心改动按「变更分级与验证口径」跑**定向最小冒烟**（**只跑与该改动直接相关的用例**，三线各一遍；顺序 1.21.1 → 1.20.1 → 26.1.2，逐线出结论）；命中该节的「**完整冒烟**」五种情形之一（发版批次 / 跨模块公共路径 / 门槛类规则自身改动 / 用户显式要求 / 26.1.2 迁移与一致性验收批次）时才跑**全清单**；纯脚本/文本/tooltip/文档/注释类改动免冒烟，但仍须构建 + 静态闸门全绿；③ 两份 CHANGELOG 同步且条目数一致；④ 26.1.2 侧的一致性测试结论已写入 `scripts/test/TESTING-SPEC.md`（工程口径记录写§附录 A，不写玩家侧 CHANGELOG）。7. **禁止「顺路一起改」与「分批补线」**：不得以某条线更快为由先把该线改完、另两条线留待以后（同批必须三线对齐）；确需分步时必须由用户明确裁决，并把未落地的那条线登记为**阻塞项**（不是「以后再说」）。

### forge-1.20.1 子项目关键差异速记(相对 neoforge-1.21.1)
- **Mixin Booster 是硬前置:未安装 → 直接拒绝启动(2026-09-15 固化,不得删除)**:Forge 1.20.1 的 FML **没有 Mixin 集成**,Sponge Mixin 0.8.5 完全由 `mixinbooster`(Modrinth `mixinbooster`,纯 ModLauncher 服务 jar,内嵌 `fabric-mixin.jar`;模组条目与版本号由自带 `IModLocator` 读 jar 根 `mixinbooster_version.txt` 得到,实装 `0.1.3+1.20.1`,实机 debug.log:`Found valid mod file transmog-mod.jar with {mixinbooster} mods - versions {0.1.3+1.20.1}`)提供——**缺它时本模组不报任何错、全部 Mixin 静默失效**,故必须在 FML **依赖排序阶段**硬拒。三处必须同时保持:① `templates/META-INF/mods.toml` 的 `[[dependencies.${mod_id}]]` 段 `modId="mixinbooster"` + `mandatory=true` + `versionRange="[0.1.3,)"` + `ordering="AFTER"` + `side="BOTH"`(**禁止**写成 `"*"`/省略:旧版本前置同样静默失效);② `build.gradle` 的 `modImplementation "maven.modrinth:mixinbooster:rOaAYvZPZ"`(dev 运行时);③ 防回归执行体 = **独立脚本** `pwsh -NoProfile -File scripts/verify/verify_forge_loader_gate.ps1`(自包含:零 `Import-Module scripts/test/lib/*`、不读写 `run/**`、不读 `cases/*.json`;`-Root` 缺省由 `$PSScriptRoot` 上两级派生 ⇒ 任意 cwd 下都校验**同一棵树**;判据 S1..S6 与读数前缀 `AP_LG_*` 沿用旧执行体,末行为 `AP_LG_VERDICT:…:range_only=1:overall=0|1|2`;退出码 **0 = 门槛成立 / 1 = 明确偏差(逐条 `LG_FAIL:<key>`)/ 2 = 无法判定(`LG_UNDECIDABLE:<key>` + `LG_REMEDY:<key>:<命令>`),且**明确偏差优先于无法判定**(`LG_FAIL` 非空即 1,即使并存 `LG_UNDECIDABLE`)**;`-SkipArtifacts` 只跳过「生成资源 + 产物 jar」两段、模板段仍查,并显式回显 `artifacts=skipped`;真树基线 `exit 0`)。旧形态(`scripts/test/mt_loadergate.ps1` + `scripts/test/cases/LOADER-GATE-FORGE-1.20.1.json`)已按用户指令废弃并删除,见 `TESTING-SPEC.md` 附录 A 续 28。拒绝文案 = FML 原生 `Missing or unsupported mandatory dependencies:` + Mod ID / Requested by / Expected range / Actual version。⚠️ **1.20.1 的依赖段不支持 `reason` 字段**(实测 `ModInfo$ModVersion` 构造器常量池只读 modId/mandatory/versionRange/ordering/side/referralUrl;`reason` 是 NeoForge 的字段),写了也不会显示给用户。
- 无数据组件/附件 API:物品数据走 `component/ItemDataKey`(ItemStack NBT),玩家数据走 `component/AttachedDataKey`(AstralData Capability;29 个 synced 键经 `ModNetwork.AttachmentSyncMessage` 同步(完整清单以 `component/ModAttachments.java` 的 `SYNCED_KEYS` 为准,含 `LIVING_PAGE_CYCLE_BONUS`/`FU_CARD_CYCLE_BONUS`/`EMPOWER_DECAY_AT`/`ELECTRIC_GLOVE_AOE`/`AIRBAG_COOLDOWN_END`/`RAILGUN_COOLDOWN_END` 等)。`ModDataComponents`/`ModAttachments` 常量名与 getX/setX 包装器签名与 1.21 分支一致。
- **伤害事件映射(2026-09-15 校正,旧写法作废)**:`LivingIncomingDamageEvent` ↔ 1.20.1 `LivingAttackEvent`(**减伤前**、可取消);**`LivingDamageEvent.Pre` ↔ 1.20.1 `LivingDamageEvent`(护甲 + 附魔减免之后)**。1.20.1 侧另在 `LivingAttackEvent`(HIGHEST 记原始值)+ `LivingHurtEvent`(LOWEST 读当前伤害)组合还原 original/current 语义(见 `FateGuidanceCardItem.onCurseMitigation`)。⚠️ 旧写「`LivingDamageEvent.Pre` → `LivingHurtEvent`」是**错误映射**:1.20.1 的 `LivingHurtEvent` 派发于**护甲前**(`LivingEntity.java:1665`,早于 `:1667-1668` 的护甲/附魔减免),在 1.21.1 **没有对应事件**(NeoForge `CommonHooks` 无 `onLivingHurt`)。**吸收(黄心)阶段两版本相反**:1.20.1 的 `LivingDamageEvent` 在**吸收之后**(吸收 `:1669-1670` 早于派发 `:1680`),1.21.1 的 `LivingDamageEvent.Pre` 在**吸收之前**(`:1789` 早于 `:1790-1792`)。**法伤主链路必须按本映射挂事件(2026-09-15 用户裁决,必须遵守)**:`event/DamageEffectCardHandler` 在 1.21.1 挂 `LivingDamageEvent.Pre`、1.20.1 挂 `LivingDamageEvent`,其 `combat/SpellDamageContext.event` 的类型随之同步(1.20.1 曾挂 `LivingHurtEvent = 护甲前`,导致**电击手套 3 格 AOE 以「护甲前原始值」为基准**、带甲目标周围多打一截 —— 已按本口径统一,取证见 `docs/scan2/P2-numeric-modifiers.md` 的 `P2-C7`);**禁止**再把 1.20.1 的法伤主链路挂回 `LivingHurtEvent`。残余(平台固有):目标带**吸收(黄心)**时两侧基准仍有差(1.20.1 在吸收后派发、1.21.1 在吸收前),普通目标无吸收故实际影响可忽略。
- Curios:槽位经 FMLCommonSetup IMC 注册(`SlotTypeMessage`,dice=1/stand=1/chip=0);`CuriosApi.getCuriosInventory` 返回 LazyOptional,统一经 `item/CuriosCompat` 包装为 Optional。
- 神秘遗物+ 联动 ID 两子项目**均为 `enigmaticlegacyplus:`**(`cursed_ring`/`the_acknowledgment`/`the_twist`);`enigmaticaddons:the_bless` 保留(forge `templates/META-INF/mods.toml` 里的 `enigmaticlegacy` 仅出现在注释,非依赖声明)。
- 千咒刻印附魔代码注册于 `effect/ModEnchantments`(1.21 分支为数据驱动 JSON:`data/astral_dice/enchantment/curse_marker.json`)。
- 数据包目录 `recipes/advancements/loot_tables/structures`、`data/forge/` 前缀、`pack.mcmeta`(pack_format 15)、`META-INF/mods.toml`。
- **两子项目均有帕秋莉手册**(1.21.1 原有;1.20.1 已于 1.1.3 移植,含 Patchouli 1.20.1-85-forge 依赖);手册结构两版本一致(`{assets,data}/astral_dice/patchouli_books/`)。
- 物品类已按 `item.dice/card/chip/sign` 拆分子包,与 1.21.1 结构一致(见「包结构规范」)。
- 产物清理规则只匹配 `astral_dice-*+forge_1.20.1.jar`,不会误删 1.21.1 产物;部署目标为仓库根 `run/1.20.1/mods`(版本隔离开发测试目录,自动推送)+ `D:/.minecraft/versions/1.20.1 模组测试/mods`(**随 build 默认自动推送**,与 1.21.1 标准一致)。
- **Mixin refmap 是生产硬需求(2026-09-16 生产事故,禁止再删)**:Forge 1.20.1 生产运行的是 **reobf(SRG 名)jar**,而 mixin 的 `@Inject(method = "…")` / `@Shadow` 是**注解里的字符串选择器**(Mojmap 名),reobf **改不动注解字符串** ⇒ 必须由 **refmap** 在运行时翻译成 SRG 名。三处缺一不可:① `build.gradle` 的 `annotationProcessor 'org.spongepowered:mixin:0.8.5'` + gson/guava/asm(注解处理器);② `mixin { add sourceSets.main, 'astral_dice.refmap.json' }`;③ `src/main/resources/astral_dice.mixins.json` 里的 `"refmap": "astral_dice.refmap.json"`(**缺该字段 Mixin 根本不加载 refmap**)。⚠️ **Mixin Booster 不能替代 refmap**:它只提供 `org.sinytra.mixinbooster.MixinModlauncherRemapper` 重映射**字节码引用**,不翻译注解选择器字符串;**dev 永远看不出该缺陷**(dev 类是 Mojmap 名,选择器直接命中,refmap 不参与)⇒ 只有生产会崩。实测故障:`InvalidInjectionException ... could not find any targets matching 'startUseItem()V' ... No refMap loaded` → 启动 FATAL。**已排除加载顺序**(ModLauncher 在 01:06:29.297 就加载了 booster、01:06:29.335 Mixin 子系统已以 booster 为 Source、我们的 config 01:06:31.906 才注册;`mods.toml` 里 `mixinbooster` 为 `mandatory=true` + `ordering="AFTER"`;全部 22 条 mixin 错误只属于本模组,其它带 refmap 的模组无一报错)。历史:`01be1fb` 曾加入该机制修生产 `@Shadow` 映射,`127a2b7` 因「Booster 自动重映射」这一**错误前提**整体移除 ⇒ 生产自此必崩;`7a980b4` 已恢复。**回归判据**(构建后必查):jar 内存在 `astral_dice.refmap.json`、`mixins.json` 声明了它、且配置里的每个 mixin 类在 refmap 中都有条目(1.20.1 目前 11/11)。
- **客户端 tick 的可视状态推进必须唯一且判相位(2026-09-16 修复,禁止再出现第二个调用点)**:Forge 1.20.1 的 `TickEvent.ClientTickEvent` **每客户端 tick 派发 START + END 两次**(1.21.1 对应 `ClientTickEvent.Post`,每 tick 一次)⇒ 「每客户端 tick 推进一次」的客户端可视状态(伤害数字存活 tick 等)只能有**一个调用点**,且必须写相位判断 `if (event.phase == TickEvent.Phase.END)`(或只在 `ClientTickEvent.Post` 里推进)。历史缺陷:伤害数字在 1.20.1 被推进**三次/tick**(`client/ClientTickHandler` 未判相位 2 次 + `client/KeyBindingSetup` 的 END 回调 1 次),同一个 `DURATION = 40` 只存活约 **13 tick(0.67 秒)**且透明度同步衰减到 0(1.21.1 为 2 秒,故只有 1.20.1 有此问题)。⚠️ **不要把「玩家完全看不到跳字」归因于本条**:1.20.1 当时另有一条更致命的**独立**缺陷——视矩阵算错,正前方目标被判为「相机背后」而**每帧剔除、从不绘制**(见下一条);本条只是让存活窗口偏短。**实机判据**(tick 推进速率 = 存活毫秒 ÷ 50):修复后应读到 **40 次推进 / ≈1963~1970 ms**(即每 tick 恰好一次);取证口径见 `docs` 与本次 CHANGELOG 条目(玩家持剑 + 佩戴骰子近战攻击**存活**怪物 → 服务端 `send` → 客户端 `add … hasEntity=true` → 覆盖层 `draw … alpha=242`)。⚠️ 复现该链路时**不要**用探针的 `/astralprobe spelltdhit` 取证绘制:它在同一 tick 末尾 `discard()` 掉全部靶子,客户端下一帧就查不到实体,只能读到 `reason=no_entity`(是测试资产假象,不是产品缺陷)。
- **客户端「世界→屏幕」投影必须按**本版本原版**的视图旋转构造,禁止把 1.21.1 的写法照搬到 1.20.1(2026-09-16 生产事故,必须遵守)**:两版本原版的视图旋转**并不等价** —— ① 1.21.1 `GameRenderer#renderLevel`(neoforge 源 1272–1273 行)= `Quaternionf q = camera.rotation().conjugate(new Quaternionf()); Matrix4f view = new Matrix4f().rotation(q);`;② 1.20.1 `GameRenderer#renderLevel`(forge 源 1127–1128 行)= `mulPose(Axis.XP.rotationDegrees(camera.getXRot()))` 紧接 `mulPose(Axis.YP.rotationDegrees(camera.getYRot() + 180.0F))`(roll 单列,来自 `ViewportEvent.ComputeCameraAngles`,相机不保存、正常为 0)。1.20.1 的 `camera.rotation()` 是 `rotationYXZ(-yRot, xRot, 0)`(`Camera#setRotation`),其逆与 ① 只差**绕 Y 的 180°**与 **pitch 符号**,但「正前方」的符号判定正好相反 ⇒ 照搬 ① 会让正前方目标的 `w` 为负,被 `w <= 0` 的「相机背后」分支**每帧**剔除,**跳字永远不绘制**;1.21.1 因公式与自身原版一致而不受影响,故障表现为「**只有 1.20.1 丢跳字**」。**判据**:取证时必须核对目标在**正前方** 3 格时 `w > 0`(照搬错误写法实测恒为 `w = -3.0`,`1064 次 skip / 0 次 draw`;修复后同一构造为 `w = +3.0` 且有 `draw`)。FOV 取 `options.fov()` 原值(原版 `getFov(…, true)` 为 private,冲刺激活时略有偏差)。**推广**:任何手算矩阵的客户端代码(世界→屏幕、朝向、视锥)都**不得跨版本抄公式**,必须先在**目标版本的 sources jar** 里找到同一处原版写法再照抄。
- **伤害跳字的数值与格式口径(2026-09-19 用户要求,必须遵守)**:① **法伤跳字**(草绿 `0x7CFC00`)显示**该次命中的完整伤害** = 本次事件伤害 **＋** 本次法伤加成,取整用 `Math.round`(两线同在 `event/DamageEffectCardHandler`)。只显示加成会让数字与实际掉血不符 —— 箭矢本身 6 点 + 加成 4 点 ⇒ 跳 **10**(旧写法只跳 4);该基准与电击手套 AOE 基准(KI-5 已定口径)**同源同值**。⚠️ **两线的「本次事件伤害」并不同阶段,别写反**:1.21.1 = `LivingDamageEvent.Pre#getNewDamage()`,**护甲/附魔/抗性之后、吸收之前**;1.20.1 = `LivingDamageEvent#getAmount()`,**护甲/附魔之后、吸收之后(净掉血)**(见 `:275` 的映射条与 `KNOWN-ISSUES.md` KI-5 残余)⇒ **带吸收(黄心)的目标上两线绿字会不同**,这是平台固有差异,不得为「对齐」而取 `getOriginalDamage()`(1.20.1 无对应 API,只会破坏对等)。② **攻击伤与法伤跳字一律不带 `+` 号**:渲染层(`client/ModClientEvents` 的 `DamageNumberOverlay`)只输出数值,禁止再拼前缀(tooltip/actionbar 里的「攻击力 §e+2§7」是另一套渲染,照旧保留)。③ **红/绿字归属由伤害来源决定**:骰战红字(`DiceCombatEvents.sendDamageNumber`,0xFF5555)只在 `directEntity instanceof Player` 时发送(仅近战;注释显式排除弓/弩/三叉戟投掷),而普通近战**不是**法伤 ⇒ **箭矢/法伤类只有绿字、近战只有红字**,两者仅在「既是玩家直接攻击、又被判为法伤」的罕见组合(只可能来自外部法术模组)下同时发送,那时两者**同为 NORMAL 优先级**、谁最后写取决于注册序,**静态不可保证** —— 改动这两处时必须一起评估。④ 该渲染器的容器是前置库 `starenginelib` 的 `ClientDamageNumbers`,语义为**一实体一数字的覆盖**(`Map.put`,键 = 实体 id)⇒ 同一实体同一 tick 多个数字只有一个可见;已知一例:**活体书页命中**会由 `LivingPageImpact` 在 `hurt` **之后**再发一次同色数字(`= base = 2 + 调查员页数 + 效果牌加成`),**覆盖**掉法伤修饰器那条 ⇒ 该书页场景玩家看到的始终是书页自身那个值(该值本身已含基础伤害,但**不含**法伤修饰器那部分加成)。⑤ 绿字是「**NORMAL 阶段**的完整值」,不是最终落地值:NORMAL 之后 LOW 的狂暴/虚弱印记、LOWEST 的安全气囊(`setNewDamage(0)`)仍会改伤害 ⇒ 气囊挡下的那一发绿字照跳全额(改动前后一致,非回归)。

## 包结构规范（Package Layout）— 必须遵守

`item` 包按物品类别拆分子包，新增物品类必须放入对应子包；**公共/共享类保留 `item` 根包**：

| 子包 | 内容 |
|---|---|
| `item`(根) | `ModItems`(注册中心)、`CurioSlotUtil`(通用装备工具)、`StarLightManager`/`HealingManager`/`MarkManager`/`ChargeManager`(玩家资源管理器)、`BossEntityUtil`、`InvestigationEventUtil` |
| `item.dice` | 骰子:`DiceCurioItem`、`DiceTier`、`DiceTierRegistry`、`NetherStarDiceItem`、`ObsidianDiceItem` |
| `item.card` | 卡牌:`CardItem`、`BaseEffectCardItem` + 全部效果牌、`RandomCardHandler`、`EffectCardPeriod`、`EffectCardUtil`、`ExclusiveCardUtil` |
| `item.chip` | 筹码:`BaseChipItem` + 全部筹码 |
| `item.sign` | 立牌:`BaseSignItem` + 全部立牌 |

规则:新增骰子/卡牌/筹码/立牌类分别放入 `item.dice`/`item.card`/`item.chip`/`item.sign`;跨包引用需显式 import(禁止依赖同包隐式可见性)。

## 物品一览（Item Registry）— 新增/修改前必读

所有物品在 `ModItems` 用 `registerItem("<注册id>", ...)` 注册（唯一例外:`astral_guide` 为 Patchouli 书籍 id,经 `ItemModBook.forBook` 生成,见下表）;稀有度按「骰子槽位与配置规范」的映射标准选择。

### 材料（materials）

| 中文名 | 注册 id | 品质 | 备注 |
|---|---|---|---|
| 规则书《恋的规则书》 | `astral_guide` | — | 帕秋莉书籍 id(**非 `ModItems` 注册物品**;经 `ItemModBook.forBook` 生成,创造栏首位;lang 无 `item.astral_dice.astral_guide` 键) |
| 星币 | `star_coin` | 蓝(稀有 RARE) | 货币;袋装星币可 9:1 互转(`star_coin_bag`) |
| 袋装星币 | `star_coin_bag` | 蓝(稀有 RARE) | 9 枚星币打包 |
| 星盘 | `star_plate` | 紫(史诗 EPIC) | 骰子/立牌/筹码合成材料;可从原版宝箱开出 |
| 黄金星盘 | `golden_star_plate` | 金(传奇 UNCOMMON) | 星盘升级/下界之星两条配方;不祥宝库可开出 |
| 空白立牌 | `blank_sign` | 白(普通) | 立牌合成核心,3×3 中央 |
| 空白筹码 | `blank_chip` | 白(普通) | 筹码合成核心,3×3 中央 |

### 骰子（dice）

| 中文名 | 注册 id | 品质 | 阶层标签 | 筹码栏(0★~3★) | 特性 |
|---|---|---|---|---|---|
| 基础骰子 | `dice` | 白(普通 COMMON) | `dice_t0` | 0/1/2/3 | — |
| 黄金骰子 | `golden_dice` | 蓝(稀有 RARE) | `dice_t1` | 1/2/3/4 | — |
| 玻璃骰子 | `glass_dice` | 蓝(稀有 RARE) | `dice_t1` | 1/2/3/4 | 战斗牌点数恒取最大;死亡丢失该骰子及已装备卡牌 |
| 下界岩骰子 | `netherrack_dice` | 蓝(稀有 RARE) | `dice_t1` | 1/2/3/4 | 下界采矿 30% 掉星币 / 5% 掉星盘;猪灵中立(`PiglinAiMixin`) |
| 钻石骰子 | `diamond_dice` | 紫(史诗 EPIC) | `dice_t2` | 2/3/4/5 | — |
| 绿宝石骰子 | `emerald_dice` | 紫(史诗 EPIC) | `dice_t2` | 2/3/4/5 | 村民交易用星币支付并享 20% 折扣 |
| 黑曜石骰子 | `obsidian_dice` | 紫(史诗 EPIC) | `dice_t2` | 2/3/4/5 | +3 基础防御力(折算 +6 护甲);火焰伤害 -70% |
| 诡异骰子 | `weird_dice` | 紫(史诗 EPIC) | `dice_t2` | 2/3/4/5 | 立牌主动冷却 -50%;战斗骰低点数(1-3)概率 +50% |
| 紫晶骰子 | `amethyst_dice` | 紫(史诗 EPIC) | `dice_t2` | 2/3/4/5 | 远程/魔法攻击也触发战斗骰并追加骰点伤害(不触发赐福、不耗卡牌耐久) |
| 下界合金骰子 | `netherite_dice` | 金(传奇 UNCOMMON) | `dice_t3` | 3/4/5/6 | — |
| 绯红骰子 | `crimson_dice` | 金(传奇 UNCOMMON) | `dice_t3` | 3/4/5/6 | 战斗骰高点数(4-6)概率 +50%;骰出 1 自身受 6 点伤害 |
| 末影骰子 | `ender_dice` | 金(传奇 UNCOMMON) | `dice_t3` | 3/4/5/6 | 致命伤害触发不死图腾(5:00 冷却;**保命时只清除有害效果 HARMFUL,保留全部增益**)+ 安全瞬移;雨中/水下受伤 +40% |
| 下界之星骰子 | `nether_star_dice` | 金(传奇 UNCOMMON) | `dice_t4` | 4/5/6/7 | 卡牌栏与费用恒按最高档(12 格、费用上限 6);每提升 1 星级攻防 +2;自带附魔光效 |

升级配方:全部经 `astral_dice:dice_upgrade` 有序配方**以阶层标签为母体**升级(非锻造台)——`dice_t1`←`dice_t0`、`dice_t2`←`dice_t1`、`dice_t3`←`dice_t2`、`dice_t4`←任意 `dice_t3`(下界之星骰子,4 颗下界之星十字环绕 `" N "/"NDN"/" N "`)。新增骰子必须加入对应 `dice_t*` 标签与 `dices` 汇总标签,否则无法作为升级母体。

- **铁砧升星白名单覆盖全部 13 种骰子**(`AnvilUpgradeHandler`):基础/黄金/玻璃/下界岩/钻石/绿宝石/黑曜石/诡异/紫晶/下界合金/绯红/末影/下界之星;★0→★3 每级消耗 15/20/25 星币。**下界之星骰子(T4)的 tooltip 不显示星级费用行**(其卡牌栏与费用上限恒为最高档、不随星级变化),只显示星级与「每提升 1 星级 +2/+2(护甲 +4)」。
- **诡异/绯红骰子的概率口径以文案为准**:单面权重 ×3.0 → P(低点数/高点数) 由 50% 升至 **75%**(相对 +50%),与 tooltip「概率提升 50%」严格一致;改动权重时必须同步核对文案。
- **绿宝石骰子交易:客户端报价必须在「数据层」替换(2026-09-13,必须遵守)**:村民报价的换币(绿宝石→星币 + 20% 折扣)分两条路径——**服务端匹配与扣款**继续走 `MerchantOfferMixin` 的取值覆写(交换上下文内 `getBaseCostA`/`getItemCostA`/`satisfiedBy`);但**发给客户端的报价**必须在数据层替换:`ClientboundMerchantOffersPacket` 的网络编码由 `Connection.sendPacket` 投递到 Netty 事件循环后才执行,那时 ThreadLocal 交换上下文已经关闭,只靠取值覆写客户端永远看到未替换的绿宝石费用(服务端实际扣款仍是星币)。基线实现:1.21.1 注入 `MerchantOffer` 的私有复制构造(数据包构造时**同步**执行 `offers.copy()`,此刻上下文仍活跃);1.20.1 在 `sendMerchantOffers` 内重建报价(NBT 往返,保留 uses/maxUses/rewardExp/xp/priceMultiplier/specialPrice/需求)后自行发包并取消原版。**通用推论**:凡「让客户端看到不同数据」的需求都不得依赖 ThreadLocal 窗口内的取值覆写,必须在数据层产出副本。
- **绿宝石骰子交易:成交后立即重发报价(2026-09-13,必须遵守)**:客户端的职业经验条与职业等级只读数据包(`ClientPacketListener.handleMerchantOffers` → `MerchantMenu.setXp/setMerchantLevel`),原版只在开界面/补货/升级时重发;故在 `MerchantResultSlot.onTake` 结束后由 `EmeraldDiceTrade.resendOffers` 补发一次,使经验/等级即时刷新。
- **活体书页的专属绑定**:发放路径(`AstralEventSystem.applyRinSignPassive` / `RinSignItem` 主动)在创建副本时 `ExclusiveCardUtil.setOwner` 绑定获得者(获得者只可能是装备调查员立牌的玩家);`LivingPageItem` 在首次使用时再以 `bindIfAbsent` 兜底,指令/创造栏取出的未绑定副本由首位使用者获得所有权。
**卡牌栏星级规则(平衡,与品阶无关)**:卡牌栏总格数仅由骰子星级决定——0★=4(攻防各2)、1★=6(各3)、2★=8(各4)、3★=12(各6),所有骰子同星级格数相同,不再按品阶区分(`DiceCurioItem.getCardSlots` 查 `CARD_SLOTS_BY_STAR`);星级超出 0-3 按最近档钳制。槽位收缩时已放卡牌由容器边界截断——升级前请先取下多余卡牌。

### 战斗牌（combat cards）

注册 id:`attack_card_*` / `defense_card_*`,费用/耐久由 `CardRegistry` 集中定义(耐久:中/大/特大/暗影突袭/防御=10、名刀=5、蓄力=1、全力攻击=5)。

| 中文名 | 注册 id | 品质 | 效果 |
|---|---|---|---|
| 攻击-中 | `attack_card_medium` | 白 | 攻击力 1~3 |
| 攻击-大 | `attack_card_large` | 蓝 | 攻击力 1~6 |
| 攻击-特大 | `attack_card_epic` | 紫 | 攻击力 1~10 |
| 暗影突袭 | `attack_card_shadow_strike` | 蓝 | 攻击力 +3 + 黑暗 0:03 |
| 名刀嘎呜切 | `attack_card_meito` | 紫 | 攻击力 1~20(装备护法立牌时费用降至 3) |
| 蓄力 | `attack_card_charge` | 金 | 攻击力 +5(须在赐福前预先放入卡牌栏,赐福期间卡牌栏锁定);骰神赐福结束返还「全力攻击」 |
| 全力攻击 | `attack_card_full_power` | 金 | 攻击力 +6,最终攻击力 +50%(仅由蓄力返还获得,不在随机卡池) |
| 防御-中/大/特大 | `defense_card_medium/large/epic` | 白/蓝/紫 | 防御力 1~3 / 1~6 / 1~10 |

### 效果牌（effect cards）

全部继承 `BaseEffectCardItem`(详见「效果牌命名与结构规范」),注册 id 统一 `effect_card_*`:

| 中文名 | 注册 id | 品质 | 效果 | 备注 |
|---|---|---|---|---|
| 狂暴 | `effect_card_berserk` | 紫 | 攻击力 +3/层、受到伤害 +1/层(最多叠 3 层,上限取常量 `MAX_EFFECT_STACKS` = 3),3:00(重复使用叠层并刷新时长,时长取 `max(旧,3:00)`) | 效果牌,参与复制计数 |
| 王之力 | `effect_card_king_power` | 金 | 受到 8 点伤害,攻击力 +5/层(最多叠 3 层,上限取常量 `MAX_EFFECT_STACKS` = 3),3:00(重复使用叠层并刷新时长) | 效果牌,参与复制计数 |
| 岿然不动 | `effect_card_unwavering` | 紫 | 每层护甲 +8(对应骰战防御力 +4,最多叠 3 层,重复使用叠层并刷新时长)+ 抗性提升 II,3:00 | 功能牌,参与复制计数 |
| 以毒攻毒 | `effect_card_fight_poison_with_poison` | 紫 | 中毒 8 秒后移除最多 3 个原版负面效果,生命恢复 II 30 秒 | 延迟触发 |
| 巧克力蛋糕 | `effect_card_chocolate_cake` | 蓝 | 恢复 20% 最大生命值 | 治疗类 |
| 汉堡 | `effect_card_hamburger` | 紫 | 恢复 40% 最大生命值 | 治疗类 |
| 奢华大餐 | `effect_card_luxury_feast` | 紫 | 治疗目标及周围 6 格内**友方玩家** 30% 最大生命(已加入队伍时仅同队;未加入任何队伍时为全体在线玩家,统一经 `EventTargetCollector.collectTeamPlayers`) | 治疗类,可对他人 |
| 你有我有 | `effect_card_you_have_i_have` | 紫 | 仅能对其他玩家使用,双方各获得一张随机卡牌 | 互动类 |
| 加急加快 | `effect_card_express_delivery` | 紫 | 目标获得 迅捷 II 1:00 | 可对他人 |
| 命运的指引 | `effect_card_fate_guidance` | 紫 | 出牌数 +1;主动技能冷却**减少「当前最大冷却 ÷ 2」的时间**;对虚弱印记目标伤害 +20%;饱食翻倍,5:00 | **专属牌**,仅获得者可用 |
| 对怪激光 | `effect_card_monster_laser` | 蓝 | 远程/魔法伤害 +4,1:00 | 法伤牌 |
| 对怪板砖 | `effect_card_monster_brick` | 紫 | 远程/魔法伤害 +6,1:00 | 法伤牌 |
| 轨道炮 | `effect_card_orbital_strike` | 金 | 远程/魔法伤害 +8,1:00 | 法伤牌 |
| 定向爆破 | `effect_card_directional_blast` | 金 | 远程/魔法伤害 +5,1:00,对目标周围 6 格敌对目标同样伤害 | 法伤牌 |
| 活体书页 | `effect_card_living_page` | 紫 | 本轮出牌数**命中前已有 ≥3 层标记的目标时 +1**(仅当前周期,封顶 9);**敌对目标选择器**(仅敌对目标、手持即选、无倒计时、**锁定范围 32 格且含垂直高度差**);书页以箭矢 4/5 速度(2.4 格/tick)飞向目标、必定命中、可穿透方块(**轨迹 = 连续 END_ROD 粒子带**);命中造成 **2 + 此前已命中的页数** 点法伤(`astral_dice:card_spell`,⚠️ **不含本次使用**:首张固定 **2**,本次的 +1 在伤害与标记之后才累计;页数加成**静默上限 120**)并施加 1 层标记 | **专属牌**;法伤牌;命中结算 |

备注说明:法伤牌(激光/板砖/轨道炮/定向爆破/活体书页)的伤害受忍者立牌「效果牌伤害增益」加成;**复制计数范围为全部效果牌**——治疗/伤害/互动/专属牌同样计入忍者立牌、魔法秘典与魔法箭袋的计数(原 `countsForCopy()` 类型过滤已移除);治疗类牌(`isHealingCard()`)使用后触发大当家被动;专属牌见 `ExclusiveCardUtil`。

### 立牌（signs）— 命名规范见下节

### 筹码（chips）— 一览见「筹码一览」节

## 立牌命名规范（Sign Naming Convention）— 必须遵守

立牌使用**固定的英文 id** 作为其唯一标识，贯穿 Java 代码（类名/字段/方法/注册名/注释中的英文标识）与 MC 数据包（lang key、效果注册 id、纹理文件名）。**禁止**再使用中文翻译生成的英文词（如 guardian/sweeper/business/ninja/vampire/investigator）作为标识符。

**玩家可见名称**取 `lang` 的 `item.astral_dice.<id>_sign`，必须与本表「中文名」列逐字一致（改名时**只动这些 lang 值**，id / 注册名 / 类名 / 附件键 / tag / 配方一律不变）。**英文译名优先采用日语罗马音的二次元（ACG）写法**，不采用直译英文词 —— 先例（2026-09-25 用户裁决）：`mimi_sign` 由「看板立牌 / Board Sign」改为「**看板娘立牌 / Kanban Musume Sign**」，并同步 `msg.astral_dice.mimi_active`（actionbar）与手册 `astral_dice.guide.entry.playstyles.2` 里的旧名，三处之内英 + 三条线全部统一（此前英文三处写法各异：Board Sign / Noticeboard sign / Bulletin Board signs）。

### 现有立牌 id 对照表

| 中文名 | 英文 id | 物品注册名 | 类名 |
|---|---|---|---|
| 看板娘 | mimi | mimi_sign | MimiSignItem |
| 经商 | parunan | parunan_sign | ParunanSignItem |
| 扫地机 | jasmine | jasmine_sign | JasmineSignItem |
| 护法 | misaki | misaki_sign | MisakiSignItem |
| 史莱姆 | lulu | lulu_sign | LuluSignItem |
| 忍者 | komachi | komachi_sign | KomachiSignItem |
| 上班族 | padman | padman_sign | PadmanSignItem |
| 大侦探 | fanny | fanny_sign | FannySignItem |
| 调查员 | rin | rin_sign | RinSignItem |
| 占星师 | haiqing | haiqing_sign | HaiqingSignItem |
| 吸血鬼 | papara | papara_sign | PaparaSignItem |
| 秘密侦探 | bonnie | bonnie_sign | BonnieSignItem |
| 大当家 | fen | fen_sign | FenSignItem |
| 骇客 | nancy_lu | nancy_lu_sign | NancyLuSignItem |
| 枪匠 | moses | moses_sign | MosesSignItem |
| 肉弹战车 | pandaman | pandaman_sign | PandamanSignItem |
| 游戏大师 | ren | ren_sign | RenSignItem |
| 风水师 | zhao | zhao_sign | ZhaoSignItem |

### 立牌技能一览（Active/Passive Skills）

| 立牌 | 主动技能 | 被动技能 |
|---|---|---|
| 看板娘 mimi | 商品补货:回收物品栏全部卡牌(含专属牌),返还 N+1 张随机卡牌(不含专属牌) | 过期回收:合成或返还卡牌时每获得 1 张战斗牌 +1 星币;装备时筹码栏 +1;主动每次返还累计 25 张战斗牌获得 1 个随机筹码(蓝 60%/紫 35%/金 5%) |
| 经商 parunan | 套现:按 2:1 将星光兑换为星币,**只允许兑换「超过基础值」(银行卡等下限)的部分、按实际扣除量发币**;**无实际扣除时整个主动技能作废**(`InteractionResultHolder.fail`:不进冷却、不发 3 选 1,提示 key `msg.astral_dice.parunan_active_no_starlight`);随机获得:饱和 0:30 / 幸运 5:00 / 村庄英雄 15:00;**触发后进入「锁定(生效中)」态,须等本技能施加的全部计时器跑完才进入主动技能冷却**(锁定期内按键无效并提示「主动技能生效中!」) | 传奇商人:每 60 秒星光 +1;触发骰神赐福时获得首次骰点 ×2 的星光 |
| 扫地机 jasmine | 能量过载:2:00 内移速 +20%、护甲 -30%;随机获得:抗性提升 2:00 / 力量 2:00;**触发后进入「锁定(生效中)」态,须等本技能施加的全部计时器跑完才进入主动技能冷却**(锁定期内按键无效并提示「主动技能生效中!」) | 移动充能:每移动 300 米(**三维位移**)交替提升 1 点攻击力与防御力(各自上限 20);使用「加急加快」效果牌后主动冷却立即减少最大冷却的 50% |
| 护法 misaki | 樱花裂空斩:2:00 内攻击力 +4 + 骰子星级追加(1★+1/2★+2/3★+3)、名刀伤害下限按星级增加;**触发后进入「锁定(生效中)」态,须等本技能施加的全部计时器跑完才进入主动技能冷却**(锁定期内按键无效并提示「主动技能生效中!」);剑气满 3 层时消耗 2 层兑换一张名刀 | 剑气:触发骰神赐福 +1 层(最多 3),每层攻击 +1,3 层时额外 +2;名刀费用降至 3 |
| 史莱姆 lulu | 治愈粘液(**目标选择器类**,2026-09-19 重写):选择任意玩家(或右键对自身)⇒ **目标**获得 +3 治愈 + 瞬间治疗;并以**目标为中心** 16 格内玩家/宠物/可骑乘生物施加瞬间治疗、对敌对生物施加缓慢 1:00(选自身时即以自己为中心,与旧行为逐字一致) | 细胞分裂:受到伤害 +1 治愈,主动技能冷却 -10 秒 |
| 忍者 komachi | 忍术连击:**一次性**——当前出牌轮出牌数 +1(**无出牌银行**;`EffectCardPeriod.grantBonusPlay`);**锁定跟随效果牌周期,冷却从"该轮出牌状态完全重置那一刻"开始;宽限 1:00 内自始至终未出任何效果牌则强制重置出牌状态并进入冷却**(一旦出过牌 ⇒ 保险失效、遵循周期);**效果牌冷却进行中 / 出牌数已达封顶 9 张 / 本轮已授予过**时不释放**且不进入主动技能冷却** | 复制者:每使用 3 张效果牌,复制最后一张使用的效果牌 + 主动冷却立即减少 30% + 效果牌伤害增益 +1 |
| 上班族 padman | 真的生气了:重置被动计数器,本次攻击/防御点数取最大值 | 毫无主见:每 60 秒基础攻防点数在 -2~+4 间变动;赐福中骰点为 6 时无视目标防御力(按本模组「目标防御力」口径 `2 + 护甲÷2 + 1.4×韧性`,与贯穿之铳同一公式;护甲已含由防御力折算而来的部分),但**保留目标的防御骰与防御牌加成**;骰点为 1 时下次骰点必为 6 |
| 大侦探 fanny | 麻烦制造者:随机触发 11 项事件之一(不含"调查阶段"事件);触发时联动 fanny 被动与调查员立牌 | 华点发现:自身触发事件后 +3 星币 |
| 调查员 rin | 活体书页:获得一张活体书页(若此前物品栏中无活体书页则共获得两张) | 调查发现:自身触发事件或受到事件影响时,向**触发者本人、其 32 格内的佩戴者与同队佩戴者**各发放一张活体书页;使用活体书页使其伤害永久 +1(死亡重生保留;**加成仅在佩戴立牌时生效**;移除立牌后重置) |
| 占星师 haiqing | 虚弱印记:按下主动进入 **30 秒待命**,待命期内攻击符合骰神赐福触发条件的目标即对其施加虚弱印记 5:00(受任意伤害 +10% + 虚弱效果);2.0.0 开发线(`multi-dev-next` / `wt/2.0.0-vnext`)改经目标选择器选目标,效果相同;**判定期内 30 秒待命窗口不算本技能的计时器**(不进入锁定态,超时未命中仍不消耗冷却) | 幸运星:赐福中骰点为 6 时立即 +6 星币;带虚弱印记目标被击杀时**占星师获得 3 星币**,「命运的指引」归**击杀者**(非玩家击杀不发牌) |
| 吸血鬼 papara | 嘬你一口:3:00 内攻击时恢复骰神赐福最终伤害的一半、受伤时恢复单次伤害的一半;**所有以血量条件决定是否生效的效果在主动技能期间均视为条件通过**——美工刀 ≥60%、肾上腺素/磨刀石 ≤50%、可口糖果"满血"、磨刀石"生命值 > 1"的不可击杀保护等;**触发后进入「锁定(生效中)」态,须等本技能施加的全部计时器跑完才进入主动技能冷却**(锁定期内按键无效并提示「主动技能生效中!」) | 可爱即正义:生命值低于一半时攻击力/防御力 +3(汲取期间) |
| 秘密侦探 bonnie | 隐匿行动:按下主动进入 **30 秒待命**,待命期内攻击**符合骰神赐福触发条件的目标**即对其施加"隐匿调查";2.0.0 开发线(`multi-dev-next` / `wt/2.0.0-vnext`)改经目标选择器选目标(可选=敌对生物或非队友玩家,无队伍时对所有玩家生效),效果相同;**判定期内 30 秒待命窗口不算本技能的计时器**(不进入锁定态,超时未命中仍不消耗冷却) | 关键线索:攻击带标记目标攻击力 +3;击杀不少于 20 血带标记敌对目标后获得一张随机攻击牌;击杀隐匿调查目标触发"调查阶段"事件;调查阶段隐身期间不被生物索敌 |
| 大当家 fen | 运功:1:00 内攻击力 +3;**触发后进入「锁定(生效中)」态,须等本技能施加的全部计时器跑完才进入主动技能冷却**(锁定期内按键无效并提示「主动技能生效中!」);拥有养精蓄锐则恢复 6 血 + 迅捷 1:00 | 养精蓄锐:层数 >0 时攻击力/防御力 +2;1 分钟未触发赐福 +1 层;触发赐福 -1 层;触发赐福时养精蓄锐已满 5 层 → 消耗 2 层并**立即**对目标及其 6 格范围内的敌对目标造成本次攻击伤害 88%(下限 5 点)的**真实伤害**;使用治疗类效果牌 +1 层;**玩家死亡时清零** |
| 骇客 nancy_lu | 远程侵入:立即进入**完全隐身**(最多 30 秒;隐身效果实例 `visible=false` 不冒药水粒子,并由客户端 `client/NancyLuClientEvents`(`RenderPlayerEvent.Pre` + `RenderHandEvent`)抑制自身盔甲/手持/Curios 装饰渲染——原版隐身只隐藏本体,渲染层不检查 `isInvisible()`;旁观者视角不在本批范围内);**任何攻击行为(近战/远程/投掷物/魔法)**命中敌对目标或玩家时解除隐身,消耗主物品栏中一张随机战斗牌并按该牌费用 ×2(最低 +2)提升攻击力,持续 2:00(无战斗牌可消耗时同样获得保底 +2);**触发后进入「锁定(生效中)」态,须等本技能施加的全部计时器跑完才进入主动技能冷却**(锁定期内按键无效并提示「主动技能生效中!」) | 网络防火墙:免疫末影珍珠传送摔落伤害(**在伤害判定最前置处取消**——1.21.1 `LivingIncomingDamageEvent`、1.20.1 `LivingAttackEvent` + `setCanceled(true)`,使 `LivingEntity.hurt` 直接返回 false,无红屏/屏幕震动/受伤音效与无击退同步;旧实现只把伤害改成 0,`hurt` 仍走完全流程);骰神赐福结束后,若周围 6 格内无敌对生物则攻击力 +3 并获得一张随机战斗牌,否则防御力 +3 |
| 枪匠 moses | 弱点反击:按下主动进入 **30 秒待命**,待命期内攻击**符合骰神赐福触发条件的目标**(近战武器 + `isBlessingTarget`,不额外限制"普通敌对生物")施加破绽 2:00(未触发不冷却;被动精密技巧使冷却 120 秒;**所有按比例减免——加急加快 / 命运指引 / 电流核心消耗档位——一律以「起冷却时记录的实际基准」为准**(附件 `sign_active_max_cooldown`),不再按 180 秒过度减免、也不会再出现「免费立即完成」;史莱姆的 −10 秒为**绝对量**(与基准无关),同批修复的是冷却哨兵值判定);2.0.0 开发线(`multi-dev-next` / `wt/2.0.0-vnext`)改经目标选择器选目标;**判定期内 30 秒待命窗口不算本技能的计时器**(不进入锁定态,超时未命中仍不消耗冷却) | 弱点识破:任意来源闪避/反击或攻击带破绽目标获得 1 层(每目标闪避/反击限一次,最多 4 层);每层攻击/防御 +1、骰点最低数 +1;赐福结束减 1 层 |
| 肉弹战车 pandaman | 大吃特吃:获得随机治疗类效果牌(汉堡/巧克力蛋糕/奢华大餐);对 16 格内敌对目标施加嘲讽 1:00(**嘲讽只对敌对生物生效,永不会施加给玩家**);被嘲讽目标攻击施加者时触发反击(不消耗反击层数) | 有好有坏:汉堡 +2 最大生命(≤100,卸下清除);汉堡/巧克力蛋糕使 8 格友方获得 2 点治疗与 1 点治愈;反击时生命未满附加缺失生命等值伤害 |
| 风水师 zhao | 白泽赐福(**目标选择器类**,2026-09-26 新增):选择 16 格内的玩家(鼠标右键可对自身使用)⇒ 目标获得「白泽赐福」—— 效果期内该玩家**溢出治疗等量转为攻击力**,持续到**下一次骰神赐福结束**后消失;施法者同时获得 1 张符卡-福并把自身**全部**符卡-祸转为符卡-福。**按下主动只开会话、确认前不进冷却**;冷却与电流核心写在动作自己的 `apply` 里;**不起锁定(生效中)态**(本技能不施加任何自己的计时器 ⇒ `startActiveLockOnUse` 走默认 false,与「三态化」无关) | 福祸相倚:骰战**定稿后的最终骰点** 1 ⇒ 1 张符卡-祸、6 ⇒ 1 张符卡-福(发放即绑定获得者;**一次结算只判定一次**);完美帮手:对**装备大当家立牌**的玩家施加白泽赐福时,该玩家 +1 层养精蓄锐(复用大当家既有附件计数,不新建效果) |

### 立牌品质（配方决定）

| 配方(骰子 + 星盘) | 品质 | 立牌 |
|---|---|---|
| 基础骰子 | 稀有(RARE) | 经商 parunan、扫地机 jasmine、看板娘 mimi、肉弹战车 pandaman |
| 基础骰子 | 史诗(EPIC) | 游戏大师 ren(2026-09-25 用户裁决:配方仍在基础骰子档,品质改史诗) |
| 黄金骰子(无星盘) | 稀有(RARE) | 史莱姆 lulu、上班族 padman |
| 黄金骰子 + 星盘 | 史诗(EPIC) | 大侦探 fanny、吸血鬼 papara |
| 钻石骰子(±星盘) | 史诗(EPIC) | 忍者 komachi、占星师 haiqing、骇客 nancy_lu、枪匠 moses |
| 钻石骰子 + 黄金星盘 | 传奇(UNCOMMON) | 调查员 rin、护法 misaki、大当家 fen、风水师 zhao |
| 下界合金骰子 + 黄金星盘 | 传奇(UNCOMMON) | 秘密侦探 bonnie |

### 已统一命名的效果/数据（参考）

- 效果注册 id(**两个发布子项目在本树内实测各 34 个**,完整以 `effect/ModEffects.java` 为准;**两侧注册条数与 id 集合一致,但不是逐行相同** —— 平台包装类型不同:neo 用 `DeferredHolder<MobEffect, MobEffect>`、forge 用 `RegistryObject<MobEffect>`(文件行数 159 vs 158;归一包装名后仍有 9 行差异),核对时请按 id 集合而非逐行):`misaki_burst`(爆发)、`papara_bite`(汲取;2026-09-12 由「嘬一口」更名,主动技能名「嘬你一口」不变)、`jasmine_sweep`(清扫)、`magic_tome_count`(魔法秘典计数)、`fen_frenzy`(战斗爽)、`nancy_lu_hack`(远程侵入)、`weak_mark`(虚弱印记)、`weakness_reveal`(弱点识破)、`undercover_investigation`(隐匿调查)、`investigation_bonus`(调查阶段加成)、`fate_guidance`(命运指引)、`healing`(治愈)、`cutter_ready`/`cutter_blade_ready`(美工刀)、`revenge_halberd`(复仇之戟)、`blue_curse`(青之诅咒)、`berserk`/`king_power`/`unwavering`(功能效果牌)、`monster_laser`/`monster_brick`/`orbital_strike`/`directional_blast`(法伤牌加成)、`living_page`(活体书页)、`dice_blessing`(骰神赐福)、`marked`(标记)、`pandaman_taunt`(嘲讽)、`charge`(充能)、`moses_broken`(破绽)、`empower`(赋能)、`ren_shield`(鼠鼠护盾)、`ren_counter`(反击;层数附件的可见镜像,图标 `images/反击.png`)、`zhao_blessing`(白泽赐福;32→34 的两个新效果之一,图标**复用风水师立牌贴图**)、`misfortune`(厄运;层数镜像效果,图标 `images/厄运.png`)。**26.1.2 线曾为例外，2026-09-19 已收口**：`neoforge-26.1.2` 原为旧「待命」等待器实现(自带该线本地 `effect/ReadyEffect`),实测 **33** 个,比两个发布线多 `haiqing_ready` / `bonnie_ready` / `moses_ready`;**2026-09-19「立牌主动前置门控收口」移植到该线后为 32 个、id 集合与两个发布线逐项一致**(该移植同时删掉 `BaseSignItem.isSkillWaiting`/`tickSignReadyTimeout` 及其调用点、本地 `effect/ReadyEffect.java`、三个立牌子类的 `READY_TYPE` 与 `addEffect(…,MAX_VALUE)`、`AstralPartyCommand` 的伴生清理,把 `handleUse` 从 `abstract` 改为发布线同款「默认实现 + WARN」,并按实测删除该线多出的 3 个 `effect.astral_dice.*_ready` lang 键与 3 张 `mob_effect/*_ready.png` ⇒ lang 键 **686→683**、`textures/mob_effect/` **35→32**,两侧一致);主线分支 `multi-1.20.1-1.21.1` 的 1.21.1 子项目仍为 **33** 个(该三件套由本合并 KI-M1 只在两个发布线移除,见 KNOWN-ISSUES)。⚠️ 该移植还证明一条硬约束:**`@EventBusSubscriber` 不能留在「零 `@SubscribeEvent` 方法」的类上**(`net.neoforged:bus:8.0.5` 对这种情况直接抛异常)⇒ 删掉某个类里全部订阅器时必须同时去掉注解。
- 数据组件注册 id(共 10 个,完整以 `component/ModDataComponents.java` 为准):`misaki_sign_stacks`、`card_uses`(战斗牌剩余次数)、`weapon_enhancement`(**骰子**铁砧升星 + 已装配卡牌/费用配置;立牌无此组件)、`owner_uuid`(专属牌绑定)、`jasmine_atk_bonus`/`jasmine_def_bonus`、`padman_atk_bonus`/`padman_def_bonus`/`padman_last_refresh`/`padman_force_six`
- 玩家附件注册 id(共 **78** 个;1.20.1 为 **80** 个,多 `damage_effect_bonus` 与 `curse_original_amount`(**2026-09-26 本批新增 7 键后重新计数:1.21.1 = 78、1.20.1 = 80;2026-09-25 的计数为 71/73(1.21.1 = 67、1.20.1 = 69 + 游戏大师 ren 的 4 键);此前记的 62/64 为「立牌主动技能三态化」之前的数字,当时新增的 5 个锁定态键两版本均有,不属差异项;上批新增的 `sign_active_max_cooldown` 同样两版本均有**),完整以 `component/ModAttachments.java` 为准;1.20.1 的 29 个 synced 键见上方「forge-1.20.1 子项目关键差异速记」):忍者 `komachi_use_count`/`komachi_last_card`/`komachi_damage_bonus`(旧 `komachi_extra_plays` 出牌银行附件已删除)、治愈 `healing_points`/`healing_prev_blessing`/`healing_timer_end`(见「治愈流派规范」)、调查员 `rin_pages`/`rin_gift_signature`/`rin_gift_tick`、骇客 `nancy_lu_passive_type`/`nancy_lu_active_bonus`/`nancy_lu_active_bonus_until`/`nancy_lu_hidden_until`/`nancy_lu_ender_pearl_immune_until`、大当家 `fen_recharge`/`fen_last_blessing_tick`、看板娘 `mimi_returned_card_count`、枪匠 `moses_broken_attack_rewarded`/`moses_dodge_counter_rewarded`、肉弹战车 `pandaman_max_health_bonus`/`pandaman_taunt_source`、魔法秘典 `magic_tome_use_count`/`magic_tome_last_card`、来源 `weak_mark_source`/`undercover_source`、`investigation_stage`(调查阶段)、`player_starlight`(星光)、`sign_active_cooldown_end`(立牌主动冷却,玩家级)、`sign_active_max_cooldown`(立牌主动技能**本次冷却实际使用的最大冷却 tick**,是全部冷却减免方的唯一基准;**仅服务端使用、不 `.sync()`**。**语义已变更(2026-09-25「立牌主动技能三态化」)**:旧写法是"由 4 个「开始冷却」入口与 `sign_active_cooldown_end` **成对写入**";现改为「**锁定开始时先写基准**(供锁定期间各减免方读它并累加进减免池);**真正进入冷却时按 `max(0, 基准 − 减免池)` 抵扣后改写并把冷却起点设在当前时刻**」——即"锁定态下 `cooldown_end` 为 0,基准先写、抵扣后改写",**不再与 `cooldown_end` 成对写入**。三态判定入口 `BaseSignItem.isSignActiveLocked`(**硬上界 `sign_active_lock_end` 未到 且 本立牌门控效果实例仍在**;外部把同一效果刷新得更长**不会**延长锁定,因为硬上界不随刷新重算;`lock_end==0` 的忍者只按锁定标记判定)、结束迁移 `endLockAndStartCooldown`)、`sign_active_cooldown_end`(立牌主动冷却,玩家级)、**锁定态 5 键(均玩家级、均不 `.sync()`)**:`sign_active_lock_sign`(锁定中的立牌注册 id,**全限定**如 `astral_dice:komachi_sign`;`""` = 未锁定)、`sign_active_lock_end`(门控计时器**硬上界**:触发时刻算定的本技能施加的全部计时器到期刻取 max;`0` = 无自身计时器,仅忍者)、`sign_active_reduction_pool`(锁定期间累计的冷却减免池,进入冷却时一次性抵扣并归零)、`sign_active_lock_grace_end`(忍者 1:00 宽限到期刻;`0` = 宽限已失效/不适用)、`sign_active_lock_played`(忍者宽限期内是否出过效果牌;`true` = 宽限保险失效、改为遵循出牌周期)、`sign_ready_type`/`sign_ready_expire`(待命等待器:当前主线在用,见下方「需指定目标的技能」)、`magic_quiver_tracking`/`magic_quiver_first_card`/`magic_quiver_cooldown_end`、`buffer_shield_cooldown_end`、`star_coin_hammer_bonus`、`cursed_sword_bonus`/`cursed_sword_blessing_triggered`、`candy_chip_play_bonus`、`satellite_give_cooldown_end`/`satellite_play_bonus`/`satellite_play_bonus_cooldown_end`、`fight_poison_with_poison_regen_at`、`effect_timer_ends`(计时器守卫)、`defense_card_consumed_blessing`、`warp_engine_portal_cooldown_end`、`ender_die_totem_cooldown_end`、`guide_book_given`(首次加入赠书)、`eight_sided_roll_accum`、`damage_effect_bonus`、`dice_curse_ratio`、`effect_card_cooldown_end`/`effect_card_play_count`/`effect_card_bonus_plays`(出牌轮一次性 +1 标记)、`fate_active_until`、`lulu_last_hurt_tick`、小猪存钱罐 `piggy_bank_use_count`、`flashlight_granted_targets`(手电筒已发放目标)、`airbag_cooldown_end`(安全气囊冷却)、`electric_glove_aoe`(电击手套法伤扩散武装)、`railgun_cooldown_end`(电磁炮冷却)、`empower_decay_at`(赋能递减计时)、`living_page_cycle_bonus`(活体书页本周期出牌数加成)——以上 6 个为**两版本共有**(此前漏列);`curse_original_amount`(青之诅咒原始值)为**仅 1.20.1**。**2026-09-26 本批(风水师 zhao)新增 7 键,两版本均有且键名逐字相同**:`zhao_blessing_active`(是否持有白泽赐福的真值)/`zhao_blessing_skip_cycles`(两分支用的跳过计数)/`zhao_prev_blessing`(上一 tick 的骰神赐福真值,下降沿基准)/`zhao_overflow_bonus`(Int,溢出治疗转来的攻击力加分)/`zhao_overflow_remainder`(Float,取整余数累加器)/`huo_card_next_damage_tick`(Long,厄运下一次周期伤害的绝对刻) + 出牌轮 `fu_card_cycle_bonus`(符卡-福本周期出牌数加成;**本批唯一 `.sync()` 的新键** —— 1.21.1 内联 `.sync`、1.20.1 经 `SYNCED_KEYS`,依据是客户端预检 `isBlockedOnClient → isBurstFull → getMaxAllowed` 必须在本轮上限上看到这份额外 +1,否则客户端会误判「已打满」)。**死亡重生保留的键为 `rin_pages`、`komachi_damage_bonus` 与 `guide_book_given` 三个**,由**两层机制**共同保证(2026-09-15 用户裁决,必须遵守):① **克隆复制**——1.21.1 经 `AttachmentType.Builder#copyOnDeath()`、1.20.1 经 `AstralData#onPlayerClone` 的死亡分支复制这三个键(两侧键集合必须一致);② **死亡暂存/重生回写**——`component/DeathPreservedBonuses`(`LivingDeathEvent` 暂存;`PlayerEvent.Clone` 以 `priority=LOWEST` 回写、`PlayerRespawnEvent` 兜底,表项取走即空操作)。**为什么必须两层**:默认 gamerule(`keepInventory=false`)下死亡会把立牌从饰品槽丢出,Curios 的 tick 轮询判定"已离身"→ `onUnequip` → 各立牌 `clearSignData` 把这两个键清零,且**早于**克隆复制——只做①会被抢先抹掉(测试世界是 `keepInventory=true`,永远测不出这个坑);回写 handler **必须保持 LOWEST**,否则会被克隆复制覆盖成 0。其余键与 1.21 附件默认行为一致——死亡不复制;死亡清理(`LivingDeathEvent`,在旧实体上执行)不得清除这些键(尤其**禁止清除 `guide_book_given`**——清了就必然在下次登录补发一本)。**《恋的规则书》的赠书守卫(2026-09-15 用户裁决,必须遵守)**:**仅在玩家第一次进入世界时发放一次,此后任何情况下都不得自动发放**——退出重进、**死亡重生后再次登录**、把书丢弃/放进箱子/销毁后重登,一律不得补发;守卫是附件 `guide_book_given`(发放前只查它,**不查背包**;`GameplayConstants.GIVE_GUIDE_BOOK_ON_FIRST_JOIN` 为**类初始化读一次**的静态开关,改配置须重启生效),因此它**必须在死亡保留集合内**(否则死亡后回默认 `false`,而发放挂在 `PlayerLoggedInEvent` ⇒ 下次登录必再发一本——2026-09-15 实测缺陷,已修)。注意手册物品本身是 `patchouli:guide_book`,`astral_dice:astral_guide` 只是数据组件里的书籍 id。此外**这两个值只在佩戴对应立牌时才作为加成生效**:唯一判定入口是 `SpellDamageRegistry#effectCardDamageBonus`(忍者)与 `#livingPageBonusPages`(调查员),伤害结算与 tooltip 显示都必须走它们,禁止直接读原附件值做加成或显示加成(死亡掉落立牌后累计值保留但不再加成,直至重新装备)。
- 游戏大师立牌 ren 的 4 个玩家级附件(**均为非同步**,仅服务端使用;2026-09-25):`ren_shield_last_seen_tick`(最后一次「持有鼠鼠护盾」的世界时刻 —— 被动「鼠鼠救我」的 5 分钟计时基准;跨重登由 level.dat 的 Time 继续累加)、`ren_shield_baseline_absorption`(授予护盾时已有的吸收值基线:护盾只在此基础上 +10、清空时只回收这 10 ⇒ 不吞掉金苹果/不死图腾等外部来源)、`ren_shield_own_resistance`(那份抗性提升是否由护盾施加;清空时只移除自己那份,玩家药水/更高等级一律保留)、`ren_counter_charges`(一次性反击层,0/1)
- **游戏大师立牌 ren「鼠鼠护盾」口径(2026-09-25 用户裁决,必须遵守)**:①护盾 = **5 黄心(10 点吸收)** + 抗性提升 + **1 层一次性反击**;HUD 图标口径 = **护盾与反击显示图标、我们施加的那份抗性提升隐藏图标**(`showIcon=false`;`ren_counter` 只是层数附件的**可见镜像**,层数 0 就立即摘掉,层数附件 `ren_counter_charges` 才是真值)。②被动「鼠鼠救我」**只作用于佩戴者本人**(作用对象由佩戴判定决定,文案不再写「仅佩戴者本人」):佩戴 ren 且超过 **5:00**(`PASSIVE_INTERVAL_TICKS` = 6000)没有护盾时,自动获得 **1 张随机卡牌 + 鼠鼠护盾**(2026-09-25 用户裁决追加卡牌,与主动同口径;发奖入口 `RenSignItem#onPassiveTriggered`);计时基准 = 玩家级附件 `ren_shield_last_seen_tick` + `level.getGameTime()`(首次观察(值 ≤ 0)只把基准置为当前刻,故装备后满 5 分钟才首次补盾;卸下立牌**不**清已有护盾)。③黄心被打空 ⇒ 最多 1 tick 内清空(每 tick 轮询 `getAbsorptionAmount() <= max(0, 基线)`,覆盖 `/effect clear`/牛奶/死亡/他模组直改等**不派发伤害事件**的路径),清空内容 = 护盾效果 + 我们那份抗性 + 反击层(及其图标) + 基线,并按基线回收我们那份黄心。④反击 = 带盾被**非自身**生物攻击时消耗 1 层并复用既有 `injectCounterDamage` 注入一次反击伤害(**不新增反击流派/层数体系**,递归由 `isInCounterChain()` 结构性截断);NEO 用 `LivingDamageEvent.Pre`、FORGE 用 `LivingDamageEvent`,两线都保证「被黄心完全吃掉的一击同样触发」。⑤主动「熊孩子特权」= **目标选择器**选任意玩家或**自身**(`allowSelf=true`;自选放行做在消费方 `SelectorTargets` 4 参重载,不动前置库);**确认前不进冷却**,冷却与 `CurrentCoreChipItem.onActiveSkillUsed` 写在动作自己的 `apply` 里。⚠️ **选择器里的技能名显示「鼠鼠护盾」,而 tooltip/手册里的主动名仍是「熊孩子特权」**(`msg.astral_dice.target_select.skill.ren_privilege` 与 `tooltip.astral_dice.sign.ren_active` 两条键有意不同名)。⑥护盾外观 = **完整球体**(球心 = 脚底 + 身高 × 0.5、半径 1.2 ⇒ 完全包住 0.6×1.8 的玩家)、深青蓝、**加色混合**外层辉光壳、球面 UV 滚动 `textures/special/ren_shield.png`(六边能量单元,生成器 `tools/gen_ren_shield_texture.py`,可平铺)⇒ 动态纹理 + 沿极角上行的能量带;渲染条件源是**同步的 mob effect**(1.20.1 的附件同步只发给本人,不能用作条件源),第一人称**跳过自己**那一个(相机在球内会糊满整屏)。⑦用例:`REN-SHIELD-*`(真实等待 5 分钟的端到端被动 + 反击 + 清空 + 主动)、`REN-SHIELD-VISUAL-*`(第三人称截图取证,探针 `renprep`/`rengrant`/`renvoid`/`renread`;`renState` 读数含 `counterfx` 反击图标位)。
- 纹理:`textures/mob_effect/` 下与效果注册 id 同名(全量见目录,含 `misaki_burst.png`、`jasmine_sweep.png`、`papara_bite.png`、`nancy_lu_hack.png`、`fen_frenzy.png` 等)
- **效果/立牌/筹码图标取材规则(2026-09-13,必须遵守)**:效果纹理优先使用**该效果自带的专用图标**(如 `blue_curse` = `images/青之诅咒.png`);**没有专用图标时**才改用其载体物品的图标(立牌→`textures/item/<id>_sign.png`,筹码→`textures/item/<id>_chip.png`,效果牌→`textures/item/effect_card_<id>.png`)。新增效果时必须按此顺序取图,不得用占位图(感叹号/灰十字/黄菱形)。
- **本模组状态效果的施加必须用六参构造 `(…, ambient, visible, showIcon)`,且 `showIcon=true`(2026-09-16 修复,必须遵守)**:`MobEffectInstance` 的**五参构造** `(效果, 时长, 等级, ambient, visible)` 在字节码层面等价于 `showIcon = visible`(两版本合并 jar 的 `javap -c` 均为 `iload 5; iload 5` → 六参 `(…ZZZ)V`),因此「只想关掉药水粒子」而传 `visible=false` 会**连 HUD 图标一起隐藏**;而客户端 `Gui` 的 HUD 效果栏渲染**以 `showIcon()` 为门控**(1.20.1 `Gui` 约 149 行 / 1.21.1 `Gui` 152 行),物品栏的效果面板**不读 `showIcon`** ⇒ 症状是「物品栏能看到该效果、HUD 上看不到」（骰神赐福曾如此:只在物品栏可见、玩家无法判断赐福剩余时间）。口径:**关粒子、留图标** = `(…, false, false, true)`(充能/赋能/弱点识破/治愈/调查阶段/骰神赐福均如此);确需连图标一起隐藏(如标记伴随的 `GLOWING`、暗影突袭的 `DARKNESS`)才传 `false`。改任何 `new MobEffectInstance(` 后必须用 `javap -c` 核对发货 jar 里该调用前的 `iconst_*` 序列。
- 立牌主动技能冷却为**玩家级**(`sign_active_cooldown_end`,默认 180 秒),不随立牌装卸重置;**本次冷却实际使用的基准记录在 `sign_active_max_cooldown`**(枪匠 120 秒、诡异骰子减半、充能上限(160 秒)都在**起冷却时**写定),全部减免方一律读它作基准。**三态化后(2026-09-25)语义变更**:主动仍处于**锁定(生效中)**态时 `sign_active_cooldown_end` **保持为 0**(锁定期内按键无效,提示「主动技能生效中!」);基准在**锁定开始时**就写进 `sign_active_max_cooldown`,锁定期间各减免方只把减免**累加进 `sign_active_reduction_pool`**,锁定结束(门控计时器跑完,或忍者宽限/出牌轮完全重置)时由 `endLockAndStartCooldown` 一次性抵扣:`effective = max(0, 基准 − 减免池)` ⇒ 写 `cooldown_end = now + effective` 并把 `max_cooldown` 改写为 `effective`,随后清空全部锁定键。判定入口 `BaseSignItem.isSignActiveLocked`(硬上界 + 门控效果仍在;外部刷新更长**不延长**;`lock_end==0` 的忍者只按锁定标记)
- **立牌主动技能不再掷骰**:主动效果为直接效果或"随机获得以下任一效果"(代码内 ThreadLocalRandom 直接随机选,不再有 1d10 掷骰/骰点显示)。`roll_result`/`roll_cooldown` 数据组件已删除。
- **饰品卸下(`onUnequip`)的参数语义与已知缺口(2026-09-15)**:Curios 三参签名 `onUnequip(slotContext, newStack, stack)` —— **第 3 参 `stack` 才是被卸下的那件饰品**,第 2 参 `newStack` 是调用方传入的"将要占槽"的栈。本仓判据 `CurioSlotUtil.isIntentionalUnequip`:**第 3 参为空 ⇒ 不清理**(无可清理对象,同时杜绝把组件写进 `ItemStack.EMPTY` 全局单例);第 2 参为空、或与被卸下者**不是同一物品** ⇒ 真实移除 ⇒ 清理;**同物品但组件变化 ⇒ 视为 Curios 自身重载、不清理**。**为什么必须有这一支**:本模组自己在 `curioTick` 里往**装备中的栈**写组件(护法剑气/扫地机移动累计/上班族攻防),Curios 的 tick 轮询会把这类写入判为"变化"而触发一次 onUnequip + onEquip,无条件清理会让这些累计值被自己的写入反复清零。**已知缺口(2026-09-15 逐条核对字节码后如实记录;**仍未修复;用户已裁定「接受现状、只写文档、不引入 mixin」,理由见本段末尾**)**:Curios 的 tick 轮询调用的是 `prevCurio.onUnequip(ctx, 槽位**当前**内容)`,桥接 `ItemizedCurioCapability#onUnequip` 再以「第 2 参 = 调用方传入的那个栈、第 3 参 = `getStack()` = `getPreviousStackInSlot` 留下的 **copy 快照**」进入本模组 ⇒ **GUI 卸下**时第 2 参已是 `EMPTY`(物品已离开槽位)、第 3 参是副本,故护法剑气 / 扫地机 / 上班族的**物品组件**归零写进副本会丢失(玩家侧清理如护甲修饰器/移动累计表仍照常执行;**死亡路径已覆盖** —— `PlayerLifecycleHandler` 在 LOWEST 按 `findFirstCurio` 直接清装备中的栈)。**「让 tick 路径操作槽内真实栈」经证实不可行**:该回调触发时槽位已空(没有真实栈可操作),而被卸下的那件在容器点击中已被 `ItemStack#split/copyWithCount` **复制**后放进背包,回调内既不能按引用也不能按槽位取到它 ⇒ 只能改用「卸下前清理(`ICurio#canUnequip`/`DynamicStackHandler#extractItem` 在提取**之前**回调,但该回调在 `simulate=true` 的提取尝试中也会触发)」「装备时清理(`onEquip` 的 tick 路径第 3 参才是真实现槽内栈,可达但只保证"不再继承旧累计")」「背包按值反查(启发式)」三者之一。**用户最终裁定(2026-09-15):接受现状,只写文档,不引入 mixin。**裁定依据(均为字节码级取证,全文见本地文档 `docs/batch3/D-B1-optionA-proof.md` 与 `docs/batch3/D-B1-BC-inject-proof.md`):① **「卸下前清理」已证不安全且不可用** —— `DynamicStackHandler#extractItem` 的 `simulate` 形参直到方法末尾才被消费(N 偏移 177 / F 174),`CurioCanUnequipEvent`/`ICurio#canUnequip` 在其**之前无条件触发**,而 `SlotContext`(identifier/entity/index/cosmetic/visible)与事件载荷(slotContext+stack+result)**都不含** simulate;Curios **自身**即把 `simulate=true` 当查询(`SlotItemHandler#mayPickup` N L65-66 / F L102-104 → `extractItem(i,1,true)`,且 `CurioSlot` 未覆写 `mayPickup`,原版 `doClick` 在 QUICK_MOVE/PICKUP/SWAP/PICKUP_ALL **六处**调用它)⇒ **点一下饰品格即会误清累计**。② 其余可行组合均需 **2~3 处 mixin 深入 Curios 与原版容器内部**(B = `DynamicStackHandler#extractItem` 的 super 调用**之前**,N 偏移 178 / F 175,此处槽内仍是 `NonNullList.get` 取出的真实栈;C = `CurioSlot#set` HEAD,F 显示名 `m_5852_`,偏移 0-4 的 `getItem()` 全程无 `copy()`;D = `ItemStackHandler#setStackInSlot` HEAD + `instanceof DynamicStackHandler` 守卫;E = `AbstractContainerMenu#moveItemStackTo` HEAD 补 shift 快捷移动),且即便全上仍有 `CPacketDestroy` 因 `isIntentionalUnequip` 判「同物品」而跳过(该缺口已记于 `item/CurioSlotUtil.java:120-121`)。BREAK(耐久耗尽)无需覆盖。③ **缺口实际影响很小**:后果仅为「经 GUI 摘下立牌后,护法剑气 / 扫地机累计 / 上班族攻防的旧值仍留在物品上,重新戴上会继承」,不影响对局正确性 ⇒ **不值得以 3 处 mixin 换取**。**禁止**再以「让 tick 路径操作槽内真实栈」或「卸下前清理」为方向实施。
- **需指定目标的技能按分支有两套实现**:`multi-1.20.1-1.21.1`(当前主线)为**"待命"等待器**——占星师/秘密侦探/枪匠按下主动后进入 30 秒等待期(`GameplayConstants.SKILL_WAIT_SECONDS`,附件 `sign_ready_type`/`sign_ready_expire` + 提示效果 `haiqing_ready`/`bonnie_ready`/`moses_ready`),等待期内攻击目标即释放对应效果;**2.0.0 开发线(`multi-dev-next` / `wt/2.0.0-vnext`)**改为目标选择器会话(`TargetSelectionManager`,见「目标选择器规范」),该开发线已废弃等待器。**待命超时归零改由玩家级 tick 负责(2026-09-15)**:`BaseSignItem.tickSignReadyTimeout` 挂在 `event/PlayerTickEvents`,与立牌是否仍在饰品槽**无关**(原先写在各立牌自己的 `onCurioTick` 里,死亡掉落等不回调 `onUnequip` 的路径会让残留计时器永远成立);主动技能冷却的门槛**只看 `sign_ready_type`**——**残留计时器不再导致主动技能永不冷却**(`isSkillWaiting` 语义不变,仍要求"待命且未过期")。
- **立牌联动规则**:大侦探(fanny)与秘密侦探(bonnie)之间**无主动技能联动**(fanny 不再因附近有 bonnie 而能抽取"调查阶段"事件);但 fanny/bonnie 触发事件时均会触发调查员(rin)立牌被动(`AstralEventSystem.applyRinSignPassive`,佩戴 rin 的玩家获活体书页)。"调查阶段"事件仅由 bonnie 击杀"隐匿调查"目标触发。
- **友方/队伍发放规则**:所有“向友方/队友发放奖励或加成”的判定统一为——触发者已加入队伍时只影响同队/队友(EventTargetCollector 收集 MC/FTB/OPAC);**未加入任何队伍时,目标改为全服在线玩家**(核心在 `EventTargetCollector.collectTeamPlayers`,银行卡/调查员 rin/真相揭露等均经此统一判定)。
- **调查员给牌去重**:`applyRinSignPassive(triggerer, eventId)` 按"触发者 UUID + 事件 ID"签名去重——同一事件在 2 tick 窗口内被重复分发(如多立牌槽导致 onKill 多次调用)时每个 rin 玩家只获得一张活体书页。触发入口必须携带独立事件 ID:调查阶段=`"investigation"`、大侦探主动=`"fanny_active"`、通用事件=`type.id().getPath()`;去重附件 `rin_gift_signature`/`rin_gift_tick`。

### 风水师立牌(zhao)与符卡-福/符卡-祸实现契约(2026-09-26)— 必须遵守

> 本批在**两条发布线**落地(`neoforge-1.21.1` + `forge-1.20.1`);`neoforge-26.1.2` 因用户裁决**冻结**(`127fabd5`)、本批**未改动**,待迁移清单见「模组内容更新规则(三线同步)」段首的冻结清单追加项。独立验证结论 = `scripts/test/TESTING-SPEC.md` 附录 A 续 25(t4:无 blocker / 无 high)。

1. **id 与注册点(冻结值,不得改名)**:物品 `zhao_sign`(风水师立牌,传奇 = 物品层 `Rarity.UNCOMMON`)/`fu_card`(符卡-福)/`huo_card`(符卡-祸)在 `item/ModItems` 注册 + `init/ModCreativeTabs` 入创造栏 + `datagen/ModItemModelProvider`、`datagen/ModRecipeProvider`(配方 = 钻石骰子 + 黄金星盘档,shape 里立牌骰子固定中下)+ 标签四处(`data/astral_dice/tags/{item|items}/signs.json` 立牌汇总、`data/curios/tags/{item|items}/stand.json` 饰品栏、**`effect_cards.json`** 与 **`is_exclusive.json`** 两张符卡);效果 `zhao_blessing`(白泽赐福,`effect/ZhaoBlessingEffect`)/`misfortune`(厄运,`effect/MisfortuneEffect`)在 `effect/ModEffects` 注册(两线效果注册 **32 → 34**);玩家附件 7 键(两线同名同型,见「玩家附件注册 id」条目);手册 `assets/astral_dice/patchouli_books/astral_guide/en_us/entries/{signs/zhao_sign,cards_effect/{fu_card,huo_card}}.json`(中英文案键 `astral_dice.guide.entry.*`);tooltip 冻结键 = `tooltip.astral_dice.sign.{zhao_active,zhao_passive,zhao_cards}`、`tooltip.astral_dice.sign.fen_xinyi`(大当家侧)、`tooltip.astral_dice.{fu_card,huo_card,huo_card_curse}`(**无 `.card.` 段**;⚠️ `huo_card_no_drop` 已随 2026-09-20「禁止丢弃」移除而**不再是冻结键**)。图标:`textures/item/{zhao_sign,fu_card,huo_card}.png` + `textures/mob_effect/{zhao_blessing,misfortune}.png`,均取自仓库根 `images/` 同名文件;**白泽赐福复用风水师立牌贴图**(无专用图)。
2. **主动「白泽赐福」= 目标选择器技能,动作 id `zhao_blessing`**:`TargetSelectionAction` + `SelfTargetable`,`TargetType.PLAYER` + `allowSelf=true`(可选**任意玩家或自身**);门控只覆写 `selectorActionId()`(按下主动只开会话并给「请选择目标」提示),**确认前不进冷却**;冷却(统一入口 `signCooldownTicks`,含诡异骰子 -50% 与充能封顶)与 `CurrentCoreChipItem.onActiveSkillUsed` 写在动作自己的 `apply` 内。本技能**不施加自己的计时器** ⇒ 不覆写 `startActiveLockOnUse`(默认 false)、**不起锁定态**,确认后立即起冷却。
3. **白泽赐福的移除时机(需求「持续到下一次骰神赐福结束」= 两分支;判定点唯一 = 玩家级 tick 下降沿)**:

   | 施加一刻目标身上 | `zhao_blessing_skip_cycles` | 移除时机 |
   |---|---|---|
   | **没有**骰神赐福 | 0 | 该次骰神赐福进度**结束后**(下降沿那一刻)立刻移除 |
   | **已有**骰神赐福 | 1 | **跳过当前这一次**结束,等**下一次**骰神赐福结束后移除 |

   边界(全部必须保持):① 下降沿判据 = `zhao_prev_blessing == true && 当前 hasEffect(DICE_BLESSING) == false`,由 `event/PlayerTickEvents` **每 tick** 驱动 `ZhaoSignItem#tickBlessing`;**禁止**为同一次结束再订阅 `MobEffectEvent.Expired`(会重复消费 skip ⇒ 赐福提前结束);`DiceCombatEvents#onDiceBlessingExpired` 的既有语义属**骰神赐福自身**(两线各 9 处),不得改动或误删。② 效果时长 = `Integer.MAX_VALUE`(**常驻、无自身倒计时**),tick 每拍 `refresh` 补齐。③ 效果被外力移除(如 `/effect clear`)⇒ tick 自检复位真值并回收溢出;`MobEffectEvent.Remove`(经本模组统一移除通道)即时回收,**与自检互为双保险、两者都幂等,且都不消费 skip**。④ **死亡清场**(`PlayerLifecycleHandler` 死亡段,与骰神赐福同段)与**重登复位**(重登清骰神赐福同一段):两者都移除效果 + 复位三键 + 溢出归零;**差别** = 重登**保留**厄运(真值 = 持有张数,由玩家级 tick 重新镜像),死亡额外清厄运与 `huo_card_next_damage_tick`。
4. **溢出治疗 → 攻击力**:`LivingHealEvent` @`LOWEST`(两线各自的事件类,语义同一)⇒ 溢出 = 请求治疗量 − `min(请求量, 最大生命 − 当前生命)`;`isCanceled` 直接返回;**双前置** = 附件真值 `zhao_blessing_active` **且** 效果实例存在于身上;溢出 ≤ 0(含刚好回满)**不写入**。整数化:`addZhaoOverflowBonus` 把整数部分进 `zhao_overflow_bonus`、小数尾数写回 `zhao_overflow_remainder`(**不静默丢数**,余数可与后续溢出凑成新整数点);消费点 = `combat/DiceCombatModifiers` 的攻击力加算项;回收动作**唯一**(`clearZhaoOverflowBonus`),在「状态机收尾 / tick 自检 / `MobEffectEvent.Remove` / 死亡与重登」四条路径上幂等调用。**已登记边界**:直接 `setHealth(...)` 绕过 `heal()` 的路径不产生溢出(本仓加血入口一律走 `heal()`)。
5. **厄运层数镜像规则**:层数(`amplifier + 1`)**恒等于**当前持有的符卡-祸张数,计数源**只取主物品栏** `player.getInventory().items`(与 `SmartWatchChipItem#countCards` 同口径 ⇒ **放进普通容器后不再计入**厄运与周期伤害);镜像由 `HuoCardItem#tick` **每 20 tick** 做一次,层数未变不写(避免无谓同步);**降层必须「先移除再施加」**(原版 `MobEffectInstance#update` 只接受更高 amplifier,否则可见层数永不下降);张数归 0 ⇒ **移除效果且把 `huo_card_next_damage_tick` 一并归 0**(下一次持有重新起算)。
6. **周期伤害(计时器与结算分离)**:间隔 `CURSE_PERIOD_TICKS = 2400`(2:00);计时器只在「张数 0 → &gt;0」时起算一次,**之后只由结算推进** —— 张数在 &gt;0 区间内任意增减(1↔5)**都不改写计时器**;伤害 = **结算时刻**的张数、真伤、无来源实体(击杀归属仅记玩家自身施加的真实伤害口径不适用 ⇒ 不重走命中判定);追赶上限 = 离线/卡顿导致到期刻已过很久时**只补结算一次**并把计时器重定到 `now + 2:00`。**禁止丢弃已移除(2026-09-20 用户指令,整套清空)**:原三通道 —— `onDroppedByPlayer` 返回 false 拒绝、`canFitInsideContainerItems`(1.21.1 两个重载 / 1.20.1 一个)返回 false 拒绝收纳、`ItemTossEvent` 兜底取消并**退还** —— 连同两个 lang 键、手册半句、`ModTooltipHandler` 的两处 tooltip 行与全部注释**一律删除**;本牌现可自由丢弃与收纳,持有代价只余上面的厄运层数与 2:00 周期伤害(放进容器即不计入)。**重建用例时不写「丢弃被拒」类断言。**
7. **被动「福祸相倚」**:挂点 = `combat/DiceCombatEvents` 在攻击方骰点**定稿之后**(含上班族立牌的强制 6 等修正之后)调 `ZhaoSignItem#onDiceRollResult`;1 ⇒ 1 张符卡-祸、6 ⇒ 1 张符卡-福;**一次结算一次判定**由 `tryClaimDiceJudgment`(纯静态槽位,不写附件、不持久化;同一玩家同一 tick 的第二次判定视为同一次结算)**必须**先经它再发牌,否则同一挥击命中 N 个目标会发 N 张牌(1.20.1 侧此前缺该守卫,已按 1.21.1 同形补齐)。
8. **专属绑定策略**:数据层 `ModDataComponents.OWNER_UUID` + `item/card/ExclusiveCardUtil`;`FuCardItem#give`/`HuoCardItem#give` **发放即绑定获得者**,`bindIfAbsent` 只在首次使用时兜底;拒绝非获得者**双处**:客户端预检与**服务端权威**都由基类按 `ExclusiveCardUtil#canUse` 拒绝,且**手持即选择**路径连选择会话都不开(`tickHeldSelector` 不 started);专属注册 = `RandomCardHandler.registerExclusiveCard`(随机卡牌池强制排除)+ `data/astral_dice/tags/{item|items}/is_exclusive.json`(**当前 4 项** = `effect_card_living_page` / `effect_card_fate_guidance` / `fu_card` / `huo_card`);**专属效果牌一律不进赏金池**(见「Bountiful 联动」小节)。**获取途径只剩创造栏与对应立牌发放**(骰点 1/6 的被动、主动白泽赐福、大当家心意相连),配方与战利品表均无。
9. **被动「完美帮手」= 复用既有附件计数,不新建效果**:对**装备大当家立牌**(`FenSignItem.isEquipped`)的玩家成功施加白泽赐福时调 `FenSignItem.addRecharge(receiver, 1)`(唯一写入入口,封顶 `MAX_RECHARGE = 5`),与「使用治疗类效果牌 +1 层」共用同一附件 `fen_recharge` ⇒ **不得**新增养精蓄锐效果类、图标或第二套层数。
10. **「心意相连」(大当家侧调用)**:触发点 = `FenSignItem` 主动技能**真正成功之后**调 `ZhaoSignItem#onAllyActiveSkill`;**必须先判 `EventTargetCollector.hasAnyTeam`**(这一步不能省 —— 否则库的「未组队 ⇒ 全服在线玩家」既有回退会把它放大成全服发牌),再取 `EventTargetCollector.collectTeamPlayers` ∩ `isEquipped`,`ally == fenOwner` 一律排除;**未组队时不生效**(返回 0)。只读读数辅助 `linkedReceiverIds`/`teamGateOpen` 与发放同一套闸门(只读、不写状态)。
11. **目标选择器的可选目标集合(冻结)**:

    | 动作 id | `TargetType` | `allowSelf` | 玩家可见口径 |
    |---|---|---|---|
    | `zhao_blessing` | `PLAYER` | true | 16 格内的**任意玩家(不限队伍)或自身** |
    | `fu_card` | `PLAYER` | true | 同上(符卡-福;**手持即选择**,无倒计时) |
    | `huo_card` | `ENEMY_OR_RIVAL` | false | 敌对生物 ∪ 已被激怒的中立生物 ∪ **非同队玩家**;**队友玩家 / 被动生物 / 自己不可选** |

12. **两线平台差异与已登记的未对齐项**(差异必须逐条登记,不得用「能跑就行」代替):① 附件形态 —— 1.21.1 `AttachmentType`(`fu_card_cycle_bonus` 内联 `.sync`)↔ 1.20.1 `AttachedDataKey` + `SYNCED_KEYS`(**29 条**),键名/类型/12 个访问器签名逐字相同;跨线集合差仪器 `temp/t75/parity-zhao.ps1` 基线 `PARITY_FAIL=0`。② 符号差异 = 冻结键名同形 + 各类同形(`tryClaimDiceJudgment`/下降沿/幂等回收),1.20.1 额外用 `MobEffectEvent.Expired` 处**不**新增(两线各 9 处既有语义未改)。③ **未对齐项(t4 findings,已登记待裁决,本批未改代码)**:VER-01 英文卡名两线分歧(1.20.1 = `Fortune / Misfortune Talisman Card`,1.21.1 冻结值 = `Blessing / Misfortune Talisman`;19 个 en 键值、zh 1 处);VER-02 **已失效（2026-09-20 随「禁止丢弃」移除）**：两线 `guide.entry.huo_card.2` 现均只写「存入容器后不再计入厄运与周期伤害」；VER-03 `FuCardItem.countFu` 口径分歧(1.21.1 仅主物品栏 ↔ 1.20.1 主物品栏 + 副手,而立牌 tooltip 两栏同屏显示);VER-04 **已失效（2026-09-20）**：被拒丢弃的 actionbar 反馈路径与 `onDroppedByPlayer`/`ItemTossEvent` 一并删除,该差异不存在了。④ 用例资产:1.21.1 三条(`ZHAO-SIGN-1.21.1` / `ZHAO-BLESSING-1.21.1` / `HUO-CURSE-1.21.1`)、1.20.1 一条合并(`ZHAO-SIGN-1.20.1`),**两线探针命令集不同**(1.21.1 19 条 `zhao*` ↔ 1.20.1 9 条)⇒ 本批**未**满足 §13.2「同探针 + 同用例 + 两侧读数 diff」的字面条件,跨线一致性以集合差仪器 + 独立代码验证给出,覆盖缺口见附录 A 续 25 的「验证边界」。

### 新增立牌时的要求

1. 选定一个新的唯一英文 id（3~8 个小写字母，语义化，如 `dragon`），将中文名与 id 追加到上方对照表。
2. 类名 `XxxSignItem extends BaseSignItem`，物品注册名 `xxx_sign`。
3. 关联的效果注册 id / 数据组件 id / 附件 id 一律以该 id 为前缀（如 `xxx_burst`、`xxx_sign_stacks`）。
4. 纹理图标文件名与效果注册 id 一致（`textures/mob_effect/xxx_*.png`）。
5. lang 中物品/效果/tooltip key 使用该 id。
6. 在 `ModItems` 注册、`ModCreativeTabs` 加入创造栏、`datagen/ModItemModelProvider` 加入模型、`datagen/ModRecipeProvider` 加入配方、`curios/tags/item/stand.json` 加入标签、**`astral_dice:signs` 汇总标签（`data/astral_dice/tags/item/signs.json`）**加入标签（新增立牌必须进汇总标签，否则图鉴/联动按标签检索时会漏）。
   - **配方统一使用 shape(`ShapedRecipeBuilder`)**:空白立牌/空白筹码(升级配方为上一级物品)始终在 3×3 **中央**;立牌骰子固定**中下**且只能 1 个;**非进阶**筹码基础配方中星币/星盘固定**最下一排**;原版材料对称填充;空槽用空格,禁止用 `_`。进阶筹码不适用本条,一律走「通用升级」模板(见「筹码一览 → 筹码配方规范」)。
7. **禁止**将中文名直译为英文标识符（如把"护法"写成 guardian、"扫地机"写成 sweeper）——一律使用约定 id。
8. 若立牌有专属卡牌/事件等联动，专属卡注册到 `RandomCardHandler.registerExclusiveCard`；事件的**统一附加效果**(触发后 +星币/给牌等)接入 `AstralEventSystem.onEventTriggered`(事件本体由立牌自行实现，2026-09-12 起已无事件类型注册表)。
9. 需指定目标的技能(占星师/秘密侦探/枪匠)在**当前主线**用"待命"等待器(`SIGN_READY_TYPE`/`SIGN_READY_EXPIRE` 附件 + `haiqing_ready`/`bonnie_ready`/`moses_ready` 提示效果,等待期内攻击目标即释放);**2.0.0 开发线(`multi-dev-next` / `wt/2.0.0-vnext`)**已改为注册 `TargetSelectionAction` 到 `TargetSelectionRegistry` 并经 `TargetSelectionManager.start` 进入目标选择模式(见「目标选择器规范」)。按所在分支选择实现,勿混用两套。
10. 在帕秋莉手册 `entries/signs/` 新增条目与 lang 键(见「帕秋莉手册同步规范」)。

### 注意：非立牌物品不受此规范约束

筹码（chip，如忍术飞镖 `ninja_star_chip`、魔法秘典 `magic_tome_chip`）、卡牌、骰子等物品的英文名按其自身命名约定，与立牌 id 无关（例如 `NinjaStarChipItem` 是筹码而非立牌，保留 ninja 词根是正确行为）。

## 效果牌命名与结构规范（Effect Card Convention）— 必须遵守

效果牌**不再区分"功能效果牌/伤害效果牌"**,全部统一继承 `BaseEffectCardItem`(item.card 包),共用同一套使用流程(专属校验→出牌锁→施加效果→出牌登记→复制计数→消耗)。

### 结构要求
1. 类名 `XxxCardItem extends BaseEffectCardItem`,物品注册名统一 `effect_card_xxx`(如 `effect_card_monster_laser`),在 `ModItems` 注册。
2. 效果实现二选一:
   - **简单状态牌**:覆写 `getEffect()` 返回效果引用(基类自动施加 `getEffectDuration()` 默认 60 秒);
   - **复杂逻辑牌**:覆写 `applyEffect(Level, Player, LivingEntity, ItemStack)`(施加实际效果)。
3. 按需覆写:
   - `selectorActionId()`:**是否走目标选择器释放**（缺省 `null` = 自身牌,按下即对自己生效;返回非 null = **手持（主手）即自动开会话**、确认后才生效并消耗;2026-09-25 起四张可对他人使用的牌全部走这条路,**旧的 `canUseOnOtherPlayers()` 已删除**;触发/收官/抑制闩口径见目标选择器规范的 `### 现行口径` 第 14 条);**目标类型由注册时传入** —— `registerSelectorAction(String actionId, TargetType targetType, boolean allowSelf)`(两参重载缺省 `TargetType.PLAYER`,既有四张牌不变),**活体书页是首个 `TargetType.ENEMY` 的效果牌**(2026-09-25);
   - `cardTypeId()`:复制计数使用该类型 id;**复制计数范围为全部效果牌**(治疗/伤害/互动/专属牌都计入忍者立牌/魔法秘典/魔法箭袋的计数,原 `countsForCopy()` 过滤已移除);
   - `isExclusive()`:专属牌(绑定获得者,见 `ExclusiveCardUtil`;活体书页/命运的指引= true);
   - `isHealingCard()`:治疗类标识(使用后触发大当家被动 `FenSignItem.onHealingCardUsed`)。
4. 效果注册:在 `ModEffects` 注册对应 MobEffect;法伤/攻防加成结算分别注册到 `SpellDamageRegistry`/`DiceCombatModifiers` 修饰器。

### 冷却机制(EffectCardPeriod 统一管理,无需在各牌实现)
- **效果牌轮次定义(必须遵守)**:效果牌轮次指当前出牌周期——**不论出牌数是否已达上限**——只要仍有效果牌的"能力/效果"或"出牌冷却"未结束,周期即未结束。当**所有效果牌进度走完且冷却时间走完**后,该周期才算结束;周期边界用于"每轮一次"类计数清理与新一轮开牌锁判定。**"完全重置"另有专用回调节点 `EffectCardPeriod.onRoundFullyReset`(第二批「三态化」新增)——只在 `tick` 情形 1(冷却到期后计数归零)与 `registerPlay` 的周期边界块两处触发,情形 2/3(未打满的收尾冷却、不变量违例修复)不算完全重置、不得调用;另有 `EffectCardPeriod.forceResetRound`(出牌数、出牌冷却与全部"每轮一次"标记一并归零,且**不**回调 `onRoundFullyReset`,由调用方自行决定后续迁移),忍者立牌主动的宽限保险走它。**轮次归零的统一清理口径 = `EffectCardPeriod.clearRoundBonuses`(2026-09-15 本批 C1 收口)**:它清除"一次性出牌数加成 / 可口糖果 / 探天卫星 / 活体书页本周期累计",**并同时解除电击手套本周期已武装的法伤扩散**(`ElectricGloveChipItem.disarmAoe(player)`,下个周期可重新武装)——该调用此前只写在 `tick` 情形 1,导致忍者宽限期满走的 `forceResetRound` 不解除武装(两条"完全重置"路径口径不等价);现已上移到 `clearRoundBonuses`,三条归零路径(`tick` 情形 1 / `registerPlay` 周期边界 / `forceResetRound`)**共用同一套清理项**,禁止再在任一调用点单独补写(含 `registerPlay` 边界块——那里的解除武装是**期望**行为:轮次完全重置即解除武装)。
- **出牌数**:基础 1 + 固定来源(大背包/忍术飞镖)+ 临时来源(活体书页:**命中前已有 ≥3 层标记的目标时累计 +1**(仅当前周期;补记入口 `EffectCardPeriod.grantLivingPageCycleBonus`,在**命中时**结算而非使用时,自带跨轮保护 `getPlayCount(player) > 0` 与封顶);命运指引:效果存在即 +1(覆盖式,不累计);可口糖果满血触发:每周期一次;探天卫星轨道炮触发:每 1:00 一次;立牌主动的**一次性 +1**),由 `registerFixedSource`/`registerTemporarySource` 与 `EffectCardPeriod.grantBonusPlay` 提供;单轮总出牌数**固定封顶 9 张**——`getMaxAllowed` 返回 `min(1 + 全部加成, GameplayConstants.MAX_EFFECT_CARD_PLAYS)`(常量 = 9,**固定常量、不写入配置文件**,原 `max_effect_card_plays` 配置项不再提供),加成来源仍可叠加但不得超过上限。**封顶 9 是全局限制,对全部来源与全部立牌一律生效(不是忍者专属上限):忍者立牌主动的一次性 +1、大背包/忍术飞镖、活体书页/命运指引/可口糖果/探天卫星等任何增加出牌数的手段都只能把上限推到 9,永远不能绕过 9;忍者主动在「已达封顶 9」时拒绝释放,只是在全局上限之外**多加一道「没空间就不释放」的前置检查**,并非独立于 9 的另一套上限。
  - 忍者立牌主动「忍术连击」= **一次性 +1(2026-09-14 用户裁决后完全重写,必须遵守)**:语义只有一条——**把当前出牌轮的可出牌数 +1**,调用唯一入口 `EffectCardPeriod.grantBonusPlay(player)`(附件 `effect_card_bonus_plays`,0/1,随出牌轮归零清除)。**禁止**再引入任何形式的「出牌银行」或主动技能来源注册:旧附件 `komachi_extra_plays`、旧常量 `GameplayConstants.KOMACHI_EXTRA_PLAYS_CAP`、以及 `EffectCardPeriod` 静态块里为主动注册的 `ExtraPlaySource` **已全部删除**,不得复活(残留检查:`grep -r komachi_extra_plays` 必须 0 命中)。出牌轮清理只有 `EffectCardPeriod.clearRoundBonuses` **一个入口**,现有 **三处**调用——`registerPlay` 的周期边界、`tick` 的周期结束、以及 `forceResetRound`(忍者宽限期满的强重置;三处共用同一套清理项,含 2026-09-15 本批 C1 上移进来的 `ElectricGloveChipItem.disarmAoe`)(**玩家死亡路径 `PlayerLifecycleHandler` 已移除该调用**:相关键都不是 `copyOnDeath`,重生后的新实体上本就是默认值,写它属空操作),禁止在各处再列一遍清理项。立牌装卸**不得**回收已授予的 +1(授予即已消耗;中途回收会造成"上限在周期中途下降"的不变量违例,见下条)。释放前置(任一不满足即**不释放且不进入主动技能冷却**,仅回 ActionBar 提示):① **效果牌冷却进行中**(`EffectCardPeriod.isCooldownActive`,提示 `msg.astral_dice.komachi_active_cooldown`)——此时本轮已无"多出一张"的空间;② 当前出牌数上限已达封顶 9 张(提示 `msg.astral_dice.komachi_active_capped`);③ 本轮已授予过这次 +1(提示 `msg.astral_dice.komachi_active_used`)。主动技能自身冷却中的拒绝仍由 `BaseSignItem.performSkill` 统一拦截。**三态化补充(第二批)**:触发成功后**不立即进入冷却**,锁定跟随出牌周期(**冷却从"该轮出牌状态完全重置那一刻"开始**:`EffectCardPeriod.onRoundFullyReset` → `BaseSignItem.onEffectCardRoundReset`),另有**宽限 1:00** 保险——期内自始至终未出任何效果牌 ⇒ 强制重置出牌状态并进入冷却(`forceResetRound` + 起冷却);一旦出过牌 ⇒ 保险失效、遵循周期;**锁定期间用 `registerPlay` 把 `sign_active_lock_played` 置真**(跨周期边界保持,不能读 `effect_card_play_count`/`effect_card_bonus_plays`,两者都会被周期边界归零)。
  - **忍者立牌被动**(`KomachiSignItem.onEffectCardUsed`,每使用 3 张效果牌触发一次):复制最后一张使用的效果牌到物品栏 + 主动技能冷却立即减少 30%(剩余部分) + 伤害类效果牌伤害加成 +1(计数器"效果牌伤害增益",附件 `komachi_damage_bonus`,无上限,卸下立牌重置)。伤害加成经 `SpellDamageRegistry` 计入激光/板砖/轨道炮/定向爆破/活体书页的法伤;tooltip 按观看者实时显示加成后的伤害数值。**活体书页自 2026-09-25 起改为「命中时结算」的法伤伤害**(命中伤害 = 2 + 页数 + 本汇总值,经 `SpellDamageRegistry#livingPageImpactDamage`,伤害类型 `astral_dice:card_spell`);它**不再**附着到其它远程/魔法伤害上,故 `ModTooltipHandler#addActiveDamageBonusTooltip` 不再计入它。
- **效果待定**:新增效果牌后,在 `EffectCardPeriod` 静态块用 `registerEffectPendingSource(Holder<MobEffect>)` 注册"效果是否在生效"的判定(冷却归零但效果未结束则禁止开新轮;剩余被锁时长由该注册源的效果自动推导,禁止硬编码效果列表)。当前已注册:激光/板砖/轨道炮/定向爆破/命运指引/王之力/狂暴/岿然不动(活体书页 2026-09-19 起不再注册,见下句)。**活体书页自 2026-09-19 起为纯即时伤害牌,不再给玩家施加任何效果**(用户裁决「不应该有持续效果,应转换为及时伤害,移除所有原本效果器」):`LivingPageItem.applyEffect` 只做「绑定获得者 + `rin_pages` +1 + 登记飞行」三件事,曾用的 1:00 标记效果已整段删除,`EffectCardPeriod` 也不再把它注册为「效果待定来源」(出牌轮只由出牌数/冷却推进)。忍术飞镖/贯穿之铳原本靠 `hasEffect(LIVING_PAGE)` 识别的「使用了伤害效果牌」改由**伤害类型**判定(`SpellDamageRegistry.isLivingPageImpact` = `source.is(astral_dice:card_spell)`),故书页自身的命中仍吃这两个筹码的加成。`ModEffects.LIVING_PAGE`/`LivingPageEffect` 的**注册保留**(探针与共享库仍引用该 id,注册项不影响玩法)。
- **出牌数打满后才进入冷却(2026-09-15 裁决后的最终口径)**:`registerPlay` 在本次出牌使出牌数**达到上限**(`getMaxAllowed`)时启动 30 秒冷却(时长由常量 `EFFECT_CARD_COOLDOWN_SECONDS` = 30 秒,**打满前不开始倒计时**);**未打满的一轮在全部计时器(效果待定/被锁时长,判据 `EffectCardPeriod.getRemainingBlockTicks(player) <= 0`)结束后同样进入一轮 30 秒冷却,但「不作废剩余出牌数」**——该分支**不把计数抬到上限**,故 `isBurstFull` 保持为假、冷却期间**仍可继续出牌**、冷却到期计数归零再开新一轮;且**已在跑的冷却不得被冷却期间的补牌重启或延长**(否则补得越晚冷却越长、持续出牌时周期永不结束)。冷却归零出牌数归零(`tick` 由 `event/PlayerTickEvents.onPlayerTick` 驱动);可口糖果"每轮一次"标记随周期归零清除;探天卫星"每 1:00 一次"由附件 `satellite_play_bonus_cooldown_end` 独立计时。
- **出牌周期状态机的不变量(2026-09-14 严重 BUG 后固化,必须遵守)**:`effect_card_play_count`(本轮已出牌数)**只在「周期存活」时有意义**,周期的唯一权威标志是 `effect_card_cooldown_end`(冷却结束时刻):① 计数达到**当轮上限**时 `registerPlay` 必然启动冷却,**禁止**出现「计数 ≥ 上限却没有冷却在跑」的状态;② 上限 `getMaxAllowed` 是**实时**计算的,任何来源变小(卸下大背包/忍术飞镖/可口糖果/探天卫星、命运指引效果到期…)都可能让计数瞬间"超标",故 `tick` **必须**把这种状态当成周期结束处理(补上这一轮冷却),**禁止**像旧实现那样在 `cooldown <= 0` 时直接 `return` —— 那会让 `isBurstFull` 永久为真、**效果牌永久不可用**,且摘掉任何筹码/立牌都救不回来(计数在玩家附件上,与物品无关);③ 新增/修改任何"每轮一次"的临时出牌数来源时,必须同时确认它在周期归零时被清除(`tick` 与 `registerPlay` 的周期边界**两处**都要覆盖)。
- **长按右键一次按下只出一张牌(2026-09-14 外部 BUG 汇报,必须遵守)**:原版在右键按住时每 4 tick 调一次 `Minecraft#startUseItem`(自动重复),而客户端 `MultiPlayerGameMode#useItem` 在 `startPrediction` 内**无论** `Item#use` 返回什么都会把 `ServerboundUseItemPacket` 发出去(**连物品冷却命中的分支也照样发包**)——所以在 `Item#use` 里返回 `fail` 或做客户端预检都**拦不住服务端**。抑制点必须在 `Minecraft#startUseItem` 的 HEAD(`client/EffectCardUseGuard` + `mixin/client/AstralUseItemGuardMixin`,两子项目同文):同一次按下只放行第一张,**松键后由 `client/ClientTickHandler` 复位**;且只对手持效果牌生效(不影响放置方块/进食/弓箭等右键行为)。**禁止**把该守卫改到 `Item#use` 或 `isBlockedOnClient` 里实现。
- **效果牌叠层口径(2026-09-13 用户裁决,必须遵守)**:① **活体书页页数永久累计（存储值无上限），但有效加成上限 120**——**每次命中**使 `rin_pages` +1(永久累计),**命中伤害 = 2 + 此前已命中的页数**(该页数经 `SpellDamageRegistry#livingPageBonusPages` 读取时按 `SIGN_DAMAGE_BONUS_CAP = 120` **静默夹取** ⇒ 命中伤害最多 2 + 120;夹取口径见上文「立牌伤害加成的静默安全上限 = 120」;与本周期出牌数加成 `living_page_cycle_bonus` 是两个互不相干的计数)。⚠️ **本次使用的那一页不计入本次伤害**(2026-09-19 用户裁决「先执行伤害,后施加标记,最后使活体书页伤害+1」):出牌时**不再** +1,改由 `combat/LivingPageImpact#resolve` 在命中结算的**最后**补记 ⇒ **首张命中固定 2**(与 tooltip 显示一致;旧行为是 3)、其后 3/4/…;**未命中的书页不计页数**(目标中途死亡/失效/换维度/超龄)。**2026-09-25 重写**:伤害在**命中时**结算(见 `combat/LivingPageImpact`),不再是"效果期间对其它远程/魔法伤害的被动加成";**后续修正(2026-09-19)**:效果已整段移除,该牌为纯即时伤害、不给玩家任何效果。⚠️ **连续出牌(+1)的判定基准 = 命中前层数**(2026-09-19 两发布线实机复验):**把目标打到 3 层的那一次本身不触发** —— 「命中后显示 3 层」≠「命中前 3 层」,只有第 4 次命中(命中前恰 3 层)才补记 +1,补记后本周期上限由 1 变 2(可立即再出一张,不必等冷却)。结算与判定的固定顺序 = **先结算伤害**(忍术飞镖等筹码加成一律按**命中前**层数,即本次新增的 1 层不参与本次伤害)→ **再施加 1 层标记** → **再补记 `rin_pages` +1** → **最后判 `markBefore >= 3`**;顺序、门槛与页数口径各由两发布线的一条回归断言钉死(用例 `LIVING-PAGE-{1.21.1,1.20.1}` 的 `LK1/LK2`(忍术飞镖 0/2 层 ⇒ `dmg=2/4`)、`LS1..LS4`(跨轮累积到命中前 3 层 ⇒ `credit=1:max=2`)与 `LI2/LI3`(佩戴 rin 立牌且页数归零后连续两张 ⇒ **`dmg=2` 然后 `dmg=3`**)步)。② **狂暴/王之力各自最多 3 层**(层数上限由常量 `MAX_EFFECT_STACKS` = 3 支撑:`BerserkCardItem`/`EffectCardItem` 取 `min(已有效果层+1, GameplayConstants.MAX_EFFECT_STACKS-1)`,重复使用同时刷新时长为 `max(旧, 3:00)`);**岿然不动**同属"功能效果牌最多 3 层"的口径,**已按口径实现叠层**(2026-09-13):`UnwaveringCardItem` 取 `min(已有效果层+1, MAX_EFFECT_STACKS-1)`、护甲修饰器随 amplifier 线性放大(1.21.1 用 `MobEffect#addAttributeModifier` 的 curve 重载,1.20.1 覆写 `getAttributeModifierValue`),抗性提升仍固定 II;③ **命运的指引为覆盖式刷新**——不累计:只要效果在生效就 +1 出牌数(覆盖式),重复使用仅刷新 5:00 时长与"主动冷却减半"的即时结算,不叠加层数、不叠加出牌数。

- **活体书页飞行视觉口径(2026-09-19 用户要求,必须遵守)**:轨迹粒子**只发 `ParticleTypes.END_ROD`(白/发光)**,命中爆发 = `END_ROD` + `FLASH`;⚠️ **禁止再引入 `ParticleTypes.GLOW`** —— 用户报告「使用活体书页出现了未预期的深绿色粒子」,而调度器一共只发过三种粒子(END_ROD / GLOW / FLASH),GLOW 是发光鱿鱼墨那种**深青绿**小点,即该报告的来源,已于 2026-09-19 移除。**密度与流畅度**:粒子沿**本 tick 的位移线段插值投放**(`event/LivingPageFlightScheduler#emitTrail`,间距 `TRAIL_SPACING = 0.45` 格、每投放点 `TRAIL_PARTICLES_PER_STEP = 2` 个、速度参数 0 = 无随机漂移),**不得**退回旧的「每 tick 只在采样点放一次」(速度 2.4 格/tick ⇒ 相邻两团之间空 2.4 格,肉眼可见分段/断点 —— 正是本次要消除的观感缺陷)。**第一人称不得遮挡视野(2026-09-19 用户要求,必须遵守)**:发射起点**不得**取施法者眼位,而应沿「眼位 → 目标包围盒中心」方向前移 `LAUNCH_FORWARD_OFFSET = 1.0` 格(目标更近时最多前移到 `距离 * 0.5`,绝不越过目标);并且**凡落在任一在线玩家眼位 `EYE_CLEAR_RADIUS = 1.25` 格球内的粒子一律不发送**(`insideEyeClear` 遍历 `level.players()`,**拖尾与命中爆发/FLASH 共用同一判据**)—— 理由:粒子在相机近平面处会被渲染成一大块白光,糊住第一人称视野。**有意接受的代价**:贴脸目标(眼位 1.25 格内)看不到命中特效,伤害与标记结算照常。⚠️ 该口径在游戏内由探针只读读数 **`:mineye=`**(`LivingPageFlightScheduler#lastFlightMinEyeDistance` = 最近一次飞行中「离施法者眼位**最近的已发射**拖尾粒子」距离,两位小数;-1 = 本次未发射)取证,用例 `LIVING-PAGE-{1.21.1,1.20.1}` 断言其 **≥ 1.25**(正则同时排除 -1/na/err ⇒ 「一个粒子都没发」不能假通过)。

### 活体书页命名规范(LIVING_PAGE)— 必须遵守
活体书页相关命名**一律使用 `LIVING_PAGE`**(统一命名标准):Java 标识符用 `ModItems.LIVING_PAGE`/`ModEffects.LIVING_PAGE`/`LivingPageItem`/`LivingPageEffect`,禁止 `LIVING_BOOK_PAGE` 等变体;效果注册 id 为 `living_page`,物品注册 id 为 `effect_card_living_page`(snake_case 已是 LIVING_PAGE 形式)。中文显示名仍为「活体书页」。

### 新增效果牌检查清单
1. `ModItems` 注册 + `ModCreativeTabs` + `datagen/ModItemModelProvider` + `datagen/ModRecipeProvider`;
2. `ModEffects` 注册效果(如需);
3. `EffectCardPeriod` 注册效果待定源(如需);
4. 参与随机发放则加入 `RandomCardHandler` 卡牌池;专属牌注册 `registerExclusiveCard`;
5. 法伤/攻防加成注册到对应修饰器注册表;
6. 禁止在牌内自行实现 `use()` 出牌/冷却逻辑(统一由基类处理)。
7. 在帕秋莉手册 `entries/cards_effect/` 新增条目与 lang 键(见「帕秋莉手册同步规范」)。

## 筹码一览（Chips）

全部筹码继承 `BaseChipItem`(不需要任何"筹码类型"判定链,`isChipItem` 已移除);创造栏按流派分组(星光/治愈/标记/充能/无流派)。属性类筹码(速度轮滑/摩托头盔/夹心饼干)通过 Curios 属性修饰器即时生效,不进入骰战修饰器注册表。

### 筹码配方规范（必须遵守）— 两档，互不越界

筹码配方分**两个互不重叠的档位**。**任何「筹码配方统一/生成」的工作只作用于非进阶筹码**（基础/流派/新补），**一律不得改写进阶筹码**；进阶筹码永远走自己的「通用升级」模板，与统一规范无关。

**① 非进阶筹码（基础 / 流派 / 新补）——统一规范**
- **第三行一律按品质取物**：稀有=`星币`（`C`）/ 史诗=`星盘`（`P`）/ 传奇=`黄金星盘`（`G`）。
- **第二行中位 = 空白筹码**（`B`）。
- **流派类筹码第二行两侧按流派替换**：治愈=再生试剂 `R` / 星光=星币尘 `D` / 标记=标记涂料 `M` / 充能=导电线材 `W`。
- 新补配方左右对称；排除 星币/星盘/黄金星盘/空白筹码/空白立牌 作为材料。
- 例外：星币锤第一行中位为 1.21 独有的重锤 `Items.MACE`，1.20.1 用 `Items.ANVIL` 替代（平台差异，见「双版本对等性」）。

**② 进阶筹码（15 个）——通用升级模板，一律遵循，无例外**

进阶筹码**不复用基础版的图案与材料**，一律按品质走下表两套固定模板；第二行中位固定为**其上一等级的筹码**，`unlockedBy` 也指向该上一等级筹码。

| 档位 | 品质 | 图案 | 材料（行1 / 行2两侧 / 行3） |
|---|---|---|---|
| 蓝 → 紫 | `EPIC`（史诗） | `LGL / GTG / PPP` | 青金石 `Items.LAPIS_LAZULI` / 金锭 `Items.GOLD_INGOT` / 星盘 `STAR_PLATE` |
| 紫 → 金 | `UNCOMMON`（传奇） | `RDR / DTD / GGG` | 红石粉 `Items.REDSTONE` / 钻石 `Items.DIAMOND` / 黄金星盘 `GOLDEN_STAR_PLATE` |

- 表中 `T` = 上一等级筹码（如拳击手套-中级 → 拳击手套-初级；美工刀-锋利 → 美工刀）。
- **无例外条款**：肾上腺素-一般（史诗）本身的基础配方为 `ZXZ/DCD/PPP`（Z=再生药水、X=下界之星、D=凋零玫瑰、C=空白筹码、P=星盘），其进阶版「肾上腺素-高效」仍沿用通用紫→金模板（原版字母写作 `RZR/ZOZ/PPP`，材料集合等价），**不因基础版特殊而特殊**。
- 权威数据表：`scripts/verify/ChipCommon.psm1` 的 `GRID`（非进阶）/ `UPGRADE`（进阶关系）/ `UPGRADE_TEMPLATE`（本表）。校验：`pwsh -NoProfile -File scripts/verify/verify_chip_recipes.ps1` 三档（源码 / 生成资源 / jar）× 双版本全绿。

### 星光类（Starlight）

| 中文名 | 注册 id | 品质 | 效果 |
|---|---|---|---|
| 手电筒-强光 | `flashlight_chip` | 紫 | 攻击敌对目标时星光 +1,**同一目标仅 +1 层**(已发放目标 UUID 记录于附件 `flashlight_granted_targets`,上限 256 个;记录满后不再发放,不做淘汰;卸下筹码或死亡时清空,见 `FlashlightChipItem.onAttack`);骰神赐福期间每 4 点星光 +1 攻击力(攻击修饰器) |
| 八面骰 | `eight_sided_dice_chip` | 蓝 | 触发骰神赐福后掷 1d10,累计每满 8 点 +1 星光;骰点恰为 8 时立即获得 8 星币(卸下清除累计;**星光已满时保留累计点数**) |
| ATM机 | `atm_chip` | 蓝 | 装备时星光 +1;星光兑换星币时兑换量 +40%(**向下取整且下限为 1**,`ResourceConversion.starlightToStarCoins`) |
| 银行卡-余额少/余额多 | `bank_card_low_chip`/`bank_card_high_chip` | 蓝/紫 | 装备期间星光基础值 +4/+7(下限,`StarLightManager.getBasePoints` 实时计算,卸下回落;装备时 `set` 自动补回) |
| 银行卡-用不完 | `bank_card_unlimited_chip` | 金 | 装备时星光 +3;每次骰神赐福结束后,自身及友方玩家(同队成员;未加入任何队伍时为全服在线玩家)获得 3 星币(`onBlessingEnd`,死亡清场不发放) |
| 星币锤 | `star_coin_hammer_chip` | 金 | 装备时星光 +5;持有星币超过 20 枚时,每次进入骰神赐福消耗 3 星币并按持有总数 30% 提升攻击力(星币袋按 9 算;**零散星币不足时自动拆开 1 个星币袋**再按枚扣除,拆袋前做「可存放性预检」——优先拆单袋槽位,否则必须已存在可容纳整袋 9 枚的空槽或未满星币堆,预检不通过则整次消耗放弃,**绝不因物品栏已满而让星币掉落到地上**;加成附件 `star_coin_hammer_bonus`,赐福结束清除) |

### 治愈类（Healing）

| 中文名 | 注册 id | 品质 | 效果 |
|---|---|---|---|
| 医疗箱-紧急/完备 | `medkit_emergency_chip`/`medkit_complete_chip` | 蓝/金 | 触发骰神赐福时 +1/+3 治愈点(经 `HealingManager.onBlessingTriggered`);卸下无副作用;装备不再立即回血 |
| 维生素药丸 | `vitamin_pill_chip` | 紫 | 合成或获得任意卡牌时,治愈 +1(拾取地面卡牌不触发) |
| 美工刀-初级 | `cutter_chip` | 紫 | 生命值 ≥ 60% 时攻击 +2,并加上当前治愈点数(处于汲取 `papara_bite` 状态时视为满血,跳过生命值判定) |
| 美工刀-锋利 | `cutter_blade_chip` | 金 | 与初级一致但基础攻击 +4;与初级为不同物品,可同时装备叠加 |
| 缓冲盾牌 | `buffer_shield_chip` | 蓝 | 受到攻击时 +2 治愈 +3 星币,每 15 秒一次(冷却附件 `buffer_shield_cooldown_end`) |
| 可口糖果 | `candy_chip` | 紫 | 每使用一张效果牌治愈 +1 并恢复 1 点生命;满血使用效果牌时本轮出牌数 +1(每个出牌轮次最多一次) |
| 友情徽章 | `friendship_badge_chip` | 紫 | 对友方玩家施加治疗效果时,双方各获得 2 点治愈(同一治疗者 1 秒内去重) |
| 大碗炖肉 | `big_bowl_stew_chip` | 金 | 骰神赐福效果结束后(`BigBowlStewChipItem.onBlessingEnd`,由 `DiceCombatEvents.onDiceBlessingExpired` 调用),对 16 格(`RANGE = 16`)范围内所有友方目标:玩家 +1 治愈点 + 恢复 2 点生命值;非玩家友方仅恢复 2 点生命值——判定见 `isFriendlyMob`:已驯服的宠物/坐骑要求**主人为自己或同队玩家**(排除野生坐骑与他人宠物),无归属的被动生物(猪、炽足兽)视为友方;筹码拥有者死亡时不发放 |

### 标记类（Mark）

| 中文名 | 注册 id | 品质 | 效果 |
|---|---|---|---|
| 普通瞄具 | `scope_chip` | 紫 | 骰神赐福期间攻击力 +2,并对目标施加 1 层标记 |
| 鹰眼瞄具 | `eagle_scope_chip` | 金 | 骰神赐福期间攻击时按目标标记层数 ×2 获得攻击力加成,并施加 1 层标记 |
| 标靶 | `target_chip` | 蓝 | 骰神赐福期间攻击力 +1;触发骰神赐福后对**距离最近的敌对目标**施加 1 层标记(无范围限制) |
| 标记喷罐 | `marker_sprayer_chip` | 蓝 | 对目标造成远程或魔法伤害后,使其获得 1 层标记 |
| 忍术飞镖 | `ninja_star_chip` | 金 | 效果牌出牌数 +1(固定来源);伤害效果牌生效期间远程/魔法伤害获得目标标记层数加成 |
| 手持风扇-小 | `hand_fan_small_chip` | 蓝 | 使用主动技能后,对周围 16 格敌对目标施加标记 |
| 手持风扇-大 | `hand_fan_big_chip` | 紫 | 使用主动技能后,获得一张随机效果牌(不含专属)并对周围范围内敌对目标施加标记 |
| 魔法箭袋 | `magic_quiver_chip` | 紫 | 使用效果牌后对带标记目标造成法伤 → 施加一层标记并返还第一张使用的效果牌(每分钟一次;追踪附件 `magic_quiver_tracking`/`magic_quiver_first_card`,冷却 `magic_quiver_cooldown_end`;**卸下筹码清除追踪记录**)。⚠️ **「带标记」按命中那一刻判定**:修饰器的 `isActive` 在全部 `onHit` 之前统一求值,而活体书页自己那一层是在 `hurt` 返回**之后**才施加 ⇒ **首次打干净目标不触发**(需先用标记喷罐/上一发书页留下标记);2026-09-19 两线实机验证见 `TESTING-SPEC.md` 附录 A「（续 7）」 |

### 充能类（Charge）

流派规则见「充能流派规范(Charge System)」。创造栏顺序:跃迁引擎 → 能量回收器 → 电流剑 → 高级外设 → 电流核心 → 电击手套 → 安全气囊 → 永动机 → 电磁炮 → 原初核心。

| 中文名 | 注册 id | 品质 | 效果 |
|---|---|---|---|
| 跃迁引擎 | `warp_engine_chip` | 蓝(稀有) | 触发指定传送后充能 +2 并获得迅捷 0:10。计入的传送:末影珍珠、末影骰子不死图腾后的安全瞬移、维度传送门、Waystone(经 `WaystoneWarpCompat` 反射接入)。**传送门与 Waystone 触发共享 5:00 冷却**(`ModAttachments` 附件 `warp_engine_portal_cooldown_end`);末影珍珠与末影骰子瞬移不受该冷却限制 |
| 能量回收器 | `energy_recycler_chip` | 蓝(稀有) | 每累计移动 150 米充能 +1(常量 `DISTANCE_THRESHOLD = 150`)。经 `curioTick` 逐 tick 累计**三维位移**;**单 tick 位移 > 10 视为传送/切维度,不累计**;卸下清除进度(`onChipUnequip`)。**当前充能 ≥ 5 层时移动速度 +5%**(常量 `SPEED_BONUS_CHARGE_REQUIRED = 5` / `SPEED_BONUS = 0.05`,`ADD_MULTIPLIED_TOTAL`):因该加成随充能升降,**不**走 Curios `getAttributeModifiers`(仅在装备变更时求值),而在 `curioTick` 中实时维护**瞬态属性修饰器**(达门槛时添加、低于门槛或卸下立即移除),修饰器 id 经 `attributeModifierId("charge_speed")` 派生 |
| 电流剑 | `electric_sword_chip` | 蓝(稀有) | 每拥有 4 层充能攻击力 +1(向下取整,常量 `CHARGE_PER_ATTACK = 4`;经 `DiceCombatModifiers` 攻击修饰器);累计击杀 10 个敌对目标充能 +2(常量 `KILLS_REQUIRED = 10`/`CHARGE_GAIN = 2`,监听 `LivingDeathEvent` 且目标须为 `Enemy`;计数卸下清除) |
| 高级外设 | `advanced_peripherals_chip` | 紫(史诗) | 充能层数 ≥ 4(`CHARGE_REQUIRED = 4`)时攻击力 +4(`ATTACK_BONUS = 4`,经 `DiceCombatModifiers`);每次触发骰神赐福移除 1 层充能(`BLESSING_CONSUME = 1`,由 `DiceCombatEvents` 赐福新周期开始处调用) |
| 永动机 | `perpetual_motion_chip` | 金(传奇) | 触发骰神赐福时,若当前充能不足 6 点则**补充至 6 点**(常量 `CHARGE_TARGET = 6`,由 `DiceCombatEvents` 赐福新周期开始处调用);**金品筹码,禁止进入 Bountiful `astral_rews` 奖励池** |
| 电磁炮 | `railgun_chip` | 金(传奇) | 充能 ≥ 6(`CHARGE_REQUIRED = 6`)时攻击力 +5(`ATTACK_BONUS = 5`,经 `DiceCombatModifiers`);攻击敌对目标时消耗 6 层充能,延迟 1 秒(`STRIKE_DELAY_TICKS = 20`,经 `RailgunStrikeScheduler`)**对目标 3 格内敌对目标降下雷击**并进入 1:00 冷却(`COOLDOWN_TICKS = 1200`)。**雷击伤害 = `STRIKE_DAMAGE_BASE = 5` + 本次攻击(骰战最终)伤害 × `STRIKE_DAMAGE_RATIO = 0.5`,且不低于 `STRIKE_DAMAGE_MIN = 5` 点**——`onAttack` 在伤害结算前段以即时伤害兜底登记,`DiceCombatEvents` 在 `setNewDamage/setAmount` 之后用 `finalDmg` 回填;降雷时经原版 `LightningBolt#setDamage` 覆写字段默认值 5.0F(点火与闪电苦力怕由 `!visualOnly` 分支独立驱动)。雷击为独立的**真伤**伤害源(`astral_dice:true_damage`,登记于 `minecraft:bypasses_armor` → 无视护甲值与盔甲韧性;由 `mixin/EntityThunderHitMixin` 只对电磁炮降下的闪电替换伤害源,标记集合见 `damage/RailgunBolts`,详见「真伤伤害类型」章节)。**命中范围已收窄为仅敌对生物与已被激怒的中立生物**(其余实体整段取消:不受伤也不被点燃),不进入骰战结算、不触发骰神赐福;金品筹码,不进入 Bountiful 奖励池 |
| 原初核心 | `primordial_core_chip` | 金(传奇) | 每消耗 1 层充能获得 1 层赋能(`EmpowerManager.onChargeConsumed`,经 `ChargeManager.consume` 统一挂钩);每层赋能攻击力 +1、防御力 +1(经 `DiceCombatModifiers` 计入,防御力按 1 防御力 = 2 护甲值折算)。赋能上限 10 层、每 0:30 递减 1 层(`DECAY_INTERVAL_TICKS = 600`),卸下筹码清除全部赋能;**金品筹码,禁止进入 Bountiful `astral_rews` 奖励池** |
| 电流核心 | `current_core_chip` | 紫(史诗) | ①**使用主动技能时充能 +1**(`CurrentCoreChipItem.onActiveSkillUsed`;普通立牌在 `BaseSignItem.performSkill` 触发成功并开始冷却处调用,需指定目标的立牌在其等待释放成功处——`DiceCombatEvents` 三处——调用,等待超时未释放不发放);②**主动技能冷却中按下主动技能键** → 按剩余冷却占比消耗充能并**立即使主动技能冷却完成**(`BaseSignItem.performSkill` 冷却分支内 `tryFinishCooldown`)。消耗档位:剩余冷却 ÷ **本次冷却实际使用的最大冷却(附件 `sign_active_max_cooldown`;记录缺失时回退 `MAX_COOLDOWN_SECONDS = 180` 秒)**的占比 × `MAX_COOLDOWN_COST = 6` 向上取整,钳制 1~6 点(每 1/6 一档);充能不足则不生效并提示。完成后**不直接释放技能**,需再次按键使用(既有行为不变)。**三态化补充(第二批)**:该「耗充能立即完成冷却」**仅在真实冷却态可用** —— 主动技能处于**锁定(生效中)**态时**不可触发、不消耗充能**,并抛与三态化**同一条**提示「%s:主动技能生效中!」(`msg.astral_dice.sign_active_in_effect`);按键在 `BaseSignItem.performSkill` 的锁定分支即被拦下(该分支判定在冷却分支之前),`CurrentCoreChipItem.tryFinishCooldown` 另加 `isSignActiveLocked` 同判据作纵深防御 |
| 电击手套 | `electric_glove_chip` | 紫(史诗) | 使用效果牌时充能 +1(`CHARGE_GAIN_PER_CARD = 1`);充能不少于 4 层(`CHARGE_REQUIRED = 4`)时,**使用伤害类效果牌**会消耗 4 层充能并武装本周期的法伤扩散——本周期内对敌对目标造成的远程/魔法伤害同时命中目标 3 格(`AOE_RADIUS = 3`)范围内的其他敌对目标(每个效果牌持续周期仅触发一次,武装状态存附件 `electric_glove_aoe`)。⚠️ **触发判定实际是「法伤白名单」而非字面的远程/魔法**:任何经 `DamageEffectCardHandler` 修饰器链的伤害都算,故**活体书页**(`card_spell`)同样触发,且**不要求目标已有标记**(与魔法箭袋相反)、首次打干净目标也扩散;`apply` 为空操作 ⇒ **主目标不吃额外伤害**,唯一表现是 3 格内**其它**敌对目标掉血(单挑时机理上「看不到变化」);扩散用 `astral_dice:true_damage` ⇒ 不递归、不进骰战、不影响书页的标记/credit/返还牌(2026-09-19 代码级验证,见 `TESTING-SPEC.md` 附录 A「续 8」) |
| 安全气囊 | `airbag_chip` | 紫(史诗) | 受到致命伤害时消耗 6 层充能(`CHARGE_COST = 6`)使本次伤害无效,随后进入 1:00 冷却(`COOLDOWN_TICKS = 1200`,附件 `airbag_cooldown_end`);充能不足或冷却中不生效。经 `ChipDamageHandler` 在最终伤害阶段以最低优先级拦截,**保命优先级高于不死图腾与末影骰子**;致命判定一律按**吸收(黄心)之后**的伤害(1.21.1 手动换算 `damage - getAbsorptionAmount()`(下限 0);1.20.1 的 `LivingDamageEvent` 天然在吸收之后);对**无视无敌**(`DamageTypeTags.BYPASSES_INVULNERABILITY`,如 `/kill`、其它模组真伤)的致死伤害**同样有效**(伤害阶段不再按该标签排除),**但虚空伤害 `minecraft:out_of_world` 例外**——虚空击杀优先级最高,气囊与磨刀石一律不阻止(刻意**只按伤害类型**排除 `out_of_world`:同一标签还含 `generic_kill`,一律查标签会把 `/kill` 也变成不可救);并另有死亡事件兜底(`EventPriority.HIGHEST`:绕过伤害管线直接致死时取消死亡并抬血到 1,同样跳过 `out_of_world`) |

注意事项:
- 充能类筹码的获得/消耗一律经 `ChargeManager.addStacks/consumeOne`(见「充能流派规范」),禁止直接操作 MobEffect 层数。
- **每个充能筹码的 tooltip 一律追加「当前充能」计数器,统一格式无例外**——新增充能筹码时,除注册/模型/配方/tooltip 文案外,必须在其 `stack.is(...)` 分支调用 `addChargeCounter`(格式与 lang key 见「充能流派规范 → 充能筹码一律追加充能点数计数器」)。
- 跃迁引擎的 Waystone 联动为**反射式可选接入**:未安装 Waystones 时自动降级,不影响构建与运行。

### 无流派（Other）

| 中文名 | 注册 id | 品质 | 效果 |
|---|---|---|---|
| 魔法秘典 | `magic_tome_chip` | 紫 | 每使用 3 张效果牌,复制最后一张使用的效果牌并返回物品栏(独立计数,**卸下筹码/死亡时清除计数**) |
| 大背包 | `big_backpack_chip` | 紫 | 效果牌出牌数 +1(固定来源) |
| 拳击手套-初/中/高级 | `boxing_gloves_low_chip`/`medium_chip`/`high_chip` | 蓝/紫/金 | 骰神赐福攻击力 +2/+4/+8(`BONUS_LOW/MEDIUM/HIGH`,`DiceCombatModifiers` 攻击修饰器) |
| 速度轮滑-初/中/高级 | `speed_skates_low_chip`/`medium_chip`/`high_chip` | 蓝/紫/金 | 移动速度 +5%/+15%/+25%(`SPEED_LOW/MEDIUM/HIGH`,属性修饰器) |
| 摩托头盔-一般/中级/高级 | `moto_helmet_low_chip`/`medium_chip`/`high_chip` | 蓝/紫/金 | **防御力** +2/+4/+6(常量 `DEFENSE_LOW/MEDIUM/HIGH`;代码层按 `ARMOR_PER_DEFENSE = 2` 折算为真实护甲 `Attributes.ARMOR` +4/+8/+12);盔甲韧性 +2 仅高级(属性修饰器)。**防御力口径:文案一律写「防御力」并保持该数字,护甲值只在代码层换算,禁止在文案里写护甲值** |
| 夹心饼干-一般/可口/美味 | `sandwich_low_chip`/`medium_chip`/`high_chip` | 蓝/紫/金 | 最大生命值 +4/+8/+8(属性修饰器);美味额外:最大生命值超过 20 点时,超出部分每 4 点生命值 +1 攻击力(经 `DiceCombatModifiers` 攻击修饰器计入,见 `SandwichChipItem.getAttackBonus`) |
| 肾上腺素-一般 | `adrenaline_low_chip` | 紫 | 生命值为 50% 或更低时:攻击力/防御力 +3(1 防御力 = 2 护甲值,代码折算为真实护甲) |
| 肾上腺素-高效 | `adrenaline_high_chip` | 金 | 生命值为 50% 或更低时:攻击力/防御力 +8(1 防御力 = 2 护甲值,代码折算为真实护甲);触发加成时被敌方攻击:20% 概率闪避单次攻击 |
| 磨刀石 | `whetstone_chip` | 紫 | 生命值为 50% 或更低时:攻击力 +4、受到伤害 -2;生命值 > 1 点时:使受到的伤害不超过剩余生命值(至多扣到剩 1 点,不会因一次伤害被击倒) |
| 诅咒之剑 | `cursed_sword_chip` | 蓝 | 装备时始终受到青之诅咒(护甲 -20%、韧性 -100%);骰神赐福期间每击杀 1 个不少于 20 血的敌对目标攻击力 +1(每次赐福至多触发 1 次,上限取常量 `GameplayConstants.CURSED_SWORD_BONUS_MAX`(默认 16,原配置文件项已移除);移除筹码清除加成与诅咒) |
| 复仇之戟 | `revenge_halberd_chip` | 紫 | 装备时若身上出现指定负面/诅咒效果(攻击类:虚弱/缓慢/挖掘疲劳/失明/黑暗/蓄风/盘丝/渗浆/寄生/青之诅咒;防御类:饥饿/反胃/中毒/凋零/袭击之兆/试炼之兆/标记),攻击力 +6、防御力 +6(代码折算护甲 +12)(每类只触发一次,不叠加)。**1.20.1 侧仅支持该版本存在的效果**:攻击类只有 虚弱/缓慢/挖掘疲劳/失明/黑暗/青之诅咒,防御类只有 饥饿/反胃/中毒/凋零/标记(蓄风/盘丝/渗浆/寄生 为 1.21 新增、袭击之兆/试炼之兆 为 1.21 新增),故 1.20.1 的 lang 文案按此删减——**这是两子项目 lang 允许差异的唯一登记项** |
| 贯穿之铳 | `piercing_gun_chip` | 金 | 伤害效果牌生效时,对敌对目标远程/魔法伤害额外增加目标防御力点数(**公式 = 2 + min(护甲,20)÷2 + 1.4×韧性,结果向下取整**;与骰战 `defensePower` 前三项逐字同构、与骰战界面显示口径一致;不含防御骰/防御卡。护甲上限 20 与骰战一致,来源为原版 `CombatRules.MAX_ARMOR`) |
| 探天卫星 | `satellite_chip` | 金 | 物品栏中轨道炮少于 6 张时每 1:00 补充 1 张;使用轨道炮后本轮出牌数 +1(每 1:00 仅一次);轨道炮生效期间远程/魔法击杀敌方目标后获得一张随机效果牌 |
| 会员推荐信 | `member_recommendation_chip` | 蓝 | 每次触发骰神赐福时获得一张随机卡牌(`MemberRecommendationChipItem.onBlessingStart`,由 `DiceCombatEvents` 赐福触发块调用,卡池 `CardCategory.ALL`) |
| 书签 | `bookmark_chip` | 蓝 | 使伤害效果牌伤害加成 +1(`BookmarkChipItem.damageBonus`,`DAMAGE_BONUS = 1`,无上限、无计数;经 `SpellDamageRegistry.effectCardDamageBonus` 计入激光/板砖/轨道炮/定向爆破/活体书页) |
| 小猪存钱罐 | `piggy_bank_chip` | 蓝 | 每使用 2 张效果牌(`CARDS_REQUIRED = 2`)获得 3 星币(`COIN_REWARD = 3`);计数存附件 `piggy_bank_use_count`,卸下清除(`onChipUnequip`),与魔法秘典/忍者立牌计数互不关联 |
| 智能手表 | `smart_watch_chip` | 紫 | 玩家物品栏中卡牌数量不足 10 张(`CARD_THRESHOLD = 10`)时,**每击杀 1 个敌对目标**获得一张随机卡牌(`SmartWatchChipItem.onHostileKilled`,`LivingDeathEvent` + `Enemy` 判定,无冷却无计数;达到阈值后不再发放) |

注意事项:
- **【已被取代（2026-09-18 目标选择器迁移）】需指定目标的三个立牌(占星师/秘密侦探/枪匠)的释放触发条件与骰神赐福完全一致**:近战武器攻击(`isMeleeWeaponAttack`)+ 目标符合赐福判定(`isBlessingTarget`),三个立牌的释放块共用同一外层判定,禁止再附加"普通敌对生物"等额外限制。（**现状**：这三个立牌的**主动**已迁到目标选择器、**改由主动技能键触发**，攻击路径已无任何立牌释放逻辑 —— 见 `DiceCombatEvents` 的「不再于攻击时自动释放」注释；攻击路径只保留枪匠**被动**「攻击已带破绽的目标 ⇒ +1 层弱点识破」。另：**游戏大师 `ren` 同样需指定目标，但只由主动技能键触发，从不走攻击释放**。本条目仅存历史沿革。）
- **「破绽」在持续 2:00 内始终有效**:该期限内目标的**每一次**攻击都会被枪匠闪避并触发一次反击;`moses_dodge_counter_rewarded` / `moses_broken_attack_rewarded` 只用于限制"每目标每段破绽的弱点识破层数 +1",**不得**用来拦截闪避/反击本身(曾因把奖励标记当作闪避门槛导致只生效一次);重新施加破绽时两个标记都会被重置。
- **百分比计算的「下限为 1」策略(必须遵守)**:任何按比例算出的产出/伤害(ATM 兑换 +40%、星币锤 30% 攻击加成、治疗类牌按最大生命百分比等)在截断后不足 1 时一律按 **1** 计,禁止出现"按比例算出来是 0"的情况。**例外(有明确数值下限,不再走 1)**:大当家立牌溅射 88% 下限 **5 点**(`FenSignItem.SPLASH_DAMAGE_MIN`)、电磁炮雷击下限 **5 点**(`RailgunChipItem.STRIKE_DAMAGE_MIN`);新增此类"下限高于 1"的比例伤害必须在常量里显式声明下限,不得只依赖 1 的兜底。
- 属性类筹码(速度轮滑/摩托头盔/夹心饼干)覆写 `ICurioItem.getAttributeModifiers`,修饰器 id 用 `BaseChipItem.attributeModifierId` 按物品派生(同属性不同筹码不得共用 id,否则后装覆盖先装)。
- **防御力口径(必须遵守)**:面向玩家的文案一律写「防御力」并保持设计数值;护甲值(原版 `Attributes.ARMOR`)只在**代码层**按 `1 防御力 = 2 护甲值` 换算(`MotoHelmetChipItem.ARMOR_PER_DEFENSE`、`DiceCombatModifiers.setDefenseArmorBonus`),禁止在文案里直接写护甲值;攻击/防御加点统一经 `DiceCombatModifiers` 注册。
- **攻击/防御类筹码加成的生效窗口**:所有经 `DiceCombatModifiers` 注册的攻击/防御修饰器(拳击手套/标靶/手电筒/电流剑/高级外设/夹心饼干-美味/肾上腺素/磨刀石/复仇之戟/鹰眼瞄具)只在**骰神赐福期间的骰战**里结算(`DiceCombatEvents` 在无赐福时提前 return);文案惯例是仅在措辞本身需要区分时标注「骰神赐福期间」(如拳击手套),其余条目按条目自身语义书写。
- **筹码配方分两档且互不越界（必须遵守）**：非进阶筹码走统一规范，**进阶筹码（15 个）一律遵循「通用升级」模板**（蓝→紫 `LGL/GTG/PPP`、紫→金 `RDR/DTD/GGG`），任何筹码配方统一/生成工作**只作用于非进阶筹码**、不得改写进阶筹码；肾上腺素-高效同样沿用通用紫→金模板，**无例外**。完整规则与模板表见「筹码一览 → 筹码配方规范」。
- 魔法箭袋追踪在**任意效果牌**使用后开启(当前实现不做效果牌类型过滤——原 `countsForCopy()` 过滤已在重构中移除,`BaseEffectCardItem` 对全部效果牌统一调用忍者/魔法秘典/魔法箭袋计数);返还卡类型映射见 `BaseEffectCardItem.cardByTypeId(String)`(1.21.1 `:93` / 1.20.1 `:92`;旧文档里的 `MagicQuiverChipItem.effectCardByType` 已不存在,2026-09-19 修正)。
- 星币锤/银行卡-用不完的赐福开始/结束钩子位于 `combat/DiceCombatEvents.onLivingDamagePre`(triggeredBlessing 块)与 `DiceCombatEvents.onDiceBlessingExpired`(顶部,早于骰子检查);会员推荐信走同一赐福触发块(`onBlessingStart`),大碗炖肉走 `onDiceBlessingExpired`(`onBlessingEnd`)。
- **「效果牌伤害加成」只有一个汇总出口(必须遵守)**:忍者立牌「效果牌伤害增益」(`komachi_damage_bonus`)与书签筹码等任何新增的伤害效果牌加成,**一律**经 `combat/SpellDamageRegistry.effectCardDamageBonus(Player)` 汇总后计入法伤(激光/板砖/轨道炮/定向爆破/活体书页),禁止在各筹码/牌内自行叠加或另开出口;tooltip 同样按观看者实时读取该汇总值,保证显示与结算一致。
- **立牌伤害加成的静默安全上限 = 120(2026-09-19 用户要求,必须遵守)**:忍者立牌「效果牌伤害增益」与调查员立牌累计页数(`rin_pages`,只作用于活体书页命中伤害)**各自的加成上限为 `SpellDamageRegistry.SIGN_DAMAGE_BONUS_CAP = 120`**,由唯一入口 `SpellDamageRegistry#cappedSignDamageBonus(int)` 夹取(`effectCardDamageBonus` 夹忍者那一项、`livingPageBonusPages` 夹页数那一项);书签筹码的固定加成**不在**此列。⚠️ **口径是「不说明不提示」**:不得为此新增任何 tooltip 文案 / actionbar 提示 / lang 键或配置项(玩家侧只表现为数值不再增长);存储值本身仍继续累计(不做写入侧截断),夹取只发生在读取出口 ⇒ 换牌/卸下再戴回不会丢进度。`ModTooltipHandler` 的忍者计数行也走同一入口,故 tooltip 与结算**恒一致**。
- **法伤作用域里的「军火类黑名单保险」现由公共配置控制(2026-09-19,必须遵守)**:`SpellDamageRegistry.isSpellDamage()` 的第一道判定是军火类排除(按伤害类型 msgId 与弹丸实体类名关键词 `bullet`/`gun`/`firearm`/`cannon`/`shell`/`missile` 识别,实现在私有方法 `isFirearmDamage`)。该排除原先**写死**,现改由公共配置 **`allow_firearm_damage`(默认 `false`)** 控制,库侧常量为 **`GameplayConstants.ALLOW_FIREARM_DAMAGE`**(库 `1.0.0-SNAPSHOT.12` 起才有该字段):`false` = **默认仍屏蔽**(枪弹/炮弹类伤害不计入法伤、吃不到任何法伤加成,**与历史行为逐字等价**);`true` = 该类伤害不再被关键词直接排除,改为与其他弹射物一样继续走白名单 matcher 判定。⚠️ 三条硬约束:① 该常量由消费方 `ModCommonConfig.snapshot()` 构造 `GameplayConfigValues`(record,**位置即契约**)后经 `GameplayConstants.applyConfig(...)` 推入(库不读配置文件),本次是**末尾追加第 7 分量**`allowFirearmDamage`,消费方实参顺序必须同步,增删/改序会让消费方编译期失败;② 库侧 `applyConfig` 里**必须**有 `ALLOW_FIREARM_DAMAGE = config.allowFirearmDamage();` —— 漏了会**静默**停在默认 `false`(玩家改成 `true` 也不生效、不报错);③ 配置在**启动时**读取(`FMLCommonSetupEvent`,无 reload 监听),改后需重启。另注:该开关只决定「是否被排除」,**不等于**「一定计入法伤」(仍需 matcher 命中)。
- **定向爆破 AOE 伤害口径(2026-09-13 用户裁决 B,必须遵守)**:对目标周围 6 格敌对目标造成的 AOE 伤害 = **定向爆破自身 5 点 + 效果牌伤害加成(书签/忍者)**,**不**计入激光/板砖/轨道炮/活体书页等其它伤害牌自身加成(与 tooltip 展示的 `%s` 同值);改动 AOE 公式或新增伤害牌时必须保持该口径,回归用例 `DIRECTIONAL-BLAST-AOE` 已固化(B1 装齐其它伤害牌后仍须为 5,B2 再装书签须为 6)。
- **文案口径:「攻击时」= 触发骰神赐福的规则(近战武器 + `isBlessingTarget`)**;当描述的是效果伤害时,一律写成「使用远程/魔法伤害」(斜杠可写作「或」),不得混用「攻击时」指代效果伤害。
- **效果牌操作方式文案统一(必须遵守,2026-09-25 二次改口径:tooltip 两行式)**:凡**目标选择器类**效果牌(加急加快 `express_delivery` / 奢华大餐 `luxury_feast` / 狂暴 `berserk` / 你有我有 `you_have_i_have`),tooltip 一律**两行** —— **第一行 = 精简用法**,固定写「§e左键§7 对准其他玩家使用，§e右键§7 对自身使用」(不能自用的你有我有写「§e左键§7 对准其他玩家使用（不能对自己使用）」);**第二行 = 该牌本身的功能**,只写效果、不写释放手势与消耗规则(加急加快 = 使目标获得 迅捷 II (1:00);奢华大餐 = 治疗目标及 6 格内友方玩家,各恢复使用者最大生命值 30%%;你有我有 = 自身与目标各获得一张随机卡牌;狂暴 = 攻击力 / 受伤 / 持续时长的效果行)。三张单键牌用**值内 `\n`** 分行(`tooltip.astral_dice.card.{express_delivery,luxury_feast,you_have_i_have}`);狂暴的卡牌 tooltip 由 `ModTooltipHandler` 组装 ⇒ 用法行用 `tooltip.astral_dice.card.berserk`、功能行复用 `effect.astral_dice.berserk.description`(该键同时是「狂暴」状态自身的描述,故**只留效果、不留用法**)。⚠️ **2026-09-19 改口径(用户裁决:「描述过于复杂」+「伤害数字未染色」)**:**活体书页**(`living_page`)是首个**敌对目标**选择器类效果牌,其 tooltip **不再两行**,改**一行** —— 用法并入效果句,键 `tooltip.astral_dice.card.living_page`,值 =「瞄准敌对目标使用，对目标造成 §e%s§7 点法术伤害，并施加一层“标记”。命中已有 §e3§7 层及以上标记的目标时，本出牌周期出牌数 §e+1§7」(`%s` = 随观看者实时计算的 `SpellDamageRegistry#livingPageImpactDamage`);`tooltip.astral_dice.card.use_on_enemy` 自此为**死键**(键保留、勿删,与 `target_select.active` 同类)。⚠️ **行内数值染色必须走 `tt(...)`**(该类里 = `ModTooltipHandler#tt`:`String.format` 之后把整串包成**一个** `literal`)——**禁止** `Component.translatable(key, 数值).withStyle(…)`:数值参数会成为**继承父样式的兄弟组件**,行内 `§e` 只覆盖它**前面**的文本 ⇒ 数字仍显示灰色(即用户报告「伤害数字未染色」的根因)。⚠️ 同一写法在另外四张伤害牌上仍存在(`monster_laser` / `monster_brick` / `orbital_strike` / `directional_blast` 的 `Component.translatable(tooltipKey, baseDamage + effectCardBonus)`)⇒ 它们的数值同样未染色,属**已登记的同类缺陷**,修法逐字相同(本轮按用户范围只改活体书页)。⚠️ **「手持此牌（主手）自动开启目标选择器（…无倒计时）…移出手持即关闭、未确认不消耗卡牌」的写法已从 tooltip 撤下**(该口径作废;**完整口径仍写在手册条目** `astral_dice.guide.entry.effect_card_*.1` 里,手册继续用长句式)。⚠️ 更旧的「使用后进入目标选择模式（30 秒内…）」与「右键-自身使用，下蹲右键-对其他玩家使用」两种写法一律作废;自身牌(王之力/岿然不动/以毒攻毒/对怪三张/定向爆破/巧克力蛋糕/汉堡/命运的指引)tooltip 不写释放手势。
- **移动距离类来源一律按三维位移统计(含垂直)**:扫地机立牌(300 米)、能量回收器筹码(150 米)均取 `pos.y` 参与,文案不得再写「水平位移」。
- **效果牌冷却显示必须为实际值**:tooltip 经 `ModTooltipHandler.effectCardCooldownSeconds` 取 `ChargeManager.effectCardCooldownTicks`(有充能时基础值封顶 20 秒),不得直接显示配置基准值。
- **「嘲讽」(`pandaman_taunt`)只对敌对生物生效**,施加点必须带 `!(entity instanceof Player)` 守卫。
- **死亡清理口径(2026-09-13 定案)**:养精蓄锐(`fen_recharge`)死亡清零;「骇客无敌」(`INVULNERABLE_TICKS`/`nancy_lu_invulnerable_until`)因无授予点已整段删除,含死亡清理中会强制解除任何来源(其他模组/指令)无敌的 `player.setInvulnerable(false)` 残留;其余冷却类附件维持现状(仅在创建/修改时的约定要求下才清除)。
- **`damage_effect_bonus` 死键**:1.21.1 侧已删除(无任何调用);1.20.1 侧因该键在 `SYNCED_KEYS` 同步协议内而保留——属「功能一致、实现允许不一致」的已登记差异。
- **物品名「标记喷罐」是唯一正确写法(2026-09-15 用户裁定,必须遵守)**:zh 显示名、帕秋莉手册、文档与**代码注释**一律写「喷罐」,**禁止**写成「标记喷灌」(「喷灌」在中文里指农田灌溉,与喷涂无关;早期曾被误判为《吉星派对》专有名词而「保留原名」,该旧口径作废)。判定入口:`item.astral_dice.marker_sprayer_chip`(类 `MarkerSprayerChipItem`,英文名 `Marker Sprayer` 不受影响)。
- **技能名硬编码**:立牌 tooltip 的主动/被动技能名仍以中文字面量传入 `active_title`/`passive_title`(既定实现,不改);玩家侧功能与数值一致即可。- **敌对目标血量门槛统一为「不少于 20 血」(必须遵守)**:所有以「20 血」为门槛的筹码/立牌判定一律为**不少于 20 血**(`getMaxHealth() >= 20`,恰好 20 血/10 心计入),禁止写成「大于/超过 20 血」。当前两处:诅咒之剑筹码(`CursedSwordChipItem.onCursedSwordKill`,用 `getMaxHealth() < 20` 提前 return)与秘密侦探立牌「关键线索」(`BonnieSignItem.onKill`);新增同类判定必须沿用该阈值与措辞,tooltip/手册文案同步写「不少于 20 血」。

## 治愈流派规范（Healing System）— 必须遵守

治愈点数由 `HealingManager` 统一管理(玩家级共享资源,与具体饰品解耦)。**治愈体系使用 30 秒独立计时器**,仅在骰神赐福期间运转。

### 点数构成
- **治愈点为单一数值池**(存储于附件 `healing_points`,恒 ≥ 0,上限固定 32 点,由 `HealingManager.HEALING_POINT_CAP` 提供,不再随最大生命值变化)。
- 来源:史莱姆立牌被动(受击 +1)/主动(+3)、缓冲盾牌(受击 +2)、医疗箱(触发赐福时)、维生素药丸(获得卡牌 +1)、可口糖果(使用效果牌 +1)、友情徽章(治疗友方双方 +2)、大碗炖肉(赐福结束后 16 格内友方玩家 +1)。

### 运行规则(30 秒治愈计时器)
1. **触发骰神赐福时**(`combat/DiceCombatEvents.onLivingDamagePre` 的赐福触发块末尾调用 `HealingManager.onBlessingTriggered`):
   1. 先增加装备的医疗箱筹码治愈点(紧急 +1、完备 +3,可叠加,受上限);
   2. 再获得 **当前治愈点 × 2** 的治疗量(回血,不扣点;对应 MC 1♥/层);
   3. 启动/重置 **30 秒治愈计时器**(`healing_timer_end`,tick 由 `GameplayConstants.HEALING_TIMER_TICKS` 控制,禁止硬编码)。
2. **治愈计时器到期**(`HealingManager.onTimerEnded`,由 `tick` 驱动):
   治愈点**减半(向下取整)**;若仍处于骰神赐福且治愈点 > 0,则再次按剩余治愈点 ×2 回血并重置计时器;否则保留减半后的点数,等待下次触发。
3. **骰神赐福结束时**(`onBlessingEnded`,由 `tick` 边沿检测驱动,附件 `healing_prev_blessing` 记录上一周期状态):**仅清除周期标记,不减半**——减半统一由治愈计时器到期处理(避免登录/死亡强制移除效果时误减半)。
4. **执行优先级最后**:触发赐福时的回血结算置于赐福触发块末尾,晚于同事件内所有影响治愈点数量的效果(史莱姆受击/缓冲盾牌钩子在伤害事件更早处执行;医疗箱加点在回血前完成)。

### 医疗箱筹码
- **触发骰神赐福时**:增加治愈点(紧急 +1、完备 +3),由 `onBlessingTriggered` 统一结算。
- **装备不再立即回血**(已移除 `onMedkitEquipped` 与装备回血常量)。
- **卸下无副作用**:不扣治愈点(治愈点是玩家资源,与装备状态解耦)。

### 关键实现
- `tick`(onPlayerTick 驱动):上限收缩(最大生命降低时收缩点数)+ 赐福结束边沿检测 + 治愈计时器到期检测 + 效果刷新。
- `add`/`spend`/`clear`(死亡清零):纯点数增减,不触发回血。
- 效果显示:等级 = 当前治愈点;时长 = 骰神赐福剩余 tick(赐福中)/固定常显时长(无赐福,持续刷新);归 0 自动移除。
- 死亡:清空治愈点、边沿标记与计时器(`HealingManager.clear`)。

## 星光流派规范（Starlight System）— 必须遵守

星光点数由 `StarLightManager` 统一管理(玩家级共享资源,与具体饰品解耦)。

- **星光为固定点数,不随时间衰减,无计数器,只有增加与减少**(存储于附件 `player_starlight`,上限为常量 `MAX_STARLIGHT` = 32)。
- **基础值(下限)默认 0**:由 `StarLightManager.getBasePoints` 实时计算——银行卡-余额少/多(银行卡-用不完除外)装备期间提供常驻基础值 +4/+7;卸下自动回落,装备时 `set` 自动补回。
- **消耗后自动补回**:`spend()` 消耗星光后若低于基础值,`set()` 自动补充回基础值(`Math.max(base, ...)`)。
- **显示**:所有影响星光的立牌/筹码 tooltip 显示当前星光点数(经商立牌计数器;手电筒/八面骰筹码经 `tooltip.astral_dice.chip.starlight`)。
- 获取来源:经商立牌被动(每 60 秒 +1)/主动(赐福触发时首次骰点 ×2)、ATM机(装备 +1)、八面骰(骰点累计)、占星师(骰点 6 → 6 星币)等。
- **看板娘立牌(mimi)被动与星光无关**:每获得/变换 1 张战斗牌获得 1 星币(获得:经 `RandomCardHandler.giveCardTo` 发放战斗牌;变换:看板主动变化已装备卡牌并插入 1 张,每张 1 星币)。

## 反击伤害注入规范（Counterattack Damage Injection）— 必须遵守

反击**不再是效果/流派**:没有 `counterattack` 效果、没有层数、没有持续反噬目标周期。具体触发源命中时调用 `DiceCombatEvents.injectCounterDamage` 做**单次**伤害计算并注入,不登记目标、不持续返还。

- **当前触发源**:
  - 肉弹战车立牌「嘲讽」:被嘲讽目标攻击施加者时触发一次反击伤害。
  - 枪匠立牌「破绽」:带破绽目标攻击枪匠被闪避后,自动触发一次反击伤害。
- **单次伤害公式**:手持(主手+副手)伤害最高的近战武器基础伤害(不含附魔;无近战武器按空手 1.0)+ 骰战攻击力加成链(`DiceCombatModifiers` 攻击修饰器)+ 已装备攻击牌随机掷骰(`ctx.attackCardSum`);**不含 1d6、不自动赐福**。随后对总伤害计算七咒减益(`applyCurseToDicePoints`),并可受「全力攻击」×1.5 修正;肉弹战车立牌装备时若生命未满再附加缺失生命值等值伤害。
- **注入方式**:以玩家为伤害来源经 `hurt()` 单次结算(目标护甲按原版减伤;击杀计入玩家击杀来源),由 `DiceCombatEvents.injectCounterDamage` 统一执行。
- **递归保护**:`DiceCombatEvents.counterProcessing` 标记包裹注入伤害结算,`onLivingDamagePre` 顶部跳过该伤害(与 `aoeProcessing` 相同模式),防止注入伤害再次进入骰战结算/递归触发。

## 充能流派规范（Charge System）— 必须遵守

充能为**玩家级层数资源/效果**,效果注册 id `charge`(`ModEffects.CHARGE`;纹理 `textures/mob_effect/charge.png`,取自 `images/充能.png`),层数 = amplifier + 1,上限常量 `GameplayConstants.CHARGE_MAX_STACKS = 20`;效果时长无限(`ChargeEffect.DURATION_TICKS = Integer.MAX_VALUE`),层数归零时移除。

- **固定流派效果(与层数多少无关)**:只要拥有至少 1 层充能:
  - **立牌主动冷却至多 160 秒、效果牌公共冷却至多 20 秒(2026-09-25 改口径)**:改为把**基础值**封顶 —— 常量 `GameplayConstants.CHARGE_SIGN_COOLDOWN_CAP_SECONDS = 160` 与 `CHARGE_EFFECT_CARD_COOLDOWN_CAP_SECONDS = 20`;立牌入口 `ChargeManager.signCooldownTicks(player, baseTicks)`、效果牌入口 `ChargeManager.effectCardCooldownTicks(player, baseTicks)`(内部 `capByCharge`;无充能或基础值本就更低时原样返回)。**上限只作用于基础值**:先封顶、再由调用方叠加其它减免(诡异骰子 -50% ⇒ 有充能时 160/2 = 80 秒;枪匠「精密技巧」的 120 秒基础值低于上限 ⇒ 有充能时不受影响)。**已在进行的倒计时不受影响**(只在开始冷却时取值)。旧的按比例减免常量 `CHARGE_COOLDOWN_REDUCTION = 0.2` 与其入口 `ChargeManager.cooldownTicks(...)` **已删除**(共享库 `starengine_lib` 侧一并删除,库版本 `1.0.0-SNAPSHOT.10` → `.11`),**不得复活**。
  - ⚠️ **充能流派不提供任何防御力/护甲加成**(2026-09-11 起):`ChargeEffect` 不再为 `Attributes.ARMOR` 注册属性修饰器,`GameplayConstants.CHARGE_ARMOR_MULTIPLIER` 已删除。**禁止**重新为充能添加护甲/防御类加成。
- **粒子禁用(2026-09-12,必须遵守)**:充能与赋能的实例**一律**以 `visible=false` 构造(`MobEffectInstance` 第 5 参;现写法 `false, false, true` = ambient / visible / showIcon)——这两个是**常驻资源类效果**,药水粒子只会糊住视野、无信息量。
  **显示不受影响**:HUD 与物品栏效果面板的显示闸门是 `showIcon`(外加 Forge/NeoForge 扩展 `isVisibleInGui`,默认 `true`),`visible` 只控粒子。已核实两版本原版源码:1.20.1 的 `PotionUtils.getColor` 跳过不可见效果、全部不可见时返回 0,`LivingEntity` 据此不生成粒子;1.21.1 由 `LivingEntity#updateSynchronizedMobEffectParticles` 逐效果经 `EffectParticleModificationEvent` 判定(事件默认取 `MobEffectInstance#isVisible()`,NeoForge 侧也能用 `setVisible(false)` 覆盖)。
  **禁止**用 `showIcon=false` 关粒子(会把图标/层数/倒计时一起关掉);今后新增同类「常驻 + 层数/倒计时可见」的资源效果必须同样 `visible=false`。改动由 `EFFECT-DECAY-FLICKER` 用例的 `AP_F1_STATE:…:vis=…` 读数守护(旧的 `*_VIS:0` 读数随已移除的旧用例一并移除)。
- **增删入口**:统一使用 `ChargeManager.addStacks / consumeOne / removeAll / getStacks / hasCharge`(内部委托 `ChargeEffect`,移除走 `ModEffectRemoval` 内部通道以绕过"禁止移除本模组效果"的拦截);**禁止直接 addEffect/removeEffect 改层数**。
- **充能筹码一律追加充能点数计数器(统一格式,无例外,必须遵守)**:所有充能类筹码(现有 10 个:跃迁引擎/能量回收器/电流剑/高级外设/电流核心/电击手套/安全气囊/永动机/电磁炮/原初核心;今后新增的同样适用)的 tooltip **必须**追加「当前充能」计数器,且**只能**经统一入口与统一 lang key 实现,不得各筹码自行拼字符串或另建 key:
  1. **实现**:`event/ModTooltipHandler.addChargeCounter(tooltip, player)`(共享辅助方法,内部走 `addSignCounter`;`player` 为空自动跳过)。每个充能筹码的 `stack.is(...)` 分支在其 `addChipLines` 描述之后各调用一次。
  2. **lang key(中英成对)**:`tooltip.astral_dice.chip.charge` —— 中文 `"当前充能：§e%s§7 / §e%s§r"`、英文 `"Current Charge: §e%s§7 / §e%s§r"`。
  3. **参数与顺序**:`ChargeManager.getStacks(player)`(当前层数)在前、`GameplayConstants.CHARGE_MAX_STACKS`(上限 20)在后,顺序不得颠倒。
  4. **排版与配色(统一格式)**:位于该筹码 tooltip **末尾**,前置一个空行(`addSignCounter` 自动插入),即「描述(可多行)→ 空行 → `当前充能: X / 20`」;标签「当前充能」为普通灰 `§7`、数值为黄 `§e`、` / ` 分隔符为灰、行尾以 `§r` 完整复位(实测两版本 zh/en 行尾均为 `§r`;`§r` 位于值串末尾、不影响显示,`scripts/audit/tooltip_color_audit.ps1` 的 R0 规则不判违规)。
  5. **禁止**:省略计数器、改标签文字、改颜色或分隔符、把上限改成配置项或硬编码非 `CHARGE_MAX_STACKS` 的值、为单个筹码另写一份计数器。
- **死亡不丢失**:`PlayerLifecycleHandler` 死亡前调用 `ChargeManager.preserveOnDeath`、重生时 `restoreAfterDeath` 恢复层数(内存态 `UUID → 层数` 映射;`clearDeathPreserved` 为登出/异常路径兜底)。
- **冷却接入点**:立牌主动入口经 `WeirdDiceHandler.signCooldownTicks` 与 `MosesSignItem.signCooldownTicks` 调用 `ChargeManager.signCooldownTicks`(**先封顶、再按诡异骰子减半**);效果牌入口在 `item/card/EffectCardPeriod` 设置冷却处与 `ModTooltipHandler.effectCardCooldownSeconds` 统一调用 `ChargeManager.effectCardCooldownTicks`。**新增立牌主动/效果牌冷却入口时必须走这些方法**,否则充能上限不生效。
- **获得/消耗来源(已接入)**:详见「筹码一览 → 充能类」的 10 个筹码(跃迁引擎 +2 / 能量回收器 每 150 米 +1 / 电流剑 每 10 击杀 +2 / 高级外设 赐福 -1 / 电流核心 每次使用主动技能 +1 / 电击手套 每次使用效果牌 +1 / 安全气囊 致命伤害 -6 / 永动机 赐福 +6 / 电磁炮 攻击敌对目标 -6 / 原初核心 消耗充能转赋能)。赐福类钩子在 `combat/DiceCombatEvents` 骰神赐福**新周期开始处**;攻击力加成在 `combat/DiceCombatModifiers`;**消耗**类(电流核心立即完成立牌主动冷却)在 `BaseSignItem.performSkill` 的冷却分支。新增来源一律调用 `ChargeManager.addStacks/consumeOne`。
- **跨版本一致**:两子项目共用同一套 `ChargeManager`/`ChargeEffect` 语义(类名、常量名、调用点一致),仅底层存储与属性修饰器写法按平台差异实现(1.20.1 无数据组件/附件 API,走 `component/AttachedDataKey` Capability)。

## 目标选择器规范（Target Selector）— **现行口径（以 `multi-dev-next` 实装为准）**

> **沿用与边界**:本节上半部分是历史沿革（`dev-targetselector` 分支已不存在,现存 dev 线**只有** `multi-dev-next`（历史上并存的 `wt/2.0.0-vnext` 已于 2026-09-17 删除,见上方版本号说明）,版本 **`2.0.0-SNAPSHOT.10`**);该分支 mod 内 `target/` 包在现存 dev 线**只剩五个文件:`TargetSelectionManager.java`、`SignSelectionGate.java`、`SelectorTargets.java`、`SelfTargetable.java`、`HoldToSelect.java`**（2026-09-18 t26 按实测清单更正为两个;同日晚 **t46** 再按实测更正为四个 —— `SelectorTargets` 由 `f6f03a3` 引入、`SelfTargetable` 由 `e9fc7b4` 引入、`HoldToSelect` 由 2026-09-25 本批引入,三者均为**移植必带项**,见下方 `### 现行口径` 第 12 条与第 14 条）,`TargetType`/`TargetSelectionAction`/`TargetSelectionRegistry` 已下沉到共享库 `com.merlinkitsune.starenginelib.target`;现存 dev 线另有 `client/TargetSelectionHighlighter`、`client/TargetOutlineCapture`、`network/TargetSelect{Start,Confirm,Cancel}Payload`、`event/TargetSelectionTestCommand`。主线仍无 `target/` 包、仍用「待命」等待器(见「已统一命名的效果/数据」)。
>
> ⚠️ **`target/SignSelectionGate` 属现行口径,移植（`forge-1.20.1` / `neoforge-26.1.2`）时必须一并带上**:它是**五个**「选择器类」立牌（占星师 `haiqing_weak_mark` / 秘密侦探 `bonnie_undercover` / 枪匠 `moses_apply_broken` / 游戏大师 `ren_privilege` / 史莱姆 `lulu_healing_slime`,2026-09-19 追加）的**前置门控登记处** —— 「按下主动键只开会话、不施效」靠它 `arm` 记下待执行动作，确认合法目标后才由 `BaseSignItem.resumeGatedActiveSkill` 恢复原流程；**漏掉它会让这五个立牌退化成「按下即施效」**。两发布线现存实现逐字节对等（`SignSelectionGate.java` 均 2975 字节、内容同构）;移植口径见下方「### 现行口径」第 12 条与「立牌选择器类主动的『前置门控』」小节。
>
> ⚠️ **下半部分（`### 现行口径`）是移植的唯一依据**:`forge-1.20.1` / `neoforge-26.1.2` 的移植**必须**按它实现 —— 上半部分若干条目是**已删除的旧行为**（Enter 确认键、客户端键盘拦截 Mixin、`LevelRenderer.renderLineBox` 描边），按上半部分照搬会把刚移除的行为带回来（见 `### 现行口径` 末尾的「禁止回归」）。

目标选择器为**通用第一人称指定目标框架**：立牌主动技能 / 效果牌可注册 `TargetSelectionAction` 到 `TargetSelectionRegistry`（现存 dev 线二者已移至共享库 `com.merlinkitsune.starenginelib.target`），再调用 `TargetSelectionManager.start(ServerPlayer, actionId)` 进入选择模式；玩家准星瞄准目标、确认后由服务端对该目标施加动作效果。

- **架构**（`neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/`）：
  - `TargetType`（现存 dev 线位于共享库 `com.merlinkitsune.starenginelib.target`）：PLAYER / ENEMY / LIVING / **ENEMY_OR_RIVAL**（客户端过滤 + 服务端权威校验共用；排除选择者自身）。ENEMY_OR_RIVAL = 敌对生物(红) 或 非队友玩家(黄,选择者无队伍时对所有玩家生效),供占星师(虚弱印记)/秘密侦探(隐匿调查)等立牌主动使用;**匹配规则（该描述已过时,见下）**: `target instanceof Enemy` 或 `target instanceof Player other && other != selector && (selector.getTeam() == null || selector.getTeam() != other.getTeam())`。⚠️ 现行口径 = 可选中判定统一走 `target/SelectorTargets`（`ENEMY` → `HostileTargets.isHostile(target)` = 敌对生物 ∪ 已被激怒的中立生物；`ENEMY_OR_RIVAL` → 该判定 ∪ 非队友玩家分支），**禁止**在玩法代码里再写裸 `instanceof Enemy`（2026-09-18 t46b 按 R2-03 标注）。
  - `TargetSelectionAction`（现存 dev 线位于共享库 `com.merlinkitsune.starenginelib.target`）：`id()/targetType()/radius()/apply(player,target)`；动作自行负责前置校验（如立牌冷却 / 效果牌出牌锁）。
  - `target/TargetSelectionManager`：服务端会话（每玩家一个，token 随机），start/confirm/cancel/过期/登出·死亡清理；确认时二次校验 token、时效、目标存活、类型、距离。
  - `client/TargetSelectionClient`：客户端状态机（准星 `player.pick(radius,...)` 射线、确认/取消、输入接管）。
  - `client/TargetSelectOverlay`：中央 HUD **只画一行**「目标名 + 距离 + 类型标签」（`hud.astral_dice.target_select.target` + 字面量 `" · "` + `hud.astral_dice.target_select.tag.<后缀>`，整行用 `highlightColor` 着色；后缀由 `TargetSelectionClient#targetTagKey(...)` 推导 = hostile / teammate / pet / neutral / player），`Component` 经 `guiGraphics.drawString`，注册于 CROSSHAIR 之上，**F1 隐藏 HUD 时整层不画**；其余提示一律走 actionbar（见 `### 现行口径`）。
- **确认 / 取消（❌ 旧口径已作废）**：~~确认 = 鼠标右键（默认）或 `CONFIRM_TARGET_KEY`（默认 Enter，可改绑）；取消 = Esc（不打开暂停界面）~~ ⇒ 见下方 `### 现行口径` 第 1–5 条（左键确认 / 右键仅提示 / 右键+潜行取消 / ESC 菜单取消 / J 取消）。
- **输入锁定（❌ 旧口径已作废）**：~~键盘经 `mixin/client/KeyboardHandlerMixin`（`KeyboardHandler.keyPress` HEAD）拦截除 移动键(WSAD/跳跃/潜行/疾跑)/Enter/J/Esc 外的全部按键…；`InputEvent.MouseButton.Pre` 右键=确认~~ ⇒ 该 Mixin **已删除**、键盘不再被模组吞掉、右键不再作确认；现行输入锁定口径见下方 `### 现行口径` 第 6 条。
- **高亮渲染（❌ 旧口径已作废）**：~~`RenderLevelStageEvent.Stage.AFTER_ENTITIES` + `LevelRenderer.renderLineBox` 描边 AABB~~ ⇒ 现为**公开 API 构造的实体棱柱边框** + **可见外框包围盒**，见下方 `### 现行口径` 第 7–8 条；颜色 友方绿 `0x55FF55` / 敌对红 `0xFF5555` / 中立黄 `0xFFFF55`、仅本地渲染（单向）不变。敌我判定：同队玩家 / `OwnableEntity`(owner=选择者) = 友方；`net.minecraft.world.entity.monster.Enemy` = 敌对（**该行口径已过时**：按现行「敌对目标」判定规范，应为 `Enemy` **或**已被激怒的 `NeutralMob`，见下方「「敌对目标」判定规范」一节）；其余中立。整合包 Sodium 0.8.13 + Iris 1.8.14-beta.1 下须保持正常（自动化测试 TC11 验证）。

### 现行口径（移植依据 · 2026-09-18 起，`multi-dev-next` 实装）

> 逐条 = 现实现事实（含代码锚点，可直接核对）。移植 `forge-1.20.1` / `neoforge-26.1.2` 时**按本节实现**，上方的历史条目只作沿革参考。
>
> **锚点口径（2026-09-18 t26 校正）**：本节锚点一律以「**文件 + 符号名**（类/方法/常量/分支）」为准 —— 行号只随**同一版本**的代码存在意义，重构或移植后必然漂移，**不构成契约**；按符号名搜索即可定位（例：`client/TargetSelectionClient.java` 的 `#onMouseButton(...)`）。历史行号锚曾出现 ±5 行内的偏移（内容仍在，属可定位性瑕疵）。

1. **左键 = 确认**：准星命中合法目标即提交（`TargetSelectConfirmPayload`）；**无有效目标时不提交**，只弹 actionbar `msg.astral_dice.target_select.no_target`（`client/TargetSelectionClient.java#confirmByPrimaryClick()`，DEBUG 行 `[Astral Dice][TargetSelectPrompt] key=left action=confirm|no_target`）。
2. **右键（不潜行）= 对自身使用**：**当前**未实现 `target/SelfTargetable` 的选择器类立牌与演示动作（`allowSelf()` 恒 `false`：占星师 / 秘密侦探 / 枪匠 与演示动作）都只弹 actionbar `msg.astral_dice.target_select.self_unsupported`，**不发包、会话保留**（`TargetSelectionClient.java#useOnSelfBySecondaryClick()`，DEBUG 行 `key=right action=self_unsupported`）。**管线已就绪**：动作若同时实现 `SelfTargetable` 并让 `allowSelf()` 返回 `true`，该标志经 `TargetSelectStartPayload.allowSelf` 下发，客户端右键改为发 `TargetSelectConfirmPayload(token, 自己)` 并 `deactivate()`，稳态提示也换成 `prompt.no_target_self`（**注意**：库内 `TargetType.matches` 仍排除选择者自身 ⇒ 真正启用还需同时放宽目标类型校验，见 `target/SelfTargetable` 的 javadoc）。
3. **右键 + 潜行 = 取消**（`TargetSelectionClient.java#onMouseButton(...)` 的「右键 + 潜行」分支，DEBUG 行 `key=right_sneak action=cancel`）。
4. **ESC = 原版照常打开暂停菜单，菜单一打开即取消**：键盘 ESC 不再被模组拦截（键盘 Mixin 已删除），取消时机在 `ScreenEvent.Opening` 收到 `PauseScreen` 时（`TargetSelectionClient.java#onScreenOpening(ScreenEvent.Opening)` 的 `PauseScreen` 分支，DEBUG 行 `key=esc action=cancel`）。⚠️ 会打开菜单是**现行为**，不再是旧口径的「不打开暂停界面」。
5. **J（主动技能键 `ACTIVATE_SIGN_KEY`，默认 J）= 取消**：走 `KeyMapping#consumeClick` 消费（`client/KeyBindingSetup.java#ClientEvents.onClientTick(...)` 的 `ACTIVATE_SIGN_KEY.consumeClick()`），**不是**键盘拦截；DEBUG 行 `key=j action=cancel`。
6. **输入锁定（鼠标 + 滚轮 + 界面）**：
   - `InputEvent.MouseButton.Pre`：选择期间按下左键=确认、右键=自用提示/取消，随后**一律 `setCanceled(true)`**（原版攻击/使用/中键都不生效；`TargetSelectionClient.java#onMouseButton(...)`）；
   - `InputEvent.MouseScrollingEvent`：选择期间**拦截滚轮**（防切栏/缩放；`TargetSelectionClient.java#onMouseScroll(...)`）——⚠️ **「手持即选择」类会话例外（2026-09-19 用户报告并裁决）**：效果牌主手一持就自动开会话，此时**必须放行滚轮**（玩家正是靠滚轮换槽把牌换下主手来收官，见第 14 条与 `target/HoldToSelect#stillHeld`），拦滚轮等于把牌焊在手上（旧行为：可释放时滚轮不可用）。判据 = 客户端会话的 `holdToSelect` 标记（`false` 才 `setCanceled(true)`），故**立牌主动（按键开局）会话的拦截行为逐字不变**；回归由 `LIVING-PAGE-*` 的 `SW1/SW2`（放行：滚一档 ⇒ `selectedSlot` 0→1 且会话按 `cancel (released)` 收官）与 `SELECTOR-KEYS-*` 的 ⑥-a/⑥-b（拦截：会话中滚轮不改槽位；取消后正对照改槽位）双向钉死；
   - `ScreenEvent.Opening`：打开任意界面即取消；**两处例外/特例** —— `ChatScreen` **豁免**（命令聊天是刻意保留的通道，含自动化注入命令）、`PauseScreen` = 上面的 ESC 取消时机（`TargetSelectionClient.java#onScreenOpening(ScreenEvent.Opening)`）；
   - **键盘不再被模组吞掉**：移动键/F3/E/T/H/第三方模组按键全部照常工作。
7. **边框渲染 = 公开 API 构造的实体棱柱**（`client/TargetSelectionHighlighter.java` 的 `PRISM = RenderType.create(...)` 常量 + `emitPrismBorder`/`emitBoxSides`/`emitQuad` 三个私有方法）：`RenderLevelStageEvent.Stage.AFTER_ENTITIES` + `RenderType.create("astral_dice_target_prism", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, false, false)` + `RENDERTYPE_ENTITY_SOLID_SHADER` + 半透明关闭/`LEQUAL_DEPTH_TEST`/`COLOR_DEPTH_WRITE`/`CULL`/`LIGHTMAP`/`OVERLAY` + 纯白纹理 `assets/astral_dice/textures/special/blank.png`（新增 16×16 全不透明，颜色完全由顶点色给出）+ 12 条边各 4 个侧面四边形（半宽 = 线宽/2、`FULL_BRIGHT`、`NO_OVERLAY`、UV(0,0)、法线 (0,1,0)），`buffers.endBatch(PRISM)` 收尾。**不是** `LevelRenderer.renderLineBox`、**不是** `RenderType.lines()`（该路径已删除）。
8. **框盒 = 可见外框包围盒（左右/前后/顶面只外扩；底面按 `BOTTOM_LIFT` 抬升）**：`TargetOutlineCapture.outlineOf(entity)` = **碰撞盒 ∪ 逐帧实测模型外框**，再按方向处理（`TargetSelectionHighlighter.java#borderBox(...)`、`client/TargetOutlineCapture.java#outlineOf(...)`/`#measure(...)`）—— 左右/前后/顶面 = `inflate(线宽/2)`；**底面 = `Math.min(outline.minY + h + BOTTOM_LIFT, outline.maxY + h)`**（`h` = 线宽/2、`BOTTOM_LIFT = 0.02` 格，2026-09-18 `b6b4e37` 按用户要求「避免嵌入地面造成闪烁」引入）⇒ 下棱柱最低点 = `outline.minY + BOTTOM_LIFT`，**与线宽无关、恒高于脚底 0.02 格**。实测外框来自 `RenderLivingEvent.Post` 里把该实体当前姿态的模型画进「只记坐标」的 `VertexConsumer` 探针取 min/max，天然包含模型几何、部位动画旋转、幼年体模型内缩放与 `SCALE` 属性。线宽：命中目标 1/16、准星命中但不可选 1/24、半径内其它可选 **1/128**（第三档 2026-09-19 由 1/64 收细一半，加大「已指向 / 未指向」的区分度）；着色 友方绿 `0x55FF55` / 敌对红 `0xFF5555` / 中立黄 `0xFFFF55`。
9. **提示分工**：中央 HUD **只有一行**「目标名 + 距离 + 类型标签」（`client/TargetSelectOverlay.java#render(...)` 的 `guiGraphics.drawString` 行，整行组成见上方「架构」的 `client/TargetSelectOverlay` 条），**F1 隐藏 HUD 时整层不画**（见第 13 条）；其余提示**每 tick 经 `ActionBarManager.show(component, 40)` 走 actionbar**（`TargetSelectionClient.java#refreshActionBarPrompt(...)`，内调 `ActionBarManager.show(prompt, ACTIONBAR_TICKS)`）—— 稳态 = 四态 `prompt.{valid|rejected|no_target_self|no_target}` + 末尾黄色 `time`（见第 1/2 条与第 9 条上方「架构」），瞬态 = `self_unsupported` / `cancelled` 等 40 tick 点击反馈（**不带**剩余时间，F-V2 裁决）。⚠️ `msg.astral_dice.target_select.active` 与 `…rejected` 现为**死键（源码零引用）**，lang 条目保留仅为兼容，**不得**再当作「默认常驻提示」引用（2026-09-18 t46b 按 R2-01 更正）。**四态的可断言读数（2026-09-19 用户裁决「补，仅 DEBUG，三条线对等」）**：① 有效目标 → DEBUG `[TargetSelectionClient] target=<id>(<名字>) hostile=… friendly=…`；② 准星命中但不可选 → **新增** DEBUG `[TargetSelectionClient] rejected=<id>(<名字>) hostile=… friendly=…`（复位时 `rejected=none`）；③ 无目标 → DEBUG `target=none`（**只在 `newTarget != currentTarget` 时打印**，故必须先有目标再让它消失）；④ 无目标且该技能允许对自身使用 → 常驻提示走 `prompt.no_target_self`（**2026-09-25 起可达**：实现 `SelfTargetable` 且 `allowSelf()=true` 的动作 = 游戏大师 `ren_privilege`、三张可自用效果牌（`express_delivery` / `luxury_feast` / `berserk`）与**史莱姆立牌 `lulu_healing_slime`**（2026-09-19 目标选择器重写时追加）；其余动作仍走 `prompt.no_target`）。三条线（1.21.1 / 1.20.1 / 26.1.2）**同口径、同措辞**；②态的实例化口径见 `scripts/test/cases/SELECTOR-PRISM-REJECTED-26.1.2.json`（**必须在会话激活状态下**把不可选实体（村民）放在**准星正前方** `~ ~ ~4`；`mt_inject` 不能移动视角，加横向偏移会命中失败 —— 实测踩坑）。
10. **禁止回归（移植时逐条守住）**：已删除的 Enter 确认键 `CONFIRM_TARGET_KEY`（与其 10 tick 就绪门槛、键盘白名单判定）与客户端键盘拦截 Mixin（`mixin/client/KeyboardHandlerMixin`，含 `astral_dice.mixins.json` 的 `client` 数组条目）**不得再被引入**；`LevelRenderer.renderLineBox` 描边路径同样不得再引入（边框一律走第 7 条的实体棱柱）。
11. **可见外框捕获的已知边界（2026-09-18 t23 登记；t26 按源码口径校正措辞；t46 补底面口径）**：`outlineOf(entity)` = **碰撞盒 ∪ 逐帧实测模型外框**，左右/前后/顶面再 `inflate(线宽/2)`、底面按第 8 条 `BOTTOM_LIFT` 抬升 ⇒ **除底面这一处有意抬升（0.02 格）外，只外扩、绝不内缩**。两类退化：
    - ① 渲染器各自覆写的 `scale()` / `setupRotations()` 分支（1.21.1 例：充能苦力怕 2× 脉冲、死亡/睡眠/旋转攻击姿态、`CreeperRenderer#scale`）**无法**在 `TargetOutlineCapture#measure(...)` 里重放 ⇒ 这类实体取「**碰撞盒 ∪ 未施加该覆写变换的实测外框**」——**不是**「只剩碰撞盒」：实测外框仍参与并集，故结果仍 ≥ 碰撞盒、也 ≥ 无该覆写时的可见模型；
    - ② **从未被渲染**的实体（视锥外/被其它模组取消渲染）**没有实测值** ⇒ 真正退化为碰撞盒（此时该实体自己也不在画面上）。
    ⇒ 判定口径：**正常（准星命中 / 已被渲染过至少一帧）情况下边框绝不小于可见模型**（底面按第 8 条抬升 0.02 格除外 —— 那是有意为之，不算内缩）；两类退化都**只可能偏大、绝不内缩**，属**已登记边界**，不要当作「边框小于模型」的同类缺陷重开（目标重新进入画面并渲染一帧后，实测外框立即恢复）。源码口径见 `client/TargetOutlineCapture.java` 的类 javadoc「已知未覆盖」段与 `#measure(...)` 实现。
12. **立牌门控 `SignSelectionGate` 与可选中判定 `SelectorTargets` 都属现行口径（移植必须一并带上；2026-09-18 t26 登记门控、t46 补可选中判定与 `SelfTargetable`）**：`target/` 包现行**五个文件** —— `TargetSelectionManager.java`、`SignSelectionGate.java`、`SelectorTargets.java`、`SelfTargetable.java`、`HoldToSelect.java`（`SignSelectionGate` 与 `SelectorTargets` 都曾被本文档漏写：**移植者据旧文会漏掉门控、或让可选中判定退回裸 `instanceof Enemy`**）。五个「选择器类」立牌（占星师 `haiqing_weak_mark` / 秘密侦探 `bonnie_undercover` / 枪匠 `moses_apply_broken` / **游戏大师 `ren_privilege`** / **史莱姆 `lulu_healing_slime`**（2026-09-19 追加）,**共五个**）走「先开会话、确认后才施效」两段式：按下主动键时 `BaseSignItem.performSkill` 的第 2.5 步只做 `TargetSelectionManager.start` + `SignSelectionGate.arm(...)` 登记待执行动作并立即 `return`（**不施效/不发牌/不进冷却/不充能**）；`TargetSelectionManager#confirm` 校验通过后**先发通用「已确认」提示**、再调用动作 `apply`（效果/冷却/充能由动作自写；动作自带的专属提示因此成为该 tick 最后一条、可见的那条），再由恢复点 `BaseSignItem.resumeGatedActiveSkill` 完成「风扇筹码发牌 + 抛 `SignActiveTriggeredEvent`」；取消/超时/会话被替换/登出/死亡 ⇒ 待执行记录清除、该次主动等同**未使用**。移植时**四个部件都要**：`SignSelectionGate`（门控登记处）、`BaseSignItem` 的第 2.5 步门控 + `resumeGatedActiveSkill`（恢复点）、各立牌的 `selectorActionId()` 覆写、**`SelectorTargets`（可选中判定唯一入口，三处调用点 = 客户端准星判定 / 客户端半径内高亮 / 服务端 `TargetSelectionManager#confirm`）**；**缺 `SignSelectionGate` 会让立牌退化为「按下即施效」；缺 `SelectorTargets` 会让判定退回裸 `instanceof Enemy` ⇒ 被激怒的中立生物不可选（`f6f03a3` 刚修掉的缺陷会在移植线重现）**。另有 `SelfTargetable`（`allowSelf()` 缺省 `false` 的可选接口，`TargetSelectStartPayload.allowSelf` 的来源）—— **当前由游戏大师 `ren_privilege`、三张可自用效果牌与史莱姆立牌 `lulu_healing_slime`（2026-09-19 追加）实现并返回 `true`**：该会话允许对自身施放，客户端常驻提示走 `prompt.no_target_self`；未实现它的动作照旧弹 `self_unsupported`、`allowSelf` 恒为 `false`。另有 `HoldToSelect`（`boolean stillHeld(Player)`）——「手持即选择」的收官判据接口，**缺它效果牌会话不会随松手关闭**（见第 14 条）。两发布线现存实现对等（`SignSelectionGate` 两线均 2975 字节、内容同构）。

13. **F1（隐藏 HUD）下本模组 overlay 一律不画（2026-09-18 t46，用户裁决「加守卫对齐 1.20.1」，两线同形）**：`client/TargetSelectOverlay#render(...)` 与 `client/ModClientEvents$ActionBarOverlay#render(...)` 首行均为 `if (mc.options.hideGui) return;`。背景：1.20.1 原版 `GameRenderer#render` 在 `hideGui` 时**整块跳过** `gui.render`（forge 源 `:949-953`，该线天然不画），1.21.1 原版无此整块跳过（neoforge 源 `:1075-1079` 只包住 `renderItemActivationAnimation`）⇒ 不自行守会让选择器 HUD 与 actionbar 提示在 F1 下仍然显示、两发布线行为不一致。该差异已作为**已修平台差异**登记进 `scripts/test/TESTING-SPEC.md` 附录 A。细节见下方「立牌选择器类主动的『前置门控』与冷却约定」小节。

14. **效果牌「手持即选择」（2026-09-25 用户裁决；两发布线同构，移植必带）**：四张可对他人使用的效果牌（加急加快 `express_delivery` / 奢华大餐 `luxury_feast` / 你有我有 `you_have_i_have` / 狂暴 `berserk`）**不再需要按键** —— 把卡牌拿在**主手**上即自动开启目标选择器，**卡牌离开主手即关闭**，且该会话**没有倒计时**（服务端 `Session.holdToSelect = true` ⇒ `expireTick = 0`、下发载荷 `durationTicks = 0`；客户端 `expireTick = Long.MAX_VALUE`、`remainingSeconds()` 恒 0）。四件套缺一即退化：
    - **服务端触发**：`PlayerTickEvents` 每 tick 对 `ServerPlayer` 调 `BaseEffectCardItem.tickHeldSelector(ServerPlayer)` —— 主手是选择器类效果牌、当前不在会话中、且未被抑制闩挡住时 `TargetSelectionManager.start(...)`；1.20.1 的 `PlayerTickEvent` 每 tick 派发两次，该路径**幂等**（第二次因 `isSelecting` 直接返回），无副作用。
    - **收官两路**：服务端 `TargetSelectionManager#tick` 用 `target/HoldToSelect#stillHeld(player)`（`SelectorAction` 的实现 = 比对主手物品 `heldCardMatches`）判定，松手即 `cleared … reason=released`（手持类会话**不参与** 30 秒超时）；客户端 `client/TargetSelectionClient#tick` 用同源判据 `holdsCardForAction(mc)`，松手走 `releaseByHeldItem()`（**只本地 `deactivate()`、刻意不发取消包** —— 否则服务端收尾会从 `reason=released` 变成 `cancel`，并多写一条无意义的抑制闩）。
    - **抑制闩**：显式取消（J / 下蹲+右键）会写 `HOLD_SUPPRESSED`（`TargetSelectionManager#cancel`，日志 `hold suppressed … (release the card to re-arm)`）——**只要该牌仍在主手上就不会再次自动开会话**；卡牌离开主手时由 `tick` 清除（日志 `hold suppression cleared`）；登出 / 死亡 / `cancelSessionForTests` 由 `clearSessionState` 一并清理。
    - **提示**：手持类会话用 `msg.astral_dice.target_select.prompt.hold.{no_target,no_target_self,rejected,valid}`（末句「移出手持即退出选择」），`steadyPrompt()` **不追加**「（剩余 N 秒）」；立牌等按键类会话仍用原四态 + 时间后缀、仍走 `GameplayConstants.SKILL_WAIT_SECONDS`（30 秒）窗口。
    - **移植检查**：`target/HoldToSelect.java`（`boolean stillHeld(Player)`）与 `BaseEffectCardItem#tickHeldSelector` / `#heldCardMatches` / `selectorActionId()` 的 **`public` 可见性**缺一不可；只补 `use()` 而不补 tick 触发会退化成「必须按键」，漏 `HoldToSelect` 则会话不会随松手关闭。
    - **目标类型（2026-09-25 追加）**：`BaseEffectCardItem#registerSelectorAction(String, TargetType, boolean)` 显式传入目标类型（两参重载 = `TargetType.PLAYER`，既有四张牌逐字不变）；**活体书页 `living_page` 是首个 `TargetType.ENEMY` 的效果牌**（仅敌对生物 ∪ 已被激怒的中立生物；不含玩家、不可对自己使用）。可选中判定仍统一走 `target/SelectorTargets`，**禁止**在效果牌侧另写判定；`SelectorAction` 因而持有 `targetType` 字段（构造注入），`targetType()` 不再是常量。

15. **锁定范围（半径）与「指向」判定（2026-09-19 用户要求，两发布线同构）**：
    - **半径来源**：`BaseEffectCardItem#registerSelectorAction(String, TargetType, boolean, double radius)` 四参重载（2026-09-19 新增）让**单张牌**声明自己的锁定范围；{@code radius <= 0}（= 三参重载）继续沿用前置库配置 `GameplayConstants.TARGET_SELECT_RADIUS`。`SelectorAction#radius()` 覆写：显式声明者原样返回，否则 `TargetSelectionAction.super.radius()`。
    - **夹取上限 = 32 格**（`TargetSelectionManager.MAX_SELECT_RADIUS`，对应前置库 `TargetSelectionAction#radius()` javadoc 的「配置上限 32 不可突破」）：`start` 用 `min(action.radius(), 32)` 生成会话半径 —— ⚠️ **不得**再写成 `min(action.radius(), 配置值)`（配置值现在只表示**缺省**范围，按它夹取会把声明 32 的活体书页静默截成 16）；`confirm` 用 `max(配置值, session.radius)` 校验距离 ⇒ 对既有动作**绝不比改动前更严**，同时让声明 32 的会话在确认这一步不被配置默认值拦下。
    - **距离一律三维（含垂直高度差）**：服务端 `Player#distanceToSqr`、客户端 `distanceToSqr` 与射线长度共用同一 `radius`，**没有**「只算水平距离」的分支（本次用户要求「包括垂直距离」即指此）。
    - **活体书页 = 32 格**：`LivingPageItem.LOCK_RANGE = 32.0D`（取契约上限；该值同时决定客户端准星射线长度、半径内高亮范围与「距目标多远还能确认」）。
    - **「指向」= 指向外框（不是指向碰撞盒）**：准星判定只认**外框盒**的相交 —— 盒 = `TargetSelectionHighlighter#hitFrameBox(entity)`（与描边同源：`TargetOutlineCapture.outlineOf` 的「碰撞盒 ∪ 逐帧实测模型外框」再按**命中档**半线宽外扩、底面按 `BOTTOM_LIFT` 抬升；⚠️ 命中档 1/16 的盒与「半径内其它可选目标」1/128 描边盒每侧差 0.027 格 —— 判定取前者，故「指向更细的那圈框」自然也算命中）；`TargetSelectionClient#updateRaycastTarget` 用 `pickFrameTarget(...)` 遍历搜索盒内实体、以 `AABB#clip` 求射线与盒的交点并取**沿视线最近**的一个。⚠️⚠️ **视点落在盒内必须单独判为距离 0**（`frame.contains(eye)` 分支）——vanilla `AABB#clip` 只认「严格从板外进入」的相交（`getDirection` → `clipPoint` 的 `startSide < minSide` 条件），起点已在盒内时**必返回 empty**；原版 `ProjectileUtil#getEntityHitResult` 正是靠 `aabb.contains(startVec)` 分支覆盖这种情况（1.21.1 `ProjectileUtil.java:77-82`、1.20.1 `:64-69`）。**贴脸正对时（僵尸伸直双臂使外框盒前伸约 0.75 格，而玩家与生物的最小中心距约 0.6 格）框会把视点整个包住**，漏掉该分支就会「画面里画着框、左键却只弹『没有可用的目标』」，与「指向外框即可选中」的初衷相反（该缺陷由 2026-09-19 独立代码验证发现并当场修掉）。⚠️ **不得**退回原版 `ProjectileUtil.getEntityHitResult`（只测碰撞盒 ⇒ 僵尸抬臂 / 蜘蛛伸腿 / 马头颈这类「可见但在碰撞盒之外」的部位点不中，正是本次要修的观感缺陷）。方块射线截断照旧（`player.pick(radius)` 的终点作为射线末端 ⇒ 墙后目标仍选不中），实体搜索盒 `inflate(1.5)`（已知边界：模型外框比碰撞盒外扩 >1.5 格的巨型实体，以及未被渲染/未被 `isTracked` 收录的实体，其盒退化为碰撞盒 —— 与描边「画不出来的也不参与判定」同源）。
    - **回归**：`SELECTOR-KEYS-*`（按键语义 / 四态读数）与 `LIVING-PAGE-*`（`SW1/SW2` 滚轮放行）不受半径/线宽改动影响；跨版本读数口径见 `scripts/test/TESTING-SPEC.md` 附录 A 的 2026-09-19（续 6）条目。

- **渲染口径的平台差异（1.20.1 实装 / 实测，26.1.2 迁移按此逐条处理；2026-09-18 t26 入档）**：
  - **(a) 状态常量的取名方式**：`RenderStateShard` 的 `NO_TRANSPARENCY` / `LEQUAL_DEPTH_TEST` / `COLOR_DEPTH_WRITE` / `CULL` / `LIGHTMAP` / `OVERLAY` / `RENDERTYPE_ENTITY_SOLID_SHADER` 在 **1.20.1 是 `protected static final`**（1.21.1/NeoForge 可直接引用）。取法 = 以子类**只读再导出同一批常量对象**：`private static final class States extends RenderStateShard { … static final TransparencyStateShard TRANSPARENCY_NONE = NO_TRANSPARENCY; … }` —— vanilla `RenderType` 本身就是这么用的（1.20.1 反编译源 `RenderType.java:22` = `public abstract class RenderType extends RenderStateShard`；`:40` 的 `ENTITY_SOLID` 直接按简单名取 `RENDERTYPE_ENTITY_SOLID_SHADER` / `NO_TRANSPARENCY` / `LIGHTMAP` / `OVERLAY`，且同为 `NEW_ENTITY + QUADS + 256 + TextureStateShard(..., false, false)` —— 与第 7 条 PRISM 的 CompositeState 逐项相同；该源取自 `forge-1.20.1-47.4.10-sources.jar`，t26 已复核）。**不复制、不重建 ⇒ 仍是原版同一个对象实例（`==` 同一）⇒ CompositeState 与 1.21.1 逐项相同**，故**不需要 mixin 访问器、也不需要 AT，refmap 零风险**（t13 已用最小 javac 工程验证该 protected 继承访问合法）。锚：`forge-1.20.1` 的 `client/TargetSelectionHighlighter` 内「只读再导出」子类（`States`）。
  - **(b) 无 `Entity#getScale()`（SCALE 属性是 1.20.5+ 才有）**：1.20.1 的渲染管道没有这一步 ⇒ `measure(...)` **不放** `pose.scale(getScale())`，该线**不存在「缩放体」形态**（t14 的缩放体用例在 1.20.1 只能以「无缩放」为基线）；**幼年体仍可测** —— 其 young 缩放在 `AgeableListModel#renderToBuffer` 的 young 分支内（1.20.1 `AgeableListModel.java:44-74`），由模型自己画进探针 ⇒ 会被捕获。该线读数行因此**不含 `scale=`**（字段为 `entity/baby/collision/model/used`；1.21.1 为 `entity/scale/baby/…`）。
  - **(c) 覆写 `scale()` / `setupRotations()` 的实体无法重放**（1.20.1 例：`StriderRenderer` / `VillagerRenderer` 幼年 0.5 缩放、死亡/睡眠/旋转攻击姿态、充能苦力怕脉冲）⇒ 框盒退化为「**碰撞盒 ∪ 未缩放实测外框**」，**只可能偏大、绝不内缩**（与第 11 条 ① 同一局限；底面按第 8 条抬升 0.02 格除外）。

- **配置**：`target_select_radius`（默认 16 格，范围 1..32，公共配置 `ModCommonConfig`）；`GameplayConstants.TARGET_SELECT_RADIUS` 运行时读取，语义 = **缺省**半径（不再充当硬上限，单项声明见第 15 条）；服务端确认距离校验用 `max(配置值, 会话半径)`，会话半径由 `TargetSelectionManager.start` 夹到契约上限 32。⚠️ **与前置库文档的措辞差异（已登记，不改库）**：`TargetSelectionAction#radius()` 的 javadoc 仍写「服务端确认校验仍以配置值为准」，而本模组按用户要求让**显式声明更大半径**的动作（当前只有活体书页 = 32）在确认时按会话半径放行；行为取向是「配置值 = 缺省值」而非「配置值 = 上限」。另一处**遗留不一致**：将来若有动作声明**小于**配置值的半径（当前没有），客户端 UX 按小半径、服务端 confirm 仍按 `max(配置, 会话)` 放行 ⇒ 服务端比客户端宽，属既有口径（改动前对全部动作都按配置值放行，故未变坏）；若将来引入小半径动作，应一并把 confirm 改成 `session.radius`。
- **调试 LOGGER（必须）**：目标选择器相关代码统一 `[Astral Dice][TargetSelection*]` 前缀 —— 现有前缀：`[TargetSelectPrompt]`（按键语义，`TargetSelectionClient#logPrompt`）、`[TargetSelection]`（服务端会话落账，`TargetSelectionManager`）、`[TargetSelectBounds]`（可见外框读数，`TargetOutlineCapture`）；标记见自动化测试流程子配置的断言清单；dev run 已 `logLevel=DEBUG`。（**已删除**的客户端键盘拦截 Mixin 不再出现在任何前缀清单里。）
- **接入方式**：动作注册后即可被任何立牌/效果牌调用；当前已注册：演示动作 `test_echo_player/enemy/living`（`/astral_dice targetselect <player|enemy|living>`，OP 权限，不施加玩法效果）、**占星师立牌 `haiqing_weak_mark`**（`ENEMY_OR_RIVAL`）、**秘密侦探立牌 `bonnie_undercover`**（`ENEMY_OR_RIVAL`）、**枪匠立牌 `moses_apply_broken`**（`ENEMY`）、**游戏大师立牌 `ren_privilege`**（`PLAYER`，并实现 `SelfTargetable#allowSelf()` 返回 `true` ⇒ 允许对自身使用）、**史莱姆立牌 `lulu_healing_slime`**（`PLAYER` + `allowSelf=true`；2026-09-19 由「自身 3 层治愈 + 瞬间治疗」重写为「对自身或被指向的玩家施加，两项范围能力以被指向的目标为中心」）、**四张效果牌动作**（加急加快 `express_delivery` / 奢华大餐 `luxury_feast` / 狂暴 `berserk` —— `PLAYER` + `allowSelf=true`；你有我有 `you_have_i_have` —— `PLAYER` + `allowSelf=false`；2026-09-25 用户裁决从旧的「下蹲右键对其他玩家」迁到选择器）。新增真实动作时在对应立牌/效果牌类中注册并调用 `TargetSelectionManager.start`；调用方必须自行处理冷却/出牌锁等前置校验（效果牌侧的触发与统一门控见 `BaseEffectCardItem#selectorActionId` / `tickHeldSelector` / `useViaSelector` / `playFromSelector`）。
- **效果牌走选择器的门控口径（2026-09-25 用户裁决，四张牌已迁移；必须遵守）**：`BaseEffectCardItem` 的 `selectorActionId()` 返回非 null 时，**触发方式是「手持即选择」而不是按键**（见第 14 条）：服务端每 tick 由 `PlayerTickEvents` 调用 `BaseEffectCardItem.tickHeldSelector(ServerPlayer)`，主手持有该牌、当前不在会话中且未被抑制闩挡住时才 `TargetSelectionManager.start`；`use()` 与 `interactLivingEntity()` 保留为**幂等兜底**（会话已开时直接返回，不会二次开会话）。开会话前的服务端前置校验顺序：专属校验 → `EffectCardPeriod.isBlocked` → `TargetSelectionManager.isSelecting` 兜底。确认目标后由 `SelectorAction#apply` 取回 `SignSelectionGate.arm(player, actionId, stack)` 登记的那张牌：按**物品**在背包里找回那张牌（⚠️ gate 存的是 `stack.copy()` **快照**，不能按对象身份消耗；1.21.1 用 `ItemStack.isSameItemSameComponents`、1.20.1 用 `isSameItemSameTags`）→ `playFromSelector(...)` 走完原出牌流程（专属校验 → 出牌锁 → `applyEffect` → `registerPlay` → 糖果/电击手套/扫地机/大当家等钩子）→ **成功才消耗一张**。取消 / 会话被替换 / 登出 / 死亡 / **松手（卡牌离开主手）** ⇒ 记录清除、**卡牌不消耗**（与立牌口径一致）；卡牌已不在身上 ⇒ `msg.astral_dice.effect_card_selector_no_card`；开会话之后出牌锁才被占满/进入冷却 ⇒ `msg.astral_dice.effect_card_selector_blocked`，同样**不消耗**。四张牌的目标类型统一 `PLAYER`、半径取配置统一值（**不再**是原来「准星 5 格」），`allowSelf` 决定能否右键自用。
- **actionbar 只有一槽位（2026-09-25 实测口径，必须遵守）**：客户端 actionbar 由 `starenginelib` 的 `ActionBarManager` 承载 —— `message`/`endTick` 各只有一个字段，`show()` 直接覆盖 ⇒ **同一 tick 内后发者覆盖先发者，玩家只看到最后一条**（`GameplayConstants.ACTIONBAR_DURATION_TICKS` 只决定这条显示多久，不排队、不叠行）。因此凡是「一次操作会连发多条提示」的路径都必须显式排定顺序：目标选择器确认路径的通用提示 `msg.astral_dice.target_select.applied` 已改为**在 `action.apply` 之前**发出，让各动作自带的专属提示（`bonnie_undercover_applied` / `haiqing_weak_mark_applied` / `ren_privilege_applied`）成为可见的那条；新增动作若自带提示，**不要**在 apply 之后再补一条通用提示。
- **立牌选择器类主动的「前置门控」与冷却约定（2026-09-17 用户裁决，取代旧「经 `isSelecting` 判断」表述）**：占星师 / 秘密侦探 / 枪匠 / **游戏大师** / **史莱姆**五个立牌的主动技能在 `BaseSignItem.performSkill` 的**第 2.5 步**（第 2 步 `isSelecting` 守卫之后、第 3 步 `handleUse` 之前）做前置门控 —— 按下主动键**只开启目标选择会话**（`TargetSelectionManager.start` 成功后 `SignSelectionGate.arm` 登记待执行记录）并立即 `return`：**不发牌、不抛 `SignActiveTriggeredEvent`、不写玩家级冷却/锁定、不施加效果、不计电流核心充能**。选择窗口取 `GameplayConstants.SKILL_WAIT_SECONDS`（秒；`expireTick = gameTime + SKILL_WAIT_SECONDS * 20L`，不写死数字）。
  - **确认合法目标后才继续原流程**：`TargetSelectionManager#confirm` 通过 token / 时效 / 目标存活 / 类型 / 距离校验后**先发通用「已确认」提示**（`msg.astral_dice.target_select.applied`），再调用 `TargetSelectionAction#apply`（施加效果 + 玩家级冷却 + 电流核心充能由各动作自行写入；各动作的专属提示在这里发出并因此成为可见的那条），最后由恢复点 `BaseSignItem.resumeGatedActiveSkill` 执行「风扇筹码发牌 + 抛 `SignActiveTriggeredEvent`（订阅方行为不变）」；门控路径**有意不补发**默认「主动技能已启动」提示（2026-09-17 用户裁决 O1，**结论不变**；理由已随 2026-09-25 F8 顺序修复更新：该 tick 已由通用提示与动作专属提示反馈过，再补发它会成为唯一可见的一条）。
  - **未选（取消 / 超时 / 会话被替换 / 登出 / 死亡）⇒ 该次主动等同「未使用」**：待执行记录被清除，**不发牌、不进冷却、不施效、不充能**。这五个立牌因此**不再保留**自己的 `handleUse`（门控分支在其之前 `return` ⇒ 原覆写不可达，且其体内是第二处 `TargetSelectionManager.start` 入口；2026-09-17 用户裁决 O2 已删除），`BaseSignItem#handleUse` 默认实现返回 `fail` 并打一条 WARN（弥补去掉 `abstract` 后失去的编译期约束）。
  - **边界（必须守住）**：只作用于**这五个**覆写 `selectorActionId()` 返回非 `null` 的立牌；其余立牌与效果牌的主动路径**逐字不变**（`selectorActionId()` 缺省 `null` ⇒ 不进第 2.5 步，走原流程立即执行并在第 5 步照旧发默认提示）。
  - **新增门控立牌时**：只覆写 `selectorActionId()` 返回自己的 action id（效果 / 冷却 / 充能写进对应 `TargetSelectionAction#apply`），**不要**再写 `handleUse`；两发布线同构实现（`neoforge-1.21.1` + `forge-1.20.1`）；26.1.2 线**已有**目标选择器全套（`target/` 四件 + `client/TargetSelectionClient` + 三个 `TargetSelect*Payload`，见 `docs/compat-26.1.2-neoforge.md`），但本批「效果牌改走选择器」尚未同步到该线（**待迁移清单见 `scripts/test/TESTING-SPEC.md` 附录 A 的 2026-09-25「效果牌『手持即选择』」条目第 ④ 条**：触发 / 收官 / 抑制闩 / 提示键 / 文案 / 探针 / 用例逐项）。旧等待器附件 `SIGN_READY_TYPE/EXPIRE` 已废弃（定义保留，禁止新代码读写）。

## 立牌 tooltip 格式规范（Sign Tooltip Format）— 必须遵守

所有立牌 tooltip 由 `event/ModTooltipHandler.onItemTooltip` 的统一辅助方法(`addSignLines`/`addChipLines`)渲染,格式如下:

```
主动技能（按下 <按键> 触发）:
<标准项>
<带子项>:
- <子项>
被动技能:
<标准项>
<带子项>:
- <子项>

<备注信息(紫色,无符号)>

<立牌计数器>
```

- 标题:金(§6),结尾带冒号;主动技能标题不含冷却倒计时(冷却由底部"冷却中"行单独显示)。
- 列表:普通项无符号、无缩进;子项带 `- ` 前缀(以 lang 中两个空格开头的行识别);**无前导空格缩进**。
- 备注区:浅紫(§d)、无标题、无列表符号。
- 颜色约定:标题=金(§6)、时间=蓝(§9)、数值=黄(§e)、效果=青(§b)、负面/冷却中=红(§c)、普通=灰(§7)、备注=浅紫(§d)。
- 时间格式:持续时间统一 `§9MM:SS§7`(蓝,尾部 §7 恢复灰),不加外括号;速率/数值保持原样。
- **彩色代码后统一用 `§7` 恢复普通灰,禁止使用 `§r`**:`§r`(RESET)会把后续文本重置为纯白 #FFFFFF(亮白),与 tooltip 普通灰不一致;`§7` 只恢复灰色,后续若再有彩色代码会被其覆盖,安全无害。
- 战斗牌 tooltip:费用置于**最上方**、黄色,格式 `Cost: ⨀⨀`——`Cost: ` 前缀 + 用 `⨀` 符号按费用重复(1费=⨀、2费=⨀⨀),费用由 `CardRegistry.cost(type, player)` 动态提供(含护法名刀折扣);下方为描述行(`点数 | 剩余次数: X`)。
- **筹码/物品 tooltip 的 lang 值若含 `\n` 换行,必须在 `ModTooltipHandler` 中用 `addChipLines(...)` 逐行拆分后添加**,禁止整段 `Component.translatable(...)` 直接入列——整段组件中的真实 `\n` 会被渲染成方块占位符(如夹心饼干-美味曾经出现的问题);与立牌 `addSignLines` 同理。
- **创建筹码必须同步创建 tooltip**:每个新增筹码物品必须同步完成 lang 的 tooltip key(zh_cn/en_us 成对)与 `ModTooltipHandler.onItemTooltip` 中对应的 `stack.is(ModItems.XXX)` 分支(多行一律经 `addChipLines`);禁止创建只有注册与 lang 名称、没有 tooltip 渲染分支的筹码。

## 语言文件同步规范（Lang Sync）— 必须遵守

- 语言文件位于 `src/main/resources/assets/astral_dice/lang/`:`zh_cn.json`(中文)与 `en_us.json`(英文),两文件 key 必须一一对应。
- **每次手动修改 `zh_cn.json` 必须同步修改 `en_us.json`**:同一 lang key 的中英文本保持对应(内容、换行结构、占位符 `%s`/`%%`、彩色代码 § 尽量一致)。
- **lang 值中的字面百分号必须写成 `%%`**:单个 `%` 经 `I18n.get`/`String.format` 会抛异常并显示 `Format error: ...`(帕秋莉手册文本即走此路径;`Component.translatable` 路径则静默回退原文)。`check_lang_sync.ps1` 会对未转义的单 `%` 输出 WARN。
- 新增/删除 lang key 时两侧必须同步新增/删除;禁止只改一侧。
- **两子项目的 lang 默认保持一致**(便于对照维护);唯一允许的差异是**某条描述依赖版本专有的原版内容**时在 1.20.1 侧删减该部分——当前唯一登记项为复仇之戟 `revenge_halberd_chip`(1.20.1 缺 6 个 1.21 新增效果),见「筹码一览 → 无流派 → 复仇之戟」;新增差异必须同步登记到 AGENTS.md,禁止随手分叉。
- 修改后必须运行同步检查:`pwsh -NoProfile -File tools/check_lang_sync.ps1 -LangDir <子项目>/src/main/resources/assets/astral_dice/lang`(对被修改的子项目执行;key 不一致退出码非 0);CI(build.yml)在构建前也会自动对**三个子项目**(`neoforge-1.21.1` / `forge-1.20.1` / `neoforge-26.1.2`,三线各一步)执行该检查,key 不一致会导致 CI 失败。
- **默认自动本地提交、不推送 GitHub**:每次改动完成后由代理自动执行本地提交(见「子项目修改默认规则」),但**不执行 `git push`**。

## 骰子槽位与配置规范（Dice Slots & Config）— 必须遵守

- **稀有度标准(仅代码层表示,映射 MC 标准 Rarity)**:白=普通 → `Rarity.COMMON`、蓝=稀有 → `Rarity.RARE`、紫=史诗 → `Rarity.EPIC`、金=传奇 → `Rarity.UNCOMMON`(MC 无金色枚举,以黄色 UNCOMMON 表示传奇)。MC 1.21.1 的 Rarity 为枚举,无法自定义新实例。新增物品的 `.rarity(...)` 按此标准选择,并尽量与图标边框颜色一致(图标边框色即品质色)。四款基础骰子按升级链配色:基础=白(普通)、黄金=蓝(稀有)、钻石=紫(史诗)、下界合金=金(传奇,**不参与赏金板**,已从 astral 池移除);1.2.0 新增骰子按各自阶层定品质(见「物品一览 → 骰子」表),不强制与升级母体同色。
- **立牌品质由合成配方决定**(见「立牌品质(配方决定)」表;立牌本身的 `.rarity(...)` 与配方分级保持一致)。

- **立牌栏固定 1**(`stand.json size=1`),所有骰子一致,不随骰子/星级变化——`DiceTier` 无立牌加成字段,`DiceCurioItem` 不做立牌槽位动态调整。
- **立牌无独立升星**(骰子升星不受影响,仍走数据组件 `weapon_enhancement.starLevel`):立牌铁砧升星与 `SIGN_STAR_LEVEL` 数据组件已移除;护法立牌(misaki)爆发/名刀的星级加成一律取**玩家装备骰子的星级**(`WeaponEnhancement.starLevel`,经 `DiceCombatContext.misakiStar` 传递),未装备骰子或 0 星骰子则无星级加成。
- 筹码栏必须佩戴骰子才有(`chip.json size=0`;1.20.1 侧 base=0 来自 IMC 注册,不是数据包 JSON),数量由 `DiceTier.chipBonus` 按星级计算(基础=星级;金=1+星级;钻石=2+星级;合金=3+星级,即 0★~3★ 分别为 0/1/2/3、1/2/3/4、2/3/4/5、3/4/5/6),由 `DiceCurioItem` 动态调整。
  - **`onEquip(SlotContext, ItemStack prevStack, ItemStack stack)` 的第 2 参是 prevStack、第 3 参才是刚装上的骰子**(Curios 5.14.1 `ICurioItem.java:81-90` / 9.5.1 `:83-92` 的 javadoc,与 `ItemizedCurioCapability#onEquip` 的 `(slotContext, prevStack, this.getStack())` 转发同构)。**禁止**把第 2 参当「刚装备的那件」用:往空槽装备时它是 `ItemStack.EMPTY`,按它推导的逻辑会**静默变成空操作**(2026-09-18 筹码栏「偶发不增加」的根因即此 —— 目标恒 0,只能靠 `curioTick` 每 20 tick 兜底,表现为「装备后要等约 1 秒」)。要「当前佩戴的那件」就**回读栏位**(`getStacksHandler("dice").getStacks().getStackInSlot(0)`),不要信任事件入参。(`onUnequip` 相反:第 3 参才是被卸下的那件。)
  - **槽位尺寸只能用「自有 id 的绝对槽位修饰符」改写**:`removeModifier(id)` → `addPermanentModifier(new AttributeModifier(id, target, ADD_VALUE/ADDITION))` → `update()`(归零时**保留 0 值修饰符**;值未变时早退,否则每 tick 都会触发一次 `SPacketSyncModifiers`)。**禁止**用 Curios 的 `grow/shrink` 差值驱动 —— 它只在 `curios:legacy`(1.20.1 = 固定 UUID `0b0eabbd-4220-4e9f-bafb-34100da2bd7e`)这个**相对累加器**上加减,而尺寸是存档里的缓存值,读到一次偏差就永久漂移且不会自愈;1.2.x 旧实现留下的 legacy 修饰符必须在迁移时清除,否则与自有修饰符**叠加翻倍**(自有 id:1.21.1/26.1.2 用 `astral_dice:chip_slots`,1.20.1 用 UUID `a5d1c9e2-6f34-4b7a-8c21-0e5d9b3f7a64`)。
  - **尺寸是缓存值,必须有主动对账入口**:登录(`PlayerLoggedInEvent`)、数据包同步(`OnDatapackSyncEvent`,用 `LOWEST` 保证晚于 Curios 自己的处理器)、复活克隆(`PlayerEvent.Clone`)各按当前骰子重算一次 ⇒ `/curios reset`、同步丢包、旧存档残值都能自愈。`getStacksHandler(id)` 落空时**必须记 WARN**(禁止 `flatMap(...).ifPresent(...)` 这种静默 no-op:该支「Curios 时序/槽位表问题」与「有 handler 但尺寸没长」的修复方向完全不同)。
  - **收缩必须保护占用中的槽位**:先把目标抬到「最靠后的非空槽位 + 1」再改写,否则「骰子槽瞬时为空 ⇒ 目标读成 0」会把筹码连同槽位一起交给背包。看板娘立牌的 +1 只在**佩戴骰子时**生效(未佩戴骰子不给筹码栏)。
- 卡牌栏槽位**只由骰子星级决定**(与品阶/阶层无关):`DiceCurioItem.CARD_SLOTS_BY_STAR = {4,6,8,12}`(0★4 / 1★6 / 2★8 / 3★12,攻防各半);`DiceTier` **没有** `cardSlots` 字段。详见「物品一览 → 骰子」的「卡牌栏星级规则」。
- **卡牌栏赐福锁定**:玩家处于骰神赐福期间,卡牌界面禁止插入/移除卡牌(`CardInventoryMenu.clicked`/`quickMoveStack` 双端拦截),界面**下方(选择区域以外)**显示红色提醒(`gui.astral_dice.card_inventory.locked`);关闭界面保存不受影响。修改卡牌栏逻辑时遵守。
- 骰神赐福持续时长现为**固定常量**:秒值 `GameplayConstants.DICE_BLESSING_DURATION_SECONDS = 60`,tick 派生值 `DICE_BLESSING_DURATION_TICKS` 在 `refresh()` 中按 ×20 计算;配置项 `dice_blessing_duration_seconds` **已删除**(历史上曾为配置项),引用一律经该常量,禁止硬编码。
- **公共配置版本号规则(用户裁决,必须遵守)**:`ModCommonConfig.CONFIG_VERSION` **只在增/删/改配置项时才 +1**;纯常量迁移、文案与文档改动一律不动版本号(2026-09-15「配置项精简」即按此规则保持 2)。版本号唯一用途是「旧配置自动备份」判据(`AstralDiceMod#backupOldConfigIfNeeded`:文件版本 < `CONFIG_VERSION` 时把旧文件复制为 `.bak`),**不**代表模组功能版本(功能版本看 `mod_version`)。代码层已核实:`config_version` 走 generic `define`,加载器 `correct()` 不会把它改写成新值,故文件里的旧版本号会长期保留,不能靠读它判断是否已升级。
- 新增骰子:在 `ModItems` 静态块注册 `DiceTier` 即可(item 参数必须传 `Supplier` 延迟解析,禁止静态初始化调用 `.get()`)。同时需在帕秋莉手册 `entries/dice/` 新增条目(见「帕秋莉手册同步规范」)。
- 新增筹码:在 `ModItems` 注册 + `ModCreativeTabs` + `datagen/ModItemModelProvider` + `datagen/ModRecipeProvider` + `curios/tags/item/chip.json` + **`astral_dice:chips` 汇总标签（`data/astral_dice/tags/item/chips.json`）** + lang(名称/tooltip) + `ModTooltipHandler.onItemTooltip` 对应分支(无需任何"筹码类型"判定链);**品质必须按图标边框颜色确定**(蓝=稀有 `Rarity.RARE`、紫=史诗 `Rarity.EPIC`、金=传奇 `Rarity.UNCOMMON`,见「物品图标约定」);属性类筹码(速度轮滑/摩托头盔/夹心饼干)覆写 `ICurioItem.getAttributeModifiers(SlotContext, ResourceLocation, ItemStack)`,修饰器 id 用 `BaseChipItem.attributeModifierId` 按物品派生(同属性不同筹码不得共用 id,否则后装覆盖先装);同时需在帕秋莉手册 `entries/chips_*` 对应流派目录新增条目(见「帕秋莉手册同步规范」)。

## Bountiful 赏金联动规范(可选前置)— 必须遵守

- 联动为纯数据驱动(无 Java 依赖),文件位于 `src/main/resources/data/bountiful/`:
  - `bounty_pools/bountiful/astral_objs.json`:需求材料池(**非传奇骰子 + 货币**:星币/袋装星币/星盘/黄金星盘);
  - `bounty_pools/bountiful/astral_rews.json`:可兑换奖励池(**astral_objs ∪ 全部卡牌 ∪ 非传奇筹码 ∪ 非传奇立牌 —— 专属效果牌除外**);
  - `bounty_decrees/bountiful/astral.json`:悬赏令(objectives=`astral_objs`, rewards=`astral_rews`)。
- **入池判定(闭集规则)**——`scripts/verify/verify_bountiful_pools.ps1` 是唯一守门(0 = 一致):
  - `objs` = 非传奇骰子 + 货币 4 项(`star_coin`/`star_coin_bag`/`star_plate`/`golden_star_plate`);
  - `rews` = `objs` + 全部卡牌(卡牌**不受**传奇排除,**但专属效果牌除外**)+ 非传奇筹码 + 非传奇立牌;
  - **传奇(物品层 `Rarity.UNCOMMON`)的「骰子」「筹码」「立牌」一律不进任何池**(货币与卡牌例外——`golden_star_plate` 为传奇但属货币,仍在池中);
  - **专属效果牌一律不进任何池(2026-09-26 用户裁决,必须遵守)**:原话「"符卡-福"与"符卡-祸"均为专属效果牌。**严禁通过除风水师立牌以外的途径获得**,遵守全局专属效果牌规则,**因此禁止进入赏金池**」。判据为**双权威且必须同时一致**:数据层 `data/<ns>/tags/{item|items}/`**`is_exclusive.json`** + 代码层 `item/card/RandomCardHandler`(`EXCLUSIVE_CARDS` / `registerExclusiveCard`)与 `item/card/ExclusiveCardUtil#isExclusive`;当前 4 项 = `effect_card_living_page` / `effect_card_fate_guidance` / `fu_card` / `huo_card`。**理由**:赏金板是一种**获取途径**,专属牌只允许由对应立牌发放(随机卡牌池早已由 `RandomCardHandler` 强制排除,赏金池同属该规则的适用面)。守门脚本已把期望集合改为「卡牌 **− 专属效果牌**」,池内残留任一专属 id 即报**致命偏差**。✅ **既有违规已按用户裁决一并清出(2026-09-26 同一轮)**:`effect_card_living_page` 与 `effect_card_fate_guidance` 原先**早已**在 `astral_rews` 内(本批之前的既存状态),用户选定「**一并清出**」⇒ 两线均已删除,**闸门不再保留任何例外白名单**(`$LEGACY_POOL_EXCEPTIONS` 已清空)。⚠️ 该删除属**玩家可见的平衡性变更**(赏金板少两个奖励)⇒ 必须写入两份 CHANGELOG 的「内容与平衡性调整 / Content & Balance」小节。⚠️ 运行守门脚本**必须显式传 `-Root <dev 工作树绝对路径>`**:其 `-Root` 默认 `'.'`,在非目标工作树的 cwd 下运行会去校验**另一棵树**并输出 `结果: ALL OK` 的**假绿**(2026-09-26 实测踩坑)。
  - 材料(4 种材料 + `blank_chip`/`blank_sign`)不进池。
- **稀有度双层映射**:本 mod 品质(白=普通/蓝=稀有/紫=史诗/金=传奇)在 Bountiful 数据层写 `"rarity"`:`COMMON`/`RARE`/`EPIC`/**`LEGENDARY`(金=传奇,金色,权重最低 6,声望门槛最高 30)**——注意与 MC 物品层不同,金品在赏金数据中必须是 `LEGENDARY` 而非 `UNCOMMON`;数据层**省略 `rarity` 字段 = `COMMON`**。
- **价值 `unitWorth` 约定**(新增条目须落在同类同品质既有价值带内,数值可微调但不得越带):
  - 骰子按阶层:基础(T0)800/700、T1(黄金档:`golden_dice`/`glass_dice`/`netherrack_dice`)3000/2800、T2(钻石档:`diamond_dice`/`emerald_dice`/`obsidian_dice`/`weird_dice`/`amethyst_dice`)8000/7500(前者 `astral_objs`,后者 `astral_rews`);T3/T4 为传奇,不入池;
  - 货币:星币 250/220、袋装星币 = 9×星币、星盘 4000/3800、黄金星盘 16000/9500;
  - 卡牌:中 300、大 800、史诗 2500、传奇卡 2000~6500;筹码:稀有 900~1500、史诗 2400~3400;立牌:稀有 1200~1600、史诗 1600~3500。
- **价值平衡式**(必须保持,Bountiful 加载时校验,违反会告警 `top value rewards cannot be matched`):
  `astral_objs` 最高价值条目(1 条,`amount.max × unitWorth`) ≥ `astral_rews` 最高价值(2 条之和) × 0.9。当前:黄金星盘 16000 ≥ (9500 + 7500) × 0.9 = 15300 ✓。
- **条目顺序** = `ModItems` 注册序(按类别分组:骰子 → 货币 → 卡牌 → 立牌 → 筹码);新增条目插在同类别既有条目之间,不重排既有条目。
- **文件格式**:UTF-8、**CRLF**、Tab 缩进、末尾换行(与 lang 的 LF 规则不同);改动后双版本四份文件必须逐字节一致(md5 相同)。
- 新增物品(骰子/星币/星盘/卡牌/筹码/立牌)时,除常规注册外,需同步维护 `astral_objs`/`astral_rews` 对应条目(若属于可兑换类)。
- 整合包自定义方式见 `docs/bountiful-integration.md`(config pack 增补/替换/排除、概率/声望调整、故障排查)。

## 骰战闪避与防御规范（Dodge & Defense）— 必须遵守

骰战结算位于 `combat/DiceCombatEvents.onLivingDamagePre`(需攻击者赐福激活 + 佩戴骰子 + 近战):

- **玩家侧闪避判定已停用**(`PLAYER_DODGE_ENABLED = false`):未佩戴骰子的玩家不再进行闪避对骰,直接进入常规防御结算。
  闪避代码**保留供未来使用**——对骰与闪避失败结算仍在 `targetDiceResult.isEmpty()` 分支内,改回 `true` 即可恢复。
  - **闪避失败伤害 = 基础伤害值 + 攻击方骰点 + 卡牌加成**;基础伤害值 = 属性攻击 + 立牌/筹码/效果攻击修饰器;
    攻击方骰点/卡牌加成均取本次实际掷出的值(非最大值);全力攻击倍率在最终伤害处适用。
- **怪物(含无护甲)**:不闪避,始终防御——每次受击掷 1d6 防御骰计入防御力,最终伤害 = 攻击力 - 防御力(按双方骰点计算)。怪物与玩家防御公式同步:防御 = 2 + 护甲÷2 + 1.4×韧性 + 防御骰 + 防御卡/修饰器(1 防御力 = 2 护甲值)。
- 其余目标(佩戴骰子的玩家)维持防御结算:防御 = 2 + 护甲÷2 + 1.4×韧性 + 防御骰(赐福中) + 防御卡/修饰器。
- **防御力折算规范(必须遵守)**:效果牌/立牌/筹码提供的防御力一律折算为**真实护甲**(1 防御力 = 2 护甲值),经 `DiceCombatModifiers.setDefenseArmorBonus` 挂到玩家 ARMOR 属性(瞬态修饰器,数值变化才增删)——骰战经护甲项(护甲÷2)自动计入,原版伤害管线(骰战伤害无穿透标志)同样按真实护甲减伤;**骰战防御修饰器仅保留战斗防御牌**(只有防御牌数值是区间变动,由 `CardRegistry` 掷骰)。负防御受护甲属性下限 0 约束自然失效。

## 物品图标约定（Item Texture Convention）— 必须遵守

- **取图规则**:新物品的图标一律从仓库根目录 `images/` **按同名文件搜索**获取（通常为中文名，例如 `images/维生素药丸.png`、`images/肾上腺素-一般.png`），复制到 `src/main/resources/assets/astral_dice/textures/item/<注册名>.png`。
- 若 `images/` 中**不存在**该物品的同名文件，或存在**疑似但文件名不重名**的文件（命名相似、仅前后缀差异等），**直接向用户询问**确认图标文件，不得擅自使用其他图标充当正式图标、不得自行猜测改名。
- **品质由图标边框颜色决定（必须遵守）**:创建/新增**筹码**（以及遵循同一标准的其他物品）时，**必须查看图标（`images/` 原图或落地纹理）的边框颜色**来确定品质，不得凭感觉或沿用模板：**蓝框 = 稀有 `Rarity.RARE`**、**紫框 = 史诗 `Rarity.EPIC`**、**金框 = 传奇 `Rarity.UNCOMMON`**（金/传奇在 MC 枚举中以黄色 `UNCOMMON` 表示，见 `ModItems` 顶部稀有度标准注释）。图标边框与代码 rarity 必须一致；若发现不一致，以图标为准修正代码，并同步 AGENTS.md 筹码表「品质」列。

## 帕秋莉手册同步规范（Patchouli Handbook Sync）— 必须遵守

本 mod 的物品图鉴《恋的规则书》（`astral_dice:astral_guide`，英文 Ren's Rulebook）由 **Patchouli** 数据驱动生成，位于 `src/main/resources/data/astral_dice/patchouli_books/astral_guide/`。**新增任何物品（材料/骰子/卡牌/立牌/筹码）后，必须同步在手册中新增对应条目**，否则图鉴缺失该物品。

### 首次加入赠送
- 《恋的规则书》首次加入赠送由 common 配置 `give_guide_book_on_first_join`（默认 `true`，GameplayConstants 常量 `GIVE_GUIDE_BOOK_ON_FIRST_JOIN`）控制；仅在玩家第一次加入世界且未领过时发放（每个玩家每世界一次，附件 `guide_book_given`，`PlayerLifecycleHandler.giveGuideBookOnFirstJoin`）。配置文件版本号保持 `1` 不变。

### 手册结构
- `book.json` 位于 `data/<modid>/patchouli_books/<书id>/book.json`(注册用);**目录/条目内容必须放在 `assets/<modid>/patchouli_books/<书id>/en_us/` 下**(Patchouli 客户端资源监听器按 `en_us` 加载,放错位置或缺少语言层级会导致章节全部不显示,且日志无报错)。
- 类别映射(新增物品按物品类别放入对应目录):
  - 材料 → `entries/materials/`;骰子 → `entries/dice/`;
  - 攻击牌 → `entries/cards_attack/`;防御牌 → `entries/cards_defense/`;效果牌 → `entries/cards_effect/`;
  - 筹码按流派 → `entries/chips_starlight/`(星光)、`entries/chips_healing/`(治愈)、`entries/chips_mark/`(标记)、`entries/chips_charge/`(充能,类别文件 `categories/chips_charge.json`,sortnum 44)、`entries/chips_other/`(泛用/无流派,sortnum 45);
  - 立牌 → `entries/signs/`。
- 类别文件(`categories/*.json`)仅在新增**类别**时修改;`book.json` 一般不动(已含必要字段 `use_resource_pack: true`——**缺少该字段 Patchouli 会判定书籍无效**)。

### 条目 JSON 模板(必填字段)
```json
{
  "name": "item.astral_dice.<注册id>",
  "category": "astral_dice:<类别目录名>",
  "icon": "astral_dice:<注册id>",
  "pages": [
    {
      "type": "patchouli:spotlight",
      "item": "astral_dice:<注册id>",
      "text": "astral_dice.guide.entry.<注册id>"
    }
  ],
  "sortnum": 0
}
```
- 有配方的物品追加 `patchouli:crafting` 页(`"recipe": "astral_dice:<配方id>"`);配方 id 必须与实际存在的配方文件一致,注意**配方 id 可能与物品 id 不同**(现有例外:星币→`star_coin_from_bag`、空白筹码→`blank_chip_duplicate`、大/史诗卡牌→`*_from_medium`/`*_from_large` 升级配方、黄金星盘→`golden_star_plate_from_plates`/`golden_star_plate_from_nether_star` 两条);无配方物品(如 `star_plate`、`attack_card_full_power`、`effect_card_fate_guidance`、`effect_card_living_page`)不写 crafting 页。
- `sortnum` 与同类物品的创造栏顺序一致。

### 语言文件
- 条目正文在 `zh_cn.json`/`en_us.json` 新增键 `astral_dice.guide.entry.<注册id>`(两侧同步,遵守「语言文件同步规范」);描述物品用途/机制,中英对应。
- **手册文本换行必须用 Patchouli 宏 `$(br)`**,不要用 `\n`/真实换行(Patchouli 1.21.1 不识别 `\n` 换行)。
- 条目正文 key 采用**分页编号** `astral_dice.guide.entry.<id>.1/.2/...`(每页 ≤ ~200 中文字符),条目 JSON 的 `pages` 逐页引用;有配方的条目,合成页独立成页放在最后。
- **介绍章节「流派」必须覆盖当前全部流派**:`entries/getting_started/playstyles.json` + `astral_dice.guide.entry.playstyles.*` 需为**星光/治愈/标记/充能四流派各一页**(充能页写明:玩家级层数资源、上限 20、由移动/传送/击杀/骰神赐福积累、**拥有充能时立牌主动冷却至多 160 秒、效果牌冷却至多 20 秒(基础值封顶)**、可换算为攻击力与移动速度或由电流核心消耗以立即结束立牌主动冷却),再加「泛用筹码(无流派)」与「队友组合技」各一页(共 7 页);**流派数量或机制变化时必须同步重写该章节 + 两份 CHANGELOG**,禁止残留「三类资源」等旧版表述(该章节曾长期停留在三流派旧版,被用户发现并要求重写)。
- **手册文本禁用 § 染色**(影响阅读);立牌条目可在第一页顶部用 `$(italic)点评$(reset)$(br2)` 放置一句斜体评语,其余内容保持精炼准确。
- 新增类别时才需要 `astral_dice.guide.category.*` 与 `*.desc` 键。

### 校验
- 新增物品后对照 `entries/` 目录逐一核对:每个注册物品都有条目、条目内 icon/text 键无拼写错误、crafting 页引用的配方文件存在。
- 手册文件是纯数据包 JSON(不参与 datagen),直接写 `src/main/resources/data/...` 即可,无需重新运行 runData。

## 编译产物上传规则（Build Deploy Rule）— 必须遵守

各子项目 `build.gradle` 已内置分发任务，`gradlew build` **构建后自动触发**，无需手动指定任务。部署目标按子项目区分：

> **整合包推送的分支口径（2026-09-17 用户裁决，必须遵守）** — 用户原话：「**该分支内所有内容均不推送整合包(严格),只推送到游戏测试目录。除非用户另行规定。**」据此的三项口径：
> ① **严格不推整合包**：`multi-dev-next` 上不向任何整合包目录写入任何内容 —— `pushToGame` 在**执行期**判定当前分支不在白名单即 `return`，只打印 `pushToGame: skipped — 分支 '…' 不在整合包推送白名单 …`；
> ② **只推游戏测试目录**：本仓库根 `run/<版本>/mods`（`pushToDevRun`）与仓库根 `build/libs`（`pushToRootBuild`）**保留**、照常随 `build` 触发，不受分支限制；
> ③ **需要时由用户另行规定**：仅当用户显式要求时才出包 —— 用 `-PdeployToPack` / `-PpackPush` 手动强推（跳过白名单），或把目标分支加入 `packPushBranches` 白名单；两种方式都属**用户另行规定**，代理不得自行启用。

**编译产物集中规则(必须遵守)**:任何版本的编译产物统一复制到**仓库根目录 `build/libs/`**(任务 `pushToRootBuild`,随各子项目 build 自动触发;按**本子项目完整版本后缀**清理旧产物,与其他版本互不误删)。根目录 `build/libs/` 为全部版本产物的统一交付目录。
⚠️ **后缀必须写全(2026-09 三线并存后为硬需求)**:自 `multi-26.1.2-neoforge` 分支起仓库同时存在 `+neoforge_1.21.1` 与 `+neoforge_26.1.2` 两个 neoforge 产物,`pushToRootBuild` 的过滤条件因此从 `contains('+neoforge_')` 收紧为 `contains('+neoforge_1.21.1')` / `contains('+neoforge_26.1.2')`——宽泛前缀会让两个 neoforge 版本**互相删除** jar。

| 子项目 | 项目测试环境(pushToDevRun) | 整合包/用户测试环境(pushToGame) | pushToGame 触发条件 |
|---|---|---|---|
| `neoforge-1.21.1` | `run/1.21.1/mods`（仓库根 run/） | `D:\.minecraft\versions\狐の航空学 Voxy Edition\mods` | 随 build 触发；**仅发布线分支 `multi-1.20.1-1.21.1` 会真正推送整合包**，其它分支（含 `multi-dev-next`）**严格跳过**并只打印 `pushToGame: skipped — 分支 '…' 不在整合包推送白名单 …` |
| `forge-1.20.1` | `run/1.20.1/mods`（仓库根 run/） | `D:\.minecraft\versions\1.20.1 模组测试\mods` | 随 build 触发；**仅发布线分支 `multi-1.20.1-1.21.1` 会真正推送整合包**，其它分支（含 `multi-dev-next`）**严格跳过**并只打印 `pushToGame: skipped — 分支 '…' 不在整合包推送白名单 …` |
| `neoforge-26.1.2` | `run/26.1.2/mods`（仓库根 run/） | `D:\.minecraft\versions\26.1.2 模组测试\mods` | 随 build 触发；**仅发布线分支 `multi-1.20.1-1.21.1` 会真正推送整合包**，其它分支（含 `multi-dev-next`）**严格跳过**并只打印 `pushToGame: skipped — 分支 '…' 不在整合包推送白名单 …` |

规则要点：
1. **推送任务随 build 触发，但整合包推送受分支白名单限制**：`pushToDevRun`/`pushToRootBuild`/`pushToGame` 三个任务均由 `finalizedBy` 随 build 触发；**`pushToGame` 在 `doLast` 里先做执行期分支判定** —— 分支不在白名单 `packPushBranches = ['multi-1.20.1-1.21.1']` 内（或无法确定分支，如 detached HEAD / 无 `.git`）时只打印 `pushToGame: skipped — 分支 '…' 不在整合包推送白名单 …` 并 `return`，**不写整合包目录**（`multi-dev-next` 即属此类，见上方「整合包推送的分支口径」）。**`-PdeployToPack` / `-PpackPush` 仍被任务读取**（三条线 `build.gradle` 均有 `def forcePackPush = project.hasProperty('deployToPack') || project.hasProperty('packPush')`，并在 `pushToGame` 的 `doLast` 里以 `if (!forcePackPush) { … }` 包裹上述白名单判定）—— 作用 = **跳过白名单手动强推整合包**（手动出包通道，保留）；强推时同时会触发「库 jar 与 mod jar 成对自检」的告警（1.21.1 / 1.20.1 原有，26.1.2 自 2026-09-17 接入库后同形）。
   ⚠️ **原文档此处曾称「该参数不再被任何任务读取（源码中仅存注释）」，与代码矛盾，已于 2026-09-17 以代码为准更正**（三线定义行/使用行：`neoforge-1.21.1/build.gradle:295`+`:335`、`forge-1.20.1/build.gradle:333`+`:381`、`neoforge-26.1.2/build.gradle:277`+`:314`）。
2. 推送时**先删除、后复制**:`pushToDevRun`/`pushToGame`/`pushToRootBuild` 三个任务均先清空目标目录中的旧产物、再复制新 jar,各目录只保留本次构建产物。清理匹配范围(**实测**,勿按"都会按后缀过滤"理解):forge-1.20.1 三个任务统一用 `/astral_dice-.+\+forge_1\.20\.1\.jar/`(仅带 `+forge_1.20.1` 后缀);`neoforge-1.21.1` 与 `neoforge-26.1.2` 的 `pushToRootBuild` 带**各自完整后缀**过滤(`contains('+neoforge_1.21.1')` / `contains('+neoforge_26.1.2')`,根目录 `build/libs/` 多版本共存,不得写成宽泛的 `+neoforge_`);`neoforge-26.1.2` 的 `pushToGame` **同样**带 `contains('+neoforge_26.1.2')`(2026-09-17 收紧:整合包目录属用户环境,不得误删其它分支放进去的产物),仅 `pushToDevRun` 与 1.21.1 侧的 `pushToGame` 匹配**任意** `astral_dice-*.jar`(这两个目标目录本身只放单一版本,故无需过滤,同时也保证旧版本号残留会被清掉)。
3. 整合包根目录不存在时（如 CI 环境）`pushToGame` 自动跳过并仅输出警告，不影响构建。⚠️ 因此**静默不部署**是最容易发生的形态(2026-09-17 实测:26.1.2 侧的 `pushToGame` 长期指向并不存在的 `D:\.minecraft\versions\26.1.2-NeoForge_26.1.2.109`,只打印 `pack dir not found, skipped`,整合包里的 jar 一直是旧时间戳)——改动 `packModsDir` 或换机后,必须**核对构建日志里确有 `pushToGame: pushed … -> <目标>` 一行**,不得只看 `BUILD SUCCESSFUL`。
4. forge-1.20.1 子项目产物分两级：`pushToDevRun` 取 `build/devlibs` 未重混淆 jar（dev 环境 Mojmap 名），`pushToGame` 取 `build/libs` 重混淆 jar（生产 SRG 名），推错方向会 `NoSuchFieldError`——不要改动该取值逻辑。
5. **禁止自动启动 runClient / 冒烟测试（自动化测试流程子配置例外）**：无流程的手动 runClient / 冒烟测试一律禁止，游戏内验证默认由用户手动运行；**仅当经「自动化测试流程（Automated Testing）」子配置（见下方章节）启动的自动化 runClient 允许**，且必须按该流程执行并产出报告。
6. **自动本地提交 + 禁止自动推送 GitHub**：每次改动完成（含构建部署）后由代理**自动执行本地提交**；但所有 `git push` 必须由用户手动执行（`deploy.ps1` 需显式 `-Push` 才推送），不得自动推送远程。
7. **编译后必须先清除 `run/mods` 中旧的本模组 jar,再置入新产物(必须遵守)**:每次执行版本编译(`gradlew :neoforge-1.21.1:build` / `:forge-1.20.1:build` / `:neoforge-26.1.2:build` / `build`)后,对每个 `run/<版本>/mods`(即 `run/1.21.1/mods`、`run/1.20.1/mods`、`run/26.1.2/mods`)按**先删除、后复制**的固定顺序处理:
   1. 先删除该目录下本模组的**全部**旧 jar——匹配任意 `astral_dice-*.jar`(含版本号不同/旧 `-rcN`/旧后缀的残留),不按后缀过滤;
   2. 确认目录内已无本模组 jar 后,再把本次构建的新产物复制进去。
   **禁止"直接覆盖式复制"**(旧版本号 jar 与新产品并存时游戏会同时加载两个本模组副本,导致行为异常甚至 `NoSuchFieldError`)。判定通过:该目录内本模组 jar **有且仅有一个**,且文件名版本号与 `build/libs` 本次产物一致、修改时间不早于本次构建。
   `pushToDevRun` 已内置该"先删后拷"逻辑(见上表第 2 条),本规则为**代理侧的强制复核要求**——自动推送失败后手工补推、或临时手工部署时同样必须遵守该顺序。

> 「发送到整合包」即上表 `pushToGame`：**随 `gradlew build` 触发**，无需额外参数 —— **但只有发布线分支 `multi-1.20.1-1.21.1` 会真正推送整合包**；其它分支（含 `multi-dev-next`）**严格跳过**，只打印 `pushToGame: skipped — 分支 '…' 不在整合包推送白名单 [multi-1.20.1-1.21.1] 内`，整合包目录不被写入、旧 jar 也不会被删。开发分支每次更新的默认落点是 `run/<版本>/mods`（`pushToDevRun`）与仓库根 `build/libs`（`pushToRootBuild`）。

### 新版本发布流程（自动本地提交）

完成一个新版本的功能/修复后，由代理自动执行（无需用户逐项指示）：
1. 更新两个更新日志文件（`CHANGELOG_ZH.md` + `CHANGELOG.md`，条目一一对应）；
2. 递增**两个**子项目 `gradle.properties` 的 `mod_version`（`deploy.ps1 -Target neoforge|forge` 可自动递增 `x.y-SNAPSHOT.N` / `x.y.z-rcN` / `x.y.z`，或用 `-Version` 显式指定；两版本保持同号，后缀各自为 `+neoforge_1.21.1` / `+forge_1.20.1`）；
3. 若改过 lang 文件，分别对**两个**子项目跑 `pwsh -NoProfile -File tools/check_lang_sync.ps1 -LangDir <子项目>/src/main/resources/assets/astral_dice/lang`；
4. `gradlew build` 同时编译并部署**三个**版本(26.1.2 与另两线同规则)——`pushToDevRun` / `pushToRootBuild` / `pushToGame`(整合包) 均默认随 build 自动触发（整合包推送仅发布线分支执行，本流程即运行在发布线上；失败则回滚版本号，不提交）；
5. **自动本地提交**（`deploy.ps1` 自动提交 `release: v<版本>`，或手工 `chore: bump version to X.Y.Z` 等），**默认不执行 `git push`**。

## 自动化测试流程（Automated Testing）— 必须遵守（子配置）
### ⚠️ 测试资产已全部清零（2026-09-20 用户指令）— 现行状态，先读本节

> 用户指令原文：「执行项目与AGENTS自检：清理所有测试项，已进行或未进行的测试项全部作废并删除，删除所有插针和测试项脚本，包括游戏环境中插入的kubejs脚本。保留测试规则，由用户对测试流程和规则进行核验，将完整的测试流程和规则向用户完整展示。」

**已删除（主树 `multi-1.20.1-1.21.1` 与工作树 `multi-dev-next` 两处同步执行）**

- 全部测试条目：`scripts/test/cases/*.json`（含 `.mt_*` 运行态）——**当前在册用例 0 条**；
- 全部探针与测试脚本（源码侧）：`scripts/test/resources/kubejs/<版本>/server_scripts/astral_*.js`；
- 游戏环境内已注入的副本：`run/<版本>/kubejs/{server_scripts,startup_scripts}/astral_*.js`；
- 历史测试报告：`scripts/test/reports/<运行id>/`（保留模板 `_template.md`）；
- 会话期测试证据：`temp/` 下的测试证据目录与日志。删除清单：`temp/cleanup-manifest-20260920-*.txt`。

**结论效力**：2026-09-20 之前的一切自动化 / 游戏内测试结论**全部作废**，不得作为验收依据；本文件下文与 `TESTING-SPEC.md` 中出现的用例名、探针命令与读数一律只作历史记录。

**保留（供用户核验）**：测试流程实现与规则文本原样保留；用户核验通过并重新授权后才可重建用例与探针。

**当前可运行性**：用例目录为空 ⇒ `--phase cases` 无可执行条目；探针已删 ⇒ `--phase launch` 预期 ERROR。**重建测试资产前，不得把本流程的任何输出当作验收证据。**

**完整展示**：`scripts/test/TESTING-RULES-OVERVIEW.md`（2026-09-20 生成）。

针对**三个子项目**（**三线同级、都必须跑**；执行顺序 `neoforge-1.21.1` → `forge-1.20.1` → `neoforge-26.1.2`，逐线出结论）的客户端渲染/输入类功能，以及任意**新增内容**与**用户指定内容**的真实游戏自动化验证。经本流程启动的自动化 `runClient` 属于「编译产物上传规则」第 5 条的**例外**；无流程的手动冒烟仍禁止。

- **测试分支（任意分支均可，2026-09-17 用户裁决「移除二重验证白名单，允许该分支执行」）**：分支**不再是前置门槛** —— 阶段 P 的分支检查已由强断言改为**信息性回显 + WARN**（发布线 `multi-1.20.1-1.21.1` 直接回显分支名；其它分支如 `multi-dev-next` 回显实际分支名并给 WARN 说明），**一律放行、不拦停**。报告「分支」一栏取运行时的实际分支名（读取失败回落 `unknown`），不再写死发布线。其余前置断言（输入法 / 遗留进程 / MCP 二进制 / 模组来源闸门 / Gradle 包装器 / 兼容栈 / run 可写）**逐条保留、一项未减**。
- **脚本语言**：全流程为纯 **PowerShell 7（pwsh）**，不使用 bash / python。平台特异的输入注入集中在 `mt_inject.ps1` / `mt_ime.ps1` 内以 P/Invoke 实现，工具链为 Windows-only（历史上的 bash + python 版本已于 2026-09-12 全部移除；旧脚本归档包见 `docs/archive/legacy_scripts_20260912.zip`）。
- **「长按」类回归必须真的按住，且必须有对照步（2026-09-14 实测教训，必须遵守）**：`mt_inject.ps1 key -Key rclick -HoldMs N` 曾**静默忽略** `-HoldMs`（始终 down→100ms→up 的单击），于是「长按右键」类用例走不到原版 4 tick 自动重复分支，用**单击也能"通过"**——判据形同虚设。现已按 `w` 键同口径实现按住语义（`-HoldMs 0` 保留旧行为，输出行回显按住时长）。凡结论依赖"重复触发"的用例，必须附一条**对照步**证明重复真的发生（如生存模式投 16 枚鸡蛋长按 3 秒后必须只剩 1 枚；创造模式下物品不消耗，**不能**用作对照）。

### 变更分级与验证口径（2026-09-19 用户裁决）— 必须遵守
> 📌 2026-09-20 登记（S9）：本节为开发线 `multi-dev-next` 专属口径，发布线 AGENTS.md 无对应节；是否回移待用户裁决。

> **核心口径（2026-09-19 二次裁决修订 —— 用户要求「减少非首要测试项目，将代码变更不直接影响的模块测试推迟或取消，仅执行最小测试」）**：
> ① **非核心功能修改不执行冒烟测试**（= 不启动游戏、不跑 `scripts/test` 的自动化流程）。
> ② **核心修改默认只跑「定向最小冒烟」**：**只运行与该改动直接触及的模块相对应的用例**；**与改动无直接关系的模块一律不在本批运行**（默认**取消**并在附录 A 登记，**不计入未完成/遗留项**；确实需要的才登记为**推迟**并写明触发条件）。
> ③ 判据是「改动是否触及**运行期行为**」，**不是文件扩展名**；**「不影响」必须举证**（见下方第 7 条），**拿不准的按更高一级处理**（宁可多跑）。

| 级别 | 典型改动 | 必需验证（缺一不可） | 不执行 |
|---|---|---|---|
| **非核心** | ① 工具链/脚本：`scripts/**`、`tools/**`；② 纯文本与文案：`**/lang/*.json`、tooltip 文案（lang 键值、卡牌/立牌 tooltip 行）、帕秋莉手册内容（`**/patchouli_books/**`）；③ 文档：`*.md`（`AGENTS.md` / `CHANGELOG*` / `TESTING-SPEC.md` 等）、`docs/**`；④ 代码注释 / javadoc；⑤ 测试夹具：`scripts/test/resources/**`、`scripts/test/cases/**` | 受影响子项目 `gradlew build`（随 build 的 `pushToDevRun` / `pushToRootBuild` / `pushToGame` 链不变）+ 相关**静态**闸门（`scripts/devtools/Test-MtSyntax.ps1`；改过 lang 必跑 `tools/check_lang_sync.ps1`）+ 自动本地提交 | **冒烟测试 / 游戏内自动化流程一律不跑**：阶段 L（`--phase launch`）、阶段 C（`--phase cases`）、`mt.ps1` 全流程 |
| **核心（默认 → 定向最小冒烟）** | 玩法逻辑与数值、网络与协议、存档/同步、注册表与数据组件、mixin、客户端渲染与输入、**数据包内容**（`src/main/resources/data/**`：配方 / 伤害类型 / 标签 / 战利品 / 进度）、`build.gradle` 的行为性改动 | ① 构建；② **只跑与该改动直接相关的用例**（映射与举证见下方第 7 条；已迁移 26.1.2 的内容按 §13.2 只对**该改动相关**的读数做比对）；③ 一旦启动游戏，**必须**通过 `mt_launch` 固有的启动期硬闸门（noai 生物 AI / 史莱姆压制 / preflight 各项），**不得**以「最小测试」为由跳过 | **与改动无直接关系的模块用例**（本批取消并登记；确需者按第 8 条登记为推迟） |
| **完整冒烟（例外 —— 命中即必须跑全清单）** | ① **发版 / Release 批次**；② **跨模块公共路径**（共享前置库 `starengine_lib`、注册表与数据组件结构、网络协议、存档/读档结构、全局伤害结算出入口如 `DiceCombatEvents`）；③ **门槛类规则自身改动**（版本互通门槛、加载器门槛、mixin 目标串、`mods.toml` / `neoforge.mods.toml` 的依赖区间）；④ **用户显式要求**；⑤ **26.1.2 迁移 / 三线一致性验收批次** | 构建 + 本规范全流程（阶段 L + 阶段 C **全清单**，按下方「测试顺序」表三线各一遍，逐线出结论） | — |
**边界与例外（必须遵守）**：

1. **同一提交内以最高级别为准**：非核心改动与核心改动在同一批（同一 commit）时，整批按**核心**处理。
2. **`assets/**/lang/*.json` 与 `**/patchouli_books/**` 属非核心，`src/main/resources/data/**` 属核心** —— 前两者只改玩家可见**文本**（语言条目、手册正文），后者直接改玩法（配方 / 伤害类型 / 标签 / 掉落 / 进度）；**不得**按扩展名一刀切。
3. **tooltip 属非核心**（用户明确列入）：只改 tooltip 文案/染色时免冒烟；但 tooltip 若是**用户指定要验证**的内容，则按「用户指定内容」走自动化流程。
4. **注释 / javadoc 属非核心**，且「只改注释 ⇒ 类字节不变 ⇒ Gradle 报 `…:jar UP-TO-DATE`」是**正常结果**（见上方「前置库配对」条），`Start-SelfTest.ps1` 已按 UP-TO-DATE 放行；**不要**为了「让 jar 时间戳变新」而 `clean` 重编。
5. **免冒烟 ≠ 免验证**：非核心改动仍必须**构建通过 + 静态闸门全绿**（`TESTING-SPEC.md` §9），并且不得据此跳过 CHANGELOG 同步、双版本对等等既有约定。
6. **等级可自查**：`pwsh -NoProfile -File scripts/test/mt_scope.ps1`（缺省读工作区变更，另有 `--staged` / `--paths <相对路径…>`）按本表回显级别与「必需验证 / 跳过项 / 何时必须升级为完整冒烟」。该脚本**只读、只建议**（退出码恒 0），与本节冲突时**以本节为准**。
7. **「不影响」的举证责任（2026-09-19 新增，必须遵守）**：要把某模块排除在最小集之外，必须给出**可证伪的影响面证据** —— 至少是「改动的入口符号 → 全仓调用点/消费点**双侧枚举** → 与用例的映射」。只有枚举结果为空、或全部落在同一模块内时，才允许只跑该模块的用例。**禁止**用「大概不影响」「以前也没坏」来缩小范围；**拿不准就按高一级**（必要时直接按完整冒烟）。**先例（可照抄的做法）**：`allow_firearm_damage` 只改 `SpellDamageRegistry.isSpellDamage()` 的第一道判定，实测该函数在两条发布线**各只有 2 个消费点**（`DamageEffectCardHandler` / `SatelliteChipItem`）⇒ 最小集只取覆盖这两条路径与相邻结算的 7 条用例（`SPELL-TRUE-DAMAGE` / `RAILGUN`×3 / `HOSTILE-TARGET-NEUTRAL` / `LIVING-PAGE` / `CARD-SELECTOR`），其余用例本批**取消**。
8. **取消 vs 推迟（必须写进批次记录）**：默认是**取消**本批运行，并在附录 A 记一行「按最小测试规则未运行 ×N（与改动无关）」，**不计入未完成/遗留项**；只有当某项在可预见窗口内确实需要时（即将发版 / 下一批会触及该模块 / 用户点名）才登记为**推迟**，并写明触发条件。**禁止**把「取消」写成「遗留问题」反复追讨。
9. **最小集 ≠ 免验证**：`gradlew build` + §9 静态闸门照旧必跑；启动期硬闸门（noai / slimeguard / preflight）属 `mt_launch` 的一部分，**不得**跳过。改动触及**共享前置库**或**跨模块公共路径**时，直接按「完整冒烟」那一行处理。
10. **二重验证不因本条缩减**：用户要求二重验证时，**第一轮「独立代码验证」照旧完整**（与用例数量无关）；只有**第二轮游戏内验证**按本条的定向最小集执行。
11. **非核心改动不写「未做游戏内验证」为遗留项**：按本条免冒烟的批次，交付说明里只写「按变更分级（非核心）未执行冒烟」，**不再**列为待办或未完成事项。
12. **构建与冒烟测试一律由主 agent 执行，禁止放进 subagent（2026-09-19 用户裁决，必须遵守）**：`scripts/test` 的阶段 P / B / E / L / C / R（含 `mt_build`、`gradlew build`、`--phase launch`、`--phase cases`、`mt.ps1` 全流程）**必须**由主 agent 亲自执行 —— 目的是让**改动与测试过程对用户可见**。subagent 只负责**代码修改**（读码/改码/自检），**不得**运行构建或任何游戏内流程；三线代码全部改完后由主 agent 统一构建三线、跑静态闸门与（核心改动）三线冒烟。构建失败时**优先**把错误回报给该文件所属 subagent 修，其已结束或连续两次未修好时主 agent 可接手，并在交付说明里记明接手原因。
### 测试顺序（必须遵守）

| 顺序 | 版本 | 子项目 | 执行条件 | 结论要求 |
|---|---|---|---|---|
| 1 | `1.21.1` | `neoforge-1.21.1` | 前置检查通过 | 独立给出 PASS / FAIL |
| 2 | `1.20.1` | `forge-1.20.1` | **1.21.1 判定 PASS**（门控） | 独立给出 PASS / FAIL |

- 1.21.1 未通过时，1.20.1 记为 `GATED`（因门控未执行），报告中显式标注为「未执行」而非「失败」——避免用未运行的版本污染结论。
- 两版本结论与综合结论汇总在 `scripts/test/reports/<运行id>/SUMMARY.md`；每版本明细在同级 `<版本>/report.md`。
- 退出码：两版本均通过 `0`；任一版本失败 `1`；综合报告未全部通过同样返回 `1`。

### 测试前清场（Pre-Test Field Cleanup）— 必须遵守

**时机**：每次冷启动**进入世界之后、运行任何条目之前**做一次即可（同一会话内跑多条时，只需在第一条之前做；用例之间不必重复）。

**落地方式（2026-09-15 用户裁决）**：由 `mt_launch.ps1` 在检测到「已进入世界」后**自动执行** —— 默认连发两次 `/kill @e[type=!player,distance=..128]`（间隔 600ms；注入通道偶发丢失，见 `scripts/test/TESTING-SPEC.md` §10-17），并在日志打印 `MT_PRECLEAN: OK — …/SKIPPED/WARN` 供报告核对。**跳过用 `--no-preclean`**，仅限「必须保留世界实体」的特殊取证。

手工补做（或换机制）时二选一：

1. **`/kill` 清场**：`/kill @e[type=!player,distance=..128]`（清玩家 128 格内的非玩家实体：残留靶、散落物、常驻敌对生物）；去掉 `distance=..128` 即清全图。
2. **难度切换清场**：`/difficulty peaceful` → 等 **≥1 秒**（敌对生物立即消失）→ `/difficulty <原难度>`。本仓库测试世界的难度是 **`easy`**（由 `mt_env.ps1` 生成 `server.properties` 写入），切回时不要凭记忆写别的值。

**为什么必须做**：`AP_*` 读数里有两类是**世界级差值**，会被任何残留实体污染 —— `self`（施放者 HP 的原始差值，**不区分伤害来源**）与 `bolt_delta`（**全局**雷击生成计数器的差值）。实测 1.20.1 testworld 里一只**常驻蜘蛛**在 `forced_survival` 下近战玩家，使一次 read 内 `php` 掉 6 点、`self` 非 0，一度被误判成「电磁炮雷击打到自己」（见 `scripts/test/TESTING-SPEC.md` §8.2-1 与 §10-18）。

**禁止事项**：

- **禁止**在用例的两次读数**之间**清场（会改变 `bolt_delta` 基线，前后读数不可比）；清场只能发生在**用例自身 setup 之前**。
- 因此**不要**把清场写成用例内部的固定步骤：`/kill @e[type=!player]` 会连**掉落物 / 展示框 / 盔甲架**一起删。需要保留实体的用例改用**距离限定**或**难度切换**。
- 1.21.1 的命令在 **tick 末**才生效（1.20.1 立即生效）：清场后必须 `wait ≥500ms` 再摆靶 / 读基线，且**不要**在同一 tick 里「清场 + 摆靶」。
- 用难度切换法时**必须切回原难度**（`easy`），否则会改变敌对生物伤害与刷新行为，污染后续用例。

### 测试后收尾（Post-Test Teardown：关闭测试端 + 清理旧存档）— 必须遵守

> **2026-09-17 用户规则**：测试任务完成后**关闭测试端**并**清理旧存档数据**，**避免游戏进程长时间驻留**。

**标准收尾命令（分步执行路线的必做动作）**：

```bash
pwsh -NoProfile -File scripts/test/mt.ps1 --version <版本> --phase stop --purge-saves
```

等价直调：`mt_cleanup.ps1 run --version <版本> --purge-saves`（或 `mt_stop.ps1 --version <版本> --purge-saves`）。

- **全流程**（`mt.ps1 --version <版本>`，不带 `--phase`）**已自动收尾**：退出清理走 `mt_cleanup.ps1 run --quiet --purge-saves`，即「收停本流程进程 + 清掉测试存档」一步到位，无需手工补命令。
- **分步执行**（`--phase launch` / `--phase cases` …）：单阶段默认**不**清理（客户端要跨阶段存活），故**最后一次读数之后必须显式跑上面的收尾命令**——这是硬性纪律，不是建议。
- **清理范围**（只动 `run/<版本>/` 内的世界目录，绝不碰 `run` 之外、也绝不碰种子包 `resources/testworld-seed-<版本>.zip`）：
  1. `run/<版本>/saves/<世界名>`（客户端读取位置，`Mt.Paths.client_world`）；
  2. `run/<版本>/<世界名>`（`runServer` 生成世界的中间位置，`Mt.Paths.server_world`）；
  3. `run/<版本>/saves/` 下**其它**含 `level.dat` 的历史遗留世界目录（逐个回显后删除）。
  删除结果以机器可读行 `MT_CLEANUP_SAVES: PURGED <N>` / `MT_CLEANUP_SAVES: KEPT（未指定 --purge-saves）` 回显，便于报告核对。
- **为什么删得起**：与阶段 E「**每次开新的自动化测试都必须重建世界**，不接受复用上一轮存档」同源——旧存档本来就必须重建，留着只会掩盖「忘了 `mt_env world`」，还白占磁盘。
- ⚠️ **两阶段/重登类用例（`CHIP-RELOG-*`）中途的 `stop` 禁止加 `--purge-saves`**：那类流程是 `saveall` → `stop`（**保留存档**）→ `launch` 读回，删了存档就没得读。故 `--phase stop` **默认不清理**，只有显式 `--purge-saves` 才清（`--keep-saves` 可显式写回默认）。
- **失败取证优先**：`.mt_keep_alive` 标记存在时（条目 `on_fail=keep_game_running`），进程收停与存档清理**都只提示、不执行**，现场留给取证；取证完用 `--phase stop --force --purge-saves` 释放。
- **不要在测试任务之间留着客户端**：进入世界后若长时间不再读数，游戏进程会一直驻留（占内存/显卡，且下一次 `launch` 的前置检查可能因此中止）——**任务结束即收尾**；这与「前置检查会自动收停遗留进程」是两道互补的保险，不能互相替代。
- **收停是异步的**：`TerminateProcess` 返回 ≠ 进程已从进程表消失，故 `mt_cleanup` 在判残留前会**最多等 20 秒**再复核（否则慢退出的客户端会被误报成 `RESIDUAL`/退出码 1）。
- **收停范围覆盖「专用服务端 / 数据生成 / run 任务包装器」（2026-09-17 实测缺口）**：旧判据只认**客户端入口** ⇒ `mt_env world`（或两段式数据生成）起的 `runServer` 包装器与服务端 JVM **永远杀不掉**；实测跑完 env 后两者双双存活，且孙进程仍持有父进程 stdout 句柄，把 `mt.ps1 --phase env` **卡死**（子进程早已退出、父进程一直等）。现由 `Mt.Proc.psm1` 的 `Test-MtPipelineProcess` 统一判定（① 客户端沿用最严格判定；② 含 `gradle-wrapper.jar` 且含 `:<子项目>:run` 的包装器；③ 入口 `net.neoforged.devlaunch.Main` 的本版本 JVM），`mt_cleanup` 的「收停后自检」也改用同一判据（否则既杀不掉也检不出）。⚠️ **只认 `run` 家族选择器**——`:neoforge-1.21.1:build` / `:neoforge-26.1.2:build` 之类的构建任务不属于测试流程，**绝不能杀**（构建中途被收停会毁产物）。

### 工具链（`scripts/test/`；脚本本身入库，仅 `mt.conf`、`reports/*`、`cases/.mt_*` 为本地忽略的运行时产物）

#### 脚本职责

| 脚本 | 语言 | 职责 | 不做什么 |
|---|---|---|---|
| `mt.ps1` | pwsh | **唯一入口**：阶段编排、版本顺序与门控、结果汇总 | 不含任何断言逻辑 |
| `lib/Mt.Phase.psm1` | pwsh | 阶段标记（`MT_*` 单行输出）、计时、退出码常量 | 无副作用 |
| `lib/Mt.Paths.psm1` | pwsh | 唯一路径/版本来源；**截图世代清单**读写；**进程判据**单点；含 `mt.conf` 解析（`Mt.Conf.psm1`） | 无副作用 |
| `lib/Mt.Proc.psm1` | pwsh | 外部命令执行助手：原始字节取回 + UTF-8 容错解码；后台进程/日志重定向；按版本精确收停 | 不含流程逻辑 |
| `lib/Mt.Win32.psm1` | pwsh | Win32 API 封装(窗口/前台/输入) | 不含流程逻辑 |
| `mt_preflight.ps1` | pwsh | 前置检查（分支/输入法可用性/遗留进程/注入通道 MCP 二进制/模组来源口径/Gradle 包装器/兼容栈/可写性/**前置库配对**） | 不启动任何游戏进程 |
| `mt_build.ps1` | pwsh | Gradle 构建守护：超时、`BUILD SUCCESSFUL` 识别、产物 jar 校验、重试 | 不做部署决策 |
| `mt_env.ps1` | pwsh | `mods` 装兼容模组 / `world` 重建世界（含原生 NBT 改写）/ `kill` 按版本精确停进程 | 不做功能断言 |
| `mt_launch.ps1` | pwsh | 启动 `runClient`、轮询就绪日志、兼容栈信号、**测试前清场**（`/kill @e[type=!player,distance=..128]` ×2，`--no-preclean` 跳过） | 不定义测试条目 |
| `mt_inject.ps1` | pwsh | 输入注入（ctypes/PostMessage）；**注入前自动切目标窗口为 en-US**；非 Windows 降级 MCP | 不做断言 |
| `mt_ime.ps1` | pwsh | 输入法管理：列出布局、校验 en-US 可用、**按窗口线程切换输入法**（`set_window_us` / `selftest`） | 不改系统全局默认输入法 |
| `mt_cleanup.ps1` | pwsh | **退出清理唯一实现**：收停本流程进程 + `gradlew --stop`；尊重失败取证标记 | 不杀非本流程进程 |
| `mt_stop.ps1` | pwsh | 阶段 D 显式收停入口(与 mt_cleanup 共用实现) | 不杀非本流程进程 |
| `mt_capture.ps1` | pwsh | 截图采集 + 世代登记；`list` / `prune` | 不判定像素内容 |
| `mt_assert.ps1` | pwsh | 断言引擎：增量日志标记、反向断言、崩溃、KubeJS、Mixin | 不发起动作、不做视觉判定 |
| `mt_case.ps1` | pwsh | 条目执行器（封闭原语）+ 条目 schema 校验 | 不生成条目、不做视觉判定 |
| `mt_gen_case.ps1` | pwsh | 条目生成器（**子技能**）：三态输入 → 条目文件 | 不执行、不启动游戏 |
| `mt_report.ps1` | pwsh | 证据收集、单版本报告、版本结论汇总 | 不替代人工判断 |
| `mt_scope.ps1` | pwsh | **变更分级自查**（非核心 / 核心）：按「变更分级与验证口径」白名单给级别并回显必需验证 / 跳过项；`--staged` / `--paths <…>` / 缺省工作区 | **只读、只建议**（退出码恒 0，不拦停、不代替判定） |
| `mt.conf` | — | 机器本地路径（整合包 mods 源、注入通道 MCP 二进制）；解释器项（`MT_PYTHON`/`MT_NODE`）已随迁移作废 | 不含流程逻辑 |
| `ref-repro.ps1` | pwsh | 松散引用复现用脚本(不入流程) | 不含流程逻辑 |
| `TESTING-SPEC.md` | — | 测试条目 schema 规范 | 不含流程逻辑 |

#### 调用顺序

```
mt.ps1
 ├─ mt_preflight.ps1 --all ................ 前置检查（失败即中止，退出码 10）
 └─ for 版本 in 1.21.1 → 1.20.1:            （1.20.1 受门控）
     ├─ mt_build.ps1      --version <v> .... 构建 + 产物校验
     ├─ mt_env.ps1        mods   --version <v>
     ├─ mt_env.ps1        world  --version <v> [--seed]
     ├─ mt_launch.ps1     --version <v> .... 进入世界 + /publish
     ├─ mt_assert.ps1     snapshot --version <v> --window launch|case|whole  ← 写判定窗口起点
     ├─ mt_case.ps1       run-dir  --version <v>（或 run --case <文件>）
     │    └─ 逐条目调用 mt_inject.ps1 / mt_capture.ps1 / mt_assert.ps1
     ├─ mt_report.ps1     collect --version <v> --verdict <PASS|FAIL>
     └─ mt_stop.ps1       --version <v>
 ├─ mt_report.ps1 summary ................... 两版本结论 + 综合结论
 └─ mt_cleanup.ps1 run ................... 退出清理（trap 兜底，Ctrl-C 也覆盖）
```

> `mt_stop.ps1` 与 `mt_cleanup.ps1 run` **共用同一实现**：前者是显式入口（阶段 D），后者是退出钩子；不存在两套收停逻辑。

#### 两条跨语言约定（改脚本前必读）

这两个坑都是实测踩到过的，症状是「看起来环境坏了」，实际是调用方式问题。改动工具链时务必保持这两条约定：

**1) 路径 — 一律用原生 Windows 路径，不依赖任何 shell 的 cwd**

工具链已全部是 pwsh（Windows-only），**不再经过 Git Bash/MSYS**，因此历史上那条
「POSIX 路径被 MSYS 转写成 `F:\f\...`」的坑不会再出现（该坑对应的是已删除的 bash 层）。
现行约定：

- 路径的唯一来源是 `lib/Mt.Paths.psm1` 的 `Get-MtPaths`（原生 Windows 形式）；
  内部一律 `Join-Path` / `[System.IO.Path]::GetDirectoryName`，
  **不要**用 `Split-Path -LiteralPath … -Parent`（pwsh 7 里是参数集冲突，直接抛异常）；
- 条目路径参数（`--case` / `--dir`）由 `mt_case.ps1` 内部解析：相对路径按 `scripts/test/` 解释，
  也接受裸名（`--case SMOKE-TOOLCHAIN`），因此**从任意 cwd 调用都成立**；
- 子进程若需要特定工作目录（gradle 一律要在仓库根跑），显式传
  `-WorkingDirectory (Get-MtRoot)`，不要靠调用方碰巧在根目录。

**2) 输出与进程 — 三条硬约定**

- **编码**：每个入口脚本的**第一行逻辑**必须是 `Initialize-MtConsole`（把控制台 I/O 固定为
  UTF-8 无 BOM）；否则本机（cp936）下 `[Console]::Out` 会把中文写成 GBK。
- **换行**：面向观众的文本一律走 `Write-MtLine` / `Write-MtErrLine`（**显式 LF**），
  **禁止** `Write-Output` / `Write-Host` / `echo`，也**禁止** `[Environment]::NewLine`（那是 CRLF）。
  python 侧的 CRLF 是解释器副产物，比对器会折行归一 —— 不要把 CRLF 当成"契约"对齐。
- **取回子进程输出**：统一经 `lib/Mt.Proc.psm1` 的 `Invoke-MtProcess*`（以**原始字节**取回再按
  UTF-8 容错解码）。判定标记（子项目名、run 目录、PID）都是 ASCII，替换字符不影响匹配。
  读正在被写入的日志用 `Read-MtSharedText`（内部 `FileShare::ReadWrite`）。
  后台跑 gradle 用 `Start-MtProcessToFile`：它把**真实文件句柄**给子进程（不是管道），
  且 `-MergeStderr` 复刻 `> log 2>&1` —— Gradle 把 `BUILD FAILED` 写在 **stderr**，
  拆成两个文件会让「编译失败」退化成「本轮未见构建结果」（已实测踩到）。

另有三条 pwsh 语言级坑（都在共享模块注释里写明，改工具链前务必看一眼）：

- 共享模块（`lib/Mt.*.psm1`）**不得用 `Import-Module -Force`**：它会卸载重载被引模块，
  从而摘掉调用方脚本作用域里已导入的函数（Import-Module 自身不报错，后续调用才炸）；
- `return $collection` 会被 PowerShell **展开成流**（单元素退化成标量）→ 整体返回集合要写 `return , $x`；
- `@{ }` / `[ordered]@{ }` 键名**大小写不敏感**（NBT 里 `Version` 与 `version` 是两个键，
  用它会静默吞掉一个）→ 需要区分大小写时用 Ordinal 的 `OrderedDictionary`。

#### 退出清理（避免进程泄漏）

自动化测试最容易留下的后患是**残留进程**：dev 客户端没关、Gradle 守护一直挂着，下一次运行就撞上前置检查的「遗留进程」而中止。清理因此做成**两道**，互为补充：

**第一道 — 退出钩子**（正常结束 / 失败 / 中断）：

- **实现单点**：`mt_cleanup.ps1`。`mt.ps1` 的 `trap … EXIT/INT/TERM`、`mt.ps1 --phase stop`、`mt_stop.ps1` 三个入口都调它，不复制逻辑；
- **收停范围**（只碰本流程自己的东西）：
  - 本流程的 dev 客户端 / Gradle 任务进程 —— 判据为 `Paths.process_markers`（gradle 任务选择器 + run 目录），**不会**误杀 IDE 语言服务器或用户自己的客户端；
  - Gradle 守护进程 —— 用 `gradlew --stop` 优雅停止，**不用** `taskkill /IM gradle.exe`（那会连用户其它项目的 Gradle 一起杀）；
- **默认开关**：全流程**默认开**（`--no-cleanup-on-exit` 可关）；单阶段**默认关**，因为 `launch → cases` 是分步执行的，需要客户端在两步之间保持存活（`--cleanup-on-exit` 可强制开）；
- **不破坏失败取证**：条目 `on_fail=keep_game_running` 失败时会落 `cases/.mt_keep_alive` 标记（记录失败原因与时间）。标记存在时清理**只提示不杀进程**，否则「自动清理」会在失败瞬间销毁现场。取证完成后用 `--phase stop --force`（`--force` 才会清除标记）；
- **收停后自检**：清理结束会再扫一遍，仍有本流程进程则以非 0 退出并列出 PID —— 清理不撒谎。

**第二道 — 启动前兜底**（阶段 P 内，实测必需）：

> ⚠️ **`trap` 在 MSYS/Git Bash 下并不可靠**，这不是推测而是实测结论：投 `SIGTERM` 会让 bash **直接终止、不执行钩子**（`timeout -s TERM` 实测：日志里没有 cleanup 段、进程仍在）；Windows 上还有任务管理器强杀、直接关控制台窗口等根本不经过 `trap` 的路径。
> **只靠第一道就等于把「进程泄漏」交给运气。** 故必须有第二道：

阶段 P 的「遗留进程」检查改为**先自动清理、清不掉才阻塞**：

- 检出**本流程**遗留进程 → 直接调 `mt_cleanup.run_cleanup()` 收停，收停干净则该项记 `OK` 并注明「已自动收停 N 个（退出钩子失效时的兜底）」——**不影响本次结论**；
- 收停后仍有残留 → `FAIL`（权限/顽固进程），提示手工 `--phase stop`；
- `.mt_keep_alive` 取证标记存在 → **不自动清理**，`FAIL` 并提示用 `--force` 显式释放（保护失败现场）；
- 用户自己的整合包客户端 → 仍只是提示，不阻塞（判据同前）。

这样泄漏最多只影响**一次**运行的干净度，不会跨运行累积。

```bash
pwsh -NoProfile -File scripts/test/mt.ps1 --phase stop                        # 显式收停（尊重 keep_alive）
pwsh -NoProfile -File scripts/test/mt.ps1 --phase stop --force                # 强制收停并清除取证标记
pwsh -NoProfile -File scripts/test/mt.ps1 --phase stop --keep-daemon          # 保 Gradle 热态，只收停客户端
pwsh -NoProfile -File scripts/test/mt.ps1 --version 1.21.1 --phase stop       # 只收停某版本
pwsh -NoProfile -File scripts/test/mt_cleanup.ps1 status                    # 只看残留（0=干净 / 1=有残留）
pwsh -NoProfile -File scripts/test/mt_cleanup.ps1 run --keep-daemon         # 同上，直接调实现
```

---

### 阶段 P — 前置检查

**前置条件**：无（本阶段自行校验环境）。

**执行命令**：
```bash
pwsh -NoProfile -File scripts/test/mt.ps1                              # 全流程时自动执行
pwsh -NoProfile -File scripts/test/mt_preflight.ps1 --all            # 单独执行
```

**预期结果**：逐项打印 `[OK  ]`，末行 `MT_PREFLIGHT: OK — N 项全部满足`。检查项 = **6 个全局项**（当前分支、输入法（**en-US 布局是否可用**，非「当前是否已是 en-US」）、遗留进程、注入通道 MCP 二进制、模组来源统一口径、`gradlew` 是否存在）+ **每个版本 3 项**（兼容栈、run 目录可写性、**前置库配对**）⇒ 单版本 **9 项**、`--all`（三条线）**15 项**。

**「前置库配对」一项（2026-09-19 新增，与 `run/Start-<版本>.bat` 同口径，必须遵守）**：`run/<版本>/mods` 内的 `starengine_lib-*.jar` 必须**恰 1 份**且文件名以 `-<该线 gradle.properties 的 starengine_lib_version>.jar` 结尾；**缺 jar 放行**（dev 运行的库由 Gradle `implementation` 放进 runtimeClasspath，`run/mods` 那份只为人手启动路径自足），**版本不一致一律 FAIL**（exit 10）并回显修复命令。原因：本批实测出现过「库版本由 `.10` 升到 `.11`、`run/mods` 仍是 `.10`」的漂移 —— 三个 `Start-*.bat` 因此拒绝启动，而工具链零感知、照常跑测试（实测 FML 的 `UniqueModListBuilder` 按版本取**最新**，故旧 jar 未污染读数，但环境已不一致）。**库版本一升就必须重新成对部署**：`pwsh -NoProfile -File scripts/devtools/Start-SelfTest.ps1 -Version <版本>`（该脚本的时间戳闸门已支持 Gradle 的合法 `…:jar UP-TO-DATE`，见 `scripts/test/TESTING-SPEC.md` 附录 A「2026-09-19（续）」条）。

**「遗留进程」一项的判定边界（区分两类，不能混为一谈）**：

| 情形 | 判定 | 说明 |
|---|---|---|
| 本流程自己的进程遗留（dev 客户端 / Gradle 任务），**无取证标记** | **OK（自动收停后继续）** | 命令行匹配 gradle 任务选择器（`:neoforge-1.21.1:`）或 run 目录；检出即自动 `mt_cleanup.run_cleanup()` 收停，收停干净则不阻塞本次运行，仅在结论里注明「已自动收停 N 个」。这是**退出钩子失效时的兜底**（见「退出清理」） |
| 同上，**但 `.mt_keep_alive` 取证标记在场** | **FAIL** | 现场是失败时故意留的，不自动销毁；须 `pwsh -NoProfile -File scripts/test/mt.ps1 --phase stop --force` 显式释放 |
| 自动收停后仍有本流程进程 | **FAIL** | 权限或顽固进程；手工 `--phase stop` 后检查 |
| `gradle.exe` 包装器进程 | **FAIL** | 归属不确定（可能是用户其它项目/IDE 的构建），不自动强杀，提示 `--phase stop` |
| 其它 Minecraft 客户端（用户自己开着的整合包） | **OK（仅提示）** | 不阻塞：注入通道已按版本锁定窗口（见阶段 C），不会投错；只提示显存占用 |

> 三条判据都是踩过坑后收紧的，别改回去：
> ① 判据**不用裸子项目名**（`neoforge-1.21.1` 同时是仓库目录名，会把 IDE 语言服务器等只是引用了该目录的进程一起命中，`mt_stop` 就会误杀）；
> ② 判据**不把用户自己的客户端当遗留** —— 那会在用户只是开着自己的整合包时误报阻塞，让人白折腾；
> ③ **本流程遗留先自动收停而不是直接 FAIL** —— 因为退出钩子在 MSYS 下不可靠，「清不掉就阻塞」会让旧泄漏反复堵死新运行。
> 具体标记由 `lib/Mt.Paths.psm1` 的 `Paths.process_markers` 单点给出，`mt_env` 与 `mt_preflight` 共用，避免两侧判据漂移。

**失败处理**：输出 `MT_PREFLIGHT: FAIL — …` 并以退出码 `10` 中止，**不触达游戏**。前置失败不计入功能缺陷。逐项修法：

| 失败项 | 处理 |
|---|---|
| 分支（仅告警、不拦停） | 无需处理：任何分支都可运行本流程；非发布线分支只在阶段 P 回显 WARN，报告按实际分支名标注（2026-09-17 用户裁决「移除二重验证白名单，允许该分支执行」——已不再是失败项） |
| en-US 布局不可用 | 在 Windows「语言和区域」中安装英语(美国)键盘（**只需可用**，不必设为当前输入法——注入前会自动切目标窗口） |
| 本流程遗留 + 取证标记在场 | 取证完成后 `pwsh -NoProfile -File scripts/test/mt.ps1 --phase stop --force`（会一并清除标记） |
| 自动收停后仍残留 / `gradle.exe` 在跑 | `pwsh -NoProfile -File scripts/test/mt.ps1 --phase stop`，仍不行则检查权限或用任务管理器结束 |
| 整合包缺少兼容模组 | 检查 `D:\.minecraft\versions\...` 对应 mods 目录 |
| run 目录不可写 | 检查磁盘/权限 |

### 阶段 B — 构建

**前置条件**：阶段 P 通过。

**执行命令**：
```bash
pwsh -NoProfile -File scripts/test/mt_build.ps1 --version 1.21.1 [--timeout 60] [--retries 3]
```

**预期结果**：`MT_BUILD: OK`，且 `neoforge-1.21.1/build/libs`（或 `forge-1.20.1/build/libs`）下产物 jar 修改时间晚于本次构建开始。

**失败处理**：超时先看输出是否已含 `BUILD SUCCESSFUL`（构建完成但进程未退出，按产物时间戳判定为成功）；否则强杀进程树、按产物时间戳判定是否实际完成并重试，最多 3 次。`BUILD FAILED` 时打印末 15 行日志并以退出码 `2` 中止。**失败则回滚版本号、不提交**（依「新版本发布流程」）。

### 阶段 E — 环境装配

**前置条件**：阶段 B 通过。

**执行命令**：
```bash
pwsh -NoProfile -File scripts/test/mt_env.ps1 mods  --version 1.21.1
pwsh -NoProfile -File scripts/test/mt_env.ps1 world --version 1.20.1 [--seed]
```

**预期结果**：`MT_MODS: OK` 与 `MT_WORLD: OK — 世界重建并设 AllowCommands=1, keepInventory=true`（两条世界规则均须在 `MT_WORLD` 行内回显，缺一即视为阶段 E 未通过）。

**行为要点（必须遵守）**：
- **1.21.1**：从整合包复制 **Sodium `sodium-neoforge-0.8.13+mc1.21.1.jar` + Iris `iris-neoforge-1.8.14-beta.1+mc1.21.1.jar` + ModernFix `modernfix-neoforge-5.27.24+mc1.21.1.jar`** 到 `run/1.21.1/mods/`（幂等，按修改时间判断是否需要复制）。ModernFix 提供启动加载时间日志，供阶段 L 判定加载完成。
- **1.20.1**：dev run **不安装渲染模组**（Embeddium/Oculus 的 mixin refmap 在 MDG mojmap 命名下无法解析，会导致 Mixin apply failed）；本阶段只校验用户生产环境渲染栈就绪。因此 1.20.1 的渲染兼容性属**生产环境人工验证项**，不在 dev 自动化范围内。
- **26.1.2（2026-09-17 起）**：dev run 安装**渲染栈 + 光影**用于光影兼容性测试 —— Sodium `mc26.1.2-0.9.2-neoforge` + Iris `1.11.4+26.1-neoforge` + 光影包 Complementary Shaders - Unbound `r5.9.3`（置于 `run/26.1.2/shaderpacks/`）。三者**一律经 Modrinth Maven**（`https://api.modrinth.com/maven/maven/modrinth/<slug>/<version>/<file>`）下载并做**体积 + SHA1 双校验**（缓存 `temp/probe_mods/26.1.2/`），**禁止**改用 GitHub Release/其它 CDN 直链（2026-09-17 用户裁决）。
  ⚠️ **光影默认关闭**（`run/26.1.2/config/iris.properties` 写 `enableShaders=false`），但**这不是因为「光影不能用」** —— 2026-09-17 用户怀疑并实测确认：元凶是 **Sodium 0.9.2**；把它降到**整合包同款的 0.9.1**（`sodium-neoforge-0.9.1+mc26.1.2.jar`）后，Iris 1.11.4 + Complementary Unbound r5.9.3 **可以正常开启光影**（`SHADER-VISION-26.1.2` 用例由 FAIL 转 **PASS**；视觉读数：原版方块云 → 光影体积云/大气散射/色调映射，游戏画面正常存活）。**此前记录的「26.1.2 上启用任意光影包都会崩 `IllegalStateException: Missing sampler Sampler1`（`GlCommandEncoder.trySetup`）、属上游无解缺陷」的结论作废**（当时的对照只换了光影包、没换 Sodium 版本，属误判）。默认仍关光影只是「测试不需要 + 省性能」：`SHADERS=disabled` 是正常状态、不报 WARN；`mt_env mods` 每次会把该属性重写回 `false`。`mt_launch` 回显 `SODIUM_LOADED` / `IRIS_LOADED` / `SHADERS=… pack=…` / `SHADERPACK_LOADED=…`，`SHADER-VISION-26.1.2` 是这条经验的**回归守卫**（换 Sodium/Iris 版本后必须复跑）。
- **世界重建**：删除旧存档 → 写 `server.properties`（超平坦/创造/`allow-cheats`/`max-players=2`）→ 取消失焦暂停 → `runServer` 生成（180 秒上限）→ 停服 → 世界迁移到 `run/<版本>/saves/testworld` → **原生 NBT 改写 `level.dat`**。
- **测试世界规则（必须，两条路径都要满足）**：
  1. `Data.allowCommands=1`（TAG_Byte）—— 单人存档的「允许命令」由该字段决定，服务器生成的世界默认不写；
  2. **`Data.GameRules.keepInventory="true"`（TAG_String）** —— 用例会主动击杀/被击杀（僵尸靶子、雷击、骰战反伤），死亡掉物会污染后续用例的背包基线与世界实体，故**任何新建或恢复的测试世界都必须开启 keepInventory**（原 `GameRules` 复合标签缺失时按 Ordinal 比较器新建；NBT 键大小写敏感，禁止用 `[ordered]@{}`）。
3. **`mobGriefing=false`（2026-09-14 新增，必须）** —— 测试世界里**实体爆炸会真的挖坑**：当晚自然刷新的苦力怕（或电磁炮雷击充能出的闪电苦力怕）在探针靶场附近自爆，`run/1.21.1` 测试世界留下一处爆炸坑（`mobGriefing=true` 时的实测）。探针自己生成的靶子虽已 `setNoAi(true)`（可用 `/data get entity <sel> NoAI` 复核），但**自然刷新的生物不受探针控制**，故任何测试世界一律先把 `mobGriefing` 关掉再跑用例。4. **`doFireTick`（建议关闭）** —— 电磁炮的雷击沿用原版闪电行为会**点燃地面**，`doFireTick=true` 时火会蔓延烧毁植被（这是原版行为，不是本模组的破坏方块；模组源码内**没有任何** `Level#explode`/`destroyBlock`/`setBlock` 调用）。⚠️ **2026-09-19 复核（只登记、未改）**：上面第 3 款（`mobGriefing=false`）**当前没有任何代码执行方** —— 全仓扫描只有注释/文档命中，`mt_env` 只写 `keepInventory` 一个键；且 1.20.1 的**种子基线** `scripts/test/resources/testworld-seed-1.20.1.zip` 内的 `level.dat` 实测 `mobGriefing=true`（当轮走种子快恢复路径，恢复后的世界实况同为 true；见 `temp/t74/world-gamerules.ps1`）。紧随本段的 `MT_WORLD: BLOCKED` 硬闸门**只覆盖第 1、2 条**（`allowCommands` / `keepInventory`），第 3 款不在其中。该款原本防的风险（会主动接近并自爆的苦力怕）现已由 2026-09-18 的 **noai 硬闸门**（`mt_launch` 要求 128 格内 `mobs == noai`）从上游覆盖，火势由各用例自带的取证前清理关掉（1.20.1/1.21.1 用 `doFireTick false`、26.1.2 用 `fire_spread_radius_around_player 0`）。**「补执行方」还是「改措辞」待用户裁决**，取证与两个候选方案见 `scripts/test/TESTING-SPEC.md` 续 21。
  两条规则任一未能写入时，`mt_env world` 报 `MT_WORLD: BLOCKED` 并以退出码 `11` 结束，**不得**带着缺规则的存档继续跑用例。
- **1.20.1 优先走种子快恢复**：`scripts/test/resources/testworld-seed-1.20.1.zip` 存在时直接解压恢复（恢复后同样强制写入上面两条规则），规避 dev `runServer` 的渲染模组崩溃问题。
- 生成世界期间**临时移出纯客户端模组**（sodium/iris/embeddium/oculus/IMBlocker），完成后恢复。
- **强制规则**：每次开新的自动化测试都必须重建世界，不接受复用上一轮存档。

**失败处理**：`MT_WORLD: BLOCKED`（世界未在时限内生成）→ 检查 `run/<版本>/logs/latest.log` 与 `crash-reports`；退出码 `11`。`MT_MODS: BLOCKED`（源目录缺失）→ 修正 `mt.conf` 中的 `MT_PACK_MODS_*` 或整合包内容。

### 阶段 L — 启动与就绪

**前置条件**：阶段 E 通过（`run/<版本>/saves/testworld/level.dat` 存在）。

**执行命令**：
```bash
pwsh -NoProfile -File scripts/test/mt_launch.ps1 --version 1.21.1 [--no-publish] [--no-preclean]
```

**预期结果**：末行 `MT_LAUNCH: OK — 已进入世界（quickplay=testworld）`。

**就绪判据（必须遵守）**：清空 `latest.log` 与 `crash-reports` 后，基础等待 **30 秒**，随后轮询 `run/<版本>/logs/latest.log` 中 ModernFix 的加载完成日志 **`Total time to load game and open world was`**；未出现则**每 15 秒复检**，180 秒上限。出现崩溃报告或进程退出即判失败。

**进入世界后同阶段完成**：
1. 输出兼容栈信号（1.21.1：`SODIUM_LOADED` / `IRIS_LOADED`；26.1.2：`KUBEJS_LOADED` / `RHINO_LOADED` / `SODIUM_LOADED` / `IRIS_LOADED` / `SHADERS=… pack=…` / `SHADERPACK_LOADED=…`；1.20.1：`EMBEDDIUM_LOADED` / `OCULUS_LOADED`，dev run 预期为 false）；
2. **窗口搬移规则已全局移除（2026-09-17 用户裁决：「全局移除移动游戏窗口到第二屏幕的规则」）**：此前本步骤会把测试客户端搬到**显示器 #1（第二屏）**并把客户区固定成 **1920x1080**（`MT_WINDOW:` 行；由 `--monitor` / `--size` 两个参数控制），现**不再**做任何搬移或改尺寸 —— 客户端窗口的位置与大小完全由系统与游戏自身决定。随之删除：`mt_launch.ps1` 的这两个参数、搬移代码块与对 `lib/Mt.Win32.psm1` 的导入；`lib/Mt.Win32.psm1` 的 `Get-MtMonitors` / `Move-MtWindowToMonitor` 两个函数及其导出项（它们只服务该规则，仓库内已无调用方）。日志里不再有 `MT_WINDOW:` 读数。
   需要窗口矩形的工具不受影响：`mt_capture` 的 `crop` 与 `mt_inject` 的坐标映射继续用保留的 `Find-MtMinecraftWindow` / `Get-MtWindowRect` / `Get-MtClientRect`。**禁止把该规则（含「默认搬到第二屏」的隐式默认）加回来。**

### 全局测试规则（测试环境硬性要求，2026-09-17 用户裁决）— 必须遵守

> 本节四条规则对**三条线**（1.21.1 / 1.20.1 / 26.1.2）同时生效，由 `scripts/test` 工具链强制执行；
> 任一条不满足时该轮测试**无效**（`MT_MODS: BLOCKED` / `MT_LAUNCH: ERROR`），**禁止**静默降级或手工绕过。

1. **测试环境必须装齐「反干扰 + 优化 + 探针宿主」三类模组**（`pwsh -File scripts/test/mt_env.ps1 mods --version <V>` 幂等落位）：
   - **史莱姆压制（服务端/运行期，硬闸门）**：`superflat-world-no-slimes` + 其 required 前置 `collective`。超平坦测试世界 y<40 处处是史莱姆区块（实测同一位置 128 格内 103 只史莱姆）⇒ 会污染实体类读数。**1.20.1 例外**：它由 `forge-1.20.1/build.gradle` 的 `modImplementation` 提供，**不得**再放进 `run/1.20.1/mods`（会被 FML 判重复模组）。launch 侧判据 = 已加载模组清单行里的 modId（**版本相关**，统一走 `mt_launch.ps1` 的 `Test-MtModLoaded`）：`1.21.1`/`26.1.2`（NeoForge）取 latest.log 的 `显示名 版本 (modId)` 即 `(superflatworldnoslimes)` / `(collective)`；**`1.20.1`（Forge）没有括号清单行**，改取 `logs/debug.log` 的 `Found valid mod file … with {modId} mods - versions {…}` 即 `{superflatworldnoslimes}` / `{collective}`（2026-09-17 实测踩坑：1.20.1 沿用括号判据 ⇒ 把**已装载**的模组判成缺失、`MT_LAUNCH: ERROR`）。缺则 `MT_LAUNCH: ERROR`（确需对照实验时设 `MT_ALLOW_NO_SLIMEGUARD=1`）。
   - **优化类（兼容性验证对象）**：`FerriteCore`（26.1.2 另有 `ModernFix`）三条线全装，坐标一律 Modrinth Maven 且版本 id 钉死。`mt_launch` 回显 `FERRITECORE_LOADED` / `MODERNFIX_LOADED` / `IMMEDIATELYFAST_LOADED`（缺装载只 WARN，但要看得见）。**`ImmediatelyFast` 在 1.20.1 上装不了 —— 已实测定位、属上游硬冲突，不是配置问题**：IF 的 1.20.1 构建只有 1.2.0~1.2.4，其 `IrisCompat.init()` 第 0 条指令就 `Class.forName("net.coderbot.iris.vertices.ImmediateState")`（**Iris 1.6 包名**），而 1.20.1 的光影加载器 Oculus 1.8.0 基于 Iris 1.7+（`net.irisshaders.iris`）⇒ 探测到 oculus 就无条件调用、**无配置项可跳过**，模组构造期 `ClassNotFoundException` 直接终止客户端（`runClient` exit -1）。⇒ 与「光影包必须默认启用」二选一，当前取舍 = **保光影**，1.20.1 线回显 `IMMEDIATELYFAST_LOADED=false` WARN 并记明原因（改用 Oculus 1.7.0 可两边都满足，但须先经用户裁决并复跑 SHADER-VISION）。
   - **探针宿主（1.21.1）**：`KubeJS` + `Rhino` + `Architectury API`（由 `mt_env` 从整合包复制；1.20.1 的 KubeJS 走 build.gradle，26.1.2 的走 `Install-MtProbeRuntime` 下载）。**缺 KubeJS ⇒ `MT_ASSERT_KUBEJS: BLOCKED`、`/astralprobe` 不存在、`MT_preflight-op` ERROR** —— 这是「探针脚本已改但读数全无」的根因形态。
   - **查看类（2026-09-19 用户要求「所有测试环境增加 jei 和 jade 模组,用于用户侧物品和效果检查」）**：`JEI`（物品/配方/用途查看）+ `Jade`（方块与实体信息 HUD）三条线全装，**均为测试环境依赖、不进发货产物**，一律走各线 `build.gradle`（与 JEI 既有做法一致，**不**放进 `run/<版本>/mods`）：`neoforge-1.21.1` / `neoforge-26.1.2` 用 `implementation`，**`forge-1.20.1` 必须用 `modImplementation`**（MDG LegacyForge 需在解析期重映射，直接塞原版坐标系 jar 会崩）。坐标与版本**必须与各线整合包内现有 jar 同版**（避免 dev run 与整合包行为不一致）：1.21.1 `curse.maven:jei-238222:8512040`(19.39.0.372) + `maven.modrinth:jade:eYz2YBGT`(15.10.6+neoforge)；1.20.1 `curse.maven:jei-238222:8778011`(15.56.0.205) + `maven.modrinth:jade:xJQHCmWJ`(11.13.3+forge)；26.1.2 `curse.maven:jei-238222:8886511`(29.37.0.99) + `maven.modrinth:jade:5jKeKXwB`(26.1.11+neoforge，game_versions 含 26.1.2)。**Jade 的 `client_side`/`server_side` 均为 `optional`** ⇒ 不属「纯客户端模组」，**不需要**进 `Invoke-MtEnvWorld` 的移出名单（与 ImmediatelyFast 相反）；两者都**不是**硬前置，**禁止**写进 `mods.toml`/`neoforge.mods.toml` 的依赖段，也**不得**纳入加载器门槛用例。
2. **光影包必须就位且默认启用**（2026-09-17 用户要求「游戏环境缺少光影包，添加光影包并设置默认启用」）：`mt_env.ps1 mods` 把 `ComplementaryUnbound_r5.9.3.zip` 落位 `run/<V>/shaderpacks/`，并把光影加载器的配置写成 `shaderPack=ComplementaryUnbound_r5.9.3.zip` + **`enableShaders=true`**（**路径按版本取**，见 `Get-MtIrisConfigFile`：Iris 线 = `config/iris.properties`，**1.20.1 的 Oculus = `config/oculus.properties`** —— 2026-09-17 实测该线根本没有 `iris.properties`）。**三条线都生效**（2026-09-17 复验：1.20.1 的渲染栈 Embeddium + Oculus 由 `forge-1.20.1/build.gradle` 的 `modImplementation` 提供，dev run 里实测 `EMBEDDIUM_LOADED=true` / `OCULUS_LOADED=true` 且进世界后打印 `Using shaderpack: ComplementaryUnbound_r5.9.3.zip`；1.21.1 Sodium + Iris、26.1.2 渲染栈由 `mt_env` 下载）。launch 侧回显 `SHADERS=enabled pack=…` + `SHADERPACK_LOADED=true`（**版本相关**：1.20.1/1.21.1 读 latest.log 的 `Using shaderpack:` 行——⚠️ 该行**出现在进入世界之后**，必须放在已进世界的闸门里判；26.1.2 读 `config/iris.properties` 的 `enableShaders`）。需要「关光影冷启动」的用例（`SHADER-VISION-26.1.2` 步骤 0）必须自己显式执行 `mt_env.ps1 shaders --state off`，不依赖默认值。
3. **禁止游戏失焦打开 ESC 菜单**（2026-09-17 用户裁决）：`mt_launch` 在**每次冷启动之前**强制把 `run/<V>/options.txt` 的 `pauseOnLostFocus` 写成 `false` 并回显 `PAUSE_LOCK: on` —— 失焦暂停会让后台注入（`mt_inject`）与截图（`mt_capture`）全部失效，还会把暂停菜单顶在画面上。统一查询/手改入口 = **新增的全局调试命令** `pwsh -File scripts/test/mt_env.ps1 debug --version <V> [--pause-lock on|off|status] [--shaders on|off|status] [--keybinds status|repair]`（不带开关即回显各项目前状态）。**据本条删除测试流程里不必要的 Esc 按键**：`mt_inject` 的 `Esc→Tab→Enter` 归一化在「pause-lock 生效 + `-NoEsc`」时**一律跳过**并回显 `ESC_SKIP: …`；只有真正需要清空**容器/聊天**界面时才去掉 `-NoEsc`（Esc 是唯一能关容器的安全键，该场景行为不变）。
4. **游戏进程捕获/等待时长必须严格受控**（2026-09-17 用户裁决「严格控制系统进程捕获时长」）：所有等待都有硬上限且**必须回显耗时**，禁止无限等待 ——
   | 环节 | 硬上限 | 回显 |
   |---|---|---|
   | 进入世界就绪（launch） | 180s（`MT_LAUNCH_READY_TIMEOUT_SEC` 覆写，≥30） | `READY_WAIT: budget=…` + `MT_TIMING: world-ready=…s` |
   | launch 子进程终态标记 | 180s（`MT_LAUNCH_TIMEOUT_SEC`）；日志 90s 零增长即判 STALL | `CHILD: STALL` / `CHILD: TIMEOUT` |
   | env / build / preflight | 240s / 300s / 120s | 同上 |
   | 单条用例 | 180s（`--case-timeout`） | 该条记 TIMEOUT 后继续跑下一条 |
   **取消固定空等**：launch 就绪等待不再先 `Start-Sleep 30`，改为**立即 3s 轮询**（实测省下约 30s 纯等待；读日志是纯文件读）。
   **调用方约定**：长驻阶段（launch）若被外层管道捕获（如 `| Select-Object`）必须改成文件重定向（`*> 文件`）——否则整条链路要等长驻子进程放开 stdout 句柄，表现为「launch 早已 `MT_LAUNCH: OK`，命令行却迟迟不返回」。

5. **注入按键语义依赖的绑定不得漂移（潜行/冲刺不变量，2026-09-19 实测踩坑）**：工具链以**真实按键注入**表达语义（`mt_inject.ps1 mouse --button right --shift` ⇒ 真实 `SendInput(LEFT SHIFT)`），而游戏侧读的是 `options.keyShift`（潜行）**当前的绑定** —— 两者只有在绑定未被改动时才等价。实测 `run/1.21.1/options.txt` 的 sneak/sprint 被**换绑**（`key_key.sneak:key.keyboard.left.control` + `key_key.sprint:key.keyboard.left.shift`）后，「右键 + 潜行 = 取消」的 `SELECTOR-KEYS` 用例注入的 LEFT SHIFT 被游戏当成**冲刺** ⇒ 客户端一直走「右键 = 自用提示」分支，断言 `key=right_sneak action=cancel` **稳定失败**（同日连续 3 次），而绑定未变的 1.20.1 同一用例全绿 —— 是**环境漂移**而非被测代码缺陷。处置：env 阶段（`mt_env.ps1 mods`）幂等修回并回显 `MT_KEYBINDS: OK` / `MT_KEYBINDS: REPAIRED <旧值→新值>`；查询/手工修复入口 `pwsh -File scripts/test/mt_env.ps1 debug --version <V> --keybinds status|repair`。**禁止**把「shift/潜行类用例失败」先归因于被测代码 —— 先跑该 `status` 查询核对绑定。

6. **瞬间效果（instant mob effect）在「下一 tick」结算 —— 涉及治疗的判据必须延后读数（2026-09-19 实测确立）**：`MobEffects.HEAL`（原版 `minecraft:instant_health`；两发布线字段名均为 `HEAL`）经 `LivingEntity#addEffect` **只把实例放进 `activeEffects`**，真正回血发生在**下一 tick** 的 `MobEffectInstance#tick → MobEffect#applyEffectTick`（`InstantenousMobEffect` 的 `shouldApplyEffectTickThisTick` / 1.20.1 的 `isDurationEffectTick` 均为 `duration >= 1`；**两线的 `addEffect` 都没有 instant 分支**，原版 `/effect give … instant_health` 走的也是这条路径）。⇒ 在**同一 tick**（如命令体末尾）读 `getHealth()` **必然读到旧值**，会把「瞬间治疗已生效」写成假 FAIL。现场铁证（1.21.1）：`/data get entity @s Health` 在施放前 = `12.0f` → 施放同 tick 仍 `12.0f` → **下一 tick** `16.0f`（同批的猪 `6.0f → 10.0f`）。**口径**：凡判据依赖「一次性回血 / 瞬间治疗效果」的用例，读数一律**排到 ≥1 tick 之后**（本仓先例 = `luluprep`/`luluactive`/`luluanchor` 的 `AP_<tag>_AFTER:delay=2:` 行，由 `ServerEvents.tick` 回调经 `emitTo` 落 latest.log）；命令体同 tick 只报**同步可读**项（会话 / 冷却 / 治愈点数 / 非瞬间效果）。同一类坑的另一面：**不要用「按类型搜索（`typeIdOf(entity) == minecraft:xxx`）取靶」** —— 实机两轮实测该匹配不可靠（取到过施放者自己、`minecraft:spider` 一条也匹配不到），取靶应由 setup 命令**发布句柄**、判据只读句柄（见 `KNOWN-ISSUES.md` KI-E2）。

3. 校验 KubeJS `run/<版本>/logs/kubejs/server.log` 为 **0 errors**（进入世界后第一步）。

**失败处理**：`MT_LAUNCH: BLOCKED`（未在时限内进入世界）→ 查看 `run/<版本>/runclient_launch.log` 末 30 行；退出码 `11`。检测到崩溃报告 → 退出码 `2`，进 `crash-reports` 定位。

> **快照点与断言窗口（B7 起）**：阶段 L 成功后由 `mt_assert.ps1 snapshot --window launch` 记录 `latest.log` / `debug.log` / `kubejs/server.log` 的**字节游标**与 crash 基线，并把这组游标**冻结**为 `launch_offsets`；阶段 C 的**每条用例开始**再由 `mt_case.ps1` 写 `snapshot --window case` 刷新当前窗口 ⇒ 断言窗口是「**自本用例起**」，而不是旧的「自 launch 起」（旧写法会让前序用例把断言喂饱 ⇒ 假 PASS，B6 实测 `APDUMP|LOCKRAW|` 命中数随用例递增 14→28→…→126）。用例可用 `assert.scope` 显式切换：`case`（缺省）/ `launch`（读 `launch_offsets`）/ `whole`（整文件）；非法值在 `validate` 阶段即被拦下。**`mixin` 断言固定读 launch 窗口**（跟随 case 会退化成恒真），`crash` 基线亦随每条用例刷新。⚠️ **反向断言注意**：`absent` 的旧语义是「整文件不得出现」，收窄后只检测本用例窗口 —— 凡「文本不是本用例产生」（尤其启动/数据包解码期文本，如 `Could not decode GlobalLootModifier`）的反向断言**必须显式写 `scope: whole`**，否则会静默丢掉启动期覆盖。详见 `scripts/test/TESTING-SPEC.md` 的「断言窗口」段。

### 阶段 C — 测试条目

**前置条件**：阶段 L 通过且已记录快照点。

**执行命令**：
```bash
pwsh -NoProfile -File scripts/test/mt_case.ps1 run-dir --version 1.21.1            # 执行全部适用条目
pwsh -NoProfile -File scripts/test/mt_case.ps1 run     --version 1.21.1 --case cases/XXX.json
pwsh -NoProfile -File scripts/test/mt_case.ps1 run     --version 1.21.1 --case XXX       # 裸名亦可
pwsh -NoProfile -File scripts/test/mt_case.ps1 validate --case cases/XXX.json      # 只校验不执行
```

> `--case` / `--dir` 的相对路径按 `scripts/test/` 解析（不是按 cwd），并接受裸名（自动补 `.json` 并在 `cases/` 下查找），因此从仓库根或任意目录调用都成立。

**窗口锁定（注入前置）**：所有注入都会带上 `--version`，`mt_inject.ps1` 据此只认命令行匹配该版本 dev run 的窗口。
用户自己的整合包客户端同时开着也不会投错；**若存在多个候选且无法唯一确定，注入直接报错退出，而不是"取第一个"** —— 投错窗口会产生「看起来通过」的假结论。

**输入法自动切换（免手动，改自旧版硬前置）**：旧流程要求「测试前手工把系统输入法切成英语(美国)」，靠人记住，忘一次就会按键串扰。
现在改为**注入前自动切目标窗口**，由 `mt_ime.ps1` 实现：

- 切换作用域是**目标窗口所在线程**（`WM_INPUTLANGCHANGEREQUEST`，失败则 `AttachThreadInput` + `ActivateKeyboardLayout` 兜底），
  **不改系统全局默认输入法**（不动 `SPI_SETDEFAULTINPUTLANG`）——所以不影响用户在别处的中文输入；
- 效果只在该游戏进程存活期间有效，**进程退出即自动回到系统原输入法**，无遗留状态；
- 注入时会校验切换结果，失败则**拒绝注入并报错**（`as-is` 模式可显式跳过，仅用于排障）；
- 前置检查因此只要求「en-US 布局**可用**」，不再要求「当前输入法已是 en-US」——zh-CN 是当前输入法也能直接跑。

```bash
pwsh -NoProfile -File scripts/test/mt_ime.ps1 list        # 列出可用布局 + en-US 可用性
pwsh -NoProfile -File scripts/test/mt_ime.ps1 selftest    # 跨进程自检：切成功 / 宿主自观测 / 本进程不受影响
pwsh -NoProfile -File scripts/test/mt_ime.ps1 status      # 当前前台窗口的语言状态
```

> 自检是**跨进程**的：父进程切换子进程窗口，由子进程自己打印线程语言变化，并同时断言父进程（即"现有功能"）输入语言**未被改动**——避免"切了别人的键盘布局"这类副作用。

**条目文件（Case Spec）**：`scripts/test/cases/*.json`，字段为 `case_id` / `title` / `version` / `target` / `fixtures` / `steps` / `asserts` / `evidence` / `on_fail`。`version` 与当前执行版本不符的条目自动 `SKIP`。

**动作原语表（封闭，不允许自造 `op`）**：

| `op` | 作用 | 关键字段 |
|---|---|---|
| `inject_key` | 注入按键/鼠标 | `key`（attack/rclick/shift-rclick/j/h/t/e/escape/enter/f2/f3/w/1-9）、`hold_ms` |
| `inject_command` | 注入斜杠命令 | `command`、`no_esc`（目标选择激活期必须为 true） |
| `kubejs_reload` | 热重载 KubeJS（`/kubejs reload server-scripts` + `/reload`） | — |
| `wait` | 显式等待 | `ms` |
| `screenshot` | 截图并登记当前世代 | `tag`、`mode`（f2/window） |
| `assert` | 内联断言 | 同下表断言字段 |
| `note` | 写入报告时间线的人工观察点 | `text` |

**断言类型**：

| 类型 | 判定者 | 说明 |
|---|---|---|
| `log` | 脚本 | 增量区间内正则命中（`pattern`、可选 `source=latest\|debug`） |
| `absent` | 脚本 | 增量区间内**不得**出现（用于「旧机制无残留」类） |
| `crash` | 脚本 | 无**本次新增**崩溃报告（快照基线 + 运行开始时间双重判定；旧世代残留不计入） |
| `kubejs` | 脚本 | `kubejs/server.log` 为 0 errors |
| `mixin` | 脚本 | 增量区间无 Mixin apply failed |
| `vision` | **视觉模型** | 只产出提问请求；脚本不解析像素 |

**预期结果**：每条目输出 `MT_CASE_RESULT: <id> = PASS|FAIL|BLOCKED|ERROR|SKIP`，末尾 `MT_CASES_SUMMARY: …`。单条失败**不阻断**后续条目。

**失败处理**：
- `FAIL` — 断言不通过，按真实缺陷处理；`on_fail: keep_game_running` 时保留游戏现场便于取证：会落 `cases/.mt_keep_alive` 标记（内容为失败原因），退出清理据此**不杀游戏进程**，取证完用 `pwsh -NoProfile -File scripts/test/mt.ps1 --phase stop --force` 释放；
- `BLOCKED` — 依赖缺失（KubeJS 日志缺失、注入找不到窗口等），先修环境再复跑，**不得计为功能缺陷**；
- `ERROR` — 执行器异常（未知原语、条目 schema 非法、注入找不到窗口）；条目被拒绝执行，不产生半执行状态。

### 阶段 R — 报告与结论

**前置条件**：阶段 C 执行完毕（即使有失败也要收尾）。

**执行命令**：
```bash
pwsh -NoProfile -File scripts/test/mt_report.ps1 collect --version 1.21.1 --verdict PASS
pwsh -NoProfile -File scripts/test/mt_report.ps1 summary
```

**预期结果**：
- `scripts/test/reports/<运行id>/<版本>/report.md` — 含**明确的通过/失败结论**、阶段结果表、条目结果表、增量日志标记、崩溃/异常（区分基线与新增）、证据索引、复跑命令；
- `scripts/test/reports/<运行id>/SUMMARY.md` — 两版本各自结论 + 综合结论。

**失败处理**：报告为收尾动作，失败不影响结论判定；若证据缺失（如 `runclient_launch.log` 不存在）在报告中标注为缺失而非静默跳过。

---

### 截图识别规则（必须遵守）

- **不使用任何固定截图目录的环境变量配置**。截图路径由 `lib/Mt.Paths.psm1` 按版本推导，且每次运行分配唯一运行 id。
- **只识别当前（最新）生成的截图**：截图在采集时登记进**当前运行的世代清单**（`run/<版本>/screenshots/.mt_shots.json`）；断言与报告一律经 `current_shots(run_id)` 取图。上一世代的残留图片不会被当作本次证据。
- 需要清场时执行 `pwsh -NoProfile -File scripts/test/mt_capture.ps1 prune --version <版本>`，只保留当前世代。
- 视觉断言若引用的截图不属于当前世代，判定为 `BLOCKED`（证据不可信）而非 `FAIL`。

### MCP 集成

| 通道 | 服务 | 用途 | 边界 |
|---|---|---|---|
| 注入通道 | `mt_inject.ps1`（ctypes/PostMessage） | 游戏内按键、鼠标、斜杠命令 | 注入前自动把**目标窗口线程**切为 en-US（`mt_ime.ps1`），无需手动改系统输入法；非 Windows 平台降级为 MCP |
| `computer_control` | computer-control-mcp（stdio） | 窗口列表/激活、截图与 OCR、窗口外 UI 操作 | **不用于**游戏内镜头与攻击；环境变量仅保留 `COMPUTER_CONTROL_MCP_WGC_PATTERNS=minecraft` |

> **mineflayer bot（minecraft-mcp-server / `LLMBot`）已于 2026-09-14 彻底移除**，连同其调用规范一并下架：
> 阶段 L 的 `/publish 25565`、条目原语 `mcp_call`、断言类型 `mcp`、会话层回填协议
> （`cases/.mcp-pending.json` ↔ `cases/.mcp-results.json`）与「双人条目需先验证 `BOT_JOINED`」的要求**全部不再存在**。
> 移除原因（实机取证）：原版协议的 bot **无法加入** Forge/NeoForge 服务器 ——
> NeoForge 1.21.1 在配置阶段直接拒绝（`你正在尝试连接一个安装了NeoForge的服务器…`）；
> Forge 1.20.1 由 `NetworkRegistry` 判定
> `Channels [astral_dice:main,patchouli:main,curios:main] rejected vanilla connections` 后断开
> （任一通道不放行 vanilla 即整台服务器不放行，非本模组可修）。
> 因此**全部用例只保留单客户端 + 输入注入**这一条驱动路径；需要服务端侧读写时改用探针命令经游戏内聊天注入。

### 环境与兼容栈速查

| 项目 | 1.21.1 | 1.20.1 |
|---|---|---|
| dev 实例 | 仓库根 `run/1.21.1`（NeoForge 21.1.235） | 仓库根 `run/1.20.1`（Forge 47.4.10） |
| 测试世界 | `testworld`（超平坦/创造/允许命令/`keepInventory=true`） | 同左（支持种子快恢复，恢复后重新写回两条世界规则） |
| 兼容模组 | Sodium + Iris + ModernFix（dev 安装） | Embeddium + Oculus（**仅生产环境验证**） |
| 光影 | `ComplementaryUnbound_r5.8.1.zip`（`config/iris.properties`） | 同文件（`config/oculus.properties` + `iris.properties`） |
| 渲染兼容断言 | `SODIUM_LOADED` / `IRIS_LOADED` | dev 不适用（见阶段 E） |
| DEBUG 标记 | `run/1.21.1/logs/debug.log` | `run/1.20.1/logs/debug.log` |
| KubeJS | `run/1.21.1/kubejs/server_scripts/` | `run/1.20.1/kubejs/server_scripts/`（`ResourceLocation(...)` 替代 `.parse`） |
| Mixin 差异 | MDG + refmap（注解选择器翻译） | **Mixin Booster 硬前置**（Sponge Mixin 运行时）**+ refmap**：Booster 只重映射**字节码引用**、**不翻译注解选择器**，缺 refmap 时生产 reobf jar 启动即 FATAL（见「forge-1.20.1 子项目关键差异速记」） |

**KubeJS 辅助命令（权威数值出口，供 `log` 断言使用）**：
- 1.21.1 / 1.20.1：`/astraldice_ts_count <半径>`、`/astraldice_ts_present <实体id> <半径>`、`/astraldice_ts_equip <sign>`、`/astraldice_ts_clearcd`；
- 1.20.1 另有 `/astraltest equip <0-3>`、`/astraltest slotcheck0..3`（骰子星级 → 卡牌格数）。
- Rhino 硬坑：命令注册必须在 `ServerEvents.commandRegistry` 回调内；每个执行体提为顶层命名函数且函数内一律用 `var`（否则 redeclaration）；用 `Java.loadClass`（非 `Java.type`）与 `sendFailure`；修改后执行 `/kubejs reload server-scripts` + `/reload` 即可热载，无需重启游戏。

### 新增内容 / 用户指定内容的测试（子技能）

为**新增内容**（新物品、筹码、立牌、配方、效果牌）或**用户指定内容**生成测试条目时，由子技能 `mt_gen_case.ps1` 产出条目文件，**插在环境创建与清理之间的阶段 C**，复用阶段 P/B/E/L/R 不变：

```bash
# 规范驱动：按内容类别套用 AGENTS 对应「必须遵守」条款生成断言
pwsh -NoProfile -File scripts/test/mt_gen_case.ps1 --version 1.21.1 --new <注册id>
# 回归驱动：从 CHANGELOG_ZH.md 找该功能条目，抽可复现步骤
pwsh -NoProfile -File scripts/test/mt_gen_case.ps1 --version 1.21.1 --feature <功能名>
# 描述驱动：用户自然语言描述
pwsh -NoProfile -File scripts/test/mt_gen_case.ps1 --version 1.21.1 --spec "<描述>"

# 生成后按常规流程执行（也可一步完成）
pwsh -NoProfile -File scripts/test/mt.ps1 --version 1.21.1 --new <注册id>
```

**约束**：
- 生成器**不执行、不启动游戏、不修改环境脚本**；产出物在物理上只是阶段 C 的一部分，无法触及前后两端；
- 生成结果**必须通过 schema 校验**（未知原语、非法字段、无断言一律拒绝落盘）；
- **断言优先级**：可机械判定的（`log`/`absent`/`crash`/`kubejs`/`mixin`）优先；无法机械判定的降级为 `vision` 并在报告中标注需人工复核；不得用像素级硬编码判定；
- 自检条目 `cases/SMOKE-TOOLCHAIN.json` 可在无游戏环境下验证执行器与断言链路。

### 注意事项

- **输入法无需手动切换**（两版本通用）：注入通道按美式键盘布局投递虚拟键码/扫描码，`mt_inject.ps1` 会在注入前把**目标游戏窗口所在线程**切为 en-US（`mt_ime.ps1`），进程退出即自动复原、不影响用户其它程序的输入语言。旧版「测试前先手工切系统输入法」的硬前置**已取消**；前置检查只校验 en-US 布局**可用**。IMBlocker 已从两版本测试环境移除。
- 失焦暂停必须关闭（`pauseOnLostFocus:false`，阶段 E 处理）。
- 游戏内瞄准通过把生物传送到固定朝向（+Z）的准星正前方实现；**MCP 鼠标移动不用于游戏内镜头**（GLFW 抓取光标下不可靠）。
- **注入器默认通道 = 真实 SendInput（2026-09-13 已修复并实机验证，必须遵守）**：`mt_inject.ps1` 默认走 `sendinput`（按键按**扫描码** `SendInput`，文本按 `KEYEVENTF_UNICODE` 整串输入，**不按 `/` 键**）；`--transport postmessage` 只用于排查。**历史误判已作废**：早先记录的「PostMessage 按键被 GLFW 丢弃」「按 `/` 键会把游戏顶进暂停菜单」两条**都不成立**（PostMessage 的 WM_KEYDOWN 确实被游戏接受——当时正是它把暂停菜单顶开的；`/` 那条是在「菜单已被顶开」的前提下取的观测）。**真实根因（变量遮蔽）**：入口第 106/109 行设的 `$script:Transport='sendinput'` / `$script:EscNormalize=$false`，被入口解析段的顶层同名变量 `$Transport=''` / `$EscNormalize=$false` **覆盖成空串**——脚本顶层 `$X` 与 `$script:X` **是同一个变量**。于是 `$script:Transport -ne 'sendinput'` 成立 ⇒ ① 实际落进 postmessage 分支（文本走 `WM_CHAR`）；② `$escNormalize` 恒为 **`$true`**，每条命令注入前先发一次 Esc 把暂停菜单顶开（`Minecraft.pause=true` → `IntegratedServer` 停止 tick，日志 `Saving and pausing game...`），后续按键与命令全部丢进菜单，而脚本仍打印 `MT_INJECT_CMD` 成功 ⇒ **断言「未命中」而 KUBEJS / crash / mixin 断言全绿**的假失败。已修复：CLI 解析变量改名 `TransportArg` / `EscNormalizeArg`，并实机验证裸跑 `mt_inject.ps1 cmd --command "/astralprobe blastbonus SC10"` **零新增** `Saving and pausing`、`AP_SC10_*` 正常命中。**新增入口变量时务必带 `Arg` 后缀，禁止与 `$script:` 默认值同名。**
- **注入前置断言保留**：每次注入前 `Assert-MtInjectForeground`（`ShowWindow(SW_RESTORE)` + `BringWindowToTop` + `AttachThreadInput` + `SetForegroundWindow`，重试 3 次仍失败即 `exit 2`，不静默通过）；`sendinput` 通道**不做**界面归一化（每条命令末尾的 Enter 已把聊天关掉），`--esc-normalize` 仅供排查。
- **用例失败取证优先看「有无输出」**：若某条探针命令的 `AP_*` 读数一条都没有（而 KUBEJS/CRASH/MIXIN 断言全绿），先怀疑注入通道/游戏暂停，不要先怀疑模组 —— 用 `computer-control` MCP 发一次真实按键或截图验证现场。
- **「注入没反应」的正确排查顺序（2026-09-13 更正，别再从「焦点」入手）**：实测在 `GetForegroundWindow()` 命中游戏 hwnd 时，注入的 **Esc / F3+P / T / 整串文本全部生效**，因此**不再**断言「前台却无键盘焦点 ⇒ 按键被静默丢弃」（该说法已被上面那条变量遮蔽根因取代）。按此顺序排查：① **先看注入时刻日志有没有 `Saving and pausing game...`** —— 一出现就说明有 Esc 被发出、暂停菜单已顶开，后续按键与命令必然全部丢失（这正是历史批量失败的**唯一已知原因**）；② 确认 `run/<版本>/options.txt` 里 `pauseOnLostFocus:false`（`mt_env.ps1` 阶段 E 自动写入；若为 true，`GameRenderer.render` 会在 `!isWindowActive()` 持续 500ms 后 `pauseGame`，把世界停住）；③ 仍怀疑输入通道时，用 `computer-control` MCP 的真实点击/按键做对照，或直接跑 `SMOKE-TOOLCHAIN` 用例分辨「工具链问题」与「产品问题」。注入前 `SetForegroundWindow` **并** `SetFocus`（附加前台线程后调用）继续保留，但它是**加固**，不是「不做就必然失效」的开关。
- 测试流程**必须串行**：本流程独占 `runClient`，前置检查会拒绝在已有实例运行时启动（不抢占、不并行两版本）。
- **进程泄漏已被自动化拦截**：退出时（正常 / 失败 / Ctrl-C）自动收停本流程进程与 Gradle 守护（详见「退出清理」）。手工收停用 `--phase stop`；失败取证保留的现场要用 `--phase stop --force` 释放，否则会一直堵住下一次前置检查。
- `AGENTS.md` **自 2026-09-15 起已纳入版本库**（`.gitignore` 中的排除项已移除，随提交进入本仓库；是否推送 GitHub 仍按「仅在用户明确要求时执行」）；`scripts/` 下脚本本身入库，仅 `scripts/test/mt.conf`、`scripts/test/reports/*`、`scripts/test/cases/.mt_*` 为本地忽略的运行时产物（与现有 gitignore 一致）；git 网络操作仍须经代理。
- 具体用例清单（功能 TC、立牌 S 系列等）以 `scripts/test/cases/` 下的条目文件与 `scripts/test/reports/` 下的历史报告为准；旧版 PowerShell 脚本归档包 `legacy_scripts_20260912.zip`（含 MANIFEST 与逐文件 sha256，可解压取回）已于 2026-09-15 从 `temp/` 移出至 `docs/archive/`，仅供追溯。
- **仓库内 `.ps1` 共 33 个（2026-09-26 在本 dev 树逐目录重数;另有 6 个 `.psm1` 模块）,全部在用**：`scripts/test/*.ps1`（18 个 + 子目录 `scripts/test/tools/lp_offline_assert.ps1` 1 个）、`scripts/verify/*.ps1`（6 个 = 5 个内容校验 + 新增的门槛独立脚本 `verify_forge_loader_gate.ps1`）、`scripts/maintenance/repair-loose-refs.ps1`、`scripts/audit/tooltip_color_audit.ps1`、`scripts/devtools/*.ps1`（3 个）、`tools/*.ps1`（3 个）;根 `deploy.ps1` **只存在于发布线树**（本 dev 树实测 `Test-Path deploy.ps1` = False,故不计入本树计数）。旧执行体 `scripts/test/mt_loadergate.ps1` 已于 2026-09-26 删除 ⇒ 与本轮新增的独立门槛脚本**净变化 0**。不要删；`.psm1` 为 `scripts/test/lib/*.psm1` 5 个 + `scripts/verify/ChipCommon.psm1`。迁移期比对器 `scripts/devtools/Compare-MtOutput.ps1` 已于 2026-09-15 删除（它比对的 python/bash 原件自 `92fbeaf` 起已不存在）；旧测试 `.ps1` 归档包在 `docs/archive/legacy_scripts_20260912.zip`。
- `scripts/test/TESTING-SPEC.md` 是测试条目 schema 规范；旧功能流程文档 `scripts/test/FLOW_1.20.1_functional.md` 已于 2026-09-12 删除（`git show 57d337f^:scripts/test/FLOW_1.20.1_functional.md` 可取回；归档包内不含此文档），实际执行一律以本章节为准。

### 测试流程超时与看门狗（Test Timeout & Watchdog）— 必须遵守

**卡死根因（2026-09-15 实测取证并已修复）**：`mt.ps1` 的 `Invoke-MtChild` 曾以 `Start-Process -NoNewWindow -PassThru -Wait` 启动子阶段，而 `-Wait` 会等待**整棵进程树**；`mt_build` 新起的 **Gradle 守护进程是其后代且长期存活** ⇒ **全流程会永久卡在 `build` 相位**（曾导致 3 位执行者先后挂死）。修复方式：需要长期驻留的阶段（launch / 会派生守护进程的 build）**改为脱离式启动 + 轮询终态标记**，不再用 `-Wait` 等树。

**分层硬预算（2026-09-16 收紧；默认值必须知道）**：

| 层 | 开关 | 默认 | 行为 |
|---|---|---|---|
| 单条用例 | `MT_CASE_TIMEOUT_SEC` / `--case-timeout` | **180 s** | 超时记为**独立状态 `TIMEOUT`**（与 `FAIL`=断言不满足、`ERROR`=跑不起来**严格区分**），**继续跑下一条**，不整体挂住；退出码 **12** |
| 单阶段 | `MT_<阶段>_TIMEOUT_SEC` | preflight 120 / build 300 / env 240 / launch 180 / report 90 / stop 150 / cleanup 180（秒） | 到点即终止**本流程那个**子进程并打印卡点；**绝不动进程树** |
| cases 阶段 | `MT_CASES_TIMEOUT_SEC` | 单条 = 单条上限+90 s；`run-dir` = 90 s×用例数+90 s | 阶段开始时打印 `CASES_BUDGET:` 实际预算 |
| 全局运行 | `mt.ps1 --run-timeout <秒>` | **2700 s**（旧默认「0 = 不限」已废除） | 超时走 `--phase stop --force` 收停，报告写 `TIMEOUT` |
| 外部看门狗 | `scripts/test/mt_watchdog.ps1` | — | 见下 |

**等待期可见性（2026-09-16 新增，长流程必读）**：
- `MT_WAIT:` 心跳每 10 s 一行（脚本 / 已等待 / 硬上限 / 子进程 CPU 或日志字节数与最后增长时刻）；
- 进度信标 `cases/.mt_progress.json`：**每次阶段、每条用例、每个步骤开跑前**落盘
  `{phase, version, case, step_index, step_total, op, detail, ts}`（原子写）。**卡点定位只看这一个文件**；
- launch 走脱离式并额外判定「日志 90 s 零增长 ⇒ `CHILD: STALL`」（进程存活 ≠ 有进展）。
- ⚠️ **后台/长任务禁止用会缓冲的管道**：`pwsh … | Select-Object -Last N` 会把子进程输出缓冲到命令结束，
  作业一旦被截断**整段日志消失**（2026-09-16 实测踩坑，导致一次 7 分 45 秒事故事后无法归因）。
  后台跑一律 `*> <文件>` 落盘后再读文件。

**`scripts/test/mt_watchdog.ps1` 用法**：`-Version <1.21.1|1.20.1>`、`-StallSeconds <int>`（默认 **180**）、`-WarnSeconds <int>`（默认 `min(90, Stall/2)`）、`-PollSeconds <int>`（默认 10）、`-IdleExitSeconds <int>`（默认 60）、`-Action <report|stop>`（默认 `report`）。

- **进展判据（2026-09-16 重写，旧判据已作废）**：① **进度信标 `cases/.mt_progress.json` 的内容**（最强；阶段/用例/步骤都会刷新它）；② `.mt_run_state.json`、`.mt_snapshot.json`、`.mt_active_run`、活动报告目录下的文件；③ `latest.log` **与** `runclient_launch.log` 尾部的**语义标记**（`[CHAT]`、`AP_*:`、**`MT_*` 驱动进展行**、`Saving and pausing`、`logged in/out`、`joined/left the game`、`KubeJS Server/`）。
  ⚠️ **禁止**再把「日志文件大小/mtime」或 DEBUG 行当进展：实测游戏空闲时 ModernFix 仍**每分钟**写一条 `Worker-ResourceReload-N shutdown`，旧判据因此一路误报 `ALIVE（有进展）`，把一次 7 分 45 秒的空转完全掩盖。
  ⚠️ `MT_*` 那一族**必须**保留：launch 阶段 `latest.log` 长时间只有 DEBUG 噪声，真正的进展只在 `runclient_launch.log` 里。

- 定期输出 `MT_WATCHDOG: ALIVE t=<秒> step=<阶段 用例 步/总 op> │ marker=<最后语义标记> │ client=alive|none driver=<驱动进程数>（停滞 <秒>）` **心跳**（供人/代理判断存活，不要用"干等"代替）；
- 停滞达 `-WarnSeconds` ⇒ `MT_WATCHDOG: WARN` + 卡点取证（**提前预警，不退出**）；达 `-StallSeconds` ⇒ `MT_WATCHDOG: STALL` + 完整取证（进度信标原文 + 客户端/驱动进程存活 + 日志尾部），`-Action stop` 时经 `mt_stop` 收停；**退出码 42** 表示「检出停滞」。
  ⚠️ **只有客户端阶段（launch/cases）才自动收停**：preflight/build/env/report/stop 本来就没有客户端，在那里收停只会误杀一次正常构建（实测踩过）。
- 「驱动进程与客户端都已退出且再无进展」达 `-IdleExitSeconds` ⇒ **退出 0**（旧版会以莫名退出码结束，容易被误读成故障）。
- **安全红线**：watchdog **只经 `mt.ps1 --phase stop` 收停**，**禁止**自己 `taskkill` 任意 java 进程（尤其**绝不可误杀用户其它 java 程序**）。

**KubeJS 探针安装（2026-09-16 起自动化）**：`pwsh -File scripts/test/mt_env.ps1 kubejs --version <版本>` 按**内容哈希**把
`scripts/test/resources/kubejs/<版本>/**` 同步到 `run/<版本>/kubejs/**`；`mt_env.ps1 mods` 顺带执行，`mt.ps1 --phase launch` 启动前**强制**执行（失败即 `BLOCKED(11)`）。
⚠️ 此前**没有任何脚本**负责这一步（只写在文档里、实际靠手工拷贝）⇒ 实测出现过「run 目录 218 KB / 模板 229.7 KB、哈希不一致」的静默漂移：用例照样注入 `/astralprobe <新命令>`，而游戏侧**没有那条命令**，所有断言只会读不到读数。探针**命令**的改动仍需**冷启动**才生效（`/kubejs reload server-scripts` 不重绑已注册命令的 lambda）。

**纪律（必须遵守）**：

1. **任何「启动客户端 + 跑用例」的长流程，必须由 `mt_watchdog.ps1` 包裹，或至少设置 `MT_CASE_TIMEOUT_SEC`**；禁止无超时地等待客户端标记。
2. 需要长期驻留 / 会派生守护进程的阶段一律**脱离式启动**，不得用 `-Wait` 等整棵进程树。
3. **遇到 `TIMEOUT` 或 `STALL` 时**：先读 `cases/.mt_progress.json`（卡在第几阶段/哪条用例/第几步/什么 op）与 watchdog 的取证块定位，再决定是否重跑；**不要盲目重试整轮**（这正是先前反复挂死的原因）。
4. 判断"是否还活着"应当看**进度信标与语义标记心跳行**，而不是单纯等待、也不是只看进程是否存活。
5. **任何后台任务超过 5 分钟没有任何响应，立即终止并检查状态**（用户 2026-09-16 裁决）：先 `job_output`/读日志文件确认卡点，再按第 3 条定位；**禁止**继续干等。
6. **客户端进程判据（2026-09-16 实测）**：1.21.1 dev 客户端主类是 `net.minecraft.client.main.Main`，而 **1.20.1 dev 客户端主类是 `net.neoforged.devlaunch.Main`** —— 只按前者筛查会**漏判 1.20.1 的残留客户端**（进而出现「明明有客户端却报无」或反过来重复启动被 `BLOCKED(rc=2)` 拦下）。判定一律用脚本内的 `Get-MtProcessMarkers`（gradle 任务选择器 + run 目录），**不要**自己按主类名 grep。

**OP 前提（测试命令可用性）**：`/astralparty` 与 `/astralprobe dumpstate` 需要**权限级 2**；测试环境实测 `hasPermissions(2)=1`、`level=4`（单人 quickplay 集成服）。不满足时 preflight 报 **`BLOCKED(11)`**（探针/dump 不可用报 `ERROR(2)`）。**禁止**为测试降低 `requires` 门槛或加后门 —— 测试必须走真实 OP 路径。已知缺口：非 OP 的**否定面**在单人环境无法覆盖。

**`mt.ps1 --version` 语义**：全流程分支**已尊重 `--version`**（指定单版本时只跑该版本、不做跨版本门控）；此前忽略该参数、恒受 1.21.1 门控的行为已修正。

## NeoForge 上游 BUG 补丁（neoforge_fixes）— 必须遵守

**背景（已用字节码核实，2026-09-13）**:NeoForge 21.1.235 的 `LivingEntity#hurt` 在方法开头把新建的 `DamageContainer` 压入 `damageContainers`,随后调用 `CommonHooks.onEntityIncomingDamage`;**事件被取消时直接 `return false` 而不 `pop`**,容器永久残留。`javap -p -c` 实证:`hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z` 的全部 `IRETURN` = {9, 21, 30, 52, 88, 402, 1063};`Stack.push` 仅 1 次(off 66),`Stack.pop` 仅 2 次(off 397 无敌帧出口、off 1057 方法结尾),**off 88 的取消出口没有 pop** ⇒ 每取消一次泄漏一层。上游修复(取消分支补 `pop`,PR #3101)只落在 **26.1.x / 26.2.x**,**21.x 全线(含 21.1.250)未 backport**。

**本模组的三处取消点**(都走 `LivingIncomingDamageEvent#setCanceled(true)` ⇒ 命中泄漏路径;取消频率随闪避修复上升):骇客「网络防火墙」末影珍珠免疫(`NancyLuSignItem`)、枪匠破绽闪避、肾上腺素-高效闪避(`DiceCombatEvents.applyDodgeCancel`)。

**实现（仅 1.21.1 侧;1.20.1 无 `DamageContainer` 伤害管线,属平台差异,不是遗漏）**:
- `mixin/fixes/LivingEntityDamageContainerMixin`:`@Shadow protected Stack<DamageContainer> damageContainers` + `HEAD` 记录进入栈深 / `RETURN` **把栈截断回该深度**(逻辑在 `mixin/fixes/DamageStackSanitizer`,纯 JDK、可单测),**不是**盲目 `pop`;用完整描述符定位目标方法,避免未来同名重载选错。
- 独立配置 `src/main/resources/astral_dice.neoforge_fixes.mixins.json`:**`required: false`** + `injectors.defaultRequire: 0` + 两个注入器 `require = 0`;并在 `src/main/templates/META-INF/neoforge.mods.toml` 追加第二个 `[[mixins]]`(neo 侧唯一登记通道;若将来有人仿 1.20.1 加清单通道,须把该配置一并加入)。
- **为什么是「恢复进入深度」而不是补 `pop`**:未修复路径补 1 次、已 backport 路径 0 次、两个正常出口 0 次,**结构上绝不弹到低于进入深度** ⇒ 不会重复弹栈、不会 `EmptyStackException`;ThreadLocal 深度栈支持 `hurt` 嵌套逐层配对,`MAX_NESTING`/无记录分支一律保守 no-op。
- **未来三情形(按运行时 `sponge-mixin 0.15.2+mixin.0.8.7` 与 FML `loader 4.0.42` 源码逐条核验)**:① 上游只补 `pop`(结构不变)⇒ 照常注入、0 次 pop,行为正确,只多一行存活日志;② 上游重命名/重构 `hurt`(如 26.x 的 `hurtServer`)⇒ 注入器 `require = 0` 静默跳过,**连 WARN 都没有**,启动正常;③ 上游删除 `damageContainers` 字段 ⇒ `@Shadow` 失败被 per-mixin try/catch 吞掉,`config.isRequired()==false` ⇒ `ErrorAction.WARN`,仅一条 `Mixin apply for mod astral_dice failed …` WARN,启动正常(补丁不生效)。**三者都不会导致启动失败**。`require = 0` 是必需项:`requiredCallbackCount>0` 时抛 `InvalidInjectionException`,该异常发生在 `preApply` 阶段、**不受 `required:false` 保护**。

**验证与回归纪律（必须）**:
- 语义:`pwsh -NoProfile -File tools/run_mixin_stack_sanitizer_test.ps1`(纯 Java 10/10:未修复补 1 次 / backport 后 0 次 / 嵌套逐层 / null 与哨兵边界 / 两个正常出口 0 次 / 绝不低弹 / 配对失同步 no-op / 存活日志每 JVM 一次)。
- 游戏内:冷启动进入世界后 `run/1.21.1/logs/latest.log` **必须出现** `[astral_dice/neoforge_fixes] DamageContainer leak patch active (first hurt: entryDepth=…, beforeRestore=…, popped=…)`;增量区间**不得**有 `Mixin apply failed`(阶段断言已覆盖后者)。
- **该存活日志是「补丁活着」的唯一正面证据**:`required: false` 的代价是上游重构时补丁会**静默失效**(情形②连 WARN 都没有),因此**升级 NeoForge 版本后必须重新 grep 这行**;日志缺席 = 补丁已失效,须重新评估。**禁止**为了让失败更显眼而把它改成 `required: true` 交付——那会让未来一次上游重构直接变成启动崩溃。
- 临时排障:本地把该配置临时改为 `"required": true` 跑一次冷启动,任何 apply 级问题会立刻以 `MixinApplyError` 暴露;确认后**必须改回 `false`**。

## 「敌对目标」判定规范（Hostile Target）— 必须遵守

**口径（2026-09-14 用户裁决；2026-09-15 补充带上下文重载）**：`敌对目标 = 敌对生物 ∪ 已被激怒的中立生物`（带上下文时**并**「非同队伍且主动攻击过观察者的玩家」）。**唯一入口** = `combat/HostileTargets`：单参 `isHostile(Entity)` 与带上下文 `isHostile(Entity viewer, Entity target)`（两个子项目各一份同名同文实现）。任何新增/修改的敌对判定一律调用该入口，**不得**再就地内联写裸判据；**能给上下文的调用点一律用两参重载**。

- **敌对生物** = `net.minecraft.world.entity.monster.Enemy` 实例 —— 含 `Monster` 全部子类，以及 `Ghast`/`Phantom`/`EnderDragon`/`Slime`（含岩浆怪）/`Shulker`/`Zoglin`/`Hoglin`。
- **平静（未激怒）的「敌对类中立生物」同样计入**（用户 2026-09-14 裁决：维持现状）：末影人 `EnderMan`、僵尸猪灵 `ZombifiedPiglin`、猪灵 `Piglin`/猪灵蛮兵 `PiglinBrute`、疣猪兽 `Hoglin`/僵尸疣猪兽 `Zoglin` —— 它们**本身就是 `Enemy`**，不额外要求 anger 判定（原版铁傀儡索敌、UI 敌对配色同样如此）。
- **已被激怒的中立生物** = `NeutralMob` 实例且 `isAngry()`（= `getRemainingPersistentAngerTime() > 0`，被激怒后持续 20~39 秒）为真。全原版 `NeutralMob` **直接实现者只有 6 个**（1.21.1 与 1.20.1 一致，已从本机反编译源码逐个核对）：`EnderMan`/`ZombifiedPiglin`（二者即 `Enemy`）＋ **狼 `Wolf` / 铁傀儡 `IronGolem` / 北极熊 `PolarBear` / 蜜蜂 `Bee`**——**只有这 4 个**是靠 anger 判定进入敌对集合的。
- **永不算敌对目标**：熊猫/骆驼/山羊/羊驼/行商羊驼/海豚/狐狸等 —— 它们**不是** `NeutralMob`、没有愤怒计时器（依据同上：实现者只有那 6 个）。玩家**默认不计入单参 `isHostile`**（无上下文时无法判断「曾主动攻击过谁」）；玩家侧另有既有规则独立处理（如 `isBlessingTarget` 的「非队友玩家」、骇客的「敌对目标**或**玩家」），而带上下文的两参重载会把**非同队伍且主动攻击过观察者的玩家**计入敌对目标（见下条）。
- **判定顺序固定**：先 `Enemy`，再 `NeutralMob#isAngry()`。三条禁令：① **禁止**只写 `instanceof Enemy` 就宣称覆盖了「被激怒的中立生物」；② **禁止**用全限定名 `net.minecraft.world.entity.monster.Enemy` 绕过统一入口（历史上正是这种写法让 5 处判据点漏改）；③ **禁止**用 `instanceof Monster` 代替（`Monster ⊂ Enemy`，会漏掉恶魂/幻翼/岩浆怪/末影龙等）。
- **唯一豁免（它根本不是敌对判定）**：`event/LootInjectionHandler` 用 `instanceof Monster` 选**战利品注入池**（怪物类实体 0.3% 掉星盘）。这是掉落池口径，**不得**改调 `HostileTargets`（改了会让岩浆怪/幻翼/恶魂/末影龙开始掉星盘）。
- **敌对玩家规则与带上下文重载（全局，2026-09-15 用户裁决，必须遵守）**：`HostileTargets` 新增带上下文重载 `isHostile(Entity viewer, Entity target)`，把「**非同队伍、且曾主动攻击过 viewer 的玩家**」计入敌对目标（故大当家溅射等范围/波及效果、以及**电磁炮落雷**（2026-09-15 本批 D-B2 对齐，viewer = `LightningBolt#getCause()`，见下条）现在对这类玩家生效）；记录表为 `combat/PlayerHostilityTracker`（服务端内存静态表，不持久化）。口径细节：① **记录 = 只记「主动攻击」** —— 排除本模组内部 AOE/溅射/反击结算（`DiceCombatEvents.isInternalAoe()` / `isInCounterChain()` 闸门）、自伤、任一侧非玩家、以及被取消的伤害（挂点在最终伤害事件，晚于可取消的减伤前事件）；② **双方都没有队伍时可互为敌对**（同队才豁免；任何一方无队伍都不算同队——`EventTargetCollector`「未加入队伍视为全服玩家」的发奖约定**不适用于此处**）；③ 玩家**死亡**（`LivingDeathEvent`，`priority=LOWEST`，晚于保命方的「取消死亡」）/死亡克隆/登出时**双向**清除该立场（作为攻击者与作为目标的记录一并清）；④ **例外**：`PandamanSignItem` 的嘲讽**刻意只对敌对生物生效**（单参 `isHostile(e)` + `!(e instanceof Player)` 守卫，保持既有约定，永不施加给玩家）。
- **统一现状（2026-09-15 复核；2026-09-15 本批 D-B2 后更新计数）**：双版本各 **24 处玩法判据点**已改调该入口，另有 `damage/RailgunBolts#isValidLightningTarget` 委托同一入口，合计 **25 个调用点 / 17 个文件**（两版本行号同构、逐条 `Compare-Object` 零差异）。其中 **23 个调用点**改用带上下文的 `isHostile(viewer, target)` 重载（含本批改口径的 `RailgunBolts#isValidLightningTarget`，viewer = `LightningBolt#getCause()`），仍用单参的 2 处为：`DiceCombatEvents.isBlessingTarget`（口径含「非队友玩家」，无法交给本入口）、`PandamanSignItem` 的嘲讽（**刻意**只对敌对生物生效，见上条）。判据同族的既有分支（`DiceCombatEvents.isBlessingTarget` 的「Boss 或正在追打该玩家的生物」、`NancyLuSignItem#clearNearbyMobTargets` 的 `mob.getTarget() == player`、`BossEntityUtil.isBossEntity`）**不是** `Enemy` 判定，保持原样。



**伤害类型**:`astral_dice:true_damage`(`<子项目>/src/main/resources/data/astral_dice/damage_type/true_damage.json`,两版本同源),并登记进 `data/minecraft/tags/damage_type/bypasses_armor.json`(标签按合并式追加,只列本模组条目即可)⇒ 结算时**完全跳过护甲值与盔甲韧性**;保护附魔与抗性提升**仍生效**(分别由 `bypasses_enchantments`/`bypasses_resistance` 标签控制,不在「无视防御力(护甲值/盔甲韧性)」的口径内)。今后新增「无视防御力」类伤害一律复用该类型,禁止让这类伤害继续吃护甲。

**真伤穿透无敌帧(2026-09-15)**:另有 `data/minecraft/tags/damage_type/bypasses_cooldown.json`(**两版本原版 client-extra 资源 jar 里都没有该标签文件** ⇒ 本模组的文件即其定义),把 `astral_dice:true_damage` 登记进去 ⇒ 真伤**穿透无敌帧**(`LivingEntity#hurt` 的 `invulnerableTime > 10` 早退分支不再对真伤生效;两版本同一处判据,1.21.1 `LivingEntity.java:1190` / 1.20.1 `:1131`)。**副作用如实写明**:电磁炮雷击走**同一个** `astral_dice:true_damage` 类型,因此同样受益;`astral_dice:dice_damage`(反击注入与绯红骰/王之力自伤)**不在其中**,行为不变。

**活体书页命中伤害类型(2026-09-25 新增)**:`astral_dice:card_spell`(`<子项目>/src/main/resources/data/astral_dice/damage_type/card_spell.json`,两版本同源,内容与 `true_damage` 同构),同样登记进 `bypasses_armor.json` ⇒ 基础值**完全跳过护甲值与盔甲韧性**;它**不**登记进 `bypasses_cooldown`(单张书页只会命中一次,不需要穿透无敌帧)。**为什么必须是独立类型**:该伤害要**登记为法伤**(`SpellDamageRegistry` 的第 4 条 matcher `source.is(ModDamageTypes.CARD_SPELL)`)才能让 `event/DamageEffectCardHandler` 原样跑完整法伤修饰器链;复用 `true_damage` 不会被判为法伤,复用 `dice_damage` 则会把一次卡牌命中变成一次骰战发起。伤害源形状 = `cardSpell(level, causing)`(直接伤害实体为空、击杀归属玩家,与 `trueDamage(level, causing)` 同形状)。

**保命一律按「吸收之后」判定(2026-09-15)**:安全气囊的致命判定按**吸收(黄心)之后**的伤害(1.21.1 手动换算 `damage - getAbsorptionAmount()`(下限 0);1.20.1 的 `LivingDamageEvent` 天然在吸收之后,不做换算);**虚空(`minecraft:out_of_world`)不受气囊保护**(伤害阶段与死亡兜底两处都只按该伤害类型整段跳过),`/kill`(`generic_kill`)仍可被气囊救回(不查 `bypasses_invulnerability` 标签,否则会把 `/kill` 一同变成不可救)。末影骰子保命改为**只清除有害效果**(`MobEffectCategory.HARMFUL`)**外加随标记一同施加的 `GLOWING`**(显式白名单 = HARMFUL ∪ {GLOWING},绝不用 `removeAllEffects()`;两版本同步)而非清空全部,增益一律保留。**为什么必须补 `GLOWING`(2026-09-15 本批 C4)**:`MarkManager.apply` 在施加 `MARKED`(HARMFUL)的同时经 `EffectTimerGuard.apply` 施加同寿命的 `GLOWING`(NEUTRAL,不属 HARMFUL)⇒ 只清 HARMFUL 会留下**残留发光轮廓**,与守卫 `ModEffectEvents.onModEffectRemovalPrevented`「标记仍存在时发光不得被单独清除」(发光与标记同寿命)的口径不一致。**口径收窄**:仅当玩家**确实带着本模组 `MARKED`** 时才连带移除 `GLOWING`——`GLOWING` 另有原版来源(光灵箭 `spectral_arrow`、`/effect`、其它模组),无标记时不得顺手清掉别人的发光;有标记时该效果实例即"标记随身的那份",随标记一起走。

**Java 入口**(`damage/ModDamageTypes`):`trueDamage(Level)` = 无来源实体(用于电磁炮雷击:与旧的原版闪电一致,不算玩家攻击、无击杀归属);`trueDamage(Level, Entity causing)` = 直接伤害实体为空 + 击杀归属 causing(用于大当家溅射,与旧 `explosion(null, player)` 同形状)。**直接伤害实体必须为空**:`DiceCombatEvents#onLivingDamagePre` 以 `source.getDirectEntity() instanceof Player` 作为骰战入口判据,直接实体一旦是玩家,溅射/雷击就会被当成玩家攻击重走骰战并递归触发赐福。

**死亡消息**:自定义伤害类型必须自带 lang 键,否则聊天栏会显示原始 key。`DamageSource#getLocalizedDeathMessage`(已核对 1.21.1 `:78-93` / 1.20.1 `:71-91`)的三种形态对应三个键:`death.attack.<msgId>`(1 参=受害者;受害者有 killer credit 时走 `.player`)、`death.attack.<msgId>.player`(2 参)、`death.attack.<msgId>.item`(3 参,击杀者主手为命名物品)。**base 键只写 `%1$s`**,使 1 参/2 参两种调用都安全;四个 lang(中英 × 双版本)必须齐全。

**电磁炮雷击为什么要 Mixin**:原版闪电自身不带伤害——`LightningBolt#tick` 只挑目标(箱体 ±3 格、垂直 +6+3、谓词 `Entity::isAlive`;`onEntityStruckByLightning` 事件可 veto)并把每个目标交给 `Entity#thunderHit`;真正的结算在 `Entity#thunderHit` 内用 `damageSources().lightningBolt()`,且火焰在 hurt **之前无条件**设置。`minecraft:lightning_bolt` **不在** `bypasses_armor` 里(已从本机 client-extra 资源 jar 逐条核对)⇒ 原版雷击会被护甲与韧性减免,这就是电磁炮此前"不是真伤"的原因。故 `mixin/EntityThunderHitMixin`(`@Inject(method = "thunderHit(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LightningBolt;)V", at = HEAD, cancellable = true)`)只对**本模组电磁炮降下的闪电**接管:复刻原版点火两行(1.21.1 `igniteForSeconds(8.0F)` / 1.20.1 `setSecondsOnFire(8)`,版本差异仅此),把伤害换成 `trueDamage(level)` 后 `ci.cancel()`。

- **标记机制**:`damage/RailgunBolts`(弱引用 `Set<LightningBolt>`,仅服务端线程),`RailgunChipItem#strike` 在 `addFreshEntity` 前 `mark`。**禁止**改成"无条件替换伤害源"——那会连带改掉雷暴、引雷三叉戟等一切原版/其它模组闪电。
- **保持不变的部分**:点火(`spawnFire`,按原版保留,受 `doFireTick` 控制)、闪电苦力怕(`Creeper#thunderHit` 在 `super` 之后无条件置充能)、盔甲架/悬挂物(空覆写,不受伤)、避雷针/铜块氧化等世界行为。
- **目标筛选与事件层(2026-09-14 用户裁决,必须遵守)**:命中范围**不在这里拦,而是在 `LightningBolt#tick` 的目标筛选处拦** —— `mixin/LightningBoltStrikeScopeMixin`(`@ModifyArg` 作用于 `tick` 内**第二个** `Level#getEntities(Entity, AABB, Predicate)` 调用,`ordinal = 1` / `index = 2`,已用 `javap -c` 从两版本合并 jar 逐条核对):把原谓词 `Entity::isAlive` 与白名单**与**起来,非敌对目标**在进入循环之前**就被剔除 —— 既不受伤、也不被转化、也不触发 `onEntityStruckByLightning`、也不进入 `CHANNELED_LIGHTNING`("Very Very Frightening")成就的实体列表(成就吃的是同一份 list)。**为什么必须上移**:海龟/村民/猪/蘑菇牛**覆写了 `thunderHit` 且不调用 `super`**(海龟自算 `Float.MAX_VALUE` 秒杀、村民转女巫、猪转僵尸猪灵、蘑菇牛换肤),只在 `Entity#thunderHit` 里 `ci.cancel()` 对它们完全无效,事件与成就也不受实体白名单约束(2026-09-14 独立验证发现并修复)。`EntityThunderHitMixin` 里的同口径检查**保留为纵深防御**。
- **风险等级**:`EntityThunderHitMixin` 与 `LightningBoltStrikeScopeMixin` 都登记在**主配置** `astral_dice.mixins.json`(`required: true` + `injectors.defaultRequire: 1`)⇒ 上游若重命名/改签名 `thunderHit`、或改动 `tick` 内 `getEntities` 的调用顺序(使 `ordinal = 1` 指向别处),**启动即崩**。这是刻意取舍:真伤与命中范围属玩法语义,静默失效会造成"看起来没改"的平衡事故;升级 MC/NeoForge/Forge 版本后必须冷启动确认日志无 `Mixin apply failed`。

**法伤(伤害效果牌)一律真伤(2026-09-14 用户裁决)**:`event/DamageEffectCardHandler` 不再把加成并进本次伤害事件(`setNewDamage/setAmount(旧值 + bonus)`——那样会连同武器伤害一起吃护甲值/盔甲韧性/保护附魔),改为对加成单独 `target.hurt(ModDamageTypes.trueDamage(level, player), bonus)`,并用 `ThreadLocal` 重入闸门`APPLYING_TRUE_BONUS` 防止自递归;**基础武器伤害仍走原规则**(只有效果牌那部分穿甲)。效果牌的两处范围波及伤害(定向爆破、电击手套)同样改用 `trueDamage`。

**大当家溅射对主目标的缺陷(2026-09-14 实机发现并修复)**:原版 `LivingEntity#hurt` 是「先写 `lastHurt`/`invulnerableTime`(用**骰战结算前**的武器伤害)→ 再进 `actuallyHurt` → 触发本模组的`LivingDamageEvent.Pre`」;溅射正在该事件内,于是"无敌帧内不更低的伤害被丢弃"规则把**主目标**那份溅射整段吞掉(实测主靶只掉近战那 3 点、5 点溅射凭空消失)。修法:只对主目标临时清零 `Entity#invulnerableTime` 再 `hurt`,结算后还原(`DiceCombatEvents` 两侧同改)。副目标不受影响(它们没有在飞的伤害)。

**电磁炮雷击命中范围(2026-09-14 用户裁决;2026-09-15 本批 D-B2 对齐双参口径)**:`RailgunBolts.isValidLightningTarget(Entity, LightningBolt)` 放行的只有 —— ① 统一入口 **带上下文** 的 `HostileTargets.isHostile(viewer, target)` 认定的**敌对目标**(敌对生物 `Enemy`,或已被激怒的中立生物 `NeutralMob#isAngry()`,如被攻击后的末影人;**以及「非同队伍、且曾主动攻击过 viewer 的玩家」**),其中 **`viewer` = 落雷来源玩家 = `bolt.getCause()`**(由 `RailgunChipItem#strike` 的 `bolt.setCause(cause)` 写入,故两版本都拿得到来源玩家;无闪电实例的退化重载 `isValidLightningTarget(Entity)` 的 viewer 为 null ⇒ 玩家不计入敌对)——电磁炮落雷由此与其它 AOE 的双参敌对口径完全一致,**会打你视为敌对的玩家**;**且** ② **不是雷击施放者自己拥有的宠物**(`OwnableEntity#getOwnerUUID()` 等于 `bolt.getCause().getUUID()`)——被激怒的已驯服宠物(如自己养的狼)属"友方宠物",永不挨自己的雷击;其它玩家拥有的宠物不在此列。其余实体(攻击者自己、平静的中立动物、盔甲架等)**在 `LightningBolt#tick` 的目标筛选处就被剔除**:不受伤、不转化、不触发 `onEntityStruckByLightning`、不发成就;方块着火按原版保留。实机取证:`/astralprobe railgunfriendly` 读数 `self=0:enemy=1:neutral=0:friendly=0`。

## 管理员调试命令 `/astralparty`（Admin Debug Commands）— 必须遵守

**定位**:本命令是**正式管理员功能**,随 jar 发布(不加 dev/测试专用开关、不加配置开关、不做「仅调试模式注册」),**只在服务端**注册与执行(`RegisterCommandsEvent` 的服务端命令派发器 + `CommandSourceStack` 的 `ServerPlayer` 路径,不存在仅客户端生效的分支);入口唯一 = `requires(src -> src.hasPermission(2))`——根字面量与每个子命令都带该门槛,**禁止**任何降低门槛的旁路(配置项、非 OP 别名、客户端旁路)。实现位于 `command/AstralPartyCommand`(两子项目各一份,API 差异见本节末)。

| 子命令 | 语义 | 作用范围 |
|---|---|---|
| `cleareffect [目标]` | 清除**全部本模组效果**(`ModEffects.ALL`,33 个) | 排障兜底(含立牌主动效果) |
| `clearcardeffect [目标]` | 只清**效果牌施加的效果**(`EffectCardPeriod.effectPendingEffects()`,9 个) | 出牌锁第 ③ 条的**最小**解锁手段 |
| `resetcardlock [目标]` | 重置出牌锁的 **①②** | **仅效果牌轮次**,不含立牌锁定态 |
| `resetcardcolddown [目标]` / `resetcardcooldown [目标]` | `resetcardlock` 的**别名**(原话拼写 + 拼写正确版) | 与主字面量**共用同一实现**,禁止写第二份逻辑 |
| `dump [目标]` | **只读**转储本模组自身的关键状态为 `APDUMP\|<组>\|<键>=<值>` 行 | 排障与自动化断言取数;**不改变任何状态** |

目标参数可选(`@a`/玩家名),省略时作用于执行者自己;执行者不是玩家且未给参数、或选择器解析不出玩家时返回可读失败文案而**不抛异常**。反馈文案一律走 lang key(`command.astral_dice.astralparty.*`,经 `Component.translatable`),禁止内联文本。

**出牌锁三条判据与分工**(判据见 `EffectCardPeriod#isBlocked`):①出牌数达当轮上限(`isBurstFull`)、②出牌冷却进行中(`isCooldownActive`)、③**效果待定**(`isEffectPending`)。

- `resetcardlock` **只解 ①②** —— 调 `EffectCardPeriod.forceResetRound(player)`:出牌数、出牌冷却、全部「每轮一次」标记(含电击手套本周期武装)一并归零。**明确不做 ③**:不清 `EFFECT_PENDING_SOURCES` 对应的效果、**不清立牌锁定态 `sign_active_lock_*`**、**不清待命等待器 `sign_ready_*`**、不补 `onRoundFullyReset` 回调。该入口对「未处于任何轮次状态」的玩家**幂等安全**(全是无条件写默认值,不读旧值)。
- ③ **只能靠清掉效果实例**解除(`forceResetRound` 覆盖不到),由 `clearcardeffect` 负责。
- `cleareffect` 与 `clearcardeffect` 的区别:前者 = **全部本模组效果**(含立牌主动效果 `misaki_burst`/`papara_bite`/`nancy_lu_hack`/`weak_mark` 等);后者 = **仅效果牌留下的那批效果**(= 出牌锁第 ③ 条的权威来源),不碰立牌主动效果,也不碰效果牌顺带施加的原版 rider(迅捷/中毒/生命恢复/抗性提升——它们不参与出牌锁且来源众多)。

**`dump [目标]`(只读转储,机器格式)**:语法 `/astralparty dump [目标]`,权限门槛与其它子命令**完全相同**(`requires(src -> src.hasPermission(2))`,服务端注册/执行);目标参数可选(`@a`/玩家名),省略时作用于执行者自己,执行者不是玩家且未给参数时 `sendFailure` 可读文案、**不抛异常**。**本命令不改变任何状态**——只经 getter 与 `is*` 判定取值(含判定入口方法),不写任何附件/组件/效果,也不提供任何"设置/给予"入口;同理**不得越界**:只 dump 本模组自己的状态(效果清单只遍历 `ModEffects.ALL`,非本模组效果一行都不输出)。

**为什么存在**:测试流程的断言 100% 是对 `run/<版本>/logs/latest.log` 的正则匹配,PowerShell 侧没有读游戏内状态的能力;`dump` 用**一条命令**把关键状态倾倒出来,以替换那批"纯读探针"代码。**所以输出格式的稳定性就是本命令的核心价值**(不是给人看的漂亮输出)——改形状 = 破坏既有断言,新增字段只能**追加**、不得改名/改序。

- **输出格式**:每个目标玩家一段,段首一行 `APDUMP|HEAD|<玩家名>=<UUID>`;其后每行固定为 `APDUMP|<组>|<键>=<值>`,分隔符固定为 `|`。组固定为 `LOCKRAW`(出牌锁原始值:`game_time`、`effect_card_cooldown_end`、`effect_card_play_count`、`effect_card_bonus_plays`、`living_page_cycle_bonus`、`max_allowed`、`remaining_block_ticks`)、`LOCKDERIVED`(`is_burst_full`/`is_cooldown_active`/`is_effect_pending`/`is_blocked`)、`SIGN`(`sign_active_lock_sign`/`sign_active_lock_end`/`sign_active_reduction_pool`/`sign_active_lock_grace_end`/`sign_active_lock_played`/`sign_active_cooldown_end`/`sign_active_max_cooldown`/`sign_ready_type`/`sign_ready_expire` + 派生 `is_sign_active_locked`)、`EFFECTS`(每行 `effect=<注册id>|amplifier=<n>|duration=<n>`)、`PENDING`(每行 `source=<稳定标识>|effect=<注册id|none>|is_active=<bool>|remaining=<n>`)。
- **约定**:数值一律十进制、tick 用 long、布尔用 `true/false`;**`APDUMP` 行内不得出现颜色码(§)**——颜色码会破坏正则;`APDUMP` 正文**不走 lang key**(它是机器格式),只有紧随其后的人类可读摘要走 lang key(`command.astral_dice.astralparty.dump.summary`)且**不得**带 `APDUMP|` 前缀。同时走**两条通道**:`source.sendSuccess(...)`(玩家 chat)与 `LOGGER.info(...)`(保证进 `latest.log`,不依赖 chat 渲染)。多玩家按选择器顺序输出,段内行序**固定**(键序写死;效果组按注册 id 序数排序;来源组按注册顺序;禁止依赖任何 `HashMap` 迭代序)。
- **原始值优先于派生值**:断言一律锚定 `LOCKRAW`/`SIGN`/`EFFECTS`/`PENDING` 的原始值。`LOCKDERIVED` 组与 `SIGN` 组的 `is_sign_active_locked` 是**判定入口字段**(它们的布尔值由多条原始值实时推导),行尾固定带 `|assert=forbidden` 标记——**禁止作为断言落点**,只作人工参考。

**能力边界(红线,违反即不合格)**:命令只做「**重置 / 清除 / 只读转储本模组自身状态**」这一件事。**不得**新增 —— ① 任何资源给予/设置(物品、卡牌、筹码、星币、星光、治愈、剑气层数、最大生命…);② 任何「把任意数值/状态设成任意值」的通用写入(如 `seteffect <效果> <时长>`、`setcooldown <tick>`、`setplaycount <n>`);③ 跨玩家扩散(默认只作用于执行者,给了选择器才作用于该玩家;**禁止**隐式作用于全体或影响选择器之外的人);④ 被任何游戏内正常玩法路径调用,或为「方便测试」在产品逻辑里加调试分支/后门;⑤ 去动原版或其它模组的玩家状态(如光灵箭施加的 `GLOWING`、经验、背包)。判定不清时的默认答案是**不做**。

**实现纪律(三条)**:

1. **效果移除只走 `ModEffectRemoval` 内部通道**:`ModEffectEvents.onModEffectRemovalPrevented` 会拦截并取消**全部 `astral_dice:` 命名空间效果**的外部移除(牛奶/`/effect clear`),直接 `removeEffect` 会让命令**完全无效**。走内部通道另有两个附带收益(**不要**另行重写):`onEffectTimerForget` 遗忘 `effect_timer_ends`(否则 `EffectTimerGuard.tick` 的 `inst == null` 分支会把效果**原样施加回来**)、`InvestigationEventUtil.onUndercoverRemoved` 清 `undercover_source`。
2. **只清与所清效果「同生共死」的耦合状态**:`fate_active_until`(效果只是显示,功能由附件驱动)、`weak_mark_source`(印记来源归属,唯一读取点被 `hasEffect` 守卫)、`sign_ready_type`/`sign_ready_expire`(`*_ready` 提示效果是待命窗口的显示,只清效果会让按主动键被 `isSkillWaiting` **静默拒绝**且无任何提示)。**不动**玩家资源与进度计数器(`healing_points`/`healing_timer_end`、`investigation_stage`、`cursed_sword_*`、`empower_decay_at`)与立牌锁定态 `sign_active_lock_*`(后者由玩家级 tick `tickSignActiveLock` 自动迁移为冷却:无空档、无残留)。**不得**去清原版效果(如 `marked` 的伴生 `GLOWING`)——已知后果:清掉 `marked` 后发光按自己的计时自然结束,期间可能短暂出现「发光但无标记」;这是该边界下的既定取舍,不要"顺手修"。
3. **只读入口**:`ModEffects.ALL`(派生自 `DeferredRegister#getEntries()` 的活视图,新增效果自动纳入)、`EffectCardPeriod.effectPendingEffects()`(由效果待定注册表去重派生)与 `EffectCardPeriod.effectPendingSources()`/`effectPendingSourceIds()`(同一注册表的逐条只读视图 + 稳定来源标识,供 `dump` 输出每条来源自己的 `isActive`);两子项目保持一致,**禁止**改成手写清单(必然漂移)。

**两版本 API 差异(已用本机 moddev 反编译源码核对)**:①「全部效果」集合的元素类型 —— 1.21.1 `DeferredHolder<MobEffect, ? extends MobEffect>`(本身即 `Holder<MobEffect>`)/ 1.20.1 `RegistryObject<MobEffect>`(需 `.get()`);② 移除签名 —— 1.21.1 `LivingEntity#removeEffect(Holder<MobEffect>)` / 1.20.1 `LivingEntity#removeEffect(MobEffect)`;③ 命令事件 —— 1.21.1 `net.neoforged.neoforge.event.RegisterCommandsEvent`(GAME bus)/ 1.20.1 `net.minecraftforge.event.RegisterCommandsEvent`(FORGE bus);④ **效果待定来源的 `effect()` 返回类型**(`dump` 的 E 组用)—— 1.21.1 `Holder<MobEffect>`(注册 id 经 `Holder#unwrapKey()`) / 1.20.1 `MobEffect`(注册 id 经 `BuiltInRegistries.MOB_EFFECT.getKey(effect)`);⑤ **`getEffect`/`hasEffect` 的入参**随之不同(1.21.1 收 `Holder<MobEffect>`,1.20.1 收 `MobEffect`);⑥ `LOGGER` 在 `AstralDiceMod` 里是 **private**,两个子项目的命令类各自声明 `LoggerFactory.getLogger(AstralPartyCommand.class)`。命令树、权限门槛、参数形态、文案与 **`APDUMP` 行的组名/键名/行序**两版本逐字一致。**新增子命令前必须重读本节的能力边界。**

## 版本历史与发布记录

历史功能/平衡性/BUG 修复记录见 `CHANGELOG_ZH.md`(中文)与 `CHANGELOG.md`(英文),两文件按版本号一一对应、条目数一致;配方细节以 `datagen/ModRecipeProvider.java` 实际生成内容为准。

## 待办交接：fengshui-sign 批（2026-09-20，更新：测试资产已清零）

> **来源**：本批由 agent team `fengshui-sign` 实施；2026-09-20 用户指令终止剩余任务并解散团队（未完成项移交本文件），同日指令「完全删除所有 agent team」⇒ 团队与全部归档状态已删除。
> **工作树**：产品改动全部在 `F:\MCProject\astral_dice_multiloader-next`（分支 `multi-dev-next`）；**已于 2026-09-20 按用户指令「提交所有更改」本地 commit（未 push）**。⚠️ 提交时本批**仍无游戏内证据**（测试资产已清零、探针与用例全删）。
> **⚠️ 测试资产已清零（2026-09-20 用户指令）**：本批的 4 条用例（`ZHAO-SIGN-*` / `HUO-CURSE-1.21.1` / `ZHAO-BLESSING-1.21.1`）与全部探针改动已随全仓测试项一并删除作废；历史 run1–run3 与冻结件已删除且结论全部作废。只剩产品与文档待办。

### A. 产品与文档待办（按优先级）

| # | 事项 | 状态 | 入口 / 判据 |
|---|---|---|---|
| A2 | **移除符卡-祸「禁止丢弃」（含 1.20.1 镜像）** | **✅ 已完成（2026-09-20 用户指令：整套清空）** | 三通道 + 潜影盒/收纳袋限制 + 两个 lang 键 + 手册半句 + tooltip 行 + 注释全部删除；落点与判据见 §C |
| A5 | 文档同步 | 未做 | 规格 §3.3/§14.1、`TESTING-SPEC.md` 探针/通道描述、两份 CHANGELOG（裁决 C 玩家侧条目 + 弃置移除条目） |
| A6 | 本批提交（**禁止 `git push`**，除用户明确要求） | **✅ 已完成（2026-09-20 用户指令「提交所有更改」）** | 本地 commit 已落地、未 push；⚠️ 本批仍**没有**游戏内证据（探针与用例已全删），重建用例后须按 §13.2 复跑 |

### B. 用户待决（3 项，**不要自行拍板**）

1. **裁决 C 的纳入清单是否含活体书页**：当前两线实现为 `fuCycle>0 || livingPageCycle>0 ⇒ return`。若只限符卡-福，改 `EffectCardPeriod.tick` 一行判据 + CHANGELOG 收窄。
2. ~~**「禁止丢弃」的移除范围**~~ **已执行（2026-09-20 用户指令）：整套清空（含潜影盒/收纳袋限制）** —— `onDroppedByPlayer` 拒绝、`ItemTossEvent` 兜底退还 + `refundToContainerSlot`、`canFitInsideContainerItems`（1.21.1 ×2 / 1.20.1 ×1）全部删除。本项关闭。
3. **H5 口径**（重建用例时适用）：若修复后伤害读数仍低于 5，断言基准改「伤害事件额」还是维持「HP 差值」。

### C. A2 的落点清单（已侦察，含「无 mixin」结论）

> ✅ **2026-09-20 用户指令：以下落点全部删除，A2 完成** —— 本清单转为**已执行的删除记录**；B2（潜影盒/收纳袋限制）一并删除。⚠️ 早前此处登记的「用户裁决：保留、不移除」与本次指令相反，经复核该登记**无来源可考**（判定为过期/失实记录），已按本次指令覆盖。
> 执行前复核（实测）：三通道两线**均实存**（1.21.1 `HuoCardItem.java:260-268` / `:277-285` / `:296-323`；1.20.1 `:260-269` / `:331-334` / `:280-307`），lang 4 文件相关键齐全 ⇒ 「是否已实施」= **是**，据此按指令删除。

- **产品（两线，已删）**：`item/card/HuoCardItem.java` 的 `#onDroppedByPlayer`、`#onItemToss` + `#refundToContainerSlot`、`canFitInsideContainerItems`（1.21.1 两个重载 / 1.20.1 一个），以及仅供其注册用的 `@EventBusSubscriber` / `@Mod.EventBusSubscriber` 类注解、随之失效的 import 与 `LOGGER` 字段。
- **⚠️ 无 mixin**：三线的 mixin 配置与源码里不存在实现该限制的 mixin ⇒ 「mixin 一并删除」不适用，勿再重复检索。
- **文案（已删/已改）**：`msg.astral_dice.huo_card_no_drop` 与 `tooltip.astral_dice.huo_card_no_drop` 两个键（zh_cn + en_us × 两线 = 4 文件全部删除，`ModTooltipHandler` 两处 `tooltip.add` 同时移除）；`astral_dice.guide.entry.huo_card.2`（中英）改写为「存入容器后不再计入厄运与周期伤害」；`ExclusiveCardUtil` / `RandomCardHandler` / `ModItems` 注释里的「禁止丢弃」表述已清理；两份 CHANGELOG 的对应句子同批改写。
- **测试流程（已清零，待用户重新授权后重建）**：`_TOSS` 类断言应写成**丢弃成功的正向证明**（手持 -1、地面 +1）；旧的「丢弃被拒且牌未丢失」口径与「`canFitInsideContainerItems` 拒绝收纳」断言**全部作废**。

### D. 实测状态（2026-09-20 已作废）

原「run3 = 254 PASS / 35 FAIL」等记录与冻结件已删除并作废，不得引用。当前唯一有效机器证据 = 两线 `mt_build` 均 `MT_BUILD: OK`（含裁决 C 的 Java 改动）；游戏内行为未验证。

### E. 已登记但未处置（低优先）

- 【2026-09-20 复核（S4）：未复现，登记过期】历史登记「`mt.ps1` 对 `MT_LAUNCH: ` 的大小写敏感判定会把 launch 被挡误落成 rc=2」——当前两树 `mt_launch.ps1` 终态输出均为大写，`mt.ps1:232` 的 marker 可正常匹配 ⇒ 本条不再成立；保留作「登记须复核」样例。
- `scripts/verify/verify_probe_case_args.ps1`（探针↔用例参数离线校验）：输入对象已随清零删除，当前无输入可用；待用户裁决删除或保留待重建。
- 文档内 55 处未来日期（09-25×38 / 09-26×17）待裁决；本批口径已统一为 2026-09-19/20。
- `neoforge-26.1.2` 线冻结：本批功能与裁决 C 待按 `docs/compat-26.1.2-neoforge.md` 迁移。⚠️ **迁移时不得带上「禁止丢弃」（2026-09-20 已删除）**：即 26.1.2 侧的 `HuoCardItem` 只需 `tick` 镜像 + 周期伤害与选择器发牌路径，**不要**移植 `onDroppedByPlayer` / `onItemToss` / `canFitInsideContainerItems` / `msg.astral_dice.huo_card_no_drop` / `tooltip.astral_dice.huo_card_no_drop`（两线已删，26.1.2 从一开始就不该有）。
- 用户已知边界（接受、仅记录）：裁决 C 下「弃打的一轮」不再自动收尾（+1 保留到被用掉 / 死亡 / `forceResetRound`）；停滞期间 `onRoundFullyReset` 不触发 ⇒ 忍者立牌主动冷却不起步。

## 待办交接：teru-sign 批（教主立牌 / 降神 · 狐光，2026-09-27）

> **工作树**：`F:\MCProject\astral_dice_multiloader-next`（分支 `multi-dev-next`）。规格文档 = `docs/features/teru-sign-spec.md`（id 表 / 注册点 / 状态机 / 防刷守卫 / 生命周期 / 26.1.2 待迁移 / 验收）。
> **范围**：发布线双版本（`neoforge-1.21.1` + `forge-1.20.1`）功能对等；`neoforge-26.1.2` **零改动**（只登记待迁移）。

### 关键口径（实现按此，勿再改）

1. **主动「降神」**：`TargetType.PLAYER` + **不实现 `SelfTargetable`** ⇒ 只能选**其它玩家**（排除生物与自身）。锁定目标 `⌊攻击力×0.5⌋` / `⌊防御力×0.5⌋` 给施法者（施法瞬间快照）；狐光攻击基数 `B = ⌊施法前施法者攻击力⌋ + ⌊目标攻击力×0.5⌋`；目标**每攻击一个新目标**消耗 1 层狐光并按 `B + 消耗后剩余层数` 追加**骰战攻击力**（层数 0 ⇒ 不消耗不加成；施法者离线 ⇒ 不加成不消耗）。
2. **结束锚点 = 被指定目标自己的下一次骰神赐福结束**（下降沿状态机，与 `ZhaoSignItem#tickBlessing` 逐字同语义：施加时目标已在赐福 ⇒ `skip=1` 跳过当前这一次）。**生效中拒绝重复施放**：新增门控钩子 `BaseSignItem#canBeginSelectorSession`（缺省 `true`，只有 teru 覆写）⇒ 只提示、不开会话、不进冷却、不发牌、不充能。
3. **真值归属**：降神 7 键**全在目标身上**（含状态机三键与「已攻击目标集」），施法者侧只有「层数 + 目标指针 + 攻击加成镜像缓存」⇒ 施法者死亡/登出**不影响**目标身上的降神，回来后由每 tick `resolveTarget` **自愈重建**；只有「目标效果结束 / 死亡 / 登出 / 重登」才真正移除（`endDescent` 唯一收敛点）。
4. **防刷（2026-09-27 用户指令，两条守卫，缺一不可）**：
   - ① **拾取不计层** —— `TeruSignItem` **刻意不订阅任何拾取事件**（源码里不出现 `ItemEntityPickupEvent` / `EntityItemPickupEvent` / `PlayerEvent.ItemPickupEvent`）⇒「丢弃→捡起」零收益；
   - ② **装备计层按「历史同时装备水位」去重** —— `teru_equip_watermark`（只升不降、跨死亡保留）⇒「插入→卸除→再插入」零收益。**为什么不能用物品级标记**：`CardInventoryMenu#saveToDice` 只把卡牌写成 `AppliedStone(type,uses)` 并销毁物品栈，卸除时由 `loadFromDice` 经 `CardRegistry.typeToItem` **重建全新栈**，标记必被抹掉（见规格 §4.2）。
5. **跨死亡保留**：`teru_huguang_layers` 与 `teru_equip_watermark` 在 1.21.1 为 `.copyOnDeath()`（该线 copyOnDeath 键 3 → **5**），在 1.20.1 已加入 `component/AstralData#onPlayerClone` 死亡白名单（3 → **5**）。
6. **跨维度链接解析必须用** `getServer().getPlayerList().getPlayer(uuid)`，**不得**用 `level.getPlayerByUUID`（只查本维度 ⇒ 跨维度会误判"链接失效"并错误清零）。
7. **1.20.1 每 tick 派发 START+END 两次** ⇒ `TeruSignItem#tick` 必须幂等（下降沿第一次调用即落 `prev=false`，第二次不再消费 `skip`）。
8. **`consumeHuguangForNewTarget` 的自目标防护必须保留**（`if (victim == attacker) return 0`）：显示/快照路径（`DiceCombatModifiers#getDisplayAttackRange` 与 `TeruSignItem#attackPowerOf`）都以 `ctx.target == attacker` 复用同一套攻击修饰器链 ⇒ 少了这一行，「打开卡牌栏看一眼攻击力」或「施法瞬间快照」都会**误消耗 1 层狐光**并把施法者自己写进已攻击目标集。真实骰战链路 `target == player` 直接 return，故实战零影响。
9. **每 tick 调用的代价已收敛**：`resolveTarget` 的"全服扫描自愈"只在「佩戴立牌 或 仍有攻击加成缓存」时发生（否则 N 个玩家 = 每 tick O(N²)）；`teru_prev_blessing` 等镜像写入一律**同值不写**（避免附件脏化）。

### 状态（实现完成，游戏内验证已完成 2026-09-20）

| 项 | 状态 | 判据 |
|---|---|---|
| 1.21.1 实现 + 构建 + datagen | ✅ | `MT_BUILD: OK`；jar 含 3 新类 + 3 贴图 + 手册 + 模型 + 配方 |
| 1.20.1 实现 + 构建 + datagen | ✅ | 同上（平台写法差异见规格 §2.2） |
| 语言键 15 个 × 4 文件 | ✅ | `tools/check_lang_sync.ps1` 退出 0（三线各自「zh_cn ↔ en_us key 完全一致」） |
| 文档 | ✅ | `docs/features/teru-sign-spec.md`、两份 CHANGELOG（各 +1 条，同一分类） |
| **游戏内行为验证** | ✅ **双侧全绿** | 3 条 teru 用例 + 1 条选择器门控回归用例，**两线各 4/4 PASS**（1.21.1：91+53+83+43 断言；1.20.1：91+53+83+43），无 `_ERR`/`_EX`、KubeJS 0 错误、无崩溃报告。命令：`pwsh -File scripts/test/mt.ps1 --version <V> --phase cases --case cases/<用例>.json` |
| 顺带修复（**drive-by**） | ✅ | 本树两线 `build.gradle` 的 `exclude("src/generated/**/.cache")` 改为相对模式 `exclude("**/.cache")`（原写法永不命中 ⇒ 产物 jar 带 3 条 `.cache` 条目、本地与 CI 字节不一致）；修复后两线 `build/libs/*.jar` 的 `.cache` 条目 = **0** |
| 26.1.2 迁移 | ⏳ 登记 | 该线**连 zhao 批次都还没迁**（`entries/signs` 17 vs 18）⇒ 迁移顺序必须「先补 zhao、再补 teru」；不得回移该线专有差异（如 `ItemTags.SPEARS`） |

### 游戏内测试资产（2026-09-20 重建，随用例入库）

| 用例 | 覆盖 | 关键读数（两线一致） |
|---|---|---|
| `TERU-SIGN-<V>` | 注册/冻结数值（含 `signs`+`curios:stand` 标签、两个效果 id、四个常量）、主动只能选其它玩家（自身/生物双拒 + 真实按键路径 debug 行）、对 FakePlayer **真实施法**的 50% 快照与 `+3` 层、生效中不可重复施放（门控 session=0 + 服务端二次校验）、骰神赐福下降沿两分支（`skip` 语义）、效果自检、死亡清场、**自目标防护** | `AP_F_CASTFAKE:cast=1:A_t=20:D_t=12:A_c=6:bonus_atk=10:bonus_def=6:base=16:atk_ok=1:def_ok=1:base_ok=1:layers=3:cache=10:armor=0>12:armor_ok=1`；`AP_GA_GATE:session=0:recast_other=0:layers=7>7`；`AP_G_GUARD:layers=7>7:newt=0>0`；`AP_RD1_R1:caster=0:base=0:armor=0:layers=7`；`AP_RD2_R2:skip=1→0:caster=1`；`AP_RD3_R3:caster=0:layers=5` |
| `TERU-EXTRA-ATTACK-<V>` | 逐层消耗 + 追加攻击力 + 新目标去重 + 0 层不追加（走真实 `Player#attack` ⇒ 骰战攻击修饰器链、目标为 1000 血蜘蛛靶） | `AP_H1_HIT:layers0=5:newt0=0:h0_dmg=208:h0_api=player_attack:h0_layers=5>4:newt=1`；同一靶二次 `h0_dmg=8:h0_layers=4>4`；换靶 `h0_layers=4>3:newt=2`；0 层对照 `h0_dmg=3:h0_layers=0>0:newt=0` |
| `TERU-HUGUANG-ANTIFARM-<V>` | 守卫②（**真实卡牌栏** 插入→卸除→再插入）、守卫①（地面牌被真实拾取但层数不变）、发牌漏斗三点计层（攻击 +2 / 防御 0 / 非卡牌 0 / 背包满走掉落分支 0）、**真死+重生后层数与水位保留** | `AP_E1_EQ:wm=->medium=1:layers=0>1:dl=1`；`AP_E2_EQ:wm=medium=1>medium=1:layers=1>1:dl=0`；`AP_E3_EQ:cards_after=…x1:inv=1>0:wm=medium=1>medium=1:layers=1>1:dl=0`（关键：真的插进去了却一层不加）；`AP_DR_DROP:added=1:ground=1` → `AP_RB5_RB5:inv_atk=2:ground_atk=0:layers=1`；`AP_C4_CARD:filled=36:inv=0>0:ground=1:dl=0`；`AP_RB7_RD:layers=1:wm=medium=1`（重生后） |
| `LULU-SIGN-<V>`（回归） | `BaseSignItem#canBeginSelectorSession` 新增钩子后**其它选择器立牌行为不变**（lulu 的 `allowSelf=true` 自选路径 + 范围能力仍全绿） | 该用例原有 43 断言全 PASS |

> **本轮新增的 KubeJS 环境坑（写探针必看，三条线通用）**：`scripts/test/resources/kubejs/**` 跑在 KubeJS Rhino 上，以下**原版方法不可见**（实测 `TypeError: Cannot find function …`），写脚手架时必须绕开或分层兜底：
> 1. `ServerPlayer#getUUID` → 用本仓既有的容错取值器 `playerUuid(p)`（退到 KubeJS 实体包装的 `p.uuid`）；
> 2. `ServerPlayer#closeContainer` → 它才负责「`containerMenu.removed(player)`（卡牌栏 `saveToDice` 挂在这里）+ 发 `ClientboundContainerClosePacket`」；漏了后者客户端会**一直停在卡牌栏界面**，后续注入的聊天命令全被 GUI 吃掉（**整条用例链一条读数都没有**）。探针 `teruCloseMenu` 手动补齐：`removed` + 按真实 `containerId` 发包 + 复位 `containerMenu`；
> 3. `ItemEntity#setPickupDelay` → 必须**单独 try**（与 `addFreshEntity` 同 try 会让异常跳过添加 ⇒ 实体压根没进世界，读数 `spawn=1` 但永远捡不到）；
> 4. `ItemStack#getOrDefault`（1.20.1 侧）→ 取不到组件就传 `null`（生产方法内部按 `WeaponEnhancement.EMPTY` 处理）；
> 5. `EntityType#getDescriptionId/_holder`、`Item#getClass`、`AttributeInstance#getModifier(UUID)`（1.20.1）同样不可见 ⇒ **不要用 `typeIdOf()`（`getKey(getType())`）判实体类型**：`BuiltInRegistries.ENTITY_TYPE` 是 DefaultedRegistry，未知/反查失败一律回落 `minecraft:pig`（本批实跑把蜘蛛报成猪；可靠判据是 `mob instanceof Spider` 与 `type_req`）。修饰器读数同理：判据用**属性真值**（`p.getArmorValue()`）而不是修饰器对象。
> 6. `Java.loadClass("X").cast(obj)` 不可用（属性查找落在被表示类的静态成员上）⇒ 直接传 `p.level`（Rhino 按运行时类型解析）。
> 7. 死亡相位：`/kill @s` 后客户端会停在死亡界面并把下一次注入当「重生」点击（`GUIDE-BOOK` 用例的老办法）⇒ 新用例统一 `/gamerule doImmediateRespawn true`，死亡后紧接的读数才可信。

---

## 待办交接：新立牌/卡牌批（nardis · mamushi · sherry · hanna + 撕咬 / 龙之咆哮）— 2026-09-27（**只纪录，未动任何代码**）

用户要求（原话）：「提前计划下一步新增内容（只纪录，不动代码）：立牌：绿洲女王立牌（nardis，稀有）、蛟龙立牌（mamushi，传奇）、怪力侦探立牌（sherry，史诗）、人偶师立牌（hanna，稀有）；卡牌：撕咬（战斗牌、蛟龙专属）、龙之咆哮（战斗牌、蛟龙专属）」。

> 本段只是**实施前的规划与本机侦察结论**（2026-09-27 记录）：清单、命名/注册触点、待裁决项、顺序与风险。**尚未新增任何物品/效果/配方/lang/测试资产**；待裁决项定稿后才动代码。本批属**玩家可见新内容** ⇒ 实施时两份 CHANGELOG 必须同步补条目（现在**不**写，避免未落地内容进日志）。

### 0. 清单（7 件）与已定/待定字段

| # | 中文名 | 拟定注册 id | 类型 | 品质（用户给定） | 物品层 `Rarity`（既有映射，冻结） | 贴图（仓库根 `images/` 已有） |
|---|---|---|---|---|---|---|
| 1 | 绿洲女王立牌 nardis | `nardis_sign` | 立牌 | 稀有 | `Rarity.RARE` | `绿洲女王立牌.png` ✅ |
| 2 | 蛟龙立牌 mamushi | `mamushi_sign` | 立牌 | 传奇 | `Rarity.UNCOMMON`（**本仓「传奇」用的就是 `UNCOMMON`**，见品质表） | `蛟龙立牌.png` ✅ |
| 3 | 怪力侦探立牌 sherry | `sherry_sign` | 立牌 | 史诗 | `Rarity.EPIC` | `怪力侦探立牌.png` ✅ |
| 4 | 人偶师立牌 hanna | `hanna_sign` | 立牌 | 稀有 | `Rarity.RARE` | `人偶师立牌.png` ✅（另有 `人偶完成.png` / `人偶制作.png`，用途待裁决，见 §3） |
| 5 | 撕咬 | `attack_card_bite` | **战斗牌**·蛟龙专属 | 待定 | 白=`(无)` / 蓝=`RARE` / 紫=`EPIC` / 金=`UNCOMMON` ⇒ **待裁决** | `撕咬.png` ✅ |
| 6 | 龙之咆哮 | `attack_card_dragon_roar` | **战斗牌**·蛟龙专属 | 待定 | 同上 | `龙之咆哮.png` ✅ |

**配方档（本仓规则 = 配方决定品质；同品质存在多个档位 ⇒ 逐件待裁决）**：稀有(RARE) 可选 `基础骰子`（同 经商/扫地机/看板娘/肉弹战车）或 `黄金骰子(无星盘)`（同 史莱姆/上班族）；史诗(EPIC) 可选 `基础骰子`（游戏大师先例）/`黄金骰子+星盘`（大侦探/吸血鬼）/`钻石骰子±星盘`（忍者/占星师/骇客/枪匠）；传奇(UNCOMMON) 可选 `钻石骰子+黄金星盘`（调查员/护法/大当家/风水师）或 `下界合金骰子+黄金星盘`（秘密侦探）。

### 1. 命名/ID 契约（照既有惯例；用户未另行指定前不得改名）

- 立牌类名 = 角色罗马音 + `SignItem`（既有 `ZhaoSignItem`/`TeruSignItem`/`LuluSignItem`… 同形）⇒ `NardisSignItem` / `MamushiSignItem` / `SherrySignItem` / `HannaSignItem`；物品 id = `<角色>_sign`。
- 战斗牌**不新建物品类**：现有 10 张战斗牌全部是通用 `CardItem` + `typeId` 字符串（`ModItems.ATTACK_CARD_*` 逐条 `new CardItem(props.stacksTo(64).rarity(…).component(CARD_USES, AppliedStone.defaultUses("<typeId>")), "<typeId>")`）⇒ 新增两张照此写，注册 id `attack_card_<typeId>`。
- 专属牌复用既有机制：`protected boolean isExclusive()` + `ExclusiveCardUtil` + `owner_uuid` 数据组件 + 标签 `is_exclusive.json`；**随机卡池必须排除**（`RandomCardHandler`）—— 参考既有专属牌（命运的指引 / 活体书页 / 符卡-福 / 符卡-祸）。
- 效果/附件/文案键的命名口径沿用 zhao 批契约（`effect/ModEffects`、`component/ModAttachments`、`tooltip.astral_dice.*`、`astral_dice.guide.entry.*`）。

### 2. 注册与资产触点清单（实施时逐条打勾；路径以 1.21.1 为准，1.20.1 见括号）

**A. 每个新立牌**
1. `item/sign/<X>SignItem.java`（继承 `BaseSignItem`；主动 `handleUse`、被动钩子，若为选择器类再覆写 `selectorActionId()`，必要时 `startActiveLockOnUse`）。
2. `item/ModItems` 注册（`.rarity(<品质映射>)`）+ `init/ModCreativeTabs` 入创造栏。
3. `datagen/ModRecipeProvider` 形状配方（立牌 + 骰子/星盘，**立牌固定中下**）+ 生成物 `recipe/<id>.json`、`advancement/recipes/misc/<id>.json`。
4. 标签：`data/astral_dice/tags/item/signs.json`（1.20.1 为 `tags/items/signs.json`）、`data/curios/tags/item/stand.json`（1.20.1 `tags/items/stand.json`）。
5. 贴图 `textures/item/<id>_sign.png`（自 `images/<中文名>立牌.png` 复制；两条发布线**逐字节相同**）。
6. `datagen/ModItemModelProvider`（两发布线单根 `src/generated/resources`）+ `lang/zh_cn.json` / `lang/en_us.json`（物品名 + tooltip 键）。
7. 手册：`assets/astral_dice/patchouli_books/astral_guide/en_us/entries/signs/<id>.json`（**手册只有 `en_us` 目录**，中英由 lang 键 `astral_dice.guide.entry.*` 承担；现有 signs 目录 19 条）。
8. 若新建效果：`effect/ModEffects`（两线现各 34 个）+ `textures/mob_effect/<effect>.png`（无专用图时按取材规则复用载体物品图）+ lang `effect.*`。
9. 若新增玩家附件：`component/ModAttachments`（1.21.1 视需要 `.sync()`；**1.20.1 新增同步键必须同时进 `SYNCED_KEYS`**）并更新 AGENTS.md 的附件计数（现 1.21.1 **78** / 1.20.1 **80**）。
10. 赏金池：复跑 `pwsh -File scripts/verify/verify_bountiful_pools.ps1 -Root <该线工作树>`；明星牌入池/出池口径待裁决。
11. AGENTS.md 三处表：**立牌技能表**、**立牌品质表**、效果/附件/数据组件清单；`neoforge-26.1.2` 冻结清单追加迁移项（见 §4）。

**B. 每张战斗牌**
1. `item/ModItems` 注册 `ATTACK_CARD_<TYPE>`（`CardItem` + `CARD_USES` 组件；品质映射见 §0）。
2. `combat/CardRegistry`：`init()` 里注册 `new CardType("<typeId>", false, <uses>, <cost>, <item>, <roller>)`，**并且**在 `defaultUses(String)` 的 `switch` 里补分支（该方法在 `ModItems` 静态初始化阶段就被调用，`CardRegistry.init()` 更晚 ⇒ **不能**只依赖 `BY_ID`，否则耐久静默回退为 10 的既有坑）。
3. 结算/加算落点：`combat/DiceCombatModifiers`（攻击力加算）或该牌自己的 `roller`。
4. 标签：`data/astral_dice/tags/item/combat_cards.json`（**战斗牌汇总标签，两线各一份**）+ 专属牌另进 `is_exclusive.json`（1.20.1 同为 `tags/items/`）。
5. tooltip 两行：费用行在最上方、黄色 `Cost: ⨀…`（费用由 `CardRegistry.cost(type, player)` 动态给），描述行 `点数 | 剩余次数: X`。
6. lang（中英）+ 手册 `entries/cards_attack/<id>.json`（现有 `cards_attack/` 7 条）。
7. 获取渠道：蛟龙专属 ⇒ 由蛟龙立牌技能（主动或被动）发放/回收，**具体入口与"专属绑定"时机待裁决**；若走随机发放则必须排除（§1）。

**C. 两线对等与测试资产（照 zhao 批）**
- 先 1.21.1 落地 → 按 `docs/compat-1.20.1-forge.md` 同步 1.20.1；共享文件（`lang/*.json`、`ModItems`、`CardRegistry`、探针）单一 owner。
- 探针新增只读读数段（立牌身份/状态、两张战斗牌的费用/耐久/骰点、专属拒绝、被动触发），用例按 zhao 批形态（前置归一化 → 双人 Carpet bot → 断言 `AP_` 读数），并复用 zhao 段的 9 条踩坑 + 2 条工具层坑（见《风水师立牌（zhao）…双人测试流程》段）。

### 3. 待用户裁决（**动代码前必须定稿**）

1. **4 个立牌的主动/被动技能**：名称、数值、时长、冷却、是否目标选择器类（`PLAYER` / `ENEMY_OR_RIVAL` / `allowSelf`）、是否起「锁定(生效中)」态（是否施加自己的计时器）。
2. **两张战斗牌**：费用、耐久（次数）、骰点/效果、品质色；「撕咬」「龙之咆哮」是否为纯攻击加算或有附加效果（造成真伤/多段/对带厄运目标加成等）。
3. **专属牌的获取与回收**：蛟龙主动技能发放？被动触发？使用后是否回收/绑定 `owner_uuid`；是否入赏金池。
4. **配方档位**：每个立牌在 §0 的同品质可选档中取哪一个。
5. **`人偶完成.png` / `人偶制作.png`**：是「人偶师」技能的两个阶段/状态图标（⇒ 需 2 个新效果或 1 个效果 + 1 个 UI 状态），还是别的东西？这决定要不要新建效果与同步键。
6. **英文名**：4 个立牌与 2 张战斗牌（`lang/en_us.json`）的英文命名。
7. **是否同步 26.1.2**：该线当前**冻结**（用户裁决），预期本批**只在两条发布线**落地，26.1.2 解冻后按 `docs/compat-26.1.2-neoforge.md` 迁移（届时必须补 `src/generated/clientResources` 的 `items/*.json` 物品模型定义——26.1.4+ 硬需求）。

### 4. 实施顺序（裁决后）

① 1.21.1 代码（subagent 并行 + 单一 owner）→ ② 1.20.1 同步 → ③ 双线构建 + 全流程冒烟（`preflight→build→env→launch→cases→report→stop`）→ ④ 测试资产（探针 + 用例）并逐条 `--case` 单跑取 PASS（**不要**依赖全目录跑，见下）→ ⑤ 文档（AGENTS.md 三表 + `scripts/test/TESTING-SPEC.md` 附录 A 工程条目）→ ⑥ **两份 CHANGELOG 同步补玩家侧条目**（中英条目数一致）→ ⑦ 本地提交（**不 push**）→ ⑧ 26.1.2 迁移（解冻后，含三侧读数 diff 一致性测试）。

### 5. 风险与照抄即可避免的坑

- **Rhino 侧**（探针）：顶层 `var`/`function` 重复声明 ⇒ 整脚本加载失败；实例方法必须调在实例上；1.20.1 的 `ModEffects.X` 是 `RegistryObject`，判效果必须 `findEffect(p,"<id>")`。
- **断言侧**：读数必须按实际字段顺序写正则；`zhaocard` 类成对读数用 `before>after`。
- **测试工具侧**：全目录 `--phase cases` 会给另一版本条目写 `SKIP` 记账，`report.md` 自动判定把非 `PASS` 全判 ❌；且其预算是固定 `90+90×用例数`，`MT_CASES_TIMEOUT_SEC` 管不到 ⇒ 一律逐条 `--case` 单跑，必要时用 `mt_report.ps1 collect --version <V> --verdict PASS` 重算并留痕（详见 zhao 测试流程段）。
- **数据生成**：改资源前必须按「Gradle 构建守护规则」第 7 条移开 `run/<V>/mods` 里的旧产物 jar，否则 `runData` 静默用旧代码；两发布线 `exclude("**/.cache")` 口径不得回退。
- **26.1.2 冻结**：本批不得动该线任何文件；迁移清单必须在本批落地后追加（否则解冻时漏项）。

---

## Carpet: NeoForged（玩家 bot / 双人联动测试）— 2026-09-27

用户要求（原话）：「向 1.21.1 和 1.20.1 测试端插入 Carpet: NeoForged 模组（Fabric Carpet 的 Forge/NeoForge 移植，https://github.com/chililisoup/neoforge-carpet）……同时 clone 源代码到本地用于分析功能（1.21.1 为 master 分支，1.20.1 为 1.20.1 分支）……在测试脚本中，插入 `/player` 命令用于创建玩家 bot，该 bot 可被用作测试 2 人及更多玩家的联动功能；使用 `/kill` 命令击杀 bot 玩家可使其退出……26.1.2 版本不使用该模组，mojang 在高版本加入了可以创建 bot 的管理员指令。后续再进行学习。」

**动机**：单人测试世界此前**没有第二名真实玩家** ⇒ teru 降神的「另一名玩家」只能用 FakePlayer（不在玩家列表 ⇒ 施法者侧指针每 tick 被判链接失效、读数只能取在施法同一次调用内）或「自身脚手架」（施法者 = 目标）。Carpet 的 `/player` bot 是**真 ServerPlayer**，于是补上了三条此前覆盖不到的语义：真实双人链接跨 tick 存续、目标中途离线/死亡后的施法者侧自愈、**被指定者（bot）真实近战**触发的狐光「新目标」消耗。

### 装配（三条线机制不同，禁止照抄）

| 线 | 机制 | 闸门读数 |
|---|---|---|
| **1.21.1** | `mt_env.ps1` 的 `$script:CarpetByVersion['1.21.1']` → Modrinth Maven `maven.modrinth:neoforge-carpet:lnOeoKcQ`（`neoforge-carpet-1.21.1-1.0.8+v251027.jar`，1498683 B，sha1 `fffcc899…`）下载进 `run/1.21.1/mods`（NeoForge 1.21.1 无 reobf，生产 jar 即 Mojmap 命名） | `CARPET_LOADED=true` |
| **1.20.1** | **`forge-1.20.1/build.gradle` 的 `modImplementation "maven.modrinth:neoforge-carpet:XMYeNIOx"`**（`forge-carpet-1.20.1-1.0.8+v251027.jar`，1740072 B，sha1 `e9406d06…`）；`mt_env` 对该版本只回显来源 + 清理 `run/1.20.1/mods` 里的历史副本 | 同上 |
| **26.1.2** | **不装**（用户裁决：该线用 Mojang 自带 bot 管理指令，后续再学） | 无闸门 |

为什么 1.20.1 必须走 build.gradle：Modrinth/CF 给的是**生产(SRG)字节码 + SRG refmap** 的 Forge jar，手工放进 `run/mods` 既不会被重映射、refmap 也不会被改写 ⇒ **mixin 静默失效**（与 FerriteCore/Jade/collective/superflat 同一类限制）。实测 MDG 解析后 `caches/9.2.1/transforms/…/neoforge-carpet-XMYeNIOx.jar` 内确有 `carpet.mixins.json` + `carpet.refmap.json`（150 KB）。

`mt_launch.ps1` 的硬闸门：`Test-MtModLoaded 'carpet'`（NeoForge 读 latest.log 已加载清单行的 `(carpet)`，Forge 读 debug.log 的 `Found valid mod file … with {carpet} mods`；**只认清单行**，避免把存档里的 `carpet (version X -> MISSING)` 误判成已加载）⇒ 缺装载直接 `MT_LAUNCH: ERROR`；唯一例外 `MT_ALLOW_NO_CARPET=1`（对照实验，留 WARN）。

### 源码分析（本地 clone 在 gitignore 的 `temp/` 下）

- `temp/carpet_src/1.21.1`（branch `master`，HEAD `44360a0` = 1.0.8）、`temp/carpet_src/1.20.1`（branch `1.20.1`，HEAD `4b3f3ddc` = 1.0.8）；经本地代理 clone。
- 关键类：`carpet/commands/PlayerCommand.java`（`/player` 全部子命令）、`carpet/patches/EntityPlayerMPFake.java`（bot 生命周期）、`carpet/CarpetServer.java`（命令注册）、`carpet/api/settings/SettingsManager.java` + `carpet/CarpetSettings.java`（`/carpet` 规则表）。
- **全量命令面**（两线基本一致；1.20.1 多一个 `/tick`）：`/carpet <rule> <value>`（`/carpet list|defaults|setDefault|removeDefault`；1.21.1 **93** 条 `@Rule`，1.20.1 **97** 条）、`/player`、`/spawn`、`/counter`、`/log`、`/profile`、`/info`、`/distance`、`/perimeterinfo`、`/draw`、`/track`、`/script`、`/tick`（仅 1.20.1）、dev 环境另有 `/testcarpet`；vanilla `/perf` 在非专用服务器也会被 Carpet 注册。
- **`/player` 子命令**：`spawn [in <gamemode>] [at <pos> [facing <rot> [in <dim> [in <gamemode>]]]]`、`kill`、`shadow`、`stop`、`use|jump|attack|drop|dropStack|swapHands [once|continuous|interval <ticks>]`、`drop|dropStack <all|mainhand|offhand|slot>`、`hotbar <1-9>`、`mount [anything]`、`dismount`、`sneak|unsneak`、`sprint|unsprint`、`look <north|south|east|west|up|down|at <pos>|<rot>>`、`turn <left|right|back|<rot>>`、`move [forward|backward|left|right]`。
- **bot 语义（源码级，已实测）**：
  - `EntityPlayerMPFake#createFake` 用**离线 UUID** 兜底（规则 `allowSpawningOfflinePlayers=true` 默认）⇒ bot 名不必是真实账号；入口 `CommandHelper.canUseCommand(source, CarpetSettings.commandPlayer)`，`commandPlayer="ops"` ⇒ **需要 OP**（单机开作弊 = 权限 4）。
  - bot 是**真 ServerPlayer**：进玩家列表（`list=N`）、参与 tick、可被命令/选择器当目标；连接实现是 `NetHandlerPlayServerFake` —— 探针用 `"" + player.connection` 的 toString 判定 `bot=1`（Rhino 下 `getClass()` 不可用）。
  - `kill()`（无参；vanilla `/kill <name>` 与 `/player <bot> kill` 都落到它）⇒ `kill(Messenger.s("Killed"))` ⇒ `connection.onDisconnect(...)` ⇒ 玩家列表移除，日志 `Bot1 lost connection: Killed`；`die()` 也调 `kill(死亡消息)` ⇒ **`/kill` 就是让 bot 退出**（不会躺在死亡界面）。
  - **bot 的背包/手持/属性跨用例重跑保留**（走正常存档）⇒ 用例必须 `/clear <bot>` 归一化。
  - `cantReMove` 拒绝真实玩家 ⇒ `/player <真实玩家> kill` 无效（只能 kill bot）；`/player <name> shadow` 是「真玩家转 bot」的另一条路（本批未用）。
- **复用片段（新用例要在游戏内造 bot 时照抄这五行）**：`/carpet allowSpawningOfflinePlayers true`（规则面，默认已是 true，显式写更稳）→ `/player <bot> spawn at ~3 ~ ~1`（建 bot，继承施法者的维度/朝向/游戏模式，`at` 可省）→ `/clear <bot>`（**必须**，见踩坑 1）→ 断言 `/astralprobe terubot <tag> <bot>` 得 `found=1:…:bot=1:…:list=N` → 收尾 `/player <bot> kill`（或 vanilla `/kill <bot>`）后再断言同命令得 `found=0`（`list` 相应减 1）。

### 游戏内测试资产（两线同步）

- 探针 teru 段新增（命令名两线一致）：`terubot <tag> <name>`（只读身份/在线名单）、`teruequipbot`（给 bot 装骰子 = 骰战链硬前提）、`terublessreal <tag> <give|clear> <name>`（给/撤 bot 的骰神赐福）、`terucastreal`（**真实施法**指向真实玩家，且**不**自动收尾 ⇒ 可跨 tick）、`terureadreal`（跨 tick 读施法者侧 + 目标侧）、`teruendreal`（显式 `endDescent` 并读双侧归零）、`teruhitreal`（**攻击者 = 真实 bot** 的真实近战，复用 `meleeHit`）。
- 用例：`scripts/test/cases/BOT-2P-1.21.1.json` / `BOT-2P-1.20.1.json`（84 步 / 43 断言）——bot 生命周期与命令面、真实双人降神（快照 + 双侧派生值）、跨 tick 链接存续、被指定者真实近战触发狐光「新目标」消耗（含同靶对照）、三种收尾（`/player kill` 登出 / 显式 `endDescent` / `/kill` 真实死亡）后施法者侧同 tick 自愈。
- **实测（2026-09-27，两线各跑一次全流程，均通过）**：1.21.1 与 1.20.1 全流程（preflight → build → env → launch → cases → report → stop）均 **PASS**，用例 **5/5 PASS**（新增 `BOT-2P` + 回归 `TERU-SIGN`/`TERU-EXTRA-ATTACK`/`TERU-HUGUANG-ANTIFARM`/`LULU-SIGN`，⇒ Carpet 的 200+ mixin 未影响既有行为）；报告 `scripts/test/reports/20260920-114651/{1.21.1,1.20.1}/report.md`。闸门读数：1.21.1 `Carpet: NeoForged 1.4.147+v1.0.8-251027 (carpet)`；1.20.1 `Found valid mod file neoforge-carpet-XMYeNIOx.jar with {carpet} mods - versions {1.4.112+v1.0.8-251027}`（Carpet 自身的内部构建号两线不同，属正常）。
- 关键读数（两线逐字段一致）：`AP_CRE_CASTREAL:cast=1:target=Bot1:A_t=20:D_t=12:A_c=6:bonus_atk=10:bonus_def=6:base=16:atk_ok=1:def_ok=1:base_ok=1:layers=3:cache=10:cache_ok=1:armor=0>12:armor_ok=1`；跨 tick `AP_RDK_KEEP:caster=0:target=1:cache=10:ap=16:dp=8:armor=12:armor_mod=12`（FakePlayer 路径下一 tick 就会被清零）；`AP_HR1_HITREAL:t_newt0=0:h0_dmg=42:…:t_newt=1` → `AP_RDA1_A1:layers=2:…:cache=10`（新目标消耗 1 层）→ `AP_HR2_HITREAL:t_newt0=1:h0_dmg=24:…:t_newt=1` → `AP_RDA2_A2:layers=2`（同靶不再消耗；两次掉血差 18 = `base 16 + 剩余层数 2`）；`/player Bot1 kill` 后 `AP_RDG_GONE:cache=0:armor=0:armor_mod=0`、`/kill Bot2` 后同形 ⇒ 链接目标离线/死亡时施法者侧自愈。

### 踩坑（本批实测，写用例必看）

1. **bot 玩家数据持久**：第二次跑同一用例时 Bot1 仍握着上一轮 `/item replace` 给的铁剑 ⇒ 施法快照 `A_t` 20→25、`bonus_atk` 10→12、`base` 16→18、`cache` 10→12，断言整段失效。⇒ 建完 bot 立刻 `/clear <bot>`（curios 槽里的骰子不在背包，`/clear` 不影响）。
2. **进世界后若暂停菜单开着，所有注入被吞**：一次重跑时日志只有 `Saving and pausing game...`、连一条 `AP_` 都没有、43 断言全 FAIL。⇒ 重跑前确认没有暂停菜单（激活游戏窗口按 ESC 回到游戏）；`mt_case` 的 `ESC_SKIP: pause-lock 生效 + -NoEsc` 表示它**不会**替你按 ESC 归一化。
3. **join/leave 文案是本地化的**（`Bot1加入了游戏` / `Bot1退出了游戏`）⇒ 断言必须用**语言无关**判据：`Bot1\[[a-z]+\] logged in with entity id [0-9]+`（登录）与 `Bot1 lost connection: Killed`（退出），不要用 `joined/left the game`。
4. `terucastreal` 内部的 `teruClearState(目标)` 会**清掉目标身上的骰神赐福**（脚手架归一化）⇒ 施法读数里 `t_bless=0:t_skip=0:t_prev=0` 是预期值；骰战链在 bot 首次攻击时自动补赐福（`HR1` 读数可见 `t_bless=1`）。
5. 降神效果实例是**无限时长**（`t_fx_d=0/2147483647`）⇒ `teruTargetRead` 的 `t_fx_d` 断言只写 `[0-9]+/[0-9]+`，不比精确值。
6. 骰战链（含狐光追加攻击）的三条硬前提：**主手近战武器** + **骰神赐福** + **curios `dice` 槽有骰子**（`DiceCombatEvents` 在 `diceStack == null` 处直接 return）⇒ 少一条，额外攻击永远不会被消费，读数是「没反应」的假阴性。

---

## 风水师立牌（zhao）+ 符卡-福 / 符卡-祸 —— 双人测试流程 — 2026-09-27

用户要求（原话）：「创建新测试流程，测试风水师立牌以及关联的符卡-福和符卡-祸。使用 carpet 完成双人测试。」

**范围**：本批**只做测试资产**（探针 zhao 段 + 4 条用例），**产品代码零改动** —— `zhao_sign` / `fu_card` / `huo_card` 的功能在 2026-09-26 批次已落地。语义契约 = 冻结件 `docs/features/fengshui-sign-spec.md`（§14 的 Q1–Q4 裁决、§14.5–§14.12 已回填），用例只对该契约取证；本批未新增/修改任何 CHANGELOG 条目。

### 资产（两线同步）

| 资产 | 路径 | 规模 |
|---|---|---|
| 探针 zhao 段 | `scripts/test/resources/kubejs/{1.21.1,1.20.1}/server_scripts/astral_bugfix_probe.js` | 两线**逐字节相同**的 1081 行块（插在 `ServerEvents.commandRegistry(...)` 之前：1.21.1 自 ≈6354 行起）＋ 分发表 25 条（插在 `teruclear` 分发块之后） |
| 立牌用例 | `scripts/test/cases/ZHAO-SIGN-{1.21.1,1.20.1}.json` | 142 步 / 50 断言 |
| 符卡用例 | `scripts/test/cases/FUHUO-CARD-{1.21.1,1.20.1}.json` | 137 步 / 46 断言 |

⚠️ **改探针必须冷启动**：KubeJS 的 `/kubejs reload` **不重绑 Brigadier 命令** ⇒「`--case` 复用在线客户端」只在**探针未改动**时成立；动过探针就要重新 `--phase launch`。

### 探针命令（25 条，两线同名同参；读数行前缀 `AP_<tag>_`，除基线/脚手架外全部只读）

| 组 | 命令 |
|---|---|
| 基线/归一化 | `zhauprep <tag> [clear]`（给自身 风水师立牌 + 骰子 + 铁剑，清卡/状态/效果/计时器/出牌轮）、`zhaoclear <tag> [name]`、`zhaosign <tag> <name> <zhao\|fen\|none>`（给真实玩家/bot 装备或卸下立牌） |
| 发牌/归属 | `zhaogive <tag> <fu\|huo> <n> [name]`（给**受赠者**，绑定非获得者）、`zhaosethuo <tag> <n> [name]`（张数设 n + 立即镜像厄运 + 计时器解耦证据） |
| 只读 | `zhauread <tag> <phase> [name]`（全量状态）、`zhaureg <tag>`（注册/冻结数值/标签/cardByTypeId） |
| 施法与门控 | `zhaocast <tag> <self\|name\|mob> [name]`（服务端权威 `applyBlessing`）、`zhaogate <tag> <self\|name> [name]`（真实选择器路径 `performSkillForCurio` → `confirm`） |
| 骰点被动 | `zhaodice <tag> <1\|6> <times>`、`zhaodedup <tag> <1\|6>`（同 tick 两次判定 ⇒ `tryClaimDiceJudgment` 去重） |
| 效果/溢出 | `zhaobless <tag> <give\|clear> [name]`（骰神赐福：施加走原版 `/effect give`、移除走**内部通道** ModEffectRemoval）、`zhaoheal <tag> <amount> <gap> [name]`、`zhaoseq <tag> [name]`（溢出链整跑 10/0、6/4、0.5/0、0.5/0、4/4 收进**一条**读数）、`zhaofx <tag> [name]`（外力移除白泽赐福 → 下一 tick 自检） |
| 符卡 | `zhaocard <tag> <fu\|huo> <self\|name\|mob> [name] [owner]`（主手 → `tickHeldSelector` → confirm **会话路径**）、`zhaoplay <tag> <fu\|huo> <self\|name\|mob> [name] [owner]`（直接 `playFromSelector` 权威路径）、`zhaohand <tag> <fu\|huo> <main\|off>`（§14.11）、`zhaofu <tag> <times>`（连用 n 次，逐次 `ok/play/max/fubonus/cool`）、`zhaohuotick <tag> <now\|force> [name]`、`zhaohuobox <tag> <main\|off\|ender> [name]`、`zhaodrop <tag>`（真 `ServerPlayer#drop`） |
| 生命周期/收尾 | `zhaolife <tag> <die\|relogin> [name]`、`zhaolink <tag> <read\|call>`（心意相连）、`zhaonuclear <tag>`（清卡/效果/状态/计时器 + 卸立牌 + 清主手） |

### 双人怎么造（复用上一节的五行）

`/carpet allowSpawningOfflinePlayers true` → `/player Bot1 spawn at ~3 ~ ~1` → `/clear Bot1` → 断言登录行 → 收尾 `/player Bot1 kill`（或 vanilla `/kill Bot1`）→ 断言 `Bot1 lost connection: Killed`。**本批新增的归一化要求**：bot 的 **curios `stand` 槽 + 7 个 zhao 附件跨轮保留**，所以每条用例开头都必须先 `zhauprep` / `zhaoclear`（同时清背包**与地面**的卡、清白泽/厄运/骰神赐福、回满血、把 bot 拉回主手位），否则上一轮残留会直接污染 `z_fu`/`z_huo` 计数。

### 覆盖（4 条用例；两线各跑一次，均 PASS）

- **`ZHAO-SIGN`**：注册冻结值（物品 id / 动作 id / `HEAL_AMOUNT=2` `DAMAGE=1.0` `CURSE_PERIOD_TICKS=2400` `DURATION_TICKS=MAX_VALUE` `MAX_RECHARGE=5` / `astral_dice:signs` + `curios:stand` 标签）；`zhaocast` 门控（**生物被拒 / 自身放行**）；双人施法 + **完美帮手**（断言用 `t_fen_delta=1`，**不比 `z_fen` 绝对值** —— 大当家立牌会被动积累养精蓄锐）；**心意相连**（team 0→1、`granted=1`）；结束判定的**下降沿**（已带骰神赐福 ⇒ `skip=1` 跳过本次，随后真结束）；`zhaoseq` 溢出链 + 结束归零**无残留**；`zhaofx` 外力移除后下一 tick 自检（`active=0`）；`zhaolife die/relogin`；**`/effect clear` 拦截证明**（外部移除被取消 ⇒ bot 仍 `z_bless=1`）；收尾真实 `/kill Bot1`。
- **`FUHUO-CARD`**：符卡-福 对 bot **+2** 治疗；`zhaofu 10` 净 0（消耗 1 / 返回 1）+ `min(9,1+extra)` 封顶 + 专属拒绝链；专属牌在**选择器开局与服务端权威两处都被拒**；§14.11 主手唤起 / 副手不唤起；符卡-祸 对 敌对/同队/自身/生物；厄运层数 = 主物品栏 **+ 副手**（**末影箱不计**）；2400 tick 结算按**结算时刻**张数；`timer_same=1`（计时器与张数解耦）；**丢弃必须成功**（`dropped=1:inv=1>0:ground=0>1`）；骰点被动 1/6 与同 tick 去重、卸下装备后 no-op。
  ⚠️ 「符卡-祸 禁止丢弃」已于 **2026-09-20 按用户裁决整体移除** ⇒ 用例断言的是**丢弃成功**；冻结规格 §3.3 / §14.1 里那句已过期。

### 实测（2026-09-27，两线各 7/7 PASS）

- 逐条：`ZHAO-SIGN`（192 步 / 142 步 + 50 断言 / **0 FAIL**）、`FUHUO-CARD`（183 步 / 137 + 46 / **0 FAIL**）＋ 5 条回归 `BOT-2P`、`LULU-SIGN`、`TERU-EXTRA-ATTACK`、`TERU-HUGUANG-ANTIFARM`、`TERU-SIGN` 全 **PASS**；两线收尾闸门一致：`CARPET_LOADED=true`、`MT_ASSERT_KUBEJS: PASS — server.log 0 errors`、`MT_ASSERT_CRASH: PASS — 无 crash-reports`。
- 报告：`scripts/test/reports/20260920-114651/1.21.1/report.md` 与 `.../1.20.1/report.md`，结论均 **✅ PASS**。
- ⚠️ **「全目录跑」与报告自动判定互相打架（本批实测踩到，两条工具层坑）**：
  1. `--phase cases`（不带 `--case`，即 run-dir）会给**另一版本**的条目写 `SKIP` 记账；而 `report.md` 的自动判定是「`cases` 字典里非 `PASS` 即 ❌」（`mt_report.ps1` 的 `$allPass` 只判 `-ne 'PASS'`），`SKIP` 因此把整版判失败（`SUMMARY.md` 侧判据反而容忍 `SKIP` —— 两处口径不一致）。实测形态：`phases` 为空、7 条真用例全 `PASS`，`report.md` 仍给 ❌。**混用「全目录跑」与「`--case` 单跑」后**用编排器接口重算并留痕：`pwsh -File scripts/test/mt_report.ps1 collect --version <V> --verdict PASS`（本批 1.21.1 即按此重算；单跑 `--case` 不写 `SKIP`，所以 1.20.1 走自动路径就是 PASS）。
  2. 全目录跑的**预算公式是固定的 `90 + 90×用例数`（7 条 = 720s）**，`MT_CASES_TIMEOUT_SEC` 只影响外层阶段预算、**管不到内层**该公式；7 条重用例（实测 ≈140s/条）必然超预算被截断，**剩余用例直接不再执行**（本批实测两次，都在第 5 条之后被杀，`TERU-SIGN`/`ZHAO-SIGN` 从未轮到）⇒ 复核请逐条 `--case` 单跑。

### 未覆盖（如实登记）

`zhaogate` 的**真实按键会话路径**（`performSkillForCurio` → `confirm`）本批**未进用例** —— 立牌的「按键 → 会话」接线与其余选择器立牌共用 `BaseSignItem` / `TargetSelectionManager`，该共享路径已由 `LULU-SIGN` / `TERU-SIGN` 覆盖；本批的立牌门控改从**服务端权威入口**取证（生物被拒 / 自身放行）。另：跨维度选择、26.1.2 一致性测试均未覆盖（26.1.2 本批**零改动**，解冻后按 `TESTING-SPEC.md` §13.2 做三侧读数 diff）。

### 踩坑（本批实测，写用例必看）

1. **Rhino 同作用域重复 `var` / `function` 声明 ⇒ 整个探针脚本加载失败**（`TypeError: redeclaration of var …`，表现为 `/astralprobe` **一条命令都没有**、launch 闸门报 61 条 KubeJS 错误）。本批因 `EffectCardPeriodClass` 与既有声明重名踩过一次 ⇒ 给探针加段前先扫「顶层名是否与既有重复」（探针里已有该自检）。
2. **实例方法必须调在实例上**：`stack.getItem().playFromSelector(...)`。对类调用（`SomeItemClass.playFromSelector(...)`）会抛 `InternalError: Java class "…" has no public instance field or method named "playFromSelector"`。
3. **1.20.1 的 `ModEffects.X` 是 Forge `RegistryObject`**：`p.hasEffect(ModEffects.X)` / `new MobEffectInstance(ModEffects.X, …)` 会触发 KubeJS 的注册表强转（`RegistryInfo.wrap` → `UtilsJS.getMCID`），抛 `ResourceLocationException` / `NPE: No such element with id null in registry minecraft:mob_effect`，**且该异常逃出 JS `try/catch`**（`guard` / `zhaoBool` 都捕不到）⇒ 判效果一律用 `findEffect(p, "<id>")` **字符串匹配**；施加骰神赐福用 `/effect give`，移除用内部通道（本批 1.20.1 客户端中途退出即因此）。
4. **断言正则必须按实际字段顺序**：`zhaoStateRead` 的顺序 = `z_fu, z_huo, z_equipped, z_active, z_skip, z_prev, z_fx, z_fx_on, z_over, z_rem, z_ap, z_mis, z_mis_on, z_mis_fx, z_next_delta, z_play, z_max, z_bonus, z_fubonus, z_cool, z_fen, z_team, z_links, z_hp, z_maxhp, z_bless, z_alive, z_hand, z_off`（`z_mis` 系列在 `z_hp` **之前**；`z_fx` 在 `z_fx_on` 之前）。`zhaocard` 行的 `owner=…` 与 `can_use` 之间**夹着** `hand=…`；`zhaocard` 的 `play/max/fubonus/cool` 是 `before>after` **成对**，`zhaoplay` 是**单值**。本批 17→16→4→1 条断言 FAIL 全部是这类字段序/形态问题。
5. **单用例注入步骤多 ⇒ 会撞默认 180s 闸门**（实测分别在 107/201、118/186 处超时）⇒ 合并步骤（5 次治疗 → `zhaoseq`、10 次出牌 → `zhaofu`）+ 压缩等待，并以 `MT_CASE_TIMEOUT_SEC=300` 跑（全目录 `MT_CASES_TIMEOUT_SEC=2400`）。
6. `zhaoclear` 会**回满血并复位养精蓄锐** ⇒ `zhaoheal` 的 `hp0` 必须读在压血之后、断言读 `heal` 之后（clamp 后值）；`zhaoheal` 与 `zhaoclear` 之间不要插别的读数步骤。
7. **用例 JSON 里的 `{` 必须写成 `\\{`**：断言文本含 `{` 时写单反斜杠会让整份 JSON 解析失败（`--- JSON …` 报 invalid escape）。
8. **参数位写错会让该步静默不产出读数**：`zhaocard` / `zhaoplay` 的尾部是 `[name] [owner]` 两个位置参数，本批误写成 `… Bot1 - Bot1`（多一个 `-`）导致该步零读数、无任何报错。
9. 1.20.1 的 `zhaohuobox ender` 计时器要到**下一个 tick** 才清 ⇒ 该断言要么等一拍、要么只断言张数与效果。
