# CurseForge 项目说明（Astral Dice · projectId 1662159）

> 用途：CurseForge 项目页「Description」全文（**含 Fabric 端前置**）。
> 现状：CF 页面被 Cloudflare 拦截（403），**无法读取现有说明** ⇒ 本文件是**可直接整段替换**的新版全文。
> 发布方式：CF 项目页 → Edit project → Description 编辑器 → 全选删除 → 粘贴本文（英文用 EN 段，中文用 ZH 段；两段择一，别同时粘）。
> 若编辑器是富文本（WYSIWYG）：先粘纯文本，再按本文的 `##` / `###` 用工具栏设为标题、`**…**` 设为加粗；本仓库不提供 HTML 版，需要可另出。

---

## EN — paste this into the CurseForge description

**Astral Dice — a dice-driven survival expansion for Minecraft (NeoForge / Forge / Fabric).**

Equip a dice and every melee attack becomes a roll of fate: trigger the **Dice Blessing** and enter a dice-battle system where your attack clashed against the enemy's defence. Four self-contained builds ship from one codebase — 1.21.1 NeoForge, 1.20.1 Forge, 26.1.2 NeoForge and 1.20.1 Fabric.

### Core features

- **Dice — 13 kinds**: upgrade step by step from the Basic Dice up to the **Nether Star Dice** (Golden / Glass / Netherrack -> Diamond / Emerald / Obsidian / Weird / Amethyst -> Netherite / Crimson / Ender -> Nether Star). Star upgrades (0-3) unlock more cost points and card slots (0=4 / 1=6 / 2=8 / 3=12, split evenly between attack and defence).
- **Battle Cards**: attack and defence cards go into the dice card inventory, grant random bonus points during a Dice Blessing and each carry their own durability.
- **Effect Cards**: King's Power, Berserk, Unwavering, Fight Poison with Poison, Monster Laser, Orbital Strike, Living Page, Fate's Guidance and many more — each with its own play-count and cooldown cycle.
- **Signs — 24 characters**: each with a unique passive and an active skill (Business, Sweeper, Guardian, Ninja, Vampire, Fengshui Master, Cult Leader, Oasis Queen, Dragon, Mechanic and more).
- **Chips — 61 pieces**: passive curios such as Boxing Gloves, Speed Skates, Moto Helmet, Medkit, Magic Quiver, Star Coin Hammer, Cutter and Flashlight.
- **Player resources**: Healing Points, Starlight, Mark stacks and Charge stacks — four resource systems that interact with signs and chips.

### Supported versions

| Build | Minecraft | Loader | Java | Current version |
|---|---|---|---|---|
| `astral_dice-<version>+neoforge_1.21.1.jar` | 1.21.1 | NeoForge 21.1.235+ | 21 | **1.3.6** |
| `astral_dice-<version>+forge_1.20.1.jar` | 1.20.1 | Forge 47.4.10+ | 17 | **1.3.6** |
| `astral_dice-<version>+neoforge_26.1.2.jar` | 26.1.2 | NeoForge 26.1.2.109+ | 25 | **1.3.6-beta.1** |
| `astral_dice-<version>+fabric_1.20.1.jar` | 1.20.1 | Fabric Loader 0.19.5+ | 17 | **1.3.6-alpha.1** |

The 1.21.1 / 1.20.1 builds are the release lines. The 26.1.2 build is the migration line (beta channel). The 1.20.1 **Fabric** build is the port line (alpha channel) and uses a **different prerequisite set** — see below.

### Required dependencies

Installed automatically by the launcher from the dependency relations on each file (the CurseForge app and most launchers resolve them for you):

