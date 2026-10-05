# 电击手套伤害递归审查 + AOE 自伤 bug（2026-09-24）

> 起因：用户要求「顺便检查电击手套是否存在伤害递归问题」。
> 结论：**当前不递归**（证据链见 §2），但审查过程中**查实一个真实 bug —— AOE 波及会把施法者自己算进目标**（§4，已修复）。
> 不涉及库改动；三线同构。

---

## 1. 被判定的代码路径

`combat/SpellDamageRegistry.java`（三线同构）电击手套修饰器：

```java
public void onHit(SpellDamageContext ctx, double bonus) {
    float total = ctx.event.getNewDamage();
    if (total <= 0) return;
    AABB aabb = ctx.target.getBoundingBox().inflate(ElectricGloveChipItem.AOE_RADIUS);   // 半径 3
    var nearby = ctx.target.level().getEntitiesOfClass(LivingEntity.class, aabb,
            e -> DiceCombatEvents.isBlessingTarget(e, ctx.attacker) && e != ctx.target && e.isAlive());
    var source = ModDamageTypes.trueDamage(ctx.target.level(), ctx.attacker);              // ← 真伤
    DiceCombatEvents.aoeProcessing = true;
    try { for (LivingEntity e : nearby) { e.hurt(source, total); ... } }
    finally { DiceCombatEvents.aoeProcessing = false; }
    ElectricGloveChipItem.disarmAoe(ctx.attacker);                                        // ← 在循环「之后」
}
```

递归的必要条件：`e.hurt(source, total)` 这个 `DamageSource` 能**再次通过法伤链入口**
（`event/DamageEffectCardHandler.onLivingDamagePre`），从而使同一修饰器被再次激活。

---

## 2. 证据链：为什么当前**不**递归

`ModDamageTypes.trueDamage(level, causing)` = `new DamageSource(holder(TRUE_DAMAGE), null, causing)`
⇒ `getDirectEntity() == null`、`getEntity() == causing`（= 攻击者玩家）。

进入 `DamageEffectCardHandler.onLivingDamagePre` 后**逐关**比对：

| # | 入口判定 | 波及伤害的结果 | 判定 |
|---|---|---|---|
| 1 | `target.level().isClientSide()` → return | 服务端 | 通过 |
| 2 | `source.getEntity() instanceof Player` | ⚠️ `getEntity()` 返回 **causing**（不是 direct）⇒ 是玩家 | **通过** |
| 3 | `DiceCombatEvents.isBlessingTarget(target, player)` | 被波及的是怪 | **通过** |
| 4 | `SpellDamageRegistry.isSpellDamage(source, direct)` | 见下表 ⇒ **false** | **拦截 ✅** |

`isSpellDamage` 的四条 matcher 逐条比对（`SpellDamageRegistry` 静态块）：

| matcher | 条件 | `true_damage` 波及伤害 | 结果 |
|---|---|---|---|
| 1 | `direct instanceof AbstractArrow \|\| ThrowableProjectile` | `direct == null` | false |
| 2 | `msgId == "magic" \|\| "indirectMagic"` | `message_id` = `true_damage`（`data/astral_dice/damage_type/true_damage.json`） | false |
| 3 | 遍历 `MAGIC_DAMAGE_TYPES` 精确匹配 | 该表只含 `ars_nouveau`(5) / `goety`(23) / `irons_spellbooks`(14)，**不含任何 `astral_dice:*`** | false |
| 4 | `source.is(ModDamageTypes.CARD_SPELL)` | 不是 `card_spell` | false |

⇒ 在 `onLivingDamagePre` 的第 4 步提前 `return`，**修饰器链不会被再次执行** ⇒ **不递归**。

> ⚠️ 结论的正确表述是「**因为白名单不含 `true_damage`，所以不递归**」，
> **不能**表述成「因为用的是真伤所以不递归」—— `true_damage` 没有任何「不是法伤」的固有属性，
> 它只是恰好不在表里。（`ModDamageTypes` 的 javadoc 亦明确记有「复用 `TRUE_DAMAGE` 不会被判定为法伤」。）

---

