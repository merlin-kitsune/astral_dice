# B0-CONTENT 移植报告：方块 + 实体 + 客户端渲染器（fabric-1.20.1）

> 批次：B0-CONTENT　基准：forge-1.20.1（次）/ neoforge-1.21.1（主，仅语义参照）
> 迁移线：fabric-1.20.1（Fabric API 0.92.12 / officialMojangMappings / Java 17 / Loom 1.14.10）
> 报告日期：2026-11-01

## 0. 结论摘要

- 方块 **4 个**（card_shop / chip_shop / event_drawer / barricade_collision）与 BlockItem **3 个**（前者三个；barricade_collision 无 BlockItem，与 forge 一致）已落线；创造栏归属与 forge 同段（本栏最末）。
- 实体 **2 个**（barricade / repulsion）+ 属性表（30.0D / 50.0D）+ 客户端渲染器 **2 个**已落线；渲染器与模型层用 Fabric 命令式注册（EntityRendererRegistry / EntityModelLayerRegistry），入口 client/EntityRenderers#bootstrapClient()。
- **编译通过**（BUILD SUCCESSFUL），block/entity/client 三目录 net.minecraftforge|net.neoforged **0 命中**，16 个 Java 文件**全部 CRLF**。
- **未接线（按约定留给 Lead）**：AstralDiceMod#commitRegistrations() 需加 BLOCKS 与 ENTITY_TYPES 两行、onInitialize() 需调 ModEntityEvents.bootstrap()、AstralDiceClient#onInitializeClient() 需调 EntityRenderers.bootstrapClient()。**BLOCKS 必须排在 ITEMS 之前**，否则 BlockItem 供应商在 commit 时读未绑定 RegistryObject ⇒ 启动崩（§7）。
- **待接线 seam**（fabric 线缺对应模块，非本批范围）：shop.*（3 个方块的开店 / 玩家状态）、ModItems.EVENT_TICKET、ModAttachments 的 EventDrawer 四件套、item/RepulsionItem、item/sign/LinglingSignManager。逐条见 §6.2，全部收敛为单点方法，落线后只改方法体。

## 1. 交付文件（16 个 Java 文件，全部在 fabric-1.20.1 线内）

路径均相对 fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/。

| # | 文件 | 行数 | forge 对应行数 | 来源 |
|---|---|---|---|---|
| 1 | block/ModBlocks.java | 92 | 64 | 新建，1:1 照 forge（4 条注册 + bootstrap() 静态入口） |
| 2 | block/CardShopBlock.java | 90 | 80 | 新建，1:1 + 1 处 seam |
| 3 | block/ChipShopBlock.java | 167 | 141 | 新建，1:1 + 4 处 seam |
| 4 | block/EventDrawBlock.java | 165 | 136 | 新建，1:1 + 5 处 seam |
| 5 | block/BarricadeBlock.java | 103 | 111 | 新建，1:1（删 Forge 私有重载，§6.1） |
| 6 | entity/ModEntityTypes.java | 66 | 67 | 新建，1:1（注册器换 shim） |
| 7 | entity/BarricadeEntity.java | 457 | 457 | 新建，**逐行等价** |
| 8 | entity/RepulsionEntity.java | 1199 | 1141 | 新建，逻辑逐行等价 + 尾部 seam 垫片（L1145-1199） |
| 9 | event/ModEntityEvents.java | 55 | 49 | 新建，forge 事件 → FabricDefaultAttributeRegistry |
| 10 | client/BarricadeModel.java | 97 | 97 | 新建，**零平台依赖**，仅改注释 |
| 11 | client/BarricadeRenderer.java | 47 | 47 | 新建，同上 |
| 12 | client/RepulsionModel.java | 147 | 147 | 新建，同上 |
| 13 | client/RepulsionRenderer.java | 48 | 48 | 新建，同上 |
| 14 | client/EntityRenderers.java | 47 | —（forge 无此类） | 新建，Fabric 专用静态入口 bootstrapClient() |
| 15 | item/ModItems.java | 1055 | —（改既有文件） | **改**：L9 加 import net.minecraft.world.item.BlockItem；L1031-1045 加 3 个 BlockItem |
| 16 | init/ModCreativeTabs.java | 196 | —（改既有文件） | **改**：L188-194 加 3 行创造栏 output.accept |

