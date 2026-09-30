\---  
name: astral-dice-fabric-1.20.1-porting  
description: 《星之骰戏》(astral_dice) forge-1.20.1 → fabric-1.20.1 移植执行手册——把 1.20.1 Forge 线移植为独立 fabric-1.20.1 线的完整可执行流程：前置库 starengine_lib 新增 fabric 平台（先行）、消费方新子项目按 P0~P7 阶段移植（骨架→注册→持久化→饰品→事件/网络→战利品/配置/GUI→mixin/datagen/联动→测试台/CI），命名映射为 Mojang 官方映射（原版名零改名），Curios→Trinkets 3.7.2、Capability/Attachments→Cardinal Components 5.2.3，含版本钉值、文件级任务清单、API 对照、验收判据与踩坑。适用于执行「把 astral_dice 移植到 1.20.1 Fabric」任务的其他大模型/工程师。  
\---

# astral_dice forge-1.20.1 → fabric-1.20.1 移植执行手册

> ⚠️ **2026-10-01 状态变更（先读这条）**：本线**已并入 `multi-main`**，成为该仓的**第四条线**
> （同树同仓；原独立 worktree `F:\MCProjectastral_dice_multiloader_fabric` 与分支 `1.20.1-fabric`
> 均已从本地与远端删除）。⇒ 本文所有 `astral_dice_multiloader_fabric` 路径**一律改指**
> `F:\MCProjectastral_dice_multiloader`；`ref/`（参考源克隆件）在**合并后的树里已不存在**
> （`.gitignore` 仍排除 `ref/`，需要时在新树内按 `VERSION_PINS.md` 重新克隆）。
> 其余口径（版本钉值、API 对照、平台差异）**不变**；本线仍是**移植线**、版本号仍带 `-alpha.x`。

> 执行前必读：本文是唯一执行入口。配套件（同目录）：`VERSION_PINS.md`（版本钉值，**先读**）、`API_CHEATSHEET.md`（API 对照）、`PORT_ANALYSIS.md`（分析全文）。  
> 目标仓 worktree：`F://MCProject//astral_dice_multiloader_fabric`（分支 `1.20.1-fabric`）；库仓：`F://MCProject//starengine_lib`。  
> ⚠️ 参考源在 worktree 的 `ref/`（fabric-loader / fabric-api / trinkets / cardinal-components-api / minecraftforge / yarn / patchouli），API 取证一律对照 `ref/` 源码，禁止凭记忆写 Fabric API。

## 0. 硬性约束（红线）

1. **命名映射 = Mojang 官方映射**（`loom.officialMojangMappings()`）——原版类/成员名与 forge 侧**完全一致**，禁改原版引用；只改加载器层（Curios/Forge 事件/Capability/SimpleChannel/GLM/DeferredRegister/mods.toml 等）。
2. **前置替换**：Curios → **Trinkets 3.7.2**（`dev.emi.trinkets.api`）；Capability/ModAttachments → **Cardinal Components 5.2.3**（`dev.onyxstudios.cca.api.v3`）。
3. **库先行**：`starengine_lib-fabric-1.20.1` 必须先于消费方 P0 完成 build + `publishToMavenLocal`（lib_version 四线同号 bump → **1.0.6**），消费方 CI 同提交改库 S继续移植HA。
4. **版本全部钉死**（见 VERSION_PINS.md）；fabric-loader **0.19.5**、fabric-loom **1.14.10**（wrapper Gradle 9.2.1 不带升）、fabric-api **0.92.12+1.20.1**、Java toolchain **17**。
5. **Loom 运行 JVM 21+、编译 toolchain 17** —— 跑 `gradlew` 用 `JAVA_HOME=C://Program Files\Zulu\zulu-21`；不要用 Java 17 跑 gradlew（Loom 会报错）。
6. **每阶段有可机读判据**，绿了才进下一阶段；🚨 任一阶段出现「换汤不换药的假绿」（编译绿但运行期静默失效）必须停下来取证。
7. 交付落点 = worktree `porting/fabric-1.20.1/`（⚠️ 仓内 `docs/` 被 gitignore）。
8. 主线 `multi-main` 零改动；不 push 除非用户另行指示。

## 1. 执行顺序（库先行 → 消费方 P0~P7）

### 1.0 前置：库 starengine_lib 新增 fabric 平台（先于消费方一切）

库仓 `F://MCProject//starengine_lib`：建分支 `fabric-1.20.1`（基线 `main @ 1.0.5`）+ worktree（可选）。新建子项目 `fabric-1.20.1/` 共 11 文件（common 44 文件**零改动**）：

