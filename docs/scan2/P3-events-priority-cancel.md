# 第二轮只读审计 — P3:事件阶段与优先级 / 取消语义 / 嵌套重入 / tick 相位

> 范围:`docs/execution-order-checklist.md` **§A(A1~A7)** + **§G4(联动回路终止)**
> 输出:本文件是本次**唯一**写入的产物。未修改任何源码 / 资源 / 配置。
> 结论格式:严格按 §I(结论落盘格式)。

---

## 0. 取证快照(必读:工作区正在被另一批代理并发修改)

```
$ git rev-parse HEAD
d48a529ba5fcf14610dc7b7383a10780dbe49168
分支:multi-1.20.1-1.21.1
快照时刻:2026-09-15 15:42:36(+08:00)
```

```
$ git diff --name-only        # 本次审计开始时的快照(已落盘 temp/scan2/working.diff)
forge-1.20.1/.../event/PlayerLifecycleHandler.java
forge-1.20.1/.../resource/ResourceConversion.java
neoforge-1.21.1/.../event/PlayerLifecycleHandler.java
neoforge-1.21.1/.../resource/ResourceConversion.java
?? */src/main/resources/data/minecraft/tags/damage_type/bypasses_cooldown.json
```

**⚠️ 审计期间工作区持续变动**:开始(15:0x)只有 2 个文件被改;15:42 快照时已扩到 **17 个已跟踪文件 + 2 个新文件**;15:44 又新增 `combat/PlayerHostilityTracker.java`。因此:

- 本报告的行号绑定 **`d48a529` + 下表文件哈希**(审计时刻的工作区),不绑定 `d48a529` 本身;
- 工作区并发改动(另一批代理的 13 项)带来的**新顺序问题**在第 3 节末尾单列,不计入"新发现"。

| 文件 | MD5(前 8) |
|---|---|
| `neoforge-1.21.1/.../combat/DiceCombatEvents.java` | `BEDB92F5` |
| `neoforge-1.21.1/.../combat/DiceCombatModifiers.java` | `6CE9355E` |
| `neoforge-1.21.1/.../event/EnderDiceHandler.java` | `BA84DC5C` |
| `neoforge-1.21.1/.../event/ChipDamageHandler.java` | `985EC085` |
| `neoforge-1.21.1/.../event/PlayerLifecycleHandler.java` | `1CFA98D9` |
| `forge-1.20.1/.../combat/DiceCombatEvents.java` | `6BDA750F` |
| `forge-1.20.1/.../combat/DiceCombatModifiers.java` | `672F4E7F` |
| `forge-1.20.1/.../event/EnderDiceHandler.java` | `C73A7767` |
| `forge-1.20.1/.../event/ChipDamageHandler.java` | `56FFD467` |
| `forge-1.20.1/.../event/PlayerLifecycleHandler.java` | `832987E0` |

**反编译证据来源**(只读,未改动):
- `neoforge-1.21.1/build/moddev/artifacts/neoforge-21.1.235-sources.jar`
- `forge-1.20.1/build/moddev/artifacts/forge-1.20.1-47.4.10-sources.jar`
- 事件总线:`bus-8.0.5-sources.jar`(NeoForge)/ `eventbus-6.0.5-sources.jar`(Forge)
- FML:`loader-4.0.42-sources.jar`

**注册顺序取证**:`build/libs/astral_dice-1.2.1+{neoforge,forge}_*.jar`(构建于 2026-09-15 14:41,即 HEAD `d48a529`)的 **jar 条目索引**——同优先级内即注册顺序。**注意:这是"今天的实测顺序",不是契约**(第一轮 §0 已定性;本次复核结论一致)。

---

# 第一部分(A1):阶段表 —— 伤害/死亡派发链路(逐行号)

## 1.1 A(neoforge-1.21.1)`LivingEntity.java`

### `hurt(DamageSource, float)` — 段 1:前置闸门与事件入口

| 行号 | 代码原文 | 阶段语义 |
|---|---|---|
| `L1142` | `public boolean hurt(DamageSource source, float amount) {` | 伤害入口 |
| `L1143` | `if (this.isInvulnerableTo(source)) {` | 无敌判定(最前) |
| `L1145` | `} else if (this.level().isClientSide) {` | **客户端直接 `return false`** |
| `L1147` | `} else if (this.isDeadOrDying()) {` | 已死直接 `return false` |
| `L1149` | `} else if (source.is(DamageTypeTags.IS_FIRE) && this.hasEffect(MobEffects.FIRE_RESISTANCE)) {` | 火免 |
| `L1152` | `this.damageContainers.push(new ...DamageContainer(source, amount));` | 压入容器 |
| **`L1153`** | `if (CommonHooks.onEntityIncomingDamage(this, this.damageContainers.peek())) return false;` | **`LivingIncomingDamageEvent` 派发点(可取消)**;取消 → `hurt` 直接 `return false` |
| `L1159` | `amount = this.damageContainers.peek().getNewDamage();` | 容器成为唯一真值 |
| `L1164` | `if (amount > 0.0F && (ev = CommonHooks.onDamageBlock(...)).getBlocked()) {` | `LivingShieldBlockEvent` |
| `L1182` | `if (source.is(DamageTypeTags.DAMAGES_HELMET) && !this.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {` | 头盔耐久 |
| `L1183-1184` | `this.hurtHelmet(source, amount);` / `amount *= 0.75F;` | **1.21.1:头盔 ×0.75 在 `actuallyHurt` 之前** |
| `L1190` | `if ((float)this.invulnerableTime > 10.0F && !source.is(DamageTypeTags.BYPASSES_COOLDOWN)) {` | 无敌帧差额结算 |
| `L1191-1194` | `if (amount <= this.lastHurt) { ... return false; }` | 低伤整段丢弃 |
| `L1197` | `this.actuallyHurt(source, amount - this.lastHurt);` | 走差额 |
| `L1201-1203` | `this.lastHurt = amount;` / `this.invulnerableTime = ...;` / `this.actuallyHurt(source, amount);` | 正常路径 |

### `actuallyHurt(DamageSource, float)` — 段 2:护甲 / 附魔 / 事件 / 吸收

| 行号 | 代码原文 | 阶段语义 |
|---|---|---|
| `L1785` | `protected void actuallyHurt(DamageSource damageSource, float damageAmount) {` | |
| `L1787` | `...setReduction(Reduction.ARMOR, ... - this.getDamageAfterArmorAbsorb(damageSource, ...));` | **护甲减免(1)** |
| `L1788` | `this.getDamageAfterMagicAbsorb(damageSource, this.damageContainers.peek().getNewDamage());` | **抗性/附魔(2)** |
| **`L1789`** | `float damage = CommonHooks.onLivingDamagePre(this, this.damageContainers.peek());` | **`LivingDamageEvent.Pre` 派发点(护甲+附魔之后、吸收之前)** |
| `L1790` | `...setReduction(Reduction.ABSORPTION, Math.min(this.getAbsorptionAmount(), damage));` | **吸收(黄心)开始** |
| `L1791-1792` | `float absorbed = ...` / `this.setAbsorptionAmount(...)` | 扣黄心 |
| `L1799-1801` | `if (f1 != 0.0F) { ... this.setHealth(this.getHealth() - f1); }` | **`setHealth`(唯一扣血点)** |
| `L1805` | `CommonHooks.onLivingDamagePost(this, this.damageContainers.peek());` | `LivingDamageEvent.Post` |

### `die(DamageSource)` — 段 3:死亡派发

| 行号 | 代码原文 |
|---|---|
| `L1408` | `public void die(DamageSource damageSource) {` |
| **`L1409`** | `if (CommonHooks.onLivingDeath(this, damageSource)) return;` ← **`LivingDeathEvent`(可取消);取消 → 方法立即返回,`L1425 this.dead = true`、`L1430 dropAllDeathLoot` 全不执行** |
| `L1410` | `if (!this.isRemoved() && !this.dead) {` |
| `L1425` | `this.dead = true;` |
| `L1430` | `this.dropAllDeathLoot(serverlevel, damageSource);` |

### 取消后的"未死但血量为 0"路径(重要)

| 行号 | 代码原文 |
|---|---|
| `L480` | `if (this.isDeadOrDying() && this.level().shouldTickDeath(this)) {` |
| `L481` | `this.tickDeath();` |
| `L565-569` | `protected void tickDeath() { this.deathTime++; if (this.deathTime >= 20 && !this.level().isClientSide() && !this.isRemoved()) { this.level().broadcastEntityEvent(this, (byte)60); this.remove(Entity.RemovalReason.KILLED); } }` |
| `Level.java L625-627` | `public boolean shouldTickDeath(Entity entity) { return true; }` |

**⇒ 若某处理器取消 `LivingDeathEvent` 却*没有*把血量抬到 >0,该实体在 20 tick 后被 `remove(KILLED)` 静默移除(无 `die()`、无掉落、不再派发任何死亡事件)。** 本仓三处取消点(`ChipDamageHandler.onLivingDeath` / `EnderDiceHandler.onLivingDeath` / 原版图腾)都显式抬血,故当前**不可达**;但这是所有"取消死亡"改动的硬约束(见 A4-C2)。

## 1.2 B(forge-1.20.1)`LivingEntity.java`

### `hurt` — 段 1

