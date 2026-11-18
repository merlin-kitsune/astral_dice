# B4 补遗：fabric-1.20.1 `item/sign/BaseSignItem.java` 对齐 forge-1.20.1

## 范围与结论
- 基准：`forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/BaseSignItem.java`（577 行）
- 目标：`fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/BaseSignItem.java`（660 → 721 行，+61）
- 只改这一个文件；未执行 gradle、未启动游戏、未 git commit/push。
- 三项结果：① `setSignActiveMaxCooldownBase` **完整落地**（钩子 1 处 + 写入点 3 处）；② `onSignActiveEnded` **零改码**（被调方法在 fabric 不存在，按纪律不造假实现，补桥方案见 §2）；③ `AP_SIGN_SOUND` 机器行 **完整落地**。
- 以下 fabric 片段均为 `read` 回读**改后**文件原文，行号即改后行号。
## 1. setSignActiveMaxCooldownBase（主动技能最大冷却基数）
### 1.1 新增虚方法钩子 `activeCooldownBaseTicksNoBattery`
forge 侧：`forge-1.20.1/.../item/sign/BaseSignItem.java:417-428`（javadoc）+ `:429-432`（实现），语义真身在 `forge-1.20.1/.../item/ChargeManager.java:157-159`：
```java
protected int activeCooldownBaseTicksNoBattery(Player player) {
    return (int) com.merlinkitsune.astral_dice.item.ChargeManager.signCooldownTicksBase(
            player, com.merlinkitsune.starengine.component.GameplayConstants.SIGN_ACTIVE_COOLDOWN_TICKS);
}
```
fabric 落点：`fabric-1.20.1/.../item/sign/BaseSignItem.java:437-467`（插在既有 `activeCooldownBaseTicks` L433-435 之后）：
```java
    protected int activeCooldownBaseTicksNoBattery(Player player) {
        long baseTicks = com.merlinkitsune.starengine.component.GameplayConstants.SIGN_ACTIVE_COOLDOWN_TICKS;
        if (baseTicks <= 1
                || !com.merlinkitsune.astral_dice.item.ChargeManager.hasCharge(player)) {
            return (int) baseTicks;
        }
        return (int) Math.min(baseTicks,
                com.merlinkitsune.starengine.component.GameplayConstants.CHARGE_SIGN_COOLDOWN_CAP_SECONDS * 20L);
    }
```
**为什么不能照抄**：fabric `item/ChargeManager.java` 没有 `signCooldownTicksBase`（只在 `:124-131` 的 `signCooldownTicks` 里先封顶、再内联折电池）。平台垫片 = 就地内联复刻 `capByCharge`（`fabric-1.20.1/.../item/ChargeManager.java:141-144`：`if (baseTicks <= 1 || !hasCharge(player)) return baseTicks; return Math.min(baseTicks, capSeconds * 20L);`），与 forge `signCooldownTicksBase` **逐字等价**：只封顶、不折电池、不含诡异骰子。未新增 import、未新建键（复用 `fabric-1.20.1/.../component/SignAttachments.java:43-56` 的 `SIGN_ACTIVE_MAX_COOLDOWN_BASE`，键名 `sign_active_max_cooldown_base`，Codec.LONG）。
### 1.2 `performSkill` 两个写入点（锁分支 / else 分支）
forge 侧：`BaseSignItem.java:198-202` 与 `:206-210`，各一行 `ModAttachments.setSignActiveMaxCooldownBase(player, sign.activeCooldownBaseTicksNoBattery(player));`，并带三行注释（「同处记录「未折电池」基准…Dorothy 2400 被写成 3200/3600」）。
fabric 落点：`fabric-1.20.1/.../item/sign/BaseSignItem.java:188-192`（锁分支）、`:197-201`（else 分支，同构）：
```java
                ModAttachments.setSignActiveMaxCooldown(player, signCooldownTicks);
                // 同处记录「未折电池」基准(命运指引卡减半用;电池只作用于立牌主动冷却本身)。
                // ⚠️ 取的是**该立牌自身**的基础值(虚方法 no-battery 版),不能用全局常量 —— 否则
                // Dorothy/Parunan/Sherry 等自定义基准的立牌会被写错(如 Dorothy 2400 被写成 3200/3600)。
                com.merlinkitsune.astral_dice.component.SignAttachments.setSignActiveMaxCooldownBase(
                        player, sign.activeCooldownBaseTicksNoBattery(player));
```
**为什么不能照抄**：fabric 该键在 `component/SignAttachments`（forge 在 `ModAttachments`）⇒ 用全限定名调用，与本文件既有风格一致，不动 import 块。
### 1.3 锁定态寿终写入点（forge 在 SignActiveLockMachine，fabric 内联在本文件）
forge 侧：`forge-1.20.1/.../item/sign/SignActiveLockMachine.java:103-113`（`baseNoBattery` / `effectiveBase` / `setSignActiveMaxCooldownBase`）。
fabric 落点：`fabric-1.20.1/.../item/sign/BaseSignItem.java:508-526` 的 `endLockAndStartCooldown`：
```java
        long baseNoBattery = lockSign != null
                ? lockSign.activeCooldownBaseTicksNoBattery(player)
                : com.merlinkitsune.astral_dice.component.SignAttachments.getSignActiveMaxCooldownBase(player);
        long effectiveBase = Math.max(0L, baseNoBattery - pool);
        ModAttachments.setSignActiveCooldownEnd(player, now + effective);
        ModAttachments.setSignActiveMaxCooldown(player, effective);
        com.merlinkitsune.astral_dice.component.SignAttachments.setSignActiveMaxCooldownBase(player, effectiveBase);
```
**为什么不能照抄**：fabric 没有 `SignActiveLockMachine` 类（锁定态状态机整段内联在 BaseSignItem），只能搬「算式」，落点为本文件 `endLockAndStartCooldown`；`lockSign` 用本文件既有 `lockSignItem(getSignActiveLockSignId(player))`（`:510` 调用，定义在 `:362-367` / `:325-328`）。**有意不搬** forge `:99` 的 `skipCooldownAfterLock()` 分支：本线无该钩子，本线蛟龙走 `forcedActiveCooldownTicks` / `forcedCooldownUntil`（见 `fabric-1.20.1/.../item/sign/MamushiSignItem.java:267` 注释），照搬会引入本线不存在的语义。
## 2. WeirdDiceHandler.onSignActiveEnded —— 未落地（前置能力缺失，非本文件可解）
forge 侧：`forge-1.20.1/.../event/WeirdDiceHandler.java:28`（`HEALTH_ON_ACTIVE_END = 5`）+ `:89-93`：
```java
public static void onSignActiveEnded(Player player) {
    if (player == null || player.level().isClientSide) return;
    if (!hasWeirdDice(player)) return;
    player.heal(HEALTH_ON_ACTIVE_END);
}
```
forge 调用点 3 处（互斥）：`BaseSignItem.java:217-219`（瞬时型，`if (!enteredLock)`）、`:294-297`（`resumeGatedActiveSkill` 尾，`if (getSignActiveLockSignId(player).isEmpty())`）、`SignActiveLockMachine.java:124`（锁定寿终）。
fabric 现状：`fabric-1.20.1/.../event/WeirdDiceHandler.java` 全文 62 行，止于 `rollD6()`，**没有**该常量与该方法；全仓 `grep -rn "onSignActiveEnded\|HEALTH_ON" fabric-1.20.1/src` = 0 命中。
结论：callee 不存在 ⇒ 本文件加调用只会换来 `cannot find symbol`；按纪律（不造假实现、不扩大改动面）本项在本文件 **零改动**，改后 `grep -c onSignActiveEnded BaseSignItem.java` = 0。
**补桥层（归属 `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/WeirdDiceHandler.java`）**，追加约 8 行：
```java
    /** 主动技能结束后恢复的生命值(与 forge 同值,用户裁决 2026-10-21) */
    public static final int HEALTH_ON_ACTIVE_END = 5;

    public static void onSignActiveEnded(Player player) {
        if (player == null || player.level().isClientSide) return;
        if (!hasWeirdDice(player)) return;
        player.heal(HEALTH_ON_ACTIVE_END);
    }
```
补桥后本文件要改 3 处（**第一处必须先做结构改造**）：fabric `performSkill` 目前把有副作用的 `sign.startActiveLockOnUse(player, now)` 直接写在 `if` 条件里（`BaseSignItem.java:183`，该方法会写 `sign_active_lock_*` 键，绝不可为取布尔值二次调用）⇒ 照 forge `:193` 提为 `boolean enteredLock = sign.startActiveLockOnUse(player, now); if (enteredLock) {`，再在冷却块尾（今 `:204` 之后、`:205` 的 `}` 之前）加 `if (!enteredLock) { com.merlinkitsune.astral_dice.event.WeirdDiceHandler.onSignActiveEnded(player); }`；第二处 `resumeGatedActiveSkill` 尾（今 `:293` 之后、`:294` 的 `}` 之前）；第三处 `endLockAndStartCooldown` 的 `:528` 之前（与 forge `SignActiveLockMachine.java:124` 同位）。
## 3. `AP_SIGN_SOUND` 机器行
forge 侧：`forge-1.20.1/.../item/sign/BaseSignItem.java:249-259`，机器行在 `:253-255`。两线真实格式（唯一实现，`neoforge-1.21.1/.../BaseSignItem.java:235` 一致）：
`AP_SIGN_SOUND: skill_use:player=<玩家名>:pos=<x>,<y>,<z>`
（任务书里「形如 `AP_SIGN_SOUND:<sign_id>`」是简写，两线真实实现均不带 sign_id，以本行为准。）
fabric 落点：`fabric-1.20.1/.../item/sign/BaseSignItem.java:236-242`（`playSkillUseSound` 内，`SoundPlayback.playAt` 之前）：
```java
        // 机器行：主动技能音效「按下即时播放」的唯一打点（2026-10-11 用户裁决）。
        // 实机判据：按技能键 ⇒ 本行出现一次；随后在事件选择界面点确认 ⇒ **不再**新增本行。
        // 格式与 forge / neoforge 两线逐字一致（测试台断言锚点，勿改）：
        //   AP_SIGN_SOUND: skill_use:player=<玩家名>:pos=<x>,<y>,<z>
        LOGGER.info("AP_SIGN_SOUND: skill_use:player={}:pos={},{},{}",
                player.getName().getString(),
                (int) player.getX(), (int) player.getY(), (int) player.getZ());
```
**为什么不能照抄**：只有访问级差异（forge `public static`，fabric `protected static`，两处调用点 `:148` / `:155` 均在同类内）——按约束**不改签名**；`LOGGER` 本文件已存在（`:26`，`:17-18` 已 import slf4j），未新增 import。打点面与 forge 同构：`:148`（选择器进入）↔ forge `:156`、`:155`（handleUse SUCCESS）↔ forge `:165`，故机器行覆盖的触发面一致。
## 4. 未做 / 做不了（逐条 + 归属）
1. **`WeirdDiceHandler.onSignActiveEnded`**（§2）：callee 缺失，本文件零改码；归属 `event/WeirdDiceHandler.java`，补桥代码与 3 个调用点位置已给全。
2. **`ChargeManager.signCooldownTicksBase`**（forge `ChargeManager.java:157-159`）本线不存在 ⇒ 1.1 才内联复刻；归属 `item/ChargeManager.java`（真修法：把 `signCooldownTicks` 拆成 base + 折电池两步），修好后 1.1 改为一行委托。
3. **命运指引卡读者缺失**：fabric `item/card/FateGuidanceCardItem.java:64` 读 `ModAttachments.getSignActiveMaxCooldown`（折电池后的值），forge `:75` 读 `getSignActiveMaxCooldownBase` ⇒ 本次写入在 fabric **暂无读者**（当前行为零变化，但减半收益也还拿不到）；归属 `item/card/FateGuidanceCardItem.java`。
4. **立牌侧 no-battery 覆写未搬**：forge 三处 `DorothySignItem.java:65` / `ParunanSignItem.java:97` / `SherrySignItem.java:230`；fabric 现只有 `activeCooldownBaseTicks`（`DorothySignItem.java:90` / `SherrySignItem.java:220`），**Parunan 连该覆写都没有**。后果：Dorothy 自定义基准 2400 会被按缺省口径写成 3200/3600（正合本文件 `:189-190` 注释警告）。归属各自立牌文件；`ParunanSignItem.java` 是他人未提交改动（git M），按红线不动。
5. **另两处 forge 写入点不在本文件**：`item/chip/CurrentCoreChipItem.java:109`（清零）、`item/sign/LuluSignItem.java:131`；归属各自文件，建议单独批次。
6. **本线缺省冷却口径仍比 forge 旧**：fabric `event/WeirdDiceHandler.java:36-43` 的 `signCooldownTicks` 仍做「诡异骰子 −50%」，forge 已于 2026-10-21 删除该减免 ⇒ 两线缺省 base 不同；非本批，归属 `event/WeirdDiceHandler.java` + `item/ChargeManager.java`。
## 5. 自检读数（改后实测）
- `grep -c "^import net.minecraftforge" BaseSignItem.java` = **0**；`^import net.neoforged` = **0**；`^import top.theillusivec4.curios` = **0**；`fromNamespaceAndPath` = **0**（本次未引入任何 `ResourceLocation` 用法）
- CRLF：721 个 `\r\n`、0 个裸 LF、无 BOM（按字节复核）；行数 660 → **721**（+61，纯插入，无删除/重排），`git diff --stat` = `1 file changed, 61 insertions(+)`。
- 未编译（本会话禁 gradle）：外部符号已按本线实物核对 —— `ChargeManager.hasCharge(Player)`（`ChargeManager.java:29` public static）、`GameplayConstants.SIGN_ACTIVE_COOLDOWN_TICKS` / `CHARGE_SIGN_COOLDOWN_CAP_SECONDS`（`ChargeManager.java:125` / `:141-143` 已在用）、`SignAttachments.get/setSignActiveMaxCooldownBase`（`SignAttachments.java:50-56`）、`lockSignItem` / `getSignActiveLockSignId`（本文件 `:362` / `:325`，同类静态）。
## 6. 待裁决
- §2 的补桥是否本批直接落 `event/WeirdDiceHandler.java`（+8 行）并在本文件加 3 处调用（含 `enteredLock` 结构改造）。建议单独小批次：跨文件，且会激活本线此前完全不存在的回血路径，需要实机/断言验证 5 点 HP 是否与既有治愈叠加。
- §4-3（命运指引卡读者）与 §4-4（Dorothy/Parunan/Sherry 覆写）建议成对处理：只补写入不补读者无可观测收益，只补读者不补覆写会把 Dorothy 的基准算错。