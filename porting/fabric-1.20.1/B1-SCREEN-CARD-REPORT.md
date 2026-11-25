# B1-SCREEN-CARD 移植报告（基线 forge-1.20.1 → 目标 fabric-1.20.1）

批次：B1-SCREEN-CARD（卡牌商店 GUI + 客户端状态，本仓最大单个 UI 文件 2151 行）。
仓库：`D:/MCProject/astral_dice_multiloader`，目标线 `fabric-1.20.1`（MC 1.20.1 + Fabric API 0.92.12 + Loom，官方 Mojang 映射，Java 17）。

## 0. 结论

两个文件已落盘并**逐段等价**：`screen/CardShopScreen.java` 与基线 `diff` 只差 **2 处平台适配 + 2 行 import**（§3），其余 2147 行与 forge 基线逐字节相同；`client/CardShopClientState.java` **与基线逐字节相同**（diff 空）。
守门：`net.minecraftforge.*` / `net.neoforged.*` / `trinkets` / `accessories` / `wispforest` 引用 **0 命中**；UTF-8 无 BOM、CRLF（2173 行 → CRLF 2173 / 裸 LF 0 / 双 CR 0）、无 U+FFFD。
按硬约束 3，本批**未运行 gradle、未启动游戏**；已用 `javac`（非 gradle，只读编译校验）证明两个文件本体零语法/语义错误——最终复验的 5 个 `error` 全部来自**本批之外**的 `LinglingSignManager` 缺口（§5.3），无一条属本两个文件的自身问题。

## 1. 交付清单

| 目标文件（`fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/`） | 基线 | 字节 | 行 | 与基线差异 |
|---|---|---|---|---|
| `screen/CardShopScreen.java` | `forge-1.20.1/…/screen/CardShopScreen.java` | 133607 → 135326 | 2151 → 2174 | 2 处语义适配（+22 行注释与助手方法）、+2 行 import |
| `client/CardShopClientState.java` | `forge-1.20.1/…/client/CardShopClientState.java` | 3411 | 76 | **无**（逐字节相同） |

## 2. 1:1 要点（实测值，一个都没改）

### 2.1 整屏几何与货架

- 整屏 `GUI_WIDTH=288` / `GUI_HEIGHT=187`；`titleLabelX/Y`、`inventoryLabelX/Y` 一律 −10000（标题与物品栏标签挪出屏幕）。
- 货架格：`FRAME_X0=7` / `FRAME_PITCH=46` / `FRAME_Y=28` / `FRAME_W=44` / `FRAME_H=61`；卡面 `FACE_SIZE=38`、内缩 `FACE_X_OFF=3` / `FACE_Y_OFF=3`。
- 名字框 `NAME_BOX_X0=3` / `NAME_BOX_W=38` / `NAME_BOX_Y0=43` / `NAME_BOX_H=16`；促销角标 `PROMO_W=17` / `PROMO_H=14`。
- 两个主按钮 32×32：兑换 `EXCHANGE_X=11` / `EXCHANGE_Y=109`；转账 `TRANSFER_X=48` / `TRANSFER_Y=109`。
- 钱包行 `BALANCE_X=11` / `BALANCE_Y=145`；物品栏格网 `GRID_X=114` / `GRID_Y=99` / `GRID_W=166` / `GRID_H=80` / `GRID_PITCH=18` / `CELL_SIZE=16`。
- 转账面板 `TP_PANEL_W=141` / `TP_PANEL_H=183` / `TP_SCALE=1` / `TP_X=(288−141)/2` / `TP_Y=(187−183)/2`；列表 `TP_ROWS=8` / `TP_ROW_H=18`；限额档 `LIMITS={5,10,15,20}`。
- 类内 `static {}` 自检块保留（格网/面板越界即 `IllegalStateException`，判据未改）。

### 2.2 颜色与交互阈值

- `COLOR_ROW_HOVER=0x66AAAAAA`、`COLOR_GOLD=0xFFFFD700`、`COLOR_GREEN=0xFF55FF55`、`COLOR_RED=0xFFFF5555`、`COLOR_GRAY=0xFFB0B0B0`、`COLOR_WHITE=0xFFFFFFFF`。
- `DOUBLE_CLICK_MS=280L`（双击判定窗口）、`DRAG_THRESHOLD=4.0D`（起拖位移阈值，:1570 判定 `dx*dx + dy*dy >= DRAG_THRESHOLD*DRAG_THRESHOLD`）。
- 名字 marquee `TP_NAME_HOLD_TICKS` / `TP_NAME_STEP_TICKS` 原值保留；星币小图标子区 `TP_COIN_U` / `TP_COIN_SRC` / `TP_COIN_SIZE` 原值保留。
- 静态常量总数 **123**（与基线逐一对应，未增删任何一个；本批新增的 `effectCardBarricade()` 是方法不是常量）。