| 行号 | 代码原文 | 阶段语义 |
|---|---|---|
| `L1088` | `public boolean hurt(DamageSource source, float amount) {` | |
| **`L1089`** | `if (!ForgeHooks.onLivingAttack(this, source, amount)) return false;` | **`LivingAttackEvent` 派发点(可取消)** |
| `L1090` | `if (this.isInvulnerableTo(source)) {` | |
| `L1092` | `} else if (this.level().isClientSide) {` | 客户端 |
| `L1094` | `} else if (this.isDeadOrDying()) {` | 已死 |
| `L1107-1108` | `if (amount > 0.0F && this.isDamageSourceBlocked(source)) {` / `ShieldBlockEvent ev = ForgeHooks.onShieldBlock(...);` | 盾牌 |
| `L1131` | `if ((float)this.invulnerableTime > 10.0F && !source.is(DamageTypeTags.BYPASSES_COOLDOWN)) {` | 无敌帧 |
| `L1136` | `this.actuallyHurt(source, amount - this.lastHurt);` | 差额 |
| `L1141-1142` | `this.invulnerableTime = 20;` / `this.actuallyHurt(source, amount);` | 正常 |
| **`L1147-1150`** | `if (source.is(DamageTypeTags.DAMAGES_HELMET) && ...) { this.hurtHelmet(source, amount); amount *= 0.75F; }` | **1.20.1:头盔 ×0.75 在 `actuallyHurt` *之后*——只影响击退,已扣血口径不含 ×0.75** |

### `actuallyHurt` — 段 2

| 行号 | 代码原文 | 阶段语义 |
|---|---|---|
| `L1663` | `protected void actuallyHurt(DamageSource damageSource, float damageAmount) {` | |
| **`L1665`** | `damageAmount = ForgeHooks.onLivingHurt(this, damageSource, damageAmount);` | **`LivingHurtEvent` 派发点 —— 护甲之前、1.21.1 无对应阶段** |
| `L1666` | `if (damageAmount <= 0) return;` | 被取消 → 事件 `getAmount()` 不变、`post` 返回 true → FordeHooks 返回 0 → **直接 `return`(整段伤害处理跳过)** |
| `L1667` | `damageAmount = this.getDamageAfterArmorAbsorb(damageSource, damageAmount);` | 护甲 |
| `L1668` | `damageAmount = this.getDamageAfterMagicAbsorb(damageSource, damageAmount);` | 抗性/附魔 |
| `L1669-1670` | `float f1 = Math.max(damageAmount - this.getAbsorptionAmount(), 0.0F);` / `this.setAbsorptionAmount(...)` | **吸收(黄心)结算** |
| **`L1680`** | `f1 = ForgeHooks.onLivingDamage(this, damageSource, f1);` | **`LivingDamageEvent` 派发点 —— 护甲+附魔+吸收之后;传入值 = 吸收后仍会扣的生命** |
| `L1683-1684` | `this.setHealth(this.getHealth() - f1);` / `this.setAbsorptionAmount(this.getAbsorptionAmount() - f1);` | **`setHealth`(唯一扣血点)+ 再次扣黄心** |

> ⚠️ `L1684` 是 1.20.1 原版既存行为:若监听器在 `LivingDamageEvent` 里 `setAmount(x)` 且 `x < damageAmount`,则已按"全额"扣过的黄心会被"二次扣减"成负数(`setAbsorptionAmount` 无下界钳制)。本仓无监听器增大该值,故不可达;仅作阶段口径备注。

### `die` / 取消后路径

| 行号 | 代码原文 |
|---|---|
| `L1342-1343` | `public void die(DamageSource damageSource) {` / `if (ForgeHooks.onLivingDeath(this, damageSource)) return;` ← **`LivingDeathEvent`(可取消)** |
| `L1359` | `this.dead = true;` |
| `L1366` | `this.dropAllDeathLoot(damageSource);` |
| `L416-417` | `if (this.isDeadOrDying() && this.level().shouldTickDeath(this)) { this.tickDeath(); }` |
| `L552-...` | `tickDeath()` 同构(约 20 tick 后 `remove(KILLED)`) |
| `Level.java L528` | `shouldTickDeath` → 恒 `true` |

## 1.3 跨版本阶段映射表(修正第一轮 §0 的一处不完整)

| 语义阶段 | 1.21.1(NeoForge) | 1.20.1(Forge) | 本仓对应监听器 |
|---|---|---|---|
| 无敌/已死判定**之后**、护甲之前 | `LivingIncomingDamageEvent`(可取消,`L1153`) | `LivingAttackEvent`(可取消,`L1089`) | A:`ObsidianDiceHandler@HIGH`、`DiceCombatEvents@NORMAL`(破绽闪避)、`AdrenalineChipItem@NORMAL`、`NancyLuSignItem@NORMAL`<br>B:同名三者挂 `LivingAttackEvent` |
| — | **无对应** | `LivingHurtEvent`(可改 amount,`L1665`) | B:`EnderDiceHandler@HIGH`、`DamageEffectCardHandler@NORMAL`、`ObsidianDiceHandler@NORMAL` |
| 护甲+附魔之后 | `LivingDamageEvent.Pre`(**吸收前**,`L1789`) | `LivingDamageEvent`(**吸收后**,`L1680`) | A:10 个监听方法(见 2.1)<br>B:8 个(见 2.2) |
| 扣除生命 | `L1801 setHealth` | `L1683 setHealth` | — |
| 死亡 | `LivingDeathEvent`(`L1409`) | `LivingDeathEvent`(`L1343`) | A/B 各 10 个(见 2.3/2.4) |

**A1 结论:第一轮 §0 的阶段表基本正确,本次给出完整行号并补两点:**
1. 1.20.1 的 **头盔 ×0.75 在 `actuallyHurt` 之后**(`L1147-1150`),1.21.1 在之前(`L1182-1184`)⇒ 戴头盔时两版本"进入 `LivingDamageEvent` 的数值"不同(1.21.1 已含 ×0.75,1.20.1 不含)。`AGENTS.md` 与第一轮报告都没写这一条。
2. 1.20.1 `LivingDamageEvent` 的传入值与前序吸收已解耦但 `setHealth`/`setAbsorptionAmount` 仍按 `f1` 双扣(`L1683-1684`)。

## 1.4 逐个伤害相关监听器:"它假设自己在哪个阶段"

| 监听器(文件:行号) | 事件 / 优先级 | 它假设的阶段 | 判定 |
|---|---|---|---|
| A `EnderDiceHandler.java:147-154`<br>B `EnderDiceHandler.java:147-155` | A `LivingDamageEvent.Pre@HIGH`<br>B `LivingHurtEvent@HIGH` | 注释写"在气囊判定之前完成放大" | **修正**:A 侧成立(HIGH 早于 LOWEST);B 侧成立但**基准不同**(护甲前 vs A 的护甲后);且本次改动后它调用的 `applyVictimDamageModifiers` 与骰战路径**重复消费**(见 A4-C3) |
| A `ChipDamageHandler.java:47-79`<br>B `ChipDamageHandler.java:47-78` | `LivingDamageEvent(.Pre)@LOWEST` | 注释 `A:64-67` 明确写"吸收前,故须换算 `damage - getAbsorptionAmount()`";`B:64-67` 写"本版本天然在吸收之后" | **证实**:两侧换算口径都正确,与 §1.1/§1.2 行号一致 |
| A `DamageEffectCardHandler.java:31`<br>B `DamageEffectCardHandler.java:28` | A `LivingDamageEvent.Pre@NORMAL`<br>B **`LivingHurtEvent@NORMAL`** | 类注释只写"法伤结算主链路" | **修正**:B 侧挂在护甲**之前**,同一目标在重甲下 bonus 的"真伤化"基线不同;`SpellDamageRegistry` 电击手套读取 `ctx.event.getNewDamage()`/`getAmount()` 因此跨版本不同(第一轮 A5 已记) |
| A `PaparaSignItem.java:56-62`<br>B `PaparaSignItem.java:56-62` | `LivingDamageEvent(.Pre)@LOWEST` | 假设"这是最终伤害" | **证实但同优先级竞态**:与 `ChipDamageHandler` 同为 LOWEST,A 侧注册顺序 `ChipDamageHandler(95) → PaparaSignItem(231)`;顺序反转会把"被气囊归零的伤害"也算成治疗量(见 2.5 G2) |
| `DiceCombatEvents.java:190(A)/186(B)` | `LivingDamageEvent(.Pre)@NORMAL` | 假设自己是"接管最终伤害"的唯一写入方(`:610`/`:607` `setNewDamage/setAmount`) | **证伪其唯一性**:`EnderDiceHandler@HIGH` 会先乘倍率,但同阶段 `DiceCombatModifiers.applyVictimDamageModifiers` 又消费一次(见 A4-C3) |
| `DiceCombatEvents.java:907(A)/901(B)`(狂暴) | `LivingDamageEvent(.Pre)@LOW` | 注释 `A:900` "伤害放大须先于 ChipDamageHandler(安全气囊,LOWEST)" | **证实**:LOW > LOWEST,顺序成立且是显式契约 |
| `DiceCombatEvents.java:953(A)/947(B)`(虚弱印记) | `LivingDamageEvent(.Pre)@LOW` | 同上 | **证实** |
| A `DiceCombatEvents.java:1095-1108`、`AdrenalineChipItem.java:86-102`、`NancyLuSignItem.java:294-303` | `LivingIncomingDamageEvent@NORMAL` ×3 | 前两者假设"取消 = 闪避"(`applyDodgeCancel`) | **证实取消语义正确**;三者同优先级无先后契约(见 2.5 G4) |
| B `ObsidianDiceHandler.java:28-...` | `LivingHurtEvent@NORMAL` | 假设"火焰伤害在此改 amount" | **修正**:与 `DamageEffectCardHandler` 同事件同优先级,顺序无契约(第一轮 A11) |
| `PlayerHostilityTracker.java:50`(并发新增) | `LivingDamageEvent(.Pre)@NORMAL` | 只记录"谁打过谁",不改数值 | **证实无害**;但它在 A 的 `LivingDamageEvent.Pre@NORMAL` 组内新增了第 5 个成员(见 2.1) |

---

