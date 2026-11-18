# B33 — fabric-1.20.1 帕秋莉手册结构对齐 neoforge-1.21.1 主基准

- 作业日期：2026-11-01
- 写域：`fabric-1.20.1/src/main/resources/assets/astral_dice/patchouli_books/**`
- 蓝本（唯一真相源）：`neoforge-1.21.1/src/main/resources/assets/astral_dice/patchouli_books/**`
- 未触碰：`forge-1.20.1` / `neoforge-1.21.1` / `neoforge-26.1.2`、三语 lang json、java 源码、build 配置、accesswidener；未做任何 git 写操作；未启动 Minecraft。

## 0. 结论

| 指标 | 改前 | 改后 |
|---|---|---|
| 变更文件 | — | **87 个修改 / 0 新增 / 0 删除**（fabric 手册 184 个 json 中 97 个无改动） |
| `pages[].type` 序列不同的文件数（fabric vs neo） | 87 | **0** |
| `pages` 内容不同的文件数（fabric vs neo） | 87 | **0** |
| 死配方页数量 | 22 | **0** |
| 全仓页型统计 | spotlight 164 / crafting 142 / text 110 | spotlight 164 / crafting 59 / text 167（与主基准完全一致） |
| 手册 lang 键引用（fabric） | 473 处 | 530 处（新增 61 处 `chip_source.note`，移除 4 处 `…sign.3`） |
| 手册 `book_texture`（`data/astral_dice/patchouli_books/astral_guide/book.json`） | `patchouli:textures/gui/book_brown.png` | **未改**（在本次写域之外；另三条线都是 `astral_dice:textures/gui/book_pink.png`，见 7.7） |

两条守门结论：`python tools/audit_patchouli_keys.py` → `✅ 无悬空引用。`；`python tools/verify_fabric_assets.py` → `=== PASS: 8 项闭环全部通过 ===`；`python tools/check_lang_sync.py` → 四线全 `[OK]`。原始末段见第 6 节。

## 1. 对任务简报中三处前提的订正（均附证据）

**1.1 语言目录不是「两套等长镜像」。** 两侧都只有 `en_us`（179 个）+ `zh_cn`（5 个）。`zh_cn` 只有 `entries/signs/{alana,deepseek,dorothy,jill,megas}_sign.json` 五个文件，且这 5 个文件在 fabric 与 neo 上的 `pages` **完全相同（0 处差异）**。因此「zh_cn 与 en_us 两套都必须处理」在本批的实际结果是：zh_cn 零改动，87 处差异全部在 en_us。这不是漏做，是两侧 zh_cn 覆盖面本来就相同。

**1.2「fabric 只有 17 个配方」是假阴性。** `fabric-1.20.1/build.gradle:29-33` 把 `src/generated/resources` 加进了 sourceSets，datagen 产物落在 `fabric-1.20.1/src/generated/resources/data/astral_dice/recipes/`（108 个）。按 fabric 目录名（复数 `recipes`）计算的有效配方 id 集合：

| 侧 | main | generated | 合计 |
|---|---|---|---|
| fabric-1.20.1 | 17（`data/astral_dice/recipes/`） | 108（`data/astral_dice/recipes/`） | **125** |
| neoforge-1.21.1 | 17（`data/astral_dice/recipe/`） | 108（`data/astral_dice/recipe/`） | **125** |

两侧集合**逐一相同**（fabric 独有 0 / neo 独有 0）。判据脚本 `tools/verify_fabric_assets.py:322-360`（`check_manual`）采的正是同一口径（`src/{generated,main}/resources/data/astral_dice/{recipes,recipe}`），所以「手册引用的配方是否真实存在」这条判据在 fabric 侧是可信的。

**1.3 死配方页确实存在，但位置与简报描述不同。** 改前 fabric 有 **22 个死配方页**，全部集中在 **22 张卡牌条目的最后一页**（`patchouli:crafting` 指向 `astral_dice:attack_card_*` / `defense_card_*` / `effect_card_*`，这 22 个 id 在 fabric 的 125 个配方里完全不存在）。而 61 个 chip 条目的 crafting 页指向的 `astral_dice:<chip>` 配方**全都真实存在**——它们不是死页，只是主线在 `a685d8d5` 把「配方页」统一换成了「获取方式说明」文本页。因此：**22 处是真实缺陷，61 处是信息结构差异**；两类都随本次对齐消失。

## 2. 对齐规则（页型替换规则）

| 规则 | 适用文件数 | 改前 | 改后 | 页数变化 | 说明 |
|---|---|---|---|---|---|
| **A** 同位置替换配方页 | 61 | `{"type":"patchouli:crafting","recipe":"astral_dice:<id>"}` | `{"type":"patchouli:text","text":"astral_dice.guide.chip_source.note"}` | 不变 | 61 个文件全部映射到同一个 lang 键，该键在 fabric 三语中均已存在 |
| **B1** 删除末尾配方页 | 22 | 末页 `crafting`（且为死配方页） | 无（条目只剩 spotlight 页） | −1 | 卡牌条目；主线已改为只保留 spotlight |
| **B2** 删除中间文本页 | 4 | 索引 2 的 `text(…sign.3)` | 无 | −1 | 立牌条目 hanna / nardis / sherry / teru；**删的是文本页不是配方页** |

规则 A 中配方页不在索引 1 的 5 个文件：`chips_charge/current_core_chip.json`[2]、`chips_other/adrenaline_high_chip.json`[2]、`chips_other/cursed_sword_chip.json`[3]、`chips_other/revenge_halberd_chip.json`[3]、`chips_other/satellite_chip.json`[3]。

