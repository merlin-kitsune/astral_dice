# R2 回归验证报告 — 被验证提交 `027e1e9`(父提交 `216f6cc`)

- 验证人:独立验证者(非本次改动实现者;第二轮 R2)
- 仓库:`F:\MCProject\astral_dice_multiloader`,分支 `multi-1.20.1-1.21.1`
- `HEAD` = `027e1e990ebfcaf8648553e6fd649381d7c7adc2`,工作区 `git status --porcelain` **干净**(无未提交改动)
- 口径:`N` = `neoforge-1.21.1`,`F` = `forge-1.20.1`;**两版本都给结论**
- 硬约束遵守情况:本文件是本次验证**唯一**写入的文件(`docs/` 在 `.gitignore:58` 内);未改任何源码/lang/CHANGELOG/AGENTS.md;未 `git commit`/`git push`;未重新构建、未启动游戏

## 独立证据方法说明(不依赖实现者自述)

除源码阅读外,另取三类**独立**证据:

1. `git show 027e1e9`(全量 diff,含 AGENTS.md/CHANGELOG)+ `git show 216f6cc:<path>`(父提交原文)做改前/改后对照;
2. `git hash-object` 逐文件比较两版本字节;
3. **已部署产物反编译核对**:`run/1.21.1/mods/astral_dice-1.2.1+neoforge_1.21.1.jar`(mtime `2026/9/15 17:38:38`)与 `run/1.20.1/mods/astral_dice-1.2.1+forge_1.20.1.jar`(mtime `17:38:55`)均**早于提交时刻 17:39:28 约 1 分钟**,即这两份 jar 就是本次提交源码的编译产物。用 `javap -p -c -classpath <jar>` 读字节码,验证「源码里的新写法确实进了产物并解析成功」。

---

## ② D-B2 — 电磁炮落雷改双参敌对口径

**结论:PASS(N/F 均 PASS)** — 但**实现者提交信息里的一处计数描述不实**(见 ②-6),属文档口径问题,不影响代码正确性。

### ②-1 改动本体(N/F 逐字一致)

`N damage/RailgunBolts.java:60-74` / `F damage/RailgunBolts.java:60-74`(**两文件 `git hash-object` 均为 `bfdd2e578b78d7c85e2df4ad448132c1c0bdf0a6`,字节完全相同**):

```java
60:    public static boolean isValidLightningTarget(net.minecraft.world.entity.Entity target, LightningBolt bolt) {
61:        if (target == null) return false;
62:        // 「视谁为敌」的上下文 = 落雷来源玩家(无闪电实例时 viewer 为 null ⇒ 玩家一律不计入敌对)
63:        ServerPlayer cause = bolt == null ? null : bolt.getCause();
64:        if (!HostileTargets.isHostile(cause, target)) return false;
65:        if (target instanceof OwnableEntity ownable) {
66:            if (cause != null && cause.getUUID().equals(ownable.getOwnerUUID())) return false;
67:        }
68:        return true;
69:    }
```

改前(父提交 `216f6cc`)为单参 `HostileTargets.isHostile(target)`,且 `ServerPlayer cause` 只在宠物排除分支内声明。改后 `cause` 被提前到方法头部,同时用于「敌对口径」与「宠物排除」两个用途。判定口径符合题目要求:`HostileTargets.isHostile(cause, target)`,`cause = bolt.getCause()`。

### ②-2 重点风险核查:`bolt.getCause()` 为 null 会怎样? → **不 NPE,且语义与改前等价**(N/F 相同)

`HostileTargets.isHostile(Entity viewer, Entity target)` 实文(`N combat/HostileTargets.java:52-63`,F 同文件同文,`git hash-object` 两版本均为 `74233d8f8b09e1d168eeef2e4b10cb9eba073a76`):

```java
52:    public static boolean isHostile(Entity viewer, Entity target) {
53:        if (target == null) return false;
54:        // 原有语义:敌对生物 ∪ 已被激怒的中立生物
55:        if (isHostile(target)) return true;
56:        if (!(viewer instanceof Player viewerPlayer)) return false;   // ← viewer == null 在此返回 false
57:        if (!(target instanceof Player targetPlayer)) return false;
58:        if (viewerPlayer == targetPlayer) return false;
59:        ...
62:        return PlayerHostilityTracker.hasAttacked(viewerPlayer, targetPlayer);
```

逐条排除题目列出的三种可能:

| 可能 | 实况 | 证据 |
|---|---|---|
| NPE | **否**。`null instanceof Player` 恒为 `false`,`!(...)` 为 `true` 即 `return false`;全程无解引用 | `HostileTargets.java:56` |
| 宽判「无队伍 ⇒ 全体敌对」 | **否**。该宽判只存在于 `EventTargetCollector` 的**发奖**约定,本类明确不适用;进入玩家分支的**前置条件**就是 `viewer instanceof Player`,viewer 为 null 时根本到不了同队判定 | `HostileTargets.java:56` 先于 61;类注释 `:60-61` 也写明「不适用于此处」 |
| 返回 false / 退化 | **是**,且**与改前的单参重载语义完全相同**:单参 `isHostile(Entity)`(`:39-43`)也只认 `Enemy`/`NeutralMob#isAngry()`,玩家一律不计入 | `:39-43` vs `:53-56` |

⇒ viewer 为 null 时,只保留「敌对生物 ∪ 被激怒的中立生物」,即改前口径。**不存在 NPE,也不存在语义跑偏**(这正是两参重载自己的设计契约,`:49-51` javadoc 已写明)。

