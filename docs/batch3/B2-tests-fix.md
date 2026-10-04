# B2 — 测试资产修复（工具链四项 + 探针重载错误 + 用例时序/新增两条）

- 执行者：测试资产实施者（子代理）
- 仓库：`F:\MCProject\astral_dice_multiloader`，分支 `multi-1.20.1-1.21.1`，HEAD(开工) `7b9e711`（工作树干净）
- 改动范围（硬约束）：**只动测试资产** `scripts/test/**`、`scripts/devtools/**`（未被要求改，未改）。
  产品源码（`neoforge-1.21.1/src`、`forge-1.20.1/src`）、lang、CHANGELOG、`AGENTS.md` 一律未动。
- 本轮**只实跑 1.21.1**（用户裁定 1.20.1 暂不跑）；1.20.1 的探针与用例副本**同步修改**但不实跑。

---

## 任务 A — 工具链四项

### A1 `Invoke-MtChild -Wait` 阻塞整棵进程树（launch 阶段不得阻塞）

**改了什么文件:行号**
- `scripts/test/mt.ps1`：`Invoke-MtChild` 新增 `-Detached` / `-PhaseMarker` / `-TimeoutSec`
  （函数体，约 `:56-145`）；`Invoke-MtRunPhase` 的 `launch` 分支改为
  `Invoke-MtChild -Script 'mt_launch.ps1' … -Detached`（约 `:225-229`）；文件头新增 A1 例外说明（约 `:31-38`）。

**为什么**
- PowerShell 的 `Start-Process -Wait` 等的是**整棵进程树**；`mt_launch` 以 `-NoNewWindow` 起
  `cmd → gradlew.bat → Minecraft 客户端`，客户端是同一控制台的后代 ⇒ `-Wait` 阻塞到客户端退出
  才返回。B1 执行者已实测根因（子进程 0.4 s 退出、父进程 12.4 s 才返回 = 孙进程时长），并证明
  「分步 `--phase launch`」同样不行。
- 做法：**复用并固化 `Start-MtDetached.ps1` 已验证的思路**（`-WindowStyle Hidden` + stdout/stderr
  落**文件句柄** + 调用方按终态标记轮询），但**内联进 `mt.ps1`**，避免测试工具链反向依赖
  `scripts/devtools/`。该 devtools 脚本本身**已在 git 纳管**（`git ls-files` 命中，非 gitignore），
  故无需搬迁；本轮未改它。
- 终态标记 = `MT_LAUNCH: OK|FAIL|ERROR|BLOCKED`（`Mt.Phase.psm1` 的输出契约），映射回
  `MT_EXIT_PASS/FAIL/ERROR/BLOCKED`。进程已退出但无标记 ⇒ 透传日志尾部 20 行并判 ERROR。
- **其余阶段保持 `-Wait` 语义不变**（argv 构造与 `ConvertTo-MtStartArgs` 完全未动）。

**实跑结果**：见任务 D。

### A2 单阶段 `--phase launch` 写快照

**改了什么文件:行号**
- `scripts/test/mt_launch.ps1`：收尾 `Write-MtOk 'LAUNCH'` 之前调用
  `mt_assert.ps1 snapshot --version <v>`（约 `:305-315`）。
- `scripts/test/mt.ps1`：`:330` 附近的快照调用保留（幂等保险）并加注释说明。

**为什么**
- 此前**只有全流程**写快照；分步路线（`--phase launch` → 逐条 `--phase cases`）不写，于是用例
  沿用上一轮的 `latest.log` 字节偏移，把**确实存在**的 `AP_` 行判成未命中 ⇒ **假 FAIL**
  （B1 实测：RAILGUN-PET-EXCLUDE 首轮因此未命中 `AP_PE_TAME`，复跑才 26/26）。把快照放进
  `mt_launch` 的收尾后，全流程与分步路线共用同一处，调用方不必再手工补。
