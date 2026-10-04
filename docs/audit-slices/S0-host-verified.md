# S0 主代理独立核验的发现(不依赖子代理)

> 基线提交 `d48a529`;两版本 `neoforge-1.21.1`(NeoForge 1.21.1)/ `forge-1.20.1`(Forge 1.20.1)。
> 所有结论均由反编译源码与本仓代码核验,不是从注释或 AGENTS.md 推断。

## 0. 前置事实:两版本伤害事件的**真实**阶段(据反编译源码)

| 1.21.1 NeoForge | 1.20.1 Forge | 阶段 |
|---|---|---|
| `LivingIncomingDamageEvent`(可取消) | `LivingAttackEvent`(可取消) | **减伤前**(原始值) |
| (无对应) | `LivingHurtEvent`(可改 amount) | **护甲前**、魔法吸收前 |
| `LivingDamageEvent.Pre`(可改 amount) | `LivingDamageEvent`(可改 amount) | **所有减免之后**、写入血量之前 |

证据(反编译源码):
- 1.20.1:`forge-1.20.1-47.4.10-sources.jar` → `net/minecraft/world/entity/LivingEntity.java`
  `L1665 ForgeHooks.onLivingHurt(...)` → `L1667 getDamageAfterArmorAbsorb` → `L1668 getDamageAfterMagicAbsorb` → `L1683 onLivingDamage` → `L1683 setHealth`。
  即 `LivingHurtEvent` **在护甲之前**,`LivingDamageEvent` 是**最终值**。
- 1.21.1:`LivingIncomingDamageEvent` 类注释原文 "after invulnerability checks but **before any damage processing/mitigation**";
  `LivingDamageEvent` 类注释原文 "At this stage in the damage sequence, **all reduction effects have been applied**"。
- ⇒ **`LivingDamageEvent.Pre` 的 1.20.1 等价体是 `LivingDamageEvent`,不是 `LivingHurtEvent`。**
  `AGENTS.md` 的映射句「`LivingDamageEvent.Pre` → `LivingHurtEvent`」与源码不符(代码本身基本是对的,文档错)。
- 另注:同一份 AGENTS.md 里「`LivingIncomingDamageEvent` → `LivingAttackEvent`(HIGHEST 记原始值)+ `LivingHurtEvent`(LOWEST 算倍率,见 onCurseMitigation)」说的是**七咒减伤那一处**的具体实现,不是通用映射——两处混在一句里,极易误读。

## S0-C1 【高】效果牌法伤加成被原版无敌帧削减/吞掉

- 机制:`DamageEffectCardHandler#onLivingDamagePre` 在**外层伤害事件内部**再调 `target.hurt(trueDamage, bonus)`(1.21.1 L62-71 / 1.20.1 L59-68)。
  此刻原版**已经**写好 `lastHurt = 本次外层伤害的原始值`、`invulnerableTime = 20`:
  - 1.20.1 `LivingEntity.java` L1140-1142(`lastHurt = amount; invulnerableTime = 20; actuallyHurt(...)`);
  - 1.21.1 `LivingEntity.java` L1201-1202(同序)。
- 而 `astral_dice:true_damage` **只登记了 `minecraft:bypasses_armor`,没有登记 `bypasses_cooldown`**
  (本仓 `data/minecraft/tags/damage_type/` 下只有 `bypasses_armor.json`;全仓 `git grep bypasses_cooldown` 只命中探针注释)。
- 原版规则(两版本同构,1.20.1 L1131-1138 / 1.21.1 L1190-1198):
  `if (invulnerableTime > 10 && !source.is(BYPASSES_COOLDOWN)) { if (amount <= lastHurt) return false; actuallyHurt(amount - lastHurt); }`
- ⇒ **嵌套那份 bonus 的**实际落地值**是 `bonus - lastHurt`(外层命中的原始伤害),`bonus <= lastHurt` 时**整段丢弃**。例:箭 1 点 + 对怪激光 +4 → 只落地 3 点;武器 8 点 + 活体书页加成 → 加成 0 点。
- **对照证据(说明这是本仓已知且已修过的坑,只是这两条路径漏了)**:大当家溅射对主目标**显式保存并清零**无敌帧
  —— `combat/DiceCombatEvents.java` L643-656(两版本同文):注释写明「无敌帧内不更低的伤害被丢弃规则会把溅射整段吞掉(实测主靶只掉近战那 3 点、5 点溅射凭空消失)」,故 `savedInvulnerable = victim.invulnerableTime; victim.invulnerableTime = 0; hurt(...); finally 还原`。
