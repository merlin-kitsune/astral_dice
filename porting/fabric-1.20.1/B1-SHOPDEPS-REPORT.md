# B1-SHOPDEPS · 卡牌商店缺失共享依赖（fabric-1.20.1）

- 目标线：`fabric-1.20.1`（MC 1.20.1 + Fabric API 0.92.12 + Loom，Mojang 官方映射，Java 17）
- 基线：`forge-1.20.1` 同名文件，逐条 1:1；`neoforge-1.21.1` 仅作语义对照，未搬任何 1.21 专属 API
- **未运行 gradle、未启动游戏**（硬约束 3）：本文任何「能编译」的判断都未经构建验证，编译由主 Agent 统一验证
- 改动 7 文件：5 改（其中 `ModMenuTypes` 已被并发批次先落线，本批只核对）+ 2 新建；未触碰 `shop/`、GUI、`ModNetwork`、`ModItems`、`ModCreativeTabs`、`AstralDiceMod`、`AstralDiceClient`

## 1. 交付清单

| 文件 | 动作 | 落点（行号=落线后） |
|---|---|---|
| `component/ModAttachments.java` | 追加（1758 → 1810 行） | 键 1763 / 1778 / 1791 / 1805；包装器 1766-1771、1781-1787、1794-1800；`SYNCED_KEYS` 追加在 1364 |
| `screen/ModMenuTypes.java` | **未改** | `CARD_SHOP` 已存在于 23-27（`com.merlinkitsune.astral_dice.shop.CardShopMenu::new` + `FeatureFlags.DEFAULT_FLAGS`），`CHIP_SHOP` 29-；17:59:27 由并发批次落线，本批核对 1:1 通过，**无需改动** |
| `item/sign/ParunanSignItem.java` | 追加 | `isEquipped(Player)` 28-32；import 3-4 |
| `item/chip/MemberRecommendationChipItem.java` | 追加 | `shopPriceReduction(Player)` 32-34 |
| `item/chip/VitaminPillChipItem.java` | 追加 | `notifyCardObtained(Player, ItemStack)` 98-103（插在既有 `onCardCrafted` 之前，未删既有事件监听） |
| `network/CardShopTransferListMessage.java` | **新建** 59 行 | 独立顶层类；`public record Entry(UUID id, String name, long balance)` |
| `network/CardShopDenyMessage.java` | **新建** 46 行 | 独立顶层类；`int reason` |

守门实测（只读脚本）：7 文件 `bareLF = 0`（全 CRLF）、`BOM = false`、花括号与圆括号配平差 = 0、
`net.minecraftforge.*` / `net.neoforged.*` 命中 = **0**。

## 2. 1:1 要点

### 2.1 `component/ModAttachments.java`（只追加，既有键零改动）
- 新增 import（行 4）：`import com.merlinkitsune.astral_dice.shop.CardShopShelf;`
- `SHOP_PROMOTION_UNTIL`（1763-1764）：`AttachedDataKey<Long>`，id `shop_promotion_until`，`Codec.LONG`，默认 `() -> 0L`，**不 sync**
  - `getShopPromotionUntil(Player)` / `setShopPromotionUntil(Player, long)`
- `SHOP_TRANSFER_COOLDOWN_END`（1778-1779）：id `shop_transfer_cooldown_end`，`Codec.LONG`，默认 `() -> 0L`，**`.sync()`**
  - 理由与 forge 一致：方块无 BlockEntity ⇒ 1.20.1 的 Jade 悬浮框收不到服务端数据，只能读同步值显示补货倒计时
  - `getShopTransferCooldownEnd(Player)` / `setShopTransferCooldownEnd(Player, long)`
- `SHOP_TRANSFER_USED`（1791-1792）：id `shop_transfer_used`，`Codec.BOOL`，默认 `() -> Boolean.FALSE`，**不 sync**
  - `isShopTransferUsed(Player)` / `setShopTransferUsed(Player, boolean)`（forge 也是 `is…`，非 `get…`）
- `SHOP_SHELF`（1805-1806）：`AttachedDataKey<CardShopShelf>`，id `shop_shelf`，`CardShopShelf.CODEC`，默认 `() -> CardShopShelf.EMPTY`，**不 sync**；**无包装器**（forge 同样没有，消费方直接用 `ModAttachments.SHOP_SHELF.get/set`，见 `shop/CardShopShelf.java:182/186`）
- `SYNCED_KEYS.add(SHOP_TRANSFER_COOLDOWN_END);`（1364）：加在 `SYNCED_KEYS.add(MAMUSHI_AWAKENING);`（1362）之后、`if (SYNCED_KEYS.isEmpty())` 块闭合（1365）之前。此处是 `syncedKeys()` 的**方法体**而非字段初始化表达式，前向引用合法。
- 照 `SIGN_ACTIVE_COOLDOWN_END`（440 行）既有写法：`register(AttachedDataKey.builder(id, codec, def).sync().build())`