| 文件                                   | 内容要点                                                                                                                                                                                                                                                                                              |
| ------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `fabric-1.20.1/build.gradle`         | `id 'fabric-loom' version '1.14.10'` + Java 17 toolchain + `loom.officialMojangMappings()` + `modImplementation "net.fabricmc:fabric-loader:0.19.5"` + `sourceSets.main.java.srcDir('../common/src/main/java')` + 发布 **remapJar**（artifactId `starengine_lib-fabric-1.20.1`）；publishing mavenJava |
| `fabric-1.20.1/gradle.properties`    | `lib_version=1.0.6`、`mod_version=1.0.6+fabric_1.20.1`（四线同号）                                                                                                                                                                                                                                       |
| `src/main/resources/fabric.mod.json` | id `starengine_lib`；entrypoints.main；depends 仅 fabricloader/minecraft(+fabric-api)；**不声明 trinkets**                                                                                                                                                                                               |
| `StarEngineLib.java`                 | `implements ModInitializer`，`onInitialize` 内 `FabricEconomyStorage.install()`                                                                                                                                                                                                                     |
| `platform/LoaderEvent.java`          | 空 abstract 类                                                                                                                                                                                                                                                                                      |
| `platform/LoaderTags.java`           | `TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation("c","bosses"))` + `EntityType#is`                                                                                                                                                                                                     |
| `client/ActionBarManager.java`       | float partialTick 变体（HUD 回调接线在消费方）                                                                                                                                                                                                                                                                |
| `event/ModEffectRemoval.java`        | `remove(Player, MobEffect)`（照 forge 签名）                                                                                                                                                                                                                                                           |
| `economy/FabricEconomyStorage.java`  | CCA 实体组件 + `RespawnCopyStrategy`；内层 NBT 保持 `starengine_lib/star_coin_wallet/{balance,name}`；事件 `CommandRegistrationCallback` + `ServerPlayerEvents.COPY_FROM`；离线读 .dat 的 `cardinal_components` 段                                                                                                  |
| `item/AstralRarities.java`           | 方案 A：rare→RARE、epic→EPIC 映射原版常量 + 库 `Rarity` 颜色权威；`tierOf` 读物品 NBT 档位                                                                                                                                                                                                                             |
| `item/TrinketsCompat.java`           | 3 方法等价物（对齐 `CuriosCompat`）；`modCompileOnly` trinkets；库不声明前置                                                                                                                                                                                                                                       |
| `component/ItemDataKey.java`         | 照 forge 版逐字复制                                                                                                                                                                                                                                                                                     |

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

| 项                       | 钉值                                                         |
| ----------------------- | ---------------------------------------------------------- |
| fabric-loader           | 0.19.5                                                     |
| fabric-loom             | 1.14.10（Gradle 9.2 带内；**勿升 1.16+** = 需 wrapper 9.4+）       |
| fabric-api              | 0.92.12+1.20.1                                             |
| Trinkets                | 3.7.2                                                      |
| Cardinal Components API | 5.2.3（groupId = `dev.onyxstudios.cardinal-components-api`） |
| StarEngine Lib (fabric) | 1.0.6（先行 publishToMavenLocal）                              |
| Java toolchain          | 17（**gradlew 用 JAVA_HOME=21**）                             |

## 3. 关键 API 对照（详见 API_CHEATSHEET.md）

| Forge                            | Fabric                                                     |
| -------------------------------- | ---------------------------------------------------------- |
| `@Mod` + mods.toml               | `ModInitializer` + fabric.mod.json                         |
| DeferredRegister ×9              | `Registry.register` + `ModRegistries` 包装                   |
| 125 @SubscribeEvent              | FAPI 回调 / 自写 mixin / 自定义回调                                 |
| Capability + ModAttachments(108) | CCA ComponentV3 + RespawnCopyStrategy                      |
| SimpleChannel 12                 | ServerPlayNetworking/ClientPlayNetworking（channel-handler） |
| GLM 战利品                          | LootTableEvents.MODIFY                                     |
| Curios（ICurioItem/IMC 槽）         | Trinkets（Trinket/数据包槽）                                     |

## 4. 踩坑清单（必须遵守）

