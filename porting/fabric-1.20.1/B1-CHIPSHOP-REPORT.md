# B1-CHIPSHOP 移植报告：筹码商店核心逻辑（fabric-1.20.1）

> 批次：B1-CHIPSHOP　基准：forge-1.20.1（主，同 MC 版本同路径同名文件）／neoforge-1.21.1（仅语义对照，**未搬任何 1.21 专属 API**）
> 迁移线：fabric-1.20.1（Fabric API 0.92.12 / officialMojangMappings / Java 17 / Loom）　报告日期：2026-11-01

## 0. 结论摘要

- **5 个文件**：新建 4 个（`shop/` 四件套）、改 1 个（`block/ChipShopBlock.java` 四处 `TODO(B0-SHOP)` seam 接真实调用）。
- **语义 1:1**：剥 block/line 注释与空白后按行多重集比对，`ChipShopOpener`、`ChipShopMenu` 与 forge **差异 0 行**；`ChipShopState` 仅 8 行、`ChipShopRoller` 仅 26 行差异，且全部落在两处已登记的 seam（玩家附件键归属、筹码槽枚举方式）。
- **未接线（按硬约束留给主 Agent）**：`screen/ModMenuTypes.CHIP_SHOP` 注册（1 行）与 `ChipShopState` 的**初始化期类初始化**（1 行）——二者都在本批 scope 之外（§5），**当前 `ChipShopMenu` 因缺 `CHIP_SHOP` 而无法编译**。
- **未编译**（硬约束：不跑 gradle、不启游戏）。静态检查见 §2，非编译等价。

## 1. 交付文件

路径相对 `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/`。

| # | 文件 | 行数 | forge 对应行数 | 性质 |
|---|---|---|---|---|
| 1 | `shop/ChipShopState.java` | 239 | 219 | 新建，1:1（+20 行 javadoc：平台 seam 与键归属说明） |
| 2 | `shop/ChipShopRoller.java` | 270 | 233 | 新建，1:1（+37 行：javadoc + 私有 helper `forEachChipStack` 的方法体） |
| 3 | `shop/ChipShopOpener.java` | 107 | 104 | 新建，**逐行等价**（+3 行 javadoc） |
| 4 | `shop/ChipShopMenu.java` | 542 | 532 | 新建，**逐行等价**（+10 行 javadoc） |
| 5 | `block/ChipShopBlock.java` | 159（原 167） | —（改既有） | **改**：四处 seam 接 `ChipShopOpener` / `ChipShopState`，删去 8 行 TODO 说明 |

未改动：`shop/ChipShopCatalog.java`（B0 已移植的 367 行版本）、`item/ModItems.java`、`compat/curios/CuriosApi.java`、`network/ModNetwork.java`、`component/ModAttachments.java`、`AstralDiceMod.java`、`AstralDiceClient.java`。

## 2. 自检结果（可复现）

| 判据 | 结果 |
|---|---|
| 行尾 / BOM | 5 文件全部 **CRLF**（`\r\n` 计数 == 换行计数，裸 LF = 0）、**无 BOM**（首字节非 EF BB BF） |
| 禁用 import | `shop/` 四文件 + `block/ChipShopBlock.java` 对 `net.minecraftforge.*` / `net.neoforged.*` **0 命中** |
| 第三方饰品库 | **0 命中** `trinkets` / `accessories` / `wispforest`（饰品统一走 `compat/curios/CuriosApi`） |
| 1.21 专属 API | 无 `StreamCodec` / `CustomPacketPayload` / `DataComponents` / `Codec.dispatch` 等 1.21 面 |
| 括号平衡 | 剥注释与字符串后 `{}` / `()` 差均为 0 |
| 语义比对 | 剥注释与空白后行多重集：`ChipShopOpener` 0/0、`ChipShopMenu` 0/0、`ChipShopState` 3 / 5、`ChipShopRoller` 8 / 18（差异逐条见 §4） |
| 外部成员可达 | `ModNetwork.sendToPlayer/ActionBarMessage`、`StarCoinBalanceSync.forceResend/notifyChanged`、`ModItems.BLANK_CHIP`、`CuriosApi.getCuriosInventory/getStacksHandler`、`DiceCurioItem.configStarLevel/isDiceItem`、`AttachedDataKey.builder/sync/copyOnDeath` 全部存在；**缺** `ModMenuTypes.CHIP_SHOP`（§5） |

