# Astral Dice 多加载器工程 — Agent 接手指南

> **用途**：把本项目完整交接给「其他 agent / harness」。本文件自包含，可整份投喂，也可只取 §8 的提示词。
> **事实快照**：2026-10-06（GMT+8）｜消费方 `multi-main` @ `9825cf1e`｜库 `main` @ `4c65323`（lib 1.0.12）
> **权威优先级**：仓库 `AGENTS.md`（3325 行，本项目权威规范） > 本文件 > 其余 `docs/`。
> **凡与本文件冲突，以 `AGENTS.md` 与仓库实际内容为准。**
> ⚠️ 版本号 / 提交号 / 商店 id 都是**快照**，接手后请用 §9 的命令重新读取，不要直接沿用本文件的数字。

---

## 0. 一句话概览

《星之骰戏》（Astral Dice）是一套 **Minecraft Java 版「单仓四线」多加载器模组工程**：

- **四条自包含子项目线**共用同一棵工作树、同一套 CHANGELOG、同一套手册资源；
  **没有 `common` 共享模块** —— 每条线各自编译、各自维护 `src/`（有意如此，代价是跨线改动要改四遍）。
- 另有**一个独立源码仓的前置库** `starengine_lib`（四平台同号），以 **JarJar 内嵌**方式进入四线产物；
  库仓**不对外分发任何 jar**。
- 分发目标是 **GitHub Release + Modrinth + CurseForge** 三处；CI 负责构建/打 tag/发 Release/传 Modrinth，
  CurseForge 由本地脚本手动上传。

---

## 1. 关系拓扑

### 1.1 两个仓库、四棵工作树、四条分支线

| # | 磁盘路径 | 分支 | 角色 | 说明 |
|---|---|---|---|---|
| A1 | `F:\MCProject\astral_dice_multiloader` | `multi-main` | **消费方 · 发布线（封包）** | 四条线同树；CI 只在推这条线时打 tag / 发 Release |
| A2 | `F:\MCProject\astral_dice_multiloader-next` | `multi-dev-next` | **消费方 · 2.0.0 开发线** | **领先发布线 41 个提交**（快照时；`git branch -vv` 显示的 `ahead 172` 是相对它**自己的远端跟踪分支** `origin/multi-dev-next`，**不是**相对发布线）；开发中的特性只落在这里 |
| B1 | `F:\MCProject\starengine_lib` | `main` | **前置库 · 发布线** | lib 1.0.12；消费方四线的 `gradle.properties` 钉的就是这棵 |
| B2 | `F:\MCProject\starengine_lib_next` | `next` | **前置库 · 2.0.0 开发线** | `2.0.0-SNAPSHOT.12`（快照时） |

远端：

| 仓库 | 远端 URL | 远端分支 |
|---|---|---|
| 消费方 | `https://github.com/merlin-kitsune/astral_dice` | `multi-main`、`multi-dev-next` |
| 前置库 | `https://github.com/merlin-kitsune/starengine_lib` | `main`、`next` |

⚠️ **`multi-26.1.2-neoforge` 只在本地存在**（远端已删），但 CI 的 `on.push.branches` 仍列着它 ⇒ 那条触发条件**永不成立**（历史残留，不要当真）。
⚠️ 本地另有两棵 `backup-*` 分支（`backup-devnext-43e033e` / `backup-release-854a6c9`），是回滚用的一次性快照，**不要在其上开发**。

### 1.2 四条线（子项目矩阵）

| 目录 | MC | 加载器 | Java | 构建插件 | 快照版本 | 商店渠道 | 优先级 |
|---|---|---|---|---|---|---|---|
| `neoforge-1.21.1` | 1.21.1 | NeoForge `21.1.235` | 21 | ModDevGradle `2.0.141` | `1.3.7-hotfix+neoforge_1.21.1` | release | **P0 · 基准** |
| `forge-1.20.1` | 1.20.1 | Forge `47.4.10` | 17 | ModDevGradle **LegacyForge** `2.0.144` | `1.3.7-hotfix+forge_1.20.1` | release | **P0** |
| `neoforge-26.1.2` | 26.1.2 | NeoForge `26.1.2.109` | 25 | ModDevGradle `2.0.147` | `1.3.7-beta.2+neoforge_26.1.2` | beta | P1 · 向上移植 |
| `fabric-1.20.1` | 1.20.1 | Fabric Loader `0.19.5` + Fabric API `0.92.12` | 17 | Fabric Loom | `1.3.7-alpha.2+fabric_1.20.1` | alpha | P1 · 向下移植 |

- 包名 / group：`com.merlinkitsune.astral_dice`（四线一致）
- 产物名统一 `astral_dice-<完整版本>+<加载器>_<MC版本>.jar`
- **`26.1.2` 与 `fabric` 的版本号恒带预发布后缀**（`-beta.N` / `-alpha.N`）⇒ 永不占用发布线的裸版本号
- 根 `settings.gradle` 只 `include` 这四行；`gradlew build` 即四线全建

### 1.3 前置库 `starengine_lib`（消费方契约）

| 维度 | 内容 |
|---|---|
| 结构 | `common/`（四平台共用源码）+ `neoforge-1.21.1/`、`forge-1.20.1/`、`neoforge-26.1.2/`、`fabric-1.20.1/` 四个平台子项目 |
| 版本 | `lib_version` = **1.0.12**（四平台同号）；`mod_version` = `1.0.12+<加载器>_<MC版本>` |
| 消费方引脚 | 四线 `starengine_lib_version=1.0.12`；区间 `[1.0.12,2.0)`（fabric 走 Fabric 谓词 `>=1.0.12 <2.0`） |
| 分发方式 | **JarJar 内嵌**（`META-INF/jarjar/*` 或 fabric 的 `META-INF/jars/*`）⇒ 玩家**不要**单独安装 |
| 库仓产物 | **不发 jar**：库仓 Release 只有源码归档；产物另发到 Modrinth 的**库项目**（`2dIXA5wO`），仅为让 `embedded` 依赖可解析 |
| 兼容契约 | **主版本号不变则禁止破坏性更新**；bump 必须 `publishToMavenLocal` 并同步四线引脚 + CI 的库 `ref` |
| CI 取库方式 | 消费方 CI 用 **提交 SHA 钉值** checkout 库源码并自行 `publishToMavenLocal`（**不依赖库仓 CI 的产物**） |

⚠️ **推送顺序恒为「先库后消费方」**：消费方 CI 的库 `ref` 是 SHA 钉值，该 SHA 未推到远端时，`actions/checkout` 会直接 **422 `No commit found for SHA`**，整个 job 失败（不打 tag、不发 Release、不传 Modrinth）。

### 1.4 CI 流水线（消费方 `.github/workflows/build.yml`）

触发：`push` 到 `multi-main` / `multi-dev-next` / `multi-26.1.2-neoforge`（后者已无远端分支），或 `tags: ['[0-9]*']`，或 PR / 手动。

步骤链（**下列为逻辑分组** —— `.github/workflows/build.yml` 的 `steps:` 字面共 19 步，此处把三段 JDK、四段 lang 检查、两个 upload 各自合并，并略去 `Setup Gradle`）：