| #   | 坑                                | 规避                                                                                            |
| --- | -------------------------------- | --------------------------------------------------------------------------------------------- |
| K1  | 用 Yarn 命名 ⇒ 全灭                   | 恒用 `loom.officialMojangMappings()`                                                            |
| K2  | 用 Java 17 跑 gradlew              | Loom 需 JVM 21+；`JAVA_HOME=C://Program Files\Zulu\zulu-21` 跑 gradlew，toolchain 17 编译           |
| K3  | loom ≥1.16 需 Gradle 9.4+         | 钉 `fabric-loom:1.14.10`；勿动 wrapper 9.2.1                                                      |
| K4  | 1.20.1 网络用 PayloadTypeRegistry   | 那是 1.20.5+；1.20.1 用 `registerGlobalReceiver(Identifier, PlayChannelHandler)` + PacketByteBufs |
| K5  | command 用 v1（deprecated）         | 用 `fabric-command-api-v2` 的 `CommandRegistrationCallback`                                     |
| K6  | mixin client 段塞同一个 json          | client 段拆 `astral_dice.client.mixins.json` + `entrypoints.client`/`"environment": "client"`   |
| K7  | 库未 publishToMavenLocal 就构建消费方    | 顺序：库 build+publish → 消费方钉 1.0.6 → 再构建                                                         |
| K8  | CCA 默认不复制死亡数据                    | 逐组件显式 `RespawnCopyStrategy`                                                                   |
| K9  | remapJar 无 refmap ⇒ 生产环境 mixin 崩 | P0 即验证 remapJar 解包有 refmap（`defaultRefmapName`）                                               |
| K10 | 交付物写进 `docs/`（被 gitignore）       | 写 `porting/fabric-1.20.1/`                                                                    |
| K11 | 凭记忆写 Fabric API                  | 对照 `ref/fabric-api`、`ref/trinkets`、`ref/cardinal-components-api` 源码                           |
| K12 | 换汤不换药的假绿（编译绿运行静默失效）              | 每阶段跑运行判据（启动/物品/余额/槽位/战利品），不只编译                                                                |

## 5. 附录

### 5.1 无 FAPI 对应事件的 mixin 注入点候选（P4 用）

| Forge 事件                     | mixin 注入点（Mojmap）             |
| ---------------------------- | ----------------------------- |
| LivingHealEvent              | `LivingEntity.heal(float)`    |
| LivingKnockBackEvent         | `LivingEntity.knockback(...)` |
| EntityTeleportEvent          | `Entity.teleportTo(...)`      |
| ItemCrafted 变体               | `ResultSlot.onTake(...)`      |
| （执行时按 PORT_ANALYSIS §4.2 补全） | —                             |

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


---

## 6. 勘误与实测补充（2026-09-29 执行轮）

> 本轮实际执行时以**发行 jar / javap / 实测**为准，推翻/修正了本手册原稿的三处判断。执行者以本节为准。

### 6.1 【重要】1.20.1 Fabric **有** 附件 API —— 「无附件 API」是错的

Fabric API `0.92.12+1.20.1` 的 jar 内**确实**含 `META-INF/jars/fabric-data-attachment-api-v1-0.92.12.jar`：

```
AttachmentRegistry.create(ResourceLocation) / createDefaulted(id, Supplier) / createPersistent(id, Codec)
AttachmentRegistry.builder() -> Builder (persistent(Codec) / copyOnDeath() / initializer(Supplier) / syncWith(...))
AttachmentType<A>: identifier() / persistenceCodec() / isPersistent() / initializer() / copyOnDeath()
AttachmentTarget: getAttached(...) / setAttached(...) / getAttachedOrCreate(...)
```

与 NeoForge `AttachmentType` **结构 1:1**，而 mod 的 `component/AttachedDataKey.java`（Forge 侧 shim）本就是为模拟 NeoForge 附件而写
⇒ **108 键附件层用 FAPI 附件近乎平移**，比 CCA 更贴近原作者设计、风险更低。

⚠️ **核验模块存在性的正确方法 = 读发行 jar**（`META-INF/jars/<module>-<ver>.jar`），
**不能**用 `ref/fabric-api` 的 `1.20` 分支目录列表 —— 该分支不含此模块，但发行 jar 含。

### 6.2 CCA 坐标（原稿写错）

`groupId` = **`dev.onyxstudios.cardinal-components-api`**（不是 `dev.onyxstudios.cca`）。
`maven.ladysnake.org/releases` 可解析；Modrinth 的 `maven.modrinth:cardinal-components-api:5.2.3` 亦可。

### 6.3 `Rarity` 5 档扩展 —— 可行，但**只能**用 Unsafe（实测 PASS）

vanilla 1.20.1 `Rarity` = `final` 枚举 + 仅 `public final ChatFormatting color`（Forge 的 `styleModifier`/`create` 是**补丁**）。
- javac 禁 `new` 枚举（JLS 15.9.1）⇒ Mixin 也帮不上忙（注入的代码同样过 javac）；
- `Constructor.newInstance` 对枚举被 JDK 硬拒；
- **唯一可行**：`Unsafe.allocateInstance` + `Unsafe` 字段偏移写 `Enum.name` / `Enum.ordinal` / `Rarity.color`，
  并把新常量追加进 `$VALUES`。字段偏移写入**绕开 java.base 模块限制，不需要 `--add-opens`**。
