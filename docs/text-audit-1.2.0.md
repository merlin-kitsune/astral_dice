# 立牌 / 筹码 / 卡牌 文案审计报告（1.2.0）

审计日期：2026-09-12
审计范围：`neoforge-1.21.1` + `forge-1.20.1` 双版本 `assets/astral_dice/lang/{zh_cn,en_us}.json`
审计对象：178 个 `tooltip.astral_dice.*`（立牌 50 / 筹码 65 / 卡牌 30 / 材料 6 / 其它 27）+ 215 条 `astral_dice.guide.*`（帕秋莉手册）
方法：文案与源码逐条比对（`item/sign/*`、`item/chip/*`、`item/card/*`、`combat/DiceCombatModifiers`、`combat/CardRegistry`、`event/ModTooltipHandler`、`event/ChipDamageHandler`），并做术语/标点/引号的机械扫描。

## 0. 前置结论（健康项）

| 检查项 | 结果 |
|---|---|
| 双版本 `zh_cn` 差异键 | **0** |
| 双版本 `en_us` 差异键 | **0** |
| 仅 neo 引用 / 仅 forge 引用的 key | **0 / 0** |
| 三档筹码配方校验 | 59 \| 一致 59 \| 不一致 0 |
| **完全未被 Java 引用的死键** | **1**（`tooltip.astral_dice.sign.lulu_healing`，见 P2-12） |

即：**双版本对等性无问题**，以下全部为文案本身的质量问题，双版本同步修复即可。

---

## 1. 严重度总览（跨类型排序）

| # | 严重度 | 类型 | 对象 | 问题摘要 |
|---|---|---|---|---|
| P0-1 | 🔴 高 | 立牌 | 吸血鬼立牌 被动 | 阈值写「低于」，实际判据含边界（`<=`），边界不触发是错的 |
| P0-2 | 🔴 高 | 筹码 | 磨刀石 | 保命阈值差 1 点，且未说明「不会被单次伤害击倒」 |
| P0-3 | 🔴 高 | 立牌 | 吸血鬼立牌 主动 | 「被视为满血和半血」为过度声明，实际只影响 2 个判定点 |
| P0-4 | 🟠 中 | 筹码 | 大碗炖肉 | 「所有友方目标 +1 治愈」包含宠物，但宠物只回血不给治愈点 |
| P1-1 | 🟠 中 | 立牌 | 秘密侦探 / 占星师 / 枪匠 主动 | 三个「需指定目标」立牌均漏掉「30 秒等待窗口 + 脱靶不进冷却」 |
| P1-2 | 🟠 中 | 立牌 | 枪匠 主动 | 缺「普通敌对目标」限定；句首标点错位（`§7 ：`） |
| P1-3 | 🟠 中 | 立牌 | 占星师 主动 | 缺「须符合骰神赐福触发条件」前置限制 |
| P1-4 | 🟠 中 | 筹码 | 电流核心 | tooltip 漏「完成后需再次按键」「充能不足不生效」两条规则 |
| P1-5 | 🟠 中 | 术语 | 充能 | 量词「点 / 层」混用，且同一物品 tooltip 与手册不同 |
| P1-6 | 🟠 中 | 术语 | 标记 | 量词「点 / 层」混用（`MarkManager` 内部一律「层」） |
| P1-7 | 🟠 中 | 术语 | 攻击/防御牌 | 同一卡牌有三种写法：`攻击(特大)` / `攻击-特大` / `攻击牌（特大）` |
| P1-8 | 🟠 中 | 术语 | 剑气（英文） | `Sword Qi` 与 `Sword Energy` 混用 |
| P1-9 | 🟠 中 | 术语 | 最大生命值 | `最大生命上限` / `最大生命值` 混用 |
| P1-10 | 🟠 中 | 术语 | 卡牌 | `随机手牌` 与全模块「卡牌」不符 |
| P1-11 | 🟠 中 | 术语 | 生命值 | `血量` 与「生命值」混用（3 处） |
| P1-12 | 🟠 中 | 术语 | 治愈 | 同一机制两种描述：`big_bowl_stew` vs `pandaman` |
| P1-13 | 🟠 中 | 术语 | 伤害效果牌 | `伤害效果牌` vs `伤害类效果牌` |
| P1-14 | 🟠 中 | 遗漏 | 中文 tooltip | 7 处中文比英文/手册少了限定词与限制条件 |
| P1-15 | 🟠 中 | 遗漏 | 手册 | 手册漏掉战斗牌「取 2 次最大值」机制 |
| P1-16 | 🟠 中 | 遗漏 | 手册 | `cursed_sword` 手册写「最大有上限」未给数值 |
| P2-1 | 🟡 低 | 格式 | 引号 | 直引号 `"` / 弯引号 `“”` / 角引号 `「」` 三制并存，且与手册不一致 |
| P2-2 | 🟡 低 | 格式 | 冒号 | 半角 `: ` 与全角 `：` 混用，资源计数器家族内部也不统一 |
| P2-3 | 🟡 低 | 格式 | 逗号 | 2 个筹码 + 2 张卡牌用半角 `,` |
| P2-4 | 🟡 低 | 格式 | 尾随空格 | `padman_passive` 行末多余空格 |
| P2-5 | 🟡 低 | 格式 | 空格 | `cutter` / `cutter_blade` 的 `%% §7` 多一个空格 |
| P2-6 | 🟡 低 | 格式 | 空格 | 2 处状态效果描述缺空格（`持续§9`、`最多§e3§7`） |
| P2-7 | 🟡 低 | 格式 | 空格 | 「秒」前空格 2 种写法 |
| P2-8 | 🟡 低 | 格式 | 增益行 | 6 个 `%s` 增益行格式互不相同，1 处缺染色与 `+` |
| P2-9 | 🟡 低 | 格式 | 阈值措辞 | `以上` / `不少于` / `不低于` / `不超过` / `或更低` 五种说法 |
| P2-10 | 🟡 低 | 可读性 | 复仇之戟 | 两层列表结构混乱，第二组标题与第一组列表同级 |
| P2-11 | 🟡 低 | 格式 | 枪匠 被动 | 句间无标点，两句话直接拼接 |
| P2-12 | 🟡 低 | 死键 | 史莱姆立牌 | `sign.lulu_healing` 无任何引用，已被 `healing_points` 取代 |
| P2-13 | 🟡 低 | 命名 | 筹码阶级 | 同一「低→中→高」阶梯有 6 种命名体系 |
| P2-14 | ✅ 已裁定 | 错别字 | 标记喷罐 | 「喷灌」为错别字,已更正为「标记喷罐」(2026-09-15 用户裁定,原建议「喷涂/喷雾器」未采纳) |
| P2-15 | 🟡 低 | 架构 | 效果牌描述 | 3 条路径来源（`card.*` / `effect.*.description` / 手册），2 处染色与信息缺失 |
| P2-16 | 🟡 低 | 格式 | 升星提示 | 全模块唯一使用 `§r` 的文案，导致 `→`/`需` 变白 |
| P2-17 | 🟡 低 | 可读性 | 八面骰 | 「若本次骰点刚好为 8 时」多一个「时」 |

