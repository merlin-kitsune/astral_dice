# B20B — 骰战吸收/黄心重对齐(D-10)与 /astralparty 实体清理命令(D-12)移植报告

- 批次：B20B（B12 语义漂移审计 D-10 / D-12）
- 分支：multi-main（工作树 `D:\MCProject\astral_dice_multiloader`）
- 蓝本：`forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/`
- 目标：`fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/`
- 审计原文：`porting/fabric-1.20.1/B12-DRIFT-AUDIT-REPORT.md`（D-10 :102-108、D-12 :115-120）
- 边界：未 commit / push / stash / add / checkout；未跑 gradle；未启动游戏；**未改任何 lang 文件**；未改 build.gradle / gradle.properties / fabric.mod.json / mixins.json（本批无需新增 mixin）。

## 0. 结论摘要

| 项 | 结果 |
|---|---|
| D-10 吸收快照 / 重对齐 / settle | 已落 fabric（等价改写，见 §1） |
| D-10 RenShieldManager 黄心钳制（记录点 + 兜底） | 已落 fabric |
| D-10 applyAlanaBigHit 三个调用点 | 已全部接上（:180 / :194，两条分支合并为单出口，见 §1.2） |
| D-10 幻影三件套（registerPhantom / phantomOf / withPhantomPeeled） | **裁决不搬**（平台差异，非联动），见 §3 |
| D-10 ChipDamageHandler try/finally 收口 | 已补（5 条出口全部落在 try 内），见 §4 |
| D-12 killbarricade / killrepulsion | 已落 fabric，与 forge 逐字节一致（`diff` 空输出，见 §7.4） |
| 新增 REAL javac errors | 0 条归因本批（1 条来自他人未提交文件 `network/VersionHandshake.java`，见 §7.1） |
| BLOCKED | 无 |

### 改动文件（本批自身 delta）

| 文件 | 本批前 | 现在 | 本批净增 | 说明 |
|---|---|---|---|---|
| `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/combat/DiceCombatEvents.java` | 未单独留存 | **2179 行 / 142373 B** | **+185** | D-10 段 `@@ +77,177 @@`（177）+ imports +7/−1（去重一重复 `RenShieldManager` import）+ 两处出口各 +1 注释（出口语句 1:1 替换 `setAmount` → `settleDiceDamage`） |
| `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/ChipDamageHandler.java` | **126 行**（= HEAD） | **152 行 / 10345 B** | **+26** | `git diff --numstat` = **26/0**（0 删除：只插不删）⇒ 26 行全为本批：try/finally 包装 + `applyChipMitigation` 签名/javadoc + 6 行平台注 |
| `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/command/AstralPartyCommand.java` | **581 行**（= HEAD） | **689 行 / 44376 B** | **+108** | `git diff --numstat` = **110/2**；两处删除是 root 上用新分支替换旧末尾，净 +108 |

> 注：`git diff --numstat` 中 DiceCombatEvents 的 294/20 **混入他人在同一文件里的既有未提交改动**（HEAD 1905 行），不能当本批 delta；ChipDamageHandler(26/0) 与 AstralPartyCommand(110/2) 的文件级 diff 恰好全为本批（两者改前均等于 HEAD 行数）。

## 1. D-10 逐符号对照表（forge 行号 → fabric 行号）

### 1.1 符号级对照

