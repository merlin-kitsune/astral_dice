# R1a 独立验证（伤害与保命）

- 仓库：`F:\MCProject\astral_dice_multiloader`；分支 `multi-1.20.1-1.21.1`；HEAD=`216f6cc`
- 被验提交：`6daebf9`（fix(1.2.1): 交互时序审计裁决落地(双版本)）——仅验其中**伤害与保命**部分
- 验证者：独立 subagent（与实现者无关）。**只读仓库**，仅写本文件。
- 判据：代码是否真的实现了用户要求；每条给 `文件:行号`；无法判定就写「无法判定」；不以「看起来没问题」当 PASS。
- 原版源码取自：`neoforge-1.21.1/build/moddev/artifacts/neoforge-21.1.235-sources.jar`、`forge-1.20.1/build/moddev/artifacts/forge-1.20.1-47.4.10-sources.jar`（Mojang mappings）。

## 状态条目（增量追加）

### 条目 1 — 真伤穿无敌帧：**PASS**

**证据 A（标签文件，两版本各一份，内容一致，仅含本模组真伤）**

- `neoforge-1.21.1/src/main/resources/data/minecraft/tags/damage_type/bypasses_cooldown.json:1-5`
  ```json
  { "values": [ "astral_dice:true_damage" ] }
  ```
- `forge-1.20.1/src/main/resources/data/minecraft/tags/damage_type/bypasses_cooldown.json:1-5` —— 与上逐字节相同。
- 该文件在 `6daebf9` 中为**新文件**（`git show 6daebf9 --stat` 记 `bypasses_cooldown.json | 5 +`，diff `new file mode`）。
- 伤害类型本体：`neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/damage/ModDamageTypes.java:26-29`（`astral_dice:true_damage`）；`data/astral_dice/damage_type/true_damage.json:1-5`（两版本一致）。

**证据 B（原版 `LivingEntity#hurt` 确实用该标签绕过无敌帧）**

- 1.21.1（`neoforge-21.1.235-sources.jar` → `net/minecraft/world/entity/LivingEntity.java:1190`）：
  `if ((float)this.invulnerableTime > 10.0F && !source.is(DamageTypeTags.BYPASSES_COOLDOWN)) {` → 命中该标签时走 `else` 分支 `:1200-1206`（`lastHurt = amount`、`invulnerableTime = postAttackInvulnerabilityTicks()`、`actuallyHurt(amount)` 全额落地），**不做 `amount - lastHurt` 折减、也不因 `amount <= lastHurt` 直接 `return false`**。
- 1.20.1（`forge-1.20.1-47.4.10-sources.jar` → `net/minecraft/world/entity/LivingEntity.java:1131`）：
  同一判定；`else` 分支 `:1139-1145`（`lastHurt = amount`、`invulnerableTime = 20`、`actuallyHurt(amount)`）。
- 标签键定义：两 jar 的 `net/minecraft/tags/DamageTypeTags.java:12` 均为 `BYPASSES_COOLDOWN = create("bypasses_cooldown")`。
- 结论：标签 → 原版判定键名一致，**两版本均真实生效**，非"写了没用"。
- 附注：`isInvulnerableTo(...)`（`LivingEntity.java` 1.21.1 `:1090` 区域 / 1.20.1 `:1090`）是**另一个**机制（`bypasses_invulnerability` 标签 / 创造模式无敌），本条目讨论的"无敌帧"指 `invulnerableTime`，两者互不替代。

**连带受益（副作用）：不必改写任何调用点，因为标签挂在 damage type 上 → 全部使用 `astral_dice:true_damage` 的伤害一次性全部获得穿无敌帧**

| # | 调用点 | 效果 | 本次是否被提交声明 |
|---|---|---|---|
| 1 | `neoforge-1.21.1/.../DamageEffectCardHandler.java:66`（forge `:63`） | 伤害效果牌附加真伤（法伤加成） | 是 |
| 2 | `neoforge-1.21.1/.../combat/DiceCombatEvents.java:670-682`（forge `:676`） | 大当家「战斗爽」溅射真伤 | 是 |
| 3 | `neoforge-1.21.1/.../combat/SpellDamageRegistry.java:251-252`（forge 同） | 定向爆破 AOE 真伤 | 是 |
| 4 | `neoforge-1.21.1/.../combat/SpellDamageRegistry.java:371-372`（forge 同） | 电击手套 AOE 真伤 | 是 |
| 5 | `neoforge-1.21.1/.../mixin/EntityThunderHitMixin.java:61`（forge 同） | **电磁炮雷击真伤** | **未声明**（未列入提交说明，但同用 `true_damage` → 同样连带获得穿无敌帧；属"连带受益"，非缺陷） |

