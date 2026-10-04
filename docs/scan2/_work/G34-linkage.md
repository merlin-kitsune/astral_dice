# §G3/G4 联动回路与去重 审计(scan2,只读)
- 审计基线: d48a529ba5fcf14610dc7b7383a10780dbe49168 (分支 multi-1.20.1-1.21.1)
- 工作区改动: 仅 PlayerLifecycleHandler.java / ResourceConversion.java (两版本);本报告涉及文件是否处于改动中逐条注明

> ⚠️ **审计期间工作区发生并发改动(重要)**:开工时 `git status` 仅有 `PlayerLifecycleHandler.java` / `ResourceConversion.java`(两版本)modified;审计进行中,同一工作区又被另一批次改写,`git status` 已变为 **16 个 modified + 2 个 untracked**(两版本)。因此:
> - 下文所有行号均为**当前工作区**重新读取后的行号(不是基线行号);
> - 凡引用 `item/sign/{BaseSignItem,BonnieSignItem,HaiqingSignItem,MosesSignItem}.java`、`combat/DiceCombatEvents.java`、`combat/HostileTargets.java`、`item/card/EffectCardPeriod.java`、`item/CurioSlotUtil.java`、`item/chip/BaseChipItem.java`、`item/dice/*`、`event/PlayerLifecycleHandler.java`、`event/PlayerTickEvents.java`、`resource/ResourceConversion.java` 的行号,**随并发批次漂移**;这些文件当前 **modified**。
> - **未**被并发批次改动的(行号稳定、且相对基线未变):`event/AstralEventSystem.java`、`item/InvestigationEventUtil.java`、`item/sign/FannySignItem.java`、`item/sign/RinSignItem.java`、`item/chip/{CurrentCoreChipItem,RailgunChipItem,SatelliteChipItem,CursedSwordChipItem,SmartWatchChipItem,ElectricSwordChipItem}.java`、`event/{RailgunStrikeScheduler,ChipDamageHandler,EnderDiceHandler}.java`、`damage/RailgunBolts.java`、`mixin/EntityThunderHitMixin.java`、`item/MarkManager.java`、`combat/{SpellDamageRegistry,DamageEffectCardHandler}`(后者未在改动清单)。
> - 并发批次**没有**改变本报告任何结论所依赖的行:它改的是 S6-C1(Curios onUnequip 参数语义)/S6-C2(待命计时器搬到玩家级 tick)/G1-G2(PvP 敌对上下文)/A3(受击侧修饰器),**未新增任何 `isCanceled()`、未改去重键、未改回路终止机制**。

---

## 结论摘要(供快速定位)

| ID | 一句话 | 核验 | 严重度 |
|---|---|---|---|
| G-C1 | 同一 tick 内两次**真实**「调查阶段」事件:大侦探 +3 星币计 2 次,调查员活体书页只发 1 张(2 tick 窗口吞掉第 2 次) | 证实 | high |
| G-C2 | 大侦探 +3 星币路径**完全无去重/无守卫**(前次审计结论成立) | 证实 | medium |
| G-C3 | `"fanny_active"` 一个 id 覆盖大侦探 11 项不同事件;`"investigation"` 不区分同类多次真实事件 → 键不唯一 | 证实(可达性分列) | low |
| G-C4 | `applyRinSignPassive(Player)` 单参重载 + `"sign_effect"` 是**死代码** | 证实 | info |
| G-C5 | 去重注释声称的动机「多立牌槽导致 onKill 多次调用」**已不存在**(invokeKillHooks 仅 1 个调用点,且发牌早已不在 onKill 路径) | 证实 | info |
| G-C6 | 占星师:带虚弱印记的**玩家**死亡被末影骰/气囊取消后,印记释放者仍拿 3 星币、击杀者仍拿命运的指引 | 证实 | high |
| G-C7 | 秘密侦探:带隐匿调查的**玩家**死亡被取消后,仍推进调查阶段 + 向全部在线调查员发放活体书页,且隐匿调查永不消失 → 无界刷取 | 证实 | blocker |
| G-C8 | 前次审计列的 CursedSword / Satellite / Bonnie 缺 `isCanceled()` 属实,但**本模组内不可达**(奖励前置要求"非玩家敌对生物",而只有玩家死亡会被取消) | 修正 | low |
| G-C9 | 基线 `LivingDeathEvent` 订阅者 **10** 个;当前工作区因新增 `PlayerHostilityTracker` 变 **11**;同优先级无顺序契约 | 证实 | info |
| G-C10 | 全部命名联动链路逐一核过:**唯一成环**的是「电流核心→冷却→立牌主动→电流核心」,由充能账目终止;其余链路无回边 | 证实 | info |
| G-C11 | `aoeProcessing` 是**普通 static boolean**(非 ThreadLocal、非深度计数),仅靠 try/finally 复位;当前无嵌套可达 → 潜在脆弱点 | 修正 | low |
| G-C12 | `EffectCardPeriod.grantBonusPlay` 的"每轮一次"守卫正确,不存在 +1 双发 | 证伪(无缺陷) | info |

---

