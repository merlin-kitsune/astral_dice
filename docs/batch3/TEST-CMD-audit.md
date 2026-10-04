# TEST-CMD — 把 `/astralparty` 调试命令引入测试流程的可行性审计

- 审计者：测试流程审计者（只读审计，**未改动任何文件**；本文件是本次唯一写入物）
- 仓库：`F:\MCProject\astral_dice_multiloader`，分支 `multi-1.20.1-1.21.1`，HEAD `7b9e711`
- 审计时点：**2026-09-15 19:45:24 → 19:47:37**（下称「读取时点」）
- 硬约束遵守情况：只写 `docs/batch3/TEST-CMD-audit.md`；未碰源码 / `scripts/test/**` / lang / CHANGELOG / `AGENTS.md`；未 `git commit`/`push`；未构建；未启动游戏。

---

## 0. 读取时点状态（重要：工作区正处于双执行者并发写中间态）

### 0.1 两个执行者同时在写，且写的是**不同**的目录

| 时点 | `git status --porcelain` 内容 |
|---|---|
| **19:45:24**（审计开始） | 仅 `scripts/test/**`（8 个 M + 4 个 ??）——**产品源码干净** |
| **19:47:37**（审计中段） | 上一行 **加上** `neoforge-1.21.1/src/**` 2 个 M + `forge-1.20.1/src/**` 2 个 M |

⇒ 19:45 到 19:47 这两分钟内，**另一执行者开始改产品源码**（即 `/astralparty` 实现者）。两个执行者的写域目前不重叠：
- 执行者 A（B2 测试资产）：`scripts/test/**`（见 `docs/batch3/B2-tests-fix.md`，该文件自称硬约束「只动测试资产…产品源码一律未动」）
- 执行者 B（`/astralparty` 实现者）：`*/src/**`

**但两者的意图是交叉的**：A 正在改「测试如何准备状态」，B 正在提供「新的状态准备手段」。第 5 节的串行建议据此给出。

### 0.2 `/astralparty` 的实现进度（读取时点实况）

| 已落地 | 证据 |
|---|---|
| `EffectCardPeriod.effectPendingEffects()` — 「效果牌施加的效果」权威清单，`List.copyOf` 只读、`effect()` 去重 | 工作区新增（**不在 HEAD**：`git show HEAD:…EffectCardPeriod.java` 检索 `effectPendingEffects` 为**空**）；javadoc 明写「供调试命令 `/astralparty clearcardeffect` 使用」 |
| `ModEffects` 的「本模组已注册全部效果的只读视图（调试命令 `/astralparty cleareffect` 用）」 | 同上（HEAD 无） |
| 两版本各 +12 / +20 行 | `git diff --stat` |

| **未落地** | 证据 |
|---|---|
| **命令注册本体**（`Commands.literal("astralparty")`） | 全 `*/src/**` 检索 `astralparty` **只命中上述两处 javadoc**；检索 `Commands.literal("astralparty")` / `RegisterCommandsEvent` / `CommandRegistrationEvent` **0 命中** |

⇒ **结论：读取时点只有「只读访问器」这一层落地，命令本身尚不存在。** 本审计第 2/3 节的替代方案均以「命令按冻结规格实现后」为前提。

### 0.3 并发运行态（审计者**未干预**）

- **Minecraft 客户端正在运行**：PID 5152 = `net.minecraft.client.main.Main`（另有 gradle wrapper PID 18708、daemon PID 13540）。⇒ 执行者 A 很可能正在实跑。
- `run/1.21.1/kubejs/server_scripts/astral_bugfix_probe.js` = 19:43:38，与源文件同秒 ⇒ 刚被 A4 的 `Sync-MtProbeScripts` 部署或手工复制。
- `cases/.mt_run_state.json`（19:39:45）记录最近一轮 `run_id=20260915-091733`：1.21.1 `cases` = **FAIL**（`ANVIL-STAR-UPGRADE-1.21.1` FAIL、`NANCY-LU-PEARL-IMMUNE-1.21.1` FAIL、`ENDER-DICE-TOTEM-GLOWING-1.21.1` FAIL），1.20.1 = **GATED**；其中 `.mt_run_state` / `.mt_snapshot` 各记 **ERROR**（证明该轮**早于** A3 点文件过滤修复生效）。
- `cases/.mt_snapshot.json` 写于 **19:45:06**（审计开始前 18 秒）⇒ 又一轮正在跑。

### 0.4 半成品标注（按任务要求逐项标注）

| 文件 | `LastWriteTime` | 判断 |
|---|---|---|
| `resources/kubejs/1.21.1/server_scripts/astral_bugfix_probe.js` | 09-15 **19:43:38**（3344 行） | **改动中**：`git diff` 显示 +454 行。`B2-tests-fix.md` 声称的 C0/C1/C4 改动**均已可见**（`Vec3Class:71`、`ANGER_NBT:2176`、`gloveround:2895/3333`、`endertotem:3042/3338`）⇒ 该文件内容与 B2 文档**基本一致**，非半成品。但 B2 文档给的**行号已全面失真**（见下） |
| `resources/kubejs/1.20.1/server_scripts/astral_bugfix_probe.js` | **19:44:04**（3398 行） | 同上，+431 行 |
| `mt.ps1` 19:23:04 / `mt_case.ps1` 19:23:02 / `mt_launch.ps1` 19:22:45 | — | A1/A2/A3/A4 四项**均已落地**（`mt_case.ps1:716` 点文件过滤、`mt_launch.ps1:44` `Sync-MtProbeScripts` 均在） |
| `cases/*.json`（ANVIL/HOSTILE）19:30–19:34 | — | C0 与 ANVIL 的用例改动已落地 |
| **`docs/batch3/B2-tests-fix.md`**（19:35） | — | ⚠️ **半成品**：末节「## 任务 D — 实跑验证」正文只有一行「（见下节，逐项补录）」，**文件在第 254 行结束，没有 D 节内容** ⇒ 作者仍在写。且其中的行号引用已过期（例：C0 称 `doRailgunFriendly` 在「1.21.1 `:2028-2040` 区」，实际读取时点该函数在 **:2063**、`:2030` 是 `doEffectCardLock`；C1 称 `doGloveRound`「`:2791-2917` 区」，实际在 **:2895-3002**） |

> ⚠️ **所有探针行号均以「读取时点」为准**。执行者 A/B 仍在写，行号会继续漂移；引用时请以符号名（函数名）为准、行号只作定位线索。

### 0.5 ⚠️ 读取时点**之后**发生的重大变化（19:49:27–19:51，审计已收尾但为诚实起见补记）

**§0.2 的结论「命令本体未落地」在读取时点之后 2 分钟即失效**。审计末尾复核 `git status` 时发现：

| 新变化 | 证据 |
|---|---|
| **`/astralparty` 命令本体已落地**（两侧各 300 / 301 行） | `neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/command/AstralPartyCommand.java`（**19:49:27**）、`forge-1.20.1/.../command/AstralPartyCommand.java`（**19:49:30**，均 `??` 未纳管） |
| lang 键（两侧） | `*/src/main/resources/assets/astral_dice/lang/{en_us,zh_cn}.json` 均被修改 |
| CHANGELOG / AGENTS.md 亦被修改 | `git status` 新增 `CHANGELOG.md`、`CHANGELOG_zh.md`、`AGENTS.md` |
| 另外两条用例被改（`NANCY-LU-PEARL-IMMUNE-*`） | A 在继续修 §1.6-② 的探针缺陷 |

**已可逐条核对的部分（对本次审计的交叉验证）**：

1. **权限形状与冻结规格一致**：根字面量与每个子命令**各自**都写了 `requires(AstralPartyCommand::hasPermission)`（`:97`、`:99`、`:104`、`:118`），实现 `source.hasPermission(REQUIRED_PERMISSION_LEVEL)`（`:124-125`）。⇒ **与 §4.6 的红线 4 相符**；且 javadoc 自陈「**没有** dev/测试专用开关」（`:58`）。
2. **别名同实现**：`RESET_LOCK_ALIASES = { "resetcardcolddown", "resetcardcooldown" }`（`:89`），根节点复用 `resetCardLockNode`（`:111`）⇒ 不存在第二份逻辑。
3. **`clearcardeffect` 确以 `EFFECT_PENDING_SOURCES` 为权威**：取 `EffectCardPeriod.effectPendingEffects()`（`:188`），并明确**不清**立牌主动效果与效果牌的原版 rider（`:183-185`）。
4. **`resetcardlock` 确走 `forceResetRound`**（`:216`），且**明确不做 ③ / 不清 `sign_active_lock_*` / 不清 `sign_ready_*` / 不补 `onRoundFullyReset`**（`:205-207`）。
   ⇒ **本次审计 §2.1-S1 的独立推断被实现注释证实**：`:209-212` 自己写明 `forceResetRound` **含 `ElectricGloveChipItem#disarmAoe`**「无条件写 false」——正是 §2.1-S1 指出的那项**额外副作用**。
5. **效果移除确走内部通道**：`removeModEffect` 经 `ModEffectRemoval.remove`（`:241`），其 javadoc（`:229-232`）引用的是**同一处**证据（`ModEffectEvents.java:111-131`）并指出直接 `removeEffect` 会让命令**完全无效** ⇒ 与 §1.6-⑧ / §2.1-S5 一致。

**审计未覆盖、因落地后才出现而需补核的一项**：`clearCompanionState`（`:254+`）——命令在清除效果之外**还会清理「同生共死」的耦合状态**（例如 `FATE_GUIDANCE` → `setFateActiveUntil(0)`、`WEAK_MARK` → 来源归属），javadoc 声明**不动**玩家资源与进度计数器（`healing_points` / `healing_timer_end` / `investigation_stage` / `cursed_sword_*` / `empower_decay_at`）。
⇒ **对 §5.5-3 的影响**：`cleareffect` / `clearcardeffect` 的**实际语义是「效果 + 耦合状态」的超集**，比本审计 §2.1-S5 的「清效果」更强。**逐用例核对时必须把耦合状态一并纳入**（例如 `EFFECT-DECAY-FLICKER` 依赖 `healing_timer_end` 构造闪烁窗口——若 `cleareffect` 顺手清掉它，前置会被破坏）。该判据的裁决依据在执行者 B 自己的 `docs/batch3/CMD-progress.md` §5。

**对第 5 节落地顺序的影响**：§5.2 的 **P1（命令落地）与 P2（两侧构建）的前置已具备**，但**仍需构建验证**（本审计未构建，硬约束禁止）；P3 及之后的步骤才可启动。

---

## 1. 现有测试流程的复杂度盘点

### 1.1 阶段机（`mt.ps1`）

`mt.ps1` 是唯一入口（`scripts/test/mt.ps1:1-46` 文件头）。两个版本**顺序**执行、**带门控**（1.21.1 不通过则 1.20.1 记 `GATED`）：`mt.ps1:374-429`（`$gateOpen` 于 `:427` 置 false）。

阶段 = `preflight | build | env | launch | cases | report | stop`，分派在 `Invoke-MtRunPhase`：