1. `checkout`（`fetch-depth: 0` + `fetch-tags`）
2. 三段 JDK：**21**（1.21.1）/ **17**（1.20.1）/ **25**（26.1.2）
3. `Checkout StarEngine Lib` —— 仓库 `merlin-kitsune/starengine_lib`，**`ref:` = 40 位 SHA**
4. `Publish StarEngine Lib to mavenLocal`
5. 四段 `check_lang_sync.ps1`（逐线）
6. `./gradlew build`（四线）
7. 上传 artifact（三线一组 + fabric 单独一组）
8. 读 `mod_version` → 派生 `base`（剥 `+后缀` 与 `-后缀`）→ 守门「base 必须是裸 `x.y.z`」
9. 打/复用 tag（**预发布后缀 `-alpha/-beta/-rc/-pre/snapshot` 才跳过；`-hotfix` 不跳过**）
10. 建/刷 Release（正文 = `release/<tag>/PLAYER_CHANGELOG_ZH.md` + `---` + `PLAYER_CHANGELOG.md`）
11. fabric pre-release（独立 tag `fabric-<完整版本>`，`--prerelease`，**body 是 CI 内建模板**，不读玩家日志）
12. `Publish to Modrinth`（主项目 + 库项目；缺 `MODRINTH_TOKEN` 时**只告警跳过**，job 仍 success）

库仓自己的 CI：打 tag + 建 Release（**不分发 jar**）。

### 1.5 分发渠道

| 渠道 | 标识 | 谁上传 | 更新日志来源 |
|---|---|---|---|
| GitHub Release（正式线） | tag = 裸版本号（如 `1.3.7`） | CI | `release/<tag>/PLAYER_CHANGELOG{_ZH}.md` |
| GitHub Release（fabric 预发布） | tag = `fabric-<完整版本>` | CI | CI 内建模板（前置 + 附件清单） |
| Modrinth | 主项目 `5xDtrJ8X` / 库项目 `2dIXA5wO` | CI（`tools/modrinth_upload.py`） | 主模组 `release/{base}`；fabric `release/fabric-{version}`；库 `release/starengine-lib/{base}` |
| CurseForge | projectId `1662159`（4 个 line key） | **本地脚本手动**（`tools/curseforge_upload.py`） | `--changelog`（**全局参数**，fabric 必须单独一次调用） |

- 商店渠道由**版本号后缀自动派生**：`-alpha` → alpha（fabric）、`-beta`/`-rc`/`-pre` → beta（26.1.2）、其余（含 `-hotfix`）→ release。
- ⚠️ Modrinth 的版本**标题**会剥掉 `+加载器_MC` 与预发布后缀 ⇒ 同一基础版本的四条线标题相同，靠 `version_number` 与标签区分。
- ⚠️ CF 的**项目说明字段不可经 API 修改** —— 全文维护在 `tools/curseforge-description.md`，改动后需人工粘贴到网页。

### 1.6 本地部署落点（`pushToGame` / `pushToDevRun` / `pushToRootBuild`）

| 线 | `pushToGame` 目标整合包 | `pushToDevRun` |
|---|---|---|
| `neoforge-1.21.1` | `D:\.minecraft\versions\狐の航空学 Voxy Edition\mods` | `run/1.21.1/mods` |
| `forge-1.20.1` | `D:\.minecraft\versions\1.20.1-Forge 模组测试\mods` | `run/1.20.1/mods` |
| `neoforge-26.1.2` | `D:\.minecraft\versions\26.1.2-NeoForge 模组测试\mods` | `run/26.1.2/mods` |
| `fabric-1.20.1` | `D:\.minecraft\versions\1.20.1-Fabric 模组测试\mods` | `run/fabric-1.20.1/mods` |

- 三处部署公共落点：仓库根 `build/libs/`
- `pushToGame` 受**白名单** `packPushBranches = ['multi-main']` 约束 ⇒ 其他分支**静默跳过**（不报错）
- ⚠️ `packModsDir` 是硬编码路径，**禁止按命名推测**，改动作业前先 `ls` 确认
- ⚠️ forge 线的 dev run **只能**用 `build/devlibs/*.jar`（Mojmap）；`build/libs` 已被 `reobfJar` 换成 SRG
- ⚠️ 编译后 `run/<版本>/mods` 下的旧产物**先删后拷**是任务内的固定动作

### 1.7 拓扑总图

```
                         ┌─────────────────────────────────────────┐
                         │  starengine_lib  (GitHub: merlin-kitsune)│
                         │  main @4c65323 = lib 1.0.12              │
                         │  common/ + 4 平台子项目                   │
                         │  不分发 jar（Release 仅源码归档）          │
                         └───────────────┬─────────────────────────┘
                                         │ JarJar 内嵌 + CI 按 SHA checkout 源码
                                         │ （消费方 CI 自行 publishToMavenLocal）
                                         ▼
   ┌──────────────────────────────────────────────────────────────────────────┐
   │  astral_dice_multiloader  (GitHub: merlin-kitsune/astral_dice)            │
   │                                                                          │
   │  worktree A2  multi-dev-next @3446ceaf ── 2.0.0 开发线（领先发布线 41）    │
   │  worktree A1  multi-main     @9825cf1e ── 发布线（CI 只认它打 tag/发版）   │
   │                                                                          │
   │  ┌────────────────┬────────────────┬────────────────┬─────────────────┐  │
   │  │ neoforge-1.21.1│ forge-1.20.1   │ neoforge-26.1.2│ fabric-1.20.1    │  │
   │  │ P0 基准        │ P0             │ P1 向上        │ P1 向下          │  │
   │  │ NG 21.1.235/J21│ Forge47.4.10/J17│ NG 26.1.2.109/J25│ Loom/FAPI 0.92│  │
   │  └────────────────┴────────────────┴────────────────┴─────────────────┘  │
   └──────────────────────────────────────────────────────────────────────────┘
                │ CI: build → tag → Release → Modrinth
                │ 本地: pushToRootBuild / pushToDevRun / pushToGame（白名单 multi-main）
                ▼
   ┌──────────────┬──────────────┬──────────────────────┬────────────────────┐
   │ GitHub       │ Modrinth     │ CurseForge           │ 本地整合包          │
   │ Release      │ 主 5xDtrJ8X  │ projectId 1662159    │ 4 个 versions 目录  │
   │ (3+1 jar)    │ 库 2dIXA5wO  │ (手动脚本上传)        │ (见 §1.6)          │
   └──────────────┴──────────────┴──────────────────────┴────────────────────┘
```

---

## 2. 版本优先级与跨线纪律（2026-10-04 用户裁决，**现行唯一权威**）

| 级别 | 线 | 要求 |
|---|---|---|
| **P0** | `neoforge-1.21.1`（基准） + `forge-1.20.1` | **功能一致、同批、优先**；两条线之间不许有「功能缺口」，只许有「三类合理差异」 |
| **P1** | `neoforge-26.1.2`（向上） + `fabric-1.20.1`（向下） | **待 P0 完成后移植**；可滞后，但每项滞后必须逐条登记 |

⛔ **旧口径「三条生产线同批实施（含 26.1.2）」已作废** —— 不要再按它编排任务。

**三类合理差异（唯一允许代码不一致的理由，必须逐条登记）**：

| # | 类别 | 例 |
|---|---|---|
| ① | **MC 版本存废的原版 API / 物品 / 效果** | 1.20.1 无 `MaceItem` ⇒ 重锤分支移除；1.20.1 无六个 1.21 新增 `MobEffects` ⇒ 复仇之戟加成条件移除 |
| ② | **加载器的机制** | Forge 用 `Capability`、Fabric 用附件；`@SubscribeEvent` vs `LoadBus` 回调；`forge:` vs `c:` 标签 |
| ③ | **某线装 / 未装某模组，或其注册名不同** | 1.20.1 神秘遗物注册名 = `enigmaticlegacy:`（1.21 侧是 `enigmaticlegacyplus:`）；26.1.2 与 fabric **整体移除** Iron's Spells 与神秘遗物联动 |

