# §H 全局静态状态 审计(scan2,只读)
- 审计基线: d48a529ba5fcf14610dc7b7383a10780dbe49168 (分支 multi-1.20.1-1.21.1)
- 工作区改动: 仅 PlayerLifecycleHandler.java / ResourceConversion.java (两版本) 有未提交改动;本报告涉及的文件是否处于改动中逐条注明
- ⚠️ 审计期间工作区发生漂移(非本次审计所为):派单时工作区仅 2 个文件改动;审计结束时 `git status --porcelain` 为 **32 个已改文件 + 4 个新增未跟踪文件**(另一进程在同步改代码)。因此**本报告所有 file:line 与原文一律取基线提交 d48a529**,并在每个 ID 内用「工作区状态」一行注明该文件此刻是否已被并发改动;若工作区与基线结论不同,以基线为准并在该行写明工作区新形态。基线源码已只读快照到 `%TEMP%\dsh_base\<subproject>\...`(426 个 java 文件),便于复算。

方法:两个子项目各跑 `static\s+(final\s+)?(Map|Set|List|HashMap|HashSet|ArrayList|ConcurrentHashMap|WeakHashMap|IdentityHashMap|Cache|Deque)<`、`static\s+\w+\s+\w+\s*=`、`ThreadLocal`、`static (int|boolean|long)`;对每个候选逐条 grep 写者/读者判定可达性。Curios 回调参数语义、`ItemStack.EMPTY` 可写性取自**依赖 jar 字节码 + 官方 sources jar**,非猜测(见 H-C19 证据)。
- 1.21.1 反编译源:`neoforge-1.21.1/build/moddev/artifacts/neoforge-21.1.235-sources.jar`
- 1.20.1 反编译源:`forge-1.20.1/build/moddev/artifacts/forge-1.20.1-47.4.10-sources.jar`
- Curios 1.21.1 = `curios-yohfFbgD.jar`(build.gradle:155);Curios 1.20.1 = `curios-IPQlZkz1.jar`(build.gradle:195)

---

# H1 static 可变状态

## 无害常量/只写一次注册表(计数可辩护,不逐条展开)
两版本各 12 处 static final 不可变常量或**只在类初始化时写一次**的注册表,均非「可变容器缺陷」:
`GameplayConstants.MAX_STARLIGHT/MAX_MARKER/EFFECT_CARD_COOLDOWN_SECONDS/MAX_EFFECT_STACKS/MAX_EFFECT_CARD_PLAYS/CHARGE_MAX_STACKS/CHARGE_COOLDOWN_REDUCTION/HAND_FAN_BIG_RANGE/CARD_SLOTS_*/MAX_CARD_COST/COMBAT_DAMAGE_CALC_INTERVAL_TICKS/SPELL_DAMAGE_CALC_INTERVAL_TICKS/HEALING_TIMER_TICKS`(A `GameplayConstants.java:15-81` / B 同)。
以下 6 个容器的写者只有类初始化路径(`<clinit>` / `ModItems` 静态初始化),运行时**只有读**:
- `SpellDamageRegistry.MATCHERS/MODIFIERS`(A:94,97;B:94,97)
- `DiceCombatModifiers.ATTACK_MODIFIERS/DEFENSE_MODIFIERS`(A:58,59;B:59,60)
- `DiceCombatEvents.EXTERNAL_DAMAGE_FACTORS`(A:915;B:909)
- `CardRegistry.BY_ID`(A/B:39,`LinkedHashMap`)
- `DiceTierRegistry.TIERS`(A/B:16;写者仅 `ModItems.java:208-244` 的 13 次 `register`,类初始化一次,无 `clear()` ⇒ 无重复注册路径)
- `EffectCardPeriod.FIXED_SOURCES/TEMPORARY_SOURCES/EFFECT_PENDING_SOURCES`(A:73-75;B:74-76;写者仅静态块与 `registerEffectPendingSource`,A:89)
判定:信息级,不构成 H1 缺陷。

---

```text
ID:H-C1
核验:证实
现象:「扫地机」立牌(jasmine)的移动累计表**没有瞬移闸门**(同仓「能量回收」筹码有)。任何"位置大跳"都会一次性灌入大量位移 ⇒ 立牌被动「每 300 米交替 +1 攻击/防御(各上限 20)」可在一次维度切换/回程/重登/末影珍珠后瞬间拉满 +20 攻 +20 防。
顺序依赖:玩家在 A 世界/维度记录 lastPos → 传送或换维度 → 下一 tick `tickJasmineWalk` 用新坐标减旧坐标。契约:位移累计必须只统计**真实行走**;反转后(加闸门)只会让"瞬移白拿加成"消失,不影响正常行走累计。
证据:
  A(neoforge-1.21.1) JasmineSignItem.java:29-30 基线原文
    `    private static final Map<UUID, Vec3> lastPosMap = new HashMap<>();`
    `    private static final Map<UUID, Float> walkAccumMap = new HashMap<>();`
  A JasmineSignItem.java:95-115 基线原文(节选,注意**无** dist 上限判断)
    `    private static void tickJasmineWalk(Player player, ItemStack stack) {`
    `        UUID uuid = player.getUUID();`
    `        Vec3 pos = player.position();`
    `        Vec3 lastPos = lastPosMap.get(uuid);`
    `        if (lastPos == null) { lastPosMap.put(uuid, pos); walkAccumMap.put(uuid, 0f); return; }`
    `        double dx = pos.x - lastPos.x; ... float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);`
    `        lastPosMap.put(uuid, pos);`
    `        float total = walkAccumMap.getOrDefault(uuid, 0f) + dist;`
    `        while (total >= DISTANCE_THRESHOLD) { total -= DISTANCE_THRESHOLD; applyMovementBonus(player, stack); }`
    `        walkAccumMap.put(uuid, total);`
  A JasmineSignItem.java:27 `    private static final float DISTANCE_THRESHOLD = 300f;`
  B(forge-1.20.1) JasmineSignItem.java:30-31 / 96-116 / 28 同文,行号 +1(声明 30,31;方法 96;阈值 28)
  对照(同仓已有正确写法) B EnergyRecyclerChipItem.java:112-115 基线原文
    `        // 传送/切维度等瞬移不视为移动,保留已有进度但不累计瞬移距离`
    `        if (dist > MAX_MOVE_PER_TICK) { return; }`
  同 A EnergyRecyclerChipItem.java:113-115(B 同文,+1)
  清理路径:A JasmineSignItem.java:53-54(clearSignData 内 `lastPosMap.remove(...)`/`walkAccumMap.remove(...)`;B:54-55)。**只在真卸下清理,不在登出/换世界清理。**
可达性:可达,最小复现:①装备基础骰子 + 扫地机立牌;②在主世界 (X≈2400,64,0) 建下界门并进出一次(下界坐标 ÷8 ⇒ 单程位移 ≈2100 ⇒ 约 7 次级联加成);或更直接:立牌佩戴中,用 `/tp` 或末影珍珠瞬移 6000 格,观察 tooltip 攻/防加成一次跳满至 20/20。也可"退出到标题再进另一个世界"触发(见 H-C3 同源跨世界残留)。
修法:把 EnergyRecycler 的 `dist > MAX_MOVE_PER_TICK(10.0f)` 闸门(或"维度/世界 ID 变化即重置 lastPos")搬到 JasmineSignItem.tickJasmineWalk,放在 `lastPosMap.put` **之前**。无需用户定夺口径(同仓已有先例)。
严重度:medium
工作区状态:两版本 JasmineSignItem.java **未在改动列表中**(结论对当前代码同样成立)。
```

```text
ID:H-C2
核验:证实
现象:「友情徽章」的触发冷却表把**世界 gameTime** 当时间戳。单人模式下退出世界 A 再进/新建世界 B,gameTime 回到小值 ⇒ `now - last` 为**负数**,恒 `< TRIGGER_COOLDOWN_TICKS` ⇒ 该玩家对的徽章治疗触发**长期失效**(直到新世界 gameTime 追上旧记录;若 A 世界已到第 100 天 ≈ 2,400,000 tick,则约 33 小时游戏时间)。反之 `size()>500` 时整表 `clear()` 会把**所有人的冷却一起清掉**(冷却可被绕过)。
顺序依赖:先写入(治疗)后判定(同玩家对再次治疗)。契约:时间戳必须与比较用的时钟同源同纪元;跨世界后纪元改变即失效。
证据:
  A FriendshipBadgeChipItem.java:36 基线原文 `    private static final Map<String, Long> LAST_TRIGGER = new HashMap<>();`
  A FriendshipBadgeChipItem.java:56-63 基线原文
    `        String key = healer.getUUID() + "|" + target.getUUID();`
    `        long now = healer.level().getGameTime();`
    `        Long last = LAST_TRIGGER.get(key);`
    `        if (last != null && now - last < TRIGGER_COOLDOWN_TICKS) return;`
    `        LAST_TRIGGER.put(key, now);`
    `        if (LAST_TRIGGER.size() > 500) { LAST_TRIGGER.clear(); }`
  B FriendshipBadgeChipItem.java:37 / 57-64 同文(声明 37;逻辑 57-64)
