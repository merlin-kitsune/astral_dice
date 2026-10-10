# 事件体系重写 · 设计规格（Spec）

> 状态：**待用户评审**（2026-10-10）。评审通过后才进入 `writing-plans` 出实施计划。
> 落地线：**P0 两线同批** `neoforge-1.21.1` + `forge-1.20.1`（26.1.2 / fabric 留待 P1）。

## 1. 目标

把「事件」从各立牌内部拔出来，建成**统一事件框架**：事件 = 一条注册项，携带 名称 / 图标 / 类型 / 阶级 / 作用域 / 触发器 /（可选）独立计时器 /（框架级）状态指示器；
**本轮只搭框架 + 注册 16 个空壳事件**（12 新 + 4 秘密侦探图标），**不实现任何具体效果**；同时**删除大侦探立牌主动技能注册的全部事件并清空该技能**；**隐匿调查保留既有行为**、改挂新框架并补图标。

## 2. 用户已裁定口径（不可擅改）

| # | 裁定 | 对设计的影响 |
|---|---|---|
| 1 | 保留：事件功能、触发者、作用域（**64 格 ∪ 团队全体**）、事件阶级（秘密侦探事件专属）、角色立牌联动钩子（调查员、秘密侦探） | 作用域与联动钩子沿用现成实现，不改语义 |
| 2 | **类型（正面/中性/负面）是纯标签**，只影响后续界面文本染色 | 类型**不得**参与任何结算判定；染色已定：**正面=绿 §a / 中性=黄 §e / 负面=红 §c** |
| 3 | **计时器完全独立、允许覆盖（重置）** | 计时器 = **每事件 × 每玩家**独立量；重复触发即覆盖/重置 |
| 4 | 秘密侦探专属「调查阶段」：**击杀隐匿调查目标 → 触发并提升一个阶级**；后续按特定条件进入**公共事件池**，但**只有击杀隐匿调查触发才能提升阶级**，其它来源**只触发当前阶级** | 阶级=事件自身进度；**提升闸门绑死触发源** |
| 5 | 删除大侦探立牌主动技能注册的**全部事件**，**清空主动技能（留空）**，技能待重做 | 删事件、保留物品与技能挂载点 |
| 6 | 本轮**全部空壳**；**只有个别事件需要计时器**，其余一次性 ⇒ **暂时都不加计时器** | 框架**支持**独立计时器，本轮**不注册**任何计时器 |
| 7 | 隐匿调查：**现有功能原样保留**，仅改挂新框架 + 补图标 | 施加逻辑不动，只换事件分发入口 |

## 3. 现状（实物证据）

- `event/AstralEventSystem.java`（121 行）**不是**事件体系，只是「事件触发后的统一附加效果层」：`onEventTriggered(Player,String)` :32、`triggerInvestigationEvent` :42、`applyRinSignPassive` :60/:71/:86、`EVENT_TARGET_RADIUS = 64.0` :29；作用域 = `PartyRelations.collectEventTargets(triggerer, 64)` :36。
- 事件本体散在立牌里：`item/sign/FannySignItem.java` 主动技能 `roll = nextInt(1,12)` :43 → `applyEventTo` 的 **11 项**硬编码事件（:78 起 switch 1..11），广播 64 格 :49-55，门控计时器 :59-61，末尾 `onEventTriggered(player,"fanny_active")` :65。
- 秘密侦探：`item/InvestigationEventUtil.triggerByKill(killer, applier, markLevel)` :41 —— 读 `ModAttachments.getInvestigationStage(applier)` :44 → 施加阶段效果（`CONCEALMENT_TICKS = 1200` :61，阶段 II 起另加调查增益 :69+）→ `setInvestigationStage(min(stage+1, 4))` :48/:55 → `AstralEventSystem.triggerInvestigationEvent(killer)` :49/:56 → actionbar :50/:57。**阶梯 0..4，卸下立牌才清零** ⇒ 与新口径「只有击杀隐匿调查提升阶级」天然一致。
- 立牌 id：大侦探 `item.astral_dice.fanny_sign`、秘密侦探 `item.astral_dice.bonnie_sign`。
- 图标素材：`images/card+event/` 共 42 张，本轮 16 个事件名**全部存在**。

## 4. 设计

### 4.1 新增类型（两线同构）

