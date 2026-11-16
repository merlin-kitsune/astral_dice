# B36 — fabric-1.20.1 语言文件收尾对齐报告

- 分支：`multi-main`
- 口径（用户裁决 2026-11-01）：一律以最新主线版本为准，旧版本差异全部放弃；参照线 = `forge-1.20.1`（与 `neoforge-1.21.1` 同源文案）
- 改动文件（仅这三个）：
  - `fabric-1.20.1/src/main/resources/assets/astral_dice/lang/en_us.json`
  - `fabric-1.20.1/src/main/resources/assets/astral_dice/lang/zh_cn.json`
  - `fabric-1.20.1/src/main/resources/assets/astral_dice/lang/ja_jp.json`
- 行尾：三份均 **纯 CRLF**、无 BOM；改动一律用宿主 `edit` 工具逐处行内替换（未用 `json.dump`，键序与缩进未变）
- 键数：1094 → **1078**（删 12 个 `msg.astral_dice.fanny_event*` + 4 个 `astral_dice.guide.entry.*_sign.3`）
- 本报告行尾：LF

---

## 一、按 forge 逐字对齐的 15 个键（三语）

`依据` 列为 forge-1.20.1 对应语言文件的**行号**（1-based），即被抄写的源行。

### 1.1 en_us（15 处）

**1) `astral_dice.guide.entry.fanny_sign.1`**（依据 `forge-1.20.1/src/main/resources/assets/astral_dice/lang/en_us.json:124`）
- 改前：`Active Skill - Trouble Maker: immediately trigger one random event: gain Regeneration (0:30); gain Strength (0:30); gain an Attack Card (XL); gain a random effect card (excl. exclusive) + 3 Star Coins; gain Nourishment (2:00) (Farmer's Delight only) and Saturation (0:30); gain Speed (0:30).`
- 改后：`Active Skill - Event Choice: randomly draw 2 global events from the current event pool, choose 1 and trigger it; you must select one and click Confirm for it to trigger.`

**2) `astral_dice.guide.entry.fanny_sign.2`**（依据 `forge-1.20.1/src/main/resources/assets/astral_dice/lang/en_us.json:125`）
- 改前：`(continued) suffer Instant Damage; gain Poison (0:15); gain Hunger (0:30) and Nausea (0:07); gain Wither (0:07) and Darkness (0:05); gain Weakness (0:15) and Mining Fatigue (0:30).`
- 改后：`(continued) If you do not choose within 15 seconds, or if you die or leave the game, the first option is triggered automatically; confirming closes the panel immediately and starts the event flow; the skill sound plays when you press the skill key.`

**3) `astral_dice.guide.entry.airbag_chip.1`**（依据 en_us:238）
- 改前：`When taking fatal damage, consume 6 charge to negate the damage (cooldown 1:00). This saving effect takes priority over the Whetstone, the Totem of Undying and the Ender Die; lethal damage that bypasses invulnerability is also negated (e.g. the /kill command).`
- 改后：`When taking fatal damage, consume 6 charge to negate the damage (cooldown 1:00). This saving effect takes priority over the Whetstone, the Totem of Undying and the Ender Die; lethal damage that bypasses invulnerability is also negated (e.g. the /kill command); lethal damage from falling into the void cannot be negated. In addition, taking damage grants 1 charge (cooldown 15 seconds).`

**4) `tooltip.astral_dice.chip.airbag`**（依据 en_us:636）
- 改前：`On taking fatal damage: consume §e6§7 charge to negate the damage\nTakes priority over the Whetstone, the Totem of Undying and the Ender Die\nAlso works against damage that bypasses invulnerability\nCooldown §91:00§r`
- 改后：`On taking fatal damage: consume §e6§7 charge to negate the damage\nTakes priority over the Whetstone, the Totem of Undying and the Ender Die\nCooldown §91:00§7\nOn taking damage: charge §e+1§7 (cooldown §915s§7)`

**5) `astral_dice.guide.entry.member_recommendation_chip.1`**（依据 en_us:234）
- 改前：`Each time the Dice Blessing triggers, gain a random card.`
- 改后：`When Dice Blessing triggers, if your inventory holds fewer than 5 cards, gain a random card. While equipped, all cards in your own shop cost 1 Star Coin less (stacks with the Parunan Sign's discount, down to 0 Star Coins).`

**6) `tooltip.astral_dice.chip.member_recommendation`**（依据 en_us:613）
- 改前：`Each time a Dice Blessing triggers, gain one random card`
- 改后：`Gain a random card when Dice Blessing triggers\nOnly triggers while your inventory holds fewer than §e5§7 cards\nWhile equipped, all cards in your own shop cost §e-1§7 Star Coin`

**7) `astral_dice.guide.entry.eagle_scope_chip.1`**（依据 en_us:92）
- 改前：`Hitting a target applies 1 Mark layer; this combat, gain +2 attack per Mark layer on the target.`
- 改后：`Hitting a target applies 1 Mark layer; deal +2 extra attack damage per Mark layer on the target.`

