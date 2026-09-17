# Astral Dice（星之骰戏）

**Minecraft multiloader 模组：1.21.1 NeoForge / 1.20.1 Forge** | A Minecraft multiloader mod: 1.21.1 NeoForge / 1.20.1 Forge

> 本仓库为 multiloader 单仓：`neoforge-1.21.1/`（1.21.1 NeoForge 主线）与 `forge-1.20.1/`（1.20.1 Forge 移植线）两个自包含子项目，共享同一套玩法内容。详见 `docs/multiloader-layout.md`。

Astral Dice 是一个以「骰子」为核心的生存扩展模组。戴上骰子，每一次近战攻击都会掷出命运之骰，触发「骰神赐福」，进入攻防对垒的骰战玩法。

Astral Dice is a survival expansion mod built around dice. Equip a dice and every melee attack becomes a roll of fate — trigger the **Dice Blessing** and enter a dice-battle system where attack clashes against defense.

---

## 中文介绍

### 核心玩法

- **骰子**：基础 → 黄金 → 钻石 → 下界合金，四阶升级；星级提升解锁更多费用点数与卡牌槽位。
- **战斗牌**：攻击牌与防御牌，插入骰子卡牌栏后，在骰神赐福中提供随机点数加成；每张卡牌拥有独立耐久。
- **效果牌**：王之力、狂暴、岿然不动、对怪激光、轨道炮、活体书页等丰富效果牌，拥有独立的出牌数与冷却周期。
- **立牌**：17 位风格各异的角色立牌，每位都有专属被动与主动技能，例如经商、扫地机、护法、忍者、吸血鬼等。
- **筹码**：大量被动饰品，如拳击手套、速度轮滑、摩托头盔、医疗箱、魔法箭袋、星币锤等，提供攻击、防御、移速、生命、星光等多维加成。
- **资源流派**：治愈点、星光点、标记层数、充能层数四大玩家资源体系，配合立牌与筹码形成多样构筑。

### 特色系统

- 骰神赐福攻防对垒
- 法伤模块：兼容弓箭、三叉戟、魔法与多种法术模组
- 卡牌选择界面：卡牌栏按骰子星级（0★=4 / 1★=6 / 2★=8 / 3★=12 格，攻防各半），实时数值预览
- 事件系统：大侦探、调查员、秘密侦探等立牌联动
- Bountiful 赏金板联动

### 支持版本 / 环境要求

| 支持 | 子项目 | Minecraft | 加载器 | Java | 当前模组版本 | 帕秋莉手册 |
|---|---|---|---|---|---|---|
| ✅ | `neoforge-1.21.1` | 1.21.1 | NeoForge 21.1.235 | 21 | 1.2.1 | ✅ |
| ✅ | `forge-1.20.1` | 1.20.1 | Forge 47.4.10 | 17 | 1.2.1 | ✅ |
| 🧪 | `neoforge-26.1.2` | 26.1.2 | NeoForge 26.1.2.109 | 25 | 1.2.1-beta | ✅ |

- `neoforge-1.21.1` 与 `forge-1.20.1` 是**发布线**（功能对等，随 GitHub Release 发布两个 jar）。
- `neoforge-26.1.2` 是**低优先级移植线**：版本号带 `-beta`、**不发布 Release**（仅随仓库提供构建产物），内容以发布线为准并在发布线完成后迁移，迁移后需通过功能一致性测试。该线**无** Iron's Spells 'n Spellbooks 联动（上游无 26.1.x 构建）。

| 前置/联动 | 要求 |
|---|---|
| 前置模组 | Curios API（1.20.1 用 Curios 5.x；1.21.1 用 Curios 9+；**26.1.2 用 Curios 15+**，缺失时会在 NeoForge 依赖排序阶段被拒绝） |
| 前置模组（仅 1.20.1） | **Mixin Booster ≥ 0.1.3**，**强制**：未安装时游戏会在 Forge 依赖排序阶段直接拒绝启动并提示缺少 `mixinbooster`；装旧版本同样会被拒 |
| 可选联动 | Bountiful、帕秋莉手册 |

---

## English Introduction

### Core Features

