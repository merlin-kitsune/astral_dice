# 批次 D · 法伤结算链独立复核（neoforge-1.21.1 / forge-1.20.1）

> 复核人：独立只读审查（子代理 `batch-d-spell-verify`）
> 日期：2026-10-27　分支：`multi-main`　HEAD：`d0cfb00f`
> 方式：**只读静态取证** —— 本仓源码逐行 + `build/moddev/artifacts/*-sources.jar` 里的**原版源码**逐行。未编译、未启动游戏。
> 上游正本：`docs/features/attack-damage-chain-audit.md`（其 **§2 目标口径**是本复核必须满足的不变量）。
> 写入范围：**本文件是本次唯一写入物**（只新建本文件；未改任何源码 / 资源 / 脚本；未 `git add/commit/push`）。

---

## 0. 口径、路径缩写与取证环境

### 0.1 路径缩写（下文所有 `文件:行号` 均按此展开）

| 缩写 | 展开 |
|---|---|
| `NEO` | `neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice` |
| `FORGE` | `forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice` |
| `NEO-RES` / `FORGE-RES` | 对应线 `src/main/resources` |
| `LE.java` | 原版 `net/minecraft/world/entity/LivingEntity.java`——**行号以 sources jar 为准**，不是本仓文件 |

sources jar：

- `neoforge-1.21.1/build/moddev/artifacts/neoforge-21.1.235-sources.jar`
- `forge-1.20.1/build/moddev/artifacts/forge-1.20.1-47.4.10-sources.jar`

读法（可复跑，纯只读）：

```bash
python - <<'PY'
import zipfile
z = zipfile.ZipFile(r"neoforge-1.21.1/build/moddev/artifacts/neoforge-21.1.235-sources.jar")
src = z.read("net/minecraft/world/entity/LivingEntity.java").decode("utf-8")
for i, ln in enumerate(src.splitlines(), 1):
    if 1785 <= i <= 1807: print(i, ln)
PY
```

### 0.2 工作树快照（取证时刻）

```bash
git -C F:/MCProject/astral_dice_multiloader status --short -uall
```

读取到的状态（**别人的并行改动，本审查员未动**）：

```
 M forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/AstralEventDispatcher.java
 M forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/EventEffects.java
 M forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/PaparaSignItem.java
 M forge-1.20.1/src/main/resources/data/minecraft/tags/damage_type/bypasses_cooldown.json
 M neoforge-1.21.1/... （同名四项）
?? .patch_batch_a.py  ?? .probe_le.py …  ?? models/astral_anvil.bbmodel
?? docs/features/attack-damage-chain-audit.md
```

**关键前提（影响第 7 节的全部结论）**：

> ⚠️ **时效声明**：本表是 §0.2 观测时刻的快照。复核收尾时批次 A 的改动已陆续落盘（N1/N2/N3/N7/N8 部分落地），**§7 的「未落盘」标签与结论请以 §11 的更正为准**。

| 批次 | 条目 | 落盘状态（本次取证时刻） |
|---|---|---|
| A | N1 / N2 / N3 / N7 / N8 | **未落盘** —— `RenShieldManager.java` / `ChipDamageHandler.java` / `DiceCombatEvents.java` 均无改动（`git status` 无这些文件） |
| B | N4 / N5 | **已落盘**（`EventEffects.java`：新增 `hurtAsEventCardPenalty`，三处点数统一走 `unreducible_damage`） |
| C | N6 | **已落盘**（两线 `bypasses_cooldown.json` 加入 `dice_damage`） |
| C | N9 | **已落盘**（neo 改读 `LivingDamageEvent.Post`，forge 读 `LivingDamageEvent#getAmount()`） |

⇒ 第 7 节对 N1/N2/N3/N7/N8 只能**按审计正本 §3 的修法文本推演**，并在每条标注「推演」。

### 0.3 交付格式四项的对账

1. **逐处改动清单**：本次**只新建 1 个文件** = `docs/features/spell-damage-chain-verification.md`（UTF-8 + CRLF，bareLF=0）。无源码/资源/脚本改动。
2. **编译原始输出尾部**：**未做 —— 无法给出**。任务硬约束明令「不跑 `gradlew`（并发编译会抢占）」，故本批**没有** `BUILD SUCCESSFUL` 读数。此项须由批次 A（N1/N2/N3/N7/N8 落地后跑 `:neoforge-1.21.1:classes :forge-1.20.1:classes`）补齐。
3. **两线口径一致取证**：见 §6.3（附可复跑的 `diff` 命令与**真实输出**）。
4. **未做 / 存疑**：见 §10。

---

## 1. 伤害类型与标签

### 1.1 七个 `damage_type`（两线逐字节等价）

```bash
diff -r --strip-trailing-cr \
  neoforge-1.21.1/src/main/resources/data/astral_dice/damage_type \
  forge-1.20.1/src/main/resources/data/astral_dice/damage_type   # 输出为空 ⇒ 等价
```

| 类型（`data/astral_dice/damage_type/*.json`） | `message_id` | `scaling` | `exhaustion` |
|---|---|---|---|
| `card_cost` | card_cost | when_caused_by_living_non_player | 0 |
| `card_spell` | card_spell | 同上 | 0 |
| `dice_damage` | dice_damage | 同上 | **0.1** |
| `extra_damage` | extra_damage | 同上 | 0 |
| `skill_damage` | skill_damage | 同上 | 0 |
| `true_damage` | true_damage | 同上 | 0 |
| `unreducible_damage` | unreducible_damage | 同上 | 0 |

七者均**未**写 `effects` / `death_message_type` ⇒ 全部走默认 `hurt` 音效与默认死亡文案。
`scaling` 全是 `when_caused_by_living_non_player` ⇒ 由**生物**（含玩家）造成才按难度缩放；无实体归属的伤害不缩放。

### 1.2 四个标签（两线 md5 相同，复跑见 §6.3）

| 标签 | 成员 |
|---|---|
| `bypasses_armor` | true_damage, card_spell, skill_damage, extra_damage, card_cost, unreducible_damage（**6**；dice_damage 不在） |
| `bypasses_cooldown` | 同上 6 种 + **dice_damage**（N6 已落盘，`NEO-RES/.../bypasses_cooldown.json:9`） |
| `bypasses_enchantments` | **仅** card_cost + unreducible_damage |
| `bypasses_resistance` | **仅** card_cost + unreducible_damage |

**本仓未定义**的三个相关标签：`bypasses_shield` / `bypasses_invulnerability` / `bypasses_effects`（原版常量见 `net/minecraft/tags/DamageTypeTags.java:9-16`，neo 另多 `:36 BYPASSES_WOLF_ARMOR`）。⇒ 本仓全部 7 种伤害**都不穿盾牌格挡标签、都不穿创造/重生无敌**。

### 1.3 四个"常被混说"的类型的 bypass 面与 scaling 语义差异

| 类型 | 穿甲 | 穿无敌帧 | 保护附魔 | 抗性药水 | 盾牌 | 创造/重生无敌 | 归属形状（`NEO/damage/ModDamageTypes.java`） | 进法伤链 |
|---|---|---|---|---|---|---|---|---|
| `card_spell` | 是 | 是 | 否 | 否 | 否（见下注） | 否 | `cardSpell(level, causing)` `:151-153`，**direct=null** | **是**（matcher ④） |
| `true_damage` | 是 | 是 | 否 | 否 | 否（见下注） | 否 | `trueDamage(level, causing)` `:142-144`，**direct=null** | 否 |
| `skill_damage` | 是 | 是 | 否 | 否 | 否 | 否 | `skillDamage(level, causing)` `:160-162`，direct=null | **禁止**（`:234-237` 硬注释） |
| `extra_damage` | 是 | 是 | 否 | 否 | 否 | 否 | `extraDamage(level, causing)` `:169-171`，direct=null | 否 |
| `card_cost` | 是 | 是 | **是** | **是** | 否 | 否 | `cardCost(level, source)` `:181-183`，direct=causing=source | 否 |
| `unreducible_damage` | 是 | 是 | **是** | **是** | 否 | 否 | `unreducibleDamage(level, causing)` `:189-191`，direct=null | 否 |
| `dice_damage` | 否 | 是（N6 后） | 否 | 否 | 否 | 否 | `diceDamage(level, source)` `:125-127`，direct=causing=source | 否 |

**注（重要，属脆弱保证）**：`card_spell` / `true_damage` 之所以**不会被盾牌挡**，不是靠标签，而是靠 `direct=null` ⇒ `DamageSource#getSourcePosition()` 返回 `null`（neo `:119-126` / forge 同形）⇒ `LivingEntity#isDamageSourceBlocked` 里 `if (vec32 != null)` 不成立（neo `:1362-1380` / forge `:1301-1320`）。**一旦有人改用带位置的构造器，盾牌就会开始挡法伤**。