## 3. ⚠️ 结构性脆弱点（已登记，**未擅自改**）

`DamageEffectCardHandler` **自己的**真伤加成结算带重入闸门：

```java
private static final ThreadLocal<Boolean> APPLYING_TRUE_BONUS = ThreadLocal.withInitial(() -> Boolean.FALSE);
// 注释自述：「真伤伤害源会再次进入本处理器，若将来某个作用域 matcher 把它判为法伤就会无限递归」
```

⇒ 作者**已认定**真伤会重入该处理器，并用 ThreadLocal 兜住。但 `SpellDamageRegistry` 的两处 AOE
（电击手套 / 定向爆破）**没有等价闸门**，只依赖「白名单不含它」这一事实；而 `disarmAoe` /
`aoeProcessing = false` 都写在 `for` 循环**之后** ⇒ 整个扩散期间武装状态仍为 `true`。

**失效条件**：只要有人把 `astral_dice:true_damage` 加进 `MAGIC_DAMAGE_TYPES`（或把 `message_id` 改成
`magic` / `indirectMagic`），扩散伤害就会重新进入修饰器链 ⇒ 再次 `isAoeArmed == true` ⇒ 再次扩散 ⇒
**指数级无限递归**（且第一次扩展是「3 格内所有目标」）。

**零行为变化的加固建议**：把 `disarmAoe(ctx.attacker)` 提到 `hurt` 循环**之前**（先解除武装再扩散）。
语义不变（本来就是「每周期仅触发一次」），但把「靠白名单兜底」换成「结构上不可能递归」。
同理适用于 `DiceCombatEvents.aoeProcessing` 的置位顺序（先置位、后发伤害 —— 这两处已经是先置位，正确）。

---

## 4. 🚨 查实的真实 bug：AOE 波及会命中施法者自己（**已修复**）

### 4.1 判据

`combat/DiceCombatEvents.isBlessingTarget(target, player)`：

```java
if (target instanceof Player other) {
    return other.getTeam() == null || other.getTeam() != player.getTeam();
}
```

当 `target == player`（施法者本人）时：`other.getTeam()` 与 `player.getTeam()` 是同一个值 ⇒

- **玩家没有队伍**（默认）⇒ `other.getTeam() == null` ⇒ **返回 true**
- 玩家有队伍 ⇒ `getTeam() != getTeam()` 为 false，且 `getTeam() != null` ⇒ 返回 false

而两处 AOE 的目标过滤都是 `isBlessingTarget(e, attacker) && e != ctx.target && e.isAlive()`
—— **只排除了主目标，没有排除施法者自己**。

### 4.2 后果

| 场景 | 结果 |
|---|---|
| 无队伍玩家（绝大多数情况）近距离（≤3 格）攻击目标 | **自己被扩散的真伤打到**（`total` = 主目标的护甲后伤害，`true_damage` 无视护甲） |
| 定向爆破（半径 **6** 格） | 更容易命中自己 |
| 有队伍玩家 | 不受影响（`getTeam() != null` ⇒ 返回 false） |

### 4.3 修复

两处 AOE 的目标过滤均补 `e != ctx.attacker`（电击手套 + 定向爆破），三线同改：

```java
e -> DiceCombatEvents.isBlessingTarget(e, ctx.attacker)
        && e != ctx.target && e != ctx.attacker && e.isAlive()
```

> 注：**没有**去改 `isBlessingTarget` 本身 —— 它「把非同队玩家视为敌对」的口径是**相对他人**设计的
> （骰神赐福、目标选择器等多处依赖），改全局闸门的影响面远大于在 AOE 这一处显式排除。

---

## 5. 附带登记：AOE 波及的**数值口径**不一致（未改，待裁决）

| 效果 | 波及伤害取值 | 含义 |
|---|---|---|
| 电击手套 | `ctx.event.getNewDamage()` | 主目标的**原始伤害**（护甲后、**不含** `bonus`） |
| 定向爆破 | `5 + effectCardDamageBonus(attacker)` | **固定 5** + 立牌/书签加成（与主目标吃的修饰器不同） |

