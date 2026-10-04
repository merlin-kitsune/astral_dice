# 伤害链路改动地图（A1 / A3 / A6 / 虚空·kill / A4 / A10）

> 只读侦察产物。所有行号与代码原文均来自本次实际读取（`read` / `grep` / 反编译 jar 直接读 entry）。
> 版本标记：**A = `neoforge-1.21.1`（MC 1.21.1 / NeoForge 21.1.235 / Java 21）**，**B = `forge-1.20.1`（MC 1.20.1 / Forge 47.4.10 / Java 17）**。
> 源码根（下称 `<v>`）：`<v>/src/main/java/com/merlinkitsune/astral_dice/`；资源根：`<v>/src/main/resources/`。
> 反编译证据来源：
> - A：`neoforge-1.21.1/build/moddev/artifacts/neoforge-21.1.235-sources.jar`（源码）、`neoforge-21.1.235-client-extra-aka-minecraft-resources.jar`（原版数据包资源）
> - B：`forge-1.20.1/build/moddev/artifacts/forge-1.20.1-47.4.10-sources.jar`、`client-extra-1.20.1-47.4.10.jar`

---

## A1 — 模组自发的嵌套/额外伤害结算改为穿透无敌帧

### ① 结论（一句话）

所有嵌套 `hurt` 只有两个伤害类型来源——`astral_dice:true_damage`（7 处中的 5 处）与 `astral_dice:dice_damage`（2 处），二者都是 **JSON 数据驱动的伤害类型**（非 Java 注册）；两个子项目各自只有 `data/minecraft/tags/damage_type/bypasses_armor.json` 一个伤害类型标签文件，**都不存在 `bypasses_cooldown.json`**；而 `DamageTypeTags.BYPASSES_COOLDOWN` 这个 **Java 常量在 1.20.1 与 1.21.1 都已存在**（源码 `DamageTypeTags.java:12`），且 `LivingEntity#hurt` 在两版本都用它判无敌帧。所以**最小 diff = 在两个子项目各新增一个 `data/minecraft/tags/damage_type/bypasses_cooldown.json`**（标签方案），无需改代码。

### ② 触点清单

**全部「模组自己发起」的 `hurt` 调用点**（`grep` 全量结果，已排除注释）：

| 位置 | 伤害源 | 伤害类型 |
|---|---|---|
| A `event/DamageEffectCardHandler.java:65-66` | `ModDamageTypes.trueDamage(target.level(), player)` | `astral_dice:true_damage` |
| B `event/DamageEffectCardHandler.java:62-63` | 同上 | `astral_dice:true_damage` |
| A `combat/DiceCombatEvents.java:652`（大当家溅射） | `ModDamageTypes.trueDamage(target.level(), player)`（640-641） | `astral_dice:true_damage` |
| B `combat/DiceCombatEvents.java:649`（大当家溅射） | 同上（637-638） | `astral_dice:true_damage` |
| A `combat/DiceCombatEvents.java:1143`（`injectCounterDamage`） | `ModDamageTypes.diceDamage(attacker.level(), player)` | `astral_dice:dice_damage` |
| B `combat/DiceCombatEvents.java:1153`（同上） | 同上 | `astral_dice:dice_damage` |
| A `combat/SpellDamageRegistry.java:259`（定向爆破 AOE） | `trueDamage(ctx.target.level(), ctx.attacker)`（251-252） | `astral_dice:true_damage` |
| B `combat/SpellDamageRegistry.java:259` | 同上（251-252） | `astral_dice:true_damage` |
| A `combat/SpellDamageRegistry.java:377`（电击手套 AOE） | `trueDamage(ctx.target.level(), ctx.attacker)`（371-372） | `astral_dice:true_damage` |
| B `combat/SpellDamageRegistry.java:377` | 同上（371-372） | `astral_dice:true_damage` |
| A `mixin/EntityThunderHitMixin.java:61`（电磁炮雷击） | `ModDamageTypes.trueDamage(level)` | `astral_dice:true_damage` |
| B `mixin/EntityThunderHitMixin.java:61` | 同上 | `astral_dice:true_damage` |
| A `event/CrimsonDiceHandler.java:64`（绯红骰自伤） | `ModDamageTypes.diceDamage(roller.level(), roller)` | `astral_dice:dice_damage` |
| B `event/CrimsonDiceHandler.java:64` | 同上 | `astral_dice:dice_damage` |
| A `item/card/EffectCardItem.java:29`（王之力自伤 8 点） | `ModDamageTypes.diceDamage(level, user)` | `astral_dice:dice_damage` |
| B `item/card/EffectCardItem.java:29` | 同上 | `astral_dice:dice_damage` |

**伤害类型注册方式（Java 还是 JSON）——结论：JSON（数据驱动）**，两版本完全同构：

A `damage/ModDamageTypes.java:14-29`（B 同文件 14-29，仅 `ResourceLocation.fromNamespaceAndPath(...)` ↔ `new ResourceLocation(...)` 差异）：

```java
public class ModDamageTypes {
    public static final ResourceKey<DamageType> DICE_DAMAGE = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "dice_damage")
    );
    ...
    public static final ResourceKey<DamageType> TRUE_DAMAGE = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "true_damage")
    );
```

对应定义文件（**A 与 B 逐字节相同**）：

- `<v>/src/main/resources/data/astral_dice/damage_type/dice_damage.json`
```json
{
  "message_id": "dice_damage",
  "scaling": "when_caused_by_living_non_player",
  "exhaustion": 0.1
}
```
- `<v>/src/main/resources/data/astral_dice/damage_type/true_damage.json`
```json
{
  "message_id": "true_damage",
  "scaling": "when_caused_by_living_non_player",
  "exhaustion": 0.0
}
```

**两子项目现有 `data/*/tags/damage_type/*.json` 清单与完整内容——各只有 1 个文件**（A 与 B 内容逐字节相同）：

- `bypasses_armor.json` 清单：
  - A：`neoforge-1.21.1/src/main/resources/data/minecraft/tags/damage_type/bypasses_armor.json`
  - B：`forge-1.20.1/src/main/resources/data/minecraft/tags/damage_type/bypasses_armor.json`
- `bypasses_armor.json` 完整内容：
```json
{
  "values": [
    "astral_dice:true_damage"
  ]
}
```
- 两个子项目都**没有** `bypasses_cooldown.json`（`build/` 下也搜不到任何 `bypasses_cooldown.json` 产物）。

**标签是「追加」还是「覆盖」的证据**（决定新文件能否只写自己的条目）：`TagLoader` 仅在 `replace: true` 时清空已有条目——A `net/minecraft/tags/TagLoader.java:53-56` / B 同文件 `:53-56`：

```java
TagFile tagfile = TagFile.CODEC.parse(new Dynamic<>(JsonOps.INSTANCE, jsonelement)).getOrThrow();
if (tagfile.replace()) {
    list.clear();
}
```
现有 `bypasses_armor.json` 未写 `replace`，故与原版 18 条并存（这也解释了 `true_damage` 能同时「无视护甲」而原版条目不受影响）。

### ③ `minecraft:bypasses_cooldown` 是否存在——本地 jar 证据