```java
// event/api/（以 1.21.1 为例；1.20.1 同名同构，仅平台 API 写法不同）
public enum AstralEventPolarity { POSITIVE, NEUTRAL, NEGATIVE }            // 纯标签：仅用于界面染色
public enum AstralEventTier { STAGE_I(1), STAGE_II(2), STAGE_III(3), REVELATION(4) }  // 仅秘密侦探事件使用
public enum AstralEventTrigger { ACTIVE_SKILL, INVESTIGATION_KILL, ITEM_USE, BLOCK_USE, CUSTOM }

public record AstralEvent(
        ResourceLocation id,            // astral_dice:<snake_case>
        String nameKey,                 // "event.astral_dice.<id>.name"（三语必须齐）
        ResourceLocation icon,          // astral_dice:textures/gui/event/<id>.png
        AstralEventPolarity polarity,   // 纯标签
        boolean secretOnly,             // 是否「秘密侦探专属」阶级事件
        Set<AstralEventTrigger> triggers,
        double radius,                  // 默认 AstralEventSystem.EVENT_TARGET_RADIUS (64.0)
        AstralEventEffect effect,       // 本轮全部 NOOP
        int defaultDurationTicks        // 0 = 无计时器（本轮 16 条全部 0）
) {}

@FunctionalInterface public interface AstralEventEffect { void apply(ServerLevel level, Player triggerer, Player target, AstralEvent event); }
```

### 4.2 注册表与分发

- `event/AstralEventRegistry`：`register(AstralEvent)` / `get` / `all()` / `byPolarity`；静态块注册 **13 条**事件（16 个事件名对应 16 张图标，其中「调查阶段」一条事件关联 4 张图标）；启动期自检：id 唯一、`nameKey` 三语齐全、`icon` 材质存在 ⇒ 任一失败 `IllegalStateException`（启动即暴露）。
- `event/AstralEventDispatcher.trigger(Player triggerer, ResourceLocation eventId, AstralEventTrigger source)`：
  1. 服务端守卫（客户端直接 return，沿用现口径）；
  2. 作用域 = `PartyRelations.collectEventTargets(triggerer, event.radius)`（沿用，单事件可覆盖半径）；
  3. **阶级闸门**：`secretOnly` 且 `source == INVESTIGATION_KILL` ⇒ 触发**并提升**（`setInvestigationStage(min(now+1,4))`）；其它来源 ⇒ **只按当前阶级触发，绝不提升**；
  4. 计时器：`defaultDurationTicks > 0` 时按玩家独立登记到期刻（覆盖/重置）；本轮全 0 ⇒ 不写任何计时器；
  5. 调用 `event.effect()`（本轮全 NOOP）；
  6. 收尾调用既有附加效果层 `AstralEventSystem.applyEventBonus(triggerer, eventId, targets)`（大侦探 +3 星币 / 调查员活体书页**保留**）。
- 旧入口保留为薄封装：`onEventTriggered(p,id)` → dispatcher（未知 id 记 warn 跳过）；`triggerInvestigationEvent(p)` → `trigger(p, investigation_stage, INVESTIGATION_KILL)`。

### 4.3 阶级与状态指示器

- **阶级**：复用 `ModAttachments.getInvestigationStage(player)`（0..4）作为「调查阶段」当前阶级；`STAGE_I..III / REVELATION` 与四张图标一一对应；**提升唯一闸门 = INVESTIGATION_KILL**；卸下秘密侦探立牌清零（沿用）。
- **状态指示器**：**用户已裁定 —— 不显示**。仅保留可选接口 `event/client/AstralEventStatusIndicator`（本轮无人实现，**不加任何 HUD**）。

### 4.4 事件清单（16 条空壳）

