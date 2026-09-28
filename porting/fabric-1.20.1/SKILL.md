---
name: astral-dice-fabric-1.20.1-porting
description: 《星之骰戏》(astral_dice) forge-1.20.1 → fabric-1.20.1 移植执行手册——把 1.20.1 Forge 线移植为独立 fabric-1.20.1 线的完整可执行流程：前置库 starengine_lib 新增 fabric 平台（先行）、消费方新子项目按 P0~P7 阶段移植（骨架→注册→持久化→饰品→事件/网络→战利品/配置/GUI→mixin/datagen/联动→测试台/CI），命名映射为 Mojang 官方映射（原版名零改名），Curios→Trinkets 3.7.2、Capability/Attachments→Cardinal Components 5.2.3，含版本钉值、文件级任务清单、API 对照、验收判据与踩坑。适用于执行「把 astral_dice 移植到 1.20.1 Fabric」任务的其他大模型/工程师。
---

# astral_dice forge-1.20.1 → fabric-1.20.1 移植执行手册

> 执行前必读：本文是唯一执行入口。配套件（同目录）：`VERSION_PINS.md`（版本钉值，**先读**）、`API_CHEATSHEET.md`（API 对照）、`PORT_ANALYSIS.md`（分析全文）。
> 目标仓 worktree：`F://MCProject//astral_dice_multiloader_fabric`（分支 `1.20.1-fabric`）；库仓：`F://MCProject//starengine_lib`。
> ⚠️ 参考源在 worktree 的 `ref/`（fabric-loader / fabric-api / trinkets / cardinal-components-api / minecraftforge / yarn / patchouli），API 取证一律对照 `ref/` 源码，禁止凭记忆写 Fabric API。

## 0. 硬性约束（红线）

1. **命名映射 = Mojang 官方映射**（`loom.officialMojangMappings()`）——原版类/成员名与 forge 侧**完全一致**，禁改原版引用；只改加载器层（Curios/Forge 事件/Capability/SimpleChannel/GLM/DeferredRegister/mods.toml 等）。
2. **前置替换**：Curios → **Trinkets 3.7.2**（`dev.emi.trinkets.api`）；Capability/ModAttachments → **Cardinal Components 5.2.3**（`dev.onyxstudios.cca.api.v3`）。
3. **库先行**：`starengine_lib-fabric-1.20.1` 必须先于消费方 P0 完成 build + `publishToMavenLocal`（lib_version 四线同号 bump → **1.0.6**），消费方 CI 同提交改库 SHA。
4. **版本全部钉死**（见 VERSION_PINS.md）；fabric-loader **0.19.5**、fabric-loom **1.14.10**（wrapper Gradle 9.2.1 不带升）、fabric-api **0.92.12+1.20.1**、Java toolchain **17**。
5. **Loom 运行 JVM 21+、编译 toolchain 17** —— 跑 `gradlew` 用 `JAVA_HOME=C://Program Files\Zulu\zulu-21`；不要用 Java 17 跑 gradlew（Loom 会报错）。
6. **每阶段有可机读判据**，绿了才进下一阶段；🚨 任一阶段出现「换汤不换药的假绿」（编译绿但运行期静默失效）必须停下来取证。
7. 交付落点 = worktree `porting/fabric-1.20.1/`（⚠️ 仓内 `docs/` 被 gitignore）。
8. 主线 `multi-main` 零改动；不 push 除非用户另行指示。

## 1. 执行顺序（库先行 → 消费方 P0~P7）

### 1.0 前置：库 starengine_lib 新增 fabric 平台（先于消费方一切）

库仓 `F://MCProject//starengine_lib`：建分支 `fabric-1.20.1`（基线 `main @ 1.0.5`）+ worktree（可选）。新建子项目 `fabric-1.20.1/` 共 11 文件（common 44 文件**零改动**）：