- 上述 5 处均为 `ModDamageTypes.trueDamage(...)` / `TRUE_DAMAGE` 的唯一来源（`git grep -n "trueDamage("` 两版本结果一致，除定义处）。
- `DiceCombatEvents.java:673-686`（1.21.1）仍保留"对主目标临时清零 `invulnerableTime`"的旧绕行代码；标签生效后该绕行**冗余但无害**（不影响正确性，仅属可清理的死代码）。

---

### 条目 2 — 末影骰雨中/水下 +40% 恰好一次：**PASS**

**结构（"唯一应用点 + 骰战只搬运"）**

- 修饰器定义（唯一内置项）：`neoforge-1.21.1/.../combat/DiceCombatModifiers.java:515-521`；`forge-1.20.1/.../combat/DiceCombatModifiers.java:517-523`。谓词 = 佩戴末影骰子（`EnderDiceHandler.hasEnderDie`）+ `player.isInWaterRainOrBubble()`，实时求值，不缓存。
- **唯一求值/应用函数**：`DiceCombatModifiers.applyVictimDamageModifiers(...)`（1.21.1 `:140-146`；forge `:140-146`）。`git grep -n "applyVictimDamageModifiers"` 两版本**各只有 1 处调用**（1.21.1 `EnderDiceHandler.java:152`；forge `EnderDiceHandler.java:152`）。
- 骰战侧**只搬运**：`DiceCombatModifiers.instanceVictimFactor(...)`（1.21.1 `:154-156`；forge `:154-156`）只读登记槽、不重新求值；调用点 1.21.1 `DiceCombatEvents.java:199`（固化到局部变量）与 `:631`（`finalDmg *= victimFactor`）；forge `:195` 与 `:637`。
- 覆盖式覆写成立：1.21.1 骰战在 `LivingDamageEvent.Pre` 用 `event.setNewDamage((float) finalDmg)`（`:633`）；forge 用 `event.setAmount(...)`（`:639` 附近），且 `finalDmg` 由 `attackPower` 重算（1.21.1 `:485`、`:600`），**不继承**事件里已被 HIGH 乘过的那份值 → HIGH 处乘出的 ×1.4 被整段替换，不构成叠加。`instanceVictimFactor` 还要求 `victim == instanceVictim`（1.21.1 `:155`），嵌套伤害实例（溅射/AOE/反击）不会串味。
- **⚠️ 单侧形状差异（必须声明）**：1.21.1 的应用点是 `LivingDamageEvent.Pre` @HIGH（`EnderDiceHandler.java:147-148`），该事件在**护甲/附魔/吸收之后**（原版 `LivingEntity#actuallyHurt` L1789 派发，晚于 L1787-1788 的护甲/魔法减免）；1.20.1 的应用点是 **`LivingHurtEvent` @HIGH**（`EnderDiceHandler.java:147-148`），在**护甲之前**（`forge-1.20.1-47.4.10-sources.jar` `LivingEntity.java:1665` `ForgeHooks.onLivingHurt`→早于 `:1667-1668` 护甲/附魔）。因原版护甲/附魔减免均为**乘性**，该差异不改变净倍率（见下表），但 1.20.1 的 `lastHurt` 无敌帧记账仍用放大前的值（`:1137`/`:1140`），属既有形状、与本条无关。

**1.20.1 七咒 ratio 守卫（避免二次计入 → x1.96）**

- `forge-1.20.1/.../combat/DiceCombatEvents.java:196-204`：`if (victimFactor != 1.0 && target instanceof Player cursed && ModAttachments.getDiceCurseRatio(cursed) > 1.0f) victimFactor = 1.0;`
- 时序核对（证明守卫**读到的不是陈旧值**）：同一次伤害在 1.20.1 内的顺序是 `LivingHurtEvent` → 护甲/附魔（`:1667-1668`）→ 吸收（`:1669-1670`）→ `LivingDamageEvent`（`:1680`）。EnderDiceHandler@HIGH 与 FateGuidanceCardItem@LOWEST 都在 `LivingHurtEvent`（`FateGuidanceCardItem.java:107-108`），**早于** DiceCombatEvents 所在的 `LivingDamageEvent`（`:186-187`）。故 ratio 是**本次实例**刚写入的，不是上一实例的残留。
- 该 ratio 含本次 ×1.4 时由 `EXTERNAL_DAMAGE_FACTORS` 内置因子（`DiceCombatEvents.java:958-969`）统一应用一次并**清零**（`:965`）→ 净倍率仍为 ×1.4。1.21.1 的捕获点在 `LivingIncomingDamageEvent` @LOWEST（`FateGuidanceCardItem.java:98-100`，原版派发点 `CommonHooks.onEntityIncomingDamage` ← 1.21.1 `LivingEntity.java:1153`，早于 `actuallyHurt`）→ ratio 不含 ×1.4，故 1.21.1 **不需要**该守卫（与注释一致）。

