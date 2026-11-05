# fabric-1.21.1 新增线：移植可行方案（2026-11-03 勘察稿）

> 触发：用户 2026-11-03 裁决「增加新版本 fabric-1.21.1，与 fabric-1.20.1 同平台，游戏版本升级为 1.21.1；先派一路子代理探查该版本情况，建立移植可行方案」。
> 状态：**只读勘察 + 方案设计**，未创建任何 fabric-1.21.1 文件、未改构建、未起游戏。
> 本文所有数字均为 2026-11-03 实读（命令见各节）。

## 0 一句话结论

**可行，但不要从 fabric-1.20.1 往上搬**：应当以 `neoforge-1.21.1` 的 419 个业务文件为蓝本（它的原版 API 天然是 1.21.1），只把**平台层**换成一套新的 Fabric shim（镜像 NeoForge 21.1 API，机制照抄 fabric-1.20.1 的 LoaderBus + FabricBridges 做法）。
量化依据：419 个文件里 **277 个完全不 import 平台 API**（可近乎逐字复制），**142 个** import NeoForge，其中 **79 个**用到「NeoForge 21.1 特有语义」（必须逐个改写），剩下 63 个是 1:1 的包名替换。
真正的新增工作是三块：① 平台层 shim（约 85 个类，其中 ~55 可复用 fabric-1.20.1 现成件、~30 重写）；② starengine 要出 **第 5 个平台产物** `starengine-fabric-1.21.1`（含 GeckoLib vendor 的 fabric 根）；③ 测试台四线→五线的版本表扩展 + AutoJS fabric-1.21.1 产物缺失问题。

## 1 事实基线（实读）

### 1.1 仓库与线

| 项 | 值 |
|---|---|
| `settings.gradle` | include 四线：`neoforge-1.21.1` / `forge-1.20.1` / `neoforge-26.1.2` / `fabric-1.20.1`；注释明确「每个 MC 版本+加载器 一个自包含子项目，不使用 common 源码子项目」 |
| 根 `gradle.properties` | 只有 `org.gradle.*`（jvmargs -Xmx3G、daemon/parallel/caching/configuration-cache=true），无版本号属性 |
| `git grep "fabric-1\.21\.1"` | **0 命中** ⇒ 仓内没有任何既存 fabric-1.21.1 痕迹，是全新线 |

### 1.2 体量对比（`find ... -name '*.java' | wc -l`）

| 线 | java 文件 | mixin | platform shim | 资源（main+generated） | assets | data |
|---|---|---|---|---|---|---|
| neoforge-1.21.1 | 419 | 24 | **无** | 977+371=1348 | 1043 | 298 |
| fabric-1.20.1 | 530 | 49 | **79** | 968+370=1338 | 1043 | 288 |
| forge-1.20.1 | 411 | — | 无 | — | — | — |
| neoforge-26.1.2 | 429 | — | 无 | — | — | — |

- 相对路径交集：n21 ∩ fabric = **379**，仅 n21 有 **40**，仅 fabric 有 **151**（platform 79 + bridge mixin 21 + trinkets/jade 兼容 + 1.20.1 专属容器 mixin 等）。
- 资源侧几乎对等（assets 都是 1043）；data 差 10 个文件 = 1.20.1 与 1.21.1 的 datapack 格式差，而 **n21 的那 298 个已经是 1.21.1 正确形态**。

### 1.3 平台耦合（`grep -rl "^import net\.neoforged"`）

- import NeoForge 的文件：**142 / 419**；完全不 import 的：**277 / 419**。
- NeoForge 顶层类型去重：**79 个**（与 fabric-1.20.1 的 shim 文件数 79 数量巧合，因为那 79 也是按 forge-1.20.1 的 import 面等量镜像出来的）。
- import 频次前列：`bus.api.SubscribeEvent` 90、`fml.common.EventBusSubscriber` 86、`network.PacketDistributor` 32、`bus.api.EventPriority` 20、`event.entity.living.LivingDeathEvent` 19、`api.distmarker.Dist` 16、`network.handling.IPayloadContext` 14、`event.entity.living.LivingDamageEvent` 14、`registries.DeferredRegister` 13、`event.tick.ServerTickEvent` 12。
- **需新语义的文件 = 79 个**（按类型分组计数，含重叠）：network 族 47、`registries.DeferredHolder/Item/Block` 12、`IMenuTypeExtension/ICondition` 3、`RegisterGuiLayersEvent/VanillaGuiLayers/RenderGuiLayerEvent` 2、`GatherDataEvent/ItemModelProvider/ExistingFileHelper` 2、`AttachmentType` 1、`ModConfigSpec/ModConfig` 1、`DamageContainer`(+ `LivingIncomingDamageEvent` 9 处调用点) 1。

### 1.4 fabric-1.20.1 的平台层做法（这就是新线的施工图纸）

