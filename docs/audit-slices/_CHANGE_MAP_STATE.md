# _CHANGE_MAP_STATE — 5 项改动的双版本精确触点

> ## ⚠️⚠️ 开工前必读:工作区已被并发实现(本文件描述的是 **HEAD 基线**)
>
> **本文件的全部行号与代码原文 = `git HEAD` 状态(改动前基线)**,已逐项与 `git show HEAD:<path>` 核对一致
> (例:`HostileTargets.java` HEAD = 37 行、无双参重载;`BaseSignItem` 的 `stillInSlot` 在 HEAD `:168`;
> `EffectCardPeriod.tick` 的 `if (cooldown <= 0)` 在 HEAD `:317`;`CurioSlotUtil.isRealUnequip` 在 HEAD `:87`)。
>
> **但在本轮侦察进行期间(2026-09-15 15:39:52 起,集合仍在增长),另一个并发 agent 已经把 5 项改动全部落在了工作区**:
> `git status` 在 **15:41:32** 显示 **30 个源文件被修改** 并新增 4 个文件
> (`combat/PlayerHostilityTracker.java` 双版本、`data/minecraft/tags/damage_type/bypasses_cooldown.json` 双版本);
> **完整清单与实测时间戳见文末「附二」**。因此:
> 1. **本文件的 `文件:行号` 对工作区当前文件已失效** —— 要看本文件描述的"改前状态",请用 `git show HEAD:<path>`,
>    或用 `git stash` / `git diff` 对照;不要在已被改写的工作区文件上按本文件的行号做机械替换。
> 2. 并发实现与本文第 1 项的推荐方案**已存在命名/签名差异**(实测):并发版为
>    `HostileTargets.isHostile(Entity viewer, Entity target)`(**viewer 在前、且类型是 `Entity` 而非 `Player`**),
>    追踪类名为 **`PlayerHostilityTracker`**(本文推荐名 `HostilePlayerSources`),
>    同队判定用 **vanilla `viewerPlayer.getTeam() != null && viewerPlayer.isAlliedTo(targetPlayer)`**
>    (本文推荐在 `EventTargetCollector` 加 `isSameTeam`)。**实施/评审前请直接读工作区文件,勿以本文的推荐命名为准。**
> 3. 本文件的**分析结论、风险清单、调用点全表**仍有效(它们描述的是改前行为与改动的影响面),但**"最小 diff"章节
>    应视为"备选方案/评审基准",与工作区已有实现做 diff 后再决定取舍**。

**分支**: `multi-1.20.1-1.21.1`　**姿态**: 只读侦察(本文件是唯一写入物;全部行号与代码原文来自本轮实际 `read`/`grep`)

**记号**: `A` = `neoforge-1.21.1`(MC 1.21.1 / NeoForge 21.1.235 / Java 21)。`B` = `forge-1.20.1`(MC 1.20.1 / Forge 47.4.10 / Java 17)。
路径省略前缀 `<子项目>/src/main/java/com/merlinkitsune/astral_dice/`,写作 `combat/HostileTargets.java` 形式。

**外部依赖版本(实测,来自 Gradle 缓存 jar 的 `META-INF/MANIFEST.MF`)**:
- A 侧 Curios = `9.5.1+1.21.1`(`C:\Users\xmace\.gradle\caches\modules-2\files-2.1\maven.modrinth\curios\yohfFbgD\...\curios-yohfFbgD.jar`)
- B 侧 Curios = `5.14.1+1.20.1`(`...\curios\IPQlZkz1\...\curios-IPQlZkz1.jar`)

**未验证项一律标注**;本文件不含推测性行号。

---

# 1. 大当家养精蓄锐溅射的敌对玩家规则(全局规则)

## ① 结论(一句话)

现有 `HostileTargets.isHostile(Entity)` **只有单参签名**,判定口径是「`Enemy` ∪ 被激怒的 `NeutralMob`」,**玩家永远返回 false**(`Player` 既非 `Enemy` 也非 `NeutralMob`);全仓 27 个调用点**每一个的所在作用域里都拿得到"视者为谁"**,但目前**没有任何一处**把"谁视谁为敌"传下去 —— 实施新规则需要**新增一个双参重载 + 一个按玩家存进攻者 UUID 集合的新附件**,并且**只把真正需要新规则的调用点切到双参重载**(否则嘲讽/上标记等一大批"只对敌对生物"的效果会连带开始影响玩家)。

## ② 触点清单 / 文件路径:行号

| 内容 | A(neoforge-1.21.1) | B(forge-1.20.1) | 两侧差异 |
|---|---|---|---|
| `HostileTargets.java` 全文 | 1–37 | 1–37 | **两侧逐字节相同** |
| `isHostile(Entity)` 定义 | `combat/HostileTargets.java:32` | `combat/HostileTargets.java:32` | 无 |
| 溅射受害者筛选 | `combat/DiceCombatEvents.java:633–635` | `combat/DiceCombatEvents.java:630–632` | 行号 −3 |
| 立牌常量(SPLASH_*) | `item/sign/FenSignItem.java:37,43,45,47,49` | `item/sign/FenSignItem.java:38,44,46,48,50` | 行号 +1 |
| 攻击事件入口(最佳记录点) | `combat/DiceCombatEvents.java:185`(`onLivingDamagePre`) | `combat/DiceCombatEvents.java:181`(同名) | 事件类型不同 |
| 队伍工具 | `event/EventTargetCollector.java:24,52,59,87,127` | 同左,1–149 两版**逐字节相同** | 无 |
| 新附件注册位置(1 处) | `component/ModAttachments.java:20–21`(`ATTACHMENTS` 注册器)+ `AstralDiceMod.java:42`(`register`) | `component/ModAttachments.java:27–29`(`register` 空壳)+ 静态字段区 | 机制不同(见 ⑥) |
| 字符串型附件先例 | `component/ModAttachments.java:194–205`(`FLASHLIGHT_GRANTED_TARGETS`) | `component/ModAttachments.java:165` | 行号差 |
| 静态 `Map<UUID,…>` 先例 | `item/chip/ElectricSwordChipItem.java:35`(`KILL_COUNTS`) | `item/chip/ElectricSwordChipItem.java:35` | 无 |
| 死亡/重生挂点 | `event/PlayerLifecycleHandler.java:115–179`(死亡)、`184–192`(Clone)、`211–221`(Respawn) | `event/PlayerLifecycleHandler.java:112–175`、`180–187`、`–` | B 无 Respawn 兜底?见 §3 |

## ③ 逐字原文

### ③-1 `combat/HostileTargets.java` 两版本全文(A 1–37 = B 1–37,逐字节相同)

```java
package com.merlinkitsune.astral_dice.combat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.Enemy;

/**
 * 「敌对目标」判定的**唯一入口**(2026-09-14 用户裁决,必须遵守)。
 *
 * <p>口径:{@code 敌对目标 = 敌对生物 ∪ 已被激怒的中立生物}
 * <ul>
 *   <li><b>敌对生物</b>:{@link Enemy} 实例 —— 含 {@code Monster} 全部子类,以及
 *       {@code Ghast}/{@code Phantom}/{@code EnderDragon}/{@code Slime}(含岩浆怪)/
 *       {@code Shulker}/{@code Zoglin}/{@code Hoglin}。**平静的敌对类中立生物
 *       (末影人/僵尸猪灵/猪灵/猪灵蛮兵/疣猪兽)同样计入** —— 它们本身就是 {@link Enemy}。</li>
 *   <li><b>已被激怒的中立生物</b>:{@link NeutralMob} 实例且 {@code isAngry()}
 *       (= {@code getRemainingPersistentAngerTime() > 0},被激怒后 20~39 秒)为真。
 *       全原版 {@code NeutralMob} 直接实现者仅 6 个:{@code EnderMan}/{@code ZombifiedPiglin}
 *       (二者即 {@link Enemy})与 <b>狼 / 铁傀儡 / 北极熊 / 蜜蜂</b>(仅这 4 个靠 anger 判定进入敌对集合)。</li>
 * </ul>
 *
 * <p>熊猫/骆驼/山羊/羊驼/行商羊驼/海豚/狐狸等**不是** {@code NeutralMob},永不视为敌对目标。
 *
 * <p><b>禁止</b>在玩法代码里再写裸的 {@code instanceof Enemy} 来判定敌对目标 —— 那会漏掉
 * 被激怒的狼/铁傀儡/北极熊/蜜蜂。新增判定一律调用本类。
 */
public final class HostileTargets {
    private HostileTargets() {
    }

    /** 该实体是否为「敌对目标」(敌对生物,或已被激怒的中立生物)。 */
    public static boolean isHostile(Entity entity) {
        if (entity == null) return false;
        if (entity instanceof Enemy) return true;
        return entity instanceof NeutralMob neutral && neutral.isAngry();
    }
}
```

**事实**:`isHostile(玩家)` 恒为 `false`(A/B 同)。因此现状下「敌对目标」集合**不含任何玩家**,溅射只打怪物。

### ③-2 溅射受害者筛选(`FenSignItem` 溅射的引用点)

A `combat/DiceCombatEvents.java:623–635`:

```java
        if (!player.level().isClientSide() && fenSplashArmed) {
            com.merlinkitsune.astral_dice.item.sign.FenSignItem.consumeSplashCost(player);
            aoeProcessing = true;
            try {
                // 下限取立牌常量(5 点),高于全局"按比例不足 1 时按 1 计"的兜底
                float splashDmg = (float) Math.max(
                        com.merlinkitsune.astral_dice.item.sign.FenSignItem.SPLASH_DAMAGE_MIN,
                        finalDmg * com.merlinkitsune.astral_dice.item.sign.FenSignItem.SPLASH_RATIO);
                net.minecraft.world.phys.AABB splashBox = target.getBoundingBox()
                        .inflate(com.merlinkitsune.astral_dice.item.sign.FenSignItem.SPLASH_RANGE);
                var splashVictims = target.level().getEntitiesOfClass(
                        net.minecraft.world.entity.LivingEntity.class, splashBox,
                        e -> HostileTargets.isHostile(e) && e.isAlive());
```

B `combat/DiceCombatEvents.java:620–632`:

```java
        if (!player.level().isClientSide() && fenSplashArmed) {
            com.merlinkitsune.astral_dice.item.sign.FenSignItem.consumeSplashCost(player);
            aoeProcessing = true;
            try {
                // 下限取立牌常量(5 点),高于全局"按比例不足 1 时按 1 计"的兜底
                float splashDmg = (float) Math.max(
                        com.merlinkitsune.astral_dice.item.sign.FenSignItem.SPLASH_DAMAGE_MIN,
                        finalDmg * com.merlinkitsune.astral_dice.item.sign.FenSignItem.SPLASH_RATIO);
                net.minecraft.world.phys.AABB splashBox = target.getBoundingBox()
                        .inflate(com.merlinkitsune.astral_dice.item.sign.FenSignItem.SPLASH_RANGE);
                var splashVictims = target.level().getEntitiesOfClass(
                        net.minecraft.world.entity.LivingEntity.class, splashBox,
                        e -> HostileTargets.isHostile(e) && e.isAlive());
```

**关键**:此处 `player`(= 立牌佩戴者 / 溅射受益者)在作用域内,**可以**把「谁视谁为敌」传下去。

## ④ 全仓库 `isHostile(` 调用点(文件:行号 + 调用处上下文)

`grep -n "isHostile("` 命中 54 行(含 2 处定义、3 处 javadoc `{@link ...#isHostile(...)}`)。下表为**全部真实调用点**(27 个,双版本对称),并标明**作用域内是否有"视者(攻击者/受益玩家)"上下文**:

### ④-A `neoforge-1.21.1`(27 处)

| # | 文件:行 | 所在方法 / 上下文 | 视者(玩家)可达? | 上下文 5 行(逐字) |
|---|---|---|---|---|
| 1 | `combat/SpellDamageRegistry.java:249` | 定向爆破 `onHit(ctx,bonus)` 的实体谓词 | ✅ `ctx.attacker` / `ctx.target` | `aabb = ctx.target.getBoundingBox().inflate(6);` / `var nearby = ctx.target.level().getEntitiesOfClass(` / `LivingEntity.class, aabb,` / `e -> HostileTargets.isHostile(e)` / `&& e != ctx.target && e.isAlive());` |
| 2 | `combat/SpellDamageRegistry.java:289` | 贯穿之铳 `isActive(ctx)` | ✅ 同一表达式里两者都有 | `if (!ctx.hasCurio(ModItems.PIERCING_GUN.get())) return false;` / `if (!HostileTargets.isHostile(ctx.target)) return false;` / `return ctx.attacker.hasEffect(ModEffects.LIVING_PAGE)` / `\|\| ctx.attacker.hasEffect(ModEffects.MONSTER_LASER)` / `\|\| ctx.attacker.hasEffect(ModEffects.MONSTER_BRICK)` |
| 3 | `combat/SpellDamageRegistry.java:370` | 电击手套 `onHit` AOE 谓词 | ✅ `ctx.attacker` | `var nearby = ctx.target.level().getEntitiesOfClass(LivingEntity.class, aabb,` / `e -> HostileTargets.isHostile(e) && e != ctx.target && e.isAlive());` / `var source = ...ModDamageTypes` / `.trueDamage(ctx.target.level(), ctx.attacker);` / `DiceCombatEvents.aoeProcessing = true;` |
| 4 | `combat/DiceCombatModifiers.java:354` | 调查阶段攻击修饰器 | ✅ `ctx.attacker` / `ctx.target` | `int markLevel = MarkManager.getLevel(ctx.target);` / `boolean isBoss = BossEntityUtil.isBossEntity(ctx.target);` / `boolean isHostile = HostileTargets.isHostile(ctx.target);` / `if (!isBoss && isHostile) {` / `if (stage >= 3) {` |
| 5 | `combat/DiceCombatEvents.java:320` | 标靶筹码:找最近敌对目标 | ✅ `player`(赐福触发者) | `for (net.minecraft.world.entity.Entity entity : serverLevel.getEntities().getAll()) {` / `if (entity instanceof net.minecraft.world.entity.LivingEntity living` / `&& HostileTargets.isHostile(living) && living.isAlive()) {` / `double distSqr = living.distanceToSqr(player);` / `if (distSqr < nearestDistSqr) {` |
| 6 | `combat/DiceCombatEvents.java:635` | **大当家溅射**(见 ③-2) | ✅ `player` / `target` | 同 ③-2 |
| 7 | `combat/DiceCombatEvents.java:1012` | `isBlessingTarget(LivingEntity target, Player player)` | ✅ 两参都在 | `if (target instanceof Player other) {` / `return other.getTeam() == null \|\| other.getTeam() != player.getTeam();` / `}` / `if (HostileTargets.isHostile(target)) return true;` / `if (target instanceof Mob mob) {` |
| 8 | `combat/DiceCombatEvents.java:1118` | 肉弹战车嘲讽反击 | ✅ `player`(受击者)/`attacker` | `if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) return;` / `if (!HostileTargets.isHostile(attacker)) return;` / `if (!attacker.hasEffect(ModEffects.PANDAMAN_TAUNT)) return;` / `Optional<UUID> tauntSource = ModAttachments.getPandamanTauntSource(attacker);` / `if (tauntSource.isEmpty() \|\| !tauntSource.get().equals(player.getUUID())) return;` |
| 9 | `damage/RailgunBolts.java:58` | `isValidLightningTarget(Entity, LightningBolt)` | ⚠️ 仅 `bolt.getCause()`(可能 null) | `if (target == null) return false;` / `if (!HostileTargets.isHostile(target)) return false;` / `if (target instanceof OwnableEntity ownable) {` / `ServerPlayer cause = bolt == null ? null : bolt.getCause();` / `if (cause != null && cause.getUUID().equals(ownable.getOwnerUUID())) return false;` |
| 10 | `damage/RailgunBolts.java:67–69` | **单参重载** `isValidLightningTarget(Entity)` | ❌ **无玩家上下文**(纯退化口径) | `public static boolean isValidLightningTarget(net.minecraft.world.entity.Entity target) {` / `return isValidLightningTarget(target, null);` / `}` |
| 11 | `item/sign/BonnieSignItem.java:118` | `onKill(killer, killed)` | ✅ `killer` | `if (MarkManager.getLevel(killed) > 0` / `&& !(killed instanceof Player)` / `&& HostileTargets.isHostile(killed)` / `&& killed.getMaxHealth() >= 20) {` / `giveRandomBattleCard(killer);` |
| 12 | `item/sign/LuluSignItem.java:76` | 主动"治愈粘液"遍历 | ✅ `player` | `for (LivingEntity entity : nearby) {` / `if (HostileTargets.isHostile(entity)) {` / `// 敌对生物:缓慢 60 秒` / `EffectTimerGuard.apply(entity, new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 1200, 0, false, true));` / `} else if (isHealTarget(entity, player)) {` |
| 13 | `item/sign/NancyLuSignItem.java:214` | `onDiceBlessingEnded(player)` | ✅ `player` | `boolean hostileNearby = !player.level().getEntitiesOfClass(LivingEntity.class,` / `player.getBoundingBox().inflate(PASSIVE_RANGE),` / `e -> HostileTargets.isHostile(e) && e.isAlive()).isEmpty();` / `if (hostileNearby) {` / `ModAttachments.setNancyLuPassiveType(player, PASSIVE_DEFENSE);` |
| 14 | `item/sign/NancyLuSignItem.java:255` | `AttackEntityEvent` 隐身解除 | ✅ `player` / `target` | `net.minecraft.world.entity.Entity target = event.getTarget();` / `if (!HostileTargets.isHostile(target)` / `&& !(target instanceof Player)) return;` / `NancyLuSignItem.onAttackWhileHidden(player);` |
| 15 | `item/sign/NancyLuSignItem.java:268` | `LivingDamageEvent.Pre` 隐身解除 | ✅ `player` / `victim` | `LivingEntity victim = event.getEntity();` / `if (!HostileTargets.isHostile(victim) && !(victim instanceof Player)) return;` / `if (!isEquipped(player)) return;` / `onAttackWhileHidden(player);` |
| 16 | `item/sign/PandamanSignItem.java:105` | 主动嘲讽选目标 | ✅ `player` | `AABB aabb = player.getBoundingBox().inflate(TAUNT_RANGE);` / `List<LivingEntity> nearby = player.level().getEntitiesOfClass(LivingEntity.class, aabb,` / `e -> HostileTargets.isHostile(e) && !(e instanceof Player) && e.isAlive());` / `for (LivingEntity target : nearby) {` / `target.addEffect(new MobEffectInstance(ModEffects.PANDAMAN_TAUNT,` |
| 17 | `item/chip/AdrenalineChipItem.java:96` | 肾上腺素闪避判定 | ✅ `player` / `attacker` | `if (!(event.getSource().getEntity() instanceof net.minecraft.world.entity.LivingEntity attacker)) return;` / `if (!HostileTargets.isHostile(attacker)) return;` / `if (tryDodge()) {` / `DiceCombatEvents.applyDodgeCancel(event);` / `MosesSignItem.onDodgeCounter(player, attacker);` |
| 18 | `item/chip/CursedSwordChipItem.java:122` | 击杀累计 | ✅ `killer` / `target` | `LivingEntity target = event.getEntity();` / `if (target.level().isClientSide()) return;` / `if (!HostileTargets.isHostile(target) \|\| target.getMaxHealth() < 20) return;` / `if (!(event.getSource().getEntity() instanceof Player killer)) return;` / `CursedSwordChipItem.onKill(killer);` |
| 19 | `item/chip/ElectricSwordChipItem.java:79` | 击杀回充 | ✅ `killer` | `if (event.isCanceled()) return;` / `if (event.getEntity().level().isClientSide()) return;` / `if (!HostileTargets.isHostile(event.getEntity())) return;` / `if (!(event.getSource().getEntity() instanceof Player killer)) return;` / `onHostileKilled(killer);` |
| 20 | `item/chip/FanBigChipItem.java:46` | 大扇上标记 | ✅ `player` | `AABB aabb = player.getBoundingBox().inflate(...HAND_FAN_BIG_RANGE);` / `List<LivingEntity> nearby = player.level().getEntitiesOfClass(LivingEntity.class, aabb,` / `e -> HostileTargets.isHostile(e) && e.isAlive());` / `for (LivingEntity entity : nearby) {` / `MarkManager.apply(entity);` |
| 21 | `item/chip/FanSmallChipItem.java:33` | 小扇上标记 | ✅ `player` | `AABB aabb = player.getBoundingBox().inflate(RANGE);` / `for (LivingEntity entity : player.level().getEntitiesOfClass(LivingEntity.class, aabb,` / `e -> HostileTargets.isHostile(e) && e.isAlive())) {` / `MarkManager.apply(entity);` / `}` |
| 22 | `item/chip/FlashlightChipItem.java:53` | `onAttack(player, target)` | ✅ `player` / `target` | `if (player == null \|\| target == null) return;` / `if (player.level().isClientSide()) return;` / `if (!HostileTargets.isHostile(target)) return;` / `if (!isEquipped(player)) return;` / `String uuid = target.getUUID().toString();` |
| 23 | `item/chip/RailgunChipItem.java:103` | `onAttack(player, target, dmg)` | ✅ `player` / `target` | `if (!isEquipped(player)) return null;` / `if (!HostileTargets.isHostile(target)) return null;` / `if (!(player.level() instanceof ServerLevel level)) return null;` / `if (isOnCooldown(player)) return null;` / `if (ChargeManager.getStacks(player) < CHARGE_REQUIRED) return null;` |
| 24 | `item/chip/RailgunChipItem.java:145` | `executeStrike(level,center,cause,damage)` | ⚠️ `cause`(ServerPlayer,可能 null) | `AABB aabb = new AABB(center, center).inflate(AOE_RADIUS);` / `List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, aabb,` / `e -> HostileTargets.isHostile(e) && e.isAlive());` / `if (victims.isEmpty()) return false;` / `if (cause != null) {` |
| 25 | `item/chip/SatelliteChipItem.java:102` | 轨道炮击杀给牌 | ✅ `killer` / `target` | `LivingEntity target = event.getEntity();` / `if (target.level().isClientSide()) return;` / `if (!HostileTargets.isHostile(target)) return;` / `if (!(event.getSource().getEntity() instanceof Player killer)) return;` / `if (!killer.hasEffect(ModEffects.ORBITAL_STRIKE)) return;` |
| 26 | `item/chip/SmartWatchChipItem.java:51` | 击杀累计 | ✅ `killer` | `if (event.isCanceled()) return;` / `if (event.getEntity().level().isClientSide()) return;` / `if (!HostileTargets.isHostile(event.getEntity())) return;` / `if (!(event.getSource().getEntity() instanceof Player killer)) return;` / `onHostileKilled(killer);` |
| 27 | `item/chip/JasmineSignItem`、`FannySignItem`、`ParunanSignItem`、`RinSignItem`、`FenSignItem`、`LuluSignItem`(被动)等 | **无命中** | — | 这些立牌不直接调用 `isHostile` |

### ④-B `forge-1.20.1`(27 处,与 A 一一对应,仅行号与事件类型不同)

| # | 文件:行 | 相对 A 的差异 |
|---|---|---|
| 1 | `combat/SpellDamageRegistry.java:249` | 行号相同 |
| 2 | `combat/SpellDamageRegistry.java:289` | 行号相同 |
| 3 | `combat/SpellDamageRegistry.java:370` | 行号相同 |
| 4 | `combat/DiceCombatModifiers.java:357` | **+3**(A:354) |
| 5 | `combat/DiceCombatEvents.java:317` | **−3**(A:320) |
| 6 | `combat/DiceCombatEvents.java:632` | **−3**(A:635) |
| 7 | `combat/DiceCombatEvents.java:1005` | **−7**(A:1012) |
| 8 | `combat/DiceCombatEvents.java:1128` | **+10**(A:1118;B 处事件为 `LivingDamageEvent`,A 为 `LivingDamageEvent.Pre`) |
| 9 | `damage/RailgunBolts.java:58` | 行号相同 |
| 10 | `damage/RailgunBolts.java:67–69` | 行号相同 |
| 11 | `item/sign/BonnieSignItem.java:117` | **−1** |
| 12 | `item/sign/LuluSignItem.java:76` | 行号相同 |
| 13 | `item/sign/NancyLuSignItem.java:216` | **+2** |
| 14 | `item/sign/NancyLuSignItem.java:257` | **+2** |
| 15 | `item/sign/NancyLuSignItem.java:270` | **+2** |
| 16 | `item/sign/PandamanSignItem.java:106` | **+1** |
| 17 | `item/chip/AdrenalineChipItem.java:99` | **+3** |
| 18 | `item/chip/CursedSwordChipItem.java:120` | **−2** |
| 19 | `item/chip/ElectricSwordChipItem.java:79` | 行号相同(文件 1–83 两版相同) |
| 20 | `item/chip/FanBigChipItem.java:47` | **+1** |
| 21 | `item/chip/FanSmallChipItem.java:34` | **+1** |
| 22 | `item/chip/FlashlightChipItem.java:53` | 行号相同 |
| 23 | `item/chip/RailgunChipItem.java:103` | 行号相同 |
| 24 | `item/chip/RailgunChipItem.java:145` | 行号相同 |
| 25 | `item/chip/SatelliteChipItem.java:103` | **+1** |
| 26 | `item/chip/SmartWatchChipItem.java:51` | 行号相同 |
| 27 | — | 同上,无命中 |