**五条路径的应用次数与净倍率（两版本同结论；倍率仅在"佩戴末影骰子 + 雨中/水下"时生效）**

| 路径 | 1.21.1 | 1.20.1 | 修饰器求值次数 | 净倍率 |
|---|---|---|---|---|
| 赐福近战（骰战） | HIGH 先乘 1.4 → 骰战 `:633` 覆盖丢弃 → `:631` ×`instanceVictimFactor` | HIGH(LivingHurtEvent) 乘 1.4 → 骰战 `:639` 覆盖丢弃 → `:637` ×factor（七咒时改用 ratio 因子） | 1 | **×1.4** |
| 非赐福近战 | 仅 HIGH（骰战在 `:374-375` `!DICE_BLESSING`/`diceStack==null` 提前 return） | 仅 HIGH（骰战在 `:380-381` 提前 return） | 1 | **×1.4** |
| 环境伤害（无玩家攻击者） | 仅 HIGH（骰战在 `:211` `!(directEntity instanceof Player)` return） | 仅 HIGH（骰战在 `:216` 同判 return） | 1 | **×1.4** |
| 溅射真伤（大当家/定向爆破/电击手套） | 仅 HIGH（骰战在 `:210` `aoeProcessing` return；`aoeProcessing=true` 在 `hurt` 之前置位 `:654`） | 仅 HIGH（骰战在 `:215` `aoeProcessing` return；`DiceCombatEvents.java:654` 附近同款置位） | 1 | **×1.4** |
| 反击注入（`injectCounterDamage`） | 仅 HIGH（骰战在 `:210` `counterDepth > 0` return；`counterDepth++` 在 `hurt` 之前 `:1170`） | 仅 HIGH（骰战在 `:215` return；`counterDepth++` `:1189`） | 1 | **×1.4** |

**关键不变量判定**：非赐福攻击者与环境伤害**仍然 ×1.4**（上表 2、3 行）⇒ 未触发 FAIL 条件。**PASS**。
（无法判定项：无。以上均为静态代码判读；实际 tick 内数值未在游戏内实测。）

---

### 条目 3 — 气囊按"吸收后"判定：**PASS**

**1.21.1（需要换算，且换算只喂气囊）**

- `neoforge-1.21.1/.../event/ChipDamageHandler.java:64-72`：`float damageAfterAbsorption = Math.max(0.0F, damage - player.getAbsorptionAmount());`（`:68`）→ 仅用于 `if (damageAfterAbsorption >= player.getHealth() && AirbagChipItem.tryNegateFatal(player))`（`:69`）。
- 磨刀石用**原值**：`:75` `WhetstoneChipItem.modifyIncomingDamage(player, damage)`（`damage` = `event.getNewDamage()`，`:53`）——未被换算污染。PASS。
- 原版先后（证明必须换算）：`neoforge-21.1.235-sources.jar` `net/minecraft/world/entity/LivingEntity.java:1789` 派发 `LivingDamageEvent.Pre`（`CommonHooks.onLivingDamagePre`），`:1790-1792` 才结算吸收（`Reduction.ABSORPTION` / `setAbsorptionAmount`）。即事件回调时**吸收尚未扣除**。

**1.20.1（天然达标）**

- `forge-1.20.1/.../event/ChipDamageHandler.java:64-68`：直接 `if (damage >= player.getHealth() && AirbagChipItem.tryNegateFatal(player))`，无换算行。
- 原版先后（证明无需换算）：`forge-1.20.1-47.4.10-sources.jar` `net/minecraft/world/entity/LivingEntity.java:1669-1670` 吸收结算（`f1 = Math.max(damageAmount - getAbsorptionAmount(), 0)`），`:1680` 才 `ForgeHooks.onLivingDamage(this, damageSource, f1)` 派发事件 —— **传入值就是"吸收后仍会扣的生命"**，与 1.21.1 的 `damage - getAbsorptionAmount()` 同一公式（下限 0）。
- 磨刀石同样用事件原值（`:74`），两侧一致。

**行为对等性**：两侧比较量均为 `max(伤害 − 吸收, 0)` vs `玩家当前生命`，语义相同。**形状单侧不同已声明**：1.21.1 多一行显式换算（因该版本事件在吸收之前），1.20.1 依赖原版传入值。**PASS**。

---

### 条目 4 — 虚空不可救 / `/kill` 可救：**PASS**

- 1.21.1：`ChipDamageHandler.java:60`（伤害阶段）与 `:95`（死亡兜底）都是
  `if (event.getSource().is(DamageTypes.FELL_OUT_OF_WORLD)) return;` —— 气囊与磨刀石（磨刀石在 `:75`，位于 `:60` 之后）双双跳过。
