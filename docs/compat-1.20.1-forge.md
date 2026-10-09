# Astral Dice 1.20.1 Forge 移植审查报告

> 本文档记录 `1.20.1-forge` 分支相对 `1.21.1-main` 的完整差异与移植决策,是后续维护与再移植的依据。
> 版本基线:Forge `1.20.1-47.4.10`、Java 17、Curios 5.14.1+1.20.1、ModDevGradle LegacyForge 2.0.144。

## 一、构建与工具链

| 项 | 1.21.1-main | 1.20.1-forge |
|---|---|---|
| 构建插件 | `net.neoforged.moddev` 2.0.141 | `net.neoforged.moddev.legacyforge` 2.0.144 |
| Java | 21 | 17 |
| Curios | 1.21 NeoForge(Modrinth `yohfFbgD`) | 5.14.1+1.20.1(Modrinth `IPQlZkz1`) |
| Iron's Spellbooks | 本地 jar(compileOnly) | Modrinth maven `hZaEegS9`(modCompileOnly) |
| KubeJS | 2101.7.2(implementation) | 2001.6.5(默认注释,未启用) |
| 产物版本号 | `1.1.2-rc1` | `1.1.2-rc1+1.20.1`(部署清理按后缀隔离) |

Gradle wrapper 保持 9.2.1(legacyforge 支持);`mods.toml` 依赖 forge `[47,)`、curios `[5,6)`、enigmaticlegacy(optional `*`)。

## 二、核心兼容 shim(本分支新增)

| 1.21 API | 1.20.1 替代 | 实现 |
|---|---|---|
| `DataComponentType`(10 个) | ItemStack NBT 键 | `component/ItemDataKey`(Codec↔NBT);常量名不变(`ModDataComponents`);1.21 `Item.Properties.component()` 默认值由 `withItemDefault` 按物品承担 |
| `AttachmentType`(61 个;1.20.1 shim 侧 63 个) | 单一 Capability `AstralData`(大 NBT compound)+ 非玩家实体 ForgeData | `component/AttachedDataKey` + `ModCapabilities`/`AstralData`;`ModAttachments.getX/setX` 包装器签名不变 |
| 附件 `.sync()`(27 键;1.20.1 的 `SYNCED_KEYS` 为 28) | 自建 S2C 同步包 | `ModNetwork.AttachmentSyncMessage`(写入即推;**登录/重生/切维度全量快照恒发全部 synced 键**,服务端缺失的键下发显式默认值;`ModCapabilities` 事件驱动);客户端缓存 `ClientAstralData`(断开连接时由 `client/ClientSessionEvents` 清空) |
| 附件持久化 | 玩家 NBT(ICapabilitySerializable)+ `PlayerEvent.Clone` | **死亡仅复制 `rin_pages` 与 `komachi_damage_bonus`**(与 1.21 侧 `.copyOnDeath()` 的键集合一一对应,见 `AstralData#onPlayerClone`),其余键不复制;维度切换全量保留;`inMemory()` 键(dice_curse_ratio 等)不落盘 |
| `CustomPacketPayload`(4 载荷) | SimpleChannel 消息 | `network/ModNetwork`(DamageNumber/ActionBar/SignActivate/OpenCardInventory/AttachmentSync);`ModNetwork.sendToPlayer/sendToPlayersTrackingEntity/sendToServer` 对应 1.21 PacketDistributor 静态方法 |
| `ModConfigSpec` | `ForgeConfigSpec` | API 同构,仅换类名(import 级) |
| `@Mod(IEventBus, ModContainer)` | `@Mod` + `FMLJavaModLoadingContext` | 主类构造器无参化;`ModLoadingContext.registerConfig` |

## 三、事件重映射(33 处理器)

