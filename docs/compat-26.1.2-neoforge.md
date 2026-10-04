# MC 26.1.2 / NeoForge 26.1.2.109 迁移文档（第三条线）

> 适用子项目：`neoforge-26.1.2`（**2026-09-17 起已并入主线**：原本的独立 worktree `F:\MCProject\astral_dice_multiloader-26.1.2` / 分支 `multi-26.1.2-neoforge` 已合并进 `multi-1.20.1-1.21.1`，现行工作目录即主线根目录，第三子项目与之同树）
> 迁移基线：主线 `multi-1.20.1-1.21.1` 的 `1.2.1`（commit `fda8ca9`）
> 本文与 `compat-1.20.1-forge.md` 同级；`docs/` 在 `.gitignore` 内，属**本机文档**，不入库。

---

## 1. 版本矩阵与前置信息（迁移前获取的必需版本信息）

| 项 | 值 | 说明 |
|---|---|---|
| Minecraft | **26.1.2** | Mojang 2026 起改用日期版本号（26.1 = 2026-03-24 / 26.1.2 = 2026-04-09）；本条线锁 26.1.2 |
| NeoForge | **26.1.2.109** | `neo_version=26.1.2.109`；`neo_version_range=[26.1.0.0,26.2)` |
| Java | **25** | toolchain 25；本机 JDK 在 `C:\Program Files\Zulu\zulu-25`（`JAVA_HOME` 仍是 zulu-21，由 Gradle toolchain 解析） |
| ModDevGradle | **2.0.147** | 版本类型：`client` / `clientData` / `serverData` / `gameTestServer` / `server` / `junit`（**没有 `data`**） |
| NeoForm | **26.1.2-1** | |
| FML loader | **11.0.15** | `@EventBusSubscriber` 只剩 `value()`/`modid()` |
| Event bus | **8.0.5** | 抽象事件不可注册监听器 |
| AccessTransformer | 11.0.2 / ASM 9.9.1 | |
| Mixin | **`net.fabricmc:sponge-mixin:0.17.3+mixin.0.8.7`** | 运行时自带；**Fabric 分支**，不再是 `org.spongepowered:mixin:0.8.5`；`CompatibilityLevel.JAVA_25` 可用（实测日志：`Compatibility level set to JAVA_25`） |
| Parchment | **无 26.1.x/26.2 数据（404）** | 故 26.1.2 的 `build.gradle` **删除** parchment 配置 |
| 客户端主类 | `net.neoforged.fml.startup.Client` | dev 运行命令行里不再出现 `net.minecraft.client.main.Main`（工具链识别已同步，见 §7） |
| datagen 主类 | `net.neoforged.fml.startup.DataClient` / `DataServer` | 见 §5 |

### 依赖可用性（Modrinth / maven 实测）

| 依赖 | 26.1.2 可用版本 | 备注 |
|---|---|---|
| Curios | `15.0.0+26.1.2`（Modrinth id `dRnRThvD`） | 声明 `neoforge [26.1.0.0,)`；`curios_version_range=[15,)` |
| Patchouli | `26.1-94-beta`（Modrinth id `2CsnFLom`） | |
| JEI | `jei-26.1.2-neoforge:29.37.0.99` | 开发期 `maven.blamejared.com` |
| KubeJS | `26.1.2-8.0.6+neoforge`（maven.latvian.dev） | KubeJS 8.x API；探针脚本需另迁移（见 §8） |
| Iron's Spells 'n Spellbooks | **无 26.1.x 构建（最高 1.21.1）** | 平台不存在该模组 ⇒ **联动整体移除**（2026-10-04）：删 `event/IronSpellbooksCompat`、`SpellDamageRegistry` 的 14 个 Iron 键、`ModTooltipHandler` 的 tooltip 分支、三语言 2 键、手册 `effect_card_fate_guidance` 第 6 页 |
| 基础模组库（`starenginelib` 等） | 不适用于 26.1.2 | 已从依赖中剔除 |

---

## 2. 编译期 API 变更映射（均已通过 `compileJava` 全量验证）

> 全部由 **26.1.2 目标版本源码/javap** 核对后改写，未凭记忆。

### 2.1 类/包重命名