**结论：Java 常量两版本都有，但原版数据包 JSON 两版本都没有（即标签当前为空）。**

- **Java 常量：两版本都有**（`net/minecraft/tags/DamageTypeTags.java` 第 12 行）：
  - A `DamageTypeTags.java:12`：`TagKey<DamageType> BYPASSES_COOLDOWN = create("bypasses_cooldown");`
  - B `DamageTypeTags.java:12`：`TagKey<DamageType> BYPASSES_COOLDOWN = create("bypasses_cooldown");`
- **原版数据包 JSON：两版本都没有**（用 `System.IO.Compression.ZipFile` 列 entry，未解压）：
  - A `neoforge-21.1.235-client-extra-aka-minecraft-resources.jar` 的 `data/minecraft/tags/damage_type/` 共 34 个 entry，**无** `bypasses_cooldown.json`（有 `bypasses_armor/effects/enchantments/invulnerability/resistance/shield/wolf_armor` 等）。
  - B `client-extra-1.20.1-47.4.10.jar` 同目录共 28 个 entry，**同样无** `bypasses_cooldown.json`。
  - 即：该标签在两个版本里都是**空标签**，判定恒为 false。

**`hurt` 中的实际使用点（两版本都有）**：

- A `net/minecraft/world/entity/LivingEntity.java:1190`
```java
if ((float)this.invulnerableTime > 10.0F && !source.is(DamageTypeTags.BYPASSES_COOLDOWN)) {
```
- B `net/minecraft/world/entity/LivingEntity.java:1131`
```java
if ((float)this.invulnerableTime > 10.0F && !source.is(DamageTypeTags.BYPASSES_COOLDOWN)) {
```

### ④ 最小 diff 方案（推荐：标签）

新增两个文件（不改任何 Java）：

- A：`neoforge-1.21.1/src/main/resources/data/minecraft/tags/damage_type/bypasses_cooldown.json`
- B：`forge-1.20.1/src/main/resources/data/minecraft/tags/damage_type/bypasses_cooldown.json`

内容（两版本逐字节相同）：

```json
{
  "values": [
    "astral_dice:true_damage",
    "astral_dice:dice_damage"
  ]
}
```

可选配套清理（非必须，但语义上会被标签取代）：A `DiceCombatEvents.java:648-656` / B `:645-653` 的「主目标临时清零无敌帧」变通可以删除：

```java
                        boolean isMainTarget = victim == target;
                        int savedInvulnerable = isMainTarget ? victim.invulnerableTime : 0;
                        try {
                            if (isMainTarget) victim.invulnerableTime = 0;
                            victim.hurt(splashSource, splashDmg);
                            sendDamageNumber(victim, (int) splashDmg);
                        } finally {
                            if (isMainTarget) victim.invulnerableTime = savedInvulnerable;
                        }
```

**代码方案（不推荐，仅作对照）**：本仓**已有**「手动清零 `invulnerableTime`」先例，行号如下（这是全仓唯一的先例，A/B 对称）：
- A `combat/DiceCombatEvents.java:649`（声明）、`:651`（清零）、`:655`（还原）
- B `combat/DiceCombatEvents.java:646`、`:648`、`:652`

### ⑤ 不确定 / 需人工确认

1. **`dice_damage` 是否也要穿透无敌帧，需你裁决**。`dice_damage` 的调用点包含**自伤**：`CrimsonDiceHandler.java:64`（绯红骰掷出 1 → 自伤 6 点）与 `EffectCardItem.java:29`（王之力自伤 8 点）。今日自伤可能被无敌帧吞掉，加标签后会**每次都吃到**——这是行为变更（自伤更容易生效）。
2. 加标签后，嵌套 `hurt` 会走 `else` 分支（`LivingEntity.java` A:1200-1203 / B:1139-1142），即 **`lastHurt` 被写成嵌套伤害值、`invulnerableTime` 被重置为 20**。对「主目标正在自己那次攻击内部」的情形，`invulnerableTime` 本来就是 20，等价；但对**其它 AOE 受害者**，会让其进入 20 tick 无敌帧（与「被正常打一下」相同，可接受）。若删除 ③ 的旧变通，请勿同时保留两套机制（重复也会正常工作，但语义重复）。
3. `bypasses_cooldown` 是**全局伤害类型标签**：只要伤害类型是 `true_damage`，**任何来源**（含其它模组通过 tag 复用）都会穿透无敌帧；无法只对「模组自己发起的那一次」生效。若未来需要更细粒度，只能回到代码清零方案。
4. 未确认项：A1 需求里提到的「`bypasses_cooldown.json` 在本地 jar 中」——**实测两个版本都不存在该文件**（见 ③），请以本条为准。

---

## A3 — 末影骰子「雨中/水下受伤 +40%」迁入骰战修饰器/因子体系

### ① 结论（一句话）

**骰战因子体系不适合承载「受伤方减益」，直接迁移会造成「平时（非赐福）雨中 +40% 失效」的回归**：`DiceCombatFactor` 消费链位于 `DiceCombatEvents.onLivingDamagePre` 内，只有在「攻击者是玩家 + 近战武器 + 佩戴骰子 + 目标是赐福目标 + 攻击者已有 `DICE_BLESSING`」时才可达；而雨中/水下 +40% 是**任何伤害源**（摔落/溺水/怪物近战/弹射物/虚空…）都要生效的**受击侧**乘算，两者作用域不同。建议最小方案：**保留在受击侧，但把它从 `EnderDiceHandler` 内联改为注册到「受击侧修饰器」列表**（若确实要「注册化」），且必须继续覆盖非赐福与非玩家伤害源。

### ② 触点清单

**`DiceCombatModifiers.java` 里是否存在因子（Factor）注册 API——结论：不存在。**

- A `combat/DiceCombatModifiers.java:58-80` 只有两套列表与 API：
  - `:58` `private static final List<AttackPowerModifier> ATTACK_MODIFIERS = new ArrayList<>();`
  - `:59` `private static final List<DefensePowerModifier> DEFENSE_MODIFIERS = new ArrayList<>();`
  - `:65` `public static void registerAttackModifier(AttackPowerModifier modifier)`
  - `:70` `public static void registerDefenseModifier(DefensePowerModifier modifier)`
  - `:74` `public static List<AttackPowerModifier> attackModifiers()` / `:78` `defenseModifiers()`
- B `combat/DiceCombatModifiers.java`：同结构（行号 58/59/65/70/74/78 一致）。
- 全仓 `grep DiceCombatFactor|registerFactor|factors`：**`DiceCombatModifiers.java` 0 命中**；`DiceCombatFactor` 只出现在 `DiceCombatEvents.java`（A 8 处 / B 8 处）。

**因子（Factor）API 的真实位置**：

- A `combat/DiceCombatEvents.java:910-920`
```java
    @FunctionalInterface
    public interface DiceCombatFactor {
        double modify(Player attacker, LivingEntity target, double damage);
    }

    private static final List<DiceCombatFactor> EXTERNAL_DAMAGE_FACTORS = new ArrayList<>();

    // 注册骰战外部伤害影响因子(供附属内容/联动扩展)
    public static void registerDiceCombatFactor(DiceCombatFactor factor) {
        EXTERNAL_DAMAGE_FACTORS.add(factor);
    }
```
- B `combat/DiceCombatEvents.java:904-914`：逐字相同（仅行号 -6）。