### 2.2 `item/sign/ParunanSignItem.java`
```java
public static boolean isEquipped(Player player) {
    return CuriosApi.getCuriosInventory(player)
            .map(h -> h.findFirstCurio(s -> s.is(ModItems.PARUNAN_SIGN.get())).isPresent())
            .orElse(false);
}
```
- forge 蓝本：`SignEquipCheck.isEquipped(player, ModItems.PARUNAN_SIGN.get())`；本线无 `SignEquipCheck`（且 `starengine` 的 `CuriosCompat` 本线也未引入）⇒ 按任务指定与 `MemberRecommendationChipItem.isEquipped` 同型写法，**不加**骰子总闸门（与 forge 语义一致）

### 2.3 `item/chip/MemberRecommendationChipItem.java`
```java
public static int shopPriceReduction(Player player) { return isEquipped(player) ? 1 : 0; }
```
- 与 forge 44 行逐字一致（静态方法；调用点 `shop/CardShopMenu.java` 也是静态调用）

### 2.4 `item/chip/VitaminPillChipItem.java`
```java
public static void notifyCardObtained(Player player, ItemStack card) {
    if (player == null || player.level().isClientSide()) return;
    if (card == null || card.isEmpty() || !ModItems.isCardItem(card)) return;
    onCardGained(player, card.getCount());
    com.merlinkitsune.astral_dice.item.sign.TeruSignItem.onAttackCardGained(player, card);
}
```
- 判定链与 forge 95-100 逐条一致（顺序：客户端拦截 → 非卡牌拦截 → 按数量治愈 → 看板娘立牌 teru 攻击牌被动）
- 本线 `TeruSignItem.onAttackCardGained(Player, ItemStack)` 位于 `item/sign/TeruSignItem.java:435`，签名吻合
- 与既有 `giveCard(...)` 的差别照 forge 保留：`giveCard` 是「本筹码发放卡」路径（receiver + mamushi 被动），`notifyCardObtained` 是「已经获得」的事后通知（不触发 mimi）

### 2.5 两个新建网络类
- 编码/解码逐字段照 forge：`Entry` → `writeUUID` + `writeUtf` + `writeVarLong`；容器用 `writeCollection` / `readList`（forge 同款）；拒绝包 `writeVarInt` / `readVarInt`
- 落点照 forge：`CardShopClientState.setTransferCandidates(msg.entries)` / `CardShopClientState.notifyDeny(msg.reason)`
- 线内风格照 `network/ChipShopDenyPayload.java`：`public final class` + 私有字段 + 访问器 + `static encode/decode/handle`，`handle` 内 `ctx.get().enqueueWork(...)` + `ctx.get().setPacketHandled(true)`
- `NetworkEvent.Context` 本线只有 `getSender()/enqueueWork()/setPacketHandled()`，**没有** `getDirection()/getReceptionSide()` ⇒ forge 的 `if (getDirection().getReceptionSide().isClient())` 守卫无法 1:1 搬，按本线 `ChipShopDenyPayload` 既有写法直接调（Fabric 端 handler 只装在 `EnvType.CLIENT` 侧）

## 3. 偏差（forge 原样 → fabric 改法 + 理由）

1. **包装器 6 个而非任务书写的 8 个**：forge 的 4 个键里 `SHOP_SHELF` **没有**包装器（另 3 个各 2 个 = 6）。为了与 forge 1:1，本批**不加** `getShopShelf/setShopShelf`；消费方继续写 `ModAttachments.SHOP_SHELF.get/set`。
2. **`SignEquipCheck.isEquipped` → `CuriosApi` 直接查槽**：本线无 `SignEquipCheck`；越权自建会与另一批次撞车，故按任务指定用既有垫片。
3. **两条消息是独立顶层类，不是 `ModNetwork` 内部类**：任务书明确要求独立文件；但见 §4.2 —— 已落线的 3 个消费方文件是按「内部类」写的，必须二选一裁定。
4. **`handle` 无侧别守卫**：见 2.5（本线 `NetworkEvent.Context` 无该 API）。
5. `ModMenuTypes.CARD_SHOP` 由并发批次落线：本批**未**写这一行（写会覆盖他人改动），已核对与 forge 1:1（注册名 `card_shop`、`FeatureFlags.DEFAULT_FLAGS`、2 参 `CardShopMenu::new`；`CardShopMenu` 确有 `(int, Inventory)` 构造，见 `shop/CardShopMenu.java:162`）。

## 4. 需主 Agent 接线 / 裁定

### 4.1 `network/ModNetwork.register()` 追加两条（**必须**，否则协议未注册）
插入位置：`register()` 内 `BeamFxPayload` 注册语句（行 78-79）之后、方法闭合 `}`（行 80）之前，紧接既有追加块。

```java
        // 卡牌商店（1.20.1 移植，基线 forge-1.20.1 同名文件）：
        // id 16 = S→C 转账候选名单（对照 forge 的 id 12）、id 17 = S→C 拒绝信号（对照 forge 的 id 15）。
        // 一律追加在末尾，顺序即协议。
        CHANNEL.registerMessage(id++, CardShopTransferListMessage.class,
                CardShopTransferListMessage::encode, CardShopTransferListMessage::decode, CardShopTransferListMessage::handle);
        CHANNEL.registerMessage(id++, CardShopDenyMessage.class,
                CardShopDenyMessage::encode, CardShopDenyMessage::decode, CardShopDenyMessage::handle);
```