**落地方式（为什么不是 `json.dump`）**：没有重排整份文件。每个文件只把 `"pages": [ … ]` 这一段的**原文区间**摘掉，换成蓝本同文件同段的**原文区间**（逐字节照抄蓝本，连 2/4/6 空格缩进与字段顺序一起继承）；文件前缀（`name`/`category`/`icon`）与后缀（`sortnum` 及尾行状态）保持 fabric 原字节不动。因此：

- 每个改后文件的 `pages` 段与蓝本**逐字节相同**；
- 由于两侧非 `pages` 字段本就完全相同，**87 个文件改后与蓝本整体逐字节一致**（已用 `改后文本 == 蓝本文本` 断言 87/87）；
- 语义断言：`json.loads(改后) == json.loads(蓝本)` 87/87 通过。

## 3. 逐文件差异表（87 行，按规则分组）

规则 A 61 行 → 规则 B1 22 行 → 规则 B2 4 行。列「改前/改后」只列**发生变化的那一页或那几页**，未列出的页两侧一致。

| # | 文件（`astral_guide/` 下相对路径） | 页数 改前→改后 | 变化页索引 | 改前 | 改后 |
|---|---|---|---|---|---|
| 1 | `astral_guide/en_us/entries/chips_charge/advanced_peripherals_chip.json` | 2→2 | 1 | crafting(astral_dice:advanced_peripherals_chip) | text(astral_dice.guide.chip_source.note) |
| 2 | `astral_guide/en_us/entries/chips_charge/airbag_chip.json` | 2→2 | 1 | crafting(astral_dice:airbag_chip) | text(astral_dice.guide.chip_source.note) |
| 3 | `astral_guide/en_us/entries/chips_charge/current_core_chip.json` | 3→3 | 2 | crafting(astral_dice:current_core_chip) | text(astral_dice.guide.chip_source.note) |
| 4 | `astral_guide/en_us/entries/chips_charge/electric_glove_chip.json` | 2→2 | 1 | crafting(astral_dice:electric_glove_chip) | text(astral_dice.guide.chip_source.note) |
| 5 | `astral_guide/en_us/entries/chips_charge/electric_sword_chip.json` | 2→2 | 1 | crafting(astral_dice:electric_sword_chip) | text(astral_dice.guide.chip_source.note) |
| 6 | `astral_guide/en_us/entries/chips_charge/energy_recycler_chip.json` | 2→2 | 1 | crafting(astral_dice:energy_recycler_chip) | text(astral_dice.guide.chip_source.note) |
| 7 | `astral_guide/en_us/entries/chips_charge/perpetual_motion_chip.json` | 2→2 | 1 | crafting(astral_dice:perpetual_motion_chip) | text(astral_dice.guide.chip_source.note) |
| 8 | `astral_guide/en_us/entries/chips_charge/primordial_core_chip.json` | 2→2 | 1 | crafting(astral_dice:primordial_core_chip) | text(astral_dice.guide.chip_source.note) |
| 9 | `astral_guide/en_us/entries/chips_charge/railgun_chip.json` | 2→2 | 1 | crafting(astral_dice:railgun_chip) | text(astral_dice.guide.chip_source.note) |
| 10 | `astral_guide/en_us/entries/chips_charge/warp_engine_chip.json` | 2→2 | 1 | crafting(astral_dice:warp_engine_chip) | text(astral_dice.guide.chip_source.note) |
| 11 | `astral_guide/en_us/entries/chips_healing/big_bowl_stew_chip.json` | 2→2 | 1 | crafting(astral_dice:big_bowl_stew_chip) | text(astral_dice.guide.chip_source.note) |
| 12 | `astral_guide/en_us/entries/chips_healing/buffer_shield_chip.json` | 2→2 | 1 | crafting(astral_dice:buffer_shield_chip) | text(astral_dice.guide.chip_source.note) |
| 13 | `astral_guide/en_us/entries/chips_healing/candy_chip.json` | 2→2 | 1 | crafting(astral_dice:candy_chip) | text(astral_dice.guide.chip_source.note) |
| 14 | `astral_guide/en_us/entries/chips_healing/cutter_blade_chip.json` | 2→2 | 1 | crafting(astral_dice:cutter_blade_chip) | text(astral_dice.guide.chip_source.note) |
| 15 | `astral_guide/en_us/entries/chips_healing/cutter_chip.json` | 2→2 | 1 | crafting(astral_dice:cutter_chip) | text(astral_dice.guide.chip_source.note) |
| 16 | `astral_guide/en_us/entries/chips_healing/friendship_badge_chip.json` | 2→2 | 1 | crafting(astral_dice:friendship_badge_chip) | text(astral_dice.guide.chip_source.note) |
| 17 | `astral_guide/en_us/entries/chips_healing/medkit_complete_chip.json` | 2→2 | 1 | crafting(astral_dice:medkit_complete_chip) | text(astral_dice.guide.chip_source.note) |
| 18 | `astral_guide/en_us/entries/chips_healing/medkit_emergency_chip.json` | 2→2 | 1 | crafting(astral_dice:medkit_emergency_chip) | text(astral_dice.guide.chip_source.note) |
| 19 | `astral_guide/en_us/entries/chips_healing/vitamin_pill_chip.json` | 2→2 | 1 | crafting(astral_dice:vitamin_pill_chip) | text(astral_dice.guide.chip_source.note) |
| 20 | `astral_guide/en_us/entries/chips_mark/eagle_scope_chip.json` | 2→2 | 1 | crafting(astral_dice:eagle_scope_chip) | text(astral_dice.guide.chip_source.note) |
| 21 | `astral_guide/en_us/entries/chips_mark/hand_fan_big_chip.json` | 2→2 | 1 | crafting(astral_dice:hand_fan_big_chip) | text(astral_dice.guide.chip_source.note) |
| 22 | `astral_guide/en_us/entries/chips_mark/hand_fan_small_chip.json` | 2→2 | 1 | crafting(astral_dice:hand_fan_small_chip) | text(astral_dice.guide.chip_source.note) |
| 23 | `astral_guide/en_us/entries/chips_mark/magic_quiver_chip.json` | 2→2 | 1 | crafting(astral_dice:magic_quiver_chip) | text(astral_dice.guide.chip_source.note) |
| 24 | `astral_guide/en_us/entries/chips_mark/marker_sprayer_chip.json` | 2→2 | 1 | crafting(astral_dice:marker_sprayer_chip) | text(astral_dice.guide.chip_source.note) |
| 25 | `astral_guide/en_us/entries/chips_mark/ninja_star_chip.json` | 2→2 | 1 | crafting(astral_dice:ninja_star_chip) | text(astral_dice.guide.chip_source.note) |
| 26 | `astral_guide/en_us/entries/chips_mark/scope_chip.json` | 2→2 | 1 | crafting(astral_dice:scope_chip) | text(astral_dice.guide.chip_source.note) |
| 27 | `astral_guide/en_us/entries/chips_mark/target_chip.json` | 2→2 | 1 | crafting(astral_dice:target_chip) | text(astral_dice.guide.chip_source.note) |
| 28 | `astral_guide/en_us/entries/chips_other/adrenaline_high_chip.json` | 3→3 | 2 | crafting(astral_dice:adrenaline_high_chip) | text(astral_dice.guide.chip_source.note) |
| 29 | `astral_guide/en_us/entries/chips_other/adrenaline_low_chip.json` | 2→2 | 1 | crafting(astral_dice:adrenaline_low_chip) | text(astral_dice.guide.chip_source.note) |
| 30 | `astral_guide/en_us/entries/chips_other/big_backpack_chip.json` | 2→2 | 1 | crafting(astral_dice:big_backpack_chip) | text(astral_dice.guide.chip_source.note) |
| 31 | `astral_guide/en_us/entries/chips_other/bookmark_chip.json` | 2→2 | 1 | crafting(astral_dice:bookmark_chip) | text(astral_dice.guide.chip_source.note) |
| 32 | `astral_guide/en_us/entries/chips_other/boxing_gloves_high_chip.json` | 2→2 | 1 | crafting(astral_dice:boxing_gloves_high_chip) | text(astral_dice.guide.chip_source.note) |
| 33 | `astral_guide/en_us/entries/chips_other/boxing_gloves_low_chip.json` | 2→2 | 1 | crafting(astral_dice:boxing_gloves_low_chip) | text(astral_dice.guide.chip_source.note) |
| 34 | `astral_guide/en_us/entries/chips_other/boxing_gloves_medium_chip.json` | 2→2 | 1 | crafting(astral_dice:boxing_gloves_medium_chip) | text(astral_dice.guide.chip_source.note) |
| 35 | `astral_guide/en_us/entries/chips_other/cursed_sword_chip.json` | 4→4 | 3 | crafting(astral_dice:cursed_sword_chip) | text(astral_dice.guide.chip_source.note) |
| 36 | `astral_guide/en_us/entries/chips_other/magic_tome_chip.json` | 2→2 | 1 | crafting(astral_dice:magic_tome_chip) | text(astral_dice.guide.chip_source.note) |
| 37 | `astral_guide/en_us/entries/chips_other/member_recommendation_chip.json` | 2→2 | 1 | crafting(astral_dice:member_recommendation_chip) | text(astral_dice.guide.chip_source.note) |
| 38 | `astral_guide/en_us/entries/chips_other/moto_helmet_high_chip.json` | 2→2 | 1 | crafting(astral_dice:moto_helmet_high_chip) | text(astral_dice.guide.chip_source.note) |
| 39 | `astral_guide/en_us/entries/chips_other/moto_helmet_low_chip.json` | 2→2 | 1 | crafting(astral_dice:moto_helmet_low_chip) | text(astral_dice.guide.chip_source.note) |
| 40 | `astral_guide/en_us/entries/chips_other/moto_helmet_medium_chip.json` | 2→2 | 1 | crafting(astral_dice:moto_helmet_medium_chip) | text(astral_dice.guide.chip_source.note) |
| 41 | `astral_guide/en_us/entries/chips_other/piercing_gun_chip.json` | 2→2 | 1 | crafting(astral_dice:piercing_gun_chip) | text(astral_dice.guide.chip_source.note) |
| 42 | `astral_guide/en_us/entries/chips_other/piggy_bank_chip.json` | 2→2 | 1 | crafting(astral_dice:piggy_bank_chip) | text(astral_dice.guide.chip_source.note) |
| 43 | `astral_guide/en_us/entries/chips_other/revenge_halberd_chip.json` | 4→4 | 3 | crafting(astral_dice:revenge_halberd_chip) | text(astral_dice.guide.chip_source.note) |
| 44 | `astral_guide/en_us/entries/chips_other/sandwich_high_chip.json` | 2→2 | 1 | crafting(astral_dice:sandwich_high_chip) | text(astral_dice.guide.chip_source.note) |
| 45 | `astral_guide/en_us/entries/chips_other/sandwich_low_chip.json` | 2→2 | 1 | crafting(astral_dice:sandwich_low_chip) | text(astral_dice.guide.chip_source.note) |
| 46 | `astral_guide/en_us/entries/chips_other/sandwich_medium_chip.json` | 2→2 | 1 | crafting(astral_dice:sandwich_medium_chip) | text(astral_dice.guide.chip_source.note) |
| 47 | `astral_guide/en_us/entries/chips_other/satellite_chip.json` | 4→4 | 3 | crafting(astral_dice:satellite_chip) | text(astral_dice.guide.chip_source.note) |
| 48 | `astral_guide/en_us/entries/chips_other/smart_watch_chip.json` | 2→2 | 1 | crafting(astral_dice:smart_watch_chip) | text(astral_dice.guide.chip_source.note) |
| 49 | `astral_guide/en_us/entries/chips_other/speed_skates_high_chip.json` | 2→2 | 1 | crafting(astral_dice:speed_skates_high_chip) | text(astral_dice.guide.chip_source.note) |
| 50 | `astral_guide/en_us/entries/chips_other/speed_skates_low_chip.json` | 2→2 | 1 | crafting(astral_dice:speed_skates_low_chip) | text(astral_dice.guide.chip_source.note) |
| 51 | `astral_guide/en_us/entries/chips_other/speed_skates_medium_chip.json` | 2→2 | 1 | crafting(astral_dice:speed_skates_medium_chip) | text(astral_dice.guide.chip_source.note) |
| 52 | `astral_guide/en_us/entries/chips_other/whetstone_chip.json` | 2→2 | 1 | crafting(astral_dice:whetstone_chip) | text(astral_dice.guide.chip_source.note) |
| 53 | `astral_guide/en_us/entries/chips_starlight/atm_chip.json` | 2→2 | 1 | crafting(astral_dice:atm_chip) | text(astral_dice.guide.chip_source.note) |
| 54 | `astral_guide/en_us/entries/chips_starlight/bank_card_high_chip.json` | 2→2 | 1 | crafting(astral_dice:bank_card_high_chip) | text(astral_dice.guide.chip_source.note) |
| 55 | `astral_guide/en_us/entries/chips_starlight/bank_card_low_chip.json` | 2→2 | 1 | crafting(astral_dice:bank_card_low_chip) | text(astral_dice.guide.chip_source.note) |
| 56 | `astral_guide/en_us/entries/chips_starlight/bank_card_unlimited_chip.json` | 2→2 | 1 | crafting(astral_dice:bank_card_unlimited_chip) | text(astral_dice.guide.chip_source.note) |
| 57 | `astral_guide/en_us/entries/chips_starlight/eight_sided_dice_chip.json` | 2→2 | 1 | crafting(astral_dice:eight_sided_dice_chip) | text(astral_dice.guide.chip_source.note) |
| 58 | `astral_guide/en_us/entries/chips_starlight/flashlight_chip.json` | 2→2 | 1 | crafting(astral_dice:flashlight_chip) | text(astral_dice.guide.chip_source.note) |
| 59 | `astral_guide/en_us/entries/chips_starlight/golden_shooting_star_chip.json` | 2→2 | 1 | crafting(astral_dice:golden_shooting_star_chip) | text(astral_dice.guide.chip_source.note) |
| 60 | `astral_guide/en_us/entries/chips_starlight/purple_shooting_star_chip.json` | 2→2 | 1 | crafting(astral_dice:purple_shooting_star_chip) | text(astral_dice.guide.chip_source.note) |
| 61 | `astral_guide/en_us/entries/chips_starlight/star_coin_hammer_chip.json` | 2→2 | 1 | crafting(astral_dice:star_coin_hammer_chip) | text(astral_dice.guide.chip_source.note) |
| 62 | `astral_guide/en_us/entries/cards_attack/attack_card_charge.json` | 2→1 | 1 | crafting(astral_dice:attack_card_charge) | （删除） |
| 63 | `astral_guide/en_us/entries/cards_attack/attack_card_epic.json` | 2→1 | 1 | crafting(astral_dice:attack_card_epic_from_large) | （删除） |
| 64 | `astral_guide/en_us/entries/cards_attack/attack_card_large.json` | 2→1 | 1 | crafting(astral_dice:attack_card_large_from_medium) | （删除） |
| 65 | `astral_guide/en_us/entries/cards_attack/attack_card_medium.json` | 2→1 | 1 | crafting(astral_dice:attack_card_medium) | （删除） |
| 66 | `astral_guide/en_us/entries/cards_attack/attack_card_meito.json` | 2→1 | 1 | crafting(astral_dice:attack_card_meito) | （删除） |
| 67 | `astral_guide/en_us/entries/cards_attack/attack_card_shadow_strike.json` | 2→1 | 1 | crafting(astral_dice:attack_card_shadow_strike) | （删除） |
| 68 | `astral_guide/en_us/entries/cards_defense/defense_card_epic.json` | 2→1 | 1 | crafting(astral_dice:defense_card_epic_from_large) | （删除） |
| 69 | `astral_guide/en_us/entries/cards_defense/defense_card_large.json` | 2→1 | 1 | crafting(astral_dice:defense_card_large_from_medium) | （删除） |
| 70 | `astral_guide/en_us/entries/cards_defense/defense_card_medium.json` | 2→1 | 1 | crafting(astral_dice:defense_card_medium) | （删除） |
| 71 | `astral_guide/en_us/entries/cards_effect/effect_card_berserk.json` | 2→1 | 1 | crafting(astral_dice:effect_card_berserk) | （删除） |
| 72 | `astral_guide/en_us/entries/cards_effect/effect_card_chocolate_cake.json` | 2→1 | 1 | crafting(astral_dice:effect_card_chocolate_cake) | （删除） |
| 73 | `astral_guide/en_us/entries/cards_effect/effect_card_directional_blast.json` | 2→1 | 1 | crafting(astral_dice:effect_card_directional_blast) | （删除） |
| 74 | `astral_guide/en_us/entries/cards_effect/effect_card_express_delivery.json` | 2→1 | 1 | crafting(astral_dice:effect_card_express_delivery) | （删除） |
| 75 | `astral_guide/en_us/entries/cards_effect/effect_card_fight_poison_with_poison.json` | 2→1 | 1 | crafting(astral_dice:effect_card_fight_poison_with_poison) | （删除） |
| 76 | `astral_guide/en_us/entries/cards_effect/effect_card_hamburger.json` | 2→1 | 1 | crafting(astral_dice:effect_card_hamburger) | （删除） |
| 77 | `astral_guide/en_us/entries/cards_effect/effect_card_king_power.json` | 2→1 | 1 | crafting(astral_dice:effect_card_king_power) | （删除） |
| 78 | `astral_guide/en_us/entries/cards_effect/effect_card_luxury_feast.json` | 2→1 | 1 | crafting(astral_dice:effect_card_luxury_feast) | （删除） |
| 79 | `astral_guide/en_us/entries/cards_effect/effect_card_monster_brick.json` | 2→1 | 1 | crafting(astral_dice:effect_card_monster_brick) | （删除） |
| 80 | `astral_guide/en_us/entries/cards_effect/effect_card_monster_laser.json` | 2→1 | 1 | crafting(astral_dice:effect_card_monster_laser) | （删除） |
| 81 | `astral_guide/en_us/entries/cards_effect/effect_card_orbital_strike.json` | 2→1 | 1 | crafting(astral_dice:effect_card_orbital_strike) | （删除） |
| 82 | `astral_guide/en_us/entries/cards_effect/effect_card_unwavering.json` | 2→1 | 1 | crafting(astral_dice:effect_card_unwavering) | （删除） |
| 83 | `astral_guide/en_us/entries/cards_effect/effect_card_you_have_i_have.json` | 2→1 | 1 | crafting(astral_dice:effect_card_you_have_i_have) | （删除） |
| 84 | `astral_guide/en_us/entries/signs/hanna_sign.json` | 4→3 | 2 | text(astral_dice.guide.entry.hanna_sign.3) | （删除） |
| 85 | `astral_guide/en_us/entries/signs/nardis_sign.json` | 4→3 | 2 | text(astral_dice.guide.entry.nardis_sign.3) | （删除） |
| 86 | `astral_guide/en_us/entries/signs/sherry_sign.json` | 4→3 | 2 | text(astral_dice.guide.entry.sherry_sign.3) | （删除） |
| 87 | `astral_guide/en_us/entries/signs/teru_sign.json` | 4→3 | 2 | text(astral_dice.guide.entry.teru_sign.3) | （删除） |