比对脚本口径：读 forge/fabric 两个文件 → 去 `/* */` 与 `//…` → 折叠空白 → 去空行 → 行多重集差。它证明「语句集合一致」，**不**证明编译通过。

## 3. 1:1 要点（未改动的语义，供复核）

**常量**（`ChipShopMenu`）：`OFFER_SLOTS=ChipShopCatalog.OFFER_COUNT`、`PLAYER_SLOT_COUNT=36`、`MAIN_SIZE=27`、`GRID_X=61`/`GRID_Y=94`/`GRID_PITCH=18`/`HOTBAR_GRID_Y=152`/`ITEM_INSET=1`、`HIDDEN_X=HIDDEN_Y=-10000`、`DATA_PRICE=0`…`DATA_TIER_FIRST=4`/`DATA_SLOT_COUNT=7`、`DENY_NONE=-1`/`DENY_NO_COIN=0`/`DENY_NO_REFRESH=1`/`DENY_INVENTORY_FULL=2`、`BUY_COOLDOWN_TICKS=6000L`。

**槽位顺序**：3 个隐藏 `OfferSlot`（`mayPlace=false`、`mayPickup=false`、`getMaxStackSize=1`）→ 主栏 9×3（物品栏槽 9..35）→ 快捷栏 9×1（槽 0..8）；**36 条玩家槽保持逐条字面量**（forge 侧 `scripts/verify/verify_chip_shop_ui.ps1` 的 G6 按字面统计），未写成循环。

**DataSlot 顺序**：0 价格 / 1 剩余刷新 / 2 刷新上限（恒 `ChipShopCatalog.REFRESH_MAX`）/ 3 拒绝原因 / 4..6 三格品质码，共 7 条，顺序与总数不变（守门 G17/G19/G20/G23 依赖字面）。

**`handleBuy` 校验顺序**（逐条未动）：菜单归属（`buyer != this.player`）→ 客户端调用直接 return → 索引越界 return → 候选空 / 越界 return → `StarEngineEconomy.isAvailable()` + `hasBalance` 失败 → `deny(DENY_NO_COIN, "msg.astral_dice.chip_shop_no_coin", price)` → `withdraw` 失败同款 deny → 发货：创造模式走原版 `add`（满包被吞、`dropped=false`、**不掉落**），生存模式先自判 `playerInventory.items` 36 格有无空格，有空 `add`、无空 `buyer.drop(bought,false)` 且 `dropped=true` → 落盘 `withPurchased(categoryOf(**chip**))`（**不是 `bought`**，理由同 forge 注释：`add` 成功会把 `bought` 清零）→ `withOffers(roll(...), diceKey(...))` → `withCooldown(gameTime+6000)` → `store` → `applyState` → `StarCoinBalanceSync.notifyChanged` → actionbar GOLD `chip_shop_bought`（`chip.getHoverName()`）→ 若掉落再补 YELLOW `chip_shop_chip_dropped` → 机器行 `CHIP_SHOP_BUY`（字段顺序 `player/offer/chip/price/purchased/refreshes/offers/dropped/cooldownUntil`）→ `buyer.closeContainer()`。

**`handleRefresh`**：同款 guard → `refreshesLeft()<=0` ⇒ `deny(DENY_NO_REFRESH, "msg.astral_dice.chip_shop_no_refresh")` → 锚点 `regenAnchor()==0L ? now+REFRESH_REGEN_TICKS : 既有锚点` → `withRefreshes(left-1, anchor)` → 重抽 → `store` → `applyState` → 机器行 `CHIP_SHOP_REFRESH`。**无自动恢复 ticker**（时间驱动的恢复已删，锚点仅作存档/冻结测试兼容字段）。

