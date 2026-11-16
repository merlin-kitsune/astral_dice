# B3 — fabric-1.20.1 SignHook 注册式分发层死代码清理（缺陷 C-3）

- 仓库：`F:/MCProject/astral_dice_multiloader`
- 分支 / HEAD：`multi-main` / `fd990c15`（`git rev-parse` 实读，未变更）
- 批次时间：2026-11-01
- 改动范围：`fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/` 下 3 个文件（2 删 1 改）
- 纪律确认：未 commit / 未 push / 未 stash / 未 tag；未启动游戏；未触碰工作树中他人改动（含 `src/generated` 约 600 个删除、`AGENTS.md`、`docs/**`、`DiceCombatEvents.java`、`ModNetwork.java`、`TargetSelectionClient.java`）

---

## 1. 取证（删除前，原文）

### 1.1 `grep -rn "SignHookDispatcher" fabric-1.20.1/src`（5 命中）

```
fabric-1.20.1\src\main\java\com\merlinkitsune\astral_dice\combat\DiceCombatEvents.java:594:             // 阿兰娜立牌:触发骰神赐福 → 被动攻击 +5(等价主线的 SignHookDispatcher.fireBlessingTriggered
fabric-1.20.1\src\main\java\com\merlinkitsune\astral_dice\item\sign\SignHookDispatcher.java:38: public final class SignHookDispatcher {
fabric-1.20.1\src\main\java\com\merlinkitsune\astral_dice\item\sign\SignHookDispatcher.java:39:     private SignHookDispatcher() {
fabric-1.20.1\src\main\java\com\merlinkitsune\astral_dice\item\sign\SignHookDispatcher.java:74:             signs.sort(Comparator.comparingInt(SignHookDispatcher::priorityOf));
fabric-1.20.1\src\main\java\com\merlinkitsune\astral_dice\item\sign\SignHooks.java:13:  * <p>分发入口在 {@link SignHookDispatcher}：它遍历玩家**当前装备的立牌**（Curios），
```

分类：**自引用 4 处**（SignHookDispatcher.java 自身 3 处声明 + SignHooks.java:13 一句 Javadoc 交叉引用）、**外部命中 1 处但为注释**（DiceCombatEvents.java:594 行注释）。**零生产调用点**。

### 1.2 `grep -rn "SignHooks" fabric-1.20.1/src`（16 命中）

```
fabric-1.20.1\src\main\java\com\merlinkitsune\astral_dice\item\sign\SignHooks.java:20: public final class SignHooks {
fabric-1.20.1\src\main\java\com\merlinkitsune\astral_dice\item\sign\SignHooks.java:21:     private SignHooks() {
fabric-1.20.1\src\main\java\com\merlinkitsune\astral_dice\item\sign\SignHookDispatcher.java:19:  * 调用实现了对应 {@link SignHooks} 接口的实例；不维护 {@code signId → 回调} 注册表。
fabric-1.20.1\src\main\java\com\merlinkitsune\astral_dice\item\sign\SignHookDispatcher.java:35:  * {@link AlanaSignItem}（实现 {@link SignHooks.BlessingTriggered}）会命中；
fabric-1.20.1\src\main\java\com\merlinkitsune\astral_dice\item\sign\SignHookDispatcher.java:45:       * {@link MamushiSignItem} 都实现 {@link SignHooks.BlessingEnded}),只按槽位序分发会与旧实现相反。
fabric-1.20.1\src\main\java\com\merlinkitsune\astral_dice\item\sign\SignHookDispatcher.java:108:             if (sign instanceof SignHooks.BlessingTriggered hook) {
fabric-1.20.1\src\main\java\com\merlinkitsune\astral_dice\item\sign\SignHookDispatcher.java:111:             if (sign instanceof SignHooks.SplashBlessingTrigger hook) {
fabric-1.20.1\src\main\java\com\merlinkitsune\astral_dice\item\sign\SignHookDispatcher.java:121:             if (sign instanceof SignHooks.BiteBlessingTrigger hook) {
fabric-1.20.1\src\main\java\com\merlinkitsune\astral_dice\item\sign\SignHookDispatcher.java:130:             if (sign instanceof SignHooks.BlessingEnded hook) {
fabric-1.20.1\src\main\java\com\merlinkitsune\astral_dice\item\sign\SignHookDispatcher.java:139:             if (sign instanceof SignHooks.BrokenTargetAttack hook) {
fabric-1.20.1\src\main\java\com\merlinkitsune\astral_dice\item\sign\SignHookDispatcher.java:148:             if (sign instanceof SignHooks.DiceJudgmentRoll hook) {
fabric-1.20.1\src\main\java\com\merlinkitsune\astral_dice\item\sign\SignHookDispatcher.java:157:             if (sign instanceof SignHooks.DiceRollResult hook) {
fabric-1.20.1\src\main\java\com\merlinkitsune\astral_dice\item\sign\SignHookDispatcher.java:166:             if (sign instanceof SignHooks.DodgeCounter hook) {
fabric-1.20.1\src\main\java\com\merlinkitsune\astral_dice\item\sign\SignHookDispatcher.java:175:             if (sign instanceof SignHooks.DragonRoar hook) {
fabric-1.20.1\src\main\java\com\merlinkitsune\astral_dice\item\sign\SignHookDispatcher.java:184:             if (sign instanceof SignHooks.SplashCost hook) {
fabric-1.20.1\src\main\java\com\merlinkitsune\astral_dice\item\sign\AlanaSignItem.java:35: public class AlanaSignItem extends BaseSignItem implements SignHooks.BlessingTriggered {
```