# 第二部分(A2/A3):同优先级订阅者普查

**结论前提(两条原版事实)**:
1. 两个加载器都**严格按优先级排序**(`EventPriority` 枚举序 `HIGHEST→HIGH→NORMAL→LOW→LOWEST`,同优先级内按注册顺序追加):
   - Forge:`ListenerList.java:254-257` `Arrays.stream(EventPriority.values()).forEach(value -> { ... ret.add(value); });` + `:265-268 register(...) { priorities.get(priority.ordinal()).add(listener); }`
   - NeoForge:`EventPriority.java` 枚举序 + `ListenerList.register`
2. **被取消的事件默认不再投递给后续监听器**(`@SubscribeEvent.receiveCanceled` 默认 `false`):
   - NeoForge:`SubscribeEventListener.java:49` `if (subInfo.receiveCanceled() || !((ICancellableEvent) event).isCanceled()) { handler.invoke(event); }`
   - Forge:`ASMEventHandler.java:69` `if (!event.isCancelable() || !event.isCanceled() || subInfo.receiveCanceled())`
   ⇒ **`if (event.isCanceled()) return;` 这类检查在自己的监听器里几乎永远不可达**(只有 `receiveCanceled=true` 才可达),真正的保护是总线层的过滤。这一点决定了 A4 的全部结论。

## 2.1 A(neoforge-1.21.1)`LivingDamageEvent.Pre` 同名同优先级组(**共 10 个监听方法**)

| 优先级 | 全部订阅者(注册顺序 = jar 条目索引) |
|---|---|
| HIGH(1) | `EnderDiceHandler:147`(idx 100) |
| NORMAL(5) | `DiceCombatEvents:190`(idx 27) → `PlayerHostilityTracker:50`(新增) → `DamageEffectCardHandler:31`(97) → `NancyLuSignItem:262`(228) → `DiceCombatEvents:1122`(Pandaman 嘲讽反击,同一类 27) |
| LOW(2) | `DiceCombatEvents:907`(狂暴) → `DiceCombatEvents:953`(虚弱印记) |
| LOWEST(2) | `ChipDamageHandler:47`(95) → `PaparaSignItem:56`(231) |

**同组隐含先后依赖(有 → 危险组)**:
- **NORMAL 组内**:`DiceCombatEvents.onLivingDamagePre`(骰战总控,覆盖式 `setNewDamage`)是唯一的**写值方**;`DamageEffectCardHandler` / `NancyLuSignItem:262` / `PlayerHostilityTracker:50` 都只读(后者完全不读数值)。因此组内真正的顺序敏感点是"`DiceCombatEvents` 写值必须早于任何依赖最终值的消费者"——当前它排在组内**最前**(jar idx 27),但这**不是契约**;`DiceCombatEvents:1122`(Pandaman 嘲讽反击,同类)反而排在组内最后。
- **LOWEST 组内**:`ChipDamageHandler`(把致命伤归零/磨刀石改值)必须**早于** `PaparaSignItem`(按最终伤害回血)。**顺序反转后果**:被气囊无效化的致命一击仍给吸血鬼玩家回 `max(1, 原始伤害/2)` 血 → "被救活还白回一口血";磨刀石减伤后也照样按未减免值回血。

## 2.2 B(forge-1.20.1)`LivingDamageEvent` 同名同优先级组(**共 8 个监听方法**)

| 优先级 | 全部订阅者(注册顺序) |
|---|---|
| NORMAL(4) | `DiceCombatEvents:186`(idx 546) → `PlayerHostilityTracker:50`(新增) → `NancyLuSignItem:264`(756) → `DiceCombatEvents:1132`(Pandaman 嘲讽反击,同类 546) |
| LOW(2) | `DiceCombatEvents:901`(狂暴) → `DiceCombatEvents:947`(虚弱印记) |
| LOWEST(2) | `ChipDamageHandler:47`(624) → `PaparaSignItem:56`(759) |

**与 A 的差异**:B 的 `DamageEffectCardHandler` / `EnderDiceHandler` / `ObsidianDiceHandler` 全部不在本组(前者挂 `LivingHurtEvent`,后两者 HIGH/另一事件),因此 **B 的 `LivingDamageEvent` 组没有"骰战写入 vs 别的写入"竞争**;危险组只剩 LOWEST 的"气囊 vs 吸血鬼回血"。

## 2.3 `LivingDeathEvent` 组(A/B 同构,10 个监听器)

| 优先级 | A | B |
|---|---|---|
| HIGHEST | `ChipDamageHandler:88`(95) | `ChipDamageHandler:87`(624) |
| NORMAL(8 个,顺序=jar 索引) | `EnderDiceHandler:157`(100) → `InvestigationEventUtil:107`(127) → `BonnieSignItem:132`(218) → `HaiqingSignItem:126`(221) → `CursedSwordChipItem:118`(174) → `ElectricSwordChipItem:75`(180) → `SmartWatchChipItem:47`(203) → `SatelliteChipItem:98`(201) | `EnderDiceHandler:157`(629) → `InvestigationEventUtil:104`(654) → `BonnieSignItem:131`(746) → `HaiqingSignItem:125`(749) → `CursedSwordChipItem:116`(702) → `ElectricSwordChipItem:75`(708) → `SmartWatchChipItem:47`(731) → `SatelliteChipItem:99`(729) |
| LOWEST | `PlayerLifecycleHandler:116`(109) | `PlayerLifecycleHandler:113`(638) |
| LOWEST(新增) | `PlayerHostilityTracker:63`(新增) | `PlayerHostilityTracker:63`(新增) |

**同组隐含先后依赖**:
1. `ChipDamageHandler@HIGHEST`(取消死亡 + 抬血)**必须早于**全部 NORMAL/LOWEST —— ✅ 由优先级保证(契约成立)。
2. `EnderDiceHandler@NORMAL`(取消死亡 + `setHealth(1.0F)`)**必须早于** `PlayerLifecycleHandler@LOWEST`(死亡清理)—— ✅ **改动后由优先级保证**;改动前两者同为 NORMAL,**无契约**。
3. `EnderDiceHandler@NORMAL` **必须早于**同组其余 7 个"击杀发奖/推进阶段"监听器 —— ❌ **无契约**。今天之所以"看起来对",纯粹因为 `EnderDiceHandler` 的 `.class` 条目索引(100/629)小于那 7 个监听器;这 7 个监听器**都不检查 `isCanceled()`**(即使检查也救不了,见 2.5 末尾说明)。
4. `PlayerLifecycleHandler@LOWEST` 与 `PlayerHostilityTracker@LOWEST`(新增)同优先级 → 死亡清理与敌对名单清理的顺序无契约;两者互不读写同一状态,**当前安全**。

## 2.4 其余事件的分组普查(仅列 >1 订阅者的组)

| 事件 @ 优先级 | A 全部订阅者 | B 全部订阅者 | 隐含先后依赖? |
|---|---|---|---|
| `LivingIncomingDamageEvent`@NORMAL / B 为 `LivingAttackEvent`@NORMAL | `AdrenalineChipItem:86`(160) → `DiceCombatEvents:1095`(27) → `NancyLuSignItem:294`(228) | `AdrenalineChipItem:87`(688) → `DiceCombatEvents:1109`(546) → `NancyLuSignItem:296`(756) | **有(弱)**:三者都可能 `setCanceled(true)`。第一个取消后,后两者仍会执行(它们不看 `isCanceled()`):<br>A:肾上腺素闪避后 `onMosesBrokenDodge` 仍可能注入反击(它只在自身条件满足时取消,不检查"已被闪避");`NancyLuSignItem` 的末影珍珠免疫仍会置位(无害)。<br>**反转后果**:若 `NancyLuSignItem` 排最前且本次是末影珍珠摔落伤 → 取消 → 肾上腺素/枪匠仍按"受到攻击"处理并可能注入反击(**闪避不消耗层数却打出反击**) |
| `Expired`(`MobEffectEvent.Expired`)@NORMAL | `DiceCombatEvents:839` → `HaiqingSignItem:142` → `MarkManager:69` → `PandamanSignItem:127` | `DiceCombatEvents:834` → `HaiqingSignItem:142` → `MarkManager:67` → `PandamanSignItem:128` | 各判各自效果,**无共享状态**。仅 `MarkManager:69` 有注释说明原版会读 `post(Expired).isCanceled()`;本仓无取消者 ⇒ 安全 |
| `Remove`(`MobEffectEvent.Remove`)@NORMAL | `InvestigationEventUtil:122`(127) → `ModEffectEvents:149`(104) | `InvestigationEventUtil:119`(654) → `ModEffectEvents:145`(633) | 两者都只读各自的计时器记录,**无依赖** |
| `Added`(`MobEffectEvent.Added`)@NORMAL | `FriendshipBadgeChipItem:75`(185) → `ModEffectEvents:135`(104) | `FriendshipBadgeChipItem:76`(713) → `ModEffectEvents:131`(633) | 无依赖(一个发治愈,一个记计时器) |
| A `ServerTickEvent.Post` / B `ServerTickEvent` @NORMAL | A:`MarkManager:90` + `RailgunStrikeScheduler:121` | B:`MarkManager:88` + `RailgunStrikeScheduler:121`(两者都有 `if (event.phase != TickEvent.Phase.END) return;`) | 无依赖 |
| `SignActiveTriggeredEvent`@NORMAL | 8 个立牌(见 3.1 表第 8 行族) | 同 8 个 | **有(弱,见 2.5 G3)**:8 个监听器都无条件进入各自的 `onSignActiveTriggered`;顺序决定"谁能先扣冷却/先消费状态" |
| `PlayerEvent.Clone`@NORMAL/LOWEST | A:仅 `PlayerLifecycleHandler:168@LOWEST` | B:`ModCapabilities:37@NORMAL` + `PlayerLifecycleHandler:164@LOWEST` | **有(显式契约)**:`PlayerLifecycleHandler` 注释要求"最后执行"(附件克隆/复制在前),LOWEST 正确 ✅ |
| `PlayerTickEvent`@NORMAL(仅 B) | A:`PlayerTickEvent.Pre` 与 `.Post` 是两个**不同事件类** | B:同一事件类的 2 个监听器,且**每 tick 各跑 2 次**(见第五部分) | **有重大缺陷**(A6,见第五部分) |
| B `ClientTickEvent`@NORMAL | A:`ClientTickEvent.Post` 单阶段 | B:`ClientTickEvent` ×2,START+END 双跑 | 同 A6 |
| B `ServerTickEvent`@NORMAL | — | `MarkManager:88` + `RailgunStrikeScheduler:121` 各自 `if (event.phase != TickEvent.Phase.END) return;` ✅ | 已正确过滤 |