- 已实现于库 `starengine_lib/fabric-1.20.1/.../item/AstralRarities.java`；探针 `temp/unsafeprobe` 在 **JDK 17 与 21** 均 PASS。
- 收益：`ModItems` 137 处 `.rarity(...)` 与 `RarityTooltipFrame` **零改动**。

### 6.4 伤害修正**必须**自建事件总线 + mixin，不能改写成 FAPI 回调

1.20.1 Fabric **没有**任何「可改伤害值」的事件（`ServerLivingEntityEvents.ALLOW_DAMAGE` 只能取消、不能改 amount）。
而 `ChipDamageHandler` / `DiceCombatEvents` 的 `EventPriority.LOWEST/HIGHEST` 是**承重语义**
（源码注释：「气囊恒为第一顺位」「LOWEST = 最终伤害阶段」）⇒ 自建 `LoaderBus` 时必须实现优先级排序，
并用 mixin（`LivingEntity#actuallyHurt` 的**吸收结算之后、setHealth 之前**）派发以对齐 Forge 的派发点。

### 6.5 Loom 构建版本门禁（原稿未提）

`java.lang.IllegalStateException: Mod was built with a newer version of Loom (X), you are using Loom (Y)`
—— mod 的 `META-INF/MANIFEST.MF` 带 `Fabric-Loom-Version`，高于本地 Loom 即被拒。
实测元凶：**`jei-1.20.1-fabric-15.62.0.216.jar`（Fabric-Loom-Version: 1.17.20）**。
⇒ 定位法：遍历 `~/.gradle/caches/modules-2/files-2.1/**/*.jar` 读 manifest；
⇒ 绕法：该 mod 移出 Gradle，运行时手工投放 `run/server/mods` / `run/client/mods`。

### 6.6 Loom 1.14 起 Mixin 注解处理器**默认关闭**

`loom { mixin { ... } }` 会被告警要求移除。refmap 改由 `remapJar` 阶段生成 —— **P0 必须实测验证**
（解包 `build/libs/*.jar` 查 `astral_dice.refmap.json` 是否嵌入、生产环境启动无 mixin 报错）。

### 6.7 Trinkets 槽位数据包格式（实测自 `ref/trinkets` 源码）

```
data/trinkets/slots/<group>/group.json     {"slot_id": <int>}
data/trinkets/slots/<group>/<slot>.json    {"icon":"<ns>:<path>","order":<int>,
                                            "validator_predicates":[...],"drop_rule":"..."}
data/trinkets/entities/<name>.json         {"slots":["<group>/<slot>",...],"entities":["minecraft:player"]}
```

⚠️ `SlotLoader` / `EntitySlotLoader` **只接受 namespace = `trinkets` 的路径**
⇒ 本模组的槽位文件必须放在 jar 内的 `data/trinkets/` 下（不是 `data/astral_dice/`）。
`SlotType.amount` 是**静态**的；动态槽位（本模组的 chip 槽随骰子星级增长）走
`TrinketInventory#addModifier(AttributeModifier, ADDITION)` —— **需专门验证**。


---

## 7. 勘误与实测补充（第二轮执行，2026-09-29）

> 本节全部为**实机/字节码级实测结论**，与上文原稿冲突时**以本节为准**。

### 7.1 【用户裁决】移除 Cardinal Components API，持久化改用 Fabric API 附件

- **落点**：库 `starengine_lib/fabric-1.20.1/.../economy/FabricEconomyStorage.java`
  （`ComponentRegistryV3` + `EntityComponentInitializer` + `RespawnCopyStrategy.ALWAYS_COPY`
  → `AttachmentRegistry.<CompoundTag>builder().persistent(CompoundTag.CODEC).copyOnDeath().initializer(CompoundTag::new).buildAndRegister(id)`）；
  消费方 `component/AttachedDataKey.java`（108 键）与 `component/AstralData`、`ModCapabilities` **全部删除**。
- **附件 API 的真实签名**（javap 实测，务必照抄）：
  - `AttachmentRegistry.create(id)` / `createDefaulted(id, supplier)` / `createPersistent(id, codec)` / `builder()`
  - `Builder` 只有 4 个方法：`persistent(Codec)` / `copyOnDeath()` / `initializer(Supplier)` / **`buildAndRegister(ResourceLocation)`**
    —— ⚠️ **没有 `build()`**（原稿若写 `build()` 会编译失败）。
  - `AttachmentTarget`：`getAttached` / `getAttachedOrCreate` / `getAttachedOrElse` / `setAttached` / `hasAttached` / `removeAttached` / `modifyAttached`。
  - 附件是**引用类型**：只改内容不会触发「已变更」⇒ 改完必须 `setAttached(...)` 重新挂一次。