> 判据原文（`porting/CROSS-VERSION-DIFFS.md` L13/L15）：
> 「⚠️ **禁止**把「对端独有行」直接当滞后 —— 实测其中很大比例是**有意的平台适配**。」
> 「⚠️ **禁用**「方法名集合差」（漏数值/实现变化）与「跨版本 diff 行数」（26.1.2 实测 1097 行差异中仅 1 项是真缺口）。
> **用语义差判据**（对端独有实质语句行本线不存在）。复算脚本：`porting/fabric-1.20.1/gap-report/{semantic_gap.py,forge_only_lines.py}`。」

---

## 3. 差异项总表

> 逐项的**完整清单与证据落点**在 `porting/CROSS-VERSION-DIFFS.md`（跨线）与 `porting/fabric-1.20.1/FABRIC-DIFFS.md`（fabric 专项）；
> 本节只给「分组骨架 + 最容易踩的条目」。每条括号内是该差异的**类别**（①/②/③，见 §2）。

### 3.1 `forge-1.20.1`（P0，相对 1.21.1 基准）

| 组 | 条目 | 类别 |
|---|---|---|
| 构建 | Gradle 插件换 `legacyforge`；Java 21→17；dev/prod **两套产物**（`devlibs` Mojmap / `libs` 经 `reobfJar` 换 SRG） | ② |
| 元数据 | `mods.toml` 用 `mandatory=true/false`；`type="required"` 无效；缺 `mandatory` 会被 FML 判为 broken jar | ② |
| 事件 | 33 个处理器重映射；`@SubscribeEvent` 成员**不能 private**；`LivingDamageEvent.Pre` → `LivingHurtEvent` | ② |
| 注册 | `DeferredRegister` → `create(ForgeRegistries.X)` + `RegistryObject`；Curios 槽位改 FML IMC 注册 | ② |
| 数据包 | 目录**复数**（`recipes/` `advancements/` `loot_tables/` `structures/`） | ① |
| 标签 | 通用标签必须用 **`forge:`**（自建 `data/forge/tags/items/bricks.json`） | ② |
| 持久化 | Forge `Capability` / `Entity#getPersistentData()`（Forge 补丁方法） | ② |
| 联动 | 神秘遗物注册名 = `enigmaticlegacy:*` | ③ |
| 版本差 | 无 `MaceItem`；无六个 1.21 效果；`NBTIngredient` → `StrictNBTIngredient` | ① |

### 3.2 `neoforge-26.1.2`（P1 向上）

| 组 | 条目 | 类别 |
|---|---|---|
| 重命名 | 类/包重命名与包结构拆分（`ResourceLocation` ↔ `Identifier`）；提取-渲染状态架构 | ① |
| 注册 | 物品必须 `registerItem(name, props -> …)`，**否则注册期 NPE**；物品模型定义 `assets/<ns>/items/<id>.json` 是硬需求 | ① |
| datagen | **两段式** + 双输出根 + HashCache 互删；`GatherDataEvent` 变抽象类 | ① |
| 数据包 | ingredient 必须字符串；`global_loot_modifiers.json` 索引废除 | ① |
| Curios | 15 的饰品栏尺寸**只能**经「槽位修饰符 → `update()`」改写 | ① |
| Mixin | 目标漂移（字符串选择器编译期查不出）；两个 mixin 因上游已修而删除 | ① |
| 渲染 | `GuiGraphicsExtractor#blit/text` 的**参数语义变了**（编译期看不出来）；**color 必须是 ARGB**（`0xFF......`，传 `0xFFFFFF` 一个字都不画且不报错） | ① |
| 联动 | Iron's Spells 与神秘遗物+ **整体移除**；保留 patchouli / waystones / curios / bountiful / JEI | ③ |
| 平台差异 | ActionBar 的 y 公式：NeoForge 两线走**动态** `max(leftHeight,rightHeight)`，1.20.1 两线保持固定 | ② |

### 3.3 `fabric-1.20.1`（P1 向下，代码蓝本 = `forge-1.20.1`）

| 组 | 条目 | 类别 |
|---|---|---|
| 加载器层 | Curios→Trinkets(+Accessories 软依赖)；Capability→Fabric API 附件（109 键）；Forge EventBus→自建 `LoaderBus`；GLM→`LootTableEvents.MODIFY`；注册表前缀 `platform.registry.*` | ② |
| 事件 | **禁** `@Mod.EventBusSubscriber`；`LoaderBus.scan` **不递归内部类**；`SubscriptionAudit` 兜底 | ② |
| 映射 | dev(named) / prod(intermediary) **两套映射**；**禁按字符串名反射原版成员**（dev 命中、prod 必崩） | ② |
| Mixin | **禁 `@ModifyConstant`**（与第三方同点注入互斥）⇒ 一律 MixinExtras `@WrapOperation` | ② |
| 附件 | 附件键注册**必须早于**玩家数据反序列化（漏 ⇒ 一次登录静默丢 33 键） | ② |
| 前置 | `depends` 是 **AND 语义** ⇒ 饰品「Trinkets 或 Accessories 二选一」只能走 `recommends` + 运行时守卫，且守卫必须放在**调用点** | ② |
| 资源 | Accessories 槽位图标必须落在**原版 `minecraft:blocks` 图集**目录 | ② |
| 标签 | 通用标签自建 **`c:`**（对齐 NeoForge 定义） | ② |
| datagen/JiJ | Loom 重映射会**剥掉** `fabric.mod.json` 的 `jars` ⇒ `tools/loom_embedded_jars.py` 兜底 | ② |
| 保留的 1.20.1 差异 | 星币锤配方、复仇之戟、`potionTag(...)`、`NbtShapedRecipe`、**通用标签 `c:`**、饰品框架 —— **有意保留，勿统一** | ① |
| 联动裁剪 | 移除 Iron's Spells / `enigmaticlegacy` / bountiful 数据联动；保留 patchouli / waystones / trinkets / accessories / Modern UI | ③ |
| 存档 | **跨加载器存档不互通**（附件/Capability 与饰品框架）—— 平台差异，**不修补** | ② |

### 3.4 判定口径（**最容易做错的地方**）

1. **先判「平台差异 vs 功能缺口」**，再决定要不要移植 —— 反向流程会白做。
2. **禁用「方法名集合差」和「跨版本 diff 行数」**做滞后测绘（实测 1097 行差异里只有 1 项是真缺口）。
3. **「整文件照搬」只适用于平台中性文件**；平台敏感类**逐段局部改**（实测对 35 文件整文件照搬 ⇒ 61 编译错 / 26 文件失败）。
4. fabric 与 forge **同为 MC 1.20.1** ⇒ 凡 forge 已实现的功能，**照搬 forge 文件 + 固定平台替换**，天然保留 1.20.1 的配方/行为差异；只有 forge 也没有的功能才从 1.21.1 降级移植。
5. **删块后必跑花括号自检**；平台差异必须**逐条登记**（到 `porting/` 对应文件的差异表），不得只在代码注释里写。

---

## 4. 未决清单

> 权威源：`KNOWN-ISSUES.md`（**§1.1 是「已处理索引」**，只留 id + 结论；正文只留未修/未决项；
> 「已修项移出正文、未修项禁删」是 §0 的硬纪律）。
> ⚠️ 下表标注「26.1.2 §8-x / §6.4」的两行**不在** `KNOWN-ISSUES.md`，出自 **`docs/compat-26.1.2-neoforge.md`**。

### 4.1 计数（快照：2026-10-04 之后）