### 2.3 三个发包出口（行号按落盘后文件）

- :1524-1526 双击快速移入 → `ModNetwork.sendToServer(new ModNetwork.CardShopBuyMessage(frame, CardShopMenu.QUICK_MOVE_SLOT))`。
- :1600-1602 拖拽落点购买 → `new ModNetwork.CardShopBuyMessage(index, invIndex)`。
- :1699-1700 转账确认 → `new ModNetwork.CardShopTransferMessage(original, transferAmount)`（`original` = 目标索引 `int`）。

### 2.4 文案键（15 个，原样未改）

`gui.astral_dice.card_shop`、`…card_shop.discount`、`…card_shop.exchange_hint`、`…card_shop.price`、`…card_shop.transfer_back`、`…card_shop.transfer_confirm_hint`、`…card_shop.transfer_empty`、`…card_shop.transfer_hint`、`…card_shop.transfer_no_match`、`…card_shop.transfer_refresh`、`…card_shop.transfer_search_hint`、`…card_shop.transfer_select_hint`、`msg.astral_dice.card_shop_no_coin`、`msg.astral_dice.card_shop_slot_occupied`、`msg.astral_dice.card_shop_transfer_used`。

### 2.5 `CardShopClientState`（逐字节相同，接口契约）

`DENY_BLINK_TICKS=30`；`denyBlinkRed()` = `denyTicks > 0 && ((denyTicks - 1) / 5) % 2 == 1`；
本批要求的两条接口均在位：`public static void setTransferCandidates(List<ModNetwork.CardShopTransferListMessage.Entry>)`（内部 `List.copyOf`）、`public static void notifyDeny(int reason)`；另含 `tickDeny()` / `denyTicks()` / `denyReason()` / `transferCandidates()` / `clear()`。

## 3. 平台差异（本批唯一改动点，2 处）

**改动 1（:682 判定 + :698-712 新助手方法）** —— 卡牌「路障 ⇒ 强化拒止」的视觉替换。

- 原：`!stack.is(ModItems.EFFECT_CARD_BARRICADE.get())` → 现：`!stack.is(effectCardBarricade())`；新增 `import net.minecraft.world.item.Item` / `import net.minecraft.world.item.Items`。
- 助手：`private static Item effectCardBarricade() { return BuiltInRegistries.ITEM.getOptional(new ResourceLocation(AstralDiceMod.MODID, "effect_card_barricade")).orElse(Items.AIR); }`
- 理由：`ModItems.EFFECT_CARD_BARRICADE` 在 fabric 线 `item/ModItems.java` **尚不存在**（物品批次缺口），硬引用会让整条线编译不过。改走注册表按 id 延迟解析，与 `shop/CardShopCatalog.java:34/80/107` 的 `byItemId` 先例同口径。物品缺失期退化为 `minecraft:air` ⇒ `stack.is(air)` 恒 false ⇒ `displayStack` 原样返回，等价于「没有该物品就没有这条替换」；**物品批次实装后本类无需再改**（注册 id 相同即自动生效）。

**改动 2（:1641-1647）** —— 同类未满栈判定。

- 原：`candidate.getCount() < candidate.getItem().getMaxStackSize(candidate)` → 现：`candidate.getCount() < candidate.getMaxStackSize()`。
- 理由（javap 实证）：fabric MC jar 的 `net.minecraft.world.item.Item` 只有 `public final int getMaxStackSize()`（无参）；`getMaxStackSize(ItemStack)` 是 Forge 补丁方法。本线等价物 = 自有契约 `platform/item/StackCountOverrideItem#maxStackSize(ItemStack)` + `mixin/ItemStackMaxCountMixin`（`@Inject(method="getMaxStackSize", at=@At("HEAD"), cancellable=true)`，物品实现该接口时用 `override.maxStackSize(self)` 覆盖返回值）。Forge 的 `IForgeItem` 默认实现即回落到无参版本 ⇒ **栈级方法语义完全等价**；`item/card/CardItem.java` 在 fabric 已按该契约实现（满耐久 16 / 已消耗 1）。

