# S3 击杀/事件/发牌链

- 仓库:`F:\MCProject\astral_dice_multiloader` 分支 `multi-1.20.1-1.21.1`,基线 `d48a529`(`git rev-parse --verify HEAD` = `d48a529ba5fcf14610dc7b7383a10780dbe49168`)。本次为**只读审计**,未修改任何源码/配置(仅新增本文件)。补充说明:`docs/` 已被 `.gitignore:58`(`docs/`)整体忽略,故审计产出文件不会出现在 `git status` 中、也不会污染工作区;`git status --porcelain` 目前唯一未跟踪项 `temp_audit_s6/` 属其它切片,与本切片无关。
- 路径缩写(下文所有 `file:line` 均相对这两个根):
  - **A** = `neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/`
  - **B** = `forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/`
- 切片范围:一次击杀(`LivingDeathEvent`)触发的全部处理器、顺序、重复计数、击杀归属/分发口径、调查阶段发牌链,以及这些效果之间的资源竞争。**不含**骰战伤害数值链(另见 `S2-blessing-chain.md`)。
- 结论只依据代码;**注释不作为判据**(凡代码注释与实测语义不一致处,已单独标注为"注释自称/注释与实现不符")。无法静态判定的写入第 5 节。
- 平台语义证据来源(本机反编译/源码产物,只读):
  - 原版:**A** `build/moddev/artifacts/neoforge-21.1.235-sources.jar`;**B** `build/moddev/artifacts/forge-1.20.1-47.4.10-sources.jar`
  - 事件总线:**A** `net.neoforged:bus:8.0.5` 源码桶(NEO-bus);**B** `net.minecraftforge:eventbus:6.0.5` 源码桶(FORGE-bus)(均位于 `~/.gradle/caches/modules-2/files-2.1/`)
- 优先级语义(两侧一致):`EventPriority` 枚举顺序 `HIGHEST, HIGH, NORMAL, LOW, LOWEST`(NEO-bus `net/neoforged/bus/api/EventPriority.java:34-38`,FORGE-bus `net/minecraftforge/eventbus/api/EventPriority.java:38-42`,注释 `//First to execute` / `//Last to execute`)。未标注 priority 的方法 = `NORMAL`(NEO-bus `SubscribeEvent.java:42` `EventPriority priority() default EventPriority.NORMAL;`,FORGE-bus `SubscribeEvent.java:43` 同)。
- **同优先级 = 注册顺序**(脆弱依赖,本切片多处结论依赖它):
  - 注册即追加:`ListenerList.register` → `priorities.get(priority.ordinal()).add(listener)`(NEO-bus `net/neoforged/bus/ListenerList.java:164-169`;FORGE-bus `net/minecraftforge/eventbus/ListenerList.java:265-271` → 内部 `ListenerListInst` 声明于 `:154-163`,append 在 `:268`)
  - 派发即按该数组顺序:`NEO-bus EventBus.java:356-367`(`for (; index < listeners.length; index++) listeners[index].invoke(event);`);`FORGE-bus EventBus.java:308-316`
  - 缓存按优先级枚举顺序拼接:`ListenerList.buildCache`(NEO-bus `:131-152`)、`ListenerListInst.buildCache`(FORGE-bus `:247-263`)
  - 本模组这些处理器**全部**经类级注解自动注册(`@EventBusSubscriber` / `@Mod.EventBusSubscriber`),代码中没有任何显式注册来固定顺序:1.20.1 唯一的显式注册是 `B:AstralDiceMod.java:57` `MinecraftForge.EVENT_BUS.register(com.merlinkitsune.astral_dice.event.IronSpellbooksCompat.class);`(与本切片无关)。故同优先级顺序由加载器扫描注解类的顺序决定,**不是 API 契约**。
- **取消态语义**(决定"保命后其它处理器还跑不跑"):`@SubscribeEvent` 的 `receiveCanceled` 默认 false,事件已被取消时该监听器**根本不会被调用**:
  - A:`net/neoforged/bus/SubscribeEventListener.java:46-53` `if (subInfo.receiveCanceled() || !((ICancellableEvent) event).isCanceled()) { handler.invoke(event); }`;消费者路径 `EventBus.passNotGenericFilter`(NEO-bus `EventBus.java:190-193`)+ `ConsumerEventHandler.WithPredicate.invoke`(`:45-50`)
  - B:`net/minecraftforge/eventbus/ASMEventHandler.java:65-77` `if (!event.isCancelable() || !event.isCanceled() || subInfo.receiveCanceled()) { ... handler.invoke(event); }`
  - 推论(本切片关键):HIGHEST 的取消会让**其后所有**默认监听器全部跳过;`NORMAL` 的取消只会让**排在它之后**的 `NORMAL/LOW/LOWEST` 监听器跳过,排在它之前、且同为 `NORMAL` 的监听器**已经执行过了**。
- 原版 `LivingDeathEvent` 派发点与次数(证据):
  - A `net/minecraft/world/entity/LivingEntity.java:1408-1410` `public void die(DamageSource damageSource) { if (CommonHooks.onLivingDeath(this, damageSource)) return; if (!this.isRemoved() && !this.dead) {`
  - B `LivingEntity.java:1342-1344` 同构(`ForgeHooks.onLivingDeath`)
  - **事件在 `!isRemoved() && !dead` 守卫之前派发** → 同一实体若再次进入 `die()`,事件会**再次派发**。
  - 但 `hurt()` 顶层有 `else if (this.isDeadOrDying()) return false;`(A `LivingEntity.java:1147-1148`;B `:1094-1095`),且模组自身**从不**调用 `die()/kill()`(全仓 grep `\.die(`/`\.kill()`:0 命中)→ 一次击杀在模组侧只有一条派发路径。二次派发的唯一原版入口是 `handleEntityEvent((byte)3)`(A `:1908-1911`、B `:1784-1787`,且 `!(this instanceof Player)`),其为服务端自派发不可达性见第 5 节。
  - `isDeadOrDying()` = `getHealth() <= 0.0F`(A `LivingEntity.java:1134-1136`;B `:1081-1083`)——注意它**不**看 `dead` 标志。

---

## 1. 参与方与订阅点清单

### 1.1 `LivingDeathEvent` 订阅者(两版本集合完全相同,共 10 个方法 / 10 个类)

