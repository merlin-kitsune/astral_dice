# Astral Dice 1.5.0 跨加载器 Mapping 文档

> 文档类型：权威 mapping（四线对照）／ 唯一产物
> 生成日期：2026-11-02
> 工作树：`F:/MCProject/astral_dice_multiloader`（分支 `multi-main`）
> 取证方式：**只读**工作树源码 + grep + git（未构建、未运行、未反编译、未实机）

## 0. 阅读约定

- **路径缩写**：`item/ModItems.java:119` 表示该线 `src/main/java/com/merlinkitsune/astral_dice/item/ModItems.java` 第 119 行；`data/...`、`assets/...`、`META-INF/...` 均指该线 `src/main/resources/` 下的相对路径；`generated/...` 指 `src/generated/resources/`。跨线同名文件在表格中会补线名前缀。
- **行号为实读快照**（2026-11-02，分支 `multi-main`）。改代码后行号会漂移，结论以**文件 + 符号名**为准。
- **无法判定**一律显式写「无法判定（原因）」，不用推测填补。
- 旧报告与工作树冲突时**以工作树为准**，冲突处在 §4.3 集中登记「旧报告已过时」。
- **工作树状态按实测时点快照登记**（订正批次实测：2026-11-02T06:20+0800，`HEAD = 33fe2f5e`，2026-11-02 06:11；`git status --porcelain=v1` 按状态列分类 = **0 D / 4 M / 279 ??**（4 个修改项 = `.github/workflows/build.yml` + 3 处 `docs/**`：`docs/agent/AGENTS-dsh-linux.md`、`docs/agent/AGENTS-full.md`、`docs/archive/card-recipes-2026-10-09/README.md`，**均非本文档改动**；279 条未跟踪含 13 条目录条目）。这批未提交改动**不是本文档产生的**，本文件是本次唯一写入。该组数字是**提交完成后的时点快照**：`fd990c15`（2026-11-01 14:42）之后已再落 9 个提交（含 `c47ad8a3` fabric 第四线落盘、`12de194e` n26 两张蛟龙战斗牌回补），当时的批量删除/修改已被收掉，所以「删除 / 修改 / 未跟踪」只对上述时点成立，**引用前必须重跑** `git -C F:/MCProject/astral_dice_multiloader rev-parse --short HEAD` 与 `git status --porcelain=v1`。凡涉及 `src/generated/**` 的**文件计数**，本文按同一时点快照登记（§1.5）；判定性结论（如某配方缺解锁）一律以**符号/文件名集合**为准，不以计数为准。详见 §1.5。

---

## 1. 口径与范围

### 1.1 四条线与版本事实

| 线 | MC | 加载器 | Java / 平台 | 版本号 | 证据 |
|---|---|---|---|---|---|
| **基准线** `neoforge-1.21.1` | 1.21.1 | NeoForge 21.1.235 | Java 21（mixin `compatibilityLevel` = `JAVA_21`） | `1.5.0+neoforge_1.21.1` | `neoforge-1.21.1/gradle.properties:15`（minecraft_version）、`:21`（neo_version）、`:37`（mod_version）；`neoforge-1.21.1/src/main/resources/astral_dice.mixins.json:5` |
| 对照 ① `forge-1.20.1` | 1.20.1 | Forge 47.4.10 | Java 17（`JAVA_17`） | `1.5.0+forge_1.20.1` | `forge-1.20.1/gradle.properties:14`、`:19`、`:41`；`forge-1.20.1/src/main/resources/astral_dice.mixins.json:5` |
| 对照 ② `neoforge-26.1.2` | 26.1.2 | NeoForge 26.1.2.109 | `JAVA_25`（mixin 级） | `1.5.0+neoforge_26.1.2` | `neoforge-26.1.2/gradle.properties:17`、`:23`、`:28`、`:54`；`neoforge-26.1.2/src/main/resources/astral_dice.mixins.json:5` |
| 对照 ③ `fabric-1.20.1` | 1.20.1 | Fabric Loader 0.19.5（+ Fabric API、PuzzlesLib 8.1.33） | Java 17（`JAVA_17`） | `1.5.0+fabric_1.20.1` | `fabric-1.20.1/gradle.properties:10`、`:17`、`:29`、`:72`；`fabric-1.20.1/src/main/resources/astral_dice.mixins.json:5` |

代码面规模（旧审计快照，其 `HEAD` 记为 `fd990c15`，见 `porting/fabric-1.20.1/ALIGNMENT-AUDIT-2026-11-01.md` §1）：`.java` 文件数当时记为 **411 / 403 / 426 / 526**（该组实为当时**工作树**口径）；**当前实测（HEAD `33fe2f5e`，`git ls-tree -r --name-only HEAD` 计数）= 414 / 406 / 426 / 525**（n121 / forge / n26 / fabric 顺序恒定，后文同）。fabric 多出的量主要在 `platform/**` 自建镜像层与 `mixin/bridge/**`（20 个桥 mixin）；fabric 由 380 增至 525 是 `c47ad8a3`（第四线落盘）一次性并入的结果。

### 1.2 「同功能」判定口径

同一功能在两条线上记为「同功能」，必须**三项同时成立**：

1. **玩家可见行为一致**：同一触发条件、同一结算次序、同一可见反馈（HUD / 提示 / 数值）。反例：fabric 上 `enable_chip_recipes=false` 不移除筹码配方（§5.1③），不是同功能。
2. **注册 id 一致**：`ResourceLocation` 命名空间与路径全等（物品 / 方块 / 效果 / payload / 附件键 / 配方 id）。例证：四线都有名为 `player_starlight` 的玩家附件键（`neoforge-1.21.1/.../component/ModAttachments.java:32-33` 与 `fabric-1.20.1/.../component/ModAttachments.java:64`）。
3. **数据契约一致**：同一批 JSON 的**路径 + 字段语义**一致，或存在已声明的等价替代通道（例如 fabric 用 FAPI `LootTableEvents.MODIFY` 替代 GLM，但注入的内容语义必须可对齐）。

只有 2、3 成立而 1 不可静态判定时，标「**待验证**」，进 §6。

### 1.3 差异性质定义（全文只用这五个词）

| 性质 | 含义 | 典型判据 |
|---|---|---|
| **等价映射** | 同一 API 概念换名/换包/换代，语义不变 | `LivingIncomingDamageEvent` → `LivingHurtEvent` 之外的同名镜像、`RegistryObject` → `DeferredItem` |
| **平台替代** | 概念无对应物，用平台机制重建且**行为口径已对齐（或被声明为不同）** | fabric 的 `LoaderBus` 反射派发、`SimpleChannel` shim、FAPI 附件 |
| **裁剪** | 该线**故意不实现**（依赖不可用/版本删除） | n26 删 Iron's Spells 段、fabric 不实现 GLM |
| **缺失** | 该线**本该有但当前没有** | forge/fabric 缺 Jade 冷却 provider（R-08）。**历史项（已关闭）**：n26 曾缺 2 个战斗牌常量，2026-11-02 由 `12de194e` 补回，现四线对等（§4.2 P-5①） |
| **待验证** | 静态证据不足，或行为只在运行时成立 | U1–U9（§4.2） |

### 1.4 取证边界（本文件的硬约束）

- 无 `javap`、无构建、无实机、无日志。凡涉及「运行时会怎样」的，一律 `待验证`。
- 静态可判定的只有四类：**符号存在性**、**调用点存在性**、**数据文件存在性与字段形态**、**字符串/常量一致性**。
- 本文件不评估性能、不评估观感（除有明确常量证据外）、不评估协议兼容（除有版本门证据外）。

---

### 1.5 工作树状态与数值口径（必读）

- 本文件的**判定性结论**（同功能/差异/缺失）来自**符号、调用点、数据文件名集合**，与工作树是否脏无关。
- **计数类数值**按**实测时点快照**登记（本批次：2026-11-02T06:20+0800，HEAD `33fe2f5e`）。上一版按「工作树 / HEAD `fd990c15`」双口径登记，是因为当时 `src/generated/**` 的 datagen 清理与 fabric 第四线文件**尚未提交**；`c47ad8a3`（fabric 第四线落盘）及其后提交已把这批文件全部入库 ⇒ **现在工作树与 HEAD 在数据文件计数上一致**，双口径已无必要（差别只剩 `.cache` 未入库 blob，见 §5.1①）。

| 指标（时点快照 HEAD `33fe2f5e`；四线 = n121 / forge / n26 / fabric） | 入库（HEAD） | 磁盘（工作树） |
|---|---|---|
| 配方文件数（n121/n26 目录名为 `recipe`，forge/fabric 为 `recipes`） | 108 / 108 / 108 / 108 | 108 / 108 / 108 / 108 |
| 立牌配方（`*_sign`） | 31 / 31 / 31 / 31 | 31 / 31 / 31 / 31 |
| 立牌解锁 advancement（`*_sign`） | 31 / 31 / 31 / 31 | 31 / 31 / 31 / 31 |
| advancement 总数 | 108 / 108 / 108 / 108 | 108 / 108 / 108 / 108 |
| 生成资源文件数（`src/generated/**`；n26 含 `clientResources/` 315 个，其余三线仅 `resources/`） | 369 / 369 / 769 / 370 | 371 / 371 / 772 / 370 |
| 效果图标 `textures/mob_effect` | 67 / 67 / 68 / 67 | 67 / 67 / 68 / 67 |

> 结论：上一版的「立牌解锁缺 6 个」与「fabric 配方多 10 个 / advancement 多 16 个」都是 `fd990c15` 时点上**未提交 datagen 清理**造成的口径差，**不是 1.5.0 的差异项**。这批文件已随 `c47ad8a3` 落盘，四线在配方 / 立牌配方 / 立牌解锁 / advancement 总数上**逐项相等**，`R-02` 因此关闭（见 §4.1）。当前唯一的数据文件差是 `.cache` blob（磁盘比入库各多 2 / 2 / 3 / 0 个，不进 jar，见 §5.1①）。

---

## 2. 差异总表

> 一行一项。**基准线列**给该类/方法的实际落点；三条对照线列给「同功能的实际落点」或差异说明。末尾列只用 §1.3 的五个词。