| # | forge 符号 / 位置 | forge 行 | fabric 行 | 处理 | 说明 |
|---|---|---|---|---|---|
| 1 | `PRE_HURT_ABSORPTION`（静态表） | 310–312 | **99–100** | 搬 | 本线为 `Collections.synchronizedMap(new WeakHashMap<>())`，与 tick 清扫的迭代器同锁 |
| 2 | `class PreHurtSnapshot` | 319–327 | **111–121** | 搬（加字段） | 本线多一个 `final long gameTime`，承担 forge `phantomOf` 的「跨 tick 判废」职责 |
| 3 | `onLivingHurtRecordAbsorption(LivingHurtEvent)` | 341–352 | **132–141** | 搬 | `@SubscribeEvent(priority = EventPriority.HIGHEST)`；本线 `LivingHurtEvent` 是 `mixin/bridge/LivingHurtBridgeMixin` 的 shim |
| 4 | ├ `RenShieldManager.remainingShieldAbsorption(defender)`（记录点钳制取值） | 346 | **137** | 搬 | 本线真实方法名已核：`item/RenShieldManager.java:100 public static float remainingShieldAbsorption(Player)` |
| 5 | `consumePreHurtAbsorption(LivingEntity)` | 356–365 | **147–157** | 搬 | 兜底：无快照 / 跨 tick ⇒ 用当前值重建（与 forge 同形） |
| 6 | ├ `remainingShieldAbsorption(defender)`（兜底取值） | 363 | **152** | 搬 | 同上 |
| 7 | `settleDiceDamage(LivingEntity, LivingDamageEvent, float)` | 378–424 | **176–196** | 搬（等价改写） | 不回写吸收余额、不登记幻影；理由见 §3 |
| 8 | ├ `applyAlanaBigHit(target, damage)`（damage<=0 早退分支） | 396 | **180** | 搬 | 0 值也走同一出口 ⇒ 与 forge 语义一致（forge 该分支同样会调用） |
| 9 | ├ `applyAlanaBigHit(target, toHealth)`（`instanceof Player` 分支） | 414 | **194** | 搬 | 本线合并为唯一出口 |
| 10 | ├ `registerPhantom(target, toHealth, expectedAfter)` | 420 | — | **不搬** | 见 §3 |
| 11 | └ `applyAlanaBigHit(target, toHealth)`（非玩家分支） | 422 | **194** | 搬（合并） | 本线无幻影剥离、无分支差异 ⇒ 两分支同体，合并为单出口，行为等价 |
| 12 | `applyAlanaBigHit(LivingEntity, float)` 定义 | 436–441 | **205–211** | 搬（换数据源） | 层数取 `SignAttachments.getAlanaPassiveBonus(Player)`（`component/SignAttachments.java:66`），清空取 `AlanaSignItem.clearBonus(Player)`（`item/sign/AlanaSignItem.java:71`）；本线 `ModAttachments` **没有** `getAlanaPassiveBonus` |
| 13 | `registerPhantom` | 475–481 | — | **不搬** | 见 §3 |
| 14 | `phantomOf` | 487–496 | — | **不搬** | 见 §3 |
| 15 | `withPhantomPeeled` | 502–519 | — | **不搬** | 见 §3 |
| 16 | `compensateAbsorption(LivingEntity, float)` 定义 | 529–543 | **220–223** | 搬（等价体） | 本线体 = `PRE_HURT_ABSORPTION.remove(target)`；`finalAmount` 保留形参只为两线同名同签名 |
| 17 | └ `registerPhantom(target, finalAmount, recorded.expectedAfter)`（重登记幻影） | 542 | — | **不搬** | 见 §3 |
| 18 | `onServerTickReconcilePhantoms(TickEvent.ServerTickEvent)` | 555–585 | **232–252** | 搬（等价体） | 本线职责换成「清扫跨 tick / isRemoved / 客户端 的陈旧快照」，`phase != END` 早退 |
| 19 | `settleDiceDamage` 调用点 1（玩家攻击路径） | 1158 | **958** | 搬 | `float applied = ...`；:957 为 parity 注释（forge 同样保留 `applied` 未消费） |
| 20 | `settleDiceDamage` 调用点 2（生物近战路径 `resolveMobMeleeAttack`） | 1495 | **1264** | 搬 | 返回值本线未消费（与 forge 同） |
| 21 | `withPhantomPeeled(target, …)` 调用点（额外加伤） | 1183 | — | **不搬** | 见 §3 |
| 22 | `withPhantomPeeled(victim, …)` 调用点（大当家溅射） | 1252 | — | **不搬** | 见 §3 |
| 23 | `withPhantomPeeled(target, …)` 调用点（狂暴反噬） | 1884 | — | **不搬** | 见 §3 |
| 24 | `compensateAbsorption(target, event.getAmount())`（`onWeakMarkDamage` 内） | 1949 | — | **不搬** | 本线无幻影余额可校正；同一失效由 `consumePreHurtAbsorption` + tick 清扫覆盖 |
| 25 | `compensateAbsorption(player, …)` 调用点（ChipDamageHandler finally） | `event/ChipDamageHandler.java:61` | `event/ChipDamageHandler.java:63` | 搬 | 见 §4 |

