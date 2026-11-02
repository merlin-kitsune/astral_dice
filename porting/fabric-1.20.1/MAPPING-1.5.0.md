# fabric-1.20.1 1.5.0 全量移植 Mapping（MAPPING-1.5.0）

> 文档用途：把 neoforge-1.21.1（主基准）/ forge-1.20.1（次基准）上已存在的 1.5.0 内容，逐项映射到 fabric-1.20.1 的落点。
> 生成方式：全部结论来自本仓实测（grep / read / git diff --no-index --numstat / git show / 文件系统枚举），不使用记忆推断。
> 写作限制：本文件是本次任务**唯一**允许写入的仓库文件；未改动、未删除、未 add/commit/stash 任何既有文件；未运行 Gradle、未启动游戏。

## 0. 基线快照与度量口径

### 0.1 仓库基线（本次实测）

| 项 | 值 | 取证方式 |
|---|---|---|
| 仓库 | `F:\MCProject\astral_dice_multiloader`（四线单仓） | 工作目录 |
| 分支 | `multi-main` | `git rev-parse --abbrev-ref HEAD` |
| HEAD | `fd990c15` | `git rev-parse --short HEAD` |
| 四线目录 | `neoforge-1.21.1` / `forge-1.20.1` / `neoforge-26.1.2` / `fabric-1.20.1` | 目录枚举 |
| fabric mod_version | `1.5.0-rc.7+fabric_1.20.1` | `fabric-1.20.1/gradle.properties` |
| 未提交改动 | 存在且非本文档产生，全程未触碰 | `git status --porcelain` |

### 0.2 Java 源文件规模

统计范围：`<线>/src/main/java/com/merlinkitsune/astral_dice` 下递归全部 `.java`。

| 线 | .java 文件数 |
|---|---|
| neoforge-1.21.1 | 411 |
| forge-1.20.1 | 403 |
| neoforge-26.1.2 | 425 |
| fabric-1.20.1 | 380 |

### 0.3 度量口径（本文档所有「差异行数」的含义）

差异行数统一取自：

```
git diff --no-index --numstat -- forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice \
                                   fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice
```

即 **「+新增行」 与 「-删除行」 之和**（同一行被重写记 2）。

**口径差异声明**：任务书给出的参考值（`DiceCombatEvents 714`、`ModAttachments 503`、`ModRecipeProvider 359`、`ModItemModelProvider 320`、`AstralDiceMod 315`、`BaseSignItem 245`、`DiceCombatModifiers 204`、`DamageEffectCardHandler 181`、`ModItems 177`、`ModTooltipHandler 169`、`ModNetwork 161`）**在本 HEAD 上无法用任何 `git diff` 选项复现**（试过 `--numstat` / `--stat` / `--shortstat` / `-w` / `--ignore-space-at-eol`）。本文档实测值为 `883 / 711 / 590 / 323 / 351 / 299 / 270 / 210 / 236 / 261 / 255`，故判定任务书数字来自更早快照或另一 fork 点。**本文档一律采信实测值**，并在 §3 表中并列保留任务书参考值以便对账。

### 0.4 目录

- §0 基线快照与度量口径
- §1 缺口类总表（130 类）
  - 1.0 分组、等级分布与平台能力汇总
  - 1.1 组一：商店经济与交易（33 类，行 1–33）
  - 1.2 组二：事件体系（33 类，行 34–66）
  - 1.3 组三：实体与效果牌（17 类，行 67–83）
  - 1.4 组四：立牌（19 类，行 84–102）
  - 1.5 组五：角色效果（15 类，行 103–117）
  - 1.6 组六：集成与数据（13 类，行 118–130）
  - 1.7 平台专属类（不算缺口）及其替代实现
- §2 三列 API 映射表（neo 1.21.1 → forge 1.20.1 → fabric 1.20.1）
- §3 共有漂移清单（前 40）
- §4 资源缺口清单
- §5 每功能验收判据
- 附录 A 取证命令复现
- 附录 B 无法判定项


## 1. 缺口类总表（130 类）

### 1.0 分组、等级分布与平台能力汇总

缺口口径（可复现）：`neoφ = neoforge-1.21.1/src/main/java/.../*.java 相对路径集合`，`forgeφ`、`fabφ` 同理，`fabric 无 = neoφ ∩ forgeφ − fabφ`，本次实测 **130**，与任务书口径一致。

#### 1.0.1 分组计数（本文档按源码目录严格划分）

| 组 | 目录范围 | 类数 | 行号区间 | 原样搬 | 需改写 | 需重写 | 不搬 |
|---|---|---|---|---|---|---|---|
| 组一 商店经济与交易 | `block/ shop/ screen/ trade/ world/ client/ network/ choice/ item/chip/ recipe/condition/` | 33 | 1–33 | 8 | 24 | 1 | 0 |
| 组二 事件体系 | `event/`（含 `event/api/`）+ `block/EventDrawBlock` + `item/EventDrawItem` + 相关 `client/` | 33 | 34–66 | 13 | 20 | 0 | 0 |
| 组三 实体与效果牌 | `entity/` + `block/BarricadeBlock` + `block/ModBlocks` + `item/card/` + `item/Repulsion*` + `combat/` + `network/BeamFxPayload` + `client/*Model`、`*Renderer` | 17 | 67–83 | 5 | 9 | 3 | 0 |
| 组四 立牌 | `item/sign/`（15）+ `component/Sign*`（2）+ `mixin/client/*`（2） | 19 | 84–102 | 6 | 12 | 0 | 1 |
| 组五 角色效果 | `effect/`（13）+ `mixin/`（2） | 15 | 103–117 | 14 | 1 | 0 | 0 |
| 组六 集成与数据 | `compat/jei/`（8）+ `compat/jade/`（3）+ `datagen/DataGenerators` + `item/ModTags` | 13 | 118–130 | 0 | 4 | 8 | 1 |
| **合计** | | **130** | 1–130 | **46** | **70** | **12** | **2** |

#### 1.0.2 与任务书分布口径的差异（必须记录）

任务书给的六组分布为 `37 / 27 / 11 / 17 / 15 / 13`，合计 **120**，不等于 130；且其自身子项相加也对不上（商店组列出 `shop 11 + screen 4 + block 3 + client 2 + network 4 + trade 2 + world 2 + item/chip 6 + recipe/condition 1 = 35`，仍非 37；事件组 `event/api 5 + event 12 + choice 2 + EventDrawBlock + EventDrawItem + client 3 = 24`，非 27）。

⇒ 任务书分布**不可同时成立**。本文档改为按源码顶层目录严格划分，六组恰好覆盖 130 类且无重复无遗漏（可用 §附录 A 的脚本逐行复算）。

#### 1.0.3 移植等级判定标准（可复算的操作化定义）

| 等级 | 判定条件（对源类逐个 grep 后落定） | 本表计数 |
|---|---|---|
| 原样搬 | 文件内 `import` 不含 `net.neoforged.*` / `net.minecraftforge.*` / `top.theillusivec4.curios.*`，且不引用平台专属类型 ⇒ 可逐字复制 | 46 |
| 需改写 | 含上述平台 import，但 fabric 线已有等价 shim（`platform/event/LoaderBus`、`platform/network/SimpleChannel`、`platform/registry/DeferredRegister`、`compat/curios/CuriosApi`、`component/AttachedDataKey`、`component/ModDataComponents`、`platform/config/ForgeConfigSpec`）⇒ 换 import/换签名 | 70 |
| 需重写 | 无等价 shim（实体注册与渲染、1.20.1 DataComponent、craft conditions 注册表、JEI 构建接法）⇒ 从零接线 | 12 |
| 不搬 | 平台机制本身在 1.20.1/fabric 不成立，理由写在表内 | 2 |

#### 1.0.4 平台能力需求汇总（按「需要新写/新增」排序）

| 平台能力 | 需要它的缺类数 | fabric 现有实现 | 缺口动作 |
|---|---|---|---|
| `LoaderBus` 事件注册（替代 `@EventBusSubscriber`） | 15 | `platform/event/LoaderBus.java`（31L 起，`INSTANCE` + `register(Class)`） | 新类统一在 `AstralDiceMod.java:125-151` 追加 `LoaderBus.INSTANCE.register(X.class)` |
| `DeferredRegister` / `RegistryObject` | 10 | `platform/registry/DeferredRegister.java`(141L)、`RegistryObject.java`(105L) | 直接可用；新增 `Registries.BLOCK` / `Registries.ENTITY_TYPE` / `Registries.MENU` 注册 |
| 新增 mixin（含 client） | 7 | `astral_dice.mixins.json`（client 组 12 条，**无** SignTranslucency/HealOrHarm/Regeneration） | 新增 3 条并过 `tools/audit_mixin_injection.py` |
| `PacketDistributor` → `SimpleChannel` 包 id | 6 | `platform/network/SimpleChannel.java`(172L) + `network/ModNetwork.java`(558L) | 新增 8 个内部消息类（轮换 id 顺延） |
| `CuriosApi` / `SlotContext` | 6 + 3 | `compat/curios/CuriosApi.java`（Accessories 主源 + Trinkets 兜底，消费方 94 处只需换 import） | 仅换 import |
| `AttachedDataKey` 新键 | 4 | `component/AttachedDataKey.java`(205L) | 新增 8 个键（SIGN_STATE / SHOP_SHELF / EVENT_DRAWER_* / …，见 §3） |
| `TickEvent`（ServerTick） | 3 | `platform/event/TickEvent.java` | 直接可用 |
| **`ExplosionEvent`** | 1 | **无**（`platform/event/` 与 `level/BlockEvent` 均无） | **需新增事件类 + 苦力怕爆炸 bridge mixin** |
| **`VillagerTradesEvent`** | 1 | **无** | **需新增事件类 + `Villager` 交易 bridge mixin** |
| `EntityAttributeCreationEvent` | 1 | **无** | 改走 `FabricDefaultAttributeRegistry`（无需事件） |
| `DataComponent`（三态） | 1 | `component/ModDataComponents.java`（ItemDataKey NBT shim） | 新增三态常量 + 迁移判据 |
| JEI 编译期 API | 8 | **不可用**（Loom 1.14.10 拒绝 JEI 15.62，见 `build.gradle:172-176`） | 需手工 `compileOnly` jar 或整组延期 |
| 资源：textures/models/blockstates | 见 §4 | `assets/astral_dice` 缺 238 项 | 230 项可从 neo 逐字复制，8 项按 1.20.1 改 |

#### 1.0.5 FQN 约定

两列 FQN 同值的原因：fabric 线采用 **Mojang 官方映射**（`fabric-1.20.1/build.gradle:67 loom.officialMojangMappings()`），且包根沿用 `com.merlinkitsune.astral_dice`，类名/包路径与 neoforge-1.21.1 完全一致。因此「映射」实际是「在相同包路径下新建同名文件 + 内部换平台写法」，§1 表中源/目标两列只是为了让每一项落点唯一可检，便于按 `test -f` 直接验收（见 §5）。

### 1.1 组一：商店经济与交易（33 类）

判据：`fabric-1.20.1/src/main/java` 下出现 `shop/CardShopCatalog.java` 等文件 + `screen/CardShopScreen` 可被 `MenuType` 打开（见 §5 G1）。