进一步:`viewer == null` 在**生产路径上不可达**:两处调用点(`mixin/LightningBoltStrikeScopeMixin.java:46-47`、`mixin/EntityThunderHitMixin.java:45,51`)都先过 `RailgunBolts.isRailgunBolt(self/bolt)` 早退,非本模组电磁炮的闪电(**`/summon lightning_bolt`、其它模组、自然雷**)**根本不进入本判定**(mixin 原文:`if (!RailgunBolts.isRailgunBolt(self)) return original;`)。单参退化重载 `isValidLightningTarget(Entity)`(`:72-74`)**全仓无任何调用方**(仅定义处),是纯退化入口。

### ②-3 来源玩家写入点核查

题目问「`RailgunChipItem#strike` 是否**两个调用点都** `setCause(bolt, player)`」。实况:

- `strike` 本身是 `private static void strike(ServerLevel, Vec3, ServerPlayer cause, float damage)`,**唯一一处** `bolt.setCause(cause)` 在其内:
  - N `item/chip/RailgunChipItem.java:170` / F 同行 `170`(`bolt.setCause(cause);`,紧邻 `bolt.setDamage(damage)` 与 `RailgunBolts.mark(bolt)`,在 `level.addFreshEntity(bolt)` **之前**,`mark` 也满足「须在 addFreshEntity 之前」的契约)
- `strike` 的两个调用点**都在同一方法内**,由同一 `cause` 变量驱动:N/F `RailgunChipItem.java:155`(`for (LivingEntity victim : victims) strike(level, victim.position(), cause, damage);`)——即「两个调用点」实为「两条受害者循环落雷路径」,**共用同一 `cause`**,不存在「只写一处 setCause」的漏洞。
- `executeStrike` 的唯一调用方 `event/RailgunStrikeScheduler.java:152`(N)/`:153`(F):`RailgunChipItem.executeStrike(p.level(), center, p.cause(), p.damage());`。`executeStrike` 内允许 `cause == null`(N/F `:148` `if (cause != null) {...}`),此时 `strike` 会执行 `setCause(null)` → 落雷的 `getCause()` 为 null → 按 ②-2 退化为「仅生物敌对」,与 `executeStrike` 里筛选受害者用的同一口径(`:146` 注释「cause 可能为 null(无归属):此时两参重载退化为'仅生物敌对'的既有语义」)**前后一致**,不会出现「筛选时按生物、落雷时按玩家」的偏差。

⇒ `RailgunChipItem.java` 两版本**逐字一致**(N/F 均为 `:170`);两版本 `RailgunBolts.java` **字节相同**(见 ②-1)。

### ②-4 API 存在性(不依赖「实现者说编译成功」)

对已部署产物(编译于提交前 1 分钟)反编译,`RailgunBolts` 字节码中:

```
15: invokevirtual #19  // Method net/minecraft/world/entity/LightningBolt.getCause:()Lnet/minecraft/server/level/ServerPlayer;
21: invokestatic  #25  // Method .../HostileTargets.isHostile:(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;)Z
```

N 与 F 两份 jar **输出完全一致**(均 87 行 javap 输出)。⇒ `LightningBolt#getCause()` 在两版本 MC 中均存在且返回 `ServerPlayer`,`ServerPlayer cause = bolt.getCause()` 与两参 `isHostile(Entity, Entity)` 均已解析并入产物。

### ②-5 行为差(改前 → 改后)

| 目标类型 | 改前(单参) | 改后(两参 + cause) | 可观察差异 |
|---|---|---|---|
| 敌对生物 / 被激怒中立生物 | 命中 | 命中 | 无 |
| 施放者自己 | 不命中(玩家不计入) | 不命中(`viewerPlayer == targetPlayer` 在 `:58` 返回 false) | 无 |
| 施放者自己的宠物 | 不命中(:65-67 排除) | 不命中(同一分支,逻辑未变,只是 `cause` 声明上移) | 无 |
| **非同队伍、且曾主动攻击过施放者的玩家** | **不命中** | **命中**(`:62` → `PlayerHostilityTracker.hasAttacked(viewerPlayer, targetPlayer)`) | **新增命中**,即题目所称「会打你视为敌对的玩家」 |
| 其它玩家(从未攻击施放者) | 不命中 | 不命中 | 无 |
| 同队玩家 | 不命中 | 不命中(`:61` 同队豁免) | 无 |
| `cause == null`(无归属落雷 / 退化重载) | 仅生物 | 仅生物 | 无 |

查询方向核对:`HostileTargets:62` 传入 `(viewerPlayer, targetPlayer)`;`PlayerHostilityTracker.hasAttacked(Player victim, Player attacker)` 实现为 `HOSTILE_ATTACKERS.get(victim.getUUID()).contains(attacker.getUUID())`,而该 map 由 `onLivingDamagePre` 以「受害者 UUID → 攻击过它的玩家集合」写入(`N combat/PlayerHostilityTracker.java:48-49,67-68,93-97`)。⇒ `hasAttacked(viewer, target)` = 「**target 曾主动攻击过 viewer**」,与 javadoc「曾主动攻击过观察者的玩家」一致;**参数命名(victim/attacker)与调用位序相反但语义正确**,不构成缺陷(仅可读性问题,不判 FAIL)。

### ②-6 `AGENTS.md`「isHostile 全部调用点统计」自查(自行重新 grep,未采信 22→23 / 3→2)

自行 grep 两版本 `src/main/java` 后逐条剔除「定义处 / javadoc `{@link}` / 类内自调用」,实况:

