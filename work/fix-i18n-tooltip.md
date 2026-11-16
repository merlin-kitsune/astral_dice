# 1.5.0 本地化 / tooltip 渲染修复报告（四线 neoforge-1.21.1 / forge-1.20.1 / neoforge-26.1.2 / fabric-1.20.1）

> 全部行号为**改后工作区行号**。`改前` 值取自动手时的工作区实读（这 16 个目标文件在本次改动前已是未提交修改状态，因此不等于 `git HEAD`）。

## 0. 落盘范围与环境事实

| 项 | 实测 |
|---|---|
| 改动文件 | 16 个：4 × `event/ModTooltipHandler.java` + 4 × 3 份 lang |
| git status（我的范围） | 仅这 16 个文件为 `M`，无新增/删除文件，未 `add`/`commit`/`stash` |
| 行尾 | neo21 / fabric 的 java = CRLF；forge / neo26 的 java = LF；fabric 的 3 份 lang = CRLF（1151 行 CRLF）；neo21 / forge / neo26 的 9 份 lang = LF（CRLF=0）；12 份 lang 均以换行结尾 |
| 写入方式 | 逐行插入 / 逐行字面替换 + 以文件自身 EOL 拼接；**未使用 `json.dump`**，未重排键序 |
| 他人改动 | fabric 工作区有别人未完成的改动（含 19 个 HEAD 键已被其删除：`effect.astral_dice.railgun_ready`、`msg.astral_dice.fanny_event[.1~.11]`、`tooltip.astral_dice.weird_dice_cooldown`、`msg.astral_dice.parunan_active_no_starlight`、`astral_dice.guide.entry.{teru,nardis,sherry,hanna}_sign.3`），**未触碰、未回滚**；另有 `tools/verify_sign_display_names.py` 的既有工作树改动，同样未碰 |

## A. 立牌技能名硬编码中文（已修，四线）

### A.1 Java 改动（helper 改为收 lang 键名）

四线 helper 定义逐字相同，两个 helper 一起改（`addSignActiveTitle` / `addSignPassiveTitle`）：

```java
// 改前（helper 只收成品字符串 ⇒ 调用点只能塞中文字面量）
private static void addSignActiveTitle(List<Component> tooltip, String skillName) {
    tooltip.add(tt("tooltip.astral_dice.sign.active_title", skillName).withStyle(ChatFormatting.GOLD));
}

// 改后（收键名，helper 内部二次取键）
private static void addSignActiveTitle(List<Component> tooltip, String skillKey) {
    tooltip.add(tt("tooltip.astral_dice.sign.active_title", translationString(skillKey)).withStyle(ChatFormatting.GOLD));
}
```

调用点示例（31 个立牌逐个改）：

```java
// 改前  addSignActiveTitle(tooltip, "网络购物");
// 改后  addSignActiveTitle(tooltip, "tooltip.astral_dice.sign.parunan.active");
```

| 线 | 文件 | active helper 行 | passive helper 行 | 调用点改写数 | 首个调用点 | 末个调用点 |
|---|---|---|---|---|---|---|
| neoforge-1.21.1 | `neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/event/ModTooltipHandler.java` | 87 | 93 | 62（31 主动 + 31 被动） | 669 | 1704 |
| forge-1.20.1 | `forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/ModTooltipHandler.java` | 87 | 93 | 62 | 689 | 1680 |
| neoforge-26.1.2 | `neoforge-26.1.2/src/main/java/com/merlinkitsune/astral_dice/event/ModTooltipHandler.java` | 163 | 169 | 62 | 742 | 1755 |
| fabric-1.20.1 | `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/ModTooltipHandler.java` | 87 | 93 | 48（24 + 24，本线只渲染 24 个立牌） | 670 | 1520 |

依据：玩家可见缺陷（英/日玩家看到「Active Skill (套现):」中英混排）；helper 只收字符串，故按最小改动把形参语义改成「键名」，内部用同文件既有的 `translationString(...)` + `tt(...)` 取译；两条 helper 头注释同步补「技能名取自 lang 键,禁止硬编码中文」（neo21/forge/fabric:86、neo26:162）。

### A.2 新增键与键值（`tooltip.astral_dice.sign.<id>.active|passive`，每线每语 62 键）