| # | 源类 FQN（neoforge-1.21.1） | fabric 目标 FQN | 功能 | 移植等级 | 平台能力依赖 | 所需资源 |
|---|---|---|---|---|---|---|
| 1 | com.merlinkitsune.astral_dice.block.CardShopBlock.java | com.merlinkitsune.astral_dice.block.CardShopBlock.java | 卡牌商店方块：四向 FACING + 形状覆写，交互打开商店界面 | 需改写 | 1.21.1 useWithoutItem(BlockState,Level,BlockPos,Player,BlockHitResult) → 1.20.1 use(...,InteractionHand,BlockHitResult)；DeferredRegister(Registries.BLOCK)；ServerPlayer#openMenu | models/block/card_shop.json、blockstates/card_shop.json、textures/block/card_shop.png(+_particle) |
| 2 | com.merlinkitsune.astral_dice.block.ChipShopBlock.java | com.merlinkitsune.astral_dice.block.ChipShopBlock.java | 筹码商店方块：同上 + 空筹码投入 useItemOn 分支 | 需改写 | 双交互签名 useItemOn/use；PacketDistributor→SimpleChannel；DeferredRegister(BLOCK) | chip_shop 三项资源；textures/block/chip_shop_particle.png |
| 3 | com.merlinkitsune.astral_dice.block.EventDrawBlock.java | com.merlinkitsune.astral_dice.block.EventDrawBlock.java | 抽奖盒售卖机方块：收星币产出 event_ticket | 需改写 | PacketDistributor(sendToPlayer)→SimpleChannel.sendToPlayer；DeferredRegister(BLOCK) | event_drawer 三项资源；textures/block/event_drawer.png |
| 4 | com.merlinkitsune.astral_dice.screen.CardShopScreen.java | com.merlinkitsune.astral_dice.screen.CardShopScreen.java | 卡牌商店界面(2158L)：货架/价格/刷新/转交按钮 | 需改写 | AbstractContainerScreen 1.20.1 渲染签名(GuiGraphics)+坐标常量；PacketDistributor→CHANNEL | textures/gui/shop/* 缺 91 张；textures/gui/ 根缺 1 张 |
| 5 | com.merlinkitsune.astral_dice.screen.ChipShopScreen.java | com.merlinkitsune.astral_dice.screen.ChipShopScreen.java | 筹码商店界面(716L)：品质/权重/重抽 | 需改写 | 同上；SimpleChannel | textures/gui/chip_shop/* 缺 67 张 |
| 6 | com.merlinkitsune.astral_dice.shop.CardShopCatalog.java | com.merlinkitsune.astral_dice.shop.CardShopCatalog.java | 抽池与星级门槛的静态目录表(224L) | 原样搬 | 无平台依赖（纯数据 + TagKey 已由 item/ModTags 提供） | 无 |
| 7 | com.merlinkitsune.astral_dice.shop.CardShopCycleTicker.java | com.merlinkitsune.astral_dice.shop.CardShopCycleTicker.java | ServerTickEvent 驱动的商店轮换计时 | 需改写 | LoaderBus + TickEvent.ServerTick（fabric platform/event/TickEvent.java 已有） | 无 |
| 8 | com.merlinkitsune.astral_dice.shop.CardShopMenu.java | com.merlinkitsune.astral_dice.shop.CardShopMenu.java | 商店容器菜单(1062L)：槽位/点击/转交逻辑 | 需改写 | AbstractContainerMenu 同签名；MenuType 走 platform DeferredRegister；SimpleChannel | 无 |
| 9 | com.merlinkitsune.astral_dice.shop.CardShopOpener.java | com.merlinkitsune.astral_dice.shop.CardShopOpener.java | 服务端打开商店(37L) | 需改写 | ServerPlayer#openMenu + MenuProvider | 无 |
| 10 | com.merlinkitsune.astral_dice.shop.CardShopShelf.java | com.merlinkitsune.astral_dice.shop.CardShopShelf.java | 货架 record(349L)：价格/折扣/可售判定 | 需改写 | top.theillusivec4.curios.api.CuriosApi → compat/curios/CuriosApi（换 import） | 无 |
| 11 | com.merlinkitsune.astral_dice.shop.CardTradeFilter.java | com.merlinkitsune.astral_dice.shop.CardTradeFilter.java | 专属牌排除规则(117L) | 原样搬 | 无（读 item/ModTags.Items.IS_EXCLUSIVE） | data/astral_dice/tags/item/is_exclusive.json |
| 12 | com.merlinkitsune.astral_dice.shop.ChipShopCatalog.java | com.merlinkitsune.astral_dice.shop.ChipShopCatalog.java | 筹码品质概率/权重表(366L) | 原样搬 | 无平台依赖 | 无 |
| 13 | com.merlinkitsune.astral_dice.shop.ChipShopMenu.java | com.merlinkitsune.astral_dice.shop.ChipShopMenu.java | 筹码商店菜单(530L) | 需改写 | MenuType + SimpleChannel | 无 |
| 14 | com.merlinkitsune.astral_dice.shop.ChipShopOpener.java | com.merlinkitsune.astral_dice.shop.ChipShopOpener.java | 打开筹码商店的三项拒绝条件(103L) | 需改写 | openMenu + SimpleChannel | 无 |
| 15 | com.merlinkitsune.astral_dice.shop.ChipShopRoller.java | com.merlinkitsune.astral_dice.shop.ChipShopRoller.java | 抽取算法(234L) | 原样搬 | 无平台依赖 | 无 |
| 16 | com.merlinkitsune.astral_dice.shop.ChipShopState.java | com.merlinkitsune.astral_dice.shop.ChipShopState.java | 商店状态 record(214L) | 原样搬 | 无平台依赖 | 无 |
| 17 | com.merlinkitsune.astral_dice.client.CardShopClientState.java | com.merlinkitsune.astral_dice.client.CardShopClientState.java | 客户端商店快照 | 原样搬 | 无（由 S2C 包填充） | 无 |
| 18 | com.merlinkitsune.astral_dice.client.ChipShopClientState.java | com.merlinkitsune.astral_dice.client.ChipShopClientState.java | 客户端筹码店快照 | 原样搬 | 无 | 无 |
| 19 | com.merlinkitsune.astral_dice.network.ChipShopBuyPayload.java | com.merlinkitsune.astral_dice.network.ChipShopBuyPayload.java | C2S 买筹码 | 需改写 | NeoForge record+StreamCodec+IPayloadContext → SimpleChannel 内部类 encode/decode/handle(Supplier<NetworkEvent.Context>) | 无 |
| 20 | com.merlinkitsune.astral_dice.network.ChipShopDenyPayload.java | com.merlinkitsune.astral_dice.network.ChipShopDenyPayload.java | S2C 拒绝原因 | 需改写 | 同上；CHANNEL.send(PacketDistributor.Target.PLAYER,...) | 无 |
| 21 | com.merlinkitsune.astral_dice.network.ChipShopRefreshPayload.java | com.merlinkitsune.astral_dice.network.ChipShopRefreshPayload.java | S2C 刷新商店 | 需改写 | 同上 | 无 |
| 22 | com.merlinkitsune.astral_dice.trade.CardTraderTrades.java | com.merlinkitsune.astral_dice.trade.CardTraderTrades.java | VillagerTradesEvent 注入卡牌交易(169L) | 需改写 | Fabric 无 VillagerTradesEvent：需新增 trade bridge mixin 或在 Villager 交易表注入；BasicItemListing→VillagerTrades.ItemListing 自实现 | textures/entity/villager/profession/card_trader.png |
| 23 | com.merlinkitsune.astral_dice.trade.ChipTraderTrades.java | com.merlinkitsune.astral_dice.trade.ChipTraderTrades.java | 同上(156L) | 需改写 | 同上 | textures/entity/villager/profession/chip_trader.png |
| 24 | com.merlinkitsune.astral_dice.world.ModPoiTypes.java | com.merlinkitsune.astral_dice.world.ModPoiTypes.java | POI 类型注册(84L，注释明确「只有 POI 不够，认领走标签」) | 需改写 | DeferredRegister(Registries.POINT_OF_INTEREST_TYPE)；RegistryObject 代替 DeferredHolder | data/astral_dice/tags/point_of_interest_type/acquirable_job_site.json |
| 25 | com.merlinkitsune.astral_dice.world.ModVillagerProfessions.java | com.merlinkitsune.astral_dice.world.ModVillagerProfessions.java | 村民职业注册(59L) | 需改写 | DeferredRegister(Registries.VILLAGER_PROFESSION)；1.20.1 VillagerProfession 6 参 record | 同上 + profession 纹理 |
| 26 | com.merlinkitsune.astral_dice.item.chip/AbyssReagentChipItem.java | com.merlinkitsune.astral_dice.item.chip/AbyssReagentChipItem.java | 深渊试剂筹码(143L)：死亡事件 + Curios 槽判定 | 需改写 | LoaderBus LivingDeathEvent + compat/curios shim；LinglingGranted 类附件键 | textures/item/abyss_reagent_chip.png；手册 chips_healing/abyss_reagent_chip.json |
| 27 | com.merlinkitsune.astral_dice.item.chip/ExtraBatteryChipItem.java | com.merlinkitsune.astral_dice.item.chip/ExtraBatteryChipItem.java | 额外电池筹码(68L) | 原样搬 | 无平台 import（继承 BaseChipItem） | textures/item/extra_battery_chip.png；手册 chips_other/extra_battery_chip.json |
| 28 | com.merlinkitsune.astral_dice.item.chip/FlashlightBurstChipItem.java | com.merlinkitsune.astral_dice.item.chip/FlashlightBurstChipItem.java | 手电筒爆发(122L)：目标集 + Curios | 需改写 | compat/curios + AttachedDataKey(FLASHLIGHT_BURST_TARGETS 新键) | textures/item/flashlight_burst_chip.png；手册 chips_starlight/flashlight_burst_chip.json |
| 29 | com.merlinkitsune.astral_dice.item.chip/FortuneStickChipItem.java | com.merlinkitsune.astral_dice.item.chip/FortuneStickChipItem.java | 幸运棍(128L)：死亡事件 + 祝福认领附件 | 需改写 | LoaderBus LivingDeathEvent + AttachedDataKey(FORTUNE_STICK_BLESSING_CLAIMED) | textures/item/fortune_stick_chip.png；手册 chips_starlight/fortune_stick_chip.json |
| 30 | com.merlinkitsune.astral_dice.item.chip/InfiniteGridChipItem.java | com.merlinkitsune.astral_dice.item.chip/InfiniteGridChipItem.java | 无限网格筹码(99L) | 需改写 | compat/curios shim（仅换 import） | textures/item/infinite_grid_chip.png；手册 chips_charge/infinite_grid_chip.json |
| 31 | com.merlinkitsune.astral_dice.item.chip/SensorChipItem.java | com.merlinkitsune.astral_dice.item.chip/SensorChipItem.java | 传感器筹码(76L) | 需改写 | compat/curios shim（仅换 import） | textures/item/sensor_chip.png；手册 chips_mark/sensor_chip.json |
| 32 | com.merlinkitsune.astral_dice.recipe.condition/ChipRecipesEnabledCondition.java | com.merlinkitsune.astral_dice.recipe.condition/ChipRecipesEnabledCondition.java | 筹码配方开关条件(66L) | 需重写 | NeoForge ICondition + NeoForgeRegistries.CONDITION_SERIALIZERS 在 Fabric 1.20.1 无对应；改为数据生成侧条件输出 + 运行时 TagKey 门控 | data/astral_dice/recipes/*.json 17→14 个（缺 3 个配方） |
| 33 | com.merlinkitsune.astral_dice.item.EventDrawItem.java | com.merlinkitsune.astral_dice.item.EventDrawItem.java | 抽奖盒物品(72L)：使用出 event_ticket | 需改写 | Item#use 同签名；PacketDistributor→SimpleChannel | textures/item/event_drawer.png、event_ticket.png；手册 materials/event_ticket.json |

小计：33 类。

### 1.2 组二：事件体系（33 类）

| # | 源类 FQN（neoforge-1.21.1） | fabric 目标 FQN | 功能 | 移植等级 | 平台能力依赖 | 所需资源 |
|---|---|---|---|---|---|---|
| 34 | com.merlinkitsune.astral_dice.choice.CardChoiceManager.java | com.merlinkitsune.astral_dice.choice.CardChoiceManager.java | 卡牌二选一会话管理(275L)：LivingDeath/PlayerEvent + 发包 | 需改写 | LoaderBus(LivingDeathEvent、PlayerEvent.Clone) + SimpleChannel | 无 |
| 35 | com.merlinkitsune.astral_dice.choice.CardChoiceType.java | com.merlinkitsune.astral_dice.choice.CardChoiceType.java | 二选一的卡牌类型标签口径(50L) | 原样搬 | 无平台依赖 | 无 |
| 36 | com.merlinkitsune.astral_dice.screen.CardChoiceMenu.java | com.merlinkitsune.astral_dice.screen.CardChoiceMenu.java | 二选一容器菜单(264L) | 需改写 | MenuType(platform DeferredRegister) | 无 |
| 37 | com.merlinkitsune.astral_dice.screen.CardChoiceScreen.java | com.merlinkitsune.astral_dice.screen.CardChoiceScreen.java | 二选一界面(610L) | 需改写 | AbstractContainerScreen 1.20.1 签名 + SimpleChannel | textures/gui/ 下二选一底图（含在 177 张缺口内） |
| 38 | com.merlinkitsune.astral_dice.client.CardChoiceClientHandler.java | com.merlinkitsune.astral_dice.client.CardChoiceClientHandler.java | 客户端收 C2S/S2C 二选一包 | 需改写 | SimpleChannel.handle 注册 | 无 |
| 39 | com.merlinkitsune.astral_dice.client.EventChoiceClientHandler.java | com.merlinkitsune.astral_dice.client.EventChoiceClientHandler.java | 事件选择客户端状态 | 需改写 | SimpleChannel | 无 |
| 40 | com.merlinkitsune.astral_dice.client.EventChoiceScreen.java | com.merlinkitsune.astral_dice.client.EventChoiceScreen.java | 事件选择界面(357L) | 需改写 | Screen + PacketDistributor→CHANNEL | textures/gui/event/* 缺 18 张 |
| 41 | com.merlinkitsune.astral_dice.client.EventCardOverlay.java | com.merlinkitsune.astral_dice.client.EventCardOverlay.java | 事件卡 HUD 覆盖层(288L) | 需改写 | 1.21.1 ILayeredDraw.Layer → fabric RegisterGuiOverlaysEvent + platform/client/gui/overlay/ForgeGui | textures/gui/event/ 覆盖层贴图 |
| 42 | com.merlinkitsune.astral_dice.event.api/AstralEvent.java | com.merlinkitsune.astral_dice.event.api/AstralEvent.java | 事件数据 record | 原样搬 | 无平台依赖 | 无 |
| 43 | com.merlinkitsune.astral_dice.event.api/AstralEventEffect.java | com.merlinkitsune.astral_dice.event.api/AstralEventEffect.java | 事件效果接口 | 原样搬 | 无 | 无 |
| 44 | com.merlinkitsune.astral_dice.event.api/AstralEventPolarity.java | com.merlinkitsune.astral_dice.event.api/AstralEventPolarity.java | 事件极性枚举 | 原样搬 | 无 | 无 |
| 45 | com.merlinkitsune.astral_dice.event.api/AstralEventTier.java | com.merlinkitsune.astral_dice.event.api/AstralEventTier.java | 事件阶级枚举 | 原样搬 | 无 | 无 |
| 46 | com.merlinkitsune.astral_dice.event.api/AstralEventTrigger.java | com.merlinkitsune.astral_dice.event.api/AstralEventTrigger.java | 触发方式枚举 | 原样搬 | 无 | 无 |
| 47 | com.merlinkitsune.astral_dice.event.AstralEventDispatcher.java | com.merlinkitsune.astral_dice.event.AstralEventDispatcher.java | 事件派发(169L) | 原样搬 | 无平台 import（走自有 API 类型） | 无 |
| 48 | com.merlinkitsune.astral_dice.event.AstralEventIds.java | com.merlinkitsune.astral_dice.event.AstralEventIds.java | 13 条事件 id 常量(40L) | 原样搬 | 无；但每条需三语 name 键 | lang：event.astral_dice.*.name ×3 语 |
| 49 | com.merlinkitsune.astral_dice.event.AstralEventRegistry.java | com.merlinkitsune.astral_dice.event.AstralEventRegistry.java | 事件注册表(224L) | 原样搬 | 无平台 import | 无 |
| 50 | com.merlinkitsune.astral_dice.event.BarricadeAggroHandler.java | com.merlinkitsune.astral_dice.event.BarricadeAggroHandler.java | 路障拉仇恨(71L，LivingChangeTargetEvent) | 需改写 | LoaderBus(entity/living/LivingChangeTargetEvent 已存在) | 无 |
| 51 | com.merlinkitsune.astral_dice.event.CurioDeathKeepHandler.java | com.merlinkitsune.astral_dice.event.CurioDeathKeepHandler.java | 饰品死亡保留(87L，LivingDropsEvent+PlayerEvent.Clone) | 需改写 | LoaderBus（两事件 fabric 均已具备）+ component/DeathPreservedBonuses 新键 | 无 |
| 52 | com.merlinkitsune.astral_dice.event.DorothyWarmthHealContext.java | com.merlinkitsune.astral_dice.event.DorothyWarmthHealContext.java | 治疗来源 ThreadLocal(79L) | 原样搬 | 无平台 import；但**必须**配套 mixin/HealOrHarmMobEffectMixin 才有效 | 无 |
| 53 | com.merlinkitsune.astral_dice.event.EventAnimationLock.java | com.merlinkitsune.astral_dice.event.EventAnimationLock.java | 事件动画锁(135L)：LivingDeath+PlayerEvent+包 | 需改写 | LoaderBus + SimpleChannel | 无 |
| 54 | com.merlinkitsune.astral_dice.event.EventCardService.java | com.merlinkitsune.astral_dice.event.EventCardService.java | 事件卡服务(128L)：发包 | 需改写 | SimpleChannel(CardShop/EventCard 消息) | 无 |
| 55 | com.merlinkitsune.astral_dice.event.EventChoiceManager.java | com.merlinkitsune.astral_dice.event.EventChoiceManager.java | 事件选择会话(309L)：PlayerTickEvent 等 | 需改写 | TickEvent(PlayerTick)已有；SimpleChannel | 无 |
| 56 | com.merlinkitsune.astral_dice.event.EventEffects.java | com.merlinkitsune.astral_dice.event.EventEffects.java | 事件效果表(611L) | 原样搬 | 无平台 import | effect 图标见 §4（mob_effect 缺 15 张） |
| 57 | com.merlinkitsune.astral_dice.event.EventTimerStore.java | com.merlinkitsune.astral_dice.event.EventTimerStore.java | 事件计时存储(159L) | 原样搬 | 无平台 import（如落附件则需 AttachedDataKey） | 无 |
| 58 | com.merlinkitsune.astral_dice.event.GlobalEventGate.java | com.merlinkitsune.astral_dice.event.GlobalEventGate.java | 全局事件闸门(164L) | 需改写 | LoaderBus + SimpleChannel | 无 |
| 59 | com.merlinkitsune.astral_dice.event.InvestigationArbitration.java | com.merlinkitsune.astral_dice.event.InvestigationArbitration.java | 调查仲裁(116L，CuriosApi) | 需改写 | compat/curios/CuriosApi 换 import | 无 |
| 60 | com.merlinkitsune.astral_dice.event.IronSpellbooksCompat.java | com.merlinkitsune.astral_dice.event.IronSpellbooksCompat.java | 铁之咒术兼容(29L，@SubscribeEvent) | 需改写 | LoaderBus；软依赖需 ModCompatibilityCheck 的 present 判定 | 无 |
| 61 | com.merlinkitsune.astral_dice.event.LinglingSignEvents.java | com.merlinkitsune.astral_dice.event.LinglingSignEvents.java | 灵灵立牌拾取/丢弃事件(77L) | 需改写 | LoaderBus(ItemTossEvent、EntityItemPickupEvent 均已具备) | 无 |
| 62 | com.merlinkitsune.astral_dice.event.ModEntityEvents.java | com.merlinkitsune.astral_dice.event.ModEntityEvents.java | EntityAttributeCreationEvent 建属性(45L) | 需改写 | Fabric 无 EntityAttributeCreationEvent → FabricDefaultAttributeRegistry.register(...)（并进 AstralDiceMod.onInitialize） | 无 |
| 63 | com.merlinkitsune.astral_dice.event.NewSignEvents.java | com.merlinkitsune.astral_dice.event.NewSignEvents.java | 立牌事件(121L)：LivingChangeTarget/LivingHeal/MobEffectEvent.Applicable | 需改写 | LoaderBus（MobEffectEvent.Applicable 在 platform 已有 :98） | 无 |
| 64 | com.merlinkitsune.astral_dice.event.PlayerEffectProtection.java | com.merlinkitsune.astral_dice.event.PlayerEffectProtection.java | 玩家效果保护(205L) | 原样搬 | 无平台 import（如接入 ModEffectEvent 则用 platform 同名事件） | 无 |
| 65 | com.merlinkitsune.astral_dice.event.RepulsionCreeperGuard.java | com.merlinkitsune.astral_dice.event.RepulsionCreeperGuard.java | 苦力怕爆炸保护拒止体(97L，ExplosionEvent.Start) | 需改写 | **fabric platform 无 ExplosionEvent** ⇒ 需新增 event 类 + level 级 bridge mixin（Explosion#explode HEAD） | 无 |
| 66 | com.merlinkitsune.astral_dice.event.RepulsionTeleportGuard.java | com.merlinkitsune.astral_dice.event.RepulsionTeleportGuard.java | 末影随机传送保护(56L，EntityTeleportEvent.EnderEntity) | 需改写 | platform/event/entity/EntityTeleportEvent 已存在；需确认含 EnderEntity 子类语义（见附录 B） | 无 |

小计：33 类（累计 66）。

### 1.3 组三：实体与效果牌（17 类）

| # | 源类 FQN（neoforge-1.21.1） | fabric 目标 FQN | 功能 | 移植等级 | 平台能力依赖 | 所需资源 |
|---|---|---|---|---|---|---|
| 67 | com.merlinkitsune.astral_dice.entity.BarricadeEntity.java | com.merlinkitsune.astral_dice.entity.BarricadeEntity.java | 路障实体(447L extends LivingEntity) | 需重写 | EntityType.Builder 注册 + FabricDefaultAttributeRegistry + EntityRendererRegistry；跨线属性用 platform/registry | textures/entity/barricade.png + 模型层 |
| 68 | com.merlinkitsune.astral_dice.entity.ModEntityTypes.java | com.merlinkitsune.astral_dice.entity.ModEntityTypes.java | 实体类型 DeferredRegister(57L) | 需改写 | DeferredRegister(Registries.ENTITY_TYPE)；DeferredHolder→RegistryObject | 无 |
| 69 | com.merlinkitsune.astral_dice.entity.RepulsionEntity.java | com.merlinkitsune.astral_dice.entity.RepulsionEntity.java | 拒止实体(1115L) | 需重写 | 同 BarricadeEntity 全套；含自定义渲染与落点逻辑 | textures/entity/repulsion.png |
| 70 | com.merlinkitsune.astral_dice.block.BarricadeBlock.java | com.merlinkitsune.astral_dice.block.BarricadeBlock.java | 路障隐形碰撞方块(99L，无 BlockItem) | 需改写 | DeferredRegister(BLOCK)；1.20.1 的 getShape/isPathfindable/getRenderShape 签名逐个复核 | models/block/barricade_collision.json、blockstates/barricade_collision.json |
| 71 | com.merlinkitsune.astral_dice.block.ModBlocks.java | com.merlinkitsune.astral_dice.block.ModBlocks.java | 方块注册表(56L) | 需改写 | DeferredRegister.create(Registries.BLOCK,MODID) + 手工 BlockItem 注册（1.20.1 无 registerSimpleBlockItem） | textures/block/* 缺 5 张 |
| 72 | com.merlinkitsune.astral_dice.item.card/BarricadeCardItem.java | com.merlinkitsune.astral_dice.item.card/BarricadeCardItem.java | 路障效果牌(176L) | 原样搬 | 无平台 import（依赖 RepulsionPlacement / 放点判据） | textures/item/effect_card_barricade.png；lang tooltip；手册 cards_effect/effect_card_barricade.json |
| 73 | com.merlinkitsune.astral_dice.item.card/RepulsionCardItem.java | com.merlinkitsune.astral_dice.item.card/RepulsionCardItem.java | 拒止效果牌(166L) | 原样搬 | 无平台 import | textures/item/effect_card_repulsion.png；手册 cards_effect/effect_card_repulsion.json |
| 74 | com.merlinkitsune.astral_dice.item.RepulsionItem.java | com.merlinkitsune.astral_dice.item.RepulsionItem.java | 强化拒止物品(203L)：DataComponent 三态 | 需重写 | 1.20.1 无 DataComponent API → 迁到 fabric 已有 component/ModDataComponents(ItemDataKey NBT shim)，三态语义逐条迁移 | textures/item/repulsion.png；lang 三态 tooltip |
| 75 | com.merlinkitsune.astral_dice.item.RepulsionPlacement.java | com.merlinkitsune.astral_dice.item.RepulsionPlacement.java | 唯一落点判据(73L) | 原样搬 | 无平台 import | 无 |
| 76 | com.merlinkitsune.astral_dice.client.BarricadeModel.java | com.merlinkitsune.astral_dice.client.BarricadeModel.java | 路障模型 | 需改写 | 1.20.1 EntityModel(ModelPart) 构造 + ModelLayerLocation 注册（EntityModelLayerRegistry） | 无 |
| 77 | com.merlinkitsune.astral_dice.client.BarricadeRenderer.java | com.merlinkitsune.astral_dice.client.BarricadeRenderer.java | 路障渲染器 | 需改写 | EntityRenderer 构造签名 + EntityRendererRegistry；fabric 不需要 EntityRendererInvoker | textures/entity/barricade.png |
| 78 | com.merlinkitsune.astral_dice.client.RepulsionModel.java | com.merlinkitsune.astral_dice.client.RepulsionModel.java | 拒止模型 | 需改写 | 同上 | 无 |
| 79 | com.merlinkitsune.astral_dice.client.RepulsionRenderer.java | com.merlinkitsune.astral_dice.client.RepulsionRenderer.java | 拒止渲染器 | 需改写 | 同上 | textures/entity/repulsion.png |
| 80 | com.merlinkitsune.astral_dice.combat.DeathTriggerGuard.java | com.merlinkitsune.astral_dice.combat.DeathTriggerGuard.java | 死亡触发守卫(98L) | 原样搬 | 无平台 import | 无 |
| 81 | com.merlinkitsune.astral_dice.combat.ElectricShockFx.java | com.merlinkitsune.astral_dice.combat.ElectricShockFx.java | 电击特效(83L) | 原样搬 | 无平台 import | 无 |
| 82 | com.merlinkitsune.astral_dice.network.BeamFxPayload.java | com.merlinkitsune.astral_dice.network.BeamFxPayload.java | 光束特效包(54L) | 需改写 | SimpleChannel 内部类 + encode/decode/handle | 无 |
| 83 | com.merlinkitsune.astral_dice.client.BeamFxRenderer.java | com.merlinkitsune.astral_dice.client.BeamFxRenderer.java | 光束渲染(249L，RenderLevelStageEvent) | 需改写 | platform/client/event/RenderLevelStageEvent 类已存在，是否有 bridge 实际派发待核（附录 B） | 无 |

小计：17 类（累计 83）。

### 1.4 组四：立牌（19 类）

| # | 源类 FQN（neoforge-1.21.1） | fabric 目标 FQN | 功能 | 移植等级 | 平台能力依赖 | 所需资源 |
|---|---|---|---|---|---|---|
| 84 | com.merlinkitsune.astral_dice.item.sign/AlanaSignItem.java | com.merlinkitsune.astral_dice.item.sign/AlanaSignItem.java | Alana 立牌(92L)：饰品槽被动 + 攻击加成 | 需改写 | compat/curios/CuriosApi 换 import | textures/item/alana_sign.png；三语 lang；手册 signs/alana_sign.json |
| 85 | com.merlinkitsune.astral_dice.item.sign/AmeSignItem.java | com.merlinkitsune.astral_dice.item.sign/AmeSignItem.java | Ame 立牌(425L)：爱之层数镜像 + LivingDamage/MobEffectEvent | 需改写 | LoaderBus(LivingDamageEvent、MobEffectEvent) + compat/curios | textures/item/ame_sign.png；手册 signs/ame_sign.json |
| 86 | com.merlinkitsune.astral_dice.item.sign/DeepseekSignItem.java | com.merlinkitsune.astral_dice.item.sign/DeepseekSignItem.java | Deepseek 立牌(190L)：钓鱼被动 + SlotContext | 需改写 | compat/curios(SlotContext) 换 import | textures/item/deepseek_sign.png；手册 signs/deepseek_sign.json |
| 87 | com.merlinkitsune.astral_dice.item.sign/DorothySignItem.java | com.merlinkitsune.astral_dice.item.sign/DorothySignItem.java | Dorothy 立牌(253L)：温暖/社交名流被动 | 需改写 | compat/curios(SlotContext)；DorothyWarmthHealContext 依赖 mixin | textures/item/dorothy_sign.png；手册 signs/dorothy_sign.json |
| 88 | com.merlinkitsune.astral_dice.item.sign/JillSignItem.java | com.merlinkitsune.astral_dice.item.sign/JillSignItem.java | Jill 立牌(252L)：混合被动 | 需改写 | compat/curios(SlotContext) | textures/item/jill_sign.png；手册 signs/jill_sign.json |
| 89 | com.merlinkitsune.astral_dice.item.sign/KangelSignItem.java | com.merlinkitsune.astral_dice.item.sign/KangelSignItem.java | Kangel 立牌(389L)：粉丝层数 + LivingDamage | 需改写 | LoaderBus(LivingDamageEvent) + compat/curios | textures/item/kangel_sign.png；手册 signs/kangel_sign.json |
| 90 | com.merlinkitsune.astral_dice.item.sign/LinglingSignItem.java | com.merlinkitsune.astral_dice.item.sign/LinglingSignItem.java | 灵灵立牌(71L) | 需改写 | LoaderBus(SubscribeEvent 处理器) | textures/item/lingling_sign.png；手册 signs/lingling_sign.json |
| 91 | com.merlinkitsune.astral_dice.item.sign/LinglingSignManager.java | com.merlinkitsune.astral_dice.item.sign/LinglingSignManager.java | 灵灵立牌授予管理(477L) | 需改写 | compat/curios(CuriosApi) + AttachedDataKey(LINGLING_GRANTED 新键) | 无 |
| 92 | com.merlinkitsune.astral_dice.item.sign/MosesEnigmaticLink.java | com.merlinkitsune.astral_dice.item.sign/MosesEnigmaticLink.java | Moses 神秘遗物联动(380L)：LivingDeath + ServerTickEvent | 需改写 | LoaderBus(TickEvent.ServerTick、LivingDeathEvent) | 无 |
| 93 | com.merlinkitsune.astral_dice.item.sign/SignActionBar.java | com.merlinkitsune.astral_dice.item.sign/SignActionBar.java | 立牌 actionbar 提示(61L) | 原样搬 | 无平台 import（msg/actionbar 走既有通道） | lang：msg.astral_dice.* 缺 52 键 |
| 94 | com.merlinkitsune.astral_dice.item.sign/SignActiveLockMachine.java | com.merlinkitsune.astral_dice.item.sign/SignActiveLockMachine.java | 主动锁定状态机(214L) | 原样搬 | 无平台 import | 无 |
| 95 | com.merlinkitsune.astral_dice.item.sign/SignAttributeUtil.java | com.merlinkitsune.astral_dice.item.sign/SignAttributeUtil.java | 立牌属性工具(50L) | 原样搬 | 无平台 import | 无 |
| 96 | com.merlinkitsune.astral_dice.item.sign/SignEquipCheck.java | com.merlinkitsune.astral_dice.item.sign/SignEquipCheck.java | 立牌佩戴判定(37L) | 需改写 | compat/curios(CuriosApi) | 无 |
| 97 | com.merlinkitsune.astral_dice.item.sign/SignHookDispatcher.java | com.merlinkitsune.astral_dice.item.sign/SignHookDispatcher.java | 立牌钩子派发(177L) | **已物理删除（不再搬）** | fabric 线已移除该文件与 SignHooks（B3 死代码清理，见 `B3-SIGNHOOK-DEADCODE-REPORT.md`）；本行仅作历史对照 | 无 |
| 98 | com.merlinkitsune.astral_dice.item.sign/SignHooks.java | com.merlinkitsune.astral_dice.item.sign/SignHooks.java | 立牌钩子接口(72L) | **已物理删除（不再搬）** | 同上；fabric 现行 `grep -rn SignHookDispatcher fabric-1.20.1/src` 与 `grep -rn SignHooks fabric-1.20.1/src` 命中均为 0 | 无 |
| 99 | com.merlinkitsune.astral_dice.component.SignState.java | com.merlinkitsune.astral_dice.component.SignState.java | 立牌状态 record(152L) | 原样搬 | 无平台 import | 无 |
| 100 | com.merlinkitsune.astral_dice.component.SignTimerSourceAdapter.java | com.merlinkitsune.astral_dice.component.SignTimerSourceAdapter.java | 立牌计时来源适配(41L) | 原样搬 | 无平台 import | 无 |
| 101 | com.merlinkitsune.astral_dice.mixin.client/SignTranslucencyMixin.java | com.merlinkitsune.astral_dice.mixin.client/SignTranslucencyMixin.java | 立牌半透明渲染(82L，@Mixin(LivingEntityRenderer)) | 需改写 | 需新增 mixin 条目到 astral_dice.mixins.json client 组；1.20.1 的 getRenderType/render 签名复核 + audit_mixin_injection 守门 | 无 |
| 102 | com.merlinkitsune.astral_dice.mixin.client/EntityRendererInvoker.java | com.merlinkitsune.astral_dice.mixin.client/EntityRendererInvoker.java | @Invoker 暴露 getTextureLocation(15L) | 不搬 | 理由：1.20.1 EntityRenderer#getTextureLocation 本就可覆写/调用，fabric 自写渲染器无需 Invoker 混入 | 无 |

小计：19 类（累计 102）。

### 1.5 组五：角色效果（15 类）

| # | 源类 FQN（neoforge-1.21.1） | fabric 目标 FQN | 功能 | 移植等级 | 平台能力依赖 | 所需资源 |
|---|---|---|---|---|---|---|
| 103 | com.merlinkitsune.astral_dice.effect.AlanaIronMaidenEffect.java | com.merlinkitsune.astral_dice.effect.AlanaIronMaidenEffect.java | 铁处女效果 | 原样搬 | 无平台 import（extends MobEffect，1.20.1 构造签名同形）；需在 effect/ModEffects.java 增常量 + 注册 | textures/mob_effect/alana_iron_maiden.png（缺）+ 三语 effect.astral_dice.alana_iron_maiden.name/description |
| 104 | com.merlinkitsune.astral_dice.effect.AmeLoveEffect.java | com.merlinkitsune.astral_dice.effect.AmeLoveEffect.java | 爱之层数镜像(63L，已实测 forge 版同构 static mirror/clear) | 原样搬 | 无平台 import（extends MobEffect，1.20.1 构造签名同形）；需在 effect/ModEffects.java 增常量 + 注册 | textures/mob_effect/ame_love.png（缺）+ 三语 effect.astral_dice.ame_love.name/description |
| 105 | com.merlinkitsune.astral_dice.effect.AmeOverdoseEffect.java | com.merlinkitsune.astral_dice.effect.AmeOverdoseEffect.java | 爱过量 | 原样搬 | 无平台 import（extends MobEffect，1.20.1 构造签名同形）；需在 effect/ModEffects.java 增常量 + 注册 | textures/mob_effect/ame_overdose.png（缺）+ 三语 effect.astral_dice.ame_overdose.name/description |
| 106 | com.merlinkitsune.astral_dice.effect.DeepseekFishingEffect.java | com.merlinkitsune.astral_dice.effect.DeepseekFishingEffect.java | Deepseek 钓鱼 | 原样搬 | 无平台 import（extends MobEffect，1.20.1 构造签名同形）；需在 effect/ModEffects.java 增常量 + 注册 | textures/mob_effect/deepseek_fishing.png（缺）+ 三语 effect.astral_dice.deepseek_fishing.name/description |
| 107 | com.merlinkitsune.astral_dice.effect.DorothySocialiteEffect.java | com.merlinkitsune.astral_dice.effect.DorothySocialiteEffect.java | 社交名流(31L) | 原样搬 | 无平台 import（extends MobEffect，1.20.1 构造签名同形）；需在 effect/ModEffects.java 增常量 + 注册 | textures/mob_effect/dorothy_socialite.png（缺）+ 三语 effect.astral_dice.dorothy_socialite.name/description |
| 108 | com.merlinkitsune.astral_dice.effect.DorothyWarmthEffect.java | com.merlinkitsune.astral_dice.effect.DorothyWarmthEffect.java | 温暖(19L) | 原样搬 | 无平台 import（extends MobEffect，1.20.1 构造签名同形）；需在 effect/ModEffects.java 增常量 + 注册 | textures/mob_effect/dorothy_warmth.png（缺）+ 三语 effect.astral_dice.dorothy_warmth.name/description |
| 109 | com.merlinkitsune.astral_dice.effect.JillMixedEffect.java | com.merlinkitsune.astral_dice.effect.JillMixedEffect.java | Jill 混合(16L) | 原样搬 | 无平台 import（extends MobEffect，1.20.1 构造签名同形）；需在 effect/ModEffects.java 增常量 + 注册 | textures/mob_effect/jill_mixed.png（缺）+ 三语 effect.astral_dice.jill_mixed.name/description |
| 110 | com.merlinkitsune.astral_dice.effect.KangelFanEffect.java | com.merlinkitsune.astral_dice.effect.KangelFanEffect.java | Kangel 粉丝(56L) | 原样搬 | 无平台 import（extends MobEffect，1.20.1 构造签名同形）；需在 effect/ModEffects.java 增常量 + 注册 | textures/mob_effect/kangel_fan.png（缺）+ 三语 effect.astral_dice.kangel_fan.name/description |
| 111 | com.merlinkitsune.astral_dice.effect.KangelFanIndicatorEffect.java | com.merlinkitsune.astral_dice.effect.KangelFanIndicatorEffect.java | 粉丝指示器(69L) | 原样搬 | 无平台 import（extends MobEffect，1.20.1 构造签名同形）；需在 effect/ModEffects.java 增常量 + 注册 | textures/mob_effect/kangel_fan_indicator.png（缺）+ 三语 effect.astral_dice.kangel_fan_indicator.name/description |
| 112 | com.merlinkitsune.astral_dice.effect.LinglingSurgeEffect.java | com.merlinkitsune.astral_dice.effect.LinglingSurgeEffect.java | 灵灵涌动(26L) | 原样搬 | 无平台 import（extends MobEffect，1.20.1 构造签名同形）；需在 effect/ModEffects.java 增常量 + 注册 | textures/mob_effect/lingling_surge.png（缺）+ 三语 effect.astral_dice.lingling_surge.name/description |
| 113 | com.merlinkitsune.astral_dice.effect.ProblemDeliveryEffect.java | com.merlinkitsune.astral_dice.effect.ProblemDeliveryEffect.java | 问题投递(21L) | 原样搬 | 无平台 import（extends MobEffect，1.20.1 构造签名同形）；需在 effect/ModEffects.java 增常量 + 注册 | textures/mob_effect/problem_delivery.png（缺）+ 三语 effect.astral_dice.problem_delivery.name/description |
| 114 | com.merlinkitsune.astral_dice.effect.RedHeatWarningEffect.java | com.merlinkitsune.astral_dice.effect.RedHeatWarningEffect.java | 赤热警告(21L) | 原样搬 | 无平台 import（extends MobEffect，1.20.1 构造签名同形）；需在 effect/ModEffects.java 增常量 + 注册 | textures/mob_effect/red_heat_warning.png（缺）+ 三语 effect.astral_dice.red_heat_warning.name/description |
| 115 | com.merlinkitsune.astral_dice.effect.StormApproachingEffect.java | com.merlinkitsune.astral_dice.effect.StormApproachingEffect.java | 风暴将至(21L) | 原样搬 | 无平台 import（extends MobEffect，1.20.1 构造签名同形）；需在 effect/ModEffects.java 增常量 + 注册 | textures/mob_effect/storm_approaching.png（缺）+ 三语 effect.astral_dice.storm_approaching.name/description |
| 116 | com.merlinkitsune.astral_dice.mixin.HealOrHarmMobEffectMixin.java | com.merlinkitsune.astral_dice.mixin.HealOrHarmMobEffectMixin.java | 瞬时治疗来源标记(53L，@Mixin(MobEffect) 注入 applyInstantenousEffect) | 原样搬 | **可从 forge-1.20.1 同名文件逐字复制**（forge 版已按 1.20.1 写：1.20.1 无 HealOrHarmMobEffect，只能注入 MobEffect 判 this==MobEffects.HEAL）；需在 astral_dice.mixins.json 普通组新增条目 | 无 |
| 117 | com.merlinkitsune.astral_dice.mixin.RegenerationMobEffectMixin.java | com.merlinkitsune.astral_dice.mixin.RegenerationMobEffectMixin.java | 生命恢复分层注入(35L) | 需改写 | 1.20.1 RegenerationMobEffect#applyEffectTick 的参数表与 1.21.1 不同，需按 forge-1.20.1 版复核后注册 mixin 条目；跑 audit_mixin_injection.py | 无 |

小计：15 类（累计 117）。

### 1.6 组六：集成与数据（13 类）

| # | 源类 FQN（neoforge-1.21.1） | fabric 目标 FQN | 功能 | 移植等级 | 平台能力依赖 | 所需资源 |
|---|---|---|---|---|---|---|
| 118 | com.merlinkitsune.astral_dice.compat.jei/CardShopCategory.java | com.merlinkitsune.astral_dice.compat.jei/CardShopCategory.java | JEI 卡牌商店配方类别(158L) | 需重写 | 构建接法：fabric 线 **不能** modImplementation JEI（Loom 1.14.10 拒绝 JEI 15.62 的「newer Loom」门禁，见 build.gradle:172-176）⇒ 需手工 compileOnly jar 或延期；API 面 mezz.jei.api 1.20.1 版 | textures/gui/jei/*（在 177 张缺口内） |
| 119 | com.merlinkitsune.astral_dice.compat.jei/CardShopJeiPlugin.java | com.merlinkitsune.astral_dice.compat.jei/CardShopJeiPlugin.java | JEI 插件注册(67L) | 需重写 | 同上；Fabric 走 fabric.mod.json entrypoints 或 @JeiPlugin 扫描（待核，附录 B） | 无 |
| 120 | com.merlinkitsune.astral_dice.compat.jei/CardShopJeiRecipes.java | com.merlinkitsune.astral_dice.compat.jei/CardShopJeiRecipes.java | JEI 配方集合(40L) | 需重写 | 同上 | 无 |
| 121 | com.merlinkitsune.astral_dice.compat.jei/CardShopRecipe.java | com.merlinkitsune.astral_dice.compat.jei/CardShopRecipe.java | JEI 配方 record(20L) | 需重写 | 同上 | 无 |
| 122 | com.merlinkitsune.astral_dice.compat.jei/ChipShopCategory.java | com.merlinkitsune.astral_dice.compat.jei/ChipShopCategory.java | JEI 筹码店类别(167L) | 需重写 | 同上 | 无 |
| 123 | com.merlinkitsune.astral_dice.compat.jei/ChipShopJeiPlugin.java | com.merlinkitsune.astral_dice.compat.jei/ChipShopJeiPlugin.java | JEI 筹码店插件(70L) | 需重写 | 同上 | 无 |
| 124 | com.merlinkitsune.astral_dice.compat.jei/ChipShopJeiRecipes.java | com.merlinkitsune.astral_dice.compat.jei/ChipShopJeiRecipes.java | JEI 筹码店配方集合(40L) | 需重写 | 同上 | 无 |
| 125 | com.merlinkitsune.astral_dice.compat.jei/ChipShopRecipe.java | com.merlinkitsune.astral_dice.compat.jei/ChipShopRecipe.java | JEI 筹码店配方(22L) | 需重写 | 同上 | 无 |
| 126 | com.merlinkitsune.astral_dice.compat.jade/AstralDiceJadePlugin.java | com.merlinkitsune.astral_dice.compat.jade/AstralDiceJadePlugin.java | Jade 插件入口(51L，@WailaPlugin + IWailaPlugin) | 需改写 | Jade 已是 fabric 的 modImplementation（build.gradle:171）；Fabric 侧注册方式需从注解改为 fabric.mod.json entrypoints.jade（待核） | 无 |
| 127 | com.merlinkitsune.astral_dice.compat.jade/EventDrawerCostProvider.java | com.merlinkitsune.astral_dice.compat.jade/EventDrawerCostProvider.java | 抽奖盒花费组件(36L) | 需改写 | snownee.jade.api.BlockAccessor/IBlockComponentProvider 跨加载器同 API，仅注册入口改 | 无 |
| 128 | com.merlinkitsune.astral_dice.compat.jade/ShopCooldownClientProvider.java | com.merlinkitsune.astral_dice.compat.jade/ShopCooldownClientProvider.java | 商店冷却客户端组件(57L) | 需改写 | 同上 | 无 |
| 129 | com.merlinkitsune.astral_dice.datagen.DataGenerators.java | com.merlinkitsune.astral_dice.datagen.DataGenerators.java | GatherDataEvent 数据生成入口(30L) | 不搬 | 理由：fabric 线已有 datagen/AstralDataGenerator.java:28 implements DataGeneratorEntrypoint，入口已存在；需把本类登记的 provider 清单并入该入口，而不是再建一个 GatherDataEvent 入口 | 无 |
| 130 | com.merlinkitsune.astral_dice.item.ModTags.java | com.merlinkitsune.astral_dice.item.ModTags.java | 数据标签常量(36L) | 需改写 | 1.21 的 `ResourceLocation.fromNamespaceAndPath(MODID,"is_exclusive")` 在 1.20.1 无此静态方法 ⇒ 改 `new ResourceLocation(MODID,"is_exclusive")`；TagKey 写法不变 | data/astral_dice/tags/item/is_exclusive.json |

小计：13 类（累计 130）。

### 1.7 平台专属类（不算缺口）及其替代实现

下列类只在某一条线上存在，**不计入 130 缺口**，但必须在 fabric 线有明确落点，否则对应功能会静默缺失。

#### 1.7.1 forge-1.20.1 独有 14 类

| 类（forge 线） | 存在的平台机制 | fabric 落点 | 取证 |
|---|---|---|---|
| `component/AstralData.java` | Forge Capability 定义（数据面） | `component/AttachedDataKey.java`（Fabric API `AttachmentType`） | `forge-1.20.1/.../component/ModAttachments.java:25` 类头：「内部由 AttachedDataKey(AstralData Capability / ForgeData) 承载」；`fabric-1.20.1/.../component/AttachedDataKey.java:3-6` `import net.fabricmc.fabric.api.attachment.v1.{AttachmentRegistry,AttachmentTarget}` |
| `component/ModCapabilities.java` | Capability 注册事件（`RegisterCapabilitiesEvent`） | 无对应；`AttachedDataKey.attachment()` 直接返回已注册附件 | 同上；`fabric.mod.json` depends 含 `fabric-data-attachment-api-v1` |
| `init/MixinRuntimeGate.java` | forge mixin 加载期门禁 | 无对应；Loom 直接应用 mixin（refmap `astral_dice.refmap.json`） | `fabric-1.20.1/src/main/resources/astral_dice.mixins.json` |
| `loot/AddTableLootModifier.java` | Forge GLM（`Global Loot Modifier`） | `loot/FabricLootInjector.java` + `data/.../loot_tables/` 直写；fabric **无** `loot_modifiers/` 目录 | 目录实测：forge 有 `data/astral_dice/loot_modifiers/`（12 个 `star_plate_*.json`），fabric 无；`build.gradle` loom `interfaceInjection` 注释指出 `FabricLootInjector` 依赖 `LootTable.Builder#pool/pools` 注入 |
| `loot/AstralLootModifiers.java` | GLM 序列化器注册 | 同上（无序列化器需求） | 同上 |
| `network/CardChoiceCloseMessage.java` | forge `SimpleChannel` 消息 | `network/ModNetwork.java` 内部消息类（`platform/network/SimpleChannel`） | `forge-1.20.1/.../network/ModNetwork.java:33,43`；`fabric-1.20.1/.../network/ModNetwork.java:32,42` 同构 |
| `network/CardChoiceConfirmMessage.java` | 同上 | 同上 | 同上 |
| `network/EventCardMessage.java` | 同上 | 同上 | 同上 |
| `network/EventChoiceCancelMessage.java` | 同上 | 同上 | 同上 |
| `network/EventChoiceCloseMessage.java` | 同上 | 同上 | 同上 |
| `network/EventChoiceConfirmMessage.java` | 同上 | 同上 | 同上 |
| `network/EventChoiceStartMessage.java` | 同上 | 同上 | 同上 |
| `network/EventChoiceTickMessage.java` | 同上 | 同上 | 同上 |
| `trade/MerchantOfferRawAccess.java` | forge 侧用 accessor 读 `MerchantOffer#getBaseCostA`（forge 的 `getBaseCostA` 只给过滤后值） | 机制等价物是「直接读 `MerchantOffer` 原始字段」——fabric 采用官方映射，落点需核 `astral_dice.accesswidener` 是否已开放字段（**无法判定**，见附录 B-3） | `forge-1.20.1/.../trade/MerchantOfferRawAccess.java` 存在；`fabric-1.20.1/.../trade/EmeraldDiceTrade.java` 为同名功能类，但缺 `isEmeraldToStarCoin/rawCostA/filteredFor`（§3 第 35 项） |

#### 1.7.2 neoforge-1.21.1 独有 31 类

| 类（neo 线） | 存在的平台机制 | fabric 落点 | 取证 |
|---|---|---|---|
| `network/*Payload.java`（24 个）+ `network/ModPayloads.java` | `PayloadRegistrar` + `CustomPacketPayload` record + `StreamCodec` | `platform/network/SimpleChannel` 的内部消息类 + `PacketDistributor.Target` | `neo/.../network/ModPayloads.java:13` `import net.neoforged.neoforge.network.registration.PayloadRegistrar`、`:22` `event.registrar(VersionGate.interopVersion())`；`fabric/.../platform/network/SimpleChannel.java`(172L) `registerMessage(int,Class,..)` |
| `fixes/DamageStackSanitizer.java` | NeoForge 专用伤害栈修复（上游 bug） | 无对应；1.20.1 无该 bug 通道 | 文件名与所在目录 `fixes/`；forge/fabric 两线均无 `fixes/` 目录 |
| `fixes/NeoForgeFixesLog.java` | 同样是 neo 专用修复日志 | 无对应 | 同上 |
| `mixin/fixes/LivingEntityDamageContainerMixin.java` | 修 `LivingEntity` 的 DamageContainer（1.21.1 专有内部结构） | 无对应；1.20.1 无 `DamageContainer` | 同名 mixin 在 neo26 线也不存在（实测四线比对结论） |
| `client/EffectTooltipDescriptions.java` | 客户端 tooltip 描述拼装 | 由 `event/ModTooltipHandler.java` 直接产出（fabric 已有该类，缺的是内容，见 §3 第 8 项） | 两线 `event/ModTooltipHandler.java` 均存在 |
| `compat/jade/ShopCooldownServerDataProvider.java` | Jade 服务端数据提供器（`IServerDataProvider`） | 需在 fabric 的 `compat/jade/` 补同类（缺类为 0 时该行不适用） | 该文件仅 neo 线存在；fabric `compat/jade/` 现有 3 个文件 |
| `mixin/jade/DisplayHelperArmorIconMixin.java` | Jade 护甲图标显示修补 | fabric 改为 `mixin/jade/ArmorElementIconMixin` + `ArmorValueProviderMixin`（两线口径不同：fabric 用 `@Inject` 而非 `@Redirect`） | `git diff --no-index` 对 `mixin/jade/ArmorElementIconMixin.java` 结果为 `+28/-53`（无同名 neo 文件，改走 forge 口径） |

> **注意**：上表 31 类中，24+1 个 `network/*` 已由 fabric 的 `SimpleChannel` 体系整体接管；残余 6 个属于「其它线专有修补」，fabric 的等价落点要么不存在（`fixes/`），要么已在 fabric 线以**不同实现**存在（jade 两个 mixin）。因此 fabric 侧**不需要**为这 31 类新建同名文件，只需在 §5 判据里确认对应**行为**存在。


## 2. 三列 API 映射表

每一小节一张三列表：**NeoForge 1.21.1 → Forge 1.20.1 → Fabric 1.20.1**，附取证路径。行号均为本 HEAD 实测。

### 2.1 事件注册与派发

| 能力 | NeoForge 1.21.1 | Forge 1.20.1 | Fabric 1.20.1（本仓实现） | 取证 |
|---|---|---|---|---|
| 处理器声明 | 类上 `@Mod.EventBusSubscriber(modid = AstralDiceMod.MODID)` + 方法上 `@SubscribeEvent` | 方法上 `net.minecraftforge.eventbus.api.SubscribeEvent`，类需被 `@Mod` 类注册到 `MOD` 总线 | `platform/event/SubscribeEvent.java` + `LoaderBus.INSTANCE.register(X.class)`（**注解不会自动生效**） | `fabric-1.20.1/.../AstralDiceMod.java:43 public static final LoaderBus BUS = LoaderBus.INSTANCE`；`:125-151` 逐类 `LoaderBus.INSTANCE.register(DiceCombatEvents.class)` 等 9 处起 |
| 总线获取 | `@Mod` 类构造注入 `IEventBus` | `AstralDiceMod.java:52 IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus()` | 单例 `LoaderBus.INSTANCE`（`implements IEventBus`） | `fabric-1.20.1/.../platform/event/LoaderBus.java`（31L 起） |
| 优先级 | `@SubscribeEvent(priority = EventPriority.HIGH)` | 同 neo（同名枚举） | `LoaderBus#addListener(EventPriority, boolean receiveCanceled, Class, Consumer)` | `LoaderBus.java` 方法表 |
| 派发 | `NeoForge.EVENT_BUS.post(event)` | `MinecraftForge.EVENT_BUS.post(event)` | `LoaderBus.INSTANCE.post(event)` / `postEvent(Object)` | `LoaderBus.java` |
| 可取消 | `ICancellableEvent` | `@Cancelable` + `event.setCanceled` | 自建 `Cancelable` 接口 + `HasResult` | `fabric-1.20.1/.../platform/event/{Cancelable,HasResult}.java` |
| 内省/审计 | — | — | `registeredOwners()` / `dispatchReport()` / `SubscriptionAudit` | `LoaderBus.java` |
| 爆炸事件 | `ExplosionEvent.Start` | `ExplosionEvent.Start` | **无此类**，需新增事件类 + 苦力怕爆炸 bridge mixin | `fabric-1.20.1/.../platform/event/` 目录清单无 ExplosionEvent（130 缺口表 §1.2 `RepulsionCreeperGuard` 依赖此项） |
| 实体属性事件 | `EntityAttributeCreationEvent` | `EntityAttributeCreationEvent` | **无事件**，改用 `FabricDefaultAttributeRegistry.register(type, builder)` | `grep -rn FabricDefaultAttributeRegistry fabric-1.20.1/src/main/java` = 0 命中（尚未接） |
| 村民交易事件 | `VillagerTradesEvent` | `VillagerTradesEvent` | **无事件**，需新增事件类 + `Villager`/`MerchantOffers` bridge mixin | `fabric-1.20.1/.../platform/event/` 无此类 |
| 已可用的实体/玩家事件面 | — | — | `TickEvent`、`LootTableLoadEvent`、`OnDatapackSyncEvent`、`RegisterCommandsEvent`、`AnvilUpdateEvent`；`entity/living/*`（LivingAttack/LivingDamage/LivingDeath/LivingDrops/LivingHurt/LivingHeal/LivingChangeTarget/LivingUseTotem/LivingEntityUseItem/MobEffectEvent）；`entity/player/*`（PlayerEvent 含 Clone:215 / PlayerLoggedInEvent:453 / PlayerLoggedOutEvent:460 / PlayerRespawnEvent:467 / PlayerChangedDimensionEvent:488 / ItemCraftedEvent:414 / ItemPickupEvent:389） | `fabric-1.20.1/.../platform/event/**` 实测文件清单 |

### 2.2 网络 payload

| 能力 | NeoForge 1.21.1 | Forge 1.20.1 | Fabric 1.20.1（本仓实现） | 取证 |
|---|---|---|---|---|
| 通道 | `RegisterPayloadHandlersEvent` + `PayloadRegistrar` | `NetworkRegistry.newSimpleChannel(...)` | `NetworkRegistry.newSimpleChannel(ResourceLocation, ...)`（自实现） | neo `network/ModPayloads.java:13 import net.neoforged.neoforge.network.registration.PayloadRegistrar`、`:22 PayloadRegistrar registrar = event.registrar(VersionGate.interopVersion())`；forge `network/ModNetwork.java:33`；fabric `network/ModNetwork.java:32` |
| 消息体 | `record X(...) implements CustomPacketPayload` + `StreamCodec`（独立文件） | 静态内部类 + `encode/decode` + `handle(Supplier<NetworkEvent.Context>)` | 静态内部类（**同 forge 形态**） | forge `ModNetwork.java:43 CHANNEL.registerMessage(id++, DamageNumberMessage.class, ...)`；fabric `ModNetwork.java:42` 同构；`platform/network/SimpleChannel.java`（172L） |
| 注册 API | `registrar.playToServer/playToClient(type, codec, handler)` | `CHANNEL.registerMessage(id, clazz, encoder, decoder, handler)` | `SimpleChannel#registerMessage(int messageId, Class type, ...)` | `SimpleChannel.java`（172L） |
| 发送 | `PacketDistributor.sendToPlayer/…` 或 `context.reply` | `CHANNEL.send(PacketDistributor.<target>, msg)` | `SimpleChannel#send(PacketDistributor.Target, Object)` / `sendToServer(Object)` | `SimpleChannel.java`；`fabric/ModNetwork.java` 包装方法 `sendToPlayer/sendToPlayersTrackingEntity/sendToAllPlayers/sendToServer/syncAttachment(key,nbt)/syncSnapshot` |
| 目标枚举 | — | `PacketDistributor.PLAYER/TRACKING_ENTITY/TRACKING_ENTITY_AND_SELF/ALL` | 同 forge 四常量（自实现 `PacketDistributor.Target`） | `platform/network/` 包 |
| 版本互通号 | `VersionGate.interopVersion()` 传给 registrar | 同上（`ModNetwork.java` 通道版本串） | 同上（`VersionGate.interopVersion()`） | `fabric-1.20.1/.../network/VersionGate.java:41-42` 读 `/astral_dice_version.properties`；`:62 public static String interopVersion()` |
| 附件同步 | `sync(handler)` 由附件系统自动 | 手写 `AttachmentSyncMessage` | 手写 `ModNetwork.syncAttachment(key, nbtTag)` | `fabric/ModNetwork.java`（558L，含 `AttachmentSyncMessage/DamageNumberMessage/ActionBarMessage` 三个内部类） |

### 2.3 附件 / 持久化

| 能力 | NeoForge 1.21.1 | Forge 1.20.1 | Fabric 1.20.1（本仓实现） | 取证 |
|---|---|---|---|---|
| 类型注册 | `DeferredRegister<AttachmentType<?>>(NeoForgeRegistries.ATTACHMENT_TYPES)` | Capability：`Component`/`ICapabilityProvider`（`AstralData`） | `AttachedDataKey` + Fabric API `AttachmentRegistry.create(builder)` | neo `component/ModAttachments.java:29-30`、`:32 DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> PLAYER_STARLIGHT`；forge `component/ModAttachments.java:25` 类头注释；fabric `component/AttachedDataKey.java:3-6 import net.fabricmc.fabric.api.attachment.v1.{AttachmentRegistry,AttachmentTarget}` |
| 序列化 | `AttachmentType.builder(supplier).serialize(codec)` | `Capability` + `INBTSerializable`/`CompoundTag` | `builder(String name, Codec, Supplier)` + `readRawTag/snapshot` | `AttachedDataKey.java`（205L） |
| 同步 | `.sync(handler)` | 手写网络消息 | `Builder.sync()` + `ModNetwork.syncAttachment(key,nbt)` | `AttachedDataKey.java` Builder；`ModNetwork.java` |
| 死亡保留 | `copyOnDeath()` | `PlayerEvent.Clone` 手工拷贝 | `Builder.copyOnDeath()` + `component/DeathPreservedBonuses` | `AttachedDataKey.java` |
| 内存临时 | `inMemory()` | 非持久 Capability | `Builder.inMemory()` | `AttachedDataKey.java` |
| 读写 | `entity.getData(KEY)` / `setData` | `entity.getCapability(...).orElseThrow()`（forge 侧要 `.get()` 取值） | `key.get(LivingEntity)` / `key.set(LivingEntity, v)` | `AttachedDataKey.java` |
| 数据组件（1.21 概念） | `DeferredRegister<DataComponentType<?>>` + `.component(...)` | **无**（`ItemStack` NBT / Capability） | `component/ModDataComponents.java`（`starengine ItemDataKey` = ItemStack NBT shim） | neo `item/ModItems.java:290 .component(ModDataComponents.CARD_USES.get(), …, )`；fabric `ModDataComponents.java` 类头注释 |

### 2.4 注册表与 RegistryObject

| 能力 | NeoForge 1.21.1 | Forge 1.20.1 | Fabric 1.20.1（本仓实现） | 取证 |
|---|---|---|---|---|
| 物品注册 | `DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID)` | `DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID)` | `DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, MODID)` | neo `item/ModItems.java:119`；forge `:121`；fabric `item/ModItems.java:110` |
| 方块注册 | `DeferredRegister.Blocks` + `DeferredBlock<Block>`（NeoForge 专有） | `DeferredRegister.create(ForgeRegistries.BLOCKS, MODID)` + `RegistryObject<Block>` | `DeferredRegister.create(Registries.BLOCK, MODID)` + `RegistryObject<Block>` | forge `block/ModBlocks.java` 类头注释明确写「1.21.1 的 DeferredRegister.Blocks/DeferredBlock 是 NeoForge 专有」 |
| 方块物品 | `registerSimpleBlockItem(...)` | **无**，手工 `ITEMS.register(name, () -> new BlockItem(block.get(), props))` | 同 forge 手工注册 | forge `item/ModItems.java:1174`、`:1178` 注释 |
| 注册包装类 | `DeferredHolder<R, T>` | `RegistryObject<T>` | `platform/registry/RegistryObject.java`（105L） | `platform/registry/` 包；API：`get/isPresent/isEmpty/getId/getKey/map/ifPresent/orElse` |
| 注册机制 | `DeferredRegister#register(IEventBus)` | `DeferredRegister#register(IEventBus)` | 自实现 `DeferredRegister`（141L）：`create(Registry,String)` / `create(ResourceKey,String)` / `register(String,Supplier)` / `getEntries()` / `register(IEventBus)` / `commit()` | `platform/registry/DeferredRegister.java` |
| 枚举注册（MenuType 例） | `DeferredRegister<MenuType<?>>` + `IMenuTypeExtension.create` | 同 neo 结构（`ForgeRegistries.MENU_TYPES`） | `screen/ModMenuTypes.java:16 DeferredRegister<MenuType<?>>`、`:19 CARD_INVENTORY = MENU_TYPES.register("card_inventory", …)` | `fabric-1.20.1/.../screen/ModMenuTypes.java` |
| 资源定位 | `ResourceLocation.fromNamespaceAndPath(MODID, path)` | `new ResourceLocation(MODID, path)` | `new ResourceLocation(MODID, path)` | neo/fabric 差异：`item/ModTags.java` 用 `ResourceLocation.fromNamespaceAndPath(...)`；1.20.1 无该静态方法 |

### 2.5 Curios → Trinkets / Accessories

| 能力 | NeoForge 1.21.1 | Forge 1.20.1 | Fabric 1.20.1（本仓实现） | 取证 |
|---|---|---|---|---|
| 前置 | `curios`（`top.theillusivec4.curios.api.*`） | `curios`（同包） | `Trinkets 3.7.2`（`trinkets`）+ `Accessories >=1.0.0-beta.48`，**二源** | `fabric-1.20.1/build.gradle:100` `modCompileOnly trinkets`、`:116` `accessories modCompileOnly`、`:119` 条件 `modRuntimeOnly`；`gradle.properties` `trinkets_version=3.7.2` / `accessories_version_range=>=1.0.0-beta.48` |
| 消费方 API | `CuriosApi.getCuriosInventory(...)` / `getCuriosHelper()` / `SlotContext` / `ICurioItem` | 同 neo（同名同包） | `compat/curios/CuriosApi.java` 等 **同名同包替代类**，消费方 94 处只需换 `import` | `compat/curios/` 目录：`CuriosApi/ICurioItem/ICurioStacksHandler/ICursiosItemHandler/IItemHandler/SlotContext/SlotResult/TrinketBridge`；`compat/accessories/{AccessoriesCompat,AccessoriesClientIconCheck}` |
| 主源选择 | — | — | `AccessoriesCompat`（主）+ `TrinketBridge`（Trinkets 兜底），`ModCompatibilityCheck#verifyAccessoryProviderOrThrow()` 在最早初始化保证二选一存在 | `compat/accessories/AccessoriesCompat.java:150`（注册适配器日志）、`:210`（槽位准入自检）；`init/ModCompatibilityCheck` 的 4 个存在性检查方法 |
| 槽位定义数据 | `data/astral_dice/curios/slots/{dice,chip,stand}.json` | 同 neo | `data/trinkets/slots/astral_dice/*.json` + `data/astral_dice/accessories/slot/*.json` | 目录实测（fabric 有 `accessories/` 与 `trinkets/`，无 `curios/`） |
| 槽位图标路径 | `astral_dice:slot/empty_dice_slot`（`assets/.../textures/slot/`） | 同 neo（`AstralDiceMod.java:195/198/201` 用 `ResourceLocation(MODID,"slot/empty_dice_slot")`） | `astral_dice:gui/slot/empty_dice_slot`（`assets/.../textures/gui/slot/`） | 见 §2.7 资源路径行 |
| 效果修饰 lang 键 | `curios.modifiers.*` | 同 neo | `trinkets.slot.astral_dice.{dice,chip,stand}` / `accessories.slot.*`（fabric 独有 26 键中的一部分） | lang 键集比对（§4.2） |

### 2.6 Config

| 能力 | NeoForge 1.21.1 | Forge 1.20.1 | Fabric 1.20.1（本仓实现） | 取证 |
|---|---|---|---|---|
| 规格类 | `net.neoforged.neoforge.common.ModConfigSpec` | `net.minecraftforge.common.ForgeConfigSpec` | `com.merlinkitsune.astral_dice.platform.config.ForgeConfigSpec`（自实现，类名/嵌套类型与 forge 对齐） | neo `config/ModCommonConfig.java:4`；forge `:4`；fabric `config/ModCommonConfig.java:3` `import com.merlinkitsune.astral_dice.platform.config.ForgeConfigSpec` |
| 值类型 | `ModConfigSpec.BooleanValue/IntValue/…` | `ForgeConfigSpec.BooleanValue/…` | `ForgeConfigSpec.BooleanValue/…`（fabric `ModCommonConfig.java:31,33,34` 与 forge `:33,35,36` 逐行同形） | 同上 |
| 装载 | `ModConfigEvent`（`modEventBus`） | `ModConfigEvent`（`FMLJavaModLoadingContext`） | 自实现最小 TOML 读写（**不支持字符串/列表/双精度，不支持热重载**，见类头注释） | `platform/config/ForgeConfigSpec.java` 类头注释 |
| 首次生成/版本迁移 | — | — | `AstralDiceMod.java:195 LOGGER.info("[Astral Dice] 已生成默认配置 {}")`；`:216` 版本过旧→备份；`:260` 写回新版本号 | 同文件 |

### 2.7 资源路径（1.20.1 与 1.21 的真实差异）

| 资源 | NeoForge 1.21.1 | Forge 1.20.1 | Fabric 1.20.1（目标） | 取证 |
|---|---|---|---|---|
| 配方目录 | `data/astral_dice/recipe/`（**单数**） | `data/astral_dice/recipes/`（**复数**） | `recipes/`（复数）—— 已存在 14 个 json | 目录实测：neo `recipe/`=17、forge `recipes/`=17、fabric `recipes/`=14 |
| 战利品表目录 | `data/astral_dice/loot_table/`（单数） | `data/astral_dice/loot_tables/`（复数） | `loot_tables/`（复数）—— 已存在 4 个 | 实测：neo `loot_table/`=7、forge `loot_tables/`=7、fabric `loot_tables/`=4 |
| 战利品修饰器 | —（1.21 无 GLM） | `data/astral_dice/loot_modifiers/`（12 个 `star_plate_*.json`） | **无该目录**；改由 `loot/FabricLootInjector.java` 直注 `LootTable.Builder#pool` | 目录实测 + `build.gradle` loom `interfaceInjection` 注释 |
| 槽位图标 | `assets/astral_dice/textures/slot/empty_{dice,chip,stand}_slot.png` | 同 neo | `assets/astral_dice/textures/gui/slot/empty_{dice,chip,stand}_slot.png`（fabric 现有 3 张） | fabric 有 `textures/gui/slot/*.png` 3 张，**无** `textures/slot/` |
| 数据目录命名空间 | `assets/astral_dice/patchouli_books/**` | 同 | 同（**在 assets 下，不在 data 下**） | 目录实测：三线均有 `assets/astral_dice/patchouli_books` |
| 村民职业纹理 | `assets/astral_dice/textures/entity/villager/profession/{card_trader,chip_trader}.png` | 同 | 同（fabric 缺 2 张） | §4.1 |
| 粒子定义 | `assets/astral_dice/particles/*.json`（179 变体） | 同 | 同（四线一致，无需改） | 实测 textures/particle 179 四线相同 |

### 2.8 数据生成

| 能力 | NeoForge 1.21.1 | Forge 1.20.1 | Fabric 1.20.1（本仓实现） | 取证 |
|---|---|---|---|---|
| 入口事件 | `GatherDataEvent`（`net.neoforged.neoforge.data.event.GatherDataEvent`） | `GatherDataEvent`（`net.minecraftforge.data.event.GatherDataEvent`） | `DataGeneratorEntrypoint#onInitializeDataGenerator(FabricDataGenerator)` | neo `datagen/DataGenerators.java:10,17`；forge `:9,15`；fabric `datagen/AstralDataGenerator.java:3-4,28` |
| 入口声明 | `@EventBusSubscriber` + `@SubscribeEvent` | 同 | `fabric.mod.json` → `entrypoints.fabric-datagen` = `com.merlinkitsune.astral_dice.datagen.AstralDataGenerator` | `fabric-1.20.1/src/main/resources/fabric.mod.json` |
| Provider 注册 | `event.getGenerator().addProvider(...)` | 同 | `FabricDataGenerator.Pack#addProvider(...)` | 需在 fabric 入口里逐个补（缺口 §1.6 行 129 说明） |
| 模型 Provider | `ItemModelProvider#registerModels()` | 同 | `FabricModelProvider#generateBlockStateModels/generateItemModels`（**无 `registerModels`**） | `git diff --no-index --numstat` 对 `datagen/ModItemModelProvider.java` = `+162/-161`（§3 第 5 项） |
| 配方 Provider | `RecipeProvider#buildRecipes` | `RecipeProvider#buildRecipes` | `FabricRecipeProvider#buildRecipes(RecipeOutput)`（`RecipeOutput` 为 1.20.1 fabric 形态） | 缺 `ChipConditionalRecipe` 等 11 个方法（§3 第 3 项） |

### 2.9 村民职业与 POI

| 能力 | NeoForge 1.21.1 | Forge 1.20.1 | Fabric 1.20.1（目标） | 取证 |
|---|---|---|---|---|
| POI 注册 | `DeferredRegister<PoiType>`（`NeoForgeRegistries.POI_TYPES` / `Registries.POINT_OF_INTEREST_TYPE`） | `DeferredRegister<PoiType>`（`ForgeRegistries.POI_TYPES`） | `DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, MODID)` | forge `world/ModPoiTypes.java:10 import net.minecraft.world.entity.ai.village.poi.PoiType` |
| 职业注册 | `DeferredRegister<VillagerProfession>` | 同（`ForgeRegistries.VILLAGER_PROFESSIONS`） | `DeferredRegister.create(Registries.VILLAGER_PROFESSION, MODID)` | forge `world/ModVillagerProfessions.java:5 import net.minecraft.world.entity.npc.VillagerProfession`（注释：1.20.1 为同签名 6 参 record） |
| 认领标签 | `data/minecraft/tags/point_of_interest_type/acquirable_job_site.json` | 同 | 同（fabric **无该标签文件**） | forge `ModPoiTypes.java` 注释「只有 POI 不够，村民认领走 `minecraft:acquirable_job_site`」；fabric `data/astral_dice/tags/` 下**无** `point_of_interest_type` |
| 交易注入 | `VillagerTradesEvent` | `VillagerTradesEvent` + `BasicItemListing` | **无事件** ⇒ 需新增事件 + `Villager#getOffers`/`MerchantOffer` bridge mixin，或复用已存在的 `mixin/trade/*` 5 个注入点 | `fabric-1.20.1/.../mixin/trade/{MerchantContainerMixin,MerchantMenuMixin,MerchantOfferMixin,MerchantResultSlotMixin,ServerPlayerMixin}.java` |
| 职业纹理 | `textures/entity/villager/profession/{card_trader,chip_trader}.png` | 同 | 同（fabric 缺 2 张） | §4.1 |
| 现状 | 有 | 有 | **整条缺失**：`grep -rl VillagerProfession fabric-1.20.1/src/main/java` = 0 命中 | 本仓实测 |


## 3. 共有漂移清单（前 40）

### 3.1 口径与集合统计

| 项 | 值 | 取证 |
|---|---|---|
| 同路径共有类（`forgeφ ∩ fabφ`） | **259** | 与任务书口径一致 |
| 其中内容有差异（`diff` 非空） | **181** | 与任务书「181 个与 forge 不一致」一致 |
| 仅在 forge 线存在（同路径） | 144 | `git diff --no-index` 的 only-in-forge |
| 仅在 fabric 线存在（同路径） | 121 | 同上 only-in-fabric |
| 参照系补充 | `neoφ ∩ forgeφ ∩ fabφ` = 250；四线全共有 = 248 | 集合运算 |

差异行数 = `numstat` 的 `+` 与 `−` 之和（见 §0.3）。「任务书参考」列原样保留任务书给的数字，**与本表实测值不同属口径/快照差异**，不一并修正。

### 3.2 前 40 项（按差异行数降序）

| 排名 | 类（同路径，两线均含 `com/merlinkitsune/astral_dice/`） | 实测 numstat | 任务书参考 | fabric 缺什么（一句话判定） | 判定依据 |
|---|---|---|---|---|---|
| 1 | `combat/DiceCombatEvents.java` | +147 / −736 = 883 | 714 | fabric 侧整块缺「预伤害吸收快照 + 幻影对账 + 骰战结算」：无 hasEnigmaticCurse/isHoldingEnigmaticItem/applyCurseToDicePoints/onLivingHurtRecordAbsorption/consumePreHurtAbsorption/PreHurtSnapshot/settleDiceDamage/applyAlanaBigHit/registerPhantom/phantomOf/withPhantomPeeled/compensateAbsorption/onServerTickReconcilePhantoms/diceBattleAttackStrengthScale，缺 5 个关键常量（ENIGMATIC_CURSED_RING/ACKNOWLEDGMENT/TWIST/BLESS、PRE_HURT_ABSORPTION、WEAK_MARK_DAMAGE_MULTIPLIER、PHANTOM_ABSORPTION），并删掉了对 CuriosCompat / LinglingSignManager / SignHookDispatcher / PlayerEffectProtection 的引用 ⇒ 神秘遗物诅咒分支、吸收与幻影对账、骰战伤害结算在 fabric 全部不生效 | 方法/字段计数 64/28 → 49/14 |
| 2 | `component/ModAttachments.java` | +102 / −609 = 711 | 503 | 缺 64 个 getter/setter 与 8 个附件键：EVENT_DRAWER_COOLDOWN_UNTIL、EVENT_DRAWER_BLANK_INSERT_AT、FLASHLIGHT_BURST_TARGETS、FORTUNE_STICK_BLESSING_CLAIMED、ABYSS_REAGENT_GRANTED、SIGN_STATE、LINGLING_GRANTED、SHOP_PROMOTION_UNTIL（另有 SHOP_TRANSFER_USED/SHOP_SHELF 相关访问器）⇒ 商店促销/货架、抽奖盒冷却、手电筒连击目标、幸运棒祝福、深渊试剂授予、立牌状态持久化全部无数据面 | 281/140 → 222/110 |
| 3 | `datagen/ModRecipeProvider.java` | +283 / −307 = 590 | 359 | 缺筹码配方条件包装与条件序列化：chipRecipeConditions/withChipCondition/ChipConditionalRecipe/serializeRecipeData/getId/getType/serializeAdvancement/getAdvancementId/potionTag 共 11 个方法 ⇒ 17 条筹码配方在 fabric 只能无条件生成（或直接缺 3 条，见 §4.4） | 11 → 4 方法 |
| 4 | `AstralDiceMod.java` | +220 / −131 = 351 | 315 | **非缺口**：fabric 已重构入口（onInitialize/commitRegistrations/registerListeners/setupConfig），差的是 STARENGINE_MODID 常量与逐类 LoaderBus 注册清单；判定依据是「成员结构不同而非功能缺失」，验收只看 `:125-151` 注册清单是否覆盖全部处理器 | 入口结构重构 |
| 5 | `datagen/ModItemModelProvider.java` | +162 / −161 = 323 | 320 | 缺 `registerModels()` 口径：forge 继承 `ItemModelProvider` 一次性注册，fabric 必须拆成 `generateBlockStateModels` + `generateItemModels`（`FabricModelProvider`），⇒ fabric 的方块物品模型（card_shop/chip_shop/event_drawer/event_ticket）从未生成 | 需重写 Provider 体 |
| 6 | `item/sign/BaseSignItem.java` | +191 / −108 = 299 | 245 | 缺 `skipCooldownAfterLock` / `activeCooldownBaseTicksNoBattery` 两个语义：fabric 版本没有「无电池时的主动冷却基线」，主动技能锁定后冷却一律按有电池算 ⇒ 装/卸电池时冷却时长错档 | 33 → 33 方法（语义缺 2） |
| 7 | `combat/DiceCombatModifiers.java` | +59 / −211 = 270 | 204 | 缺「防御力 ↔ 臂甲值」双向折算：armorValueForDefensePoints / defensePointsForArmor / effectiveArmorPoints / skipOnClient / setAttackPowerBonus 与 PLAYER_ARMOR_PER_DEFENSE、MOB_ARMOR_PER_DEFENSE 两个系数（玩家 0.30 / 生物 0.40）⇒ fabric 的防御点数不参与护甲换算，战斗平衡偏离 | 33/9 → 28/7 |
| 8 | `event/ModTooltipHandler.java` | +15 / −246 = 261 | 169 | 缺四类 tooltip 行：路障（Barricade）说明、强化拒止的卡片形态与物品形态两套说明、所有者行、神秘遗物联动描述 ⇒ 这些物品在 fabric 悬停无说明（等于功能不可发现） | 24 → 24 方法（内容删 246 行） |
| 9 | `network/ModNetwork.java` | +6 / −249 = 255 | 161 | 缺 6 个消息类：TargetSelectCloseMessage 与 CardShop{TransferList,Transfer,Buy,Deny}Message，连带缺字段 entries/targetIndex/amount/shelfIndex/targetSlot/reason ⇒ fabric 无法完成卡牌商店的转移/购买/拒绝与服务端目标选择关闭 | 30/27 → 24/21 |
| 10 | `item/ModItems.java` | +36 / −200 = 236 | 177 | 缺 20 个物品注册：EFFECT_CARD_BARRICADE、EFFECT_CARD_REPULSION、REPULSION、CARD_SHOP、CHIP_SHOP、EVENT_TICKET、EVENT_DRAWER、EXTRA_BATTERY_CHIP、FLASHLIGHT_BURST_CHIP、FORTUNE_STICK_CHIP、ABYSS_REAGENT_CHIP、INFINITE_GRID_CHIP 等，并缺 registerSimpleBlockItem/isCombatCard/isEffectCard 三个辅助 ⇒ 创造栏与配方在这些条目上全空 | 160 → 140 字段 |
| 11 | `event/DamageEffectCardHandler.java` | +50 / −160 = 210 | 181 | 缺 foldBonusBypassingArmor / onLivingHurtPre / onLivingDamageSound 三个入口（fabric 只剩 onLivingDamagePre）⇒ 效果牌的「无视护甲加成折叠」与受击音效不生效 | 3 → 1 方法 |
| 12 | `item/sign/FannySignItem.java` | +162 / −27 = 189 | — | **非缺口**：fabric 走另一套立牌框架（多出 applyEventTo/applyTimed/startActiveLockOnUse/giveRandomEffectCard/giveCoins 等 12 个方法）；需核「发放随机效果牌/发币」两条路径与 forge 等价（本条列入附录 B-5） | 方法集不同 |
| 13 | `item/chip/ElectricGloveChipItem.java` | +37 / −138 = 175 | — | 缺电击实现与四个常量 SHOCK_DAMAGE/SHOCK_DELAY_TICKS/COOLDOWN_TICKS/CHARGE_PER_HIT 及 isOnCooldown/executeShock ⇒ 电击手套芯片在 fabric 只会给属性、不会放电 | 缺 2 方法 + 4 常量 |
| 14 | `item/card/TemporaryCardUtil.java` | +12 / −159 = 171 | — | 缺临时卡「所有者绑定 + 非法持有清理」整块：ownerOf/ownerKeyOf/isOwnedBy/isIllegalHold/purgeEquippedWhere/isLegalStoneOwner/purgeIllegalEquipped/purgeIllegalHoldings ⇒ fabric 的临时卡可被任意玩家持有/装备，且无清扫入口 | 缺 8 方法 |
| 15 | `combat/DamageCardFxScheduler.java` | +55 / −108 = 163 | — | 缺 latestGloveTarget / sendBeamFx 与 LASER_WIDTH/BEACON_WIDTH/BEAM_TTL_TICKS 三个常量（fabric 只有 drawBeam）⇒ 光束特效不下发（对应缺口 BeamFxPayload），电击视觉在多人环境里只能本地播 | 缺发包 + 3 常量 |
| 16 | `item/sign/ParunanSignItem.java` | +70 / −77 = 147 | — | 缺 isEquipped / activeCooldownBaseTicks / activeCooldownBaseTicksNoBattery / ACTIVE_COOLDOWN_SECONDS ⇒ 帕鲁南立牌的主动冷却与佩戴判定缺失（与第 6 项同源问题） | 缺 4 成员 |
| 17 | `component/AttachedDataKey.java` | +108 / −35 = 143 | — | **非缺口**：平台实现换代（Capability → Fabric `AttachmentType`），fabric 反而多出 attachment/target/rawTag/decode/snapshot/copyOnDeath；唯一缺的是 `store` 便利方法 | 13/5 → 18/7 |
| 18 | `event/PlayerTickEvents.java` | +34 / −107 = 141 | — | 缺筹码冷却指示器 tick 刷新：updateChipCooldownIndicators / refreshIndicatorRemaining / REMAINING_TOLERANCE ⇒ fabric 的 HUD 筹码冷却不随时间回落 | 缺 3 成员 |
| 19 | `init/ModCreativeTabs.java` | +47 / −90 = 137 | — | **非缺口**：成员集合与 forge 完全一致，差异只在内容（物品引用）——随第 10 项补齐后自动收敛 | 成员集相同 |
| 20 | `client/ModernUITooltipCompat.java` | +59 / −77 = 136 | — | 桥接方式换代：fabric 用 beginRender/endRender（mixin 桥）替代 onTooltipPre/onTooltipPost（Forge 事件）⇒ 需核 ModernUI 下自定义 tooltip 边框仍被接管（附录 B-4） | 桥 mixin 换代 |
| 21 | `event/AnvilUpgradeHandler.java` | +33 / −101 = 134 | — | **真实缺陷**：fabric 缺 `event.setMaterialCost(req)` 与 starCoinCost/isDice 判定（5 方法→1 方法）⇒ 铁砧强化在 fabric **不收星币**（免费的强化） | 5 → 1 方法 |
| 22 | `item/sign/MamushiSignItem.java` | +64 / −69 = 133 | — | 缺 startActiveLockOnUse / skipCooldownAfterLock / handleDiceBlessingTriggered / handleDiceBlessingEnded / handleDragonRoar 五个钩子 ⇒ 玛姆希立牌的主动锁定与「骰子祝福」「龙吼」联动不生效 | 多 5 个 forcedCooldown 方法 |
| 23 | `command/AstralPartyCommand.java` | +11 / −119 = 130 | — | 缺 killbarricade / killrepulsion 两个子命令及 KEY_KILL_*/KILL_*_LITERAL/ARG_RADIUS/ARG_ALL/DEFAULT_KILL_RADIUS/MAX_KILL_RADIUS ⇒ fabric 无运维清理入口（路障/拒止实体只能清区块） | 缺子命令节点 |
| 24 | `combat/SpellDamageRegistry.java` | +64 / −59 = 123 | — | 缺 collectGloveShockTargets ⇒ 电击手套的连锁目标收集缺失（与第 13/15 项同链） | 缺 1 方法 |
| 25 | `mixin/jade/ArmorValueProviderMixin.java` | +33 / −84 = 117 | — | **非缺口**：`@Redirect` 口径换成 `@Inject`（0/0 方法体差异），需按 Jade 11.13.3 实际字节码复核注入点与 `require` 计数 | 0/0（纯口径） |
| 26 | `effect/ModEffects.java` | +6 / −110 = 116 | — | 缺 15 个效果常量：JILL_MIXED、DOROTHY_WARMTH、DOROTHY_SOCIALITE、ALANA_IRON_MAIDEN、DEEPSEEK_FISHING、WHETSTONE_GUARD_CD、AIRBAG_GUARD_CD、RED_HEAT_WARNING、STORM_APPROACHING、PROBLEM_DELIVERY、KANGEL_FAN、KANGEL_FAN_INDICATOR、AME_LOVE、AME_OVERDOSE、LINGLING_SURGE ⇒ 与 §1.5 的 15 个效果类一一对应，不注册则效果类无法被挂上 | 缺 15 常量 |
| 27 | `component/DeathPreservedBonuses.java` | +21 / −84 = 105 | — | 缺 KANGEL_FAN_COUNT / AME_LOVE_LAYERS / AME_LOVE_CAP / PRESERVED_TARGETS ⇒ 死亡时「粉丝数 / 爱之层数」不保留，与第 26 项同链 | 缺 4 成员 |
| 28 | `item/sign/NancyLuSignItem.java` | +63 / −41 = 104 | — | 缺 handleDiceBlessingEnded 与 ACTIVE_ATTACK_BONUS（fabric 换成 findAndConsumeRandomBattleCard）⇒ 南希露立牌的祝福结束回调缺失 | 缺 2 成员 |
| 29 | `event/LootInjectionHandler.java` | +21 / −80 = 101 | — | 缺 onLootTableLoad（含「自有命名空间早退」与双通道注入修复）⇒ 需核 `loot/FabricLootInjector.java` 是否完整等价（附录 B-6） | 缺 1 入口 |
| 30 | `init/ModCompatibilityCheck.java` | +88 / −6 = 94 | — | **非缺口**：fabric 多出 Trinkets/Accessories 存在性检查 4 个方法（平台二源必需），非功能缺失 | fabric 多 4 方法 |
| 31 | `item/card/FateGuidanceCardItem.java` | +13 / −77 = 90 | — | 缺 onCurseMitigation ⇒ 命运指引卡不能减免神秘遗物诅咒 | 缺 1 方法 |
| 32 | `client/CombatPowerHud.java` | +30 / −58 = 88 | — | 缺 onRenderGuiOverlayPre 与 OVERLOADED_ARMOR_BAR_LAYER ⇒ 过载护甲条图层不渲染（fabric overlay 锚点缺失，与第 37 项同链） | 缺 2 成员 |
| 33 | `event/PlayerLifecycleHandler.java` | +55 / −32 = 87 | — | 缺 onPlayerLoggedInMigrateSignState ⇒ 老存档登录时不迁移立牌状态（与 §3 第 2 项 SIGN_STATE 新键配套） | fabric 多 countGuideBooks |
| 34 | `item/StarLightManager.java` | +44 / −38 = 82 | — | 缺 replenishOnExchange ⇒ 交易后星光的补充路径缺失 | fabric 多 getBasePoints/grantStarlightFloorOnEquip |
| 35 | `trade/EmeraldDiceTrade.java` | +12 / −70 = 82 | — | 缺 isEmeraldToStarCoin / rawCostA / filteredFor：fabric 用 `transformOffers` 做**数据级**改写（`EmeraldDiceTrade.java:87-105`）替代 forge 的 duck interface `MerchantOfferRawAccess` + `MerchantOfferMixin` 取值覆写，但没有 raw 费用兜底 ⇒ 互换上下文激活时「兑换条目识别」不可用（`MerchantOfferMixin.java:28-32` 让 getBaseCostA 直接返回星币） | 需补 duck interface 或 NBT 兜底 |
| 36 | `mixin/jade/ArmorElementIconMixin.java` | +28 / −53 = 81 | — | **非缺口**：同第 25 项，`@Redirect`→`@Inject` 口径替换，需按 Jade 11.13.3 复核 | 0/0（纯口径） |
| 37 | `client/ModClientEvents.java` | +14 / −60 = 74 | — | 缺 registerEntityRenderers / registerLayerDefinitions，以及 overlay 锚点注册（被删行含 `event.registerAbove(VanillaGuiOverlay.CROSSHAIR.id(), "event_card", EventCardOverlay.OVERLAY)` 与 ARMOR_LEVEL/FOOD_LEVEL 锚点）⇒ 实体渲染器/模型层/事件卡 overlay 全未接线 | 缺 2 注册入口 + 锚点 |
| 38 | `event/ModEffectEvents.java` | +18 / −53 = 71 | — | 缺 onPlayerLoggedOut / onPlayerClone / onExternalRemovalRestore ⇒ 效果在登出/重生/被外部移除后不恢复（与第 27 项死亡保留配套） | 缺 3 入口 |
| 39 | `item/ChargeManager.java` | +11 / −58 = 69 | — | 缺 signCooldownTicksBase ⇒ 立牌主动冷却基线缺失（第 6/16 项的共同底层） | 缺 1 成员 |
| 40 | `item/chip/PerpetualMotionChipItem.java` | +6 / −60 = 66 | — | 缺 attackSpeedModifierId / getAttributeModifiers / ATTACK_SPEED_MULTIPLIER ⇒ 永动芯片不给攻击速度属性 | 缺 3 成员 |

