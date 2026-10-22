# Agent Instructions

<!-- BEGIN 代理协作契约（2026-10-16 用户裁决）— 如需挪位，整块剪切到别处即可 -->

## ⛔ 回滚裁决（2026-10-18 用户裁决，优先级高于本文件其余测试/环境条款）

**背景**：WSL 内运行 Minecraft 客户端存在严重性能问题，实机读数与截图不可信；2026-10-18 的 WSL + AutoJS 测试台与其全部实机结果（含 1.21.1 / 1.20.1 双版本）**一律作废**。

**2026-10-22 改写（DSH 转 Windows 原生）**：本块第 1–6 条已由「WSL 口径」整体改写为 Windows 口径，原 WSL 条款**一律失效**（含 `/etc/profile.d` 注入脚本、`/opt/zulu`+`use-jdk`、`~/work/adm-*` 编译副本、WSL git 变通、「bash 里没有 pwsh」类说明）；逐条对照历史见 `docs/agent/AGENTS-full.md` 末尾〈WSL 口径失效对照〉。下文标着「生效红线区」的那一小节（= 快照 L2–L120）经核**不含任何 WSL 专用条款**，故未改动（它与快照必须逐字一致，单边改必 FAIL `verify-split.py` 第 2 项）。

1. **测试台唯一口径 = PowerShell 7**。所有测试脚本都是 `.ps1`，一律 `pwsh -NoProfile -File <绝对路径>` 执行；
   **禁止** `bash`、`.sh`、`bash mt.sh`、WSL 侧测试入口（`.sh` 已从仓库清除，不得再引入）；禁止 Windows PowerShell 5.1。
2. **编译与实机都在 Windows 原生跑**（原「WSL 只承担编译」口径**失效**）：编译 = 在 `F:\MCProject\astral_dice_multiloader` 执行
   `gradlew.bat :<线>:classes|jar`；实机 = `pwsh -NoProfile -File F:\MCProject\astral_dice_multiloader\scripts\test\mt.ps1 …`（禁 `wsl.exe` / `/mnt/...`）。
3. **产物投放**（原「WSL 产物复制回 `-next\run`」**失效**）：以工作树 `build\libs` 为唯一来源，按线复制到
   `run\<1.21.1|1.20.1|26.1.2|fabric-1.20.1>\mods`，或**成对**投放两个 P0 整合包（`astral_dice-*.jar` + `starengine-*-1.1.0-pre.2.jar`）；
   投完必须核 sha256，别把同名当新构建。
4. **git 一律用 git bash 的 `git`**（原「一切 git 操作经 `cmd.exe`」与「WSL 报 `not a git repository`」的变通**均失效，该问题不存在**）：
   命令写全 `git -C F:\MCProject\<工作树> <子命令>`，或先 `cd` 进工作树；不需要 `--git-dir=`，也不需要 `cmd.exe /c`。
5. **工作副本口径作废**：工作树 `F:\MCProject\astral_dice_multiloader` 是唯一事实源 / 编译源 / 提交源；
   `~/work/adm-*`、`/mnt/**` 副本一律不再使用（原「WSL 副本只作编译缓存」条款取消）。
6. **环境注入脚本与 WSL JDK 路径作废**：`/etc/profile.d/99-wsl-proxy.sh`、`99-wsl-github.sh`、`wsl-env-restore.sh`、`/opt/zulu` + `use-jdk` 均视为已失效；
   Windows 侧代理走系统环境变量（Clash `http://127.0.0.1:7897`，无注入脚本、无需恢复）；JDK 用 `C:\Program Files\Zulu\zulu-21`（21.0.12），
   `forge-1.20.1` 的 toolchain 17 由 Gradle 从 `%USERPROFILE%\.gradle\jdks` 取（已缓存 Adoptium 17.0.20），不要手改。

7. **测试收尾必须自动关闭游戏进程（2026-10-18 用户裁决）**：任何测试轮次（单条 / 全量 / 中断 / 失败）结束后，
   必须执行 `pwsh -NoProfile -File scripts\test\mt.ps1 --version <线> --phase stop --force`，并在交付报告里贴出
   `MT_STOP: OK` 原文 + 「无本流程 java 客户端残留」的核对结果（`Get-Process java` 计数）。
   **禁止**让 Minecraft 客户端无人观察地驻留 —— 它会持续占用 CPU/GPU，并污染下一轮冷启动的前置状态。
   唯一例外：用例 `on_fail=keep_game_running` 落了 `.mt_keep_alive-<版本>` 的取证现场，
   此时在报告里说明保留原因与路径，取证完成后立即 `--phase stop --force`。


## 下沉到 StarEngine 的判定规则 —— 必须遵守（2026-10-19 用户裁决）

1. **不包含饰品 API**：Curios / Trinkets / Accessories 相关的一切（栏位校验、自动装备、平台 shim）**不下沉**。
   ⇒ `item/CurioSlotUtil` **明确排除**。
2. **不下沉与筹码流派 / 体系相关的类**：充能、治愈、星光、反击、标记等流派资源管理**不下沉**。
   ⇒ `item/ChargeManager`、`item/HealingManager`、`item/StarLightManager` 等**明确排除**。
3. **第三方 API 的注册放 starengine，只下沉「关键联动 / 调用」部分**：
   第三方库（FTB Teams、OpenPAC、Apotheosis、Bountiful 等）的探测与调用封装属可下沉项；
   注册本身在 starengine 内完成，主模组只留业务语义。

