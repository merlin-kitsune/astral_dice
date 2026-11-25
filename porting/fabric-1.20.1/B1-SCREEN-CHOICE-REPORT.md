# B1-SCREEN-CHOICE 移植报告（基线 forge-1.20.1 → 目标 fabric-1.20.1）

批次：B1-SCREEN-CHOICE（选卡界面 GUI + 选卡状态 + 选卡网络消息）。
仓库：`D:/MCProject/astral_dice_multiloader`，目标线 `fabric-1.20.1`（MC 1.20.1 + Fabric API 0.92.12 + Loom，官方 Mojang 映射，Java 17）。

## 0. 结论

7 个文件已落盘；其中 **3 个 GUI/客户端文件与 forge 基线逐字节相同**（`cmp` 全等，0 处差异），
4 个平台相关文件按本线垫片改写（diff 只在 import / 打开菜单 / 类头 javadoc / 标签取用四处）。
`net.minecraftforge.*` / `net.neoforged.*` / `trinkets` / `wispforest` / `accessories` 引用 **0 命中**（含注释）。
按硬约束 3，本批**未运行 gradle、未启动游戏**，编译由主 Agent 统一做。

⚠️ 三种东西仍需主 Agent 接线才能编译/显示（§4）：`ModMenuTypes.CARD_CHOICE`（**编译阻塞**）、
`MenuScreens.register`（客户端屏幕工厂）、`LoaderBus.register(CardChoiceManager.class)`（死亡/掉线兜底）、
两个网络消息 id 20/21；另加 6 键 × 3 语 lang（§3.2，缺键只显示裸键名，不阻塞编译）。

## 1. 交付清单

| 目标文件（`fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/`） | 基线 | 字节 | 行 | 与基线差异 |
|---|---|---|---|---|
| `screen/CardChoiceMenu.java` | `forge-1.20.1/…/screen/CardChoiceMenu.java` | 12254 | 267 | **0**（逐字节相同） |
| `screen/CardChoiceScreen.java` | `forge-1.20.1/…/screen/CardChoiceScreen.java` | 28496 | 591 | **0**（逐字节相同） |
| `client/CardChoiceClientHandler.java` | `forge-1.20.1/…/client/CardChoiceClientHandler.java` | 963 | 24 | **0**（逐字节相同） |
| `choice/CardChoiceType.java` | `forge-1.20.1/…/choice/CardChoiceType.java` | 3234 | 68 | 基 1900B/49L；+19L：本地 TagKey 对 + 平台 javadoc |
| `choice/CardChoiceManager.java` | `forge-1.20.1/…/choice/CardChoiceManager.java` | 15829 | 302 | 基 13246B/257L；+45L：去掉 `@Mod.EventBusSubscriber`、新增 `ChoiceMenuFactory` 内部类 |
| `network/CardChoiceConfirmMessage.java` | `forge-1.20.1/…/network/CardChoiceConfirmMessage.java` | 1835 | 45 | 基 1427B/39L；仅 import 与 javadoc |
| `network/CardChoiceCloseMessage.java` | `forge-1.20.1/…/network/CardChoiceCloseMessage.java` | 2060 | 46 | 基 1441B/39L；仅 import 与 javadoc |

7 个文件均 **UTF-8 无 BOM、CRLF 行尾、裸 LF 0 处、双 CR 0 处**（逐文件字节校验）。

## 2. 1:1 要点（一个都不能改的实测值）

### 2.1 槽位几何（`CardChoiceMenu`，`public` 常量原样）