## 4. 平台差异例外表

**空表（无例外项）。**

理由：本次唯一可能触发例外的是任务简报里「若某页引用了 fabric 侧不存在的 recipe，则不要照抄成死页」这一条。实测结论是该情形**在两份页面集合上都不成立**：

- 蓝本（neo）59 个 crafting 页引用的配方，在 fabric 的 125 个有效配方里**全部存在**（缺失集合为空）；
- 改后 fabric 的 59 个 crafting 页与蓝本逐页相同，因此同样**全部存在**；
- 改前 fabric 的 142 个 crafting 页中，缺失的 22 个（`attack_card_*` / `defense_card_*` / `effect_card_*`）恰好就是规则 B1 删除的 22 页——主线本就把它们删了，不存在「照抄成死页」的风险。

所以不需要任何「保留 spotlight 页 / 换成等价 text 页」的平台适配变通；任何此类变通都会**偏离**主基准，反而引入差异。

## 5. 死配方页清单（改前 / 改后）

判据：扫描 fabric 手册全部 `patchouli:crafting` 页的 `recipe` id，与 fabric `src/{main,generated}/resources/data/astral_dice/{recipes,recipe}/*.json` 的实际 id 集合（125 个）求差。

**改前：22 个死配方页**（全部为卡牌条目的末页；22 个 id 互不相同，均不在 fabric 配方集合内）