- **玩家 .dat 的 NBT 布局（决定性）**：根键 = **`fabric:attachments`**（`AttachmentTarget.NBT_ATTACHMENT_KEY` 的字节码常量），
  其下以**附件的 `identifier()` 字符串**为键：
  ```
  玩家 .dat
    └─ fabric:attachments
         └─ starengine_lib:star_coin_wallet
              ├─ balance : Long
              └─ name    : String
  ```
  ⇒ 离线读档的路径与 Forge 线（`ForgeData`）/ NeoForge 线（`NeoForgeData`）不同，`readOfflineWallet` 已按此改写。
- **`copyOnDeath` 的驱动方**：`fabric-data-attachment-api-v1` 的 mixin 列表里**没有** Player mixin ——
  真正的复制由 **`fabric-entity-events-v1`** 调 `AttachmentTargetImpl.transfer(...)` 完成
  （这正是附件模块 `depends` 里声明 `fabric-entity-events-v1` 的原因）。
- ⚠️ **CCA 无法从运行环境彻底消失，但已不是「我们的前置」**：
  **Trinkets 3.7.2 自身的 `fabric.mod.json` hard-depends `cardinal-components-base/entity >=3.0.0-0`**
  （且 `TrinketComponent extends ComponentV3` —— javap 实证）。
  ⇒ ① CCA 变成「**Trinkets 的传递前置**」，玩家不再需要为**本模组**单独安装；
  ② 但它仍必须在**编译期**可见（否则 javac 解析 `TrinketsApi#getTrinketComponent` 的签名时报
  「无法访问 ComponentV3」）⇒ 库与消费方各保留一条 **`modCompileOnly`**（**不写进 `fabric.mod.json`**）。
  ③ 若将来要彻底摆脱 CCA，只能换饰品方案（另议）。

### 7.2 🚨 红线：Loom 只重映射「纯方法名」的 `@Inject(method = ...)`

Bytecode 级实测（javap -v 读生产 jar 的注解值）：

| 源码写法 | 生产 jar 内的注解值 | 结果 |
|---|---|---|
| `method = "getMaxStackSize"` | **`method_7914`** | ✅ 已重映射 |
| `method = "drop(Z)Lnet/minecraft/world/entity/item/ItemEntity;"` | 原样保留（含 Mojmap `ItemEntity`） | ❌ **未重映射** |

Loom 1.14 起 Mixin 注解处理器默认关闭、改由 `remapJar` 阶段重映射注解值，
**它只认纯方法名**；带描述符者原样透传 ⇒ intermediary 生产环境「找不到目标方法 → 注入失败 → `defaultRequire: 1` 直接崩」。
⇒ **本线所有 mixin 的 `method` 一律写纯方法名**；重载歧义交给 Mixin 按 handler 签名自动筛选（不匹配的跳过）。
**交付前必跑**：解包生产 jar，断言所有 `mixin/**` class 内**不含 `net/minecraft/<小写包>/` 形态的 Mojmap 路径**。
（当前 21 个 mixin 类、0 处残留。）

### 7.3 战利品：1.20.1 ↔ 1.21 的三处易错差异

1. **没有 `Registries.LOOT_TABLE`** —— 战利品表不是注册表条目 ⇒ 引用一律走 `ResourceLocation`
   （不是 1.21 的 `ResourceKey<LootTable>`）。
2. `LootTableReference.lootTableReference(...)` 收 **`ResourceLocation`**。
3. `LootTable.Builder#pool(...)` 收 **`LootPool`**（不是 `LootPool.Builder`）⇒ 必须 `.build()`。
4. **`LootTableEvents.MODIFY` 是 5 参 lambda**：`(ResourceManager, LootManager, Identifier, LootTable.Builder, LootTableSource)`
   —— 本仓 `API_CHEATSHEET.md` 原稿写成 3 参，**已订正**。
5. **不需要** Forge 那套「`table.getPool("astral_dice:xxx") != null` 幂等早退」：MODIFY 每次重载作用在**新建的 Builder** 上，天然幂等；
   1.20.1 的 `LootTable` 也没有 `getPool(String)` 这种 Forge 补丁 API。
6. **自有命名空间早退必须保留**（`astral_dice` 直接 return）：自有表 `astral_dice:chests/star_plate` 的 path 也是 `chests/...`，
   会被前缀判据命中（项目历史上的「双通道重复注入、概率 1−(1−p)² 放大」就是它）。