| 口径 | N 实况 | F 实况 | AGENTS.md 声称 | 判定 |
|---|---|---|---|---|
| `isHostile(` 文本命中总数 | 32 | 32 | — | — |
| 其中:定义 2(`:39`,`:52`)+ javadoc 3(`:29`,`:31`,`PlayerHostilityTracker:35`)+ 类内自调用 1(`:55`) | 6 | 6 | — | — |
| **外部调用点合计** | **25** | **25** | 25 | ✅ 相符 |
| 其中·两参 `isHostile(viewer,target)` | **23** | **23** | 23 | ✅ 相符 |
| 其中·单参 `isHostile(x)` | **2** | **2** | 2 | ✅ 相符 |
| 涉及文件数 | 17 | 17 | 17 | ✅ 相符 |

单参的 2 处已核对为:`DiceCombatEvents.java:1042`(N)/`1044`(F)的 `isBlessingTarget`(口径含「非队友玩家」)、`PandamanSignItem.java:107`(N)/`108`(F)的嘲讽(刻意只对敌对生物生效,`:107` 行原文 `HostileTargets.isHostile(e) && !(e instanceof Player)`)。AGENTS.md `:1251` 的三项陈述(**24 处玩法判据点 + RailgunBolts 委托 = 25 个调用点 / 17 个文件;23 个两参;2 个单参**)与代码实况**逐项相符**。

> 与实现者提交信息不符之处(不影响代码,但须记录):提交信息写「无闪电实例的退化重载仍不计入玩家」正确;但 **`disarmAoe` 的「全仓仅剩 1 处调用点」不实**(见 ③-2)。②本身无此类问题。

---

## ③ C1 — `disarmAoe` 上移到 `clearRoundBonuses`

**结论:PASS(N/F 均 PASS)** — 行为变更为**有意且自洽**;未发现「周期尚未真正结束却被判结束」从而过早解除武装的**新**路径。

### ③-1 落点与删除

- N `item/card/EffectCardPeriod.java:193-200`,`F :194-201`,两版本同构:

```java
193:    public static void clearRoundBonuses(Player player) {          // F: 194
194:        ModAttachments.setEffectCardBonusPlays(player, 0);
195:        ModAttachments.setCandyChipPlayBonusActive(player, false);
196:        ModAttachments.setSatellitePlayBonusActive(player, false);
197:        ModAttachments.setLivingPageCycleBonus(player, 0);
198:        // 周期归零:解除电击手套本周期已武装的法伤扩散(下个周期可重新武装)
199:        ElectricGloveChipItem.disarmAoe(player);                    // F: 200
200:    }
```

- 方法签名/可见性:`public static void clearRoundBonuses(Player player)`(N `:193` / F `:194`)——**能拿到 `player`,两版本同构**;新增 `import ...item.chip.ElectricGloveChipItem;`(N `:9` / F `:8` 区,已在 diff 中)。
- **`tick` 情形 1 内的原调用已删除**:改前父提交 `216f6cc` 的 `EffectCardPeriod.java` 里 `disarmAoe` **只有 1 处**,位于 `tick` 情形 1(`N:397` / `F:398`,紧跟 `clearRoundBonuses`/`onRoundFullyReset` 之后);现已删除(git diff 该行为 `-`,现文件 `tick` 情形 1 = `N:400-406` / `F:401-407`,已无 `disarmAoe`)。父提交的 `clearRoundBonuses` 本体(`N:184-190` / `F:185-191`)**不含** `disarmAoe`——即上移确实发生。
  - **措辞纠正**:父提交中该调用**只有 1 处**(并未重复),所以「删除 tick 情形 1 里的**重复**调用」这一描述不准确;准确说法是「把 `tick` 情形 1 里的唯一调用**上移**进 `clearRoundBonuses`」。

### ③-2 「全仓 `disarmAoe` 调用点恰为 1 处」核查 → **不成立**(实为 2 处/版本);但**不构成缺陷**

自行 grep 两版本 `src/main/java` 的 `disarmAoe`:

| 位置 | N | F | 性质 |
|---|---|---|---|
| 定义 | `item/chip/ElectricGloveChipItem.java:78` | `:78` | — |
| **调用点 1** | `item/card/EffectCardPeriod.java:199` | `:200` | 本批新增(周期归零) |
| **调用点 2** | `combat/SpellDamageRegistry.java:384` | `:384` | **改前既有**,`onHit` 里「每周期仅触发一次:触发后解除武装」——与本批无关 |

⇒ **`EffectCardPeriod` 内恰为 1 处调用**,但**全仓是 2 处**(提交信息与 AGENTS.md 的「全仓仅剩 1 处」若按字面理解则为**不实**)。第 2 处是功能上必需的不同语义(触发即消耗),不应删除。**判定:文档/提交信息计数不准(轻微),代码正确**,不判 FAIL。

### ③-3 三条调用路径与「过早解除武装」审计

`clearRoundBonuses` 每版本**恰 3 个调用点**(自行 grep):

| # | 调用点 | N | F | 该调用点是否同时归零「出牌数 + 出牌冷却」 |
|---|---|---|---|---|
| A | `forceResetRound`(忍者宽限 1:00 期满强重置) | `EffectCardPeriod.java:225` | `:226` | 是——`:223-224`(F `:224-225`)先 `setEffectCardCooldownEnd(0)` + `setEffectCardPlayCount(0)` |
| B | `registerPlay` 周期边界块 | `:328` | `:329` | 是——`:325-326`(F `:326-327`)先归零 |
| C | `tick` 情形 1(冷却到期) | `:404` | `:405` | 是——`:400-401`(F `:401-402`)先归零 |

