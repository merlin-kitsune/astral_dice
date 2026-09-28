# 《星之骰戏》forge-1.20.1 → fabric-1.20.1 移植分析报告

> 版本：2026-09-29（分析定稿）。目标读者 = 后续执行移植的大模型/工程师。
> 配套件：`VERSION_PINS.md`（版本钉值）、`API_CHEATSHEET.md`（API 差异对照）、`SKILL.md`（可执行移植 SKILL）。
> 分析基准：消费方 `forge-1.20.1`（包 `com.merlinkitsune.astral_dice`，254 个 .java，版本 `1.3.2-hotfix+forge_1.20.1`）+ 前置库 `starengine_lib`（common 44 文件 + forge 平台 9 文件）。
> 参考源：`ref/` 已克隆 fabric-loader / fabric-api(1.20) / trinkets(1.20.1) / cardinal-components-api(1.20) / minecraftforge(1.20.x) / yarn(1.20.1) / patchouli(1.20.x)。

---

## 1. 目标与基线

以 1.20.1 Forge 线为基准，移植出一条独立的 `fabric-1.20.1` 线（Fabric Loader + Fabric API + Trinkets + Cardinal Components API）。基线：

- 分支：`1.20.1-fabric`（基线 `multi-main @ 34e8f7cf`）；worktree `F://MCProject//astral_dice_multiloader_fabric`。
- 命名映射：**Mojang 官方映射**（`loom.officialMojangMappings()`；用户裁决）⇒ 原版类/成员名两平台一致，254+44 文件与 18 mixin **近乎零改名**。
- 前置替换：**Curios → Trinkets 3.7.2**、**Capability/Attachments → Cardinal Components API 5.2.3**、其余第三方依赖按 `VERSION_PINS.md` 的 fabric 变体。
- Patchouli 手册**纳入首版**（用户裁决）。
- 交付物落点 = 仓内 `porting/fabric-1.20.1/`（⚠️ 仓内 `docs/` 被 gitignore）。

## 2. 版本钉值总表

见 `VERSION_PINS.md`。核心：fabric-loader **0.19.5**、fabric-loom **1.14.10**（wrapper Gradle 9.2.1 兼容带的最高线）、fabric-api **0.92.12+1.20.1**、Trinkets **3.7.2**、CCA **5.2.3**、Java toolchain **17**（Loom 运行 JVM 需 21）。

## 3. 总体移植方法与阶段划分

方法 = **「骨架先行、分层替换、逐阶段验收」**：先让骨架编译并空跑，再按「注册 → 持久化 → 饰品 → 事件/网络 → 战利品/配置/GUI → mixin/datagen/联动 → 测试台/CI」推进，每阶段有可机读的验收判据（详见 §10）。

| 阶段 | 内容 | 验收判据 |
|---|---|---|
| **P0 骨架** | 子项目、loom、fabric.mod.json、入口、`ModRegistries` 空壳、**最小 mixin 端到端验证** | `:fabric-1.20.1:build` 绿；`runServer` 到 "Done"；remapJar 解包确认 refmap 嵌入 + 生产启动无 mixin 报错 |
| **P1 注册+物品** | 9 注册面、137 物品、45 效果、12 音效、创造栏 | 编译绿；`/give` 抽查 5 物品；创造栏可见 |
| **P2 持久化+经济** | CCA 组件（8~12 域）+ 钱包（库 FabricEconomyStorage） | 存钱→kill→重生余额在；重进在；离线 .dat 布局核验；108 键抽样 |
| **P3 饰品** | Trinkets 三槽（dice/stand/chip）+ 基类 + CurioSlotUtil→TrinketSlotUtil | 槽位 UI 可见；装备/卸下回调触发 |
| **P4 事件+战斗+网络** | 事件改写（对照表 §4.2）、12 消息、战斗链 | 测试台冒烟用例过（此时接入 Mt.Paths fabric 分派） |
| **P5 战利品+配置+GUI** | Loot 注入、TOML 配置、容器/HUD/按钮 | 首箱赠礼用例、配置重载、GUI 打开 |
| **P6 mixin+datagen+联动** | 18 mixin、datagen、Patchouli/JEI/Jade 等 | datagen 产物与 forge 侧比对；联动件 Fabric 变体加载 |
| **P7 测试台+CI+收口** | Mt.Paths、verify_fabric_loader_gate、CI、AGENTS/README/CHANGELOG | 全套件绿 + CI 绿 |

