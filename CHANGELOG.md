# Changelog / 更新日志

## 1.0.2-rc1

### 内容与平衡性调整 / Content & Balance Changes
- 调整骰子筹码栏位数量（按 0★~3★）：普通 0/1/2/3，黄金 1/2/3/4，钻石 2/3/4/5，下界合金 3/4/5/6。 / Adjusted dice chip slot counts (0★~3★): Basic 0/1/2/3, Golden 1/2/3/4, Diamond 2/3/4/5, Netherite 3/4/5/6.
- 调整骰子攻击/防御卡牌栏数量（每侧）：普通 3，黄金 4，钻石 5，下界合金 6。 / Adjusted per-side attack/defense card slots: Basic 3, Golden 4, Diamond 5, Netherite 6.
- 主动触发“骰神赐福”时不再消耗防御牌耐久。 / Actively triggering Dice Blessing no longer consumes defense card durability.
- 怪物近战攻击拥有“骰神赐福”的玩家时，每个赐福期间最多消耗一次防御牌耐久。 / When a melee monster attacks a player with Dice Blessing, defense card durability is consumed at most once per blessing.
- 秘密侦探立牌击杀奖励只返还攻击牌，攻击玩家不返还卡牌。 / Bonnie sign kill rewards now grant only attack cards, and attacking players grants no card.

### 界面与显示 / UI & Display
- 所有立牌 Tooltip 增加主动/被动技能名称，并将主动技能按键提示移至最上方。 / Added active/passive skill names to all sign tooltips and moved the key hint to the top.
- 主动技能按键提示整体改为亮色，按键符本身改为黄色。 / The sign key hint is now bright-colored, with the key symbol in yellow.
- 大当家立牌主动技能图标改为使用大当家立牌图标。 / The Fen sign active skill icon now uses the Fen sign icon.
- 统一 Tooltip 时间与数值格式：时间蓝色、数值黄色、不足 1:00 使用秒、药水时间使用括号。 / Unified tooltip formatting: blue time, yellow values, seconds for durations under 1:00, and parentheses around potion effect times.
- 同步大当家立牌主动技能中英文 Tooltip 文案。 / Synced the Fen sign active tooltip text between Chinese and English.

### 已修复BUG / Bug Fixes
- 移除立牌主动技能触发成功后的通用“技能已激活”ActionBar，避免覆盖各立牌自身的特殊提示。 / Removed the generic “Skill activated” ActionBar after sign skills trigger, preventing it from overwriting sign-specific messages.
- 修复溅射/范围伤害未在被影响目标身上显示伤害数字的问题（大当家立牌扩散、定向爆破 AOE）。 / Fixed missing damage numbers on affected targets for splash/AOE damage (Fen cleave and Directional Blast AOE).
- 定向爆破 AOE 伤害数字改为使用效果牌的绿色数字。 / Directional Blast AOE damage numbers now use the green effect-card damage color.
- 本 Mod 创建的自定义效果无法被牛奶、蜂蜜瓶或 `/effect clear` 清除，仅玩家死亡可清除。 / Custom effects created by this mod can no longer be removed by milk, honey bottles, or `/effect clear`; only player death can remove them.
- 修复玩家死亡时部分效果状态未正确重置的问题，统一清理立牌等待、扩散、出牌、魔法箭袋、命运指引、调查阶段等状态。 / Fixed some effect states not being reset on player death; sign-ready, cleave, play-count, Magic Quiver, Fate Guidance, and investigation states are now reset properly.

### 工程 / Project
- 版本号更新为 `1.0.2-rc1`。 / Version updated to `1.0.2-rc1`.

## 1.0.1-rc1

### 内容与平衡性调整 / Content & Balance Changes
- 重新设计治愈体系：为治愈增加独立计时器，默认每 30 秒触发一次治愈效果（仅在骰神赐福期间）。 / Reworked the healing system with an independent timer: healing triggers every 30 seconds by default (only during Dice Blessing).
- 触发骰神赐福时按治愈点×2 回血，计时结束治愈点减半。 / Healing Points heal ×2 when Dice Blessing triggers, and halve when the timer ends.
- 美工刀触发阈值改为最大生命值 60% 以上即可触发，避免大部分时候都触发不了。 / Cutter chips now trigger above 60% of max HP, preventing them from being unable to trigger in most situations.
- 护法立牌：主动技能攻击力加成提高至 +4，持续时间提高至 2:00。 / Misaki sign: active skill attack bonus increased to +4 and duration increased to 2:00.
- 吸血鬼立牌：主动效果调整第二项为：不论玩家当前血量为多少，都同时视为满血和半血以下状态。 / Papara sign: the second active effect now treats the player as both full HP and below half HP regardless of current health.
- 秘密侦探立牌：被动技能返还卡牌只对击杀 20 血以上敌对目标生效。 / Bonnie sign: passive card reward now only applies when killing hostile targets with more than 20 HP.
- 巧克力蛋糕/汉堡改为恢复最大生命值 20%/40%；奢华大餐改为恢复自身最大生命值 30%，并治疗同队/无队伍玩家。 / Chocolate Cake and Hamburger now heal 20%/40% of max HP; Luxury Feast heals 30% of the user's max HP and also heals teammates and teamless players.
- 治愈盾牌触发间隔改为 15 秒。 / Buffer Shield trigger cooldown changed to 15 seconds.
- 调整战斗牌耐久值：中/大/特大=10，暗影突袭=10，名刀=5，蓄力=1，全力攻击=2。 / Adjusted battle card durability values: Medium/Large/Epic=10, Shadow Strike=10, Meito=5, Charge=1, Full Power=2.
- 蓄力在赐福结束后返还全力攻击，并增加兜底检测与 ActionBar 提示。 / Charge now refunds Full Power after blessing, with fallback check and ActionBar message.

### 已修复BUG / Bug Fixes
- 修复骰神赐福的触发目标判定和武器判定问题：仅近战武器触发，且友好/被动/未激怒中立生物不再触发。 / Fixed Dice Blessing target and weapon checks: it now only triggers with melee weapons and no longer triggers on friendly, passive, or non-angered neutral mobs.
- 修复骰子/筹码/立牌下蹲右键装备不生效。 / Fixed sneak-right-click auto-equip for dice/chips/signs.
- 修复战斗牌耐久条显示异常、Tooltip 丢失问题，恢复剩余次数显示。 / Fixed battle card durability bar display issues and missing tooltip, restored remaining uses display.
- 修复大当家立牌触发伤害扩散时意外递归导致游戏崩溃。 / Fixed an unexpected recursion crash when Fen sign triggered damage cleave.
- 治愈效果不再产生药水粒子。 / Healing effect no longer shows potion particles.

### 质量更新 / Quality Improvements
- 为部分行为增加 ActionBar 提示，同时受到事件影响的玩家也会有 ActionBar 提示。 / Added ActionBar feedback for several actions, and players affected by events also receive ActionBar messages.
- 大侦探随机事件提示会显示具体事件名称。 / Fanny random event messages now show the triggered event name.
- 微调骰子卡牌选择界面的卡牌显示。 / Fine-tuned card display in the dice card selection GUI.
- 替换卡牌选择界面背景贴图。 / Replaced the card selection background texture.
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