| 1.21.1 | 26.1.2 |
|---|---|
| `ResourceLocation` / `net.minecraft.resources.ResourceLocation` | **`Identifier`**（`Identifier.fromNamespaceAndPath` / `.parse` / `.identifier()`） |
| `ResourceKey#location()` | `ResourceKey#identifier()` |
| 实体类散落在 `world.entity.*` | 移入类型子包（如 `world.entity.monster.piglin.PiglinAi`） |
| `ClickType` | **`ContainerInput`** |
| `BlockEvent.BreakEvent` | `net.neoforged.neoforge.event.level.block.BreakBlockEvent` |
| `FMLEnvironment.dist`（字段） | `FMLEnvironment.getDist()` |
| `net.neoforged.neoforge.network.PacketDistributor.sendToServer` | **`ClientPacketDistributor.sendToServer`**（服务端→客户端仍用 `PacketDistributor.sendToPlayer/sendToPlayersTrackingEntity`） |
| `HolderGetter#getHolderOrThrow` | `getOrThrow` |
| `HolderLookup.Provider#registryOrThrow` | `lookupOrThrow` |
| `RecipeSerializer`（接口实现） | **record**（`new RecipeSerializer<>(CODEC, STREAM_CODEC)`） |
| `ItemModelProvider`（NeoForge 生成器） | **已删除**；改用原版 `net.minecraft.client.data.models.ModelProvider` + `ItemModelGenerators`（1.21.4+ 的 `assets/<ns>/items/*.json`） |
| `ExistingFileHelper` | **已删除**（原版 ModelProvider 不再需要；`GatherDataEvent#getExistingFileHelper` 亦无） |
| GUI 渲染 | 提取-渲染状态架构：`GuiGraphicsExtractor`、`GuiLayer`、`Matrix3x2fStack`；屏幕 `extractBackground/extractLabels/extractRenderState` |
| `AbstractContainerScreen` | 5 参构造 + `slotClicked(Slot,int,int,ContainerInput)`；`item/itemDecorations`；`blit(RenderPipelines.GUI_TEXTURED, …)` |

### 2.2 方法/字段访问变更

| 旧写法 | 新写法 |
|---|---|
| `Level#isClientSide` / `Level#random`（字段） | `isClientSide()` / `getRandom()` |
| `Inventory#items` | `getNonEquipmentItems()` |
| `Player#displayClientMessage(Component, boolean)` | `sendOverlayMessage(c)` / `sendSystemMessage(c)` |
| `Entity#getServer()` | `level().getServer()` |
| `Entity#moveTo(…)` | `snapTo(…)` |
| `EntityType#is(TagKey)` | `builtInRegistryHolder().is(…)` |
| `EntityType.create(Level)` | `create(Level, EntitySpawnReason)` |
| `Item#getFoodProperties` | `stack.get(DataComponents.FOOD)` |
| `Registry#getHolder` | `get` |
| `OwnableEntity#getOwnerUUID` | `getOwner()` |
| `VillagerData#getLevel()` | `level()` |
| `GameProfile#getName()` | `name()` |
| `AnvilUpdateEvent#setCost/getCost` | `setXpCost/getXpCost` |
| `ItemStack.STRICT_CODEC` | `ItemStack.CODEC` |
| `ItemModBook.forBook(...)` 返回 `ItemStack` | 返回 **`ItemStackTemplate`** → `.create()` |
| 药水效果常量 | `DAMAGE_BOOST→STRENGTH`、`MOVEMENT_SPEED→SPEED`、`MOVEMENT_SLOWDOWN→SLOWNESS`、`DIG_SLOWDOWN→MINING_FATIGUE`、`CONFUSION→NAUSEA`、`DAMAGE_RESISTANCE→RESISTANCE`、`HARM→INSTANT_DAMAGE`、`HEAL→INSTANT_HEALTH` |
| 配方 builder | `shaped/shapeless(HolderGetter<Item>, RecipeCategory, ItemLike)`；`save(RecipeOutput, ResourceKey<Recipe<?>>)` |
| 自定义有序配方 | `super(new Recipe.CommonInfo(showNotification), new CraftingRecipe.CraftingBookInfo(category, group), pattern, ItemStackTemplate.fromNonEmptyStack(result))`；`assemble(CraftingInput)`；`getSerializer()` 返回 `RecipeSerializer<ShapedRecipe>`（见源码注释的强转说明） |
| `Camera` 视图矩阵 | 原版新增 `Camera#getViewRotationProjectionMatrix(Matrix4f)`、`camera.position()` ⇒ **不要**再照抄 1.20.1/1.21.1 的手算公式（见 §6.3） |
| 权限判定 | `source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)` |
| 按键分类 | `KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MODID,"main"))`（lang key 改为 `key.category.astral_dice.main`，两语言文件均已同步，622/622 校验通过） |
| 附件 | `AttachmentType.Builder#serialize` 只接受 `MapCodec` 或 `IAttachmentSerializer` ⇒ 全部 65 处改为 `.serialize(<codec>.fieldOf("value"))` |
| 物品注册 | `DeferredRegister.Items#register(String, Supplier)` **不再注入 id** ⇒ 必须 `registerItem(name, props -> new XxxItem(props…))`（见 §4.2） |

### 2.3 数据包目录（不变）

`recipe/` `advancement/` `loot_table/` `tags/` 在 26.1.2 **未改名**（保持 1.21.1 口径）。
物品模型新增 `assets/<ns>/items/*.json`（1.21.4+ 的物品模型定义）——26.1.2 **必需**，缺失则物品无模型。

---

## 3. Mixin 目标漂移（字符串选择器不会被编译器发现，逐条对本版本源码核对）