| 类 | 事件 | 优先级 | 1.21.1 file:line | 1.20.1 file:line |
|---|---|---|---|---|
| `event/ChipDamageHandler`(安全气囊兜底) | `LivingDeathEvent` | **HIGHEST**(显式) | A:`ChipDamageHandler.java:75-76` | B:`ChipDamageHandler.java:75-76` |
| `event/EnderDiceHandler`(末影骰伪图腾) | `LivingDeathEvent` | **NORMAL**(无 priority) | A:`EnderDiceHandler.java:146-147` | B:`EnderDiceHandler.java:147-148` |
| `event/PlayerLifecycleHandler`(玩家死亡清理) | `LivingDeathEvent` | **NORMAL**(无 priority) | A:`PlayerLifecycleHandler.java:114-115` | B:`PlayerLifecycleHandler.java:111-112` |
| `item/InvestigationEventUtil`(调查阶段触发) | `LivingDeathEvent` | **NORMAL**(无 priority) | A:`InvestigationEventUtil.java:107-108` | B:`InvestigationEventUtil.java:104-105` |
| `item/sign/BonnieSignItem`(立牌击杀钩子总分发) | `LivingDeathEvent` | **NORMAL**(无 priority) | A:`BonnieSignItem.java:137-138` | B:`BonnieSignItem.java:136-137` |
| `item/sign/HaiqingSignItem`(虚弱印记击杀奖励) | `LivingDeathEvent` | **NORMAL**(无 priority) | A:`HaiqingSignItem.java:131-132` | B:`HaiqingSignItem.java:130-131` |
| `item/chip/CursedSwordChipItem` | `LivingDeathEvent` | **NORMAL**(无 priority) | A:`CursedSwordChipItem.java:118-119` | B:`CursedSwordChipItem.java:116-117` |
| `item/chip/ElectricSwordChipItem` | `LivingDeathEvent` | **NORMAL**(无 priority) | A:`ElectricSwordChipItem.java:75-76` | B:`ElectricSwordChipItem.java:75-76` |
| `item/chip/SatelliteChipItem` | `LivingDeathEvent` | **NORMAL**(无 priority) | A:`SatelliteChipItem.java:98-99` | B:`SatelliteChipItem.java:99-100` |
| `item/chip/SmartWatchChipItem` | `LivingDeathEvent` | **NORMAL**(无 priority) | A:`SmartWatchChipItem.java:47-48` | B:`SmartWatchChipItem.java:47-48` |

- "无 priority"的判据:全仓 `EventPriority\.` 检索(A/B 各 10 处命中)后,逐个核对这些类里带显式 priority 的 `@SubscribeEvent` **分别属于哪个事件**:A:`ChipDamageHandler.java:75`(HIGHEST,**本切片:死亡**)、A:`ChipDamageHandler.java:45`(LOWEST,伤害)、A:`EnderDiceHandler.java:134`(HIGH,**伤害**非死亡)、A:`PlayerLifecycleHandler.java:184`(LOWEST,`PlayerEvent.Clone` 非死亡)、A:`ModEffectEvents.java:110`(HIGH,`MobEffectEvent.Remove` 非死亡),另 5 处(`DiceCombatEvents.java:894/940` LOW、`FateGuidanceCardItem.java:85` LOWEST、`ObsidianDiceHandler.java:29` HIGH、`PaparaSignItem.java:56` LOWEST)均不在本切片的死亡链上。→ 10 个 `LivingDeathEvent` 处理器中**只有 `ChipDamageHandler` 用的是显式 HIGHEST,其余 9 个全部取默认 `NORMAL`**(B 侧逐条对应:B:`ChipDamageHandler.java:75`(HIGHEST,死亡)与 `:45`(LOWEST,伤害)、B:`EnderDiceHandler.java:135`(HIGH,伤害)、B:`PlayerLifecycleHandler.java:180`(LOWEST,Clone)、B:`ModEffectEvents.java:106`(HIGH,Remove)、B:`DiceCombatEvents.java:888/934`(LOW)、B:`FateGuidanceCardItem.java:86/94`(HIGHEST/LOWEST)、B:`PaparaSignItem.java:56`(LOWEST);B 的 `ObsidianDiceHandler` **没有**显式 priority,与 A 的 `ObsidianDiceHandler.java:29`(HIGH)不一致——该差异属伤害链,见 `S2-blessing-chain.md`)。
- 自动注册的注解位置(便于人工核对扫描顺序):A `ChipDamageHandler.java:39`、`EnderDiceHandler.java:42`、`PlayerLifecycleHandler.java:111`、`InvestigationEventUtil.java:35`、`BonnieSignItem.java:38`、`HaiqingSignItem.java:38`、`CursedSwordChipItem.java:33`、`ElectricSwordChipItem.java:26`、`SatelliteChipItem.java:26`、`SmartWatchChipItem.java:23`;B 对应 `@Mod.EventBusSubscriber` 在 `:39`、`:43`、`:108`、`:33`、`:37`、`:37`、`:34`、`:26`、`:27`、`:23`。
- 其中 **9 个靠注册顺序**、只有 1 个(HIGHEST)靠优先级——见第 2 节。

### 1.2 击杀链上的非 `LivingDeathEvent` 参与方(同一链路的调用点)

