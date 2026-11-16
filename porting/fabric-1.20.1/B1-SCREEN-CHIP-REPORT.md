# B1-SCREEN-CHIP 移植报告（基线 forge-1.20.1 → 目标 fabric-1.20.1）

批次：B1-SCREEN-CHIP（筹码商店 GUI + 客户端状态）。
仓库：`F:/MCProject/astral_dice_multiloader`，目标线 `fabric-1.20.1`（MC 1.20.1 + Fabric API 0.92.12 + Loom，官方 Mojang 映射，Java 17）。

## 0. 结论

两个 Java 文件已落盘。**代码行与 forge 基线逐字节相同**（差异仅类头「平台差异」javadoc 段，见 §2.6）；
静态常量 73/73 全等（`ChipShopScreen` 68 + `ChipShopClientState` 5）；`net.minecraftforge.*` / `net.neoforged.*` / `wispforest` 引用 0 命中。
按硬约束 3，本批**未运行 gradle、未启动游戏**，编译由主 Agent 统一做。

## 1. 交付清单

| 目标文件（fabric-1.20.1/…/com/merlinkitsune/astral_dice/） | 基线 | 字节 | 行 | 与基线差异 |
|---|---|---|---|---|
| `screen/ChipShopScreen.java` | `forge-1.20.1/…/screen/ChipShopScreen.java` | 41680 | 722 | 仅 javadoc 平台段：−3 行 / +7 行 |
| `client/ChipShopClientState.java` | `forge-1.20.1/…/client/ChipShopClientState.java` | 12887 | 269 | 仅 javadoc 平台段：−3 行 / +4 行 |

两个文件均 UTF-8 无 BOM、CRLF 行尾、裸 LF 0 处、双 CR 0 处。

## 2. 1:1 要点（一个都不能改的实测值）

### 2.1 整屏与条带几何（`ChipShopScreen`）

- 整屏 `GUI_WIDTH=284` / `GUI_HEIGHT=183`；`titleLabelX/Y`、`inventoryLabelX/Y` 一律 −10000（标题与物品栏标签挪出屏幕）。
- 条带 `STRIP_X=34` / `STRIP_Y=6` / `STRIP_W=216` / `STRIP_H=78`；格相对 x `CELL_X={3,45,88,131,174}`，`CELL_W=40`。
- 两个按钮 24×24：确认 `CONFIRM_X=11,CONFIRM_Y=36`；刷新 `REFRESH_X=181,REFRESH_Y=36`。
- 三个筹码位 28×28：`QUALITY_X={51,94,137}`、`QUALITY_Y=31`；图标 24×24，内缩 `CHIP_ICON_OFF=(28−24)/2=2`，源素材 `CHIP_ICON_SRC_SIZE=32`。
- 标签框 42×15：`LABEL_X={1,44,87,130,172}`、`LABEL_Y=61`、`LABEL_TEXT_DY=4`。
- 物品栏面板 `PANEL_X=56,PANEL_Y=89,PANEL_W=172,PANEL_H=86`；格网 `SLOT_X0=61,SLOT_Y0=94,SLOT_PITCH=18,SLOT_COLS=9,SLOT_ROWS=3,HOTBAR_Y=152`。
- 类内 `static {}` 自检块保留：格网越出面板即抛 `IllegalStateException`（不改判据）。

### 2.2 命中面与交互（硬约束）

- 筹码格命中 = 「格 40×78 整列」∪「该格标签框 42×15」两处并集（`chipIndexAt`）；cell0/cell4 由更早分支截获。
- 单击筹码格**只** `ChipShopClientState.select(chip)`，不发包；无双击判定、无拖拽状态字段、无清选分支。
- 购买唯一出口 = 确认按钮 → `ModNetwork.sendToServer(new ChipShopBuyPayload(chip))`；未选中/空位直接 return。
- 刷新按钮：`remainingRefreshes() <= 0` ⇒ **只** `notifyDeny(ChipShopMenu.DENY_NO_REFRESH)` 不发包；否则发 `ChipShopRefreshPayload`。

### 2.3 颜色与动画相位

`COLOR_TEXT=0xFFFFFF`、`COLOR_EMPTY=0x808080`、`COLOR_DENY=0xFF5555`、`COLOR_SELECTED_NAME=0x000000`、`COLOR_HOVER_OVERLAY=0x66AAAAAA`；
`PRESS_TICKS=2`（悬停**不**切 push 贴图）；marquee `CHIP_NAME_HOLD_TICKS=20` / `CHIP_NAME_STEP_TICKS=2` / `CHIP_NAME_INNER_X=2` / `CHIP_NAME_INNER_W=38`；
星币小图标子区 `COST_ICON_SIZE=7`、`COST_ICON_U=3`、`COST_ICON_V=3`、`COST_ICON_SRC=13`、`COST_ICON_GAP=2`。

