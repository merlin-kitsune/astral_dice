# 原版附魔与效果在骰战下失效的修正方案

> 目标版本：1.21.1（基准）/ 1.20.1 / 26.1.2 三线同构
> 状态：**已按第二轮裁决更新（v3）· 代码已落地** —— 库 `2.0.0-SNAPSHOT.2` 已发布到 mavenLocal，
> 三线 `DiceCombatEvents` / `DiceCombatModifiers` 已改造并编译通过，已按 dev 契约成对部署（本模组 jar + 库 jar）
> 效果预览：`docs/balance/dice-combat-tuning.html`（输出/承伤曲线、陡峭度、修饰器复检矩阵）
> 取证来源：三线 `build/moddev/artifacts/*-sources.jar`（Mojang 官方映射反编译产物）+ 本仓 `combat` 包源码

---

## 0. 摘要

| # | 失效项 | 方向 | 现状 | 归因 | 修法 |
|---|---|---|---|---|---|
| A | **锋利**（武器伤害附魔） | 玩家→受击方 | Δ = 0% | 攻击点只读 `ATTACK_DAMAGE` **属性**，未调 `EnchantmentHelper.modifyDamage` | 攻击点起始值改为「经附魔后的值」 |
| B | **暴击**（×1.5） | 玩家→受击方 | Δ = 0% | `Player#attack` 算出的暴击已并入被丢弃的 `f`；骰战未复刻判定 | 复刻判定并乘入攻击点 |
| C | **保护**（附魔） | 受击方 | 仅经 `getNewDamage()` 泄漏（量级失真） | 骰战防御点只有「护甲÷2 + 韧性」，无保护项 | 受击方乘算因子 `(1 − clamp(prot,0,20)/25)` |
| D | **抗性提升**（效果） | 受击方 | 同上 | 同上 | 受击方乘算因子 `(25 − 5×(amp+1))/25` |
| E | **攻击冷却**（同源缺口，建议一并） | 玩家→受击方 | 无衰减 | 未读 `getAttackStrengthScale` | 与 A 天然耦合，一行等价复刻 |

**核心判断**：A/B/C/D 四者是**同一类错误** —— 骰战在 `LivingDamageEvent.Pre` 以 `setNewDamage` **覆盖式写入**自算终值，于是原版在 `Pre` 之前的全部计算（护甲 → 抗性+保护 → 附魔加伤 → 暴击 → 冷却缩放）都被整段丢弃。现状唯一残留的 C/D 影响来自「生物→玩家」路径把 `getNewDamage()` 当**加项**用 —— 那是**泄漏**而非设计，且造成两条路径的结构性不对称。

**结论**：修法不是打补丁，而是**把原版被丢弃的那几项显式重算回来**，并明确「哪些归骰战、哪些归原版」。

---

## 0.1 第一轮裁决结果（v2，据此完成机制设计）

| 原待裁决项 | 裁决 | 本文档对应改动 |
|---|---|---|
| §3.5 `getNewDamage()` 去留 | **采纳方案 A（移除）** | §3.3 / §3.4 / §3.6 已按方案 A 改写 |
| §3.2 暴击口径 | **简单口径**（×1.5 作用于整个攻击点） | §3.2 口径表已锁定 |
| §3.1 攻击冷却是否一并 | **一并处理** | §3.1 的 A+C 合并实现成为必做项 |
| §7 附魔项是否进显示口径 | **加入** | §7 表格已把 A 行改为「进」 |

## 0.2 第二轮裁决结果（v3 —— **当前落地口径**）

| # | 第二轮裁决 | 落地内容 |
|---|---|---|
| ① | **卡牌基数不压缩** —— 卡牌是「攻击-特大每次攻击提供的伤害加成在 1~10 之间随机变化」的**临时性加成**，属运气成分，不作为曲线压缩杠杆 | `card_coef` 保持 **1.0**（§11 档位 C 原名 `0.65` 已回退） |
| ② | **保留卡牌基数执行 C 方案** | 抬基数 + 凹斜率 + 相对下限三项落地，**唯卡牌项不动** |
| ③ | **暴击口径改为分拆**，以确保实测倍率 = **1.5** | §3.2 由「简单口径」改为「**分拆口径**」：`×1.5` 只乘**原版基础段**（同原版域），附魔增量与骰点/卡牌**不入暴击域** |
| ④ | **`resolve` 相对下限入库** —— 定性为「基本玩法的一次大型机制调整」 | `DiceBattleResolver.RELATIVE_FLOOR_RATIO = 0.15F` 与三参重载直接进**库**（非调用点内联） |

**★ 裁决 ③ 的量化结果**：分拆口径下暴击实测 Δ = **+50%**，与原版 +50% **完全一致**；此前的整点口径（×1.5 作用于整个攻击点）为 +75%（见 `docs/balance/dice-combat-tuning.html` §5 复检矩阵两行对照）。

**★ 裁决 ① 的必然结果**：输出动态范围只能压到 **6.48×**（原版 2.20×），**无法**压到档位 C 原设计的 4.99× —— 因为卡牌在终局是最大单项杠杆。三条替代路（卡牌冷却 / 同时生效张数上限 / 改乘算）见 `docs/balance/dice-combat-tuning.html` §4.3，**均不在本次裁决范围内**。

**方案 A 的代价已被量化**（`docs/balance/dice-combat-tuning.html`）：移除 `getNewDamage()` 后
生物攻击点从 ≈`2×属性值` 降到 `属性值 + baseAttack`，玩家承伤整体下降 —— 这本身是**修 bug 的正确结果**，
但会连带把整条承伤曲线压低，因此必须配合 §11 的调参档位一起落地。

**本轮同时解决「曲线过陡」**：现状输出动态范围（后期÷前期）为 **10.40×**（原版 2.20×）、
前期输出仅原版的 **0.52×**、前期承伤是原版的 **2.99×**。四项裁决落地后叠加 §11 档位调整，
可把输出动态范围压到 **4.99×**、前期输出提到 **1.02×**、前期承伤收到 **1.12×**。

---

## 1. 取证（第一手）

### 1.1 骰战两条路径的现行公式

**路径 ① 玩家 → 受击方**（`DiceCombatEvents#onLivingDamagePre`，1.21.1 L440-470）

```java
double attackPower = player.getAttributeValue(Attributes.ATTACK_DAMAGE);   // L440 ← 只读属性，无附魔
for (var modifier : DiceCombatModifiers.attackModifiers()) { ... }          // L441-443 本模组加成链
double diceAttackBonus = applyCurseToDicePoints(player, baseDice + attackCardSum);  // L461
attackPower += diceAttackBonus;                                            // L464
if (hasFullPower) attackPower = Math.ceil(attackPower * 1.5);              // L469
finalDmg = Math.max(1, attackPower - defensePower);                        // L567
event.setNewDamage((float) finalDmg);                                      // L600
```

防御点（L546-557）：`CombatFormula.playerDefense(护甲, 韧性)` 或 `mobDefenseInt(分类, 护甲, 韧性)` + 防御骰 + 防御牌。

**路径 ② 生物 → 玩家**（`DiceCombatEvents#resolveMobMeleeAttack`，1.21.1 L836-882）