`onHit(ctx, bonus)` 的时序 = 「所有 `apply` 求值完 → `bonus > 0` 时用**独立真伤**结算 bonus → 再跑 `onHit`」
⇒ 在 `onHit` 里读到的 `getNewDamage()` **不含** `bonus`。

⇒ 文案若写「造成**同样伤害**」，玩家容易理解成「含加成的总伤害」。建议后续在文案侧统一为
「造成**基础伤害**」或按实现分别表述。

---

## 6. 本次改动落点（三线）

| 文件 | 改动 |
|---|---|
| `combat/SpellDamageRegistry.java` | 两处 AOE 过滤补 `e != ctx.attacker`（+ 注释说明闸门自反性） |
| `item/card/BaseEffectCardItem.java` | 新增 `isDamageEffectCard(ItemStack)`（伤害效果牌单一事实源） |
| `item/chip/ElectricGloveChipItem.java` | `isDamageEffectCard` 改为委托 |
| `item/chip/MagicQuiverChipItem.java` | 追踪只统计伤害效果牌；取消活体书页例外 |
## 7. 2026-10-05：§3 的结构性脆弱点**已结构性修复**（随电击手套重写）

电击手套按用户裁决重写（见两份 `CHANGELOG` 的 `2.0.0-SNAPSHOT.26` 段与 `AGENTS.md`「筹码一览 → 电击手套」），
**同时**把本文 §3 登记的隐患一并修掉：

1. **不再依赖「白名单恰好不含 `true_damage`」这一可变事实**：扩散改由独立重入闸门
   `DiceCombatEvents#gloveSplashSettling`（`runAsGloveSplashSettlement`）结构性拦下 —— 即便将来把
   `astral_dice:true_damage` 加进 `MAGIC_DAMAGE_TYPES`（或把 `message_id` 改成 `magic` / `indirectMagic`），
   扩散也不会再次进入修饰器链 ⇒ **指数递归在结构上不成立**（不再是"恰好不发生"）。
2. **四处裸 `boolean` 直写 `aoeProcessing` 改为「保存 / 恢复」式**（`runAsInternalAoe` 或手工 save/restore）：
   `DamageCardFxScheduler#resolveBlast` 的 AOE 段、`DamageCardFxScheduler#applySpellDamageAmount`、
   `combat/LivingPageImpact#resolve`、以及手套扩散本身。原写法在**嵌套**时内层 `finally` 会把外层的执行窗口提前关掉
   —— §3 只记了「扩散期间武装态仍为 true」，未覆盖该嵌套窗口问题，本次一并修。
3. **「触发后 / 周期结束才解除武装」的时点问题从根上消失**：不再有玩家级武装状态（附件键 `electric_glove_aoe` 已删），
   扩散绑定在**该牌本次投出的那一次伤害**上（`DamageCardFxScheduler` / `LivingPageFlightScheduler` 的 `Pending#glovePaid`）。
4. **定向爆破**：手套扩散只从其**主目标**算一次；爆破自身的 6 格 AOE 走独立结算段、**不带扩散窗口**
   ⇒ 不二次触发、不递归。

---

## 8. 2026-10-05（第二轮）：一次用量内**同一目标只吃一次扩散/波及**（新增结构性去重）

用户裁决：「**已经吃到一次溅伤的目标应避免重复吃**」。核查后发现这是一条**真实存在、只是恰好被掩盖**
的重复结算路径 —— 与 §3 同属「保证建立在可变事实上」的同类隐患。

### 8.1 路径（定向爆破 + 电击手套）

同一次用量里有**两条独立枚举**，锚点相同（都是那张牌的主目标）、半径 **3 ⊂ 6** ⇒ **交集必非空**：

| # | 枚举 | 位置 | 半径 | 伤害类型 |
|---|---|---|---|---|
| ① | 电击手套扩散 | `combat/SpellDamageRegistry` 手套修饰器 `onHit`（**在** ② 的主目标 `hurt` 内部同步跑完） | `AOE_RADIUS = 3` | `astral_dice:true_damage` |
| ② | 定向爆破自身 AOE | `combat/DamageCardFxScheduler#resolveBlast` | `BLAST_RADIUS = 6` | `astral_dice:card_spell` |

