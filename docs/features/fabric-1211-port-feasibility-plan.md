# fabric-1.21.1 新增线：移植执行计划（v2，2026-11-04 重写）

> **触发**：用户 2026-11-03 裁决「增加新版本 fabric-1.21.1，与 fabric-1.20.1 同平台，游戏版本升级为 1.21.1，先建移植可行方案」；用户 2026-11-04 指示「重写打开之前的 1.21.1 fabric 移植计划」。
> **状态**：**执行计划**。仓内仍无任何 fabric-1.21.1 文件（`glob "fabric-1.21.1/**"` = **0 路径**），本文件不建工程、不构建、不起游戏。
> **与 v1 的关系**：v1 勘察稿（2026-11-03，36,338 B）已原样归档到 `docs/features/archive/fabric-1211-port-feasibility-plan-v1.md`。v2 重写的是**结论与步骤**；v1 逐条实读的取证表（1.21.1 语义差计数、数据包目录对照、四线硬编码骨架）保留在本文 §13，未删。
> **数字口径**：标 **v2 实读** = 2026-11-04 本会话命令输出；标 **v1 实读** = 2026-11-03 快照，**实施前必须复算**（本仓「事实源原则」）。

## 0 本次重写改了什么（v1 → v2）

| 项 | v1（2026-11-03 勘察稿） | v2（2026-11-04 执行稿） |
|---|---|---|
| **取数通道** | 未决：AutoJS 无 fabric-1.21.1 产物，CraftTweaker 待评（v1 §10 待拍板 1） | **已定**：AutoJS 自带 **Rhino 内核**为唯一主通道；CraftTweaker **否决**（§8.3）。风险 R1/R2 **关闭** |
| **AutoJS 产物** | 仓内 vendor 只有 forge-1.20.1 / fabric-1.20.1 / neoforge-1.21.1 / neoforge-26.1 | 已有 `scripts/test/resources/mods/autojs/AutoJS-fabric-1.21.1-1.0.0.jar`（sha256 `f498cf4a9d8df3a26554d09b09ce615eb2badba08120964460703677c4822fa3`），PROVENANCE 有登记 |
| **上游 target** | 「需要新克隆 + 新 target，约 1 个工作包」 | 上游 `F:/MCProject/AutoJS/targets/fabric-1.21.1` **已存在并构建成功**，其钉版可作本仓的对照证据（§6.2） |
| **版本线** | 假设 `26.4-SNAPSHOT.5` | 四线现行 **`26.4-SNAPSHOT.6+<line>`**（n21 `gradle.properties:38`、fabric `gradle.properties:29`，v2 实读） |
| **测试台** | 按 pwsh 口径列改造点 | pwsh **已废弃**，口径 = `scripts/test/win/*.sh` + `scripts/test/mt_*.py`（§9 按此重列） |
| **依赖钉版** | 2026-11-03 Modrinth 实查 | **2026-11-04 19:2x 代理恢复后已逐项复验**（25 个 slug 全通过，见 §6.4）：除 **Collective 8.75 → 8.77**（`JsrQpNp7` → `qSQaLB3S`）外，其余与 11-03 快照完全一致 |
| **Loom 版本** | 「必须抬到 1.19.3」 | **改为**「先沿用仓内 1.17.21（`fabric-1.20.1/build.gradle:14`），失败再抬」——AutoJS 的 1.21.1 target 用 **Loom 1.10.5 + Gradle 8.14.4** 也构建成功（`targets/fabric-1.21.1/build.gradle:8`、wrapper 实读），说明 1.21.1 对 Loom 版本不挑 |

## 1 一句话执行结论

**可行，且蓝本已确定**：以 `neoforge-1.21.1` 的 **419 个业务文件**为蓝本（它的原版 API 天然是 1.21.1），只把**平台层**换成镜像 NeoForge 21.1 的 Fabric shim；机制（LoaderBus + Bridges + bridge mixin）整体照抄 `fabric-1.20.1`。
真正的**新增工作四块**：① 平台层 shim（约 85 类，其中约 30 类是真新语义）；② `starengine` 出**第 5 个平台产物** `starengine-fabric-1.21.1`（含 GeckoLib vendor 的 fabric 根）；③ 测试台四线→五线扩表；④ 4 个 KubeJS 探针改写成 AutoJS 原生脚本。
**不许做的事**：① 不从 `fabric-1.20.1` 往上搬（等于重做一遍 1.20.1→1.21.1 迁移）；② 不引 Architectury 重构架构；③ **P5（注册与服务端跑通）之前不宣布「可用」**。

## 2 事实基线（v2 实读，2026-11-04）

### 2.1 四条既有线的体量

| 线 | java | mixin | platform shim | 说明 |
|---|---|---|---|---|
| `neoforge-1.21.1` | **419** | 24 | 0 | **蓝本**（原版 API = 1.21.1） |
| `forge-1.20.1` | 411 | 26 | 0 | 仅作对照 |
| `neoforge-26.1.2` | 429 | 26 | 0 | 仅作对照（MC 26.1.2，API 又是另一代） |
| `fabric-1.20.1` | **530** | 49 | **79** | 平台层做法的**施工图纸**（镜像 Forge 1.20.1 那套） |

计数命令：`glob "<line>/src/main/java/**/*.java"`、`.../**/mixin/**`、`.../**/platform/**`。

### 2.2 仓结构（`settings.gradle`，v2 实读）

- `:29-32` 只有四条 `include`：`neoforge-1.21.1` / `forge-1.20.1` / `neoforge-26.1.2` / `fabric-1.20.1`；注释明确「每个 MC 版本+加载器 一个自包含子项目，**不使用 common 源码子项目**」。
- `pluginManagement` 已含 `maven.fabricmc.net`（fabric-1.20.1 引入），新线**不需要**再加仓库。
- 根 `gradle.properties` 只有 `org.gradle.*`，无版本号属性 ⇒ 新线钉版全部写在 `fabric-1.21.1/gradle.properties`。

### 2.3 现有 fabric 线的构建口径（`fabric-1.20.1/build.gradle`，v2 实读）

| 项 | 值 | 行号 |
|---|---|---|
| Loom 插件 | `fabric-loom` **1.17.21**（注释要求 Gradle ≥ 9.5.0，JDK21 可用） | `:14` |
| 编译 toolchain | Java **17** | `:25` |
| 映射 | `loom.officialMojangMappings()` | `:70` |
| 运行 JVM | `languageVersion = JavaLanguageVersion.of(21)`（仅 run* 任务换 JVM，产物仍是 17 字节码） | `:580` |
| 内嵌库投放 | 交给 `python tools/loom_embedded_jars.py --side client --apply`（不手写坐标） | `:222` |

