# B3 — 电击手套重设计移植报告（forge-1.20.1 → fabric-1.20.1）

- 宿主 `D:\MCProject\astral_dice_multiloader`，分支 `multi-main`；唯一蓝本 = forge-1.20.1 同路径（2026-10-21 重设计）。
- 落地 **4 个文件**：`ElectricGloveChipItem.java | 173 ++++--`、`SpellDamageRegistry.java | 93 +++---`、`DamageCardFxScheduler.java | 43 +++-`、`RailgunStrikeScheduler.java | 59 ++++-`（合计 `4 files changed, 267 insertions(+), 101 deletions(-)`，只含本批 4 文件）。
- 未 commit、未启动游戏、未跑 gradle。4 个文件全部 CRLF（`ElectricGloveChipItem 190/0`、`SpellDamageRegistry 534/0`、`DamageCardFxScheduler 635/0`、`RailgunStrikeScheduler 206/0`，格式 = CRLF 数 / 裸 LF 数）。
- 4 个文件内 `net.minecraftforge` / `net.neoforged` 引用 **0 命中**（grep 已验证）。

## 1. 改动清单（前 3 / 后 3 行原文）

### 1.1 `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/chip/ElectricGloveChipItem.java`
89 → 190 行，整文件换 forge 版；旧版备份 `fabric-1.20.1/__glove_before.java.bak`。

**常量区**：旧 :34/:36/:38 → 新 :51-59（与 forge :51-59 同号同文）
```java
- public static final int   CHARGE_REQUIRED = 4;          // 旧 :34
- public static final int   CHARGE_GAIN_PER_CARD = 1;     // 旧 :36
- public static final int   AOE_RADIUS = 3;               // 旧 :38
+ public static final int   AOE_RADIUS = 3;               // 新 :51 = forge :51
+ public static final float SHOCK_DAMAGE = 2.0F;          // 新 :53 = forge :53
+ public static final int   SHOCK_DELAY_TICKS = 20;       // 新 :55 = forge :55
+ public static final int   COOLDOWN_TICKS = 20 * 60;     // 新 :57 = forge :57
+ public static final int   CHARGE_PER_HIT = 1;           // 新 :59 = forge :59
```

**isEquipped 空判顺序**（forge 已修，本批一并对齐）：旧 :45-48 → 新 :75-79
```java
- public static boolean isEquipped(Player player) {
-     if (!CurioSlotUtil.hasDiceEquipped(player)) return false;   // 旧 :47 先解引用
-     if (player == null) return false;                           // 旧 :48 才判空
+ public static boolean isEquipped(Player player) {
+     if (player == null) return false;                           // 新 :76
+     if (!CurioSlotUtil.hasDiceEquipped(player)) return false;   // 新 :78
+     var curios = CuriosApi.getCuriosInventory(player);          // 新 :79 ← 唯一平台垫片（forge :79 = CuriosCompat）
```

**出牌钩子**：旧 :71-88（加充能 + 消耗 4 + 绑载体）→ 新 :121-139（只排延迟动作）
```java
- ChargeManager.addStacks(player, CHARGE_GAIN_PER_CARD);                     // 旧 :76
- if (ChargeManager.getStacks(player) < CHARGE_REQUIRED) return;             // 旧 :78
- boolean claimed = cardStack.is(ModItems.LIVING_PAGE.get())                 // 旧 :82
-         ? LivingPageFlightScheduler.markLatestGlovePaid(serverPlayer)       // 旧 :83
-         : DamageCardFxScheduler.markLatestGlovePaid(serverPlayer, cardStack);// 旧 :84
- ChargeManager.consume(player, CHARGE_REQUIRED);                           // 旧 :87
+ if (isOnCooldown(player)) return;                                         // 新 :126
+ final LivingEntity target = DamageCardFxScheduler.latestGloveTarget(serverPlayer, cardStack); // 新 :130
+ RailgunStrikeScheduler.Pending pending = RailgunStrikeScheduler.schedule(   // 新 :133
+         level, target, target.position(), serverPlayer, SHOCK_DELAY_TICKS,
+         ElectricGloveChipItem::executeShock);                              // 新 :135
+ COOLDOWN_END.put(serverPlayer.getUUID(), level.getGameTime() + COOLDOWN_TICKS); // 新 :138
```

