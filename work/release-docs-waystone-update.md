# 1.5.0 发布文档更新：Waystone 联动缺口（事实 A）与 26.1.2 验收边界（事实 B）

状态：6 个目标文件已改完，验收全绿；**未**执行 git add / commit / push，未触碰源码 / 资源 / jar / gradle。

## 一、结论速览

- 事实 A（功能缺口）落到玩家侧三份（ZH / EN / MCMOD）的「平台差异」条目 + 两份 CHANGELOG 的「平台差异与已知问题」小节 + MAPPING 的 R-17 / §2 第 31 行。
- 事实 B（工程级受测集边界）只落到两份 CHANGELOG 的「工程」小节 + MAPPING 的 §6 第 16 项 / 新增 §6.1 验证记录；三份玩家说明里未出现任何 `mru` / `sounds` / 版本号细节。
- 版本号：新增文字里没有任何版本号（除平台号 `26.1.2`），无 `rc` 字样；禁用串「试剂-深渊」在 6 个文件里 0 命中（既有物品名「深渊试剂」未改）。
- 行尾：三份 CRLF 文件裸 LF = 0，两份 LF 文件 CRLF = 0（见第四节表）。

## 二、逐文件改动（含改前原文片段）

### 1. `release/1.5.0/PLAYER_CHANGELOG_ZH.md`（CRLF）

位置：`## 平台差异（如实说明）`（原第 364 行）一节内 —— 该小节已存在，按要求**就地追加一条**，没有新建小节。

改前（第 366 行）：

> 四条线的功能与数值一致，但有**四处**行为因平台机制不同而不同。这不是遗漏，读之前先确认自己那条线的情况。

改后（同位置）：

> 四条线的核心玩法与数值一致，但有**五处**行为因平台机制不同而不同。这不是遗漏，读之前先确认自己那条线的情况。

新增条目（现第 372 行，接在原第 4 条「1.20.1 两条线用铁砧代替重锤」之后）：

> - **NeoForge 26.1.2 上「跃迁引擎 × Waystone（传送石碑）」的联动无法接入**：受上游事件机制限制（不是本模组的接入写法问题），该平台不接收传送石碑的传送事件，因此用传送石碑传送不会被当作「Waystone 传送」结算 —— 同维度传送不给跃迁引擎充能与迅捷，跨维度传送只会走维度传送门的通用路径。NeoForge 1.21.1 / Forge 1.20.1 / Fabric 1.20.1 三条线不受影响，联动正常。

连带修订（现第 376 行，`## 已知问题与设计说明` 首条）：

> 改前：…四条线之间的机制差异只有上文「平台差异」一节列出的**四点**。
> 改后：…四条线之间的机制差异只有上文「平台差异」一节列出的**五点**。

刻意不写进玩家侧的内容：类名 / 堆栈 / 事件总线版本 / `Balmstrap` / `validateAbstractChain` 等内部细节（这些只出现在 CHANGELOG 与 MAPPING 的技术条目里）。

### 2. `release/1.5.0/PLAYER_CHANGELOG.md`（CRLF，英文镜像）

位置：`## Platform differences (stated plainly)`（原第 371 行），同样是追加。

改前（第 373 行）：

> All four lines carry the same features and the same numbers, but **four** behaviours differ because the platforms differ underneath. This is not an oversight - know which line you are on before reading.

改后：

> All four lines carry the same core gameplay and the same numbers, but **five** behaviours differ because the platforms differ underneath. This is not an oversight - know which line you are on before reading.

新增条目（现第 379 行）：

> - **On NeoForge 26.1.2 the “Warp Engine × Waystone” linkage cannot be hooked in**: the platform’s upstream event mechanism will not take it (this is not a problem with how this mod hooks in), so teleport-stone teleports are never settled as a “Waystone teleport” there: a same-dimension teleport gives no Warp Engine charge or Speed, and a cross-dimension one only runs the generic dimension-portal path. NeoForge 1.21.1, Forge 1.20.1 and Fabric 1.20.1 are unaffected and keep the linkage.

连带修订（现第 383 行）：

> 改前：…the only mechanical differences between them are the **four** listed under “Platform differences” above.
> 改后：…the only mechanical differences between them are the **five** listed under “Platform differences” above.

中英对称性：条目数 1:1，位置一致（都是该小节最后一条），语义逐句对应（平台范围 / 「不是本模组写法问题」/ 同维度后果 / 跨维度后果 / 另三线不受影响）。