> 「条目总数：**24** 条未修/未决 = A 组 7（KI-1…KI-7）+ B 组 1（KI-8）+ C 组 3（KI-9…KI-11）+ M 组 3（KI-M2/M3/M4）
> + D 组 1（KI-D1）+ E 组 2（KI-E1/E2）+ F 组 **7**（KI-F4/F7/F21/F22/F23/F24/F25）+ G 组 0，
> 另有 §5 的 **2** 条「测试资产待修项」。」

⚠️ 其中 `KI-F21` / `KI-F22` / `KI-F23` **标题自述「已完成/已修/已缓解」**，不计入「未修缺陷」。

### 4.2 未闭环 / 待裁决（按影响面排序）

| id | 一句话 | 面 |
|---|---|---|
| **KI-F25②(b)** | 裸 `removeEffect` 的偶发 `CancellationException` **来源未定位** ⇒ 登记为**未定位观察项**，**不得**写成「非产品缺陷」 | fabric，必须如实标注 |
| ~~**KI-F8**~~ | ✅ **已闭环（2026-10-06）**：四线三语文案已对齐代码稀有度（`bizarre()`），守门 `tools/verify_rarity_consistency.py` C1 PASS ⇒ 已入 `KNOWN-ISSUES.md` §1.1 | — |
| **KI-9** | R1/R2「两处 1.20.1 功能缺失」待按 R 编号重新取证 | forge；原文档未展开 |
| **KI-2** | ×1.4 与七咒 `dice_curse_ratio` 交叉 ⇒ 1.20.1 双重放大 | forge 数值 |
| §5-追加 D | `SPELL-TRUE-DAMAGE`：1.21.1 侧护甲读取失效（`rc=undefined`）+ 断言仅锚标签前缀 ⇒ 空证据判 PASS。⚠️ **该用例文件已随 2026-09-20 资产清零删除**（`0f6c6fe3`），缺陷成立但**当前无机器判据** | 测试资产（历史） |
| §5-追加 C | `ELECTRIC-GLOVE-AOE-BASE`：探针 `base` 读取路径 `calls=0` ⇒ 该用例当时不可用（连带 KI-5 游戏内验证未完成）。⚠️ **用例文件已随 2026-09-20 资产清零删除**；2026-10-06 复核：其断言本身**不弱**（正则反向引用锁死 `base=self=nbr`），且探针取数表达式与生产 `SpellDamageRegistry` 一致 ⇒ 「断言过弱」只对 `SPELL` 成立 | 测试资产（历史） |
| 26.1.2 §8-1/2/3/5 | 1.21.1 的 4088 行回归探针未迁移 / `opprobe` 的 `level` 退化为 `-1` / 手册与 GUI 未做视觉-行为取证 / 二重验证第二轮未跑 | 26.1.2 |
| 26.1.2 §6.4 待查 | `TargetSelectionClient` 的 `COLOR_FRIENDLY/HOSTILE/NEUTRAL` 无 alpha ⇒ 文字不可见（**同类待查，尚未修**） | 26.1.2 |
| fabric §4.3 未覆盖 | 「装上真医疗箱之后的重登回血数值」未做（人工实机项）；Q3 的 `CancellationException` 未定位 | fabric |
| forge §九 5 项 + §六核对 | 全部标「待实机 / 待游戏内确认」（Curios 槽位、骰战结算、附件同步、卡牌栏菜单、神秘遗物注册名） | forge，用户手动 |
| **KI-F4** | 缺陷**模式**已固化：重写构建脚本漏搬 `sourceSets.srcDir` ⇒ 生成资源不进产物 | 纪律项 |
| **KI-F2 / KI-F3** | 跨加载器存档不互通 / 双饰品通道槽位聚合语义 | 平台差异，**不修补** |

### 4.3 数据库化的「不要重复登记」

- `KI-E3`（三守门不含 fabric）**已闭环**；`KI-G2`（医疗箱刷血）**已闭环**；`KI-E4`（军火守门判据滞后）**已修** ⇒ 都在 §1.1。
- 新增条目**必须**在 §11「变更记录」补一行日期行（§0 第 4 条要求），否则算未闭环。

---

## 5. 关键词 / 术语表

> 本项目的行话密度很高，且**同义词被严格区分**（用错词会被当成理解错）。接手前先过一遍。

### 5.1 线与优先级

| 术语 | 含义 |
|---|---|
| **四线 / 子项目线** | `neoforge-1.21.1`、`forge-1.20.1`、`neoforge-26.1.2`、`fabric-1.20.1`；**四条自包含线，无 `common`** |
| **生产线 / 发布线 / 迁移线 / 移植线** | 旧分类词；现行等价说法：**P0 两条 = 生产线/发布线**，26.1.2 = **迁移线**，fabric = **移植线** |
| **P0 / P1** | P0 = `neoforge-1.21.1`(基准) + `forge-1.20.1`（功能一致、同批、优先）；P1 = `neoforge-26.1.2`(向上) + `fabric-1.20.1`(向下)（待 P0 后移植） |
| **基准（baseline）** | 指 `neoforge-1.21.1`；跨线差异一律「相对基准」描述 |
| **追平（catch-up）** | 把 P0 已落地的裁决/修复**集中移植**到滞后线（fabric 的「追平批 1~9」即此） |
| **封包** | `multi-main` 处于「可发布快照」状态；`scripts/test/**` 等必须保持封包版本 |
| **半移植** | 判据 = 方法名集合差 + `@SubscribeEvent` 计数差；特指「只搬了一半」的状态 |

### 5.2 差异与判据

| 术语 | 含义 |
|---|---|
| **三类合理差异** | ① MC 版本存废的原版 API/物品/效果 ② 加载器机制 ③ 联动模组存在性/注册名；**唯一允许代码不一致的理由** |
| **平台差异 ≠ 功能缺口** | 平台差异**不修补**（且要登记）；功能缺口**必须移植**。**先判类别再动手** |
| **语义差判据** | 「对端独有**实质语句**行本线不存在」；**禁用**方法名集合差与 diff 行数 |
| **永真门** | 判据对任何输入都通过（等于没门）；写完判据必须构造反例实测 |
| **判据滞后** | 判据**曾经有效**、因设计裁决变更而失效（`KI-E4` 即此类：改裁决时没同步改守门脚本） |
| **平台期望矩阵（`PAN_EXPECT`）** | 平台差异型断言的正确写法：写明「哪个键应在哪些线上存在」，**缺期望项 = FAIL，非期望项 = note** |
| **负控 / 反证** | 改完判据后注入反例证明它会红（删期望项 ⇒ FAIL、加非期望项 ⇒ note）；**不做负控等于没改** |
| **静默失效** | 不报错但功能没生效（**本项目最高频的缺陷类别**）：标签命名空间写错、`enabled:false`、shader 静默退化、`packPushBranches` 白名单跳过 |
| **松垮引用（loose ref）** | git 松散对象引用；`tools/`（旧 `scripts/maintenance/repair-loose-refs.ps1`）可修 |

### 5.3 游戏内容域名词（出现在类名 / lang key / 手册条目里）

| 术语 | 英文 / key 片段 | 说明 |
|---|---|---|
| **骰子** | dice | 核心物件；有星级（0★~3★）与稀有度 |
| **立牌** | sign（`*_sign`） | 24 个角色立牌，各有被动 + 主动技能 |
| **筹码** | chip（`BaseChipItem`，61 枚） | 饰品类，分基础/流派两档配方 |
| **卡牌 / 战斗牌** | card / battle card | 消耗品，有费用点数（cost） |
| **效果牌** | effect card（`BaseEffectCardItem`） | 可对他人使用的功能牌，走目标选择器 |
| **骰神赐福** | dice blessing / `isBlessingTarget` | 战斗核心事件 |
| **骰战** | dice combat（`DiceCombatEvents`） | 近战判定、闪避与防御、扩散伤害 |
| **流派** | healing / starlight / charge / counter | 治愈点 / 星光 / 充能 / 反击 四套资源体系 |
| **星币** | star coin（`StarCoinWallet`） | 货币；账本在**前置库**，落玩家持久化 NBT |
| **稀有度** | `AstralRarities`：rare / epic / legendary / pinnacle / bizarre | 5 档自有稀有度 + 原版 `COMMON`；**不进池 = 巅峰 + 奇特** |
| **两口「饕餮之锅」** | `enigmaticlegacy:eldritch_pan` / `enigmaticdelicacy:voracious_pan` | 军火/近战显式纳入清单的特例（后者本身是 `BlockItem`） |