`card_spell` 的类内文档解释了三选一的原因（`ModDamageTypes.java:33-37`）：不复用 `true_damage`（不判法伤）、不复用 `dice_damage`（会重走骰战）。`:41-48` 记录 2026-10-08 增补 `bypasses_cooldown` 的实测数据（旧口径下板砖 6→5、激光 4→3）。

---

## 2. 法术判定入口：`SpellDamageRegistry`

### 2.1 `isSpellDamage`（`NEO/combat/SpellDamageRegistry.java:130-136`，FORGE 同构）

```java
130  public static boolean isSpellDamage(DamageSource source, Entity direct) {
132      if (!GameplayConstants.ALLOW_FIREARM_DAMAGE && isFirearmDamage(source)) return false;
         // 顺序遍历 MATCHERS，任一命中即 true
136  }
```

- 军火排除在前：`GameplayConstants.ALLOW_FIREARM_DAMAGE=false`（库 `starengine` `component/GameplayConstants.java:54`，可被 config 覆盖 `:121`）⇒ 默认**把枪械伤害踢出法伤链**。
- `isFirearmDamage`（`:520-544`）三层判据：`FIREARM_DAMAGE_TYPES` 精确表（`:451-505`）→ `FIREARM_PROJECTILE_PACKAGES` 包名前缀（`:509-514`）→ msgId/类名关键词兜底。
- **唯一调用点**：`NEO/event/DamageEffectCardHandler.java:62` / `FORGE/.../DamageEffectCardHandler.java:68`。

### 2.2 全部 matcher（4 条，静态块 `NEO :214-237`；FORGE 行号 +1）

| # | 行号（NEO） | 判据 |
|---|---|---|
| ① | `:217` | `direct instanceof AbstractArrow \|\| direct instanceof ThrowableProjectile` —— 原生箭矢 / 投掷物 / 投掷药水 |
| ② | `:219-222` | `source.getMsgId()` 为 `"magic"` 或 `"indirectMagic"` |
| ③ | `:224-229` | `MAGIC_DAMAGE_TYPES` 精确 key 表（`:51-95`：ars_nouveau 5 / goety 22 / irons_spellbooks 17） |
| ④ | `:233` | `source.is(ModDamageTypes.CARD_SPELL)` —— 本模组卡牌法伤 |

```bash
grep -n "registerMatcher" NEO/combat/SpellDamageRegistry.java
```

`:234-237` 是**硬约束注释**：禁止把 `astral_dice:skill_damage` 加进白名单——它是立牌/技能**固定点数**的专用类型，一旦进链会被全部修饰器放大并触发电击手套波及。需要「不是法伤但无视护甲」时用 `skillDamage(...)`。

### 2.3 判定链之外的第二个闸门

`DamageEffectCardHandler` 在 `isSpellDamage` **之前**还有两道（`NEO :53-59`）：

1. `:57` 施法者必须是玩家：`source.getEntity() instanceof Player`（`getEntity()` 只返回 causing，neo `:65-68` / forge `:63-66`）；
2. `:59` 目标必须过 `DiceCombatEvents.isBlessingTarget(target, player)`（`NEO/combat/DiceCombatEvents.java:1726-1738`）：非同队玩家 / `HostileTargets.isHostile` / Boss / 正在攻击你的怪；**被动友好生物一律不吃法伤加成**。

⇒ 法伤链的作用域 = 「玩家造成 且 命中赐福合法目标 且 伤害类型在 4 条 matcher 里 且 非军火」。四条缺一不进链。

---

## 3. 每条 bonus 源（9 个修饰器，注册顺序 = 求值顺序）

聚合器（`NEO/event/DamageEffectCardHandler.java:67-74` / `FORGE :73-80`）是**单次遍历 + 单累加器**：

```java
for (SpellDamageModifier modifier : SpellDamageRegistry.modifiers()) {
    if (modifier.isActive(ctx)) { active.add(modifier); bonus = modifier.apply(ctx, bonus); }
}
```

⇒ ① 各源的 bonus **串联累加**（不是取最大）；② `isActive` 每次命中只求值一次；③ `onHit` 在 `NEO :116-118` / `FORGE :123-125` 统一在**加成段嵌套真伤之后**执行。

| # | 修饰器 | `isActive` 判据 | bonus 公式 | 上限 | 可叠加 |
|---|---|---|---|---|---|
| 1 | 对怪激光 `NEO :249-260` | **恒 false**（`:253`） | `+4 + effectCardDamageBonus` | — | 已停用 |
| 2 | 对怪板砖 `:262-273` | **恒 false**（`:266`） | `+6 + effectCardDamageBonus` | — | 已停用 |
| 3 | 轨道炮 `:275-286` | **恒 false**（`:279`） | `+8 + effectCardDamageBonus` | — | 已停用 |
| 4 | 定向爆破 `:288-303` | **恒 false**（`:292`） | `+5 + effectCardDamageBonus` | — | 已停用 |
| 5 | 忍术飞镖 `:312-322` | `ctx.hasCurio(NINJA_STAR_CHIP)` | `+MarkManager.getLevel(target)` | 目标层数 ≤ 32 | 是 |
| 6 | 贯穿之铳 `:335-345` | `ctx.hasCurio(PIERCING_GUN)` | `+PiercingGunChipItem.getTargetDefense(target)` | 无内建钳制 | 是 |
| 7 | 标记喷罐 `:347-359` | **无 isActive 覆写 ⇒ 恒 true** | `+0`（`apply` 原样返回） | — | onHit 施一层标记 |
| 8 | 魔法箭袋 `:363-384` | 四重（见 3.4） | `+0` | 30s 冷却 | onHit 返还卡 |
| 9 | 紫晶骰子 `:386-396` | `ctx.hasCurio(AMETHYST_DICE)` | `+AmethystDiceHandler.rollD6(attacker)` | 1..6 | 是 |

**单次命中的 bonus 上界（按现行链）**：`标记层数(0..32) + 目标防御(0..N) + d6(1..6)`，**没有总钳制** `clamp`。唯一的上限是立牌加成自身在 `effectCardDamageBonus` 内部被 `cappedSignDamageBonus`（`SIGN_DAMAGE_BONUS_CAP=120`，`:154` / `:157-159`）截断——而停用的 1-4 号才用它，现役 5/6/9 号**不受该上限约束**。

### 3.1 忍术飞镖（`NINJA_STAR_CHIP` + `MarkManager.getLevel`）

- 触发：`ctx.hasCurio(ModItems.NINJA_STAR_CHIP.get())`（`:315`）。**不要求**「已使用伤害效果牌」——2026-09-24 用户裁决已删该前提（`:304-311` 注释）。
- 公式：`bonus + MarkManager.getLevel(ctx.target)`（`:320`）。
- `getLevel` = `effect.getAmplifier() + 1`，无标记返回 0（`NEO/item/MarkManager.java:55-58`）。
- 层数硬上限：`apply` 时 `Math.min(amplifier + 1, GameplayConstants.MAX_MARKER - 1)`（`MarkManager.java:47`，`MAX_MARKER=32`）⇒ `getLevel` 最大 **32**。
- 标记寿命 1200 tick（`:25`）。
- 可叠加：是（与 6/9 号串联累加）。
- **口径提示**：层数在 `apply` 那一刻读取（循环内），而**施加新标记**发生在 `onHit`（`:116-118`）之后 ⇒ 同一次命中吃不到自己刚加的那层（顺序正确）。

### 3.2 贯穿之铳（`PIERCING_GUN` + 目标防御）

- 触发：`ctx.hasCurio(ModItems.PIERCING_GUN.get())`（`:338`）。**不做**目标范围二次收窄（`:327-333` 注释：与忍术飞镖同形，只由 `isBlessingTarget` 把关）。
- 公式：`bonus + PiercingGunChipItem.getTargetDefense(ctx.target)`（`:343`）。
- `getTargetDefense`（`NEO/item/chip/PiercingGunChipItem.java:26-35`）：
  - `armor = DiceCombatModifiers.effectiveArmorPoints(target)`、`toughness = ARMOR_TOUGHNESS`；
  - 玩家：`floor(CombatFormula.playerDefense(armor, toughness))`（类头 doc：`2 + 护甲×0.30 + 0.85×韧性`）；
  - 其它生物：`CombatFormula.mobDefenseInt(TargetCategory.classify(target), armor, toughness)`（doc：初始 0、`护甲×0.40 + 1.0×韧性`）。