| 类 | 入口/事件 | 优先级 | 1.21.1 file:line | 1.20.1 file:line |
|---|---|---|---|---|
| `event/AstralEventSystem` | 被立牌直接调用(事件统一附加效果) | — | A:`AstralEventSystem.java:22-31`、`:34-39`、`:57-83` | B:`AstralEventSystem.java:22-31`、`:34-39`、`:57-83` |
| `event/EventTargetCollector` | 被 `AstralEventSystem` / `InvestigationEventUtil` / `RandomCardHandler` 调用 | — | A:`EventTargetCollector.java:24-49` | B:`EventTargetCollector.java:24-49` |
| `item/sign/BaseSignItem` | `invokeKillHooks`(立牌 `onKill` 总分发) | — | A:`BaseSignItem.java:202-212` | B:`BaseSignItem.java:203-213` |
| `item/MarkManager` | 标记层数读写;`MobEffectEvent.Expired` / `ServerTickEvent.Post`(待补写) | NORMAL | A:`MarkManager.java:69-87`、`:90-102` | B:`MarkManager.java:67-68`、`:88-89` |
| `event/ModEffectEvents` | `MobEffectEvent.Remove`(拦截 astral 效果移除)/ `Added` | **HIGH** / NORMAL | A:`ModEffectEvents.java:110-131`、`:136-144`、`:149-159` | B:`ModEffectEvents.java:106-127`、`:131-140`、`:145-155` |
| `event/EffectTimerGuard` | 计时守卫 `apply/tick/forget/clear` | — | A:`:68-74`、`:95-147`、`:150-157`、`:160-163` | B:`:65-71`、`:95-147`、`:149-156`、`:159-162` |
| `item/chip/VitaminPillChipItem` | `giveCard`(发牌唯一入口之一) | — | A:`VitaminPillChipItem.java:38-47` | B:`VitaminPillChipItem.java:39-48` |
| `item/card/RandomCardHandler` | `giveCardTo` → `VitaminPillChipItem.giveCard` | — | A:`RandomCardHandler.java:184-189` | B:`RandomCardHandler.java:184-189` |
| `combat/DiceCombatEvents` | 立牌主动施加(虚弱印记/隐匿调查);`LivingChangeTargetEvent`(调查阶段禁索敌) | NORMAL | A:`:228-262`、`:785-824`(禁索敌 `:805-810`)、`:1006-1020` | B:`:224-258`、`:780-819`(`:800-805`)、`:999-1013` |
| `event/LootInjectionHandler` | `LivingDropsEvent`(掉星盘)/ `LootTableLoadEvent`(宝箱) | NORMAL | A:`LootInjectionHandler.java:110-126`、`:129-167` | B:`LootInjectionHandler.java:106-123`、`:125-162` |
| `item/chip/FlashlightChipItem` | 攻击时 +星光(非击杀事件) | — | A:`FlashlightChipItem.java:50-64` | B:`FlashlightChipItem.java:50-64` |
| `item/chip/AdrenalineChipItem` | 受击闪避(**不是** `LivingDeathEvent` 订阅者,仅上游) | NORMAL | A:`AdrenalineChipItem.java:86-102` | B:`AdrenalineChipItem.java:87-105` |
| `component/DeathPreservedBonuses` | 死亡暂存/重生回写 | — | A:`:39-45`、`:48-59` | B:`:39-45`、`:48-59` |
| 立牌栏容量(`stand`) | Curios 槽位定义 | — | A:`src/main/resources/data/astral_dice/curios/slots/stand.json:1-6`(`"size": 1`) | B:`AstralDiceMod.java:101-103`(`.size(1)`) |

> 立牌栏 `size = 1` 是本节一个重要事实:`BaseSignItem.invokeKillHooks` 遍历的是"已装备的全部 `BaseSignItem`",槽位只有 1 个 + `canEquip` 拒绝重复装备(A:`BaseSignItem.java:31-42`),因此**当前配置下不可能**出现"同一立牌的 `onKill` 被调用两次"(这条否证了 `AstralEventSystem` 去重注释里的前提,详见 S3-C3)。

---

## 2. 触发顺序

一次 `LivingDeathEvent` 派发时,上表 10 个监听器**全部**会按"优先级 → 同优先级注册顺序"被调用一次(每个方法内部各自有早期 return 守卫,不满足条件的什么都不做)。下面按场景给出**实际生效**的顺序。

### 场景 1:玩家(近战/任意来源)击杀一个敌对生物,击杀者未佩戴任何立牌/筹码

1. `ChipDamageHandler.onLivingDeath`(**HIGHEST**,A:`:75`)——仅当被杀者是玩家时才有意义,此处 `!(entity instanceof Player)` 直接 return(A:`:79`)。**依据:显式 HIGHEST 保证最先执行。**

### 场景 2:玩家死亡(被杀者=玩家)且未佩戴气囊/末影骰子

1. `ChipDamageHandler.onLivingDeath`(HIGHEST,A:`:76`)→ A:`:81` `AirbagChipItem.tryNegateFatal(player)` 不满足(未佩戴/冷却/充能不足)→ 不取消,继续。
   依据 A:`:79-85`;`tryNegateFatal` 本体 A:`AirbagChipItem.java:45-58`(消耗 6 充能 + 置 1:00 冷却,A:`:51-52`)。
2. **其余 9 个 NORMAL 监听器:顺序 = 注册顺序,未定义**(见下"顺序未定义"小节)。其中:
   - `EnderDiceHandler.onLivingDeath`(A:`:147`)若满足条件则 `event.setCanceled(true)` + `setHealth(1.0F)`(A:`:157-159`),此后**它之后的**监听器全部被总线跳过(取消态过滤)。
   - `PlayerLifecycleHandler.onPlayerDeathClearEffects`(A:`:115`)执行死亡清理:`ChargeManager.preserveOnDeath`(A:`:121`)、`DeathPreservedBonuses.preserveOnDeath`(A:`:124`)、`DiceCurioItem.removeGlassDiceOnDeath`(A:`:126` → `DiceCurioItem.java:176-189`,把骰子槽置 `ItemStack.EMPTY` 并收缩筹码栏)、`EffectTimerGuard.clear`(A:`:129`)、以及 A:`:131-178` 的一整串效果/附件复位。
   - 其余击杀奖励类监听器对"被杀者是玩家"基本 no-op:`SmartWatchChipItem`/`ElectricSwordChipItem`/`CursedSwordChipItem`/`SatelliteChipItem` 都要求 `HostileTargets.isHostile(被杀者)`(`HostileTargets.java:32-36` 只认 `Enemy`/被激怒的 `NeutralMob`,玩家永不成立);`BonnieSignItem.onKill` 额外要求 `!(killed instanceof Player)`(A:`BonnieSignItem.java:117`);但 `HaiqingSignItem.onWeakMarkKill` 与 `InvestigationEventUtil.onUndercoverInvestigationKill` **不要求**敌对,故对玩家目标生效(见场景 4)。

### 场景 3:玩家击杀带"虚弱印记"的目标(占星师主动已施加)

1. HIGHEST 气囊(被杀者为玩家时)/ NORMAL 顺序未定。
2. `HaiqingSignItem.onWeakMarkKill`(NORMAL,A:`:132`)条件:`target.hasEffect(WEAK_MARK)`(A:`:135`)、`ModAttachments.getWeakMarkSource(target)` 非空(A:`:136-137`)、`target.level().getPlayerByUUID(...)` 在线(A:`:138`)→ `grantWeakMarkKillReward(applier, killer)`(A:`:141`)。
3. 发放内容与归属(A:`HaiqingSignItem.java:108-129`):**3 星币 → 释放印记的占星师**(A:`:110-113`);**「命运的指引」→ 击杀者**(A:`:114-117`,且 `ExclusiveCardUtil.setOwner(card, killer)`);击杀者为非玩家时为 `null`,**不发牌**(A:`:140` `event.getSource().getEntity() instanceof Player k ? k : null`)。
4. 该处理器**没有**去重、**没有** `isCanceled()` 守卫、**没有**"发放时是否仍佩戴占星师立牌"的复核(见 S3-C4/C5)。

