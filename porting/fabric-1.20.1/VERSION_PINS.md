# 版本钉值表（fabric-1.20.1 移植；2026-09-29 核验）

> 核验来源：meta.fabricmc.net / Modrinth API / maven.fabricmc.net / GitHub Releases（均为实测查询，非记忆）。
> 用途：执行移植时**直接照抄**，禁浮动版本（与仓库「坐标写全、版本钉死」约定一致）。

## 工具链

| 项 | 钉值 | 备注/证据 |
|---|---|---|
| Minecraft | **1.20.1** | — |
| Java toolchain（编译/运行游戏） | **17** | 与 forge-1.20.1 一致；本机实测可用 = `~/.gradle/jdks/eclipse_adoptium-17-amd64-windows.2` |
| Gradle wrapper | **9.2.1**（现状，不动） | `gradle/wrapper/gradle-wrapper.properties`（aliyun 镜像） |
| fabric-loom | **1.14.10** | ⚠️ **选型理由（实测）**：loom 1.16 起要求 Gradle ≥9.4、1.17 到 9.5；wrapper 9.2.1 不带升（会牵动三条既有线），故钉 **1.14 线最新补丁 1.14.10**（官方注记「requires Gradle 9.2」⇒ 9.2.1 满足）。⚠️ **Loom 运行 JVM 需 21+，而 toolchain 仍 17** —— 运行 `gradlew` 用 JAVA_HOME=21（本机 `C://Program Files\Zulu\zulu-21`），编译由 toolchain 17 完成；写进踩坑清单。 |
| fabric-loader | **0.19.5** | meta.fabricmc.net 实测 1.20.1 最新 stable（0.19.4/0.19.3 均 non-stable） |
| 命名映射 | **`loom.officialMojangMappings()`**（Mojang 官方映射） | 用户裁决（2026-09-29）：消费方 254 文件 + 库 44 文件 + 18 mixin 全为 Mojmap 命名，用 Yarn 需全量改名 ⇒ 禁用 Yarn。refmap 由 loom remapJar（official→intermediary）生成 |
| intermediary | 1.20.1（随 loader 元数据自动钉） | meta.fabricmc.net |

## 前置/运行依赖（fabric-1.20.1 侧）