```java
double mobAttack = TargetBattleStats.baseAttack(mob)
        + mob.getAttributeValue(Attributes.ATTACK_DAMAGE)   // L865
        + event.getNewDamage()                              // L866 ← 已过护甲/抗性/保护
        + mobRoll;
double playerDefense = CombatFormula.playerDefense(护甲, 韧性) + playerRoll + defenseCardSum;
float finalDmg = DiceBattleResolver.resolve(mobAttack, playerDefense);
```

### 1.2 原版四条链的确切位置

**`Player#attack`** —— 三线均已逐行确证：

| 版本 | 位置 | 关键序列 |
|---|---|---|
| 1.21.1 | `Player.java:1222-1290` | `f = ATTACK_DAMAGE` → `f1 = getEnchantedDamage(...) − f` → `f2 = getAttackStrengthScale(0.5F)` → `f *= 0.2 + f2²·0.8` → `f1 *= f2` → `f += getAttackDamageBonus(...)` → crit 判定 → `f *= 1.5` → `f3 = f + f1` → `hurt` |
| 1.20.1 | `Player.java:1124-1178` | `f = ATTACK_DAMAGE` → `f1 = EnchantmentHelper.getDamageBonus(mainHand, mobType)` → `f *= 0.2 + f2²·0.8` → `f1 *= f2` → crit → `f *= 1.5` → `f += f1` → `hurt` |
| 26.1.2 | `Player.java:991-1058` | `baseDamage = ATTACK_DAMAGE` → `magicBoost = scale × (getEnchantedDamage(...) − baseDamage)` → `baseDamage *= baseDamageScaleFactor()`（= `0.2 + s²·0.8`）→ `baseDamage += getAttackDamageBonus(...)` → crit → `baseDamage *= 1.5` → `totalDamage = baseDamage + magicBoost` → `hurtOrSimulate` |

★ **关键等价式**（三线一致，本方案依赖它）：

```
属性经附魔后的值 × 冷却缩放  ==  属性值×缩放 + (附魔值−属性值)×缩放
```

即 1.21.1 的 `f *= coef; f1 *= coef; f3 = f + f1` 等价于 `getEnchantedDamage(...) × coef`。⇒ 冷却缩放与附魔加伤可以**一行同时复刻**。

★ **附魔加伤不吃暴击**：三线都是 `f3 = f + f1`（crit 只作用于 `f`）。若骰战采用「暴击作用于整个攻击点」的简化口径，需在文档中标注与原版的这一处差异。

**附魔加伤的实现（三线不同，且都不在 `Player` 里）**：

| 版本 | 实现位置 | 签名 |
|---|---|---|
| 1.21.1 | `ServerPlayer.java:2162` | `EnchantmentHelper.modifyDamage(ServerLevel, ItemStack, Entity, DamageSource, float)` → float（**替换式**：入参原值、出参修改后值） |
| 26.1.2 | `ServerPlayer.java:2391` | 同上 |
| 1.20.1 | `Player.java:1131` 内联 | `EnchantmentHelper.getDamageBonus(ItemStack, MobType)` → float（**绝对量**） |

⚠️ `Player#getEnchantedDamage` 在三线都是 `protected` 且 **`Player` 的默认实现只是 `return damage;`**，真正实现落在 `ServerPlayer`。⇒ **模组不能调 `player.getEnchantedDamage(...)`**，必须直接调 `EnchantmentHelper`。

**受击方减免（`LivingEntity#getDamageAfterMagicAbsorb`）**：

| 版本 | 抗性系数 | 保护 |
|---|---|---|
| 1.20.1 | `damage × (25 − 5×(amp+1)) / 25` | `EnchantmentHelper.getDamageProtection(Iterable<ItemStack>, DamageSource)` → **int** |
| 1.21.1 | 同上 | `EnchantmentHelper.getDamageProtection(ServerLevel, LivingEntity, DamageSource)` → **float** |
| 26.1.2 | 同上 | 同 1.21.1 |

统一落在 `CombatRules.getDamageAfterMagicAbsorb(damage, points)`（三线同构）：

```java
float f = Mth.clamp(enchantModifiers, 0.0F, 20.0F);
return damage * (1.0F - f / 25.0F);
```

⇒ 抗性因子与保护因子都是**纯乘法**，可连乘，与顺序无关。

⚠️ **效果常量改名**：`MobEffects.DAMAGE_RESISTANCE`（1.20.1 / 1.21.1）→ `MobEffects.RESISTANCE`（26.1.2）。本仓已有先例记录（`RenShieldManager.java:67-68`）。

**生物近战（`Mob#doHurtTarget`）**：

| 版本 | 伤害构成 |
|---|---|
| 1.21.1 | `f = ATTACK_DAMAGE` → `f = EnchantmentHelper.modifyDamage(serverLevel, getWeaponItem(), target, source, f)` → `hurt` |
| 26.1.2 | 同 1.21.1 |
| 1.20.1 | `f = ATTACK_DAMAGE` → `f += EnchantmentHelper.getDamageBonus(getMainHandItem(), mobType)` → `hurt` |

⇒ `Pre` 里的 `getNewDamage()` = **该值经护甲 → 抗性 → 保护三重减免后的结果**。

### 1.3 归因（一图话说清）

```
原版管线:  ATTACK_DAMAGE ─┬─ 附魔加伤 ─┐
                          ├─ 冷却缩放  ├─→ f ─┬─ 暴击 ─┐
                          └─ 物品加伤  ┘      └────────┴─→ hurt(pre) ─→ 护甲 → 抗性+保护 → [Pre 事件]
                                                                                              ↑
骰战:                                                            覆盖写入 ←──── setNewDamage ────┘
                                                                 ↑↑↑↑ 上面整条链被丢弃
```

- **A/B/C** 的失效点在上游（`f` 的构成被丢弃）。
- **C/D** 的失效点在覆盖点：原版的抗性+保护已经算过一遍，但算在**被丢弃的那份值**里；路径 ② 反而把这份值当加项捡回来，于是产生「稀释泄漏 + 两路径不对称」。

---

## 2. 设计约束（不可违背）

1. **公式唯一源 = 库 `starengine_lib` 的 `combat` 包**（内存红线：三线禁再内联公式）。
2. **库只能用三版本都存在且签名一致的 API**（内存红线）。`EnchantmentHelper.modifyDamage` 在 1.20.1 **不存在**，`MobEffects` 常量名三线不同 ⇒ **平台差异必须留在消费方**，库只接收**标量入参**。
3. **结算次序**：`DiceBattleResolver` 已定义 `骰战层 → 乘算因子 → 减算减免`。保护/抗性属**乘算因子**，必须落在 `resolve()` 之后、`ChipDamageHandler@LOWEST` 之前。
4. **HUD / tooltip 同源**：任何进入攻击点/防御点的项，都要在 `DiceCombatModifiers` 的显示口径里有对应处理或明确豁免（否则又出「HUD 一个值、结算另一个值」漂移）。
5. **伤害跳字**：唯一出口 `DamageNumberAggregator` 不变（本次改动只改数值，不改数据流）。
6. **改动按现行 P0/P1 两级模型落地**（P0 两线同批 + P1 待移植；⛔ 原「三线同步」口径已作废，见 `AGENTS.md`〈版本优先级与跨线移植纪律〉）（版本坐标见各线 `gradle.properties`）。

---

## 3. 修正方案

### 3.1 修正 A + C：武器附魔加伤 + 攻击冷却（一行等价复刻）

**1.21.1 / 26.1.2**