- `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/platform/` 79 个类，**镜像的是 Forge 1.20.1 API**：`event/{Event,Cancelable,HasResult,EventPriority,IEventBus,LoaderBus,SubscribeEvent,SubscriptionAudit,TickEvent,...}`、`fml/{IModInfo,LogicalSide,ModContainer,ModList,ModLoadingStage,loading/{FMLEnvironment,FMLPaths},event/lifecycle/...}`、`registry/{DeferredRegister,RegistryObject}`、`config/ForgeConfigSpec`、`network/{SimpleChannel,PacketDistributor,NetworkRegistry,NetworkEvent}`、`client/{FabricClientBridges,event/*12,gui/overlay/{ForgeGui,IGuiOverlay,VanillaGuiOverlay}}`、`api/distmarker/Dist`、`item/{DropGuardItem,StackCountOverrideItem}`。
- 事件机制 = **自建总线 + Fabric 回调桥 + 缺回调处用 mixin 派发**。`FabricBridges` 类注释载明「消费方 125 个处理器全部注册在 `LoaderBus` 上（保留 Forge 的 EventPriority 排序语义）」，且带一张**覆盖状态表**（哪条事件从哪个 FAPI 回调 / 哪个 mixin 派发）——这是必须逐条维护的关键资产，新线要照做。
- 网络是 1.20.1 形态：`SimpleChannel` + 手写 channel handler，17 个文件（`ModNetwork` / `VersionGate` / `VersionHandshake` / 各 `*Message`）。
- 构建：`fabric-loom 1.17.21`、toolchain 17、`loom.officialMojangMappings()`、`archivesName='astral_dice'`、`sourceSets.main.resources.srcDir('src/generated/resources')` 且必须 exclude `**/*.bbmodel`（历史事故：漏掉会丢 137 模型/118 配方）。
- `fabric.mod.json` entrypoints：main/client/fabric-datagen/jade/jei_mod_plugin；`accessWidener: astral_dice.accesswidener`；mixins 三份（`astral_dice.mixins.json` / `.jade.mixins.json` / `.trinkets.mixins.json`）。

### 1.5 治理文档现状

- `porting/CROSS-VERSION-DIFFS.md`（193 行）已给出优先级表：**P0 基准 = neoforge-1.21.1**；fabric-1.20.1 = 「功能面蓝本 n21 + 代码蓝本 forge-1.20.1（因同为 1.20.1）」。新线的定位会自动变成「**代码蓝本 = n21**（因同为 1.21.1），功能面蓝本 = n21」，等于把 B 系列差异里「1.20.1 保留差异」那几条作废，平台差异照抄。
- `porting/fabric-1.20.1/` 12 份移植档案（FABRIC-DIFFS / PORT_ANALYSIS / SKILL / VERSION_PINS / EVENT_API_RESEARCH / gap-report …）可直接作为施工图。
- `ref/` 已有 fabric-api / fabric-loader / yarn / trinkets / puzzles-lib / cardinal-components-api / jade / patchouli 的上游源码克隆 ⇒ **1.21.1 API 差异可以在本地查证，不必联网猜**。

## 2 路线裁决

| 路线 | 做法 | 优点 | 代价 | 结论 |
|---|---|---|---|---|
| **A（推荐）** | 蓝本 = `neoforge-1.21.1` 的 419 业务文件；新建 Fabric shim 镜像 **NeoForge 21.1** API；机制照抄 fabric-1.20.1（LoaderBus + Bridges + bridge mixin） | 原版 API 已经是 1.21.1，不用做 1.20.1→1.21.1 的版本迁移；277 个文件可逐字复制；资源侧 n21 已正确 | 需写一套新 shim（~85 类，其中 ~30 真是新语义） | **采用** |
| B | 蓝本 = `fabric-1.20.1`（530 文件，平台层现成），再把 1.20.1 原版 API 迁到 1.21.1 | 平台层零改动 | 要对 **530 个文件**再做一遍 1.20.1→1.21.1 迁移（n21 已经做过一次，等于重做）；且 1.20.1 的 `LivingHurtEvent`/`SimpleChannel` 语义在 1.21.1 已被取代 | 否决 |
| C | 引 Architectury / 自建跨加载器抽象层，两条 fabric 线共用 | 长期最省 | 等于重写整个模组架构，与「自包含子项目」的既有裁决冲突 | 否决（可作远期议题） |

**关键洞察**：两块省工点。
1. **网络层几乎免费**：n21 的 payload record 全部 `implements net.minecraft.network.protocol.common.custom.CustomPacketPayload`（**原版类型，加载器中立**）+ 自带 `StreamCodec`。加载器相关的只有两处 API：`RegisterPayloadHandlersEvent/PayloadRegistrar`（注册）与 `PacketDistributor`（发送，66 处调用）。所以 shim 只需提供 4 个门面类，**47 个 network 文件业务逻辑一行不用改**。
2. **事件总线可 80% 复用**：NeoForge 21.1 与 Forge 1.20.1 的事件模型相同（`IEventBus`/`SubscribeEvent`/`EventPriority`/`EventBusSubscriber`），fabric-1.20.1 的 `LoaderBus` 与排序语义可直接搬；差异在于「NeoForge 的 `@EventBusSubscriber` 自动扫描」在 Fabric 侧不存在（B2 差异）⇒ 那 **86 个** 用 `@EventBusSubscriber` 的文件要改成显式注册（fabric-1.20.1 已经全部改过，可直接对齐其写法）。

## 3 目标结构（fabric-1.21.1 子项目）

```
fabric-1.21.1/
  gradle.properties              # 1.21.1 依赖钉版（见 §6）
  build.gradle                   # loom 1.17.21 / toolchain 21 / officialMojangMappings
  src/main/resources/
    fabric.mod.json              # entrypoints + AW + 三份 mixin config + depends
    astral_dice.accesswidener    # 从 fabric-1.20.1 迁，按 1.21.1 映射复核
    astral_dice.mixins.json      # JAVA_21；业务 mixin + bridge mixin
    astral_dice.jade.mixins.json
    astral_dice.trinkets.mixins.json
  src/main/java/com/merlinkitsune/astral_dice/
    <业务代码：蓝本 n21>          # 277 逐字 + 63 包名替换 + 79 语义改写
    platform/                    # 新 shim（镜像 NeoForge 21.1，~85 类）
    mixin/                       # n21 的 24 个 + fabric 专属 bridge mixin
    compat/{jade,jei,trinkets,accessories}/
  src/generated/resources/       # 蓝本 n21（371 文件，1.21.1 格式正确）
```

## 4 平台层映射表（NeoForge 21.1 → platform.*）

### 4.1 1:1 包名替换（可直接机械替换，63 个文件、约 45 个类型）

