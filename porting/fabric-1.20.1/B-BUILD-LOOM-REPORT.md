# B — fabric-1.20.1 Loom 升级与 JEI 编译期依赖报告

> **状态：未完成（阻塞）**。目标 Loom 版本已在 `fabric-1.20.1/build.gradle` 落盘，但"JEI 重新进入 compileClasspath"
> **没有在真实环境跑通**：本仓共享的 Gradle wrapper 是 **9.2.1**，而任何 ≥1.17 的 Loom 都要求 Gradle ≥9.5.0。
> 该 wrapper 由四条线共用，改它属跨线决策，本批**未执行**，留给用户裁决（见 §2.4）。下面每条结论都标注了它来自实跑命令还是静态取证。

## 0. 本批实际做过的验证（先声明，防误读）
- **跑过**：本机 Gradle 9.2.1 / 9.5.1 / 9.8.2 × Loom 1.15.4…1.19.3 的**插件解析与配置阶段**（原始报错见 §1、§2.3）。
- **跑过**：Loom 1.19.3 在 Gradle 9.8.2+JDK25 下加载完整 `fabric-1.20.1/build.gradle`（含 loom 块/runs/新 JEI 依赖）并打印版本横幅。
- **没跑过**：`dependencies --configuration compileClasspath`（**因此"jei 那一行"拿不到**）、`tasks --all`（**因此 runClient/runServer/runDatagen/push* 的存在性未验证**）、任何编译、任何 remapJar。
- 原因不是源码立牌，而是**执行沙箱的地基限制**：本沙箱 JVM 的 `Files.isWritable()` 对一切路径返回 false，JDK zipfs 据此把所有 jar 判为只读 ⇒ Loom 的 merge / AW transform / mod remap 必然抛
  `java.nio.file.ReadOnlyFileSystemException`（最小复现 `build/_Z.java`）。1.14.10 之所以能跑完配置期，只因缓存全命中、零写入。

## 1. 取证段：旧 Loom 拒绝 JEI 的证据链
### 1.1 没能抓到的那条报错（如实记录）
**我没有在 Loom 1.14.10 下抓到"Loom 拒绝 JEI"的运行时原文**。原因：`ArtifactMetadata.validateLoomVersion` 在依赖处理阶段才执行，而本沙箱在更早的 Minecraft setup 阶段就已抛 ReadOnlyFileSystemException（`build/_loom-jei-evidence*.log`），走不到门禁那一行。
> 早期曾插入临时探针 `modImplementation "maven.modrinth:jei:pQiaGCAN"`（现已转为正式依赖），但同样止步于 setup。

### 1.2 替代证据（静态，可复核）
1. **JEI 坐标**：`maven.modrinth:jei:pQiaGCAN` 是 **fabric 变体**（`jei-1.20.1-fabric-15.62.0.216.jar`，2,263,284 B）；
   forge 变体是 `p5jzDqhi`（`maven.modrinth:jei:15.62.0.216` 解析到的是 forge/通用 jar，Loom 判 isFabricMod=false ⇒ 完全不过门禁，别用）。
2. **JEI 的 manifest 实测**（解包读到）：`Fabric-Loom-Version: 1.17.20`、`Fabric-Minecraft-Version: 1.20.1`、`Fabric-Jar-Type: classes`、`Fabric-Loom-Mixin-Remap-Type: static`，含 `fabric.mod.json`、`jei.mixins.json`。
3. **门禁代码**：1.14.10 的 `net/fabricmc/loom/configuration/mods/ArtifactMetadata.class` 含字符串 `newer version of Loom`，由 `validateLoomVersion(String,String)` 抛出，且只在 isFabricMod=true 时调用。