### 场景 4:玩家击杀带"隐匿调查"的目标(→ 调查阶段 → rin 发牌 → 隐身/禁索敌)

1. `InvestigationEventUtil.onUndercoverInvestigationKill`(NORMAL,A:`:108`)条件:`target.hasEffect(UNDERCOVER_INVESTIGATION)`(A:`:111`)、`event.getSource().getEntity() instanceof Player killer`(A:`:112`)、`getUndercoverSource(target)` 非空且施加者在线(A:`:113-116`)→ `MarkManager.getLevel(target)`(A:`:117`)→ `triggerByKill(killer, applier, markLevel)`(A:`:118`)。
2. `triggerByKill`(A:`InvestigationEventUtil.java:41-58`):
   - `stage = ModAttachments.getInvestigationStage(applier)`(A:`:44`)
   - `killer == applier` 分支(A:`:45-52`):`applyStageEffects(killer, null, stage, markLevel)` → `setInvestigationStage(applier, min(stage+1,4))`(A:`:48`)→ `AstralEventSystem.triggerInvestigationEvent(killer)`(A:`:49`)→ ActionBar(A:`:50`)→ return
   - 否则(A:`:53-57`):`applyStageEffects(killer, applier, ...)` → 推进施加者进度(A:`:55`)→ `triggerInvestigationEvent(killer)`(A:`:56`)→ ActionBar(A:`:57`)
3. `applyStageEffects`(A:`:61-95`):时长按 stage 1/2/3/4 = 300/400/600/1200 tick(A:`:62-67`);受者 = 触发者 + 施加者(A:`:68-72`);`stage>=4` 时再加 `EventTargetCollector.collectTeamPlayers(self)`(A:`:76`)与(附近 64 格存在 boss 时)32 格内玩家(A:`:81-88`);对每个受者 `EffectTimerGuard.apply(INVISIBILITY, duration, 0, false, true)`(A:`:91`)+ `addEffect(INVESTIGATION_BONUS, duration, stage, false, false, true)`(A:`:93`)。
4. `AstralEventSystem.triggerInvestigationEvent`(A:`:29-31`)→ `onEventTriggered(triggerer, "investigation")`(A:`:22-26`):
   - `applySignBuffs`(A:`:34-39`):触发者佩戴大侦探立牌 → **+3 星币**(`holdsSign` 判定 A:`:36`,正是"发放时复核装备"的正例)
   - `applyRinSignPassive(triggerer, "investigation")`(A:`:57-83`):对 `serverLevel.players()` 中每个佩戴调查员立牌者(A:`:63-64`),满足"是触发者本人 / 32 格内 / 团队(无队伍时为全服同维度在线玩家)"之一(A:`:66-69`)即发 1 张活体书页(A:`:78-80`),发牌前按 `triggerer.getUUID()+"|"+eventId` 做 2 tick 去重(A:`:62`、`:71-76`)
5. 禁索敌:`DiceCombatEvents.onLivingChangeTarget`(NORMAL,A:`:786`)在"目标是玩家 且 同时有 `INVISIBILITY` 与 `INVESTIGATION_BONUS`"时 `event.setCanceled(true)`(A:`:806-810`)——**这是本切片中"隐身不被索敌"的唯一实现点**(骇客的完全隐身是另一条 A:`:800-804`)。

### 场景 5:一次 AoE 同 tick 击杀多个目标

同一 tick 内每个目标的死亡各自派发一次 `LivingDeathEvent` → 上述链**每个目标各走一遍**。这带来两个可观察后果:
- `CursedSwordChipItem.onKill` 每个赐福周期只 +1(A:`CursedSwordChipItem.java:89-90` 的 `cursedSwordBlessingTriggered` 互斥),故多杀不叠加;
- 多次 `triggerInvestigationEvent(killer)` 使用**同一个** `eventId="investigation"`,2 tick 去重会把第 2..N 次的书页吞掉(见 S3-C3)。

### 场景 6:玩家击杀带标记的 ≥20 血敌对生物(秘密侦探被动)

1. `BonnieSignItem.onBonnieKill`(NORMAL,A:`:138`):要求击杀者为玩家(A:`:141`)→ `BaseSignItem.invokeKillHooks(killer, target)`(A:`:142`)。
2. `invokeKillHooks`(A:`BaseSignItem.java:202-212`)遍历击杀者全部已装备立牌并调用 `sign.onKill(killer, killed)`——**全仓唯一调用点**(grep `invokeKillHooks`:A/B 各只有 `BonnieSignItem` 的调用 + `BaseSignItem` 的定义)。
3. `BonnieSignItem.onKill`(A:`:114-122`):`MarkManager.getLevel(killed) > 0 && !(killed instanceof Player) && HostileTargets.isHostile(killed) && killed.getMaxHealth() >= 20` → 随机一枚攻击牌 `VitaminPillChipItem.giveCard`(A:`:120`、`:125-133`)。

> 结构风险(非本切片的 bug,列入第 5 节):**整个"立牌击杀钩子"体系的唯一分发点长在 `BonnieSignItem` 的一个 `LivingDeathEvent` 处理器里**,即"其它立牌的击杀被动"隐式依赖秘密侦探立牌这个类被加载/注册;该处理器也没有 `isCanceled()` 守卫。

### 场景 7:一次击杀同时满足多个奖励条件(资源叠加)

同一击杀链上可能与"击杀者"有关的效果(各自独立判定,互不排斥):

| 效果 | 触发条件 | 发放入口 | 1.21.1 file:line |
|---|---|---|---|
| 占星师 3 星币 | 目标带虚弱印记(施加者在场) | `grantWeakMarkKillReward` | A:`HaiqingSignItem.java:110-113` |
| 「命运的指引」1 张 | 同上,且击杀者为玩家 | 同上 | A:`HaiqingSignItem.java:114-117` |
| 随机攻击牌 1 张 | 目标带标记 + 敌对 + ≥20 血 + 击杀者佩 bonnie | `BonnieSignItem.onKill` | A:`BonnieSignItem.java:120` |
| 随机卡 1 张(阈值 10) | 击杀者佩智能手表且当前卡数 < 10 | `SmartWatchChipItem.onHostileKilled` | A:`SmartWatchChipItem.java:40-45`,阈值 A:`:26` |
| 随机效果牌 1 张 | 击杀者佩探天卫星 + 有 `orbital_strike` + 该伤害判为法伤 | `SatelliteChipItem.onRangedMagicKill` | A:`SatelliteChipItem.java:74-78`、`:99-108` |
| 充能 +2 / 10 杀 | 击杀者佩电流剑 + 敌对 | `ElectricSwordChipItem.onHostileKilled` | A:`ElectricSwordChipItem.java:61-73` |
| 攻击力 +1(每赐福一次) | 击杀者佩诅咒之剑 + 赐福中 + ≥20 血敌对 | `CursedSwordChipItem.onKill` | A:`CursedSwordChipItem.java:83-96` |
| 星盘掉落(世界掉落,无归属) | 任何 `Monster` 0.3% / 凋灵·监守者 100% | `LootInjectionHandler.onLivingDrops` | A:`LootInjectionHandler.java:111-126` |
| 大侦探 +3 星币(事件链) | 事件触发者本人佩 fanny | `AstralEventSystem.applySignBuffs` | A:`AstralEventSystem.java:34-39` |
| 活体书页(事件链) | 佩戴 rin 且满足本人/32 格/团队 | `applyRinSignPassive` | A:`AstralEventSystem.java:57-83` |

