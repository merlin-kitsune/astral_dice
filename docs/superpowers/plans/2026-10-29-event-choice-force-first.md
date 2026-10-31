# 事件二选一：退出即强制触发首个事件 + 叠加口径统一 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `subagent-driven-development` (recommended) or `executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让「事件二选一」界面的任何形式的退出都强制触发第一个事件（ESC 两段式），并把同一事件的重复触发统一为「撤销旧的 + 重新计时」。

**Architecture:** 方案 A —— 客户端在三条出口（ESC 二次确认 / `onClose` / `removed`）发同一个 exit 意图，服务端把它当作「退出即结算」，并额外挂 `PlayerChangedDimensionEvent` 覆盖换维度；叠加语义在 `EventTimerStore.start` 的入口做 revert-at-start，复用既有纯撤销回调。

**Tech Stack:** Java 21（neoforge-1.21.1）/ Java 17（forge-1.20.1）· Minecraft Screen/EventBus · 单仓四线（本轮只动 P0 两条）

**Spec:** `docs/features/event-choice-force-first-spec.md`

## Global Constraints

- 落地线：**P0 = `neoforge-1.21.1` 先做、验收通过后移植 `forge-1.20.1`**；`neoforge-26.1.2` / `fabric-1.20.1` 冻结期不动。
- 不改 `ModAttachments` 的落盘结构，不做数据迁移。
- 三语 lang 必须同步（zh_cn / en_us / ja_jp），文案无字面 `%`，不需要 `%%`。
- 包类不新增：复用 `EventChoiceCancelPayload` / `EventChoiceCancelMessage`，只改其 Javadoc 与服务端处理语义。
- `cancelFor(...)`（服务端内部关屏，如确认后的动画锁）**必须保持不触发**任何事件。
- 文件必须保持 LF 行尾（仓库口径），提交前 `git ls-files --eol` 复核。
- 版本号：计划定稿时四线仍是 `1.5.0-rc.6+<线>`，实际落地时同批 bump 到 `1.5.0-rc.7+<线>`（`d91fa390` 内含四线 `gradle.properties` 各一行改动）。

## Review Focus

1. **确认选择后又被当成「退出」** —— `confirm` 置 `resolving` 后同 tick `remove`+`Close`，客户端收到 Close 会 `setScreen(null)` 触发 `removed()`；若 `confirmed` / `closedByServer` 闸门失效，就会在选完之后再额外结算一次首个事件。
2. **换维度时把加载屏关掉** —— `closeIfToken` 若只比对 token 不校验 `instanceof EventChoiceScreen`，会把 `ReceivingLevelScreen` 一起 `setScreen(null)`，玩家卡在黑屏。
3. **回放块位置写错** —— revert-at-start 若写进 `if (onExpire != null)` 里，`red_heat_warning` 的新 +5 攻击会被旧回调立刻撤掉（`EventEffects:102` 先于 `:103`），玩家看到「指示器还在但攻击力没加成」。
4. **玩家已下线仍被结算** —— 登出路径的 `forceFirst("logout")` 若在 `ServerPlayer` 已移除世界后调用，往离线玩家施加效果会静默失败或 NPE。
5. **exit 包与 Close 包竞态** —— 同一 tick 内客户端既发 exit 又收到 Close，服务端必须靠会话存在性幂等，不能二次触发。

---

### Task 1: 服务端「退出即结算」语义（neoforge-1.21.1）

**Files:**
- Modify: `neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/event/EventChoiceManager.java:188-198`（`cancel` 改名并改语义）
- Modify: `neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/network/EventChoiceCancelPayload.java:39-43`（调用点）

**Interfaces:**
- Consumes: 现有 `EventChoiceManager.forceFirst(ServerPlayer player, String reason)`（`:161-173`）、会话表 `SESSIONS`（`:55`）、幂等判定（`:187`）
- Produces: `public static void exitWithoutChoice(ServerPlayer player, int token, String reason)` —— 供 Task 2 的客户端包、Task 3 的维度钩子复用

- [ ] **Step 1: 写断言脚本（先红）**

在 `scripts/verify/` 下新建 `verify_event_choice_exit.ps1`：对给定线路文件断言三件事 —— ① `EventChoiceManager` 里存在 `exitWithoutChoice`；② 该方法体内调用 `forceFirst(`；③ 文件里不再存在 `void cancel(ServerPlayer`。运行一次，预期三条全 FAIL。

- [ ] **Step 2: 改名并把语义改成 forceFirst**

