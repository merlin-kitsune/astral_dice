# B30 · fabric-1.20.1 文案数值同步报告（对齐 neoforge-1.21.1 / forge-1.20.1 主线口径）

- 批次：B30-LANG-NUMBER-SYNC
- 工作树：`F:/MCProject/astral_dice_multiloader`（分支 `multi-main`，Windows 原生）
- 执行日期：2026-11-01
- 权威裁决：以最新主线为准（neoforge-1.21.1 主基准 / forge-1.20.1 次基准）；fabric 侧与主线语义冲突的旧行为一律改向主线，不再保留"平台差异"。
- 结论：**11 个键 × 3 语 = 33 处文案数值已与 forge-1.20.1 / neoforge-1.21.1 逐字一致；帕秋莉手册无需改动（无内联文本，全部走 lang 键）**；三道守门全绿。

---

## 1. 代码事实核对（只读，代码未改动）

| # | 文件:行 | 当前值 | 对文案的推论 |
|---|---|---|---|
| 1 | `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/chip/WarpEngineChipItem.java:39` | `PORTAL_WAYSTONE_COOLDOWN_TICKS = 20 * 60 * 3` | 上一批次由 5:00 改 3:00，文案须由 `5:00` 改 `3:00` |
| 2 | `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/chip/BookmarkChipItem.java:18` | `DAMAGE_BONUS = 2` | 文案 `+1` → `+2` |
| 3 | `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/NancyLuSignItem.java:58`（增益）与 `:54`（半径） | `ACTIVE_ATTACK_BONUS = 6`（固定值，不再按卡牌费用 ×2、不再消耗战斗牌、无保底 +2）；`PASSIVE_RANGE = 3.0` | 主动说明整段重写为"破隐固定 +6"；被动半径 6 → 3 |
| 4 | `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/trade/EmeraldDiceTrade.java:68` | `new ItemStack(ModItems.STAR_COIN.get(), Math.max(1, stack.getCount()))` | 折扣公式已改为等值 1:1，"20%% 折扣"表述**不再成立**；采用 forge 同键的 `1:1` 原文 |

说明：第 4 项是本批唯一"文案数值是否仍成立"的判定点。按 forge 同键原文比对，forge/neo 均为 `1:1` 表述，且与 fabric 代码现状（`Math.max(1, count)` = 原价等值）一致，故 fabric 的 `20%% 折扣` 属旧行为残留，改向主线。

---

## 2. 变更文件清单

| 文件 | 改动行 | 字节（改动后实读 / Δ=11 处行长度差之和） | 行尾 / BOM |
|---|---|---|---|
| `fabric-1.20.1/src/main/resources/assets/astral_dice/lang/zh_cn.json` | 11 行 | 146,347（Δ −195，即改动前 146,542 的推算值）| CRLF 1096 / bareLF 0 / 无 BOM |
| `fabric-1.20.1/src/main/resources/assets/astral_dice/lang/en_us.json` | 11 行 | 151,311（Δ −153，即改动前 151,464 的推算值）| CRLF 1096 / bareLF 0 / 无 BOM |
| `fabric-1.20.1/src/main/resources/assets/astral_dice/lang/ja_jp.json` | 11 行 | 186,798（Δ −272，即改动前 187,070 的推算值）| CRLF 1096 / bareLF 0 / 无 BOM |

改动方式：以 `utf8` 读入 → `split("\r\n")` → 仅在 11 个下标上把该行内容整体替换为 forge 同键行内容（forge 侧为纯 LF，只取行内容、丢弃其行尾）→ `join("\r\n")` 写回。
**未使用 `json.dump`**，键序、缩进、行序、转义、行尾全部保持；键数 1094 不变（仅改值）。

---

## 3. 逐键结论摘要（11 键）