ID:G-C1
核验:证实
现象:玩家同时具备大侦探(fanny)与(另一名)调查员(rin)配置时,**同一 tick 内两个不同目标各自触发的两次「调查阶段」事件**,大侦探获得 **+3×2 = 6 星币**,而所有调查员佩戴者**只收到 1 张活体书页**(第二次被吞)。反向情形同样成立:若两次都是活体书页而星币路径因不满足立牌条件不发,则表现为"少发 1 张牌"。
顺序依赖:必须先有 `applySignBuffs`(无守卫、先执行)再有去重(`applyRinSignPassive` 内有窗口);`onEventTriggered` 内顺序固定为 24→25 行,反转不会改变"星币无守卫"这一事实,只会改变异常抛出时的部分发放。契约:两条奖励路径**不共享**去重状态,也没有任何文档规定"事件级去重应统一";因此这不是"契约反转",而是**契约缺失**。
证据:
- A(neoforge-1.21.1) `event/AstralEventSystem.java`(**未改动**) 22-26、70-76:
```java
22:     public static void onEventTriggered(Player triggerer, String eventId) {
23:         if (triggerer.level().isClientSide()) return;
24:         applySignBuffs(triggerer);
25:         applyRinSignPassive(triggerer, eventId);
26:     }
```
```java
70:                 // 同一事件 2 tick 窗口内已给过 → 跳过(防多槽重复分发)
71:                 if (signature.equals(com.merlinkitsune.astral_dice.component.ModAttachments.getRinGiftSignature(sp))
72:                         && now - com.merlinkitsune.astral_dice.component.ModAttachments.getRinGiftTick(sp) <= 2) {
73:                     continue;
74:                 }
75:                 com.merlinkitsune.astral_dice.component.ModAttachments.setRinGiftSignature(sp, signature);
76:                 com.merlinkitsune.astral_dice.component.ModAttachments.setRinGiftTick(sp, now);
```
- B(forge-1.20.1) `event/AstralEventSystem.java`(**未改动**) 同样 22-26 / 71-76(逐字相同,仅 `holdsSign` 走 `CuriosCompat`,见单侧差异节)。
- 两次事件的入口 `item/InvestigationEventUtil.java:49,56`(A)/`:47,54`(B),每次都是独立的 `AstralEventSystem.triggerInvestigationEvent(killer)` → `onEventTriggered(killer,"investigation")`。
可达性:可达。最小复现:① A 装末影骰子(或气囊筹码 + 6 充能)、B 装 bonnie 立牌;② B 对两只 ≥20 血敌对生物分别用一次主动(待命→攻击)施加"隐匿调查"(B 与 A 非同队/无队伍,`isBlessingTarget` 对生物恒真);③ A(或第三方)用**大当家「战斗爽·溅射」**(`FenSignItem` 真伤源 `trueDamage(level, player)`,`getEntity()`=玩家)一发同时击杀这两只怪 → 同 tick 两次 `LivingDeathEvent` → 两次 `triggerByKill`。观察:B 的星币 +6;场上任一 rin 佩戴者只 +1 张活体书页。对照:把两次击杀**分到两个 tick**,则活体书页 +2。该对照即为"吞牌"的判据。
修法:最小 diff 方向 = 给两条奖励路径统一同一去重键;或把窗口从 `<= 2` 改为"同 tick 且同一次事件实例"。**需要用户先定夺口径**:同一 tick 的两次独立击杀到底算"2 次事件"(星币与牌都发 2 次)还是"1 次事件"(都发 1 次)。建议前者 + 引入"事件实例 id"(单调计数器)而非 tick 窗口。
严重度:high

---

ID:G-C2
核验:证实(前次审计结论成立)
现象:大侦探立牌佩戴者每触发一次事件固定 +3 星币,**没有任何去重、窗口、冷却或一次性标记**;因此只要事件入口被调用 N 次就发 N 次。
顺序依赖:`applySignBuffs` 在去重之前调用(AstralEventSystem:24),两者互不知道对方;没有契约。反转(把 buffs 放到去重之后)需要先去重才能少发,但当前实现里星币根本不参与去重。
证据:
- A(neoforge-1.21.1) `event/AstralEventSystem.java`(**未改动**) 33-39 + 90-92:
```java
33:     // 立牌增益挂钩:触发事件后,持有特定立牌的玩家获得特定增益。
34:     private static void applySignBuffs(Player player) {
35:         // 大侦探立牌:自身触发事件后获得 3 星币
36:         if (holdsSign(player, ModItems.FANNY_SIGN.get())) {
37:             giveStarCoins(player, 3);
38:         }
39:     }
```
```java
90:     private static void giveStarCoins(Player player, int count) {
91:         giveItem(player, new ItemStack(ModItems.STAR_COIN.get(), count));
92:     }
```
- B(forge-1.20.1) `event/AstralEventSystem.java`(**未改动**) 33-39 / 90-92:逐字相同。
可达性:可达。最小复现:装 fanny 立牌 + 任意触发一次「调查阶段」,星币 +3;再让同 tick 的第二次事件发生(G-C1 步骤③),星币 +6。对照:去掉第二次事件则为 +3。
修法:最小 diff 方向 = 在 `applySignBuffs` 内复用与 `applyRinSignPassive` 相同的签名/窗口(或统一到 `onEventTriggered` 顶部做一次事件级判定,再分发两条奖励)。**需要用户先定夺口径**(是同 tick 去重还是每事件实例一次)。
严重度:medium(单独不构成刷取;与 G-C7 叠加后成为放大器)

---

ID:G-C3
核验:证实(键不唯一);可达性分列
现象:去重键 = `triggerer.getUUID() + "|" + eventId`,而 `eventId` 的取值**不唯一标识真实事件**:
① `"fanny_active"` 覆盖大侦探主动的 **11 项语义完全不同**的事件(roll 1..11 全部走同一 id);
② `"investigation"` 只标识"调查阶段"这一**类**事件,不区分同类中的多次真实事件(多目标同 tick 击杀 = 多个真实事件)。
③ `"sign_effect"`(单参重载)从未被传入。
顺序依赖:无契约。键在"事件发生后"生成,生成前无从判定唯一性;反转(先判唯一性)需要事件方提供实例 id。
证据:
- A(neoforge-1.21.1) `item/sign/FannySignItem.java`(**未改动**) 43-48:
```java
43:         int roll = ThreadLocalRandom.current().nextInt(1, 12);
44:         applyEvent(player, roll);
45:         sendEventActionBar(player, roll);
46:         // 触发统一事件附加效果:大侦探立牌被动(+3 星币)与调查员立牌联动(活体书页)
47:         // (带独立事件 ID,避免与调查阶段事件在同 tick 触发时互相串扰去重)
48:         com.merlinkitsune.astral_dice.event.AstralEventSystem.onEventTriggered(player, "fanny_active");
```
- B(forge-1.20.1) `item/sign/FannySignItem.java`(**未改动**) 47:`... onEventTriggered(player, "fanny_active");`(其余同 43-47 偏移 -1)。
- 键构造:`event/AstralEventSystem.java:62`(两版本同) `String signature = triggerer.getUUID() + "|" + eventId;`
可达性:①**当前不可达**(fanny 主动有玩家级冷却,一次按键只产生一个事件,且只有一个立牌槽 → 同 2 tick 内不可能出现两个 `fanny_active`;要触发吞并需两个不同真实事件共用一个 id 且落在 2 tick 内)。②**可达**(G-C1 的双击杀即两次 `"investigation"` 共用 id)。③死代码见 G-C4。
修法:最小 diff 方向 = 事件 id 至少带上"事件类型 + 本次调用序号/实体 id";或改传 `ResourceLocation` 级 id 并把窗口收窄为"同一实例"。**需要用户先定夺口径**(fanny 的 11 项算不算 11 个不同事件)。
严重度:low(现状吞并只发生在 `"investigation"`,已由 G-C1 计分)

---