| Mixin | 变更 | 目标版本依据 |
|---|---|---|
| `mixin/PiglinAiMixin` | `isWearingGold` → **`isWearingSafeArmor`** | `PiglinAi.java:653` `public static boolean isWearingSafeArmor(LivingEntity)` |
| `mixin/client/GuiMixin` | `renderEffects` → **`extractEffects`** | `Gui.java:521`（层注册在 `:242`） |
| `mixin/client/EffectsInInventoryMixin` → **`EffectsInInventoryMixin`**（`git mv`，类名与目标一并改） | `@Mixin(EffectRenderingInventoryScreen)` → **`@Mixin(EffectsInInventory)`**，注入 `getEffectName` | `EffectsInInventory` 为 26.1.2 的效果牌陈列屏 |
| `mixin/trade/MerchantOfferMixin` | **无需改动** | 26.1.2 `MerchantOffer` 仍有 **私有复制构造** `private MerchantOffer(MerchantOffer)`（`MerchantOffer.java:81`），`copy()` 在 `:215` 调它；`getItemCostA/B`、`getCostA/B`、`satisfiedBy` 全部存在，字段 `baseCostA`/`costB`/`demand`/`priceMultiplier`/`specialPriceDiff` 名未变 |
| `mixin/trade/ServerPlayerMixin` | 无需改动 | 实测注入成功（日志：`astralDice$beginSwap`/`$endSwap` 已应用） |
| `mixin/client/AstralUseItemGuardMixin` | 无需改动 | 实测注入成功 |
| 混合配置 | `compatibilityLevel` → `JAVA_25`；mixin 列表 8 项 | 实测 `Compatibility level set to JAVA_25` |

### 3.1 已删除：NeoForge「伤害容器泄漏」修复 mixin

1.21.1 侧曾用 `mixin/fixes/LivingEntityDamageContainerMixin` + 独立配置 `astral_dice.neoforge_fixes.mixins.json` 修 NeoForge 的伤害容器泄漏。**26.1.2 上游已修**：`LivingEntity.java:1205-1206` 的取消分支已含 `pop()`。故 26.1.2 线删除该 mixin、其配置、`fixes/` 包（两个 helper）以及 `neoforge.mods.toml` 的第二个 `[[mixins]]` 段（原位保留说明注释）。

---

## 4. 加载期/运行期关键差异（编译不报错、启动才炸的类型）

### 4.1 `@EventBusSubscriber` 与 `GatherDataEvent`

- `@EventBusSubscriber` 只剩 `value()`（`Dist[]`）与 `modid()`；`bus = Bus.MOD` 已删除（mod bus 与 game bus 统一）。
- **`GatherDataEvent` 在 26.1.2 变成 `abstract`**，并向 `Client` / `Server` 两个具体子类拆分。
  在抽象类上注册监听器会**直接让模组加载失败**：
  `IllegalArgumentException: Cannot register listeners for abstract class GatherDataEvent. Register a listener to one of its subclasses instead!`
  实测堆栈：`AutomaticEventSubscriber → FMLModContainer#constructMod` → `ModLoadingException: Astral Dice (astral_dice) has failed to load correctly`。
- 对应实现（本仓库最终形态）：
  - `datagen/ClientDataGenerators`：`@EventBusSubscriber(value = Dist.CLIENT, modid = …)` + `gatherClientData(GatherDataEvent.Client)` → `ModItemModelProvider`
  - `datagen/ServerDataGenerators`：`@EventBusSubscriber(modid = …)` + `gatherServerData(GatherDataEvent.Server)` → `ModRecipeProvider.Runner`
  - 分两个类（而不是一个类两个方法）是**必须**的：客户端 provider 继承的是 `net.minecraft.client.data.models.ModelProvider`，而 `runServerData` 的 classpath **不含客户端类**，把两者写在同一个类里会在服务端数据生成时因解析不到客户端父类而失败；`value = Dist.CLIENT` 同时保证该类不在服务端数据生成时注册。

### 4.2 物品 id 必须写入 `Item.Properties`（26.1.2 硬性）

- `Item` 构造器现在会调用 `Item.Properties#itemIdOrThrow`（`Item.java:681`）推导默认描述 id，**没有 id 直接 NPE**：
  `java.lang.NullPointerException: Item id not set`
- `DeferredRegister.Items#register(String, Supplier)`（`DeferredRegister.java:555`）**不做** `setId`；只有 `registerItem(String, Function<Item.Properties,? extends I>[, …])` 与 `registerSimpleItem` 会注入 id（`:679-680`）。
- 本仓库改法：`ModItems.registerItem(String name, Function<Item.Properties, ? extends T> factory)` → `ITEMS.registerItem(name, factory)`；123 个注册点统一改写为
  `registerItem("x", props -> new XxxItem(props.stacksTo(1).rarity(…), <extra args…>))`。
  - ⚠️ **不要**用 `XxxItem::new` 方法引用形式：构造器带额外参数时，`p -> …` 链会被逗号截断（本次踩过：4 个参数导致 `无法推断类型变量 T`）。
  - ⚠️ 属性链必须挂在**传入的 `props`** 上，不得再 `new Item.Properties()`。

### 4.3 原版 `ModelProvider` 拒绝重复登记