**最终伤害处如何消费这些因子**：

- A `combat/DiceCombatEvents.java:598-602`
```java
        // 接管外部伤害影响:按原始设计应用最终伤害数值(各因子相乘)。
        // 附属内容可通过 registerDiceCombatFactor 注册自定义因子,影响骰战最终伤害。
        for (DiceCombatFactor factor : EXTERNAL_DAMAGE_FACTORS) {
            finalDmg = factor.modify(player, target, finalDmg);
        }
```
- B `combat/DiceCombatEvents.java:595-599`：逐字相同（`event.setAmount` 在 B:601 / A:604）。

**该消费路径是否只在「被赐福/掷骰战」时才走到——结论：是，而且有四道闸门，非赐福攻击者必然跳过。**

`onLivingDamagePre` 的四道早退（A 行号 / B 行号）：

| 闸门 | A | B | 原文 |
|---|---|---|---|
| AOE / 反击链截断 | `:200` | `:197` | `if (aoeProcessing \|\| counterDepth > 0) return;` |
| 直接伤害实体必须是玩家 | `:201` | `:198` | `if (!(directEntity instanceof Player player)) return;` |
| 必须近战武器 | `:212` | `:209` | `if (!isMeleeWeaponAttack(player)) return;` |
| **必须已有骰神赐福** | `:354` | `:351` | `if (!player.hasEffect(ModEffects.DICE_BLESSING)) return;` |

A `:353-362`：
```java
        // Dice combat mechanics require the Dice Blessing effect
        if (!player.hasEffect(ModEffects.DICE_BLESSING)) return;
        if (diceStack == null) return;
        if (enhancement == null) {
            enhancement = WeaponEnhancement.EMPTY;
            ...
```
B `:350-359`：同上（`ModEffects.DICE_BLESSING.get()`）。

**→ 结论：非赐福攻击者（以及所有非玩家伤害源：摔落、溺水、虚空、怪物近战、弹射物）根本到不了 `:600` 的因子循环。把 ×1.4 迁进因子链 = 这些情形全部丢失 +40%。**

### ③ `event/EnderDiceHandler.java` 雨中/水下 ×1.4 的两版本完整代码块

**A（neoforge-1.21.1）`event/EnderDiceHandler.java:133-143`**（原文）：

```java
    // 装备期间处于雨中或水下:受到的伤害 +40%(在气囊判定之前完成放大,见 ChipDamageHandler 优先级说明)
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        if (!(entity instanceof Player player)) return;
        if (!hasEnderDie(player)) return;
        // 雨(含被雨淋到)/水下/气泡柱都满足"雨中或水下"
        if (!player.isInWaterRainOrBubble()) return;
        event.setNewDamage(event.getNewDamage() * RAIN_WATER_DAMAGE_MULTIPLIER);
    }
```

**B（forge-1.20.1）`event/EnderDiceHandler.java:134-144`**（原文）：

```java
    // 装备期间处于雨中或水下:受到的伤害 +40%(在气囊判定之前完成放大,见 ChipDamageHandler 优先级说明)
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        if (!(entity instanceof Player player)) return;
        if (!hasEnderDie(player)) return;
        // 雨(含被雨淋到)/水下/气泡柱都满足"雨中或水下"
        if (!player.isInWaterRainOrBubble()) return;
        event.setAmount(event.getAmount() * RAIN_WATER_DAMAGE_MULTIPLIER);
    }
```

倍率常量（两版本同名同值）：
- A `event/EnderDiceHandler.java:47-48`：`/** 雨中/水下受到伤害倍率(+40%) */` + `public static final float RAIN_WATER_DAMAGE_MULTIPLIER = 1.4F;`
- B `event/EnderDiceHandler.java:48-49`：同上。

### ④ 最小 diff 方案

**方案 1（推荐，最小且无回归）**：把这段逻辑「注册化」，但注册到**受击侧修饰器列表**，由**同一个 HIGH 优先级监听器**消费（作用域、顺序、覆盖范围全部不变）：

- 在 `combat/DiceCombatModifiers.java` 增加一套受击侧 API（与既有 `registerAttackModifier` 同风格）：
```java
    private static final List<IncomingDamageModifier> INCOMING_DAMAGE_MODIFIERS = new ArrayList<>();

    @FunctionalInterface
    public interface IncomingDamageModifier {
        float modify(Player victim, net.minecraft.world.damagesource.DamageSource source, float damage);
    }

    public static void registerIncomingDamageModifier(IncomingDamageModifier modifier) {
        INCOMING_DAMAGE_MODIFIERS.add(modifier);
    }

    public static List<IncomingDamageModifier> incomingDamageModifiers() {
        return List.copyOf(INCOMING_DAMAGE_MODIFIERS);
    }
```
- `EnderDiceHandler` 的 HIGH 监听器改为遍历该列表（A 用 `event.setNewDamage`，B 用 `event.setAmount`），末影骰子自身因子（雨中/水下 ×1.4）改为 `registerIncomingDamageModifier` 注册项。

**方案 2（如果坚持「骰战修饰器/因子体系」）**：需要同时保留受击侧的非骰战路径，即**双写**（因子链覆盖赐福近战；受击侧只覆盖「非骰战」情形），并在受击侧显式排除「已由因子链处理」的情形。这会把一处逻辑变成两处、并引入「两处是否重复应用」的判定，**不推荐**。

### ⑤ 不确定 / 需人工确认

1. **需求原话「改为注册到骰战修饰器/因子体系」与现有体系语义冲突**（因子只作用于骰战最终伤害，且签名是 `(Player attacker, LivingEntity target, double damage)`，第一参数是**攻击者**，没有「受伤方减益」的位置）。请确认采用方案 1（受击侧注册表）还是接受回归。
2. B 侧监听的是 `LivingHurtEvent`（**护甲减免之前**，见 A6 节的证据），A 侧是 `LivingDamageEvent.Pre`（护甲/附魔/抗性减免**之后**）。因此**「雨中 +40%」在两个版本的实际数值本就不同**（B 会先放大再吃护甲减免，等效收益更低）。这是既有的单侧差异，本次改动是否顺带对齐需你裁决。
3. 顺序依赖：`ChipDamageHandler`（安全气囊）用 `LOWEST`，注释明确要求放大先于气囊——改注册表后**必须保持 HIGH 优先级**，否则出现「雨中被打死但气囊没触发」。

---

## A6 — 安全气囊「致命伤害」统一按吸收（黄心）之后判定

### ① 结论（一句话）

**B（1.20.1）已经满足**：它监听 `LivingDamageEvent`，该事件在 `actuallyHurt` 中被 `ForgeHooks.onLivingDamage` 于**吸收扣减之后**派发（`LivingEntity.java:1680`，吸收在 `:1669-1670`），`event.getAmount()` 已是「将要从生命里扣掉的量」；**A（1.21.1）不满足**：`LivingDamageEvent.Pre.getNewDamage()` 在吸收扣减**之前**取值（`LivingEntity.java:1789`，吸收在 `:1790-1792`），需要手写 `- player.getAbsorptionAmount()`。

