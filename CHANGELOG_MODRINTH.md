# Astral Dice 更新日志 / Astral Dice Changelog

## 1.1.1-rc1

### 中文版

#### ✨ 新增内容
- 看板立牌（mimi）技能重做：
  - **被动**：合成、奖励、返还卡牌时，每获得一张战斗牌 +1 星币；装备时筹码栏位 +1；每累计 25 个星币获得 1 个随机筹码（蓝色 60% / 紫色 35% / 金色 5%）
  - **主动**：回收物品栏中全部卡牌（含专属牌），并返还 N+1 张随机卡牌（不含专属牌）
- 新增通用**谜之炖菜**配方，支持 `astral_dice:suspicious_stews` 标签中的任意物品参与合成

#### ⚖️ 平衡调整
- 诅咒之剑配方改用 `astral_dice:suspicious_stews` 标签（不再限定单一谜之炖菜物品）

#### 🖥️ UI / 显示
- 新增骇客立牌（nancy_lu）“远程骇入”效果图标与描述
- 立牌主动技能 ActionBar 改用本地化名称
- 命运指引备注文本颜色修正

#### 🐛 修复
- 修复治愈图标在计时结束且未再次触发时未移除的问题

---

### English Version

#### ✨ New Content
- **Mimi sign rework**:
  - **Passive**: gain +1 Star Coin per battle card gained from crafting, rewards, or card returns; +1 chip slot while equipped; every 25 Star Coins gained this way grants a random chip (Blue 60% / Purple 35% / Gold 5%)
  - **Active**: recycle all cards in your inventory (including exclusive cards) and receive N+1 random cards (exclusive cards excluded)
- Added a generic **Suspicious Stew** recipe accepting any item from the `astral_dice:suspicious_stews` tag

#### ⚖️ Balance Changes
- Cursed Sword recipe now uses the `astral_dice:suspicious_stews` tag instead of a single stew item

#### 🖥️ UI / Display
- Added Nancy Lu (Hacker) "Remote Hack" effect icon and description
- Sign active-skill ActionBar now uses localized names
- Fixed Fate Guidance note text color

#### 🐛 Bug Fixes
- Fixed the healing icon not being removed when its timer ends without re-triggering

---

## 1.1.0-rc1

### 中文版

#### ✨ 新增内容
- 新增效果牌：**以毒攻毒**
  - 移除最多 3 个原版负面效果，并获得生命恢复
- 新增效果：**青之诅咒**
  - 护甲值 -20%，盔甲韧性归 0
- 新增筹码：
  - **维生素药丸**：获得卡牌时治愈 +1
  - **诅咒之剑**：装备时受到青之诅咒；骰神赐福期间击杀加成
  - **复仇之戟**：负面/诅咒效果触发攻击/防御 +6
  - **贯穿之铳**：伤害效果牌生效时，远程/魔法伤害附加目标防御力
  - **可口糖果**：使用效果牌时回血/治愈；满血时出牌数 +1
  - **友情徽章**：对友方治疗时双方获得治愈点
  - **探天卫星**：自动补充轨道炮；轨道炮击杀奖励
- 新增立牌：**骇客立牌（nancy_lu）**
  - 被动：网络防火墙（免疫末影珍珠伤害、赐福结束攻防加成）
  - 主动：完全隐身；攻击解除隐身并消耗战斗牌获得攻击力加成
- 新增筹码：**手持风扇-小**、**手持风扇-大**

#### ⚖️ 平衡调整
- 手持风扇-小配方调整
- 手持风扇-大改为通用蓝→紫升级配方
- 狂暴配方简化为 1 火药 + 1 星币
- 诅咒之剑：
  - 骰神赐福期间每轮最多触发一次击杀攻击力加成
  - 上限默认 16，最大 32
- 占星师立牌、忍者立牌品质提升至**史诗**，配方改用**钻石骰子**
- 骇客立牌（nancy_lu）主动技能重做为**完全隐身**
  - 完全隐身期间生物无法将玩家设为索敌目标
- 创造栏筹码重新分类：
  - 星光类 → 治愈类 → 标记类 → 无流派

#### 🖥️ UI / 显示
- 治愈类筹码与史莱姆立牌 tooltip 显示当前治愈点
- 修复 tooltip 中按键名、`%` 号、计数器符号无法正确染色的问题
- 修复诅咒之剑 tooltip 中青之诅咒描述多余空行
- 立牌 / 材料 tooltip 分类从 `card` 修正至 `sign` / `material`

#### 🐛 修复
- 修复大侦探立牌被动无法触发的问题
- 修复维生素药丸 tooltip 缺失；拾取卡牌不再触发维生素药丸
- 友情徽章配方改为必须使用**瞬间治疗药水**
- 修复 tooltip 颜色代码导致部分文本变白的问题

---

### English Version

#### ✨ New Content
- New effect card: **Fight Poison with Poison**
  - Removes up to 3 vanilla negative effects and grants Regeneration
- New effect: **Blue Curse**
  - -20% Armor and zero Armor Toughness
- New chips:
  - **Vitamin Pill** – gain +1 Healing Point when obtaining a card
  - **Cursed Sword** – always afflicted by Blue Curse; kill bonus during Dice Blessing
  - **Revenge Halberd** – conditional +6 Attack/Defense from negative/curse effects
  - **Piercing Gun** – ranged/magic damage gains bonus damage equal to target defense while damage cards are active
  - **Candy** – heal and gain Healing Points from effect cards; +1 play count at full HP
  - **Friendship Badge** – both players gain Healing Points when healing a friendly player
  - **Satellite** – auto-supplies Orbital Strikes and grants effect cards on ranged/magic kills
- New sign: **Nancy Lu (Hacker)**
  - Passive: Network Firewall – immune to Ender Pearl damage, gains Attack/Defense after Dice Blessing
  - Active: Full Invisibility – attacking removes invisibility and consumes a battle card for bonus Attack
- New chips: **Hand Fan (Small)**, **Hand Fan (Large)**

#### ⚖️ Balance Changes
- Adjusted Hand Fan Small recipe
- Hand Fan Big now uses the generic Blue→Purple upgrade recipe
- Simplified Berserk recipe to 1 Gunpowder + 1 Star Coin
- Cursed Sword:
  - Kill bonus triggers at most once per Dice Blessing
  - Cap default 16, max 32
- Astrologer and Ninja signs upgraded to **Epic** and now require **Diamond Dice**
- Nancy Lu active reworked into **Full Invisibility**
  - While fully invisible, mobs cannot target the player
- Creative chip tab regrouped:
  - Starlight → Healing → Mark → No School

#### 🖥️ UI / Display
- Healing chips and Slime sign now show current Healing Points in tooltips
- Fixed sign key, percent sign, and counter symbol coloring in tooltips
- Fixed extra blank line around Blue Curse in the Cursed Sword tooltip
- Moved sign/material tooltip keys from `card` to `sign` / `material`

#### 🐛 Bug Fixes
- Fixed Great Detective passive not triggering
- Fixed missing Vitamin Pill tooltip; picking up cards no longer triggers it
- Friendship Badge recipe now requires an **Instant Healing Potion**
- Fixed tooltip color-code issues causing some text to appear white