| # | 键 | 三语一句话结论（旧 → 新） |
|---|---|---|
| 1 | `astral_dice.guide.entry.emerald_dice.1` | 绿宝石骰子交易说明：`20%% 折扣`/`20%% discount`/`20%% の割引` → 一律 `1:1` 等值代付（折扣文案作废，因折扣公式已改 `Math.max(1, count)`） |
| 2 | `astral_dice.guide.entry.nancy_lu_sign.1` | 主动技能说明：`消耗随机战斗牌、按费用×2（最低+2）、有保底` → `破隐时固定 +6，按下主动不给、自然到期不给` |
| 3 | `astral_dice.guide.entry.nancy_lu_sign.2` | 被动判定半径：`6 格 / 6 blocks / 6 ブロック` → `3` |
| 4 | `astral_dice.guide.entry.bookmark_chip.1` | 书签芯片伤害加成：`+1` → `+2` |
| 5 | `astral_dice.guide.entry.warp_engine_chip.1` | 传送门/Waystone 共享冷却：`5:00` → `3:00` |
| 6 | `tooltip.astral_dice.chip.bookmark` | `§e+1§r` → `§e+2§r` |
| 7 | `tooltip.astral_dice.chip.warp_engine` | `§95:00§7` → `§93:00§7` |
| 8 | `tooltip.astral_dice.emerald_dice_trade` | `§e20%%§2 折扣` → `（§e1:1§2）` 等值代付表述 |
| 9 | `tooltip.astral_dice.sign.nancy_lu_active` | `消耗战斗牌 ×2（最低 §e+2§7）` → `破隐固定 §e+6§7，持续 §92:00§7` |
| 10 | `tooltip.astral_dice.sign.nancy_lu_passive` | `§e6§7 格` → `§e3§7` |
| 11 | `msg.astral_dice.nancy_lu_active` | `攻击任意一个目标…移除完全隐身` → `攻击敌对目标破隐时获得固定攻击力加成`（`%d` 占位符不变） |

---

## 4. 逐键前后对照（33 行，含三语）