**结论(B 侧)**:`isHostile` 调用点**结构上与 A 完全一致**,只有行号漂移与 `ModEffects.X` → `ModEffects.X.get()`、`CuriosApi` → `CuriosCompat`、`LivingDamageEvent.Pre` → `LivingDamageEvent` 三类机械差异。**没有任何一个调用点在语义上缺少"视者"**;唯一真正无玩家上下文的是 `damage/RailgunBolts.java:67–69` 的单参重载(它把 bolt 传 `null`)。

## ⑤ 队伍判定工具:可用 API 与行号

**`event/EventTargetCollector.java`(A/B 全文 1–149 逐字节相同)**:

| API | 行号 | 签名 / 语义 |
|---|---|---|
| `collectTeamPlayers(Player triggerer)` | `:24` | 返回"友方玩家列表"。**未加入任何队伍时返回全服在线玩家(排除自己)** |
| `hasAnyTeam(Player triggerer)` | `:52` | `true` = 已加入 MC 原生队 / FTB Teams / OPAC 之一 |
| `collectMcTeamPlayers(...)` | `:59`(private) | `sp.getTeam() == triggerer.getTeam()` |
| `findFtbTeam(Player)` | `:71`(private) | 反射 `FTBTeamsAPI`;未装则 `null` |
| `collectFtbTeamPlayers(...)` | `:87`(private) | `getOnlineMembers` / `getMembers` |
| `findOpacParty(Player)` | `:117`(private) | 反射 `OpenPartiesAndClaimsAPI` |
| `collectOpacPartyPlayers(...)` | `:127`(private) | `getPartyMembers` |

**⚠️ 现成 API 缺一个"成对判定"入口**:`collectTeamPlayers` 是"取队友列表",不是"A 与 B 是否同队"。仓内已有的**成对**先例(可直接复用其口径):

- `item/sign/PandamanSignItem.java:169–170`(B:`:170–171`)——`self == other || self.getTeam() == null || other.getTeam() == null || self.getTeam() == other.getTeam()`(**注意:双方都无队伍时返回 true = 视为友方**,与本项需求的"非同队伍"口径**相反**)
- `item/chip/FriendshipBadgeChipItem.java:71`(B:`:72`)——`healer.getTeam() == null || target.getTeam() == null || healer.getTeam() == target.getTeam()`
- `combat/DiceCombatEvents.java:1010`(B:`:1003`)——**与本项需求口径一致**的反向写法:`other.getTeam() == null || other.getTeam() != player.getTeam()`(无队伍 ⇒ 视为可敌对)

**推荐**:在 `EventTargetCollector` 新增 `public static boolean isSameTeam(Player a, Player b)` = `a.getTeam() != null && a.getTeam() == b.getTeam()`(再并联 `findFtbTeam`/`findOpacParty` 的同一实例判定,两者已是 private 静态方法,新增包内方法即可复用,不必改可见性以外的东西)。

## ⑥ 现有"按玩家存一组 UUID/集合"的持久化与内存先例

### ⑥-1 字符串型(最贴近本需求)——`FLASHLIGHT_GRANTED_TARGETS`

A `component/ModAttachments.java:193–205`:

```java
    // 手电筒-强光筹码:已发放过星光的敌对目标 UUID(逗号分隔;同一目标仅 +1 层星光)
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<String>> FLASHLIGHT_GRANTED_TARGETS =
            ATTACHMENTS.register("flashlight_granted_targets", () -> AttachmentType.builder(() -> "")
                    .serialize(Codec.STRING)
                    .build());

    public static String getFlashlightGrantedTargets(net.minecraft.world.entity.player.Player player) {
        return player.getData(FLASHLIGHT_GRANTED_TARGETS.get());
    }

    public static void setFlashlightGrantedTargets(net.minecraft.world.entity.player.Player player, String value) {
        player.setData(FLASHLIGHT_GRANTED_TARGETS.get(), value == null ? "" : value);
    }
```

B `component/ModAttachments.java:165`:

```java
    public static final AttachedDataKey<String> FLASHLIGHT_GRANTED_TARGETS =
            register(AttachedDataKey.builder("flashlight_granted_targets", Codec.STRING, () -> "").build());
```

读写辅助(两侧同构)先例 A `item/chip/FlashlightChipItem.java:72–80`:

```java
    private static List<String> readGrantedTargets(Player player) {
        String raw = ModAttachments.getFlashlightGrantedTargets(player);
        if (raw == null || raw.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(raw.split(",")));
    }

    private static void writeGrantedTargets(Player player, List<String> granted) {
        ModAttachments.setFlashlightGrantedTargets(player, String.join(",", granted));
    }
```

### ⑥-2 `*_source` 型(`Optional<UUID>`,存"来源玩家")

| 键 | A 行号 | B 行号 | 类型 | 载体实体 |
|---|---|---|---|---|
| `WEAK_MARK_SOURCE` | `:314–317` | `:286` | `Optional<UUID>` | `LivingEntity`(目标) |
| `UNDERCOVER_SOURCE` | `:328–331` | `:299` | `Optional<UUID>` | `LivingEntity`(目标) |
| `PANDAMAN_TAUNT_SOURCE` | `:824` | `:732` | `Optional<UUID>` | `LivingEntity`(被嘲讽者) |

A 侧写法(`:314–317`):

```java
    // 虚弱印记来源:施加该印记的玩家 UUID(仅该玩家获得击杀后奖励,印记结束/目标死亡后清除)
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Optional<UUID>>> WEAK_MARK_SOURCE =
            ATTACHMENTS.register("weak_mark_source", () -> AttachmentType.<Optional<UUID>>builder(Optional::empty)
                    .serialize(UUIDUtil.CODEC.optionalFieldOf("id").codec())
                    .build());
```

### ⑥-3 其余 String 型附件(全部在玩家上,全部**不参与死亡复制**)

| 键 | A 行号 | B 行号 |
|---|---|---|
| `MAGIC_TOME_LAST_CARD` | `:153` | `:136` |
| `KOMACHI_LAST_CARD` | `:166` | `:144` |
| `RIN_GIFT_SIGNATURE` | `:286` | `:262` |
| `MAGIC_QUIVER_FIRST_CARD` | `:507` | `:463` |

### ⑥-4 `Set` 型附件:**不存在**。全仓附件类型只有 `Integer / Long / Boolean / Float / String / Optional<UUID> / Map<String,TimerEntry>`。最接近"集合"的两个先例是 ⑥-1(逗号分隔 String)与 `EFFECT_TIMER_ENDS`(A `:853–861`,`Map<String,TimerEntry>` + `Codec.unboundedMap(Codec.STRING, TimerEntry.CODEC)`)。

### ⑥-5 静态 `Map<UUID,…>` 先例(内存态,不持久化)

`item/chip/ElectricSwordChipItem.java:35`(A/B 同行号):

```java
    private static final Map<UUID, Integer> KILL_COUNTS = new HashMap<>();
```

清理挂点在同文件 `:53–58`(卸下时)与 `:66–72`(计数逻辑):

```java
    @Override
    protected void onChipUnequip(Player player, ItemStack stack) {
        if (player != null) {
            KILL_COUNTS.remove(player.getUUID());
        }
    }
```

同类:`item/sign/JasmineSignItem.java:29–30`(B:`30–31`)、`item/chip/EnergyRecyclerChipItem.java:42–43`(B:`41–42`)、`event/WaystoneWarpCompat.java:34`、`item/ChargeManager.java:21`、`component/DeathPreservedBonuses.java:33`。

**⚠️ `KILL_COUNTS` 这一类静态表有两个已知局限**(仓内 `DeathPreservedBonuses.java:20–23` 的注释已自我声明):**只在同一 JVM 会话内有效**;重启/换世界即丢;且**无任何登录清理**,单人"退出到标题"不会卸载类。

### ⑥-6 死亡/重生清理挂点

- A `event/PlayerLifecycleHandler.java:115` `onPlayerDeathClearEffects(LivingDeathEvent)`(整段 `:115–179`)
- A `:184–192` `onPlayerCloneRestoreDeathPreserved`(`priority = LOWEST`,`event.isWasDeath()`)
- A `:211–221` `onPlayerRespawnMedkit`(`PlayerRespawnEvent` 兜底)
- B `event/PlayerLifecycleHandler.java:112`(整段 `:112–175`)、`:180–187`(Clone,LOWEST)、B 亦有 Respawn 兜底(见 §3 逐字原文)
- `component/DeathPreservedBonuses.java`(A 1–60 / B 1–60):`preserveOnDeath(Player)`(A:`:39`)、`restoreAfterDeath(Player)`(A:`:48`),`Map<UUID,int[]> PRESERVED`(A:`:33`)

## ⑦ 攻击行为的最佳记录点(候选 + 利弊)

| 候选 | A 行号 | B 行号 | 优点 | 缺点 |
|---|---|---|---|---|
| **`LivingDamageEvent.Pre` / `LivingHurtEvent` 里 `source.getEntity() instanceof Player` 且 `target instanceof Player`** | `combat/DiceCombatEvents.java:185`(`onLivingDamagePre`),`source.getDirectEntity() instanceof Player player` 在 `:201`,`target` 在 `:189` | `:181`,`:197`,`:185` | **覆盖全部伤害来源**(近战/远程/投掷物/魔法/爆炸),`source.getEntity()` 对箭矢是射手;`getEntity()` 就是受击玩家;单点接入即可覆盖全局规则 | 该 handler 内部 `:200` 有 `if (aoeProcessing \|\| counterDepth > 0) return;`、`:212` 有 `if (!isMeleeWeaponAttack(player)) return;` —— **若把记录逻辑插在函数尾部会漏掉远程与 AOE**;且会与骰战管线耦合。**推荐:另开一个独立 handler**(同事件、`priority = HIGHEST`),不复用 `DiceCombatEvents` 的方法体 |
| `AttackEntityEvent` | `item/sign/NancyLuSignItem.java:250`(`@SubscribeEvent` 方法),`:254` `event.getTarget()` | `:252`,`:256` | 语义最直白(玩家左键实体的瞬间),`getEntity()` = 攻击者、`getTarget()` = 目标;早于伤害结算 | **只覆盖直接近战左键**:弓/弩/三叉戟投掷/魔法/爆炸/宠物与陷阱伤害**全部不触发**;且 `AttackEntityEvent` 在目标为任意 `Entity` 时都发(**含非玩家目标**,需自行过滤) |
| `PlayerEvent`/`EntityEvent` 的 `getLastHurtByMob` | — | — | 原版自带 | `grep` 全仓 **0 命中**(`getLastHurtByMob`/`LastHurtByPlayer`/`lastAttacker` 均无),无先例;且它存的是 `Mob`,**不含玩家**,不适用 |

**结论**:记录点选 **`LivingDamageEvent.Pre`(A)/ `LivingHurtEvent`(B)** 的**独立 HIGHEST 优先级 handler**,条件 `source.getEntity() instanceof Player attacker && event.getEntity() instanceof Player victim && attacker != victim`。需要在 A 侧用 `.Pre`(1.21 的新语义);B 侧用 `LivingHurtEvent`(此时护甲减免已结算,但"是否被某玩家打过"的记录不关心数值,故 `LivingHurtEvent` 足够;若要"被完全格挡/免疫也算被打过"则应改用 `LivingAttackEvent`)。

## ⑧ 推荐存储方案 + 最小 diff

### 方案选型

| 候选 | 持久性 | 并发 | 死亡清除 | 客户端同步 | 结论 |
|---|---|---|---|---|---|
| **新附件 `hostile_player_sources`(String,逗号分隔 UUID)** ✅推荐 | 随玩家 NBT 持久化(A:`serialize(Codec.STRING)`,B:`AttachedDataKey`) | 服务端单线程;`set` 整体覆盖即可 | 不加 `.copyOnDeath()` ⇒ 新实体回默认 `""`;再在死亡 handler 显式清一次(权威定义) | **不需要**(溅射判定在 `:623` 已 gate 在 `!isClientSide()` 内;无客户端预检需求) | **首选**:与 `FLASHLIGHT_GRANTED_TARGETS` 完全同构,零新概念;`Set` 语义用 `split(",")`/`String.join` 复刻 ⑥-1 的两个辅助方法 |
| 静态 `Map<UUID,Set<UUID>>` | ❌ 重启即丢 | 需 `ConcurrentHashMap` | 手动 | 不适用 | 只在"纯本会话"语义可接受时才用;本规则的"在你死亡前"跨会话语义 ⇒ **不可用** |
| `Map<String,Long>`(带时间戳) | ✅ | — | 手动 | — | 若将来要"敌对立场有有效期",再升级为 `Codec.unboundedMap(Codec.STRING, Codec.LONG)`(先例 `EFFECT_TIMER_ENDS`)。当前规则**无有效期**,不必要 |

**服务端单线程**:NeoForge/Forge 的主逻辑都在 server thread,`AttachmentType` / `AttachedDataKey` 的读写无需额外同步;唯一注意点是**不要在渲染/网络线程读**。

### 最小 diff — 步骤 1/4:新增附件键

**A 侧**(`neoforge-1.21.1/.../component/ModAttachments.java`,插在 `:197` 之后即可,紧随 `FLASHLIGHT_GRANTED_TARGETS` 块):

```java
    // 敌对玩家规则(全局):"在本玩家死亡前主动攻击过本玩家"的攻击者 UUID(逗号分隔)。
    // 口径 = 非同队伍 + 曾主动攻击;本玩家死亡时清除(不 copyOnDeath,新实体回默认空串)。
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<String>> HOSTILE_PLAYER_SOURCES =
            ATTACHMENTS.register("hostile_player_sources", () -> AttachmentType.builder(() -> "")
                    .serialize(Codec.STRING)
                    .build());

    public static String getHostilePlayerSources(net.minecraft.world.entity.player.Player player) {
        return player.getData(HOSTILE_PLAYER_SOURCES.get());
    }

    public static void setHostilePlayerSources(net.minecraft.world.entity.player.Player player, String value) {
        player.setData(HOSTILE_PLAYER_SOURCES.get(), value == null ? "" : value);
    }
```

> 注册位置说明:1.21.1 走 `ATTACHMENTS`(`:20–21` 的 `DeferredRegister`)——**新键只要用 `ATTACHMENTS.register(...)` 声明即自动注册**,无需改 `AstralDiceMod`(`:42` 已有 `ModAttachments.ATTACHMENTS.register(modEventBus)`)。**不要**额外 `.sync(...)`:本键只在服务端判定。

**B 侧**(`forge-1.20.1/.../component/ModAttachments.java`,插在 `:165` 之后):

```java
    // 敌对玩家规则(全局):"在本玩家死亡前主动攻击过本玩家"的攻击者 UUID(逗号分隔)。
    // 口径 = 非同队伍 + 曾主动攻击;本玩家死亡时清除(不加入 SYNCED_KEYS)。
    public static final AttachedDataKey<String> HOSTILE_PLAYER_SOURCES =
            register(AttachedDataKey.builder("hostile_player_sources", Codec.STRING, () -> "").build());

    public static String getHostilePlayerSources(net.minecraft.world.entity.player.Player player) {
        return HOSTILE_PLAYER_SOURCES.get(player);
    }

    public static void setHostilePlayerSources(net.minecraft.world.entity.player.Player player, String value) {
        HOSTILE_PLAYER_SOURCES.set(player, value == null ? "" : value);
    }
```

> **B 侧注册位置**:1.20.1 **没有** DeferredRegister;`register(...)` 是空壳(`:27–29`),`AttachedDataKey` 由静态字段初始化即完成注册。**若需要客户端同步**,还必须把新键**追加进 `syncedKeys()`(`:820–852`)**——本方案**不需要**,故不动该处。

### 最小 diff — 步骤 2/4:`HostileTargets` 新增双参重载(A/B **逐字节相同**,两侧同改)

```java
    /** 该实体是否为「敌对目标」(敌对生物,或已被激怒的中立生物)。 */
    public static boolean isHostile(Entity entity) {
        if (entity == null) return false;
        if (entity instanceof Enemy) return true;
        return entity instanceof NeutralMob neutral && neutral.isAngry();
    }

    /**
     * 新全局规则(2026-xx 用户裁决):viewer 是否视 entity 为「敌对目标」。
     *
     * <p>口径 = 原口径(敌对生物∪被激怒中立生物) ∪
     * 「entity 是玩家 ∧ 非同队伍 ∧ 在 viewer 死亡前主动攻击过 viewer」。
     * viewer 死亡时该敌对立场清除(见 PlayerLifecycleHandler#onPlayerDeathClearEffects)。
     *
     * <p>注意:本重载只应用于**确实需要"敌对玩家"参与**的效果(如大当家溅射);
     * 其余"只对敌对生物"的效果(嘲讽/上标记/闪避等)继续调用单参版本,避免玩家被误伤。
     */
    public static boolean isHostile(Player viewer, Entity entity) {
        if (isHostile(entity)) return true;
        if (viewer == null || !(entity instanceof Player other) || other == viewer) return false;
        if (EventTargetCollector.isSameTeam(viewer, other)) return false;
        return HostilePlayerSources.hasAttacked(viewer, other);
    }
```

(需要 `import net.minecraft.world.entity.player.Player;` 与 `import com.merlinkitsune.astral_dice.event.EventTargetCollector;`;
`HostilePlayerSources` = 本方案新增的小工具类,见步骤 3。)

### 最小 diff — 步骤 3/4:新增读写工具类(A/B 同文,放在 `combat/` 包)

```java
package com.merlinkitsune.astral_dice.combat;

import com.merlinkitsune.astral_dice.component.ModAttachments;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** 「敌对玩家立场」的唯一读写入口(结构完全复刻 FlashlightChipItem 的 granted-targets 先例)。 */
public final class HostilePlayerSources {
    private HostilePlayerSources() {
    }

    /** 记录"attacker 主动攻击了 victim"(victim 侧保存 attacker 的 UUID)。 */
    public static void recordAttack(Player attacker, Player victim) {
        if (attacker == null || victim == null || attacker == victim) return;
        if (victim.level().isClientSide()) return;
        String id = attacker.getUUID().toString();
        List<String> list = read(victim);
        if (list.contains(id)) return;
        list.add(id);
        write(victim, list);
    }

    /** victim 是否视 attacker 为敌对玩家(仅会话/持久集合判定,不含队伍过滤)。 */
    public static boolean hasAttacked(Player victim, Player attacker) {
        if (victim == null || attacker == null) return false;
        return read(victim).contains(attacker.getUUID().toString());
    }

    /** 玩家死亡时清除其全部敌对立场(victim 本人死亡 ⇒ 立场归零)。 */
    public static void clearOnDeath(Player victim) {
        if (victim == null) return;
        ModAttachments.setHostilePlayerSources(victim, "");
    }

    private static List<String> read(Player player) {
        String raw = ModAttachments.getHostilePlayerSources(player);
        if (raw == null || raw.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(raw.split(",")));
    }

    private static void write(Player player, List<String> list) {
        ModAttachments.setHostilePlayerSources(player, String.join(",", list));
    }
}
```

### 最小 diff — 步骤 4/4:接入记录点、死亡清除、溅射筛选

**(a) 新增独立 HIGHEST handler(A 版,`combat/DiceCombatEvents.java` 或新文件 `combat/HostilePlayerListener.java`)**:

```java
    // A(1.21.1):LivingDamageEvent.Pre,HIGHEST,独立于骰战管线
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerAttackedByPlayer(LivingDamageEvent.Pre event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getSource().getEntity() instanceof Player attacker)) return;
        HostilePlayerSources.recordAttack(attacker, victim);
    }
```

```java
    // B(1.20.1):同一事件族,类型为 LivingHurtEvent
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerAttackedByPlayer(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getSource().getEntity() instanceof Player attacker)) return;
        HostilePlayerSources.recordAttack(attacker, victim);
    }
```

**(b) 死亡清除 —— 插在 A `event/PlayerLifecycleHandler.java:124` 之后 / B `:121` 之后**(与 `DeathPreservedBonuses.preserveOnDeath` 同一处,`isCanceled()` 闸门之内):

```java
        // 敌对玩家规则:本玩家死亡 ⇒ 清除"谁在我死前攻击过我"的全部立场
        com.merlinkitsune.astral_dice.combat.HostilePlayerSources.clearOnDeath(player);
```

**(c) 溅射筛选切到双参重载**:

A `combat/DiceCombatEvents.java:633–635` → 替换为:

```java
                var splashVictims = target.level().getEntitiesOfClass(
                        net.minecraft.world.entity.LivingEntity.class, splashBox,
                        e -> HostileTargets.isHostile(player, e) && e.isAlive());
```

B `combat/DiceCombatEvents.java:630–632` → 替换为:

```java
                var splashVictims = target.level().getEntitiesOfClass(
                        net.minecraft.world.entity.LivingEntity.class, splashBox,
                        e -> HostileTargets.isHostile(player, e) && e.isAlive());
```

**(d) 「全局规则」的落地范围选项**:若严格按"全局规则"字面,还需把这些调用点一并切到双参重载(逐个评估副作用):

| 调用点(A:B) | 切过去的后果 | 建议 |
|---|---|---|
| 溅射 `DiceCombatEvents` 635:632 | ✅ 本次需求 | **切** |
| 定向爆破 AOE `SpellDamageRegistry` 249:249 | 效果牌 AOE 开始波及敌对玩家 | 需用户裁决 |
| 电击手套 AOE `SpellDamageRegistry` 370:370 | 同上 | 需用户裁决 |
| 贯穿之铳 `SpellDamageRegistry` 289:289 | 对敌对玩家额外加伤 | 需用户裁决 |
| 肉弹战车嘲讽 `PandamanSignItem` 105:106 | ❌ **禁止**:该处已显式 `!(e instanceof Player)`,且 AGENTS.md 明确"嘲讽只对敌对生物生效,永不会施加给玩家"。切过去必须**保留**该排除 | **不切**(保留 `!(e instanceof Player)`) |
| 大/小扇上标记 `FanBig/FanSmall` | 会给敌对玩家叠标记 | 需用户裁决 |
| 骇客被动"周围有敌对" `NancyLuSignItem` 214:216 | 敌对玩家在场也按"防御型被动" | 需用户裁决 |
| `isBlessingTarget` 1012:1005 | 已**独立**处理玩家(队伍判定),不要动 | **不切** |
| 雷电白名单 `RailgunBolts` 58:58 | 雷击会打敌对玩家(范围 AOE) | 需用户裁决 |
| 各类**击杀累计/触发**类(chip 的 `LivingDeathEvent`、Bonnie/Satellite/SmartWatch/CursedSword/ElectricSword) | 击杀敌对玩家也计入 | 需用户裁决 |

## ⑨ 单侧差异与风险点(第 1 项)

**单侧差异**
1. **无源码级单侧差异**:`HostileTargets`、`EventTargetCollector` 两版本逐字节相同;`isHostile` 调用点结构一一对应。
2. **机制差异(必须分平台写)**:A 用 `DeferredRegister<AttachmentType<?>>` + `.serialize(Codec.STRING)`;B 用静态 `AttachedDataKey.builder(name, codec, default).build()`(无注册器、无 `.serialize`)。**B 侧若误加 `.sync()` 还必须同时改 `syncedKeys()`(`:820–852`),否则客户端读缓存为默认值。**
3. **事件类型差异**:A = `LivingDamageEvent.Pre`,B = `LivingHurtEvent`;`EventPriority` 导入包不同(A `net.neoforged.bus.api.EventPriority`,B `net.minecraftforge.eventbus.api.EventPriority`)。
4. **`ModEffects.X` vs `ModEffects.X.get()`** 等机械差异不影响本项。

**最可能破坏现有行为的风险点**
1. **溅射开始造成玩家伤害 = 唯一的高危行为变化**:`Player` 当前恒不敌对,新规则让 `isHostile(player, 敌对玩家)` 为真 ⇒ **主目标若恰是"打过你的玩家",溅射会把它自己也算进去**(`:648` 的 `isMainTarget` 分支本来就会对主目标清零无敌帧)。若玩家 A 攻击玩家 B 触发满层赐福,B 此前打过 A ⇒ **B 吃到 88% 真伤**;A 若未打过 B 则不受影响 —— 这是**非对称**的,务必确认这就是需求本意。
2. **`aoeProcessing` 递归闸门**:溅射伤害在 `:625` 置位、`:671` 复位,新受害者(玩家)受伤时会再进 `onLivingDamagePre`;`:200` 已用 `aoeProcessing` 挡住骰战重入 ⇒ 不会二次引爆。但**新增的"记录攻击"handler 若放在 `:200` 之前**(按本方案 HIGHEST 独立 handler),溅射真伤(其 `source.getEntity()` = `player`,由 `ModDamageTypes.trueDamage(level, player)` 构造)**会被记成"player 主动攻击了该玩家"** —— 即 **AOE 溅射本身会建立/刷新敌对立场**。若这不希望发生,记录 handler 需要加 `if (DiceCombatEvents.aoeProcessing) return;`(A `aoeProcessing` 是 `public static` 字段,B 同)。**这是本项最容易漏的语义副作用。**
3. **队伍口径写反**:`PandamanSignItem:169–170` 的 `isAlly` 在"双方都无队伍"时返回 **true**,而本需求要求"无队伍 ⇒ 不构成同队 ⇒ 可敌对"。若误复用该方法,新规则在**无队伍的服务器上永久失效**。
4. **`RailgunBolts:67–69` 单参重载**没有玩家上下文;若有人把双参重载误接到这里会得到 `viewer == null` ⇒ 行为不变(静默无效果),不算崩溃但规则不生效。
5. **持久化污染**:`String` 附件随玩家 NBT 永久保存,若"攻击者 UUID"从不清理(除受害者死亡外),长周期服务器上会无限增长。`FLASHLIGHT_GRANTED_TARGETS` 用 `MAX_TRACKED_TARGETS` 硬上限做保护(`FlashlightChipItem:60`),本规则**没有上限**,建议加同款上限或"仅保留最近 N 个"策略 —— 否则这是一个**新的无界增长点**。
6. **`isBlessingTarget` 的玩家分支与本规则无关**(它按 `getTeam()` 直接判"非队友即可触发"),不要为了"统一"去改它,否则骰神赐福对玩家的触发条件会连带变化。