| 1.21 NeoForge | 1.20.1 Forge | 备注 |
|---|---|---|
| `LivingDamageEvent.Pre`(6 处) | `LivingHurtEvent` | `getContainer().setNewDamage(x)` → `setAmount(x)`;`getNewDamage()` → `getAmount()`;伤害管线均为"护甲结算前",骰战结算逻辑不变 |
| `LivingIncomingDamageEvent` | `LivingAttackEvent`(HIGHEST 记原始值)+ `LivingHurtEvent`(LOWEST 算倍率) | 七咒倍率捕获:`original` 由 AttackEvent 存瞬态附件 `curse_original_amount`,还原 1.21 original/current 语义 |
| `PlayerTickEvent.Pre/Post` | `TickEvent.PlayerTickEvent` + phase 守卫 | START=Pre、END=Post |
| `ClientTickEvent.Post` | `TickEvent.ClientTickEvent`(END) | KeyBindingSetup.ClientEvents |
| `MobEffectEvent.{Expired,Added,Remove}` | 同名 Forge 事件 | — |
| `RegisterGuiLayersEvent`/`LayeredDraw.Layer`/`DeltaTracker` | `RegisterGuiOverlaysEvent`/`IGuiOverlay(ForgeGui,...)` | `VanillaGuiLayers.X` → `VanillaGuiOverlay.X.id()` |
| `RegisterMenuScreensEvent` | 同名 Forge 事件 | 移至 client 类(Dist.CLIENT 守卫) |
| 其余(LivingDeath/Drops/ChangeTarget/ProjectileImpact/LootTableLoad/AnvilUpdate/ItemTooltip/PlayerEvent/LivingEntityUseItem.Finish/AttackEntity) | 同名 Forge 事件 | — |

## 四、注册层

- `DeferredRegister.createItems`/`DeferredItem`/`DeferredHolder` → `DeferredRegister.create(ForgeRegistries.X)` + `RegistryObject`,常量名与 helper(`registerItem`)签名不变。
- `ModDataComponents`/`ModAttachments` 的 DeferredRegister 移除(由 shim 承担)。
- **Curios 槽位注册**:1.21 的 `data/astral_dice/curios/slots/*.json`(dice=1/stand=1/chip=0)改为 FMLCommonSetup IMC `SlotTypeMessage.REGISTER_TYPE`(带 slot 图标);`data/curios/tags/item/*.json` 标签保留。
- **Curios API**:1.20.1 `getCuriosInventory` 返回 LazyOptional → `item/CuriosCompat` 统一 `resolve()` 为 Optional,调用面(33 文件 56 处)保持 1.21 写法;`ICurioItem` 在 Curios 5.x 已有 2/3 参现代签名,`canEquip(SlotContext,ItemStack)`/`onEquip(ctx,curio,prev)` 等原样保留;`registerCurio` 移除(实现 ICurioItem 自动识别)。
- **属性筹码**(速度轮滑/摩托头盔/夹心饼干):`getAttributeModifiers(SlotContext, ResourceLocation, ItemStack)` → `(SlotContext, UUID, ItemStack)`;`AttributeModifier` 由 RL id 改 UUID(`BaseChipItem.attributeModifierId` 稳定派生);操作枚举 `ADD_VALUE/ADD_MULTIPLIED_TOTAL` → `ADDITION/MULTIPLY_TOTAL`;`Holder<Attribute>` → `Attribute`。
- **千咒刻印附魔**:1.21 数据驱动 `enchantment/curse_marker.json` → 代码注册 `effect/ModEnchantments.CURSE_MARKER`(isCurse=true,仅代码施加);`minecraft:curse` 标签保留。
- `MobEffect.addAttributeModifier`:RL id → UUID 字符串(`JasmineSweepEffect.modifierId` 稳定派生)。
- `Holder<MobEffect>`(效果引用)→ 直接 `MobEffect`(1.20.1 MobEffectInstance 构造器取直引用)。
- `DiceUpgradeShapedRecipe`:1.21 Codec/StreamCodec 序列化 → 1.20.1 fromJson/fromNetwork/toNetwork(内嵌原版 pattern/key 解析)。

## 五、数据包/资源差异

| 1.21.1 | 1.20.1 |
|---|---|
| `data/astral_dice/recipe` | `data/astral_dice/recipes` |
| `data/astral_dice/advancement` | `data/astral_dice/advancements` |
| `data/astral_dice/loot_table` | `data/astral_dice/loot_tables` |
| `data/astral_dice/structure` | `data/astral_dice/structures` |
| `data/neoforge/loot_modifiers` + `neoforge:` 前缀(14 个 modifier) | `data/forge/loot_modifiers` + `forge:` 前缀 |
| 无 pack.mcmeta(1.20.5+ 不再需要) | 新建 `pack.mcmeta`(pack_format 15) |
| `neoforge.mods.toml` | `META-INF/mods.toml`(javafml,[47,)) |
| `data/astral_dice/curios/slots|entities` | 移除(IMC 注册) |
| 数据驱动附魔 `enchantment/curse_marker.json` | 移除(代码注册) |
| `DataComponentIngredient.of(POTION_CONTENTS)`(友情徽章药水原料) | `NBTIngredient.of(PotionUtils.setPotion(...))` |
| **通用标签命名空间 `c:`**(NeoForge 自带 `c:bricks` / `c:bricks/normal` / `c:bricks/nether`) | **`forge:`** —— Forge 47.x 自带 `forge:ingots/brick` 与 `forge:ingots/nether_brick`,**没有汇总 `forge:bricks`**,由本模组自建 `data/forge/tags/items/bricks.json`(见下) |