### ② 触点清单与两版本全文

**A（neoforge-1.21.1）`event/ChipDamageHandler.java:39-66`（全文，含注解与方法签名）**：

```java
@EventBusSubscriber(modid = AstralDiceMod.MODID)
public final class ChipDamageHandler {

    private ChipDamageHandler() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        if (!(entity instanceof Player player)) return;

        float damage = event.getNewDamage();
        if (damage <= 0.0F) return;

        // 安全气囊:致命伤害(伤害 ≥ 当前血量)时消耗 6 点充能使本次伤害无效。
        // 不排除「无视无敌」(bypasses_invulnerability)的伤害源:气囊必须始终有效(见类注释)。
        if (damage >= player.getHealth() && AirbagChipItem.tryNegateFatal(player)) {
            event.setNewDamage(0.0F);
            return;
        }

        // 磨刀石:低血量减伤 + 血量 > 1 时不可被一次伤害击倒
        float modified = WhetstoneChipItem.modifyIncomingDamage(player, damage);
        if (modified != damage) {
            event.setNewDamage(modified);
        }
    }
```

**B（forge-1.20.1）`event/ChipDamageHandler.java:39-66`（全文）**：

```java
@Mod.EventBusSubscriber(modid = AstralDiceMod.MODID)
public final class ChipDamageHandler {

    private ChipDamageHandler() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(LivingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        if (!(entity instanceof Player player)) return;

        float damage = event.getAmount();
        if (damage <= 0.0F) return;

        // 安全气囊:致命伤害(伤害 ≥ 当前血量)时消耗 6 点充能使本次伤害无效。
        // 不排除「无视无敌」(bypasses_invulnerability)的伤害源:气囊必须始终有效(见类注释)。
        if (damage >= player.getHealth() && AirbagChipItem.tryNegateFatal(player)) {
            event.setAmount(0.0F);
            return;
        }

        // 磨刀石:低血量减伤 + 血量 > 1 时不可被一次伤害击倒
        float modified = WhetstoneChipItem.modifyIncomingDamage(player, damage);
        if (modified != damage) {
            event.setAmount(modified);
        }
    }
```

**挂载的事件与优先级**（两版本相同）：

- A `:45` `@SubscribeEvent(priority = EventPriority.LOWEST)` → `LivingDamageEvent.Pre`
- B `:45` `@SubscribeEvent(priority = EventPriority.LOWEST)` → `LivingDamageEvent`
- 死亡侧兜底：A `:75-85` `@SubscribeEvent(priority = EventPriority.HIGHEST)` → `LivingDeathEvent`；B `:75-85` 同。

**「伤害值」处于吸收前还是吸收后——`LivingEntity` 反编译源码证据**：

**A（1.21.1）`net/minecraft/world/entity/LivingEntity.java:1785-1804`**（节选原文）：

```java
    protected void actuallyHurt(DamageSource damageSource, float damageAmount) {
        if (!this.isInvulnerableTo(damageSource)) {
            this.damageContainers.peek().setReduction(... Reductions.ARMOR, ... this.getDamageAfterArmorAbsorb(...));
            this.getDamageAfterMagicAbsorb(damageSource, this.damageContainers.peek().getNewDamage());
            float damage = net.neoforged.neoforge.common.CommonHooks.onLivingDamagePre(this, this.damageContainers.peek());   // :1789 ← LivingDamageEvent.Pre 在此派发
            this.damageContainers.peek().setReduction(... Reductions.ABSORPTION, Math.min(this.getAbsorptionAmount(), damage));  // :1790 ← 吸收在此才结算
            float absorbed = Math.min(damage, this.damageContainers.peek().getReduction(... ABSORPTION));
            this.setAbsorptionAmount(Math.max(0, this.getAbsorptionAmount() - absorbed));  // :1792
            float f1 = this.damageContainers.peek().getNewDamage();
            float f = absorbed;
            ...
            if (f1 != 0.0F) {
                this.getCombatTracker().recordDamage(damageSource, f1);
                this.setHealth(this.getHealth() - f1);
                ...
            }
            net.neoforged.neoforge.common.CommonHooks.onLivingDamagePost(this, this.damageContainers.peek());   // :1805 ← Post 在扣血之后
        }
    }
```
→ **`float damage = CommonHooks.onLivingDamagePre(...)` 发生在 `:1790` 吸收扣减之前**，故 `LivingDamageEvent.Pre.getNewDamage()` = **吸收前**。

**B（1.20.1）`net/minecraft/world/entity/LivingEntity.java:1663-1688`**（节选原文）：

```java
    protected void actuallyHurt(DamageSource damageSource, float damageAmount) {
       if (!this.isInvulnerableTo(damageSource)) {
          damageAmount = net.minecraftforge.common.ForgeHooks.onLivingHurt(this, damageSource, damageAmount);   // :1665 ← LivingHurtEvent(护甲/吸收之前)
          if (damageAmount <= 0) return;
          damageAmount = this.getDamageAfterArmorAbsorb(damageSource, damageAmount);                            // :1667
          damageAmount = this.getDamageAfterMagicAbsorb(damageSource, damageAmount);                            // :1668
          float f1 = Math.max(damageAmount - this.getAbsorptionAmount(), 0.0F);                                 // :1669 ← 吸收在此结算
          this.setAbsorptionAmount(this.getAbsorptionAmount() - (damageAmount - f1));                           // :1670
          float f = damageAmount - f1;
          ...
          f1 = net.minecraftforge.common.ForgeHooks.onLivingDamage(this, damageSource, f1);                     // :1680 ← LivingDamageEvent(吸收之后!)
          if (f1 != 0.0F) {
             this.getCombatTracker().recordDamage(damageSource, f1);
             this.setHealth(this.getHealth() - f1);                                                             // :1683
             ...
```
→ **`LivingDamageEvent`（`ForgeHooks.onLivingDamage`，`:1680`）拿到的 `f1` 已经是吸收后的剩余扣血量**。`ForgeHooks` 侧映射证据：`net/minecraftforge/common/ForgeHooks.java:298-302` `new LivingDamageEvent(entity, src, amount)`（`:292-296` 为 `LivingHurtEvent`）。

**A 的替代选项证据**：`net/neoforged/neoforge/event/entity/living/LivingDamageEvent.java:97`（`public static class Post`）与 `:129-131`（`/** {@return the amount of health this entity lost during this sequence} */ public float getNewDamage()`）——`Post` 的 `getNewDamage()` 正是「本次从生命里真正扣掉多少」；但 `Post` **没有** `setNewDamage`（`setNewDamage` 只在 `Pre`，`:81-83`），所以气囊（需要把伤害改成 0）**不能**迁到 `Post`。

### ③ 最小 diff 方案

只改 A（B 不动）：

A `event/ChipDamageHandler.java:51-59`，把 `float damage = event.getNewDamage();` 拆成「原始值（给磨刀石）」+「吸收后值（给气囊）」：