id 对照（两条线 id 已因各自 feature 而错位，只保证同一条线内两端一致）：

| forge id | forge 类 | fabric id | fabric 类 |
|---|---|---|---|
| 12 | `CardShopTransferListMessage` | **16** | `CardShopTransferListMessage` |
| 13 | `CardShopTransferMessage`(C→S) | （未落，见 4.3） | — |
| 14 | `CardShopBuyMessage`(C→S) | （未落，见 4.3） | — |
| 15 | `CardShopDenyMessage` | **17** | `CardShopDenyMessage` |

### 4.2 ⚠️ 类命名空间冲突（编译阻断，必须裁定）
已落线的 3 个消费方文件按 **`ModNetwork` 内部类** 写，共 11 处（非 javadoc）：

- `client/CardShopClientState.java:21 / :61 / :66` —— `List<ModNetwork.CardShopTransferListMessage.Entry>`
- `shop/CardShopMenu.java:598 / :604 / :609` 与 `:990`
- `screen/CardShopScreen.java:329 / :1160 / :1294 / :1912`

两套解法，请择一（本批按任务书交付的是**独立类**，即方案 B 的产物）：

- **方案 A（推荐，零改他人文件）**：把这两个类**改写成 `ModNetwork` 的内部 `public static class`**（本批文件已含完整实现，整块搬入 `ModNetwork` 即可），并**删除** `network/CardShopTransferListMessage.java`、`network/CardShopDenyMessage.java`；注册时类名写 `ModNetwork.CardShopTransferListMessage`。代价：两条 forge 内部类在 fabric 仍是内部类（与「一消息一文件」的风格不一致，但 `ModNetwork` 里本来就有 `AttachmentSyncMessage`/`DamageNumberMessage` 这类内部类，风格合法）。
- **方案 B（保独立文件）**：把上述 11 处 `ModNetwork.CardShopTransferListMessage` / `ModNetwork.CardShopDenyMessage` 改成裸类名，并在 `CardShopClientState` 加 `import com.merlinkitsune.astral_dice.network.CardShopTransferListMessage;`；`CardShopMenu:609/:990` 的 `new ModNetwork.CardShopTransferListMessage(...)` → `new CardShopTransferListMessage(...)`。代价：要动 3 个他人未提交文件（11 行）。

### 4.3 本批 scope 之外但仍缺的两条 C→S 消息（编译仍会红）
`screen/CardShopScreen.java:1526 / :1602 / :1700` 引用 `ModNetwork.CardShopBuyMessage(int,int)` 与 `ModNetwork.CardShopTransferMessage(int,int)`，**不在本批任务书内**（本批只列了 2 条 S→C），目前 fabric `network/` 下也没有。forge 蓝本（`forge-1.20.1/.../ModNetwork.java:703-731`、`:745-773`）里两者落点分别是：

- `CardShopTransferMessage(int targetIndex, int amount)` → `((shop.CardShopMenu) ctx.getSender().containerMenu).handleTransfer(serverPlayer, targetIndex, amount)`
- `CardShopBuyMessage(int shelfIndex, int targetSlot)` → 同上 `handleBuy(serverPlayer, shelfIndex, targetSlot)`

两者目标方法在 fabric 侧都已存在：`shop/CardShopMenu.java:621 handleTransfer(ServerPlayer,int,int)`、`:470 handleBuy(ServerPlayer,int,int)`。**建议**：把 4 条一次性按 forge 相对顺序追加（16=list、17=transfer、18=buy、19=deny），这样相对顺序与 forge 一致；若保持本批 16/17 = list/deny，则后两条拿 18/19，相对顺序与 forge 不同（功能无影响，仅偏离注释里的排序纪律）。

## 5. 无法判定 / 风险

1. **编译未验证**：未跑 gradle（硬约束 3）。已做静态守门（编码 / 行尾 / 括号配平 / 禁用 import / 符号存在性），但不能替代 javac。
2. `client/CardShopClientState.java` 为并发批次新文件（本批未改），其 `Entry` 类型引用正是 4.2 冲突点；本批按 forge 签名调用 `setTransferCandidates(List)` / `notifyDeny(int)`，**方法名与参数个数一致**，仅泛型实参的类归属待裁定。
3. `ModAttachments` 的 `SHOP_*` 键与并发批次的 `FLASHLIGHT_BURST_TARGETS` 等追加块相邻（同一文件多批并发写）：本批用**字节级追加**落线，未重排任何既有行；落线后全文扫过 4 个新键各 1 处声明，无重复定义。
4. `.sync()` 的实际同步行为（`AttachmentSyncMessage` 快照时机）沿用既有 `syncedKeys()` 机制，本批只做「入列」，未改同步实现。