- 1.20.1：`ChipDamageHandler.java:60` 与 `:94` 同款判定。
- `/kill` 可用性：判定**只按 `DamageTypes.FELL_OUT_OF_WORLD`**，未用 `DamageTypeTags.BYPASSES_INVULNERABILITY`（注释 `:58-59` / forge `:58-59` 明确说明：该标签同时含 `generic_kill`，一律查标签会把 `/kill` 也变成不可救）。`generic_kill` ≠ `out_of_world` ⇒ `/kill` 走完整保命链（气囊照旧能救）。**PASS**。
- 末影骰判定未被误改：`EnderDiceHandler.onLivingDeath` 的 `if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;`（1.21.1 `:164`；forge `:164`）**未出现在 `6daebf9` 的增删行中**（`git show` 该文件 diff 无此行 +/-），本次只改了"清效果"段（1.21.1 `:169-184`）。**PASS**（此条对 1.20.1 未逐字节比对修改前版本，但 diff 证明未被本提交触碰）。

---

> **续验（条目 5–7）**：由**第二位**独立验证者补齐，同一 HEAD=`216f6cc`；仅追加，不改前文。判据同上（代码是否真的实现了用户要求；无法判定则写明缺什么）。

### 条目 5 — 末影骰保命只清除有害效果（HARMFUL）：**PASS**

**1.21.1 —— 已从 `removeEffectsCuredBy(PROTECTED_BY_TOTEM)` 改为 HARMFUL 过滤**

- `neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/event/EnderDiceHandler.java:175`：`player.setHealth(1.0F);`
- 同文件 `:176-181`（先收集、再删除，避免遍历中修改）：
  `List<Holder<MobEffect>> harmfulEffects = new ArrayList<>();` + `if (instance.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) harmfulEffects.add(instance.getEffect());`
- 同文件 `:182-184`：`for (Holder<MobEffect> effect : harmfulEffects) { ModEffectRemoval.remove(player, effect); }`
- **改动前**原行（`git show 6daebf9 -- .../EnderDiceHandler.java` 的 `-` 行）：
  `player.removeEffectsCuredBy(net.neoforged.neoforge.common.EffectCures.PROTECTED_BY_TOTEM);`
- 旧写法**确实会连增益一起清掉**：`neoforge-21.1.235-sources.jar` → `net/neoforged/neoforge/common/EffectCures.java:24` `DEFAULT_CURES = Set.of(MILK, PROTECTED_BY_TOTEM)`，且 `PROTECTED_BY_TOTEM` 的 javadoc（`:20-22`）为 “Cures any effect by default”。
- 入口签名匹配：`event/ModEffectRemoval.java:27` `remove(Player, Holder<MobEffect>)`；调用点传入的 `Holder<MobEffect>` 来自 1.21.1 `MobEffectInstance.java:199` `public Holder<MobEffect> getEffect()`。

**1.20.1 —— 已从 `removeAllEffects()` 改为 HARMFUL 过滤**

- `forge-1.20.1/.../event/EnderDiceHandler.java:174`：`player.setHealth(1.0F);`
- 同文件 `:175-180`：`List<MobEffect> harmfulEffects = ...`，判据 `instance.getEffect().getCategory() == MobEffectCategory.HARMFUL`（1.20.1 `MobEffectInstance.java:146` 返回 `MobEffect`、`MobEffect.java:158` `getCategory()`）。
- 同文件 `:181-183`：`for (MobEffect effect : harmfulEffects) { ModEffectRemoval.remove(player, effect); }`
- **改动前**原行：`player.removeAllEffects();`（diff `-` 行）。
- 入口签名匹配：`forge-1.20.1/.../event/ModEffectRemoval.java:26` `remove(Player, MobEffect)`。

**`ModEffectEvents` 的 HIGH 命名空间守卫仍完整，且本模组自身的有害效果确实被清掉**