ID:G-C4
核验:证实
现象:无玩家可见后果(死代码)。
顺序依赖:无。
证据:
- A(neoforge-1.21.1) `event/AstralEventSystem.java`(**未改动**) 45-48:
```java
45:     // 兼容入口:未指定事件 ID 时按默认签名去重(供外部直接调用)。
46:     public static void applyRinSignPassive(Player triggerer) {
47:         applyRinSignPassive(triggerer, "sign_effect");
48:     }
```
- B(forge-1.20.1) 同文件(**未改动**) 45-48:逐字相同。
- 全仓检索:`applyRinSignPassive` 的调用点只有 `AstralEventSystem.java:25`(内部)与自身重载;`"sign_effect"` 字面量**仅出现于该重载**(`grep -rn '"sign_effect"'` 两版本各 1 处,均在 AstralEventSystem.java:47)。即单参重载**无外部调用者**。
可达性:不可达(死代码)。最小验证:两版本 `grep -n "applyRinSignPassive" **/*.java` 只列出定义与内部调用。
修法:最小 diff 方向 = 删除该重载;或若打算保留外部入口,补一个真实调用点并明确其 id。无需用户定夺。
严重度:info

---

ID:G-C5
核验:证实
现象:无玩家可见后果(注释与代码脱节);但这条**注释是"2 tick 窗口存在"的唯一理由**,理由失效意味着该窗口现在只起负作用(吞 G-C1 的合法奖励)。
顺序依赖:无契约。
证据:
- A(neoforge-1.21.1) `event/AstralEventSystem.java`(**未改动**) 50-56:
```java
50:     /**
51:      * 带事件 ID 的被动触发。
52:      *
53:      * <p>去重规则:同一玩家(触发者)发出的同一事件 ID,在 2 tick 窗口内被重复分发时
54:      * (如多立牌槽导致 onKill 多次调用),每个佩戴调查员立牌的玩家只获得一次"活体书页",
55:      * 避免"1 次事件导致重复给牌"。不同事件 ID / 不同触发者 / 超过窗口的真实重复不受影响。
56:      */
```
- B(forge-1.20.1) 同文件(**未改动**) 50-56:逐字相同。
- 反证:`item/sign/BaseSignItem.java` 两版本 `invokeKillHooks` **仅 1 个调用点**(`BonnieSignItem.onBonnieKill`),而 `rin` 发牌早已不在 onKill 路径(见 `item/sign/BonnieSignItem.java` A:104-107 / B:103-106 注释"被动 3 已移至 InvestigationEventUtil…")。立牌槽位:两版本均为 `stand` **size 1**(A: `data/astral_dice/curios/slots/stand.json:2` `"size": 1,`;B: `AstralDiceMod.java:101-102` `new SlotTypeMessage.Builder("stand").size(1)`),且 `BaseSignItem.canEquip` 拒绝同物品重复装备 → **"多立牌槽导致 onKill 多次调用"的触发前提不存在**。
可达性:注释描述的路径不可达;真实可达的重复分发路径只有"同 tick 多次真实击杀"(见 G-C1)。
修法:最小 diff 方向 = 改写注释并把窗口语义改为"同一次事件实例";若确认不再需要去重,直接移除窗口(但那会让 G-C1 的活体书页也发 2 张——需用户定夺)。
严重度:info

---

ID:G-C6
核验:证实(前次审计"缺 isCanceled"在占星师这一条上**成立且可造成实际发奖**)
现象:佩戴末影骰子(或气囊筹码 +6 充能)的玩家被"击杀"时,死亡事件被取消、玩家不死,但**虚弱印记的击杀奖励照发**:印记释放者 +3 星币、击杀者获一张「命运的指引」。由于死亡未成立,虚弱印记(5:00)与击杀者身份都可复用 → 每次"差点死掉"都能再领一份。
顺序依赖:取消方(`EnderDiceHandler`,NORMAL 优先级)与发奖方(`HaiqingSignItem.onWeakMarkKill`,NORMAL)同优先级 → **顺序无契约**;但两者**任何顺序都会发奖**(取消在前:玩家没死却已发奖;取消在后:发完奖才取消)。因此这不是"竞态",而是缺守卫的确定性缺陷。契约面:`PlayerLifecycleHandler` 已明文规定"取消死亡 = 不是死亡,不做清理"(`:120-121`),占星师/调查员未遵守同一契约。
证据:
- A(neoforge-1.21.1) `item/sign/HaiqingSignItem.java`(**当前 modified**,行号漂移后) 126-138:
```java
126:     @SubscribeEvent
127:     public static void onWeakMarkKill(LivingDeathEvent event) {
128:         LivingEntity target = event.getEntity();
129:         if (target.level().isClientSide()) return;
130:         if (!target.hasEffect(ModEffects.WEAK_MARK)) return;
131:         Optional<UUID> source = ModAttachments.getWeakMarkSource(target);
132:         if (source.isEmpty()) return;
133:         if (target.level().getPlayerByUUID(source.get()) instanceof Player applier) {
134:             // 只判定击杀者:非玩家击杀(如其它生物)时不发放「命运的指引」
135:             Player killer = event.getSource().getEntity() instanceof Player k ? k : null;
136:             HaiqingSignItem.grantWeakMarkKillReward(applier, killer);
137:         }
138:     }
```
(注:第 124 行的 `if (event.isCanceled()) return;` 属于**另一个**处理器 `onUndercoverRemoved`(`MobEffectEvent.Remove`),不是本处理器。)
- A 发奖本体 `:103-112`:`if (applier == null || ...) return; ItemStack coinStack = new ItemStack(ModItems.STAR_COIN.get(), 3); ... VitaminPillChipItem.giveCard(killer, card);`
- B(forge-1.20.1) `item/sign/HaiqingSignItem.java`(**当前 modified**) 125-137:逐字等价(`ModEffects.WEAK_MARK.get()`,`applier != null` 分支写法,见单侧差异)。
- 取消方:A `event/EnderDiceHandler.java`(**未改动**) 146-159:`if (event.isCanceled()) return; ... event.setCanceled(true); player.setHealth(1.0F);`;A `event/ChipDamageHandler.java`(**未改动**) 75-85:`@SubscribeEvent(priority = EventPriority.HIGHEST) ... event.setCanceled(true); player.setHealth(Math.max(1.0F, player.getHealth()));`。B 同文件同内容(`EnderDiceHandler.java:147-160`、`ChipDamageHandler.java:75-85`)。
- 玩家可被施加虚弱印记:A `combat/DiceCombatEvents.java`(**modified**,漂移后) 228-235 `... isBlessingTarget(target, player) ... setWeakMarkSource(target, Optional.of(player.getUUID()));`;`isBlessingTarget` 对玩家 `:1016-1018` `if (target instanceof Player other) { return other.getTeam() == null || other.getTeam() != player.getTeam(); }`。B 对应 224-231 / 1009-1011。
可达性:可达(两版本)。最小复现:① A 佩戴末影骰子(冷却 5:00)或气囊(1:00、6 充能);② B 与 A **不同队**(或双方无队伍),B 装占星师,按主动进入待命 → 用近战武器攻击 A 一下(施加虚弱印记 5:00);③ B 把 A 打到 0 血 → A 触发不死图腾/气囊,**A 活着**,但 B 背包出现 +3 星币,且 B(击杀者)获得「命运的指引」。对照:让 A 摘掉末影骰子/气囊再打一次 → A 真死,奖励同样发放 → 说明"是否真死"与奖励无关,即已证实。
修法:最小 diff 方向 = 在 `onWeakMarkKill` 第 128 行后加 `if (event.isCanceled()) return;`(与 `EnderDiceHandler`/`PlayerLifecycleHandler` 同款);更强做法是把"玩家是否真的死亡"收敛为一个共用守卫。**无需用户定夺**(与既有 A4 契约一致)。
严重度:high