- **Curios API** — on the three NeoForge / Forge builds. Curios 9+ on 1.21.1, Curios 5.x on 1.20.1 Forge, **Curios 15+** on 26.1.2.
- **Mixin runtime (1.20.1 Forge only)** — **Mixin Booster 0.1.3+** *or* **Sinytra Connector**, either one. If your pack already ships Sinytra Connector, install nothing extra.
- **Fabric (1.20.1) build** — Fabric API + **Puzzles Lib 8.1.33+** (plus its own prerequisite **Forge Config API Port 8.0.3**) + an accessory provider, **either Trinkets 3.7.2+ or Accessories 1.0.0-beta.48+**. Curios and the Mixin runtime do **not** apply to this build. *(CurseForge lists Accessories as this build's optional dependency; Trinkets is available on Modrinth.)*

### Bundled — do NOT install separately

**StarEngine Lib** is **bundled inside this mod** (embedded 1.0.7 on the 1.21.1 / 1.20.1 / 26.1.2 builds, 1.0.6-alpha.2 on the Fabric build) — the loader picks the embedded copy up at startup.

**Do not drop a standalone `starengine_lib-*.jar` into your `mods` folder.** The loader de-duplicates by mod id and prefers the loose file, so an older copy would shadow the bundled one. (The library's own repository publishes source only — there is no jar to download there.)

### Optional

- **Patchouli** — the in-game guide book.
- **Bountiful** — bounty board integration (**NeoForge 1.21.1 / 1.20.1 Forge / 26.1.2 only**; there is no 1.20.1 Fabric build of Bountiful).

### Installation

1. Install the loader for the build you picked (NeoForge / Forge / Fabric) and the mods listed above.
2. Drop the matching `astral_dice-*.jar` into your `mods` folder — pick the file whose loader **and** Minecraft version match your instance.
3. Launch. If a required dependency is missing or too old, the mod refuses to start and tells you exactly what to install.

### Links

- **Source & issue tracker**: <https://github.com/merlin-kitsune/astral_dice>
- **Releases** (changelogs, all builds): <https://github.com/merlin-kitsune/astral_dice/releases>
- **Modrinth**: <https://modrinth.com/mod/astral-dice>

Suggested one-line **Summary** for the project header:

> Dice-driven survival expansion: 13 dice, battle & effect cards, 24 signs and 61 chips — with a full dice-battle showdown on every attack.

---

## ZH — 粘贴到 CurseForge 说明（中文版）

**星之骰戏 —— 以骰子为核心的 Minecraft 生存扩展（NeoForge / Forge / Fabric）。**

装备骰子后，每次近战攻击都是一次命运投掷：触发**骰神赐福**即进入「攻击对上防御」的骰战系统。同一套代码发布四个自包含构建 —— 1.21.1 NeoForge、1.20.1 Forge、26.1.2 NeoForge 与 1.20.1 Fabric。

### 核心内容

- **骰子 · 13 种**：从基础骰子一路升到**下界之星骰子**（黄金 / 玻璃 / 下界岩 → 钻石 / 绿宝石 / 黑曜石 / 诡异 / 紫晶 → 下界合金 / 绯红 / 末影 → 下界之星）。升星（0~3 星）解锁更多消耗点数与卡牌栏（0★=4 / 1★=6 / 2★=8 / 3★=12，攻防各半）。
- **战斗牌**：攻防牌插入骰子的卡牌栏，在骰神赐福期间提供随机加成点数，每张牌各有耐久。
- **效果牌**：王之力、狂暴、岿然不动、以毒攻毒、对怪激光、轨道炮、活体书页、命运的指引等，各有出牌数与冷却周期。
- **立牌 · 24 位**：每位都有独特的被动与主动技能（上班族、扫地机、护法、忍者、吸血鬼、风水师、教主、绿洲女王、蛟龙、机械师……）。
- **筹码 · 61 枚**：拳套、滑轮鞋、摩托头盔、医疗箱、魔法箭袋、星币锤、美工刀、手电筒等被动饰品。
- **玩家资源**：治愈点、星光、标记层数、充能层数 —— 四套与立牌 / 筹码联动的资源体系。

### 支持版本

| 构建 | Minecraft | 加载器 | Java | 当前版本 |
|---|---|---|---|---|
| `astral_dice-<版本>+neoforge_1.21.1.jar` | 1.21.1 | NeoForge 21.1.235+ | 21 | **1.3.6** |
| `astral_dice-<版本>+forge_1.20.1.jar` | 1.20.1 | Forge 47.4.10+ | 17 | **1.3.6** |
| `astral_dice-<版本>+neoforge_26.1.2.jar` | 26.1.2 | NeoForge 26.1.2.109+ | 25 | **1.3.6-beta.1** |
| `astral_dice-<版本>+fabric_1.20.1.jar` | 1.20.1 | Fabric Loader 0.19.5+ | 17 | **1.3.6-alpha.1** |

1.21.1 / 1.20.1 两个是**发布线**；26.1.2 是**迁移线**（beta 渠道）；1.20.1 **Fabric** 是**移植线**（alpha 渠道），用的**前置是另一套**（见下）。

### 必需前置

启动器会按各文件上的依赖关系**自动安装**（CurseForge 客户端与多数启动器都会解析）：

- **Curios API** —— 三条 NeoForge / Forge 线上必需：1.21.1 用 Curios 9+、1.20.1 Forge 用 Curios 5.x、**26.1.2 用 Curios 15+**。
- **Mixin 运行时（仅 1.20.1 Forge）** —— **Mixin Booster 0.1.3+** **或** **Sinytra Connector**，二选一。整合包自带 Sinytra Connector 时无需再装。
- **Fabric（1.20.1）构建** —— Fabric API + **Puzzles Lib 8.1.33+**（连同它自己的前置 **Forge Config API Port 8.0.3**）+ 饰品栏提供者 **Trinkets 3.7.2+** **或** **Accessories 1.0.0-beta.48+**（二选一）。本线**不适用** Curios 与 Mixin 运行时。*（CurseForge 上本线把 Accessories 登记为可选依赖；Trinkets 见 Modrinth。）*

### 已内嵌 · 请勿单独安装

**StarEngine Lib 已内嵌进本模组**（1.21.1 / 1.20.1 / 26.1.2 内嵌 1.0.7，Fabric 线内嵌 1.0.6-alpha.2），加载器启动时会自动载入内嵌副本。

**请勿**再往 `mods` 里单独放 `starengine_lib-*.jar` —— 加载器按 modId 去重时优先采用散装文件，更旧的副本会盖掉内嵌库。（库仓本身只提供源码，没有 jar 可下载。）

### 可选

- **帕秋莉手册（Patchouli）** —— 游戏内手册。
- **Bountiful（赏金板）** —— **仅 1.21.1 NeoForge / 1.20.1 Forge / 26.1.2**；Bountiful 无 1.20.1 Fabric 版。

### 安装

1. 按你选的构建装好加载器（NeoForge / Forge / Fabric）与上面的前置。
2. 把对应的 `astral_dice-*.jar` 放进 `mods` 文件夹 —— 请选**加载器与 Minecraft 版本都对得上**的那个文件。
3. 启动。若必需前置缺失或版本过旧，模组会拒绝启动并提示要装什么。

### 链接

- **源码与问题反馈**：<https://github.com/merlin-kitsune/astral_dice>
- **发布页**（更新日志与全部构建）：<https://github.com/merlin-kitsune/astral_dice/releases>
- **Modrinth**：<https://modrinth.com/mod/astral-dice>

项目标题下方建议填的**一句话简介（Summary）**：

> 以骰子为核心的生存扩展：13 种骰子、战斗牌与效果牌、24 位立牌、61 枚筹码 —— 每次攻击都触发一场骰战对决。