- 与现有设计不冲突（快照就是「launch 之后建立增量基线」的语义）。

**实跑结果**：见任务 D。

### A3 用例扫描排除状态文件

**改了什么文件:行号**
- `scripts/test/mt_case.ps1`：`Get-MtCaseFiles` 内加 `-not Name.StartsWith('.')`
  （约 `:704-722`），并在 `.NOTES` 补记该偏差。

**为什么**
- python 原实现 `Path.glob("*.json")` **不匹配**点文件，pwsh 的 `-like '*.json'` **会**匹配
  `.mt_run_state.json` / `.mt_snapshot.json` ⇒ 这两个运行态文件被当成用例、走进校验分支各记一条
  ERROR。真实用例文件名（`cases/*.json` 全量复核）**没有一个以 `.` 开头**，故不会误伤。

**实跑结果**：见任务 D。

### A4 探针自动部署（避免旧探针假 FAIL）

**改了什么文件:行号**
- `scripts/test/mt_launch.ps1`：新增 `Sync-MtProbeScripts` 函数（约 `:44-118`），并在冷启动前调用
  （约 `:190-195`）。

**为什么**
- TESTING-SPEC §6 规定探针靠**手工**复制，`mt_env` 只管 mods/world、从不刷新
  `run/<版本>/kubejs/`。B1 实测：18:19 的全流程用的仍是 14:54 的旧探针（源 16:33 已更新），
  直接导致 `KOMACHI-EXTRA-PLAY-1.21.1` 旧探针 34/41 FAIL、新探针 41/41 PASS ⇒ 旧探针下是**假 FAIL**。
- 实现：源 → `run/<版本>/kubejs/server_scripts/` **逐文件 SHA256 比对**，不同才复制；复制后**再复核**
  一次哈希，不一致则告警「本轮 AP_ 读数不可信」。每个脚本打一行
  `MT_INFO: PROBE_DEPLOY: <名字> <哈希前12位> <copied|up-to-date|MISMATCH>`，另打一行汇总
  `PROBE_DEPLOY: OK — N 个脚本（复制 x / 已是最新 y）`。
- 位置选在 `mt_launch` 冷启动**紧前**：KubeJS 只在启动时加载 `server_scripts`，且
  `--phase launch` 单阶段同样会走到这里。

**实跑结果**：见任务 D。

---

## 任务 B — 探针重载错误（setDeltaMovement）

**改了什么文件:行号**
- `scripts/test/resources/kubejs/1.21.1/server_scripts/astral_bugfix_probe.js`
  - 新增 `var Vec3Class = Java.loadClass("net.minecraft.world.phys.Vec3");`（`:55-63` 区，AABBClass 之后）
  - `nancySpawnPearl` 内 `pearl.setDeltaMovement(0.0,0.0,0.8)` → `pearl.setDeltaMovement(new Vec3Class(0.0,0.0,0.8))`（原 `:1613`）
- `scripts/test/resources/kubejs/1.20.1/server_scripts/astral_bugfix_probe.js`
  - 同款新增 `Vec3Class`（`:56-62` 区）
  - `nancySpawnPearl` 同款改写（原 `:1604`）

**为什么**
- 用例 `NANCY-LU-PEARL-IMMUNE-1.21.1` 在 P2/P3 相位抛
  `InternalError: Can't find method net.minecraft.world.entity.Entity.setDeltaMovement(number,number,number)`
  ⇒ 珍珠相位整段不执行，17/29，产品**不可判**（ERROR 而非 FAIL）。
- 反编译源核对：**两版本** `Entity.java` 都有 `setDeltaMovement(double,double,double)`
  （1.21.1 `:3477`、1.20.1 `:3374`），所以这是 **Rhino 在 `ThrownEnderpearl` 实例上的重载解析问题**，
  不是「1.21.1 没有三参重载」。改用 `Vec3` 重载可绕开解析歧义：
  `Vec3` 两版本都只有 `Vec3(double,double,double)` 与 `Vec3(Vector3f)`，3 个 number 命中唯一。