**按此规则修订后的下沉候选（2026-10-19）**

| 类 | 判定 | 依据 |
|---|---|---|
| `combat/PartyRelations` | 候选 | 规则 3：FTB Teams / OpenPAC 反射联动，且与库 `EventTargetCollector` 重复实现 |
| `combat/EliteTargets` | 候选 | 规则 3：Apotheosis 精英/Boss 口径 + `c:bosses` 标签 |
| `target/HoldToSelect` | 候选（最安全） | 零依赖纯接口 |
| `target/SelfTargetable` | 候选（须与 `TargetSelectionManager` 同批） | 目标选择扩展点 |
| `event/EffectTimerGuard` | 候选（需把计时表抽 seam） | 通用计时，非筹码体系 |
| ~~`item/CurioSlotUtil`~~ | **排除** | 规则 1（饰品 API） |
| ~~`item/ChargeManager`~~ | **排除** | 规则 2（筹码流派体系） |

## ⛔ 代理协作契约 —— A 方案 + 子代理职业指定（必须遵守，2026-10-16 用户裁决）

> 本节与下文「## 子代理与测试环境硬规则」第 1 / 4 / 7 条**并用**；任何冲突处取**更严格**的一方。
> 放在文件最前是**故意的**：本仓 AGENTS.md 已超 53 万字符，实测首次请求总输入仅 2.4～3.7 万 tokens ⇒ **只有前缀真正进入上下文，文件尾部的规则实际不生效**（详见 `~/.codex/usage-baseline-2026-10-16.md`）。

### A. A 方案：主 Agent 的请求数 / 上下文硬指标（省额度）

**实测依据**：K3 主线程单请求输入中位 **18 万 tokens**（96.9% 命中缓存）；42 个用户回合打了 **871 次**请求
（中位 12 次/回合、最高 132 次）；K3 输出仅 52 万 tokens ⇒ **额度是被主线程重复上下文烧掉的，不是被「思考」烧掉的**；
子线程已 **100%** 走 DeepSeek（无误伤）。

1. **硬指标：主 Agent 每个用户回合的请求数 ≤ 6**（当前 12–21）。每多一次主线程工具往返 ≈ +18 万 tokens。
2. **主 Agent 除「写计划 / 派活 / 验收 / 提交」外不得亲自执行任何动作**：`exec_command`、`apply_patch`、grep、
   读大文件、跑 `gradlew`、跑 `mt.ps1`、`git` 取证命令、"我再验一遍" **一律不执行** ——
   并入现有第 7 条的「主 Agent 只负责规划 / 派活 / 验收 / 提交」，扩展为**不亲自跑任何命令**。
3. **一次派发 + 一次等待**：`wait_agent` 用长超时（≥600s）一次到位；**禁止轮询**（`list_agents` / `read_thread` /
   反复 `wait_agent` 探进度）—— 每次轮询 ≈ 一次主 Agent 全上下文请求。
4. **验收只看产物**：文件路径 + 行号 + 命令输出片段 + 哈希/`git rev-parse --short`；不要求主 Agent 复算。
5. **上下文闸门**：主 Agent 会话 >15–20 回合或上下文 >25 万 tokens ⇒ 把 `plan.md` / `findings.md` 落盘（`work/` 或任务文档）后
   **新开会话**；长日志、整份源码、长表格不进主会话。
6. 子代理一律 `deepseek-flash`（每次显式写 `model`）；仅确需深推理（架构裁剪、复杂 mixin 冲突、跨线差异裁决）
   才用 `deepseek-v4-pro` 并写明理由（既有第 7 条⑧不变）。

### B. 子代理职业指定 —— 每次派发**必须**写明

`spawn_agent` / `followup_task` 的 brief **第一句必须是职业声明**，句式统一为「**你是一位……**」+ 该职业的交付标准；
**禁止派发没有职业的裸任务**。

| 本仓任务类型 | 必须使用的职业开场 |
|---|---|
| 写 / 改模组代码（任一子项目） | 「你是一位专业的 Minecraft 模组开发者（`<neoforge-1.21.1 / forge-1.20.1 / neoforge-26.1.2 / fabric-1.20.1>` / MC `<版本>`），精通 mixin 与本仓多线对等纪律；交付前必须 `./gradlew :<线>:classes` 编译通过并贴出命令输出」 |
| 跨线移植 | 「你是一位 Minecraft 跨加载器移植工程师，按 `docs/compat-*.md` 差异映射逐项落地，禁止整分支 merge，移植后必须逐项核对本线特有内容未被覆盖」 |
| 读文件 / 取证 / 摸清现状 | 「你是一位专注的项目文件分析员，只做只读取证，输出结论 + `文件:行号` 证据，不做任何修改」 |
| 语言表 / 文案 / 术语 | 「你是一位 Minecraft 本地化（i18n）工程师，精通 lang/JSON 结构与术语一致性」 |
| 构建 / CI / 部署 | 「你是一位 Java 构建与 CI 工程师，精通 Gradle 与 GitHub Actions」 |
| 崩溃 / 报错排查 | 「你是一位 JVM 崩溃排查工程师，按日志-复现-根因-证据链作业」 |
| 功能审查（第 7 条⑤⑦） | 「你是一位独立功能审查员，按需求逐字对账 + 静态取证 + 守门脚本，逐条给 SUPPORTED / REFUTED + 逐字证据」 |
| CHANGELOG / 文档 / 手册 | 「你是一位技术文档工程师，与代码与提交事实逐字对账」 |
| 资源 / 模型 / Patchouli 手册 | 「你是一位 Minecraft 资源包与手册工程师，精通 JSON 结构与 texture/model 引用一致性」 |