| # | 文件（`astral_guide/` 下相对路径） | 页索引 | 引用配方 |
|---|---|---|---|
| 1 | `astral_guide/astral_guide/en_us/entries/cards_attack/attack_card_charge.json` | 1 | `astral_dice:attack_card_charge` |
| 2 | `astral_guide/astral_guide/en_us/entries/cards_attack/attack_card_epic.json` | 1 | `astral_dice:attack_card_epic_from_large` |
| 3 | `astral_guide/astral_guide/en_us/entries/cards_attack/attack_card_large.json` | 1 | `astral_dice:attack_card_large_from_medium` |
| 4 | `astral_guide/astral_guide/en_us/entries/cards_attack/attack_card_medium.json` | 1 | `astral_dice:attack_card_medium` |
| 5 | `astral_guide/astral_guide/en_us/entries/cards_attack/attack_card_meito.json` | 1 | `astral_dice:attack_card_meito` |
| 6 | `astral_guide/astral_guide/en_us/entries/cards_attack/attack_card_shadow_strike.json` | 1 | `astral_dice:attack_card_shadow_strike` |
| 7 | `astral_guide/astral_guide/en_us/entries/cards_defense/defense_card_epic.json` | 1 | `astral_dice:defense_card_epic_from_large` |
| 8 | `astral_guide/astral_guide/en_us/entries/cards_defense/defense_card_large.json` | 1 | `astral_dice:defense_card_large_from_medium` |
| 9 | `astral_guide/astral_guide/en_us/entries/cards_defense/defense_card_medium.json` | 1 | `astral_dice:defense_card_medium` |
| 10 | `astral_guide/astral_guide/en_us/entries/cards_effect/effect_card_berserk.json` | 1 | `astral_dice:effect_card_berserk` |
| 11 | `astral_guide/astral_guide/en_us/entries/cards_effect/effect_card_chocolate_cake.json` | 1 | `astral_dice:effect_card_chocolate_cake` |
| 12 | `astral_guide/astral_guide/en_us/entries/cards_effect/effect_card_directional_blast.json` | 1 | `astral_dice:effect_card_directional_blast` |
| 13 | `astral_guide/astral_guide/en_us/entries/cards_effect/effect_card_express_delivery.json` | 1 | `astral_dice:effect_card_express_delivery` |
| 14 | `astral_guide/astral_guide/en_us/entries/cards_effect/effect_card_fight_poison_with_poison.json` | 1 | `astral_dice:effect_card_fight_poison_with_poison` |
| 15 | `astral_guide/astral_guide/en_us/entries/cards_effect/effect_card_hamburger.json` | 1 | `astral_dice:effect_card_hamburger` |
| 16 | `astral_guide/astral_guide/en_us/entries/cards_effect/effect_card_king_power.json` | 1 | `astral_dice:effect_card_king_power` |
| 17 | `astral_guide/astral_guide/en_us/entries/cards_effect/effect_card_luxury_feast.json` | 1 | `astral_dice:effect_card_luxury_feast` |
| 18 | `astral_guide/astral_guide/en_us/entries/cards_effect/effect_card_monster_brick.json` | 1 | `astral_dice:effect_card_monster_brick` |
| 19 | `astral_guide/astral_guide/en_us/entries/cards_effect/effect_card_monster_laser.json` | 1 | `astral_dice:effect_card_monster_laser` |
| 20 | `astral_guide/astral_guide/en_us/entries/cards_effect/effect_card_orbital_strike.json` | 1 | `astral_dice:effect_card_orbital_strike` |
| 21 | `astral_guide/astral_guide/en_us/entries/cards_effect/effect_card_unwavering.json` | 1 | `astral_dice:effect_card_unwavering` |
| 22 | `astral_guide/astral_guide/en_us/entries/cards_effect/effect_card_you_have_i_have.json` | 1 | `astral_dice:effect_card_you_have_i_have` |