⚠️ **通用标签命名空间必须区分**(2026-10-04 用户实报缺陷):1.20.1 Forge 只提供 `forge:` 命名空间、**不存在任何 `c:` 标签的提供者** ⇒ 配方 / 代码里写 `c:xxx` 会**静默失效**(材料永不可满足,配方永远做不出来,且不报任何错)。本线唯一被引用的通用标签是「砖物品」:1.21.1 用 NeoForge 自带的 `c:bricks`,本线改用 **`forge:bricks`** 并在 `src/main/resources/data/forge/tags/items/bricks.json` **自建汇总标签**(= `#forge:ingots/brick` ∪ `#forge:ingots/nether_brick`,与 NeoForge 的 `c:bricks` 语义等价)。

datagen:`RecipeOutput` → `Consumer<FinishedRecipe>`(`.save(output::accept)`);`ItemModelProvider`/`GatherDataEvent` 为 Forge 包名,PackOutput/lookupProvider 构造器同构。

## 六、神秘遗物联动差异(1.20.1 专属)

- ID 常量(`ModEventHandlers`):`enigmaticlegacyplus:cursed_ring/the_acknowledgment/the_twist` → `enigmaticlegacy:` 同名;`enigmaticaddons:the_bless` 保留(独立模组,若 1.20.1 环境存在则生效,否则安全空操作)。
- `ModList.isLoaded("enigmaticlegacyplus")` → `"enigmaticlegacy"`(3 处)。
- lang:两处 tooltip「神秘遗物+联动 / Enigmatic Legacy+ link」→「神秘遗物联动 / Enigmatic Legacy link」。
- mods.toml optional 依赖 `enigmaticlegacy`。
- **核对清单**(游戏内验证时确认 1.20.1 注册名):七咒之戒 `enigmaticlegacy:cursed_ring`、启示之证 `the_acknowledgment`、倒转之启 `the_twist`;若注册名不符,ID 查询自动空操作不崩溃,仅需改常量。
- 七咒倍率捕获管线(见「事件重映射」)依赖神秘遗物 1.20.1 在 LivingHurtEvent 内应用第一诅咒倍率;若其在其他管线生效,仅倍率捕获退化(不影响其他功能)。

## 七、其余查找 API 机械替换(33 处)

- `ResourceLocation.fromNamespaceAndPath/parse` → `new ResourceLocation(...)`。
- `BuiltInRegistries.MOB_EFFECT.getHolder(rl)`(FannySignItem 营养效果查询)→ `ForgeRegistries.MOB_EFFECTS` 直查。
- `Capabilities.ItemHandler` → `ForgeCapabilities.ITEM_HANDLER`。
- `lookupOrThrow` → `registryOrThrow`。
- `BuiltInRegistries.ITEM.get(rl)` 直接可用(1.20.1 注册表返回 AIR 缺省)。

## 八、无需处理项(与审查结论一致)

- 效果类零 MobEffect 方法覆写(纯标记),仅构造器属性修饰器差异。
- Ars/Goety/Iron's 白名单为 damage-type 字符串(版本无关)。
- FTB/OPAC/女仆联动已是反射调用。
- 无 Java 18+ 语法(代码基线兼容 Java 17)。
- lang/纹理与版本无关(共用 1.21 资产)。

## 八点五、编译循环中发现的补充差异