| # | 功能域 | 基准线 neoforge-1.21.1（类/方法 + 文件:行号） | forge-1.20.1 | neoforge-26.1.2 | fabric-1.20.1 | 差异性质 |
|---|---|---|---|---|---|---|
| 1 | 物品注册容器 | `DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID)`（`item/ModItems.java:119`），注册点 `DeferredItem<Item> DICE`（`:162`） | `DeferredRegister<Item>.create(ForgeRegistries.ITEMS, MODID)`（`item/ModItems.java:120-121`），`RegistryObject<Item> DICE`（`:164`） | 同基准形态：`DeferredRegister.Items`（`item/ModItems.java:116-117`），`DeferredItem<Item>`（`:163`） | 自建 shim：`platform.registry.DeferredRegister.create(Registries.ITEM, MODID)`（`item/ModItems.java:14-15`、`:120`），返回 `RegistryObject`（`:160`）；落点 `Registry.register(...)`（`platform/registry/DeferredRegister.java:126`） | forge 等价映射 / n26 等价映射 / fabric 平台替代 |
| 2 | 创造栏 | `init/ModCreativeTabs.java:67-69`（注释「蛟龙立牌(mamushi)专属战斗牌」+ `ATTACK_CARD_BITE` + `ATTACK_CARD_DRAGON_ROAR`） | 同基准形态（内容项逐条对齐，未逐项 diff） | 同基准形态：`:67` 注释「蛟龙立牌(mamushi)专属战斗牌」→ `:68` `ATTACK_CARD_BITE` → `:69` `ATTACK_CARD_DRAGON_ROAR` → `:70`「// 防御牌」；常量 `item/ModItems.java:333`、`:342`。**两项均已存在**（`ATTACK_CARD_BITE` grep 10/10/**10**/10、`DRAGON_ROAR` 9/9/**9**/9；2026-11-02 由 `12de194e` 补回） | `FabricItemGroup.builder()`（`init/ModCreativeTabs.java:8`、`:19-20`、`:22-23`） | fabric 平台替代 / n26 与基准线对等（曾为内容面差，已由 `12de194e` 关闭，见 §4.2 P-5①） |
| 3 | 饰品注册与槽位模型 | Curios 9.x：`CuriosApi.registerCurio(...)`（`AstralDiceMod.java:177-178`）+ **数据驱动槽位** `data/astral_dice/curios/slots/{chip,dice,...}.json`、`data/curios/tags/item/*.json`（单数 `item`） | Curios 5.x：仅 `data/curios/tags/items/{chip,dice,stand}.json`（**复数 `items`**），无 `curios/slots` 目录 | 同基准（`curios_version_range=[15,)`，`neoforge-26.1.2/gradle.properties:31`；`data/astral_dice/curios/slots/*` 存在） | 三后端：Trinkets 3.7.2（`TrinketBridge.java:9`、`:51`、`:65`）+ Accessories 1.0.0-beta.48 + `compat/curios` shim（`CuriosApi.java:267` 注释「Trinkets Compat Layer 用 mixin 把 TrinketsApi 重定向到 Accessories 数据」） | forge 平台替代 / fabric 平台替代（双后端）/ n26 等价映射 |
| 4 | 饰品槽位数据契约 | `data/astral_dice/curios/**` + `data/curios/tags/item/**` | `data/curios/tags/items/**`（无 slots 定义，槽位由代码固定） | 同基准 | 同时存在 `data/curios/tags/items/*`（占位）、`data/trinkets/{entities/astral_dice.json, slots/astral_dice/{chip,dice,group,stand}.json, tags/items/astral_dice/chip.json}`、`data/astral_dice/accessories/{entity,group,slot}/**` | fabric 平台替代（契约不同）/ forge 平台替代 |
| 5 | 事件总线与订阅 | `@Mod.EventBusSubscriber` + `@SubscribeEvent`（注解命中 184 处）；入口 `AstralDiceMod.java:31`（`@Mod`）、`:38`（ctor 收 `IEventBus`）、`:75`（`modEventBus.register(this)`） | 同注解（114 处）；`AstralDiceMod.java:34`、`:41`（无参 ctor）、`:52`（`FMLJavaModLoadingContext.get().getModEventBus()`） | 同基准（190 处）；`AstralDiceMod.java:32`、`:39`、`:54-72` | **无** `@Mod.EventBusSubscriber`（42 处命中均为 `platform` 层说明/审计）⇒ `LoaderBus.INSTANCE.register(X.class)`（`AstralDiceMod.java:43`、`:141` 起约 104 处）；反射派发落点 `platform/event/LoaderBus.java:31`（`implements IEventBus`）、`:112`（`register(Object)`）、`:124`（`register(Class)`）、`:138`（`getDeclaredMethods`）、`:156`（`m.invoke`） | fabric 平台替代 / forge、n26 等价映射 |
| 6 | 伤害事件三件套 | `LivingIncomingDamageEvent` + `LivingDamageEvent` + `LivingChangeTargetEvent`（`combat/DiceCombatEvents.java:51-54`；**基准线不使用 `LivingHurtEvent`**，全 src 无该 import） | `LivingHurtEvent`（`combat/DiceCombatEvents.java:53`）+ `MobEffectEvent`（`:54`） | 同基准（`combat/DiceCombatEvents.java:63-67`） | `platform.event.entity.living.LivingHurtEvent`（`combat/DiceCombatEvents.java:47`）+ 自写桥 `mixin/bridge/LivingHurtBridgeMixin.java`（`platform/PuzzlesBridges.java:37-42`、`:61-66` 明示「语义不等价 ⇒ 必须自写」） | fabric 平台替代（语义差异，靠 mixin 补齐）/ forge 等价映射 |
| 7 | 事件桥连续性（第三方事件面） | 不需要 | 不需要 | 不需要 | PuzzlesLib 8.1.33 桥：`platform/PuzzlesBridges.java:20-24`（import 面）、`:41-58`（映射表：`LivingHurtCallback`→`LivingDamageEvent` 注入 `actuallyHurt` HEAD、`LivingAttackCallback` 由 FAPI 覆盖不重复注册、`MobEffectEvents.*`→`MobEffectEvent.*`、`AnvilRepairCallback`→`AnvilRepairEvent`）；`platform/FabricBridges.java:81`、`:115`、`:131`、`:147`、`:165`、`:174`、`:204` | fabric 平台替代 |
| 8 | 网络通道与 payload | `CustomPacketPayload` + `RegisterPayloadHandlersEvent`（`network/ModPayloads.java:16`、`:18`；`CustomPacketPayload` 命中 140） | `SimpleChannel` + `NetworkRegistry.newSimpleChannel(...)`（`network/ModNetwork.java:26`、`:33`），`CHANNEL.registerMessage(id++, ...)`（`:43` 起） | 同基准（`network/ModPayloads.java:16`、`:18`；命中 141） | 自建 shim：`platform/network/SimpleChannel.java:43`（class）、`:108`（`registerMessage`）、`:119`（`send`）、`:136`（`ServerPlayNetworking.send`）、`:163`、`:179`（`ClientPlayNetworking.registerGlobalReceiver`）、`:198`（dispatch）；调用面 `network/ModNetwork.java:3-7` | forge 等价映射（API 世代差异）/ fabric 平台替代 |
| 9 | 选卡/目标选择的收口包 | 未在 `network/ModPayloads.java` 定位同名包（无法判定：基准线该功能的包名未逐条核对） | 未定位（同上） | `CardChoiceConfirmPayload` / `CardChoiceClosePayload` 注册（`network/ModPayloads.java:101-108`） | `TargetSelectCloseMessage` 注册（`network/ModNetwork.java:127-128`，类定义 `:483`）+ 发送侧 `target/TargetSelectionManager.java:308` + 客户端 `TargetSelectionClient.java:372` | 待验证（基准线对应物未定位）；fabric 侧 C-2 静态已闭 |
| 10 | 数据生成入口 | `GatherDataEvent`（`datagen/DataGenerators.java:10`、`:17`；客户端侧 `datagen/ClientDataGenerators.java:7`、`:14-17`） | `GatherDataEvent`（3 处） | `GatherDataEvent`（9 处）+ **双输出根** `src/generated/resources` 与 `src/generated/clientResources`（各含 `.cache`；工作树 772 个文件 / HEAD 769） | FAPI datagen：`datagen/AstralDataGenerator.java:28`（class）、`:31`（`onInitializeDataGenerator`）、`:32`（`createPack()`）、`:34`/`:36`（仅 2 个 provider）；gradle 侧 `fabric-1.20.1/build.gradle:272-287`（`-Dfabric-api.datagen*` + `runDir run/datagen`） | fabric 平台替代 / n26 平台替代（双根） |
| 11 | 生成资源目录命名 | `data/astral_dice/{advancement, recipe, loot_table, loot_modifiers, enchantment, curios, damage_type, tags}` | `{advancements, recipes, loot_tables, loot_modifiers, damage_type, tags}`（复数；无 `curios`/`enchantment`/`loot_table`） | 同基准 | `{accessories, advancements, damage_type, loot_tables, patchouli_books, recipes, tags}`（复数；**无 `loot_modifiers`**） | 平台替代（MC 版本目录名） |
| 12 | Mixin 配置形态 | `astral_dice.mixins.json`：`required=true` / **无 refmap** / `JAVA_21`；另有 `astral_dice.jade.mixins.json`（`required=false`，client 2 项）与 `astral_dice.neoforge_fixes.mixins.json`（`required=false`，`defaultRequire=0`，`fixes/LivingEntityDamageContainerMixin`） | `required=true` / **`refmap=astral_dice.refmap.json`** / `JAVA_17`；含 `PiglinAiMixin`、`container.BundleInsertGuardMixin`、`container.ShulkerBox*` | `required=true` / 无 refmap / `JAVA_25`；额外 `astral_dice.ysm.mixins.json`（`plugin=...AstralYsmMixinPlugin`，client 3 项） | `required=true` / **refmap** / `JAVA_17`；含 `PiglinAiMixin`、`ItemStackMaxCountMixin`、`PlayerItemDropGuardMixin`、`server.ServerPlayerDropGuardMixin`、`bridge.LivingHurtBridgeMixin`（引用行 55 vs 基准 29） | 等价映射（forge/fabric 走 refmap；neo 两线无 refmap） |
| 13 | 玩家持久化：Attachment vs Capability | `AttachmentType` + `DeferredRegister`（`component/ModAttachments.java:7`、`:29`、`:32-33`，键 `player_starlight`） | Forge Capability（`component/AstralData.java:50` `getCapability`/`LazyOptional`、`:69`、`:87`）+ `component/{AttachedDataKey, ModCapabilities, ModDataComponents}.java`（`AttachedDataKey.java:11` shim 注释、`:116` 客户端同步标记） | 同基准（`component/ModAttachments.java:27` 注释「`serialize` 已只接受 `MapCodec<T>`」、`:33`、`:36-37`） | FAPI 附件：`AttachmentRegistry`/`AttachmentType`（`component/AttachedDataKey.java:5`、`:23`），`builder().persistent(codec).copyOnDeath().initializer(...).buildAndRegister(id)`（`:60-68`）；注册面 `component/ModAttachments.java:64` 起 | fabric 平台替代 / forge 平台替代（**存档布局不同**，`AttachedDataKey.java:47-49` 明示不兼容） |
| 14 | 附件/能力注册时机 | 由 `AstralDiceMod.java:60`（`ModAttachments.ITEMS/ATTACHMENTS.register`）在 mod 构造期完成 | 同（`:57-80` 批量注册） | 同（`AstralDiceMod.java:54-72`） | 必须在玩家数据反序列化**之前**（`ModAttachments#ensureRegistered()`，见 `porting/CROSS-VERSION-DIFFS.md` B5）；工作树内未逐行复核调用点 | 待验证（fabric 时序） |
| 15 | 配置系统 | `ModConfigSpec`（`config/ModCommonConfig.java:4`）；字段 `SPEC`（`:33`）、`GIVE_GUIDE_BOOK_ON_FIRST_JOIN`（`:35`）、`ALLOW_FIREARM_DAMAGE`（`:36`）；`neoforge-1.21.1/.../ModCommonConfig.java:11-12` 注明与 Forge 版不通用、不可下沉共享库 | `ForgeConfigSpec`（`config/ModCommonConfig.java:4`），字段位置同上 | 同基准（`:4`、`:33`、`:35`、`:36`） | **自建 shim** `platform.config.ForgeConfigSpec`（`config/ModCommonConfig.java:3`；类文件 `platform/config/ForgeConfigSpec.java`），字段 `:32`、`:34`、`:35` | fabric 平台替代 / forge、n26 等价映射 |
| 16 | 村民交易 | 事件注册：`trade/{CardTraderTrades, ChipTraderTrades, EmeraldDiceTrade}.java` + `shop/CardTradeFilter.java` + `mixin/trade/{MerchantContainerMixin, MerchantMenuMixin, MerchantOfferMixin, ServerPlayerMixin}.java`（`VillagerTradesEvent`） | 同基准形态（`VillagerTradesEvent` 14 处、`TradeOffer` 同构） | **数据驱动**：`VillagerTradesEvent` 已删（`datagen/ModVillagerTradeProvider.java:31` 注释），改 `data/<ns>/villager_trade/<职业>/<等级>/<名字>.json`（`:34`；provider `:49`、`:57`、`:64`、`:79`）+ `trade/VillagerTradeEntry.java`；产物目录 `generated/data/astral_dice/villager_trade` 与 `tags/villager_trade`（仅本线） | 同基准形态（`VillagerTradesEvent` 2 处、`TradeOffer` 10 处） | n26 平台替代（事件被删除）；forge/fabric 等价映射 |
| 17 | 帕秋莉手册 | `maven.modrinth:patchouli:1.21.1-93-neoforge`（`neoforge-1.21.1/build.gradle:275`）；资产 `assets/astral_dice/patchouli_books/astral_guide/{en_us,zh_cn}` + 数据 `data/astral_dice/patchouli_books/astral_guide` | `1.20.1-85-forge`（`forge-1.20.1/build.gradle:242`），目录同形 | `maven.modrinth:patchouli:2CsnFLom`（`neoforge-26.1.2/build.gradle:226`），目录同形 | `patchouli_version=1.20.1-85-FABRIC`（`fabric-1.20.1/gradle.properties:81`）；目录同形，且**独有** `data/astral_dice/patchouli_books` 侧条目目录（另三线仅基础目录） | 等价映射（目录同形、版本不同）；fabric 数据侧多一层的用途待验证 |
| 18 | 资源与语言 | lang 键 **1142**（zh/en/ja 三语同数） | 键集合与基准**0 差**（1142） | **1166**：比基准多 **24** 键（列表见 §5.2），核心为 `railgun_ready`、`entity.astral_dice.villager.*`、`card_shop/chip_shop/event_drawer`、`fanny_event.1-11` | **1151**：比基准多 **9** 键 —— 7 个槽位名 `accessories.slot.{chip,dice,stand}`、`accessories.slot_group.astral_dice`、`trinkets.slot.astral_dice.{chip,dice,stand}`，另 2 个为 `entity.minecraft.villager.{card_trader,chip_trader}`；**无缺失键** | 等价映射 + n26 新增（内容面） |
| 19 | 模型与渲染（HUD 图层） | `RegisterGuiLayersEvent` + `RenderGuiLayerEvent.Pre`（`client/ModClientEvents.java:22`、`:77`、`:78`/`:81`/`:84`/`:91`/`:92`/`:95`；`client/CombatPowerHud.java:19-20`、`:32`） | `IGuiOverlay` + `ForgeGui` + `RenderGuiOverlayEvent`（`client/CombatPowerHud.java:16`、`:60`、`:116`、`:126`；`client/EventCardOverlay.java:12`） | `GuiLayer` + `RenderGuiLayerEvent.Pre` + `RenderPipelines.GUI_TEXTURED` + `Identifier`（`client/ModClientEvents.java:27`、`:88-98`；`client/CombatPowerHud.java:20-21`、`:59`） | 注册事件为自建镜像，**逐帧渲染走 `HudRenderCallback`**（`platform/client/event/RegisterGuiOverlaysEvent.java:16`；`client/CombatPowerHud.java:31`；`client/EventCardOverlay.java:29`） | 等价映射（neo 两线换代）/ 平台替代（forge、fabric） |
| 20 | 资源根组成 | `assets/astral_dice/{blockstates, lang, models, particles, patchouli_books, sounds, sounds.json, textures, tooltipoverhaul}` | 同基准（含 `blockstates`、`models`） | 同基准 + **独有 `assets/astral_dice/items/**`**（26.x 硬需求）+ 第二生成根 `src/generated/clientResources` | 同基准（`items/` 无；生成资源单根、无 `.cache`） | n26 平台替代（资源契约）/ fabric 等价映射 |
| 21 | Jade 兼容 | 4 个 provider：`compat/jade/{AstralDiceJadePlugin, EventDrawerCostProvider, ShopCooldownClientProvider, ShopCooldownServerDataProvider}`；`astral_dice.jade.mixins.json` client = `[ArmorValueProviderMixin, DisplayHelperArmorIconMixin]` | **缺** `ShopCooldownServerDataProvider`（3 个）；jade mixin client = `[ArmorValueProviderMixin, **ArmorElementIconMixin**]`（与 fabric **完全相同**；Jade 11.13.3 内部类不同名） | 4 个（同基准） | **缺** `ShopCooldownServerDataProvider`（3 个）；jade mixin client = `[ArmorValueProviderMixin, **ArmorElementIconMixin**]`（Jade 11.13.3 内部类不同名） | forge/fabric **缺失**（服务端冷却数据源）；Jade mixin 按 **MC 世代**分叉而非按加载器 —— forge 与 fabric 同为 `ArmorElementIconMixin`，n121/n26 才是 `DisplayHelperArmorIconMixin`（§4.1 R-04 已订正） |
| 22 | 维度与结构 | 无 | 无 | 无 | 无 | 等价映射（四线均无实现；`data` 下无 `worldgen/`、`dimension/`，src 无 `StructureType`/`DimensionType` 引用） |
| 23 | 战利品注入 | GLM：`data/astral_dice/loot_modifiers/*.json` **14** 个 + `LootModifier` 代码通道 | **13** 个 JSON；`LootModifier` 命中 24 | **14** 个 JSON；`LootModifier` 命中 0（走 n26 数据中心化通道） | **0** 个 GLM 目录 ⇒ FAPI `LootTableEvents.MODIFY`（`event/LootInjectionHandler.java:31`、`:59`；`loot/FabricLootInjector.java`；入口注释 `AstralDiceMod.java:63`） | fabric 平台替代 / forge、n26 等价映射（数量差 1 见 §5.2 待核） |
| 24 | Tacz/枪械类识别 | `combat/DiceCombatEvents.java:1759`（`isFirearmItem`），modId 名单 `:1734` 含 `tacz`，调用点 `:1824` | `:2047` / `:2022` / `:2112` | `:1597` / `:1572` / `:1660` | `:1798` / `:1773`（注释「永恒枪械工坊：零 + 其 1.21.1 NeoForge 非官方移植，两个 jar 同 modId」）/ `:1860` | 等价映射（纯字符串识别，无编译期依赖） |
| 25 | FTB Teams / OpenPAC 友军判定 | **无**：`ftbteams`/`openpac` grep 命中 0 | **无**：grep 命中 0 | `combat/PartyRelations.java`（`:43` 注释「不能编译期依赖」），`Class.forName` 反射 `:411-415`、`:550-552` | `combat/PartyRelations.java`（`:42` 同注释），反射 `:401-405`、`:540-542` | 基准/forge **缺失**（仅 2 线有）；n26/fabric 平台替代（反射）。**旧报告 F19「forge 走反射」已过时**（§4.3） |
| 26 | YSM（Yes Steve Model） | 无（ysm 文件 0） | 无（0） | **独有** 3 个 java + `astral_dice.ysm.mixins.json`（`plugin=...AstralYsmMixinPlugin`，`client=[YsmEntityRenderDispatcherMixin, YsmGeometryMixin, YsmRenderTypeMixin]`，`defaultRequire=1`）+ `client/render/ysm/YsmRenderContext.java:76` | 无（0） | n26 新增能力（非基准功能，不构成缺失） |
| 27 | Bountiful | 仅注释/池名提及（`item/ModItems.java`、`client/ClientAlpha.java`） | 同（1 处命中） | 同（1 处） | 同（1 处） | 等价映射（四线均无代码级联动） |
| 28 | 状态效果注册 | `effect/ModEffects.java` 注册 67 项；`assets/astral_dice/textures/mob_effect/` 67 张 | 67 / 67 | **68 / 68**：多注册 `railgun_ready`（`effect/ModEffects.java:157-158`，`ChipReadyEffect` 0x81D4FA；lang 键 `effect.astral_dice.railgun_ready` 亦仅本线） | 67 / 67（`railgun_ready.png` 在本线**不存在**，`find` 命中 0；`effect/ModEffects.java:141` 注释「别再按 forge-1.20.1 旧版本补回 `railgun_ready`」） | n26 新增/反向差异（§4.2 P-5②，仍待裁决） |
| 29 | 枚举扩展（如自定义 Rarity） | `META-INF/enumextensions.json`（实测存在） | **无**该文件（`ls` → No such file） | 有（同基准） | **无**该文件；稀有度走共享库 `com.merlinkitsune.starengine.item.Rarity`（`client/ChipShopClientState.java:4`、`:26` 注释「不在本类里写 `==` 链」） | forge/fabric 替代路径（fabric 用库内枚举，非原版枚举扩展）⇒ forge 侧落点待验证 |
| 30 | 平台层规模与裁剪 | 无 `platform/**` 层 | 无 | 无 | `platform/**`（registry / config / network / event / client / item / fml 自建镜像）+ `mixin/bridge/**` 20 个桥 mixin + `compat/curios/**`（`CuriosApi`/`ICurioItem`/`TrinketBridge`）；`.java` 总数 526 vs 基准 411 | fabric 平台替代（结构性） |
| 31 | 跃迁引擎 × Waystone（传送石碑）传送联动 | 反射注册三件套：`...WaystoneTeleportEvent$Pre`（`event/WaystoneWarpCompat.java:46`）、`WaystoneTeleportEntityEvent$Post`（`:48`）、`...WaystoneTeleportEvent$Complete`（`:50`），成功后打 INFO「Waystone teleport integration enabled」（`:53`）；`addListener(Class, Consumer)`（`:93-94`） | 同基准（同文件 `:41`、`:46`/`:48`/`:50`、`:53`；`addListener` 4 参带优先级 `:95-97`） | 经 Balm 事件通道注册（同一文件 `WaystoneWarpCompat.java`）：`registerWaystoneTeleportEvent`（`:161`）先经 `findBalmEventMapper`（`:180`，反射读事件的静态 `EVENT` 字段并判定接口名 `net.blay09.mods.balm.platform.event.EventMapper`）取到 `BidirectionalEventMapper`，命中即调 `EventMapper.register(...)`（`:205` / `:223`），未命中才回退 `NeoForge.EVENT_BUS.addListener(...)`（`:169-172`）；候选类链 `TELEPORT_PRE_EVENT_CANDIDATES`（`:49` = `$Pre` → `$Before` → `$Prepare`）、`isPlatformUnregistrable(Throwable)`（`:127`）仅在该平台确实不可注册时把失败从 WARN 降为 DEBUG；成功路径打 INFO「Waystone teleport integration enabled」（`:71`）。为什么不走 NeoForge 总线：Balm bound custom event（`Balmstrap.createBoundCustomEvent` 生成）的抽象父类链以 `java.lang.Object` 为直接父，被 NeoForge `bus-8.0.5` 的 `EventBus.validateAbstractChain(EventBus.java:301)` 拒绝 —— 该平台已由 Balm 通道接管，与基准线行为一致 | 同基准（同文件 `:41`、`:46`/`:48`/`:50`、`:53`；`:95-97`） | **平台替代**（26.1.2 改走 Balm 事件通道注册；行为与基准线一致，机制与实测见 §4.1 R-17） |
| 32 | 库分发形态（StarEngine 前置） | **外置**：产物不含 `META-INF/jarjar/`（`build.gradle:354-359` 自检口径为「必须无 jarjar」）；`neoforge.mods.toml:144` 声明 `versionRange="[\${starengine_version},2.0.0)"` | **外置**：同口径自检 `build.gradle:616-621`；`mods.toml:158` 同一 `versionRange` | **2026-11-02 改为外置**：移除 `jarJar(...)`，自检反转为「产物里仍出现 `META-INF/jarjar/` 才告警」（`build.gradle:357-379`，判定块 `:364-367`）；`build.gradle:254` 的 `replaceProperties` 新增 `starengine_version`；`neoforge.mods.toml:146` 新增 `versionRange="[\${starengine_version},2.0.0)"` | **2026-11-02 改为外置**：移除 Loom `include(...)`，自检反转为「产物里仍出现 `META-INF/jars/` 才告警」（`build.gradle:471-489`），并新增「整合包里缺少独立 starengine jar」告警（`:490-500`）；`gradle.properties:42` 区间由 `>=1.1.0` 收紧为 `>=1.1.0 <2.0.0`，经 `build.gradle:302` 展开进 `fabric.mod.json:44` 的 `depends.starengine` | **等价映射**（2026-11-02 统一外置，四线一致） |

