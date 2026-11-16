# B1-CHIPS-WIRING 接线报告（fabric-1.20.1）

- 目标线：`fabric-1.20.1`（MC 1.20.1 + FAPI 0.92.12，Mojang 映射，Java 17），分支 `multi-main`。
- 蓝本：`forge-1.20.1` 同路径文件（行号逐条对照，见各节）。
- 范围：仅 a)~f) 六条接线 + 条件类取值来源；**未运行 gradle、未启动游戏**（用户明令禁止），以下结论均为源码级核对。
- 行尾：7 个被改文件现全部 UTF-8 无 BOM + CRLF（`DiceCombatEvents.java` 原为纯 LF，本批已统一为 CRLF，git 视图零行尾噪声，`git diff --numstat` 仍为 22/0）。

## 0. 路径与签名核对（先做，结论影响后续全部接线）

| 指令写的路径 | 实际路径 | 结论 |
|---|---|---|
| `combat/HealingManager.java` | `item/HealingManager.java` | **路径漂移**，改的是 item/ 下这份 |
| `combat/ChargeManager.java` | `item/ChargeManager.java` | **路径漂移**，改的是 item/ 下这份 |

被调方法签名 grep 实证 **全部与调用点一致，无实参改写**：

- `AbyssReagentChipItem.isEquipped(Player)`:90、`BOON_POINTS=2`:71、`onHealingTriggered(Player,float)`:128、`@SubscribeEvent onPlayerDeath(LivingDeathEvent)`:115
- `FortuneStickChipItem.isEquipped(Player)`:62、`@SubscribeEvent onHostileKill(LivingDeathEvent)`:71
- `InfiniteGridChipItem.onChargeGained(Player,int)`:66；`ExtraBatteryChipItem.applySignActiveCooldownReduction(Player,long)`:62
- `FlashlightBurstChipItem.onAttack(Player,LivingEntity)`:79、`extraDamage(Player)`:91；`SensorChipItem.reduceTargetDice(Player,LivingEntity,int)`:51、`onDodgeSuccess(Player,DamageSource)`:67
- `ModAttachments.setFortuneStickBlessingClaimed(Player,boolean)`:373（fabric 已有，非本批新增）

## a) `AstralDiceMod.java` — LoaderBus 显式登记（漏登记 = 钩子永不触发且不报错）

`registerListeners()` 内，两处均**只插入、未重排**：

1. **改后 169–170**（改前 169 为 `AdrenalineChipItem.class` 登记）：在其**之前**新增 1 行注释 + `LoaderBus.INSTANCE.register(...item.chip.AbyssReagentChipItem.class);`
   - 依据：该类带 `@SubscribeEvent onPlayerDeath`（`AbyssReagentChipItem.java:115`）。
2. **改后 175–176**（改前 174 `FlashlightChipItem` / 175 `FriendshipBadgeChipItem` 之间）：新增 1 行注释 + `LoaderBus.INSTANCE.register(...item.chip.FortuneStickChipItem.class);`
   - 依据：该类带 `@SubscribeEvent onHostileKill`（`FortuneStickChipItem.java:71`）。

## b) `item/HealingManager.java` — 深渊试剂：回血钩子 + 赐福/治愈结算加点

3. **`triggerHealing` 改后 271–282**（改前 270–277）。改前：`float total*2` 直接 `player.heal(...)`，无钩子。改后三段：
   - `float requested = total * 2;`（273）
   - `float missing = Math.max(0.0F, player.getMaxHealth() - player.getHealth());`（275）
   - `float effective = Math.min(requested, missing);`（276）—— **在 heal 之前求值**，照抄 forge 279–290 同位置写法（含 `Math.max(0.0F, …)` 夹取，负血/超上限不会传负值）。
   - `player.heal(requested);`（277）语义不变；随后 `AbyssReagentChipItem.onHealingTriggered(player, effective);`（278）。
4. **`addChipPoints` 改后 285–288**：在 `addMedkitPoints(player);`（286）之后追加 `addAbyssReagentPoints(player);`（287）。
5. **新增 `private static void addAbyssReagentPoints(Player)` 改后 290–297**：`if (!…isEquipped(player)) return;`（295）+ `add(player, …BOON_POINTS);`（296）。形态照 forge 305–308（与 `addMedkitPoints` 同位同口径，受 `HEALING_POINT_CAP` 约束）。

## c) `item/ChargeManager.java` — 无线输电杆：获得充能；额外电池：立牌主动冷却

6. **`addStacks` 改后 81–89**（改前 5 行体）。改前：`return ChargeEffect.addStacks(player, stacks);` 直接返回。改后：`before/total/gained` 三行，`gained > 0` 时 `InfiniteGridChipItem.onChargeGained(player, gained);`（86）—— 照 forge 84–92；`gained >= 1` 才触发，已到上限（gained=0）与客户端侧不触发，一次调用只发放一次。方法 javadoc 同步扩写（76–79）。
7. **`signCooldownTicks` 改后 123–130**（改前 3 行体）。改前：`return capByCharge(player, baseTicks, …CAP_SECONDS);`。改后：`long capped = capByCharge(...)`，再 `return ExtraBatteryChipItem.applySignActiveCooldownReduction(player, capped);`（128–129）。
   - **作用域硬边界**（已在注释 125–127 明写，2026-10-21 用户裁决）：只作用于「立牌主动技能冷却」，未触及 `effectCooldownTicks` / 抽奖盒 / 商店等其它冷却路径。

