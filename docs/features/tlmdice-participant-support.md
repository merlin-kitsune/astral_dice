# 骰战参与者与非玩家骰子能力（附属模组接入契约）

> 批次：2026-10-28 dev 线（`multi-dev-next`）。两条需求合并为同一个功能面：
>
> - **参与者**：非玩家 Mob（如第三方宠物）可被显式注册后参与骰战结算；
> - **骰子能力**：参与者 Mob 手里的骰子可经注册表改写掷点（内置诡异 / 绯红 + 第三方注册）。

## 1. 目的与边界

- 让「骰战」不再局限于「玩家攻击」与「生物打玩家」两条老路径，第三方宠物可用自己的骰子参与结算。
- **不**放宽成「所有生物打所有生物」：进入结算必须由附属模组**显式注册**。
- 本模组**不提供**任何配置项 / 数据包标签 / 命令 / 自动发现入口；未注册的实体永远不是参与者。
- 源码内**不出现**任何第三方模组名：本模组对参与者只做类型/身份判定，不认识具体宠物类。

## 2. API 契约

### 2.1 `com.merlinkitsune.astral_dice.combat.DiceCombatParticipants`

| 方法 | 语义 |
|---|---|
| `public static void registerExtraAttacker(String owner, Predicate<LivingEntity> predicate)` | Setup 期显式注册；`owner` 只用于调试日志；`predicate` 为 `null` 忽略；同一谓词实例重复注册幂等 |
| `public static void registerExtraAttacker(Predicate<LivingEntity>)` | 兼容重载，`owner` 记为 `unknown` |
| `public static boolean isParticipant(LivingEntity attacker)` | 注册表为空时**无条件** `false`；谓词抛异常吞掉并 `LOGGER.debug` |
| `public static void clearForTests()` | 仅测试台 / 热重载使用 |

判定器运行在服务端伤害热路径上：**只允许做类型/身份判定**（不读存档、不掷骰、不发网络包）。

### 2.2 `com.merlinkitsune.astral_dice.combat.DiceCombatAbilities`

| 成员 | 语义 |
|---|---|
| `public static void register(Predicate<ItemStack> dieMatcher, DiceRollAbility ability)` | 注册「某类骰子」在**非玩家持有者**手里的掷骰能力；`null` 忽略；`matcher + ability` 引用相等幂等 |
| `public static final class RollContext` | 只读上下文：`holder`（掷骰实体）、`die`（骰子栈）、`opponent`（对立方）、`attackerSide`（攻/守）、`sides`（当前 6）、`defaultRoll`（未改写时的均匀掷点） |
| `public interface DiceRollAbility { int roll(RollContext); }` | 返回本次骰点，**必须落在 `1..sides`** |
| `public static void clearForTests()` | 只清第三方注册；内置能力始终有效 |

裁决顺序：**内置优先**（诡异骰 → 低面偏置；绯红骰 → 高面偏置 + 掷出 1 自伤 6）→ 再按注册顺序取第一个命中的第三方能力；一颗骰子最多执行一个能力。
能力越界（不在 `1..sides`）、抛异常、matcher 抛异常 ⇒ 一律退回 `defaultRoll`，并 `LOGGER.debug`，不影响结算。

**能力只能改骰点，不能改结算**：攻防值、乘算、写回仍由本模组的管线决定。

## 3. 管线语义（参与者 Mob 打任意 `LivingEntity`）