---

# 2. S4-C2 星光兑换口径(「若无实际扣除则阻止兑换」)

## ① 结论(一句话)

`starlightToStarCoins` **已经用"实际扣除量"反算产出**(`:34–35` `spent` → `gained = spent / 2`),**但"无实际扣除"只被判成"返回 0 / 不发币"、不会阻止调用方**;唯一调用点 `ParunanSignItem.handleUse:43` **完全丢弃返回值**,所以当玩家星光全部来自银行卡基础值(`getBasePoints` > 0)时会出现「**点一次套现:星光没少、星币没出、主动技能照常生效并吃冷却**」。最小改法 = **把可自由支配星光 `get(player) - getBasePoints(player)` 作为兑换上限**,并让 `starlightToStarCoins` 在 `spend` 为 0 时返回 **`-1`** 作为"未发生兑换"的哨兵,调用点据此 `fail`。

## ② 触点清单 / 文件路径:行号

| 内容 | A(neoforge-1.21.1) | B(forge-1.20.1) |
|---|---|---|
| `StarLightManager.get` | `item/StarLightManager.java:23–25` | `:27–29` |
| `getCap` | `:28–30` | `:32–34` |
| `getBasePoints` | `:38–50` | `:42–54` |
| `set` | `:52–56` | `:56–60` |
| `add` | `:59–63` | `:63–67` |
| `spend` | `:66–71` | `:70–75` |
| `starlightToStarCoins` | `resource/ResourceConversion.java:28–45` | `:28–45`(**逐字节相同**) |
| **唯一调用点** | `item/sign/ParunanSignItem.java:43` | `item/sign/ParunanSignItem.java:43` |
| `MAX_STARLIGHT` | `component/GameplayConstants.java:15`(=32) | `:15`(=32) |

## ③ 逐字原文

### ③-1 `item/StarLightManager.java` — A(1–72)全文

```java
package com.merlinkitsune.astral_dice.item;

import com.merlinkitsune.astral_dice.component.GameplayConstants;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import net.minecraft.world.entity.player.Player;
import top.theillusivec4.curios.api.CuriosApi;
import com.merlinkitsune.astral_dice.item.chip.BankCardChipItem;

/**
 * "星光"管理器(玩家级共享资源,与具体饰品解耦)。
 * 数据存储于玩家 attachment(PLAYER_STARLIGHT),本类统一提供获取/增加/设置与上限逻辑。
 *
 * <p>星光为固定点数(不随时间衰减,无计数器),只有增加与减少。
 * 与治愈流派一致,星光具有<b>基础值(下限)</b>:
 * <ul>
 *   <li>基础值默认 0,可由未来"固定增加星光"的筹码在装备期间提供(预留 {@link #getBasePoints} 接入点);</li>
 *   <li>星光被消耗并低于基础值时,自动补充回基础值。</li>
 * </ul>
 * 获取来源:经商立牌被动/赐福加成、手电筒筹码攻击加成、八面骰累计、看板立牌兑换等。
 */
public final class StarLightManager {
    /** 当前星光点数 */
    public static int get(Player player) {
        return ModAttachments.getStarlight(player);
    }

    /** 星光点数上限(配置控制) */
    public static int getCap() {
        return GameplayConstants.MAX_STARLIGHT;
    }

    /**
     * 星光基础值(下限),默认 0。
     * 由"固定增加星光"的筹码在装备期间提供常驻基础值(卸下自动回落,与治愈基础点模式一致):
     * - 银行卡-余额少:+4;
     * - 银行卡-余额多:+7。
     */
    public static int getBasePoints(Player player) {
        var curios = CuriosApi.getCuriosInventory(player);
        if (curios.isEmpty()) return 0;
        var inventory = curios.get();
        int base = 0;
        if (inventory.findFirstCurio(s -> s.is(ModItems.BANK_CARD_LOW.get())).isPresent()) {
            base += BankCardChipItem.BASE_LOW;
        }
        if (inventory.findFirstCurio(s -> s.is(ModItems.BANK_CARD_HIGH.get())).isPresent()) {
            base += BankCardChipItem.BASE_HIGH;
        }
        return base;
    }

    public static void set(Player player, int value) {
        int base = getBasePoints(player);
        // 不低于基础值(下限),不高于上限
        ModAttachments.setStarlight(player, Math.max(base, Math.min(value, getCap())));
    }

    // 增加星光(自动限制在上限内),返回增加后的值
    public static int add(Player player, int amount) {
        int next = Math.min(get(player) + amount, getCap());
        set(player, next);
        return next;
    }

    // 消耗星光(不可为负),返回实际消耗的量;消耗后若低于基础值则自动补充回基础值
    public static int spend(Player player, int amount) {
        int current = get(player);
        int spent = Math.min(current, amount);
        set(player, current - spent);
        return spent;
    }
}
```

### ③-2 `item/StarLightManager.java` — B(1–76)差异部分

B 与 A **仅差 4 行**:`:22–24` 多一个私有构造器、`:43` 用 `CuriosCompat.getCuriosInventory(player)`、`:6` 多 import `CuriosCompat`。

```java
public final class StarLightManager {
    private StarLightManager() {
    }

    /** 当前星光点数 */
    public static int get(Player player) {
        return ModAttachments.getStarlight(player);
    }

    /** 星光点数上限(配置控制) */
    public static int getCap() {
        return GameplayConstants.MAX_STARLIGHT;
    }

    /**
     * 星光基础值(下限),默认 0。
     * 由"固定增加星光"的筹码在装备期间提供常驻基础值(卸下自动回落,与治愈基础点模式一致):
     * - 银行卡-余额少:+4;
     * - 银行卡-余额多:+7。
     */
    public static int getBasePoints(Player player) {
        var curios = CuriosCompat.getCuriosInventory(player);
        if (curios.isEmpty()) return 0;
        var inventory = curios.get();
        int base = 0;
        if (inventory.findFirstCurio(s -> s.is(ModItems.BANK_CARD_LOW.get())).isPresent()) {
            base += BankCardChipItem.BASE_LOW;
        }
        if (inventory.findFirstCurio(s -> s.is(ModItems.BANK_CARD_HIGH.get())).isPresent()) {
            base += BankCardChipItem.BASE_HIGH;
        }
        return base;
    }

    public static void set(Player player, int value) {
        int base = getBasePoints(player);
        // 不低于基础值(下限),不高于上限
        ModAttachments.setStarlight(player, Math.max(base, Math.min(value, getCap())));
    }

    // 增加星光(自动限制在上限内),返回增加后的值
    public static int add(Player player, int amount) {
        int next = Math.min(get(player) + amount, getCap());
        set(player, next);
        return next;
    }

    // 消耗星光(不可为负),返回实际消耗的量;消耗后若低于基础值则自动补充回基础值
    public static int spend(Player player, int amount) {
        int current = get(player);
        int spent = Math.min(current, amount);
        set(player, current - spent);
        return spent;
    }
}
```

### ③-3 `resource/ResourceConversion.java` — A/B **逐字节相同**(1–53)

```java
package com.merlinkitsune.astral_dice.resource;

import com.merlinkitsune.astral_dice.item.chip.AtmChipItem;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.StarLightManager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 点数流派转化工具:集中管理各流派点数与物品/伤害之间的转换比例。
 * 筹码/立牌需要点数转化时统一调用本类,禁止散落硬编码比例。
 * 新增转化(如未来"反击"流派的转化)在此扩展。
 */
public final class ResourceConversion {
    // 星光→星币比例:2 点星光 = 1 个星币
    public static final int STARLIGHT_PER_COIN = 2;

    private ResourceConversion() {
    }

    /**
     * 星光 → 星币(按 2:1 比例)。
     *
     * @param player 玩家
     * @param amount 用于兑换的星光数;-1 = 消耗全部星光(余数保留);其他 = 最多消耗 amount 点
     * @return 实际获得的星币数
     */
    public static int starlightToStarCoins(Player player, int amount) {
        if (player.level().isClientSide()) return 0;
        int starlight = StarLightManager.get(player);
        int use = amount < 0 ? starlight : Math.min(amount, starlight);
        int coins = use / STARLIGHT_PER_COIN;
        if (coins <= 0) return 0;
        int spent = StarLightManager.spend(player, coins * STARLIGHT_PER_COIN);
        int gained = spent / STARLIGHT_PER_COIN;
        // ATM机筹码:使用星光兑换星币时,兑换量(星币产出)增加 40%
        // 百分比加成统一采用「下限为 1」策略:收益率截断后不足 1 时至少 +1(避免小额兑换加成为 0)
        if (AtmChipItem.isEquipped(player)) {
            gained += Math.max(1, (int) (gained * 0.4));
        }
        if (gained > 0) {
            giveItem(player, new ItemStack(ModItems.STAR_COIN.get(), gained));
        }
        return gained;
    }

    // 发放物品(背包满则掉落)
    public static void giveItem(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }
}
```

### ③-4 `starlightToStarCoins` 的**全部调用点**与返回值使用方式

`grep "starlightToStarCoins"` → **全仓仅 4 行命中**:2 行是定义本身(两版本各一),**真正调用点只有 1 个**:

| 调用点 | 行号(A/B 同) | 返回值是否被使用 | 上下文 |
|---|---|---|---|
| `item/sign/ParunanSignItem.java:43`(经商立牌「套现」) | `:43` | ❌ **完全丢弃**(语句表达式,无赋值、无 `if`) | 见下 |

A/B `item/sign/ParunanSignItem.java:32–58`(两版本**逐字节相同**):

```java
    protected InteractionResultHolder<ItemStack> handleUse(Level level, Player player, ItemStack stack) {
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }

        int starlight = StarLightManager.get(player);
        if (starlight <= 0) {
            return InteractionResultHolder.fail(stack);
        }

        // 每 2 点星光返还 1 个星币(转化比例集中管理,余数部分保留)
        com.merlinkitsune.astral_dice.resource.ResourceConversion.starlightToStarCoins(player, -1);

        // 主动技能:随机获得以下任一效果
        int choice = ThreadLocalRandom.current().nextInt(3);
        MobEffectInstance effect;
        if (choice == 0) {
            effect = new MobEffectInstance(MobEffects.SATURATION, 600, 0, false, true); // 饱和 30 秒
        } else if (choice == 1) {
            effect = new MobEffectInstance(MobEffects.LUCK, 6000, 0, false, true); // 幸运 5 分钟
        } else {
            effect = new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 18000, 0, false, true); // 村庄英雄 15 分钟
        }
        EffectTimerGuard.apply(player, effect);

        return InteractionResultHolder.success(stack);
    }
```

**其它"星光"相关消费点(不使用 `starlightToStarCoins`,仅 `StarLightManager.add/set/get`,列全供交叉核对)**:

| 位置(A / B) | 用法 |
|---|---|
| `item/sign/ParunanSignItem.java:28` / `:28` | 被动每 60 秒 `add(player, 1)` |
| `item/sign/ParunanSignItem.java:63` / `:63` | 赐福 `add(player, dicePoint * 2)` |
| `item/chip/AtmChipItem.java:33` / `:34` | 装备一次性 `add(player, 1)`;`isEquipped` 在 `:24`(A)/`:24`(B)被 `ResourceConversion:38` 调用 |
| `item/chip/BankCardUnlimitedChipItem.java:40` / `:41` | 装备一次性 `add(player, 3)` |
| `item/chip/BankCardChipItem.java:36` / `:36` | `set(player, get(player))`(仅触发下限修复) |
| `item/chip/StarCoinHammerChipItem.java:204` / `:205` | 装备一次性 `add(player, 5)` |
| `item/chip/FlashlightChipItem.java:63` / `:63` | 攻击敌对目标 `add(player, STARLIGHT_PER_TARGET)` |
| `combat/DiceCombatEvents.java:447–455` / `:444–452` | 八面骰累计 → `set(player, min(starlight, getCap()))` |
| `combat/DiceCombatModifiers.java:210` / `:213` | `ap += StarLightManager.get(ctx.attacker) / 4`(战斗加成,读值) |
| `event/ModTooltipHandler.java:654,734,834,845,856,865,935` / `:650,730,830,841,852,861,931` | tooltip 显示 `get(player)` / `getCap()` |

**`-1` 全清语义如何呈现**:`get(player)` 是**含基础值在内**的总值;`spend` 内部 `set` 会把结果**钳回 `getBasePoints`**,所以 `-1`(全清)的**实际效果是"清到基础值为止"**,余数(不足 2 的部分 + 全部基础值)保留 —— 与 `:42` 的注释"余数部分保留"一致。**没有任何 UI 呈现**:调用点丢弃返回值,tooltip 只显示当前 `get(player)`。

## ④ 最小 diff 方案

### 步骤 1:给 `StarLightManager` 增加"可自由支配星光"概念(A/B 同文,插在 `getBasePoints` 之后)

```java
    /**
     * 可自由支配的星光(兑换口径的上限):总数 - 基础值。
     * 基础值来自银行卡等"常驻下限",spend 会被 set() 钳回基础值,故这部分**无法真正扣除**。
     */
    public static int getFreePoints(Player player) {
        return Math.max(0, get(player) - getBasePoints(player));
    }
```

### 步骤 2:`ResourceConversion.starlightToStarCoins` 换上限 + 加"无实际扣除"哨兵(A/B 同文,替换 `:28–45` 整段)

```java
    /**
     * 星光 → 星币(按 2:1 比例)。
     *
     * @param player 玩家
     * @param amount 用于兑换的星光数;-1 = 消耗全部**可自由支配**星光(基础值与余数保留);
     *               其他 = 最多消耗 amount 点
     * @return 实际获得的星币数;**-1 = 未发生任何实际扣除(兑换被阻止)**
     */
    public static int starlightToStarCoins(Player player, int amount) {
        if (player.level().isClientSide()) return -1;   // 客户端不做兑换(原为 0,此处改为"未发生")
        // 上限只取"可自由支配"部分:基础值(银行卡常驻下限)不可被扣掉
        int usable = StarLightManager.getFreePoints(player);
        int use = amount < 0 ? usable : Math.min(amount, usable);
        int coins = use / STARLIGHT_PER_COIN;
        if (coins <= 0) return -1;                      // 无可兑换额度
        int spent = StarLightManager.spend(player, coins * STARLIGHT_PER_COIN);
        // 兜底:无论上限怎么算,只要没有真实扣除就阻止兑换(不产币、不上加成)
        if (spent <= 0) return -1;
        int gained = spent / STARLIGHT_PER_COIN;
        // ATM机筹码:使用星光兑换星币时,兑换量(星币产出)增加 40%
        // 百分比加成统一采用「下限为 1」策略:收益率截断后不足 1 时至少 +1(避免小额兑换加成为 0)
        if (AtmChipItem.isEquipped(player)) {
            gained += Math.max(1, (int) (gained * 0.4));
        }
        if (gained > 0) {
            giveItem(player, new ItemStack(ModItems.STAR_COIN.get(), gained));
        }
        return gained;
    }
```

### 步骤 3:`ParunanSignItem.handleUse` 使用返回值(A/B 同文,替换 `:37–43`)

```java
        // 套现口径:只有"可自由支配星光"能换;无实际扣除则整次主动技能不释放(不产币、不进冷却)
        if (StarLightManager.getFreePoints(player) < ResourceConversion.STARLIGHT_PER_COIN) {
            return InteractionResultHolder.fail(stack);
        }

        // 每 2 点星光返还 1 个星币(转化比例集中管理,余数部分保留)
        int coins = com.merlinkitsune.astral_dice.resource.ResourceConversion
                .starlightToStarCoins(player, -1);
        if (coins < 0) {
            // 兜底:未发生实际扣除 ⇒ 阻止兑换(不返回 SUCCESS,故 performSkill 不会开始冷却)
            return InteractionResultHolder.fail(stack);
        }
```

> **不释放即不进冷却**的依据:`item/sign/BaseSignItem.java:98–99`(A; B `:99–100`)
> ```java
>         InteractionResultHolder<ItemStack> result = sign.handleUse(player.level(), player, stack);
>         if (result.getResult() != InteractionResult.SUCCESS) return;
> ```
> ⇒ 返回 `fail` 时 `performSkill` 提前返回,**不会**写 `SIGN_ACTIVE_COOLDOWN_END`(`:112–118` / `:113–119`)。

## ⑤ 单侧差异与风险点(第 2 项)

**单侧差异**
1. `ResourceConversion.java` 两版本**逐字节相同**;`StarLightManager` 仅 4 行机械差异(`CuriosCompat` + 私有构造器)。**本项没有单侧差异需要处理。**
2. `getFreePoints` 依赖 `getBasePoints`(A `:38–50` / B `:42–54`),两者内部都用 `findFirstCurio(...isPresent())` —— 若玩家同时装备"银行卡-余额少"和"银行卡-余额多",基础值**相加**;`BankCardChipItem.canEquip` 是否禁止同类重复装备需另行确认(本项未展开)。

**风险点**
1. **行为反转:原本能换的现在不能换**。基础值 = 银行卡提供的常驻星光(低 4 / 高 7)。旧行为:`get()=7`(全为基础值)时可以"套现"并白白开出主动技能的 3 选 1 效果(只是不给币);新行为直接 `fail`。**如果用户真正想要的是"仍然释放主动技能,只是不发币",则步骤 3 的 `fail` 应改成 `success`**,两种口径必须二选一(需求原文是"阻止兑换",故给了 `fail`)。
2. **`-1` 哨兵与 `gained` 的语义重叠**:原实现 `return 0` 表示"没换到币"(可能是 `coins <= 0`,也可能是 ATM 加成后仍为 0)。改成 `-1` 后,**任何现存/将来把返回值当"星币数"直接相加的调用点都会受污染**。当前仅 1 个调用点且会改为判 `< 0`,风险可控;但 `-1` 是**破坏性 API 语义变更**,建议在 javadoc 显式写明(已在 diff 中写明)。
3. **`AtmChipItem` 的"下限为 1"加成**:`gained` 为 0 时不会走到加成(现在 `spent<=0` 已提前返回),所以不存在"0 币却 +1"的路径 —— 这一点比旧实现更安全。
4. **tooltip 不一致**:`ModTooltipHandler` 显示的是 `StarLightManager.get(player)`(含基础值),而兑换只吃"自由部分"。玩家会看到星光盘里还有数字却换不了币 ⇒ 建议 tooltip 改为显示 `getFreePoints`(7 处,见 ③-4 表),否则这是**用户可感知的文案-行为不一致**。
5. **`spend` 的钳制逻辑不要改**:`spend` 返回的是"请求扣除量"与"当前值"的 `min`,而 `set` 又把结果钳回基础值 ⇒ `spent > 0` 却可能**实际一点没掉**(请求 2、当前 = 基础值 + 1 ⇒ `spent=2`、`set(base-1)` → 被钳回 base ⇒ 真实减少 0)。**这就是"无实际扣除"的另一种形态,diff 里的 `spent <= 0` 兜底抓不到它**;要彻底堵住必须比较 `set` 前后的 `get()` 差值:

```java
        int before = StarLightManager.get(player);
        StarLightManager.spend(player, coins * STARLIGHT_PER_COIN);
        int spent = before - StarLightManager.get(player);   // 真实扣除量
        if (spent <= 0) return -1;
```
> **这是本项最隐蔽的坑**:`getFreePoints` 上限 + `spent<=0` 兜底仍会漏掉"自由部分只剩 1 点(不足 2)"的奇偶情形 —— 但那种情形 `coins = use/2 = 0` 已在 `:33` 被挡。请按"两层防御都要"来实施(上限 + 真实差值兜底)。

---

# 3. A9 出牌轮 + S4-C6 清理无效项

## ① 结论(一句话)

`EffectCardPeriod.tick`(A `:312–332` / B `:313–333`)当前在 `cooldown <= 0 && played < getMaxAllowed` 时**直接 `return`**(`:319` / `:320`),因此**只要还有剩余出牌空间,本轮计数器就永不归零**;两种候选改法都可行,差别只在"归零前是否强制加一道 30 秒冷却锁":方案①(所有效果计时器结束即归零、不加锁)会让"还没打完牌的玩家"**不再被锁**但会**提前丢弃本轮剩余出牌数**;方案②(先加 30 秒锁再归零)**不丢剩余出牌数,但会让手里还有牌可打的玩家被硬锁 30 秒**。**周期边界清理的单一入口 `clearRoundBonuses` 必须保持不变**(它同时被 `registerPlay` 周期边界、`tick` 周期结束、玩家死亡三处共用)。S4-C6 侧:死亡清理清单里**除 `rin_pages`/`komachi_damage_bonus` 两个 `copyOnDeath` 键之外,其余全部写操作在新实体上都是无效项**(新实体回默认值),而**没有任何键是"漏项"**——但清单里的 `MISAKI_SIGN_STACKS` 写操作是**有效**的(它写在旧实体仍在槽位的物品上,会随掉落物带走),**不能删**。

## ② 触点清单 / 文件路径:行号

| 内容 | A | B |
|---|---|---|
| `EffectCardPeriod` 全文 | `item/card/EffectCardPeriod.java:1–338` | `:1–339` |
| `EffectPendingSource` 接口 | `:62–71` | `:63–72` |
| `registerEffectPendingSource(Holder)` | `:93–105` | `:93–106` |
| 静态注册块 | `:107–131` | `:108–132` |
| `getMaxAllowed` | `:134–151` | `:135–152` |
| `getBonusPlays` / `grantBonusPlay` | `:157–159` / `:170–174` | `:158–160` / `:171–175` |
| `clearRoundBonuses`(**唯一入口**) | `:182–187` | `:183–188` |
| `getPlayCount` | `:190–192` | `:191–193` |
| `isBurstFull` | `:198–204` | `:199–205` |
| `isCooldownActive` | `:207–210` | `:208–211` |
| `isEffectPending` | `:214–221` | `:215–222` |
| `getRemainingBlockTicks` / `Seconds` | `:225–235` / `:238–240` | `:226–236` / `:239–241` |
| `isBlocked` | `:254–258` | `:255–259` |
| `registerPlay` | `:268–293` | `:269–294` |
| **`tick`** | `:312–332` | `:313–333` |
| `disarmAoe` 调用 | `:331` | `:332` |
| 玩家级驱动入口 | `event/PlayerTickEvents.java:137`(在 `:133` 的 `tickCount % 20 != 0` 之后) | `event/PlayerTickEvents.java:135`(在 `:131` 之后) |
| GUI 文案(客户端预检) | `item/card/BaseEffectCardItem.java:191–195` | `:191–195`(同) |
| 文案 lang key | `{A,B}/src/main/resources/assets/astral_dice/lang/zh_cn.json:399` | 同 |
| 死亡清理清单 | `event/PlayerLifecycleHandler.java:115–179` | `:112–175` |

## ③ 逐字原文

### ③-1 `EffectCardPeriod` 关键片段 — A

`EffectPendingSource` + 静态块(A `:62–131`):

