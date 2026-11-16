# B0 平台注册能力取证 — fabric-1.20.1

> 目标：为 Astral Dice 的 fabric-1.20.1 线做「B0 平台注册能力取证」，产出可直接照着写代码的结论。
> 基准线：neoforge-1.21.1（主）、forge-1.20.1（次）；目标线：fabric-1.20.1。
> 本文件是**只读取证**结果：全部结论均附实读证据（文件:行号 或 javap 片段）。本轮**未**修改任何源码/资源/构建脚本，未跑 gradle，未启动游戏。
> 生成时间：2026-11-01（Asia/Hong_Kong）；工作树分支 multi-main。

## 0. 取证用的实物（jar 坐标）

| 用途 | 路径 |
|---|---|
| Fabric 线 dev 具名合并 jar（原版类签名/字节码的唯一事实源） | `C:/Users/xmace/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/1.20.1-loom.mappings.1_20_1.layered+hash.2198-v2/minecraft-merged-1.20.1-loom.mappings.1_20_1.layered+hash.2198-v2.jar` |
| Forge 线 dev 具名合并 jar（含 AT patch，用于对照 Forge 与原版的差异） | `C:/Users/xmace/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/forge-1.20.1-47.3.0-minecraft-merged/1.20.1-loom.mappings.1_20_1.layered+hash.40359-v2-forge-1.20.1-47.3.0/forge-1.20.1-47.3.0-minecraft-merged-1.20.1-loom.mappings.1_20_1.layered+hash.40359-v2-forge-1.20.1-47.3.0.jar` |
| fabric-api 0.92.12+1.20.1（bundle；模块在 META-INF/jars/ 内） | `C:/Users/xmace/.gradle/caches/modules-2/files-2.1/net.fabricmc.fabric-api/fabric-api/0.92.12+1.20.1/3e9cdd3e2f827ca9a259df9eb8e31949437b6bd4/fabric-api-0.92.12+1.20.1.jar` |