- **开发/生产需要不同的 mod jar(致命,构建系统)**:`forgeclientuserdev` 运行时加载 **Mojmap 名**的原版类,mods 目录须放 `build/devlibs` 未重混淆 jar;生产 launcher 加载 SRG 名原版类,须放 `build/libs` 重混淆 jar。推错方向都会 `NoSuchFieldError`(dev 报 `f_256929_`、生产报 `MOB_EFFECT`)。pushToDevRun/pushToGame 已按环境分别取 devlibs/libs 产物。
- **RegisterGuiOverlaysEvent.registerAbove 的 id 是纯 path(启动崩溃)**:Forge 自动拼 modid 前缀,传全名 `modid:id` 会生成 `modid:modid:id` 触发 `ResourceLocationException: Non [a-z0-9/._-] character`(1.21 的 RegisterGuiLayersEvent 用 ResourceLocation 全名)。
- **部署必须用 reobfJar 的产物(致命,构建系统)**:MDG LegacyForge 的产物分两级——jar 任务原始输出在 `build/devlibs`(Mojmap 名,未重混淆),`reobfJar`(RemapJar)的最终产物在 `build/libs`(SRG 名)。生产环境字段是 SRG 名(含 static final 常量,如 `Registries.MOB_EFFECT` → `f_256929_`),推送未重混淆 jar 会在注册阶段报 `NoSuchFieldError`。pushToDevRun/pushToGame 已改为 `dependsOn reobfJar` + 读取 `reobfJar.archiveFile`。
- **@SubscribeEvent 成员不能是 private(致命)**:Forge 1.20.1 的 EventAccessTransformer 在类加载时检查,发现 private 的 @SubscribeEvent 抛 `Illegal private member with @SubscribeEvent annotation`,mod 报"class loading errors"无法加载(NeoForge 允许 private)。主类 `onCommonSetup` 已改 public。
- **mods.toml 依赖声明格式(致命)**:Forge 1.20.1 用 `mandatory=true/false` 布尔字段;`type="required"/"optional"` 是 NeoForge 1.20.5+/1.21 格式。FML 47 解析到缺失 `mandatory` 的依赖块时标记整个 jar 为 broken,启动时报"无效的mod文件"(Invalid mod file)。1.20.1 无 optional 依赖类型——非强制依赖即 `mandatory=false`;enigmaticlegacy 联动未声明依赖(代码内 `ModList.isLoaded` 守卫)。

- `NbtOps` 位于 `net.minecraft.nbt`(1.20.5 才移入 `com.mojang.serialization`)。
- `ForgeGui`/`IGuiOverlay`/`VanillaGuiOverlay` 位于 `net.minecraftforge.client.gui.overlay`(非 gui 根包);`RegisterGuiOverlaysEvent.registerAbove` 的 id 参数为 String。
- `RegisterMenuScreensEvent` 不存在:菜单屏幕经 `FMLClientSetupEvent` + `MenuScreens.register(type, factory)`。
- `TickEvent.PlayerTickEvent` 用 `event.player` 字段(无 `getEntity()`)。
- `ItemTooltipEvent.getEntity()` 直接返回 `Player`(1.21 需 instanceof 的调用面在此平铺;对 LivingEntity 事件保留 instanceof)。
- `MobEffectInstance.getEffect()` 直接返回 `MobEffect`;`MobEffects.X` 为直接效果引用(无 `.value()`)。
- `MobEffects.{WIND_CHARGED,WEAVING,OOZING,INFESTED,RAID_OMEN,TRIAL_OMEN}` 为 1.21 效果:复仇之戟筹码的对应加成条件在 1.20.1 移除。
- `MaceItem`/`Items.MACE` 为 1.21 物品:近战武器判定移除重锤分支;星币锤配方第一行中位以 `Items.ANVIL`(铁砧)替代"1 重锤"(配方统一改版后两侧均为 `SXS/DBD/GGG`,仅此格不同)。
- `AttributeModifier` 构造为 `(UUID, String, double, Operation)`;`AttributeInstance.removeModifier(UUID)`;操作枚举 `ADDITION/MULTIPLY_TOTAL`(无 ADD_VALUE/ADD_MULTIPLIED_TOTAL)。
- 药水原料 `NBTIngredient` → `StrictNBTIngredient.of(ItemStack)`(1.20.1 无 NBTIngredient)。
- `FoodProperties.getSaturationModifier()`(非 getSaturation)。
- `ItemStack.consume(1, player)` → `shrink(1)`(服务端路径)。
- `GameTestHelper.makeMockSurvivalPlayer()`(无 GameType 参数版本)。
- `AbstractContainerScreen.renderSlot` 为私有:卡牌栏"大卡缩放"显示在 1.20.1 退化为常规大小(仅视觉差异);`mouseScrolled` 为 3 参(无水平滚动)。
- `LivingChangeTargetEvent.getNewTarget()`(1.21 为 getNewAboutToBeSetTarget)。
- `stack.getCapability(ForgeCapabilities.ITEM_HANDLER)` 返回 `LazyOptional`(取值需 `.orElse(null)`,能力常量无 `.ITEM` 后缀)。
- 开发测试目录按版本隔离并统一存放于仓库根 `run/`:1.20.1 游戏目录为 `run/1.20.1`,1.21.1 为 `run/1.21.1`(2026-09 起从子项目内 run/ 迁移至仓库根)。