### 1.3 门禁的判定语义（javap 反编译 1.19.3 的 ArtifactMetadata，1.14.10 同族）
```
validateLoomVersion(modLoom, projectLoom, modId):
  if (projectLoom.equals("0.0.0+unknown")) return;
  a = modLoom.split("."); b = projectLoom.split(".");
  for i in 0..1:                       // 只比前两段(major.minor),第三段不参与
     if (parseInt(a[i]) > parseInt(b[i])) throw IllegalStateException(
        "Mod %s was built with Loom %s; this project uses Loom %s. Upgrade this project's Loom version.");
     if (parseInt(a[i]) < parseInt(b[i])) break;
```
⇒ **相等即放行**。JEI 是 1.17.20，所以项目 Loom 只要 major.minor ≥ **1.17** 就过门禁（1.17.20 本身也够）。

## 2. 升级段
### 2.1 目标：1.19.3（maven.fabricmc.net 的 `<release>` 实测 = 1.19.3；`<latest>` = 1.19-SNAPSHOT，按用户裁决不用 SNAPSHOT）
### 2.2 每个候选版本的硬门槛（`.module` 元数据 + 实跑报错，两路一致）
| Loom | 要求 `org.gradle.plugin.api-version` | 要求 daemon JVM | 在本仓(Gradle 9.2.1/JDK21) |
|---|---|---|---|
| 1.15.4 / 1.15.5 | 9.2.0 | 21 | 可解析，但 15 < 17 ⇒ **JEI 门禁仍拒** |
| 1.16.1–1.16.3 | 9.4.0 | 21 | 不可解析（需 Gradle ≥9.4） |
| 1.17.0–1.17.21 | 9.5.0 | 21 | 不可解析（需 Gradle ≥9.5） |
| 1.18.1/1.18.3/1.18.4 | 9.7.0（1.18.4 实测） | **25** | 双重不可行 |
| 1.19.3 | 9.8.0 | **25** | 双重不可行 |

### 2.3 原始报错（整段摘，均为本机实跑）
1. Loom **1.19.3**，wrapper 9.2.1 + JDK21（`build/_loom193-config.log`）：
   ```
   > Could not resolve net.fabricmc:fabric-loom:1.19.3.
      > Plugin net.fabricmc:fabric-loom:1.19.3 requires at least Gradle 9.8.0. This build uses Gradle 9.2.1.
   ```
2. Loom **1.18.4**，wrapper 9.2.1 + JDK21（`build/_loom184-config.log`）：
   ```
   > Could not resolve net.fabricmc:fabric-loom:1.18.4.
      > Dependency requires at least JVM runtime version 25. This build uses a Java 21 JVM.
   ```
3. Loom **1.18.4**，wrapper 9.2.1 + **JDK25**（`build/_loom184-jdk25.log`）——JVM 门槛过后卡 Gradle：
   ```
   > No matching variant of net.fabricmc:fabric-loom:1.18.4 was found ... attribute 'org.gradle.plugin.api-version'
     with value '9.7.0' and the consumer needed ... '9.2.1'
   ```
4. Loom **1.17.21 / 1.17.20**，wrapper 9.2.1（`build/_loom-1.17.21-config.log`、`_loom-1.17.20-config.log`）：同上形态，`value '9.5.0' ... needed '9.2.1'`。
5. **正向验证 A**：独立解压的 **Gradle 9.8.2 + JDK25**（未动仓内 wrapper）跑 Loom **1.19.3**（`build/_g98-loom193.log`）：
   ```
   > Configure project :fabric-1.20.1
   Fabric Loom: 1.19.3
   ~~~~~ Using of the official minecraft mappings is at your own risk! ~~~~~
   FAILURE: ... > Failed to setup Minecraft, java.nio.file.ReadOnlyFileSystemException: null
   ```
   ⇒ **整份 build.gradle 在 1.19.3 下零改动即被接受**（loom 块、runs、sourceSets、新增 JEI 依赖全部通过求值），失败点纯属沙箱写盘。
6. **正向验证 B**：**Gradle 9.5.1 + JDK21** 跑 Loom **1.17.21**（`build/_g951-loom11721.log`）：
   ```
   Fabric Loom: 1.17.21
   > Failed to setup Minecraft, java.lang.RuntimeException: ... Failed to apply transformation to
     net/minecraft/client/gui/GuiGraphics.class
   ```
   ⇒ 1.17.x 路线**不需要 JDK25**，只需要 Gradle ≥9.5。

