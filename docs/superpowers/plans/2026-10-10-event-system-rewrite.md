# 事件体系重写 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: 用 `subagent-driven-development` 或 `executing-plans` 逐任务实施；步骤用 checkbox(`- [ ]`) 跟踪。

**Goal:** 把散在各立牌里的事件改造成统一事件框架（注册表 + 元数据 + 触发器 + 独立计时器），注册 13 个空壳事件（12 个新事件 + 调查阶段；图标共 16 张），删除大侦探立牌的全部事件并清空其主动技能，让「隐匿调查/调查阶段」改挂新框架。

**Architecture:** 新增 `event/api/**` 承载元数据类型，`AstralEventRegistry` 静态注册；`AstralEventDispatcher` 统一分发（服务端守卫 → 64 格∪团队作用域 → 阶级闸门 → 每玩家独立计时器 → 事件效果 → 既有附加效果层）；旧 `AstralEventSystem` 退化为「附加效果层」并保留薄封装；`InvestigationEventUtil` 只换分发入口、行为不动。

**Tech Stack:** Java 21（1.21.1 NeoForge）/ Java 17（1.20.1 Forge）· 单仓四线 · KubeJS 探针 + `scripts/test/mt.ps1` 测试台 · pwsh 7 守门脚本。

**Spec:** `docs/specs/event-system-rewrite.md`

## Global Constraints

- 落地线：**P0 = `neoforge-1.21.1` + `forge-1.20.1` 同批**；26.1.2 / fabric 本轮不动。
- 版本：四线 `mod_version=2.0.0-SNAPSHOT.60+<platform>`，两份 CHANGELOG 段头同步 `.60`（提交前必须 bump）。
- 类型 = **纯标签**，仅用于界面文本染色：**正面绿 §a / 中性黄 §e / 负面红 §c**；**不得**参与任何结算判定。
- 「调查阶段」= **一条**事件 `investigation_stage` + **4 张图标**（按阶级切换），类型 **POSITIVE**；**只有 `INVESTIGATION_KILL` 能提升阶级**，其它来源只触发当前阶级。
- 状态指示器：**不显示**（只留空接口，不得新增任何 HUD/解释性文本）。
- 作用域：默认 `AstralEventSystem.EVENT_TARGET_RADIUS`（64.0）∪ 团队全体（`PartyRelations.collectEventTargets`）。
- 计时器：**每事件 × 每玩家**独立、重复触发**覆盖/重置**；本轮 16 条正式事件**不登记**计时器（仅临时测试事件登记）。
- 事件 id 一律 ascii snake_case；游戏内贴图 `assets/astral_dice/textures/gui/event/<id>.png`；lang 键 `event.astral_dice.<id>.name` **三语齐全**。
- 文件 CRLF、无 BOM；`git` 一律 `-C <绝对路径>`；**不 push**；pwsh 7。
- 分工（用户 2026-10-10 裁决）：**编码由主 Agent 亲自完成**；`code_review` 做代码复核；**子代理只做功能验证与测试**。
- **允许插入临时测试内容**（一次性事件 + 带计时器事件各一），**测试完成后必须清除并留删除证据**。

## Review Focus

1. **公共池为空 / 全部为 secretOnly** 时抽事件：必须安全失败（不抽、不 NPE、记 warn 机器行）。
2. **同一带计时器事件被重复触发**：必须**重置**到期刻而非叠加（判据：剩余回到满值，且只有一条记录）。
3. **阶级已在 4（真相揭露）时再次击杀**：必须保持 4、不越界，且仍触发一次事件。
4. **客户端调用 dispatcher**：必须 no-op，不崩、不写数据。
5. **未知 eventId**（旧调用点传 `fanny_active` 之类）：必须 warn + 跳过，不得抛异常。
6. **卸下秘密侦探立牌**：阶级清零沿用现行为；**进行中的事件计时器不受影响**（计时器按事件独立、不绑立牌）。

---

## File Structure（1.21.1 线；1.20.1 同批镜像，仅平台 API 写法不同）