## 九、风险与后续验证(用户手动)

1. 游戏内验证 Curios 槽位(dice/stand/chip)IMC 注册与动态筹码栏(佩戴骰子后按星级增长)。
2. 骰战结算:近战触发赐福、怪物防御骰、全力攻击倍率。
3. 附件同步:tooltip 数值(忍者伤害加成/治愈点/星光)、立牌冷却显示。
4. 卡牌栏菜单(H 键)与骰子升级配方(星级/已装卡继承)。
5. 神秘遗物联动(核对清单见上);Iron's/KubeJS 为可选前置。

## 十、卡牌商店批(2026-10-08)平台差异

> 本批 = 卡牌商店(方块 + 菜单 + 界面 + 星光兑换 + 玩家间转账)与「经商立牌」主动技能改写;P0 两线
> (`neoforge-1.21.1` / `forge-1.20.1`)同批落地。下表每一条都经 `:forge-1.20.1:compileJava` 编译期验证
> (其中第 7/8 条是**本批踩到的编译错误**与**对任务书错误说法的纠正**)。移植到 26.1.2 / fabric 时按此表逐条走。

1. **方块注册**:1.21.1 `DeferredRegister.createBlocks(MODID)` + `DeferredBlock<Block>`(`ModBlocks.CARD_SHOP`) ⇒ 1.20.1 `DeferredRegister.create(ForgeRegistries.BLOCKS, MODID)` + `RegistryObject<Block>`。
2. **方块交互**:1.21.1 覆写 `useWithoutItem(BlockState, Level, BlockPos, Player, BlockHitResult)` ⇒ 1.20.1 覆写**六参** `use(BlockState, Level, BlockPos, Player, InteractionHand, BlockHitResult)`(1.20.1 无 `useWithoutItem`)。
3. **方块物品**:1.21.1 走 `DeferredRegister.Items#registerSimpleBlockItem("card_shop", ModBlocks.CARD_SHOP)` ⇒ 1.20.1 `registerItem("card_shop", () -> new BlockItem(ModBlocks.CARD_SHOP.get(), new Item.Properties()))`(显式 `net.minecraft.world.item.BlockItem`)。
4. **菜单类型**:1.21.1 `Supplier<MenuType<X>>` ⇒ 1.20.1 `RegistryObject<MenuType<X>>`(工厂均 `new MenuType<>(X::new, FeatureFlags.DEFAULT_FLAGS)`)。
5. **网络**:1.21.1 = `CustomPacketPayload` + `StreamCodec` 独立文件(`network/CardShopTransferListPayload` / `CardShopTransferPayload`)+ `network/ModPayloads` 的 `registrar.playToClient/playToServer`;1.20.1 = `network/ModNetwork` 的 `SimpleChannel.registerMessage` **顺序 id**(本批在**末尾追加 id 12 / 13**,既有 0..11 顺序不变)+ 内嵌 `static class ...Message`。列表编解码 1.21.1 `ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list())` ⇒ 1.20.1 `buf.writeCollection(list, FriendlyByteBuf::writeUtf)` / `buf.readList(FriendlyByteBuf::readUtf)`;处理端 `context.player()` ⇒ `ctx.get().getSender()`;发包 `PacketDistributor.sendToPlayer` ⇒ `ModNetwork.sendToPlayer(ServerPlayer, msg)`;提示条改用 `new ModNetwork.ActionBarMessage(Component, GameplayConstants.ACTIONBAR_DURATION_TICKS)`。
6. **玩家持久化**:1.21.1 `AttachmentType<Long>` ⇒ 1.20.1 `AttachedDataKey<Long>`(本线 `AttachedDataKey.builder(id, Codec.LONG, () -> 0L).build()`)。本批两个键 `shop_promotion_until` / `shop_transfer_cooldown_end` **两线都不 `.sync()`**、都不进 `SYNCED_KEYS`(客户端只从菜单 DataSlot 读货架状态与冷却)。
7. **Curios 入口(本批编译错误)**:1.21.1 `CuriosApi.getCuriosInventory(player)` 返回 `Optional`;1.20.1 官方 API 返回 `LazyOptional`(**无 `isEmpty()/get()` 同形用法**) ⇒ 本线一律走库 shim `com.merlinkitsune.starenginelib.item.CuriosCompat#getCuriosInventory`(与 `item/CurioSlotUtil.java` 同口径,返回 `Optional`)。本批 3 处(`CardShopMenu` 的星级 / 团队判定、`ParunanSignItem#isEquipped`)。
8. **屏幕生命周期(纠正)**:`AbstractContainerScreen#containerTick()` 在 **1.20.1 也存在**(`tick()` 是 `final`,覆写它会编译失败)⇒ 与 1.21.1 同形覆写 `containerTick()`。⚠️ **任务书里「1.20.1 没有 containerTick、要改覆写 tick()」的说法是错的**,以编译期实证为准。
9. **`GuiGraphics.blit`**:1.21.1 基 9 参(无 blitOffset) ⇒ 1.20.1 10 参 `blit(ResourceLocation, int x, int y, int blitOffset, float u, float v, int w, int h, int texW, int texH)`,本批一律传 `blitOffset = 0`(与 `screen/CardInventoryScreen.java:71` 同款);36×36 卡面仍用 `pose().pushPose()/translate/scale(36/64)` + 64×64 blit 缩放绘制。
10. **`mouseScrolled`**:1.21.1 四参(含 `horizontalAmount`) ⇒ 1.20.1 **三参** `(double, double, double)`(本批只用纵向增量;与本节第 122 行既有登记一致)。
11. **`ResourceLocation`**:1.21.1 `ResourceLocation.fromNamespaceAndPath(ns, path)` ⇒ 1.20.1 `new ResourceLocation(ns, path)`(新增 2 条弃用告警,与本线既有 73 条同款)。
12. **资源目录与配方键**:1.21.1 `data/astral_dice/loot_table/`、`recipe/`、`data/minecraft/tags/block/` ⇒ 1.20.1 **复数** `loot_tables/`、`recipes/`、`tags/blocks/`;配方 `result` 1.21.1 用 `"id"` ⇒ 1.20.1 用 `"item"`。
13. **本批游戏内手工验证项**(需用户点名下指令) —— ① 合成 `card_shop`(8 木板围 1 星币)并右键开店;② 买 1 张卡:确认「已售出」、星币扣除、余额 HUD 同步;③ 星光兑换按钮(2:1,无冷却);④ 转账(5~20、60 秒冷却沙漏、目标离线自动刷新名单);⑤ 同队有经商立牌玩家时的货架加成(额外 1 张战斗牌 + 每张 10% 降价)与**转账后自己货架强制降价一张**;⑥ 经商立牌主动 = 开店 + 120 秒冷却(佩戴)。