**顺序未定义小节**:上述效果里,除 HIGHEST 的气囊外,**全部是 NORMAL**,彼此顺序由注册顺序决定。这直接导致 S3-C1(死亡清理 vs 末影骰保命)与 S3-C6(智能手表读卡数 vs 其它同 tick 发牌)。

---

## 3. 冲突项

> 严重度口径:**blocker** = 崩溃/存档损坏;**high** = 明确的错误结果或数据丢失;**medium** = 依顺序/条件才出现的错误结果或经济/重复计数;**low** = 口径不一致、可复现但影响小。

### S3-C1 | **high** | 同优先级脆弱依赖:玩家死亡清理与末影骰"保命取消死亡"的先后完全由注册顺序决定,顺序不利时玩家被保命却已执行完整死亡清理(玻璃骰子与已装配卡牌被销毁)

- 现象:A 两处 NORMAL 处理器对同一事件做出互斥假设——`EnderDiceHandler.onLivingDeath` 会取消死亡并 `setHealth(1.0F)`,而 `PlayerLifecycleHandler.onPlayerDeathClearEffects` 把"收到未取消的死亡事件"当作真死。若后者在前者之前被调用,清理会在取消发生**之前**跑完:玻璃骰子槽被置空、全部效果与出牌轮状态被重置,随后玩家被末影骰救活 → "没死却丢了一整套状态"。
- 证据:
  - A:`event/EnderDiceHandler.java:146-147` 无 priority(=NORMAL),`:151` `if (event.isCanceled()) return;`,A:`:157-159` `event.setCanceled(true); player.setHealth(1.0F);`
  - A:`event/PlayerLifecycleHandler.java:114-115` 无 priority(=NORMAL),`:119` `if (event.isCanceled()) return;`(该守卫**只**在它排在被取消之后时才起作用)
  - A:`PlayerLifecycleHandler.java:126` `DiceCurioItem.removeGlassDiceOnDeath(player);` → A:`item/dice/DiceCurioItem.java:180-184` `diceHandler.getStacks().setStackInSlot(0, ItemStack.EMPTY); ... setChipSlotCount(player, chip, CHIP_NO_DICE_SLOTS, true)`
  - A:`PlayerLifecycleHandler.java:129` `EffectTimerGuard.clear(player);`、`:131-178`(赐福/命运指引/骰子诅咒比/出牌轮/立牌待命等整串复位)
  - B 同构:B:`EnderDiceHandler.java:147-152`、`:158-160`;B:`PlayerLifecycleHandler.java:111-116`、`:123`(removeGlassDiceOnDeath)
  - 顺序依据:NEO-bus `ListenerList.java:164-169` + `EventBus.java:356-367`;FORGE-bus `ListenerList.java:265-271` + `EventBus.java:308-316`;取消后跳过:NEO-bus `SubscribeEventListener.java:49`、FORGE-bus `ASMEventHandler.java:69`
  - 结论与顺序无关:两种注册顺序**必有一侧语义错误**——清理在前 → 状态被误清;保命在前 → 正确跳过清理。当前代码把正确性押在未定义的顺序上。
- 建议修法:给 `EnderDiceHandler.onLivingDeath` 显式 `priority = HIGH`(或给 `PlayerLifecycleHandler.onPlayerDeathClearEffects` 显式 `LOW`/`LOWEST`),用优先级而非注册顺序保证"保命判定先于死亡清理";也可在 `PlayerLifecycleHandler` 内改为"若本 tick 已被保命则跳过"(需要显式状态,不推荐)。
- 两版本:皆有。

### S3-C2 | **medium** | 末影骰保命的"清除效果"对**本模组**效果完全无效(两版本皆然):模组自己的移除拦截器把 `astral_dice:*` 的移除全数取消

- 现象:`EnderDiceHandler` 先 `setHealth(1.0F)` 再清效果,因此清效果时 `isDeadOrDying()` 已为 false;`ModEffectEvents.onModEffectRemovalPrevented` 的"死亡放行"逃生口失效,于是按命名空间把 `astral_dice:*` 的移除 `setCanceled(true)` → 赐福/命运指引/汲取/调查阶段等本模组效果在保命后**全部保留**。1.20.1 那句 `removeAllEffects()` 因此只是"清掉原版/他模组效果"。
- 证据:
  - A:`EnderDiceHandler.java:159-160` `player.setHealth(1.0F); player.removeEffectsCuredBy(...PROTECTED_BY_TOTEM);`;B:`EnderDiceHandler.java:160-161` `player.setHealth(1.0F); player.removeAllEffects();`
  - A:`event/ModEffectEvents.java:110-117`(`priority = HIGH`;`:114` `if (ModEffectRemoval.isInternal()) return;`、`:115` `if (EffectTimerGuard.isForcedRemoval()) return;`、`:117` `if (event.getEntity().isDeadOrDying()) return;`)+ `:128-130` `if (effectId.startsWith(AstralDiceMod.MODID + ":")) { event.setCanceled(true); }`
  - B:`event/ModEffectEvents.java:106-113`、`:123-126` 同构
  - 原版移除前会把 `MobEffectEvent.Remove` 交给总线、被取消就 `continue`:A `LivingEntity.java:3602-3616`(`removeEffectsCuredBy`,`!EventHooks.onEffectRemoved(...)` 才移除);B `LivingEntity.java:903-919`(`removeAllEffects`,`if (MinecraftForge.EVENT_BUS.post(new MobEffectEvent.Remove(this, effect))) continue;`)
  - "本模组效果本来应该被图腾清除"的依据:默认 cure 集合含图腾 cure——A `neoforged/common/extensions/IMobEffectExtension.java:23-28` `cures.addAll(EffectCures.DEFAULT_CURES);`、`neoforged/common/EffectCures.java:24` `DEFAULT_CURES = Set.of(MILK, PROTECTED_BY_TOTEM)`、`MobEffectInstance.java:86` `this.effect.value().fillEffectCures(this.cures, this);`;本模组 33 个效果类均**未**覆写 `fillEffectCures`(全仓 grep `fillEffectCures`:0 命中)