| NeoForge 21.1 | fabric-1.21.1 目标 |
|---|---|
| `net.neoforged.bus.api.{IEventBus,SubscribeEvent,EventPriority}` | `...astral_dice.platform.event.{IEventBus,SubscribeEvent,EventPriority}`（沿用 fabric-1.20.1 实现） |
| `net.neoforged.fml.{ModList,ModContainer,ModLoadingStage}`、`fml.common.{Mod,EventBusSubscriber}` | `platform.fml.*`（`EventBusSubscriber` 只保留注解语义供扫描期校验，实际注册走 LoaderBus，见 B2） |
| `net.neoforged.neoforge.event.entity.living.*`（LivingDeathEvent / LivingDamageEvent / LivingChangeTargetEvent / MobEffectEvent / LivingHealEvent / LivingDropsEvent / LivingUseTotemEvent / LivingEntityUseItemEvent 等） | `platform.event.entity.living.*`（fabric-1.20.1 已有同名类，**按 n21 的字段/方法签名对齐**） |
| `neoforge.event.entity.player.*`、`event.entity.item.*`、`event.level.*`、`event.tick.*`、`event.village.*`、`event.RegisterCommandsEvent`、`event.LootTableLoadEvent`、`event.AnvilUpdateEvent`、`event.BuildCreativeModeTabContentsEvent`、`event.entity.EntityAttributeCreationEvent` | `platform.event.*`（同上，多数已有） |
| `neoforge.registries.{DeferredRegister,DeferredHolder}` | `platform.registry.*`（**签名要按 NeoForge 21.1 改**：`DeferredHolder` 而非 `RegistryObject`；13+10 个文件） |
| `neoforge.common.util.TriState`、`common.ModConfigSpec`、`fml.config.ModConfig` | `platform.common.*` / `platform.config.*` |
| `api.distmarker.{Dist,OnlyIn}`（16 处） | `platform.api.distmarker.*`（已有） |

### 4.2 必须重写的新语义（~30 个类 / 79 个业务文件）

| 能力 | NeoForge 21.1 形态 | Fabric 1.21.1 实现方式 | 受影响业务文件 |
|---|---|---|---|
| **网络注册** | `RegisterPayloadHandlersEvent` → `PayloadRegistrar.playToClient/playToServer(TYPE, STREAM_CODEC, handler)`，handler 收 `IPayloadContext`（`context.enqueueWork`） | shim 提供同名 4 类：registrar 内部转 `PayloadTypeRegistry.playC2S/playS2C` + `ServerPlayNetworking.registerGlobalReceiver` / `ClientPlayNetworking.registerGlobalReceiver`；`IPayloadContext.enqueueWork` → `context.client().execute(...)` | 47（业务代码零改，只改 import） |
| **网络发送** | `PacketDistributor.sendToPlayer/sendToServer/sendToPlayersTrackingEntity` | shim `PacketDistributor`：`ServerPlayNetworking.send` / `ClientPlayNetworking.send` / `PlayerLookup.tracking(entity)` | 32 文件 66 处 |
| **物品数据组件** | `DataComponents` + `stack.get(KEY)` / `.component(KEY, v)`——n21 有 **138 处** `DataComponents.`、12 处 `.component(` | fabric-1.20.1 是 `starengine` 的 `ItemDataKey` shim（`get(stack)/set/remove`，126 处调用面 / **22 个文件**）；新线直接用 n21 形态 ⇒ 只换 shim 实现，不改业务语义 | **126 处 / 22 文件**（比原估的 10 个文件大一个量级） |
| **数据附件** | `AttachmentType` + `.serialize(codec)` | fabric-1.20.1 走 Cardinal Components（`component/` 10 文件）；1.21.1 可继续 CCA 6.1.3，或改用 `fabric-data-attachment-api-v1` | 1 处 import + component/ 10 文件 |
| **GUI 图层** | `RegisterGuiLayersEvent` / `VanillaGuiLayers` / `RenderGuiLayerEvent` | FAPI `HudLayerRegistrationCallback` + `IdentifiedLayer`（1.21 改名：Overlays→Layers） | 2 |
| **配置** | `ModConfigSpec`（NeoForge 21.1） | 二选一：① shim 包一层 cloth-config 15.0.140；② 直接用 **Forge Config API Port v21.1.6-1.21.1-Fabric**（它就是 NeoForge 21.1 的 config API 移植，可能直接提供 `net.neoforged.neoforge.common.ModConfigSpec`，**待验证**） | 1 |
| **伤害管线** | `LivingIncomingDamageEvent`（9 处调用点）+ `DamageContainer` | fabric-1.20.1 的做法 = **Puzzles Lib** 的伤害回调 + `bridge/LivingHurtBridgeMixin` 派发。1.21.1 有 `puzzles-lib 21.1.66`，需确认其回调面与 `DamageContainer` 语义差 | 1 类 + 9 调用点 |
| **菜单类型扩展** | `IMenuTypeExtension.create(Codec)` | shim：`new MenuType<>(factory, FeatureFlags.VANILLA_SET)` 等价实现 | 1 |
| **配方条件** | `ICondition`/`IConditionBuilder` | shim 直通原版 `ICondition`（1.21.1 原版已有） | 2 |
| **数据生成** | `GatherDataEvent` / `ItemModelProvider` / `ExistingFileHelper` | FAPI `FabricDataGenerator` + 自定义 provider；n21 的 `datagen/` 3 文件需按 Loom datagen 重写 | 2–3 |
| **注册表附属** | `NeoForgeRegistries` | 原版 `BuiltInRegistries`（1.21.1 已并入） | 0（未使用） |

## 5 分阶段执行计划（每阶段带判据）