### 3.3 第 41–45 名（补充，便于排期）

| 排名 | 类 | 实测 numstat | 一句话 |
|---|---|---|---|
| 41 | `event/RailgunStrikeScheduler.java` | 65 | 电磁炮落雷调度缺失（与电击链第 13/15/24 项同族） |
| 42 | `network/VersionGate.java` | 63 | 互通号生成路径差异，需核与 forge/neo 的互通号一致（附录 B-7） |
| 43 | `item/CurioSlotUtil.java` | 58 | 饰品槽位工具在 Trinkets/Accessories 双源下口径不同 |
| 44 | `client/TargetSelectionClient.java` | 57 | 目标选择客户端态（依赖 TargetSelect* 消息，第 9 项） |
| 45 | `client/gui/StarCoinWalletButtons.java` | 57 | 钱包按钮注入口径（原版 InventoryScreen 注入方式差异） |

### 3.4 从这 40 项归纳出的 4 条「一条链」

1. **商店/事件数据面缺失链**：`ModAttachments`（第 2，缺 8 个键）→ `ModNetwork`（第 9，缺 6 消息）→ `ModItems`（第 10，缺 20 物品）→ `PlayerLifecycleHandler`（第 33，缺登录迁移）。四处必须同批补齐，否则只补一处仍是死功能。
2. **立牌主动技能链**：`ChargeManager`（第 39，缺 signCooldownTicksBase）→ `BaseSignItem`（第 6）/ `ParunanSignItem`（第 16）/ `MamushiSignItem`（第 22）→ `PlayerTickEvents`（第 18）。
3. **电击/光束链**：`ElectricGloveChipItem`（第 13）→ `SpellDamageRegistry`（第 24）→ `DamageCardFxScheduler`（第 15，缺 BeamFxPayload）→ `RailgunStrikeScheduler`（第 41）。
4. **失败牌/效果牌链**：`ModEffects`（第 26，缺 15 常量）→ `DeathPreservedBonuses`（第 27）→ `ModEffectEvents`（第 38）→ 累计到 `DiceCombatEvents`（第 1）的幻影/吸收分支。