| 阶段 | 实现 | 行号 |
|---|---|---|
| `preflight` | `mt_preflight.ps1 --all` | `mt.ps1:362` |
| `build` | `mt_build.ps1` | `mt.ps1:210-212` |
| `env` | `mt_env.ps1 mods` → `mt_env.ps1 world [--seed]` | `mt.ps1:213-225` |
| `launch` | `mt_launch.ps1`，**必须脱离执行**（`-Detached`） | `mt.ps1:226-230` + `Invoke-MtChild:95-142` |
| `cases` | `mt_case.ps1 run --case X` 或 `run-dir` | `mt.ps1:231-236` |
| `report` | `mt_report.ps1 collect` | `mt.ps1:237-239` |
| `stop` | `mt_stop.ps1` | `mt.ps1:329-335` |

两个与「测试复杂度」直接相关的结构事实：
1. **launch 只能脱离执行**：`Start-Process -Wait` 等的是**整棵进程树**，而 launch 留下客户端作为后代 ⇒ 用 `-Wait` 必然阻塞到客户端退出（`mt.ps1:66-82` 记录 B1 实测根因；上游缺陷见 `docs/batch3/B1-in-game-results.md` ①）。
2. **断言只读「快照之后的增量」**：`launch` 收尾写快照（`mt.ps1:405-407`，A2 后 `mt_launch.ps1` 收尾也写），`mt_assert.ps1:12` 定义 `snapshot`。**单阶段 `--phase launch` 曾不写快照 ⇒ 静默用陈旧字节偏移判「未命中」⇒ 假 FAIL**（B1 ⑥-2 实测：`RAILGUN-PET-EXCLUDE` 首轮假 FAIL，复跑 26/26）。

### 1.2 用例与步骤机制

`cases/*.json` 的结构 = `steps[]`（**动作**）+ 独立的 `asserts[]`（**断言**）。二者**分开存放**，`steps` 里没有 `op:"assert"`。

**步骤类型（`Invoke-MtCaseOp`，`mt_case.ps1:547-634`）——共 6 种：**

| `op` | 语义 | 行号 | 全库用量 |
|---|---|---|---|
| `inject_key` | 注入**单个按键**（`mt_inject.ps1 key`），可 `hold_ms` | `mt_case.ps1:565-574` | **0**（35 条用例无一使用） |
| `inject_command` | 注入**聊天栏命令**（`mt_inject.ps1 cmd`），固定后置 `Start-Sleep 600ms` | `mt_case.ps1:576-586`（后置 sleep 在 `:583`） | **153** |
| `kubejs_reload` | `/kubejs reload server-scripts` + `/reload`，共固定 3.0 s | `mt_case.ps1:588-596` | **0** |
| `wait` | `Start-Sleep -Milliseconds ms`（默认 500） | `mt_case.ps1:598-602` | **158** |
| `screenshot` | `mt_capture.ps1 capture --tag --mode[f2/window] [--crop-to-window]` | `mt_case.ps1:604-619` | **8** |
| `note` | 纯注释（`NOTE`，不产生结果） | `mt_case.ps1:625-631` | **87** |

**断言类型（`Invoke-MtCaseAssert`，`mt_case.ps1:457-544`）——共 6 种，全部是「对日志/环境做正则或存在性检查」，没有一种能读游戏内状态：**

| `type` | 语义 | 行号 | 全库用量 |
|---|---|---|---|
| `log` | 增量区间内**必须**匹配正则 | `mt_case.ps1:471-484`（`scope=whole` 时加 `--no-snapshot` 对整文件求值，`:479-481`） | **406** |
| `absent` | 增量区间内**不得**匹配 | `mt_case.ps1:485-491` | **40** |
| `crash` | 增量区间内无崩溃报告 | `mt_case.ps1:492-495` | **33** |
| `kubejs` | KubeJS `server.log` 为 0 errors（exit 11 ⇒ `BLOCKED`） | `mt_case.ps1:496-500` | **33** |
| `mixin` | 增量区间内无 Mixin 应用失败 | `mt_case.ps1:501-504` | **3** |
| `vision` | **不自动判定**，返回 `DELEGATED` + 截图路径给人/模型看 | `mt_case.ps1:505-542` | **0** |

退出码语义（`Get-MtVerdict`，`mt_case.ps1:425-454`）：`0=PASS`、`1=FAIL`、`2=ERROR`；`BLOCKED` 单列。`mt_assert.ps1:12-17` 是权威子命令清单。

> **关键结构结论**：**PowerShell 侧完全没有「读游戏内状态」的能力**。「日志里有没有某行」是唯一断言手段 ⇒ 一切状态读取都必须由**探针主动打印成 `AP_*` 行**，再由 `log`/`absent` 正则匹配。这是整个工具链复杂度的**根源**，也是第 3 节提案的立足点。

### 1.3 逐条用例量化

**读取时点磁盘上有 35 个用例 JSON**：git 纳管 **31** 个（`git ls-files scripts/test/cases/` = 31），工作区另有 **4** 个 `??` 新增（`ELECTRIC-GLOVE-ROUND-RESET-*`、`ENDER-DICE-TOTEM-GLOWING-*`，各两版本）。任务书所载「33 个」与读取时点不符——**33 应为更早时点的计数**；另有 2 个点文件（`.mt_run_state.json` / `.mt_snapshot.json`）**不是用例**（A3 修复前被误当用例，见 1.4-⑤）。

| 指标 | 合计 | 单条平均 |
|---|---|---|
| 用例数 | 35 | — |
| 步骤数 | **406** | 11.6 |
| 其中 `inject_command` | **153** | 4.4 |
| 其中 `/astralprobe …` | **131**（85.6%） | 3.7 |
| 其中**原版命令** | **22**（`/effect clear @s`×4、`/gamerule`×8、`/fill`×8、`/data get`×2） | 0.6 |
| 其中 `wait` | **158** | 4.5 |
| `wait` 纯睡眠总量 | **150.2 s** | 4.3 s |
| 其中 `note` | **87** | 2.5 |
| 其中 `screenshot` | **8** | 0.23 |
| `inject_key` / `kubejs_reload` | **0 / 0** | 0 |
| 断言总数 | **515** | 14.7 |

**探针命令复用度**：全库共 **27 个不同的 `/astralprobe` 子命令**（`airbag*`6、`anvil*`3、`komachi*`6、`nancy*`4、`railgunfriendly*`3、`fensplash*`3、`decay*`2、`spelltd*`2、`railtruedmg*`2、`attack`、`blastbonus`、`diag`、`emeraldtrade`、`equipslot`、`gloveround`、`endertotem`、`tradeclose`、`truedmg`）。命令注册源码位置：`astral_bugfix_probe.js:3101-3344`（`ServerEvents.commandRegistry`），44 个 `do*` 处理函数。

### 1.4 单条用例耗时量级（**这是复杂度的真实度量**）

注入一条命令的**结构性下限**可从源码直接推出（`sendinput` 为默认通道，`mt_inject.ps1:466`）：

`Invoke-MtInjectCmdCommand`（`mt_inject.ps1:432-529`）固定序列 = **Esc 350ms → Tab 150ms → Enter 450ms → T 800ms → 文本 → 300ms → Enter**，显式停顿合计 **2 050 ms**（`:498/500/502/505/525`）；再加 `Send-MtInjectKey` 的按下/抬起开销（`:207-217`）与 `mt_case.ps1:583` 的后置 **600 ms**：

> **每条 `inject_command` ≈ 2.65 s 起**（不含游戏侧响应）。与 B1 实测「注入器每条命令的固定按键序列约 **2.5–3 s**」（`B1-in-game-results.md:121`、`B2-tests-fix.md:126`）一致。

据此按 `inject×2.65 + wait + shot×6` 估算（脚本另计），**全套 35 条 ≈ 602 s ≈ 10.0 分钟**纯注入+等待（不含 build/env/launch/报告）。最重的 5 条：

| 用例 | 步骤 | 注入 | 截图 | wait | 估算 |
|---|---|---|---|---|---|
| `RAILGUN-PET-EXCLUDE-*` | 20 | 8 | 0 | 6.4 s | **28 s** |
| `ANVIL-STAR-UPGRADE-*` | 15 | 5 | 1 | 7.0 s | **26 s** |
| `NANCY-LU-CLOAK-*` | 14 | 6 | 1 | 4.4 s | **26 s** |
| `HOSTILE-TARGET-NEUTRAL-*` | 19 | 7 | 0 | 6.8 s | **25 s** |
| `RAILGUN-OVERRIDE-CLASS-*` / `RAILGUN-AOE-SCOPE-*` | 17 / 18 | 7 / 7 | 0 | 5.9 s | **24 s** |

最轻的：`LOADER-GATE-FORGE-1.20.1`（7 步 0 注入，纯日志断言）、`SMOKE-TOOLCHAIN`（2 步）。

### 1.5 KubeJS 探针当前承担的职责（逐类）

探针 `astral_bugfix_probe.js`（1.21.1：**3344 行 / 184.7 KB**；1.20.1：**3398 行**）**不是**「测试助手」，而是**测试的主体**：它同时承担状态准备、动作触发、读数、断言、时序五类职责。分类如下（行号 = 读取时点 1.21.1 版）：

**A. 状态准备（state prep）**

| 子类 | 真实符号 | 行号 | 用量 |
|---|---|---|---|
| Curios 槽位清空 | `clearCurioSlots` | `:1159-1165` | **20** 处调用 |
| Curios 槽位放置 | `putInSlot` / `ensureChipSlot` / `equipSign` | `:289-300` / `:260-271` / `:1168-1172` | 12 / 7 / — |
| **出牌周期 + 立牌锁定态归零** | `resetEffectCardCycle` | `:1179-1190` | **13** 处调用 |
| 立牌锁定态清空 | `resetActiveLock` | `:1193-1199` | 3 处（另被上一函数 `:1186` 内调） |
| 附件直写 | `ModAttachments.set*` | 散布全文件 | **74** 处 |
| 效果移除 | `ModEffectRemoval.remove` / `p.removeEffect(原版)` | 散布 | **17 / 9** 处 |
| 「临时出牌数来源」效果摘除 | `clearExtraPlayEffects` | `:1224-1227` | 1 处定义 |
| 效果施加 | `p.addEffect(new MobEffectInstanceClass(...))` | 如 `:649-654`（一次挂 4 个法伤牌加成）、`:1406-1407`、`:1582`、`:3060-3062` | 18 处 |
| 玩家状态 | `setGameMode(SURVIVAL)` / `setHealth(max)` / `ChargeManager.removeAll+addStacks(p,6)` | `:2072` / `:2073` / `:2076-2077` | — |
| 世界 | `p.level.setWeatherParameters(...)` | `:2068` | — |
| 实体摆位 | `spawnDummy` / `placeAt` / `aimPositiveZ` | `:339-359` / `:309-323` / `:324-338` | — |
| 界面打开/关闭 | `anvilOpenMenu` / `anvilCloseMenu` / `spawnTrader`+`openTradingScreen` | `:801-843` / `:853-870` / `:532-544`+`:605` | — |

