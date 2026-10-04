# P4 状态机 / 周期 / 资源池 / 计数器(第二轮只读审计)

> 范围:`docs/execution-order-checklist.md` §D(状态机与周期)+ §F(资源池与计数器)。
> 纪律:每条结论 = `文件:行号` + 代码原文(A/B 分开)+ 可达性 + 最小实机验证步骤 + 严重度;不可达路径不列为缺陷。
> 本文件是本次**唯一**写入的文件;未修改任何源码/资源/配置。

---

## 0. 基线与并发声明(必读)

**基线**:

```text
$ git rev-parse HEAD
d48a529ba5fcf14610dc7b7383a10780dbe49168      (分支 multi-1.20.1-1.21.1)
```

**`git diff --name-only`(15:41 冻结快照;共 28 行 = 14 个文件 × 2 子项目,全部为工作区未提交改动)**:

> 注意:该快照在扫描期间**持续增长** —— 15:41 之前为 4 行(仅 `PlayerLifecycleHandler`×2 + `ResourceConversion`×2),
> 15:41 为下面 28 行,审计结束时为 **36 行**(又新增 `DiceCombatEvents`、`component/ModAttachments`、
> `event/ChipDamageHandler`、`event/EnderDiceHandler`,详见 §0.1 末尾的第二次冻结说明)。

```text
forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/combat/DiceCombatModifiers.java
forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/combat/HostileTargets.java
forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/PlayerLifecycleHandler.java
forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/PlayerTickEvents.java
forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/CurioSlotUtil.java
forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/card/EffectCardPeriod.java
forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/chip/BaseChipItem.java
forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/dice/DiceCurioItem.java
forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/dice/NetherStarDiceItem.java
forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/BaseSignItem.java
forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/BonnieSignItem.java
forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/HaiqingSignItem.java
forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/MosesSignItem.java
forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/resource/ResourceConversion.java
neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/combat/DiceCombatModifiers.java
neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/combat/HostileTargets.java
neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/event/PlayerLifecycleHandler.java
neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/event/PlayerTickEvents.java
neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/item/CurioSlotUtil.java
neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/item/card/EffectCardPeriod.java
neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/item/chip/BaseChipItem.java
neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/item/dice/DiceCurioItem.java
neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/item/dice/NetherStarDiceItem.java
neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/BaseSignItem.java
neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/BonnieSignItem.java
neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/HaiqingSignItem.java
neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/MosesSignItem.java
neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/resource/ResourceConversion.java
```

> 另有未跟踪文件 `*/src/main/resources/data/minecraft/tags/damage_type/bypasses_cooldown.json`(与本切片无关)。

### 0.1 扫描期间的并发改动(影响的是"结论还是不是新发现",必须逐条标注)

本次扫描**与另一批代理的 13 项改动重叠**。扫描开始时 `git diff --name-only` 只有 4 行(`PlayerLifecycleHandler`×2、`ResourceConversion`×2);扫描中途(约 15:39–15:45)**A9 与 S4-C6、S6-C1/C2 的改动整体落盘**,文件总数先变为 28 行,审计结束时为 36 行。因此:

| 文件 | 扫描结束时状态 | 对本报告的影响 |
|---|---|---|
| `item/card/EffectCardPeriod.java` | **改动中(已落盘 A9)** | §D-1~D-4 是对**已落盘实现**的核验(不是对提案的推演);行号 = 落盘后 |
| `event/PlayerLifecycleHandler.java` | **改动中(已落盘 S4-C6 删除 + LOWEST)** | §D-7 核验"删除无效项"的正确性 |
| `resource/ResourceConversion.java` | **改动中(已落盘新兑换口径)** | §F-1 核验新口径;§F-2 指出未覆盖的相邻缺陷 |
| `item/sign/BaseSignItem.java`、`Haiqing/Bonnie/MosesSignItem`、`PlayerTickEvents.java` | **改动中(已落盘 S6-C1/C2)** | §D 矩阵中 `sign_ready_*` 一行的清理路径 |
| `CurioSlotUtil`、`BaseChipItem`、`DiceCurioItem`、`NetherStarDiceItem`、`HostileTargets`、`DiceCombatModifiers` | 改动中 | 仅与 §E/§G 相关,本报告只在矩阵里引用其结论 |
| 计数器相关文件(`KomachiSignItem`/`MagicTomeChipItem`/`MimiSignItem`/`CursedSwordChipItem`/`PiggyBankChipItem`/`FlashlightChipItem`/`EightSidedDiceChipItem`/`ElectricSwordChipItem`/`InvestigationEventUtil`/`EmpowerManager`/`StarLightManager`/`HealingManager`/`MarkManager`) | **未在 diff 中(稳定)** | §F 的行号可长期引用 |

> **凡是引用 `EffectCardPeriod` / `PlayerLifecycleHandler` / `ResourceConversion` / `BaseSignItem` 的行号,读者必须意识到这些文件仍在被改**:行号可能再次整体平移。本报告所有行号都是 **2026-09-15 15:45 前后**在工作区实测的当前值(A 侧逐行读取,B 侧逐行读取或由并列复核给出)。

**审计结束后又观测到的新改动(第二次冻结快照,36 行 = 18 文件)**:除上表外,另有
`combat/DiceCombatEvents.java`、`event/ChipDamageHandler.java`、`event/EnderDiceHandler.java`、
**`component/ModAttachments.java`** 被改。其中 `ModAttachments` 追加了一个新键
`SIGN_ACTIVE_MAX_COOLDOWN`(A `:416` / B `:386`,属"枪匠冷却基准"项,不在本切片范围),
它插在 neo `:413` / forge `:383` 之后 ⇒ **本报告中对 `ModAttachments` 的行号引用凡在该点之后的都已 +15**,
下面三处已按新值更新(用 `[现值]` 标注),读者若看到漂移请以"常量名"为准定位:

| 引用处 | 报告内引用 | 当前实际行号(A) | 当前实际行号(B) |
|---|---|---|---|
| D-4 `ELECTRIC_GLOVE_AOE` | A `:888-892` / B `:786-787` | 888(定义) | 786(定义) |
| D-5 `NANCY_LU_PASSIVE_TYPE` | A `:587-591` / B `:518-519` | 587 | 518 |
| F-6 `MAGIC_QUIVER_TRACKING/_FIRST_CARD/_COOLDOWN_END` | A `:516-531` / B `:473-481` | 516/522/528 | 473/477/481 |
| D-7 `INVESTIGATION_STAGE` | A `:334-337` / B `:304-305` | 334(未受插入影响) | 304(未受插入影响) |
| F-7 `EIGHT_SIDED_ROLL_ACCUM` | A `:29-33` / B `:35-36` | 29(未受插入影响) | 35(未受插入影响) |

---

## 1. 结论速览

| ID | 严重度 | 一句话 | 归属 | 是否新增 |
|---|---|---|---|---|
| **D-1** | **高** | A9 的"补齐计数到上限"把"空闲重置计时器"变成"硬锁 30 秒 + 剩余出牌数静默作废";max≥2 的玩家每轮实际只能出 1 张 | §D 状态机 | **新增**(对刚落盘的 A9) |
| **D-2** | 中 | A9 下忍者主动「忍术连击」的 +1 可在 ≤20 tick 内被 `clearRoundBonuses` 抹掉,而主动技能 180 秒冷却已扣 → 一次白扣冷却的主动 | §D + §F 计数器 | **新增** |
| **D-3** | 中低 | A9 后 GUI 在"只出过 1 张"的轮显示「本轮出牌数已用完!剩余冷却 X 秒」,tooltip 显示 `max/max` | §D GUI/状态一致性 | **新增** |
| **D-4** | 中 | `clearRoundBonuses` 的"唯一入口"不含 `electric_glove_aoe`;周期经 `registerPlay` 结束时不解武装,且该键跨重登保留 → 免费 AoE(证实 S5-C3 并补"重登"维度) | §D 边界唯一性 | 证实既有 |
| **D-5** | 中 | 重登用 `removeEffect` 移除赐福**不派发 `MobEffectEvent.Expired`** → 赐福结束钩子链整体不执行:星币锤加成残留(既有 S4-C13)+ **骇客被动 +3 攻/防无限期残留**、枪匠弱点识破少减 1 层、蓄力→全力攻击不转换、银行卡用不完/大碗炖肉漏发 | §D 重登 | S4-C13 已记星币锤;**其余 5 项本轮补齐** |
| **D-6** | 低 | 死亡清单被删后**依然有效且必须保留**的是 `MISAKI_SIGN_STACKS` 段(物品组件);`HAIQING/BONNIE_READY` 的 `removeEffect` 与缺失的 `MOSES_READY` 都是无效项的不对称(无功能后果) | §D 死亡路径 | 核验既有改动 |
| **D-7** | 低 | `INVESTIGATION_STAGE` 注释称"死亡保留调查阶段进度",实测真死回默认 `1`(它不在复制集合) | §D 文档漂移 | 新增 |
| **F-1** | **高** | 新兑换口径本身正确,但**入口门槛没跟着改**:`ParunanSignItem` 只判 `get() > 0` 且忽略返回值 ⇒ 只有银行卡基础星光时"净扣 0 仍发奖励"(奖励从星币变成主动 3 选 1 增益) | §F 资源池 | **新增**(由本次改动引入的行为反转) |
| **F-2** | 中 | 三个筹码"装备即得星光"(+1/+3/+5)卸下不回收(既有 S4-C5);新口径只封 `base` 白嫖,**不封**这个装卸循环 ⇒ 仍可产出可兑换星光 | §F 资源池 | 既有,改动未覆盖 |
| **F-3** | 低 | `StarLightManager.spend` 返回"请求量"、`add` 返回"钳制前的 next"、`HealingManager.add` 可返回负值 —— 返回值口径三处不一致(唯一 caller 已改为差值口径,故当前不可达为缺陷) | §F 返回口径 | 新增(口径) |
| **F-4** | 低中 | 零调用死代码:`ChargeManager.clearDeathPreserved`(静态表 `DEATH_PRESERVED_STACKS` 因此**无任何登出清理**)、`HealingManager.spend`、`HealingManager.tick` 的"上限收缩"分支(上限是常量 32,永远不触发) | §F 死代码 | 新增 |
| **F-5** | 低 | `ElectricSwordChipItem.KILL_COUNTS` 是静态表:死亡/重登都不清(与附件类计数器口径不一致),且只在卸下筹码时清 | §F 计数器归属 | 新增 |
| **F-6** | 低中 | 计数器/结算不同处的两处:`magic_quiver_*`(记录在筹码、结算在 `SpellDamageRegistry`,且 `tracking`/`first_card` 跨重登保留 → 可在新会话凭旧记录返还一张牌)、`star_coin_hammer_bonus`(写在筹码、读在 `DiceCombatModifiers`、清在赐福结束) | §F 计数器 | 新增 |
| **F-7** | 低 | 诅咒之剑:旗标在"上限判定之前"置位 ⇒ 满 16 后本期首次达标击杀被消耗且无奖励;`eight_sided_roll_accum` 注释称"达到 32 星光后归 0"但代码有意保留余数 | §F 计数器 | 新增 |