---

ID:G-C7
核验:证实(本批最严重)
现象:带"隐匿调查"的**玩家**死亡事件被取消后,`InvestigationEventUtil` 仍**推进调查阶段**(`min(stage+1,4)`)并调用事件系统 → 触发者 +3 星币(若佩戴大侦探)、**向服务器上所有佩戴调查员立牌的玩家各发一张活体书页**。而"隐匿调查"是以 `Integer.MAX_VALUE` 时长施加的(`DiceCombatEvents` A:247-249),死亡被取消不会移除它 → **同一目标可无限次复用**,每次只受末影骰 5:00 / 气囊 1:00 冷却限制。叠加 G-C2(星币无去重)后,这是一条可重复的奖励生产线。
顺序依赖:同上,取消方与发奖方同优先级、无契约;任何顺序都会推进状态。关键不对称:`PlayerLifecycleHandler`(`:121`)与 `PlayerHostilityTracker`(`:65`)都检查 `isCanceled()`,而**状态推进与发奖方不检查** → 契约被单方面违反。
证据:
- A(neoforge-1.21.1) `item/InvestigationEventUtil.java`(**未改动**) 106-119:
```java
106:     // 击杀"隐匿调查"目标 → 触发调查阶段事件(全局处理,不要求击杀者佩戴秘密侦探立牌)
107:     @SubscribeEvent
108:     public static void onUndercoverInvestigationKill(LivingDeathEvent event) {
109:         LivingEntity target = event.getEntity();
110:         if (target.level().isClientSide()) return;
111:         if (!target.hasEffect(ModEffects.UNDERCOVER_INVESTIGATION)) return;
112:         if (!(event.getSource().getEntity() instanceof Player killer)) return;
113:         java.util.Optional<java.util.UUID> source = ModAttachments.getUndercoverSource(target);
114:         if (source.isEmpty()) return;
115:         Player applier = target.level().getPlayerByUUID(source.get());
116:         if (applier == null) return;
117:         int markLevel = MarkManager.getLevel(target);
118:         InvestigationEventUtil.triggerByKill(killer, applier, markLevel);
119:     }
```
- A 状态推进与发奖 `:41-58`:
```java
44:         int stage = ModAttachments.getInvestigationStage(applier);
...
48:             ModAttachments.setInvestigationStage(applier, Math.min(stage + 1, 4));
49:             AstralEventSystem.triggerInvestigationEvent(killer);
...
55:         ModAttachments.setInvestigationStage(applier, Math.min(stage + 1, 4));
56:         AstralEventSystem.triggerInvestigationEvent(killer);
```
- A 无期限施加隐匿调查 `combat/DiceCombatEvents.java`(**modified**,漂移后) 244-249:
```java
244:             if (bonnieResult.isPresent() && ModAttachments.getSignReadyType(player) == BonnieSignItem.READY_TYPE) {
...
247:                 ModAttachments.setUndercoverSource(target, Optional.of(player.getUUID()));
248:                 target.addEffect(new MobEffectInstance(ModEffects.UNDERCOVER_INVESTIGATION,
249:                         Integer.MAX_VALUE, 0, false, true));
```
- A 对照(检查了 isCanceled 的两处):`event/PlayerLifecycleHandler.java:121` `if (event.isCanceled()) return;`;`combat/PlayerHostilityTracker.java:65` `if (event.isCanceled()) return;`(后者为工作区**新增未跟踪**文件)。
- B(forge-1.20.1) `item/InvestigationEventUtil.java`(**未改动**) 103-116(与 A 逐字等价,`ModEffects...get()`,偏移 -3);状态推进 `:39-56`;`combat/DiceCombatEvents.java`(**modified**) 243-245;`event/PlayerLifecycleHandler.java:118`;`combat/PlayerHostilityTracker.java:65`。
- 收件人范围放大:A `event/EventTargetCollector.java`(**未改动**) 39-47:`if (members.isEmpty() && anyTeamSystemEnabled && !hasAnyTeam(triggerer)) { ... for (ServerPlayer sp : serverLevel.players()) { if (sp != triggerer) members.add(sp); } }` + `component/GameplayConstants.java:37` `EVENT_APPLY_MC_TEAM = true`(默认)→ **触发者无队伍时,同维度全部在线玩家都算"友方"**,故每个 rin 佩戴者都会收到牌(注:实际只遍历 `serverLevel.players()`,即"同维度"而非真"全服",与 §G5 的口径提示一致)。
可达性:可达(两版本)。最小复现:① B 装 bonnie,按主动进入待命,用近战武器攻击玩家 A(A 与 B 不同队/无队伍,满足 `isBlessingTarget` 的玩家分支)→ A 获得永久"隐匿调查";② A 装末影骰子(或气囊);③ B 击杀 A → A 因图腾/气囊存活;④ 观察:B 的调查阶段从 I 前进到 II,且**每一名**在线 rin 佩戴者的背包各多一张活体书页、B(若也戴 fanny 则不可能因单槽)不适用;⑤ 等 5:00(末日骰冷却)后重复 ③ → 阶段保持 IV 但**每次仍发牌与星币**(阶段封顶不封奖励) → 无界重复即证实。
修法:最小 diff 方向 = `onUndercoverInvestigationKill` 首行加 `if (event.isCanceled()) return;`,并考虑把"事件发奖"统一挂到"死亡成立"的守卫之后(例如只在 `PlayerLifecycleHandler` 的未取消分支里派发)。**无需用户定夺**(属 A4 既有契约的执行遗漏)。
严重度:blocker

