# B1-CARDSHOP 移植报告：卡牌商店核心逻辑（fabric-1.20.1）

> 批次：B1-CARDSHOP　基准：forge-1.20.1（主，同 MC 版本同路径同名文件）／neoforge-1.21.1（仅语义对照，未搬任何 1.21 专属 API）
> 迁移线：fabric-1.20.1（Fabric API 0.92.12 / officialMojangMappings / Java 17 / Loom）　报告日期：2026-11-01

## 0. 结论摘要

- **4 个文件全部落盘**（`shop/` 下同名同包），另按授权改了 1 个接线点（`block/CardShopBlock.java` 的 `TODO(B0-SHOP)`）。
- **1:1 程度**：`CardShopMenu` / `CardShopOpener` **逐字节复制、0 处改动**；`CardShopShelf` 2 处（饰品入口换本线垫片）；`CardShopCycleTicker` 3 处（事件垫片 + 删类注解）。无一处逻辑改写。
- **常量 / 耐久 / 冷却 / tick 间隔 / 命中顺序全部未动**：`SHELF_SIZE=MAX_SLOTS`、`MIN_TRANSFER=5`、`MAX_TRANSFER=20`、`TRANSFER_COOLDOWN_SECONDS=60`、`PARUNAN_EXCHANGE_BONUS_RATE=0.50F`、`ATM_TRANSFER_STARLIGHT/COINS=1/2`、`TOPUP_CHECK_INTERVAL_TICKS=10` 与 10 个 DataSlot 编码逐位一致（§3）。
- **未编译**（硬约束：本任务不跑 gradle）。本批自身**无平台未知项**；编译阻塞全部来自 **11 组尚未移植的外部符号**（附件键 4 组、S→C 载荷 2 条、MenuType 1 条、item 静态方法 3 个、LinglingSignManager 1 个），逐条见 §7 —— 本批对它们的调用签名与 forge **一字不差**，符号补齐即编译通过，本批代码无需再改一行。
- **未接线**（硬约束：不改 `AstralDiceMod.java`）：`CardShopCycleTicker` 必须在 `AstralDiceMod#registerListeners()` 里显式登记。**漏了不会报错**，换牌与补差会静默全停（§5.1）。

## 1. 交付文件

路径均相对 `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/`。

| # | 文件 | 行数 | 字节 | forge 对应行数 | 性质 |
|---|---|---|---|---|---|
| 1 | `shop/CardShopMenu.java` | 1058 | 59908 | 1058 | **逐字节复制**，0 改动 |
| 2 | `shop/CardShopShelf.java` | 346 | 20144 | 346 | 复制 + 2 处 import/调用点替换（§4.1） |
| 3 | `shop/CardShopCycleTicker.java` | 100 | 6186 | 98 | 复制 + 3 处事件垫片替换（§4.2），+2 行 javadoc |
| 4 | `shop/CardShopOpener.java` | 36 | 1593 | 36 | **逐字节复制**，0 改动 |
| 5 | `block/CardShopBlock.java` | 83 | 3746 | —（本线既有） | **改**：`openShop(Player)` 空实现 → `CardShopOpener.open(player)`，删仅服务日志的 LOGGER（§4.3） |

`AstralDiceMod.java` / `AstralDiceClient.java` **未改动**。`shop/CardShopCatalog.java`（238 行，B0 移植）与 `shop/CardTradeFilter.java`（119 行）**未改写**，只按既有签名对齐使用。

## 2. 自检结果（可复现）

| 判据 | 结果 |
|---|---|
| 行尾 | 5 个文件全部 CRLF（每文件 LF 计数 == CRLF 计数，裸 LF 0） |
| BOM | 全部无 BOM |
| 禁用 import（硬约束 1） | scope 内（`shop/` 递归 + `block/CardShopBlock.java`）`net.minecraftforge.|net.neoforged.` **0 命中** |
| 第三方饰品库（硬约束 2） | scope 内 `trinkets|wispforest|accessories` **0 命中**；饰品判定统一走 `compat/curios/CuriosApi` |
| 1.21 专属 API | 0 处（无 `StreamCodec` / `CustomPacketPayload` / `DataComponent` 等） |
| starengine 包名 | 用现行 mavenLocal `com.merlinkitsune.starengine:starengine-fabric-1.20.1:1.1.0`（`gradle.properties:40-42`）验签，非 `build/processIncludeJars` 里的旧包名残留 |