- 隐藏卡槽坐标 `HIDDEN_X = -10000` / `HIDDEN_Y = -10000`（卡槽不参与绘制，靠 `CardChoiceScreen` 自绘）。
- 玩家物品栏 `INVENTORY_X = 8`、`INVENTORY_Y = 119`、`INVENTORY_HOTBAR_Y = 177`；4 行 ×9 列，步距 18。
- 槽位顺序：先 `slotCount` 个 `ChoiceCardSlot`（`index 0..slotCount-1`），再 36 个玩家槽（`slotCount..slotCount+35`）。
- `ChoiceCardSlot`：`mayPlace = type.matches(stack)`；`mayPickup` **恒 true**（返回 false 会让 `QUICK_MOVE` 静默失败）；`getMaxStackSize = 1`。
- `quickMoveStack` 一次只搬 **1 张**进卡槽，并 `return ItemStack.EMPTY`（返回副本会让原版 `doClick` 把每个空卡槽都补满 —— 注释里记的实测 `put_filled=3`）。
- `consumeSelection` 先**整槽校验**（任一空 / 任一不匹配 ⇒ 返回 `List.of()`，零消耗），再逐槽 `copyWithCount(1)` + 非创造模式 `shrink(1)`。
- `removed(Player)`：死亡/掉线（`!player.isAlive()` 或 `hasDisconnected()`）⇒ 卡牌掉落死亡点 + 掉落手上物品；否则 `placeItemBackInInventory`；未确认则 `CardChoiceManager.abandon`。

### 2.2 整屏与卡框几何（`CardChoiceScreen`）

- 整屏 `GUI_WIDTH = 176` / `GUI_HEIGHT = 200`；标题基线 `TITLE_Y = 8`（`renderLabels` 居中于 `imageWidth/2`，色 `0xFFFFFFFF`）。
- 卡框 `CARD_Y = 24`、`FRAME_W = 44`、`FRAME_H = 61`、`FRAME_PITCH = 46`；`cardX(i) = (176 - (44 + 46×(n−1)))/2 + 46×i` ⇒ 单卡 `x=66`、三卡 `x=20/66/112`（整体居中，未改）。
- 卡面 `FACE_SIZE = 38`，源图 64×64 等比缩放（`FACE_SIZE / 64f`）；`FACE_X_OFF = 3`、`FACE_Y_OFF = 3`。
- 名牌框 `NAME_BOX_X0 = 3` / `NAME_BOX_W = 38` / `NAME_BOX_Y0 = 43` / `NAME_BOX_H = 16`；文字居中 `NAME_CENTER_X_OFF = 22`、`NAME_Y_OFF = 47`。
- 跑马灯 `NAME_HOLD_TICKS = 20` / `NAME_STEP_TICKS = 2`；`containerTick()` 里 `nameMarqueeTick++` 且拖拽中失焦即 `cancelDrag()`。
- 物品栏网格 `GRID_X = 5` / `GRID_Y = 116` / `GRID_W = 166` / `GRID_H = 80`（正好盖住 §2.1 的 4×9 槽位）。
- 确认按钮 `CONFIRM_X = (176-51)/2 = 62` / `CONFIRM_Y = 90` / `CONFIRM_W = 51` / `CONFIRM_H = 22`；**仅 `menu.isComplete()` 时绘制**，悬停切高亮贴图。
- 交互阈值：`DOUBLE_CLICK_MS = 280L`、`DRAG_THRESHOLD = 4.0D`；颜色 `COLOR_WHITE = 0xFFFFFFFF`、`COLOR_GRAY = 0xFFB0B0B0`；缺卡面兜底 `0x66000000` 底 + 居中 `?`（灰）。
- 单击只记录 `pressedSlot/pressX/pressY`；Shift+单击与 280ms 内同槽二击走 `ClickType.QUICK_MOVE`；确认按钮左键才发包（`CardChoiceConfirmMessage(menu.token())`）。
- 手动 tooltip：`super.render` 之后对「鼠标下的隐藏卡槽」调 `guiGraphics.renderTooltip(font, stack, mouseX, mouseY)`。

### 2.3 文案键（原样，未改）

`CardChoiceType#titleKey` 生成 `gui.astral_dice.card_choice.title_{single|triple}_{combat|effect|any}` 6 键；
卡面名走 `item.<ns>.<path>.short`，缺则该键回落 `stack.getHoverName()`（两线 `.short` 键均 0 条 ⇒ 两线同为回落，行为一致）。

### 2.4 服务端权威（`CardChoiceManager`）