- 1.20.1 侧**本来能跑**（它有同名三参重载），但两版本探针必须逐条对等，故一并改成同一写法，
  避免下次移植时把 1.21.1 的坑重新引入。

**实跑结果**：见任务 D。

---

## 任务 C — 用例时序 + 新增用例

### C0 `HOSTILE-TARGET-NEUTRAL` 时序缺陷（臂装步自己劈死了北极熊）

**改了什么文件:行号**
- 两个探针 `doRailgunFriendly`（1.21.1 `:2028-2040` 区 / 1.20.1 `:2006-2018` 区）：
  在 `ANGER_POST` 之后、`meleeHit` **之前**新增
  `runCmd(ctx, "data get entity @e[type=minecraft:polar_bear,limit=1] AngerTime")` +
  `send(ctx, "AP_<tag>_ANGER_NBT:" + angerNbt)`。
- `scripts/test/cases/HOSTILE-TARGET-NEUTRAL-1.21.1.json`：删掉 step「`/data get entity … AngerTime`」
  （原 `:50-52`），该处改为 `wait 900`；NOTE 改写说明取证时刻前移。
- `scripts/test/cases/HOSTILE-TARGET-NEUTRAL-1.20.1.json`：同款（原 `:49-52`）。

**为什么**
- 用例唯一失守的断言是 `北极熊拥有以下实体数据：\d+`，日志回显「未找到实体」。
  机制：`railgunfriendly` 的**臂装步自己就打了那一记近战**（`AP_HN_MELEE:playerAttack`），赐福随即触发、
  落雷 ~1 s 后结算；而注入器每条命令的固定按键序列约 2.5–3 s ⇒ 用例侧那条 `/data get`
  **在时序上永远落在北极熊死亡之后**（30 HP 在窗口内 30→0 并从世界移除）。
- 修法按要求「先把 NBT 回读做掉、再触发落雷」：把同一条**原版命令**交给探针在近战之前执行。
  输出同样是聊天栏 → `latest.log`，**断言文本一字未改**（不是弱化断言、也不是换成探针私有读数）。
  命令经 `performPrefixedCommand`，1.21.1 上延迟到本 tick 末执行，而落雷在 20 tick 后 ⇒ 读数必然作用于存活靶。

**实跑结果**：见任务 D（25/26 → **29/29 PASS**）。

### C1 新增用例 —— 电击手套「轮次归零解除武装」

**改了什么文件:行号**
- 两个探针新增 `gloveArmed()` / `doGloveRound()`（1.21.1 `:2791-2917` 区 / 1.20.1 同构）
  与命令注册 `.then(Commands.literal("gloveround")…)`；新增 `ElectricGloveClass`（两版本）。
- 新增用例 `scripts/test/cases/ELECTRIC-GLOVE-ROUND-RESET-1.21.1.json`、`…-1.20.1.json`（12 断言）。

**为什么**
- 产品侧 `item/card/EffectCardPeriod.java:193-200` 的 `clearRoundBonuses` 现在统一调用
  `ElectricGloveChipItem.disarmAoe(player)`（注释 `:187-191` 说明此前只写在 `tick` 情形 1，
  导致忍者宽限强重置路径口径不等价）。`clearRoundBonuses` 的调用点恰好三条：
  `tick` 情形 1（`:404`）、`registerPlay` 周期边界（`:328`）、`forceResetRound`（`:225`，由
  `BaseSignItem.tickSignActiveLock:337` 调用）。B1 报告已确认「电击手套轮次归零」**零游戏内证据**
  （全部 1.21.1 用例与探针检索 `electric_glove/ELECTRIC_GLOVE/电击手套` 0 命中）。
