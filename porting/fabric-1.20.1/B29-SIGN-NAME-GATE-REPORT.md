# B29 立牌英文名门禁覆盖 fabric 线 + fabric 立牌英文名订正

- 仓库：`D:\MCProject\astral_dice_multiloader`（分支 `multi-main`，工作树未提交；本批未 commit / push / stash / add / checkout，未跑 gradle，未启动游戏）
- 基线：`neoforge-1.21.1`（P0 主基准）、`forge-1.20.1`（P0 次基准）为权威实现；`fabric-1.20.1` 为对齐目标
- 命名正本：技能 `astral-dice-sign-i18n-naming`（流程）+ 权威表 `tools/sign_display_names.json`（32 条）
- 执行日期：2026-11-01

## 0. 结论摘要

| 项 | 结果 |
|---|---|
| 门禁覆盖线 | 2 → **3**（`neoforge-1.21.1` / `forge-1.20.1` / `fabric-1.20.1`） |
| fabric 立牌命名订正 | **41 条键**（17 条 `item.astral_dice.*_sign` + 24 条散文引用键），对应门禁 **59 处**不符 |
| 权威表是否需增补 | **否**（fabric 的立牌 id 集合与表 32 条完全相等，无 fabric 特有立牌） |
| `ja_jp.json` 是否需改 | **否**（41 个受影响键的日文值与两条 P0 线逐字节相同，已用角色名，0 处需改） |
| 三线门禁 | `GATE: PASS`（含 fabric），检查项 74,673 |
| 三语键数 | fabric `zh_cn/en_us/ja_jp = 1094 / 1094 / 1094`（改前改后一致） |
| 反向判据 | 注入旧职称 `Vampire Sign` → `FAIL: 2 处不符` 并指名；恢复后 sha256 复原 |
| BLOCKED | **无** |

## 1. 技能加载记录

按用户指令「动手前先加载技能」，本轮在**任何文件读取 / git 查询之前**依次加载：

1. `using-superpowers` —— 会话第一条动作，按其规则「先查技能目录再动手」执行。
2. `astral-dice-sign-i18n-naming` —— 命名规则正本。加载后严格按其流程执行（下文每步可对应该技能「步骤」1–5）：

| 技能步骤 | 本轮落地 |
|---|---|
| 1. 定 id + 找 wiki 页 | 立牌 id 全部取自 `tools/sign_display_names.json`，表内 32 条均已登记 `wiki_page` |
| 2. 拉名字 | `python tools/sign_names_from_wiki.py --only papara,zhao,fen` ⇒ `[ok] 3/3`、`漂移 0`、`表与 wiki 一致`（见 §2.2） |
| 3. 写权威表 | 无需新增（fabric 无特有立牌，见 §2.1） |
| 4. 改 lang | 逐行替换 `fabric-1.20.1/.../en_us.json` 的 41 条立牌相关键（含散文指代），保键序、保 CRLF |
| 5. 跑门禁 | `python tools/verify_sign_display_names.py` ⇒ PASS；`check_lang_sync.py --line fabric-1.20.1` ⇒ OK |

技能铁律逐条遵守：**zh 未动**（`zh_cn.json` 一个字未改）；**先表后 lang**（表本轮不变，故不存在顺序问题）；**改名改全部引用**（24 条散文键一并改）；**既有专名不动**（`Ninja Star` 筹码、`Legendary Merchant` 技能名、`Boss` 敌人泛指在 fabric 文案里未命中，见 §8.4 反查）；**不机翻**（全部取表内 wiki 人名）。

## 2. 事实源与前置核查

### 2.1 权威表 vs fabric 立牌键集合

- 权威表：32 条（`blank` / `deepseek` 为非 wiki 角色，其余 30 条为 Astral Party 角色）。
- fabric `en_us.json` 中 `item.astral_dice.*_sign` 键：32 条。
- 交集差集为空 ⇒ **表无需增补**，任务步骤 4 不触发（无 fabric 特有立牌）。

### 2.2 wiki 复核（技能步骤 2）

```
$ python tools/sign_names_from_wiki.py --only papara,zhao,fen
[ok]    fen        Fen Sign | ヒメのスタンド
[ok]    papara     Papara Sign | パパラのスタンド
[ok]    zhao       Zhao Sign | ユメのスタンド
---- 与 wiki 一致 3 / 漂移 0 / 本地过 0 / 失败 0
WIKI: OK（权威表与 wiki 一致）
```

### 2.3 ja_jp 是否需要改（技能铁律 4 的前置检查）

对 41 个受影响键逐条比对 fabric 与两条 P0 线的 `ja_jp` 值：

- 39 条键：`ja` 与 P0 **完全相同**（fabric 日文早已使用角色名，如 `ボニーのスタンド` / `ヒメのスタンド`）。
- 2 条 fabric 独有键（`astral_dice.guide.entry.nardis_sign.3`、`msg.astral_dice.parunan_active_no_starlight`）：P0 无对应键，但 fabric 的日文值本身已是角色名（`ルルのスタンド` / `パルナンのスタンド`），不越权改。

⇒ **`ja_jp.json` 本轮零改动**（sha256 与改前一致，见 §8）。

### 2.4 行尾基线（改前实读）