### 1.2 `applyAlanaBigHit` 三个调用点逐个核对（任务要求 3）

forge 的三处 = `:396`（damage<=0 早退）、`:414`（Player 分支）、`:422`（非玩家分支）。

| forge | 触发条件 | fabric 对应位置 | 是否接上 |
|---|---|---|---|
| :396 | `damage <= 0` ⇒ `applyAlanaBigHit(target, 0)` | DiceCombatEvents.java **:180**（`if (damage <= 0.0F) { setAmount(0); applyAlanaBigHit(target, 0.0F); return 0.0F; }`） | ✅ |
| :414 | 目标为 `Player` | DiceCombatEvents.java **:194**（公共出口，不分支） | ✅ |
| :422 | 目标非 `Player` | DiceCombatEvents.java **:194**（公共出口，不分支） | ✅ |

**说明**：forge 的 `:414/:422` 之所以分成两支，是因为 `registerPhantom`（:420）夹在中间且对 Player/非 Player 处理不同；本线不搬幻影后两支体完全相同（都是 `applyAlanaBigHit(target, toHealth)`），合并成单出口是**行为等价**的，不是漏接。三处调用点全部接上，**不存在半接**。

`applyAlanaBigHit` 自身判据（fabric :205–211）与 forge :436–441 逐条对齐：目标必须是 `Player`（本线用 `target instanceof Player victim`）、`applied > 10.0F`、且当前被动层数 `> 0` ⇒ `clearBonus`。

### 1.3 settle 的行为差异（必须知情的等价性说明）

forge `settleDiceDamage` 做三件事：①护盾钳制 ②回写吸收余额（`setAbsorptionAmount`）+ 登记幻影 ③修女被动。
fabric 只做 ①③ + 写终值（`event.setAmount(damage)`），**不回写吸收余额**。

理由（本线字节码取证，见 §7.5）：本线 `LivingDamageEvent` 由 Puzzles `LivingEntityFabricMixin#actuallyHurt(DamageSource,float,CallbackInfo)` 在**方法入口**派发，平台随后才走 `f1 = max(dmg - A0, 0)` → 扣 A0 → （仅当 `f1 > 0`）再扣 f1 ⇒ **净效果 = 吸收只扣 min(dmg, A0) 一次**，正是 forge 想要却拿不到的时序。若照搬 forge 的回写 + 幻影补偿，本线会被扣两次（幻影虚高）或在 `f1 == 0` 时整段退还（二次扣减）。

⇒ 因此本线 settle 只写终值、吸收余额与幻影全部不碰，**语义目标与 forge 相同（吸收按骰战终值只扣一次）**，实现手段按平台差异改写。

## 2. 被裁决不搬的第三方模组联动清单

### 2.1 神秘遗物 / 七咒（Enigmatic Legacy, modId `enigmaticlegacy`）

| forge 位置 | 内容 |
|---|---|
| :81 | `AP_CURSE_APPLY` 证据通道注释 |
| :83–90 | 联动整段：`ENIGMATIC_CURSED_RING` / `ENIGMATIC_ACKNOWLEDGMENT` / `ENIGMATIC_TWIST` / `ENIGMATIC_BLESS` 四个常量（附「本线 2.30.1 无 `the_bless` ⇒ 该分支恒不生效」注） |
| :251–257 | `hasEnigmaticCurse(Player)`：查 `cursed_ring` 是否装在 Curios 里（`CuriosCompat.getCuriosInventory`） |
| :259–266 | `isHoldingEnigmaticItem(Player, String)`：主/副手判定 |
| :268–282 | `applyCurseToDicePoints(Player, double)`：七咒 −40%（持 `the_acknowledgment` 再 −20%；`MISAKI_BURST` / `the_twist` / `the_bless` 完全免疫） |
| :1489–1492 / :1919–1922 | `AP_CURSE_APPLY:channel=mob…` / `channel=player…` 机器行 |
| :2061–2072 | `MELEE_WEAPON_EXTRA_INCLUDES` 里的 `enigmaticlegacy:eldritch_pan`（饕餮之锅） |