## 4. 资源缺口清单

枚举口径：`<线>/src/main/resources/` 下的**文件**（不是目录、不是条目数），`fabric` 缺 = 「neo 有、fabric 无」的同相对路径文件。脚本见附录 A-3（可逐行复算）。

### 4.1 资产目录总表（assets/astral_dice）

| 目录 | neoforge-1.21.1 | forge-1.20.1 | fabric-1.20.1 | fabric 缺 | 来源标注 |
|---|---|---|---|---|---|
| `textures/gui` | 242 | 242 | 68 | **177** | 177 项中 1 项与 forge 不同（见 4.1.1）；176 项与 neo **字节相同** ⇒ 可从 `neoforge-1.21.1` 直接复制 |
| `textures/item` | 158 | 158 | 141 | **17** | 全部与 neo 字节相同 ⇒ 直接复制 |
| `textures/mob_effect` | 67 | 67 | 53 | **15** | 全部与 neo 字节相同 ⇒ 直接复制（且必须与 15 个效果类同批，见 §1.5） |
| `textures/block` | 5 | 5 | 0 | **5** | 直接复制 |
| `textures/entity` | 4 | 4 | 0 | **4** | 直接复制（其中 2 项是村民职业纹理） |
| `blockstates` | 4 | 4 | 0 | **4** | 直接复制（1.20.1 与 1.21.1 的 blockstate 结构一致，无需改） |
| `models` | 9 | 9 | 1 | **8** | 直接复制（`models/block/` 4 + `models/item/` 4） |
| `sounds` | 23 | 23 | 15 | **8** | **不可直接复制**：8 个 `.ogg` 在 neo 与 forge 之间**字节不同** ⇒ 必须从 `forge-1.20.1` 复制 |
| **合计** | 508 | 508 | 278 | **238** | 230 项可从 neo 直取，8 项必须取 forge 线版本 |