AstralDiceMod.java / AstralDiceClient.java **未改动**（硬约束 C）。

## 2. 验收 1：编译

命令（仓库根，硬约束 G 原文）：

    cmd.exe //c "gradlew.bat :fabric-1.20.1:classes --console=plain --project-cache-dir=build/_ilcache -I build/_ilinit.gradle"

原始输出（末次，含 seam 对齐后的重编）：

    Reusing configuration cache.
    > Task :fabric-1.20.1:processResources
    > Task :fabric-1.20.1:compileJava
    > Task :fabric-1.20.1:classes
    [Incubating] Problems report is available at: file:///F:/MCProject/astral_dice_multiloader/build/reports/problems/problems-report.html
    BUILD SUCCESSFUL in 1s
    2 actionable tasks: 2 executed
    Configuration cache entry reused.

stderr 仅既有告警，无「找不到符号 / 不兼容的类型」：

    注: 某些输入文件使用或覆盖了已过时的 API。
    注: 有关详细信息, 请使用 -Xlint:deprecation 重新编译。

## 3. 验收 2：禁止 forge 类型（三目录 0 命中）

模式 net\.minecraftforge|net\.neoforged，逐目录 grep：

| 目录 | 命中数 |
|---|---|
| fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/block | **0** |
| fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/entity | **0** |
| fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/client | **0** |

## 4. 验收 3：CRLF

逐文件实测（LF 计数 == CRLF 计数 == CR 计数 ⇒ 无裸 LF、无 \r\r）：

| 文件 | LF | CRLF | CR | 判定 |
|---|---|---|---|---|
| block/ModBlocks.java | 92 | 92 | 92 | OK |
| block/CardShopBlock.java | 90 | 90 | 90 | OK |
| block/ChipShopBlock.java | 167 | 167 | 167 | OK |
| block/EventDrawBlock.java | 165 | 165 | 165 | OK |
| block/BarricadeBlock.java | 103 | 103 | 103 | OK |
| entity/ModEntityTypes.java | 66 | 66 | 66 | OK |
| entity/BarricadeEntity.java | 457 | 457 | 457 | OK |
| entity/RepulsionEntity.java | 1199 | 1199 | 1199 | OK |
| event/ModEntityEvents.java | 55 | 55 | 55 | OK |
| client/BarricadeModel.java | 97 | 97 | 97 | OK |
| client/BarricadeRenderer.java | 47 | 47 | 47 | OK |
| client/RepulsionModel.java | 147 | 147 | 147 | OK |
| client/RepulsionRenderer.java | 48 | 48 | 48 | OK |
| client/EntityRenderers.java | 47 | 47 | 47 | OK |
| item/ModItems.java | 1055 | 1055 | 1055 | OK |
| init/ModCreativeTabs.java | 196 | 196 | 196 | OK |

## 5. 验收 4：与 forge 的逐条对照

### 5.1 方块注册（fabric block/ModBlocks.java vs forge block/ModBlocks.java）

| 注册 id | 类 | 属性（逐条） | forge:行 | fabric:行 |
|---|---|---|---|---|
| card_shop | CardShopBlock | of().mapColor(WOOD).strength(2.5F).sound(WOOD).noOcclusion() | 25-31 | 45-50 |
| chip_shop | ChipShopBlock | 同上 | 34-40 | 53-58 |
| event_drawer | EventDrawBlock | of().mapColor(METAL).strength(2.5F).sound(METAL)（**无** noOcclusion） | 43-47 | 61-65 |
| barricade_collision | BarricadeBlock | of().mapColor(WOOD).strength(-1.0F, 3600000.0F).sound(WOOD).noOcclusion().noLootTable().isRedstoneConductor((s,l,p)->false).isSuffocating((s,l,p)->false).isViewBlocking((s,l,p)->false) | 54-63 | 71-80 |

注册顺序一致（card_shop → chip_shop → event_drawer → barricade_collision）。容器差异仅 DeferredRegister.create(ForgeRegistries.BLOCKS,…) → DeferredRegister.create(Registries.BLOCK,…)（本仓 fabric shim 的 create(ResourceKey,String)，见 platform/registry/DeferredRegister.java）。

