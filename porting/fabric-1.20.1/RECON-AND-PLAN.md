# 1.20.1-fabric 并入开发线（`multi-dev-next`）—— 迁移方案（待确认）

> 用途：执行前的复核底稿。**用户确认后才动代码**。
> 侦察基线：`astral_dice_multiloader-next @ 833392b9`（`multi-dev-next`，ahead 96）、
> `astral_dice_multiloader @ 9b422213`（`multi-main`）、`starengine_lib @ 9362869`（`next`）。
> 侦察日期：2026-10-01。
>
> ⚠️ **2026-10-02 状态更新（本方案已执行完毕，其后又有变更）**：① fabric 线已并入本分支（提交 `693850ce`）；
> ② 本文 §1 表中「库 `next` @ `9362869` / `2.0.0-SNAPSHOT.7`」与「`starengine_lib_fabric @ fea5f06` / `1.0.6-alpha.1`」
> 均为**侦察当时的快照**；③ 库的工作树布局改为 `F:\MCProject\starengine_lib` = **`main`**（发布线）、
> `F:\MCProject\starengine_lib_next` = **`next`**（本仓开发线绑定），`starengine_lib_fabric` 已删除，
> 其分支上唯一未并入的实质改动（`GameplayConstants#CURSED_SWORD_BONUS_MAX` 16 → 32）已收编进 `next` ⇒ 库 `2.0.0-SNAPSHOT.9`。

---

## 0. 一句话结论

开发线从未有过 fabric 线（`git diff 9b422213 HEAD` 里 `fabric-1.20.1/src` 的 1228 条全是「相对主线缺失」）。
本次迁移 = **把主线已有的 fabric 工程整体搬入开发线当骨架**，再**把开发线三线的功能补齐到 fabric 线**，
并**把库的 fabric 平台并进 `next` 分支**，最后统一版本号为 `2.0.0-SNAPSHOT.19`。

⚠️ **不能整分支 merge**：主线 fabric 目录是「三线 → fabric」的移植产物，与开发线三线**不是同一份源码**
（fabric 有 115 个平台 shim 文件、250 个同名文件内容并不逐字节相同）。必须「搬骨架 + 按文件补功能」。

---

## 1. 现状核查（实证数据）

### 1.1 拓扑

| 仓库 | 分支 | 线数 | 版本号 | 说明 |
|---|---|---|---|---|
| `-next`（开发线，本次战场） | `multi-dev-next` | **3** | 三线 `2.0.0-SNAPSHOT.18+<平台>`；库 `2.0.0-SNAPSHOT.7` | 无 `fabric-1.20.1/`、无 `porting/`、无 `scripts/test/fabric/` |
| `astral_dice_multiloader`（主线） | `multi-main` | **4** | 三线 `1.3.6`；fabric `1.3.6-alpha.1+fabric_1.20.1`；库 `1.0.6-alpha.1` | 有完整 `fabric-1.20.1/`（1230 文件）+ `porting/fabric-1.20.1/`（6 文档） |
| `starengine_lib`（库） | `next` | 3 平台 + `common` | `2.0.0-SNAPSHOT.7` | `settings.gradle` 只 include 三平台 |
| `starengine_lib`（库） | `fabric-1.20.1`（独立 worktree `starengine_lib_fabric @ fea5f06`） | 1 平台 | `1.0.6-alpha.1` | **落后于 `next`**：common 少 5 个 `combat` 公式类 |

### 1.2 fabric 骨架的构成（主线）