**8) `tooltip.astral_dice.chip.eagle_scope`**（依据 en_us:596）
- 改前：`Hitting a target applies §e1§7 Mark layer. This combat: §e+2§7 attack per target Mark layer`
- 改后：`Hitting a target applies §e1§7 Mark layer. Deal §e2§7 extra attack damage per Mark layer on the target`

**9) `astral_dice.guide.entry.bank_card_low_chip.1`**（依据 en_us:43）
- 改前：`Grants 4 base Starlight layers while equipped.`
- 改后：`Gain 4 Starlight on equip; after each successful Starlight exchange, Starlight is replenished by your worn Bank Cards (Low / High / Unlimited) and Star Coin Hammer - this chip counts as 4, multiple chips stack, up to 32.`

**10) `astral_dice.guide.entry.bank_card_high_chip.1`**（依据 en_us:42）
- 改前：`Grants 7 base Starlight layers while equipped.`
- 改后：`Gain 7 Starlight on equip; after each successful Starlight exchange, Starlight is replenished by your worn Bank Cards (Low / High / Unlimited) and Star Coin Hammer - this chip counts as 7, multiple chips stack, up to 32.`

**11) `astral_dice.guide.entry.bank_card_unlimited_chip.1`**（依据 en_us:44）
- 改前：`Gain 3 Starlight on equip; after each Dice Blessing ends, you and friendly players gain 3 Star Coins (team members if you are in a team; all online players if you are not in any team).`
- 改后：`Gain 3 Starlight on equip; after each successful Starlight exchange, Starlight is replenished by your worn Bank Cards (Low / High / Unlimited) and Star Coin Hammer - this chip counts as 3, multiple chips stack, up to 32; after each Dice Blessing ends, you and friendly players gain 3 Star Coins (team members if you are in a team; all online players if you are not in any team).`

**12) `astral_dice.guide.entry.star_coin_hammer_chip.1`**（依据 en_us:219）
- 改前：`Gain 5 Starlight on equip; if you hold at least 32 Star Coins, each Dice Blessing consumes 18 Star Coins and grants 30%% of your held Star Coins as Attack Damage, up to 100 (a Star Coin Bag counts as 9; ...)`
- 改后：`Gain 5 Starlight on equip; after each successful Starlight exchange, Starlight is replenished by your worn Bank Cards (Low / High / Unlimited) and Star Coin Hammer - this chip counts as 5, multiple chips stack, up to 32; if you hold at least 32 Star Coins, each Dice Blessing consumes 18 Star Coins and grants 30%% of your held Star Coins as Attack Damage, up to 100 (a Star Coin Bag counts as 9; when loose coins are short it breaks open one bag, takes only the coins needed and leaves the rest in your inventory, and consumes nothing if they cannot be stored safely).`
- 说明：改前 = 改后去掉新增的「装备会话闸门 + 每次兑换后按银行卡/星币锤补充、计 5 层、上限 32」一段，其余逐字相同。

**13) `tooltip.astral_dice.chip.bank_card_high`**（依据 en_us:578）
- 改前：`Provides §e7§7 base Starlight (increases base value)`
- 改后：`Gain §e7§7 Starlight on equip`

**14) `tooltip.astral_dice.chip.bank_card_low`**（依据 en_us:579）
- 改前：`Provides §e4§7 base Starlight (increases base value)`
- 改后：`Gain §e4§7 Starlight on equip`

**15) `tooltip.astral_dice.sign.fanny_active`**（依据 en_us:683）
- 改前：`Immediately trigger one random event:\n  §9Regeneration (0:30)§r\n  §9Strength (0:30)§r\n  Gain: Attack Card XL\n  Gain a random effect card (excl. exclusive) + §e3§7 Star Coins\n  §9Nourishment (2:00)§7 (Farmer's Delight only) + §9Saturation (0:30)§r\n  §9Speed (0:30)§r\n  §cInstant Damage§r\n  §cPoison (0:15)§r\n  §cHunger (0:30)§7 + §cNausea (0:07)§r\n  §cWither (0:07)§7 + §cDarkness (0:05)§r\n  §cWeakness (0:15)§7 + §cMining Fatigue (0:30)§r`
- 改后：`Choose 1 of 2 global events and trigger it`
- 说明：该项**超出任务清单字面范围**（任务只点名 guide 条目 .1/.2），但它是同一处旧行为残留（玩家在物品提示里会直接看到旧的随机事件列表），与第 1 条同源缺陷，故一并按 forge 对齐。

### 1.2 zh_cn（13 处）

**1) `astral_dice.guide.entry.fanny_sign.1`**（依据 `forge-1.20.1/src/main/resources/assets/astral_dice/lang/zh_cn.json:124`）
- 改前：`主动技能-麻烦制造者：立即触发以下随机事件之一：获得生命恢复 (0:30)；获得力量 (0:30)；获得一张攻击-特大；获得一张随机效果牌（不含专属）+ 3 星币；获得滋养 (2:00)（需安装农夫乐事）与饱和 (0:30)；获得迅捷 (0:30)。`
- 改后：`主动技能-事件选择：从当前事件池随机抽出 2 个全体事件，选择其中 1 个并触发；必须选中并点击确认才会触发。`

