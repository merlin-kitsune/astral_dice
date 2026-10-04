# 项目全景扫描报告（接手交接文档）

> 扫描时间：2026-09-11 · 分支 `multi-1.20.1-1.21.1`（本地领先 `origin` 4 个提交，未推送）
> 权威规范来源：仓库根 `AGENTS.md`（738 行，`.gitignore` 排除不入库）+ `docs/` 六份配套文档
> 本文件由扫描生成，仅用于快速建立认知；**规则冲突时以 `AGENTS.md` 为准**

---

## 一、仓库定位

`astral_dice_multiloader` 是 Minecraft 模组 **Astral Dice（星骰）** 的**多加载器单仓**。

- 不是常见的「同 MC 版本 + common 共享源码」模板结构，而是**每个「MC 版本 + 加载器」一个自包含子项目**。
- 原因：1.21.1 ↔ 1.20.1 跨 MC 大版本，API 差异过大（数据组件/附件、事件体系、Curios 注册、Mixin、数据包目录全部不同），做 common 共享层需整体重构、风险不可控。
- 代价：**功能对等只能靠纪律 + 审计保证**，没有任何编译器强制。这是本项目最大的结构性风险来源。

| 子项目 | 定位 | MC | 加载器 | Java | 当前版本 |
|---|---|---|---|---|---|
| `neoforge-1.21.1` | **主线**，以其为准 | 1.21.1 | NeoForge 21.1.235 | 21 | `1.2.0+neoforge_1.21.1` |
| `forge-1.20.1` | 移植线 | 1.20.1 | Forge 47.4.10 | 17 | `1.2.0+forge_1.20.1` |

构建工具：Gradle 9.2.1（阿里云镜像）+ ModDevGradle 2.0.141 / legacyforge 2.0.144（Groovy DSL，原样沿用分支脚本，未改 Kotlin DSL）。

---

## 二、规模与结构

### 源码

| 指标 | neoforge-1.21.1 | forge-1.20.1 |
|---|---|---|
| `.java`（`src/main`） | 196 | 198 |
| 代码行数 | 19,636 | 20,055 |
| `@SubscribeEvent` 处理器 | 64 | 68 |

包结构两侧**完全一致**（`mixin/trade/client` 子包亦一致）：`init` `item{,.dice,.card,.chip,.sign}` `combat` `event` `effect` `component` `config` `datagen` `client` `network` `mixin{,.client,.trade}` `screen` `recipe` `resource` `trade` `damage` `test`。

### 注册内容基线

- 物品 **118**（`registerItem` 调用 119 处，含 1 处辅助注册）
- MobEffect **33**、物品数据组件 **10**、玩家附件 **60（neo）/ 61（forge，多瞬态 `curse_original_amount`）**
- 语言键 **587**（`zh_cn.json` / `en_us.json` 各 587，双侧 key 完全一致）
- 标签文件 **12**、配方 **112**、进度 **99**、帕秋莉手册条目 **123**（手写 JSON 共 137 个，含 `book.json`/`categories/`）

### 资源

| | assets | data |
|---|---|---|
| neoforge | 351 | 61 |
| forge | 351 | 55 |

数据包目录命名是**已知的、非缺陷的平台差异**：`data/astral_dice/` 下 neo 用 `advancement/recipe/loot_table/structure`，forge 用 `advancements/recipes/loot_tables/structures`；forge 多 `pack.mcmeta`（pack_format 15）与 `data/forge/` 前缀。

---

## 三、构建与部署链路

```
gradlew build
  └─ finalizedBy ─┬─ pushToDevRun   → run/1.21.1/mods  | run/1.20.1/mods（仓库根）
                  ├─ pushToGame     → D:\.minecraft\versions\狐の航空学 Voxy Edition\mods
                  │                   D:\.minecraft\versions\1.20.1 模组测试\mods
                  └─ pushToRootBuild→ 根 build/libs（双版本 jar 统一交付目录）
```

三个任务都是**「先删除、后复制」**（防旧版本号 jar 与新产物共存导致双加载）。整合包目录缺失（CI 环境）时 `pushToGame` 自动跳过并告警，不失败。

**forge 独有的致命细节**：产物分两级——
- `build/devlibs` = 未重混淆 jar（Mojmap 名）→ **只能**推 dev run
- `build/libs` = `reobfJar` 重混淆 jar（SRG 名）→ **只能**推生产包

推错方向直接 `NoSuchFieldError`。`pushToDevRun` 取 `jar.archiveFile`、`pushToGame`/`pushToRootBuild` 取 `reobfJar.archiveFile`，**不要改动该取值逻辑**。