**fabric 现状（实测 grep）**：整个 fabric 源码树对这些符号 **0 命中**，仅剩 `combat/DiceCombatEvents.java:1779` 一条注释「`enigmaticlegacy:eldritch_pan`（神秘遗物）已随该联动整体移除」。⇒ 与 1.20.1 fabric 线「排除全部第三方模组联动」的既定裁决一致，**本批不新增任何一条**。

### 2.2 关键结论（对任务书的回答）

> 任务书要求「逐条取证：判断哪些**纯属** enigmaticlegacy 七咒联动」。
>
> **取证结论：D-10 列出的符号中，没有任何一个属于该联动。** 七咒联动全部落在「骰点修正 + 证据日志 + 近战武器白名单」三处，与吸收快照 / settle / 幻影 / 修女被动 / `RenShieldManager` 钳制**无交集**。
>
> ⇒ 「联动整条不搬」这条裁决**不构成任何 D-10 符号不搬的理由**；除 §3 的幻影三件套（平台差异）外，D-10 全部符号都已落地。

### 2.3 其余被排除的第三方面（本批顺带核实，均未新增）

- `top.theillusivec4.*`（Curios 直接引用）：fabric 线一律走 `compat/curios/CuriosApi`，本批**未引入**任何 `top.theillusivec4` 符号。
- `net.minecraftforge.*`：本批**未引入**（改走 `isClientSide()` / `new ResourceLocation(a,b)` / `Level#getServer` 等本线写法；本批实际未用到 `ResourceLocation` 与 `getServer`）。

## 3. 幻影三件套：裁决不搬（唯一不搬的 D-10 符号组）

| forge 符号 | forge 行 | 为何不搬 |
|---|---|---|
| `registerPhantom(LivingEntity, float, float)` | 475–481 | 存在前提是「forge 在 `LivingDamageEvent` 里写终值后，平台还会**再扣一次**已扣过的吸收」⇒ 需要登记「幻影余额」让 `:1684` 那一段扣在假值上 |
| `phantomOf(LivingEntity)` | 487–496 | 同上，读幻影余额 + `gameTime` 比较 |
| `withPhantomPeeled(LivingEntity, Runnable)` | 502–519 | 同上，嵌套 `hurt` 前临时剥掉幻影 |

**本线不需要的判据**：fabric `LivingEntity#actuallyHurt` 字节码（§7.5）中第二段扣减被 `if (f1 == 0) return` 前置，只有 `f1 = max(dmg - A0, 0) > 0` 时才发生 ⇒ 本线天然等价于「吸收只扣一次」。
**若照搬的后果**：`f1 == 0`（伤害被吸收完全吃掉）时平台直接 `return`，幻影永远不会被清 ⇒ 吸收余额被永久虚高；`f1 > 0` 时又会多扣一次 ⇒ 人为漏血。两种都是**引入新 bug**。
**补偿手段**：`PreHurtSnapshot.gameTime` + `consumePreHurtAbsorption` 的跨 tick 判废 + `onServerTickReconcilePhantoms`（:232–252）保证快照不残留。

⇒ 登记为**待你裁决项**（见 §8 R-1）。若你认为仍需与 forge 字形对齐（哪怕是空实现），可以再开一批补三个同签名空方法，但当前实现按行为等价处理。

## 4. ChipDamageHandler 早退点全表

### 4.1 结构对照

| 结构 | forge | fabric |
|---|---|---|
| 监听方法 | `onLivingDamage`（:50） | `onLivingDamage`（**:48**） |
| `try` 起点 | :58 | **:57** |
| try 内主体 | `applyChipMitigation(player, event)`（:59） | `applyChipMitigation(player, event)`（**:58**） |
| `finally` | :60 | **:59** |
| finally 内 | `DiceCombatEvents.compensateAbsorption(player, event.getAmount())`（:61） | 同（**:63**） |
| 主体方法 | `applyChipMitigation`（:70–147） | `applyChipMitigation`（**:71–138**） |

### 4.2 全部出口（含早退）—— 逐条确认都在 try 内