### 5.4 工具链术语

| 术语 | 含义 |
|---|---|
| **守门 / gate** | 可重复的机器判据脚本；退出码 0 = 通过 |
| **守门散三处** | `scripts/verify/*.ps1` + `scripts/audit/tooltip_color_audit.ps1` + `tools/` 下若干脚本 ⇒ **只跑一处必漏** |
| **探针 / probe** | 注入游戏内的 KubeJS 脚本（`/astralprobe`、`/astralcatchup`、`/astralparty`），产出机器行供断言 |
| **机器行** | 探针/日志输出中**结构稳定、可 grep** 的行（如 `AP_*`、`APDUMP`）；断言只锚机器行 |
| **用例 / case** | 声明式 JSON（`scripts/test/cases/*.json`），由测试台执行 |
| **测试台** | 生产三线 `scripts/test/mt.ps1`；fabric 线**自包含** `scripts/test/fabric/ft.ps1`（**不得复用 mt.ps1**） |
| **双重验证** | ① 机器判据（守门）② 独立子代理反证；**两者不可互相替代** |
| **开包核对** | 直接 `unzip` 产物 jar 校验资源/元数据/字节码，是「已落地」的最终证据 |

---

## 6. 注意事项

### 6.1 硬规则（违反会被判为不合格交付）

| # | 规则 | 判据 / 命令 |
|---|---|---|
| 1 | **改动只落在所属工作树**，禁跨树镜像 / 回填 | `git -C <工作树绝对路径> status --short --untracked-files=all` 除 `?? .agent-teams/**` 必须无输出 |
| 2 | **跨工作树写文件一律绝对路径**；`git` 命令一律带 `-C` | 相对路径会按进程 cwd 落到**主线**（实测事故） |
| 3 | **一律 pwsh 7**，禁 Windows PowerShell 5.1 | 多行 / 含 `if` 表达式 / 三元的命令须落盘 `.ps1` 再 `pwsh -NoProfile -File <绝对路径>` |
| 4 | **远程 git 操作必须经代理** `http://127.0.0.1:7897` | `git config http.proxy` 已配；不通先查代理进程 |
| 5 | **默认不 `git push`** | 只有用户明确要求才推；推送顺序见 §1.3 |
| 6 | **禁 `git add -A` / `git checkout .`** | 用具体路径 `git add <paths>`；恢复用 `git restore -- <路径>` |
| 7 | **禁批量删除**（`rm -r` / `git rm -r`） | 守卫会 SIGTERM；正确做法 `shutil.move` 到 `temp/` + `git add -u <路径>` |
| 8 | **非平凡改动收尾必须派独立子代理二次验证** | brief 只给「期望的不变量 + 可执行的判据命令」，**不得**把主会话结论当事实喂给它 |
| 9 | **平衡性问题 = FATAL** | 影响平衡一律当批修复，不得登记搁置 / try-catch 绕过 |
| 10 | **构建与冒烟一律主 agent 亲自执行** | 禁放子代理（代码修改类**才**需要 subagent） |
| 11 | **`docs/` 已入库** | 早期它被 `.gitignore` 排除，现已移除该条目；新增文档直接提交 |

### 6.2 高发坑速查（每条都实测踩过）

| 现象 | 根因 | 正确做法 |
|---|---|---|
| 「改完了但功能没生效，也不报错」 | **静默失效**：标签命名空间写错（`c:` vs `forge:`）、白名单分支跳过、字段名不匹配 | 先查「是否真的进入了该分支」；开包核对 + 探针读数 |
| 守门**恒红**或**恒绿** | 判据滞后 / 永真门 | 用负控反例实测；平台差异改**期望矩阵** |
| PowerShell 守门日志**0 字节**却 rc=0 | 脚本用 `[Console]::Out` 直写**进程 stdout**，`*>` 抓不到 | 以**子进程**跑：`pwsh -NoProfile -File <脚本> *> log` |
| 长命令**永不返回** | `*>>` 捕获了启动阶段，游戏进程继承管道 | 启动放后台任务；注入/断言走独立前台调用 |
| `git diff --numstat` 显示**整文件重写** | 补丁脚本对已是 CRLF 的文件又做了一次 `\n → \r\n` ⇒ `\r\r\n` | 先归一到 `\n` 再按目标行尾重建；写盘后断言 `count(b'\r\r\n')==0` |
| Python 读 CRLF 文件做锚点替换**恒不命中** | `open()` 通用换行把 `\r\n` 转成 `\n` | `open(p, encoding='utf-8', newline='')` |
| `open(path,'wb').write(text.encode())` 把文件**截断成 0 字节** | encode 抛异常时文件已打开 | **先 encode 到变量，再 open+write** |
| 修改后 Release 出现**新旧两套附件并存** | `--clobber` 只覆盖**同名**文件，而 hotfix 让文件名多出 `-hotfix` | 见 §6.3 |
| GitHub API 查 `commits/{sha}` 返回 **422** 但 CI 明明跑起来了 | 带缓存的抓取工具（15 分钟）返回旧结果 | 判断远端可达性用 **`git ls-remote`**（权威、无缓存） |
| 第三方商店页面看不到刚上传的文件 | `api.cfwidget.com` 是**第三方快照**，有缓存延迟 | 权威信号 = 上传接口返回 **HTTP 200 + `{"id": <fileId>}`** |
| fabric dev 里含内嵌库的 mod **全部起不来** | Loom 重映射剥掉 `fabric.mod.json` 的 `jars` 声明（KI-F23） | 先跑 `pwsh -NoProfile -File scripts/test/fabric/ft_env.ps1 --side both --install-embedded` |
| `enabled: false` 被当成「连接器坏了」 | 无项目 / 模式不对导致工具不可用 | 先查 `get_capabilities` 的项目与模式 |
| 探针「一行都不输出」 | KubeJS `server_scripts` **共享全局作用域**，与既有探针同名 helper 互覆 | 探针必须**整体包 IIFE** |
| `ResourceKey` 参与 `==` 抛 NPE | Rhino `SpecialEquality` | 探针禁比较 `ResourceKey`；`getLootTable()` 走 try/catch |
| 26.1.2 上文字/矩形**不显示**且不报错 | `blit/text` 同名但参数语义变了；color 非 ARGB | 传 `0xFF......`；跨版本**禁抄投影公式** |

### 6.3 hotfix / 预发布轮的「旧工件并存」（发布前必查）

`--clobber` 只覆盖同名文件；hotfix 让文件名多出 `-hotfix`，预发布线还会同时升号（`-beta.1 → -beta.2`）⇒ 旧工件**并存**：

| 处 | 粒度 | 清理手段 |
|---|---|---|
| GitHub Release | 附件 | `gh release delete-asset`（**需 token**） |
| Modrinth | version | `PATCH /version/{id}` 置 `status=archived`（**可逆**）或 `DELETE` |
| CurseForge | file | **只能网页删**（upload token 无删除端点） |