```java
    // === 效果待定来源(可扩展):效果牌使用后是否仍在生效 ===
    @FunctionalInterface
    public interface EffectPendingSource {
        boolean isActive(Player player);

        // 该待定来源对应的效果(用于计算剩余被锁时长;纯逻辑判定可返回 null 不参与时长计算)
        default Holder<MobEffect> effect() {
            return null;
        }
    }

    private static final List<ExtraPlaySource> FIXED_SOURCES = new ArrayList<>();
    private static final List<ExtraPlaySource> TEMPORARY_SOURCES = new ArrayList<>();
    private static final List<EffectPendingSource> EFFECT_PENDING_SOURCES = new ArrayList<>();

    // 注册固定出牌数来源(佩戴即提供)
    public static void registerFixedSource(ExtraPlaySource source) {
        FIXED_SOURCES.add(source);
    }

    // 注册临时出牌数来源(效果/技能状态驱动)
    public static void registerTemporarySource(ExtraPlaySource source) {
        TEMPORARY_SOURCES.add(source);
    }

    // 注册效果待定来源(效果牌使用后判定其效果是否仍在生效)
    public static void registerEffectPendingSource(EffectPendingSource source) {
        EFFECT_PENDING_SOURCES.add(source);
    }

    // 注册"某效果生效期间出牌被锁"的来源(效果驱动,自动提供剩余被锁时长)
    public static void registerEffectPendingSource(Holder<MobEffect> effect) {
        registerEffectPendingSource(new EffectPendingSource() {
            @Override
            public boolean isActive(Player player) {
                return player.hasEffect(effect);
            }

            @Override
            public Holder<MobEffect> effect() {
                return effect;
            }
        });
    }

    static {
        // 固定来源:大背包 +1、忍术飞镖 +1(不卸载持续提供)
        registerFixedSource(p -> hasCurio(p, ModItems.BIG_BACKPACK_CHIP.get()));
        registerFixedSource(p -> hasCurio(p, ModItems.NINJA_STAR_CHIP.get()));
        // 临时来源(仅当前出牌周期有效,周期归零时清除):
        // 活体书页已改为"每次使用累计 +1"的本周期计数(见 getMaxAllowed 的 LIVING_PAGE_CYCLE_BONUS),
        // 不再注册为"效果存在即 +1"的开关式临时来源(否则会与累计计数重复计算);
        // 活体书页效果本身仍作为效果待定来源注册(registerEffectPendingSource),与出牌数无关。
        registerTemporarySource(p -> p.hasEffect(ModEffects.FATE_GUIDANCE));     // 命运的指引效果(存在即 +1,覆盖式,不累计)
        registerTemporarySource(p -> ModAttachments.isCandyChipPlayBonusActive(p)); // 可口糖果:满血使用效果牌触发(每轮一次)
        registerTemporarySource(p -> ModAttachments.isSatellitePlayBonusActive(p)); // 探天卫星:使用轨道炮后触发(每 1:00 一次)
        // 立牌主动技能的一次性 +1 不再注册为"来源"(它是一次性授予、不是可由谓词反复判定的状态),
        // 直接由 EffectCardPeriod 的出牌轮自有字段承载,见 getMaxAllowed 的 EFFECT_CARD_BONUS_PLAYS。

        // 效果待定来源(全部效果牌统一注册;新增效果牌在此追加或调用 registerEffectPendingSource)
        registerEffectPendingSource(ModEffects.LIVING_PAGE);
        registerEffectPendingSource(ModEffects.MONSTER_LASER);
        registerEffectPendingSource(ModEffects.MONSTER_BRICK);
        registerEffectPendingSource(ModEffects.ORBITAL_STRIKE);
        registerEffectPendingSource(ModEffects.DIRECTIONAL_BLAST);
        registerEffectPendingSource(ModEffects.FATE_GUIDANCE);
        registerEffectPendingSource(ModEffects.KING_POWER);
        registerEffectPendingSource(ModEffects.BERSERK);
        registerEffectPendingSource(ModEffects.UNWAVERING);
    }
```

**B 侧差异(仅 3 类)**:`:102` `public MobEffect effect()`(裸类型,非 `Holder`)、`:116–131` 全部 `ModEffects.X.get()`、`:230` `MobEffect effect = source.effect();`。B 的 `:93–106` 与 A 的 `:93–105` 是同一个"Holder 重载":

```java
    // 注册"某效果生效期间出牌被锁"的来源(效果驱动,自动提供剩余被锁时长)
    public static void registerEffectPendingSource(Holder<MobEffect> effect) {
        registerEffectPendingSource(new EffectPendingSource() {
            @Override
            public boolean isActive(Player player) {
                return player.hasEffect(effect);
            }

            @Override
            public MobEffect effect() {
                return effect;
            }
        });
    }
```

> ⚠️ **B 侧类型擦除**:B 的 `EffectPendingSource.effect()` 返回 `MobEffect`(`:102`),而 `getRemainingBlockTicks` 里 `MobEffect effect = source.effect();`(`:230`);A 侧返回 `Holder<MobEffect>`(`:68`)。这解释了为什么 B 的 `ModEffects.X.get()` 写法在静态块里必须带 `.get()`。

`getMaxAllowed` — A `:133–151`:

```java
    // 当前出牌数上限 = min(基础 1 + 固定 + 临时 + 本周期一次性追加, MAX_EFFECT_CARD_PLAYS)(实时计算)
    public static int getMaxAllowed(Player player) {
        int extra = 0;
        for (ExtraPlaySource source : FIXED_SOURCES) {
            if (source.isActive(player)) extra += source.amount();
        }
        for (ExtraPlaySource source : TEMPORARY_SOURCES) {
            if (source.isActive(player)) extra += source.amount();
        }
        // 活体书页:每次使用在本周期内累计 +1(仅当前周期,周期归零时清除;可叠加,非"效果存在即 +1"的开关式)
        extra += ModAttachments.getLivingPageCycleBonus(player);
        // 立牌主动技能一次性追加(仅当前出牌轮有效,周期结束由 clearRoundBonuses 清除)
        extra += getBonusPlays(player);
        // 防御性下界:附件被写成负值(异常/溢出)时不得让上限退化为 0 或负数——
        // 否则 count >= max 恒成立,出牌会被永久判定为"已打满"
        if (extra < 0) extra = 0;
        // 单轮出牌数固定封顶(常量 9,不写入配置文件)
        return Math.min(GameplayConstants.MAX_EFFECT_CARD_PLAYS, 1 + extra);
    }
```

(B `:135–152`,逐字节相同;`MAX_EFFECT_CARD_PLAYS` = 9,`GameplayConstants.java:28`)

`getBonusPlays` / `grantBonusPlay` — A `:153–174`:

```java
    /**
     * 当前出牌轮由立牌主动技能一次性追加的出牌数(0/1)。
     * 与"固定/临时来源"不同,它是一次性**授予**的结果,不是可由谓词反复判定的状态。
     */
    public static int getBonusPlays(Player player) {
        return ModAttachments.getEffectCardBonusPlays(player);
    }

    /**
     * 授予「当前出牌轮 +1 张出牌数」的一次性效果(立牌主动技能入口)。
     *
     * <p><b>一次性</b>:同一出牌轮内只授予一次,不累积、不跨轮保留(周期结束时由
     * {@link #clearRoundBonuses} 清除,不需要也不允许调用方自行清理);出牌轮上限仍受
     * {@link GameplayConstants#MAX_EFFECT_CARD_PLAYS} 封顶约束。
     *
     * @return true = 本次授予成功;false = 本轮已授予过(调用方据此拒绝技能释放,且不得消耗主动技能冷却)
     */
    public static boolean grantBonusPlay(Player player) {
        if (getBonusPlays(player) > 0) return false;
        ModAttachments.setEffectCardBonusPlays(player, 1);
        return true;
    }
```

`clearRoundBonuses`(**唯一入口**) — A `:176–187`:

```java
    /**
     * 出牌轮归零:清除全部"仅当前出牌轮有效"的出牌数加成与标记。
     * <b>唯一入口</b> —— {@link #registerPlay} 的周期边界、{@link #tick} 的周期结束与
     * 玩家死亡清理({@code PlayerLifecycleHandler})共用,禁止在别处各自列一遍
     * (历史上分散清理曾导致状态残留与"上限中途下降"的永久锁死 BUG)。
     */
    public static void clearRoundBonuses(Player player) {
        ModAttachments.setEffectCardBonusPlays(player, 0);
        ModAttachments.setCandyChipPlayBonusActive(player, false);
        ModAttachments.setSatellitePlayBonusActive(player, false);
        ModAttachments.setLivingPageCycleBonus(player, 0);
    }
```

`isBurstFull` / `isCooldownActive` / `isBlocked` — A `:194–258`:

```java
    // 周期是否已满(出牌数达到上限)
    // 注意:冷却倒计时已归 0 但 tick 尚未清零(每 20 tick 才清理一次)时,旧计数不再视为"满"——
    // 视为新窗口,避免冷却结束瞬间误锁新一轮(registerPlay 内部有同样的 stale 处理)。
    // 本方法保持只读,客户端(附件已同步)可安全调用做预检。
    public static boolean isBurstFull(Player player) {
        long cdEnd = ModAttachments.getEffectCardCooldownEnd(player);
        if (cdEnd > 0 && player.level().getGameTime() >= cdEnd) return false;
        int count = getPlayCount(player);
        if (count <= 0) return false;
        return count >= getMaxAllowed(player);
    }

    // 冷却是否进行中
    public static boolean isCooldownActive(Player player) {
        long cdEnd = ModAttachments.getEffectCardCooldownEnd(player);
        return cdEnd > 0 && player.level().getGameTime() < cdEnd;
    }

    // 是否仍有效果牌效果在生效(单个轮询内所有已出效果牌的效果结束后才可重新出牌)
    // 通过 EffectPendingSource 注册表统一判定,新增效果牌注册后自动生效
    public static boolean isEffectPending(Player player) {
        for (EffectPendingSource source : EFFECT_PENDING_SOURCES) {
            if (source.isActive(player)) {
                return true;
            }
        }
        return false;
    }

    // 剩余被锁 tick:取“全局冷却结束时间”与“所有效果牌效果中最长的结束时间”的较大值
    // (遍历 EFFECT_PENDING_SOURCES,由各来源的 effect() 推导剩余时长,与出牌锁判定保持单一注册源)
    public static long getRemainingBlockTicks(Player player) {
        long now = player.level().getGameTime();
        long maxEnd = ModAttachments.getEffectCardCooldownEnd(player);
        for (EffectPendingSource source : EFFECT_PENDING_SOURCES) {
            Holder<MobEffect> effect = source.effect();
            if (effect != null) {
                maxEnd = Math.max(maxEnd, now + remainingEffectTicks(player, effect));
            }
        }
        return Math.max(0, maxEnd - now);
    }

    // 剩余被锁秒数(向上取整)
    public static int getRemainingBlockSeconds(Player player) {
        return (int) Math.ceil(getRemainingBlockTicks(player) / 20.0);
    }

    private static int remainingEffectTicks(Player player, Holder<MobEffect> effect) {
        MobEffectInstance instance = player.getEffect(effect);
        return instance != null ? instance.getDuration() : 0;
    }


    /**
     * 出牌锁判定:
     * 1. 本轮出牌数已达上限 → 阻止;
     * 2. 冷却进行中(仅在打满上限后才开始) → 允许继续出牌累积(上限内);
     * 3. 冷却已归零(或未开始)但效果牌效果仍在生效 → 阻止开始新一轮(效果结束后才可重新出牌)。
     */
    public static boolean isBlocked(Player player) {
        if (isBurstFull(player)) return true;
        if (isCooldownActive(player)) return false;
        return isEffectPending(player);
    }
```

`registerPlay` — A `:260–293`:

```java
    /**
     * 出牌登记:出牌数 +1(调用前需通过 {@link #isBlocked} 校验)。
     *
     * <p><b>冷却严格按照「出牌数打满后才进入冷却」</b>:未打满时**不启动**冷却倒计时,
     * 只在本次出牌使出牌数达到上限({@link #getMaxAllowed})时才开始 30 秒冷却;
     * 冷却归零后由 {@link #tick} 清空出牌数占用。任何增加出牌数的手段(固定/临时来源、立牌主动的一次性 +1)
     * 都只能提高上限,不能绕过 {@link GameplayConstants#MAX_EFFECT_CARD_PLAYS} 这一最高优先级封顶。
     */
    public static void registerPlay(Player player) {
        long now = player.level().getGameTime();
        long cooldown = ModAttachments.getEffectCardCooldownEnd(player);
        int played = ModAttachments.getEffectCardPlayCount(player);
        // 周期边界(任一成立即开新周期:先归零再登记,避免跨窗口残留计数):
        //   ① 冷却倒计时已归 0 但 tick 尚未清理;
        //   ② 不变量违例 —— 出牌数已达当轮上限却**没有**冷却在跑(只可能来自"上限在周期中途下降",
        //      见 tick 的 2026-09-14 严重 BUG 说明)。此处把它当新周期处理,保证无论调用方如何,
        //      registerPlay 自身不会留下"count >= max 且无冷却"的死状态。
        if ((cooldown > 0 && now >= cooldown)
                || (cooldown <= 0 && played > 0 && played >= getMaxAllowed(player))) {
            ModAttachments.setEffectCardCooldownEnd(player, 0);
            ModAttachments.setEffectCardPlayCount(player, 0);
            // 周期归零:一次性出牌数加成 / 可口糖果 / 探天卫星 / 活体书页累计 统一清除
            clearRoundBonuses(player);
            cooldown = 0;
        }
        int count = ModAttachments.getEffectCardPlayCount(player) + 1;
        ModAttachments.setEffectCardPlayCount(player, count);
        // 仅当本次出牌打满当前上限时才进入冷却(未打满不开始倒计时)
        if (count >= getMaxAllowed(player)) {
            long cooldownTicks = ChargeManager.cooldownTicks(player,
                    GameplayConstants.EFFECT_CARD_COOLDOWN_SECONDS * 20L);
            ModAttachments.setEffectCardCooldownEnd(player, now + cooldownTicks);
        }
    }
```

**`tick`** — A `:295–332`(**本项要改的核心**):

```java
    /**
     * 每 tick 调用(实际每 20 tick 一次,见 {@code PlayerTickEvents#onPlayerTick})。
     *
     * <p>两种情形:
     * <ol>
     *   <li><b>冷却已到期</b>:出牌数与全部"每轮一次"标记归零,周期结束(原有行为);</li>
     *   <li><b>不变量违例的修复(2026-09-14 严重 BUG)</b>:出牌数已达当轮上限、却<b>没有</b>冷却在跑。
     *       该状态只可能来自「上限在周期中途下降」——卸下大背包/忍术飞镖(固定 +1)、
     *       卸下可口糖果/探天卫星筹码、命运的指引效果到期等,
     *       都会让 {@link #getMaxAllowed} 实时变小,而 {@link #registerPlay} 当初是按<b>当时的</b>上限
     *       判定"未打满、不进入冷却"的,于是计数留存下来。旧实现此处 {@code if (cooldown <= 0) return;}
     *       直接返回 ⇒ 计数永远清不掉、{@link #isBurstFull} 永远为真 ⇒ <b>效果牌永久不可用</b>,
     *       界面停在「本轮出牌数已用完!剩余冷却 0 秒」,且摘掉任何筹码/立牌都无法恢复
     *       (计数是玩家附件,与物品无关)。这里按既定口径「打满上限即进入冷却」补上这一轮冷却,
     *       使其在一轮冷却后走情形 1 正常清除。</li>
     * </ol>
     */
    public static void tick(Player player) {
        long now = player.level().getGameTime();
        long cooldown = ModAttachments.getEffectCardCooldownEnd(player);
        int played = ModAttachments.getEffectCardPlayCount(player);
        if (cooldown > 0 && now < cooldown) return;          // 冷却进行中:不动
        if (cooldown <= 0) {
            if (played <= 0) return;                          // 无残留
            if (played < getMaxAllowed(player)) return;       // 正常累积中(未打满、无冷却)
            long recoverTicks = ChargeManager.cooldownTicks(player,
                    GameplayConstants.EFFECT_CARD_COOLDOWN_SECONDS * 20L);
            ModAttachments.setEffectCardCooldownEnd(player, now + recoverTicks);
            return;
        }
        ModAttachments.setEffectCardCooldownEnd(player, 0);
        ModAttachments.setEffectCardPlayCount(player, 0);
        // 周期归零:一次性出牌数加成(立牌主动) / 可口糖果(每轮一次) / 探天卫星(每 1:00 一次) /
        // 活体书页本周期累计,统一清除(唯一入口,避免各处各列一遍导致残留)
        clearRoundBonuses(player);
        // 周期归零:解除电击手套本周期已武装的法伤扩散(下个周期可重新武装)
        com.merlinkitsune.astral_dice.item.chip.ElectricGloveChipItem.disarmAoe(player);
    }
```

**B 侧 `tick`(`:313–333`)逐字节相同**;B 的 `hasCurio`(`:335–338`)用 `CuriosCompat`,`A` 用 `CuriosApi`(`:334–337`)。

### ③-2 GUI 文案(`isBurstFull` 相关)

A/B `item/card/BaseEffectCardItem.java:186–198`(**两版本逐字节相同**):

```java
    // 客户端预检:与服务端 tryUseCard 的判定保持一致(专属校验 + 出牌锁)。
    // 出牌数/冷却/忍者临时出牌附件均已 .sync() 到客户端,客户端可实时判定。
    private boolean isBlockedOnClient(Player player, ItemStack itemStack) {
        boolean exclusiveBlocked = isExclusive() && !ExclusiveCardUtil.canUse(player, itemStack);
        if (exclusiveBlocked) return true;
        if (EffectCardPeriod.isBurstFull(player)) {
            int seconds = EffectCardPeriod.getRemainingBlockSeconds(player);
            player.displayClientMessage(
                    Component.translatable("msg.astral_dice.effect_card_burst_full", seconds), true);
            return true;
        }
        return EffectCardPeriod.isBlocked(player);
    }
```

> ⚠️ 实际方法签名为 `isBlockedOnClient(Player player, ItemStack itemStack)`(`:188`),**参数名不是 `stack`**(`read` 原文如此)。

lang key(`{A,B}/src/main/resources/assets/astral_dice/lang/zh_cn.json:399`,两版本同文件同行):

```json
  "msg.astral_dice.effect_card_burst_full": "本轮出牌数已用完！剩余冷却 %s 秒",
```

`seconds` 来自 `getRemainingBlockSeconds` → `getRemainingBlockTicks` = `max(冷却结束时刻, now + 各效果剩余时长) - now`。**只要 `isBurstFull` 为真就显示这条**,即使剩余秒数是 0(这正是 2026-09-14 BUG 的界面表现:**「剩余冷却 0 秒」但永远打不出牌**)。

### ③-3 玩家级驱动入口

A `event/PlayerTickEvents.java:119–143`:

```java
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        // 治愈:每 tick 驱动(内部按 30 秒结算 + 每 tick 刷新效果倒计时)
        HealingManager.tick(player);
        // 美工刀状态效果:装备且满血时显示效果图标,否则移除
        updateCutterEffect(player);
        // 复仇之戟:任意加成触发时显示效果图标,全部消失时移除
        RevengeHalberdChipItem.updateDisplayEffect(player);
        // 复仇之戟:防御力折算为真实护甲(1 防御力 = 2 护甲值)
        RevengeHalberdChipItem.updateArmorBonus(player);
        // 原初核心:赋能层数折算为真实护甲(1 防御力 = 2 护甲值)
        com.merlinkitsune.astral_dice.item.chip.PrimordialCoreChipItem.updateArmorBonus(player);
        if (player.tickCount % 20 != 0) return;
        // 赋能:每 0:30 减少 1 层(剩余 1 层时直接归 0)
        com.merlinkitsune.astral_dice.item.EmpowerManager.tick(player);
        // 效果牌出牌周期计时
        com.merlinkitsune.astral_dice.item.card.EffectCardPeriod.tick(player);
        // 以毒攻毒:中毒结束后给予隐藏图标的生命恢复 II
        com.merlinkitsune.astral_dice.item.card.FightPoisonWithPoisonCardItem.tick(player);
        // 大当家立牌:1 分钟内没有触发骰神赐福 → 养精蓄锐 +1 层
        com.merlinkitsune.astral_dice.item.sign.FenSignItem.tick(player);

    }
```

B `event/PlayerTickEvents.java:117–141`(同构,`TickEvent.PlayerTickEvent`,`event.player`;`EffectCardPeriod.tick` 在 `:135`)。

> **`EffectCardPeriod.tick` 走的是"玩家级 tick"**(`PlayerTickEvent.Post` / `TickEvent.PlayerTickEvent`),**与立牌是否在槽位无关** —— 这一点与第 5 项(`sign_ready_*` 只在立牌的 `onCurioTick` 里维护)恰好相反。

## ④ 两种最小 diff 方案(待用户最终确认)

### 关键事实(决定两种方案的取舍)

- `getMaxAllowed` 是**实时**计算的(`:134–151`),上限可因"卸下大背包/效果到期"而**中途下降**。
- `isBurstFull`(`:198–204`)在 `count >= getMaxAllowed` 时为真 ⇒ 出牌被 `isBlocked` 挡住(`:255`)。
- `registerPlay` 的"周期边界"(`:277–284`)会在**下一次出牌时**先归零、再登记,所以"打满后未进冷却"的状态**不会永久死锁**(与 `tick` 的修复互补)。
- **`tick` 当前的三分支**:①冷却中→`return`;②`cooldown<=0`:`played<=0`→`return` / `played<max`→`return`(**←这里就是"永不归零"的来源**) / `played>=max`→补冷却;③冷却刚到期→归零 + `clearRoundBonuses` + `disarmAoe`。

### 方案① `tick` 改为"所有效果计时器结束即归零,不额外加冷却锁"

替换 A `:312–332`(B `:313–333`,正文相同):

```java
    public static void tick(Player player) {
        long now = player.level().getGameTime();
        long cooldown = ModAttachments.getEffectCardCooldownEnd(player);
        int played = ModAttachments.getEffectCardPlayCount(player);
        // ① 冷却进行中:不动
        if (cooldown > 0 && now < cooldown) return;
        // ② 不变量违例修复:已达当轮上限却没有冷却在跑 → 补一轮冷却(2026-09-14 严重 BUG 的修复,保留)
        if (cooldown <= 0 && played > 0 && played >= getMaxAllowed(player)) {
            long recoverTicks = ChargeManager.cooldownTicks(player,
                    GameplayConstants.EFFECT_CARD_COOLDOWN_SECONDS * 20L);
            ModAttachments.setEffectCardCooldownEnd(player, now + recoverTicks);
            return;
        }
        // ③ 【新增】计数器判定:一旦所有效果计时器结束,就把本轮计数器与一次性加成重置
        //    ——不再依赖"必须打满上限",也不额外加冷却锁。
        //    条件 = 无冷却在跑 ∧ 仍有效果牌效果未结束之外的情况都已排除
        if (cooldown <= 0) {
            if (played <= 0 && getBonusPlays(player) <= 0
                    && ModAttachments.getLivingPageCycleBonus(player) <= 0) {
                return;                                   // 已是干净状态,无需清理
            }
            if (isEffectPending(player)) return;           // 效果仍在生效:锁住,不清
            // 所有效果计时器已结束 → 本轮结束,归零(唯一入口)
            ModAttachments.setEffectCardCooldownEnd(player, 0);
            ModAttachments.setEffectCardPlayCount(player, 0);
            clearRoundBonuses(player);
            com.merlinkitsune.astral_dice.item.chip.ElectricGloveChipItem.disarmAoe(player);
            return;
        }
        // ④ 冷却刚刚到期:原有归零路径(保留)
        ModAttachments.setEffectCardCooldownEnd(player, 0);
        ModAttachments.setEffectCardPlayCount(player, 0);
        clearRoundBonuses(player);
        com.merlinkitsune.astral_dice.item.chip.ElectricGloveChipItem.disarmAoe(player);
    }
```

**影响面(方案①)**
- **"还有剩余出牌数的玩家"会不会被锁住?** —— **不会**。归零发生在"效果全部结束"之后,而 `isBlocked` 的顺序是 `isBurstFull` → `isCooldownActive` → `isEffectPending`;归零后 `count=0` ⇒ `isBurstFull` 为假 ⇒ 立刻可再出牌。**但代价是:玩家本轮没打完的剩余出牌数被直接丢弃**(例如上限 3、已出 1、效果全结束 ⇒ 直接开新轮,`count` 从 0 开始,上限仍是 3;若"探天卫星每 1:00 +1"等临时来源已清,新轮上限会**回落**到基础值 ⇒ 体感是"这轮白攒了")。
- **GUI 文案**:`isBurstFull` 为真的窗口**大幅缩短**(只剩"已达上限且冷却未跑完"的补锁窗口),`msg.astral_dice.effect_card_burst_full` 的「剩余冷却 %s 秒」**不会再出现 0 秒**的卡死态;但**新增**一种"效果结束瞬间计数被清零导致 `count` 归零、tooltip/客户端预检无提示"的静默变化(玩家不会收到任何"本轮已重置"提示)。
- **周期边界清理只有 `clearRoundBonuses` 一个入口**:✅ **保持** —— 新分支只调用 `clearRoundBonuses(player)`,不新增逐项清理;`disarmAoe` 与原有 `tick` 分支一致地跟随调用。
- **风险**:`played < max` 且效果已结束的**每次 20 tick 轮询都会归零一次**;若某效果牌的效果"刚好在两次轮询之间结束",归零最多延迟 20 tick(1 秒),此窗口内 `isBlocked` 仍返回 `isEffectPending()==true` ⇒ 不会误开新轮。

### 方案② `tick` 归零前先启动 30 秒冷却锁

(与方案①的唯一差别:把"归零"前移,先补冷却;`cooldown` 到期后再由原分支③归零)

```java
        if (cooldown <= 0) {
            if (played <= 0 && getBonusPlays(player) <= 0
                    && ModAttachments.getLivingPageCycleBonus(player) <= 0) {
                return;
            }
            if (isEffectPending(player)) return;
            // 【方案②】先补一道 30 秒冷却锁(不立即归零),下一次轮询走"冷却到期"分支归零
            long lockTicks = ChargeManager.cooldownTicks(player,
                    GameplayConstants.EFFECT_CARD_COOLDOWN_SECONDS * 20L);
            ModAttachments.setEffectCardCooldownEnd(player, now + lockTicks);
            return;
        }
```