⇒ 新线与它的**唯一结构性差异**：toolchain 必须 **21**（MC 1.21.1 要求），运行 JVM 也是 21（一致）。

## 3 路线裁决（沿用 v1 结论，v2 复核仍成立）

| 路线 | 做法 | 结论 |
|---|---|---|
| **A（采用）** | 蓝本 = `neoforge-1.21.1` 的 419 业务文件；新写 Fabric shim 镜像 **NeoForge 21.1**；机制抄 fabric-1.20.1 | 原版 API 已是 1.21.1 ⇒ 不做 1.20.1→1.21.1 版本迁移 |
| B | 蓝本 = `fabric-1.20.1`（530 文件、平台层现成），再做一遍版本迁移 | 否决：重做 n21 已做过的工作，且 `LivingHurtEvent`/`SimpleChannel` 语义已被取代 |
| C | Architectury / 自建跨加载器抽象层 | 否决（与「自包含子项目」既有裁决冲突），可作远期议题 |

**两块省工点（v1 实读，P1 前复算）**：
1. **网络层近乎免费**：n21 的 payload 全部 `implements net.minecraft.network.protocol.common.custom.CustomPacketPayload`（原版类型，加载器中立）+ 自带 `StreamCodec`；加载器相关只有两处 API（`RegisterPayloadHandlersEvent/PayloadRegistrar` 注册、`PacketDistributor` 发送 66 处）⇒ shim 4 个门面类，**47 个 network 文件业务逻辑一行不改**。
2. **事件总线可 80% 复用**：NeoForge 21.1 与 Forge 1.20.1 事件模型同构（`IEventBus`/`SubscribeEvent`/`EventPriority`/`EventBusSubscriber`），`LoaderBus` 与排序语义直接搬；差异只在「`@EventBusSubscriber` 自动扫描在 Fabric 侧不存在」（B2）⇒ 那 86 个文件改显式注册（fabric-1.20.1 已全部改过，照它的写法）。

## 4 目标结构

```
fabric-1.21.1/
  gradle.properties              # 1.21.1 钉版（§6）；mod_version=26.4-SNAPSHOT.<n>+fabric_1.21.1
  build.gradle                   # loom 1.17.21（失败再抬）/ toolchain 21 / officialMojangMappings
  src/main/resources/
    fabric.mod.json              # entrypoints + AW + 三份 mixin config + depends（rhino [2101,) 由 AutoJS 侧声明）
    astral_dice.accesswidener    # 从 fabric-1.20.1 迁，按 1.21.1 映射逐条复核
    astral_dice.mixins.json      # JAVA_21；业务 mixin + bridge mixin
    astral_dice.jade.mixins.json
    astral_dice.trinkets.mixins.json
  src/main/java/com/merlinkitsune/astral_dice/
    <业务代码：蓝本 n21>          # 277 逐字 + 63 包名替换 + 79 语义改写（v1 实读）
    platform/                    # 新 shim（镜像 NeoForge 21.1，~85 类）
    mixin/                       # n21 的 24 个 + fabric 专属 bridge mixin
    compat/{jade,jei,trinkets,accessories}/
  src/generated/resources/       # 蓝本 n21（371 文件，1.21.1 格式正确）
```

命名纪律（与四线一致）：
- 子项目目录 / `run/` 标签 / 测试台 `--version` 值 **一律 `fabric-1.21.1`**。
- `mod_version=26.4-SNAPSHOT.<n>+fabric_1.21.1`（`<n>` 与四线**同号递增**，全平台同版）。
- 产物名 `astral_dice-26.4-SNAPSHOT.<n>+fabric_1.21.1.jar`（`archivesName='astral_dice'`，与 fabric-1.20.1 同形）。

## 5 平台层映射（NeoForge 21.1 → platform.*）

### 5.1 1:1 包名替换（可机械替换，约 63 文件 / 45 类型）

| NeoForge 21.1 | 目标 |
|---|---|
| `net.neoforged.bus.api.{IEventBus,SubscribeEvent,EventPriority}` | `...astral_dice.platform.event.*`（沿用 fabric-1.20.1 实现） |
| `net.neoforged.fml.{ModList,ModContainer,ModLoadingStage}`、`fml.common.{Mod,EventBusSubscriber}` | `platform.fml.*`（`EventBusSubscriber` 只留注解语义，实际注册走 LoaderBus） |
| `neoforge.event.entity.living.*`（LivingDeath / LivingDamage / LivingChangeTarget / MobEffect / LivingHeal / LivingDrops / LivingUseTotem / LivingEntityUseItem…） | `platform.event.entity.living.*`（fabric-1.20.1 已有同名类，**按 n21 签名对齐**） |
| `neoforge.event.entity.player.*`、`entity.item.*`、`level.*`、`tick.*`、`village.*`、`RegisterCommandsEvent`、`LootTableLoadEvent`、`AnvilUpdateEvent`、`BuildCreativeModeTabContentsEvent`、`EntityAttributeCreationEvent` | `platform.event.*`（多数已有） |
| `neoforge.registries.{DeferredRegister,DeferredHolder}` | `platform.registry.*`（**按 21.1 用 `DeferredHolder` 而非 `RegistryObject`**；13+10 文件） |
| `common.util.TriState`、`common.ModConfigSpec`、`fml.config.ModConfig` | `platform.common.*` / `platform.config.*` |
| `api.distmarker.{Dist,OnlyIn}`（16 处） | `platform.api.distmarker.*`（已有） |

### 5.2 必须重写的新语义（约 30 类 / 79 业务文件）