## 十一、卡牌商店 UI v4 批(2026-10-08)平台差异

> 本批 = 商店界面按定稿重排 + 购买改为**拖拽成交** + 中按钮 5 态/沙漏/大酬宾流光/卡名缩放。P0 两线同批。

1. **拖拽购买 C2S**：1.21.1 = 新文件 `network/CardShopBuyPayload.java`（`CustomPacketPayload` + `StreamCodec`）+ `network/ModPayloads` 注册 + `PacketDistributor.sendToServer`；1.20.1 = `network/ModNetwork.java` **末尾追加** `CardShopBuyMessage`（**id 14**，既有 0..13 顺序未动）+ `ModNetwork.sendToServer`。
2. **`mouseScrolled`**：1.21.1 四参（含 `horizontalAmount`）⇒ 1.20.1 **三参**；本批逻辑只用纵向增量，语义等价。
3. **`ResourceLocation`**：新代码 2 处 `fromNamespaceAndPath` ⇒ `new ResourceLocation`（新增 2 条同款弃用告警，与本文件第 11 条口径一致）。
4. **Curios 入口**：本批新写的 2 处调用同样走库 shim `CuriosCompat#getCuriosInventory`（见 §十 第 7 条）。
5. **`blit`**：本批**无**差异 —— 1.21.1 侧本就用 10 参重载（`blitOffset=0`），1.20.1 同形可编译（§十 第 9 条在本批再次得到验证）。
6. **`containerTick()`**：两线同形覆写（§十 第 8 条的纠错在本批生效）。
7. **对等证据**：两线 `CardShopScreen` / `CardShopMenu` 去掉 import 与类 javadoc 后的**行级 LCS diff 各仅 16 行**，全部落在上述平台改写与 javadoc 上。

