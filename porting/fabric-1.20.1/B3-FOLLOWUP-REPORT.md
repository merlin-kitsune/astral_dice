# B3-FOLLOWUP — 路障 / 拒止 tooltip、三语 lang 补键、电击手套特效(B3 收尾批次)
## 0. 结论
- A(ModTooltipHandler 三段 tooltip):**已完成**;本批无编译环境,结论为源码级核对(§3)。
- C(三份 lang 补 19 键):**已完成并全绿**;JSON 可解析、check_lang_sync exit 0、三语键集一致、值逐键等于 forge 原文(§4)。
- B(ElectricGloveChipItem 两处特效):**未落地**。所需前置在本线缺失或可见性不足,且全部落在本批禁写清单内,证据见 §5。
- 未 commit / 未 push / 未启动游戏 / 未编译。
## 1. A — event/ModTooltipHandler.java
文件:`fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/ModTooltipHandler.java`;1529 → 1557 行(+28),CRLF、loneLf=0、无 BOM,`git diff --stat` 该文件为 `28 ++++`。
### 1.1 新增两条 import(:27-28),前后各 3 行
```
 24: import java.util.List;
 25: 
 26: import com.merlinkitsune.astral_dice.client.KeyBindingSetup;
 27: import com.merlinkitsune.astral_dice.item.RepulsionItem;
 28: import com.merlinkitsune.astral_dice.item.card.ExclusiveCardUtil;
 29: import com.merlinkitsune.astral_dice.item.card.EffectCardPeriod;
 30: import com.merlinkitsune.astral_dice.item.chip.RevengeHalberdChipItem;
```
### 1.2 三段插入点:原 :605 收尾大括号(fight_poison 段)之后、原 :604 BLANK_SIGN 段之前(现 :632 起)
```
600:         if (stack.is(ModItems.EFFECT_CARD_FIGHT_POISON_WITH_POISON.get())) {
601:             tooltip.add(Component.translatable("effect.astral_dice.fight_poison_with_poison.description")
602:                     .withStyle(ChatFormatting.GRAY));
603:             addEffectCardPlayCountTooltip(tooltip, player);
604:             tooltip.add(Component.translatable("tooltip.astral_dice.card.effect_cooldown", effectCardCooldownSeconds(player)).withStyle(ChatFormatting.RED));
605:         }
         …… 606-631 = 本批新增代码(下节逐行) ……
632:         if (stack.is(ModItems.BLANK_SIGN.get())) {
633:             tooltip.add(Component.empty());
634:             tooltip.add(Component.translatable("tooltip.astral_dice.material.blank_sign")
635:                     .withStyle(ChatFormatting.GRAY));
636:         }
```
### 1.3 新增代码原文(:606-631);forge 蓝本 `:606-614` 路障牌 / `:615-626` 拒止牌 / `:627-650` 拒止物品
```java
606:         // 2026-11-01 移植自 forge-1.20.1 同路径:路障效果牌三行 = 用法(右键方块面) / 挡路 + 拉仇恨 / 耐久(末行);
607:         // 行内数值一律 §e(非时间数值着黄,见本类头部「物品 tooltip 统一染色规则」)
608:         if (stack.is(ModItems.EFFECT_CARD_BARRICADE.get())) {
609:             addCardLines(tooltip, "tooltip.astral_dice.card.barricade");
610:             addEffectCardPlayCountTooltip(tooltip, player);
611:             tooltip.add(Component.translatable("tooltip.astral_dice.card.effect_cooldown", effectCardCooldownSeconds(player)).withStyle(ChatFormatting.RED));
612:         }
613:         // 2026-11-01 移植自 forge-1.20.1 同路径:强化拒止(专属效果牌)五行 = 用法 / 吸附 + 缓慢·虚弱 / 苦力怕不能自爆 / 不拉仇恨·免疫 / 耐久(末行);
614:         // 行内数值着 §e(非时间数值)、时间着 §9;专属行照本线 exclusive_owner 段(DARK_PURPLE)
615:         if (stack.is(ModItems.EFFECT_CARD_REPULSION.get())) {
616:             addCardLines(tooltip, "tooltip.astral_dice.card.repulsion");
617:             addEffectCardPlayCountTooltip(tooltip, player);
618:             tooltip.add(Component.translatable("tooltip.astral_dice.card.effect_cooldown", effectCardCooldownSeconds(player)).withStyle(ChatFormatting.RED));
619:             tooltip.add(Component.translatable("tooltip.astral_dice.card.exclusive_owner").withStyle(ChatFormatting.DARK_PURPLE));
620:         }
621:         // 2026-11-01 移植自 forge-1.20.1 同路径:强化拒止(物品形态) = 静态说明(四行) + 三份状态(耐久 / 剩余寿命 / 已提供充能) + 所有者两行;
622:         // 行内数值着 §e、时间着 §9。所有者名字在绑定 owner 时写入物品,所有者离线也读得到(同卡条目 DARK_PURPLE)。
623:         if (stack.is(ModItems.REPULSION.get())) {
624:             addCardLines(tooltip, "tooltip.astral_dice.item.repulsion");
625:             tooltip.add(Component.translatable("tooltip.astral_dice.item.repulsion_durability", RepulsionItem.getDurability(stack), RepulsionItem.MAX_DURABILITY).withStyle(ChatFormatting.GRAY));
626:             tooltip.add(Component.translatable("tooltip.astral_dice.item.repulsion_lifetime", formatMmSs((int) ((RepulsionItem.getRemainingTicks(stack) + 19L) / 20L))).withStyle(ChatFormatting.GRAY));
627:             tooltip.add(Component.translatable("tooltip.astral_dice.item.repulsion_charge", RepulsionItem.getChargeGiven(stack), com.merlinkitsune.astral_dice.entity.RepulsionEntity.MAX_CHARGE_STACKS).withStyle(ChatFormatting.GRAY));
628:             String repulsionOwnerName = ExclusiveCardUtil.ownerName(stack);
629:             tooltip.add(Component.translatable("tooltip.astral_dice.item.repulsion_owner_name", repulsionOwnerName.isEmpty() ? Component.translatable("tooltip.astral_dice.item.repulsion_owner_unknown") : Component.literal(repulsionOwnerName)).withStyle(ChatFormatting.DARK_PURPLE));
630:             tooltip.add(Component.translatable("tooltip.astral_dice.item.repulsion_owner").withStyle(ChatFormatting.DARK_PURPLE));
631:         }
```
## 2. C — 三份 lang 各补 19 键
文件:`fabric-1.20.1/src/main/resources/assets/astral_dice/lang/{en_us,zh_cn,ja_jp}.json`。每份先 `read` 注册观察、再整份重排插入(只 splice 新行、不动既有行),靠 FS 版本号兜并发;三份均未报 `FS_STALE_VERSION`。
| 项目 | 改前 | 改后 |
|---|---|---|
| 键行数 / 唯一键数 | 923 / 921 | 942 / 940 |
| 字节(en / zh / ja) | 120683 / 74226 / 85623 | 123506 / 75853 / 87508 |
| 编码 | 2 空格缩进、CRLF、无 BOM | 同左(CRLF 944、loneLf 0、以收尾大括号 + CRLF 结束) |
插入行号三份一致:`item.astral_dice.effect_card_barricade`@324、`…effect_card_repulsion`@338、`item.astral_dice.repulsion`@352、`msg.…barricade_invalid`@423、`…barricade_placed`@424、`msg.…repulsion_invalid`@426、`…repulsion_placed`@427、`msg.astral_dice.repulsion_invalid`@899、`…repulsion_need_sign`@900、`…repulsion_placed`@901、`tooltip.astral_dice.card.barricade`@500、`tooltip.astral_dice.card.repulsion`@524、`tooltip.astral_dice.item.repulsion`@618、`…repulsion_charge`@619、`…repulsion_durability`@620、`…repulsion_lifetime`@621、`…repulsion_owner`@622、`…repulsion_owner_name`@623、`…repulsion_owner_unknown`@624。
注:`tooltip.astral_dice.card.exclusive_owner` 本线三份早已存在(B3 §5-③ 的「若缺」不成立),未重复新增。
### 2.1 新增键与三语值(取自 forge-1.20.1 同路径 lang 原文,未改写;`\n` 为文件内转义,`§r` 尾随码保留)
| 键 | en_us | zh_cn | ja_jp |
|---|---|---|---|
| `item.astral_dice.effect_card_barricade` | Barricade | 路障 | バリケード |
| `item.astral_dice.effect_card_repulsion` | Reinforced Repulsion | 强化拒止 | 強化拒止 |
| `item.astral_dice.repulsion` | Reinforced Repulsion | 强化拒止 | 強化拒止 |
| `msg.astral_dice.effect_card_barricade_invalid` | You can't place a barricade here (needs a solid surface to attach to) | 这里放不下路障（需要可依附的实心面） | ここにはバリケードを設置できません（設置できる固体の面が必要です） |
| `msg.astral_dice.effect_card_barricade_placed` | Barricade placed | 已放置路障 | バリケードを設置しました |
| `msg.astral_dice.effect_card_repulsion_invalid` | You can't place a Reinforced Repulsion here (needs a solid surface to attach to, and the spot must be free) | 这里放不下强化拒止（需要可依附的实心面，且该处没有强化拒止） | ここには強化拒止を設置できません（設置できる固体の面が必要で、その位置に強化拒止があってはいけません） |
| `msg.astral_dice.effect_card_repulsion_placed` | Reinforced Repulsion placed | 已放置强化拒止 | 強化拒止を設置しました |
| `msg.astral_dice.repulsion_invalid` | You can't place a Reinforced Repulsion here (needs a solid surface to attach to, and the spot must be free) | 这里放不下强化拒止（需要可依附的实心面，且该处没有强化拒止） | ここには強化拒止を設置できません（設置できる固体の面が必要で、その位置に強化拒止があってはいけません） |
| `msg.astral_dice.repulsion_placed` | Reinforced Repulsion placed | 已放置强化拒止 | 強化拒止を設置しました |
| `msg.astral_dice.repulsion_need_sign` | You must equip the LingLing Sign to place a Reinforced Repulsion | 需要装备「怪力乱神立牌」才能放置强化拒止 | 強化拒止を設置するには「リンリンのスタンド」を装備している必要がある |
| `tooltip.astral_dice.card.barricade` | §eRight-click§7 the face of a block to place a barricade there\nBlocks players and mobs like a fence, and pulls non-elite, non-boss hostile mobs within a §e8§7-block radius to attack it\nDurability §e30§7, loses §e1§7 point per creature hit, and vanishes §920:00§7 after being placed | §e右键§7 对你注视的方块面使用，在该处放置路障\n像栅栏一样阻挡玩家与生物通过，并吸引半径 §e8§7 格内的非精英、非 Boss 敌对生物攻击它\n耐久 §e30§7，被生物攻击一次损失 §e1§7 点，放置满 §920:00§7 后自动消失 | §e右クリック§7 で見ているブロックの面にバリケードを設置する\nフェンスのようにプレイヤーとモブの通行を妨げ、半径 §e8§7 ブロック以内のエリート・ボス以外の敵対モブを引き寄せて攻撃させる\n耐久値 §e30§7、モブに攻撃されるたびに §e1§7 減る、設置から §920:00§7 経過で自動消滅 |
| `tooltip.astral_dice.card.repulsion` | §eRight-click§7 the face of a block to place a Reinforced Repulsion there\nIt keeps pulling hostile mobs within §e12§7 blocks slowly toward itself, and applies §9Slowness (0:10)§7 and §9Weakness (0:10)§7\nAttached Creepers cannot detonate\nDurability §e50§7, losing an amount equal to the number of attached targets every §910 seconds§7; it vanishes automatically §920:00§7 after being placed\n§e5§7 melee hits from its placer send it back as an item (durability / remaining lifetime / charge are kept; nobody else can recover it) | §e右键§7 对你注视的方块面使用，在落点放置强化拒止\n强化拒止持续将半径 §e12§7 格内敌对生物拉向自身，并施加 §9缓慢 (0:10)§7、§9虚弱 (0:10)§7\n被吸附的苦力怕无法自爆\n耐久 §e50§7，每 §910 秒§7 按吸附目标数消耗耐久；放置满 §920:00§7 后自动消失\n放置者近战命中 §e5§7 次会把它回收成物品形式（保留耐久 / 剩余寿命 / 已提供充能；其他人无法回收） | §e右クリック§7 で見ているブロックの面に強化拒止を設置する\n強化拒止は半径 §e12§7 ブロック以内の敵対モブを自身へ引き寄せ続け、§9移動速度低下 (0:10)§7 と §9弱体化 (0:10)§7 を付与する\n引き寄せ中のクリーパーは自爆できなくなる\n耐久値 §e50§7、§910 秒§7 ごとに引き寄せ中のターゲット数だけ耐久値を消費し、尽きると砕ける；設置から §920:00§7 経過で自動消滅\n設置者の近接攻撃が §e5§7 回命中するとアイテムとして回収される（耐久値 / 残り寿命 / 付与済みチャージは保持。他のプレイヤーは回収不可） |
| `tooltip.astral_dice.item.repulsion` | §eRight-click§7 the face of a block to place a Reinforced Repulsion there (placement rules are identical to the effect card)\nIt restores the state stored in the item: durability / remaining lifetime / charge already granted\n§e5§7 melee hits from its placer send it back as an item (nobody else can recover it; projectile hits do not count)\nHostile mobs and environmental damage cannot harm it, and player attacks no longer cost durability | §e右键§7 对你注视的方块面放置强化拒止（放置判据与效果牌完全一致）\n放置后恢复到物品里保存的状态：耐久 / 剩余寿命 / 已提供充能\n放置后放置者近战命中 §e5§7 次即可把它重新回收成物品（其他人无法回收；弹射物命中不算）\n怪物与环境伤害对它无效；玩家攻击也不会扣耐久 | §e右クリック§7 で見ているブロックの面に強化拒止を設置する（設置判定は効果カードと完全に同一）\n設置するとアイテムに保存された状態へ復元される：耐久値 / 残り寿命 / 付与済みのチャージ\n設置後に設置者の近接攻撃が §e5§7 回命中すると、再びアイテムとして回収される（他のプレイヤーは回収不可。飛び道具は不可）\n敵対モブや環境ダメージでは傷つかず、プレイヤーの攻撃でも耐久値は減らない |
| `tooltip.astral_dice.item.repulsion_durability` | Durability: §e%s§7 / §e%s§r | 耐久：§e%s§7 / §e%s§r | 耐久値：§e%s§7 / §e%s§r |
| `tooltip.astral_dice.item.repulsion_lifetime` | Remaining lifetime: §9%s§r | 剩余寿命：§9%s§r | 残り寿命：§9%s§r |
| `tooltip.astral_dice.item.repulsion_charge` | Charge granted: §e%s§7 / §e%s§r | 已提供充能：§e%s§7 / §e%s§r | 付与済みチャージ：§e%s§7 / §e%s§r |
| `tooltip.astral_dice.item.repulsion_owner` | Exclusive: only its owner can place and recover it | 专属：仅获得者可以放置与回收 | 専用：獲得者のみ設置・回収できる |
| `tooltip.astral_dice.item.repulsion_owner_name` | Owner: %s | 所有者：%s | 所有者：%s |
| `tooltip.astral_dice.item.repulsion_owner_unknown` | Unknown | 未知 | 不明 |
## 3. 签名对照(调用点 vs 现存声明原文)
| 我方调用点 | 现存声明(实读) |
|---|---|
| `addCardLines(tooltip, "…")` :609 / :616 / :624 | `ModTooltipHandler.java:114: private static void addCardLines(List<Component> tooltip, String langKey, Object... args) {` |
| `addEffectCardPlayCountTooltip(tooltip, player)` :610 / :617 | `ModTooltipHandler.java:296: private static void addEffectCardPlayCountTooltip(List<Component> tooltip, Player player) {` |
| `formatMmSs(int)` :626 | `ModTooltipHandler.java:263: private static String formatMmSs(int seconds) {`(Locale.ROOT 的 `%d:%02d`,不含单位) |
| `RepulsionItem.getDurability(stack)` / `MAX_DURABILITY` :625 | `RepulsionItem.java:73: public static int getDurability(ItemStack stack) {` / `:51: public static final int MAX_DURABILITY = 50;` |
| `RepulsionItem.getRemainingTicks(stack)` :626 | `RepulsionItem.java:85: public static long getRemainingTicks(ItemStack stack) {` |
| `RepulsionItem.getChargeGiven(stack)` :627 | `RepulsionItem.java:90: public static int getChargeGiven(ItemStack stack) {` |
| `RepulsionEntity.MAX_CHARGE_STACKS` :627 | `entity/RepulsionEntity.java:278: public static final int MAX_CHARGE_STACKS = 5;` |
| `ExclusiveCardUtil.ownerName(stack)` :628 | `item/card/ExclusiveCardUtil.java:32: public static String ownerName(ItemStack stack) {`(空串 = 未绑定) |
## 4. 验收(实跑原始输出)
① 三份 lang JSON 可解析:
```
$ python -c "import json,io; [json.load(io.open(p,encoding='utf-8')) for p in [en_us, zh_cn, ja_jp]]; print('JSON OK all three')"
JSON OK all three
exit=0
```
② 语言一致性门禁:
```
$ python tools/check_lang_sync.py
[OK]   neoforge-1.21.1: en_us=1070   (原输出中文后缀走本机 GBK stdout,显示为乱码,ASCII 部分原样)
[OK]   forge-1.20.1: en_us=1070      (同上)
[OK]   neoforge-26.1.2: en_us=1094   (同上)
[OK]   fabric-1.20.1: en_us=940      (同上;由 921 增至 940)
EXIT=0
```
③ 值逐键比对(forge en_us 非合法 JSON ⇒ 行正则 + unescape;zh/ja 直接 JSON.parse):`value mismatches after unescape: 0`(19 键 × 3 语)。
④ 三语键集:`en==zh: true  en==ja: true`。⑤ 禁写 import 终检:`grep -rn` 搜 `^import .*net.minecraftforge` 于上述两 Java 文件 ⇒ `GREP_EXIT=1`(0 命中)。
## 5. B — ElectricGloveChipItem 未落地(需裁决)
现状:`item/chip/ElectricGloveChipItem.java` 共 89 行,仍是 2026-10-05 版(充能记账 + `markLatestGlovePaid`),**没有** `executeShock` / `SHOCK_DAMAGE` / `SHOCK_DELAY_TICKS` / `COOLDOWN_TICKS` / `isOnCooldown`;forge 同路径 191 行为 2026-10-21 重设计:`:159: public static boolean executeShock(ServerLevel level, Vec3 center, ServerPlayer cause) {`,两处调用在 `:175: ElectricShockFx.sparksAt(level, ElectricShockFx.victimCenter(victim));` 与 `:182: ElectricShockFx.shockSound(level, center.add(0.0D, 1.0D, 0.0D));`。
`combat/ElectricShockFx` 本线存在且签名与 forge 一致(`:61 public static Vec3 victimCenter(LivingEntity victim)`、`:67 public static void sparksAt(ServerLevel level, Vec3 at)`、`:81 public static void shockSound(ServerLevel level, Vec3 at)`),但缺两个前置:
| 缺件 | 证据 | 归属 |
|---|---|---|
| `SpellDamageRegistry.collectGloveShockTargets(...)` | forge `combat/SpellDamageRegistry.java:419` 有;fabric 同文件无(全仓 grep 方法名 0 命中) | `combat/**` — 本批禁写 |
| `DamageCardFxScheduler.applySpellDamageAmount(...)` 可见性 | forge `:616: public static boolean applySpellDamageAmount(...)`;fabric 同文件 `:565: private static void applySpellDamageAmount(...)` | `combat/**` — 本批禁写 |
缺这两件时 `executeShock` 无法保持 forge 语义;若改用 `ModDamageTypes.cardSpell(...)` + `hurt` 会绕过 `DamageNumberAggregator` 的组别登记与 `DiceCombatEvents.beginAoe/endAoe` 窗口(语义漂移),故未擅自实现。
## 6. 无法判定 / 需主 Agent 裁决
1. **B 项整体**:宿主方法 `executeShock` 在本线不存在;补齐需改 `combat/SpellDamageRegistry`(新增方法)与 `combat/DamageCardFxScheduler`(private → public static),两者都在本批禁写清单 ⇒ 请指派 `combat` 批次,或解禁后本批续做。
2. **FX 数值与调度**:forge `executeShock` 内含伤害、延迟、冷却等常量与调度,属 2026-10-21 设计的一部分;本批只被要求补两处调用行,是否同批移植需裁决。
3. **`§r` 尾随码**:新增 tooltip 值与 forge 逐字节一致,结尾含 `… §e%s§r`。项目 tooltip 染色 v2 规范禁止文案内用 `§r` 回落,但任务要求「照 forge 原样」,故未擅改 —— 需裁决是否统一去除。
4. **`card.exclusive_owner` 文案**:该键本线已存在且与 :619 用法一致,但未与 forge 当前值逐字节比对(不在本批 19 键清单内);若 forge 侧近改过文案,需另行对齐。
5. **编译未验证**:整线被 `item/sign/LinglingSignManager` 缺席阻塞(非本批职责),A 的结论为源码级核对,无字节码级证据。
## 7. 未做的事
- 未 commit / 未 push / 未打 tag;未启动 dev 客户端或服务端;未跑 Gradle 构建。
- 未触碰 `AstralDiceMod.java`、`ModItems.java`、`ModCreativeTabs.java`、mixin 配置、`item/sign/**`、`network/**`、`component/**`、`combat/**`;未改动任何既有 lang 行,未重排全文顺序。