1.21.1 的 NeoForge `ItemModelProvider` 对重复 `basicItem(...)` 静默覆盖；26.1.2 的原版 `ModelProvider` 会抛
`IllegalStateException: Duplicate model definition for astral_dice:item/star_coin`。
**来源**：1.21.1 的 `ModItemModelProvider` 里 `STAR_COIN` 被登记了两次（1.21.1 侧至今仍有该重复，无副作用）。26.1.2 线只保留一处（附注释说明差异）。

---

## 5. 数据生成：两段式 + HashCache 互删陷阱（本次最重要的发现）

### 5.1 运行类型与事件

| 任务 | userdev 运行类型 | 主类 | 派发事件 | 产物 |
|---|---|---|---|---|
| `:neoforge-26.1.2:runClientData` | `clientData` | `net.neoforged.fml.startup.DataClient` | **仅** `GatherDataEvent.Client` | `assets/**` |
| `:neoforge-26.1.2:runServerData` | `serverData` | `net.neoforged.fml.startup.DataServer` | **仅** `GatherDataEvent.Server` | `data/**` |

- 26.1.2 **没有** `data` 运行类型：旧 `runs { data { data() } }` 会让 `prepareDataRun` 报
  `Trying to prepare unknown run: data. Available run types: [client, clientData, serverData, gameTestServer, server, junit]`。
- `--all` 只影响**原版** provider 的开关；mod 侧由事件类型决定，客户端运行**不会**生成配方。

### 5.2 HashCache 会删掉「不是本次运行产物」的一切

`DataGenerator.Cached#run()`（`net/minecraft/data/DataGenerator.java`）每次都新建
`HashCache(rootOutputFolder, allProviderIds, version)`，其 `purgeStaleAndWrite()`
（`net/minecraft/data/HashCache.java`）会**遍历整个输出根并删除所有不在本次运行 provider 清单里的文件**。

⇒ 若两次运行共用 `src/generated/resources`，**后跑的那次必然清空前一次的全部产物**。实测（同一输出根）：
```
runServerData → Caching: total files: 463, old count: 216, new count: 216, removed stale: 247, written: 216
runClientData → Caching: total files: 463, old count:   0, new count: 246, removed stale: 217, written: 246
```
最终只剩后者（246 个物品模型），`data/` 被清空；反向运行则反过来。

### 5.3 本仓库采用的双输出根方案（已验证稳定）

```
src/generated/resources/        ← runServerData（data/…：配方 108 + 进度 108）
src/generated/clientResources/   ← runClientData（assets/…：items 123 + models/item 123）
```
两侧都作为主资源集目录：`sourceSets.main.resources { srcDir('src/generated/resources'); srcDir('src/generated/clientResources') }`。
**验证**：两个任务同批次运行（server→client 顺序）后
`removed stale: 0 / 0`，两棵树完整共存，jar 内 1233 条目同时含 `assets/astral_dice/items/*`（123）与 `data/astral_dice/recipe/*`（108）。
**推论（写进工程约定）**：
- 重新生成资源必须**两个任务都跑**（`gradlew :neoforge-26.1.2:runClientData :neoforge-26.1.2:runServerData`）。
- **不要**把两个运行指向同一个 `--output`（哪怕临时试验，也会静默删掉另一棵树）。
- **不要**在 `.cache` 目录上做手工干预；两个根各有自己的 `.cache`（均已在 `build.gradle` 与 `.gitignore` 中排除）。

### 5.4 生成结果与 1.21.1 基线的一致性

| 产物 | 1.21.1 | 26.1.2 | 结论 |
|---|---|---|---|
| `models/item/*.json` | 123 | 123 | 文件名集合**零差异** |
| `items/*.json`（1.21.4+ 定义） | —（该版本不需要） | 123 | 与 model 名逐一对应 |
| `data/…/recipe/*.json` | 108 | 108 | 数量一致（JSON 形状按 26.1.2 原版新格式） |
| `data/…/advancement/**` | 108 | 108 | 一致 |

---

## 6. 客户端渲染相关（人工核对，非编译器可验）

### 6.1 提取-渲染状态架构
`ModClientEvents` 的覆盖层改注册为 `GuiLayer`；伤害数字的世界→屏幕投影改为
`camera.getViewRotationProjectionMatrix(new Matrix4f())` + `camera.position()` + `poseStack.translate`（float 实参），并移除 `Quaternionf` 依赖。

### 6.2 玩家渲染事件
`NancyLuClientEvents` 改用 `RenderPlayerEvent.Pre<?>` 与 `event.getRenderState().id`（`AvatarRenderState#id` = `entity.getId()`，见 `AvatarRenderer.java:198`），`LocalPlayer` 类型同步。

### 6.3 禁止跨版本抄投影公式（沿用主线既有裁决）
26.1.2 原版已提供 `Camera#getViewRotationProjectionMatrix`，**不得**把 1.20.1 的 `Axis.XP/YP` 或 1.21.1 的 `Quaternionf` 写法搬过来。