可达性:可达,最小复现:单人世界 A(A 与 B 两名玩家同队,靠 `/execute` 或第二客户端),A 对 B 施加治疗类效果牌(汉堡/巧克力蛋糕),确认双方各 +2 治愈;退出到标题 → 新建世界 B(同日同队)→ 再次治疗:治愈点**不再增加**(冷却被负差值永久拦住),直到整表被 500 条上限清空。
修法:时间戳改用与纪元无关的计数(如 `healer.level().getServer().getTickCount()`,或 `System.nanoTime()` 换算),或把表放到"按世界/服务端实例"持有并在世界切换时 `clear()`。**是否允许冷却跨世界保留属平衡口径,需用户先定夺**(另一选择:登出即清表,冷却只在会话内有效)。
严重度:medium
工作区状态:两版本 FriendshipBadgeChipItem.java **未在改动列表中**。
```

```text
ID:H-C3
核验:证实
现象:电磁炮延迟雷击队列是**静态 List**,条目持有 `ServerLevel`/`LivingEntity`/`ServerPlayer` 强引用;超龄判定用的是**该条目自己的 level 的 gameTime**。一旦那个 level 不再被 tick(单人"退出到标题"、维度卸载、`ServerLevel` 被替换),`now` 被冻结,`now - fireAt > MAX_AGE_TICKS` 永远不成立 ⇒ 条目**永不回收**,连带整个 `ServerLevel`+实体图**无法被 GC**(单人同一 JVM 内持续). 若该 level 之后又恢复 tick,条目会在 `now >= fireAt` 时用**旧 level 对象**执行 `executeStrike`。
顺序依赖:先 `schedule`(登记) → 服务端 tick 到期执行。契约:条目必须有"世界已不存在即丢弃"的出口;反转(以服务器全局 tick 计龄)会立刻消除该泄漏。
证据:
  A RailgunStrikeScheduler.java:81 `    private static final List<Pending> PENDING = new ArrayList<>();`
  A RailgunStrikeScheduler.java:83-84 `    /** 超龄保护(tick):维度卸载等极端情形下丢弃滞留条目,避免无限堆积 */` / `    private static final long MAX_AGE_TICKS = 400L;`
  A RailgunStrikeScheduler.java:133-143 基线原文
    `            Pending p = it.next();`
    `            long now = p.level().getGameTime();`
    `            if (now - p.fireAt() > MAX_AGE_TICKS) { it.remove(); continue; }`
    `            if (now < p.fireAt()) continue;`
  A RailgunStrikeScheduler.java:42-59 `Pending` 字段:`private final ServerLevel level; private final LivingEntity target; ... private final ServerPlayer cause;`
  B RailgunStrikeScheduler.java:81/83-84/129-139 同文(PENDING 81,MAX_AGE 84,age 判定 134-140)
可达性:可达,最小复现:装备电磁炮筹码,攻击敌对目标(登记 +20 tick 后降雷),**1 秒内**按 Esc 暂停(集成服务端随之停止 tick)并"保存并退出到标题";再用 JVM 内存快照/调试器观察 `RailgunStrikeScheduler.PENDING` 仍为 1 且旧 `ServerLevel` 不可回收。单人下"暂停→退出"是可稳定复现的窗口。
修法:把 age 判定改为不受 level tick 影响的数据源(如 `p.fireAt()` 记录时同时记 `level.getServer().getTickCount()`,或用 `ServerTickEvent` 的全局 server tick 计龄);并在 `ServerStoppedEvent`/`LevelEvent.Unload` 时 `PENDING.clear()` 或按 level 过滤剔除。无需用户定夺(纯资源回收口径)。
严重度:medium
工作区状态:两版本 RailgunStrikeScheduler.java **未在改动列表中**。
```

```text
ID:H-C4
核验:证实(程度为"跨会话残留",非串扰)
现象:电流剑筹码的击杀计数是静态 UUID 表,**只在真正卸下筹码时清**(`onChipUnequip`),不在登出/换世界时清。⇒ 退出再进/换世界后击杀进度继续累积(5 杀 + 再 5 杀即回充,而非重新数 10);长期占位表项。
顺序依赖:击杀累加 → 满 10 清零回充。契约:该计数是"当前佩戴期间的进度";跨会话保留会让回充更快。
证据:
  A ElectricSwordChipItem.java:35 `    private static final Map<UUID, Integer> KILL_COUNTS = new HashMap<>();`
  A ElectricSwordChipItem.java:53-58 `    protected void onChipUnequip(Player player, ItemStack stack) { if (player != null) { KILL_COUNTS.remove(player.getUUID()); } }`
  A ElectricSwordChipItem.java:65-72 `        int kills = KILL_COUNTS.getOrDefault(uuid, 0) + 1; if (kills >= KILLS_REQUIRED) { KILL_COUNTS.remove(uuid); ChargeManager.addStacks(killer, CHARGE_GAIN); } else { KILL_COUNTS.put(uuid, kills); }`
  B ElectricSwordChipItem.java:35 / 54-57 / 66-71 同文
可达性:可达,最小复现:单人装电流剑筹码(已有 4 杀进度,可先卸下再装 → 清零,再杀 4 个)→ 退出到标题 → 进同一世界 → 再杀 1 个敌对目标:应"第 5 杀"即刻回充 2 点充能(会话内不中断计数)。
修法:按口径决定:若"进度仅限佩戴期且随会话结束"，在 `PlayerLoggedOutEvent`/世界切换清表;若"跨会话保留是有意的",此条降为 info 并加注释锚定口径。**需用户先定夺**。
严重度:low
工作区状态:两版本 ElectricSwordChipItem.java **未在改动列表中**。另:新文件 `combat/PlayerHostilityTracker.java` 的注释把本类列为"静态表先例"(见 H-C17)。
```

```text
ID:H-C5
核验:证实(修正注释声称的兜底路径不存在)
现象:`ChargeManager.DEATH_PRESERVED_STACKS` 的"登出/异常路径兜底"`clearDeathPreserved(Player)` **全仓无任何调用者**(死代码)⇒ 死亡后未重生就退出,表项跨世界存活;此后在**另一个世界**重生时 `restoreAfterDeath` 会把旧世界的充能层数发还。
顺序依赖:`preserveOnDeath`(死亡,读当前层数) → `restoreAfterDeath`(重生,`remove` 取值回写)。契约:表项生命周期必须**限于同一次死亡-重生**;跨世界/跨会话存活即违约。
证据:
  A ChargeManager.java:21 `    private static final Map<UUID, Integer> DEATH_PRESERVED_STACKS = new HashMap<>();`
  A ChargeManager.java:75/77 `            DEATH_PRESERVED_STACKS.put(player.getUUID(), stacks);` / `            DEATH_PRESERVED_STACKS.remove(player.getUUID());`
  A ChargeManager.java:85 `        Integer stacks = DEATH_PRESERVED_STACKS.remove(uuid);`
  A ChargeManager.java:91-96 `    /** 清理指定玩家的死亡暂存(登出/异常路径兜底) */` … `    public static void clearDeathPreserved(Player player) { … }`
  A 全仓 grep `clearDeathPreserved` 仅命中该声明本身(1 处);B 同样仅命中声明(ChargeManager.java:92)
  同文:B ChargeManager.java:21 / 75,77 / 85 / 92-97