---

## 2. P0 —— 描述与实际效果不符

### P0-1 吸血鬼立牌被动：阈值符号写错（含边界写成不含边界）

| 项 | 内容 |
|---|---|
| 原文位置 | `tooltip.astral_dice.sign.papara_passive`（zh_cn.json **L585**）<br>`astral_dice.guide.entry.papara_sign.2` |
| 原文 | 「生命值低于最大生命值一半时，攻击力/防御力 +3」 |
| 源码 | `item/sign/PaparaSignItem.java:44` → `getHealth() <= getMaxHealth() / 2.0f`<br>`combat/DiceCombatModifiers.java:256` → 同判据 |
| 问题类型 | **描述与实际不符**（边界条件） |
| 说明 | `低于` = 严格小于。实际判据 `<=` 含边界：生命值**恰好等于** 50%（如 20 血上限的玩家在 10 血）时**会**触发加成，文案却说不会。<br>**佐证**：同一个 key 的英文是 `At or below half max HP`（含边界、正确）→ 只有中文写错，中英已互相矛盾。 |
| 建议 | 统一为「**生命值为 50% 或更低时**，攻击力/防御力 +3」，与本次刚修订的肾上腺素 / 磨刀石措辞对齐（`adrenaline_*`、`whetstone` 已用此表述）。手册 `papara_sign.2` 同步。 |

> 同类边界缺陷此前已在肾上腺素上修过一次（`isLowHp` 由 `<` 改 `<=`）。papara 是同一类问题的漏网条目，建议顺手做一次全量阈值符号复核（见第 5 节检查清单）。

### P0-2 磨刀石：保命阈值差 1 点，且未说明「不会被单次伤害击倒」

| 项 | 内容 |
|---|---|
| 原文位置 | `tooltip.astral_dice.chip.whetstone`（zh_cn.json **L508**）第 2 行<br>`astral_dice.guide.entry.whetstone_chip.1` |
| 原文 | 「生命值大于 1 点时：受到的伤害不超过剩余生命值」 |
| 源码 | `item/chip/WhetstoneChipItem.java:62-66`<br>`float health = player.getHealth(); if (health > 1.0F) { float cap = health - 1.0F; if (reduced > cap) reduced = cap; }` |
| 问题类型 | **描述与实际不符**（数值精度 + 语义缺失） |
| 说明 | 实现是「伤害至多扣到**剩 1 点**生命值」= 不可能被单次伤害击倒。文案说「不超过剩余生命值」：若伤害**恰等于**剩余生命值，按文案应当放行（没有「超过」），但实际会被拦截到 `health - 1`。**低估了保命强度 1 点**，且与同文件 javadoc（`WhetstoneChipItem.java:51` 写「不超过(剩余生命值 - 1)」）自相矛盾。 |
| 建议 | 改为「生命值大于 1 点时：**单次所受伤害至多使生命值降至 1 点（不会被单次伤害击倒）**」。 |

### P0-3 吸血鬼立牌主动：「被视为满血和半血」是过度声明