| 文件 | 内容要点 |
|---|---|
| `fabric-1.20.1/build.gradle` | `id 'fabric-loom' version '1.14.10'` + Java 17 toolchain + `loom.officialMojangMappings()` + `modImplementation "net.fabricmc:fabric-loader:0.19.5"` + `sourceSets.main.java.srcDir('../common/src/main/java')` + 发布 **remapJar**（artifactId `starengine_lib-fabric-1.20.1`）；publishing mavenJava |
| `fabric-1.20.1/gradle.properties` | `lib_version=1.0.6`、`mod_version=1.0.6+fabric_1.20.1`（四线同号） |
| `src/main/resources/fabric.mod.json` | id `starengine_lib`；entrypoints.main；depends 仅 fabricloader/minecraft(+fabric-api)；**不声明 trinkets** |
| `StarEngineLib.java` | `implements ModInitializer`，`onInitialize` 内 `FabricEconomyStorage.install()` |
| `platform/LoaderEvent.java` | 空 abstract 类 |
| `platform/LoaderTags.java` | `TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation("c","bosses"))` + `EntityType#is` |
| `client/ActionBarManager.java` | float partialTick 变体（HUD 回调接线在消费方） |
| `event/ModEffectRemoval.java` | `remove(Player, MobEffect)`（照 forge 签名） |
| `economy/FabricEconomyStorage.java` | CCA 实体组件 + `RespawnCopyStrategy`；内层 NBT 保持 `starengine_lib/star_coin_wallet/{balance,name}`；事件 `CommandRegistrationCallback` + `ServerPlayerEvents.COPY_FROM`；离线读 .dat 的 `cardinal_components` 段 |
| `item/AstralRarities.java` | 方案 A：rare→RARE、epic→EPIC 映射原版常量 + 库 `Rarity` 颜色权威；`tierOf` 读物品 NBT 档位 |
| `item/TrinketsCompat.java` | 3 方法等价物（对齐 `CuriosCompat`）；`modCompileOnly` trinkets；库不声明前置 |
| `component/ItemDataKey.java` | 照 forge 版逐字复制 |

→ `./gradlew :fabric-1.20.1:build` + `./gradlew :fabric-1.20.1:publishToMavenLocal` → 四线 CHANGELOG 段头 + 提交 → 消费方 CI 同提交改库 SHA。

### 1.1 消费方 P0 骨架

1. 复制 `forge-1.20.1/` 为 `fabric-1.20.1/`（源码起步），`settings.gradle` 加 `include 'fabric-1.20.1'`。
2. 写 `fabric-1.20.1/build.gradle`（`id 'fabric-loom' version '1.14.10'` + Java 17 + `loom.officialMojangMappings()` + 依赖：loader 0.19.5 / fabric-api 0.92.12+1.20.1 / trinkets 3.7.2 / cca 5.2.3 / `include` 内嵌 starengine_lib-fabric jar + 两个 `exclusiveContent` + `generateModMetadata`(模板改 fabric.mod.json) + pushTo* 三件套后缀 `+fabric_1.20.1`）。
3. 写 `fabric-1.20.1/gradle.properties`（`mod_version=<base>+fabric_1.20.1` + 库三键 starengine_lib_version=1.0.6 / group / version_range=[1.0.6,2.0)）。
4. 写 `src/main/resources/fabric.mod.json`（entrypoints.main/client；depends：fabricloader ≥0.19.5、minecraft ~1.20.1、fabric-api、trinkets ≥3.7.2、cardinal-components-base ≥5.2.3、starengine_lib ≥1.0.6；mixins 数组）。
5. 入口类改 `implements ModInitializer`/`ClientModInitializer`；**删除** `init/MixinRuntimeGate.java` 与 mods.toml 模板/`mixinbooster` 依赖。
6. **最小 mixin 端到端验证**（R3）：保留一个最小 mixin（如 `EntityThunderHitMixin`），`defaultRefmapName="astral_dice.refmap.json"` → `remapJar` → 解包确认 refmap 嵌入 → `runServer` 到 "Done" 无 mixin 报错。
7. ⚠️ client 段 mixin：把 `mixin/client/**` 拆成独立 `astral_dice.client.mixins.json` 并在 fabric.mod.json 的 `entrypoints.client`/`"environment": "client"` 处理（模板见 §4 踩坑 R9）。
8. **判据**：`:fabric-1.20.1:build` 绿 + `runServer` "Done" + remapJar refmap 嵌入。

### 1.2 P1 注册 + 物品

- 写 `ModRegistries` 薄包装（内部 `Registry.register(Registries.X, id, obj)`）；`ModItems`(137)/`ModEffects`(45)/`ModSounds`(12)/`ModParticles`/`ModMenuTypes`/`ModRecipeSerializers`/`ModCreativeTabs`/`ModEnchantments` 的 DeferredRegister 逐一改走包装层（声明面零改动）；创造栏 → `FabricItemGroup`；菜单 → `ScreenHandlerRegistry`；实体属性 → `FabricDefaultAttributeRegistry`。
- 判据：编译绿 + `/give` 抽查 5 物品 + 创造栏可见。

### 1.3 P2 持久化 + 经济