## d) `combat/DiceCombatEvents.java` — 爆闪/感测/财运签复位

8. **电磁炮段之前：改后 286–290**（改前 284 后即电磁炮注释）。插入 4 行注释 + `FlashlightBurstChipItem.onAttack(player, target);`（290），位于 `if (target == player) return;` 之后、`// 电磁炮筹码` 之前 ⇒ 不要求触发赐福、不要求主手持近战武器；上方 `directEntity instanceof Player` 闸门已保证「这一击直接来源就是该玩家本人」。
9. **闪避成功段：改后 1718–1723**（`applyDodgeCancel` 内，`BufferShieldChipItem.onHurt` 之后）：`if (!isInCounterChain()) { …SensorChipItem.onDodgeSuccess(player, event.getSource()); }`（1721–1723）。该方法是全部闪避来源（枪匠破绽 / 肾上腺素闪避 / 魔女漂浮闪避）的统一取消入口，挂此处即全覆盖；`isInCounterChain()` 守卫与破绽闪避 / 嘲讽反击 / 鼠鼠反击同一递归截断口径。
10. **`defenseBaseDice` 两处（玩家对玩家 / 实体分支）**：
    - 改后 **658–660**（改前 655）：`defenseBaseDice = rollCombatDie(targetPlayer);` 之后 `defenseBaseDice = SensorChipItem.reduceTargetDice(player, targetPlayer, defenseBaseDice);`
    - 改后 **680–682**（改前 677）：生物分支 `if/else` 之后（`else-if` 块内末尾）`defenseBaseDice = SensorChipItem.reduceTargetDice(player, target, defenseBaseDice);`
    - 两处都**只包防御方基础骰点**，攻击方骰点不受影响；均在防御卡 / 防御修饰器读取 `defenseBaseDice` 之前。
11. **财运签复位两处**（对应 forge 342/344 与 354/355；forge 实际落于 `DiceCombatEvents.java:729` / `:741`）：
    - 改后 **351–352**：攻击方 `setCursedSwordBlessingTriggered(player, false)` 之后，新增 `ModAttachments.setFortuneStickBlessingClaimed(player, false);`
    - 改后 **364–365**：被攻击方分支内 `setCursedSwordBlessingTriggered(targetPlayer, false)` 之后，新增 `ModAttachments.setFortuneStickBlessingClaimed(targetPlayer, false);`

## e) `combat/DiceCombatModifiers.java` — 爆闪额外加伤

12. **改后 395–403**：在「额外加伤:手电筒-强光」`registerExtraDamageModifier` 块（389–393）之后，新增同型 `registerExtraDamageModifier`：`if (ctx.attacker.level().isClientSide()) return 0;` + `return FlashlightBurstChipItem.extraDamage(ctx.attacker);`（402）。

^ **偏离指令 e) 原文**：指令写「攻击力修饰器链里加 `ap += FlashlightBurstChipItem.extraDamage(ctx.attacker)`」，本批落到**额外加伤**通道，理由三条：
     ① forge-1.20.1 `combat/DiceCombatModifiers.java:512–519` 即 `registerExtraDamageModifier`（同位置同写法）；
     ② 同文件 498/506 注释记 2026-09-25 用户裁决：星光加伤**不得**并入攻击力（污染「攻击力快照」⇒ 教主降神狐光基数被连带放大）；
     ③ `item/chip/FlashlightBurstChipItem.java:90` 类内 javadoc 已写死「结算见 DiceCombatModifiers 额外加伤修饰器」。
     若上级裁定仍走 `ap` 路线：删掉本条 modifier、在 `registerAttackModifier` 链内加一行 `ap += …` 即可（其余不动）。

## f) `config/ModCommonConfig.java` + `recipe/condition/ChipRecipesEnabledCondition.java`