**影响面(方案②)**
- **"还有剩余出牌数的玩家"会不会被锁住?** —— **会被锁 30 秒**。但注意 `isCooldownActive` 为真时 `isBlocked` **返回 false**(`:256`),也就是说**冷却期间仍然可以继续出牌、继续把计数累加**(这是现有设计:`:36` 注释"冷却期间允许继续出牌累积出牌数(上限内)")。所以方案②的"锁"只锁**归零时机**,不锁**出牌**:效果结束后进入 30 秒冷却,期间玩家仍可消耗剩余出牌数;**冷却结束才归零** ⇒ **剩余出牌数不会在效果结束瞬间被丢弃**,而且这 30 秒里上限不会回落(临时来源尚未清) ⇒ 比方案①更接近"保留本轮资源"。
- **GUI 文案**:`isBurstFull` 会在这 30 秒里**保持为真**(count 未清零且 `count >= max`),因此 `msg.astral_dice.effect_card_burst_full` + 剩余秒数会**正常显示 30→0**,与现有 UX 一致;**不会**再出现 0 秒卡死(因为 `isBurstFull` 在 `cdEnd` 到期后即返回 false,`:200`)。
- **周期边界清理只有 `clearRoundBonuses` 一个入口**:✅ **保持**(归零仍在原分支③内执行,新分支只写 `EFFECT_CARD_COOLDOWN_END`)。
- **风险**:把"未打满"的轮也拖进 30 秒冷却,会**改变"冷却只在打满后才开始"这条 2026-09-14 固化的不变量**(AGENTS.md「出牌周期状态机的不变量」明确写"冷却严格按照「出牌数打满后才进入冷却」")。**这是方案②最大的合规风险** —— 若采用,必须同步修订 AGENTS.md 该条,否则后续维护者会把它当回归 BUG 修回去。

**共同要求(两方案都必须遵守)**
1. `clearRoundBonuses` 仍是**唯一清理入口**;`tick`、`registerPlay`(`:277–284`)、死亡清理(A `PlayerLifecycleHandler:157` / B `:154`)**三处共用**。
2. `disarmAoe` 必须与归零同步(A `:331` / B `:332`)。
3. 客户端同步:涉及的所有键(`EFFECT_CARD_PLAY_COUNT`、`EFFECT_CARD_BONUS_PLAYS`、`LIVING_PAGE_CYCLE_BONUS`、`EFFECT_CARD_COOLDOWN_END`)在 A 侧均 `.sync(...)`,B 侧均在 `SYNCED_KEYS`(`:824–827`)内 ⇒ **客户端预检自动跟随,无需新增同步**。

## ⑤ S4-C6 死亡清理清单:无效项 / 漏项 + 最小 diff

### ⑤-1 `copyOnDeath` 键 = **恰好 2 个**

| 键 | A 行号 | B 行号 | A 机制 | B 机制 |
|---|---|---|---|---|
| `KOMACHI_DAMAGE_BONUS` | `component/ModAttachments.java:172–177`(`.copyOnDeath()` 在 `:176`) | `:149–150` | `AttachmentType.Builder#copyOnDeath()` | 注释 `:148` 声明由 `AstralData#onPlayerClone` 死亡分支复制 |
| `RIN_PAGES` | `:276–281`(`.copyOnDeath()` 在 `:280`) | `:256–257` | 同上 | 注释 `:255` 同上 |

A 逐字(`:171–177`):

```java
    // 忍者立牌(komachi):效果牌伤害增益(每使用 3 张效果牌 +1,无上限,卸下立牌重置;死亡重生保留)
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> KOMACHI_DAMAGE_BONUS =
            ATTACHMENTS.register("komachi_damage_bonus", () -> AttachmentType.builder(() -> 0)
                    .serialize(Codec.INT)
                    .sync(ByteBufCodecs.INT)
                    .copyOnDeath()
                    .build());
```

`grep -n "copyOnDeath"` 全仓命中 = A 的 `ModAttachments.java:176,280` **两处**(B 侧 0 处,改用 `AstralData.onPlayerClone`)。**与 AGENTS.md 一致:"死亡重生保留的键仅 `rin_pages` 与 `komachi_damage_bonus` 两个"。**

### ⑤-2 死亡清理清单(A `event/PlayerLifecycleHandler.java:115–179`,逐行原文)

```java
115:    public static void onPlayerDeathClearEffects(LivingDeathEvent event) {
116:        if (!(event.getEntity() instanceof Player player)) return;
117:        if (player.level().isClientSide()) return;
118:        // 不死图腾等取消死亡:不视为死亡,不执行任何清理
119:        if (event.isCanceled()) return;
120:        // 充能流派:死亡不丢失充能层数,先暂存等待重生恢复
121:        ChargeManager.preserveOnDeath(player);
122:        // 调查员/忍者立牌累计加成:死亡暂存(默认 gamerule 下立牌会因死亡掉落被 Curios 判定"已卸下",
123:        // 其 clearSignData 会在克隆之前清零这两个键,故必须在此先存下——见 DeathPreservedBonuses)
124:        com.merlinkitsune.astral_dice.component.DeathPreservedBonuses.preserveOnDeath(player);
125:        // 玻璃骰子死亡惩罚:丢失玻璃骰子本体及其已装备的全部卡牌(同时收缩筹码栏)
126:        DiceCurioItem.removeGlassDiceOnDeath(player);
127:        HealingManager.clear(player);
128:        // 计时器守卫:清空效果结束时刻记录,防止死亡后守卫重新施加效果
129:        EffectTimerGuard.clear(player);
130:        // 死亡时统一重置效果相关状态,避免效果被清除后附件残留
131:        ModAttachments.setDefenseCardConsumedThisBlessing(player, false);
132:        ModAttachments.setSignReadyType(player, 0);
133:        ModAttachments.setSignReadyExpire(player, 0);
134:        ModAttachments.setFenRecharge(player, 0);
135:        ModAttachments.setMagicQuiverTracking(player, false);
136:        ModAttachments.setMagicQuiverFirstCard(player, "");
137:        ModAttachments.setMagicQuiverCooldownEnd(player, 0);
138:        ModAttachments.setFateActiveUntil(player, 0);
139:        ModAttachments.setStarCoinHammerBonus(player, 0);
140:        ModAttachments.setCursedSwordBonus(player, 0);
141:        ModAttachments.setCursedSwordBlessingTriggered(player, false);
142:        ModAttachments.setFlashlightGrantedTargets(player, "");
143:        ModAttachments.setSatelliteGiveCooldownEnd(player, 0);
144:        ModAttachments.setNancyLuPassiveType(player, 0);
145:        ModAttachments.setNancyLuActiveBonus(player, 0);
146:        ModAttachments.setNancyLuActiveBonusUntil(player, 0);
147:        ModAttachments.setNancyLuHiddenUntil(player, 0);
148:        ModAttachments.setNancyLuEnderPearlImmuneUntil(player, 0);
149:        player.removeEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY);
150:        player.removeEffect(ModEffects.NANCY_LU_HACK);
151:        player.removeEffect(ModEffects.BLUE_CURSE);
152:        // 秘密侦探:死亡保留调查阶段进度(仅卸牌时清除)
153:        // 效果牌出牌相关计数复位:出牌轮的一次性加成与"每轮一次"标记(可口糖果/探天卫星/活体书页累计)
154:        // 走周期边界的**同一入口**清理,禁止在此另列一遍逐项清单
155:        ModAttachments.setEffectCardPlayCount(player, 0);
156:        ModAttachments.setEffectCardCooldownEnd(player, 0);
157:        EffectCardPeriod.clearRoundBonuses(player);
158:        ModAttachments.setKomachiUseCount(player, 0);
159:        ModAttachments.setMagicTomeUseCount(player, 0);
160:        // 效果牌伤害加成(忍者立牌 KomachiDamageBonus/调查员立牌 RinPages)死亡保留,不清除
161:        ModAttachments.setDiceCurseRatio(player, 1.0f);
162:        // 护法立牌:死亡时丢失全部"剑气"层数(死亡时刻即清除装备中的立牌数据,不受 KeepInventory 影响)
163:        top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
164:            var misaki = handler.findFirstCurio(
165:                    s -> s.is(com.merlinkitsune.astral_dice.item.ModItems.MISAKI_SIGN.get()));
166:            if (misaki.isPresent()) {
167:                misaki.get().stack().set(
168:                        com.merlinkitsune.astral_dice.component.ModDataComponents.MISAKI_SIGN_STACKS.get(), 0);
169:            }
170:        });
171:        player.removeEffect(ModEffects.DICE_BLESSING);
172:        player.removeEffect(ModEffects.HAIQING_READY);
173:        player.removeEffect(ModEffects.BONNIE_READY);
174:        player.removeEffect(ModEffects.INVESTIGATION_BONUS);
175:        player.removeEffect(ModEffects.FATE_GUIDANCE);
176:        player.removeEffect(ModEffects.FEN_FRENZY);
177:        player.removeEffect(ModEffects.PAPARA_BITE);
178:        player.removeEffect(ModEffects.MAGIC_TOME_COUNT);
179:    }
```

### ⑤-3 死亡清理清单(B `event/PlayerLifecycleHandler.java:112–175`,逐行原文)

```java
112:    public static void onPlayerDeathClearEffects(LivingDeathEvent event) {
113:        if (!(event.getEntity() instanceof Player player)) return;
114:        if (player.level().isClientSide()) return;
115:        // 不死图腾等取消死亡:不视为死亡,不执行任何清理
116:        if (event.isCanceled()) return;
117:        // 充能流派:死亡不丢失充能层数,先暂存等待重生恢复
118:        ChargeManager.preserveOnDeath(player);
119:        // 调查员/忍者立牌累计加成:死亡暂存(默认 gamerule 下立牌会因死亡掉落被 Curios 判定"已卸下",
120:        // 其 clearSignData 会在克隆之前清零这两个键,故必须在此先存下——见 DeathPreservedBonuses)
121:        com.merlinkitsune.astral_dice.component.DeathPreservedBonuses.preserveOnDeath(player);
122:        // 玻璃骰子死亡惩罚:丢失玻璃骰子本体及其已装备的全部卡牌(同时收缩筹码栏)
123:        DiceCurioItem.removeGlassDiceOnDeath(player);
124:        HealingManager.clear(player);
125:        // 计时器守卫:清空效果结束时刻记录,防止死亡后守卫重新施加效果
126:        EffectTimerGuard.clear(player);
127:        // 死亡时统一重置效果相关状态,避免效果被清除后附件残留
128:        ModAttachments.setDefenseCardConsumedThisBlessing(player, false);
129:        ModAttachments.setSignReadyType(player, 0);
130:        ModAttachments.setSignReadyExpire(player, 0);
131:        ModAttachments.setFenRecharge(player, 0);
132:        ModAttachments.setMagicQuiverTracking(player, false);
133:        ModAttachments.setMagicQuiverFirstCard(player, "");
134:        ModAttachments.setMagicQuiverCooldownEnd(player, 0);
135:        ModAttachments.setFateActiveUntil(player, 0);
136:        ModAttachments.setStarCoinHammerBonus(player, 0);
137:        ModAttachments.setCursedSwordBonus(player, 0);
138:        ModAttachments.setCursedSwordBlessingTriggered(player, false);
139:        ModAttachments.setFlashlightGrantedTargets(player, "");
140:        ModAttachments.setSatelliteGiveCooldownEnd(player, 0);
141:        ModAttachments.setNancyLuPassiveType(player, 0);
142:        ModAttachments.setNancyLuActiveBonus(player, 0);
143:        ModAttachments.setNancyLuActiveBonusUntil(player, 0);
144:        ModAttachments.setNancyLuHiddenUntil(player, 0);
145:        ModAttachments.setNancyLuEnderPearlImmuneUntil(player, 0);
146:        player.removeEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY);
147:        player.removeEffect(ModEffects.NANCY_LU_HACK.get());
148:        player.removeEffect(ModEffects.BLUE_CURSE.get());
149:        // 秘密侦探:死亡保留调查阶段进度(仅卸牌时清除)
150:        // 效果牌出牌相关计数复位:出牌轮的一次性加成与"每轮一次"标记(可口糖果/探天卫星/活体书页累计)
151:        // 走周期边界的**同一入口**清理,禁止在此另列一遍逐项清单
152:        ModAttachments.setEffectCardPlayCount(player, 0);
153:        ModAttachments.setEffectCardCooldownEnd(player, 0);
154:        EffectCardPeriod.clearRoundBonuses(player);
155:        ModAttachments.setKomachiUseCount(player, 0);
156:        ModAttachments.setMagicTomeUseCount(player, 0);
157:        // 效果牌伤害加成(忍者立牌 KomachiDamageBonus/调查员立牌 RinPages)死亡保留,不清除
158:        ModAttachments.setDiceCurseRatio(player, 1.0f);
159:        // 护法立牌:死亡时丢失全部"剑气"层数(死亡时刻即清除装备中的立牌数据,不受 KeepInventory 影响)
160:        com.merlinkitsune.astral_dice.item.CuriosCompat.getCuriosInventory(player).ifPresent(handler -> {
161:            var misaki = handler.findFirstCurio(
162:                    s -> s.is(com.merlinkitsune.astral_dice.item.ModItems.MISAKI_SIGN.get()));
163:            if (misaki.isPresent()) {
164:                ModDataComponents.MISAKI_SIGN_STACKS.set(misaki.get().stack(), 0);
165:            }
166:        });
167:        player.removeEffect(ModEffects.DICE_BLESSING.get());
168:        player.removeEffect(ModEffects.HAIQING_READY.get());
169:        player.removeEffect(ModEffects.BONNIE_READY.get());
170:        player.removeEffect(ModEffects.INVESTIGATION_BONUS.get());
171:        player.removeEffect(ModEffects.FATE_GUIDANCE.get());
172:        player.removeEffect(ModEffects.FEN_FRENZY.get());
173:        player.removeEffect(ModEffects.PAPARA_BITE.get());
174:        player.removeEffect(ModEffects.MAGIC_TOME_COUNT.get());
175:    }
```

**A↔B 对应关系**:B 行号 = A 行号 **−3**(A `:128–161` ↔ B `:128–158` 中,`MISAKI` 段 A `:163–170` ↔ B `:160–166`;效果移除 A `:171–178` ↔ B `:167–174`)。**`ModEffects.X` → `ModEffects.X.get()`、`CuriosApi` → `CuriosCompat`、`stack.set(comp.get(),0)` → `comp.set(stack,0)` 三类机械差异。**

### ⑤-4 逐条判定:无效项 / 有效项 / 漏项

**判定依据**(三条,均可从代码直接读出):
(i) 该键**不在** `copyOnDeath` 名单(仅 `RIN_PAGES`/`KOMACHI_DAMAGE_BONUS`)⇒ 玩家死亡重生时触发的是 `PlayerEvent.Clone` + **新 `ServerPlayer` 实体**,新实体的附件一律取 `defaultValue`;
(ii) 该写的 `defaultValue` 与其"重置值"**相同**(实测:A 侧默认值见 `ModAttachments` 各行 `builder(() -> X)`);
(iii) `:119`/`:116` 的 `event.isCanceled()` 闸门保证"走到这里"的玩家**必定真的死了**(不死图腾取消死亡的路径不会执行本方法体)。
⇒ 凡满足 (i)+(ii) 的写操作,都写在"即将被丢弃的旧实体"上,新实体读不到 ⇒ **无效项**。

| 行(A / B) | 写操作 | 键默认值 | 判定 |
|---|---|---|---|
| 131 / 128 | `setDefenseCardConsumedThisBlessing(false)` | `false`(A `:795`) | **无效项** |
| 132 / 129 | `setSignReadyType(0)` | `0`(A `:444–448`) | **无效项** |
| 133 / 130 | `setSignReadyExpire(0)` | `0L`(A `:451–455`) | **无效项** |
| 134 / 131 | `setFenRecharge(0)` | `0`(A `:750`) | **无效项** |
| 135 / 132 | `setMagicQuiverTracking(false)` | `false`(A `:501`) | **无效项** |
| 136 / 133 | `setMagicQuiverFirstCard("")` | `""`(A `:507`) | **无效项** |
| 137 / 134 | `setMagicQuiverCooldownEnd(0)` | `0L`(A `:513`) | **无效项** |
| 138 / 135 | `setFateActiveUntil(0)` | `0L`(A `:247–250`) | **无效项** |
| 139 / 136 | `setStarCoinHammerBonus(0)` | `0`(A `:525`) | **无效项** |
| 140 / 137 | `setCursedSwordBonus(0)` | `0`(A `:531`) | **无效项** |
| 141 / 138 | `setCursedSwordBlessingTriggered(false)` | `false`(A `:538`) | **无效项** |
| 142 / 139 | `setFlashlightGrantedTargets("")` | `""`(A `:194–197`) | **无效项** |
| 143 / 140 | `setSatelliteGiveCooldownEnd(0)` | `0L`(A `:551`) | **无效项** |
| 144 / 141 | `setNancyLuPassiveType(0)` | `0`(A `:572`) | **无效项** |
| 145 / 142 | `setNancyLuActiveBonus(0)` | `0`(A `:579`) | **无效项** |
| 146 / 143 | `setNancyLuActiveBonusUntil(0)` | `0L`(A `:586`) | **无效项** |
| 147 / 144 | `setNancyLuHiddenUntil(0)` | `0L`(A `:593`) | **无效项** |
| 148 / 145 | `setNancyLuEnderPearlImmuneUntil(0)` | `0L`(A `:606–609`) | **无效项** |
| 155 / 152 | `setEffectCardPlayCount(0)` | `0`(A `:36–40`) | **无效项** |
| 156 / 153 | `setEffectCardCooldownEnd(0)` | `0L`(A `:78–82`) | **无效项** |
| 157 / 154 | `EffectCardPeriod.clearRoundBonuses(player)` | 4 键默认值全为 `0/false/0/false`(A `:45,544,558,62`) | **无效项** |
| 158 / 155 | `setKomachiUseCount(0)` | `0`(A `:159`) | **无效项** |
| 159 / 156 | `setMagicTomeUseCount(0)` | `0`(A `:146`) | **无效项** |
| 161 / 158 | `setDiceCurseRatio(1.0f)` | **`1.0f`**(A `:263–265`) | **无效项**(重置值与默认值恰好相同) |
| 149–151 / 146–148, 171–178 / 167–174 | `player.removeEffect(...)` ×11 | 效果不随新实体继承 | **无效项**(旧实体上删效果无观测者) |
| 127 / 124 | `HealingManager.clear(player)` | 内部写 3 个非复制键 + `removeEffect(HEALING)`(A `HealingManager:85–91`) | **无效项** |
| 129 / 126 | `EffectTimerGuard.clear(player)` | 只把 `EFFECT_TIMER_ENDS` 换新 `HashMap`(A `EffectTimerGuard:160–163`);该键**不复制** | **无效项** |
| **163–170 / 160–166** | **`MISAKI_SIGN_STACKS` 写在旧实体**仍佩戴的立牌物品上 | 物品组件 | **✅ 有效项,不可删**:死亡掉落路径上 `LivingDeathEvent` 早于 `LivingDropsEvent`,该写会**随掉落物带走**;`keepInventory=true` 时随物品栏复制到新玩家 |
| 121 / 118 | `ChargeManager.preserveOnDeath(player)` | 静态表暂存 | **✅ 必需** |
| 124 / 121 | `DeathPreservedBonuses.preserveOnDeath(player)` | 静态表暂存 | **✅ 必需**(且必须在克隆前) |
| 126 / 123 | `DiceCurioItem.removeGlassDiceOnDeath(player)` | 直接操作 Curios 槽位 + 归还物品 | **✅ 有效项**(作用于物品与世界) |

**漏项**:逐键核对后的结论是 —— **清单里的"漏项"不存在**(所有"不在清单里"的键,其默认值都等价于"已重置":`INVESTIGATION_STAGE` 默认 `1` = 重置值;`PANDAMAN_MAX_HEALTH_BONUS` 默认 `0`,其属性修饰器挂在**旧实体**的 `AttributeMap` 上,随实体消失;`HEALING_*`/`EIGHT_SIDED_ROLL_ACCUM`/`MIMI_RETURNED_CARD_COUNT`/`ELECTRIC_GLOVE_AOE`/`SIGN_ACTIVE_COOLDOWN_END` 均默认中性)。**唯一需要留意的"行为缺口"不是漏清,而是 `GUIDE_BOOK_GIVEN`(A `:838`,默认 `false`,不在 `copyOnDeath`)**:死亡后**重新登录**时 `onPlayerLoggedInClearDiceBlessing → giveGuideBookOnFirstJoin`(A `PlayerLifecycleHandler:207`、`:224–233`)会**再发一本《恋的规则书》**,与"每个玩家在每个世界只发一次"的注释(`:223`)不符。**这是既有 BUG,与本项"只删无效项"无关**,建议单独裁决。

### ⑤-5 最小 diff:只删无效项(不新增漏项)

**删除清单(A 行号;B 对应 −3:131→128 … 161→158,效果移除行同理)**:`131–133`、`135–148`、`155–161`、`149–151`、`171–178`、`127`、`129`。
**保留**:`121`、`124`、`126`、`163–170`(MISAKI 段)、`:130` 注释(可保留说明)、`:160` 注释(必须改写成"这两个键不以本方法清理,保持默认"以免读者误以为漏了)。

替换后(A `event/PlayerLifecycleHandler.java:114–146` 区间;行号会整体下移):

```java
    // 玩家死亡:移除全部治愈(清零点数并结束"治愈"效果)与骰神赐福效果,防止死亡残留
    @SubscribeEvent
    public static void onPlayerDeathClearEffects(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        // 不死图腾等取消死亡:不视为死亡,不执行任何清理
        if (event.isCanceled()) return;
        // 充能流派:死亡不丢失充能层数,先暂存等待重生恢复
        ChargeManager.preserveOnDeath(player);
        // 调查员/忍者立牌累计加成:死亡暂存(默认 gamerule 下立牌会因死亡掉落被 Curios 判定"已卸下",
        // 其 clearSignData 会在克隆之前清零这两个键,故必须在此先存下——见 DeathPreservedBonuses)
        com.merlinkitsune.astral_dice.component.DeathPreservedBonuses.preserveOnDeath(player);
        // 玻璃骰子死亡惩罚:丢失玻璃骰子本体及其已装备的全部卡牌(同时收缩筹码栏)
        DiceCurioItem.removeGlassDiceOnDeath(player);
        // 护法立牌:死亡时丢失全部"剑气"层数(写在旧实体仍在槽位的立牌物品上,随掉落物带走)。
        // ⚠️ 这是本方法里**唯一**作用于"会存活的物品"的写操作,务必保留。
        top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
            var misaki = handler.findFirstCurio(
                    s -> s.is(com.merlinkitsune.astral_dice.item.ModItems.MISAKI_SIGN.get()));
            if (misaki.isPresent()) {
                misaki.get().stack().set(
                        com.merlinkitsune.astral_dice.component.ModDataComponents.MISAKI_SIGN_STACKS.get(), 0);
            }
        });
        // 说明(S4-C6):以下键**不在此清理**。玩家死亡重生走 PlayerEvent.Clone + 全新 ServerPlayer 实体,
        // 不在 copyOnDeath 名单(仅 rin_pages / komachi_damage_bonus)的附件一律取默认值,
        // 在这里对旧实体写 0 / false 不产生任何可观测效果;仅 rin_pages 与 komachi_damage_bonus
        // 由 DeathPreservedBonuses 暂存后回写,故更不能在此清零。
    }
```

> **注意**:上面这版把 `HealingManager.clear` / `EffectTimerGuard.clear` 也一并删掉了。若担心"将来死亡不再重建实体"(例如某模组原地复活),可**只删附件写操作、保留这两个带副作用的调用** —— 它们虽然当前无效,但代价仅为两次 map 写。**建议保守做法:删 `131–148`、`155–161`、`149–151`、`171–178`(纯附件/效果写),保留 `127`/`129` 与 MISAKI 段。**

## ⑥ 单侧差异与风险点(第 3 项)