补充：fabric 另有 3 张 neo/forge 都没有的图 —— `textures/gui/slot/{empty_chip_slot,empty_dice_slot,empty_stand_slot}.png`，这是**槽位图标的路径差异**（§2.7），不是缺口。

#### 4.1.1 8 项「不能从 neo 直取」的文件（必须用 forge-1.20.1 版本）

| 文件 | 原因 |
|---|---|
| `sounds/event_bonus.ogg` | neo 与 forge 的音频二进制不同（sha256 前 16 位不等） |
| `sounds/event_damage.ogg` | 同上 |
| `sounds/event_fade_in.ogg` | 同上 |
| `sounds/event_fade_out.ogg` | 同上 |
| `sounds/event_heal.ogg` | 同上 |
| `sounds/event_poisoning.ogg` | 同上 |
| `sounds/event_tick.ogg` | 同上 |
| `sounds/event_transport.ogg` | 同上 |

#### 4.1.2 `textures/gui` 177 项按子目录（与任务书 68/242 口径一致）

| 子目录 | 缺 | 内容 |
|---|---|---|
| `textures/gui/shop/` | **91** | 卡牌商店货架/星级/货币图标 |
| `textures/gui/chip_shop/` | **67** | 筹码商店（含 `chips/` 下每枚芯片的商品图） |
| `textures/gui/event/` | **18** | 事件卡/事件界面（含 `investigation_stage` 阶级图） |
| `textures/gui/`（根） | **1** | `book_pink.png` |

