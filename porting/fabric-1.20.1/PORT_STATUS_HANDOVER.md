# fabric-1.20.1 移植执行状态与交接（2026-09-29）

> 执行者：本次会话（自主决策轮）。基线：`1.20.1-fabric` 分支 / 工作树 `F:\MCProject\astral_dice_multiloader_fabric`。
> 前置库工作树：`F:\MCProject\starengine_lib_fabric`（分支 `fabric-1.20.1`，基线 `main @ 1.0.5`）。
> 配套件：`SKILL.md`（执行手册）、`PORT_ANALYSIS.md`、`VERSION_PINS.md`、`API_CHEATSHEET.md`。
> **本文状态：移植未完成。** 已完成的部分均带可复现证据；未完成部分给出依赖顺序与已就位的输入。

---

## 一、结论速览

| 项 | 状态 |
|---|---|
| 分支 / 工作树 | ✅ `1.20.1-fabric` + `astral_dice_multiloader_fabric` |
| `ref/` 参考源（7 仓） | ✅ 已克隆 |
| P0 工具链（loom 1.14.10 × Gradle 9.2.1） | ✅ **实测通过**（`temp/loomprobe` 探针 `BUILD SUCCESSFUL`） |
| 建议替换面证据化 | ✅ 971 个 Forge 独有成员 / 15 个确定性缺失符号 / 138 条签名漂移 |
| `Rarity` 5 档枚举扩展可行性 | ✅ **实测 PASS**（JDK 17 + 21，见 `temp/unsafeprobe`） |
| 前置库 `starengine_lib-fabric-1.20.1` | ✅ **构建成功 + 已 publishToMavenLocal（1.0.6）** |
| 消费方 `fabric-1.20.1` 子项目骨架 | ✅ 构建系统就位，已进入正常编译阶段 |
| 消费方源码移植（254 文件） | ❌ **未开始替换**（javac 首批 101 错全部是加载器层替换面） |
| 冒烟测试 | ❌ 未执行 |

---

## 二、已完成并验证的部分

### 2.1 P0：工具链与映射（风险 R2/R3 部分解除）

- `fabric-loom 1.14.10` + Gradle wrapper `9.2.1` **可用**；`JAVA_HOME` 默认已是 Zulu 21（Loom 运行 JVM 要求满足），编译由 toolchain 17 完成。
- 原版 Mojmap jar 已落盘：`~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/1.20.1-loom.mappings.1_20_1.layered+hash.2198-v2/*.jar`
- Forge 补丁合并 jar：`F:\MCProject\starengine_lib\forge-1.20.1\build\moddev\artifacts\forge-1.20.1-47.4.10-merged.jar`
- ⚠️ **尚未验证 remapJar 是否产出 refmap**（Loom 1.14 起 Mixin AP 默认关闭）⇒ P0 剩余项。

### 2.2 替换面的证据化清单（`temp/apisurface/`）

自建类文件解析器对比「vanilla Mojmap jar」vs「Forge 补丁 jar」的**全体 public/protected 成员面**：

| 指标 | 数值 |
|---|---|
| Forge 独有的原版类成员 | **971 个 / 393 个类** |
| 其中「名字在 vanilla 完全不存在」 | 493 个 |
| mod 源码实际引用的**确定性缺失符号** | **15 个** |
| 签名漂移条目（被源码引用的名字） | 138（41 ABSENT / 16 DESC / 81 OTHER-CLASS） |

15 个确定性缺失符号（**必然编译失败**，已逐一给出替代方案）：

| 符号 | 归属 | Fabric 替代 |
|---|---|---|
| `getPersistentData` | `Entity` / `BlockEntity` | FAPI 附件 或 CCA 组件 / `AttachmentTarget` |
| `getCapability` / `invalidateCaps` / `reviveCaps` | `LivingEntity` 等 | 同上（Capability 体系整体替换） |
| `fromNamespaceAndPath` | `ResourceLocation` | `new ResourceLocation(ns, path)` |
| `getPartialTick` | `Minecraft` | `getFrameTime()` |
| `getGuiLeft` / `getGuiTop` | `AbstractContainerScreen` | `leftPos` / `topPos` |
| `getSlotIndex` | `Slot` | `slot.index` |
| `withTabsBefore` | `CreativeModeTab.Builder` | `FabricItemGroup` 自有序配置 |
| `addPool` | `LootTable` | `LootTable.Builder#withPool` |
| `getPackOutput` / `modId` | `DataGenerator` / `TagsProvider` | datagen 重写（FAPI `FabricDataGenerator`） |
| `deserializeNBT` | `ItemStack` | `ItemStack.of(CompoundTag)` |
| `getStyleModifier` | `Rarity` | 见 §2.3（库侧自建） |

### 2.3 `Rarity` 5 档枚举扩展（**本轮最重要的可行性突破**）

原版 1.20.1 `Rarity`（javap 实证）：

```
public final class Rarity extends Enum<Rarity> {
    public static final Rarity COMMON, UNCOMMON, RARE, EPIC;
    public final ChatFormatting color;     // ← 唯一颜色通道（无 Forge 的 styleModifier）
    private Rarity(ChatFormatting);        // ← private 构造器 + final 类 + 枚举
}
```

三条硬约束排除常规手段：javac 禁 `new` 枚举（JLS 15.9.1）；`Constructor.newInstance` 对枚举被 JDK 硬拒；类 `final` 不可继承。

**采用方案（已实测 PASS，JDK 17 与 21 双验证）**：`Unsafe.allocateInstance(Rarity.class)` + `Unsafe` 字段偏移写 `Enum.name` / `Enum.ordinal` / `Rarity.color`，并把新常量追加进 `Rarity.$VALUES`。字段偏移写入**绕开了 `setAccessible` 对 `java.base` 的模块限制，不需要 `--add-opens`**。