## 2.5 同优先级危险组清单(最终)

| 组 | 危险点 | 顺序反转/今天的实际后果 | 级别 |
|---|---|---|---|
| **G1** `LivingDeathEvent@NORMAL`(8 个) | "保命取消"必须先于"击杀发奖/阶段推进";7 个发奖监听器**全部缺 `isCanceled()`** | 取消在 `EnderDiceHandler`(同组**排最前**)内部发生,bus 逐监听器过滤 ⇒ 排在其后的 7 个监听器**今天确实被跳过**;但这是 `.class` 条目顺序的偶然结果 | 高 |
| **G2** `LivingDamageEvent(.Pre)@LOWEST`(2 个) | 气囊/磨刀石改值必须先于吸血鬼按最终伤害回血 | 今天顺序正确(idx 95 < 231;624 < 759);反转 ⇒ 被无效化的致命伤照样回血 | 中 |
| **G3** `SignActiveTriggeredEvent@NORMAL`(8 个) | 8 个立牌监听器无条件进入;谁先扣冷却/谁先消费状态无契约 | 目前每个立牌都自查 `isEquipped`,互不干扰;但"同一玩家同时戴多枚立牌 + `handleSignActive` 内改玩家级冷却"这一组合无契约 | 低 |
| **G4** `LivingIncomingDamageEvent/LivingAttackEvent@NORMAL`(3 个) | 三个取消者/反击者无先后契约 | 反转 ⇒ 末影珍珠免疫与破绽闪避同 tick 互相越过(闪避层数不消耗却打反击) | 中 |
| **G5** B `LivingHurtEvent@NORMAL`(2 个) | 火焰 ×0.3 与法伤加成同阶段无契约 | 与 A 的"两事件分离"不对称;顺序变化只影响火焰+法伤同击的少见组合 | 低 |

> ⚠️ **G1 的语义必须精确界定**:`ListenerList` 在同一 `post()` 内按 forEach 顺序逐个调用监听器,而"是否跳过"是在**每个监听器的包装器里**判断的:
> - NeoForge `SubscribeEventListener.java:49` `if (subInfo.receiveCanceled() || !((ICancellableEvent) event).isCanceled()) { handler.invoke(event); }`
> - Forge `ASMEventHandler.java:69` `if (!event.isCancelable() || !event.isCanceled() || subInfo.receiveCanceled())`
>
> 因此 `setCanceled(true)` **确实会**让**注册在其之后**的监听器被跳过(这与事件是否 `@Cancelable` 无关,只与 `isCanceled()` 的当前值有关)。
> **推论(本组真正的病情)**:同优先级内"谁被跳过我"完全由 `.class` 条目顺序决定 ⇒
> **一旦某个发奖监听器排在 `EnderDiceHandler` *之前*,它就会在"已保命"的死亡上照常发奖**(星币/战斗牌/推进调查阶段),
> 而它自己**没有任何办法知道**这次死亡已被取消(它的 `isCanceled()` 检查写在方法体里,但那时 `isCanceled()` 还是 `false`)。
> 这就是"缺 `isCanceled()`"与"顺序无契约"叠加后的真实缺陷形态:**不是"今天必错",而是"安全边界完全寄托于构建产物顺序"**。
> **最小实机验证**:装末影骰子 + 秘密侦探立牌 + 电剑筹码,让一只敌对生物打死你(不还手),观察是否拿到战斗牌/星币(不应拿到)。
> 再故意新增一个按字典序排在 `EnderDiceHandler` 之前的 `@EventBusSubscriber` 类后重建、重测 —— 若开始发奖即证实。

---

# 第三部分(A4):取消语义普查

## 3.1 全部 `setCanceled(true)` 调用点(两版本各 8 处)

| # | 文件:行号(事件) | 代码原文 | 取消语义 |
|---|---|---|---|
| 1 | A `ChipDamageHandler.java:98` / B `:97`(`LivingDeathEvent`) | `event.setCanceled(true);` 紧接 `player.setHealth(Math.max(1.0F, player.getHealth()));` | ✅ 取消 + **抬血**(避免 §1.3 的静默移除) |
| 2 | A `EnderDiceHandler.java:168` / B `:168`(`LivingDeathEvent`) | `event.setCanceled(true);` 紧接 `player.setHealth(1.0F);` | ✅ |
| 3 | A `DiceCombatEvents.java:1077` / B `:1071`(`LivingIncomingDamageEvent` / `LivingAttackEvent`,由 `applyDodgeCancel` 调用) | `event.setCanceled(true);` 后显式补发 `BaseSignItem.invokeHurtHooks(player, event.getAmount());` / `BufferShieldChipItem.onHurt(...)` | ✅ 最前置取消,理由充分(注释 `A:1050-1066`/`B:1045-1064`) |
| 4 | A `NancyLuSignItem.java:301` / B `:303`(`LivingIncomingDamageEvent` / `LivingAttackEvent`) | `if (player.level().getGameTime() < ModAttachments.getNancyLuEnderPearlImmuneUntil(player)) { event.setCanceled(true); }` | ✅ |
| 5 | A `DiceCombatEvents.java:809,815,827` / B `:804,810,822`(`LivingChangeTargetEvent`) | 骇客隐身 / 调查阶段 / 暗影突袭 三处取消索敌 | ✅(消费者是自己,无下游) |
| 6 | A `ModEffectEvents.java:124,129` / B `:120,125`(`MobEffectEvent.Remove`,HIGH) | 按 `astral_dice:` 命名空间阻止移除 | ⚠️ 见 3.3 |
| 7 | A `NancyLuClientEvents.java:42,50` / B `:43,51`(`RenderPlayerEvent.Pre` / `RenderHandEvent`) | 纯客户端渲染抑制 | ✅ |
| 8 | A `NancyLuSignItem.java:301` 已计 | — | — |

**缺失检查者(在动手前应看 `isCanceled()` 却没看)** —— 注意:因 §第二部分前提 2(**总线逐监听器过滤已取消事件**)",这些缺失**只在"取消者排在自己前面"时才成为真实缺陷**;按当前 jar 顺序多为"顺序依赖型隐患",而非今天必错。

| 事件 | 缺失 `isCanceled()` 的订阅者 | 现有唯一取消者 | 风险方向 |
|---|---|---|---|
| `LivingDeathEvent`(8 个 NORMAL) | `InvestigationEventUtil:107/104`、`BonnieSignItem:132/131`、`HaiqingSignItem:126/125`、`CursedSwordChipItem:118/116`、`ElectricSwordChipItem:75`、`SmartWatchChipItem:47`、`SatelliteChipItem:98/99` | `EnderDiceHandler:168@NORMAL`(同优先级)、`ChipDamageHandler@HIGHEST`(已由 HIGHEST 保护) | **发奖/推进阶段绕过保命**;`EnderDiceHandler` 今天排最前故暂不发作 |
| `LivingDamageEvent(.Pre)@LOWEST` | `PaparaSignItem:57`(仅读 `event.getNewDamage()`,不检查取消) | 同组无人 `setCanceled`(只有 `setNewDamage`),**事件本身不可取消** ⇒ 缺 `isCanceled()` 无意义 | — |
| `LivingDamageEvent(.Pre)@NORMAL` | `NancyLuSignItem:262/264`(直接 `onAttackWhileHidden`,不看取消) | 无取消者(事件不可取消) | — |
| `LivingIncomingDamageEvent/LivingAttackEvent@NORMAL` | `DiceCombatEvents:1095/1109`、`AdrenalineChipItem:86/87` **都没有** `isCanceled()` 前置 | `NancyLuSignItem:294/296`(可能先取消) | **G4**:末影珍珠免疫先取消 → 枪匠/肾上腺素仍按"被攻击"处理 |
| `LivingUseTotemEvent` | 无缺失(`EnderDiceHandler:204/203` 有检查) | — | — |
| `MobEffectEvent.Remove` | 无缺失(`InvestigationEventUtil:122/119`、`ModEffectEvents:149/145` 都有) | — | — |
| `PlayerEvent.ItemCraftedEvent` / `BlockEvent.BreakEvent` / `ProjectileImpactEvent` / `EntityTeleportEvent.*` | 各自唯一订阅者均不检查 | 无模组取消者 | 低(仅第三方模组可取消) |

## 3.2 逐条结论(§I 格式)