| 阶段 | 内容 | 判据（机器可验） |
|---|---|---|
| **P0 脚手架** | 新建 `fabric-1.21.1/`：`gradle.properties`（1.21.1 钉版）、`build.gradle`（loom 1.17.21 + toolchain 21）、`settings.gradle` include、`fabric.mod.json` 骨架、空主类 | `:fabric-1.21.1:build` 绿；jar 落 `fabric-1.21.1/build/libs/` 与仓根 `build/libs/`；`bash scripts/test/win/env.sh --version fabric-1.21.1` 认这个版本（先只做版本表扩展） |
| **P1 平台层 shim** | 建 `platform/`（~85 类）：先搬 fabric-1.20.1 的 event/fml/registry/distmarker（~55），再写 network 4 类 + gui layers 3 类 + attachment/config/datagen/menu/condition（~30） | `grep -rn "^import net\.neoforged" fabric-1.21.1/src` = **0**；`:fabric-1.21.1:compileJava` 绿 |
| **P2 业务代码搬运** | 277 文件逐字复制 → 63 文件包名替换 → 79 文件语义改写（network 47 只改 import） | compileJava 非 UP-TO-DATE 且绿；`grep -rn "net\.minecraftforge\|software\.bernie\.geckolib" fabric-1.21.1/src` = 0 |
| **P3 资源与数据** | 蓝本 n21 的 main+generated 资源（1348 文件）；重写 `fabric.mod.json` / AW / 三份 mixin config；container/bridge mixin 按 1.21.1 原版类复核。**1.21.1 数据包硬差异**：`pack.mcmeta` 的 `pack_format` 须 `{"resource":34,"data":48}`（fabric-1.20.1 现在是 15）；目录单复数 `recipes→recipe`、`loot_tables→loot_table`、`advancements→advancement`、`tags/items→tags/item`；配方 `result` 由 `{"item":X}` 变 `{"count":N,"id":X}`（ingredient 段仍 `{"item":…}`，id 化属 1.21.2+）；1.21 起附魔数据驱动（n21 有 `data/astral_dice/enchantment/curse_marker.json`，fabric-1.20.1 是代码实现） | jar 内 assets 计数 = n21；`pack.mcmeta` 34/48；`python tools/check_lang_sync.py` 该线 OK；`python scripts/verify/verify_tag_parity.py` 该线 OK |
| **P4 注册与服务端** | 注册表逐项落地（item/block/effect/entity/attachment/menu/particle/sound） | 起 `runServer` 到 `Done (`；AutoJS/RCON 读注册表：`astral_dice:sykestentacle`、效果数、附件键在位 |
| **P5 网络与版本门禁** | payload 注册 + 版本握手（`VersionGate`） | 用例 `INJECT-ROUNDTRIP-fabric-1.21.1`、`VERSION-GATE-fabric-1.21.1` 绿 |
| **P6 客户端渲染** | GUI 图层、签牌半透明、tooltip 框色、粒子 provider | `mt_capture` 截图非空；立牌 tooltip 分支数 = 31 |
| **P7 GeckoLib 前置** | starengine 新平台 `starengine-fabric-1.21.1`（Loom）+ vendored GeckoLib 的 **fabric 根**（上游 1.21.1 分支有 `fabric/` 8 个 java）；消费方接该产物 | 触手实体渲染 + `triggerAnim` 攻击动画（与 1.21.1 neoforge 同一判据）；jar 内无 jarjar |
| **P8 测试台与治理** | VERSIONS 四线→五线、用例库 `-fabric-1.21.1` 克隆、CROSS-VERSION-DIFFS 新增一行、AGENTS 口径 | `MT_PREFLIGHT: OK`；`BOOT-READY`/`JAR-ASSETS` 绿；门禁三绿（lang / tag / changelog） |

## 6 依赖可得性（Modrinth 实查，2026-11-03；game_versions=1.21.1, loader=fabric）

| 用途 | 坐标 / 版本 | version id | 结论 |
|---|---|---|---|
| **Fabric Loader** | `0.20.1`（stable） | — | ✅ 必须抬（现用 0.19.5） |
| **Fabric Loom** | 插件版本 **1.19.3**（release；latest 1.19-SNAPSHOT） | — | ✅ **必须抬**：现钉 1.17.21 对 1.21.1 不足 |
| Fabric API | `0.116.17+1.21.1` | `Mys3P7lK` | ✅ |
| **Puzzles Lib（硬依赖，伤害回调）** | `21.1.66` | `IV6k1yRp` | ✅（fabric-1.20.1 靠它拿可改数值伤害事件，新线同路） |
| Trinkets（软依赖） | `3.10.0` | `JagCscwi` | ⚠️ 只有 1 个 1.21.1 版本，且 3.10 与现用 3.7.2 有 API 差异（槽位数据驱动化） |
| Accessories（软依赖，二选一） | `1.1.0-beta.54+1.21.1` | `YZ9RWTNl` | ⚠️ beta 大版本跃迁（1.0.0-beta.48 → 1.1.0-beta.54） |
| Architectury | `13.0.11+fabric` | `Pzc2FP5K` | ✅ |
| Cardinal Components | `6.1.3` | `nLsCe2VD` | ✅ |
| Patchouli | `1.21.1-93-fabric` | `XIjNwQkI` | ✅ |
| JEI | `19.70.0.469` | `WkRLMWAY` | ✅ |
| Jade | `15.10.6+fabric` | `adpNvTZS` | ✅ |
| Cloth Config | `15.0.140+fabric` | `HpMb5wGb` | ✅ |
| Forge Config API Port | `v21.1.6-1.21.1-Fabric` | `N5qzq0XV` | ✅（版本号对齐 NeoForge 21.1.6 ⇒ 可能直接提供 `ModConfigSpec`，**待验证**） |
| Mod Menu | `11.0.5` | `6lgOkclV` | ✅ |
| Sodium | `mc1.21.1-0.8.13-fabric` | `SMxNOGZ6` | ✅ |
| Iris | `1.8.14-beta.1+1.21.1-fabric` | `bAo1Qhte` | ✅ |
| Lithium | `mc1.21.1-0.15.4-fabric` | `N08Z8wog` | ✅ |
| Carpet | `1.4.147` | `f2mvlGrg` | ✅ |
| Debugify | `1.21.1+1.1` | `pyIdu3f1` | ✅ |
| YACL | `3.8.2+1.21.1-fabric` | `o3cDn8Vp` | ✅ |
| Superflat World No Slimes | `1.21.1-3.7-fabric+forge+neo` | `OHCdhqlM` | ✅ |
| Collective | `1.21.1-8.75-fabric+forge+neo` | `JsrQpNp7` | ✅ |
| ModernFix | `5.25.1+mc1.21.1` | `NnNX8LBn` | ✅ |
| ImmediatelyFast | `1.6.15+1.21.1-fabric` | `aLnBqxFW` | ✅ |
| Indium | `1.0.35+mc1.21` | `Z8VpxxGh` | ⚠️ Sodium 0.6+ 已内置 FRAPI，1.21.1 大概率**不需要** Indium（需实测） |
| **KubeJS** | — | — | ❌ **无 fabric 1.21.1 构建**（`2101.x` 全部只标 neoforge） |
| **Rhino** | `2101.2.7-build.85` | `cQ4POTah` | 有件但无 KubeJS 宿主则无意义 |
| Carpet-Fixes | — | — | ❌ 无 1.21.1 |
| StarLight | — | — | ❌ 无 1.21.1（1.21 起由原版/其他件覆盖） |

