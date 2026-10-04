# 配方 / 帕秋莉手册覆盖审计报告

> 审计时间：2026-09-12 ｜ 分支 `multi-1.20.1-1.21.1` ｜ 范围：`neoforge-1.21.1` + `forge-1.20.1`（双版本）
> 方式：只读扫描，未修改任何源文件。一键复跑：`python ~/.workbuddy/skills/mc-recipe-manual-audit/scripts/audit.py .`

---

## 一、结论速览

| 检查项 | 结果 |
|---|---|
| 注册物品 ↔ 配方覆盖 | **123 物品 / 119 有配方 / 4 零配方** —— 4 项**全部为有意设计**，非遗漏 |
| 配方 ↔ 手册条目覆盖 | ⚠️ **15 个物品有配方，但手册条目未展示配方**（双版本同缺） |
| 物品 ↔ 手册条目覆盖 | ✅ 0 个物品缺手册条目（128 条目 = 123 物品 + 5 概念页） |
| 创造栏覆盖 | ✅ 123/123，无孤儿、无多余 |
| 汇总标签覆盖（chips/signs/dices/cards） | ✅ 全绿（见第五节） |
| 双版本对等性 | ✅ 零配方集 / 缺口集 / 缺条目集三项完全一致 |

**唯一需要处理的问题 = 第二节的 15 个手册缺口。**

---

## 二、需处理：15 个物品「有配方但手册未展示配方」

判据：物品的配方 json 存在，但其手册条目的 `pages` 数组里**没有任何 `patchouli:crafting` 页**。
已确认手册全库（128 条目、所有分类文件）**无任何他处**展示这 15 个配方。

### 2.1 筹码（11 个）

| 物品 id | 所在手册分类 | 配方文件 | 类型 |
|---|---|---|---|
| `current_core_chip` | chips_charge | current_core_chip.json | shaped |
| `big_bowl_stew_chip` | chips_healing | big_bowl_stew_chip.json | shaped |
| `member_recommendation_chip` | chips_other | member_recommendation_chip.json | shaped |
| `bookmark_chip` | chips_other | bookmark_chip.json | shaped |
| `piggy_bank_chip` | chips_other | piggy_bank_chip.json | shaped |
| `smart_watch_chip` | chips_other | smart_watch_chip.json | shaped |
| `electric_glove_chip` | chips_charge | electric_glove_chip.json | shaped |
| `airbag_chip` | chips_charge | airbag_chip.json | shaped |
| `railgun_chip` | chips_charge | railgun_chip.json | shaped |
| `primordial_core_chip` | chips_charge | primordial_core_chip.json | shaped |
| `whetstone_chip` | chips_other | whetstone_chip.json | shaped |

### 2.2 合成材料（4 个）

| 物品 id | 所在手册分类 | 配方文件 | 类型 |
|---|---|---|---|
| `regeneration_reagent` | materials | regeneration_reagent.json | shapeless |
| `conductive_wire` | materials | conductive_wire.json | shaped |
| `star_coin_dust` | materials | star_coin_dust.json | shapeless |
| `mark_paint` | materials | mark_paint.json | shaped |

### 2.3 成因溯源

配方落地时**未同步补手册页**，共 3 个提交：

| 提交 | 内容 | 连带缺口 |
|---|---|---|
| `fceba41` | 统一 59 个筹码配方 + 补齐 10 个缺失配方 | 10 个筹码 |
| `4e6acc6` | 4 种合成材料新增合成配方 | 4 个材料 |
| `475d484` | 调整 5 个充能筹码配方 + 为「电流核心」新增配方 | 1 个筹码（`current_core_chip`） |

> 后续 `3d4a8d1`（原初核心）、`2e08bd6`（磨刀石）、`841f7ad`（电磁炮）只**修改**已存在的配方，
> 不是缺口成因，但同样没有补手册页 —— 修复时一并覆盖即可。

### 2.4 修复方式

在对应的 `entries/<分类>/<id>.json` 的 `pages` 数组末尾追加一页：

```json
{ "type": "patchouli:crafting", "recipe": "astral_dice:<物品id>" }
```

- **双版本各改一份**（`neoforge-1.21.1` 与 `forge-1.20.1` 的 entries 树），共 15 × 2 = 30 个文件。
- `sortnum`、条目顺序均不受影响。
- **不需要改 `lang/*.json`** —— `patchouli:crafting` 页无文案键。
- 收尾：`build` → 六处部署 → CHANGELOG（并入未发布块，不新开版本）→ 本地提交（默认不 push）。
- 复跑审计脚本，退出码应为 `0`。