- 1.21.1 `.../event/ModEffectEvents.java:110-131`：守卫本身是 `@SubscribeEvent(priority = EventPriority.HIGH)`（`:110`）；内部通道放行在 `:114` `if (ModEffectRemoval.isInternal()) return;`；`astral_dice:` 命名空间拦截在 `:127-130`（`effectId.startsWith(AstralDiceMod.MODID + ":") → event.setCanceled(true)`）；标记/发光配对保护在 `:122-126`。
- 1.20.1 `.../event/ModEffectEvents.java:106-125`：同款（内部放行 `:110`、命名空间拦截 `:123-125`）。
- 两个文件**均不在 `6daebf9` 的改动清单中**（`git show 6daebf9 --stat` 无 `ModEffectEvents` / `ModEffectRemoval` 条目）⇒ 守卫未被本次改动削弱。
- **内部通道在此处是必需的（不是可有可无）**：保命路径先 `setHealth(1.0F)`（1.21.1 `:175` / forge `:174`）再清效果，而 `isDeadOrDying()` 即 `getHealth() <= 0`（1.21.1 源码 `LivingEntity.java:1134-1136`）⇒ 清效果时实体已“不处于死亡态”，守卫的 `isDeadOrDying()` 放行分支（`ModEffectEvents.java:117` / forge `:113`）**不成立**，只能靠 `ModEffectRemoval.isInternal()`。本模组确有 **6 个 HARMFUL 效果**（两版本同名、同数量）：`BlueCurseEffect.java:17`（forge `:19`）、`MarkedEffect.java:8`、`MosesBrokenEffect.java:16`、`PandamanTauntEffect.java:11`、`UndercoverInvestigationEffect.java:8`、`WeakMarkEffect.java:8` —— 若不经内部通道会被守卫拦下并永久残留。
- 清效果之后追加的 3 个增益（REGENERATION / ABSORPTION / FIRE_RESISTANCE，1.21.1 `:185-190`；forge `:184-189`）不受影响（BENEFICIAL，不在待删集合内）。

**双版本行为对等性**：两侧流程逐段一致（`setHealth(1.0F)` → 收集 HARMFUL → `ModEffectRemoval` 逐个移除 → 追加 3 个增益 → 图腾动画包 → 安全瞬移 → 5:00 冷却），差异仅为 API 形态（1.21.1 `Holder<MobEffect>` / 1.20.1 `MobEffect`）与包名（`EnderDieTotemPayload.send` / `ModNetwork.EnderDieTotemMessage.send`）。**PASS**（静态判读；未在游戏内实测实际效果列表）。

- 注（非 FAIL，两点观察）：① `MARKED` 是 HARMFUL 会被清除，但 `GLOWING` 是 **NEUTRAL**（1.21.1 源码 `MobEffects.java:96`）不在待删集合内 ⇒ 保命后可能残留一圈发光轮廓直到自然到期，与守卫 `:122-126` 的“发光与标记同寿命”口径不再一致（按“只清 HARMFUL”的字面要求属预期行为，但值得记一笔）；② forge `ModEffectRemoval.java:9` 的 javadoc `{@link ModEventHandlers#onModEffectRemovalPrevented}` 指向**不存在的类**（实际守卫在 `ModEffectEvents`），属既有文档笔误，非本次引入。

### 条目 6 — 死亡清理降为 LOWEST：**PASS**

**优先级（两版本均为 LOWEST）**

- 1.21.1：`neoforge-1.21.1/.../event/PlayerLifecycleHandler.java:116` `@SubscribeEvent(priority = EventPriority.LOWEST)`，紧接 `:117` `onPlayerDeathClearEffects(LivingDeathEvent event)`；`EventPriority` 导入为 `net.neoforged.bus.api.EventPriority`（`:57`）。
- 1.20.1：`forge-1.20.1/.../event/PlayerLifecycleHandler.java:113` / `:114`；导入为 `net.minecraftforge.eventbus.api.EventPriority`（`:57`）。
- 改动前两侧都是裸 `@SubscribeEvent`（默认 NORMAL）：diff 为 `-    @SubscribeEvent` / `+    @SubscribeEvent(priority = EventPriority.LOWEST)`。

**`if (event.isCanceled()) return;` 早退：必须存在 —— 两版本均存在且未被删**

- 1.21.1 `:120-121`；1.20.1 `:117-118`：`if (event.isCanceled()) return;`。
- 父提交（`git show 6daebf9^:...PlayerLifecycleHandler.java`）中该行**已存在**，且**不在本提交的 `-` 行里** ⇒ 早退被保留（若删除即 FAIL，此处未删）。
- 排序证明（为何必须在 LOWEST 才能挡住“被取消的死亡”）：`LivingDeathEvent` 在 `LivingEntity.die()` **最顶部**派发（1.21.1 源码 `:1409` `CommonHooks.onLivingDeath`；1.20.1 `:1343` `ForgeHooks.onLivingDeath`），早于 `dead = true`（`:1425` / `:1359`）与 `dropAllDeathLoot`（`:1430` / `:1366`）；两个保命方分别位于 HIGHEST（安全气囊 `event/ChipDamageHandler.java:88-99`；1.20.1 `:87-98`，取消 + `setHealth(max(1, health))`）与默认 NORMAL（末影骰 `event/EnderDiceHandler.java:157-168`，两版本同）⇒ 均早于 LOWEST；清理在 LOWEST 才能稳定读到 `isCanceled()==true` 并早退（改动前是 NORMAL，与其它 NORMAL 死亡处理器之间的顺序未定义）。