### 2.1 需要额外说明的四条

**① 饰品不是「一个映射」，而是三种数据契约。** 基准线与 n26 走 Curios 9+/15 的**数据驱动槽位**（`data/astral_dice/curios/slots/*.json` + `data/curios/tags/item/*.json` 单数）；forge 走 Curios 5.x 的**代码固定槽位 + 复数标签**（`data/curios/tags/items/*.json`）；fabric 同时铺了 Trinkets（`data/trinkets/**`）与 Accessories（`data/astral_dice/accessories/**`）两套后端数据，并在 `compat/curios` 里做 API 适配。移植改动涉及槽位「尺寸/图标/生效条件」时，**不能只改一处**。

**② 事件语义在 fabric 侧不是「换名」，是「换注入点」。** `platform/PuzzlesBridges.java:29-79` 的映射表本身写明：`LivingHurtCallback` 实际对应 `LivingDamageEvent`（注入 `LivingEntity#actuallyHurt` HEAD），而 `LivingHurtEvent` 语义不等价，必须由 `mixin/bridge/LivingHurtBridgeMixin.java` 在 `LivingEntity#hurt` 上自建（`:37-42`、`:61-66`）。因此 fabric 上「伤害相关逻辑」的正确性**依赖 mixin 注入点**，不依赖事件名。