| 立牌 id | 后缀 | zh（neo21 = forge） | en（neo21；neo26 差异见备注） | ja（neo21；neo26 差异见备注） | 备注 |
|---|---|---|---|---|---|
| parunan | active | 网络购物 | Online Shopping | ネット通販 | zh 跨线不同：neo21/forge = `网络购物`；neo26 = `套现`；fabric = `套现`；en：25 = `Online Shopping` / 26 = `Cash Out`；ja：25 = `ネット通販` / 26 = `現金化` |
| parunan | passive | 传奇商人 | Legendary Merchant | 伝説の商人 |  |
| jasmine | active | 能量过载 | Energy Overload | エネルギー過負荷 |  |
| jasmine | passive | 移动充能 | Motion Charge | 移動充電 |  |
| misaki | active | 樱花裂空斩 | Sakura Sky Slash | 桜花裂空斬 |  |
| misaki | passive | 剑气 | Sword Qi | 剣気 |  |
| mimi | active | 商品补货 | Restock | 商品補充 |  |
| mimi | passive | 过期回收 | Recycle | 期限切れ回収 |  |
| lulu | active | 治愈粘液 | Healing Slime | 治癒スライム |  |
| lulu | passive | 细胞分裂 | Cell Division | 細胞分裂 |  |
| komachi | active | 忍术连击 | Ninja Combo | 忍術連撃 |  |
| komachi | passive | 复制者 | Duplicator | 複製者 |  |
| padman | active | 真的生气了 | Really Angry | 本当に怒った |  |
| padman | passive | 毫无主见 | No Opinion | 主体性なし |  |
| fanny | active | 事件选择 | Event Choice | イベント選択 | zh 跨线不同：neo21/forge = `事件选择`；neo26 = `麻烦制造者`；fabric = `麻烦制造者`；en：25 = `Event Choice` / 26 = `Trouble Maker`；ja：25 = `イベント選択` / 26 = `トラブルメーカー` |
| fanny | passive | 华点发现 | Spot the Highlight | 華点の発見 |  |
| rin | active | 活体书页 | Living Page | 生けるページ |  |
| rin | passive | 调查发现 | Investigation Findings | 調査の発見 |  |
| haiqing | active | 虚弱印记 | Weakness Mark | 弱体の印 |  |
| haiqing | passive | 幸运星 | Lucky Star | 幸運の星 |  |
| papara | active | 嘬你一口 | Take a Bite | 一口ちょうだい |  |
| papara | passive | 可爱即正义 | Cuteness Is Justice | 可愛いは正義 |  |
| bonnie | active | 隐匿行动 | Covert Operation | 隠密行動 |  |
| bonnie | passive | 关键线索 | Key Clue | 重要な手掛かり |  |
| fen | active | 运功 | Channel Qi | 運功 |  |
| fen | passive | 养精蓄锐 | Recharged Energy | 充填エネルギー |  |
| nancy_lu | active | 远程侵入 | Remote Intrusion | 遠隔侵入 |  |
| nancy_lu | passive | 网络防火墙 | Network Firewall | ネットワークファイアウォール |  |
| moses | active | 弱点反击 | Weak Counter | 弱点反撃 |  |
| moses | passive | 精密技巧 | Precision | 精密技術 |  |
| pandaman | active | 大吃特吃 | Devour | 大食い |  |
| pandaman | passive | 有好有坏 | Give and Take | 良し悪し |  |
| ren | active | 熊孩子特权 | Brat's Privilege | わんぱく特権 |  |
| ren | passive | 鼠鼠救我 | Mouse Rescue | ネズミの救出 |  |
| zhao | active | 白泽赐福 | Baize's Blessing | 白澤の加護 |  |
| zhao | passive | 福祸相倚 | Fortune and Misfortune Intertwined | 福禍は隣り合わせ |  |
| teru | active | 降神 | Divine Descent | 降神 |  |
| teru | passive | 狐光 | Fox Light | 狐光 |  |
| hanna | active | 漂浮魔法 | Float Magic | 浮遊魔法 |  |
| hanna | passive | 幻想千金 | Fantasy Heiress | 幻想の令嬢 |  |
| sherry | active | 怪力投掷 | Strong-arm Throw | 怪力投げ |  |
| sherry | passive | 侦探出击 | Detective's Strike | 探偵の出撃 |  |
| kangel | active | 网络天使 | Network Angel | ネットエンジェル |  |
| kangel | passive | 小天使请安 | Little Angel's Greeting | 小さな天使のご挨拶 |  |
| ame | active | 感情过量摄入 | Emotional Overdose | 感情過剰摂取 |  |
| ame | passive | 无法承受的爱 | Unbearable Love | 耐えられない愛 |  |
| nardis | active | 女王特权 | Queen's Privilege | 女王の特権 |  |
| nardis | passive | 威压 | Intimidation | 威圧 |  |
| mamushi | active | 连锁反应 | Chain Reaction | 連鎖反応 |  |
| mamushi | passive | 湖沼之王 | King of the Marsh | 湖沼の王 |  |
| megas | active | 轨道轰炸 | Orbital Bombardment | 軌道爆撃 |  |
| megas | passive | 物资补充 | Resupply | 物資補給 |  |
| lingling | active | 能量突击 | Energy Surge | エネルギー突撃 |  |
| lingling | passive | 拦截者 | Interceptor | インターセプター |  |
| jill | active | 调制人生 | Mix Life | 調合人生 |  |
| jill | passive | 改变生活 | Change Life | 暮らしを変える |  |
| dorothy | active | 真正的我 | True Me | 本当の私 |  |
| dorothy | passive | 交际花 | Socialite | 社交家 |  |
| alana | active | 铁处女 | Iron Maiden | アイアンメイデン |  |
| alana | passive | 鼓起勇气 | Courage | 勇気 |  |
| deepseek | active | 大口干饭 | Big Feast | 大食い |  |
| deepseek | passive | 摸鱼中 | Slacking | サボり中 |  |

### A.3 四线新增键计数对照