**2) `astral_dice.guide.entry.fanny_sign.2`**（依据 zh_cn:125）
- 改前：`（接上）受到瞬间伤害；获得中毒 (0:15)；获得饥饿 (0:30) 与反胃 (0:07)；获得凋灵 (0:07) 与黑暗 (0:05)；获得虚弱 (0:15) 与挖掘疲劳 (0:30)。`
- 改后：`（接上）15 秒内未选择、死亡或退出游戏时强制触发第一个；确认后立即关闭界面并进入事件流程；技能音效在按下技能键时播放。`

**3) `astral_dice.guide.entry.airbag_chip.1`**（依据 zh_cn:238）
- 改前：`受到致命伤害时，消耗 6 层充能，使本次伤害无效（冷却 1:00）。该保命效果优先于磨刀石、不死图腾与末影骰子；无视无敌的致死伤害同样有效（如指令 /kill）。`
- 改后：`受到致命伤害时，消耗 6 层充能，使本次伤害无效（冷却 1:00）。该保命效果优先于磨刀石、不死图腾与末影骰子；无视无敌的致死伤害同样有效（如指令 /kill）；掉入虚空的致命伤害无法被无效化。此外，受到伤害时充能 +1（冷却 15 秒）。`

**4) `tooltip.astral_dice.chip.airbag`**（依据 zh_cn:636）
- 改前：`受到致命伤害时：消耗 §e6§7 层充能，使本次伤害无效\n优先于磨刀石、不死图腾与末影骰子\n无视无敌的致死伤害同样有效\n冷却 §91:00§r`
- 改后：`受到致命伤害时：消耗 §e6§7 层充能，使本次伤害无效\n优先于磨刀石、不死图腾与末影骰子\n冷却 §91:00§7\n受到伤害时：充能 §e+1§7（冷却 §915 秒§7）`

**5) `astral_dice.guide.entry.member_recommendation_chip.1`**（依据 zh_cn:234）
- 改前：`每次触发骰神赐福时，获得一张随机卡牌。`
- 改后：`触发骰神赐福时，若物品栏卡牌数少于 5 张，获得一张随机卡牌。装备期间，自身商店内所有卡牌价格 -1 星币（可与经商立牌的降价叠加，最低 0 星币）。`

**6) `tooltip.astral_dice.chip.member_recommendation`**（依据 zh_cn:613）
- 改前：`每次触发骰神赐福时，获得一张随机卡牌`
- 改后：`触发骰神赐福时，获得一张随机卡牌\n仅在物品栏卡牌数少于 §e5§7 张时触发\n装备期间，自身商店内所有卡牌价格 §e-1§7 星币`

**7) `astral_dice.guide.entry.eagle_scope_chip.1`**（依据 zh_cn:92）
- 改前：`攻击时对目标施加 1 层标记；本次战斗提升目标标记层数 ×2 的攻击力。`
- 改后：`攻击时对目标施加 1 层标记；对带标记目标额外造成其标记层数 ×2 的攻击伤害。`

**8) `tooltip.astral_dice.chip.eagle_scope`**（依据 zh_cn:596）
- 改前：`攻击时对目标施加 §e1§7 层标记，本次战斗提升目标标记层数 §e2§7 倍的攻击力`
- 改后：`攻击时对目标施加 §e1§7 层标记，对带标记目标额外造成其标记层数 §e2§7 倍的攻击伤害`

**9) `astral_dice.guide.entry.bank_card_low_chip.1`**（依据 zh_cn:43）
- 改前：`装备期间获得 4 层基础星光。`
- 改后：`装备时获得 4 层星光；每次星光兑换成功后，按当前佩戴的银行卡（余额少 / 余额多 / 用不完）与星币锤补充星光 —— 本筹码计 4 层，多枚叠加，上限 32。`

**10) `astral_dice.guide.entry.bank_card_high_chip.1`**（依据 zh_cn:42）
- 改前：`装备期间获得 7 层基础星光。`
- 改后：`装备时获得 7 层星光；每次星光兑换成功后，按当前佩戴的银行卡（余额少 / 余额多 / 用不完）与星币锤补充星光 —— 本筹码计 7 层，多枚叠加，上限 32。`

**11) `astral_dice.guide.entry.bank_card_unlimited_chip.1`**（依据 zh_cn:44）
- 改前：`装备时获得 3 层星光；每次骰神赐福结束后，自身及友方玩家（已加入队伍时为同队成员，未加入任何队伍时为全体玩家）获得 3 星币。`
- 改后：`装备时获得 3 层星光；每次星光兑换成功后，按当前佩戴的银行卡（余额少 / 余额多 / 用不完）与星币锤补充星光 —— 本筹码计 3 层，多枚叠加，上限 32；每次骰神赐福结束后，自身及友方玩家（已加入队伍时为同队成员，未加入任何队伍时为全体玩家）获得 3 星币。`