## 4. Forge↔Fabric API 差异对照

主对照见 `API_CHEATSHEET.md`（9 大类）。以下为「逐事件」对照（约 40 种；FAPI 无对应者标注处理方式）。

### 4.1 五大机制对照（速览）

| 机制 | Forge | Fabric | 章节 |
|---|---|---|---|
| 入口/元数据 | `@Mod` + mods.toml | `ModInitializer`/`ClientModInitializer` + fabric.mod.json | AC §1 |
| 注册 | DeferredRegister ×9 | `Registry.register` + `ModRegistries` 薄包装 | AC §2 |
| 事件 | 125 @SubscribeEvent + 66 @EventBusSubscriber | FAPI 回调 / 自写 mixin / 自定义回调 | AC §3、本 §4.2 |
| 持久化 | Capability + ModAttachments(108 键) + getPersistentData | **CCA 组件（8~12 域）+ RespawnCopyStrategy** | AC §4 |
| 网络 | SimpleChannel 12 消息 + VersionGate | ServerPlayNetworking/ClientPlayNetworking（1.20.1 channel-handler 形态，**无 PayloadTypeRegistry**）+ 自写握手 | AC §5 |

### 4.2 逐事件对照（约 40 种；节选高频/关键项）

| Forge 事件 | Fabric 对应 / 处理 |
|---|---|
| ServerTickEvent / LevelTickEvent | `ServerTickEvents.START/END_SERVER_TICK`、`START/END_WORLD_TICK` |
| LivingDamageEvent / LivingHurtEvent | `ServerLivingEntityEvents.ALLOW_DAMAGE`（可取消） |
| LivingDeathEvent | `ServerLivingEntityEvents.AFTER_DEATH` |
| LivingHealEvent | **无** ⇒ 自写 mixin（注入 `LivingEntity.heal`）或改在 `ALLOW_DAMAGE` 阴性侧处理 |
| LivingKnockBackEvent | **无** ⇒ 自写 mixin（`LivingEntity.knockback`） |
| EntityJoinLevelEvent | `ServerEntityEvents.ENTITY_LOAD` |
| PlayerEvent.Clone ×5 | `ServerPlayerEvents.COPY_FROM` |
| PlayerLoggedIn/Out | `ServerPlayConnectionEvents.JOIN/DISCONNECT` |
| PlayerRespawn | `ServerPlayerEvents.AFTER_RESPAWN` |
| RegisterCommandsEvent | `CommandRegistrationCallback`（v2） |
| LootTableLoadEvent / GLM | `LootTableEvents.MODIFY` |
| RightClickItem / RightClickBlock / EntityInteract | `UseItemCallback` / `UseBlockCallback` / `UseEntityCallback` |
| ItemTooltipEvent（提示框染色/边框） | `ItemTooltipCallback.EVENT`（⚠️ 奇特彩虹边框在 tooltip 回调内自绘，与 forge 侧 `TooltipBorderMixin` 等价 mixin 方案另议；第三方 tooltip 模组整管边框的坑在 Fabric 生态同样存在） |
| ClientTickEvent | `ClientTickEvents.START/END_CLIENT_TICK` |
| RenderLevelStageEvent | `WorldRenderEvents`（fabric-rendering-v1） |
| EntityAttributeModificationEvent | `FabricDefaultAttributeRegistry` |
| RegisterCapabilitiesEvent + AttachCapabilitiesEvent | **CCA**（见 AC §4） |
| FMLCommonSetupEvent | `onInitialize`（主体）；需在其它 mod 之后者用 entrypoint 顺序/`mod entrypoint` |
| RegisterClientReloadListenersEvent | `ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(...)` |
| RegisterParticleProvidersEvent | `ParticleFactoryRegistry` |
| 其余少数（PlayerEvent.ItemCraftedEvent 变体、EntityTeleport、BlockEvent 等） | 无 FAPI 对应 ⇒ 自写 mixin（注入点逐一列入 SKILL 的事件-mixin 清单）或自定义接口 |