| 能力 | NeoForge 21.1 形态 | Fabric 1.21.1 实现 | 影响面（v1 实读） |
|---|---|---|---|
| 网络注册 | `RegisterPayloadHandlersEvent` → `PayloadRegistrar.playToClient/playToServer(TYPE, CODEC, handler)`，handler 收 `IPayloadContext` | shim 同名 4 类：`PayloadTypeRegistry` + `ServerPlayNetworking/ClientPlayNetworking.registerGlobalReceiver`；`enqueueWork` → `context.client().execute(...)` | 47 文件（只改 import） |
| 网络发送 | `PacketDistributor.sendToPlayer/sendToServer/sendToPlayersTrackingEntity` | `ServerPlayNetworking.send` / `ClientPlayNetworking.send` / `PlayerLookup.tracking(entity)` | 32 文件 66 处 |
| 物品数据组件 | `DataComponents` + `stack.get(KEY)` / `.component(KEY,v)`（n21 **138 处** `DataComponents.`、12 处 `.component(`） | 1.21.1 是**原版组件体系**（加载器中立）⇒ 只需 shim 转发 | 126 处 / 22 文件 |
| 数据附件 | `AttachmentType.serialize(codec)` | 继续 CCA 6.1.3，或 `fabric-data-attachment-api-v1` | 1 import + `component/` 10 文件 |
| GUI 图层 | `RegisterGuiLayersEvent` / `VanillaGuiLayers` / `RenderGuiLayerEvent` | FAPI `HudLayerRegistrationCallback` + `IdentifiedLayer`（1.21 已改名：Overlays→Layers） | 2 |
| 配置 | `ModConfigSpec` | 二选一：① shim 包 cloth-config；② 直接用 **Forge Config API Port**（它就是 NeoForge config API 移植，**待验证**是否直接提供同名类） | 1 |
| 伤害管线 | `LivingIncomingDamageEvent` + `DamageContainer` | fabric-1.20.1 做法 = Puzzles Lib 回调 + `bridge/LivingHurtBridgeMixin` 派发；1.21.1 有 puzzles-lib 21.1.x，需确认回调面 | 1 类 + 9 调用点 |
| 菜单类型 | `IMenuTypeExtension.create(Codec)` | `new MenuType<>(factory, FeatureFlags.VANILLA_SET)` 等价实现 | 1 |
| 配方条件 | `ICondition/IConditionBuilder` | 直通原版 `ICondition`（1.21.1 原版已有） | 2 |
| 数据生成 | `GatherDataEvent` / `ItemModelProvider` / `ExistingFileHelper` | `FabricDataGenerator` + 自定义 provider（n21 `datagen/` 3 文件按 Loom datagen 重写） | 2–3 |

## 6 依赖与钉版

### 6.1 已由上游产物**实证**的三条（可当硬事实用）

`F:/MCProject/AutoJS/targets/fabric-1.21.1/gradle.properties`（v2 实读）：

```
fabric_minecraft_version=1.21.1
minecraft_version_range=>=1.21.1 <1.22
fabric_loader_version=0.20.1
fabric_api_version=0.116.17+1.21.1
rhino_version=2101.2.8-build.91
rhino_version_range=[2101,)
```

⇒ ① **Fabric Loader 0.20.1** 可用；② **Fabric API 0.116.17+1.21.1** 可用；③ **Rhino 2101.2.8-build.91** 在 1.21.1 上是多加载器单 jar（AutoJS 的 fabric target 就是靠它跑起来的）。

### 6.2 构建链口径（实读对照）

| 项 | 本仓 fabric-1.20.1 | AutoJS 的 fabric-1.21.1 target | 新线取法 |
|---|---|---|---|
| Loom | 1.17.21 | 1.10.5（`build.gradle:8`） | **先沿用 1.17.21**；若 `loom` 报 1.21.1 不支持再抬 1.19.x |
| Gradle wrapper | 9.2.1 | 8.14.4 | 沿用本仓 9.2.1（同一 wrapper 已在四线跑通） |
| Java toolchain | 17 | 21 | **21**（MC 1.21.1 硬要求） |

### 6.3 依赖表（**2026-11-04 19:2x 逐项复验通过**，口径 = `game_versions=["1.21.1"]` + `loaders=["fabric"]`）

| 用途 | 版本 | version id | 结论 |
|---|---|---|---|
| Fabric Loader | 0.20.1 | — | ✅（§6.1 实证） |
| Fabric API | 0.116.17+1.21.1 | `Mys3P7lK` | ✅ |
| Puzzles Lib（硬依赖，伤害回调） | 21.1.66 | `IV6k1yRp` | ✅ |
| Trinkets（软依赖） | 3.10.0 | `JagCscwi` | ⚠️ 仅 1 个 1.21.1 版本，3.10 与现用 3.7.2 有 API 差异（槽位数据驱动化） |
| Accessories（软依赖，二选一） | 1.1.0-beta.54+1.21.1 | `YZ9RWTNl` | ⚠️ 大版本跃迁（1.0.0-beta.48 → 1.1.0-beta.54） |
| Architectury | 13.0.11+fabric | `Pzc2FP5K` | ✅ |
| Cardinal Components | 6.1.3 | `nLsCe2VD` | ✅ |
| Patchouli | 1.21.1-93-fabric | `XIjNwQkI` | ✅ |
| JEI | 19.70.0.469 | `WkRLMWAY` | ✅（⚠️ 19.70 起硬前置 **mezz_config 0.6.10**，缺则 Loader 硬拒） |
| Jade | 15.10.6+fabric | `adpNvTZS` | ✅ |
| Cloth Config | 15.0.140+fabric | `HpMb5wGb` | ✅ |
| Forge Config API Port | v21.1.6-1.21.1-Fabric | `N5qzq0XV` | ✅（**待验证**能否直接提供 `ModConfigSpec`） |
| Mod Menu | 11.0.5 | `6lgOkclV` | ✅ |
| Sodium | mc1.21.1-0.8.13-fabric | `SMxNOGZ6` | ✅ |
| Iris | 1.8.14-beta.1+1.21.1-fabric | `bAo1Qhte` | ✅ |
| Lithium | mc1.21.1-0.15.4-fabric | `N08Z8wog` | ✅ |
| Carpet | 1.4.147 | `f2mvlGrg` | ✅ |
| Debugify | 1.21.1+1.1 | `pyIdu3f1` | ✅ |
| YACL | 3.8.2+1.21.1-fabric | `o3cDn8Vp` | ✅（⚠️ yacl 3.6+ 的内嵌 quilt-parsers 在 Loom dev 下会丢，见 §9 的 `loom_embedded_jars`） |
| Superflat World No Slimes | 1.21.1-3.7 | `OHCdhqlM` | ✅ |
| Collective | 1.21.1-**8.77**（v1 写 8.75，已漂移） | `qSQaLB3S` | ✅ 2026-11-03 发的 8.77 |
| ModernFix | 5.25.1+mc1.21.1 | `NnNX8LBn` | ✅ |
| ImmediatelyFast | 1.6.15+1.21.1-fabric | `aLnBqxFW` | ✅ |
| Indium | 1.0.35+mc1.21 | `Z8VpxxGh` | ⚠️ Sodium 0.6+ 已内置 FRAPI ⇒ 1.21.1 大概率不需要（实测定） |
| **KubeJS** | — | — | ❌ 无 fabric 1.21.1 构建（`2101.x` 只标 neoforge）⇒ **本线不装 KubeJS** |
| Rhino | 2101.2.8-build.91 | §6.1 | ✅ 由 AutoJS 侧声明 required（`[2101,)`），本仓不重复 vendor |
| CraftTweaker | 21.0.38（fabric `TvJixPvk`） | — | 存在但**不采用**（§8.3） |