`forceResetRound` 的唯一调用方:`item/sign/BaseSignItem.java:337`(N)/`:339`(F),条件为「忍者锁定 + 宽限刻已到 + 宽限期内 `sign_active_lock_played == false`」(`BaseSignItem.java:326-339` / `:328-341`)——即用户裁定的「宽限 1:00 内自始至终未出任何效果牌 ⇒ 强制重置」。

**改前 vs 改后(有意行为变更,已核实)**:

| 路径 | 改前是否解除武装 | 改后 | 证据 |
|---|---|---|---|
| A `forceResetRound` | **否** | **是** | 父提交 `216f6cc` 中 `clearRoundBonuses` 不含该调用,且 `tick/registerPlay` 之外无其它调用点 |
| B `registerPlay` 边界 | **否** | **是** | 同上 |
| C `tick` 情形 1 | 是 | 是 | 父提交 `:397` |

**是否「周期尚未真正结束却被判结束 ⇒ 过早解除武装」?** 逐路径结论:

- **C(tick 情形 1)**:判据 `cooldown > 0 && now >= cooldown`(N `:382-384` 的落空)→ 出牌冷却**确实**已跑完,且同一块把出牌数归零;与既有「周期结束」定义一致。**不是过早**。
- **B-①(registerPlay 边界的第一半)**:`cooldown > 0 && now >= cooldown`,即冷却已到期但 `tick`(每 20 tick 轮询)尚未清理。此时判为「新周期开始、先归零再登记」是既有契约(`:318-322` 注释)。**关键**:改前此处**不**解除武装 ⇒ 会让「上一周期已武装但仍未触发」的 `ELECTRIC_GLOVE_AOE` **跨周期带到新周期**,而 `onEffectCardUsed` 有「已武装则不再消耗充能」的短路(`ElectricGloveChipItem.java:65`),于是玩家可**不花 4 层充能白嫖一次新周期扩散**。⇒ 改后在此解除武装是**修漏**,不是过早。
- **B-②(registerPlay 边界的第二半)**:`cooldown <= 0 && played > 0 && played >= getMaxAllowed(player)`,即「出牌数已达上限却无冷却在跑」——文档定性为「上限在周期中途下降」的不变量违例修复(N `:320-322`)。**这是唯一一条「墙上时钟意义上的周期未结束」的路径**:它会清零出牌数、清零冷却,并把 `onRoundFullyReset`(忍者起冷却)一并触发(`:329-330`)。因此从**系统自身的周期语义**看它就是一次完整的「轮次归零」,与另外三条「每轮一次」标记(可口糖果/探天卫星/活体书页)在本批**之前就已**被同一分支清除——`disarmAoe` 只是补齐同族项,**口径自洽**。
  - 其代价(如实记录,非缺陷):玩家在「武装后、未触发前」恰好卸下/失效一个「临时 +1 出牌数」来源(如命运指引到期、卸下大背包/忍术飞镖)会连带失去本周期的武装。缓解:武装可重新取得(每张效果牌 +1 充能,满 4 层再武装,见 `ElectricGloveChipItem.java:57-70`),且该分支每次触发后 `played` 归零,不会反复刷;该路径本身即「已放弃本周期」的修复路径。
- **A(forceResetRound)**:宽限期内**未出任何效果牌**,而武装的唯一入口就是「使用伤害类效果牌」(`onEffectCardUsed`,由 `BaseEffectCardUse` 路径调用)⇒ 该路径触发时**绝大多数情况下本就未武装**;能出现的唯一交集是「本轮先用伤害牌武装 → 随后开忍者主动进入锁定 → 宽限 1:00 内不再出牌」。此时 `forceResetRound` 已把出牌数/冷却归零(声明放弃本轮),解除武装与「轮次归零」口径一致。**不是无端过早**。
- **无「同一周期内二次武装白嫖」新风险**:解除武装后重新武装需再攒 4 层充能(=再出 4 张效果牌),且 `onEffectCardUsed` 的「已武装短路」在武装期内阻止重复消耗(`:65`),故不存在「一次充能多次触发」。

⇒ **③ 判定 PASS**:三条路径共用清理项,且每条路径都在同一代码块内同时归零出牌数与冷却;未发现新引入的「周期未结束却解除武装」路径。**唯一提示**:`registerPlay` 的 B-② 分支在语义上是「声明放弃本轮」,若用户认为「武装后因上限下降而丢扩散」不可接受,那是**既有口径的取舍**(该项与另外 4 个每轮一次标记同源),需另行裁定,不属本批实现缺陷。

---

## ④ C4 — 保命时追加移除 `GLOWING`(带门控)

**结论:PASS(N/F 均 PASS)** — 白名单显式、门控真实、收集阶段确先于移除、增益未被误清。

### ④-1 行号与实现(题目给的行号**正确**)

- N `event/EnderDiceHandler.java:189-190` / F `:188-189`:

```java
177:        List<Holder<MobEffect>> effectsToRemove = new ArrayList<>();      // F:176  List<MobEffect>
178:        for (MobEffectInstance instance : player.getActiveEffects()) {     // F:177
179:            if (instance.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {  // F:178 无 .value()
180:                effectsToRemove.add(instance.getEffect());
181:            }
182:        }
183:        // 显式白名单 = HARMFUL ∪ {@code GLOWING}(绝不用 removeAllEffects)。
...
189:        if (player.hasEffect(ModEffects.MARKED)) {          // F:188  hasEffect(ModEffects.MARKED.get())
190:            effectsToRemove.add(MobEffects.GLOWING);        // F:189
191:        }
192:        for (Holder<MobEffect> effect : effectsToRemove) {  // F:191  MobEffect
193:            ModEffectRemoval.remove(player, effect);        // F:192
194:        }
```