| 项 | 内容 |
|---|---|
| 原文位置 | `tooltip.astral_dice.sign.papara_active`（zh_cn.json **L584**）第 3 行<br>`astral_dice.guide.entry.papara_sign.1` |
| 原文 | 「无论玩家当前血量多少，都将被视为满血和半血状态」 |
| 源码 | `PAPARA_BITE` 全模块仅被 3 处读取：<br>① `combat/DiceCombatModifiers.java:176`（美工刀 60% 门槛）<br>② `event/PlayerTickEvents.java:156`（同上）<br>③ `item/sign/PaparaSignItem.java:44`（自身被动 50% 门槛）<br>**真正的满血判定 `item/chip/CandyChipItem.java:33`（`getHealth() >= getMaxHealth()`）不读该效果。** |
| 问题类型 | **描述与实际不符**（过度声明）+ 表述歧义 |
| 说明 | ① 「被视为**满血**」不准确：实际只满足「≥60%」这个门槛，且仅被美工刀一类筹码采纳；<br>② 对可口糖果的「生命值已满时」判定**无效**——玩家会误以为吃满血相关收益；<br>③ 「和半血状态」也是靠「或」逻辑并列，不是同时成立，原文写作「满血和半血」容易被读成「同时满足两者」。 |
| 建议 | 改为「**同时视为处于「生命值 ≥60%」与「生命值 ≤50%」两种状态（仅用于美工刀与本立牌被动）**」，或至少删去「满血」一词、写明适用对象。 |

### P0-4 大碗炖肉：「所有友方目标」含宠物，但宠物拿不到治愈点

| 项 | 内容 |
|---|---|
| 原文位置 | `tooltip.astral_dice.chip.big_bowl_stew`（zh_cn.json **L457**）<br>`astral_dice.guide.entry.big_bowl_stew_chip.1` |
| 原文 | 「骰神赐福效果结束后，对 16 格范围内所有友方目标增加 1 点治愈并恢复 2 点生命值」 |
| 源码 | `item/chip/BigBowlStewChipItem.java:57-71`<br>玩家：`HealingManager.add(sp, 1)` + `sp.heal(2f)`<br>非玩家友方（驯服宠物 / 可骑乘）：**仅** `entity.heal(2f)`<br>源码注释：「非玩家友方…仅回血（**治愈点数为玩家级资源**）」 |
| 问题类型 | 描述与实际不符（对象范围） |
| 建议 | 改为「…使 16 格内所有**友方玩家**增加 1 点治愈并恢复 2 点生命值，**友方生物**恢复 2 点生命值」。 |

---

## 3. P1 —— 信息缺失 / 术语不一致

### P1-1 三个「需指定目标」的立牌主动都漏掉了等待窗口机制

| 项 | 内容 |
|---|---|
| 原文位置 | `sign.bonnie_active`（L548）、`sign.haiqing_active`（L556）、`sign.moses_active`（L578） |
| 现状 | 三者都写「下次攻击的第一个目标将被施加…」 |
| 源码 | `component/GameplayConstants.java:48` → `SKILL_WAIT_SECONDS = 30`<br>`item/sign/{Bonnie,Haiqing,Moses}SignItem.handleUse()` → 按键后写 `SignReadyExpire = now + 30s`<br>`combat/DiceCombatEvents.java:217-259` → 等待期内首次命中才施加并**此时**才 `setSignActiveCooldownEnd(...)`；超时（`MosesSignItem.java:52-58`）自动取消 |
| 问题类型 | 信息缺失（关键机制未进 tooltip） |
| 说明 | 手册 `moses_sign.1` 有完整描述（「激活后 30 秒内攻击普通敌对目标…**未攻击到目标不会进入冷却**」），**物品栏 tooltip 完全没有**。玩家在物品栏看到的是「下次攻击」，无法预判 30 秒超时与脱靶不耗冷却。 |
| 建议 | 三处 tooltip 统一补一句：`（激活后 30 秒内首次命中目标时施加；未命中则不消耗冷却）`。 |

### P1-2 枪匠主动：缺「普通敌对目标」限定 + 标点错位

- 位置：`sign.moses_active`（L578）
- 原文：`…将被施加 §9破绽 (2:00)§7 ：目标骰点只能为 §e0§7，…`
- 问题：① `§7 ：` 在色码复位后多一个半角空格再接全角冒号；② 源码只对**普通敌对目标**（`DiceCombatEvents` 判定 + `MosesSignItem.applyBroken`）生效，文案未限定。
- 建议：删除空格，补「普通敌对」限定。

### P1-3 占星师主动：缺触发前置条件

- 位置：`sign.haiqing_active`（L556）
- 源码注释 `combat/DiceCombatEvents.java:213`：「占星师立牌主动：对本次攻击的第一个目标施加"虚弱印记"5:00（**须符合骰神赐福触发条件**）」。
- tooltip 未提该限制；手册 `haiqing_sign.1` 也没提。
- 建议：补「（须满足骰神赐福触发条件）」。

### P1-4 电流核心：tooltip 漏两条关键规则

| 项 | 内容 |
|---|---|
| 原文位置 | `tooltip.astral_dice.chip.current_core`；对照 `astral_dice.guide.entry.current_core_chip.2` |
| tooltip | 「主动技能冷却中按下主动技能键时：按剩余冷却时长占比消耗充能，并立即使主动技能冷却完成」 |
| 手册多出 | 「（**完成后需再次按键才释放技能**）」、「**充能不足则不生效**」 |
| 问题类型 | 信息缺失（同一物品 tooltip 与手册内容不对等） |
| 建议 | tooltip 补这两句；或把两者收敛到同一措辞。 |

### P1-5 术语：充能的量词「点 / 层」混用