分类：15 处在 `SignHooks.java` / `SignHookDispatcher.java` 内部（声明、Javadoc、`instanceof` 分派），**唯一外部命中是 `AlanaSignItem.java:35` 的 `implements`**。

### 1.3 `grep -rn "implements SignHooks" fabric-1.20.1/src`（1 命中）

```
fabric-1.20.1\src\main\java\com\merlinkitsune\astral_dice\item\sign\AlanaSignItem.java:35: public class AlanaSignItem extends BaseSignItem implements SignHooks.BlessingTriggered {
```

即：除 `AlanaSignItem` 外**无任何实现者**（`SignHooks` 的 10 个嵌套接口中 9 个零实现）。

### 1.4 补充扫描 `grep -rn "SignHook" fabric-1.20.1`（全子项目 21 命中）

与 1.1+1.2 并集一致，全部落在 `fabric-1.20.1/src/main/java/.../item/sign/{SignHooks,SignHookDispatcher,AlanaSignItem}.java` 与 `DiceCombatEvents.java:594` 一行注释。**资源侧（`src/main/resources`、mixin json、lang、data）零命中**，无反射字符串（`Class.forName`）引用。

### 1.5 钩子实例方法残留扫描

`grep -n "handleBlessingTriggered|handleDiceBlessingTriggered|handleDiceBlessingEnded|handleAttackBrokenTarget|handleDiceJudgmentRoll|handleDiceRollResult|handleDodgeCounter|handleDragonRoar|handleSplashCost|handleSplashBlessing|handleBiteBlessing"` 在 `fabric-1.20.1/src` 共 22 命中：11 处在 `SignHooks.java`（接口声明）、10 处在 `SignHookDispatcher.java`（调用）、**1 处在 `AlanaSignItem.java:60`（唯一实现）**。除此之外没有任何立牌类残留 `handleXxx` 实例方法。

### 1.6 `SignHookDispatcher` 的 11 个 `fire*`（活引用计数 = 0）

