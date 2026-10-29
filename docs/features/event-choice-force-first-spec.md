# 事件二选一界面：退出即强制触发首个事件 + 事件叠加口径统一

> 状态：设计已定稿、待开工（2026-10-29，用户 m00076 裁决「按 A 执行，B 登记为备选；A 若出现明显漏洞则改 B」）
> 适用范围：P0 双线 `neoforge-1.21.1`（基准）/ `forge-1.20.1`（移植）。P1 两线（`neoforge-26.1.2` / `fabric-1.20.1`）冻结期间不动。
> 关联：`docs/specs/event-system-rewrite.md`（事件体系口径）、`docs/features/card-choice-screen-spec.md`（Menu 路线先例）

## 0 一句话

大侦探立牌主动技能弹出的「事件二选一」界面，从「可以无代价 ESC 退出」改为**任何形式的退出都强制触发第一个事件**；ESC 改两段式（先警告、再退出）；同一事件重复触发统一为**覆盖旧效果并重新计时**。

## 1 需求

| # | 用户要求（m00070 / m00076） | 可观测判据 |
|---|---|---|
| R1 | 处于二选一界面时，**任何形式**的退出（ESC / 换维度 / 断线 / 服务端强制关屏）都强制触发第一个事件 | 退出后第一个事件的效果与 `AP_EVENT_FIRE: id=<事件id>` 日志出现，且该行带 `src=ACTIVE_SKILL`（`src` 用来区分「二选一界面派发」与 `ITEM_USE` / `INVESTIGATION_KILL` / `CUSTOM`；确认派发与强制派发同为 `ACTIVE_SKILL`，两者再用 `AP_EVENT_CHOICE: confirm:*` 与 `AP_EVENT_CHOICE: force:*:reason=*` 区分）—— 原判据写的那个机器行名在源码里**并不存在**（实际机读行只有 `AP_EVENT_FIRE`） |
| R2 | 按 ESC 先发一次 actionbar 警告（「退出会强制触发第一个事件，再次按下 Esc 退出」），第二次 ESC 才退出 | 第一次 ESC：界面仍在 + actionbar 出现；第二次：界面关闭 + 首个事件生效 |
| R3 | 同一带计时器事件再次触发 ⇒ 覆盖旧效果并重新计时；不带计时器的事件 ⇒ 再触发一遍 | 计时器 `end` 改写为新的 `now + ticks`；旧批属性修饰符被撤销；即时事件二次结算 |

> **R1 的显式例外（待用户确认）**：`cancelFor(...)` 这条出口 —— 事件卡动画开播时把选择界面顶掉 —— **不**触发强制结算，尽管 R1 的字面判据是「任何形式的退出」。理由：该时刻已经在播另一条事件卡动画，若再强制触发第一个事件会**双触发**（两条事件的卡动画与结算同时进行）。本例外**本次未改代码**，只登记待裁决；若用户要求取消该例外，需先给出「双触发如何收敛」的口径。

## 2 用户已裁定口径（不可擅改）

| # | 裁定（2026-10-29） | 对设计的影响 |
|---|---|---|
| D1 | 「任何形式的退出」包含换维度 / 断线 / 服务端强制关屏，不限于 ESC（m00070） | 服务端必须有维度钩子 + 关屏兜底，不能只靠客户端发包 |
| D2 | 出口方案走 **A**：客户端 exit 意图 + 服务端权威 `forceFirst` + 维度钩子 | 不做 Menu/Container 改造 |
| D3 | 方案 **B**（改造成 Menu/Container）登记为备选，**仅当 A 暴露明显漏洞**时启用（m00076） | 见 §6 |
| D4 | 叠加按「覆盖 + 重置」实现，不引入层数 | 不做 amplifier 累加、不做独立实例栈 |

## 3 现状取证（`neoforge-1.21.1`，`forge-1.20.1` 同构，差异见下）

### 3.1 链路