### 6.4 复验记录与可重跑命令

**2026-11-04 19:2x 实跑结果（代理 7897 恢复后）**：
- 代理连通：TCP `127.0.0.1:7897` OPEN；经代理出网 `https://api.ipify.org` → `103.147.200.11`（1.10s）；Modrinth API 2.34s 回包。
- 25 个 slug 全部取到版本（脚本见下）；与 11-03 快照逐条比对，**只有一处漂移**：`collective` `1.21.1-8.75/JsrQpNp7` → **`1.21.1-8.77-fabric+forge+neo` / `qSQaLB3S`**（2026-11-03 发布）。
- 另三条顺带核实的硬事实：`fabric-loader` latest stable = **0.20.1**（无更新）；`fabric-loom` latest release = **1.19.3**（本仓现用 1.17.21，见 §6.2）；`fabric-api` 1.21.1 最新仍是 **0.116.17+1.21.1**（top3 不变）。
- **rhino 的标注差异（P1 必须实测）**：Modrinth 上 `game_versions=[1.21.1]` 的 11 个 rhino 件里，`2101.2.8-build.91`（`SqkDvOLG`）只标 **neoforge**，标 fabric 的最新是 `2101.2.7-build.85`（`cQ4POTah`，fabric+forge+neoforge）。但 AutoJS 的 fabric target 直接用 **maven 坐标** `dev.latvian.mods:rhino:2101.2.8-build.91`（`targets/fabric-1.21.1/build.gradle:201-202`，注释说明该 maven 件同时带 `rhino` mod 身份）⇒ **新线取 maven 坐标**；若 fabric 侧解析不到 `fabric.mod.json`，退回 Modrinth `cQ4POTah`。

可重跑命令（P0 每次开工前跑一次；**不要**相信历史读数）：

```bash
PROXY=http://127.0.0.1:7897 python - <<'PY'
import json,urllib.request,urllib.parse,os
p=os.environ["PROXY"]
o=urllib.request.build_opener(urllib.request.ProxyHandler({"http":p,"https":p}))
for s in ["fabric-api","puzzles-lib","trinkets","accessories","jei","jade","patchouli",
          "cloth-config","forge-config-api-port","modmenu","sodium","iris","lithium",
          "carpet","debugify","yacl","superflat-world-no-slimes","collective","modernfix",
          "immediatelyfast","indium","architectury-api","cardinal-components-api"]:
    u="https://api.modrinth.com/v2/project/%s/version?game_versions=%s&loaders=%s"%(s,
      urllib.parse.quote('["1.21.1"]'),urllib.parse.quote('["fabric"]'))
    try:
        d=json.loads(o.open(urllib.request.Request(u,headers={"User-Agent":"astral-dice/1.0"}),timeout=25).read())
        print("%s: latest=%s id=%s"%(s,d[0]["version_number"],d[0]["id"]) if d else "%s: NONE"%s)
    except Exception as e: print("%s: ERR %s"%(s,str(e)[:80]))
PY
```

**纪律**：直连（不走代理）仍被拒 ⇒ 测试台与本仓所有取版本脚本都必须显式挂 `127.0.0.1:7897`。另：2026-11-03 曾出现「脚本报 OK 但没写进文件」，所以取到读数后要**逐项回读落盘结果**，别只看脚本自己的 PASS。

## 7 分阶段执行计划（P0–P9，逐阶段卡口）

| 阶段 | 内容 | 判据（机器可验） | 前置 |
|---|---|---|---|
| **P0 预检与复验** | ① §6.4 复验依赖；② 验 AutoJS fabric-1.21.1 能起（用 fabric-1.20.1 的 run 目录临时挂）；③ 确认 Gradle 9.2.1 + Loom 1.17.21 能解 1.21.1 的 MC/mappings | 依赖表全部有 id；`python scripts/test/tools/publish_autojs_maven.py --dry-run` 认得第五个 artifact；`loom` 解析无 `Unsupported Minecraft version` | — |
| **P1 脚手架** | 建 `fabric-1.21.1/`：`gradle.properties`、`build.gradle`（toolchain 21）、`settings.gradle` include、`fabric.mod.json` 骨架、空主类 | `:fabric-1.21.1:build` 绿；jar 落 `fabric-1.21.1/build/libs/`；`runClient` 能到主菜单（无业务代码） | P0 |
| **P2 平台层 shim** | `platform/` ~85 类：先搬 fabric-1.20.1 的 event/fml/registry/distmarker（~55），再写 network 4 类 + gui layers 3 类 + attachment/config/datagen/menu/condition（~30） | `grep -rn "^import net\.neoforged" fabric-1.21.1/src` = **0**；`:fabric-1.21.1:compileJava` 绿 | P1 |
| **P3 业务代码搬运** | 277 逐字 → 63 包名替换 → 79 语义改写（network 47 只改 import） | `compileJava` **非 UP-TO-DATE** 且绿；`grep -rn "net\.minecraftforge\|software\.bernie\.geckolib" fabric-1.21.1/src` = 0 | P2 |
| **P4 资源与数据** | 蓝本 n21 的 main+generated（1348 文件）；重写 `fabric.mod.json` / AW / 三份 mixin config；按 1.21.1 原版类复核 bridge/container mixin | jar 内 assets 计数 = n21；`pack.mcmeta` = `{"resource":34,"data":48}`；`python tools/check_lang_sync.py`、`python scripts/verify/verify_tag_parity.py` 该线 OK | P3 |
| **P5 注册与服务端** | 注册表逐项落地（item/block/effect/entity/attachment/menu/particle/sound） | `runServer` 到 `Done (`；AutoJS 读注册表：`astral_dice:sykestentacle`、效果数、附件键在位 | P4 |
| **P6 网络与版本门禁** | payload 注册 + `VersionGate` 握手 | 用例 `INJECT-ROUNDTRIP-fabric-1.21.1`、`VERSION-GATE-fabric-1.21.1` 绿 | P5 |
| **P7 客户端渲染** | GUI 图层、立牌半透明、tooltip 框色、粒子 provider、光影栈 | `mt_capture` 截图非空（**注意 §9 的窗口定位坑**）；立牌 tooltip 分支数 = **31** | P6 |
| **P8 GeckoLib 前置** | `starengine` 新平台 `starengine-fabric-1.21.1`（Loom）+ vendored GeckoLib 的 **fabric 根**；消费方接该产物 | 触手实体渲染 + `triggerAnim` 攻击动画（与 1.21.1 neoforge 同一判据：summon +`attack` ⇒ hp 掉 1）；jar 内无 jarjar | P1（可并行） |
| **P9 测试台与治理** | 五线扩表、用例克隆、`porting/CROSS-VERSION-DIFFS.md` 加行、AGENTS 口径 | `python scripts/test/mt_preflight.py --version fabric-1.21.1` = `MT_PREFLIGHT: OK`；`BOOT-READY`/`JAR-ASSETS` 绿；门禁三绿 | P7 |

