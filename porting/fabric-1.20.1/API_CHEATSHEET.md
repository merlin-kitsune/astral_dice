# Forge ↔ Fabric API 差异对照速查表（fabric-1.20.1 移植）

> 证据列 = 参考源在 `ref/` 内的真实路径（已克隆并逐条核验，2026-09-29）。
> 映射裁决 = **Mojang 官方映射** ⇒ 原版类名/成员名（net.minecraft.*）两平台**完全一致**，只有「加载器层」API 不同。

## 1. 加载器入口与元数据

| 主题 | Forge 1.20.1（现状） | Fabric 1.20.1 | 证据 |
|---|---|---|---|
| 入口 | `@Mod(MODID)` + 构造器；`@Mod.EventBusSubscriber` | `implements ModInitializer` → `onInitialize()`；`implements ClientModInitializer` → `onInitializeClient()` | `ref/fabric-loader/src/main/java/net/fabricmc/api/ModInitializer.java`、`.../ClientModInitializer.java` |
| 元数据 | `META-INF/mods.toml`（Groovy 模板展开占位符） | `fabric.mod.json`（processResources expand 属性；`entrypoints.main/client`、`depends`、`mixins`、`custom`） | `ref/fabric-loader/src/main/resources/`（fabric.mod.json 范式） |
| 依赖门槛 | `[[dependencies.<modid>]] modId/mandatory/versionRange` | `depends: {"fabricloader": ">=0.19.5", "minecraft": "~1.20.1", "fabric-api": "*", "trinkets": ">=3.7.2", "cardinal-components-base": ">=5.2.3", "starengine_lib": ">=1.0.6"}`；不可用 = 启动即拒 | 同上 |
| 内部事件总线基类 | `net.minecraftforge.eventbus.api.Event` | **无**（loader 无注解事件体系；用 FAPI 回调或自定义接口） | — |

## 2. 注册面（DeferredRegister → 直接注册）

| 主题 | Forge | Fabric | 备注 |
|---|---|---|---|
| 注册 API | `DeferredRegister<T>` + `register(IEventBus)` + `RegistryObject<T>` | `Registry.register(Registries.<X>, ResourceLocation, T)`；薄包装层 `ModRegistries` 保持「静态字段 + 入口统一注册」结构 | 包装层让 `ModItems`/`ModEffects` 等 9 类的声明面**零改名** |
| 创造栏 | `CreativeModeTab` + DeferredRegister + `displayItems(Consumer<ItemStack>)` | `FabricItemGroup.builder()` + `FabricItemGroupEntries.accept(...)` | `ref/fabric-api/fabric-item-group-api-v1/.../FabricItemGroup.java` |
| 菜单 | `MenuType`（DeferredRegister）| `ScreenHandlerRegistry.registerSimple(...)` / `registerExtended(...)` | FAPI `fabric-screen-handler-api-v1` |
| 配方序列化器 | `RecipeSerializer`（DeferredRegister）| 直接 `Registry.register(Registries.RECIPE_SERIALIZER, ...)` | 无变化，仅通道 |
| 实体属性注册 | `EntityAttributeRegistrationEvent` | `FabricDefaultAttributeRegistry.register(type, builder)` | `ref/fabric-api/fabric-object-builder-api-v1/.../FabricDefaultAttributeRegistry.java` |
| 命令注册 | `RegisterCommandsEvent` | `CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> ...)` | `ref/fabric-api/fabric-command-api-v2/.../CommandRegistrationCallback.java`（**用 v2**，v1 已 deprecated） |
| 效果注册 | DeferredRegister（45 个） | `Registry.register(Registries.MOB_EFFECT, ...)` | — |

## 3. 事件总线（125 @SubscribeEvent / ~40 种事件类型）

**总体策略：先查 FAPI 回调；无对应者按「自写 mixin（给注入点）/ 自定义回调接口」三选一并标注。**