## 5. 各功能模块迁移方案（forge-1.20.1 → fabric-1.20.1）

新子项目 `fabric-1.20.1/` 复制自 `forge-1.20.1/` 起步，按下表逐模块改写。命名映射为 Mojmap ⇒ 原版引用零改名，只改加载器层。

### 5.1 饰品（Curios → Trinkets；~88 文件触点）

- **适配层**：新建 `compat/trinkets/` 包，写 `TrinketSlotUtil`（对齐 forge 侧 `CurioSlotUtil` 的方法签名面，尽量让调用点零改动）。
- **基类**：`DiceCurioItem` / `BaseChipItem` / `BaseSignItem` 由实现 `top.theillusivec4.curios.api.type.capability.ICurioItem` 改为实现 `dev.emi.trinkets.api.Trinket`；回调逐一对齐：`curioTick→tick`、`onEquip→onEquip`、`onUnequip→onUnequip`、`canEquip→canEquip`、`canUnequip→canUnequip`（签名 `(ItemStack, SlotReference, LivingEntity)`）。
- **槽位定义**：由 IMC（`SlotTypeMessage`）改为数据包：`data/trinkets/slots/<group>/dice.json`、`stand.json`、`chip.json` + `group.json` + `data/trinkets/entities/astral_dice.json`（引用实体 + 槽组）；`curios:stand`/`curios:chip` 的手写标签对应物 = Trinkets 的 `data/trinkets/tags/...`（核对 forge 侧 `data/curios/tags/**`）。
- **访问组件**：`TrinketsApi.getTrinketComponent(LivingEntity): Optional<TrinketComponent>`（`getInventory(String)` 取槽组）。
- **源文件 → 目标文件**：`item/base/*Curio*` → `compat/trinkets/` 下对应类；`util/CurioSlotUtil` → `util/TrinketSlotUtil`；入口 IMC 段删除，改数据包。
- **Trinkets 运行版**：Trinkets 在 1.20.1 内置了 TrinketComponent（CCA 实现）与内置 UI；玩家仍可用 Curios 布局配置包获得相同 UI（选项）。

### 5.2 事件（125 @SubscribeEvent / ~40 种）

- 按 §4.2 对照逐事件改写；无 FAPI 对应者（预估 10~15 种）自写 mixin（注入点候选逐一列入 SKILL 的清单）或自定义回调接口。
- 自定义事件 `SignActiveTriggeredEvent`（库 LoaderEvent 子类）：Fabric 侧库 `LoaderEvent` 为空类，事件派发改由消费方直接调用订阅点（包一层 Fabric 回调或直接 method call）。

### 5.3 持久化（Capability + ModAttachments 108 键 → CCA）

- 注册 `component/AstralComponents`（`ComponentRegistryV3` + `EntityComponentInitializer`）。
- **粒度 = 按域 8~12 组件**（dice/chips/signs/economy/cards/cooldowns/effects/misc 等），与 forge 侧 `AttachedDataKey` 的域注释对应；需同步者实现 `AutoSyncedComponent`。
- 死亡保留：逐组件显式 `RespawnCopyStrategy`（对齐 forge 侧 5 处 PlayerEvent.Clone 的复制语义）。
- getPersistentData ×4 处按域归入 CCA 组件；**钱包**走库的 `FabricEconomyStorage`（见 §6）。

### 5.4 配置（ForgeConfigSpec 16 项 + 备份）

- **保留 TOML 自实现**（NightConfig 在 Fabric 可用）：`config/` 包的读取/备份/CONFIG_VERSION 逻辑近平移；Cloth Config 仅作可选游戏内 GUI 集成。