**改后：0 个死配方页**（crafting 页总数 59，全部命中真实配方）。

## 6. 四条守门的原始末段

### 6.1 `python tools/audit_patchouli_keys.py`

```text
=== neoforge-1.21.1 ===
   手册引用 530 处，全部命中三语 lang ✓
=== forge-1.20.1 ===
   手册引用 530 处，全部命中三语 lang ✓
=== neoforge-26.1.2 ===
   手册引用 467 处，全部命中三语 lang ✓
=== fabric-1.20.1 ===
   手册引用 530 处，全部命中三语 lang ✓
--------------------------------------------------------------------
合计：引用 2057 处，悬空 0 处
✅ 无悬空引用。
exit=0
```

### 6.2 `python tools/verify_fabric_assets.py`

```text
jar = fabric-1.20.1\build\libs\astral_dice-1.5.0-rc.7+fabric_1.20.1.jar

[1] 物品闭环: 注册 157 (方块物品 3) / 模型 158 / 贴图 155 (方块贴图另计)
[2] 标签闭环: 引用 10 / 自建 29 / 有提供者 10
[3] 音效闭环: 注册 23 / sounds.json 23 / ogg 23
[4] 粒子闭环: 注册 2 / particles/*.json 2
[5] 语言闭环: zh_cn 1094 / en_us 1094 / ja_jp 1094
[6] 手册闭环: 条目 184 / 引用配方 125
[7] 创意标签覆盖: 156/157
    已知与基线一致（不阻塞）: ['repulsion']
[8] 饰品槽位图标: 检查 7 条 icon

=== PASS: 8 项闭环全部通过 ===
exit=0
```