| 写法 | 出现位置 |
|---|---|
| **点充能** | `chip.airbag`、`chip.current_core`、`chip.electric_glove`、`chip.primordial_core`、`chip.railgun`<br>手册：`airbag_chip.1`、`current_core_chip.1` |
| **层充能** | `chip.electric_sword`<br>手册：`advanced_peripherals_chip.1`（充能层数）、`electric_glove_chip.1`、`electric_sword_chip.1`、`primordial_core_chip.1`、`railgun_chip.1` |
| **充能层数** | `chip.advanced_peripherals`、手册 `category.chips_charge.desc` |

**同一物品两侧不一致**：`electric_glove`（tooltip「点」/ 手册「层」）、`primordial_core`（同）、`railgun`（同）。

另：`赋能` 一律用「层」（`chip.primordial_core`、手册 `primordial_core_chip.1`）——**这是对的**，因此「层」应保留给「赋能」，「充能」统一为「点」。代码侧 `ChargeManager` 用 `getStacks()/consume(n)`，本质是计数，用「点」更贴合。

**建议**：全量把 `充能` 的量词改为「点」；`advanced_peripherals` 的「充能层数不低于 4」→「充能不少于 4 点」。

### P1-6 术语：标记的量词「点 / 层」混用

| 写法 | 出现位置 |
|---|---|
| **点标记** | `chip.scope`（L502）、`chip.eagle_scope`（L471） |
| **层标记** | `chip.target`、`chip.hand_fan_big`、`chip.hand_fan_small`、`chip.marker_sprayer`、`chip.magic_quiver`、`card.living_page`<br>手册：`target_chip.1`、`living_page.2` |

代码 `item/MarkManager.java:26-40` `apply()` 为 `amplifier + 1`，全类注释、`getLevel()`、`EffectTimerGuard` 一律用「层数」。

**建议**：`scope` / `eagle_scope` 两处「1 点标记」→「1 层标记」。

### P1-7 术语：同一张卡牌有三种写法

| 来源 | 写法 |
|---|---|
| 物品显示名 `item.astral_dice.attack_card_epic` | `攻击(特大)`（**半角圆括号**） |
| `sign.fanny_active`（L551） | `获得一张：攻击-特大`（**连字符**） |
| 手册 `astral_dice.guide.entry.attack_card_epic.1` | `攻击牌（特大）`（**全角括号 + 「牌」字**） |

防御牌同理（`防御(中)` / `防御牌（中）`）。

**建议**：统一为 `攻击牌（特大）`；至少统一分隔符与是否带「牌」字。改动仅涉及显示名与文案，`item.astral_dice.*` 键与注册名不动 → 无存档风险。

### P1-8 术语（英文）：剑气被译成两个词

| key | 英文 |
|---|---|
| `tooltip.astral_dice.sign.misaki_death_note` | `All **Sword Qi** stacks are lost on death` |
| `tooltip.astral_dice.sign.misaki_passive` | `Gain 1 **Sword Energy** on Dice Blessing trigger` |
| `tooltip.astral_dice.sign.misaki_stacks` | `Current **Sword Energy**` |

**建议**：统一为 `Sword Qi`（`Sword Qi` 更贴近原名，且 `death_note` 用的是它）。

### P1-9 术语：最大生命上限 vs 最大生命值

- `sign.pandaman_passive`（L605）：「获得 §e2§7 点**最大生命上限**（不超过 §e100§7 点，卸下清除）」
- 其余全部用「最大生命值」：`chip.sandwich_high/low/medium`、`card.hamburger`、`card.luxury_feast`
- 源码注释内部也不一致：`PandamanSignItem.java:39`「最大生命上限」/ `:148`「最大生命值上限」/ `:153`「最大生命值上限」
- **建议**：统一「最大生命值」。

### P1-10 术语：随机手牌

- `chip.smart_watch`（L503）：「每击杀 §e1§7 个敌对目标获得一张随机**手牌**」
- 全模块其余一律「卡牌」；手册 `smart_watch_chip.1` 也用「卡牌」。
- 另：「每击杀 1 个」啰嗦，可作「每击杀一个」。
- **建议**：改「随机卡牌」。

### P1-11 术语：血量 vs 生命值

| 位置 | 原文片段 |
|---|---|
| `sign.fen_active`（L553） | 「恢复 §e6§7 点**血量**」 |
| `sign.papara_active`（L584） | 「无论玩家当前**血量**多少」 |
| `card.luxury_feast`（L443） | 「各恢复自身最大生命值 §e30%%§7 的**血量**」 |

其余 7 处一律「生命值」（`chip.adrenaline_*`、`chip.whetstone`、`sign.papara_passive`、`chip.sandwich_*`…）。另 `sign.bonnie_passive`、`chip.cursed_sword` 用「不少于 20 **血**」的简写。

**建议**：3 处「血量」→「生命值」；`luxury_feast` 的「的血量」属冗余，可直接删。简写「20 血」建议改「20 点生命值」。

### P1-12 术语：同一机制两种描述（大碗炖肉 vs 肉弹战车）

| 位置 | 原文 | 代码语义 |
|---|---|---|
| `chip.big_bowl_stew`（L457） | 「增加 §e1§7 点治愈并恢复 §e2§7 点生命值」 | `HealingManager.add(1)` + `heal(2f)` |
| `sign.pandaman_passive`（L605） | 「获得 §e2§7 点**治疗**和 §e1§7 点治愈」 | `friendly.heal(2f)` + `HealingManager.add(friendly, 1)` |