| # | 出口性质 | forge 行 | fabric 行 | 代码判据 | 是否在 `applyChipMitigation` 内（⇒ 经过 finally） |
|---|---|---|---|---|---|
| E0 | 方法入口早退（客户端） | :51–53 | :49–51 | `player.level().isClientSide()` / 非 `Player` | ❌ **在 try 之前**（见 4.3） |
| E1 | 早退 | :72 | **:73** | `if (damage <= 0.0F) return;` | ✅ |
| E2 | 早退 | :80 | **:77** | `if (RenShieldManager.remainingShieldAbsorption(player) > 0.0F) return;` | ✅ |
| E3 | 早退 | :86 | **:83** | `if (event.getSource().is(DamageTypes.FELL_OUT_OF_WORLD)) return;` | ✅ |
| E4 | 早退（安全气囊） | :96–99 | **:97–100** | `if (damage >= player.getHealth() && AirbagChipItem.tryNegateFatal(…) ) { event.setAmount(0); return; }` | ✅ |
| E5 | 正常走完 | :147 | :138 | 方法末尾自然返回 | ✅ |
| — | forge 独有段 | :130–146 | — | kangel/ame「超天酱 −1」（本线未移植，见 §9） | — |

**核对结论**：E1–E5 五条出口全部位于 `applyChipMitigation` 方法体内，而该方法体整体处于调用方的 `try` 块中 ⇒ **五条出口都会经过 finally**，与 forge「所有出口（含早退）统一把吸收重对齐一次」的意图一致。

### 4.3 唯一在 try 之外的出口：E0（与 forge 一致，且不影响收口）

fabric :49–51：
```java
if (!(event.getEntity() instanceof Player player)) return;   // 非玩家
if (player.level().isClientSide()) return;                   // 客户端
```
forge :51–53 同形（`ForgeHooks.onLivingDamage` 的服务端语义 + 类型判定），同样在 `try` 之前。
**为什么不移进 try**：① 与 forge 逐行同形；② 这两条路径下 `compensateAbsorption` 无事可做（非玩家 / 客户端都不会有骰战快照）；③ 若移进 try，`player` 变量作用域反而会跨出 try 结构。**已在报告登记，不视为漏收口。**

### 4.4 finally 内**没有**搬的那一条（forge :65）

forge 的 finally 还调 `PaparaSignItem.settleBiteHeal(event)`。本线**没有这个方法**：咬合回血在 fabric 是独立监听器 `item/sign/PaparaSignItem.java:70 public static void onPaparaBiteHurtHeal(LivingDamageEvent)`（LOWEST 优先级），由 `LoaderBus` 反射自动注册，**不需要**在 chip 的 finally 里再调一次（调了会重复回血）。⇒ 已在 :61–62 注释写明，登记为**已知线级差异**（§9）。

## 5. D-12 子命令行为对照

### 5.1 注册与解析

| 项 | forge | fabric |
|---|---|---|
| 子命令字面量 | `killbarricade` / `killrepulsion`（:140/:142） | 同（**:138/:140**） |
| root 挂载 | `.then(killEntityNode(KILL_BARRICADE_LITERAL, BarricadeEntity.class, "barricade", KEY_KILL_BARRICADE))` + repulsion（:207–210） | 同（**:205–208**） |
| 构建器 | `killEntityNode(String literal, Class<? extends Entity> type, String entityId, String langKey)`（:240） | 同（**:238**） |
| 权限 | `.requires(AstralPartyCommand::hasPermission)` ⇒ `REQUIRED_PERMISSION_LEVEL = 2`（:113） | 同（**:111**） |
| 无参 | `DEFAULT_KILL_RADIUS`（**64**）（:148） | 同（**:146**） |
| `all` | 走 `-1` ⇒ 整维度（:245–246） | 同（**:243–244**） |
| `radius <n>` | `IntegerArgumentType.integer(1, MAX_KILL_RADIUS)`，`MAX_KILL_RADIUS = 1024`（:247–249） | 同（**:245–247**） |

### 5.2 `killEntities` 本体（forge :257–299 ↔ fabric :255–297）