fabric-api 0.92.12 内含（读 META-INF/jars/*/fabric.mod.json 得到精确版本）：fabric-object-builder-api-v1 **11.1.5+e35120df77**、fabric-rendering-v1 **3.0.10+54550fb677**、fabric-networking-api-v1 **1.3.15+0caff8d077**、fabric-lifecycle-events-v1 **2.2.23+1802ada577**、fabric-content-registries-v0 **4.0.13+1802ada577**、fabric-transitive-access-wideners-v1 **4.3.2+1802ada577**、fabric-entity-events-v1 **1.6.1+1c78457f77**。
各模块的 sources jar 就在同名目录下（文件名 `<module>-<version>-sources.jar`），可直接 python zipfile 打印，无需解包。注意：sources 是 **intermediary 映射**（`class_XXXX`），注释与文档可读，标识符要按 class_XXXX 对照。

---

## §A 村民职业与 POI（工作方块）

### A1 neo / forge 两侧 `world/ModPoiTypes.java` 与 `world/ModVillagerProfessions.java` 依赖的加载器专属 API

**neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/world/ModPoiTypes.java（83 行）**

| 行 | 代码要点 | 加载器专属 API（具体类/方法） |
|---|---|---|
| L11-12 | `import net.neoforged.neoforge.registries.DeferredHolder; import net.neoforged.neoforge.registries.DeferredRegister;` | NeoForge 专属：`DeferredRegister` / `DeferredHolder` |
| L31-32 | `public static final DeferredRegister<PoiType> POI_TYPES = DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, AstralDiceMod.MODID);` | `DeferredRegister.create(ResourceKey<? extends Registry<T>>, String)`（NeoForge 重载，接受 ResourceKey） |
| L35-37、L45-47 | `ResourceKey<PoiType> CARD_SHOP_KEY = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, ResourceLocation.fromNamespaceAndPath(MODID, "card_shop"))` | 原版 `ResourceKey.create`；但 `ResourceLocation.fromNamespaceAndPath` 是 **1.21 才有**的工厂方法（1.20.1 必须用 `new ResourceLocation(...)`，见 porting/fabric-1.20.1/MAPPING-1.5.0.md §2.4 资源定位行） |
| L40-42、L50-52 | `DeferredHolder<PoiType, PoiType> CARD_SHOP = POI_TYPES.register("card_shop", () -> new PoiType(ImmutableSet.copyOf(ModBlocks.CARD_SHOP.get().getStateDefinition().getPossibleStates()), 1, 1));` | `DeferredHolder` = NeoForge 专属 holder 类型；`new PoiType(Set<BlockState>, int, int)` 与原版 `Block#getStateDefinition().getPossibleStates()` 都是原版 API |
| L72-74、L77-79 | `TagKey<PoiType> CARD_TRADER_JOB_SITES = TagKey.create(Registries.POINT_OF_INTEREST_TYPE, ResourceLocation.fromNamespaceAndPath(MODID, "card_trader_job_site"))` | 原版 API |
| L14-28、L57-70 | 类头文档：POI 单独注册不够 —— 村民的「可发现 / 可升职」判定走原版标签 `minecraft:acquirable_job_site`；2026-10-23 起改为「共享标签 + 职业私有标签」双轨，避免两个职业抢同一工作方块、并避免 JEI（Just Enough Professions）把两个职业画到同一个方块上 | —（设计约束，非 API） |

**neoforge-1.21.1/.../world/ModVillagerProfessions.java（58 行）**

| 行 | 代码要点 | 加载器专属 API |
|---|---|---|
| L7-8 | `import net.neoforged.neoforge.registries.DeferredHolder; import ...DeferredRegister;` | NeoForge 专属 |
| L29-30 | `public static final DeferredRegister<VillagerProfession> PROFESSIONS = DeferredRegister.create(Registries.VILLAGER_PROFESSION, AstralDiceMod.MODID);` | `DeferredRegister.create(ResourceKey, String)` |
| L32-42、L45-54 | `CARD_TRADER = PROFESSIONS.register("card_trader", () -> new VillagerProfession("card_trader", holder -> holder.is(ModPoiTypes.CARD_SHOP_KEY), holder -> holder.is(ModPoiTypes.CARD_TRADER_JOB_SITES), ImmutableSet.of(), ImmutableSet.of(), null));` | 6 参 `VillagerProfession` 构造器是**原版** API（record，见 A3 的 javap）；此处无加载器专属调用 |

**forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/world/ModPoiTypes.java（99 行）**（L30-43 自述平台差异，本轮实读确认）

| 行 | 代码要点 | 加载器专属 API |
|---|---|---|
| L11-13 | `import net.minecraftforge.registries.DeferredRegister; import net.minecraftforge.registries.ForgeRegistries; import net.minecraftforge.registries.RegistryObject;` | Forge 专属：`ForgeRegistries`（`IForgeRegistry` 句柄）、`RegistryObject` |
| L47-48 | `public static final DeferredRegister<PoiType> POI_TYPES = DeferredRegister.create(ForgeRegistries.POI_TYPES, AstralDiceMod.MODID);` | `DeferredRegister.create(IForgeRegistry<T>, String)` —— 与 neo 的 `create(ResourceKey, String)` 是**不同重载**（neo 传 `Registries.POINT_OF_INTEREST_TYPE`，forge 传 `ForgeRegistries.POI_TYPES`，两者指向同一张注册表） |
| L56-58、L66-68 | `RegistryObject<PoiType> CARD_SHOP = POI_TYPES.register("card_shop", () -> new PoiType(ImmutableSet.copyOf(ModBlocks.CARD_SHOP.get().getStateDefinition().getPossibleStates()), 1, 1));` | `RegistryObject` 取代 `DeferredHolder`（1.20.1 Forge 无 `DeferredHolder`） |
| L51-53、L61-63 | `ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, ...)` | 原版 API（1.20.1 无 `fromNamespaceAndPath`） |
| L88-95 | 与 neo 同形的两个 `TagKey` | 原版 API |
| L37-42 | 自述：Forge 1.20.1 把 `PoiTypes.TYPE_BY_STATE` 换成 `GameData.getBlockStatePointOfInterestTypeMap()`，由注册表 onAdd 回调按 `matchingStates` 自动填充 | 见 §F-4：本轮只独立复核了「AT patch 后 `registerBlockStates` 为空方法」这一半 |

**forge-1.20.1/.../world/ModVillagerProfessions.java（66 行）**

| 行 | 代码要点 | 加载器专属 API |
|---|---|---|
| L6-8 | `import net.minecraftforge.registries.DeferredRegister; import ...ForgeRegistries; import ...RegistryObject;` | Forge 专属 |
| L37-38 | `DeferredRegister<VillagerProfession> PROFESSIONS = DeferredRegister.create(ForgeRegistries.VILLAGER_PROFESSIONS, AstralDiceMod.MODID);` | **注意复数**：`ForgeRegistries.VILLAGER_PROFESSIONS`（neo 是单数 key `Registries.VILLAGER_PROFESSION`） |
| L40-50、L53-62 | `CARD_TRADER` / `CHIP_TRADER` 的构造体与 neo **逐字一致**（同 6 参 ctor、同两条 predicate） | 仅注册句柄与 holder 类型不同 |

> 结论 A1：两侧的加载器专属面只有三处 —— ①注册器入口（`DeferredRegister.create` 的重载 / `ForgeRegistries` 常量名）②holder 类型（`DeferredHolder` / `RegistryObject`）③1.21 才有的 `ResourceLocation.fromNamespaceAndPath`。**POI 与职业的本体构造、`TagKey`、`PoiType` 的 3 参构造器都是纯原版 API**，可直接复用到 Fabric。

### A2 原版 1.20.1 里 `PoiTypes.TYPE_BY_STATE` 是怎么填充的；mod 必须往哪里插

取证对象：fabric 线 dev 具名 jar（见 §0）。`javap -p` / `javap -c -p` 原文摘要：

```
$ javap -p net.minecraft.world.entity.ai.village.poi.PoiTypes
  private static final java.util.Map<net.minecraft.world.level.block.state.BlockState,
        net.minecraft.core.Holder<net.minecraft.world.entity.ai.village.poi.PoiType>> TYPE_BY_STATE;
  private static void registerBlockStates(net.minecraft.core.Holder<PoiType>, java.util.Set<BlockState>);
  private static PoiType register(Registry<PoiType>, ResourceKey<PoiType>, Set<BlockState>, int, int);
  public static PoiType bootstrap(Registry<PoiType>);
  public static java.util.Optional<Holder<PoiType>> forState(BlockState);
```

- **字段是 private**：`private static final Map<BlockState, Holder<PoiType>> TYPE_BY_STATE;`（javap 输出如上）。**写它的方法也是 private**：`private static void registerBlockStates(Holder<PoiType>, Set<BlockState>)`。
- **唯一填充路径**：`private static PoiType register(Registry<PoiType>, ResourceKey<PoiType>, Set<BlockState>, int, int)` 的字节码为
  `new PoiType(states, maxTickets, validRange)` → `Registry.register(registry, key, poiType)` → `registry.getHolderOrThrow(key)` → `registerBlockStates(holder, states)`。
  ⇒ 一个调用里同时写「注册表」和「BlockState→PoiType 映射」。
- `registerBlockStates` 的字节码是 `aload_1; aload_0; invokedynamic accept:(Holder)Consumer; Set.forEach`，即对每个 state 调 `method_43991(Holder, BlockState)`；`method_43991` 的字节码是 `getstatic TYPE_BY_STATE` → `Map.put(state, holder)`，**若旧值非 null 就 `new IllegalStateException` 抛字面量 `"%s is defined in more than one PoI type"`**（外层套 `Util.pauseInIde`）。
- `TYPE_BY_STATE` 只在 `PoiTypes` 静态初始化里被赋值一次（`Maps.newHashMap()` + `putstatic TYPE_BY_STATE`）⇒ 是一个**可变 HashMap，且永远不被清空**。
- `public static PoiType bootstrap(Registry<PoiType>)` 的字节码是一长串 `getstatic <常量 key>; getstatic Blocks.<X>; getBlockStates(Block); iconst_1; iconst_1; invokestatic register(...)` —— 逐条登记原版 POI，**只追加，不重建 map**。
- 冻结链：`BuiltInRegistries.bootStrap()` = `createContents(); freeze(); validate(REGISTRY)`；`BuiltInRegistries.freeze()` = `REGISTRY.freeze()` 再遍历所有内置注册表逐个 `Registry.freeze()`；`MappedRegistry` 有 `private boolean frozen`，`private void validateWrite()` 在 `frozen` 时抛 `IllegalStateException("Registry is already frozen")`。`BuiltInRegistries` 的静态初始化给每个注册表挂一个 `BuiltInRegistries$RegistryBootstrap` 到 `LOADERS`，POI 那一条就是 `PoiTypes::bootstrap`，由 `createContents()` 执行 ⇒ **`PoiTypes.bootstrap` 在 mod 初始化之后、`freeze()` 之前跑**。

**⇒ mod 新增 POI 时必须做的事（两步一体，缺一即无效）**

1. 把 `PoiType` 注册进 `BuiltInRegistries.POINT_OF_INTEREST_TYPE`（`Registry.register`，必须在 `BuiltInRegistries.bootStrap()` 之前，也就是 mod 初始化阶段）；
2. 把自己方块的 **全部 BlockState** 写进 `PoiTypes.TYPE_BY_STATE`（值必须是该 PoiType 的 `Holder`）。

**访问方式（三选一，推荐第 1 种）**

| 方案 | 需要什么 | 取证 |
|---|---|---|
| **1. 用 fabric-api `PointOfInterestHelper`** | 零 AW、零 mixin（fabric-api 自己的 AW 已放行原版私有方法） | `PointOfInterestHelper.register(ResourceLocation id, int ticketCount, int searchDistance, Block... blocks)` → `register(ResourceLocation, int, int, Iterable<BlockState>)` → 私有版最终调 `PoiTypes.register(BuiltInRegistries.POINT_OF_INTEREST_TYPE, ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, id), states, tickets, distance)`；它能调私有方法是因为 `fabric-object-builder-api-v1.accesswidener` 有 `accessible method net/minecraft/class_7477 method_43993 (Lnet/minecraft/class_2378;Lnet/minecraft/class_5321;Ljava/util/Set;II)Lnet/minecraft/class_4158;`（class_7477=PoiTypes，class_4158=PoiType） |
| 2. 自己写 accesswidener | 2 行：`accessible field net/minecraft/world/entity/ai/village/poi/PoiTypes TYPE_BY_STATE Ljava/util/Map;` + `accessible method net/minecraft/world/entity/ai/village/poi/PoiTypes registerBlockStates (Lnet/minecraft/core/Holder;Ljava/util/Set;)V` | 字段/方法 private 由 javap 确认；AW 文件已在 loom 生效（`fabric-1.20.1/build.gradle:202 accessWidenerPath = file('src/main/resources/astral_dice.accesswidener')`），加行即生效 |
| 3. 自己写 mixin | `@Mixin(PoiTypes.class)` + `@Accessor("TYPE_BY_STATE")` 或 `@Invoker("registerBlockStates")` | 同上 |

**必须记住的两个硬约束**

- 一个 BlockState **只能属于一个 PoiType**：重复写入会抛 `"%s is defined in more than one PoI type"`。⇒ mod 的 POI 只能登记**自己的方块**；若把自己的方块状态塞进原版 POI 或反之，会在 `bootstrap` 阶段崩。
- `PoiType` 必须在冻结前注册（否则 `Registry is already frozen`），而 `TYPE_BY_STATE` 永不过期 —— 所以「先注册 + 先写 map」在 mod 初始化内一次做完最安全（不要拖到 `SERVER_STARTED`）。

**Forge 侧对照（解释为什么 forge 线不需要写这个 map）**：在 Forge AT-patched jar 上 `javap -p` 得到 `private static final java.util.Map<BlockState, PoiType> TYPE_BY_STATE;`（值类型是裸 `PoiType`，已被 patch），且 `private static void registerBlockStates(Holder<PoiType>, Set<BlockState>)` 的方法体是 `0: return` —— **空方法**。⇒ Forge 语境下原版路径不再写映射，改由 Forge 自己的 slave map 承担；这条差异**不能**带进 Fabric，Fabric 走的是原版语义。

### A3 `Registries.POINT_OF_INTEREST_TYPE` / `Registries.VILLAGER_PROFESSION` 在 Fabric 1.20.1 的 mod 初始化阶段是否可写

**结论：注册表层面可写（是）；但「可写」不等于「POI 生效」，还必须写 `TYPE_BY_STATE`（见 A2）。**

依据链（全部实读）：

1. **Fabric 侧的写路径就是原版静态注册**：`fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/platform/registry/DeferredRegister.java`（141 行）
   - L111-120 `commit()` → `resolveRegistry()` → 逐条 `bindUnchecked`；
   - L123-128 `bindUnchecked`：`I value = entry.supplier().get(); Registry.register(target, entry.holder().getId(), value); entry.holder().bind(value);` —— 即原版 `Registry.register(Registry<T>, ResourceLocation, T)`（javap 确认该静态方法存在：`public static <V, T extends V> T register(Registry<V>, ResourceLocation, T)`）；
   - L130-140 `resolveRegistry()`：`BuiltInRegistries.REGISTRY.get(registryKey.location())`，取不到就抛 `IllegalStateException("No registry found for key " + ...)` ⇒ 任何 `ResourceKey<? extends Registry<T>>`（含 `Registries.POINT_OF_INTEREST_TYPE` / `Registries.VILLAGER_PROFESSION`）都能解析；
   - 类头 L28-30 自述契约：「commit 必须在 mod 初始化阶段调用（Fabric 的注册表在此阶段仍可写）」。
2. **已成功注册的注册表清单**（`grep DeferredRegister.create fabric-1.20.1/src/main/java` 的全部实体调用点，共 7 处，另 2 处是 javadoc）：

   | 文件:行 | 注册表 |
   |---|---|
   | `init/ModParticles.java:31` | `Registries.PARTICLE_TYPE` |
   | `screen/ModMenuTypes.java:17` | `Registries.MENU` |
   | `recipe/ModRecipeSerializers.java:11` | `Registries.RECIPE_SERIALIZER` |
   | `effect/ModEffects.java:32` | `Registries.MOB_EFFECT` |
   | `effect/ModEnchantments.java:16` | `net.minecraft.core.registries.Registries.ENCHANTMENT` |
   | `audio/ModSounds.java:52` | `Registries.SOUND_EVENT` |
   | `item/ModItems.java:110` | `net.minecraft.core.registries.Registries.ITEM` |

   另有 `init/ModCreativeTabs.java:19` `DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(...)`（链式写法，走 `create(Registry, String)` 重载）。
   `AstralDiceMod.java` L107-116 `commitRegistrations()` 对 ITEMS / EFFECTS / SOUNDS / ENCHANTMENTS / RECIPE_SERIALIZERS / CREATIVE_TABS / MENU_TYPES / PARTICLE_TYPES 逐个 `.register(BUS)` ⇒ 这 8 张注册表**在 `onInitialize()` 内同步完成注册且生产可用**。
   `grep DeferredRegister.create` 在 fabric 线**没有任何** `Registries.POINT_OF_INTEREST_TYPE` / `Registries.VILLAGER_PROFESSION` 调用点。
3. **为什么 ITEMS/EFFECTS 能注册 ⇒ POI/职业同理（注册表层面）**：`BuiltInRegistries.freeze()` 是**统一**遍历所有内置注册表逐个 `freeze()` 的（见 A2 的冻结链），没有「部分注册表更早冻结」的分支；`BuiltInRegistries` 里 `POINT_OF_INTEREST_TYPE` 是 `public static final Registry<PoiType>`、`VILLAGER_PROFESSION` 是 `public static final DefaultedRegistry<VillagerProfession>`（默认值 `VillagerProfession.NONE`，由 `private static VillagerProfession method_47454(Registry)` 提供）。既然 8 张注册表在 mod 初始化阶段注册都不抛 `"Registry is already frozen"`，说明 `onInitialize()` 运行在 `BuiltInRegistries.bootStrap()` 之前 ⇒ POI/职业这两张表此刻同样未冻结、同样可写。
   （`Registries.POINT_OF_INTEREST_TYPE` 与 `Registries.VILLAGER_PROFESSION` 两个 `ResourceKey` 在 1.20.1 原版已存在，javap 确认。）
4. **差异点**：POI 的「生效」还要求写 `TYPE_BY_STATE`，而那张 map 不是注册表、不受 freeze 管辖（它是 `PoiTypes` 的私有静态 HashMap），必须经 `register` 路径（=`PointOfInterestHelper`）一起写。

### A4 Fabric 1.20.1 有没有「官方回调」可注册 POI / 职业

**`net.fabricmc.fabric.api.registry` 包下：没有。** 该包（module `fabric-content-registries-v0`）只有 18 个类：CompostingChanceRegistry、FabricBrewingRecipeRegistry、FlammableBlockRegistry(+Entry)、FlattenableBlockRegistry、FuelRegistry、LandPathNodeTypesRegistry(+Dynamic/PathNodeTypeProvider/StaticPathNodeTypeProvider)、LootEntryTypeRegistry、OxidizableBlocksRegistry、SculkSensorFrequencyRegistry、StrippableBlockRegistry、TillableBlockRegistry、VillagerInteractionRegistries（+ `net/fabricmc/fabric/api/util` 的 Block2ObjectMap、Item2ObjectMap）。其中名字最接近的 `VillagerInteractionRegistries` 管的是「村民交互行为」，**不是**注册表条目注册，不能用来注册职业。

**但 fabric-api 确实提供了官方 API，只是不在 `api.registry` 下**（module `fabric-object-builder-api-v1` 11.1.5）：

| 需求 | 官方 API（确切类名） | 形态 |
|---|---|---|
| 注册 POI | `net.fabricmc.fabric.api.object.builder.v1.world.poi.PointOfInterestHelper` | `register(ResourceLocation id, int ticketCount, int searchDistance, Block... blocks)` / `register(ResourceLocation, int, int, Iterable<BlockState>)`，一次性写注册表 + `TYPE_BY_STATE` |
| 注册职业 | `net.fabricmc.fabric.api.object.builder.v1.villager.VillagerProfessionBuilder`（**@Deprecated**）或直接 `new VillagerProfession(...)` | 见 B5/A1：现代推荐做法是直接调原版 6 参 ctor —— `fabric-transitive-access-wideners-v1` 已把它 `transitive-accessible`（该 AW 共 282 行，含 `class net/minecraft/class_3853$class_4161/…` 等 `VillagerTrades` 内部类；**不含任何 POI 相关项**，所以 POI 必须走 PointOfInterestHelper 或自带 AW） |
| 注册交易 | `net.fabricmc.fabric.api.object.builder.v1.trade.TradeOfferHelper` | 见 §B |

> `VillagerProfessionBuilder` 的 javadoc 里有两句可直接照抄的资源契约：职业纹理在 `assets/<namespace>/textures/entity/villager/profession/<path>.png`，同目录还要一个 `<path>.mcmeta`（HatType / 帽子层）；以及「本类不注册任何交易 —— 要给 `VillagerTrades.TRADES` 加一条以你的职业为 key 的条目」。

---

## §B 村民交易表

### B5 `trade/CardTraderTrades.java` / `ChipTraderTrades.java` 在 forge/neo 怎么挂上去；Fabric 的替换点

**neoforge-1.21.1/.../trade/CardTraderTrades.java（168 行）**

- 入口：类注解 `@EventBusSubscriber(modid = AstralDiceMod.MODID)`（L52，NeoForge game bus）+ `@SubscribeEvent public static void onVillagerTrades(VillagerTradesEvent event)`（L93-121）。
- 事件类：`net.neoforged.neoforge.event.village.VillagerTradesEvent`（L19-22 import 区）。
- 写法：`if (event.getType() != ModVillagerProfessions.CARD_TRADER.get()) return;` → 逐级 `for level 1..5` 调 `event.getTrades().get(level).add(new CardTraderListing(pool, MIN_PRICE[level], MAX_PRICE[level], VILLAGER_XP[level]))`；第 3 级额外加 `new BasicItemListing(new ItemStack(Items.EMERALD), new ItemStack(ModItems.STAR_COIN.get()), 12, 1, 0.0F)`。
- Listing 实现：`private record CardTraderListing(List<CardShopCatalog.PoolEntry> pool, int minPrice, int maxPrice, int villagerXp) implements VillagerTrades.ItemListing`（L141-167），`getOffer(Entity trader, RandomSource random)` 里 `new MerchantOffer(new ItemCost(Items.EMERALD, priceFor(result)), result, CARD_MAX_USES, villagerXp, CARD_PRICE_MULTIPLIER)`（**`ItemCost` 重载是 1.21 才有**）。
- 触发时机（neo）：NeoForge `VillagerTradingManager` 挂在 `TagsUpdatedEvent`（SERVER_DATA_LOAD）上 ⇒ **datapack reload 会重放**该事件（L47-50 自述）。

**forge-1.20.1/.../trade/CardTraderTrades.java（200 行）**

- 入口：`@Mod.EventBusSubscriber(modid = AstralDiceMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)`（L84）+ 同名 `@SubscribeEvent onVillagerTrades(VillagerTradesEvent)`（L125-153）。
- 事件类：`net.minecraftforge.event.village.VillagerTradesEvent`，挂在 `MinecraftForge.EVENT_BUS`；`event.getTrades()` 返回 `Int2ObjectMap<List<VillagerTrades.ItemListing>>`，`event.getType()` 返回 `VillagerProfession`。
- 触发链（L45-61、L63-82 自述，本轮沿用其引用的 forge 源码行）：`VillagerTradingManager.loadTrades(ServerAboutToStartEvent)` → `postVillagerEvents()` 遍历 `ForgeRegistries.VILLAGER_PROFESSIONS` → 每级建非空 `NonNullList` → `MinecraftForge.EVENT_BUS.post(new VillagerTradesEvent(...))` → 写回 `VillagerTrades.TRADES`。**只触发一次**（`/reload` 不重放）。
- 报价构造：`new MerchantOffer(new ItemStack(Items.EMERALD, priceFor(result)), result, CARD_MAX_USES, villagerXp, CARD_PRICE_MULTIPLIER)`（L188-189）—— 因为 1.20.1 的 `MerchantOffer` **没有 `ItemCost` 重载**（javap 只列 5/6/7/8 参的 `ItemStack` 构造器）。
- `ChipTraderTrades.java` 两线同构：neo 用 `@EventBusSubscriber`+`VillagerTradesEvent`；forge 用 `@Mod.EventBusSubscriber(bus = Bus.FORGE)` + 同事件 + `ItemStack` 版 `MerchantOffer`。价格常量 `MIN_PRICE={0,20,24,32,40,48}`、`MAX_PRICE={0,24,32,40,48,64}`、`VILLAGER_XP={0,2,5,10,15,30}`，价格 = `clamp(min + round((max-min)*(tier-1)/2), min, max)`。

**Fabric 1.20.1：该事件不存在（`porting/fabric-1.20.1/MAPPING-1.5.0.md` §2.1/§2.9 明确写「无事件」）。替换点有两级：**

**① 首选：`TradeOfferHelper`（零 mixin，官方 API）**

- 签名（fabric-object-builder-api-v1 11.1.5 源码实读）：
  `public static void registerVillagerOffers(class_3852 profession, int level, Consumer<List<class_3853.class_1652>> factories)`
  （`class_3852`=`VillagerProfession`，`class_3853`=`VillagerTrades`，`class_3853.class_1652`=`VillagerTrades.ItemListing`）另有 `registerWanderingTraderOffers(int level, Consumer<List<...>>)`。
- 实现语义（`TradeOfferInternals` 源码实读）：`registerVillagerOffers` 是 `synchronized`，内部 `registerOffers(class_3853.field_17067.computeIfAbsent(profession, key -> new Int2ObjectOpenHashMap<>()), level, factory)`，再
  `originalEntries = leveledTradeMap.computeIfAbsent(level, k -> new ItemListing[0]); allEntries = ArrayUtils.addAll(originalEntries, addedEntries); leveledTradeMap.put(level, allEntries);`
  ⇒ **直接原地改 `VillagerTrades.TRADES`，调用即生效**，与 forge 的「追加到该等级列表」同语义。
- 原版配套事实（javap）：`VillagerTrades.TRADES` = `public static final Map<VillagerProfession, Int2ObjectMap<VillagerTrades.ItemListing[]>> TRADES;`（descriptor `Ljava/util/Map;`）；其静态初始化是 `TRADES = Util.make(Maps.newHashMap(), map -> {…})`，**整个原版表在类初始化时就建好**，所以 mod 侧调用 `TradeOfferHelper` 会顺带触发 clinit，再追加自己的条目 —— 顺序安全。
- 时机建议：放在 `onInitialize()`（mod 初始化）里，紧跟职业注册之后。注意 `TradeOfferHelper.refreshOffers()` 是 **`@Deprecated(forRemoval=true)` 的空操作**，只在被调用时打一条 warn，**不要用**。

**② 需要「替换 / 每次重算」时才用 mixin**（追加语义用不上，但记录精确注入点）

- 目标类：`net.minecraft.world.entity.npc.Villager`；方法：`protected void updateTrades()`，**descriptor `()V`**（`javap -s -p` 原文：`protected void updateTrades(); descriptor: ()V`）。
  - 原版行为（`javap -c`）：`getstatic VillagerTrades.TRADES` → `VillagerData.getProfession()` → `Map.get` → `Int2ObjectMap.isEmpty()` 为真就 `return` → `Int2ObjectMap.get(level)` → checkcast `[LVillagerTrades$ItemListing;` → `getOffers()` → `addOffersFromItemListings(offers, listings, iconst_2)`。
    ⇒ **每次升级都从 TRADES 重读（不缓存），且原版每级恰好塞 2 个 offer** —— 所以在任何村民升级之前改表即可生效。
  - 推荐注入：`@Inject(method = "updateTrades", at = @At("HEAD"))`，在方法体最前面做幂等补写（`TRADES.computeIfAbsent(...)`）。选 HEAD 而非 TAIL 的原因：vanilla 在 HEAD 之后立刻读表，TAIL 已晚一步（那一级已经发放完）。
- 备选目标类：`net.minecraft.world.entity.npc.AbstractVillager`；方法：`protected void addOffersFromItemListings(MerchantOffers, VillagerTrades.ItemListing[], int)`，**descriptor `(Lnet/minecraft/world/item/trading/MerchantOffers;[Lnet/minecraft/world/entity/npc/VillagerTrades$ItemListing;I)V`**。此处能直接拿到目标 `MerchantOffers` 与候选数组，适合「按玩家状态动态过滤/插入 offer」；`HEAD` 在 vanilla 添加前介入（可 `cancellable = true` 完全接管），`RETURN` 在添加后补。
- 容器位置：fabric 线已有 `mixin/trade/`（`MerchantContainerMixin`、`MerchantMenuMixin`、`MerchantResultSlotMixin`、`ServerPlayerMixin`、`MerchantOfferMixin`，已登记在 `fabric-1.20.1/src/main/resources/astral_dice.mixins.json` 的 `"mixins"` 末 5 条 L31-35），新增 `VillagerTradeTableMixin`（或同职责类）直接加进这个配置即可，`injectors.defaultRequire = 1` 已是全局默认。

**两侧都要补的配套件（不影响编译，但不补就不生效）**

- 认领标签：`data/minecraft/tags/point_of_interest_type/acquirable_job_site.json` —— fabric 线**没有**（`grep` 与目录实测均为 0）。缺它 ⇒ 村民不会被吸引到工作方块、无法就职/升职（A1 的 L14-28 文档已写明这是「可发现 / 可升职」的唯一入口）。
- 职业私有站点标签：`data/astral_dice/tags/point_of_interest_type/card_trader_job_site.json` 与 `chip_trader_job_site.json`（对应 `ModPoiTypes` L72-79 的两个 `TagKey`）—— fabric 线同样没有。
- 职业纹理：`assets/astral_dice/textures/entity/villager/profession/card_trader.png` / `chip_trader.png` **各缺 1 张**（MAPPING §2.7 已标），另按 A4 的 javadoc 还需要同名 `.mcmeta`。
- `BasicItemListing`：那是 `net.minecraftforge.common.BasicItemListing`（Forge 类），Fabric 侧要自建一个等价 `ItemListing` 实现（或写进同一个 `trade/` 包）。

---

## §C 方块与实体注册

### C6 `block/ModBlocks.java`（4 方块）与 `entity/ModEntityTypes.java`（2 实体）的加载器专属 API、Fabric 等价、shim 复用、额外步骤

**方块（4 个：`CARD_SHOP` / `CHIP_SHOP` / `EVENT_DRAWER` / `BARRICADE_COLLISION`）**

| 线 | 注册器 | holder 类型 | 证据 |
|---|---|---|---|
| neo（`block/ModBlocks.java`，55 行） | `DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(AstralDiceMod.MODID)`（L14） | `DeferredBlock<Block>`（L16/L25/L38/L50） | **NeoForge 专有**：`DeferredRegister.Blocks` / `DeferredBlock` |
| forge（`block/ModBlocks.java`，64 行） | `DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, AstralDiceMod.MODID)`（L22-23） | `RegistryObject<Block>`（L25/L34/L43/L54） | Forge 通用重载 + `RegistryObject` |

四个方块都是**纯 `Block` 子类、没有 BlockEntity**（两份 `ModBlocks` 里没有任何 block-entity 注册）。属性链都用原版 `BlockBehaviour.Properties.of()`：`.mapColor(MapColor.WOOD|METAL)`、`.strength(2.5F)`（`BARRICADE_COLLISION` 为 `.strength(-1.0F, 3600000.0F)`）、`.sound(SoundType.WOOD|METAL)`、`.noOcclusion()`、`.noLootTable()`、`.isRedstoneConductor((s,l,p) -> false)`、`.isSuffocating(...)`、`.isViewBlocking(...)`。

**Fabric 等价写法（DeferredRegister shim 可直接复用）**

```java
public static final DeferredRegister<Block> BLOCKS =
        DeferredRegister.create(Registries.BLOCK, AstralDiceMod.MODID);
public static final RegistryObject<Block> CARD_SHOP =
        BLOCKS.register("card_shop", () -> new CardShopBlock(BlockBehaviour.Properties.of()...));
```

- 依据：shim 的 `commit()` 是原版 `Registry.register`（A3 第 1 条），`Registries.BLOCK` 与 `Registries.ITEM` 同级同冻结语义；MAPPING §2.4 的方块行已给出同形态目标写法。
- `BlockBehaviour.Properties.of()` 在 1.20.1 是 `public static`（javap：`public static BlockBehaviour$Properties of();`），`noOcclusion()/sound()/strength(float)/strength(float,float)` 也都是 public ⇒ **不需要 `FabricBlockSettings`**（它只是 fabric 的包装门面）。
- 额外步骤：① 必须在 `AstralDiceMod.commitRegistrations()`（L107-116）里对 `BLOCKS` 加一行 `.register(BUS)`；② 方块物品要**手工**注册 —— 1.20.1 没有 `registerSimpleBlockItem`，照 fabric 线 `item/ModItems.java:110` 的 `ITEMS` 用法写 `ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()))`（MAPPING §2.4「方块物品」行同理）。

**实体（2 个：`BARRICADE` / `REPULSION`）**

| 线 | 注册器 / holder | 证据 |
|---|---|---|
| neo | `DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID)`（L29-30）；`DeferredHolder<EntityType<?>, EntityType<BarricadeEntity>>`（L33-38） | neo 专属 holder/工厂重载 |
| forge | `DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID)`（L35-36）；`RegistryObject<EntityType<BarricadeEntity>>`（L39-44） | Forge 重载 |

两线都用**纯原版构建链**（javap 确认全部存在）：
`EntityType.Builder.of(EntityType$EntityFactory<T>, MobCategory)` → `.sized(float,float)` → `.clientTrackingRange(int)` → `.build(String)`（`public EntityType<T> build(java.lang.String)`）。
neo：`BARRICADE` = `.of(BarricadeEntity::new, MobCategory.MISC).sized(1.2F, 2.0F).clientTrackingRange(8).build("barricade")`；`REPULSION` = `.sized(1.6F, 2.4F).clientTrackingRange(10).build("repulsion")`；forge 逐字同形。

**Fabric 等价的写法与额外步骤**

1. 注册器：`DeferredRegister.create(Registries.ENTITY_TYPE, MODID)` + `RegistryObject<EntityType<...>>`，并在 `commitRegistrations()` 里注册。**shim 可直接复用**（同上，原版 `Registry.register`）。
2. 实体类本体：fabric 线**根本没有 `entity/` 包**（`ls .../entity/` 空；`grep BarricadeEntity|RepulsionEntity` 在 fabric 主源码 0 命中）⇒ `entity/BarricadeEntity.java`、`entity/RepulsionEntity.java`、`entity/ModEntityTypes.java` 全是从零新建。两者在两线都是 `public class X extends LivingEntity`（neo `BarricadeEntity.java:75`、`RepulsionEntity.java:173`；forge `:88`、`:195`），**不是 Mob 子类** ⇒ `FabricEntityTypeBuilder.createMob()` 不适用；要么直接用原版 `EntityType.Builder`，要么用 `FabricEntityTypeBuilder.create(MobCategory.MISC, BarricadeEntity::new)`（其 `dimensions` 默认 `EntityDimensions.scalable(-1.0f, -1.0f)`，**必须显式 sized**）。
3. 属性注册（**Fabric 没有 `EntityAttributeCreationEvent`**）：改用 `net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry`：
   - `public static void register(EntityType<? extends LivingEntity> type, DefaultAttributeContainer.Builder builder)`（源码 L56）
   - `public static void register(EntityType<? extends LivingEntity> type, DefaultAttributeContainer container)`（L78）
   直接抄 forge `event/ModEntityEvents.java:34-48` 的两个值：`LivingEntity.createLivingAttributes().add(Attributes.MAX_HEALTH, 30.0D).build()`（BARRICADE）与 `…50.0D`（REPULSION）。**漏注册的后果是生成即崩**（forge 该文件 L14-17 已写明）。
4. 客户端渲染：`client/BarricadeModel` / `BarricadeRenderer` / `RepulsionModel` / `RepulsionRenderer` 在 fabric 线全缺（`client/` 21 个文件清单里没有），要新建；模型层用 `EntityModelLayerRegistry`（见 C7）。
5. BlockEntity：本项目 4 个方块**都没有 BlockEntity** ⇒ `FabricBlockEntityTypeBuilder` 本轮用不到；备查签名：`create(Factory<? extends T> factory, Block... blocks)`（L44）、`addBlock(Block)`（L57）、`addBlocks(Block...)`（L68）、`build()`（L73）/ `build(Type<?>)`（L77）。若将来加带 BE 的方块，还必须在客户端调 `BlockEntityRendererRegistry.register(BlockEntityType<E>, BlockEntityRendererProvider<? super E>)`。

### C7 Fabric 1.20.1 注册 `EntityType` 与客户端 `EntityRenderer` 的确切 API 名 / 签名 / 调用时机

- `EntityType`（服务端/两端）：就是**原版** `net.minecraft.core.Registry`（`Registries.ENTITY_TYPE`）+ `EntityType.Builder`，没有 fabric 专属 API。签名见 C6。
- 客户端 renderer：**`net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry`**（module `fabric-rendering-v1` 3.0.10+54550fb677；即题目所问的那个类名，实测存在）
  ```
  public static <E extends Entity> void register(EntityType<? extends E> entityType,
                                                  EntityRendererProvider<E> entityRendererFactory)
  ```
  源码 L39（`class_1297`=Entity，`class_1299`=EntityType，`class_5617`=EntityRendererProvider）。
- 客户端**模型层**（因为两个实体都用自定义 `ModelLayerLocation`）：`net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry`
  ```
  public static void registerModelLayer(class_5601 modelLayer, TexturedModelDataProvider provider)
  ```
  其中 `TexturedModelDataProvider` 是内嵌 `@FunctionalInterface`（`getTexturedModelData()`）；`class_5601` = `ModelLayerLocation`。
- **调用时机**：两者都是「客户端初始化期一次性登记」，从 client entrypoint 调（`fabric-1.20.1/src/main/resources/fabric.mod.json` 的 `entrypoints.client` = `com.merlinkitsune.astral_dice.AstralDiceClient`，即 `ClientModInitializer#onInitializeClient`），必须在任何实体渲染之前、且只在客户端。不要试图拖到渲染线程/延迟任务里（`EntityRenderDispatcher` 在不含该 type 的映射时不会给你补登记的机会）。
  - 对照：neo 用 `EntityRenderersEvent.RegisterRenderers#registerEntityRenderer` + `EntityRenderersEvent.RegisterLayerDefinitions#registerLayerDefinition`（`neoforge-1.21.1/.../client/ModClientEvents.java:53-64`）；forge 用 `net.minecraftforge.client.event.EntityRenderersEvent`（`forge-1.20.1/.../client/ModClientEvents.java:76-83`）。**Fabric 没有这两个事件**，直接换成上面两个静态 `register` 调用。
- 顺带：菜单屏幕在 Fabric 用原版 `MenuScreens.register(MenuType, ScreenConstructor)`（本仓已在 `client/ModClientEvents.java` 的 `onClientSetup` 里这样注册 `CARD_INVENTORY`），不要找 `RegisterMenuScreensEvent`。

---

## §D 网络 payload

### D8 `SimpleChannel` 的能力边界；能否承载「自定义 payload 类型 + 客户端接收后渲染」；缺什么

**结论：能。而且本仓 fabric 线已经用它做过「服务端权威数据 → 客户端渲染」这件事，有 4 个现成先例。`SimpleChannel` 本身不需要新增方法；缺的是消息类与客户端接收端（sink）。**

**能力面（`fabric-1.20.1/.../platform/network/SimpleChannel.java`，172 行，全文实读）**

- 线格式 `[VarInt messageId][payload...]`；类头 L21-40 自述「1.20.1 的 FAPI 没有 `PayloadTypeRegistry`/原版 `CustomPayload`（那是 1.20.5+），一个 `ResourceLocation` 只能对应一个裸字节通道 ⇒ 用前置 VarInt 区分 12 种消息」。
- `public <T> void registerMessage(int messageId, Class<T> type, BiConsumer<T, FriendlyByteBuf> encoder, Function<FriendlyByteBuf, T> decoder, BiConsumer<T, Supplier<NetworkEvent.Context>> handler)`（L71-77）—— 与 Forge 的 `SimpleChannel#registerMessage` 同名同参。
- `public void send(PacketDistributor.Target target, Object message)`（L82-101）：**每个收件人单独编码**（L96-97 注释：复用同一个 `FriendlyByteBuf` 会让第二个收件人收到空包），再 `ServerPlayNetworking.send(player, id, encode(message))`。
- `public void sendToServer(Object message)`（L104-107）→ `ClientPlayNetworking.send(id, buf)`（客户端发送放在独立 holder 类里，避免专用服务端 `NoClassDefFoundError`）。
- 接收端在**构造 channel 时**就装好（`SimpleChannel` 构造器 L60-63 → `installReceivers()` L124-137）：服务端 `ServerPlayNetworking.registerGlobalReceiver(id, ...)` **无条件**注册；客户端仅在 `FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT` 时注册。
- 回调在**网络线程**执行，`NetworkEvent.Context#enqueueWork(Runnable)` 回主线程（`platform/network/NetworkEvent.java` L18-45）；`setPacketHandled(boolean)` 是空实现（「Fabric 的接收回调返回即视为已处理」）。
- `NetworkRegistry.newSimpleChannel(ResourceLocation, Supplier<String> versionSupplier, Predicate<String> clientAccepted, Predicate<String> serverAccepted)`（`platform/network/NetworkRegistry.java:18-23`）—— 三个 Forge 版本谓词被接受但**忽略**，真正的版本互通由自有 handshake 包完成（`VersionGate`）。

**已经在跑的「payload → 客户端渲染」先例（全部在 `fabric-1.20.1/.../network/ModNetwork.java`）**

| 消息 | 服务端→客户端动作 | 证据 |
|---|---|---|
| `DamageNumberMessage` | `client.DamageNumberStore.add(entityId, bonusDamage, color, x, y, z)` | L200-205 |
| `EnderDieTotemMessage` | `client.EnderDieTotemAnimator.play(entityId)` | L426-430 |
| `TargetSelectStartMessage` | `client.TargetSelectionClient.start(...)` | L342-348 |
| `RenShieldStateMessage` | `combat.RenShieldVisibility.replaceAll(ids)` | L547-556 |

⇒ 「自定义 payload 类型 + 客户端接收后渲染」不仅是能力，已是既有范式；照抄这四个的形态即可。

**缺口（实测差集）**

fabric 线当前 `register()`（L39-66）只登记 **12** 条消息；forge 线登记 **29** 条（id 0..28）。**实测缺 17 条**（`grep CHANNEL.registerMessage` 两侧对差）：

| id | 缺失消息 | 方向（按 forge/neo 语义） |
|---|---|---|
| 12 | CardShopTransferListMessage | S→C（列表同步） |
| 13 | CardShopTransferMessage | C→S |
| 14 | CardShopBuyMessage | C→S |
| 15 | CardShopDenyMessage | S→C |
| 16 | EventCardMessage | S→C |
| 17 | EventChoiceStartMessage | S→C |
| 18 | EventChoiceConfirmMessage | C→S |
| 19 | EventChoiceCloseMessage | S→C |
| 20 | EventChoiceTickMessage | S→C |
| 21 | **ChipShopBuyPayload** | C→S |
| 22 | **ChipShopRefreshPayload** | C→S |
| 23 | **ChipShopDenyPayload** | S→C |
| 24 | CardChoiceConfirmMessage | C→S |
| 25 | CardChoiceCloseMessage | S→C |
| 26 | **BeamFxPayload** | S→C（**渲染用**：客户端画光束） |
| 27 | EventChoiceCancelMessage | C→S |
| 28 | TargetSelectCloseMessage | S→C |

> 口径说明：任务书里写「6 个新消息类」，`porting/fabric-1.20.1/MAPPING-1.5.0.md` §1.0.4 写「8 个内部消息类（轮换 id 顺延）」，两份都是阶段性快照；**以本轮实测差集 17 条为准**。任务书点名的 4 个（ChipShopBuy/Deny/Refresh + BeamFx）都在其中（id 21/23/22/26）。

**要新增的方法/类（明确清单）**

1. `SimpleChannel` 无需任何新方法（能力已足）。
2. `network/ModNetwork.java`：在 `register()` **末尾**按上表顺序追加 17 条 `CHANNEL.registerMessage(id++, X.class, X::encode, X::decode, X::handle)`。**顺序即协议**（forge `ModNetwork.java:67-118` 每条都带注释「只能追加在末尾：顺序即协议，插队会让旧客户端错包」），fabric 必须与 forge 的 29 条顺序**逐 id 对齐**，否则两线客户端无法互通。
3. 17 个消息类：可以继续用 fabric 既有的「静态内部类」形态（= forge 内部类形态），也可以像 forge 那样把 chip/BeamFx 做成顶层类。**其中 4 个可近乎逐字搬**：
   - `forge-1.20.1/.../network/BeamFxPayload.java`（72 行）：6×double + int colorArgb + float widthBlocks + int ttlTicks；`encode` 写 `writeDouble×6, writeInt, writeFloat, writeVarInt`；`handle` 调 `client.BeamFxRenderer.add(...)`。**唯一要改的两处**：删掉 `net.minecraftforge.api.distmarker.Dist` 与 `net.minecraftforge.fml.loading.FMLEnvironment` 两个 import（`if (FMLEnvironment.dist == Dist.CLIENT)` 判断在 fabric 不成立），改为依赖 `SimpleChannel` 只在客户端装 receiver 的事实（L134-137）。
   - `ChipShopBuyPayload`（52 行）：`int offerIndex`，`writeVarInt`；`handle` → `serverPlayer.containerMenu instanceof ChipShopMenu menu` → `menu.handleBuy(serverPlayer, msg.offerIndex)`。
   - `ChipShopRefreshPayload`（36 行）：无字段（`encode` 空体，对应 1.21 的 `StreamCodec.unit`）；`handle` → `menu.handleRefresh(serverPlayer)`。
   - `ChipShopDenyPayload`（48 行）：`int reason`；`handle` → `ChipShopClientState.notifyDeny(msg.reason)`。原因码：`DENY_NO_COIN=0` / `DENY_NO_REFRESH=1` / `DENY_INVENTORY_FULL=2` / `DENY_NONE=-1`（后者从不发送；2026-10-14 起 2 不再发出）。
4. **客户端接收端**（fabric 侧全缺，`grep` 0 命中）：`client/BeamFxRenderer.java`、`client/ChipShopClientState.java`。
5. **服务端容器**（fabric 侧全缺）：`screen/ChipShopMenu.java`（`handleBuy(ServerPlayer,int)` / `handleRefresh(ServerPlayer)`）、`screen/CardShopMenu.java` 等 —— 三个 chip payload 与若干 card payload 的 `handle` 全部依赖这些菜单类，菜单不在则消息类无法编译（fabric `screen/` 目录实测只有 `CardInventoryMenu.java`、`CardInventoryScreen.java`、`ModMenuTypes.java`）。
6. 若改用「原生 payload 类型」而非 SimpleChannel：fabric-networking-api-v1 1.3.15 确实提供 `net.fabricmc.fabric.api.networking.v1.FabricPacket`（`Type<? extends FabricPacket> getType()` + `void write(PacketByteBuf)`）、`PacketType`，以及 `ServerPlayNetworking/ClientPlayNetworking#registerGlobalReceiver(PacketType, handler)`。**但不建议**：本仓已有 12 条走 SimpleChannel、且与 forge 靠「id 顺序」对齐，混用两套协议面会同时承担两套版本互通风险。

---

## §E B0 必须新建 / 改动的文件清单

> 依据列指向本文的取证结论；「改」= 已存在文件、需追加接线。

### E.1 POI / 职业（依据 §A）

| # | 文件路径 | 作用 | 依据 |
|---|---|---|---|
| 1 | `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/world/ModPoiTypes.java`（新建） | `DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, MODID)` 或直接 `PointOfInterestHelper.register(...)` 注册 2 个 POI；两个 `TagKey` | A1、A2、A4 |
| 2 | `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/world/ModVillagerProfessions.java`（新建） | `DeferredRegister.create(Registries.VILLAGER_PROFESSION, MODID)` + 6 参 `new VillagerProfession(...)` | A1、A3、A4 |
| 3 | `fabric-1.20.1/src/main/resources/data/minecraft/tags/point_of_interest_type/acquirable_job_site.json`（新建） | 让村民把两个工作方块识别为「可发现 / 可升职」站点 | A1(L14-28)、B5 |
| 4 | `fabric-1.20.1/src/main/resources/data/astral_dice/tags/point_of_interest_type/card_trader_job_site.json`（新建） | 职业私有的 `heldJobSite` 标签 | A1(L72-74) |
| 5 | `fabric-1.20.1/src/main/resources/data/astral_dice/tags/point_of_interest_type/chip_trader_job_site.json`（新建） | 同上（chip） | A1(L77-79) |
| 6 | `fabric-1.20.1/src/main/resources/assets/astral_dice/textures/entity/villager/profession/card_trader.png`（新建，从 neo 线复制） | 职业纹理 | A4(javadoc)、MAPPING §2.7 |
| 7 | `fabric-1.20.1/src/main/resources/assets/astral_dice/textures/entity/villager/profession/card_trader.png.mcmeta`（新建） | 帽子层定义（HatType），缺则纹理层报错 | A4(javadoc) |
| 8 | 同 6/7 的 `chip_trader.png` / `chip_trader.png.mcmeta`（新建 2） | 同上 | A4、MAPPING §2.7 |
| 9 | `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/AstralDiceMod.java`（改） | `commitRegistrations()` 追加 `POI_TYPES / PROFESSIONS` 的 `.register(BUS)` | A3(1) |
| 10 | `fabric-1.20.1/src/main/resources/astral_dice.accesswidener`（**仅当不走** `PointOfInterestHelper` 才改） | 加 `TYPE_BY_STATE` 字段 + `registerBlockStates` 方法两行 | A2(方案 2/3) |

### E.2 交易表（依据 §B）

| # | 文件路径 | 作用 | 依据 |
|---|---|---|---|
| 11 | `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/trade/CardTraderTrades.java`（新建） | 用 `TradeOfferHelper.registerVillagerOffers(CARD_TRADER.get(), level, factories)` 逐级追加；价格常量/等级梯度照抄 neo L58-88 | B5① |
| 12 | `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/trade/ChipTraderTrades.java`（新建） | 同上（chip 价格梯度 `{0,20,24,32,40,48}`/`{0,24,32,40,48,64}`） | B5① |
| 13 | `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/trade/BasicItemListing.java`（新建） | Forge `BasicItemListing` 的等价 `VillagerTrades.ItemListing` 实现（1 emerald→1 star coin 那条） | B5、A1 |
| 14 | `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/mixin/trade/VillagerTradeTableMixin.java`（**仅当需要替换/动态计算**；纯追加不需要） | `@Inject(method="updateTrades", at=@At("HEAD"))`，descriptor `()V` | B5② |
| 15 | `fabric-1.20.1/src/main/resources/astral_dice.mixins.json`（改，仅当 #14 落地） | 在 `"mixins"` 数组追加该 mixin | B5② |

### E.3 方块 / 实体（依据 §C）

| # | 文件路径 | 作用 | 依据 |
|---|---|---|---|
| 16 | `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/block/ModBlocks.java`（新建） | `DeferredRegister.create(Registries.BLOCK, MODID)` + 4 个 `RegistryObject<Block>` | C6 |
| 17 | `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/block/CardShopBlock.java`（新建） | 方块本体 | C6 |
| 18 | `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/block/ChipShopBlock.java`（新建） | 方块本体 | C6 |
| 19 | `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/block/EventDrawerBlock.java`（新建） | 方块本体 | C6 |
| 20 | `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/block/BarricadeBlock.java`（新建） | 隐形碰撞方块 | C6 |
| 21 | `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/ModItems.java`（改） | 4 个方块的 `BlockItem` 手工注册（1.20.1 无 `registerSimpleBlockItem`） | C6 |
| 22 | `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/entity/ModEntityTypes.java`（新建） | `DeferredRegister.create(Registries.ENTITY_TYPE, MODID)` + `EntityType.Builder.of(...).sized(...).clientTrackingRange(...).build("id")` | C6、C7 |
| 23 | `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/entity/BarricadeEntity.java`（新建，参照 neo 446 行） | `extends LivingEntity` | C6 |
| 24 | `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/entity/RepulsionEntity.java`（新建，参照 neo 1114 行） | `extends LivingEntity` | C6 |
| 25 | `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/ModEntityEvents.java`（新建/重构） | 改用 `FabricDefaultAttributeRegistry.register(type, builder)`，值 = 30 / 50 | C6(3) |
| 26 | `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/AstralDiceClient.java`（改） | `onInitializeClient()` 里调 `EntityRendererRegistry.register(...)` × 2 + `EntityModelLayerRegistry.registerModelLayer(...)` × 2 | C7 |
| 27 | `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/client/BarricadeModel.java`（新建） | 模型 + `LAYER_LOCATION` | C6(4)、C7 |
| 28 | `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/client/BarricadeRenderer.java`（新建） | `EntityRenderer` 子类 | C6(4)、C7 |
| 29 | `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/client/RepulsionModel.java`（新建） | 同上 | C6(4)、C7 |
| 30 | `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/client/RepulsionRenderer.java`（新建） | 同上 | C6(4)、C7 |
| 31 | `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/AstralDiceMod.java`（改） | `commitRegistrations()` 追加 `BLOCKS / ENTITY_TYPES`（与 #9 同一处改动） | C6 |

### E.4 网络（依据 §D）

| # | 文件路径 | 作用 | 依据 |
|---|---|---|---|
| 32 | `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/network/ModNetwork.java`（改） | `register()` 末尾追加 id 12..28 共 17 条 `registerMessage`；追加 17 个消息内部类（或顶层类 + import） | D8(2)(3) |
| 33 | `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/network/BeamFxPayload.java`（新建，从 forge 移） | 6×double+int+float+varInt；`handle` → `client.BeamFxRenderer.add(...)` | D8(3) |
| 34 | `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/network/ChipShopBuyPayload.java`（新建） | `int offerIndex`；→ `ChipShopMenu.handleBuy` | D8(3) |
| 35 | `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/network/ChipShopRefreshPayload.java`（新建） | 无字段；→ `ChipShopMenu.handleRefresh` | D8(3) |
| 36 | `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/network/ChipShopDenyPayload.java`（新建） | `int reason`；→ `ChipShopClientState.notifyDeny` | D8(3) |
| 37 | `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/network/` 下其余 13 个消息类（新建，从 forge `ModNetwork` 内部类搬） | CardShop×4 / Event×5 / CardChoice×2 / TargetSelectClose / 等 | D8(2)(3) |
| 38 | `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/client/BeamFxRenderer.java`（新建） | 客户端光束缓存 + 渲染 | D8(4) |
| 39 | `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/client/ChipShopClientState.java`（新建） | 客户端拒绝态/提示 | D8(4) |
| 40 | `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/screen/ChipShopMenu.java`（新建） | 服务端菜单（`handleBuy` / `handleRefresh`） | D8(5) |
| 41 | `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/screen/ChipShopScreen.java` + `CardShopMenu.java` + `CardShopScreen.java` 等（新建） | 与 14/15/12/13 号消息配对的服务端/客户端 UI | D8(5) |
| 42 | `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/screen/ModMenuTypes.java`（改） | 追加 shop 菜单类型 | D8(5) |

> **不需要改**：`platform/network/SimpleChannel.java`（能力已足，见 D8 结论）；`build.gradle`（AW 已在 `fabric-1.20.1/build.gradle:202` 挂好，新增 AW 行无需改构建脚本）。

---

## §F 无法判定项 / 未完成取证

1. **fabric-loader 的入口点注入 vs `Bootstrap.bootStrap()` 的先后**：本仓不含 fabric-loader 源码，无法用实物取证「entrypoint 在 `Main.main` HEAD 被调用、早于 `Bootstrap.bootStrap()`」这条 loader 内部事实。本文件用两条**间接但充分**的证据替代：①`onInitialize()` 内注册的 8 张内置注册表在生产可用（若已冻结则必抛 `"Registry is already frozen"`）②`BuiltInRegistries.freeze()` 是对全部内置注册表的统一遍历、无例外分支。→ 若要把这条彻底做实，需读 fabric-loader 的 knot/patch 类。
2. **A2 的 Forge 侧「`GameData` slave map 由 onAdd 自动填充」**：本轮只独立复核了「AT-patched `PoiTypes` 的 `TYPE_BY_STATE` 是 `Map<BlockState, PoiType>`、`registerBlockStates` 是空方法（`0: return`）」两点；`GameData.java:509-525` 的填充细节沿用的是 forge 线源码注释的自述，未独立打开 Forge 源码核对。不影响 Fabric 结论（Fabric 走原版语义）。
3. **17 条缺失消息中只有 4 条读取了源码**（BeamFxPayload / ChipShopBuyPayload / ChipShopDenyPayload / ChipShopRefreshPayload）。其余 13 条（CardShop×4、Event×5、CardChoice×2、TargetSelectClose、EventChoiceCancel 中的未读部分）本轮只核到「在 forge `ModNetwork.register()` 的 id 与类名」，字段级细节未逐个读 → §E #37 只能给到「从 forge 内部类搬」的指引，落地阶段需逐类核对 wire 字段。
4. **`DeferredRegister.create(Registry, String)` 重载在 `init/ModCreativeTabs.java:19` 的具体注册表 key** 未逐行读（只见链式写法），不影响 A3 结论（A3 的「可写」由 7 处 `create(...)` + `commit()` 的原版 `Registry.register` 路径支撑）。
5. **实体渲染资源缺口未盘点**：`fabric-1.20.1` 下 barricade / repulsion 的实体贴图、`assets/astral_dice/models` 相关条目是否存在未逐项核实（本轮只确认了 `client/` 无 Model/Renderer 类）。
6. **商店整条链路在 fabric 侧的缺失面只测到目录级**：`screen/` 仅有 `CardInventoryMenu.java` / `CardInventoryScreen.java` / `ModMenuTypes.java`；`client/` 无 `BeamFxRenderer` / `ChipShopClientState`；`grep ChipShopClientState|ChipShopMenu|BeamFxRenderer` 全 0 命中。完整缺失清单（含 `CardShopCatalog` / `ChipShopCatalog` 等纯数据类是否已在 fabric）需下一批次测绘。
7. **`TradeOfferHelper` 与 datapack reload 的语义选择**：fabric 的 `registerVillagerOffers` 在调用时即改 `TRADES`（不随 reload 重放），neo 线会在 reload 时重放、forge 线不重放 —— 三条线行为本就不一致，本报告只陈述事实，不替产品做取舍（若要求「与 neo 一致」需要在 `START_DATA_PACK_RELOAD`/`END_DATA_PACK_RELOAD` 上重跑注册，且有重复追加风险，需先定策略）。