- 上限：**无**（`Math.floor` 直接返回 int）⇒ 「目标越肉、这一发越疼」，高护甲 Boss 上该项可远超 d6。
- 可叠加：是。

### 3.3 紫晶骰子（`AMETHYST_DICE` + `rollD6`）

- 触发：`ctx.hasCurio(ModItems.AMETHYST_DICE.get())`（`:389`）。
- 公式：`bonus + AmethystDiceHandler.rollD6(ctx.attacker)`（`:394`）。
- 库实现（`F:/MCProject/starengine` @ `16b48c4` release 1.1.0，`common/.../event/AmethystDiceHandler.java:25-27`）：
  ```java
  public static int rollD6(Player roller) { return ThreadLocalRandom.current().nextInt(1, D6_FACES + 1); }
  ```
  `D6_FACES=6`（`:19`）⇒ 均匀 1..6。
- **反例（P3 口径提示）**：形参 `roller` **完全未使用** ⇒ 佩戴「诡异骰子 / 绯红骰子」的点数偏置**不会**作用在法伤骰上（对比 `DiceCombatEvents.rollCombatDie`，`NEO/combat/DiceCombatEvents.java:1562-1569` 才走 `WeirdDiceHandler` / `CrimsonDiceHandler`）。若设计意图是"紫晶骰子也吃骰子偏置"，这是缺口；若意图是"法伤骰恒均匀"，应在注释里写明（当前无注释）。
- 可叠加：是（每命中**独立掷一次**，无冷却、无层数）。

### 3.4 魔法箭袋（`MAGIC_QUIVER`）

`isActive`（`:364-373`）四道全过才生效：① `hasCurio(MAGIC_QUIVER)`；② `gameTime >= getMagicQuiverCooldownEnd`；③ `MarkManager.getLevel(target) > 0`；④ `getMagicQuiverTracking(attacker)`（已记录本次使用的伤害效果牌）。

`apply` **不加伤害**（`:376-378` 原样返回 `bonus`）；真正的效果在 `onHit` → `MagicQuiverChipItem.tryProc(ctx)`（`NEO/item/chip/MagicQuiverChipItem.java:70-93`）：施加一层标记 → `createCopyFor(lastCard, attacker)` 返还本次那张伤害效果牌 → `VitaminPillChipItem.giveCard` → 记 `cooldownEnd = now + 600`（`COOLDOWN_TICKS=600`，`:30`）→ 清 `tracking`。

⇒ 它的性质是「**每 30 秒最多一次的卡牌回收**」，不是伤害源。`isEquipped` 另有一道总闸门 `CurioSlotUtil.hasDiceEquipped`（`:37-43`）。

### 3.5 波及面：电击手套 AOE 的每一跳都各自走一遍完整法伤链

`NEO/item/chip/ElectricGloveChipItem.java:159-184`：`executeShock` 收集受害者后，**逐目标**调 `DamageCardFxScheduler.applySpellDamageAmount(level, cause, victim, (int) SHOCK_DAMAGE, false)`（`:170-171`）→ 该出口发 `card_spell`（见 §5.2）⇒ **每个受害者各自触发一次 `DamageEffectCardHandler`**，各自按"该目标的标记层数 / 该目标的防御力 / 各自重掷一个 d6"累加 bonus。

⇒ 一次触发最多 N 个目标 × (标记 + 防御 + d6) 的额外真伤。`:154-155` 的注释说「不包裹 `runAsGloveSplashInstance` ⇒ 天然不可能递归」——**防的是递归，不是防"每个目标各吃一遍加成"**。是否合意需裁决（本审查员倾向：属设计，但应在注释里写明"加法伤链是刻意的"）。

---

## 4. 加成段投递（`DamageEffectCardHandler`）

### 4.1 位置：相对护甲 / 吸收 / 无敌帧

```
neo  hurt()  :1190 无敌帧闸门（invulnerableTime>10 且 !BYPASSES_COOLDOWN ⇒ 差额结算或整段吞）
             :1201-1203 lastHurt=amount / invulnerableTime=20 / actuallyHurt
  actuallyHurt :1787 护甲减免 → :1788 魔法减免（保护附魔 / 抗性）
               :1789 onLivingDamagePre  ← ★ DamageEffectCardHandler 在这里（NORMAL 档）
               :1790-1792 吸收结算
               :1793 f1 = peek().getNewDamage() → :1799-1804 setHealth

forge hurt() :1131 无敌帧闸门（同构）→ :1140-1142 lastHurt / invulnerableTime=20 / actuallyHurt
             :1147 DAMAGES_HELMET×0.75（★在 actuallyHurt 之后，neo 在之前）
  actuallyHurt :1665 LivingHurtEvent（护甲前）→ :1667 护甲 → :1668 魔法减免
               :1669-1670 吸收结算（★按 LivingHurt 段的值算）
               :1680 LivingDamageEvent  ← ★ DamageEffectCardHandler 在这里（NORMAL 档）
               :1683 setHealth(health-f1) → :1684 setAbsorptionAmount(abs - f1)（★模组无法移除的第二次扣吸收）
```

⇒ 加成段的位置是：**护甲/魔法减免之后、本体伤害的最终扣血之前**；neo 在吸收**之前**、forge 在吸收**之后**（这是两线唯一的结构性差异，见 §6.2）。

### 4.2 投递形状与重入闸门

```java
// NEO :82-99 / FORGE :91-106
if (bonus > 0 && !APPLYING_TRUE_BONUS.get()) {
    DamageNumberAggregator.tag(target, source, Group.SPELL);                 // 本体也归入法伤组
    APPLYING_TRUE_BONUS.set(true);
    try {
        var bonusSource = ModDamageTypes.trueDamage(target.level(), player); // direct=null, causing=player
        DamageNumberAggregator.tag(target, bonusSource, Group.SPELL);        // 类型复用 ⇒ 必须显式登记组别
        target.hurt(bonusSource, (float) bonus);                             // 一次独立 hurt
    } finally { APPLYING_TRUE_BONUS.set(false); }
}
```

- ⛔ **2026-10-27 废止（提交 `047bb89c`）**：下文围绕 `APPLYING_TRUE_BONUS` / `isApplyingTrueBonus()` 的分析与建议已失效 —— 该 ThreadLocal 与判定方法连同三处消费者已整体删除，法伤加成改为折叠进同一次伤害事件（见 `docs/features/attack-damage-chain-audit.md` §7.6）。本节保留作历史取证。
- `APPLYING_TRUE_BONUS` 是 `ThreadLocal<Boolean>`（`NEO :33` / `FORGE :29`），对外暴露 `isApplyingTrueBonus()`（`:43-45` / `:41`），唯一外部消费者是 `LinglingSignEvents`（`NEO :94` / `FORGE :107`）。
- 加成段**不写回本体事件**（不 `setNewDamage` / `setAmount`）⇒ 本体的吸收/护甲/附魔结算完全按原版进行；加成段是**第二条独立伤害实例**。

### 4.3 为什么能绕过无敌帧

三层机制叠加，缺一不可：

1. **加成段的类型在 `bypasses_cooldown` 内**（`true_damage`，`NEO-RES/.../bypasses_cooldown.json:3`）⇒ `hurt()` 里 `invulnerableTime > 10.0F && !source.is(DamageTypeTags.BYPASSES_COOLDOWN)` 整条分支被跳过（neo `:1190` / forge `:1131`），因此**永远不会**走到 `amount <= lastHurt ⇒ return false`（neo `:1192` / forge `:1132`）那条"整段吞"的路径。
2. **它是独立的 `hurt()` 调用**，不是本体的数值修改 ⇒ 自己 push 一个 damage container、自己算 `setHealth`。
3. **重入闸门**保证加成段自身（也是 `hurt→onLivingDamagePre`）不会再叠加一次。

### 4.4 连带影响（都是"能绕过"的代价，两线同构）