可达性:可达但影响小,最小复现:持有 >0 层充能 → 普通死亡(不重生)→ 直接"退出到标题" → 新建/进入另一世界 → 重生瞬间充能层数被发还(本应归 0)。
修法:在 `PlayerLoggedOutEvent` 调 `clearDeathPreserved`,`PlayerRespawnEvent` 回写后也兜底清一次;或把暂存并入已有的 `DeathPreservedBonuses`(该表至少还有克隆回写契约)。无需用户定夺。
严重度:low
工作区状态:两版本 ChargeManager.java **未在改动列表中**。
```

```text
ID:H-C6
核验:修正(注释断言"不会把旧世界的值写进新世界"不成立)
现象:`DeathPreservedBonuses.PRESERVED` 是 `ConcurrentHashMap<UUID,int[]>`;死亡时写入、重生时 `remove` 回写。若玩家死亡后**不重生**就退出(单人退出到标题),表项跨世界存活;下次在**另一个世界**重生时会把旧世界的 `rin_pages`/`komachi_damage_bonus` 写进新世界(注释称"任一新世界的死亡都会先覆盖该表项"——但"先覆盖"只在玩家在新世界**再次死亡**时才发生,首次重生路径不会覆盖)。
顺序依赖:`LivingDeathEvent` 写 → `PlayerEvent.Clone(LOWEST)`/`PlayerRespawnEvent` 读+删。契约:同一次死亡-重生内有效。
证据:
  A DeathPreservedBonuses.java:33 `    private static final Map<UUID, int[]> PRESERVED = new ConcurrentHashMap<>();`
  A DeathPreservedBonuses.java:21-23 基线注释原文
    ` * 局限:本表只在**同一 JVM 会话**内有效——死亡后未重生就退出世界/关服则不再保留`
    ` * (单人模式"退出到标题"不会卸载类,表项会跨世界存活;因重生事件只由真正的重生流程触发、`
    ` * 且任一新世界的死亡都会先覆盖该表项,故不会把旧世界的值写进新世界)。`
  A DeathPreservedBonuses.java:41-44 `        PRESERVED.put(player.getUUID(), new int[] { ModAttachments.getRinPages(player), ModAttachments.getKomachiDamageBonus(player) });`
  A DeathPreservedBonuses.java:50 `        int[] preserved = PRESERVED.remove(player.getUUID());`(后接 `> 现值` 才回写)
  B DeathPreservedBonuses.java:33 / 21-23 / 41-44 / 50 同文
可达性:可达但影响小,最小复现:单人世界 A 用调查员立牌刷若干页(`rin_pages` 可在附件面板/tooltip 观察)→ 死亡不重生直接退出到标题 → 进新建世界 B → 重生后立刻查看 `rin_pages`:应为 0,实际为世界 A 的值。
修法:在 `PlayerLoggedOutEvent` 清该玩家的表项;或把 `PlayerRespawnEvent` 的回写限定为"同 server 会话同一 level 维度"。另建议同步修正类注释(现注释是错误断言)。无需用户定夺。
严重度:low
工作区状态:两版本 DeathPreservedBonuses.java **未在改动列表中**。
```

```text
ID:H-C7
核验:证伪(该表本身不是缺陷)
现象:能量回收筹码的移动表与 H-C1 同形,但**已有瞬移闸门**,不会因传送/换维度白拿充能。残留仅是"退出再进后 `lastPosMap` 里的旧坐标仍在"(同世界同位置进服 ⇒ 位移≈0;不同世界/不同位置进服 ⇒ 该次 `dist>10` 被闸门拦掉,只更新 lastPos)。唯一副作用是表项不在登出时清(每个 UUID 2 条,量级可忽略)。
顺序依赖:`tickMovement` 先比距离后过闸门;闸门必须在累加前。
证据:
  A EnergyRecyclerChipItem.java:42-43 声明;96-123 逻辑;`dist > MAX_MOVE_PER_TICK` 在 A:113(阈值 `MAX_MOVE_PER_TICK = 10f` 在 A:34)
  B EnergyRecyclerChipItem.java:41-42 声明;95-122 逻辑;闸门 B:112;阈值 B:33
  清理:A:125-129 `clearTracking`(remove 两表),由 A:64-65 的 `onChipUnequip` 调用;B:124-129 / 64-65 同文
可达性:不可达为"白拿加成"这一现象(被闸门挡住)。仅"登出后条目残留"可达,无玩家可见后果。
修法:无需修;若要与 H-C1 统一,可在登出时清两个移动表。
严重度:info
工作区状态:两版本 EnergyRecyclerChipItem.java **未在改动列表中**。
```

```text
ID:H-C8
核验:证实
现象:`EmeraldDiceTrade` 用 **ThreadLocal CONTEXT/DEPTH** 携带"当前交易玩家",守卫是 mixin 的 `@At("HEAD")`/`@At("RETURN")` 成对注入——**不是 try/finally**。被注入的方法一旦抛异常,`@At("RETURN")` 不执行 ⇒ CONTEXT/DEPTH 在服务端主线程**永久泄漏** ⇒ `isSwapActive()` 恒真;而 `MerchantOfferMixin` 的 6 处覆写**只判 `isSwapActive()`、不校验交易者身份** ⇒ 全服所有村民报价(对所有玩家)都被改写成"星币支付 + 20% 折扣",直到重启。另:`begin()` 对"无绿宝石骰子"的玩家提前 return 而 `end()` 仍会递减 DEPTH,若存在跨玩家的嵌套窗口会提前摘掉别人的上下文(此路径未核验)。
顺序依赖:begin(压栈)→ 窗口内所有 `getItemCostA/satisfiedBy/...` → end(弹栈)。契约:成对必须由 finally 保证;反转(改 finally)后异常不再污染全局。
证据:
  A EmeraldDiceTrade.java:21-22 `    private static final ThreadLocal<Player> CONTEXT = new ThreadLocal<>();` / `    private static final ThreadLocal<Integer> DEPTH = new ThreadLocal<>();`
  A EmeraldDiceTrade.java:29-33 `        if (CONTEXT.get() == null) { CONTEXT.set(player); DEPTH.set(0); }` / `        DEPTH.set(DEPTH.get() + 1);`
  A EmeraldDiceTrade.java:36-45 `    public static void end() { if (CONTEXT.get() == null) return; int depth = DEPTH.get() - 1; if (depth <= 0) { CONTEXT.remove(); DEPTH.remove(); } else { DEPTH.set(depth); } }`
  A mixin/trade/MerchantMenuMixin.java:19-29 `@Inject(method = "tryMoveItems", at = @At("HEAD"))` … `@Inject(method = "tryMoveItems", at = @At("RETURN"))`(无 try/finally)
  A mixin/trade/MerchantOfferMixin.java:35-38 `    @Inject(method = "getItemCostA", at = @At("HEAD"), cancellable = true)` / `        if (!EmeraldDiceTrade.isSwapActive()) return;`(getItemCostB/getCostA/getCostB/satisfiedBy 同形,均无玩家身份校验)
  B EmeraldDiceTrade.java:23-24 / 31-35 / 38-47 / mixin 同名同行(forge 侧 `mixin/trade/` 亦为 HEAD/RETURN;`sendMerchantOffers` 走自行发包路径见 AGENTS.md)
可达性:条件可达——需被注入的村民交易方法内部抛异常(vanilla 方法,概率低;其它模组的 mixin 注入其中即可能)。最小复现:临时在 `tryMoveItems` 中触发一次异常(仅测试),随后**任意玩家**打开任意村民交易界面:绿宝石费用显示/结算为星币且打 8 折。
修法:把 `begin/end` 改为在 mixin 外层用 `try/finally`(或给 ThreadLocal 记为 `AutoCloseable` 并在 `try (var s = EmeraldDiceTrade.begin(...))` 内调用被注入方法);并在 `MerchantOfferMixin` 的守卫里补一条"CONTEXT 玩家 == 当前交易玩家"的身份确认。无需用户定夺口径。
严重度:medium
工作区状态:两版本 EmeraldDiceTrade.java 与 `mixin/trade/*` **未在改动列表中**。
```

```text
ID:H-C9
核验:修正(单线程假设成立,但不是"没有风险")
现象:`DiceCombatEvents.aoeProcessing`(包级可见,可直接被外部赋值)与 `counterDepth` 是**普通 static**,非 volatile、非 ThreadLocal。所有写者/读者都在服务端主线程(SpellDamageRegistry 的 AOE 注入 + DiceCombatEvents),`aoeProcessing` 的置位**有 try/finally**。假设成立;但一旦有任何客户端线程路径读它(1.21.1 客户端也会跑 `LivingIncomingDamageEvent` 的本地预测),该闸门会被误判 ⇒ 本地骰战结算被跳过。本次未找到客户端写者/读者。
顺序依赖:置位(溅射前)→ 内部 `hurt` → 复位。契约:同线程、成对、不可重入(嵌套溅射靠单一布尔闸门而非深度)。
证据:
  A DiceCombatEvents.java:134 `    static boolean aoeProcessing = false;`
  A DiceCombatEvents.java:143 `    private static int counterDepth = 0;`
  A DiceCombatEvents.java:200 `        if (aoeProcessing || counterDepth > 0) return;`
  A SpellDamageRegistry.java:256-264 `                DiceCombatEvents.aoeProcessing = true;` / `                try {` … `                } finally { DiceCombatEvents.aoeProcessing = false; }`(第二处 373-381 同形)
  B DiceCombatEvents.java:130 / 139 / 196 / SpellDamageRegistry.java:256-263、374-381 同文