主动技能键 → `SignActivatePayload`（C2S）→ `BaseSignItem.performSkillForCurio` → `performSkill` → `FannySignItem.handleUse:42` → `EventChoiceManager.start:95` → `EventChoiceStartPayload` → `EventChoiceClientHandler.open:33-37` → `EventChoiceScreen`（**纯 Screen，无 Menu/Container/方块/实体**）。

- 客户端选项：`client/EventChoiceScreen.java:71-72`（`ResourceLocation[] options` + `int[] durations`），`:91-92` 填 `{first, second}`。
- 服务端选项：`event/EventChoiceManager.java:110` `AstralEventRegistry.publicPool(player)`；`:116-117` **两次独立随机抽取** ⇒ 「第一个事件」= 第一次抽到的 `session.firstEvent`。
- 确认：`EventChoiceScreen:217` → `EventChoiceManager.confirm:132` → `:157` `AstralEventDispatcher.trigger(..., ACTIVE_SKILL)`。

### 3.2 退出路径现状（关键：今天 ESC 是「纯取消、零触发」）

| 退出方式 | 客户端行为 | 服务端行为 | 是否触发首个事件 |
|---|---|---|---|
| ESC | `shouldCloseOnEsc` 恒 true（`EventChoiceScreen:232-234`）→ `onClose:245-250` 发 Cancel 包 | `EventChoiceManager.cancel:188-198` 只清会话 + 发 Close | **否** |
| 死亡 | 原版 `setScreen(DeathScreen)` 顶掉，不发包 | `LivingDeathEvent` → `forceFirst("death")` `:247-252` | 是（已实现） |
| 登出 | 原版 `setScreen(DisconnectedScreen)`，不发包 | `PlayerLoggedOutEvent` → `forceFirst("logout")` `:256-260` | 是（已实现） |
| 超时 | 界面仍开着 | `tick` `:227-228` → `forceFirst("timeout")` | 是（15 s 后，已实现） |
| 换维度 | `setScreen(DimensionTransitionScreen)` 顶掉，**不发包** | 无钩子，会话残留 | 只在 15 s 超时后 |
| 服务端强制关屏 | `closeIfToken:47-52` → `setScreen(null)`，只回调 `removed()` | 服务端自己发起 | 依发起方而定 |
| 客户端崩溃 | 无 | 无（登出钩子兜底） | login 恢复后由登出路径触发 |

结论：`onClose` 内没有任何强制触发；「关屏即触发」目前只在死亡 / 登出 / 超时成立，换维度要等 15 s。

### 3.3 计时器与叠加现状

- `EventTimerStore.start`（`:44-46` 2 参 → `:53-62` 4 参）：`ON_EXPIRE.put` / `EXPIRY.put` 都是**覆盖写**，`end = now + Math.max(1, ticks)` ⇒ 「重新计时」已天然成立。
- 到期执行点唯一：`:74-97`，**先 `remove` 钩子再 `accept`**（`:82-89`）。
- 分发器固定形状：`AstralEventDispatcher.java:132` 先 `start(3 参, onExpire=null)`，`:136` 再 `effect().apply(...)`。**先登记计时器、后执行效果**是 §4.4 回放块安全的因果前提：`start(...)` 最开头的回放块回放的是**旧周期**的 `onExpire`（撤销旧状态），此刻新状态尚未写入，因此回放块不会撤掉新写入的状态。
- 6 条计时事件（`defaultDurationTicks > 0`）的到期回调**全部是纯撤销且幂等**（6/6，见 `temp/analyst_events_findings.md` 与本次复核）：`red_heat_warning`（撤攻击 +5，`EventEffects:103`→`:64-77`）、`swift_stride`（`null`，`:111`）、`storm_approaching`（撤本批怪 +2/+5，`:136-141` → `:85-88`）、`shop_bonanza`（`null`，靠附件时间戳 `:428-433`）、`problem_delivery`（解除治疗封锁，`:505` → `:596-609`）、`investigation_stage`（`null`，NOOP 效果）。
- **缺陷**：`ON_EXPIRE.put` 直接覆盖会丢弃上一轮回调 ⇒ `storm_approaching` 重复触发后，**上一批怪的 +2 攻 / +5 甲永不撤销**（同 key 的 `AttributeModifier` 永久生效）。
- 即时事件：`AstralEventDispatcher:136-138` 无幂等判断 ⇒ 「再触发一遍」已天然成立，无需改。
- MobEffect 层：`addEffect` 在同效果上取「较长时长」，而事件施加的是**满时长**，故重复触发即等效重置满；`PlayerEffectProtection`/`EffectTimerGuard` 取 max 只影响保护窗口，不影响效果本身。