- **Dice**: Four tiers — Basic, Golden, Diamond, and Netherite. Star upgrades unlock more cost points and card slots.
- **Battle Cards**: Attack and defense cards are inserted into the dice card inventory. They grant random bonus points during Dice Blessings, and each card has its own durability.
- **Effect Cards**: King's Power, Berserk, Unwavering, Monster Laser, Orbital Strike, Living Page, and many more — each with its own play-count and cooldown cycle.
- **Signs**: 17 unique character signs, each with a passive ability and an active skill, such as Business, Sweeper, Guardian, Ninja, and Vampire.
- **Chips**: A wide variety of passive curios — Boxing Gloves, Speed Skates, Moto Helmet, Medkit, Magic Quiver, Star Coin Hammer, and more — providing attack, defense, movement speed, health, starlight, and other bonuses.
- **Player Resources**: Healing Points, Starlight, Mark stacks and Charge stacks form four player resource systems that work together with signs and chips.

### Highlights

- Dice Blessing attack/defense showdown
- Spell Damage system compatible with bows, crossbows, tridents, magic, and many magic mods
- Card selection GUI with star-based card slots (0★=4 / 1★=6 / 2★=8 / 3★=12, split evenly between attack and defense) and real-time stat previews
- Event system featuring Detective, Investigator, and Secret Detective signs
- Bountiful bounty board integration

### Supported Versions / Requirements

| Support | Subproject | Minecraft | Loader | Java | Current Version | Patchouli |
|---|---|---|---|---|---|---|
| ✅ | `neoforge-1.21.1` | 1.21.1 | NeoForge 21.1.235 | 21 | 1.2.1 | ✅ |
| ✅ | `forge-1.20.1` | 1.20.1 | Forge 47.4.10 | 17 | 1.2.1 | ✅ |
| 🧪 | `neoforge-26.1.2` | 26.1.2 | NeoForge 26.1.2.109 | 25 | 1.2.1-beta | ✅ |

- `neoforge-1.21.1` and `forge-1.20.1` are the **release lines** (feature-parity pair; both jars are published with every GitHub Release).
- `neoforge-26.1.2` is the **low-priority port line**: its version carries `-beta`, it **does not publish Releases** (the jar is only available from the repository), and content is ported from the release lines *after* they are done, followed by a functional-consistency test. This line has **no** Iron's Spells 'n Spellbooks integration (upstream ships no 26.1.x build).

| Dependency | Requirement |
|---|---|
| Required | Curios API (Curios 5.x on 1.20.1, Curios 9+ on 1.21.1, **Curios 15+ on 26.1.2**; a missing or too-old Curios is rejected during NeoForge's dependency sorting) |
| Required (1.20.1 only) | **Mixin Booster ≥ 0.1.3**, **mandatory**: when it is missing, the game refuses to start right at Forge's dependency-sorting stage and reports the missing `mixinbooster`; an outdated version is rejected the same way |
| Optional | Bountiful, Patchouli |

---

## 下载 / Download

- 支持平台：Minecraft 1.21.1 / NeoForge、1.20.1 / Forge（发布线）；Minecraft 26.1.2 / NeoForge（低优先级线，**不发 Release**）
- 前置：Curios API（1.20.1 另需 Mixin Booster ≥ 0.1.3）
- 构建产物：`neoforge-1.21.1/build/libs/astral_dice-<版本>+neoforge_1.21.1.jar`、`forge-1.20.1/build/libs/astral_dice-<版本>+forge_1.20.1.jar`、`neoforge-26.1.2/build/libs/astral_dice-<版本>+neoforge_26.1.2.jar`；GitHub Release 的 tag 使用无后缀的基础版本号（如 `1.1.3`），自动附带发布线的两个 jar

## 构建 / Build

```bash
./gradlew build                    # 构建全部子项目（三个版本）
./gradlew :neoforge-1.21.1:build   # 仅构建 1.21.1 NeoForge
./gradlew :forge-1.20.1:build      # 仅构建 1.20.1 Forge
./gradlew :neoforge-26.1.2:build   # 仅构建 26.1.2 NeoForge（低优先级线）
```

- **模组依赖来源有硬规则**：所有第三方模组（含前置/附属模组）只允许经 **Curse Maven**（`curse.maven:`）或 **Modrinth Maven**（`maven.modrinth:`）获取，并由 `build.gradle` 的 `exclusiveContent` 在依赖解析期强制、由 `tools/check_mod_sources.ps1` 静态守门（细则见 `AGENTS.md`「模组依赖添加规则(统一口径)」）。第三方模组 jar **不入库**（`base-mod-*` 目录已在 `.gitignore` 中排除）。