| 线 | A 键 | B 键 | 合计新增/语 | 三语各文件键数（zh/en/ja） | 与改前键数 |
|---|---|---|---|---|---|
| neoforge-1.21.1 | 62 | 10 | 72 | 1142 / 1142 / 1142 | 1070 → 1142 |
| forge-1.20.1 | 62 | 10 | 72 | 1142 / 1142 / 1142 | 1070 → 1142 |
| neoforge-26.1.2 | 62 | 10 | 72 | 1166 / 1166 / 1166 | 1094 → 1166 |
| fabric-1.20.1 | 62 | 10 | 72 | 1149 / 1149 / 1149 | 846 → 1149（其中 231 键是他人未提交改动，不属于本次） |

### A.4 静态自检

- 四线「把 CJK 中文字面量传给 `addSign*Title`」的调用点 = **0**（正则扫 `addSign(Active|Passive)Title\(tooltip, "[^\x00-\x7F]`）；
- 四线「实参含 CJK 的 `tooltip.add(Component.literal(...))`」代码行 = **0**（仅剩行 60 / 136 / 182 一类注释文本）；
- 四线三语新增键数一致（每线每语 72；fabric 虽只渲染 24 个立牌，lang 仍补满 31 × 2 以满足三语/四线一致性）；
- 键序：A 块严格连续插在 `"tooltip.astral_dice.sign.passive_title"` 之后、按 IDS 顺序 `.active`/`.passive` 成对；断言 `A_contig=True`（四线三语全部 12 份）。

## B. 骰面石 tooltip 手写中文（已修，四线）

现象：`tooltip.add(Component.literal(" §7- §5暗影突袭 ..."))` 直接写中文骰面名，英/日玩家看到中文。

改法：抽出 10 个三语键 `tooltip.astral_dice.dice_face.<face>`，调用点改 `tt(...)`（`tt` 走 `translationString(...)`，能正确处理值里的 `%%`；骰面文案含 `50%%`，按规则必须走 `tt` 而不是 `Component.translatable`）。染色与占位符结构原样保留，`.withStyle(ChatFormatting.GRAY)` 那一行未动。

| 行号（neo21/forge/fabric 同为 395/400/405/410/415/420/425；neo26 为 468/473/478/483/488/493/498） | 键 | 改前（字面量节选） | 改后 |
|---|---|---|---|
| 395 / 468 | `dice_face.shadow_strike` | `Component.literal(" §7- §5暗影突袭 §e+3§7 固定 §7| §9黑暗(3秒)§7 §7[剩余:§e" + stone.uses() + "§7]")` | `tt("tooltip.astral_dice.dice_face.shadow_strike", stone.uses())` |
| 400 / 473 | `dice_face.meito` | `Component.literal(" §7- §d名刀·噶呜切 §e1~20§7 攻击 §7[剩余:§e" + stone.uses() + "§7]")` | `tt("tooltip.astral_dice.dice_face.meito", stone.uses())` |
| 405 / 478 | `dice_face.charge` | `Component.literal(" §7- §e蓄力 §e+5§7 固定(赐福期间) §7| 赐福结束后返还§c全力攻击")` | `tt("tooltip.astral_dice.dice_face.charge")` |
| 410 / 483 | `dice_face.full_power` | `Component.literal(" §7- §c全力攻击 §e+6§7 攻击力 本次攻击的最终攻击力§e+50%§7 §7[剩余:§e" + stone.uses() + "§7]")` | `tt("tooltip.astral_dice.dice_face.full_power", stone.uses())` |
| 415 / 488 | `dice_face.defense_medium` | `Component.literal(" §7- §b中 §e1~3§7 防御 §7[剩余:§e" + stone.uses() + "§7]")` | `tt("tooltip.astral_dice.dice_face.defense_medium", stone.uses())` |
| 420 / 493 | `dice_face.defense_large` | `Component.literal(" §7- §d大 §e1~6§7 防御 §7[剩余:§e" + stone.uses() + "§7]")` | `tt("tooltip.astral_dice.dice_face.defense_large", stone.uses())` |
| 425 / 498 | `dice_face.defense_epic` | `…" §7- §6特大 §e1~10§7 防御 …"` | `tt("tooltip.astral_dice.dice_face.defense_epic", stone.uses())` |

未知 / 兜底分支（neo21/forge/fabric 原 429-442、neo26 原 502-515）——原来先 `switch` 出中文 `stoneName` 再拼串，改为：

```java
// 改后（保留原 range switch；名字走键，未知类型只打原始 id 与剩余次数，不再出现中文）
String range = switch (stone.type()) { case "medium" -> "1~3"; case "large" -> "1~6"; case "epic" -> "1~10"; default -> "?"; };
String rangeKey = switch (stone.type()) { case "medium", "large", "epic" -> "tooltip.astral_dice.dice_face." + stone.type(); default -> null; };
if (rangeKey != null) {
    tooltip.add(tt(rangeKey, range, stone.uses()).withStyle(ChatFormatting.GRAY));
} else {
    tooltip.add(Component.literal(" §7- §7" + stone.type() + " §7[§e" + stone.uses() + "§7]").withStyle(ChatFormatting.GRAY));
}
```

10 个键的键值（neo21/forge/neo26/fabric 四线一致；en/ja 见下表，%s 个数与 zh 相同）：