**新增 executeShock**（新 :159-189 = forge :159-189）：:162 `collectGloveShockTargets` → 逐目标 :170 `applySpellDamageAmount(level, cause, victim, (int) SHOCK_DAMAGE, false)` → 命中才 :175 `ElectricShockFx.sparksAt(...)` + :177 `ChargeManager.addStacks(cause, CHARGE_PER_HIT)` → 至少命中一个才 :182 `ElectricShockFx.shockSound(...)`。

### 1.2 `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/combat/SpellDamageRegistry.java`
`git diff` 头 `@@ -383,62 +383,45 @@`（净 -17 行）：**删旧修饰器、加静态收集器，不留双份逻辑**。
- 删（旧 :386-441 整块 `registerModifier(new SpellDamageModifier(){...})`）：旧 :394-396 `isGloveSplashInstance() && !isGloveSplashSettling() && ...ElectricGloveChipItem.isEquipped(ctx.attacker)`；旧 :406 `float total = ctx.event.getAmount();`；旧 :417-418 `ModDamageTypes.trueDamage(...)`；旧 :429/:434 `markCardSplashHit(e)` + `e.hurt(source, total)`。
- 加（新 :407-425，照 forge :419-437 整段）：:407 签名 / :411 `return List.of();` / :415 `new AABB(center, center).inflate(r)` / :417 `getEntitiesOfClass` / :423 `LinkedHashSet` 去重 / :424 `return new ArrayList<>(unique)`。
- 删后本文件 `isGloveSplash*` 命中 **0**；**未新增 import**（新方法全用全限定名）。

### 1.3 `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/combat/DamageCardFxScheduler.java`
- :178 `markLatestGlovePaid` javadoc 补 forge 2026-10-21「改用 latestGloveTarget(peek)、可能 dead code、保留勿删」段。
- :207 **新增** `public static LivingEntity latestGloveTarget(ServerPlayer, ItemStack)`（= forge :219）：按 `kindOf` 配对的 peek，不置 `glovePaid`、不出队、无命中返回 null。⚠️ 超出「只改可见性」的字面范围，但不补则 1.1 的 :130 编译不过（fabric 全树此前 0 命中）。
- :596 `private static void` → `public static boolean applySpellDamageAmount`；:598 null/dead 返 `false`；:600 `final boolean[] dealt = {false};`；:603 `dealt[0] = target.hurt(...)`；:622 `return dealt[0];`（结算逻辑同 forge :616，仅可见性 + 返回值）。

### 1.4 `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/RailgunStrikeScheduler.java`（⚠️ 越界，见 §6-1）
- :41 `@FunctionalInterface public interface StrikeAction`；:60 `private StrikeAction action;`；:63 构造第 7 参；:74 `action()` 访问器。
- :115 旧 float 重载改为 :123 `pending.action = (l, c, p) -> RailgunChipItem.executeStrike(l, c, p, pending.damage());`（**必须读实例字段**，否则 `setDamage` 回填失效）。
- :142-143 新增 6 参 `StrikeAction` 重载；:195-198 process 里 `p.action() != null ? action.run(...) : RailgunChipItem.executeStrike(...)`；:202 warn 文案改「延迟动作触发失败(电磁炮雷击 / 电击手套)」。

## 2. difflib 对照结论（剥 package/import 行 + CRLF 归一后重算，脚本输出留在 `porting/fabric-1.20.1/.b3-diff.tmp`）

| 文件 | fabric / forge 行数 | similarity | 剩余差异（逐处说明） |
|---|---|---|---|
| ElectricGloveChipItem | 165 / 165 | **0.99394** | 仅 1 行：`CuriosApi.getCuriosInventory` ↔ `CuriosCompat.getCuriosInventory` —— 平台垫片，预期（forge 走 starengine，fabric 走本仓 compat.curios） |
| SpellDamageRegistry | 508 / 520 | **0.97082** | `collectGloveShockTargets` 段 **0 差异**；其余为**移植前既有**：fabric 2026-09-29 主动裁掉 irons_spellbooks 的 14 个法术 key（forge 仍保留）+ 魔法箭袋注释措辞 —— 与本批无关 |
| DamageCardFxScheduler | 598 / 616 | **0.90774** | `applySpellDamageAmount` / `latestGloveTarget` 段 **0 差异**；其余为 forge 2026-10-21 的 `BeamFxPayload` 客户端光线渲染（替代 fabric 的 84 尘粒）+ 板砖 NBT 组装写法 + 类头 javadoc + `@Mod.EventBusSubscriber` —— 属光束渲染批次，本批按「其余不动」未碰 |
| RailgunStrikeScheduler | 188 / 189 | **0.99735** | 仅 1 行：forge 的 `@Mod.EventBusSubscriber(modid = AstralDiceMod.MODID)`；fabric 由 `AstralDiceMod.java:164` 的 `LoaderBus.INSTANCE.register(...)` 登记，不适用 |
| ElectricShockFx（**未改**） | 75 / 72 | 0.96599 | 仅 fabric 多 3 行移植注记 javadoc；`victimCenter` / `sparksAt` / `shockSound` 三方法同签名同实现 |