- **为什么现有回归用例抓不到**:`SPELL-TRUE-DAMAGE` 的判据只要求「重甲靶与无甲靶的加成掉血**相同**」(probe `astral_bugfix_probe.js` L2345-2346 甚至明写“原版无敌帧的「amount − lastHurt」差额结算会体现在绝对值上;两靶同条件 ⇒ 差值可比”)——两只靶被削减同样的量,断言仍成立,绝对损失不可见。
- 影响面:`bonus` 来自 SpellDamageRegistry 全部修饰器(激光/板砖/轨道炮/定向爆破/活体书页/书签/忍者增益/紫晶骰子等),即**所有法伤加成都受影响**。
- 修法(二选一,需用户定夺):
  1. 给 `astral_dice:true_damage` 补登记 `minecraft:bypasses_cooldown`(新增一个 data tag 文件)——**同时影响**大当家溅射/电击手套 AOE/反击以外所有 true_damage 命中,使其彻底无视无敌帧(标准“真伤穿无敌帧”口径);
  2. 只在嵌套结算处对齐溅射做法:保存/清零/还原 `invulnerableTime`(改动小,但需在 1.20.1/1.21.1 各写一次,且要处理 `lastHurt` 语义)。
- 两版本**皆有**(1.20.1 L59-68 / 1.21.1 L62-71 同构)。

## S0-C2 【中高】电击手套 AOE 波及伤害跨版本数值不一致(护甲靶差异最大)

- `combat/SpellDamageRegistry.java:365`(两版本**同一行号**)取本次事件伤害作为 AOE 伤害:
  - 1.21.1 `float total = ctx.event.getNewDamage();` → 运行在 `LivingDamageEvent.Pre` = **护甲之后**
  - 1.20.1 `float total = ctx.event.getAmount();` → 运行在 `LivingHurtEvent` = **护甲之前**
- 随后 `L377 e.hurt(source, total)`,即**AOE 波及伤害 = 该值**。
- ⇒ 同一发攻击打重甲目标时,1.20.1 的电击手套 AOE **比 1.21.1 更高**(差值 = 护甲与附魔吸收量,重甲下可达 70%)。
- 两版本**不一致**;根因是 1.20.1 的 `DamageEffectCardHandler` 选错了事件(见 S0-C3)。
- 修法:把 1.20.1 的 `DamageEffectCardHandler` 及其 `SpellDamageContext.event` 类型从 `LivingHurtEvent` 改为 `LivingDamageEvent`(与 DiceCombatEvents/ChipDamageHandler/PaparaSignItem 等 1.20.1 同类处理器保持一致)。

## S0-C3 【中】1.20.1 火焰 -70% 与法伤并入挤在同一事件同一优先级,相对顺序未定义

- `ObsidianDiceHandler#onFireDamage`:1.21.1 = `LivingIncomingDamageEvent` + `HIGH`(L29);1.20.1 = `LivingHurtEvent` + **默认 NORMAL**(L29)。
  → 阶段仍等价(都是护甲前),但优先级从 HIGH 掉到 NORMAL。
- 1.20.1 的 `LivingHurtEvent` 上同时有 `DamageEffectCardHandler`(NORMAL)与 `ObsidianDiceHandler`(NORMAL),
  **两者相对顺序由 `@EventBusSubscriber` 扫描/注册顺序决定,代码中无任何显式保证**。
  ⇒ 火的 ×1.4 与「法伤 bonus 的嵌套真伤 + onHit AOE/标记」的先后不可预期(1.21.1 分处两个事件,顺序是确定的)。
- 修法:1.20.1 的 `ObsidianDiceHandler` 加 `priority = HIGH`,与 1.21.1 对齐并固定顺序。

## S0-C4 【中】死亡被取消时,死亡清理可能已经执行(同优先级跨类竞态)

- `PlayerLifecycleHandler#onPlayerDeathClearEffects`(默认优先级,1.21.1 L114 / 1.20.1 L112)做的是**不可逆清理**:
  清治愈、清充能(先暂存)、清养精蓄锐、清各种计数与冷却、移除赐福/隐身/虚弱印记、**移除玻璃骰子及其卡牌**……