- 探针四相位（全部走生产入口）：A 正对照（冷却进行中 ⇒ `aoe` 必须仍为 1 且冷却结束时刻未被改写）；
  B `tick` 情形 1；C `registerPlay` 周期边界；D 真实 `BaseSignItem.tickSignActiveLock`
  （忍者锁定态 + `lock_end=0` + 宽限过期 + 期内未出效果牌）⇒ `forceResetRound`。
  读数同时给附件真值 `aoe=` 与生产判定入口 `armed=`（`isEquipped && 附件`）。
- D 相位显式写 `sign_active_max_cooldown=3600`：不调 `performSkillForCurio` 时
  `endLockAndStartCooldown` 的基准为 0，「是否真起了冷却」不可判；这只替换**基准的写入者**，
  不绕过被测路径（已在探针注释里写明）。

**实跑结果**：**PASS 18/18**（`AP_G1_VERDICT:held=1:tick_reset=1:register_reset=1:force_reset=1`、`AP_G1_GLOVE_OK:1`）。

### C4 新增用例 —— 末影骰「保命清 GLOWING」

**改了什么文件:行号**
- 两个探针新增 `enderGlowReset()` / `enderGlowState()` / `applyLethalDamage()` / `doEnderTotem()`
  与命令注册 `.then(Commands.literal("endertotem")…)`；新增 `EnderDiceHandlerClass`、
  `DESC_GLOW` / `DESC_SPEED`（两版本）。
- 新增用例 `scripts/test/cases/ENDER-DICE-TOTEM-GLOWING-1.21.1.json`、`…-1.20.1.json`（12 断言）。

**为什么**
- 产品侧 `event/EnderDiceHandler#onLivingDeath`（1.21.1 `:189-190` / 1.20.1 `:188-189`）：
  移除集合 = `HARMFUL` ∪ {`GLOWING`，**仅当** `player.hasEffect(ModEffects.MARKED)`}。
  B1 报告已确认「末影骰保命清 GLOWING」**零游戏内证据**（全部用例检索 `ender_dice/末影骰/GLOWING` 0 命中）。
- P1：`MARKED + GLOWING + 速度` → 致死 ⇒ 断言 `MARKED` 与 `GLOWING` 均被移除、增益仍在；
  P2 对照：只有 `GLOWING`（无 `MARKED`）→ 致死 ⇒ 断言 `GLOWING` **被保留**（验证门控）。
- 致死伤害用 `damageSources().generic()`：已核对两版本 `bypasses_invulnerability` 标签
  **只含** `minecraft:out_of_world` 与 `minecraft:generic_kill`（从
  `neoforge-21.1.235-client-extra-aka-minecraft-resources.jar` / `client-extra-1.20.1-47.4.10.jar`
  内 `data/minecraft/tags/damage_type/bypasses_invulnerability.json` 读出）⇒ `generic` 不绕过无敌，
  产品与原版不死图腾都会响应（`/kill` 用的 `generic_kill` 则会被两者跳过，故不能用 `AIRBAG` 的手法）。
- 探针先校验 `EnderDiceHandler#hasEnderDie` 为真才施加致死伤害，否则直接 ERR + 回创造，避免误杀玩家。

**实跑结果**：**FAIL 4/12（测试资产不可用，非产品 FAIL）** —— 见任务 D-2 末行：状态构件与 P2 对照相位成立，但环境内**致死注入打不出致死**（`p.hurt` 被 Rhino 包装器错配参数 / `/damage` 被推迟到 tick 末 / `causeFallDamage(100)` 本会话未掉血），末影骰保命未触发。断言**未弱化**，如实保留 FAIL。

---

## 追加任务 — ANVIL 用例/探针缺陷（依据 `docs/batch3/ANVIL-attribution.md`）

### 1 让 block 路径真跑通