## 7 测试台改造清单（四线 → 五线）

| 位置 | 现状 | 改法 |
|---|---|---|
| `scripts/test/lib/mt_compat.py:49` `VERSIONS`、`:51 LOADER`、`:57 SUBPROJECT`、`:63 DEFAULT_PACK_MODS`、`:75 CONF_KEYS` | 四元组 | 加 `fabric-1.21.1`（loader=fabric，subproject 同名） |
| `scripts/test/lib/mt_paths.py:26 VERSIONS`、`:33 LOADER` | 四元组 | 同上 |
| `scripts/test/lib/mt_ps.py:49`、`:54-55 SUBPROJECT`、`mt_ime.py:37/:343/:347` | 四元组 / choices | 同上 |
| **`scripts/test/lib/mt_ps.py:61-62 _MARKER_ORDER`** | `("fabric-1.20.1","1.21.1","26.1.2","1.20.1")`，注释说明 fabric 必须先判否则被 `1.20.1` 吞 | **`fabric-1.21.1` 会被 `1.21.1` 吞** ⇒ 新串必须排在 `1.21.1` 之前；`mt_compat.py:748-752`、`:175/:189/:195/:242-251/:788` 六处字面量分支同理要泛化成 `fabric-*` |
| `scripts/test/fabric/astral_*_probe.js`（4 个探针） | **全部是 KubeJS 脚本** | 新线没有 KubeJS ⇒ 探针必须改纯 AutoJS 或 RCON（见 R1/R2） |
| `scripts/test/win/lib/common.sh:26 MT_ALL_VERSIONS`、`:38-41` subproject、`:48-50` loader、`:57-60` **java 主版本**、`:73` autojs | fabric-1.20.1→17 | 加分支；**fabric-1.21.1 → 21** |
| `scripts/test/win/lib/fabric.sh:30-31`（17 处 `fabric-1.20.1` 字面量） | 只认 1.20.1 | 泛化成 `fabric-*` 或加分支 |
| `launch.sh:55/65/132/148/220/273`、`phases.sh:148`、`live.sh:58`、`env.sh`/`stop.sh`/`mt.sh` | 字面量 | 同上 |
| `scripts/verify/verify_tag_parity.py`、`verify_dice_combat_attrless_guard_p1.py`、`verify_sign_registration.py`、`scripts/audit/tooltip_color_audit.py:44`、`scripts/devtools/unpack_nested_mod_jars.py:71-89` | 四线 | 加第五线 |
| `scripts/test/cases/*-fabric-1.20.1.json` | 一批 fabric 用例 | 克隆为 `*-fabric-1.21.1`（含 JSON 内 `"version"` 字段），按 1.21.1 语义复核断言 |

## 8 风险与缓解