## 3. 1:1 语义要点（逐文件）

### 3.1 `CardShopMenu`（1058 行，最大件）

- 布局常量：`INVENTORY_X=117 / INVENTORY_Y=102 / INVENTORY_HOTBAR_Y=160`、`SHELF_ITEM_X=10 / SHELF_ITEM_Y=31`、`SHELF_SIZE=CardShopCatalog.MAX_SLOTS(=6)`、藏槽方式 `HIDDEN_X/Y=-10000`。
- 按钮 id：`BUTTON_TRANSFER=16`（打开二级转账视图）、`BUTTON_EXCHANGE=17`（星光→星币）、`BUTTON_REFRESH_TRANSFER=18`（刷新候选名单）。
- **10 个 DataSlot**（客户端读数口径，编码位不可动）：0..5 为货架格 `encodeShelf(i)= (sold?1:0) | (discounted?2:0) | ((price & 0xFF) << 8)`；6 剩余秒数；7 大酬宾激活；8 换架周期进行中；9 本轮是否已转账。
- **购买**：`handleBuy(ServerPlayer, int shelfIndex, int targetSlot)` → 校验 → `planPlacement`（可空位 / `mergeInto` 并栈 / `findQuickMoveSlot` 兜底）→ `applyPlacement`。并栈判定 `ItemStack.isSameItemSameTags`，上限取 `stack.getItem().getMaxStackSize(stack)`（**不是** 64 常量），与 forge 完全一致。拒绝码 `DENY_NO_COIN=0 / DENY_INVENTORY_FULL=1`（走 `ModNetwork.CardShopDenyMessage`）。
- **命中顺序（不可调换）**：先判钱（`StarEngineEconomy`）→ 再算落位 → 再扣钱 → 再给货 → 再发货架刷新与机器行；失败路径在扣钱**之前**返回。
- **转账**：`handleTransfer` 锁只看 `computeTransferUsed()`；成功后才 `startCycleIfReady()` + `setShopTransferUsed(true)` + `forceDiscountOnOwnShelf()` + ATM 双发（`ATM_TRANSFER_STARLIGHT=1` 星光 / `ATM_TRANSFER_COINS=2` 星币）。跨玩家转账走 `StarEngineEconomy.transfer`。
- **兑换**：`tryExchange` 用 `ResourceConversion.starlightToStarCoins(player, -1)`（-1 = 全兑）；佩戴经商立牌时 `*1.5`（`PARUNAN_EXCHANGE_BONUS_RATE=0.50F`）且**下限 1**，并额外给 `PARUNAN_ENTER_COIN=1`。
- **`quickMoveStack`**：`fromHotbar = index >= playerStart + 27`，`playerStart = SHELF_SIZE`，`playerEnd = SHELF_SIZE + 36`，`QUICK_MOVE_SLOT=-1` 表拒绝。
- `stillValid` 恒 `true`；`removed` 只调 `super`（**不得**在关界面时重抽货架，这是硬不变量）。
- 静态入口 `triggerPromotion(ServerPlayer, long windowTicks)`：只补差 + `reloadShelf`，机器行 `CARD_SHOP_PROMOTION`；另有 `CARD_SHOP_ENTER_REWARD`（`granted=1/0`）、`CARD_SHOP_SHELF_TOPUP`。
- `ShelfSlot`：`mayPlace=false` / `mayPickup=false` / `getMaxStackSize=1`。

### 3.2 `CardShopShelf`（346 行，数据模型 + 抽卡）

- `record CardShopShelf(List<ResourceLocation> items, List<Integer> prices, List<Boolean> discounted, List<Boolean> sold, boolean enterRewardPaid)`；`EMPTY` 常量；`CODEC` 用 `RecordCodecBuilder`，字段 `items` 必填、`prices/discounted/sold` `optionalFieldOf` 默认空表、`enter_reward_paid` 默认 `false`。
- `initialized()=!items.isEmpty()`；`batchId()=31*items.hashCode()+prices.hashCode()`；`stackAt` 走 `BuiltInRegistries.ITEM.getOptional` + `map(ItemStack::new)`（**不抛**未注册 id）；`ensureLengths()` 短补长裁、等长直接返回同一实例。
- `roll()`：`player.getRandom()` + `DiceCurioItem.configStarLevel` + `teamHasParunan` + 大酬宾窗口 + `MemberRecommendationChipItem.shopPriceReduction`；`targetSize()=BASE_SLOTS(3) + (promotion?2:0) + (team?1:0)`；`topUp()` 只增不减（`extras=size-BASE` ⇒ `hasTeamSlot = extras==1||extras>=3`，`hasPromotionSlots = extras>=2`）。
- `priceOf(discounted, memberReduction) = max(0, base - memberReduction)`，`base = discounted ? DISCOUNT_PRICE(1) : BASE_PRICE(3)`。
- **有副作用的唯一入口**：`get/store` 走 `ModAttachments.SHOP_SHELF`；`roll/topUp` 只算不写，写盘由调用方决定（与 forge 一致）。