可达性:不可达(作为缺陷)。判定为"单线程契约依赖",无可复现的玩家可见后果。
修法:降为设计提示即可:`aoeProcessing` 收紧为 `private`(现为包级 `static boolean`,可由 `SpellDamageRegistry` 同包直接写,亦可能被将来同包代码误写);若要跨线程安全用 `ThreadLocal<Boolean>`(注意 ThreadLocal 不能跨线程传递,本场景同线程,无需改)。
严重度:info
工作区状态:**两版本 DiceCombatEvents.java / SpellDamageRegistry.java 已在并发改动列表中**(DiceCombatEvents 已改,SpellDamageRegistry 未列);此处结论基于基线。
```

```text
ID:H-C10
核验:证实(低危)
现象:两个"全局内部性标志"用单个 static boolean 表示:`EffectTimerGuard.forcedRemoval` 与 `ModEffectRemoval.internal`。二者都由 try/finally 保护(同线程安全),但**不可重入**:窗口内若发生**另一次、针对别的实体**的同类移除,也会被当作"内部移除"而放行 ⇒ 外部清除保护(牛奶/`/effect clear`)在嵌套窗口内失效。
顺序依赖:置位 → `removeEffect` → 事件拦截器读标志 → 复位。契约:标志必须只覆盖"本次移除";反转(改成显式传参/带实体维度)后嵌套不再误放行。
证据:
  A EffectTimerGuard.java:165-172 `    private static void forceRemove(Player player, Holder<MobEffect> effect) { forcedRemoval = true; try { player.removeEffect(effect); } finally { forcedRemoval = false; } }`;读取点 A:60-61 `    public static boolean isForcedRemoval() { return forcedRemoval; }`
  A ModEffectRemoval.java:16 / 27-35 `    private static boolean internal = false;` … `        internal = true; try { player.removeEffect(effect); } finally { internal = false; }`
  B EffectTimerGuard.java:40 / 165-172;ModEffectRemoval.java:15 / 27-35(同文)
可达性:可达但需构造嵌套(例如本模组的内部移除内部又触发另一玩家的移除拦截路径);本次未找到这样的嵌套链 ⇒ 未见实际后果。
修法:改为"按 (实体,效果) 的短期白名单集合"或把标志传成参数;至少加注释说明不可重入。无需用户定夺。
严重度:low
工作区状态:两版本 EffectTimerGuard.java / ModEffectRemoval.java **未在改动列表中**。
```

```text
ID:H-C11
核验:证实(仅观感)
现象:客户端 actionbar 管理器把消息与结束 tick 放在**静态字段**,且 `endTick` 以 `mc.level.getGameTime()` 为基准,却**没有会话/世界切换清理**。换到 gameTime 更小的世界(新建世界/回标题再进)后,`endTick - gameTime` 巨大 ⇒ 上一条 actionbar 消息**永久冻结在屏幕上**(直到出现新消息)。
顺序依赖:show(记消息+tick)→ render(读剩余);契约:时间基准必须与渲染时的 level 同纪元。
证据:
  A ActionBarManager.java:14-15 `    private static Component message;` / `    private static long endTick;`
  A ActionBarManager.java:21-25 `    public static void show(Component msg, int ticks) { message = msg; int capped = ...; endTick = currentTick() + capped; }`
  A ActionBarManager.java:39-43 `        long remaining = endTick - mc.level.getGameTime(); if (remaining <= 0) { message = null; return; }`
  A ActionBarManager.java:35-37 `        if (mc.level == null || mc.font == null) { message = null; return; }`(仅在 render 被调用时清;标题界面不渲染 Gui ⇒ 不清)
  B ActionBarManager.java:14-15 / 23 / 51 / 39-47 同文(结构等同,行号不同)
可达性:可达,最小复现:单人世界 A(已过数天)触发任一模组 actionbar 提示(如未装骰子时下蹲右键立牌 → `msg.astral_dice.need_dice`)→ 立刻退出到标题 → 新建世界 B(gameTime≈0):该提示文字仍显示且**不淡出**。
修法:在 `ClientPlayerNetworkEvent.LoggingOut`(forge 已有 `ClientSessionEvents` 可复用)清 `message=null`;1.21.1 需新增一个等价的客户端会话清理订阅器。
严重度:low
工作区状态:两版本 ActionBarManager.java **未在改动列表中**;forge `client/ClientSessionEvents.java` 已存在但只清 `ClientAstralData`。
```

```text
ID:H-C12
核验:证伪(自清理,非缺陷)
现象:`ClientDamageNumbers.activeNumbers`(静态 Map<entityId,FloatingNumber>)与 `EffectCardUseGuard.playedDuringHold`(静态 boolean)每客户端 tick 自清理/复位(`ClientTickHandler.onClientTick` → `ClientDamageNumbers.tick()` + `EffectCardUseGuard.onClientTick()`;后者在 `keyUse` 未按下时立刻复位)。跨世界残留窗口 ≤ 40 tick 且实体 id 复用时最多显示一个"幽灵跳字";无持久泄漏。
顺序依赖:tick() 每帧递减;`playedDuringHold` 每 tick 视按键复位。
证据:
  A ClientDamageNumbers.java:9-10 `    private static final Map<Integer, FloatingNumber> activeNumbers = Maps.newHashMap();` / `    private static final int DURATION = 40;`;20-29 tick 内 `it.remove()`
  A EffectCardUseGuard.java:28 `    private static boolean playedDuringHold;`;38-44 `    public static void onClientTick() { ... if (!minecraft.options.keyUse.isDown()) { playedDuringHold = false; } }`
  B ClientDamageNumbers.java:9-10 / 20-29;EffectCardUseGuard.java:28 / 38-44(forge:`ClientTickHandler.java:17-21` 调用两者)
可达性:不可达(缺陷)。
修法:无需。
严重度:info
工作区状态:两版本上述文件 **未在改动列表中**。
```

```text
ID:H-C13
核验:证伪(有界/自清理)
现象:另三个静态容器有明确的有界或自清理机制,不构成泄漏:
  ① `WaystoneWarpCompat.PENDING`(Map<UUID,Long>):TTL=200 tick,且每次查询/写入/tick 都 `removeIf(值 < now)`;`registered` 为一次性注册标志。
  ② `MarkManager.PENDING_DECAY`(List,持实体引用):每个服务端 tick **复制后立即 clear** ⇒ 存活 ≤ 1 tick(暂停期间停滞但量极小)。
  ③ `RailgunBolts.RAILGUN_BOLTS`:`Collections.newSetFromMap(new WeakHashMap<>())` ⇒ 闪电被 GC 后条目自动消失,且不反向强引用实体。
证据:
  A WaystoneWarpCompat.java:31 `    private static final long PENDING_TTL_TICKS = 200;`;34 `    private static final Map<UUID, Long> PENDING = new HashMap<>();`;35 `    private static boolean registered = false;`;86 `        PENDING.entrySet().removeIf(e -> e.getValue() < now);`
  B WaystoneWarpCompat.java:31/34/35/86 同文
  A MarkManager.java:30 / 92-94 `        if (PENDING_DECAY.isEmpty()) return; List<PendingDecay> pending = new ArrayList<>(PENDING_DECAY); PENDING_DECAY.clear();`
  B MarkManager.java:29 / 91-93 同文
  A RailgunBolts.java:22-23 `    private static final Set<LightningBolt> RAILGUN_BOLTS = Collections.newSetFromMap(new WeakHashMap<>());`
  B RailgunBolts.java:22-23 同文
可达性:不可达(作为泄漏)。
修法:无需。可选手册化:若将来 `PENDING_DECAY` 里要放"跨 tick 才处理"的条目,当前的"每 tick 全清"会成为静默丢弃点。
严重度:info
工作区状态:三文件两版本 **均未在改动列表中**。
```

```text
ID:H-C14
核验:证实(仅设计登记,无玩家可见后果)
现象:`GameplayConstants` 有 13 个 **非 final 的 public static** 字段(`GIVE_GUIDE_BOOK_ON_FIRST_JOIN`、`EVENT_APPLY_MC_TEAM/FTB/OPAC`、`SIGN_ACTIVE_COOLDOWN_SECONDS/TICKS`、`SKILL_WAIT_SECONDS`、`JASMINE_MAX_BONUS`、`LULU_ACTIVE_RANGE`、`PADMAN_REFRESH_SECONDS`、`PARUNAN_PASSIVE_INTERVAL_SECONDS`、`ACTIONBAR_DURATION_TICKS/FADE_TICKS`、`DICE_BLESSING_DURATION_SECONDS/TICKS`、`CURSED_SWORD_BONUS_MAX`)。写者仅**静态块**:从 `ModCommonConfig` 读入一次(类初始化)。运行期无写者 ⇒ 无数据竞争;但它们是"全局可变单例配置",改动需重开 JVM。
顺序依赖:类初始化(配置读取)→ 所有读取点。契约:所有读取必须发生在类初始化之后(由 `ModCommonConfig` 在模组构造期加载保证)。
证据:
  A GameplayConstants.java:23 `    public static boolean GIVE_GUIDE_BOOK_ON_FIRST_JOIN = true;`
  A GameplayConstants.java:94-101 `        GIVE_GUIDE_BOOK_ON_FIRST_JOIN = ModCommonConfig.GIVE_GUIDE_BOOK_ON_FIRST_JOIN.get();` … `        ACTIONBAR_FADE_TICKS = ModCommonConfig.ACTIONBAR_FADE_TICKS.get();`
  B GameplayConstants.java:23/37/39/41/43/45/47/49/51/53/55/60/62/65/69 同文(静态块同形)
  全仓 grep `GameplayConstants\.\w+\s*=`(赋值)→ 0 命中 ⇒ 运行期无写者(除静态块)