1. 入口闸门：`if (directEntity instanceof Mob mob && (target instanceof Player || DiceCombatParticipants.isParticipant(mob)))` ⇒ 走 `resolveMobMeleeAttack`，否则原样返回（原版伤害照常落地）。
2. 早退：客户端；`source.getDirectEntity() != mob`；`target == mob`（自伤）。
3. 对骰成立：`attackerHasDice || targetHasDice`（任一方有骰）。攻击方骰子**只有**在 `isParticipant(mob)` 时才会去取 —— 这条短路是回归红线的一部分。
4. 攻击骰：有骰 ⇒ 走能力注册表；无骰但对骰成立 ⇒ 均匀 1d6；否则 0。
5. 防守骰：防守方有骰 ⇒ 走能力注册表；否则 0。
6. 攻击力 = `TargetBattleStats.baseAttack(mob)` + 本线既有附魔合成项 + 攻击骰。
7. 防御力：目标是玩家 ⇒ 本线原公式（护甲 + 韧性 + 防御骰 + 防御牌点数）；非玩家 ⇒ `TargetBattleStats.baseDefense(target)` + 防守骰（**不再叠护甲/韧性**，避免二次计入）。
8. 结算：`DiceBattleResolver.resolve` → `magicAbsorbFactor` → 标记 `+1` → `victimFactor` → 虚弱印记 ×1.10 → 七咒倍率（**仅玩家目标**）→ 写回（各线各自 API）。
9. 出口：伤害数字组别 `ATTACK`；对骰成立时扣防御牌耐久（非玩家目标空转）；参与者带骰且满足条件 ⇒ 给**参与者自己**上骰神赐福。

## 4. 回归红线

- 未注册任何参与者 ⇒ `isParticipant` 恒 `false` ⇒ 闸门不再放行 mob→非玩家；`attackerHasDice` 因短路恒 `false` ⇒ **生物→玩家路径逐值不变**。
- 「不结算 ≠ 不掉血」：未注册时原版 `doHurtTarget` 伤害照常落地，测试判据必须是数值口径而非「血量不变」。
- 玩家攻击链、修饰器链、立牌钩子、筹码触发**零改动**。

## 5. 各线差异（实现侧）

| | neoforge-1.21.1 | forge-1.20.1 | neoforge-26.1.2 | fabric-1.20.1 |
|---|---|---|---|---|
| 事件类型 | `LivingDamageEvent.Pre` | 裸 `LivingDamageEvent` | `LivingDamageEvent.Pre` | 裸 `LivingDamageEvent` |
| Curios 入口 | `CuriosApi` | `CuriosCompat` | `CuriosApi` | 本仓 `compat.curios.CuriosApi` |
| 写回 | `event.setNewDamage` | `settleDiceDamage(...)` | `event.setNewDamage` | `event.setAmount` |
| 2026-10-27 审计块（标记 +1 / 虚弱印记 / 七咒） | 有 | 有 | 无 | 无 |
| `isDiceBattleSettledDamage` | 有（已同步放宽） | 有（已同步放宽） | 无 | 无 |

> fabric 线若将来补上 2026-10-27 审计块，必须同时补 `isDiceBattleSettledDamage` 的参与者放宽，否则「无效 ×1.21 双乘」。

## 6. 验证证据（1.21.1 实测，测试台）

资产：

- 探针 `scripts/test/resources/kubejs/1.21.1/server_scripts/astral_tlmdice_probe.js`（参与者 / 回归读数，命令 `/astraltlm`）
- 探针 `scripts/test/resources/kubejs/1.21.1/server_scripts/astral_tlmability_probe.js`（能力注册表读数，命令 `/astraltlma`）
- 数据包 `scripts/test/resources/kubejs/1.21.1/data/astral_dice/curios/entities/pillager.json`（给劫掠者一个 `dice` 槽，仅测试台）
- 用例 `scripts/test/cases/TLMDICE-PARTICIPANT-1.21.1.json`（58 断言）、`scripts/test/cases/TLMDICE-ABILITY-1.21.1.json`（26 断言）

关键读数（`latest.log`，`AP_A1_A_*`）：

