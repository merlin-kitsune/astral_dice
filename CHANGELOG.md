# Changelog / 更新日志

## 1.0.1-rc1

### 界面与显示 / UI & Display
- 替换卡牌选择界面背景贴图。 / Replaced the card selection background texture.
- 恢复战斗牌 Tooltip 剩余次数显示。 / Restored remaining uses text in battle card tooltips.
- 修复攻击-特大/防御-特大等战斗牌耐久条显示问题。 / Fixed durability bar display for Epic battle cards and others.
- 调整战斗牌耐久值：中/大/特大=10，暗影突袭=10，名刀=5，蓄力=1，全力攻击=2。 / Adjusted card durability values.

### 战斗与触发 / Combat & Triggers
- 修复骰子/筹码/立牌下蹲右键装备不生效。 / Fixed sneak-right-click auto-equip for dice/chips/signs.
- 骰神赐福仅限近战武器触发。 / Dice Blessing now only triggers with melee weapons.
- 攻击友好/被动/未激怒中立生物不再触发骰神赐福。 / Friendly, passive, and non-angered neutral mobs no longer trigger Dice Blessing.
- 蓄力在赐福结束后返还全力攻击，并增加兜底检测与 ActionBar 提示。 / Charge now refunds Full Power after blessing, with fallback check and ActionBar message.

### 治愈体系 / Healing System
- 重构治愈机制：独立 30 秒计时器，触发时按治愈点×2 回血，计时结束减半。 / Reworked healing with an independent 30s timer.
- 治愈效果不再产生药水粒子。 / Healing effect no longer shows potion particles.
- 美工刀触发条件改为生命值≥60%。 / Cutter chips now trigger at 60% HP or above.
- 吸血鬼“嘬一口”期间视为满血与半血状态。 / During Vampire Bite, player counts as both full HP and half HP.
- 巧克力蛋糕/汉堡改为恢复最大生命值 20%/40%。 / Chocolate Cake and Hamburger now heal 20%/40% of max HP.
- 奢华大餐改为恢复自身最大生命值 30%，并治疗同队/无队伍玩家。 / Luxury Feast heals 30% of user max HP to teammates and teamless players.

### 平衡性 / Balance
- 秘密侦探立牌返还战斗牌仅限 20 血以上敌对目标。 / Bonnie sign battle card reward now only for hostile targets above 20 HP.
- 治愈盾牌触发间隔改为 15 秒。 / Buffer Shield trigger cooldown changed to 15 seconds.
- 护法立牌“爆发”攻击力 +4，持续 2:00。 / Misaki Burst attack bonus increased to +4 and duration to 2:00.

### 提示与反馈 / Feedback
- 新增多类 ActionBar 提示：虚弱印记奖励、随机事件、调查事件、全力攻击返还、立牌待命、未佩戴骰子等。 / Added ActionBar messages for Weak Mark rewards, random events, investigation events, Full Power refunds, sign ready states, and missing dice.
- 大侦探随机事件提示会显示具体事件名称。 / Fanny random event messages now show the triggered event name.

### 工程 / Project
- 修复 GitHub Actions 中 `gradlew` 无执行权限问题，并增加构建产物上传。 / Fixed gradlew permission in CI and added artifact upload.
- 更新 README 模组介绍。 / Updated README with mod introduction.
- 版本号更新为 `1.0.1-rc1`。 / Version updated to `1.0.1-rc1`.

## 1.0-rc1

### 卡牌选择界面重写 / Card Selection GUI Rewrite
- 使用了新卡牌容器贴图。 / Used the new card container texture.
- 右侧显示攻击/防御 `<下限>-<上限>` 范围，并实时随卡牌增删更新。 / Right side now shows attack/defense `<min>-<max>` ranges, updating live as cards are added or removed.
- 下方新增卡牌选择器：攻击左列、防御右列，按物品栏顺序排列，支持滚动。 / Added a card selector at the bottom: attack on the left, defense on the right, sorted by inventory order, with scrolling support.
- 支持点击下方存放区域/右键将手持卡牌放回物品栏。 / Added support for returning a held card to the inventory by clicking the storage area or right-clicking.
- 暗影突袭、名刀、蓄力、全力攻击图标缩放并向右下对齐。 / Shadow Strike, Meito, Charge, and Full Power icons are scaled and aligned to the bottom-right.