#### 4.1.3 逐项目录（可直接当复制清单）

```
textures/item/        abyss_reagent_chip.png alana_sign.png ame_sign.png deepseek_sign.png
                      dorothy_sign.png effect_card_barricade.png effect_card_repulsion.png
                      event_ticket.png extra_battery_chip.png flashlight_burst_chip.png
                      fortune_stick_chip.png infinite_grid_chip.png jill_sign.png kangel_sign.png
                      lingling_sign.png repulsion.png sensor_chip.png
textures/mob_effect/  airbag_guard_cd.png alana_iron_maiden.png ame_love.png ame_overdose.png
                      deepseek_fishing.png dorothy_socialite.png dorothy_warmth.png jill_mixed.png
                      kangel_fan.png kangel_fan_indicator.png lingling_surge.png problem_delivery.png
                      red_heat_warning.png storm_approaching.png whetstone_guard_cd.png
textures/block/       card_shop.png card_shop_particle.png chip_shop.png chip_shop_particle.png event_drawer.png
textures/entity/      barricade.png repulsion.png villager/profession/card_trader.png villager/profession/chip_trader.png
blockstates/          barricade_collision.json card_shop.json chip_shop.json event_drawer.json
models/block/         barricade_collision.json card_shop.json chip_shop.json event_drawer.json
models/item/          card_shop.json chip_shop.json event_drawer.json event_ticket.json
sounds/               event_bonus.ogg event_damage.ogg event_fade_in.ogg event_fade_out.ogg
                      event_heal.ogg event_poisoning.ogg event_tick.ogg event_transport.ogg
```