**同文件内其它 LOWEST 处理器与共享可变状态（已逐项核）**

- 同文件第二个 LOWEST 处理器：`onPlayerCloneRestoreDeathPreserved`（1.21.1 `:189-197`；1.20.1 `:180-188`），挂在 `PlayerEvent.Clone`（**不同事件类型**）。
- 二者共享的可变状态 = 两个**静态暂存表**：`ChargeManager.DEATH_PRESERVED_STACKS`（`item/ChargeManager.java:75/77` 写、`restoreAfterDeath` 读）与 `DeathPreservedBonuses.PRESERVED`（`component/DeathPreservedBonuses.java:41-44` 写、`:50` `remove` 读）。
- **安全性判定**：① 两个处理器的事件类型不同，`PlayerEvent.Clone` 必然晚于 `LivingDeathEvent`，写-读次序天然成立；② `restoreAfterDeath` 用 `PRESERVED.remove(uuid)` 取走并“取较大值”合并（`:50-58`），重复调用幂等（`PlayerRespawnEvent` 侧再兜底也是空操作）；③ Clone 侧的 LOWEST 是**必要**的 —— 必须晚于 NeoForge 自身的附件克隆（`neoforge-21.1.235-sources.jar` `attachment/AttachmentInternals.java:56-59` `onPlayerClone` 为默认 NORMAL），否则回写会被复制覆盖；它与死亡清理的 LOWEST 不冲突（不同事件，不存在同事件内的顺序竞争）。**结论：安全**。
- 跨文件补充（不属本文件，但同事件、同优先级，故一并核）：`combat/PlayerHostilityTracker.java:72-77`（1.20.1 `:74-79`）也是 `LivingDeathEvent` 的 LOWEST，同样有 `isCanceled()` 早退，且只动自己的静态敌对表（`forget(uuid)`），与死亡清理的状态不相交 ⇒ LOWEST 内部顺序不确定也无害。
- 注（非 FAIL）：1.21.1 `:114-115`（1.20.1 `:111-112`）注释称两个保命方“在 NORMAL”，实测安全气囊为 `HIGHEST`（`ChipDamageHandler.java:88`）—— 结论不受影响（HIGHEST 同样早于 LOWEST）。

**行为变化（正面，记录备查）**：改动前清理处于 NORMAL，与其它 NORMAL 死亡处理器（如 `InvestigationEventUtil.java:107-108` 读取受害者的 `UNDERCOVER_INVESTIGATION`）顺序随机；降为 LOWEST 后清理**确定性最后执行**。本清理不触碰 `UNDERCOVER_INVESTIGATION`（效果清单见下条 ③），无相互影响。**PASS**（两版本一致）。

### 条目 7 — S4-C6 清理无效项 + 必须保留的项：**PASS**

**被删条目清点（diff 硬证，两版本完全相同）**

- `git show 6daebf9 -- .../PlayerLifecycleHandler.java` 的 `-` 行共 29 行，其中 **23 行**是 `ModAttachments.setXxx(player, 默认值)`（neoforge / forge **各 23**），另有 `EffectCardPeriod.clearRoundBonuses(player);` 1 行（其余 = 4 行注释 + 1 行 `@SubscribeEvent`），即 29 = 1 + 4 + 23 + 1。
- 23 个键：DefenseCardConsumedThisBlessing、SignReadyType、SignReadyExpire、FenRecharge、MagicQuiverTracking、MagicQuiverFirstCard、MagicQuiverCooldownEnd、FateActiveUntil、StarCoinHammerBonus、CursedSwordBonus、CursedSwordBlessingTriggered、FlashlightGrantedTargets、SatelliteGiveCooldownEnd、NancyLuPassiveType、NancyLuActiveBonus、NancyLuActiveBonusUntil、NancyLuHiddenUntil、NancyLuEnderPearlImmuneUntil、EffectCardPlayCount、EffectCardCooldownEnd、KomachiUseCount、MagicTomeUseCount、DiceCurseRatio。
- **“只把附件写回默认值”成立（逐个体核到实现体）**：上述 23 个 setter 在 `component/ModAttachments.java` 中均为单条 `player.setData(KEY.get(), value)`（部分带 `Math.max(0, …)` / `null→""` 夹取），**没有任何其它语句**；`EffectCardPeriod.clearRoundBonuses`（`item/card/EffectCardPeriod.java:184-189`）也只写 4 个附件默认值（`setEffectCardBonusPlays(0)`、`setCandyChipPlayBonusActive(false)`、`setSatellitePlayBonusActive(false)`、`setLivingPageCycleBonus(0)`）。两版本一致。
- **被删项无一属于 `copyOnDeath` 集合**：`git` diff 中不存在 `setRinPages` / `setKomachiDamageBonus`（`setKomachiUseCount` 是另一个键，不在保留集内）。