```text
ID: A4-C1 死亡清理 vs 保命取消 —— 改动后的状态复核
核验: 证实(改动后已修)
现象: (改动前)末影骰把玩家从死亡救回后,该玩家仍被按"真死"清理:玻璃骰子整格销毁(连 WEAPON_ENHANCEMENT 里
      的全部卡牌)、治愈清零、赐福/出牌轮/立牌待命状态全部清空。气囊路径因 HIGHEST 取消而不受影响。
顺序依赖: 有契约需求——「保命方(取消死亡)」必须先于「死亡清理」。
      改动前:两者同为 NORMAL,顺序 = .class 条目顺序;A: EnderDiceHandler(idx100) 在 PlayerLifecycleHandler(idx109) 之前
      ⇒ 今天"看起来安全";B: EnderDiceHandler(629) 在 PlayerLifecycleHandler(638) 之前 ⇒ 同上。
      但**新加入的 PlayerHostilityTracker(id 未定)或任何新 @EventBusSubscriber 类都可能插到前面**。
      反转后:被救回玩家的死亡清理照常执行(玻璃骰销毁),且因总线过滤语义,清理排在取消者之后才会被跳过——
      即"取消者排最后"是唯一安全位置。
证据: A(neoforge-1.21.1)
  - event/PlayerLifecycleHandler.java:116  @SubscribeEvent(priority = EventPriority.LOWEST)
  - event/PlayerLifecycleHandler.java:117  public static void onPlayerDeathClearEffects(LivingDeathEvent event) {
  - event/PlayerLifecycleHandler.java:121  if (event.isCanceled()) return;
  - event/EnderDiceHandler.java:157  @SubscribeEvent   ← 默认 NORMAL
  - event/EnderDiceHandler.java:168  event.setCanceled(true);
  - combat/DiceCombatEvents.java:1095-1108  (同组发奖/反击)
  B(forge-1.20.1)
  - event/PlayerLifecycleHandler.java:113  @SubscribeEvent(priority = EventPriority.LOWEST)
  - event/PlayerLifecycleHandler.java:118  if (event.isCanceled()) return;
  - event/EnderDiceHandler.java:157/168  同上
可达性: 可达。最小实机:佩戴末影骰子 → 让敌对生物把你打到 0 血(不还手)→ 观察玻璃骰子是否消失、
      治愈点数是否归零。改动后应为「保留」。
修法: 已由本轮改动(PlayerLifecycleHandler → LOWEST)解决,方向正确。**残余风险**(评估该改动是否引入新问题):
      ① LOWEST 与新增的 PlayerHostilityTracker:63(LOWEST)同优先级,两者无共享状态,安全;
      ② LOWEST 使清理晚于**所有** NORMAL 发奖监听器 —— 即在"死亡**未被**取消"的真实死亡路径上,
         发奖监听器先跑、清理后跑,顺序与改动前**相反**。因为真实死亡时"发奖"读的是被击杀者(不是自己),
         不受影响;但若将来出现"玩家死亡时给自己发奖"的逻辑,它会先于清理执行(清理不再能挡住它)。
         建议:把清理保留在 LOWEST 的同时,明确「LOWEST = 本文件的专用层」并加注释。
      ⚠️ 需用户定夺:是否把"击杀发奖/阶段推进"一并移到 LOW(语义上=「必须晚于保命判定」),
         而不是继续依赖 .class 顺序。
```

```text
ID: A4-C2 取消死亡但不抬血 ⇒ 20 tick 后静默移除
核验: 证实(机制证实;当前不可达)
现象: 任何"取消 LivingDeathEvent 但不把血量抬到 >0"的处理器,会让实体在 20 tick 后被
      Level#shouldTickDeath(恒 true)驱动的 tickDeath() 以 RemovalReason.KILLED 移除:
      不派发 die()、不掉落战利品、不派发第二次死亡事件。玩家会"原地消失"而不是死亡。
顺序依赖: 取消死亡 ⇒ 必须在同一处理器内 setHealth(≥1);否则与后续 tick 的 tickDeath 构成竞态。
证据: A: world/entity/LivingEntity.java:480-481 (isDeadOrDying && shouldTickDeath → tickDeath),
        :565-569 (deathTime++ / >=20 → remove(Entity.RemovalReason.KILLED));
        world/level/Level.java:625-627 public boolean shouldTickDeath(Entity entity) { return true; }
      B: world/entity/LivingEntity.java:416-417 / :552-... 同构;world/level/Level.java:528 同。
      本仓三处取消均抬血:A ChipDamageHandler.java:98-99 / EnderDiceHandler.java:168-169(及 B 侧同)
可达性: 当前不可达(三处都抬血)。属**回归护栏**:将来新增"保命"类处理器时必踩。
最小实机: 临时把 ChipDamageHandler.java:99 注释掉 → 用气囊挡一次致命伤 → 观察玩家是否在 1 秒后
      被静默移除(无死亡画面、无掉落)。
修法: 在 BaseSignItem/保命工具的公共入口里加断言或抽取 `cancelDeathAndRevive(player, minHealth)` 单一入口。
```

```text
ID: A4-C3 【改动中新引入】受击侧伤害修饰器被消费两次 ⇒ 雨中/水下 ×1.4 实际 ×1.96
核验: 证实(源码层面成立;需实机确认数值)
现象: 同时"佩戴末影骰子 + 处于雨中/水下 + 被玩家攻击且对方触发骰战结算"时,伤害倍率 1.4 被应用两次
      (1.4×1.4=1.96)。玩家在雨中被打出远高于预期的伤害。
顺序依赖: EnderDiceHandler@HIGH 先把注册表乘进事件值(第 1 次);DiceCombatEvents@NORMAL 随后
      `DiceCombatModifiers.applyVictimDamageModifiers(...)` 再乘一次(第 2 次),然后 setNewDamage/setAmount
      覆盖式写入 ⇒ 第 1 次的乘法没有"被丢弃"(第一轮 A3 想修的就是这个),而是**与第 2 次叠加**。
证据: A(neoforge-1.21.1)
  - event/EnderDiceHandler.java:147  @SubscribeEvent(priority = EventPriority.HIGH)
  - event/EnderDiceHandler.java:152-153  event.setNewDamage((float) DiceCombatModifiers.applyVictimDamageModifiers(
                                             entity, event.getSource(), event.getNewDamage()));
  - combat/DiceCombatEvents.java:608  finalDmg = DiceCombatModifiers.applyVictimDamageModifiers(target, source, finalDmg);
  - combat/DiceCombatEvents.java:610  event.setNewDamage((float) finalDmg);
  - combat/DiceCombatModifiers.java:487-493  registerVictimDamageModifier((victim, source, damage) -> { ...
                                             return damage * EnderDiceHandler.RAIN_WATER_DAMAGE_MULTIPLIER; });
  B(forge-1.20.1)
  - event/EnderDiceHandler.java:147  @SubscribeEvent(priority = EventPriority.HIGH)   (LivingHurtEvent)
  - event/EnderDiceHandler.java:152-153  同上
  - combat/DiceCombatEvents.java:605  同 A
  - combat/DiceCombatEvents.java:607  event.setAmount((float) finalDmg);
可达性: 可达。最小实机:双人 PvP;A 佩末影骰子并潜入水中(或站在雨中),B 为触发骰神赐福的攻击者,
      B 打 A → 记录 A 掉血。对照:关掉骰战(无赐福)再打一次。两次都应是 1.4×(正常值);
      若第一次 = 1.96×,即命中本缺陷。
      (1.21.1 与 1.20.1 都需测:两版本代码同构,但挂的事件阶段不同。)
修法(二选一,需口径定夺):
      ① 保留注册表为**唯一消费点**,删掉 EnderDiceHandler 的 HIGH 监听器(风险:未触发骰战的伤害源
         如摔落/环境/怪物普通攻击也不再有 +40%;但骰战只接管"玩家攻击"分支,环境伤害不经过
         DiceCombatEvents:L200 的 `!(directEntity instanceof Player) return` ⇒ **会丢掉环境伤害的 +40%**);
      ② 保留 HIGH 监听器覆盖所有来源,把骰战路径的 `applyVictimDamageModifiers` 改为"只对**未经 HIGH 处理**
         的伤害源消费"(需要一个"已消费"标记或把该乘子移出骰战路径)。
      ③ 推荐:把该乘子从"受击侧注册表"改回**受击侧事件的唯一消费点**(即删掉 DiceCombatEvents:608/605),
         并接受 A3 的"被覆盖式写入丢弃"——因为 A3 的真实伤害来源是**同一个事件内**的 HIGH→NORMAL 覆盖,
         而 `applyVictimDamageModifiers` 放在 `setNewDamage` **之前**只是把顺序问题变成了重复问题。
```

```text
ID: A4-C4 死亡清理"半途而废"引出的顺序暗坑(改动评估)
核验: 证实(改动内容评估)
现象: 本轮改动删除了 PlayerLifecycleHandler 的 23 项附件归零(理由:S4-C6「死亡不复制附件 ⇒ 写了也没人读」)。
      该理由对**真实死亡路径**成立,但玩家可能因 §1.3 的 tickDeath 路径被"静默移除"(不改血量)——
      此时附件**不复制也不清零**,而是随实体一并销毁;重登/重生后仍是新实体 ⇒ 结论仍成立。
顺序依赖: 死亡清理不再清理附件 ⇒ 若将来任何附件键被加入 copyOnDeath,该清理清单必须同步恢复,
      否则"死亡被取消"分支(本清单唯一生效的分支)会留下脏状态。
证据: A: event/PlayerLifecycleHandler.java:116-121 + 已删除段的注释 :135;
      B: event/PlayerLifecycleHandler.java:113-118 + :132。
      DeathPreservedBonuses 的复制集合见 docs/interaction-audit-1.2.1.md S4-C6。
可达性: 不可达(当前无新增 copyOnDeath 键)。
修法: 在 PlayerLifecycleHandler 顶部保留一条"新增 copyOnDeath 键 ⇒ 必须回到此处加清理"的注释。
```

## 3.3 `MobEffectEvent.Remove` 的 HIGH 取消(归类备注)