**B. 动作触发（action trigger）**

| 子类 | 符号 | 行号 |
|---|---|---|
| 近战命中 | `meleeHit`（多 API 回退，返回 `{api,dealt}`） | `:360-388` |
| 按类型攻击 | `doAttack` | `:482-493` |
| 摔落伤害 | `applyFallDamage` | `:1642-1676` |
| 致死伤害 | `applyLethalDamage` | `:3027-3041` |
| 法术伤害 | `applySpellDamage` / `spellSource` | `:2607-2628` / `:2597-2606` |
| 伪造雷击实体 | `spawnVisualBoltAt` | `:503-525` |
| 生成末影珍珠 | `nancySpawnPearl` | `:1756-1767` |
| **执行原版命令** | `runCmd` / `runCmdP`（`performPrefixedCommand`） | `:2283-2301` / `:1635-1641` |
| 铁砧点击 | `anvilClick`（`ClickType` 直投） | `:844-852` |
| **直接驱动生产 tick** | `EffectCardPeriodClass.tick(p)` | `:2043`、`:2048` |

**C. 读数（readings）——唯一出口是 `send`**

| 子类 | 符号 | 行号 | 用量 |
|---|---|---|---|
| **唯一读数出口** | `send(ctx, "AP_"+tag+…)` | `:103-107` | **290** 处 |
| 附件读 | `ModAttachments.get*` | 散布 | **26** 处 |
| 效果存在/可见/时长 | `findEffect` / `effectVis` / `fxState` / `fxInWindow` | `:186-200` / `:201-207` / `:1827-1837` / `:1838-1850` | — |
| 槽位读 | `curioSlots` / `diceSlotItemId` / `chipSlotCount` / `chipSlotItemId` | `:208-225` / `:226-239` / `:240-259` / `:2757-2768` | — |
| **生产判定入口读** | `isBlocked` / `isCooldownActive` / `getMaxAllowed` / `getRemainingBlockSeconds` / `gloveArmed` | `:2040,2051` / `:1509` / `:2035` / `:2041` / `:2887-2894` | — |
| 世界/实体读 | `countLightning` / `rghp` / `nancyHurtSnapshot` / `enderGlowState` | `:273-288` / `:1993-2002` / `:1602-1607` / `:3013-3026` | — |

**D. 断言 / 自判（self-verdict）——本审计最关注的一类**

- 异常包装：`guard(ctx, tag, fn)` `:114-123`，**47** 处包裹；异常统一打成 `AP_<tag>_EX:`（`:124-138` `exText`）。
- **探针**在函数内自行计算 `ok` 并打印结论行，例如 `doEffectCardLock` `:2056-2058`：
  ```js
  var ok = blockedBefore && remainBefore === 0 && recovered === 1
      && countCleared === 0 && cdCleared === 0 && !blockedAfter;
  send(ctx, "AP_" + tag + "_LOCK_OK:" + (ok ? 1 : 0));
  ```
- **PowerShell 侧只把该结论行当正则匹配**（`mt_case.ps1:471-484`）。

⇒ **判据的产生与消费完全分离在两侧**：`ok` 由探针（被测代码的同一进程、同一 classloader）算出，工具链只检查「这行文本在不在」。这正是第 5 节「反循环红线」针对的结构性风险点。

**E. 时序 / 异步**

`loadScheduler`（`:164-168`）、`pearlWatch*` 全局态 + `pearlTickBody`（`:1724-1730`、`:1927-1990`）、`boltSpawnHook`（`:390-405`）、模块级状态 `rgfState`/`rtgState`/`fenState`/`spellState`（`:1991`/`:2369`/`:2453`/`:2571`）、`probeExtraSourceArmed`（`:1328+`）。

### 1.6 脆弱性清单（有据可查）

| # | 现象 | 机制 | 证据 |
|---|---|---|---|
| ① | `HOSTILE-TARGET-NEUTRAL-1.21.1` **25/26 FAIL**，产品 25 条全绿 | 用例 step 的原版 `/data get … AngerTime` 回读**时序上永远不可达**：探针臂装步自己就打了近战 → 赐福 → ~1 s 后落雷劈死 30 HP 北极熊；而**注入一条命令 ≥2.65 s** ⇒ 回读时实体已不存在（日志回「未找到实体」） | `B1-in-game-results.md:101,118-122`；`B2-tests-fix.md:112-132`（C0 修法：把同一条原版命令交给探针在近战**之前**跑，断言文本未改） |
| ② | `NANCY-LU-PEARL-IMMUNE-1.21.1` **17/29**，判为 ERROR 而非 FAIL | 探针 `pearl.setDeltaMovement(0.0,0.0,0.8)` 在 Rhino 上重载解析失败：`InternalError: Can't find method …Entity.setDeltaMovement(number,number,number)` ⇒ P2/P3 相位整段没跑 | `B1-in-game-results.md:103,124-125`；修法见 `B2-tests-fix.md:85-104`，**读取时点已落地**（`:71` 新增 `Vec3Class`，`:1759` 改用 `new Vec3Class(...)`） |
| ③ | `KOMACHI-EXTRA-PLAY-1.21.1` 旧探针下 **34/41 FAIL（假 FAIL）** | `mt_env` 从不刷新 `run/<版本>/kubejs/`，探针靠手工复制（TESTING-SPEC §6）⇒ 用了 14:54 的旧探针（源 16:33 已更新）；换新探针后 41/41 PASS | `B1-in-game-results.md:102,144,178`；A4 修法 `mt_launch.ps1:44-118`（SHA256 比对 + 部署后复核 + 不一致告警） |
| ④ | `RAILGUN-PET-EXCLUDE` 首轮**假 FAIL** | 单阶段 `--phase launch` 不写快照 ⇒ 沿用上一轮 `latest.log=74375` 偏移，把**确实存在**的 `AP_PE_TAME` 判成未命中 | `B1-in-game-results.md:176`；A2 修法 `mt_launch.ps1` 收尾写快照 |
| ⑤ | `.mt_run_state` / `.mt_snapshot` 被当成用例各记一条 **ERROR** | pwsh 移植回归：python `Path.glob("*.json")` 不匹配点文件，pwsh `-like '*.json'` **会**匹配 | `B1-in-game-results.md:168,177`；A3 修法 `mt_case.ps1:716`；**读取时点 `.mt_run_state.json` 里仍留着这两条 ERROR**（该轮早于修复） |
| ⑥ | `FEN-SPLASH-MAIN-TARGET-1.21.1` **不稳定**（首轮 18/19 FAIL → 复跑 19/19 PASS） | `ratio = near_dealt / melee_est`，主靶被**过量击杀**时观测溅射反映真实伤害而 `melee_est` 不跟随 | `B1-in-game-results.md:105,140,181` |
| ⑦ | 整轮命令**静默全丢**（断言未命中，而 KUBEJS/CRASH/MIXIN 全绿） | Esc 是暂停菜单**开关**：旧实现每条注入先盲发 `Esc,Esc`，若 T 尚未打开聊天，Esc 把暂停菜单顶开 ⇒ `Minecraft.pause=true` ⇒ **IntegratedServer 停止 tick** ⇒ 后续聊天键再也打不开 | `mt_inject.ps1:455-496`（反面教材逐条记录：只按 Enter / Esc×2 / 只按 Esc 均踩过） |
| ⑧ | 用例里的 `/effect clear @s` 对**本模组效果无效** | `ModEffectEvents#onModEffectRemovalPrevented`（`@SubscribeEvent(priority = HIGH)`，`ModEffectEvents.java:110-131`）对 `astral_dice:*` 一律 `event.setCanceled(true)`（`:128-129`），仅 `ModEffectRemoval.isInternal()`（`:114`）、`EffectTimerGuard.isForcedRemoval()`（`:115`）、死亡（`:117`）三条通道放行 | 用例 NOTE 自己写明了这一点：`EFFECT-DECAY-FLICKER-1.21.1.json:16`；步本身在 `:20`（`FEN-SPLASH-MAIN-TARGET-1.21.1.json:32`） |
| ⑨ | `ANVIL-STAR-UPGRADE` **45/53 FAIL**（首次实跑） | 其中 `AP_Z1_ERR:lost_after_close` 是**探针自报误报**（`ds<0||fs<0` 判据在 `direct` 路径下必然触发）；且 `ContainerLevelAccess.NULL.execute()` 静默 no-op 使 block 路径从未真正跑通 | `B1-in-game-results.md:109,127-128`；`docs/batch3/ANVIL-attribution.md`；`B2-tests-fix.md:183-226` |

**结论性量化（一句话）**：35 条用例 = **406 步**（其中 **153 次键盘注入聊天命令、每次 ≥2.65 s**）+ **515 条断言**（全部是对 `latest.log`/`debug.log` 的**正则匹配**，无一条能读游戏内状态），探针 **3344/3398 行**同时干「准备/触发/读数/自判/时序」五件事，全套纯注入+等待 ≈ **602 s ≈ 10 分钟**；而近 3 天内 9 类已知脆弱性中，**①②⑦ 是时序/注入类**、**③④⑤ 是工具链状态类**、**⑨ 是探针自判类**。

---

## 2. 可替代性分析（核心）

### 2.0 先立成本经济学——不先说清这一条，后面会得出完全错误的结论

两个成本相差**三个数量级**：

| 手段 | 成本 | 依据 |
|---|---|---|
| 注入一条**聊天命令**（`inject_command`） | **≥2.65 s**（固定按键序列 2 050 ms + 后置 600 ms；实测 2.5–3 s） | `mt_inject.ps1:432-529`（`:498/500/502/505/525` 停顿）、`mt_case.ps1:583`；`B1-in-game-results.md:121` |
| 探针**内部**直接调生产 API | **≈ 0**（同一 JVM、同一 classloader、无 I/O） | 探针通篇即此法（`ModAttachments.set*` 74 处、`ModEffectRemoval.remove` 17 处） |

由此推出四条结论，它们决定了后面所有判定：

> **结论 1**：若某步「准备」**已经在探针函数内部**完成，改用 `/astralparty` **不会省任何一次注入、任何一秒**——反而更差（要走聊天栏，2.65 s）。
>
> **结论 2**：探针若需要「与生产同一份语义」，**直接调生产 API 即可**（`EffectCardPeriod.forceResetRound(p)` / `ModEffectRemoval.remove(...)` / `EffectCardPeriod.effectPendingEffects()`），**命令在探针内部没有任何增量价值**。
>
> **结论 3**：三条命令的真实价值只剩三处 ——（a）**case JSON 层**的「准备/清理」原语（今天只能用**另一个探针命令**表达，或干脆缺失）；（b）消除「探针复刻生产清理项」的**漂移面**；（c）人工/手动调试与 OP 管理员功能（见第 5 节的新原则）。
>
> **结论 4**：153 次注入中 **131 次是探针相位驱动**（每个命令内部已一次性完成 准备+动作+读数+自判），**三条命令无法减少这些注入**。可替代面只在其「纯清理 / 纯读数」部分：**28 次 teardown 型 + 26 次只读型 = 54 次（占注入 35%）**。