| # | 键 | 语言 | 行号 | 改前 | 改后 |
|---|---|---|---|---|---|
| 1 | `astral_dice.guide.entry.emerald_dice.1` | zh_cn | 66 |   "astral_dice.guide.entry.emerald_dice.1": "绿宝石骰子。与钻石骰子同阶，可由钻石骰子升级；佩戴后与村民交易时用星币代替绿宝石，并享受 20%% 折扣。", |   "astral_dice.guide.entry.emerald_dice.1": "绿宝石骰子。与钻石骰子同阶，可由钻石骰子升级；佩戴后与村民交易时按 1:1 用星币代替绿宝石支付。", |
| 2 | `astral_dice.guide.entry.emerald_dice.1` | en_us | 66 |   "astral_dice.guide.entry.emerald_dice.1": "Emerald die. Same tier as the diamond die and upgradeable from it; while equipped, villager trades use Star Coins instead of Emeralds with a 20%% discount.", |   "astral_dice.guide.entry.emerald_dice.1": "Emerald die. Same tier as the diamond die and upgradeable from it; while equipped, villager trades use Star Coins instead of Emeralds at a 1:1 rate.", |
| 3 | `astral_dice.guide.entry.emerald_dice.1` | ja_jp | 66 |   "astral_dice.guide.entry.emerald_dice.1": "エメラルドのダイス。ダイヤモンドのダイスと同品位で、ダイヤモンドのダイスからアップグレードできる；装着すると村人との取引でエメラルドの代わりにスターコインを使用し、20%% の割引を受ける。", |   "astral_dice.guide.entry.emerald_dice.1": "エメラルドのダイス。ダイヤモンドのダイスと同品位で、ダイヤモンドのダイスからアップグレードできる；装着すると村人との取引でエメラルドの代わりにスターコインを 1:1 で使用できる。", |
| 4 | `astral_dice.guide.entry.nancy_lu_sign.1` | zh_cn | 131 |   "astral_dice.guide.entry.nancy_lu_sign.1": "主动技能-远程侵入：立即进入完全隐身（最多 30 秒）。对敌对目标或玩家发动攻击时立即解除隐身，并消耗主物品栏中随机一张战斗牌，按该牌费用 ×2 增加攻击力（最低 +2），持续 2:00（主物品栏无战斗牌可消耗时同样获得保底 +2）。", |   "astral_dice.guide.entry.nancy_lu_sign.1": "主动技能-远程侵入：立即进入完全隐身（最多 30 秒）。隐身期间攻击敌对目标并破除隐身时，立即获得固定 +6 攻击力，持续 2:00（按下主动本身不给加成，隐身自然到期而未攻击同样不给）。", |
| 5 | `astral_dice.guide.entry.nancy_lu_sign.1` | en_us | 131 |   "astral_dice.guide.entry.nancy_lu_sign.1": "Active Skill - Remote Intrusion: enter full invisibility instantly (up to 30 seconds). Attacking a hostile mob or player immediately removes invisibility, consumes one random battle card from your main inventory, and grants attack equal to that card's cost ×2 (minimum +2) for 2:00 (a guaranteed +2 still applies when no battle card is available in your main inventory).", |   "astral_dice.guide.entry.nancy_lu_sign.1": "Active Skill - Remote Intrusion: enter full invisibility instantly (up to 30 seconds). While invisible, attacking a hostile target and breaking invisibility immediately grants a fixed +6 attack for 2:00 (pressing the active skill itself grants no bonus, and natural expiry without an attack grants none either).", |
| 6 | `astral_dice.guide.entry.nancy_lu_sign.1` | ja_jp | 131 |   "astral_dice.guide.entry.nancy_lu_sign.1": "アクティブスキル-遠隔侵入：即座に完全ステルスに入る（最大 30 秒）。敵対ターゲットまたはプレイヤーを攻撃すると即座にステルスが解除され、メインインベントリの戦闘カードをランダムに 1 枚消費し、そのカードのコスト ×2 分の攻撃力を加算（最低 +2）、持続 2:00（メインインベントリに消費できる戦闘カードがない場合も最低 +2 を獲得）。", |   "astral_dice.guide.entry.nancy_lu_sign.1": "アクティブスキル-遠隔侵入：即座に完全ステルスに入る（最大 30 秒）。ステルス中に敵対ターゲットを攻撃してステルスが解除されると、即座に固定 +6 の攻撃力を獲得、持続 2:00（アクティブ発動自体では攻撃力ボーナスを得ず、攻撃せず自然解除された場合も得られない）。", |
| 7 | `astral_dice.guide.entry.nancy_lu_sign.2` | zh_cn | 132 |   "astral_dice.guide.entry.nancy_lu_sign.2": "被动技能-网络防火墙：免疫末影珍珠传送伤害。骰神赐福结束后，若周围 6 格内没有敌对生物，则攻击力 +3 并获得一张随机战斗牌；否则防御力 +3。", |   "astral_dice.guide.entry.nancy_lu_sign.2": "被动技能-网络防火墙：免疫末影珍珠传送伤害。骰神赐福结束后，若周围 3 格内没有敌对生物，则攻击力 +3 并获得一张随机战斗牌；否则防御力 +3。", |
| 8 | `astral_dice.guide.entry.nancy_lu_sign.2` | en_us | 132 |   "astral_dice.guide.entry.nancy_lu_sign.2": "Passive Skill - Network Firewall: immune to Ender Pearl teleport damage. After a Dice Blessing ends, if there are no hostiles within 6 blocks, gain +3 attack and a random battle card; otherwise gain +3 defense.", |   "astral_dice.guide.entry.nancy_lu_sign.2": "Passive Skill - Network Firewall: immune to Ender Pearl teleport damage. After a Dice Blessing ends, if there are no hostiles within 3 blocks, gain +3 attack and a random battle card; otherwise gain +3 defense.", |
| 9 | `astral_dice.guide.entry.nancy_lu_sign.2` | ja_jp | 132 |   "astral_dice.guide.entry.nancy_lu_sign.2": "パッシブスキル-ネットワークファイアウォール：エンダーパール転送ダメージを無効化。ダイス神の加護終了後、周囲 6 ブロック以内に敵対モブがいない場合は攻撃力 +3 とランダムな戦闘カードを 1 枚獲得；それ以外は防御力 +3。", |   "astral_dice.guide.entry.nancy_lu_sign.2": "パッシブスキル-ネットワークファイアウォール：エンダーパール転送ダメージを無効化。ダイス神の加護終了後、周囲 3 ブロック以内に敵対モブがいない場合は攻撃力 +3 とランダムな戦闘カードを 1 枚獲得；それ以外は防御力 +3。", |
| 10 | `astral_dice.guide.entry.bookmark_chip.1` | zh_cn | 193 |   "astral_dice.guide.entry.bookmark_chip.1": "使伤害类效果牌伤害加成 +1。", |   "astral_dice.guide.entry.bookmark_chip.1": "使伤害类效果牌伤害加成 +2。", |
| 11 | `astral_dice.guide.entry.bookmark_chip.1` | en_us | 193 |   "astral_dice.guide.entry.bookmark_chip.1": "Damage effect cards gain +1 damage bonus.", |   "astral_dice.guide.entry.bookmark_chip.1": "Damage effect cards gain +2 damage bonus.", |
| 12 | `astral_dice.guide.entry.bookmark_chip.1` | ja_jp | 193 |   "astral_dice.guide.entry.bookmark_chip.1": "ダメージ系効果カードのダメージボーナス +1。", |   "astral_dice.guide.entry.bookmark_chip.1": "ダメージ系効果カードのダメージボーナス +2。", |
| 13 | `astral_dice.guide.entry.warp_engine_chip.1` | zh_cn | 208 |   "astral_dice.guide.entry.warp_engine_chip.1": "触发瞬移时，充能 +2 并获得迅捷 (0:10)。计入的传送：末影珍珠、末影骰子不死图腾后的安全瞬移、维度传送门、Waystone（需安装 Waystones）。传送门与 Waystone 触发共享 5:00 冷却；末影珍珠与末影骰子瞬移无冷却。", |   "astral_dice.guide.entry.warp_engine_chip.1": "触发瞬移时，充能 +2 并获得迅捷 (0:10)。计入的传送：末影珍珠、末影骰子不死图腾后的安全瞬移、维度传送门、Waystone（需安装 Waystones）。传送门与 Waystone 触发共享 3:00 冷却；末影珍珠与末影骰子瞬移无冷却。", |
| 14 | `astral_dice.guide.entry.warp_engine_chip.1` | en_us | 208 |   "astral_dice.guide.entry.warp_engine_chip.1": "On teleport, charge +2 and gain Speed (0:10). Counted warps: ender pearl, the Ender Die's safe warp after the death totem, dimension portals, and Waystones (requires Waystones). Portals and Waystones share a 5:00 cooldown; ender pearl and Ender Die warps have no cooldown.", |   "astral_dice.guide.entry.warp_engine_chip.1": "On teleport, charge +2 and gain Speed (0:10). Counted warps: ender pearl, the Ender Die's safe warp after the death totem, dimension portals, and Waystones (requires Waystones). Portals and Waystones share a 3:00 cooldown; ender pearl and Ender Die warps have no cooldown.", |
| 15 | `astral_dice.guide.entry.warp_engine_chip.1` | ja_jp | 208 |   "astral_dice.guide.entry.warp_engine_chip.1": "テレポート発動時、チャージ +2 と移動速度上昇 (0:10) を獲得。対象となるテレポート：エンダーパール、エンダーのダイスの不死のトーテム後の安全テレポート、ディメンション間ポータル、Waystone（Waystones が必要）。ポータルと Waystone は 5:00 のクールダウンを共有；エンダーパールとエンダーのダイスのテレポートはクールダウンなし。", |   "astral_dice.guide.entry.warp_engine_chip.1": "テレポート発動時、チャージ +2 と移動速度上昇 (0:10) を獲得。対象となるテレポート：エンダーパール、エンダーのダイスの不死のトーテム後の安全テレポート、ディメンション間ポータル、Waystone（Waystones が必要）。ポータルと Waystone は 3:00 のクールダウンを共有；エンダーパールとエンダーのダイスのテレポートはクールダウンなし。", |
| 16 | `tooltip.astral_dice.chip.bookmark` | zh_cn | 535 |   "tooltip.astral_dice.chip.bookmark": "使伤害类效果牌伤害加成 §e+1§r", |   "tooltip.astral_dice.chip.bookmark": "使伤害类效果牌伤害加成 §e+2§r", |
| 17 | `tooltip.astral_dice.chip.bookmark` | en_us | 535 |   "tooltip.astral_dice.chip.bookmark": "Damage effect card damage bonus §e+1§r", |   "tooltip.astral_dice.chip.bookmark": "Damage effect card damage bonus §e+2§r", |
| 18 | `tooltip.astral_dice.chip.bookmark` | ja_jp | 535 |   "tooltip.astral_dice.chip.bookmark": "ダメージ系効果カードのダメージボーナス §e+1§r", |   "tooltip.astral_dice.chip.bookmark": "ダメージ系効果カードのダメージボーナス §e+2§r", |
| 19 | `tooltip.astral_dice.chip.warp_engine` | zh_cn | 574 |   "tooltip.astral_dice.chip.warp_engine": "触发瞬移时：充能 §e+2§7，并获得 §9迅捷 (0:10)§r\n维度传送门与 Waystone 共享 §95:00§7 冷却；末影珍珠与末影骰子瞬移无冷却", |   "tooltip.astral_dice.chip.warp_engine": "触发瞬移时：充能 §e+2§7，并获得 §9迅捷 (0:10)§r\n维度传送门与 Waystone 共享 §93:00§7 冷却；末影珍珠与末影骰子瞬移无冷却", |
| 20 | `tooltip.astral_dice.chip.warp_engine` | en_us | 574 |   "tooltip.astral_dice.chip.warp_engine": "On teleport: charge §e+2§7 and gain §9Speed (0:10)§r\nDimension portals and Waystones share a §95:00§7 cooldown; ender pearl and Ender Die warps have no cooldown", |   "tooltip.astral_dice.chip.warp_engine": "On teleport: charge §e+2§7 and gain §9Speed (0:10)§r\nDimension portals and Waystones share a §93:00§7 cooldown; ender pearl and Ender Die warps have no cooldown", |
| 21 | `tooltip.astral_dice.chip.warp_engine` | ja_jp | 574 |   "tooltip.astral_dice.chip.warp_engine": "テレポート発動時：チャージ §e+2§7、§9移動速度上昇 (0:10)§r を獲得\nディメンション間ポータルと Waystone は §95:00§7 のクールダウンを共有；エンダーパールとエンダーのダイスのテレポートはクールダウンなし", |   "tooltip.astral_dice.chip.warp_engine": "テレポート発動時：チャージ §e+2§7、§9移動速度上昇 (0:10)§r を獲得\nディメンション間ポータルと Waystone は §93:00§7 のクールダウンを共有；エンダーパールとエンダーのダイスのテレポートはクールダウンなし", |
| 22 | `tooltip.astral_dice.emerald_dice_trade` | zh_cn | 601 |   "tooltip.astral_dice.emerald_dice_trade": "与村民交易时用星币代替绿宝石，并享受 §e20%%§2 折扣", |   "tooltip.astral_dice.emerald_dice_trade": "与村民交易时用星币代替绿宝石支付（§e1:1§2）", |
| 23 | `tooltip.astral_dice.emerald_dice_trade` | en_us | 601 |   "tooltip.astral_dice.emerald_dice_trade": "Uses Star Coins instead of Emeralds in villager trades, with a §e20%%§2 discount", |   "tooltip.astral_dice.emerald_dice_trade": "Uses Star Coins instead of Emeralds in villager trades (§e1:1§2)", |
| 24 | `tooltip.astral_dice.emerald_dice_trade` | ja_jp | 601 |   "tooltip.astral_dice.emerald_dice_trade": "村人との取引でエメラルドの代わりにスターコインを使用し、§e20%%§2 の割引を受ける", |   "tooltip.astral_dice.emerald_dice_trade": "村人との取引でエメラルドの代わりにスターコインで支払う（§e1:1§2）", |
| 25 | `tooltip.astral_dice.sign.nancy_lu_active` | zh_cn | 657 |   "tooltip.astral_dice.sign.nancy_lu_active": "使用后立即进入完全隐身状态（最多持续 §930 秒§7）。若对敌对目标或玩家发动攻击，则立即解除隐身状态，同时消耗主物品栏中随机一张战斗牌，并增加该牌费用点数 §e×2§7（最低 §e+2§7）的攻击力，持续 §92:00§7（若主物品栏无战斗牌可消耗，同样保底增加 §e+2§7 攻击力）", |   "tooltip.astral_dice.sign.nancy_lu_active": "使用后立即进入完全隐身状态（最多持续 §930 秒§7）。隐身期间攻击敌对目标并破除隐身时，立即获得固定 §e+6§7 攻击力，持续 §92:00§7（按下主动本身不给加成，隐身自然到期而未攻击同样不给）", |
| 26 | `tooltip.astral_dice.sign.nancy_lu_active` | en_us | 657 |   "tooltip.astral_dice.sign.nancy_lu_active": "Instantly enter full invisibility (up to §930 seconds§7). Attacking a hostile mob or player immediately removes invisibility, consumes one random battle card from your main inventory, and grants attack equal to §e×2§7 that card's cost (minimum §e+2§7) for §92:00§7 (a guaranteed §e+2§7 attack still applies when no battle card is available in your main inventory)", |   "tooltip.astral_dice.sign.nancy_lu_active": "Instantly enter full invisibility (up to §930 seconds§7). While invisible, attacking a hostile target and breaking invisibility immediately grants a fixed §e+6§7 attack for §92:00§7 (pressing the active skill itself grants no bonus, and natural expiry without an attack grants none either)", |
| 27 | `tooltip.astral_dice.sign.nancy_lu_active` | ja_jp | 657 |   "tooltip.astral_dice.sign.nancy_lu_active": "使用後、即座に完全ステルス状態に入る（最大 §930 秒§7）。敵対ターゲットまたはプレイヤーを攻撃すると即座にステルスが解除され、同時にメインインベントリの戦闘カードをランダムに 1 枚消費し、そのカードのコスト点数 §e×2§7（最低 §e+2§7）の攻撃力を加算、持続 §92:00§7（メインインベントリに消費できる戦闘カードがない場合も、最低 §e+2§7 の攻撃力を加算）", |   "tooltip.astral_dice.sign.nancy_lu_active": "使用後、即座に完全ステルス状態に入る（最大 §930 秒§7）。ステルス中に敵対ターゲットを攻撃してステルスが解除されると、即座に固定 §e+6§7 の攻撃力を獲得、持続 §92:00§7（アクティブ発動自体では攻撃力ボーナスを得ず、攻撃せず自然解除された場合も得られない）", |
| 28 | `tooltip.astral_dice.sign.nancy_lu_passive` | zh_cn | 659 |   "tooltip.astral_dice.sign.nancy_lu_passive": "免疫末影珍珠传送时的伤害。骰神赐福效果结束后，若周围 §e6§7 格范围内没有敌对生物，则攻击力 §e+3§7 并获得一张随机战斗牌，否则防御力 §e+3§r", |   "tooltip.astral_dice.sign.nancy_lu_passive": "免疫末影珍珠传送时的伤害。骰神赐福效果结束后，若周围 §e3§7 格范围内没有敌对生物，则攻击力 §e+3§7 并获得一张随机战斗牌，否则防御力 §e+3§r", |
| 29 | `tooltip.astral_dice.sign.nancy_lu_passive` | en_us | 659 |   "tooltip.astral_dice.sign.nancy_lu_passive": "Immune to Ender Pearl teleport damage. After Dice Blessing ends, if there are no hostiles within §e6§7 blocks, gain §e+3§7 attack and a random battle card; otherwise gain §e+3§7 defense", |   "tooltip.astral_dice.sign.nancy_lu_passive": "Immune to Ender Pearl teleport damage. After Dice Blessing ends, if there are no hostiles within §e3§7 blocks, gain §e+3§7 attack and a random battle card; otherwise gain §e+3§7 defense", |
| 30 | `tooltip.astral_dice.sign.nancy_lu_passive` | ja_jp | 659 |   "tooltip.astral_dice.sign.nancy_lu_passive": "エンダーパール転送時のダメージを無効化。ダイス神の加護終了後、周囲 §e6§7 ブロック以内に敵対モブがいない場合は攻撃力 §e+3§7 とランダムな戦闘カード 1 枚を獲得、それ以外は防御力 §e+3§r", |   "tooltip.astral_dice.sign.nancy_lu_passive": "エンダーパール転送時のダメージを無効化。ダイス神の加護終了後、周囲 §e3§7 ブロック以内に敵対モブがいない場合は攻撃力 §e+3§7 とランダムな戦闘カード 1 枚を獲得、それ以外は防御力 §e+3§r", |
| 31 | `msg.astral_dice.nancy_lu_active` | zh_cn | 680 |   "msg.astral_dice.nancy_lu_active": "骇客立牌：已触发完全隐身 (%d 秒)，攻击任意一个目标获得攻击力加成并移除完全隐身", |   "msg.astral_dice.nancy_lu_active": "骇客立牌：已触发完全隐身 (%d 秒)，攻击敌对目标破隐时获得固定攻击力加成", |
| 32 | `msg.astral_dice.nancy_lu_active` | en_us | 680 |   "msg.astral_dice.nancy_lu_active": "Hacker sign: full invisibility triggered (%d s) — attack any target to gain an attack bonus and end the invisibility", |   "msg.astral_dice.nancy_lu_active": "Hacker sign: full invisibility triggered (%d s) — attack a hostile target to break invisibility and gain a fixed attack bonus", |
| 33 | `msg.astral_dice.nancy_lu_active` | ja_jp | 680 |   "msg.astral_dice.nancy_lu_active": "ナンシー・ローのスタンド：完全ステルスを発動 (%d 秒)。任意のターゲットを攻撃すると攻撃力ボーナスを獲得し、完全ステルスは解除されます", |   "msg.astral_dice.nancy_lu_active": "ナンシー・ローのスタンド：完全ステルスを発動 (%d 秒)。敵対ターゲットを攻撃してステルスが解除されると固定攻撃力ボーナスを獲得します", |