把 `cancel(ServerPlayer player, int token, String reason)` 改名为 `exitWithoutChoice(...)`，方法体保留既有守卫（会话缺失 / token 不匹配 / `resolving != 0` ⇒ 直接 return），守卫之后改为调用 `forceFirst(player, reason)`（它负责清会话 + 发 Close + 触发首个事件）。Javadoc 写明「用户 2026-10-29 裁决：退出即强制结算首个事件」。

- [ ] **Step 3: 更新唯一调用点**

`EventChoiceCancelPayload` 的 `handle` 里把 `EventChoiceManager.cancel(...)` 改成 `EventChoiceManager.exitWithoutChoice(...)`，理由字符串保持 `"esc"`（每次退出带的 reason 由客户端传来的现状值透传）。

- [ ] **Step 4: 编译 + 跑断言**

Run: `pwsh -NoProfile -Command ".\gradlew.bat :neoforge-1.21.1:classes --console=plain -q"`，再 `pwsh -NoProfile -File scripts/verify/verify_event_choice_exit.ps1`
Expected: 编译 0 错；断言三条全 PASS。

- [ ] **Step 5: Commit**

```bash
git add neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/event/EventChoiceManager.java \
        neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/network/EventChoiceCancelPayload.java \
        scripts/verify/verify_event_choice_exit.ps1
git commit -m "feat(event): ESC/exit 退出二选一界面即强制结算首个事件（服务端语义）"
```

---

### Task 2: 客户端 ESC 两段式 + 三条出口统一发 exit（neoforge-1.21.1）

**Files:**
- Modify: `neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/client/EventChoiceScreen.java:83-86`（新字段）、`:232-234`（`shouldCloseOnEsc`）、`:245-250`（`onClose`）、新增 `keyPressed` / `removed` / `requestExit` / `markClosedByServer`
- Modify: `neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/client/EventChoiceClientHandler.java:47-52`（`closeIfToken` 加类型校验与 `markClosedByServer`）

**Interfaces:**
- Consumes: Task 1 的 `exitWithoutChoice` 仅服务端可见；客户端只需按现状发 `EventChoiceCancelPayload(token)`
- Produces: `EventChoiceScreen#markClosedByServer()`（`public void`，供 handler 调用）

- [ ] **Step 1: 加字段与 ESC 两段式**

新增 `private boolean escalationWarned; private boolean exitSent; private boolean closedByServer;`。新增覆写：

```java
@Override
public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
    if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
        if (!escalationWarned) {
            escalationWarned = true;
            if (minecraft != null && minecraft.player != null) {
                minecraft.player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("message.astral_dice.event_choice.esc_warning"), true);
            }
            return true;
        }
        requestExit();
        this.onClose();
        return true;
    }
    return super.keyPressed(keyCode, scanCode, modifiers);
}
```

`shouldCloseOnEsc()` 改返回 `false`；`requestExit()` 实现为 `if (confirmed || exitSent) return; exitSent = true; PacketDistributor.sendToServer(new EventChoiceCancelPayload(this.token));`（1.20.1 移植时换成 `ModNetwork.sendToServer(...)`）。

- [ ] **Step 2: onClose 与 removed 都走同一条出口**

`onClose()` 改为 `if (!confirmed) requestExit(); super.onClose();`；新增 `@Override public void removed() { if (!confirmed && !exitSent && !closedByServer) requestExit(); super.removed(); }`。

- [ ] **Step 3: 抑制「服务端关屏」误判**

`EventChoiceClientHandler.closeIfToken` 改成：只有当前 Screen 是 `EventChoiceScreen` 且 token 匹配时才 `markClosedByServer()` + `setScreen(null)`；否则什么都不做。

- [ ] **Step 4: 编译 + 断言**

Run: `pwsh -NoProfile -Command ".\gradlew.bat :neoforge-1.21.1:classes --console=plain -q"`
Expected: 0 错。再 `grep -c "shouldCloseOnEsc" `（应 1 处且 `return false`）、`grep -n "keyPressed"`（应命中新覆写）。

- [ ] **Step 5: Commit**

```bash
git add neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/client/EventChoiceScreen.java \
        neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/client/EventChoiceClientHandler.java
git commit -m "feat(event): 二选一界面 ESC 两段式 + 三条出口统一发 exit 意图"
```

---

### Task 3: 换维度钩子（neoforge-1.21.1）

**Files:**
- Modify: `neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/event/EventChoiceManager.java`（在现有 `onPlayerLogout` 之后新增监听）

**Interfaces:**
- Consumes: Task 1 的 `exitWithoutChoice` 或直接 `forceFirst`
- Produces: `public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event)`

- [ ] **Step 1: 加监听**