### 6.4 `GuiGraphicsExtractor` 的 `blit` / `text` 语义陷阱（2026-09-22 实机取证；钱包控件显示异常的真因）

26.1.2 用 `GuiGraphicsExtractor` 取代 `GuiGraphics` 后，**同名方法的参数语义变了，编译期完全看不出来**：

| 调用 | 四个 int | 四个 float | 结论 |
|---|---|---|---|
| `blit(Identifier, int, int, int, int, float, float, float, float)` | **绝对终点坐标 `(x0, y0, x1, y1)`** | **已归一化的 `(u0, u1, v0, v1)`**（0..1） | ❌ 按 1.21.1 的 `(x, y, w, h, u, v, texW, texH)` 去调它：矩形退化成 `x0..w` × `y0..h` 的细条、UV 退化成单点采样 ⇒ 实机上只剩一条 56×2 像素的纯色细条（余额条实际参数 `(152,26,96,24)`） |
| `blit(RenderPipeline, Identifier, int, int, float, float, int, int, int, int)` | `(x, y, w, h)` | 后两 int = `(texW, texH)`，UV 由内层除以 texW/texH 归一化 | ✅ 正确用法（与本仓 `screen/CardInventoryScreen` 一致），**26.1.2 绘制一律用它** |
| `text(Font, String/Component, int x, int y, int color, boolean)` | — | — | ⚠️ **color 必须是 ARGB**：方法体第一步 `if (ARGB.alpha(color) == 0) return;` ⇒ 传 `0xFFFFFF` **一个字都不画且不报错**（1.21.1 的 `drawString` 有「alpha = 0 视为不透明」旧口径，本版本已移除） |

**判据（字节码级，可复核）**：`blit(Identifier,…)` 转调 `innerBlit(pipeline, tex, a, c, b, d, e, f, g, h, -1)`，
终点落在 `BlitRenderState`（字段名就是 `x0,y0,x1,y1,u0,u1,v0,v1,color`）；
带管线的重载体内先做 `x + w` / `y + h` 与 `(u + …)/texW`、`(v + …)/texH`。

**本轮修掉的真实缺陷**（`client/gui/StarCoinWalletButtons`，26.1.2 专属）：
1. 两处 `gfx.blit(...)` 按 1.21.1 语义调用 ⇒ 余额条与三个按钮全部退化为细条 + 单点采样；
   实机表现（Sodium+Iris+光影，与纯原版一致）＝物品栏上方一条黑条、控件不可见（用户报「无法点击」）。
   已改为带管线重载（`RenderPipelines.GUI_TEXTURED, tex, x, y, 0F, 0F, w, h, w, h`）。
2. `BALANCE_TEXT_COLOR = 0xFFFFFF` ⇒ 余额数字恒不画；已改为 `0xFFFFFFFF`。

**同类待查（尚未修）**：`client/TargetSelectionClient` 的 `COLOR_FRIENDLY/HOSTILE/NEUTRAL`
（`0x55FF55` / `0xFF5555` / `0xFFFF55`）都是**无 alpha** 的 RGB，被 `client/TargetSelectOverlay`
直接喂给 `text(...)` ⇒ 该覆盖层的文字在 26.1.2 上同样不可见；
`client/TargetSelectionHighlighter` 只取 RGB 分量描棱柱，不受影响。

### 6.5 叠加式 GUI 控件的两个陷阱 + 一处迁移遗漏（2026-09-22 实机取证；用户报「切创造模式后钱包栏消失」「存款数不实时」）

**A. `isInventoryOpen()` 不是「界面是创造栏」，而是「当前选中的标签页是『物品栏』」**
（字节码：`selectedTab.getType() == CreativeModeTab.Type.INVENTORY`）。而 `CreativeModeInventoryScreen.init()`
每次都会把 `selectedTab` 重置为 `CreativeModeTabs.getDefaultTab()` = **「建筑方块」**（`Type.CATEGORY`）。
⇒ 把叠加控件的可见性绑在 `isInventoryOpen()` 上，**一进创造栏就是隐藏**；又因为原版
`InventoryScreen.containerTick()/init()` 在 `hasInfiniteMaterials()` 时**换屏**到创造栏（切模式即触发），
实机观感就是「切创造模式后钱包栏整条消失」。
本仓裁决：余额条与钱包按钮位于面板**上方界外**，任何标签页都不遮挡原版元素 ⇒ **常显**；
只有两个兑换按钮（落在面板内右侧，会压住物品格子并抢走点击）继续受「物品栏」标签页限制。

**B. 跨版本通用坑：绝不要用 `AbstractWidget#visible` 表达「本帧隐藏」** —— `AbstractWidget#render` 只在
`visible` 为真时才调用 `renderWidget`，所以在 `renderWidget` 里写 `this.visible = false` 之后，
**该方法再也不会被调用**，「隐藏」就变成**永久**（把标签页切回来也不恢复）。
表达「隐藏」要用 `active`：它只参与鼠标命中（`mouseClicked` 检查 `active && visible`），不拦截渲染回调。
用户「只有重新打开界面才会显示」正是这个自锁 —— 重开界面时 `ScreenEvent.Init.Post` 会重新挂控件。