## 4 设计

### 4.1 出口：统一 exit 意图（方案 A）

1. **客户端**（`client/EventChoiceScreen.java`）：
   - 新增字段 `escalationWarned`、`exitSent`、`closedByServer`。
   - `shouldCloseOnEsc()` 改返回 **false**（原版不再直接关窗，ESC 全部交给 `keyPressed`）。
   - 新增 `keyPressed(int keyCode, int scanCode, int modifiers)`：`keyCode == GLFW.GLFW_KEY_ESCAPE` 时——第一次：置 `escalationWarned = true`，`minecraft.player.displayClientMessage(Component.translatable("message.astral_dice.event_choice.esc_warning"), true)`（actionbar），返回 true；第二次：`onClose()`，返回 true。
   - `onClose()`：`if (!confirmed) sendExit(); super.onClose();`
   - 新增 `removed()` 覆写：`if (!confirmed && !exitSent && !closedByServer) sendExit();` —— 覆盖「被别的 Screen 顶掉」（换维度 / 死亡 / 断线 / 其它模组抢屏）。
   - `sendExit()`：`if (confirmed || exitSent) return; exitSent = true;` 发既有 Cancel 包（**不新增包类**，见 4.3）。
2. **服务端**（`event/EventChoiceManager.java`）：把 `cancel(player, token, reason)` 的语义从「丢弃会话」改为「**退出即结算**」：仍先做 token 与 `resolving` 幂等校验（`:187` 现成），然后走 `forceFirst(player, "exit")`（`:161-173` 现成）。`cancelFor(...)`（`:203-209`，服务端内部关屏，如确认后的动画锁）**保持不触发** —— 这是 R1 的**显式例外**（**待用户确认**）：同一时刻已经在播另一条事件卡动画，再强制触发第一个事件会双触发。
3. **换维度**：新增 `PlayerChangedDimensionEvent` 监听 → `forceFirstIfPending(player, "dimension")`；与客户端 `removed()` 双保险，靠 `forceFirst` 的幂等（会话已移除即 no-op）去重。
4. **死亡 / 登出 / 超时**：沿用现有 `forceFirst`，不改。

### 4.2 ESC 两段式

必须在 `keyPressed` 里做，不能靠 `shouldCloseOnEsc` 返回 false —— 原版 `Screen#keyPressed` 只在 `shouldCloseOnEsc()` 为 true 时调 `onClose()`，为 false 时**既不关窗也不给任何反馈**。

### 4.3 为什么复用 Cancel 包而不是新增包

客户端到服务端只需要表达「没选就退出了」这一件事，现有 `EventChoiceCancelPayload` 的语义位正好空出来；复用可省掉两线的包注册与 5 个包的一致性回归。包类名保留（改名会扩大 diff），在其 Javadoc 注明新语义「退出即强制结算首个事件」。

### 4.4 叠加：revert-at-start（等价于 ①②合流）

`EventTimerStore.start` 4 参体最开头（**无条件**，不在 `if (onExpire != null)` 内）：

```java
Map<ResourceLocation, Consumer<Player>> hooks = ON_EXPIRE.get(player.getUUID());
if (hooks != null) {
    Consumer<Player> old = hooks.remove(eventId);
    if (hooks.isEmpty()) ON_EXPIRE.remove(player.getUUID());
    if (old != null) {
        try { old.accept(player); } catch (Exception ex) { LOGGER.warn("AP_EVENT_TIMER: revert_failed:{}:{}", eventId, ex.toString()); }
    }
}
```