按现有 `onPlayerDeath` / `onPlayerLogout` 的写法（同一个 `@SubscribeEvent` 静态方法风格与订阅方式）新增一个：取 `ServerPlayer`，若该玩家存在待决会话则调 `exitWithoutChoice(player, session.token, "dimension")`；若签名取不到 token，直接调 `forceFirst(player, "dimension")`（`forceFirst` 自带幂等）。

- [ ] **Step 2: 编译 + 断言**

Run: `pwsh -NoProfile -Command ".\gradlew.bat :neoforge-1.21.1:classes --console=plain -q"`，`grep -n "PlayerChangedDimensionEvent"` 应命中 1 处。

- [ ] **Step 3: Commit**

```bash
git add neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/event/EventChoiceManager.java
git commit -m "feat(event): 换维度即结算待决的二选一会话"
```

---

### Task 4: 三语 lang（neoforge-1.21.1）

**Files:**
- Modify: `neoforge-1.21.1/src/main/resources/assets/astral_dice/lang/zh_cn.json`（`gui.astral_dice.event_choice.title` 附近，约 :1024）
- Modify: `neoforge-1.21.1/src/main/resources/assets/astral_dice/lang/en_us.json`（约 :1024）
- Modify: `neoforge-1.21.1/src/main/resources/assets/astral_dice/lang/ja_jp.json`（约 :1023）

**Interfaces:**
- Produces: 键 `message.astral_dice.event_choice.esc_warning`（Task 2 的客户端渲染依赖它）

- [ ] **Step 1: 三处各加一行**（用 python 按行插入，不用会整文件转 CRLF 的编辑器工具）

- zh_cn：`"message.astral_dice.event_choice.esc_warning": "退出会强制触发第一个事件，再次按下 Esc 退出",`
- en_us：`"message.astral_dice.event_choice.esc_warning": "Leaving will force-trigger the first event. Press Esc again to leave.",`
- ja_jp：`"message.astral_dice.event_choice.esc_warning": "終了すると最初のイベントが強制発動します。もう一度 Esc で終了します。",`

- [ ] **Step 2: 守门**

Run: `pwsh -NoProfile -File tools/check_lang_sync.ps1`，再 `python tools/audit_patchouli_keys.py`
Expected: 两处均 0 缺键；`git ls-files --eol` 显示 LF。

- [ ] **Step 3: Commit**

```bash
git add neoforge-1.21.1/src/main/resources/assets/astral_dice/lang/
git commit -m "feat(lang): 二选一界面 ESC 警告文案（三语）"
```

---

### Task 5: 叠加 revert-at-start（neoforge-1.21.1）

**Files:**
- Modify: `neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/event/EventTimerStore.java:53-62`

**Interfaces:**
- Consumes: 现有 `ON_EXPIRE`（`:33`）与 `start(Player, ResourceLocation, int, Consumer<Player>)` 签名
- Produces: 行为契约 —— 「同一 `(player, eventId)` 再次 `start` 时，先执行旧钩子再登记新钩子」

- [ ] **Step 1: 在 4 参体最开头插入回放块（无条件，不在 `if (onExpire != null)` 内）**

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

位置必须在方法体第一行（`end = ...` 之前）；2 参重载本就走 4 参，不必重复。

- [ ] **Step 2: 编译 + 断言**

Run: `pwsh -NoProfile -Command ".\gradlew.bat :neoforge-1.21.1:classes --console=plain -q"`
再断言：`grep -n "revert_failed"` 命中 1 处、且该行位于 `if (onExpire != null)` 之前（脚本按行号比较）。

- [ ] **Step 3: Commit**

```bash
git add neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/event/EventTimerStore.java
git commit -m "fix(event): 同一事件重复触发时先撤销旧效果再重新计时"
```

---

### Task 6: forge-1.20.1 移植

**Files:**
- 按 Task 1–5 的同名文件逐条移植到 `forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/...` 与 `forge-1.20.1/src/main/resources/.../lang/`

**Interfaces:**
- Consumes: 无（与 neoforge 线同构，仅网络 API 差异）

- [ ] **Step 1: 逐处移植并按差异表改写**：`PacketDistributor.sendToServer` → `ModNetwork.sendToServer`；`PlayerTickEvent.Post` → `TickEvent.PlayerTickEvent`；`ModEffects.X` → `ModEffects.X.get()`（本任务不涉）；`drawString` 的 float/int 重载差异（本任务不涉）。

- [ ] **Step 2: 编译 + 同一套断言**

Run: `pwsh -NoProfile -Command ".\gradlew.bat :forge-1.20.1:classes --console=plain -q"`，再跑 `scripts/verify/verify_event_choice_exit.ps1` 指向 forge 线。

