# tooltip / 手册条目 文本自查报告（2026-09-21）

范围：`neoforge-1.21.1` + `forge-1.20.1` × `zh_cn` + `en_us`（4 份 lang）的全部
`tooltip.*` 与 `astral_dice.guide.entry.*`；`neoforge-26.1.2` 按冻结口径**未动**。

---

## ① 四个立牌「第二被动（联动技能）」最终文案（可直接粘贴）

放置位置：**备注区**（`addSignNoteLines` → 紫色 `§d`、无标题、无列表符号），位于被动正文之后、
动态计数器之前。行内高亮值后一律回落 `§7`（handler 会统一改写成备注区底色 `§d`）。

### 大当家立牌 · 心意相连（键：`tooltip.astral_dice.sign.fen_xinyi`，**原有键，本次回归原文**）

| | |
|---|---|
| 玩家原文（DSH 09-19 22:00） | `新增被动技能（心意相连）：使用主动技能时，使友方队伍中装备「风水师立牌」的玩家获得 1 张「符卡-福」` |
| 最终 zh | `心意相连：使用主动技能时，使友方队伍中装备「风水师立牌」的玩家获得 §e1§7 张符卡-福` |
| 最终 en | `Hearts Linked: when you use the active skill, every teammate wearing the Fengshui Master Sign gains §e1§7 Blessing Talisman` |

### 风水师立牌 · 完美帮手（键：`tooltip.astral_dice.sign.zhao_wanmei_bangshou`，**新增**）

| | |
|---|---|
| 玩家原文（DSH 09-19 22:00） | `被动技能（完美帮手）：对装备「大当家立牌」的玩家施加「白泽赐福」时，使该玩家获得 1 层「养精蓄锐」` |
| 最终 zh | `完美帮手：对装备「大当家立牌」的玩家施加「白泽赐福」时，使该玩家获得 §e1§7 层「养精蓄锐」` |
| 最终 en | `Perfect Helper: applying Baize's Blessing to a player wearing the Boss Sign grants that player §e1§7 Recharged Energy stack` |

### 怪力侦探立牌 · 挚友守护（键：`tooltip.astral_dice.sign.sherry_zhishou_shouhu`，**新增**）

| | |
|---|---|
| 玩家原文（本次会话 09-21 17:00） | `被动技能（挚友守护）：同队伍内若存在装备人偶师立牌的玩家，则使该玩家受到的伤害-1` |
| 最终 zh | `挚友守护：同队伍内若存在装备人偶师立牌的玩家，则使该玩家受到的伤害 §e-1§7` |
| 最终 en | `Friend's Guard: while a teammate wears the Hanna Sign, that teammate takes §e-1§7 less damage` |

### 人偶师立牌 · 挚友祝福（键：`tooltip.astral_dice.sign.hanna_zhishou_zhufu`，**新增**）

| | |
|---|---|
| 玩家原文（本次会话 09-21 18:03） | `被动技能（挚友祝福）：路过装备"怪力侦探"立牌的玩家时，使该玩家获得 力量II (1:00) + 抗性提升(1:00) 和一层"推理时间"。该能力每 1:00 仅触发1次` |
| 最终 zh | `挚友祝福：路过装备「怪力侦探」立牌的玩家时，使该玩家获得 §9力量 II (1:00)§7 + §9抗性提升 (1:00)§7 和 §e1§7 层「推理时间」。该能力每 §91:00§7 仅触发 §e1§7 次` |
| 最终 en | `Friend's Blessing: passing a player wearing the Sherry Sign grants them §9Strength II (1:00)§7 + §9Resistance (1:00)§7 and §e1§7 Reasoning Time stack. This ability triggers only §e1§7 time per §91:00§7` |

**染色依据**（权威 = 双版本 `ModTooltipHandler` 类头 javadoc 规则 1/2/3）：非时间数值 `§e` 并连同符号
（`§e1§7`、`§e-1§7`）；时间 `§9`（`§91:00§7`）；「效果名（时间）」整段蓝（`§9力量 II (1:00)§7`、`§9抗性提升 (1:00)§7`）。

---

## ② 改动清单

### A. 第二被动移入备注区（本轮主项）

| # | 键 | 旧 → 新 | 归类 |
|---|---|---|---|
| 1 | `sign.zhao_passive` | 去掉第 2 行 `完美帮手：…`（改为独立备注键） | 移位 |
| 2 | `sign.sherry_passive` | 去掉第 3 行 `  挚友守护：…`（**原带 2 空格 ⇒ 被渲染成 `- ` 子项**，不再需要） | 移位 |
| 3 | `sign.hanna_passive` | 去掉第 2 行 `  挚友祝福：…`（同上） | 移位 |
| 4 | `sign.zhao_wanmei_bangshou` / `sherry_zhishou_shouhu` / `hanna_zhishou_zhufu` | 新增 3 键（见 ①） | 新增 |
| 5 | `ModTooltipHandler`（双版本） | 三段被动正文之后各加 `tooltip.add(Component.empty()); addSignNoteLines(...)`；块注释同步 | 组装 |

### B. 符号 / 标点 / 空格