```java
        float damage = event.getNewDamage();
        if (damage <= 0.0F) return;

        // 安全气囊:致命伤害判定必须按「吸收(黄心)之后」剩余的实际扣血量 ——
        // 1.21.1 的 LivingDamageEvent.Pre 在吸收扣减之前派发(LivingEntity.java:1789 早于 :1790),
        // 故此处手动扣除吸收值;这与 1.20.1 的 LivingDamageEvent(ForgeHooks.onLivingDamage,
        // LivingEntity.java:1680,已在 :1669-1670 扣过吸收)口径一致。
        float lethalDamage = damage - player.getAbsorptionAmount();
        if (lethalDamage >= player.getHealth() && AirbagChipItem.tryNegateFatal(player)) {
            event.setNewDamage(0.0F);
            return;
        }

        // 磨刀石:低血量减伤 + 血量 > 1 时不可被一次伤害击倒(仍按吸收前的 damage 计算)
        float modified = WhetstoneChipItem.modifyIncomingDamage(player, damage);
        if (modified != damage) {
            event.setNewDamage(modified);
        }
```

（`lethalDamage >= player.getHealth()` 时 `getHealth() > 0` 必然蕴含 `lethalDamage > 0`，无需额外 `> 0` 判定；原 `:52` 的 `damage <= 0` 早退保留。）

### ④ 不确定 / 需人工确认

1. **B 侧是否也要动**：按以上证据 B 已满足需求，**建议 B 不改**——但这样两版本代码形状就不一致（A 有 `lethalDamage`，B 没有），需要你确认「功能对等优先于代码同形」（AGENTS.md 的默认规则是功能对等）。
2. **`LivingDeathEvent` 兜底（A `:75-85` / B `:75-85`）没有吸收概念**：它直接用 `AirbagChipItem.tryNegateFatal(player)`，与吸收无关。本次 A6 只涉及 `LivingDamageEvent` 路径；兜底路径是否也要考虑吸收需你裁决（现状：兜底只在「完全没经过伤害管线」时生效）。
3. 若 `player.getAbsorptionAmount() > damage`，`lethalDamage` 变负 → 不触发气囊（正确：本次不致命）。此时磨刀石仍会用吸收前的 `damage` 参与封顶（`WhetstoneChipItem.modifyIncomingDamage`，A/B `:60-78`），**行为与今日一致**（今日也是吸收前值），无回归。

---

## 虚空 / `/kill` 规则 — 维持现状 vs 现状是否达标

### ① 结论（一句话）

**现状不满足你写的规则「虚空不可救 + `/kill` 可救」**：`minecraft:bypasses_invulnerability` 的取值就是 `{minecraft:out_of_world, minecraft:generic_kill}`——**虚空和 `/kill` 都在里面**。因此原版图腾（`checkTotemDeathProtection`）与末影骰子对**两者一律拒绝**（`/kill` 救不了），而**安全气囊两者都救**（`ChipDamageHandler` 刻意不检查该标签，虚空也能被气囊救）。若要真正落在「虚空不可救 + `/kill` 可救」，最小 diff 需要**改气囊的排除条件**（见 ③）。

### ② 三项证据

**①-a 保命判定是否检查 `BYPASSES_INVULNERABILITY`**

- `ChipDamageHandler`：**不检查**（这是刻意的，写在类注释里，且两版本一致）。
  - A `event/ChipDamageHandler.java:24-30`（类注释）：
```java
 * <p><b>安全气囊对「无视无敌」的伤害源同样生效(2026-09-15 用户裁决)</b>:不再按
 * {@code DamageTypeTags.BYPASSES_INVULNERABILITY} 排除。原版源码依据:本事件由
 * {@code CommonHooks.onLivingDamagePre} 在 {@code LivingEntity#actuallyHurt} 内派发,
 * 且 {@code actuallyHurt} 的 {@code isInvulnerableTo} 前置判定对 bypasses_invulnerability
 * 的伤害源为假 → 事件照常派发;把最终伤害改成 0 后 {@code if (f1 != 0.0F)} 分支跳过扣血,
 * 与普通致命伤完全等价。故 {@code /kill}(generic_kill)、虚空伤害与其它模组的真伤都会在
 * 气囊前止步。另有 {@link #onLivingDeath} 兜底:绕过伤害管线直接致死时仍由气囊接管。
```
  - A `:54-59`（实际代码，**无**标签判定）：
```java
        // 安全气囊:致命伤害(伤害 ≥ 当前血量)时消耗 6 点充能使本次伤害无效。
        // 不排除「无视无敌」(bypasses_invulnerability)的伤害源:气囊必须始终有效(见类注释)。
        if (damage >= player.getHealth() && AirbagChipItem.tryNegateFatal(player)) {
            event.setNewDamage(0.0F);
            return;
        }
```
  - B `:24-30` 注释同文、`:54-59` 代码同形（`event.setAmount(0.0F)`）。B `grep BYPASSES_INVULNERABILITY` 在 `ChipDamageHandler.java` 只命中注释 `:25`。
  - A `:75-85` / B `:75-85` 的 `onLivingDeath` 兜底**同样不检查**该标签。

- `EnderDiceHandler`：**检查**（两版本一致）。
  - A `event/EnderDiceHandler.java:145-156`（原文节选）：
```java
    // 受到致命伤害:未处于冷却时触发一次不死图腾效果并进入 5:00 冷却
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        if (!(entity instanceof Player player)) return;
        if (event.isCanceled()) return;
        // 与原版不死图腾一致:无视无敌(bypasses_invulnerability)的致死伤害不触发
        if (event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        if (!hasEnderDie(player)) return;
        if (isTotemOnCooldown(player)) return;
```
  - B `event/EnderDiceHandler.java:147-156`：`:154` 为 `if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;`（B 已 import `net.minecraft.tags.DamageTypeTags`，见 `:14`；A 用全限定名内联）。

**①-b 原版 `checkTotemDeathProtection` 的判定条件**

- A `net/minecraft/world/entity/LivingEntity.java:1306-1309`：
```java
    private boolean checkTotemDeathProtection(DamageSource damageSource) {
        if (damageSource.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        } else {
```
- B `net/minecraft/world/entity/LivingEntity.java:1247-1250`：
```java
    private boolean checkTotemDeathProtection(DamageSource damageSource) {
       if (damageSource.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
          return false;
       } else {
```
- 调用点：A `:1260-1267`（`if (this.isDeadOrDying()) { if (!this.checkTotemDeathProtection(source)) { ... this.die(source); } }`）；B `:1205-1212` 同形。
- 触发后清效果：A `:1329` `this.removeEffectsCuredBy(net.neoforged.neoforge.common.EffectCures.PROTECTED_BY_TOTEM);`；B `:1270` `this.removeAllEffects();`。

**①-c `generic_kill` 与 `out_of_world` 是否在 `bypasses_invulnerability` 里（从 jar 直接读 entry，未解压）**

- A `neoforge-21.1.235-client-extra-aka-minecraft-resources.jar` → `data/minecraft/tags/damage_type/bypasses_invulnerability.json`：
```json
{
  "values": [
    "minecraft:out_of_world",
    "minecraft:generic_kill"
  ]
}
```
- B `client-extra-1.20.1-47.4.10.jar` → 同名文件，**内容逐字节相同**（同样是 `out_of_world` + `generic_kill`）。
- `/kill` 的伤害类型确认：`data/minecraft/damage_type/generic_kill.json` 在**两个** client-extra jar 中都存在（A 103B / B 103B）。
- 虚空伤害类型确认：`data/minecraft/damage_type/out_of_world.json` 同样在两个 jar 中都存在（A 102B / B 102B）。