- 实现落点：库 `fabric-1.20.1/.../item/AstralRarities.java`（已编译通过）。
- 收益：**`ModItems` 137 处 `.rarity(...)` 与 `client/RarityTooltipFrame` 零改动**。
- 配色口径：稀有 `AQUA`、史诗 `LIGHT_PURPLE`（与库 rgb 精确一致）；传奇 `GOLD`、巅峰/奇特 `RED`（近似）。奇特与巅峰**同色是原设计**（「奇特文字色 = 亮红，彩虹只在边框」），故文字色 4 种、`Rarity` identity 仍 5 个互不相同 ⇒ `tierOf` 与边框的 5 档判定完全保留。

### 2.4 前置库 `starengine_lib-fabric-1.20.1`：已构建 + 已发布

- 工作树 `F:\MCProject\starengine_lib_fabric`，分支 `fabric-1.20.1`（基线 `main @ 1.0.5`，与消费方引脚一致）。
- `common` 44 个源文件 **零改动**；新增 9 个平台类 + `fabric.mod.json` + `build.gradle`（loom）+ `gradle.properties`。
- 发布坐标：`com.merlinkitsune.starenginelib:starengine_lib-fabric-1.20.1:1.0.6`（74914 B，`~/.m2/repository/...`）。
- 逐字复制的类（纯原版 API）：`component/ItemDataKey.java`、`event/ModEffectRemoval.java`、`client/ActionBarManager.java`。
- 新写：`StarEngineLib`(ModInitializer)、`platform/LoaderEvent`(空基类)、`platform/LoaderTags`(`c:bosses` 自构造)、`economy/FabricEconomyStorage`(CCA 组件 + `RespawnCopyStrategy.ALWAYS_COPY`)、`item/AstralRarities`(见 §2.3)、`item/TrinketsCompat`(Curios 形状适配层)。
- ⚠️ **坐标纠正**：CCA 的 groupId 实测为 `dev.onyxstudios.cardinal-components-api`（**不是** `dev.onyxstudios.cca`，`VERSION_PINS.md` 写错）。
- ⚠️ 钱包 NBT 布局与另三线**必然不同**：CCA 写在玩家 `.dat` 的 `cardinal_components/starengine_lib:star_coin_wallet/{balance,name}`（Forge 侧为 `ForgeData/starengine_lib/star_coin_wallet`）。跨线存档不通用属预期。

### 2.5 消费方 `fabric-1.20.1`：骨架与构建系统已就位

- `settings.gradle` 已 `include 'fabric-1.20.1'`（+ `pluginManagement` 加 `maven.fabricmc.net`）。
- `fabric-1.20.1/build.gradle`：loom 1.14.10 + `loom.officialMojangMappings()` + Java 17 toolchain + 依赖（fabric-loader 0.19.5 / fabric-api 0.92.12+1.20.1 / trinkets 3.7.2 / CCA 5.2.3 / patchouli 1.20.1-85-fabric / 库 `modImplementation` + `include` 内嵌）+ 测试环境 `modRuntimeOnly`（**sodium / lithium / indium / starlight** / modernfix / ferrite-core / jade / carpet / kubejs / rhino）+ `pushToDevRun`/`pushToRootBuild`。
- `fabric-1.20.1/src/main/resources/fabric.mod.json`：`entrypoints.main = AstralDiceMod`、`entrypoints.client = AstralDiceClient`、`mixins = astral_dice.mixins.json`、`depends`（fabricloader / minecraft / fabric-api / trinkets / CCA base+entity / starengine_lib 区间）。
- 源码已从 `forge-1.20.1` 复制（254 个 `.java` + assets + data）。
- 编译已进入正常阶段：javac 首批 101 行错误**全部**是 `net.minecraftforge.*` / `top.theillusivec4.curios.*` 未解析 —— 即预期的加载器层替换面，无「隐藏的第二类问题」。

---

## 三、本轮修正的三项规划文档错误（**重要**）

1. **「1.20.1 Fabric 无附件 API」是错的。** Fabric API 0.92.12 的 jar 内确有
   `META-INF/jars/fabric-data-attachment-api-v1-0.92.12.jar`，API 为
   `AttachmentRegistry.create/createDefaulted/createPersistent/builder()` +
   `AttachmentType{identifier, persistenceCodec, isPersistent, initializer, copyOnDeath}` +
   `AttachmentTarget`。这与 NeoForge `AttachmentType` **结构 1:1**，而 mod 的 `AttachedDataKey`
   正是为模拟 NeoForge 附件而写。
   ⇒ **建议**：108 键附件层改用 **FAPI 附件**（近乎平移、风险最低、最贴近原作者设计）；
   钱包仍可留在 CCA（已实现并发布）。⚠️ 用户此前口径为「用 CCA 处理 Components」，
   该口径形成于本条事实被发现之前 ⇒ **此项需裁决**（见 §五）。

2. **`VERSION_PINS.md` 的 CCA 坐标写错**（见 §2.4）。

3. **`ref/fabric-api` 的 `1.20` 分支目录列表不能作为「某模块是否存在」的判据**
   （该分支不含 `fabric-data-attachment-api-v1`，但 0.92.12 发行 jar 含）。核验模块必须**读发行 jar**。

---

## 四、剩余工作量与执行顺序

### 4.1 必须先解决（阻塞一切）