### 2.4 最终结论与待裁决项
- **结论**：Loom 1.19.3 与现有 `build.gradle` **DSL 完全兼容**（无需改 accessWidener / interfaceInjection / mixin / runs 写法）；真正的门槛是**构建工具链**，不是 Minecraft 1.20.1 兼容性。
- **硬阻塞**：`gradle/wrapper/gradle-wrapper.properties` 当前是 **9.2.1**，由四条线共用。不升它，则 1.17+ 任何 Loom 都无法解析 ⇒ **JEI 永远进不了编译期**。
  - 路线 A（最小改动）：wrapper → **9.5.1**，Loom → **1.17.21**（JDK21 不变，已验证能加载）。
  - 路线 B（用户原定目标）：wrapper → **9.8.2**，Loom → **1.19.3**，且 **daemon 必须 JDK25**（本机 `C:/Program Files/Zulu/zulu-25` 存在）。
  - 路线 C（不升 wrapper）：只能停在 Loom ≤1.15.x，JEI 只能继续手工投放，8 个 compat/jei 类无法恢复。
- **本批的选择**：按要求把 `fabric-1.20.1/build.gradle` 定在 **1.19.3 + JEI modImplementation**（即路线 B 的终点状态），**没有**改共享 wrapper。
  ⚠️ 因此在 wrapper 仍是 9.2.1 的机器上，**fabric 这条线当前无法配置**（报上面第 1 条）。若决定走路线 A，把 build.gradle 第 14 行改成 `1.17.21` 即可。

### 2.5 build.gradle 改动（相对 `build/_build.gradle.orig-1.14.10`，14 增 9 删，保持 CRLF）
```groovy
 // 第 2 行:头部注释
-// Java 17 · Mojang 官方映射(officialMojangMappings)· Fabric Loom 1.14.10
+// Java 17 · Mojang 官方映射(officialMojangMappings)· Fabric Loom 1.19.3

 // 第 14 行:插件版本
-    id 'fabric-loom' version '1.14.10'
+    id 'fabric-loom' version '1.19.3'

 // 第 172 行起:JEI 从注释态改为编译期依赖
-    // ⚠️ JEI 15.62.0.216 的 fabric 产物由 **Loom 1.17.20** 构建 ⇒ 被 Loom 1.14.10 的
-    //    「Mod was built with a newer version of Loom」门禁拒绝(实测)。JEI 不参与编译,
-    //    故改为**运行时手工投放**:把 jei-1.20.1-fabric-15.62.0.216.jar 放进 run/server/mods/ 与 run/client/mods/。
+    // JEI(物品/配方查看器;compat/jei 适配层的编译期依赖)
+    // 历史:JEI 15.62.0.216 的 fabric 产物由 **Loom 1.17.20** 构建,曾被本线的 Loom 1.14.10
+    //   以「Mod was built with a newer version of Loom」门禁拒绝,故只作 modRuntimeOnly 手工投放。
+    //   2026-11-01 本线 Loom 升至 1.19.3(1.19.3 > 1.17.20)门禁放行,恢复为编译期依赖。
+    // 坐标:Modrinth version id `pQiaGCAN` = jei-1.20.1-fabric-15.62.0.216.jar
+    //   (与 gradle.properties 的 jei_version=15.62.0.216 同版;forge 变体 id 是 p5jzDqhi,别混用)。
+    modImplementation "maven.modrinth:jei:pQiaGCAN"
```
其余改动**仅注释同步**（AutoJS 段与 mixin 段里的版本数字、interfaceInjection 注释补一句 1.19.3 的 convention(true) 实证）。
**未改**：accessWidenerPath、interfaceInjection 默认开关、runs（client/server/datagen 及三个 `-Dfabric-api.datagen*` 开关 + strict-validation）、`include()` starengine、四个 push 任务、archivesName、sourceSets 纳入 `src/generated/resources`、`gradle.properties`（`jei_version=15.62.0.216` 原样可用，无需新增键）。