### 3.3 `CardShopCycleTicker`（100 行，货架刷新周期）

- `@SubscribeEvent static void onServerTick(TickEvent.ServerTickEvent)` → `if (event.phase != TickEvent.Phase.END) return;`（forge 1.20.1 本来就写两相 END，**不是** .Post 写法，无需改）。
- 每 tick 遍历 `server.getPlayerList().getPlayers()`：先 `refreshIfDue(player)`，再在 `server.getTickCount() % TOPUP_CHECK_INTERVAL_TICKS(10) == 0` 时对持 `CardShopMenu` 的玩家调 `menu.topUpIfNeeded("tick")`。
- `refreshIfDue`：`now = player.level().getGameTime()`，`end = ModAttachments.getShopTransferCooldownEnd(player)`，`end <= 0 || now < end` ⇒ 返回 `false`（**不重抽**）；否则 `roll` → `store` → `setShopTransferCooldownEnd(0)` → `setShopTransferUsed(false)` → 若菜单开着则 `reloadShelf()` + 机器行 `CARD_SHOP_CYCLE`。

### 3.4 `CardShopOpener`（36 行）

`open(Player)` 转调 `open(Player, boolean viaActiveSkill)`：客户端侧直接 return，服务端 `serverPlayer.openMenu(new SimpleMenuProvider((containerId, inventory, p) -> new CardShopMenu(containerId, inventory, viaActiveSkill), Component.translatable("gui.astral_dice.card_shop")))`。**注意 openMenu 这里走的是 3 参构造**（openMenu 不查 MenuType），与 `ModMenuTypes.CARD_SHOP` 的 2 参工厂各走各的，两条都必须存在。逐字节与 forge 相同。

## 4. 偏差与 seam（forge 原样 → fabric 改法 + 理由）

### 4.1 `CardShopShelf`：饰品入口换垫片（2 处，唯一真差异）

- forge 原样：`import com.merlinkitsune.starengine.item.CuriosCompat;` + `CuriosCompat.getCuriosInventory(player)`（2 处：`currentStarLevel`、`hasParunan`）。
- fabric 改法：`import com.merlinkitsune.astral_dice.compat.curios.CuriosApi;` + `CuriosApi.getCuriosInventory(player)`。
- 理由：硬约束 2 要求饰品统一走本模组垫片；starengine 的 `CuriosCompat` 在 fabric 产物里未提供（本线全树 0 命中）。**签名同形**：`compat/curios/CuriosApi.java:89` 返回 `Optional<ICursiosItemHandler>`，`ICursiosItemHandler.java:25` 有 `Optional<SlotResult> findFirstCurio(Predicate<ItemStack>)` ⇒ 原调用的 `.isEmpty() / .get().findFirstCurio(...).stack()` 语义与返回类型完全不变，**行为零差异**。

### 4.2 `CardShopCycleTicker`：事件垫片 + 去类注解（3 处）

- forge 原样：`import net.minecraftforge.eventbus.api.SubscribeEvent;` / `import net.minecraftforge.event.TickEvent;` / `@Mod.EventBusSubscriber(modid = AstralDiceMod.MODID)`。
- fabric 改法：`com.merlinkitsune.astral_dice.platform.event.SubscribeEvent`、`...platform.event.TickEvent`、**删类注解**（连带删变多余的 `AstralDiceMod` import，改为 javadoc 里的登记说明）。
- 理由：Fabric 无 `@Mod.EventBusSubscriber` 对应机制，本线「自建总线 + 显式登记」是既定形态。垫片 `platform/event/TickEvent.java:37-65` 提供 `ServerTickEvent(Phase, BooleanSupplier, MinecraftServer)` + `getServer()`，`FabricBridges.java:124-126` 以 `ServerTickEvents.END_SERVER_TICK` 派发 END 相；同写法先例 `combat/OrbitalBombardmentManager.java:190-195`。**方法体一字未动**，`phase != END` 早退保留（与既有多件移植件一致，且无害）。
- 代价：**必须显式登记**，否则静默失效（§5.1）。

