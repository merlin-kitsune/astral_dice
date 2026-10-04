# 审计：StarEngine Lib 的第三方前置

日期：2026-09-22 · 对象：`F:\MCProject\starengine_lib` 1.0.0（远端 `main` = `9029e2b`）· 只读审计，未改动库内任何文件

## 结论速览

| 问题 | 结论 |
|---|---|
| 库自身是否需要 **Curios API**？ | **库自身零调用**，但 **`forge-1.20.1` 产物把它声明成了必需前置**（元数据层面的硬门槛） |
| `1.20.1` 侧的库是否依赖 **Mixin Booster**？ | **完全不依赖** —— 库无 mixin、无 Mixin 运行时引用、无相关元数据 |

⇒ 库当前声明的第三方前置只有一项：**Curios（仅 forge-1.20.1）**。

---

# 一、Curios

## 1.1 三平台产物逐项核对

| 产物 | `mods.toml` 依赖块 | 引用 Curios 的 class |
|---|---|---|
| `starengine_lib-neoforge-1.21.1-1.0.0.jar` | neoforge `type=required [21.1,21.2)` / minecraft | **0 个** |
| `starengine_lib-forge-1.20.1-1.0.0.jar` | forge `mandatory=true [47.4.10,48)` / minecraft / **curios `mandatory=true [5,6)`** | **1 个**：`com/merlinkitsune/starenginelib/item/CuriosCompat.class` |
| `starengine_lib-neoforge-26.1.2-1.0.0.jar` | neoforge `type=required [26.1.0.0,26.2)` / minecraft | **0 个** |

（判据：`zipfile` 解 jar 读 `mods.toml` + 逐 `.class` 扫常量池里的 `top/theillusivec4/curios`。）

## 1.2 Curios 在库里的全部出现点（仅 3 处，全在 forge 侧）

| 位置 | 内容 | 性质 |
|---|---|---|
| `forge-1.20.1/build.gradle` | `modImplementation "maven.modrinth:curios:IPQlZkz1"`（+ Modrinth maven 仓库） | 编译期依赖 |
| `forge-1.20.1/.../item/CuriosCompat.java` | 1.20.1 → `Optional` 的 shim（包装 `CuriosApi.getCuriosInventory(...).resolve()` 等） | 公共 API，**只给消费方用** |
| `forge-1.20.1/src/main/templates/META-INF/mods.toml` | `modId="curios" mandatory=true versionRange="[5,6)" ordering="AFTER" side="BOTH"` | 运行期**硬前置** |

**库自身零调用**：全仓 grep `CuriosCompat`（排除定义文件）只命中 `build.gradle` 的两行注释；
库内没有任何入口会加载该类。`item/CuriosCompat.java` 是实现者，不是使用者。

## 1.3 `mandatory=true` 的实际语义（加载器源码取证）

Forge `1.20.1-47.4.10` · `fmlloader-…-sources.jar` · `net/minecraftforge/fml/loading/ModSorter.java`：

```java
final var missingVersions = modRequirements.stream()
        .filter(mv -> (mv.isMandatory() || modVersions.containsKey(mv.getModId())) && this.modVersionNotContained(mv, modVersions))
        .collect(toSet());
final long mandatoryMissing = missingVersions.stream().filter(IModInfo.ModVersion::isMandatory).count();
if (mandatoryMissing > 0) { LOGGER.error(LOADING, "Missing or unsupported mandatory dependencies:\n{}", …); }
…
.map(mv -> new ExceptionData(mv.isMandatory() ? "fml.modloading.missingdependency"
                                              : "fml.modloading.missingdependency.optional", …))
```

- `mandatory=true` 且版本不满足 ⇒ `fml.modloading.missingdependency`（**致命**）⇒ **整包拒载**，与该类是否被调用无关。
- **同一行的判据给出了「可选集成」的标准写法**：`mv.isMandatory() || modVersions.containsKey(mv.getModId())`
  ⇒ `mandatory=false` 时「**装了才校验版本、不装静默放行**」，配 `ordering="AFTER"` 即可。

## 1.4 当前实际影响

| 维度 | 事实 |
|---|---|
| 玩家侧 | **无影响** —— 消费方 `astral_dice` 三线**自己**都把 curios 声明为必需（1.20.1 `[5,6)`、1.21.1 `[9,)`、26.1.2 `[15,)`），1.20.1 源码另有 **186 处** `CuriosCompat` 调用点 |
| 架构侧 | **耦合**：库替消费方做了决定。任何 1.20.1 的消费方，即使不做饰品集成，也被库的 `mandatory=true` 卡住必须装 Curios |
| 口径侧 | 与库内既有注释**自相矛盾**：neo 两线的 `build.gradle` 明写「本库不引入任何第三方模组依赖」；README §3 则如实写「库的必需前置只剩加载器本身（**Forge 侧另有 Curios**）」 |
| 公共 API | 不对称（既有设计，已在 README 表格登记）：`CuriosCompat` **只在 forge 侧存在**，方法签名暴露第三方类型（`ICuriosItemHandler` / `ICurioStacksHandler`）；neo 两线无同名 shim |