**C. 迁移遗漏的另一类：「半移植」不只在方法体** —— `StarCoinBalanceSync.tick` 没有进 26.1.2 的
`PlayerTickEvents`：1.21.1 / 1.20.1 每玩家 tick 都有 `StarCoinBalanceSync.tick(player)`
（1 秒节流 + 值变化才发包），本线只剩 `PlayerLifecycleHandler` 的 `forceResend`（登录 / 重生一次）
⇒ **余额条进世界后再不刷新**。已补回；同时三线新增 `StarCoinBalanceSync.notifyChanged(player)`：
**本模组自己**的余额写入点（存入 / 取出 / 拾取吸收 / 发币漏斗）执行完**立即**回推，
1 秒轮询退回为「兜底」（覆盖第三方模组、库的 `/starcoin` 命令、直接写账本）。
判据：`grep -c "StarCoinBalanceSync.tick" */src/main/java/.../event/PlayerTickEvents.java` 三线均应为 1。

---

## 7. 本仓库工具链（`scripts/test`）的 26.1.2 适配

| 位置 | 内容 |
|---|---|
| `lib/Mt.Paths.psm1` | `$VERSIONS` 增加 `'26.1.2'`；LOADER/SUBPROJECT 映射；`DEFAULT_PACK_MODS['26.1.2'] = D:\.minecraft\versions\26.1.2-NeoForge_26.1.2.109\mods`；配置键 `MT_PACK_MODS_NEOFORGE_26_1_2` |
| `mt_preflight.ps1` | 分支白名单 `@('multi-1.20.1-1.21.1','multi-26.1.2-neoforge')` |
| `mt.conf(.example)` | 新增 `MT_PACK_MODS_NEOFORGE_26_1_2` |
| `lib/Mt.Proc.psm1` | 客户端入口识别新增 **`net.neoforged.fml.startup.Client`**（26.1.2 的客户端主类；用全限定名，`…startup.DataClient` 不会误命中） |
| `mt_launch.ps1` | ①「全机已存在客户端」防撞预检同步新增该入口（漏检会导致跨版本撞车、读到他人 `latest.log`）；② **就绪判据按版本分支**——26.1.2 已无 `Total time to load game and open world was`，改用 `logged in with entity id` **且** `Loaded <N> advancements`（都取英文原版行，避免本地化文案）；③ 兼容性信号在 26.1.2 改报 `KUBEJS_LOADED` / `RHINO_LOADED`（本版本 dev run 不装渲染模组） |
| `mt_env.ps1` | 新增 **26.1.2 探针运行时装装**（`Install-MtProbeRuntime`）：从 KubeJS 官方 maven 拉 `kubejs-neoforge-<gradle.properties:kubejs_version>` + `rhino` + `better-advanced-tooltips` 到 `run/26.1.2/mods`，缓存在 `temp/probe_mods/26.1.2/`；dev run **不**装渲染模组 |
| `mt_case.ps1` | 条目 `version` 白名单改为取 `Get-MtVersions`（原先硬编码 `@('1.21.1','1.20.1')`，26.1.2 条目会被判「version 非法」） |
| `mt_inject.ps1` | 未找到窗口时的报错文案同步两种入口 |
| `resources/kubejs/26.1.2/server_scripts/astral_probe.js` | **26.1.2 探针**（迁移期最小集）：`diag` / `itemcount` / `opprobe` / `dumpstate` / `give` / `equipslot` / `readstate` |
| `cases/MIGRATION-SMOKE-26.1.2.json` | 迁移冒烟条目（24 条断言，见 §9.1） |
| `.github/workflows/build.yml` | 分支触发 `multi-26.1.2-neoforge`、JDK 25、第三条 lang 校验、第三个 jar 上传；tag/Release 仍只走发布分支 |
| `deploy.ps1`（本机、gitignore） | `'26.1.2' = 'neoforge-26.1.2'` |

### 7.1 KubeJS 运行时（26.1.2）——两个实测坑

1. **`better-advanced-tooltips` 是硬前置，即使只跑服务端**：KubeJS 26.1.2-8.0.6 的
   `TextIcons.<clinit>` 无条件引用 `dev.latvian.mods.betteradvancedtooltips.BATIcons`
   （mods.toml 里它却标成 `optional/CLIENT`）⇒ 缺它时**世界生成用的 runServer 直接崩**：
   `NoClassDefFoundError: dev/latvian/mods/betteradvancedtooltips/BATIcons`（RegisterEvent 阶段）。
2. **`tiny-java-server` 不能放进 `run/mods`**：它是纯 Java 库（无 `mods.toml`），FML 会弹
   「不是一个有效的模组文件」警告屏并**停在那里**（不进主菜单、quickplay 不生效）。本工具链刻意不装它
   （只服务 KubeJS 自带 HTTP 面板）。

### 7.2 探针侧的 KubeJS 8 / 26.1.2 API 形态（写 26.1.2 探针必须照做）