## 十二、商店方块美术批(2026-10-08)平台差异

1. **块模型 `render_type` 两线都支持**（**推翻**先前「Forge 不支持」的猜测）：NeoForge `client/model/ExtendedBlockModelDeserializer.java:66-69` 与 Forge `net/minecraftforge/client/model/ExtendedBlockModelDeserializer` 都读 `render_type` 并 `setRenderTypeHint`，`SimpleUnbakedGeometry` 建 `RenderTypeGroup`，`NamedRenderTypeManager` 预注册 `cutout`；`ChunkRenderDispatcher.java:633` 按 `BakedModel#getRenderTypes` 分派 ⇒ **JSON 即生效**（1.20.1 侧另支持 `render_type_fast`）。
2. **1.20.1 保留 `ItemBlockRenderTypes.setRenderLayer(..., RenderType.cutout())`**（`client/ModClientEvents#onClientSetup`）作为**物品层双保险**；1.21.1 侧该 API 已弃用且不需要 ⇒ **不写**。代价：forge 线多 1 条弃用告警。
3. **交互入口**：1.21.1 = `useWithoutItem(...)`；1.20.1 **无此方法** ⇒ 六参 `use(BlockState, Level, BlockPos, Player, InteractionHand, BlockHitResult)`。
4. **形状**：两线均为 `Shapes.box(0,0,0,1,14.5/16,1)`（`getShape`/`getCollisionShape` 同值）+ `ModBlocks` 的 `.noOcclusion()`；无 API 差异。

## 十三、转账二级面板批(2026-10-08)平台差异

1. **头像 API**：1.21.1 用 `Minecraft#getConnection()#getPlayerInfo(uuid)#getSkin()`（`PlayerSkin` record）+ `DefaultPlayerSkin.get(uuid)` + `PlayerFaceRenderer.draw(GuiGraphics, PlayerSkin, int, int, int)`；**1.20.1 没有 `PlayerSkin` record** ⇒ 用 `PlayerInfo#getSkinLocation()`（`ResourceLocation`）+ `DefaultPlayerSkin.getDefaultSkin(uuid)` + `PlayerFaceRenderer.draw(GuiGraphics, ResourceLocation, int, int, int)`（映射表 `:19065 / :26559 / :10542`）。
2. **载荷载体**：1.21.1 是独立 `CustomPacketPayload`（`CardShopTransferListPayload`，`StreamCodec.composite` + `UUIDUtil.STREAM_CODEC/STRING_UTF8/VAR_LONG`）；1.20.1 复用 `network/ModNetwork.java` 里既有的 **id 12** `CardShopTransferListMessage`，把 `List<String>` 改为 `public record Entry(UUID id, String name, long balance)` 并用 `buf.writeCollection/readList + writeUUID/writeUtf/writeVarLong` 手写编解码（逐字节等价）。**不新增 id**。
3. **常规改写**：`ResourceLocation.fromNamespaceAndPath` → `new ResourceLocation`（本批新增 2 条同款弃用告警，见 §十第 11 条）；`PacketDistributor.sendToServer/sendToPlayer` → `ModNetwork.sendToServer/sendToPlayer`；`mouseScrolled` 四参 → **三参**；Curios 走库 shim `CuriosCompat#getCuriosInventory`。
4. **两线同形、无需改写**（已核实映射）：`EditBox`（构造 + `setBordered/setMaxLength/setHint/setTextColor/setResponder/setValue/setFocused`）、`AbstractSliderButton`（`updateMessage/applyValue/onClick/onDrag/onRelease/renderWidget` + `value/canChangeValue`）、`GuiGraphics.blit` 10 参（`blitOffset=0`）/`fill`/`enableScissor`/`disableScissor`、`AbstractContainerScreen#containerTick()`、`AbstractContainerMenu#removed(Player)`。
5. **对等证据**：`git diff --no-index --unified=0` 两线 `CardShopScreen.java` 差异 **63 行**、`CardShopMenu.java` 差异全部为 import/javadoc/平台符号 —— **界面逻辑零差异**（含几何常量、marquee、箭头位置、门控、会话锁、冷却顺延）。

## 十四、工作方块/村民职业/交易批(2026-10-09)平台差异

