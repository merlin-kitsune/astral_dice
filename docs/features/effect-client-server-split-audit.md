# 效果前后端分离审计（客户端只做计数器）

> 版本：`1.5.0-pre.3+*`，分支 `multi-main`，基线 `fc913507`。
> 性质：**只读审计**（首版 2026-10-23，本轮未修改任何代码/资源）。
> 后续：**2026-10-23 用户裁决**（见 §10）—— mob 侧保持原版（R1 不修）、玩家侧全部收口（R2 已修，`PlayerEffectProtection`）。
> 证据 = 工作树源码 `文件:行号` + 官方映射反编译源码 jar（附录 A 有可复算命令）。

## 0. 结论速览

| 维度 | 结论 | 强度 |
|---|---|---|
| D1 客户端零判定 | **成立**。客户端只读效果「存在性/时长」用于 HUD、tooltip、高亮、半透明；客户端包内无任何 add/remove 效果调用，无玩法判定依赖客户端计时 | 强（client/ mixin/ screen/ network 全扫） |
| D2 计时权威在服务端 | **成立但有洞**。服务端 wall-clock 守卫 `EffectTimerGuard` 管住 42 个施加点；另有 7 处「原版效果 + 玩家」裸施、9 处「施给非玩家」完全不进守卫 → **7 处已于 2026-10-23 收口（§10）**，mob 侧按裁决不设防 | 中（审计时 42 / 102 处 `new MobEffectInstance`） |
| D3 抗「减速/冻结效果时长」 | **玩家 + 走守卫的效果成立**（每 tick 把剩余时长拉回 20t/s 预期：加速补齐、减速截断）；**mob 目标不设防（裁决）**，7 处裸施原版效果已收口 | 中 |
| D4 抗外力清除（奶桶 / `/effect clear`） | **成立**。两侧 `removeAllEffects()` / `removeEffect()` 都先派发可取消的 `MobEffectEvent.Remove`，本模组 `astral_dice:` 命名空间一律取消 | 强（反编译源码逐字） |
| D5 客户端计数器一致性 | 显示可能与真值短暂不一致（≤1 tick 的截断、≤40 tick 的指示器容差），但**无玩法判定读客户端数值** | 强 |

**一句话**：客户端确实只是计数器，计时真值与结算都在服务端 —— 但服务端守卫只覆盖「玩家 + 有限时长」，**怪身上的功能窗口效果（标记/破绽/嘲讽/破骰/粉丝）**和**玩家身上的 7 处原版效果裸施**是当前对计时类模组/外挂的裸露面。

**2026-10-23 裁决后**：玩家侧裸露面已闭合（7 处裸施 + 卡牌统一入口 + 外部清除补回，见 §10）；mob 侧维持原版行为（女巫/术士对 mob 反复喝药清状态会造成卡顿，改造成保活计时会叠加 tick 负载，故不搬计时表）。

## 1. 审计口径与范围

- 范围：P0 两线（`neoforge-1.21.1` 全量基准、`forge-1.20.1` 计数对照 + 关键类抽样）；P1（`neoforge-26.1.2` / `fabric-1.20.1`）**只登记不深入**。
- 规模：主模组 `neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/effect/` **38 个** `*Effect.java` + `F:/MCProject/starengine/common/src/main/java/com/merlinkitsune/starengine/effect/` **18 个** = 56 个效果类；`effect/ModEffects.java` 注册 **66 个**效果（`ChipReadyEffect` ×7、`RangedBoostEffect` ×4、`CutterReadyEffect` ×2 等多处注册）。
- 每个效果看五件事：① 类里有没有 tick/属性逻辑；② 时长从哪来（常量 / 附件 / 事件表）；③ 施加点走不走 `EffectTimerGuard`；④ 目标可否非玩家；⑤ 有没有 `MobEffectEvent.Expired` 服务端结算。
- **本次未做**：不改代码、不编译、不实机、不碰整合包、不提交。

## 2. 四层机制（为什么可以说「客户端只是计数器」）

### 2.1 效果类本身零逻辑

56 个效果类**没有一个**重写 `applyEffectTick` / `isDurationEffectTick` / `shouldApplyEffectTickThisTick`。唯一的「类内功能」是**属性修饰器**（原版在 add/remove 时挂载/撤销，服务端权威）：

| 类 | 修饰器 | 证据 |
|---|---|---|
| `RenShieldEffect` | `MAX_ABSORPTION` +10（黄心上限） | `effect/RenShieldEffect.java:39` |
| `UnwaveringEffect` | `ARMOR`（curve 随 amplifier 线性） | `effect/UnwaveringEffect.java:21` |
| `BlueCurseEffect` | `ARMOR` / `ARMOR_TOUGHNESS` | `effect/BlueCurseEffect.java:18,21` |
| `JasmineSweepEffect` | `MOVEMENT_SPEED` + `ARMOR` | `effect/JasmineSweepEffect.java:17,20` |
| `HannaFloatEffect` | `MOVEMENT_SPEED` | `effect/HannaFloatEffect.java:38` |
| `AmeOverdoseEffect` | `MOVEMENT_SPEED` | `effect/AmeOverdoseEffect.java:40` |
| `DragonRoarBreakEffect` | `ARMOR` | `effect/DragonRoarBreakEffect.java:34` |

⇒ 属性类效果的功能**绑定在实例存活期**（效果在、buff 在），这也是「延长/冻结时长」类模组唯一能碰到的功能面（见 R6）。

### 2.2 服务端 wall-clock 守卫：`EffectTimerGuard`