---

## 5. 已核对但无需改动的键

| 键 | 结论 |
|---|---|
| `effect.astral_dice.nancy_lu_hack.description` | 三语 fabric 与 forge **逐字相同**（本批代码只改主动/被动数值，未改效果描述语义），不改 |
| `astral_dice.guide.entry.card_trader.1` | 已为 `1:1` 代付表述，与 forge 一致 |
| `astral_dice.guide.entry.chip_trader.1` | 同上 |
| 其余 21 个同域键（`emerald_dice.*` / `bookmark*chip*` / `warp_engine*` / `nancy_lu*` 的 name、page title 等） | 三语 fabric 与 forge 逐字一致，无需改动 |

同域范围扫描口径：对键名与键值同时匹配 `emerald_dice|绿宝石骰子|エメラルドのダイス|bookmark|书签|ブックマーク|warp_engine|跃迁引擎|ワープエンジン|nancy_lu|骇客|南希|ナンシー|网络防火墙|远程侵入|遠隔侵入` 的 21 个键做三语逐值比对，差异集恰为上表 11 键；改完后 36/36（12 键 × 3 语，含 effect 描述）与 forge 逐字相同。

---

## 6. 帕秋莉手册（patchouli）核查结论：**无改动**

- 结构：`assets/astral_dice/patchouli_books/astral_guide/` 下 fabric 与 forge 各 184 个文件，文件集合完全一致；`en_us/` 为正文树（179 文件），`zh_cn/` 仅 5 个覆盖页（`entries/signs/` 下 alana / deepseek / dorothy / jill / megas），三条线这 5 页内容一致。另有 `data/astral_dice/patchouli_books/astral_guide/book.json` 1 个。
- **内联文本为 0**：对 fabric / forge / neoforge-1.21.1 三棵手册树逐文件解析 JSON，所有 `"text"` 字段值均为翻译键（`astral_dice.*` / `patchouli.*` / `minecraft.*` 前缀），非键文本 0 处 ⇒ 手册页不含任何内联数值，数值文案全部落在 lang 文件，**故本批手册无需任何数值改动**。
- 4 个目标页现状（三线同路径）：
  - `.../patchouli_books/astral_guide/en_us/entries/dice/emerald_dice.json` — fabric 与 forge 逐字节相同
  - `.../patchouli_books/astral_guide/en_us/entries/signs/nancy_lu_sign.json` — fabric 与 forge 逐字节相同
  - `.../patchouli_books/astral_guide/en_us/entries/chips_charge/warp_engine_chip.json` — 与 forge 仅差第 2 页类型（fabric `patchouli:crafting` vs 主线 `patchouli:text` + `astral_dice.guide.chip_source.note`），无数值
  - `.../patchouli_books/astral_guide/en_us/entries/chips_other/bookmark_chip.json` — 同上，无数值