- `fabric-1.20.1/src/main/java`：**365 个 `.java`**（vs forge 258 / 开发线 forge 263），与 forge **同名 250 个**。
- 115 个 fabric 专属文件，全部是**平台抽象层**，无需重做、整体搬运即可：
  - `platform/event/**`（36）—— 自建 `LoaderBus`，模拟 Forge 事件与 `EventPriority` 排序语义
  - `mixin/bridge/**`（18）—— 无 FAPI 对应事件的原版注入桥
  - `platform/client/**`（17）、`platform/fml/**`（11）—— 客户端事件与 ModList shim
  - `compat/curios/**`（8）—— Curios 门面 → Trinkets/Accessories 双源聚合
  - `platform/network/**`（4）、`platform/registry/**`（2）、`platform/item/**`（2）、`compat/accessories/**`（2）
  - `loot/FabricLootInjector.java`、`crafting/Nbt*`、`datagen/AstralDataGenerator.java` 等
- `porting/fabric-1.20.1/`：6 份文档（`SKILL.md` / `PORT_STATUS_HANDOVER.md` / `PORT_ANALYSIS.md` /
  `API_CHEATSHEET.md` / `EVENT_API_RESEARCH.md` / `VERSION_PINS.md`）—— **这是本次迁移的方法论来源，必须一并搬入**。

### 1.3 🚨 关键发现：主线 fabric 线是「滞后跟随者」

主线三线的最新改动**只做三条线、没跟 fabric**。逐提交核对（自 fabric 引入提交 `c059ead8` 起，30 个改 forge 的提交）：

- **17 个提交**的 java 改动在 fabric 侧**完全没有同步**；
- 特征字符串实证：`fillRecharge`（风水师）/ `descendChaseDamage`（教主）/ `Concealment`（秘密侦探）/
  `ArmorValueProvider` / `CombatPowerHud` / `DamageNumberAggregator` 在 fabric 线**命中 0 次**，
  而在开发线 forge 线命中 2/2/6/3/3/9 次；
- 反例（说明「不是全都没同步」）：`INFINITE_DURATION`、`card_cost` 在 fabric 与开发线**命中数相同**（14 / 7）。

⇒ **fabric 线的功能水平低于它 `gradle.properties` 里写的 `1.3.6`**，更远低于开发线。
⇒ 所以本次「补功能」的范围**不止**开发线的 24 个独有提交，还包括主线并入后漏改 fabric 的那批。

### 1.4 开发线相对主线的净增量（`git diff 9b422213 HEAD`）

- 新增 155 / 修改 109 / 「缺失」1262（绝大多数是 fabric 目录本身）。
- 三线各自的 src 增量**完全同构**：每线 **24 个文件修改 + 11 个文件新增**（以 forge 为例见 §4.3）。
- 三线外：`scripts/test/**`（148 处，含 20 个 `scripts/test/fabric/` 缺失）、`tools/**`、`settings.gradle`、根 `build.gradle`、
  `AGENTS.md`、双 `CHANGELOG`、`.github/workflows/build.yml`（开发线比主线**少 191 行**，缺 fabric 段）。

---

## 2. 目标与验收判据

**目标**：开发线成为**四线同仓**，fabric 线功能与三线对齐；库 `next` 分支含 fabric 平台；四线版本号同号。

| # | 判据 | 手段 |
|---|---|---|
| 1 | 四线均可 `build` 成功 | `:neoforge-1.21.1:build` / `:forge-1.20.1:build` / `:neoforge-26.1.2:build` / `:fabric-1.20.1:build` |
| 2 | 库四平台 build + `publishToMavenLocal` 成功 | `:fabric-1.20.1:publishToMavenLocal` |
| 3 | fabric 产物内嵌库 jar 恰 1 份 | 开 `build/libs/*.jar` 查 `META-INF/jars/` |
| 4 | mixin 注入点在**生产形态**可命中 | 开 jar 读 mixin class 常量池，断言无 `net/minecraft/<小写包>/` Mojmap 残留；客户端预加载 `-PpreloadClasses` |
| 5 | 三语键集一致 | `tools/check_lang_sync.ps1 -LangDir fabric-1.20.1/.../lang` |
| 6 | fabric 资源守门 | `python tools/verify_fabric_assets.py` |
| 7 | fabric 冒烟 | `scripts/test/fabric/ft.ps1` 既有用例（FAB-BOOT-EMBED / FAB-CLIENT-BOOT / FAB-DISPATCH-BASIC / FAB-JAR-ASSETS / FAB-PARTY-BACKENDS） |
| 8 | 功能对齐 | §4.3 的 90 个文件逐个核对，特征字符串在 fabric 线可命中 |