1. **消费方 `platform/` 包（新增，本次未写）**
   - `platform/reg/DeferredRegister` + `RegistryObject`：薄包装 `Registry.register`，使 `ModItems`(137)/`ModEffects`(45)/`ModSounds`(12)/`ModParticles`/`ModMenuTypes`/`ModRecipeSerializers`/`ModCreativeTabs`/`ModEnchantments`/`AstralLootModifiers` 的**声明面零改动**。
   - `platform/event/LoaderBus` + `SubscribeEvent` + `EventPriority` + 约 40 个事件类（**必须保留 Forge 的优先级排序语义**）。
   - **理由（本轮实证）**：1.20.1 Fabric **没有任何可改伤害值的 FAPI 事件**（`ServerLivingEntityEvents.ALLOW_DAMAGE` 只能取消，不能改 amount）；而 `ChipDamageHandler` / `DiceCombatEvents` 的 `EventPriority.LOWEST/HIGHEST` 是**承重语义**（源码注释明写「气囊恒为第一顺位」「LOWEST = 最终伤害阶段」）。改写成 FAPI 回调会**丢失排序**，行为不可保真。
   - 桥接：从 mixin（`LivingEntity#actuallyHurt` / `#hurt` / `#die` / `#heal` / `#knockback`…）+ FAPI 回调（tick / join / disconnect / respawn / commands / loot / tooltip / render / 注册）向自建总线派发。

2. **Curios 适配层（消费方侧）**：`SlotContext`（`identifier()` / `entity()` / `index()`）、`ICurioItem`（`curioTick/onEquip/onUnequip/canEquip/canUnequip`）、`CuriosInventory`/`SlotHandler`/`SlotInventory` 形状，底层为 lib 的 `TrinketsCompat` + Trinkets 3.7.2。72 处 import 机械替换。
   - ⚠️ **动态槽位**：`DiceCurioItem` 用 Curios 的槽位尺寸修饰符（`CHIP_SLOT_MODIFIER` UUID）动态调整 chip 槽数。Trinkets 侧对应物是 `TrinketInventory#addModifier(EntityAttributeModifier)`（`ADDITION` 运算改 `getContainerSize()`）—— **必须专门验证**（这是本次移植最大的功能风险点之一）。

3. **持久化层**：`AttachedDataKey.store()` 后端替换（108 键、488 个调用点、57 个文件；调用面集中在 `ModAttachments` 门面 ⇒ 只需改 `AttachedDataKey` + `AstralData`/`ModCapabilities`/`ClientAstralData` 四个文件）。

4. **入口改写**：`AstralDiceMod` → `implements ModInitializer`；新增 `AstralDiceClient implements ClientModInitializer`；删 `init/MixinRuntimeGate.java`；配置改自实现 TOML（NightConfig）或 Cloth Config。

### 4.2 资源侧

- 删 `data/forge/loot_modifiers/global_loot_modifiers.json`；删 `data/bountiful/**`（无 Fabric 版）。
- `data/curios/tags/items/{dice,stand,chip}.json` → `data/trinkets/slots/{dice,stand,chip}/...`：
  ```
  data/trinkets/slots/dice/group.json     {"slot_id": <未占用值>}
  data/trinkets/slots/dice/dice.json      {"icon":"astral_dice:slot/empty_dice_slot","order":...}
  ... stand / chip 同理
  data/trinkets/entities/astral_dice.json {"slots":["dice/dice","stand/stand","chip/chip"],
                                           "entities":["minecraft:player"]}
  ```
  （格式实证自 `ref/trinkets/src/main/resources/data/trinkets/slots/**` 与 `data/SlotLoader.java`/`EntitySlotLoader.java`；
  注意 loader 只接受 namespace = `trinkets` 的路径 ⇒ 文件必须放在本模组 jar 的 `data/trinkets/` 下。）
- 13 个 `data/astral_dice/loot_modifiers/*.json`：可保留为**数据源**，由 `LootTableEvents.MODIFY` 前的 reload listener 读入（避免把概率硬编码进代码）。

### 4.3 客户端

- Forge 的 `RenderTooltipEvent.Color`（`RarityTooltipFrame` 用它写边框色）在 Fabric **无对应事件** ⇒ 需 mixin `GuiGraphics#renderTooltipInternal` 改写背景/边框 `fillGradient` 常量。26.1.2 线已有 `TooltipBorderMixin` + `RarityBorderRenderer` 先例可参考。
- 17 个 `net.minecraftforge.api.distmarker.Dist` / `@OnlyIn` 引用 ⇒ Fabric 无此概念，按包隔离处理（删注解即可）。
- HUD / 屏幕注入：`IGuiOverlay`→`HudRenderCallback`；`ScreenEvent.Init.Post`→`ScreenEvents.AFTER_INIT`；`RegisterKeyMappingsEvent`→`KeyBindingHelper`；`RegisterParticleProvidersEvent`→`ParticleFactoryRegistry`。

### 4.4 冒烟测试（用户明确要求，未执行）

- 环境：Loom `run/server` + `run/client`（`runDir` 已配为 `run/server` / `run/client`）。
- 用户要求加入的 mod：**lithium / indium / starlight**（+ sodium）——已在 `modRuntimeOnly`，版本为 Modrinth 实测值。
- ⚠️ **JEI 不能走 Gradle**：`jei-1.20.1-fabric-15.62.0.216.jar` 的 manifest 为 `Fabric-Loom-Version: 1.17.20`，被 Loom 1.14.10 的**构建版本门禁**拒绝（`Mod was built with a newer version of Loom`）。须手工投放 `run/server/mods` 与 `run/client/mods`。
- 判据（对标另三线）：启动到 `Done`、137 物品注册抽查、饰品槽位 UI、钱包余额跨死亡保留、首箱赠礼、网络包（12 条）、客户端 tooltip 边框 5 档。

---

## 五、需要裁决的两项

1. **附件层实现选型**：FAPI 附件（`fabric-data-attachment-api-v1`，推荐；近平移、风险最低）vs **CCA**（用户原口径，钱包已按此实现并发布）。
   建议：**附件层用 FAPI 附件 + 钱包保留 CCA**，或**统一改用 FAPI 附件**（此时库的 `FabricEconomyStorage` 需从 CCA 改为附件实现，改动约 60 行）。