## 3. 验证段（缺什么就写缺）
- `dependencies --configuration compileClasspath`：**未取得输出**（沙箱 setup 阶段即挂）。⇒ "JEI 出现在依赖树里"**未验证**。
- `tasks --all`：**未取得输出**。⇒ `runClient / runServer / runDatagen / pushToDevRun / pushToRootBuild / pushToGame` 的存在性**未验证**（静态上看，push 四任务是本脚本自建、与 Loom API 无关；runs 的 DSL 已由 2.3-5 的横幅运行证明可被 1.19.3 接受）。
- 已取得的**间接**证据：Loom 1.19.3 完整求值了含 `modImplementation "maven.modrinth:jei:pQiaGCAN"` 的脚本，说明该配置项被 Loom 的 remap 配置容器接受（否则会在求值期抛 "Could not find method modImplementation"）。
- Loom 1.19.3 的 `LoomGradleExtensionAPI` 逐项 javap 核对：`officialMojangMappings()`、`getAccessWidenerPath()`、`runs(...)`、`getInterfaceInjection()`、`mixin(...)` 全在；`RunConfigSettings` 的 `client/server/ideConfigGenerated/runDir/vmArg/programArg` 全在；`InterfaceInjectionExtensionAPI` 的 `convention(true)` 未变。

## 4. 风险段
- **refmap**：注解处理器仍默认关、refmap 仍由 remapJar 产出；`mixin { }` DSL 已换成 `MixinExtensionAPI`，将来显式配置需改写法。本批未生成/未检查任何 refmap。
- **remapJar 产物命名**：push 任务用 `staleJarMatcher = /astral_dice-.+\+fabric_1\.20\.1\.jar/` 清扫旧产物，依赖 Loom 的文件名规则；1.19.3 若改命名会静默失配 → 旧 jar 残留。
- **mod 配置注入**：`modImplementation` 仍自动创建；但 AutoJS 的 `cc.sighs.autojs:…`（mavenLocal、非 Loom 产物）在新版是否仍被识别**未验证**。
- **runs / datagen**：`-Pquickplay`/`-PdevUsername`/`-PpreloadClasses` 走 `programArg`/`vmArg`，API 未变；1.19 新增 `appendProjectPathToDisplayName` 等属性可能影响 IDE 配置名（任务名应仍是 runClient/runServer），**未实跑**。
- **wrapper 的跨线影响**：升到 9.5.1/9.8.2 会同时作用于 neoforge-1.21.1 / forge-1.20.1 / neoforge-26.1.2。本批想验证另三线是否兼容，但 `:其他线:tasks` 必然先配置 `:fabric-1.20.1`，被它的 Loom 失败连坐，**未能测出**。⇒ 升 wrapper 前必须先确认另三线在其上的兼容性（ForgeGradle / NeoForge MDG 对新 Gradle 的容忍度）。
- **缓存副作用**：本批多次失败的 Loom 运行删掉了 `~/.gradle/caches/fabric-loom/` 下部分中间产物（merged jar / AW 处理后 jar 等），并且探测时往 `minecraftMaven/.../1.20.1-loom.…-v2` 播过种子。
  真实环境下首次构建会重下重算，**需要网络与若干分钟**；必要时删掉 `~/.gradle/caches/fabric-loom/1.20.1` 与 `minecraftMaven/net/minecraft/minecraft-merged` 让它彻底重来。

## 5. 对 8 个 compat/jei 类的恢复条件
- 恢复条件是**门禁放行 + 真实 resolve 成功**。按 §1.3 的判定语义，Loom ≥1.17 就足以放行 JEI 15.62.0.216；所以**只要 wrapper 到位（9.5.1 或 9.8.2）、配置期转绿，条件即满足**。
- **本批未写这 8 个类**（任务书要求）。建议下一批：`B-JEI-COMPAT`，前置门槛写死两条硬判据——
  ① `gradlew :fabric-1.20.1:dependencies --configuration compileClasspath | grep -i jei` 有命中；
  ② `gradlew :fabric-1.20.1:tasks --all` 里 runClient/runServer/runDatagen/push* 全在。
  两条不过就别开写，否则 8 个类只会变成新的编译红灯。