**改了什么文件:行号**
- 两个探针 `anvilOpenMenu`（1.21.1 `:801-853` 区 / 1.20.1 同构）：
  - 每一步失败原因都进读数：`AP_<TAG>_SRC:<src>:why=…`（`place_failed` / `provider_null` /
    `block_ex:<异常>` / `access_create` / `access_ex:<异常>` / `null_access`）。
  - **兜底改为 `ContainerLevelAccess.create(level, pos)`**（新增 `ContainerLevelAccessClass`），
    即「真实方块访问」，与 `AnvilBlock#getMenuProvider`（`AnvilBlock.java:76-80`）同源；
    不再退回两参构造（那正是 `ContainerLevelAccess.NULL`）。
  - 放块后加 `getBlockState(pos).is(BlocksClass.ANVIL)` 校验（避免「放块失败但静默走 provider」）。
- 用例 `ANVIL-STAR-UPGRADE-1.21.1.json` / `-1.20.1.json`：`AP_S{1,2,3}_SRC:(block|direct)`
  → `AP_S{1,2,3}_SRC:block`（收紧为硬前置），NOTE 同步改写。

**为什么**
- 归因文档实测 `AP_S*_SRC:direct`：`ItemCombinerMenu#removed` 的退回动作写在
  `this.access.execute(...)` 里，而 `ContainerLevelAccess.NULL` 覆写 `evaluate` 返回 `Optional.empty`、
  `execute` 是接口 default ⇒ `NULL.execute()` **静默 no-op**；两参构造 `new AnvilMenu(id, inv)`
  正是 NULL。旧探针把「放方块 + 取 MenuProvider」放在同一个 try 里，任何一步抛异常都
  `provider = null` 且**不留原因**，实跑因此 100% 落 direct。

### 2 `_INV_AFTER_CLOSE` 按 `_SRC` 分流

- 因为第 1 条已把 `_SRC` 收紧为 `block`（硬前置），block 语义下 `_INV_AFTER_CLOSE` **必须为 10**，
  故该断言**保留为 `:10`**（这就是本次的判定性检查）；只有 `direct` 才「不主张退回」，
  而 `direct` 现在会让 `_SRC:block` 断言直接 FAIL，无需另一条断言分流。
  同时新增只读读数 `AP_<TAG>_S{1,2,3}_{PRE,POST}_STATE` 使该路径完全可判。

### 3 修 Z1 误报

**改了什么文件:行号**
- 两个探针 `doAnvilBags`（1.21.1 `:1095-1130` 区 / 1.20.1 同构）：`ds < 0` 时先从**上一菜单的槽 0**
  把骰子取回（`recovered=from_menu_slot0`），只有两条路都拿不到才报 ERR；
  新增 `AP_<TAG>_Z1_AFTER_CLOSE:ds=…:fs=…:coins=…:recovered=…`。

**为什么**
- 旧判据 `if (ds < 0 || fs < 0) → ERR:lost_after_close` 在 `direct` 路径下**必然触发**
  （骰子还在槽 0，从未回到物品栏），导致 4 条 `AP_Z1_FEW_*` / `AP_Z1_DONE` 断言永远缺失。
- **重要更正**：`AP_Z1_ERR:lost_after_close:-1:5` 里的 `5` **不是「只剩 5 枚星币」**，
  而是 `anvilFindSlot` 返回的**物品栏槽位下标**（`anvilFindSlot` 语义见 `:746-754`）。
  归因文档 §3 末尾「9→5 的 4 枚差额无法判定」是把槽位下标误读成枚数 —— **不存在这 4 枚差额**；
  该相位期望的 `AP_Z1_FEW_TOTAL:9` 本身也证明星币一枚未少。新增的 `_Z1_AFTER_CLOSE` 读数
  把 `ds/fs`（下标）与 `coins=`（枚数）分列，杜绝再次误读。

### 4 修静默吞异常

**改了什么文件:行号**
- 两个探针 `anvilCloseMenu`：`catch (e1)` 不再吞掉，改为
  `how = "closeContainer_ex:" + exText(e1)`，并在 `menuRemoved` 兜底失败时追加 `|removed_ex:<异常>`。