| 行为 | 实现（两线一致） |
|---|---|
| 维度 | `CommandSourceStack#getLevel()` ⇒ **只清当前维度** |
| `radius < 0`（all） | 遍历 `level.getAllEntities()`，`type.isInstance(entity)` 过滤 |
| `radius >= 0` | 以 `source.getPosition()` 为中心建 `new AABB(x±r, y±r, z±r)`（**立方体**，对角最远 r×√3），遍历 `level.getEntitiesOfClass(Entity.class, area)` 再按 `type.isInstance` 过滤 |
| 先收集再删 | `List<Entity> found` 先装满，再逐个 `discard()`（注释写明：`getAllEntities()/getEntitiesOfClass()` 返回的集合可能在 `discard()` 期间被改写） |
| 跳过已移除 | `if (entity.isRemoved()) continue;` |
| 删除方式 | `Entity#discard()`（路障删隐形碰撞方块 + 解除半径 10 仇恨；强化拒止还原飞行目标重力） |
| 机器行 | `LOGGER.info("{}|type={}|killed={}|mode={}|radius={}|dim={}|x={}|y={}|z={}", "AFTERM", entityId, killed, radius<0 ? "all" : "radius", radius<0 ? -1 : radius, level.dimension().location(), floor(x), floor(y), floor(z))` |
| 玩家反馈 | `source.sendSuccess(() -> Component.translatable(langKey, killed), true)` |
| 返回值 | `killed`（供命令链判成功） |

**逐字节核验**：`diff <(sed -n '226,299p' forge…AstralPartyCommand.java) <(sed -n '224,297p' fabric…AstralPartyCommand.java)` ⇒ **空输出**（74 行全等，含注释）。证据见 §7.4。

### 5.3 类 javadoc 同步

- 新增两条 `<li>` 说明 `killbarricade`/`killrepulsion`（与 forge 一致）。
- 原「五个子命令:三个写入类」计数改为「**七个子命令:五个写入类**」（forge 原文口径）。

## 6. 需要你补的 lang 键（本批**未改**任何 lang 文件）

| 键 | zh_cn | en_us | ja_jp |
|---|---|---|---|
| `command.astral_dice.astralparty.killbarricade.success` | `已清除 %s 个路障实体` | `Cleared %s barricade(s)` | `バリケードを %s 個除去しました` |
| `command.astral_dice.astralparty.killrepulsion.success` | `已清除 %s 个强化拒止实体` | `Cleared %s repulsion field(s)` | `強化拒止フィールドを %s 個除去しました` |

三语值逐字取自 forge `assets/astral_dice/lang/{zh_cn,en_us,ja_jp}.json` 的 :789/:790（zh_cn）、:789/:790（en_us）、:788/:789（ja_jp）对应行。
**在你补进 fabric 三语之前，这两条命令会显示原始 key（`%s` 不展开）**，功能不受影响。

## 7. 自检原始输出

### 7.1 `python scripts/__javac_check.py`

```
EXIT 1 ERRORS 172 jars 685 srcs 525
=== buckets (NOISE_* = 由 Loom AW / 接口注入 提供，javac 侧无法复现) ===
  164  NOISE_AW
    7  NOISE_IFACE_INJECTION
    1  REAL
=== REAL errors ===
    1  cannot find symbol | symbol:   method disconnect(MutableComponent) location: variable connection of t
=== REAL by file ===
    1  F
saved errors.txt lines= 989
JAVAC_EXIT=0
```

**那 1 条 REAL 的归因（不是本批）**：

```
D:\MCProject\astral_dice_multiloader\fabric-1.20.1\src\main\java\com\merlinkitsune\astral_dice\network\VersionHandshake.java:259: error: cannot find symbol
                connection.disconnect(Component.literal(text));
                          ^
  symbol:   method disconnect(MutableComponent)
  location: variable connection of type ClientPacketListener
```

- 该文件 `git status --porcelain` = `?? `（**未跟踪**），mtime `2026-11-01 19:36:32`（本会话进行中由他人写入），不在本批任务范围、本批从未打开。
- 同一时段 `srcs` 由 522 涨到 525（新增 3 个未跟踪源文件），均属他人并行工作。
- **本批三个文件在 `errors.txt` 里零命中**（连 NOISE 桶都没有）：`grep -c "DiceCombatEvents.java\|ChipDamageHandler.java\|AstralPartyCommand.java" errors.txt` ⇒ 0。
- 本批开工时的基线也是「REAL 空」，唯一变化即上述他人文件 ⇒ **本批新增 REAL errors = 0**。

