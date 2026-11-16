# B16 — D-08 / D-05 / D-07 三条漂移移植报告（fabric-1.20.1）

> 基准线 `forge-1.20.1` → 目标线 `fabric-1.20.1`。行号均为当次实读（写盘后复核）。批次：B16。
## 0 结论速览

| 编号 | 状态 | 一句话 |
|---|---|---|
| D-08 | **DONE** | 6 个类 + 1 个数据组件补齐：键、4 个 mark/判定方法、2 个巡查方法、4 类调用点全落；非本人临时牌在「拾取 / 物品栏 / 饰品 / 菜单槽位 / 使用」五条路径上都会被拦下或清掉 |
| D-05 | **DONE** | `CHARGE_TARGET` 6→8；补 `ATTACK_SPEED ×MULTIPLY_TOTAL` 修饰器（近战冷却 −50%）；补 `BaseChipItem#attributeModifierId(Item,String)` 静态重载；三语 3 把键逐字对齐 forge |
| D-07 | **DONE** | 新建 fabric 侧 `AnvilRepairEvent` 垫片 + 接 Puzzles `AnvilRepairCallback`；`onAnvilRepair` → `ChipShopState.withRefreshGranted()` 落地，`withRefreshGranted()` 不再是死代码 |
| D-07b | **DONE（审计口径订正）** | 审计说的「缺 `setMaterialCost` ⇒ 免费强化」在 fabric 源码层不成立（`event.setMaterialCost(req)` 一直在）；真 bug 是 `PuzzlesBridges` 的 AnvilUpdate 桥恒返回 `EventResult.PASS` ⇒ Puzzles 侧 `if (result.isPass()) return;` 把写进去的产物全丢 ⇒ 铁砧升星根本不产出。已改 `ALLOW` |
| BLOCKED | **无** | 三条都没有落到「fabric 不存在该平台能力」；D-07 所需事件在原版 1.20.1 里就是 `AnvilMenu#onTake`，Puzzles 已提供等价注入点 |
## 1 D-08 临时牌获得者绑定（安全漏洞，最高优先级）
### 1.1 先回答「这个键在 1.20.1 上是什么」

`forge-1.20.1` 的 `component/ModDataComponents.java:87-90` 不是平台数据组件，而是走 `ItemDataKey` 的 ItemStack NBT（1.20.1 没有 NeoForge 的 `DataComponentType`）：

```java
// 临时牌的获得者 UUID(2026-10-27 用户裁决:临时牌只能由获得者持有与使用)。
// 未写入 NBT 前 get 返回 null = 无主(老存档)⇒ 按「非本人持有」清掉;纯服务端判定,客户端不读。
public static final ItemDataKey<UUID> TEMPORARY_CARD_OWNER =
        ItemDataKey.create("temporary_card_owner", UUIDUtil.CODEC);
```

fabric 线 `ModDataComponents` 用的是同一套 `ItemDataKey` / `UUIDUtil.CODEC`（`UUIDUtil` 早已 import），所以等价 NBT 方案 = 原样复制这段，不引入任何新依赖。落点：`fabric-1.20.1/.../component/ModDataComponents.java:87-90`（键在 :89）。
### 1.2 forge 行号 → fabric 新行号