**为什么**
- 旧写法吞异常后只打 `menuRemoved`，实跑 100% 是 `menuRemoved` ⇒ 「哪条路径执行」永远不可判
  （归因文档 §4-6 指出这是修 block 路径的前置信息）。

### 5 只读读数闭合未闭合项

**改了什么文件:行号**
- 两个探针新增 `anvilCloseState()`（新增 `ItemEntityClass`），在 S1/S2/S3 与 Z1 的**关界面前后**各调一次：
  `AP_<TAG>_<phase>_STATE:coins=<物品栏星币枚数>:slot0=<槽0内容>:slot1=<槽1内容>:drops=<玩家 8 格内 ItemEntity 列表|none>`。
  `phase` ∈ `S1_PRE/S1_POST/S2_PRE/S2_POST/S3_PRE/S3_POST/Z1_BAG_PRE/Z1_BAG_POST/Z1_FEW_PRE/Z1_FEW_POST`。
- `closeContainer()` 的异常文本（`e1` 的消息 + 至多 4 帧堆栈，经 `exText`）已并入 `_CLOSE` 读数（第 4 条）。

**为什么**
- 关界面后若出现 `star_coin` 掉落物 ⇒ 走的是 `clearContainer` 的 `drop` 分支（死亡/掉线）；
  若槽内容仍在且物品栏没增加 ⇒ 随菜单丢弃（`NULL.execute` no-op）；若物品栏 = 初始 − 费用 ⇒ 正常退回。

---

## 任务 D — 实跑验证（1.21.1，冷启动 5 轮）

**跑法（= A1/A2/A4 的实际验证）**：`pwsh -File scripts/test/mt.ps1 --version 1.21.1 --phase launch`
（前台即可，A1 已使其不再阻塞到客户端退出）→ 逐条 `mt_case.ps1 run --case …`（每条前再手工 snapshot，
因为会话内 `absent` 断言的粒度是「上一次快照」而非「上一条用例」）。

### D-1 工具链四项的实跑证据

| 项 | 证据 |
|---|---|
| A1 launch 不阻塞 | 启动 5 次全部 `MT_LAUNCH: OK (40s)`，**mt.ps1 进程随即退出**（客户端继续存活并可供 cases 使用；后台作业退出码 0）。⚠️ 残留：若调用方用 `pwsh -Command "… 2>&1"` 这类**管道捕获**包装，包装进程要等客户端退出才结束（实测 5/5 如此）——这是调用方捕获层的句柄继承现象，与 B1 记录的 `-Wait` 等整树不同；用户直接前台调用不受影响。 |
| A2 单阶段写快照 | launch 自身输出 `MT_SNAPSHOT: OK — 1.21.1 run=… latest=68919B …`，`.mt_snapshot.json` mtime 随 launch 更新（此前只有全流程才写）。 |
| A3 状态文件不当作例 | `Get-MtCaseFiles` 返回 **35** 个用例（`cases/` 下 37 个 `*.json` − 2 个点文件），无 `DOTFILE LEAK`。 |
| A4 探针自动部署 | 每轮 launch 都打 `PROBE_DEPLOY: astral_bugfix_probe.js <hash> copied` + `PROBE_DEPLOY: OK — 5 个脚本`；源/部署 SHA256 逐字节一致（`72C6ED98…` → `F6B560DA…` → `1B13D7AC…` → `6AF35DCC…` → `897E3265…`，随本轮编辑推进）。 |

### D-2 逐条结果（最终一轮，探针 `897E3265…`，客户端 20:00 冷启动）