**排序**：P0→P1→P2→P3→P4→P5 是串行硬依赖；P8（GeckoLib/starengine 第 5 平台）应在 P5 之后（它需要能构建的消费方来验），但可与 P6/P7 并行；P9 收尾。

## 8 取数通道（v2 的主要变化）

### 8.1 已就绪的实物（不再是风险）

| 项 | 证据 |
|---|---|
| 上游 target | `F:/MCProject/AutoJS/targets/fabric-1.21.1/`（Loom 1.10.5 / Gradle 8.14.4 / toolchain 21 / loader 0.20.1 / FAPI 0.116.17+1.21.1） |
| 产物 | `scripts/test/resources/mods/autojs/AutoJS-fabric-1.21.1-1.0.0.jar`，sha256 `f498cf4a9d8df3a26554d09b09ce615eb2badba08120964460703677c4822fa3` |
| mavenLocal 坐标 | `cc.sighs.autojs:AutoJS-fabric-1.21.1:1.0.0`（**groupId 仍写 `cc.sighs.autojs`**，只内容重编；PROVENANCE 已登记） |
| 装载方式 | Loom `modRuntimeOnly` 解析期 intermediary→named（与 fabric-1.20.1 同路） |

### 8.2 脚本通道形态（Rhino 内核，实测 API 形态见 §13.4）

- 不装 KubeJS 时 AutoJS 用**自带 Rhino 内核**，脚本是**普通 JavaScript**（不是 KubeJS 的 `ServerEvents` 那一套）。
- Rhino 在 1.21.1 fabric 上是多加载器单 jar（`2101.2.8-build.91`），AutoJS 侧声明 `rhino [2101,) required`。
- ⇒ 新线**不需要第二个脚本引擎**，取数通道与另四线一致（都是 AutoJS HTTP `25585`）。

### 8.3 CraftTweaker 为什么不采用（结论 + 理由）

- 存在性成立：`Xg35A4rS`，1.21.1 共 72 个版本，最新 `21.0.38`，fabric id `TvJixPvk`。
- 但它的语言是 **ZenScript**、能力面围绕「数据与事件」；本仓探针要的是三件事：**任意读运行时状态**（反射/字段/容器）、**自持 tick 采样**（`ServerEvents.tick` 等价物 + 自己的计数器）、**写机器行**（`console.info` 形态固定、可被 `type=log` 断言）。ZenScript 对这三件都没有等价能力面。
- ⇒ 采用 AutoJS Rhino（C2 主通道）+ Java 侧探针（C3 兜底），**不引入 CraftTweaker**，避免多一个只能做数据的模组挂在测试台里。

### 8.4 探针迁移映射（`scripts/test/fabric/` 的 4 个 KubeJS 探针）

| 现件（KubeJS） | 迁移后（AutoJS 原生 JS） | 备注 |
|---|---|---|
| `astral_catchup_probe.js` | `scripts/test/autojs/fabric-1.21.1/*.js` | 改用 `AutoJS.level()`/`AutoJS.players()` |
| `astral_*_probe.js`（另 3 个） | 同上目录 | 机器行格式**逐字不变**（`MT_...` 前缀照抄），否则断言层要动 |
| `ft_env.py --install-probe`（KubeJS 装载） | `mt_inject.py` / AutoJS HTTP eval | 通道变，契约不变 |
| 8 个 `FAB-*` 用例 | 克隆为 `*-fabric-1.21.1.json` | 断言按 1.21.1 语义复核 |

**红线**：探针改写**只换宿主通道，不换机器行契约**（`scripts/test/mt_assert.py` 与用例里的 `expect` 都按机器行匹配）。

## 9 测试台五线改造（bash + python 口径，pwsh 已废弃）

| 位置 | 现状 | 改法 |
|---|---|---|
| `scripts/test/lib/mt_compat.py` `VERSIONS` / `LOADER` / `SUBPROJECT` / `DEFAULT_PACK_MODS` / `CONF_KEYS` | 四元组 | 加 `fabric-1.21.1`（loader=fabric，subproject 同名） |
| `scripts/test/lib/mt_paths.py` `VERSIONS` / `SUBPROJECT` / `LOADER` | 四元组 | 同上 |
| `scripts/test/lib/mt_ps.py` / `mt_ime.py` 的 choices | 四元组 | 同上 |
| **`mt_ps.py` `_MARKER_ORDER`** | `("fabric-1.20.1","1.21.1","26.1.2","1.20.1")` | **`fabric-1.21.1` 会被 `1.21.1` 吞** ⇒ 新串必须排在 `1.21.1` 之前；`mt_compat.py` 里 6 处 `"fabric-1.20.1"` 字面量分支泛化成 `fabric-*` |
| `scripts/test/win/lib/common.sh` `MT_ALL_VERSIONS` / subproject / loader / **java 主版本** / autojs | 四线；fabric-1.20.1→17 | **fabric-1.21.1 → 21** |
| `scripts/test/win/lib/fabric.sh`（17 处 `fabric-1.20.1` 字面量） | 只认 1.20.1 | 泛化 `fabric-*` 或加分支（**AutoJS 状态文件 `.mt_autojs_injected` 分侧写入逻辑**在这一层） |
| `win/{mt,env,launch,stop,live}.sh`、`phases.sh` | 字面量分支 | 同上 |
| `tools/*.py` 的 `LINES`/`SUBPROJECTS` 常量（`check_lang_sync.py`、`verify_tag_parity.py`、`gen_particle_library.py`、`verify_effect_icons.py`、`loom_embedded_jars.py`、`unpack_nested_mod_jars.py`、`fetch_shaderpacks.py` 等） | 四线 | 加第五线 |
| `.github/workflows/build.yml` 的 lang-sync 守卫 / 产物 / 验证矩阵 / Release glob | 四线 | 加第五线（守卫写 `hashFiles(...)!=''`） |
| `scripts/test/cases/*-fabric-1.20.1.json` | 一批 | 克隆为 `*-fabric-1.21.1`（含 JSON 内 `"version"` 字段） |