**③ n26 的两处「结构换代」改变数据契约本身，不只是 API 名。** 其一：数据生成双根（`src/generated/resources` + `src/generated/clientResources`），且物品模型改由 `assets/astral_dice/items/**` 声明；其二：村民交易从事件改为 `data/<ns>/villager_trade/**` 数据驱动（`VillagerTradesEvent` 已被上游删除，`datagen/ModVillagerTradeProvider.java:31`）。跨线同步交易内容时**必须走数据文件**，不能补事件。

**④ fabric 的「同功能」有一层显式的 shim 层，改基准线代码时要连 shim 一起改。** `platform/event/**` 是 forge 同名事件的镜像类（`LivingHurtEvent`、`PlayerEvent`、`TickEvent`、`ItemTooltipEvent`、`LootTableLoadEvent`、`OnDatapackSyncEvent` 等），业务代码 `combat/DiceCombatEvents.java:47-48` 直接 import 镜像类；镜像类由 `FabricBridges`/`PuzzlesBridges`/`LoaderBus` 三处喂数据。任何在基准线新增/改名的事件订阅，都要在 fabric 侧同时补「镜像类 + 桥 + 订阅注册」三件事。

---

## 3. 等价映射速查表

> 用于「基准线写了一行，另外三线怎么落地」。每行只给**最小可用落点**；复杂语义差异看 §2、§4。