```java
// 原: double attackPower = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
double attackPower = player.getAttributeValue(Attributes.ATTACK_DAMAGE);

// 新增:复刻原版 f 的两个上游构成项(附魔加伤 + 攻击冷却缩放)
//   · EnchantmentHelper.modifyDamage 是**替换式** ⇒ 返回值即「属性值经附魔后的值」
//   · 原版 f *= (0.2 + s²·0.8) 与 f1 *= s 两式合并 ⇒ 等价于「附魔后的值 × coef」
float attackStrengthScale = player.getAttackStrengthScale(0.5F);
double cooldownCoef = 0.2 + attackStrengthScale * attackStrengthScale * 0.8;
if (player.level() instanceof ServerLevel serverLevel) {
    ItemStack weapon = player.getWeaponItem();
    if (!weapon.isEmpty()) {
        attackPower = EnchantmentHelper.modifyDamage(
                serverLevel, weapon, target, source, (float) attackPower);   // source = event.getSource()
    }
}
attackPower *= cooldownCoef;
```

**1.20.1**

```java
double attackPower = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
float attackStrengthScale = player.getAttackStrengthScale(0.5F);
double cooldownCoef = 0.2 + attackStrengthScale * attackStrengthScale * 0.8;
double enchanted = EnchantmentHelper.getDamageBonus(
        player.getMainHandItem(),
        target instanceof LivingEntity le ? le.getMobType() : MobType.UNDEFINED);
attackPower = (attackPower + enchanted) * cooldownCoef;
```

**插入位置**：`attackPower = ATTACK_DAMAGE` 之后、`attackModifiers()` 链**之前**（语义：这是「基础攻击力」的构成部分，应参与后续全部加成）。

**七咒隔离**：`applyCurseToDicePoints` 只作用于**骰点 + 卡牌点数**（`baseDice + attackCardSum`），附魔是武器项 ⇒ **不得并入**。现有 L461 的调用已是这个口径，无需改动。

**取舍点**：若只做 A 不做 C，会出现「锋利满额、连击不衰减」的超模不一致 —— 因为原版的附魔加伤**是**吃冷却缩放的（`f1 *= f2`）。本方案建议 A+C 一并，理由即此。

### 3.2 修正 B：暴击

复刻原版判定（**判定条件逐条取自 `Player#attack`，非凭记忆**）：

```java
// 1.20.1 / 1.21.1
boolean criticalAttack = attackStrengthScale > 0.9F            // flag4
        && player.fallDistance > 0.0F
        && !player.onGround()
        && !player.onClimbable()
        && !player.isInWater()
        && !player.hasEffect(MobEffects.BLINDNESS)
        && !player.isPassenger()
        && target instanceof LivingEntity
        && !player.isSprinting();

// 26.1.2:倒数第三项改为 !player.isMobilityRestricted()（Player.java:2019 为 public）
```

应用位置（**第二轮裁决后 = 分拆口径**，1.21.1 L461-470 段）：

```java
// ★ 分拆口径：暴击 ×1.5 只乘「原版基础段」，附魔增量与骰点/卡牌不入暴击域
float attackCooldown = player.getAttackStrengthScale(0.5F);
double attackPower = player.getAttributeValue(Attributes.ATTACK_DAMAGE)
        * (0.2 + attackCooldown * attackCooldown * 0.8);      // 原版基础段（已含冷却缩放）
for (var modifier : DiceCombatModifiers.attackModifiers()) {  // 本模组加成链
    attackPower = modifier.apply(ctx, attackPower);
}
if (isVanillaCriticalHit(player, target, attackCooldown)) {
    attackPower *= 1.5;                                       // ① 基础段吃暴击
}
attackPower += enchantDamageDelta(player, target, source, rawAttackDamage)
        * attackCooldown;                                     // ② 附魔增量：不吃暴击（同原版）
attackPower += applyCurseToDicePoints(player, baseDice + attackCardSum);  // ③ 骰点/卡牌：不吃暴击
if (hasFullPower) attackPower = Math.ceil(attackPower * 1.5);
```

**口径裁决史** —— 由「简单口径」改为「**分拆口径**」（第二轮裁决 ③）：

| 口径 | 含义 | 与原版差异 | 实测暴击 Δ |
|---|---|---|---|
| 简单口径（**已废弃**） | ×1.5 作用于**整个攻击点**（含附魔、骰点、卡牌） | 原版附魔不吃暴击 ⇒ 骰战下附魔也吃 | **+75%**（vs 原版 +50%） |
| **分拆口径（当前采纳）** | ×1.5 只乘 `0.2 + s²·0.8` 段；附魔增量按 `×s` 单列、骰点/卡牌单列 | **无**（与原版同域） | **+50%**（与原版**完全一致**） |

**第三方兼容（可选）**：原版 `Player#attack` 会 fire `CriticalHitEvent`（1.21.1/26.1.2: `CommonHooks.fireCriticalHit`；1.20.1: `ForgeHooks.getCriticalHit`）来允许第三方改倍率。骰战若也 fire，可让第三方暴击修饰器生效，**但同一次攻击会派发两次该事件**（原版一次 + 骰战一次），监听器副作用可能翻倍。**默认建议不 fire，直接用 1.5**，把第三方暴击修饰器列为已知不兼容项。

### 3.3 修正 D：保护 + 抗性 → 受击方乘算因子（两侧对称）

**库侧新增纯函数**（见 §4）：`VanillaMitigation.magicAbsorbFactor(protectionPoints, resistanceAmplifier)`。

**消费方取值（三线不同）**：

```java
// 1.21.1 / 26.1.2
float protectionPoints = 0.0F;
int  resistanceAmp    = VanillaMitigation.NO_RESISTANCE;   // -1 = 无效果
if (!source.is(DamageTypeTags.BYPASSES_EFFECTS)) {
    if (target.hasEffect(MobEffects.RESISTANCE)                      // 1.21.1: DAMAGE_RESISTANCE
            && !source.is(DamageTypeTags.BYPASSES_RESISTANCE)) {
        resistanceAmp = target.getEffect(...).getAmplifier();
    }
    if (!source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS)
            && target.level() instanceof ServerLevel sl) {
        protectionPoints = EnchantmentHelper.getDamageProtection(sl, target, source);
    }
}
double magicFactor = VanillaMitigation.magicAbsorbFactor(protectionPoints, resistanceAmp);

// 1.20.1
int protectionPoints = EnchantmentHelper.getDamageProtection(target.getArmorSlots(), source);  // → int
```

**应用位置（两条路径都要）**：

```java
// 路径 ①(玩家→受击方)  L598 附近
finalDmg *= magicFactor;         // 受击方的保护/抗性
finalDmg *= victimFactor;        // 既有:末影骰子雨中/水下 +40%(同为乘算,顺序无关)

// 路径 ②(生物→玩家)      L874 附近
finalDmg *= magicFactor;
finalDmg *= (float) victimFactor;
```

**为什么放在 `resolve()` 之后**：`DiceBattleResolver` 的类注释已明确 `骰战层 → 乘算因子 → 减算减免`，并说明「消费方的减伤类效果必须在本方法之后应用，且允许把伤害扣到 0」。

**为什么两条路径都要**：原版 `getDamageAfterMagicAbsorb` 是**受击方**的属性，与攻击方是谁无关。只在路径 ② 生效 ⇒ 又制造新的不对称（生物穿附魔甲时同样该吃保护）。