| face | zh | en | ja |
|---|---|---|---|
| shadow_strike | ` §7- §5暗影突袭 §e+3§7 固定 §7| §9黑暗(3秒)§7 §7[剩余:§e%s§7]` | ` §7- §5Shadow Strike §e+3§7 fixed §7| §9Darkness (0:03)§7 §7[Uses:§e%s§7]` | ` §7- §5シャドウストライク §e+3§7 固定 §7| §9暗闇 (0:03)§7 §7[残り:§e%s§7]` |
| meito | ` §7- §d名刀·噶呜切 §e1~20§7 攻击 §7[剩余:§e%s§7]` | ` §7- §dMeito: Gawu Cut §e1~20§7 attack §7[Uses:§e%s§7]` | ` §7- §d名刀・ガウ切 §e1~20§7 攻撃 §7[残り:§e%s§7]` |
| charge | ` §7- §e蓄力 §e+5§7 固定(赐福期间) §7| 赐福结束后返还§c全力攻击` | ` §7- §eCharge §e+5§7 fixed (during blessing) §7| when the blessing ends, returns §cFull Power` | ` §7- §eチャージ §e+5§7 固定（加護期間） §7| 加護終了時に§c全力攻撃を返却` |
| full_power | ` §7- §c全力攻击 §e+6§7 攻击力 本次攻击的最终攻击力§e+50%%§7 §7[剩余:§e%s§7]` | ` §7- §cFull Power Attack §e+6§7 attack; this attack's final attack power §e+50%%§7 §7[Uses:§e%s§7]` | ` §7- §c全力攻撃 §e+6§7 攻撃力、今回の攻撃の最終攻撃力 §e+50%%§7 §7[残り:§e%s§7]` |
| defense_medium | ` §7- §b中 §e1~3§7 防御 §7[剩余:§e%s§7]` | ` §7- §bMedium §e1~3§7 defense §7[Uses:§e%s§7]` | ` §7- §b中 §e1~3§7 防御 §7[残り:§e%s§7]` |
| defense_large | ` §7- §d大 §e1~6§7 防御 §7[剩余:§e%s§7]` | ` §7- §dLarge §e1~6§7 defense §7[Uses:§e%s§7]` | ` §7- §d大 §e1~6§7 防御 §7[残り:§e%s§7]` |
| defense_epic | ` §7- §6特大 §e1~10§7 防御 §7[剩余:§e%s§7]` | ` §7- §6XL §e1~10§7 defense §7[Uses:§e%s§7]` | ` §7- §6特大 §e1~10§7 防御 §7[残り:§e%s§7]` |
| medium | ` §7- §b中 §e%s§7 攻击 §7[剩余:§e%s§7]` | ` §7- §bMedium §e%s§7 attack §7[Uses:§e%s§7]` | ` §7- §b中 §e%s§7 攻撃 §7[残り:§e%s§7]` |
| large | ` §7- §d大 §e%s§7 攻击 §7[剩余:§e%s§7]` | ` §7- §dLarge §e%s§7 attack §7[Uses:§e%s§7]` | ` §7- §d大 §e%s§7 攻撃 §7[残り:§e%s§7]` |
| epic | ` §7- §6特大 §e%s§7 攻击 §7[剩余:§e%s§7]` | ` §7- §6XL §e%s§7 attack §7[Uses:§e%s§7]` | ` §7- §6特大 §e%s§7 攻撃 §7[残り:§e%s§7]` |

依据：玩家可见缺陷；zh 逐字保留原字面量（仅删掉拼串外壳）；en/ja 沿用仓库既有骰面词表（`item.astral_dice.attack_card_*` / `defense_card_*` 与手册同名，如 Shadow Strike / Meito: Gawu Cut / Charge / Full Power Attack / Defense Card (M)(L)(XL)）。

自检：四线「实参含 CJK 的 `tooltip.add(Component.literal(...))` 代码行」= 0；`dice_face` 键命中的行内 10 键 × 4 线 × 3 语全部存在且非空。

## C. `防御 Cost: %s / %s` 中英混排 + 日语未译（已修，四线）

| 键 | 线 | 行号（zh/en/ja 同位置或差分 1 行） | 改前 zh | 改后 zh | 改前 en | 改后 en | 改前 ja | 改后 ja |
|---|---|---|---|---|---|---|---|---|
| `gui.astral_dice.defense_cost` | neo21/forge | 305（ja 304） | `防御 Cost: %s / %s` | `防御花费: %s / %s` | `Defense Cost: %s / %s` | 不变 | `防御 Cost: %s / %s` | `防御コスト: %s / %s` |
| `gui.astral_dice.defense_cost` | neo26 | 260 | `防御 Cost: %s / %s` | `防御花费: %s / %s` | `Defense Cost: %s / %s` | 不变 | `防御 Cost: %s / %s` | `防御コスト: %s / %s` |
| `gui.astral_dice.defense_cost` | fabric | 275 | `防御 Cost: %s / %s` | `防御花费: %s / %s` | `Defense Cost: %s / %s` | 不变 | `防御 Cost: %s / %s` | `防御コスト: %s / %s` |
| `tooltip.astral_dice.defense_cost` | neo21/forge | 661（ja 660） | `防御 §e%s§r` | `防御花费 §e%s§r` | `Defense §e%s§r` | `Defense Cost §e%s§r` | `防御 §e%s§r` | `防御コスト §e%s§r` |
| `tooltip.astral_dice.defense_cost` | neo26 | 536 | `防御 §e%s§r` | `防御花费 §e%s§r` | `Defense §e%s§r` | `Defense Cost §e%s§r` | `防御 §e%s§r` | `防御コスト §e%s§r` |
| `tooltip.astral_dice.defense_cost` | fabric | 594 | `防御 §e%s§r` | `防御花费 §e%s§r` | `Defense §e%s§r` | `Defense Cost §e%s§r` | `防御 §e%s§r` | `防御コスト §e%s§r` |