**注入按性质分类（全库 153 次）**：

| 性质 | 次数 | 明细 |
|---|---|---|
| **teardown / 清理** | **28** | `railgunfriendlyend` 16、`decayclear` 4、`airbagreset` 2、`anvilclose` 2、`nancypearlclose` 2、`tradeclose` 2 |
| **只读读数** | **26** | `railgunfriendlyread` 16、`nancystate` 4、`airbagread` 2、`fensplashread` 2、`komachiread` 2 |
| 动作 / 相位驱动 | 99 | 探针相位 77 + 原版命令 22（`/gamerule` 8、`/fill` 8、`/effect clear @s` 4、`/data get` 2） |

> ⚠️ **附带的隐藏成本**：`railgunfriendlyread` 与 `railgunfriendlyend` 各 16 次 = **8 次 × 2**。用例 NOTE 自陈「下面每条读命令都注入**两次**……冷启动后前若干次注入偶发丢失，只发一次会得到『没有读数』的空跑」（`HOSTILE-TARGET-NEUTRAL-1.21.1.json:20`）。⇒ **16 次注入是纯粹的冗余重试**，值 **≈42 s**，是本流程**唯一可直接删除而不损覆盖度**的注入。

### 2.1 逐条替代判定表

下表的「省掉什么」按 2.0 的口径分列，**不把「命令替换命令」算成节省**。

| # | 步骤是什么 | 文件:行号 | 现用什么 | 能否用新命令 | 替代后省掉了什么 |
|---|---|---|---|---|---|
| **S1** | **出牌周期归零**（探针基线重置，12 个调用点） | 定义 `probe:1179-1190`；调用 `:1248,1396,1463,1475,1502,1521,1559,1814,2034,2908,2952,2980` | 8 条附件直写 + 内调 `resetActiveLock` | **部分可替代**：`resetcardlock`（= `forceResetRound`）覆盖其中 **6/8** 项 + **额外 1 项**（`disarmAoe`）；**缺** 5 个立牌锁定键 + `sign_active_cooldown_end` | 函数体 **12 行 → 约 3 行**；**注入 0 次、重载 0 次**。真正收益 = 消除「必须与生产 `clearRoundBonuses` 逐项对齐」的**漂移面**（该风险由注释 `:1174-1178` 自陈） |
| **S2** | **立牌「锁定（生效中）」态清空**（探针基线重置） | 定义 `probe:1193-1199`；调用 `:1186`（S1 内）、`:1297` | 5 条附件直写 | **不可替代**：冻结规格明确 `resetcardlock` **不做** ③、也不碰锁定态 5 键（`sign_active_lock_*` / `sign_active_reduction_pool`） | —（S1 必须保留 `resetActiveLock`） |
| **S3** | **清掉全部本模组效果**（探针清理） | `probe:1565,1810`（`fxHack`）、`:1854-1856`、`:1903-1905`、`:3004` 等，共 **17 处** `ModEffectRemoval.remove` | `ModEffectRemoval.remove(p, fx…)`，**逐个人工列举** | **部分可替代**（`cleareffect`）：仅当语义确为「全清」时 | 17 行 → 1 行/处；但**多数处是选择性清除**（见 S4），全清会**降覆盖度** |
| **S4** | **只清「效果牌施加的」效果** | `probe:1224-1227` `clearExtraPlayEffects`（6 个调用点）；`:2689`（spell 相位清 4 个法伤牌加成）；`:3004`（`enderGlowReset` 清 `MARKED`） | `ModEffectRemoval.remove` **逐个人工列举** | **可替代**（`clearcardeffect`）：`effectPendingEffects()` 按注册表自动覆盖 9 个待定源 | **重复度最高的一类**：探针把 9 个待定源效果手写散落各处；命令改为**从注册表派生** ⇒ 消除「新增效果牌后探针漏改」的漂移 |
| **S5** | **用例级前置清理**：`/effect clear @s` | `EFFECT-DECAY-FLICKER-1.21.1.json:20`（1.20.1 `:20`）、`FEN-SPLASH-MAIN-TARGET-1.21.1.json:32`（1.20.1 `:32`），共 **4 步** | 原版 `/effect clear @s` | **应当替代** —— 因为**它今天对本模组效果是无效的**（`ModEffectEvents.java:110-131`，`:128-129` 对 `astral_dice:*` 一律 cancel；用例 NOTE 自己写明了这点，`EFFECT-DECAY-FLICKER-1.21.1.json:16`） | **步骤数 0 省、秒数 0 省**，但**修复一个名不副实的步骤**：今天是「看起来清了、其实没清」，换成 `cleareffect` 后才真正达到用例作者的意图 |
| **S6** | **骰神赐福清除**（跨用例污染源） | `probe:1865`、`:2469`、`:2542` | `ModEffectRemoval.remove(p, ModEffects.DICE_BLESSING)` | **可替代**（`cleareffect`，同 S3） | 同 S3。⇒ 这三处正是「必须知道 `/effect clear` 无效」的**知识负担**所在 |
| **S7** | **Curios 槽位清空** | 定义 `probe:1159-1165`；**19 个调用点** | `clearCurioSlots` 直写 `setStackInSlot(i, EMPTY)` | **不可替代**：三条命令都不碰 Curios | —（属第 3 节的**只读 dump** 范围，不含写入） |
| **S8** | **其它附件键归零**（如 `electric_glove_aoe` / 各类 `cooldown_end` / `komachi_*`） | 散布全文件，`ModAttachments.set*` 共 **74 处** | 附件直写 | **不可替代**：三条命令只覆盖出牌锁 ①② 与效果 | — |
| **S9** | **玩家模式 / 血量 / 充能** | `probe:2072-2073`（`setGameMode(SURVIVAL)` / `setHealth(max)`）、`:2076-2077`（`ChargeManager.removeAll/addStacks`） | 生产 API 直调 | **不可替代** | — |
| **S10** | **世界/地面准备** | case `:24`（`/gamerule doFireTick false`）、`:32`（`/fill … stone`），共 16 步 | 原版命令 | **不可替代**（越出「本模组自身状态」范围） | — |
| **S11** | **关闭界面** | `probe:853-870` `anvilCloseMenu`、`:613-632` `doTradeClose`、`:1801-1826` `doNancyPearlClose` | 探针内 `closeContainer()` | **不可替代** | — |
| **S12** | **被测动作本身**（被测路径） | `probe:2965` `BaseSignItemClass.tickSignActiveLock(p)`、`:2941` `EffectCardPeriodClass.registerPlay(p)`、`:2920/2931` `tick(p)`、`:2063+` `doRailgunFriendly` | **真实生产入口** | **禁止替代** —— 替代即循环自证（见第 5 节红线） | — |

**S1 的逐项对齐（为什么只能省 6/8 而不是整块删）**：

| 清理项 | `resetEffectCardCycle`（探针） | `resetcardlock` = `forceResetRound`（生产 `EffectCardPeriod.java:242-246`） |
|---|---|---|
| `effect_card_bonus_plays`（「每轮一次」标记） | ✓ `:1180` | ✓（经 `clearRoundBonuses:214`） |
| `effect_card_play_count`（① 出牌数） | ✓ `:1181` | ✓ `:244` |
| `effect_card_cooldown_end`（② 出牌冷却） | ✓ `:1182` | ✓ `:243` |
| `sign_active_cooldown_end` | ✓ `:1183` | **✗** |
| 锁定态 5 键 | ✓（`resetActiveLock:1186`） | **✗** |
| `candy_chip_play_bonus` | ✓ `:1187` | ✓（`clearRoundBonuses:215`） |
| `satellite_play_bonus` | ✓ `:1188` | ✓（`:216`） |
| `living_page_cycle_bonus` | ✓ `:1189` | ✓（`:217`） |
| **`electric_glove_aoe`（`disarmAoe`）** | **✗** | **✓（`clearRoundBonuses:219`）—— 额外副作用** |

⇒ **`resetcardlock` 是「6 项覆盖 + 1 项新增 − 7 项缺失」**，既不是超集也不是子集 ⇒ **不能整块替换 `resetEffectCardCycle`**，只能替换其前 3 行。

**S1 新增副作用的实测风险（已核查，结论：低）**：`resetcardlock` 会**顺带解除电击手套武装**。在唯一对 `aoe` 敏感的用例里，每个相位都在 `resetEffectCardCycle` **之后**显式重写 `setElectricGloveAoe(p, true/false)`（`probe:2909` 前置 false、`:2917/2928/2938/2954` 各相位 true），故该副作用会被立即覆盖 ⇒ **本用例安全**；但作为一般规则，**任何用 `resetcardlock` 做前置的用例都必须知道它会顺手清 `electric_glove_aoe`**，否则会得到与预期不符的基线。

### 2.2 与探针既有辅助函数的重复度（grep 实名，精确计数）

任务书猜测的名字（`resetActiveLock` / `resetEffectCardCycle` / `clearEffects`）**基本猜中**，实测实名与计数如下（1.21.1 / 1.20.1 两侧结构一致）：

| 真实符号 | 1.21.1 定义 | 1.21.1 调用 | 函数体行数 | 与新命令的关系 |
|---|---|---|---|---|
| `resetEffectCardCycle` | `:1179` | **12 处**（`:1248,1396,1463,1475,1502,1521,1559,1814,2034,2908,2952,2980`） | 12 行（`:1179-1190`） | **部分重复**（S1，6/8 项）⇒ 可缩到约 3 行，**不能删** |
| `resetActiveLock` | `:1193` | **2 处**（`:1186` 内调、`:1297`） | 7 行（`:1193-1199`） | **无重复** ⇒ 必须保留 |
| `clearExtraPlayEffects` | `:1224` | **6 处**（`:1247,1296,1395,1461,1476,1501`） | 4 行（`:1224-1227`） | **重复**（其 2 个效果都是待定源）⇒ 语义并入 `clearcardeffect` |
| `clearCurioSlots` | `:1159` | **19 处** | 7 行（`:1159-1165`） | **无重复** ⇒ 必须保留 |
| `ModEffectRemoval.remove` 直接调用 | — | **17 处** | — | 部分可被 `cleareffect` / `clearcardeffect` 取代 |
| `p.removeEffect(原版效果)` | — | **9 处** | — | **不可取代**（命令只清本模组效果） |

> **「`clearEffects`」这个名字不存在**：探针里没有任何名为 `clearEffects` 的函数；效果清除全靠 17 处 `ModEffectRemoval.remove` 内联 + 上述 2 个封装（`clearExtraPlayEffects` 与各 `do*Clear`）。**这一点本身就是复杂度证据**：同一件事（清效果）在探针里有三种写法、散落 26 处。

### 2.3 不可替代清单（明确列出，避免读者以为「都能替」）

