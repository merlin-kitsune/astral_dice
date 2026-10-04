# Astral Dice（星之骰戏）

[English](README.md) | **中文**

**Minecraft multiloader 模组：1.21.1 NeoForge / 1.20.1 Forge / 26.1.2 NeoForge / 1.20.1 Fabric（移植线）**

> 本仓库为 multiloader 单仓：`neoforge-1.21.1/`（1.21.1 NeoForge 主线）、`forge-1.20.1/`（1.20.1 Forge 移植线）、`neoforge-26.1.2/`（26.1.2 NeoForge 迁移线）与 `fabric-1.20.1/`（1.20.1 **Fabric 移植线**，2026-10-01 作为第四条线并入 `multi-main`）四个自包含子项目，共享同一套玩法内容。详见 `docs/multiloader-layout.md`。

Astral Dice 是一个以「骰子」为核心的生存扩展模组。戴上骰子，每一次近战攻击都会掷出命运之骰，触发「骰神赐福」，进入攻防对垒的骰战玩法。

---

## 核心玩法

- **骰子 · 13 种**：从基础骰子到「下界之星骰子」逐阶升级（黄金 / 玻璃 / 下界岩 → 钻石 / 绿宝石 / 黑曜石 / 诡异 / 紫晶 → 下界合金 / 绯红 / 末影 → 下界之星）；星级（0–3★）提升解锁更多费用点数与卡牌槽位（0★=4 / 1★=6 / 2★=8 / 3★=12 格，攻防各半）。
- **战斗牌**：攻击牌与防御牌，插入骰子卡牌栏后，在骰神赐福中提供随机点数加成；每张卡牌拥有独立耐久。
- **效果牌**：王之力、狂暴、岿然不动、以毒攻毒、对怪激光、轨道炮、活体书页、命运的指引等，拥有独立的出牌数与冷却周期。
- **立牌 · 24 位**：每位角色都有专属被动与主动技能 —— 经商、扫地机、护法、忍者、吸血鬼、风水师、教主、绿洲女王、蛟龙、机械师等。
- **筹码 · 61 枚**：拳击手套、速度轮滑、摩托头盔、医疗箱、魔法箭袋、星币锤、美工刀、手电筒-强光等被动饰品，提供攻击、防御、移速、生命、星光等多维加成。
- **资源流派**：治愈点、星光、标记层数、充能层数四大玩家资源体系，配合立牌与筹码形成多样构筑。

## 特色系统

- 骰神赐福攻防对垒
- 法伤模块：兼容弓箭、三叉戟、魔法与多种法术模组
- 卡牌选择界面：卡牌栏按骰子星级（0★=4 / 1★=6 / 2★=8 / 3★=12 格，攻防各半），实时数值预览
- 事件系统：大侦探、调查员、秘密侦探等立牌联动
- Bountiful 赏金板联动

## 本版更新（1.3.3）