---

## 3. 迁移策略

**三个来源、五个阶段**：

```
来源①  主线 fabric 骨架（1230 + 20 + 6 + 5 文件）        → 阶段 A/B 直接搬
来源②  开发线三线相对主线的增量（每线 35 文件）           → 阶段 C 逐文件补
来源③  主线并入后漏改 fabric 的三线改动（74 文件）        → 阶段 C 逐文件补
                                                      ↓
                        阶段 D 版本号统一 · 阶段 E 配套收口 · 验证
```

**核心操作原则**（来自 `porting/fabric-1.20.1/SKILL.md` + 库/仓既有纪律）：

1. **骨架整体搬**：`fabric-1.20.1/**` 原样复制，**不改** —— 它已经是一个可构建、可启动、有实机证据的移植体。
2. **功能按文件补**：以**开发线 forge 线**为蓝本（同 MC 1.20.1、同 Mojmap），把功能改动落到 fabric 同名文件；
   ⚠️ **禁止整份覆盖** —— fabric 同名文件含平台适配（如 `DiceCombatEvents` 走自建 `LoaderBus`），整份覆盖会打断平台层。
3. **平台三类改动特殊处理**：
   - **lang**：JSON 键级合并（绝不走 patch，冲突标记会破坏结构）；
   - **datagen**：改 provider 后必须 `runDatagen` 重跑，产物核对；
   - **平台机制**：Forge 专有类（`ModAttachments` / GLM / `@SubscribeEvent` / `SimpleChannel`）**整段不搬**，改用 fabric 侧等价物。
4. **每补一批就编译**：javac 报错数**不单调下降**（语法错会让 javac 提前中止 ⇒ 假绿），每批看完整日志。
5. **库先行**：库 fabric 平台必须先 `publishToMavenLocal`，消费方才能解析。
6. **工作区恒干净**：搬骨架前先 `git status` 确认无未提交改动。

---

## 4. 详细作业清单

### 4.1 阶段 A：骨架搬入（**全自动，不改内容**）

| 来源（主线） | 目标（开发线） | 文件数 | 备注 |
|---|---|---|---|
| `fabric-1.20.1/`（含 `src` + `build.gradle` + `gradle.properties`） | 同路径 | **1230** | 排除 `build/`、`.gradle/` |
| `porting/fabric-1.20.1/` | 同路径 | 6 | 移植方法论，留档 |
| `scripts/test/fabric/` | 同路径 | 20 | **独立套件** `ft.ps1`（不走 `mt.ps1`） |
| `tools/verify_fabric_assets.py` | 同路径 | 1 | fabric 资源守门 |
| `tools/fabric_datagen_convert.py` | 同路径 | 1 | datagen 转换 |
| `scripts/devtools/unpack_nested_mod_jars.py` | 同路径 | 1 | dev 展开内嵌 jar（Loom 1.14 不展开） |
| `release/fabric-1.3.5-alpha.1/`、`release/starengine-lib/` | 同路径 | 3 | 历史发布留档 |
| `ref/`（10 个上游参考源，~76MB，gitignored） | 本机复制 | — | API 取证用，可选择性复制 |

> ⚠️ `fabric-1.20.1/gradle.properties` 虽随骨架搬入，但**版本行在阶段 D 必须改**（见 §4.4）。

### 4.2 阶段 B：库侧 `starengine_lib`

**做法**：在 `next` 分支新增 `fabric-1.20.1/` 平台子项目（从 `fabric-1.20.1` 分支取），`common` **零改动**。