### 4.2 语言文件（`assets/astral_dice/lang/`）

| 语言 | neoforge-1.21.1 键数 | forge-1.20.1 | fabric-1.20.1 | 缺 |
|---|---|---|---|---|
| `en_us.json` | 1070 | 1070 | 844 | 226（forge→fabric 缺 252 键，fabric 另有 26 键为平台专属） |
| `zh_cn.json` | 1070 | 1070 | 844 | 同上 |
| `ja_jp.json` | 1070 | 1070 | 844 | 同上 |

三语**键集彼此相等**（每线 844 相同键集）⇒ 缺口可以三语同批搬，不存在「只缺某一语」的情况。

缺 252 键按顶级前缀分布（`python3 tools/check_lang_sync.py --line fabric-1.20.1` 之外另作前缀统计）：

| 前缀 | 缺键数 | 对应功能 |
|---|---|---|
| `astral_dice.guide.*` | 52 | 帕秋莉手册条目名/正文（与 §4.3 的 28 个 json 配套） |
| `msg.astral_dice.*` | 52 | 商店/事件/立牌的操作反馈 |
| `tooltip.astral_dice.*` | 40 | 物品与效果牌 tooltip（与 §3 第 8 项同源） |
| `gui.astral_dice.*` | 25 | 商店/事件 GUI 文案 |
| `effect.astral_dice.*` | 24 | 15 个新效果 + 9 个既有效果补充（name/description） |
| `item.astral_dice.*` | 17 | 17 个未注册物品 |
| `event.astral_dice.*` | 13 | 13 条事件定义（`verify_event_registry.ps1` 强校验） |
| `jei.astral_dice.*` | 8 | JEI 类别名 |
| `block.astral_dice.*` | 4 | 3 个方块 + 1 个方块变体 |
| `curios.modifiers.*` | 3 | 饰品槽位修饰（fabric 侧走 `trinkets.slot.*` / `accessories.slot.*`，**属合法平台差异，不改**） |
| `jade.astral_dice.*` + `config.jade.*` | 3 + 2 | Jade 提供器文案 |
| `entity.astral_dice.*` + `entity.minecraft.*` | 4 | 实体名（路障/拒止/村民职业） |
| `command.astral_dice.*` | 2 | 新增子命令提示 |
| `hud.astral_dice.*` / `message.astral_dice.*` / `bountiful.decree.*` | 1 + 1 + 1 | HUD 与赏金联动 |

fabric 独有的 26 键（**不是缺口**，不得删）：`trinkets.slot.astral_dice.{chip,dice,stand}`、`accessories.slot.{chip,dice,stand}`、`accessories.slot_group.astral_dice`、`effect.astral_dice.railgun_ready`、`msg.astral_dice.fanny_event(/.1–.11)`、`msg.astral_dice.parunan_active_no_starlight`、`tooltip.astral_dice.weird_dice_cooldown`、`astral_dice.guide.entry.{teru,nardis,sherry,hanna}_sign.3`。

### 4.3 帕秋莉手册（`assets/astral_dice/patchouli_books/astral_guide/`）

| 项 | neoforge-1.21.1 | forge-1.20.1 | fabric-1.20.1 | 缺 |
|---|---|---|---|---|
| 全部 `.json` | 184 | 184 | 156 | **28** |
| `entries/` | 169 | 169 | 142 | 27 |
| `categories/` | 15 | 15 | 14 | 1 |

28 个缺失文件（相对 `patchouli_books/astral_guide/`，全部可从 `neoforge-1.21.1` 直取，手册 json 无 1.20.1/1.21 差异）：

| # | 文件 | 说明 |
|---|---|---|
| 1 | `en_us/categories/blocks.json` | 新增「方块」分类（fabric 缺整类） |
| 2–6 | `en_us/entries/blocks/{card_shop,card_trader,chip_shop,chip_trader,event_drawer}.json` | 商店/村民/抽奖盒 5 页 |
| 7–8 | `en_us/entries/cards_effect/{effect_card_barricade,effect_card_repulsion}.json` | 两张效果牌 |
| 9–14 | `en_us/entries/chips_charge/infinite_grid_chip.json`、`chips_healing/abyss_reagent_chip.json`、`chips_mark/sensor_chip.json`、`chips_other/extra_battery_chip.json`、`chips_starlight/{flashlight_burst_chip,fortune_stick_chip}.json` | 6 枚筹码 |
| 15–16 | `en_us/entries/getting_started/{battle_system_changes,integration}.json` | 战斗体系变更 + 联动说明 |
| 17 | `en_us/entries/materials/event_ticket.json` | 抽奖券 |
| 18–24 | `en_us/entries/signs/{alana,ame,deepseek,dorothy,jill,kangel,lingling}_sign.json` | 7 张立牌英文页 |
| 25–28 | `zh_cn/entries/signs/{alana,deepseek,dorothy,jill}_sign.json` | 同样 4 张的中文页（en_us 有、zh_cn 也缺，说明是整页缺而非翻译缺） |

> 任务书给的「patchouli 141/164」与实测 142/169（entries 计数）不同：任务书数字更接近「当前 fabric 计数 142 与另一快照 169」，无法判定其确切口径（附录 B-2）。本表按文件数给出可复现清单。

### 4.4 数据文件（`data/astral_dice/`）

| 类别 | neo 1.21.1（路径） | forge 1.20.1（路径） | fabric 现状 | 缺 | 来源标注 |
|---|---|---|---|---|---|
| 配方 | `recipe/` 17 | `recipes/` 17 | `recipes/` 14 | **3**：`card_shop.json`、`chip_shop.json`、`event_drawer.json` | 从 forge 复制（1.20.1 的 `recipes/` 复数目录名，fabric 已正确） |
| 战利品表 | `loot_table/` 7 | `loot_tables/` 7 | `loot_tables/` 4 | **3**：`blocks/{card_shop,chip_shop,event_drawer}.json` | 从 forge 复制 |
| 战利品修饰器 | 无（1.21 无 GLM） | `loot_modifiers/` **13** 个 `star_plate_*.json` | 无目录 | **不搬**，由 `loot/FabricLootInjector.java` 直注；需核等价（§3 第 29） | — |
| 物品标签 | `tags/item/` 12 | `tags/items/` 12 | `tags/items/` 12 | 0（**只是目录名复数化**，已正确） | — |
| POI 标签 | `tags/point_of_interest_type/` 2 | `tags/point_of_interest_type/` 2 | **无** | **2**：`card_trader_job_site.json`、`chip_trader_job_site.json` | 从 forge 复制（1.20.1 与 1.21 该目录名相同） |
| 伤害类型 | `damage_type/` 7 | `damage_type/` 7 | 7 | 0 | — |
| 进度 | 无 | 无 | `advancements/recipes/misc/suspicious_stew.json`（fabric 独有） | 0 | 1.20.1 datagen 自动产物，保留 |
| 饰品槽位 | `curios/slots/{dice,chip,stand}.json` | 同 neo | `trinkets/slots/astral_dice/*` + `accessories/slot/*` | 0（已是平台形态） | — |

### 4.5 「321 项」口径核对

逐项枚举得到的真实文件级缺口是 **274 项**（238 资产 + 28 手册 + 3 配方 + 3 战利品表 + 2 POI 标签）；若把语言键也算「项」则为 **526 项**（+252 键）。任务书给的 **321 项** 两者都不等，且其逐目录数字（`textures/gui 177`、`item 17`、`mob_effect 15`、`block 5`、`entity 4`、`blockstates 4`、`models 8`、`sounds 8` = 238）**与本表完全一致**，矛盾只出现在合计。⇒ 判定：任务书合计为不同口径混合（可能把手册条目数与资产数相加时用了另一快照），本文档以 274（文件）/ 526（含 lang 键）为准，且 238 这一层与任务书逐目录完全对账。


## 5. 每功能验收判据

判据分四类，全部**可执行**：

- **[F] 文件存在性**：`test -f`（路径相对仓库根）
- **[G] grep 计数/模式**：`grep -c` / `grep -q`（模式逐字给出）
- **[S] 守门脚本**：`python` / `pwsh` 命令 + 退出码
- **[L] 游戏内机器行**：`run/logs/latest.log` 里必须出现的字面量（或其正则）

统一前置（对所有小节成立）：

```bash
# 1) 不残留旧平台 API
grep -rn "net\.minecraftforge\|net\.neoforged\|top\.theillusivec4" \
  fabric-1.20.1/src/main/java | wc -l          # 期望 0
# 2) 平台 shim 全部就位
for f in platform/event/LoaderBus platform/network/SimpleChannel \
         platform/registry/DeferredRegister platform/registry/RegistryObject \
         platform/config/ForgeConfigSpec component/AttachedDataKey \
         compat/curios/CuriosApi; do
  test -f fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/$f.java || echo "MISSING $f"; done
```

### 5.1 商店经济（§1.1 组一，33 类）

| 判据 | 类型 | 命令 / 模式 | 通过标准 |
|---|---|---|---|
| 三个商店方块类存在 | F | `test -f fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/block/{CardShopBlock,ChipShopBlock,EventDrawBlock}.java` | 3/3 存在 |
| 方块已注册 | G | `grep -c "CARD_SHOP\|CHIP_SHOP\|EVENT_DRAWER" fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/block/ModBlocks.java` | ≥ 6（3 常量 + 3 register） |
| 方块物品已注册 | G | `grep -c "registerSimpleBlockItem\|new BlockItem" .../item/ModItems.java` | ≥ 3（1.20.1 无 `registerSimpleBlockItem`，用 `new BlockItem`） |
| 3 条方块配方 | F | `test -f fabric-1.20.1/src/main/resources/data/astral_dice/recipes/{card_shop,chip_shop,event_drawer}.json` | 3/3 |
| 3 条方块战利品表 | F | `test -f fabric-1.20.1/src/main/resources/data/astral_dice/loot_tables/blocks/{card_shop,chip_shop,event_drawer}.json` | 3/3 |
| 方块模型/贴图/blockstate | F | `ls fabric-1.20.1/src/main/resources/assets/astral_dice/{blockstates,models/block,textures/block}` | 4 blockstate + 4 models/block + 5 贴图 |
| 商店目录/概率类可执行 | G | `grep -c "public static" .../shop/{CardShopCatalog,ChipShopCatalog,ChipShopRoller}.java` | 与 neo 版方法数一致（§1.1 已列） |
| 商店菜单已注册 | G | `grep -n "CARD_SHOP_MENU\|CHIP_SHOP_MENU" .../screen/ModMenuTypes.java` | 2 条 register |
| 商店物品提示 | G | `grep -c "CardShop\|ChipShop" .../event/ModTooltipHandler.java` | ≥ 4 |
| 资源闭合（注册↔模型↔贴图） | S | `pwsh -NoProfile -File scripts/verify/verify_resource_integrity.ps1` | 退出码 0 |
| 商店 UI 结构 | S | `pwsh -NoProfile -File scripts/verify/verify_chip_shop_ui.ps1 fabric`（**需先给脚本加 `fabric` 分支**，现仅 `neo`/`forge`） | 退出码 0（G1..G27） |
| 客户端资源 | S | `python tools/verify_fabric_assets.py --jar <fabric 构建产物>` | 退出码 0（无「注册了但资源没跟上」） |
| 游戏内 | L | `grep -c "Astral Dice mod loaded." run/logs/latest.log` | ≥ 1，且无 `MISSING TEXTURE`/`Unable to load model` 行 |

### 5.2 事件体系（§1.2 组二，33 类）

| 判据 | 类型 | 命令 / 模式 | 通过标准 |
|---|---|---|---|
| 13 条事件定义存在 | F | `ls fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/api/` | 5 个文件（AstralEvent/AstralEventEffect/AstralEventPolarity/AstralEventTier/AstralEventTrigger） |
| 事件 id 常量 | G | `grep -c "public static final" .../event/AstralEventIds.java` | = 13 |
| 事件注册守门 | S | `pwsh -NoProfile -File scripts/verify/verify_event_registry.ps1 -Line fabric-1.20.1` | 退出码 0（判据：id 有常量、路径唯一、三语 `event.astral_dice.<path>.name`、图标 `textures/gui/event/<path>.png`、`investigation_stage` 4 张阶级图） |
| 事件图标 | F | `ls fabric-1.20.1/src/main/resources/assets/astral_dice/textures/gui/event/` | 13 + 4 阶 |
| 事件三语 lang | G | `grep -c "event\.astral_dice\." fabric-1.20.1/src/main/resources/assets/astral_dice/lang/en_us.json` | = 13（三语同） |
| 事件处理器全部注册到总线 | G | `grep -c "LoaderBus.INSTANCE.register" .../AstralDiceMod.java` | ≥ 现有 9 + 本轮新增（EventChoiceManager/EventCardService/GlobalEventGate/InvestigationArbitration/BarricadeAggroHandler/CurioDeathKeepHandler/NewSignEvents/RepulsionCreeperGuard/RepulsionTeleportGuard/ModEntityEvents 等，逐类 1 条） |
| 新增爆炸事件 | G | `grep -q "class ExplosionEvent" fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/platform/event/` | **当前缺失**，需新增（§2.1） |
| 选卡会话 | G | `grep -c "CardChoiceManager" .../choice/CardChoiceManager.java` 且 `grep -q "class CardChoiceManager" ` | 类存在 + 会话 API 与 neo 同名 |
| 网络侧会话包 | G | `grep -c "CardChoice\(Close\|Confirm\)\|EventChoice\(Start\|Tick\|Confirm\|Cancel\|Close\)" .../network/ModNetwork.java` | ≥ 7（8 个消息类里 fabric 现缺 2） |
| 游戏内 | L | `grep -E "\[Astral Dice\].*event" run/logs/latest.log` | 事件触发时有点名日志（本轮未验证事件点是否有日志 → 附录 B-8 的实机项） |

### 5.3 实体与效果牌（§1.3 组三，17 类）

| 判据 | 类型 | 命令 / 模式 | 通过标准 |
|---|---|---|---|
| 实体类存在 | F | `test -f fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/entity/{ModEntityTypes,BarricadeEntity,RepulsionEntity}.java` | 3/3 |
| 实体已注册 | G | `grep -c "register\(" .../entity/ModEntityTypes.java` | ≥ 2（barricade/repulsion） |
| 属性注册接线 | G | `grep -rn "FabricDefaultAttributeRegistry" fabric-1.20.1/src/main/java \| wc -l` | ≥ 2（每实体 1 条；当前 0） |
| 渲染器注册 | G | `grep -rn "EntityRendererRegistry" fabric-1.20.1/src/main/java \| wc -l` | ≥ 2（当前 0，且 §3 第 37 项缺 `registerEntityRenderers`） |
| 模型层注册 | G | `grep -rn "EntityModelLayerRegistry\|ModelLayerLocation" fabric-1.20.1/src/main/java \| wc -l` | ≥ 2 |
| 实体贴图 | F | `test -f fabric-1.20.1/src/main/resources/assets/astral_dice/textures/entity/{barricade,repulsion}.png` | 2/2 |
| 效果牌物品 | G | `grep -c "EFFECT_CARD_BARRICADE\|EFFECT_CARD_REPULSION\|REPULSION" .../item/ModItems.java` | ≥ 3 常量 + 3 register |
| 拒止三态数据面 | G | `grep -c "REPULSION" .../component/ModDataComponents.java` | ≥ 1（三态变量需新增） |
| 路障实体清理命令 | G | `grep -c "killbarricade\|killrepulsion" .../command/AstralPartyCommand.java` | ≥ 2（当前 0，§3 第 23） |
| 光束特效发包 | G | `test -f .../network/BeamFxPayload.java && grep -c "sendBeamFx" .../combat/DamageCardFxScheduler.java` | 文件存在且 ≥1（当前两者皆缺） |
| mixin 注入守门 | S | `python tools/audit_mixin_injection.py --hint-sources` | 退出码 0（`@Redirect`/`require` 计数 = 目标方法实际调用数） |