依据：C 项裁决（中文「防御花费」/ 英文「Defense Cost」/ 日文「防御コスト」）；`%s` 个数、`§e`/`§r` 结构与位置一字未动。四线三语共 6 个值 × 4 线 = 24 个值全部落到位（en 的 `gui` 值本就合规，未改）。

## D. neo26 `tooltip.astral_dice.sign.fanny_active` 时长被写进红色段

范围：**只有 neoforge-26.1.2** 有这条长文案（其余三线该键是「在2个全体事件中选择1个全体事件并触发」短句，无时间，无需改）。位置：neo26 三份 lang 第 568 行。

改前 / 改后（三语各 **7 对**，任务描述写「4 处」是误数——是 4 行、其中两行各含 2 个效果）：

| 语 | 改前 | 改后 |
|---|---|---|
| zh | `获得 §c中毒 (0:15)§r` / `§c饥饿 (0:30)§7 和 §c反胃 (0:07)§r` / `§c凋灵 (0:07)§7 和 §c黑暗 (0:05)§r` / `§c虚弱 (0:15)§7 和 §c挖掘疲劳 (0:30)§r` | `获得 §c中毒§r §9(0:15)§r` / `§c饥饿§r §9(0:30)§7 和 §c反胃§r §9(0:07)§r` / `§c凋灵§r §9(0:07)§7 和 §c黑暗§r §9(0:05)§r` / `§c虚弱§r §9(0:15)§7 和 §c挖掘疲劳§r §9(0:30)§r` |
| en | `§cPoison (0:15)§r` / `§cHunger (0:30)§7 + §cNausea (0:07)§r` / `§cWither (0:07)§7 + §cDarkness (0:05)§r` / `§cWeakness (0:15)§7 + §cMining Fatigue (0:30)§r` | `§cPoison§r §9(0:15)§r` / `§cHunger§r §9(0:30)§7 + §cNausea§r §9(0:07)§r` / `§cWither§r §9(0:07)§7 + §cDarkness§r §9(0:05)§r` / `§cWeakness§r §9(0:15)§7 + §cMining Fatigue§r §9(0:30)§r` |
| ja | `§c毒 (0:15)§r` / `§c空腹 (0:30)§7 と §c吐き気 (0:07)§r` / `§c衰弱 (0:07)§7 と §c暗闇 (0:05)§r` / `§c弱体化 (0:15)§7 と §c採掘速度低下 (0:30)§r` | `§c毒§r §9(0:15)§r` / `§c空腹§r §9(0:30)§7 と §c吐き気§r §9(0:07)§r` / `§c衰弱§r §9(0:07)§7 と §c暗闇§r §9(0:05)§r` / `§c弱体化§r §9(0:15)§7 と §c採掘速度低下§r §9(0:30)§r` |

未动：正面的 `§9再生能力 (0:30)§r` 等已合规；`§c即時ダメージ§r` / `§c瞬间伤害§r` 无时间。全库 12 份再扫「`§c` 段内含时间」= **0** 段（改前仅这一行）。

实现细节：正则只作用于该键那一行，命中数断言 = 7（zh/ja）与 7（en，其中 `Mining Fatigue` 名字含空格，单独补一次），随后统一复扫确认没有残留「名字 + 空格 + `§r`」的双空格形态（首轮曾产生 `§c中毒 §r §9(...)`，已修成裁决要求的 `§c中毒§r §9(...)`）。


### D.1 ⚠ 与本仓规则文档 / 审计脚本的冲突（**需要产品裁决，我没有擅自放宽判据**）

**硬数字（同一台机器、同一脚本 `scripts/audit/tooltip_color_audit.ps1`，只差 fanny_active 这一个键的形态）**：

| 树 | 命令 | 总违规 | `fanny_active` 命中行 | R2（时间未着蓝） | R3（名与时间整段同色） |
|---|---|---|---|---|---|
| 改前基线（fanny 还原成 `§c中毒 (0:15)§r`） | `pwsh -File scripts/audit/tooltip_color_audit.ps1 -Root F:\tmp\auditbase` | 230 | 18（全为既有 R1） | **0** | **0** |
| 当前工作区（D 裁决形态） | `pwsh -File scripts/audit/tooltip_color_audit.ps1` | 251 | 39（18 R1 + 21 R3） | **0** | **21** |

即：**改前形态本来就不是 R2 违例**（`§c` 段内的时间被 `Test-ExemptRun` 按「§c 红色条目豁免」放行），也不是 R3 违例（名与时间同色，都是 `§c`）；按 D 裁决换成「名红 + 时间蓝」后，R2 仍为 0，但 R3 增加了 21 行（4 线无关，只有 neo26 三语 × 7 对）。

