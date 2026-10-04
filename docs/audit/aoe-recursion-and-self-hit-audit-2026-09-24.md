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