### ④-2 未退化为 `removeAllEffects()` / `removeEffectsCuredBy(PROTECTED_BY_TOTEM)`

对两版本 `EnderDiceHandler.java` 全文 grep `removeAllEffects|removeEffectsCuredBy|removeEffect|getActiveEffects|HARMFUL|GLOWING|MARKED|setHealth`:命中的**非注释**行只有上表所列(`getActiveEffects` 178、`HARMFUL` 179、`hasEffect(MARKED)` 189、`add(GROWING)` 190、`ModEffectRemoval.remove` 193)。`removeAllEffects(...)`/`removeEffectsCuredBy(...)` 在两版本**只出现在注释里**(N `:171`、F `:171` 解释「为何不再用」)。⇒ **未退化**,判定由字节码二次确认:产物中只见 `MobEffect.getCategory()`、`Player.hasEffect(Holder|MobEffect)`、`ModEffectRemoval.remove(...)`,**无** `removeAllEffects` / `removeEffectsCuredBy` 调用。

### ④-3 门控真实性 + 收集先于移除

- 门控:`if (player.hasEffect(ModEffects.MARKED))`(N `:189` / F `:188`)。`MARKED` 引用写法两版本各自正确:
  - N `effect/ModEffects.java:22-23`:`DeferredHolder<MobEffect, MobEffect> MARKED`,`player.hasEffect(Holder<MobEffect>)` —— 直接用 `ModEffects.MARKED`,正确;
  - F `effect/ModEffects.java:22-23`:`RegistryObject<MobEffect> MARKED`,1.20.1 `Player#hasEffect(MobEffect)` —— 用 `ModEffects.MARKED.get()`,正确(**F 无 `Holder` 体系,写法差异属平台必需,非缺陷**)。
- **收集阶段先于任何移除动作**:收集循环 `:178-182`,门控判定 `:189`,移除循环 `:192-194` —— 门控在**任何** `ModEffectRemoval.remove` 之前求值,不会被同批清除影响(与 `docs/batch3/PROGRESS.md:107` 的自述一致,现已独立复核)。`setHealth(1.0F)`(`:176`)不是效果移除动作,不影响门控。字节码偏移亦可佐证顺序:`hasEffect` 在 159/162(N)、151/160(F),`ModEffectRemoval.remove` 在 211(N)、209(F)。
- 门控的语义边界(如实记录,非缺陷):`GLOWING` 是**单实例**效果,若玩家先被光灵箭打上发光、又被本模组标记,标记期间该发光实例会被一并移除 ⇒ 光灵箭发光提前结束(AGENTS.md 已把该点写成「口径收窄」)。这是单实例效果的固有边界,实现者亦在 `PROGRESS.md:108` 记为遗留取舍。

### ④-4 移除了哪些 / 保留了哪些(两版本一致)

| 类别 | 处理 | 依据 |
|---|---|---|
| `MobEffectCategory.HARMFUL` 全部实例(含本模组 `MARKED`、`blue_curse`、`weak_mark` 等) | **移除** | `:179-181` |
| `GLOWING`(NEUTRAL,**仅当玩家确实带 `MARKED`**) | **移除** | `:189-190` |
| `MobEffectCategory.BENEFICIAL` 全部(力量/迅捷/再生…以及本模组 `dice_blessing` 等) | **保留** | 收集条件只认 `HARMFUL` |
| `MobEffectCategory.NEUTRAL` 其它(如 `GLOWING` 之外的) | **保留** | 同上 |
| `GLOWING` 但**无** `MARKED`(纯光灵箭/`/effect` 来源) | **保留** | 门控 `:189` |
| 本模组命名空间效果的「外部清除守卫」 | 未动,移除仍走 `ModEffectRemoval` 内部通道 | `:193` + `ModEffectRemoval.java:29-34`(`internal=true` 放行);`ModEffectEvents.java:114`(N)/`:110`(F) `if (ModEffectRemoval.isInternal()) return;` |

补充正确性论据(顺序无关性):`effectsToRemove` 先装 HARMFUL(迭代序不定)、**再**追加 `GLOWING`,即 `GLOWING` 必被**最后**移除 ⇒ 「标记仍存在时不得单独清发光」的守卫语义即使生效也不会挡住本次移除;何况 `ModEffectRemoval` 的内部标志本就使其放行(`ModEffectEvents` 的发光守卫在 `internal` 早退**之后**才判定)。⇒ ④ 的移除序列**不会**被自家守卫吃掉(即「改了但被拦掉」的风险已排除)。

---

## ⑤ C4b — forge `ModEffectRemoval` javadoc 笔误

**结论:PASS(N/F 均 PASS)** — F 的笔误已改且指向真实调用方;N 侧原本正确且**未被改动**。

- F `event/ModEffectRemoval.java:9` 现文:`* <p>效果移除拦截器({@link ModEffectEvents#onModEffectRemovalPrevented})会拦截`
  - 指向的类**确实存在**:`F event/ModEffectEvents.java:104-107`(`@Mod.EventBusSubscriber` + `public class ModEffectEvents` + `public static void onModEffectRemovalPrevented(MobEffectEvent.Remove event)`)。
  - 是**真实调用方**:`F event/ModEffectEvents.java:110` `if (ModEffectRemoval.isInternal()) return;` —— 即该方法确实读取本类(ModEffectRemoval)的内部标志,注解指向的语义关系成立。