```
SignHookDispatcher.java:82:  public static void fireKillHooks(Player killer, LivingEntity killed)
SignHookDispatcher.java:94:  public static void fireHurtHooks(Player player, DamageSource source, float amount)
SignHookDispatcher.java:105: public static boolean fireBlessingTriggered(Player player)
SignHookDispatcher.java:119: public static void fireDiceBlessingTriggered(Player player)
SignHookDispatcher.java:128: public static void fireDiceBlessingEnded(Player player)
SignHookDispatcher.java:137: public static void fireAttackBrokenTarget(Player player, LivingEntity target)
SignHookDispatcher.java:146: public static void fireDiceJudgmentRoll(Player player, int dice)
SignHookDispatcher.java:155: public static void fireDiceRollResult(Player player, int dice)
SignHookDispatcher.java:164: public static void fireDodgeCounter(Player player, LivingEntity target)
SignHookDispatcher.java:173: public static void fireDragonRoar(Player attacker, LivingEntity victim)
SignHookDispatcher.java:182: public static void fireSplashCost(Player player)
```

每个 `fire*` 在本线 `fabric-1.20.1/src` 内**零调用者**（`DiceCombatEvents` 已改为逐点直调，见任务背景）。判定：**继续删除，不需要停止**（不存在除 `AlanaSignItem` 之外的生产调用者）。

---

## 2. 删除清单

| # | 文件 | 行数 | 内容 | 处置 |
|---|---|---|---|---|
| 1 | `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/SignHookDispatcher.java` | 189 | 注册式分发器：`fire*` 11 个 + `priorityOf` 排序 + Curios `findCurios` 遍历 + 10 处 `instanceof SignHooks.*` 分派 | **物理删除** |
| 2 | `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/SignHooks.java` | 73 | 钩子契约容器类 + 10 个嵌套接口：`BlessingTriggered` / `SplashBlessingTrigger` / `BiteBlessingTrigger` / `BlessingEnded` / `BrokenTargetAttack` / `DiceJudgmentRoll` / `DiceRollResult` / `DodgeCounter` / `DragonRoar` / `SplashCost` | **物理删除** |

删除方式与结果（`cmd.exe del`，未使用 `rm`，退出码 0）：

```
$ cd fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/sign
$ cmd.exe //c "del /f /q SignHookDispatcher.java SignHooks.java"
--- exit=0
$ ls -la SignHook*.java
ls: cannot access 'SignHook*.java': No such file or directory
(none)
```

**物理删除成功，无需用户终端补删。**

git 侧事实（`git status --porcelain -- .../item/sign`）：两文件均为 **未跟踪（`??`）**，即本次删除只回收工作树里的新增文件，**不产生 git 历史删除**，也不涉及他人已提交内容。

---

## 3. `AlanaSignItem.java` 改动

### 3.1 改动前（关键行原文）

```java
35: public class AlanaSignItem extends BaseSignItem implements SignHooks.BlessingTriggered {
...
57:     // === B1 注册式钩子分发入口（实例转发；静态方法保留兼容外部直调） ===
58:
59:     @Override
60:     public void handleBlessingTriggered(Player player) {
61:         onBlessingTriggered(player);
62:     }
63:
64:     public static void refreshBonus(Player player) {
```

### 3.2 改动后（关键行原文）

```java
35: public class AlanaSignItem extends BaseSignItem {
...
48:     /** 骰神赐福触发一次 ⇒ 累加 5 点攻击。 */
49:     public static void onBlessingTriggered(Player player) {
50:         if (player == null || player.level().isClientSide()) return;
51:         if (!isEquipped(player)) return;
52:         int bonus = SignAttachments.getAlanaPassiveBonus(player) + BONUS_PER_TRIGGER;
53:         SignAttachments.setAlanaPassiveBonus(player, bonus);
54:         refreshBonus(player);
55:     }
56:
57:     public static void refreshBonus(Player player) {
```

删除内容：`implements SignHooks.BlessingTriggered`（原 :35）、B1 分节注释 + `@Override public void handleBlessingTriggered(Player)` 转发方法整段（原 :57–:62 含其后空行）。
保留内容：`public static void onBlessingTriggered(Player)` 及其全部逻辑（`:49-55`，逐字未动）、`isEquipped`、`refreshBonus`、`clearBonus`、`handleUse`、`onUnequip`。

### 3.3 其余 `@Override` 复核