**职业声明的三个作用**（派发时必须体现）：① **约束动作边界**（分析员只读不写、开发者必须编译取证、审查员不得改文件）；
② **约束质量标准**（把判据命令写进 brief）；③ **配合模型选择**（机械型 → `deepseek-flash`；确需深推理 → `deepseek-v4-pro` 并写明理由）。

⚠️ **已知缺陷（2026-10-16 实测 3 次）**：`spawn_agent(fork_turns="none")` 的 brief **投递不到子代理**
（子代理只收到环境上下文，会反问「请把 brief 发给我」）。应对：优先用默认 `fork_turns`；
子代理回报「没收到任务」时**最多重试 1 次**，仍失败则**如实上报用户**，禁止在主会话反复重派（每轮 ≈18 万 tokens）。
### C. 交接文档用法 —— 接手 / 交班都按这条走（2026-10-18 用户裁决）

**四层事实源，别搞混**：

| 文件 | 角色 | 能不能当现状 |
|---|---|---|
| `docs/handover-2.0.0-dev-next.md` | **开发线滚动交接正本**（§0 速览 / §3 批次表 / §4 实机读数 / §5 遗留 / §6 坑 / §7 路径） | 是，但**每次交班必须刷新** |
| `docs/handover-new-session-context.md` | 可整份贴给新会话的**寒暄提示词**（环境 / 先读哪几份 / 基本盘 / 立即待办） | 是，同步刷新 |
| `docs/agent/AGENTS-full.md` 末尾「待办交接各批」 | **逐批历史条目**（第 N 条 = 一批的完整事实 + 判据 + 坑） | 是，只追加不改旧条 |
| `docs/handover-<版本>-<主题>.md` | 单批详案（如 .97 筹码批） | 是，属该批详证 |
| ⛔ `docs/AGENT-HANDOFF.md` | multi-main @9825cf1e 的**历史快照**（工程拓扑/CI/发布链） | **不是**，只作背景，禁止据此下现状结论 |

**接手（新会话）顺序 —— 一律实读，不信文档里的数字**：
1. `git -C <工作树绝对路径> status --short -uall` + `git log -1 --oneline`（分支 / HEAD / 未提交改动）；
2. 四线 `gradle.properties` 的 `mod_version`（文档里的 .1xx 只是快照，可能已落后）；
3. 读正本 `docs/handover-2.0.0-dev-next.md` 的 §0 / §5，再按 §7 取路径；
4. 要某批细节 ⇒ 用主题名在 `docs/agent/AGENTS-full.md` 检索「待办交接」第 N 条（标题 + 行号总表见 `docs/agent/AGENTS-TOC.md`）。

**交班义务（收班前必须做完，缺一即不算交付）**：
1. 刷新正本 `docs/handover-2.0.0-dev-next.md`：header 快照（日期 / 分支 / HEAD / mod_version / 工作树残留）+ §0 速览 + §3 追加批次行 + §4 追加实机机器行原文 + §5 追加或销账遗留项 + §6 追加新坑 + §7 补关键路径；
2. 在 `docs/agent/AGENTS-full.md` 末尾「待办交接各批」**追加**一条（新条目，禁止改写旧条目）；
3. 同步 `AGENTS.md` 索引最后一行（尾部行号 = AGENTS-full 的行数），并跑 `python docs/agent/verify-split.py`，**四项全 [OK]** 才算过；
4. 同步刷新 `docs/handover-new-session-context.md` 的基本盘 / 已完成 / 立即待办；
5. 未实机的欠账、REFUTED 的规格差异（例：lingling 冷却 3600t=180s vs 规格 120s）、无法测度的判据（例：牌面「厚度边」在两面同图案后无像素差异）**如实登记**，禁止静默删；
6. 交接文档属**非代码改动** ⇒ 按第 7 条⑨a 只做 `dsh_code_review`，免子代理功能复验。

<!-- END 代理协作契约（2026-10-16 用户裁决） -->

> **本文件已拆分（2026-10-16 用户裁决：AGENTS.md 只保留「每次必读」红线 + 索引，原文不丢）**
> - **逐字全文快照**：`docs/agent/AGENTS-full.md` —— 等于拆分前的 AGENTS.md 原文（3,480 行），**逐字节一致**；下文所有行号都指该文件。
> - **定位命令**：`grep -n '^#' docs/agent/AGENTS-full.md`；标题 + 行号总表见 `docs/agent/AGENTS-TOC.md`。
> - **派活必须带规则**：子代理看不到本文件以外的规则 ⇒ brief 里要写「先读 `sed -n '<a>,<b>p' docs/agent/AGENTS-full.md`」（区间见下表）。
> - **交叉引用口径**：其它文件（脚本 / README / TESTING-SPEC 等）里写的「见 AGENTS.md〈X〉」，一律改到 `docs/agent/AGENTS-full.md` 里**按标题名检索**；标题文字未做任何改动。
> - **回滚**：`cp AGENTS.md.bak-slim-20261016-184259 AGENTS.md`（备份在仓库根；另有一份拆分前的原始备份 `AGENTS.md.bak-a-plan-*`）。
> - **自检**：`python3 docs/agent/verify-split.py`（只读，校验快照哈希 / 红线区逐字一致 / 索引无空洞）。