```
AP_A1_A_SETUP:reg=ok:pred=1:part=1:slot=ok:prod=1:abl=fn
AP_A1_A_FN:n=3:dmg=15,15,15:abl=fn        # 4(敌对 baseAttack) + 5(ATTACK_DAMAGE) + 6(注册能力)
AP_A1_A_CLR:n=8:min=10:max=15:in=8:lt=7   # clearForTests 后回均匀 1d6
AP_A1_A_ONE:dmg=10                        # 能力恒 1 ⇒ 9+1
AP_A1_A_OOB:n=6:min=10:max=15:in=6        # 能力返回 99 越界 ⇒ 回退默认掷点
AP_A1_A_CRIM:n=120:dmg10=7:loss=42:rel=1:bad=0  # 绯红内置：掷 1 自伤 6，loss = 6×次数
AP_A1_A_WD:n=600:mean=2.74:low=0.76       # 诡异骰低面偏置（设计均值 2.75 / 0.75）
AP_A1_A_CD:n=600:mean=4.12:high=0.72      # 绯红骰高面偏置（设计均值 4.25 / 0.75）
```

判据口径提醒：测试台测「牛掉血」前**必须先把 `MAX_HEALTH` 抬到伤害之上**。原版牛 `MAX_HEALTH = 10`，`setHealth(20)` 会被钳到 10，导致 ≥10 的伤害全读成 10（本批排查浪费过一次）。

## 7. 消费方接入（TLMDice 等）

```java
// Setup 期（FMLCommonSetupEvent / ModInitializer#onInitialize）
DiceCombatParticipants.registerExtraAttacker("<yourmodid>", e -> e instanceof YourPetEntity);
DiceCombatAbilities.register(
        stack -> stack.is(YourItems.SPECIAL_DICE),
        ctx -> ctx.holder.getRandom().nextInt(1, ctx.sides + 1)); // 必须返回 1..sides
```

落地顺序（不可拆）：① 本仓发布带本功能的版本 ⇒ ② 消费方摘掉自算结算与 `finalDamage` 写回，改「只读不写」 ⇒ ③ 消费方修订自身 AGENTS / 文档。

## 8. 已知限制

- **防守侧不要求注册**：任何 `LivingEntity` 只要 Curios 里有骰子就会掷防守骰（用户 2026-10-28 裁决 q2）；接入门槛只作用于「谁可以**打出去**」。
- 一颗骰子只执行**第一个**命中的能力（内置优先，然后注册顺序）；没有优先级 / 让位 / 叠加机制。
- 非玩家没有 `WeaponEnhancement`、筹码栏、战斗牌：**2026-11-08（G6）起已按能力位放开** ——
  经 `DiceCombatParticipants` 注册的参与者若在骰子槽装有骰子/战斗牌，攻击侧跑 `DiceCombatModifiers`
  并计入攻击牌点数、防御侧掷防御牌；未注册生物仍全为 0（回归红线）。
- 玩家专属的账户/背包类加成（星币锤、七咒、狐光快照、治愈点等）对注册生物**按不适用跳过**；
  护法立牌的名刀折扣只对玩家生效（注册生物按基础费用）。
- 26.1.2 / fabric 两线仅有编译与静态证据，**未实机验证**。

## 9. 代码锚点（各线）

| | neo-1.21.1 | forge-1.20.1 | neo-26.1.2 | fabric-1.20.1 |
|---|---|---|---|---|
| 入口闸门 | `DiceCombatEvents.java:334-344` | `:647-655` | `:317-328` | `:276-283` |
| 对骰块 / 取骰 | `:1112-1131` | `:1427-1446` | `:1054-1073` | `:1007-1026` |
| `rollCombatDieFor` | `:1758` | `:2051` | `:1535` | `:1470` |
| `findFirstCurioMatching` | `CurioSlotUtil.java:93` | `:94` | `:77` | `:78` |
| 权重骰（诡异） | `WeirdDiceHandler.java:57/70` | `:57/70` | `:50/63` | `:48/61` |
| 权重骰 + 自伤（绯红） | `CrimsonDiceHandler.java:43/59/80` | `:43/59/80` | `:43/59/80` | `:43/59/80` |
| 参与者注册表 | `DiceCombatParticipants.java:39/60/73` | 同 | 同 | 同 |
| 能力注册表 | `DiceCombatAbilities.java:84/103/117` | 同 | 同 | 同 |