`F:/MCProject/starengine/common/src/main/java/com/merlinkitsune/starengine/event/EffectTimerGuard.java`（213 行）：

- `apply(LivingEntity, MobEffectInstance)` L103-109：施加 + **仅当目标是 `Player` 且有限时长**才 `record`；
- `record` L112-128：`end = timerSource.now(player) + duration`，与旧记录取 **max**（刷新不被缩短的记录覆盖）；`INFINITE_THRESHOLD = Integer.MAX_VALUE/2` 视为无限、不记录；
- `tick(Player)` L131-180（由 `event/PlayerTickEvents.java:34-37` 在 `PlayerTickEvent.Pre` 首行驱动）：`expectedRemaining = end - now`，**加速**（剩余 < 期望）→ L165-168 补回；**减速/冻结**（剩余 > 期望 + `CLAMP_TOLERANCE(20)`）→ L169-173 `forceRemove` 后按期望截断重施；**已过期仍残留** → L150-158 移除 + 按 1 tick 重施，使其当 tick 自然到期并照常触发 `Expired` 结算；
- `forceRemove` L204-211 置 `forcedRemoval` 标志放行本模组拦截；`forget/clear` 负责主动移除/死亡清场时删记录，防止复活。

⇒ 「冻结效果时间」对**走守卫的玩家效果**无效：真值以服务端 wall-clock 为准，每 tick 校正一次，且只在服务端跑（`isClientSide()` 早退）。

### 2.3 服务端结算：7 个 `MobEffectEvent.Expired` 处理器

| 效果 | 结算点 |
|---|---|
| `dice_blessing` | `combat/DiceCombatEvents.java:1326` `onDiceBlessingExpired`（另 `event/NewSignEvents.java:111`） |
| `weakness_reveal` / `sherry_reasoning` | `combat/DiceCombatEvents.java:1422` `onPassiveStackExpired`（到期减 1 层，下一 tick 重施） |
| `marked` | `item/MarkManager.java:70` `onMarkExpired`（下一 tick 补写 amplifier-1） |
| `ame_overdose` | `item/sign/AmeSignItem.java:283` `onOverdoseExpired` |
| `weak_mark` | `item/sign/HaiqingSignItem.java:139` `onWeakMarkExpired` |
| `pandaman_taunt` | `item/sign/PandamanSignItem.java:132` `onPandamanTauntExpired` |

⇒ 「效果到期」这一**玩法事件**由服务端事件驱动，客户端不参与；反过来，**效果永远不到期 ⇒ 结算永远不来** —— 这正是需要守卫的原因，也是 mob 目标的裸露点。

### 2.4 计时搬进附件/事件表（更彻底，已在用）

| 机制 | 真值位置 | 效果实例的角色 | 证据 |
|---|---|---|---|
| 赋能衰减 | 附件 `empowerDecayAt` + `gameTime`，每 20 tick 检查 | 只做面板倒计时（duration=620） | `item/EmpowerManager.java:59-84`（L76-78 明确不可每秒刷新时长） |
| 治愈点数 | 独立计时（附件）→ 同步成 amplifier/duration | 显示 + 层数 | `item/HealingManager.java:369-403` |
| 事件类效果 | `EventTimerStore.EXPIRY`（UUID→id→endTick，惰性到期回调） | 只是指示器 | `event/EventTimerStore.java` |
| 活体书页周期加成 | 附件 `living_page_cycle_bonus` | 注册了 `living_page` 效果但**代码里无任何读取/施加** | `effect/ModEffects.java:66-67` |
| 充能层数 | 效果 amplifier（∞，无衰减） | 真值本身 | `effect/ChargeEffect.java:45-49` |
| 护盾/反击层数 | 效果 amplifier + 阈值刷新 | 真值本身 | `item/RenShieldManager.java:302-338` |

### 2.5 客户端只读面（D1 详表）

| 位置 | 读什么 | 用途 | 影响玩法？ |
|---|---|---|---|
| `client/EffectTooltipDescriptions.java:48,50,64` | 效果注册表 | tooltip 文案 | 否 |
| `client/RenShieldRenderer.java:29-37` | **不再读** `hasEffect`（注释明确他人客户端恒 false），改读 `RenShieldStatePayload` | 护盾边框 | 否 |
| `client/TargetSelectionClient.java:199` | `entity.hasEffect(UNDERCOVER_INVESTIGATION)` | HUD 标签 | 否（L195-210 纯显示） |
| `mixin/client/EffectRenderingInventoryScreenMixin.java:26-31` | amplifier | 物品栏效果数值徽标 | 否 |
| `mixin/client/GuiMixin.java:19,40,55-57` | 存在性/时长 | 抑制到期闪烁 | 否 |
| `mixin/client/SignTranslucencyMixin.java:24-25` | `ALANA_IRON_MAIDEN` / `DEEPSEEK_FISHING` | 立牌半透明 | 否 |
| `screen/CardInventoryScreen.java:104,115` | `DICE_BLESSING` 存在性 | 卡槽高亮 | 否 |
| `screen/CardInventoryMenu.java:435-449` | `hasEffect(DICE_BLESSING)` | 有赐福时拦 clicked/quickMoveStack（注释：服务端权威；客户端同逻辑避免操作闪烁） | **仅存在性**、不读时长；真实扣除在服务端 |

客户端包内**没有任何** `addEffect` / `removeEffect` 调用。

## 3. D2 66 个效果的计时口径矩阵