| 事项 | 结论 |
|---|---|
| 命令注册 | `ServerEvents.commandRegistry(event => { var Commands = event.commands; event.register(Commands.literal(...)...) })` —— **与 KubeJS 7 同形**（`CommandRegistryKubeEvent#register` + `getCommands()` 仍在） |
| `Level#dimension()` | Rhino 下在 `ServerLevel` 上是**属性**：`p.level.dimension.identifier()`（写 `dimension()` 会 `Cannot call property dimension ... It is not a function`） |
| 权限读数 | `ctx.source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)`；`CommandSourceStack#hasPermission(int)` **已删除**。`MinecraftServer#getProfilePermissions(NameAndId)`（`NameAndId` 记录 = `(UUID, String)`）在 Rhino 下取不到时 `level` 退化为 `-1`（`mt_launch` 只判 `has2`） |
| `Commands#performPrefixedCommand` | 返回 **void**（1.21.1 返回 int）⇒ 只能报「已投递/异常」，不能用返回码当判据（`/astralparty dump` 的可用性判据仍是 `APDUMP\|LOCKRAW\|` 机器行） |
| Curios 15 | `CuriosApi.getCuriosInventory(LivingEntity)` → `Optional<ICuriosItemHandler>`；`getStacksHandler(slot)` → `Optional<ICurioStacksHandler>`；`getStacks().setStackInSlot(i, stack)`；**`ICurioStacksHandler` 无 `grow`** |
| ⚠️ **禁止用 `findFirstCurio(...)`** | Curios 15 多了 `findFirstCurio(Item)` 重载 ⇒ KubeJS 8 的 `ItemWrapper` 会按该重载去 wrap JS 函数并抛 `KubeRuntimeException: Failed to read item stack from Unknown: …`，**且该异常不被 JS `try/catch`（含探针的 `guard`）捕获** ⇒ 读数全无、连 `AP_*_EX` 都没有，只在 latest.log 留一行 Brigadier `threw an exception`。改法：直接遍历 `getStacks().getStackInSlot(i)` + `getItem()` |
| 注册表物品 | `BuiltInRegistries.ITEM.get(Identifier)` 返回 `Optional<Holder<Item>>`（1.21.1 直接返回 Item） |


---

## 8. 尚未完成 / 已知缺口
- ✅ **库侧缺口已解决（2026-10-04 结案；原文见文末「沿革」）**：库提交 `b90a16e`（2026-09-22，随库 `1.0.0`）已为
  `neoforge-26.1.2` 补齐平台侧经济存储 `economy/NeoForgeEconomyStorage`，并在库入口 `StarEngineLib` 构造器调用 `install()`。
  **字节码实证**（`javap -c -p`，库 `1.0.11` jar）：① `StarEngineLib.<init>` = `invokestatic
  com/merlinkitsune/starenginelib/economy/NeoForgeEconomyStorage.install:()V`；② `NeoForgeEconomyStorage.isAvailable()`
  = `iconst_1; ireturn`（恒 `true`）；③ 事件接线在位（`RegisterCommandsEvent#getDispatcher` ⇒ `/starcoin`；
  `PlayerEvent$Clone#getOriginal` ⇒ 死亡不掉落）。
  **产物面**：`1.0.11` 四平台 jar 均含该类；消费方 26.1.2 产物内嵌 `starengine_lib-neoforge-26.1.2-1.0.11.jar`
  （9 个 economy 类、入口含 `install()` 调用）；`run/26.1.2/mods` 与整合包 `mods/` 均**无**独立库 jar（无遮蔽）。
  ⇒ **该缺口在代码/产物层已不存在**。运行期读数由用例 `scripts/test/cases/ECON-26.1.2.json`
  （探针 `/astralprobe econ <tag>`）承担 —— **实机已 PASS（2026-10-04）**：12/12 断言通过，
  机器行 `AP_E1_ECON:available=1:before=<n>:set_ok=1:after_set=4321:restore_ok=1:after_restore=<n>`，
  无 `AP_*_ERR` / `AP_*_EX` / crash。**三层证据齐备 ⇒ 本条结案。**


1. **1.21.1 的 4088 行完整回归探针尚未迁移到 26.1.2**：现在只有迁移期最小探针
   （`resources/kubejs/26.1.2/server_scripts/astral_probe.js`，7 条子命令）+ 一条冒烟条目。
   忍者/骇客/电磁炮/法伤口径/牌桌锁 等条目需按 §7.2 已固化的 API 形态逐条补齐。
2. **`opprobe` 的 `level` 读数退化为 `-1`**（`getProfilePermissions(NameAndId)` 在 Rhino 下取不到；
   `has2` 与 `dump` 判据正常，`mt_launch` 只判后者）。属读数残缺，不影响闸门。