可达性:不可达(作为缺陷)。
修法:可加 `final` 并把配置读取收进一个 `init()`(或至少在类注释写明"仅静态块写入")。若保留"可热改"意图,则需 `volatile`。
严重度:info
工作区状态:两版本 GameplayConstants.java **未在改动列表中**。
```

```text
ID:H-C15
核验:证实(ThreadLocal 使用正确)
现象:三个 ThreadLocal 的成对使用均正确:`DamageStackSanitizer.DEPTHS`(`ThreadLocal.withInitial(ArrayDeque::new)`,弹空后 `remove()`——注释明确"不在工作线程上长期保留空容器");`DamageEffectCardHandler.APPLYING_TRUE_BONUS`(置位/复位在 try/finally 内);`EmeraldDiceTrade` 的两个见 H-C8(唯一有风险者)。ThreadLocal 本身不构成跨玩家串扰(线程隔离),但**服务端所有玩家共用一个主线程** ⇒ ThreadLocal 只做"同线程窗口"隔离,不做玩家隔离。
证据:
  A fixes/DamageStackSanitizer.java:54 `    private static final ThreadLocal<Deque<Integer>> DEPTHS = ThreadLocal.withInitial(ArrayDeque::new);`;96-105 `        final Deque<Integer> depths = DEPTHS.get();` … `            DEPTHS.remove(); // 不在工作线程上长期保留空容器`
  B 同文件同实现(`fixes/DamageStackSanitizer.java`)
  A event/DamageEffectCardHandler.java:29 `    private static final ThreadLocal<Boolean> APPLYING_TRUE_BONUS = ThreadLocal.withInitial(() -> Boolean.FALSE);`;62-68 `        if (bonus > 0 && !APPLYING_TRUE_BONUS.get()) { APPLYING_TRUE_BONUS.set(true); try { target.hurt(...) } finally { APPLYING_TRUE_BONUS.set(false); } }`
  B event/DamageEffectCardHandler.java:26 / 59-65 同文
可达性:不可达(作为缺陷)。
修法:无需。
严重度:info
工作区状态:两版本这两文件 **未在改动列表中**。
```

```text
ID:H-C17
核验:证实(该文件不在基线内,属并发进行中的改动,仍需按 H1 判据登记)
现象:**新增未跟踪文件** `combat/PlayerHostilityTracker.java`(两版本各一份)引入静态表 `HOSTILE_ATTACKERS`(受害者 UUID → 攻击者 UUID 集合)。清理点齐全:玩家死亡(LOWEST)、死亡克隆、**退出服务器**;但**没有世界切换/服务端停止清理** ⇒ 单人"退出到标题"不卸载类,表项跨世界存活:在 A 世界被攻击过的玩家,进入**新建世界 B** 后仍被当作"主动攻击过我"的敌对目标(例如依赖 `HostileTargets.isHostile(victim, attacker)` 的溅射/AOE 会把 B 世界里的该玩家当敌对),而 B 世界内并未发生任何攻击。注释自称"服务器重启即清空,与死亡清除同一口径"——单人退出到标题 ≠ 重启,该断言不覆盖本场景。表为普通 `HashMap`,仅服务端主线程读写(与 H-C4 同一先例),无串扰;`forget()` 为 O(表大小) 遍历,规模可控。
顺序依赖:伤害事件写入 → 敌对判定读取;清理 = 死亡/克隆/登出。契约:敌对立场应随"世界会话"作废;反转(加世界切换清理)只影响"跨世界继承敌对关系"这一非预期行为。
证据:
  A(neoforge-1.21.1,工作区文件,基线**不存在**;下列为工作区当前原文)
    `    /** 受害者 UUID → 「主动攻击过该受害者」的玩家 UUID 集合 */`
    `    private static final Map<UUID, Set<UUID>> HOSTILE_ATTACKERS = new HashMap<>();`
    `    @SubscribeEvent public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) { forget(event.getEntity().getUUID()); }`
    `    private static void forget(UUID uuid) { if (uuid == null) return; HOSTILE_ATTACKERS.remove(uuid); for (Set<UUID> attackers : HOSTILE_ATTACKERS.values()) { attackers.remove(uuid); } }`
  B(forge-1.20.1,工作区文件)同源同形(仅事件包名不同:`net.minecraftforge.event.entity.living.LivingDamageEvent`)
  基线核对:`git ls-tree -r d48a529 | grep PlayerHostilityTracker` → 无;`git status` → `?? forge-1.20.1/.../combat/PlayerHostilityTracker.java`、`?? neoforge-1.21.1/.../combat/PlayerHostilityTracker.java`
可达性:可达,最小复现(单人):世界 A 中玩家 B 攻击玩家 A → 退出到标题 → 新建世界 B → A 与 B 同处,检查依赖"敌对玩家"的效果(如大当家溅射/电击手套 AOE 是否波及 B,或任何 `isHostile(victim,attacker)` 分支)在 B 未发生攻击的情况下已把 B 视为敌对。
修法:在 `ServerStoppedEvent`/`LevelEvent.Unload` 或"进入新世界"时 `HOSTILE_ATTACKERS.clear()`;或把表挂到 `ServerLevel`/服务端实例而非 static。因该文件仍在改动中,**修法需与正在改这一块的进程协调**(本报告只读,不改)。
严重度:medium
工作区状态:两版本均为**新增未跟踪文件**(进行中的改动)。
```

---

# H2 客户端缓存与会话清理

```text
ID:H-C18
核验:修正(任务假设只对 1.20.1 成立,且该侧已被两层机制覆盖;1.21.1 结构性不适用)
现象:
  ① **B(forge-1.20.1)**:客户端附件同步是手写 shim,缓存是 **`ClientAstralData` 的静态 `CompoundTag`**(不随客户端玩家实体重建)。服务端"缺键即不下发"本身确实会造成"显示上一会话旧值";实现方已用**两层**覆盖:登出即 `clear()`(ClientSessionEvents),以及登录/重生/切维度时对**全部 28 个 synced 键下发显式默认值**(`ModNetwork.syncSnapshot` + `AttachedDataKey.defaultRawTag()`);单键写入时若服务端 tag 为 null 也下发默认值。⇒ 清单 H2 假设的缺陷**在当前基线上不可复现**。
  ② **A(neoforge-1.21.1)**:**不存在**该客户端缓存类(`component/ClientAstralData.java` 仅存在于 forge 侧),读取走 `player.getData(...)`,数据挂在**实体实例**上;登录/重生/切维度会新建客户端玩家实体 ⇒ 缓存随会话重建。并且 NeoForge **会同步"删除"**:`AttachmentSync.syncUpdate` 在 `getExistingDataOrNull(type)==null` 时写 `writeBoolean(false)`,客户端 `receiveSyncedDataAttachments` 据此 `holder.attachments.remove(type)` ⇒ 缺键 ≡ 复位,而非"保留旧值"。唯一"只发存在的键"之处是初始快照 `syncInitialAttachments`(只遍历 `holder.attachments`),但它作用于**新实体**的空表,无残留可留。
  ③ **残留风险(仅 B,未核验可达性)**:shim 的 `get(LivingEntity holder)` 对**任何** holder 都返回同一个全局缓存 ⇒ 客户端读取"别的玩家/实体"的 synced 键会拿到**本地玩家**的值。1.21.1 的 `getData` 是逐实体的。已逐个核对客户端读取点:`NancyLuClientEvents.onRenderPlayer` 有 `if (player != Minecraft.getInstance().player) return;` 前置守卫,不会误伤他人渲染;`client` 包内 grep `ModAttachments.` → 0 命中。**其余跨实体读取点未穷尽核验**。