| # | forge（`forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/…`） | fabric 新位置 |
|---|---|---|
| 1 | `component/ModDataComponents.java:87-90` 定义 `TEMPORARY_CARD_OWNER` | `component/ModDataComponents.java:87-90`（键 :89） |
| 2 | `component/AppliedStone.java:1-77` 四分量 record（`type, uses, temporary, owner`） | `component/AppliedStone.java:1-77` 整文件（record :27；CODEC 第 4 字段 :33；兼容构造器 :37 / :45；`ownerId()` :55） |
| 3 | `item/card/TemporaryCardUtil.java:168-231` mark 家族 + 判定 | `:169` mark 委托；`:181` `mark(Player,ItemStack,UUID)`；`:187` `mark(ItemStack,long,UUID)`；`:198` `ownerOf`；`:204` `ownerKeyOf`；`:210` `isOwnedBy`；`:223` `isIllegalHold` |
| 4 | `:416-420` `grantRandom` 发牌写 owner | `:408` `grantRandom`（内部 `player.getUUID()` 落 NBT） |
| 5 | `:660-703` `purgeEquippedWhere` + `purgeEquipped` 委托 | `:662` 委托；`:671` `purgeEquippedWhere(Player, Predicate<AppliedStone>)` |
| 6 | `:705-728` `isLegalStoneOwner` / `purgeIllegalEquipped` | `:706` `isLegalStoneOwner`；`:720` `purgeIllegalEquipped` |
| 7 | `:730-776` `purgeIllegalHoldings`（背包 0..35 / 副手 / `getCarried()` / 打开中的 `CardInventoryMenu`） | `:739` `purgeIllegalHoldings` |
| 8 | `:805-823` `tick()` 两处接线 | `:808` `purgeIllegalEquipped(player)`；`:822` `purgeIllegalHoldings(player)` |
| 9 | `event/TemporaryCardEvents.java:64-88` `onItemPickup` | `event/TemporaryCardEvents.java:81` |
| 10 | `screen/CardInventoryMenu.java:266-280` `loadFromDice` 两处 mark | `:269` / `:279`（`mark(player, itemStack, stone.ownerId())`） |
| 11 | `:302-306` `new AppliedStone(..., ownerKeyOf(stack))` | `:306`（4 参 + `TemporaryCardUtil.ownerKeyOf(stack)`） |
| 12 | `:354-367` `purgeIllegalCards()` | `:361`（`isIllegalHold` 命中 ⇒ `setItem(i, EMPTY)`，返回累计数量） |
| 13 | `:489-496` / `:521-528` 两处 `mayPlace` 拦截 | `:493` / `:525`（保持 fabric 的 `this.index`） |
| 14 | `item/card/BaseEffectCardItem.java:402` / `:515` 使用拦截 | `:386`（`tryStartSelector`）/ `:491`（`tryUseCard`） |
### 1.3 行为结果（漏洞被封住的路径）

- **转手**：他人掉落/丢出的临时牌被本玩家拾取 ⇒ `EntityItemPickupEvent` 判定非法 ⇒ `setItem(EMPTY)` + `discard()`（就地销毁，`LOGGER.debug` 记录）。
- **持有 / 使用**：每 20 tick 的 `purgeIllegalEquipped` + 每 tick 尾的 `purgeIllegalHoldings` 扫背包 / 副手 / 鼠标所持 / 打开中的卡牌菜单；`BaseEffectCardItem` 两处入口拦截、`CardInventoryMenu` 两个 `mayPlace` 拒绝他人牌进槽。
- **装备中**：`purgeIllegalEquipped` 走 `purgeEquippedWhere`，只清「临时且非本人」的已嵌牌（原 `purgeEquipped` 语义不变，仍是清全部临时牌）；NBT 无 owner（老存档）⇒ `ownerOf` 返回 `null` ⇒ 按「非本人」清掉，与 forge 注释里的裁决一致。
## 2 D-05 永动筹码

| 项 | forge | fabric 新位置 |
|---|---|---|
| `CHARGE_TARGET` | `item/chip/PerpetualMotionChipItem.java:35` = **8** | `:35` = 8（原为 6） |
| `ATTACK_SPEED_MULTIPLIER` | `:41` = `1.0D` | `:41` |
| `attackSpeedModifierId()` | `:55-57` | `:55-57` |
| `getAttributeModifiers(SlotContext,UUID,ItemStack)` | `:65-73`（`Attributes.ATTACK_SPEED` + `MULTIPLY_TOTAL`） | `:66-73`（`HashMultimap` 写法照本线 MotoHelmet / SpeedSkates / Sandwich 先例） |
| 静态 id 派生 | `item/chip/BaseChipItem.java:81-87` `attributeModifierId(Item,String)` | `:81-87` 新增静态重载；`:70-71` 的 protected 版改为委托它 |
| 近战冷却补偿 | `combat/DiceCombatEvents.java:1562-1591` `diceBattleAttackStrengthScale` | `:1145-1175` 同构移植（临时叠加 `MULTIPLY_TOTAL = 1/(1+a)−1` 探针，`finally` 移除；调用点 `:574`） |