| 文件 | 行尾 | 字节 | sha256（改前） |
|---|---|---|---|
| `tools/verify_sign_display_names.py` | LF（CRLF 0 / 裸 LF 121） | 5,433 | `341f65fe…75fca6bb` |
| `tools/sign_display_names.json` | LF（CRLF 0 / 裸 LF 268） | 6,501 | `93d1a003…4a50c1bc0`（未改） |
| `fabric-1.20.1/.../en_us.json` | CRLF 1096 / 裸 LF 0 / 无 BOM | 151,662 | `ed45a58e…2c156977cf5` |
| `fabric-1.20.1/.../ja_jp.json` | CRLF 1096 / 裸 LF 0 / 无 BOM | 187,070 | `92d0db7a…361795e935`（未改） |

## 3. 门禁扩线（改了什么）

只动两处，脚本结构与输出格式不变：

```diff
-'''立牌英日命名门禁（P0 两线：neoforge-1.21.1 / forge-1.20.1）。
+'''立牌英日命名门禁（三线：neoforge-1.21.1 / forge-1.20.1 / fabric-1.20.1）。
 
-LINES = ['neoforge-1.21.1', 'forge-1.20.1']
+LINES = ['neoforge-1.21.1', 'forge-1.20.1', 'fabric-1.20.1']
```

- 检查项 49,414 → 74,673（新增 fabric 的 25,259 项）。
- 行尾保持 **LF**（改后 CRLF 0 / 裸 LF 121，与改前一致）。

## 4. 门禁扩线前后输出

### 4.1 扩线前（LINES 仍是两线）

```
$ python tools/verify_sign_display_names.py
== 立牌英日命名门禁 ==
权威表: tools/sign_display_names.json（32 条，updated 2026-10-26）
-- neoforge-1.21.1 -- zh 1070 keys / en 1070 keys / ja 1070 keys
-- forge-1.20.1 -- zh 1070 keys / en 1070 keys / ja 1070 keys
检查项: 49414
GATE: PASS（立牌英日命名与权威表逐字一致，无旧职称残留）
EXIT=0
```

### 4.2 扩线后、改 lang 前（fabric 失败清单，本轮工作清单，原样）

```
$ python tools/verify_sign_display_names.py
== 立牌英日命名门禁 ==
权威表: tools/sign_display_names.json（32 条，updated 2026-10-26）
-- neoforge-1.21.1 -- zh 1070 keys / en 1070 keys / ja 1070 keys
-- forge-1.20.1 -- zh 1070 keys / en 1070 keys / ja 1070 keys
-- fabric-1.20.1 -- zh 1094 keys / en 1094 keys / ja 1094 keys
检查项: 74673
FAIL: 59 处不符
  - [fabric-1.20.1] item.astral_dice.bonnie_sign = 'Secret Detective Sign'，应为 'Bonnie Sign'
  - [fabric-1.20.1] item.astral_dice.fanny_sign = 'Great Detective Sign'，应为 'Fanny Sign'
  - [fabric-1.20.1] item.astral_dice.fen_sign = 'Boss Sign'，应为 'Fen Sign'
  - [fabric-1.20.1] item.astral_dice.haiqing_sign = 'Astrologer Sign'，应为 'HaiQing Sign'
  - [fabric-1.20.1] item.astral_dice.jasmine_sign = 'Sweeper Sign'，应为 'Jasmine Sign'
  - [fabric-1.20.1] item.astral_dice.komachi_sign = 'Ninja Sign'，应为 'Komachi Sign'
  - [fabric-1.20.1] item.astral_dice.lulu_sign = 'Slime Sign'，应为 'Lulu Sign'
  - [fabric-1.20.1] item.astral_dice.mimi_sign = 'Kanban Musume Sign'，应为 'Mimi Sign'
  - [fabric-1.20.1] item.astral_dice.misaki_sign = 'Guardian Sign'，应为 'Misaki Sign'
  - [fabric-1.20.1] item.astral_dice.moses_sign = 'Gunsmith Sign'，应为 'Moses Sign'
  - [fabric-1.20.1] item.astral_dice.papara_sign = 'Vampire Sign'，应为 'Papara Sign'
  - [fabric-1.20.1] item.astral_dice.parunan_sign = 'Business Sign'，应为 'Parunan Sign'
  - [fabric-1.20.1] item.astral_dice.rin_sign = 'Investigator Sign'，应为 'Rin Sign'
  - [fabric-1.20.1] item.astral_dice.ren_sign = 'Game Master Sign'，应为 'Ren Sign'
  - [fabric-1.20.1] item.astral_dice.zhao_sign = 'Fengshui Master Sign'，应为 'Zhao Sign'
  - [fabric-1.20.1] item.astral_dice.teru_sign = 'Cult Leader Sign'，应为 'Teru Sign'
  - [fabric-1.20.1] item.astral_dice.nardis_sign = "Oasis Queen's Sign"，应为 'Nardis Sign'
  - [fabric-1.20.1] en astral_dice.guide.entry.effect_card_living_page.3 仍含旧职称命名 'Investigator' :: While wearing the Investigator sign, each Living Page used permanently increases its damag
  - [fabric-1.20.1] en astral_dice.guide.entry.playstyles.3 仍含旧职称命名 'Slime' :: Healing: healing layers form a single pool provided by Medkits, the Slime sign and the Buf
  - [fabric-1.20.1] en astral_dice.guide.entry.adrenaline_high_chip.1 仍含旧职称命名 'Vampire' :: Adrenaline (High-Grade): at 50%% health or below, attack/defense +8 (counts as satisfied w
  - [fabric-1.20.1] en astral_dice.guide.entry.adrenaline_low_chip.1 仍含旧职称命名 'Vampire' :: Adrenaline (Common): at 50%% health or below, attack/defense +3 (counts as satisfied while
  - [fabric-1.20.1] en astral_dice.guide.entry.spell_damage.2 仍含旧职称命名 'Ninja' :: Magic damage is affected by damage bonuses, e.g. the Ninja sign's "Damage Effect Card Bonu
  - [fabric-1.20.1] en item.astral_dice.bonnie_sign 仍含旧职称命名 'Secret Detective' :: Secret Detective Sign
  - [fabric-1.20.1] en item.astral_dice.fanny_sign 仍含旧职称命名 'Great Detective' :: Great Detective Sign
  - [fabric-1.20.1] en item.astral_dice.fen_sign 仍含旧职称命名 'Boss' :: Boss Sign
  - [fabric-1.20.1] en item.astral_dice.haiqing_sign 仍含旧职称命名 'Astrologer' :: Astrologer Sign
  - [fabric-1.20.1] en item.astral_dice.jasmine_sign 仍含旧职称命名 'Sweeper' :: Sweeper Sign
  - [fabric-1.20.1] en item.astral_dice.komachi_sign 仍含旧职称命名 'Ninja' :: Ninja Sign
  - [fabric-1.20.1] en item.astral_dice.lulu_sign 仍含旧职称命名 'Slime' :: Slime Sign
  - [fabric-1.20.1] en item.astral_dice.mimi_sign 仍含旧职称命名 'Kanban Musume' :: Kanban Musume Sign
  - [fabric-1.20.1] en item.astral_dice.misaki_sign 仍含旧职称命名 'Guardian' :: Guardian Sign
  - [fabric-1.20.1] en item.astral_dice.moses_sign 仍含旧职称命名 'Gunsmith' :: Gunsmith Sign
  - [fabric-1.20.1] en item.astral_dice.papara_sign 仍含旧职称命名 'Vampire' :: Vampire Sign
  - [fabric-1.20.1] en item.astral_dice.parunan_sign 仍含旧职称命名 'Business' :: Business Sign
  - [fabric-1.20.1] en item.astral_dice.rin_sign 仍含旧职称命名 'Investigator' :: Investigator Sign
  - [fabric-1.20.1] en msg.astral_dice.moses_ready 仍含旧职称命名 'Gunsmith' :: Gunsmith sign: active skill activated — select a hostile target to apply Broken
  - [fabric-1.20.1] en msg.astral_dice.moses_apply 仍含旧职称命名 'Gunsmith' :: Gunsmith sign: Broken applied
  - [fabric-1.20.1] en msg.astral_dice.haiqing_ready 仍含旧职称命名 'Astrologer' :: Astrologer sign: active skill activated — select a hostile target to apply the Weak Mark
  - [fabric-1.20.1] en msg.astral_dice.komachi_active 仍含旧职称命名 'Ninja' :: Ninja sign: effect card play count +1 granted. Remaining plays: %d
  - [fabric-1.20.1] en msg.astral_dice.mimi_active 仍含旧职称命名 'Kanban Musume' :: Kanban Musume Sign: got %d new cards and %d Star Coins
  ... 其余 19 处省略
EXIT=1
```