**完全相同的机制，措辞与语序都不同**，且 `pandaman` 用了非标准的「点治疗」。

**建议**：两处统一为「**恢复 2 点生命值、获得 1 点治愈**」。

### P1-13 术语：伤害效果牌 vs 伤害类效果牌

| 写法 | 位置 |
|---|---|
| `伤害效果牌` | `chip.bookmark`（L458）、`chip.ninja_star`（L486）、`chip.electric_glove` |
| `伤害类效果牌` | `sign.komachi_passive`、手册 `komachi_sign.2`、`special_effects.*` |

**建议**：统一「伤害类效果牌」。

### P1-14 中文 tooltip 比英文 / 手册少了限定词与限制条件

共 7 处。这三者描述**同一机制**，中文更宽泛，容易让玩家误判作用范围：

| 位置 | 中文（缺） | 英文 / 手册（全） |
|---|---|---|
| `chip.electric_glove` | 「本周期的远程/魔法伤害同时命中…」 | `all ranged/magic damage **to hostiles**`；手册「使本效果牌周期内的远程和魔法伤害同时对…」 |
| `card.directional_blast` | 「使远程和魔法伤害增加 N 点」 | `all ranged/magic damage **to hostiles**`；手册「所有**对敌对目标造成的**远程与魔法伤害 +5」 |
| `card.monster_brick` | 同上 | 同上 |
| `card.monster_laser` | 同上 | 同上 |
| `card.orbital_strike` | 同上 | 同上 |
| `sign.mimi_active`（L550） | 「回收物品栏中所有卡牌，并返还 §eN+1§7 张随机卡牌」 | `(including exclusive cards)` + `(exclusive cards excluded)`；手册亦无 |
| `sign.padman_passive`（L583） | 「无视目标防御力」 | `ignore target defense & armor **(except enemy defense cards)** = true damage` |
| `sign.fanny_active`（L551） | 「获得 §9滋养 (2:00)§7 和 §9饱和 (0:30)§7」 | `Nourishment (2:00) **(Farmer's Delight only)**` |
| `card.charge`（L426） | 「攻击力 §e+5§7, 骰神赐福效果结束后返还全力攻击」 | `§e+5§7 **fixed during Dice Blessing**, drops Full Power when blessing ends` |
| `card.shadow_strike`（L449） | 「攻击力 §e+3§7 + §9黑暗 (0:03)§7」 | `§e+3§7 **fixed** + Darkness (0:03)` |

**建议**：以英文 / 手册为准补齐中文；`padman_passive` 的「（敌人防御牌除外）」与 `fanny_active` 的「（需安装农夫乐事）」属玩法相关限定，优先级最高。`mimi_active` 的 `N+1` 中 `N` 在文案里无定义（代码 `MimiSignItem.java:58` 为 `recycled + 1`），建议改为「**返还（回收张数 + 1）张随机卡牌（不含专属牌）**」。

### P1-15 手册漏掉战斗牌「取 2 次最大值」机制

- tooltip 有：`card.attack_medium/large/epic/meito`、`card.defense_*` 均含「**（取2次最大值）**」
- 手册 `attack_card_medium.1` 等只有「骰战中提供 1~3 点攻击力」
- 代码 `combat/CardRegistry.java:169-174` `rollTwoMax()` 确认「掷两次取最大」是核心机制（显著影响期望值）
- **建议**：手册 6~7 条战斗牌条目补「（掷 2 次取最大值）」。

### P1-16 手册 `cursed_sword`「最大有上限」未给数值

- 手册 `cursed_sword_chip.2`：「…攻击力 +1（每个赐福期间最多 1 次），**最大有上限**」
- tooltip `chip.cursed_sword` 用 `§e%s§7` 动态显示实际上限值
- **建议**：手册改为具体数值或「上限随星级变化」，避免「有上限」这种无信息量表述。

---

## 4. P2 —— 风格与格式一致性

### P2-1 引号三制并存（且与手册不一致）

| 风格 | 位置 |
|---|---|
| 直引号 `"…"` | `sign.bonnie_active/passive`、`sign.haiqing_passive`、`sign.investigation_desc`、`sign.misaki_active/passive`、`sign.rin_active/passive`、`card.fate_guidance_desc` |
| 弯引号 `“…”` | `sign.misaki_enigmatic`（`“爆发”`）、`chip.satellite`（`“轨道炮”`） |
| 角引号 `「…」` | `sign.moses_passive`、`sign.misaki_death_note`、`sign.pandaman_passive`、`sign.jasmine_passive` |

手册**一律**用 `「」`。→ **同一术语在物品栏与手册里长得不一样**：

| 术语 | 物品栏 | 手册 |
|---|---|---|
| 活体书页 | `"活体书页"` | `「活体书页」` |
| 隐匿调查 | `"隐匿调查"` | `「隐匿调查」` |
| 虚弱印记 | `"虚弱印记"` | `「虚弱印记」` |
| 轨道炮 | `“轨道炮”` | `「轨道炮」` |
| 名刀·嘎呜切 | `"名刀·嘎呜切"` | `「名刀·嘎呜切」` |
| 破绽 / 弱点识破 | `「破绽」` ✅ | `「破绽」` |