当前产物：`build/libs/astral_dice-1.2.0+neoforge_1.21.1.jar`（858 KB）、`astral_dice-1.2.0+forge_1.20.1.jar`（888 KB），时间戳 2026-09-11 19:24。

### 外部依赖

| | NeoForge 侧 | Forge 侧 |
|---|---|---|
| 必需 | Curios、Patchouli 1.21.1-93 | Curios、Patchouli 1.20.1-85-forge |
| 可选/编译期 | JEI、Iron's Spellbooks（`base-mod-libs` 本地 jar） | JEI、Iron's Spellbooks（modCompileOnly） |
| 测试/开发 | Mixin 0.8.5、KubeJS 2101.7.2 | Mixin 0.8.5、**Mixin Booster**、ModernFix、KubeJS 2001.6.5 |

---

## 四、开发规范（铁律摘要）

### 1. 改动纪律

1. **功能/修复默认双版本对等**：先在 `neoforge-1.21.1` 实现并以之为准，再按 `docs/compat-1.20.1-forge.md` 同步 forge。仅当用户明说「只改 1.21.1」才可单侧。
2. **收尾四步自动执行**（无需用户逐项指示）：同步两 CHANGELOG → 构建两版本 → 自动部署整合包 → 自动本地提交。
3. **默认不 `git push`**。需要推送时必须走代理 `http://127.0.0.1:7897`（仓库级 `http.proxy`/`https.proxy` 已配）。
4. 平台差异按各平台正确写法实现（数据组件/附件 vs `ItemDataKey` + Capability），**不得为「看起来一致」破坏目标平台写法**。

### 2. CHANGELOG

- 拆成两份**纯单一语言**文件：`CHANGELOG_ZH.md`（中）/ `CHANGELOG.md`（英），禁内联双语。
- 同版本**条目数与顺序完全一致**，子节相对位置一致（新内容 / 内容与平衡性调整 / 已修复BUG / 工程 ↔ New Content / Content & Balance / Bug Fixes / Project）。
- 未发布版本的后续改动**合并进原条目**，禁追加「再次修改」类条目。
- 开新版本：先升 `gradle.properties` 的 `mod_version`，再在两文件顶部建 `未发布(<版本>)` / `Unreleased (<version>)`。
- 自检：按版本号比对两文件 `- ` 条目数须相等。

### 3. 新增物品的标准动作矩阵

| 物品类型 | 必须同步的位置 |
|---|---|
| **通用** | `ModItems` 注册 → `ModCreativeTabs` → `datagen/ModItemModelProvider`（模型）→ `datagen/ModRecipeProvider`（配方）→ Curios 槽位标签 `data/curios/tags/item/{chip,dice,stand}.json` → **汇总标签** → lang（名称 + tooltip，中英成对）→ `ModTooltipHandler` tooltip 分支 → 帕秋莉手册条目 |
| **筹码** | 额外：`astral_dice:chips` 汇总标签；**品质按图标边框颜色定**（蓝=RARE / 紫=EPIC / 金=UNCOMMON）；属性类覆写 `ICurioItem.getAttributeModifiers`，修饰器 id 用 `attributeModifierId` 派生（同属性不同筹码禁共用 id）；充能筹码**必须**追加「当前充能」计数器 `addChargeCounter` |
| **立牌** | 额外：`astral_dice:signs` 汇总标签；**固定英文 id**（如 `komachi` 而非 `ninja`），贯穿类名/字段/注册名/lang/纹理；配方 shape 中骰子固定中下且只 1 个 |
| **骰子** | 额外：对应 `dice_t0~t4` 阶层标签 + `dices` 汇总标签（否则无法作为升星母体）；`DiceTier` 注册时 item 参数**必须传 `Supplier`** 延迟解析 |
| **效果牌** | 统一继承 `BaseEffectCardItem`，共用使用流程（专属校验→出牌锁→施加→登记→复制计数→消耗）；**禁止**在牌内自行实现 `use()` 出牌/冷却 |

⚠️ 三个最易漏的坑：**汇总标签**（漏则图鉴/联动按标签检索时静默漏项）、**tooltip 渲染分支**（漏则只有名字没有说明）、**手册条目**（漏则图鉴缺项）。基线：chips 55 / signs 17 / dices 13 / cards 25。

### 4. 语言与手册