- 改前(`216f6cc`)该行为 `{@link ModEventHandlers#onModEffectRemovalPrevented}`;`git ls-files`/全仓 grep 确认 **`ModEventHandlers` 类已不存在**(残留文本仅出现在 `docs/` 的历史记录里:`docs/batch3/PROGRESS.md:114`(本批自述)、`docs/scan2/D-discrepancy-table.md:69`、`docs/scan2/R1a-damage.md:140`、`docs/compat-1.20.1-forge.md:75`),属**真实**坏链接修复。
- N 侧同一 javadoc 原文为 `{@link ModEffectEvents#onModEffectRemovalPrevented}`(`N event/ModEffectRemoval.java:10`),**本批未改动**(`git show --stat 027e1e9` 的文件清单中**不含** `neoforge-1.21.1/.../ModEffectRemoval.java`)⇒ 符合「N 原本正确、不应被改动」。
- 两版本该类除 `MobEffect` vs `Holder<MobEffect>` 的 API 差异外一致(其余 34 行逐行同文)。

---

## 回归面(逐条 `命令 | 退出码 | 关键输出行`)

> 全部命令均在仓库根 `F:\MCProject\astral_dice_multiloader` 执行。`scripts/verify/` 下共 **5 个** `.ps1`(另有模块 `ChipCommon.psm1`、`__pycache__/`,非脚本),**逐个全跑**。工具链清单与 `scripts/test/TESTING-SPEC.md:288-298`(§9 静态守门)一致。

### R-1 lang 同步(两版本)

| 命令 | 退出码 | 关键输出行 |
|---|---|---|
| `pwsh -NoProfile -File tools/check_lang_sync.ps1 -LangDir neoforge-1.21.1/src/main/resources/assets/astral_dice/lang` | **0** | `OK: zh_cn.json(614 keys) 与 en_us.json(614 keys) key 完全一致。` |
| `pwsh -NoProfile -File tools/check_lang_sync.ps1 -LangDir forge-1.20.1/src/main/resources/assets/astral_dice/lang` | **0** | `OK: zh_cn.json(614 keys) 与 en_us.json(614 keys) key 完全一致。` |

⇒ **仍为 614/614,两版本全绿** ✅

### R-2 tooltip 染色审计

| 命令 | 退出码 | 关键输出行 |
|---|---|---|
| `pwsh -NoProfile -File scripts/audit/tooltip_color_audit.ps1 --root .`(§9 规定写法) | **0** | `tooltip 染色审计： PASS（无违规）` |
| `pwsh -NoProfile -File scripts/audit/tooltip_color_audit.ps1`(无参形式,附加) | **0** | `tooltip 染色审计： PASS（无违规）` |

### R-3 `scripts/verify/` 全部脚本(逐个)

| # | 命令 | 退出码 | 关键输出行 |
|---|---|---|---|
| 1 | `pwsh -NoProfile -File scripts/verify/verify_bountiful_instance_exclusions.ps1` | **0** | `汇总: 目标条目 31 | 已覆盖 31 | 泄漏 0 | 错误 0` / `RESULT: ALL CLEAR` |
| 2 | `pwsh -NoProfile -File scripts/verify/verify_bountiful_pools.ps1` | **0** | `OK neoforge-.../astral_objs.json 条目 13 与规则一致` … `OK 双版本一致 astral_rews.json md5 c656ca…` / `结果: ALL OK` |
| 3 | `pwsh -NoProfile -File scripts/verify/verify_chip_acquisition.ps1` | **0** | `✅ chips: 一致=True 差异=[]`(4 项对等) / `RESULT: PASS（致命项 0）` |
| 4 | `pwsh -NoProfile -File scripts/verify/verify_chip_recipes.ps1` | **0** | `[java] 筹码 59 | 一致 59 | 不一致 0 | 缺配方 0`(neo/forge 各一行) / `RESULT: ALL OK` |
| 5 | `pwsh -NoProfile -File scripts/verify/verify_content_library.ps1` | **0** | `[OK] 计数基线：汇总标签 {'chips': 60, 'signs': 17, 'dices': 13, 'materials': 14}` / `ALL OK —— 内容库与工程实际一致（33 物品 / 6 效果 / 双版本）` |

⇒ **5/5 全绿** ✅(bountiful 实例审计中「无 bountiful jar，跳过」为脚本既定的条件跳过,其唯一有 jar 的实例 `D:\.minecraft\versions\狐の航空学 Voxy Edition` 判定 `CLEAR`,泄漏 0)

### R-4 CHANGELOG 一致性(自行计数,未采信 47↔47)

自行按「`##` 段区间内 `^- ` 行数」计数(脚本:`Select-String '^## (未发布|Unreleased)\s*\(1\.2\.1\)'` 截段到下一个 `##`):

| 文件 | 段区间 | 顶级 `- ` 条目数 | 子节序列 | 空行数 |
|---|---|---|---|---|
| `CHANGELOG_ZH.md` | 6–77 | **47** | 新内容 / 内容与平衡性调整 / 已修复BUG / 工程 / 工程 | 18 |
| `CHANGELOG.md` | 6–77 | **47** | New Content / Content & Balance / Bug Fixes / Project / Project | 18 |

⇒ **47 ↔ 47,条目数、子节序列(含相对位置)、空行结构完全对应** ✅