| # | 影响 | 证据 |
|---|---|---|
| a | **`lastHurt` 被加成段覆盖**：本体先置 `lastHurt=本体值`（neo `:1201` / forge `:1140`），加成段随后置 `lastHurt=bonus` ⇒ 命中后 10 tick 内其它**非 bypass** 伤害的差额结算基准是 bonus，不是本体值 | 同上行号 |
| b | **`invulnerableTime` 被重设为 20**：本体已置 20，加成段再置 20 ⇒ 0.5s 窗口从加成段那次开始计时 | 同上 |
| c | **加成段仍吃"无敌"与"被取消"**：会重走 `isInvulnerableTo`（创造/重生无敌，`Entity.java:2681/2683`；neo LivingEntity 覆写 `:3759-3769`）、`CommonHooks.onEntityIncomingDamage`（neo `:1153`）/ `ForgeHooks.onLivingAttack`（forge `:1089`）⇒ 任何模组取消这次入站伤害都会让加成**整段消失** | 见左 |
| d | **`ChipDamageHandler` 对加成段也独立跑一遍**（`NEO/event/ChipDamageHandler.java:48-49` @LOWEST，只对玩家）⇒ 目标是自己（自伤/反噬场景）时"安全气囊/磨刀石"会对加成段再判一次 | `:52` / `:74` |
| e | **吸收池被两个实例先后瓜分**：neo 是"加成段先吃黄心、本体后吃"；forge 是"本体先吃（`:1669-1670`）、加成段后吃"。总红心伤害 `max(0, 本体+加成-吸收)` 两边相同，但**扣吸收的顺序与分段归属不同** | 见 §4.1 |


---

## 5. 失效 / 恒 `false` 的修饰器（四张伤害效果牌）

### 5.1 逐条证据：`isActive` 恒 `false`

| 牌 | NEO 行号 | FORGE 行号 | 证据（原文） |
|---|---|---|---|
| 对怪激光 | `SpellDamageRegistry.java:249-260`，`isActive` `:253` | `:250-261` / `:254` | 方法体只有一行 `return false;` + 「已停用」注释 |
| 对怪板砖 | `:262-273` / `:266` | `:263-274` / `:267` | 同上 |
| 轨道炮 | `:275-286` / `:279` | `:276-287` / `:280` | 同上 |
| 定向爆破 | `:288-303` / `:292` | `:289-304` / `:293` | 同上（`onHit` 已迁走，`:300-302` 注释：永不执行） |

复跑：

    grep -n "return false;" neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/combat/SpellDamageRegistry.java
    # 253:            return false;
    # 266:            return false;
    # 279:            return false;
    # 292:            return false;

**结论**：四条 **100% 恒 false**，无任何配置/条件分支能翻真（`isActive` 方法体只有 `return false;` 一行）。

### 5.2 停用后的真实伤害出口（不是「牌坏了」，是「伤害搬走了」）

四张牌的伤害现在完全由 `NEO/combat/DamageCardFxScheduler.java` 负责：

    :602-606  applySpellDamage(...)        → base + effectCardDamageBonus(caster)
    :622-649  applySpellDamageAmount(...)  → :625 ModDamageTypes.cardSpell(level, caster)
                                          → :629 dealt[0] = target.hurt(source, (float) Math.max(1, amount))
    :94-97    LASER_DAMAGE=4 / BRICK_DAMAGE=6 / ORBITAL_DAMAGE=8 / BLAST_DAMAGE=5
    :401-448  resolveBlast(...)            → :414 damage = BLAST_DAMAGE + effectCardDamageBonus
                                          → :429 另一份 DamageSource = cardSpell(...)
                                          → :440-441 e.hurt(...)（6 格内其它目标）

⇒ 牌本身的 4/6/8/5 点 + 立牌加成**只经 `applySpellDamage` 加一次**（`:602-606`），**不**再被 4 个停用修饰器加一次 ⇒ **没有重复加成**（与直觉相反，是好消息）。

`ModEffects.MONSTER_LASER` 一类的残留：全仓命中 11 处（`CardShopCatalog:74`、`ModTooltipHandler:1206-1213`、`SpellDamageRegistry:252`、`ModEffects:156`、`ModItems:409`、`BaseEffectCardItem:104`、`EffectCardPeriod:202`、`RandomCardHandler:155` …），**无一处 `addEffect`** ⇒ 效果实体恒不挂载、恒不生效 ⇒ **无害**。

### 5.3 反例（必须记下的一条）

四张牌走 `card_spell` 直伤，而 `card_spell` 自己命中 matcher ④ ⇒ **牌打出的每一跳都会重新触发剩下 5 个修饰器**（忍术飞镖 / 贯穿之铳 / 紫晶骰子 / 魔法箭袋 onHit / 标记喷罐 onHit）。

⇒ 「一张激光牌」的实际伤害 = `max(1, 4 + 立牌加成)` **+** 标记层数(目标) + 目标防御 + d6(若佩紫晶骰子)。
这不是 bug（统一法伤链的设计意图），但**做数值平衡时必须按「牌 + 修饰器」合并看**，不能只看 `LASER_DAMAGE=4`。同理 `resolveBlast` 的 6 格溅射（`:440-441`）也是 `card_spell`，对每个溅射目标**各自**再跑一遍 5 个修饰器。

---

## 6. 两线对等性

### 6.1 逐环节阶段对照

| 环节 | neoforge-1.21.1 | forge-1.20.1 | 结构等价 |
|---|---|---|---|
| 挂载事件 | `LivingDamageEvent.Pre`（`DamageEffectCardHandler.java:52`） | `LivingDamageEvent`（`:58`） | **不等价（阶段不同）** |
| 优先级 | `EventPriority.NORMAL`（`:51`） | 同（`:57`） | 等价 |
| 在管线中的位置 | 护甲 + 魔法减免**之后**、**吸收之前**（`LE.java:1787-1788` → `:1789` → `:1790-1792`） | 护甲 + 魔法减免 + 吸收**之后**（`LE.java:1667-1668` → `:1669-1670` → `:1680`） | **不等价** |
| 读值 API | `event.getNewDamage()` | `event.getAmount()` | 语义等价（都=本层终值），但**基准层不同** |
| 写回 API | `event.setNewDamage(...)`（本文件不写回） | `event.setAmount(...)`（本文件不写回） | 等价 |
| bonus 累加 | 单循环顺序累加（`:67-74`） | 同（`:73-80`） | 等价 |
| 加成段类型 | `ModDamageTypes.trueDamage(level, player)`（`:93`） | 同（`:99`） | 等价（`direct=null`） |
| 加成段投递 | `target.hurt(bonusSource, bonus)`（`:96`） | 同（`:103`） | 等价 |
| 重入闸门 | `APPLYING_TRUE_BONUS` ThreadLocal（`:33`） | 同（`:29`） | 等价 |
| 数字归因 | `DamageNumberAggregator` 挂 `LivingDamageEvent.Post` + `getNewDamage()`（`:113-116`） | 挂 `LivingDamageEvent` + `getAmount()`（`:119-123`） | 语义等价 |
| 音效阈值 | `round(event.getNewDamage() + bonus)`（`:108`） | `round(event.getAmount() + bonus)`（`:115`） | **基准层不同 ⇒ 有吸收时阈值不同** |
| 修饰器注册表 | 9 个（4 停用 + 5 现役） | 同（行号 +1） | 等价（见 6.3） |
| 触发闸门（玩家 / 赐福 / isSpellDamage） | `:54 / :57 / :59 / :61-62` | `:60 / :63 / :65 / :67-68` | 等价 |
| 进骰战的闸门 | `direct=null` ⇒ `DiceCombatEvents.java:331` 早退 | 同 | 等价 |

### 6.2 会造成**数值差异**的差异点（穷举）

| # | 差异点 | 方向 | 量级 |
|---|---|---|---|
| **D1** | forge `LE.java:1684` `setAbsorptionAmount(getAbsorptionAmount() - f1)` —— 吸收被扣**两次**（`:1669-1670` 一次、`:1684` 又一次） | forge 黄心消耗更快 | 每次命中 `+f1` 的额外吸收扣除；法伤链上有**两段**（本体 + 加成）⇒ 每命中扣两次×2 |
| **D2** | 弱标记 ×1.10（fate guidance 再 +0.20）的**基准层**：neo 吸收前（`:1544`）vs forge 吸收后（`:1527`） | 目标有吸收时 forge 偏低 | 最多 `0.10 × min(伤害, 吸收)`（fate guidance 时 `0.30 ×`） |
| **D3** | 黄心钳制（`RenShieldManager`）生效性：neo 挂 `LivingDamageEvent.Pre` LOWEST（`:414 setNewDamage`）可生效；forge 挂 `LivingHurtEvent`（护甲前）`:416 setAmount`，随后 `DiceCombatEvents :1097 setAmount(finalDmg)`（`LivingDamageEvent`）**把钳制值覆盖掉** | forge 法伤可漏到红心 | 最多 `min(伤害, 剩余黄心)`（N1 未落盘） |
| **D4** | `ChipDamageHandler` 的致命判定输入：neo `damageAfterAbsorption = max(0, damage - absorption)`（`:74`）vs forge `damage >= getHealth()`（`:67-76`） | 气囊的归零阈值不同 | 0 或整段伤害（N3 未落盘） |
| **D5** | 音效阈值基数（`:108` vs `:115`） | 有吸收时 bighit/hit 选择不同 | 非数值，听感差异 |
| **D6** | Papara 回血基数：neo 读 `Post.getNewDamage()`（`:87`）vs forge 读 `LivingDamageEvent#getAmount()`（`:87`） | 二者都=扣红心量（N9 已对齐）；但**段数**都是 2 | 见 §7 N9 |
| **D7** | `DAMAGES_HELMET ×0.75` 的顺序（neo `:1182` 在 `actuallyHurt` 前 / forge `:1147` 在 `actuallyHurt` 后） | **对本链 no-op** | 本仓 7 种类型都不在 `minecraft:damage_type/damages_helmet` 内（本仓不含该 tag 文件，原版该 tag 只列原版类型） |