### 5.5 网络（SimpleChannel 12 消息 + VersionGate）

- `network/` 全部改写为 `ServerPlayNetworking`/`ClientPlayNetworking`（channel-handler 形态 + PacketByteBufs）；12 条消息逐一映射。
- VersionGate（SimpleChannel + DisplayTest）重写为握手包内嵌协议版本常量比对。

### 5.6 战利品（GLM add_table + 13 JSON + 2 handler）

- `loot/` 改 `LootTableEvents.MODIFY` + `LootPool`；13 个 loot JSON 平移；GLM serializer/DeferredRegister 删除；自有命名空间早退判据在 MODIFY 回调内保留。

### 5.7 注册面（9 个 DeferredRegister）

- 薄包装层 `ModRegistries`（内部 `Registry.register(Registries.X, id, obj)`），保持 `ModItems`（137）/`ModEffects`（45）/`ModSounds`（12）/`ModParticles`/`ModMenuTypes`/`ModRecipeSerializers`/`ModCreativeTabs`/`ModEnchantments`/GLM 的声明面零改动；创造栏 → `FabricItemGroup`；菜单 → `ScreenHandlerRegistry`；实体属性 → `FabricDefaultAttributeRegistry`。

### 5.8 Mixin（18 个）

- Mojmap 零改名；`fabric.mod.json` 注册 `astral_dice.mixins.json`；`defaultRefmapName="astral_dice.refmap.json"`；**P0 先做最小 mixin 端到端验证**（R3）。
- 注意 forge 侧有 `client/` 子包 mixin（client 配置段）——fabric.mod.json 的 mixins 数组不分双端，需把 client 段拆成独立 json 并在 `entrypoints.client` 段/自定义 `environment` 处理（⚠️ 细节列入 SKILL）。

### 5.9 屏幕/容器/HUD

- `screen/` 容器 → ScreenHandlerRegistry + HandledScreens；钱包 HUD 按钮 → `ScreenEvents.AFTER_INIT`；HUD → `HudRenderCallback`；注意叠加 GUI 三红线的 Fabric 等价（active 标记、当前标签页判定、按钮坐标）。

### 5.10 datagen

- `FabricDataGenEntrypoint` + 等价 Provider（模型/配方/语言）；`runDatagen` 任务；产物与 forge 侧 `src/generated` 比对。

### 5.11 联动

- Patchouli 纳入（Fabric 变体，书籍资源零改动）；Bountiful/Iron's Spells 裁剪；KubeJS/JEI/Jade/ModernFix/FerriteCore 用 fabric 变体；Embeddium/Oculus→Sodium/Iris（仅运行环境）；neoforge-carpet→fabric-carpet。

### 5.12 删除清单

- `init/MixinRuntimeGate.java`（Connector 门控）、mods.toml 的 `mixinbooster` 依赖、enumextensions 相关（NeoForge 线独有，1.20.1 forge 本就无 enumextensions；本条主要指概念对齐）、Forge 专属模板 `mods.toml`/`pack.mcmeta`。

## 6. 前置库 starengine_lib 新增 fabric-1.20.1 平台（跨仓硬前置，先于消费方 P0）

库仓 `F://MCProject//starengine_lib`（基线 `main @ 1.0.5`）。库侧也建 `fabric-1.20.1` 分支 + worktree（执行时）；`lib_version` 四线同号 bump → **1.0.6** → build + `publishToMavenLocal` → 消费方钉值 + CI SHA 同提交更新。

**新建平台子项目 11 文件**（common 44 文件**零改动**）：