`grep -n "@Override" AlanaSignItem.java` 现为 2 处：

```
70: @Override   → handleUse(Level, Player, ItemStack)      // 覆写 BaseSignItem
86: @Override   → onUnequip(SlotContext, ItemStack, ItemStack) // 覆写 BaseSignItem
```

两者覆写的都是 `BaseSignItem` 的既有成员（本次未触碰 `BaseSignItem.java`），删掉 `SignHooks.BlessingTriggered` 对其**无影响**；编译实证见第 6 节。

### 3.4 编码 / 行尾

```
改动前：4918 B，CRLF 100 / LF 100，无 BOM
改动后：4653 B，CRLF 93 / LF 93，无 BOM
```

行尾与编码保持原样（纯 CRLF、UTF-8 无 BOM）。

---

## 4. `AstralDiceMod.java` 的 LoaderBus 登记确认

```
AstralDiceMod.java:205:         LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.sign.AlanaSignItem.class);
```

`LoaderBus`（`fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/platform/event/LoaderBus.java`）语义核实：

```
112: public void register(Object target)   → 委托 :124
124: public void register(Class<?> target)
137:         registeredOwners.add(owner);
138:         for (Method m : owner.getDeclaredMethods()) {
139:             SubscribeEvent ann = m.getAnnotation(SubscribeEvent.class);
```

结论：`register(Class<?>)` **只扫 `@SubscribeEvent` 注解方法**并登记 owner（审计用），**不读取被登记类实现的接口**。`AlanaSignItem` 全文无 `@SubscribeEvent` 方法 ⇒ 该行登记在删除接口后语义不变（登记成功、处理函数计数为 0），**无需改动，且不构成 SignHooks 的活引用**。

---

## 5. 验收判据逐条核对（改动后实测原文）

### 5.1 `grep -rn "SignHookDispatcher\|SignHooks" fabric-1.20.1/src`

```
fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/combat/DiceCombatEvents.java:594:            // 阿兰娜立牌:触发骰神赐福 → 被动攻击 +5(等价主线的 SignHookDispatcher.fireBlessingTriggered
# rc=0
```

⇒ **只剩 1 行注释，零代码命中**（判据「只剩注释 / 零命中」满足）。

### 5.2 `grep -rn "SignHooks" fabric-1.20.1/src`

```
# rc=1   （零命中）
```

### 5.3 物理删除确认

```
$ ls fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/ | grep -i signhook
(none)
```

### 5.4 `onBlessingTriggered` 静态方法仍在、且 `DiceCombatEvents` 有调用

```
fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/combat/DiceCombatEvents.java:593: fenSplashArmed = com.merlinkitsune.astral_dice.item.sign.FenSignItem.onBlessingTriggered(player);
fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/combat/DiceCombatEvents.java:596: com.merlinkitsune.astral_dice.item.sign.AlanaSignItem.onBlessingTriggered(player);
fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/combat/DiceCombatEvents.java:601: com.merlinkitsune.astral_dice.item.HealingManager.onBlessingTriggered(player);
fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/AlanaSignItem.java:49:     public static void onBlessingTriggered(Player player) {
```

⇒ 静态方法存在（`AlanaSignItem.java:49`），调用点存在（`DiceCombatEvents.java:596`，任务指定「不要动那段」已原样保留）。

---

## 6. 编译验证（命令与原文）

命令（仓库根）：

```
cmd.exe //c "gradlew.bat :fabric-1.20.1:compileJava --console=plain"
```

### 6.1 第一次运行 — FAILED（**与本次改动无关**，为并行落盘竞态）

```
> Task :fabric-1.20.1:compileJava
F:\MCProject\astral_dice_multiloader\fabric-1.20.1\src\main\java\com\merlinkitsune\astral_dice\network\ModNetwork.java:500: 错误: 找不到符号
                    com.merlinkitsune.astral_dice.client.TargetSelectionClient.closeFromServer(msg.token));
                                                                              ^
  符号:   方法 closeFromServer(int)
  位置: 类 TargetSelectionClient
注: 某些输入文件使用或覆盖了已过时的 API。
注: 有关详细信息, 请使用 -Xlint:deprecation 重新编译。
1 个错误

> Task :fabric-1.20.1:compileJava FAILED
BUILD FAILED in 4s
```