### 7.2 `python tools/check_lang_sync.py --line fabric-1.20.1`

```
[OK]   fabric-1.20.1: en_us=1095 （退出码 0）
```

（终端把中文按 GBK 解码显示成乱码，判据是 `[OK]` 与退出码 0；本批未改 lang，结果与开工前一致。）

### 7.3 `python tools/verify_fabric_assets.py`（只报告）

```
jar = fabric-1.20.1\build\libs\astral_dice-1.3.7-alpha.2+fabric_1.20.1.jar
[!] build/libs 内 jar 版本 1.3.7-alpha.2+fabric_1.20.1 与 mod_version 1.5.0-rc.7+fabric_1.20.1 不符，改为扫描源码资源目录
[1] 物品闭环: 注册 157 (方块物品 3) / 模型 158 / 贴图 155 (方块贴图另计)
[2] 标签闭环: 引用 11 / 自建 29 / 有提供者 11
[3] 音效闭环: 注册 23 / sounds.json 23 / ogg 23
[4] 粒子闭环: 注册 2 / particles/*.json 2
[5] 语言闭环: zh_cn 1095 / en_us 1095 / ja_jp 1095
[6] 手册闭环: 条目 184 / 引用配方 147
[7] 创意标签覆盖: 156/157
    已知与基线一致（不阻塞）: ['repulsion']
[8] 饰品槽位图标: 检查 7 条 icon
=== PASS: 8 项闭环全部通过 ===
EXIT=0
```

第 1 行的 `[!]` 是**既有陈旧 jar**（build/libs 里 1.3.7 的旧产物）导致的假 FAIL，按任务书不追；脚本自动改扫源码资源目录，8 项闭环全 PASS。

### 7.4 D-12 与 forge 逐字节 diff

```
$ diff <(sed -n '226,299p' forge-1.20.1/.../command/AstralPartyCommand.java) \
       <(sed -n '224,297p' fabric-1.20.1/.../command/AstralPartyCommand.java) && echo "IDENTICAL 74L"
IDENTICAL 74L      (exit 0)
```

### 7.5 文件字节级自检（CRLF / UTF-8 无 BOM）

| 文件 | 行数 | 字节 | bareLF | BOM |
|---|---|---|---|---|
| `combat/DiceCombatEvents.java` | 2179 | 142373 | **0** | **false** |
| `event/ChipDamageHandler.java` | 152 | 10345 | **0** | **false** |
| `command/AstralPartyCommand.java` | 689 | 44376 | **0** | **false** |

### 7.6 平台取证依据（供复核 settle 的等价性）

**forge（`forge-1.20.1-47.4.10-sources.jar`，`LivingEntity.java`）**：
```
:1665  ForgeHooks.onLivingHurt(...)                       // 吸收未扣
:1669  f1 = max(damage - A0, 0)
:1670  setAbsorptionAmount(A0 - (damage - f1))          // 第一次扣吸收
:1680  ForgeHooks.onLivingDamage(...)                   // 骰战在此覆盖终值
:1684  setAbsorptionAmount(getAbsorptionAmount() - f1)  // 第二次扣吸收 ⇒ forge 侧吸收被扣两次
```
**fabric（loom `minecraft-merged` 1.20.1，字节码偏移）**：
```
actuallyHurt: 23-35   f1 = max(dmg - A0, 0)
actuallyHurt: 36-45   setAbsorptionAmount(A0 - (dmg - f1))
actuallyHurt: 107-113 if (f1 == 0) return;               // ★ 关键差异：f1==0 时直接返回
actuallyHurt: 133-140 setAbsorptionAmount(getAbsorptionAmount() - f1)   // 仅 f1>0 时发生
```
**fabric 事件锚点**（`fuzs.puzzleslib.mixin.LivingEntityFabricMixin`，由 `puzzles-lib` remapped jar 中 `javap -p -c` 取得）：
```
protected void actuallyHurt(DamageSource, float, CallbackInfo)  // 偏移 0-54：isInvulnerableTo → 派发 LIVING_HURT → 可 cancel
protected float actuallyHurt(float, DamageSource)              // 回写修改后的值
```
`platform/PuzzlesBridges.java:100-112` 把 `LivingHurtCallback` 桥成 `LivingDamageEvent(entity, source, amount)` 并经 `LoaderBus` 派发 ⇒ **本线 `LivingDamageEvent` 落在 `actuallyHurt` 入口、护甲/抗性/吸收之前，载荷是原始入参**。
⇒ 本线 settle 写进去的终值会被平台再套一次护甲/抗性/保护（**既有线级差异**，见 §8 R-2，本批不处理）。