| # | 键 | 旧 → 新 | 归类 |
|---|---|---|---|
| 6 | `applied_stones`（zh，2 份） | `已装载卡片:` → `已装载卡片：` | 半角冒号 → 全角 |
| 7 | `card.upgrade_hint`（zh） | `升星: ` → `升星：` | 同上 |
| 8 | `star_level`（zh） | `星级: ` → `星级：` | 同上 |
| 9 | `sign.pandaman_health_gain`（zh） | `获得生命值: ` → `获得生命值：` | 同上 |
| 10 | `card.living_page`（zh） | `“标记”` → `「标记」` | 弯引号 → 「」 |
| 11 | `card.attack_epic/large/medium/meito`、`defense_epic/large/medium`（zh，7 键） | `（取§e2§7次最大值）\| ` → `（取 §e2§7 次最大值） \| ` | 数值与中文紧贴、竖线前缺空格（**英侧本就有空格 ⇒ 中英不对称**） |
| 12 | `card.fate_curse_mitigation`（zh） | `降低§e50%%§7` → `降低 §e50%%§7` | 数值左侧缺空格 |
| 13 | `sign.nancy_lu_active`（zh） | `费用点数§e×2§7` → `费用点数 §e×2§7` | 同上 |
| 14 | `sign.parunan_passive`（zh） | `首次骰点§e×2§7` → `首次骰点 §e×2§7` | 同上 |

### C. 实现细节词（内容口径，但属"不合理"范畴 ⇒ 一并清理）

| # | 键 | 旧 → 新 | 归类 |
|---|---|---|---|
| 15 | `chip.energy_recycler`（4 份） | 删括注「（按实际位移统计，单 tick 位移过大视为传送不累计）」/ 对应英文 | 删实现细节词（技能 §3 高置信例） |
| 16 | `guide.entry.energy_recycler_chip.1`（4 份） | 删「单 tick 位移过大视为传送，不累计；」/ 对应英文（保留「卸下清除进度」） | 同上（手册侧同一处漏网） |

### D. 回归原文措辞 + 修正英译错

| # | 键 | 旧 → 新 | 归类 |
|---|---|---|---|
| 17 | `sign.fen_xinyi`（zh/en） | `同队中装备风水师立牌的玩家各获得 1 张符卡-福（未组队时不生效）` → `使友方队伍中装备「风水师立牌」的玩家获得 1 张符卡-福`；**删「（未组队时不生效）」** | 回归原文 + 删补充说明引用 |
| 18 | `guide.entry.zhao_sign.2`（zh） | `对装备大当家立牌的玩家施加白泽赐福时，该玩家获得 1 层养精蓄锐` → 补「」 | 回归原文 |
| 19 | `guide.entry.zhao_sign.3`（zh/en） | 同上口径（`同队中…各获得` → `使友方队伍中…获得`；删「（未组队时不生效）」） | 回归原文 |
| 20 | `guide.entry.hanna_sign.2`（en） | `wearing the Hanna Sign` → `wearing the Sherry Sign` | **英译错**（zh 写的是「怪力侦探」） |
| 21 | `sign.fen_xinyi`（en） | `every teammate … (no effect when not in a team)` → 去掉括注 | 同上 |

**未改动项**：全部 `§r`（共 100+ 处，**均在行尾** ⇒ 按规则 0 例外保留，且染色审计 PASS）；
`dice_desc_prefix/middle/suffix`、`chip.revenge_halberd_current` 的首尾空格（**拼接片段，有意为之**）；
`1-6 / 4-6`（骰子区间族通篇如此）与 `1~10`（攻防牌族通篇如此，各自内部一致）；
4 条材料 tooltip 与 `parunan_active/passive` 句首行的行尾句号（**中英一致、语法正确**）；
手册条目的半角括号 `(0:30)`（**规则 3 的「效果名（时间）」约定**）。

---

## ③ 待确认

1. **`energy_recycler` 的第 3 行**「（移动进度在任何卸下筹码时清除）」保留了 —— 它同属「生命周期副作用」类
   （技能 §4 列为隐藏行为），但玩家可能真需要知道"为什么进度没了"。是否也删？（本次只删了含实现词的那一段）
2. **行尾句号**：`sign.parunan_active/passive` 的首行以 `。`/`.` 结尾，而其余 ~50 条立牌行都不带句号。
   语法上没错、中英也一致，本次未动；要统一成"立牌行不带句号"的话我再过一遍。
3. **内容层剩余候选**（脚本 `SOFT/LONG/PAREN/DUP` 段，共 90/282/174/114 条）属"待判词/超长/疑似机制括注/条内重复"，
   不在本次「符号·标点·换行」范围内。需要的话我按技能口径做一轮内容体检。
4. **26.1.2 线**：其中 `chip.energy_recycler` 仍留有「单 tick」（2 条 HIGH）。本批未镜像该线，
   随后随功能移植一并同步。

---

## 验证

| 闸门 | 结果 |
|---|---|
| `tools/check_lang_sync.ps1`（双版本） | ✅ `OK: zh_cn.json(804 keys) 与 en_us.json(804 keys) key 完全一致` |
| `scripts/audit/tooltip_color_audit.ps1` | ✅ `PASS（无违规）` exit=0 |
| `scan_tooltip_content.py`（全类别） | HIGH **活动两树 0 条**（残留 2 条在 26.1.2）、PCT（未转义 %）**0 条** |
| 自研 `temp/audit_text.py` | 真问题类（半角标点/弯引号/数值紧贴/竖线空格/行内 §r/结构不对称/文件换行符）**全部 0 条** |
| 行尾 | 4 份 lang 均 CRLF（807 行）且末尾有换行；`ModTooltipHandler` forge=CRLF(1572/0)、neo=LF（沿用该文件既有约定） |
| 键数 | 801 → **804**（+3 备注键），双版本 × 双语一致 |
| 构建 | 双版本 `BUILD SUCCESSFUL`；开 jar 核对：3 个新键在位、旧串清零、handler 常量池引用新键、en 误译已修 |