⚠️ **本项目惯例不统一**（1.3.5 在 CF/Modrinth 清过、GitHub 保留；1.2.1 三处都保留）⇒ **默认不要擅自删除**，把清单交给用户裁决。

---

## 7. 守门与测试体系

### 7.1 守门脚本清单（22 项；退出码 **0 = 通过**）

> **口径**：本节只列**闸门**（机器判据）。`tools/` 下另有 12 个**一次性/分析类**工具（`analyze_*.py` ×4、`compare_sources.py`、
> `plan_migration.py`、`preflight_lib_migration.py`、`classify_platform_deps.py`、`fabric_datagen_convert.py`、
> `gen_ren_shield_texture.py`、`migrate_to_starengine_lib.py`、`recompute_island_strict.py`）**不是闸门**，勿接流水线；
> 其中 `run_mixin_stack_sanitizer_test.ps1`（编译真实源 + 退出码判据）最接近闸门，可按需纳入。

**`scripts/verify/`（PowerShell）**

| 脚本 | 职责 | 备注 |
|---|---|---|
| `verify_bountiful_pools.ps1` | 赏金池集合 ↔ 四份池文件 | **必须显式 `-Root <绝对路径>`**，否则假绿 |
| `verify_bountiful_instance_exclusions.ps1` | 外部模组物品清除覆盖 | |
| `verify_chip_recipes.ps1` | 筹码配方三档校验（源码/生成/jar）× 双版本 | 判据 `61|一致61|不一致0` |
| `verify_chip_acquisition.ps1` | 筹码「配方→获取途径」闭包 + 槽位/创造栏对等 | |
| `verify_crafting_recipe_uniqueness.ps1` | 3×3 配方网格唯一性 | **必须在 `runData` 之后跑** |
| `verify_forge_loader_gate.ps1` | 1.20.1 加载器版本门槛 + Mixin Booster/Connector 硬前置 | 独立于测试台用例链 |
| `verify_probe_class_refs.ps1` | 探针 `Java.loadClass(...)` 目标是否真在 classpath | 需已构建产物 |
| `verify_resource_integrity.ps1` | 注册表→模型→物品模型定义 全链闭合（防紫黑格） | 需已构建产物 |
| `ChipCommon.psm1` | **模块**（配方权威数据表），非独立入口 | |

**`scripts/audit/`**

| 脚本 | 职责 |
|---|---|
| `tooltip_color_audit.ps1` | Tooltip 染色规则审计（规则见 `docs/tooltip-color-rules.md`） |

**`tools/`**

| 脚本 | 职责 | 备注 |
|---|---|---|
| `check_lang_sync.ps1` | zh 基线 ↔ en ↔ ja 键集一致 + 裸 `%` 判 FAIL | 用 `[Console]::Out` 直写 stdout；**WARN 也要逐条看** |
| `check_mod_sources.ps1` | 模组依赖来源统一口径 | |
| `audit_actionbar.py` | 动作栏三约束（值内禁 `§` / 键齐 / 通道白名单） | |
| `audit_mixin_injection.py` | Mixin 注入参数离线门禁 | |
| `audit_patchouli_keys.py` | 手册悬空翻译键 | |
| `verify_aoe_spread_invariants.py` | 扩散伤害不递归（A1–A5，四线） | |
| `verify_effect_icons.py` | 每个 `MOB_EFFECT` 都有同名图标 PNG | |
| `verify_fabric_assets.py` | fabric 线资源/注册 8 项闭环 | 需产物 jar |
| `verify_firearm_detection.py` | 枪械判定常量 ↔ 证据表 + `PAN_EXPECT` + 实物 jar 回查 | 默认强制实物 |
| `verify_medkit_and_removal_invariants.py` | Q2/Q3 静态不变量（M1 四线 + M2 fabric） | |
| `verify_party_api.py` | `PartyRelations` 反射契约 ↔ 真实 jar | `--strict` 可把 SKIP 判失败 |
| `verify_rarity_consistency.py` | C1 手册档位词↔代码稀有度 / C2 库反射契约 / C3 手册页↔lang | |
| `verify_selector_key_ownership.py` | 主动技能键归属（防回退到「按键=取消」） | |

> ⚠️ 本仓**没有**官方「一键跑全部守门」的 runner（已确认）。需要时自建 wrapper 逐条跑并落 `rc` —— **注意 PowerShell 脚本必须以子进程方式跑**（§6.2）。
> ⚠️ `scripts/devtools/verify_content_library.ps1` **不是闸门**（1.2.0 冻结期一次性工具），勿接流水线。

### 7.2 测试台

**生产三线：`scripts/test/mt.ps1`**

```bash
pwsh -NoProfile -File scripts/test/mt.ps1 --version 1.21.1 --case scripts/test/cases/<X>.json
pwsh -NoProfile -File scripts/test/mt.ps1 --version <版本> --phase stop --purge-saves   # 收尾
```

- `--phase` ∈ `build|env|launch|stop|report|cases`；**`--case` 必须前台跑**
- ⚠️ **批量编排闸门**：无 `--phase` 全流程 / `--phase cases` 无 `--case` / `--phase report` 会被拦（退出码 2，stderr 首行 `MT_AUTO_DISABLED:`）；放行开关 `--allow-auto` 或 `MT_ALLOW_AUTO=1`
- ⚠️ **实机注入需前台键盘焦点** ⇒ 无人值守跑不通
- ⚠️ FAIL 收尾会跳过清档 ⇒ 重跑前先 `--phase stop --purge-saves`

**fabric 线：`scripts/test/fabric/ft.ps1`**（**自包含，不得复用 `mt.ps1`**）

- 入口 `ft.ps1`；子脚本 `ft_build / ft_env / ft_launch / ft_stop / ft_inject / ft_assert / ft_case / ft_prod / ft_dispatchreport`
- 注入**唯一通道** `--channel rcon`；运行目录在 `fabric-1.20.1/run/{client,server}`
- fabric dev 前置（必须）：`ft_env.ps1 --side both --install-embedded`（KI-F23）
- `ft_prod.ps1` = **生产映射冒烟**（intermediary 环境）；改 mixin 必须 `-PreloadClasses <intermediary 名>`

---

## 8. 可直接复用的接手提示词

> 下面四段可**整段复制**给其他 agent / harness。P1 是总纲（自包含）；P2–P4 是场景变体。
> 若目标 agent 能读文件，优先让它读 `AGENTS.md`（权威）+ 本文件；若不能，则 P1 已包含最小必要信息。

### P1 · 通用接手（总纲）