### 3. `release/1.5.0/PLAYER_CHANGELOG_MCMOD.html`（LF）

位置：`[h2=七、平台差异（如实说明）]`（第 341 行）的 `<ul>` 内追加一个 `<li>`，章节编号无需改动（八/九/十保持不变）。

改前（第 342 行）：`<p>四条线的功能与数值一致，但有四处行为因平台机制不同而不同。…</p>`
改后（第 342 行）：`<p>四条线的核心玩法与数值一致，但有五处行为因平台机制不同而不同。…</p>`

新增（现第 348 行，`</ul>` 之前）：

> `<li><b>NeoForge 26.1.2 上「跃迁引擎 × Waystone（传送石碑）」的联动无法接入</b>：受上游事件机制限制（不是本模组的接入写法问题），该平台不接收传送石碑的传送事件，因此用传送石碑传送不会被当作「Waystone 传送」结算 —— 同维度传送不给跃迁引擎充能与迅捷，跨维度传送只会走维度传送门的通用路径。NeoForge 1.21.1 / Forge 1.20.1 / Fabric 1.20.1 三条线不受影响，联动正常。</li>`

连带修订（现第 353 行）：`…只有上文「平台差异」一节列出的四点。` → `…五点。`

自包含校验：全文 `href` / `script` / `http` / `table` / `style=` 各 0 命中；新增 `<li>` 只用了 `<b>`；id 沿用本章节既有写法（裸写 `enable_chip_recipes` / `astral_dice:nbt_shaped`，不加反引号）。

### 4. `CHANGELOG.md`（CRLF）

1.5.0 段（`## 1.5.0 (2026-11-02)`）此前没有平台 / 工程小节。在 `### Licensing` 之前插入两个新小节：

- 第 806 行 `### Platform differences and known issues`，第 808–824 行为 1 条事实 A 条目（`- **The Waystones integration cannot register on NeoForge 26.1.2** (functional gap on that line only, found by the 26.1.2 acceptance run): …`），内容依次覆盖：跨维度传送标记无法安装 → 同维度 / 跨维度两条后果 → 另三线正常且 1.21.1 实机打 INFO `[Astral Dice] Waystone teleport integration enabled for 跃迁引擎.` → 根因（Waystones 2.x 的 Balm 自定义事件 `net.blay09.mods.waystones.api.event.WaystoneTeleportEvent$Before/$Prepare/$After/$Complete`，由 `Balmstrap.createBoundCustomEvent` 生成；抽象父类链以 `java.lang.Object` 为直接父；NeoForge `bus-8.0.5` 的 `EventBus.validateAbstractChain(EventBus.java:301)` 抛 `IllegalArgumentException: Abstract event class ... has a non-abstract superclass class java.lang.Object. The superclass must be made abstract.`）→ 本版处置（`neoforge-26.1.2/src/main/java/com/merlinkitsune/astral_dice/event/WaystoneWarpCompat.java` 新增 `isPlatformUnregistrable(Throwable)`（:126）把该失败从 WARN 降为 DEBUG，其余异常仍 WARN 带栈；`TELEPORT_PRE_EVENT_CANDIDATES`（:48）按 `$Pre` -> `$Before` -> `$Prepare` 探测）。
- 第 826 行 `### Engineering`，第 828–832 行为 1 条事实 B 条目（`- **The 26.1.2 live acceptance ran on a reduced mod set** (engineering record only, no player-visible change): …`）：受测集 = 玩家实例 95 件 − `mru` − `sounds` + `rhino` + `better-advanced-tooltips`；原因是测试台 NeoForge **26.1.2.109** vs `mru` 的 `neoforge.mods.toml` 要求 **>= 26.1.2.114**；`sounds` 与 `mru` 同源一并排除；`astral_dice` 本体未受影响；共存性仍未验证（指向 `docs/porting/MAPPING-1.5.0.md` section 6）。

体例参照：1.3.7-hotfix 段已有的 `### Engineering` 小节（加粗引导句 + 文件路径 + 缩进续行），新小节沿用同一写法。

### 5. `CHANGELOG_ZH.md`（CRLF）