---

## 三、已确认「非遗漏」：4 个零配方物品

| 物品 | 获取途径 | 定性 |
|---|---|---|
| `attack_card_full_power`（全力攻击） | 消耗「蓄力」获得（`DiceCombatEvents.onDiceBlessingExpired`）；源码注释明示「不在随机池」；赏金池可得 | 有意设计 |
| `effect_card_living_page`（活体书页） | **专属牌**，仅调查员立牌 `RinSignItem` 产出（`RandomCardHandler.registerExclusiveCard`） | 有意设计 |
| `effect_card_fate_guidance`（命运的指引） | **专属牌**，仅占星师立牌 `HaiqingSignItem` 产出 | 有意设计 |
| `star_plate`（星盘） | 纯战利品：箱子 5%（weight 1:19）、实体 1%（weight 1:99）；赏金池可得 | 有意设计（手册已说明不可合成） |

> 补充：`star_plate` 的 4 个衍生配方（`golden_star_plate_from_plates` / `from_nether_star` 等）产出的是**黄金星盘**，
> 不是星盘本身；已确认全库 `"id": "astral_dice:star_plate"` 作为配方产物的出现次数为 **0**。

---

## 四、结构完整性（无问题项）

| 检查 | 结果 |
|---|---|
| 双版本注册物品集差集 | 空（123 = 123） |
| 创造栏 `output.accept` ↔ `ModItems` 常量 | 123/123 完全一致，无未进栏、无悬空常量 |
| 手册条目名 ↔ 物品 id 双向差集 | 仅 5 个 getting_started 概念页（battle_dice / integration / playstyles / special_effects / spell_damage），无物品缺条目 |
| 手册 crafting 页引用的「非同名额」配方 | 7 个（`attack_card_epic_from_large`、`attack_card_large_from_medium`、`defense_card_epic_from_large`、`defense_card_large_from_medium`、`blank_chip_duplicate`、`golden_star_plate_from_plates`、`star_coin_from_bag`）**全部实际存在**，非断链 |
| 配方结果统计 | 122 个配方文件 / 120 个结果（含 `patchouli:guide_book`） |

---

## 五、汇总标签覆盖（顺带核查）

| 标签 | 标签条数 | 应有物品数 | 缺项 | 多余 |
|---|---|---|---|---|
| `astral_dice:chips` | 60 | 60 | — | — |
| `astral_dice:signs` | 17 | 16 立牌 + `blank_sign` | — | `blank_sign`（有意收录） |
| `astral_dice:dices` | 13 | 13 | — | — |
| `astral_dice:dice_t0~t4` | 1/3/5/3/1 | — | — | — |
| `astral_dice:combat_cards` | 10 | 10 | — | — |
| `astral_dice:effect_cards` | 15 | 15 | — | — |

同时确认**标签内无引用不存在物品的悬空项**。图鉴 / 赏金联动不会因标签漏项出错。

---

## 六、附带的非阻塞发现（文案层面）

1. **`star_plate` 手册文案有歧义**
   - 中文：`星光凝铸的板片，用于高阶工艺。本身不参与配方。`
   - 英文：`A plate of solidified starlight for higher crafts. It appears in no recipe itself.`
   - 问题：星盘**确实**是全部史诗筹码配方的第三行材料（它在配方中大量出现），本意应是「**不能通过合成获得**」。
   - 建议改为「无法通过合成获得，仅可从战利品获取」以消除歧义。

2. **`ModItems.java` 第 493 行注释已过期**
   - 现状：`// === 新材料(1.2.0):合成材料,本身不参与配方 ===`
   - `4e6acc6` 已为这 4 个材料补了合成配方，注释与事实不符。

---

## 七、复现方式

```bash
# 一键完整审计（自动探测子项目与命名空间，退出码 1 = 存在缺口）
python <skill>/scripts/audit.py <仓库根目录>

# 本次使用的原始脚本（含战利品表 / Bountiful 池 / 手册条目五源比对）已随 2026-09-15 的 temp/ 清理移除；
# 需要五源比对时改用上面的技能脚本（技能 mc-recipe-manual-audit）
```

审计技能位置：`.dsh/skills/mc-recipe-manual-audit/`（`SKILL.md` 载有判据与读结果要点；技能自带 `<技能目录>/scripts/audit.py`）。