| # | 风险 | 影响 | 缓解 |
|---|---|---|---|
| R1 | **KubeJS 无 fabric 1.21.1** | 依赖 KubeJS 的游戏内探针/用例在该线不可用 | ① 用例改纯 AutoJS/RCON；② 或明确「本线只做命令级断言」并记录差异（fabric.mod.json 不声明 kubejs） |
| R2 | **AutoJS 无 fabric-1.21.1 产物**（仓内 vendor 只有 forge-1.20.1 / fabric-1.20.1 / neoforge-1.21.1 / neoforge-26.1）；`temp/AutoJS-src` 已不在 | 没有 AutoJS 就没有 e2e 断言通道（`launch.sh --enable-autojs` 失效） | 方案 a：重新克隆上游 `github.com/MerrySnow404/AutoJS` 新增 `targets/fabric-1.21.1` 目标再发布 mavenLocal（约 1 个工作包）；方案 b：先用 RCON 回退（`common.sh` 已支持），e2e 延后。**需用户裁决** |
| R3 | Trinkets 3.10 / Accessories 1.1.0-beta 大版本跃迁 | 饰品兼容层（`compat/trinkets`、槽位尺寸、四态测试台）需重写 | 以 `ref/trinkets` 源码 + 3.10 jar 逐字节对比；先只保 Trinkets 单态，Accessories 延后 |
| R4 | NeoForge 21.1 的伤害管线（`LivingIncomingDamageEvent` + `DamageContainer`）在 Fabric 侧无等价物 | 9 处调用点的语义可能漂移（n21 的 `fixes/` 修补也依赖它） | 走 Puzzles Lib 21.1.66 + bridge mixin 派发；用与 1.21.1 neoforge 相同的数值用例做对照 |
| R5 | 1.21.1 数据组件体系（`DataComponents`/`components`） | `component/` 10 文件必须按 n21 重写，不能搬 fabric-1.20.1 的 NBT 版 | 蓝本直接用 n21；新线无历史存档包袱 |
| R6 | `@EventBusSubscriber` 自动扫描在 Fabric 侧不存在（B2） | 86 个文件要改显式注册，漏一个就「静默永不触发」 | 沿用 fabric-1.20.1 的 `SubscriptionAudit`（它就是为了审计「注册了但没派发」而存在的） |
| R7 | Loom 对 `fabric.mod.json` 的 `jars` 剥离（B10） | 内嵌依赖进 dev 后丢失 | 新线继续走 `tools/loom_embedded_jars.py` |
| R8 | 测试环境不能与 fabric-1.20.1 逐件对齐（KubeJS / Carpet-Fixes / StarLight / Indium 缺件） | 「同平台」承诺有折扣 | 在 TESTING-SPEC 记录该线独立清单 |
| R9 | 双 fabric 线并存 | `run/` 目录、端口、AutoJS 25585 互斥、stop 判据都要区分 | `fabric.sh` 泛化 `fabric-*` + `mt_compat` 分侧目录逻辑照 1.20.1 的修法复用 |
| R10 | 工作量被低估 | 半成品线进主线 | 用 P0–P8 的阶段判据卡口，**每阶段绿了才进下一阶段**；P4 之前不宣布「可用」 |

## 9 版本与治理口径

- 版本号：`mod_version=26.4-SNAPSHOT.<n>+fabric_1.21.1`，`<n>` 与其余四线同步递增（本仓纪律：全平台同号）。
- StarEngine：必须新增 `starengine-fabric-1.21.1` 产物（`lib_version=26.4-SNAPSHOT.<n>`），消费方 `starengine_version` 引脚 + `starengine_version_range=>=26.4-SNAPSHOT.<n> <27.0.0`（Fabric 谓词写法）。
- 门禁：`check_lang_sync.py`（新线三语键数一致）、`verify_tag_parity.py`、`check_changelog_parity.py` 全部要把第五线纳入。
- 治理文档：`porting/CROSS-VERSION-DIFFS.md` 增加「fabric-1.21.1 = P0 同侪（与 n21 同 MC 版本，代码蓝本 = n21，平台蓝本 = fabric-1.20.1）」并新增 F/B 混合差异段。

## 10 待拍板（4 问）

1. **AutoJS fabric-1.21.1**：现在就做（重克隆上游 + 新 target，约 1 个工作包，换来完整 e2e），还是本线先 RCON 回退、e2e 延后？
2. **starengine 第 5 平台**：确认要出 `starengine-fabric-1.21.1`（含 GeckoLib vendor 的 fabric 根）——这会把 starengine 仓的发布矩阵从 4 个变 5 个。
3. **饰品后端**：1.21.1 上 Trinkets 3.10 与 Accessories 1.1.0-beta 只保一个（建议 Trinkets），还是继续双后端四态测试台？
4. **发布范围**：fabric-1.21.1 是否立刻进发布矩阵（5 jar）与 CI，还是先 dev-only 稳定一段时间？

## 11 工作量估算（按文件级数字）

| 工作包 | 规模 | 说明 |
|---|---|---|
| 脚手架 + 构建 | ~6 文件 | 主要是把 1.21.1 依赖钉版与 Loom 配置写对 |
| 平台层 shim | ~85 类（其中 ~30 真新写） | 最大单块；network 4 类 / gui layer 3 类 / attachment / config / datagen / menu / condition |
| 业务代码 | 277 复制 + 63 改名 + 79 改写 | 47 个 network 文件只改 import |
| 资源 | 1348 文件 + 6 个配置件 | 蓝本 n21 |
| bridge mixin | ~15 个 | 按 FAPI 1.21.1 回调面重新对表（fabric-1.20.1 有 21 个可作参照） |
| starengine 第 5 平台 | ~10 文件 + GeckoLib fabric 根 | 含 vendored fabric 平台层 |
| 测试台 | ~200 处版本表 + 用例克隆 | 机械但有量 |
| 文档与门禁 | CROSS-VERSION-DIFFS / TESTING-SPEC / AGENTS | — |

**排序建议**：P0 → P1 → P2 是串行硬依赖；P3/P4 可与 P1 后半并行；P7（GeckoLib）应在 P4 之后（它需要 starengine 第五平台先能构建）；P8 收尾。

## 12 勘察子代理补充事实（2026-11-03 19:58 回报，与本文独立取证、互相印证）

派出的只读勘察子代理（全程未改仓、未构建、未起游戏）给出的**新增/订正**事实，已并入上文相应小节，此处留全量索引便于追溯：

### 12.1 版本事实（可直接当常量用）
| 项 | 值 | 出处 |
|---|---|---|
| MC 1.21.1 `pack_format` | `{"resource": 34, "data": 48}` | loom 缓存 1.21.1 client jar 内 `version.json` |
| MC 1.21.1 Java 要求 | `javaVersion.majorVersion = 21` | `mojang_minecraft_info.json` |
| Fabric Loader | `0.20.1`（stable）；intermediary `1.21.1` | meta.fabricmc.net |
| Fabric Loom | release **1.19.3** / latest 1.19-SNAPSHOT | maven-metadata.xml |
| Yarn | `1.21.1+build.3`（本仓用 Mojmap，不需要） | meta.fabricmc.net |