---

## 2. §D 状态机与周期

### D-1 【高】A9「补齐计数」把空闲重置计时器变成硬锁,并静默作废剩余出牌数

```text
ID:D-1
核验:证实(对已落盘的 A9 实现;非提案推演)
现象:
  max≥2(大背包/忍术飞镖/活体书页/糖果/卫星/命运指引等任一加成存在)时,玩家出 1 张牌后
  被"效果待定"挡住;等该效果自然结束后的 ≤20 tick 内,出牌数被直接改写成 max 并起 30 秒冷却,
  于是 (a) 30 秒内完全无法出牌(isBurstFull 为真),(b) 本轮剩余出牌数(如 8 张)全部作废,
  (c) 玩家看不到任何"本轮已结束/剩余出牌数作废"的提示,只看到冷却提示。
顺序依赖:
  tick 的三分支顺序 = ①冷却中→return ②cooldown<=0:played<=0→return / played<max→
  「pending>0 则 return;否则 setEffectCardPlayCount(max)」/ played>=max→直接补冷却
  ③冷却到期→归零+clearRoundBonuses+disarmAoe。
  ★ 正确性关键:②中 `played >= max` 的"不变量修复"分支**不检查 pending**,而 `played < max` 分支
    会检查 —— 二者的先后顺序若被反转(把 pending 检查提到 max 判定之前),"已达上限但效果未结束"
    会被当作空闲轮处理,退回"永久锁死/无限出牌"两种退化之一。当前顺序是对的(本次核验通过)。
  新增的顺序问题来自 `setEffectCardPlayCount(player, maxAllowed)` 这一行:
  - `isBurstFull` = count>=max && cd 未到期 ⇒ 该行让"未打满的轮"在冷却期间**变成** isBurstFull;
  - 于是"冷却期间仍可继续出牌"(isBlocked 对 isCooldownActive 返回 false)这条既有设计被绕过,
    剩余出牌数在效果结束瞬间被丢弃。
证据:
  A(neoforge-1.21.1) item/card/EffectCardPeriod.java:327-343
    327: if (cooldown > 0 && now < cooldown) return;          // 冷却进行中:不动
    328: if (cooldown <= 0) {
    329:     if (played <= 0) return;                          // 无残留(不凭空开冷却)
    330:     int maxAllowed = getMaxAllowed(player);
    331:     if (played < maxAllowed) {
    334:         if (getRemainingBlockTicks(player) > 0) return;
    337:         ModAttachments.setEffectCardPlayCount(player, maxAllowed);   // ← 补齐 = 作废
    338:     }
    339:     long recoverTicks = ChargeManager.cooldownTicks(player,
    340:             GameplayConstants.EFFECT_CARD_COOLDOWN_SECONDS * 20L);
    341:     ModAttachments.setEffectCardCooldownEnd(player, now + recoverTicks);
    342:     return;
    343: }
  A 同文件 :200-206 isBurstFull(冷却未到期时 count>=max 即为"满")
    200: public static boolean isBurstFull(Player player) {
    201:     long cdEnd = ModAttachments.getEffectCardCooldownEnd(player);
    202:     if (cdEnd > 0 && player.level().getGameTime() >= cdEnd) return false;
    203:     int count = getPlayCount(player);
    204:     if (count <= 0) return false;
    205:     return count >= getMaxAllowed(player);
    206: }
  A 同文件 :256-260 isBlocked(冷却中不拦、只有 isBurstFull 拦)
    256: public static boolean isBlocked(Player player) {
    257:     if (isBurstFull(player)) return true;
    258:     if (isCooldownActive(player)) return false;
    259:     return isEffectPending(player);
    260: }
  B(forge-1.20.1) item/card/EffectCardPeriod.java:328-343 —— 与 A 逐行同构(仅行号 +1):
    328: if (cooldown > 0 && now < cooldown) return;
    329: if (cooldown <= 0) {
    330:     if (played <= 0) return;
    331:     int maxAllowed = getMaxAllowed(player);
    332:     if (played < maxAllowed) {
    335:         if (getRemainingBlockTicks(player) > 0) return;
    338:         ModAttachments.setEffectCardPlayCount(player, maxAllowed);
    339:     }
    342:     ModAttachments.setEffectCardCooldownEnd(player, now + recoverTicks);
    343:     return;
    344: }
  B 同文件 :201-207 / :257-261 同构。
达标性(max≥2 才是新行为):
  max=1(无任何加成,默认)时 played=1 直接走"修复"分支 ⇒ 与 A9 之前完全一致;
  只有 max≥2 才进入"补齐"分支 ⇒ 影响面 = 佩戴大背包/忍术飞镖/触发糖果/卫星/活体书页/命运指引的玩家。
可达性:可达(创造/生存均可)
  最小实机验证步骤:
   1) 创造模式,戴「大背包」+「忍术飞镖」筹码(max=3),手持「对怪激光」;
   2) 对敌对生物右键 1 次 → 观察 tooltip 计数 = 1/3,且 60 秒内再次右键被拒(效果待定,预期行为);
   3) 用 F3 或日志确认激光效果结束(60 秒)后,在 ≤1 秒内再次右键 →
      **预期(新行为)**:被拒并提示「本轮出牌数已用完!剩余冷却 ~30 秒」,tooltip 显示 3/3;
      **旧行为**:可以再出 2 张。
   4) 30 秒冷却结束后再右键 → 计数回到 0/3,可出牌。
   判定点:第 3 步是否在"只出过 1 张"的情况下显示 3/3 并锁 30 秒。
修法:最小 diff 方向 = 让"空闲收尾"只承担"计时器",不承担"锁定"与"作废":
  ① 删除 :337(A)/:338(B) 的 `setEffectCardPlayCount(player, maxAllowed)`,并在归零时保留
     `played` 语义;或
  ② 若确实要"作废剩余出牌数",则必须同时:把该轮改为**显式的新状态**(如 `bonus/remaining=0` 的可显示状态)、
     在 ActionBar 提示"本轮结束、剩余出牌数作废",并同步修 :265-268 的 `registerPlay` javadoc 与
     AGENTS.md「出牌周期状态机的不变量」(现文写"冷却严格按照「出牌数打满后才进入冷却」")。
  ⇒ **需用户先定夺口径**(方案①/②的取舍 + 是否要"作废"语义),再写 diff。
```

### D-2 【中】A9 下忍者主动「忍术连击」的 +1 可能在 ≤20 tick 内被抹掉,而 180 秒主动冷却已扣