### 4.3 `CardShopBlock`：接线（按授权）

- forge 原样（forge 侧同名方法）：直接 `CardShopOpener.open(player);`。
- fabric 改法：`private static void openShop(Player player)` 体内的失败日志注释 → `com.merlinkitsune.astral_dice.shop.CardShopOpener.open(player);`；顺带删掉因此变多余的 `LogUtils`/`Logger` import 与 `LOGGER` 字段；类 javadoc 的「shop 包未移植」段改写为「与 forge 逐字一致，菜单经由 `ModMenuTypes.CARD_SHOP` 的 `MenuType` 走原版 `openMenu`」。
- 理由：`TODO(B0-SHOP)` 的占位实现只打日志不办事；forge 该处就是一行调用，1:1 恢复。

## 5. 需 Lead 接线（确切类名 / 方法 / 插入位置）

### 5.1 事件登记（**必做，漏了静默失效**）

`AstralDiceMod.java:136 private static void registerListeners()` 内，按现有字母序插在 `item.InvestigationEventUtil.class`（现 L161）**之后**、或与其它 `shop.*` 分组一起：

```java
LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.shop.CardShopCycleTicker.class);
```

### 5.2 菜单类型注册（**必做**）

`screen/ModMenuTypes.java`（现仅 `CARD_INVENTORY`）追加一行：

```java
public static final RegistryObject<MenuType<CardShopMenu>> CARD_SHOP = MENU_TYPES.register("card_shop",
        () -> new MenuType<>(CardShopMenu::new, FeatureFlags.DEFAULT_FLAGS));
```

`CardShopMenu.java:162` 提供 2 参工厂 `public CardShopMenu(int containerId, Inventory playerInventory)`（`MenuType` 工厂就是要这个形状）；:169 的 3 参 `viaActiveSkill` 版本只给服务端 `openMenu` 用，不要拿来做工厂。注册名 `card_shop` 与 forge 一致。

### 5.3 协议 id 追加（跨批次，本批只调用不实现）

`network/ModNetwork.java` 现末位是 `id 15 = BeamFxPayload`（B1-NETWORK）。卡牌商店两条**必须追加在 15 之后**（顺序即协议，不得插队）：

- `id 16 = CardShopTransferListMessage`（S→C）：内含 `public record Entry(UUID id, String name, long balance)`；`encode` 用 `writeCollection` + `writeUUID/writeUtf/writeVarLong`，`decode` 用 `readList` + 对应读法；`handle` 里 `if (ctx.get().getDirection().getReceptionSide().isClient()) client.CardShopClientState.setTransferCandidates(msg.entries);`
- `id 17 = CardShopDenyMessage`（S→C）：字段 `int reason`，`writeVarInt/readVarInt`；`handle` → `client.CardShopClientState.notifyDeny(reason)`。

调用点（本批已写死，签名不可改）：`CardShopMenu.java:598/604/609`、`:990`。

### 5.4 附件键（跨批次）

`component/ModAttachments.java` 按既有 `AttachedDataKey.builder(name, codec, supplier)` 风格补 4 键（forge 原样，名字/编解码/同步位都不能改）：

| 键 | 名称 | Codec | 默认 | sync | 访问器 |
|---|---|---|---|---|---|
| `SHOP_PROMOTION_UNTIL` | `shop_promotion_until` | `Codec.LONG` | `0L` | 否 | `getShopPromotionUntil(Player)` / `setShopPromotionUntil(Player, long)` |
| `SHOP_TRANSFER_COOLDOWN_END` | `shop_transfer_cooldown_end` | `Codec.LONG` | `0L` | **是** | `getShopTransferCooldownEnd(Player)` / `setShopTransferCooldownEnd(Player, long)` |
| `SHOP_TRANSFER_USED` | `shop_transfer_used` | `Codec.BOOL` | `false` | 否 | `isShopTransferUsed(Player)` / `setShopTransferUsed(Player, boolean)` |
| `SHOP_SHELF` | `shop_shelf` | `CardShopShelf.CODEC`（public，`CardShopShelf.java:53`） | `() -> CardShopShelf.EMPTY` | 否 | `CardShopShelf.get/store` 已封装，无需再开 |