| # | 概念 | neoforge-1.21.1（基准） | forge-1.20.1 | neoforge-26.1.2 | fabric-1.20.1 |
|---|---|---|---|---|---|
| 1 | 物品注册容器 | `DeferredRegister.createItems(MODID)` → `DeferredItem<T>` | `DeferredRegister.create(ForgeRegistries.ITEMS, MODID)` → `RegistryObject<T>` | 同基准 | `platform.registry.DeferredRegister.create(Registries.ITEM, MODID)` → `RegistryObject<T>`（落点 `Registry.register`） |
| 2 | 泛型注册容器（方块/效果/实体） | `DeferredRegister.create(Registries.X, MODID)` | `DeferredRegister.create(ForgeRegistries.X, MODID)` | 同基准 | 同基准写法，但类来自 `platform.registry` |
| 3 | 注册到 mod 总线 | `X.register(modEventBus)`（`AstralDiceMod.java:53-71`） | `X.register(FMLJavaModLoadingContext.get().getModEventBus())`（`:57-80`） | 同基准（`:54-72`） | `DeferredRegister.register(IEventBus)` 为 shim 空转/直注册（`platform/registry/DeferredRegister.java:106`、`:126`） |
| 4 | 创造栏 | `CreativeModeTab` 注册（`init/ModCreativeTabs.java`） | 同构 | 同构 | `FabricItemGroup.builder()`（`init/ModCreativeTabs.java:23`） |
| 5 | 事件订阅 | `@Mod.EventBusSubscriber` + `@SubscribeEvent` | 同基准（成员**禁止** `private`） | 同基准 | `LoaderBus.INSTANCE.register(X.class)`（`AstralDiceMod.java:141`；派发 `platform/event/LoaderBus.java:156`） |
| 6 | 伤害（可改数值前） | `LivingIncomingDamageEvent`（`DiceCombatEvents.java:53`） | `LivingHurtEvent`（`:53`） | `LivingIncomingDamageEvent`（`:65`） | `LivingHurtEvent`（镜像，`:47`）+ `mixin/bridge/LivingHurtBridgeMixin` |
| 7 | 伤害（结算后观察） | `LivingDamageEvent`（`:52`） | `LivingHurtEvent`（同点兼用） | `LivingDamageEvent`（`:64`） | `platform.event.entity.living.LivingDamageEvent`（由 `PuzzlesBridges.java:41-44` 注入 `actuallyHurt` HEAD 产生） |
| 8 | 目标切换 | `LivingChangeTargetEvent`（`:51`） | `LivingChangeTargetEvent`（Forge API） | `LivingChangeTargetEvent`（`:63`） | `LivingChangeTargetCallback`→镜像 `LivingChangeTargetEvent`（`PuzzlesBridges.java:55-56`） |
| 9 | 效果增删过期 | `MobEffectEvent.{Added,Remove,Expired}`（`:54`） | `MobEffectEvent.*`（`:54`） | 同基准（`:67`） | `MobEffectEvents.Apply/Remove/Expire`→镜像（`PuzzlesBridges.java:52-54`） |
| 10 | 工具提示 | `ItemTooltipEvent`（`event/ModTooltipHandler.java:23`） | `net.minecraftforge.event.entity.player.ItemTooltipEvent`（`event/ModTooltipHandler.java:22`） | 同基准（`combat/DiceCombatEvents.java:69`） | 镜像 `platform.event.entity.player.ItemTooltipEvent`（`event/ModTooltipHandler.java:22`）+ `ItemTooltipCallback`（客户端） |
| 11 | 服务端 tick | `ServerTickEvent.Post`（`combat/DamageCardFxScheduler.java:30`、`:281`） | `TickEvent.ServerTickEvent` | 同基准（`DiceCombatEvents.java:68`） | `ServerTickEvents.END_SERVER_TICK`（`platform/FabricBridges.java:101`） |
| 12 | 玩家加入/离开 | （基准线由附件/事件面处理，未逐条定位） | `PlayerEvent.PlayerLoggedInEvent` / `PlayerLoggedOutEvent` | 同基准 | `ServerPlayConnectionEvents.JOIN/DISCONNECT`（`platform/FabricBridges.java:148`、`:150`） |
| 13 | 死亡后复活复制 | 附件 `.copyOnDeath()`（`component/ModAttachments.java:32-33`） | 手写复制表（`component/AstralData.java:69`、`:87`） | 附件 `.copyOnDeath()`（`:36-37`） | FAPI `builder().copyOnDeath()`（`component/AttachedDataKey.java:61-66`） |
| 14 | 玩家持久数据读写 | `player.getData(ATTACHMENT)` | `player.getCapability(...)` + `LazyOptional.orElse(null)`（`component/AstralData.java:50`） | 同基准 | `AttachedDataKey` 包装（`component/ModAttachments.java:64` 起） |
| 15 | 网络发送 | `PacketDistributor.sendToPlayer(...)`（CustomPacketPayload） | `CHANNEL.send(PacketDistributor.PLAYER.with(...), msg)`（`network/ModNetwork.java:33`、`:43`） | 同基准 | `SimpleChannel.send`（`platform/network/SimpleChannel.java:119`）→ `ServerPlayNetworking.send`（`:136`） |
| 16 | 客户端上行包 | `PacketDistributor.sendToServer(...)` | `CHANNEL.sendToServer(msg)` | 同基准 | `SimpleChannel.sendToServer`（`SimpleChannel.java:141`） |
| 17 | 客户端收包 | `RegisterPayloadHandlersEvent` 内注册 handler | `CHANNEL.registerMessage(id++, Msg.class, encoder, decoder, handler)` | 同基准 | `ClientPlayNetworking.registerGlobalReceiver`（`SimpleChannel.java:179`） |
| 18 | 配置文件 | `ModConfigSpec`（`config/ModCommonConfig.java:4`） | `ForgeConfigSpec`（`:4`） | 同基准 | `platform.config.ForgeConfigSpec`（`:3`） |
| 19 | 数据生成入口 | `GatherDataEvent`（`datagen/DataGenerators.java:17`） | `GatherDataEvent` | `GatherDataEvent`（双根） | `DataGeneratorEntrypoint`（`datagen/AstralDataGenerator.java:31`）+ `pack.addProvider(...)` |
| 20 | 生成输出目录 | `src/generated/resources` | 同基准 | `src/generated/resources` + `src/generated/clientResources` | `src/generated/resources`（gradle `-Dfabric-api.datagen.output-dir=`，`build.gradle:272-287`） |
| 21 | 村民交易内容 | `VillagerTradesEvent` 注册 listing | 同基准 | `data/<ns>/villager_trade/<prof>/<level>/<name>.json`（provider `ModVillagerTradeProvider`） | `VillagerTradesEvent` 镜像 |
| 22 | 战利品注入 | GLM JSON（`data/astral_dice/loot_modifiers/`）或 `LootTableLoadEvent` | 同基准 | 同基准 | `LootTableEvents.MODIFY`（`event/LootInjectionHandler.java:31`） |
| 23 | 配方条件 | `neoforge:conditions`（`astral_dice:chip_recipes_enabled`） | `conditions`（无命名空间） | `neoforge:conditions` | **无对应机制**（`recipe/condition/ChipRecipesEnabledCondition.java:14-18`）⇒ 当前无效果（§5.1③） |
| 24 | 饰品 API | Curios `ICurioItem` / `CuriosApi.registerCurio`（`AstralDiceMod.java:177-178`） | Curios 5.x 同 API | Curios 15（槽位修饰符 + `update()`） | `compat/curios`：`TrinketBridge`→`TrinketsApi.registerTrinket`（`TrinketBridge.java:65`） |
| 25 | HUD 图层 | `RegisterGuiLayersEvent.registerAbove(...)`（`client/ModClientEvents.java:77-95`） | `IGuiOverlay` + 注册事件 `registerAbove`（`client/CombatPowerHud.java:116`、`:126`） | 同基准 + `GuiLayer`（`:88-98`） | 镜像 `RegisterGuiOverlaysEvent` + `HudRenderCallback` 逐帧（`platform/client/event/RegisterGuiOverlaysEvent.java:16`） |
| 26 | mixin 重映射 | 无 refmap（Mojmap） | `refmap=astral_dice.refmap.json` | 无 refmap | `refmap=astral_dice.refmap.json`；`@Inject` 只写方法名（不带描述符） |
| 27 | 自定义 Rarity | `META-INF/enumextensions.json` | 无该文件（落点待验证） | 同基准 | 共享库 `starengine.item.Rarity`（`client/ChipShopClientState.java:4`） |

---

## 4. 已知实现差异与风险清单

### 4.1 风险条目（差异 → 影响 → 是否需要同步 → 证据）

