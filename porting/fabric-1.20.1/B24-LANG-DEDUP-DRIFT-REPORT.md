# B24 · fabric-1.20.1 语言文件重复键清理 + 跨线取值漂移分类审计

| 项 | 值 |
|---|---|
| 仓库 | `D:/MCProject/astral_dice_multiloader`（分支 `multi-main`） |
| 蓝本 | `forge-1.20.1/src/main/resources/assets/astral_dice/lang/{zh_cn,en_us,ja_jp}.json`（各 1070 键） |
| 目标 | `fabric-1.20.1/src/main/resources/assets/astral_dice/lang/{zh_cn,en_us,ja_jp}.json`（各 1094 键） |
| 第一部分 | 只删重复键行，未改动任何其他键的取值 / 顺序 / 缩进 |
| 第二部分 | 只读审计，**未修改任何取值** |

## 0. 结论摘要

| 指标 | 结果 |
|---|---|
| 三语键数（解析后） | zh_cn **1094** / en_us **1094** / ja_jp **1094**（三语键集一致） |
| 重复键 | 三语均为 **0**；`effect.astral_dice.concealment` 与 `effect.astral_dice.concealment.description` 各只剩 1 处（第 229 行） |
| forge 侧独有键 | 0 条 |
| fabric 侧独有键（平台键，不参与本表） | 24 条 |
| 共有键取值不同（union） | **159** 条（zh_cn 112 / en_us 158 / ja_jp 104） |
| P 平台命名差异 | **0** 条 |
| C 已裁决裁剪 | **1** 条 |
| D 真漂移 | **158** 条（D-N 14 / D-M 41 / D-W 103） |
| U 无法判定 | **0** 条 |

## 1. 第一部分：重复键清理（允许改，已改）

### 1.1 问题

fabric 三语各自的同文件内出现了**同一键两次**：`effect.astral_dice.concealment` 与 `effect.astral_dice.concealment.description`，分别在原文 228–229 行与 860–861 行，两处取值逐字相同。
JSON 解析器遇到重复键会**静默取最后一个**、不报任何错，所以这类问题「看起来没事」，但改一处、另一处残留时极易踩空（谁后出现谁生效），也干扰键数核对与语言同步门禁。

### 1.2 改动

| 文件 | 动作 | 影响行 | 保留 |
|---|---|---|---|
| `fabric-1.20.1/src/main/resources/assets/astral_dice/lang/zh_cn.json` | 删除第二处（行号较大的那一对） | −2 行（原 860、861 行） | 第一处（228–229 行） |
| `fabric-1.20.1/src/main/resources/assets/astral_dice/lang/en_us.json` | 删除第二处（行号较大的那一对） | −2 行（原 860、861 行） | 第一处（228–229 行） |
| `fabric-1.20.1/src/main/resources/assets/astral_dice/lang/ja_jp.json` | 删除第二处（行号较大的那一对） | −2 行（原 860、861 行） | 第一处（228–229 行） |

未重排键序、未改缩进、未改任何其他键的取值；只做了行级删除。

### 1.3 键数核对（与本批简报期望值 1093 的差异说明）

- 简报的 `1095 − 2 = 1093` 前提不成立：**重复键在 `json.loads` 里本来就已经被折叠成 1 个键**，删掉重复行减少的是「原文里的键行数」（1097 → 1095），解析后的键数仍是 1095。
- 随后**并发写入**（另一执行者做 B23 收尾，文件 mtime 19:42:06）删掉了 fabric 独有的平台键 `tooltip.astral_dice.weird_dice_cooldown`（该键描述的能力在 fabric 已被删除，改名为 `tooltip.astral_dice.weird_dice_heal`，见 `porting/fabric-1.20.1/B23-CHIP-TOOLTIP-REPORT.md` §4 与 `fabric-1.20.1/build/b23_parts/09.md:6`），键数 1095 → **1094**。
- 1094 − 1070 = 24，正好是 fabric 独有的平台键（`accessories.*` / `trinkets.*` / `msg.astral_dice.fanny_event*` 等）。
- 结论：**当前三语各 1094 键**，与「1093」的 1 之差来自上述并发写入，非本批改动所致。本批删除动作经原文扫描证明已完全生效（见 1.4 d）。

### 1.4 验收输出

**a) 键数** — 由 1.4 d 的原文扫描给出：三语原文键行 = 解析键 = 1094。

**b) 语言同步门禁**

```
[OK]   fabric-1.20.1: en_us=1094 键，3 份语言键集一致
EXIT=0
```

**c) fabric 资源闭环门禁**