**12) `astral_dice.guide.entry.star_coin_hammer_chip.1`**（依据 zh_cn:219）
- 改前：`装备时获得 5 层星光；若物品栏持有至少 32 枚星币，每次进入骰神赐福消耗 18 星币，并按持有星币总数的 30%% 提升攻击伤害（上限 100；星币袋按 9 枚计；零散星币不足时自动拆开 1 个星币袋、只扣走所需枚数、其余留在物品栏，无法安全拆袋时不消耗）。`
- 改后：`装备时获得 5 层星光；每次星光兑换成功后，按当前佩戴的银行卡（余额少 / 余额多 / 用不完）与星币锤补充星光 —— 本筹码计 5 层，多枚叠加，上限 32；若物品栏持有至少 32 枚星币，每次进入骰神赐福消耗 18 星币，并按持有星币总数的 30%% 提升攻击伤害（上限 100；星币袋按 9 枚计；零散星币不足时自动拆开 1 个星币袋、只扣走所需枚数、其余留在物品栏，无法安全拆袋时不消耗）。`

**13) `tooltip.astral_dice.sign.fanny_active`**（依据 zh_cn:683）
- 改前：`立即触发以下随机事件之一：\n  获得 §9生命恢复 (0:30)§r\n  获得 §9力量 (0:30)§r\n  获得一张：攻击-特大\n  获得一张随机效果牌（不含专属）+ §e3§7 星币\n  获得 §9滋养 (2:00)§7（需安装农夫乐事）和 §9饱和 (0:30)§r\n  获得 §9迅捷 (0:30)§r\n  获得 §c瞬间伤害§r\n  获得 §c中毒 (0:15)§r\n  获得 §c饥饿 (0:30)§7 和 §c反胃 (0:07)§r\n  获得 §c凋灵 (0:07)§7 和 §c黑暗 (0:05)§r\n  获得 §c虚弱 (0:15)§7 和 §c挖掘疲劳 (0:30)§r`
- 改后：`在2个全体事件中选择1个全体事件并触发`
- 说明：同 1.1 第 15 条，超出任务清单字面范围但属同一缺陷。

> zh_cn 的 `tooltip.astral_dice.chip.bank_card_high` / `.bank_card_low` 与 forge 已经逐字相同（只有 en_us 有旧表述），故本语言无这两处改动。

### 1.3 ja_jp（13 处）

**1) `astral_dice.guide.entry.fanny_sign.1`**（依据 `forge-1.20.1/src/main/resources/assets/astral_dice/lang/ja_jp.json:124`）
- 改前：`アクティブスキル-トラブルメーカー：以下のランダムイベントを 1 つ即座に発動：再生能力 (0:30) を獲得；攻撃力上昇 (0:30) を獲得；攻撃-特大を 1 枚獲得；ランダムな効果カード（専用を除く）を 1 枚 + 3 スターコインを獲得；栄養補給 (2:00)（Farmer's Delight 導入時）と満腹度回復 (0:30) を獲得；移動速度上昇 (0:30) を獲得。`
- 改後：`アクティブスキル-イベント選択：現在のイベントプールから全体イベントをランダムに 2 つ抽選し、そのうち 1 つを選んで発動；選択して確定を押さない限り発動しない。`

**2) `astral_dice.guide.entry.fanny_sign.2`**（依据 ja_jp:125）
- 改前：`（続き）即時ダメージを受ける；毒 (0:15) を獲得；空腹 (0:30) と吐き気 (0:07) を獲得；衰弱 (0:07) と暗闇 (0:05) を獲得；弱体化 (0:15) と採掘速度低下 (0:30) を獲得。`
- 改後：`（続き）15 秒以内に選ばなかった場合、死亡した場合、またはログアウトした場合は 1 つ目が強制的に発動する；確定すると即座に画面が閉じてイベント処理に入る；スキル音はスキルキーを押した時点で再生される。`

**3) `astral_dice.guide.entry.airbag_chip.1`**（依据 ja_jp:237）
- 改前：`致命ダメージを受けた時、チャージを 6 スタック消費して今回のダメージを無効化する（クールダウン 1:00）。この保命効果は砥石、不死のトーテムとエンダーのダイスより優先される；無敵を無視する致命ダメージにも有効（例：コマンド /kill）。`
- 改後：`致命ダメージを受けた時、チャージを 6 スタック消費して今回のダメージを無効化する（クールダウン 1:00）。この保命効果は砥石、不死のトーテムとエンダーのダイスより優先される；無敵を無視する致命ダメージにも有効（例：コマンド /kill）；奈落への落下による致命ダメージは無効化できない。また、ダメージを受けた時はチャージ +1（クールダウン 15 秒）。`