7. **GLM 整体不存在**：Fabric 没有 `forge:global_loot_modifier_serializers` 注册表 ⇒
   原 `loot/AddTableLootModifier.java` + `loot/AstralLootModifiers.java` + 13 份 `data/astral_dice/loot_modifiers/*.json`
   + `data/forge/loot_modifiers/global_loot_modifiers.json` **全部移出**（备份在 `temp/removed_glm/`），
   等价物 = `loot/FabricLootInjector.java` 用 `LootTableReference` 把星盘子表挂上去。
   ⚠️ 注入面**逐字取自原 GLM JSON**（43 个箱表 + 12 个实体表，写成 `Set`/`Map` 常量），
   **不要用 `chests/` 前缀近似** —— 那会让概率口径与 Forge 线不一致。

### 7.4 饰品适配层：Trinkets 的动态槽位是**原生支持**的（最大功能风险解除）

`TrinketInventory`（javap 实证）：
```
private final int baseSize;
private final Map<UUID, AttributeModifier> modifiers;
getModifiers() / addModifier(AttributeModifier) / addPersistentModifier(AttributeModifier)
removeModifier(UUID) / update() / getModifiersByOperation(Operation)
```
⇒ 槽位数 = `baseSize` + 修饰符运算结果，**与 Curios 用修饰符增量控制槽位数的机制同源**。
且本模组的两个槽位修饰符常量（`CHIP_SLOT_MODIFIER` / `CURIO_LEGACY_MODIFIER`）**本身就是 `UUID`**
⇒ 适配层零转换，`ICurioStacksHandler#getModifiers()` 直接返回 `Map<UUID, AttributeModifier>`；
`addPermanentModifier`（Curios 名）→ `addPersistentModifier`（Trinkets 名）。

另外：`Trinket` 接口的 `getModifiers(ItemStack, SlotReference, LivingEntity, UUID): Multimap<Attribute, AttributeModifier>`
正是 Curios `ICurioItem#getAttributeModifiers(SlotContext, UUID, ItemStack)` 的等价物 ⇒ 桥接写法见 `compat/curios/TrinketBridge.Adapter`。

### 7.5 FAPI 在 1.20.1 **没有** stack-aware 堆叠上限

`FabricItem` 只有 6 个方法（`allowNbtUpdateAnimation` / `allowContinuingBlockBreaking` / `getAttributeModifiers` /
`isSuitableFor` / `getRecipeRemainder` …），**没有** `getMaxStackSize(ItemStack)`（那是 Forge 补丁）。
⇒ 本模组自建契约 `platform/item/StackCountOverrideItem` + 极小 mixin `mixin/ItemStackMaxCountMixin`
（注 `ItemStack#getMaxStackSize` 的 HEAD，对实现该接口的物品返回栈级值，其余物品字节码路径不变）。

### 7.6 Forge 补丁方法「没有 FAPI 等价物」时的通用套路（本轮共用到 3 次）

`Item#onDroppedByPlayer` / `Item#getMaxStackSize(ItemStack)` / `LightningBolt#setDamage` 三者共用同一模式：

1. 在 `platform/` 下定义**自有契约接口**（`DropGuardItem` / `StackCountOverrideItem`）；
2. 让原类 `implements` 它，方法体**零改动**（只把 `super.xxx(...)` 换成语义等价返回值）；
3. 写一个**极小的 mixin** 在原版对应方法的 HEAD 询问该接口，未实现者**不改返回值**（原版路径字节码不变）。

例外：`LightningBolt#setDamage/getDamage` 连接口都不必 —— 直接把这一个 `float` 放进既有的
`damage/RailgunBolts` 弱引用表（`mark(bolt, damage)` / `damageOf(bolt)`），零 mixin。

### 7.7 编译收敛轨迹（可复用的工作量标尺）

```
javac 首轮        588 错（全部是 net.minecraftforge.* / top.theillusivec4.* 未解析 ⇒ 无隐藏第二类问题）
机械 import 重写   525
平台 shim 补齐     344
清 FQN 残留        6   ← 此处的「6」是假象：语法错误会让 javac 提前中止，掩盖语义错误
Loops 修复后       61
Curios/战利品/附件 43 → 18 → 3 → 0  ✅ BUILD SUCCESSFUL
```
⚠️ **javac 报错数不是单调下降的**：语法错误（如方法调用参数列表的尾逗号）会让 javac 在 parse 阶段中止，
此时「错误数很少」是**假绿**，修掉语法后语义错误会一次性全部浮出。**每修一批都要看完整日志，不要相信数量下降。**

### 7.8 库侧版本/缓存的坑（本轮实测）