3. 手册（Patchouli UI）打开、立牌技能行为、GUI 屏幕（`CardInventoryScreen`）尚未做视觉/行为取证。
4. Iron's Spells 'n Spellbooks 集成在 26.1.2 线**整体移除**（2026-10-04 二次清理）——该模组无 26.1.x 构建，按「平台不存在的模组 ⇒ 联动整体移除」口径，`event/IronSpellbooksCompat` 及其伤害键 / tooltip / lang / 手册条目全部删除，无残留。
5. **二重验证的第二轮（修改后回归）**未跑：本轮完成的是「代码层改动 → 工具链游戏内冒烟」。
6. `docs/` 为本机目录，本文不会随 git 提交；如需长期保存，请连同主线 `docs/` 一起备份。

---

## 9. 本轮验证记录（可复核）

### 9.1 工具链游戏内冒烟（`cases/MIGRATION-SMOKE-26.1.2.json`，2026-09-16/17 实跑）

流程：`mt_env mods` → `mt_env world`（重建 `testworld`，allowCommands=1、keepInventory=true）→
`mt_launch`（quickplay）→ `mt_case run`。结果 **PASS（24/24 断言）**，关键读数：

| 读数 | 值 | 说明 |
|---|---|---|
| `MT_LAUNCH` | OK (48~49s) | 已进入世界；`KUBEJS_LOADED=true` / `RHINO_LOADED=true` |
| `MT_ASSERT_KUBEJS` | PASS（server.log 0 errors） | 探针脚本在 26.1.2 上无脚本错误 |
| `PREFLIGHT_OP` | `has2=1 dump=sent`；`APDUMP\|LOCKRAW\|` 14~28 行 | 权限判据（`permissions()`）与 `/astralparty dump` 机器行均可用 |
| `AP_M1_DIAG` | `mod=1:att=1:eff=1:items=123` | 模组类/附件 API/效果注册全部可见，注册表物品数 123 |
| `AP_M2_ITEMCOUNT` | `astral_dice=123:total=1630` | 与 `ModItems` 的 123 个注册点**精确一致** |
| `AP_M3_GIVE` / `AP_M4_EQUIP` | `astral_dice:dice:1` / `dice:astral_dice:dice` | 物品注册 + Curios 15 槽位装配可用 |
| `AP_M5_STATE` | `healing=0:pages=0:signcd=0:dice=1:diceSlots=1:chipSlots=0` | 附件读数与槽位规模正确（筹码槽 0 符合「须先有骰子星级」设计） |
| `Missing item model` 警告 | **0 条**（修复前 1 条：`astral_dice:astral_guide`） | 见 §9.2 |
| 视觉 | 快捷栏内 dice/卡牌/星币/立牌/筹码图标**正常渲染**（无紫黑缺失贴图） | 截图取证 |

### 9.2 冒烟过程中修掉的真实产品缺陷

**帕秋莉手册物品缺模型（26.1.2 真实回归）**：1.21.1/1.20.1 只用 `assets/astral_dice/models/item/astral_guide.json`；
26.1.2（1.21.4+）要求**物品模型定义** `assets/astral_dice/items/astral_guide.json`。
缺失时客户端只报一行 `Missing item model for location astral_dice:astral_guide`，游戏照常运行 ——
典型的「不崩但玩家可见」回归。已按 Patchouli 26.1 的形态补上（`minecraft:model` 指向本模组自己的
`item/astral_guide`，保持 1.21.1 的外观），修复后该警告归零。

### 9.3 构建与产物（复用 §9.4 的既有证据 + 本轮补充）

| 项 | 证据 |
|---|---|
| 三子项目配置 | `settings.gradle` 含 `include 'neoforge-26.1.2'` |
| 编译/构建 | `:neoforge-26.1.2:compileJava` / `:neoforge-26.1.2:build` **BUILD SUCCESSFUL**；1.21.1 / 1.20.1 回归构建亦通过 |
| 数据生成 | `runClientData` + `runServerData` 计数 123/123/108/108，`removed stale: 0 / 0` |
| 产物 | `astral_dice-1.2.1+neoforge_26.1.2.jar` 推送到根 `build/libs` + `run/26.1.2/mods` + 整合包；根 `build/libs` 三版本共存 |
| 语言闸门 | 三个子项目均 622/622 |
| 客户端启动 | 主菜单 → quickplay 进测试世界，无 ERROR/FATAL，无崩溃报告 |

### 9.4 早期（B0/B1）验证记录

| 项 | 证据 |
|---|---|
| 编译 | `:neoforge-26.1.2:compileJava` / `:neoforge-26.1.2:build` **BUILD SUCCESSFUL** |
| 数据生成 | 见 §5.4 计数；`removed stale: 0` |
| 产物 | `astral_dice-1.2.1+neoforge_26.1.2.jar`（1233 条目）三处部署 |
| 语言闸门 | 622/622（`zh_cn` ↔ `en_us`） |
| 客户端启动 | 窗口 `Minecraft NeoForge* 26.1.2`、`NeoForge 26.1.2.109（6 个模组）`；`Astral Dice mod loaded.`、版本门槛行、资源重载 `mod/astral_dice` |
| Mixin 应用 | `EntityThunderHitMixin`、`PiglinAiMixin`、`ServerPlayerMixin`、`client.AstralUseItemGuardMixin` 等注入成功 |