**4) `tooltip.astral_dice.chip.airbag`**（依据 ja_jp:635）
- 改前：`致命ダメージを受けた時：チャージを §e6§7 スタック消費し、今回のダメージを無効化\n砥石、不死のトーテムとエンダーのダイスより優先\n無敵を無視する致命ダメージにも有効\nクールダウン §91:00§r`
- 改後：`致命ダメージを受けた時：チャージを §e6§7 スタック消費し、今回のダメージを無効化\n砥石、不死のトーテムとエンダーのダイスより優先\nクールダウン §91:00§7\nダメージを受けた時：チャージ §e+1§7（クールダウン §915 秒§7）`

**5) `astral_dice.guide.entry.member_recommendation_chip.1`**（依据 ja_jp:233）
- 改前：`ダイス神の加護発動のたびに、ランダムカードを 1 枚獲得。`
- 改後：`ダイス神の祝福の発動時、インベントリ内のカードが 5 枚未満ならランダムなカードを 1 枚獲得。装着中、自分のショップの全カード価格が 1 星貨安くなる（商人スタンドの値下げと重複可、最低 0）。`

**6) `tooltip.astral_dice.chip.member_recommendation`**（依据 ja_jp:612）
- 改前：`ダイス神の加護発動のたびに、ランダムカードを 1 枚獲得`
- 改後：`ダイス神の祝福が始まるたびにランダムなカードを 1 枚獲得\nインベントリ内のカードが §e5§7 枚未満の時のみ発動\n装着中、自分のショップの全カード価格が §e-1§7 星貨`

**7) `astral_dice.guide.entry.eagle_scope_chip.1`**（依据 ja_jp:92）
- 改前：`攻撃するとターゲットに 1 スタックのマーキングを付与；今回の戦闘でターゲットのマーキングスタック数 ×2 の攻撃力を上昇させる。`
- 改後：`攻撃するとターゲットに 1 スタックのマーキングを付与；マーキング状態のターゲットにそのスタック数 ×2 の追加攻撃ダメージを与える。`

**8) `tooltip.astral_dice.chip.eagle_scope`**（依据 ja_jp:595）
- 改前：`攻撃すると、ターゲットに §e1§7 スタックのマーキングを付与し、今回の戦闘でターゲットのマーキングスタック数の §e2§7 倍の攻撃力を上昇`
- 改後：`攻撃すると、ターゲットに §e1§7 スタックのマーキングを付与し、マーキング状態のターゲットにそのスタック数の §e2§7 倍の追加攻撃ダメージ`

**9) `astral_dice.guide.entry.bank_card_low_chip.1`**（依据 ja_jp:43）
- 改前：`装備中は基本星光を 4 スタック獲得。`
- 改後：`装備時に 4 スタックのスターライトを獲得；スターライト交換が成功するたびに、装備中のキャッシュカード（残高少 / 残高多 / 無限）とスターコインのハンマーに応じてスターライトを補充する —— このチップは 4 スタック換算、複数装備時は合算、上限 32。`

**10) `astral_dice.guide.entry.bank_card_high_chip.1`**（依据 ja_jp:42）
- 改前：`装備中は基本星光を 7 スタック獲得。`
- 改後：`装備時に 7 スタックのスターライトを獲得；スターライト交換が成功するたびに、装備中のキャッシュカード（残高少 / 残高多 / 無限）とスターコインのハンマーに応じてスターライトを補充する —— このチップは 7 スタック換算、複数装備時は合算、上限 32。`

**11) `astral_dice.guide.entry.bank_card_unlimited_chip.1`**（依据 ja_jp:44）
- 改前：`装備時に 3 スタックのスターライトを獲得；ダイス神の加護終了のたびに、自身と味方プレイヤー（チーム加入時は同チームメンバー、未加入時は全プレイヤー）が 3 スターコインを獲得する。`
- 改後：`装備時に 3 スタックのスターライトを獲得；スターライト交換が成功するたびに、装備中のキャッシュカード（残高少 / 残高多 / 無限）とスターコインのハンマーに応じてスターライトを補充する —— このチップは 3 スタック換算、複数装備時は合算、上限 32；ダイス神の加護終了のたびに、自身と味方プレイヤー（チーム加入時は同チームメンバー、未加入時は全プレイヤー）が 3 スターコインを獲得する。`

**12) `astral_dice.guide.entry.star_coin_hammer_chip.1`**（依据 ja_jp:218）
- 改前：`装備時に 5 スタックのスターライトを獲得；インベントリに 32 枚以上のスターコインを所持している場合、ダイス神の加護に入るたびに 18 スターコインを消費し、所持しているスターコイン総数の 30%% に応じて攻撃ダメージを上昇させる（上限 100；…）。`
- 改後：`装備時に 5 スタックのスターライトを獲得；スターライト交換が成功するたびに、装備中のキャッシュカード（残高少 / 残高多 / 無限）とスターコインのハンマーに応じてスターライトを補充する —— このチップは 5 スタック換算、複数装備時は合算、上限 32；インベントリに 32 枚以上のスターコインを所持している場合、ダイス神の加護に入るたびに 18 スターコインを消費し、所持しているスターコイン総数の 30%% に応じて攻撃ダメージを上昇させる（上限 100；スターコインの袋は 9 枚として計算；バラのスターコインが不足する場合はスターコインの袋を自動で 1 個開け、必要な枚数のみ差し引き残りはインベントリに残す。安全に開けられない場合は消費しない）。`