A `ModEffectEvents.java:110-131` / B `:106-127`:按 `astral_dice:` 命名空间 `setCanceled(true)`。其消费者 A `InvestigationEventUtil:122` / B `:119`(`onUndercoverRemoved`)**有** `isCanceled()` 检查(但因总线过滤实际不可达),而 A `ModEffectEvents:149` / B `:145`(`onEffectTimerForget`)也在同事件同优先级 —— **后者不看 `isCanceled()`**。

- **反转后果**:若 `onEffectTimerForget` 排在 `onModEffectRemovalPrevented` 之前,被"阻止移除"的效果仍会把计时器记录删掉 ⇒ `EffectTimerGuard` 失去对本模组效果的结束时刻记录,效果可能被重新施加(A10 的同一族问题)。
- 当前顺序:A `onModEffectRemovalPrevented`(idx 104)在 `onEffectTimerForget`(104)之后?—— 两个方法同在一个类里,`getDeclaredMethods()` 的顺序不保证。**这是本组最不确定的一处**(见第六部分)。

---

# 第四部分(A5 + §G4):嵌套派发与重入

## 4.1 嵌套触发点清单

| # | 嵌套点(文件:行号) | 代码原文 | 重入的是哪个事件链 | 保护机制 |
|---|---|---|---|---|
| N1 | A `DiceCombatEvents.java:652` / B `:649`(大当家溅射) | `victim.hurt(splashSource, splashDmg);` | 完整 `hurt` 链 → `LivingIncomingDamageEvent` / `LivingDamageEvent` / 后续 `LivingDeathEvent` | `aoeProcessing`(`A:134`/`B:130`,**非 ThreadLocal 的 static boolean**)+ `try/finally`(`A:631/678`、`B:628/675`);主目标额外 `invulnerableTime=0` 保存/恢复(`A:649-655`/`B:646-652`) |
| N2 | A `DiceCombatEvents.java:1143` / B `:1153`(反击) | `attacker.hurt(ModDamageTypes.diceDamage(attacker.level(), player), (float) dmg);` | 同上 | `counterDepth`(`A:143`/`B:139`,**非 ThreadLocal**)+ `isInCounterChain()`(`A:146`/`B:142`)+ 三个入口前置判定(破绽闪避 `A:1103`、嘲讽反击 `A:1121`、骰战 `A:200`)+ `injectCounterDamage` 自身再入拒绝(`A:1141`/`B:1151`) |
| N3 | A `SpellDamageRegistry.java:259`(定向爆破 AOE 内 `e.hurt(blastSource, aoeDamage)`)/ B 同 ;A `:377`(电击手套 AOE) | `e.hurt(...)` | 同上 | `DiceCombatEvents.aoeProcessing = true` + `try/finally`(A `:374-382`) |
| N4 | A `DamageEffectCardHandler.java:65-66` / B `:62-63`(法伤加成真伤) | `target.hurt(ModDamageTypes.trueDamage(...), (float) bonus);` | 同上(且**会再次进入本处理器**) | `ThreadLocal<Boolean> APPLYING_TRUE_BONUS`(`A:29`) + `try/finally`(`:63-69`) ✅ **本仓唯一的 ThreadLocal 门闩** |
| N5 | A `EntityThunderHitMixin.java:61` / B 同 | `self.hurt(ModDamageTypes.trueDamage(level), bolt.getDamage());` | 独立真伤;`true_damage` 已登记 `bypasses_cooldown`(新增 `data/minecraft/tags/damage_type/bypasses_cooldown.json`) | 无(单次注入) |
| N6 | A `EffectCardItem.java:29` / B 同 | `user.hurt(ModDamageTypes.diceDamage(level, user), 8.0f);` | 自伤(王之力) | 无 |
| N7 | A `CrimsonDiceHandler.java:64` / B 同 | `roller.hurt(ModDamageTypes.diceDamage(roller.level(), roller), SELF_DAMAGE_ON_ONE);` | 自伤(骰出 1) | 在 `injectCounterDamage` 链内时被 `counterDepth>0` 挡住(`A:200` 骰战闸门) |
| N8 | A `EnderDiceHandler.java:169` / B `:160`(保命 `setHealth`) | `player.setHealth(1.0F)` | **不派发**任何事件(阶段 3 已过) | — |
| N9 | A `ChipDamageHandler.java:99` / B `:98` | `player.setHealth(Math.max(1.0F, player.getHealth()));` | 同上 | — |

## 4.2 逐条结论

```text
ID: A5-C1 溅射嵌套 hurt 会二次执行"每个监听器都跑一遍"的受击联动(重入面已被闸门覆盖一半)
核验: 证实
现象: 大当家溅射(`A:652`/`B:649`)在同一 tick 内对同一/victim 触发第二次完整伤害链。
      被 aoeProcessing 挡住的是 DiceCombatEvents 自己的骰战分支(`A:200`/`B:196`),**没被挡住**的是:
      ① ChipDamageHandler(气囊/磨刀石)—— 溅射若致命,会对**第二个目标**独立消耗一次气囊充能(语义正确);
      ② PaparaSignItem 回血 —— 溅射命中带汲取的玩家会按溅射伤害回血(语义正确);
      ③ DamageEffectCardHandler(法伤加成)与 NancyLuSignItem(骇客解除隐身)—— 若溅射伤害源被判为法伤/命中,
         会对同一受害者**再跑一次**(`APPLYING_TRUE_BONUS` 只覆盖"自己发起的那一次",溅射的伤害源是
         `true_damage` 且 `getEntity()==player`,会被 `isBlessingTarget` + `isSpellDamage` 重新判定)。
顺序依赖: `aoeProcessing` 必须在**进入第一次 hurt 之前**置位、在 `finally` 复位;当前实现正确。
      但它是 **static boolean 而非 ThreadLocal**(`A:134`/`B:130`),依赖"单服务端线程"假设。
证据: A: combat/DiceCombatEvents.java:134 `static boolean aoeProcessing = false;`
        :631 `aoeProcessing = true;` / :678 `aoeProcessing = false;` / :652 `victim.hurt(splashSource, splashDmg);`
      B: :130 / :628 / :675 / :649
可达性: 可达(大当家赐福满层 + 3 个敌对目标贴身)。
最小实机: 佩大当家立牌 + 气囊筹码,把 3 只僵尸堆在 1 格内,触发溅射 → 观察本次溅射是否消耗
      多次气囊充能(每个受害者各一次,属预期)以及自己是否吃到两次"受击"类联动。
修法: ① 把 `aoeProcessing`/`counterDepth` 改 `ThreadLocal`(或 `static` + 断言主线程)以匹配真实契约;
      ② 若确认"溅射不应再次触发受击联动",在溅射前设置一个同类闸门供 ChipDamageHandler/PaparaSignItem 读取。
```

```text
ID: A5-C2 反击链的递归截断在结构上成立,但闸门同样是非线程安全的 static
核验: 证实(机制正确) / 修正(线程假设)
现象: `injectCounterDamage`(`A:1140`/`B:1150`)、破绽闪避(`A:1095`)、嘲讽反击(`A:1116`)、骰战(`A:200`)
      四处入口都判定 `isInCounterChain()`,且 `counterDepth` 用 `try/finally` 只减不置零 ⇒ 反击→闪避→反击
      的循环在结构上不可能(不是"限制深度")。**判定结论与第一轮 A17 一致**。
顺序依赖: 无显式优先级需求(四个入口跨两个事件类)。`injectCounterDamage` 内部先 `counterDepth++`,
      再算伤害(算伤害过程本身可能自伤 → 见 N7),因此自伤不会再触发反击 —— 顺序正确。
证据: A: combat/DiceCombatEvents.java:143 `private static int counterDepth = 0;`
        :1141 `if (isInCounterChain()) return;` / :1146 `counterDepth++;` / :1155 `counterDepth--;`
      B: :139 / :1151 / :1156 / :1165
可达性: 不可达(递归本身被截断)。
修法: 与 A5-C1 合并:两个闸门都改 ThreadLocal + 主线程断言。
```