| 文件 | 要点 |
|---|---|
| `fabric-1.20.1/build.gradle` | fabric-loom **1.14.10** + Java 17 toolchain + **`loom.officialMojangMappings()`** + loader 0.19.5 + `sourceSets.main.java.srcDir('../common/src/main/java')` + 发布 **remapJar**（intermediary；artifactId `starengine_lib-fabric-1.20.1`） |
| `fabric-1.20.1/gradle.properties` | `lib_version`/`mod_version=...+fabric_1.20.1`（四线同号 1.0.6） |
| `src/main/resources/fabric.mod.json` | id `starengine_lib`；entrypoints.main；depends 仅 loader/minecraft(+fabric-api)；**不声明 trinkets 前置** |
| `StarEngineLib.java` | `implements ModInitializer`，`onInitialize` 内 `FabricEconomyStorage.install()` |
| `platform/LoaderEvent.java` | 空 abstract 类（Fabric 无事件基类；派发留消费方） |
| `platform/LoaderTags.java` | `TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation("c","bosses"))` 自构造 + `EntityType#is` |
| `client/ActionBarManager.java` | float partialTick 变体（HUD 回调接线在消费方） |
| `event/ModEffectRemoval.java` | `remove(Player, MobEffect)`（照 forge 签名） |
| `economy/FabricEconomyStorage.java` | **D2=CCA**（实体组件 + `RespawnCopyStrategy`；内层 NBT 布局保持 `starengine_lib/star_coin_wallet/{balance,name}`）；事件 = `CommandRegistrationCallback` + `ServerPlayerEvents.COPY_FROM`；离线读 .dat 的 `cardinal_components` 段 |
| `item/AstralRarities.java` | **D1=方案 A**：映射原版常量（rare→RARE、epic→EPIC；传奇/巅峰/奇特按约定映射）+ 库 `Rarity` 仍为颜色权威；`tierOf` 输入源改物品 NBT 档位（而非反查原版枚举） |
| `item/TrinketsCompat.java` | 3 方法等价物（对齐 `CuriosCompat` 签名面）；`modCompileOnly` trinkets；库不声明前置 |
| `component/ItemDataKey.java` | 照 forge 版逐字复制（1.20.1 无 DataComponent ⇒ NBT+Codec shim，纯 vanilla+Codec） |

**裁决**：D1 = 方案 A（映射原版常量 + 库 Rarity 颜色权威）；D2 = Cardinal Components（用户指定）。

## 7. 构建与工程接入（消费方 fabric-1.20.1 子项目）

1. `settings.gradle`：`include 'fabric-1.20.1'`（本分支四线超集，主线不污染；pluginManagement 按需加 `maven.fabricmc.net`）。
2. `fabric-1.20.1/build.gradle`：`plugins { id 'fabric-loom' version '1.14.10' }` + Java 17 toolchain + `loom.officialMojangMappings()` + 依赖（fabric-loader 0.19.5 / fabric-api 0.92.12+1.20.1 / trinkets 3.7.2 / cca 5.2.3 / `include` 内嵌 starengine_lib-fabric jar，替代 jarJar）+ 依赖解析两个 `exclusiveContent`（照 forge）+ `generateModMetadata`（模板改 `fabric.mod.json`）+ pushTo* 三件套（后缀 `+fabric_1.20.1`，packPushBranches 口径与主线对齐）。
3. `fabric-1.20.1/gradle.properties`：`mod_version` 后缀 `+fabric_1.20.1` + 库三键（starengine_lib_version=1.0.6 / group / version_range=[1.0.6,2.0)）。
4. `src/main/resources/fabric.mod.json`：depends 区间（fabricloader ≥0.19.5、minecraft ~1.20.1、fabric-api、trinkets ≥3.7.2、cardinal-components-base ≥5.2.3、starengine_lib ≥1.0.6）。
5. CI `.github/workflows/build.yml`：push branches 加 `1.20.1-fabric`；JDK 17 复用（⚠️ loom 运行 JVM 21 与 toolchain 17 分离）；lang sync 加第四步；upload-artifact 与 Release JARS 加 `fabric-1.20.1/build/libs/astral_dice-*.jar`；库 checkout ref 钉库 fabric 版 SHA。
6. 测试台：`scripts/test/lib/Mt.Paths.psm1` 加 fabric 分派（VERSIONS/LOADER/SUBPROJECT/DEFAULT_PACK_MODS + conf 键，版本键建议 `1.20.1-fabric`）；`mt.conf.example` 同步；新建 `scripts/verify/verify_fabric_loader_gate.ps1`（对标 forge 版，判 fabric.mod.json depends 门槛 + 库三键 + 门控在位）。
7. `AGENTS.md`（矩阵四线、工作树三树、部署表、依赖口径）、`README(_ZH)`、双 `CHANGELOG` 新段。
8. `tools/check_mod_sources.ps1` 核验（$LibraryGroups 已含 net.fabricmc）。