| 编号 | 差异 | 影响 | 需同步? | 证据（文件:行号） |
|---|---|---|---|---|
| R-01 | fabric 的 chip 配方**无条件块**，另三线各有 **67 个配方 + 67 个 advancement**（合计 134 个文件）带 `chip_recipes_enabled` 条件 | 玩家在 fabric 上把 `enable_chip_recipes` 关掉**不会**移除筹码配方；另三线会 | **是**（要么补机制，要么在文档/配置说明里声明 fabric 不支持） | 条件文件计数：配方 67/67/67/0、advancement 67/67/67/0（旧记「134 个配方」是把两类相加后的重复计数）；`fabric-1.20.1/src/generated/resources/data/astral_dice/recipes/airbag_chip.json`（head 无 `conditions`）；`recipe/condition/ChipRecipesEnabledCondition.java:14-18`、`:52-65`、`:70+` |
| R-02 | **已关闭**（曾：fabric 的立牌解锁 advancement 只有 **25** 个，另三线各 **31** 个；缺 `advancements/recipes/misc/{alana,ame,dorothy,jill,kangel,lingling}_sign.json`） | 曾使这 6 个立牌配方不自动进配方书（仍可合成、JEI 可见）；现已四线对等 | 否（已关闭） | 实测（HEAD `33fe2f5e`）：四线 `src/generated/**/advancements` 下 `*_sign.json` 均 **31**，配方侧同为 31/31/31/31；关闭于 `c47ad8a3`（fabric 第四线落盘，6 个解锁文件入库），`fd990c15` 上实测确为 31/31/31/25 |
| R-03 | fabric 生成资源**无 `.cache`** | 不影响玩家（不进 jar）；影响 datagen 增量与产物可复现 | 否（工程面）/ 建议在 build 文档标注 | `fabric-1.20.1/build.gradle:29`、`:37-40`（`exclude("**/.cache")`）；四线 `git ls-files` 对 `.cache` 命中均为 0 |
| R-04 | Jade mixin 目标按 **MC 世代**分叉，**不是按加载器分**：forge 与 fabric **同为** `ArmorElementIconMixin`（都是 1.20.1 + Jade 11.13.3），n121 与 n26 才是 `DisplayHelperArmorIconMixin` | 1.20.1 世代 Jade 的 `ArmorElement#render` 走 `IconUI` 硬编码 UV（`DisplayHelper.renderIcon`），1.21 世代改为 sprite ⇒ 升级 Jade 时 **forge 与 fabric 会同时**静默失配 | **是**（升级 Jade 必查，且两条 1.20.1 线要一起查） | 四线 `src/main/resources/astral_dice.jade.mixins.json` 的 `client` 数组实测：n121/n26 = `[ArmorValueProviderMixin, DisplayHelperArmorIconMixin]`，**forge/fabric = `[ArmorValueProviderMixin, ArmorElementIconMixin]`**；版本依据 `forge-1.20.1/build.gradle:276`（`Jade-1.20.1-Forge-11.13.3.jar`）与 `fabric-1.20.1/gradle.properties:87`（`jade_version=11.13.3`）；fabric 的 `mixin/jade/ArmorElementIconMixin.java` 头注释自述「与 forge 线相同」 |
| R-05 | **已关闭**（曾：n26 缺 2 个战斗牌常量 `ATTACK_CARD_BITE` / `ATTACK_CARD_DRAGON_ROAR`） | 曾少两张「蛟龙立牌(mamushi)专属战斗牌」的创造栏条目；现与基准线对等 | 否（已关闭） | 修复于 `12de194e`（2026-11-02 06:10，restore two dragon cards）：`neoforge-26.1.2/.../init/ModCreativeTabs.java:67`（注释）→ `:68` `ATTACK_CARD_BITE` → `:69` `ATTACK_CARD_DRAGON_ROAR` → `:70`「// 防御牌」；常量 `neoforge-26.1.2/.../item/ModItems.java:333`、`:342`。四线 `grep -rn` 计数 = `ATTACK_CARD_BITE` **10/10/10/10**、`ATTACK_CARD_DRAGON_ROAR` **9/9/9/9** |
| R-06 | n26 多注册 `railgun_ready` 效果（68 vs 67），lang 键仅本线有 | 内容面反向差（另三线无该效果） | 待裁决（保留 or 三线补齐） | `neoforge-26.1.2/.../effect/ModEffects.java:157-158`；`fabric-1.20.1/.../effect/ModEffects.java:141` 明确禁止补回；图标 67/67/**68**/67 |
| R-07 | n26 村民交易改为数据驱动，`VillagerTradesEvent` 在上游已删 | 交易内容改动**只能改数据文件**；沿用事件写法在该线不生效 | **是**（跨线同步交易时） | `neoforge-26.1.2/.../datagen/ModVillagerTradeProvider.java:31-34`、`:49`、`:64`、`:79`；产物 `generated/data/astral_dice/villager_trade` 仅本线 |
| R-08 | forge 与 fabric 缺 Jade `ShopCooldownServerDataProvider` | 商店冷却的**服务端数据源**缺失 ⇒ 客户端显示可能退化为无冷却/静态值 | **是**（行为面，需实测确认退化程度） | n121 与 n26 各 4 个 `compat/jade/*`；forge 与 fabric 各 3 个（无该 provider） |
| R-09 | fabric 把 `LivingHurtEvent` 语义交由自写 mixin 承担（`LivingEntity#hurt`） | 注入点漂移（MC 更新/其它 mixin 冲突）会让**所有伤害相关逻辑**静默失效，而非报错 | **是**（每次升 MC 版本必验） | `platform/PuzzlesBridges.java:37-42`、`:61-66`；`mixin/bridge/LivingHurtBridgeMixin.java` |
| R-10 | fabric 事件派发是**反射**（`LoaderBus` 扫描 `getDeclaredMethods` + `m.invoke`），且不递归内部类 | 反射失败/注解拼错时**静默不派发**（无编译错误）；内部类订阅者不会被注册 | **是**（新增订阅者时） | `platform/event/LoaderBus.java:112`、`:124`、`:138`、`:156`；`platform/event/SubscriptionAudit.java`（自检护栏） |
| R-11 | fabric `@Inject` 必须只写方法名（带描述符会不重映射），且依赖 loom 自动生成 refmap | 生产环境崩（dev 不崩） | **是**（新增 mixin 时） | `fabric-1.20.1/src/main/resources/astral_dice.mixins.json:4`（`refmap`）；`porting/fabric-1.20.1/API_CHEATSHEET.md` §7 口径 |
| R-12 | forge 的 `@SubscribeEvent` 成员不得 `private`；`@Mod.EventBusSubscriber` 只扫顶层类 | 静默不注册 | **是**（新增订阅者时） | `porting/CROSS-VERSION-DIFFS.md` F4（旧报告口径，工作树逐条未复核） |
| R-13 | fabric 存档布局与 forge **不兼容**（FAPI 附件段 vs `ForgeData → astral_data`） | 跨线换包会丢玩家附件数据 | 无需改代码，需在迁移/升级说明中声明 | `fabric-1.20.1/.../component/AttachedDataKey.java:47-49` |
| R-14 | fabric 饰品后端三选一（Trinkets / Accessories），`TrinketBridge` 在 Trinkets 缺席时**直接返回** | Accessories 后端下部分 Curios 语义可能不生效（静默） | **是**（需实机验证两个后端各一遍） | `compat/curios/TrinketBridge.java:51`、`:65`；`compat/curios/CuriosApi.java:267` |
| R-15 | n26 独有 `assets/astral_dice/items/**` 与 `clientResources` 生成根 | 新增物品时该线必须补 `items/<id>.json`，否则模型缺失（另三线不需要） | **是**（新增物品时） | `neoforge-26.1.2/src/main/resources/assets/astral_dice/items/`（仅本线存在）；`src/generated/clientResources`（仅本线） |
| R-16 | 基准线与 n26 有 `META-INF/enumextensions.json`，forge 与 fabric **无** | 新增「原版枚举扩展」类内容（自定义稀有度等）在 forge/fabric 上落点不同 | 待验证（forge 落点未逐行核对；fabric 已知走库内 `starengine.item.Rarity`） | `neoforge-1.21.1/src/main/resources/META-INF/enumextensions.json`、`neoforge-26.1.2/...`（存在）；forge/fabric `ls` → No such file；`fabric-1.20.1/.../client/ChipShopClientState.java:4` |
| R-17 | **26.1.2 的「跃迁引擎 × 传送石碑」联动已通过 Balm 事件通道注册成功（2026-11-02 收口，上一批「该联动不可用」的判定已作废）** | 无玩家可见差异：26.1.2 上同维度传送照常给充能与迅捷、跨维度传送照常按 Waystone 结算（`isPendingAndClear` 正常翻转），与基准线一致 | 否 | 机制（实现差异记录，非缺陷）：事件类自带 `public static final BidirectionalEventMapper<Consumer<X>> EVENT`，`BidirectionalEventMapper` 实现 `net.blay09.mods.balm.platform.event.EventMapper`，其 `register(TCallback)` 才是 Balm 注册入口；故不走 `NeoForge.EVENT_BUS`（其 `EventBus.validateAbstractChain(EventBus.java:301)` 会因「抽象父类链以 `java.lang.Object` 为直接父」抛 `IllegalArgumentException: Abstract event class ... has a non-abstract superclass class java.lang.Object.`）。证据（当前工作树）：`neoforge-26.1.2/src/main/java/com/merlinkitsune/astral_dice/event/WaystoneWarpCompat.java:161`（`registerWaystoneTeleportEvent`）、`:180`（`findBalmEventMapper` 反射读 `EVENT`）、`:205` / `:223`（`EventMapper.register`）、`:169-172`（回退总线）、`:49`（候选链）、`:127`（`isPlatformUnregistrable`）、`:71`（成功 INFO）；1.21.1 仍走总线 `WaystoneWarpCompat.java:92-94`。实测：`run/26.1.2/prodgame/logs/latest.log:525` = `[0211月2026 05:42:39.922] [modloading-worker-0/INFO] [com.merlinkitsune.astral_dice.event.WaystoneWarpCompat/]: [Astral Dice] Waystone teleport integration enabled for 跃迁引擎.`（NeoForge 26.1.2.109） |
| R-18 | **2026-11-02 库分发形态统一为「外置前置」，四线一致**：四条线产物都不再内嵌 StarEngine（n121 / forge 此前已完成，n26 的 `jarJar` 与 fabric 的 Loom `include` 本批撤除）；版本区间为 NeoForge 两线 `[1.1.0,2.0.0)`、fabric 线 `>=1.1.0 <2.0.0`（等价） | 玩家与整合包必须单独放置对应平台的 starengine jar，缺库或越界会以「Missing or unsupported mandatory dependencies」拒绝启动；四份库产物互不通用，不能跨线复用 | 否（四线一致，无跨线差异需同步） | 证据（当前工作树）：`neoforge-26.1.2/build.gradle:364-367`（自检已反转为「出现 META-INF/jarjar/ 才告警」）、`:254`（`replaceProperties` 新增 `starengine_version`）、`neoforge-26.1.2/src/main/templates/META-INF/neoforge.mods.toml:146`；`fabric-1.20.1/build.gradle:87`（裁决注释）、`:302`、`:471-489`、`:490-500`；`fabric-1.20.1/gradle.properties:40`、`:42`；`fabric-1.20.1/src/main/resources/fabric.mod.json:44`；`neoforge-1.21.1/src/main/templates/META-INF/neoforge.mods.toml:144`、`forge-1.20.1/src/main/templates/META-INF/mods.toml:158` |

### 4.2 fabric 三个未收口项 + P-5 的当前真实状态

> 旧审计：`porting/fabric-1.20.1/ALIGNMENT-AUDIT-2026-11-01.md`（HEAD `fd990c15`）。下表为 **2026-11-02 工作树复核**。

#### C-3（SignHookDispatcher 是否仍是死代码）——**已收口：整类已物理删除**

- 工作树事实：fabric 全 `src` 对 `SignHookDispatcher` / `SignHooks` grep 命中 **0**；`item/sign/` 目录下两者均不存在。
- 删除记录：`porting/fabric-1.20.1/B3-SIGNHOOK-DEADCODE-REPORT.md`（物理删除 `SignHookDispatcher.java` 189 行 + `SignHooks.java` 73 行，并移除 `AlanaSignItem implements`）。
- 外部调用者数量：**0**（类已不存在，任何调用都会编译失败 ⇒ 静态上不可能有遗漏调用）。
- 对照：`SignHookDispatcher` 在基准线/forge 各 17 处命中、n26 14 处（这些线的钩子链仍在，见 R 组无对应项）。
- 结论：C-3 **不再是风险项**；但 fabric 因此失去「立牌 hook」通道，若后续要把立牌钩子行为拉回 fabric，属于**功能补齐**而非修 bug。

#### P-5（裁决状态）——**两条差额：① 已关闭，② 仍待裁决**

P-5 原文只写「n26 内容面差额」。复核后它是**两条独立差额**，都不是平台差异：

1. **P-5①（已关闭）n26 曾缺 2 个战斗牌常量** —— 2026-11-02 复核：`neoforge-26.1.2/.../init/ModCreativeTabs.java` 现为 `:67` 注释「蛟龙立牌(mamushi)专属战斗牌」→ `:68` `ATTACK_CARD_BITE` → `:69` `ATTACK_CARD_DRAGON_ROAR` → `:70`「// 防御牌」，常量定义在 `item/ModItems.java:333`、`:342`。四线 `grep -rn` 计数已对等：`ATTACK_CARD_BITE` 10/10/**10**/10、`ATTACK_CARD_DRAGON_ROAR` 9/9/**9**/9（n121/forge/n26/fabric）。**关闭提交** `12de194e`（fix(26.1.2) restore two dragon cards，2026-11-02 06:10，为当前 HEAD `33fe2f5e` 的祖先，merge-base --is-ancestor 通过；该提交对本文件正好 3 +++）。**历史说明**：`fd990c15` 时点 n26 确实缺这两行（当时计数 9 与 8），本条曾是真实差异，现由提交关闭，**不再需要裁决**。
2. **P-5②（仍待裁决）n26 多注册 `railgun_ready` 效果**：`effect/ModEffects.java:157-158`（`EFFECTS.register(railgun_ready, () -> new ChipReadyEffect(0x81D4FA))`），效果总数 **68 vs 67**，图标 `textures/mob_effect/railgun_ready.png` 仅 n26 有（其余三线 `find` 命中 0），lang 键 `effect.astral_dice.railgun_ready` 仅本线存在；`fabric-1.20.1/.../effect/ModEffects.java:141` 有一条反向纪律注释（「别再按 forge-1.20.1 旧版本补回 `railgun_ready`」）。注意：`ChipReadyEffect.java` 与 `RailgunChipItem.java` 四线**都有**，缺的只是注册项本身。

⇒ **仍需裁决的问题（只剩 ②）**：`railgun_ready` 是「另三线补齐」还是「n26 删除」。属内容决策，静态无法判定哪边是期望态。（原问题里的「n26 是否补齐 2 个战斗牌」一半已由 `12de194e` 关闭。）

#### U1–U9（哪些只能靠实机验证）