**13) `tooltip.astral_dice.sign.fanny_active`**（依据 ja_jp:682）
- 改前：`以下のランダムイベントを 1 つ即座に発動：\n  §9再生能力 (0:30)§r を獲得\n  §9攻撃力上昇 (0:30)§r を獲得\n  1 枚獲得：攻撃 - 特大\n  ランダムな効果カード（専用を除く）を 1 枚 + §e3§7 スターコインを獲得\n  §9滋養 (2:00)§7（Farmer's Delight 導入時）と §9飽和 (0:30)§r を獲得\n  §9移動速度上昇 (0:30)§r を獲得\n  §c即時ダメージ§r を獲得\n  §c毒 (0:15)§r を獲得\n  §c空腹 (0:30)§7 と §c吐き気 (0:07)§r を獲得\n  §c衰弱 (0:07)§7 と §c暗闇 (0:05)§r を獲得\n  §c弱体化 (0:15)§7 と §c採掘速度低下 (0:30)§r を獲得`
- 改後：`2つの全体イベントから1つを選んで発動する`
- 说明：同 1.1 第 15 条。

> ja_jp 的 `tooltip.astral_dice.chip.bank_card_high` / `.bank_card_low` 与 forge 已逐字相同，无改动。

---

## 二、行为依据（代码引用点，验证文案与实现一致）

| 筹码 | 实现证据（fabric-1.20.1 源码） |
|---|---|
| 安全气囊 `airbag_chip` | `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/chip/AirbagChipItem.java:37` `public static final int HIT_CHARGE_GAIN = 1;`；`:39` `public static final int HIT_CHARGE_COOLDOWN_TICKS = 20 * 15;`；`:129` `ModAttachments.setAirbagHitChargeCooldownEnd(player, now + HIT_CHARGE_COOLDOWN_TICKS);`；`:130` `ChargeManager.addStacks(player, HIT_CHARGE_GAIN);` |
| 会员推荐 `member_recommendation_chip` | `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/chip/MemberRecommendationChipItem.java:21` `private static final int HAND_CARD_LIMIT = 5;`；`:63` `if (countHandCards(player) >= HAND_CARD_LIMIT) return;` |
| 鹰眼镜 `eagle_scope_chip` | `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/chip/EagleScopeChipItem.java:17` `public static final int DAMAGE_PER_MARK = 2;`；`:8` 注释「…的**攻击伤害**(额外加伤通道,结算在 DiceCombatModifiers)」；`fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/combat/DiceCombatModifiers.java:454` `return ctx.eagleScopeMarkLevel * EagleScopeChipItem.DAMAGE_PER_MARK;` |
| 银行卡三档 + 星光锤 | 文案改为「装备时获得 N 层星光 + 每次兑换成功后按佩戴银行卡/星币锤补充、本筹码计 N 层、多枚叠加上限 32」，删除「装备直给底盘星光」的说法（与 forge 一致） |
| ATM 机 `atm_chip` | 核对结论：fabric 的 `astral_dice.guide.entry.atm_chip.1` 与 `tooltip.astral_dice.chip.atm` **已与 forge 逐字相同**，文案里已无「装备 +1 星光」「兑换 +40%%」，本次无需改动（见第五节）。 |
| 用不完 `bank_card_unlimited` | `tooltip.astral_dice.chip.bank_card_unlimited` 与 forge 已逐字相同；guide 条目按 forge 补齐闸门说明（见 1.x 第 11 条）。 |

---

## 三、删除的键（三语同步删，共 16 个/语言）

### 3.1 `msg.astral_dice.fanny_event` 及其 `.1`~`.11`（12 个）
依据：fabric java 全树 `grep -rln "fanny_event" --include=*.java` **0 命中**；同名键在 `forge-1.20.1` 与 `neoforge-1.21.1` 的 lang 里**根本不存在**（只有 `neoforge-26.1.2`（P1 线，不在本次范围）各 12 条）。

| 键名 | en_us 删除值 | zh_cn 删除值 | ja_jp 删除值 |
|---|---|---|---|
| `msg.astral_dice.fanny_event` | Random event triggered: %s | 随机事件触发：%s | ランダムイベント発生：%s |
| `.1` | Regeneration | 生命恢复 | 再生能力 |
| `.2` | Strength | 力量 | 攻撃力上昇 |
| `.3` | Attack XL | 攻击-特大 | 攻撃 - 特大 |
| `.4` | Random Effect Card + Star Coins | 随机效果牌 + 星币 | ランダム効果カード + スターコイン |
| `.5` | Nourishment + Saturation | 滋养 + 饱和 | 栄養補給 + 飽和 |
| `.6` | Speed | 迅捷 | 移動速度上昇 |
| `.7` | Instant Damage | 瞬间伤害 | 即時ダメージ |
| `.8` | Poison | 中毒 | 毒 |
| `.9` | Hunger + Nausea | 饥饿 + 反胃 | 空腹 + 吐き気 |
| `.10` | Wither + Darkness | 凋灵 + 黑暗 | 衰弱 + 暗闇 |
| `.11` | Weakness + Mining Fatigue | 虚弱 + 挖掘疲劳 | 弱体化 + 採掘速度低下 |