### 3.4 修正 E：生物武器附魔（对称补齐）

路径 ② 的 `mobAttack` 目前用 `ATTACK_DAMAGE` 属性原值。原版 `Mob#doHurtTarget` 还会叠加**生物手持武器的附魔加伤**：

```java
// 1.21.1 / 26.1.2
if (mob.level() instanceof ServerLevel sl) {
    ItemStack mobWeapon = mob.getWeaponItem();
    if (!mobWeapon.isEmpty()) {
        rawMobDamage = EnchantmentHelper.modifyDamage(sl, mobWeapon, target, source, (float) rawMobDamage);
    }
}
// 1.20.1
rawMobDamage += EnchantmentHelper.getDamageBonus(mob.getMainHandItem(), MobType.PLAYER);
```

### 3.5 关键裁决点：`getNewDamage()` 的去留（**已裁决：方案 A**）

`resolveMobMeleeAttack` 现在的 `mobAttack` 含 `event.getNewDamage()`（L866）。该项 = `ATTACK_DAMAGE + 生物武器附魔` 经**护甲 → 抗性 → 保护**减免后的值。三条互斥路径：

| 方案 | 做法 | 结果 | 评价 |
|---|---|---|---|
| **A（推荐）** | 移除 `getNewDamage()`，改用原版 **pre-mitigation 值**（`ATTACK_DAMAGE + 生物武器附魔`），并把保护/抗性改为乘算因子 | 保护/抗性**完整生效**；两路径对称；无重复计算 | **唯一数学正确的方案** |
| B | 保留 `getNewDamage()`，不加乘算因子 | 保护/抗性仍然无效（只是被稀释泄漏） | 等于不修 |
| C | 保留 `getNewDamage()` **并**加乘算因子 | 保护/抗性被计算两次 | 错误，不可取 |

**方案 A 的必然代价**：`getNewDamage()` 移除后，`mobAttack` 从 ≈`2 × 属性值` 降到 ≈`属性值 + baseAttack`。这会**推翻 2026-09-26 已裁决的「属性值 + 事件原值两者都纳入」**，并**降低生物对玩家的实际承伤**（进而降低整条承伤曲线）。

★ 方案 A 是唯一能同时修好 C/D 又不引入重复计算的做法；但它是**平衡性改动**，不是纯 bugfix。⇒ **必须先裁决**。若裁决保留 2× 口径，则 C/D 无解 —— 这一点需要在裁决时明确知悉。

**替代补偿（若采纳方案 A 且希望保持承伤量级）**：上调 `TargetBattleStats.HOSTILE_BASE_ATTACK`（现 5）或改用「属性值 × 系数」。属独立平衡议题，本方案不代做决定。

### 3.6 修正后的完整公式

**路径 ①（玩家 → 受击方）**

```
属性经附魔 = EnchantmentHelper.modifyDamage(武器, 目标, 源, ATTACK_DAMAGE属性)
攻击点     = 属性经附魔 × (0.2 + s²·0.8)          ← A + C
           + Σ 攻击修饰器链                        ← 本模组
           + (骰点 + 卡牌点数) × 七咒系数          ← 本模组
           → ×1.5 若暴击                           ← B
           → ceil(×1.5) 若全力攻击                 ← 本模组
防御点     = CombatFormula(护甲, 韧性) + 防御骰 + 防御牌
终值       = max(1, 攻击点 − 防御点)               ← DiceBattleResolver
           + 标记加成 / × 虚弱印记 / Σ 外部乘算因子  ← 本模组
           × 保护×抗性因子                          ← D（受击方）
           × 受击侧因子(末影骰)                      ← 既有
```

**路径 ②（生物 → 玩家）**

```
原始生物伤害 = baseAttack(分类) + ATTACK_DAMAGE属性 + 生物武器附魔   ← E，且不含 getNewDamage
攻击点       = 原始生物伤害 + 生物骰
玩家防御点   = CombatFormula(护甲, 韧性) + 玩家骰 + 防御牌
终值         = max(1, 攻击点 − 防御点)
             × 保护×抗性因子                                        ← D（玩家侧）
             × 受击侧因子
```

两条路径**结构对称**：都能被受击方的保护/抗性减免。

---

## 4. 库侧改动（`starengine_lib`）

**版本**：`2.0.0-SNAPSHOT.1` → **`2.0.0-SNAPSHOT.2`**（改库必 bump，见 `mc-prereq-lib-version-contract`）。

**新增文件** `common/src/main/java/com/merlinkitsune/starenginelib/combat/VanillaMitigation.java`（**以下为落地后的实码**，命名与文档早前草案略有差异）：

```java
package com.merlinkitsune.starenginelib.combat;

/**
 * 原版「抗性提升 + 保护附魔」两条魔法减伤通道的纯算术复刻。
 * 骰战在 LivingDamageEvent 点用自算值整段覆盖 ⇒ 这两条已算完的减免被丢弃 ⇒ 需显式重算。
 * 护甲不在此列（骰战已把护甲折进「防御力」减法项）。
 * 纯函数、无状态：不注册注册表条目、不注册事件、不反向依赖任何消费方。
 */
public final class VanillaMitigation {

    /** 抗性提升每级提供的减伤百分点（原版 (amplifier + 1) * 5）。 */
    public static final int RESISTANCE_PERCENT_PER_LEVEL = 5;
    /** 抗性通道的分母（原版 25）。 */
    public static final float RESISTANCE_DENOMINATOR = 25.0F;
    /** 保护附魔点数的生效上限（原版 CombatRules#getDamageAfterMagicAbsorb 的上限）。 */
    public static final float MAX_PROTECTION_POINTS = 20.0F;

    private VanillaMitigation() {
    }

    /** 抗性因子；amplifier = null 表示未持有该效果 ⇒ 1.0。I 级 = 0.8、II 级 = 0.6、V 级及以上 = 0。 */
    public static float resistanceFactor(Integer resistanceAmplifier) {
        if (resistanceAmplifier == null) {
            return 1.0F;
        }
        int i = (resistanceAmplifier + 1) * RESISTANCE_PERCENT_PER_LEVEL;
        return Math.max((RESISTANCE_DENOMINATOR - i) / RESISTANCE_DENOMINATOR, 0.0F);
    }

    /** 保护因子；点数 clamp 到 [0, 20]。全套保护 IV（16 点）= 0.36。 */
    public static float protectionFactor(float protectionPoints) {
        float points = Math.min(Math.max(protectionPoints, 0.0F), MAX_PROTECTION_POINTS);
        return 1.0F - points / RESISTANCE_DENOMINATOR;
    }

    /** 两条通道连乘，直接乘到骰战终值上。 */
    public static float magicAbsorbFactor(Integer resistanceAmplifier, float protectionPoints) {
        return resistanceFactor(resistanceAmplifier) * protectionFactor(protectionPoints);
    }

    /** double 入参重载（便于与骰战链上的 double 值直连）。 */
    public static float magicAbsorbFactor(Integer resistanceAmplifier, double protectionPoints) {
        return magicAbsorbFactor(resistanceAmplifier, (float) protectionPoints);
    }
}
```

**同批修改的另三个库文件（本轮的数值再校准 + 下限入库）**：

1. `combat/CombatFormula.java` —— 系数全面重标定，**并删除 `ARMOR_DIVISOR`**（破坏性变更）：