本批对 CHANGELOG 的改动(`git show 027e1e9 -- CHANGELOG_ZH.md CHANGELOG.md`)是**两文件各 4 行**:3 条既有条目改写(电磁炮/敌对玩家规则合并进原条目、出牌轮收尾口径、保命清效果)+ 1 条新 `工程` 条目 —— **中英严格镜像,未新增“再次修改”类条目**,符合「合并约定」。

> 记:两文件在 1.2.1 段内各有**两个 `### 工程` / `### Project`** 子节标题(第 59 行与 73 行)。经与父提交 `216f6cc` 比对,**该重复标题在父提交中已存在**(父提交 ZH `:59`、`:73`),**不是本批引入**;两文件一致,不判缺陷(仅记录为历史格式冗余)。

### R-5 `AGENTS.md` 与代码实况一致性(本批四处更新)

| # | AGENTS.md 条目(行号) | 声称 | 代码实况 | 判定 |
|---|---|---|---|---|
| 1 | 饰品卸下 `onUnequip`(`:327`) | 已知缺口**仍未修复,待用户裁定**;tick 路径第 3 参是 copy 快照 ⇒ GUI 卸下时物品组件归零写进副本丢失;**死亡路径已覆盖**;三种候选修法待裁定 | `git show --stat 027e1e9` **不含** `CurioSlotUtil`/任何立牌类 ⇒ 本批确未改行为 ✅;`CurioSlotUtil.isIntentionalUnequip` 判据与该条描述逐条相符(N `:122-130` / F `:123-131`:第 3 参空 ⇒ 不清理;第 2 参空 ⇒ 清理;同物品 ⇒ 不清理;异物品 ⇒ 清理);「死亡路径已覆盖」确有实现:`PlayerLifecycleHandler`(N)`@SubscribeEvent(priority=LOWEST)` `:116` + `findFirstCurio` `:148/157/165`(misaki/jasmine/padman) | **PASS**(如实记录未修复) |
| 2 | 敌对玩家规则 + 统一现状(`:1250-1251`) | 两参重载语义;25 个调用点 / 17 文件 / 23 两参 / 2 单参;RailgunBolts 委托;电磁炮落雷 `viewer = bolt.getCause()` | 已自行 grep 复核:25 / 17 / 23 / 2 **全部相符**(见 ②-6);`RailgunBolts.java:64` 确用两参 | **PASS** |
| 3 | 轮次归零统一口径(`:368`、`:370`) | `clearRoundBonuses` = 4 个键 + `disarmAoe`;三条归零路径共用;**现仅三处调用**;`onRoundFullyReset` 只在 tick 情形 1 与 registerPlay 边界两处 | 逐条相符:`EffectCardPeriod.java:194-199`(F `:195-200`)恰为 4 个 setter + `disarmAoe`;`clearRoundBonuses` 调用点 = `:225 / :328 / :404`(F `:226 / :329 / :405`)恰 3 处;`onRoundFullyReset` 调用点 = `:330 / :406`(F `:331 / :407`)恰 2 处;父提交中 `disarmAoe` 只在 `tick` 情形 1(N `:397`)与描述一致 | **PASS** |
| 4 | 保命白名单(`:~1259`) | 只清 `HARMFUL` **外加**随标记施加的 `GLOWING`(显式白名单,绝不 `removeAllEffects`);仅当玩家确实带本模组 `MARKED` 时才连带移除;两版本同步 | 与 `EnderDiceHandler.java`(N `:177-194` / F `:176-193`)逐条相符;两版本语义等价(Holder/`.get()` 差异除外) | **PASS** |

**未发现 AGENTS.md 与代码不符之处。** 另核实:`AGENTS.md` 未出现 `isHostile` 计数以外的旧数字残留;`docs/` 内 `ModEventHandlers` 残留(`PROGRESS.md:114` 为本次修复自述、`docs/scan2/*` 与 `docs/compat-1.20.1-forge.md:75` 为历史记录)**不在源码内**,`src/` 下 0 命中。

### R-6 `git show --stat 027e1e9` 改动清单核定(是否夹带无关改动)

```
AGENTS.md                                                    | 14 +++++++-------
CHANGELOG.md                                                 | 10 ++++++----
CHANGELOG_ZH.md                                              | 10 ++++++----
forge-1.20.1/.../damage/RailgunBolts.java                    | 15 ++++++++++-----
forge-1.20.1/.../event/EnderDiceHandler.java                 | 16 +++++++++++++---
forge-1.20.1/.../event/ModEffectRemoval.java                 |  2 +-
forge-1.20.1/.../item/card/EffectCardPeriod.java             | 19 ++++++++++++++-----
neoforge-1.21.1/.../damage/RailgunBolts.java                 | 15 ++++++++++-----
neoforge-1.21.1/.../event/EnderDiceHandler.java              | 16 +++++++++++++---
neoforge-1.21.1/.../item/card/EffectCardPeriod.java          | 19 ++++++++++++++-----
10 files changed, 94 insertions(+), 42 deletions(-)
```

- 「应改文件」= ② `RailgunBolts` ×2、③ `EffectCardPeriod` ×2、④ `EnderDiceHandler` ×2、⑤ `ModEffectRemoval`(仅 F)= **7 个源文件**,加文档 3 个(CHANGELOG ×2 + AGENTS.md)= **10 个**,与 stat **完全吻合**;
- 逐 hunk 复核(全量 diff):**每个 hunk 都落在上述四项或对应文档口径内**——`RailgunBolts` 只改 `isValidLightningTarget` 与 javadoc;`EnderDiceHandler` 只加 `import ModEffects` + 效果列表/门控;javadoc 与 `clearRoundBonuses` 变更是 `EffectCardPeriod` 的全部改动;`ModEffectRemoval` 只有 1 行 javadoc;**无夹带的无关源码/资源/数据包改动**,也无对其它文件的顺带格式化。
- 未纳入的项:`D-B1`(GUI 卸下)如题**未改**,与提交信息一致(⑤ 之外无其它 item 混入)。