| 用例 | 判定 | 断言 | 说明 |
|---|---|---|---|
| `ANVIL-STAR-UPGRADE-1.21.1` | **PASS** | 53/53 | **判定性检查通过**：`AP_S{1,2,3}_SRC:block:why=none`（真的走了方块 `MenuProvider` ⇒ `ContainerLevelAccess.create`）且 `AP_S{1,2,3}_INV_AFTER_CLOSE:10`（= 初始 − 费用）。⇒ **归因结论成立：3 枚星币是「没退回」而非「被吞」，`direct`(NULL access) 才是 7 的根因** |
| `ELECTRIC-GLOVE-ROUND-RESET-1.21.1` | **PASS** | 18/18 | `AP_G1_VERDICT:held=1:tick_reset=1:register_reset=1:force_reset=1` ⇒ 三条归零路径（tick 情形 1 / registerPlay 边界 / forceResetRound 忍者宽限强重置）**全部**解除武装，未归零时武装仍在 |
| `HOSTILE-TARGET-NEUTRAL-1.21.1` | **PASS** | 29/29 | 由 25/26 变为满分：`北极熊拥有以下实体数据：\d+` 命中 1 次（探针在近战前自跑同一条原版命令） |
| `NANCY-LU-PEARL-IMMUNE-1.21.1` | **FAIL（仅 kubejs 断言）** | 数据断言全 PASS | P2/P3 真实珍珠相位首次跑通：`AP_P2_PEARL:window=…:window_max=19:tel=1:drop=0:hurt=0:invul=0:marked=false`、`AP_P3_PEARL:window=0:window_max=0:tel=1:drop=5:hurt=9`、`P2_OK/P3_OK=1`（免疫相位无反馈、对照相位掉 5）。唯一 FAIL = `kubejs server.log 0 errors`：探针侧仍有 1 条 Rhino/KubeJS **DamageSource 类型包装器**异常（`… damage_type … minecraft:5.0`，1.21.1 tick 路径）。**属测试资产不可用，产品面全部通过** |
| `ENDER-DICE-TOTEM-GLOWING-1.21.1` | **FAIL（测试资产不可用）** | 4/12 | P1/P2 的**状态构件**全部成功（`marked/glow/speed = 1/1/1`、`invul=0`），P2 对照相位的「保留 GLOWING」半边成立（`control_keep=1:marked_cleared=1`），但**致死注入在本环境打不出致死**：`p.hurt(src,num)` 被包装器错配参数、`/damage` 命令被 `performPrefixedCommand` 推迟到 tick 末（同步读不到）、`p.causeFallDamage(100,1,src)` 本次未产生掉血（同会话 5.0 点却有效）⇒ 末影骰保命未触发、`gated_clear=0`。**产品行为不可判（不是产品 FAIL）** |

### D-3 产品级 FAIL

**无。** 本轮所有失守都能归到测试资产（探针/用例/环境注入手段），没有任何一条指向 `neoforge-1.21.1/src` 或 `forge-1.20.1/src` 的行为缺陷。
其中 ANVIL 的归因（原先疑似产品缺陷）已被本轮 `_SRC:block ⇒ _INV_AFTER_CLOSE:10` 的实跑**反证**。

### D-4 未跑条目

1.20.1 全部（用户裁定本轮不跑）；1.21.1 其余回归条目（RAILGUN-* / KOMACHI-* / AIRBAG / EFFECT-DECAY / SPELL-TD / FEN-SPLASH / DIRECTIONAL / EMERALD / NANCY-CLOAK）因时间与上下文预算未在本轮复跑，其最近一次结果见 `docs/batch3/B1-in-game-results.md`。（工具链改动为增量式，不影响这些条目的既有结论。）

---

## 收尾

- 本地提交：`517954f`（15 files changed, 1524 insertions(+), 93 deletions(-)），**未执行 `git push`**。
- 提交只纳入本轮测试资产 15 个文件；工作区里 `AGENTS.md` / `CHANGELOG*` / 产品源码 / 新增 `command/` 目录等改动**属其它并行执行者**，未被本提交纳入。
- 收尾：`--phase stop --force` 后 java 进程计数 **0**（客户端与 Gradle 守护均已退出；只由 mt_cleanup 按本流程标记收停，未误杀任何其它 java 进程）。