1. **立牌锁定态 5 键 + `sign_active_cooldown_end`**（S2）——冻结规格明确排除 ③，也排除锁定态。
2. **Curios 槽位**（S7，19 处）——三条命令都不碰饰品槽。
3. **其余 74 处附件写入**（S8）——覆盖面远超三条命令。
4. **玩家模式/血量/充能**（S9）——不属「本模组状态的重置/清除」语义。
5. **世界/地面/gamerule**（S10，16 步）——越出「本模组自身状态」范围（与第 5 节新原则一致）。
6. **界面关闭**（S11）。
7. **被测动作本身**（S12）——**替代即循环自证**。

### 2.4 净收益汇总（诚实版）

| 收益类型 | 量化 | 说明 |
|---|---|---|
| **注入次数** | **0 次** | 153 次中 131 次是相位驱动；其余 22 次原版命令里只有 4 次 `/effect clear @s` 属三条命令的语义范围，且是 1:1 替换 |
| **执行秒数** | **0 s**（三条命令本身） | 见 2.0 结论 1/2 |
| **步骤语义修复** | **4 步** | `/effect clear @s` → `cleareffect`（S5），今天这 4 步对本模组效果是**空操作** |
| **探针 JS 行数** | **两侧合计约 24–30 行** | S1 缩 12→3（×2 版本 = 18 行）、S4 缩 4→1（×2 = 6 行）、S3/S6 部分内联收敛 |
| **消除的漂移面** | **2 处** | ① `resetEffectCardCycle` 与 `clearRoundBonuses` 的「逐项对齐」义务（`:1174-1178` 自陈）；② 9 个待定源效果在探针里的人工列举 |
| **可独立删除的冗余注入** | **16 次 / ≈42 s** | 读/清命令的「注入两次」重试（与三条命令**无关**，是纯浪费） |
| **可折叠进探针的原版注入** | **22 次 / ≈58 s** | `/gamerule` 8 + `/fill` 8 + `/effect clear @s` 4 + `/data get` 2，均可用探针内 `runCmd`（`:2283` 已验证机制）执行 —— **不需要任何新命令** |

> **核心判决**：把 `/astralparty` 三条命令引入测试流程，**不能降低测试耗时**（省 0 次注入、0 秒），能降低的是**维护复杂度**（探针 ~30 行 JS + 2 处漂移面）并**修复 4 个名不副实的步骤**。真正能压时间的两个手段**都不需要新命令**：删掉 16 次冗余重试（≈42 s）、把 22 次原版命令折叠进探针（≈58 s），合计 **≈100 s ≈ 17%** 的全套时长。
>
> 因此「引入新命令以降低测试复杂度」这一命题，**在「耗时」维度上不成立**；要成立只能走第 3 节的**只读 dump**（降低「断言写法」的复杂度）而非写入类命令。

---

## 3. 还缺什么命令（提案）

### 3.0 总纲：调试命令 = 管理员功能，必须避免功能越界

本节所有提案先过一道闸门，闸门由用户追加的原则给出。核心判据是本审计归纳的一条**可分性判据**：

> **「重置」与「任意值写入」的分界 = 目标值集合的基数。**
> **重置**：写入**唯一**的规范默认值（`0` / `false` / `清空`）⇒ 只能把状态还原成「这件事没发生过」的样子，**无法合成玩家在正常玩法中到达不了的状态**。
> **任意值写入**：目标值集合有**无限/多个**元素（`set … <n>`、`set … <效果> <时长>`）⇒ 能凭空合成不可达状态，遂从「复位工具」变成「玩法操纵器」。

| 标记 | 含义 |
|---|---|
| `[只读]` | 只读取、不写入；**最安全**，无越界风险 |
| `[重置本模组状态]` | 只写**本模组自身**状态的**唯一默认值**；可用于准备/清理，**绝不可用于断言** |
| `[越界-不建议]` | 触碰五条红线之一；本节**显式列出并说明理由**（不省略） |

**五条红线（逐条对应到本审计的实际诱惑）**：

1. **资源类**：给予/设置物品、卡牌、筹码、星币、星光、治愈、剑气层数、最大生命。
   ↳ *本流程的实际诱惑*：探针里已有 `ChargeManager.addStacks(p, 6)`（`:2077`）、`EmpowerManager.addStacks(p, 3)`（`:1879`）、`HealingManagerClass.add(p, 3)`（`:1873`）——**它们只能留在探针内**（探针是测试脚手架、不属于产品面），**绝不可提升为产品命令**。
2. **任意值 setter**：`setcooldown <任意 tick>` / `setplaycount <n>` / `seteffect <效果> <时长>`。
3. **跨玩家扩散**：隐式作用于全体，或影响选择器之外的玩家。
   ↳ **冻结的三条命令已合规**：「不接参数作用于执行者自己，可选接玩家选择器」= **显式单目标**，无隐式扩散。**新增提案必须沿用同一形状：缺省=执行者自己，选择器=显式指定，永不缺省为「全体」**。
4. **进入玩法逻辑**：为「让测试好写」在产品逻辑里加调试专用分支/后门。
5. **超出本模组状态范围**：动原版或其它模组的玩家状态。

---

### 3.1 提案 P1 —— `dump [玩家]`：一条命令转储本模组自身状态 `[只读]` ✅ **最推荐**

| 项 | 内容 |
|---|---|
| **建议字面量** | `/astralparty dump [玩家选择器]`（缺省 = 执行者自己；**永不**缺省为全体） |
| **权限** | `hasPermission(2)`（与冻结三命令一致，仅 OP） |
| **语义（只读）** | 对目标玩家打印一条结构化读数行 `AP_DUMP:<字段=值, …>`，字段全部为**本模组自身状态**：<br>① **出牌锁三判据**：`isBurstFull` / `isCooldownActive` / `isEffectPending` 三个**原始判据**，以及 `isBlocked` 合成值；<br>② **出牌轮数值**：`effect_card_play_count`、`getMaxAllowed`、`effect_card_bonus_plays`（「每轮一次」标记）、`effect_card_cooldown_end` 剩余 tick、`getRemainingBlockSeconds`；<br>③ **立牌三态**：锁定态（`sign_active_lock_sign` / `lock_end` 剩余 / `grace_end` 剩余 / `played` / `reduction_pool`）、待命态（`sign_ready_type` / `sign_ready_expire` 剩余）、主动冷却（`sign_active_cooldown_end` / `sign_active_max_cooldown`）；<br>④ **本模组效果列表**：遍历 `ModEffects` 的只读视图，逐条打印 `id` / `amplifier` / **剩余时长** / `visible`（含 `empower` / `healing` 这类 `visible=false` 的）；<br>⑤ **第 ③ 判据的来源明细**：遍历 `EffectCardPeriod.effectPendingEffects()`，逐条打印 `id + active`（**这是排查「出牌锁为什么还在」的唯一直接手段**）；<br>⑥ **其它只读附件**：如 `electric_glove_aoe`、Curios 三个槽位（dice/stand/chip）的当前物品 id 与数量。 |
| **能替掉哪些现有步骤** | • **26 次只读注入**中的通用部分：`nancystate`×4、`airbagread`×2、`fensplashread`×2、`komachiread`×2（`astralbugfix_probe.js` 对应 `doNancyState:1592-1601`、`doAirbagRead:2846-2852`、`doFenSplashRead:2520-2570`、`doKomachiRead:1527-1541`）；<br>• **2 次原版 NBT 回读**：`RAILGUN-PET-EXCLUDE-1.21.1.json` 的 `/data get entity @e[type=minecraft:wolf,limit=1] Owner`（1.20.1 同）——dump 可直接给出「狼是否已驯服/主人是否为目标玩家」的等价只读事实；<br>• **探针的读数命令面膨胀**：44 个 `do*` 中有 **6 个是纯 read**（`railgunfriendlyread`、`railtruedmgread`、`fensplashread`、`komachiread`、`nancystate`、`airbagread`），共被注入 26 次 |
| **预估节省** | **注入：0 次**（1:1 替换，铁律见 §2.0 结论 1）；<br>**代码行：约 120–180 行 JS**（6 个纯 read 命令体 + 其命令注册块，两侧合计约 240–360 行）；<br>**复杂度：把「每条特性自己发明读数口号」收敛为单一词汇表** —— 这是本提案的**主要收益** |
| **风险：会不会循环自证？** | **只读 ⇒ 不会降低覆盖度**（不触碰任何执行路径，纯读取）。**但有一个必须写进红线的陷阱**：dump 若直接打印被测代码的**判定入口**（`isBlocked` / `isEffectPending`），而用例又拿这个字段去断言「`isBlocked` 行为正确」，那就是**用被测功能验证自身**。⇒ **dump 必须同时（且优先）打印原始附件真值**（`play_count` / `cooldown_end` / 各效果是否存在），让用例的断言落在**原始值**上；判定入口字段只作**排查线索**，不作断言依据。 |
| **是否侵入断言** | **否**（只读）。但如上，**要防止断言的落点被它带偏**。 |

**为什么这是最推荐的一条**：它是唯一同时满足「降低复杂度」与「零越界风险」的提案 —— 用户新原则明确把「只读状态读数」列为**最安全的一类**，而它恰好直击本流程最大的结构性痛点：**PowerShell 侧完全没有读游戏内状态的能力**（§1.2 结尾结论），一切状态都必须由探针主动打印成 `AP_*` 行、再由正则匹配。统一 dump 让「读数」从**每特性一套**变成**一套**。

---

### 3.2 提案 P2 —— 更细粒度的出牌锁重置：`clearcooldown` / `clearplaycount` `[重置本模组状态]` ⚠️ **可提，但须论证，且优先级低**

按用户指示，对「把 `resetcardlock` 落到更细粒度」显式论证其**为何不算任意值写入**：

> **论证**：`clearcooldown` 的唯一效果是把 `effect_card_cooldown_end` 写成 **0**；`clearplaycount` 的唯一效果是把 `effect_card_play_count` 写成 **0**。两者的**目标值集合基数为 1**（= 规范默认值 = 「该状态不存在」），执行者**无法**借此表达式合成任何在正常玩法中到达不了的状态（例如「冷却还剩 37 tick」「出牌数 = 5」）。
> 对照越界样例：`setcooldown <tick>` 的目标值集合为**无限**，可直接构造「冷却还剩任意值」这一正常路径不可控的状态 ⇒ **越界**。
> **故 `clearcooldown` / `clearplaycount` 属「重置」而非「任意值写入」，不触碰红线 2。**