### 6.3 `python tools/check_lang_sync.py`

```text
[OK]   neoforge-1.21.1: en_us=1070 键，3 份语言键集一致
[OK]   forge-1.20.1: en_us=1070 键，3 份语言键集一致
[OK]   neoforge-26.1.2: en_us=1094 键，3 份语言键集一致
[OK]   fabric-1.20.1: en_us=1094 键，3 份语言键集一致
exit=0
```

> 说明：该脚本的中文提示在 MSYS 捕获下会显示为乱码（GBK/UTF-8 编码错配，纯显示问题）。上面的输出是用 `PYTHONUTF8=1` 重跑后的原文，四线键数与我改动前的基线完全相同（本批未改任何 lang）。

### 6.4 临时校验脚本（`scripts/b33_verify_patchouli.py`，已按要求删除）

```text
=== [1] pages[].type 序列差异 ===
fabric 文件 184 / neo 文件 184 / 集合相同: True
pages[].type 序列不同的文件数: 0
pages 内容不同的文件数: 0

=== [2] 死配方页扫描 ===
fabric 有效配方 id 数: 125
fabric 手册 crafting 页总数: 59
死配方页数量: 0

=== [3] 行尾 / BOM 自检（fabric 全部 184 个） ===
CRLF>0 且 bareLF==0 且 无BOM 的文件数: 184/184
末尾有换行 / 无换行: 169 / 15

RESULT: OK 差异 0 / 死配方页 0 / 行尾洁净
exit=0
```