## 3. 调用面审计（`grep -rn ... fabric-1.20.1/src --include=*.java`）

| 调用点 | 结论 |
|---|---|
| `item/ModItems.java:806` `new ElectricGloveChipItem(new Item.Properties()...)` | ✅ 仍成立（构造器 `Properties` 未变） |
| `item/ChargeManager.java:51` `\|\| item instanceof ...ElectricGloveChipItem` | ✅ 仍成立（新版仍 `extends BaseChipItem`，isChargeChipItem 白名单口径不变） |
| `item/card/BaseEffectCardItem.java:511` `ElectricGloveChipItem.onEffectCardUsed(player, stack);` | ✅ 仍成立（新签名同为 `static void (Player, ItemStack)`，调用点在 `applyEffect` **之后**，正好满足新设计的 peek 前提） |
| `combat/SpellDamageRegistry.java:396` / `:404` / `:414` | ⚠️ 旧修饰器对 `isEquipped` 的**调用**已随块删除；残留仅 javadoc（:396/:404 文字）与半径回退常量 `:414 ...ElectricGloveChipItem.AOE_RADIUS` —— 均属新设计，不冲突 |
| `combat/DamageCardFxScheduler.java:413`（resolveBeam 主命中）、`:578`（applySpellDamage 包装） | ✅ `void` → `boolean` 源兼容，两处忽略返回值即可 |
| `item/chip/ElectricGloveChipItem.java:170` | ✅ 新调用方，消费返回值（每目标 +1 充能） |
| `item/chip/ElectricGloveChipItem.java:162` → `collectGloveShockTargets` | ✅ 此前 fabric 全树 0 命中，现为唯一调用点 |
| `DamageCardFxScheduler.java:155`/`:183`、`event/LivingPageFlightScheduler.java:52`/`:86`（`glovePaid` / `markLatestGlovePaid`） | ✅ **按指示保留**；但已无调用方（与 forge 一致）⇒ 恒 `false`，`runAsGloveSplashInstance` 分支事实上不可达 |

## 4. ElectricShockFx：已接活（不再是死代码）
引用行 —— `item/chip/ElectricGloveChipItem.java:8` `import ...combat.ElectricShockFx;`、`:175` `ElectricShockFx.sparksAt(level, ElectricShockFx.victimCenter(victim));`、`:182` `ElectricShockFx.shockSound(level, center.add(0.0D, 1.0D, 0.0D));`。三处都在新 `executeShock` 内：每个**真正受伤**的目标一发火花，一次电击只播一次音（避免半径 3 格多目标叠成轰鸣）。

## 5. 行为差异声明（可直接向用户报告）
旧设计（fabric 2026-10-05）：佩戴手套时**每张效果牌**先 +1 充能（`CHARGE_GAIN_PER_CARD`），伤害效果牌在充能 ≥ 4 时**当场**消耗 4 层（`CHARGE_REQUIRED`），把「可扩散」写进该牌本次投出的伤害载体（`DamageCardFxScheduler` / `LivingPageFlightScheduler` 的 `glovePaid`）；等这张牌命中主目标的那一瞬间，法伤链里的手套修饰器把**本次主目标伤害的完整金额**以真伤（`ModDamageTypes.trueDamage`，无视护甲）同步扩散给主目标 **3 格 AABB 立方体内**的其他敌对目标（显式排除主目标与施法者，`markCardSplashHit` 保证同一次用量内只吃一次）——无延迟、无冷却、无粒子无音效，扩散伤害随主目标伤害水涨船高，代价是每 4 张效果牌才换来一次。新设计（forge 2026-10-21，本批 1:1 移植）：**不再消耗充能、也没有充能门槛**，出牌时只做一次「排程」——用 `latestGloveTarget` peek 到该牌的主目标当圆心，登记 **20 tick（1 秒）后**的延迟动作（`RailgunStrikeScheduler`，与电磁炮雷击共用同一队列/同一 tick 边界），随即把玩家冷却置为 **1200 tick（1:00）**；到期后 `executeShock` 自行枚举**以该点为中心、半径 3 格的球体**（改用中心点距离 `distanceToSqr` 筛，替代旧的 AABB 立方体；**包含主目标**，仅排除施法者）内所有 `isBlessingTarget` 目标，逐目标固定 **2 点**伤害（`SHOCK_DAMAGE = 2.0F`，走效果牌同一条 `astral_dice:card_spell` 法伤通道，**不再是真伤**），每造成一次伤害给施法者 **+1 充能**（`CHARGE_PER_HIT`，含主目标），并在每个受伤目标身上撒电火花、在圆心播一次电击音效。三处关键差异：① 半径同为 3 格，但判定由立方体改球体、主目标由「排除」改「包含」；② 单次伤害由「等于主目标伤害的不定额真伤、瞬时」变为「每目标固定 2 点法伤、延迟 1 秒、带粒子与音效」；③ 防刷由「4 层充能 / 每张牌一次」改为「**每玩家 60 秒硬冷却**」，且冷却在**排程时刻**就检查并置位（连打不会重复排队；若该牌本次没产出 FX 载体 ⇒ peek 失败则不排程、也不进冷却），同时充能来源由「使用效果牌 +1」变成「只来自电击命中本身」。