与英文侧结构一一对应：第 663 行 `### 平台差异与已知问题` + 第 665–678 行条目（`- **NeoForge 26.1.2 上 Waystone（传送石碑）联动无法注册**（仅该线的功能缺口，由 26.1.2 实机验收发现）：…`）；第 680 行 `### 工程` + 第 682–685 行条目（`- **26.1.2 实机验收用的是缩减排模组集**（仅工程记录，玩家无感）：受测集 = 玩家实例 **95 件 − `mru` − `sounds` + `rhino` + `better-advanced-tooltips`**，…）。

条目数对称（脚本判据）：1.5.0 段 EN 213 条 / ZH 213 条；`### 新增内容` 82/82、`### 内容与平衡性调整` 40/40、`### 已修复BUG` 57/57、新增 `### 平台差异与已知问题` 1/1、新增 `### 工程` 1/1、`### 许可与授权` 6/6、`### 由 1.3.5-alpha.1 未发布开发线并入` 26/26。

### 6. `docs/porting/MAPPING-1.5.0.md`（LF）

- §2 差异总表新增第 31 行（第 113 行）：`| 31 | 跃迁引擎 × Waystone（传送石碑）传送联动 | … | **缺失**（仅 26.1.2；根因在上游事件机制，非本模组写法，详见 §4.1 R-17） |`，三个平台的单元格逐列填了各自的注册类/行号（基准线 `WaystoneWarpCompat.java:46/:48/:50/:53`、n26 候选链 `:48` + `isPlatformUnregistrable` `:126`）。
- §4.1 风险条目新增 `R-17`（第 185 行）：差异描述、影响（`isPendingAndClear` 恒为 false ⇒ 跨维度只走 `onDimensionTravel` 通用路径 `item/chip/WarpEngineChipItem.java:84-97`）、`需同步?=否（本模组侧不可修；属上游事件机制限制）`、以及「根因一句话」+ 证据行号（`:48`、`:62-80`、`:70`、`:126`，对照另三线 `:41`、`:46`/`:48`/`:50`、`:53`）。
- §6 未验证项新增第 16 项（第 298 行）：`26.1.2 上 mru / sounds 与 astral_dice 能否共存`，最小验证动作 = 把 NeoForge 升到 ≥ 26.1.2.114 后整集启动并核对加载日志（顺带确认 Waystone 联动按 R-17 降级为 DEBUG 而非 WARN）。
- 计数行（第 300 行）由 `15 项` 改为 `16 项`：`> 计数：**16 项**未验证；其中 9 项来自 ALIGNMENT-AUDIT 的 U1–U9，7 项为本轮 mapping 新发现（含 1 项工作树状态确认、1 项 26.1.2 实机验收的受测模组集边界）。`
- 新增 §6.1 `验证记录：26.1.2 实机验收的受测模组集`（第 302–308 行）：受测集 / 排除原因 / 对结论的影响三条；明确「本节不新增未验证项」。

## 三、一致性自检

- **术语统一**：初稿在 `CHANGELOG_ZH.md` 与 `MAPPING-1.5.0.md` 用了「指路石」，与玩家侧的「传送石碑」不一致 → 已统一为「Waystone（传送石碑）」（`CHANGELOG_ZH.md` 2 处、`MAPPING-1.5.0.md` 2 处）；复查 `rg -c '指路石'` 这三个文件 0 命中。
- **数字同步**：三份玩家说明的「四处 → 五处 / four → five」与「四点 → 五点 / four listed → five listed」全部改到；`rg '四处|four behaviours' release/1.5.0/` 0 命中。
- **版本号 / 禁区词**：新增文字无版本号、无 `rc`；「试剂-深渊」在 6 文件 0 命中（第 378 行既有的「深渊试剂」是物品名，未改）。
- **HTML 自包含**：MCMOD 全文 `href` / `script` / `http` / `table` / `style=` 各 0 命中。
- **未越界**：只改了 6 个目标文件 + 本交付文档；`git status` 里 `CHANGELOG.md` 的 50 行改动含既有未提交内容（12 处删除等来自 1.5.0 收尾，非本次），本次只增加了那两个小节。

## 四、验收输出

### 1) `python tools/check_changelog_parity.py` ⇒ exit 0

```
WARN P2 ## 1.3.0: entries equal (58/58) but bold-start counts differ EN=53 ZH=40 (historical style difference; not a failure)
WARN P2 ## 2.0.0-SNAPSHOT.5: entries equal (2/2) but bold-start counts differ EN=2 ZH=1 (historical style difference; not a failure)
CHANGELOG_PARITY: OK sections=26 entries=787/787
```

