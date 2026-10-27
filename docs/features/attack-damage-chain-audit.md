# 攻击 / 受击（伤害）链路审计与隐患登记

> 审计日期：2026-10-27　范围：**P0 双线** `neoforge-1.21.1` + `forge-1.20.1`（P1 `neoforge-26.1.2` / `fabric-1.20.1` 只登记不验证）
> 方式：**只读静态取证**（两线 sources jar 逐行 + 全量 `@SubscribeEvent` 枚举）。未实机。
> 起因：`m20350`「执行代码测攻击/受击链路全面排查，找出仍存在的问题和隐患」；`m20514`「记录所有问题，派子代理分批修正，务必使两线处理链路统一；再开一路验证两端法伤链」。

## 0. 速览表

| 编号 | 等级 | 影响线 | 问题 | 状态 | 批次 |
|---|---|---|---|---|---|
| N1 | P1 | forge | 鼠鼠护盾对「近战怪」完全失效（钳制值被骰战终值覆盖） | **已修** | A |
| N2 | P1 | forge | 黄心（吸收）对近战怪失效且被扣两次 | **已修** | A |
| N3 | P1 | forge | 安全气囊致死判定偏高（黄心充足也白耗充能） | **已修**（N2 落地后语义自动正确，仅订正注释） | A |
| N4 | P1 | 双线 | 事件牌「取长补短」-8 走 `setHealth` 旁路（可 0 血不死、绕过全部减伤与保命） | **已修** | B |
| N5 | P2 | 双线 | 事件牌 4/2 点用 `damageSources().generic()`（无归属、被无敌帧吞、仍吃护甲） | **已修** | B |
| N6 | P2 | 双线 | `dice_damage` 未登记 `bypasses_cooldown`（反击注入 / 绯红骰子自伤可被无敌帧吞） | **已修**（P1 两线 json 未同步 ⇒ N25） | C |
| N7 | P2 | 双线 | 狂暴反噬在嵌套伤害实例下多次触发 | **已修** | A |
| N8 | P2 | 双线 | 弱标记 ×1.10 锚点两线不同（forge 吸收后 / neo 吸收前） | **已修**（骰战路径；法伤路径受平台钩子限制，见 N16） | A |
| N9 | P2 | 双线 | Papara 汲取回血基数两线不同（forge 吸收后 / neo 吸收前） | **已修**（两线统一到「实际扣红心量」） | C |
| N10 | P2 | 双线 | 受击钩子在 AOE 早退之前分发（嵌套实例重复触发） | 登记，不改（语义确认后另批） | — |
| N11 | P2 | 双线 | `true_damage` 不 bypass 附魔/抗性（与「真伤」命名不符） | **待裁决** | — |
| N12 | P3 | 双线 | 若干伤害点无击杀归因 | 部分修（B 批修事件牌三条），其余登记（N26） | B |
| N13 | P3 | 双线 | `BarricadeEntity` 全覆写 `hurt`（已文档化） | 登记，不改 | — |
| N14 | P3 | 双线 | AOE 早退把「赐福/降神倒计时、隐匿解除」一起挡掉 | **待裁决** | — |
| N15 | P1 | 双线 | **法伤一次命中 = 两次 `hurt()`**（本体 + 嵌套真伤加成段）⇒ 所有「每次受击」效果翻倍 | **待裁决**（属平衡改动） | — |
| N16 | P2 | 双线 | forge 无「护甲后 / 吸收前」钩子 ⇒ 法伤 + 黄心时弱标记 ×1.10 数值无法与 neo 完全统一 | **待裁决**（接受差异 or 自算护甲） | — |
| N17 | P2 | 双线 | 加成段覆写 `lastHurt` / `invulnerableTime` ⇒ 命中后 10t 内非 bypass 伤害的差额基准变成加成段值 | 登记（`lastHurt` private，不建议 AT/mixin） | — |
| N18 | P2 | 双线 | 贯穿之铳 bonus 无上限（随目标护甲线性增长，且乘命中次数） | **待裁决**（数值） | — |
| N19 | P2 | 双线 | 紫晶骰子法伤 `rollD6(roller)` 忽略 `roller`（不吃诡异/绯红偏置，与骰战口径不同） | **待裁决**（设计意图确认） | — |
| N20 | P2 | 双线 | 电击手套 AOE 每个目标各走一遍完整修饰器（标记 + 防御 + 各自 d6） | **待裁决**（合意则补注释） | — |
| N21 | P2 | 双线 | B 批惩罚伤害带 causing ⇒ 触发受击被动（立牌钩子 / BufferShield / Papara / 赐福倒计时） | **已修**：`isHostileAttack` 排除 `UNREDUCIBLE_DAMAGE` / `CARD_COST`（neo `combat/DiceCombatEvents.java:1777-1786` / forge `:1907-1916`，文本 sha256 相同）；P1 两线未镜像（N25） | E |
| N22 | P3 | 双线 | 四张伤害效果牌 `isActive` 恒 false = 死代码；若将来恢复会双倍加成 | 登记（建议删或加断言锁死） | — |
| N23 | P3 | 双线 | 盾不挡 `card_spell`/`true_damage` 靠 `direct=null` 而非标签 | 登记（建议在 `ModDamageTypes` javadoc 写死） | — |
| N24 | P3 | 双线 | 惩罚伤害跳字组别 `SPELL`，语义上不是法术 | 登记（可选 `CARD_COST` 配色） | — |
| N25 | P3 | P1 两线 | `neoforge-26.1.2` / `fabric-1.20.1` 的 `bypasses_cooldown.json` 未含 `dice_damage`（四线一致性欠账） | 登记（P1 不碰） | — |
| N26 | P3 | 双线 | 仍无归因的伤害点：电磁炮雷击真伤、厄运 `true_damage`、大餐对亡灵 `magic()`、Sherry 兜底 `generic()` | 登记（部分已文档化） | — |

## 1. 判据基石：两线伤害派发顺序不一样

```
forge actuallyHurt (net/minecraft/world/entity/LivingEntity.java:1663-1688)
  :1665 ForgeHooks.onLivingHurt            ← 护甲前
  :1666 if (damageAmount <= 0) return;
  :1667 getDamageAfterArmorAbsorb / :1668 getDamageAfterMagicAbsorb
  :1669-1670 扣「黄心(吸收)」               ← 用 LivingHurt 段的 damageAmount 算
  :1680 ForgeHooks.onLivingDamage           ← 护甲+吸收之后 ★骰战在这里 setAmount
  :1683 setHealth(getHealth() - f1)
  :1684 setAbsorptionAmount(abs - f1)       ← Forge 额外再扣一次吸收

neo actuallyHurt (同文件 :1785-1805)
  :1787-1788 护甲 / 魔法吸收
  :1789 onLivingDamagePre                  ★骰战在这里 setNewDamage（护甲后、吸收前）
  :1790-1792 按新值扣吸收
  :1793 f1 = damageContainers.peek().getNewDamage()
  :1801 setHealth(getHealth() - f1)   :1805 onLivingDamagePost
```

叠加第二条：forge 的「生物近战打玩家」**无条件整段重算伤害**——

```
forge combat/DiceCombatEvents.java:327-334   if (directEntity instanceof Mob && target instanceof Player) resolveMobMeleeAttack(...)
forge combat/DiceCombatEvents.java:1057      if (source.getDirectEntity() != mob) return;
forge combat/DiceCombatEvents.java:1060      boolean opposed = hasDice(target);   // 只 gate 掷骰/防御牌耐久（:1101-1103）
forge combat/DiceCombatEvents.java:1097      event.setAmount(finalDmg);           // ← 无条件覆盖 LivingHurt 段结果
```