## 8. 风险与应对

| # | 风险 | 应对 |
|---|---|---|
| R1 | 交付文档被 gitignore（仓内 `docs/`） | 落 `porting/`（本报告已规避） |
| R2 | loom 1.14.10 与 Gradle 9.2.1 兼容性（理论兼容，未实跑） | P0 先跑 `gradlew help`/最小骨架终验；失败则按官方发布页在 9.2 带内另选；**loom 运行 JVM 21 / toolchain 17 分离**写进 SKILL 踩坑 |
| R3 | Mojmap 下 mixin refmap 生产链（本仓首次 loom 下 remap） | P0 即做最小 mixin 端到端验证（remapJar 解包查 refmap + 生产启动），不拖到 P6 |
| R4 | CCA respawn 复制粒度与 forge PlayerEvent.Clone 语义差 | 逐组件显式 `RespawnCopyStrategy`；P2 验收用例含死亡重生 108 键抽样 |
| R5 | FAPI 无对应事件（预估 10~15 种） | §4.2 逐事件三选一（FAPI 回调/自写 mixin/自定义接口）并标工作量；mixin 注入点清单进 SKILL |
| R6 | 库/消费方 CI 的库 SHA 钉值不同步 | 强制顺序：库 push 先行 → 消费方同提交改 SHA+properties |
| R7 | Trinkets/Curios 槽位语义差（Curios 的 IMC 槽 vs Trinkets 数据包槽） | TrinketSlotUtil 对齐签名面；槽位 JSON 按 `data/trinkets/slots/` 布局；测试用例 P3 覆盖装备/卸下回调 |
| R8 | 奇特彩虹/巅峰边框（tooltip 自绘）与 Fabric 生态第三方 tooltip 模组的冲突 | 边框自绘逻辑保留在 tooltip 回调；第三方整管边框的坑（同 Forge 生态）在风险登记中标注，调色板真值沿用库 `Rarity` |
| R9 | client 段 mixin 的 fabric.mod.json 拆分 | client 段拆独立 json + entrypoints.client / environment 处理（SKILL 给模板） |

## 9. 裁剪/删除清单

- **联动裁剪**：Iron's Spells（无 Fabric 1.20.1 版）、Bountiful（Forge-only）。
- **整条删除**：`init/MixinRuntimeGate.java`（Connector 门控）、mods.toml 的 `mixinbooster` 依赖、mods.toml/pack.mcmeta 模板（Forge 专属）、enumextensions 相关概念。
- **运行环境替换**：Embeddium/Oculus→Sodium/Iris、neoforge-carpet→fabric-carpet。

## 10. 验收判据汇总

- 编译：`:fabric-1.20.1:build` 绿（全部阶段）。
- 启动：`runServer` 到 "Done" 无 crash（P0 起每阶段）。
- 注册：137 物品 `/give` 抽查 + 创造栏可见（P1）。
- 持久化：存钱→死亡→重生余额在；108 键抽样（P2）。
- 饰品：Trinkets 槽位 UI + 装备/卸下回调（P3）。
- 战斗/网络：测试台冒烟用例（P4）。
- 战利品/配置/GUI：首箱赠礼用例、配置重载、GUI 打开（P5）。
- mixin/datagen/联动：remapJar refmap 嵌入、datagen 产物比对 forge 侧（P6）。
- 收口：全套件绿 + CI 绿（P7）。

---

> 本文与 `VERSION_PINS.md`、`API_CHEATSHEET.md`、`SKILL.md` 共同构成移植交付包。执行移植时以 `SKILL.md` 为唯一执行入口。