**互斥的根源**（读脚本即可证明，`scripts/audit/tooltip_color_audit.ps1:270-278`）：

```powershell
if (($codes[$namePos] -cne $codes[$timePos]) -or (-not (@('§9', '§c') -ccontains $codes[$namePos]))) { $problems.Add(@('R3', ...)) }
```
⇒ R3 要求「名字与时间**同色**且该色 ∈ {§9, §c}」。负面条目要满足 R2（时间蓝）就必然「名红 + 时间蓝」⇒ 必然命中 R3。**任何配色都不可能同时让 R2 与 R3 对负面条目为 0。**

而 `docs/tooltip-color-rules.md:154-155` 明写：

> 1. **负面红保留**：条目已用 `§c` 的，`§c` 优先，规则 1/2/3 不再改写它。
>    - 负面效果条目：`§c中毒 (0:15)§7`、`§c虚弱 (0:15)§7`、`§c饥饿 (0:30)§7` 等保持红色

**我做了什么**：按 D 裁决落地（`§c名§r §9(时间)§尾码`），并达成裁决给的验收线「TIME-NOT-BLUE（R2）0 违例」。**我没有**去改 `scripts/audit/tooltip_color_audit.ps1` 或规则文档来「让它变绿」。

**两条可选收口路径（择一，我不擅自选）**：

1. **承认 D 形态** ⇒ 同步工具与文档：给 R3 增加「名字色码为 `§c` 的负面条目」豁免（与规则文档第 154 行「§c 优先，规则 1/2/3 不再改写它」一致），并把文档第 155 行的示例改成 `§c中毒§r §9(0:15)§7`；预期审计从 251 回落到 230。
2. **以规则文档为准回滚 D** ⇒ 每语 7 对反向替换即可复原（`§c(名)§r §9(时间)` → `§c\1 (时间)`），审计回到 230，R3 归零；但这会撤销「时间统一着蓝」的一致性，属于文案裁决。

复现基线的方法（本次实际用过）：把 16 个文件复制到 `F:/tmp/auditbase/<line>/src/main/...`，对 neo26 三份 lang 的 `fanny_active` 行做上条反向替换（各 7 对），再 `pwsh -File scripts/audit/tooltip_color_audit.ps1 -Root F:\tmp\auditbase`。


## E. 我查到但**没有改**的问题（附原因）

1. **fabric 的 `sign.parunan_active` / `sign.fanny_active` 中文标题与行为矛盾（疑似移植残留）**：fabric 的 Java 字面量是 `套现` / `麻烦制造者`，但本线行为是「远程打开卡牌商店」/「2 选 1 全体事件」（`ParunanSignItem.java:73` 注释「主动「开设卡牌商店」」+ `CardShopOpener.open(player,true)`；`FannySignItem.java:12-15`「主动技能重做为「事件选择界面」」+ `EventChoiceManager.start()`），zh 描述键也写「远程打开卡牌商店」/「在2个全体事件中选择1个全体事件并触发」。按「zh 逐字不变」保留，en/ja 取**本线自己的手册名**（Online Shopping / Event Choice）以保证英日文案自洽。**建议产品裁决后把 fabric 的 zh 标题改回「网络购物 / 事件选择」**（`套现`/`麻烦制造者` 只属于 neo26 的 Cash Out / Trouble Maker）。
2. **既有 `tooltip.astral_dice.card.<type>`（11 键）与新 `dice_face.*`（10 键）语义重叠**：前者是卡面文案、措辞更紧凑（用「| 剩余次数：」、无「蓄力」名字、无「固定(赐福期间)」），直接复用会破坏「zh 逐字不变」⇒ 新建键，重叠关系登记在此。
3. **B 项既有错别字**：代码原字面量写「名刀·**噶**呜切」，而 `item.astral_dice.attack_card_meito` 与手册写「名刀·**嘎**呜切」。按 zh 逐字保留 `噶`，建议后续统一为 `嘎`。
4. **既有跨语色码数不一致（不是我改的）**：`tooltip.astral_dice.sign.dorothy_passive`（zh 10 / en 10 / ja 14 个色码，四线）、`tooltip.astral_dice.sign.jill_active`（zh 14 / en 10 / ja 10，neo21 与 forge）。`tools/check_lang_sync.ps1` 报 4 条 `[WARN]`；`git diff` 显示这些 `§e` 来自工作树里**他人未提交改动**（`+` 行即含），故未动。
5. **既有的 R2 / R3 存量违规**：集中在 `effect.astral_dice.{red_heat_warning,storm_approaching,problem_delivery}.description` 与 `tooltip.astral_dice.sign.deepseek_passive`（时间着 `§e`），全部是本次四项之外的既有缺陷，未动。
6. **neo26 手册条目的立牌英名仍是职称式**（`Game Master Sign` / `Fengshui Master Sign` / `Cult Leader Sign` / `Oasis Queen sign`），且 `tools/verify_sign_display_names.py` 只覆盖 neo21/forge/fabric 三线、**不覆盖 neo26**。属命名门禁覆盖面的缺口，只报告不改（改名字表会动到 `tools/` 与手册文案，超出本次范围）。
7. **`gui.astral_dice.cost` 三语全都未译**（zh/en/ja 都是 `Cost: %s / %s`），与 C 项属同一类中英混排。任务只裁定了 `defense_cost`，故未动；**建议成对改**：zh「攻击花费: %s / %s」、en「Attack Cost: %s / %s」、ja「攻撃コスト: %s / %s」（同族的 `tooltip.astral_dice.cost` 现为 `攻击 §e%s§r` / `Attack §e%s§r` / `攻撃 §e%s§r`，也应一并统一为「攻击花费 / Attack Cost / 攻撃コスト」）。
8. **其余口径分歧**：26.1.2 与基准 112~159 键的旧口径分歧属产品裁决（只报告不擅改）；`getting_started.json` 的 icon 纹理路径另有人处理；fabric 工作区中他人删除的 19 个键（第 0 节列出）保持删除状态，未恢复。