- 结构性差异（**不在本批"数值同步"范围，未改动，仅记录**）：neoforge-1.21.1 与 forge-1.20.1 手册树逐字节相同（0/184 差异）；fabric 与主线差 92/184 文件，其中 61 个是"fabric 第 2 页为 `patchouli:crafting` 配方页、主线为 `chip_source.note` 文本页"，另 31 个为分类页/卡片页/立牌页等其它差异。该差异属手册版式与内容结构对齐议题，需另立批次处理，避免与数值同步混改。

---

## 7. 守门命令与原始输出末段

### 7.1 `python tools/check_lang_sync.py`（exit 0）

```
[OK]   neoforge-1.21.1: en_us=1070 键，3 份语言键集一致
[OK]   forge-1.20.1: en_us=1070 键，3 份语言键集一致
[OK]   neoforge-26.1.2: en_us=1094 键，3 份语言键集一致
[OK]   fabric-1.20.1: en_us=1094 键，3 份语言键集一致
```

四线键集：neoforge-1.21.1 1070、forge-1.20.1 1070、neoforge-26.1.2 1094、fabric-1.20.1 1094（本批 fabric 键数保持 1094/1094/1094，未增删键）。

### 7.2 `python tools/audit_patchouli_keys.py`（exit 0；该脚本内部已遍历四线，无需 fabric 参数）