- `zh_cn.json` / `en_us.json` key **必须一一对应**；每次改中文必须同改英文。校验：
  `python tools/check_lang_sync.ps1 --lang-dir <子项目>/src/main/resources/assets/astral_dice/lang`（CI 也跑，不一致即构建失败）。
- lang 值中**字面百分号必须写 `%%`**（单 `%` 会抛 `Format error`）。
- 帕秋莉手册**无文案副本**：所有页面 `text` 都是语言键，只有 `en_us` 子目录；换行用宏 `$(br)`（**不是 `\n`**），正文禁 `§` 染色。
- 语言文件格式基线：UTF-8 / LF / **结尾无换行** / 2 空格缩进 / 每 key 独占行 → **禁止 `json.dump` 整写**，必须逐行定点替换。

### 5. 构建红线

- `gradlew` 一律**后台运行 + 60 秒超时**（Gradle 已知 bug：构建结束守护进程不退出）。超时须强杀进程树，再用「日志 `BUILD SUCCESS` + 产物时间戳 + 开 jar 核对」三重验证。**绝不误杀 MC 客户端**。
- `runData` 前**必须移开** `run/*/mods/astral_dice-*.jar`（否则遮蔽新编译类，用旧代码生成资源且不报错）。
- 只改 `ModRecipeProvider` 不跑 `runData`，jar 内仍是旧配方——核对**必须开 jar 读** `data/.../recipe{,s}/<id>.json`。
- **禁无流程 `runClient`**；游戏内验证默认由用户手动。仅「自动化测试流程」章节授权的自动化 runClient 例外。
- 运行时用**系统版本**：Python `C:\Users\xmace\AppData\Local\Python\bin\python.exe`、Node、Git `C:\cmd\git`。不要用 WorkBuddy 内置版本，也不要动 `safe-bin` 目录。

---

## 五、审计与校验工具现状

| 工具 | 作用 | 本次实测结果 |
|---|---|---|
| `compat` 技能（用户级，只读） | 七维度双版本对等审计 | ✅ **7/7 维度无漂移** |
| `tools/check_lang_sync.ps1` | 中英 lang key 一致性 | ✅ 两侧各 587 key 完全一致 |
| `patchouli-lang-sync` 技能 | lang ↔ 手册 ↔ tooltip 措辞一致性 | ✅ 6 对材料 tooltip/手册全部一致 |
| `.github/workflows/build.yml` | CI：lang 检查 → 双版本 build → 打 tag → 发 Release | ⚠️ 见风险 R3 |

`compat.py` 明细：item 118=118、lang 587=587、tag 12=12、recipe 112=112、advancement 99=99、handbook 123=123、changelog 10 节 216=216。

---

## 六、可改进点 / 潜在风险

### 🔴 R1 真实功能缺失：1.20.1 未移植「调查阶段」击杀触发

- 现象：`forge-1.20.1/.../item/InvestigationEventUtil.java` **没有** `onUndercoverInvestigationKill`，且类上**没有** `@EventBusSubscriber`；其 `triggerByKill(...)` 在 forge 全局**零调用点**（死代码）。
- 但 forge 的 `BonnieSignItem` 第 109 行注释明确写着「被动 3 已移至 `InvestigationEventUtil.onUndercoverInvestigationKill` 统一处理」——注释指向了一个**不存在的方法**。
- 影响：1.20.1 上击杀「隐匿调查」目标**不会触发调查阶段事件**，秘密侦探立牌被动 3 实际失效（与 `AGENTS.md` 描述的功能不对等）。
- 附带的同类缺口：forge 也缺 `onUndercoverRemoved`，`undercover_source` 附件在被移除时不会被清空。

### 🔴 R2 真实功能缺失：1.20.1 缺「标记」层递减

- 现象：neo 的 `MarkManager` 有 `onMarkExpired`（标记到期时层数逐层递减、层数归零时移除伴随的 GLOWING），forge 的 `MarkManager` 只有 43 行、**无任何事件监听**，也无等价实现。
- 影响：1.20.1 上多层标记到期时**一次性全部消失**（而非逐层递减），发光效果的显式清理缺失。
- 说明：`docs/compat-1.20.1-forge.md` 全文**未提及** R1/R2，属未记录漂移。

### 🔴 R3 CI 分支过滤器指向已废弃分支名

`.github/workflows/build.yml` 的 `on.push.branches` 仍写着 `'multi-1.20.1/1.21.1'`（带斜杠的旧名）。工作分支已于 2026-09-11 改名为无斜杠的 `multi-1.20.1-1.21.1`，远端也只有 `origin/multi-1.20.1-1.21.1` 与 `origin/dev-targetselector`。

