# 更新日志 / Changelog

本文档按语言完全拆分:上半部分为中文版,下半部分为英文版。
This changelog is fully split by language: the Chinese version comes first, followed by the English version.

# 中文更新日志

## 未发布 / Unreleased（1.2.0，仅 1.21.1）

> 约定：对当前版本已记录条目的后续改动，直接合并进原条目，仅保留改动后的最终版本，不追加“再次修改”条目。

### 新内容

- 立牌主动技能 ActionBar 提示重构:新建独立响应事件 `SignActiveTriggeredEvent`,各立牌在立牌类中注册自身提示——忍者(出牌数+1 及剩余出牌数)、看板(新卡牌数与星币数)、骇客(完全隐身时长)已注册专属提示;占星师/秘密侦探提示文本更新为「主动技能已激活,攻击敌对目标向其施加…」;未注册的立牌(大当家/扫地机/史莱姆/护法/上班族/吸血鬼/经商/调查员)显示默认提示「<立牌名>：主动技能已启动！」(仅 1.21.1)。 / Sign-active ActionBar feedback refactored: a dedicated response event `SignActiveTriggeredEvent` was added, and signs register their own prompts in their sign classes — Komachi (play count +1 and remaining), Mimi (new cards and Star Coins) and Nancy Lu (invisibility duration) now have custom texts; Haiqing/Bonnie prompt texts were updated; unregistered signs (Fen, Jasmine, Lulu, Misaki, Padman, Papara, Parunan, Rin) show the default "<Sign>: Active skill started!" (1.21.1 only).
- 卡牌界面:骰神赐福期间的锁定红色提醒由界面顶部移至界面下方(选择区域以外)(仅 1.21.1)。 / Card inventory screen: the red locked warning during a Dice Blessing moved from the top to the bottom of the screen, outside the selection area (1.21.1 only).
- 卡牌选择界面:卡牌背包背景贴图按骰子星级(0-3)自动切换(`card_inventory_0~3.png`),`CardInventoryMenu` 暴露 `getStarLevel()`(1.20.1 同步,双版本一致)。 / Card inventory screen: background texture now switches by die star level (0-3), with `getStarLevel()` exposed on the menu (synced to 1.20.1, feature parity across loaders).
- 新增骰子「下界之星骰子」:全新 T4 奇异品阶(`astral_dice:dice_t4`),可由任意 T3 骰子(下界合金/绯红/末影,经 `dice_t3` 标签)升级——配方为外圈 4 颗下界之星十字环绕(`" N " / "NDN" / " N "`,N=下界之星,D=T3 骰子);筹码栏 = 4+s(0★4/1★5/2★6/3★7,比 T3 多 1 格);卡牌槽与费用点数始终按最高档 3★ 配置(卡牌栏 12 格/攻防费用上限各 6,经 `DiceCurioItem.configStarLevel`,菜单持久化时保留实际星级);骰子每提升 1 星级攻击力 +2、防御力 +2(折算护甲 +4,经 `NetherStarDiceItem` 瞬态属性修饰器实时刷新);物品自带附魔光效(`isFoil` 恒真)(1.20.1 已同步,双版本一致)。 / New "Nether Star Die": a brand-new exotic T4 tier (`astral_dice:dice_t4`), upgradeable from any T3 die (Netherite/Crimson/Ender via the `dice_t3` tag) — 4 Nether Stars in a cross around it (`" N " / "NDN" / " N "`, N=Nether Star, D=T3 die); chip slots = 4+s (0★4/1★5/2★6/3★7, one more than T3); card slots and cost points are always configured as the top 3★ tier (12 card slots / cost cap 6 per side, via `DiceCurioItem.configStarLevel`, while the menu persists the actual star); each star level grants +2 attack and +2 defense (+4 armor via transient attribute modifiers refreshed by `NetherStarDiceItem`); the item has a permanent enchanted glint (`isFoil` always true) (both loaders).
- 新增骰子「末影骰子」:与下界合金骰子同阶(`astral_dice:dice_t3`),可由下界合金骰子升级(ZYZ/YDY/ZYZ,Z=末影珍珠,Y=末影之眼,D=下界合金骰子);受到致命伤害时触发一次不死图腾效果(复活至 1 生命、清除全部效果、生命恢复 II 0:45、伤害吸收 II 0:05、火焰抗性 0:40,与原版图腾一致;图腾粒子/音效/手持高亮动画使用末影骰子图标),随后进入 5:00 冷却(经 `EnderDiceHandler` 在 `LivingDeathEvent` 取消死亡并附加到玩家);不死图腾触发后会尝试瞬移到 16 格内安全地面(排除空气/水面/船上,玩家在水中不触发传送),并播放末影粒子与传送音效;主手/副手原版不死图腾触发保命时,若佩戴末影骰子也会执行同款瞬移(不进入末影骰子冷却);但装备期间雨中/水下(`isInWaterRainOrBubble`)受到的伤害 +40%(在 `LivingDamageEvent.Pre` 最终减免后放大)(1.20.1 已同步,双版本一致)。 / New "Ender Die": same tier as the Netherite Die (`astral_dice:dice_t3`), upgradeable from it (ZYZ/YDY/ZYZ, Z=Ender Pearl, Y=Eye of Ender, D=Netherite Die); lethal damage triggers one totem-of-undying effect (revive to 1 HP, clear all effects, Regeneration II 0:45, Absorption II 0:05, Fire Resistance 0:40 — matching the vanilla totem, with the totem particles/sound/held-up animation using the Ender Die icon), followed by a 5:00 cooldown (via `EnderDiceHandler`, cancelling `LivingDeathEvent` and attached to the player); after the Ender Die's own totem effect or a vanilla Totem of Undying triggers, it attempts to teleport to safe ground within 16 blocks (no air/water-surface/boat destinations, and no teleport while the player is in water), with ender particles and a teleport sound; the vanilla-totem path does not start the Ender Die cooldown; while equipped, damage taken in rain/underwater (`isInWaterRainOrBubble`) is increased by 40%% (applied after final reductions in `LivingDamageEvent.Pre`) (both loaders).
- 新增骰子「紫晶骰子」:与钻石骰子同阶(`astral_dice:dice_t2`),可由钻石骰子升级(AAA/ADA/AAA,A=紫水晶块,D=钻石骰子);远程/魔法攻击命中时也触发战斗骰(1-6)并追加骰点伤害(经 `SpellDamageRegistry` 法伤修饰器统一结算:作用域沿用白名单——箭矢/投掷物/原版与联动模组魔法,与法伤效果牌同一链路),但不触发骰神赐福、不消耗卡牌耐久(1.20.1 已同步,双版本一致)。 / New "Amethyst Die": same tier as the Diamond Die (`astral_dice:dice_t2`), upgradeable from it (AAA/ADA/AAA, A=Block of Amethyst, D=Diamond Die); ranged/magic attacks also roll the combat die (1-6) and add its roll as damage (settled through the `SpellDamageRegistry` spell-damage modifier chain, sharing the whitelisted scope — arrows/throwables/vanilla and linked mod magic — with the spell-damage effect cards), without triggering a Dice Blessing or consuming card durability (both loaders).
- 新增骰子「绯红骰子」:与下界合金骰子同阶(`astral_dice:dice_t3`),可由下界合金骰子升级(YJY/JDJ/YJY,Y=下界疣块,J=绯红菌,D=下界合金骰子);战斗骰高点数(4-6)出现概率提升 50%(权重法:4/5/6 权重 ×1.5,P(高点数) 50%→60%,对攻击骰与玩家防御骰均生效;攻击方掷骰/玩家防御骰/反击伤害掷骰统一经 `rollCombatDie` 分发至 `CrimsonDiceHandler.rollD6`),但掷出 1 点时立即受到 6 点伤害(骰子伤害类型,与王之力自伤同一来源)(1.20.1 已同步,双版本一致)。 / New "Crimson Die": same tier as the Netherite Die (`astral_dice:dice_t3`), upgradeable from it (YJY/JDJ/YJY, Y=Nether Wart Block, J=Crimson Fungus, D=Netherite Die); high combat-die rolls (4-6) are 50%% more likely (weighted: faces 4-6 ×1.5, P(high) 50%%→60%%, applies to attack rolls and player defense rolls), but rolling a natural 1 immediately deals 6 damage to you (dice-damage type, the same source as King Power's self-damage) (both loaders).
- 新增骰子「诡异骰子」:与钻石骰子同阶(`astral_dice:dice_t2`),可由钻石骰子升级(CJC/JDJ/CJC,C=缠怨藤,J=诡异菌,D=钻石骰子);立牌主动技能冷却时间 -50%(所有开始冷却入口统一经 `WeirdDiceHandler.signCooldownTicks` 取值),但战斗骰低点数(1-3)出现概率提升 50%(权重法:1/2/3 权重 ×1.5,P(低点数) 50%→60%,对攻击骰与玩家防御骰均生效)(1.20.1 已同步,双版本一致)。 / New "Weird Die": same tier as the Diamond Die (`astral_dice:dice_t2`), upgradeable from it (CJC/JDJ/CJC, C=Twisting Vines, J=Warped Fungus, D=Diamond Die); sign active-skill cooldowns are halved, but low combat-die rolls (1-3) are 50%% more likely (weighted: faces 1-3 ×1.5, P(low) 50%%→60%%, applies to attack rolls and player defense rolls) (both loaders).
- 新增骰子「下界岩骰子」:与黄金骰子同阶(`astral_dice:dice_t1`),可由黄金骰子升级(NNN/NTN/NNN,N=下界砖,T=黄金骰子);佩戴后在下界采集矿物(石英矿/下界金矿/远古残骸)时有 30% 概率额外掉落 1 枚星币、5% 概率额外掉落 1 个星盘(同一次采矿最多一种,先判星盘),且猪灵对佩戴者保持中立(经 `PiglinAiMixin` 视同身穿金甲)(1.20.1 已同步,双版本一致)。 / New "Netherrack Die": same tier as the Golden Die (`astral_dice:dice_t1`), upgradeable from it (NNN/NTN/NNN, N=Nether Brick, T=Golden Die); mining Nether ores (quartz/gold/ancient debris) has a 30% chance to drop a Star Coin and 5% to drop a Star Plate (at most one per block, plate checked first), and piglins stay neutral to the wearer (via `PiglinAiMixin`, treated as wearing gold armor) (both loaders).
- 新增骰子「黑曜石骰子」:与钻石骰子同阶(`astral_dice:dice_t2`),可由钻石骰子升级(OOO/ODO/OOO,O=黑曜石,D=钻石骰子);佩戴后获得 3 点基础防御力(按 1 防御力 = 2 护甲折算为 +6 护甲属性修饰器),且受到的火焰伤害(原版 `is_fire` 标签)减少 70%;因加入 `dice_t2` 标签,可作为母体合成更高阶的下界合金骰子(1.20.1 已同步,双版本一致)。 / New "Obsidian Die": same tier as the Diamond Die (`astral_dice:dice_t2`), upgradeable from it; grants 3 base defense (+6 armor via attribute modifier, 1 defense = 2 armor) and reduces fire damage taken (vanilla `is_fire` tag) by 70%; being in the `dice_t2` tag it can craft the higher-tier Netherite Die (both loaders).
- 新增骰子「绿宝石骰子」:与钻石骰子同阶(`astral_dice:dice_t2`),佩戴后与村民交易时用星币代替绿宝石支付,并享受 20% 折扣(向下取整,至少 1 枚);配方 = GPG/PDP/GPG(G=绿宝石块,P=星盘,D=钻石骰子)(双版本)。 / New "Emerald Die": same tier as the Diamond Die (`astral_dice:dice_t2`); while equipped, villager trades are paid with Star Coins instead of Emeralds at a 20% discount (rounded down, minimum 1); recipe = GPG/PDP/GPG (G=Emerald Block, P=Star Plate, D=Diamond Die) (both loaders).
- 新增骰子「玻璃骰子」:与黄金骰子同阶(`astral_dice:dice_t1`),战斗牌点数始终取最大值(仅随机骰牌:中/大/特大/名刀/防御牌;固定牌暗影突袭/蓄力/全力攻击副作用保留),但死亡会丢失该骰子及其已装备的全部卡牌;配方 = GGG/GTG/GGG(G=玻璃,T=黄金骰子)(双版本)。 / New "Glass Die": same tier as the Golden Die (`astral_dice:dice_t1`); combat cards always roll their maximum (random cards only: Medium/Large/Epic/Meito/Defense; fixed cards Shadow Strike/Charge/Full Power keep their side effects), but dying destroys this die and all of its equipped cards; recipe = GGG/GTG/GGG (G=Glass, T=Golden Die) (both loaders).
- 新增枪匠立牌(Moses,史诗):弱点识破——任意来源的闪避/反击(每名目标至多一次)以及攻击带「破绽」目标都会获得 1 层(最多 4 层),每层攻击/防御 +1、骰点最低数 +1,骰神赐福结束减 1 层;主动「弱点反击」——等待 30 秒,攻击普通敌对目标施加「破绽」2:00,未触发不进入冷却,主动冷却经被动「精密技巧」减为 120 秒;破绽目标骰点只能为 0,会被枪匠闪避,闪避后自动反击(反击含弱点识破攻击加成);tooltip 将「弱点识破/精密技巧」两条被动合并展示,把「破绽」效果说明并入主动描述,并将弱点识破计数器置于 tooltip 最下方;配方为 GPG/TCT/PZP(G=弩、P=星盘、Z=钻石骰子、C=空白筹码、T=红石块),图标取自 images/枪匠立牌.png(1.20.1 已同步,双版本一致)。 / New "Gunsmith Sign" (Moses, epic): Weakness Insight — gain 1 layer (max 4) after any source of dodging/counterattacking (at most once per target) or after attacking a Broken target; each layer gives +1 attack/defense and raises the minimum dice roll by +1; one layer is removed when a Dice Blessing ends. Active "Weak Counter" — waits 30s, applying Broken (2:00) when you attack a normal hostile; no cooldown is consumed if no target is hit, and Passive "Precision" reduces the active cooldown to 120s. A Broken target's dice can only be 0 and it is dodged by the Gunsmith; after dodging the Gunsmith automatically counterattacks (including Weakness Insight attack). The tooltip merges the two passive sections (Weakness Insight/Precision), moves the Broken effect description into the active description, and places the counter at the very bottom. Recipe = GPG/TCT/PZP (G=Crossbow, P=Star Plate, Z=Diamond Die, C=Blank Chip, T=Block of Redstone); icon taken from images/枪匠立牌.png (both loaders).

- 新增立牌「肉弹战车立牌」(Pandaman,稀有):被动「有好有坏」——使用「汉堡」获得 2 点最大生命上限(不超过 100,卸下清除);使用「汉堡」或「巧克力蛋糕」后,使 8 格内所有友方玩家获得 2 点治疗与 1 点治愈;装备时反击若生命未满,增加缺失生命值等值的伤害。主动「大吃特吃」——获得一张随机治疗类效果牌(汉堡/巧克力蛋糕/奢华大餐),并对 16 格内所有敌对目标施加「嘲讽」1:00。新增嘲讽效果:目标只能攻击对其施加嘲讽的玩家。配方 = WHW/HCH/BBB(W=白色混凝土,H=黑色混凝土,C=空白立牌,B=星币);tooltip 底部显示「获得生命值」与治愈点计数器;图标取自 images/肉弹战车立牌.png(双版本一致)。 / New "Pandaman Sign" (rare): Passive "Good and Bad" — using Hamburger grants 2 max health (up to 100, cleared on unequip); using Hamburger or Chocolate Cake gives friendly players within 8 blocks 2 healing and 1 Healing Point; while equipped, counterattacks add damage equal to missing health when not at full health. Active "Devour" — gain one random healing card (Hamburger/Chocolate Cake/Luxury Feast) and apply Taunt (1:00) to all hostile targets within 16 blocks. New Taunt effect: the target can only attack the player who taunted it. Recipe = WHW/HCH/BBB (W=White Concrete, H=Black Concrete, C=Blank Sign, B=Star Coin); the tooltip shows Health Gained and Healing Point counters at the bottom; icon from images/肉弹战车立牌.png (both loaders).

### 内容与平衡性调整

- 效果牌出牌系统调整:保留出牌窗口(基础出牌数 1 + 固定/临时 +1 来源 + 忍者出牌数银行),移除效果牌周期内连续出牌上限(原默认 9 张,`max_effect_card_plays` 配置删除),加成来源可无限叠加;效果牌轮次按新定义判定(周期结束 = 所有效果牌进度走完 **且** 出牌冷却走完)(仅 1.21.1)。
- 看板立牌被动重做:合成或主动返还卡牌时每获得一张战斗牌 +1 星币(奖励/复制不再触发);主动每返还累计 25 张战斗牌获得一个随机筹码(原为每累计 25 星币)(仅 1.21.1)。
- 扫地机立牌被动追加:使用「加急加快」效果牌后,主动技能冷却立即减少最大冷却的 50%(仅 1.21.1)。
- 调查员立牌主动:获得一张活体书页,若使用前物品栏中没有活体书页则共获得两张(仅 1.21.1)。
- 秘密侦探立牌:调查阶段隐身期间(隐身 + 调查阶段效果)不会被生物索敌(仅 1.21.1)。
- 骇客立牌主动:消耗的战斗牌仅从主物品栏选取(不再从末影箱/背包类容器);攻击力加成最低 +2,主物品栏无战斗牌可消耗时同样获得保底 +2(仅 1.21.1)。
- 岿然不动:防御力 +2 → 护甲 +8(对应骰战防御力 +4),迁移为效果属性修饰器,真实护甲与骰战均生效且不重复计算(仅 1.21.1)。
- 全力攻击:耐久 2 → 5(仅 1.21.1)。
- 诅咒之剑配方:中轴材料由「谜之炖菜」标签改为「哭泣黑曜石」;删除废弃的 `suspicious_stews` 物品标签(仅 1.21.1)。
- 青之诅咒描述:盔甲韧性「归 0」→「-100%%」(数值本身不变)(仅 1.21.1)。
- 治愈体系:治愈点上限改为固定 32(原 max(10, 最大生命值÷2));医疗箱-紧急/完备移除「装备时立即恢复 2/6 点生命」(赐福加点保留)(仅 1.21.1)。
- 维生素药丸:触发范围由「合成或奖励途径」扩为「合成或获得卡牌时」;探天卫星补充轨道炮改经统一发牌路径(仅 1.21.1)。
- 探天卫星第三项能力:使用「轨道炮」后本轮出牌数 +1 由「每轮最多一次」改为「每 1:00 仅一次」(仅 1.21.1)。
- 命名统一:活体书页相关 Java 标识符/效果注册 id 统一为 `LIVING_PAGE`/`living_page`(物品 id `effect_card_living_page` 不变)(仅 1.21.1)。
- 防御力折算规范:效果牌/立牌/筹码提供的防御力一律折算为**真实护甲**(1 防御力 = 2 护甲值,经 ARMOR 属性修饰器),不再参与骰战防御修饰器——骰战防御修饰器仅保留战斗防御牌(区间变动)。受影响来源:扫地机(2 护甲/点,护甲上限 40)、上班族(-4~+8 护甲)、吸血鬼(半血 +6 护甲)、复仇之戟(+12 护甲)、骇客(被动防御 +6 护甲)、大当家(养精蓄锐 +4 护甲);抗性提升不再折算骰战防御点(原版减伤仍生效)(仅 1.21.1)。
- 怪物防御-护甲折算与玩家同步:怪物防御公式由「护甲÷4」改为与玩家一致「护甲÷2」(1 防御力 = 2 护甲值);贯穿之铳的目标防御计算同步(仅 1.21.1)。
- 贯穿之铳配方:下界合金碎片 → 下界合金锭(双版本)。
- 新增筹码「肾上腺素-一般」(史诗)恢复,「肾上腺素-高效」(传奇)配方回滚为升级链:一般=生命值低于最大生命值一半时攻击/防御 +3;高效=+8 且触发时被敌方攻击有 20%% 概率闪避单次攻击。配方:一般=ZXZ/DCD/PPP(Z=再生药水,X=下界之星,D=凋零玫瑰,C=空白筹码,P=星盘);高效=RZR/ZOZ/PPP(R=红石粉,Z=钻石,O=肾上腺素-一般,P=黄金星盘)(双版本)。
- 标靶筹码:触发骰神赐福后,对**距离最近的敌对目标**施加 1 层标记(移除原「标靶范围内随机」逻辑与 `target_chip_range` 范围配置,不再限定作用距离)(仅 1.21.1)。
- 夹心饼干-美味:最大生命值 +12 → +8;移除「生命值低于一半时每 1:00 获得 1 层「反击」」;新增:最大生命值超过 20 点时,超出部分每 4 点生命值 +1 攻击力(仅 1.21.1)。
- 创造物品栏骰子顺序调整:玻璃骰子、下界岩骰子移到下界合金骰子之后(1.20.1 已同步,双版本一致)。 / Creative tab dice order adjusted: Glass and Netherrack dice now appear after the Netherite die (both loaders).
- 修复骰子 tooltip 显示攻击牌时加成误标为「骰子」及染色问题:攻击牌(中/大/特大/名刀)统一显示为「攻击」,范围不再带「+」前缀,与防御牌/独立牌 tooltip 格式一致(仅 1.21.1)。
- 骰子卡牌栏平衡:卡牌栏总格数改为仅由星级决定(与骰子品阶无关)——0★=4(攻防各2)、1★=6(各3)、2★=8(各4)、3★=12(各6);实际可用格严格按星级,无隐藏可用格(双版本)。
- 骰子升级配方改用阶层标签作为升级母体:新增 `astral_dice:dice_t0`(基础骰子)/`dice_t1`(黄金骰子)/`dice_t2`(钻石骰子)/`dice_t3`(下界合金骰子)四个物品标签;黄金/钻石/下界合金骰子的升级配方输入由具体物品改为对应标签(双版本)。
- 反击效果器:玩家死亡不再清除反击层数——死亡时记录当前层数,重生后自动恢复(层数仍经 HUD 图标持续显示,归 0 自动移除)(双版本)。
- 效果等级角标改为阿拉伯数字并扩展上限:原版显示罗马数字(II~X)且仅到等级 10,现改为阿拉伯数字角标(如「治愈 3」「治愈 32」)并支持到等级 100(新增 `EffectRenderingInventoryScreenMixin`;1.20.1 经 Mixin Booster 运行时重映射,`getEffect()` 无 Holder 包装)(双版本)。

### 已修复BUG

- 修复 `PiglinAiMixin` 注入签名:1.21.1 原版 `PiglinAi.isWearingGold` 参数为 `LivingEntity`,此前按 `Player` 编写导致整合包运行时 Mixin 应用失败(Invalid descriptor);现改为 `LivingEntity` 并在内部仅对玩家执行下界岩骰子判定(仅 1.21.1)。 / Fixed the `PiglinAiMixin` injection signature: vanilla `PiglinAi.isWearingGold` takes a `LivingEntity` in 1.21.1; it was written against `Player`, causing a runtime Mixin apply failure (invalid descriptor) in the modpack — it now accepts `LivingEntity` and applies the Netherrack-die check only to players (1.21.1 only).
- 修复新增骰子 tooltip 缺失:黑曜石/下界岩/诡异/绯红/紫晶/末影/下界之星骰子此前未进入骰子 tooltip 分发入口,导致这些骰子缺少专属说明;现已统一纳入骰子 tooltip 处理(1.20.1 已同步,双版本一致)。 / Fixed missing tooltips on newer dice: Obsidian, Netherrack, Weird, Crimson, Amethyst, Ender, and Nether Star dice were not entering the dice tooltip branch, so their unique descriptions were absent — they are now handled as dice (both loaders).
- 修复骰子 tooltip 染色在 `%%`/占位符转译后断裂的问题:含 `%%` 或 `%s` 的骰子 tooltip(如绿宝石骰子的 20%% 折扣)改为先经 `translationString` 展开再放入 `Component.literal`,避免 Minecraft 将 `%%`/`%s` 拆成独立无样式片段,导致 `%` 号与后续文字丢失正确颜色(1.20.1 已同步,双版本一致)。 / Fixed dice-tooltip coloring breaking around translated `%%`/placeholder tokens: dice tooltips containing `%%` or `%s` (e.g. the Emerald die's 20%% discount) are now expanded via `translationString` into a single `Component.literal`, so Minecraft no longer splits the format token into an unstyled fragment and the `%` sign and following text keep the intended colors (both loaders).
- 修复所有骰子 tooltip 中「骰神赐福 + 时间」染色中断:通用 `dice_desc` 行不再把时间放进会被翻译/占位符拆断的彩色区间,改为拆成「前缀/赐福效果+时间/中间说明/按键/后缀」独立染色组件,确保「骰神赐福 (60秒)」整体保持蓝色(1.20.1 已同步,双版本一致)。 / Fixed the broken coloring of “Dice Blessing + duration” in every die tooltip: the shared `dice_desc` line no longer places the duration inside a legacy-color range that translation/placeholder handling can split; it is now assembled from independently styled fragments (prefix / blessing+duration / middle / key / suffix), so “Dice Blessing (60s)” stays fully blue (both loaders).
- 修复骰子 tooltip 数字高亮后文字被误染为灰色(含新增骰子专属说明与铁砧升星提示):这些 tooltip 原用 `§7`(灰色)作为“恢复”标记,导致金/红/绿/暗绿等条目主色在数字后变成灰色;已改为 `§r`(真正重置回该条目主色),使数字后面的说明文字与条目原本颜色一致(1.20.1 已同步,双版本一致)。 / Fixed text after numeric highlights in die tooltips being wrongly colored gray (including newer die unique descriptions and the anvil star-upgrade hint): they used `§7` (gray) as the “restore” marker, so gold/red/green/dark-green lines turned gray after the highlighted number; changed to `§r` (true reset back to the line’s own color), keeping the following text consistent with each tooltip’s intended color (both loaders).
- 删除紫晶骰子 tooltip 中易歧义的「不会触发骰神赐福」条目,仅保留“远程/魔法攻击也触发战斗骰并追加骰点伤害”(1.20.1 已同步,双版本一致)。 / Removed the potentially misleading “Does not trigger a Dice Blessing” line from the Amethyst die tooltip, keeping only the ranged/magic combat-die description (both loaders).
- 修复部分战斗牌 tooltip 的耐久度数字未染黄:中/大/特大/暗影突袭/名刀/蓄力/防御中/防御大/防御特大仍走 `Component.translatable`,使 `%s` 处的耐久度变成无样式片段;已统一改为 `tt()` 先展开再以单字面量渲染,剩余次数数字保持黄色(1.20.1 已同步,双版本一致)。 / Fixed durability numbers on some combat-card tooltips not being colored yellow: medium/large/epic/Shadow Strike/Meito/Charge/defense medium/large/epic still used `Component.translatable`, so the `%s` durability value became an unstyled fragment; they now use `tt()` to expand first and render as one literal, keeping the remaining-uses number yellow (both loaders).
- 绿宝石骰子补充 `curios:dice` 饰品槽标签(此前遗漏,导致无法装备进骰子栏),并同步补入 `astral_dice:dices` 汇总标签(双版本)。 / Added the missing `curios:dice` curios-slot tag for the Emerald Die (previously it could not be equipped into the dice slot), and synced it into the `astral_dice:dices` summary tag (both loaders).
- 死亡清理调整:不死图腾等取消死亡时不再执行任何清理;护法立牌死亡丢失全部「剑气」层数(tooltip 追加死亡提示);吸血鬼立牌死亡清除主动技能效果;秘密侦探死亡保留调查阶段进度(仅卸牌时清除);忍者/调查员立牌的效果牌伤害加成死亡保留;移除死亡清理中无读取者的 DamageEffectBonus 残留调用(仅 1.21.1)。 / Death-cleanup adjustments: totem-canceled deaths no longer trigger any cleanup; Misaki loses all Sword Qi stacks on death (tooltip note added); Papara's active effect is cleared on death; Bonnie keeps investigation progress on death (unequip only); Komachi/Rin effect-card damage bonuses survive death; removed the leftover no-reader DamageEffectBonus reset in the death handler (1.21.1 only).
- 修复忍者立牌主动技能在已有出牌进度或处于出牌冷却期时无法生效的问题:出牌数+1 改为累积式「出牌数银行」(按实际出牌消耗,跨周期保留,不受满额/冷却影响),并移除旧布尔标记及其残留调用(仅 1.21.1)。 / Fixed the Komachi sign's active failing when play progress existed or the cooldown was running: the play-count +1 is now a banked extra-play token (consumed per actual play, persists across windows, unaffected by burst-full or cooldown), and the old boolean flag plus its leftover calls were removed (1.21.1 only).
- 1.20.1 recipe fixes: the "Koi's Rulebook" (patchouli:guide_book) recipe was in the wrong folder (singular "recipe") and thus never loaded — moved to "recipes"; the Cursed Sword chip's generated recipe used the 1.21 result.id format and failed to parse on 1.20.1 — regenerated in the 1.20.1 item format (generation sources fixed in both versions).
- 修复部分筹码 tooltip 中换行符被渲染成方块占位符的问题(夹心饼干-美味/肾上腺素-高效/卫星/复仇之戟/诅咒之剑神秘遗物联动等多行 tooltip):改为按 lang 值内 `\n` 逐行拆分添加,不再整段组件内嵌真实换行符(仅 1.21.1)。 / Fixed chip tooltips rendering embedded newlines as box glyphs (Sandwich - Gourmet / Adrenaline - High-Grade / Satellite / Revenge Halberd / Cursed Sword Enigmatic Legacy+ link, etc.): multi-line lang values are now split into separate tooltip lines instead of keeping real `\n` inside a single component (1.21.1 only).
- 1.20.1 配方修复:「恋的规则书」(patchouli:guide_book) 配方此前放错目录(recipe 单数)未加载,已移至 recipes;诅咒之剑配方生成文件误用 1.21 result.id 格式导致解析失败,已改回 1.20.1 item 格式(双版本生成源同步修正)。

### 工程
- 测试环境集成 JEI 便于配方查验(forge-1.20.1 15.56.0.205 / neoforge-1.21.1 19.39.0.372);1.20.1 dev 的整合包模组改为 curse maven modImplementation 依赖,修复 dev 环境无法加载生产 mixin 模组的问题。
- 1.20.1 引入 Mixin Booster 并改为强依赖(`mixinbooster` 0.1.3):运行时以 Sponge Mixin 接管 mixin 执行并自动处理 Mojmap→SRG 重映射,移除 MDG LegacyForge 的 `mixin` 扩展/refmap/注解处理器(村民交易 mixin 不再需要构建期 refmap)。 / 1.20.1 now depends on Mixin Booster (`mixinbooster` 0.1.3, mandatory): Sponge Mixin takes over mixin execution at runtime with automatic Mojmap→SRG remapping, replacing the MDG LegacyForge `mixin` extension/refmap/annotation-processor setup (the villager-trade mixins no longer need a build-time refmap).
- 将 1.21.1 的新增骰子(黑曜石/下界岩/诡异/绯红/紫晶/末影/下界之星)、创造栏排序、tooltip 染色与末影骰子瞬移等改动同步移植到 forge-1.20.1;1.20.1 的下界岩猪灵中立 `PiglinAiMixin` 沿用 Mixin Booster / Sponge Mixin 规范(与 neoforge/fabric 一致),未使用 Forge 原生或其他第三方 Mixin API(1.20.1 同步)。 / Ported the newer dice (Obsidian/Netherrack/Weird/Crimson/Amethyst/Ender/Nether Star), creative-tab ordering, tooltip coloring, and the Ender Die teleport changes from 1.21.1 to forge-1.20.1; the 1.20.1 Netherrack-die piglin-neutral `PiglinAiMixin` follows the Mixin Booster / Sponge Mixin convention (consistent with NeoForge/Fabric) and does not use Forge-native or other third-party Mixin APIs (1.20.1 sync).
- 测试环境集成 ModernFix:1.21.1 从整合包复制 `modernfix-neoforge-5.27.24+mc1.21.1.jar` 到 `run/1.21.1/mods`(`install_test_mods.ps1`);1.20.1 经 build.gradle `modImplementation "maven.modrinth:modernfix:OvpPdk44"` 注入 dev run classpath。两版 launch 验证改为识别 ModernFix 的 `Total time to load game and open world was` 加载完成日志(基础等待 30s,未出现则每 15s 再检测)。 / Integrated ModernFix into the test environment: 1.21.1 copies `modernfix-neoforge-5.27.24+mc1.21.1.jar` from the modpack to `run/1.21.1/mods` (`install_test_mods.ps1`); 1.20.1 injects it into the dev run classpath via `modImplementation "maven.modrinth:modernfix:OvpPdk44"`. Both launch verifications now detect ModernFix's `Total time to load game and open world was` loading-complete log (base wait 30s, re-check every 15s if absent).
- 构建默认自动推送整合包:双版本 `gradlew build` 现在会同时推送 run/mods、根目录 build/libs 与各自整合包 mods 目录(原需 `-PdeployToPack`,现默认触发;该参数保留兼容;pack 根不存在仍自动跳过)。 / Builds now push to integration packs by default: `gradlew build` deploys to run/mods, the root build/libs, and both pack mods directories automatically (previously required `-PdeployToPack`; the flag remains accepted for compatibility, and missing pack roots are skipped).

## 1.1.3

### 新内容

- 骰神赐福期间,骰子卡牌界面禁止插入/移除卡牌(服务端权威 clicked/quickMoveStack 拦截 + 客户端同步拦截),界面顶部显示红色提醒文字(双版本)。
- 效果牌复制/返还覆盖全部效果牌(忍者立牌/魔法秘典/魔法箭袋不再有排除项;含治疗/伤害/专属牌,专属牌复制后绑定获得者)(双版本)。
- 帕秋莉手册:1.21.1 原有;1.20.1 新增移植(109 个页面 + 175×2 条 guide key,新增 Patchouli 1.20.1-85-forge 依赖,整合包补装 Patchouli;4 处联动文案按 1.20.1 改写为「神秘遗物」)。
- 手册分类图标:基本介绍=头号玩家立牌、材料=星币、卡牌=攻击-特大(双版本)。

### 已修复BUG

- **1.20.1 手册移植补全**:此前仅复制了手册页面(assets)而缺失书籍定义(book.json)、合成配方、创造栏书籍物品与书籍模型,导致手册物品丢失、全部页面文本(含联动文本)不可用;已补全(配方按 1.20.1 NBT 格式适配)并重新发布。
- 效果移除拦截误伤本模组主动移除:新增内部移除通道(ModEffectRemoval),「待命」/计数/治愈等效果不再永久残留(双版本)。
- 以毒攻毒移除效果时遍历实时视图导致 ConcurrentModificationException(服务端 tick 崩溃风险):改为快照迭代(双版本)。
- 效果牌复制/返还映射缺失导致错发「王之力」:统一为 BaseEffectCardItem.cardByTypeId 单一映射,补齐以毒攻毒/活体书页等全部效果牌(双版本)。
- 随机卡牌池不一致:ALL 补齐以毒攻毒、BATTLE 按定义含防御牌(双版本)。
- 帕鲁南(经商)立牌手持右键可绕过冷却/等待并意外触发主动:右键行为统一为仅装备或替换装备,主动技能统一由快捷键/立牌栏触发(双版本)。
- FTB Teams UUID 成员分支永不入队:与 OPAC 分支对齐修复(双版本)。
- 顺劈/定向爆破等 AOE 波及目标二次进入骰战结算:改为跳过(1.21.1 标志位 / 1.20.1 dice_damage 源判定)(双版本)。
- 骇客立牌卸载误清公共无敌/隐身,且 onCurioTick 隐身到期判断恒真(默认附件 0)每 tick 误删其他来源隐身:仅清除自身授予的状态(双版本)。
- 标记携带的发光与标记同寿命(牛奶/effect clear 不再单独清除),且不再显示 HUD 效果标识器(双版本)。
- 帕秋莉手册条目 `Format error`:guide 文本字面 `%` 未转义(18 条目中英 36 条)改为 `%%`;4 个同隐患 tooltip key 同步修复(1.21.1;1.20.1 手册为移植的已转义文案,4 个 tooltip key 同步修复)。
- 客户端伤害数字因错误总线订阅永不消失 + 网络线程竞态(1.21.1;1.20.1 无此问题)。
- 技能改名:大当家立牌主动「运攻」→「运功」、骇客立牌主动「远程骇入」→「远程侵入」(双版本)。

### 工程

- 版本号:1.21.1 = `1.1.3-rc1+neoforge_1.21.1`,1.20.1 = `1.1.3-pre1+forge_1.20.1`;重新编译并发布至各自整合包(自动清理旧版本产物)。
- ModEventHandlers 订阅拆分到各自功能实现处(DiceCombatEvents/ModTooltipHandler/LootInjectionHandler/AnvilUpgradeHandler/PlayerLifecycleHandler/ModEffectEvents/PlayerTickEvents + 各立牌/筹码/卡牌/管理器类)(仅 1.21.1)。
- 移除蓄力卡"赐福进行中放入"的延迟转换代码(charge_defer 附件及分支)——卡牌栏锁定后不再可能发生;赐福结束一律返还「全力攻击」(双版本)。
- 移除写而未读的 PlayerResourceRegistry(PlayerResource/ResourceType 及治愈/星光注册实现)(双版本)。
- 全量代码审计与清理(1.21.1 为主,1.20.1 同步移植):消除每 tick 重复施加效果同步包(美工刀/复仇之戟/治愈)、每 tick 附魔资源键分配、菜单打开时每 tick 全量重算(20 tick 节流)、法伤修饰器 isActive 双次求值;跳数字发送/效果牌类型映射等重复实现收敛为单一入口;删除 8 个未用数据组件、isChipItem 手写链、骰子冗余构造参数、空覆写、未用方法/导入与死分支。
- 仓库结构迁移为 multiloader 单仓(1.21.1-main → neoforge-1.21.1/、1.20.1-forge → forge-1.20.1/;跨 MC 大版本不设 common 共享层)。
- 原仓库 git 排除内容(AGENTS.md、docs/、temp/、scripts/、run/、deploy.ps1 等)迁入;tools/check_lang_sync.py 移至根 tools/。
- CI(build.yml) 双 JDK(17/21) 构建两子项目并对两者执行 lang 同步检查。

## 1.1.2-rc1

### 新内容

- **复仇之戟**:tooltip 新增当前攻击力/防御力加成显示;触发任意加成时显示"复仇之戟"状态效果(图标为物品本体图标),加成消失时自动移除。
- 新增**计时器守卫**:本模组所有有时长效果严格按 20 tick/秒 流动,不受任何加快/减慢效果时间的 buff(如神秘遗物+ 的烈焰之核)影响。
- 卡牌栏选择器改为 **3 列** 显示,同屏可见更多卡牌。
- 同步本地中文文本修改(看板立牌 tooltip 措辞、命运的指引描述)。
- 移除 CurseForge / Modrinth 独立更新日志文件及其关联。

### 内容与平衡性调整

- 岿然不动、狂暴持续时间调整为 **3:00**。

### 已修复BUG

- 修复骰神赐福进行中放入蓄力会立即转换为全力攻击的问题:赐福进行中放入的蓄力本次赐福不生效、不转换,改为下次触发骰神赐福时生效并在其结束时转换。
- 修复标记效果消失后发光效果残留的问题:发光改为与标记同寿命(不再使用无限时长),多层标记同步刷新,标记结束时发光随之消失。

### 工程

- 版本号更新为 `1.1.2-rc1`。
## 1.1.1-rc1

### 新内容

- 看板立牌（mimi）技能重做：被动改为“合成、奖励、返还卡牌时，每获得一张战斗牌 +1 星币；装备时筹码栏位 +1；每累计 25 个星币获得 1 个随机筹码（蓝色 60% / 紫色 35% / 金色 5%）”；主动改为“回收物品栏中全部卡牌（含专属牌），返还 N+1 张随机卡牌（不含专属牌）”。
- 新增通用谜之炖菜配方；诅咒之剑配方改用 `astral_dice:suspicious_stews` 标签。

### 界面与显示

- 新增骇客立牌“远程骇入”效果图标与描述；立牌主动技能 ActionBar 改用本地化名称；命运指引备注颜色修正。

### 已修复BUG

- 修复治愈图标在计时结束且未再次触发时未移除的问题。

### 工程

- 版本号更新为 `1.1.1-rc1`。

## 1.1.0-rc1

### 新内容

- 新增效果牌：以毒攻毒（移除最多 3 个原版负面效果，并获得生命恢复）。
- 新增效果：青之诅咒（护甲值 -20%，盔甲韧性归 0）。
- 新增筹码：维生素药丸、诅咒之剑、复仇之戟、贯穿之铳、可口糖果、友情徽章、探天卫星。
- 新增立牌：骇客立牌（nancy_lu）。
- 新增手持风扇-小 / 手持风扇-大筹码。

### 内容与平衡性调整

- 手持风扇-小配方调整为羽毛上排；手持风扇-大改为通用蓝→紫升级配方（以手持风扇-小为原料）。
- 狂暴配方简化为 1 火药 + 1 星币。
- 诅咒之剑：骰神赐福期间每轮最多触发一次击杀攻击力加成；上限默认 16、最大 32。
- 占星师立牌、忍者立牌品质提升为史诗，配方由黄金骰子改为钻石骰子。
- 完全隐身期间生物无法将玩家设为索敌目标。
- 创造栏筹码按星光、治愈、标记、无流派分类摆放；美工刀归入治愈类。

### 界面与显示

- 治愈类筹码与史莱姆立牌 tooltip 显示当前治愈点。
- 修复 tooltip 中按键名、`%` 号、计数器符号无法正确染色的问题。
- 修复诅咒之剑 tooltip 中青之诅咒描述多余空行。
- 立牌/材料 tooltip 分类从 `card` 移至 `sign`/`material`。

### 已修复BUG

- 修复大侦探立牌被动无法触发（主动技能与击杀“隐匿调查”目标）。
- 修复维生素药丸 tooltip 缺失；拾取卡牌不再触发维生素药丸效果。
- 友情徽章配方改为必须使用瞬间治疗药水。
- 修复 tooltip 颜色代码导致部分文本变白的问题。

## 1.0.3-rc1

### 内容与平衡性调整

- 防御牌耐久消耗恢复为仅 PvP 生效：玩家攻击带骰子的玩家时，双方触发骰神赐福，并消耗被攻击方防御牌耐久（每次赐福仅一次）；怪物攻击不再消耗防御牌耐久。
- 新增防御力/护甲值换算：1 防御力 = 2 护甲值。
- 效果牌、立牌、事件、筹码提供的防御力，无论是否触发骰神赐福，均按 1:2 折算为护甲值；只有战斗防御牌直接作为防御点加入骰神赐福。
- 重写“岿然不动”：使用后防御力 +2，并获得 抗性提升 II，持续 1:00。

### 工程

- 版本号更新为 `1.0.3-rc1`。

## 1.0.2-rc1

### 内容与平衡性调整

- 调整骰子筹码栏位数量（按 0★~3★）：普通 0/1/2/3，黄金 1/2/3/4，钻石 2/3/4/5，下界合金 3/4/5/6。
- 调整骰子攻击/防御卡牌栏数量（每侧）：普通 3，黄金 4，钻石 5，下界合金 6。
- 主动触发“骰神赐福”时不再消耗防御牌耐久。
- 玩家若攻击带有骰子的玩家，则自动触发双方拥有骰子玩家的骰神赐福，并消耗被攻击方的防御牌耐久度（每次骰神赐福仅消耗一次）。
- 秘密侦探立牌击杀奖励只返还攻击牌，攻击玩家不返还卡牌。
- 所有效果牌、立牌、事件、筹码提供的防御力加成，默认只作为护甲值加成；触发骰神赐福后作为防御点加入。
- 防御卡仅在骰神赐福期间作为防御点生效。

### 界面与显示

- 所有立牌 Tooltip 增加主动/被动技能名称，并将主动技能按键提示移至最上方。
- 主动技能按键提示整体改为白色，按键符本身为黄色。
- 大当家立牌主动技能图标改为使用大当家立牌图标。
- 统一 Tooltip 时间与数值格式：时间蓝色、数值黄色、不足 1:00 使用秒、药水时间使用括号。

### 已修复BUG

- 移除立牌主动技能触发成功后的通用“技能已激活”ActionBar，避免覆盖各立牌自身的特殊提示。
- 修复溅射/范围伤害未在被影响目标身上显示伤害数字的问题（大当家立牌扩散、定向爆破 AOE）。
- 本 Mod 创建的自定义效果无法被牛奶、蜂蜜瓶或 `/effect clear` 清除，仅玩家死亡可清除。
- 修复玩家死亡时部分效果状态未正确重置的问题，统一清理立牌等待、扩散、出牌、魔法箭袋、命运指引、调查阶段等状态。

## 1.0.1-rc1

### 内容与平衡性调整

- 重新设计治愈体系：为治愈增加独立计时器，默认每 30 秒触发一次治愈效果（仅在骰神赐福期间）。
- 触发骰神赐福时按治愈点×2 回血，计时结束治愈点减半。
- 美工刀触发阈值改为最大生命值 60% 以上即可触发，避免大部分时候都触发不了。
- 护法立牌：主动技能攻击力加成提高至 +4，持续时间提高至 2:00。
- 吸血鬼立牌：主动效果调整第二项为：不论玩家当前血量为多少，都同时视为满血和半血以下状态。
- 秘密侦探立牌：被动技能返还卡牌只对击杀 20 血以上敌对目标生效。
- 巧克力蛋糕/汉堡改为恢复最大生命值 20%/40%；奢华大餐改为恢复自身最大生命值 30%，并治疗同队/无队伍玩家。
- 治愈盾牌触发间隔改为 15 秒。
- 调整战斗牌耐久值：中/大/特大=10，暗影突袭=10，名刀=5，蓄力=1，全力攻击=2。
- 蓄力在赐福结束后返还全力攻击，并增加兜底检测与 ActionBar 提示。

### 已修复BUG

- 修复骰神赐福的触发目标判定和武器判定问题：仅近战武器触发，且友好/被动/未激怒中立生物不再触发。
- 修复骰子/筹码/立牌下蹲右键装备不生效。
- 修复战斗牌耐久条显示异常、Tooltip 丢失问题，恢复剩余次数显示。
- 修复大当家立牌触发伤害扩散时意外递归导致游戏崩溃。
- 治愈效果不再产生药水粒子。

### 质量更新

- 为部分行为增加 ActionBar 提示，同时受到事件影响的玩家也会有 ActionBar 提示。
- 大侦探随机事件提示会显示具体事件名称。
- 微调骰子卡牌选择界面的卡牌显示。
- 替换卡牌选择界面背景贴图。
- 修复 GitHub Actions 中 `gradlew` 无执行权限问题，并增加构建产物上传。
- 更新 README 模组介绍。
- 版本号更新为 `1.0.1-rc1`。

## 1.0-rc1

### 卡牌选择界面重写

- 使用了新卡牌容器贴图。
- 右侧显示攻击/防御 `<下限>-<上限>` 范围，并实时随卡牌增删更新。
- 下方新增卡牌选择器：攻击左列、防御右列，按物品栏顺序排列，支持滚动。
- 支持点击下方存放区域/右键将手持卡牌放回物品栏。
- 暗影突袭、名刀、蓄力、全力攻击图标缩放并向右下对齐。

### 耐久度机制

- 战斗牌改用 MC 原生耐久数据（damage/maxDamage），可被 Durability Tooltip 模组识别。
- 移除战斗牌自定义“剩余次数”Tooltip 文本。
- 防御牌现在会在受到伤害时正确消耗耐久。

### 配方调整

- 忍者立牌：铁锭 → 回响碎片。
- 占星师立牌：青金石块 → 海晶砂砾。
- 魔法秘典：中间书与笔 → 回响碎片。
- 魔法箭袋：中间光灵箭 → 回响碎片。
- 忍术飞镖：磁石 → 红石块。

### 效果牌提示

- 出牌数用完后显示 ActionBar 提示，冷却按所有效果牌中最长剩余时间计算。

### 工程配置

- 版本号更新为 `1.0-rc1`。

## 1.0-SNAPSHOT.23

### 物品 ID 与 Tag

- 效果牌统一为 `effect_card_*`。
- 筹码统一为 `*_chip`。
- 活体书页改为 `effect_card_living_page`。
- 新增物品 Tag：`dices`、`combat_cards`、`effect_cards`、`is_exclusive`、`signs`、`chips`、`materials`。

### 配方

- 基础骰子改为红石 + 石英块。
- 黄金/钻石/下界合金骰子统一为 `dice_upgrade` 有序升级；移除下界合金骰子锻造配方。
- 调整大量卡牌、效果牌、立牌、筹码配方。
- 筹码通用升级模板：
  - 蓝→紫：`LGL/GTG/PPP`
  - 紫→金：`RDR/DTD/GGG`
- 定向爆破改为有序对称配方。

### 战利品

- 星盘可从所有原版宝箱开出。
- 黄金星盘可从 `minecraft:chests/trial_chambers/reward_ominous` 开出。

### 创造栏

- 材料移至最前端。
- 调整效果牌与活体书页顺序。

### 数值与 Tooltip

- 拳击手套攻击：+2/+4/+8。
- 速度轮滑移速：+5%/+15%/+25%。
- 摩托头盔防御：+2/+4/+6；高级额外盔甲韧性 +2。
- 夹心饼干生命：+4/+8/+12。
- 缓冲盾牌冷却改为 30 秒。
- 手电筒：每 4 点星光 +1 攻击力。
- 效果牌 Tooltip 新增出牌数与当前周期伤害加成提示。

---

# 更新日志 / Changelog

本文档按语言完全拆分:上半部分为中文版,下半部分为英文版。
This changelog is fully split by language: the Chinese version comes first, followed by the English version.

# English Changelog

## Unreleased (1.2.0, 1.21.1 only)

> Convention: later edits to an entry already recorded for this version are merged into that entry — only the final version is kept, no “updated again” follow-ups.

### New Content

### Content & Balance

- Effect-card play system: the play window is kept (base 1 play + fixed/temporary +1 sources + Komachi's banked plays), while the per-window consecutive play cap (default 9, config `max_effect_card_plays`) is removed — bonus sources now stack without limit; the effect-card round now follows the new definition (a round ends only when all effect progress AND the play cooldown are done) (1.21.1 only).
- Mimi passive rework: +1 Star Coin per battle card gained via crafting or the active skill's returns (rewards/copies no longer trigger); every 25 battle cards returned by the active skill grants a random chip (was: every 25 Star Coins) (1.21.1 only).
- Jasmine passive addition: using an Express Delivery card immediately reduces the active skill's cooldown by 50%% of its maximum (1.21.1 only).
- Rin active: grants one Living Page, or two if you had none before (1.21.1 only).
- Bonnie: while hidden during an Investigation stage (Invisibility + Investigation Stage), mobs can no longer target you (1.21.1 only).
- Nancy Lu active: the consumed battle card is now taken from the main inventory only (ender chest / backpack-like containers no longer count); the attack bonus is at least +2, and a guaranteed +2 applies even when no battle card is available in the main inventory (1.21.1 only).
- Unwavering: defense +2 → armor +8 (equal to +4 defense in dice battles), moved to an attribute modifier on the effect so both real armor and dice combat apply without double counting (1.21.1 only).
- Full Power: durability 2 → 5 (1.21.1 only).
- Cursed Sword recipe: the middle ingredient is now Crying Obsidian instead of the Suspicious Stew item tag; the unused `suspicious_stews` item tag was removed (1.21.1 only).
- Blue Curse description: armor toughness "0" → "-100%%" (values unchanged) (1.21.1 only).
- Healing: the healing point cap is now fixed at 32 (was max(10, max HP ÷ 2)); Medkit chips no longer restore 2/6 HP on equip (blessing points kept) (1.21.1 only).
- Vitamin Pill: triggers on any crafted/obtained card (was crafting/reward sources only); the Satellite chip's Orbital Strike replenishment now uses the unified card-grant path (1.21.1 only).
- Satellite third ability: the "play count +1 after using an Orbital Strike" is now once per 1:00 (was once per round) (1.21.1 only).
- Naming unification: Living Page Java identifiers and the effect registry id are unified to `LIVING_PAGE`/`living_page` (item id `effect_card_living_page` unchanged) (1.21.1 only).
- Defense conversion: defense from effect cards/signs/chips is now converted to real armor (1 defense = 2 armor via ARMOR attribute modifiers) and no longer participates in dice-combat defense modifiers — only battle defense cards (range-varying values) remain there. Affected sources: Jasmine (+2 armor per stack, armor cap 40), Padman (-4 to +8 armor), Papara (+6 armor at half HP), Revenge Halberd (+12 armor), Nancy Lu (+6 armor on defensive passive), Fen (+4 armor with Recharged Energy); Resistance no longer adds dice-defense points (its vanilla damage reduction still applies) (1.21.1 only).
- Monster armor-to-defense conversion synced with players: monster defense now uses armor÷2 like players (1 defense = 2 armor); the Piercing Gun chip's target-defense calculation was synced too (1.21.1 only).
- Piercing Gun recipe: Netherite Scrap → Netherite Ingot (both versions).
- Restored the "Adrenaline - Common" chip (epic) and rolled back the "Adrenaline - High-Grade" (legendary) recipe to an upgrade chain: Common = attack/defense +3 while below half max HP; High-Grade = +8 with a 20%% dodge chance against a hostile attack while the bonus is active. Recipes: Common = ZXZ/DCD/PPP (Z = Potion of Regeneration, X = Nether Star, D = Wither Rose, C = Blank Chip, P = Star Plate); High-Grade = RZR/ZOZ/PPP (R = Redstone, Z = Diamond, O = Adrenaline - Common, P = Golden Star Plate) (both versions).
- Target chip: after triggering the Dice Blessing, apply 1 Mark layer to the **nearest hostile target** (the old "random hostile within target-chip range" logic and the `target_chip_range` range config were removed; no distance cap) (1.21.1 only).
- Sandwich (Gourmet): max health +12 → +8; removed the "gain 1 Counterattack layer per 1:00 while below half max HP" passive; new: while max HP exceeds 20, gain +1 attack per 4 HP above 20 (1.21.1 only).
- Fixed the dice tooltip showing attack cards' bonus mislabeled as "dice" and a coloring issue: attack cards (Medium/Large/Epic/Meito) now uniformly read "attack", the range no longer carries a "+" prefix, matching the defense-card and standalone-card tooltip format (1.21.1 only).
- Dice card-slot balance: the total card slots are now determined by star level only (independent of dice tier) — 0★ = 4 (2 attack + 2 defense), 1★ = 6 (3+3), 2★ = 8 (4+4), 3★ = 12 (6+6); usable slots strictly follow the star level, with no extra hidden usable slots (both versions).
- Dice upgrade recipes now use tier tags for the upgrade base: added four item tags — `astral_dice:dice_t0` (base) / `dice_t1` (golden) / `dice_t2` (diamond) / `dice_t3` (netherite); the golden / diamond / netherite dice upgrade recipe inputs now reference these tags instead of specific items (both versions).
- Counterattack effect: player death no longer clears counterattack layers — the current layer count is captured on death and restored on respawn (layers remain continuously visible via the HUD icon and are removed automatically at 0) (both versions).
- Effect level badge now uses Arabic numerals and has a raised cap: vanilla rendered Roman numerals (II–X) up to level 10; it now shows an Arabic-numeral badge (e.g., "Healing 3", "Healing 32") up to level 100 (new `EffectRenderingInventoryScreenMixin`) (1.21.1 only).

### Bug Fixes

- Fixed the `PiglinAiMixin` injection signature: vanilla `PiglinAi.isWearingGold` takes a `LivingEntity` in 1.21.1; it was written against `Player`, causing a runtime Mixin apply failure (invalid descriptor) in the modpack — it now accepts `LivingEntity` and applies the Netherrack-die check only to players (1.21.1 only).
- Death-cleanup adjustments: totem-canceled deaths no longer trigger any cleanup; Misaki loses all Sword Qi stacks on death (tooltip note added); Papara's active effect is cleared on death; Bonnie keeps investigation progress on death (unequip only); Komachi/Rin effect-card damage bonuses survive death; removed the leftover no-reader DamageEffectBonus reset in the death handler (1.21.1 only).
- Fixed the Komachi sign's active failing when effect-card play progress existed or the cooldown was running: the play-count +1 is now a banked extra-play token (consumed per actual play, persists across windows, unaffected by burst-full or cooldown); the old boolean flag and its leftover calls were removed (1.21.1 only).

### Project
- Integrated JEI into the dev test environments for recipe checking (forge-1.20.1 15.56.0.205 / neoforge-1.21.1 19.39.0.372); the 1.20.1 dev pack mods now come from curse maven via modImplementation so production mixin mods load in the dev environment.

## 1.1.3

### New Content

- During the Dice Blessing, cards can no longer be inserted into or removed from the dice card interface (server-authoritative guards + client-side blocking), and the screen shows a red warning at the top (both versions).
- Effect-card copy/refund now covers every effect card — the Komachi sign, Magic Tome and Magic Quiver no longer exclude any card (healing/damage/exclusive cards included; exclusive copies are bound to their new owner) (both versions).
- Patchouli handbook: already present on 1.21.1; newly ported to 1.20.1 (109 pages + 175×2 guide keys, new Patchouli 1.20.1-85-forge dependency, Patchouli added to the 1.20.1 modpack; the 4 Enigmatic-Legacy+ link texts rewritten for Enigmatic Legacy).
- Handbook category icons (both versions): Getting Started = No.1 Player Sign, Materials = Star Coin, Cards = Epic Attack Card.

### Bug Fixes

- **1.20.1 handbook registration completed**: the earlier port copied only the book pages (assets) but missed the book definition (book.json), crafting recipe, creative-tab book item and book model, so the book item was missing and no page text (including the integration texts) was reachable; all are now in place (the recipe uses the 1.20.1 NBT format) and republished.
- The effect-removal guard no longer swallows the mod's own removals — a new internal-removal channel (ModEffectRemoval) keeps ready/count/healing indicators from lingering forever (both versions).
- 以毒攻毒 removed effects while iterating the live view, risking a ConcurrentModificationException on the server tick — now iterates a snapshot (both versions).
- Copy/refund mappings missing card ids silently granted King Power — unified into BaseEffectCardItem.cardByTypeId covering every effect card (both versions).
- Random card pools diverged: ALL now includes 以毒攻毒 and BATTLE includes defense cards per its contract (both versions).
- Parunan's in-hand right-click bypassed cooldown/wait checks and could fire the active unexpectedly — right-click now only equips/replaces, actives are triggered via the keybind/equipped path only (both versions).
- The FTB Teams UUID-member branch never added anyone — aligned with the OPAC branch (both versions).
- AoE victims (cleave / directional blast) no longer take a second full dice-combat hit (both versions).
- Nancy Lu unequip wiped invulnerability/invisibility it never granted, and its onCurioTick expiry check was always true at the default value, stripping other sources' invisibility every tick — now only self-granted state is cleared (both versions).
- The mark's glow shares the mark's lifetime (milk/effect clear no longer removes it alone) and no longer shows a HUD effect icon (both versions).
- Patchouli "Format error" entries: literal `%` in guide texts (18 entries, zh+en) escaped to `%%`; the 4 tooltip keys with the same latent issue fixed too (1.21.1; the 1.20.1 handbook was ported already-escaped and the same 4 tooltip keys were fixed).
- Client damage numbers never expired due to a wrong-bus subscription plus a network-thread race (1.21.1; 1.20.1 was already correct).
- Skill renames: Fen's active 运攻 → 运功 and Nancy Lu's active 远程骇入 → 远程侵入 (both versions).

### Project

- Versions: 1.21.1 = `1.1.3-rc1+neoforge_1.21.1`, 1.20.1 = `1.1.3-pre1+forge_1.20.1`; rebuilt and published to each modpack (old artifacts auto-cleaned).
- ModEventHandlers subscribers moved to their feature homes (DiceCombatEvents, ModTooltipHandler, LootInjectionHandler, AnvilUpgradeHandler, PlayerLifecycleHandler, ModEffectEvents, PlayerTickEvents, plus sign/chip/card/manager classes) (1.21.1 only).
- Removed the charge-deferral timing code for Charge cards (the charge_defer attachment and branches) — impossible now that the card slots lock; Full Power is always refunded when the Blessing ends (both versions).
- Removed the write-only PlayerResourceRegistry (PlayerResource/ResourceType and the healing/starlight registration stubs) (both versions).
- Full code audit and cleanup (led by 1.21.1, synced to 1.20.1): removed per-tick effect re-apply sync packets (cutter/revenge-halberd/healing), per-tick enchantment ResourceKey allocation, per-tick full recompute while a menu is open (20-tick throttle) and double isActive evaluation; consolidated duplicated damage-number senders and card-type mappings; removed 8 unused data components, the isChipItem chain, redundant dice ctor args, empty overrides, unused methods/imports and dead branches.
- Repository restructured as a multiloader monorepo (1.21.1-main → neoforge-1.21.1/, 1.20.1-forge → forge-1.20.1/; no shared common source set across MC major versions).
- Formerly git-excluded content (AGENTS.md, docs/, temp/, scripts/, run/, deploy.ps1, etc.) migrated; tools/check_lang_sync.py moved to the repo root tools/.
- CI (build.yml) builds both subprojects with dual JDKs (17/21) and runs the lang sync check for both.

## 1.1.2-rc1

### New Content

- **Revenge Halberd**: tooltip now shows the current attack/defense bonus; a status effect with the halberd's own icon appears while either bonus is active and is removed when the bonus ends.
- Added a timer guard: all timed effects of this mod now strictly tick at 20 t/s, immune to buffs that speed up or slow down effect durations (e.g. the Blazing Core from Enigmatic Legacy+).
- The card selector now displays **3 columns**, showing more cards at once.
- Synced local zh_cn.json text updates (Mimi sign tooltip wording, Fate Guidance description).
- Removed the separate CurseForge and Modrinth changelog files and their references.

### Balance Changes

- Unwavering and Berserk durations adjusted to **3:00**.

### Bug Fixes

- Fixed Charge converting to Full Power immediately when placed during an active Dice Blessing: a Charge placed mid-blessing is inactive for the current blessing and now only takes effect on the next blessing, converting to Full Power when that blessing ends.
- Fixed the glowing effect lingering after the mark disappears: glowing now shares the mark's lifetime (no more infinite duration), is refreshed on layer transitions, and ends together with the mark.

### Project

- Version updated to `1.1.2-rc1`.
## 1.1.1-rc1

### New Content

- Mimi sign reworked: passive now grants +1 Star Coin per battle card gained from crafting, rewards, or card returns, +1 chip slot while equipped, and a random chip every 25 Star Coins (Blue 60% / Purple 35% / Gold 5%); active now recycles all cards in the inventory (including exclusive cards) and returns N+1 random cards (exclusive cards excluded).
- Added a generic Suspicious Stew recipe; Cursed Sword now uses the `astral_dice:suspicious_stews` tag.

### UI & Display

- Added Nancy Lu "Remote Hack" effect icon and description; sign active-skill ActionBar now uses localized names; fixed Fate Guidance note color.

### Bug Fixes

- Fixed the healing icon not being removed when its timer ends without re-triggering.

### Project

- Version updated to `1.1.1-rc1`.

## 1.1.0-rc1

### New Content

- Added Fight Poison with Poison: removes up to 3 vanilla negative effects and grants Regeneration.
- Added Blue Curse: -20% armor and zero armor toughness.
- Added chips: Vitamin Pill, Cursed Sword, Revenge Halberd, Piercing Gun, Candy, Friendship Badge, Satellite.
- Added Nancy Lu (Hacker) sign.
- Added Hand Fan Small and Hand Fan Big chips.

### Content & Balance Changes

- Adjusted Hand Fan Small recipe; Hand Fan Big now uses the generic Blue→Purple upgrade recipe.
- Simplified Berserk recipe to 1 Gunpowder + 1 Star Coin.
- Cursed Sword kill bonus now triggers at most once per Dice Blessing; cap default 16, max 32.
- Astrologer and Ninja signs upgraded to Epic and now require Diamond Dice.
- While fully invisible, mobs cannot target the player.
- Creative chip tab regrouped by Starlight, Healing, Mark, and No-school; Cutter chips moved to Healing.

### UI & Display

- Healing chips and Slime sign now show current healing points.
- Fixed sign key, percent sign, and counter symbol coloring in tooltips.
- Fixed an extra blank line around Blue Curse in the Cursed Sword tooltip.
- Moved sign and material tooltip keys out of the card category.

### Bug Fixes

- Fixed Great Detective passive not triggering from active skill or Undercover Investigation kills.
- Fixed missing Vitamin Pill tooltip; picking up cards no longer triggers it.
- Friendship Badge recipe now requires an Instant Healing potion.
- Fixed tooltip color-code issues causing some text to appear white.

## 1.0.3-rc1

### Content & Balance Changes

- Defense card durability is now only consumed in PvP: when attacking another dice-holding player, both gain Dice Blessing and the defender consumes defense card durability once per blessing; monster attacks no longer consume it.
- Added a defense-to-armor conversion: 1 defense = 2 armor.
- Defense bonuses from effect cards, signs, events, and chips always convert to armor at 1:2 regardless of Dice Blessing; only battle defense cards add directly to defense points.
- Reworked Unwavering: grants +2 defense and Resistance II for 1:00.

### Project

- Version updated to `1.0.3-rc1`.

## 1.0.2-rc1

### Content & Balance Changes

- Adjusted dice chip slot counts (0★~3★): Basic 0/1/2/3, Golden 1/2/3/4, Diamond 2/3/4/5, Netherite 3/4/5/6.
- Adjusted per-side attack/defense card slots: Basic 3, Golden 4, Diamond 5, Netherite 6.
- Actively triggering Dice Blessing no longer consumes defense card durability.
- When a player attacks another player with a dice, both dice-holding players automatically gain Dice Blessing, and the defender's defense card durability is consumed at most once per blessing.
- Bonnie sign kill rewards now grant only attack cards, and attacking players grants no card.
- Defense bonuses from effect cards, signs, events, and chips now count as armor value by default, and become defense points while Dice Blessing is active.
- Defense cards only provide defense points during Dice Blessing.

### UI & Display

- Added active/passive skill names to all sign tooltips and moved the key hint to the top.
- The sign key hint is now white, with the key symbol in yellow.
- The Fen sign active skill icon now uses the Fen sign icon.
- Unified tooltip formatting: blue time, yellow values, seconds for durations under 1:00, and parentheses around potion effect times.

### Bug Fixes

- Removed the generic “Skill activated” ActionBar after sign skills trigger, preventing it from overwriting sign-specific messages.
- Fixed missing damage numbers on affected targets for splash/AOE damage (Fen cleave and Directional Blast AOE).
- Custom effects created by this mod can no longer be removed by milk, honey bottles, or `/effect clear`; only player death can remove them.
- Fixed some effect states not being reset on player death; sign-ready, cleave, play-count, Magic Quiver, Fate Guidance, and investigation states are now reset properly.

## 1.0.1-rc1

### Content & Balance Changes

- Reworked the healing system with an independent timer: healing triggers every 30 seconds by default (only during Dice Blessing).
- Healing Points heal ×2 when Dice Blessing triggers, and halve when the timer ends.
- Cutter chips now trigger above 60% of max HP, preventing them from being unable to trigger in most situations.
- Misaki sign: active skill attack bonus increased to +4 and duration increased to 2:00.
- Papara sign: the second active effect now treats the player as both full HP and below half HP regardless of current health.
- Bonnie sign: passive card reward now only applies when killing hostile targets with more than 20 HP.
- Chocolate Cake and Hamburger now heal 20%/40% of max HP; Luxury Feast heals 30% of the user's max HP and also heals teammates and teamless players.
- Buffer Shield trigger cooldown changed to 15 seconds.
- Adjusted battle card durability values: Medium/Large/Epic=10, Shadow Strike=10, Meito=5, Charge=1, Full Power=2.
- Charge now refunds Full Power after blessing, with fallback check and ActionBar message.

### Bug Fixes

- Fixed Dice Blessing target and weapon checks: it now only triggers with melee weapons and no longer triggers on friendly, passive, or non-angered neutral mobs.
- Fixed sneak-right-click auto-equip for dice/chips/signs.
- Fixed battle card durability bar display issues and missing tooltip, restored remaining uses display.
- Fixed an unexpected recursion crash when Fen sign triggered damage cleave.
- Healing effect no longer shows potion particles.

### Quality Improvements

- Added ActionBar feedback for several actions, and players affected by events also receive ActionBar messages.
- Fanny random event messages now show the triggered event name.
- Fine-tuned card display in the dice card selection GUI.
- Replaced the card selection background texture.
- Fixed gradlew permission in CI and added artifact upload.
- Updated README with mod introduction.
- Version updated to `1.0.1-rc1`.

## 1.0-rc1

### Card Selection GUI Rewrite

- Used the new card container texture.
- Right side now shows attack/defense `<min>-<max>` ranges, updating live as cards are added or removed.
- Added a card selector at the bottom: attack on the left, defense on the right, sorted by inventory order, with scrolling support.
- Added support for returning a held card to the inventory by clicking the storage area or right-clicking.
- Shadow Strike, Meito, Charge, and Full Power icons are scaled and aligned to the bottom-right.

### Durability System

- Battle cards now use vanilla Minecraft durability data (damage/maxDamage), recognized by Durability Tooltip mods.
- Removed the custom "remaining uses" tooltip text from battle cards.
- Defense cards now correctly consume durability when the wearer takes damage.

### Recipe Adjustments

- Komachi sign: Iron Ingot → Echo Shard.
- Haiqing sign: Lapis Block → Prismarine Crystals.
- Magic Tome: center Book and Quill → Echo Shard.
- Magic Quiver: center Spectral Arrow → Echo Shard.
- Ninja Star: Lodestone → Redstone Block.

### Effect Card Feedback

- Shows an ActionBar message when play count is exhausted; cooldown uses the longest remaining time among all effect cards.

### Project Configuration

- Version updated to `1.0-rc1`.

## 1.0-SNAPSHOT.23

### Item IDs & Tags

- Effect cards unified to `effect_card_*`.
- Chips unified to `*_chip`.
- Living Book Page renamed to `effect_card_living_page`.
- Added item tags: `dices`, `combat_cards`, `effect_cards`, `is_exclusive`, `signs`, `chips`, `materials`.

### Recipes

- Basic dice recipe changed to Redstone + Quartz Block.
- Golden/Diamond/Netherite dice now use the `dice_upgrade` shaped upgrade; removed the Netherite dice smithing recipe.
- Adjusted many battle card, effect card, sign, and chip recipes.
- Chip generic upgrade templates:
  - Blue→Purple: `LGL/GTG/PPP`
  - Purple→Gold: `RDR/DTD/GGG`
- Directional Blast changed to a shaped symmetrical recipe.

### Loot

- Star Plates can now be found in all vanilla chests.
- Golden Star Plates can be found in `minecraft:chests/trial_chambers/reward_ominous`.

### Creative Tab

- Materials moved to the front of the creative tab.
- Adjusted the order of effect cards and Living Book Page.

### Values & Tooltip

- Boxing Gloves attack: +2/+4/+8.
- Speed Skates movement speed: +5%/+15%/+25%.
- Moto Helmet defense: +2/+4/+6; High tier additionally grants +2 armor toughness.
- Sandwich max health: +4/+8/+12.
- Buffer Shield cooldown changed to 30 seconds.
- Flashlight: +1 attack per 4 Starlight.
- Added effect card tooltip hints for play count and current-cycle damage bonus.