| 项 | 内容 |
|---|---|
| 新增文件 | `fabric-1.20.1/build.gradle`、`gradle.properties`、`src/main/resources/fabric.mod.json` + **9 个 java**：`StarEngineLib` / `client/ActionBarManager` / `component/ItemDataKey` / `economy/FabricEconomyStorage` / `event/ModEffectRemoval` / `item/AstralRarities` / `item/TrinketsCompat` / `platform/LoaderEvent` / `platform/LoaderTags` |
| `settings.gradle` | 加 `include 'fabric-1.20.1'` + `maven.fabricmc.net` 仓库（照抄 `fabric-1.20.1` 分支版本） |
| `common` | **不改**：`next` 的 common（44 文件）是 `fabric-1.20.1` 分支（39 文件）的**超集**，多出 5 个 `combat` 类（`CombatFormula` / `DiceBattleResolver` / `TargetBattleStats` / `TargetCategory` / `VanillaMitigation`）——正是骰战公式的**唯一源** |
| 版本号 | `fabric-1.20.1/gradle.properties` 的 `lib_version` / `mod_version` 由 `1.0.6-alpha.1` 改 `2.0.0-SNAPSHOT.8` |
| 依赖坐标 | artifactId `starengine_lib-fabric-1.20.1`（消费方引用名不变） |
| 验证 | `:fabric-1.20.1:build` + `publishToMavenLocal`；随后**必须编译消费方**验证与 `next` common 的兼容性 |

> 🚨 **最大不确定点**：fabric 平台的 9 个 java 是**按旧 common 写的**，`next` 的 common 有 API 演进
> （`economy` / `EventTargetCollector` / `combat` 包）。执行时以 `compileJava` 暴露为准逐处修。

### 4.3 阶段 C：消费方功能补齐（**工作量主体**）

合并三方来源后：**90 个 java 文件需核对/改写 + 6 个新建 + 5 张纹理 + 1 个 mixin json + lang + 元数据**。
（`三线漏改`74 ∪ `开发线增量`20，交集 4。）

#### C-1 新建文件（6 个 java，fabric 侧当前不存在）

| 文件 | 来源 | fabric 适配要点 |
|---|---|---|
| `combat/DamageNumberAggregator.java` | 开发线三线 | 服务端聚合口径；发包走 fabric 的 `platform/network` shim |
| `client/DamageNumberStore.java` | 开发线三线 | 纯客户端状态，几乎直搬 |
| `client/CombatPowerHud.java` | 开发线三线 | HUD → fabric 的 `HudRenderCallback`（`platform/client/event/RegisterGuiOverlaysEvent` shim 已在） |
| `effect/ConcealmentEffect.java` | 开发线三线 | 复刻原版隐身图标；**连带** `effect/ModEffects`（注册）、`item/InvestigationEventUtil`、`lang/*` |
| `mixin/jade/ArmorValueProviderMixin.java` | 开发线三线 | 直搬（Jade fabric 变体） |
| `mixin/jade/<ArmorIcon>Mixin.java` | 开发线三线 | ⚠️ forge 叫 `ArmorElementIconMixin`、neo 叫 `DisplayHelperArmorIconMixin` ⇒ **fabric 需按 Jade-Fabric 的类结构定名**，执行时读 `ref/` 或反编译 Jade-Fabric 确认 |

配套资源：`assets/astral_dice/textures/gui/{attack,defense}_{empty,full,half}.png`（**5 张**）、
`astral_dice.jade.mixins.json`（新 mixin 配置，需登记进 `fabric.mod.json` 的 `mixins` 数组）。

#### C-2 内容核对/改写（90 个文件，按模块分组）

> 判据：以**开发线 forge 同名文件**为蓝本，把功能改动落进 fabric 文件；平台层差异（`LoaderBus` 派发、附件、网络）**保留 fabric 写法**。