| 项 | 内容 |
|---|---|
| **建议字面量** | `/astralparty clearcooldown [玩家]`、`/astralparty clearplaycount [玩家]`（沿用冻结三命令的权限与选择器形状） |
| **语义** | 分别把出牌冷却 / 出牌数**单独**重置为 0（**不做** ③ 效果待定、**不做** 立牌状态、**不做** `disarmAoe`） |
| **真实需求证据（存在，但需求方是探针）** | 探针确实需要「**只清一部分**」的精确基线，例如 `doEffectCardLock:2037-2039` 构造的死状态 = **出牌数 = 上限 但 冷却 = 0**（只清冷却、不清计数）；`doGloveRound:2918-2919` = 计数 1 + 冷却 `now+200`（**故意保留**冷却）。 |
| **能替掉哪些现有步骤** | `probe:2038-2039`、`:2918-2919`、`:2929-2930`、`:2939-2940` 等处的**部分**附件直写；以及 `resetEffectCardCycle` 内 **3 行中的 2 行** |
| **预估节省** | **注入 0 次、秒数 0 s**；代码行 **约 4–6 行 JS**（两侧 8–12 行） |
| **诚实评估** | **收益接近于零**：需求方是探针，而探针**直接调 API 更便宜**（§2.0 结论 2：探针内 API 调用 ≈0 s；走命令要 2.65 s）。它的唯一价值在**人工/OP 手动调试**（例如玩家自己卡在出牌锁里时按需只清冷却）。⇒ **列为可选、低优先，不建议为测试流程专门实施**。 |
| **风险** | 若把 `clearcooldown` 用于**准备**：安全（重置语义）。若有人拿它去**断言**「冷却被清了」：仍属**循环自证**（见 §5 红线）——任何重置命令都只能用于准备，不能作为断言对象。 |
| **是否侵入断言** | **否**（作为准备）；**是**（若被拿来当断言对象 ⇒ 禁止） |

---

### 3.3 被判定为越界的候选（**显式不建议 + 理由**，非省略）

任务书列出的候选方向中，有相当一部分**正在五条红线上**。逐条明确否决：

| 候选 | 标记 | 不建议的理由（对应红线） |
|---|---|---|
| `/astralparty givecard <卡牌> [数量]`（给予/清空卡牌） | `[越界-不建议]` | **红线 1**：给予物品/卡牌 = 资源注入。且**红线 4**：它会让「需要真的打出 N 张牌」的长时序路径被短路 ⇒ **降低对真实出牌路径的覆盖度**（本来走真实路径，现在走捷径）。 |
| `/astralparty setplaycount <n>`（把出牌数设为指定值） | `[越界-不建议]` | **红线 2**：任意值 setter（目标值集合无限）。可合成「出牌数 = 5」这类正常玩法不可控的中间态。 |
| `/astralparty setcooldown <tick>`（把出牌冷却设为指定值） | `[越界-不建议]` | **红线 2**：同上。**注意与 P2 的区别**：P2 的 `clearcooldown` 只写 0。 |
| `/astralparty seteffect <效果> <时长> [等级]`（施加指定效果） | `[越界-不建议]` | **红线 2**（时长/等级为任意值）+ **红线 4**：这是「直接施加指定效果」类提案。它比 `cleareffect` 更精细，但代价是**在玩法逻辑里开一个任意施加效果的后门**。⇒ 若确有「精确施加单个效果」的诊断需求，正确做法是**只读 dump 出效果明细**（P1 ④），**施加**留给探针（探针已有 `p.addEffect(new MobEffectInstanceClass(...))`，18 处，且免费）。 |
| `/astralparty setstarlight <n>` / `sethealing <n>` / `setmisaki <n>` / `setmaxhealth <n>`（伪造玩家级资源） | `[越界-不建议]` | **红线 1**（资源）+ **红线 2**（任意值）。用户原则明确点名了「星光、治愈、剑气层数、最大生命」。 |
| `/astralparty clearalleffects`（作用于**全体**玩家） | `[越界-不建议]` | **红线 3**：跨玩家扩散。⇒ 若需要清效果，用**冻结的 `cleareffect [玩家]`**（缺省=自己，显式选择器）即可，**不得**缺省为全体。 |
| `/astralparty testmode on`（测试模式：绕过冷却 / 绕过出牌锁 / 免消耗） | `[越界-不建议]` | **红线 4**：典型玩法后门。这是「降低测试复杂度」最容易想到的捷径（让 `isBlocked` 恒 false 就不用清状态了），但**必须明确拒绝** —— 它会让被测逻辑本身被短路，测试从此**无法证伪**任何出牌锁缺陷。 |
| `/astralparty clearcurio <槽位>`（清空饰品槽） | `[越界-不建议]` | **红线 1**（破坏/移除玩家物品，属资源面）+ **红线 5**（Curios 是**其它模组**的库存域）。且探针内 `clearCurioSlots`（`:1159-1165`，**19 处调用**）已免费实现同一目的，**无增量价值**。 |
| `/astralparty setgamemode` / `heal` / `/fill` / `/gamerule` 类世界与玩家通用操作 | `[越界-不建议]` | **红线 5**：超出「本模组状态」范围（原版/其它模组域）。这些继续由探针内 `runCmd`（`:2283`）或 case 步的原版命令承担。 |
| 「强制触发骰神赐福」/「强制触发立牌主动」 | `[越界-不建议]` | **红线 4**：直接进入玩法逻辑。这类「帮测试跨过触发条件」的命令会让「触发条件是否正确」这一被测点失去覆盖。 |
| 「跳过效果牌冷却以连打 N 张」 | `[越界-不建议]` | **红线 4** + 降低覆盖度：`EffectCardPeriod` 的冷却/上限正是被 `KOMACHI-EXTRA-PLAY` 等用例覆盖的对象。 |

**小结**：在红线约束下，**可提的只有两类** ——（i）**只读**：`dump`（P1，强推荐）；（ii）**重置**：更细粒度的清零（P2，弱推荐，因需求方在探针内、收益≈0）。任务书候选里的「给予卡牌」「设置任意值」「伪造玩家级资源」**全部越界**，已逐条给出理由。

---

### 3.4 一条不花钱就能拿到的等价收益（无新命令）

由 §2.4 得出：**耗时上的真实收益来自整理现有注入，而非新增命令**。若目标是「降低测试复杂度」，以下两件事**优先于**任何新命令，且完全不触碰权限模型：

1. **删除 16 次冗余重试注入**（`railgunfriendlyread` / `railgunfriendlyend` 各注入两次）⇒ **≈42 s**。前提是先把「冷启动后前若干次注入偶发丢失」的**根因**查清（这是真缺陷，不是应有的常态）。
2. **把 22 次原版命令折叠进探针**（`/gamerule doFireTick false` 8、`/fill … stone` 8、`/effect clear @s` 4、`/data get` 2）⇒ **≈58 s**，机制已在 `runCmd`（`:2283-2301` 的 `performPrefixedCommand`）验证过。**代价**：案卷（case JSON）的可读性与「准备步骤对读者可见」这一点会下降 ⇒ 需权衡，建议只折叠纯机械的 `/gamerule` 与 `/fill`。

---

## 4. 前置条件核查：测试客户端里的玩家是否真有 OP 权限级 2

**结论（先说）**：**有，而且不是 2，是 4。** `/astralparty` 的 `hasPermission(2)` 在测试环境下**必然通过**；**不需要 `ops.json`、不需要 `server.properties` 改动、不需要改 `scripts/test/` 任何脚本**。

### 4.1 环境形态：单人集成服务器（不是专用服务器）

| 事实 | 证据 |
|---|---|
| 启动方式是**单人存档**，进世界走 quickplay | `build.gradle:70-75`：`programArgument '--quickPlaySingleplayer'` + `programArgument project.findProperty('quickplay')`；调用点 `mt_launch.ps1:222`（`-Pquickplay=$world`） |
| **未覆盖 `--username`** | 检索 `neoforge-1.21.1/build.gradle`、`forge-1.20.1/build.gradle` 的 `runs { }` 段（`:60-75` / `:62-77`）**无 username 参数** ⇒ 用 dev 默认用户名 |
| 服务端是**集成服务端**（与客户端同进程、同一构建） | 单人游戏语义；`IntegratedServer` 即服务端实现 |
| **命令走聊天栏**（因此必须过服务端权限校验） | `mt_inject.ps1:497-526`：Esc→Tab→Enter 归一化 → `T` 开聊天 → 整串文本 → `Enter` |

> ⚠️ **易误判点**：`mt_env.ps1:626` 会把 `run/server.properties` 写成含 `allow-cheats=true`（该行来自 `:505-512` 的字面量列表）。**这份 `server.properties` 只被 `runServer`（专用服务端）读取，单人集成服务端不读它** ⇒ 它与「客户端玩家的权限级」**无关**。真正的开关是 `level.dat`（见 4.2）。

### 4.2 测试环境实际强制了什么

| 项 | 实现 | 证据行号 |
|---|---|---|
| 允许命令（**关键项**） | `Set-MtAllowCommands` 把 `level.dat` 的 `Data.allowCommands` 写成 **TAG_Byte 1** | `mt_env.ps1:349-388`，写入点 **`:375`** |
| 死亡不掉落 | `Set-MtKeepInventory` 把 `Data.GameRules.keepInventory` 写成 **TAG_String `"true"`**（与 `allowCommands` 类型不同，注意区分） | `mt_env.ps1:390-445`，写入点 **`:435`** |
| **两条世界路径都强制** | ① 种子快恢复：`mt_env.ps1:600-609`；② 世界重建：`:716-725` | 两处都调 `Set-MtAllowCommands` + `Set-MtKeepInventory` |
| **未就位即硬阻断** | 任一规则写失败 ⇒ `MT_WORLD: BLOCKED`（exit 11） | `mt_env.ps1:601`、`:606`、`:717`、`:722`；规范见 `TESTING-SPEC.md:312`（「mt_env world 强制写入 allowCommands=1 与 GameRules.keepInventory="true"…任一规则缺失即 MT_WORLD: BLOCKED」） |

### 4.3 原版权限链（反编译源逐行核对，**这是本节的核心证据**）

**1.21.1**（源：`neoforge-1.21.1/build/moddev/artifacts/neoforge-21.1.235-sources.jar`）：

```
① IntegratedServer.java:63      this.setSingleplayerProfile(minecraft.getGameProfile());
② IntegratedServer.java:293-295 isSingleplayerOwner(p) = getSingleplayerProfile()!=null
                                   && p.getName().equalsIgnoreCase(getSingleplayerProfile().getName())
③ PlayerList.java:649-653       isOp = ops.contains(profile)
                                   || server.isSingleplayerOwner(profile) && server.getWorldData().isAllowCommands()
                                   || allowCommandsForAllPlayers
④ PlayerList.java:539-543       sendPlayerPermissionLevel(player) → server.getProfilePermissions(profile)
⑤ PlayerList.java:210 / :498    在 placeNewPlayer / respawn 处调用 ④
⑥ MinecraftServer.java:1759-1774 getProfilePermissions(profile):
        if (getPlayerList().isOp(profile)) {
            entry = ops.get(profile);
            if (entry != null)  return entry.getLevel();          // ← 走 ops.json 的显式等级
            else if (isSingleplayerOwner(profile)) return 4;      // ← ★ 我们走这一支
            else if (isSingleplayer())  return isAllowCommandsForAllPlayers() ? 4 : 0;
            else return getOperatorUserPermissionLevel();
        } else {
            return 0;                                              // ← 非 OP ⇒ 一律 0
        }
```

**逐步代入本测试环境**：