另：`sign.misaki_active` 里的「名刀**嘎呜切**」**缺了 `·`**（物品名与手册 `misaki_sign.2` 都是「名刀**·**嘎呜切」），而同一 key 最后一行又写成「"名刀嘎呜切"」→ **同一个 tooltip 内就前后不一致**；手册 `misaki_sign.1` 也缺 `·`。

**建议**：全模块（含手册与物品名引称）统一为 `「」`；补齐「名刀·嘎呜切」的 `·`。

### P2-2 冒号半角 / 全角混用

- 半角 `: `：`sign.active_title`、`sign.passive_title`、`sign.cooldown_remaining`、`sign.fen_recharge`、`sign.komachi_damage_bonus`、`sign.lulu_healing`、`sign.moses_weakness_reveal`、`sign.nancy_lu_bonus`、`sign.padman_bonus`、`sign.pandaman_health_gain`、`sign.parunan_starlight`、`chip.charge`、`chip.starlight`、全部 `card.*`
- 全角 `：`：`sign.fanny_active`、`sign.fen_active`、`sign.misaki_stacks`、`sign.papara_active`、`chip.adrenaline_*`、`chip.airbag`、`chip.current_core`、`chip.electric_glove`、`chip.warp_engine`、`chip.whetstone`、`card.play_count`、`card.active_damage_bonus`

**同一「资源计数器」家族内部就不统一**：

| key | 渲染 |
|---|---|
| `sign.misaki_stacks` | `剑气：§e%s§7 / §e3§7` ← 全角 |
| `sign.fen_recharge` | `养精蓄锐: §e%s§7 / §e%s§7` ← 半角 |
| `sign.parunan_starlight` | `星光: §e%s§7 / §e%s§7` ← 半角 |
| `sign.moses_weakness_reveal` | `弱点识破: §e%s§7 / §e%s§7` ← 半角 |
| `chip.charge` | `当前充能: §e%s§7 / §e%s§7` ← 半角 |
| `chip.starlight` | `当前星光: §e%s§7 / §e%s§7` ← 半角 |

**建议**：中文正文统一全角 `：`；计数器行统一为 `名称：§e%s§7 / §e%s§7`。

### P2-3 半角逗号混入中文

- `chip.medkit_complete`（L480）：「触发骰神赐福时**,**增加 §e3§7 点治愈」
- `chip.medkit_emergency`（L481）：同上
- `card.charge`（L426）：「攻击力 §e+5§7**,** 骰神赐福效果结束后…」
- `card.full_power`：「攻击力 §e+6§7**,** 本次攻击的最终攻击力…」

其余全部用 `，`。**建议**改为全角，并去掉逗号后多余空格（对比 `chip.medkit_*` 与相邻 chip 的写法差异）。

### P2-4 尾随空格

- `sign.padman_passive`（L583）第 2 行末：「骰神赐福期间骰点为 §e6§7 时无视目标防御力` `」——行尾多一个空格。
- 全模块唯一一处。

### P2-5 百分比色码后的多余空格

- `chip.cutter`（L469）：「生命值在 §e60%%` `§7以上时…」
- `chip.cutter_blade`（L470）：同上
- 全模块唯一两处；其余均为 `§eNN%%§7 或更低/不少于…`（空格在 `§7` **之后**）。**建议**改成 `§e60%%§7 以上时`。

### P2-6 状态效果描述缺空格

| key | 现文 | 应为 |
|---|---|---|
| `effect.astral_dice.berserk.description` | `持续§93:00§7` | `持续 §93:00§7` |
| `effect.astral_dice.fight_poison_with_poison.description` | `移除最多§e3§7种` | `移除最多 §e3§7 种` |

`effect.astral_dice.empower.description`、`blue_curse.description` 等同类描述都带空格 → 只有这两处漏。

### P2-7 「秒」前空格两种写法

- 无空格：`chip.buffer_shield`（`§915秒§7`）、`sign.lulu_passive`（`§9-10秒§7`）、`card.effect_cooldown`（`§9%s秒§7`）
- 有空格：`sign.nancy_lu_active`（`§930 秒§7`）、`chip.railgun`（`§91 秒§7`）、`sign.moses_passive`（`§9120 秒§7`）、`chip.current_core`（`§9180 秒§7`）

**建议**：统一 `§9N 秒§7`（不带空格的 `%s秒` 保留可读性即可，但同一文件内应择一）。

### P2-8 增益行（`%s` 家族）格式互不相同

| key | 现文 |
|---|---|
| `sign.jasmine_bonus` | `攻击力 §e+%s§7 \| 防御力 §e+%s§7`（无冒号） |
| `sign.nancy_lu_bonus` | `攻击力: §e+%s§7 \| 防御力: §e+%s§7` |
| `sign.padman_bonus` | `攻击力: §e+%s§7 \| 防御力: §e+%s§7` |
| `chip.revenge_halberd_current`（L490） | `\n攻击力 %s \| 防御力 %s` ← **缺 `+`、缺 §e 染色** |
| `chip.cursed_sword_bonus` | `攻击力 §e+%s§7` |
| `sign.lulu_healing` / `sign.komachi_damage_bonus` | `治愈: §e%s§7` / `效果牌伤害增益: §e%s§7` |

