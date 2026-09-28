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