---

ID:G-C8
核验:修正(前次审计把 5 处并列,其中 3 处**本模组内不可达**)
现象:无玩家可见后果(本模组内);若**第三方模组**取消了敌对生物的死亡事件(如"宠物不死"类模组),则这 3 处会成为真实缺陷。
顺序依赖:同上,无契约。
证据:
- A(neoforge-1.21.1) `item/sign/BonnieSignItem.java`(**当前 modified**,漂移后) 132-138 无 `isCanceled()`;但其唯一奖励路径 `onKill` `:109-116` 要求 `!(killed instanceof Player) && HostileTargets.isHostile(killed) && killed.getMaxHealth() >= 20`,而 `combat/HostileTargets.java:39-43`(`isHostile(Entity)`,**当前 modified** 但该方法体未变)`if (entity instanceof Enemy) return true; ... return entity instanceof NeutralMob neutral && neutral.isAngry();` → **玩家恒为 false**。
- A `item/chip/CursedSwordChipItem.java`(**未改动**) 118-125:
```java
118:     @SubscribeEvent
119:     public static void onCursedSwordKill(LivingDeathEvent event) {
120:         LivingEntity target = event.getEntity();
121:         if (target.level().isClientSide()) return;
122:         if (!HostileTargets.isHostile(target) || target.getMaxHealth() < 20) return;
123:         if (!(event.getSource().getEntity() instanceof Player killer)) return;
124:         CursedSwordChipItem.onKill(killer);
125:     }
```
- A `item/chip/SatelliteChipItem.java`(**未改动**) 98-108:`if (!HostileTargets.isHostile(target)) return; ...`(单参重载 → 玩家恒 false)。
- B(forge-1.20.1) 三处同:`item/sign/BonnieSignItem.java`(**modified**) 131-137、`item/chip/CursedSwordChipItem.java`(**未改动**) 116-123、`item/chip/SatelliteChipItem.java`(**未改动**) 99-109。
- 本模组**唯一**的 `LivingDeathEvent` 取消方只对玩家生效:A `event/ChipDamageHandler.java:79` `if (!(entity instanceof Player player)) return;`、A `event/EnderDiceHandler.java:150` `if (!(entity instanceof Player player)) return;`(B 同)。
可达性:**本模组内不可达**(奖励前置与"只有玩家死亡被取消"互斥)。判定:前次审计"这 5 处都缺检查"在**代码事实**上正确,在**缺陷判定**上应修正为"仅 Haiqing / Investigation 两处可造成实际后果"。最小验证:装 bonnie + 标记一只 ≥20 血敌对怪,用末影骰玩家挡在中间被同一发伤害"打死" → 怪真死(无取消),玩家被取消;观察 bonnie 是否给牌 —— 若给牌属于"怪真死"的正常奖励,不能作为缺陷证据;需第三方模组取消**怪**的死亡才能证伪。
修法:最小 diff 方向 = 三处同样补 `if (event.isCanceled()) return;`(成本极低、防第三方模组与未来改动),但按"不可达路径不得列为缺陷"的纪律,本报告只给 low。无需用户定夺(建议顺手加)。
严重度:low

---

ID:G-C9
核验:证实
现象:无直接玩家可见后果;但决定了"同一 tick 多个击杀处理器谁先跑"没有保证。
顺序依赖:基线 10 个 `LivingDeathEvent` 处理器分布在 NORMAL(Haiqing/Bonnie/Investigation/CursedSword/Satellite/SmartWatch/ElectricSword/EnderDice)、HIGHEST(ChipDamageHandler)、LOWEST(PlayerLifecycleHandler);同优先级之间的执行顺序 = jar 内 class 条目顺序,**不是契约**。反转后果:ChipDamageHandler(HIGHEST)先取消 → 若另外 5 处补了 `isCanceled()` 就都不会发奖;若它们先于取消者执行则照样发奖。
证据:
- 基线枚举(`git grep -n "(LivingDeathEvent event)" d48a529… -- neoforge-1.21.1`)= **10** 个方法:ChipDamageHandler:76、EnderDiceHandler:147、PlayerLifecycleHandler:115、InvestigationEventUtil:108、CursedSwordChipItem:119、ElectricSwordChipItem:76、SatelliteChipItem:99、SmartWatchChipItem:48、BonnieSignItem:138、HaiqingSignItem:132。
- 当前工作区同一检索 = **11** 个(新增 `combat/PlayerHostilityTracker.java:64`,该文件为 `??` 未跟踪新增)。
- 优先级证据:`event/ChipDamageHandler.java:75` `@SubscribeEvent(priority = EventPriority.HIGHEST)`;`event/EnderDiceHandler.java:146` `@SubscribeEvent`(NORMAL);`event/PlayerLifecycleHandler.java:116` `@SubscribeEvent(priority = EventPriority.LOWEST)`;B 同(`ChipDamageHandler.java:75`、`EnderDiceHandler.java:147`、`PlayerLifecycleHandler.java:113`)。
- `onKill` 扇出远小于前次审计的假设:A `item/sign/BaseSignItem.java`(**modified**,漂移后) 239-250 `invokeKillHooks` 遍历已装备立牌并调用 `sign.onKill`;全仓 `onKill` 覆写**只有** `BonnieSignItem` 一处(A `grep "protected void onKill"` 命中 BaseSignItem:229 定义 + BonnieSignItem:109 覆写);`invokeKillHooks` 调用点**只有** `BonnieSignItem.onBonnieKill` 一处。
可达性:顺序不可观测即"今天安全";最小验证:构造"取消方 vs 发奖方"同 tick 场景(见 G-C6/G-C7),多次重启游戏观察是否偶发"发奖/不发奖"差异 → 若差异出现即证明无契约(当前因缺 `isCanceled()` 而恒发奖,反而掩盖了顺序问题)。
修法:最小 diff 方向 = 让发奖方显式声明优先级(必须在取消方之后)或统一改为"只在未取消分支发奖"。**需要用户先定夺**:是否允许"死亡被取消后仍发击杀奖励"(与奖励口径绑定)。
严重度:info

---