1. ① 把**发起启动的那个客户端自己的** `GameProfile` 记为 singleplayer profile ⇒ **② 对客户端自己的玩家恒为 true**（**与存档历史、用户名是否变化无关**——这正是与专用服务器最大的区别）。
2. ③ 因 ② 为 true **且** `level.dat` 的 `allowCommands=1`（4.2 已强制）⇒ `isOp` = **true**。
3. ⑥ 中 `ops.get(profile)` = **null**（测试玩家**不在** `ops.json` 里，也**不需要**在）⇒ 落 `else if (isSingleplayerOwner)` ⇒ **`return 4`**。
4. ⇒ **权限级 = 4 ≥ 2** ⇒ `hasPermission(2)` 通过。
5. 旁证：`IntegratedServer.java:279-281` 的 `getOperatorUserPermissionLevel()` = **2**，但它只在上面的 `else` 分支（**既非 op、又非单人**）才被取用，**本环境走不到它**。

**1.20.1**（源：`forge-1.20.1/build/moddev/artifacts/forge-1.20.1-47.4.10-sources.jar`）——**同构**：

| 环节 | 1.20.1 行号 |
|---|---|
| `setSingleplayerProfile(minecraft.getUser().getGameProfile())` | `IntegratedServer.java:50` |
| `isSingleplayerOwner` | `IntegratedServer.java:254` |
| `getOperatorUserPermissionLevel` | `IntegratedServer.java:242` |
| `PlayerList.isOp`（用 `getWorldData().getAllowCommands()` / `allowCheatsForAllPlayers`，语义相同） | `PlayerList.java:625-627` |
| `MinecraftServer.getProfilePermissions`（同一阶梯，`isSingleplayerOwner ⇒ return 4`） | `MinecraftServer.java:1516-1531` |

### 4.4 经验证据（不依赖我对反编译代码的解读）

等级 2 的四条原版命令**已经在用例里被真实注入并产生了预期效果**：

| 命令 | 要求的权限级（证据） | 是否已被注入且生效 |
|---|---|---|
| `/gamerule …` | `GameRuleCommand.java:14` → `hasPermission(2)` | **是**：8 次（`HOSTILE-TARGET-NEUTRAL-*`、`RAILGUN-AOE-SCOPE-*`、`RAILGUN-OVERRIDE-CLASS-*`、`RAILGUN-PET-EXCLUDE-*` 各 2 次）；且其效果被记入结论——「石头地面 + 关火焰蔓延后 self=0」（`HOSTILE-TARGET-NEUTRAL-1.21.1.json:20`） |
| `/fill …` | `FillCommand.java:41` → `hasPermission(2)` | **是**：8 次；同上，是「self=0」结论的必要条件 |
| `/effect clear @s` | `EffectCommands.java:37` → `hasPermission(2)` | **是**：4 次（`EFFECT-DECAY-FLICKER-*`、`FEN-SPLASH-MAIN-TARGET-*`）。⚠️ 它**执行成功**（权限没问题），只是对本模组效果**无效**（§1.6-⑧）——这两件事必须分开看 |
| `/data get entity …` | `DataCommands.java:64`（包路径 `net/minecraft/server/commands/data/DataCommands.java`）→ `hasPermission(2)` | **是**：2 次（`RAILGUN-PET-EXCLUDE-*` 的 `…wolf… Owner`）；该用例在 B1 正式轮 **26/26 PASS**（`B1-in-game-results.md:98`），其断言依赖该命令的输出 ⇒ **权限级 ≥2 已被绿灯实测证明** |

> 「`/data get` 回显『未找到实体』」这类**报错同样证明权限通过**——权限不足时原版返回的是「权限不足」而**不是**实体未找到。

### 4.5 若拿不到 OP：允许的最小手段（**在不削弱命令权限门槛的前提下**）

按优先级排列。注意：**当前环境并不需要其中任何一条**（4.3/4.4 已证权限级 = 4），以下是**应急预案**。

| 优先 | 手段 | 是否需要改 `scripts/test/` | 说明 |
|---|---|---|---|
| **1（首选）** | **先诊断，不要动产品**：确认 `mt_env world` 确实跑过、且 `level.dat` 的 `Data.allowCommands` = 1（4.2 的写入点 `:375`）。若为 0 ⇒ 世界是**绕过 `mt_env` 手工创建**的，重跑 `mt_env` 即可 | **否**（只排查） | `mt_env` 本身已对缺失做 `MT_WORLD: BLOCKED` 硬阻断（`:601/606/717/722`）⇒ 正常流程不可能静默拿到 0 |
| **2（推荐）** | 在**测试资产侧**加一条**只读**前置断言：探针打印 `player.hasPermissions(2)`（只读，不改产品），`preflight`/用例首步据此 **BLOCKED**（而不是 FAIL） | **是**（探针 / preflight） | 把「权限」明确建模为**前置条件**，与 `allowCommands` / `keepInventory` 同一处理范式：**缺前置 ⇒ BLOCKED，不是弱化产品** |
| **3** | 若将来改用**专用服务端**形态：在**测试服务端目录**的 `ops.json` 里给测试账号 `"level": 2`（或 4） | **否**（手动写入测试运行目录，不入版本库） | 这是原版**官方**的授权机制，**不触碰产品代码**。⚠️ 单人 quickplay 路线**不需要**它（4.3 的 `ops.get != null` 分支与 `isSingleplayerOwner` 分支都能给到 ≥2） |
| **4** | 用服务端控制台 `/op <玩家>` | **否** | 单人无控制台；且 4.3 已保证 ≥4 ⇒ **当前完全不必要** |
| **❌ 禁止** | ① 把 `hasPermission(2)` 降低为 0/1；② 加「测试模式」开关/配置项绕过校验；③ 注册命令时不写 `requires`；④ 用 `if (isTestEnv())` 之类分支；⑤ 让命令仅在测试产物中注册 | — | 均触红线 4（见 4.6 与第 5 节） |

### 4.6 为什么**必须**走真实 OP 路径（不能用测试专用后门绕过）

1. **`hasPermission(2)` 本身是被冻结的产品行为**，也是**被测对象的一部分**。绕过它 ⇒ 用例只证明「命令在有权限时能跑」，**永远不证明**「无权限时被拒绝」；而发布产物若带着被削弱的门槛，就是**权限提升漏洞**（任意玩家可重置自己的出牌锁 / 清除自身本模组效果）。
2. **会产生「假绿」**：测试测的是**与发布物不同的一份产物**（门槛不同），绿灯不能推出发布物正确。这与本项目已经踩过的「旧探针 → 假 FAIL / 假 PASS」同类（§1.6-③），只是方向相反、更危险（假 PASS 比假 FAIL 危险得多）。
3. **测试专用后门天然有泄漏风险**：这类开关极少被正确地限定在 dev 环境；一旦随 jar 发布即成为可利用面。红线 4 的用意正是**从源头禁止**这种结构出现。
4. **与既有前置条件范式一致**：本流程已经把 `allowCommands` / `keepInventory` 建模为**环境前置**并 `BLOCKED` 而非削弱（`mt_env.ps1:601/606/717/722`）。OP 只是**同一类**前置，理应同样处理。
5. **可验证性**：走真实路径时，「权限够用」这件事**已被 4.4 的四条原版命令独立证明**；走后门则连这个证明都会消失。

> **已知覆盖缺口（如实记录）**：`hasPermission(2)` 的**否定面**（非 OP 玩家应被拒绝）在**单人 quickplay 测试环境里无法覆盖**——单人只有一个玩家且恒为 level 4，构造不出「非 OP 玩家」。要覆盖它需要局域网/专用服务端 + 第二个账号，**当前 `scripts/test/` 不具备该能力**。⇒ 该门槛目前**只能靠代码审查背书**，这是一项**明确的、已知的覆盖缺口**，不应以「加后门」的方式伪装成已覆盖。

---

## 5. 落地步骤建议 + 反循环红线

### 5.1 关键前提：本审计的所有替代方案都**以命令存在**为前提

读取时点（§0.2）**命令本体不存在**，只有两个只读访问器（`EffectCardPeriod.effectPendingEffects()`、`ModEffects` 的只读视图）。因此：

> **凡涉及「调用 `/astralparty …`」的步骤，一律必须等另一执行者（B）把命令实现完成、两侧都构建通过之后才能开始。**
> **`dump`（P1）是一条新命令提案，本身也属于 B 的写域**，A **不得**自行实现。

### 5.2 分步落地顺序（含依赖与冲突标注）

| 阶段 | 内容 | 依赖 | 写域 | 冲突风险 | 完成判据 |
|---|---|---|---|---|---|
| **P0** | **冻结并保住现有成果**：A 把 `scripts/test/**` 的未提交改动**先本地提交**（当前 13 个文件全在工作区、未提交，一旦被覆盖即丢失） | 无 | A | ⚠️ **高**：全部未提交 | `git status` 中 `scripts/test/**` 干净 |
| **P1** | **等 B 落命令本体**：`Commands.literal("astralparty")` + 三子命令 + 别名（`resetcardcolddown` / `resetcardcooldown`）+ `hasPermission(2)` + 可选玩家选择器 | 无 | **B（产品源码）** | 与 A 写域不重叠 | 全仓检索 `astralparty` 命中**命令注册**（不止 javadoc） |
| **P2** | **两侧构建通过**（`neoforge-1.21.1` 与 `forge-1.20.1` 功能对等，AGENTS.md 默认规则） | P1 | B | 与 A 的实跑冲突（见 5.3-C3） | 两版本 `BUILD SUCCESS` + 产物更新（按 AGENTS.md 构建守护规则双重验证） |
| **P3** | **A 的重构（低风险三件）**：① `EFFECT-DECAY-FLICKER-*` / `FEN-SPLASH-MAIN-TARGET-*` 的 4 步 `/effect clear @s` → `/astralparty cleareffect`（§2.1-S5，**修复空操作**）；② `clearExtraPlayEffects`（`probe:1224-1227`）语义并入 `clearcardeffect`；③ `resetEffectCardCycle`（`:1179-1190`）前 3 行改调 `forceResetRound` 语义（§2.1-S1） | **P2** | A（`scripts/test/**`） | ⚠️ 与 B 的未来改动无关，但**必须确认 A 已停止手头其他编辑** | 4 条用例断言数**不减少**；`kubejs` / `crash` / `mixin` 三条断言仍全绿 |
| **P4** | **实现 `dump`（P1 提案）**：**只读**、`hasPermission(2)`、缺省=执行者 | P2（可与 P3 并行，但需协调构建窗口） | **B** | 与 A 的实跑冲突 | 两版本构建通过；`/astralparty dump` 在游戏内输出读数行 |
| **P5** | **A 消费 `dump`**：把 6 个纯 read 探针命令（`railgunfriendlyread` / `railtruedmgread` / `fensplashread` / `komachiread` / `nancystate` / `airbagread`）的**通用部分**改为调 `dump`；保留各自**特性特有的增量断言** | P4 | A | — | 断言数不减少；每条用例的 `AP_*` 读数语义等价（逐条比对日志） |
| **P6** | **加 OP 前置检查**（§4.5 手段 2）：探针只读打印 `player.hasPermissions(2)`，`preflight` 据此 **BLOCKED** | P2 | A | — | 权限为 0 时得到 BLOCKED 而非一堆难解的 FAIL |
| **P7** | **清理冗余注入**（§2.4 / §3.4）：先查清「冷启动后前若干次注入偶发丢失」的**根因**，再删 16 次重复读/清注入（≈42 s） | 无（**不依赖任何新命令**） | A | — | 单条用例复跑稳定性提升，断言数不变 |
| **P8** | **全量回归**：两版本全流程，逐用例比对**断言数**与**结论** | P3/P5/P6/P7 | A | 独占客户端（见 5.3-C3） | 断言总数 ≥ 515；无新增 FAIL/ERROR |