> 补偿方法不是额外发挥：forge 侧早就存在（否则装上修饰器后骰战基础段会按 ×2 攻速算虚高伤害）。fabric 若只搬修饰器不搬补偿，等于引入新的数值漂移，故一并移植，并保留 forge 的 `factor <= 0` / `counter == 0` 早退。
### 2.1 语言/手册三把键（值逐字取 forge 三语；三语行号一致）

| 键 | fabric 位置 |
|---|---|
| `tooltip.astral_dice.chip.perpetual_motion` | `:578`（充能不足 `§e8§7` 点补至 `§e8§7` 点 + `装备期间近战冷却 §e-50%%§7`） |
| `astral_dice.guide.entry.perpetual_motion_chip.1` | `:200`（8 点 + 冷却减半） |
| `astral_dice.guide.entry.battle_system_changes.3` | `:199`（**新增键**，字母序插入 guide 块；见 §5 未做项） |

染色复核：数值连符号着 `§e`（`§e8§7`、`§e-50%%§7`）；本批无时间量 ⇒ 不涉及 `§9`。字面百分号写作 `%%`。
## 3 D-07 铁砧取走成品 → 商店刷新 +1
### 3.1 事件取证（先定「有没有垫片」，不猜）

- fabric `platform/event/**` 原本只有 `AnvilUpdateEvent`，没有 `AnvilRepairEvent`。
- 但 `ref/puzzles-lib` 有等价回调：`Common/src/main/java/fuzs/puzzleslib/api/event/v1/entity/player/AnvilRepairCallback.java:21` —— `void onAnvilRepair(Player, ItemStack leftInput, ItemStack rightInput, ItemStack output, MutableFloat breakChance)`。
- 实装点 `Fabric/src/main/java/fuzs/puzzleslib/mixin/AnvilMenuFabricMixin.java:43-47`：注入原版 `AnvilMenu#onTake`，位于 `Container#setItem` 之前（= Forge `ForgeHooks.onAnvilRepair` 的语义位置：输入槽与产物都还在），`breakChance` 初值 `0.12F`；其 `onTake$1` 用终值判铁砧损坏 ⇒ **D-07 不 BLOCKED**（原版 1.20.1 没有「取走成品」的 `PlayerEvent`，唯一入口就是 `AnvilMenu#onTake`；fabric 靠 Puzzles 已接上，无需自写 mixin）。
### 3.2 改动对照

| 项 | forge | fabric 新位置 |
|---|---|---|
| 事件类 | `net.minecraftforge.event.entity.player.AnvilRepairEvent` | **新建** `platform/event/entity/player/AnvilRepairEvent.java`（86 行；`getLeft/getRight/getOutput/getBreakChance/setBreakChance`，构造器同 forge） |
| 桥 | Forge 原生事件总线 | `platform/PuzzlesBridges.java:242` `AnvilRepairCallback.EVENT.register(...)` → `LoaderBus.INSTANCE.post(event)` → `breakChance` 写回 `MutableFloat` |
| `onAnvilRepair` | `event/AnvilUpgradeHandler.java:51-95` | `:51-96`（方法体 `:67-96`，`LOGGER.info` `:94`） |
| 成本白名单 | `:97-105` `isDice` / `:107-120` `starCoinCost` | `:100-107` / `:109-122`（`onAnvilUpdate` 同时改为调这两个，消灭第二套 15/20/25） |
| `CHIP_SHOP_REFRESH_GRANTED` 日志 | `:92-94` | `:94-96`（同名同字段，便于测试台按行断言） |
### 3.3 D-07b 真 bug（审计口径订正 + 已修）

审计早前提的「fabric 的 `AnvilUpgradeHandler` 可能缺 `event.setMaterialCost(...)` ⇒ 免费强化」：**不成立**。改前 `event/AnvilUpgradeHandler.java:48-49` 就有 `event.setMaterialCost(req); event.setCost(10);`。