## 📍 生效红线区 —— 原文逐字（`docs/agent/AGENTS-full.md` L1–L120；原文 H1 由本文件 H1 取代）


> **子项目优先级与默认规则(必须遵守,2026-10-04 用户裁决重新划级)**:⛔ **本条原「三条生产线同批实施（含 26.1.2）」的口径已作废** —— 现行**两级优先模型**见下方「## 版本优先级与跨线移植纪律」:**P0 最高 = `neoforge-1.21.1` + `forge-1.20.1`**（功能一致、同批、优先）;**P1 次要 = `neoforge-26.1.2` + `fabric-1.20.1`**（待 P0 完成后移植）。以下为旧文存档 —— 所有功能/修复默认**同步修改三个版本**(`neoforge-1.21.1` + `forge-1.20.1` + `neoforge-26.1.2` —— 这三条是**生产线**),三线保持功能对等(无法对等的项必须按平台差异逐条登记,见 `docs/compat-26.1.2-neoforge.md`;**不再存在"只同步两个版本"的口径**);⚠️ **第四条线 `fabric-1.20.1`(Fabric 移植线)不在本「同批实施」约束内** —— 它由用户按批次单独下达移植任务,规则边界见下方「第四条线(`fabric-1.20.1`)的规则边界」;每次改动完成后由代理**自动本地提交**并**自动部署到整合包**(随 `gradlew build` 触发,**四条线各自推往自己的整合包**:`狐の航空学 Voxy Edition` / `1.20.1-Forge 模组测试` / `26.1.2-NeoForge 模组测试` / `1.20.1-Fabric 模组测试`),但**默认不执行 `git push`**。

项目基线:
- 主线子项目 `neoforge-1.21.1`:MC 1.21.1 / NeoForge 21.1.235 / Java 21 / ModDevGradle(`net.neoforged.moddev` 2.0.141)
- 移植子项目 `forge-1.20.1`:MC 1.20.1 / Forge 1.20.1-47.4.10 / Java 17 / ModDevGradle LegacyForge(`net.neoforged.moddev.legacyforge` 2.0.144)
- 第三条线 `neoforge-26.1.2`:MC 26.1.2 / NeoForge 26.1.2.109 / Java 25 / ModDevGradle 2.0.147;由 1.21.1 源码整体迁移,差异见 `docs/compat-26.1.2-neoforge.md`。**2026-09-17 起已并入主线目录**(原独立 worktree `F:\MCProject\astral_dice_multiloader-26.1.2` / 分支 `multi-26.1.2-neoforge` 已合并进 `multi-1.20.1-1.21.1`,现行工作目录即主线根目录,四子项目同树)。
- 第四条线 `fabric-1.20.1`:MC 1.20.1 / Fabric Loader 0.19.5 + Fabric API 0.92.12 / Java 17 / Fabric Loom(`fabric-loom`);由 `forge-1.20.1` 的 1.3.2-hotfix 源码整体移植(**加载器层整体替换**:Curios→Trinkets(+Accessories 软依赖) · Capability→Fabric API 附件 · Forge EventBus→自建 `LoaderBus`+Puzzles Lib/FAPI 回调/mixin · GLM→`LootTableEvents.MODIFY`),裁剪项与平台差异见 `porting/fabric-1.20.1/VERSION_PINS.md`、`KNOWN-ISSUES.md` §9。**2026-10-01 已并入主线目录**(原独立 worktree `F:\MCProject\astral_dice_multiloader_fabric` / 分支 `1.20.1-fabric` 已合并进 `multi-main` 并**已删除本地 worktree 与分支**（⚠️ **远端分支 `origin/1.20.1-fabric` 的删除需 push，尚未执行**，待用户放行；删除本地分支前已取证其 tip `a89487ac` 是 `multi-main` 的祖先、独有提交 0 个），四条线同一工作树)。⚠️ **本线 dev 环境有一个必须先做的前置**：Loom 重映射第三方 mod 时会剥离产物的 `fabric.mod.json` 的 `jars` 声明 ⇒ 自带内嵌库的 mod（Puzzles Lib / Accessories / Patchouli / Cloth Config / KubeJS）在 dev 里**全部起不来**（`HARD_DEP_NO_CANDIDATE` / `NoClassDefFoundError`）。跑任何 fabric dev 运行前先执行 `pwsh -NoProfile -File scripts/test/fabric/ft_env.ps1 --side both --install-embedded`（= `python tools/loom_embedded_jars.py --apply`，把内嵌库按 id 去重投放到 `run/<side>/mods/`，靠 Loom 的 `fabric.remapClasspathFile` 运行时重映射生效）；**换 `-PtestTrinkets` / `-PtestAccessories` 后必须重跑**。详见 `KNOWN-ISSUES.md` §9 **KI-F23**。
- Base package/group: com.merlinkitsune.astral_dice;各子项目产物名均为 `astral_dice-<版本>.jar`,版本号自带加载器后缀(1.21.1 带 `+neoforge_1.21.1`,1.20.1 带 `+forge_1.20.1`,26.1.2 带 `+neoforge_26.1.2`,fabric 1.20.1 带 `+fabric_1.20.1`,下划线分隔)。

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
   - 当前两个常驻工作树：**主线** `F:\MCProject\astral_dice_multiloader`（分支 `multi-main`，含**四条线** —— 三条生产线 + `fabric-1.20.1` 移植线，属**封包**状态）与 **2.0.0 开发线** `F:\MCProject\astral_dice_multiloader-next`（分支 `multi-dev-next`）。**fabric 的原独立工作树 `F:\MCProject\astral_dice_multiloader_fabric` 已于 2026-10-01 移除**（分支并入 `multi-main`，故本仓现只余上述两棵常驻树）。
   - dev 分支的任务（如目标选择器改造与其工具链改动）**只能**在 `-next` 工作树落地；主线的 `scripts/test/**` 等文件**必须保持封包版本**（改动若必须进主线，须由用户在主线工作树内单独裁决，不由 dev 任务顺带推送）。
   - 核验方式（收尾必做）：`git -C F:\MCProject\astral_dice_multiloader status --short --untracked-files=all` 除 `?? .agent-teams/**` 外**必须无输出**；`git -C F:\MCProject\astral_dice_multiloader rev-parse --short HEAD` 必须仍是收尾前的封包提交。**所有 git 命令一律带 `-C <工作树绝对路径>`**，不要依赖 cwd。
