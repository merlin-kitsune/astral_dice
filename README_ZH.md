# Astral Dice（星之骰戏）

[English](README.md) | **中文**

**Minecraft multiloader 模组：1.21.1 NeoForge、1.20.1 Forge、26.1.2 NeoForge、1.20.1 Fabric**

> 本仓库是 multiloader 单仓。四个自包含子项目共用同一套玩法内容：`neoforge-1.21.1/`（1.21.1 NeoForge 主线）、`forge-1.20.1/`（1.20.1 Forge 移植线）、`neoforge-26.1.2/`（26.1.2 NeoForge 迁移线）、`fabric-1.20.1/`（1.20.1 Fabric 移植线）。目录结构见 `docs/multiloader-layout.md`。

Astral Dice 是一个以骰子为核心的生存扩展。戴上骰子后，每次近战攻击都会掷出「骰神赐福」，随之进入骰战：你的攻击掷点与目标的防御掷点对拼。

---

## 核心玩法

- **骰子 13 种。** 从基础骰子升到下界之星骰子：黄金 / 玻璃 / 下界岩 → 钻石 / 绿宝石 / 黑曜石 / 诡异 / 紫晶 → 下界合金 / 绯红 / 末影 → 下界之星。星级（0 到 3★）提高费用点数上限与卡牌槽位（0★=4、1★=6、2★=8、3★=12，攻防各半）。
- **战斗牌 12 种。** 攻击牌与防御牌放在骰子的卡牌栏里，在骰神赐福期间提供随机点数，每张牌各自带耐久。
- **效果牌 19 种。** 王之力、狂暴、岿然不动、以毒攻毒、对怪激光、轨道炮、活体书页、命运的指引等，每张牌单独记出牌次数与冷却周期。
- **立牌 28 位。** 每位角色一个被动加一个主动：经商、扫地机、护法、忍者、吸血鬼、风水师、教主、绿洲女王、蛟龙、机械师、调酒师、多萝西、修女、大肥鱼等。
- **筹码 67 枚。** 拳击手套、速度轮滑、摩托头盔、医疗箱、魔法箭袋、星币锤、美工刀、手电筒等被动饰品，提供攻击、防御、移速、生命、星光等加成。
- **玩家资源。** 治愈点、星光、标记层数、充能层数四种，立牌与筹码的行为都依赖这四项。

## 特色系统

- 骰神赐福的攻击与防御对垒。
- 法术伤害，弓箭、弩、三叉戟与各类魔法模组都能吃到加成。
- 选卡界面：卡牌槽按星级给（0★=4、1★=6、2★=8、3★=12，攻防各半），并实时预览数值。
- 立牌挂钩分发：立牌注册自己的钩子，战斗事件处理器里不再堆硬编码分支。
- Bountiful 赏金板联动。
- 四条线的 Jade 都把护甲位换成本模组的防御力。

## 1.5.0 更新内容

本节覆盖 1.5.0 全部预发布（pre）与候选（rc）构建已交付的内容，单个构建的明细见 [CHANGELOG.md](CHANGELOG.md)。

**新增内容**

- **六张新立牌。** 超天酱与地雷女（奇特品质，备注「一心同体」写明两人的队伍联动），以及调酒师、多萝西、修女、大肥鱼。
- **两张新效果牌。** 路障（30 点耐久的可见实体，配一个栅栏高度的碰撞方块，会把仇恨拉到自己身上）与强化拒止（专属牌，100 点耐久，把敌对目标钳制在 8 格光环内并施加缓慢与虚弱）。
- **新筹码。** 手电筒-爆闪、财运签、试剂-深渊、无线输电杆、感测装置。
- **卡牌商店与筹码商店两个方块**，各自可当村民工作站点，村民因此能转职为卡牌交易员与筹码商人，按等级出售卡牌或筹码。
- **事件系统重写**，统一了名称、图标、类型、档位与作用范围模型，加入逐事件计时器，并新增 12 个事件（红温警告、疾行、雷暴将至、取长补短、商店大酬宾、手牌抹杀、天崩地裂、天降之物、天使降临、问题外卖、拥挤通道、战斗爽）。「奇怪的问号」物品与抽奖盒方块可以按需抽取事件。
- **两条新的战斗数值条**：防御力与攻击力，画在原版护甲条所在的那一行上。

**平台与兼容**