- 与 S2 的交叉:同仓 `S2-blessing-chain.md` 的 **S2-C16** 描述为"B 会把玩家全部状态效果一并清掉(含本模组的赐福/增益)"——按上述证据,该括号**不成立**(本模组效果被自家拦截器保住);1.20.1 与原版图腾的差异真实存在,但只作用于原版/他模组效果。建议以本条证据修正 S2-C16 的措辞。
- 建议修法:二选一并保持两版本一致——(a) 在 `EnderDiceHandler` 清效果期间临时置 `ModEffectRemoval` 内部标志(让"保命=按图腾口径清效果"真正生效);(b) 明确"保命不清本模组效果"的口径,把 `removeAllEffects()/removeEffectsCuredBy(...)` 换成显式白名单,并同步更新文案。
- 两版本:皆有(机制相同,受影响的"谁被清"不同)。

### S3-C3 | **medium** | 事件附加效果去重不对称:大侦探 +3 星币无任何去重,调查员发牌按 `(触发者,eventId)` 2 tick 去重;且同一 `eventId="investigation"` 被复用于**不同**真实事件

- 现象一(多发):同一触发者的同一事件在 2 tick 内被真实触发两次(见场景 5:AoE 同 tick 击杀 2 个"隐匿调查"目标)→ 大侦探拿 **6** 星币,而调查员只发 **1** 张书页 → 同一事件的两个副效果数量不一致。
- 现象二(少发):去重签名不含目标/事件实例,只用 `triggerer UUID + eventId`,所以上述第二次**真实**事件的书页被误吞(而非去重所声称的"重复分发")。
- 现象三(去重前提不成立):注释声称去重是为防"多立牌槽导致 `onKill` 多次调用",但立牌槽 `size = 1` 且 `canEquip` 拒绝重复装备(A:`data/astral_dice/curios/slots/stand.json:2`、A:`BaseSignItem.java:31-42`;B:`AstralDiceMod.java:102`),当前配置下不存在该重复路径 → 该守卫目前**只**产生误吞。
- 证据:
  - A:`event/AstralEventSystem.java:34-39`(`applySignBuffs` 内 `if (holdsSign(player, ModItems.FANNY_SIGN.get())) giveStarCoins(player, 3);`,**无任何签名/计数守卫**)
  - A:`AstralEventSystem.java:62` `String signature = triggerer.getUUID() + "|" + eventId;`、`:71-76`(命中签名且 `now - tick <= 2` 则 `continue`,否则写签名/时刻并发牌)
  - 固定 eventId 的两个调用点:A:`item/InvestigationEventUtil.java:49` 与 `:56`(都是 `triggerInvestigationEvent(killer)`,内部 `"investigation"`,A:`AstralEventSystem.java:30`);大侦探主动另用独立 ID A:`item/sign/FannySignItem.java:48` `onEventTriggered(player, "fanny_active")`
  - 去重附件 A:`component/ModAttachments.java:286-311`(`default ""` / `default 0L`,仅持久化、不跨玩家共享);B:`ModAttachments.java:262-282`
- 建议修法:给 `applySignBuffs` 引入与发牌同源的签名去重(或把"星币"并入同一去重键);把 `eventId` 细化为事件实例级(如 `"investigation:" + 目标 UUID + ":" + 阶段`),使不同真实事件不再互相吞;同时删除或明确落地"多槽重复分发"的守卫前提。
- 两版本:皆有。

### S3-C4 | **medium** | 5 个 `LivingDeathEvent` 处理器缺 `isCanceled()` 守卫:被保命的"死亡"仍可能推进调查阶段、发放星币与「命运的指引」

- 现象:目标带 `WEAK_MARK` 或 `UNDERCOVER_INVESTIGATION` 时(两者都可施加到**非队友玩家**),该目标受致命伤但被末影骰/气囊保命。取消者 `EnderDiceHandler` 是 NORMAL;若上述 5 个无守卫处理器在注册顺序上排在它**之前**,它们会在取消发生前先执行、看到**未取消**的事件并照常发奖:占星师拿 3 星币、击杀者拿「命运的指引」、调查阶段推进并施加隐身/调查增益 —— 而目标并没有死(同一具身体稍后真死时还会再来一轮)。若它们排在取消者之后,则被总线跳过、什么都不发。同一段代码的结果取决于注册顺序。
- 证据(缺守卫的 5 处):A:`item/InvestigationEventUtil.java:108-119`、A:`item/sign/HaiqingSignItem.java:132-143`、A:`item/sign/BonnieSignItem.java:138-143`、A:`item/chip/CursedSwordChipItem.java:119-125`、A:`item/chip/SatelliteChipItem.java:99-108`(B 对应 `InvestigationEventUtil.java:105`、`HaiqingSignItem.java:131`、`BonnieSignItem.java:137`、`CursedSwordChipItem.java:117`、`SatelliteChipItem.java:100`)
- 对照(有守卫的同类处理器,证明这是遗漏而非约定):A:`ChipDamageHandler.java:80`、A:`EnderDiceHandler.java:151`、A:`PlayerLifecycleHandler.java:119`、A:`ElectricSwordChipItem.java:77`、A:`SmartWatchChipItem.java:49`、A:`ModEffectEvents.java:152`
- 可施加到玩家的依据:A:`combat/DiceCombatEvents.java:1006-1011` `if (target instanceof Player other) { return other.getTeam() == null || other.getTeam() != player.getTeam(); }`;施加点 A:`DiceCombatEvents.java:233-234`(虚弱印记)、`:247-249`(隐匿调查)
- 顺序依赖说明:由于取消态过滤(NEO-bus `SubscribeEventListener.java:49` / FORGE-bus `ASMEventHandler.java:69`),这些处理器**只在**它们排在取消者之前时才会看到取消态 → 是否可复现取决于注册顺序,故定级 medium 而非 high。
- 建议修法:5 处统一补 `if (event.isCanceled()) return;`(与同类处理器一致),使行为不再依赖顺序。
- 两版本:皆有(守卫集合逐条一致)。