2. **相对路径陷阱（必须用绝对路径）**：.NET / `System.IO` / 多数文件 API 的**相对路径按进程 cwd 解析**，而本会话的进程 cwd 往往是**主线工作树** —— 于是「写一个 `scripts/test/cases/xxx.json`」会静默落到**主线**（实测事故：一条 0 字节用例文件落在主线，事后删除复原）。
   - 规则：跨工作树的任务**写文件一律用绝对路径**（`F:\MCProject\astral_dice_multiloader-next\...`），或先 `Set-Location` 到目标工作树再操作；`git` 命令用 `-C` 显式指定工作树；脚本内的输出路径同理（相对路径只在「已知 cwd 就是目标工作树」时可用）。
   - 自查：改动后对**另一个**工作树跑一次 `git status --short`，确认没有意外文件。

## 二次验证规范（Independent Second-Pass Verification）— 必须遵守（2026-10-01 用户裁决）

> ⚠️ **2026-10-08 用户裁决：复核执行方已改为 DSH 原生代码审查（2026-10-22 起执行工具 = `dsh_code_review`，插件 `@michengai/dsh-code-review`）** —— 本节的验收要求（独立证据、逐条对账、先取证后结论）仍然有效，但**不再派 subagent 做复核**；规则正本见〈子代理与测试环境硬规则〉第 4 条。

**任何非平凡改动，收尾前必须做一遍独立二次验证（2026-10-08 起复核执行方 = DSH 原生代码审查 `dsh_code_review` 一次性独立会话，正本见〈子代理与测试环境硬规则〉第 4 条）。** 目的**不是**「再读一遍」，
而是用**独立证据**去证伪主会话的结论：主会话的自述（「我已核对」「守门全绿」「已落盘」「已全线对等」）
**不能作为验收依据** —— 它可能就是幻觉或遗漏，且**自证天然有偏**（复用自己的假设去检查自己的结论）。

### 触发面（命中任一即必须做）

- 任何**代码 / 资源改动**（新增、修复、重构、批量替换）提交前；
- 任何**发布动作**之后（打 tag、建 Release、上传 CurseForge、部署到整合包）；
- 任何**批量或跨线**改动（三线同改、跨工作树、批量改 lang / 手册 / 配方 / 资源）；
- 任何**结论型交付**（审计报告、「该缺陷不存在」、「两线已对等」、「已全部覆盖」）；
- 用户明确要求复核时。

### 怎么派（brief 必须自包含 —— 子代理看不到本会话）

> ⚠️ 2026-10-08 起：复核统一改用 DSH 原生代码审查（一次性独立会话、brief 一次性写全）；本节「brief 自包含 / 禁喂主会话结论 / 要原文证据」的要求同样适用于喂给审查工具的输入。**2026-10-22 工具口径**：执行工具 = `dsh_code_review({ input })`（插件 `@michengai/dsh-code-review`）；`input` 非空 ⇒ 整段**原样**作为自定义审查要求（不解析 CLI 选项，`--base main` / `.` / `status` 都当普通文字），`input` 为空 ⇒ 打开原生范围选择（对比基准分支 / 未提交更改 / 某次提交 / 自定义要求）。⚠️ 故**范围必须写成自然语言**（例「审查工作树未提交改动，含未跟踪文件」「审查最近 1 个提交 `7e7cf978`」）；子 Agent 自行取 git 状态与差异，报告以 GFM 回到本会话，进度看 `/review-status`、取消用 `/review-cancel`。

- 🚫 **禁**把主会话的结论、改动清单、「我已经改好了」当**事实**喂给它 —— 那是诱导它附和，等于白做。
- ✅ **只给**：目标 / 涉及路径 / **期望的不变量** / **可独立执行的判据命令**（守门脚本、`grep -rn`、开包核验…）。
- ✅ **要求它**：① 先独立取证、后下结论；② 每条结论附**命令 + 原始输出片段**；③ **优先找反例**（差一处、漏一文件、范围跑错）；
  ④ 取不到证据就说 **「无法判定」**，禁止脑补；⑤ **空结论也要报告**（说明「查了什么」才没发现问题）。