### 12.2 1.21.1 语义差的仓库内实物对照（grep 计数：neo / fabric）
| 特征 | neo-1.21.1 | fabric-1.20.1 |
|---|---|---|
| `ResourceLocation.fromNamespaceAndPath` | 121 | 1 |
| `new ResourceLocation(` | **0** | **99** |
| `.component(` | 12 | 0 |
| `DataComponents.` | 138 | 128（shim 内同名转发 `ItemDataKey`） |
| `StreamCodec` | 83 | 7 |
| `CustomPacketPayload` | 140 | 1 |
| `MapCodec` | 20 | 0 |
| `Holder<` | 335 | 42 |
| 网络载荷文件数 | 30 | 17 |

### 12.3 数据包目录对照（`data/astral_dice/`）
- neo：`advancement` / `curios` / `damage_type` / `enchantment` / `loot_modifiers` / `loot_table` / `patchouli_books` / `recipe` / `tags{item,point_of_interest_type}`
- fabric：`accessories` / `advancements` / `damage_type` / `loot_tables` / `patchouli_books` / `recipes` / `tags{items,point_of_interest_type}`
- 配方 result 对照：neo `recipe/amethyst_dice.json` = `{"count":1,"id":"astral_dice:amethyst_dice"}`；fabric `recipes/amethyst_dice.json` = `{"item":"astral_dice:amethyst_dice"}`；**两者的 ingredient 段都还是 `{"item":…}`**（id 化是 1.21.2+）。

### 12.4 探针通道被 KubeJS 绑死的实证
- `scripts/test/fabric/cases/FAB-Q2Q3-INVARIANTS.json` 的 steps 写明「装探针：`ft_env.ps1 --side server --install-probe astral_catchup_probe.js`」，读数载体是 Fabric API 的 FakePlayer。
- `scripts/test/fabric/astral_catchup_probe.js:10` 记载「KubeJS 各 server_scripts 共享全局作用域」⇒ 探针是 KubeJS 脚本。
- 结论：KubeJS 缺席 ⇒ `scripts/test/fabric/` 那 8 个 FAB-* 用例 + 4 个探针在新线**整条不可用**，不是「改个配置」能绕的。

### 12.5 四线硬编码的完整骨架（比 §7 更细）
- `mt_compat.py`：`VERSIONS:49`、`LOADER:51-56`、`SUBPROJECT:58-63`、`DEFAULT_PACK_MODS:65-70`（fabric 项 = `"1.20.1-Fabric 模组测试"`）、`CONF_KEYS:72-76` + `CONF_KEY_DEFAULT:77`；另有 **6 处把 `"fabric-1.20.1"` 当字面量的分支**：`175 resolve_side` / `189 run_root` / `195 run_label` / `242-251 process_markers`（追加 `fabric.dli.env=<side>`）/ `748-752 cmdline 识别`（devlaunchinjector、KnotClient）/ `788 stop 侧`。
- `mt_paths.py`：`VERSIONS:26`、`SUBPROJECT:27-32`、`LOADER:33`；`:36-39` 的 `RECIPES_PREFIXES` 已同时含 `recipes/` 与 `recipe/`（路径层已为 1.21 预留）。
- shell：`linux/env.sh:32/62-63/71-72/309`、`linux/launch.sh:61,95,101-102,138,149`、`linux/stop.sh:16,30`；`mt_env.py:60 GAMERULE_FILE_VERSION="26.1.2"`、`:848`。
- tools：14 个带 `LINES`/`SUBPROJECTS` 常量的脚本（`check_lang_sync.py:26`、`audit_actionbar.py:44`、`audit_mixin_injection.py:69`、`verify_effect_icons.py:52`、`gen_particle_library.py:23`、`tooltip_color_audit.py:44` 等），加 `fetch_shaderpacks.py:40-45 RUN_DIRS`、`loom_embedded_jars.py:80 SUBPROJECT`、`unpack_nested_mod_jars.py:71-89`、`verify_party_api.py:101`。
- CI：`.github/workflows/build.yml` 的 lang-sync `:130-143`（用 `hashFiles('fabric-1.20.1/gradle.properties')!=''` 守卫）、产物 `:187-206`、验证矩阵 `:225-228/:443-446`、Release glob `:400,434-436,457-466,488,558-559`。

### 12.6 子代理明确未确认（不要在实现期当成已知）
modernfix / ferrite-core / starlight / debugify / yacl / collective / superflat / conditional-mixin / mezzconfig 的 1.21.1 fabric 版本；JEI 19.x 是否仍硬依赖 mezzconfig；`ref/` 与 `ref/trinkets` 快照滞后于 1.21.1（`ref/fabric-api/gradle.properties:6` 是 1.20-pre7）⇒ **不能当 1.21.1 API 依据**；sodium/iris/indium 与 LWJGL 钉版在 1.21.1 的兼容组合；本机 `~/.minecraft/versions` 下是否已有 `1.21.1-Fabric` 整合包目录。

## 13 脚本引擎通道：AutoJS 与「KubeJS 缺位」的处置（2026-11-03 20:0x 实读）

> 本节回答一个问题：新线上「取数通道」用什么。原 fabric-1.20.1 线靠 **KubeJS 脚本 + AutoJS MCP**，而 1.21.1 的 fabric 侧**没有 KubeJS**。

### 13.1 AutoJS 上游现状（已 clone 到 `temp/AutoJS-src`，被 .gitignore 忽略）
- `git ls-remote`：上游只有一个分支 `main` = **`7138d72c`**（2026-10-19，"改包名"）；无 tag。
- 历史 7 个提交：`84f2c72` 初始 / `935457c` 启动期 MCP 端口覆盖 / `056f588` **加 Rhino 模式并扩展 target** / `3b45088` **扩到六个 loader/version target** / `7ccb73b` 清 AI 草稿 / `eb4d971` 扩测试指南 / `7138d72` 改包名。
- **上游有更新**：`7138d72` 把 `mod_group_id` 与全部 Java 包从 `cc.sighs.autojs` 改成 **`com.sighs.autojs`**（`gradle.properties:9` + 40 个文件的 package 行 + `targets/fabric-1.20.1/build.gradle`）。本仓现在用的是改名前那批 jar（mavenLocal 里是 `cc/sighs/autojs/...`）。
- 现有 target 六个：`fabric-1.20.1` / `forge-1.18.2` / `forge-1.19.2` / `forge-1.20.1` / `neoforge-1.21.1` / `neoforge-26.1` —— **没有 fabric-1.21.1**。