### 2.4 拒绝红闪（`ChipShopClientState`）

`DENY_BLINK_TICKS=30`、`DENY_NONE=-1`；`denyBlinkRed()` 逐字保留 `denyTicks > 0 && ((denyTicks - 1) / 5) % 2 == 1`（3 次红/白、每相 5t）。
花费文本认码 0/2（`DENY_NO_COIN` / `DENY_INVENTORY_FULL`）、刷新计数认码 1（`DENY_NO_REFRESH`）—— 判定链一字未改。

### 2.5 文案键（原样，未改）

`gui.astral_dice.chip_shop.cost`（`%s`）、`gui.astral_dice.chip_shop.empty`、`gui.astral_dice.chip_shop.refresh_counter`（`%s`）。

### 2.6 平台差异（本批唯一改动点，均在 javadoc 内，不影响字节码语义）

1. 发包：`ModNetwork.sendToServer(Object)`（本线 `platform/network/SimpleChannel` 垫片，与 forge 同形）—— 代码行与基线**逐字相同**，无需改写法。
2. 贴图定位：`new ResourceLocation(AstralDiceMod.MODID, "textures/gui/shop/" + name)`（1.20.1 两线同一写法）。
3. 品质档位反查：`AstralRarities.tierOf(stack.getRarity())` + `com.merlinkitsune.starengine.item.Rarity`（fabric 线已有同款用法：`shop/CardTradeFilter.java:4-5,49-50`）。

## 3. 缺失资源清单（fabric 侧 **一个都没有**，需从 forge 基线拷入，勿自己画）

### 3.1 `assets/astral_dice/textures/gui/shop/`（fabric 无该目录）

| 文件 | forge 实测尺寸 | 用途 |
|---|---|---|
| `chipshop_bg.png` | 284×183 | ① 整屏背景 |
| `chipshop_frame_2.png` | 216×78 | ② 条带深底（外层） |
| `chipshop_frame_1.png` | 216×78 | ③ 五格轮廓 |
| `chipshop_quality_1.png` / `_2.png` / `_3.png` | 28×28 ×3 | ④ 稀有/史诗/传奇描边环 |
| `chipshop_select.png` | 42×15 | ⑥ 选中标记 |
| `chipshop_confirm.png` / `chipshop_confirm_push.png` | 24×24 ×2 | ⑧ 确认按钮常态/按下 |
| `chipshop_refresh.png` / `chipshop_refresh_push.png` | 24×24 ×2 | ⑧ 刷新按钮常态/按下 |
| `chipshop_inventory.png` | 172×86 | ⑨ 物品栏面板底纹 |
| `star_coin_small.png` | 13×13 | ⑦ 花费格星币图标（取 (3,3) 起 7×7） |
| `chipshop_frame.png` | 216×78 | 仅素材登记项，**不在绘制路径**（`TEX_STRIP_FRAME` 未被 blit） |

### 3.2 `assets/astral_dice/textures/gui/chip_shop/chips/`（fabric 无该目录）

- **67 张 32×32 PNG**，文件名 = `ChipShopCatalog.POOL` 的 `entry.id()`（ASCII）。
- 已双向核对：`POOL` 67 条 ↔ forge 目录 67 个文件，**无缺无多**（尺寸全为 32×32）。
- 路径由 `ChipShopCatalog.Entry#iconLocation()` 生成（`ICON_DIR = "textures/gui/chip_shop/chips/"`），批量拷目录即可。

### 3.3 语言键（fabric `lang/zh_cn.json` + `en_us.json` 均缺）

| 键 | forge zh_cn | forge en_us |
|---|---|---|
| `gui.astral_dice.chip_shop.cost` | `"%s"` | `"%s"` |
| `gui.astral_dice.chip_shop.empty` | `"无"` | `"None"` |
| `gui.astral_dice.chip_shop.refresh_counter` | `"%s"` | `"%s"` |

（同批相关但属其它批次 scope：`gui.astral_dice.chip_shop`（菜单标题，`ChipShopOpener.java:63` 使用）、`msg.astral_dice.chip_shop_*` 共 8 键在 fabric 侧亦缺。缺键只显示裸键名，不影响编译。）

## 4. 需主 Agent 接线（确切语句 + 插入位置）

### 4.1 `screen/ModMenuTypes.java` —— 追加 `CHIP_SHOP`（**当前 fabric 侧编译阻塞点**）

`shop/ChipShopMenu.java:136` 已写 `super(ModMenuTypes.CHIP_SHOP.get(), containerId)`，但该常量尚不存在。
插入位置：第 20 行（`CARD_INVENTORY` 注册块末行 `() -> new MenuType<>(CardInventoryMenu::new, FeatureFlags.DEFAULT_FLAGS));`）之后、第 22 行 `/** 服务端打开卡牌栏… */` 之前，即当前第 21 行空行处：