顺序依赖:服务端写入 → 同步包 → 客户端缓存;契约:客户端缓存必须"每会话重建 + 缺键即默认值"。B 的两层机制互为兜底(注释亦如此声明),去掉任一层仍大致可用。
证据:
  B ClientAstralData.java:11 `    private static CompoundTag cache = new CompoundTag();`
  B ClientAstralData.java:16-22 `    public static <T> T get(AttachedDataKey<T> key) { if (!cache.contains(key.name)) { return key.defaultValue.get(); } ... }`(缺键 → 默认值)
  B client/ClientSessionEvents.java:25-28 `    @SubscribeEvent public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) { ClientAstralData.clear(); }`
  B network/ModNetwork.java:92-104 `    public static void syncSnapshot(ServerPlayer player, List<AttachedDataKey<?>> keys) { ... if (tag == null) { tag = key.defaultRawTag(); } ... }`
  B network/ModNetwork.java:73-84 `        if (tag == null) { tag = key.defaultRawTag(); }`(单键同步同样下发默认值)
  B component/ModCapabilities.java:43-61 三处 `ModAttachments.sendSyncSnapshot(sp)`(LoggedIn / Respawn / ChangedDimension)
  B component/AttachedDataKey.java:47-50 `    public T get(LivingEntity holder) { if (holder.level().isClientSide()) { return ClientAstralData.get(this); } ... }`
  B component/ModAttachments.java:815-852 `sendSyncSnapshot` + `syncedKeys()` 28 个键(懒初始化静态表)
  A component/ModAttachments.java:24-28(示例)`            ATTACHMENTS.register("player_starlight", () -> AttachmentType.builder(() -> 0).serialize(Codec.INT).sync(ByteBufCodecs.INT).build());`
  A 依赖源码 `neoforge-21.1.235-sources.jar!/net/neoforged/neoforge/attachment/AttachmentSync.java`
    `        var existingData = holder.getExistingDataOrNull(type);`
    `        if (existingData != null) { buf.writeBoolean(true); type.syncHandler.write(buf, holder.getData(type), false); } else { buf.writeBoolean(false); }`
    以及 `syncInitialAttachments`:`        for (var attachment : holder.attachments.keySet()) { anySyncableAttachment = anySyncableAttachment | attachment.syncHandler != null; }`(只遍历**已存在**的附件 → 即任务所指的"只下发存在的键",但作用于新建实体的空表)
    `receiveSyncedDataAttachments`:`                if (result == null) { if (holder.attachments != null) { holder.attachments.remove(type); } }`
  A:无 `component/ClientAstralData.java`;`client/` 目录无 `ClientSessionEvents.java`(A 的 client 目录:ActionBarManager/ClientDamageNumbers/ClientTickHandler/EffectCardUseGuard/EnderDieTotemAnimator/KeyBindingSetup/ModClientEvents/NancyLuClientEvents)
可达性:
  ① 可达但已被覆盖——最小验证:单人会话 1 用效果牌把出牌数/冷却打出来(tooltip 可见),退出到标题 → 会话 2 进另一世界 → 立刻看 tooltip:出牌数/冷却应为默认值(快照兜底生效)。若观察到旧值,则兜底失效(基线已核实不会)。
  ② 1.21.1 侧同步骤应表现一致(逐实体重建)。
  ③ (仅 B)可达性未核验——最可能的验证方式:两名玩家,客户端玩家 A 处于"骇客隐身"中,观察 B 的盔甲/手持是否被抑制(`NancyLuClientEvents` 已守卫,预期**不**被抑制);再对其它"客户端读取他人 synced 键"的功能做同样核对。
修法:
  ① 无需修(现状已是"能修的都修了");建议把 `syncSnapshot` 的键表与 `ModAttachments.SYNCED_KEYS` 的懒初始化改为不可变初始化,避免将来漏键。
  ② 1.21.1 无需修,但**建议补一个 `ClientSessionEvents` 等价物**专门清 H-C11 的 `ActionBarManager.message/endTick`(A 侧现无任何客户端会话清理入口)。
  ③ 若确认存在跨实体读取:把 shim 的客户端缓存改为 `Map<UUID, CompoundTag>`(或把 `get()` 的客户端分支限为本地玩家并对其余 holder 走默认值),并在 `AttachmentSyncMessage` 里带上目标 UUID。**是否需要改口径(是否允许客户端读他人附件)需用户先定夺。**
严重度:① info(已被覆盖);③ medium 但**未核验可达性**,标为待核验项而非已证实缺陷。
工作区状态:B 侧 `ClientAstralData.java` / `ClientSessionEvents.java` / `ModNetwork.java` / `ModCapabilities.java` / `AttachedDataKey.java` **均未在改动列表中**;A 侧 `component/ModAttachments.java` **未在改动列表中**。
```

---

# 全局单例写入(对 `ItemStack.EMPTY` 的 `.set(...)`)

先给**可写性**的证据链(这是本节全部结论的地基):
- Curios 1.21.1 `ICurioItem.onUnequip(SlotContext, ItemStack newStack, ItemStack stack)`;`ItemizedCurioCapability.onUnequip(SlotContext, ItemStack)` 的字节码为 `curioItem.onUnequip(arg1, arg2, this.getStack())` ⇒ **第 2 参 = Curios 传入的 newStack,第 3 参 = 该 ICurio 挂载的栈(= 被卸下那件)**。
- `CuriosEventHandler` 的 tick 路径(1.21.1 与 1.20.1 结构一致,已逐指令核对):`var14 = getStackInSlot(i)`(**当前/新**栈)、`var15/16 = getPreviousStackInSlot(i)`(**旧**栈);`if (!ItemStack.matches(var14, var15))` → `getCurio(var15).ifPresent(onUnequip(slotContext, var14))` → 随后 `if (!var14.isEmpty())` 才走 `onEquip(slotContext, var15)`。真正卸下时槽位内容就是 `ItemStack.EMPTY`(`ItemStackHandler(int size)` = `NonNullList.withSize(size, ItemStack.EMPTY)` ⇒ 槽内是**同一个 EMPTY 单例**)。另 `CurioStacksHandler.lambda$loseStacks$9` 显式传 `ItemStack.EMPTY`。
- 1.21.1 `ItemStack.set` (sources `ItemStack.java:716-718`) = `this.components.set(component, value)`;`ItemStack.EMPTY` 的 components 是 `new PatchedDataComponentMap(DataComponentMap.EMPTY)`(`ItemStack.java:271-274`),`PatchedDataComponentMap.set` 先 `ensureMapOwnership()`(copyOnWrite → 复制为可变 map)**不抛异常** ⇒ **对 EMPTY 的 set 会成功并永久污染全局单例**。
- 1.20.1 shim `ItemDataKey.set(stack,v)` = `stack.getOrCreateTag().put(name, tag)`(`ItemDataKey.java:85-92`);1.20.1 `ItemStack.getOrCreateTag()` = `if (this.tag == null) this.setTag(new CompoundTag())`、`setTag` 只做 `this.tag = t` + 对 AIR 的 no-op 校验(`ItemStack.java:552-558 / 595-605`)⇒ **同样成功写入 EMPTY 单例**(`isEmpty()` 仍为 true,但 `getTag()` 已非 null,`ItemDataKey.has(EMPTY)` 变为真)。

```text
ID:H-C19
核验:证实
现象:三个立牌的"卸下清理"把组件归零写到**第 2 参(newStack = 真正卸下时的 `ItemStack.EMPTY`)**上,而不是写到被卸下的那件立牌上。后果两件:
  (a) **全局单例被永久污染**:`ItemStack.EMPTY` 上被写入 `jasmine_atk_bonus=0`、`jasmine_def_bonus=0`、`padman_atk_bonus=0`、`padman_def_bonus=0`、`padman_last_refresh=0`、`misaki_sign_stacks=0`。1.20.1 侧是给 EMPTY 的 NBT 根挂键(`hasTag()` 由 false 变 true),1.21.1 侧是给 EMPTY 的 `PatchedDataComponentMap` 打 patch。**同一 JVM 内永久生效、跨世界、跨登录**;`isEmpty()` 仍为 true,所以不会有"空槽突然有物品"的可见现象,但它是**全局可变状态**,任何"对空栈 set"的路径都会写进同一个对象(1.21.1 的 `getComponents()` 对空栈返回 `DataComponentMap.EMPTY`,所以污染对"遍历组件"不可见,但对 `get()/getOrDefault()` **可见**)。
  (b) **被卸下的立牌自己没被清**:`clearSignData(player, stack)` 拿到的 stack 是 EMPTY ⇒ 目标物品的攻/防点数与剑气压层保留;重新装备后旧累计值继续生效(与"卸下即清零"的设计契约相反,可形成"摘掉再戴上保住累计值"的稳定操作)。