- 影响：往工作分支 push **不会触发 CI**（PR 与 tag push 仍会触发），lang 同步检查与双版本构建的 CI 守门形同虚设。
- 顺带：`main` 分支自动打 tag 逻辑依赖 `refs/heads/main`，当前仓库并无 `main` 分支。

### 🟡 R4 5 个筹码生存模式不可得

`big_bowl_stew_chip`（大碗炖肉）、`member_recommendation_chip`（会员推荐）、`bookmark_chip`（书签）、`piggy_bank_chip`（小猪存钱罐）、`smart_watch_chip`（智能手表）：

- 已注册、已有图标模型、已进汇总标签与 Curios 标签、已进手册与 lang；
- 但**无配方、不在 Bountiful `astral_rews` 奖励池、不在随机卡牌池、无任何掉落/进度产出** → 仅创造模式/指令可得。

**需用户决策**：建配方，还是纳入奖励池？（`big_bowl_stew_chip` 与 `piggy_bank_chip` 的实现逻辑已存在，属「做完但没接上获取途径」）。

### 🟡 R5 `AGENTS.md` 不入库 + 文档与代码可能失同步

`AGENTS.md`、`docs/`、`scripts/`、`.workbuddy/` 均被 `.gitignore` 排除。规范唯一权威副本只存在于本地——换机器/协作即丢失，且 CI 无法据此校验。建议至少把 `AGENTS.md` 的「规范」部分（非临时调查记录）纳入版本控制。

### 🟢 R6 代码卫生（低危，不影响功能）

- forge 侧死代码：`DiceTierRegistry.all()`、`ActionBarManager.clear()/isShowing()` 定义但零调用。
- `.gitignore` 存在畸形条目 `"temp/`（含未闭合引号，无效模式）与重复的 `temp/`、`run/`、`build/` 段（迁移时叠加造成）。
- `build/` 同时出现在 Gradle 段与「项目级排除」段。

### 平台差异（非缺陷，勿「修正」）

`recipe/DiceUpgradeShapedRecipe`（98 vs 183 行）、`ModAttachments`（889 vs 810）、`component/*`、`client/ModClientEvents`（`registerGuiLayers` ↔ `onClientSetup`+`registerGuiOverlays`）、`event/EnderDiceHandler`（`LivingDamageEvent.Pre` ↔ `LivingHurtEvent`）、`network/`（`ModPayloads` 拆分 ↔ `ModNetwork` 单文件）、`ModEffectEvents` 的 `event.getEffect().value()` ↔ `getEffect()` 等，均为 API 差异导致的必然不同。

---

## 七、新功能开发准备清单

**开工前必读**：`AGENTS.md` 对应章节 → `docs/compat-1.20.1-forge.md`（API 差异）→ 本次扫描报告的 R1~R4（避免踩到已存在的不对等）。

**标准流程**：

1. `neoforge-1.21.1` 实现（按第四节的矩阵补齐所有注册/资源/数据点）
2. 跑 `python tools/check_lang_sync.ps1 --lang-dir neoforge-1.21.1/.../lang`
3. 按兼容文档同步 `forge-1.20.1`（注意 `recipe`→`recipes` 目录名、`.get()` 取值、Capability 写法）
4. 跑 forge 侧 lang 检查
5. 同步两份 CHANGELOG（条目一一对应，同版本条目数相等）
6. `gradlew build`（自动部署 + 根 `build/libs`）
7. 本地提交（**不推送**）
8. 收尾跑一次 `compat.py --root .` 确认无新漂移
9. 若动过 lang/手册，再跑 `patchouli-lang-sync` 的 `lint`

**验证三件套**（用户长期要求）：日志 `BUILD SUCCESS` + 产物时间戳 + **开 jar 核对内容**。不接受「只改源码不跑数据生成」或「只看日志不看产物」的结论。

---

## 八、待用户决策项

| # | 事项 | 选项 |
|---|---|---|
| 1 | R1/R2 两处 1.20.1 功能缺失 | 立即补齐（双版本对等）／记录为已知差异 |
| 2 | R3 CI 分支过滤器 | 改为 `multi-1.20.1-1.21.1` ／ 保持现状 |
| 3 | R4 五个筹码获取途径 | 各建配方／纳入 Bountiful 奖励池／明确设计为仅创造可得 |
| 4 | R5 `AGENTS.md` 入库 | 部分入库／保持现状 |
| 5 | 充能 6 筹码是否另入赏金池 | 入池／仅合成 |