**四条前置坑（都已踩过，别再踩）**：
1. **端口互斥**：AutoJS 端点 `25585` 在测试台里是硬编码，**同一时刻只能一条线占着**；`25585`/`25565` 被占时**绝不能 launch**（会注入别人那台游戏，产生跨线污染的假绿）。
2. **run 目录分侧**：fabric 的 `run/client` 与 `run/server` 曾经混用导致 `mt_env world` 生成 noise 世界（fabric-1.20.1 的 §11.1 事故）⇒ 新线一开始就按分侧目录写。
3. **截图窗口定位**：`mt_capture.py` 在 fabric 上曾定位不到窗口（回落到整屏抓图抓到桌面）⇒ P7 前先修窗口定位，否则「画面级取证」不可用。
4. **内嵌库**：Loom 会剥 `fabric.mod.json` 的 `jars` 声明 ⇒ 依赖件缺失用 `python tools/loom_embedded_jars.py --apply --clean --side client`（`--clean` 先撤旧投放，避免重复 id 拒绝启动）。

## 10 风险表（相对 v1 的闭合状态）

| # | 风险 | 状态 |
|---|---|---|
| R1 | KubeJS 无 fabric 1.21.1 ⇒ 探针不可用 | **已闭合**：改走 AutoJS Rhino（§8.2），CT 否决（§8.3） |
| R2 | AutoJS 无 fabric-1.21.1 产物 | **已闭合**：target + 产物 + mavenLocal 三件齐（§8.1） |
| R3 | Trinkets 3.10 / Accessories 1.1.0-beta 大版本跃迁 | 开放：先只保 Trinkets 单态，Accessories 延后 |
| R4 | NeoForge 21.1 伤害管线在 Fabric 无等价物 | 开放：Puzzles Lib + bridge mixin；用与 n21 相同的数值用例对照 |
| R5 | 1.21.1 组件体系（`DataComponents`） | 开放但低：新线无历史存档包袱，直接用 n21 形态 |
| R6 | `@EventBusSubscriber` 自动扫描在 Fabric 不存在（B2） | 开放：86 文件改显式注册 + 用 `SubscriptionAudit` 审计「注册了但没派发」 |
| R7 | Loom 剥内嵌 `jars`（B10） | 已有工具（`loom_embedded_jars.py`），按 §9 坑 4 用 |
| R8 | 测试环境不能与 fabric-1.20.1 逐件对齐（KubeJS / Carpet-Fixes / StarLight / Indium 缺件） | 开放：「同平台」承诺有折扣，需在 TESTING-SPEC 记录独立清单 |
| R9 | 双 fabric 线并存（run 目录 / 端口 / stop 判据） | 开放：`fabric.sh` 泛化 + 分侧目录（§9） |
| R10 | 工作量被低估 | 用 P0–P9 卡口；**P5 之前不宣布可用** |
| R11（新） | 依赖表未二次核验 | **已闭合**（2026-11-04 19:2x 25 项全通过，仅 collective 漂移到 8.77）；纪律保留：P0 每次开工前重跑 §6.4 |
| R12（新） | 截图窗口定位在 fabric 侧不可用 | P7 前修；否则 P7 判据退化为「日志 + AutoJS 读数」 |

## 11 待拍板（3 问，AutoJS 那条已关闭）

1. **starengine 第 5 平台**：确认出 `starengine-fabric-1.21.1`（含 GeckoLib vendor 的 fabric 根）——库仓发布矩阵 4 → 5。
2. **饰品后端**：1.21.1 上 Trinkets 3.10 与 Accessories 1.1.0-beta 只保一个（建议 Trinkets），还是继续双后端四态测试台？
3. **发布范围**：fabric-1.21.1 立刻进发布矩阵（5 jar）与 CI，还是先 dev-only 稳定一段时间？

## 12 工作量（按文件级数字，v1 实读口径）

| 工作包 | 规模 |
|---|---|
| 脚手架 + 构建 | ~6 文件 |
| 平台层 shim | ~85 类（~30 真新写） |
| 业务代码 | 277 复制 + 63 改名 + 79 改写 |
| 资源 | 1348 文件 + 6 个配置件 |
| bridge mixin | ~15 个（fabric-1.20.1 有 21 个可参照） |
| starengine 第 5 平台 | ~10 文件 + GeckoLib fabric 根 |
| 测试台 | ~200 处版本表 + 用例克隆 |
| 探针迁移 | 4 个 KubeJS → AutoJS JS |
| 文档与门禁 | CROSS-VERSION-DIFFS / TESTING-SPEC / AGENTS |

## 13 附录：v1 保留的实读证据（实施时逐条复核）

### 13.1 1.21.1 语义差（grep 计数：n21 vs fabric-1.20.1，v1 实读）
`ResourceLocation.fromNamespaceAndPath` 121 / 1；`new ResourceLocation(` **0 / 99**；`.component(` 12 / 0；`DataComponents.` 138 / 128；`StreamCodec` 83 / 7；`CustomPacketPayload` 140 / 1；`MapCodec` 20 / 0；`Holder<` 335 / 42；网络载荷文件 30 / 17。

### 13.2 数据包目录对照（`data/astral_dice/`）
- neo：`advancement` / `curios` / `damage_type` / `enchantment` / `loot_modifiers` / `loot_table` / `patchouli_books` / `recipe` / `tags{item,point_of_interest_type}`
- fabric-1.20.1：`accessories` / `advancements` / `damage_type` / `loot_tables` / `patchouli_books` / `recipes` / `tags{items,point_of_interest_type}`
- 配方 result：neo `{"count":1,"id":"..."}` vs fabric `{"item":"..."}`；**ingredient 段两者都还是 `{"item":…}`**（id 化是 1.21.2+）。

### 13.3 版本常量（v1 实读）
`pack_format` = `{"resource":34,"data":48}`（1.21.1）；Java = 21；Fabric Loader 0.20.1；Yarn `1.21.1+build.3`（本仓用 Mojmap，不需要）。