13. **`ENABLE_CHIP_RECIPES` 字段：改后 46–52**（`MODERNUI_TOOLTIP_FRAME_COMPAT` 之后、`EVENT_APPLY_MC_TEAM` 之前）：`public static final ForgeConfigSpec.BooleanValue ENABLE_CHIP_RECIPES;` + javadoc（含「本项不进 `snapshot()`，库 `GameplayConfigValues` 无对应字段，不得改动库契约」）。
14. **定义：改后 107–109**（`modernui_tooltip_frame_compat` 之后、`builder.push("event_system")` 之前）：`ENABLE_CHIP_RECIPES = builder.comment("是否启用筹码合成配方(默认：false；关闭时筹码无法通过合成获得)").define("enable_chip_recipes", false);` —— 键名 / 注释 / 默认值与 forge 逐字对齐。
15. **`CONFIG_VERSION` 改后 30**：`6` → `7`（该文件 javadoc 自带规则「只有修改配置项时才 +1」；forge-1.20.1 与 neoforge-1.21.1 均为 7），配套在 javadoc **27 行**加一行 v7 说明。
16. **`ChipRecipesEnabledCondition.java` 改后 73 行（原 78 行）**：
    - **4 行**新增 `import com.merlinkitsune.astral_dice.config.ModCommonConfig;`
    - **44–50 行** javadoc：原 TODO(CHIP-RECIPES-CONFIG) 段替换为「B1-CHIPS-WIRING 已接线」，并明写「接线只解决取值来源，**不构成加载期求值**：本方法目前无调用方；fabric 1.20.1 无配方条件机制（`ResourceCondition` 是 1.21.2+），配方 JSON 的 `conditions` 不会被求值」。
    - **51–53 行** `isEnabled()`：`return false;` → `return ModCommonConfig.ENABLE_CHIP_RECIPES.get();`（52）。`register()` 仍为空实现（保持不变）。

## 2. 验证（源码级，未编译）

- **文件范围**：`git diff --numstat` 中我的改动仅落在 7 个文件：`AstralDiceMod.java` 23/0（**含他人同文件既有未提交改动**，本批实增 4 行：169–170、175–176）、`item/HealingManager.java` 16/1、`item/ChargeManager.java` 21/3、`combat/DiceCombatEvents.java` 22/0、`combat/DiceCombatModifiers.java` 10/0、`config/ModCommonConfig.java` 15/1、`recipe/condition/ChipRecipesEnabledCondition.java`；`DiceCombatEvents.java` 为纯新增 22 行 0 删改。
- **禁项 grep**：这 7 个文件内 `net.minecraftforge.` / `net.neoforged.` / `dev.emi.trinkets` / `io.wispforest.accessories` 的 **import 语句 0 命中**；新增代码全部为 `com.merlinkitsune.*`（详见第 3 节边界声明）。
- **行尾 / 编码**：7 文件 CRLF 数与 LF 数相等（bareLF=0）、无 BOM、UTF-8 可完整解码。
- **未做**：`gradlew build`、游戏启动、任何运行时冒烟（用户明令禁止）⇒ 编译期正确性仅由「签名一致 + 括号闭合 + 无未解析简单名」保证，**未经编译器验证**。

## 3. 偏离项与无法判定项（需上级裁决）

1. **e) 通道偏离**：如上，落额外加伤而非攻击力快照（有 forge 同位置 + 用户历史裁决 + 类内 javadoc 三重依据，仍属对指令字面的偏离）。
2. **`ChargeManager.isChargeChipItem` 名单少 1 枚（未改）**：forge 名单含 `InfiniteGridChipItem`（forge `item/ChargeManager.java:55`，注释「共 11 枚」），fabric 现为 10 枚（`item/ChargeManager.java:44–54`）。影响面：**仅装备无线输电杆时**「充能图标/充能类判定」可见性会漏。B1 清单未含此项，遵「不顺手改」原则**未动**，仅记录。
3. **forge 有 `signCooldownTicksBase`，fabric 无**（forge 电池折算前的基准值，供命运指引卡减半使用）。B1 未要求新增，未加；若后续需要「先减半再折算电池」的顺序，需单独一批处理。
4. **`CONFIG_VERSION` 6→7 属清单外配套**：由该文件 javadoc 自带规则推导（改配置项即 +1），非新增配置项以外的主张。
5. **禁项 grep 的模块级边界**：fabric 模块**整体**仍存在 11 处 `dev.emi.trinkets.*` / `io.wispforest.accessories.*` import，全部位于既有兼容垫片（`compat/curios/TrinketBridge.java`、`compat/accessories/AccessoriesCompat.java`）与 `client/gui/StarCoinWalletButtons.java` —— 均**非本批文件、未触碰**（fabric 线用 Trinkets/Accessories 等价 Curios，属既有设计）。本批遵守的判据是「被改文件内 0 命中」。
6. **`DiceCombatEvents.java` 原为 LF**（不是我引入）：本批按硬约束统一为 CRLF，`git diff --numstat` 复核仍为 22/0（`core.autocrlf=true`，索引侧无行尾噪声）。
7. **行号口径**：报告内所有 fabric 行号均为**改后**行号（括号内标注改前锚点）；forge 行号为蓝本线改前行号。用户所指「forge 342/344 与 354/355」经实读为 forge 的 729 / 741 两处（fabric 对应落的 352 / 365）。
8. **工作树并发写者**：`git status` 显示大量**他人**未提交改动（`network/ModNetwork.java`、`screen/ModMenuTypes.java`、`client/ModClientEvents.java`、`item/ModItems.java`、`audio/SoundPlayback.java`、`component/ModAttachments.java`、3 个 lang、`scripts/test/*` 等）及未跟踪 `.tmp_b1e_*.py` —— 一律未触碰；最终「7 文件」结论是在该并发背景下核对的。