顺序依赖:真卸下时 Curios 先判"槽位已空"→ 传 EMPTY → 清理写错对象。契约:`onUnequip` 的第 2 参是**新占用者**(可能为 EMPTY),第 3 参才是被卸下者;把两者搞反即同时制造 (a)(b)。
证据(基线行号;`clearSignData` 接收的实参即 `BaseSignItem.onUnequip` 的第 2 参):
  A BaseSignItem.java:165-179 基线原文(节选)
    `    public void onUnequip(SlotContext slotContext, ItemStack stack, ItemStack prevStack) {`
    `        boolean stillInSlot = CuriosApi.getCuriosInventory(player)`
    `                .map(h -> slotContext.index() < h.getSlots()`
    `                        && !h.getStacks().getStackInSlot(slotContext.index()).isEmpty()`
    `                        && h.getStacks().getStackInSlot(slotContext.index()).getItem() == stack.getItem())`  ← stack=EMPTY ⇒ getItem()=AIR
    `        if (stillInSlot) { return; }`
    `        clearSignData(player, stack);`   ← 传入 EMPTY
  B BaseSignItem.java:166-179 同文(声明 166,`clearSignData(player, stack)` 在 179)
  A PadmanSignItem.java:47-54 `    protected void clearSignData(Player player, ItemStack stack) {` … `        stack.set(ModDataComponents.PADMAN_ATK_BONUS.get(), 0);` / `        stack.set(ModDataComponents.PADMAN_DEF_BONUS.get(), 0);` / `        stack.set(ModDataComponents.PADMAN_LAST_REFRESH.get(), 0L);`
  B PadmanSignItem.java:47-54 `    protected void clearSignData(Player player, ItemStack stack) {` … `        ModDataComponents.PADMAN_ATK_BONUS.set(stack, 0);` / `        ModDataComponents.PADMAN_DEF_BONUS.set(stack, 0);` / `        ModDataComponents.PADMAN_LAST_REFRESH.set(stack, 0L);`(行 50/51/52)
  A JasmineSignItem.java:48-56 `        stack.set(ModDataComponents.JASMINE_ATK_BONUS.get(), 0);`(51)`        stack.set(ModDataComponents.JASMINE_DEF_BONUS.get(), 0);`(52)
  B JasmineSignItem.java:49-56 `        ModDataComponents.JASMINE_ATK_BONUS.set(stack, 0);`(52)`        ModDataComponents.JASMINE_DEF_BONUS.set(stack, 0);`(53)
  A MisakiSignItem.java:41-45 `        stack.set(ModDataComponents.MISAKI_SIGN_STACKS.get(), 0);`(44)
  B MisakiSignItem.java:42-46 `        ModDataComponents.MISAKI_SIGN_STACKS.set(stack, 0);`(45)
  Curios 侧`newStack` 语义(字节码):`top/theillusivec4/curios/common/capability/ItemizedCurioCapability.onUnequip` → `ICurioItem.onUnequip(slotContext, arg2, getStack())`;`CuriosEventHandler.lambda$tick$22` → `ICurio.onUnequip(slotContext, arg1)`;`CurioStacksHandler.lambda$loseStacks$9` → `ICurio.onUnequip(slotContext, ItemStack.EMPTY)`
  可写性:`neoforge-21.1.235-sources.jar!/net/minecraft/world/item/ItemStack.java:271-274,716-718` 与 `PatchedDataComponentMap.java:66-78,129-133`;`forge-1.20.1-47.4.10-sources.jar!/net/minecraft/world/item/ItemStack.java:552-558,595-605` + 本项目 `component/ItemDataKey.java:85-92`
  ⚠️ 相关"调用方是否本来就该传第 2 参"的两处**证据**:
    - 同一个仓库里 `DiceCurioItem.onUnequip` 的注释已明确"第 2 参 newStack = 将要占用槽位的栈,第 3 参 stack = 被卸下的那件骰子"(A DiceCurioItem.java:83-85 / B:83-85),并用第 3 参调用 `tryRemoveChipBonus` ⇒ 立牌侧写成第 2 参是**同一仓库内自相矛盾**;
    - 而 `BaseChipItem` 走 `CurioSlotUtil.runOnRealUnequip(slotContext, curio, ...)` 且 `isRealUnequip` 用 `槽位物品.getItem() == stack.getItem()` 比对(A CurioSlotUtil.java:87-94;B:88-95)⇒ 在真卸下时该判据因 stack=EMPTY 而恒为 false/true 的形态**只对"重载场景"误判**,对筹码清理恰好仍能通过(所以筹码侧没有 (b));但立牌侧绕过了该判据直接用 `clearSignData(player, stack)`,于是 (a)(b) 全中。
可达性:可达,最小复现(两版本皆可):
  ① **EMPTY 污染(需运行时/dump 或间接观察)**:装电流剑/筹码无关;装**上班族立牌** → 卸下 → 之后在同一 JVM 内任意位置获取一个空槽物品栈(如 `getStackInSlot` 返回的 EMPTY)并读 `PADMAN_ATK_BONUS`:基线实现下 `hasTag()`/`has(PADMAN_ATK_BONUS)` 已为 true(1.20.1 用 NBT 检查最直观:`/data get entity` 查不到,需在 `build/` 里写一段测试或断点;javadoc 级证据已是"必然写入"。)
  ② **(b) 被卸下立牌未清零**:装上班族立牌 → 用主动技能("真的生气了")把攻/防置 4/4 或等被动刷出非 0 值(tooltip 可见)→ 卸下立牌 → 观察物品 tooltip/再次装备后的加成:基线实现下旧值仍在(应归零)。
  ③ 同法适用 Jasmine(走到累计非 0 再卸下)与 Misaki(剑气层数)。
修法:把 `BaseSignItem.onUnequip` 的第 2 参改名为 `newStack`、第 3 参为 `removedStack`,并改为 `clearSignData(player, removedStack)`;同时把 `stillInSlot` 判据改成**只依据 Curios 两参**的"有意卸下"判定,并加 `removedStack == null || removedStack.isEmpty() → return`(从源头杜绝"对 EMPTY 调 set")。各立牌的 `clearSignData` 无需改动。**与并发进行中的改动协调**:审计结束时工作区**两版本** `CurioSlotUtil.java` / `BaseSignItem.java` / `BaseChipItem.java` 已在改动列表中,且工作区已出现 `CurioSlotUtil.isIntentionalUnequip(newStack, removedStack)` 与 `BaseSignItem.onUnequip(..., ItemStack newStack, ItemStack stack)` + `clearSignData(player, stack)`(第 3 参)——即本修法方向正在被实施(其注释亦自述"从源头杜绝对 ItemStack.EMPTY 调 set"),**修法条目以工作区为准即可,本报告不复述**。
严重度:high
工作区状态:**改动中**——`CurioSlotUtil.java`、`BaseSignItem.java`、`BaseChipItem.java`(两版本)均已修改;`PadmanSignItem.java` / `JasmineSignItem.java` / `MisakiSignItem.java`(两版本)**未改动**(其 `clearSignData` 本身正确,只要实参修好即可)。
```

```text
ID:H-C20
核验:证实
现象:`DiceCurioItem.onEquip` 的初始化判据也用了第 2 参(= Curios 传入的 **previous stack**;把骰子装进**空槽**时它就是 `ItemStack.EMPTY`)⇒ `curio.set(WEAPON_ENHANCEMENT, WeaponEnhancement.EMPTY)` 写到 **`ItemStack.EMPTY`** 上:①再次污染全局单例(写入的是**语义非空**的 `WeaponEnhancement.EMPTY` 值,比 H-C19 的 0 更"实质");②**本应初始化的那颗骰子没被初始化**——`diceStack.has(WEAPON_ENHANCEMENT)` 首次装备后仍为 false,后续全靠 `getOrDefault(..., WeaponEnhancement.EMPTY)` 兜底。由于污染后 `EMPTY.has(WEAPON_ENHANCEMENT)` 变为 true,该分支从第二次起会被跳过(即"污染只发生一次",但一旦发生就永久存在,且**空栈会被读成携带武器强化**——任何"空栈 + `getOrDefault(WEAPON_ENHANCEMENT, null)`"的写法都会从 null 变成非 null,详见下"顺序依赖")。
顺序依赖:装备(进入空槽)→ onEquip(param2=旧栈=EMPTY, param3=新骰子)。契约:第 2 参是旧占用者,不能当"刚装上的那件"。反转(用第 3 参)后:骰子被正确初始化、EMPTY 不再被写。
证据:
  A DiceCurioItem.java:67-70 基线原文
    `    public void onEquip(SlotContext slotContext, ItemStack curio, ItemStack prevStack) {`
    `        if (!curio.has(ModDataComponents.WEAPON_ENHANCEMENT.get())) {`
    `            curio.set(ModDataComponents.WEAPON_ENHANCEMENT.get(), WeaponEnhancement.EMPTY);`
  B DiceCurioItem.java:68-71 基线原文
    `    public void onEquip(SlotContext slotContext, ItemStack curio, ItemStack prevStack) {`
    `        if (!ModDataComponents.WEAPON_ENHANCEMENT.has(curio)) {`
    `            ModDataComponents.WEAPON_ENHANCEMENT.set(curio,  WeaponEnhancement.EMPTY);`
  同文件对照(A DiceCurioItem.java:81-87 / B:82-88):`onUnequip` 已用**第 3 参**,并注明"第 2 参 = 将要占用槽位的栈"⇒ 同一方法对里 param2/param3 的用法自相矛盾。
  空栈读成非 null 的落点(需"空栈"实参才会命中,当前调用方均为 curio 栈,故**未核验**):A DiceCombatEvents.java:855 `        WeaponEnhancement enh = dice.getOrDefault(ModDataComponents.WEAPON_ENHANCEMENT.get(), null);` / `        if (enh == null) return;`(第 827 行 `onDiceBlessingExpired`,其 `dice` 来自 `findFirstCurio(...)` ⇒ 必非空 ⇒ 当前不可达);B DiceCombatEvents.java 同形。