| Forge 事件（典型） | Fabric 对应 | 证据（ref/fabric-api 内路径） |
|---|---|---|
| `ServerTickEvent` / `LevelTickEvent` | `ServerTickEvents.START_SERVER_TICK / END_SERVER_TICK`、`ServerTickEvents.START_WORLD_TICK / END_WORLD_TICK` | `fabric-lifecycle-events-v1/.../ServerTickEvents.java` |
| `LivingDamageEvent` / `LivingHurtEvent` | `ServerLivingEntityEvents.ALLOW_DAMAGE`（可取消） | `fabric-entity-events-v1/.../ServerLivingEntityEvents.java` |
| `LivingDeathEvent` | `ServerLivingEntityEvents.AFTER_DEATH` | 同上 |
| `PlayerEvent.Clone`（死亡重生复制 ×5） | `ServerPlayerEvents.COPY_FROM` | `fabric-entity-events-v1/.../ServerPlayerEvents.java` |
| 登录/登出/重生 | `ServerPlayConnectionEvents.JOIN / DISCONNECT`、`ServerPlayerEvents.AFTER_RESPAWN` | `fabric-networking-api-v1` / `fabric-entity-events-v1` |
| 实体加入世界 | `ServerEntityEvents.ENTITY_LOAD / ENTITY_UNLOAD` | `fabric-entity-events-v1` |
| `RegisterCommandsEvent` | `CommandRegistrationCallback`（v2） | 见 §2 |
| `LootTableLoadEvent` / GLM 战利品注入 | `LootTableEvents.MODIFY.register((key, tableBuilder, source) -> ...)` + `LootPool` | `fabric-loot-api-v2/.../LootTableEvents.java` |
| 玩家交互（右键物品/方块/实体） | `UseItemCallback`、`UseBlockCallback`、`UseEntityCallback` | `fabric-events-interaction-v0/...` |
| 渲染 HUD | `HudRenderCallback.EVENT.register((drawContext, tickDelta) -> ...)`（1.20.1 = `MatrixStack`） | `fabric-rendering-v1/src/client/.../HudRenderCallback.java` |
| 屏幕事件（按钮注入/afterInit） | `ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> ...)` + `Screens.getButtons(screen)` | `fabric-screen-api-v1/src/client/.../ScreenEvents.java` |
| tooltip 事件 | `ItemTooltipCallback.EVENT` | `fabric-item-api-v1/.../ItemTooltipCallback.java` |
| 其余 ~10~15 种（LivingKnockBack、ItemTooltipEvent 特殊参数、EntityJoinLevel 细节、部分 PlayerEvent 变体等） | **无 FAPI 对应** ⇒ 自写 mixin（注入点候选列于 PORT_ANALYSIS §4.2 事件对照表）或自定义接口 | — |

## 4. 持久化（Capability/Attachments → Cardinal Components）

| 主题 | Forge 1.20.1（现状） | Fabric（CCA 5.2.3） | 证据（ref/cardinal-components-api 内路径） |
|---|---|---|---|
| 注册容器 | `ModCapabilities.register(...)` + `AttachCapabilitiesEvent<Entity>`（108 键经 `AstralData/AttachedDataKey`） | `ComponentRegistryV3.INSTANCE.getOrCreate(ResourceLocation, Class)` 返回 `ComponentKey<T>`；`EntityComponentInitializer.register(Entity.class, key, factory)` | `cardinal-components-base/.../ComponentRegistryV3.java`、`cardinal-components-entity/.../EntityComponentInitializer.java` |
| 组件契约 | `INBTSerializable<CompoundTag>`（readNbt/writeNbt） | `ComponentV3`（`readFromNbt(NbtCompound)` / `writeToNbt(NbtCompound)`） | `cardinal-components-base/.../ComponentV3.java` |
| 同步 | 自定义包（SimpleChannel） | 需同步者实现 `sync.AutoSyncedComponent`（自动 S2C） | `cardinal-components-base/.../sync/AutoSyncedComponent.java` |
| 死亡重生保留 | `PlayerEvent.Clone` 手工复制 ×5 | 逐组件显式 `entity.RespawnCopyStrategy`（`INVENTORY / CHARACTER / ALWAYS_COPY / NEVER_COPY / LOST_AFTER_DEATH`） | `cardinal-components-entity/.../RespawnCopyStrategy.java` |
| 直接 NBT（getPersistentData ×4） | FML patch | CCA 组件或自挂 NBT（按域归属；钱包走库的 `FabricEconomyStorage`，见库方案） | — |

## 5. 网络（SimpleChannel → FAPI Networking）

| 主题 | Forge（SimpleChannel，12 消息） | Fabric（fabric-networking-api-v1） | 证据 |
|---|---|---|---|
| 通道注册 | `SimpleChannel.registerMessage(id, Class, encoder, decoder, handler)` | `ServerPlayNetworking.registerGlobalReceiver(Identifier channelName, PlayChannelHandler)`（C2S 收包）；`ServerPlayNetworking.createS2CPacket(...)` / `send(...)`（S2C 发包）；客户端 = `ClientPlayNetworking.registerGlobalReceiver(...)` | `fabric-networking-api-v1/.../ServerPlayNetworking.java`、`.../ClientPlayNetworking.java` |
| 序列化 | `FriendlyByteBuf` 手写 encode/decode | `PacketByteBufs.create()`（= FriendlyByteBuf）手写 | 同上 |
| ⚠️ 误区 | — | 1.20.1 **没有** `PayloadTypeRegistry`/原版 `CustomPayload`（那是 1.20.5+）⇒ 用上面的 channel-handler 形态；1.20.1 另有 `PacketType/PlayPacketHandler` 新式注册可用但不强制 | 实测 ref/fabric-api 内无 PayloadTypeRegistry.java |
| 握手/版本互通 | `registerDisplayTest` + SimpleChannel `PROTOCOL_VERSION` | 握手包内嵌协议版本常量比对（自定义；无 DisplayTest 等价物） | — |