**`copyOnDeath` 键集合 = `rin_pages` + `komachi_damage_bonus`（自核，恰为 2 个 ✅）**

- 1.21.1：`neoforge-1.21.1/.../component/ModAttachments.java` 全文 `.copyOnDeath()` **恰好 2 处** —— `:176`（`komachi_damage_bonus`，注册见 `:173`）与 `:280`（`rin_pages`，注册见 `:277`）。
- 上游语义（证明“非 copyOnDeath ⇒ 新实体取默认值”）：`neoforge-21.1.235-sources.jar` → `net/neoforged/neoforge/attachment/AttachmentInternals.java:52-53` `copyEntityAttachments(from, to, isDeath)` 的过滤器为 `isDeath ? type -> type.copyOnDeath : type -> true`，由 `:56-59` 的 `onPlayerClone` 调用；`copyOnDeath` 默认 `false`（`AttachmentType.java:153`，仅 `:224-227` 显式置 true）。
- 1.20.1：`forge-1.20.1/.../component/AstralData.java:74-99` `onPlayerClone`，死亡分支白名单 `kept = { ModAttachments.RIN_PAGES.name(), ModAttachments.KOMACHI_DAMAGE_BONUS.name() }`（`:79-82`）；键名确为 `"rin_pages"`（`ModAttachments.java:257`）与 `"komachi_damage_bonus"`（`:150`）；非死亡分支才整份复制（`:93-96`）。
- 上游语义（证明新实体 Capability 是全新默认实例）：`forge-1.20.1-47.4.10-sources.jar` → `net/minecraft/server/level/ServerPlayer.java:1151-1195` `restoreFrom` 只逐字段复制固定清单（物品栏 / 经验 / 末影箱 / 持久数据 …），**不复制 Capability**，末尾 `:1192` 触发 `onPlayerClone`；全 jar 中 `ForgeCaps` 仅出现在 `Entity.java:1738/1821`（NBT 序列化/反序列化）与 `CraftingHelper`，**没有“克隆即复制 Capability”的路径**。
- 结论：被删的 23 键 + `clearRoundBonuses` 的 4 键**均为非 `copyOnDeath` 且只写默认值** ⇒ 在“死亡 → 重生”的新实体上本来就是默认值，**删除对重生后玩家的状态无副作用**，与注释 `:132-138`（1.20.1 `:129-135`）的论证一致。**判据满足**。
- 边界（已核，不影响结论）：唯一能把旧实体附件原样读回的是“死亡后未重生即退出 → 重新登录”（`PlayerList.load` → `player.load(tag)`，1.21.1 源码 `PlayerList.java:155` + `:334-347`），但该实体在死亡时血量已为 0（`isDeadOrDying()` 真），而任何重生都走 `PlayerList.respawn`（`:456-468`：新建 `ServerPlayer` + `restoreFrom(player, keepInventory)`，`wasDeath = !keepEverything`）⇒ 仍以“新实体 + 白名单”收场，残留值到不了活着的玩家。`keepInventory=true` gamerule 只影响物品栏拷贝（1.20.1 `ServerPlayer.java:1166`），不改变 `wasDeath`。

**① `MISAKI_SIGN_STACKS` 段仍在（且写在旧实体仍佩戴的立牌物品组件上）✅**

- 1.21.1 `PlayerLifecycleHandler.java:147-153`：`handler.findFirstCurio(s -> s.is(ModItems.MISAKI_SIGN.get()))` → `misaki.get().stack().set(ModDataComponents.MISAKI_SIGN_STACKS.get(), 0);`
- 1.20.1 `:144-149`：`ModDataComponents.MISAKI_SIGN_STACKS.set(misaki.get().stack(), 0);`
- “旧实体仍佩戴”已证：`LivingDeathEvent` 早于 `dropAllDeathLoot`（上游 `LivingEntity.die()` 1.21.1 `:1409` vs `:1430`；1.20.1 `:1343` vs `:1366`），而 Curios 只在 `LivingDropsEvent` 里掉落（两个 Curios jar 的 `top.theillusivec4.curios.common.event.CuriosEventHandler.playerDrops` → `handleDrops`，1.21.1 Curios 9.5.1 / 1.20.1 Curios 5.14.1）。
- `findFirstCurio` 返回的是**活栈引用**（非副本）：Curios jar 字节码 `CurioInventoryCapability$CurioInventoryWrapper.findFirstCurio` 为 `IDynamicStackHandler.getStackInSlot(i)` → 直接 `new SlotResult(SlotContext, ItemStack)`（`SlotResult."<init>"`），无复制 ⇒ 写入落在装备中的那件立牌上，并随其后的掉落一起带走。

**② 新增的 JASMINE_ / PADMAN_ 归零仍在（且确无其它死亡清理路径）✅**