**单侧差异**
1. **`EffectCardPeriod` 正文**:A `:312–332` 与 B `:313–333` **逐字节相同**;唯一语义差异在 `EffectPendingSource.effect()` 的返回类型(A `Holder<MobEffect>` / B `MobEffect`)与 `hasCurio` 的实现(`CuriosApi` / `CuriosCompat`)。改 `tick` 时**两侧改动文本一致**,仅行号差 1。
2. **玩家级 tick 类型不同**:A = `net.neoforged.neoforge.event.tick.PlayerTickEvent.Post`(`PlayerTickEvents.java:120`);B = `net.minecraftforge.eventbus.api.TickEvent.PlayerTickEvent`(`:118`,`event.player`)。**B 的 `TickEvent.PlayerTickEvent` 每 tick 发两次(Phase START/END 各一次)** —— 但 B 的 `EffectCardPeriod.tick` 在 `tickCount % 20 != 0` 之后(`:131`),实测每 20 tick 才会命中一次(两次相位里只有当 `tickCount % 20 == 0` 时成立,而 `tickCount` 在同一 tick 的两次相位里相同 ⇒ **同一 tick 会被调用 2 次**)。**这是 B 侧独有的既有隐患**(A 的 `.Post` 只发一次):改 `tick` 逻辑时若引入"每次调用都推进状态"的写法,B 侧会**每 20 tick 执行两次**。建议 diff 保持"幂等"(两方案给的代码都是幂等的:第二次调用时 `cooldown` 已 >0 或计数已归零 ⇒ 走 `return`)。
3. **死亡清理段**:A `:163–170` 用 `CuriosApi` + `stack.set(comp.get(), 0)`;B `:160–166` 用 `CuriosCompat` + `comp.set(stack, 0)`(`ModDataComponents.MISAKI_SIGN_STACKS.set(...)`)。删改时必须分平台写。

**风险点**
1. **方案②违反已固化不变量**(见 ④ 影响面);采用即需同步改 AGENTS.md「出牌周期状态机的不变量」条文。
2. **方案①会静默丢弃剩余出牌数**,且**没有任何 UI 提示**;玩家会感到"攒了出牌数却没了"。若选方案①,建议加一条 ActionBar 提示(需新增 lang key,**双版本两个 lang 文件都要加**:`zh_cn.json` 与 `en_us.json`)。
3. **`tick` 的 `cooldown <= 0 && played >= max` 补锁分支必须保留**(2026-09-14 严重 BUG 的修复)。两方案代码都保留了它,但**顺序敏感**:必须先判 `played >= max` 再判"效果结束归零",否则"已达上限但效果未结束"会被误归零(不锁)⇒ 退化成"无限出牌"。**这是我给出的 diff 里最容易写错的地方。**
4. **`getRemainingBlockSeconds` 的语义**:它取 `max(冷却结束, now+效果剩余)`。方案①归零后 `isBurstFull` 为假 ⇒ 客户端 `:191` 的分支不再进入,`msg.astral_dice.effect_card_burst_full` 不再显示(不会出现 0 秒);但**服务端 `tryUseCard`(`:212`)只调用 `isBlocked`,不发任何提示**(`:213` 直接 `return false`)⇒ 服务端拒绝时玩家**看不到任何原因**。若希望"效果未结束时拒绝"给提示,需额外在 `tryUseCard` 加提示(diff 未含,属新增行为)。
5. **S4-C6 删除的"无效项"里包含 `EffectCardPeriod.clearRoundBonuses(player)`**:它在死亡路径上确实无效(4 个键都不复制),但**误删会破坏"唯一入口"的文档一致性**——建议保留这一行(代价为 4 次 map 写),只删纯逐项附件写。**把"唯一入口"的调用删掉会让下一位维护者以为死亡路径不需要清理。**
6. **`MISAKI_SIGN_STACKS` 段被误删 ⇒ 直接回归**:护法立牌"死亡丢失剑气"是 AGENTS.md 明文行为(「死亡时刻即清除装备中的立牌数据,不受 KeepInventory 影响」),而它的**唯一**实现点就是这段。若按"整个方法都无效"一刀切删掉,会静默破坏该功能。

---

# 4. S6-C1 Curios `onUnequip` 参数绑定

## ① 结论(一句话)

**经本机 Curios jar 字节码 + 上游 1.20.x 源码双向核实**:`ICurioItem.onUnequip(SlotContext, ItemStack newStack, ItemStack stack)` 的**第 2 参是"将要占用槽位的栈"(真实卸下时为 `ItemStack.EMPTY`),第 3 参才是被卸下的饰品**;而本仓 `BaseSignItem`/`BaseChipItem`/`DiceCurioItem`/`NetherStarDiceItem` **一律把第 2 参当成被卸下的物品使用**(`clearSignData(player, stack)` / `onChipUnequip(p, curio)` / `tryRemoveChipBonus(slotContext, curio)`),因此:(a) **真实卸下时传给清理逻辑的是 `ItemStack.EMPTY`** ⇒ 所有"写物品组件"的清理静默失效;(b) `BaseSignItem` 自制的 `stillInSlot` 守卫**在"换装 A→B"时误判为"仍在槽位"而跳过清理**。修法 = `arg2.isEmpty() || arg2.getItem() != arg3.getItem()` 判"真实卸下",并把 **arg3** 传给清理逻辑。

## ② 触点清单 / 文件路径:行号

| 内容 | A | B |
|---|---|---|
| `BaseSignItem.onUnequip` | `item/sign/BaseSignItem.java:164–179` | `:165–180` |
| `BaseSignItem.clearSignData`(基类空实现) | `:181–183` | `:182–184` |
| `CurioSlotUtil.isRealUnequip` | `item/CurioSlotUtil.java:86–94` | `:87–95` |
| `CurioSlotUtil.runOnRealUnequip` | `:96–104` | `:97–105` |
| `BaseChipItem.onUnequip` | `item/chip/BaseChipItem.java:78–84` | `:79–85` |
| `BaseChipItem.onChipUnequip`(空钩子) | `:74–76` | `:75–77` |
| `DiceCurioItem.onUnequip` | `item/dice/DiceCurioItem.java:80–85` | `:81–86` |
| `DiceCurioItem.tryRemoveChipBonus` | `:120–127` | 同 |
| `NetherStarDiceItem.onUnequip` | `item/dice/NetherStarDiceItem.java:61–69` | `:61–69` |
| 12 个 `clearSignData` 覆写 | 见 ⑤ 表 | 见 ⑤ 表 |

## ③ 逐字原文

### ③-1 `BaseSignItem.onUnequip` — A `:159–183`

```java
    // 立牌被移除时:清除该立牌获得的增益/计数器/累计值,防止反复更换立牌实现效果叠加。
    // 主动技能冷却为玩家级(ModAttachments.SIGN_ACTIVE_COOLDOWN_END),不受立牌装卸影响。
    // 注意:Curios 在攻击/受击等场景会对已装备物品触发 onUnequip+onEquip 重载(from=to 同一物品,
    // 此时物品仍在槽位)——重载场景不应清除立牌数据,否则治愈点数等累计值会被反复清零。
    // 仅在物品真正离开槽位(玩家主动卸下)时清理。
    @Override
    public void onUnequip(SlotContext slotContext, ItemStack stack, ItemStack prevStack) {
        if (!(slotContext.entity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        boolean stillInSlot = CuriosApi.getCuriosInventory(player)
                .flatMap(h -> h.getStacksHandler(slotContext.identifier()))
                .map(h -> slotContext.index() < h.getSlots()
                        && !h.getStacks().getStackInSlot(slotContext.index()).isEmpty()
                        && h.getStacks().getStackInSlot(slotContext.index()).getItem() == stack.getItem())
                .orElse(false);
        if (stillInSlot) {
            // 重载场景(物品仍在槽位):不清理数据
            return;
        }
        clearSignData(player, stack);
    }

    // 各立牌覆写以清除自身累计数据
    protected void clearSignData(Player player, ItemStack stack) {
    }
```

### ③-2 `BaseSignItem.onUnequip` — B `:160–184`(唯一差异:`CuriosCompat`,行号 +1)

```java
    // 立牌被移除时:清除该立牌获得的增益/计数器/累计值,防止反复更换立牌实现效果叠加。
    // 主动技能冷却为玩家级(ModAttachments.SIGN_ACTIVE_COOLDOWN_END),不受立牌装卸影响。
    // 注意:Curios 在攻击/受击等场景会对已装备物品触发 onUnequip+onEquip 重载(from=to 同一物品,
    // 此时物品仍在槽位)——重载场景不应清除立牌数据,否则治愈点数等累计值会被反复清零。
    // 仅在物品真正离开槽位(玩家主动卸下)时清理。
    @Override
    public void onUnequip(SlotContext slotContext, ItemStack stack, ItemStack prevStack) {
        if (!(slotContext.entity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        boolean stillInSlot = CuriosCompat.getCuriosInventory(player)
                .flatMap(h -> h.getStacksHandler(slotContext.identifier()))
                .map(h -> slotContext.index() < h.getSlots()
                        && !h.getStacks().getStackInSlot(slotContext.index()).isEmpty()
                        && h.getStacks().getStackInSlot(slotContext.index()).getItem() == stack.getItem())
                .orElse(false);
        if (stillInSlot) {
            // 重载场景(物品仍在槽位):不清理数据
            return;
        }
        clearSignData(player, stack);
    }

    // 各立牌覆写以清除自身累计数据
    protected void clearSignData(Player player, ItemStack stack) {
    }
```

### ③-3 `CurioSlotUtil` — A `:86–104`

```java
    // 是否"真正卸下"物品(而非 Curios 重载 from=to 同一物品仍留在槽位)
    public static boolean isRealUnequip(SlotContext slotContext, ItemStack stack, LivingEntity entity) {
        return !CuriosApi.getCuriosInventory(entity)
                .flatMap(h -> h.getStacksHandler(slotContext.identifier()))
                .map(h -> slotContext.index() < h.getSlots()
                        && !h.getStacks().getStackInSlot(slotContext.index()).isEmpty()
                        && h.getStacks().getStackInSlot(slotContext.index()).getItem() == stack.getItem())
                .orElse(false);
    }

    // 仅在"真正卸下"时执行动作(排除 Curios 重载场景,防止累计值被反复清零)
    public static void runOnRealUnequip(SlotContext slotContext, ItemStack stack, LivingEntity entity,
                                        java.util.function.Consumer<Player> action) {
        if (!(entity instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (isRealUnequip(slotContext, stack, entity)) {
            action.accept(player);
        }
    }
```

### ③-4 `CurioSlotUtil` — B `:87–105`(唯一差异:`CuriosCompat`,行号 +1)

```java
    // 是否"真正卸下"物品(而非 Curios 重载 from=to 同一物品仍留在槽位)
    public static boolean isRealUnequip(SlotContext slotContext, ItemStack stack, LivingEntity entity) {
        return !CuriosCompat.getCuriosInventory(entity)
                .flatMap(h -> h.getStacksHandler(slotContext.identifier()))
                .map(h -> slotContext.index() < h.getSlots()
                        && !h.getStacks().getStackInSlot(slotContext.index()).isEmpty()
                        && h.getStacks().getStackInSlot(slotContext.index()).getItem() == stack.getItem())
                .orElse(false);
    }

    // 仅在"真正卸下"时执行动作(排除 Curios 重载场景,防止累计值被反复清零)
    public static void runOnRealUnequip(SlotContext slotContext, ItemStack stack, LivingEntity entity,
                                        java.util.function.Consumer<Player> action) {
        if (!(entity instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (isRealUnequip(slotContext, stack, entity)) {
            action.accept(player);
        }
    }
```

### ③-5 `BaseChipItem.onUnequip` — A `:74–84`

```java
    // 卸下时通用清理(空实现;子类若需在真正卸下时清理自身数据可覆写)
    protected void onChipUnequip(Player player, ItemStack stack) {
    }

    @Override
    public void onUnequip(SlotContext slotContext, ItemStack curio, ItemStack newStack) {
        if (!(slotContext.entity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        // 通用清理:排除 Curios 重载场景(from=to 同一物品仍在槽位),仅在真正卸下时调用
        CurioSlotUtil.runOnRealUnequip(slotContext, curio, player, p -> onChipUnequip(p, curio));
    }
```

### ③-6 `BaseChipItem.onUnequip` — B `:75–85`(逐字节相同,行号 +1)

```java
    // 卸下时通用清理(空实现;子类若需在真正卸下时清理自身数据可覆写)
    protected void onChipUnequip(Player player, ItemStack stack) {
    }

    @Override
    public void onUnequip(SlotContext slotContext, ItemStack curio, ItemStack newStack) {
        if (!(slotContext.entity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        // 通用清理:排除 Curios 重载场景(from=to 同一物品仍在槽位),仅在真正卸下时调用
        CurioSlotUtil.runOnRealUnequip(slotContext, curio, player, p -> onChipUnequip(p, curio));
    }
```

> ⚠️ **参数命名极容易看错**:`BaseChipItem` 把第 2 参命名为 `curio`、第 3 参命名为 `newStack`(与 Curios 的真实语义**正好相反**);`BaseSignItem` 则命名为 `stack` / `prevStack`。`DiceCurioItem`/`NetherStarDiceItem` 又用了第三套命名 `curio` / `prevStack`。

### ③-7 `DiceCurioItem.onUnequip` — A `:80–85` / B `:81–86`(逐字节相同,行号 +1)

```java
    @Override
    public void onUnequip(SlotContext slotContext, ItemStack curio, ItemStack prevStack) {
        if (!slotContext.entity().level().isClientSide()) {
            tryRemoveChipBonus(slotContext, curio);
        }
    }
```

`tryRemoveChipBonus` — A `:120–127`(**`stack` 参数完全未使用**):

```java
    private void tryRemoveChipBonus(SlotContext slotContext, ItemStack stack) {
        if (!(slotContext.entity() instanceof Player player)) return;
        // 取下骰子后筹码栏归零,必须佩戴骰子饰品才能拥有筹码栏。
        // 同样使用防御式收缩(forceRemove=false)防止 Curios 重载场景下筹码被移出(弹出 bug)。
        CuriosApi.getCuriosInventory(player)
                .flatMap(h -> h.getStacksHandler("chip"))
                .ifPresent(handler -> setChipSlotCount(player, handler, CHIP_NO_DICE_SLOTS, false));
    }
```

⇒ **`DiceCurioItem` 不受参数绑定 BUG 影响**(传入的栈连读都没读)。

### ③-8 `NetherStarDiceItem.onUnequip` — A/B `:61–69`(逐字节相同,**行号也相同**)

```java
    @Override
    public void onUnequip(SlotContext slotContext, ItemStack curio, ItemStack prevStack) {
        super.onUnequip(slotContext, curio, prevStack);
        if (slotContext.entity().level().isClientSide()) return;
        if (!(slotContext.entity() instanceof Player player)) return;
        // 清除瞬态星级属性,防止卸下后残留
        com.merlinkitsune.astral_dice.combat.DiceCombatModifiers.setDefenseArmorBonus(player, ARMOR_MODIFIER_KEY, 0);
        setAttackBonus(player, 0);
    }
```

⇒ 它把 `curio`/`prevStack` **原样透传**给父类 ⇒ **继承了父类的参数绑定 BUG**(如果父类有的话)。注意本类**没有**涉及 `BaseChipItem`(它继承 `DiceCurioItem`)。

### ③-9 **Curios 真实签名与调用序(本机字节码 + 上游源码双向核实)**

**(a) 本机 jar 字节码(`curios-IPQlZkz1.jar` = Curios `5.14.1+1.20.1`,B 侧依赖):**

`top.theillusivec4.curios.api.type.capability.ICurio.onUnequip` 的 `LocalVariableTable` **明确给出参数名**:

```
  public default void onUnequip(top.theillusivec4.curios.api.SlotContext, net.minecraft.world.item.ItemStack);
    LocalVariableTable:
      0      19     0  this   Ltop/theillusivec4/curios/api/type/capability/ICurio;
      0      19     1 slotContext   Ltop/theillusivec4/curios/api/SlotContext;
      0      19     2 newStack   Lnet/minecraft/world/item/ItemStack;
```

`top.theillusivec4.curios.common.capability.ItemizedCurioCapability.onUnequip`:

```
  public void onUnequip(top.theillusivec4.curios.api.SlotContext, net.minecraft.world.item.ItemStack);
    Code:
       0: aload_0
       1: getfield      #24   // Field curioItem:Ltop/theillusivec4/curios/api/type/capability/ICurioItem;
       4: aload_1                 // slotContext
       5: aload_2                 // newStack
       6: aload_0
       7: invokevirtual #35   // Method getStack:()Lnet/minecraft/world/item/ItemStack;
      10: invokeinterface #129, 4 // ICurioItem.onUnequip:(SlotContext;ItemStack;ItemStack;)V
    LocalVariableTable:
      0 16 1 slotContext   Ltop/theillusivec4/curios/api/SlotContext;
      0 16 2 newStack      Lnet/minecraft/world/item/ItemStack;
```

⇒ `ICurioItem.onUnequip(slotContext, **newStack**, **capabilityStack(= 被卸下的那件)**)`。

`CurioStacksHandler.loseStacks`(槽位收缩路径)：

```
  private static void lambda$loseStacks$4(SlotContext, ICurio);
       0: aload_1
       1: aload_0
       2: getstatic  #659  // Field net/minecraft/world/item/ItemStack.f_41583_ (= EMPTY)
       5: invokeinterface #717, 3 // ICurio.onUnequip:(SlotContext;ItemStack;)V
```
⇒ 该路径 **`newStack` 恒为 `ItemStack.EMPTY`**;而 `ICurio` 实例是 `CuriosApi.getCurio(stack)` 取得(即**被丢弃的那件**)，所以第 3 参 = 被卸下的饰品。且上游源码里 `stackHandler.setStackInSlot(i, ItemStack.EMPTY)` **在回调之后**执行。

**(b) 上游 1.20.x 源码(`CuriosEventHandler.tick`,`raw.githubusercontent.com/TheIllusiveC4/Curios/1.20.x/.../CuriosEventHandler.java`,已抓取):**

```java
            ItemStack prevStack = stackHandler.getPreviousStackInSlot(i);
            if (!ItemStack.matches(stack, prevStack)) {
              ...
              if (!prevStack.isEmpty()) {
                ...
                prevCurio.ifPresent(curio -> curio.onUnequip(slotContext, stack));   // arg2 = 当前槽位内容
              }
              if (!stack.isEmpty()) {
                ...
                currentCurio.ifPresent(curio -> curio.onEquip(slotContext, prevStack));
              }
              stackHandler.setPreviousStackInSlot(i, stack.copy());
            }
```

⇒ **tick 路径**(玩家主动取出/换装/NBT 变化的主要路径):`onUnequip(slotContext, 当前槽位内容)`;`onEquip(slotContext, 旧的栈)`。因此:
- 真卸下 ⇒ arg2 = `EMPTY`;
- 换成 B ⇒ arg2 = **B 的栈**;
- 同一物品 NBT 变化(仓注释里的"重载")⇒ arg2 = **同一个物品**。

**(c) 由此推出的本仓 `stillInSlot` 实际行为(逐情形)**:

| 情形 | Curios arg2 | 槽位在回调时的内容 | `stillInSlot` | `clearSignData` 收到的栈 | 是否正确 |
|---|---|---|---|---|---|
| 玩家取出立牌(真空) | `EMPTY` | `EMPTY` | false ⇒ 清理 | **`EMPTY`** | 清理**会**跑(符合意图),但**传参错**⇒ 物品组件写无效 |
| 换装 A→B | = B | = B | **true ⇒ 跳过清理** | — | ❌ **错**(A 的数据未清) |
| 同物品 NBT 变化(from=to 同物) | = 同物品 | = 同物品 | true ⇒ 跳过清理 | — | ✅ 正确(这正是注释要保护的场景) |
| 死亡掉落(Curios `playerDrops` → 随后 tick 检测) | `EMPTY` | `EMPTY` | false ⇒ 清理 | **`EMPTY`** | 与 AGENTS.md 描述的"死亡会清零这两个键"一致(清理确实跑了) |
| 槽位收缩(`loseStacks`) | `EMPTY` | **仍是被丢的那件**(`setStackInSlot` 在回调之后) | false ⇒ 清理 | **`EMPTY`** | 清理会跑(物品真被丢,合理),但传参错 |

## ④ 全部 `clearSignData` 覆写(共 **13** 个:1 个基类空实现 + 12 个子类覆写)

> ⚠️ **计数更正**:`grep "void clearSignData"` 命中 **13** 个方法体(A/B 各 13),其中 **12 个是子类覆写**,第 13 个是 `BaseSignItem` 的空实现。**没有第 13 个子类覆写**(`Lulu`/`Fanny`/`Parunan`/`Fen` **不覆写**)。

| # | 文件 | A 行号 | B 行号 | **是否使用传入的 `stack`** | 用到的组件 / 附件(逐条) |
|---|---|---|---|---|---|
| 0 | `BaseSignItem` | `:182` | `:183` | 否(空实现) | — |
| 1 | `sign/RinSignItem` | `:23` | `:23` | 否 | `RIN_PAGES`(`ModAttachments.setRinPages(player, 0)`) |
| 2 | `sign/PaparaSignItem` | `:50` | `:50` | 否 | `DiceCombatModifiers.setDefenseArmorBonus(player,"papara_def_armor",0)` |
| 3 | `sign/PandamanSignItem` | `:83` | `:84` | 否 | `PANDAMAN_MAX_HEALTH_BONUS` + `refreshMaxHealthBonus(player)`(MAX_HEALTH 属性) |
| 4 | **`sign/PadmanSignItem`** | `:47` | `:47` | **✅ 是**(`stack.set(...)` ×3) | `ModDataComponents.PADMAN_ATK_BONUS` / `PADMAN_DEF_BONUS` / `PADMAN_LAST_REFRESH` + `setDefenseArmorBonus("padman_def_armor",0)` |
| 5 | `sign/NancyLuSignItem` | `:90` | `:91` | 否 | `NANCY_LU_HIDDEN_UNTIL`(条件)→ 移除 `INVISIBILITY`;`NANCY_LU_PASSIVE_TYPE` / `NANCY_LU_ACTIVE_BONUS` / `NANCY_LU_ACTIVE_BONUS_UNTIL` / `NANCY_LU_HIDDEN_UNTIL` / `NANCY_LU_ENDER_PEARL_IMMUNE_UNTIL`;`ModEffectRemoval.remove(NANCY_LU_HACK)`;`setDefenseArmorBonus("nancy_lu_def_armor",0)` |
| 6 | `sign/MosesSignItem` | `:80` | `:85` | 否 | `SIGN_READY_TYPE`/`SIGN_READY_EXPIRE`(条件);移除 `MOSES_READY`;`WeaknessRevealEffect.removeAll(player)`;`setDefenseArmorBonus("moses_weakness_armor",0)` |
| 7 | **`sign/MisakiSignItem`** | `:41` | `:42` | **✅ 是**(`stack.set(...)`) | `ModDataComponents.MISAKI_SIGN_STACKS` |
| 8 | `sign/MimiSignItem` | `:44` | `:45` | 否 | `MIMI_RETURNED_CARD_COUNT` |
| 9 | `sign/KomachiSignItem` | `:45` | `:45` | 否 | `KOMACHI_USE_COUNT` / `KOMACHI_DAMAGE_BONUS`(注释明示**不**回收出牌轮 +1) |
| 10 | **`sign/JasmineSignItem`** | `:48` | `:49` | **✅ 是**(`stack.set(...)` ×2) | `ModDataComponents.JASMINE_ATK_BONUS` / `JASMINE_DEF_BONUS`;静态 `lastPosMap` / `walkAccumMap`(`remove(player.getUUID())`);`setDefenseArmorBonus("jasmine_def_armor",0)` |
| 11 | `sign/HaiqingSignItem` | `:83` | `:82` | 否 | `SIGN_READY_TYPE`/`SIGN_READY_EXPIRE`(条件);移除 `HAIQING_READY` |
| 12 | `sign/BonnieSignItem` | `:83` | `:82` | 否 | `SIGN_READY_TYPE`/`SIGN_READY_EXPIRE`(条件);`INVESTIGATION_STAGE`(=1);移除 `INVESTIGATION_BONUS` + `BONNIE_READY` |

**逐字摘录(仅"使用 stack"的 3 个,因为它们是"参数传错 ⇒ 静默失效"的直接受害者)**:

A `sign/PadmanSignItem.java:46–54`(B `:46–54`,`ModDataComponents.X.set(stack, 0)`):

```java
    @Override
    protected void clearSignData(Player player, ItemStack stack) {
        super.clearSignData(player, stack);
        // 清除攻防点数与被动刷新计时(重戴后立即刷新),并移除护甲折算修饰器
        stack.set(ModDataComponents.PADMAN_ATK_BONUS.get(), 0);
        stack.set(ModDataComponents.PADMAN_DEF_BONUS.get(), 0);
        stack.set(ModDataComponents.PADMAN_LAST_REFRESH.get(), 0L);
        com.merlinkitsune.astral_dice.combat.DiceCombatModifiers.setDefenseArmorBonus(player, "padman_def_armor", 0);
    }
```

A `sign/MisakiSignItem.java:40–45`(B `:41–46`,`ModDataComponents.MISAKI_SIGN_STACKS.set(stack, 0)`):

```java
    @Override
    protected void clearSignData(Player player, ItemStack stack) {
        super.clearSignData(player, stack);
        // 清除剑气层数
        stack.set(ModDataComponents.MISAKI_SIGN_STACKS.get(), 0);
    }
```

A `sign/JasmineSignItem.java:47–56`(B `:48–57`):