| 编号 | 条目 | 当前静态状态 | 是否只能实机 | 证据 / 覆盖建议 |
|---|---|---|---|---|
| U1 | 激光/光束 FX 观感 | **部分收口**：`BEAM_PARTICLES` 常量在 n26 已无任何引用（P-4 已闭），仅基准/forge/fabric 的 `BeamFxRenderer` javadoc 提及旧实现（`client/BeamFxRenderer.java:28` / forge `:28` / fabric `:26`） | **是**（观感无法静态判定） | 覆盖：`porting/fabric-1.20.1/LIVE-TEST-LIST-2026-11-02.md:440`（LT6） |
| U2 | S2C 同步的去重与过期 | 逻辑可读，行为时序不可判 | **是** | `fabric-1.20.1/.../component/AttachedDataKey.java:30-33`（注释：同步仍走自有 S2C 包，含「缺失键下发显式默认值」防残留） |
| U3 | 动态饰品槽位 resize | 静态只剩「已按修饰符实现」的注释面 | **是** | 覆盖：`LIVE-TEST-LIST-2026-11-02.md:122`（LT1）；参照 `mc-curios-slot-sizing-and-gating` 技能的绝对修饰符判据 |
| U4 | MergedHandler 读写归属（双后端合并） | 静态不可判 | **是** | 覆盖：LT1 |
| U5 | 蹲右键装备的落点（槽位 vs 手持） | 静态不可判 | **是** | 覆盖：LT1 |
| U6 | 死亡不掉落 | **部分收口**：`copyOnDeath` 已在 fabric 附件上注册（`component/AttachedDataKey.java:61-66`），且有 `component/DeathPreservedBonuses.java:39-55` 的兜底说明（死亡时立牌先掉出槽 ⇒ 早于克隆复制） | **是**（槽位掉落与复制次序需实机） | 覆盖：`LIVE-TEST-LIST-2026-11-02.md:374`（LT5） |
| U7 | C-2 客户端选卡关闭模式 | **静态已闭**：`TargetSelectCloseMessage` 注册（`network/ModNetwork.java:127-128`，类 `:483`）+ 发送（`target/TargetSelectionManager.java:308`）+ 接收（`TargetSelectionClient.java:372`） | **是**（行为需实机） | 覆盖：`LIVE-TEST-LIST-2026-11-02.md:482`（LT7） |
| U8 | 跨线协议兼容 / VersionGate | 静态不可判（需两线同时在线） | **是** | 覆盖：LT7 |
| U9 | n26 未注册 payload 的抛点 | **静态已闭**：`CardChoiceConfirmPayload` / `CardChoiceClosePayload` 已注册（`network/ModPayloads.java:101-108`） | **是**（运行期抛点需实机） | 覆盖：LT7；n26 侧与 LT7 的「未注册 payload」用例对应 |

> 统计：U1–U9 共 **9 项**，其中 **3 项**（U6/U7/U9）静态已出现收口证据、仍需实机确认行为；**6 项**（U1–U5、U8）静态无法判定。**全部 9 项都必须在实机用例里覆盖**。

### 4.3 旧报告已过时（以工作树为准）

| 文档 | 旧结论 | 工作树实测 |
|---|---|---|
| `porting/CROSS-VERSION-DIFFS.md` F19 | 「Iron/Ars/Goety 用 damage-type 字符串，**FTB/OPAC/女仆走反射**」暗示 forge 线有反射接入 | `ftbteams`/`openpac` 在 **n121 与 forge 均 0 命中**；`combat/PartyRelations.java` **只在 n26 与 fabric** 存在 |
| `porting/CROSS-VERSION-DIFFS.md` B5 | 「附件键须早于玩家数据反序列化（`ModAttachments#ensureRegistered()`）」 | 调用点在 fabric 侧未逐行定位 ⇒ 本文件按**待验证**处理（R-14 / §1.4） |
| `porting/fabric-1.20.1/ALIGNMENT-AUDIT-2026-11-01.md` §3 | C-1 / C-2 / P-1 / P-2 / P-3 / P-4 为未收口缺陷 | **全部已闭**：C-1（`combat/DiceCombatEvents.java:593`/`:596`/`:601`）、C-2（见 §4.2 U7）、P-1（`item/chip/BankCardUnlimitedChipItem.java:33`、`:87`）、P-2（`network/ModPayloads.java:101-108`）、P-3（`combat/DiceCombatEvents.java:26`、`:367`、`:442`、`:446`、`:543`）、P-4（n26 无 `BEAM_PARTICLES` 引用） |
| `neoforge-1.21.1/.../datagen/ClientDataGenerators.java:14-17` 注释 | 描述 `GatherDataEvent` 已 abstract、`Cannot register listeners for abstract class` | 该描述对应 **n26** 的行为；基准线自身仍为具体类（该注释是前瞻性说明）⇒ 引用时勿当成基准线现状 |
| `fabric-1.20.1/.../recipe/condition/ChipRecipesEnabledCondition.java:47-50` 注释 | 「两线 `data/astral_dice/recipes/` 下没有任何 JSON 使用该条件（全量 grep `conditions` = 0）」 | **已过时**：n121/forge/n26 各有 **134** 个配方使用 `chip_recipes_enabled`（fabric 0 个） |
| `fabric-1.20.1/build.gradle:29` 注释 | 「137 个物品模型 + 118 个配方 + 118 个进度」 | 与实物不符（实测 HEAD `33fe2f5e`：fabric 生成资源 **370** 个文件、配方 **108** 个、advancement **108** 个；注释里的 137/118/118 是旧口径） |
| `fabric-1.20.1/.../component/ModAttachments.java:18` 类注释 | 「玩家附件注册中心(1.20.1 Forge 移植版)… 内部由 `AttachedDataKey`(AstralData Capability / ForgeData) 承载」 | **已过时**：fabric 实际走 FAPI 附件（`component/AttachedDataKey.java:5` import `AttachmentRegistry`，`:60-68` 构建注册） |

---

## 5. 数据与资源差异

### 5.1 fabric vs 基准线：精确差异 + 玩家影响（① 仍成立 / ② 已关闭 / ③ 仍成立）

| 项 | 基准线（n121 / forge / n26） | fabric | 精确差异 | 是否影响玩家 |
|---|---|---|---|---|
| ① datagen `.cache` | `src/generated/resources/.cache` 存在（n26 另有 `clientResources/.cache`）；`git ls-files` 对 `.cache` 命中 0（即**未入库**） | 无任何 `.cache`（对 `src` 全量 find 命中 0） | FAPI 1.20.1 datagen 不产出 cache 文件；且 `build.gradle:40` 主动 `exclude("**/.cache")`，而 `:37-40` 注释承认 `exclude` 模式相对各 srcDir 根解析、`src/generated/**/.cache` 永不命中 ⇒ 本地可能残留、CI 不残留 | **否**（cache 不进 jar）。影响的是「增量 datagen 与产物可复现」这一工程面 |
| ② 立牌配方与其解锁 advancement | 立牌配方 31 个、解锁 31 个（forge / n26 同） | **31 / 31（与基准线对等，已关闭）** | `fd990c15` 时 fabric 只有 25 个立牌配方 + 25 个解锁（缺 `advancements/recipes/misc/{alana,ame,dorothy,jill,kangel,lingling}_sign.json` 及其配方）；这批文件随 `c47ad8a3` 落盘，HEAD `33fe2f5e` 实测 31/31 | **否（已关闭）**：6 个立牌配方与其解锁均已存在，四线一致 |
| ③ chip 配方 `conditions` | `neoforge:conditions`（n121/n26）或 `conditions`（forge，无命名空间）—— 各 **67 个配方 + 67 个 advancement**（合计 134 个文件）使用 `astral_dice:chip_recipes_enabled` | 生成物**完全无条件块**（0 个配方、0 个 advancement） | 配方名集合四线一致（108 个 recipe json，diff 为空）⇒ 差异只在条件块 | **是（功能配置失效）**：fabric 上 `enable_chip_recipes=false` 不移除筹码配方 |

补充（chip 条件在 fabric 侧的落点）：`recipe/condition/ChipRecipesEnabledCondition.java:14-18` 自述「占位重写…Fabric 1.20.1 没有任何对应机制（原版 `RecipeManager` 不支持外部条件；`ResourceCondition` 是 1.21.2+）」；`:52-65` 的 `isEnabled()` 读 `ModCommonConfig.ENABLE_CHIP_RECIPES` 但**无调用方**；`:70+` 的 `register()` 为空 + `TODO(CHIP-RECIPES-MECHANISM)`。⇒ 若要收口，需要机制层方案（自定义配方类型/过滤器/加载期剔除），不是改 JSON 能解决的。

### 5.2 其它数据面差异

| 面 | 基准线 | forge | n26 | fabric | 性质 / 玩家影响 |
|---|---|---|---|---|---|
| 数据目录命名 | `advancement/ recipe/ loot_table/`（单数） | `advancements/ recipes/ loot_tables/` | 同基准 | `advancements/ recipes/ loot_tables/` | MC 版本差异；数据包作者可见，玩家无感 |
| 饰品标签目录 | `data/curios/tags/item/*.json` + `data/astral_dice/curios/**` | `data/curios/tags/items/*.json` | 同基准 | `data/curios/tags/items/*`（占位）+ `data/trinkets/**` + `data/astral_dice/accessories/**` | 契约不同（§2.1①）；改槽位定义时四线不同文件 |
| 战利品注入目录 | `loot_modifiers/` 14 个 | 13 个 | 14 个 | **无该目录** | forge 少 1 个与 n121 的差**未逐条对齐**（无法判定：差的是哪项未定位）⇒ 列入 §6 |
| 村民交易数据 | 无（事件注册） | 无 | `data/astral_dice/villager_trade/**` + `tags/villager_trade/**` | 无 | n26 结构换代；交易内容改动必须改数据 |
| 物品模型声明 | `models/item/<id>.json` | 同 | **另需** `assets/astral_dice/items/<id>.json` | 同基准 | n26 硬需求（R-15） |
| 生成资源根 | `src/generated/resources` | 同 | `resources` + `clientResources` | `resources` | n26 双根（R-15） |
| lang 键数（zh_cn，三语同数） | 1142 | 1142（**0 差**） | 1166（+24） | 1151（+9：7 个槽位名 + 2 个 `entity.minecraft.villager.{card_trader,chip_trader}`；**无缺失**） | n26 的 24 键含 `effect.astral_dice.railgun_ready`、`entity.astral_dice.villager.*`、`item.astral_dice.{card_shop,chip_shop,event_drawer}`、`key.category.astral_dice.main`、`msg.astral_dice.fanny_event(.1-.11)`、`msg.astral_dice.parunan_active_no_starlight`、`tooltip.astral_dice.weird_dice_cooldown`、`astral_dice.guide.entry.{hanna,nardis,sherry}_sign.3` |
| 效果图标 | 67 | 67 | 68 | 67 | 与 `ModEffects` 注册计数一致（§4.2） |