```java
public static final double PLAYER_BASE_DEFENSE = 4.0;     // 原 2.0（抬基数）
public static final double PLAYER_ARMOR_COEF = 0.30;      // 新（原为统一除数 ARMOR_DIVISOR = 2）
public static final double PLAYER_TOUGHNESS_COEF = 0.85;  // 原 1.4（凹斜率）
public static final double MOB_ARMOR_COEF = 0.40;         // 新
public static final double MOB_TOUGHNESS_COEF = 1.0;      // 原 1.125
// playerDefense / mobDefense 由「÷ ARMOR_DIVISOR」改为「× 各自系数」
// mobDefenseInt 仍为 (int) Math.floor(...)
```

2. `combat/TargetBattleStats.java` —— 四分类初始值下调：

```java
public static final int HOSTILE_BASE_DEFENSE = 0;   // 原 2
public static final int HOSTILE_BASE_ATTACK = 4;    // 原 5
public static final int NEUTRAL_BASE_ATTACK = 3;    // 原 4
```

3. `combat/DiceBattleResolver.java` —— **相对下限入库**（第二轮裁决 ④，定性为基本玩法的大型机制调整）：

```java
public static final float MIN_DAMAGE = 1.0F;
public static final float RELATIVE_FLOOR_RATIO = 0.15F;

public static float resolve(float attackPower, float defensePower) {
    return resolve(attackPower, defensePower, RELATIVE_FLOOR_RATIO);
}
public static float resolve(float attackPower, float defensePower, float relativeFloorRatio) {
    float floor = Math.max(MIN_DAMAGE, attackPower * relativeFloorRatio);
    return Math.max(floor, attackPower - defensePower);   // 下限 = max(1 点, 攻击点 ×15%)
}
```

**同步动作**（改库标准流程，**已全部执行**）：

1. ✅ 库 bump `gradle.properties` → `2.0.0-SNAPSHOT.2`；
2. ✅ 库三线 `build` + `publishToMavenLocal`（`BUILD SUCCESSFUL in 9s`）；
3. ✅ 三线 `starengine_lib_version` 同步升到 `2.0.0-SNAPSHOT.2`；
4. ✅ 库 CHANGELOG 双语同步（新增 `未发布（2.0.0-SNAPSHOT.2）` / `Unreleased (2.0.0-SNAPSHOT.2)` 段，含 `.1` 未记录的 `combat` 包引入）。

⚠️ 同版本号**就地覆盖**且闸门只比文件名 ⇒ 改库必重跑部署（内存红线）；整合包处于冻结期时**勿用** `-PdeployToPack`。
⚠️ **本轮实测到的部署陷阱**：`run/<版本>/mods` 里残留着旧版 `starengine_lib-*-2.0.0-SNAPSHOT.1.jar`（`pushToDevRun` 只推本模组 jar）。
必须用 `Start-SelfTest.ps1`（成对 + 先删后拷）才能清掉旧库。已三线执行。

---

## 5. 三线差异表

| 项 | 1.20.1（Forge / Java 17） | 1.21.1（NeoForge / Java 21） | 26.1.2（NeoForge / Java 21） |
|---|---|---|---|
| 武器附魔加伤 | `EnchantmentHelper.getDamageBonus(ItemStack, MobType)` → **绝对量** | `EnchantmentHelper.modifyDamage(ServerLevel, ItemStack, Entity, DamageSource, float)` → **替换式** | 同 1.21.1 |
| 保护点数 | `getDamageProtection(Iterable<ItemStack>, DamageSource)` → **int** | `getDamageProtection(ServerLevel, LivingEntity, DamageSource)` → **float** | 同 1.21.1 |
| 抗性常量 | `MobEffects.DAMAGE_RESISTANCE` | `MobEffects.DAMAGE_RESISTANCE` | **`MobEffects.RESISTANCE`** |
| 暴击失能项 | `!hasEffect(MobEffects.BLINDNESS)` | 同 1.20.1 | **`!isMobilityRestricted()`** |
| 攻击冷却 | `getAttackStrengthScale(0.5F)` | 同 | 同（`0.2 + s²·0.8` 被抽成 `baseDamageScaleFactor()`） |
| 物品额外加伤 | **无** `getAttackDamageBonus` | `ItemStack#getItem().getAttackDamageBonus(target, dmg, src)` | 同 1.21.1 |
| 生物武器槽 | `getMainHandItem()` | `getWeaponItem()`（`LivingEntity` public） | 同 1.21.1 |
| 事件写入 | `event.setAmount(...)` / `getAmount()` | `event.setNewDamage(...)` / `getNewDamage()` | 同 1.21.1 |
| 伤害管线取值 | `LivingDamageEvent`（单类，`isCanceled()` + `getAmount()`） | `Post#getNewDamage()` | **`Post#getHealthDamage()`** |

★ 三线 `DiceCombatEvents` **不得互抄**（内存红线：HUD/公式类三线各自一份）。本次改动点在 `onLivingDamagePre` 与 `resolveMobMeleeAttack` 两处，三线逐一改。

---

## 6. 逐文件改动清单（**已全部落地**）

| 文件 | 位置（1.21.1 行号） | 改动 |
|---|---|---|
| `starengine_lib/.../combat/VanillaMitigation.java` | 新建 | 见 §4（实码） |
| `starengine_lib/.../combat/CombatFormula.java` | 系数区 | 抬基数 + 凹斜率；**删除 `ARMOR_DIVISOR`**（破坏性） |
| `starengine_lib/.../combat/TargetBattleStats.java` | 四分类初始值 | `HOSTILE_BASE_DEFENSE` 2→0、`HOSTILE_BASE_ATTACK` 5→4、`NEUTRAL_BASE_ATTACK` 4→3 |
| `starengine_lib/.../combat/DiceBattleResolver.java` | `resolve` | 新增 `RELATIVE_FLOOR_RATIO=0.15F` + 三参重载（**入库**） |
| `neoforge-1.21.1/.../combat/DiceCombatEvents.java` | L440 附近（攻击点初始化） | A+C：附魔加伤（`×attackCooldown`）+ 冷却缩放 |
| 同上 | L461-470 | B：**分拆口径**暴击（`×1.5` 只乘基础段） |
| 同上 | L546-598 | D：受击方 `magicAbsorbFactor`（`resolve` 之后） |
| 同上 | L864-874（`resolveMobMeleeAttack`） | E（`enchantedMobAttack`）+ 去 `getNewDamage()` + D |
| 同上 | 私有辅助区 | 新增 `enchantDamageDelta` / `enchantedMobAttack` / `isVanillaCriticalHit` / `resistanceAmplifierOf` / `protectionPointsOf` |
| `forge-1.20.1/.../combat/DiceCombatEvents.java` | L444 / L461-470 / L546-604 / L865-876 | 同上（API 按 §5 替换：`getDamageBonus` / `getAmount()` / `BLINDNESS`） |
| `neoforge-26.1.2/.../combat/DiceCombatEvents.java` | L484 / L505-514 / L589-644 / L909-920 | 同上（`RESISTANCE` / `isMobilityRestricted` / `getDamageProtection(ServerLevel,…)`） |
| 三线 `gradle.properties` | — | **仅** `starengine_lib_version` → `2.0.0-SNAPSHOT.2`（`mod_version` **不变**，仍 `2.0.0-SNAPSHOT.13…`） |
| `DiceCombatModifiers.java`（三线） | `attackPowerBase` / `attackPowerOf` / 新增 `enchantedAttackDamage` | 见 §7（显示口径切分） |