2. **分支拓扑**：当前 `1.20.1-fabric` 分支的 `settings.gradle` 已含**四条线**（含 fabric）。
   主线 `multi-main` 仍是三条线；本次未 push、未改主线。

---

## 六、本轮新增踩坑（已写入项目记忆）

| # | 坑 | 规避 |
|---|---|---|
| K13 | **Loom 构建版本门禁**：mod 的 `MANIFEST.MF` 带 `Fabric-Loom-Version`，高于本地 Loom 即拒 | 定位：扫 `~/.gradle/caches/modules-2/files-2.1/**/*.jar` 的 manifest；绕法：该 mod 移出 Gradle 改手工投放 `run/mods` |
| K14 | Loom 1.14 起 Mixin AP 默认关闭，`loom { mixin {} }` 会告警 | 移除该配置；refmap 依赖 remapJar 自动生成 ⇒ **必须实测验证** |
| K15 | Gradle 9 configuration cache：任务闭包内引用 `project` 会执行期失败 | 属性在**配置期**取到局部变量 |
| K16 | `exclusiveContent` 是 `RepositoryHandler` 的方法 | 必须写在 `repositories { }` 内 |
| K17 | Modrinth maven 坐标大小写/`+` 敏感 | 用 **version id**（如 `nm6fiGRx`） |
| K18 | `-Xmaxerrs` 默认 100，javac 报错被截断 | 统计错误面时必须区分「截断」与「真实总数」 |

---

## 七、执行进展（第二轮，2026-09-29 03:xx）

### 7.1 已落地的加载器层（全部为新增实现，非 stub）

| 组件 | 文件 | 说明 |
|---|---|---|
| 事件总线 | `platform/event/{LoaderBus,IEventBus,Event,EventPriority,SubscribeEvent,Cancelable,HasResult}.java` | 自建，**保留 Forge 的优先级排序语义**（承重）；放宽为可派发任意事件对象（兼容库的 `SignActiveTriggeredEvent`） |
| 事件类（44 个） | `platform/event/**`、`platform/client/event/**`、`platform/fml/event/**` | **从 Forge sources jar 逐字转译**（类名/构造器/访问器完全一致），非手写近似 |
| 注册包装 | `platform/registry/{DeferredRegister,RegistryObject}.java` | 9 个注册类共 93 处 `register(...)` 与 1800+ 处 `.get()` **零改动** |
| 饰品适配 | `compat/curios/*`（`CuriosApi`/`SlotContext`/`ICurioItem`/`ICuriosItemHandler`/`IItemHandler`/`SlotResult`/`TrinketBridge`） | Curios 形状层，底层 Trinkets 3.7.2；`onEquip/onUnequip` 的参数语义映射已逐条写明 |
| 存档层 | `component/AttachedDataKey.java`（重写） | 改用 **Fabric API 附件**（`fabric-data-attachment-api-v1`，与 NeoForge 附件 1:1）；6 个 death-preserved 键用 `copyOnDeath()` 显式声明；**`AstralData` / `ModCapabilities` 已删除** |
| 网络层 | `platform/network/{SimpleChannel,NetworkRegistry,NetworkEvent,PacketDistributor}.java` | 前置 VarInt 消息 id 的单通道多路复用；主线程派发语义与 Forge 一致 |
| 配置层 | `platform/config/ForgeConfigSpec.java` | 自带最小 TOML 读写；`ModCommonConfig`（15 项）仅换 import |
| FML 工具 shim | `platform/fml/{ModList,IModInfo,ModLoadingStage,ModLoadingException,LogicalSide}`、`platform/fml/loading/{FMLPaths,FMLEnvironment}`、`platform/api/distmarker/Dist` | 让 7 处 `ModList` 等调用点零改动 |
| FAPI 桥接 | `platform/FabricBridges.java` | tick（server/world/player）、玩家登录/登出/重生、`ALLOW_DAMAGE`→`LivingAttackEvent`、命令注册 |
| 访问放宽 | `src/main/resources/astral_dice.accesswidener` | `RenderType$CompositeState`、`RenderStateShard$TransparencyStateShard`（Forge 亦为 public） |

### 7.2 编译收敛轨迹（`./gradlew :fabric-1.20.1:compileJava`）

```
588  → 525 → 344 → 361 → 135 → 6 → 4 → 72 → 71 → 61   （剩余 61 条）
```

### 7.3 剩余 61 条（全部已定位到具体文件与成因）