（两条 WARN 是既存历史小节 `## 1.3.0` 与 `## 2.0.0-SNAPSHOT.5` 的加粗起始句数量差，脚本自身标注 not a failure，本次未触碰。）

### 2) `python tools/check_lang_sync.py` ⇒ exit 0（四行 [OK]，无副作用）

```
[OK]   neoforge-1.21.1: en_us=1142 键，3 份语言键集一致
[OK]   forge-1.20.1: en_us=1142 键，3 份语言键集一致
[OK]   neoforge-26.1.2: en_us=1166 键，3 份语言键集一致
[OK]   fabric-1.20.1: en_us=1151 键，3 份语言键集一致
```

### 3) `rg -n 'Waystone|传送石碑|跃迁' release/1.5.0/ CHANGELOG.md CHANGELOG_ZH.md docs/porting/MAPPING-1.5.0.md` ⇒ 35 行命中（其中 22 行为本次新增/修订，其余 13 行为既存条目）

原始输出（未截断，同时存于 `work/_rg_waystone.txt`）：

```
docs/porting/MAPPING-1.5.0.md:113:| 31 | 跃迁引擎 × Waystone（传送石碑）传送联动 | 反射注册三件套：`...WaystoneTeleportEvent$Pre`（`event/WaystoneWarpCompat.java:46`）、`WaystoneTeleportEntityEvent$Post`（`:48`）、`...WaystoneTeleportEvent$Complete`（`:50`），成功后打 INFO「Waystone teleport integration enabled」（`:53`）；`addListener(Class, Consumer)`（`:93-94`） | 同基准（同文件 `:41`、`:46`/`:48`/`:50`、`:53`；`addListener` 4 参带优先级 `:95-97`） | **联动注册不上**：同一文件改用候选类链 `TELEPORT_PRE_EVENT_CANDIDATES`（`:48` = `$Pre` → `$Before` → `$Prepare`）并新增 `isPlatformUnregistrable(Throwable)`（`:126`）把该失败从 WARN 降为 DEBUG；Waystones 2.x 的 Balm 自定义事件抽象父类链以 `java.lang.Object` 为直接父，被 NeoForge `bus-8.0.5` 的 `EventBus.validateAbstractChain(EventBus.java:301)` 拒绝 ⇒ 该线不产生「Waystone 传送」结算（同维度不计，跨维度退化为维度门通用路径） | 同基准（同文件 `:41`、`:46`/`:48`/`:50`、`:53`；`:95-97`） | **缺失**（仅 26.1.2；根因在上游事件机制，非本模组写法，详见 §4.1 R-17） |
docs/porting/MAPPING-1.5.0.md:185:| R-17 | **26.1.2 的「跃迁引擎 × 传送石碑」联动注册不上，另三线正常**（同功能实现差异；根因不在本模组写法） | 该线上跃迁引擎不按「Waystone 传送」结算：`isPendingAndClear` 恒为 false ⇒ 跨维度传送只走 `onDimensionTravel` 的通用路径（`item/chip/WarpEngineChipItem.java:84-97`），同维度传送既无充能也无迅捷；玩家可见差异仅出现在 26.1.2 | 否（本模组侧不可修；属上游事件机制限制，已在日志里降级为 DEBUG） | 根因一句话：Waystones 2.x 用 Balm 自定义事件（`net.blay09.mods.waystones.api.event.WaystoneTeleportEvent$Before/$Prepare/$After/$Complete`，`Balmstrap.createBoundCustomEvent` 生成），其抽象父类链以 `java.lang.Object` 为直接父，被 NeoForge `bus-8.0.5` 的 `EventBus.validateAbstractChain(EventBus.java:301)` 抛 `IllegalArgumentException: Abstract event class ... has a non-abstract superclass class java.lang.Object.` 拒绝 ⇒ 该线任何 `NeoForge.EVENT_BUS` 监听都注册不上。证据：`neoforge-26.1.2/src/main/java/com/merlinkitsune/astral_dice/event/WaystoneWarpCompat.java:48`（候选链）、`:62-80`（三处注册同处 try）、`:70`（INFO 不可达）、`:126`（`isPlatformUnregistrable`）；对照 `neoforge-1.21.1` / `forge-1.20.1` / `fabric-1.20.1` 同文件 `:41`、`:46`/`:48`/`:50`、`:53` |
docs/porting/MAPPING-1.5.0.md:298:| 16 | 26.1.2 上 `mru` / `sounds` 与 astral_dice 能否共存（本轮实机验收把它们排除在外） | 测试台 NeoForge 为 26.1.2.109，而 `mru` 的 `neoforge.mods.toml` 要求 ≥ 26.1.2.114，两者无法同台；共存冲突无法静态判定 | 把 NeoForge 升到 ≥ 26.1.2.114 后，用「玩家实例 + `mru` + `sounds`」启动一次并核对加载日志；顺带确认 Waystone 联动仍按 R-17 降级为 DEBUG 而非 WARN | R-17 / §6.1 |
CHANGELOG_ZH.md:425:  - **跃迁引擎** —— 维度传送门 / Waystone 的共享冷却 **5:00 → 3:00**；末影珍珠与末影骰子瞬移仍无冷却。
CHANGELOG_ZH.md:439:  **主版本跃迁**（`1.0.4` → `2.0.0`），符合库自身「改变既有语义必须升主版本」的约定 —— 本次把「护甲 20 点上限」
CHANGELOG_ZH.md:665:- **NeoForge 26.1.2 上 Waystone（传送石碑）联动无法注册**（仅该线的功能缺口，由 26.1.2 实机验收发现）：跃迁引擎与
CHANGELOG_ZH.md:666:  Waystone 传送共用的跨维度传送标记在该平台永远装不上，因此用传送石碑传送不会被当作「Waystone 传送」结算 ——
CHANGELOG_ZH.md:667:  同维度传送不给跃迁引擎充能与迅捷，跨维度传送只走维度传送门的通用路径；NeoForge 1.21.1 / Forge 1.20.1 /
CHANGELOG_ZH.md:668:  Fabric 1.20.1 三线联动正常（1.21.1 实机打 INFO `[Astral Dice] Waystone teleport integration enabled for 跃迁引擎.`）。
CHANGELOG_ZH.md:669:  根因在上游，不是本模组的注册写法：Waystones 2.x 把传送事件做成 Balm 自定义事件
CHANGELOG_ZH.md:670:  （`net.blay09.mods.waystones.api.event.WaystoneTeleportEvent$Before/$Prepare/$After/$Complete`，由
CHANGELOG_ZH.md:675:  本版处置：`neoforge-26.1.2/src/main/java/com/merlinkitsune/astral_dice/event/WaystoneWarpCompat.java` 新增
CHANGELOG_ZH.md:678:  逐个探测，同一份实现兼容两代 Waystones。
CHANGELOG_ZH.md:1162:- **看板娘立牌（mimi）被动「每累计 25 张战斗牌送 1 个随机筹码」的筹码池改为运行时派生：此前 20 个后加的筹码一个都拿不到**（2026-10-01 用户实报）：该池原本是三张**手写清单**（蓝 13 / 紫 16 / 金 12，共 41 个），而实际注册的筹码有 **61** 个 ⇒ **缺 20 个**（稀有 6 / 史诗 8 / 传奇 6；按流派 = 充能类 10 / 无流派 7 / 星光（飞星）2 / 治愈 1），其中就包括玩家点名的**充能类**（能量回收器 / 电流剑 / 跃迁引擎 / 电流核心 / 先进外设 / 电流手套 / 安全气囊 / 永动机 / 磁轨炮 / 原初核心）与**飞星**（紫色飞星 / 金色飞星）。**修法**：不再维护手写表，改为**运行时遍历物品注册表派生** —— 取所有「继承 `BaseChipItem` 的物品」，按本模组档位分三桶（蓝 = 稀有 60% / 紫 = 史诗 35% / 金 = 传奇 5%）⇒ **以后新增筹码自动进池，不需要再改这段代码**（旧表已删除，四线同步）。口径与旧清单保持一致：`blank_chip`（空白筹码，不是 `BaseChipItem`）不进池、**进阶筹码照旧进池**、巅峰 / 奇特按既有「不进池」裁决排除（当前无此类筹码；将来若出现会打一条日志，**不静默丢弃**）；三档概率与「累计 25 张」阈值都不变。首次建池时会打一条可断言的读数 `AP_MIMI_CHIP_POOL: blue=… purple=… gold=… total=…`，便于冒烟核验。
CHANGELOG_ZH.md:1394:- **一批在移植过程中未接通的机制已全部接通**：首次开启战利品箱的赠礼、末影珍珠落地的免疫窗口、跃迁引擎经维度门的充能、
CHANGELOG_ZH.md:2014:- 新增充能流派基础设施:图标取自 `images/充能.png`(效果纹理 `mob_effect/charge.png`);最大层数写入 `GameplayConstants.CHARGE_MAX_STACKS = 20`;拥有至少 1 层充能时使立牌主动/效果牌冷却时间 -20%(经 `ChargeManager`,接入立牌冷却入口与效果牌冷却;**不提供任何防御力/护甲加成**);充能层数死亡不丢失(死亡前 `ChargeManager.preserveOnDeath`、重生恢复);获得/消耗来源统一调用 `ChargeManager.addStacks/consumeOne/removeAll`(双版本一致)。5 个充能筹码(跃迁引擎/能量回收器/电流剑/高级外设/永动机)的 tooltip 统一追加「当前充能」计数器(显示 `当前层数 / CHARGE_MAX_STACKS`,经 `ModTooltipHandler.addChargeCounter` → lang `tooltip.astral_dice.chip.charge`)(双版本一致)。
CHANGELOG_ZH.md:2031:- 新增充能筹码「跃迁引擎」(`warp_engine_chip`,稀有):触发指定传送后充能 +2 并获得迅捷 0:10(计入末影珍珠、末影骰子不死图腾后的安全瞬移、维度传送门、Waystone;传送门与 Waystone 共享 5:00 冷却,末影珍珠与末影骰子瞬移不受该冷却限制);配方 = `XXX/WCW/BBB`(X=末影珍珠、W=导电线材、C=空白筹码、B=星币)(双版本一致)。
CHANGELOG_ZH.md:2086:- 充能类筹码展示顺序调整：「永动机」移至「电磁炮」紧邻之前（创造物品栏与帕秋莉手册「充能类筹码」章节同步重排，两处顺序保持一致；调整后顺序为跃迁引擎→能量回收器→电流剑→高级外设→电流核心→电击手套→安全气囊→永动机→电磁炮→原初核心）（双版本一致）。
CHANGELOG_ZH.md:2119:- 补全物品汇总标签:此前新增的筹码/立牌遗漏进 `astral_dice:chips` / `astral_dice:signs` 汇总标签——筹码 12 个(肾上腺素-一般/高效、跃迁引擎/能量回收器/电流剑/高级外设/永动机/电流核心、大碗炖肉/会员推荐信/书签/小猪存钱罐/智能手表)、立牌 2 个(枪匠/肉弹战车);现已补全,两版本汇总标签含全部筹码(55)与立牌(17)(双版本一致)。
CHANGELOG_ZH.md:2134:- 修复筹码 tooltip/手册与实现不一致(逐条校正文案,不改变数值;双版本一致):① 摩托头盔三档的**防御力口径**修正——文案保持「防御力 +2/+4/+6」不变,代码层改为按 1 防御力 = 2 护甲值折算为真实护甲 `Attributes.ARMOR`(+4/+8/+12,常量 `MotoHelmetChipItem.ARMOR_PER_DEFENSE = 2`),即**用代码换算对齐文案**,而不是把文案改成护甲值;② 拳击手套中文 tooltip 缺「骰神赐福」限定(英文与手册已有);③ ATM 机「+40%%」补注**向下取整且下限为 1**(截断后不足 1 枚时按 1 枚计,`ResourceConversion.starlightToStarCoins`);④ 普通/鹰眼瞄具的标记施加补注「骰神赐福期间」;⑤ 美工刀补注「处于汲取状态时视为满血」与「不低于 60%%」边界;⑥ 友情徽章补注「同一治疗者 1 秒内仅触发一次」;⑦ 魔法秘典/魔法箭袋/八面骰/电流剑/能量回收器/跃迁引擎/原初核心/安全气囊分别补注卸下清除计数或追踪、共享 5:00 冷却、赋能上限 10 层与卸下清空、无视无敌的致死伤害不参与;⑧ 手持风扇手册补注「需指定目标的立牌在按下进入待命时即触发」;⑨ 物品名「标记喷罐」——早期误判为《吉星派对》原始专有名词「标记喷灌」并保留原名,现按最新裁定更正为「标记喷罐」。
CHANGELOG.md:515:  - **Warp Engine** - the shared cooldown for dimension portals / Waystones goes **5:00 → 3:00**; ender pearls and the Ender Die's warp still have no cooldown.
CHANGELOG.md:808:- **The Waystones integration cannot register on NeoForge 26.1.2** (functional gap on that line only, found by the
CHANGELOG.md:809:  26.1.2 acceptance run): the cross-dimension teleport mark that the Warp Engine chip and Waystone teleports share is
CHANGELOG.md:810:  never installed there, so a teleport-stone teleport is not settled as a "Waystone teleport" on 26.1.2 - a
CHANGELOG.md:813:  `[Astral Dice] Waystone teleport integration enabled for 跃迁引擎.` at INFO).
CHANGELOG.md:814:  Root cause, upstream and not this mod's hooking style: Waystones 2.x raises its teleport events as Balm custom events
CHANGELOG.md:815:  (`net.blay09.mods.waystones.api.event.WaystoneTeleportEvent$Before/$Prepare/$After/$Complete`, generated by
CHANGELOG.md:820:  Handling in this release: `neoforge-26.1.2/src/main/java/com/merlinkitsune/astral_dice/event/WaystoneWarpCompat.java`
CHANGELOG.md:823:  `TELEPORT_PRE_EVENT_CANDIDATES` (:48) probes `$Pre` -> `$Before` -> `$Prepare`, so one file serves both Waystones
CHANGELOG.md:2461:- New Charge chip "Warp Engine" (`warp_engine_chip`, rare): charge +2 and Speed (0:10) after select teleports (ender pearl, the Ender Die's safe blink after its totem, dimension portals and Waystones; portals and Waystones share a 5:00 cooldown, while ender pearl and Ender Die blinks ignore it); recipe = `XXX/WCW/BBB` (X=Ender Pearl, W=Conductive Wire, C=Blank Chip, B=Star Coin) (both loaders).
release/1.5.0/PLAYER_CHANGELOG_ZH.md:292:    跃迁引擎（共享冷却 **5:00 → 3:00**）、永动机（补足档 **6 → 8**，且装备期间原版近战冷却 **−50%**，攻速约 ×2；骰战不抬攻击力）、
release/1.5.0/PLAYER_CHANGELOG_ZH.md:372:- **NeoForge 26.1.2 上「跃迁引擎 × Waystone（传送石碑）」的联动无法接入**：受上游事件机制限制（不是本模组的接入写法问题），该平台不接收传送石碑的传送事件，因此用传送石碑传送不会被当作「Waystone 传送」结算 —— 同维度传送不给跃迁引擎充能与迅捷，跨维度传送只会走维度传送门的通用路径。NeoForge 1.21.1 / Forge 1.20.1 / Fabric 1.20.1 三条线不受影响，联动正常。
release/1.5.0/PLAYER_CHANGELOG_MCMOD.html:264:<li>银行卡-用不完（未组队时作用距离收紧到 <b>32 格</b>）、大碗炖肉（未组队 <b>16 格</b>）、魔法箭袋（返还<b>本次使用的那一张</b>）、鹰眼瞄具（改为额外攻击伤害，层数 ×2）、跃迁引擎（共享冷却 <b>5:00 → 3:00</b>）、永动机（补足档 <b>6 → 8</b>，且装备期间原版近战冷却 <b>−50%</b>，攻速约 ×2；骰战不抬攻击力）、书签（<b>+1 → +2</b>）；</li>
release/1.5.0/PLAYER_CHANGELOG_MCMOD.html:348:<li><b>NeoForge 26.1.2 上「跃迁引擎 × Waystone（传送石碑）」的联动无法接入</b>：受上游事件机制限制（不是本模组的接入写法问题），该平台不接收传送石碑的传送事件，因此用传送石碑传送不会被当作「Waystone 传送」结算 —— 同维度传送不给跃迁引擎充能与迅捷，跨维度传送只会走维度传送门的通用路径。NeoForge 1.21.1 / Forge 1.20.1 / Fabric 1.20.1 三条线不受影响，联动正常。</li>
release/1.5.0/PLAYER_CHANGELOG.md:379:- **On NeoForge 26.1.2 the “Warp Engine × Waystone” linkage cannot be hooked in**: the platform’s upstream event mechanism will not take it (this is not a problem with how this mod hooks in), so teleport-stone teleports are never settled as a “Waystone teleport” there: a same-dimension teleport gives no Warp Engine charge or Speed, and a cross-dimension one only runs the generic dimension-portal path. NeoForge 1.21.1, Forge 1.20.1 and Fabric 1.20.1 are unaffected and keep the linkage.
```