⚠️ **1.20.1 的 `mod_version` 语义提醒**：其 `mod_version` 自带加载器后缀（`2.0.0-SNAPSHOT.13+forge_1.20.1`），
与仓库根的 `mod_version`（`2.0.0-SNAPSHOT.13`）不同 —— 核版本坐标时勿混淆。

---

## 7. HUD / tooltip 同步口径

`DiceCombatModifiers.attackPowerBase(...)` 同时被三处消费：`attackPowerOf`（**降神快照**）、`attackPowerDisplayOf`（HUD 攻击力条）、`getDisplayAttackRange`（tooltip 区间）。若攻击点加了附魔项而显示口径不加，就会出现「HUD/tooltip 一个值、结算另一个值」的漂移（内存红线）。

| 项 | 是否进显示口径 | 理由与做法 |
|---|---|---|
| A 附魔加伤 | **切分为两口径（落地后实现）** | `EnchantmentHelper.modifyDamage` **需要 `ServerLevel`** ⇒ 客户端（HUD/tooltip）拿不到。故：`attackPowerOf(player, true)` = **含武器**（服务端，含附魔，供降神快照/结算）；`attackPowerOf(player, false)` / `attackPowerBase(3参)` = **不含武器**（客户端，**不含附魔**，供 HUD/tooltip 区间）。与原版客户端 tooltip 同样不显示附魔加伤 ⇒ **非回归**。新增私有 `enchantedAttackDamage(player)` 承载服务端口径（`EnchantmentHelper.modifyDamage(level, 主手, player, playerAttack(player), base)`） |
| C 冷却缩放 | **不进** | 依赖瞬间状态，每 tick 变动 ⇒ HUD 会闪 |
| B 暴击 | **不进** | 瞬态（依赖 `fallDistance`/`onGround`），显示会闪；如需提示应在 tooltip 另起一行 |
| D 保护/抗性 | **不进「防御力」点数** | 它是**乘算因子**，与「防御点」不同量纲 ⇒ 混入会破坏 `CombatFormula` 语义。若要展示，应在 tooltip 单独一行显示「伤害减免 X%」 |

★ 三线 `DiceCombatModifiers` 各一份，不得互抄。
★ **实机已核**：HUD 探针读数与预期一致（裸装 `def=4`、`atk=1`），未见口径漂移。

---

## 8. 数值影响方向与复算方法

**方向（定性）**

| 修正 | 玩家输出 | 玩家承伤 | 备注 |
|---|---|---|---|
| A+C | **↑**（附魔生效）／**↓**（连击衰减） | — | 满蓄力下净升；连击下净降 |
| B | **↑**（暴击时 ×1.5） | — | 跳跃攻击时更明显 |
| D | — | **↓**（保护/抗性生效，量级随配装） | 对抗性 II：×0.6；保护 IV 全套（16 点）：×0.36 |
| E | — | **↑**（生物持附魔武器时） | 少数生物 |
| §3.5 方案 A | — | **↓**（移除 `getNewDamage()` 的 2× 口径） | 与 D 的 ↓ 叠加 |

**复算方法**：`tools/balance_sim/dice_combat_sim.py` 的 `dice_mob_to_player(...)` 已参数化 `player_prot` / `player_resistance_amp` / `mob_weapon_bonus`，改 `exp2` / `exp3` 的调用即可量化。**本方案未代跑**，因为 §3.5 的裁决会决定基线口径 —— 先裁决再复算，避免白跑一轮。

---

## 9. 验收清单（2026-09-26 更新：标 ✅ 的已实测通过）

**编译期**

- [x] ✅ 库 `build` + `publishToMavenLocal` 通过（`BUILD SUCCESSFUL in 9s`）；三线 `build` 通过（`BUILD SUCCESSFUL`，28 tasks）
- [x] ✅ `javap -p` 核 `VanillaMitigation` 四个方法 + `RELATIVE_FLOOR_RATIO` + `resolve(float,float,float)` 全在位（防「同版本号就地覆盖、旧件漏网」）

**静态核对**

- [x] ✅ 三线 `DiceCombatEvents` 的改动用**各版本** API（§5 表逐项核，尤其 26.1.2 的 `RESISTANCE` / `isMobilityRestricted`）
- [x] ✅ `resolveMobMeleeAttack` 内已无 `getNewDamage()` 参与攻击点（1.20.1 = `getAmount()`）
- [x] ✅ 保护/抗性因子在**两条路径**都应用，且都在 `resolve()` 之后

**实机断言**

- [x] ✅ **运行时链接**（1.21.1 实机）：日志 0 ERROR、无 `NoSuchMethodError`/`LinkageError`；`StarEngine Lib 2.0.0-SNAPSHOT.2` + `astral_dice` 均加载；JarJar 按设计采纳 mods 目录的独立库 jar
- [x] ✅ **新基数实时生效证据**：HUD 探针裸装读数 `def=4`（`armor=0`/`tough=0`）—— 旧库为 `2`，直接证明 `PLAYER_BASE_DEFENSE=4` 已在运行时生效
- [ ] 锋利 V 武器 vs 无附魔武器 ⇒ 骰战输出差值 ≈ 附魔加伤（±roll 噪声）—— **仿真已对齐（+42.9% vs 原版 +42.9%）**，实机未做
- [ ] 跳跃暴击 vs 站立攻击 ⇒ 差值 ≈ ×1.5 —— **仿真已对齐（+50.1% vs 原版 +50.0%）**，实机未做
- [ ] 抗性 II 药水 ⇒ 承伤 ≈ ×0.6（仿真 −40.0% = 原版）
- [ ] 保护 IV 全套 ⇒ 承伤 ≈ ×0.36（16 点）（仿真 −64.0% = 原版）
- [ ] **对称性**：同一对（保护/抗性）在「玩家→生物」与「生物→玩家」两侧的相对变化率**同号同量级**
- [x] ✅ 全 UP-TO-DATE 而 jar 仍旧 = 陷阱 ⇒ 已核 jar 时间戳（`run/*/mods` 三线均为本次构建时间戳）+ 开包核内嵌 `starengine_lib-<loader>-2.0.0-SNAPSHOT.2.jar`

**回归**

- [ ] 七咒第四诅咒（−40%）仍只作用于骰点 + 卡牌，**不含**附魔加伤
- [ ] 全力攻击 `ceil` 与暴击 `×1.5` 的叠加结果符合裁决口径
- [ ] 既有 `CUTTER-*` flaky（`slots=2`）**勿据此改判据**

---

## 10. 裁决状态 / 风险 / 不确定性

**已裁决（第一轮四项，见 §0.1）**

1. ~~§3.5 `getNewDamage()` 去留~~ → **方案 A（移除）**。代价（承伤整体下降）已量化，由 §11 档位补偿。
2. ~~§3.2 暴击口径~~ → **简单口径**（**已被第二轮裁决 ③ 推翻，改为分拆口径**）。
3. ~~§3.1 攻击冷却是否一并~~ → **一并处理**。
4. ~~§7 附魔项是否进显示口径~~ → **进**（但见下「显示口径切分」）。

**已裁决（第二轮四项，见 §0.2）—— 全部已落地代码**