### 5.2 BlockItem（fabric item/ModItems.java L1031-1045 vs forge item/ModItems.java L444-462）

| 项 | fabric | forge |
|---|---|---|
| CARD_SHOP | registerItem("card_shop", () -> new BlockItem(ModBlocks.CARD_SHOP.get(), new Item.Properties().stacksTo(1)))（L1036-1037） | 同（L447-448） |
| CHIP_SHOP | 同构（L1040-1041） | 同（L451-452） |
| EVENT_DRAWER | 同构（L1044-1045） | 同（L461-462） |
| BARRICADE_COLLISION | **不注册 BlockItem** | 同（无） |

1.20.1 两侧都无原版 registerSimpleBlockItem（forge 那个是自己写的 helper 且不接受属性），故三块一律走 registerItem + new BlockItem(…stacksTo(1))；可堆叠数 = 1，与 forge 一致。

### 5.3 创造栏（fabric init/ModCreativeTabs.java L188-194 vs forge L224-230）

- 位置：**本创造栏最末**（PIERCING_GUN 之后、}).build() 之前），与 forge 同段。
- fabric 加 3 行：EVENT_DRAWER / CARD_SHOP / CHIP_SHOP（L192-194）。
- **少 1 行**：forge 另有 output.accept(ModItems.EVENT_TICKET.get())（forge L227），fabric 线**没有 event_ticket 物品**（属 B0-ITEMS），故缺；代码内已注释说明。

### 5.4 实体注册（fabric entity/ModEntityTypes.java vs forge 同路径）

| 项 | fabric | forge |
|---|---|---|
| barricade | EntityType.Builder.of(BarricadeEntity::new, MobCategory.MISC).sized(1.2F, 2.0F).clientTrackingRange(8).build("barricade")（L42-47） | 逐字同（L39-44） |
| repulsion | EntityType.Builder.of(RepulsionEntity::new, MobCategory.MISC).sized(1.6F, 2.4F).clientTrackingRange(10).build("repulsion")（L57-62） | 逐字同（L58-63） |
| 注册器 | DeferredRegister.create(Registries.ENTITY_TYPE, MODID)（L38-39） | DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID) |
| 注册名 | build(String) 传 "barricade" / "repulsion"（1.20.1 两侧同形） | 同 |

### 5.5 属性表（fabric event/ModEntityEvents.java vs forge event/ModEntityEvents.java:34-48）

| 实体 | 最大生命 | forge 证据 | fabric 证据 |
|---|---|---|---|
| barricade | **30.0D** | forge add(Attributes.MAX_HEALTH, 30.0D) | event/ModEntityEvents.java:42-45 |
| repulsion | **50.0D** | forge 同文件 50.0D（注释「2026-10-21 总量由 100 调整为 50」） | event/ModEntityEvents.java:50-53 |

两者都先 LivingEntity.createLivingAttributes() 再覆写 MAX_HEALTH（与 forge 同口径；移动速度 / 护甲 / 击退抗性沿用默认）。平台替代：forge EntityAttributeCreationEvent#put → Fabric FabricDefaultAttributeRegistry.register(EntityType<? extends LivingEntity>, AttributeSupplier.Builder)（javap 复核通过）。

### 5.6 客户端渲染器（fabric client/* vs forge client/*）

| 文件 | 差异 |
|---|---|
| BarricadeModel.java | **仅 Javadoc 一行不同**（补「2026-11-01 移植到 fabric-1.20.1」）；几何全部逐字：LayerDefinition.create(mesh, 64, 64)、ModelLayerLocation(new ResourceLocation(MODID,"barricade"), "main")、TILT = Math.toRadians(22.5)、两块板 addBox(-10.01F|-10F,-14F,0F,20F,14F,1F)（texOffs(0,0)/(0,15)）、PartPose.offsetAndRotation(0,24,5,TILT,0,0) / (0,23.6,-6,-TILT,0,0) |
| BarricadeRenderer.java | 仅 Javadoc 一行；LivingEntityRenderer 三参、0.5F 阴影、TEXTURE = new ResourceLocation(MODID,"textures/entity/barricade.png")、shouldShowName(...) = false |
| RepulsionModel.java | 仅 Javadoc 一行；BLADE_TWIST=toRadians(90)、HEAD_TWIST=toRadians(45)、blade addBox(0,-2,0,24,2,1) 枢轴 (1,24,-0.5) 绕 Z -BLADE_TWIST、head addBox(-12,-12,0,12,12,2) 枢轴 (0,1.5,-1) 绕 Z HEAD_TWIST |
| RepulsionRenderer.java | 仅 Javadoc 一行；0.6F 阴影、textures/entity/repulsion.png、shouldShowName(...) = false |