- 1.21.1 `:157-174`：`JASMINE_ATK_BONUS` / `JASMINE_DEF_BONUS` / `PADMAN_ATK_BONUS` / `PADMAN_DEF_BONUS` / `PADMAN_LAST_REFRESH` 五个组件全部写 0（`stack().set(...)`）；1.20.1 `:153-165` 同款 5 个（`ModDataComponents.X.set(stack, 0)`）。
- 其它路径核查：`clearSignData`（`JasmineSignItem.java:48-56` / `PadmanSignItem.java:47-54`）只由 `BaseSignItem.onUnequip`（`:379-383`，经 `CurioSlotUtil.isIntentionalUnequip`）调用；而**死亡掉落不回调 `onUnequip`** —— 两个 Curios jar 的 `CuriosEventHandler` 中 `ICurio.onUnequip` 的**唯一**引用点在 tick 的 lambda（`lambda$tick$22` / `lambda$tick$31`），`handleDrops` 内无任何 `onUnequip` 引用；tick 路径传的是 `getPreviousStackInSlot` 的副本（仓库自述缺口 `BaseSignItem.java:182`、`:371-383`），写入会丢失 ⇒ 死亡时只有本次新增的这 5 行能真正归零**掉落出去的那件立牌**。**必要且有效**。

**③ 有真实副作用的调用仍在 ✅（逐项核到实现体）**

| 调用 | 位置（1.21.1 / 1.20.1） | 真实副作用（核到实现） |
|---|---|---|
| `player.removeEffect(...)` ×11 | `:139-141` + `:176-183` / `:136-138` + `:167-174` | 真移除 MobEffect：INVISIBILITY、NANCY_LU_HACK、BLUE_CURSE、DICE_BLESSING、HAIQING_READY、BONNIE_READY、INVESTIGATION_BONUS、FATE_GUIDANCE、FEN_FRENZY、PAPARA_BITE、MAGIC_TOME_COUNT |
| `HealingManager.clear` | `:129` / `:126` | 实现体 `HealingManager.java:85-91` / `:86-92` 内含 `ModEffectRemoval.remove(player, ModEffects.HEALING)` ⇒ 真移除 MobEffect |
| `EffectTimerGuard.clear` | `:131` / `:128` | ⚠️ 见下方注：`EffectTimerGuard.java:160-163` **仅**写 `effect_timer_ends` 一个**非 copyOnDeath** 附件 |
| `ChargeManager.preserveOnDeath` | `:123` / `:120` | 静态表 `DEATH_PRESERVED_STACKS.put/remove`（`ChargeManager.java:71-79`）⇒ 附件之外的副作用 |
| `DeathPreservedBonuses.preserveOnDeath` | `:126` / `:123` | 静态表 `PRESERVED.put`（`DeathPreservedBonuses.java:39-45`，读 `rin_pages`/`komachi_damage_bonus`） |
| `DiceCurioItem.removeGlassDiceOnDeath` | `:128` / `:125` | 从 Curios 骰子槽移除玻璃骰并收缩筹码栏（`DiceCurioItem.java:178-191` / `:179-192`）⇒ 真删物品 |

- ⚠️ **注（本条不判 FAIL，但提交注释口径不准）**：`EffectTimerGuard.clear`（`EffectTimerGuard.java:160-163`）只做 `player.setData(EFFECT_TIMER_ENDS.get(), new HashMap<>())`；`effect_timer_ends` 注册于 `ModAttachments.java:951-959`（默认空表、仅服务端、序列化），**没有 `.copyOnDeath()`** ⇒ 它与上面 23 个被删项同属“只写非 copyOnDeath 附件默认值”一类；其可观察作用只存在于“死亡事件 → 实体被替换”这段窗口内（守卫 tick 会据记录重新施加已被移除的效果，`EffectTimerGuard.java:126-129`；而 `onEffectTimerForget` 在 `isDeadOrDying()` 时本就跳过，`ModEffectEvents.java:154` / forge `:150`）。因此代码注释 `PlayerLifecycleHandler.java:137-138`（1.20.1 `:134-135`）“下面保留下来的调用都带有附件之外的真实副作用”**对这一行不成立**。用户要求的是“这些调用必须仍在”（已满足），故不判 FAIL，仅记为口径瑕疵。

**双版本对等性**：①②③ 在 neoforge-1.21.1 与 forge-1.20.1 上逐项对应（组件形态为 1.21.1 数据组件 `.get()/.set()` 与 1.20.1 `ItemDataKey.getOrDefault/.set`，语义一致）。**PASS**（静态判读；未做游戏内实测）。

**本批（条目 5–7）结论：三项均 PASS（两版本一致），无 FAIL、无「无法判定」。**