```java
    /** 筹码商店菜单（逻辑批已完整实现；见 shop/ChipShopMenu）。 */
    public static final RegistryObject<MenuType<com.merlinkitsune.astral_dice.shop.ChipShopMenu>> CHIP_SHOP =
            MENU_TYPES.register("chip_shop",
                    () -> new MenuType<>(com.merlinkitsune.astral_dice.shop.ChipShopMenu::new, FeatureFlags.DEFAULT_FLAGS));
```

（全限定名写法与 forge 基线一致；所需 import `MenuType` / `RegistryObject` / `FeatureFlags` / `DeferredRegister` 该文件已具备。）

### 4.2 `client/ModClientEvents.java` —— 注册屏幕工厂（客户端）

位置：第 55–60 行 `onClientSetup`，把现有单条 `enqueueWork` 改成两条注册；并在 import 区（第 5 行 `import com.merlinkitsune.astral_dice.screen.CardInventoryScreen;` 下一行）加 `import com.merlinkitsune.astral_dice.screen.ChipShopScreen;`。

```java
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            net.minecraft.client.gui.screens.MenuScreens.register(
                    ModMenuTypes.CARD_INVENTORY.get(), CardInventoryScreen::new);
            net.minecraft.client.gui.screens.MenuScreens.register(
                    ModMenuTypes.CHIP_SHOP.get(), ChipShopScreen::new);
        });
    }
```

### 4.3 已就绪、**不需要**再动的接线

- 网络：`network/ModNetwork.java:72-77` 已注册 `ChipShopBuyPayload`(id 12) / `ChipShopRefreshPayload`(id 13) / `ChipShopDenyPayload`(id 14)。
- 服务端开店：`shop/ChipShopOpener.java:61-63` 已 `openMenu(new SimpleMenuProvider((containerId, inventory, p) -> new ChipShopMenu(containerId, inventory), Component.translatable("gui.astral_dice.chip_shop")))`。
- 拒绝包落点：`network/ChipShopDenyPayload.java:44-48` 已调 `ChipShopClientState.notifyDeny(int)`（签名与本类一致：`public static void notifyDeny(int)`）。

## 5. 无法判定项 / 风险

1. **未编译、未实机**（硬约束 3）。以下符号仅做静态存在性核对，无字节码证据：`GuiGraphics#renderTooltip(Font, ItemStack, int, int)`（同线 `mixin/bridge/ClientTooltipBridgeMixin.java:60` 以该方法为注入目标 ⇒ 1.20.1 存在）、`AbstractContainerScreen#containerTick()`（forge 线 `CardShopScreen`/`CardChoiceScreen`/`ChipShopScreen` 三处 1.20.1 覆写）、`ItemStack#getHoverName()`（本线 `ChipShopMenu.java:420` 在用）。
2. 覆写的 `containerTick()` 声明为 `public`，原版为 `protected` —— 属合法加宽访问（forge 基线同样写法），本批按基线保留、未改成 `protected`。
3. `chipshop_frame.png`（`TEX_STRIP_FRAME`）为素材登记项、不在绘制路径；拷资源时可一并拷入，不拷也不影响显示（无 blit 调用）。
4. 品质外框兜底：`tierOf` 返回 null（非本模组档位）与 pinnacle/bizarre **一律用 `chipshop_quality_3`**（`QUALITY_FRAME_FALLBACK=3`）—— 这是 forge 基线继承的**待用户裁决**口径，本批不改。
5. 资源未落盘前打开界面：`TextureManager` 只报 missing texture 警告、不崩；但筹码格无图标可画（`byStack` 非空才画），视觉会缺整块 —— 属资源批次遗留，非本批代码问题。
6. `ChipShopScreen` 的只读探针（`chipShopLayoutProbe()` / `chipShopLogicProbe()` / `buyRequestCount()` 等）原样搬运；若主 Agent 的守门脚本（`scripts/verify/verify_chip_shop_ui.ps1`，本线未搬）按 forge 路径 grep，需按 fabric 路径另行核对（本批未改探针字面）。

## 6. 本批自检（可复算）

- `python` 只读守门：两文件 `static final` 常量 68/68、5/5 全等；`bom=False`；`crlf` 722/269、`bareLF` 0；`net.minecraftforge.|net.neoforged.|wispforest` 命中 0。`GATE PASS`。
- 行级 diff：`ChipShopScreen` 公共前缀 57 行 / 公共后缀 659 行，差异块 = 平台 javadoc 3 行→7 行；`ChipShopClientState` 前缀 31 / 后缀 235，差异块 3 行→4 行。
- 贴图审计：§3.1 14 个文件在 forge 全部存在且尺寸如上；§3.2 67/67 与 `ChipShopCatalog.POOL` 双向吻合；fabric 侧 `textures/gui/shop/`、`textures/gui/chip_shop/` **目录均不存在**。