- 只读查证用 `Explore`；需要跑命令 / 开 jar / 查远端用 `general-purpose`。

### 验收标准（子代理输出必须满足；主会话逐条回应）

1. 结论逐条挂**原始证据**，不是转述；
2. 覆盖主会话**声称改过的每一处**（数量、路径、内容），并与主会话的自述**对账**（逐项核对差异）；
3. 明确列出 **不一致 / 无法判定 / 未覆盖** 三类项；
4. 主会话**必须逐条回应**：认同 ⇒ 立即修；不认同 ⇒ 给出**反证**。**不得静默忽略**；
5. 子代理的结论本身也**不是事实** ⇒ 它的证据必须能被主会话**复算**（命令可重跑、输出可比对）。

### 与既有守门的关系（两层都要，不得互相替代）

- **第一层 = 机器判据**：`scripts/verify/**`、`tools/audit_*.py`、`tools/check_lang_sync.ps1` 等守门脚本，可重复、可回归。
- **第二层 = 二次验证**：专治第一层覆盖不到的遗漏 —— **判据本身有漏**（实例：手册悬空键
  `guide.entry.special_effects.6` / `guide.entry.teru_sign.3` 长期**无任何门禁**，直到 2026-10-01 才补上
  `tools/audit_patchouli_keys.py`）、**守门跑错范围 / 跑错工作树**、**报告与实物不符**、**声称已改而实际未落盘**。
- ⇒ **守门全绿 ≠ 已验收**；只做二次验证而跳过守门同样不合格。

## 子代理与测试环境硬规则（2026-10-08 用户裁决）— 必须遵守

1. **一个 subagent 只派一个任务，任务完成即销毁**：禁止把同一 teammate 复用于第二个任务、禁止在其上累积长上下文；
   每条任务一律**新建**一个 subagent，交付验收后不再向它派活（用完即弃）。⇒ brief 必须一次写全（自包含）。
2. **测试与演示一律使用非亡灵生物**：游戏内实机测试、连拍演示、探针靶子**不得**用僵尸等亡灵生物（undead），
   一律改用**非亡灵**生物（如 minecraft:pillager、minecraft:cow）。原因：亡灵受治疗 / 伤害反转等特殊规则影响，
   会污染 FX / 伤害 / 音效类判据；历史脚本中以 zombie 为靶的写法一律视为待替换。
3. **测试流程结束后必须关闭测试用游戏实例**：收尾时执行 pwsh -File scripts/test/mt.ps1 --version 线 --phase stop，
   确认 MT_STOP: OK 且无本流程 java 客户端残留（gradle 守护进程保留属正常）；禁止让客户端无人观察地驻留 ——
   它会持续占用 CPU / GPU、污染下一次冷启动前置状态，并让 enableShaders 之类的运行期状态被意外持久化。
   **收尾一律带 `--purge-saves`**（2026-10-08 用户裁决：每轮实机验证后清理旧存档）：标准收尾命令 = `pwsh -File scripts/test/mt.ps1 --version <线> --phase stop --purge-saves`，成功判据 = `MT_CLEANUP_SAVES: PURGED <n>` + `MT_CLEANUP_RESULT: OK`；`.mt_keep_alive` 存在时只提示、保留取证现场。收尾同时**删除本轮注入的临时探针**（`run/<线>/kubejs/**` 的一次性测试脚本，以及源码侧 `scripts/test/resources/kubejs/**` 的临时探针），并在交付报告里贴 `git status --short -uall` 原文（2026-10-11 用户交接裁决）。

4. **二次验证（复核）一律改用 DSH 原生代码审查（插件 `@michengai/dsh-code-review`，工具 `dsh_code_review`），不再派 subagent / teammate**：每次复核 = **一次性独立会话、不复用**；
   调用 `dsh_code_review({ input })` 把「复核对象（提交或文件范围，用自然语言写清）+ 背景 + 逐项判据（R1..Rn）+ 输出要求（判定 SUPPORTED / REFUTED / 部分成立 + 逐字证据 文件:行号 + 未验证项 + 最小修法）」**一次性写全**进 `input`（会话干净，不给它沿用上下文的机会；该工具不解析 CLI 选项）。代码修改仍按第 1 条（每任务一个新 subagent、单任务、用完即弃）；复核不再由 subagent 承担。 ⚠️ **区分两件事**：本条约束的是**复核（二次验证，执行方 = `dsh_code_review`）**；**功能审查 / 功能验证**（静态取证、`:<线>:classes` 编译、`scripts/verify/**` 守门脚本、与需求逐字对账）自 2026-10-12 起按第 7 条**由一次性独立子代理**执行 —— 两者不得互相替代（**复核不派 subagent，功能审查必须派 subagent**）。

5. **实机验证必须由用户在提示词中明确下达**（2026-10-08 用户裁决）：默认情况下 agent 只做**代码功能验证** —— 静态取证与逐字对账、`:<线>:classes` 编译、`dsh_code_review`、`scripts/verify/**` 守门脚本、用例 JSON 与文档的一致性/行尾核对；**不得**自行启动 dev 客户端跑实机、连拍、探针或用例。用户明确要求实机时才执行，且收尾按第 3 条带 `--purge-saves`；未实机的欠账如实登记在交付报告里，等用户点名下指令再补。