### 3.2 B33 遗留孤儿键（4 个）

| 键名 | en_us | zh_cn | ja_jp |
|---|---|---|---|
| `astral_dice.guide.entry.hanna_sign.3` | Recipe: amethyst dice + golden star plate ×2 (Bizarre tier). | 配方：紫晶骰子 + 黄金星盘 ×2（奇特档）。 | レシピ：アメジストのダイス + 黄金の星盤 ×2（ビザール段階）。 |
| `astral_dice.guide.entry.nardis_sign.3` | Recipe: golden dice tier (no star plate), the same tier as the Lulu Sign and the Padman Sign. | 配方：黄金骰子（无星盘）档，与史莱姆立牌、上班族立牌同档。 | レシピ：金のダイス（星盤なし）の段階で、ルルのスタンド・パッドマンのスタンドと同段階。 |
| `astral_dice.guide.entry.sherry_sign.3` | Recipe: amethyst dice + golden star plate (Bizarre tier). | 配方：紫晶骰子 + 黄金星盘（奇特档）。 | レシピ：アメジストのダイス + 黄金の星盤（ビザール段階）。 |
| `astral_dice.guide.entry.teru_sign.3` | Recipe: diamond dice + golden star plate ×2 (Legendary tier). | 配方：钻石骰子 + 黄金星盘 ×2（传奇档）。 | レシピ：ダイヤモンドのダイス + 黄金の星盤 ×2（レジェンダリー段階）。 |

判定依据：B33（手册结构对齐）已把四个立牌条目的第 3 页删掉 —— `fabric-1.20.1/src/main/resources/assets/astral_dice/patchouli_books/astral_guide/en_us/entries/signs/{hanna,nardis,sherry,teru}_sign.json` 现在各只有 3 个 pages（spotlight `.1` + text `.2` + crafting），**没有** `.3` 页；forge/neoforge-1.21.1 的 lang 早已不含这 4 个键；`tools/verify_fabric_assets.py` 的 `LANG_KEY_ALLOW`（第 80-86 行）注释也写明「teru_sign.3 已随之移除」。⇒ lang 侧属孤儿，删除。

---

## 四、保留的 fabric 独有键（8 个/语言，逐条判定）

删完后，fabric 三语各剩 8 个 forge 没有的键，全部**保留**：

| 键 | 判定 | 依据 |
|---|---|---|
| `accessories.slot.chip` / `.dice` / `.stand` | 保留（运行时 key） | `fabric-1.20.1/src/main/resources/data/astral_dice/accessories/slot/{chip,dice,stand}.json` 定义槽名；Accessories 的 `SlotType` 用片段 `"slot."` + 槽名 + `accessories.` 前缀取译文（jar：`C:/Users/xmace/.gradle/caches/modules-2/files-2.1/maven.modrinth/accessories/A7wwzJ0e/38e3d368d97914966bdda5c6719f77c7f1f9a58d/accessories-A7wwzJ0e.jar`，其 lang 里就是 `accessories.slot.belt` 这类同族键） |
| `accessories.slot_group.astral_dice` | 保留（运行时 key） | `fabric-1.20.1/src/main/resources/data/astral_dice/accessories/group/astral_dice.json`；`SlotGroup` 用片段 `"slot_group."`，同族键见上述 jar 的 `accessories.slot_group.misc/head/chest/...` |
| `trinkets.slot.astral_dice.chip` / `.dice` / `.stand` | 保留（运行时 key） | `fabric-1.20.1/src/main/resources/data/trinkets/slots/astral_dice/{chip,dice,stand,group}.json`；Trinkets 3.7.2 的 lang 走 `trinkets.slot.<group>.<slot>` 同族键（`C:/Users/xmace/.gradle/caches/modules-2/files-2.1/maven.modrinth/trinkets/3.7.2/d58cc8759587f03f320f188972ca66baa788d008/trinkets-3.7.2.jar`）。两个模组在 `fabric-1.20.1/build.gradle` 里都是**软前置**（recommends + modCompileOnly + 条件 modRuntimeOnly），玩家有可能装上，故键必须跟着走 |
| `msg.astral_dice.parunan_active_no_starlight` | 保留（待移植） | 本线 java **0 引用**，但这是功能缺口而非孤儿：主线该文案被 `neoforge-26.1.2/src/main/java/com/merlinkitsune/astral_dice/item/sign/ParunanSignItem.java:88` 引用（`sendSignActionBarColored(..., "msg.astral_dice.parunan_active_no_starlight")`）；fabric 的 ParunanSignItem 尚未移植该分支（`fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/BaseSignItem.java:455-456` 注释「Parunan 的覆写尚未移植」）。按「旧版本差异全部放弃、向主线看齐」的口径，键应留着等移植，删了反而会引入后续回归 |