- [ ] **Step 3: Commit**

```bash
git add forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/ \
        forge-1.20.1/src/main/resources/assets/astral_dice/lang/
git commit -m "feat(event): forge-1.20.1 同步二选一退出结算 + 叠加口径"
```

---

### Task 7: 静态守门与产物

- [ ] **Step 1:** `pwsh -NoProfile -Command ".\gradlew.bat :neoforge-1.21.1:jar :forge-1.20.1:jar --console=plain"`，Expected: BUILD SUCCESSFUL、两件 jar 产出。
- [ ] **Step 2:** `python tools/verify_sign_display_names.py` 与 `tools/check_lang_sync.ps1` 全绿。
- [ ] **Step 3:** `git status --short -uall` 复核：除本批改动外，仍只剩既有 3 个未跟踪文件（`models/astral_anvil.bbmodel`、`astral_batch3_probe.js`、`zz_screen_probe.py`），一个都不 add。

### Task 8: 实机验证（须用户点名下达）

- [ ] **Step 1:** 按 spec §5 六条实机判据执行（ESC 一次 / ESC 两次 / 换维度 / 断线 / 正常确认 / 同一事件连触发两次）。
- [ ] **Step 2:** 收尾 `pwsh -NoProfile -File scripts/test/mt.ps1 --phase stop --purge-saves`，日志必须出现 `MT_STOP: OK`。

---

## 执行记录 / 遗留（2026-10-29 收口复核）

本节只追加**收口审查**发现的订正点，不回改上面 Task 1–8 的正文。

- **`AP_EVENT_FIRE` 判据**：R1 与实机判据原先写的那个机器行名在源码里**并不存在**。实际机读行是 `AstralEventDispatcher` 的 `AP_EVENT_FIRE: id={}:src={}:tier={}:targetsFinal={}`。判据改按 `AP_EVENT_FIRE: id=<事件id>` 且 `src=ACTIVE_SKILL` 断言 —— `src` 用来区分「二选一界面派发」与 `ITEM_USE` / `INVESTIGATION_KILL` / `CUSTOM`；确认派发与强制派发同为 `ACTIVE_SKILL`，两者再用 `AP_EVENT_CHOICE: confirm:*` 与 `AP_EVENT_CHOICE: force:*:reason=*` 区分。
- **`cancelFor` 例外（待用户确认）**：`cancelFor(...)`（事件卡动画开播时把选择界面顶掉）**不**触发强制结算，尽管 R1 写的是「任何形式的退出」。理由：同一时刻已经在播另一条事件卡动画，若再强制触发第一个事件会**双触发**。**本次未改代码**，只登记为例外，等用户裁决后再定。
- **`force:` 行改记实际派发事件**：`AP_EVENT_CHOICE: force:{token}:pick=0:reason={}:event={}` 原先传 `session.firstEvent`，fallback 时记的是**没被派发**的 id；已改为传实际派发的 `forced`（标签、字段顺序、参数位置都不变），fallback 细节仍由既有 `AP_EVENT_CHOICE: force_fallback:*:from=*:to=*` 行给出。
- **断言补洞**：`scripts/verify/verify_event_timer_stack.py` 原断言 01–03 只证明「回放块在 `if (onExpire != null)` 之前」，而「回放不会撤掉**新写入**的状态」还依赖派发器**在 `effect().apply` 之前**用无回调 `start` 登记过 revert 回调。新增断言 04：派发器内无回调的 `EventTimerStore.start(...)` 行号必须 **小于** `effect().apply(` 行号，并打印两个行号。已用变异测试验证（对调两者顺序 / 删掉该 `start` 行 ⇒ 04 红、01–03 仍绿；原文件 ⇒ 04 绿）。
- **已知限制**（详见 spec §8）：① 登出 / 单机退游时 38 tick 卡片动画队列无人驱动，可能「已结算但 `AP_EVENT_FIRE` 不出现」；② alt-tab 失焦回焦后「已警告」被复位，需重新按两次 ESC；③ 长按 ESC 永不退出（刻意吞 REPEAT）；④ `EventTimerStore.clear()` 无调用点且不回放 hook。
- **ESC 守卫补丁**：`EventChoiceScreen#keyPressed` 的 ESC 分支在 `escHeld` 吞 REPEAT 的守卫**之前**新增 `if (this.confirmed) return super.keyPressed(...)` —— 点过确认、服务端 Close 包尚未回来的 1~2 tick 窗口里按 ESC 不再弹「退出会强制触发第一个事件」的 actionbar 警告（该警告与实际语义相反）。双线同形。