## 7. 无法判定 / 需裁决项

**7.1 三个分类文件的 `sortnum` 不一致（超出 pages 对齐范围，未改）。**

| 文件 | fabric | neo | 备注 |
|---|---|---|---|
| `astral_guide/en_us/categories/cards.json` | `sortnum: 3` | `sortnum: 4` | fabric 侧 cards 与 blocks 的 sortnum 撞号（都是 3） |
| `astral_guide/en_us/categories/chips.json` | `sortnum: 4` | `sortnum: 5` | |
| `astral_guide/en_us/categories/signs.json` | `sortnum: 5` | `sortnum: 6` | |

其余 12 个分类文件两侧一致（`getting_started=0 / materials=1 / dice=2 / blocks=3 / cards_attack=31 / cards_defense=32 / cards_effect=33 / chips_healing=42 / chips_mark=43 / chips_starlight=41 / chips_charge=44 / chips_other=45`）。本任务只要求对齐 `pages`，改 `sortnum` 会改变手册分类的呈现顺序，属于设计裁决，故**未动**。若按「一律以最新主线为准」应改成 4/5/6；请裁决后另行下单（改动量 3 行）。

**7.2 蓝本侧自身存在两处行尾异常（未跟抄）。** `neoforge-1.21.1` 的 `entries/signs/megas_sign.json`（`en_us` 与 `zh_cn` 各一份）是**纯 LF**（无任何 CRLF），其余 182 个 neo 文件都是 CRLF。这 2 个文件与 fabric 对应文件**内容完全相同**，只差行尾。按「fabric 保 CRLF」的要求我没有把它们改成 LF，也没有在 fabric 侧引入任何裸 LF。

**7.3 作业期间工作树存在并发写入（不是我的改动）。** 20:00–20:23 之间，`fabric-1.20.1/src` 下有 12 个**非 patchouli** 文件被其他进程写入：`lang/{zh_cn,en_us,ja_jp}.json`、`datagen/ModRecipeProvider.java`、`mixin/trade/{MerchantContainerMixin,MerchantMenuMixin,ServerPlayerMixin,MerchantOfferMixin}.java`、`trade/{EmeraldDiceTrade,MerchantOfferRawAccess}.java`、`item/sign/NancyLuSignItem.java`、`item/chip/AbyssReagentChipItem.java`。同一时间段内 `verify_fabric_assets.py` 的 `[2] 标签闭环` 计数从「引用 11 / 有提供者 11」变成「引用 10 / 有提供者 10」，即由这些并发改动造成。我未触碰上述任何文件。另外，树里同时出现了一个并发的 `porting/fabric-1.20.1/B34-CARD-RECIPE-REMOVAL-REPORT.md`（不是我写的），说明 `cards_*` 配方在同窗口被另一批次移除；本报告「改前 22 个死配方页」是**我在自己那次检查时点的实测快照**，与这一并发进程的方向一致，不影响改后结论。**若验收要求固定基线，应在无并发写入的窗口重跑四条守门。**

**7.4 未做实机验证。** 任务禁止启动 Minecraft，因此「手册在游戏内渲染是否正常、空配方页是否真的消失」只由 json 结构 + 配方 id 集合 + 守门脚本三层证据支撑，没有游戏内截图/日志证据。若需要，可另开实机批次复核 `chips_charge/airbag_chip` 等条目。

**7.5 jar 复建的实际结果（照实记录）。** 按任务给的命令执行 `cmd.exe //c "gradlew.bat :fabric-1.20.1:remapJar --console=plain"`，Gradle 输出：

```text
> Task :fabric-1.20.1:processResources UP-TO-DATE
> Task :fabric-1.20.1:jar UP-TO-DATE
> Task :fabric-1.20.1:remapJar UP-TO-DATE

BUILD SUCCESSFUL in 464ms
```

即 Gradle 判定为「无变化」。开箱核对（直接读 zip）证明产物**确实已包含本次改动**：`fabric-1.20.1/build/libs/astral_dice-1.5.0-rc.7+fabric_1.20.1.jar` 的 mtime 为 `2026-11-01 20:23:50`（晚于我最后一次写盘 `20:22:53`，由 7.3 的并发构建产出），内含 `assets/astral_dice/patchouli_books` 条目 208 个，`…/chips_charge/airbag_chip.json` 已是 `patchouli:text + astral_dice.guide.chip_source.note`。另外，守门 `[6] 手册闭环` 读的是**源码**（`tools/verify_fabric_assets.py:331` 与 `:339` 直接 `open()` 源文件），不是 jar，所以这一项本来也不受 jar 新旧影响。

**7.6 未处理项汇总。** 除 7.1（三个 `sortnum`）与 7.7（`book.json` 的 `book_texture`）外，184 个 `assets/…/patchouli_books/**` 文件两侧已无任何 `pages` 差异；无新增/删除文件，无需列出删除清单。

**7.7 手册封面贴图口径不一致（在写域之外，未改，需裁决）。**