**Create**
- `event/api/AstralEventPolarity.java` — 枚举 + `ChatFormatting color()`（正绿/中黄/负红）
- `event/api/AstralEventTier.java` — `STAGE_I(1)…REVELATION(4)` + `int level()` + `int iconIndex()`
- `event/api/AstralEventTrigger.java` — `ACTIVE_SKILL / INVESTIGATION_KILL / ITEM_USE / BLOCK_USE / CUSTOM`
- `event/api/AstralEventEffect.java` — `@FunctionalInterface` + `NOOP` 常量
- `event/api/AstralEvent.java` — `record(id,nameKey,icon,polarity,secretOnly,inPublicPool,triggers,radius,effect,defaultDurationTicks)` + `Component displayName()`
- `event/AstralEventIds.java` — 16 条 `ResourceLocation` 常量
- `event/AstralEventRegistry.java` — 注册 16 条 + `get/all/publicPool/drawRandom/validate()`
- `event/EventTimerStore.java` — `start(ServerPlayer,eventId,ticks)` 覆盖式、`remaining/isActive/clear/tick`
- `event/AstralEventDispatcher.java` — `trigger(Player,ResourceLocation,AstralEventTrigger)`（阶级闸门 + 计时器 + 效果 + 附加效果层）
- `event/client/AstralEventStatusIndicator.java` — **空接口**（本轮无实现）
- `item/EventDrawItem.java` / `block/EventDrawBlock.java` — 抽事件的**独立注册**物品与方块（`ITEM_USE` / `BLOCK_USE`）
- `scripts/verify/verify_event_registry.ps1` — id 唯一 / nameKey 三语 / icon png 存在 / 无散落 id 字面量
- **临时**：`event/AstralEventTestFixtures.java`（`tmp_one_shot` 一次性 + `tmp_timed` 带计时器；**验证后删除**）

**Modify**
- `event/AstralEventSystem.java` — 保留效果层；`onEventTriggered` / `triggerInvestigationEvent` 改薄封装转 dispatcher（未知 id warn）
- `item/sign/FannySignItem.java` — 删 11 项事件（`roll`/`applyEventTo`/actionbar/门控/`fanny_active`），`performSkill` 留空挂载点 + 注释「技能待重做」
- `item/InvestigationEventUtil.java` — 改走 `dispatcher.trigger(..., INVESTIGATION_KILL)`；**施加逻辑一行不改**
- `item/ModItems.java`、`block/ModBlocks.java`、创造栏、三语 lang、`textures/gui/event/*.png`、新物品/方块模型与 blockstate
- 1.20.1 镜像同批；`docs/compat-1.20.1-forge.md`、`AGENTS.md`、`CHANGELOG{,_ZH}.md`、`scripts/test/TESTING-SPEC.md`

---

## Task 1: 元数据类型 + 注册表 + 16 条注册
**Files:** Create `event/api/*.java`、`event/AstralEventIds.java`、`event/AstralEventRegistry.java`；Modify `AstralEventSystem.java`（仅加 `applyEventBonus` 别名）。
**Interfaces — Produces:** `AstralEventRegistry.register/get/all/publicPool/drawRandom/validate`；`AstralEventIds.<NAME>`。
- [ ] 1. 写 `validate()`：id 唯一、nameKey 三语存在、icon 材质存在 ⇒ 违规抛 `IllegalStateException`。
- [ ] 2. 注册 13 条（12 新 + `investigation_stage`）为**空壳**（`effect=NOOP`、`defaultDurationTicks=0`、`radius=64.0`）。
- [ ] 3. 两线 `:<line>:classes` 编译通过。
- [ ] 4. 加启动机器行 `AP_EVENT_REG: count=16:pool=N`（供断言），提交。

## Task 2: 分发器 + 阶级闸门 + 计时器
**Files:** Create `event/AstralEventDispatcher.java`、`event/EventTimerStore.java`；Modify `AstralEventSystem.java`。
**Interfaces — Produces:** `AstralEventDispatcher.trigger(Player,ResourceLocation,AstralEventTrigger)`；`EventTimerStore.start/remaining/isActive/tick`。
- [ ] 1. 实现分发 5 步（客户端守卫 → 作用域 → 阶级闸门 → 计时器 → effect → `applyEventBonus`）。
- [ ] 2. 阶级闸门：`secretOnly && source==INVESTIGATION_KILL` ⇒ `setInvestigationStage(min(now+1,4))`；其它来源只读当前阶级。
- [ ] 3. 计时器覆盖式登记；`tick` 到期调用 `ON_EXPIRE`（本轮仅临时事件用）。
- [ ] 4. 旧入口薄封装（未知 id warn）；`triggerInvestigationEvent(p)` → `trigger(p, investigation_stage, INVESTIGATION_KILL)`。
- [ ] 5. 两线编译 + `validate()` 不抛；提交。