判定代号：**A** 纯指示器（时长只给 HUD）·**B** 无限资源/形态（duration=-1，功能由 amplifier/附件驱动）·**C** 有服务端 `Expired` 结算 ·**D** 时长即窗口（玩家，已进守卫或有自我纠偏）·**E** 时长即窗口（可施于非玩家 ⇒ **不进守卫**）·**F** 属性类（见 2.1）。

| # | id | 类 | 时长口径 | 结算/读取点 | 施加路径 | 判定 |
|---|---|---|---|---|---|---|
| 1 | king_power | KingPowerEffect | 卡牌给 newDuration | `item/card/EffectCardItem.java:36` | 裸 | D |
| 2 | berserk | BerserkEffect | max(现有, 3600) | `item/card/BerserkCardItem.java:54-55` | 裸 | D |
| 3 | unwavering | UnwaveringEffect | 卡牌给 newDuration | `item/card/UnwaveringCardItem.java:39-42` | 裸（属性走守卫） | F |
| 4 | marked | MarkedEffect | 1200，amplifier=层数 | `item/MarkManager.java:47,70` | 裸，**目标=怪** | **E** |
| 5 | jill_mixed | JillMixedEffect | 守卫记录 | `item/sign/JillSignItem.java:145-155` | 守卫 | D |
| 6 | dorothy_warmth | DorothyWarmthEffect | ∞ | `event/NewSignEvents.java:95` | 裸 | B |
| 7 | alana_iron_maiden | AlanaIronMaidenEffect | 守卫记录 | `mixin/client/SignTranslucencyMixin.java:24` | 守卫（`item/sign/AlanaSignItem.java:72`） | D |
| 8 | deepseek_fishing | DeepseekFishingEffect | 守卫记录 | `mixin/client/SignTranslucencyMixin.java:25` | 守卫（`item/sign/DeepseekSignItem.java:64`） | D |
| 9 | dice_blessing | DiceBlessingEffect | `DICE_BLESSING_DURATION_TICKS` | `combat/DiceCombatEvents.java:1326`（Expired） | **裸**（`:392`,`:408`） | **C**（玩家裸施 ⇒ 不进守卫） |
| 10 | misaki_burst | MisakiBurstEffect | 守卫记录 | `item/sign/MisakiSignItem.java:28` | 守卫 | D |
| 11 | living_page | LivingPageEffect | — | 无读取（真值在 `living_page_cycle_bonus` 附件） | 代码内未见施加 | A/B |
| 12 | jasmine_sweep | JasmineSweepEffect | 守卫记录 | `item/sign/JasmineSignItem.java:67-72` | 守卫 | F |
| 13 | weak_mark | WeakMarkEffect | 守卫记录 | `item/sign/HaiqingSignItem.java:139`（Expired） | 守卫（**目标可为怪**） | **C/E** |
| 14 | fate_guidance | FateGuidanceEffect | 6000 | `item/card/FateGuidanceCardItem.java:57` | 裸 | D |
| 15 | papara_bite | PaparaBiteEffect | 守卫记录 | `item/sign/PaparaSignItem.java:36` | 守卫 | D |
| 16 | undercover_investigation | UndercoverInvestigationEffect | ∞（永久直到目标消失） | `client/TargetSelectionClient.java:199` | 裸（`item/sign/BonnieSignItem.java:78`，**目标=怪/被选者**） | E（无时长风险） |
| 17 | investigation_bonus | InvestigationBonusEffect | 调用方给 | `item/InvestigationEventUtil.java:90` | 裸 | D |
| 18 | concealment | ConcealmentEffect | 调用方给 durationTicks | `effect/ConcealmentEffect.java:43` | 守卫 | D |
| 19 | healing | HealingEffect | = 剩余治愈点数 | `item/HealingManager.java:394`（+`record` L396-402） | 裸（手动补 record） | D（附件驱动） |
| 20 | cutter_ready | CutterReadyEffect | ∞ | HUD | `event/PlayerTickEvents.java:213-223` | A |
| 21 | cutter_blade_ready | CutterReadyEffect | ∞ | HUD | 同上 | A |
| 22 | flashlight_ready | FlashlightReadyEffect | ∞ | HUD | 同上 | A |
| 23 | whetstone_ready | ChipReadyEffect | ∞ | HUD | `event/PlayerTickEvents.java:186-199` | A |
| 24 | adrenaline_ready | ChipReadyEffect | ∞ | HUD | 同上 | A |
| 25 | adrenaline_high_ready | ChipReadyEffect | ∞ | HUD | 同上 | A |
| 26 | cursed_sword_ready | ChipReadyEffect | ∞ | HUD | 同上 | A |
| 27 | electric_sword_ready | ChipReadyEffect | ∞ | HUD | 同上 | A |
| 28 | whetstone_guard_cd | ChipReadyEffect | 剩余冷却（倒计时） | HUD | `event/PlayerTickEvents.java:239-268` | A |
| 29 | airbag_guard_cd | ChipReadyEffect | 剩余冷却（倒计时） | HUD | 同上 | A |
| 30 | monster_laser | RangedBoostEffect | ∞/倒计时 | HUD | 同上 | A |
| 31 | monster_brick | RangedBoostEffect | ∞/倒计时 | HUD | 同上 | A |
| 32 | orbital_strike | RangedBoostEffect | ∞/倒计时 | HUD | 同上 | A |
| 33 | directional_blast | RangedBoostEffect | ∞/倒计时 | HUD | 同上 | A |
| 34 | magic_tome_count | CounterEffect | ∞，amplifier=计数 | HUD | `item/chip/MagicTomeChipItem.java:56` | A |
| 35 | fen_frenzy | FenFrenzyEffect | 守卫记录 | `item/sign/FenSignItem.java:91,102` | 守卫 | D |
| 36 | blue_curse | BlueCurseEffect | ∞ | `item/chip/CursedSwordChipItem.java:136` | 裸 | B/F |
| 37 | nancy_lu_hack | NancyLuHackEffect | 守卫记录 | `item/sign/NancyLuSignItem.java:126,177` | 守卫 | D |
| 38 | revenge_halberd | RevengeHalberdEffect | ∞ | `item/chip/RevengeHalberdChipItem.java:81` | 裸 | B |
| 39 | charge | ChargeEffect | ∞，amplifier=层数 | `effect/ChargeEffect.java:45-49` | 裸（自身类） | B |
| 40 | empower | EmpowerEffect | 620（显示）；衰减在附件 | `item/EmpowerManager.java:59-84` | 裸（自身类） | **B（正向样例）** |
| 41 | weakness_reveal | WeaknessRevealEffect | 1200，amplifier=层数 | `combat/DiceCombatEvents.java:1422`（Expired） | **裸**（`effect/WeaknessRevealEffect.java:32,58`） | **C**（玩家裸施） |
| 42 | moses_broken | MosesBrokenEffect | 2:00 | `item/sign/MosesSignItem.java:153` | 裸，**目标=怪** | **E** |
| 43 | pandaman_taunt | PandamanTauntEffect | 守卫记录 | `item/sign/PandamanSignItem.java:132`（Expired） | 守卫（目标多为怪） | **C/E** |
| 44 | ren_shield | RenShieldEffect | `SHIELD_EFFECT_DURATION_TICKS` + 阈值续 | `item/RenShieldManager.java:313` | 裸（属性生效） | F |
| 45 | ren_counter | RenCounterEffect | 同上 | `item/RenShieldManager.java:319` | 裸 | D/F |
| 46 | zhao_blessing | ZhaoBlessingEffect | 2400 / 占位 -1 | `effect/ZhaoBlessingEffect.java:93-98`（tick 自愈） | 裸（先 remove 再 add，L80-82） | D |
| 47 | misfortune | MisfortuneEffect | ∞ | `effect/MisfortuneEffect.java:55` | 裸 | B |
| 48 | teru_descent | TeruDescentEffect | 2400 / 占位 -1 | `effect/TeruDescentEffect.java:78-94` | 裸 | D |
| 49 | teru_huguang | HuguangEffect | ∞ | `effect/HuguangEffect.java:59` | 裸 | B |
| 50 | nardis_privilege | NardisPrivilegeEffect | 3600 | `item/sign/NardisSignItem.java:217,224` | 裸 | D |
| 51 | mamushi_dragon | MamushiDragonEffect | ∞ | `effect/MamushiDragonEffect.java:45,46,66` | 裸 | B |
| 52 | dragon_roar_break | DragonRoarBreakEffect | 1200 | 守卫 | 守卫（`item/sign/MamushiSignItem.java:322-324` 等） | F |
| 53 | sherry_reasoning | SherryReasoningEffect | 1200 | `combat/DiceCombatEvents.java:1422`（Expired） | **裸**（`effect/SherryReasoningEffect.java:55`） | **C**（玩家裸施） |
| 54 | hanna_doll_craft | HannaDollCraftEffect | ∞ | `effect/HannaDollCraftEffect.java:49` | 裸 | B |
| 55 | hanna_doll_complete | HannaDollCompleteEffect | ∞ | `item/sign/HannaSignItem.java:153-154` | 裸 | B |
| 56 | hanna_float | HannaFloatEffect | 1200 | 守卫 | 守卫（`item/sign/HannaSignItem.java:201,262-281`） | F |
| 57 | precision_strike | PrecisionStrikeEffect | ∞ | `effect/PrecisionStrikeEffect.java:64` | 裸 | B |
| 58 | rin_page_range | RinPageRangeEffect | 2400 | `item/sign/RinSignItem.java:54` | 守卫 | D |
| 59 | red_heat_warning | RedHeatWarningEffect | 3600 | `event/EventEffects.java:89-96` + `EventTimerStore` | 裸（事件表即真值） | C |
| 60 | storm_approaching | StormApproachingEffect | 2400 | `event/EventEffects.java:108-120` | 裸（+事件表） | C |
| 61 | problem_delivery | ProblemDeliveryEffect | 300 | `event/EventEffects.java:299-311` | 裸（+事件表） | C |
| 62 | kangel_fan | KangelFanEffect | ∞ | `effect/KangelFanEffect.java:47` | 裸，**目标=怪** | E（无时长风险） |
| 63 | kangel_fan_indicator | KangelFanIndicatorEffect | ∞ | `effect/KangelFanIndicatorEffect.java:58` | 裸 | B |
| 64 | ame_love | AmeLoveEffect | ∞ | `effect/AmeLoveEffect.java:52` | 裸 | B |
| 65 | ame_overdose | AmeOverdoseEffect | 1200 | `item/sign/AmeSignItem.java:283`（Expired） | 守卫 | C/F |
| 66 | lingling_surge | LinglingSurgeEffect | 1200 | 守卫 | 守卫（`item/sign/LinglingSignItem.java:43`） | F |