- **新增 Fabric 1.20.1 构建**，模组因此四条线齐备且功能对等。
- **四条线的 Jade 都把护甲位换成本模组的防御力**，商店方块另外显示 Jade 冷却倒计时。
- **前置库更名为 StarEngine。** mod id 从带 `_lib` 后缀的旧 id 改成 `starengine`，包名改为 `com.merlinkitsune.starengine`，maven 坐标改为 `starengine-<platform>`。仓库迁到 [merlin-kitsune/StarEngine](https://github.com/merlin-kitsune/StarEngine)。
- **钱包数据在改名后仍然有效。** 各平台都先读新的 `starengine` NBT 键，读不到再回退到旧键 `starengine_lib`；删掉这条回退，每个玩家的余额都会被清零。
- **版本闸门。** `major.minor` 相同的构建可以互通；版本号里带 `SNAPSHOT` 的构建不与任何其它构建互通。

**平衡与规则**

- **卡牌改为交易获得，不再合成。** 22 条卡牌合成配方全部移除，卡牌只从商店与村民交易拿；专属牌和巅峰 / 奇特牌永远不进商店与交易。
- **所有星币发放都走钱包**，包括占星师、秘密侦探、人偶师、看板娘、天降之物，以及大侦探立牌的被动。
- **商店规则调整。** 60 秒一轮，只有归零那一刻才换新一批；每轮一次转账、一次星光兑换；重开界面不再刷新货架。佩戴经商立牌或触发商店大酬宾时按缺口补位，不重建货架。
- **冷却规则。** 经商、枪匠、多萝西、怪力侦探 120 秒，其余 180 秒。蛟龙锁定 60 秒，锁定结束后不再追加冷却。
- **立牌内部整合。** `SignHookDispatcher` 取代了 `DiceCombatEvents` 里的硬编码分支（只剩风水师与教主的钩子仍显式保留）；22 处独立的装备检查合并为 `SignEquipCheck`；属性与效果施加统一走 `SignAttributeUtil` 与 `EffectTimerGuard`。
- **十个立牌附件合并成一个。** `component/SignState` 只保留一个 `sign_state` 附件；十个旧键照旧注册，登录时迁移数据。
- **五个工具类下沉到 StarEngine。** `PartyRelations`、`EliteTargets`、`HoldToSelect`、`SelfTargetable` 与 `EffectTimerGuard`；本模组注入 `TimerSource` 的实现。

**表现与修复**

- 新增五条自定义音效、重写一条，另有 179 张贴图的铁-艾尔登法环粒子库。
- 事件牌在一个服务端计时序列里依次淡入、停留、淡出；大侦探立牌会打开带 15 秒倒计时的事件选择界面。
- 枪械伤害不再被当成远程或魔法伤害；激光与爆炸伤害补进伤害表；活体书页的堆叠已修；「现代化 UI」的稀有度边框改用正确颜色；医疗箱筹码不再在重登或切换维度时回血；手册里两枚立牌的稀有度已改正。
- Forge 侧开发环境需要 `-Dmixin.config=astral_dice.jade.mixins.json`，因为 dev 不读 `mods.toml`。

## 支持版本

| 支持 | 子项目 | Minecraft | 加载器 | Java | 版本 | 帕秋莉手册 |
|---|---|---|---|---|---|---|
| ✅ | `neoforge-1.21.1` | 1.21.1 | NeoForge 21.1.235 | 21 | 1.5.0-pre.5 | ✅ |
| ✅ | `forge-1.20.1` | 1.20.1 | Forge 47.4.10 | 17 | 1.5.0-pre.5 | ✅ |
| ✅ | `neoforge-26.1.2` | 26.1.2 | NeoForge 26.1.2.109 | 25 | 1.5.0-pre.5 | ✅ |
| ✅ | `fabric-1.20.1` | 1.20.1 | Fabric Loader 0.19.5 | 17 | 1.5.0-pre.5 | ✅ |

- `neoforge-1.21.1` 与 `forge-1.20.1` 是发布线：两条线保持功能对等，两个 jar 都随每个 GitHub Release 发布。
- `neoforge-26.1.2` 是迁移线：不单独打 tag，也不单独发 Release，它的 jar 作为第三个附件挂在发布线的 Release 上（CI 会把全部线一起构建，见 `.github/workflows/build.yml`）。上游没有 26.1.x 构建，因此这条线没有 Iron's Spells 'n Spellbooks 联动。
- `fabric-1.20.1` 是 Fabric 移植线（2026-09-29 加入，2026-10-01 并入 `multi-main`）：单独以预发布形式发布，绝不混进发布线的 Release。它有一套自己的前置，并且没有 Curios、Bountiful 与 Iron's Spells 联动。

## 前置依赖

| 依赖 | 要求 |
|---|---|
| 必需，需单独安装 | **StarEngine**（mod id `starengine`，当前 **1.1.0-pre.2**）。**NeoForge 1.21.1** 与 **Forge 1.20.1** 两线**不再内置**该库：请把 `starengine-neoforge-1.21.1-<版本>.jar` / `starengine-forge-1.20.1-<版本>.jar` 与本模组一起放进 `mods`，版本须与本构建编译时所用的一致。缺库会以「Missing or unsupported mandatory dependencies」拒绝启动。（NeoForge 26.1.2 与 Fabric 1.20.1 两线目前仍内置。） |
| 必需 | Curios API：1.20.1 用 5.x，1.21.1 用 9+，26.1.2 用 15+。缺失或版本过旧会在依赖排序阶段被拒绝。这项前置由本模组声明，StarEngine 只在 Forge 侧把它当编译期依赖。 |
| 必需，仅 1.20.1 | Mixin 运行时二选一：**Mixin Booster 0.1.3 或更新**，或者 **Sinytra Connector**。整合包已带 Connector 时无需额外安装，因为 Connector 自带同一套运行时，Mixin Booster 会自动让位。两者都不在时模组拒绝启动，并提示安装方式。旧版 Mixin Booster 会被拒绝。 |
| 必需，仅 fabric-1.20.1 | 另一套前置，见 [Fabric（1.20.1）前置一览](#fabric1201前置一览)。Curios 与 Mixin Booster 那条链在本线不适用。 |
| 可选 | Bountiful、帕秋莉手册。 |
| 可选，仅 fabric-1.20.1 | 帕秋莉手册。Bountiful 没有 1.20.1 Fabric 版本。 |

### Fabric（1.20.1）前置一览

| # | 前置 | 版本 |
|---|---|---|
| 1 | Fabric Loader | 0.19.5+（0.19.x） |
| 2 | Fabric API | 1.20.1 用 0.92.12 |
| 3 | Puzzles Lib（事件桥） | 8.1.33+，与第 4 项一起安装 |
| 4 | Forge Config API Port | 8.0.3，Puzzles Lib 8.x 需要它 |
| 5 | 饰品栏提供者，二选一 | Trinkets 3.7.2+ 或 Accessories 1.0.0-beta.48+ |
| 6 | 可选 | 帕秋莉手册（游戏内手册） |
| 不适用 | 本线用不到 | Curios API、Mixin Booster、Sinytra Connector、Bountiful、Iron's Spells 'n Spellbooks |

- 饰品栏是二选一。Fabric 的 `depends` 只有 AND 语义，所以 Trinkets 与 Accessories 都写在 `recommends` 下，模组自己在启动时检查，只有两者都不在时才拒绝启动。
- Modrinth 与 CurseForge 的商店页把 Trinkets 记为必需、Accessories 记为可选。这只是商店页上的声明，`fabric.mod.json` 仍把两者当推荐，只装 Accessories 的环境照样能启动。

---

## 下载

- **GitHub Releases**：[merlin-kitsune/astral_dice/releases](https://github.com/merlin-kitsune/astral_dice/releases)。每个 Release 带三个 jar：两条发布线加上 26.1.2 的 jar。
- **Fabric 移植线**：以单独的 `fabric-<版本>` 预发布形式发布，标记为 pre-release，不占「最新」，只带它自己那一个 jar。
- **Modrinth**：四个构建发布到主项目（`5xDtrJ8X`）。StarEngine 有独立项目（`2dIXA5wO`）；**NeoForge 1.21.1** 与 **Forge 1.20.1** 不再内置该库，玩家需自行安装那份独立 jar（26.1.2 与 Fabric 两线目前仍内置）。版本号保留 `+<加载器>_<MC版本>` 后缀（例如 `1.5.0-pre.5+neoforge_1.21.1`），发布渠道由版本后缀决定。
- **构建产物**：`astral_dice-<版本>+neoforge_1.21.1.jar`、`+forge_1.20.1.jar`、`+neoforge_26.1.2.jar`、`+fabric_1.20.1.jar`，各自位于对应子项目的 `build/libs/` 下。GitHub Release 的 tag 用不带后缀的基础版本号，Fabric 线则用 `fabric-<版本>` 预发布 tag。

## 构建

```bash
./gradlew build                      # 全部子项目
./gradlew :neoforge-1.21.1:build     # 1.21.1 NeoForge
./gradlew :forge-1.20.1:build        # 1.20.1 Forge
./gradlew :neoforge-26.1.2:build     # 26.1.2 NeoForge
./gradlew :fabric-1.20.1:build       # 1.20.1 Fabric
```

- 第三方模组（含前端模组）只从 **Curse Maven**（`curse.maven:`）或 **Modrinth Maven**（`maven.modrinth:`）获取。`build.gradle` 的 `exclusiveContent` 在依赖解析时强制这一点，`tools/check_mod_sources.ps1` 负责静态检查，细则见 `AGENTS.md`。模组 jar 不入库。
- 库从 mavenLocal 解析。做新版本时先发布：在 StarEngine 仓库里执行 `./gradlew :<platform>:publishToMavenLocal`。

## 许可

- **源代码**：Java 源码、Gradle 脚本、`tools/` 与 `scripts/` 下的工具、以及 `docs/` 下的文档，按 **GNU 通用公共许可证第 3 版（仅第 3 版，`GPL-3.0-only`）**发布，全文见 [LICENSE](LICENSE)。每个已发布 jar 的源码就是本仓库对应 tag 上的那棵树。
- **1.3.7 及以前的版本**：截至并包含 **1.3.7** 的全部发布（四线都算，含 `1.3.7-hotfix` 刷新版与 `1.3.7-alpha` / `1.3.7-beta` 预发布）**继续按 MIT 许可**，该授权不可撤回，MIT 原文见对应发布 tag（如 `1.3.7`）的 `LICENSE`；本工作树不再单独保留 MIT 文本。
- **游戏内资产**：本模组随包发布的全部贴图、方块/物品/实体模型、动画、粒子贴图、GUI 贴图、字体、音效与音乐，以及帕秋莉手册的正文与插图（即四个子项目 `src/main/resources/assets/astral_dice/` 下**除 `lang/` 以外**的内容，连同 `models/`、`images/`、`sound/` 三处素材），按 **CC BY-NC-SA 4.0**（[知识共享 署名—非商业性使用—相同方式共享 4.0 国际](https://creativecommons.org/licenses/by-nc-sa/4.0/deed.zh-hans)）授权：可自由分享与改编用于非商业用途，但必须署名 *Astral Dice* / MerlinKitsune，并以相同方式共享你的演绎作品。**上述 GPL 不适用于这些文件。**
- **数据与语言文件**：配方、战利品、标签、世界生成的 JSON 与 `lang/*.json` 随代码按 `GPL-3.0-only` 授权。
- **商业用途**：付费整合包、付费服务器、付费转载或转售资产需先取得作者授权；免费整合包、免费视频与直播无需授权，欢迎使用。
- **例外**：属于第三方的素材保留其原许可，见下文「第三方资源」。

## 第三方资源

- **铁-艾尔登法环像素粒子库**：179 张 shuimo0413 手工绘制的 32x32 粒子贴图（[仓库](https://github.com/shuimo0413/Iron-Elden-Ring-Particle-library)，MIT 许可）。四条线都在 `assets/astral_dice/textures/particle/ier/` 下随包发布，并由生成的 `assets/astral_dice/particles/particle_library.json` 逐一列出。许可全文与归属声明见 [docs/third-party/Iron-Elden-Ring-Particle-library-LICENSE.txt](docs/third-party/Iron-Elden-Ring-Particle-library-LICENSE.txt)。
- 游戏内这些贴图以粒子类型 `astral_dice:particle_library` 暴露，选项为 `variant`、`scale`、`rgb`：
  - 1.21.1 与 26.1.2：`/particle astral_dice:particle_library{variant:12,scale:1.5,rgb:16755200}`
  - 1.20.1 用位置参数，三项都必填：`/particle astral_dice:particle_library 12 1.5 16755200`
- 增删贴图后用 `python tools/gen_particle_library.py` 重新生成接线；加 `--check` 只校验不写盘。新特效应使用这些贴图，不要临时另造或退回原版粒子。