## F. 重跑判据（本节都是**实际输出**，非预期值）

### F.1 `python3 tools/check_lang_sync.py` → exit=0

```
[OK]   neoforge-1.21.1: en_us=1142 键，3 份语言键集一致
[OK]   forge-1.20.1: en_us=1142 键，3 份语言键集一致
[OK]   neoforge-26.1.2: en_us=1166 键，3 份语言键集一致
[OK]   fabric-1.20.1: en_us=1149 键，3 份语言键集一致
```

### F.2 `pwsh -File tools/check_lang_sync.ps1` → exit=0（键集全 OK；`[WARN]` 仅剩 2 个既有键的结构标记差异）

```
OK: zh_cn.json(1142 keys) 与 en_us.json(1142 keys) key 完全一致；ja_jp.json(1142 keys) 与 zh_cn.json key 完全一致。   # neo21
[WARN] tooltip.astral_dice.sign.dorothy_passive: 结构标记不一致(占位符/换行/颜色码) zh=((), 1, 10) ja=((), 1, 14)
[WARN] tooltip.astral_dice.sign.jill_active: 结构标记不一致(占位符/换行/颜色码) zh=((), 3, 14) en=((), 3, 10)
[WARN] tooltip.astral_dice.sign.jill_active: 结构标记不一致(占位符/换行/颜色码) zh=((), 3, 14) ja=((), 3, 10)
OK: zh_cn.json(1142 keys) 与 en_us.json(1142 keys) key 完全一致；ja_jp.json(1142 keys) 与 zh_cn.json key 完全一致。   # forge（同 3 条 WARN）
OK: zh_cn.json(1166 keys) 与 en_us.json(1166 keys) key 完全一致；ja_jp.json(1166 keys) 与 zh_cn.json key 完全一致。   # neo26（1 条 WARN：dorothy_passive）
OK: zh_cn.json(1149 keys) 与 en_us.json(1149 keys) key 完全一致；ja_jp.json(1149 keys) 与 zh_cn.json key 完全一致。   # fabric（1 条 WARN：dorothy_passive）
```
说明：2 个 WARN 键（`sign.dorothy_passive` / `sign.jill_active`）的色码差异来自工作树里**他人未提交改动**，本次未触碰（见 E.4）。

### F.3 `python3 tools/verify_sign_display_names.py` → exit=0

```
== 立牌英日命名门禁 ==
权威表: tools/sign_display_names.json（32 条，updated 2026-10-26）
-- neoforge-1.21.1 -- zh 1142 keys / en 1142 keys / ja 1142 keys
-- forge-1.20.1 -- zh 1142 keys / en 1142 keys / ja 1142 keys
-- fabric-1.20.1 -- zh 1149 keys / en 1149 keys / ja 1149 keys
检查项: 79250
GATE: PASS（立牌英日命名与权威表逐字一致，无旧职称残留）
```
（该门禁按设计不覆盖 neo26，见 E.6。）

### F.4 12 份 lang：JSON 解析 + 键数 + 新增键计数

```
neoforge-1.21.1  zh_cn.json  keys=1142  parse=OK A=62 B=10 empty=0
neoforge-1.21.1  en_us.json  keys=1142  parse=OK A=62 B=10 empty=0
neoforge-1.21.1  ja_jp.json  keys=1142  parse=OK A=62 B=10 empty=0
forge-1.20.1     zh_cn.json  keys=1142  parse=OK A=62 B=10 empty=0
forge-1.20.1     en_us.json  keys=1142  parse=OK A=62 B=10 empty=0
forge-1.20.1     ja_jp.json  keys=1142  parse=OK A=62 B=10 empty=0
neoforge-26.1.2  zh_cn.json  keys=1166  parse=OK A=62 B=10 empty=0
neoforge-26.1.2  en_us.json  keys=1166  parse=OK A=62 B=10 empty=0
neoforge-26.1.2  ja_jp.json  keys=1166  parse=OK A=62 B=10 empty=0
fabric-1.20.1    zh_cn.json  keys=1149  parse=OK A=62 B=10 empty=0
fabric-1.20.1    en_us.json  keys=1149  parse=OK A=62 B=10 empty=0
fabric-1.20.1    ja_jp.json  keys=1149  parse=OK A=62 B=10 empty=0
```
### F.5 键序 / 块连续性（对照 `git HEAD`）