命中分布：`docs/porting/MAPPING-1.5.0.md:113/185/298`（新增）、`CHANGELOG_ZH.md:665-678`（新增 8 行）、`CHANGELOG.md:808-823`（新增 8 行）、`release/1.5.0/PLAYER_CHANGELOG_ZH.md:372`、`release/1.5.0/PLAYER_CHANGELOG_MCMOD.html:348`、`release/1.5.0/PLAYER_CHANGELOG.md:379`（各 1 行新增）；其余 13 行为 1.5.x 历史条目（Warp Engine 冷却、充能流派、筹码池等），未改动。

注：按任务书原样给的模式（`Waystone|指路石|跃迁`）重跑，同样是 **35 行、位置集合完全相同**（原先含「指路石」的那 4 行同时也含「Waystone」或「跃迁」，术语统一不会改变命中面）；上面的输出用的是统一后的「传送石碑」版本。

### 4) 三份 release 文件（+ 另三份）行尾统计

```
release/1.5.0/PLAYER_CHANGELOG_ZH.md    bytes=54351  CRLF=408  bareLF=0  strayCR=0  lines=408
release/1.5.0/PLAYER_CHANGELOG.md       bytes=56459  CRLF=415  bareLF=0  strayCR=0  lines=415
release/1.5.0/PLAYER_CHANGELOG_MCMOD.html bytes=56902 CRLF=0  bareLF=385 strayCR=0  lines=385
CHANGELOG.md                            bytes=565091 CRLF=2865 bareLF=0  strayCR=0  lines=2865
CHANGELOG_ZH.md                         bytes=505495 CRLF=2435 bareLF=0  strayCR=0  lines=2435
docs/porting/MAPPING-1.5.0.md           bytes=60335  CRLF=0   bareLF=330 strayCR=0  lines=330
```