### 5.4 立牌（§1.4 组四，19 类）

| 判据 | 类型 | 命令 / 模式 | 通过标准 |
|---|---|---|---|
| 15 个立牌类存在 | F | `ls fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/` | ≥ 15 个 `*Sign*.java` |
| 立牌物品全注册 | G | `grep -c "_SIGN," .../item/ModItems.java` | 与 neo 的立牌数量一致（§1.4 已列 7 个缺失贴图的立牌） |
| 立牌贴图 | F | `ls fabric-1.20.1/src/main/resources/assets/astral_dice/textures/item/*_sign.png` | 7 张缺项补齐（alana/ame/deepseek/dorothy/jill/kangel/lingling） |
| Curios 消费方无泄漏 | G | `grep -rn "top\.theillusivec4" fabric-1.20.1/src/main/java \| wc -l` | 0 |
| 立牌状态持久化 | G | `grep -c "SIGN_STATE" .../component/ModAttachments.java` | ≥ 1（当前 0，§3 第 2） |
| 立牌主动冷却基线 | G | `grep -c "signCooldownTicksBase" .../item/ChargeManager.java` | ≥ 1（当前 0，§3 第 39） |
| 半透明渲染 mixin | G | `grep -c "SignTranslucencyMixin" fabric-1.20.1/src/main/resources/astral_dice.mixins.json` | ≥ 1（当前 0） |
| 命名守门（英日立牌名） | S | `python tools/verify_sign_display_names.py` | 退出码 0 |
| 手册立牌页 | F | `ls fabric-1.20.1/src/main/resources/assets/astral_dice/patchouli_books/astral_guide/*/entries/signs/` | en_us 7 + zh_cn 4 补齐 |

### 5.5 角色效果（§1.5 组五，15 类）

| 判据 | 类型 | 命令 / 模式 | 通过标准 |
|---|---|---|---|
| 效果常量齐全 | G | `grep -c "DeferredHolder\|RegistryObject<MobEffect>\|register(" .../effect/ModEffects.java` | 与 neo 效果常量数一致（fabric 现缺 15） |
| 效果图标逐个存在 | S | `python tools/verify_effect_icons.py` | 退出码 0（脚本 `:52` `LINES` 已含 `fabric-1.20.1`；判据=每个 MOB_EFFECT id 必须有 `textures/mob_effect/<id>.png`） |
| 图标数对账 | G | `ls fabric-1.20.1/src/main/resources/assets/astral_dice/textures/mob_effect/*.png \| wc -l` | = 53 + 15 = 68（与 neo 一致） |
| 效果三语文案 | G | `grep -c "effect\.astral_dice\..*\.name" .../lang/en_us.json` | 与常量数一致（三语同） |
| 两处 mixin 已注册 | G | `grep -c "HealOrHarmMobEffectMixin\|RegenerationMobEffectMixin" fabric-1.20.1/src/main/resources/astral_dice.mixins.json` | = 2（当前 0） |
| 瞬时治疗来源标记 | S | `python tools/audit_mixin_injection.py --hint-sources` | 退出码 0（`applyInstantenousEffect` HEAD/RETURN 各 1 处命中） |
| 游戏内 | L | `grep -c "effect" run/logs/latest.log`（结合施加点日志） | 施加后 `effect.astral_dice.*` 名称在 tooltip 中可见（实机项，见附录 B-9） |

### 5.6 集成与数据（§1.6 组六，13 类）

| 判据 | 类型 | 命令 / 模式 | 通过标准 |
|---|---|---|---|
| JEI 可编译（若采用手工 compileOnly） | S | `grep -c "jei" fabric-1.20.1/build.gradle` | 若仍为「不参与编译」注释状态，则 JEI 类不得出现在 `src/main/java`（否则构建必挂） |
| Jade 插件入口 | G | `grep -rn "IWailaPlugin\|entrypoints.*jade" fabric-1.20.1/src/main/resources/fabric.mod.json` | ≥ 1（Jade 已是 `modImplementation`，`build.gradle:171`） |
| 手册键 ↔ lang 键闭合 | S | `python tools/audit_patchouli_keys.py -v` | 退出码 0（手册引用的每个键都在 lang 里） |
| 三语键集相等 | S | `python3 tools/check_lang_sync.py --line fabric-1.20.1` | 退出码 0 |
| 手册文件齐全 | G | `find fabric-1.20.1/src/main/resources/assets/astral_dice/patchouli_books -name "*.json" \| wc -l` | = 184（现 156） |
| 标签常量 1.20.1 化 | G | `grep -c "ResourceLocation.fromNamespaceAndPath" fabric-1.20.1/src/main/java/.../item/ModTags.java` | = 0（须用 `new ResourceLocation`） |
| 物品标签就位 | F | `ls fabric-1.20.1/src/main/resources/data/astral_dice/tags/items/` | 12 个（已是 1.20.1 复数目录） |

### 5.7 村民职业与交易（当前整条缺失）

| 判据 | 类型 | 命令 / 模式 | 通过标准 |
|---|---|---|---|
| 职业/POI 类存在 | F | `test -f fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/world/{ModPoiTypes,ModVillagerProfessions}.java` | 2/2（当前目录都不存在） |
| 职业已注册 | G | `grep -rn "Registries.VILLAGER_PROFESSION" fabric-1.20.1/src/main/java \| wc -l` | ≥ 2（card_trader/chip_trader） |
| POI 已注册 | G | `grep -rn "Registries.POINT_OF_INTEREST_TYPE" fabric-1.20.1/src/main/java \| wc -l` | ≥ 2 |
| 认领标签 | F | `test -f fabric-1.20.1/src/main/resources/data/astral_dice/tags/point_of_interest_type/{card_trader_job_site,chip_trader_job_site}.json` | 2/2（可从 forge 复制） |
| 职业纹理 | F | `test -f fabric-1.20.1/src/main/resources/assets/astral_dice/textures/entity/villager/profession/{card_trader,chip_trader}.png` | 2/2 |
| 交易注入路径 | G | `grep -rn "VillagerTradesEvent\|getOffers" fabric-1.20.1/src/main/java \| wc -l` | ≥ 1（当前 0；需新增事件或复用 `mixin/trade/`） |
| 交易类存在 | F | `test -f fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/trade/{CardTraderTrades,ChipTraderTrades}.java` | 2/2 |

### 5.8 网络与附件（跨小节基础能力）

| 判据 | 类型 | 命令 / 模式 | 通过标准 |
|---|---|---|---|
| 消息类数量对账 | G | `grep -c "registerMessage(" .../network/ModNetwork.java` | 与 forge 的 `registerMessage` 条数一致（现 fabric 少 6 条，§3 第 9） |
| 附件键数量对账 | G | `grep -c "AttachedDataKey" .../component/ModAttachments.java` | ≥ forge 侧键数（现缺 8 键） |
| 同步路径存在 | G | `grep -c "syncAttachment" .../network/ModNetwork.java` | ≥ 1（已存在） |
| 版本互通号一致 | G | `grep -n "interopVersion" .../network/VersionGate.java` | 与 forge/neo 生成规则一致（附录 B-7） |
| 游戏内网络握手 | L | `grep -c "版本互通门槛(Version Gate)" run/logs/latest.log` | ≥ 1，且双方互通号相同 |
| 饰品槽位自检 | L | `grep -c "AP_FAB_SLOT_ICON" run/logs/latest.log` | ≥ 1；`文件缺失=0 图集未收录=0` |
| 饰品适配器注册 | L | `grep -c "已为 .* 件饰品物品注册 Accessories 适配器" run/logs/latest.log` | ≥ 1 |

### 5.9 全局回归（每次提交必跑）

```bash
python tools/verify_effect_icons.py                  # 退出码 0
python tools/audit_patchouli_keys.py -v              # 退出码 0
python3 tools/check_lang_sync.py --line fabric-1.20.1 # 退出码 0
python tools/audit_mixin_injection.py --hint-sources  # 退出码 0
pwsh -NoProfile -File scripts/verify/verify_resource_integrity.ps1   # 退出码 0
grep -rn "net\.minecraftforge\|net\.neoforged\|top\.theillusivec4" fabric-1.20.1/src/main/java | wc -l  # 0
```

构建后追加：

```bash
python tools/verify_fabric_assets.py --jar fabric-1.20.1/build/libs/astral_dice-1.5.0-*.jar   # 退出码 0
```


## 附录 A：取证命令复现

所有命令在仓库根执行（Windows 上为 git-bash；以下为 bash 语法）。临时文件写到 `$TEMP`，不写仓库。

### A-1 集合运算（§1 的 130 / §3 的 259·181·144·121）

```bash
P=src/main/java/com/merlinkitsune/astral_dice
for L in neoforge-1.21.1 forge-1.20.1 neoforge-26.1.2 fabric-1.20.1; do
  (cd $L/$P && find . -name "*.java" | sed "s|^\./||" | sort) > /tmp/$L.list
  echo "$L $(wc -l < /tmp/$L.list)"
done
comm -12 /tmp/neoforge-1.21.1.list /tmp/forge-1.20.1.list > /tmp/neo_forge.list
comm -23 /tmp/neo_forge.list /tmp/fabric-1.20.1.list | tee /tmp/gap130.list | wc -l    # 130
comm -12 /tmp/forge-1.20.1.list /tmp/fabric-1.20.1.list | wc -l                        # 259 同路径共有
```

`181`（内容有差异的共有类）= 对上面 259 个路径逐个 `git diff --no-index --quiet` 判非零。

### A-2 漂移行数（§3）

```bash
P=src/main/java/com/merlinkitsune/astral_dice
git diff --no-index --numstat -- forge-1.20.1/$P fabric-1.20.1/$P \
  | awk '{print $1+$2, $3}' | sort -rn | head -45
```

### A-3 资源缺口（§4.1）

```bash
A=src/main/resources/assets/astral_dice
python - <<'EOF'
import os,hashlib
A="src/main/resources/assets/astral_dice"
dirs=["textures/gui","textures/item","textures/mob_effect","textures/block",
      "textures/entity","blockstates","models","sounds"]
def walk(base):
    out={}
    for r,_,fs in os.walk(base):
        for f in fs:
            p=os.path.join(r,f); out[os.path.relpath(p,base)]=p
    return out
tot=same=0; diff=[]
for d in dirs:
    n=walk(f"neoforge-1.21.1/{A}/{d}"); f=walk(f"forge-1.20.1/{A}/{d}"); b=walk(f"fabric-1.20.1/{A}/{d}")
    miss=[k for k in n if k not in b]; tot+=len(miss)
    sh=lambda p: hashlib.sha256(open(p,"rb").read()).hexdigest()[:16]
    for k in miss:
        if k in f and sh(n[k])==sh(f[k]): same+=1
        else: diff.append(f"{d}/{k}")
    print(d, len(n), len(f), len(b), len(miss))
print("total",tot,"same_as_neo",same,"diff",diff)
EOF
```

期望输出末行：`total 238 same_as_neo 230 diff [8 个 sounds/*.ogg]`。

### A-4 语言键差集（§4.2）

```bash
python - <<'EOF'
import json
L="src/main/resources/assets/astral_dice/lang/en_us.json"
a=json.load(open("forge-1.20.1/"+L)); b=json.load(open("fabric-1.20.1/"+L))
miss=[k for k in a if k not in b]; extra=[k for k in b if k not in a]
print(len(a),len(b),"miss",len(miss),"extra",len(extra))
from collections import Counter
import re
print(Counter((re.match(r"^[a-z_]+\.",k).group(0) if re.match(r"^[a-z_]+\.",k) else "other") for k in miss))
EOF
```

期望：`1070 844 miss 252 extra 26`。

### A-5 手册差集（§4.3）

```bash
cd neoforge-1.21.1/src/main/resources/assets/astral_dice/patchouli_books \
  && find . -name "*.json" | sort > /tmp/pm_neo.txt && cd - >/dev/null
cd fabric-1.20.1/src/main/resources/assets/astral_dice/patchouli_books \
  && find . -name "*.json" | sort > /tmp/pm_fab.txt && cd - >/dev/null
comm -23 /tmp/pm_neo.txt /tmp/pm_fab.txt | tee /tmp/pm_miss.txt | wc -l    # 28
```

### A-6 数据目录比对（§4.4）

```bash
D=src/main/resources/data/astral_dice
diff <(cd neoforge-1.21.1/$D/recipe && find . -name "*.json" | sort) \
     <(cd fabric-1.20.1/$D/recipes && find . -name "*.json" | sort)     # 3 条差（另加目录名复数化）
diff <(cd forge-1.20.1/$D/loot_tables && find . -name "*.json" | sort) \
     <(cd fabric-1.20.1/$D/loot_tables && find . -name "*.json" | sort)
```

### A-7 平台 shim 盘点（§2 的落点）

```bash
find fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/platform -name "*.java" | sort
ls fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/compat/curios
grep -n "LoaderBus.INSTANCE.register" fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/AstralDiceMod.java
```

### A-8 平台 API 泄漏扫描

```bash
grep -rn "net\.minecraftforge\|net\.neoforged\|top\.theillusivec4" fabric-1.20.1/src/main/java | wc -l
grep -rn "net\.minecraftforge\|net\.neoforged" neoforge-1.21.1/src/main/java | wc -l   # 对照组
```

## 附录 B：无法判定项

| 编号 | 项 | 为什么无法判定 | 需要什么才能定 |
|---|---|---|---|
| B-1 | 任务书参考值 `714/503/359/320/315/245/204/181/177/169/161` 与 `321 项` | 已穷举 `git diff` 选项（`--numstat/--stat/--shortstat/-w/--ignore-space-at-eol`）在本 HEAD 上均无法复现；`321` 也与我逐项枚举的 274（文件）/526（含 lang 键）都不等 | 任务书生成时的 commit 与所用命令 |
| B-2 | 手册 `141/164` 口径 | 我实测 entries = 169/142、全部 json = 184/156；任务书数字既不等于 entries 也不等于 json 总数 | 任务书的计数脚本 |
| B-3 | `trade/MerchantOfferRawAccess` 在 fabric 的等价物 | fabric `mixin/trade/MerchantOfferMixin.java:28-32` 直接覆写 `getBaseCostA` 返回星币，且 `EmeraldDiceTrade.java` 无 `rawCostA`/`isEmeraldToStarCoin`；是否存在「另一条 raw 费用读取路径」需读完整 1115 行 `EmeraldDiceTrade` 与 5 个 trade mixin 才能定 | 逐行通读 `trade/**` + `mixin/trade/**` |
| B-4 | `client/ModernUITooltipCompat` 在 fabric 是否会接管 ModernUI tooltip 边框 | fabric 用 `beginRender/endRender`（mixin 桥）替代 Forge 事件，静态比对只能看到方法名更换，**渲染结果无法静态判定** | 实机 + ModernUI 环境截图 |
| B-5 | `item/sign/FannySignItem` 两套立牌框架是否等价 | fabric 多出 12 个方法（applyEventTo/applyTimed/giveRandomEffectCard/giveCoins…），forge 版对应能力可能写在别的类里；未做逐方法语义配对 | 对读 forge 侧立牌基类 + 实机发放测试 |
| B-6 | `event/LootInjectionHandler`（缺 `onLootTableLoad`）与 `loot/FabricLootInjector` 是否等价 | fabric 无 `loot_modifiers/`，13 个 GLM 改由 Java 直注；「自有命名空间早退」这一保护在 fabric 是否有等价实现未验证 | 读 `loot/FabricLootInjector.java` 全文 + 13 个 GLM 的落点比对 |
| B-7 | `network/VersionGate` 互通号在三条线上是否同值 | 只确认了 fabric `VersionGate.java:41-42,62` 的实现位置，未与 forge/neo 的生成规则逐行比对 | 三线 `VersionGate` 对读 |
| B-8 | 事件触发/效果施加是否有可断言的机器行 | 本轮只确认了启动/饰品/版本门的机器行（§5 引用），事件与效果的施加点日志未逐条核 | `grep -rn "LOGGER.info"` 于 `event/`、`effect/` 后确认 |
| B-9 | `BarricadeEntity`(447 行)/`RepulsionEntity`(1115 行) 在 1.20.1 的逐方法签名 | 未逐方法对照 1.20.1 `LivingEntity` 的 `tick/aiStep/defineSynchedData/addAdditionalSaveData` 签名；这类重写必须逐签名核 | 与 1.20.1 反编译源逐方法对读 |
| B-10 | `ExplosionEvent` / `VillagerTradesEvent` 新事件的注入点 | fabric `platform/event/` 无这两个事件，需新写事件类 + bridge mixin；**具体注入哪个原版方法（`Explosion.finalizeExplosion` / `Villager.getOffers`）需要按 1.20.1 字节码取证** | `javap` 1.20.1 目标类 + `audit_mixin_injection.py` |
| B-11 | `verify_chip_shop_ui.ps1` 的 fabric 判据是否成立 | 脚本内 `$lines` 只映射 `neo`/`forge`（`verify_chip_shop_ui.ps1:45`），无 fabric 分支；G1..G27 的具体断言在 fabric 上是否全部适用未验证 | 先补 `fabric` → `fabric-1.20.1` 映射再跑 |
| B-12 | JEI 在 fabric 的可行性 | `build.gradle:172-176` 明确 JEI 15.62 由 Loom 1.17.20 构建、被 1.14.10 拒绝，因此「不参与编译」；是否能用「手工 compileOnly jar」绕过未做实验（且本任务禁止跑 gradle） | 需允许构建的环境 |
| B-13 | 游戏内行为层（打开商店、抽事件、路障碰撞、放拒止） | 本任务**禁止启动游戏**，§5 全部判据均为静态可执行判据 + 已存在的机器行；行为验证需另跑实机 | 实机测试台 |

---

生成信息：分支 `multi-main` / HEAD `fd990c15` / 取证时点 2026-11-01（Asia/Hong_Kong）。本文件由实测结果机械汇总，未修改任何源码、资源或构建脚本。