- 单玩家最多 1 个未结算会话；`start` 被拒口径：`bad_slot_count`（非 1/3）、`already_open`、`open_failed`（含 `menu=` 回读不符）。
- `confirm` 三重校验：会话存在且 token 符 → `containerMenu` 是本 token 的 `CardChoiceMenu` → 槽位填满；任一不过 ⇒ 只记日志、零消耗。
- 成功顺序（逐字保留）：`sendToPlayer(CardChoiceCloseMessage)` → `player.closeContainer()` → `handler.accept(List.copyOf(picked))`；handler 抛异常只记 `handler_failed`。
- 兜底：`LivingDeathEvent`（`EventPriority.LOWEST`，被取消即不动）+ `PlayerEvent.PlayerLoggedOutEvent`；日志前缀 `AP_CARD_CHOICE:` 全量保留（主 Agent 的守门口径可直接沿用）。

### 2.5 平台差异（本批全部改动点）

1. **打开菜单**：forge `NetworkHooks.openScreen(player, SimpleMenuProvider, buf -> {3×writeVarInt})` → 本线 `player.openMenu(new ChoiceMenuFactory(token, slotCount, type))`，
   `ChoiceMenuFactory implements net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory`，`writeScreenOpeningData(ServerPlayer, FriendlyByteBuf)` 写入
   **同序同义**的三个 VarInt（token → slotCount → type.ordinal()）。⇒ 客户端 `CardChoiceMenu(int, Inventory, FriendlyByteBuf)` 构造器与 forge **完全相同**，两张 GUI 文件因此 0 改动。
2. **事件订阅**：删 `@Mod.EventBusSubscriber(modid = …)`（本线无自动注册）⇒ 改 `LoaderBus.INSTANCE.register(CardChoiceManager.class)`（显式登记，见 §4.4）。
   `LoaderBus#scan` 只扫**该类自身**的 `@SubscribeEvent` 方法（`LoaderBus.java:136-149`），不递归内部类 —— 与本线约束一致。
3. **消息**：`platform/network/NetworkEvent` 替 forge `NetworkEvent`；S2C 关包用 `platform/api/distmarker/Dist` + `platform/fml/loading/FMLEnvironment` 守客户端分支（同 `BeamFxPayload` 写法），客户端类只在 CLIENT 分支解析。
4. **卡牌类型判定**：forge 委托 `ModItems.isCombatCard/isEffectCard`；本线 `ModItems` 的 `COMBAT_CARDS_TAG/EFFECT_CARDS_TAG`（`item/ModItems.java:120-125`）是 private 且无包装方法，
   ⇒ `CardChoiceType` 就地持有同 id 的 `TagKey.create(Registries.ITEM, new ResourceLocation(AstralDiceMod.MODID, "combat_cards"|"effect_cards"))`；`ANY` 仍走 `ModItems.isCardItem(stack)`（`item/ModItems.java:1096-1098`）。判定结果与 forge 逐项等价。

## 3. 缺失资源清单

### 3.1 贴图 —— **零缺失**

`assets/astral_dice/textures/gui/shop/` 两线文件名逐项 `diff` 为空（各 66 个 PNG）。本界面用到的 4 张全在：
`shop_card.png`（卡框）、`shop_item_inventory.png`（物品栏网格）、`fanny_skill_confirm.png` / `fanny_skill_confirm_highlight.png`（确认按钮常态/高亮）。
卡面目录 `textures/gui/shop/card/` 两线均 **25 张**，`diff` 为空 ⇒ 无新增缺口、**无需补图**。

### 3.2 语言键 —— 6 键 × 3 语，fabric 侧 **0 条**（forge 基线齐全）

| 键 | en_us（forge 1066-1071） | zh_cn（forge 1067-1072） | ja_jp（forge 1065-1070） |
|---|---|---|---|
| `gui.astral_dice.card_choice.title_single_combat` | Choose a Combat Card | 选择一张战斗牌 | 戦闘カードを1枚選択 |
| `gui.astral_dice.card_choice.title_single_effect` | Choose an Effect Card | 选择一张效果牌 | 効果カードを1枚選択 |
| `gui.astral_dice.card_choice.title_single_any` | Choose a Card | 选择一张卡牌 | カードを1枚選択 |
| `gui.astral_dice.card_choice.title_triple_combat` | Choose Three Combat Cards | 选择三张战斗牌 | 戦闘カードを3枚選択 |
| `gui.astral_dice.card_choice.title_triple_effect` | Choose Three Effect Cards | 选择三张效果牌 | 効果カードを3枚選択 |
| `gui.astral_dice.card_choice.title_triple_any` | Choose Three Cards | 选择三张卡牌 | カードを3枚選択 |