⇒ 结论：**两线「语义」对齐，但「层」不对齐**。法伤链上真正会跑出不同数字的只有 D1/D2/D3/D4 四条；其中 D1 是原版代码（不可移除），D2/D3/D4 靠 N8/N1/N3 落地。

### 6.3 「两线口径一致」取证（可复跑 + 真实输出）

    cd F:/MCProject/astral_dice_multiloader
    # 1) 资源层：damage_type + tags 逐字节
    diff -r --strip-trailing-cr neoforge-1.21.1/src/main/resources/data/astral_dice/damage_type \
                              forge-1.20.1/src/main/resources/data/astral_dice/damage_type
    diff -r --strip-trailing-cr neoforge-1.21.1/src/main/resources/data/minecraft/tags/damage_type \
                              forge-1.20.1/src/main/resources/data/minecraft/tags/damage_type
    # 两条命令均无输出 ⇒ 等价

    # 2) 标签 md5（两线同名同值）
    md5sum */src/main/resources/data/minecraft/tags/damage_type/*.json

实测（本次读数，两线相同）：

    3ee8d72035f49e057a84ae0bd92eb92b  bypasses_armor.json        (223B)
    f7fbb3a37d80d178310ad892ccb7878d  bypasses_cooldown.json     (255B, 含 dice_damage)
    02288108519c17c63be66cc3b0ef5e54  bypasses_enchantments.json (94B)
    02288108519c17c63be66cc3b0ef5e54  bypasses_resistance.json   (94B)

    # 3) 源码层：SpellDamageRegistry（归一化 CRLF 后 diff）
    sed 's/\r$//' neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/combat/SpellDamageRegistry.java > /tmp/a.java
    sed 's/\r$//' forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/combat/SpellDamageRegistry.java   > /tmp/b.java
    diff /tmp/a.java /tmp/b.java

实测差异**只有 6 处、且全部是平台层**：

    1a2    > import ...network.ModNetwork;              （forge 需要显式 import）
    6d6    < import ...event.AmethystDiceHandler;       （neo 需要显式 import）
    211c211 ResourceLocation.fromNamespaceAndPath(...)  ↔  new ResourceLocation(...)
    237a238 （多一个空行）
    252/265/278/291c253/266/279/292  ModEffects.X       ↔  ModEffects.X.get()
    394c395 AmethystDiceHandler.rollD6(...)             ↔  全限定名调用

⇒ `isSpellDamage` 4 条 matcher、9 个修饰器的 `isActive`/`apply`/`onHit` **零行为漂移**。

    # 4) 投递层：DamageEffectCardHandler diff（同上归一化）

实测差异 = 平台 import 群、`@EventBusSubscriber` ↔ `@Mod.EventBusSubscriber`、forge 多 9 行平台注释（`:48-56`）、**事件类型 `LivingDamageEvent.Pre` ↔ `LivingDamageEvent`**、`getNewDamage()` ↔ `getAmount()`、forge 多 4 行「sendBonusDamageNumber 已移除」注释（`:127-130`）。**逻辑同构**。

---

## 7. 与本次修复（N1/N2/N3/N5/N7/N8/N9）的交互

> 前提：N4/N5/N6/N9 **已落盘**；N1/N2/N3/N7/N8 **未落盘**（见 §0.2）。未落盘项一律标「推演」。

| 条目 | 落盘 | 对法伤链**数值**的影响 | 对**触发次数**的影响 | 结论 |
|---|---|---|---|---|
| **N1**（forge 黄心钳制挂错事件） | 未落盘 | 修好后 forge 法伤开始吃黄心钳制 ⇒ 红心伤害**下降**，最多 `min(伤害, 剩余黄心)` | 0（钳制不算触发） | **会改变数值**（forge 变低），需实测对比 |
| **N2**（forge 吸收双扣） | 未落盘 | 修好后黄心消耗减慢 ⇒ 后续受击红心伤害**下降** | 0 | 法伤链上「每段各双扣一次」⇒ 双段叠加，影响比单段路径更明显 |
| **N3**（forge 致命判定输入错层） | 未落盘 | 修好后气囊/保命判定基准变成「扣吸收后的量」⇒ 触发气囊的阈值变化 | 0 | 会改变数值（是否归零） |
| **N5**（事件牌 4/2 点走 generic） | **已落盘** | **法伤链数值不变**（`unreducible_damage` 不命中任何 matcher：非 CARD_SPELL、`direct=null` 非箭、msgId 非 magic、不在 MAGIC 表） | **触发面扩大**：`getEntity()` 由 null 变玩家 ⇒ `DiceCombatEvents.java:291-296`（立牌受击钩子 / BufferShield）+ `:308-314`（赐福/降神倒计时）现在会触发（`isHostileAttack` 认可「非同队且攻击过该玩家」的玩家，`DiceCombatEvents.java:1759-1764`）。`:319-323` 隐匿解除仍不触发（要求 target 非玩家） | 法伤数值不变；**受击侧副作用变大**（P2-5） |
| **N6**（`dice_damage` 进 `bypasses_cooldown`） | **已落盘** | 法伤链直接数值不变（`dice_damage` 不在 matcher） | 骰战伤害现在**穿无敌帧** ⇒ 同一窗口内结算次数↑ ⇒ 它会把 `lastHurt` 重置为自己（`LE.java:1201/1140`）⇒ 随后**非 bypass** 伤害的差额结算基准变化 | **间接数值影响**，需回归 |
| **N7**（狂暴反噬嵌套多次） | 未落盘 | 推演：法伤两段 ⇒ 反噬 `unreducible_damage` 两发（`:1485-1486`）；修好嵌套闸门后**每命中的反噬发数减少** | **会变**（减少） | 会改变数值与触发次数 |
| **N8**（弱标记基准） | 未落盘 | 修好后 forge 法伤本体 ×1.10 的基准从「吸收后」变「吸收前」⇒ 数值**上升**最多 `0.10 × 吸收量` | 0 | **若修法只覆盖骰战路径（`:822/:1097`）而不覆盖独立 handler `:1511`，法伤在 forge 上仍停在吸收后 ⇒ 两线仍不一致**（P2，必须显式覆盖） |
| **N9**（Papara 回血基数） | **已落盘** | neo 回血基数由「吸收前 Pre」改为「扣红心量 Post」⇒ 有吸收时回血**变少**（向 forge 靠拢） | 0（**段数修复前后都是 2**：本体 + 加成各触发一次） | 数值更准；但「两段各回一次血」是既有行为，不是 N9 引入 |

### 7.1 一条必须写进裁决的横切结论：「加成段是独立伤害实例」

法伤命中 = **两次 `hurt()`**。凡是「每次受击」类效果都会被执行**两遍**：

| 效果 | 证据 | 后果 |
|---|---|---|
| Papara 回血 | `NEO/item/sign/PaparaSignItem.java:79` `isHostileAttack` + `:87-88` `heal(max(1, d/2))` | 单次法伤命中回血 = `max(1, body/2) + max(1, bonus/2)`；加成段只有 1-2 点时也**白送 1 点** |
| BufferShield / 立牌受击被动 | `DiceCombatEvents.java:291-296` 在 `:300/:324` 早退**之前**，所以 `card_spell/true_damage` 也会走到 | 受击被动触发两次 |
| 弱标记 ×1.10 | `DiceCombatEvents.java:1527-1545` 无类型过滤 | **加成段也被放大 10%**（两线同） |
| 狂暴反噬 | `:1474-1490` @LOW，无类型过滤 | 两段各反噬 |
| `ChipDamageHandler` | `NEO/event/ChipDamageHandler.java:48-49` @LOWEST | 两段各判一次保命 |
| `RenShieldManager` 黄心钳制 | `:406-414` @LOWEST | 两段各钳一次；因加成段是**先**结算（嵌套在本体 Pre 内），它先吃黄心，本体随后读到的是**已减少的** `remaining` ⇒ 总吸收仍受池上限约束，**不双扣**（与 forge 的 D1 不同——那边是真双扣） |

**审计正本 §2 第 1 条（受击侧修饰统一作用于「本次命中终值 finalDmg」）在法伤链上是结构性偏离**：法伤没有「单一 finalDmg」，本体与加成是两条独立管线。最小修法见 §8 P1-2（**复用现成的 `isApplyingTrueBonus()` 闸门**，成本极低）。⛔ **2026-10-27 废止**：该偏离已由提交 `047bb89c` 按「折叠单次结算」根治，闸门随之删除。

### 7.2 立牌「被追踪 +5」

审计正本 §4 记它已迁 `LivingDamageEvent` LOWEST。若它的实现挂在「受击/伤害事件」上，则同样会在两段各加一次（+5 → +10）。本次**未复核该立牌实现**（超出法伤链范围）⇒ **无法判定**，复核命令：

    grep -rn "LOWEST" neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/*.java | grep -i damage

---

## 8. 缺口与风险（按 P 级）

> ⛔ **2026-10-27 复核订正（Lead + 批次E 双重取证）：本节 P1-1 结论 REFUTED，勿据以改码。**
>
> `forge-1.20.1/build/moddev/artifacts/forge-1.20.1-47.4.10-sources.jar` → `net/minecraft/world/entity/LivingEntity.java:2932-2938`：
> `setAbsorptionAmount(float)` 是**唯一**写入口，且开头就把负值钳到 0（`if (absorptionAmount < 0.0F) absorptionAmount = 0.0F;`）。
> 记 A = 吸收余额、d = 护甲/附魔后伤害、`f1 = max(d - A, 0)`（`:1669`）：
> ① `d <= A` ⇒ `f1 = 0`，`:1670` 把吸收打成 `A - d`，`:1681` 分支跳过 ⇒ 消耗 = d；
> ② `d > A` ⇒ `:1670` 已把吸收打成 0，`:1684` 减到负数**被 `:2933` 钳回 0** ⇒ 消耗 = A。
> ⇒ 可观测消耗恒为 `min(d, A)`，**不存在双扣，吸收也不会变负**；与 neo `:1790-1792` 的 `Math.max(0, A - absorbed)` 等价。
> 本节 §8 P1-1 建议的「在 `LivingDamageEvent` LOWEST 里 `setAbsorptionAmount(abs + event.getAmount())` 补偿」**有害**：
> 该桶位于 `:1680` 之后、`:1683-1684` 之前，骰战路径的 `settleDiceDamage` 已用 `setAbsorptionAmount(A0 - absorbedShould + toHealth)` 精确抵消过，
> 再补偿一次等于每次受击白送 `f1` 点吸收（**已否决，禁止落地**）。
> 唯一真实但极窄的机制：若有处理器在 `LivingDamageEvent` 内**抬高**金额且吸收仍有余额（如法伤 `onWeakMarkDamage` ×1.10），
> `:1684` 会按抬升后的 `f1` 多扣一段吸收，正确补偿量是「**抬升量**」而不是 `f1`，且必须排除已自补偿的骰战路径。
### P1-1 forge 的吸收双扣（`LE.java:1684`）是**原版代码**，模组无法删除

    // forge-1.20.1-47.4.10-sources.jar  net/minecraft/world/entity/LivingEntity.java
    1669  float f1 = Math.max(damageAmount - getAbsorptionAmount(), 0.0F);
    1670  setAbsorptionAmount(getAbsorptionAmount() - (damageAmount - f1));   // 第 1 次扣 = min(d, A)
    1680  f1 = ForgeHooks.onLivingDamage(this, source, f1);
    1681  if (f1 != 0) {
    1683      setHealth(getHealth() - f1);
    1684      setAbsorptionAmount(getAbsorptionAmount() - f1);                  // 第 2 次扣（重复）

代数（d=护甲/附魔后伤害，A=吸收池，f1=扣红心量）：
**实际消耗** = `min(d, A) + f1`，**应为** `min(d, A)`；因为 `:1684` 只在 `f1 != 0` 时执行，此时 `A` 已被打空 ⇒ 吸收量被写成 **负值**（`A − f1 < 0`），而 `:1669` 的 `Math.max(d − getAbsorptionAmount(), 0)` 对负数会**放大后续伤害**。

⇒ N2 若照「只扣一次」的直觉去改事件数值，**必然改不动**（`:1684` 用事件返回的 `f1` 再扣一次）。
**最小修法（不改原版、不上 mixin）**：在 `LivingDamageEvent` 的 **LOWEST** 处理器里把重复扣**补偿**回去：

    if (event.getEntity() instanceof Player player) {
        player.setAbsorptionAmount(Math.max(0.0F, player.getAbsorptionAmount() + event.getAmount()));
    }

补偿量 = `f1`（LOWEST 时 `event.getAmount()` 就是最终 f1），`Math.max(0,…)` 把负值兜回 0。
⚠️ **必须按段计**：法伤有本体 + 加成两段，每段各执行一次 `:1669-1684` ⇒ 每段各需自己的补偿。⚠️ 只在 `LivingHurtEvent` 段（`:1665`）确实产出过 `f1 != 0` 的路径上才有意义；`damageAmount <= 0` 早退（`:1666`）不涉及。

### P1-2 「加成段是独立伤害实例」⇒ 所有「每次受击」效果翻倍

一条闸门就能解决，而且**已经存在**：`DamageEffectCardHandler.isApplyingTrueBonus()`（`NEO :43-45` / `FORGE :41`），目前只有 `LinglingSignEvents`（`NEO :94` / `FORGE :107`）一个消费者。⛔ **2026-10-27 废止**：该闸门已随 `047bb89c` 删除，法伤不再有第二次 hurt，本条修法作废。

**最小修法**：在下列每个 handler 开头加一行

    if (DamageEffectCardHandler.isApplyingTrueBonus()) return;

清单（都要加，否则只修一半）：

| # | 位置 | 目的 |
|---|---|---|
| 1 | `PaparaSignItem.onPaparaBiteHurtHeal`（`NEO :72` / `FORGE :72`） | 回血不翻倍 |
| 2 | `DiceCombatEvents.onWeakMarkDamage`（`NEO :1527` / `FORGE :1511`） | 加成段不被再 ×1.10 |
| 3 | `DiceCombatEvents.onBerserkDamageTaken`（`NEO :1474` / `FORGE :1457`） | 反噬不翻倍 |
| 4 | `ChipDamageHandler.onLivingDamagePre`（`NEO :48` / `FORGE :54`） | 保命只判一次 |
| 5 | `DiceCombatEvents` `:291-296` 块（立牌受击钩子 / BufferShield） | 受击被动不翻倍 |
| 6 | `RenShieldManager.onRenShieldAbsorb`（`NEO :407` / `FORGE :409`） | **谨慎**：加闸门会让加成段**吃不到**黄心钳制 ⇒ 加成段可能漏进红心；建议**不加**，保持「两段各钳一次、总吸收受池上限」（当前行为正确） |

⚠️ 加这行会**降低**现有法伤的实际收益（Papara 回血、弱标记加成、反噬、受击被动都会减半）⇒ 属**玩家可感知的平衡改动**，必须由用户裁决后再动。§9 的 C8 用例就是这条改动的验收器。

### P1-3 两线法伤数值仍不同（N1/N2/N3/N8 未落盘）

D1/D2/D3/D4 四条差异点里，只有 D1 是原版代码、D2/D3/D4 全都等修。
**最小修法** = 按审计正本 §3 落地；落地后必须跑 §9 的 C6/C7/C8。特别注意 **N8 的覆盖范围**：只改 `:822/:1097` 不够，`:1511` 的独立 `onWeakMarkDamage` 也要迁（否则法伤在 forge 上仍停在「吸收后 ×1.10」）。

### P2-1 加成段覆写 `lastHurt` / `invulnerableTime`

`true_damage` 在 `bypasses_cooldown` ⇒ `hurt()` 跳过 i-frame 分支但仍执行 `lastHurt = amount` / `invulnerableTime = 20`（`LE.java:1201-1202` / `:1140-1141`）。
⇒ 命中后 10 tick 内，其它**非 bypass** 伤害的差额结算基准变成「加成段的值」，而不是本体值。
**最小修法**：`invulnerableTime` 是 public 字段，可在嵌套 hurt 前后保存/恢复；`lastHurt` 是 **private**（无 setter）⇒ 需 AT/mixin，**不建议**。文档化为已知行为 + 在 `ModDamageTypes` 注释里点明。

### P2-2 贯穿之铳 bonus **无上限**

`PiercingGunChipItem.getTargetDefense`（`:26-35`）返回 `floor(2 + 护甲×0.30 + 0.85×韧性)`（玩家）/ `mobDefenseInt(...)`（生物），**不做钳制**。对高护甲 Boss 该项可达两位数且随护甲线性增长，并会**乘上命中次数**（紫晶骰子每命中重掷、电击手套每个目标一份）。
**最小修法**：给出 cap（例如 `min(defense, 20)`）或在 tooltip 写明「随目标护甲线性增长」。需数值裁决。

### P2-3 紫晶骰子的 `rollD6(roller)` **完全忽略 `roller`**

`starengine/common/src/main/java/com/merlinkitsune/starengine/event/AmethystDiceHandler.java:25-27` ⇒ 法伤骰恒均匀 1..6，**不吃**诡异/绯红骰子的偏置；而 `DiceCombatEvents.rollCombatDie`（`NEO :1562-1569`）吃偏置。
**最小修法**：若设计就是「法伤骰恒均匀」，补一行注释；若要联动，改为走 `rollCombatDie` 同口径（注意会改变期望值）。

### P2-4 电击手套 AOE 的每个目标各吃一遍完整修饰器

`ElectricGloveChipItem.java:159-184`（`:170-171` 逐目标 `applySpellDamageAmount`）⇒ N 个目标 × (标记 + 防御 + 各自重掷 d6)。
**最小修法**：若合意，在 `:154-155` 注释补一句「每个 AOE 目标各自进入法伤链（含各自 d6）」；若不合意，在 `SpellDamageContext` 加 `aoe` 标志并在 `isActive` 里跳过 5/6/9 号。

### P2-5 N4/N5 落盘带来的副作用（`EventEffects.hurtAsEventCardPenalty`）

惩罚伤害改走 `unreducible_damage` + `causing = triggerer` ⇒ `isHostileAttack`（`DiceCombatEvents.java:1759-1764`）在 PvP 且非同队时**认作敌对攻击** ⇒ 事件牌惩罚现在会触发：立牌受击被动、BufferShield、Papara 回血、赐福/降神倒计时。
**最小修法**：在 `isHostileAttack` 里排除「模组自身的惩罚类型」（`UNREDUCIBLE_DAMAGE` + `CARD_COST`），或在 `EventEffects` 惩罚期间置一个 ThreadLocal 闸门（与 `APPLYING_TRUE_BONUS` 同形）。
⚠️ 注意 `UNREDUCIBLE_DAMAGE` 也被**狂暴反噬**（`:1485`）使用 —— 排除它对反噬也是**正确**的（反噬本就不应触发受击被动）。

### P3 类

| # | 项 | 建议 |
|---|---|---|
| **P3-1** | 四个停用修饰器是死代码；若将来有人把 `isActive` 恢复而 Fx 路径未删 ⇒ **双倍加成** | 删除 4 段，或以注释 + 单测断言锁死 |
| **P3-2** | 盾不挡 `card_spell/true_damage` 靠 `direct=null`（`getSourcePosition()==null`），而非标签 | 在 `ModDamageTypes.cardSpell/trueDamage` 的 javadoc 写死「禁止改用带 `Vec3` / 带 direct 的构造器」 |
| **P3-3** | 惩罚伤害被归入 `Group.SPELL`（`hurtAsEventCardPenalty` 内 `DamageNumberAggregator.tag(...)`）⇒ 数字显示为法伤绿，语义上是「事件惩罚」 | 加独立 Group 或复用 `CARD_COST` 的配色 |

---

## 9. 实机判据草案（可直接落 `scripts/test/cases/*.json`）

> 本批只读，**未新增任何探针**。下列判据全部依赖**尚不存在**的探针命令，需由落地批次实现后再启用；报文格式沿用现有约定 `AP_<tag>_<NAME>:k=v:k=v:`（`console.info` 落 `latest.log`）。用例结构沿用 `scripts/test/cases` 既有 schema（`case_id` / `steps` / `asserts` / `evidence`，断言 `type`:`log`|`absent`、`source`:`latest`、`pattern`）。

### 9.1 需要的探针（草案，全部为新增）

| 探针 | 作用 | 最小实现位置 |
|---|---|---|
| `/astralprobe spell_on` | 记录下一次法伤聚合结果 | `DamageEffectCardHandler` 聚合循环后打 `AP_SPELL_AGG:active=<类名逗号表>:bonus=<double>:type=<msgId>:direct=<null或类名>` |
| `/astralprobe dmg_on` | 记录下一次伤害事件的入站值 | 在 `onLivingDamagePre` 打 `AP_DMG_IN:type=<msgId>:amount=<float>:absorption=<float>:health=<float>` |
| `/astralprobe mark_set <n>` | 给最近敌对目标设 n 层标记 | 调 `MarkManager.apply` n 次 |
| `/astralprobe curio <chip>` | 给测试玩家装指定芯片 | 现有创造栏/指令若不够则新增 |
| `/astralprobe def_on` | 打印目标防御力分解 | `PiercingGunChipItem.getTargetDefense` 处打 `AP_DEF:armor=<float>:toughness=<float>:defense=<int>` |
| `/astralprobe heal_on` | 记录 Papara 回血 | `PaparaSignItem` `:87-88` 打 `AP_HEAL:base=<float>:heal=<int>` |

### 9.2 用例草案

**C1 `SPELL-BASE`（无芯片基线）**

    { "case_id": "SPELL-BASE-1.21.1", "version": "1.21.1",
      "steps": [
        {"op":"inject_command","value":"/astralprobe spell_on"},
        {"op":"inject_command","value":"/astralprobe dmg_on"},
        {"op":"inject_command","value":"/summon minecraft:zombie ~ ~ ~2 {NoAI:1b,Tags:[\"ap_t\"]}"},
        {"op":"inject_command","value":"/astralprobe cast card_spell 4"}]}

断言：`AP_SPELL_AGG:...bonus=0.0:...type=astral_dice:card_spell`（无芯片 ⇒ 5/6/9 号全不激活；7/8 号 `apply` 只返回 0）。

**C2 `SPELL-NINJA`（忍术飞镖 = 目标标记层数 + 32 层上限）**
步骤：`curio ninja_star` → `mark_set 3` → cast。断言 `bonus=3.0`；再 `mark_set 40`（触发 `MAX_MARKER=32` 上限）⇒ 断言 `bonus=32.0`（**层数上限判据**）。

**C3 `SPELL-PIERCING`（贯穿之铳 = 目标防御力）**
步骤：给目标穿全套钻石甲（`/item replace entity @e[tag=ap_t,limit=1] armor.head minecraft:diamond_helmet` ×4）→ `curio piercing_gun` → cast。
断言：`AP_DEF` 与 `AP_SPELL_AGG:bonus` **交叉验证**（`bonus == 0 + defense`，且 `defense == floor(2 + armor*0.30 + 0.85*toughness)`）。
**禁止在用例里硬编码期望值** —— 公式一改判据就假绿；两条探针读数必须自洽。

**C4 `SPELL-AMETHYST`（紫晶骰子 = d6）**
断言：连打 12 次，全部 `1 ≤ bonus ≤ 6`，且**出现 ≥ 3 个不同值**（只断言区间会被「恒返回 3」骗过）。

**C5 `SPELL-QUIVER`（魔法箭袋 30s 冷却）**
断言：第 1 次出现 `AP_QUIVER_PROC`；600 tick 内第 2 次**不出现**；`wait 620` 后再打重新出现（`type`:`log` 计行数 + `absent` 反向断言）。

**C6 `SPELL-IFRAME`（加成段穿无敌帧）—— 最关键**
步骤：先打一发 `player_attack` 让 `invulnerableTime=20`，**同一 tick** 再 cast `card_spell`（佩紫晶骰子）。
断言：`AP_DMG_IN:type=astral_dice:true_damage:.*amount=>0` 行**必须存在**（证明 bonus 段结算），同时本体 `card_spell` 允许被差额结算削减。

**C7 `SPELL-WEAKMARK`（弱标记 ×1.10 两段都放大）**
步骤：目标施 `WEAK_MARK` → cast。断言**两行**：本体终值 = `round(原始 × 1.10)`、加成段 `true_damage` = `round(bonus × 1.10)`（只断言一行会漏掉 §7.1 的翻倍问题）。

**C8 `SPELL-PAPARA`（PvP 两段各回一次血）—— P1-2 的验收器**
断言：`AP_HEAL` 出现 **2 次**，两次 `base` 分别 = 本体终值 / 加成值。
**现状基线**：2 次；若采纳 P1-2 闸门 ⇒ 用例改为断言 **1 次**。这条用例本身就是修复的验收器。

### 9.3 判据纪律（避免恒绿）

1. **禁止硬编码期望数值**（C3 公式、C7 的 1.10 都在漂移风险里）—— 让探针输出中间量，用例比对两条读数。
2. **每次注入前清空 live 日志**（否则早停被判据骗过）。
3. `AP_` 行必须带 `tag`/`case` 字段以区分同批多目标（法伤链一次命中会打 ≥3 行）。
4. 区间类断言（C4）必须配「出现 ≥N 个不同值」。

---

## 10. 未做 / 存疑（取不到证据即写「无法判定」）

| # | 项 | 状态 |
|---|---|---|
| 1 | **编译验证**（`BUILD SUCCESSFUL` + EXIT 码） | **未做** —— 硬约束禁止跑 `gradlew`（并发抢占）。须由落地批次补 `:neoforge-1.21.1:classes :forge-1.20.1:classes` |
| 2 | 实机读数（§9 全部判据） | **未做** —— 禁止启动游戏；§9 全为**草案**，探针尚不存在 |
| 3 | 立牌「被追踪 +5」在加成段是否也 +5 | **无法判定** —— 未读该立牌实现（超出本批范围），复核命令见 §7.2 |
| 4 | `MAGIC_DAMAGE_TYPES` 表内第三方 id 是否与实装 jar 一致 | **无法判定** —— 需逐 jar 反查（超出本批范围） |
| 5 | `CombatFormula.mobDefenseInt` 的返回值范围（是否可能为负/0） | **无法判定** —— 未读库实现，只读了 `PiercingGunChipItem` 的 doc 注释 |
| 6 | `LinglingSignEvents` 使用 `isApplyingTrueBonus()` 的具体效果 | 未复核（本批范围外）；仅确认它是**唯一**外部消费者 |
| 7 | `RenShieldManager.tick` 的黄心清空时机（影响 P1-2 第 6 条的取舍） | 部分读到（`:95-115` / `:380-425`），未读 `tick` 全文 |
| 8 | N1/N2/N3/N7/N8 的**实际**数值影响 | **推演**（未落盘，只能按审计正本 §3 修法文本推演，见 §7 表） |
| 9 | 两线原版 `CombatRules` 本体差异 | 未核 `CombatRules` 本体；只核了 `LivingEntity` 的调用点 |
| 10 | 两线整体编译期一致性 | **无法判定**（未编译） |

---

## 附：本文件自检

    python - <<'PY'
    p = r"docs/features/spell-damage-chain-verification.md"
    b = open(p, "rb").read()
    bare = b.count(b"\n") - b.count(b"\r\n")
    print("bytes", len(b), "CRLF lines", b.count(b"\r\n"), "bareLF", bare)
    PY
    # 期望 bareLF 0

---

## 11. 时效声明：复核期间落盘状态已变化（更正 §0.2 与 §7）

> 本节记录的是**审查过程中观察到的工作树变化**（另一并行批次正在落盘），**不是本审查员所做的改动**；本审查员全程只读源码。
> 本节只做「状态与影响方向的更正」，**未对新增代码做完整审计**（超出本批范围）——落地后须由批次 A 自证 + 独立复核。

### 11.1 观测到的增量（收尾时刻 `git status`）

```
 M .../combat/DiceCombatEvents.java      （两线：176 / 11 行）
 M .../damage/ModDamageTypes.java        （两线：各 +2 行注释，N6 订正）
 M .../event/ChipDamageHandler.java      （forge 线：12 行，N3 口径订正）
 M .../event/AstralEventDispatcher.java  （N4/N5）
 M .../event/EventEffects.java           （N4/N5）
 M .../item/sign/PaparaSignItem.java     （N9）
 M .../data/minecraft/tags/damage_type/bypasses_cooldown.json （N6）
```

```bash
grep -n 'settleDiceDamage\|remainingShieldAbsorption' forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/combat/DiceCombatEvents.java
# 280: 注释：将来任何新的写终值路径也必须走它
# 306: 注释：…(LivingDamageEvent)再读 remainingShieldAbsorption 时…
# 337: RenShieldManager.remainingShieldAbsorption(defender)
# 350: RenShieldManager.remainingShieldAbsorption(defender)
# 365: private static void settleDiceDamage(LivingEntity target, LivingDamageEvent event, float finalDamage)
# 940-941: N1+N2 注释 + settleDiceDamage(target, event, (float) finalDmg)
# 1224:    settleDiceDamage(target, event, finalDmg)
```

### 11.2 对法伤链的**更正结论**（关键）

| 条目 | §7 原判断 | 更正后（按收尾时刻工作树） | 对法伤链是否仍成立 |
|---|---|---|---|
| **N1** | 未落盘 | **已落盘，但换了解法**：没有迁 `RenShieldManager` 的事件锚点（该类仍挂 `LivingHurtEvent`，`git status` 里无它），而是在 `DiceCombatEvents` @NORMAL 处用 `remainingShieldAbsorption` 钳制终值（forge `:337` / `:350`） | **D3 仍然成立** —— 钳制位于**骰战路径**；法伤 `direct=null` 在该类 `:331` 早退（§2.3 已证），**不经过该钳制** ⇒ forge 上的法伤仍不吃黄心钳制 |
| **N2** | 未落盘 | **已落盘**：`settleDiceDamage`（forge `:365`）成为两条写终值路径的唯一出口（`:941` / `:1224`），写入 `F' - min(F', A0)` | **P1-1 仍然成立** —— 出口只在骰战路径；法伤的本体段 + 加成段仍各走一次原版 `:1684` 双扣 ⇒ 补偿式修法（§8 P1-1）依然需要 |
| **N3** | 未落盘 | **已落盘（口径订正）**：`ChipDamageHandler` 新增注释「N2 之后骰战两条写终值路径也归到同一口径…本处读到的就是红心量」 | 对法伤链：该 handler 对两段各判一次（§7.1）不变；**致命判定基准是否对法伤也正确，需落地批次复核**（本节不判） |
| **N7** | 未落盘 | **已落盘**：`BERSERK_BACKLASH_APPLYING` 闸门保留，并**新增** `|| DamageEffectCardHandler.isApplyingTrueBonus()`（NEO `:1486` / FORGE `:1596`） | **P1-2 清单第 3 条已完成** ⇒ 法伤加成段**不再**触发狂暴反噬（本审查员的建议被采纳，方向一致） |
| **N8** | 未落盘 | **已落盘（仅骰战口径）**：抽出 `WEAK_MARK_DAMAGE_MULTIPLIER = 1.10f`，×1.10 并入骰战终值（吸收前），`onWeakMarkDamage` 对骰战路径改为跳过 | **D2 仍然成立** —— 法伤**不是**骰战路径 ⇒ 仍走 `onWeakMarkDamage` @LOW（forge 吸收后）。即 §7 的「N8 覆盖不足」判断在**落盘后依旧是真问题（对象从骰战换成法伤）** |

### 11.3 P1-2 清单的最新进度

| # | 位置 | 状态 |
|---|---|---|
| 1 | `PaparaSignItem.onPaparaBiteHurtHeal` | **未加** ⇒ 法伤两段各回一次血（§9 C8 现状基线仍为 2 次） |
| 2 | `DiceCombatEvents.onWeakMarkDamage` | **未加** ⇒ 加成段仍被再 ×1.10 |
| 3 | `DiceCombatEvents.onBerserkDamageTaken` | **已加**（NEO `:1486` / FORGE `:1596`） |
| 4 | `ChipDamageHandler.onLivingDamagePre` | **未加** ⇒ 保命对两段各判一次 |
| 5 | `DiceCombatEvents`「立牌受击钩子 / BufferShield」块 | **未加** ⇒ 受击被动触发两次 |
| 6 | `RenShieldManager.onRenShieldAbsorb` | 建议**不加**（理由见 §8 P1-2） |

```bash
grep -rn 'isApplyingTrueBonus' neoforge-1.21.1/src/main/java forge-1.20.1/src/main/java
# 当前命中：DamageEffectCardHandler 定义处 + LinglingSignEvents + DiceCombatEvents(狂暴反噬)
```

### 11.4 仍为「无法判定」

- 新增 `settleDiceDamage` 的实现是否在所有分支上都真满足「吸收只扣一次」（未审计）。
- `ChipDamageHandler` 12 行改动的实际语义是否对**法伤**路径也正确（该 handler 对两段都生效）。
- 收尾时刻工作树仍在变动 ⇒ 本节的任何行号都只对应**这一次观测**。