| 模块 | 文件 | 数量 |
|---|---|---|
| **战斗核心** | `combat/{DiceCombatEvents, DiceCombatModifiers, OrbitalBombardmentManager, PartyRelations, PlayerHostilityTrackerEvents, ShootingStarManager, CardRegistry, LivingPageImpact, SherryThrowManager, SpellDamageRegistry, DamageNumberAggregator}`、`damage/{ModDamageTypes, RailgunBolts}` | 13 |
| **立牌** | `item/sign/{BaseSignItem, BonnieSignItem, FenSignItem, HannaSignItem, KomachiSignItem, LuluSignItem, MamushiSignItem, MosesSignItem, MosesEnigmaticLink, NancyLuSignItem, NardisSignItem, PandamanSignItem, PaparaSignItem, ParunanSignItem, SherrySignItem, TeruSignItem, ZhaoSignItem}` | 17 |
| **筹码** | `item/chip/{Adrenaline, Airbag, BankCardUnlimited, BigBowlStew, BufferShield, CursedSword, ElectricSword, FanBig, FanSmall, Flashlight, FriendshipBadge, MagicTome, Railgun, Satellite, SmartWatch, Cutter, CutterBlade, PiercingGun}*ChipItem` | 18 |
| **效果** | `effect/{ChargeEffect, HannaDollCompleteEffect, HannaDollCraftEffect, HuguangEffect, MamushiDragonEffect, MisfortuneEffect, ModEffects, SherryReasoningEffect, WeaknessRevealEffect, UnwaveringEffect, ConcealmentEffect}` | 11 |
| **卡牌** | `item/card/{BaseEffectCardItem, EffectCardItem, EffectCardPeriod, LuxuryFeastCardItem, RandomCardHandler, UnwaveringCardItem}` | 6 |
| **事件/计时** | `event/{AstralEventSystem, EffectTimerGuard, LootInjectionHandler, ModEffectEvents, ModTooltipHandler, PlayerLifecycleHandler, PlayerTickEvents, ChipDamageHandler, DamageEffectCardHandler}`、`item/{HealingManager, ChargeManager, InvestigationEventUtil}` | 12 |
| **客户端** | `client/{ClientTickHandler, KeyBindingSetup, ModClientEvents, TargetSelectionClient}` | 4 |
| **网络/注册/入口** | `network/ModNetwork`、`AstralDiceMod`、`screen/ModMenuTypes`、`economy/StarCoinWalletActions`、`item/ModItems` | 5 |
| **⚠️ 需判断是否适用** | `component/ModAttachments`（fabric 用 FAPI 附件 ⇒ **可能整体不搬**）、`datagen/ModRecipeProvider`（fabric 有独立 datagen ⇒ 改 `AstralDataGenerator`）、`mixin/client/{EffectRenderingInventoryScreenMixin, GuiMixin}`（fabric 有 `mixin/bridge/**` 替代 ⇒ 逐个判）、`event/LootInjectionHandler` | 5 |
| **语言** | `lang/{zh_cn, en_us, ja_jp}.json` —— ⚠️ **JSON 键级合并**，不走 patch | 3 |
| **元数据** | `templates/META-INF/mods.toml` → **`fabric.mod.json`**（版本、前置、mixins 数组） | 1 |

> ⚠️ forge 侧新增的 11 个文件中，`effect/ConcealmentEffect` 之外的两个 jade mixin 已计入 C-1；
> `component/AstralData`、`component/ModCapabilities`、`datagen/DataGenerators`、`event/IronSpellbooksCompat`、
> `init/MixinRuntimeGate`、`loot/AddTableLootModifier`、`loot/AstralLootModifiers` 是 **fabric 故意裁剪项**，**不搬**。

### 4.4 阶段 D：版本号同步（用户明确要求）