⇒ **结论**：凡在 forge `LivingHurtEvent` 上改数值的代码，遇到近战怪就整段被丢。这是 N1/N2/N3/N8 的共同根因。

## 2. 目标口径（改造后两线必须同时满足的不变量）

1. **受击侧修饰统一作用在「本次命中终值」上**：骰战终值 `finalDmg` 是唯一权威；护盾钳制、弱标记 ×1.10、末影骰受害者倍率、立牌「被追踪 +5」都作用于 `finalDmg`。
2. **吸收（黄心）按终值结算且只扣一次**：`absorbed = min(finalDmg, absorption)`；红心伤害 = `finalDmg - absorbed`；不得出现「按骰战前数值扣吸收 + 按终值扣红心」的双扣。
3. **受击方乘算发生在吸收之前**（等价 neo `LivingDamageEvent.Pre` 语义）：forge 需把乘算带进 `finalDmg` 而不是留在 `LivingHurtEvent`。
4. **保命/减伤判定读取「扣除本次吸收后的量」**（等价 neo 的 `damageAfterAbsorption`）：气囊、致死判定一律如此。
5. **任何「惩罚性伤害」必须走 `hurt()` 正常管线**（不得 `setHealth` 直改），且带 causing entity 以便归因。

## 3. 逐项证据与修法

### N1 · P1 · forge · 鼠鼠护盾对近战怪完全失效

```
forge item/RenShieldManager.java:408   @SubscribeEvent(priority = EventPriority.LOWEST)
forge item/RenShieldManager.java:409   onRenShieldAbsorb(LivingHurtEvent event)
forge item/RenShieldManager.java:412   float remaining = remainingShieldAbsorption(player);
forge item/RenShieldManager.java:416   event.setAmount(remaining);        ← 被 :1097 覆盖
neo  item/RenShieldManager.java:406-414 （LivingDamageEvent.Pre LOWEST，:414 setNewDamage）  ← 正常
```

设计语义（类注释 forge `:42-43` / neo `:44-45`）：把伤害钳到剩余黄心以内、超出的部分丢弃。实际被覆盖后护盾等于不存在。
**修法**：让骰战两条 `setAmount` 路径在写入前按 `remainingShieldAbsorption` 做钳制（等价把 LOWEST 的钳制并入终值），或把 forge 该处理器改挂 `LivingDamageEvent`。

### N2 · P1 · forge · 黄心失效且被扣两次

```
forge LivingEntity.java:1669-1670  f1 = max(damageAmount - absorption, 0);   // 按骰战前算
forge LivingEntity.java:1680       onLivingDamage → dice :1097 setAmount(finalDmg)
forge LivingEntity.java:1683       setHealth(getHealth() - f1)              // 红心照吃满
forge LivingEntity.java:1684       setAbsorptionAmount(abs - f1)            // 再扣一次
```

推演：护甲后 5 点、黄心 10 ⇒ 原版应为 0 伤害；骰战终值 8 ⇒ 红心 -8 且吸收 -8~-13（内部可为负、对外读 0）。这就是「护盾/黄心挡不住一发大伤害」的根因（neo 无此问题）。
**修法**：骰战写终值时同步结算吸收——`absorbed = min(finalDmg, getAbsorptionAmount())`，`setAbsorptionAmount(abs - absorbed)`，`event.setAmount(finalDmg - absorbed)`（forge 路径）；两线口径见 §2 不变量 2、3。

### N3 · P1 · forge · 安全气囊致死判定偏高

```
forge event/ChipDamageHandler.java:67-76   damage >= player.getHealth() ⇒ setAmount(0)
neo  event/ChipDamageHandler.java:60       float damageAfterAbsorption = Math.max(0, damage - absorption);  ← 正确口径
```

N2 未修时 `damage` 是骰战终值、不含已吸收部分 ⇒ 黄心充足也判致死，白耗 6 点充能与 15s 冷却。
**修法**：N2 落地后自然缓解；同时把 forge 判定改成「扣除本次吸收后的量」。

### N4 · P1 · 双线 · 「取长补短」-8 走 setHealth 旁路

```
neo   event/EventEffects.java:287 learnFromOthersApply → :300 healed.heal(8.0F) / :305 damaged.setHealth(max(0, hp - 8))
forge event/EventEffects.java:292 learnFromOthersApply → :305 healed.heal(8.0F) / :310 damaged.setHealth(max(0, hp - 8))
```

原版 `setHealth`（`LivingEntity.java:1077`）不派发任何伤害事件；`die()` 只在 `hurt()` 的 `:1205-1212` 与 `handleEntityEvent(byte 3)` 的 `:1784-1787` 被调用 ⇒ 护甲/抗性/保护/鼠鼠护盾/磨刀石/侦探/超天酱/气囊全部不参与、无受伤反馈、不派发 `LivingDeathEvent`；打到 0 血不死，而进入 `tickDeath()`（`LivingEntity.java:416` / `:552-559`：`++deathTime`，≥20 时 `broadcastEntityEvent(60)` + `remove(KILLED)`）这条异常路径。
**修法**：改走 `damaged.hurt(<惩罚性伤害源>, 8.0F)`，两线同一伤害类型与同一归因口径。

### N5 · P2 · 双线 · 事件牌惩罚伤害用 generic

```
neo   event/EventEffects.java:455 worldCollapse → target.hurt(level.damageSources().generic(), 4.0F)
neo   event/EventEffects.java:482 problemDelivery → target.hurt(level.damageSources().generic(), 2.0F)
forge event/EventEffects.java:462 / :489                                       同上
```

`generic()` 无 causing entity ⇒ 无击杀归因、`DamageNumberAggregator` 不显示；`generic` 不在 `bypasses_cooldown` ⇒ 可被 0.5s 无敌帧整段吞；仍吃护甲/保护/抗性（与「事件牌惩罚」设计意图不一致）。
**修法**：换成本仓已有的惩罚型伤害类型（`card_cost` 或 `unreducible_damage`，二者均已在 `bypasses_cooldown` / `bypasses_enchantments` / `bypasses_resistance` 内）并把触发者作为 causing entity 传入。

### N6 · P2 · 双线 · dice_damage 未登记 bypasses_cooldown

```
data/minecraft/tags/damage_type/bypasses_armor.json      = true_damage/card_spell/skill_damage/extra_damage/card_cost/unreducible_damage
data/minecraft/tags/damage_type/bypasses_cooldown.json   = 同上 6 种（**dice_damage 不在内**）
data/minecraft/tags/damage_type/bypasses_enchantments.json / bypasses_resistance.json = 仅 card_cost + unreducible_damage
```

`dice_damage` 的两处现役用途：反击注入（`DiceCombatEvents.injectCounterDamage`，counterDepth 保护）与绯红骰子掷 1 自伤（`event/CrimsonDiceHandler.java:64`）⇒ 都可能被无敌帧吞掉。
**修法**：先枚举 `ModDamageTypes.diceDamage(` 全部调用点确认无「本应吃无敌帧」的用途，再决定加入 `bypasses_cooldown`；两线资源文件必须逐字节一致。

### N7 · P2 · 双线 · 狂暴反噬在嵌套实例下多次触发