### S3-C5 | **low** | 占星师击杀奖励在"发放时"不复核装备:施加印记后卸下立牌,仍能拿到 3 星币

- 现象:`grantWeakMarkKillReward` 只按 `weak_mark_source` 记录的 UUID 给星币,不检查该玩家是否仍佩戴占星师立牌、也不检查立牌数据是否已被 `clearSignData` 清理。同链上其它击杀奖励都在发放时复核装备。
- 证据:
  - A:`item/sign/HaiqingSignItem.java:108-113`(无 `isEquipped`/`holdsSign`)、`:82-91`(`clearSignData` 只清"待命"状态与 `HAIQING_READY`,**不**清理目标身上的印记来源)
  - 对照正例:A:`event/AstralEventSystem.java:36` `if (holdsSign(player, ModItems.FANNY_SIGN.get()))`;A:`SmartWatchChipItem.java:42` `if (!isEquipped(killer)) return;`;A:`ElectricSwordChipItem.java:63`;A:`CursedSwordChipItem.java:85`;A:`SatelliteChipItem.java:76`
  - B 同构:B:`HaiqingSignItem.java:107-112`、`:81-90`
- 建议修法:在 `onWeakMarkKill`(而非 `grantWeakMarkKillReward`)里按 `source` 解析施加者后复核 `isEquipped(applier)`(或至少复核"仍佩戴占星师立牌"),并明确"卸下立牌是否应撤销已施加印记的后续奖励"。
- 两版本:皆有。

### S3-C6 | **low** | 智能手表"卡数 < 10 才补牌"在死亡事件里即时读取,与同 tick 其它发牌竞争 → 发不发取决于注册顺序

- 现象:同一次击杀里,占星师(命运指引)与秘密侦探(随机攻击牌)也在给击杀者发牌(A:`HaiqingSignItem.java:115-117`、A:`BonnieSignItem.java:120`)。若这两者先执行,智能手表读到的卡数已经 +1/+2,可能在阈值 10 的边界上**不再补牌**;反之则补牌。合并结果随注册顺序变化。
- 证据:A:`item/chip/SmartWatchChipItem.java:40-45`(`countCards(killer) >= CARD_THRESHOLD` 才 return,再 `RandomCardHandler.giveCardTo`)、`:26` `CARD_THRESHOLD = 10`、`:57-65`(只统计主背包 `getInventory().items`);发牌竞品见上一行的两处;顺序依据同 S3-C1。
- 建议修法:把阈值判定改为"本 tick 末统一结算"(或按 `LivingDropsEvent` 之后的阶段处理),避免同 tick 竞争;或在文档中固化"阈值以本次击杀开始时的卡数为准"。
- 两版本:皆有。

### S3-C7 | **low** | 击杀归属口径不统一:奖励类一律要求"玩家击杀",而星盘掉落完全无归属(任何来源的 `Monster`/凋灵/监守者死亡都掉)

- 现象:同一次"击杀"里,玩家归属的奖励(`getSource().getEntity() instanceof Player`)与环境/生物击杀的掉落并存;`LootInjectionHandler.onLivingDrops` 不检查击杀者、也不检查 `event.isCanceled()`。
- 证据:A:`event/LootInjectionHandler.java:111-126`(仅按实体类型 + 0.3% 概率注入星盘,无击杀者判定);对照 A:`SmartWatchChipItem.java:52`、`ElectricSwordChipItem.java:80`、`SatelliteChipItem.java:103`、`CursedSwordChipItem.java:123`、`HaiqingSignItem.java:140`、`BonnieSignItem.java:141`、`InvestigationEventUtil.java:112`(统一 `event.getSource().getEntity() instanceof Player`;`ChipDamageHandler`/`PlayerLifecycleHandler` 处理的是死亡方而非击杀方,故不在对照内)
- 补充(一致性正例):全部 8 个击杀奖励处理器**都**用 `getSource().getEntity()`,**无人**使用 `getKillCredit()`/`getDirectEntity()` → 宠物/陷阱/间接来源击杀对全部效果一律不发放,归属口径在"击杀类效果"之间是一致的。
- 建议修法:若希望掉落也归属玩家,改为在 `LivingDropsEvent` 中按 `event.getSource().getEntity()` 判定(或明确记录"掉落无归属"为设计口径)。
- 两版本:皆有。

### S3-C8 | **low** | "未加入队伍时按全服在线玩家"实际只是"同维度在线玩家"

- 现象:`collectTeamPlayers` 与 `applyRinSignPassive` 都基于 `ServerLevel.players()`,跨维度(主世界/下界/末地)的在线玩家既不被算作队友,也不在"全服"直发范围里;文档口径"全服"与实现不符。
- 证据:A:`event/EventTargetCollector.java:39-47` `if (triggerer.level() instanceof ServerLevel serverLevel) { for (ServerPlayer sp : serverLevel.players()) ... }`;A:`event/AstralEventSystem.java:58-63`(`serverLevel.players()` 遍历发牌);B:`EventTargetCollector.java:39-47`、B:`AstralEventSystem.java:58-63`
- 建议修法:改为遍历 `server.getAllLevels()`(或 `MinecraftServer.getPlayerList().getPlayers()`)以符合"全服"口径;若维持同维度,则修正文案与文档。
- 两版本:皆有。

---

## 4. 跨版本对等性差异