## 1.5 文档缺口

GitHub Release `1.0.0` 正文只说「把与你的 MC 版本对应的**这一个** jar 放进 `mods/`」，
**没有提 Forge 1.20.1 侧还需 Curios**（库 README §3 有写）。
⇒ 单看 Release 页面的整合包作者，在 1.20.1 上会漏装 Curios 并撞上「缺必需前置」的拒载。

## 1.6 可选改法（未执行）

| 方案 | 改动 | 代价 |
|---|---|---|
| A. 维持现状 | 无 | 库继续替消费方强制 Curios |
| B. 放宽为可选 | `forge` 侧 curios 块改 `mandatory=false`（保留 `ordering="AFTER"` + 区间） | 消费方若忘声明却调用 shim，失败形态从「缺必需前置」明确报错退化为运行期 `NoClassDefFoundError` |
| C. 彻底解耦 | B + `modImplementation` → `compileOnly` | 需重构建 + 重发 + 成对更新三个整合包 |

B / C 只改构建与元数据，**不涉及 public 类型/方法/字段/可见性/签名/语义**
⇒ 不属库 §6 兼容契约的破坏性变更，**可在 1.x 内做**；但都要 bump 版本 + `publishToMavenLocal`
+ 同步消费方 CI 的库 `ref:` 钉值 + 整合包成对更新。只补 Release 说明文字则无需重构建。

---

# 二、Mixin Booster（1.20.1 侧）

## 2.1 结论：库不依赖，也不需要

**库仓全仓搜 `mixin booster` / `mixinbooster` / `mixin_booster`：0 处命中**（含 README / CHANGELOG / 全部 gradle / 源码 / 元数据）。

## 2.2 五条独立判据（源码 → 构建 → 字节码 → 元数据）

| 判据 | 结果 |
|---|---|
| 全仓 `mixin` 字样 | 仅 9 处，**全是注释/文档**：neo 两线 `build.gradle` 的「本库不注册任何东西，**也没有 mixin / AT**」、CHANGELOG/README 里「下沉候选排除理由（依赖消费方专有类 / **mixin**）」 |
| `forge-1.20.1/build.gradle` 插件 | `java-library` / `maven-publish` / `net.neoforged.moddev.legacyforge 2.0.144` / `idea` —— **无** `org.spongepowered.mixin` 插件、**无** `mixin { }` 块、**无** `accessTransformer` |
| mixin 配置文件 | 全仓 **0 个** `*.mixins.json`、**0 个** refmap |
| jar 元数据（`forge-1.20.1-1.0.0.jar`，72 项） | 含 `mixin` 的条目 **0**；`MANIFEST.MF` 仅 `Manifest-Version: 1.0`（**无 `MixinConfigs` 属性**）；`pack.mcmeta` 只有资源包声明 |
| 字节码 | 引用 `org/spongepowered/**` 或 `MixinBooster` 的 class **0 个**（三平台均 0） |

## 2.3 边界：需要 Mixin Booster 的是**模组**，不是**库**

消费方 `astral_dice` 的 1.20.1 侧：

- `mods.toml`：`modId="mixinbooster" mandatory=true versionRange="[0.1.3,)" ordering="AFTER" side="BOTH"`
  （注释记录：**缺它时本模组不报任何错、全部 mixin 静默失效**，故选 `mandatory=true` 硬拒）
- 自己有 **16 个 mixin 类** + `astral_dice.mixins.json`
- 原因（`forge-1.20.1/build.gradle` 已固化的结论）：**Forge 1.20.1 的 FML 对 Mixin 零集成**，
  Sponge Mixin 0.8.5 完全由 Mixin Booster 这个纯 ModLauncher 服务 jar 提供；
  且 1.20.1 下 `mods.toml` 的 `[[mixins]] config=` 被完全忽略，Mixin 只认两条通道 ——
  ① 生产 jar 的 `META-INF/MANIFEST.MF` → `MixinConfigs`；② 开发环境 `--mixin.config` 启动参数。

⇒ 玩家侧的实际安装清单里 Mixin Booster 当然会出现（因为要装模组），但它是**模组的前置**，
与库无关：只要满足 forge / minecraft / curios，库 jar 可独立加载。

## 2.4 前瞻红线

**库若将来引入 mixin，必须自己声明 `mixinbooster`**（不能依赖消费方已声明而"搭便车"），
且要一并处理 `MixinConfigs` 清单属性通道（1.20.1 侧 `mods.toml [[mixins]]` 无效）与 refmap 注解处理器 ——
消费方为此踩过坑（`build.gradle` 里记着「Booster 只重映射字节码引用，不生成 refmap；
曾因少写注解处理器导致 `astral_dice.mixins.json:client.AstralUseItemGuardMixin from mod (unknown) -> FATAL`」）。

---

# 附：本轮未改动任何文件

两仓工作区均干净；本文件位于 `docs/`（已 gitignore，不入库）。结论已同步进
技能 `mc-prereq-lib-version-contract` §7 与项目记忆。