```
forge combat/DiceCombatEvents.java:1457-1473  onBerserkDamageTaken (LOW) → target.hurt(unreducibleDamage, extra)
由 BERSERK_BACKLASH_APPLYING ThreadLocal 只护自身递归
```

法伤真伤段、`extra_damage`、大当家溅射等嵌套实例各自会再触发一次反噬（仅玩家为受害者时相关）。
**修法**：与骰战同一把闸门（`isInternalAoe() || counterDepth > 0` 或一次性 per-hit 标记），两线同步。

### N8 · P2 · 双线 · 弱标记 ×1.10 锚点不同

```
forge combat/DiceCombatEvents.java:1527   event.setAmount(event.getAmount() * 1.10f)   // 吸收之后
neo   combat/DiceCombatEvents.java:1544   event.setNewDamage(event.getNewDamage() * mult) // 吸收之前
```

有黄心时两线数值不同（对黄心的消耗量差异）。
**修法**：按 §2 不变量 3 统一到「吸收前、作用于 `finalDmg`」。

### N9 · P2 · 双线 · Papara 汲取回血基数不同

```
neo   item/sign/PaparaSignItem.java:72-82   LivingDamageEvent.Pre LOWEST; :80 heal = max(1, (int) getNewDamage()/2)
forge item/sign/PaparaSignItem.java:72-73   LivingDamageEvent LOWEST;     同一表达式
```

两线基数一个含吸收段、一个不含；且都落在 LOWEST 桶，与 RenShield 钳制 / ChipDamageHandler 的先后只靠注册顺序（本仓已有「同档顺序不可依赖」先例）。
**修法**：统一到「实际扣红心量」口径（neo 用 Post 的实际伤害值，forge 用 LivingDamage 的 `getAmount()`），并显式写明依赖的先后关系。

### N10 · P2 · 双线 · 受击钩子在 AOE 早退之前（登记，不改）

```
forge combat/DiceCombatEvents.java:294-298  BaseSignItem.invokeHurtHooks(...) + BufferShieldChipItem.onHurt(...)
forge combat/DiceCombatEvents.java:303      if (isInternalAoe() || counterDepth > 0) return;   ← 早退在钩子之后
```

嵌套实例（额外加伤 `:837-849`、溅射、法伤真伤段）会再次进入钩子。现有实现各自带冷却/闸门，故无可见影响：`LuluSignItem:166-186`（HURT_COOLDOWN_TICKS）、`BufferShieldChipItem:43-55`（15s + isHostileAttack）、`AmeSignItem:331-339` / `KangelSignItem:317-329`（`addLoveWithGate` / 闸门）。`BaseSignItem:511-513 invokeHurtHooks` 统一过 `isHostileAttack`。
**处置**：不改——「AOE 命中是否算受击」需规格确认；登记为此后**新增受击被动必须自带冷却或闸门**的原因。

### N11 · P2 · 双线 · true_damage 不 bypass 附魔/抗性（待裁决）

`true_damage` 在 `bypasses_armor` + `bypasses_cooldown`，但**不在** `bypasses_enchantments` / `bypasses_resistance` ⇒ 法伤加成段、飞星、大当家溅射、电磁炮雷击都会被「保护」附魔与抗性药水削减。与「真伤」命名不符，需用户裁决是否本意。

### N12 · P3 · 双线 · 无击杀归因的伤害点

```
neoforge-1.21.1/.../mixin/EntityThunderHitMixin.java:39-63  真伤无来源（javadoc 明示「无击杀归属」）
item/card/HuoCardItem.java:257 (forge :254)                true_damage 无来源（厄运）
item/card/LuxuryFeastCardItem.java:90 (forge :89)           magic() 无来源（大餐打亡灵）
combat/SherryThrowManager.java:180                          caster==null 兜底 generic()
event/EventEffects.java:455/:482 (forge :462/:489)          generic()（并入 N5 修复）
```

**处置**：事件牌两条并入 B 批修；其余登记（击杀统计 / 掉落归属 / 隐匿调查 / 立牌击杀奖励均不认这些伤害）。

### N13 · P3 · 双线 · BarricadeEntity 全覆写 hurt（登记，不改）

```
entity/BarricadeEntity.java:282-304  只接受 source.getEntity() instanceof LivingEntity；每下固定 1 点；markHurt + broadcastDamageEvent
entity/BarricadeEntity.java:53-63    类头已明列：绕过平台伤害事件 / 绕过格挡与无敌帧 / 无击杀归因
entity/BarricadeEntity.java:371-379/:387-391 die()/kill() 覆写；:400+ remove 只对 KILLED/DISCARDED 清场
```

与其它伤害系统联调时需知悉（对路障「磨刀石/护盾不生效」是设计）。

### N14 · P3 · 双线 · AOE 早退挡住赐福/降神倒计时与隐匿解除（待裁决）

```
combat/DiceCombatEvents.java:303   if (isInternalAoe() || counterDepth > 0) return;
combat/DiceCombatEvents.java:305-317 赐福/降神倒计时启动、秘密侦探隐匿解除  ← 在早退之后
```

即「技能/AOE 命中」不启动赐福倒计时、不解除隐匿。是否符合「任意攻击行为且必须命中」的规格，需用户裁决。

## 4. 已核对、确认无问题（供回归基线）

- **NancyLu**：`onNancyLuAnyAttackWhileHidden` 只调 `onAttackWhileHidden(player)`、不改伤害（neo `LivingDamageEvent.Pre` / forge `LivingDamageEvent`）⇒ 无覆盖风险；末影珍珠免疫走取消事件。
- **FateGuidance**：两线都对骰战分支提前 return 并只捕获 ratio（neo `item/card/FateGuidanceCardItem.java:110-142` / forge `:111-151`）。
- **ObsidianDice**：爆炸伤害的直接实体非 Mob/Player ⇒ 不进骰战覆盖（forge `event/ObsidianDiceHandler.java:28-36` / neo `:29-37`）。
- **NewSignEvents 免疫**：取消语义两线有效（neo `LivingIncomingDamageEvent` / forge `LivingHurtEvent` ⇒ `ForgeHooks.onLivingHurt` 返回 0 ⇒ `:1666` 早退）。
- **AirbagChipItem 计数**：只 `ChargeManager.addStacks`、不改伤害（偏差已在 javadoc 登记）。
- **DamageCardFxScheduler**：`beginAoe/endAoe` + `markCardSplashHit` 去重 + `APPLYING_CARD_FX_DAMAGE` 三层窗口正确。
- **SherryThrowManager 正常分支**：`skillDamage` + `beginAoe` + `DamageNumberAggregator.tag`。
- **反击注入**：`isInCounterChain()` + `counterDepth++/finally--`，伤害源 `diceDamage(attacker.level(), player)`（归属玩家、不递归）。
- **大当家溅射**：`trueDamage` + `isBlessingTarget` 选敌 + 仅对主目标临时 `invulnerableTime=0` 并在 finally 还原。
- **飞星 / 轨道轰炸**：`trueDamage` / `skillDamage` + `beginAoe` ⇒ 骰战早退，类型自带 `bypasses_cooldown`；轨道轰炸伤害封顶与只按 base 记账正确。
- **DamageNumberAggregator**：`onLivingDamagePost` 取管线终值，同 tick 按 `(entityId, group, recipient)` 聚合，`tagKey = entityId<<32 | identityHashCode(source)` ⇒ 嵌套实例不串味。
- **立牌「被追踪 +5」**：forge 已于 2026-10-27 从 `LivingHurtEvent` 迁到 `LivingDamageEvent` LOWEST（与 neo 同档），实机 median=5。
- **击杀奖励去重**：`combat/DeathTriggerGuard.claim(entity, channel)` 覆盖 5 处（智能手表 / 电磁剑 / 手电筒 / 海青 / Bonnie）+ 隐匿调查幂等闸门。