- **九项平衡与技能调整**：风水师「完美帮手」把目标的「养精蓄锐」直接补满到 5 层；「白泽赐福」与「降神」的 2:00 倒计时改为「任意攻击命中」即可启动（此前限近战）；教主「降神」的狐光追加改为独立的「追击」额外伤害；秘密侦探「调查阶段」的原版隐身整体替换为新效果「隐匿」；命运的指引新增「战斗骰点始终为 6」；岿然不动与蓄力的文案精简；史莱姆立牌移除主动技能的范围治疗；大碗炖肉不再治疗友方生物、治愈点由 1 提到 2。
- **四个「受击类」效果收紧为「只有敌对目标的攻击才触发」**：缓冲盾牌、鼠鼠护盾的「反击」、史莱姆立牌「细胞分裂」、吸血鬼立牌的「汲取」—— 此前摔落 / 仙人掌 / 着火等环境伤害与自伤同样能触发（可白刷收益），现在只有敌对目标的攻击才算。
- **动作栏（ActionBar）修复**：补齐两条缺失的提示文案；清除 11 条文案内嵌色码造成的「前半黄、后半灰」断层；拒绝类提示统一为红色；安全气囊 / 钱包 / 卡牌栏 / 客户端「出牌数已用完」四处从原版覆盖层合并回模组动作栏。
- 完整清单见 [Releases 页面的发布说明](https://github.com/merlin-kitsune/astral_dice/releases)。
## 支持版本 / 环境要求

| 支持 | 子项目 | Minecraft | 加载器 | Java | 当前模组版本 | 帕秋莉手册 |
|---|---|---|---|---|---|---|
| ✅ | `neoforge-1.21.1` | 1.21.1 | NeoForge 21.1.235 | 21 | 1.3.6 | ✅ |
| ✅ | `forge-1.20.1` | 1.20.1 | Forge 47.4.10 | 17 | 1.3.6 | ✅ |
| ✅ | `neoforge-26.1.2` | 26.1.2 | NeoForge 26.1.2.109 | 25 | 1.3.6-beta.1 | ✅ |
| ✅ | `fabric-1.20.1` | 1.20.1 | Fabric Loader 0.19.5 | 17 | 1.3.6-alpha.1 | ✅ |

- `neoforge-1.21.1` 与 `forge-1.20.1` 是**发布线**（功能对等，两者的 jar 随每个 GitHub Release 发布）。
- `neoforge-26.1.2` 是**迁移线**（2026-09-19 起已纳入主线、与另两线同级同步；2026-09-22 起由客户端实验性支持转为**正式迁移线**）：**不单独打 tag / 发 Release**，但其 jar 作为**第三个附件随发布线的 Release 一并发布**（CI 三线同批构建，见 `.github/workflows/build.yml`）。该线**无** Iron's Spells 'n Spellbooks 联动（上游无 26.1.x 构建）。
- `fabric-1.20.1` 是 **Fabric 移植线**（2026-09-29 接入；2026-10-01 作为第四个子项目并入 `multi-main`，原独立分支与独立工作树均已移除）。其版本号**独立维护并一律带 `-alpha.x` 预发布后缀**（当前 `1.3.6-alpha.1`）—— 与 26.1.2 线的 `-beta.x` 是同一套安排：预发布线**永不占用**发布线的裸版本号。该线**单独发一个 `fabric-<版本>` 预发布 Release**，其 jar **绝不**混入发布线的 Release。⚠️ 它的前置是**另一套**（见下表），且**无** Curios / Bountiful / Iron's Spells 'n Spellbooks 联动。

| 前置/联动 | 要求 |
|---|---|
| 前置模组（**已内嵌，无需单独安装**） | **StarEngine Lib**（`starengine_lib`）**自 1.3.1 起内嵌于本模组**（当前**四条线均内嵌 `1.0.12`** —— Fabric 线的库版本已于 2026-10-03 对齐为与三条发布/迁移线相同的号；兼容区间 **`[1.0.12,2.0)`** / **`>=1.0.12 <2.0`**）：加载器启动时自动载入内嵌副本。⚠️ **请勿**再往 `mods` 里单独放 `starengine_lib-*.jar` —— 加载器按 modId 去重时优先采用那一份，更旧的会盖掉内嵌库。⚠️ 库仓**本身不提供任何 jar 下载**（2026-10-01 起只保留源码），本来也无从下载 |
| 前置模组 | Curios API（1.20.1 用 Curios 5.x；1.21.1 用 Curios 9+；**26.1.2 用 Curios 15+**，缺失时会在 NeoForge 依赖排序阶段被拒绝）。⚠️ 该前置**由本模组声明**；StarEngine Lib 本身仅在 forge 侧把它作为**编译期**依赖（不进库的 `mods.toml`） |
| 前置模组（仅 1.20.1） | **Mixin 运行时二选一**（自 1.3.2-hotfix 起）：**Mixin Booster ≥ 0.1.3** **或** **Sinytra Connector**。整合包自带 Sinytra Connector 时**无需额外安装**（Connector 自带同一套 Mixin 运行时，此时 Mixin Booster 会自动让位）；两者都没有才会拒绝启动并提示安装方式。装了**旧版** Mixin Booster 仍会被拒 |
| 前置（仅 fabric-1.20.1） | **另一套** —— 见下方 **[Fabric（1.20.1）前置一览](#fabric1201前置一览)**：**Fabric Loader 0.19.x + Fabric API**（1.20.1 线，0.92.12）+ **Puzzles Lib 8.1.33**（连同它的 **Forge Config API Port 8.0.3**），外加 **Trinkets 3.7.2** **或** **Accessories 1.0.0-beta.48**（装任一即可）。发布线的 Curios / Mixin Booster 那一套在本线**不适用** |
| 可选（仅 fabric-1.20.1） | 帕秋莉手册。（本线**无** Bountiful —— 该模组无 1.20.1 Fabric 版） |
| 可选联动 | Bountiful、帕秋莉手册 |

### Fabric（1.20.1）前置一览

Fabric 移植线用的是**另一套前置** —— 发布线的 Curios / Mixin Booster 那条链在本线**完全不适用**：

| # | 前置 | 版本 / 说明 |
|---|---|---|
| 1 | **Fabric Loader** | **0.19.5+**（0.19.x） |
| 2 | **Fabric API** | **1.20.1** 线，**0.92.12** |
| 3 | **Puzzles Lib**（事件桥） | **8.1.33+** —— 请连同它自己的前置一起装 |
| 4 | **Forge Config API Port** | **8.0.3**（Puzzles Lib 8.x 的要求） |
| 5 | **饰品栏提供者** —— *二选一* | **Trinkets 3.7.2+** *或* **Accessories 1.0.0-beta.48+** |
| 6 | 可选 | **帕秋莉手册**（游戏内手册） |
| — | **本线不适用** | Curios API、Mixin Booster / Sinytra Connector、Bountiful、Iron's Spells 'n Spellbooks |

- 饰品栏是**二选一**：Fabric 的 `depends` 是 AND 语义、表达不了 OR，故 Trinkets 与 Accessories 都声明为 `recommends`；模组自己在启动时校验，**只有两个都不在**时才拒绝启动。
- **商店登记**：**Modrinth 与 CurseForge 同口径** —— 本线把 **Trinkets** 记为*必需*、**Accessories** 记为*可选*。这只是商店页面上的声明：模组自身的 `fabric.mod.json` 仍把两者写在 `recommends`，因此「只装 Accessories」的环境照样能正常启动。

---

## 下载

- **发布页**：[GitHub Releases](https://github.com/merlin-kitsune/astral_dice/releases)（每个版本附**三个** jar：发布线两个 + 26.1.2 的 `-beta` jar）
- **Fabric 移植线**：单独发一个**预发布（pre-release）** `fabric-<版本>`（标记为 pre-release、不占「Latest」位），附件**只有它自己那一个** jar —— 刻意与发布线的 Release 分开，避免被当成正式版下载
- **Modrinth**：四个构建同批发布到主项目（id `5xDtrJ8X`）；内嵌的前置库 **StarEngine Lib** 发布到**它自己的**项目（id `2dIXA5wO`）—— 库产物绝不混进本模组的项目。Modrinth 上的版本号保留 `+<加载器>_<MC版本>` 后缀（如 `1.3.6+neoforge_1.21.1`），四条线因此可区分；发布渠道由版本号后缀决定（`-alpha` → alpha、`-beta` → beta、其余 → release）。⚠️ 库项目上的 jar 是为了让 `embedded` 依赖引用可解析而发布的，**请勿单独安装**：库已内嵌于本模组，加载器按 modId 去重时优先采用独立那份 ⇒ 独立副本会盖掉内嵌库
- 支持平台：Minecraft 1.21.1 / NeoForge、1.20.1 / Forge（发布线）；Minecraft 26.1.2 / NeoForge（迁移线，不单独发 Release，jar 随发布线 Release 附带）；Minecraft 1.20.1 / Fabric（移植线，单独预发布、不与发布线 Release 混装）
- 前置：Curios API（1.20.1 另需 Mixin 运行时：Mixin Booster ≥0.1.3 **或** Sinytra Connector，二选一）。**StarEngine Lib 已内嵌于本模组，无需单独安装**；库仓本身**只提供源码**（不提供 jar 下载）。**Fabric 线是另一套前置** —— **Fabric Loader 0.19.x + Fabric API + Puzzles Lib（+ Forge Config API Port）**，再加 **Trinkets 或 Accessories 二选一**；Curios 与 Mixin 运行时在本线**不适用**（详见上方「Fabric（1.20.1）前置一览」）
- 构建产物：`neoforge-1.21.1/build/libs/astral_dice-<版本>+neoforge_1.21.1.jar`、`forge-1.20.1/build/libs/astral_dice-<版本>+forge_1.20.1.jar`、`neoforge-26.1.2/build/libs/astral_dice-<版本>+neoforge_26.1.2.jar`、`fabric-1.20.1/build/libs/astral_dice-<版本>+fabric_1.20.1.jar`；GitHub Release 的 tag 使用无后缀的基础版本号（如 `1.3.5`），Fabric 移植线则用 `fabric-<版本>` 预发布 tag

## 构建

```bash
./gradlew build                    # 构建全部子项目（四个版本）
./gradlew :neoforge-1.21.1:build   # 仅构建 1.21.1 NeoForge
./gradlew :forge-1.20.1:build      # 仅构建 1.20.1 Forge
./gradlew :neoforge-26.1.2:build   # 仅构建 26.1.2 NeoForge（迁移线）
./gradlew :fabric-1.20.1:build     # 仅构建 1.20.1 Fabric（移植线）
```

- **模组依赖来源有硬规则**：所有第三方模组（含前置/附属模组）只允许经 **Curse Maven**（`curse.maven:`）或 **Modrinth Maven**（`maven.modrinth:`）获取，并由 `build.gradle` 的 `exclusiveContent` 在依赖解析期强制、由 `tools/check_mod_sources.ps1` 静态守门（细则见 `AGENTS.md`「模组依赖添加规则(统一口径)」）。第三方模组 jar **不入库**（`base-mod-*` 目录已在 `.gitignore` 中排除）。