可达性:可达(污染部分),最小复现:装一颗任意骰子(空 dice 槽)→ 卸下 → 再装;期间用断点/临时测试读 `ItemStack.EMPTY.get(WEAPON_ENHANCEMENT)`:基线实现下非 null,且被卸下的骰子自身从未被写入该组件。玩家可见后果需经由"空栈被当作带强化"的读取点,**当前未找到可达读取点**(故本条的玩家可见面为"未核验",污染事实为"证实")。
修法:改为 `ItemStack dice = prevStack;` → 实际应写第 3 参(即 `ItemStack equipped`,Curios 的 `getStack()`),并加 `if (equipped == null || equipped.isEmpty()) return;`。**与 H-C19 同一处修复面**,建议一并处理;工作区两版本 `DiceCurioItem.java` **已在改动列表中**,请以工作区最终形态复核本条是否已修。
严重度:medium(污染事实 high,可见后果未核验 ⇒ 取 medium)
工作区状态:**改动中**(两版本 DiceCurioItem.java 已修改)。
```

```text
ID:H-C21
核验:证伪(不存在写入,但存在**永不清理**)
现象:筹码基类把 EMPTY 传给了 `onChipUnequip`,但**遍历全部 21 个 `onChipUnequip` 实现,没有任何一处对传入 stack 写组件**(它们只操作玩家级 Capability/附件,或用自己的 static 表),故不构成 EMPTY 写入。副作用仅:筹码的"自身物品数据清理"若将来被写进 `onChipUnequip(stack)` 会立刻踩 H-C19 的坑。
顺序依赖:同 H-C19(第 2 参)。
证据:
  A BaseChipItem.java:79-84 基线原文
    `    public void onUnequip(SlotContext slotContext, ItemStack curio, ItemStack newStack) {`
    `        if (!(slotContext.entity() instanceof Player player)) return;`
    `        if (player.level().isClientSide()) return;`
    `        CurioSlotUtil.runOnRealUnequip(slotContext, curio, player, p -> onChipUnequip(p, curio));`   ← 第 2 参
  B BaseChipItem.java:80-85 同文(`CurioSlotUtil.runOnRealUnequip(slotContext, curio, player, p -> onChipUnequip(p, curio));` 在 84)
  A CurioSlotUtil.java:87-94 基线 `    public static boolean isRealUnequip(SlotContext slotContext, ItemStack stack, LivingEntity entity) {` … `&& h.getStacks().getStackInSlot(slotContext.index()).getItem() == stack.getItem())` … `    public static void runOnRealUnequip(...)`
  B CurioSlotUtil.java:88-95 同文
  全部 onChipUnequip 实现(两版本同名同形):Adrenaline / Candy / CursedSword / EightSidedDice / ElectricSword / EnergyRecycler / Flashlight / MagicQuiver / MagicTome / MedkitComplete / MedkitEmergency / PiggyBank / PrimordialCore / RevengeHalberd / Satellite / StarCoinHammer(±更多);对传入 stack 的 `.set(`/`.remove(`/`getOrCreateTag()` 命中数 = **0**(grep `\.set\((stack|curio|...)` 与 `\b(stack|curio)\.(set|remove|getOrCreateTag|setTag)\(` 均未命中这些方法体)。
可达性:不可达(作为"写 EMPTY")。
修法:与 H-C19 一起改成 `runOnIntentionalUnequip(newStack, removedStack, ...)`,并统一把"被卸下的那件"作为回调实参(工作区已如此改)。**建议加一条静态检查/注释契约**:任何 `onXxxUnequip` 的 stack 参数都必须非空才可用。
严重度:info
工作区状态:**改动中**(两版本 BaseChipItem.java、CurioSlotUtil.java 已修改)。
```

---

## 单侧差异(仅存在于一个子项目的东西)

| 项 | 仅存在于 | 说明 |
|---|---|---|
| `component/ClientAstralData.java`(静态客户端附件缓存) | **仅 B(forge-1.20.1)** | A 用 NeoForge 原生逐实体附件,无对应类 |
| `client/ClientSessionEvents.java`(登出清缓存) | **仅 B** | A 无任何客户端会话清理入口(⇒ H-C11 的 `ActionBarManager` 静态消息在 A 侧也无人清) |
| `network/ModNetwork.syncSnapshot` / `AttachedDataKey.defaultRawTag()` / `ModAttachments.sendSyncSnapshot` / `ModCapabilities` 的三个快照钩子 | **仅 B** | A 由 NeoForge `AttachmentSync` 承担(附删除同步) |
| `component/ItemDataKey.java`(数据组件 shim,走 NBT) | **仅 B** | A 用真 `DataComponentType`;这也是 H-C19/20 在两侧"写入方式不同、结果相同"的原因 |
| `combat/PlayerHostilityTracker.java` | **两版本各一份,但都不在基线**(新增未跟踪) | 见 H-C17;不是单侧差异,是"改动中" |
| 本次 H1 其余 20 个候选项 | **两版本一一对应** | 逐条 grep 比对,除上述外**未发现单侧独有的 static 可变状态**;`SYNCED_KEYS`/附件清单的行号偏移是唯一系统性差别 |

---

## 未核验

1. **H-C18③ 的真实可达性**:`AttachedDataKey.get(holder)` 在客户端对**任意** holder 都返回本地玩家缓存(仅 B)。已核对 `client/NancyLuClientEvents`(有 `player != Minecraft.getInstance().player` 守卫)与 `client` 包内 `ModAttachments.` 的 0 命中,但**未穷尽**所有客户端读取路径(尤其 `event/ModTooltipHandler` 的客户端分支与 GUI 层);因此"是否会误显他人状态"标为**未核验**。
2. **H-C19/20 的玩家可见面**:"EMPTY 被写入"是字节码+官方源码级证实;**但**这些被写入的值(三个 0、一个 `WeaponEnhancement.EMPTY`)与各自默认值语义相同,且 1.21.1 的 `ItemStack.getComponents()` 对空栈返回 `DataComponentMap.EMPTY`,故"具体哪个玩法数值被污染读出"未找到可达读取点 ⇒ 影响面标记为**未核验**,未据此升级严重度。
3. **H-C8 的嵌套/跨玩家变体**:`begin()` 对"无绿宝石骰子的玩家"提前 return 而 `end()` 仍递减 DEPTH,是否会提前摘掉**另一玩家**的上下文 —— 需要确认四个 begin/end 注入点之间是否真的存在"跨玩家嵌套"(单线程内同一窗口不挂起 ⇒ 直觉上不可能,但未逐路径核验)。
4. **H-C1 的"是否属有意设计"**:立牌被动"每 300 米"是否**有意**允许瞬移灌入(与 EnergyRecycler 的闸门存在明确不一致,像漏移植而非有意)—— 需用户定夺口径。
5. **运行时确认类**:本审计未启动游戏、未跑 `runClient`、未做内存 dump;所有"可达性/最小复现"均为**代码级推演**,不是实测结论。`RailgunStrikeScheduler` 的"旧 level 不可回收"(H-C3)与 `ItemStack.EMPTY` 的运行时污染(H-C19/20)建议由 `scripts/test` 实机流程或 JVM 堆快照确认。
6. **`EffectCardPeriod` 的 `registerFixedSource/registerTemporarySource` 反复调用**:本次只确认其静态块/注册入口,未核验是否存在"周期重入导致重复注册"的调用路径(读者遍历这些表计算上限)⇒ 标记**未核验**(低优先)。