```text
你接手一个 Minecraft Java 版「单仓四线」多加载器模组工程：《星之骰戏》Astral Dice。

【工程坐标】
- 消费方仓：F:\MCProject\astral_dice_multiloader（GitHub: merlin-kitsune/astral_dice）
  · 发布线工作树：multi-main（位于上述路径，处于「封包」状态，CI 只认它打 tag/发版）
  · 开发线工作树：F:\MCProject\astral_dice_multiloader-next（分支 multi-dev-next，领先发布线）
- 前置库仓：F:\MCProject\starengine_lib（GitHub: merlin-kitsune/starengine_lib，分支 main；另有 next 开发线）
  · 库以 JarJar 内嵌进四线产物，不分发独立 jar；消费方四线引脚 1.0.12，区间 [1.0.12,2.0)
- 四条线（同树，无 common 共享模块）：neoforge-1.21.1（P0 基准）/ forge-1.20.1（P0）/
  neoforge-26.1.2（P1 向上，版本恒带 -beta.N）/ fabric-1.20.1（P1 向下，版本恒带 -alpha.N）
- 包名：com.merlinkitsune.astral_dice；产物：astral_dice-<完整版本>+<加载器>_<MC版本>.jar

【权威信息源（按优先级）】
1. 仓库 AGENTS.md（3325 行，本项目权威规范，含 60+ 条「必须遵守」）
2. docs/AGENT-HANDOFF.md（本接手指南：拓扑 / 差异 / 未决 / 注意事项）
3. KNOWN-ISSUES.md（未决清单；§1.1 是已处理索引，正文只留未修项）
4. porting/CROSS-VERSION-DIFFS.md（跨线差异）、porting/fabric-1.20.1/FABRIC-DIFFS.md（fabric 专项）
5. docs/compat-26.1.2-neoforge.md、docs/compat-1.20.1-forge.md
开工前**必须**先读 1 与 2；凡与你的先验知识冲突，以前者为准。

【优先级与跨线纪律（现行唯一权威）】
- P0 = neoforge-1.21.1 + forge-1.20.1：功能一致、同批、优先。
- P1 = neoforge-26.1.2 + fabric-1.20.1：待 P0 完成后移植；滞后必须逐条登记。
- 允许的代码不一致只有「三类合理差异」：① MC 版本存废的原版 API/物品/效果 ② 加载器机制
  ③ 联动模组存在性/注册名。**先判「平台差异 vs 功能缺口」再动手**；
  禁用「方法名集合差」与「跨版本 diff 行数」做滞后测绘（实测 1097 行差异里只有 1 项是真缺口）。

【硬规则（违反即不合格）】
1. 改动只落在所属工作树；跨工作树写文件一律用绝对路径；git 命令一律带 -C <绝对路径>。
2. 一律 PowerShell 7：多行/含 if 表达式/三元的命令落盘 .ps1 后用 pwsh -NoProfile -File <绝对路径> 跑。
3. 远程 git 操作必须经代理 http://127.0.0.1:7897。**默认不 git push**（仅用户明确要求才推）。
4. 禁 git add -A / git checkout .；禁批量删除（用 shutil.move 到 temp/ + git add -u）。
5. 非平凡改动（代码/资源/批量/跨线/发布/结论型交付）收尾**必须**派独立子代理二次验证：
   只给「期望的不变量 + 可执行的判据命令」，不得把主会话结论当事实喂给它；逐条回应它指出的不一致。
6. 影响平衡性的问题按 FATAL 处理，当批修复，不得登记搁置。
7. 「守门全绿 ≠ 已验收」：机器判据与独立验证**两层都要**，不可互替。

【验收口径】
- 构建：「构建日志 + 产物时间戳 + 开包核对」三重证据；开包核对要核到 jar 内的元数据/资源/字节码。
- 守门：本仓守门散在三处（scripts/verify/*.ps1、scripts/audit/tooltip_color_audit.ps1、tools/ 下若干），
  **只跑一处必漏**；PowerShell 守门脚本必须用 pwsh 7 **以子进程方式**跑（部分脚本用 [Console]::Out 直写 stdout，
  本进程内重定向会得到 0 字节日志）。
- 改守门判据时必须做负控反例（证明它会红）；平台差异型断言写成「平台期望矩阵」而非「N 线同构」。
- lang / 手册 / tooltip 改动后跑 tools/check_lang_sync.ps1 + tools/audit_patchouli_keys.py + scripts/audit/tooltip_color_audit.ps1。

【收尾固定动作】
两份 CHANGELOG（CHANGELOG.md ↔ CHANGELOG_ZH.md 逐条一一对应）→ 四线构建 → 部署（pushToRootBuild /
pushToDevRun / pushToGame，后者白名单仅 multi-main）→ 跑守门 → 本地提交（**不 push**）。
发布动作另见 §8-P4。

【可用技能（用户级，按场景直接调用）】
- 内容新增：astral-dice-new-sign-rollout / astral-dice-new-chip-rollout / astral-dice-sound-effects
- 文案与本地化：astral-dice-tooltip-optimizer / tooltip-color-rules / mc-lang-batch-rewrite / patchouli-lang-sync
- 发布：astral-dice-player-changelog / mc-release-lock-and-player-notes / mc-prereq-lib-version-contract /
        mc-modrinth-publish / mc-multiloader-github-ci
- 跨线与移植：mc-multiloader-three-line-patch / mc-multiloader-fix-transplant /
        mc-multiloader-lagging-line-catchup / mc-multiloader-mirror-audit / mc-multiloader-branch-merge
- 取证与排查：mc-vanilla-constant-lookup / mc-thirdparty-content-evidence-probe /
        mc-thirdparty-reflective-api-verify / mc-mixin-crash-triage / independent-second-pass-verification
- 测试台：mc-testbench-harness-hardening / mc-testbench-tick-sampling / mc-client-side-instrumentation
- 建模：blockbench-modeling（内置）+ blockbench-mcp-verification

【工作方式】
先侦察（读配置与文档）→ 再分类（平台差异 / 功能缺口 / 真 BUG）→ 给出方案与代价 → 落地 → 三重验收 →
二次验证 → 本地提交。不确定就标注「待验证」，不要用「应该/理论上」结案。
```

### P2 · 功能改动 / 缺陷修复（P0 优先 + 四线同步）

```text
在 astral_dice_multiloader 上做一次功能改动/缺陷修复。遵守 P1 总纲的全部硬规则与验收口径，另加：

1. 先定位影响面：该功能在四条线的实现文件分别在哪（无 common 共享模块 ⇒ 四线各有一份）。
2. **先改 P0 两条线（neoforge-1.21.1 基准 + forge-1.20.1），功能必须一致**；
   改完再按「三类合理差异」判定 P1 两条线（26.1.2 / fabric）该不该同批改，若要滞后必须登记。
3. 修改代码本身请按项目约定派 subagent（每线一个并行）；**构建与冒烟由主 agent 亲自执行**。
4. 收尾：两份 CHANGELOG 各加对应条目（中英一一对应；工程/CI 类改动只进「工程」段，不进玩家可见段）→
   四线构建 → 部署 → 跑相关守门 → 本地提交（不 push）。
5. 若改动影响平衡性 ⇒ 按 FATAL 级处理，当批修复并补守门。
```

### P3 · 跨线移植 / 追平（含 fabric）

```text
把一个已经落地的改动从一条线移植到另一条线。遵守 P1 总纲，另加：

1. **先判类别**：同版本跨加载器 / 同加载器跨版本 / 跨版本跨加载器 / 反向降级 —— 四类改动面完全不同。
2. **两条轴拆开跑**：加载器轴（元数据/入口/注册/事件/网络/能力/访问变换）改结构；
   MC 版本轴（注册表常量/数据组件/数据包目录/配方泛型/渲染签名）改语义。各自编译通过再合并。
3. fabric 线的**代码蓝本是 forge-1.20.1**（同为 MC 1.20.1）：凡 forge 已实现的功能，
   照搬 forge 文件 + 固定平台替换；但**「整文件照搬」只适用于平台中性文件**，
   平台敏感类必须逐段局部改（实测整文件照搬 35 个 ⇒ 61 编译错 / 26 文件失败）。
4. fabric 专属红线：禁 @Mod.EventBusSubscriber（用 LoadBus）；LoaderBus.scan 不递归内部类；
   禁按字符串名反射原版成员；禁 @ModifyConstant（用 MixinExtras @WrapOperation）；
   附件键注册必须早于玩家数据反序列化。
5. 移植完成必须给出：等价映射表 + 降级项清单（含代价）+ 依赖坐标对齐表 + 验收矩阵；
   平台差异**逐条登记**到 porting/ 对应文件。
6. 收尾跑 mc-multiloader-mirror-audit 做双线一致性审查，并派独立子代理反证「已对等」这个结论。
```