- 位置**必须在 `start()` 最开头**：分发器在 `effect.apply` 之前就先调 **3 参** `start(target, event.id(), event.defaultDurationTicks())`（neo `AstralEventDispatcher.java:132` 委托 `EventTimerStore.java:47-48` 的 2 参重载、后者再补 `onExpire = null` 转 4 参；`effect.apply(...)` 在 `:136`。forge 同构：`start` 在 `:130`、`apply` 在 `:134`），回放正好落在「旧周期收尾、新周期尚未施加」；若写在 4 参分支内、由效果函数触发，则 `red_heat_warning`（`:102` 先加 +5 再 `:103` 登记）与 `problem_delivery`（`:504` 先封锁再 `:505`）会出现「新状态刚写上就被旧回调撤掉」。
- 同时消费旧钩子（`remove` 后再 `accept`），与 `:82-89` 的到期语义保持一致，避免二次触发。
- 该改动**顺带修掉** `storm_approaching` 旧批怪属性残留。
- MobEffect 与 `shop_bonanza` 时间戳本就是覆盖写，不改。

### 4.5 幂等与竞态

- 服务端 `forceFirst` 与新的 exit 处理都必须以「会话存在且 `resolving == 0`」为前提，重复调用 no-op。
- 确认路径（`confirm`）在置 `resolving` 后同 tick `remove` 会话；此后任何 exit/维度包都会落空。
- 客户端 `closeIfToken` 必须**校验当前 Screen 类型**（`instanceof EventChoiceScreen`）再 `setScreen(null)`，否则换维度时会把 `ReceivingLevelScreen` 关掉；并通过 `closedByServer` 标志抑制 `removed()` 里的 exit 发送。

### 4.6 文案（三语，六处）

键 `message.astral_dice.event_choice.esc_warning`：

- zh_cn：`退出会强制触发第一个事件，再次按下 Esc 退出`
- en_us：`Leaving will force-trigger the first event. Press Esc again to leave.`
- ja_jp：`終了すると最初のイベントが強制発動します。もう一度 Esc で終了します。`

## 5 判据

静态：

1. `.\gradlew.bat :neoforge-1.21.1:classes` 与 `:neoforge-1.21.1:jar` 通过（forge 线移植后同）。
2. `grep -c "shouldCloseOnEsc" ` 在两线 `EventChoiceScreen` 均为 1 处且返回 false；`keyPressed` 覆写存在。
3. 三语 lang 三键齐备、`git ls-files --eol` 无 CRLF 混入。
4. `EventTimerStore.start` 头部回放块存在且不在 `onExpire != null` 内。
5. `python scripts/verify/verify_event_timer_stack.py` 四条断言全 PASS —— 其中断言 04 补掉假绿空间：派发器里无回调的 `EventTimerStore.start(...)` 行号必须**小于** `effect().apply(` 行号（回放不会撤掉新写入的状态，全靠这个先后顺序）。

实机（须用户点名下达，收尾 `mt.ps1 --phase stop --purge-saves` + `MT_STOP: OK`）：

1. 开界面 → 按一次 ESC：界面仍在 + actionbar 出现；日志无 `forceFirst`。
2. 再按 ESC：界面关闭 + `AP_EVENT_FIRE: id=<session.firstEvent>` 出现且带 `src=ACTIVE_SKILL`（若首选项此刻已不可派发，实际派发的是 fallback 后的事件：以 `AP_EVENT_CHOICE: force:*:event=` 记下的 `forced` 为准）。
3. 开界面 → 换维度（或传送）：1 秒内触发首个事件，无 15 s 延迟。
4. 开界面 → 断线重连：首个事件已触发一次，且**只触发一次**。
5. 正常确认选择：不再额外触发首个事件（`confirmed` 闸门）。
6. 同一计时事件连续触发两次（`/astral_event` 或探针）：第二次 `AP_EVENT_TIMER: start` 的 `end` = 新 now + ticks；`storm_approaching` 旧批怪攻击/护甲回到基线。