### 5.3 玩家可见性判断汇总

| 差异 | 玩家可见? | 说明 |
|---|---|---|
| chip 条件失效（R-01） | **是** | 配置项失效；是否有人用该配置属产品面 |
| 立牌解锁缺 6 个（R-02，**已关闭**） | 否（现四线均 31/31） | `c47ad8a3` 已补齐；`fd990c15` 时点曾需在配方书里手动搜索 |
| `.cache` 缺失（R-03） | 否 | 纯工程面 |
| 数据目录单复数（§5.2） | 否（对玩家） | 对数据包/整合包作者可见 |
| 饰品槽位数据契约（§2.1①） | 是（槽位数/生效规则） | 需实机核 U3/U4 |
| n26 内容差（R-05 **已关闭** / R-06 待裁决） | R-06 是 / R-05 否 | 1 个效果（`railgun_ready`）；2 张战斗牌已由 `12de194e` 补回 |
| n26 独有 items/<id>.json（R-15） | 否（若漏补则变「模型缺失」= 是） | 漏补必现紫黑方块 |

---

## 6. 未验证项（静态无法判定，必须实机）

> 供后续实机测试用例覆盖。每项给「为什么静态判不了」+「最小验证动作」。

| # | 未验证项 | 为什么静态判不了 | 最小验证动作 | 关联 |
|---|---|---|---|---|
| 1 | 饰品槽位动态尺寸（+/-）是否正确（U3） | 尺寸是存档缓存 + 运行时修饰符累积结果 | 增减触发物品后看槽位数变化，再重进世界复看 | R-14 / LT1 |
| 2 | 双后端（Trinkets / Accessories）下 MergedHandler 读写归属（U4） | 两个后端各自把手上数据合并的先后不可静态断言 | 同存档分别只装 Trinkets / 只装 Accessories 各跑一遍 | R-14 / LT1 |
| 3 | 蹲 + 右键装备的落点（U5） | 涉及原版交互优先级与槽位判定 | 蹲右键对准槽位/手持各测一次 | LT1 |
| 4 | 死亡不掉落与立牌先掉出槽的次序（U6） | 掉落与克隆复制的实际先后 | 带高价值立牌死亡→复活→对照附件值 | LT5 |
| 5 | 激光/光束 FX 观感（U1） | 纯渲染观感 | 触发光束技，录屏/截图对照 | LT6 |
| 6 | S2C 同步去重与过期（U2） | 时序与网络重排 | 高频触发同一同步、断线重连各一次 | LT1/LT7 |
| 7 | 客户端选卡关闭模式行为（U7） | 静态只证明包存在 | 打开选卡界面→ESC/关闭→核对服务端状态 | LT7 |
| 8 | 跨线协议兼容与 VersionGate（U8） | 需两线同时在线 | 跨线联机握手、错版本握手 | LT7 |
| 9 | n26 未注册 payload 的抛点（U9） | 运行期抛异常位置 | 构造未注册 payload 场景观察日志 | LT7 |
| 10 | forge 侧 13 vs 基准 14 个 GLM 的具体缺项 | 未逐条比对 JSON 名 | `diff` 两线 `loot_modifiers/*.json` 文件名集合 | §5.2 |
| 11 | fabric 附件注册是否真的早于玩家数据反序列化（B5） | 调用点未逐行定位 | 加载既有存档并读附件值；或装调试打印 | R-14 |
| 12 | forge 线「原版枚举扩展」的实际落点 | 无 `enumextensions.json`，代码面未逐行核对 | 搜 forge 线自定义 Rarity 的构造点 | R-16 |
| 13 | Jade 冷却 provider 缺失的实际表现（R-08） | 退化程度取决于 Jade provider 的兜底 | forge/fabric 各看一次商店冷却显示 | R-08 |
| 14 | n26 `railgun_ready` 与另三线筹码机制的等价性（R-06） | 注册项在、语义未核 | n26 触发筹码就绪效果并与基准线对照提示 | R-06 |
| 15 | 工作树 0 D / 4 M / 279 ??（时点快照 HEAD `33fe2f5e`）的归属与去向 | 「是否 lead 正在进行的 datagen 清理」无法从代码判定；本次实测已无删除项（0 D），4 个修改项为 `.github/workflows/build.yml` 与 3 处 `docs/**`（`docs/agent/AGENTS-dsh-linux.md`、`docs/agent/AGENTS-full.md`、`docs/archive/card-recipes-2026-10-09/README.md`），本文档未触碰任何此类改动 | 由 lead 确认这 4 处修改（含 `.github/workflows/build.yml`）与 279 条未跟踪项的意图；数据面已随 `c47ad8a3` 落盘，§5.1② 因此关闭 | §1.5 / §5.1② |
| 16 | 26.1.2 上 `mru` / `sounds` 与 astral_dice 能否共存（本轮实机验收把它们排除在外） | 测试台 NeoForge 为 26.1.2.109，而 `mru` 的 `neoforge.mods.toml` 要求 ≥ 26.1.2.114，两者无法同台；共存冲突无法静态判定 | 把 NeoForge 升到 ≥ 26.1.2.114 后，用「玩家实例 + `mru` + `sounds`」启动一次并核对加载日志；顺带确认 Waystone 联动仍打 INFO「Waystone teleport integration enabled」（R-17 已收口，不应出现 WARN/DEBUG 降级） | R-17 / §6.1 |

> 计数：**16 项**未验证；其中 9 项来自 ALIGNMENT-AUDIT 的 U1–U9，7 项为本轮 mapping 新发现（含 1 项工作树状态确认、1 项 26.1.2 实机验收的受测模组集边界）。

### 6.1 验证记录：26.1.2 实机验收的受测模组集

> 记录一次已发生的实机验收的边界条件，供后续复现与判读日志；本节不新增未验证项。

- **受测集** = 本轮 **106 个模组**（含 `waystones-neoforge-26.1.2-26.1.2.16`、`rhino`、`better-advanced-tooltips`）− `mru` − `sounds`；上一批记录的「玩家实例 95 件」为早期口径，已被本轮实读取代。
- **排除原因**：测试台的 NeoForge 为 **26.1.2.109**，`mru` 的 `neoforge.mods.toml` 要求 **≥ 26.1.2.114**，两者无法同台；`sounds` 与 `mru` 同源，一并排除。
- **对结论的影响**：`astral_dice` 本体未受影响；但这两件模组与本模组在 26.1.2 的共存性因此仍未验证（§6 第 16 项）。
- **本轮结果**：用例 `RELEASE150-26.1.2` = **PASS**（25 条机器断言 + 2 条 vision 断言），构建 2,879,891 B / sha256 `0a069d24b94fe33df891c9fdc3a2b978fd9c3b1c23e0b2bf41e6f9fd75a80fcd`，运行平台 NeoForge **26.1.2.109**。
- **Waystone 联动证据**：该轮 `run/26.1.2/prodgame/logs/latest.log` 第 **525** 行为 `[0211月2026 05:42:39.922] [modloading-worker-0/INFO] [com.merlinkitsune.astral_dice.event.WaystoneWarpCompat/]: [Astral Dice] Waystone teleport integration enabled for 跃迁引擎.`；全轮日志无 `Failed to register Waystone`、无 `not registrable` ⇒ 第 16 项里的「顺带确认」本轮已给出基线证据，后续 `mru`/`sounds` 共存轮仍应复核一次。

---

## 附录 A：本文件引用到的关键文件清单

**入口与注册**：`neoforge-1.21.1/.../AstralDiceMod.java`、`forge-1.20.1/.../AstralDiceMod.java`、`neoforge-26.1.2/.../AstralDiceMod.java`、`fabric-1.20.1/.../AstralDiceMod.java`、四线 `item/ModItems.java`、四线 `init/ModCreativeTabs.java`。

**事件与桥**：`fabric-1.20.1/.../platform/event/LoaderBus.java`、`platform/FabricBridges.java`、`platform/PuzzlesBridges.java`、`mixin/bridge/LivingHurtBridgeMixin.java`、`platform/event/SubscriptionAudit.java`；四线 `combat/DiceCombatEvents.java`。

**网络**：`neoforge-1.21.1/.../network/ModPayloads.java`、`neoforge-26.1.2/.../network/ModPayloads.java`、`forge-1.20.1/.../network/ModNetwork.java`、`fabric-1.20.1/.../network/ModNetwork.java`、`fabric-1.20.1/.../platform/network/SimpleChannel.java`。

**持久化/配置**：四线 `component/`（`ModAttachments.java`、forge 侧 `AstralData.java` / `AttachedDataKey.java` / `ModCapabilities.java` / `ModDataComponents.java`、fabric 侧 `AttachedDataKey.java` / `SignAttachments.java` / `DeathPreservedBonuses.java`）、四线 `config/ModCommonConfig.java`、`fabric-1.20.1/.../platform/config/ForgeConfigSpec.java`。

**数据生成**：`neoforge-1.21.1/.../datagen/{DataGenerators, ClientDataGenerators}.java`、`neoforge-26.1.2/.../datagen/ModVillagerTradeProvider.java`、`fabric-1.20.1/.../datagen/AstralDataGenerator.java`、`fabric-1.20.1/build.gradle`。

**资源与 mixin 配置**：四线 `src/main/resources/astral_dice*.mixins.json`、`META-INF/enumextensions.json`（仅 neo 两线）、`src/generated/**`。

**旧报告（历史，冲突处见 §4.3）**：`porting/CROSS-VERSION-DIFFS.md`、`porting/fabric-1.20.1/{MAPPING-1.5.0, ALIGNMENT-AUDIT-2026-11-01, API_CHEATSHEET, B3-SIGNHOOK-DEADCODE-REPORT, LIVE-TEST-LIST-2026-11-02}.md`、`porting/neo-26.1.2/P1-DEFECT-REPAIR-CONTEXT.md`、`docs/compat-*.md`。

---

_本文件为只读取证的产物：未构建、未运行、未修改任何源码。所有「运行时行为」结论均在 §6 标注为待验证。_