- 库改了 `TrinketsCompat`（新增 4 个方法 + `findCurios`）后 `publishToMavenLocal` 成功、jar 内**确实含新方法**，
  但消费方仍报「找不到符号」⇒ **Gradle 对同名同版本 artifact 有缓存**。
- ⚠️ **不能用 `--refresh-dependencies`**：它会强制重新解析**全部**依赖，而本机沙箱访问
  `repo.maven.apache.org` 返回 **403 Forbidden** ⇒ 整条 `:compileJava` 因依赖解析失败而挂（实测 2m44s 失败）。
- 正确做法：**只清该 artifact 的缓存条目**（`~/.gradle/caches/modules-2/files-2.1/<group>/` 与
  `metadata-*/descriptors/<group>/`，移走而非删除），或按项目规范 **bump 库版本号**再发布。

---

## 8. 给 Fabric 侧接第二套饰品系统（Accessories）—— 方法论与踩坑（2026-09-29）

### 8.1 先量「门面收敛度」，它决定改造成本

`grep -rn "CuriosApi\." src/main/java | wc -l` ⇒ 本轮是 **94 处**，但**几乎全部**收敛成
`CuriosApi.getCuriosInventory(x).findFirstCurio(pred)` 一种形状。
⇒ 结论：**不要在 94 个调用点做双写**，而是把「多源聚合」压在门面里
（`findFirstCurio/findCurios` 取并集；`getStacksHandler` 冲突时返回合并 handler）。
若调用点形态发散（直接摸 `getCurios().get(k).getStacks()` 之类），先做一层归一化再聚合。

### 8.2 Accessories 的机制事实（javap / sources 实证，1.0.0-beta.48）

| 概念 | Trinkets 3.7.2 | Accessories | 语义是否同构 |
|---|---|---|---|
| 实体库存 | `TrinketComponent` | `AccessoriesCapability`（`AccessoriesCapability.get(entity)`） | ✔ |
| 槽位组 | `TrinketInventory` | `AccessoriesContainer` | ✔ |
| 槽位数 | `getContainerSize()` | `getSize()` / `getAccessories().getContainerSize()` | ✔ |
| **槽位修饰符** | `getModifiers() : Map<UUID, AttributeModifier>` | **同类型同名** | ✔✔ 零转换 |
| 增删修饰符 | `addPersistentModifier` / `removeModifier` / `update` | 同名 | ✔ |
| 物品契约 | `Trinket`（`TrinketsApi.registerTrinket`） | `Accessory`（`AccessoriesAPI.registerAccessory`） | 回调基本 1:1 |
| 槽位验证 | 物品标签 `trinkets:<group>/<slot>` | predicate：内建 `accessories:tag` 或**自定义** | ✘ 见 8.3 |

⚠️ 三条容易翻车的细节：
1. **槽位名 = 文件名（无命名空间）**：`ResourceReloadListener` 走 `FileToIdConverter`，
   前缀 `accessories/slot` 与 `.json` 都被剥掉 ⇒ `data/astral_dice/accessories/slot/dice.json`
   注册出来的槽位就叫 `dice`，**全局共享**。想命名空间化只能放子目录
   （`.../slot/astral_dice/dice.json` → `astral_dice:dice`），但那会被判为 *unique slot*
   （名字含冒号）⇒ 语义变成「不出现在 Accessories 界面里」⇒ **别这么做**。
2. **`strictMode` 默认 false**（`ExtraSlotTypeProperties.DEFAULT = (allowResizing=true, strictMode=false, …)`）
   ⇒ 数据包定义的槽位**可以**被 `EntitySlotLoader` 挂到实体上，也能读 `amount`。
   若误用 unique slot（代码注册，默认 `strictMode=true`），实体绑定那步会**只打 WARN 然后跳过**。
3. **图标路径**：`icon` 与 group `icon` 都按 `textures/` + `<path>` + `.png` 拼
   （`AccessoriesScreen` 里的 `withPrefix("textures/")` 实证）⇒ 填 `astral_dice:slot/empty_dice_slot`
   正好命中既有的 `assets/astral_dice/textures/slot/empty_dice_slot.png`，**零新增纹理**。
   同理 Trinkets 侧 `SlotData.create()` 也是 `textures/` + path + `.png`。

### 8.3 用**自定义 predicate**做槽位准入，不要往别人命名空间塞标签

- 内建 `accessories:tag` 验证器查的是 **`accessories:<槽位名>`**（含 `accessories:any`）——
  也就是说「让物品进 dice 槽」要写 `data/accessories/tags/items/dice.json`，**污染他人命名空间**
  且与 Accessories 将来可能新增的同名槽位冲突。