**`ChipShopRoller` 算法**：`availablePool`（剔已拥有，`ownsEverything`/`isOwned`/`ownedChipItems` 只扫 36 格、**不含副手**）→ `equippedSchoolCounts`（筹码槽 `chip`）→ `starLevel` → `QUALITY_PROB[star]` → 逐 slot `pickTier`（仅对有候选的档归一化，浮点残差兜底取最高可用档）→ `pickWeighted`（`schoolWeight × categoryWeight(slot, candidate, lastPick)`，`double` 随机 + 兜底）→ `picked.add` + `pool.remove`。`diceKey` = 无骰 `"none"`，否则 `BuiltInRegistries.ITEM.getKey(getItem()) + "|" + star`。

**`ChipShopState`**：`record(List<String> offers, Optional<String> lastPickCategory, int purchasedCount, int refreshesLeft, long regenAnchor, long cooldownUntil, long lastBlankInsertAt, String rolledDiceKey)`；`CODEC` 8 个 `optionalFieldOf`，键名与默认值 `offers/[]`、`last_pick/∅`、`purchased/0`、`refreshes/REFRESH_INITIAL`、`regen_anchor/0L`、`cooldown_until/0L`、`last_blank_insert/0L`、`rolled_dice/""` 逐字照抄；`EMPTY`、`price()`、`offerStack/offerTier`、`withOffers/withPurchased/withRefreshes/withRefreshGranted/withCooldown/withBlankInsert`、`inCooldown`、`needsReroll`、`offersKey`（空 ⇒ `"-"`，否则 `join("+")`）语义不变；`withRefreshes` 与 `withRefreshGranted` 的 clamp 区间 `0..REFRESH_MAX` 不变。

**`ChipShopOpener`**：`INVENTORY_SLOTS=36`；拒绝顺序 `ownsEverything` → `hasNoRoom` → `inCooldown(gameTime)`，对应日志 reason `owned_all` / `inventory_full` / `cooldown` 与文案键 `chip_shop_owned_all` / `chip_shop_inventory_full` / `chip_shop_cooldown`（红字 actionbar + `CHIP_SHOP_OPEN_DENIED`）；随后 `ensureOffers`（仅 `needsReroll` 才重掷并 `CHIP_SHOP_REROLL_ON_ENTER`）+ `openMenu(new SimpleMenuProvider((id,inv,p) -> new ChipShopMenu(id,inv), Component.translatable("gui.astral_dice.chip_shop")))` + 机器行 `CHIP_SHOP_OPEN`。

## 4. 偏差与 seam（forge 原样 → fabric 改法 + 理由）

| # | forge 原样 | fabric 改法 | 理由 |
|---|---|---|---|
| 1 | `component/ModAttachments.CHIP_SHOP_STATE`（`ModAttachments.java:590-592`，并 `SYNCED_KEYS.add(...)`） | 键**内联在 `shop/ChipShopState.java`**：`CHIP_SHOP_STATE = AttachedDataKey.builder("chip_shop_state", CODEC, () -> EMPTY).sync().copyOnDeath().build()`；`get/store` 只调本类静态方法 | fabric 的 `component/ModAttachments.java`（1709 行）**没有**该键，且本批硬约束不许改它（他人持有）。键名、codec、默认值、同步、死亡保留四项与 forge 完全对齐 |
| 2 | 死亡保留写在 `AstralData#onPlayerClone` 白名单；客户端同步靠 `SYNCED_KEYS` | 同一张「死亡保留表」改由键自己声明 `Builder#copyOnDeath()`（`AttachedDataKey.java:196`），同步靠 `Builder#sync()` | `AttachedDataKey` 类头已写明「Forge 的 white-list 与 fabric 的自声明是同一份集合」；构造器 `AttachedDataKey.java:60-68` 直接 `buildAndRegister`，无需另处注册 |
| 3 | javadoc 中 `{@link CardShopShelf}` | 改 `{@code CardShopShelf}` | 无功能影响；该类已由并行批次 B1-CARDSHOP 落地，此处保守用 `{@code}`（避免与对方后续改名产生坏链） |
| 4 | `item/CurioSlotUtil#forEachCurioStack(player, "chip", consumer)` | 本类私有 `forEachChipStack(Player, Consumer<ItemStack>)`：`CuriosApi.getStacksHandler(player, CHIP_SLOT_ID)` → `handler.getStacks()` → 按 `getSlots()` 逐格回调（**空栈照常回调**，与 forge 一致） | fabric 的 `item/CurioSlotUtil.java` **没有** `forEachCurioStack`；且硬约束要求饰品统一走 `compat/curios/CuriosApi`。改动只落在「如何枚举筹码槽」，枚举结果与调用点一字未动 |
| 5 | `Math.max(0, Math.min(3, CardShopShelf.currentStarLevel(player)))` | **同字面调用**（`shop/CardShopShelf.java:314` 为包级 `static int currentStarLevel(Player)`，同包可直接调；其内部已把 forge 的 `CuriosCompat` 换成本线 `CuriosApi`） | **无偏差**：该类由并行批次 B1-CARDSHOP 落地；先前各自复刻的 `equippedDiceStarLevel` 已删除，避免两份星阶口径漂移 |
| 6 | `starengine.item.CuriosCompat.getCuriosInventory(player)`（现仅剩 `diceKey` 一处） | `compat/curios/CuriosApi.getCuriosInventory(player)` | 同签名 `Optional<ICuriosItemHandler>`，`findFirstCurio(Predicate<ItemStack>)` 语义一致；硬约束指定走本线垫片 |
| 7 | `net.minecraft.core.registries.BuiltInRegistries` / `net.minecraft.resources.ResourceLocation` 全限定名 | 正常 import | 纯风格，无语义差 |