## 5. 未覆盖 / 约束

- 全部结论为**静态取证**，未实机；N1/N2/N4 的实机判据见 §6。
- **P1 两线未审计**（`neoforge-26.1.2` / `fabric-1.20.1`）：同名处理器仍未接 `DeathTriggerGuard`、未做本表任何修复，属既定范围外欠账。
- `Combat` 内 `PlayerHostilityTrackerEvents`（HIGH）与 `BarricadeBlock`（thrown 伤害源）未做进一步语义评估。
- N11 / N14 需用户裁决，未改。

## 6. 批次与验收判据

| 批次 | 范围 | 交付判据 |
|---|---|---|
| A | N1 + N2 + N3 + N7 + N8（战斗核心，两线统一到 §2 不变量） | `:<线>:classes` 双线 EXIT 0；`DiceCombatEvents` 两条 `setAmount` 路径都做吸收结算；护盾钳制/弱标记乘算作用于终值；反噬有闸门 |
| B | N4 + N5（事件牌走伤害管线） | 双线 `EventEffects` 无 `setHealth` 惩罚性扣血；惩罚伤害带 causing entity 且类型在 bypasses_cooldown 内 |
| C | N6 + N9（冷却标签 + Papara 基数统一） | 两线资源逐字节一致；Papara 两线同一基数口径 |
| D | 两端**法伤链**独立复核（只读） | 逐条 matcher / bonus 源 / 派发点证据 + 缺口清单 + 可跑判据建议 |

实机判据（待用户放行后执行）：

1. **N2**：给玩家挂 10 点吸收，令近战怪命中一次 → 吸收消耗量与红心伤害在同一 tick 内满足 `absorbed + health = finalDmg`（不得出现红心照吃满）。
2. **N1**：挂鼠鼠护盾 + 黄心，怪打一次 → 红心必须为 0、黄心按剩余钳制消耗。
3. **N4**：把目标玩家血量降到 6，触发「取长补短」→ 必须触发正常 `LivingDeathEvent`（有死亡界面 / 击杀归因），不得 0 血滞留。
4. **N8**：同一套装备下对比有/无黄心两组的实际扣血量，验证 ×1.10 作用点一致。
## 7. 批次落地与独立复核（2026-10-27）

### 7.1 批次 A / B / C 落地事实（两线对称，均 `:classes` EXIT 0，尚未实机）

**批次 A（N1/N2/N3/N7/N8，forge 为主、neo 仅 N7）**
- forge `combat/DiceCombatEvents.java:365` 新增 `private static void settleDiceDamage(LivingEntity target, LivingDamageEvent event, float finalDamage)`，是**两条写终值路径的唯一出口**：玩家攻击 `:941`、生物近战 `:1224`（全文 `event.setAmount(` 只剩 `:381`/`:388` 在 settle 内 + `:1663` 非骰战弱印记 LOW）。
  - 护盾钳制 `:371 if (snapshot.shield > 0.0F && damage > snapshot.shield)`；`A0 <= 0` 时**完全跳过吸收补偿**直接 `setAmount(damage)`（`:376-383`，避免中途改写吸收污染同档 LOWEST 的读值）；否则 `absorbedShould=min(damage,A0)` / `toHealth=damage-absorbedShould` / `setAbsorptionAmount(A0 - absorbedShould + toHealth)` / `setAmount(toHealth)`。
- 受击前快照：`PRE_HURT_ABSORPTION = Collections.synchronizedMap(new WeakHashMap<>())`（`:301-302`）+ `PreHurtSnapshot{absorption, shield}`（`:304-318`）+ `@SubscribeEvent(priority=HIGHEST) onLivingHurtRecordAbsorption(LivingHurtEvent)`（`:331-339`，护甲前取 A0 与受击前护盾余额）+ `consumePreHurtAbsorption(LivingEntity)`（`:341-352`，先 `remove` 再算，防嵌套串味）。
- **N1 落点偏离原方案（有反证）**：原计划在 `LivingDamageEvent` 里读 `RenShieldManager.remainingShieldAbsorption`；实测 forge 平台 `:1669-1670` 已按平台数值扣掉 `min(d, A0)`，到 `:1680` 时 remaining 常为 0（shield=10、d=18 时会漏 8 点红心，neo 为 0）⇒ 改为 LivingHurt(HIGHEST) 快照护盾余额。
- N8：抽常量 `WEAK_MARK_DAMAGE_MULTIPLIER = 1.10f`（`:320-323`），玩家路径 `:918`、生物近战路径 `:1216-1224`（该路径**原先完全没有**弱印记乘算），LOW 处理器改调 `isDiceBattleSettledDamage(source, target)`（`:1666-1686`）+ 常量。
- N7：forge `:1588-1598` / neo `:1478-1488` 同为 `if (isInternalAoe() || DamageEffectCardHandler.isApplyingTrueBonus()) return;`，保留 `BERSERK_BACKLASH_APPLYING` 自递归保护。
- N3：不改逻辑（N2 落地后 `event.getAmount()` 已是红心量），只订正 `event/ChipDamageHandler.java:57-63` 的过期假设（「本事件拿到 0」）与 `:73-78` 的 A6 口径注释。
- **已知限制（登记，不改）**：settle 之后再被 LOWEST 处理器改金额（气囊归零 / 磨刀石 -N）⇒ 平台 `:1684` 的扣减量 ≠ 写入值 H ⇒ 吸收终值偏移；方向是「少消耗吸收」，绝不双扣；1.20.1 无 `LivingDamageEvent.Post`，无法补偿。

**批次 B（N4/N5）**
- 新增唯一出口 `event/EventEffects.java` 的 `private static boolean hurtAsEventCardPenalty(ServerLevel level, Player triggerer, Player target, float amount, String tag)`（neo `:568-578` / forge `:575-585`）：`causing = triggerer != null ? triggerer : target` → `ModDamageTypes.unreducibleDamage(level, causing)` → `DamageNumberAggregator.tag(..., Group.SPELL)` → `target.hurt(source, amount)` → 机器行 `AP_EVENT_PENALTY: tag=…:target=…:causing=…:amount=…:hurt=…`。
- 三处调用（两线除行号逐字相同）：-8 `learn_from_others`（neo `:313` / forge `:318`）、4 点 `world_collapse`（neo `:469` / forge `:476`）、2 点 `problem_delivery`（neo `:497` / forge `:504`）；`healed.heal(8.0F)` 属治疗、保留。
- 连带签名：`learnFromOthersApply(Player triggerer, List<Player>)`（neo `:293` / forge `:298`）、`afterDispatch(Player triggerer, AstralEvent, List<Player>)`（neo `:328` / forge `:333`），调用点 `event/AstralEventDispatcher.java`（neo `:134` / forge `:133`）改传 `bonusFinal`。
- 反向判据：两线 `setHealth(` 代码调用 **0**、`damageSources().generic()` **0**。