## 6. 战利品（GLM → Loot API）

| 主题 | Forge | Fabric | 证据 |
|---|---|---|---|
| 注入机制 | Global Loot Modifier（`astral_dice:add_table` serializer + 13 JSON + LootInjectionHandler/FirstLootChestHandler） | `LootTableEvents.MODIFY`（或 `LootTableEvents.REPLACE`）+ `LootPool.builder()`；首箱赠礼 handler 改为在 MODIFY 里按表 key 判定后注入 | `fabric-loot-api-v2/.../LootTableEvents.java` |
| 概率/命名空间判别 | 自有命名空间早退判据（`getNamespace().equals(MODID)`） | 同上，在 MODIFY 回调里先判 key 命名空间 | — |

## 7. Mixin（18 个，Mojmap 零改名）

| 主题 | Forge 1.20.1 | Fabric 1.20.1 | 证据 |
|---|---|---|---|
| 配置注册 | `META-INF` + `mixin` block + refmap | `fabric.mod.json` 的 `"mixins": ["astral_dice.mixins.json"]` | loader 惯例 |
| refmap | MDG `mixin { config; add refmap }`（Mojmap→SRG） | loom `remapJar` 自动产 refmap（official→intermediary）；build.gradle 设 `mixin { defaultRefmapName = "astral_dice.refmap.json" }`；⚠️ **P0 必须先做最小 mixin 端到端验证**（解包查 refmap 嵌入 + 生产启动） | 本仓首次 loom 下 remap（高风险 R3） |
| 目标/方法名 | Mojmap | **Mojmap 同名**（裁决）⇒ 18 个 mixin 源码**零改名** | — |
| 兼容性声明 | `compatibilityLevel: "JAVA_17"` | 同上 | — |

## 8. 渲染/客户端

| 主题 | Forge | Fabric | 证据 |
|---|---|---|---|
| HUD | ForgeGui/IGuiOverlay | `HudRenderCallback`（1.20.1 参数 = `MatrixStack`） | `fabric-rendering-v1/...` |
| 屏幕注册 | `MenuScreens.register` | `HandledScreens.register(...)` | `fabric-screen-api-v1` |
| 粒子 | 自定义粒子类型 + ParticleEngine | `FabricParticleTypes` / `ParticleFactoryRegistry` | `fabric-particles-v1` |
| 声音 | 自定义 SoundEvent（DeferredRegister 12 个） | `Registry.register(Registries.SOUND_EVENT, ...)` | — |

## 9. 三方联动（替代对照）

| Forge 侧 | Fabric 侧 | 说明 |
|---|---|---|
| Curios 5.14.1（`top.theillusivec4.curios.api`） | **Trinkets 3.7.2**（`dev.emi.trinkets.api`） | 接口映射：`ICurioItem.curioTick/onEquip/onUnequip/canEquip/canUnequip` → `Trinket.tick/onEquip/onUnequip/canEquip/canUnequip`（签名 `(ItemStack, SlotReference, LivingEntity)`）；槽位定义由 IMC 改为数据包 `data/trinkets/slots/<group>/<slot>.json` + `group.json` + `data/trinkets/entities/astral_dice.json`；访问组件 = `TrinketsApi.getTrinketComponent(LivingEntity): Optional<TrinketComponent>` | 证据：`ref/trinkets/src/main/java/dev/emi/trinkets/api/Trinket.java`、`.../TrinketsApi.java`、`ref/trinkets/src/main/resources/data/trinkets/slots/**` |
| Patchouli 1.20.1-85-forge | Patchouli **1.20.1-85-FABRIC**（同 build 号变体） | 手册条目/书籍数据零改动（内容在资源目录）；仅运行时依赖与入口变体 |
| KubeJS/Rhino/Architectury | 同版本号 fabric 变体 | 测试探针运行时 |
| JEI/Jade/ModernFix/FerriteCore | 同版本号 fabric 变体 | — |
| Embeddium/Oculus | **Sodium 0.5.13 / Iris 1.7.6**（仅运行环境） | 代码零联动 |
| neoforge-carpet | **fabric-carpet 1.4.112** | 测试 bot |
| Iron's Spells / Bountiful | **裁剪**（无 Fabric 1.20.1 版） | 联动整段删除 |

> 全部证据路径均指向 worktree 内 `ref/`（已克隆）。事件/回调的完整「逐事件」对照（约 40 种）在 `PORT_ANALYSIS.md` §4.2。