### R-7 产物与源码一致性(替代「重新构建」的独立证据)

硬约束禁止重新构建,故改用**已部署产物反编译**核对(见文首方法说明):

- `run/1.21.1/mods/astral_dice-1.2.1+neoforge_1.21.1.jar`(2026/9/15 17:38:38)与 `run/1.20.1/mods/astral_dice-1.2.1+forge_1.20.1.jar`(17:38:55)均早于提交时间 17:39:28;
- `javap` 结果(两版本一致):`LightningBolt.getCause:()Lnet/minecraft/server/level/ServerPlayer;` + `HostileTargets.isHostile:(Entity;Entity)Z`(②);`ElectricGloveChipItem.disarmAoe` 出现在 **`clearRoundBonuses` 方法体内**(偏移 20-21)(③);`ModEffects.MARKED` + `Player.hasEffect` + `MobEffects.GLOWING` + `ModEffectRemoval.remove` 全部出现在 `EnderDiceHandler` 的保命处理里,且 `hasEffect` 偏移(159/162 N、151/160 F)**早于** `remove` 偏移(211 N、209 F)(④)。
- ⇒ 「源码已编入产物、且新 API 在生产类路径上解析成功」有独立证据;但**本 R2 未重跑编译**(受硬约束),故这一条只作编译通过性的**佐证**,不等同于一次全新构建。

---

## 总体判定

| 项 | N(neoforge-1.21.1) | F(forge-1.20.1) | 说明 |
|---|---|---|---|
| ② D-B2 电磁炮双参敌对口径 | **PASS** | **PASS** | `getCause()` 为 null **不 NPE**、语义等价改前;两文件字节相同 |
| ③ C1 `disarmAoe` 上移 `clearRoundBonuses` | **PASS** | **PASS** | 三条归零路径口径统一;未发现过早解除武装的新路径 |
| ④ C4 保命追加移除 `GLOWING`(带门控) | **PASS** | **PASS** | 白名单显式、门控真实、收集先于移除、增益未误清 |
| ⑤ C4b forge javadoc 笔误 | **PASS**(原本正确、未被改) | **PASS** | 指向真实存在的 `ModEffectEvents#onModEffectRemovalPrevented` |
| 回归面 R-1…R-7 | 全绿 | 全绿 | lang 614/614 ×2、tooltip PASS、verify 5/5、CHANGELOG 47↔47、AGENTS 4/4 相符、无夹带改动 |

**FAIL:0 条。无法判定:0 条。** 发现的**新问题**(均非本次改动引入、不影响判定,但如实上报):

1. **`disarmAoe` 调用点计数不实(轻微文档问题)**:提交信息与 `AGENTS.md` 若按「全仓仅剩 1 处调用点」理解则不成立 —— 全仓实为 **2 处/版本**(`EffectCardPeriod.java:199/200`(=归零路径)与 `SpellDamageRegistry.java:384`(改前既有的「触发即消耗」路径))。准确表述应为「`EffectCardPeriod` 内 1 处、全仓 2 处」。**无代码缺陷,无需修法**。
2. **CHANGELOG 1.2.1 段存在重复子节标题**(`### 工程` / `### Project` 各两个,`CHANGELOG_ZH.md:59,73` / `CHANGELOG.md:59,73`):**父提交 `216f6cc` 已存在**,非本批引入,中英一致;仅历史格式冗余,可选清理。
3. **`bolt.getCause()` 为 null 的场景(用户最担心的一条)已排除风险**:两参重载在 `viewer == null` 时于 `HostileTargets.java:56` 直接返回 false,**不会 NPE,也不会走"无队伍 ⇒ 全体敌对"的宽判**;且生产路径上非本模组闪电被 `LightningBoltStrikeScopeMixin.java:46` / `EntityThunderHitMixin.java:45` 提前挡掉,**根本不会进入本判定**。唯一残留的语义边界是「`executeStrike` 被以 `cause == null` 调用时,落雷只打生物」——这与筛选受害者所用口径同源(`RailgunChipItem.java:146`),前后自洽。
4. (附带核查)AGENTS.md「饰品卸下」条的**Curios 内部机制细节**(第 3 参 = `getPreviousStackInSlot` 的 copy 快照)本 R2 **未做完全独立复核**:已确认 `ICurio.getStack()` 确实是桥接传入的第 3 参来源(`javap` Curios 1.20.1 jar:`ItemizedCurioCapability.onUnequip` → `ICurioItem.onUnequip(slotContext, newStack, this.getStack())`),但「该 `getStack()` 即 copy 快照」这一具体来源未继续追证。它属 `D-B1`(明确不在本批 4 项内、且本批未改行为),**不影响 ②③④⑤ 任何一项的判定**。

### 硬约束遵守声明

- 本次验证**只写** `docs/batch3/R2-verification.md`(该路径被 `.gitignore:58` 的 `docs/` 覆盖,`git status --porcelain` 在本文件写入后仍应显示为空 —— 可用 `git status` 复核);
- 未修改任何源码 / lang / CHANGELOG / AGENTS.md;未执行 `git commit` / `git push`;未重新构建、未启动游戏;
- 所有结论均有 `文件:行号` 或命令退出码/输出行为证;无「看起来没问题」式的 PASS。