**批次 C（N6/N9）**
- N6：两线 `src/main/resources/data/minecraft/tags/damage_type/bypasses_cooldown.json` 末尾追加 `astral_dice:dice_damage`，两文件**字节相同**（sha256 `d673d5951dce61fd1e98caf0a1157b7cfab26a4522941f17be4c89d90f088055`，255 B / CRLF 11 / bareLF 0）。产伤点仅 `combat/DiceCombatEvents.java:1926`（反击注入）与 `event/CrimsonDiceHandler.java:64`（掷 1 自伤）；`DamageNumberAggregator` 只读该类型做跳字分组。
- N9：neo `item/sign/PaparaSignItem.java:73` 由 `LivingDamageEvent.Pre` 改 `LivingDamageEvent.Post`（`:87` 表达式不变），forge 侧本就等价 ⇒ 两线锚点统一为「实际扣红心量」，闸门与最小回血 1 未动。
- 连带：两线 `damage/ModDamageTypes.java` 的 `CARD_COST` javadoc 增补 2026-10-27 订正段（旧文「骰子伤害类型不在 bypasses_cooldown 里」已失真）。

### 7.2 独立复核（批次 D）：`docs/features/spell-damage-chain-verification.md`
该文（706 行，只读）对法伤链做了 9 项逐环节取证，三条主结论：① **法伤一次命中 = 两次 `hurt()`**（本体 + 嵌套 `true_damage` 加成段）⇒ 所有「每次受击」效果翻倍（N15）；② 声称 forge `LivingEntity.java:1684` 双扣吸收（**经 Lead 复核为 REFUTED，见 7.3**）；③ 四张伤害效果牌 `isActive` 恒 `false`，真正出口是 `DamageCardFxScheduler.cardSpell`，且 `card_spell` 自我命中 matcher ⇒ 每一跳再触发余下 5 个修饰器（N22）。
其新增的 N15–N26 已并入本文 §0 表；N21 已由批次 E 修完（2026-10-27 19:03，双线 `:classes` EXIT 0）；其中只有 N21（惩罚伤害触发受击被动）判为**必须修**（批次 E 进行中），其余均登记待裁决或登记不改。

### 7.3 Lead 复核订正：D 的「P1-1 forge 吸收双扣」判定为 REFUTED
证据（`forge-1.20.1/build/moddev/artifacts/forge-1.20.1-47.4.10-sources.jar` → `net/minecraft/world/entity/LivingEntity.java`）：

```java
2932    public void setAbsorptionAmount(float absorptionAmount) {
2933       if (absorptionAmount < 0.0F) {
2934          absorptionAmount = 0.0F;      // ← 负值在写入时即被钳到 0
2935       }
2936       this.absorptionAmount = absorptionAmount;
2937    }
```

代数：d = 护甲/魔法后伤害、A = 吸收池、`f1 = max(d - A, 0)`（`:1669`）⇒ `:1670` 后 `abs = A - (d - f1)`；`d > A` 时该值恰为 0，`:1684` 再执行 `abs -= f1 = -(d-A)` **被 `:2933-2935` 钳回 0** ⇒ 总消耗 = `A = min(d, A)`，与 neo `:1790-1792` 的 `Math.max(0, A - absorbed)` 等价。**不存在双扣**。
要点：D 的修法建议（在 `LivingDamageEvent` LOWEST 里 `setAbsorptionAmount(abs + event.getAmount())`）若被执行，会给**每一次 forge 受击**白送 `f1` 点吸收，属于回归。**该建议已否决，勿落地。**
`dice` 路径确实会出现「吸收扣减量与写入终值不匹配」，但这是**改写 `f1` 的必然代价**，批次 A 的 `settleDiceDamage` 已用 `setAbsorptionAmount(A0 - absorbedShould + toHealth)` 里的 `+ toHealth` 精确抵消 `:1684`（净消耗 = `A0 - absorbedShould`，正确）。

### 7.4 汇总：待用户裁决 / 登记不改（本轮不落代码）

#### 7.4a 用户裁决（2026-10-27 19:26，m20709）

| 项 | 裁决 | 落地要求 |
|---|---|---|
| **N15** | **必须改回单次结算**（两次 hurt 本身就是非预期平衡问题，且第三方会捕获成多次判定；此前发现的两个 bug 都源于此） | 用户给了两个方案，**先用只读子代理验证可行性**再落地：方案一（按护甲/韧性/法抗等预补偿成单次实际伤害，需改 HUD 与跳字时序，并对神化 / Iron 的法术与魔法书 / 咒法学 / 新生魔艺 / 血魔法 / 诡厄巫法 / 神秘遗物(+) 等魔法模组的自带法术抗性做单独适配）、方案二（mixin 底层改写，使对应伤害类型从底层绕过原版护甲机制，有破坏性，需兼容性测试） |
| **N11** | **以法伤为标准**：`true_damage` 与法伤链行为必须完全一致（二者目前「雷同但结算有别」） | 与 N15 同批判定；若法伤改单次结算，真伤段也必须走同一口径（不得保留「被保护附魔/抗性药水削减」的差异） |
| **N14** | **技能命中算「攻击行为」**（近战 / 远程 / 法伤一律算） | AOE 早退不得再挡住赐福·降神倒计时启动与秘密侦探隐匿解除；改动时须核「一次 AOE 命中多个目标」的计数语义 |
| **N25** | **不处理**：`neoforge-26.1.2` / `fabric-1.20.1` 持续搁置，直到 1.5.0 正式版再执行完整内容移植 | 仅在交接与审计里保留欠账登记，不做局部镜像 |

> 验证产出（只读）：`docs/features/spell-single-hit-option-a.md`（方案一 + 折叠路径）与 `docs/features/spell-single-hit-option-b.md`（方案二 + mixin 冲突面）。

| 编号 | 说明 | 需要裁决什么 |
|---|---|---|
| N15 | 法伤 = 两次 hurt ⇒ Papara 回血、受击钩子、弱标记、反噬、ChipDamageHandler 全部翻倍 | 是否在加成段统一加 `isApplyingTrueBonus()` 闸门（**会降低现有法伤收益，玩家可感知**） |
| N16 | forge 缺「护甲后 / 吸收前」钩子 ⇒ 法伤 + 黄心时弱标记 ×1.10 与 neo 数值不同 | 接受差异（推荐）还是自己复刻护甲计算（脆弱） |
| N11 | `true_damage` 仍被「保护」附魔与抗性药水削减 | 是否符合「真伤」设计意图 |
| N14 | AOE 早退把「赐福/降神倒计时、秘密侦探隐匿解除」一起挡掉 | 技能/AOE 命中是否应算「攻击行为」 |
| N18 | 贯穿之铳 bonus 无上限（随护甲线性、乘命中次数） | 是否加 cap |
| N19 | 紫晶骰子法伤 `rollD6(roller)` 忽略 roller | 法伤骰是否恒均匀（若要联动骰子偏置则改走 `rollCombatDie`） |
| N20 | 电击手套 AOE 每目标各走完整修饰器 | 是否合意（合意则补注释，不合意则在 `SpellDamageContext` 加 aoe 标志） |
| N22 | 四张伤害效果牌 `isActive` 恒 false = 死代码 | 删除还是加断言锁死 |
| N23 | 盾不挡 `card_spell`/`true_damage` 靠 `direct=null` 而非标签 | 是否在 `ModDamageTypes` javadoc 写死禁令 |
| N24 | 惩罚伤害跳字组别 `SPELL` | 是否改用 `CARD_COST` 配色 |
| N25 | P1 两线 `bypasses_cooldown.json` 缺 `dice_damage` | 何时补（P1 批次） |

### 7.5 批次 F：幻影吸收余额显式化（2026-10-27 19:0x–19:2x，forge 单线）