**结论表**：

| 情形 | 原版不死图腾（checkTotemDeathProtection） | 末影骰子（EnderDiceHandler） | 安全气囊（ChipDamageHandler） |
|---|---|---|---|
| 虚空 `out_of_world` | ❌ 不救（在标签里，`:1307`/`:1248` 直接 return false） | ❌ 不救（A `:153` / B `:154`） | ✅ **救**（不检查标签） |
| `/kill` `generic_kill` | ❌ 不救（在标签里） | ❌ 不救 | ✅ **救** |
| 普通致命伤 | ✅ 救 | ✅ 救 | ✅ 救 |

**即：现状 = 「虚空可被气囊救、`/kill` 不能被图腾/末影骰救」——与需求「虚空不可救 + `/kill` 可救」正好各自相反。**

### ③ 最小 diff 方案（若确认要达标）

需求要的是「虚空不可救（优先级最高）+ `/kill` 可救」，而现状的判据（`bypasses_invulnerability`）把两者绑在一起，**无法用同一个标签区分**。可选最小改动（二选一，均只动气囊；图腾/末影骰保持"照抄原版"不动）：

- **选项 X（只做「虚空不可救」，不碰 `/kill`）**：给气囊加一个**仅排除虚空**的判定——`event.getSource().is(net.minecraft.world.damageTypes.DamageTypes.OUT_OF_WORLD)`（两版本都有该常量；可先用 `grep` 在反编译源码确认常量名后再写），并在 `onLivingDeath` 兜底里加同样判定。这会让「虚空不可救」达标，但 `/kill` 依旧不可救。
- **选项 Y（两者都达标）**：气囊排除 `BYPASSES_INVULNERABILITY` **但放行 `GENERIC_KILL`**，即 `if (src.is(BYPASSES_INVULNERABILITY) && !src.is(DamageTypes.GENERIC_KILL)) return;`；同时**移除末影骰子与（不可能的）原版图腾对 `/kill` 的拒绝**——注意：原版 `checkTotemDeathProtection` 的拒绝是**硬编码在 MC 里的**，1.20.1/1.21.1 都改不了（除非 Mixin），故「`/kill` 被不死图腾救」在不改 Mixin 的前提下**做不到**；末影骰子侧则可去掉/放宽 `:153`/`:154` 的判定。

### ④ 不确定 / 需人工确认

1. **「`/kill` 会被不死图腾阻止」这一前提与本地 jar 证据冲突**：`generic_kill ∈ bypasses_invulnerability` ⇒ 原版图腾**不会**救 `/kill`。请确认需求原意是「要改成可救（需 Mixin 改原版）」还是「现状已可救（实测记忆有误）」。
2. 若坚持「虚空不可救」，要注意**气囊是唯一能救虚空的东西**——加排除后，玩家在虚空里将只剩末影骰子（而末影骰子也已被 `:153`/`:154` 排除）⇒ 虚空必死。这正是需求想要的，但属于**削弱**（当前气囊能救虚空），请确认。
3. `DamageTypes.OUT_OF_WORLD` / `GENERIC_KILL` 的**确切 Java 常量名本次未逐一核对**（只核对了 JSON 数据包侧），实施前请在 `net/minecraft/world/damagesource/DamageTypes.java` 中确认拼写。

---

## A4 — 死亡清理降为 `LOWEST` 优先级

### ① 结论（一句话）

两版本的死亡清理处理器目前都是**默认优先级（NORMAL）**，改成 `LOWEST` 只需在注解里加 `priority = EventPriority.LOWEST`（两文件都已 import `EventPriority`：A `:57`、B `:57`），同文件已有 `LOWEST` 先例可直接照抄。

### ② 触点清单（注解行 / 方法签名行 / 行号）

**A（neoforge-1.21.1）`event/PlayerLifecycleHandler.java`**

- 注解行 `:114`、方法签名行 `:115`：
```java
    @SubscribeEvent
    public static void onPlayerDeathClearEffects(LivingDeathEvent event) {
```
- 方法体结束于 `:179`（`:113` 为上方注释行）。
- 同文件 `LOWEST` 先例 `:184-186`：
```java
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void onPlayerCloneRestoreDeathPreserved(
            net.neoforged.neoforge.event.entity.player.PlayerEvent.Clone event) {
```

**B（forge-1.20.1）`event/PlayerLifecycleHandler.java`**

- 注解行 `:111`、方法签名行 `:112`：
```java
    @SubscribeEvent
    public static void onPlayerDeathClearEffects(LivingDeathEvent event) {
```
- 方法体结束于 `:175`（`:110` 为上方注释行）。
- 同文件 `LOWEST` 先例 `:180-182`：
```java
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void onPlayerCloneRestoreDeathPreserved(
            net.minecraftforge.event.entity.player.PlayerEvent.Clone event) {
```

### ③ 最小 diff 方案

A：`event/PlayerLifecycleHandler.java:114`
```java
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerDeathClearEffects(LivingDeathEvent event) {
```
B：`event/PlayerLifecycleHandler.java:111`
```java
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerDeathClearEffects(LivingDeathEvent event) {
```
（两文件顶部已 `import net.neoforged.bus.api.EventPriority;` / `import net.minecraftforge.eventbus.api.EventPriority;`，见 A `:57` / B `:57`，无需新增 import。）

### ④ 不确定 / 需人工确认

1. **这是本次最需要实测的风险点**：`ChipDamageHandler.onLivingDeath` 是 `HIGHEST`，`EnderDiceHandler.onLivingDeath` 是**默认 NORMAL**，死亡清理改为 LOWEST 后会**晚于**末影骰子。若末影骰子取消死亡（`event.setCanceled(true)`，A `:157` / B `:158`），清理会在 `event.isCanceled()` 检查处（A `:119` / B `:116`）早退 ⇒ **保命成功时不再执行死亡清理**。这正是 `LOWEST` 的目的，但请确认这是想要的语义。
2. **反过来的风险**：任何依赖「死亡清理先于其它 NORMAL 监听器执行」的逻辑都会顺序变化。全仓 `LivingDeathEvent` 监听器未逐一枚举——实施前建议 `grep -rn "LivingDeathEvent" <v>/src/main/java` 复核一遍顺序依赖。
3. `PlayerEvent.Clone` 的 LOWEST 回写（A `:184-192` / B `:180-188`）在**死亡事件之后**才派发，故死亡清理改 LOWEST **不影响**它。

---

## A10 — 1.20.1 末影骰保命路径改为「只清除有害效果」

### ① 结论（一句话）

**A10 的前提有一处事实错误**：1.21.1 的 `removeEffectsCuredBy(EffectCures.PROTECTED_BY_TOTEM)` **不是「只清除有害效果」**——NeoForge 的 `EffectCures.DEFAULT_CURES = {MILK, PROTECTED_BY_TOTEM}`，`IMobEffectExtension.fillEffectCures` 默认把这两个 cure 加给**所有**效果，所以该调用实际清掉的是「几乎全部效果（含增益）」，与 1.20.1 的 `removeAllEffects()` 语义基本相同。真正要做的选择是：**（i）保持现状（≈清全部）** 还是 **（ii）按你的新口径改成只清 HARMFUL**（那 1.21.1 侧同样要改，否则两版本不对等）。1.20.1 侧可用 API 齐备（见 ②-d）。