> 脚本按设计只打印前 40 处（`fails[:40]`）。59 = **17**（技能判据 1：立牌键与表逐字比对）× 1 + **42**（技能判据 2：EN 旧职称 `<旧职称>(?:'s)? ?[Ss]ign` 命中，按「键 × 旧职称词」计）。完整 42 条命中见 §4.3，不靠省略行猜测。

### 4.3 判据 2 的完整 42 条命中（工作清单全量枚举）

| # | 键 | 命中旧职称词 |
|---|---|---|
| 1 | `astral_dice.guide.entry.adrenaline_high_chip.1` | Vampire |
| 2 | `astral_dice.guide.entry.adrenaline_low_chip.1` | Vampire |
| 3 | `astral_dice.guide.entry.effect_card_living_page.3` | Investigator |
| 4 | `astral_dice.guide.entry.nardis_sign.1` | Oasis Queen |
| 5 | `astral_dice.guide.entry.nardis_sign.3` | Slime |
| 6 | `astral_dice.guide.entry.playstyles.3` | Slime |
| 7 | `astral_dice.guide.entry.ren_sign.1` | Game Master |
| 8 | `astral_dice.guide.entry.spell_damage.2` | Ninja |
| 9 | `astral_dice.guide.entry.teru_sign.1` | Cult Leader |
| 10 | `astral_dice.guide.entry.zhao_sign.1` | Fengshui Master |
| 11 | `astral_dice.guide.entry.zhao_sign.2` | Boss |
| 12 | `astral_dice.guide.entry.zhao_sign.3` | Boss |
| 13 | `astral_dice.guide.entry.zhao_sign.3` | Fengshui Master |
| 14 | `item.astral_dice.bonnie_sign` | Secret Detective |
| 15 | `item.astral_dice.fanny_sign` | Great Detective |
| 16 | `item.astral_dice.fen_sign` | Boss |
| 17 | `item.astral_dice.haiqing_sign` | Astrologer |
| 18 | `item.astral_dice.jasmine_sign` | Sweeper |
| 19 | `item.astral_dice.komachi_sign` | Ninja |
| 20 | `item.astral_dice.lulu_sign` | Slime |
| 21 | `item.astral_dice.mimi_sign` | Kanban Musume |
| 22 | `item.astral_dice.misaki_sign` | Guardian |
| 23 | `item.astral_dice.moses_sign` | Gunsmith |
| 24 | `item.astral_dice.nardis_sign` | Oasis Queen |
| 25 | `item.astral_dice.papara_sign` | Vampire |
| 26 | `item.astral_dice.parunan_sign` | Business |
| 27 | `item.astral_dice.ren_sign` | Game Master |
| 28 | `item.astral_dice.rin_sign` | Investigator |
| 29 | `item.astral_dice.teru_sign` | Cult Leader |
| 30 | `item.astral_dice.zhao_sign` | Fengshui Master |
| 31 | `msg.astral_dice.haiqing_ready` | Astrologer |
| 32 | `msg.astral_dice.komachi_active` | Ninja |
| 33 | `msg.astral_dice.mimi_active` | Kanban Musume |
| 34 | `msg.astral_dice.moses_apply` | Gunsmith |
| 35 | `msg.astral_dice.moses_ready` | Gunsmith |
| 36 | `msg.astral_dice.nardis_active` | Oasis Queen |
| 37 | `msg.astral_dice.parunan_active_no_starlight` | Merchant |
| 38 | `msg.astral_dice.teru_descent_in_effect` | Cult Leader |
| 39 | `msg.astral_dice.teru_ready` | Cult Leader |
| 40 | `msg.astral_dice.zhao_blessing_ready` | Fengshui Master |
| 41 | `tooltip.astral_dice.sign.fen_xinyi` | Fengshui Master |
| 42 | `tooltip.astral_dice.sign.zhao_wanmei_bangshou` | Boss |