```
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

**d) 原文扫描断言（不靠 json 解析，逐键计次）**

脚本对每个文件用正则 `^\s*"(<key>)"\s*:` 统计原文出现次数（正则不限缩进——该文件里 24 个键的行首缩进被前序批次改成了 0 空格，按 2 空格统计会漏），再与 `json.loads` 的键数互相印证：

```
zh_cn: 原文键行=1094 解析键=1094 重复键=无 bareLF=0 CRLF=1096 BOM=False concealment出现=2 -> OK
en_us: 原文键行=1094 解析键=1094 重复键=无 bareLF=0 CRLF=1096 BOM=False concealment出现=2 -> OK
ja_jp: 原文键行=1094 解析键=1094 重复键=无 bareLF=0 CRLF=1096 BOM=False concealment出现=2 -> OK
断言: 三语每个键在原文中出现次数均为 1（concealment 两个键各 1 次）;PASS
EXIT=0
```

**e) 删行量证明**

```
257	9	fabric-1.20.1/src/main/resources/assets/astral_dice/lang/en_us.json
257	9	fabric-1.20.1/src/main/resources/assets/astral_dice/lang/ja_jp.json
257	9	fabric-1.20.1/src/main/resources/assets/astral_dice/lang/zh_cn.json
```

说明：`numstat` 的删除列是对 HEAD 的累计值（HEAD 版 lang 只有 848 行，本工作树已累积多个批次改动），无法单独隔离本批的 2 行。本批动手前后的实测序列为 **`257 6` →（本批删除）`257 8` →（并发写入再删 1 键）`257 9`**：删除列恰 +2 对应本批两行，末位 +1 属于 1.3 的并发写入。功能层面的证明以 1.4 d 为准（每个键在原文只出现一次）。

## 2. 第二部分：跨线取值漂移分类（只读，未改值）

### 2.1 口径与子类定义

| 分类 | 含义 | 处置 |
|---|---|---|
| **P** | 平台命名差异（Curios↔Trinkets/Accessories、加载器名、平台措辞） | 保留 |
| **C** | 已裁决裁剪（`irons_spellbooks` / `enigmaticlegacy` / `bountiful` 等不移植联动） | 保留 |
| **D** | 真漂移：与平台无关、与裁剪无关，两侧描述不同行为/数值/机制 | 需要修（本批不修） |
| **U** | 证据不足，无法判定 | 待补证 |

D 组按风险再分三个子类：**D-N** = 数值 / 常量不等（两侧写出不同数字）；**D-M** = 机制或触发条件不同（同一道具/技能两侧描述不同玩法）；**D-W** = 措辞 / 术语 / 命名 / 信息量差异（不改变可执行语义，但存在术语分裂或违反立牌命名权威表）。

**P 组为何是 0 条**：平台命名键（`accessories.slot.*` / `trinkets.slot.*` / `accessories.slot_group.astral_dice`）在 forge 侧**根本不存在**，是 fabric 独有键，不进入「共有键取值不同」的集合；159 条漂移键的取值里也没有出现 Curios / Trinkets / Accessories 字样（grep 命中 0）。故 P 组在本表内无条目。

### 2.2 分布

| 分类 | 条数 |
|---|---|
| D-N 数值 / 常量不等 | 14 |
| D-M 机制或触发条件不同 | 41 |
| D-W 措辞 / 术语 / 命名 / 信息量 | 103 |
| C 已裁决裁剪 | 1 |
| P 平台命名差异 | 0 |
| U 无法判定 | 0 |
| **合计** | **159** |

表中「语」列标出该键发生漂移的语种；forge / fabric 两列展示差异最明显的那一语（优先 zh_cn，其次 en_us、ja_jp）。

### 2.3 D-N 数值 / 常量不等（14 条）

| 键名 | 语 | forge 值 | fabric 值 | 分类 | 判定依据 |
|---|---|---|---|---|---|
| `astral_dice.guide.entry.battle_dice.3` | zh_cn+en_us+ja_jp | 骰子可经铁砧以星币升星：星级越高赐福加成越强、卡牌费用上限越高（0★=3、1★=4、2★=5、3★=6）；卡牌栏格数由骰子品质决定（基础 4 格、黄金/玻璃/下界岩 8 格、钻石/绿宝石/黑曜石/诡异/紫晶 10 格、下界合金/绯红/末影 12 格，攻防各半），与星级无关；基础→黄金→钻石→下界合金的升级链解锁更多筹码栏位。 | 骰子可经铁砧以星币升星，星级越高赐福加成越强，卡牌栏格数也越多（0★=4、1★=6、2★=8、3★=12，攻防各半）；基础→黄金→钻石→下界合金的升级链解锁更多筹码栏位（卡牌栏格数由星级决定，各品阶同星级格数相同；同一阶层的骰子均可作为升级基础）。 | D-N | 同一系统两套数值：forge 卡牌栏格数由骰子品质决定（基础 4/黄金 8/钻石 10/下界合金 12，与星级无关），费用上限 0★=3/1★=4/2★=5/3★=6；fabric 改为由星级决定（0★=4/1★=6/2★=8/3★=12）并删掉费用上限一条。二者不可能同时成立。 |
| `astral_dice.guide.entry.bookmark_chip.1` | zh_cn+en_us+ja_jp | 使伤害类效果牌伤害加成 +2。 | 使伤害类效果牌伤害加成 +1。 | D-N | forge +2、fabric +1。代码权威：forge BookmarkChipItem.java:17 DAMAGE_BONUS = 2（注释「2026-10-15 用户裁决:+1 → +2」）；fabric 同文件:17 DAMAGE_BONUS = 1 ⇒ fabric 代码与文案都停在旧值。 |
| `astral_dice.guide.entry.diamond_dice.1` | zh_cn+en_us+ja_jp | 钻石骰子。更高品阶，解锁更多筹码栏位；卡牌栏格数由骰子品质决定（攻防各 5，共 10 格），与星级无关。 | 钻石骰子。更高品阶，解锁更多筹码栏位；卡牌栏格数由星级决定（各品阶同星级相同）。 | D-N | 与 battle_dice.3 同源冲突：forge「由骰子品质决定（5 格）…与星级无关」，fabric「由星级决定、各品阶同星级相同」。 |
| `astral_dice.guide.entry.emerald_dice.1` | zh_cn+en_us+ja_jp | 绿宝石骰子。与钻石骰子同阶，可由钻石骰子升级；佩戴后与村民交易时按 1:1 用星币代替绿宝石支付。 | 绿宝石骰子。与钻石骰子同阶，可由钻石骰子升级；佩戴后与村民交易时用星币代替绿宝石，并享受 20%% 折扣。 | D-N | forge「按 1:1 用星币代替绿宝石支付」vs fabric「用星币代替绿宝石并享受 20%% 折扣」，两种结算规则。 |
| `astral_dice.guide.entry.golden_dice.1` | zh_cn+en_us+ja_jp | 黄金骰子。比基础骰子品阶更高，解锁更多筹码栏位；卡牌栏格数由骰子品质决定（攻防各 4，共 8 格），与星级无关。 | 黄金骰子。比基础骰子品阶更高，解锁更多筹码栏位；卡牌栏格数由星级决定（各品阶同星级相同）。 | D-N | 与 battle_dice.3 同源：forge 品质决定 4 格且与星级无关，fabric 星级决定。 |
| `astral_dice.guide.entry.mimi_sign.2` | zh_cn+en_us+ja_jp | 被动技能-过期回收：返还卡牌时，每获得一张战斗牌获得 1 星币；装备时筹码栏位 +1；每主动返还累计 100 张战斗牌，获得一个随机筹码（每次主动最多 1 个）。 | 被动技能-过期回收：合成或返还卡牌时，每获得一张战斗牌获得 1 星币；装备时筹码栏位 +1；每主动返还累计 25 张战斗牌，获得一个随机筹码（每次主动最多 1 个）。 | D-N | forge 100 张 vs fabric 25 张（另 fabric 多「合成或」）。代码权威：两线 MimiSignItem.java 均为 RETURNED_CARD_THRESHOLD = 100 ⇒ fabric 文案 25 与自身代码直接矛盾。 |
| `astral_dice.guide.entry.nancy_lu_sign.2` | zh_cn+en_us+ja_jp | 被动技能-网络防火墙：免疫末影珍珠传送伤害。骰神赐福结束后，若周围 3 格内没有敌对生物，则攻击力 +3 并获得一张随机战斗牌；否则防御力 +3。 | 被动技能-网络防火墙：免疫末影珍珠传送伤害。骰神赐福结束后，若周围 6 格内没有敌对生物，则攻击力 +3 并获得一张随机战斗牌；否则防御力 +3。 | D-N | 周围 3 格 vs 6 格。代码权威：forge NancyLuSignItem.java:54 PASSIVE_RANGE = 3.0；fabric 同文件:57 PASSIVE_RANGE = 6.0 ⇒ 行为差异不是文案差异。 |
| `astral_dice.guide.entry.netherite_dice.1` | zh_cn+en_us+ja_jp | 下界合金骰子。最高阶的骰子，解锁最多筹码栏位；卡牌栏格数由骰子品质决定（攻防各 6，共 12 格），与星级无关。 | 下界合金骰子。最高阶的骰子，解锁最多筹码栏位；卡牌栏格数由星级决定（各品阶同星级相同）。 | D-N | 与 battle_dice.3 同源：forge 品质决定 6 格且与星级无关，fabric 星级决定。 |
| `astral_dice.guide.entry.warp_engine_chip.1` | zh_cn+en_us+ja_jp | 触发瞬移时，充能 +2 并获得迅捷 (0:10)。计入的传送：末影珍珠、末影骰子不死图腾后的安全瞬移、维度传送门、Waystone（需安装 Waystones）。传送门与 Waystone 触发共享 3:00 冷却；末影珍珠与末影骰子瞬移无冷却。 | 触发瞬移时，充能 +2 并获得迅捷 (0:10)。计入的传送：末影珍珠、末影骰子不死图腾后的安全瞬移、维度传送门、Waystone（需安装 Waystones）。传送门与 Waystone 触发共享 5:00 冷却；末影珍珠与末影骰子瞬移无冷却。 | D-N | 传送门/Waystone 共享冷却 3:00 vs 5:00。代码权威：forge WarpEngineChipItem.java:40 PORTAL_WAYSTONE_COOLDOWN_TICKS = 20*60*3（注释「2026-10-15 用户裁决:5:00 → 3:00」）；fabric 同文件:38 = 20*60*5 ⇒ fabric 未跟进裁决。 |
| `tooltip.astral_dice.chip.bookmark` | zh_cn+en_us+ja_jp | 使伤害类效果牌伤害加成 §e+2§r | 使伤害类效果牌伤害加成 §e+1§r | D-N | 与 guide bookmark_chip.1 同源：+2 vs +1；forge 代码 DAMAGE_BONUS = 2（2026-10-15 裁决 +1→+2），fabric 仍为 1。 |
| `tooltip.astral_dice.chip.warp_engine` | zh_cn+en_us+ja_jp | 触发瞬移时：充能 §e+2§7，并获得 §9迅捷 (0:10)§r<br>维度传送门与 Waystone 共享 §93:00§7 冷却；末影珍珠与末影骰子瞬移无冷却 | 触发瞬移时：充能 §e+2§7，并获得 §9迅捷 (0:10)§r<br>维度传送门与 Waystone 共享 §95:00§7 冷却；末影珍珠与末影骰子瞬移无冷却 | D-N | 与 guide warp_engine_chip.1 同源：3:00 vs 5:00；forge 代码=3:00（2026-10-15 裁决），fabric 代码仍 5:00。 |
| `tooltip.astral_dice.emerald_dice_trade` | zh_cn+en_us+ja_jp | 与村民交易时用星币代替绿宝石支付（§e1:1§2） | 与村民交易时用星币代替绿宝石，并享受 §e20%%§2 折扣 | D-N | 与 guide emerald_dice.1 同源：1:1 等值代付 vs 20%% 折扣。 |
| `tooltip.astral_dice.sign.mimi_passive` | zh_cn+en_us+ja_jp | 返还卡牌时，每获得一张战斗牌获得 §e1§7 星币<br>装备时筹码栏位 §e+1§r<br>每主动返还累计 §e100§7 张战斗牌后获得一个随机筹码（每次主动最多 §e1§7 个） | 合成或返还卡牌时，每获得一张战斗牌获得 §e1§7 星币<br>装备时筹码栏位 §e+1§r<br>每主动返还累计 §e25§7 张战斗牌后获得一个随机筹码（每次主动最多 §e1§7 个） | D-N | 与 guide mimi_sign.2 同源：100 vs 25（另多「合成或」）；两线代码 RETURNED_CARD_THRESHOLD 均为 100，fabric 文案与自身代码矛盾。 |
| `tooltip.astral_dice.sign.nancy_lu_passive` | zh_cn+en_us+ja_jp | 免疫末影珍珠传送时的伤害。骰神赐福效果结束后，若周围 §e3§7 格范围内没有敌对生物，则攻击力 §e+3§7 并获得一张随机战斗牌，否则防御力 §e+3§r | 免疫末影珍珠传送时的伤害。骰神赐福效果结束后，若周围 §e6§7 格范围内没有敌对生物，则攻击力 §e+3§7 并获得一张随机战斗牌，否则防御力 §e+3§r | D-N | 与 guide nancy_lu_sign.2 同源：3 格 vs 6 格；两线 PASSIVE_RANGE 分别为 3.0 / 6.0。 |

### 2.4 D-M 机制或触发条件不同（41 条）

| 键名 | 语 | forge 值 | fabric 值 | 分类 | 判定依据 |
|---|---|---|---|---|---|
| `astral_dice.guide.entry.airbag_chip.1` | zh_cn+en_us+ja_jp | 受到致命伤害时，消耗 6 层充能，使本次伤害无效（冷却 1:00）。该保命效果优先于磨刀石、不死图腾与末影骰子；无视无敌的致死伤害同样有效（如指令 /kill）；掉入虚空的致命伤害无法被无效化。此外，受到伤害时充能 +1（冷却 15 秒）。 | 受到致命伤害时，消耗 6 层充能，使本次伤害无效（冷却 1:00）。该保命效果优先于磨刀石、不死图腾与末影骰子；无视无敌的致死伤害同样有效（如指令 /kill）。 | D-M | forge 多出「掉入虚空的致命伤害无法被无效化」与「受到伤害时充能 +1（冷却 15 秒）」两句，fabric 整段删除。同一道具机制描述少了两个分支。 |
| `astral_dice.guide.entry.atm_chip.1` | zh_cn+en_us+ja_jp | 装备后，转账时使双方各获得 1 层星光与 2 星币。 | 装备时获得 1 层星光；使用星光兑换星币时，兑换量 +40%%（向下取整；按「下限为 1」策略，加成至少 1 枚）。 | D-M | forge 是「转账时双方各得 1 层星光 + 2 星币」；fabric 换成「使用星光兑换星币时兑换量 +40%%（向下取整、下限 1 枚）」。两套不同收益机制。 |
| `astral_dice.guide.entry.bank_card_high_chip.1` | zh_cn+en_us+ja_jp | 装备时获得 7 层星光；每次星光兑换成功后，按当前佩戴的银行卡（余额少 / 余额多 / 用不完）与星币锤补充星光 —— 本筹码计 7 层，多枚叠加，上限 32。 | 装备期间获得 7 层基础星光。 | D-M | forge 含「每次星光兑换成功后按佩戴的银行卡与星币锤补充星光 —— 本筹码计 7 层，多枚叠加，上限 32」；fabric 只剩「装备期间获得 7 层基础星光」。 |
| `astral_dice.guide.entry.bank_card_low_chip.1` | zh_cn+en_us+ja_jp | 装备时获得 4 层星光；每次星光兑换成功后，按当前佩戴的银行卡（余额少 / 余额多 / 用不完）与星币锤补充星光 —— 本筹码计 4 层，多枚叠加，上限 32。 | 装备期间获得 4 层基础星光。 | D-M | 同 bank_card_high_chip.1：forge 有补充星光/叠加/上限 32，fabric 只剩基础 4 层。 |
| `astral_dice.guide.entry.bank_card_unlimited_chip.1` | zh_cn+en_us+ja_jp | 装备时获得 3 层星光；每次星光兑换成功后，按当前佩戴的银行卡（余额少 / 余额多 / 用不完）与星币锤补充星光 —— 本筹码计 3 层，多枚叠加，上限 32；每次骰神赐福结束后，自身及友方玩家（已加入队伍时为同队成员，未加入任何队伍时为全体玩家）获得 3 星币。 | 装备时获得 3 层星光；每次骰神赐福结束后，自身及友方玩家（已加入队伍时为同队成员，未加入任何队伍时为全体玩家）获得 3 星币。 | D-M | forge 含「补充星光 —— 本筹码计 3 层，多枚叠加，上限 32」，fabric 只保留「装备时 3 层 + 赐福结束 3 星币」。 |
| `astral_dice.guide.entry.eagle_scope_chip.1` | zh_cn+en_us+ja_jp | 攻击时对目标施加 1 层标记；对带标记目标额外造成其标记层数 ×2 的攻击伤害。 | 攻击时对目标施加 1 层标记；本次战斗提升目标标记层数 ×2 的攻击力。 | D-M | forge「对带标记目标额外造成其伤害」，fabric「本次战斗提升攻击力」——增益对象与触发条件都换了。 |
| `astral_dice.guide.entry.electric_glove_chip.1` | zh_cn+en_us+ja_jp | 使用伤害类效果牌攻击目标时，使目标 3 格内所有目标（含主目标）受到 2 点魔法伤害，延迟 1 秒生效；每使一个目标受到该效果伤害，获得 1 层【充能】。冷却 1:00。 | 使用效果牌时，充能 +1。充能不少于 4 层时，使用伤害类效果牌会消耗 4 层充能，使本次远程和魔法伤害同时命中目标 3 格范围内的其他目标。 | D-M | forge 写「攻击目标时所有（含主目标）受 2 点魔法伤害，每使一个目标受伤获 1 层充能」；fabric 写「使用效果牌时充能 +1，充能≥4 时消耗 4 层让本次远程/魔法伤害额外命中其他目标」。整套触发与结算重写。 |
| `astral_dice.guide.entry.fanny_sign.1` | zh_cn+en_us+ja_jp | 主动技能-事件选择：从当前事件池随机抽出 2 个全体事件，选择其中 1 个并触发；必须选中并点击确认才会触发。 | 主动技能-麻烦制造者：立即触发以下随机事件之一：获得生命恢复 (0:30)；获得力量 (0:30)；获得一张攻击-特大；获得一张随机效果牌（不含专属）+ 3 星币；获得滋养 (2:00)（需安装农夫乐事）与饱和 (0:30)；获得迅捷 (0:30)。 | D-M | forge 描述「从事件池抽 2 个全体事件、选 1 个触发」；fabric 描述「麻烦制造者：立即触发随机之一（回血/力量/攻击-特大/效果牌/滋养/迅捷）」。同一主动技能两套效果定义。 |
| `astral_dice.guide.entry.fanny_sign.2` | zh_cn+en_us+ja_jp | （接上）15 秒内未选择、死亡或退出游戏时强制触发第一个；确认后立即关闭界面并进入事件流程；技能音效在按下技能键时播放。 | （接上）受到瞬间伤害；获得中毒 (0:15)；获得饥饿 (0:30) 与反胃 (0:07)；获得凋灵 (0:07) 与黑暗 (0:05)；获得虚弱 (0:15) 与挖掘疲劳 (0:30)。 | D-M | 承 fanny_sign.1：forge「未选择/死亡/退出时强制触发第一个」，fabric 换成「瞬间伤害/中毒/饥饿/反胃/凋灵/黑暗/虚弱/挖掘疲劳」的负面池。 |
| `astral_dice.guide.entry.lulu_sign.2` | zh_cn+en_us+ja_jp | 被动技能-细胞分裂：受到敌对目标攻击时，获得 1 层治愈，并使主动技能冷却 -10 秒（每 15 秒最多触发一次）。 | 被动技能-细胞分裂：受到敌对目标攻击时，获得 1 层治愈，并使主动技能冷却 -10 秒。 | D-M | forge 有「每 15 秒最多触发一次」，fabric 整句删除 ⇒ 被动触发频率描述不一致。 |
| `astral_dice.guide.entry.magic_quiver_chip.1` | zh_cn+en_us+ja_jp | 使用伤害效果牌，并对已有标记的目标造成远程或魔法伤害后，返还本次使用的效果牌并对目标施加 1 层标记。每 30 秒只触发一次。 | 使用伤害效果牌，并对已有标记的目标造成远程或法术伤害后，返还第一张使用的效果牌并对目标施加 1 层标记。每 30 秒只触发一次。 | D-M | forge「使用伤害效果牌对敌对目标造成伤害时返还并施加标记」；fabric「对已有标记的目标造成远程或法术伤害后返还第一张并施加标记」——触发前提不同。 |
| `astral_dice.guide.entry.member_recommendation_chip.1` | zh_cn+en_us+ja_jp | 触发骰神赐福时，若物品栏卡牌数少于 5 张，获得一张随机卡牌。装备期间，自身商店内所有卡牌价格 -1 星币（可与经商立牌的降价叠加，最低 0 星币）。 | 每次触发骰神赐福时，获得一张随机卡牌。 | D-M | forge「赐福时若卡牌<5 张才给随机卡；装备期间商店内所有卡牌 -1 星币」；fabric 只剩「每次赐福获得一张随机卡牌」，条件与降价均消失。 |
| `astral_dice.guide.entry.nancy_lu_sign.1` | zh_cn+en_us+ja_jp | 主动技能-远程侵入：立即进入完全隐身（最多 30 秒）。隐身期间攻击敌对目标并破除隐身时，立即获得固定 +6 攻击力，持续 2:00（按下主动本身不给加成，隐身自然到期而未攻击同样不给）。 | 主动技能-远程侵入：立即进入完全隐身（最多 30 秒）。对敌对目标或玩家发动攻击时立即解除隐身，并消耗主物品栏中随机一张战斗牌，按该牌费用 ×2 增加攻击力（最低 +2），持续 2:00（主物品栏无战斗牌可消耗时同样获得保底 +2）。 | D-M | forge「破隐时固定 +6 攻击力」；fabric「消耗主物品栏随机一张战斗牌，按该牌费用 ×2 提升（最低 +2）」。两线代码已分叉：forge NancyLuSignItem.java:55 ACTIVE_ATTACK_BONUS = 6；fabric 同文件:60-61 ACTIVE_BONUS_MULTIPLIER = 2 / ACTIVE_MIN_BONUS = 2。 |
| `astral_dice.guide.entry.parunan_sign.1` | zh_cn+en_us+ja_jp | 经商立牌。主动「网络购物」：远程打开卡牌商店界面（购买卡牌、星光兑换、向其他在线玩家转账星币）；主动本身不施加任何效果，佩戴时触发后进入 120 秒冷却。 | 主动技能-套现：按 2:1 将星光兑换为星币，并随机获得以下任一效果：饱和 (0:30)、幸运 (5:00)、村庄英雄 (15:00)。 | D-M | forge「网络购物：远程打开卡牌商店（购买/兑换/转账）」；fabric「技能-套现：按 2:1 把星光换星币并随机获得饱和/幸运/村庄英雄」。同一技能两套功能。 |
| `astral_dice.guide.entry.parunan_sign.2` | zh_cn+en_us+ja_jp | 被动「传奇商人」：每次进入商店时获得 1 星币；自身与团队成员的商店中可购买战斗牌数量 +1；进入商店后每张牌有 10%% 概率降至 1 星币；转账后使当前商店内第一张未降价卡牌降至 1 星币；星光兑换额外获得 50%% 收入（进店星币、转账降价、兑换加成为佩戴者本人；战斗牌 +1 与降价对自身及团队成员生效）。 | 被动技能-传奇商人：每隔一段时间 +1 星光（有上限）；触发骰神赐福时，获得首次骰点 ×2 的星光。 | D-M | 承上：forge 被动（入店得星币、商店卡牌 +1、10%% 降价、50%% 兑换加成），fabric 改成「每 N 增加 1 层星光（上限）+ 赐福时获得首次骰点 ×2 星光」。 |
| `astral_dice.guide.entry.playstyles.2` | zh_cn+en_us+ja_jp | 星光：手电筒、八面骰、银行卡、星币锤等产出与消耗，经商立牌（星光兑换星币、兑换 +50%%）、看板娘立牌联动。 | 星光：手电筒、八面骰、ATM、银行卡、星币锤等产出与消耗，经商、看板娘立牌联动。 | D-M | forge 玩法清单列「星光兑换/兑换 +50%%」等；fabric 把 ATM、银行卡、星币锤写进清单并删掉 +50%% 表述。 |
| `astral_dice.guide.entry.star_coin_hammer_chip.1` | zh_cn+en_us+ja_jp | 装备时获得 5 层星光；每次星光兑换成功后，按当前佩戴的银行卡（余额少 / 余额多 / 用不完）与星币锤补充星光 —— 本筹码计 5 层，多枚叠加，上限 32；若物品栏持有至少 32 枚星币，每次进入骰神赐福消耗 18 星币，并按持有星币总数的 30%% 提升攻击伤害（上限 100；星币袋按 9 枚计；零散星币不足时自动拆开 1 个星币袋、只扣走所需枚数、其余留在物品栏，无法安全拆袋时不消耗）。 | 装备时获得 5 层星光；若物品栏持有至少 32 枚星币，每次进入骰神赐福消耗 18 星币，并按持有星币总数的 30%% 提升攻击伤害（上限 100；星币袋按 9 枚计；零散星币不足时自动拆开 1 个星币袋、只扣走所需枚数、其余留在物品栏，无法安全拆袋时不消耗）。 | D-M | forge 含「每次兑换成功后按佩戴银行卡与星币锤补充星光 —— 本筹码计 5 层，多枚叠加，上限 32」，fabric 整段删除。 |
| `astral_dice.guide.entry.teru_sign.2` | zh_cn+en_us+ja_jp | 被动「狐光」：使用主动技能后，立即获得 3 层「狐光」。当自身或「降神」目标获得或装备攻击牌时，使自身获得一层「狐光」。 | 被动「狐光」：使用主动技能后，立即获得 3 层「狐光」。当自身或「降神」目标合成、获得或装备攻击牌时，使自身获得一层「狐光」。 | D-M | fabric 增补「合成、」进入触发来源；forge 侧 2026-10-09 已删除 ItemCraftedEvent 监听，两线实现分叉。 |
| `astral_dice.guide.entry.vitamin_pill_chip.1` | zh_cn+en_us+ja_jp | 获得任意卡牌时，治愈 +1。 | 合成或获得任意卡牌时，治愈 +1。 | D-M | fabric 增补「合成或」作为获得途径，forge 无。 |
| `astral_dice.guide.entry.weird_dice.1` | zh_cn+en_us+ja_jp | 诡异骰子。与钻石骰子同阶，可由钻石骰子升级；你的战斗骰更容易骰出低点数（1-3）。 | 诡异骰子。与钻石骰子同阶，可由钻石骰子升级；立牌主动技能冷却时间减半，但你的战斗骰更容易骰出低点数（1-3）。 | D-M | fabric 增补「立牌主动技能冷却时间减半，但…」，该能力在 fabric 已被删除（见 porting/fabric-1.20.1/B23-CHIP-TOOLTIP-REPORT.md §4、build/b23_parts/09.md:6）⇒ fabric 侧为虚假描述。 |
| `msg.astral_dice.nancy_lu_active` | zh_cn+en_us+ja_jp | 骇客立牌：已触发完全隐身 (%d 秒)，攻击敌对目标破隐时获得固定攻击力加成 | 骇客立牌：已触发完全隐身 (%d 秒)，攻击任意一个目标获得攻击力加成并移除完全隐身 | D-M | 与 tooltip.sign.nancy_lu_active 同源：forge 固定攻击力加成，fabric 消耗随机战斗牌按费用 ×2。 |
| `tooltip.astral_dice.card.berserk` | zh_cn+en_us+ja_jp | §e左键§7 对准友方玩家使用，§e右键§7 对自身使用 | §e左键§7 对准非敌对生物使用（敌对生物不可选），§e右键§7 对自身使用 | D-M | 可选目标口径不同：forge「友方玩家」，fabric「非敌对生物（敌对生物不可选）」。 |
| `tooltip.astral_dice.card.bite` | zh_cn+en_us+ja_jp | 攻击力增加当前觉醒层数（最多 §e4§7 点），触发「骰神赐福」后，获得 §e1§7 层「觉醒」 \| 剩余次数：§e%s§7 | 装备该战斗牌，并触发「骰神赐福」后，获得 §e1§7 层「觉醒」，使玩家攻击力增加当前觉醒层数（最多 §e4§7 点），持续到当前骰神赐福效果结束 \| 剩余次数：§e%s§7 | D-M | fabric 增补「装备该战斗牌，并触发骰神赐福后」与「持续到当前效果结束」，把触发前提与持续时间写死。 |
| `tooltip.astral_dice.card.express_delivery` | zh_cn+en_us+ja_jp | §e左键§7 对准友方玩家使用，§e右键§7 对自身使用<br>使目标获得 §9迅捷 II (1:00)§7 | §e左键§7 对准非敌对生物使用（敌对生物不可选），§e右键§7 对自身使用<br>使目标获得 §9迅捷 II (1:00)§7 | D-M | 与 card.berserk 同口径：forge「友方玩家」vs fabric「非敌对生物（敌对生物不可选）」。 |
| `tooltip.astral_dice.card.luxury_feast` | zh_cn+en_us+ja_jp | §e左键§7 对准友方玩家使用，§e右键§7 对自身使用<br>治疗目标及周围 §e6§7 格内的友方玩家，各恢复使用者最大生命值 §e30%%§7<br>对亡灵生物使用会造成等量伤害 | §e左键§7 对准非敌对生物使用（敌对生物不可选），§e右键§7 对自身使用<br>治疗目标及周围 §e6§7 格内的友方玩家（已加入队伍时仅同队成员；未加入任何队伍时为全体玩家），各恢复使用者最大生命值 §e30%%§7<br>亡灵目标改为受到等量伤害而非治疗 | D-M | 目标与队伍口径都改了：forge「友方玩家…」vs fabric「非敌对生物（敌对不可选）（队伍时仅同队；无队伍全体）…」。 |
| `tooltip.astral_dice.chip.atm` | zh_cn+en_us+ja_jp | 转账后，使双方各获得 §e1§7 层星光与 §e2§7 星币 | 装备时获得 §e1§7 层星光。使用星光兑换星币时，兑换量增加 §e40%%§7（向下取整，至少 §e1§7 枚） | D-M | 与 guide atm_chip.1 同源：forge 转账给双方星光+2 星币，fabric 星光兑换 +40%%。 |
| `tooltip.astral_dice.chip.bank_card_high` | en_us | Gain §e7§7 Starlight on equip | Provides §e7§7 base Starlight (increases base value) | D-M | 与 guide bank_card_high_chip.1 同源：forge 含补充星光机制，fabric 只写基础层数。 |
| `tooltip.astral_dice.chip.bank_card_low` | en_us | Gain §e4§7 Starlight on equip | Provides §e4§7 base Starlight (increases base value) | D-M | 同上：forge 含补充星光机制，fabric 只写基础层数。 |
| `tooltip.astral_dice.chip.eagle_scope` | zh_cn+en_us+ja_jp | 攻击时对目标施加 §e1§7 层标记，对带标记目标额外造成其标记层数 §e2§7 倍的攻击伤害 | 攻击时对目标施加 §e1§7 层标记，本次战斗提升目标标记层数 §e2§7 倍的攻击力 | D-M | 与 guide eagle_scope_chip.1 同源：forge 额外伤害 vs fabric 本次战斗攻击力提升。 |
| `tooltip.astral_dice.chip.electric_glove` | zh_cn+en_us+ja_jp | 使用伤害效果牌攻击目标时：目标 §e3§7 格内所有目标受到 §e2§7 点魔法伤害<br>每使 §e1§7 个目标受到该效果伤害：充能 §e+1§7<br>冷却 §91:00§7 | 使用效果牌时：充能 §e+1§r<br>充能不少于 §e4§7 层时使用伤害类效果牌：消耗 §e4§7 层充能，使本次远程和魔法伤害同时命中目标 §e3§7 格范围内的其他目标 | D-M | 与 guide electric_glove_chip.1 同源：触发（攻击目标 vs 使用效果牌）与消耗（每目标 +1 vs 消耗 4 层）不同。 |
| `tooltip.astral_dice.chip.magic_quiver` | zh_cn+en_us+ja_jp | 使用伤害效果牌对敌对目标造成伤害时，返还此效果牌并对目标施加 §e1§7 层标记。每 §930 秒§7 只触发一次 | 使用伤害效果牌，并对已有标记的目标造成远程或法术伤害后，返还第一张使用的效果牌并对目标施加 §e1§7 层标记。每 §930 秒§7 只触发一次 | D-M | 与 guide magic_quiver_chip.1 同源：触发前提从任意伤害改为打带标记目标的远程/法术伤害。 |
| `tooltip.astral_dice.chip.member_recommendation` | zh_cn+en_us+ja_jp | 触发骰神赐福时，获得一张随机卡牌<br>仅在物品栏卡牌数少于 §e5§7 张时触发<br>装备期间，自身商店内所有卡牌价格 §e-1§7 星币 | 每次触发骰神赐福时，获得一张随机卡牌 | D-M | 与 guide member_recommendation_chip.1 同源：forge 有 <5 张条件与装备期间 -1 星币，fabric 二者皆无。 |
| `tooltip.astral_dice.chip.railgun` | zh_cn+en_us+ja_jp | 充能不少于 §e6§7 层时：攻击力 §e+5§r<br>近战攻击敌对目标时：延迟 §91 秒§7对目标 §e3§7 格范围内的敌对目标降下雷击（雷击伤害 = §e5§7 + 本次攻击伤害的 §e50%%§7）<br>雷击伤害无视目标防御力<br>冷却 §91:00§r | 充能不少于 §e6§7 层时：攻击力 §e+5§r<br>近战攻击敌对目标时：延迟 §91 秒§7对目标 §e3§7 格范围内的敌对目标降下雷击（雷击伤害 = §e5§7 + 本次攻击伤害的 §e50%%§7，下限 §e5§7 点）<br>雷击为独立的真伤伤害源，不触发骰神赐福<br>冷却 §91:00§r | D-M | fabric 增补「下限 5 点」「雷击为独立的真伤伤害源，不触发骰神赐福」，把伤害来源性质写死。 |
| `tooltip.astral_dice.chip.satellite` | zh_cn+en_us+ja_jp | 物品栏中「轨道炮」少于 §e6§7 张时，每 §91:00§7 获得一张「轨道炮」<br>使用「轨道炮」后，本轮出牌数 §e+1§7，每 §91:00§7 仅触发一次<br>效果牌的目标选择距离 §e+50%%§7 | 物品栏中「轨道炮」少于 §e6§7 张时，每 §91:00§7 获得一张「轨道炮」<br>使用「轨道炮」后，本轮出牌数 §e+1§7，每 §91:00§7 仅触发一次<br>「轨道炮」生效期间，远程或魔法击杀敌方目标后获得一张随机效果牌 | D-M | 完全不同的第三条：forge「效果牌目标选择距离 +50%%」，fabric「轨道炮生效期间远程/魔法击杀获得随机效果牌」。 |
| `tooltip.astral_dice.chip.vitamin_pill` | zh_cn+en_us+ja_jp | 获得任意卡牌时，治愈 §e+1§r | 合成或获得任意卡牌时，治愈 §e+1§r | D-M | 与 guide vitamin_pill_chip.1 同源：fabric 增补「合成或」获得途径。 |
| `tooltip.astral_dice.sign.fanny_active` | zh_cn+en_us+ja_jp | 在2个全体事件中选择1个全体事件并触发 | 立即触发以下随机事件之一：<br>  获得 §9生命恢复 (0:30)§r<br>  获得 §9力量 (0:30)§r<br>  获得一张：攻击-特大<br>  获得一张随机效果牌（不含专属）+ §e3§7 星币<br>  获得 §9滋养 (2:00)§7（需安装农夫乐事）和 §9饱和 (0:30)§r<br>  获得 §9迅捷 (0:30)§r<br>  获得 §c瞬间伤害§r<br>  获得 §c中毒 (0:15)§r<br>  获得 §c饥饿 (0:30)§7 和 §c反胃 (0:07)§r<br>  获得 §c凋灵 (0:07)§7 和 §c黑暗 (0:05)§r<br>  获得 §c虚弱 (0:15)§7 和 §c挖掘疲劳 (0:30)§r | D-M | 与 guide fanny_sign.1/2 同源：forge「选择 2 个全体事件之一」vs fabric「立即触发以下随机之一」。 |
| `tooltip.astral_dice.sign.lulu_passive` | zh_cn+en_us+ja_jp | 受到敌对目标攻击时：<br>  获得 §e1§7 层治愈<br>  主动技能冷却 §9-10 秒<br>  每 §915 秒§7 触发一次§r | 受到敌对目标攻击时：<br>  获得 §e1§7 层治愈<br>  主动技能冷却 §9-10 秒§r | D-M | 与 guide lulu_sign.2 同源：forge 有「每 15 秒触发一次」，fabric 无该冷却限制。 |
| `tooltip.astral_dice.sign.nancy_lu_active` | zh_cn+en_us+ja_jp | 使用后立即进入完全隐身状态（最多持续 §930 秒§7）。隐身期间攻击敌对目标并破除隐身时，立即获得固定 §e+6§7 攻击力，持续 §92:00§7（按下主动本身不给加成，隐身自然到期而未攻击同样不给） | 使用后立即进入完全隐身状态（最多持续 §930 秒§7）。若对敌对目标或玩家发动攻击，则立即解除隐身状态，同时消耗主物品栏中随机一张战斗牌，并增加该牌费用点数 §e×2§7（最低 §e+2§7）的攻击力，持续 §92:00§7（若主物品栏无战斗牌可消耗，同样保底增加 §e+2§7 攻击力） | D-M | 与 guide nancy_lu_sign.1 同源：固定 +6 vs 消耗卡牌费用 ×2（最低 +2）；两线代码常量已分叉。 |
| `tooltip.astral_dice.sign.parunan_active` | zh_cn+en_us+ja_jp | 远程打开卡牌商店 | 按 §e2:1§7 比例将持有的星光兑换为星币。<br>随机获得以下任一效果：<br>  §9饱和 (0:30)§r<br>  §9幸运 (5:00)§r<br>  §9村庄英雄 (15:00)§r | D-M | 与 guide parunan_sign.1 同源：远程开店 vs 2:1 兑换 + 随机增益。 |
| `tooltip.astral_dice.sign.parunan_passive` | zh_cn+en_us+ja_jp | 进入商店时获得 §e1§7 星币<br>自身与团队成员的商店：可购买战斗牌数量 §e+1§7<br>商店内每张卡牌有 §e10%%§7 概率降至 §e1§7 星币<br>转账后：当前商店内第一张未降价卡牌降至 §e1§7 星币<br>星光兑换额外获得 §e50%%§7 收入 | 每 §e%s§7 增加 §e1§7 层星光（上限 §e%s§7 层）。<br>触发骰神赐福时，获得首次骰点 §e×2§7 的星光 | D-M | 与 guide parunan_sign.2 同源：商店被动 vs 星光层数被动。 |
| `tooltip.astral_dice.sign.teru_passive` | zh_cn+en_us+ja_jp | 使用主动技能后，立即获得 §e3§7 层「狐光」<br>  当自身或「降神」目标获得或装备攻击牌时，使自身获得 §e1§7 层「狐光」 | 使用主动技能后，立即获得 §e3§7 层「狐光」<br>  当自身或「降神」目标合成、获得或装备攻击牌时，使自身获得 §e1§7 层「狐光」 | D-M | 与 guide teru_sign.2 同源：fabric 增补「合成、」作为触发来源。 |

### 2.5 D-W 措辞 / 术语 / 命名 / 信息量差异（103 条）

| 键名 | 语 | forge 值 | fabric 值 | 分类 | 判定依据 |
|---|---|---|---|---|---|
| `astral_dice.guide.category.dice.desc` | zh_cn+en_us+ja_jp | 骰子是核心装备：装备后，近战攻击会为自身施加「骰神赐福」；卡牌栏格数由骰子品质决定，筹码栏个数由骰子品阶与星级共同决定。在铁砧消耗星币可以升星：星级越高赐福加成越强、卡牌费用上限越高，筹码栏也随之增加。 | 骰子是核心装备：装备后，近战攻击会为自身施加「骰神赐福」；卡牌栏格数由骰子星级决定，筹码栏个数由骰子品阶与星级共同决定，在铁砧消耗星币能将骰子升星，以获得更多的槽位。 | D-W | forge 逐项列出「星级越高赐福加成越强、卡牌费用上限越高、筹码栏随之增加」；fabric 改为「星级越高…卡牌栏格数随星级」的概述。两线无相反硬数据，但 fabric 丢了「费用上限」一项，属信息量裁剪。 |
| `astral_dice.guide.entry.adrenaline_high_chip.1` | en_us | Adrenaline (High-Grade): at 50%% health or below, attack/defense +8 (counts as satisfied while the Papara Sign's Drain is active). | Adrenaline (High-Grade): at 50%% health or below, attack/defense +8 (counts as satisfied while the Vampire sign's Drain is active). | D-W | EN 立牌名由权威表的 Papara Sign 改成职称直译 Vampire Sign，违反 tools/sign_display_names.json（wiki 英文页名 + Sign）与「禁机翻职称」规则。 |
| `astral_dice.guide.entry.adrenaline_low_chip.1` | en_us | Adrenaline (Common): at 50%% health or below, attack/defense +3 (counts as satisfied while the Papara Sign's Drain is active). | Adrenaline (Common): at 50%% health or below, attack/defense +3 (counts as satisfied while the Vampire sign's Drain is active). | D-W | 同 adrenaline_high_chip.1：EN Papara Sign → Vampire Sign，违反权威命名表。 |
| `astral_dice.guide.entry.attack_card_epic.1` | zh_cn+en_us+ja_jp | 攻击牌（特大）：骰战中提供 1~10 点攻击力。 | 攻击牌（特大）：骰战中提供 1~10 点攻击力（单次掷取；点数越高越难命中，中位数约在范围的一半处）。 | D-W | fabric 增补「（单次掷取；点数越高越难命中，中位数约在范围的一半处）」，forge 无此句。 |
| `astral_dice.guide.entry.attack_card_large.1` | zh_cn+en_us+ja_jp | 攻击牌（大）：骰战中提供 1~6 点攻击力。 | 攻击牌（大）：骰战中提供 1~6 点攻击力（单次掷取；点数越高越难命中，中位数约在范围的一半处）。 | D-W | 同 attack_card_epic.1：fabric 增补「单次掷取/中位数」说明。 |
| `astral_dice.guide.entry.attack_card_medium.1` | zh_cn+en_us+ja_jp | 攻击牌（中）：骰战中提供 1~3 点攻击力。 | 攻击牌（中）：骰战中提供 1~3 点攻击力（单次掷取；点数越高越难命中，中位数约在范围的一半处）。 | D-W | 同 attack_card_epic.1：fabric 增补「单次掷取/中位数」说明。 |
| `astral_dice.guide.entry.attack_card_meito.1` | zh_cn+en_us+ja_jp | 名刀·嘎呜切：骰战中提供 1~20 点攻击。 | 名刀·嘎呜切：骰战中提供 1~20 点攻击（单次掷取；点数越高越难命中，中位数约在范围的一半处）。 | D-W | 同 attack_card_epic.1：fabric 增补「单次掷取/中位数」说明。 |
| `astral_dice.guide.entry.bonnie_sign.1` | zh_cn+en_us+ja_jp | 主动技能-隐匿行动：指定一个敌对目标，向其施加 1 层标记和「隐匿调查」；随后按目标当前标记层数 §e×2§7 获得星币。 | 主动技能-隐匿行动：指定一个敌对目标，向其施加「隐匿调查」；若目标带「标记」，按标记层数 §e×2§7 获得星币。 | D-W | forge「1 层标记和随后按当前…」与 fabric「若带「标记」，按…」是同一结算的两种写法。 |
| `astral_dice.guide.entry.candy_chip.1` | zh_cn+en_us+ja_jp | 每使用一张效果牌，+1 层治愈并恢复 1 点生命；生命值已满时，本轮出牌数 +1（当前出牌期间最多触发一次）。 | 每使用一张效果牌，+1 层治愈并恢复 1 点生命；生命值已满时，本轮出牌数 +1（每轮最多一次）。 | D-W | forge「当前出牌期间最多触发一次」vs fabric「每个出牌轮次最多触发一次」；两线 CandyChipItem 注释均为「每个效果牌出牌轮次最多触发一次」。 |
| `astral_dice.guide.entry.defense_card_epic.1` | zh_cn+en_us+ja_jp | 防御牌（特大）：骰战中提供 1~10 点防御力。 | 防御牌（特大）：骰战中提供 1~10 点防御力（单次掷取；点数越高越难命中，中位数约在范围的一半处）。 | D-W | 同 attack_card_epic.1：fabric 增补「单次掷取/中位数」说明。 |
| `astral_dice.guide.entry.defense_card_large.1` | zh_cn+en_us+ja_jp | 防御牌（大）：骰战中提供 1~6 点防御力。 | 防御牌（大）：骰战中提供 1~6 点防御力（单次掷取；点数越高越难命中，中位数约在范围的一半处）。 | D-W | 同 attack_card_epic.1：fabric 增补「单次掷取/中位数」说明。 |
| `astral_dice.guide.entry.defense_card_medium.1` | zh_cn+en_us+ja_jp | 防御牌（中）：骰战中提供 1~3 点防御力。 | 防御牌（中）：骰战中提供 1~3 点防御力（单次掷取；点数越高越难命中，中位数约在范围的一半处）。 | D-W | 同 attack_card_epic.1：fabric 增补「单次掷取/中位数」说明。 |
| `astral_dice.guide.entry.effect_card_directional_blast.1` | zh_cn+en_us | 定向爆破：瞄准敌对目标使用，延迟 0.5 秒后引爆目标，对选中目标及其周围 6 格范围内的其他目标同时造成 5 点魔法伤害（同样计入效果牌伤害加成）。 | 定向爆破：瞄准敌对目标使用，延迟 0.5 秒后引爆目标，对选中目标及其周围 6 格范围内的其他目标同时造成 5 点法术伤害（同样计入效果牌伤害加成）。 | D-W | 术语分歧：zh 魔法→法术、en magic→spell。 |
| `astral_dice.guide.entry.effect_card_living_page.2` | zh_cn+en_us | 手持此牌（主手）自动开启目标选择器（可选择 32 格内的敌对目标，无倒计时），确认后书页高速飞向目标并必定命中（可穿透方块），造成 2 + 调查员已使用页数 点魔法伤害（受全部法伤加成影响），并施加 1 层标记；移出手持即关闭、未确认不消耗卡牌。 | 手持此牌（主手）自动开启目标选择器（可选择 32 格内的敌对目标，无倒计时），确认后书页高速飞向目标并必定命中（可穿透方块），造成 2 + 调查员已使用页数 点法术伤害（受全部法伤加成影响），并施加 1 层标记；移出手持即关闭、未确认不消耗卡牌。 | D-W | 同句两处改动：zh 魔法→法术 + en「Rin has used」→「the Investigator has used」（角色名离开权威表）。 |
| `astral_dice.guide.entry.effect_card_living_page.3` | en_us | While wearing the Rin Sign, each Living Page used permanently increases its damage by +1; only when the target already had 3+ Mark layers before the hit does this card cycle grant +1 card slot (hard cap of 9 per round). | While wearing the Investigator sign, each Living Page used permanently increases its damage by +1; only when the target already had 3+ Mark layers before the hit does this card cycle grant +1 card slot (hard cap of 9 per round). | D-W | EN 角色名 Rin → Investigator，违反权威命名表（zh/ja 未变）。 |
| `astral_dice.guide.entry.effect_card_monster_brick.1` | zh_cn+en_us | 对怪板砖：瞄准敌对目标使用，向目标头顶上方投掷一块红砖（原版砖物品图标，约与僵尸头部同大），悬停片刻后竖直砸落；命中后对其造成 6 点魔法伤害（同样计入效果牌伤害加成）并产生碎裂粒子。 | 对怪板砖：瞄准敌对目标使用，向目标头顶上方投掷一块红砖（原版砖物品图标，约与僵尸头部同大），悬停片刻后竖直砸落；命中后对其造成 6 点法术伤害（同样计入效果牌伤害加成）并产生碎裂粒子。 | D-W | 术语分歧：zh 魔法→法术、en magic→spell。 |
| `astral_dice.guide.entry.effect_card_monster_laser.1` | zh_cn+en_us | 对怪激光：瞄准敌对目标使用，延迟 0.5 秒后在目标头顶生成红色激光线（无限高）并射中目标，对其造成 4 点魔法伤害（同样计入效果牌伤害加成）；激光持续 1 秒。 | 对怪激光：瞄准敌对目标使用，延迟 0.5 秒后在目标头顶生成红色激光线（无限高）并射中目标，对其造成 4 点法术伤害（同样计入效果牌伤害加成）；激光持续 1 秒。 | D-W | 术语分歧：zh 魔法→法术、en magic→spell。 |
| `astral_dice.guide.entry.effect_card_orbital_strike.1` | zh_cn+en_us | 轨道炮：瞄准敌对目标使用，延迟 0.5 秒后在目标头顶生成金色信标光束（无限高）并射中目标，对其造成 8 点魔法伤害（同样计入效果牌伤害加成）；光束持续 1 秒，命中时产生发光飞溅粒子。 | 轨道炮：瞄准敌对目标使用，延迟 0.5 秒后在目标头顶生成金色信标光束（无限高）并射中目标，对其造成 8 点法术伤害（同样计入效果牌伤害加成）；光束持续 1 秒，命中时产生发光飞溅粒子。 | D-W | 术语分歧：zh 魔法→法术、en magic→spell。 |
| `astral_dice.guide.entry.haiqing_sign.2` | en_us | Passive Skill - Lucky Star: a roll of 6 during the Dice Blessing immediately grants 6 Star Coins; when a Weak Marked target is killed, the HaiQing gains 3 Star Coins; if the killer is a player, that player gains a Fate's Guide. | Passive Skill - Lucky Star: a roll of 6 during the Dice Blessing immediately grants 6 Star Coins; when a Weak Marked target is killed, the Astrologer gains 3 Star Coins; if the killer is a player, that player gains a Fate's Guide. | D-W | zh/ja 相同，仅 EN 角色名 HaiQing → Astrologer。 |
| `astral_dice.guide.entry.komachi_sign.2` | zh_cn+en_us+ja_jp | 被动技能-复制者：使用 3 张效果牌后：复制最后一张使用的效果牌到物品栏；主动技能冷却立即减少 30%%；伤害类效果牌伤害加成 +1。 | 被动技能-复制者：每使用 3 张效果牌时：复制最后一张使用的效果牌到物品栏；主动技能冷却立即减少 30%%；伤害类效果牌伤害加成 +1。 | D-W | forge「使用 3 张效果牌后：」vs fabric「每使用 3 张效果牌时：」，同一累计触发。 |
| `astral_dice.guide.entry.lulu_sign.1` | zh_cn+en_us | 主动技能-治愈粘液：指定友方玩家或自身，向其施加「治愈粘液」：目标获得 3 层治愈并瞬间治疗；并以目标为中心：对范围内敌对生物施加缓慢 (1:00)。 | 主动技能-治愈粘液：指定友方玩家或自身，向其施加 治愈粘液：目标获得 3 层治愈并瞬间治疗；并以目标为中心：对范围内敌对生物施加缓慢 (1:00)。 | D-W | forge 带「治愈粘液」书名号并写「对目标周围 N 格」，fabric 去书名号写「对周围 N 格」；en 只是分句改并列。 |
| `astral_dice.guide.entry.magic_tome_chip.1` | zh_cn+en_us+ja_jp | 使用 3 张效果牌后，复制最后一张使用的效果牌并返回到物品栏。 | 每使用 3 张效果牌，复制最后一张使用的效果牌并返回到物品栏。 | D-W | forge「…后返还」vs fabric「每复制并返回到物品栏」，措辞差异。 |
| `astral_dice.guide.entry.megas_sign.3` | zh_cn+en_us+ja_jp | 效果「精准打击」：每层使该目标受到的轨道轰炸伤害 +1，永久持续直到目标死亡。 | 配方：金锭 ×2 + 发射器 + 下界合金块 ×2 + 空白立牌 + 黄金星盘 ×3（传奇档）。效果「精准打击」：每层使该目标受到的轨道轰炸伤害 +1，永久持续直到目标死亡。 | D-W | fabric 增补整句配方（金锭 ×2 + 发射器 + 下界合金块 ×2 + 空白立牌 + 黄金星盘 ×3），与两线 ModRecipeProvider 中完全相同的 shaped 配方一致；forge 缺该句。 |
| `astral_dice.guide.entry.moses_sign.3` | en_us | Broken effect: the target's dice can only be 0 while fighting Moses, and it is dodged and triggers a counterattack. | Broken effect: the target's dice can only be 0 while fighting the Gunsmith, and it is dodged and triggers a counterattack. | D-W | EN 角色名 Moses → Gunsmith，违反权威命名表。 |
| `astral_dice.guide.entry.nardis_sign.1` | en_us | Nardis Sign. Active - Queen's Privilege: immediately gain 3 random temporary cards lasting 3:00. Temporary cards can only be equipped and used, not dropped or transferred. All temporary cards are discarded when the timer ends. Using the active skill does not freeze; if temporary cards remain, they are resupplied and the timer resets. | Oasis Queen sign. Active - Queen's Privilege: immediately gain 3 random temporary cards lasting 3:00. Temporary cards can only be equipped and used, not dropped or transferred. All temporary cards are discarded when the timer ends. Using the active skill does not freeze; if temporary cards remain, they are resupplied and the timer resets. | D-W | EN 角色名 Nardis → Oasis Queen，违反权威命名表。 |
| `astral_dice.guide.entry.ninja_star_chip.1` | zh_cn+en_us+ja_jp | 效果牌出牌数 +1；使远程和魔法伤害额外增加目标标记层数的点数伤害。 | 效果牌出牌数 +1；对目标造成的远程和魔法伤害增加目标标记层数点。 | D-W | 语序/句式不同，数值与机制同义。 |
| `astral_dice.guide.entry.piercing_gun_chip.1` | zh_cn+en_us+ja_jp | 对目标造成远程或魔法伤害时额外增加目标防御力点数的伤害。 | 对目标造成的远程或魔法伤害额外增加目标防御力点数的伤害。 | D-W | 介词/语序差异，机制同义。 |
| `astral_dice.guide.entry.piggy_bank_chip.1` | zh_cn+en_us+ja_jp | 使用 2 张效果牌后，获得 3 星币。 | 每使用 2 张效果牌，获得 3 星币。 | D-W | 「…后」vs「每…时」，措辞差异。 |
| `astral_dice.guide.entry.playstyles.3` | en_us | Healing: healing layers form a single pool provided by Medkits, the Lulu Sign and the Buffer Shield; triggering the Dice Blessing — and every 1:00 settlement afterwards — heals you for healing layers × 2 (each settlement halves the layers first, then the Medkit tops them up). | Healing: healing layers form a single pool provided by Medkits, the Slime sign and the Buffer Shield; triggering the Dice Blessing — and every 1:00 settlement afterwards — heals you for healing layers × 2 (each settlement halves the layers first, then the Medkit tops them up). | D-W | EN 角色名 Lulu → Slime，违反权威命名表。 |
| `astral_dice.guide.entry.playstyles.4` | en_us | Marks: build around mark stacks, combined with chips like the Magic Quiver for heavy magic damage. | Marks: build around mark stacks, combined with chips like the Magic Quiver for heavy spell damage. | D-W | 术语分歧：en magic→spell。 |
| `astral_dice.guide.entry.ren_sign.1` | en_us | Ren Sign. Active "Brat's Privilege": choose a friendly player or yourself and grant them the Mouse Shield: they gain 1 random card; the Mouse Shield = 5 yellow hearts (10 absorption) + Resistance + 1 Counterattack charge, and it vanishes the moment the yellow hearts are used up. The shield is immune to a single hit larger than its yellow hearts. | Game Master Sign. Active "Brat's Privilege": choose a friendly player or yourself and grant them the Mouse Shield: they gain 1 random card; the Mouse Shield = 5 yellow hearts (10 absorption) + Resistance + 1 Counterattack charge, and it vanishes the moment the yellow hearts are used up. The shield is immune to a single hit larger than its yellow hearts. | D-W | EN 角色名 Ren → Game Master，违反权威命名表。 |
| `astral_dice.guide.entry.revenge_halberd_chip.1` | zh_cn+en_us+ja_jp | 获得以下任一效果时，攻击力 +6：虚弱、缓慢、挖掘疲劳、失明、黑暗、蓄风、盘丝、渗浆、寄生、青之诅咒。 | 获得以下任一效果时，攻击力 +6：虚弱、缓慢、挖掘疲劳、失明、黑暗、青之诅咒。 | D-W | fabric 删去 4 个状态名（蓄风/盘丝/渗浆/寄生），可触发状态清单变短。 |
| `astral_dice.guide.entry.revenge_halberd_chip.2` | zh_cn+en_us+ja_jp | 获得以下任一效果时，防御力 +6：饥饿、反胃、中毒、凋零、袭击之兆、试炼之兆、标记。 | 获得以下任一效果时，防御力 +6：饥饿、反胃、中毒、凋零、标记。 | D-W | 同上：fabric 删去「袭击之兆、试炼之兆」列举。 |
| `astral_dice.guide.entry.rin_sign.1` | zh_cn+en_us+ja_jp | 主动技能-活体书页：获得一张「活体书页」；并使自身获得「书页射程」（活体书页使用距离 +50%%，持续 2:00）。 | 主动技能-活体书页：获得一张「活体书页」；并使自身获得「书页射程」（活体书页使用射程 +50%%，持续 2:00）。 | D-W | 「使用距离」→「使用射程」，措辞统一。 |
| `astral_dice.guide.entry.spell_damage.1` | en_us | Magic damage comes from the damage effect cards: Monster Laser, Monster Brick, Orbital Strike, Directional Blast and the Living Page. | Spell damage comes from the damage effect cards: Monster Laser, Monster Brick, Orbital Strike, Directional Blast and the Living Page. | D-W | 术语分歧：en Magic → Spell。 |
| `astral_dice.guide.entry.spell_damage.2` | en_us | Magic damage is affected by damage bonuses, e.g. the Komachi Sign's "Damage Effect Card Bonus" passive grants +1 per 3 effect cards used. | Magic damage is affected by damage bonuses, e.g. the Ninja sign's "Damage Effect Card Bonus" passive grants +1 per 3 effect cards used. | D-W | EN 角色名 Komachi → Ninja（术语 magic→spell）。 |
| `astral_dice.guide.entry.spell_damage.3` | zh_cn+en_us+ja_jp | 魔法箭袋对带标记目标造成法伤时，施加 1 层标记并返还本次使用的效果牌。 | 魔法箭袋对带标记目标造成法伤时，施加 1 层标记并返还第一张使用的效果牌。 | D-W | 「本次」→「第一张」+ magic→spell，措辞调整。 |
| `astral_dice.guide.entry.spell_damage.name` | en_us | Magic Damage | Spell Damage | D-W | 术语分歧：en Magic → Spell。 |
| `astral_dice.guide.entry.teru_sign.1` | en_us | Teru Sign. Active "Divine Descent": designate a player and apply Descent to them, so you gain 50%% of that player's attack and defense power. Each time the target attacks a hostile target it triggers "Pursuit", adding extra damage equal to the caster's own attack power plus the current Fox Light stacks; attacking a new target spends 1 Fox Light stack. Descent lasts 2:00, the timer starts only after the affected player first attacks a hostile target, and it is removed when it ends. When it ends, the caster's attack and defense bonus is removed. | Cult Leader Sign. Active "Divine Descent": designate a player and apply Descent to them, so you gain 50%% of that player's attack and defense power. Each time the target attacks a hostile target it triggers "Pursuit", adding extra damage equal to the caster's own attack power plus the current Fox Light stacks; attacking a new target spends 1 Fox Light stack. Descent lasts 2:00, the timer starts only after the affected player first attacks a hostile target, and it is removed when it ends. When it ends, the caster's attack and defense bonus is removed. | D-W | EN 角色名 Teru → Cult Leader，违反权威命名表。 |
| `astral_dice.guide.entry.zhao_sign.1` | en_us | Zhao Sign. Active "Baize's Blessing": choose a player within 16 blocks or yourself and apply Baize's Blessing to them - overhealing is converted into attack power in equal amount, lasting 2:00; the timer starts only after the affected player first attacks a hostile target, and it is removed when it ends. The caster also gains 1 Blessing Talisman and converts every Misfortune Talisman they hold into Blessing Talismans. | Fengshui Master Sign. Active "Baize's Blessing": choose a player within 16 blocks or yourself and apply Baize's Blessing to them - overhealing is converted into attack power in equal amount, lasting 2:00; the timer starts only after the affected player first attacks a hostile target, and it is removed when it ends. The caster also gains 1 Blessing Talisman and converts every Misfortune Talisman they hold into Blessing Talismans. | D-W | EN 角色名 Zhao → Fengsui Master，违反权威命名表。 |
| `astral_dice.guide.entry.zhao_sign.2` | en_us | Passive "Fortune and Misfortune Intertwined": a dice result of 1 grants 1 Misfortune Talisman, a result of 6 grants 1 Blessing Talisman. Passive "Perfect Helper": applying Baize's Blessing to a player wearing the Fen Sign fills their Recharged Energy to the maximum (5 stacks). | Passive "Fortune and Misfortune Intertwined": a dice result of 1 grants 1 Misfortune Talisman, a result of 6 grants 1 Blessing Talisman. Passive "Perfect Helper": applying Baize's Blessing to a player wearing the Boss Sign fills their Recharged Energy to the maximum (5 stacks). | D-W | EN 角色名 Fen → Boss，违反权威命名表。 |
| `astral_dice.guide.entry.zhao_sign.3` | en_us | Interaction with the Fen Sign, "Hearts Linked": when the Fen Sign uses its active skill, every teammate wearing the Zhao Sign gains 1 Blessing Talisman. | Interaction with the Boss Sign, "Hearts Linked": when the Boss Sign uses its active skill, every teammate wearing the Fengshui Master Sign gains 1 Blessing Talisman. | D-W | EN 角色名 Zhao/Fen → Fengsui Master/Boss，违反权威命名表。 |
| `item.astral_dice.bonnie_sign` | en_us | Bonnie Sign | Secret Detective Sign | D-W | EN 物品名 Bonnie Sign → Secret Detective，立牌物品名须与权威表一致。 |
| `item.astral_dice.fanny_sign` | en_us | Fanny Sign | Great Detective Sign | D-W | EN 物品名 Fanny Sign → Great Detective，同上。 |
| `item.astral_dice.fen_sign` | en_us | Fen Sign | Boss Sign | D-W | EN 物品名 Fen Sign → Boss，同上。 |
| `item.astral_dice.haiqing_sign` | en_us | HaiQing Sign | Astrologer Sign | D-W | EN 物品名 HaiQing Sign → Astrologer，同上。 |
| `item.astral_dice.jasmine_sign` | en_us | Jasmine Sign | Sweeper Sign | D-W | EN 物品名 Jasmine Sign → Sweeper，同上。 |
| `item.astral_dice.komachi_sign` | en_us | Komachi Sign | Ninja Sign | D-W | EN 物品名 Komachi Sign → Ninja，同上。 |
| `item.astral_dice.lulu_sign` | en_us | Lulu Sign | Slime Sign | D-W | EN 物品名 Lulu Sign → Slime，同上。 |
| `item.astral_dice.mimi_sign` | en_us | Mimi Sign | Kanban Musume Sign | D-W | EN 物品名 Mimi Sign → Kanban Musume，同上。 |
| `item.astral_dice.misaki_sign` | en_us | Misaki Sign | Guardian Sign | D-W | EN 物品名 Misaki Sign → Guardian，同上。 |
| `item.astral_dice.moses_sign` | en_us | Moses Sign | Gunsmith Sign | D-W | EN 物品名 Moses Sign → Gunsmith，同上。 |
| `item.astral_dice.nardis_sign` | en_us | Nardis Sign | Oasis Queen's Sign | D-W | EN 物品名 Nardis Sign → Oasis Queen，同上。 |
| `item.astral_dice.papara_sign` | en_us | Papara Sign | Vampire Sign | D-W | EN 物品名 Papara Sign → Vampire，同上。 |
| `item.astral_dice.parunan_sign` | en_us | Parunan Sign | Business Sign | D-W | EN 物品名 Parunan Sign → Business，同上。 |
| `item.astral_dice.ren_sign` | en_us | Ren Sign | Game Master Sign | D-W | EN 物品名 Ren Sign → Game Master，同上。 |
| `item.astral_dice.rin_sign` | en_us | Rin Sign | Investigator Sign | D-W | EN 物品名 Rin Sign → Investigator，同上。 |
| `item.astral_dice.teru_sign` | en_us | Teru Sign | Cult Leader Sign | D-W | EN 物品名 Teru Sign → Cult Leader，同上。 |
| `item.astral_dice.zhao_sign` | en_us | Zhao Sign | Fengshui Master Sign | D-W | EN 物品名 Zhao Sign → Fengsui Master，同上。 |
| `msg.astral_dice.haiqing_ready` | en_us | HaiQing Sign: active skill activated — select a hostile target to apply the Weak Mark | Astrologer sign: active skill activated — select a hostile target to apply the Weak Mark | D-W | EN 角色名 HaiQing → Astrologer（其余同）。 |
| `msg.astral_dice.komachi_active` | en_us | Komachi Sign: effect card play count +1 granted. Remaining plays: %d | Ninja sign: effect card play count +1 granted. Remaining plays: %d | D-W | EN 角色名 Komachi → Ninja。 |
| `msg.astral_dice.mimi_active` | en_us | Mimi Sign: got %d new cards and %d Star Coins | Kanban Musume Sign: got %d new cards and %d Star Coins | D-W | EN 角色名 Mimi → Kanban Musume。 |
| `msg.astral_dice.moses_apply` | en_us | Moses Sign: Broken applied | Gunsmith sign: Broken applied | D-W | EN 角色名 Moses → Gunsmith。 |
| `msg.astral_dice.moses_ready` | en_us | Moses Sign: active skill activated — select a hostile target to apply Broken | Gunsmith sign: active skill activated — select a hostile target to apply Broken | D-W | EN 角色名 Moses → Gunsmith。 |
| `msg.astral_dice.nardis_active` | en_us | Nardis Sign: got %d temporary card(s) (lasts 3:00) | Oasis Queen's Sign: got %d temporary card(s) (lasts 3:00) | D-W | EN 角色名 Nardis → Oasis Queen。 |
| `msg.astral_dice.teru_descent_in_effect` | en_us | Teru Sign: Descent is still active and cannot be recast | Cult Leader Sign: Descent is still active and cannot be recast | D-W | EN 角色名 Teru → Cult Leader。 |
| `msg.astral_dice.teru_ready` | en_us | Teru Sign: choose another player to descend upon | Cult Leader Sign: choose another player to descend upon | D-W | EN 角色名 Teru → Cult Leader。 |
| `msg.astral_dice.zhao_blessing_ready` | en_us | Zhao Sign: choose a target (a player or yourself) | Fengshui Master Sign: choose a target (a player or yourself) | D-W | EN 角色名 Zhao → Fengsui Master。 |
| `tooltip.astral_dice.card.attack_epic` | zh_cn+en_us+ja_jp | 攻击力 §e1~10§7 \| 剩余次数：§e%s§r | 攻击力 §e1~10§7（单次掷取，点数越高越难命中） \| 剩余次数：§e%s§r | D-W | fabric 增补「（单次掷取，点数越高越难命中）」。 |
| `tooltip.astral_dice.card.attack_large` | zh_cn+en_us+ja_jp | 攻击力 §e1~6§7 \| 剩余次数：§e%s§r | 攻击力 §e1~6§7（单次掷取，点数越高越难命中） \| 剩余次数：§e%s§r | D-W | fabric 增补「（单次掷取，点数越高越难命中）」。 |
| `tooltip.astral_dice.card.attack_medium` | zh_cn+en_us+ja_jp | 攻击力 §e1~3§7 \| 剩余次数：§e%s§r | 攻击力 §e1~3§7（单次掷取，点数越高越难命中） \| 剩余次数：§e%s§r | D-W | fabric 增补「（单次掷取，点数越高越难命中）」。 |
| `tooltip.astral_dice.card.charge` | zh_cn+en_us+ja_jp | 攻击力 §e+5§7，赐福结束后返还全力攻击 \| 剩余次数：§e%s§r | 骰神赐福期间攻击力固定 §e+5§7，赐福结束后返还全力攻击 \| 剩余次数：§e%s§r | D-W | fabric 增补「骰神赐福期间固定」。 |
| `tooltip.astral_dice.card.defense_epic` | zh_cn+en_us+ja_jp | 防御力 §e1~10§7 \| 剩余次数：§e%s§r | 防御力 §e1~10§7（单次掷取，点数越高越难命中） \| 剩余次数：§e%s§r | D-W | fabric 增补「（单次掷取，点数越高越难命中）」。 |
| `tooltip.astral_dice.card.defense_large` | zh_cn+en_us+ja_jp | 防御力 §e1~6§7 \| 剩余次数：§e%s§r | 防御力 §e1~6§7（单次掷取，点数越高越难命中） \| 剩余次数：§e%s§r | D-W | fabric 增补「（单次掷取，点数越高越难命中）」。 |
| `tooltip.astral_dice.card.defense_medium` | zh_cn+en_us+ja_jp | 防御力 §e1~3§7 \| 剩余次数：§e%s§r | 防御力 §e1~3§7（单次掷取，点数越高越难命中） \| 剩余次数：§e%s§r | D-W | fabric 增补「（单次掷取，点数越高越难命中）」。 |
| `tooltip.astral_dice.card.directional_blast` | zh_cn+en_us | 瞄准敌对目标使用，延迟 §90.5§7 秒后引爆目标，对其及周围 §e6§7 格范围内的其他目标同时造成 §e%s§7 点魔法伤害 | 瞄准敌对目标使用，延迟 §90.5§7 秒后引爆目标，对其及周围 §e6§7 格范围内的其他目标同时造成 §e%s§7 点法术伤害 | D-W | 术语分歧：zh 魔法→法术、en magic→spell。 |
| `tooltip.astral_dice.card.king_power` | zh_cn+en_us+ja_jp | 受到 §e8§7 点伤害，攻击力 §e+5§7/层（最多叠 §e3§7 层），持续 §93:00§7 | 受到 §e8§7 点伤害，攻击力 §e+5§7/层（最多叠 §e3§7 层），持续 §93:00§7（重复使用叠加层数并刷新时长） | D-W | fabric 增补「（重复使用叠加层数并刷新时长）」。 |
| `tooltip.astral_dice.card.living_page` | zh_cn+en_us | 瞄准敌对目标使用，对目标造成 §e%s§7 点魔法伤害，并施加一层「标记」。命中已有 §e3§7 层及以上标记的目标时，本出牌周期出牌数 §e+1§7 | 瞄准敌对目标使用，对目标造成 §e%s§7 点法术伤害，并施加一层「标记」。命中已有 §e3§7 层及以上标记的目标时，本出牌周期出牌数 §e+1§7 | D-W | 术语分歧：zh 魔法→法术、en magic→spell。 |
| `tooltip.astral_dice.card.meito` | zh_cn+en_us+ja_jp | 攻击力 §e1~20§7 \| 剩余次数：§e%s§r | 攻击力 §e1~20§7（单次掷取，点数越高越难命中） \| 剩余次数：§e%s§r | D-W | fabric 增补「（单次掷取，点数越高越难命中）」。 |
| `tooltip.astral_dice.card.monster_brick` | zh_cn+en_us+ja_jp | 瞄准敌对目标使用，对其造成 §e%s§7 点魔法伤害 | 瞄准敌对目标使用，向其投掷一块红砖，命中后对其造成 §e%s§7 点法术伤害 | D-W | 术语分歧 + fabric 增补「向其投掷一块红砖，命中后」。 |
| `tooltip.astral_dice.card.monster_laser` | zh_cn+en_us+ja_jp | 瞄准敌对目标使用，对其造成 §e%s§7 点魔法伤害 | 瞄准敌对目标使用，延迟 §90.5§7 秒后以红色激光命中目标，对其造成 §e%s§7 点法术伤害 | D-W | 术语分歧 + fabric 增补「延迟 0.5 秒后以红色激光命中」。 |
| `tooltip.astral_dice.card.orbital_strike` | zh_cn+en_us+ja_jp | 瞄准敌对目标使用，对其造成 §e%s§7 点魔法伤害 | 瞄准敌对目标使用，延迟 §90.5§7 秒后以金色信标光束命中目标，对其造成 §e%s§7 点法术伤害 | D-W | 术语分歧 + fabric 增补「延迟 0.5 秒后以金色信标光束命中」。 |
| `tooltip.astral_dice.card.shadow_strike` | zh_cn+en_us+ja_jp | 攻击力 §e+3§7 + §9黑暗 (0:03)§7 \| 剩余次数：§e%s§r | 固定攻击力 §e+3§7 + §9黑暗 (0:03)§7 \| 剩余次数：§e%s§r | D-W | forge「攻击力 +3」vs fabric「固定攻击力 +3」，另「剩余次数」→「Uses」。 |
| `tooltip.astral_dice.chip.airbag` | zh_cn+en_us+ja_jp | 受到致命伤害时：消耗 §e6§7 层充能，使本次伤害无效<br>优先于磨刀石、不死图腾与末影骰子<br>冷却 §91:00§7<br>受到伤害时：充能 §e+1§7（冷却 §915 秒§7） | 受到致命伤害时：消耗 §e6§7 层充能，使本次伤害无效<br>优先于磨刀石、不死图腾与末影骰子<br>无视无敌的致死伤害同样有效<br>冷却 §91:00§r | D-W | fabric 增补「无视无敌的致死伤害同样有效」；forge 把该句放在指南侧（airbag_chip.1），同一信息位置不同。 |
| `tooltip.astral_dice.chip.bank_card_unlimited` | zh_cn+en_us+ja_jp | 装备时，获得 §e3§7 层星光。每次骰神赐福效果结束后，使自身及友方玩家获得 §e3§7 星币 | 装备时，获得 §e3§7 层星光。每次骰神赐福效果结束后，使自身及友方玩家（无队伍时为全体玩家）获得 §e3§7 星币 | D-W | fabric 增补「（无队伍时为全体玩家）」。 |
| `tooltip.astral_dice.chip.candy` | zh_cn+en_us+ja_jp | 每使用一张效果牌，增加 §e1§7 层治愈并恢复 §e1§7 点生命值；当生命值已满时，本轮出牌数 §e+1§7（当前出牌期间最多触发一次） | 每使用一张效果牌，增加 §e1§7 层治愈并恢复 §e1§7 点生命值；当生命值已满时，本轮出牌数 §e+1§7（每个出牌轮次最多触发一次） | D-W | 「当前出牌期间」vs「每个出牌轮次」，与 CandyChipItem 注释一致。 |
| `tooltip.astral_dice.chip.current_core` | zh_cn+en_us+ja_jp | 使用主动技能时，获得 §e1§7 层充能<br>主动技能冷却中按下主动技能键时：按剩余冷却时长占比消耗充能使主动技能立即冷却完成<br>消耗档位：按剩余冷却 ÷ §9180 秒§7 的占比分成 §e6§7 档（最低 §e1§7 层、最高 §e6§7 层） | 使用主动技能时，获得 §e1§7 层充能<br>主动技能冷却中按下主动技能键时：按剩余冷却时长占比消耗充能，并立即使主动技能冷却完成（充能不足则不生效）<br>消耗档位：剩余冷却 ÷ §9180 秒§7 的占比切成 §e6§7 档（最低 §e1§7 层、最高 §e6§7 层） | D-W | fabric 增补「（充能不足则不生效）」，并把「切成」改为「按…的占比分成」。 |
| `tooltip.astral_dice.chip.magic_tome` | zh_cn+en_us+ja_jp | 使用 §e3§7 张效果牌后，返还最后一张使用的效果牌 | 每使用 §e3§7 张效果牌，复制最后一张使用的效果牌并返回到物品栏 | D-W | 「…后返还」vs「每复制并返回到物品栏」。 |
| `tooltip.astral_dice.chip.ninja_star` | zh_cn+en_us+ja_jp | 效果牌出牌数 §e+1§7。使远程和魔法伤害额外增加目标标记层数的点数伤害 | 效果牌出牌数 §e+1§7。对目标造成的远程和魔法伤害增加目标标记层数点 | D-W | 语序差异，机制同义。 |
| `tooltip.astral_dice.chip.piercing_gun` | zh_cn+en_us+ja_jp | 对目标造成远程或魔法伤害时额外增加目标防御力点数的伤害 | 对目标造成的远程或魔法伤害额外增加目标防御力点数的伤害 | D-W | 介词差异，机制同义。 |
| `tooltip.astral_dice.chip.piggy_bank` | zh_cn+en_us+ja_jp | 使用 §e2§7 张效果牌后，获得 §e3§7 星币 | 每使用 §e2§7 张效果牌后，获得 §e3§7 星币 | D-W | 「…后」vs「每…」。 |
| `tooltip.astral_dice.chip.primordial_core` | zh_cn+en_us+ja_jp | 每消耗 §e1§7 层充能：获得 §e1§7 层赋能（上限 §e10§7 层）<br>每层赋能：攻击力 §e+1§7、防御力 §e+1§r<br>每 §90:30§7 减少 §e1§7 层 | 每消耗 §e1§7 层充能：获得 §e1§7 层赋能（上限 §e10§7 层）<br>每层赋能：攻击力 §e+1§7、防御力 §e+1§r<br>每 §90:30§7 减少 §e1§7 层赋能 | D-W | 仅「减少 1 层」→「减少 1 层赋能」（en 加 of Empowerment），效果名其余处仍为强化/Empowerment。 |
| `tooltip.astral_dice.fu_card` | zh_cn+en_us+ja_jp | 对玩家或自身使用：目标恢复 §e%s§7 点生命值<br>使用后出牌数 §e+1 | 对玩家（不限队伍）或自身使用：目标恢复 §e%s§7 点生命值<br>使用后出牌数 §e+1 | D-W | fabric 增补「（不限队伍）」。 |
| `tooltip.astral_dice.huo_card` | zh_cn+en_us+ja_jp | 只能对敌对目标使用：目标受到 §e%s§7 点伤害 | 只能对敌对目标（含非同队玩家）使用：目标受到 §e%s§7 点伤害 | D-W | fabric 增补「（含非同队玩家）」。 |
| `tooltip.astral_dice.sign.bonnie_active` | zh_cn+en_us+ja_jp | 指定一个敌对目标，向其施加 §e1§7 层标记和「隐匿调查」<br>随后按目标当前标记层数 §e×2§7 获得星币 | 指定一个敌对目标，向其施加「隐匿调查」<br>若目标带「标记」，按标记层数 §e×2§7 获得星币 | D-W | 与 guide bonnie_sign.1 同源：先给 1 层标记 vs 判标记后结算。 |
| `tooltip.astral_dice.sign.fen_xinyi` | en_us | Hearts Linked: when you use the active skill, every teammate wearing the Zhao Sign gains §e1§7 Blessing Talisman | Hearts Linked: when you use the active skill, every teammate wearing the Fengshui Master Sign gains §e1§7 Blessing Talisman | D-W | zh/ja 相同，仅 EN 角色名改动（离开权威命名表）。 |
| `tooltip.astral_dice.sign.haiqing_passive` | en_us | During Dice Blessing, rolling §e6§7 grants §e6§7 Star Coins.<br>When a Weak Mark target is killed, HaiQing gains §e3§7 Star Coins;<br>if the killer is a player, that player gains a Fate's Guide | During Dice Blessing, rolling §e6§7 grants §e6§7 Star Coins.<br>When a Weak Mark target is killed, Astrologer gains §e3§7 Star Coins;<br>if the killer is a player, that player gains a Fate's Guide | D-W | zh/ja 相同，仅 EN 角色名 HaiQing → Astrologer。 |
| `tooltip.astral_dice.sign.investigation_desc` | zh_cn+en_us+ja_jp | 调查阶段事件（由击杀「隐匿调查」目标触发，共 §e4§7 阶段，均为 §91:00§r）：<br>  I：自身与施加者获得 §9隐匿 (1:00)§r<br>  II：隐匿 + 非boss敌对攻击力 §e+2§7，持续 §91:00§r<br>  III：隐匿 + 非boss敌对攻击力 §e+(2+标记层数)§7，持续 §91:00§r<br>  真相揭露：自身+施加者+同队（无队伍时全体玩家）获得隐匿，非boss攻击 §e+(2+标记层数)§7，boss攻击 §e+(2+标记层数×2)§7，持续 §91:00§r | 调查阶段事件（由击杀「隐匿调查」目标触发，共 §e4§7 阶段，均为 §91:00§r）：<br>  I：自身与施加者获得 §9隐匿 (1:00)§r<br>  II：隐匿 + 非boss敌对攻击力 §e+2§7，持续 §91:00§r<br>  III：隐匿 + 非boss敌对攻击力 §e+(2+标记层数)§7，持续 §91:00§r<br>  真相揭露：自身+施加者+同队（无队伍时全体玩家）获得隐匿，非boss攻击 §e+(2+标记层数)§7，boss攻击 §e+(2+标记层数×2)§7，持续 §91:00§r<br>  隐匿期间生物无法索敌该玩家；攻击怪物会解除隐匿，并在骰战中追加目标「标记」层数的伤害 | D-W | fabric 增补末句「隐匿期间生物无法索敌该玩家；攻击怪物会解除隐匿，并在骰战中追加目标「标记」层数的伤害」。 |
| `tooltip.astral_dice.sign.komachi_passive` | zh_cn+en_us+ja_jp | 使用 §e3§7 张效果牌后：<br>  复制最后一张使用的效果牌到物品栏<br>  主动技能冷却时间立即减少 §e30%%§r<br>  伤害类效果牌伤害加成 §e+1§r | 每使用 §e3§7 张效果牌时：<br>  复制最后一张使用的效果牌到物品栏<br>  主动技能冷却时间立即减少 §e30%%§r<br>  伤害类效果牌伤害加成 §e+1§r | D-W | 「使用 3 张后」vs「每使用 3 张时」。 |
| `tooltip.astral_dice.sign.lulu_active` | zh_cn+en_us+ja_jp | 指定友方玩家或自身，向其施加「治愈粘液」：目标获得 §e3§7 层治愈 + 瞬间治疗；<br>对目标周围 §e%s§7 格内敌对生物施加 §9缓慢 (1:00)§r | 指定友方玩家或自身，向其施加 治愈粘液：目标获得 §e3§7 层治愈 + 瞬间治疗；<br>对周围 §e%s§7 格内敌对生物施加 §9缓慢 (1:00)§r | D-W | forge 带「治愈粘液」书名号并写「对目标周围」，fabric 去书名号写「对周围」；en 另补「Instant Health I」等级。 |
| `tooltip.astral_dice.sign.padman_passive` | zh_cn+en_us+ja_jp | 每 §91:00§7 使基础攻击/防御点数变动（§e-2~4§7 点）<br>骰神赐福期间骰点为 §e6§7 时无视目标防御力<br>骰神赐福期间骰点为 §e1§7 时下次骰点必为 §e6§r | 每 §91:00§7 使基础攻击/防御点数变动（§e-2~4§7 点）<br>骰神赐福期间骰点为 §e6§7 时无视目标防御力（含护甲值）<br>骰神赐福期间骰点为 §e1§7 时下次骰点必为 §e6§r | D-W | fabric 在「骰点 6 无视目标防御力」后增补「（含护甲值）」。 |
| `tooltip.astral_dice.sign.rin_active` | zh_cn+ja_jp | 获得一张「活体书页」<br>若玩家物品栏中没有「活体书页」，则额外获得一张<br>活体书页使用距离 §e+50%%§7，持续 §92:00§7 | 获得一张「活体书页」<br>若玩家物品栏中没有「活体书页」，则额外获得一张<br>活体书页使用射程 §e+50%%§7，持续 §92:00§7 | D-W | 「使用距离」→「使用射程」。 |
| `tooltip.astral_dice.sign.zhao_wanmei_bangshou` | en_us | Perfect Helper: applying Baize's Blessing to a player wearing the Fen Sign fills that player's Recharged Energy to the maximum (§e5§7 stacks) | Perfect Helper: applying Baize's Blessing to a player wearing the Boss Sign fills that player's Recharged Energy to the maximum (§e5§7 stacks) | D-W | zh/ja 相同，仅 EN 角色名改动（离开权威命名表）。 |

### 2.6 C 已裁决裁剪（1 条）

| 键名 | 语 | forge 值 | fabric 值 | 分类 | 判定依据 |
|---|---|---|---|---|---|
| `astral_dice.guide.entry.misaki_sign.3` | zh_cn+en_us+ja_jp | 神秘遗物+联动：佩戴七咒之戒，处于「爆发」状态（由主动技能触发，持续 2:00）时，修正第四诅咒——总能造成全额伤害。 | 神秘遗物+联动：佩戴七咒之戒并处于「爆发」状态时，修正第四诅咒——总能造成全额伤害。 | C | 整条是 enigmaticlegacy「七咒之戒」联动：forge 写「佩戴七咒之戒，处于「爆发」状态（由主动技能触发，持续 2:00）」，fabric 删去括号内触发/时长说明；属既定不移植联动内容的裁剪。 |

### 2.7 P 平台命名差异（0 条）

见 2.1：平台命名键为 fabric 独有键，不构成「共有键取值不同」，159 条内 0 条命中。平台键清单一并记录，供后续键集核对使用：

```
accessories.slot.chip
accessories.slot.dice
accessories.slot.stand
accessories.slot_group.astral_dice
astral_dice.guide.entry.hanna_sign.3
astral_dice.guide.entry.nardis_sign.3
astral_dice.guide.entry.sherry_sign.3
astral_dice.guide.entry.teru_sign.3
msg.astral_dice.fanny_event
msg.astral_dice.fanny_event.1
msg.astral_dice.fanny_event.10
msg.astral_dice.fanny_event.11
msg.astral_dice.fanny_event.2
msg.astral_dice.fanny_event.3
msg.astral_dice.fanny_event.4
msg.astral_dice.fanny_event.5
msg.astral_dice.fanny_event.6
msg.astral_dice.fanny_event.7
msg.astral_dice.fanny_event.8
msg.astral_dice.fanny_event.9
msg.astral_dice.parunan_active_no_starlight
trinkets.slot.astral_dice.chip
trinkets.slot.astral_dice.dice
trinkets.slot.astral_dice.stand
```

### 2.8 U 无法判定（0 条）

159 条逐一给出判定依据后，没有剩余证据不足的条目：数值/机制类均落到 java 源码常量、datagen 配方或自身注释；措辞类可由同一道具在两线的完整文案对照读出。

## 3. 并发写入说明（必读）

本批执行期间检测到另一执行者对同一组三个文件写入了改动：

| 项 | 我的改动后状态 | 当前状态 |
|---|---|---|
| 键数 | 1095 | 1094（平台键被删，属 B23 收尾，合理） |
| `effect.astral_dice.concealment` 出现次数 | 1 | 1（本批去重结果保留完好） |
| 行尾 / 编码 | CRLF / UTF-8 无 BOM | CRLF（bareLF=0）/ UTF-8 无 BOM |

处置：不覆盖、不回滚对方的写入（红线：不与他人的改动冲突），仅在 1.3 记录其对本批验收数值的影响。第二部分审计所用 159 条取值与写入前逐字一致（已重新全量比对），审计结论不受影响。

## 4. D 组建议优先修的 5 条

| # | 键（相关键一并） | 冲突点 | 依据 |
|---|---|---|---|
| 1 | `tooltip.astral_dice.chip.warp_engine` + `astral_dice.guide.entry.warp_engine_chip.1` | 传送门/Waystone 共享冷却 3:00 vs 5:00 | `forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/chip/WarpEngineChipItem.java:40` `PORTAL_WAYSTONE_COOLDOWN_TICKS = 20 * 60 * 3`（注释「2026-10-15 用户裁决:5:00 → 3:00」）vs fabric 同文件 `:38` `= 20 * 60 * 5` ⇒ fabric 代码与文案都未跟进裁决 |
| 2 | `tooltip.astral_dice.chip.bookmark` + `astral_dice.guide.entry.bookmark_chip.1` | 伤害加成 +2 vs +1 | `forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/chip/BookmarkChipItem.java:17` `DAMAGE_BONUS = 2`（注释「2026-10-15 用户裁决:+1 → +2」）vs fabric `:17` `= 1` |
| 3 | `tooltip.astral_dice.sign.mimi_passive` + `astral_dice.guide.entry.mimi_sign.2` | 返还阈值 100 张 vs 25 张 | 两线 `item/sign/MimiSignItem.java` 均为 `RETURNED_CARD_THRESHOLD = 100` ⇒ fabric 文案 25 与**自身代码**直接矛盾 |
| 4 | `tooltip.astral_dice.sign.nancy_lu_active` + `msg.astral_dice.nancy_lu_active` + `astral_dice.guide.entry.nancy_lu_sign.1` | 主动技能：固定 +6 攻击力 vs 消耗随机战斗牌按费用 ×2（最低 +2） | 两线代码已分叉：forge `NancyLuSignItem.java:55` `ACTIVE_ATTACK_BONUS = 6`；fabric `:60-61` `ACTIVE_BONUS_MULTIPLIER = 2` / `ACTIVE_MIN_BONUS = 2` ⇒ 需产品裁决，不是文案笔误 |
| 5 | `astral_dice.guide.entry.battle_dice.3` + `diamond_dice.1` / `golden_dice.1` / `netherite_dice.1` | 卡牌栏格数由「骰子品质」还是「星级」决定（品质 4/8/10/12 且与星级无关 vs 星级 4/6/8/12）；费用上限 3/4/5/6 仅 forge 有 | 同一玩家可见规则在两线给出互斥数值，且 fabric 侧删掉了费用上限说明 ⇒ 玩家可感知的错误信息 |

