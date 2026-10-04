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