### 耐久度机制 / Durability System
- 战斗牌改用 MC 原生耐久数据（damage/maxDamage），可被 Durability Tooltip 模组识别。 / Battle cards now use vanilla Minecraft durability data (damage/maxDamage), recognized by Durability Tooltip mods.
- 移除战斗牌自定义“剩余次数”Tooltip 文本。 / Removed the custom "remaining uses" tooltip text from battle cards.
- 防御牌现在会在受到伤害时正确消耗耐久。 / Defense cards now correctly consume durability when the wearer takes damage.

### 配方调整 / Recipe Adjustments
- 忍者立牌：铁锭 → 回响碎片。 / Komachi sign: Iron Ingot → Echo Shard.
- 占星师立牌：青金石块 → 海晶砂砾。 / Haiqing sign: Lapis Block → Prismarine Crystals.
- 魔法秘典：中间书与笔 → 回响碎片。 / Magic Tome: center Book and Quill → Echo Shard.
- 魔法箭袋：中间光灵箭 → 回响碎片。 / Magic Quiver: center Spectral Arrow → Echo Shard.
- 忍术飞镖：磁石 → 红石块。 / Ninja Star: Lodestone → Redstone Block.

### 效果牌提示 / Effect Card Feedback
- 出牌数用完后显示 ActionBar 提示，冷却按所有效果牌中最长剩余时间计算。 / Shows an ActionBar message when play count is exhausted; cooldown uses the longest remaining time among all effect cards.

### 工程配置 / Project Configuration
- 版本号更新为 `1.0-rc1`。 / Version updated to `1.0-rc1`.

## 1.0-SNAPSHOT.23

### 物品 ID 与 Tag / Item IDs & Tags
- 效果牌统一为 `effect_card_*`。 / Effect cards unified to `effect_card_*`.
- 筹码统一为 `*_chip`。 / Chips unified to `*_chip`.
- 活体书页改为 `effect_card_living_page`。 / Living Book Page renamed to `effect_card_living_page`.
- 新增物品 Tag：`dices`、`combat_cards`、`effect_cards`、`is_exclusive`、`signs`、`chips`、`materials`。 / Added item tags: `dices`, `combat_cards`, `effect_cards`, `is_exclusive`, `signs`, `chips`, `materials`.

### 配方 / Recipes
- 基础骰子改为红石 + 石英块。 / Basic dice recipe changed to Redstone + Quartz Block.
- 黄金/钻石/下界合金骰子统一为 `dice_upgrade` 有序升级；移除下界合金骰子锻造配方。 / Golden/Diamond/Netherite dice now use the `dice_upgrade` shaped upgrade; removed the Netherite dice smithing recipe.
- 调整大量卡牌、效果牌、立牌、筹码配方。 / Adjusted many battle card, effect card, sign, and chip recipes.
- 筹码通用升级模板： / Chip generic upgrade templates:
  - 蓝→紫：`LGL/GTG/PPP` / Blue→Purple: `LGL/GTG/PPP`
  - 紫→金：`RDR/DTD/GGG` / Purple→Gold: `RDR/DTD/GGG`
- 定向爆破改为有序对称配方。 / Directional Blast changed to a shaped symmetrical recipe.

### 战利品 / Loot
- 星盘可从所有原版宝箱开出。 / Star Plates can now be found in all vanilla chests.
- 黄金星盘可从 `minecraft:chests/trial_chambers/reward_ominous` 开出。 / Golden Star Plates can be found in `minecraft:chests/trial_chambers/reward_ominous`.

### 创造栏 / Creative Tab
- 材料移至最前端。 / Materials moved to the front of the creative tab.
- 调整效果牌与活体书页顺序。 / Adjusted the order of effect cards and Living Book Page.

### 数值与 Tooltip / Values & Tooltip
- 拳击手套攻击：+2/+4/+8。 / Boxing Gloves attack: +2/+4/+8.
- 速度轮滑移速：+5%/+15%/+25%。 / Speed Skates movement speed: +5%/+15%/+25%.
- 摩托头盔防御：+2/+4/+6；高级额外盔甲韧性 +2。 / Moto Helmet defense: +2/+4/+6; High tier additionally grants +2 armor toughness.
- 夹心饼干生命：+4/+8/+12。 / Sandwich max health: +4/+8/+12.
- 缓冲盾牌冷却改为 30 秒。 / Buffer Shield cooldown changed to 30 seconds.
- 手电筒：每 4 点星光 +1 攻击力。 / Flashlight: +1 attack per 4 Starlight.
- 效果牌 Tooltip 新增出牌数与当前周期伤害加成提示。 / Added effect card tooltip hints for play count and current-cycle damage bonus.