```text
ID:D-2
核验:证实
现象:玩家先出一张效果牌(其效果进入待定,count=1)、再按忍者立牌主动 → grantBonusPlay 成功
  (bonus_plays=1,max 从 3 → 4,主动技能 180 秒冷却立即开始);该效果一结束,下一次 20 tick 轮询
  就把 count 补齐到 4 并起冷却,冷却结束后 `clearRoundBonuses` 把 bonus_plays 清 0。
  净结果:主动技能消耗了完整冷却,但 +1 从未被用上(甚至可能只存在不到 1 秒)。
顺序依赖:
  `KomachiSignItem.handleUse` 的三条前置只看 isCooldownActive / MAX_EFFECT_CARD_PLAYS / 本轮是否已授予,
  **不看"本轮剩余计时器"**;而 A9 的补齐把"授予的 +1"合并进 count 后一并作废。
  与既有契约冲突:KomachiSignItem:48-49 明文写"不随立牌装卸回收…若在此清除会造成'上限在周期中途下降'
  的不变量违例",A9 现在恰好造成一次"周期中途下降 + 已授予 +1 被回收"。
证据:
  A item/sign/KomachiSignItem.java:70-84
     70: if (EffectCardPeriod.isCooldownActive(player)) {   // 冷却中拒绝
     74: if (EffectCardPeriod.getMaxAllowed(player) >= GameplayConstants.MAX_EFFECT_CARD_PLAYS) {
     80: if (!EffectCardPeriod.grantBonusPlay(player)) {
  A item/card/EffectCardPeriod.java:171-175 grantBonusPlay 写 bonus_plays=1
  A item/card/EffectCardPeriod.java:184-189 clearRoundBonuses(0/…),:337 补齐 count
  A item/sign/BaseSignItem.java:119-125 —— 主动成功即写 sign_active_cooldown_end(180s 基准)
  B 同文件同构(KomachiSignItem.java:70/74/80;EffectCardPeriod.java:338/349;BaseSignItem.java:120-126)
可达性:可达
  最小实机验证步骤:
   1) 戴忍者立牌 + 大背包(max=3);
   2) 出 1 张「对怪激光」(count=1,激光效果 60 秒待定);
   3) **立即**按立牌主动键 → 应看到「出牌数+1/剩余出牌数」提示(bonus 已授予);
   4) 等 60 秒激光结束 → ≤1 秒内查看 tooltip/尝试出牌 → 出现"已用完"提示且 3→4 的余量不可用;
   5) 再按主动键 → 提示「立牌冷却中」(冷却确实被扣过)。
   判定点:第 3 步提示成功 + 第 4 步余量不可用 + 第 5 步冷却已扣 = 本缺陷成立。
修法:与 D-1 同源,取决于口径裁定;最小可选:空闲收尾的归零不触碰 `EFFECT_CARD_BONUS_PLAYS`
  之外,或在补齐前判断 `getBonusPlays(player) > 0` 时**不补齐**(让玩家把 +1 用掉)。
```

### D-3 【中低】A9 后 GUI 文案与实际状态不符("本轮出牌数已用完"出现在只出过 1 张的轮)

```text
ID:D-3
核验:证实
现象:补齐 count 后 `isBurstFull` 为真,客户端预检进入 :191 分支,ActionBar 显示
  「本轮出牌数已用完!剩余冷却 %s 秒」,而该轮实际只出过 1 张;同时物品 tooltip 显示 `3/3`。
顺序依赖:文案判定只看 isBurstFull(不含"是否真打满"),而 A9 让"未打满"也能满足它。
证据:
  A item/card/BaseEffectCardItem.java:191-196
    191: if (EffectCardPeriod.isBurstFull(player)) {
    192:     int seconds = EffectCardPeriod.getRemainingBlockSeconds(player);
    193:     player.displayClientMessage(
    194:             Component.translatable("msg.astral_dice.effect_card_burst_full", seconds), true);
    195:     return true;
    196: }
  A event/ModTooltipHandler.java:300 `EffectCardPeriod.getPlayCount(p), EffectCardPeriod.getMaxAllowed(p)`
  B item/card/BaseEffectCardItem.java:191-196 同文;B event/ModTooltipHandler.java:296 同构。
可达性:可达(同 D-1 步骤 3:提示里的"已用完"与实际只出 1 张矛盾)
  最小实机验证步骤:同 D-1 第 3 步;另把鼠标悬停在任意效果牌上读 tooltip 计数行(X/Y)。
修法:文案层与状态层二选一——要么回退 D-1 的补齐(推荐与 D-1 一并定夺),要么给"作废"新增文案
  (需新增 lang key,zh_cn/en_us + 双版本,Patchouli 手册同步规则见 AGENTS.md)。
```

### D-4 【中】周期归零的唯一入口不含 `electric_glove_aoe`;周期经 `registerPlay` 结束时不解武装(证实 S5-C3,并补"重登"维度)

```text
ID:D-4
核验:证实(第一轮 S5-C3 已报"经 registerPlay 结束时不解除";本轮补充:该键**跨重登保留**)
现象:本周期已武装的电击手套法伤扩散在下列两种情形不清除:(a) 周期由 registerPlay 的分支①/②
  结束(冷却已到期的 ≤19 tick 窗口内再出一张 / "上限中途下降"修复);(b) 玩家在武装状态下重登
  (键已序列化,登录清理不涉及该键)。两者都会让下一周期的第一张伤害效果牌**不再扣 4 层充能**
  即获得 AoE。
顺序依赖:`clearRoundBonuses` 被文档与代码注释称为"唯一入口",但它只含 4 个键;
  `disarmAoe` 只有两个调用点:tick 的"冷却到期"分支与 `SpellDamageRegistry:384`(实际触发扩散时)。
证据:
  A item/card/EffectCardPeriod.java:184-189(唯一入口,无 glove 项)
    184: public static void clearRoundBonuses(Player player) {
    185:     ModAttachments.setEffectCardBonusPlays(player, 0);
    186:     ModAttachments.setCandyChipPlayBonusActive(player, false);
    187:     ModAttachments.setSatellitePlayBonusActive(player, false);
    188:     ModAttachments.setLivingPageCycleBonus(player, 0);
    189: }
  A 同文件 :279-286(registerPlay 的周期边界只调 clearRoundBonuses)
  A 同文件 :344-351(只有"冷却到期"分支调 disarmAoe)
    348: clearRoundBonuses(player);
    350: com.merlinkitsune.astral_dice.item.chip.ElectricGloveChipItem.disarmAoe(player);
  A component/ModAttachments.java:888-892 ELECTRIC_GLOVE_AOE 有 `.serialize` + `.sync`,无 `.copyOnDeath`
  A event/PlayerLifecycleHandler.java:178-186(登录清理:只清 defenseCardConsumed / EffectTimerGuard / 赐福)
  B item/card/EffectCardPeriod.java:185-189 / :280-287 / :345-352 同构;
  B event/PlayerLifecycleHandler.java 登录分支同构(仅 EffectTimerGuard.clear + 移除赐福 + HealingManager.tick)
可达性:可达但需要构造;重登路径**极易复现**
  最小实机验证步骤:
   (a) 构造路径:装备电击手套并让充能≥4 → 用一张伤害效果牌武装 AoE(工具提示/日志确认)
       → 等该牌效果结束 → 用两张无待定效果的牌把上限打满 → 冷却到期后 ≤1 秒内再出一张
       → 检查 `electric_glove_aoe` 是否仍为 true(可用后续第一张伤害牌是否"不再扣 4 层充能"判定)。
   (b) 重登路径(推荐):武装 AoE 后直接退出并重新登录 → 第一张伤害牌是否仍不扣充能。
修法:把 `disarmAoe` 并入 `clearRoundBonuses`(唯一入口名副其实),重登路径可**不需要**单独补清理
  (因为 login 时若周期仍在,应保留?需口径:武装是"本周期"状态,重登后本轮若未结束——按 D-1 的
  A9 语义,登录后 pending 为 0 时下一次 tick 会收尾并 disarm ⇒ 修好 D-4 的入口即可覆盖两者)。
```

### D-5 【中】重登用 `removeEffect` 移除赐福不派发 `Expired` ⇒ 赐福结束钩子链整体不执行

```text
ID:D-5
核验:证实(S4-C13 已记「星币锤」一项;本轮补齐同源清单另 5 项)
现象:玩家在赐福期间退出并重登,`onPlayerLoggedInClearDiceBlessing` 用 ModEffectRemoval 移除
  DICE_BLESSING;原版只在**自然到期**时派发 MobEffectEvent.Expired ⇒
  `DiceCombatEvents.onDiceBlessingExpired` 的整条钩子链不执行,造成:
   ① star_coin_hammer_bonus 残留(既有 S4-C13:下次赐福星币≤20 时白吃旧加成);
   ② NANCY_LU_PASSIVE_TYPE 残留 → 骇客被动 +3 攻击/防御**无限期生效**(读侧只判"戴着立牌 &&
      passive_type==ATTACK/DEFENSE");
   ③ 枪匠 onDiceBlessingEnded 不执行 → 弱点识破少减 1 层;
   ④ 蓄力→全力攻击的转换不执行(该转换就写在同一 Expired 处理器尾部);
   ⑤ 银行卡-用不完的"赐福结束 3 星币"、大碗炖肉的"赐福结束 +1 治愈/回血"漏发。
顺序依赖:赐福"结束"有两条路径(自然到期 / 显式移除),而所有"结束结算"都只挂在 Expired 上;
  登录路径只做了"移除效果"这一半。治疗体系之所以没事,是因为它另有 prevBlessing 边沿检测
  (`HealingManager.tick`,登录时被显式调用)——同一份"边沿"问题在赐福钩子链上没有对应处理。
证据:
  A event/PlayerLifecycleHandler.java:185 `ModEffectRemoval.remove(player, ModEffects.DICE_BLESSING);`
    (登录分支:183-189 只做 defenseCardConsumed=false / EffectTimerGuard.clear / 移除赐福 / HealingManager.tick)
  A event/ModEffectRemoval.java:27-35(`player.removeEffect(effect)`,无任何 Expired 派发)
  反编译证据(neoforge-21.1.235-sources.jar → net/minecraft/world/entity/LivingEntity.java):
    807-810  `if (... post(new MobEffectEvent.Expired(this, mobeffectinstance)).isCanceled()) {...}`
             —— 只在 tickEffects 里"自然到期且 tick() 返回 false"时派发;
    1035-1039 `public boolean removeEffect(Holder<MobEffect> effect) { if (EventHooks.onEffectRemoved(...)) return false; ... }`
             —— 显式移除走的是 **Remove** 事件,不是 Expired。
  A combat/DiceCombatEvents.java:840-861 `onDiceBlessingExpired(MobEffectEvent.Expired)`(唯一调用链):
    850: ModAttachments.setDefenseCardConsumedThisBlessing(player, false);
    853: com.merlinkitsune.astral_dice.item.chip.StarCoinHammerChipItem.onBlessingEnd(player);
    855: com.merlinkitsune.astral_dice.item.chip.BankCardUnlimitedChipItem.onBlessingEnd(player);
    857: com.merlinkitsune.astral_dice.item.chip.BigBowlStewChipItem.onBlessingEnd(player);
    859: NancyLuSignItem.onDiceBlessingEnded(player);
    861: MosesSignItem.onDiceBlessingEnded(player);
  A item/sign/NancyLuSignItem.java:191 `return isEquipped(player) && ModAttachments.getNancyLuPassiveType(player) == PASSIVE_ATTACK ...`
  A component/ModAttachments.java:587-591 NANCY_LU_PASSIVE_TYPE 有 serialize(**跨重登保留**),默认 0
  B 同构:event/PlayerLifecycleHandler.java(登录分支 + `ModEffectRemoval.remove(ModEffects.DICE_BLESSING.get())`);
  B combat/DiceCombatEvents.java:835-856 的 Expired 处理器(调用点 :848 StarCoinHammer / :850 BankCardUnlimited /
    :852 BigBowlStew / :854 NancyLu / :856 Moses);forge 的 `LivingEntity.removeEffect` 同样只走 Remove。
可达性:可达(极简)
  最小实机操作步骤:
   1) 佩戴骇客立牌 + 星币锤筹码,打出一次骰神赐福;
   2) 在赐福**未到期**时退出到标题并重新进入(或断线重连);
   3) 用 `F3` 无法看附件,改用行为判定:戴骇客立牌时不触发赐福直接近战攻击 → 攻击力是否仍带 +3;
      或打下一次赐福前把星币数控制在 ≤20,看攻击力是否仍带着上一次的星币锤加成。
   判定点:重登后骇客被动/星币锤加成仍在 = 本缺陷成立。
修法:登录分支显式补一次"赐福结束结算"(不要只移除效果),或把这些状态改成**实时判定**
  (读 `hasEffect(DICE_BLESSING)`)而不落附件。**需要在两版本同时改**;星币锤一条与 S4-C13 合并裁定。
```