```
neoforge-1.21.1  zh_cn.json  keys=1142  head=1070  A_contig=True B_contig=True extra=72 missing_head=0
neoforge-1.21.1  en_us.json  keys=1142  head=1070  A_contig=True B_contig=True extra=72 missing_head=0
neoforge-1.21.1  ja_jp.json  keys=1142  head=1070  A_contig=True B_contig=True extra=72 missing_head=0
forge-1.20.1     zh_cn.json  keys=1142  head=1070  A_contig=True B_contig=True extra=72 missing_head=0   # en/ja 同
neoforge-26.1.2  zh_cn.json  keys=1166  head=1094  A_contig=True B_contig=True extra=72 missing_head=0   # en/ja 同
fabric-1.20.1    zh_cn.json  keys=1149  head=846   A_contig=True B_contig=True extra=324 missing_head=19  # en/ja 同
   missing（他人删除，非本次）: effect.astral_dice.railgun_ready, msg.astral_dice.fanny_event[.1~.11],
            tooltip.astral_dice.weird_dice_cooldown, msg.astral_dice.parunan_active_no_starlight,
            astral_dice.guide.entry.{teru,nardis,sherry,hanna}_sign.3   → 共 19 个
```
`A_contig` = A 的 62 键严格连续、位于 `tooltip.astral_dice.sign.passive_title` 之后且为 `id.active, id.passive` 交错顺序；`B_contig` = B 的 10 键严格连续位于 `tooltip.astral_dice.applied_stones` 之后；`extra` = 相对 HEAD 的新增键数（fabric 的 324 = 本次 72 + 他人新增 252；同时另有 19 个 HEAD 键被他人删除，故相对 HEAD 净 +303 = 1149 − 846）。

### F.6 占位符 / 裸 `%` / `§c` 段时间 / `§e` 跨语一致性

```
占位符不一致键数 = 0
裸 % 键数 = 0
§c 段内含时间的段数 = 0
§e 数跨语不一致键数 = 6（全部是既有键，他人未提交改动所致）
  neoforge-1.21.1 tooltip.astral_dice.sign.dorothy_passive {'zh_cn.json': 4, 'en_us.json': 4, 'ja_jp.json': 6}
  neoforge-1.21.1 tooltip.astral_dice.sign.jill_active     {'zh_cn.json': 5, 'en_us.json': 3, 'ja_jp.json': 3}
  forge-1.20.1    tooltip.astral_dice.sign.jill_active     {'zh_cn.json': 5, 'en_us.json': 3, 'ja_jp.json': 3}
  forge-1.20.1    tooltip.astral_dice.sign.dorothy_passive {'zh_cn.json': 4, 'en_us.json': 4, 'ja_jp.json': 6}
  neoforge-26.1.2 tooltip.astral_dice.sign.dorothy_passive {'zh_cn.json': 4, 'en_us.json': 4, 'ja_jp.json': 6}
  fabric-1.20.1   tooltip.astral_dice.sign.dorothy_passive {'zh_cn.json': 4, 'en_us.json': 4, 'ja_jp.json': 6}
```
### F.7 染色审计（R0/R1/R1b/R2/R3/R4，12 份 lang）

```
覆盖矩阵： neoforge-1.21.1 / forge-1.20.1 / neoforge-26.1.2 / fabric-1.20.1  x  zh_cn.json, en_us.json, ja_jp.json  (实审 lang 文件 12 个)
tooltip 染色审计： FAIL（251 处违规）      # R0=0 R1=66 R1b=0 R2=72 R3=113 R4=0
  本次新增/改动的键命中 = 0（grep: dice_face.|defense_cost|sign.<id>.active|passive 无靠前行）
  改前基线同脚本 = 230 处（差 21 = fanny_active 的 R3，见 D.1）
```

## G. 最终改动文件清单（16 个，全部为 `M`）

```
neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/event/ModTooltipHandler.java
neoforge-1.21.1/src/main/resources/assets/astral_dice/lang/zh_cn.json
neoforge-1.21.1/src/main/resources/assets/astral_dice/lang/en_us.json
neoforge-1.21.1/src/main/resources/assets/astral_dice/lang/ja_jp.json
forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/ModTooltipHandler.java
forge-1.20.1/src/main/resources/assets/astral_dice/lang/zh_cn.json
forge-1.20.1/src/main/resources/assets/astral_dice/lang/en_us.json
forge-1.20.1/src/main/resources/assets/astral_dice/lang/ja_jp.json
neoforge-26.1.2/src/main/java/com/merlinkitsune/astral_dice/event/ModTooltipHandler.java
neoforge-26.1.2/src/main/resources/assets/astral_dice/lang/zh_cn.json
neoforge-26.1.2/src/main/resources/assets/astral_dice/lang/en_us.json
neoforge-26.1.2/src/main/resources/assets/astral_dice/lang/ja_jp.json
fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/ModTooltipHandler.java
fabric-1.20.1/src/main/resources/assets/astral_dice/lang/zh_cn.json
fabric-1.20.1/src/main/resources/assets/astral_dice/lang/en_us.json
fabric-1.20.1/src/main/resources/assets/astral_dice/lang/ja_jp.json
```

本次未运行任何 gradle / build / test；未触碰 `scripts/test/**`、`release/**`、`work/**`（仅新建本报告）、`.git`；未 `add`/`commit`/`push`。