真 bug 在桥：`platform/PuzzlesBridges.java` 的 AnvilUpdate 桥把 `output/xpCost/materialCost` 写进 Puzzles 的 `MutableValue/MutableInt` 后恒 `return EventResult.PASS`，而 `ref/puzzles-lib/Fabric/.../AnvilMenuFabricMixin.java:84` 是 `if (result.isPass()) return;` —— PASS = 不干预、直接放行原版 ⇒ 写进去的产物被整段丢弃（后果不是「免费强化」，而是「升星根本不产出」）。只有 interrupt 类结果才走到 `:87-89 resultSlots.setItem(0, output.get()); this.cost.set(...); this.repairItemCountCost = ...`。

修法：产物非空 ⇒ `return EventResult.ALLOW;`（`platform/PuzzlesBridges.java:232`）；产物为空 ⇒ 仍 `PASS`（让原版算）；取消 ⇒ `INTERRUPT`。注释已写清「为什么不能是 PASS」。
## 4 自检原始输出
### 4.1 `python scripts/__javac_check.py`

```
EXIT 1 ERRORS 171 jars 685 srcs 522
=== buckets (NOISE_* = 由 Loom AW / 接口注入 提供，javac 侧无法复现) ===
  164  NOISE_AW
    7  NOISE_IFACE_INJECTION
=== REAL errors ===
=== REAL by file ===
saved errors.txt lines= 984
```

`=== REAL errors ===` 后仍为空。171 条既有噪声里与 B16 相关的仅 `errors.txt:24/972/977` 三处 `MenuType` 构造器 private access（Loom AW 噪声，B16 未触碰菜单注册）；grep `Anvil|PuzzlesBridges|ChipShop` 无新增错误行。
### 4.2 `python tools/verify_fabric_assets.py`

```
jar = fabric-1.20.1\build\libs\astral_dice-1.3.7-alpha.2+fabric_1.20.1.jar
[1] 物品闭环: 注册 157 (方块物品 3) / 模型 138 / 贴图 138 (方块贴图另计)
[5] 语言闭环: zh_cn 980 / en_us 980 / ja_jp 980
=== FAIL ===
  ✗ 方块物品资源不全(blockstate=['card_shop', 'chip_shop', 'event_drawer'] / model=['card_shop', 'chip_shop', 'event_drawer'] / texture=['card_shop', 'chip_shop', 'event_drawer'])
  ✗ 缺模型(紫黑格): ['abyss_reagent_chip', 'alana_sign', 'ame_sign', 'card_shop', 'chip_shop', 'deepseek_sign', 'dorothy_sign', 'effect_card_barricade', 'effect_card_repulsion', 'event_drawer', 'event_ticket', 'extra_battery_chip', 'flashlight_burst_chip', 'fortune_stick_chip', 'infinite_grid_chip', 'jill_sign', 'kangel_sign', 'lingling_sign', 'repulsion', 'sensor_chip']
  ✗ 缺贴图: ['abyss_reagent_chip', 'alana_sign', 'ame_sign', 'deepseek_sign', 'dorothy_sign', 'effect_card_barricade', 'effect_card_repulsion', 'event_ticket', 'extra_battery_chip', 'flashlight_burst_chip', 'fortune_stick_chip', 'infinite_grid_chip', 'jill_sign', 'kangel_sign', 'lingling_sign', 'repulsion', 'sensor_chip']
```

**结论：FAIL 与 B16 无关（既有状态，非本批引入）**。该脚本 `tools/verify_fabric_assets.py:127-130,383-391` 是**产物 jar 判据**（`build/libs` 最新 = `1.3.7-alpha.2`，mtime 2026-10-05），本轮禁止 gradle ⇒ jar 无法重建；源树里手工模型只有 4 个（其余靠 datagen 生成），而 FAIL 清单全部是模型/贴图，B16 既未新增物品也未改任何资源。本批触及的面是 `[5] 语言闭环 980/980/980`，已通过。
### 4.3 `python tools/check_lang_sync.py --line fabric-1.20.1`

```
[OK]   fabric-1.20.1: en_us=980 键，3 份语言键集一致
```
### 4.4 `python tools/verify_defense_unit_parity.py`

```
[PASS] D8[fabric-1.20.1]  P1 未移植（旧折算命中 0 处）—— 必须登记在 docs/compat-1.5.0-porting.md，不作为本批 FAIL
GATE: PASS（42 项全部满足）
```
### 4.5 字节复核（写盘后重读，逐文件）