**建议**：攻/防双值行统一 `攻击力 §e+%s§7 | 防御力 §e+%s§7`；单值行统一 `名称：§e+%s§7`。`revenge_halberd_current` 需补 `+` 与 `§e`。

### P2-9 阈值措辞五种说法

| 说法 | 位置 |
|---|---|
| `以上` | `chip.cutter`、`chip.cutter_blade`、手册 `cutter_*_chip.1` |
| `不少于` | `chip.electric_glove`、`chip.railgun`、`chip.cursed_sword`、`sign.bonnie_passive`、手册对应条目 |
| `不低于` | `chip.advanced_peripherals`、手册 `advanced_peripherals_chip.1` |
| `不超过` | `chip.whetstone`、`sign.pandaman_passive` |
| `或更低` | `chip.adrenaline_*`、`chip.whetstone`（本次已统一） |
| `低于` | `sign.papara_passive` ← **错误**，见 P0-1 |

**建议**：统一为「**X% 或更高 / X% 或更低**」（含边界时）与「**X% 以上 / X% 以下**」（不含边界时），并在文档中固化。当前全模块实际上都是含边界语义，因此推荐一律用「或更高 / 或更低」。

### P2-10 复仇之戟：列表结构混乱

现文（`chip.revenge_halberd`，L489）：

```
获得以下任一效果时，攻击力 §e+6§7：
虚弱、缓慢、挖掘疲劳、失明、黑暗、蓄风、盘丝、渗浆、寄生、青之诅咒
获得以下任一效果时，防御力 §e+6§7：
饥饿、反胃、中毒、凋零、袭击之兆、试炼之兆、标记。
（每类只触发一次，不叠加；对应效果全部消失后加成立即消失）
```

问题：第 3 行的「防御力 +6：」标题与第 2 行的**效果列表处于同一缩进层级**，没有任何缩进或前缀区分，视觉上会被读成「攻击力加成清单里的一项」。且第 2 行末无标点、第 4 行末 `。`、第 5 行整体括号——标点风格页不一致。

**建议**：

```
获得以下任一效果时，攻击力 §e+6§7：
  虚弱、缓慢、挖掘疲劳、失明、黑暗、蓄风、盘丝、渗浆、寄生、青之诅咒
获得以下任一效果时，防御力 §e+6§7：
  饥饿、反胃、中毒、凋零、袭击之兆、试炼之兆、标记
（每类只触发一次，不叠加；对应效果全部消失后加成立即消失）
```

（效果清单一与源码 `RevengeHalberdChipItem.java:35-55` 完全一致，内容无误，仅排版问题。）

### P2-11 枪匠被动：句间无标点

- `sign.moses_passive`（L579）第 2 行：`主动技能冷却时间减为 §9120 秒§7 触发闪避、反击、或攻击带「破绽」的目标后，获得…`
- 两句直接拼接（`§7 触发`），会被读成「减为 120 秒触发闪避」。
- 另：此处缺「**装备时**」限定（手册 `moses_sign.2` 有；源码 `MosesSignItem.signCooldownTicks()` 仅在 `isEquipped` 时用 120 秒）。
- **建议**：拆行 + 补「装备时」。

### P2-12 死键：`sign.lulu_healing`

- `tooltip.astral_dice.sign.lulu_healing`（L567）= `治愈: §e%s§7`，在 410 个 Java 源文件中**无任何引用**；史莱姆立牌的计数器实际走 `tooltip.astral_dice.healing_points`（`ModTooltipHandler.java:211`）。
- 全模块唯一死键。**建议**删除该条目（双版本 × 中英，共 4 处）。

### P2-13 筹码阶级命名不成体系

| 阶梯写法 | 筹码 |
|---|---|
| 初级 / 中级 / 高级 | 拳击手套、速度轮滑 |
| 一般 / 中级 / 高级 | 摩托头盔 |
| 一般 / 高效 | 肾上腺素 |
| 一般 / 可口 / 美味 | 夹心饼干 |
| 初级 / 锋利 | 美工刀 |
| 余额多 / 余额少 / 用不完 | 银行卡（3 档） |
| 大 / 小 | 手持风扇 |
| 紧急治疗 / 完备治疗 | 医疗箱 |
| 高效（单档） | 手电筒（`手电筒-强光`） |

同一「低 → 中 → 高」阶梯至少有 **6 种**命名体系。

**建议**：统一「**初级 / 中级 / 高级**」；功能性词汇（银行卡的余额、夹心饼干的口味）可保留但应保持一致的三段式。**注意**：仅改 `item.astral_dice.*` 显示值，注册名与标签不动 → **无存档风险**。

### P2-14 错别字：标记喷灌 → 标记喷罐 ✅ 已裁定

- `item.astral_dice.marker_sprayer_chip`：zh 显示名现为「**标记喷罐**」（英文 `Marker Sprayer`）
- 类名 `MarkerSprayerChipItem`（Sprayer = 喷雾器）；手册 `mark_paint` 描述为「用于标记目标的**涂料**」
- 判定经过：本条最初判定「喷灌」为错别字 → 中途一度按《吉星派对》专有名词「保留原名」（该判断作废）→
  **2026-09-15 用户裁定：「标记喷罐」是唯一正确写法，「标记喷灌」为错别字**。
- **处置**：zh 显示名 / 手册 / 文档 / 代码注释全文统一为「喷罐」；`AGENTS.md` 的旧注记已改写。

