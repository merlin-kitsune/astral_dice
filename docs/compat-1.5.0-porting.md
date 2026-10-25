# 1.5.0 移植映射（P0 → P1：`neoforge-26.1.2` / `fabric-1.20.1`）

> **用途**：把 1.5.0 相对上一版新增/变更的 P0 内容固化成「主题 → P0 落点 → 移植注意」映射表，供 P1 两线移植时逐行核对。
> **状态（2026-10-22 登记）**：下列主题在 P1 两线**均尚未具备**（`grep -rl <符号> neoforge-26.1.2/src/main/java fabric-1.20.1/src/main/java` 全部 0 命中）。
> **口径**：本文件只描述映射，**不改任何 P1 代码**；P1 移植任务由用户单独下达。

## 1. 两线 API 基线差异（移植前先对表，逐项都要落）

| 能力 | `neoforge-1.21.1`（1.21.1） | `forge-1.20.1`（1.20.1） |
|---|---|---|
| 物品同判 | `ItemStack#isSameItemSameComponents` | `ItemStack#isSameItemSameTags` |
| 扣 1 张 | `stack.consume(1, player)`（instabuild 自动不扣） | `if (!player.isCreative()) stack.shrink(1);`（**必须带守卫**） |
| 网络注册 | `RegisterPayloadHandlersEvent` + `registrar.playToClient(TYPE, STREAM_CODEC, handler)`（`network/ModPayloads`） | `SimpleChannel#registerMessage(id++, cls, encode, decode, handle)`（`network/ModNetwork`） |
| 网络载荷 | `CustomPacketPayload` + `StreamCodec` | 手写 `FriendlyByteBuf` `encode`/`decode` |
| 事件订阅 | `@EventBusSubscriber` + `net.neoforged.bus.api.SubscribeEvent` | `@Mod.EventBusSubscriber` + `net.minecraftforge.eventbus.api.SubscribeEvent` |
| 事件类包名 | `net.neoforged.neoforge.event.*` / `...event.server.ServerStoppingEvent` | `net.minecraftforge.event.*` / `...event.server.ServerStoppingEvent` |
| 玩家附件 | `AttachmentType`（`component/ModAttachments`） | `AttachedDataKey` + `Codec`（同名键，`component/ModAttachments`） |
| 方块交互入口 | `useItemOn` + `useWithoutItem` | 六参 `use(...)` 一肩挑（1.20.1 无上述两者） |
| 服务端 tick | `ServerTickEvent.Post` | `TickEvent.ServerTickEvent` + `phase == END` |

## 2. 主题映射表