### ② 证据

**②-a 1.20.1 该段完整代码**

B `event/EnderDiceHandler.java:146-179`（原文，含上下文）：

```java
    // 受到致命伤害:未处于冷却时触发一次不死图腾效果并进入 5:00 冷却
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        if (!(entity instanceof Player player)) return;
        if (event.isCanceled()) return;
        // 与原版不死图腾一致:无视无敌(bypasses_invulnerability)的致死伤害不触发
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        if (!hasEnderDie(player)) return;
        if (isTotemOnCooldown(player)) return;

        event.setCanceled(true);
        // 与原版图腾一致:清除全部效果(死亡后不掉落/不触发死亡逻辑,由取消死亡保证)
        player.setHealth(1.0F);
        player.removeAllEffects();
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION,
                REGEN_DURATION_TICKS, REGEN_AMPLIFIER, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,
                ABSORPTION_DURATION_TICKS, ABSORPTION_AMPLIFIER, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE,
                FIRE_RESIST_DURATION_TICKS, FIRE_RESIST_AMPLIFIER, false, true));
        // 客户端播放不死图腾动画:粒子 + 音效 + 手持高亮(图标为末影骰子)
        ModNetwork.EnderDieTotemMessage.send(player);
```
（**需改的一行是 `:161` `player.removeAllEffects();`**；`:160` 是 `player.setHealth(1.0F);`。）

**②-b 1.21.1 对应段**

A `event/EnderDiceHandler.java:145-168`（原文节选）：

```java
    // 受到致命伤害:未处于冷却时触发一次不死图腾效果并进入 5:00 冷却
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        if (!(entity instanceof Player player)) return;
        if (event.isCanceled()) return;
        // 与原版不死图腾一致:无视无敌(bypasses_invulnerability)的致死伤害不触发
        if (event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        if (!hasEnderDie(player)) return;
        if (isTotemOnCooldown(player)) return;

        event.setCanceled(true);
        // 与原版图腾一致:清除「可被图腾治愈」的效果(死亡后不掉落/不触发死亡逻辑,由取消死亡保证)
        player.setHealth(1.0F);
        player.removeEffectsCuredBy(net.neoforged.neoforge.common.EffectCures.PROTECTED_BY_TOTEM);
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION,
                REGEN_DURATION_TICKS, REGEN_AMPLIFIER, false, true));
```
（对应行：`:158` 注释、`:159` `setHealth`、`:160` `removeEffectsCuredBy(...)`。）

**②-b′ 为什么它不是「只清有害」**（反编译证据）

- `net/neoforged/neoforge/common/EffectCures.java:22-24`：
```java
    public static final EffectCure PROTECTED_BY_TOTEM = EffectCure.get("protected_by_totem");

    public static final Set<EffectCure> DEFAULT_CURES = Set.of(MILK, PROTECTED_BY_TOTEM);
```
- `net/neoforged/neoforge/common/extensions/IMobEffectExtension.java:23-24`：
```java
    default void fillEffectCures(Set<EffectCure> cures, MobEffectInstance effectInstance) {
        cures.addAll(EffectCures.DEFAULT_CURES);
```
- `net/minecraft/world/effect/MobEffectInstance.java:86`：`this.effect.value().fillEffectCures(this.cures, this);`；`:363-369` 为 `private final Set<EffectCure> cures` + `getCures()`。
- `net/minecraft/world/entity/LivingEntity.java:3602-3616`（`removeEffectsCuredBy`）：`:3609` `if (effect.getCures().contains(cure) && !EventHooks.onEffectRemoved(this, effect, cure))`。
- 本模组**没有**任何 `fillEffectCures` 覆写（`grep fillEffectCures` 在 `neoforge-1.21.1/src/main/java` 0 命中）⇒ 本模组效果也带 `PROTECTED_BY_TOTEM`。

**②-c 1.20.1 API 可用性**

- 1.20.1 **没有** `EffectCures` / `removeEffectsCuredBy`（`forge-1.20.1-47.4.10-sources.jar` 全 jar 搜 `EffectCure` **0 命中**；`LivingEntity` 里也没有 `removeEffectsCuredBy`）。
- 1.20.1 可用：
  - `net/minecraft/world/effect/MobEffect.java:158` `public MobEffectCategory getCategory()`
  - `net/minecraft/world/effect/MobEffectInstance.java:146` `public MobEffect getEffect()`（返回 `MobEffect`，**不是** `Holder<MobEffect>`）
  - `net/minecraft/world/entity/LivingEntity.java:921-923` `public Collection<MobEffectInstance> getActiveEffects()`
  - `net/minecraft/world/entity/LivingEntity.java:1002` `public boolean removeEffect(MobEffect effect)`
  - `net/minecraft/world/effect/MobEffectCategory` 枚举（本模组已大量使用，例：B `effect/BlueCurseEffect.java:19` `super(MobEffectCategory.HARMFUL, 0x1E90FF);`）
- 1.20.1 的对照实现范例（同口径的「只清有害」代码）B `item/card/FightPoisonWithPoisonCardItem.java:68`：`&& effect.getCategory() == MobEffectCategory.HARMFUL) {`（A 同文件 `:68` 相同）。

**②-d `MobEffectEvent.Remove` HIGH 守卫（会取消 `astral_dice:` 效果的移除）**

- A `event/ModEffectEvents.java:110-131`（原文）：
```java
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onModEffectRemovalPrevented(MobEffectEvent.Remove event) {
        if (event.getEntity().level().isClientSide()) return;
        // 本模组内部移除(ModEffectRemoval)/计时器守卫的强制移除(时长校正)放行
        if (ModEffectRemoval.isInternal()) return;
        if (EffectTimerGuard.isForcedRemoval()) return;
        // 死亡时允许清除,保证死亡后效果状态能正常重置
        if (event.getEntity().isDeadOrDying()) return;
        MobEffectInstance effect = event.getEffectInstance();
        if (effect == null || effect.getEffect() == null) return;
        // 标记携带的发光效果:标记仍存在时同步保留发光(牛奶/effect clear 不得单独清除),
        // 保证发光与标记同寿命——标记自然到期/死亡时两者一起移除(此时标记已不存在,此处自动放行)
        if (effect.getEffect().value() == net.minecraft.world.effect.MobEffects.GLOWING.value()
                && event.getEntity().hasEffect(ModEffects.MARKED)) {
            event.setCanceled(true);
            return;
        }
        String effectId = effect.getEffect().getRegisteredName();
        if (effectId != null && effectId.startsWith(AstralDiceMod.MODID + ":")) {
            event.setCanceled(true);
        }
    }
```
  关键行：A `:110`（注解）、`:114`（内部放行）、`:117`（死亡放行）、`:128-130`（`astral_dice:` 取消）。