（`lang/*.json` 属共享资源，可能是并行批次的写入点 ⇒ 本批**不擅自改**，整段 JSON 由主 Agent 粘入；缺键只显示裸键名、不阻塞编译。）

## 4. 需主 Agent 接线（确切语句 + 插入位置）

### 4.1 `screen/ModMenuTypes.java` —— 追加 `CARD_CHOICE`（**当前 fabric 侧唯一编译阻塞点**）

`screen/CardChoiceMenu.java:60` 已用 `super(ModMenuTypes.CARD_CHOICE.get(), containerId)`，该常量尚不存在。
插入位置：第 30 行（`CHIP_SHOP` 注册块末行 `() -> new MenuType<>(com.merlinkitsune.astral_dice.shop.ChipShopMenu::new, FeatureFlags.DEFAULT_FLAGS));`）之后、第 32 行 `/** 服务端打开卡牌栏… */` 之前（当前第 31 行空行处）：

```java
    /** 选卡界面（B1-SCREEN-CHOICE）：带 extra data，必须用 Fabric 的 ExtendedScreenHandlerType。 */
    public static final RegistryObject<MenuType<CardChoiceMenu>> CARD_CHOICE = MENU_TYPES.register("card_choice",
            () -> new net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType<CardChoiceMenu>(
                    CardChoiceMenu::new));
```

依据：`platform/registry/DeferredRegister.java:70` 为 `<I extends T> RegistryObject<I> register(String, Supplier<? extends I>)`，`T = MenuType<?>`，
`ExtendedScreenHandlerType<CardChoiceMenu> extends MenuType<CardChoiceMenu>` ⇒ 返回类型 `RegistryObject<MenuType<CardChoiceMenu>>` 成立；
`CardChoiceMenu::new` 命中 `ExtendedScreenHandlerType.ExtendedFactory#create(int, Inventory, FriendlyByteBuf)`，即**已有的** 3 参构造器，无需改 GUI 文件。
（`CardChoiceMenu` 与本文件同包，不需要 import。）

### 4.2 `client/ModClientEvents.java` —— 注册屏幕工厂（客户端，**必需**）

Fabric 的 `ClientNetworking` 用 `MenuScreens.getScreenFactory(...)` 找屏幕，未注册会打 `Screen not registered for screen handler {}!` 并**什么都不做**。
位置：`onClientSetup`（57-64 行）的 `enqueueWork` 内，第 62 行 `CHIP_SHOP` 注册之后；import 区第 6 行 `import …screen.ChipShopScreen;` 之后加 `import com.merlinkitsune.astral_dice.screen.CardChoiceScreen;`。

```java
            net.minecraft.client.gui.screens.MenuScreens.register(
                    ModMenuTypes.CARD_CHOICE.get(), CardChoiceScreen::new);
```

### 4.3 `network/ModNetwork.java` —— 两个消息（本线 id **20/21**，对应 forge id 24/25）

位置：`register()` 内第 91 行（`CardShopDenyMessage` 注册块末行）之后、第 92 行 `}` 之前。同包，无需 import。

```java
        // 选卡界面（B1-SCREEN-CHOICE，基线 forge-1.20.1 同文件 id 24/25，此处 append 成 20/21）：
        // 20 = C→S 确认已选卡牌、21 = S→C 带 token 关闭界面。顺序即协议，一律追加在末尾。
        CHANNEL.registerMessage(id++, CardChoiceConfirmMessage.class,
                CardChoiceConfirmMessage::encode, CardChoiceConfirmMessage::decode, CardChoiceConfirmMessage::handle);
        CHANNEL.registerMessage(id++, CardChoiceCloseMessage.class,
                CardChoiceCloseMessage::encode, CardChoiceCloseMessage::decode, CardChoiceCloseMessage::handle);
```