```text
ID: A5-C3 法伤真伤递归有正确保护;但"跳过列表"同时使加成者不被计入(设计备注)
核验: 证实(保护正确)
现象: `DamageEffectCardHandler` 以 ThreadLocal 门闩阻止"自己发起的真伤再次进入自己";代价是
      **嵌套真伤不会获得法伤加成**(正确:加成只算一次)。真正的问题在 A1/A5-C1 的数值阶段(第一轮 A1)。
顺序依赖: 门闩必须在 `hurt` 前置位、`finally` 复位 —— 当前正确(`A:62-69`)。
证据: A: event/DamageEffectCardHandler.java:29 `private static final ThreadLocal<Boolean> APPLYING_TRUE_BONUS ...`
        :62 `if (bonus > 0 && !APPLYING_TRUE_BONUS.get()) {` / :63 `.set(true);` / :68 `.set(false);`
可达性: 可达(法伤牌 + 敌对目标)。
修法: 无(正确实现,建议作为另两个闸门的样板)。
```

## 4.3 §G4 联动回路终止(逐回路)

| 回路 | 终点 | 终止机制 | 判定 |
|---|---|---|---|
| A→B→A:破绽闪避 → 反击 → 被反击方也有破绽 → 再闪避 | `attacker.hurt(...)` | `counterDepth` + 四入口 `isInCounterChain()` | ✅ 结构性终止 |
| 大当家溅射 → 溅射目标再触发赐福满层 → 再溅射 | `victim.hurt(...)` | `aoeProcessing`(在嵌套前的骰战分支 `A:200` 直接 return) | ✅ 终止(注意 `aoeProcessing` 非 ThreadLocal) |
| 法伤加成真伤 → 再次进入法伤处理器 | `target.hurt(trueDamage)` | `APPLYING_TRUE_BONUS`(ThreadLocal) | ✅ 终止 |
| 骰出 1 自伤(绯红骰) → 触发闪避/反击 → 再掷骰 → 再自伤 | `roller.hurt(...)` | 自伤发生在 `counterDepth++` 之内(`A:1146`)⇒ 位于反击链内 ⇒ 骰战闸门 `A:200` return | ✅ 终止(依赖"自伤一定在反击链内"这一不变量;若将来有别的自伤入口(如 `EffectCardItem:29` 王之力)在链外,则**无保护**) |
| 落雷真伤(`EntityThunderHitMixin:61`) → 溅射/反击 | `self.hurt(trueDamage)` | 无专用闸门;依赖 `true_damage` 不触发骰战(非玩家直击) | ⚠️ 未核验:若落雷的受害者佩戴末影骰子并可能进入保命链,需单独验证 |
| 骇客隐身解除(攻击时)→ 触发战斗牌消耗 → 战斗牌效果造成伤害 → 再判"攻击行为" | `onAttackWhileHidden` | `nancy_lu_hidden_until` 时间窗(一次性) | ✅ 一次性标记终止 |

**§G4 总结**:三条已知回路都有终止机制,且都是**显式**的(深度计数 / 布尔闸门 / ThreadLocal)。**唯一结构性弱点是前两个闸门是 `static` 而非 `ThreadLocal`**——它们只在"单服务端线程"假设下成立(第一轮 A17 已记,本轮复核结论不变)。

---

# 第五部分(A6/A7):tick 相位 / 双跑 / 同 tick 多次触发

```text
ID: A6-C1(单侧差异,仅 forge-1.20.1)PlayerTickEvent 未按 TickEvent.Phase 过滤 ⇒ 每 20 tick 跑两次
核验: 证实
现象: 1.20.1 侧 `PlayerTickEvents` 的两个监听器都收 START 与 END 两个相位 ⇒ 每 tick 各执行 2 次
      ⇒ `player.tickCount % 20 != 0` 的门槛每 20 tick 命中 **2 次**(相邻两相位同 tickCount),
      于是 EffectCardPeriod.tick / EmpowerManager.tick / FightPoisonWithPoisonCardItem.tick /
      FenSignItem.tick / 新增的 BaseSignItem.tickSignReadyTimeout 全部每周期执行两次。
      1.21.1 用 `PlayerTickEvent.Pre/Post` 两个**不同事件类**,各只派发一次 ⇒ 天然不双跑。
顺序依赖: 相位过滤是**显式契约**:必须在方法首行 `if (event.phase != TickEvent.Phase.END) return;`。
      未过滤 ⇒ 同一 tick 内两次调用之间的状态无契约(幂等则无害,非幂等则双倍)。
证据: B(forge-1.20.1)
  - event/PlayerTickEvents.java:108-113 `@SubscribeEvent public static void onPlayerTickPre(TickEvent.PlayerTickEvent event) {`
      (无任何 `event.phase` 判断)
  - event/PlayerTickEvents.java:122-... `public static void onPlayerTick(TickEvent.PlayerTickEvent event) {`
      (同样无 phase 判断)
  - 反编译证据: forge-1.20.1-47.4.10 ForgeEventFactory.java:938-946
      `public static void onPlayerPreTick(Player player) { MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.START, player)); }`
      `public static void onPlayerPostTick(Player player) { MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player)); }`
  - 调用点: world/entity/player/Player.java:217 `ForgeEventFactory.onPlayerPreTick(this);` / :288 `onPlayerPostTick(this);`
  - 对照(已正确过滤者): event/RailgunStrikeScheduler.java:123 `if (event.phase != TickEvent.Phase.END) return;`
                          item/MarkManager.java:90          `if (event.phase != TickEvent.Phase.END) return;`
  A(neoforge-1.21.1): event/PlayerTickEvents.java:110-111 `PlayerTickEvent.Pre` / :123-124 `PlayerTickEvent.Post`
      ⇒ 两个不同事件类,各一次。反编译证据:EventHooks.java:971 / :980(分别由 Player#tick 头/尾调用一次)。
可达性: 可达,但**当前不可观察**(四个被驱动者都幂等:都是"按绝对值比较后写状态")。
      例:`EffectCardPeriod.tick` 的周期结束条件基于 `gameTime` 与附件绝对值;`FenSignItem.tick` 基于
      `fen_last_blessing_tick` 差值。双跑不会改变结果,只是白跑一次。
最小实机: forge 1.20.1 单人;给 `EffectCardPeriod.tick` 加一行 `AstralDiceMod.LOGGER.info("tick")`,
      观察每 20 tick 是否打印 2 行(同一 gameTime)。或在不加日志的情况下:让未打满的一轮自然收尾,
      确认冷却只启动一次(当前会启动一次,因两次调用中第二次看到 `cooldown>0` 而早退)。
修法: 两个方法各加一行 `if (event.phase != TickEvent.Phase.END) return;`(最小 diff,零风险)。
      ⚠️ 需注意:加过滤后"每 tick 驱动"的部分(HealingManager.tick / updateCutterEffect /
      RevengeHalberdChipItem.updateDisplayEffect / updateArmorBonus / PrimordialCoreChipItem.updateArmorBonus)
      会从"每 tick 两次"变成"每 tick 一次"——这些是**显示/属性同步**逻辑,单次足够,但要确认没有
      "依赖两次调用才能收敛"的写法(本仓逐行看过:都是"计算期望值 → 写入",收敛一次即够)。
      ⚠️ **本次新增**(PlayerTickEvents 里新加的 `BaseSignItem.tickSignReadyTimeout(player)`,B:117)自带注释
      声称"随 START/END 两个阶段各执行一次,重置逻辑幂等,无副作用"——该注释**承认了双跑**。作为单侧差异
      应在 1.20.1 单独加 phase 过滤,或统一把该调用移到 `onPlayerTick`(END 专属)之后。
```

```text
ID: A6-C2(单侧差异,仅 forge-1.20.1)ClientTickEvent 未过滤相位 ⇒ 客户端每 tick 跑两次
核验: 证实
现象: B `ClientTickHandler.java:16-21` 与 `KeyBindingSetup.java:33` 都收 START/END ⇒
      `ClientDamageNumbers.tick()` 与 `EffectCardUseGuard.onClientTick()` 每次客户端 tick 执行两次。
顺序依赖: 同 A6-C1;`Player#tick` 的 START/END 是两个独立相位(ForgeEventFactory.java:938-946 同款)。
证据: B client/ClientTickHandler.java:16-20
        `@SubscribeEvent public static void onClientTick(TickEvent.ClientTickEvent event) { ClientDamageNumbers.tick(); EffectCardUseGuard.onClientTick(); }`
      B client/KeyBindingSetup.java:33-35 → `if (event.phase != TickEvent.Phase.END) return;` ← **KeyBindingSetup 已过滤**
      A client/ClientTickHandler.java:16-17 `onClientTick(ClientTickEvent.Post event)`(单相位)
可达性: 可达但幂等(`ClientDamageNumbers.tick` 按剩余 tick 递减;`EffectCardUseGuard.onClientTick` 只读按键状态)。
      唯一可观察差异:伤害数字的淡出速度在 forge 侧为 1.21.1 的两倍(每次 tick 扣 2)。
最小实机: forge 1.20.1 打出一次伤害,对比 1.21.1 的数字停留时长(或直接读两处常量)。
修法: `ClientTickHandler.onClientTick` 首行加 `if (event.phase != TickEvent.Phase.END) return;`。
```

```text
ID: A7-C1 同 tick 多次触发:大当家溅射"主目标无敌帧清零 + 立即 hurt" 的竞态窗口
核验: 证实(有修正)
现象: 溅射循环对主目标执行 `victim.invulnerableTime = 0; victim.hurt(...); victim.invulnerableTime = saved;`
      (`A:649-655`/`B:646-652`)。若同一 tick 内有**另一个来源**(落雷/法伤真伤/反击自伤)也打主目标,
      它会在"无敌帧为 0"的窗口内通过判定,造成比正常更多的伤害次数。
顺序依赖: 该窗口的对齐对象是**同步调用链**(单线程内),因此窗口长度 = 一次 `hurt` 的时长;外部来源若要
      在同一线程内插入,只能发生在 `hurt` 内部的嵌套点(N1~N4)。⇒ 实际窗口是可控的。
证据: A: combat/DiceCombatEvents.java:648-656; B: :645-653
可达性: 可达但窗口极窄(需在同一 `hurt` 栈内插入)。
最小实机: 佩大当家 + 电磁炮筹码(延迟 1 秒落雷),把主目标与 2 只僵尸堆叠,使落雷恰好落在溅射的同 tick;
      对比击杀耗时。
修法: 用 `try/finally` 已正确;建议改为"临时设置一个忽略无敌帧的伤害源标记"而不是直接改物品字段,
      避免窗口暴露给第三方监听器。
```

```text
ID: A7-C2 击杀去重键(A7 的"同 tick 多次触发"面)
核验: 未核验(超出 §A/§G4 但与本组同源)
现象: `LivingDeathEvent` 有 8 个 NORMAL 监听器,其中 4 个涉及"发奖/发牌";同一击杀可能因
      "多立牌槽 + 多订阅者"在同 tick 多次进入。第一轮 G3 已指出 `applyRinSignPassive` 有 (触发者,eventId)
      2 tick 去重,而大侦探 +3 星币**无去重**。
证据: 见第一轮 A12/G3;本次仅确认事件层:8 个监听器确实都会收到同一 `LivingDeathEvent` 实例
      (无任何"只投递一次"机制)。