其余三张 FX 牌（激光 / 板砖 / 轨道炮）与活体书页都只有**一条**枚举 ⇒ 不受影响。

### 8.2 为什么此前「看起来没事」（实证）

原版 `LivingEntity#hurt`（NeoForge 1.21.1 源码 `LivingEntity.java:1190-1206`，四线同形）：

```java
if ((float)this.invulnerableTime > 10.0F && !source.is(DamageTypeTags.BYPASSES_COOLDOWN)) {
    if (amount <= this.lastHurt) { this.damageContainers.pop(); return false; }   // ← 整段丢弃
    ... this.actuallyHurt(source, amount - this.lastHurt);                        // ← 只结算差额
} else {
    this.lastHurt = amount;
    this.invulnerableTime = ...postAttackInvulnerabilityTicks...;
    this.actuallyHurt(source, amount);
}
```

四线标签实测（`data/minecraft/tags/damage_type/bypasses_cooldown.json`）：

| 伤害类型 | 在 `bypasses_cooldown` 内 | 后果 |
|---|---|---|
| `astral_dice:true_damage`（手套扩散） | **是** | 走 `else` 分支 ⇒ **免疫无敌帧、永远全额落地**，并写 `lastHurt` / `invulnerableTime` |
| `astral_dice:card_spell`（爆破 AOE） | **否** | `invulnerableTime > 10` 成立 ⇒ 第二次被**丢弃**（等值时）或**只结算差额**（更大时） |

⇒ **当前「不翻倍」是「① 恰好免无敌帧 + ② 恰好不免 + 顺序恰好是 ①→②」三者共同作用的结果**，
不是任何一条显式规则。等值伤害时第二次被整段丢弃（看起来"没重复吃"），一旦 `② > ①`
（例：主目标受减伤而外围目标没有）就会**真的多吃一次差额**；若将来把 `card_spell` 也放进
`bypasses_cooldown`（或给手套扩散换成不免无敌帧的类型），则**逐字翻倍**。

### 8.3 修复（四线）

新增**一次用量**级别的登记窗口，把「先到先得」显式化：

- `combat/DiceCombatEvents#runAsCardSplashResolution(Runnable)` —— 开一个身份集合
  （`IdentityHashMap` 支撑），**保存并恢复**原值 ⇒ 与 `runAsInternalAoe` 同款嵌套安全；
  未开窗口时 `cardSplashResolved == null` ⇒ 登记**恒放行**（单枚举的牌行为完全不变）。
- `combat/DiceCombatEvents#markCardSplashHit(Entity)` —— 登记一次扩散命中；
  返回 `false` = 该目标本次用量内已吃过 ⇒ 调用方**必须跳过**。
- `combat/DamageCardFxScheduler#resolveBlast` —— ① 主目标命中 + ② AOE 循环整体包进窗口；
  ② 的循环体首行 `if (!markCardSplashHit(e)) continue;` ⇒ **①②的交集只结算一次**。
- `combat/SpellDamageRegistry` 手套修饰器 —— 扩散循环同样登记（既为自己去重，
  也让 ② 能看到"这个已经吃过"）。

**语义取向**：手套扩散在 ① 的 `hurt` 内部同步完成 ⇒ 「先到」的必然是手套那一次，
交集目标按**手套口径**结算（= 主目标实际承伤），爆破 AOE 只补 **3~6 格那一圈**
（与用户 2026-10-05 首轮裁决「只从命中的主目标计算」一致）。

### 8.4 产物级验证

`javap -c` 反汇编 `build/libs/astral_dice-2.0.0-SNAPSHOT.27+*.jar`（四线同形）：

```
// SpellDamageRegistry$10#onHit(扩散循环)
29: invokestatic  DiceCombatEvents.markCardSplashHit:(Lnet/minecraft/world/entity/Entity;)Z
32: ifne  38        // 登记成功 ⇒ 正常结算
35: goto  7         // 已登记 ⇒ 跳过该目标
```

`DamageCardFxScheduler` 内亦可见 `runAsCardSplashResolution` 与同样的 `ifne 38 / goto 7`。