## 8. 无法判定 / 需要你裁决

| # | 项 | 现状 | 需要你定 |
|---|---|---|---|
| R-1 | **幻影三件套不搬** | 已按「平台差异 ⇒ 行为等价改写」处理，`PreHurtSnapshot.gameTime` + tick 清扫承担同等职责 | 认可该裁决，还是补三个同签名空方法以对齐字形？ |
| R-2 | **本线 `LivingDamageEvent` 载荷时点**与 forge 不同（护甲前 vs 护甲后）⇒ 骰战写终值后平台会再套一次护甲/抗性/保护 | 既有线级差异，本批**未动**（动它等于重定义整条伤害管线） | 是否另开批次，把 settle 的终值口径改成「护甲前」或把事件锚点后移？ |
| R-3 | `RenShieldManager.java` 的 javadoc 断言「本线 LivingDamageEvent 派发在吸收之后」 | 与 §7.6 字节码取证**相反**，属文档漂移；本批未改该文件（不在任务范围） | 是否授权我改这条 javadoc？ |
| R-4 | `compensateAbsorption` 的 `finalAmount` 形参在本线**未使用** | 保留是为了两线同名同签名（方法 javadoc 已写明） | 保留（默认），还是改成单参？ |
| R-5 | 本线 `onWeakMarkDamage` 无 `isDiceBattleSettledDamage`（N8）判据，且 forge :1949 的 `compensateAbsorption` 调用点在本线不存在 | 本批**未补**（属 B12 其他条目范围） | 是否并入后续批次？ |

## 9. 已知既有漂移登记（本批**未动**，仅供后续批次排期）

- `fabric DiceCombatEvents#onBerserkDamageTaken`（约 :1437–1453）缺 forge :1871–1873 的 `isInternalAoe` 守卫。
- `fabric DiceCombatEvents#onWeakMarkDamage`（约 :1490–1508）缺 `isDiceBattleSettledDamage`（N8）判据（forge :1938 有；fabric 全树无该符号）。
- `fabric event/ChipDamageHandler` 缺 forge :130–146 的 kangel/ame「超天酱 −1」段。
- `fabric` 无 `PaparaSignItem.settleBiteHeal`，改由独立 `onPaparaBiteHurtHeal`（`item/sign/PaparaSignItem.java:70`）承担 ⇒ chip 的 finally 不再调用它（见 §4.4）。
- `fabric DiceCombatEvents` 缺 forge :1153–1155 的 Lingling +5 并入段。
- `fabric` 全树无 `enigmaticlegacy` / `enigmaticdelicacy` 之外的第三方联动符号（既定裁决，非缺陷）。

## 10. 复现本批结论的最小命令集

```bash
cd /d/MCProject/astral_dice_multiloader
# 1) D-10 符号是否落地
grep -n "PRE_HURT_ABSORPTION\|settleDiceDamage(target\|applyAlanaBigHit(LivingEntity\|compensateAbsorption\|onServerTickReconcilePhantoms" \
     fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/combat/DiceCombatEvents.java
# 2) try/finally 收口
grep -n "try {\|finally\|applyChipMitigation\|compensateAbsorption" \
     fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/ChipDamageHandler.java
# 3) D-12 与 forge 逐字节一致
diff <(sed -n '226,299p' forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/command/AstralPartyCommand.java) \
     <(sed -n '224,297p' fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/command/AstralPartyCommand.java)
# 4) 三连自检
python scripts/__javac_check.py; python tools/check_lang_sync.py --line fabric-1.20.1; python tools/verify_fabric_assets.py
```