- B `event/ModEffectEvents.java:106-127`：同结构，关键行 `:106`（注解）、`:110`、`:113`、`:123-126`（判定用 `net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getKey(effect.getEffect()).toString()`）。
- `ModEffectRemoval`（内部通道）签名差异：A `event/ModEffectRemoval.java:27` `public static void remove(Player player, Holder<MobEffect> effect)`；B `event/ModEffectRemoval.java:26` `public static void remove(Player player, MobEffect effect)`。
- **`removeAllEffects()` 是否触发该守卫——是**：A `net/minecraft/world/entity/LivingEntity.java:932-948`，`:941` `if(net.neoforged.neoforge.event.EventHooks.onEffectRemoved(this, effect, null)) continue;`；B `:903-919`，`:912` `if(net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.living.MobEffectEvent.Remove(this, effect))) continue;`。`EventHooks.onEffectRemoved` → `MobEffectEvent.Remove`：A `net/neoforged/neoforge/event/EventHooks.java:1054-1056`。
- ⇒ **推论（重要）**：EnderDiceHandler 里执行 `setHealth(1.0F)` 之后 `isDeadOrDying()` 为 false，故守卫生效 ⇒ 现在 `removeAllEffects()` **实际清不掉任何 `astral_dice:` 效果**（如 `dice_blessing`、`papara_bite`），只能清原版/其它模组的效果。1.21.1 的 `removeEffectsCuredBy` 同样被该守卫拦截（`:3609` 走同一个 `onEffectRemoved`）⇒ **两版本在「本模组效果是否被清除」上现状其实是一致的**。

### ③ 最小 diff 方案

**若目标是「只清有害效果」（HARMFUL），两版本都要改**（否则不对等）：

- B `event/EnderDiceHandler.java:161`，把：
```java
        player.removeAllEffects();
```
替换为（用 `ModEffectRemoval` 内部通道，绕开 HIGH 守卫，才能真的清掉 `astral_dice:` 的 HARMFUL 效果：`marked` / `weak_mark` / `moses_broken` / `pandaman_taunt` / `undercover_investigation` / `blue_curse`）：
```java
        // 与原版图腾的「清全部」区分:只清除有害效果(对齐"末影骰保命不剥夺增益"的口径);
        // 必须经 ModEffectRemoval 内部通道,否则 astral_dice:* 的有害效果会被
        // ModEffectEvents.onModEffectRemovalPrevented(HIGH) 取消移除(行 106/123-126)。
        java.util.List<net.minecraft.world.effect.MobEffect> harmful = new java.util.ArrayList<>();
        for (net.minecraft.world.effect.MobEffectInstance inst : player.getActiveEffects()) {
            if (inst.getEffect().getCategory() == net.minecraft.world.effect.MobEffectCategory.HARMFUL) {
                harmful.add(inst.getEffect());
            }
        }
        for (net.minecraft.world.effect.MobEffect effect : harmful) {
            com.merlinkitsune.astral_dice.event.ModEffectRemoval.remove(player, effect);
        }
```
（`ModEffectRemoval` 与 `EnderDiceHandler` 同在 `com.merlinkitsune.astral_dice.event` 包，可省略 import，但该包规范要求跨类引用显式 import，按仓库习惯补 `import`。）

- A `event/EnderDiceHandler.java:160`，若要同口径，把：
```java
        player.removeEffectsCuredBy(net.neoforged.neoforge.common.EffectCures.PROTECTED_BY_TOTEM);
```
替换为等价的有害效果筛选（A 的 `ModEffectRemoval.remove(Player, Holder<MobEffect>)` 需要 `Holder`，可从 `inst.getEffect()`（A 返回 `Holder<MobEffect>`）直接取）：
```java
        java.util.List<net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>> harmful = new java.util.ArrayList<>();
        for (net.minecraft.world.effect.MobEffectInstance inst : player.getActiveEffects()) {
            if (inst.getEffect().value().getCategory() == net.minecraft.world.effect.MobEffectCategory.HARMFUL) {
                harmful.add(inst.getEffect());
            }
        }
        for (net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect : harmful) {
            com.merlinkitsune.astral_dice.event.ModEffectRemoval.remove(player, effect);
        }
```

**若目标是「维持原版图腾语义（清全部）」**：**两版本都不改**（现状已等价，见 ②-d 推论）——但这与「只清除有害效果」的需求相反，需你明确取舍。

### ④ 不确定 / 需人工确认

1. **需求前提需修正**：`removeEffectsCuredBy(PROTECTED_BY_TOTEM)` ≠ 「只清有害」，而是「清掉所有默认 cure 的效果（含增益）」（证据见 ②-b′）。请确认要的是「只清有害（两版本都改）」还是「对齐原版清全部（都不改）」。
2. 「清除有害效果」会与**原版不死图腾的既有语义产生差异**：原版图腾清掉增益（含其它模组给的增益）。改成 HARMFUL-only 后，末影骰保命会**变成比原版图腾更强**（保留增益）。这是平衡性变更，不是纯技术重构。
3. **与 A4 的顺序耦合**：A4 把死亡清理降为 LOWEST 后，末影骰（NORMAL）会先取消死亡 ⇒ 清理早退。但本项改动在**死亡事件内部**直接操作效果，与 A4 无关；若两者同时实施，请注意「末影骰保命成功时，`PlayerLifecycleHandler` 的死亡清理不再执行」——那么**剩余的有害效果不会被死亡清理兜底清除**，需要确认是否可接受（本项改动的目的正是保留/清除特定效果，故应可接受，但仍建议实测）。
4. 未确认项：`player.getActiveEffects()` 在迭代中直接 `removeEffect` 会有 `ConcurrentModificationException` 风险——上面的方案先收集到 `List` 再移除，已规避；但 `ModEffectRemoval.remove` 内部走 `player.removeEffect(...)` 会触发 `MobEffectEvent.Remove` 与 `onEffectRemoved` 回调，回调中若再次改动 `activeEffects` 需实测确认。

---

## 附：本次侦察中「两版本存在单侧差异」一览

| 项 | 差异 |
|---|---|
| A1 | 无差异：伤害类型定义、标签文件清单与内容、`bypasses_cooldown` 缺失情况、`invulnerableTime` 先例，两版本行号仅相差 2-3 行 |
| A3 | `DiceCombatModifiers` 无差异；**雨中 ×1.4 的实际数值语义有差异**（B 在护甲减免前放大，A 在减免后放大，见 A3 ⑤-2） |
| A6 | **单侧**：B 已是吸收后判定；A 需手写 `- getAbsorptionAmount()` |
| 虚空/kill | 无差异（两版本 `bypasses_invulnerability` 内容逐字节相同；气囊/末影骰判定同形） |
| A4 | 无差异（均默认优先级，均有 LOWEST 先例） |
| A10 | **单侧**：B 用 `removeAllEffects()`，A 用 `removeEffectsCuredBy(PROTECTED_BY_TOTEM)`；但受 HIGH 守卫影响后**运行时效果基本等价**（都清不掉 `astral_dice:` 效果） |
| 其它 | 1.20.1 **无** `EffectCures` / `removeEffectsCuredBy`（反编译 jar 全库 0 命中）——这是平台 API 缺失，不是本仓的疏漏 |