## Task 3: 图标 + 三语 lang + 守门脚本
- [ ] 1. 拷 16 张 png 到 `assets/astral_dice/textures/gui/event/`（ascii id；`investigation_stage` 用 4 张分阶级贴图 `investigation_stage_1..3`、`investigation_stage_revelation`）。
- [ ] 2. 三语 lang 加 `event.astral_dice.<id>.name` 同步。
- [ ] 3. 写 `scripts/verify/verify_event_registry.ps1`（4 项判据）跑绿 + `check_lang_sync.ps1` 三语一致；提交。

## Task 4: 删除大侦探立牌的全部事件
- [ ] 1. 删 `roll = nextInt(1,12)` :43、`applyEventTo` 11 分支 :78 起、11 项 actionbar、门控 :59-61、`onEventTriggered(p,"fanny_active")` :65。
- [ ] 2. `performSkill` 方法体留空 + 注释「技能待重做（2026-10-10）」；保留物品/冷却/挂载点。
- [ ] 3. 两线编译 + grep 证明无 `fanny_active` 残留；提交。

## Task 5: 隐匿调查改挂新框架（行为不回归）
- [ ] 1. `triggerInvestigationEvent(killer)` → `dispatcher.trigger(killer, INVESTIGATION_STAGE, INVESTIGATION_KILL)`。
- [ ] 2. `git diff` 证明施加逻辑一行未改（1200t 隐匿、阶段 II 调查增益、`min(stage+1,4)`、actionbar 全保留）。
- [ ] 3. 两线编译；提交。

## Task 6: 抽事件的物品与方块（独立注册）
- [ ] 1. 物品 `use` ⇒ `ITEM_USE`，从 `publicPool()` 抽一条并 `trigger`（空池 ⇒ warn 机器行、不崩）。
- [ ] 2. 方块右键 ⇒ `BLOCK_USE`，同上。
- [ ] 3. 两者**独立注册**（各自 entry + 创造栏 + 贴图/模型 + 三语 lang）。
- [ ] 4. 两线编译 + 资源完整性 PASS；提交。

## Task 7: 1.20.1 镜像
- [ ] 1. 按 1.20.1 平台写法镜像全部新增/改动（附件/注册/事件总线差异），逐文件对照。
- [ ] 2. 两线 `mt_build` OK；`verify_event_registry.ps1` / `check_lang_sync.ps1` / `verify_resource_integrity.ps1` 全绿；提交。

## Task 8: 临时测试内容（一次性 + 带计时器）→ 子代理实机验证 → 清除
- [ ] 1. 注册 `tmp_one_shot`（触发写 `AP_EVENT_TEST:one_shot:...`）与 `tmp_timed`（`defaultDurationTicks=200`：触发写 `remaining=200`、重复触发**必须重置**为 200、到期写 `expired`）。
- [ ] 2. 派**子代理**冷启动实机跑用例，断言：注册表 `count=18` / 阶级闸门（击杀⇒提升、物品与方块⇒不提升）/ 计时器覆盖重置 / 空池安全失败 / 客户端 no-op / 未知 id warn。
- [ ] 3. 按结果修复（主 Agent 编码）+ `code_review` 复核；重跑至全绿。
- [ ] 4. **清除全部临时内容**（fixtures/用例/探针/临时 lang 键）并附 `git diff` 空 + `grep` 为 0 的删除证据；重跑一次基础用例确认无回归。

## Task 9: 文档 + 提交收口
- [ ] 1. bump 四线 `.60` + 两份 CHANGELOG 段头（玩家可见条目：事件框架 / 新事件图标 / 大侦探技能待重做 / 秘密侦探事件图标）。
- [ ] 2. `AGENTS.md` 追加条目 31（事件体系运行规则：阶级闸门唯一来源、计时器覆盖语义、类型纯标签与染色、状态指示器不显示、抽事件物品与方块独立注册）。
- [ ] 3. `docs/compat-1.20.1-forge.md` 追加本轮平台差异；`TESTING-SPEC.md` 续 76（框架判据与教训）。
- [ ] 4. `code_review` 代码复核 + 子代理独立复核；两线 `mt_build` OK；提交（不 push）。