1. **卡牌基数不压缩**（`card_coef` 保持 1.0）⇒ 输出跨度按设计停在 6.48×（不再是原方案的 4.99×）。
2. **保留卡牌基数执行 C 方案**（抬基数 + 凹斜率 + 相对下限）⇒ 已入 `CombatFormula` / `TargetBattleStats`。
3. **暴击改分拆口径** ⇒ 实测 Δ 由 +75% 收敛到 **+50.1%（= 原版）**。
4. **`resolve` 相对下限入库**（`RELATIVE_FLOOR_RATIO = 0.15F`）⇒ 已进库，非调用点内联。

**★ 由此派生的一项未确认裁决（原 §10 待裁决 2/3/4 已随第二轮裁决消解）**

- **后期「比原版更痛」的接受度**：最终采纳下满配玩家承伤 `0.57`（原版 `0.25`，≈ 2.3×）。
  这是「压平曲线 + 不压卡牌」的组合代价，**不是**可调项 —— 报告 §4.3 已登记为设计取舍。

**★ 显示口径切分（实现时新增的口径，需知悉）**

- `DiceCombatModifiers.attackPowerOf(player, false)` 起算值改为 `enchantedAttackDamage(player)`，
  但 `EnchantmentHelper.modifyDamage` 需要 `ServerLevel` ⇒ **客户端拿不到附魔加伤**。
  故显示口径切分为：**含武器分支（服务端，含附魔）** 供降神快照/结算；**不含武器分支（客户端，不含附魔）** 供 HUD/tooltip。
  与本模组既有做法一致，且**与原版客户端 tooltip 同样不显示附魔加伤**（非回归）。

**风险**

- **平衡性**：已按第二轮裁决落地（基数 + 斜率），量化见 §11.4。
- **`CriticalHitEvent` 重复派发**：本方案**不 fire**，直接用 1.5 ⇒ 第三方暴击修饰器列为已知不兼容项。
- **`DamageContainer.Reduction` 记录失真（既有，非本次引入）**：原版在 `getDamageAfterMagicAbsorb` 里写了 reduction 明细，骰战随后覆盖 `setNewDamage`，两者不自洽。若第三方模组读该明细会看到失真值。
- **物品额外加伤（`getAttackDamageBonus`）未纳入**：1.21.1/26.1.2 有此链（`MaceItem`/斧等的条件加伤），1.20.1 无 ⇒ 三线不同构。本方案列为**可选**，未纳入。
- **库 `ARMOR_DIVISOR` 删除 = 破坏性变更**：已在库 javadoc 与三个 `gradle.properties` 注释中显式标注 `SNAPSHOT.2 内破坏性变更`，并全仓 grep 确认无 residue。

**待验证**

- `Player#getWeaponItem()` 在 1.20.1 的可访问性未逐行确证（1.20.1 原版 `Player#attack` 用的是 `getMainHandItem()`）⇒ 本方案在 1.20.1 一律用 `getMainHandItem()` 规避。
- `EnchantmentHelper.modifyDamage` 在 `entity`/`source` 为 null 时的行为未验证 ⇒ 当前实现以 `player` 自身作 `entity`、以 `playerAttack(player)` 作 `source`，**未走 null 分支**。
- 26.1.2 的 `Player#getEnchantedDamage` 覆写链已确证在 `ServerPlayer:2391`，但**尚有其它覆写点未全量扫描**（本次只扫 `getEnchantedDamage` / `getDamageBonus` 关键字）。
- **实机数值层断言未做**（锋利/暴击/保护/抗性四项目前只有仿真口径对齐）—— 如需实机取证需新建专用用例。

---

## 11. 调参档位（配套方案 A 的数值再校准）

### 11.1 问题量化（1.21.1，僵尸靶 / 僵尸源，`docs/balance/dice-combat-tuning.html`）

| 指标 | 原版 | 现状骰战 | 定性 |
|---|---|---|---|
| 输出 前期（石剑/裸装/无卡） | 4.92 | **2.54**（0.52×） | 打不动怪 |
| 输出 后期（下界合金/3×特大） | 10.82 | **26.46**（2.44×） | 秒怪 |
| 输出动态范围（后期÷前期） | 2.20× | **10.40×** | 陡 4.7 倍 |
| 承伤 前期（僵尸→裸装） | 3.00 | **8.97**（2.99×） | 裸装被打穿 |
| 承伤 后期（僵尸→钻石+保护16） | 0.25 | **1.00**（触底） | 满配无敌 |
| 前期可承受击数（20 HP） | 6.7 | **2.2** | 前期难度过大 |

根源有二：① 生物攻击点含 `getNewDamage()`，与 `playerDefense` 的 `护甲÷2` 构成**双计** ⇒ 承伤斜率极陡；
② 骰点 + 卡牌作为**加项**直接构成输出动态范围 ⇒ 加法模型天然比原版的乘法模型陡。

### 11.2 三类旋钮

| 类别 | 旋钮 | 作用 | 第二轮裁决 |
|---|---|---|---|
| **基数** | `play_base` 2→4、`mob_atk_base` 5→4、`mob_def_base` 2→0 | 平移曲线上下限（不动斜率） | **采纳** |
| **斜率** | `play_armor_coef` 0.5→0.30、`play_tough_coef` 1.4→0.85、`mob_armor_coef` 0.5→0.40 | 压「低配→满配」跨度（凹化） | **采纳** |
| ~~**上限**~~ | ~~`card_coef`、`bonus_softcap_*`~~ | ~~只削高投入档位（终局卡牌）~~ | **❌ 不采纳** —— 卡牌是「1~10 随机变化的临时性加成」，不作杠杆 |
| **下限语义** | `resolve_floor_ratio` 0 → 0.15 | 把「绝对 1 点」改为「攻击点 ×15%」，治触底失真 | **采纳，且已入库** |

### 11.3 参数（全部叠加在方案 A 之上）—— **最终采纳 = 档位 C 去掉卡牌压缩**

| 旋钮 | 现状 | **最终采纳** | 落点 |
|---|---|---|---|
| `play_base` | 2.0 | **4.0** | 库 `CombatFormula.PLAYER_BASE_DEFENSE` |
| `play_armor_coef` | 0.5 | **0.30** | 库 `CombatFormula.PLAYER_ARMOR_COEF`（原先为统一除数 `ARMOR_DIVISOR=2`，**已删除**） |
| `play_tough_coef` | 1.4 | **0.85** | 库 `CombatFormula.PLAYER_TOUGHNESS_COEF` |
| `mob_def_base` | 2.0 | **0.0** | 库 `TargetBattleStats.HOSTILE_BASE_DEFENSE` |
| `mob_armor_coef` | 0.5 | **0.40** | 库 `CombatFormula.MOB_ARMOR_COEF` |
| `mob_tough_coef` | 1.125 | **1.0** | 库 `CombatFormula.MOB_TOUGHNESS_COEF` |
| `mob_atk_base` | 5.0 | **4.0**（中立 4→**3**） | 库 `TargetBattleStats.HOSTILE_BASE_ATTACK` / `NEUTRAL_BASE_ATTACK` |
| `card_coef` | 1.0 | **1.0（不变）** | —— （第二轮裁决 ①，**不压缩卡牌**） |
| `bonus_softcap_*` | — | **不引入** | —— （原档位 D 的方案已废弃） |
| `resolve_floor_ratio` | 0.0 | **0.15** | 库 `DiceBattleResolver.RELATIVE_FLOOR_RATIO`（**入库**，第二轮裁决 ④） |
| 暴击口径 | 简单口径 | **分拆口径** | 三线 `DiceCombatEvents.isVanillaCriticalHit` + 分段实现（第二轮裁决 ③） |