### D-6 【低】死亡清单删除后的有效性核验:保留项才是"唯一有效项";`MOSES_READY` 缺失属无效项不对称

```text
ID:D-6
核验:证实(S4-C6 的"删除无效项"判断在机制层面正确;`removeEffect` 同样属无效项)
现象:落盘后的死亡处理器只剩:静态暂存(充能/两个立牌加成)、玻璃骰销毁、HealingManager.clear、
  EffectTimerGuard.clear、8 个 removeEffect、MISAKI 物品组件写。其中:
   - **有效**:`MISAKI_SIGN_STACKS` 段(写在旧实体仍佩戴的立牌物品上,随掉落物/物品栏带走)、
     `ChargeManager.preserveOnDeath`、`DeathPreservedBonuses.preserveOnDeath`、`removeGlassDiceOnDeath`;
   - **无效**:所有 `removeEffect(...)`(死亡克隆不复制效果)、HealingManager.clear 的 3 个附件写与
     removeEffect、EffectTimerGuard.clear(EFFECT_TIMER_ENDS 不复制)。
  因此"删 HAIQING/BONNIE_READY 却不删 MOSES_READY"没有功能差异(两者都是无效项),但作为维护信号
  是不对称的:MOSES_READY 以 Integer.MAX_VALUE 授予,若将来死亡路径改成"不换实体",它会残留。
顺序依赖:`LivingDeathEvent`(旧实体)先于克隆;LOWEST + isCanceled 早退保证"死亡被取消"整段不执行。
证据:
  A event/PlayerLifecycleHandler.java:116-121(LOWEST + isCanceled 早退)
    116: @SubscribeEvent(priority = EventPriority.LOWEST)
   121: if (event.isCanceled()) return;
  A 同文件 :129-131(保留 HealingManager.clear / EffectTimerGuard.clear)、:147-154(MISAKI 段)、
    :155-162(removeEffect ×8,含 HAIQING/BONNIE,**无 MOSES_READY**)
  B event/PlayerLifecycleHandler.java:118(isCanceled)/:126-128/:144-150(MISAKI)/:151-158(removeEffect ×8)
  反编译证据:neoforge-21.1.235-sources.jar → net/neoforged/neoforge/attachment/AttachmentInternals.java
    52-59 `copyEntityAttachments(from,to,isDeath)`:filter = `isDeath ? type -> type.copyOnDeath : type -> true`;
           `onPlayerClone` 用 `event.isWasDeath()`
    → 真死:只复制 copyOnDeath 键;非死亡克隆(末地返回):**全部可序列化附件复制**。
  net/minecraft/server/level/ServerPlayer.java:1444-1464
    `if (keepEverything) { ... for (MobEffectInstance i : that.getActiveEffects()) this.addEffect(new MobEffectInstance(i)); ... }`
    → **效果只在 keepEverything=true 时被复制**;真死(keepEverything=false)不复制效果 ⇒ removeEffect 无效。
  net/minecraft/server/level/ServerPlayer.java:1485 `EventHooks.onPlayerClone(this, that, !keepEverything)`
    → wasDeath == !keepEverything;net/minecraft/server/network/ServerGamePacketListenerImpl.java:1676(neo)
      `respawn(this.player, false, KILLED)` on death;:1669 `respawn(this.player, true, CHANGED_DIMENSION)`(末地返回)
  A event/ModTooltipHandler.java — 与本条无关,略。
  B component/AstralData.java:74-99(死亡分支白名单恰为 rin_pages / komachi_damage_bonus;非死亡分支整份 NBT 复制)
可达性:不可达为缺陷(两条 `removeEffect` 都是无效项);记录为"无效项不对称"信息项。
  最小实机验证步骤(可选):开启 keepInventory=false 死亡一次,检查新实体上 MOSES_READY 是否存在
   —— 预期不存在(证明该差异无功能后果)。
修法:建议把死亡清单里所有 `removeEffect` 一并删除并留注释,或**保留但补齐 MOSES_READY** 以消除不对称;
  两种都可,属于工程整洁项,需与 S4-C6 的最终形态一致。
```

### D-7 【低】`INVESTIGATION_STAGE` 注释与实现相反(真死会丢进度)

```text
ID:D-7
核验:修正(注释错,代码对)
现象:注释写"秘密侦探:死亡保留调查阶段进度(仅卸牌时清除)",但 INVESTIGATION_STAGE 无复制标记,
  真死后新实体取默认值 1 ⇒ 进度实际被重置;只有"重登"才保留进度。
证据:
  A event/PlayerLifecycleHandler.java:142 `// 秘密侦探:死亡保留调查阶段进度(仅卸牌时清除)`
  A component/ModAttachments.java:334-337 INVESTIGATION_STAGE(默认 `() -> 1`,无 copyOnDeath)
  A item/sign/BonnieSignItem.java:86 `ModAttachments.setInvestigationStage(player, 1);`(唯一重置入口=卸牌)
  B component/ModAttachments.java:304-305 默认 1;B item/sign/BonnieSignItem.java:85 同。
可达性:可达(死亡后阶段回到 I;重登保留)⇒ 与注释不符
  最小实机验证步骤:推进到调查阶段 II/III → 真死重生 → 再次击杀隐匿调查目标,观察阶段是否从 I 重新开始。
修法:改注释为"死亡会回到阶段 I(附件不复制),仅重登/卸牌语义见代码";若确实希望死亡保留,
  需把该键加入 copyOnDeath / AstralData 白名单(**并同步两版本 + DeathPreservedBonuses**)。