| id | 中文名 | 素材 | 类型（建议） | 秘密专属 | 本轮触发器（占位） | 计时器 |
|---|---|---|---|---|---|---|
| `red_heat_warning` | 红温警告 | 红温警告.png | NEGATIVE | 否 | CUSTOM | 无 |
| `swift_stride` | 疾行 | 疾行.png | POSITIVE | 否 | CUSTOM | 无 |
| `storm_approaching` | 雷暴将至 | 雷暴将至.png | NEGATIVE | 否 | CUSTOM | 无 |
| `learn_from_others` | 取长补短 | 取长补短.png | NEUTRAL | 否 | CUSTOM | 无 |
| `shop_bonanza` | 商店大酬宾 | 商店大酬宾.png | POSITIVE | 否 | CUSTOM | 无 |
| `hand_erasure` | 手牌抹杀 | 手牌抹杀.png | NEGATIVE | 否 | CUSTOM | 无 |
| `world_collapse` | 天崩地裂 | 天崩地裂.png | NEGATIVE | 否 | CUSTOM | 无 |
| `gift_from_sky` | 天降之物 | 天降之物.png | NEUTRAL | 否 | CUSTOM | 无 |
| `angel_descent` | 天使降临 | 天使降临.png | POSITIVE | 否 | CUSTOM | 无 |
| `problem_delivery` | 问题外卖 | 问题外卖.png | NEUTRAL | 否 | CUSTOM | 无 |
| `crowded_passage` | 拥挤通道 | 拥挤通道.png | NEGATIVE | 否 | CUSTOM | 无 |
| `battle_frenzy` | 战斗爽 | 战斗爽.png | POSITIVE | 否 | CUSTOM | 无 |
| `investigation_stage` | 调查阶段 | 调查阶段1/2/3.png + 真相揭露.png（**一条事件关联 4 张图标**，按阶级切换） | **POSITIVE**（2026-10-10 用户裁定：该事件针对敌对目标，故为正面） | **是** | INVESTIGATION_KILL（唯一可提升）+ 后续公共池 | 无 |

### 4.5 删除清单（大侦探立牌）

- `FannySignItem`：删 `roll = nextInt(1,12)` :43、`applyEventTo` 全部 11 个分支（:78 起）、`sendEventActionBar` 的 11 项文案、门控登记 :59-61、`onEventTriggered(player,"fanny_active")` :65。
- 结果：`performSkill` **保留挂载点但方法体留空**（不清物品、不删能力槽、保留冷却框架），加注释「技能待重做（2026-10-10 用户裁决）」。
- 【待确认】被删效果的辅助方法（`applyTimed`/给物品分支）**整段删除**（我建议）还是保留为未使用私有方法。

### 4.6 资产与本地化

- 贴图：`images/card+event/<中文名>.png` → `assets/astral_dice/textures/gui/event/<id>.png`（**两线同批**）；【待确认】游戏内文件命名建议一律用 ascii id（现有 GUI 素材均为英文名）。
- lang 三语：`event.astral_dice.<id>.name`（zh_cn / en_us / ja_jp 同步）；**染色**：POSITIVE=绿 `§a`、NEUTRAL=白（默认）、NEGATIVE=红 `§c`，**仅界面文本染色、不影响结算**。【待确认】

### 4.7 守门与判据

1. 两线 `mt_build` ⇒ `MT_BUILD: OK`（`.60`）。
2. `tools/check_lang_sync.ps1` ⇒ 三语 key 完全一致。
3. 新增 `scripts/verify/verify_event_registry.ps1`（静态）：① 16 条 id 唯一；② 每条 nameKey 三语存在；③ 每条 icon 的 png 在 `assets/astral_dice/textures/gui/event/` 存在；④ 除注册表/探针外无事件 id 字面量散落。
4. 既有 `verify_resource_integrity.ps1` 仍 PASS。
5. 行为不回归：`InvestigationEventUtil` 施加逻辑逐行未改（git diff 证明只换分发入口）；大侦探主动技能**不再产生任何效果**。
6. 独立复核交子代理（协作规则 30）；实机按用户指令。

## 5. 明确不做（本轮边界）

- 不实现 12 个新事件的任何效果；不登记计时器；不新增 HUD/指示器渲染。
- 不重做大侦探主动技能（仅清空 + 留注释）。
- 不改隐匿调查时长（1200t）、阶段效果、作用域、联动钩子。
- 不动 26.1.2 / fabric 两线。

## 6. 待确认（评审时一并拍板）

1. ~~调查阶段~~ ⇒ **已定**：**一条事件**（`investigation_stage`）关联 **4 张图标**（按阶级切换），类型 = **POSITIVE**。
2. ~~状态指示器~~ ⇒ **已定：不显示**。
3. ~~图标命名/染色~~ ⇒ **已定**：游戏内用 ascii id；染色 = **正面绿 §a / 中性黄 §e / 负面红 §c**。

## 7. 后续

用户评审并批准本 spec → 调 `writing-plans` 出实施计划、选定执行方式 → **主 Agent 亲自编码**（P0 两线同批）→ 静态守门 + 子代理独立复核 → 逐事件补实现（你后续逐个下达）。