---

## 五、逐条核对后「无需改动」的项（改动前已取证）

- `astral_dice.guide.entry.atm_chip.1` / `tooltip.astral_dice.chip.atm`：fabric 与 forge **逐字相同**，已是「装备期间，转账后使双方各获得 §e1§7 层星光与 §e2§7 星币」，无「+40%%」也无「装备 +1 星光」。⇒ 任务第三条里对 ATM 的「任何仍写着 +40% / 装备 +1 星光的文案都必须改」——本轮无命中，属**已在前批修好**。
- `astral_dice.guide.entry.fanny_sign.3`（被动「华点发现 / Spot the Highlight / 華点発見」）：与 forge 逐字相同，按要求**未动**。
- `tooltip.astral_dice.chip.bank_card_unlimited`：与 forge 逐字相同（「无队伍时自身 32 格内」的表述已在对齐线上），未动；guide 条目按 forge 补了兑换补充段（见 1.x 第 11 条）。
- 除上述 15 个键外，fabric 三语与 forge 共同键**再无值差异**（脚本实测 diff 列表为空）。

## 六、无法判定 / 未处理项

- 无「无法判定」项。所有要点都有 forge 行号或代码引用点支撑。
- 唯一需要后续跟进（不属本轮可改范围）：`msg.astral_dice.parunan_active_no_starlight` 在 fabric 线无代码引用，等 ParunanSignItem 覆写移植后自动生效（见第四节）。

---

## 七、验收原始输出

### 7.1 `python tools/check_lang_sync.py`

```
[OK]   neoforge-1.21.1: en_us=1070 键，3 份语言键集一致
[OK]   forge-1.20.1: en_us=1070 键，3 份语言键集一致
[OK]   neoforge-26.1.2: en_us=1094 键，3 份语言键集一致
[OK]   fabric-1.20.1: en_us=1078 键，3 份语言键集一致
EXIT=0
```

### 7.2 `python tools/verify_fabric_assets.py`

```
jar = fabric-1.20.1\build\libs\astral_dice-1.5.0-rc.7+fabric_1.20.1.jar

[1] 物品闭环: 注册 157 (方块物品 3) / 模型 158 / 贴图 155 (方块贴图另计)
[2] 标签闭环: 引用 10 / 自建 29 / 有提供者 10
[3] 音效闭环: 注册 23 / sounds.json 23 / ogg 23
[4] 粒子闭环: 注册 2 / particles/*.json 2
[5] 语言闭环: zh_cn 1078 / en_us 1078 / ja_jp 1078
[6] 手册闭环: 条目 184 / 引用配方 125
[7] 创意标签覆盖: 156/157
    已知与基线一致（不阻塞）: ['repulsion']
[8] 饰品槽位图标: 检查 7 条 icon

=== PASS: 8 项闭环全部通过 ===
EXIT=0
```

### 7.3 `python tools/audit_patchouli_keys.py`

```
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
EXIT=0
```

### 7.4 `gradlew.bat :fabric-1.20.1:remapJar --console=plain`

```
Reusing configuration cache.
> Task :fabric-1.20.1:processIncludeJars UP-TO-DATE
> Task :fabric-1.20.1:compileJava UP-TO-DATE
> Task :fabric-1.20.1:processResources
> Task :fabric-1.20.1:classes
> Task :fabric-1.20.1:jar
> Task :fabric-1.20.1:remapJar

BUILD SUCCESSFUL in 4s
5 actionable tasks: 3 executed, 2 up-to-date
Configuration cache entry reused.
```

### 7.5 自检脚本（临时脚本，跑完即删）

判据：三语键数相等 = 1078；16 个删除键在三语均不存在；与 forge 共同键值差异为空；fabric 独有键恰为第四节那 8 个；`bareLF==0`、无 BOM、文件以 `}\r\n` 结尾。

```
[en_us] keys=1078 crlf=1080 bareLF=0 BOM=False
[zh_cn] keys=1078 crlf=1080 bareLF=0 BOM=False
[ja_jp] keys=1078 crlf=1080 bareLF=0 BOM=False

key counts equal: True = 1078
expected key count 1078: True

=== SELF-CHECK PASS ===
EXIT=0
```

（说明：`crlf=1080` 与 `keys=1078` 的关系是 —— 1082 个物理行 = 1078 个键行 + 首行 `{` + 末行 `}` + 1 个空尾部，共 1080 个 CRLF；`bareLF==0` 表示没有裸 LF。）

---

## 八、边界声明

- 只改了上述三个 lang 文件，并新建了本报告。
- **未**改 java 源码、其它资源、手册 json、其它三条线、构建脚本；**未**做任何 git 写操作；**未**启动 Minecraft。
- 工作树里原本就存在的其它改动（AGENTS.md、docs/**、`fabric-1.20.1/build.gradle`、`fabric-1.20.1/src/generated/**` 等）与本批无关，未触碰。