```

### D-8 周期边界唯一性总核验(结论:4 个键唯一入口成立,第 5 个键漏在入口外)

| 一次性状态 | 写入点 | 归零点 | 唯一入口? |
|---|---|---|---|
| `effect_card_bonus_plays`(忍者主动 +1) | `EffectCardPeriod.java:173` | `clearRoundBonuses:185` | ✅ |
| `candy_chip_play_bonus`(可口糖果每轮一次) | `chip/CandyChipItem.java:42` | `clearRoundBonuses:186` | ✅ |
| `satellite_play_bonus`(探天卫星每 1:00) | `chip/SatelliteChipItem.java:69` | `clearRoundBonuses:187` | ✅(独立计时 `satellite_play_bonus_cooldown_end` 故意不清) |
| `living_page_cycle_bonus`(活体书页本周期累计) | `LivingPageItem.java:47` | `clearRoundBonuses:188` | ✅ |
| `electric_glove_aoe`(电击手套本周期武装) | `chip/ElectricGloveChipItem.java:69` | 仅 `tick:351` + `SpellDamageRegistry:384` | ❌ **见 D-4** |
| `effect_card_play_count` / `effect_card_cooldown_end`(周期权威) | `registerPlay:282/288/293`、`tick:337/341` | `registerPlay:281/282`、`tick:344/345` | 只写 2 处,入口集中 ✅ |

`clearRoundBonuses` 的调用点(落盘后):`EffectCardPeriod.java:284`(registerPlay 边界)、`:348`(tick 收尾)。
**死亡路径已不再调用**(`PlayerLifecycleHandler` 中已无该调用)——这削弱了"三处共用"的文档约束(D 检查点 D1),
但机制上正确(见 D-6)。若后续有人按旧文档"找回"这一行,不会造成缺陷,只是无用的 4 次 map 写。

---

## 3. 玩家级状态 × 六路径覆盖矩阵

图例:
- `—` = 该路径**没有任何代码触碰**该状态(值原样保留)
- `D` = 回到默认值,但**只因新实体**(不是显式清理)
- `C` = 复制保留(1.21.1 `.copyOnDeath()` / 1.20.1 `AstralData.onPlayerClone` 白名单)
- `E` = **显式**清零(代码里能指到行)
- `P` = 持久化保留(重登/换维度后仍是原值)
- `⚠` = **真残留**:六条路径都不清理,且可观测有害
- `√` = 已核验无缺陷(含"时间戳自到期"/"被新实体兜底"两类)

> 路径定义与实证:
> ① **死亡被取消**(不死图腾/末影骰/安全气囊):`PlayerLifecycleHandler:121` 早退 ⇒ 本方法体一行不执行。
> ② **真死(旧实体)**:同方法体剩余部分(`:123-162`)。
> ③ **克隆 `wasDeath=true`**:`AttachmentInternals.java:52-59`(neo)/`AstralData.java:78-92`(forge)⇒ 非复制键一律默认。
> ④ **重生 `PlayerRespawnEvent`**:`PlayerLifecycleHandler:201-204`(neo `HealingManager.tick` + 充能/两键兜底回写)。
> ⑤ **重登 `PlayerLoggedInEvent`**:`PlayerLifecycleHandler:179-189`(defenseCardConsumed=false、EffectTimerGuard.clear、移除赐福、HealingManager.tick、发书)。
> ⑥ **换维度**:普通传送门/传送指令 = **同一实体、无克隆**(`ServerPlayer.changeDimension` 自实现;`ServerPlayer.restoreFrom` 的唯一调用点是 `PlayerList.respawn:468`)⇒ 全部 `—`;
>    **末地返回(credits)** = 新实体 + `respawn(player,true,CHANGED_DIMENSION)` ⇒ `wasDeath=false` ⇒ **全部可序列化键 `C`**(附件与效果都被复制)。
>    (仅 `DICE_CURSE_RATIO` 不可序列化 ⇒ 该路径 `D`,无副作用)

### 3.1 出牌轮/周期族

| 状态 | ①被取消 | ②真死 | ③克隆 | ④重生 | ⑤重登 | ⑥换维度 | 备注 |
|---|---|---|---|---|---|---|---|
| `effect_card_play_count` | — | D | D | — | **P**(A9 空闲收尾兜底) | — / C | A9 后能在 ≤30s 内收尾 ✅ |
| `effect_card_cooldown_end` | — | D | D | — | P(同上) | — / C | 冷却是时间戳,自到期 ✅ |
| `effect_card_bonus_plays` | — | D | D | — | P(registerPlay 边界/收尾兜底) | — / C | 授予即消耗;**D-2** 是收尾过早的问题 |
| `living_page_cycle_bonus` | — | D | D | — | P(同上) | — / C | ✅(活体书页效果另作待定源) |
| `candy_chip_play_bonus` | — | D | D | — | P(同上) | — / C | 每轮一次语义受 A9 轮长影响(**D-1**) |
| `satellite_play_bonus` | — | D | D | — | P | — / C | 同上 |
| `satellite_play_bonus_cooldown_end` | — | D | D | — | P(无清理,自到期) | — / C | ✅信息项 |
| `satellite_give_cooldown_end` | — | D | D | — | P(自到期) | — / C | ✅ |
| **`electric_glove_aoe`** | — | D | D | — | **P(无任何清理)** | — / C | **⚠ D-4**:周期经 registerPlay 结束时不解除;重登保留 ⇒ 免费 AoE |

### 3.2 立牌主动/待命族

| 状态 | ①被取消 | ②真死 | ③克隆 | ④重生 | ⑤重登 | ⑥换维度 | 备注 |
|---|---|---|---|---|---|---|---|
| `sign_active_cooldown_end` | — | D | D | — | P | — / C | 设计如此(玩家级,不随装卸/死亡重置);9 个写入方 ✅ |
| `sign_ready_type` | — | D | D | — | P + **E(新)** | — / C | 新增玩家级 `tickSignReadyTimeout`(`BaseSignItem.java:169-187`,由 `PlayerTickEvents:118` 驱动)⇒ 超时即清 ✅ |
| `sign_ready_expire` | — | D | D | — | P + **E(新)** | — / C | 同上;旧"只在立牌 onCurioTick 里清"的问题已修 ✅ |
| `HAIQING_READY`/`BONNIE_READY` 效果 | — | E(无效) | 不复制 | — | P(待命超时移除) | C(末地返回会复制效果) | ✅(末地返回时 sign_ready 与效果一起保留,自洽) |
| `MOSES_READY` 效果 | — | — **(未列)** | 不复制 | — | P(待命超时移除) | C | **D-6 的不对称项;无功能后果**(效果不随真死复制) |
| `nancy_lu_passive_type` | — | D | D | — | **P** | — / C | **⚠ D-5②**:登录移除赐福不触发结束钩子 ⇒ +3 攻/防无限期残留 |
| `nancy_lu_active_bonus`/`_until` | — | D | D | — | P(时间戳自到期 + `NancyLuSignItem:80-81` 到期清) | — / C | ✅ |
| `nancy_lu_hidden_until` | — | D | D | — | P(时间戳自到期) | — / C | ✅(注意:隐身在重登后仍按时间戳生效,属设计) |
| `nancy_lu_ender_pearl_immune_until` | — | D | D | — | P(自到期) | — / C | ✅ |

### 3.3 资源池族

| 状态 | ①被取消 | ②真死 | ③克隆 | ④重生 | ⑤重登 | ⑥换维度 | 备注 |
|---|---|---|---|---|---|---|---|
| `player_starlight` | — | D | D | — | P | — / C | 资源,持久正确 ✅;**F-2** 是产出侧缺陷 |
| `healing_points` | — | E(HealingManager.clear)≡D | D | `HealingManager.tick`(上限收缩+显示) | `tick`(同) | — / C | ✅;**F-4** 上限收缩分支恒不可达 |
| `healing_prev_blessing` | — | E | D | — | `tick` 边沿修正 | — / C | ✅(登录时靠它补赐福结束边沿) |
| `healing_timer_end` | — | E | D | — | `tick` 处理到期 | — / C | ✅ |
| `charge`(效果)/`empower_decay_at` | — | 静态表暂存→④恢复 | 效果不复制 | E(restoreAfterDeath) | P(自到期/层数守卫) | C(末地返回复制效果) | ✅两层机制成立 |
| `eight_sided_roll_accum` | — | D | D | — | P(仅卸筹码清) | — / C | ✅资源侧;**F-7** 注释与代码不符 |
| `empower_decay_at` | — | D | D | — | P(自到期/层数守卫) | — / C | ✅ |
| `mark`(目标身上的效果) | — | n/a(非玩家键) | — | — | P(标记自然到期→逐层衰减) | — | ✅ `MarkManager` 无计数池 |

### 3.4 计数器族(详见 §4)

| 状态 | ①被取消 | ②真死 | ③克隆 | ④重生 | ⑤重登 | ⑥换维度 | 备注 |
|---|---|---|---|---|---|---|---|
| `komachi_use_count` | — | D | D | — | **P**(≤2 张进度跨会话) | — / C | 残留仅"少一次计数",低 |
| `komachi_last_card` | — | D | D | — | **P(无任何显式清)** | — / C | 无害(与 use_count 同步持久) |
| `komachi_damage_bonus` | — | 暂存→**C** | **C** | **E**(兜底回写) | P | — / C | ✅用户裁决的两层机制本次复核成立 |
| `magic_tome_use_count` / `_last_card` | — | D | D | — | P(同上) | — / C | 低 |
| `piggy_bank_use_count` | — | D | D | — | P(≤1 张进度) | — / C | 低 |
| `mimi_returned_card_count` | — | D | D | — | P(≤24 张进度) | — / C | 低 |
| `cursed_sword_bonus` / `_blessing_triggered` | — | D | D | — | P(旗标由新赐福/卸牌清) | — / C | **F-7** 旗标先置后判 |
| `star_coin_hammer_bonus` | — | D | D | — | **⚠ P(D-5①)** | — / C | 既有 S4-C13 |
| `flashlight_granted_targets` | — | D | D | — | P(仅卸筹码清) | — / C | 文档仍称"死亡清空"⇒ 与 D-6 同类的文档漂移 |
| `magic_quiver_tracking` / `_first_card` | — | D | D | — | **P** | — / C | **F-6**:重登后可凭旧记录返还一张牌 |
| `magic_quiver_cooldown_end` | — | D | D | — | P(自到期) | — / C | ✅ |
| `rin_pages` | — | 暂存→**C** | **C** | **E** | P | — / C | ✅ |
| `rin_gift_signature` / `_tick` | — | D | D | — | P(2 tick 去重窗,自失效) | — / C | ✅ |
| `investigation_stage` | — | D | D | — | P | — / C | **D-7** 注释错 |
| `fen_recharge` | — | D | D | — | P | — / C | ✅(AGENTS"死亡清零"由新实体兜底) |
| `lulu_last_hurt_tick` | — | D | D | — | P(时间戳) | — / C | ✅ |
| `airbag/railgun/ender_totem/warp_engine` 冷却 | — | D | D | — | P(自到期) | — / C | ✅信息项(死亡不清冷却=不能刷冷却) |
| `dice_curse_ratio` | — | D | D | — | **D(不序列化)** | **D**(末地返回也丢) | ✅瞬态捕获 |
| `defense_card_consumed_blessing` | — | D | D | — | **E**(登录) | — / C | ✅ |
| `guide_book_given` | — | D | D | — | **P=false → 再发一本** | — / C | 真死→重登会得第二本(既有已知项,需单独裁决) |
| `effect_timer_ends` | — | E(clear) | D | — | **E**(clear) | — / C | ✅ |
| `moses_broken_attack_rewarded`/`_dodge_counter_rewarded` | — | n/a(目标实体键) | — | — | P(目标持久数据) | — | 与玩家生命周期无关,略 |

### 3.5 矩阵结论:空白格与真残留

**没有任何路径清理的状态(真残留,按危害排序)**:

1. `nancy_lu_passive_type`(**⚠ D-5②**,重登后 +3 攻/防无限期;唯一清理=赐福自然到期或卸牌)
2. `electric_glove_aoe`(**⚠ D-4**,重登保留 / 周期经 registerPlay 结束时不解除 ⇒ 免费 AoE)
3. `star_coin_hammer_bonus`(**⚠ D-5①**,既有 S4-C13)
4. `magic_quiver_tracking` + `magic_quiver_first_card`(**F-6**,跨会话可返还一张牌)
5. `komachi_last_card` / `magic_tome_last_card` / `rin_gift_signature` / `rin_gift_tick`
   (无显式清理,但**与同族计数同步持久 / 窗口极短,已核验无可观测危害** ⇒ 非缺陷)
6. 各"仅卸下清理"的计数器(`piggy_bank_use_count`、`mimi_returned_card_count`、`eight_sided_roll_accum`、
   `flashlight_granted_targets`、`komachi_use_count`、`magic_tome_use_count`):重登保留进度(≤1~24 次的进度),
   危害仅"少一次计数" ⇒ 低
7. 静态表类:`ChargeManager.DEATH_PRESERVED_STACKS`、`DeathPreservedBonuses.PRESERVED`、
   `ElectricSwordChipItem.KILL_COUNTS`、`JasmineSignItem.walkAccumMap`、`EnergyRecyclerChipItem.walkAccumMap`
   —— **静态 JVM 表,不受克隆/重登影响**;`KILL_COUNTS`/`walkAccumMap` 只在卸下清理(**F-5**),
   且**无登出清理**(F-4 的 `clearDeathPreserved` 零调用)。

**"写在不会走到的路径里"的清单(本次核验)**:
- 死亡清单里所有附件写 → 真死路径因"新实体回默认"而无效(**D-6**,已由另一代理删除)。
- 死亡清单里所有 `removeEffect` → 效果不随真死克隆(反编译证据见 D-6)⇒ 同样无效;
  其中 `HAIQING/BONNIE_READY` 保留、`MOSES_READY` 缺失,属无效项不对称。
- `HealingManager.tick` 的"上限收缩"块(见 F-4)是**死分支**。
- **反例(必须保留)**:`MISAKI_SIGN_STACKS` 段 —— 它写在会存活的物品上,是本方法唯一有效的清理。

---

## 4. §F 资源池与计数器

### F-1 【高】新兑换口径正确,但入口门槛没跟着改 ⇒「净扣 0 仍发奖励」换了个形式(星币 → 主动增益)

```text
ID:F-1
核验:证实(口径正确性通过;入口门槛缺口为新问题)
现象:佩戴银行卡(base=4 或 7)且星光恰等于 base 时,经商立牌主动「套现」:
  - 新口径下 starlightToStarCoins(player,-1) 返回 0(没有任何可支配星光)✅白嫖星币已封;
  - 但 ParunanSignItem 只在调用前判 `StarLightManager.get(player) <= 0`(**含基础值**,恒 >0),
    且**完全忽略返回值** ⇒ 仍然 return SUCCESS ⇒ 主动技能的"随机 3 选 1 增益"照发,并且
    BaseSignItem 会写 180 秒主动冷却。
  ⇒ 玩家以"零代价"获得 饱和0:30 / 幸运5:00 / 村庄英雄15:00 之一(每 3 分钟一次,完全可重复)。