| `LivingHurtEvent.java:33` | 程序包eventbus.api不存在 |  |
| `LivingDropsEvent.java:36` | 程序包eventbus.api不存在 |  |
| `PlayerLifecycleHandler.java:189` | 找不到符号 | com.merlinkitsune.astral_dice.platform.event.OnDatapackS |
| `AddTableLootModifier.java:31` | 找不到符号 |  |
| `AddTableLootModifier.java:62` | 找不到符号 | public Codec<? extends IGlobalLootModifier> codec() { |
| `AstralLootModifiers.java:22` | 找不到符号 | public static final DeferredRegister<Codec<? extends IGl |
| `AstralLootModifiers.java:26` | 找不到符号 | public static final RegistryObject<Codec<? extends IGlob |
| `EntityEvent.java:146` | Deprecated 不是可重复的注释类型 | @Deprecated(forRemoval = true, since = "1.20.1") |
| `DeferredRegister.java:99` | 不兼容的类型: RegistryObject<CAP#1>无法转换为RegistryObject<? e | out.add(e.holder()); |
| `StarCoinWalletButtons.java:239` | leftPos 在 AbstractContainerScreen 中是 protected 访问控制 | setX(parent.leftPos + offsetX); |
| `StarCoinWalletButtons.java:240` | topPos 在 AbstractContainerScreen 中是 protected 访问控制 | setY(parent.topPos + offsetY); |
| `EliteTargets.java:61` | 找不到符号 | CompoundTag persistent = entity.getPersistentData(); |
| `EffectTimerGuard.java:80` | 程序包net.minecraft.core.registries.BuiltInnet.minecraf | ResourceLocation effectId = net.minecraft.core.registrie |
| `EffectTimerGuard.java:107` | 程序包net.minecraft.core.registries.BuiltInnet.minecraf | MobEffect effect = net.minecraft.core.registries.BuiltIn |
| `ModEffects.java:259` | 不兼容的类型: Collection<RegistryObject<? extends MobEffec | public static final Collection<RegistryObject<MobEffect> |
| `FirstLootChestHandler.java:192` | 找不到符号 | return player.getPersistentData().getCompound(ROOT_KEY). |
| `FirstLootChestHandler.java:196` | 找不到符号 | CompoundTag persistent = player.getPersistentData(); |
| `LootInjectionHandler.java:58` | 找不到符号 | if (table.getPool("astral_dice:star_coin") != null) retu |
| `LootInjectionHandler.java:69` | 找不到符号 | .name("astral_dice:star_coin") |
| `LootInjectionHandler.java:78` | 找不到符号 | .name("astral_dice:blank_chip") |
| `LootInjectionHandler.java:85` | 找不到符号 | .name("astral_dice:blank_chip") |
| `LootInjectionHandler.java:94` | 找不到符号 | .name("astral_dice:star_plate") |
| `LootInjectionHandler.java:103` | 找不到符号 | .name("astral_dice:glass_dice") |
| `ModEffectEvents.java:31` | 程序包net.minecraft.core.registries.BuiltInnet.minecraf | String effectId = net.minecraft.core.registries.BuiltInn |
| `ModEffectEvents.java:45` | 程序包net.minecraft.core.registries.BuiltInnet.minecraf | String id = net.minecraft.core.registries.BuiltInnet.min |
| `ModEffectEvents.java:62` | 程序包net.minecraft.core.registries.BuiltInnet.minecraf | EffectTimerGuard.forget(player, net.minecraft.core.regis |
| `ModCompatibilityCheck.java:102` | 找不到符号 | IModInfo self = ModList.get().getModContainerById(Astral |
| `ModCompatibilityCheck.java:103` | 找不到符号 | .map(container -> container.getModInfo()) |
| `ModCreativeTabs.java:22` | 无法将类 CreativeModeTab中的方法 builder应用到给定类型; | .register("dice_tab", () -> CreativeModeTab.builder() |
| `BaseEffectCardItem.java:573` | 方法不会覆盖或实现超类型的方法 | @Override |
| `BaseEffectCardItem.java:576` | 找不到符号 | return super.onDroppedByPlayer(stack, player); |
| `CardItem.java:22` | 方法不会覆盖或实现超类型的方法 | @Override |
| `CardItem.java:93` | 方法不会覆盖或实现超类型的方法 | @Override |
| `CardItem.java:96` | 找不到符号 | return super.onDroppedByPlayer(stack, player); |
| `EffectCardPeriod.java:565` | 找不到符号 | var curios = com.merlinkitsune.starenginelib.item.Curios |
| `FateGuidanceCardItem.java:150` | 无法将类 Item中的方法 getFoodProperties应用到给定类型; | net.minecraft.world.food.FoodProperties food = stack.get |
| `CursedSwordChipItem.java:104` | 找不到符号 | if (stack.getEnchantmentLevel(marker) <= 0) { |
| `FanBigChipItem.java:32` | 找不到符号 | var curios = com.merlinkitsune.starenginelib.item.Curios |
| `FanSmallChipItem.java:27` | 找不到符号 | var curios = com.merlinkitsune.starenginelib.item.Curios |
| `MotoHelmetChipItem.java:39` | 方法不会覆盖或实现超类型的方法 | @Override |
| `RailgunChipItem.java:169` | 找不到符号 | bolt.setDamage(damage); |
| `SandwichChipItem.java:36` | 方法不会覆盖或实现超类型的方法 | @Override |
| `SpeedSkatesChipItem.java:31` | 方法不会覆盖或实现超类型的方法 | @Override |
| `DiceCurioItem.java:250` | 找不到符号 | if (handler.getModifiers().containsKey(CURIO_LEGACY_MODI |
| `DiceCurioItem.java:251` | 找不到符号 | handler.removeModifier(CURIO_LEGACY_MODIFIER); |
| `DiceCurioItem.java:256` | 找不到符号 | AttributeModifier existing = handler.getModifiers().get( |
| `DiceCurioItem.java:260` | 找不到符号 | handler.removeModifier(CHIP_SLOT_MODIFIER); |
| `DiceCurioItem.java:261` | 找不到符号 | handler.addPermanentModifier(new AttributeModifier(CHIP_ |
| `DiceCurioItem.java:263` | 找不到符号 | handler.update(); |
| `ObsidianDiceItem.java:36` | 方法不会覆盖或实现超类型的方法 | @Override |
| `ModItems.java:113` | 找不到符号 | net.minecraft.tags.ItemTags.create(new ResourceLocation( |
| `ModItems.java:116` | 找不到符号 | net.minecraft.tags.ItemTags.create(new ResourceLocation( |
| `BaseSignItem.java:606` | 找不到符号 | var results = handler.findCurios(s -> s.getItem() instan |
| `BaseSignItem.java:619` | 找不到符号 | var results = handler.findCurios(s -> s.getItem() instan |
| `AddTableLootModifier.java:33` | 找不到符号 | public static final Codec<AddTableLootModifier> CODEC =  |
| `AddTableLootModifier.java:49` | 方法不会覆盖或实现超类型的方法 | @Override |
| `AddTableLootModifier.java:61` | 方法不会覆盖或实现超类型的方法 | @Override |
| `AstralLootModifiers.java:23` | 程序包ForgeRegistries不存在 | DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT |
| `EntityThunderHitMixin.java:61` | 找不到符号 | self.hurt(ModDamageTypes.trueDamage(level), bolt.getDama |
| `MerchantOfferMixin.java:77` | 找不到符号 | if (itemstack.getItem().isDamageable(itemstack)) { |
| `VersionGate.java:125` | 找不到符号 | return ModList.get().getModContainerById(AstralDiceMod.M |

### 7.4 明确未完成 / 已知缺口（**必须补齐，否则属静默失效**）

1. **战利品（~20 条错误）**：`AddTableLootModifier` / `AstralLootModifiers`（GLM）需整体替换为
   `LootTableEvents.MODIFY` + `LootPool`；`LootInjectionHandler` / `FirstLootChestHandler` 的
   `LootTableLoadEvent` 路径同样要改。
2. **`getPersistentData()`（4 条）**：`EliteTargets` / `FirstLootChestHandler` → 改用 Fabric 附件。
3. **客户端（未编译验证）**：`ModClientEvents` 的 HUD/按键/粒子注册需要 `AstralDiceClient` 接线；
   `RenderTooltipEvent.Color` 无 FAPI 对应 ⇒ 需 mixin `GuiGraphics#renderTooltipInternal`。
4. **未接线的关键事件（静默风险最高的部分）**：`LivingHurtEvent` / `LivingDamageEvent` /
   `LivingDeathEvent`（可取消）/ `MobEffectEvent.*` / `LivingHealEvent` / `LivingChangeTargetEvent` /
   `ProjectileImpactEvent` / `ItemTossEvent` / `EntityTravelToDimensionEvent` / `EntityTeleportEvent` /
   `LivingDropsEvent` / `AnvilUpdateEvent` / `BlockEvent.BreakEvent` / `ItemCraftedEvent` /
   `EntityItemPickupEvent` / `AttackEntityEvent` 等 **FAPI 无等价回调**者，需写 mixin 在对应注入点派发。
   ⚠️ **这批未接线前，游戏能启动但战斗链完全不生效** —— 属必做的下一批。
5. **dimension 切换**：`PlayerChangedDimensionEvent` 未接线（FAPI 无该回调），需 `ServerPlayer#changeDimension` mixin。
6. **datagen**：已整体移出（`temp/removed/datagen_*`），需按 FAPI 的 `FabricDataGenerator` 重新实现。
7. **冒烟测试**：未开始。


---

## 八、第三轮执行记录（2026-09-29 10:00–11:00）：CCA 移除 + 编译归零 + 产物级验证

### 8.1 用户裁决执行：移除 CCA → Fabric API 附件

| 位置 | 改动 |
|---|---|
| 库 `FabricEconomyStorage` | CCA 组件 → `AttachmentRegistry.builder().persistent(CompoundTag.CODEC).copyOnDeath().buildAndRegister(id)`；离线读档改走 `fabric:attachments` |
| 库 `build.gradle` / `gradle.properties` / `fabric.mod.json` | 删 CCA 运行依赖与 entrypoint；`depends` 改 `fabric-data-attachment-api-v1`；**保留 `modCompileOnly` CCA base**（Trinkets 的 `TrinketComponent` 继承 `ComponentV3`，编译期必须可见） |
| 库 `StarEngineLib` | 补调 `FabricEconomyStorage.registerCommands()`（原实现里 `/starcoin` 命令**从未接线**，属既有缺陷） |
| 消费方 `build.gradle` / `gradle.properties` / `fabric.mod.json` | 同上（`modCompileOnly` + 删运行前置） |
| 消费方 源码 | 无 CCA 引用（`AttachedDataKey` 早已是 FAPI 附件） |

**关键事实（必须登记）**：**Trinkets 3.7.2 自己 hard-depends CCA**
（`fabric.mod.json` 声明 `cardinal-components-base/entity >=3.0.0-0`，且 `TrinketComponent extends ComponentV3`）。
⇒ CCA 无法从运行环境消失，但已降级为「**Trinkets 的传递前置 + 我们的编译期 only**」，玩家不再为**本模组**单独安装它。

**产物核验**：库 jar `starengine_lib-fabric-1.20.1-1.0.6.jar`（72822 B）内 `fabric.mod.json` 的 `depends`
= `{fabricloader, minecraft, fabric-api, fabric-data-attachment-api-v1}`，**无 CCA** ✅。

### 8.2 编译归零（588 → 0）

| 阶段 | 处理内容 |
|---|---|
| 事件层 | 21 个 mixin（含新增 3 个）；`@eventbus.api.X` → `@X`；`EntityEvent` 重复 `@Deprecated` |
| 生命周期 | 生成 `OnDatapackSyncEvent` shim + `FabricBridges` 桥接 `SYNC_DATA_PACK_CONTENTS` |
| 注册/工具 | `ModList` 返回**真实** `ModContainer`/`IModInfo`（含 `getVersion()`，VersionGate 依赖它）；`IModInfo` 补 `getVersion()` |
| 战利品 | GLM 两文件 + 13 JSON + `data/forge/loot_modifiers` 移出（`temp/removed_glm/`）；新建 `loot/FabricLootInjector`（43 箱表 + 12 实体表，逐字取自原 GLM JSON） |
| 饰品 | `ICuriosItemHandler` → `ICursiosItemHandler`（文件/接口名对齐）；`ICurioStacksHandler` 补 `getModifiers/removeModifier/addPermanentModifier/update`；`ICursiosItemHandler` 补 `findCurios`；`ICurioItem` 补 `getAttributeModifiers`；`TrinketBridge` 桥接 `getModifiers` |
| Forge 补丁 API | `getPersistentData` → FAPI 附件（`FIRST_LOOT_CHEST_GIVEN`）；`ItemTags.create` → `TagKey.create`；`isDamageable(stack)` → `isDamageableItem()`；`getFoodProperties(stack,player)` → `getFoodProperties()`；`getEnchantmentLevel` → `EnchantmentHelper`（`world.item.enchantment` 包）；`getGuiLeft/Top` → `leftPos/topPos`（AW 放宽）；`CreativeModeTab.builder()` → `FabricItemGroup.builder()`（删 Forge 的 `withTabsBefore`）；`Item#onDroppedByPlayer` → 自有 `DropGuardItem` + 2 mixin；`Item#getMaxStackSize(stack)` → 自有 `StackCountOverrideItem` + 1 mixin；`LightningBolt#setDamage/getDamage` → `RailgunBolts` 弱引用表 |
| 裁剪 | `Apotheosis` 精英判据（无 Fabric 1.20.1 版）；Iron's Spells 联动 |

### 8.3 产物级验证（本轮最重要的可交付证据）

| 判据 | 结果 |
|---|---|
| `:fabric-1.20.1:build` | ✅ SUCCESSFUL，产物 `astral_dice-1.3.2+fabric_1.20.1.jar`（**1,539,193 B**） |
| jar 内 `fabric.mod.json` | ✅ 占位符已展开：`id=astral_dice`、`version=1.3.2+fabric_1.20.1`、`depends` 含 `fabric-data-attachment-api-v1` 与 `trinkets>=3.7.2`、**无 CCA**；`accessWidener` + `mixins` 均在 |
| **mixin 重映射**（R3） | ✅ 21 个 mixin 类，**0 处 Mojmap 残留**；`@Inject` 注解值已是 intermediary（`method_7914` / `method_7328` / `method_7329` …） |
| refmap | 无 refmap 文件（**预期**：Loom 1.14+ 在 `remapJar` 阶段直接重映射注解值，不再需要 refmap） |

### 8.4 仍未完成（下一轮必做，按优先级）

1. **🚨 事件 mixin 桥（最高优先，未接线 = 战斗链静默失效）**：
   `LivingDamageEvent`(11 处) / `LivingDeathEvent`(13) / `LivingHurtEvent`(4) / `MobEffectEvent.{Added,Pre,Remove,Expired}`(14) /
   `LivingHealEvent` / `LivingChangeTargetEvent` / `LivingUseTotemEvent` / `LivingDropsEvent` / `EntityItemPickupEvent` /
   `AttackEntityEvent` / `ItemTossEvent` / `ItemCraftedEvent` / `AnvilUpdateEvent` / `ProjectileImpactEvent` /
   `EntityTravelToDimensionEvent` / `EntityTeleportEvent` / `BlockEvent.BreakEvent`。
   共 47 种事件类型 / 109 个处理器，其中 **FAPI 已覆盖 9 类**（tick / 登录登出 / clone / respawn / 命令 / 数据包同步 /
   ALLOW_DAMAGE / 交互 / tooltip），**其余需 mixin 或 FAPI 交互回调补齐**。
   第三方可行性结论见 **`EVENT_API_RESEARCH.md`**（结论：不引入，自写；MixinExtras 已内置可用）。
2. **客户端接线**：`AstralDiceClient`（`ClientModInitializer`）+ HUD（`HudRenderCallback`）+ 按键（`KeyBindingHelper`）+
   粒子（`ParticleFactoryRegistry`）+ `HandledScreens.register`（容器）+ tooltip 边框 mixin
   （Forge 的 `RenderTooltipEvent.Color` 无 FAPI 对应）+ `RenderLevelStageEvent` → `WorldRenderEvents`。
3. **datagen**：按 `FabricDataGenerator` 重做（现整体移出）。
4. **资源重排**：`data/curios/slots|tags` → `data/trinkets/slots/<group>/{group,<slot>}.json` + `data/trinkets/entities/astral_dice.json`；
   删 `data/forge/**`（若还有残留）。
5. **冒烟测试**：`run/server` + `run/client`；测试环境需装 lithium / indium / starlight（已进 `modRuntimeOnly`）；
   ⚠️ **JEI 不能走 Gradle**（其 manifest `Fabric-Loom-Version: 1.17.20` 被 Loom 1.14.10 门禁拒绝）⇒ 手工投放 `run/mods`。

---

## 9. 本轮追加：Accessories 兼容 + dev 环境阻塞修复（2026-09-29）

### 9.1 需求与方案

用户需求：**饰品同时可以在 Trinkets 和 Accessories 上正常工作**。
Accessories（Wisp Forest）是**数据驱动**饰品库，API 与 Trinkets 完全不同 ⇒ 走**原生适配**，
不依赖已停更的官方 Trinkets 兼容层（理由见 `VERSION_PINS.md` 的追加段）。

**架构：单门面多源聚合。** 全部 94 处消费方调用都收敛在
`compat/curios/CuriosApi.getCuriosInventory(...)`，所以只改门面即可让所有消费方零改动双平台化：

| 操作 | 聚合语义 |
|---|---|
| `findFirstCurio` / `findCurios` | 按优先级遍历两源取并集（并集按 `ItemStack` **实例同一性**去重 —— 防玩家另装官方 compat layer 时两源指向同一库存而重复） |
| `getStacksHandler(id)` | 同名槽位两边都在时返回 `MergedHandler`：**读优先取非空侧、写落到持有侧**（都空则落主源 = 下蹲右键自动装备的落点） |
| `getModifiers` / `addPermanentModifier` / `removeModifier` / `update` | **两侧同写**，保证「筹码栏位数 = 骰子星级」在两套系统里始终一致 |
| 主源方向 | **Accessories 在场时为主源**；只装 Trinkets 的玩家走单源快路径，行为与本轮改动前完全一致 |

### 9.2 新增/修改文件

**新增**：`compat/accessories/AccessoriesCompat.java`（验证器注册 + `ICurioItem`→`Accessory` 适配器 +
`AccessoriesCapability`→`ICursiosItemHandler` 视图 + 启动自检）、`AstralDiceClient.java`（客户端入口）、
`data/astral_dice/accessories/{slot,group,entity}/*.json`、`data/trinkets/{slots,entities,tags}/**`、
`scripts/devtools/unpack_nested_mod_jars.py`。

**修改**：`CuriosApi`（多源聚合）、`AstralDiceMod`（守卫内注册 Accessories 适配器）、
`build.gradle`（可选依赖 + `interfaceInjection` 注释 + 渲染栈开关）、`gradle.properties`（谓词语法 + 钉值）、
`fabric.mod.json`（`recommends`）、三份 lang（`accessories.slot.*` / `trinkets.slot.astral_dice.*`）、
两个 drop-guard mixin（描述符）。

### 9.3 本轮修掉的三个**启动级**阻塞（均为移植遗留缺陷，非 Accessories 引入）

1. **Fabric 版本谓词不能写 Forge 语法**：`minecraft_version_range=[1.20.1]`（Maven 区间）被
   `VersionPredicateParser` 当成字面量 ⇒ `HARD_DEP_INCOMPATIBLE_PRESELECTED`。
   ⇒ 改 `~1.20.1`；`starengine_lib_version_range` 由 `[1.0.6,2.0)` 改 `>=1.0.7 <2.0`（空格 = AND）。
   **库侧同步修复并 bump 到 1.0.7**（`gradle.properties`）。
2. **mixin 注入目标歧义**：`@Inject(method = "drop")` 在 `Player` / `ServerPlayer` 上有多个重载 ⇒
   `InvalidInjectionException`。必须写**完整描述符**，且 `ServerPlayer#drop(boolean)` 返回 **boolean**（不是 ItemEntity）。
   ℹ️ 同时**订正一条旧结论**：Loom 1.14 的 `remapJar` **能够**正确重映射带描述符的 mixin 目标
   （产物实证 `drop(...)` → `method_7329(Lnet/minecraft/class_1799;ZZ)Lnet/minecraft/class_1542;`）。
3. **Loom interface injection 的双向副作用**：Accessories 的 `custom.loom:injected_interfaces` 把
   `AbstractButtonExtension` 注入 `AbstractButton` ⇒ 本模组自己的 `WalletButton` 被要求实现
   `getRenderingEvent()`（编译失败）。该开关**不能关**（Fabric API 的 `LootTable.Builder#pool` 依赖同款机制），
   故在源码侧补一个**编译期占位实现**（返回 null；私有内部类、无外部调用方，且 JVM 不校验抽象方法实现）。

### 9.4 本轮实测判据

| 判据 | 结果 |
|---|---|
| `:fabric-1.20.1:build` | ✅ SUCCESSFUL，产物 `astral_dice-1.3.2+fabric_1.20.1.jar`（1,560,637 B） |
| jar 内 `fabric.mod.json` | ✅ `recommends.accessories = ">=1.0.0-beta.48"`，**不在 `depends`** |
| jar 内数据 | ✅ `data/astral_dice/accessories/{slot,group,entity}` + `data/trinkets/{slots,entities,tags}` 全部入包 |
| **服务端启动** | ✅ `Loading 67 mods`（含 `accessories 1.0.0-beta.48+1.20.1` / `cloth-config 11.1.136` / `trinkets 3.7.2`）→ `Done (0.622s)!` |
| **双通道适配器** | ✅ `已为 98 件饰品物品注册 Trinkets 适配器` + `已为 98 件饰品物品注册 Accessories 适配器,槽位验证器 = astral_dice:curio_slot` |
| **槽位准入链** | ✅ 启动自检：`dice -> [dice]` / `stand -> [stand]` / `chip -> [chip]` —— 同时验证槽位定义、entity 绑定与自定义验证器三者生效且不串槽 |
| **客户端启动** | ✅ `Astral Dice client initialized.` → `Sound engine started` → 纹理图集全部创建（4 个 client mixin 无错） |
| 槽位解析告警 | ✅ 无 `Unable to locate a given slot` / strictMode 告警 |

### 9.5 仍未完成（更新 8.4）

1. **🚨 事件 mixin 桥**（不变，最高优先）：见 `EVENT_API_RESEARCH.md`。
2. **客户端接线**：`AstralDiceClient` 入口已补（客户端**能启动**），但
   `platform/FabricBridges` **没有客户端对应物** ⇒ 按键 / 粒子 / HUD overlay / tooltip 边框 /
   `ScreenEvent` / 渲染阶段的 `@SubscribeEvent` 目前**注册了但不派发**。
3. **datagen**：仍待按 `FabricDataGenerator` 重做。
4. ~~资源重排~~ ⇒ **Trinkets / Accessories 两侧槽位数据本轮已完成**（`data/curios/tags/items/**` 仍是物品清单的
   单一事实源，`trinkets` 侧标签用 `#curios:<slot>` 引用它）。
5. **玩家侧实机装填验证**：本轮验到「槽位准入链通」；「GUI 拖拽装备 + 筹码栏随星级增长 + 卸载回调」
   仍需要一个在线玩家（测试台 `mt.ps1` 或手工）复验。