- 末影骰子的保命在 `EnderDiceHandler#onLivingDeath`(**也是默认优先级**,1.21.1 L146 / 1.20.1 对应处)里 `setCanceled(true)`。
- 两者同为默认优先级、不同类;`isCanceled()` 守卫只能挡“先被取消、后清理”,**挡不住“先清理、后取消”**。
  ⇒ 触发末影骰子保命时,是否执行了全套死亡清理,取决于未指定的注册顺序(可能表现:玩家没死,但治愈/赐福/计数被清空、玻璃骰子没了)。
- 安全的部分:安全气囊在 `LivingDeathEvent` 用 `HIGHEST` 取消(`ChipDamageHandler` L75-85),**必定早于**默认优先级的所有死亡处理器。
- 修法:`PlayerLifecycleHandler#onPlayerDeathClearEffects` 改 `EventPriority.LOWEST`(清理永远是最后一件事),或给 `EnderDiceHandler` 的保命改 `HIGH`。

## S0-C5 【低中】反击伤害同样受无敌帧差额结算

- `DiceCombatEvents#injectCounterDamage`(1.21.1 L1133-1150)在伤害事件内嵌套 `attacker.hurt(diceDamage, dmg)`;
  `astral_dice:dice_damage` **不在任何 damage_type 标签里**(本仓只登记了 `true_damage` 到 bypasses_armor)
  ⇒ 同样走 `amount − lastHurt`;当被反击者 0.5 秒内刚受过伤(PvP 对拼、被第三方打)时,反击会被削减或吞掉。
- 两版本同构(`forge-1.20.1` 对应处 `L1143` 同形)。

## S0-C6 【低】4 个击杀类死亡处理器缺 `isCanceled()` 守卫(两版本一致)

- `CursedSwordChipItem`、`SatelliteChipItem`、`BonnieSignItem`、`HaiqingSignItem` 的 `LivingDeathEvent` 处理器均未检查 `event.isCanceled()`
  (1.21.1 与 1.20.1 **都没有**;`PlayerLifecycleHandler`/`InvestigationEventUtil`/`ElectricSwordChipItem`/`SmartWatchChipItem`/`EnderDiceHandler` 有)。
- 本模组自己的取消只针对玩家(气囊),这些处理器要求受害者是敌对生物,故当前不构成自查冲突;
  但**其它模组取消生物死亡**(图腾类/保护类)时,仍会按“死亡”计数并发放奖励。
- 修法:4 处各加 1 行 `if (event.isCanceled()) return;`。

## S0-C7 【结构性】同优先级顺序**没有任何显式保证**

- 全仓 **37 个类**用 `@EventBusSubscriber` 自动注册(`AstralDiceMod` 只显式注册了 mod 总线与 `IronSpellbooksCompat`),类之间没有注册顺序代码。
- `LivingDamageEvent.Pre` 上共 7 个处理器,分 4 个优先级:1.21.1 → `EnderDiceHandler HIGH`(L134)、`DiceCombatEvents NORMAL`(L184)、`DamageEffectCardHandler NORMAL`(L31)、`NancyLuSignItem NORMAL`(L262)、`DiceCombatEvents LOW ×2`(L894 狂暴、L940 虚弱印记)、`ChipDamageHandler LOWEST`(L45 气囊)、`PaparaSignItem LOWEST`(L56)。
- 同优先级内的相对顺序只能靠“恰好如此”,任何新增处理器/重命名都可能改变结果;`LOW` 层两个处理器还同在 `DiceCombatEvents` 一个类内(靠方法声明顺序:先 `+1×层数`(L900)后 `×1.10`(L952)——顺序一变数值就变)。
- 建议:至少给有先后语义的成对处理器显式指定不同优先级,并把“谁必须在谁之前”写进 `AGENTS.md`。

## S0-C8 【低 / 信息】两处“单线程假设”与一处被关闭的功能

- `DiceCombatEvents.counterDepth`(1.21.1 L143)与 `aoeProcessing`(L134)是 **static 字段**(非 ThreadLocal)。
  原版服务端单线程伤害处理下没问题;多线程伤害(如 Folia / 并行实体 tick)下会串状态。
- `PLAYER_DODGE_ENABLED = false`(1.21.1 L132 / 1.20.1 L128):玩家侧闪避对骰整段关闭。
  ⇒ 任何文案承诺的“玩家闪避”来源不可达;枪匠被动「任意来源闪避/反击」中的“闪避”需以破绽闪避那条路径为准确认(交 S2 核对)。