### 13.4 AutoJS 运行时 API 形态（Rhino 侧实测，写探针必用）
- `AutoJS.level` 是**方法**：`AutoJS.level()` → `ServerLevel[testworld]` / ClientLevel；`AutoJS.players()` → `Player[]`。
- `AutoJS.findEntities(...)` 返回**字符串** ⇒ 取实体用 `AutoJS.level().getEntity(id)`。
- `AutoJS.player('@a')` **不存在**。
- 动画控制器：`e.getAnimatableInstanceCache().getManagerForId(id).getAnimationControllers()`（v4：`getAnimationState()/getCurrentAnimation()/isPlayingTriggeredAnimation()`）。
- `Java.loadClass('int')` 非法（原语不能这么取）。

### 13.5 参考件清单
- 施工图：`porting/fabric-1.20.1/` 12 份（FABRIC-DIFFS / PORT_ANALYSIS / SKILL / VERSION_PINS / EVENT_API_RESEARCH / API_CHEATSHEET / MAPPING-1.5.0 …）。
- 上游源码：`ref/`（131 条目，含 fabric-api / loader / yarn / trinkets / puzzles-lib / CCA / jade / patchouli）。**警告**：`ref/fabric-api/gradle.properties:6` 是 `1.20-pre7` 量级 ⇒ **不能当 1.21.1 API 依据**，只能当结构参考。

---

重写：DSH（multi-dev-next），2026-11-04；v1 勘察稿归档于 `docs/features/archive/fabric-1211-port-feasibility-plan-v1.md`；标「v2 实读」的数字来自本会话命令输出，标「v1 实读」的需在实施前复算。

## 14 执行状态（2026-11-04 夜，本轮实测）

> 本节是**执行记录**，不是计划。用户当日裁决：「执行移植，**只进行 code_review 代码复核**」⇒ 本轮**未做任何实机/测试台验证**，验收依据 = 静态复核 + 机器读数（构建、datagen、jar 内容、行尾）。

### 14.1 阶段进度

| 阶段 | 状态 | 关键读数 |
|---|---|---|
| P0 预检 | ✅ | 代理 7897 通、25 个 slug 复验（Collective 8.75→8.77）、Loom 1.17.21 为硬上限（JEI 19.70 fabric 件 manifest 要求 1.18.2+，而 1.18 需 JVM25/Gradle 9.7） |
| P1 脚手架 | ✅ | `settings.gradle` 第五条 include；`fabric-1.21.1/build.gradle`（Loom 1.17.21 / toolchain 21 / officialMojangMappings / `-Xmaxerrs 4000`） |
| P2 平台层 | ✅ | 79 个平台镜像文件；`platform/network/SimpleChannel.java` 整体重写为「单一 CustomPacketPayload + BY_ID 路由」 |
| P3 业务代码 | ✅ | 编译错误 **1549 → 3064（-Xmaxerrs 4000 露全）→ 2538 → 642 → 456 → 57 → 0** |
| P4 资源 | ✅ | assets **893** / data **85**；972 次复制 sha256 全等；lang 三文件与 n21 逐字节相同 |
| P5 接线 | ✅ | fabric.mod.json 五组 entrypoints / 三份 mixin（49:49 一一对应，`JAVA_21`）/ `pack.mcmeta`（pack_format 34）/ 线级 AW 26 行 |
| P6/P7 实机与回归 | ⛔ 未做 | 用户本轮明确只做 code_review，实机留待放行 |

### 14.2 datagen 与产物（本轮实测）

- `./gradlew :fabric-1.21.1:runDatagen` ⇒ 生成 **369** 个文件（item 模型 153 + 配方 108 + 进度 108），与 `neoforge-1.21.1/src/generated/resources` **逐项对齐**；`src/generated` 与 `src/main/resources` 同路径交集 **0**。
- `./gradlew :fabric-1.21.1:build` ⇒ **BUILD SUCCESSFUL**；产物 `fabric-1.21.1/build/libs/astral_dice-26.4-SNAPSHOT.6+fabric_1.21.1.jar` = **2,992,358 B / 2330 条目 / 808 class / 165 模型 / 126 配方 / 113 进度 / 3 语言 / 3 份 mixin 配置 / pack.mcmeta / accesswidener**，无 `META-INF/jarjar|jars`、无 starengine 引用。
- 资源面与 n21 jar **各 1341 项**，差异仅平台专属：Fabric 多 `accessories/**`、`trinkets/**`、`c/tags/item/**`、`textures/gui/slot/*.png`；n21 多 `curios/**`、`loot_modifiers/**`、`neoforge/**`（Fabric 侧由 `loot/FabricLootInjector` 代码注入）。

### 14.3 本轮踩住并修掉的两个「上服就炸」缺陷（code_review 发现）

1. **`platform/network/SimpleChannel.java` 的 `encode` 用零长度缓冲**（原 `bufOf(new byte[0])`）：netty 的 `Unpooled.wrappedBuffer(byte[0])` 返回**不可写**的 `EMPTY_BUFFER`，随后 `writeResourceLocation` 必抛 `IndexOutOfBoundsException` ⇒ **每一次 `send` / `sendToServer`（含版本握手）全灭**。已改为 `new FriendlyByteBuf(Unpooled.buffer())`（可扩容），解码方向仍用 `bufOf(byte[])`。
2. **版本门在 Fabric 线上整条空转**（`network/VersionHandshake.java`）：`ServerPlayNetworking.canSend(player, channelId)` / `ClientPlayNetworking.canSend(channelId)` 传的是**旧式频道名**（`astral_dice:main`），FAPI 0.116 只认**已注册 payload type id**，该重载恒 `false` ⇒ 28 号 `VersionProbe` 一次也发不出去、200 tick 后只留 `AP_NET_VERSION_ABSENT`，错版本两端永不被拦。已改用 payload 类型重载：`ServerPlayNetworking.canSend(player, SimpleChannel.S2CPayload.TYPE)` 与 `ClientPlayNetworking.canSend(SimpleChannel.C2SPayload.TYPE)`（两个 `PacketType<?>` 重载在 fabric-networking-api-v1 1.3.10 已 `javap` 核存在）。

顺带补齐（同批次）：`SimpleChannel.MessageType` 增加**方向**字段并在 `dispatchC2S` / `dispatchS2C` 丢弃错路由（日志前缀 `AP_NET_ROUTE_DROP_C2S|S2C`），`ModNetwork.register()` 的 **31 条消息逐个显式标注方向**（C→S 13 条、S→C 18 条，依据是各自真实发送点，不是 handler 推测）。