```
   手册引用 467 处，全部命中三语 lang ✓
=== fabric-1.20.1 ===
   手册引用 473 处，全部命中三语 lang ✓
--------------------------------------------------------------------
合计：引用 2000 处，悬空 0 处
✅ 无悬空引用。
```

### 7.3 `python tools/verify_fabric_assets.py`（exit 0）

```
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
```

（`[7] 创意标签覆盖 156/157` 的 `repulsion` 缺项为基线已知、脚本声明不阻塞项。）

### 7.4 行尾 / BOM 自检（python，逐字节）

```
zh_cn  bytes=146347 CRLF=1096 LF=1096 bareLF=0 BOM=False keys=1094 endswith_nl=True
en_us  bytes=151311 CRLF=1096 LF=1096 bareLF=0 BOM=False keys=1094 endswith_nl=True
ja_jp  bytes=186798 CRLF=1096 LF=1096 bareLF=0 BOM=False keys=1094 endswith_nl=True
```

---

## 8. 观察到但不在本批范围的差异（未改动，仅报告）

对 fabric 与 forge 共有的 1070 个键做全量逐值比对，仍有 93–108 个键取值不同（zh_cn 101 / en_us 108 / ja_jp 93），其中"数值型 token 集"不同者 36–52 个，集中在**本批四类改动之外**的其它功能：`atm_chip`、`bank_card_*`、`star_coin_hammer_chip`、`member_recommendation_chip`、`airbag_chip`、`electric_glove_chip`、`satellite`、`railgun`、`parunan_sign`、`fanny_sign`、`bonnie_sign`、`lulu_sign`、`mimi_sign`、`misaki_sign`、`battle_dice.3`、`diamond_dice.1`、`golden_dice.1`、`netherite_dice.1`、`playstyles.2`、`megas_sign.3`、`card.monster_laser`、`card.orbital_strike` 等。