ID:G-C10
核验:证实(逐链路核过;**唯一成环的是电流核心链路,且有终止机制**)
现象:无玩家可见后果(未发现失控回路)。
顺序依赖:各链路均为单向扇出;唯一回边是 `CurrentCoreChipItem.tryFinishCooldown → BaseSignItem.performSkill → sign.handleUse → CurrentCoreChipItem.onActiveSkillUsed`,它的每次闭合都要求一次玩家按键 + 至少 1 点充能,且"消耗 ≥ 1 / 获得 = 1"→ 净不增。
证据(逐链路,`文件:行号` 以 A 为准,`是否成环` 见下表):
- fanny→rin:A `item/sign/FannySignItem.java:48` → `event/AstralEventSystem.java:22-26,36-38,57-83`;回边检查:`giveItem`→`item/chip/VitaminPillChipItem.java:38-47` 只调用 `HealingManager.add`,**不回调任何事件系统**(全仓 `AstralEventSystem.` 调用点仅 FannySignItem:48、InvestigationEventUtil:49/56)= **无回边**。
- bonnie→rin / bonnie→调查阶段:A `item/InvestigationEventUtil.java:41-58,108-119`;回边检查:`applyStageEffects` 只 `addEffect`(INVISIBILITY / INVESTIGATION_BONUS),`DiceCombatEvents` 里这两个效果只影响 `LivingChangeTargetEvent`(A `:818-823`,B `:814-816`)与伤害加成 = **无回边**;阶段 `Math.min(stage+1,4)` 单调封顶。
- haiqing→击杀奖励:A `item/sign/HaiqingSignItem.java:103-138`;回边检查:奖励是星币 +「命运的指引」牌,`item/card/FateGuidanceCardItem.java`(**未改动**) 的两个订阅者(`:85` `LivingIncomingDamageEvent` 七咒减伤、`:122` 进食饱和)都不产生印记 = **无回边**。
- railgun→雷击→伤害:A `item/chip/RailgunChipItem.java:99-111`(登记,1 秒后)、`event/RailgunStrikeScheduler.java:121-157`(tick 边界、先摘队列再处理)、`mixin/EntityThunderHitMixin.java:44-62`(换真伤)、`damage/ModDamageTypes.java:39-41`:`public static DamageSource trueDamage(Level level) { return new DamageSource(trueHolder(level, TRUE_DAMAGE)); }`(**无来源实体**)。因此雷击击杀**不满足** `event.getSource().getEntity() instanceof Player`(SmartWatch/ElectricSword/CursedSword/Satellite/Bonnie/Investigation/Haiqing 全部前置失败)= **无回边**;终止机制 = `isOnCooldown`(1:00,`RailgunChipItem:80-83,105`)+ 6 层充能(`:106,149-151`)+ `RailgunBolts` 弱引用集合(`damage/RailgunBolts.java:22-23`)。
- current core→立牌冷却→立牌主动→current core:A `item/chip/CurrentCoreChipItem.java:55-59`(`onActiveSkillUsed` → `ChargeManager.addStacks(player, 1)`)、`:78-95`(`tryFinishCooldown`:消耗 `instantCooldownCost`(1..6)、`setSignActiveCooldownEnd(player, now)`)、`item/sign/BaseSignItem.java:87-96`(冷却中按键 → 消耗充能完成冷却)、`:113-122`(技能生效 → 冷却 + `onActiveSkillUsed`;B `:119-125`) = **成环**;终止机制 = 充能账目(消耗 ≥1 / 获得 1)+ "需玩家再次按键"的离散化,单 tick 内不自递归。
- 递归保护字段与异常安全:`combat/DiceCombatEvents.java` `:134` `static boolean aoeProcessing = false;`(**普通 static,非 ThreadLocal**)、`:143` `private static int counterDepth = 0;`(**普通 static 深度计数**)、`:637-684` / `:1146-1155` 均在 `try { ... } finally { ... }` 内复位(`counterDepth--`,注释明说"异常/提前返回都会复位");`event/DamageEffectCardHandler.java:29` `private static final ThreadLocal<Boolean> APPLYING_TRUE_BONUS = ThreadLocal.withInitial(() -> Boolean.FALSE);` + `:62-69` `try/finally` → **四个闸门都不会"异常后卡 true"**。
可达性:回路本身可达但受终止机制约束。最小验证(电流核心):装满 6 充能 + 立牌,连按主动键两次 → 冷却立即完成并进入新一轮、充能净减 ≥0;连续 20 次按键后充能不增(净不增即终止机制成立)。
修法:无需修改。若要防御 `aoeProcessing` 的"嵌套提前清闸"潜在问题(见 G-C11),改为深度计数或 ThreadLocal。
严重度:info

---

ID:G-C11
核验:修正(前次审计称"`counterDepth`/`aoeProcessing`(非 ThreadLocal,单线程假设)"——事实成立,但**当前不构成缺陷**)
现象:无玩家可见后果(当前不可达);潜在后果 = 嵌套 AOE 时 `aoeProcessing` 被内层 `finally` 提前清假,外层剩余循环失去闸门。
顺序依赖:外层置真 → 内层置真 → 内层 `finally` 置假 → 外层仍以为在处理中(实为假)。契约:无;靠"结构上不会嵌套"来保证。
证据:
- A `combat/DiceCombatEvents.java`(**modified**,漂移后) `:134` / `:200` / `:637-684`:
```java
134:     static boolean aoeProcessing = false;
...
200:         if (aoeProcessing || counterDepth > 0) return;
...
637:             aoeProcessing = true;
...
684:                 aoeProcessing = false;
```
- A `combat/SpellDamageRegistry.java`(**未改动**) `:256,263` 与 `:374,381`:同样 `DiceCombatEvents.aoeProcessing = true; try { ... } finally { DiceCombatEvents.aoeProcessing = false; }`。
- B 对应:`combat/DiceCombatEvents.java`(**modified**) `:130/:196/:634/:681`;`combat/SpellDamageRegistry.java`(**未改动**) `:256,263,374,381`。
可达性:**当前不可达**。理由(AOE 波及伤害无法再次进入 AOE 路径):① 两个 AOE 都用 `trueDamage(level, attacker)`,其 `getDirectEntity()` 为 null,而 `combat/DiceCombatEvents.java:201` `if (!(directEntity instanceof Player player)) return;` 会先返回(故骰战/溅射分支不会再进);② `event/DamageEffectCardHandler.java:37-42` 要求 `source.getEntity() instanceof Player` 且 `SpellDamageRegistry.isSpellDamage(...)` 命中断言,而三组 matcher(A `SpellDamageRegistry.java:162-174`)分别为"弹射物实例 / msgId=magic|indirectMagic / `MAGIC_DAMAGE_TYPES` 白名单(其他模组魔法)",`astral_dice:true_damage`(A `damage/ModDamageTypes.java:26-28`)不在其中 → `DamageEffectCardHandler` 不会对 AOE 伤害再跑 `onHit`。③ 大当家溅射的受害者也走 `trueDamage(level, player)`。
修法:最小 diff 方向 = 把 `aoeProcessing` 换成与 `counterDepth` 同款的深度计数(或在 AOE 入口显式 `isInCounterChain`-风格的自拒绝)。无需用户定夺(纯稳健性)。
严重度:low