结论：forge 这 4 个文件本来就**零 forge import**（只用原版 API + AstralDiceMod.MODID），且两线同为 1.20.1 同版本 ⇒ 机械移植即 1:1，无 API 改写。

### 5.7 实体 / 方块类的逐行 diff（机械核对，忽略注释行）

| 文件 | fabric/forge 行数 | 仅存于一侧的非注释行 |
|---|---|---|
| entity/BarricadeEntity.java | 457 / 457 | fabric: import …astral_dice.combat.EliteTargets；forge: import …starengine.combat.EliteTargets —— **逻辑零差异** |
| entity/RepulsionEntity.java | 1199 / 1141 | fabric 多 58 行，全部是尾部 seam 垫片（L1145-1199）+ 同名常量 / 方法转发；@javax.annotation.Nullable → @Nullable（org.jetbrains） |
| block/BarricadeBlock.java | 103 / 111 | fabric 删了 getBlockPathType 覆写与 2 个 import（见 §6.1） |
| client/*.java（4 个） | 同 / 同 | 只有 Javadoc 一行 |

## 6. fabric 等价实现与行为差异

### 6.1 平台 API 替换表

| forge 用法 | fabric 等价实现 | 证据 |
|---|---|---|
| DeferredRegister.create(ForgeRegistries.BLOCKS / ENTITY_TYPES, MODID) | DeferredRegister.create(Registries.BLOCK / Registries.ENTITY_TYPE, MODID)（本仓 shim） | platform/registry/DeferredRegister.java 的 create(ResourceKey,String)；fabric block/ModBlocks.java:41-42、entity/ModEntityTypes.java:38-39 |
| @Mod 构造器 .register(modEventBus) | commit()（等价 register(IEventBus)），在 AstralDiceMod#commitRegistrations() 命令式调用 | platform/registry/DeferredRegister.java；§7 |
| EntityAttributeCreationEvent | FabricDefaultAttributeRegistry.register(type, builder) | event/ModEntityEvents.java:42,50；javap 复核：public static void register(EntityType<? extends LivingEntity>, AttributeSupplier$Builder) |
| EntityRenderersEvent.RegisterRenderers | EntityRendererRegistry.register(EntityType, EntityRendererProvider) | client/EntityRenderers.java；javap：public static <E extends Entity> void register(EntityType<? extends E>, EntityRendererProvider<E>) |
| EntityRenderersEvent.RegisterLayerDefinitions | EntityModelLayerRegistry.registerModelLayer(ModelLayerLocation, TexturedModelDataProvider) | 同上；嵌套接口 TexturedModelDataProvider { TexturedModelData createModelData(); } ⇒ XModel::createBodyLayer 直接作 lambda（LayerDefinition extends TexturedModelData） |
| Forge 私有 BlockBehaviour#getBlockPathType(BlockState, BlockGetter, BlockPos, Mob) | **原版 1.20.1 无此方法**（Forge 自己 patch 进 BlockBehaviour）⇒ fabric 侧只保留 isPathfindable(...) = true；原版 WalkNodeEvaluator 只认 isPathfindable 一处，等价达成「寻路可走、物理仍挡」 | fabric block/BarricadeBlock.java L60-76 说明块；实证：保留该覆写时 :fabric-1.20.1:compileJava 报「方法不会覆盖或实现超类型的方法」 |
| com.merlinkitsune.starengine.combat.EliteTargets（forge 用前置库） | com.merlinkitsune.astral_dice.combat.EliteTargets（**fabric 线既有唯一入口**，已被 combat/ShootingStarManager.java:382、combat/OrbitalBombardmentManager.java:289 使用） | combat/EliteTargets.java:53-69；语义差见 §6.3 |
| Forge @javax.annotation.Nullable | org.jetbrains.annotations.Nullable（fabric 线无 javax.annotation；本线已有 9 处同类用法） | entity/RepulsionEntity.java:4,424 |

### 6.2 待接线 seam 清单（fabric 线缺模块，本批按「单点出口 + TODO 标记」落地，**不伪造调用**）

| fabric 落点 | 缺的符号（forge 出处） | 落线后的改法（一行） |
|---|---|---|
| block/CardShopBlock.java:86 openShop(Player) | shop.CardShopOpener#open(Player)（forge 同路径 use()；整个 shop/ 包 11 类未移植） | 方法体换成 com.merlinkitsune.astral_dice.shop.CardShopOpener.open(player); |
| block/ChipShopBlock.java:135 openShop(Player) | shop.ChipShopOpener#open(Player) | com.merlinkitsune.astral_dice.shop.ChipShopOpener.open(player); |
| block/ChipShopBlock.java:146 lastBlankInsertAt(Player) | shop.ChipShopState.get(player).lastBlankInsertAt()（forge L109-110） | 直传 |
| block/ChipShopBlock.java:155 refreshesLeft(Player) | ChipShopState.get(player).refreshesLeft()（forge L121） | 直传 |
| block/ChipShopBlock.java:165 storeBlankInsert(Player,long) | ChipShopState.store(player, shopState.withBlankInsert(now, shopState.refreshesLeft()+1, 0L))（forge L118） | 直传 |
| block/EventDrawBlock.java:145 giveEventTicket(Player) | ModItems.EVENT_TICKET（forge item/ModItems.java:455） | ItemStack t = new ItemStack(ModItems.EVENT_TICKET.get()); if (!player.getInventory().add(t)) player.drop(t, false); |
| block/EventDrawBlock.java:149 / 154 / 158 / 163 | ModAttachments.get/setEventDrawerCooldownUntil + get/setEventDrawerBlankInsertAt（forge L77/85/86/95/117） | 4 个方法体直传同名附件访问器 |
| entity/RepulsionEntity.java:1174 hasOwnerSign(Player) | item.sign.LinglingSignManager#hasSign(Player)（forge L504） | return LinglingSignManager.hasSign(player); |
| entity/RepulsionEntity.java:1183 itemDurability(ItemStack) | item.RepulsionItem#getDurability(ItemStack) | 直传 |
| entity/RepulsionEntity.java:1195 createRecoveredStack(...) | item.RepulsionItem#createRecovered(float,long,int,UUID,String) | 直传；**当前返回 ItemStack.EMPTY** ⇒ 回收不掉物品（不会产生首 tick 自毁的 ItemEntity） |
| entity/RepulsionEntity.java:1155 REPULSION_MAX_DURABILITY | item.RepulsionItem#MAX_DURABILITY（= 50，2026-10-21 由 100 调整） | 改成常量引用 |

**seam 的行为后果（Lead 必须知道，属可观测差异）**：

1. 三个商店方块右键**暂无界面**（只落日志 CARD_SHOP_OPEN_PENDING / CHIP_SHOP_OPEN_PENDING）；
2. 抽奖盒右键会**扣 5 星币但不发券**（giveEventTicket 空实现）⇒ B0-ITEMS 落线前不要把 event_drawer 当可用内容；
3. 抽奖盒冷却 / 塞入状态恒 0 ⇒ 无冷却、可无限连抽（同上）；
4. 筹码商店塞入空白筹码**会消耗筹码但状态不落盘**（storeBlankInsert 空实现）；
5. 强化拒止「回收成物品」不掉物品；立牌依附判据恒 false。

### 6.3 与任务书文字 / 与 neo 基准的不一致处（以源码 + 用户后续裁决为准）

| 任务书表述 | 源码事实 | 本批处理 |
|---|---|---|
| 「EventDrawerBlock」 | forge 实际类名 **EventDrawBlock**（forge block/EventDrawBlock.java:36） | 按实际类名落地 block/EventDrawBlock.java |
| 「RepulsionEntity 的 8 格吸附范围」 | AFFECT_RADIUS = 12.0D、CLAMP_RADIUS = 11.5D、FORGET_RADIUS = 32.0D（forge entity/RepulsionEntity.java 常量区） | 按 12.0D 落地 |
| 「RepulsionEntity 的 100 耐久」 | 2026-10-21 起总量 100 → **50**（forge event/ModEntityEvents.java 注释 + RepulsionEntity 注释）；MAX_DURABILITY_COST = 5、每 200 tick 结算 | 按 50.0D 落地 |
| 「两者都 extends LivingEntity（不是 Mob）」 | 两侧 BarricadeEntity / RepulsionEntity 均 extends LivingEntity，已核 | 一致 |
| 属性数值 30 / 50 | 实测一致 | 一致 |

另：fabric 用本地 combat.EliteTargets 替代前置库版本，唯一语义差 = **去掉 Apotheosis 的 apoth.boss / apoth.miniboss NBT 判据**（getPersistentData() 是 Forge 补丁，fabric 1.20.1 无 Apotheosis）。影响面：只有「神化自造的 Invader / miniboss」不再被路障判为精英；阈值判据与 Boss 判据不受影响。属既有裁剪，本批未新增差异。

## 7. Lead 接线（建议调用位置，可直接抄）

三处都在既有文件里各加 1-3 行；本批按硬约束 C 未触碰那两个文件。

### 7.1 AstralDiceMod#commitRegistrations()（fabric AstralDiceMod.java L107-116）

**第一行**必须是方块注册（否则 BlockItem 供应商 commit 时 ModBlocks.X.get() 抛 IllegalStateException）：

        // ⚠️ 必须排在 ITEMS 之前：BlockItem 的供应商在 commit 阶段才求值
        com.merlinkitsune.astral_dice.block.ModBlocks.BLOCKS.register(BUS);
        com.merlinkitsune.astral_dice.item.ModItems.ITEMS.register(BUS);   // ← 既有行

同时在既有列表里加一行（相对其它注册器顺序不敏感）：

        com.merlinkitsune.astral_dice.entity.ModEntityTypes.ENTITY_TYPES.register(BUS);

### 7.2 AstralDiceMod#onInitialize()：commitRegistrations() 之后调用一次

        com.merlinkitsune.astral_dice.event.ModEntityEvents.bootstrap();

（必须在 ENTITY_TYPES 提交之后；同一 EntityType 重复 register 会崩。）

### 7.3 AstralDiceClient#onInitializeClient() 末尾

        com.merlinkitsune.astral_dice.client.EntityRenderers.bootstrapClient();

（**只能一次**：EntityModelLayerRegistry.registerModelLayer 内部 Map.putIfAbsent，冲突抛 IllegalArgumentException("Cannot replace registration for entity model layer …")。）

## 8. 验收 5：素材清单（本批**未拷贝任何二进制**，按硬约束 I 交由 Lead）

来源一律取 **forge-1.20.1**（1.20.1 目录名是复数形式：loot_tables/ 、recipes/ ）。**不要取 neoforge-1.21.1**：neo 线目录已改为单数 loot_table/ 、recipe/ ，路径与 1.20.1 不兼容。
目标根：fabric-1.20.1/src/main/resources/ 。fabric 侧实测：assets/astral_dice 下**无 blockstates/、无 models/block/、无 textures/block/、无 textures/entity/**（models/item 只有 astral_guide.json）；data/astral_dice/loot_tables 只有 chests/ 与 entities/（无 blocks/）；recipes 只有 14 个既有文件。

### 8.1 方块状态（4，JSON）

    assets/astral_dice/blockstates/card_shop.json          ← forge-1.20.1/src/main/resources/assets/astral_dice/blockstates/card_shop.json
    assets/astral_dice/blockstates/chip_shop.json          ← forge 同路径
    assets/astral_dice/blockstates/event_drawer.json       ← forge 同路径
    assets/astral_dice/blockstates/barricade_collision.json← forge 同路径

### 8.2 方块模型（4，JSON）

    assets/astral_dice/models/block/card_shop.json           ← forge 同路径
    assets/astral_dice/models/block/chip_shop.json           ← forge 同路径
    assets/astral_dice/models/block/event_drawer.json        ← forge 同路径
    assets/astral_dice/models/block/barricade_collision.json ← forge 同路径

### 8.3 物品模型（3，JSON；barricade_collision 无 BlockItem ⇒ 无物品模型）

    assets/astral_dice/models/item/card_shop.json    ← forge 同路径
    assets/astral_dice/models/item/chip_shop.json    ← forge 同路径
    assets/astral_dice/models/item/event_drawer.json ← forge 同路径

### 8.4 方块贴图（5，PNG）

    assets/astral_dice/textures/block/card_shop.png          ← forge 同路径
    assets/astral_dice/textures/block/card_shop_particle.png ← forge 同路径（noOcclusion 的粒子贴图）
    assets/astral_dice/textures/block/chip_shop.png          ← forge 同路径
    assets/astral_dice/textures/block/chip_shop_particle.png ← forge 同路径
    assets/astral_dice/textures/block/event_drawer.png       ← forge 同路径

### 8.5 实体贴图（2，PNG；渲染器硬依赖，缺了渲染器在客户端 new 时会崩 ResourceLocation 加载）

    assets/astral_dice/textures/entity/barricade.png ← forge 同路径
    assets/astral_dice/textures/entity/repulsion.png ← forge 同路径

### 8.6 掉落表（3，JSON；barricade_collision 用 noLootTable()，两侧都无掉落表文件）

    data/astral_dice/loot_tables/blocks/card_shop.json    ← forge 同路径
    data/astral_dice/loot_tables/blocks/chip_shop.json    ← forge 同路径
    data/astral_dice/loot_tables/blocks/event_drawer.json ← forge 同路径

内容实测（三份同构）：type = minecraft:block，pools[0].rolls = 1、bonus_rolls = 0、entries[0] = { type: minecraft:item, name: astral_dice:<自己> }，条件含 survives_explosion。**无 silk_touch 分支、无掉落数量变体** ⇒ 覆盖自 drop 即可，无需 Block 类内的 getDrops 覆写（两侧 Block 类也确实没有）。

### 8.7 配方（3，JSON；**依赖 B0-ITEMS**，请 Lead 判断是否与本批同批下发）

    data/astral_dice/recipes/card_shop.json    ← forge 同路径
    data/astral_dice/recipes/chip_shop.json    ← forge 同路径
    data/astral_dice/recipes/event_drawer.json ← forge 同路径

引用材料（实测）：card_shop = minecraft:smooth_stone_slab + astral_dice:star_coin；chip_shop = minecraft:gold_ingot + minecraft:polished_blackstone_bricks + astral_dice:star_plate + astral_dice:blank_chip；event_drawer = astral_dice:star_plate + astral_dice:star_coin + astral_dice:diamond_dice。**这四个 astral_dice 物品 fabric 线当前不存在** ⇒ 先于 B0-ITEMS 下发会得到「配方加载失败 / Unknown item」，方块可注册但不可合成。建议与 B0-ITEMS 同批。

### 8.8 语言键（14 个，三语 all MISSING，实测 fabric en_us/zh_cn/ja_jp 均 846 键、14 个全缺）

方块/实体名（6，值直接取 forge 同语种同键）：

    block.astral_dice.card_shop            en_us Card Shop           zh_cn 卡牌商店      ja_jp カードショップ
    block.astral_dice.chip_shop            en_us Chip Shop           zh_cn 筹码商店      ja_jp チップショップ
    block.astral_dice.event_drawer         en_us Lottery Box         zh_cn 抽奖盒        ja_jp 抽選ボックス
    block.astral_dice.barricade_collision  en_us Barricade (Collision) zh_cn 路障（隐形碰撞方块） ja_jp バリケード（当たり判定）
    entity.astral_dice.barricade           en_us Barricade           zh_cn 路障          ja_jp バリケード
    entity.astral_dice.repulsion           en_us Reinforced Repulsion zh_cn 强化拒止     ja_jp 強化拒止

（BlockItem 显示名自动走 block.* 键，**不需要** item.astral_dice.card_shop 等键。）

代码内已引用、但 fabric 缺席的 8 个提示键（三语 values 均在 forge 同语种文件里现成）：

    msg.astral_dice.event_drawer_no_coin          含占位 %s（星币不足（需要 %s））
    msg.astral_dice.event_drawer_cooldown
    msg.astral_dice.event_drawer_blank_insert
    msg.astral_dice.chip_shop_blank_cooldown
    msg.astral_dice.chip_shop_blank_insert
    msg.astral_dice.repulsion_not_owner
    msg.astral_dice.repulsion_hit                 含占位 %s
    msg.astral_dice.repulsion_recovered           含占位 %s / %s

### 8.9 本批不包含（列在此处仅为划界）

- 手册页 assets/astral_dice/patchouli_books/astral_guide/{en_us,zh_cn}/entries/blocks/{card_shop,chip_shop,event_drawer}.json ×3（属手册 / 文本批次；fabric 侧 patchouli_books/ 目录**已存在**）。
- 商店 GUI 贴图 textures/gui/shop/** 与 textures/gui/chip_shop/**（属 shop UI 批次）。
- 效果牌 / 强化拒止物品贴图 textures/item/{effect_card_barricade,effect_card_repulsion,repulsion}.png（属 B0-ITEMS）。
- Jade 插件键 jade.astral_dice.event_drawer_cost 与 config.jade.plugin_astral_dice.event_drawer_cost（fabric 线无 Jade 集成，见 §9）。

## 9. 无法判定 / 未验证项（硬约束 K：不猜）

| # | 事项 | 原因 | 现状 |
|---|---|---|---|
| 1 | 方块右键、实体 tick、渲染三角面、贴图加载的**运行时**行为 | 硬约束 H 禁止启动游戏，本批只做静态 1:1 + 编译 | 无法判定（静态逐行比对已做，见 §5） |
| 2 | seam 五处（商店界面 / 发券 / 冷却落盘 / 回收物品 / 立牌依附）的真实行为 | fabric 侧对应模块未移植，方法体为空实现 | 无法判定；§6.2 已给落线后的一行改法与当前可观测后果 |
| 3 | Apotheosis Invader/miniboss 是否仍会被路障判为精英 | fabric 线无 Apotheosis，本地 EliteTargets 无 apoth.* NBT 判据 | 无法判定「是否有玩家装了神化」这一前提；按现线裁剪已对齐 |
| 4 | 创造栏三行在本栏的实际落位（顺序 / 图标） | 需启动客户端看 GUI | 无法判定；仅静态核对到「PIERCING_GUN 之后、build() 之前」 |
| 5 | 是否需要 Jade 键 | fabric 线无 Jade 集成模块 | 无法判定（除非后续引入 Jade 兼容批次） |
| 6 | barricade / repulsion 的客户端模型与 forge 在游戏内是否一模一样 | 需实机对比截图 | 无法判定（源码级逐行等价已证，§5.6） |
| 7 | 1:1 的「neo 语义优先」部分 | 本批类目在 neo 线同名同构；neo 已是 1.21.1，实体/方块 API 差异不影响本批逻辑 | 已核：三线该批源码逻辑一致（除 §6.3 记录项），无未决差异 |

## 10. 收尾复验（报告成稿后重跑一次，原始输出）

编译（同硬约束 G 命令，源码未再改动 ⇒ UP-TO-DATE，佐证上一轮已成功）：

    Reusing configuration cache.
    > Task :fabric-1.20.1:processResources UP-TO-DATE
    > Task :fabric-1.20.1:compileJava UP-TO-DATE
    > Task :fabric-1.20.1:classes UP-TO-DATE
    BUILD SUCCESSFUL in 504ms
    2 actionable tasks: 2 up-to-date

对 block/ + entity/ + client/ **整目录递归**（不是只看本批 16 个文件）重跑禁用类型扫描与行尾扫描：

    scanned files: 34 | forge/neoforge hits: 0
    CRLF violations: none

34 = block/ 5 + entity/ 3 + client/ 26（client/ 含既有 22 个文件，一并复核无 net.minecraftforge / net.neoforged、行尾全 CRLF）。