| 线 | `book.json` 的 `book_texture` | 该线是否自带该贴图 |
|---|---|---|
| fabric-1.20.1 | `patchouli:textures/gui/book_brown.png`（Patchouli 原生棕色书） | 否（`assets/astral_dice/textures/gui/` 无任何 book 贴图） |
| neoforge-1.21.1 | `astral_dice:textures/gui/book_pink.png` | 是（`book_pink.png`） |
| forge-1.20.1 | `astral_dice:textures/gui/book_pink.png` | 是 |
| neoforge-26.1.2 | `astral_dice:textures/gui/book_pink.png` | 是 |

fabric 是四条线里**唯一**还用手册原生棕色封面的线，且它**没有** `book_pink.png` 这个资源。后果有两条：① 对齐主基准需要同时改 `book.json` 的 `book_texture` **并**移植 `assets/astral_dice/textures/gui/book_pink.png`；② 这两处都落在本任务写域（`assets/astral_dice/patchouli_books/**`）**之外**——`book.json` 在 `data/…` 下，贴图在 `textures/gui/` 下。故**未改**，按「需要裁决的问题停下来报告」处理：若按「一律以最新主线为准」，应另开一单把这两处一起对齐（改 1 行 json + 复制 1 个 png）；若 fabric 是有意保留原生封面，则维持现状，并在 AGENTS 里标注为合法平台差异。

> 附：`book.json` 共 9 行，四条线只差 `book_texture` 这 1 行，其余 8 行完全一致。

## 附录 A：临时校验脚本源码（`scripts/b33_verify_patchouli.py`，运行后已删除）

```python
#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""B33 临时校验脚本（用完删除）：fabric-1.20.1 帕秋莉手册 vs neoforge-1.21.1 主基准。"""
import os, json, re, collections

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BOOK = os.path.join("assets", "astral_dice", "patchouli_books")
FAB_RES = os.path.join(ROOT, "fabric-1.20.1", "src", "main", "resources")
NEO_RES = os.path.join(ROOT, "neoforge-1.21.1", "src", "main", "resources")
FAB = os.path.join(FAB_RES, BOOK)
NEO = os.path.join(NEO_RES, BOOK)


def rels(base):
    out = []
    for dp, _, fns in os.walk(base):
        for f in fns:
            if f.endswith(".json"):
                out.append(os.path.relpath(os.path.join(dp, f), base))
    return sorted(out)


def load(p):
    return json.loads(open(p, encoding="utf-8").read())


print("=== [1] pages[].type 序列差异 ===")
fab_rels, neo_rels = rels(FAB), rels(NEO)
print("fabric 文件 %d / neo 文件 %d / 集合相同: %s" % (len(fab_rels), len(neo_rels), set(fab_rels) == set(neo_rels)))
type_diff, pages_diff, ex = [], [], []
for rel in fab_rels:
    f = load(os.path.join(FAB, rel))
    n = load(os.path.join(NEO, rel))
    ft = [p.get("type") for p in f.get("pages", [])]
    nt = [p.get("type") for p in n.get("pages", [])]
    if ft != nt:
        type_diff.append(rel)
        ex.append((rel, ft, nt))
    if f.get("pages") != n.get("pages"):
        pages_diff.append(rel)
print("pages[].type 序列不同的文件数: %d" % len(type_diff))
print("pages 内容不同的文件数: %d" % len(pages_diff))
for rel, ft, nt in ex[:10]:
    print("   DIFF", rel, ft, "->", nt)

print()
print("=== [2] 死配方页扫描 ===")
recipes = set()
for sub in ("recipes", "recipe"):
    for base in ("generated", "main"):
        d = os.path.join(ROOT, "fabric-1.20.1", "src", base, "resources", "data", "astral_dice", sub)
        if os.path.isdir(d):
            recipes |= {n[:-5] for n in os.listdir(d) if n.endswith(".json")}
print("fabric 有效配方 id 数: %d" % len(recipes))
dead, total_craft = [], 0
for rel in fab_rels:
    raw = open(os.path.join(FAB, rel), encoding="utf-8").read()
    doc = json.loads(raw)
    for p in doc.get("pages", []):
        if p.get("type") != "patchouli:crafting":
            continue
        total_craft += 1
        rid = p.get("recipe", "")
        ns, _, path = rid.partition(":")
        if ns == "astral_dice" and path not in recipes:
            dead.append((rel, rid))
print("fabric 手册 crafting 页总数: %d" % total_craft)
print("死配方页数量: %d" % len(dead))
for d in dead[:20]:
    print("   DEAD", d)

print()
print("=== [3] 行尾 / BOM 自检（fabric 全部 184 个） ===")
bad = []
ends = collections.Counter()
for rel in fab_rels:
    raw = open(os.path.join(FAB, rel), "rb").read()
    crlf = raw.count(b"\r\n")
    bare = raw.count(b"\n") - crlf
    bom = raw.startswith(b"\xef\xbb\xbf")
    ends[raw.endswith(b"\n")] += 1
    if crlf == 0 or bare != 0 or bom:
        bad.append((rel, crlf, bare, bom))
print("CRLF>0 且 bareLF==0 且 无BOM 的文件数: %d/%d" % (len(fab_rels) - len(bad), len(fab_rels)))
print("末尾有换行 / 无换行: %d / %d" % (ends[True], ends[False]))
for b in bad[:10]:
    print("   BAD", b)
print()
ok = (not type_diff) and (not dead) and (not bad)
print("RESULT: %s" % ("OK 差异 0 / 死配方页 0 / 行尾洁净" if ok else "NOT CLEAN"))
```