顺序依赖:兑换实现(ResourceConversion)与入口门槛(ParunanSignItem)分处两个文件,
  门槛写于旧口径时代("有星光就能套现");改动只动了兑换侧 ⇒ 语义漂移。
证据:
  A resource/ResourceConversion.java:29-50(新口径;两版本逐行相同)
    31: // 可自由支配的星光 = 当前值 − 基础值(银行卡等提供的"下限"不可被兑换出去)。
    33: int spendable = Math.max(0, StarLightManager.get(player) - StarLightManager.getBasePoints(player));
    34: int use = amount < 0 ? spendable : Math.min(amount, spendable);
    35: int coins = use / STARLIGHT_PER_COIN;
    36: if (coins <= 0) return 0;
    38: int before = StarLightManager.get(player);
    39: StarLightManager.spend(player, coins * STARLIGHT_PER_COIN);
    40: int gained = Math.max(0, before - StarLightManager.get(player)) / STARLIGHT_PER_COIN;
    43: if (AtmChipItem.isEquipped(player)) { gained += Math.max(1, (int) (gained * 0.4)); }
    46: if (gained > 0) { giveItem(player, new ItemStack(ModItems.STAR_COIN.get(), gained)); }
  A item/sign/ParunanSignItem.java:37-43(**唯一 caller**)
    37: int starlight = StarLightManager.get(player);
    38: if (starlight <= 0) {
    39:     return InteractionResultHolder.fail(stack);
    40: }
    42: // 每 2 点星光返还 1 个星币(转化比例集中管理,余数部分保留)
    43: com.merlinkitsune.astral_dice.resource.ResourceConversion.starlightToStarCoins(player, -1);
    (44 之后直接进入随机增益与 return success)
  A item/StarLightManager.java:38-50 getBasePoints(银行卡 LOW=4 / HIGH=7,`BankCardChipItem.java:15-17`)
  B resource/ResourceConversion.java:29-50 同文;B item/sign/ParunanSignItem.java:37-43 同文。
可达性:可达(需要银行卡筹码)
  最小实机操作步骤:
   1) 创造模式,只戴「银行卡-余额少」(base=4)与「经商」立牌(不再获得其它星光来源);
   2) 先按一次主动把星光兑换掉 → 星光应停在 4(= base),星币不增加;
   3) 再按一次主动 → **预期(新行为)**:仍提示技能成功并获得一个 0:30/5:00/15:00 增益;
      应然:无任何可兑换星光时不应发奖(或至少不应进入 180 秒冷却)。
   判定点:第 3 步是否发增益。
修法:**需用户先定夺口径**:
  (a) 若"阻止兑换"= 连主动增益也不给 ⇒ 门槛改为 `get() - getBasePoints() > 0`(或判返回值 <0/==0 时 return fail);
  (b) 若"仍释放主动、只是不发币"(改动说明里提到的另一种口径)⇒ 保持 success,但应把
      "光环式空转"写进 tooltip/文档,并考虑不消耗主动冷却。
  另注:改动说明里讨论过的 `-1` 返回值方案**未落盘**(落盘版仍 `return 0`),所以不存在"污染调用方"的问题 ✅。
```

### F-2 【中】三个筹码"装备即得星光"卸下不回收 ⇒ 新口径仍未封住的产出循环(既有 S4-C5,改动未覆盖)

```text
ID:F-2
核验:证实(第一轮 S4-C5;本轮按新口径重新评估其可利用性)
现象:`AtmChipItem`(+1)、`BankCardUnlimitedChipItem`(+3)、`StarCoinHammerChipItem`(+5)在 onEquip 里
  `StarLightManager.add`,三者的 `onChipUnequip` **都不回退星光**(星币锤只清自己的攻击加成)。
  ⇒ "装备→卸下"循环每次净增 1/3/5 点星光;这些点位于 base 之上 ⇒ 属于新的 `spendable`,
    可用经商主动换成星币(2:1),即新口径**堵住了 base 白嫖,但没堵住这条产出线**。
顺序依赖:资源下限(base)与资源产出(装备钩子)是两套机制;改动只处理"下限不可花"。
证据:
  A item/chip/AtmChipItem.java:27-34 `StarLightManager.add(player, 1);`(无 onChipUnequip)
  A item/chip/BankCardUnlimitedChipItem.java:34-41 `StarLightManager.add(player, 3);`(`onChipUnequip` 不存在)
  A item/chip/StarCoinHammerChipItem.java:198-211 `StarLightManager.add(player, 5);` +
      onChipUnequip 只 `setStarCoinHammerBonus(player, 0)`
  A resource/ResourceConversion.java:33(spendable 只看 base,不看来源)
  B 三文件同构(AtmChipItem.java:34 / BankCardUnlimitedChipItem.java:41 / StarCoinHammerChipItem.java:205,209-212)
可达性:可达
  最小实机操作步骤:戴 ATM 筹码 → 卸下 → 重复 N 次 → 经商主动套现 → 星币是否增加(每次循环净 +1 星光)。
   判定点:多次装卸后星光明显上升。
修法:① 在三个 `onChipUnequip` 里按"装备时授予量"回退(需处理已花掉的情形:回退可用 `set(max(base, get-granted))`);
   ② 或把"装备即得"改成一次性标记(类似 `guide_book_given`),每个筹码每个世界只给一次;
   ③ 或改成"被动缓慢产出"而不是即时授予。**需用户定夺**。