**起因**：`dsh_code_review` run `15212f5f` 的两条 P2 —— 批次A 的 `settleDiceDamage` 用 `setAbsorptionAmount(A0 - absorbedShould + toHealth)` 写回一个**只活到平台 `LivingEntity.java:1684` 之前**的幻影余额 H，把约定隐式散落在时间轴上：

1. **P2-A**：窗口内对同一目标补发的嵌套 `hurt()`（额外加伤、大当家溅射、狂暴反噬）在平台 `:1669-1670` 按 `c1 = min(P, 含 H 的余额)` 结算 ⇒ 这段伤害被幻影整段吞掉（跳字满额、红心未扣）。
2. **P2-B**：窗口内改金额的 LOWEST 处理器（筹码减伤 / 立牌 +5 / 弱印记 ×1.10）让平台 `:1684` 的扣减量 ≠ H；气囊归零时 `:1681` 整段跳过 ⇒ 吸收停在 `A0 - absorbedShould + H`（例 A0=4、F=20 ⇒ 黄心白涨 12 点）。

**修法（forge `combat/DiceCombatEvents.java`）**：把 H 登记成显式数据（`PHANTOM_ABSORPTION`，弱键 + `gameTime`，跨 tick 自动失效），新增三个操作：
- `phantomOf(LivingEntity)`（`:443-452`）读登记；
- `withPhantomPeeled(LivingEntity, Runnable)`（`:458-472`）剥 H 并**摘登记** ⇒ 嵌套 hurt 在真实余额上结算 ⇒ 结束后挂回 H 交给外层 `:1684`；三处包裹：额外加伤 `:1062`、大当家溅射 `:1131`、狂暴反噬 `:1717`（清单外同机制，注释标注可单点回退）；
- `compensateAbsorption(LivingEntity, float finalAmount)`（`:482-489`）剥 H ⇒ 写 `真实余额 + finalAmount` ⇒ 平台扣 finalAmount 落回真实余额；**写完把登记刷新成 `finalAmount` 而非清除**（LOWEST 段可能多个处理器依次改金额，后者必须知道平台还会扣多少），`finalAmount <= 0` 才转清除。调用点：`event/ChipDamageHandler.java:61`（`finally` 覆盖气囊/磨刀石/侦探/超天酱四条分支与全部早退）、`event/LinglingSignEvents.java:121`（+5 之后）、`combat/DiceCombatEvents.java:1777`（弱印记乘算之后）。
- 另在 `onLivingHurtRecordAbsorption`（`:335-347`）末尾 `PHANTOM_ABSORPTION.remove(target)`，保证每段新受击从干净状态开始。

**判据**：双线 `:classes` EXIT 0；`setAbsorptionAmount(` 在 forge `DiceCombatEvents.java` 共 9 命中（4 注释 + 5 代码：`:395` 写回 / `:465` 剥 / `:469` 挂回 / `:487` 对齐）；neo 与 P1 两线零 diff。**未实机**。

**残余边缘（登记不改）**：① 窗口内其它代码读余额仍看到虚高值（只活到同 tick `:1684`，对 HUD/跨线程不可见）；② `event/DamageEffectCardHandler.java:102` 的嵌套真伤与骰战近战互斥（不可达），若将来交叉再套 `withPhantomPeeled`（无登记时 no-op）；③ 第三方在 LOWEST 段之后再改金额且不调 `compensateAbsorption` 会回退偏移；④ 非玩家目标的登记无 compensate 出口，但同 tick `:1684` 照常扣，跨 tick 由 `gameTime` 过期与 `:346` 兜底。

### 7.6 法伤加成折叠为单次结算（方案 A，2026-10-27 19:3x–20:2x，双线）

**裁决**：N15 由用户裁决「必须改回单次结算」（m20709）；两路只读验证（`docs/features/spell-single-hit-option-a.md` / `-option-b.md`）后落地方案一 / 路径 0 —— 把加成**折叠进本次伤害事件**，不再补一次独立真伤。

**第一轮的订正（关键，必须记住）**：初版把折叠点放在 `LivingDamageEvent`（neo `:1789 onLivingDamagePre` / forge `:1680 onLivingDamage`），而这两个派发点都在**护甲与魔法吸收之后** ⇒ 折进去的加成不再吃抗性 / 保护附魔：抗性 II + 保护 IV 放大 **4.63×**、抗性 IV + 保护 IV 放大 **13.9×**（裸目标 ×1.0 不变）。与「加成与本体同管道」的目标语义相反，故折叠点**上移到护甲之前**。

**最终落点（双线）**：
- neo `neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/event/DamageEffectCardHandler.java:73-74` = `@SubscribeEvent(priority = EventPriority.HIGHEST) public static void onLivingIncomingDamagePre(LivingIncomingDamageEvent event)`；唯一写值 `:134 event.setAmount(event.getAmount() + (float) bonus);`
- forge `forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/DamageEffectCardHandler.java:66-67` = `@SubscribeEvent(priority = EventPriority.HIGHEST) public static void onLivingHurtPre(LivingHurtEvent event)`；`:122-128 event.setAmount(event.getAmount() + (float) bonus);`
- 音效拆出：neo `:157-158 onLivingDamageSound(LivingDamageEvent.Pre)`（`:176 Math.round(event.getNewDamage())`）/ forge `:152-153 onLivingDamageSound(LivingDamageEvent)`（`:169 Math.round(event.getAmount())`）—— **不再 `+ bonus`**（加成已在同一实例里），仍挂在原实现的同一个桶（NORMAL）；档位与 HUD 跳字同源，带保护/抗性时数值比旧代码手算的 `getAmount()+bonus` 略低，这是「加成也被减免」的必然结果。
- `combat/SpellDamageContext.java` 两线 event 字段类型随之改为 `LivingIncomingDamageEvent` / `LivingHurtEvent`（全仓无消费者读该字段）。

**「一次 hurt 只加一次」取证**：neo `LivingEntity.java:1153 CommonHooks.onEntityIncomingDamage` 是全文件唯一派发点；forge `:1665 ForgeHooks.onLivingHurt` 唯一派发点（`hurt` 的 `:1136` 无敌帧差额 / `:1142` 全额二选一，各只调一次 `actuallyHurt`）。

**数值口径（相对改造前，按本体源分两类）**：折叠后加成随**本体源**的标签走，而旧加成恒走 `astral_dice:true_damage`（两线 `src/main/resources/data/minecraft/tags/damage_type/bypasses_armor.json:3` 与 `bypasses_cooldown.json:3` 都在内）⇒ (a) **本体源也不吃护甲时**（P0 全部法伤牌/技能的 `astral_dice:card_spell`，两线同）乘性因子与旧实现**逐位一致**：裸 ×1.0、保护 IV + 抗性 II ×0.216、保护 IV + 抗性 IV ×0.072；(b) **本体源不在 `bypasses_armor` 内时**（近战 / 箭矢 / 部分第三方法术类型）加成改为**吃护甲**，相对旧实现下降 —— 反例 护甲 20 / 韧性 8 / 本次 10 ⇒ `f = 2 + 8/4 = 4`、`armorEff = clamp(20 - 10/4, 4, 20) = 17.5`、因子 `1 - 17.5/25 = 0.30`，再叠保护 IV + 抗性 II 由旧 0.216 降到 0.0648（约 30%）—— 这是「加成与本体同管道」的**预期语义变更**（2026-10-27 独立复核 R6 订正，初稿漏算护甲）；(c) **无敌帧**：旧加成源带 `bypasses_cooldown` ⇒ 必落地，折叠后与本体同命（neo 参与 `LivingEntity.java:1190-1197` 差额分摊 / forge 在 `:1131-1142` 可整段丢弃）⇒ 连击时加成可能被吞，登记为已知差异待裁决。真正的行为修正是「只结算一次」的那些环节：吸收钳制、筹码固定点数减伤、按次触发的受击被动、跳字与音效、第三方对多次判定的捕获。