**优先级提醒**：若目标是「降低测试复杂度」而非「引入命令」，则 **P7（≈42 s）与 §3.4 的折叠原版命令（≈58 s）收益最大、且零依赖、零越界风险**，应在 P3/P5 之**前**做。

### 5.3 写入冲突风险与串行建议（**重点**）

**当前实况（§0.1）**：A 与 B **同时在写、且都未提交**。写域目前**不重叠**，但**目标交叉**（B 提供手段、A 消费手段）。风险与处置：

| 编号 | 风险 | 处置建议 |
|---|---|---|
| **C1** | **两边都是未提交状态**：`scripts/test/**` 13 个文件 + `*/src/**` 4 个文件全在工作区。任何一次误操作（`git checkout` / 清理 / 覆盖）都会丢工作 | **各自先提交自己的写域**，把「未提交工作区」这个最大暴露面消掉 |
| **C2** | **跨域依赖必须串行**：A 的重构（P3/P5）要**消费** B 的命令产出的 jar；探针与产品**同 JVM**，命令不存在时 `performPrefixedCommand` 必然失败 | 严格 **B(P1→P2) → A(P3/P5)**。**禁止** A 在命令未落地时提前改探针（会得到一片 `AP_*_EX` 或读数缺失） |
| **C3** | **测试环境是独占资源**：只有一个 `run/1.21.1`、一个 `cases/.mt_snapshot.json`、一个 KubeJS `server_scripts/`、**一个客户端**。审计期间**已有一个客户端在跑**（PID 5152） | **任何时刻只允许一个执行者跑 `mt.ps1` / 单阶段流程**。B 若需游戏内自测 `dump`，必须先与 A 约定窗口，或 A 先 `--phase stop` |
| **C4** | **`Sync-MtProbeScripts`（A4）会在 launch 时覆盖 `run/<版本>/kubejs/server_scripts/`** | B 若手工把测试探针放进 run 目录做自测，会在 A 下次 launch 时被覆盖。**B 自测应自带独立脚本或明确知悉该覆盖** |
| **C5** | **AGENTS.md 要求两版本功能对等**：B 若只落 1.21.1，A 的 1.20.1 用例会因命令缺失而 ERROR | B 必须**两侧同落**（或明确声明仅 1.21.1、A 相应跳过 1.20.1 消费步骤） |
| **C6** | **行号漂移**：`B2-tests-fix.md` 里的行号在 19:43 的探针上**已全面失真**（§0.4） | 所有跨执行者的引用**一律用符号名**（函数名 / 命令名）定位，行号只作线索；提交前重新定位 |
| **C7** | A 与 B 会**各自触发构建**，可能撞上 AGENTS.md 记录的 `fileHashes.lock` 守护进程占锁 | 按 AGENTS.md 第 8 条处置（只终止 gradlew wrapper 与 Gradle daemon，**绝不误杀 `net.minecraft.client.main.Main`**——它正是测试客户端） |

**"必须等命令实现完成"的步骤清单（一句话）**：**P3 / P5 / P8 以及对 `dump`、`cleareffect`、`clearcardeffect`、`resetcardlock` 的任何调用**都必须等 P1+P2 完成；**P0 / P6 / P7 与 §3.4 的折叠工作不依赖命令，可立即做**。

### 5.4 反循环红线（**必须遵守**）

以下各条是**方法学硬约束**，与「命令本身是否越界」是**两个独立维度**——第 3 节管「命令能不能做这件事」，本节管「测试能不能这样用它」。

1. **禁止用命令断言命令自身**：**不得用 `resetcardlock` 去断言 `resetcardlock` 生效**；同理不得用 `cleareffect` 断言「效果被清了」、用 `clearcardeffect` 断言「待定源被清了」。**任何重置/清除命令都只能作为「准备/清理」，绝不能同时充当被测对象**（否则是自证循环，必然恒绿）。
2. **命令只用于准备/清理；断言必须独立测量**：断言必须落在**原始附件真值**（`effect_card_play_count` / `effect_card_cooldown_end` / 各效果是否存在及其时长）或**与命令无关的生产入口**上。**允许**的生产入口读数（如 `EffectCardPeriod.isBlocked`、`BaseSignItem.isSignActiveLocked`）只能作为**排查线索**；一旦把它当断言落点，就等于「用被测功能验证自身」。
3. **不得因为引入命令而删掉对真实出牌路径的覆盖**：`KOMACHI-EXTRA-PLAY` 等用例覆盖的是**出牌轮的冷却/上限/一次性加成**语义，以及三条**真实归零入口**（`tick` 情形 1 / `registerPlay` 周期边界 / `forceResetRound`）。**不得**用 `resetcardlock` 把这些入口「简化掉」；准备可以用重置命令，**归零路径本身必须继续走真实入口**（对照 `probe:2920/2931/2941/2965` 的既有正确做法）。
4. **`dump` 的字段不得作为被测判据的落点**（§3.1 风险栏）：`dump` 可以打印 `isBlocked` / `isEffectPending`，但**断言必须读原始值**。若某用例要断言「出牌锁三判据的行为」，必须**独立构造状态 + 独立测量**，不能读 dump 的合成字段。
5. **不得用命令替代被测动作**（§2.1-S12）：被测路径（`tickSignActiveLock` / `registerPlay` / `tick` / 各 `do*` 相位里的真实入口）**永远走真实调用**，命令只能出现在它**之前**（准备）或**之后**（清理）。
6. **命令缺失/失败必须显式失败，不得静默降级**：`/astralparty` 不存在或执行失败时，用例必须落 **ERROR/BLOCKED**，**不得**因为「准备步骤没生效」而让断言以「未命中」的形式混过去——后者会把**工具链故障**伪装成**产品缺陷**（这正是 §1.6-② 「ERROR 被混作产品不可判」教训的反面）。当轮必须核对 `kubejs` / `crash` / `mixin` 三条断言仍全绿，以证明不是脚本自身报错。
7. **不得让测试专用分支进入产品**（与红线 4 同源）：禁止 `if (isTestEnv())`、禁止「仅测试产物注册命令」、禁止用配置项/系统属性绕过 `hasPermission(2)`。
8. **测试流程必须走真实 OP 权限路径**（§4.6 的强制条款）：`/astralparty` 的 `hasPermission(2)` **必须**由真实的 OP 权限满足（本环境为 level 4，§4.3/4.4 已证），**不得**为了「让测试方便」而降低门槛、加旁路开关、或不写 `requires`。若测试客户端拿不到 OP，正确做法是**让环境满足前置**（确认 `level.dat` 的 `allowCommands=1`，必要时在**测试目录**的 `ops.json` 授权），**或把该项建模为 BLOCKED 前置（§4.5 手段 2）**；**绝不能**削弱产品侧权限门槛。**理由**：`hasPermission(2)` 是被冻结的产品行为，绕过它 ⇒ 测试测的是一份**与发布物不同**的产物，绿灯推不出发布物正确，且会把一个**权限提升面**带进发布产物。

### 5.5 覆盖度不倒退的验收判据

引入命令后的回归必须能回答「有没有变松」。逐条核对：

1. **每条用例的断言数不得减少**（引入前基线：全库 515 条；逐条见 §1.3 的 `Asserts` 列）。若某条用例断言数下降，必须逐条说明**为什么被删的断言不构成覆盖损失**。
2. **`kubejs` / `crash` / `mixin` 三条环境断言必须保持全绿**——它们是「工具链自身没坏」的证据（`mt_case.ps1:492-504`）。
3. **被替代步骤的语义必须逐条对照**：例如 `/effect clear @s` → `cleareffect`，要证明**替代后清掉的效果集合 ⊇ 替代前实际清掉的效果集合**。注意反直觉之处：替换前它**只清原版效果**，替换后会**清掉全部 33 个本模组效果** ⇒ 对「需要保留某些模组效果」的用例是**语义增强但也可能是污染**（必须逐用例确认；`EFFECT-DECAY-FLICKER` / `FEN-SPLASH` 当前都是「先清后构造」，故安全）。
4. **`resetcardlock` 的额外副作用必须被核对**：它会顺带 `disarmAoe`（§2.1-S1）。凡在准备阶段使用它的用例，都要确认该副作用**被后续显式写入覆盖**或**不影响断言**（`ELECTRIC-GLOVE-ROUND-RESET` 属前者：每个相位都显式重写 `aoe`）。
5. **失败注入测试（可选但推荐）**：临时把命令做坏（或让权限降到 0），确认相关用例**变成 ERROR/BLOCKED 而不是仍然 PASS**——这是验证「没有静默降级」的唯一硬手段（对应红线 6）。

---

## 6. 无法判定项（如实列出）

| 项 | 缺什么才能判定 |
|---|---|
| `/astralparty` 的**实际行为**是否与冻结规格一致（尤其 `resetcardlock` 是否真的等于 `forceResetRound`、`clearcardeffect` 是否真的以 `EFFECT_PENDING_SOURCES` 为唯一权威） | 命令本体尚未实现（§0.2）。本审计只核对了**已落地的两个只读访问器** |
| 引入命令后**真实的**耗时与稳定性变化 | 需要用例改造完成后实跑两轮对比；本审计给出的 0 s / 0 注入是**由成本模型推出的**（§2.0），非实测 |
| `dump` 提案的**实际代码量收益**（6 个纯 read 命令能否整块删除） | 需要先定义 `dump` 的字段集合并逐条核对是否覆盖那 6 个命令的读数；本审计给出的 120–180 行是**按函数体行数估算** |
| `hasPermission(2)` 的**否定面**（非 OP 被拒） | 单人环境构造不出非 OP 玩家（§4.6）⇒ 该门槛的验证能力**当前不存在** |
| 1.20.1 侧 `IntegratedServer.setSingleplayerProfile` 之后的**完整调用时机**（是否也在构造期、有无 Forge 侧改写） | 已核对到 `IntegratedServer.java:50` 赋值与 `MinecraftServer.java:1516-1531` 阶梯；Forge 自身若另有权限插件/API 介入，未审计 |
| A 与 B 的**最终合并结果** | 两者都在并发写、且都未提交（§5.3-C1） |

---

*本文件为只读审计的产出物；审计过程未修改仓库内任何其它文件。所有行号以 §0 所述读取时点（2026-09-15 19:45–19:47）为准。*