```

### F-3 【低】`spend/add` 返回值口径三处不一致(当前不可达为缺陷,但语义陷阱明确)

```text
ID:F-3
核验:证实(口径不一致);当前缺陷性为"不可达/已被 caller 规避"
现象:
  - `StarLightManager.spend` 返回 `min(current, amount)`,即**请求扣除量**,但 `set` 会把结果抬回 base,
    真实扣除可能更少(请求 2、余额=base+1 ⇒ 实际只掉 1);**唯一 caller 已改为读 `get()` 前后差值** ⇒ 现状安全。
  - `StarLightManager.add` 返回 `next`(钳制到 cap 之后、但**在 set 的 base 钳制之前**)⇒ 若 base>next,
    返回值小于真实存储值(现状:base 不会大于 next,因为 get≥base)。
  - `HealingManager.add` 返回 `newPoints = min(get+amount, cap)`,不夹下界 ⇒ 传负数时返回值可为负
    (存储侧被 `setHealingPoints` 的 `Math.max(0,·)` 夹住)⇒ 现值口径不一致;全仓 7 个 caller 全传正常数
    (`CandyChipItem:37` +1、`LuluSignItem:58/66` +1/+3、`BufferShieldChipItem:45`、`BigBowlStewChipItem:64`、
    `FriendshipBadgeChipItem:65-66`、`VitaminPillChipItem:68`、`PandamanSignItem:160`)⇒ **不可达**。
  - `ChargeEffect.consume` / `ChargeManager.consume` / `HealingManager.spend` 均返回"实际消耗量"(正确)。
证据:
  A item/StarLightManager.java:52-71
    52: public static void set(Player player, int value) {
    53:     int base = getBasePoints(player);
    55:     ModAttachments.setStarlight(player, Math.max(base, Math.min(value, getCap())));
    56: }
    59: public static int add(Player player, int amount) {
    60:     int next = Math.min(get(player) + amount, getCap());
    61:     set(player, next);
    62:     return next;                    // ← 未反映 base 钳制
    63: }
    66: public static int spend(Player player, int amount) {
    67:     int current = get(player);
    68:     int spent = Math.min(current, amount);   // ← "请求量"而非"实际扣除"
    69:     set(player, current - spent);
    70:     return spent;
    71: }
  A item/HealingManager.java:61-68 `int newPoints = Math.min(getPoints(player) + amount, cap);`(无下界)
  B item/StarLightManager.java:56-75 / item/HealingManager.java:62-69 同构。
可达性:当前不可达为缺陷(唯一 spend caller 已用差值口径;HealingManager.add 无负增量 caller)
  最小实机验证步骤:佩银行卡(base=4)、星光=5 → 调用经商套现(请求 2)→ 观察星光与星币:
    预期=星光 4、星币 0(实际扣除 1 不足 2 点 → 不发币);这一步同时验证 F-3 与 F-1 的差值口径。
修法:在 javadoc 显式写明三个函数的返回值语义;更稳妥的做法是让 `spend` 返回**真实扣除量**
  (`before-get()`),`add` 返回 `get()`(写入后的真实值)。属低风险整洁项。
```

### F-4 【低中】零调用死代码 / 恒不可达分支

```text
ID:F-4
核验:证实
现象:
  ① `ChargeManager.clearDeathPreserved` **全仓零调用**(两版本各一处定义),其 javadoc 声称"登出/异常路径兜底"
     ⇒ 静态表 `DEATH_PRESERVED_STACKS` 在"死亡后未重生就退出"时不会被清;该项会在**同一 JVM**内一直留着,
     直到该 UUID 再次死亡(会被覆盖)或被 `restoreAfterDeath` 取走。单人"退出到标题"不卸载类。
  ② `HealingManager.spend` **全仓零调用**(治愈点没有消耗方)。
  ③ `HealingManager.tick` 的"上限收缩"分支恒不可达:`getCap` 返回常量 32,而 `add` 已把值夹到 32、
     `setHealingPoints` 夹下界 0 ⇒ `getPoints() > cap` 永不成立(注释仍写"上限动态跟随最大生命值")。
证据:
  A item/ChargeManager.java:91-96(定义)`public static void clearDeathPreserved(Player player) { ... }`
    —— 全仓 grep `clearDeathPreserved` 仅命中定义行(A:92 / B:92)。
  A item/HealingManager.java:73-82 `public static int spend(Player player, int amount)` —— 全仓无 caller。
  A item/HealingManager.java:51-53 `public static int getCap(Player player) { return HEALING_POINT_CAP; }`(常量 32)
  A item/HealingManager.java:184-190
    184: // 上限收缩(最大生命降低时)
    185: int total = getPoints(player);
    186: int cap = getCap(player);
    187: if (total > cap) { ModAttachments.setHealingPoints(player, cap); total = cap; }
  B item/ChargeManager.java:91-96 / item/HealingManager.java:74-83 / :185-191 同构。
可达性:死代码/不可达;①有**副作用缺口**(登出无清理)
  最小实机验证步骤(①):死亡一次(暂存 5 层充能)→ 在重生画面前退出到标题 → 重进世界 →
   观察充能是否被恢复(预期:会被恢复,因为表项还在)——即"表项未清"是可观测的。
修法:① 在 `PlayerLoggedOutEvent` 调 `clearDeathPreserved`(或在 `restoreAfterDeath` 之外补一处清理);
  ② 删除 `HealingManager.spend` 或补上调用方;③ 修正 HealingManager 的注释(或恢复"上限随最大生命"的设计)。
```

### F-5 【低】静态表计数器 `ElectricSwordChipItem.KILL_COUNTS`:死亡/重登都不清(与附件类口径不一致)

```text
ID:F-5
核验:证实
现象:电流剑「每 10 杀 +2 充能」的计数存在 **static Map<UUID,Integer>**,不是玩家附件:
  真死/重生/重登都不会清零(只有卸下筹码 `onChipUnequip` 清);且静态表无登出清理 ⇒ 跨世界/跨会话残留。
  同类:`JasmineSignItem.walkAccumMap`、`EnergyRecyclerChipItem.walkAccumMap`(每 300/150 米一档)。
  对比:附件类计数器(`komachi_use_count` 等)在真死时回默认 ⇒ 两类计数器"死亡待遇"不一致(S4-C6 已指出同类问题)。
证据:
  A item/chip/ElectricSwordChipItem.java:31-35
    31: public static final int KILLS_REQUIRED = 10;
    33: public static final int CHARGE_GAIN = 2;
    35: private static final Map<UUID, Integer> KILL_COUNTS = new HashMap<>();
  A 同文件 :60-73(增量+结算同处)
    66: int kills = KILL_COUNTS.getOrDefault(uuid, 0) + 1;
    67: if (kills >= KILLS_REQUIRED) { KILL_COUNTS.remove(uuid); ChargeManager.addStacks(killer, CHARGE_GAIN); }
  A 同文件 :53-58 `onChipUnequip` → `KILL_COUNTS.remove(player.getUUID());`(唯一清理)
  B 同文件同行同构。
可达性:可达
  最小实机验证步骤:戴电流剑杀 9 个敌对目标 → 死亡重生 → 再杀 1 个 → 是否立刻 +2 充能
    (预期:是,因为计数跨死亡保留;而附件类计数器会归零)。
修法:把该类计数迁到玩家附件(与 `komachi_use_count` 同口径),或在 `LivingDeathEvent`/登出补 `remove`。
```

### F-6 【低中】计数与结算不同处的两处:`magic_quiver_*`、`star_coin_hammer_bonus`

```text
ID:F-6
核验:证实
现象:
  ① 魔法箭袋:记录在 `MagicQuiverChipItem.onEffectCardUsed`(筹码类),结算在
     `SpellDamageRegistry.tryProc`(战斗类),二者通过 `magic_quiver_tracking` / `magic_quiver_first_card`
     两个**未同步、跨重登保留**的附件通信。重登后 `tracking=true` 仍在 ⇒ 第一张"命中带标记目标"的攻击
     会返还 `first_card` 记录的那张牌,**即使本会话从未出过效果牌**;冷却 `magic_quiver_cooldown_end`
     同样持久 ⇒ 不会双发。(同类:无清理点。)
  ② 星币锤:写在 `StarCoinHammerChipItem.onBlessingStart`、读在 `DiceCombatModifiers:328`、
     清在"赐福自然到期"(`DiceCombatEvents:853`)⇒ 清除依赖唯一的过期事件,见 D-5①。
证据:
  A item/chip/MagicQuiverChipItem.java:39-46(记录)
    42: if (ModAttachments.getMagicQuiverTracking(player)) return;
    44: ModAttachments.setMagicQuiverTracking(player, true);
    45: ModAttachments.setMagicQuiverFirstCard(player, cardType);
  A 同文件 :53-73(结算,由 combat/SpellDamageRegistry.java:335 `MagicQuiverChipItem.tryProc(ctx);` 调度)
    70: ModAttachments.setMagicQuiverCooldownEnd(player, now + COOLDOWN_TICKS);
    71: ModAttachments.setMagicQuiverTracking(player, false);
  A 同文件 :76-79(卸下只清 tracking;first_card 与 cooldown_end 不清)
  A component/ModAttachments.java:516-531(三键均 serialize、**无 sync**)
  A combat/DiceCombatModifiers.java:328 `ap += ModAttachments.getStarCoinHammerBonus(ctx.attacker);`(guard :327 hasCurio)
  A item/chip/StarCoinHammerChipItem.java:180-196(set/清)/ combat/DiceCombatEvents.java:853(唯一清入口)
  B 同构(MagicQuiverChipItem.java:40-47/54-74/77-80;DiceCombatModifiers.java:331;StarCoinHammerChipItem.java:181-197)