## 5. 旧名 → 新名逐条对照表（41 条键，59 处不符）

> 类别「立牌物品键」= `item.astral_dice.*_sign`（显示名本身）；「散文引用」= `guide.entry.*` / `msg.*` / `tooltip.*` 里复述立牌名的句子（技能铁律 4）。
> wiki 列给出该行改动所涉角色；含多名角色的行（第 12 行为 Fen + Zhao，第 5 行为 Lulu + Padman）以「改动的那一个」为准。

| # | 键 | 类别 | 旧值 | 新值 | wiki 来源 |
|---|---|---|---|---|---|
| 1 | `astral_dice.guide.entry.adrenaline_high_chip.1` | 散文引用 | `Adrenaline (High-Grade): at 50%% health or below, attack/defense +8 (counts as satisfied while the Vampire sign's Drain is active).` | `Adrenaline (High-Grade): at 50%% health or below, attack/defense +8 (counts as satisfied while the Papara Sign's Drain is active).` | [Papara](https://astralparty.miraheze.org/wiki/Papara) |
| 2 | `astral_dice.guide.entry.adrenaline_low_chip.1` | 散文引用 | `Adrenaline (Common): at 50%% health or below, attack/defense +3 (counts as satisfied while the Vampire sign's Drain is active).` | `Adrenaline (Common): at 50%% health or below, attack/defense +3 (counts as satisfied while the Papara Sign's Drain is active).` | [Papara](https://astralparty.miraheze.org/wiki/Papara) |
| 3 | `astral_dice.guide.entry.effect_card_living_page.3` | 散文引用 | `While wearing the Investigator sign, each Living Page used permanently increases its damage by +1; only when the target already had 3+ Mark layers before the hit does this card cycle grant +1 card slot (hard cap of 9 per round).` | `While wearing the Rin Sign, each Living Page used permanently increases its damage by +1; only when the target already had 3+ Mark layers before the hit does this card cycle grant +1 card slot (hard cap of 9 per round).` | [Rin](https://astralparty.miraheze.org/wiki/Rin) |
| 4 | `astral_dice.guide.entry.nardis_sign.1` | 散文引用 | `Oasis Queen sign. Active - Queen's Privilege: immediately gain 3 random temporary cards lasting 3:00. Temporary cards can only be equipped and used, not dropped or transferred. All temporary cards are discarded when the timer ends. Using the active skill does not freeze; if temporary cards remain, they are resupplied and the timer resets.` | `Nardis Sign. Active - Queen's Privilege: immediately gain 3 random temporary cards lasting 3:00. Temporary cards can only be equipped and used, not dropped or transferred. All temporary cards are discarded when the timer ends. Using the active skill does not freeze; if temporary cards remain, they are resupplied and the timer resets.` | [Nardis](https://astralparty.miraheze.org/wiki/Nardis) |
| 5 | `astral_dice.guide.entry.nardis_sign.3` | 散文引用 | `Recipe: golden dice tier (no star plate), the same tier as the Slime Sign and the Padman Sign.` | `Recipe: golden dice tier (no star plate), the same tier as the Lulu Sign and the Padman Sign.` | [Padman](https://astralparty.miraheze.org/wiki/Padman) |
| 6 | `astral_dice.guide.entry.playstyles.3` | 散文引用 | `Healing: healing layers form a single pool provided by Medkits, the Slime sign and the Buffer Shield; triggering the Dice Blessing — and every 1:00 settlement afterwards — heals you for healing layers × 2 (each settlement halves the layers first, then the Medkit tops them up).` | `Healing: healing layers form a single pool provided by Medkits, the Lulu Sign and the Buffer Shield; triggering the Dice Blessing — and every 1:00 settlement afterwards — heals you for healing layers × 2 (each settlement halves the layers first, then the Medkit tops them up).` | [Lulu](https://astralparty.miraheze.org/wiki/Lulu) |
| 7 | `astral_dice.guide.entry.ren_sign.1` | 散文引用 | `Game Master Sign. Active "Brat's Privilege": choose a friendly player or yourself and grant them the Mouse Shield: they gain 1 random card; the Mouse Shield = 5 yellow hearts (10 absorption) + Resistance + 1 Counterattack charge, and it vanishes the moment the yellow hearts are used up. The shield is immune to a single hit larger than its yellow hearts.` | `Ren Sign. Active "Brat's Privilege": choose a friendly player or yourself and grant them the Mouse Shield: they gain 1 random card; the Mouse Shield = 5 yellow hearts (10 absorption) + Resistance + 1 Counterattack charge, and it vanishes the moment the yellow hearts are used up. The shield is immune to a single hit larger than its yellow hearts.` | [Ren](https://astralparty.miraheze.org/wiki/Ren) |
| 8 | `astral_dice.guide.entry.spell_damage.2` | 散文引用 | `Magic damage is affected by damage bonuses, e.g. the Ninja sign's "Damage Effect Card Bonus" passive grants +1 per 3 effect cards used.` | `Magic damage is affected by damage bonuses, e.g. the Komachi Sign's "Damage Effect Card Bonus" passive grants +1 per 3 effect cards used.` | [Komachi](https://astralparty.miraheze.org/wiki/Komachi) |
| 9 | `astral_dice.guide.entry.teru_sign.1` | 散文引用 | `Cult Leader Sign. Active "Divine Descent": designate a player and apply Descent to them, so you gain 50%% of that player's attack and defense power. Each time the target attacks a hostile target it triggers "Pursuit", adding extra damage equal to the caster's own attack power plus the current Fox Light stacks; attacking a new target spends 1 Fox Light stack. Descent lasts 2:00, the timer starts only after the affected player first attacks a hostile target, and it is removed when it ends. When it ends, the caster's attack and defense bonus is removed.` | `Teru Sign. Active "Divine Descent": designate a player and apply Descent to them, so you gain 50%% of that player's attack and defense power. Each time the target attacks a hostile target it triggers "Pursuit", adding extra damage equal to the caster's own attack power plus the current Fox Light stacks; attacking a new target spends 1 Fox Light stack. Descent lasts 2:00, the timer starts only after the affected player first attacks a hostile target, and it is removed when it ends. When it ends, the caster's attack and defense bonus is removed.` | [Teru](https://astralparty.miraheze.org/wiki/Teru) |
| 10 | `astral_dice.guide.entry.zhao_sign.1` | 散文引用 | `Fengshui Master Sign. Active "Baize's Blessing": choose a player within 16 blocks or yourself and apply Baize's Blessing to them - overhealing is converted into attack power in equal amount, lasting 2:00; the timer starts only after the affected player first attacks a hostile target, and it is removed when it ends. The caster also gains 1 Blessing Talisman and converts every Misfortune Talisman they hold into Blessing Talismans.` | `Zhao Sign. Active "Baize's Blessing": choose a player within 16 blocks or yourself and apply Baize's Blessing to them - overhealing is converted into attack power in equal amount, lasting 2:00; the timer starts only after the affected player first attacks a hostile target, and it is removed when it ends. The caster also gains 1 Blessing Talisman and converts every Misfortune Talisman they hold into Blessing Talismans.` | [Zhao](https://astralparty.miraheze.org/wiki/Zhao) |
| 11 | `astral_dice.guide.entry.zhao_sign.2` | 散文引用 | `Passive "Fortune and Misfortune Intertwined": a dice result of 1 grants 1 Misfortune Talisman, a result of 6 grants 1 Blessing Talisman. Passive "Perfect Helper": applying Baize's Blessing to a player wearing the Boss Sign fills their Recharged Energy to the maximum (5 stacks).` | `Passive "Fortune and Misfortune Intertwined": a dice result of 1 grants 1 Misfortune Talisman, a result of 6 grants 1 Blessing Talisman. Passive "Perfect Helper": applying Baize's Blessing to a player wearing the Fen Sign fills their Recharged Energy to the maximum (5 stacks).` | [Fen](https://astralparty.miraheze.org/wiki/Fen) |
| 12 | `astral_dice.guide.entry.zhao_sign.3` | 散文引用 | `Interaction with the Boss Sign, "Hearts Linked": when the Boss Sign uses its active skill, every teammate wearing the Fengshui Master Sign gains 1 Blessing Talisman.` | `Interaction with the Fen Sign, "Hearts Linked": when the Fen Sign uses its active skill, every teammate wearing the Zhao Sign gains 1 Blessing Talisman.` | [Zhao](https://astralparty.miraheze.org/wiki/Zhao) |
| 13 | `item.astral_dice.bonnie_sign` | 立牌物品键 | `Secret Detective Sign` | `Bonnie Sign` | [Bonnie](https://astralparty.miraheze.org/wiki/Bonnie) |
| 14 | `item.astral_dice.fanny_sign` | 立牌物品键 | `Great Detective Sign` | `Fanny Sign` | [Fanny](https://astralparty.miraheze.org/wiki/Fanny) |
| 15 | `item.astral_dice.fen_sign` | 立牌物品键 | `Boss Sign` | `Fen Sign` | [Fen](https://astralparty.miraheze.org/wiki/Fen) |
| 16 | `item.astral_dice.haiqing_sign` | 立牌物品键 | `Astrologer Sign` | `HaiQing Sign` | [HaiQing](https://astralparty.miraheze.org/wiki/HaiQing) |
| 17 | `item.astral_dice.jasmine_sign` | 立牌物品键 | `Sweeper Sign` | `Jasmine Sign` | [Jasmine](https://astralparty.miraheze.org/wiki/Jasmine) |
| 18 | `item.astral_dice.komachi_sign` | 立牌物品键 | `Ninja Sign` | `Komachi Sign` | [Komachi](https://astralparty.miraheze.org/wiki/Komachi) |
| 19 | `item.astral_dice.lulu_sign` | 立牌物品键 | `Slime Sign` | `Lulu Sign` | [Lulu](https://astralparty.miraheze.org/wiki/Lulu) |
| 20 | `item.astral_dice.mimi_sign` | 立牌物品键 | `Kanban Musume Sign` | `Mimi Sign` | [Mimi](https://astralparty.miraheze.org/wiki/Mimi) |
| 21 | `item.astral_dice.misaki_sign` | 立牌物品键 | `Guardian Sign` | `Misaki Sign` | [Misaki](https://astralparty.miraheze.org/wiki/Misaki) |
| 22 | `item.astral_dice.moses_sign` | 立牌物品键 | `Gunsmith Sign` | `Moses Sign` | [Moses](https://astralparty.miraheze.org/wiki/Moses) |
| 23 | `item.astral_dice.nardis_sign` | 立牌物品键 | `Oasis Queen's Sign` | `Nardis Sign` | [Nardis](https://astralparty.miraheze.org/wiki/Nardis) |
| 24 | `item.astral_dice.papara_sign` | 立牌物品键 | `Vampire Sign` | `Papara Sign` | [Papara](https://astralparty.miraheze.org/wiki/Papara) |
| 25 | `item.astral_dice.parunan_sign` | 立牌物品键 | `Business Sign` | `Parunan Sign` | [Parunan](https://astralparty.miraheze.org/wiki/Parunan) |
| 26 | `item.astral_dice.ren_sign` | 立牌物品键 | `Game Master Sign` | `Ren Sign` | [Ren](https://astralparty.miraheze.org/wiki/Ren) |
| 27 | `item.astral_dice.rin_sign` | 立牌物品键 | `Investigator Sign` | `Rin Sign` | [Rin](https://astralparty.miraheze.org/wiki/Rin) |
| 28 | `item.astral_dice.teru_sign` | 立牌物品键 | `Cult Leader Sign` | `Teru Sign` | [Teru](https://astralparty.miraheze.org/wiki/Teru) |
| 29 | `item.astral_dice.zhao_sign` | 立牌物品键 | `Fengshui Master Sign` | `Zhao Sign` | [Zhao](https://astralparty.miraheze.org/wiki/Zhao) |
| 30 | `msg.astral_dice.haiqing_ready` | 散文引用 | `Astrologer sign: active skill activated — select a hostile target to apply the Weak Mark` | `HaiQing Sign: active skill activated — select a hostile target to apply the Weak Mark` | [HaiQing](https://astralparty.miraheze.org/wiki/HaiQing) |
| 31 | `msg.astral_dice.komachi_active` | 散文引用 | `Ninja sign: effect card play count +1 granted. Remaining plays: %d` | `Komachi Sign: effect card play count +1 granted. Remaining plays: %d` | [Komachi](https://astralparty.miraheze.org/wiki/Komachi) |
| 32 | `msg.astral_dice.mimi_active` | 散文引用 | `Kanban Musume Sign: got %d new cards and %d Star Coins` | `Mimi Sign: got %d new cards and %d Star Coins` | [Mimi](https://astralparty.miraheze.org/wiki/Mimi) |
| 33 | `msg.astral_dice.moses_apply` | 散文引用 | `Gunsmith sign: Broken applied` | `Moses Sign: Broken applied` | [Moses](https://astralparty.miraheze.org/wiki/Moses) |
| 34 | `msg.astral_dice.moses_ready` | 散文引用 | `Gunsmith sign: active skill activated — select a hostile target to apply Broken` | `Moses Sign: active skill activated — select a hostile target to apply Broken` | [Moses](https://astralparty.miraheze.org/wiki/Moses) |
| 35 | `msg.astral_dice.nardis_active` | 散文引用 | `Oasis Queen's Sign: got %d temporary card(s) (lasts 3:00)` | `Nardis Sign: got %d temporary card(s) (lasts 3:00)` | [Nardis](https://astralparty.miraheze.org/wiki/Nardis) |
| 36 | `msg.astral_dice.parunan_active_no_starlight` | 散文引用 | `Merchant Sign: no convertible starlight (starlight from bank cards cannot be converted), active skill not used` | `Parunan Sign: no convertible starlight (starlight from bank cards cannot be converted), active skill not used` | [Parunan](https://astralparty.miraheze.org/wiki/Parunan) |
| 37 | `msg.astral_dice.teru_descent_in_effect` | 散文引用 | `Cult Leader Sign: Descent is still active and cannot be recast` | `Teru Sign: Descent is still active and cannot be recast` | [Teru](https://astralparty.miraheze.org/wiki/Teru) |
| 38 | `msg.astral_dice.teru_ready` | 散文引用 | `Cult Leader Sign: choose another player to descend upon` | `Teru Sign: choose another player to descend upon` | [Teru](https://astralparty.miraheze.org/wiki/Teru) |
| 39 | `msg.astral_dice.zhao_blessing_ready` | 散文引用 | `Fengshui Master Sign: choose a target (a player or yourself)` | `Zhao Sign: choose a target (a player or yourself)` | [Zhao](https://astralparty.miraheze.org/wiki/Zhao) |
| 40 | `tooltip.astral_dice.sign.fen_xinyi` | 散文引用 | `Hearts Linked: when you use the active skill, every teammate wearing the Fengshui Master Sign gains §e1§7 Blessing Talisman` | `Hearts Linked: when you use the active skill, every teammate wearing the Zhao Sign gains §e1§7 Blessing Talisman` | [Zhao](https://astralparty.miraheze.org/wiki/Zhao) |
| 41 | `tooltip.astral_dice.sign.zhao_wanmei_bangshou` | 散文引用 | `Perfect Helper: applying Baize's Blessing to a player wearing the Boss Sign fills that player's Recharged Energy to the maximum (§e5§7 stacks)` | `Perfect Helper: applying Baize's Blessing to a player wearing the Fen Sign fills that player's Recharged Energy to the maximum (§e5§7 stacks)` | [Fen](https://astralparty.miraheze.org/wiki/Fen) |

## 6. 反向判据（证明门禁不是假绿）

把 fabric 的 `item.astral_dice.papara_sign` 临时改回旧职称直译 `Vampire Sign`：

```
$ python tools/verify_sign_display_names.py
== 立牌英日命名门禁 ==
权威表: tools/sign_display_names.json（32 条，updated 2026-10-26）
-- neoforge-1.21.1 -- zh 1070 keys / en 1070 keys / ja 1070 keys
-- forge-1.20.1 -- zh 1070 keys / en 1070 keys / ja 1070 keys
-- fabric-1.20.1 -- zh 1094 keys / en 1094 keys / ja 1094 keys
检查项: 74673
FAIL: 2 处不符
  - [fabric-1.20.1] item.astral_dice.papara_sign = 'Vampire Sign'，应为 'Papara Sign'
  - [fabric-1.20.1] en item.astral_dice.papara_sign 仍含旧职称命名 'Vampire' :: Vampire Sign
EXIT=1
```

- 注入后文件 sha256：`01183e32ef6ab66000d41a7ebfca01235342e5c151e3052259af466ddc2dc0fe`
- 恢复后文件 sha256：`3e49f9fda9d7f5bf4beed089fa7788943ff2030e993c716aadaadc048c9a9114` ⇒ **与订正完成时逐字节相同（复原成功）**

## 7. 验收原始输出

### (a) 三线门禁 PASS

```
$ python tools/verify_sign_display_names.py ; echo "EXIT=$?"
== 立牌英日命名门禁 ==
权威表: tools/sign_display_names.json（32 条，updated 2026-10-26）
-- neoforge-1.21.1 -- zh 1070 keys / en 1070 keys / ja 1070 keys
-- forge-1.20.1 -- zh 1070 keys / en 1070 keys / ja 1070 keys
-- fabric-1.20.1 -- zh 1094 keys / en 1094 keys / ja 1094 keys
检查项: 74673
GATE: PASS（立牌英日命名与权威表逐字一致，无旧职称残留）
EXIT=0
```

（输出里出现 **三条** `-- <线> --` 行；无 `[WARN]` zh 行，说明 fabric 的中文立牌名也与表一致。）

### (c) fabric 资产闭环

```
$ python tools/verify_fabric_assets.py ; echo "EXIT=$?"
jar = fabric-1.20.1\build\libs\astral_dice-1.3.7-alpha.2+fabric_1.20.1.jar

[!] build/libs 内 jar 版本 1.3.7-alpha.2+fabric_1.20.1 与 mod_version 1.5.0-rc.7+fabric_1.20.1 不符，改为扫描源码资源目录
[1] 物品闭环: 注册 157 (方块物品 3) / 模型 158 / 贴图 155 (方块贴图另计)
[2] 标签闭环: 引用 11 / 自建 29 / 有提供者 11
[3] 音效闭环: 注册 23 / sounds.json 23 / ogg 23
[4] 粒子闭环: 注册 2 / particles/*.json 2
[5] 语言闭环: zh_cn 1094 / en_us 1094 / ja_jp 1094
[6] 手册闭环: 条目 184 / 引用配方 147
[7] 创意标签覆盖: 156/157
    已知与基线一致（不阻塞）: ['repulsion']
[8] 饰品槽位图标: 检查 7 条 icon

=== PASS: 8 项闭环全部通过 ===
EXIT=0
```

（首两行 `[!]` 提示是**改前既有**的陈旧 jar 版本告警，与本次改动无关；脚本已回落到源码资源目录扫描。）

### (d) 语言键同步

```
$ python tools/check_lang_sync.py --line fabric-1.20.1 ; echo "EXIT=$?"
[OK]   fabric-1.20.1: en_us=1094 键，3 份语言键集一致
EXIT=0
```

### (e) 三语键数 + numstat

```
$ git diff --numstat -- tools/verify_sign_display_names.py tools/sign_display_names.json \
      fabric-1.20.1/src/main/resources/assets/astral_dice/lang/en_us.json \
      fabric-1.20.1/src/main/resources/assets/astral_dice/lang/ja_jp.json
298     50      fabric-1.20.1/src/main/resources/assets/astral_dice/lang/en_us.json
257     9       fabric-1.20.1/src/main/resources/assets/astral_dice/lang/ja_jp.json
2       2       tools/verify_sign_display_names.py
```

- `en_us.json` 由 `257/9` 变为 `298/50` ⇒ 本批净增 **+41 行改 / -41 行**（正好 41 条键，一进一出，无整文件重排）。
- `ja_jp.json` 仍是 `257/9` ⇒ **本批未动**（那 257/9 是上批遗留的未提交改动）。
- `tools/sign_display_names.json` **未出现在 numstat** ⇒ 本批未动（任务步骤 4 未触发）。
- 三语键数 `1094 / 1094 / 1094`（门禁与 `verify_fabric_assets` 两处独立印证，改前改后一致）。

## 8. 字节与行尾自检（改后实读）

| 文件 | 状态 | 行尾 | 字节 | sha256 |
|---|---|---|---|---|
| `fabric-1.20.1/.../en_us.json` | 已改（41 键） | CRLF 1096 / 裸 LF 0 / 裸 CR 0 / 无 BOM | 151,464 → 151,464 | `3e49f9fda9d7f5bf4beed089fa7788943ff2030e993c716aadaadc048c9a9114` |
| `fabric-1.20.1/.../ja_jp.json` | 未改 | CRLF 1096 / 裸 LF 0 / 无 BOM | 187,070 | `92d0db7ad0579a2a5e182d5e54b80340fed0a4c2b3b06e7461363b981795e935` |
| `tools/verify_sign_display_names.py` | 已改（2 行） | **LF**（CRLF 0 / 裸 LF 121） | 5,433 | `823d9271eb95596165b4728555cab7e89d66ca6eb43ee700f7805bef492b0ed2` |
| `tools/sign_display_names.json` | 未改 | LF | 6,501 | `93d1a003e6c988da5274a2b89794194e2cbbfa1fc8f121b9634063f4a50c1bc0` |

### 8.1 替换方式与完整性自检（脚本内断言，全部通过）

- **逐行替换**：只重写「键值对行」冒号后的 JSON 字符串字面量，前缀 `  "<key>": ` 与行尾逗号原样保留；未用 `json.dump` 重写。
- 断言 1：命中键数 = **41**，且处理前后 `len(cur)==1094`。
- 断言 2：`list(o.keys())==list(n.keys())` ⇒ **键序未变**。
- 断言 3：`CRLF` 计数前后相等（1096）⇒ **行尾未染色**。
- 断言 4：文件前 3 字节非 BOM。
- 断言 5：`{k for k in n if n[k]!=o[k]}` == 41 条目标键集合 ⇒ **无误伤其它键**。
- 断言 6：字节数 151,662 → 151,464（−198 B，均为 ASCII 名字替换的净差值）。

### 8.2 旧值来源的可复核性

报告表中的「旧值」列取自 `git show HEAD:fabric-1.20.1/.../en_us.json`，并已用脚本证明：对全部 41 条键，`HEAD 旧值` 仅做「旧职称→角色名」一次替换后 **逐字节等于** 当前工作区值（不一致条数 = 0）。因此表中旧值即改前工作区值，不是推测。

### 8.3 命名映射（20 个旧职称 → 20 个角色，1:1 无歧义）

`Secret Detective→Bonnie`、`Great Detective→Fanny`、`Boss→Fen`、`Astrologer→HaiQing`、`Sweeper→Jasmine`、`Ninja→Komachi`、`Slime→Lulu`、`Kanban Musume→Mimi`、`Guardian→Misaki`、`Gunsmith→Moses`、`Vampire→Papara`、`Business→Parunan`、`Merchant→Parunan`、`Investigator→Rin`、`Game Master→Ren`、`Fengshui Master→Zhao`、`Cult Leader→Teru`、`Oasis Queen→Nardis`、`Bartender→Jill`、`Sister→Alana`。

匹配一律带 `\b…(?:'s)? ?[Ss]ign\b` 词边界 ⇒ `Ninja Star`、`Legendary Merchant`、`Healing Slime` 等既有专名不受影响。

### 8.4 既有专名反查（确认未误伤）

改后 fabric `en_us.json` 中 `Ninja Star` / `Legendary Merchant` 出现次数与改前一致（均为筹码/技能名，非立牌命名）；`Boss` 现仅剩「敌人泛指」用法（如 `Bosses`、`boss fight`），无 `Boss Sign` 形态。

## 9. 无法判定清单

**无。**

- 32 个立牌 id 全部在权威表中有 `wiki_page`（除 `blank`、`deepseek` 两个非角色条目，表内 `note` 已注明「通用基材」「本模组自制形象」）。
- 未出现「角色无官方英文名」的情形，未做任何机翻。

## 10. 未改动声明（红线自查）

- **未改**：`zh_cn.json`（三线均未动）、任何 `.java`、recipes、patchouli、build 文件。
- **未改**：`tools/verify_fabric_assets.py`、`tools/check_lang_sync.py`（仅只读调用）、`tools/sign_names_from_wiki.py`（仅只读调用）。
- **未改**：`tools/sign_display_names.json`（本轮无需增补）。
- **未执行**：`git commit` / `push` / `stash` / `add` / `checkout`；未跑 gradle；未启动游戏。
- 工作区中其它 `M` 文件（大量 `fabric-1.20.1/src/main/java/**`、`zh_cn.json`、`ja_jp.json` 等）均为**本次之前既存**的未提交改动，本批未触碰。