| 对象 | 现值 | 目标值 |
|---|---|---|
| 三线 `mod_version` | `2.0.0-SNAPSHOT.18+<平台>` | `2.0.0-SNAPSHOT.19+<平台>` |
| `fabric-1.20.1/mod_version` | `1.3.6-alpha.1+fabric_1.20.1` | **`2.0.0-SNAPSHOT.19+fabric_1.20.1`** |
| `fabric-1.20.1/starengine_lib_version` | `1.0.6-alpha.1` | **`2.0.0-SNAPSHOT.8`** |
| `fabric-1.20.1/starengine_lib_version_range` | `>=1.0.6-alpha.1 <2.0` | **`>=2.0.0-SNAPSHOT.8 <2.1`**（Fabric 谓词语法：空格=AND，**不认** Maven 区间） |
| 库四平台 `lib_version` / `mod_version` | `2.0.0-SNAPSHOT.7` | `2.0.0-SNAPSHOT.8` |
| 根 `build.gradle` 注释 | 「**三个**子项目」 | 「**四个**子项目」+ 加 `:fabric-1.20.1:build` 示例 |
| 根 `settings.gradle` | 无 fabric | 加 `include 'fabric-1.20.1'` + `maven.fabricmc.net` 仓库 |

> 约定：「每提交一版」⇒ 本次迁移的最终提交对应 `SNAPSHOT.19`；库对应 `SNAPSHOT.8`。**bump 必须早于 build**。

### 4.5 阶段 E：配套收口

| 项 | 内容 |
|---|---|
| `.github/workflows/build.yml` | 把主线的 fabric 段并回开发线（fabric 的第 4 步 lang sync、artifact、**独立 pre-release tag `fabric-<ver>`**）；并把库 checkout ref 由「库 `fabric-1.20.1` 分支 SHA」**改为「库 `next` 分支 SHA」**（并入后库只剩一条线） |
| `scripts/test/lib/Mt.Paths.psm1` | 主线 fabric 走**独立套件** `scripts/test/fabric/ft.ps1`，**不需要** mt 分派 —— 保持现状即可；如需统一再议（见 §7 待裁决） |
| `AGENTS.md` | 三线口径文案 → 四线；补 fabric 线专属纪律（Loom/JVM21、Fabric 谓词语法、`fabric.mod.json`） |
| `CHANGELOG.md` / `CHANGELOG_ZH.md` | 新段头 `## 未发布（2.0.0-SNAPSHOT.19）`，中英同步（纯单一语言、条目数/顺序一致） |
| `porting/fabric-1.20.1/` | 已在阶段 A 搬入；需把 `SKILL.md` 顶部的「2026-10-01 状态变更」段更新为「本线已并入开发线」 |
| `tools/` | 开发线独有的 `balance_sim/`（4 文件）保留；确认 `verify_fabric_assets.py` / `fabric_datagen_convert.py` 与开发线工具链无冲突 |

---

## 5. 风险与对策

| # | 风险 | 等级 | 对策 |
|---|---|---|---|
| R1 | 库 fabric 平台 9 个类与 `next` common 的 API 演进不兼容 | **高** | 阶段 B 先行，`compileJava` 逐个修；**再**做消费方 |
| R2 | fabric 线落后 17 个提交，功能搬运量被低估 | **高** | 以 §4.3 的 90 文件清单为准逐项勾稽；每模块用**特征字符串**做回归判据（如 `fillRecharge` 必须命中） |
| R3 | Jade 图标 mixin 的 fabric 类结构不同 | 中 | 反编译 Jade-Fabric 确认；必要时推迟该一条，不影响其余 |
| R4 | Loom 只重映射**纯方法名**的 `@Inject(method=...)`，带描述符者原样透传 ⇒ 生产注入失败 | **高** | 新增/改动的 mixin 一律**纯方法名**；开 jar 断言无 Mojmap 残留（判据 4） |
| R5 | dev 环境 Loom 1.14 不展开内嵌 jar（Accessories/Patchouli/puzzlesaccessapi） | 中 | 跑 `scripts/devtools/unpack_nested_mod_jars.py` |
| R6 | `gradlew` 必须用 **JVM 21**（toolchain 17 编译），loom 钉 `1.14.10`、wrapper 9.2.1 不动 | 中 | 写进执行脚本；勿升 loom≥1.16 |
| R7 | lang JSON 冲突标记破坏结构 | 中 | 键级合并 + `check_lang_sync.ps1` |
| R8 | 遗漏 fabric `.gitignore` 差异，把 `build/`、`run/` 提交入库 | 低 | 阶段 A 同时对齐 `.gitignore` |