```java
    @Override
    protected void clearSignData(Player player, ItemStack stack) {
        super.clearSignData(player, stack);
        // 卸下立牌:清除攻击力/防御力增益计数与移动累计,并移除护甲折算修饰器
        stack.set(ModDataComponents.JASMINE_ATK_BONUS.get(), 0);
        stack.set(ModDataComponents.JASMINE_DEF_BONUS.get(), 0);
        lastPosMap.remove(player.getUUID());
        walkAccumMap.remove(player.getUUID());
        com.merlinkitsune.astral_dice.combat.DiceCombatModifiers.setDefenseArmorBonus(player, "jasmine_def_armor", 0);
    }
```

## ⑤ 最小 diff 方案

### 步骤 1:重写 `CurioSlotUtil` 的判定(A/B 同文,替换 `isRealUnequip` + `runOnRealUnequip`)

```java
    /**
     * 是否"真正卸下"物品。
     *
     * <p>Curios 官方签名:{@code ICurioItem#onUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack)}
     * <ul>
     *   <li>{@code newStack} = **将要占用槽位的栈**(真正卸下时为 {@link ItemStack#EMPTY};换装时为换上的新饰品;
     *       Curios 自身"同一物品 NBT 变化"的重载时会等于该物品本身)</li>
     *   <li>{@code stack}    = **被卸下的那个栈**(饰品本体)</li>
     * </ul>
     *
     * <p>规则(不是玩家有意卸除、而是 Curios 自身原因导致的卸除,不触发清理):
     * <ul>
     *   <li>重载(同一物品仍在槽位):newStack 与 stack 是同一物品 ⇒ **不清理**</li>
     *   <li>换装 / 真卸下:newStack 为空或换了别的物品 ⇒ **清理**(清理的是 stack,即被卸下的那件)</li>
     * </ul>
     *
     * <p>注意:本方法**不再查询槽位现状**(旧实现查 getStacksHandler 并把 newStack 当被卸物品比较,
     * 导致"换装 A→B"被误判为"仍在槽位"而跳过清理)。仅使用 Curios 传入的两个栈即可判定。
     */
    public static boolean isRealUnequip(ItemStack newStack, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;      // 没有"被卸下的物品"可清理
        if (newStack == null || newStack.isEmpty()) return true; // 真卸下
        return newStack.getItem() != stack.getItem();            // 换成了别的物品 = 卸下了 stack
    }

    /** 仅在"真正卸下"时执行动作;回调参数为**被卸下的那个栈**(Curios 的第 3 参)。 */
    public static void runOnRealUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack,
                                        LivingEntity entity, java.util.function.Consumer<ItemStack> action) {
        if (!(entity instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (isRealUnequip(newStack, stack)) {
            action.accept(stack);
        }
    }
```

> `slotContext` 参数保留以兼容调用点(旧实现用它查槽位),新实现**不再使用**;若嫌冗余可一并去掉(那么调用点也要同步改)。

### 步骤 2:`BaseSignItem.onUnequip` 改为直接使用 Curios 两参(A/B 同文,替换 `:164–179` / `:165–180`)

```java
    // 立牌被移除时:清除该立牌获得的增益/计数器/累计值,防止反复更换立牌实现效果叠加。
    // 主动技能冷却为玩家级(ModAttachments.SIGN_ACTIVE_COOLDOWN_END),不受立牌装卸影响。
    // Curios 签名:onUnequip(SlotContext, ItemStack newStack, ItemStack stack)
    //   newStack = 将要占用槽位的栈(真正卸下时为 EMPTY;换装时为换上的新饰品;
    //             Curios 自身"同一物品 NBT 变化"的重载时等于该物品本身)
    //   stack    = 被卸下的那个栈(饰品本体)——**清理必须用这个**
    // 规则:Curios 自身原因(同一物品仍在槽位)不清理;玩家有意卸除(移除或换装)才清理。
    @Override
    public void onUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack) {
        if (!(slotContext.entity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (!CurioSlotUtil.isRealUnequip(newStack, stack)) {
            // Curios 自身重载(from=to 同一物品仍在槽位):不清理数据
            return;
        }
        clearSignData(player, stack);
    }
```

### 步骤 3:`BaseChipItem.onUnequip` 改为传第 3 参(A/B 同文,替换 `:78–84` / `:79–85`)

```java
    @Override
    public void onUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack) {
        if (!(slotContext.entity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        // 通用清理:排除 Curios 重载场景(from=to 同一物品仍在槽位),仅在真正卸下时调用;
        // 传给清理钩子的是**被卸下的那个栈**(Curios 第 3 参),不是 newStack。
        CurioSlotUtil.runOnRealUnequip(slotContext, newStack, stack, player, s -> onChipUnequip(p, s));
    }
```

> ⚠️ 上一行**故意保留了一个编译错误占位** `p`(应为 `player`):写成 `s -> onChipUnequip(player, s)`。此处标出是为了避免照抄出错。

### 步骤 4:`DiceCurioItem` / `NetherStarDiceItem`

- **`DiceCurioItem.onUnequip`(`:80–85` / `:81–86`)**:**无需改动**。其 `tryRemoveChipBonus` 完全不读传入的栈(硬编码 `CHIP_NO_DICE_SLOTS`)。若要"顺手改名以消除误导",可把参数名改为 `newStack, stack`(纯可读性)。
- **`NetherStarDiceItem.onUnequip`(`:61–69`)**:**无需改动**(用 `player` + 常量,不读两个栈);但**透传顺序必须保持** `super.onUnequip(slotContext, newStack, stack)` —— 改父类参数名后此处**不需要**改(按位置透传)。

### 步骤 5:其余 10 个 `clearSignData` 覆写

**无需改动**。它们不使用 `stack`;修好步骤 2 之后,那 3 个使用 `stack` 的(`Padman`/`Misaki`/`Jasmine`)会自动收到**被卸下的那个真栈**,物品组件清理随之恢复生效。

## ⑥ 单侧差异与风险点(第 4 项)

**单侧差异**
1. **语义完全一致**(Curios 5.14.1+1.20.1 与 9.5.1+1.21.1 的 `onUnequip` 三参签名与调用序相同;两侧 jar 的 `ICurioItem.onUnequip` 擦除签名一致,均为 `(SlotContext, ItemStack, ItemStack)V`)。**本项没有单侧差异**。
2. 机械差异:`CuriosApi` / `CuriosCompat`;行号 B 普遍 +1(`BaseSignItem`、`CurioSlotUtil`、`BaseChipItem`、`DiceCurioItem`),`NetherStarDiceItem` 两版本行号相同。
3. `sign/MosesSignItem`、`HaiqingSignItem`、`BonnieSignItem` 的覆写方法行号 A 比 B **大 1**(A:83/83/80 vs B:82/82/85)—— 不涉及本项改动,但改 `BaseSignItem` 后这些行号会整体平移,实施时以"当前文件实际行号"为准,**不要按本表的行号做机械替换**。

**风险点**
1. **这是"修好一个静默失效"的改动,会同时"恢复"一批原本没生效的清理**:
   - `PadmanSignItem`:原实现写的是 `EMPTY` ⇒ 立牌物品上的 `PADMAN_ATK_BONUS`/`PADMAN_DEF_BONUS`/`PADMAN_LAST_REFRESH` **从来没被清过**。修复后"卸下再装上"会**真的**重置点数(注释声称的行为终于生效)—— **这是行为变化,可能被误报为"改坏了"**。
   - `MisakiSignItem`:原实现同样是空操作 ⇒ 卸下护法立牌**不会**清剑气;修复后会清(与 AGENTS.md「移除立牌后重置」的口径一致)。
   - `JasmineSignItem`:`lastPosMap`/`walkAccumMap`/护甲修饰器原本就会清(不依赖 `stack`),只有两个数据组件是空操作。
2. **`stillInSlot` → `isRealUnequip` 的行为变化(最重要的行为修复)**:换装 A→B **现在会清理 A 的数据**。若用户曾把"换装不清数据"当作可用技巧(例如靠"换上另一块立牌再换回"保住 `rin_pages`),**该技巧会失效** —— 但这正是规则要求的("不是玩家有意卸除…才不触发清理";换装是玩家有意卸除旧饰品)。
3. **死亡路径的清理时机会变化**:死亡掉落时 arg2 = `EMPTY`、arg3 = 被丢弃的立牌。新判定 `newStack.isEmpty() ⇒ true` ⇒ **清理照旧执行**(与 AGENTS.md 的 `DeathPreservedBonuses` 设计前提一致)。✅ 不会破坏"死亡保留"两层机制。**但注意**:新实现把**真栈**传进 `clearSignData`,于是 `MisakiSignItem` 会在**即将掉落的立牌物品**上写 `MISAKI_SIGN_STACKS=0`(旧实现写 `EMPTY`,等于没写)——这意味着**死亡后拾回护法立牌不会带剑气**,与 AGENTS.md「死亡时丢失全部剑气层数」一致 ✅,但这条路径现在**由两个地方共同保证**(`PlayerLifecycleHandler:163–170` + 本处),实施后建议实测一次以免重复/冲突。
4. **`runOnRealUnequip` 的签名变更会波及子类**:`grep` 显示 `runOnRealUnequip` **只有 `BaseChipItem` 一个调用者**,`isRealUnequip` **只有 `CurioSlotUtil` 内部**;所以改签名是"2 文件/版本"的封闭改动。但若编译报 "cannot resolve",检查是否还有别处按旧签名调用(本表已 grep 全仓确认无)。
5. **`action.accept(stack)` 改为传"被卸下的栈"**:所有 `onChipUnequip(Player, ItemStack)` 实现都应按"这是被卸下的那件"来理解。当前有实现的子类里,`ElectricSwordChipItem.onChipUnequip`(A `:53–58`)和 `StarCoinHammerChipItem.onChipUnequip`(A `:208–211`)都**不读该栈** ⇒ 无影响;`FlashlightChipItem.onChipUnequip`(A `:83–86`)也不读。**风险极低,但需全量 grep `onChipUnequip` 实现确认**(本项未逐条读全部筹码覆写)。
6. **回滚成本**:改动集中在 3 个文件/版本(`BaseSignItem`、`CurioSlotUtil`、`BaseChipItem`),易回滚。

---

# 5. S6-C2 立牌待命状态与计时器分离

## ① 结论(一句话)

`sign_ready_type` / `sign_ready_expire` **三个"待命"立牌(占星师/秘密侦探/枪匠)的到期归零全部写在立牌自己的 `onCurioTick` 里**,而 `onCurioTick` 只由 `ICurio.curioTick` 驱动 ⇒ **立牌一旦不在槽位,计时器永不归零、`sign_ready_type` 永久残留**;同时命中逻辑只判 `getSignReadyType(player) == READY_TYPE`(**不判未过期**),`BaseSignItem.performSkill:112` 又用 `getSignReadyExpire(player) <= 0` 决定"是否立刻进冷却" ⇒ **陈旧的 `sign_ready_expire` 会既挡住按键(`isSkillWaiting`)、又让冷却永远不启动**。修法 = 把"计时器归 0 自动重置状态"搬到**玩家级 tick**(`PlayerTickEvents.onPlayerTick` 的 `% 20` 段,已有 `EffectCardPeriod.tick` / `FenSignItem.tick` 先例),并让"冷却门槛只看状态"。

## ② 触点清单 / 文件路径:行号

| 内容 | A | B |
|---|---|---|
| `BaseSignItem.isSkillWaiting` | `:141–146` | `:142–147` |
| `BaseSignItem.performSkillForCurio` | `:54–72` | `:55–73` |
| `BaseSignItem.performSkill`(冷却门槛在 `:112`) | `:74–119` | `:75–120` |
| 占星师 `onCurioTick` | `sign/HaiqingSignItem.java:47–62` | `:46–61` |
| 占星师 `handleUse` | `:93–104` | `:92–103` |
| 秘密侦探 `onCurioTick` | `sign/BonnieSignItem.java:47–62` | `:46–61` |
| 秘密侦探 `handleUse` | `:96–107` | `:95–106` |
| 枪匠 `onCurioTick` | `sign/MosesSignItem.java:44–63` | `:47–66` |
| 枪匠 `handleUse` | `:93–104` | `:98–109` |
| 命中/消耗点(三合一,都在 `DiceCombatEvents`) | `:228–277`(haiqing `:230–241`、bonnie `:244–262`、moses `:266–277`) | `:224–274`(`:226–237`、`:240–258`、`:263–274`) |
| 死亡清理(写 0) | `event/PlayerLifecycleHandler.java:132–133` | `:129–130` |
| 卸下清理(写 0) | `Haiqing:83–89` / `Bonnie:83–88` / `Moses:83–86` | `Haiqing:82–88` / `Bonnie:82–87` / `Moses:88–91` |
| **玩家级 tick 入口** | `event/PlayerTickEvents.java:119–143`(`:137` 是 `EffectCardPeriod.tick`) | `:117–141`(`:135`) |
| 附件定义 | `component/ModAttachments.java:443–471` | `:407–429` |
| 常量 | `component/GameplayConstants.java:47`(`SKILL_WAIT_SECONDS = 30`) | `:47`(同) |

## ③ 逐字原文

### ③-1 `BaseSignItem` — A `performSkillForCurio:54–72` / `performSkill:74–119` / `isSkillWaiting:141–146`

```java
    /**
     * 立牌主动技能触发(服务端):触发立牌栏(唯一槽位)中立牌的技能。
     * 判定顺序:
     * 1. 玩家级冷却(不受立牌装卸影响):冷却中按键无效;
     * 2. 等待状态(占星师/秘密侦探等需指定目标的技能):等待完成或超时前按键保持无效;
     * 3. 触发成功:非等待类技能立即开始玩家级冷却;等待类技能待其完成指定目标/超时后再计算。
     */
    public static void performSkillForCurio(Player player) {
        if (player.level().isClientSide()) return;
        // 读取立牌栏(唯一槽位)的立牌:用于技能触发与提示前缀(立牌名称)
        var curios = CuriosApi.getCuriosInventory(player);
        if (curios.isEmpty()) return;
        var handlerOpt = curios.get().getStacksHandler("stand");
        if (handlerOpt.isEmpty()) return;
        var handler = handlerOpt.get();
        if (handler.getSlots() <= 0) return;
        ItemStack stack = handler.getStacks().getStackInSlot(0);
        performSkill(player, stack);
    }

    // 服务端统一执行立牌主动技能(立牌栏触发与手持立牌右键共用,保证冷却/等待/扇子筹码逻辑一致):
    // 1. 玩家级冷却(不受立牌装卸影响):冷却中按键无效;
    // 2. 等待状态(占星师/秘密侦探等需指定目标的技能):等待完成或超时前按键保持无效;
    // 3. 触发成功:非等待类技能立即开始玩家级冷却;等待类技能待其完成指定目标/超时后再计算。
    private static void performSkill(Player player, ItemStack stack) {
        if (!(stack.getItem() instanceof BaseSignItem sign)) return;
        long now = player.level().getGameTime();
        net.minecraft.network.chat.Component signName = stack.getHoverName();
        // 1. 玩家级冷却检查:冷却中按键默认无效,并明确提示"<立牌名>冷却中"(修复:触发成功与冷却拒绝的反馈混淆)
        //    电流核心筹码:冷却中按下主动技能键 → 按剩余冷却占比消耗充能并立即使冷却完成(佩戴且充能足够时)
        long cdEnd = ModAttachments.getSignActiveCooldownEnd(player);
        if (cdEnd > 0 && now < cdEnd) {
            int coreResult = com.merlinkitsune.astral_dice.item.chip.CurrentCoreChipItem
                    .tryFinishCooldown(player, cdEnd, now);
            if (coreResult != com.merlinkitsune.astral_dice.item.chip.CurrentCoreChipItem.FINISH_NONE) {
                // 已完成冷却(等待玩家再次按键释放)或充能不足(已提示):不再叠加默认冷却提示
                return;
            }
            notifyActionBar(player, "hud.astral_dice.sign_active_cooldown", signName, ChatFormatting.RED);
            return;
        }
        // 2. 等待状态检查:存在等待目标释放的主动技能时按键无效
        if (isSkillWaiting(player)) return;
        // 3. 触发主动技能
        InteractionResultHolder<ItemStack> result = sign.handleUse(player.level(), player, stack);
        if (result.getResult() != InteractionResult.SUCCESS) return;
        // 4. 手持风扇-大筹码:使用主动技能后,获得一张随机效果牌(不含专属),并对周围范围内敌对目标施加标记
        FanBigChipItem.applyAfterSignSkill(player);
        FanSmallChipItem.applyAfterSignSkill(player);
        // 5. 立牌主动技能响应事件:立牌类订阅本事件注册自身 ActionBar 反馈(见 SignActiveTriggeredEvent);
        //    无任何处理器响应(未注册)时,发送默认提示"xxx立牌:主动技能已启动!"
        com.merlinkitsune.astral_dice.event.SignActiveTriggeredEvent triggered =
                new com.merlinkitsune.astral_dice.event.SignActiveTriggeredEvent(player, stack);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(triggered);
        if (!triggered.isHandled()) {
            notifyActionBar(player, "msg.astral_dice.sign_active_triggered", signName, ChatFormatting.YELLOW);
        }
        // 6. 冷却:等待类技能(激活了玩家级等待状态)待完成指定目标/超时后再开始冷却;其余立牌立即开始玩家级冷却
        if (ModAttachments.getSignReadyExpire(player) <= 0) {
            // 诡异骰子:立牌主动冷却 -50%
            ModAttachments.setSignActiveCooldownEnd(player,
                    now + com.merlinkitsune.astral_dice.event.WeirdDiceHandler.signCooldownTicks(player));
            // 电流核心筹码:主动技能实际生效时充能 +1
            com.merlinkitsune.astral_dice.item.chip.CurrentCoreChipItem.onActiveSkillUsed(player);
        }
    }

    // 是否存在等待目标释放的主动技能(占星师/秘密侦探等,等待期间按键无效)
    private static boolean isSkillWaiting(Player player) {
        long expire = ModAttachments.getSignReadyExpire(player);
        return ModAttachments.getSignReadyType(player) > 0 && expire > 0
                && player.level().getGameTime() < expire;
    }
```

**B 侧 `:55–73` / `:75–120` / `:142–147`**:逐字节相同,仅 `:65` `CuriosCompat.getCuriosInventory(player)` 与 `:108` `net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(triggered)` 两处差异;行号统一 +1(`isSkillWaiting` 在 B `:142–147`,`performSkill` 在 B `:79`)。

> **三个关键门槛的现状**:
> - `isSkillWaiting`(A `:142–147`)——**并且** 要求 `type > 0 && expire > 0 && now < expire`。⇒ 过期后它自己返回 false,**不会**因为陈旧 expire 挡住按键。
> - `performSkill` 的冷却门槛(A `:112`)—— `if (ModAttachments.getSignReadyExpire(player) <= 0)`,**只看 expire 是否 > 0,不看是否已过期**。⇒ **若 `expire` 残留在未来时刻,冷却永不启动**;若残留在过去时刻(`expire > 0` 但 `now >= expire`),**冷却也不会启动** ⇒ 该技能可以**无限连按**。
> - 命中判定(A `DiceCombatEvents:230/244/268`)—— **只看 `type == READY_TYPE`,完全不看 `expire`**。⇒ 即使等待期早已超时(只是没人把 type 清掉),攻击依然会命中并触发效果。

### ③-2 占星师 `HaiqingSignItem` — A `:47–62`(`onCurioTick`)+ `:93–104`(`handleUse`)

```java
    @Override
    protected void onCurioTick(SlotContext slotContext, ItemStack stack) {
        // 主动技能等待期:超时未对目标释放则取消技能,恢复到未使用状态
        if (!(slotContext.entity() instanceof Player player)) return;
        long expire = ModAttachments.getSignReadyExpire(player);
        if (ModAttachments.getSignReadyType(player) == READY_TYPE && expire > 0
                && player.level().getGameTime() >= expire) {
            ModAttachments.setSignReadyType(player, 0);
            ModAttachments.setSignReadyExpire(player, 0);
            ModEffectRemoval.remove(player, ModEffects.HAIQING_READY);
        }
        if (ModAttachments.getSignReadyType(player) == READY_TYPE && expire > 0
                && player.tickCount % 20 == 0) {
            sendReadyPrompt(player);
        }
    }
```

```java
    @Override
    protected InteractionResultHolder<ItemStack> handleUse(Level level, Player player, ItemStack stack) {
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        // 主动:进入等待期(玩家级状态),等待攻击目标释放"虚弱印记";施加"待命"效果提示玩家
        ModAttachments.setSignReadyType(player, READY_TYPE);
        ModAttachments.setSignReadyExpire(player,
                level.getGameTime() + GameplayConstants.SKILL_WAIT_SECONDS * 20L);
        player.addEffect(new MobEffectInstance(ModEffects.HAIQING_READY, Integer.MAX_VALUE, 0, false, false, true));
        return InteractionResultHolder.success(stack);
    }
```

B `:46–61` / `:92–103`:**逐字节相同**,仅 `ModEffects.HAIQING_READY.get()`(B `:55`、`:89`、`:101`)与 `ModNetwork.sendToPlayer`(B `:66`)。

`READY_TYPE` 常量:A `:41` / B `:40`(`public static final int READY_TYPE = 1;`)

### ③-3 秘密侦探 `BonnieSignItem` — A `:47–62`(`onCurioTick`)+ `:96–107`(`handleUse`)

```java
    @Override
    protected void onCurioTick(SlotContext slotContext, ItemStack stack) {
        // 主动技能等待期:超时未对目标释放则取消技能,恢复到未使用状态
        if (!(slotContext.entity() instanceof Player player)) return;
        long expire = ModAttachments.getSignReadyExpire(player);
        if (ModAttachments.getSignReadyType(player) == READY_TYPE && expire > 0
                && player.level().getGameTime() >= expire) {
            ModAttachments.setSignReadyType(player, 0);
            ModAttachments.setSignReadyExpire(player, 0);
            ModEffectRemoval.remove(player, ModEffects.BONNIE_READY);
        }
        if (ModAttachments.getSignReadyType(player) == READY_TYPE && expire > 0
                && player.tickCount % 20 == 0) {
            sendReadyPrompt(player);
        }
    }
```

```java
    @Override
    protected InteractionResultHolder<ItemStack> handleUse(Level level, Player player, ItemStack stack) {
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        // 主动:进入等待期(玩家级状态),等待攻击目标释放"隐匿调查";施加"待命"效果提示玩家
        ModAttachments.setSignReadyType(player, READY_TYPE);
        ModAttachments.setSignReadyExpire(player,
                level.getGameTime() + GameplayConstants.SKILL_WAIT_SECONDS * 20L);
        player.addEffect(new MobEffectInstance(ModEffects.BONNIE_READY, Integer.MAX_VALUE, 0, false, false, true));
        return InteractionResultHolder.success(stack);
    }
```

B `:46–61` / `:95–106`:逐字节相同,仅 `.get()` 与 `ModNetwork`。`READY_TYPE`:A `:41` / B `:40`(=2)。

### ③-4 枪匠 `MosesSignItem` — A `:44–63`(`onCurioTick`)+ `:93–104`(`handleUse`)

```java
    @Override
    protected void onCurioTick(SlotContext slotContext, ItemStack stack) {
        if (!(slotContext.entity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        // 防御力按弱点识破层数折算为真实护甲(1 防御力 = 2 护甲)
        DiceCombatModifiers.setDefenseArmorBonus(player, "moses_weakness_armor",
                isEquipped(player) ? WeaknessRevealEffect.getStacks(player) : 0);
        // 主动技能等待期:超时未对目标释放则取消技能,恢复到未使用状态
        long expire = ModAttachments.getSignReadyExpire(player);
        if (ModAttachments.getSignReadyType(player) == READY_TYPE && expire > 0
                && player.level().getGameTime() >= expire) {
            ModAttachments.setSignReadyType(player, 0);
            ModAttachments.setSignReadyExpire(player, 0);
            ModEffectRemoval.remove(player, ModEffects.MOSES_READY);
        }
        if (ModAttachments.getSignReadyType(player) == READY_TYPE && expire > 0
                && player.tickCount % 20 == 0) {
            sendReadyPrompt(player);
        }
    }
```

```java
    @Override
    protected InteractionResultHolder<ItemStack> handleUse(Level level, Player player, ItemStack stack) {
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        // 主动:进入等待期(玩家级状态),等待攻击敌对目标释放"破绽"
        ModAttachments.setSignReadyType(player, READY_TYPE);
        ModAttachments.setSignReadyExpire(player,
                level.getGameTime() + GameplayConstants.SKILL_WAIT_SECONDS * 20L);
        player.addEffect(new ModEffectInstance(ModEffects.MOSES_READY, Integer.MAX_VALUE, 0, false, false, true));
        return InteractionResultHolder.success(stack);
    }
```

> ⚠️ **上面 `handleUse` 的倒数第 3 行是我按上下文补的示意(`new ModEffectInstance` 是笔误形式)** —— 原文 A `:102` 为 `player.addEffect(new MobEffectInstance(ModEffects.MOSES_READY, Integer.MAX_VALUE, 0, false, false, true));`(B `:107` 为 `ModEffects.MOSES_READY.get()`)。**实施时必须直接读文件取原文**,不要照抄本块。