### P2-15 效果牌描述来源三条路径

| 路径 | 覆盖的效果牌 |
|---|---|
| `tooltip.astral_dice.card.*` | 汉堡、巧克力蛋糕、奢华大餐、王之力、你有我有、加急加快、对怪激光、对怪板砖、轨道炮、定向爆破、活体书页、命运的指引 |
| `effect.astral_dice.*.description`（**与状态效果 Tooltip 共用**） | 狂暴、岿然不动、以毒攻毒 |
| 仅手册 | — |

由此产生两处次级问题：

| key | 问题 |
|---|---|
| `effect.astral_dice.unwavering.description` | 「并获得**抗性提升** II」——① 状态名未按 §9 染色（对比 `sign.jasmine_active` 的 `§9抗性提升 (2:00)§7`）；② `并获得 抗性提升` 中间多一个空格 |
| `effect.astral_dice.fight_poison_with_poison.description` | 缺手册里的「**（隐藏图标）**」说明 |

**建议**：把 3 张效果牌的描述迁到 `tooltip.astral_dice.card.*` 命名空间，或在 `docs/` 固化「效果牌描述放哪」的规则，避免后续新增时再次分叉。

### P2-16 升星提示：全模块唯一的 `§r`

- `card.upgrade_hint`（L450）：`在铁砧上使用星币升星: §e★%s§r→§e★%s§r 需 §e%s§r 星币`
- 该组件整体被 `.withStyle(ChatFormatting.YELLOW)`（`ModTooltipHandler.java:353`）
- `§r` 是**复位**码，会把后续文字刷成默认白，而非组件基色黄 → 「→」与「需」会以白色显示，与周围黄色不搭。
- 立牌 / 筹码 / 卡牌三类中**仅此 1 处**用 `§r`，其余一律用 `§7` 收尾。
- **建议**：改为 `§e★%s§7→§e★%s§7 需 §e%s§7 星币`。

### P2-17 八面骰：「若本次骰点刚好为 8 时」多一个「时」

- `chip.eight_sided_dice`：「…若本次骰点刚好为 §e8§7 **时**获得 §e8§7 星币」
- 手册 `eight_sided_dice_chip.1` 为「若本次骰点刚好为 8，获得 8 星币」。
- **建议**：删「时」，或改「时，」。

---

## 5. 建议的修复顺序与检查清单

**第一批（P0，涉及玩法判断，建议单独一次提交）**
1. `sign.papara_passive` + 手册 `papara_sign.2`：`低于` → `生命值为 50% 或更低时`
2. `chip.whetstone` + 手册 `whetstone_chip.1`：补「至多使生命值降至 1 点（不会被单次伤害击倒）」
3. `sign.papara_active` + 手册 `papara_sign.1`：重写「被视为满血和半血」
4. `chip.big_bowl_stew` + 手册：区分「友方玩家」与「友方生物」

**第二批（P1，信息缺失 / 术语）**
5. 三个立牌主动补 30 秒窗口（`bonnie_active` / `haiqing_active` / `moses_active`）
6. `chip.current_core` 补两条规则；`sign.padman_passive` 补「敌人防御牌除外」；`sign.fanny_active` 补「需安装农夫乐事」
7. 术语统一批：充能「点」、标记「层」、最大生命值、随机卡牌、生命值、伤害类效果牌
8. 卡牌名三处写法归一；`sign.mimi_active` 的 `N+1` 改为「回收张数 +1」
9. 手册补「取 2 次最大值」与 `cursed_sword` 上限数值

**第三批（P2，格式，可一次性机械替换）**
10. 引号统一「」；冒号统一全角；半角逗号→全角；`%% §7`→`%%§7`；尾随空格；`§r`→`§7`；`§9N秒§7` 空格；`revenge_halberd_current` 补 `+`/`§e`；`revenge_halberd` 与 `moses_passive` 重排
11. 删除死键 `sign.lulu_healing`（4 处）
12. 筹码阶级命名统一；「标记喷灌」→「标记喷罐」（✅ 已完成，见 P2-14）

**收尾校验（项目既有流程）**

```powershell
# 1. 语言文件格式与双版本一致性（须 cd 进子项目）
python tools/check_lang_sync.ps1
# 2. tooltip 染色规则
python scripts/audit/tooltip_color_audit.ps1
# 3. 内容库一致性
python scripts/verify/verify_content_library.ps1
# 4. 双版本构建 + 自动三部署
Set-Location F:\MCProject\astral_dice_multiloader; & .\gradlew.bat build --console=plain *>> temp\build.log
```

**批量修改语言文件时的红线**（血泪教训）：必须 `open(..., encoding='utf-8', newline='')` 读写、逐行定点替换并 `assert count == 1`；**禁用 `json.dump` 整写**，否则 Windows 默认会把 LF 变 CRLF 并补结尾换行，直接违反 lang 格式约定。

**新增阈值判定时的自查**：改动任何 `getHealth()` 比较前，先确认文案用的是「或更低（含边界）」还是「以下（不含边界）」，并同时核对 tooltip、手册、英文三处——papara 的 P0-1 就是三处中只有中文写错、英文写对，导致矛盾长期未被发现。

---

*报告生成：2026-09-12。审计为只读操作，未修改任何源码或语言文件。*