| 模组 | 钉值（文件名） | 坐标参考 | 替代关系 |
|---|---|---|---|
| **Trinkets** | **3.7.2**（`trinkets-3.7.2.jar`） | `maven.modrinth:trinkets:3.7.2` / `curse.maven:trinkets-341284:5173501` | **替 Curios**（饰品栏；包 `dev.emi.trinkets.api`） |
| **Cardinal Components API** | **5.2.3**（**仅编译期，已从运行前置移除**） | ⚠️ **实测坐标 = `dev.onyxstudios.cardinal-components-api:cardinal-components-base:5.2.3`**（groupId **不是** `dev.onyxstudios.cca`；可从 `https://maven.ladysnake.org/releases` 解析）。包名仍为 `dev.onyxstudios.cca.api.v3` | **替 Capability/Attachments**（⚠️ 但 1.20.1 Fabric **另有** `fabric-data-attachment-api-v1`，见 SKILL §勘误） |
| **Fabric API** | **0.92.12+1.20.1**（`fabric-api-0.92.12+1.20.1.jar`） | `maven.modrinth:fabric-api:0.92.12+1.20.1` | 运行必需（1.20.1 支持止于 0.92 线） |
| **StarEngine Lib（fabric 变体）** | **1.0.6**（库新增平台后发布） | `com.merlinkitsune.starenginelib:starengine_lib-fabric-1.20.1:1.0.6`（mavenLocal） | 跨仓硬前置；**先行发布**（四线同号 bump） |
| Patchouli（手册，**纳入首版**） | **1.20.1-85-FABRIC**（`Patchouli-1.20.1-85-FABRIC.jar`） | `maven.modrinth:patchouli:1.20.1-85-fabric`（待反查确切 versionId） | 手册（Forge 版为 1.20.1-85-forge，同 build 号有 Fabric 变体） |
| KubeJS（测试探针） | **2001.6.5-build.26+fabric**（`kubejs-fabric-2001.6.5-build.26.jar`） | `curse.maven:kubejs-238086:`（fabric 变体 fileId 待反查） | 与 forge 侧 2001.6.5-build.26 对齐 |
| Rhino（KubeJS 前置） | **2001.2.3-build.10+fabric**（`rhino-fabric-2001.2.3-build.10.jar`） | `curse.maven:rhino-416294:` | 同上 |
| Architectury API | **9.2.14+fabric**（`architectury-9.2.14-fabric.jar`） | `curse.maven:architectury-api-419699:` | 同上 |
| JEI | **15.62.0.216**（`jei-1.20.1-fabric-15.62.0.216.jar`） | `curse.maven:jei-238222:` | 配方查看 |
| Jade | **11.13.3+fabric**（`Jade-1.20-Fabric-11.13.3.jar`） | `maven.modrinth:jade:` | HUD |
| ModernFix | **5.25.2+mc1.20.1**（`modernfix-fabric-5.25.2+mc1.20.1.jar`） | `maven.modrinth:modernfix:` | 启动优化/测试台锚点 |
| FerriteCore | **6.0.1**（`ferritecore-6.0.1-fabric.jar`） | `maven.modrinth:ferrite-core:` | 内存优化 |
| Sodium | **0.5.13**（`sodium-fabric-0.5.13+mc1.20.1.jar`） | `maven.modrinth:sodium:` | **替 Embeddium**（仅运行环境，非代码联动） |
| Iris | **1.7.6+1.20.1**（`iris-1.7.6+mc1.20.1.jar`） | `maven.modrinth:iris:` | **替 Oculus**（仅运行环境） |
| Cloth Config | **11.1.136+fabric**（`cloth-config-11.1.136-fabric.jar`） | `curse.maven:cloth-config-348521:` | 配置 GUI（可选；本体配置保留 TOML 自实现） |
| Fabric Carpet（玩家 bot） | **1.4.112**（`fabric-carpet-1.20-1.4.112+v230608.jar`） | `maven.modrinth:carpet:1.4.112`（待反查） | 替 neoforge-carpet；26.1.2 线有真实运行经验 |

## 裁剪（无 Fabric 1.20.1 版 ⇒ 联动整段裁掉）

| 联动 | 实测 | 处理 |
|---|---|---|
| **Iron's Spells 'n Spellbooks** | Modrinth 反查**无 fabric/1.20.1 版** | fabric 线裁剪（事件接线 `IronSpellbooksCompat` 删除） |
| **Bountiful（赏金板）** | 无 Fabric 版（Forge/NeoForge only） | fabric 线裁剪（`data/bountiful/**` 与守门 `verify_bountiful_pools.ps1` 不进 fabric 线） |
| **Mixin Booster / MixinRuntimeGate** | Fabric Loader 自带 Mixin | **整条删除**（mods.toml 的 mixinbooster 依赖、`init/MixinRuntimeGate.java`） |
| Embeddium / Oculus | Forge-only | 用 Sodium/Iris 替（仅运行环境） |
| neoforge-carpet | NeoForge-only | 用 fabric-carpet 替 |

## 参考源（ref/ 内已克隆，供 API 取证）