- `component/AstralComponents`：`ComponentRegistryV3.INSTANCE.getOrCreate` + `EntityComponentInitializer.register`；**按域 8~12 组件**（dice/chips/signs/economy/cards/cooldowns/effects/misc），需同步者 `AutoSyncedComponent`；逐组件显式 `RespawnCopyStrategy`（对齐 forge 侧 5 处 PlayerEvent.Clone）。
- getPersistentData ×4 处按域归 CCA；钱包用库的 `FabricEconomyStorage`。
- 判据：存钱→kill→重生余额在；重进在；离线 .dat 布局核验；**108 键抽样**。

### 1.4 P3 饰品（Trinkets）

- `compat/trinkets/TrinketSlotUtil`（对齐 forge 侧 `CurioSlotUtil` 签名面）；基类 `DiceCurioItem/BaseChipItem/BaseSignItem` 改实现 `dev.emi.trinkets.api.Trinket`（回调映射 `curioTick→tick`、`onEquip/onUnequip/canEquip/canUnequip` 同名）。
- 槽位改数据包：`data/trinkets/slots/<group>/dice.json`、`stand.json`、`chip.json` + `group.json` + `data/trinkets/entities/astral_dice.json`；`curios:stand/chip` 手写标签对应物 = Trinkets 的 `data/trinkets/tags/...`。
- 判据：槽位 UI 可见 + 装备/卸下回调触发。

### 1.5 P4 事件 + 战斗 + 网络

- 事件按 `API_CHEATSHEET.md §3` + `PORT_ANALYSIS.md §4.2` 逐事件改写；无 FAPI 对应者（10~15 种）自写 mixin（注入点列在 §5.1）或自定义回调。
- 自定义事件 `SignActiveTriggeredEvent` 派发改消费方直调订阅点。
- `network/` 改 `ServerPlayNetworking`/`ClientPlayNetworking`（1.20.1 channel-handler + PacketByteBufs，**无 PayloadTypeRegistry**）；12 消息逐一映射；VersionGate 重写为握手包内嵌协议版本常量比对。
- 判据：测试台冒烟用例过（**此时接入** Mt.Paths.psm1 的 fabric 分派）。

### 1.6 P5 战利品 + 配置 + GUI

- 战利品：`LootTableEvents.MODIFY` + LootPool；13 JSON 平移；删 GLM serializer/DeferredRegister；自有命名空间早退判据保留在回调内。
- 配置：保留 TOML 自实现（NightConfig）；Cloth 仅可选 GUI。
- GUI：容器 → ScreenHandlerRegistry + HandledScreens；钱包按钮 → `ScreenEvents.AFTER_INIT`；HUD → `HudRenderCallback`（1.20.1 参数 MatrixStack）。
- 判据：首箱赠礼用例、配置重载、GUI 打开。

### 1.7 P6 mixin + datagen + 联动

- 18 mixin 全部迁移（零改名）；`astral_dice.mixins.json` + client 段独立 json 注册进 fabric.mod.json。
- datagen：`FabricDataGenEntrypoint` + 等价 Provider；产物与 forge 侧 `src/generated` 比对。
- 联动：Patchouli（Fabric 变体，书籍资源零改动）、KubeJS/JEI/Jade/ModernFix/FerriteCore 用 fabric 变体；运行环境 Sodium/Iris/fabric-carpet。
- 判据：remapJar refmap 嵌入 + datagen 产物比对 + 联动件加载。

### 1.8 P7 测试台 + CI + 收口

- `Mt.Paths.psm1` 加 fabric 分派（版本键 `1.20.1-fabric`）；`mt.conf.example`；新建 `scripts/verify/verify_fabric_loader_gate.ps1`（对标 forge 版）。
- CI build.yml：branches + JDK17 + lang 第四步 + artifact/Release JARS 加 fabric jar；库 checkout ref 钉库 fabric 版 SHA。
- `AGENTS.md`/`README(_ZH)`/双 `CHANGELOG` 更新。
- 判据：全套件绿 + CI 绿。

## 2. 版本钉值（照抄 VERSION_PINS.md 核心）

| 项 | 钉值 |
|---|---|
| fabric-loader | 0.19.5 |
| fabric-loom | 1.14.10（Gradle 9.2 带内；**勿升 1.16+** = 需 wrapper 9.4+） |
| fabric-api | 0.92.12+1.20.1 |
| Trinkets | 3.7.2 |
| Cardinal Components API | 5.2.3 |
| StarEngine Lib (fabric) | 1.0.6（先行 publishToMavenLocal） |
| Java toolchain | 17（**gradlew 用 JAVA_HOME=21**） |

## 3. 关键 API 对照（详见 API_CHEATSHEET.md）