归因（**不是本批改动引起**）：

1. javac 本次扫描全部源文件后**只报 1 个错误**，且该错误与 `item/sign/**` 无关 —— 删掉的类若被使用，必然出现 `找不到符号: 类 SignHooks / SignHookDispatcher`，实际没有；
2. 失败点是缺陷 C-2 的并行修复：`ModNetwork.java:500`（git 状态 ` M`，未提交）调用 `TargetSelectionClient.closeFromServer(int)`，而该方法当时尚未落盘；取证时该方法已存在于 `TargetSelectionClient.java:382`（`public static void closeFromServer(int closeToken)`，注释自述「2026-11-01 补齐（缺陷 C-2）」）。即编译发生在**另一个并行任务的两次写盘之间**。

### 6.2 第二次运行（源树稳定后）— SUCCESS

```
Reusing configuration cache.

> Task :fabric-1.20.1:compileJava
注: 某些输入文件使用或覆盖了已过时的 API。
注: 有关详细信息, 请使用 -Xlint:deprecation 重新编译。

BUILD SUCCESSFUL in 2s
1 actionable task: 1 executed
Configuration cache entry reused.
```

`1 actionable task: 1 executed` ⇒ `compileJava` **实际执行**（非 `UP-TO-DATE`），覆盖本次删除 + `AlanaSignItem` 改动后的源树。

### 6.3 第三次运行（复核）— SUCCESS

```
Reusing configuration cache.
> Task :fabric-1.20.1:compileJava

BUILD SUCCESSFUL in 1s
1 actionable task: 1 executed
Configuration cache entry reused.
```

**结论：`:fabric-1.20.1:compileJava` 通过（BUILD SUCCESSFUL，两次连续复核）。** 首次 FAILED 已定位为并行 C-2 修复的写盘竞态，与本批删改无因果关系。

---

## 7. 无法判定项 / 遗留

1. **`DiceCombatEvents.java:594-595` 两行注释仍指向已删除的符号**（`SignHookDispatcher.fireBlessingTriggered` / `AlanaSignItem#handleBlessingTriggered`）。该文件**不在本任务允许改动的文件清单内**，且任务明确「不要动那段（:593 后的显式调用）」，故未修改。**纯注释、非 `{@link}` 形式，无编译影响**；建议后续批次或主线合并时顺便改文案。
2. **运行期未验证**：按硬约束不启动游戏，故「`AlanaSignItem` 被动在实机仍 +5 攻击」只做了静态与编译层确认，未做游戏内断言。
3. **未做跨线对比**：`neoforge-1.21.1` / `forge-1.20.1` / `neoforge-26.1.2` 三线的 `SignHookDispatcher` / `SignHooks` 是**活路径**（主线用注册式分发），本批**完全未触碰**（`glob **/SignHook*.java` 确认三条线文件仍在）。
4. **并行任务干扰记录**：编译期间观察到其他并行任务在写 `ModNetwork.java` / `TargetSelectionClient.java`（缺陷 C-2）；本次执行未修改这两个文件，也未替其收尾。

---

## 8. 结论（一句话）

删除 `fabric-1.20.1` 线已成死代码的 `item/sign/SignHookDispatcher.java`（189 行，11 个零调用 `fire*`）与 `item/sign/SignHooks.java`（73 行，10 个嵌套接口），并摘掉 `AlanaSignItem` 上唯一残留的 `implements SignHooks.BlessingTriggered` 与实例转发方法（静态 `onBlessingTriggered` 及其调用点原样保留）；两个文件**物理删除成功**（`cmd del` 返回 0，目录内已无 `SignHook*`），`:fabric-1.20.1:compileJava` **BUILD SUCCESSFUL**（两次复核）。