---

## 6. 执行顺序与提交切分

```
① 库侧  : next 加 fabric 平台 → bump SNAPSHOT.8 → build + publishToMavenLocal → 库提交
② 骨架  : 搬入 fabric-1.20.1 / porting / scripts-test-fabric / tools / release → 改版本号 → :fabric-1.20.1:build 通过
③ 功能  : 按模块 A→J 逐批补齐（每批 compileJava）→ 资源/语言/datagen → build 通过
④ 配套  : 根 settings/build、CI、AGENTS、双 CHANGELOG、porting 文档状态更新
⑤ 验证  : 四线 build + 库 publish + 守门（16 项 + fabric 资源 + lang）+ fabric 冒烟 + 开 jar 核符号
⑥ 收尾  : bump 三线 .19（若 ② 已用则确认）→ 双 CHANGELOG 段头 → 四线 build → 部署 → 本地提交（默认不 push）
```

**建议提交切分**（保持可回滚）：
- `feat(fabric): 并入 1.20.1-fabric 第四条线 —— 骨架与配套`（②④）
- `feat(fabric): 补齐 fabric 线功能到三线水平 —— 战斗/HUD/立牌/筹码/效果/卡牌`（③）
- `chore(release): 四线版本号统一 2.0.0-SNAPSHOT.19`（⑥，或并入上两条）
- 库：`feat(fabric-1.20.1): 新增 fabric 平台并同步 next 线 → 2.0.0-SNAPSHOT.8`

---

## 7. 待用户裁决

1. **版本号口径**：fabric 线由 `1.3.6-alpha.1+fabric_1.20.1` 直接改为 `2.0.0-SNAPSHOT.19+fabric_1.20.1`（与三线同号）——是否确认？（`-alpha.x` 后缀随之取消）
2. **fabric 测试台**：沿用主线**独立**的 `scripts/test/fabric/ft.ps1` 套件，还是并入 `mt.ps1` 分派（`1.20.1-fabric` 版本键）？
3. **`ref/` 参考源**（~76MB，gitignored）：本机从主线复制，还是按 `VERSION_PINS.md` 重新克隆？
4. **CI 合并方式**：`build.yml` 是「以主线版本为基底再叠加开发线差异」，还是「以开发线版本为基底再叠加主线 fabric 段」？（建议后者，避免丢开发线的 CI 改动）
5. **提交粒度**：按 §6 的三段式提交，还是合并为一次性大提交？
6. **是否同步把 fabric 线纳入「四线同步改」默认约定**（即此后三线改动必须同步 fabric）？

---

## 附：本方案的关键证据索引

| 结论 | 证据 |
|---|---|
| 开发线无 fabric 线 | `git diff --name-status 9b422213 HEAD` ⇒ `fabric-1.20.1/src` 1228 条 `D` |
| fabric 骨架规模 | `fabric-1.20.1/src/main/java` = 365 java，与 forge 同名 250 |
| fabric 平台层 | 「仅 fabric 有」= 115 文件：`platform/**` 36+17+11+4+2+2、`mixin/bridge/**` 18、`compat/**` 10 等 |
| fabric 落后 | `grep -rl fillRecharge fabric-1.20.1/src` = 0（开发线 forge = 2） |
| 漏改提交 | `c059ead8..HEAD` 中 30 个改 forge 的提交里，17 个未同步 fabric |
| 作业清单 | 三线漏改 74 ∪ 开发线增量 20（交集 4）= **90**；新增 6 |
| 库差异 | `git diff --stat next fabric-1.20.1 -- common/` ⇒ 仅 5 个 `combat` 类（425 行删除） |
| 版本号 | 三线 `.18` / fabric `1.3.6-alpha.1` / 库 `.7` |