---

ID:G-C12
核验:证伪(不存在 +1 双发)
现象:无缺陷;`grantBonusPlay` 的"每轮一次"守卫正确,重复调用不会叠加。
顺序依赖:立牌主动(忍者)调用 `grantBonusPlay` → 失败则拒绝释放且不进入冷却(`item/sign/KomachiSignItem.java:80`),保证"上限中途下降"不变量不被破坏。
证据:
- A `item/card/EffectCardPeriod.java`(**modified**,漂移后) `:171-175`:
```java
171:     public static boolean grantBonusPlay(Player player) {
172:         if (getBonusPlays(player) > 0) return false;
173:         ModAttachments.setEffectCardBonusPlays(player, 1);
174:         return true;
175:     }
```
- 唯一清理入口 `:184-189` `clearRoundBonuses`(清 `effect_card_bonus_plays` / candy / satellite / `living_page_cycle_bonus`)。
- B `item/card/EffectCardPeriod.java`(**modified**) 同结构(`grep -n grantBonusPlay` 命中 `:171` 附近同一实现)。
可达性:可达但行为正确。最小验证:同一出牌轮内连按两次忍者主动 → 第二次返回 false、不进入冷却、出牌上限只 +1。
修法:无需修改。
严重度:info

---

## 去重键清单

| 触发入口 | 文件:行号 | eventId 实参 | 窗口/守卫 | 唯一性判定 |
|---|---|---|---|---|
| 大侦探主动(11 项随机事件全部) | A `item/sign/FannySignItem.java:48` / B `:47` | `"fanny_active"` | **无任何窗口/守卫**(`AstralEventSystem.applySignBuffs` 只判是否佩戴 fanny) | ✗ 11 个语义不同事件共用一个 id;且星币发放本身无去重 |
| 调查阶段事件(击杀隐匿调查目标) | A `item/InvestigationEventUtil.java:49` / `:56`;B `:47` / `:54` | `"investigation"` | 2 tick(**gameTime**)窗口,判定 A `event/AstralEventSystem.java:71-72`;状态键 `rin_gift_signature` / `rin_gift_tick`,默认 `""` / `0L`(A `component/ModAttachments.java:286-303`;B `:262-275`) | △ 类级唯一、实例级不唯一:同 tick 两次真实击杀 → 第二次被吞(仅影响 rin 发牌,不影响星币/阶段推进) |
| 兼容重载(无调用者) | A/B `event/AstralEventSystem.java:46-48` | `"sign_effect"` | 同上窗口 | — 死代码,全仓无调用点 |
| 星币发放(大侦探) | A/B `event/AstralEventSystem.java:36-37`(`giveStarCoins` `:90-92`) | 无(不参与去重) | **无** | ✗ 无键可谈:调用 N 次即发 N 次 |
| 活体书页发放(调查员) | A/B `event/AstralEventSystem.java:57-83` | 继承调用方 id | 每个收件人各存一份 (`sp` 侧签名+时刻) | 收件人粒度正确(不同 rin 玩家互不影响);触发者粒度靠 UUID 前缀正确 |
| 占星师击杀奖励(虚弱印记) | A `item/sign/HaiqingSignItem.java:127-137`;B `:126-137` | 无 | **无去重**;唯一前置 = 目标仍带 `weak_mark` 且 `weak_mark_source` 可解析 | ✗ 死亡被取消时不消耗印记 → 同一目标可反复发奖(见 G-C6) |
| 秘密侦探击杀奖励(带标记 ≥20 血敌对) | A `item/sign/BonnieSignItem.java:109-117`(经 `:133` 的 `invokeKillHooks`) | 无 | 前置:`!(killed instanceof Player) && isHostile && maxHealth>=20` | ✗ 无去重,但 AOE 多杀 = 多次真实击杀,属"按次发放";玩家目标被前置排除 |
| 调查阶段推进 | A `item/InvestigationEventUtil.java:48,55`;B `:46,53` | 无 | `Math.min(stage+1,4)` 封顶 | ✗ 无去重(stage 封顶不等于奖励封顶:每次事件仍发牌/发币) |
| 出牌数 +1(立牌主动) | A/B `item/card/EffectCardPeriod.java:171-175` | 无 | 附件 `effect_card_bonus_plays` > 0 即拒绝;周期结束 `clearRoundBonuses` 清 | ✓ 每轮一次,正确 |

**窗口性质回答(必答项)**:窗口大小 = **2**;基准 = `ServerLevel#getGameTime()`(A/B `event/AstralEventSystem.java:59` `long now = serverLevel.getGameTime();`),即**世界 gameTime 窗口**(不是 `player.tickCount`、不是自增计数器);判定式为 `now - 存值 <= 2`(**含 2**,故实际覆盖 [t, t+2] 共 3 个 tick 边界);两侧附件默认值 `""` / `0L` 使"从未发过牌"的玩家不会被误判(需 `signature` 相等且 `now-0<=2` 同时成立,仅服务器开局 2 tick 内可能满足,且那时 `signature` 也已写入)。

---

## 回路清单