### 11.4 效果（僵尸靶 / 僵尸源，n=40000 / seed 20260927）

| 配置 | 输出 前期→后期 | 输出动态范围 | 承伤 前期→后期 | 承伤跨度 | 前期可承受击数 |
|---|---|---|---|---|---|
| 原版 | 4.92 → 10.82 | **2.20×** | 3.00 → 0.25 | 0.083 | 6.7 |
| 现状 | 2.55 → 26.46 | 10.38× | 8.98 → 1.00 | 0.111 | 2.2 |
| 方案 A（仅修机制，**分拆暴击**） | 2.55 → 29.46 | 11.55× | 5.98 → 0.36 | 0.060 | 3.3 |
| 档位 B（仅校准基数） | 5.01 → 32.46 | 6.48× | 3.27 → 0.36 | 0.110 | 6.1 |
| **★ 最终采纳（C 去卡牌压缩）** | **5.01 → 32.46** | **6.48×** | **3.37 → 0.57** | **0.169** | **5.9** |

★ 关键判据一：**方案 A 单独落地会让曲线更陡**（输出 10.38× → 11.55×）—— 因为它只修了机制，没动基数。必须与档位一起落地。

★ 关键判据二：**最终采纳与档位 B 的输出跨度完全相同（6.48×）** —— 这正是第二轮裁决 ①（不压缩卡牌）的直接后果：
档位 C 与 B 的唯一差别就是 `card_coef`，而卡牌基数被裁定不动，于是 `resolve_floor_ratio=0.15` 只改善了**承伤侧**
（满配承伤 0.36 → 0.57，不再触底）。**输出跨度压不到原版的 2.20×** —— 已在
`docs/balance/dice-combat-tuning.html` §4.3 说明原因并给出三条替代路（卡牌冷却 / 生效张数上限 / 改乘算），
**均不在本次裁决范围内**。

### 11.5 修饰器复检（裁决落地的验收依据，n=40000）

| 修饰器 | 原版 Δ% | 现状骰战 Δ% | 修正后 Δ% | 判定 |
|---|---|---|---|---|
| 锋利 V（+3） | +42.9% | **+0%** | **+42.9%** | ✅ **完全对齐** |
| 攻击冷却 50% | −60.3% | **+0%** | **−55.3%** | ✅（差值来自骰点不随冷却缩放） |
| **暴击 ×1.5（采纳·分拆口径）** | +50.0% | **+0%** | **+50.1%** | ✅ **完全对齐** |
| 暴击 ×1.5（对照·整点口径） | +50.0% | +0% | +75.0% | ❌ 已废弃（超发 25 个百分点） |
| 保护 IV 全套（16 点） | −64.0% | −36%（泄漏） | **−64.0%** | ✅ **完全对齐** |
| 抗性提升 II | −40.0% | −22%（泄漏） | **−40.0%** | ✅ **完全对齐** |

### 11.6 复现

```bash
python tools/balance_sim/dice_combat_tuning.py --n 40000 --out docs/balance/dice-combat-tuning.json
python tools/balance_sim/render_tuning.py
```

---

## 附：证据索引

| 结论 | 证据位置 |
|---|---|
| 1.21.1 `Player#attack` 全序列 | `neoforge-1.21.1/build/moddev/artifacts/neoforge-21.1.235-sources.jar` → `Player.java:1222-1290` |
| 1.20.1 `Player#attack` 全序列 | `forge-1.20.1/build/moddev/artifacts/forge-1.20.1-47.4.10-sources.jar` → `Player.java:1124-1178` |
| 26.1.2 `Player#attack` 全序列 | `neoforge-26.1.2/build/moddev/artifacts/minecraft-patched-26.1.2.109-sources.jar` → `Player.java:991-1058` |
| 附魔加伤真实实现落点 | `ServerPlayer.java:2162`（1.21.1）/ `:2391`（26.1.2）；1.20.1 内联于 `Player.java:1131` |
| 保护/抗性公式 | `LivingEntity#getDamageAfterMagicAbsorb`：1.21.1 `:1739` / 1.20.1 `:1625` / 26.1.2 `:1941`；`CombatRules.getDamageAfterMagicAbsorb`（三线同构） |
| 生物近战伤害构成 | `Mob#doHurtTarget`：1.21.1 `:1491` / 1.20.1 `:1442` |
| 效果常量改名 | 26.1.2 `LivingEntity.java:1945` = `MobEffects.RESISTANCE` |
| 落地**前**本仓无任何接入 | 全仓 grep `getDamageProtection` / `getEnchantedDamage` / `modifyDamage` / `getAttackStrengthScale` / `getDamageBonus` / `getAttackDamageBonus` ⇒ **0 命中**（落地后这些关键字已出现在三线 `DiceCombatEvents` / `DiceCombatModifiers`，属预期） |

复现脚本：`temp/t110/vanilla_probe2.py` ~ `vanilla_probe5.py`（仅标准库，只读 jar）。

---

## 12. 收尾状态（2026-09-26 20:3x 本轮结束时）

| 项 | 状态 |
|---|---|
| 库 `VanillaMitigation` + 三处系数/初始值 + `resolve` 下限 | ✅ 已入库并 `publishToMavenLocal`（`javap` 核符号通过） |
| 三线 `DiceCombatEvents` / `DiceCombatModifiers` 改造 | ✅ 已改，三线 `compileJava` / `build` 全部 `BUILD SUCCESSFUL` |
| 三线 dev 成对部署（含清掉残留的旧版库 jar） | ✅ 已用 `Start-SelfTest.ps1` 三线执行 |
| 1.21.1 实机冒烟 | ✅ `DAMAGE-NUMBER-1.21.1` 51/51 全 PASS（含红/绿跳字、`dealt`、无 Mixin 失败/崩溃） |
| 1.20.1 / 26.1.2 实机冒烟 | 见任务 #53 收尾记录 |
| 两份模组 CHANGELOG 双语 | ✅ 已更新（改 2 条已记条目 + 新增 1 条 + 库条目并入） |
| 库 CHANGELOG 双语 | ✅ 已新增 `未发布（2.0.0-SNAPSHOT.2）` 段 |
| 效果报告重渲染 | ✅ `docs/balance/dice-combat-tuning.{html,md}` 已按新口径重跑 |
| 本地提交 | 见任务 #54 |

**遗留（不在本轮裁决范围）**

1. **输出跨度压不到原版（6.48× vs 2.20×）** —— 直接后果是「卡牌不压缩」；三条替代路（卡牌冷却 / 同时生效张数上限 / 改乘算）见 `docs/balance/dice-combat-tuning.html` §4.3。
2. **实机数值层断言**未做（锋利/暴击/保护/抗性目前只有仿真口径对齐）—— 如需实机取证应新建专用用例（现有用例不覆盖这五项）。
3. **前端显示口径**已切分（服务端含附魔 / 客户端不含），若日后要求 HUD 也含附魔，需另想服务端下发通道。
4. **`CombatFormula.ARMOR_DIVISOR` 删除**属破坏性变更 —— 已在库 javadoc + 三个 `gradle.properties` + 库 CHANGELOG 三处标注。