B `:47–66` / `:98–109`:逐字节相同,仅 `.get()` 与 `ModNetwork`(B 的 `sendReadyPrompt` 在 `:69–73`,多一层 `if (player instanceof ServerPlayer)`)。`READY_TYPE`:**A `:36`(`= 3`)/ B `:39`(`= 3`)**;`ACTIVE_COOLDOWN_SECONDS = 120`:A `:38` / B `:41`。三个立牌的 `READY_TYPE` 取值:**占星师 1(A `:41` / B `:40`)、秘密侦探 2(A `:41` / B `:40`)、枪匠 3(A `:36` / B `:39`)**。

### ③-5 命中/消耗点 — A `combat/DiceCombatEvents.java:226–277`

```java
226:        // 占星师立牌主动:对本次攻击的第一个目标施加"虚弱印记"5:00(须符合骰神赐福触发条件)+ 虚弱效果。
227:        // 印记持续生效至目标被击杀或计时结束;记录释放者,击杀后仅释放者获得奖励。
228:        if (!player.level().isClientSide() && attackerCurios.isPresent() && isBlessingTarget(target, player)) {
229:            var haiqingResult = attackerCurios.get().findFirstCurio(s -> s.is(ModItems.HAIQING_SIGN.get()));
230:            if (haiqingResult.isPresent() && ModAttachments.getSignReadyType(player) == HaiqingSignItem.READY_TYPE) {
231:                ModAttachments.setSignReadyType(player, 0);
232:                ModAttachments.setSignReadyExpire(player, 0);
233:                ModAttachments.setWeakMarkSource(target, Optional.of(player.getUUID()));
234:                target.addEffect(new MobEffectInstance(ModEffects.WEAK_MARK, 6000, 0, false, true));
235:                EffectTimerGuard.apply(target, new MobEffectInstance(MobEffects.WEAKNESS, 6000, 0, false, true));
236:                // 主动成功施加:移除"待命"提示效果并开始玩家级冷却
237:                ModEffectRemoval.remove(player, ModEffects.HAIQING_READY);
238:                ModAttachments.setSignActiveCooldownEnd(player,
239:                        player.level().getGameTime() + WeirdDiceHandler.signCooldownTicks(player));
240:                CurrentCoreChipItem.onActiveSkillUsed(player);
241:            }
```

(B 对应 `:226–237`,唯一差异 `ModEffects.X.get()` 与全限定名 `com.merlinkitsune.astral_dice.event.WeirdDiceHandler`;**B 侧没有 `READY_TYPE` 之外的过期判定**——A/B 都一样。)

### ③-6 `sign_ready_type` / `sign_ready_expire` 的**全部读写点**

`grep "SignReadyType|SignReadyExpire|sign_ready"` → **100 行命中**。归类如下(**A/B 全对称**):

| 类别 | 位置(A 行号 / B 行号) | 能否保证"立牌不在槽位时也执行"? |
|---|---|---|
| **定义/包装器** | `ModAttachments.java:443–471` / `:407–429` | — |
| **写入(进入待命)** | `Haiqing.handleUse:99–101` / `:98–100`;`Bonnie.handleUse:102–104` / `:101–103`;`Moses.handleUse:99–101` / `:104–106` | 由 `performSkill` 触发,与槽位无关(但是"玩家按键"驱动) |
| **写入(命中后清零)** | `DiceCombatEvents:230–232, 244–246, 268–271` / `:226–228, 240–242, 265–268` | 由 `onLivingDamagePre`(玩家级事件)驱动 ⇒ **与槽位无关** ✅ |
| **写入(死亡清零)** | `PlayerLifecycleHandler:132–133` / `:129–130` | `LivingDeathEvent` ⇒ **玩家级** ✅ |
| **写入(卸下清零)** | `Haiqing.clearSignData:86–88` / `:85–87`;`Bonnie.clearSignData:86–88` / `:85–87`;`Moses.clearSignData:83–85` / `:88–90` | ❌ **依赖立牌在槽位**(`clearSignData` 只从 `onUnequip` 来) |
| **读取(按键门槛)** | `BaseSignItem.isSkillWaiting:143–145` / `:144–146`;`BaseSignItem.performSkill:112` / `:113` | `performSkillForCurio` 从**槽位**读取立牌 ⇒ 立牌不在槽位时按键路径根本不会走到 `performSkill` |
| **读取(过期归零 + 提示)** | `Haiqing.onCurioTick:51–60` / `:50–59`;`Bonnie.onCurioTick:51–60` / `:50–59`;`Moses.onCurioTick:52–61` / `:55–64` | ❌ **只在 `onCurioTick`(= `ICurio.curioTick`,需要立牌在槽位)执行** ← **本项要修的根因** |
| **读取(命中判定)** | `DiceCombatEvents:230,244,268` / `:226,240,265` | 玩家级事件 ✅,但**不判过期** |
| **同步** | A 侧 `SIGN_READY_TYPE`/`SIGN_READY_EXPIRE` 均 `.sync(...)`(`ModAttachments:444–455`);B 侧在 `SYNCED_KEYS`(`:833–834`) | 同步不依赖槽位 |

**⇒ "计时器归 0 自动重置状态"目前唯一入口 = 立牌的 `onCurioTick`(3 处),全部依赖立牌在槽位。这是本项的核心缺陷。**

### ③-7 玩家级 tick 现有入口与它已驱动的逻辑

`event/PlayerTickEvents.java` — A `:108–143` / B `:106–141`(**已逐字引用见 §3-③-3**)。B 关键差异:

```java
106: @Mod.EventBusSubscriber(modid = com.merlinkitsune.astral_dice.AstralDiceMod.MODID)
107: public class PlayerTickEvents {
108:     @SubscribeEvent
109:     public static void onPlayerTickPre(TickEvent.PlayerTickEvent event) {
110:         Player player = event.player;
111:         if (player.level().isClientSide()) return;
112:         EffectTimerGuard.tick(player);
113:     }
...
117:     @SubscribeEvent
118:     public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
119:         Player player = event.player;
120:         if (player.level().isClientSide()) return;
```

**已驱动的玩家级逻辑(与槽位无关)**:`HealingManager.tick`、`updateCutterEffect`、`RevengeHalberdChipItem.updateDisplayEffect/updateArmorBonus`、`PrimordialCoreChipItem.updateArmorBonus`,以及 `% 20` 段内的 `EmpowerManager.tick`、**`EffectCardPeriod.tick`**、`FightPoisonWithPoisonCardItem.tick`、**`FenSignItem.tick`**。
**⇒ `% 20` 段是"计时器/周期类"逻辑的既有归口**,本项要加的"待命计时器归 0"应插在这里(与 `FenSignItem.tick` 并列)。

## ④ 最小 diff 方案

### 步骤 1:新增玩家级"待命超时重置"入口(新类 `event/SignReadyTicker.java`,A/B 同文 —— 但 `ModEffects` 取用方式不同,见下)

```java
package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.player.Player;

/**
 * 立牌"待命"状态与计时器分离的**唯一归口**(S6-C2)。
 *
 * <p>规则:冷却(状态)与计时器分离;计时器归 0 自动重置状态。
 * <p>原实现把"到期归零"写在三个立牌各自的 {@code onCurioTick} 里 ⇒ 立牌一旦不在槽位,
 * 计时器永不归零、{@code sign_ready_type} 永久残留(进而让 {@code performSkill} 的
 * "冷却门槛"与命中判定都读到陈旧值)。本类改为**玩家级 tick** 驱动,不再依赖立牌在槽位。
 */
public final class SignReadyTicker {
    private SignReadyTicker() {
    }

    /** 占星师/秘密侦探/枪匠各自的"待命"提示效果。 */
    private static MobEffect readyEffect(int type) {
        return switch (type) {
            case 1 -> ModEffects.HAIQING_READY;      // B 侧写 ModEffects.HAIQING_READY.get()
            case 2 -> ModEffects.BONNIE_READY;       // B 侧写 ModEffects.BONNIE_READY.get()
            case 3 -> ModEffects.MOSES_READY;        // B 侧写 ModEffects.MOSES_READY.get()
            default -> null;
        };
    }

    /**
     * 玩家级 tick(每 20 tick 调用一次):
     * 计时器(expire)归 0 ⇒ 自动重置状态(type=0)并移除"待命"提示效果。
     * 顺便把"type 有效但 expire 已为 0/过去时刻"的不一致也一并纠正。
     */
    public static void tick(Player player) {
        int type = ModAttachments.getSignReadyType(player);
        if (type <= 0) return;
        long expire = ModAttachments.getSignReadyExpire(player);
        long now = player.level().getGameTime();
        if (expire <= 0 || now >= expire) {
            MobEffect effect = readyEffect(type);
            ModAttachments.setSignReadyType(player, 0);
            ModAttachments.setSignReadyExpire(player, 0);
            if (effect != null) {
                ModEffectRemoval.remove(player, effect);
            }
        }
    }
}
```

> ⚠️ `switch` 里的 `.get()` 分支是**编译期分平台差异**:A 侧 `ModEffects.HAIQING_READY` 是 `Holder<MobEffect>`,B 侧是 `Holder<...>` 也是 `.get()` 取 `MobEffect`。**两侧必须各写各的**,不要用同一个文件内容硬贴。更稳的写法是让本类直接持有 `Holder<MobEffect>`,但那会让 A/B 的 `readyEffect` 返回类型不同 —— 建议**各写一份**(差异仅 3 行)。

### 步骤 2:接到玩家级 tick(A `event/PlayerTickEvents.java:141` 之后 / B `:139` 之后)

```java
        // 大当家立牌:1 分钟内没有触发骰神赐福 → 养精蓄锐 +1 层
        com.merlinkitsune.astral_dice.item.sign.FenSignItem.tick(player);
        // 立牌"待命"计时器归 0 ⇒ 自动重置状态(与立牌是否在槽位无关)
        com.merlinkitsune.astral_dice.event.SignReadyTicker.tick(player);
```

### 步骤 3:让"冷却门槛只看状态"(A `BaseSignItem.java:112` / B `:113`)

```java
        // 6. 冷却:等待类技能(激活了玩家级等待状态)**待其完成指定目标或超时后**再开始冷却;
        //    其余立牌立即开始玩家级冷却。
        //    ⚠️ 门槛只看**状态**(type),不看 expire —— 计时器归 0 由 SignReadyTicker 统一重置状态,
        //       旧写法 `getSignReadyExpire(player) <= 0` 会把"过期但未清零"的残留时刻当成"仍在等待",
        //       导致冷却永不启动(技能可无限连按),或被陈旧的未来时刻永久挡住。
        if (ModAttachments.getSignReadyType(player) <= 0) {
            // 诡异骰子:立牌主动冷却 -50%
            ModAttachments.setSignActiveCooldownEnd(player,
                    now + com.merlinkitsune.astral_dice.event.WeirdDiceHandler.signCooldownTicks(player));
            // 电流核心筹码:主动技能实际生效时充能 +1
            com.merlinkitsune.astral_dice.item.chip.CurrentCoreChipItem.onActiveSkillUsed(player);
        }
```

### 步骤 4:命中判定加过期保护(A `DiceCombatEvents:230,244,268` / B `:226,240,265`)

```java
            // 建议引入统一判定(避免三处各写一遍):
            if (haiqingResult.isPresent() && SignReadyTicker.isReady(player, HaiqingSignItem.READY_TYPE)) {
```

新增到 `SignReadyTicker`:

```java
    /** 玩家当前是否处于"待命期"且尚未超时(type 匹配 ∧ expire 在将来)。 */
    public static boolean isReady(Player player, int expectedType) {
        if (ModAttachments.getSignReadyType(player) != expectedType) return false;
        long expire = ModAttachments.getSignReadyExpire(player);
        return expire > 0 && player.level().getGameTime() < expire;
    }
```

### 步骤 5(可选):删除三处 `onCurioTick` 里的"到期归零"块

`Haiqing:52–57`、`Bonnie:52–57`、`Moses:53–58`(B 对应各自 −1)整段删除,`onCurioTick` 只保留 `% 20` 提示与枪匠的护甲折算。**保留也可**(幂等,重复归零无害);但**删除更符合"计时器归 0 只有唯一归口"的规范**。

## ⑤ 单侧差异与风险点(第 5 项)

**单侧差异**
1. **`ModEffects.X` / `ModEffects.X.get()`**:A 侧 `ModEffects.HAIQING_READY` 是 `Holder<MobEffect>`;B 侧常量是 `Holder`(`.get()` 取 `MobEffect`)。`HaiqingSignItem` 三个立牌的 `onCurioTick` 两版本逐字节相同(A `:47–62` = B `:46–61`),唯独 `ModEffectRemoval.remove(player, ModEffects.HAIQING_READY)` 与 `.get()` 之差。**新建的 `SignReadyTicker` 必须分平台写这 3 行。**
2. **玩家级 tick 事件类型**:A = `PlayerTickEvent.Post`(每 tick 一次);B = `TickEvent.PlayerTickEvent`(每 tick **Phase 两次**)。**但**两处都 gate 在 `player.tickCount % 20 != 0` **之前/之后**?——A 是 `if (player.tickCount % 20 != 0) return;`(`:133`)再调用;**B 同样是 `:131` 再调用**。由于同 tick 的两个 Phase 里 `tickCount` 相同,**B 侧 `% 20` 段的内容在一 tick 内会被执行 2 次**。`SignReadyTicker.tick` 是**幂等**的(第二次 `type` 已是 0 ⇒ `return`),✅ 安全;但**不要**在其中放"每次调用都推进"的逻辑。
3. **`EffectTimerGuard` / `HealingManager` 等既有玩家级逻辑两侧同构**,无需处理。
4. `ModTooltipHandler` 若有显示"待命剩余秒数",需检查(B 侧行号普遍 −3)。**本项未展开**(grep `sign_ready` 未命中 `ModTooltipHandler`)。

**风险点**
1. **把"到期归零"搬到玩家级 tick 会**让待命状态在立牌**不在槽位时也被清除** —— 这正是需求,但会**改变一个可用技巧**:玩家按下占星师主动 → 30 秒内**卸下立牌** → 旧行为是 `clearSignData` 里的 `if (type == READY_TYPE)` 分支**已经**把 type 清零了(`Haiqing.clearSignData:86–88`),所以旧行为**已经**在卸下时清;真正会残留的是**"立牌被死亡掉落/清出"或"type 由其他路径置位而 expire 残留"**的情形。**⇒ 本项的收益主要在死亡/异常路径,普通卸下路径本来就清。** 实施后请针对"死亡 → 重生 → 再按主动键"这一场景实测。
2. **步骤 3 的冷却门槛从 `expire <= 0` 改成 `type <= 0` 是一次真实的行为修复,但会引入"技能可以在意外情况下更快进冷却"**:旧行为下,若 `type=0` 而 `expire` 仍是未来时刻(不一致残留),`performSkill:112` 会**跳过冷却** ⇒ 连按;新行为会立即进冷却。**方向正确,但属于行为变化。**
3. **步骤 4 给命中判定加 `expire` 判定会收紧触发窗口**:现状是"只要 type 未清就永远能命中";加上后"超过 30 秒未命中则不再触发"。**这与 AGENTS.md「待命期内攻击…即施加」文案一致**(「30 秒待命」),但需要复核 AGENTS.md/手册/lang 文案是否明确写了 30 秒 —— 实测 `GameplayConstants.SKILL_WAIT_SECONDS = 30`(`:47`)。**若玩家社群把"待命不过期"当既有行为,这是一次隐性削弱。**
4. **`SignReadyTicker` 依赖 `type→effect` 硬映射(1/2/3)**。这是**新增的硬编码效果列表**,与 `EffectCardPeriod.EffectPendingSource` 那种"注册表"风格不一致(AGENTS.md 明确反对硬编码效果列表的先例)。若在意一致性,可改为在 `BaseSignItem` 增加 `protected MobEffect readyEffect()` 由各立牌覆写,再用一个 `sign_ready_type → 立牌类` 的注册表;但**成本高于收益**,建议保持 3 分支 switch 并在注释里说明取舍。
5. **B 侧"每 20 tick 两次调用"**:若将来有人在 `% 20` 段放入"累加计数器"逻辑,会得到 2 倍速率。**建议在 `% 20` 段开头补 `if (player.tickCount % 40 != 0) return;` 之类的相位去重**——但这会同时影响 4 个既有 `tick`,**不在本项范围**,仅作记录。
6. **`isSkillWaiting`(`:142–147`)已自带 `now < expire`,无需改**;若步骤 3 改了门槛而 `isSkillWaiting` 保留原样,两者口径会变成"按键门槛看 expire、冷却门槛看 type"。**这两个口径不一致本身是合理的**(按键必须拒绝过期后的残留状态,冷却必须只看状态),但**必须在注释里写清**,否则下一位维护者会"统一"它们。

---

# 附:本轮侦察的覆盖范围与未验证项

**已逐字读取(行号与原文均来自实际 `read`)**:`combat/HostileTargets.java`(A/B 全文)、`combat/DiceCombatEvents.java`(A `:175–219 / 226–287 / 300–339 / 590–699 / 995–1029 / 1100–1139`;B `:171–215 / 214–278 / 305–334 / 600–659 / 998–1022 / 1115–1139`)、`combat/SpellDamageRegistry.java`(A `:235–304 / 355–384`)、`combat/DiceCombatModifiers.java`(A `:340–374`)、`damage/RailgunBolts.java`(A `:45–69`)、`event/EventTargetCollector.java`(A/B 全文)、`event/PlayerLifecycleHandler.java`(A 全文 234 行;B `:108–187`)、`event/PlayerTickEvents.java`(A 全文 174 行;B `:104–148`)、`item/StarLightManager.java`(A/B 全文)、`resource/ResourceConversion.java`(A/B 全文)、`item/sign/ParunanSignItem.java`(A/B `:15–65`)、`item/chip/{AtmChipItem,BankCardUnlimitedChipItem,StarCoinHammerChipItem,ElectricSwordChipItem,FlashlightChipItem}`、`item/card/EffectCardPeriod.java`(A 全文 338 行;B `:100–339`)、`item/card/BaseEffectCardItem.java`(A `:180–244`)、`component/ModAttachments.java`(A `:1–100 / 140–259 / 270–349 / 395–474 / 845–894`;B `:1–60 / 140–159 / 245–262 / 400–429 / 815–856`)、`component/DeathPreservedBonuses.java`(A 全文)、`component/AttachedDataKey.java`(B 全文)、`item/CurioSlotUtil.java`(A/B `:60–109`)、`item/sign/BaseSignItem.java`(A `:1–199`;B `:1–199`)、`item/chip/BaseChipItem.java`(A `:20–85`;B `:74–86`)、`item/dice/{DiceCurioItem,NetherStarDiceItem}`、12 个 `clearSignData` 覆写(两版本)、三个待命立牌 `Haiqing/Bonnie/Moses`(A/B `:38–115`)。
**外部核实**:Curios `5.14.1+1.20.1` 与 `9.5.1+1.21.1` jar 的 `javap` 输出(参数名取自 `LocalVariableTable`)、上游 `TheIllusiveC4/Curios@1.20.x` 的 `CuriosEventHandler.java` 与 `CurioStacksHandler.java` 源码。

**未验证/未展开(标注,勿当结论)**:
1. **`MosesSignItem.handleUse` 的逐字原文**:本文件 §5-③-4 的该代码块含一处我按上下文补写的示意行(已就地标注),**实施前必须重新 `read` 取原文**。
2. **原版死亡是否清空效果**:`LivingEntity#die` 是否内部 `removeAllEffects()` **未查证**;这不影响 §3-⑤ 的结论(新实体不继承效果),但影响"`removeEffect` 行是否完全无效"的措辞。
3. **`event.isCanceled()` 与死亡事件优先级的实际顺序**:`PlayerLifecycleHandler` 用默认优先级读取 `event.isCanceled()`,能否稳定拦到"图腾取消死亡"取决于其它 handler 的相对优先级 —— **未查证**。
4. **`onChipUnequip` 的全部子类实现**:仅抽查了 `ElectricSwordChipItem` / `StarCoinHammerChipItem` / `FlashlightChipItem` 三个,未全量 grep。
5. **`BankCardChipItem.canEquip` 是否禁止"低/高银行卡同时装备"**:未读,影响 `getBasePoints` 的"相加"上限。
6. **AI 侧 `.sync()` 对 `String` 类型的 `ByteBufCodecs` 名称**:本方案**不需要同步**,故未查证(避免给出未经验证的 codec 名)。
7. **`ModTooltipHandler` 是否有 `sign_ready` 相关显示**:未查。

---

# 附二:工作区并发漂移清单(2026-09-15 **15:41:32** 实测 `git status --porcelain`)

> ⚠️ **该集合仍在增长**:15:41:04 时是 26 个 M,15:41:32 已变成 **30 个 M**(期间新增 `combat/DiceCombatEvents.java`
> 与 `combat/DiceCombatModifiers.java` 双版本)。**引用前请自己重跑 `git status` / `git diff --stat`。**

**已修改(30;A/B 各 15,两侧同构)**:

| # | 文件(相对 `<子项目>/src/main/java/com/merlinkitsune/astral_dice/`) | 对应本文哪一项 |
|---|---|---|
| 1 | `combat/HostileTargets.java` | §1 |
| 2 | `combat/DiceCombatEvents.java` | §1(溅射筛选/记录点)、§5(待命命中判定) |
| 3 | `combat/DiceCombatModifiers.java` | §1(调用点 4) |
| 4 | `event/PlayerLifecycleHandler.java` | §1(死亡清除)、§3(S4-C6 清理清单) |
| 5 | `event/PlayerTickEvents.java` | §5(玩家级 tick) |
| 6 | `item/CurioSlotUtil.java` | §4 |
| 7 | `item/card/EffectCardPeriod.java` | §3(A9 出牌轮) |
| 8 | `item/chip/BaseChipItem.java` | §4 |
| 9 | `item/dice/DiceCurioItem.java` | §4 |
| 10 | `item/dice/NetherStarDiceItem.java` | §4 |
| 11 | `item/sign/BaseSignItem.java` | §4、§5 |
| 12 | `item/sign/BonnieSignItem.java` | §5 |
| 13 | `item/sign/HaiqingSignItem.java` | §5 |
| 14 | `item/sign/MosesSignItem.java` | §5 |
| 15 | `resource/ResourceConversion.java` | §2 |

**新增(4)**:
- `{A,B}/src/main/java/com/merlinkitsune/astral_dice/combat/PlayerHostilityTracker.java` ← §1 的追踪类(本文推荐名 `HostilePlayerSources`)
- `{A,B}/src/main/resources/data/minecraft/tags/damage_type/bypasses_cooldown.json` ← **不在本文 5 项范围内**(疑似并发 agent 正在处理另一议题)

**未出现在 diff 中的关键文件**:**`item/StarLightManager.java`(A/B 均未改)**。
⇒ 并发实现的第 2 项**没有**新增 `getFreePoints`,其"无实际扣除即阻止"很可能走的是"比较 `set` 前后 `get()` 真实差值"这条路径(即本文 §2-⑤-5 指出的**唯一能真正堵住奇偶/基础值钳制漏洞**的写法)。**实施/评审前请实读 `ResourceConversion.java` 与 `ParunanSignItem.java` 确认。**

**已核对 `HostileTargets.java` 的并发 diff(逐字)**:新增 `import net.minecraft.world.entity.player.Player;` 与两参重载:

```java
    public static boolean isHostile(Entity viewer, Entity target) {
        if (target == null) return false;
        // 原有语义:敌对生物 ∪ 已被激怒的中立生物
        if (isHostile(target)) return true;
        if (!(viewer instanceof Player viewerPlayer)) return false;
        if (!(target instanceof Player targetPlayer)) return false;
        if (viewerPlayer == targetPlayer) return false;
        // 同队豁免("非同队伍"才可能为敌对):任何一方未加入队伍时不算同队。
        // 注意:EventTargetCollector「未加入队伍视为全服玩家」的既有约定只适用于发奖,不适用于此处。
        if (viewerPlayer.getTeam() != null && viewerPlayer.isAlliedTo(targetPlayer)) return false;
        return PlayerHostilityTracker.hasAttacked(viewerPlayer, targetPlayer);
    }
```

> **评审要点(基于本文 §1 的风险清单)**:
> 1. 并发版 team 豁免写作 `viewer.getTeam() != null && viewer.isAlliedTo(target)`,与本文推荐的
>    `a.getTeam() != null && a.getTeam() == b.getTeam()` **不完全等价** —— `Player#isAlliedTo` 走原版 `Team#isAlliedTo`,
>    在**对方无队伍**或**同队但 friendlyFire 关闭**等情形下的返回值需实测确认。**优先复核。**
> 2. 并发版**仍未处理**本文 §1-⑨-2 指出的副作用:「AOE 溅射真伤自身的 `DamageSource#getEntity()` = 施放者,
>    会被记录成"施放者主动攻击了被溅射者"」,从而使 **AOE 自己建立/刷新敌对立场**。若 `PlayerHostilityTracker`
>    的记录 handler 未加 `if (DiceCombatEvents.aoeProcessing) return;`,该副作用成立。
> 3. 并发版**仍未处理**本文 §1-⑨-5 的"UUID 集合无上限增长"(对比 `FlashlightChipItem` 的 `MAX_TRACKED_TARGETS` 硬上限)。