`ChipShopBlock.java` 的四处 seam（原为 TODO 空实现，现已接真实调用）：`openShop` → `ChipShopOpener.open(player)`；`lastBlankInsertAt` → `ChipShopState.get(player).lastBlankInsertAt()`；`refreshesLeft` → `ChipShopState.get(player).refreshesLeft()`；`storeBlankInsert` → `ChipShopState.store(player, state.withBlankInsert(now, state.refreshesLeft() + 1, 0L))`。其余（`BLANK_INSERT_COOLDOWN_TICKS=2400L`、world `gameTime` 差值判据、`stack.shrink(1)`、三处 actionbar/日志）原样未动。

## 5. 需主 Agent 接线处（**必做**，否则编译不过 / 存档静默丢失）

**(a) 菜单类型注册** — `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/screen/ModMenuTypes.java`：

- 在 `import com.merlinkitsune.astral_dice.item.dice.DiceCurioItem;`（现 L7）之后加：`import com.merlinkitsune.astral_dice.shop.ChipShopMenu;`
- 在 `CARD_INVENTORY` 声明（现 L19-20）之后加两行：
  `public static final RegistryObject<MenuType<ChipShopMenu>> CHIP_SHOP = MENU_TYPES.register("chip_shop",`
  `        () -> new MenuType<>(ChipShopMenu::new, FeatureFlags.DEFAULT_FLAGS));`

这是 `ChipShopMenu` 构造器 `super(ModMenuTypes.CHIP_SHOP.get(), containerId)` 的唯一阻塞项。

**(b) 附件键的初始化期类初始化** — 二选一：

- `AstralDiceMod#onInitialize()` 中 `com.merlinkitsune.astral_dice.component.ModAttachments.ensureRegistered();`（现 L57）之后加一行：
  `com.merlinkitsune.astral_dice.shop.ChipShopState.CHIP_SHOP_STATE.name();`
- 或在 `ModAttachments#ensureRegistered()` 末尾加同一行。

理由即 `AstralDiceMod.java:52-56` 既有注释：附件键的静态初始化是惰性的，若推迟到「第一次读写附件」才跑，玩家 NBT 反序列化时注册表里还没有该 id，Fabric 会逐条 `Unknown attachment type ... skipping` **静默丢弃**持久化值（forge 的 `chip_shop_state` 里含 `purchased`/`refreshes`/`last_blank_insert`/`cooldown_until`，丢了等于玩家刷新额度与冷却全部归零）。位置必须是 mod 初始化期，本批 scope 覆盖不到这两个文件。