| 仓 | 分支/标签 | 用途 |
|---|---|---|
| `ref/fabric-loader` | 默认分支（c75cac1） | loader 内部、entrypoint/mixin 机制 |
| `ref/fabric-api` | `1.20`（96a7932） | **Fabric API 1.20.x 源码**（事件/战利品/网络/渲染等模块路径证据） |
| `ref/trinkets` | `1.20.1`（7cb63ce） | **Trinkets API 1.20.1 源码**（`dev.emi.trinkets.api`；槽位 JSON 布局样例） |
| `ref/cardinal-components-api` | `1.20`（1a9bf0a, = 1.20.4 线） | **CCA API 源码**（`dev.onyxstudios.cca.api.v3`；1.20.x API 稳定） |
| `ref/minecraftforge` | `1.20.x`（71d814f） | **Forge 1.20.1 源码**（API 差异对照的一手参照） |
| `ref/yarn` | `1.20.1`（9672e1f） | Yarn 映射（备选对照；**不采用**，仅留作字段名对照参考） |
| `ref/patchouli` | `1.20.x`（f561109，多平台 Fabric/NeoForge/Xplat 结构） | Patchouli Fabric 版结构 |

> ⚠️ 上表坐标列为「建议坐标」，**逐一以 `tools/check_mod_sources.ps1`（R1/R1b/R2/R3 守门）反查的最终坐标为准**；Modrinth/CF 的 fileId 精确值在执行移植时用 Modrinth API `v2/project/<slug>/version?loaders=["fabric"]&game_versions=["1.20.1"]` 重新反查一次（本表版本号已为当时实测值）。


---

## 订正（2026-09-29 实测）

### CCA 已从「运行前置」降级为「编译期 only」

- **用户裁决**：移除 CCA，组件/持久化改用 **Fabric API 自带的 `fabric-data-attachment-api-v1`**（随 `fabric-api` 分发）。
- 库与消费方的 `fabric.mod.json` `depends` 里 **不再有 `cardinal-components-*`**，
  改为声明 `fabric-data-attachment-api-v1`。
- 但 `cca_version=5.2.3` **必须保留**，且依赖降级为 **`modCompileOnly`**：
  **Trinkets 3.7.2 的 `TrinketComponent extends ComponentV3`**（javap 实证），
  javac 解析 `TrinketsApi#getTrinketComponent` 的签名时必须能加载 CCA 类型，
  否则报「无法访问 ComponentV3」。
- 同理，`maven.ladysnake.org` 仓库也只为**编译期解析**而保留。
- **运行期 CCA 由 Trinkets 自己 hard-depends**（其 `fabric.mod.json` 声明
  `cardinal-components-base/entity >=3.0.0-0`）⇒ 玩家不再需要为**本模组**单独安装 CCA；
  但要彻底摆脱它只能更换饰品方案。

### 实测确认的钉值（与原表一致，附证据）

| 项 | 钉值 | 证据 |
|---|---|---|
| fabric-loom | 1.14.10 | 与 wrapper 9.2.1 实测兼容（`BUILD SUCCESSFUL`） |
| fabric-loader | 0.19.5 | **内置 MixinExtras**（0.17.0+）⇒ `@WrapOperation` 等零依赖可用 |
| Trinkets | 3.7.2 | `Trinket.getModifiers(...)` / `TrinketInventory` 动态槽位 API 实测确认 |
| CCA | 5.2.3 | **仅 `modCompileOnly`** |
| fabric-api | 0.92.12+1.20.1 | 内含 `fabric-data-attachment-api-v1`（`META-INF/jars/`）+ `fabric-loot-api-v2` |

---

## 追加：Accessories 兼容接入（2026-09-29 实测）

用户需求：「饰品**同时**可以在 Trinkets 和 Accessories 上正常工作」。
定位 = **软依赖** —— `fabric.mod.json` 里只进 `recommends`、**绝不进 `depends`**；
缺席时照旧走 Trinkets，在场时 Accessories 成为主通道（双源聚合，见 `compat/curios/CuriosApi`）。