## 6 明确不做（本轮边界）

- **不做** Menu/Container 化改造（方案 B）。B 的价值是服务端天然权威（客户端崩溃也能在容器移除时立即结算），代价是注册 `MenuType`、重写开屏与发卡链路、Screen 改由 menu 驱动；卡牌选卡线 `docs/features/card-choice-screen-spec.md:101-105` 已有可照抄的成熟实现，真需要时可照该先例做。**后续若 A 暴露明显漏洞（例如客户端被强制杀进程后首个事件不结算而玩家又能重开界面刷掉），直接改 B**，不在 A 上继续打补丁。
- **不做**事件层数 / amplifier 累加（D4）。
- **不做** P1 两线同步（冻结期，正式版后再议）。
- **不改** `GlobalEventGate` 的 ACTIVE_SKILL 豁免口径（2026-10-29 D1 裁决）。
- **不修** `EventTimerStore.clear` 与 `ON_EXPIRE` 的语义不对称、登出清理缺失（无调用方，登记为已知边界）。

## 7 已知边界与风险

1. 客户端进程被强杀：退出意图发不出去，靠登出钩子兑现（下线瞬间 `forceFirst("logout")`）——语义等价，但玩家若在同一 tick 内被踢，事件可能施加在已离线的 player 上（`forceFirst` 内需保证 player 仍在线，否则跳过）。
2. 换维度时 `gameTime` 按世界各自递增，现有超时判定用当前维度 `gameTime` 与发起时比较（`EventChoiceManager:121` vs `:223`）本就存在跨维度偏差；本次加维度钩子后该偏差不再影响结果。
3. `storm_approaching` 回放会**立刻**撤掉旧批怪的加成（符合「覆盖」语义），若玩家预期旧批怪保持到原到期时间，需要在验收时确认这是期望行为。
4. 三语 lang 的 `%%` 规则：本批文案无字面百分号。

## 8 已知限制（2026-10-29 收口复核）

| # | 限制 | 现状 / 影响 |
|---|---|---|
| 1 | 登出 / 单机退游时 38 tick 卡片动画队列无人驱动 | `EventCardService.showThenRun` 的结算回调靠服务端 tick 推进；玩家在动画播完前登出（或单机退出存档）时该队列可能不再被驱动 ⇒ 出现「服务端已按强制结算收尾（`AP_EVENT_CHOICE: force:*:reason=logout` 已打），但**`AP_EVENT_FIRE` 不出现**」。判据侧需接受这种「已结算但无 `AP_EVENT_FIRE`」。 |
| 2 | alt-tab 失焦回焦后「已警告」被复位 | `EventChoiceScreen#tick` 在窗口失焦时同时复位 `escHeld` 与 `escalationWarned`；回焦后第一次 ESC 又只给警告，玩家需**重新按两次 ESC** 才能退出。 |
| 3 | 长按 ESC 永不退出 | `keyPressed` 刻意吞掉同一次按住的 GLFW REPEAT，只有「松开再按」才被当作第二次按下；长按不放不会退出（与 R2「再次按下」的字面语义一致，属**刻意**行为）。 |
| 4 | `EventTimerStore.clear()` 无调用点且不回放 hook | 全仓**零调用**；它只删 `EXPIRY` 条目，**不**触发 `ON_EXPIRE` 回调。若将来用它做登出清理，必须先决定是否回放 hook，否则瞬态效果残留；当前登出泄漏因此只是内存条目滞留（玩家已离线，无实际危害）。 |

## 9 回滚

回滚 = 撤销 4.1 / 4.4 两处改动 + 恢复 `shouldCloseOnEsc` 返回 true + 删三语键 + 撤销维度钩子；无数据迁移、无存档结构变更（`ModAttachments` 不动）。