1. **`VillagerProfession` 签名相同**（两线都是 6 参 record：`name / heldJobSite / acquirableJobSite / requestedItems / secondaryPoi / workSound`，1.20.1 `VillagerProfession.java:25` 原文取证）⇒ 逐字对位，无取舍。
2. **POI 注册表与映射机制不同**：1.20.1 用 `DeferredRegister.create(ForgeRegistries.POI_TYPES, MODID)`（`ForgeRegistries.java:88`；`Registries.POINT_OF_INTEREST_TYPE` 亦存在）；blockstate→POI 映射由 Forge `GameData` 回调（`GameData.java:504-537` 的 `PointOfInterestTypeCallbacks.onAdd`）自动建立，`PoiTypes.registerBlockStates` 是空方法 ⇒ 注册 PoiType 即生效。
3. **`DeferredRegister#register` 返回类型**：1.21.1 `DeferredHolder<T,T>` → 1.20.1 **`RegistryObject<T>`**（`DeferredRegister.java:175`）。
4. **交易事件**：载荷同形（`Int2ObjectMap<List<ItemListing>>`），但**总线与触发时机不同** —— 1.20.1 = `MinecraftForge.EVENT_BUS`（`VillagerTradesEvent.java:21`），由 `VillagerTradingManager#loadTrades(ServerAboutToStartEvent)` 触发（开服一次；副作用：**`/reload` 不重发**，已上架交易保留）；1.21.1 = NeoForge 总线 + `TagsUpdatedEvent`（数据包标签加载/`/reload` 时重发）。两线发报时物品标签都已绑定 ⇒ 排除判据口径一致。`ItemListing#getOffer(Entity, RandomSource)` 两线同签名。
5. **`BasicItemListing`**：包名从 `net.neoforged.neoforge.common` → **`net.minecraftforge.common`**（签名同 5 参）。
6. ⚠️ **1.20.1 的 `MerchantOffer#getBaseCostA()` 被既有「绿宝石骰子改价」mixin 覆写**（`MerchantOfferMixin` 把绿宝石费用替换成星币），而「戴骰子隐藏兑换条目」的判据**读的就是 getBaseCostA** ⇒ 直接读会把星币当原值、判据失效。对策：新增软接口 **`trade/MerchantOfferRawAccess`**（Mixin 接口注入 `MerchantOffer`，暴露 `astralDice$rawCostA()` 读原始 `baseCostA` 字段）+ `createTag()` 兜底；1.21.1 侧无此问题。
7. **`Merchant` 包名不同**：1.20.1 = `net.minecraft.world.item.trading.Merchant`（1.21.1 = `net.minecraft.world.entity.npc.Merchant`）⇒ `@Redirect` 描述符必须按本线写。
8. **无 `MerchantOffer` 复制构造（1.20.1）** ⇒ 村民交易报价用 5 参栈式构造 `new MerchantOffer(ItemStack costA, ItemStack result, int maxUses, int xp, float priceMultiplier)`；`getBaseCostA()` 返回 `ItemStack`（1.21.1 是 `ItemCost`）。
9. **标签目录**：1.20.1 物品标签是复数 `data/astral_dice/tags/items/`（1.21.1 `tags/item/`）；**POI 标签两线同为** `data/minecraft/tags/point_of_interest_type/`（已双线取证）。
10. **JEI**：1.20.1 = `jei-1.20.1-forge-15.56.0.205`（`modImplementation`），1.21.1 = `jei-1.21.1-neoforge-19.39.0.372`（`implementation`）⇒ 插件 API 需按 15 版重取（`IRecipeCategory`/`IRecipeRegistration`/`createDrawableIngredient` 等重载可能不同）。


### 十四之补充（2026-10-09 商店 GUI 细节修正）
11. 本次改动**不含任何新平台符号**（纯逻辑/常量/注释）⇒ 两线文本逐字相同；仅保留既有平台写法（`new ResourceLocation`、`ModNetwork.*`、`mouseScrolled` 三参、`getSkinLocation`）。
12. **素材**：`shop_wallet.png`（96×24，左格自带星币图标）四份副本（`images/shop/` 与两线 `gui/shop/`）sha256 全同；**两线均已删除** `shop_coin_bar.png` / `shop_coin_icon.png`。⚠️ forge 模块的 `build/resources/main/` 因 Gradle 增量特性可能残留旧副本 ⇒ 收尾需 `clean` 该模块重建（jar 内不得出现旧素材）。