6. **改动后旧实机证据一律作废，先停旧实例再测新构建（2026-10-11 用户交接裁决）**：任何代码 / 资源改动落地后，此前一切实机读数与截图立即失效，不得作为新构建的验收依据；测新构建前必须先 `mt.ps1 --phase stop` 停掉旧实例（禁止在旧实例上叠加新构建 —— `run/<线>/mods` 的 jar 会被旧进程持有且内容是旧代码）。
7. **开发操作流程（2026-10-12 用户裁决，取代 2026-10-11 版本；同日二次裁决修订②③⑤⑥⑧：所有代码编写一律通过向 Subagent 下达写代码命令完成，主 Agent 只负责规划 / 派活 / 验收 / 提交，不亲自编写任何代码）**：① 新功能先写**开发需求 / 设计计划**；② **P0 基准线 `neoforge-1.21.1` 的实现派子代理编写**（一次性、单任务、brief 自包含；主 Agent 不亲自写主线实现）；③ 子代理交付后由主 Agent 验收并按需自测（`:<线>:classes` 编译 / `scripts/verify/**` 守门脚本）；④ 完成后用 `dsh_code_review` 复核；⑤ 复核问题派新子代理修完后，**派子代理做独立功能审查**（静态取证、编译、守门脚本、与需求逐字对账）；⑥ 主 Agent 逐条回应审查结论、缺陷**派新子代理修复**，**全部确认无误后**才派子代理做 `forge-1.20.1` 移植（按 `docs/compat-1.20.1-forge.md` 差异映射）；⑦ 移植线再走一次 `dsh_code_review` 复核 + **另派一个独立子代理**做功能审查；⑧ 子代理派发一律**优先派给 DeepSeek**（功能实现用 **DeepSeek V41 Flash**），其它模型仅作备用，仍受第 1 条「一个子代理一个任务、用完即弃」约束；复核 / 审查发现的问题交**新**子代理改进，**连续 3 次仍改不到位 ⇒ 报用户拍板**。⑨ **复核分级（2026-10-13 用户裁决）**：a) **非代码改动**（文档 / 规则 / 版本号 / CHANGELOG / 配置注释等）只做 `dsh_code_review`，免子代理功能复验；b) **小规模代码修正**（口径 / 字符串 / 数值 / 显示内容等简单修改）只做 `dsh_code_review`，除非用户另行下达指令，不执行子代理功能复验；c) **新功能请求**：`dsh_code_review` 后由主 Agent 按改动规模自判功能复验范围（全量 / 部分 / 不做），判断依据如实写进交付报告；d) **大型功能变更或新增**执行**全量**功能复核；e) **实机测试仍须用户明确许可**——无法仅靠功能复核确认的结果，必须向用户说明情况并请求其下达实机测试。**代码与二进制都无法确认是否符合需求时，必须询问用户是否实机测试**（用户明确下达才做，流程见第 5 条，收尾按第 3 条）；必须用户决策的问题或难以实现的功能**交用户拍板**，不得擅自取舍。

## 命令执行规范（Shell）— 必须遵守（2026-09-19 用户裁决）

**一律使用 PowerShell 7（`pwsh`）；禁止使用 Windows PowerShell 5.1。**

- **背景（实测）**：本代理工具的默认 shell 是 **Windows PowerShell 5.1**（`$PSVersionTable.PSVersion` = 5.1.x），而本仓全部工具链脚本与 AGENTS 示例均按 **pwsh 7**（当前 7.6.6）编写。5.1 下的差异会**静默改变行为或直接报错**：① `if` / `switch` **不能作表达式**（`(if ($x) { … } else { … })` 报「术语 'if' 不会被识别为 cmdlet…」，实测踩中）；② 不支持三元 `? :`、`??`、`?.`；③ `ConvertFrom-Json -AsHashtable`、`-Depth`、`Test-Json` 等参数缺失或语义不同；④ 默认编码、`$PSNativeCommandUseErrorActionPreference`、`&&`/`||` 等行为不同。
- **规则**：
  1. 多行或含 `if` 表达式 / 三元 / PS7 专用参数的命令，**一律落盘为 `.ps1` 再用 pwsh 跑**：`pwsh -NoProfile -File <绝对路径>`（脚本放 `temp/tNN/`，绝对路径，见「工作树与路径纪律」）。
  2. 单行命令用 `pwsh -NoProfile -Command '…'`。
  3. **禁止**直接用工具默认 shell 跑 PowerShell 代码（尤其上述语法）。
  4. 调用本仓脚本（`scripts/test/*.ps1`、`tools/*.ps1`、`scripts/devtools/*.ps1`、`scripts/maintenance/*.ps1`）**必须**显式写 `pwsh -NoProfile -File …`；文档与交付说明里的示例同样写 `pwsh`。
   5. **批处理（.bat）空参必须守卫（2026-10-11 用户交接裁决）**：`for %%A in (%*) do (` 在无参时会展开成 `for %%A in () do (` ⇒ 语法错误、窗口闪退；解析参数前一律先 `if not "%~1"==""` 守卫。
   6. **手动启动脚本只做启动，禁止前置判断（2026-10-11 用户交接裁决）**：`run/Start-<线>.bat`（用户双击自测）= `chcp 65001` → `cd` → `gradlew :<线>:runClient` → `pause` 六行；**禁止**加版本号比对等任何前置判断（曾比对 `gradle.properties` 引脚与 `mods` 内 jar 名 ⇒ 每次升版都 FAIL，用户裁决只保留启动）。
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