其余全部照搬、**未做任何其它改写**。已逐一核对基线用到的每条原版调用在 fabric MC jar 里同签名存在（`GuiGraphics#blit(ResourceLocation,int,int,int,float,float,int,int,int,int)`、`drawString(Font,String,int,int,int,boolean)`、`renderTooltip(Font,List<? extends FormattedCharSequence>,int,int)`、`renderComponentTooltip(Font,List<Component>,int,int)`、`AbstractContainerScreen#renderSlotHighlight`）；且 fabric 侧 `Screen#minecraft/font`、`AbstractContainerScreen#imageWidth/imageHeight/leftPos/topPos/hoveredSlot` 可见性只会**更宽**（public），不存在访问收紧。

## 4. 缺失资源清单（贴图 / 模型）

**结论：本屏 0 缺失。** 逐项实测（`assets/astral_dice/textures/gui` 两线全树 diff：forge 242 文件 / fabric 244 文件）：

- `tex(name)` 前缀 `astral_dice:textures/gui/shop/` 下被引用的 14 个文件，fabric **全部存在**：
  `card_shop_inventory_bg.png`、`shop_item_inventory.png`、`shop_title.png`、`shop_card_soldout.png`、`shop_promotion.png`、`shop_wallet.png`、`shop_transfer_bg.png`、`shop_transfer_search.png`、`shop_transfer_button_back.png`、`shop_transfer_limit.png`、`star_coin_small.png`、`shop_transfer_select.png`、`shop_transfer_slide_rail.png`、`shop_transfer_slide_button.png`。
- 卡面 `textures/gui/shop/card/<物品注册名>.png`（+ `_discount.png` 减费版）：两线 `shop/card/` 均 **25 个文件，文件名集合完全相同**，无缺口。
- 两线 `textures/gui` 仅有的差异与本批无关：fabric 多 `slot/empty_chip_slot.png`、`slot/empty_dice_slot.png`、`slot/empty_stand_slot.png`；forge 多 `book_pink.png`。**无需补图**。
- 模型 JSON：本屏为纯 GUI（不渲染方块/物品模型），无模型依赖。
- 缺素材时本类自带占位逻辑（`faceExists` 判定 + 半透明底 + 问号，:734 起），不会崩。

## 5. 需主 Agent 接线处

### 5.1 【必须】客户端界面注册 —— `client/ModClientEvents.java`

- 位置：`onClientSetup(FMLClientSetupEvent)` 的 `event.enqueueWork(() -> { … })` 块内，**在 :62（`ModMenuTypes.CHIP_SHOP.get(), ChipShopScreen::new);`）之后**追加：

```java
            net.minecraft.client.gui.screens.MenuScreens.register(
                    ModMenuTypes.CARD_SHOP.get(), CardShopScreen::new);
```

- 同文件 import 区（:6 `import …screen.ChipShopScreen;` 之前）追加一行：

```java
import com.merlinkitsune.astral_dice.screen.CardShopScreen;
```

- 依据：forge 对照位于 `forge-1.20.1/…/client/ModClientEvents.java:99-100`；fabric 现仅注册了 `CARD_INVENTORY` / `CHIP_SHOP`，**未注册 `CARD_SHOP`** ⇒ 若不补，服务端 `openMenu` 打开商店时客户端拿不到 Screen。

### 5.2 `screen/ModMenuTypes.java` —— **无需追加**

`ModMenuTypes.CARD_SHOP` 在 fabric 线**已存在**：:22-25

```java
    /** 卡牌商店菜单（逻辑批已完整实现；见 shop/CardShopMenu）。 */
    public static final RegistryObject<MenuType<com.merlinkitsune.astral_dice.shop.CardShopMenu>> CARD_SHOP =
            MENUS.register("card_shop",
                    () -> new MenuType<>(com.merlinkitsune.astral_dice.shop.CardShopMenu::new, FeatureFlags.DEFAULT_FLAGS));
```

类型与本屏签名匹配（`CardShopScreen extends AbstractContainerScreen<CardShopMenu>`，构造 `(CardShopMenu, Inventory, Component)`），**不要改该文件**。

### 5.3 【硬前置·不在本批 scope】`item/sign/LinglingSignManager`

本屏 :7（import）、:684（`isEquipped`）、:685（`displayAsRepulsion`）、:1623（`resolvePlacedItem`）四处引用它；该类在整个 fabric 线**尚不存在**（立牌批次未落线）。同批次 `shop/CardShopMenu.java:489` 与 `network/ModNetwork.java:595` 也硬引用它 ⇒ 属全批次共同前置。
forge 侧契约（`forge-1.20.1/…/item/sign/LinglingSignManager.java`）：`static ItemStack resolvePlacedItem(ItemStack shelfStack, Player player)`、`static boolean isEquipped(Player)`、`static ItemStack displayAsRepulsion(ItemStack)`、`static boolean hasSign(Player)`。本批**按 forge 签名直接调用、未写桩、未留 TODO**。