| 文件 | 行数 | 字节 | bareLF | BOM |
|---|---|---|---|---|
| `component/ModDataComponents.java` | 120 | 5945 | 0 | false |
| `component/AppliedStone.java` | 77 | 4138 | 0 | false |
| `item/card/TemporaryCardUtil.java` | 827 | 48769 | 0 | false |
| `event/TemporaryCardEvents.java` | 95 | 5992 | 0 | false |
| `screen/CardInventoryMenu.java` | 556 | 23807 | 0 | false |
| `item/card/BaseEffectCardItem.java` | 638 | 37122 | 0 | false |
| `item/chip/BaseChipItem.java` | 103 | 5642 | 0 | false |
| `item/chip/PerpetualMotionChipItem.java` | 91 | 5115 | 0 | false |
| `combat/DiceCombatEvents.java` | 1992 | 131494 | 0 | false |
| `event/AnvilUpgradeHandler.java` | 144 | 7442 | 0 | false |
| `platform/PuzzlesBridges.java` | 253 | 16121 | 0 | false |
| `platform/event/entity/player/AnvilRepairEvent.java`（新） | 86 | 3088 | 0 | false |
| `lang/zh_cn.json` / `en_us.json` / `ja_jp.json` | 984 ×3 | 128791 / 132666 / 164561 | 0 | false |

全部 CRLF、UTF-8 无 BOM；三份 lang 均通过 `json.loads` 解析。
## 5 未做项 / 限制

1. **`astral_dice.guide.entry.battle_system_changes.3` 目前是孤立键**：forge 有 `patchouli_books/.../getting_started/battle_system_changes.json`（引用 `name/1..6`），fabric 线 `getting_started/` 只有 `battle_dice / playstyles / special_effects / spell_damage`。整页手册移植会引入新的配方/物品引用面，不属 B16 范围，登记为后续批次；键本身已与另外两把一并对齐 forge。
2. **`EntityItemPickupEvent` 的取消语义受限**：Puzzles 的 `PlayerEvents.ITEM_PICKUP` 是 `void`，不可取消。fabric 侧「不拾取」靠 `setItem(EMPTY)` + `discard()` 达成（效果等价，但不是原版级取消），已在 `event/TemporaryCardEvents.java` 类 javadoc 的 `LIMITATION(fabric 拾取路径)` 段登记。要真正取消需自写 `ItemEntity#playerTouch` mixin（Puzzles 无此能力）—— 记为未做项而非 BLOCKED，因等价效果已达成。
3. **`verify_fabric_assets.py` 的 FAIL 在「禁 gradle」下无法复评**：它是产物 jar 判据（见 §4.2），需一次真实构建后重跑才能给出最终结论；B16 未触碰任何资源/注册面。
4. **环境备注**：工作树在本批之前已是脏的（`git status` 里大量 `M` 文件如 `AstralDiceClient` / `ModSounds` / `ModItems` / `datagen/*` 属先前批次；B16 只改 §4.5 的 15 个路径，未 add / commit / stash / checkout）；`DiceCombatEvents` 的攻速补偿在 fabric 是新代码（forge 已有，见 §2 表末行），两侧已同构但只做源码级对照、未实机实测（本轮禁实机）。
## 6 约束遵守

- 文件改动全部经 DSH `write` 工具落盘；`read` 用于锚点取证与写后复核；`ops_bash` 只跑自检脚本与只读命令（`git status`、`ls`、`grep`、`wc`）。
- 未跑 gradle / 游戏 / datagen / commit；未删任何文件；未改 `build.gradle`、`gradle.properties`、`AstralDiceMod.java`、`shop/**`（只读调用 `ChipShopState` / `ChipShopCatalog` 既有 API）。
- 平台差异未回退：`compat/curios/CuriosApi` 仍在用（`PerpetualMotionChipItem` 的 `CuriosApi.getCuriosInventory`）、`isClientSide()` 保持（`TemporaryCardUtil` 7 处、0 处裸字段）、`new ResourceLocation(a,b)` 未改。
- `CardInventoryMenu` 保留 fabric 的 `this.index`（未引入 forge 的 `getSlotIndex()`）。