### P4 · 发布（锁版 / hotfix / 双站上传）

```text
对 astral_dice_multiloader 执行一次发布。遵守 P1 总纲，另加以下**顺序不可颠倒**的流程：

0. 发布前核查：四线号源一致 / 产物与内嵌库版本 / 两份 CHANGELOG 与 release/ 玩家日志成对 /
   全部守门跑一遍（散三处）/ 商店路由与渠道派生口径。
1. **先推前置库** starengine_lib（main）：消费方 CI 用**提交 SHA 钉值** checkout 库，
   该 SHA 未推到远端时 CI 会直接 422 失败（不打 tag / 不发 Release / 不传 Modrinth）。
   推完用 `git ls-remote` 确认可达（**不要**用带缓存的网页抓取去查 commits/{sha}，会得到旧 422）。
2. 再推消费方 multi-main ⇒ CI 自动：四线构建 → 打/复用 base tag（`-hotfix` 不跳过）→
   刷新 Release（正文取 release/<tag>/PLAYER_CHANGELOG{_ZH}.md）→ fabric 独立 pre-release → 上传 Modrinth。
3. CurseForge **手动**用 tools/curseforge_upload.py 上传；三条生产线一次调用，**fabric 必须单独一次调用**
   （`--changelog` 是全局参数，多 jar 会共用同一份日志）。
4. 机械依赖：release/<tag>/、release/fabric-<完整版本>/、release/starengine-lib/<库版本>/ 的目录名
   必须与对应版本号**逐字相等**；缺失时对应 Release/版本说明会**静默为空**（不报错）。
5. hotfix 轮注意「旧工件并存」：`--clobber` 只覆盖同名文件 ⇒ GitHub 附件 / Modrinth 版本 / CF 文件
   会新旧并存；**先核对项目惯例再决定是否清理**（Modrinth 可逆归档 / CF 只能网页删 / GitHub 删附件需 token）。
6. CF 的**项目说明字段不可经 API 修改**：全文在 tools/curseforge-description.md，改完需人工粘贴。
7. 发布动作完成后**派独立子代理复核**（发布属强制触发面），核对：CI 结论、Release 附件与正文、
   两站版本列表、CF fileId。
```

---

## 9. 权威信息源索引与复读命令

### 9.1 索引

| 主题 | 文件 |
|---|---|
| **项目权威规范**（60+ 条必须遵守） | `AGENTS.md` |
| 未决清单 / 已处理索引 / 变更记录 | `KNOWN-ISSUES.md`（§1 / §1.1 / §5 / §9 F 组 / §11） |
| 跨线差异与优先级 | `porting/CROSS-VERSION-DIFFS.md` |
| fabric 专项差异与处置 | `porting/fabric-1.20.1/` 全目录：`FABRIC-DIFFS.md`（差异表）、`VERSION_PINS.md`（版本钉）、`API_CHEATSHEET.md`、`EVENT_API_RESEARCH.md`、`PORT_ANALYSIS.md`、`PORT_STATUS_HANDOVER.md`、`SKILL.md`、`CATCHUP-PLAN-2026-10-04.md` + `CATCHUP-PROGRESS-2026-10-04.md`（追平批载体）、`gap-report/`（语义差复算脚本） |
| 26.1.2 迁移 | `docs/compat-26.1.2-neoforge.md` |
| 1.20.1 Forge 差异 | `docs/compat-1.20.1-forge.md` |
| 更新日志 | `CHANGELOG.md` / `CHANGELOG_ZH.md`（工程类条目另见 `scripts/test/TESTING-SPEC.md` 附录 A） |
| 玩家侧发布说明 | `release/<tag>/PLAYER_CHANGELOG{_ZH}.md` |
| 测试规范 | `scripts/test/TESTING-SPEC.md`（+ `TESTING-RULES-OVERVIEW.md`） |
| tooltip 染色规则 | `docs/tooltip-color-rules.md`（`tooltip_color_audit.ps1` 的规则源） |
| 测试台用法 | `scripts/test/fabric/README.md`（fabric 线，自包含）；生产三线见 `scripts/test/TESTING-SPEC.md` 与 `TESTING-RULES-OVERVIEW.md` |
| 商店配置 | `tools/modrinth.json`、`tools/curseforge.json`、`tools/curseforge-description.md`、`tools/firearm-detection-evidence.json`（被 `verify_firearm_detection.py` 消费的实物证据表） |
| 部署与 CI | 各线 `build.gradle`（`packModsDir` / `packPushBranches`）、`.github/workflows/build.yml` |
| 接手指南（本文件） | `docs/AGENT-HANDOFF.md` |
| 最近一次发布核查 | `docs/prerelease-check-1.3.7-hotfix.md` |
| 工作区长期记忆 | `.workbuddy/memory/MEMORY.md` + `.workbuddy/memory/YYYY-MM-DD.md` |

### 9.2 复读命令（**不要直接信本文件的快照数字**）

```bash
# 四线版本号与库引脚
grep -E '^(mod_version|minecraft_version|neo_version|forge_version|fabric_version|starengine_lib_version)' \
  neoforge-1.21.1/gradle.properties forge-1.20.1/gradle.properties \
  neoforge-26.1.2/gradle.properties fabric-1.20.1/gradle.properties

# 工作树 / 分支 / 远端一致性
git worktree list && git branch -vv && git ls-remote --heads origin

# 库仓状态与 CI 钉的 ref
git -C ../starengine_lib log --oneline -3
grep -n 'ref: [0-9a-f]\{40\}' .github/workflows/build.yml

# 商店 id 与 line 映射
python -c "import json;m=json.load(open('tools/modrinth.json',encoding='utf-8'));print(m['projectId'], m['lib']['projectId'])"
python -c "import json;c=json.load(open('tools/curseforge.json',encoding='utf-8'));print(c.get('projectId') or c.get('projectID'))"

# 部署落点（禁按命名推测）
grep -rn 'def packModsDir' --include=build.gradle .
```

---

## 10. 已知的不确定性（不要当成已定论）

1. **本文件是快照**：版本号、提交号、分支领先数、商店 fileId 全部会变；用 §9.2 复核。
2. **`mt.ps1 --case-timeout` 默认值口径不一**：脚本内注释写 600s，`AGENTS.md` 看门狗表写单条 180s —— 分属两个脚本的预算，**未逐行复核运行时实际取值**。
3. **本仓无官方「一键跑全部守门」runner**：§7.1 的聚合方式是自拟模板，未实机验证过全部退出码聚合。
4. **部分守门脚本的 `rc=2` 语义未逐条确认**（已确认的有 `verify_chip_acquisition` / `verify_probe_class_refs` / `verify_resource_integrity` / `verify_selector_key_ownership` / `verify_medkit_and_removal_invariants`）。
5. **`KI-F25②(b)`（偶发 `CancellationException`）来源未定位** —— 必须继续标为「未定位观察项」，**不得**写成「非产品缺陷」。
6. **实机行为层有多项未验证**（26.1.2 的手册/GUI 取证、forge §九 5 项、fabric 的「真医疗箱重登回血数值」、以及 J 键行为/黄心 HUD 位置/AOE 多目标的目视确认）—— 需**前台键盘焦点**，无人值守跑不通。
7. **`docs/` 曾长期被 `.gitignore` 排除**，故 `docs/` 下部分文档的「可复核性」依赖本地文件而非版本历史；可版本化的差异载体在 `porting/`。（该 gitignore 条目已于 2026-10-05 移除。）
8. **`multi-26.1.2-neoforge`**：本地分支存在、远端已删，而 CI 仍列着它 ⇒ 该触发条件永不成立（历史残留）。