### 5.4 【同批但不在本批 scope 的真缺陷】`shop/CardShopMenu.java:985`

```java
        return stack.getItem().getMaxStackSize(stack);
```

fabric 编译报 `method getMaxStackSize in class Item cannot be applied to given types; required: no arguments`。修法与本批 §3 改动 2 完全相同：改成 `stack.getMaxStackSize()`（`return stack.getMaxStackSize();`）。**未代改**（非本批 scope）；最终复验时该行已被并行批次修好，当前不再报错。

### 5.5 最终编译复验（落盘后最后一次 `javac`，非 gradle）

输入 = 本批两个文件，`-sourcepath` 指向 fabric 线全源码树：**5 个 error，全部是 `cannot find symbol` 指向 `LinglingSignManager`** —— `CardShopScreen.java:7`（import）、`:684`、`:685`、`:1623`，以及 `shop/CardShopMenu.java:489`（非本批文件）。除该族外**零报错**，即本批两个文件在依赖齐备时完全干净。附带证据：编译期曾用 `(ItemStack,int)` 桩试 `CardShopTransferMessage` 报「int cannot be converted to ItemStack」，反证 :1700 的 `(int,int)` 调用与消息类实签名一致。

## 6. 「拖拽购买三处共用转换逻辑」在本文件的落点

主题：佩戴「怪力乱神」立牌时，货架「路障卡」落到背包后应变成「强化拒止」，客户端判定/展示与服务端结算必须共用**同一份**转换逻辑（`LinglingSignManager#resolvePlacedItem`），不得复制第二份。

1. **客户端拖拽受理判定** —— `CardShopScreen.java:1620-1636` `private boolean canAcceptDrop(int shelfIndex, int invIndex)`；转换落点 **:1622-1625**（注释「与服务端 handleBuy 共用 … 不复制第二份转换逻辑」+ `ItemStack incoming = LinglingSignManager.resolvePlacedItem(this.menu.getSlot(shelfIndex).getItem(), this.minecraft == null ? null : this.minecraft.player);`）。后续 `sameCard(target, incoming)` / `hasMergeableStack(incoming)` 全部用**转换后**的 `incoming`（:1632 / :1635）。
2. **货架展示层** —— `CardShopScreen.java:679-686` `private ItemStack displayStack(ItemStack stack)`：:682 路障判定（本批改为 `effectCardBarricade()`）→ :684 `LinglingSignManager.isEquipped(this.minecraft.player)` → :685 `return LinglingSignManager.displayAsRepulsion(stack);`。
3. **服务端结算**（不在本文件，交叉引用）—— `shop/CardShopMenu.java:486` 注释 + `:490` `LinglingSignManager.resolvePlacedItem(card, buyer).copy()`。

⇒ 本文件内的落点即 **:1622-1625（判定）与 :679-686（展示）**；第 3 处落在 `shop/CardShopMenu.java`。

## 7. 无法判定项

1. **`LinglingSignManager` 的实际 fabric 实现**（立牌批次）：本批只保证调用点与 forge 签名一致；若该批次改动签名的参数类型（例如 `resolvePlacedItem` 第二参改成 `LocalPlayer`），需同步本屏 :1623。
2. **`ModNetwork.CardShop*Message` 的最终构造签名**：本批按 forge 调用（`CardShopBuyMessage(int,int)` / `CardShopTransferMessage(int,int)` / `CardShopTransferListMessage(List<Entry>)`，`Entry(UUID,String,long)`）。落盘时 fabric `ModNetwork.java` 已注册 4 条 `registerMessage`（:83-90），但消息体由网络批次定稿；若其 `CardShopTransferMessage` 改为 `(int,int)` 以外形态需同步 :1700。编译校验时曾用 `(ItemStack,int)` 桩试过，报「int cannot be converted to ItemStack」，反证当前代码与 `(int,int)` 一致。
3. **`gui.astral_dice.card_shop*` 15 个文案键的 lang 覆盖**：属 lang/资源批次，本批未改语言文件；若 fabric 线 `zh_cn.json` / `en_us.json` 缺键，界面会显示原始键名（不影响本文件等价性）。
4. **运行期观感**（字体度量、tooltip 换行、`ForgeGui` 相关 HUD 叠加）：本批不启动游戏，按硬约束 3 未做实机验证，交由主 Agent 统一编译与实机确认。
5. **`CARD_CHOICE` 界面亦未在 `ModClientEvents` 注册**（forge `:103-104` 有），`screen/CardChoiceScreen.java` 在 fabric 已存在 —— 属 `CARD_CHOICE` 批次，一并提示主 Agent 接线时不要漏。