**(c) 可选**：若后续搬 Jade 悬浮框或任何**客户端**要读 `cooldownUntil` 的显示层，需把该键加入 `ModAttachments` 的 `SYNCED_KEYS` 白名单（现从 L1331 起），否则全量快照同步不会带它（逐次 `set()` 的即时同步仍然生效）。

**(d) 无需接线**：`network/ModNetwork` 侧的 3 条筹码商店消息已由 B1-NETWORK 落地（fabric id 12/13/14 ↔ forge 21/22/23），收包器按 `EnvType` 自动安装。

## 6. 依赖并行批次 / 未解析类型

| 依赖 | 状态 |
|---|---|
| `network/ChipShopDenyPayload`（本批 `deny(...)` 里 `new ChipShopDenyPayload(int)`） | ✅ 已存在，签名与 forge 一致（B1-NETWORK 落地） |
| `network/ChipShopBuyPayload#handle` / `ChipShopRefreshPayload#handle` 调 `menu.handleBuy(serverPlayer, offerIndex)` / `menu.handleRefresh(serverPlayer)` | ✅ 本批方法签名与之匹配（均为 `public`，`ServerPlayer` 参数） |
| `client/ChipShopClientState`（`ChipShopDenyPayload.java:46` 调用 `notifyDeny(int)`） | ❌ **文件不存在** ⇒ 拒绝包当前无法编译；属屏幕批次，本批不创建 |
| `screen/ModMenuTypes.CHIP_SHOP` | ❌ 缺（§5a） |

## 7. 无法判定项

1. **未编译**（硬约束禁 gradle）：§2 是静态检查，不覆盖泛型推断、方法重载与 mixin；`playerInventory.items` 直读 36 格在 fabric 线是否为 `public final DefaultedList<ItemStack>` 未用编译器证实（按 1.20.1 Mojmap 应为是，且 forge 侧同写法）。
2. **forge 侧 UI 守门未搬**：`scripts/verify/verify_chip_shop_ui.ps1`（G6/G17/G19/G20/G23）是 PowerShell 界面层字面统计，本批只保留其依赖的字面结构（36 条槽、7 条 DataSlot 顺序、原因码常量），未搬脚本本身。
3. **`ChipShopClientState.notifyDeny` 的 30t 红闪语义**不在本批，未复核。
4. `ChipShopState` 的 `sync()` 是否被 `ClientAstralData` 正确回读（客户端 `get(player)`）未验证——本批客户端路径只读 DataSlot 与隐藏槽同步内容，不依赖它。

## 8. 任务前提修正（重要）

`planPlacement` / `canAcceptDrop` / `resolvePlacedItem` 三处**不属于筹码商店**：它们分别在 `forge-1.20.1/.../shop/CardShopMenu.java:906`、`forge-1.20.1/.../screen/CardShopScreen.java:1602`、`forge-1.20.1/.../item/sign/LinglingSignManager.java:138`，即**卡牌商店的并栈逻辑**；`docs/compat-1.5.0-porting.md:31` 的「商店并栈」一节讲的也是 CardShop。`ChipShopMenu` 里没有这三处方法（候选槽 `mayPickup=false`、购买只有 `handleBuy` 一条入口，不存在并入/拖拽放置的转换逻辑）⇒ 本批不存在「三处必须共用同一转换逻辑」的约束对象。若后续搬卡牌商店，该约束应在 `CardShopMenu`/`CardShopScreen` 上执行。

## 9. 复用未改写（B0 既有件）

`shop/ChipShopCatalog.java`（367 行，B0 移植，被 `trade/ChipTraderTrades.java` 消费）**一字未改**；本批使用的成员全部存在于该版本：`OFFER_COUNT`、`TIER_RARE/EPIC/LEGENDARY`、`QUALITY_PROB`、`PRICE_INITIAL`、`REFRESH_INITIAL/MAX/REGEN_TICKS`、`POOL`、`School`/`Category`、`Entry`（`id/tier/school/category/itemId/item/stack/iconPath`）、`byId`/`byStack`/`schoolOf`/`categoryOf`/`priceOf`/`schoolWeight`/`categoryWeight`/`tierOf`/`weightOf`/`pickWeighted`。