1. **击杀链订阅者集合与守卫集合完全对等**:两版本各 10 个 `LivingDeathEvent` 处理器,类名/方法/条件/守卫逐条一致(见 §1.1 与 S3-C4 的证据行);`AstralEventSystem`、`EventTargetCollector`、`InvestigationEventUtil`、`MarkManager`、`BaseSignItem.invokeKillHooks`、`SmartWatchChipItem`、`ElectricSwordChipItem`、`CursedSwordChipItem`、`SatelliteChipItem` 的逻辑逐条对等(差异仅为 `CuriosCompat`/`ModNetwork`/`Holder.get()` 的平台写法,例 A:`AstralEventSystem.java:86` vs B:`AstralEventSystem.java:86` `CuriosCompat.getCuriosInventory`)。
2. **末影骰保命的效果清除范围不同**(已在 `S2-C16` 记录):A:`EnderDiceHandler.java:160` `removeEffectsCuredBy(EffectCures.PROTECTED_BY_TOTEM)` ↔ B:`EnderDiceHandler.java:161` `removeAllEffects()`。两侧都与其平台原版图腾写法一致(A 原版 `LivingEntity.java:1329`;B 原版 `:1270`),因此这是**平台继承差异**;但按 S3-C2 的证据,该差异对**本模组效果**无实际影响(都被自家拦截器保住),只影响原版/他模组效果。建议与 S2-C16 合并处理并修正其措辞。
3. **`MobEffectEvent.Expired` 可取消性不同**:A 实现 `ICancellableEvent`(A `net/neoforged/neoforge/event/entity/living/MobEffectEvent.java:212`),B **不可取消**(B `net/minecraftforge/event/entity/living/MobEffectEvent.java:156-159`,`// This event is not Cancelable.`)。本切片相关处理器(`MarkManager.onMarkExpired`、`HaiqingSignItem.onWeakMarkExpired`)在两版本中**都没有**调用 `setCanceled`(全仓 `setCanceled` 命中清单中无 Expired 事件)→ 目前无功能差异,但任何未来"取消到期以保证链式逻辑"的改动都会只在 1.21.1 可行。
4. **`MobEffectEvent.Remove` 可取消性一致**(A `MobEffectEvent.java:43`、B `:46-47` 均 `@Cancelable`/`ICancellableEvent`)→ `ModEffectEvents` 的拦截逻辑两版本等价(见 S3-C2 证据行)。
5. **死亡事件的取消语义一致**:`LivingDeathEvent` 两版本都可取消,且 `die()` 均在 `!isRemoved() && !dead` 守卫**之前**派发(A `LivingEntity.java:1409-1410` ↔ B `:1343-1344`)。
6. **立牌栏容量一致**:A 数据驱动 `curios/slots/stand.json` `"size": 1`;B IMC `.size(1)`(B:`AstralDiceMod.java:102`)。
7. **不属于本切片的既有差异**(仅记录,勿计入本切片结论):1.20.1 `ChipDamageHandler.onLivingDamage(LivingDamageEvent)`(B:`:46`)↔ 1.21.1 `LivingDamageEvent.Pre`(A:`:46`);1.20.1 `FateGuidanceCardItem` 用 `LivingAttackEvent(HIGHEST)+LivingHurtEvent(LOWEST)`(B:`:86-96`)↔ 1.21.1 单 `LOWEST`(A:`FateGuidanceCardItem.java:85`)——两条都在骰战/伤害链(S2 范围)。

---

## 5. 未能判定 / 需人工确认

1. **同优先级的真实注册顺序未实测**:`EventPriority` 全为 NORMAL 的 9 个 `LivingDeathEvent` 处理器之间的实际执行顺序,由加载器扫描 `@EventBusSubscriber`/`@Mod.EventBusSubscriber` 类的顺序决定(本仓库代码未固定它,见头部"同优先级=注册顺序"证据)。**无法静态判定**。建议人工在 dev/`run` 环境用一次"玩家被末影骰保命"的实测+临时调试日志确认 `EnderDiceHandler.onLivingDeath` 与 `PlayerLifecycleHandler.onPlayerDeathClearEffects` 的相对顺序(S3-C1 的两种结果可肉眼区分:保命后玻璃骰子是否消失/赐福是否被清)。本次只读审计不修改源码,故未插入任何探针。
2. **`handleEntityEvent((byte)3)` 是否可能在服务端对同一非玩家实体二次触发 `die()`**(从而二次派发 `LivingDeathEvent`,直接命中"重复计数"):`die()` 的派发点在守卫之前(A:`LivingEntity.java:1409-1410`、B:`:1343-1344`),该分支自身排除了玩家(A:`:1908-1911`、B:`:1784-1787`),但"服务端是否会自己调用 `handleEntityEvent(3)`"需运行时确认。若可达,则 S3-C3/C5/C6 的重复计数风险会从"顺序相关"升级为"必然"。
3. **标记层数与死亡的 tick 竞态**:`MarkManager` 的"减 1 层"补写被推迟到下一 tick(`ServerTickEvent.Post`,A:`MarkManager.java:90-102`),而击杀在 `LivingDeathEvent` 里读层数(A:`BonnieSignItem.java:116`、A:`InvestigationEventUtil.java:117`)。当目标的 `MARKED` 恰好在其死亡同一 tick 到期时,读到的层数可能是 0(旧实例已被移除、补写尚未发生)。攻守双方 tick 的相对顺序不可静态判定 → 需运行确认。
4. **无 ID 版入口 `applyRinSignPassive(Player)`(签名 `"sign_effect"`)当前无任何调用者**(A:`AstralEventSystem.java:46-48`;全仓 `applyRinSignPassive(` 检索只有该定义、`:57` 定义、`:25` 与 `:30` 通过 `onEventTriggered` 的调用)。它是"预留兼容入口"还是遗留死代码、以及是否应改为强制传 ID,需人工定夺;若未来有第二个事件类型也走该重载,2 tick 去重会在**不同类型事件之间**串扰。
5. **S3-C2 的设计意图需人工定夺**:"末影骰保命后是否应清除本模组效果"没有可据以判定的代码依据(拦截器口径 vs 图腾口径互相矛盾),两种修法(见 S3-C2 建议)会带来不同的游戏体验,需产品裁决。
6. **`stand` 槽若被外部数据包/他模组扩容到 >1**:`invokeKillHooks` 会遍历所有立牌并对每张调用一次 `onKill`,此时"同一立牌两次调用"是否可能出现取决于 Curios 的 `findCurios` 去重语义;当前 `size = 1` 下不可达,故未展开(需人工在扩容场景下复验)。
7. **注释与实现不符的两处**(仅记录,不据此下结论):
   - `AstralEventSystem` 的去重注释把动机写成"多立牌槽导致 `onKill` 多次调用",但立牌槽 `size = 1` 且禁止重复装备(§1.1 末尾),该前提在当前配置下不成立(S3-C3 现象三)。
   - `ChipDamageHandler` 类注释声称"死亡事件侧的兜底用 HIGHEST,同样早于末影骰子的死亡处理"——HIGHEST 确实早于 NORMAL 的末影骰(这点与实现一致),但**死亡清理(`PlayerLifecycleHandler`,NORMAL)与末影骰(NORMAL)之间没有任何优先级保证**;注释未覆盖这一风险(S3-C1)。
8. **本次未覆盖**:`LootTableLoadEvent` 的宝箱注入重复防护(`table.getPool("astral_dice:star_coin") != null`,A:`LootInjectionHandler.java:137`)在多数据包重载场景下的行为;以及 `RandomCardHandler` 的发牌目标收集(`collectTargets`)在"未加入队伍"分支下的完整口径(与本切片击杀链无直接交集)。