`SHOP_TRANSFER_COOLDOWN_END` 的 `sync()` **不能省**：Jade 侧读这个同步值显示「补货倒计时」。

### 5.5 三个 item 静态方法 + 一个管理器（跨批次）

- `item/sign/ParunanSignItem.isEquipped(Player)`：forge 原样 `SignEquipCheck.isEquipped(player, ModItems.PARUNAN_SIGN.get())`；**fabric 无 `SignEquipCheck`**（全树 0 命中），本线通行写法即 `CuriosApi.getCuriosInventory(player).map(h -> h.findFirstCurio(s -> s.is(ModItems.PARUNAN_SIGN.get())).isPresent()).orElse(false)`（先例 `combat/DiceCombatEvents.java:306,438,511`）。
- `item/chip/MemberRecommendationChipItem.shopPriceReduction(Player)`：forge 原样 `return isEquipped(player) ? 1 : 0;`（`isEquipped` 本线已有）。
- `item/chip/VitaminPillChipItem.notifyCardObtained(Player, ItemStack)`：forge 原样判 `ModItems.isCardItem(stack)` → `onCardGained(player, card.getCount())` → `TeruSignItem.onAttackCardGained(player, card)`。
- `item/sign/LinglingSignManager`（forge 435 行）**整类在本线不存在**；本批只用 `resolvePlacedItem(ItemStack, Player)`（`CardShopMenu.java:486-490`，注释与 `CardShopScreen#canAcceptDrop` 共用同一入口）。按 `docs/compat-1.5.0-porting.md` §31，该方法是唯一入口、禁止各处自写一份 ⇒ **建议整类随障碍牌批次移植**，不要为商店单独造实现。

## 6. 无法判定项

1. **`LinglingSignManager` 归属**：属障碍牌批次（本线已有 `entity/BarricadeEntity`、`entity/RepulsionEntity`、`client/BarricadeRenderer`，独缺该管理器）。若该批次不排期，商店购买线在「路障牌/精灵牌转换」上会留一个编译空位。
2. **客户端侧**：`CardShopScreen` / `CardShopClientState` / 拖拽 `canAcceptDrop` 不在本批 scope，本批无法验证客户端拖拽路径能否落地。
3. **机器行消费方**：`CARD_SHOP_*` 四条机器行已按 forge 字面写出，但测试台用例（`scripts/test`）尚无对应断言，未验证。

## 7. 未解析符号清单（编译阻塞，全部来自本批之外）

| 符号 | 引用点 | 归属 |
|---|---|---|
| `ModAttachments.getShopTransferCooldownEnd/setShopTransferCooldownEnd` | `CardShopMenu.java:570,763,831,855,858`；`CardShopCycleTicker.java:80,86` | 附件键批次（§5.4） |
| `ModAttachments.getShopPromotionUntil/setShopPromotionUntil` | `CardShopMenu.java:755,1008` | 同上 |
| `ModAttachments.isShopTransferUsed/setShopTransferUsed` | `CardShopMenu.java:656,843,861`；`CardShopCycleTicker.java:88` | 同上 |
| `ModAttachments.SHOP_SHELF` | `CardShopShelf.java:182,186,200,229,264` | 同上 |
| `ModNetwork.CardShopTransferListMessage(.Entry)` | `CardShopMenu.java:598,604,609` | 网络批次（§5.3） |
| `ModNetwork.CardShopDenyMessage` | `CardShopMenu.java:990` | 同上 |
| `ModMenuTypes.CARD_SHOP` | `CardShopMenu.java:170` | 接线（§5.2，1 行） |
| `ParunanSignItem.isEquipped(Player)` | `CardShopMenu.java:299,581,679` | item 批次（§5.5） |
| `MemberRecommendationChipItem.shopPriceReduction(Player)` | `CardShopMenu.java:682` | 同上 |
| `VitaminPillChipItem.notifyCardObtained(Player, ItemStack)` | `CardShopMenu.java:536` | 同上 |
| `item.sign.LinglingSignManager.resolvePlacedItem` | `CardShopMenu.java:489` | 障碍牌批次（§5.5） |

补齐以上 11 条符号即可编译；**本批 4 个文件 + CardShopBlock 无需再改一行**。