| 主题 | P0 落点（文件） | 移植注意 |
|---|---|---|
| **JEI 筹码商店类别** | `compat/jei/ChipShopCategory` `ChipShopJeiPlugin` `ChipShopJeiRecipes` `ChipShopRecipe`；数据源 `shop/ChipShopCatalog` | 两线文件同名同职责，差异全在 JEI 版本 API（1.21.1 = JEI 15.x `ITypedIngredient`；1.20.1 = JEI 11.x）；26.1.2 须按该线 JEI 重取类别/槽位绘制 API，Fabric 须走 `ModMenu`/JEI Fabric 变体；`ChipShopCatalog` 是纯数据（无平台依赖），可整文件直搬。 |
| **激光 / 轨道炮客户端光束** | `network/BeamFxPayload`、`client/BeamFxRenderer`；注册 `network/ModPayloads`（1.21.1）/ `network/ModNetwork`（1.20.1） | 1.21.1 走 `RegisterPayloadHandlersEvent` + `StreamCodec`（`ModPayloads.java:33-36` 的 `playToClient`）；1.20.1 走 `SimpleChannel#registerMessage`（`ModNetwork.java:111`）。**只注册不渲染 = 静默无光束**：客户端渲染器还要在客户端事件里挂载（`ModClientEvents`），Fabric 需改用 `ClientPlayNetworking.registerGlobalReceiver` + `WorldRenderEvents`。 |
| **全员事件硬间隔** | `event/GlobalEventGate`、`event/AstralEventDispatcher`（两处入口）、`event/EventChoiceManager`（**先判间隔再删会话**） | 门禁是纯 Java（`Map<UUID, Long>` + `gameTime`），可直搬；入口差异 = 事件类包名与 tick 事件（见 §1）。`EventChoiceManager` 的顺序不可颠倒：**先判硬间隔 → 再 `remove(session)`**，反了会把玩家会话误删成「无会话」而被放行。 |
| **强化拒止** | `entity/RepulsionEntity`（充能归属 / 逐 tick 收敛 / 飞行与遁地处理）、`event/RepulsionTeleportGuard`（传送拦截） | 实体注册与 `EntityType.Builder` 两线同构；传送拦截在 1.21.1 = `EntityTeleportEvent`/`PlayerTeleportEvent` 的 NeoForge 版，1.20.1 = Forge 版（包名不同，语义同）；「充能归属」依赖玩家附件（§1 附件差异），收敛必须逐 tick 写回，不能只在生成时算一次。 |
| **路障** | `entity/BarricadeEntity`（数值 + `LIFETIME_TICKS = 24000L`，即 20:00）、`event/BarricadeAggroHandler` | 寿命用 `level.getGameTime() - createdAt` 差值，**不要用 `tickCount`**（区块重载会归零）；1.20.1 无 `createdAt` 的老存档需回填当前 `gameTime`（见该线 `readAdditionalSaveData` 分支）。仇恨处理两线事件类包名不同。 |
| **抽奖盒** | `block/EventDrawBlock`（投入冷却 `BLANK_INSERT_COOLDOWN_TICKS = 2400L`）+ 附件 `event_drawer_blank_insert_at` | 交互入口两线不同（1.21.1 `useItemOn`；1.20.1 六参 `use`）；附件在 1.20.1 是 `AttachedDataKey.builder(name, Codec.LONG, () -> 0L)`。**语义勿抄错**：抽奖盒 = 只清购买冷却（无刷新机制），筹码商店 = 刷新批次 +1。 |
| **商店并栈** | `shop/CardShopMenu#planPlacement`、`screen/CardShopScreen#canAcceptDrop`、`item/sign/LinglingSignManager#resolvePlacedItem` | 三处共用**同一份**转换逻辑（`resolvePlacedItem` 唯一入口），移植时禁止各写一份；物品同判按 §1（1.21.1 组件 / 1.20.1 NBT）；`planPlacement` 是服务端权威顺序（目标格同类栈 → 其余同类未满栈 → 空槽），客户端 `canAcceptDrop` 只做预判。 |
| **电池折算作用域** | `item/ChargeManager#signCooldownTicksBase` + 附件 `sign_active_max_cooldown_base` | `signCooldownTicksBase` = 只做充能封顶、**跳过「额外电池 ×0.5」**；电池折算仍由 `signCooldownTicks` 负责。命运指引卡减半读 base，老存档缺附件时回退折算后的记录（`FateGuidanceCardItem` 有兼容分支，须一并移植）。 |
| **防御力折算 1:1（2026-10-25 P0 已改，P1 欠账）** | `combat/DiceCombatModifiers`（`armorValueForDefensePoints(points, side)` / `defensePointsForArmor(armor, side)` / `PLAYER_ARMOR_PER_DEFENSE` / `MOB_ARMOR_PER_DEFENSE`）、`combat/DiceCombatEvents` 骰战 `effectiveArmor`、`item/chip/MotoHelmetChipItem`、`item/dice/ObsidianDiceItem`、`item/sign/DorothySignItem`（暖意护甲 + 「当前防御力」反算）、`item/sign/JillSignItem`、事件雷暴生物护甲 | P0 口径：防御力点数按 **库系数倒数** 折成护甲（`1.0 / CombatFormula.PLAYER_ARMOR_COEF` ≈ 3.333 护甲/点，生物 `1.0 / MOB_ARMOR_COEF` = 2.5），**禁止**任何地方再写死 `points * 2.0`（旧「1 防御 = 2 护甲」残留在 P1 各 5 处，见守门 D8）。折算必须走单一入口，反向（护甲→防御力）只走 `defensePointsForArmor`；取整记得带 `+ 1.0E-6D` 浮点尘埃容差。库系数是现成 public 常量，**库无需改动、无版本 bump**。

## 3. 取证命令（移植前后各跑一遍）

```bash
# P1 是否已具备（0 = 未移植）
for s in BeamFxPayload GlobalEventGate RepulsionEntity BarricadeEntity EventDrawBlock \
         signCooldownTicksBase planPlacement canAcceptDrop ChipShopCategory; do
  echo "$s=$(grep -rl "$s" neoforge-26.1.2/src/main/java fabric-1.20.1/src/main/java 2>/dev/null | wc -l)"
done
# 创造模式扣卡两线一致性（P0 现状）
grep -rn "shrink(1)" forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/card/ \
  forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/screen/CardChoiceMenu.java
# 防御力折算 1:1 守门（D1–D8；D8 打印 P1 旧折算残留处数，正常应各 5 处）
python tools/verify_defense_unit_parity.py
```

## 4. 待办登记

1. 上表 9 个主题在 26.1.2 / Fabric 的移植（按用户批次下达；本文件不含任何 P1 代码改动）。
2. **创造模式扣卡**：P0 两线已对齐（创造不扣、生存扣 1）；`fabric-1.20.1` 的效果卡仍是裸 `shrink(1)`，**待移植时统一**为 `if (!player.isCreative())`。
3. 26.1.2 的 `ChipShopCategory` 等 JEI 类须按该线 JEI 版本重取 API；Fabric 的 JEI 走 `fabric-api` 变体，不可照搬 Forge 侧注册。

4. **防御力折算 1:1（2026-10-25 P0 已修）**：P0 两线已全部改为「防御力点数 ÷ 库护甲系数」的单一入口；**P1 两线仍是旧的 `× 2.0` 折算（各 5 处）**，`python tools/verify_defense_unit_parity.py` 的 D8 项只统计、不判 FAIL。移植时必须同步落地（否则 P1 的「防御力 +X」仍只兑现 60% / 80%），并连带把该线 12 份指南 lang（四线 × 三语）的「4 + 护甲×0.30」改成「2 + 护甲×0.30」与「1 防御力 = 1÷护甲系数 护甲值」。