判读：三份 CRLF 文件裸 LF = 0（无混入），两份 LF 文件 stray CR = 0（无混入 CRLF），全部文件以单个换行结尾。

## 五、剩下的风险

1. **26.1.2 的玩家可见后果是静态推导 + 日志降级，没有游戏内对照实测**：本轮按规则没有启动实机，「同维度不计、跨维度退化为通用路径」来自 `WaystoneWarpCompat` 与 `WarpEngineChipItem` 的代码路径推导（三处注册同一 try 块，首个 `addListener` 抛异常后后两个也注册不上）。建议下次 26.1.2 实机时顺手做一次对照：同维度石碑传送 → 充能不涨；跨维度石碑传送 → 仍能用但按维度门冷却结算。
2. **共存性仍未验证（事实 B 的直接后果）**：`mru` / `sounds` 在 26.1.2 上与本模组的共存没有测过，只在文档里标成未验证项。测试台升到 ≥ 26.1.2.114 后需补一次整集启动，并回填 MAPPING §6 第 16 项。
3. **R-17 的「需同步=否」是「本模组侧不可修」的判断**，不是「永远不需要动」：若上游 Waystones/Balm 或 NeoForge 修掉这个抽象链约束，或 Waystones 换掉事件机制，需回填 R-17 并复查日志降级逻辑（届时 DEBUG 降级会把「能注册了」和「仍然注册失败」混在一起，可能掩盖真实成功）。
4. **未验证项从 15 项变 16 项**：`docs/` 或交接文档里若有别处引用「15 项未验证」，本次未同步（不在 6 个目标文件范围内），可能出现口径差异。
5. **术语已在本批内统一为「传送石碑」，但仓里其他历史文档仍可能用「指路石」**（`work/n26-waystone-and-guide-fix.md` 里的模组包名本身是「[传送石碑／指路石]」双写），未做全仓统一（未授权）。
6. **参考数已经过时**：三份玩家说明第 292/379 行等处的 1.5.0 内容本轮未复核，只改了平台差异这一节；若后续还有平台差异增删，三处「五处 / 五点」要一起改。

## 六、产物与未做的事

- 交付：本文件；`work/_rg_waystone.txt`（rg 原始输出，便于复核）。
- 未做：`git add` / `commit` / `push` / 打 tag；未改任何源码、资源、jar、gradle；未启动实机；未改动工作树里既有的其他脏文件。