## 6. 阻塞项与需裁决项

1. **需裁决（越界改动）**：`event/RailgunStrikeScheduler.java` 在用户「不要碰 event 目录」清单内，但 forge `ElectricGloveChipItem:133` 必须调 6 参 `StrikeAction` 重载，fabric 原本只有 `float` 重载 ⇒ 不补这一处，本批文件直接编译不过。已按 forge 增量**逐字**补齐（§1.4），未加 forge 的 `@Mod.EventBusSubscriber`。请在「保留 / 改派他人 / 回滚并另立批次」中裁决；`porting/fabric-1.20.1/MAPPING-1.5.0.md:503` 已把该文件列为缺口 #41（与电击链 #13/#15/#24 同族），说明它本来就在待补清单里。
2. **超任务书范围的必要补丁**：`DamageCardFxScheduler.latestGloveTarget`（§1.3 第二条）不在「只把 `applySpellDamageAmount` 改 public」的字面范围内，但 forge 手套 :130 直接调用它、fabric 全树 0 命中 ⇒ 不补即编译失败。若不允许，需把该文件整块另立批次。
3. **编译验证缺失**：任务书给的唯一编译根因 `item/sign/LinglingSignManager` **已被另一批次补齐**（`fabric-1.20.1/.../item/sign/LinglingSignManager.java`，21,570 B，18:14 落盘，`git status` 为 `??`）。按任务书「不要跑 gradle 证明编译」，本批**未做编译验证**，仅源码级自检 + grep 调用面核对。
4. **留待其它批次（非本批范围）**：`DamageCardFxScheduler` 仍走旧的 84 枚尘粒画法，而 fabric 侧 `network/BeamFxPayload.java`、`client/BeamFxRenderer.java`、`ModNetwork.sendToPlayersTrackingEntity`（`:105`）都已存在，forge 2026-10-21 已切 `sendBeamFx` —— 属光束渲染批次。
5. **观察（无需动作）**：`DiceCombatEvents.isGloveSplashSettling` / `runAsGloveSplashSettlement` 删修饰器后已无生产方（仅保留定义，forge 相同）；`glovePaid` 载体保留但无写方 ⇒ 恒 `false`。

## 7. 复现自检
```bash
# 1) difflib 对照（剥 package/import 行、CRLF 归一后取 difflib.SequenceMatcher.ratio）—— §2 结论由其输出得出
#    5 组 fabric/forge 文件对；完整输出留在 porting/fabric-1.20.1/.b3-diff.tmp
# 2) 调用面 + 接活
grep -rn 'ElectricGloveChipItem\|collectGloveShockTargets\|latestGloveTarget\|applySpellDamageAmount\|ElectricShockFx' fabric-1.20.1/src --include=*.java
# 3) 平台污染 / 行尾
grep -rn 'net\.minecraftforge\|net\.neoforged' <本批 4 文件>          # 0 命中
python3 -c "print(open(f,'rb').read().count(b'\r\n') > 0)"           # 4 文件全 CRLF、0 裸 LF
```

_临时产物：`porting/fabric-1.20.1/.b3-diff.tmp`、`porting/fabric-1.20.1/.b3-fx.diff`（可删）；旧版源码备份 `fabric-1.20.1/__glove_before.java.bak`（未跟踪，建议保留到本线可编译后再删）。_