**连带删除**：`APPLYING_TRUE_BONUS` ThreadLocal + `isApplyingTrueBonus()` + 三处消费者（两线 `LinglingSignEvents` 的 +5 闸门、`DiceCombatEvents` 的 N7 狂暴反噬闸门）—— 同一击不再有第二次 hurt，闸门失去判据。⚠️ **旧行为订正**：那道 +5 闸门本来就排除了嵌套加成段，所以「一次命中 +10」的说法不成立，折叠前后都是 **+5 一次**（neo `event/LinglingSignEvents.java:93` 注释已写明）。

**骰战覆盖风险（已核不可达）**：法伤源 `getDirectEntity() == null` 在 `forge-1.20.1/.../combat/DiceCombatEvents.java:552` 早退，另有 `:576` 近战闸门；若将来可达，settle 会**覆盖**折叠值（加成丢失）而不是再叠一次 —— 注释已写死该前提。

**判据**：双线 `:classes` EXIT 0；javap 两线 `DamageEffectCardHandler` 类面只剩 `LIVING_PAGE_BIG_HIT_THRESHOLD` + `onLivingIncomingDamagePre`(neo)/`onLivingHurtPre`(forge) + `onLivingDamageSound`，`setAmount` 各 1 处、`hurt` 0 处、`trueDamage` 0 处；P0 两线 `.java` 的 `isApplyingTrueBonus` / `APPLYING_TRUE_BONUS` 只剩注释（P1 `fabric-1.20.1` / `neoforge-26.1.2` 仍是活代码 + 双段 hurt ⇒ 本批范围仅 P0 双线）。**未实机、未重出 jar**。

### 7.7 折叠批独立复核（2026-10-27，R1–R7）—— 两条必须处置项已落地

复核路径两条（结论一致）：一次性只读 `subagent` + 复用 teammate `verify-opt-a`。原 `dsh_code_review` 因「1 个提交 + 23 文件（含 6 份文档）」在 600s 内跑不完被掐（报告目录只留 139 B interrupted stub）⇒ 结论：一个提交 + 大量文档核验的复核必须拆小，或改派只读子代理。

| 项 | 结论 | 关键判据 |
| --- | --- | --- |
| R1 neo 折叠点 | SUPPORTED | `LivingEntity.java:1153` 唯一派发（`CommonHooks.java:296` 全 jar 仅 1 处 new），`:1159` 以 container 为唯一事实源，早于 `:1164` 盾牌 / `:1178` 冰冻 / `:1182` 头盔 / `:1190-1197` 无敌帧 / `:1787-1789` 护甲与魔法吸收 |
| R2 forge 折叠点 | SUPPORTED（措辞订正） | `LivingEntity.java:1665` 早于 `:1667` 护甲 / `:1668` 魔法吸收；但全 jar 有**两个互斥**派发点（另一处 `player/Player.java:942` 覆写不调 super）⇒「全 jar 唯一」不成立，「LivingEntity 内唯一」成立 |
| R3 `SpellDamageContext` | SUPPORTED | 两线字段类型与处理器入参一致；全仓无消费者读该字段（`DiceCombatModifiers` 读的是 `DiceCombatContext.event`） |
| R4 折叠残留 | 部分成立 | P0 两线 `.java` 只剩注释、判据链完整；但 mapping 文档 `:56/:106/:472` 仍教旧闸门（**已订正**），且 P1 两线仍是活代码 |
| R5 骰战覆盖 | SUPPORTED + 窄风险（**已加闸门**） | 本仓法伤源 `new DamageSource(holder, null, causing)` ⇒ `getDirectEntity()` 恒 null ⇒ 必早退；第三方法术源若 direct = 玩家本人且主手近战武器则可进 `settleDiceDamage` ⇒ 已在两线 `combat/DiceCombatEvents.java` 的近战闸门前加 `if (SpellDamageRegistry.isSpellDamage(source, directEntity)) return;` |
| R6 数值不变 | **REFUTED（已订正口径）** | 旧加成走 `true_damage`（在 `bypasses_armor` 内）⇒ 不吃护甲；折叠后随本体源。见 §7.6 数值口径 (a)(b)(c) |
| R7 音效 | SUPPORTED | 音效处理器与折叠处理器同集同序，读数已去 `+bonus`（neo `:176` / forge `:169`）；既存不对称：neo 读吸收前、forge 读吸收后 |

**两线四点不对称（本批引入，已写进两线 handler 注释）**：① 无敌帧——neo 折叠点在 `LivingEntity.java:1190` 之前（加成参与差额分摊），forge 在 `actuallyHurt` 之后（与本体同命）；② 冰冻 ×5 / 头盔 ×0.75——neo 加成吃，forge 不吃；③ 盾牌 `onDamageBlock`——neo 折算，forge 不参与；④ onHit 副作用——neo 被无敌帧丢弃的击仍执行 `modifier.onHit`，forge 不执行。

**既存不对称（非本批）**：forge `event/LinglingSignEvents.java:125` 有 `DiceCombatEvents.compensateAbsorption(target, event.getAmount())`，neo 无对应（因 neo 折叠点在其之前）。

**范围**：本批只覆盖 P0 双线。P1 `fabric-1.20.1` / `neoforge-26.1.2` 仍是「双段 hurt + 活闸门」⇒ 「全仓单次结算」不成立。**未实机**。

### 7.8 三条裁决落地（2026-10-27 20:5x，用户裁决）

| 裁决 | 落地 |
| --- | --- |
| **N14：AOE 命中也算攻击行为** | 两线 `combat/DiceCombatEvents.java` 把「赐福 / 降神倒计时启动」与「秘密侦探隐匿解除」两个副作用块提到 AOE 闸门**之前**：原先合并的 `if (isInternalAoe() || counterDepth > 0) return;` 拆成两段 —— `counterDepth > 0`（反击链自算伤害）仍在原位置整段早退，`isInternalAoe()` 则下移到「生物近战 / 玩家攻击」分支之前（forge 现 :521 与 :545 附近，neo :300 与 :324 附近）。效果：AOE 的每个被命中目标都算一次攻击行为（按目标逐个计），但骰战结算仍只对主目标发生。 |
| **法伤加成必须绕过护甲（特殊处理）** | 两线 `event/DamageEffectCardHandler.java` 新增 `foldBonusBypassingArmor(target, source, base, bonus)`：源自身带 `bypasses_armor`（本仓全部 `astral_dice:card_spell` 法伤都在内）或目标护甲为 0 时直接 `base + bonus`；否则在护甲**之前**二分求解 x 使 `CombatRules.getDamageAfterAbsorb(x) = getDamageAfterAbsorb(base) + bonus`（区间 `[0, 8*want]`，40 次迭代）⇒ 护甲之后的总量 = 本体护甲后值 + bonus，**加成等效绕过护甲**，而抗性 / 保护 / 吸收 / 受击倍率仍对整段只作用一次。数值口径因此回到与旧 `true_damage` 段一致（旧段也只绕过护甲、吃抗性与保护）⇒ §7.6 数值口径 (b) 的「预期语义变更」已被本条取代（不再下降）。 |
| **连击：保留 cooldown 现状** | 不给加成单独加 `bypasses_cooldown`；连击时加成与本体同命（被无敌帧吞掉即一起吞）。§7.7 登记的「neo 参与差额分摊 / forge 整段丢弃」保留为已知差异。 |