| 链路 | 是否成环 | 终止机制 | 文件:行号 |
|---|---|---|---|
| fanny → 事件系统 → rin 发牌 → (用牌) | 否(无回边) | 无需终止;`giveCard` 只走 `HealingManager.add` | A `item/sign/FannySignItem.java:48`;`event/AstralEventSystem.java:22-26,78-80`;`item/chip/VitaminPillChipItem.java:38-47` |
| bonnie → 调查阶段 → rin 发牌 | 否(无回边) | 阶段单调封顶 `min(stage+1,4)` | A `item/InvestigationEventUtil.java:41-58`;`:108-119` |
| bonnie → 调查阶段 → 隐身/调查增益 → 生物索敌 | 否 | 效果到期;`LivingChangeTargetEvent` 取消索敌不产生新事件 | A `combat/DiceCombatEvents.java:818-823`(B `:814-816`) |
| haiqing 印记 → 击杀奖励 → 命运的指引 | 否(无回边) | 奖励只产币/牌 | A `item/sign/HaiqingSignItem.java:103-138`;`item/card/FateGuidanceCardItem.java:85,122` |
| railgun → 雷击 → 真伤 → (击杀钩子) | 否(源无实体→钩子全部前置失败) | 1:00 冷却 + 6 层充能 + 弱引用标记集合 | A `item/chip/RailgunChipItem.java:80-83,99-111,141-157`;`damage/ModDamageTypes.java:39-41`;`mixin/EntityThunderHitMixin.java:44-62`;`event/RailgunStrikeScheduler.java:121-157` |
| current core → 立牌主动冷却 → 立牌主动技能 → current core | **是** | 充能账目(消耗≥1 / 获得 1)+ 每次闭合需玩家按键;单 tick 不自递归 | A `item/chip/CurrentCoreChipItem.java:55-59,78-95`;`item/sign/BaseSignItem.java:87-96,113-122`(B `:119-125`) |
| 骰战 → 大当家溅射(嵌套 `hurt`) → 骰战 | 否(结构截断) | `aoeProcessing` 闸门 + 真伤源 `getDirectEntity()==null` | A `combat/DiceCombatEvents.java:134,200,635-686`(B `:130,196,632-683`) |
| 反击 → 闪避 → 反击 | 否(结构截断) | `counterDepth` 深度计数 + `try/finally` | A `combat/DiceCombatEvents.java:143,146-148,1109,1146-1155`(B `:139,143-145,1119,1156-1165`) |
| 法伤加成 → 真伤 `hurt` → 法伤加成 | 否 | `ThreadLocal<Boolean> APPLYING_TRUE_BONUS` + `try/finally`;且 matcher 不匹配 `true_damage` | A `event/DamageEffectCardHandler.java:29,62-69`;`combat/SpellDamageRegistry.java:162-174` |
| 调查阶段发奖 → (死亡被取消后)状态推进 | 否(无环),但**缺守卫** | 无(见 G-C7) | A `item/InvestigationEventUtil.java:48-56,108-119` |

---

## 单侧差异

| 项 | neoforge-1.21.1 | forge-1.20.1 | 是否语义差异 |
|---|---|---|---|
| `event/AstralEventSystem.java` 取饰品 | `CuriosApi.getCuriosInventory`(A:86) | `CuriosCompat.getCuriosInventory`(B:86) | 否(包装层等价) |
| `item/InvestigationEventUtil.java` 效果比较 | `effect.getEffect().value() != ModEffects.UNDERCOVER_INVESTIGATION.get()`(A:126-127) | `effect.getEffect() != ModEffects.UNDERCOVER_INVESTIGATION.get()`(B:123-124) | 否(1.21.1 多一层 `Holder.value()`) |
| `item/sign/HaiqingSignItem.java` applier 解析 | `if (target.level().getPlayerByUUID(source.get()) instanceof Player applier)`(A:133) | `Player applier = ...; if (applier != null)`(B:132-133) | 否(等价) |
| `item/chip/CursedSwordChipItem.java` 千咒刻印附魔获取 | `registryAccess().lookupOrThrow(...).get(CURSE_MARKER_KEY)`(A:101-104) | `ModEnchantments.CURSE_MARKER.get()`(B:103) | 否(注册方式差异:1.21.1 数据驱动 JSON) |
| `event/RailgunStrikeScheduler.java` tick 挂钩 | `ServerTickEvent.Post`(无相位判定) | `TickEvent.ServerTickEvent` + `if (event.phase != TickEvent.Phase.END) return;` | 否(等价:Post ≡ END) |
| `item/MarkManager.java` tick 挂钩 | `ServerTickEvent.Post` | `TickEvent.ServerTickEvent` + Phase.END 过滤 | 否(同上) |
| `mixin/EntityThunderHitMixin.java` 点火 API | `igniteForSeconds(8.0F)` | `setSecondsOnFire(8)` | 否(Mojang 改名) |
| **回路/去重相关** | — | — | **未发现任何单侧差异**:去重键、窗口、星币路径、取消死亡的两处取消方、5 处缺 `isCanceled()` 的处理器、`aoeProcessing`/`counterDepth`/`APPLYING_TRUE_BONUS` 全部两版本同构 |

---

## 未核验

1. **`LivingDeathEvent` 是否能对同一实体在同一 tick 被派发两次**:未核验(需反编译 1.21.1/1.20.1 的 `LivingEntity.die` 与 NeoForge `CommonHooks.onLivingDeath` 挂点)。若存在"被取消后同一 tick 再次 `die()`"的路径,G-C6/G-C7 的发放次数会进一步放大。理由:本报告只用到"被取消后仍发奖"这一事实,不需要该假设;但定量"每次冷却能领几份"需要它。
2. **第三方模组取消**敌对生物**死亡**时 G-C8 三处的实际后果**:未核验(本仓不含此类模组,无法实机复现)。判定依赖"只有玩家死亡会被本模组取消"这一本仓事实。
3. **`stand` 槽位是否可能被数据包/其他模组扩到 >1**:未核验。已核验默认值 = 1(A `data/astral_dice/curios/slots/stand.json:2`;B `AstralDiceMod.java:101-102`),且 `BaseSignItem.canEquip` 拒绝同物品重复。若被扩到 2,`invokeKillHooks` 会遍历多个立牌,但当前只有 Bonnie 覆写 `onKill`,与 rin 发牌无关(G-C5 的前提仍然不成立)。
4. **`ModEffects.WEAK_MARK` / `UNDERCOVER_INVESTIGATION` 在"死亡被取消"时是否会被其它路径移除**:未核验(推测不会,因为 `die()` 被取消后 `removeAllEffects` 不执行;但未逐行核对 1.21.1/1.20.1 的 `die()` 与 `checkTotemDeathProtection` 分支)。这决定 G-C6/G-C7 的"可重复次数"。
5. **基线 vs 工作区的行号一致性**:已核验结论层面(并发批次未触及去重/守卫),但**逐行号**在 G-C1/G-C2/G-C6/G-C7/G-C9/G-C11 引用的 modified 文件上会随并发批次继续漂移;引用时请以 `grep` 关键字而非裸行号定位。
6. **`git diff` 是否已覆盖全部并发改动**:以审计结束时刻的 `git status` 为准(16 modified + 2 untracked);若批次在我落盘后继续写入,`item/sign/*`、`combat/DiceCombatEvents.java` 的行号可能再次变化。