| 依赖 | 钉值 | Modrinth versionId | 说明 |
|---|---|---|---|
| Accessories | `1.0.0-beta.48+1.20.1` | `A7wwzJ0e` | Wisp Forest 数据驱动饰品库。**1.20.1 线官方已停更**（见其 issue #271 与 `INFO_ABOUT_UPDATES.md`）⇒ 钉死不再跟随 |
| Cloth Config | `11.1.136+fabric` | `2xQdCMyG` | **Accessories 的硬前置**（其 `fabric.mod.json` `depends: cloth-config >=11.1.118`）⇒ dev 运行期必须一并投放 |
| Architectury | `9.2.14+fabric` | `WbL7MStR` | **KubeJS 的硬前置**（`>=9.1.12`）。与本模组无关，但 dev 测试环境装了 KubeJS ⇒ 必须补 |
| CCA | `5.2.3` | ladysnake maven | 编译期 `modCompileOnly` **+ dev 运行期 `modRuntimeOnly`**；生产由 Trinkets 的传递依赖满足 |

### ⚠️ 不要用官方的 Trinkets 兼容层

`Trinkets Compat Layer for Accessories`（Modrinth `accessories-tc-layer`）在 **1.20.1 已停更**，
1.21.1+ 的 `Accessories Compatibility Layer` 也不覆盖 1.20.1；且兼容层本身只能解决
「Trinkets API 被重定向」，**槽位数据仍要本模组自己写**。
结论：走**原生适配**（`io.wispforest.accessories.api`），见 `SKILL.md` §8。

### dev 环境实测缺口（与本模组代码无关，但会挡住启动）

1. **Loom 1.14 在 dev 环境不展开 mod 依赖的内嵌 JarJar**：
   `accessories` 内嵌 `endec/gson/netty`、`patchouli` 内嵌 `fiber` ⇒ 两者 entrypoint 初始化即
   `NoClassDefFoundError`。修复脚本 = `scripts/devtools/unpack_nested_mod_jars.py`（只解**纯库**白名单，
   不能解内嵌的 MC mod，否则 intermediary 版在 dev 加载不了）。
2. **Sodium 0.5.x 在 dev 要求 LWJGL 3.3.2**（1.20.1 自带 3.3.1）⇒ 客户端启动即
   `currently active LWJGL version is not compatible`；本轮用 `-PtestSodium=false` 临时排除。

---

## 追加：事件桥前置 Puzzles Lib（2026-09-29，用户裁决「许可引入」）

**背景**：Fabric API 1.20.1 **没有可改伤害值的事件**（`ServerLivingEntityEvents.ALLOW_DAMAGE`
只能取消、不能改 `amount`），而本模组 109 个处理器里承重最重的正是伤害/死亡/效果这一族。
用户要求「必须解决所有事件 mixin 桥，它是完美移植的必要条件」，并解除「不引入第三方」的约束。

| 依赖 | 钉值 | Modrinth versionId | 定位 |
|---|---|---|---|
| Puzzles Lib | `8.1.33-1.20.1-Fabric` | `N8gFdljq` | **硬依赖**（`fabric.mod.json` 的 `depends.puzzleslib = >=8.1.33`） |
| Forge Config API Port | `8.0.3-1.20.1-Fabric` | `HvR3IdRE` | Puzzles 的**传递硬前置**（dev 运行期需投放；玩家由 Loader 提示安装） |
| puzzlesaccessapi | 20.1.1 | — | Puzzles **内嵌**（`META-INF/jars/`）⇒ dev 需 `scripts/devtools/unpack_nested_mod_jars.py` 展开 |

### ⚠️ 玩家安装面变化（必须在发布说明里写清）

安装本模组后会**多出两个前置**：Puzzles Lib 与 Forge Config API Port。

### ⚠️ 映射必须按注入点核对，不能按名字

实测 Puzzles 的 `LivingEntityFabricMixin` 字节码后确认：其 `LivingHurtCallback` 注入在
**`LivingEntity#actuallyHurt` 的 HEAD** —— 而 Forge 的 `ForgeHooks.onLivingDamage` 正是这个位置
⇒ 它对应的是 **`LivingDamageEvent`**，**不是** `LivingHurtEvent`（后者在 `hurt()` 内、
`actuallyHurt` 调用之前）。完整映射表见 `EVENT_API_RESEARCH.md` §5.2。