（当前已占用：0-11 既有、12-15 筹码商店/光束、16-19 卡牌商店 ⇒ 本批取 20/21，与 forge 的 24→25 相对顺序一致。）

### 4.4 `AstralDiceMod.java` —— 显式登记监听器（**必需**，否则死亡/掉线兜底静默失效）

位置：`registerListeners()` 内第 196 行 `LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.shop.CardShopCycleTicker.class);` 之后（紧随 B1-CARDSHOP 那段注释）：

```java
        // 选卡界面(B1-SCREEN-CHOICE):同上,forge 靠 @Mod.EventBusSubscriber 自动订阅;
        // 漏登记 = 死亡/掉线兜底不执行 ⇒ 未结算会话残留,该玩家此后 start() 恒判 already_open。
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.choice.CardChoiceManager.class);
```

### 4.5 已就绪、**不需要**再动

- `ModItems.isCardItem(ItemStack)` 已存在（`item/ModItems.java:1096-1098`）；`CardChoiceType` 的两个 TagKey 与 `ModItems` 私有字段同 id。
- `ModNetwork.sendToPlayer(ServerPlayer, Object)` / `sendToServer(Object)` 已在（`network/ModNetwork.java:96,115`），`CardChoiceManager` / `CardChoiceScreen` 调用点无需改。
- 贴图与卡面资源齐备（§3.1）。

## 5. 范围澄清：网络消息是 **2 个**，不是 6 个

forge 的 `network/CardChoice*` 只有 2 个：`CardChoiceConfirmMessage`（C2S，token）、`CardChoiceCloseMessage`（S2C，token）。
任务书里的「6 个」应是与 `EventChoice*` 混计 —— forge `network/EventChoice{Start,Confirm,Close,Tick,Cancel}Message` 共 5 个，属**事件选卡**子系统，
其服务端 `event/EventChoiceManager` 与客户端 `screen/EventChoiceScreen` 在 fabric 侧**尚不存在**（本线 `**/*Choice*.java` 仅本批 7 个文件），
且任务点名「39t 延迟结算 / 先判硬间隔再删会话」在 `EventChoiceManager` 里 ⇒ **本批不移植**，留给后续批次（届时再连 `EventChoice*` 5 个消息一起做）。

## 6. 无法判定项 / 风险

1. **未编译、未实机**（硬约束 3）。以下符号仅做静态存在性核对，无字节码证据：`GuiGraphics#renderTooltip(Font, ItemStack, int, int)`（同线 `mixin/bridge/ClientTooltipBridgeMixin.java` 以该方法为注入目标 ⇒ 1.20.1 存在）、`AbstractContainerScreen#containerTick()`（同线 `CardInventoryScreen` 在用）、`Language#has/getOrDefault`、`ItemStack#copyWithCount`、`Player#openMenu(MenuProvider)`。
2. **`ExtendedScreenHandlerFactory` 的打开路径依赖 fabric-api 的 `ServerPlayerEntityMixin`**：它只对 `instanceof ExtendedScreenHandlerFactory` 的 provider 改发 `fabric-screen-handler-api-v1:open_screen`；若 provider 不是 extended 而类型是 `ExtendedScreenHandlerType`，该 mixin 会抛 `IllegalArgumentException`。本批两者成对出现（`ChoiceMenuFactory` + §4.1 的 `ExtendedScreenHandlerType`）⇒ 一致；**主 Agent 若改其中一边，另一边必须同改**。
3. `CardChoiceScreen` 覆写的 `containerTick()` 声明为 `public`（基线如此，原版 `protected`），属合法加宽，本批保留。
4. 卡面名 `item.<ns>.<path>.short` 在两线均无对应 lang 键 ⇒ 卡名走 `getHoverName()`（与 forge 同表现，非本批缺陷）。
5. `ChoiceMenuFactory` 是 `private static final class`，不参与 `LoaderBus` 扫描（无 `@SubscribeEvent`），符合「不递归内部类」约束。
6. 日志行 `AP_CARD_CHOICE:` 全量保留；若主 Agent 的守门脚本按 `AP_CARD_CHOICE` grep，本线输出格式与 forge 一致（`open/confirm/abandon/death_cleanup/handler_failed/reject` 六类）。