可达性:① 可达(重登后攻击带标记目标);② 见 D-5
  最小实机验证步骤(①):装备魔法箭袋 + 用一张效果牌(记录)→ **不命中**,退出重登 → 攻击一个已带标记的
    敌对目标 → 是否返还了那张牌(预期:是 = 跨会话残留);对照:不重登时同一流程需要"先出牌再命中"。
修法:① 在登录清理里把 `magic_quiver_tracking` 置 false(或把它改成"本 tick 内有效"的短窗口);
  ② 星币锤见 D-5。
```

### F-7 【低】计数器边角:诅咒之剑旗标先置后判;八面骰"32 归零"注释与代码相反

```text
ID:F-7
核验:证实
现象:
  ① `CursedSwordChipItem.onKill`:先置 `cursed_sword_blessing_triggered=true`,**之后**才判
     `current < CURSED_SWORD_BONUS_MAX(16)`。⇒ 已满 16 时,本赐福周期内第一次达标击杀"被消耗但不给奖励";
     其后所有达标击杀同样只走 early-return。玩家可观测:满层后本期再无任何提示/收益。
  ② `eight_sided_roll_accum` 的注册注释写"达到 32 星光后归 0",但代码为
     `while (accum >= 8 && starlight < cap) {...}` + 满时**保留**余数(`DiceCombatEvents:453-454` 注释自陈),
     ⇒ 注释与实现相反(实现是有意设计,注释过期)。
证据:
  A item/chip/CursedSwordChipItem.java:87-94
     87: if (!player.hasEffect(ModEffects.DICE_BLESSING)) return;
     89: if (ModAttachments.getCursedSwordBlessingTriggered(player)) return;
     90: ModAttachments.setCursedSwordBlessingTriggered(player, true);   // ← 先置
     91: int current = getCursedSwordBonus(player);
     92-93: (max 判定)
     94: ModAttachments.setCursedSwordBonus(player, current + 1);
  A component/ModAttachments.java:29-33(`EIGHT_SIDED_ROLL_ACCUM` 注释"每满 8 点 +1 星光,达到 32 星光后归 0")
  A combat/DiceCombatEvents.java:447-456
    449: int accum = ModAttachments.getEightSidedAccum(player) + roll;
    450: while (accum >= 8 && starlight < StarLightManager.getCap()) { 451: accum -= 8; 452: starlight++; }
    453: // 星光已满时**保留**累计点数(不清零),待星光回落后继续换算
    455: StarLightManager.set(player, Math.min(starlight, StarLightManager.getCap()));
  A component/GameplayConstants.java `MAX_STARLIGHT = 32`
  B 同构(CursedSwordChipItem.java:88-95;DiceCombatEvents.java:446-453)。
可达性:① 可达;② 信息项(注释)
  最小实机验证步骤(①):把诅咒之剑加成刷到上限 16(或直接把上限调到 1 观察)→ 进入新赐福 →
    达标击杀 ≥20 血敌对目标 → 检查是否既无提示也无加成(且本期不再触发)。
修法:① 把旗标置位移到"确实发奖之后",或把 `current >= MAX` 作为拒绝条件(不消耗旗标);
  ② 修正注释,并在 tooltip/手册说明"满星光时余数保留"。
```

### F-8 §F 逐项核验结果汇总(资源池)

| 资源池 | 下限/基础值语义 | `spend` 返回值 | `add` 上限钳制 | 结论 |
|---|---|---|---|---|
| 星光 `StarLightManager` | base=银行卡 4/7,`set` 用 `max(base, min(value,cap))` | 返回**请求量**(F-3) | `min(get+amount,cap)` 在 `set` 之前,方向正确 | 白嫖已封(F-1 修好兑换侧)✅;产出侧 F-2 未封 |
| 治愈点 `HealingManager` | 无 base;`setHealingPoints` 夹下界 0;cap=常量 32 | `spend` 返回**实际量**(正确) | `min(get+amount,cap)`;但返回值不夹下界(F-3) | 上限"动态跟随最大生命"是**死代码**(F-4③) |
| 充能 `ChargeManager`/`ChargeEffect` | 无 base;层数=amplifier+1,上限 `CHARGE_MAX_STACKS` | `consume` 返回**实际量**(正确) | `min(MAX, get+stacks)` 正确 | ✅;`clearDeathPreserved` 零调用(F-4①) |
| 赋能 `EmpowerManager` | 层数=amplifier+1;`empower_decay_at` 计时 | 无 spend | `addStacks` 走 `EmpowerEffect.addStacks` | ✅(tick 幂等,`now < next` 守卫) |
| 标记 `MarkManager` | 无池;层数=amplifier+1,上限 `MAX_MARKER` | 无 spend | `min(existing.amplifier+1, MAX_MARKER-1)` 方向正确 | ✅(衰减经 PENDING_DECAY 延到下一 tick,设计正确) |
| 调查阶段 `investigation_stage` | 默认 1,`set` 用 `max(1,value)` | 无 spend | `min(stage+1, 4)` 正确 | ✅;注释错(D-7) |
| 活体书页 `rin_pages` / 本周期累计 | 无池 | 无 spend | 周期累计在 `LivingPageItem:44-46` 钳到 9 | ✅(两层机制复核成立) |

---

## 5. 单侧差异(A/B)

| # | 差异 | 影响 | 需同步? |
|---|---|---|---|
| 1 | `EffectCardPeriod` 的 `EffectPendingSource.effect()` 返回 `Holder<MobEffect>`(A)/ `MobEffect`(B);`hasCurio` 用 `CuriosApi`(A)/`CuriosCompat`(B) | 无行为差异;A9 改动文本两侧一致(行号 +1) | 是(语义同一,必须同步改) |
| 2 | A 用 `PlayerTickEvent.Post`(每 tick 一次);B 用 `TickEvent.PlayerTickEvent`(**START/END 各一次**,`PlayerTickEvents.java:118-120` 未过滤 phase) | 本轮核验:`EffectCardPeriod.tick` 与 A9 新增写入**幂等**(第二次调用被 `cooldown > 0 && now < cooldown` 或 `played<=0` 挡住)⇒ 无功能差异;但仍是 B 侧独有隐患 | 建议补 phase 过滤(独立于本切片) |
| 3 | 死亡复制机制:A `.copyOnDeath()`(仅 2 键)/ B `AstralData.onPlayerClone` 白名单(仅 2 键)+ 非死亡分支整份 NBT | **语义等价**(本轮由反编译双向核实);但**任何新增"死亡保留"键必须两处同改** | 是 |
| 4 | 持久化:`DICE_CURSE_RATIO` A 侧无 `.serialize`(不复制)/ B 侧 `.inMemory()` | 等价(均为瞬态) | 否 |
| 5 | 登录清理:B 侧另有"全量快照下发 + 断线清缓存"(`ModCapabilities`/`ClientSessionEvents`) | 仅客户端同步;与状态机清理无关 | 否 |
| 6 | 行号偏移:forge `EffectCardPeriod` +1、`PlayerLifecycleHandler` 死亡块 −3、`ParunanSignItem` 与 `ResourceConversion` 逐行相同 | 引用时需按侧取行号 | — |
| 7 | `charge`/赋能/标记等池:A/B 数值与钳制逐行相同 | 无差异 | 否 |

**本次范围内未发现"仅一个版本存在"的功能性单侧差异**(除上表第 2 条的既有 tick 相位隐患)。

---

## 6. 不确定点 / 需用户裁决

1. **A9 的最终语义(D-1/D-2/D-3 的共同根)**:「计时器结束后启动 30 秒冷却」是否应当同时
   **作废剩余出牌数**?落盘实现选了"作废 + 硬锁",导致每轮实际只能出 1 张、忍者主动白扣冷却、
   GUI 说"已用完"。三种可选:(a) 只起冷却不作废(删 `setEffectCardPlayCount(maxAllowed)`);
   (b) 作废但补 ActionBar 文案 + 更新 AGENTS.md 不变量;(c) 打满才收尾(回退 A9)。
   **必须定夺后才能给最终 diff。**
2. **F-1 的入口口径**:无"可支配星光"时,经商主动应当 `fail`(什么都不发生)还是
   `success`(仍给随机增益)?影响 tooltip 与冷却。
3. **F-2 的"装备即得星光"**:回退 / 一次性标记 / 改被动产出,三者取一。
4. **`guide_book_given`**:真死→重登会再发一本《恋的规则书》(矩阵 3.4),是否单独修。
5. **D-6 的形态**:死亡清单里保留的 `removeEffect` 与 `HealingManager.clear` 是否一并删除?
   (机制上无效,但保留可读性;若保留,建议补齐 `MOSES_READY` 以消除不对称。)
6. **未核验项(需实机或进一步源码核对)**:
   - B 侧 Curios 的 player tick 是否过滤 `TickEvent.Phase`(影响 `ParunanSignItem:26` 的
     `gameTime % interval == 0` 被动是否每窗口触发两次 → 星光 +1 变 +2)。验证:戴经商立牌静置 N 秒,
     统计星光增量应为 +1/间隔。
   - D-4 的构造型复现序列(未实机)。
   - A9 对**多人**场景的顺序(两名玩家互不共享这些附件,理论无影响;未实测)。
   - 赐福结束时钩子链的完整清单是否还有其它"只挂在 Expired 上"的结算(本轮只按 `DiceCombatEvents:840-861`
     的调用点列举;更下游的 `NancyLuSignItem.onDiceBlessingEnded`/`MosesSignItem.onDiceBlessingEnded`
     内部还有别的状态写入,未逐行展开)。
```