- 正解：`AccessoriesAPI.registerPredicate(自有的 ResourceLocation, SlotBasedPredicate)`，
  再在 `slot/*.json` 的 `validators` 里引用它。predicate 是**静态注册表**（不随数据包重载清空）
  ⇒ 在 mod init 注册一次即可覆盖后续所有 reload，且**双端都要注册**（客户端也要用它渲染/校验）。
- ⚠️ 返回值用 `TriState.DEFAULT`（而不是 `FALSE`）表示「本验证器不表态」，让同槽位的其它验证器继续判。
- ⚠️ **物品清单只保留一份**：本模组让三套系统都指向同一份 `data/curios/tags/items/<slot>.json`
  （Trinkets 侧标签写成 `{"values": ["#curios:dice"]}`，Accessories 侧 predicate 直接读同一个 TagKey），
  避免「加物品时漏补某一套」——这正是本项目历史上踩过的坑。

### 8.4 动态槽位（筹码栏随星级增长）在 Accessories 上怎么落地

链路与 Curios/Trinkets **完全同构**：往槽位组挂一个 `AttributeModifier.Operation.ADDITION` 的绝对修饰符，
值 = 目标槽位数，再 `update()` 触发 resize。
- Trinkets：`TrinketInventory.addPersistentModifier / removeModifier / update`
- Accessories：`AccessoriesContainer.addPersistentModifier / removeModifier / update`
  （或 capability 级 `addPersistentSlotModifiers(Multimap<String, AttributeModifier>)`，key = 槽位名）
- ⇒ 已有代码无需改语义；**两侧同写**即可保证两套系统的槽位数一致。

### 8.5 ⚠️ 可选依赖的**类加载隔离**写法（必须照做）

直接引用第三方类型的适配类，**绝不能在依赖缺席时被加载**：

```java
// 门面里：只放一个静态布尔（类名是字符串，不触发加载）
private static final boolean X_LOADED = FabricLoader.getInstance().isModLoaded(X.MOD_ID);

public static Optional<...> get(...) {
    if (X_LOADED) {                          // ← 守卫
        XCompat.getInventory(entity).ifPresent(list::add);   // invokestatic 在未执行时不解析
    }
    ...
}
```
三条硬约束：
1. 适配类的**公开签名里不出现第三方类型**（否则门面类的常量池就有它，类加载即失败）；
2. 对第三方 MC mod 内嵌的 **impl 包**类型（如 `AccessoriesContainer#getAccessories()` 返回
   `io.wispforest.accessories.impl.ExpandedSimpleContainer`）尽量当原版接口用
   （`net.minecraft.world.Container`），只依赖签名本身、不碰 impl 独有成员；
3. 资源清单（`fabric.mod.json`）里写 `recommends` 而**不是** `depends`。

### 8.6 接入任何「自带 `loom:injected_interfaces`」的 mod 之前，先看它的注入面

Accessories 往 `LivingEntity` 注入了 `AccessoriesAPIAccess`、往 **`AbstractButton` 注入了
`AbstractButtonExtension`**。后者会波及**本模组自己的所有按钮类**（编译期被要求实现其抽象方法）。
- **不能**用 `loom { interfaceInjection { enableDependencyInterfaceInjection = false } }` 关掉：
  Fabric API 自己的 `LootTable.Builder#pool` 等 API 同款机制 ⇒ 关掉会连带消失（实测 7 处「找不到符号」）。
- 正解：在源码侧补一个**编译期占位实现**。安全性来自两点：
  ① 该类是 `private static final` 内部类，外部拿不到实例 ⇒ 没人会调它；
  ② JVM 的类加载与实例化**都不校验抽象方法是否实现**，只有真正调用才抛 `AbstractMethodError`
  ⇒ 第三方缺席时该方法永不执行，方法体里也不必引用它的任何类型（此处返回 `null` 即可；
  泛型实参只出现在 `Signature` 属性里，对 JVM 惰性）。

### 8.7 ⚠️ Fabric 侧「版本谓词」不是 Maven 区间 —— 迁移时必查

`VersionPredicateParser` 只解析 `= / > / >= / < / <= / ~ / ^` 与通配符，**没有** `[` `(` `)` 的处理。
从 Forge 线搬过来的 `"minecraft": "[1.20.1]"`、`"[1.0.6,2.0)"` 会被当成**字面量版本串**，
报「需要 X 的 [1.20.1] 版本，但已经安装了的版本 1.20.1 不对」（看起来自相矛盾，极易误判）。
⇒ 一律改写：`~1.20.1`（= `>=1.20.1 <1.21.0`）、`>=1.0.7 <2.0`（**空格分隔 = AND**）。