| Forge | Fabric |
|---|---|
| `@Mod` + mods.toml | `ModInitializer` + fabric.mod.json |
| DeferredRegister ×9 | `Registry.register` + `ModRegistries` 包装 |
| 125 @SubscribeEvent | FAPI 回调 / 自写 mixin / 自定义回调 |
| Capability + ModAttachments(108) | CCA ComponentV3 + RespawnCopyStrategy |
| SimpleChannel 12 | ServerPlayNetworking/ClientPlayNetworking（channel-handler） |
| GLM 战利品 | LootTableEvents.MODIFY |
| Curios（ICurioItem/IMC 槽） | Trinkets（Trinket/数据包槽） |

## 4. 踩坑清单（必须遵守）

| # | 坑 | 规避 |
|---|---|---|
| K1 | 用 Yarn 命名 ⇒ 全灭 | 恒用 `loom.officialMojangMappings()` |
| K2 | 用 Java 17 跑 gradlew | Loom 需 JVM 21+；`JAVA_HOME=C://Program Files\Zulu\zulu-21` 跑 gradlew，toolchain 17 编译 |
| K3 | loom ≥1.16 需 Gradle 9.4+ | 钉 `fabric-loom:1.14.10`；勿动 wrapper 9.2.1 |
| K4 | 1.20.1 网络用 PayloadTypeRegistry | 那是 1.20.5+；1.20.1 用 `registerGlobalReceiver(Identifier, PlayChannelHandler)` + PacketByteBufs |
| K5 | command 用 v1（deprecated） | 用 `fabric-command-api-v2` 的 `CommandRegistrationCallback` |
| K6 | mixin client 段塞同一个 json | client 段拆 `astral_dice.client.mixins.json` + `entrypoints.client`/`"environment": "client"` |
| K7 | 库未 publishToMavenLocal 就构建消费方 | 顺序：库 build+publish → 消费方钉 1.0.6 → 再构建 |
| K8 | CCA 默认不复制死亡数据 | 逐组件显式 `RespawnCopyStrategy` |
| K9 | remapJar 无 refmap ⇒ 生产环境 mixin 崩 | P0 即验证 remapJar 解包有 refmap（`defaultRefmapName`） |
| K10 | 交付物写进 `docs/`（被 gitignore） | 写 `porting/fabric-1.20.1/` |
| K11 | 凭记忆写 Fabric API | 对照 `ref/fabric-api`、`ref/trinkets`、`ref/cardinal-components-api` 源码 |
| K12 | 换汤不换药的假绿（编译绿运行静默失效） | 每阶段跑运行判据（启动/物品/余额/槽位/战利品），不只编译 |

## 5. 附录

### 5.1 无 FAPI 对应事件的 mixin 注入点候选（P4 用）

| Forge 事件 | mixin 注入点（Mojmap） |
|---|---|
| LivingHealEvent | `LivingEntity.heal(float)` |
| LivingKnockBackEvent | `LivingEntity.knockback(...)` |
| EntityTeleportEvent | `Entity.teleportTo(...)` |
| ItemCrafted 变体 | `ResultSlot.onTake(...)` |
| （执行时按 PORT_ANALYSIS §4.2 补全） | — |

### 5.2 Trinkets 槽位 JSON 布局（P3 用）

- `data/trinkets/slots/<group>/group.json`（槽组定义：icon、order 等）
- `data/trinkets/slots/<group>/dice.json` / `stand.json` / `chip.json`（单槽：amount、validator 标签）
- `data/trinkets/entities/astral_dice.json`（把实体挂到槽组）
- 对应物核对：forge 侧 `data/curios/slots/**` 与 `data/curios/tags/item/{stand,chip}.json` ⇒ Trinkets 的 `data/trinkets/tags/**`

### 5.3 裁剪（无 Fabric 1.20.1 版）

- Iron's Spells 'n Spellbooks、Bountiful —— 联动整段删除；Embeddium/Oculus → Sodium/Iris（仅运行环境）；neoforge-carpet → fabric-carpet。

### 5.4 删除清单

- `init/MixinRuntimeGate.java`（Connector 门控）、mods.toml 的 `mixinbooster` 依赖、mods.toml/pack.mcmeta 模板（Forge 专属）、enumextensions 相关概念。

---

> 执行完成判据 = `PORT_ANALYSIS.md` §10 验收判据汇总全绿。执行中遇到的口径分叉（无 FAPI 事件映射、稀有度方案、组件粒度细节）以 `PORT_ANALYSIS.md` §4~§6 为准。
