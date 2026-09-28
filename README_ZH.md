# Astral Dice（星之骰戏）

[English](README.md) | **中文**

**Minecraft multiloader 模组：1.21.1 NeoForge / 1.20.1 Forge / 26.1.2 NeoForge**

> 本仓库为 multiloader 单仓：`neoforge-1.21.1/`（1.21.1 NeoForge 主线）、`forge-1.20.1/`（1.20.1 Forge 移植线）与 `neoforge-26.1.2/`（26.1.2 NeoForge 迁移线）三个自包含子项目，共享同一套玩法内容。详见 `docs/multiloader-layout.md`。

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

## 本版更新（1.3.2）

- **新立牌「机械师」（传奇）**：主动「轨道轰炸」—— 指定一个敌对目标后，按「快捷栏 → 副手 → 背包」的顺序取用卡牌（取至总伤害达上限即停，多余卡牌保留），对目标及其周围 12 格范围进行轰炸；被动「物资补充」定时补牌。
- **初次冒险赠礼**：在本存档开启的**第一个战利品箱子**必定包含 1 个骰子（同存档仅一次，死亡重生与跨维度不重复）。
- **自定义音效**：技能生效、玻璃骰子碎裂、轨道轰炸命中、效果牌使用等一批新音效。
- **平衡性调整与修复**：骰神赐福相关的加伤通道口径（攻击力 / 攻击伤害）、手电筒-强光星光获取条件、卡牌品阶重排、战利品箱概率修正等 —— 完整清单见 [Releases 页面的发布说明](https://github.com/merlin-kitsune/astral_dice/releases)。

## 支持版本 / 环境要求

| 支持 | 子项目 | Minecraft | 加载器 | Java | 当前模组版本 | 帕秋莉手册 |
|---|---|---|---|---|---|---|
| ✅ | `neoforge-1.21.1` | 1.21.1 | NeoForge 21.1.235 | 21 | 1.3.2 | ✅ |
| ✅ | `forge-1.20.1` | 1.20.1 | Forge 47.4.10 | 17 | 1.3.2 | ✅ |
| ✅ | `neoforge-26.1.2` | 26.1.2 | NeoForge 26.1.2.109 | 25 | 1.3.2-beta.1 | ✅ |

- `neoforge-1.21.1` 与 `forge-1.20.1` 是**发布线**（功能对等，两者的 jar 随每个 GitHub Release 发布）。
- `neoforge-26.1.2` 是**迁移线**（2026-09-19 起已纳入主线、与另两线同级同步；2026-09-22 起由客户端实验性支持转为**正式迁移线**）：**不单独打 tag / 发 Release**，但其 jar 作为**第三个附件随发布线的 Release 一并发布**（CI 三线同批构建，见 `.github/workflows/build.yml`）。该线**无** Iron's Spells 'n Spellbooks 联动（上游无 26.1.x 构建）。

| 前置/联动 | 要求 |
|---|---|
| 前置模组（**已内嵌，无需单独安装**） | **StarEngine Lib**（`starengine_lib`）**自 1.3.1 起内嵌于本模组**（当前内嵌 **`1.0.5`**，兼容区间 **`[1.0.5,2.0)`**）：加载器启动时自动载入内嵌副本。⚠️ **请勿**再往 `mods` 里单独放 `starengine_lib-*.jar` —— 加载器按 modId 去重时优先采用那一份，更旧的会盖掉内嵌库 |
| 前置模组 | Curios API（1.20.1 用 Curios 5.x；1.21.1 用 Curios 9+；**26.1.2 用 Curios 15+**，缺失时会在 NeoForge 依赖排序阶段被拒绝）。⚠️ 该前置**由本模组声明**；StarEngine Lib 本身仅在 forge 侧把它作为**编译期**依赖（不进库的 `mods.toml`） |
| 前置模组（仅 1.20.1） | **Mixin Booster ≥ 0.1.3**，**强制**：未安装时游戏会在 Forge 依赖排序阶段直接拒绝启动并提示缺少 `mixinbooster`；装旧版本同样会被拒 |
| 可选联动 | Bountiful、帕秋莉手册 |

---

## 下载

- **发布页**：[GitHub Releases](https://github.com/merlin-kitsune/astral_dice/releases)（每个版本附**三个** jar：发布线两个 + 26.1.2 的 `-beta` jar）
- 支持平台：Minecraft 1.21.1 / NeoForge、1.20.1 / Forge（发布线）；Minecraft 26.1.2 / NeoForge（迁移线，不单独发 Release，jar 随发布线 Release 附带）
- 前置：Curios API（1.20.1 另需 Mixin Booster ≥ 0.1.3）。**StarEngine Lib 已内嵌于本模组，无需单独安装**
- 构建产物：`neoforge-1.21.1/build/libs/astral_dice-<版本>+neoforge_1.21.1.jar`、`forge-1.20.1/build/libs/astral_dice-<版本>+forge_1.20.1.jar`、`neoforge-26.1.2/build/libs/astral_dice-<版本>+neoforge_26.1.2.jar`；GitHub Release 的 tag 使用无后缀的基础版本号（如 `1.3.2`）

## 构建

```bash
./gradlew build                    # 构建全部子项目（三个版本）
./gradlew :neoforge-1.21.1:build   # 仅构建 1.21.1 NeoForge
./gradlew :forge-1.20.1:build      # 仅构建 1.20.1 Forge
./gradlew :neoforge-26.1.2:build   # 仅构建 26.1.2 NeoForge（迁移线）
```

- **模组依赖来源有硬规则**：所有第三方模组（含前置/附属模组）只允许经 **Curse Maven**（`curse.maven:`）或 **Modrinth Maven**（`maven.modrinth:`）获取，并由 `build.gradle` 的 `exclusiveContent` 在依赖解析期强制、由 `tools/check_mod_sources.ps1` 静态守门（细则见 `AGENTS.md`「模组依赖添加规则(统一口径)」）。第三方模组 jar **不入库**（`base-mod-*` 目录已在 `.gitignore` 中排除）。