> 判定是**结构性**（时长从哪来、谁记录、谁结算），不等于逐条核实玩法语义；标 **C**/**E** 的是「时长被篡改会产生玩法后果」的重点行。

## 4. D3 守卫覆盖率（数字与缺口）

`neoforge-1.21.1/src/main/java` 全量 401 个 java 的计数（`forge-1.20.1` 同口径对照）：

| 计数项 | neo 1.21.1 | forge 1.20.1 |
|---|---|---|
| `new MobEffectInstance(` | 102 → **103** | 103 → **104** |
| `EffectTimerGuard.apply(` | 42 → **1**（仅剩 `PlayerEffectProtection.apply` 内部） | 42 → **1** |
| `PlayerEffectProtection.apply(` | — → **52**（51 处调用点 + 类内 restore 1 处；定义行不含该串） | — → **52** |
| `EffectTimerGuard.tick(` / `record(` | 2 / 6 | 2 / 6 |
| `hasEffect(` | 135 | 128 |
| `getDuration()` | 21 | 21 |

> 箭头右侧 = **2026-10-23 收口后重测**（`+1` 来自 `PlayerEffectProtection.restore` 的 `new MobEffectInstance`）。复算命令见附录 A 第 2 条。

42 个守卫施加点分布：立牌主动（`item/sign/{Alana,Ame,Deepseek,Fen,Haiqing,Hanna,Jasmine,Jill,Lingling,Lulu,Mamushi,Misaki,NancyLu,Nardis,Pandaman,Papara,Rin}SignItem`）、`item/RenShieldManager.java:336,365,373`、`item/MarkManager.java:48,81`（GLOWING）、`item/card/{ExpressDelivery,FightPoisonWithPoison,Unwavering}CardItem`、`item/chip/WarpEngineChipItem.java:73`、`combat/DiceCombatEvents.java:943`（DARKNESS）。

### 4.1 缺口一：7 处「原版效果 + 玩家」裸施（**2026-10-23 已全部收口**）

下表是审计时的原始清单；收口后这 7 处一律改为 `PlayerEffectProtection.apply(player, new MobEffectInstance(...))`（判据：`PlayerEffectProtection.apply(` 计数 52、`EffectTimerGuard.apply(` 仅剩 1）。

| 位置 | 效果 | 场景 |
|---|---|---|
| `event/EnderDiceHandler.java:196` | `REGENERATION` | 末影骰子触发（方法 `onLivingDeath`，L160） |
| `event/EnderDiceHandler.java:198` | `ABSORPTION` | 同上 |
| `event/EnderDiceHandler.java:200` | `FIRE_RESISTANCE` | 同上 |
| `item/chip/AbyssReagentChipItem.java:136` | `ABSORPTION` | 深渊试剂筹码 |
| `event/EventEffects.java:102` | `MOVEMENT_SPEED` | 事件「迅捷步伐」（功能计时在 `EventTimerStore`，效果只是指示器） |
| `event/EventEffects.java:306` | `POISON` | 事件「问题投递」 |
| `event/EventEffects.java:307` | `HUNGER` | 同上 |

### 4.2 缺口二：9 处施给非玩家目标（守卫 `record` 只认 `Player`）

| 位置 | 效果 | 影响 |
|---|---|---|
| `item/MarkManager.java:47` / `:99` | `MARKED`（amplifier=层数） | 层数衰减靠到期（`onMarkExpired` 下一 tick 补写）⇒ 冻结时长 = 标记永不衰减 |
| `item/sign/MosesSignItem.java:153` | `MOSES_BROKEN` | 「破骰」窗口 2:00 |
| `item/sign/LuluSignItem.java:115` | `MOVEMENT_SLOWDOWN` | 减速窗口 |
| `entity/RepulsionEntity.java:660` / `:662` | `MOVEMENT_SLOWDOWN` / `WEAKNESS` | 拒止实体自身 debuff |
| `item/sign/HaiqingSignItem.java:68-69` | `WEAK_MARK` / `WEAKNESS` | 目标是怪时不记录 |
| `item/sign/PandamanSignItem.java:111` | `PANDAMAN_TAUNT` | 同上 |
| `item/sign/MamushiSignItem.java:322-324` | 走守卫但 victim 是怪 | 实际不记录 |
| `effect/KangelFanEffect.java:47` | `KANGEL_FAN`（∞） | 无时长风险 |
| `effect/PrecisionStrikeEffect.java:64` | `PRECISION_STRIKE`（∞） | 无时长风险 |

⇒ 玩家侧「本模组自定义效果」的计时基本都有权威或自我纠偏（42 个守卫点 + 3 套附件/事件表机制）；裸露面集中在 **mob 目标** 与 **原版效果裸施**。

> ⚠️ **2026-10-23 裁决**：mob 侧**保持原版状态效果**（不搬计时表、不设防）。理由：女巫 / 诡厄巫法的术士等 AI 会持续尝试用奶桶/药水清掉 mob 身上的本模组效果，改造成「保活计时」会让它们反复重施，反而叠加 tick 负载。因此本节的 mob 缺口是**设计取舍**，不是待修项。

## 5. D4 抗篡改 / 抗清除（含两侧平台差异）

### 5.1 机制

`neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/event/ModEffectEvents.java`（审计时 80 行，收口后 ≈ 130 行）：

- `MobEffectEvent.Remove`（HIGH）：内部移除（`ModEffectRemoval`）/ 守卫强制（`isForcedRemoval()`）/ 玩家死亡（`isDeadOrDying`，同时 `PlayerEffectProtection.clear(player)`）放行；**非 Player 目标一律放行**（mob 裁决）；`DECLARED_CLEARABLE`（当前 = `astral_dice:marked`）放行；`GLOWING` 在目标仍有 `MARKED` 时取消移除；其余 `astral_dice:` 命名空间 `setCanceled(true)`。
- `MobEffectEvent.Added`：仅「Player + `astral_dice:`」→ `EffectTimerGuard.record`。
- `MobEffectEvent.Remove`（未被取消、非守卫、非死亡）→ `forget`，防止复活。
- **`MobEffectEvent.Remove`（LOWEST，2026-10-23 新增）**：未被 HIGH 拦下（即**原版命名空间**）的玩家侧移除 → `PlayerEffectProtection.onExternalRemoval(...)` 记入待补回表，**下一 tick**（`PlayerEvent.PlayerTickEvent.Pre` 里的 `PlayerEffectProtection.tick`）按 `endTick` 余量重施。延后而非就地重施，是为避免 `removeAllEffects()` 遍历期改表引发 CME。
- `PlayerEvent.PlayerLoggedOutEvent`（2026-10-23 新增）：`PlayerEffectProtection.clear(player)`，防按 UUID 累积。

### 5.2 平台取证（反编译源码逐字）

- NeoForge 1.21.1 `LivingEntity.removeAllEffects()`：`if (EventHooks.onEffectRemoved(this, effect, null)) continue;` ⇒ **取消即不删**；`removeEffect(Holder)`：`if (EventHooks.onEffectRemoved(this, effect, null)) return false;`。
- Forge 1.20.1 同方法：`if (MinecraftForge.EVENT_BUS.post(new MobEffectEvent.Remove(this, effect))) continue;` ⇒ 同样成立。
- ⇒ **奶桶、`/effect clear`、走 `removeAllEffects`/`removeEffect` 的第三方清理都吃拦截**，这是 debuff 体系能站住的根基。
- `MobEffectEvent.Expired`：NeoForge 1.21.1 **实现 `ICancellableEvent`**（可取消，取消后效果留在身上、`iterator.remove()` 被跳过）；Forge 1.20.1 明确 **不 Cancelable**（`MarkManager.java` L67 注释同此口径）。
- `addEffect` 在派发 `Added` 后，若同效果已存在且时长更长会走 `MobEffectInstance#update` 接受更长时长 ⇒ 守卫 `record` 取 max 与之自洽。

### 5.3 残留风险

- **R3**：若第三方在 1.21.1 上 cancel `Expired`，效果会被冻在「时长为 0」且**永不结算**；守卫此时已把该记录删掉（`tick()` L150-158 处理完即 `it.remove()`），不会继续纠正。当前无 `Expired` 拦截/兜底。
- **R4**：`event/EventTimerStore.java` 的 `EXPIRY` 以玩家 UUID 为键，**没有接线登出/换维清理**，重登后可能残留到期回调（回调多为 `setXxx(false)` 之类，最坏是失败一次读取）。
- 反方向：`record` 取 max ⇒ 外部用 `addEffect` 给更长时长会被**当作刷新接受**（与自身刷新不可区分）；这是设计取舍，不是缺陷。

## 6. D5 客户端计数器一致性

- 客户端实例的 `getDuration()` 由原版本地递减，与服务端只在同步包时点对齐（`ClientboundUpdateMobEffectPacket`）；
- 守卫每次纠正都走 `addEffect` 或 `forceRemove`，两者都产生同步包 ⇒ HUD 倒计时通常在 **≤1 tick** 内回到真值；
- 指示器类（∞ 与倒计时）由 `event/PlayerTickEvents.java:239-268` 的 `refreshIndicatorRemaining` 维护，偏差 > `REMAINING_TOLERANCE(40)` 才重施 ⇒ 允许 ≤40 tick 的显示漂移（纯显示）；
- 唯一「双端都跑」的判断是 `screen/CardInventoryMenu.java:435-449`，**只读存在性**、不含时长，真实扣除在服务端。

⇒ 客户端计数器可被任意模组干扰，但**不参与任何玩法判定**。

## 7. 风险分级总表

| 级别 | 编号 | 风险 | 证据锚点 |
|---|---|---|---|
| **P0** | R1 | mob 目标的功能窗口效果（标记/破绽/嘲讽/破骰/减速/拒止）对「冻结/减速效果时长」零防护，`Expired` 结算可能永不触发 **—— 2026-10-23 裁决「保持原版、不修」（见 4.2 的取舍说明）** | 4.2；`item/MarkManager.java:47,70`、`item/sign/MosesSignItem.java:153` |
| **P0** | R2 | 7 处「原版效果 + 玩家」裸施不入守卫（末影骰子保命三件套、深渊吸收、事件迅捷/中毒/饥饿） **—— 2026-10-23 已修（§10，走 `PlayerEffectProtection.apply`）** | 4.1 |
| P1 | R3 | 1.21.1 上 `Expired` 可被第三方取消 ⇒ 效果卡在 0 时长且不再结算 | 5.2 / `EffectTimerGuard.java:150-158` |
| P1 | R4 | `EventTimerStore` 无登出清理（UUID 键残留） | `event/EventTimerStore.java` |
| P2 | R5 | 附件/事件表驱动的指示器（赋能、事件类）在「时长被冻结」时显示与实际衰减不同步（真值在附件，玩法正确） | `item/EmpowerManager.java:59-84` |
| P2 | R6 | 属性类效果（黄心上限/护甲/移速）功能随实例存活：玩家侧守卫会截断，mob 侧无守卫 | 2.1 |
| P2 | R7 | `HealingManager` 注释称「刷新时长只发 Changed、不触发 Added」，而两侧源码 `addEffect` 都无条件派发 `Added`；该注释与事件口径需复核（当前行为不受影响，因 `record` 取 max） | `item/HealingManager.java:396-402` |
| P2 | R8 | 1.20.1 Forge 无 `MAX_ABSORPTION` 属性，`RenShieldEffect` 的黄心机制只在 1.21.1 生效（既有平台差异，此处仅登记） | `effect/RenShieldEffect.java:39` |
| 登记 | R9 | P1（`neoforge-26.1.2` / `fabric-1.20.1`）未审计 | — |

## 8. 建议（本次不改代码，供排期）

1. **（R1）⛔ 不做**（2026-10-23 裁决）：mob 侧保持原版状态效果。原建议（搬计时表 / 给 `EffectTimerGuard` 加非玩家分支）会让 mob 被 AI 反复清状态时不断重施，叠加 tick 负载 —— 这正是要避免的卡顿源。若将来要重提，须先给出「mob 侧重施频率上限」的实机数据。
2. **（R2）✅ 已实施**：7 处原版效果裸施 + `BaseEffectCardItem` 默认入口统一改走 `PlayerEffectProtection.apply(...)`（= `EffectTimerGuard.apply` + 玩家快照登记），并新增 LOWEST 补回通道挡住「原版命名空间被外部清除」。详见 §10。
3. **（R3）** `ModEffectEvents` 增加 `Expired` 兜底：`astral_dice:` 效果到期但被取消时，走 `ModEffectRemoval` 清掉并按 `endTick` 结算一次（或至少落一条机器行便于判据）。
4. **（R4）** `EventTimerStore` 接 `PlayerEvent.PlayerLoggedOutEvent` / 换维清理，并考虑键的生命周期。
5. **（R7）** 复核 `HealingManager` 注释与事件口径，避免后人按注释改坏 `record`。
6. **（新纪律）** 任何「本模组主动移除玩家身上效果」的代码**必须**走 `PlayerEffectProtection.removeNow(...)`，不得直接 `removeEffect(...)` —— 否则会被 LOWEST 补回通道当成外部清除而复活（2026-10-23 已实际踩到 7 处：6 处 `removeEffect` + 1 处裸 `ModEffectRemoval.remove`，见 §10.4）。

## 9. 未验证项

- **未实机**（用户明确只做分析）：所有「抗冻结成立」的结论来自代码路径 + 平台事件语义，未用「冻结类模组」实测；要实测需用户指认具体第三方模组。
- 66 个效果的「玩法语义影响」未逐条核实，仅按结构（时长来源 / 结算点 / 目标类型）判定。
- P1 两线未审计；`forge-1.20.1` 只做计数对照与关键类抽样（`ModEffectEvents`、`ChargeEffect`、`MarkManager` 等）。
- 未统计客户端发包频率与 HUD 实际刷新观感。

## 10. 2026-10-23 实施记录（按用户裁决收口玩家侧）

### 10.1 裁决原文要点（m04335）

- ① 对 mob 类**保持原版状态效果**（女巫 / 诡厄巫法的术士会一直尝试用奶桶/药水解除状态，搬计时表会加剧卡顿）⇒ R1 不修。
- ② **玩家侧所有裸施问题一律修补**，玩家侧效果保持前后端分离：**除非该效果被声明可以被清除（牛奶、蜂蜜瓶等），否则不能受到任何外部影响**。

### 10.2 新增 `event/PlayerEffectProtection.java`（两线各一份）

| 成员 | 作用 |
|---|---|
| `DECLARED_CLEARABLE = Set.of("astral_dice:marked")` | 「声明可清除」白名单（默认一律保护）。保留 2026-09-30 裁决：标记可被牛奶清掉 |
| `apply(LivingEntity, MobEffectInstance)` | 唯一玩家侧施加入口 = `EffectTimerGuard.apply` + 玩家快照登记（跳过 ∞ / 近 ∞ / 可清除） |
| `onExternalRemoval(Player, String)` | LOWEST 通道入口：可清除 ⇒ `forget`；有记录 ⇒ 进 `PENDING_RESTORE`（**延后**补回，避免遍历期 CME） |
| `tick(Player)` | 每 tick Pre 补回（余量 = `endTick - gameTime`，走 `apply` 重新登记），并清理 `endTick + 40 < now` 的陈旧记录 |
| `removeNow(LivingEntity, effect)` | **本模组主动移除**的唯一合法入口：先 `forget` 再 `ModEffectRemoval.remove`（非玩家直接 `removeEffect`） |
| `clear(Player)` | 死亡 / 登出清场 |

`Observed` 记录 = `(effect, endTick, amplifier, ambient, visible, showIcon)`；`endTick` 取 `max(旧记录, now + duration)`，与 `EffectTimerGuard.record` 的自洽口径一致。

### 10.3 接线（两线同构）

- `event/ModEffectEvents.java`：HIGH 分支改「死亡 ⇒ clear + 声明可清除放行」；新增 **LOWEST** 补回处理器；新增 `PlayerEvent.PlayerLoggedOutEvent` 清理；新增 `PlayerEvent.Clone` 清理（2026-10-23 复核 P3：原版死亡**不派发** `MobEffectEvent.Remove`，只清本模组 11 个效果，故「只带经 apply 施加的原版效果」死亡时快照会残留）。
- `event/PlayerTickEvents.java`：`PlayerEffectProtection.tick(player)` 插在 `EffectTimerGuard.tick(player)` **之前**（先补回、后校正）。
- 机械替换：两线各 **26 个文件** `EffectTimerGuard.apply(` → `PlayerEffectProtection.apply(`（25 个非 `event` 包文件补 import，18 个文件删掉已无引用的 `EffectTimerGuard` import）。
- 7 处原版裸施（§4.1 清单）+ `item/card/BaseEffectCardItem.java` 默认 `applyEffect` 一并改走统一入口。

### 10.4 踩到并修掉的两个真 bug

1. **自递归**：替换脚本用 `endsWith("event/PlayerEffectProtection.java")` 判断「跳过自身」，但 Windows 路径分隔符是 `\` ⇒ 新类被自己改写，`apply()` 变成自调用（**能编译、运行即栈溢出**）。已改回 `EffectTimerGuard.apply(...)`。
2. **补回通道会「复活」本模组主动移除的效果**：`MarkManager`（GLOWING 层数归零）、`RenShieldManager`（黄心耗尽）、`NancyLuSignItem`（隐身到期，3 处）、`FightPoisonWithPoisonCardItem` 共 6 处直接 `removeEffect` 被 LOWEST 当成外部清除 ⇒ 已全部改 `PlayerEffectProtection.removeNow(...)`。第 7 处由 2026-10-23 复核发现：`EnderDiceHandler` 保命清效果走的是裸 `ModEffectRemoval.remove`（内部标志放行 ⇒ 效果真被移除，但**快照没丢**）——陈旧 `endTick` 会让随后第三方短时中毒 + 牛奶后的补回按**旧剩余时长**复活，已一并改走 `removeNow`。

### 10.5 判据（可复算）

```powershell
# 1) 两线编译（Windows 原生，JAVA_HOME = Zulu 21）
gradlew.bat :neoforge-1.21.1:classes :forge-1.20.1:classes --console=plain   # BUILD SUCCESSFUL
# 2) 玩家侧施加已全部收口
#    PlayerEffectProtection.apply( = 52 / EffectTimerGuard.apply( 仅 1（PlayerEffectProtection 内部）
# 3) 主动移除已全部走 removeNow（7 处调用 + 1 处定义 = 8）
```

### 10.6 残留（如实登记，未修）

- mob 侧窗口效果（R1）：按裁决不设防。
- 仍走裸 `addEffect` 的**自定义** `astral_dice:` 效果（如 `event/EventEffects.java` 的 `RED_HEAT_WARNING` / `STORM_APPROACHING`、`combat/DiceCombatEvents.java` 的 `DICE_BLESSING`）：**移除已被 HIGH 取消 ⇒ 抗清除成立**，但它们没有 wall-clock 计时权威（真值在事件表 / 附件里，效果只是指示器），因此不构成计时裸露面；如需绝对一致可后续改走 `apply`。
- 1.21.1 `Expired` 可被第三方取消（R3）仍未兜底。

## 附录 A 取证命令（可复算）

```bash
# 1) 效果类里有没有 tick 逻辑（应 0 命中）
grep -rn 'applyEffectTick\|isDurationEffectTick\|shouldApplyEffectTickThisTick' \
  F:/MCProject/astral_dice_multiloader/neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/effect \
  F:/MCProject/starengine/common/src/main/java/com/merlinkitsune/starengine/effect

# 2) 守卫覆盖率
grep -rc 'new MobEffectInstance(' F:/MCProject/astral_dice_multiloader/neoforge-1.21.1/src/main/java | awk -F: '{s+=$2} END{print s}'
grep -rc 'EffectTimerGuard.apply(' F:/MCProject/astral_dice_multiloader/neoforge-1.21.1/src/main/java | awk -F: '{s+=$2} END{print s}'

# 3) Expired 处理器清单
grep -rn 'MobEffectEvent.Expired' F:/MCProject/astral_dice_multiloader/neoforge-1.21.1/src/main/java --include=*.java

# 4) 客户端读取面
grep -rn 'hasEffect(\|getDuration()' F:/MCProject/astral_dice_multiloader/neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/client \
  F:/MCProject/astral_dice_multiloader/neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/mixin/client

# 5) 平台事件语义（反编译源码 jar，python zipfile 读）
#    neoforge-1.21.1/build/moddev/artifacts/neoforge-21.1.235-sources.jar
#      net/minecraft/world/entity/LivingEntity.java → removeAllEffects / removeEffect / addEffect / tickEffects
#      net/neoforged/neoforge/event/entity/living/MobEffectEvent.java → Expired implements ICancellableEvent
#    forge-1.20.1/build/moddev/artifacts/forge-1.20.1-47.4.10-sources.jar
#      net/minecraft/world/entity/LivingEntity.java → 同上（MinecraftForge.EVENT_BUS.post(Remove)）
#      net/minecraftforge/event/entity/living/MobEffectEvent.java → Remove 带 @Cancelable，Expired 不带
```

## 附录 B 后续实机判据提示

| 想验的东西 | 机器判据 |
|---|---|
| 守卫是否在跑 | 目前无机器行（`forceRemove` 不落日志）⇒ 若要实机验 R1/R2，建议先补一条守卫日志 |
| 事件类计时 | `AP_EVENT_TIMER: start:<id>:ticks=<n>:end=<tick>` / `expired:<id>`（`event/EventTimerStore.java`） |
| 标记层数 | 层数 = `MARKED.amplifier + 1`（`item/MarkManager.java`）；`/astralparty` dump 可读 |
| 客户端显示 | 只影响 HUD 数字，不被任何判定读取（D5） |