其中若干处并非"数值过期"而是**键语义已经分叉**，需按功能单独裁决，本批不处理：

- `astral_dice.guide.entry.battle_dice.3`：fabric 写"卡牌栏格数由星级决定（0★=4…3★=12）"，forge 写"费用上限 0★=3…3★=6、格数由品质决定"——属两套骰子成长模型，非纯数值同步。
- `astral_dice.guide.entry.diamond_dice.1` / `golden_dice.1`：同上（格数模型不同）。
- `tooltip.astral_dice.sign.parunan_passive`：fabric 是派鲁南被动效果文本，forge 同键下是商店经济文本——键映射已错位，属功能对齐议题。
- `astral_dice.guide.entry.member_recommendation_chip.1`：fabric 仅"每次赐福获得随机卡牌"，forge 含"卡牌数 < 5 条件 + 商店价格 -1"。

处理建议：按"每功能一批"的方式与主线逐项裁决，避免一次性大批量替换引入未经代码核对的文案。

---

## 9. 无法判定 / 风险项

1. **手册结构差异的归属**（92/184 文件）：无法从数值同步判据推出应采用哪种版式；本批按"只同值不同构"处理，未改结构。若后续要求 fabric 手册与主线逐字节一致，需单独批次并同步处理 61 处 `crafting` ↔ `chip_source.note` 页替换（该键在三语 lang 中均已存在，替换后 `audit_patchouli_keys` 仍可过）。
2. **`%%` 字面百分号**：本批 33 处新文本均不含字面百分号（旧文本的 `20%%` 随折扣文案一并移除），因此无需转义调整；`msg.astral_dice.nancy_lu_active` 的 `%d` 占位符数量与位置未变（1 个 `%d`），格式化调用不受影响。全文件仍存在 219 处单 `%`，均为 `%s`/`%d`/`%1$s` 类格式符（非字面百分号），与 forge 同键一致。
3. **未实机验证**：按约束不启动游戏，文案正确性以"与 forge 同键逐字一致 + 代码常量核对"为判据，未做游戏内目视复核。

---

## 10. 可复现步骤

```bash
# 1) 逐键比对 fabric 与 forge（forge 为纯 LF，只取行内容）
# 2) 仅在 11 个行下标上整行替换为 forge 内容，join("\r\n") 写回，禁用 json.dump
python tools/check_lang_sync.py          # [OK] 四线键集一致
python tools/audit_patchouli_keys.py     # ✅ 无悬空引用。
python tools/verify_fabric_assets.py     # === PASS: 8 项闭环全部通过 ===
```

断言（脚本内已全部通过）：每键在 fabric/forge 目标文件中各自有且仅有 1 行匹配；缩进均为 2 空格；行尾逗号存在性一致；新旧行内容不相同；写回后 `CRLF == LF`（无裸 LF）、无 BOM、`json.loads` 通过、键数不变。