### 14.4 AW 的两条 `accessible class` 是**必要**的（曾被误判为空操作）

`RenderType$CompositeState` 与 `RenderStateShard$TransparencyStateShard` 的**类文件头**是 public，但 javac 判可见性读的是外层类 `InnerClasses` 属性里的成员标志（仍是 `protected static [final]`）。可复跑证据：把这两个类型放进**异包**（如 `probe.pkg`）用 `javac -cp <1.21.1 merged jar>` 编译，不带 AW 时报「在 RenderType 中是 protected 访问控制」，带本 AW 后通过。⇒ 文件里这两条**保留**，注释已订正（原注释自相矛盾，已重写并附命令）。

### 14.5 行尾口径

`fabric-1.21.1/src/main/java` 下 **526 个 .java**：本轮把 15 个 LF-only 文件统一成 CRLF ⇒ 现 **CRLF 526 / 裸 LF 0 / 混行 0**，与仓内四条既有线一致。

### 14.6 未完项

1. **P6/P7 实机**：注册与服务端跑通、mixin 实际注入命中、`ExtendedScreenHandlerType` 运行期编解码、`data/**/enchantment/*.json` 的 datapack 解析 —— 全部只能进游戏才能验，本轮按裁决不做。
2. 第 11 节三条待拍板仍未答复（starengine 第 5 平台**已做**；饰品后端单双、是否进发布矩阵/CI 仍开放）。
3. `mixin/container` 的已知缺口（两线对等）：`Container#canPlaceItem` 路径（漏斗→箱子/木桶、Fabric Transfer API）不覆盖。
4. 发送侧仍未做方向断言（`send` 传 C→S 消息、`sendToServer` 传 S→C 消息仍会静默发出），路由层已能挡住接收侧。

### 14.7 代码复核（三轮 code_review + 一轮 mixin 静态核查）

| 轮次 | 范围 | 结论 | 处置 |
|---|---|---|---|
| 1 | 接线面 6 个文件/目录（fabric.mod.json / 三份 mixin 配置 / build.gradle / AW / datagen） | 1 个 P0 + 1 个误报 | P0：`SimpleChannel.encode` 零长度缓冲 ⇒ 已修；AW 两条 `accessible class` 被误判为空操作 ⇒ **反证后保留**（§14.4） |
| 2 | 网络层（platform/network + network/VersionHandshake + 入口） | 1 个 P1 + 1 个 P2 | P1 版本门空转、P2 C2S 无方向校验 ⇒ 均已修（§14.3） |
| 3 | 注册与初始化（`AstralDiceMod` / `AstralDiceClient` / `platform/registry/**` / `platform/fml/**`） | 1 个 P3 | `DeferredRegister.commit()` 的 `committed = true` 提前置位 ⇒ 已移到循环之后（`fabric-1.21.1/src/main/java/com/merlinkitsune/astral_dice/platform/registry/DeferredRegister.java:111-124`） |
| 4 | 29 个 mixin（bridge 20 / jade 2 / trinkets 1 / container 6）静态核查 | **29/29 注入点命中、无 InjectionError 风险**；1 个语义缺陷 D1 | D1 末影珍珠**重复派发** `ProjectileImpactEvent` ⇒ 已修（见下） |

第 3 轮的正面结论（可直接引用）：9 个 `DeferredRegister` 全部在 `onInitialize` 内提交且顺序固定（BLOCKS→ITEMS→EFFECTS→SOUNDS→RECIPE_SERIALIZERS→CREATIVE_TABS→MENU_TYPES→PARTICLE_TYPES→ENTITY_TYPES）；151 处 `@SubscribeEvent` 与公共侧 72 条 + 客户端 14 条登记求差集**无漏登记无重复**；`AstralDiceMod` / `platform/registry` / `platform/fml` 的常量池里 `net/minecraft/client` 与 `astral_dice/client` **0 命中**（专用服务端不会连锁加载客户端类）。

### 14.8 D1 的真相与修法（本轮最后修的一处语义缺陷）

- **事实**：`ThrownEnderpearl#onHit` 的字节码**偏移 2** 就是 `invokespecial super.onHit(HitResult)`；而 `ThrowableItemProjectile` / `ThrowableProjectile` 都**没有**声明 `onHit`（javap 计数 0）⇒ 该 super 实际解析到 `Projectile#onHit`，也就是被我们的 mixin 注入的方法。原注释（两个文件都写「珍珠覆写 onHit 且不调 super，两者互斥不会重复派发」）**在 1.21.1 字节码上不成立**。
- **后果**：不取消时一次落地派发**两次** `ProjectileImpactEvent`，取消路径只派发一次（次数随路径变化）。当前唯一订阅者 `item/sign/NancyLuSignItem` 写的是绝对截止时间（幂等），所以没有可见后果，但契约已破。
- **修法**：`mixin/bridge/ProjectileImpactBridgeMixin.java` 在 post 之前 `if (self instanceof ThrownEnderpearl) return;` —— 珍珠仍由 `EnderPearlTeleportBridgeMixin` 在 HEAD 无条件派发（含取消路径）⇒ **一次落地恒定一次事件**。两个文件的 javadoc 已按真实字节码重写。

### 14.9 抽查到的正面证据：mixin 在真实运行中确实 apply 了

`fabric-1.21.1/run/datagen/logs/debug.log`（Mixin 0.8.7，Env=SERVER）里能看到 `Mixing ... into ...` 成功记录：`LivingHurtBridgeMixin`、`ItemTossBridgeMixin`、`LivingUseItemFinishBridgeMixin`、`ExplosionStartBridgeMixin`、全部 container mixin、`ServerPlayerDimensionTravelBridgeMixin`、`MobFinalizeSpawnBridgeMixin` —— **无任何 `InvalidInjectionException`**。jade（client 列表）与 trinkets（目标类 datagen 期未加载）只在静态层面通过，需客户端启动日志才能补证。

### 14.10 五线整构建（本轮收尾）

`./gradlew build -x test`（五条线全量）⇒ **BUILD SUCCESSFUL**；仓根 `build/libs/` 现有五件：`astral_dice-26.4-SNAPSHOT.6+{neoforge_1.21.1, forge_1.20.1, neoforge_26.1.2, fabric_1.20.1, fabric_1.21.1}.jar`（2,663,131 / 2,712,182 / 2,840,878 / 2,994,607 / 2,994,174 B）。