**判据**：双线 `:classes` EXIT 0（本批 3s / 2 executed）；两线 helper 出现在 `DamageEffectCardHandler` 内、折叠行改为调用它；AOE 顺序可由源码顺序核对（副作用块行号 < `isInternalAoe()` 行号）。**未实机**。
### 7.9 近战链路专项审计与修复（2026-10-27 21:0x–21:3x，P0 双线）

用户指令（2026-10-27）：「继续验证近战攻击类是否有遗留问题（重点关注 forge 端），争取一次全部解决战斗/伤害类问题」。
做法：三路**只读**子代理并行取证（F1–F9 / A1–A6 / MELEE-01–08），互相印证；Lead 用两线 sources jar 逐字复核每条机制前提后落代码。

#### 7.9.1 最关键的平台事实（F1 的前提，本轮新证）

`forge-1.20.1/build/moddev/artifacts/forge-1.20.1-47.4.10-sources.jar` → `net/minecraft/world/entity/player/Player.java:940-965`（`Player#actuallyHurt` **覆写且不调 super**）：

| 行 | 内容 |
|---|---|
| `:942` | `ForgeHooks.onLivingHurt(this, src, amount)`（⇒ `LivingHurtEvent` 在护甲之前派发，玩家路径也走这里） |
| `:944-945` | 护甲 / 魔法吸收 |
| `:946` | `f1 = Math.max(damage - absorption, 0)` |
| `:947` | `setAbsorptionAmount(getAbsorptionAmount() - (damage - f1))` |
| `:948` | `f1 = ForgeHooks.onLivingDamage(this, src, f1)` |
| `:954-963` | `setHealth(getHealth() - f1)` / 统计 / gameEvent —— **没有** `LivingEntity.java:1684` 的第二次 `setAbsorptionAmount` |

⇒ 批次 F 的「settle 写回 `A0 - absorbedShould + H`，等平台 `:1684` 再扣一次 H」在**非玩家**上成立、在**玩家**上不成立：写回值里的幻影 H 永远不会被扣掉，A0=8、F=20 时终态吸收变成 **12**（白得 12 点黄心），且 `compensateAbsorption` 对玩家恰好是恒等式（永远清不掉），并连带抬高鼠鼠护盾钳制上限、让 `ChipDamageHandler` 的「护盾 > 0 直接 return」旁路筹码减伤。

#### 7.9.2 必修清单（本轮全部落地）

| 编号 | 级 | 线 | 问题 | 落点 |
|---|---|---|---|---|
| **F1** | P0 | forge | 玩家受害者的黄心被写高（见 7.9.1）；PvP 与 mob→player 都中，player→mob 不受影响 | `combat/DiceCombatEvents.java` 的 `settleDiceDamage` 新增玩家分支：直接写 `expectedAfter`、**不登记幻影**、`event.setAmount(toHealth)` |
| **F2** | P0 | 双线 | 无骰玩家攻击「赐福合法目标」时弱印记 ×1.10 被乘两次（×1.21；命运的指引激活 ×1.69） | 新增 `public static boolean isDiceBattleSettledDamage(DamageSource, LivingEntity)`，判据与骰战入口**同源**（有骰 ⇒ 看赐福；无骰 ⇒ 看 `isBlessingTarget`）；`onWeakMarkDamage` / `LinglingSignEvents` / `FateGuidanceCardItem` 三处统一改用它 |
| **F3** | P1 | 双线 | 七咒（神秘遗物+）/ `DiceCurseRatio` 在生物近战路径被整段丢弃 | 生物近战路径在终值前乘 ratio 并清零；`FateGuidanceCardItem` 的 `diceCombat` 判据改同源 |
| **A3** | P2 | 双线 | 带「额外加伤」的骰战让受击被动一次攻击触发两次（`extra_damage` 段是嵌套 hurt、`directEntity = null`） | `isHostileAttack` 排除清单加 `ModDamageTypes.EXTRA_DAMAGE` |
| **A4** | P1 | 双线 | 立牌 +5 与筹码减伤 / 气囊同在 LOWEST，先后不可依赖（气囊归零后仍 +5 / +5 先加则气囊白烧） | +5 折进骰战终值；`LinglingSignEvents` 加「骰战已接管则跳过」+「金额 ≤ 0 不追加」 |
| **A5** | P2 | forge | 幻影 H 在第三方取消 / 改金额后残留（本仓气囊已有 finally 补偿，第三方没有） | `PhantomBalance` 增 `expectedAfter`；新增 `onServerTickReconcilePhantoms(TickEvent.ServerTickEvent END)` 只纠上偏 |
| **A6** | P1 | 双线 | 生物近战路径漏了「目标有标记 ⇒ +1」（玩家路径有） | 生物路径补同三行 |
| **F7** | P1 | 双线 | 修女被动「单次受击 > 10 清空加成」读护甲前原版值 ⇒ 骰战下几乎永不触发 | 骰战落地量 > 10 且目标有被动加成时 `AlanaSignItem.clearBonus` |
| **弱印记** | P2 | 双线 | 生物近战路径的 ×1.10 口径（forge 已并入终值，neo 靠事件后乘） | neo 生物路径补并入终值，与 forge 对齐（F2 的连带项） |
| **D3** | P3 | 双线 | `BaseSignItem#onHurt` 的 `amount` 两线口径不同（forge 护甲/药水/吸收后、neo 护甲前）无人写明 | 两线 javadoc 增「amount 口径」段：只用于开关判定，**禁止**数值计算 |

#### 7.9.3 登记不改（本轮定案）

| 项 | 结论 |
|---|---|
| MELEE-04 Papara 主动汲取回血基数 | **保持** `finalDmg`（lang 规格写的是「恢复骰神赐福最终伤害的一半」，不是落地红心量） |
| F8 mob→player 不判敌对 | 2026-09-26 设计即「对所有生物生效」，收窄需用户裁决 |
| MELEE-06 立牌 +5 未限定近战 | 与 lang / 手册规格一致（「造成的任意伤害」） |
| MELEE-07 熊猫人嘲讽反击无 per-swing 去重 | 近战主路径一挥击一实例，重复仅在异常多实例时出现 |
| F5 显示侧 | `DamageNumberAggregator`（LOWEST）在骰战覆盖前后读到中间值；forge 无 Post 事件，搬到别处仍与 `ChipDamageHandler` 同档 |

#### 7.9.4 判据

- 两线 `gradlew.bat :neoforge-1.21.1:classes :forge-1.20.1:classes` ⇒ EXIT 0（forge 89 条 / neo 4 条既有 deprecation 警告）。
- 编译期踩坑：`resolveMobMeleeAttack(Mob mob, Player target, ...)` 的形参**已是 Player** ⇒ 写 `target instanceof Player p` 会报「模式类型 Player 是表达式类型 Player 的子类型」，直接读形参即可。
- **未实机**（本轮为代码级修复；实机判据待用户放行：F1 读黄心终态、F2 无骰 ×1.21、A4 气囊归零后是否仍 +5）。