### 13.2 架构（`AGENT.md` 是权威口径，写得很硬）
- `common` = 无确证版本差异的共享源码树；`targets/<loader>-<mcver>` = 独立工程，通过 `includeBuild('../../common')` + `prepareCommonSources`（**Sync 任务**把 `../../common/src/main/java` 同步到 `$buildDir/generated/common-main` 并加入 main 源码集）把共享码编译进来，**不是** `project(':common')` 依赖。
- 版本差异的登记点是各 target `prepareCommonSources` 里的 **`exclude` 块**（某文件在该 target 有确证差异 ⇒ exclude，并由 target 提供实现）。
- 三条轴：**脚本引擎轴**（KubeJS 引擎 / Rhino 内核，绑死某一代 API）、**加载器轴**（mod 入口 + `AutoJSBackends` 门面）、**MC API 轴**（`mc/McApi` → `LoaderApi`/`VanillaApi`/`ClientApi` 三个接缝接口；接缝签名里**不得**出现只在某版本存在的类型，id 一律用 `"ns:path"` 文本传）。
- 根 `build.gradle` 里有 `targetBuilds` 映射表（键 = target 名，值 = 目录 + 任务名），新增 target 必须在这里登记；但 CI **不用**这个聚合（`docs/CI_TARGET_DISCOVERY.md` 是各自 wrapper 独立构建）。

### 13.3 决定性事实：Rhino 在 1.21.1 fabric 上是可用的
Modrinth `rhino`（project `sk9knFPE`）在 `game_versions=["1.21.1"]` 下 12 个版本，**loaders 含 fabric**：
`2101.2.7-build.81+Rhino-1.21` = **`ZdLtebKH`**（fabric/forge/neoforge 三平台同版）、`build.85` = `cQ4POTah`（三平台）、`build.91` = `SqkDvOLG`（**仅 neoforge**）。
- 而 `targets/neoforge-1.21.1/gradle.properties` 钉的正是 `rhino_version=2101.2.7-build.81` ⇒ **fabric-1.21.1 可以用同一行 Rhino**。
- 含义：AutoJS 的「**不装 KubeJS 就用自己的 Rhino 内核**」这条路径在 fabric-1.21.1 上成立 ⇒ **1.21.1 fabric 不是没有脚本能力，只是没有 KubeJS 的 API 面**。这一条直接改写了「必须用 CraftTweaker」的成本假设（见 §13.5）。

### 13.4 本仓侧已有的 AutoJS 接线（新增 target 时要一起改的点）
- 运行期注入：`fabric-1.20.1/build.gradle` 在 `-PautojsTestMod=1` 下以 **`modRuntimeOnly`** 注入，**永不进** `run/<side>/mods`（`scripts/test/win/lib/fabric.sh:160-200` 的注释写明制品是 intermediary 命名空间，手放进 mods 会 `NoSuchMethodError`）。
- 就绪判据：因为不进 mods，判据改成状态文件 `$side_dir/.mt_autojs_injected`（`mt_fab_autojs_state_write/read`，`fabric.sh:175-200`），内容含 `artifact=AutoJS-fabric-1.20.1-1.0.0.jar` 与 `source=mavenLocal:cc.sighs.autojs/...`。
- mavenLocal 分发：`scripts/test/tools/publish_autojs_maven.py`（`GROUP="cc.sighs.autojs"`、`VERSION="1.0.0"`、`ARTIFACTS` 每条 = id/loader/jar/build/ns/remap），源 jar 优先取 **vendor 副本** `scripts/test/resources/mods/autojs/`。
- 源码标注：`tools/check_mod_sources.py:77-80` 把 `cc.sighs.autojs` 登记为「AutoJS 实机测试库：本地 mavenLocal 分发，无 Curse/Modrinth 发布（GPL-3.0）」。
- ⇒ 新增 fabric-1.21.1 时，**组名从 `cc.sighs` 改到 `com.sighs` 会连带改**：`publish_autojs_maven.py`（GROUP + 目标目录 + pom 模板）、`fabric.sh:184` 的 `source=` 行、`check_mod_sources.py` 的登记表，以及 mavenLocal 里旧的 `cc/sighs` 目录是否保留。

### 13.5 对「KubeJS → CraftTweaker」假设的影响
- 事实上 CraftTweaker **确实有 fabric 1.21.1 构建**（project `Xg35A4rS`，1.21.1 共 72 个版本，最新 `21.0.38`，fabric id `TvJixPvk` / neoforge id `BVOSHJwc`）⇒ 用户的前提在「有没有这个模组」层面成立。
- 但它的脚本语言是 **ZenScript**（不是 JavaScript），且能力面以「数据/事件」为中心；而本仓探针要的是**任意读运行时状态 + 自持 tick 采样 + 写机器行**。这两者是否等价，由 `docs/features/autojs-fabric-1211-and-probe-migration.md` 的结论回答（B 路探查）。
- §13.3 给出第三条路：**AutoJS 原生脚本（Rhino 内核）**。若它成立，则新线探针不需要 CraftTweaker，且能保持「AutoJS 单一取数通道」的多平台一致性。

---

勘察：DSH（multi-dev-next），主会话独立实读 + 只读子代理交叉验证；快照 2026-11-03；所有数字均实读，漂移以命令输出为准。