可达性: 可达(同一玩家戴多枚立牌 + 一次击杀)。
修法: 沿用第一轮 G3 的修法(统一去重键 = 受害者 UUID + 事件 id + tick 窗口)。
```

---

# 第六部分:跨版本(单侧)差异清单

| # | 差异 | 证据 |
|---|---|---|
| D1 | **`TickEvent.Phase` 未过滤 ⇒ 双跑**(仅 forge-1.20.1):`PlayerTickEvents` 两个监听器 + `ClientTickHandler` | A6-C1 / A6-C2;`ForgeEventFactory.java:938-946` vs `EventHooks.java:971/980` |
| D2 | **`LivingDamageEvent.Pre` 对应 1.20.1 的 `LivingDamageEvent`(吸收后)**,而不是 `LivingHurtEvent`(护甲前) | §1.1 `L1789` vs §1.2 `L1680`;第一轮 §0 已定性,本次给出完整行号 |
| D3 | **头盔 ×0.75 的时机不同**:1.21.1 在 `actuallyHurt` 之前(进入事件) / 1.20.1 在之后(不影响已扣血) | A `LivingEntity.java:1182-1184` vs B `:1147-1150` |
| D4 | **法伤处理器挂在不同事件**:A `LivingDamageEvent.Pre@NORMAL`(护甲后) / B `LivingHurtEvent@NORMAL`(护甲前) | A `DamageEffectCardHandler.java:31` vs B `:28` |
| D5 | **末影骰雨中/水下 ×1.4 的事件阶段不同**:A 在 `LivingDamageEvent.Pre@HIGH`(护甲后) / B 在 `LivingHurtEvent@HIGH`(护甲前) ⇒ 重甲目标下数值不同 | A `EnderDiceHandler.java:147` vs B `:147` |
| D6 | **同优先级组的成员不同**:A `LivingDamageEvent.Pre@NORMAL` 6 个(含 `DamageEffectCardHandler`) / B `LivingDamageEvent@NORMAL` 4 个;A 的 `LivingIncomingDamageEvent@NORMAL` 3 个 / B 的 `LivingAttackEvent@NORMAL` 3 个 | 2.1 / 2.2 |
| D7 | **`EffectCures` 有无**:A 用 `removeEffectsCuredBy(EffectCures.PROTECTED_BY_TOTEM)` / B 改为"只清 HARMFUL 类别"(本次改动统一口径) | A `EnderDiceHandler.java:169+` / B `:166+` |
| D8 | **`PlayerEvent.Clone` 的订阅者不同**:B 有 `ModCapabilities.java:37@NORMAL`(AstralData 复制) + `PlayerLifecycleHandler.java:164@LOWEST` 两级;A 只有 `PlayerLifecycleHandler.java:168@LOWEST` | 2.4 表 |
| D9 | **`PlayerHostilityTracker`(并发新增)两版本同构**,但 B 侧挂在 `LivingDamageEvent`(吸收后) ⇒ "谁打过谁"的判定在"伤害实际为 0(被吸收吃光)"时 A 不记录、B 记录 | 新增文件 `:50`,A/B 事件类不同 |

---

# 第七部分:不确定点 / 需定夺

**不确定(需实机或需另一批代理确认)**
1. **同优先级内注册顺序的权威性**:本次用的是 `build/libs/*.jar` 的条目索引(构建于 14:41,对应 HEAD)。
   `AutomaticEventSubscriber`(loader-4.0.42 `AutomaticEventSubscriber.java:46-49`)按 `scanData.getAnnotations()`
   的迭代顺序 `forEach` 注册,而该集合由 ASM 扫描产生 —— **我没有验证"jar 条目顺序 == 扫描顺序"**。
   Forge 侧同样(ModFileScanData 由 FML 的 `ModFileParser` 生成)。
   ⇒ 所有"今天安全"的断言的置信度 = **中**。建议:用 `-Dfml.dumpEventListeners`(如存在)或临时给
   `PlayerLifecycleHandler` 与 `EnderDiceHandler` 各加一行日志实测注册顺序。
2. **`LivingDamageEvent.Pre@NORMAL` 组内 `DiceCombatEvents` 的两个监听器(184 与 1116)的相对顺序**:
   同一 `getDeclaredMethods()` 的返回顺序在 JVM 规范中不保证。本次未验证。
3. **`MobEffectEvent.Remove` 组内 `onModEffectRemovalPrevented`(同类的另一个方法)的顺序**(§3.3)。
4. **落雷真伤(EntityThunderHitMixin)是否可能进入保命/反击链**:未核验。
5. **A4-C3 的 ×1.96 需要实机数值确认**(源码层面成立,但需排除"骰战分支在 HIGH 之前被 return"的可能——
   `DiceCombatEvents:L200` 的 `aoeProcessing || counterDepth > 0` 与 `!(directEntity instanceof Player)` 两个
   前置 return 决定了能否走到 `:608`;本次只做了静态分析)。

**需用户定夺**
1. **A4-C3 的修法口径**:倍率只由受击侧事件消费(会丢掉环境伤害的 +40%)?还是只在骰战路径消费(会丢掉
   非骰战来源)?还是引入"已消费"标记(最精确但最复杂)?
2. **A6-C1 是否统一给 1.20.1 加 phase 过滤**:加过滤会把"每 tick 驱动"的显示/属性同步从 2 次降到 1 次
   (预期无副作用,但属行为变化)。
3. **A4-C1 的残余语义**:死亡清理降到 LOWEST 后,"击杀发奖/阶段推进"是否也一并移到 LOW(语义 =
   "必须晚于保命判定"),以摆脱对 `.class` 顺序的依赖?
4. **A5-C1/A5-C2 的闸门是否改 ThreadLocal**:当前依赖单线程假设(第一轮 A17 同源)。
5. **A7-C1 的无敌帧临时清零**是否接受"在嵌套调用链内暴露窗口"这一实现方式。

---

# 第八部分:本轮**不**重复计入的已知改动(按题目要求)

以下属另一批代理的并发改动,**不作为本报告的新发现**,仅评估其是否引入**新的顺序问题**:

| 改动 | 顺序影响评估 |
|---|---|
| `event/PlayerLifecycleHandler.java` 死亡清理 → `LOWEST` | ✅ 方向正确(§3.2 A4-C1);残余风险 = 与新增 `PlayerHostilityTracker@LOWEST` 同优先级(无共享状态,安全) |
| `event/PlayerLifecycleHandler.java` 删除 23 项附件归零 | ⚠️ 依赖"附件不随死亡复制"这一不变量(§3.2 A4-C4) |
| `event/ChipDamageHandler.java`(虚空例外 + A6 吸收口径换算 + HIGHEST 兜底) | ✅ 换算口径与 §1.1/§1.2 行号一致;**新增顺序依赖**:`onLivingDeath@HIGHEST` 必须早于 `EnderDiceHandler@NORMAL`(由优先级保证) |
| `event/EnderDiceHandler.java`(受击修饰器注册表 + HARMFUL-only 清效果) | ❌ **引入新问题 A4-C3(×1.96 双消费)**;清效果改为 HARMFUL-only 后,`MobEffectEvent.Remove@HIGH` 的 `astral_dice:` 拦截行为需一并复核(§3.3) |
| `combat/DiceCombatEvents.java`(受击修饰器消费点 + `HostileTargets.isHostile(player, x)` 上下文) | 消费点 = A4-C3;敌对判定改带上下文后,溅射的受害者集合语义变化(G1/G2 第一轮已定夺) |
| `item/CurioSlotUtil.java`(`isRealUnequip` → `isIntentionalUnequip`,绑定第 3 参) | ✅ 修掉第一轮 S6-C1;新语义"换装也清理"需用户确认(第一轮定夺项 10) |
| `item/card/EffectCardPeriod.java`(未打满的一轮在计时器结束后补冷却) | ⚠️ 与 A6-C1 的双跑交互:两次 `tick` 调用中第二次会看到刚写入的 `cooldownEnd` 而早退 ⇒ 实际只启动一次,安全 |
| `item/sign/BaseSignItem.java`(待命门槛只看 `sign_ready_type`) + 新增 `tickSignReadyTimeout` | ✅ 修掉第一轮 S6-C2;但 `tickSignReadyTimeout` 挂在**未过滤相位**的 `PlayerTickEvents`(A6-C1) |
| `resource/ResourceConversion.java`(只兑换超过 base 的部分 + 按实际差额发币) | 与事件顺序无关(第一轮 S4-C2) |
| 新增 `data/minecraft/tags/damage_type/bypasses_cooldown.json`(`astral_dice:true_damage`) | ✅ 直接消除第一轮 A1 的"法伤加成被无敌帧吞掉";**顺序影响**:`true_damage` 现在穿透无敌帧 ⇒ 溅射/反击/法伤真伤在**同一 tick 内不再互相压制**,同 tick 多次结算的概率上升(与 A7-C1 的窗口叠加) |

---

## 附:本次审计的检查覆盖度

| 检查点 | 覆盖 |
|---|---|
| A1 阶段表(两版本逐行号) | ✅ §1 |
| A1 "每处伤害监听器的阶段理解" | ✅ §1.4 |
| A2 同优先级订阅者普查(全部 `@SubscribeEvent`) | ✅ §2(A 共 79 个订阅方法 / B 共 80 个,含并发新增的 `PlayerHostilityTracker` 各 4 个) |
| A3 显式优先级契约 | ✅ §2.5 + §3.2 |
| A4 取消语义 + 缺失 `isCanceled()` | ✅ §3 |
| A5 嵌套派发与重入 | ✅ §4 |
| A6 tick 相位 / 双端 | ✅ §5(A6-C1/C2) |
| A7 同 tick 多次触发 | ✅ §5(A7-C1/C2),G3 部分转引第一轮 |
| G4 联动回路终止 | ✅ §4.3 |
| **未覆盖(超出本次载荷)** | `Mixin` 注入点顺序、客户端预测、数据包时序、§B/§C/§D/§E/§F/§H |