<!-- END 生效红线区（原文逐字，AGENTS-full.md L1–L120） -->

## 📇 规则索引 —— 其余原文的位置（`docs/agent/AGENTS-full.md`）

| 行号区间 | 主题（原文标题摘引） | 何时必读（触发条件） |
|---|---|---|
| L121–L206 | 第三方联动取源与反射纪律 · 第三方模组源码核验 · 嵌套松散引用「消失」 | 取源/反射第三方模组或库、或遇到「引用消失」现象前 |
| L207–L236 | Gradle 构建守护规则（Gradlew Watchdog） | 跑任何 `gradlew` 构建/部署前 |
| L237–L531 | 多版本子项目矩阵（含 starengine_lib 版本与兼容性契约、26.1.2 关键差异速记） | 改动跨版本、或动前置库版本前 |
| L532–L582 | 版本优先级与跨线移植纪律（两级优先模型 / P0 三类差异 / 联动口径 / 逐项对照清单 / 沿革） | 任何改动的定级与落地顺序 |
| L583–L613 | 平衡性红线 | 改数值 / 卡牌强度 / 掉落前 |
| L614–L657 | 模组依赖添加规则（统一口径，1.20.1 + 1.21.1） | 新增第三方模组依赖前 |
| L658–L700 | 版本互通门槛（Version Gate）· 版本号递增约定 | 改版本号 / 互通判断前 |
| L701–L809 | 更新日志约定 · 子项目修改默认规则 · 26.1.2 迁移清单与批次记录 · forge-1.20.1 差异速记 | 写 CHANGELOG、做移植前 |
| L810–L942 | 包结构规范（Package Layout）· 物品一览（材料/骰子/战斗牌/效果牌/立牌/筹码） | 新增或改名任何物品前 |
| L943–L1136 | 立牌命名规范（id 对照表 / 技能一览 / 品质 / 风水师契约）· 效果牌命名与结构规范 · 活体书页命名 | 新增立牌 / 效果牌前 |
| L1137–L1310 | 筹码一览 · 筹码配方规范 · 星光/治愈/标记/充能各类筹码 | 新增筹码前 |
| L1311–L1569 | 治愈 / 星光 / 反击 / 充能 流派规范 · 目标选择器规范 · 立牌 tooltip 格式规范 | 改流派机制、目标选择、tooltip 前 |
| L1570–L1842 | tooltip 文案唯一基准 · 动作栏（ActionBar）口径 · 语言文件同步规范 · 骰子槽位与配置规范 | 改文案 / 语言表 / 配置前 |
| L1843–L2067 | 星币钱包规范 · Bountiful 赏金联动 · 战利品箱初次赠礼与战利品注入 | 经济 / 联动 / 战利品相关改动前 |
| L2068–L2197 | 骰战闪避与防御规范 · 物品图标约定 · 帕秋莉手册同步规范 · 神秘遗物(+) 联动适配 | 战斗口径 / 资源 / 手册改动前 |
| L2198–L2308 | Mixin 注入规范 · 编译产物上传规则（新版本发布 / CurseForge / Modrinth） | 写 mixin、发布前 |
| L2309–L2909 | 自动化测试流程（资产重建 / 变更分级 / 测试顺序 / 清场与收尾 / 工具链 / 阶段 P·B·E·L·C·R / 截图识别 / MCP / 超时看门狗） | 任何游戏内测试前（**必读**） |
| L2910–L3046 | NeoForge 上游 BUG 补丁（neoforge_fixes）· 「敌对目标」判定规范 · `/astralparty` 管理员调试命令 | 上游 bug / 敌对判定 / 调试命令前 |
| L3047–L3541 | 版本历史与发布记录 · 待办交接各批（fengshui / teru / 新立牌批 / Carpet / 双人测试流程 / 遗留项 / 立牌批验证闭环 + 选卡界面 + Jade 倒计时 + 工具链回 Windows 原生 + AutoJS 求值通道 / lingling 用例 / 强化拒止牌面 / 欠账角色立牌 / DSH 原生批次（2026-10-22：规则 Windows 化 + 守门假红修复 + 立牌冷却补 max）/ WSL 口径失效对照（2026-10-22，末尾小节）/ 代码审查工具链换插件（2026-10-22，第 42 条节）） | 续做历史批次时 |

**维护约定**
1. 新增「每次必读」的红线 ⇒ 直接写进上面的红线区（并同步到 `docs/agent/AGENTS-full.md` 的对应章节，保持两边一致）。
2. 新增「情境规则 / 知识 / 历史记录」⇒ **追加到 `docs/agent/AGENTS-full.md`**（不要写进本文件的索引之后），并更新上表分区行。
3. **本文件不要再涨过 ~30K 字符**：实测本仓会话首次请求总输入只有 2.4–3.7 万 tokens，超出部分不会进入上下文（这正是本次拆分的原因）。
4. 长驻规则（每次都需要的）以外的内容一律走「索引 + 按需读取」，读取动作**由子代理执行**，不进主代理上下文。
