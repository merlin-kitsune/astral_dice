# 实机验证报告：选卡界面（§9 八条）+ Jade 三店倒计时（§5 五条）（2.0.0-SNAPSHOT.128，2026-10-17）

- 被测线：`neoforge-1.21.1`（MC 1.21.1 / NeoForge 21.1.235 / Java 21）
- 工作树：`/mnt/f/MCProject/astral_dice_multiloader-next`（分支 `multi-dev-next`，HEAD = `f7ee13e0`）
- 版本：`2.0.0-SNAPSHOT.128`；产物 `astral_dice-2.0.0-SNAPSHOT.128+neoforge_1.21.1.jar`
  （`sha256 = bed6443296ccceb02f0e1a70bb08de71ec78478e55f4e094a221aa8b61181141`，`build/libs` 与 `run/1.21.1/mods` **逐字相同**）
- 用例来源：`docs/features/card-choice-screen-spec.md` §9（8 条）、`docs/features/jade-shop-cooldown-spec.md` §5（5 条）
- 判据通道：服务端 KubeJS 临时探针 `/astralcc`（`console.info` → `latest.log` 的 `AP_CC:*`）
  + 客户端 KubeJS 临时探针（屏幕事件驱动 + 自截图 → `latest.log` 的 `AP_CCC:*`）
  + `mt_case` 的 `log / absent / kubejs / crash / mixin` 断言
- 靶子：本轮**未生成任何生物**（无亡灵靶问题）
- 测试世界：`testworld`（超平坦/创造或生存按用例切换/允许命令/keepInventory；测后已 purge，见 §6）

---

## 0. 结论速览

### 选卡界面（spec §9，8 条）

| # | 判定项 | 结果 | 关键证据（`temp/ccjade-20261017/logs/latest.log`） |
|---|---|---|---|
| 1 | 双击放入 / 再次双击取回 | **PASS** | `step=42:...:filled=1:c0=astral_dice:attack_card_medium` → `step=70:...:c0=empty:...:inv={...8:astral_dice:attack_card_mediumx1}` |
| 2 | 拖动 ≥4px 跟手；松手空卡槽=放入；拖回物品栏空格=落格；松手空白处=原路还原 | **PASS** | `step=44:...:c0=...medium` / `step=70:...:c0=empty:inv={1:...largex1,2:...mediumx1}` / `step=96:...:c3 仍在` |
| 3 | 类型过滤（效果牌 → 战斗牌会话） | **部分成立（见 §5-D1）** | 不进入卡槽（`filled=0`）✅；但「原地还原」不成立：卡被原版点击**拿到光标**（`step=44:...:carried=astral_dice:effect_card_berserk`） |
| 4 | 未满无按钮、点击该区域无反应 | **PASS** | `step=44:...:complete=0:filled=1`；点击后 `step=60:...:complete=0:filled=1`；截图 `cc-incomplete-1of3-no-button.png`（无按钮） |
| 5 | 放满确认（按钮出现、消耗 1 张、回调拿到 3 个 ItemStack） | **PASS** | `AP_CARD_CHOICE: confirm:...picked=3` + `AP_CC:HANDLER:...n=3:items=[mediumx1,largex1,epicx1]` + `INV_DUMP:inv={}` |
| 6 | Esc 零消耗 | **PASS** | `AP_CARD_CHOICE: abandon:...consumed=0` + `AFTER_CLOSE:inv={0:...mediumx1,1:...largex1}` + 无 `HANDLER` |
| 7 | 拖到已占用（不同物品）格 → 原路还原、不换位 | **PASS** | `step=74:...:c0=...medium:...carried=empty:inv={...5:minecraft:diamondx1}`；截图 `cc-occupied-after-drag.png` |
| 8 | 开界面死亡 → 掉牌 + 无回调 + 重生后可再开 | **PASS** | `death_cleanup` + `abandon:...consumed=0` + `AP_CC:DROPS:n=2{medium@d=8,large@d=8}` + 第二次 `SCREEN:open:...:seq=death` + `step=40:...:inv={}` + 无 `already_open` |

### Jade 三店倒计时（spec §5，5 条）

| # | 判定项 | 结果 | 证据 |
|---|---|---|---|
| 1 | 卡牌店：`补货倒计时 M:SS` 出现且逐秒递减、到 0 后消失 | **PASS** | 截图 `jade-card-cd-t1/t2`（`0:50` → `0:41`）、`jade-card-expired`（无该行）；`AP_CC:CDDIAG card=940 → 758`；`AP_CC:CDS` 每 20t `-20` |
| 2 | 筹码店：`可进入倒计时 M:SS`；清零后立即消失 | **PASS**（清零由探针置 0，见 §5-D5 说明） | 截图 `jade-chip-cd-t1/t2`（`4:53`）、`jade-chip-cleared`（无该行）；`chip=5774 → 5714` |
| 3 | 抽奖盒：`可进入倒计时 M:SS` | **PASS** | 截图 `jade-drawer-cd-t1/t2`（`4:53`）、`jade-drawer-cleared`；`drawer=5780 → 5720` |
| 4 | 无冷却时三块均无该行 | **PASS** | 截图 `jade-card-nocd` / `jade-chip-nocd` / `jade-drawer-nocd`（悬浮框只有方块名 + `Astral Dice`） |
| 5 | 未安装 Jade 启动正常 | **无法判定**（环境约束，见 §5-D4） | 见 §5-D4 的举证与原因 |

> 无产品崩溃、无 Mixin 失败、`kubejs/server.log` 0 errors、无新增 `crash-reports`。

---

## 1. 构建与产物核对

```
$ pwsh -NoProfile -File scripts/test/mt.ps1 --version 1.21.1 --phase build
MT_INFO: [尝试 1/3] gradlew :neoforge-1.21.1:build (超时 60s)
MT_BUILD: OK (2s) — 产物已更新：astral_dice-2.0.0-SNAPSHOT.128+neoforge_1.21.1.jar

$ sha256sum neoforge-1.21.1/build/libs/astral_dice-2.0.0-SNAPSHOT.128+neoforge_1.21.1.jar \
            run/1.21.1/mods/astral_dice-2.0.0-SNAPSHOT.128+neoforge_1.21.1.jar
bed6443296ccceb02f0e1a70bb08de71ec78478e55f4e094a221aa8b61181141  .../build/libs/...
bed6443296ccceb02f0e1a70bb08de71ec78478e55f4e094a221aa8b61181141  .../run/1.21.1/mods/...
```

环境装配（`--phase env`）：`MT_MODS: OK`（Sodium/Iris/ModernFix/KubeJS/Rhino/Architectury/Carpet/史莱姆压制/优化类）、
`MT_PAUSE_LOCK: on`、`MT_KEYBINDS: OK`、`MT_WINDOWED: on`、`MT_WORLD: OK — 世界重建（allowCommands=1, keepInventory=true）`。

---

## 2. 方法与临时探针（为什么必须自建入口）

本批选卡界面**没有接入任何具体技能**（spec §8），游戏内没有真实入口能打开它；Jade 悬浮框是**客户端视觉**，
服务端日志读不到渲染结果。因此本轮用了两支**临时** KubeJS 探针（收尾已删除，备份见 `temp/ccjade-20261017/probe-backup/`）：

1. **服务端 `/astralcc`**（`server_scripts/astral_cc_jade_probe.js`）
   - `open <1|3> <combat|effect|any> <场景名>`：调 `CardChoiceManager.start(...)`（与将来调用方**同一公开 API**、
     **同一服务端权威链路**），并把**场景序号编码进 token**（`300000 + idx*10000 + n`）——
     KubeJS 类过滤器拒绝 `java.nio.file.Path`（实测 `JsonIO.isRegularFile` 抛 `InternalError`），标记文件方案不可用。
   - 回调 `handler` 把拿到的 ItemStack 逐项落盘：`AP_CC:HANDLER:token=..:n=..:items=[id x1,...]`。
   - `cd <card|chip|drawer|clear> <秒>`：按**产品同一附件/同一 API** 写三处冷却
     （`ModAttachments.setShopTransferCooldownEnd` / `ChipShopState#withCooldown` / `setEventDrawerCooldownUntil`），
     **不是**改渲染，只是把「购买/转账后本会写入的冷却」直接写进去，让 Jade 的读取路径有非零值可显示。
   - `diag` / `cdsample`：按 Jade provider 的**同一算法**（`截止时刻 − 世界时间`）逐秒输出剩余 tick；
   - `drops`：服务端扫描附近 `:item` 实体（死亡掉牌取证）。
   - Rhino 实测坑：`ServerLevel.getGameTime()` **不可见**（`Cannot find function getGameTime`）；
     可用的是 `p.level.dayTime()`（同一世界时间轴，本轮实测 `dayTime == server.tickCount == 240`，且全程未 `/time set`）。
2. **客户端探针**（`client_scripts/astral_cc_client.js`）
   - `mt_inject.ps1` 的真鼠标**没有坐标参数**（只能打窗口中心），且**界面开着时 T 键被 GUI 吞掉**（命令注入失效）；
   - 因此由客户端脚本调用屏幕自己的**公开事件入口** `mouseClicked / mouseDragged / mouseReleased / onClose`，
     传**绝对 GUI 坐标**（`getGuiLeft()/getGuiTop() + 相对值`）——卡牌移动仍由原版点击包走
     「客户端 → 服务端菜单」，与真人操作同一条产品路径；
   - 双击按真实语义**跨 tick** 实现（两次按下间隔 4t=200ms < 280ms 窗口）；
   - 每个动作与读数都落 `AP_CCC:*` 行；GUI 内容另用 `Screenshot.grab(...)` 自截图（纯 framebuffer，不含桌面）。

> 运行期对注入的约束（实测）：`inject_command` 每次约 **2.5–3 s**；`screenshot(mode=f2)` 约 **1.9 s**；
> 因此「设置冷却」与「截第一张图」之间实际已流逝 ~10–20 s，倒计时首值不是整 60/30x 秒，属正常。

---

## 3. 选卡界面逐条证据（spec §9）

用例文件：`scripts/test/cases/CC-{DBLCLICK,DRAG,FILTER,INCOMPLETE,CONFIRM,ESC,OCCUPIED,DEATH}-1.21.1.json`
（11 条全部 `MT_VALIDATE: OK`；逐条 `MT_CASE_RESULT` 见 §0 与 `temp/ccjade-20261017/run-*.log`）。

### 3.1 双击放入 / 取回（CC-DBLCLICK）
```
AP_CCC:SCREEN:open:token=...:seq=dblclick:slots=3:type=0:complete=0:filled=0:...:inv={0:astral_dice:attack_card_mediumx1,1:astral_dice:attack_card_largex1}
AP_CCC:evt=click:step=22:what=inv_card_p1:rel=16,185:ret=1:carried=empty
AP_CCC:evt=click:step=26:what=inv_card_p2:rel=16,185:ret=1:carried=empty      ← 200ms 内第二次按下 = 双击
AP_CCC:step=42:phase=after_dbl_in:...:complete=0:filled=1:c0=astral_dice:attack_card_medium:c1=empty:c2=empty:carried=empty:inv={1:...largex1}
AP_CCC:step=70:phase=after_dbl_back:...:filled=0:c0=empty:c1=empty:c2=empty:carried=empty:inv={1:...largex1,8:astral_dice:attack_card_mediumx1}
```
取回落点 = 物品栏 **槽 8**（`quickMoveStack` 反向填充，原版语义），不是原槽 0 —— 属正确行为，断言按实测口径写。
截图：`shots/cc-dblclick-after-in.png`（1/3，卡在卡框 0）与 `shots/cc-dblclick-after-back.png`（0/3，卡回物品栏），
分别对应日志 `AP_CCC:evt=shot:what=after_dbl_in:n=8` / `...after_dbl_back:n=9`。

### 3.2 拖动（CC-DRAG）
```
AP_CCC:evt=drag:step=26:what=to_slot0:...:carried=astral_dice:attack_card_medium   ← 位移≥4px 才升格拿牌
AP_CCC:step=44:...:filled=1:c0=astral_dice:attack_card_medium
AP_CCC:evt=click:step=50:what=slot0_press ... / evt=drag:step=52:... / evt=release:step=56:what=drop_hotbar2
AP_CCC:step=70:phase=after_drag_back:...:c0=empty:...:inv={1:...largex1,2:astral_dice:attack_card_mediumx1}
AP_CCC:step=96:phase=after_blank_drop:...:c0=empty:...:inv={1:...largex1,2:...mediumx1}   ← 松手空白处：原路还原、不丢失
```

### 3.3 类型过滤（CC-FILTER）—— 部分成立
```
AP_CCC:evt=click:step=24:what=effect_card_press:rel=34,185:ret=1:carried=empty
AP_CCC:evt=release:step=30:what=effect_drop_slot0:...:carried=astral_dice:effect_card_berserk   ← 卡被拿到光标
AP_CCC:step=44:phase=after_drag_effect:...:complete=0:filled=0:c0=empty:c1=empty:c2=empty:carried=astral_dice:effect_card_berserk:inv={0:...mediumx1}
AP_CCC:step=62:phase=after_return:...:inv={0:...mediumx1,4:astral_dice:effect_card_berserkx1}  ← 点空格放回，未丢失
AP_CCC:step=86:phase=after_dbl_effect:...:complete=0:filled=0:...:carried=astral_dice:effect_card_berserk
AP_CCC:INV_DUMP:inv={0:...mediumx1,1:astral_dice:effect_card_berserkx1}                        ← 关屏后回背包
```
判定：**「不进入卡槽」成立**（`filled` 恒 0）；**「原地还原」不成立** —— 见 §5-D1。

### 3.4 未满无按钮（CC-INCOMPLETE）
```
AP_CCC:step=44:phase=one_of_three:...:complete=0:filled=1:c0=astral_dice:attack_card_medium
AP_CCC:evt=click:step=50:what=confirm_area_press:rel=87,101:ret=1
AP_CCC:step=60:phase=after_confirm_click:...:complete=0:filled=1:c0=astral_dice:attack_card_medium   ← 点击无任何变化
```
服务端窗口内无 `AP_CARD_CHOICE: confirm*`（`absent` 断言 PASS）。
截图 `shots/cc-incomplete-1of3-no-button.png`：1/3 时**没有**绿色确认按钮（与 3/3 的 `cc-confirm-3of3-button.png` 成对照）。

### 3.5 放满确认（CC-CONFIRM，**生存模式**）
```
AP_CCC:step=62:phase=three_of_three:...:complete=1:filled=3:c0=...medium:c1=...large:c2=...epic
AP_CARD_CHOICE: confirm:345180:player=Dev:slots=3:type=COMBAT:picked=3
AP_CC:HANDLER:token=345180:n=3:items=[astral_dice:attack_card_mediumx1,astral_dice:attack_card_largex1,astral_dice:attack_card_epicx1]
AP_CCC:INV_DUMP:inv={}        ← 三张各消耗 1 张，背包为空
```
> ⚠️ **必须在生存模式验证**：创造模式下 `ItemStack#consume(int, Player)` 受原版 `hasInfiniteMaterials()` 保护 ⇒ 不扣物。
> 首轮在创造模式跑时确实出现「确认后卡牌回到背包」，那**是原版语义不是缺陷**；改生存后同一用例 PASS（见 §5-D3）。

### 3.6 Esc 零消耗（CC-ESC）
```
AP_CCC:step=50:phase=two_of_three:...:complete=0:filled=2
AP_CARD_CHOICE: abandon:346xxx:player=Dev:consumed=0
AP_CCC:SCREEN:closed:...:inv={0:astral_dice:attack_card_mediumx1,1:astral_dice:attack_card_largex1}
AP_CCC:AFTER_CLOSE:inv={0:...mediumx1,1:...largex1}
```
（Esc 由 `mt_inject.ps1 key --key escape` 注入**真实按键**；断言另含 `absent AP_CC:HANDLER:`。）

### 3.7 拖到已占用格（CC-OCCUPIED）
```
AP_CCC:step=44:phase=card_in_slot:...:c0=astral_dice:attack_card_medium
AP_CCC:evt=drag:step=52:what=to_occupied:rel=106,185:ret=1:carried=astral_dice:attack_card_medium
AP_CCC:evt=release:step=56:what=drop_occupied:rel=106,185:ret=1:carried=empty
AP_CCC:step=74:phase=after_occupied_drop:...:c0=astral_dice:attack_card_medium:...:inv={...5:minecraft:diamondx1}
```
断言另含 `absent`：`5:astral_dice:attack_card_mediumx1`（确认没有换位）。截图 `shots/cc-occupied-after-drag.png`：卡在卡框、钻石仍在快捷栏 5。

### 3.8 开界面死亡 + 重生再开（CC-DEATH，**新世界**）
```
AP_CCC:step=48:phase=two_cards_before_death:...:filled=2:c0=...medium:c1=...large:...:inv={}
AP_CCC:KILLPOS:x=2.5:y=-60:z=0.5
AP_CARD_CHOICE: death_cleanup:370633:player=Dev
AP_CARD_CHOICE: abandon:370633:player=Dev:consumed=0
AP_CCC:DEATH:after20:screen=null:hp=20:inv={}:pos=10.5,-60,3.5
AP_CC:DROPS:n=2{astral_dice:attack_card_medium@d=8,astral_dice:attack_card_large@d=8}
AP_CCC:SCREEN:open:token=370748:n=..:seq=death:...:filled=0:...:inv={}     ← 重生后立刻再次 start 成功
AP_CCC:step=40:phase=reopened:...:complete=0:filled=0:...:inv={}
```
死亡点 `(2.5,-60,0.5)` 与重生点 `(10.5,-60,3.5)` 相距 **8.5 格**，掉落物距玩家 **8 格** ⇒ 两张卡确实掉在**死亡点**附近，
且背包为空（未回包）。断言另含 `absent AP_CARD_CHOICE: reject:reason=already_open` 与 `absent AP_CC:HANDLER:`。

---

## 4. Jade 三店倒计时逐条证据（spec §5）

用例：`scripts/test/cases/JADE-{CARD,CHIP,DRAWER}-1.21.1.json`。截图全部为 **F2**（无界面打开，走 `mt_capture` 世代登记，
故可用 `vision` 复核），文件名与 tag 见 `run/1.21.1/screenshots/.mt_shots.json`；副本在 `temp/ccjade-20261017/shots/`。

### 4.1 卡牌店（JADE-CARD）
```
AP_CC:CDSET:kind=card:secs=60:now=12285:end=13485
AP_CC:CDDIAG:now=12545:card=940:...
AP_CC:CDDIAG:now=12727:card=758:...
AP_CC:CDS:t=12379:now=12379:card=1106:...   ← 每 20t 一行，逐行 -20
AP_CC:CDS:t=12399:now=12399:card=1086:...
...
AP_CC:CDSET:kind=card:secs=3:now=12794:end=12854
AP_CC:CDDIAG:now=12992:card=-12992:...      ← 到期后不再显示
```
截图判读：`jade-card-cd-t1` = 「卡牌商店 / **补货倒计时 0:50** / Astral Dice」；
`jade-card-cd-t2`（约 9 s 后）= 「补货倒计时 **0:41**」（逐秒递减）；
`jade-card-expired` = 悬浮框只剩「卡牌商店 / Astral Dice」（**该行消失**）。

### 4.2 筹码店（JADE-CHIP）
```
AP_CC:CDSET:kind=chip:secs=300:...
AP_CC:CDDIAG:now=9921:card=-9921:chip=5797:drawer=-9921
AP_CC:CDS:t=9944:now=9944:...:chip=5774:...
AP_CC:CDS:t=9964:now=9964:...:chip=5754:...
AP_CC:CDSET:kind=chip:secs=0:...            ← 清零
```
截图：`jade-chip-cd-t1` = 「筹码商店 / **可进入倒计时 4:53** / Astral Dice」；`jade-chip-cleared` = **无该行**。
> spec §5-2 的「空白筹码投入后冷却清零 ⇒ 该行立即消失」：本轮用「把冷却写成 0」覆盖了**显示侧**（清零后立即消失 ✅），
> **没有**走真实「投空白筹码」交互（见 §5-D5）。

### 4.3 抽奖盒（JADE-DRAWER）
```
AP_CC:CDSET:kind=drawer:secs=300:...
AP_CC:CDDIAG:now=11146:...:drawer=5797
AP_CC:CDS:t=11163:...:drawer=5780
AP_CC:CDS:t=11183:...:drawer=5760
AP_CC:CDSET:kind=clear:secs=0:...
```
截图：`jade-drawer-cd-t1` = 「抽奖盒 / **可进入倒计时 4:53** / Astral Dice」；`jade-drawer-cleared` = **无该行**。

### 4.4 无冷却无该行（§5-4）
`jade-card-nocd` / `jade-chip-nocd` / `jade-drawer-nocd` 三张截图均为「方块名 + Astral Dice」两行，**没有倒计时行**。

### 4.5 未装 Jade（§5-5）—— 无法判定
见 §5-D4。

---

## 5. 缺陷 / 观察 / 限制

### D1（产品，P2，仅取证未修）：类型不符的卡在选卡界面会被原版点击「拿到光标」，不是「原地还原」
- 现象：战斗牌会话里对**效果牌**按下左键，屏幕层 `canStartGesture` 判否（`menu.type().matches(stack)==false`）
  ⇒ 落到 `super.mouseClicked` ⇒ 原版 `PICKUP` 把卡拿到光标；双击则走原版 `PICKUP_ALL`。
- 证据：`AP_CCC:step=44:phase=after_drag_effect:...:carried=astral_dice:effect_card_berserk`；
  `step=86:...:carried=astral_dice:effect_card_berserk`。
- 与 spec 的差距：§9-3 写「用效果牌拖到战斗牌会话的卡槽 → 拒绝（**原地还原**）」。实测**不进入卡槽**、
  **不丢失**（关屏后 `INV_DUMP` 里卡回到背包），但卡会先挂在光标上。**判定：该条「部分成立」**。
- 影响面：只影响「类型不符时的手势观感」；不产生复制/丢失，不触发回调。

### D2（工具链，非产品）：`mt_launch` 的 `preflight-op` 在刚进世界后**稳定**注入失败（本轮 4/4）
- 现象：`MT_TIMING: world-ready=21s` 后约 6 s，`MT_preflight-op: ERROR (27s) — 未取到 AP_OP_PERM 读数（注入返回码 /）`；
  日志原文：`[CHAT] ...tralprobe opprobe/astralprobe opprobe<--[此处]`（T 键被吞、文本被回显进聊天）。
- 对照：同一环境**手工**再注入 `/astralprobe opprobe` 立即成功：
  `[CHAT] AP_OP_PERM:has2=1:level=4:src=cmdsource+profile:dump=rc=undefined`。
- 结论：属测试工具链的启动期注入竞态（`mt_launch` 的 preflight 过早），**不是产品缺陷**；本轮改用「launch 报 ERROR 后手工复验
  探针可用」继续执行用例（`--phase cases` 仍正常）。

### D3（测试口径，非产品）：确认消费必须在**生存模式**验证
- 创造模式下 `ItemStack#consume(int, Player)` 因 `hasInfiniteMaterials()` **不扣物** ⇒ 首轮「确认后 3 张卡仍在背包」是原版语义。
- 证据：首轮（创造）`SCREEN:closed:...inv={0:medium,1:large,2:epic}`；改生存后 `INV_DUMP:inv={}` 且 `picked=3`。

### D4（无法判定）：未安装 Jade 时客户端启动
- 原因①：dev 客户端把 Jade 作为 **Gradle 运行时依赖**加载（`neoforge-1.21.1/build.gradle:200`
  `implementation "maven.modrinth:jade:eYz2YBGT"`），`run/1.21.1/mods` 里没有 Jade 文件 ⇒ 要「不装 Jade」必须改 build.gradle（本任务禁止改源码），
  或用全局 Gradle init script（会同时影响另一代理正在跑的 `forge-1.20.1` 线，风险不可接受）。
- 原因②：无独立的非 dev 实例可用于「只装本模组」的冷启动。
- 静态侧登记（**不构成实机结论**）：`neoforge.mods.toml` 无 jade 依赖；`astral_dice.jade.mixins.json` 为
  `"required": false`（缺目标类时 Mixin 跳过）；`snownee.*` 引用只出现在 `compat/jade/**` 与 `mixin/jade/**`，
  `compat.jade` 包在 `compat/jade/` 之外**零引用** ⇒ 缺席路径在代码上不产生强引用。**仍需一次「无 Jade 冷启动」实机才能判 PASS。**

### D5（覆盖缺口）：筹码店「空白筹码投入 ⇒ 冷却清零」的真实交互未跑
- 本轮只验证了「冷却被清零后该行立即消失」（把冷却写成 0）。真实交互需要开筹码店界面、投空白筹码（有既有
  `CHIP-*` 用例与客户端驱动能力），属可补做项；未补做**不计为产品缺陷**。

### D6（测试环境陷阱，供后续参考）
- 复用旧世界跑死亡用例会把**上一轮遗留的掉落物**算进 `drops` 扫描（实测 18:14 轮的残留让 18:17 轮误判「卡牌同时掉地+回包」）。
  正确做法：死亡用例前必须重建世界（本轮最终轮即在新世界 PASS）。
- KubeJS/Rhino：`ServerLevel.getGameTime()` 不可见、`player.level` 是属性不是函数；客户端 `player.level()` 会抛
  `Cannot call property level`。时间源用 `p.level.dayTime()`（本测试世界与 gameTime 同步，全程未 `/time set`）。

---

## 6. 收尾与工作区状态

```
$ pwsh -NoProfile -File scripts/test/mt.ps1 --version 1.21.1 --phase stop --purge-saves
===== MT_PHASE: stop =====
MT_WARN: STOP: 收停后仍有残留（= 保留的 Gradle 守护进程，见下）

$ pwsh -NoProfile -File scripts/test/mt_cleanup.ps1 status
本流程进程 0 个：
Gradle 守护进程 1 个：  PID=31428 (Zulu 21, Gradle daemon)
MT_CLEANUP_STATUS: RESIDUAL — 仍有残留

$ pwsh -NoProfile -File scripts/test/mt_cleanup.ps1 run --version 1.21.1 --purge-saves
MT_CLEANUP: 指定了 --version，保留 Gradle 守护进程
MT_CLEANUP_SAVES: PURGED 0
MT_CLEANUP: 已收停 0 个本流程进程（范围：1.21.1）
MT_CLEANUP_RESULT: OK
```

- `run/1.21.1/saves/` 已清空（`PURGED 0` 是因为前一条 `--phase stop --purge-saves` 已删过，目录实测为空）。
- **无 1.21.1 客户端残留**；残留的 `PID=31428` 是 Gradle 守护进程（AGENTS 允许保留）。
- ⚠️ 另一个 `java` 进程（`PID=5032/4356`）是**另一代理正在跑的 `forge-1.20.1` 客户端**（命令行含
  `:forge-1.20.1:runClient`），本轮**未触碰**。
- 临时探针已删除（两侧：`scripts/test/resources/kubejs/1.21.1/**` 与 `run/1.21.1/kubejs/**`），备份在
  `temp/ccjade-20261017/probe-backup/`（temp 不入库）。保留 11 个用例 JSON（本批测试资产，可复跑）。

`git status --short -uall` 原文：
```
?? %SystemDrive%/ProgramData/Microsoft/Windows/Caches/cversions.2.db
?? %SystemDrive%/ProgramData/Microsoft/Windows/Caches/{1FFBA86B-CAE4-4136-B1CE-4114B4F0E200}.2.ver0x0000000000000001.db
?? %SystemDrive%/ProgramData/Microsoft/Windows/Caches/{6AF0698E-D558-4F6E-9B3C-3716689AF493}.2.ver0x0000000000000001.db
?? %SystemDrive%/ProgramData/Microsoft/Windows/Caches/{DDF571F2-BE98-426D-8288-1A9A39C3FDA2}.2.ver0x0000000000000001.db
?? docs/reports/card-choice-jade-2026-10-17-2.0.0-SNAPSHOT.128.md
?? "models/\345\274\272\345\214\226\346\213\222\346\255\242 - Converted.bbmodel"
?? "models/\350\267\257\351\232\234 - Converted.bbmodel"
?? scripts/test/cases/CC-CONFIRM-1.21.1.json
?? scripts/test/cases/CC-DBLCLICK-1.21.1.json
?? scripts/test/cases/CC-DEATH-1.21.1.json
?? scripts/test/cases/CC-DRAG-1.21.1.json
?? scripts/test/cases/CC-ESC-1.21.1.json
?? scripts/test/cases/CC-FILTER-1.21.1.json
?? scripts/test/cases/CC-INCOMPLETE-1.21.1.json
?? scripts/test/cases/CC-OCCUPIED-1.21.1.json
?? scripts/test/cases/JADE-CARD-1.21.1.json
?? scripts/test/cases/JADE-CHIP-1.21.1.json
?? scripts/test/cases/JADE-DRAWER-1.21.1.json
?? scripts/test/resources/kubejs/1.20.1/client_scripts/astral_ccj_client.js
?? scripts/test/resources/kubejs/1.20.1/server_scripts/astral_ccj_probe.js
```
> 最后两行 `astral_ccj_*` 属**另一代理**（forge-1.20.1 线）的临时探针，本轮**未创建、未修改、未删除**。
> 前 4 行 `%SystemDrive%/*.db` 与两行 `models/*.bbmodel` 为交接时已登记的既有未跟踪残留，**未触碰**。
> 本轮另清除了一个由早期 shell 重定向误产生的怪文件名 `F:\...\temp\ccjade-20261017\build.log`（已删除）。

---

## 7. 证据索引（`temp/` 不入库）

| 路径 | 内容 |
|---|---|
| `temp/ccjade-20261017/run-*.log` | 11 条用例的 `mt_case` 完整输出（步骤 + PASS/FAIL + 断言原文） |
| `temp/ccjade-20261017/run2-JADE-CARD.log` | 卡牌店断言口径放宽后（旧断言过严，非产品问题）的复跑 |
| `temp/ccjade-20261017/logs/latest.log` | 本轮 `latest.log` 快照（sha256 `59d162b0…`，含全部 `AP_CC:*` / `AP_CCC:*` / `AP_CARD_CHOICE:*`） |
| `temp/ccjade-20261017/logs/kubejs_{server,client}.log` | KubeJS 两侧日志（server 0 errors） |
| `temp/ccjade-20261017/shots/jade-*.png` | Jade 悬浮框截图（F2，已登记世代） |
| `temp/ccjade-20261017/shots/cc-*.png` | 选卡界面自截图（1/3 无按钮、3/3 有按钮、类型过滤、已占用格、死亡前） |
| `temp/ccjade-20261017/probe-backup/` | 两支临时探针的原始副本（复跑时放回 `scripts/test/resources/kubejs/1.21.1/` + `mt_env.ps1 kubejs` + 冷启动即可） |
| `temp/ccjade-20261017/{build,env,world*,launch*,final-stop}.log` | 构建/环境/启动/收尾日志 |

---

## 8. 复跑命令（探针恢复后）

```bash
# 1) 放回探针
cp temp/ccjade-20261017/probe-backup/scripts__test__resources__kubejs__1.21.1__server_scripts__astral_cc_jade_probe.js \
   scripts/test/resources/kubejs/1.21.1/server_scripts/astral_cc_jade_probe.js
cp temp/ccjade-20261017/probe-backup/scripts__test__resources__kubejs__1.21.1__client_scripts__astral_cc_client.js \
   scripts/test/resources/kubejs/1.21.1/client_scripts/astral_cc_client.js

pwsh -NoProfile -File scripts/test/mt_env.ps1 kubejs --version 1.21.1
pwsh -NoProfile -File scripts/test/mt_env.ps1 world --version 1.21.1
pwsh -NoProfile -File scripts/test/mt.ps1  --version 1.21.1 --phase launch
# 2) 逐条（每条用例单独一次执行；Jade 三条无状态耦合）
for c in CC-DBLCLICK CC-DRAG CC-FILTER CC-INCOMPLETE CC-CONFIRM CC-ESC CC-OCCUPIED CC-DEATH \
         JADE-CARD JADE-CHIP JADE-DRAWER; do
  pwsh -NoProfile -File scripts/test/mt.ps1 --version 1.21.1 --phase cases --case "scripts/test/cases/$c-1.21.1.json"
done
# 3) 收尾
pwsh -NoProfile -File scripts/test/mt.ps1 --version 1.21.1 --phase stop --purge-saves
```

---

## 9. 回归验证（2026-10-17 第二轮：CC-FILTER 新口径 / CC-DBLCLICK 多牌栈 / 右键 / 无 Jade）

- 被测线：`neoforge-1.21.1`（MC 1.21.1 / NeoForge 21.1.235 / Java 21）
- 工作树：`/mnt/f/MCProject/astral_dice_multiloader-next`（分支 `multi-dev-next`，HEAD `96352433` + 本批未提交修复）
- 版本：`2.0.0-SNAPSHOT.128`；产物 `astral_dice-2.0.0-SNAPSHOT.128+neoforge_1.21.1.jar`
  （`sha256 = 44f16811d4dafe52396da4fcd47e9557ba25edcc15975250e3488edb2d7c93af`，`build/libs` 与 `run/1.21.1/mods` 逐字相同）
- 证据目录：`temp/ccregress-20261017/`（用例输出 `case-*.log`、启动日志 `temp/mt_detached/mt_launch_20261017-20*.log`、无 Jade 日志 `logs-nojade/`）

### 9.0 结论速览

| # | 用例 | 判定 | 关键证据（`temp/ccregress-20261017/`） |
|---|---|---|---|
| 1 | **CC-FILTER**（左键按下 / 双击：原地不动、不进拖动、光标无携带物） | **PASS（25 步全 PASS / 15 条断言）** | `case-CC-FILTER-c.log`：`step=24 ... carried=empty`、`step=30/56 ... filled=0 ... carried=empty`、`absent evt=drag` |
| 2 | **CC-RIGHTCLICK**（右键 / 创意拾取键不拿牌；有携带物时右键放一个） | **PASS（27 步全 PASS / 17 条断言）** | `case-CC-RIGHTCLICK-f.log`：`btn=1 ret=1 carried=empty`、`btn=2 ... carried=empty`、`diamond_pick carried=minecraft:diamondx5`、`after_merge ... 4:minecraft:diamondx5` |
| 3 | **CC-DBLCLICK**（5 张同种牌栈双击 → 恰好 1 张入槽） | **PASS（24 步全 PASS / 15 条断言）** | `case-CC-DBLCLICK-f.log`：`step=42 ... filled=1 ... x4`、`step=70 ... filled=2 ... x3`、`step=96 ... c1=medium ... x4`、关屏 `x5` |
| 4 | **CC-OCCUPIED**（§9-7 合法卡拖到已占用格 → 原路还原） | **PASS（20 步全 PASS / 9 条断言）** | `case-CC-OCCUPIED-c.log`：`step=74 ... c0=...medium ... carried=empty ... 5:minecraft:diamondx1` + `absent 5:astral_dice:attack_card_mediumx1` |
| 5 | **J5 未装 Jade 启动** | **PASS** | `logs-nojade/latest.log`：Mod List 块 0 处 jade（27 行），对照组同位置为 `Jade 15.10.6+neoforge (jade)`；`0 ERROR/FATAL`、`0 crash-reports`、`logged in with entity id` 命中 |

> 本轮无产品崩溃（`crash-reports` 0）、无 Mixin 失败、`kubejs/server.log` 0 errors。测试靶子：本轮未生成任何生物（无亡灵靶问题）。

### 9.1 构建与部署

```
$ pwsh -NoProfile -File scripts/test/mt.ps1 --version 1.21.1 --phase build
MT_INFO: [尝试 1/3] gradlew :neoforge-1.21.1:build (超时 60s)
MT_BUILD: OK (2s) — 产物已更新：astral_dice-2.0.0-SNAPSHOT.128+neoforge_1.21.1.jar

$ sha256sum neoforge-1.21.1/build/libs/astral_dice-2.0.0-SNAPSHOT.128+neoforge_1.21.1.jar \
            run/1.21.1/mods/astral_dice-2.0.0-SNAPSHOT.128+neoforge_1.21.1.jar
44f16811d4dafe52396da4fcd47e9557ba25edcc15975250e3488edb2d7c93af  .../build/libs/...
44f16811d4dafe52396da4fcd47e9557ba25edcc15975250e3488edb2d7c93af  .../run/1.21.1/mods/...
```

`--phase env`：`MT_KUBEJS: OK`（探针同步）、`MT_MODS: OK`、`MT_PAUSE_LOCK: on`、`MT_WINDOWED: on`、`MT_WORLD: OK`（世界重建）。
jar 时间戳 `2026-10-17 20:01`，晚于本批 `screen/*.java` / `build.gradle` 改动。

### 9.2 启动与「F：preflight-op 首次注入即成功」实机验证

本轮三次「干净启动」（全部：preflight 首次注入即 OK + PRECLEAN 自证 + 无需重注）：

```
# 201404 —— 服务 CC-OCCUPIED / CC-FILTER
MT_INFO: MT_TIMING: world-ready=43s（预算 180s）
MT_INFO: PREFLIGHT_OP_READY: chat-line
MT_INFO: PREFLIGHT_OP: hasPermissions(2)=1 level=4 src=cmdsource+profile required=2
MT_INFO: PREFLIGHT_OP: OK — has2=1 level=4 src=cmdsource+profile dump=rc=undefined
MT_INFO: PRECLEAN: OK — /kill @e[type=!player,distance=..128] ×2
MT_INFO: PRECLEAN_EVIDENCE: zh:killed=30
MT_LAUNCH: OK (1m02s) — 已进入世界（quickplay=testworld）

# 202041 —— 服务 CC-RIGHTCLICK / CC-DBLCLICK
MT_INFO: MT_TIMING: world-ready=44s（预算 180s）
MT_INFO: PREFLIGHT_OP_READY: chat-line
MT_INFO: PREFLIGHT_OP: OK — has2=1 level=4 src=cmdsource+profile dump=rc=undefined
MT_INFO: PRECLEAN: OK — /kill @e[type=!player,distance=..128] ×2
MT_INFO: PRECLEAN_EVIDENCE: zh:killed=8
MT_LAUNCH: OK (59s) — 已进入世界（quickplay=testworld）
```
（`mt_launch_20261017-201224.log` 同为 `MT_LAUNCH: OK (40s)`、`PREFLIGHT_OP: OK`、`PRECLEAN_EVIDENCE: zh:killed=12`。）

- **F 判定：首次注入即成功（注入尝试次数 = 1，零重注）**。判据：`PREFLIGHT_OP_READY: chat-line` 之后紧跟 `PREFLIGHT_OP: OK`，
  且当日**全部** `mt_launch` 日志中 `PREFLIGHT_OP_RETRY` 行数为 **0**（`grep -h PREFLIGHT_OP_RETRY temp/mt_detached/mt_launch_20261017-*.log | wc -l` → `0`）。
  对比上轮 §5-D2「4/4 失败 + 手工重注」——D4-1 的「等聊天就绪 + 注入后回读 + 重试」在本轮实机成立。
- ⚠️ 另两次启动（`mt_launch_20261017-200317` / `-200808`）在 **preflight-op 之后的** PRECLEAN / `doImmediateRespawn` 注入上失败
  （`PRECLEAN: WARN — 注入返回码 /`、`未确认「doImmediateRespawn=true」`）⇒ 启动闸门记 ERROR（客户端本身健康、已进世界）。
  为取得后续用例证据，本轮后续启动统一带 `MT_ALLOW_NO_IMMEDIATE_RESPAWN=1`（**本批无用例涉及死亡**；该开关只跳过该硬闸门，不降低其它前置）。
  该差异已登记为工具链问题 §9.8-D4-5。
- 启动同时记录：`SODIUM_LOADED/IRIS_LOADED/SHADERS=enabled/SLIMEGUARD/COLLECTIVE/CARPET=true`、`NOAI_ENFORCED=true`。

### 9.3 CC-FILTER（新口径，PASS）

用例：`scripts/test/cases/CC-FILTER-1.21.1.json`（战斗中会话 + 效果牌；左键按下 / 双击）。

```
AP_CCR:evt=click:step=24:btn=0:what=effect_left_press:rel=34,185:ret=1:carried=emptyx0
AP_CCR:step=30:phase=after_left_press:...:complete=0:filled=0:c0=empty:c1=empty:c2=empty:carried=empty:inv={0:...mediumx1,1:astral_dice:effect_card_berserkx1}
AP_CCR:step=56:phase=after_dbl:...:complete=0:filled=0:c0=empty:c1=empty:c2=empty:carried=empty:inv={...1:astral_dice:effect_card_berserkx1}
AP_CCR:INV_DUMP:inv={0:...mediumx1,1:astral_dice:effect_card_berserkx1}
```

- ① 左键按下：屏幕返回 `ret=1` 且**光标仍为空** ⇒ 按下即被吞、未发包；
- 「不进入拖动」：`absent AP_CCR:evt=drag:` PASS（本次会话零 `mouseDragged` 记录）；
- ② 双击（两次按下间隔 4t=200ms）：`filled` 恒 0、光标恒空 ⇒ 未走 `QUICK_MOVE`；
- 关屏后背包仍各 1 张（效果牌回到原槽 1）⇒ 不丢失、不复制；
- `absent AP_CARD_CHOICE: confirm:|reject:`、`absent AP_CC(R) 异常行` 均 PASS。

### 9.4 CC-RIGHTCLICK（新口径，PASS）

用例：`scripts/test/cases/CC-RIGHTCLICK-1.21.1.json`（效果牌在 hotbar.1、钻石 ×5 在 hotbar.3）。

```
AP_CCR:evt=click:step=24:btn=1:what=effect_right_press:rel=34,185:ret=1:carried=emptyx0
AP_CCR:step=30:phase=after_right_press:...:filled=0:c0=empty:c1=empty:c2=empty:carried=empty:inv={...1:effect_card_berserkx1,3:minecraft:diamondx5}
AP_CCR:evt=click:step=34:btn=2:what=effect_middle_press:rel=34,185:ret=1:carried=emptyx0
AP_CCR:step=40:phase=after_middle_press:...:filled=0:c0=empty:c1=empty:c2=empty:carried=empty
AP_CCR:evt=click:step=46:btn=0:what=diamond_pick:rel=70,185:ret=1:carried=minecraft:diamondx5
AP_CCR:step=64:phase=after_place_two:...:carried=minecraft:diamond:inv={1:astral_dice:effect_card_berserkx1,4:minecraft:diamondx2}
AP_CCR:step=78:phase=after_merge:...:carried=empty:inv={1:...berserkx1,4:minecraft:diamondx5}
```

- ① **右键按下**（btn=1）：`ret=1` + `carried=empty` ⇒ 不再出现「半栈上光标」（上轮 R1 的漏洞路径已封）；
- 创意模式**拾取键**（btn=2 = `keyPickItem` / `CLONE` 路径）：同样 `carried=empty`；
- ③ **有携带物时右键「放一个」**：拿起钻石 5 张后右键空格两次 ⇒ 目标格 `diamondx2`、光标剩 3（下一步日志 `carried=minecraft:diamondx3` 佐证），
  再左键并回 ⇒ 该格 `diamondx5`、光标清空 ⇒ 非卡物品操作未被误伤；
- 类型不符的效果牌始终留在原槽（`1:effect_card_berserkx1`），卡槽恒空。

### 9.5 CC-DBLCLICK（多牌栈，PASS）

用例：`scripts/test/cases/CC-DBLCLICK-1.21.1.json`（hotbar.0 = `attack_card_medium ×5`）。

```
AP_CCR:step=42:phase=after_dbl1:...:filled=1:c0=astral_dice:attack_card_medium:c1=empty:c2=empty:carried=empty:inv={0:astral_dice:attack_card_mediumx4}
AP_CCR:step=70:phase=after_dbl2:...:filled=2:c0=...medium:c1=...medium:c2=empty:carried=empty:inv={0:astral_dice:attack_card_mediumx3}
AP_CCR:step=96:phase=after_dbl_back:...:filled=1:c0=empty:c1=astral_dice:attack_card_medium:c2=empty:carried=empty:inv={0:astral_dice:attack_card_mediumx4}
AP_CCR:SCREEN:closed:...:inv={0:astral_dice:attack_card_mediumx5}
AP_CCR:INV_DUMP:inv={0:astral_dice:attack_card_mediumx5}
```

- **双击一次只搬 1 张**：5 → 4 留原槽（`x4`），第 1 格 1 张；**未出现整栈填满**（`absent filled=3` PASS）；
- 第二次双击填第 2 格（`x3` + 两格各 1）；
- 双击卡槽取回：卡槽 1 张回背包（`c0=empty`、`c1=medium`、背包 `x4`）；
- 关屏：卡槽余量退回背包 ⇒ 总数守恒 `x5`（无丢失/复制）。

### 9.6 CC-OCCUPIED（§9-7 复测，PASS）

用例：`scripts/test/cases/CC-OCCUPIED-1.21.1.json`（hotbar.5 = 钻石 ×1 作占用格）。

```
AP_CCR:step=44:phase=card_in_slot:...:c0=astral_dice:attack_card_medium
AP_CCR:step=74:phase=after_occupied_drop:...:c0=astral_dice:attack_card_medium:c1=empty:c2=empty:carried=empty:inv={...5:minecraft:diamondx1}
```

断言（`case-CC-OCCUPIED-c.log` 第 15–17 条）另含 `absent 5:astral_dice:attack_card_mediumx1`（确认没有换位）
⇒ `canAcceptCarried` 预判分支生效、未被本批其它改动误伤。

### 9.7 J5：未安装 Jade 启动（PASS）

**启动方式**（mt 工具链不透传 `-P`，直接起 dev 客户端）：

```
gradlew.bat :neoforge-1.21.1:runClient -PtestJade=false -Pquickplay=testworld --console=plain
```

**静态依赖对照**（`temp/ccregress-20261017/deps-*.txt`）：

```
$ gradlew :neoforge-1.21.1:dependencies --configuration runtimeClasspath -PtestJade=false | grep -c jade
0
$ gradlew :neoforge-1.21.1:dependencies --configuration runtimeClasspath | grep -i jade
\--- maven.modrinth:jade:eYz2YBGT
```

**运行期（`temp/ccregress-20261017/logs-nojade/latest.log`）**：

```
[20:28:34] ModDiscoverer:  Mod List:
        Architectury 13.0.11 ... Astral Dice 2.0.0-SNAPSHOT.128+neoforge_1.21.1 ... Sodium 0.8.13 ...
        （27 行，grep -ci jade = 0；对照组同位置为 `Jade 15.10.6+neoforge (jade)`）
[20:28:47.259] [Render thread/INFO] [minecraft.advancements.AdvancementTree]: Loaded 1437 advancements
[20:28:48.398] [Server thread/INFO] [minecraft.server.players.PlayerList]: Dev[local:E:3b7dff2d] logged in with entity id 8 at (3.5, -60.0, -1.5)   ← 正常进入世界
logged in with entity id：1 次
```

- **mod list 无 jade**：Mod List 块 27 行、`grep -ci jade` = **0**；对照（默认依赖）同块含 `Jade 15.10.6+neoforge (jade)`；
- **日志无 Jade 相关报错**：`ERROR|FATAL` = **0**、`crash-reports` = **0**；KubeJS `server.log` 0 errors；
- 仅有的 jade 相关行是**预期 WARN**（`astral_dice.jade.mixins.json` 为 `"required": false`，缺目标类时跳过）：
  ```
  [main/WARN] [mixin/]: Error loading class: snownee/jade/addon/vanilla/EntityHealthAndArmorProvider (ClassNotFoundException)
  [main/WARN] [mixin/]: @Mixin target snownee.jade.addon.vanilla.EntityHealthAndArmorProvider was not found astral_dice.jade.mixins.json:ArmorValueProviderMixin
  [main/WARN] [mixin/]: Error loading class: snownee/jade/overlay/DisplayHelper (ClassNotFoundException)
  [main/WARN] [mixin/]: @Mixin target snownee.jade.overlay.DisplayHelper was not found astral_dice.jade.mixins.json:DisplayHelperArmorIconMixin
  ```
  另有 `jade (version 15.10.6+neoforge -> MISSING)` 一行，属模组可选依赖提示，不是异常。
- 新增用例 `scripts/test/cases/JADE-NOJADE-1.21.1.json`（`MT_VALIDATE: OK`）：**启动之后**的运行期冒烟（`/astralprobe opprobe` 可用 + 无 crash/KubeJS/Mixin 失败）。
  ⚠️ 其断言窗口自用例起算，**看不到启动期的 Mod List 行** ⇒ 「mod list 无 jade」以本节的启动日志为权威证据（该用例的 note 已写明）。
- ⚠️ 本轮试过用客户端 KubeJS 探针读 `ModList#isLoaded("jade")`，被 KubeJS 类过滤器拒绝
  （`InternalError: Failed to load Java class 'net.neoforged.fml.ModList': Class is not allowed by class filter`），
  故运行期读数改用 NeoForge 自己的 Mod List 输出；探针已随收尾删除（`temp/ccregress-20261017/probe-backup/`）。

### 9.8 工具链发现（本轮实测，非产品缺陷）

#### D4-3（已修，测试工具链）：双线并行时 `Find-MtMinecraftWindow -Version` 无法确定本线窗口

- 现象：另一条线（`forge-1.20.1`）客户端同时在跑时，本线**全部** `inject_command` 报
  `MT_INJECT: ERROR — 未能唯一确定本版本 Minecraft 窗口（存在多个候选客户端）`（`case-CC-FILTER-a.log`）。
- 根因：本线 dev 客户端的 java 命令行是 DevLaunch 短形式（实测 `net.caffeinemc.sodium / net.minecraft.client.main.Main /`，不含子项目名/run 目录），
  只能走「标题含版本号的唯一候选」回退；旧代码额外要求**全局候选数 == 1** ⇒ 双线必然返回 0。
- 修法（`scripts/test/lib/Mt.Win32.psm1`，仅测试工具链）：回退判据改为**按标题里的版本号**做唯一性判定（`$titleHit.Count -eq 1` 即接受；同版本出现 2 个窗口仍返回 0，不猜）。
- 验证：`Find-MtMinecraftWindow -Version 1.21.1` → `1245682`（修前 `0`）；窗口枚举原样两份：`Minecraft NeoForge* 1.21.1` / `Minecraft* Forge 1.20.1`。
  **该改动未提交，需评审。**

#### D4-4（未修，环境/工具链）：两条线的 dev 客户端**不能共存**——同时运行时互相以 glfw.dll 原生崩溃退出

- 本轮实测崩溃对（`EXCEPTION_ACCESS_VIOLATION ... glfw.dll+0x101ab`，Render thread `Minecraft.runTick`）：
  `run/1.21.1/hs_err_pid{8820,20236,40132,8240}.log` 与 `run/1.20.1/hs_err_pid{38676,9180,23884,15540}.log` 的时间戳**同步**
  （20:06 / 20:11 / 20:13 / 20:18），两侧同刻死亡。
- 影响：`LAUNCH_LOCK` 只覆盖「spawn → 进世界」；**已进世界的客户端仍会被另一条线的启动杀死**，用例阶段随之 `USER_TERMINATED`。
  本轮因此出现 **7 次** `USER_TERMINATED`（b 批 4 次 / d 批 2 次 / e 批 1 次，`case-*-{b,d,e}.log`），
  最终在**双方都不在启动**的窗口内一次性跑完 4 条用例。
- 结论：并行测试的物理约束比 AGENTS 现行「短时启动锁」更强，建议后续把「启动锁」升级为**会话级互斥**或分机执行（待裁决，本轮未改）。

#### D4-5（未修，工具链）：从 WSL 起 `pwsh.exe` 时，`& <exe>` 子进程的 stdout / 退出码丢失

- 现象：`mt_launch` 里 `& $psExe ...` 的 `$LASTEXITCODE` 为 `$null`（日志显示 `注入返回码 /`），
  且从 WSL 直接 `& pwsh -File …` 完全拿不到子进程输出（最小复现：`temp/ccregress-20261017/probe_lastrc.ps1` → `lastrc=[] type=null`，连 `cmd.exe /c exit 9` 也读不到）。
  经 `cmd.exe`/`.bat` + **文件重定向**启动时恢复正常（`launch_nogamerule.bat`）。
- 影响：WSL 环境下 `--phase launch` 的 PRECLEAN / `doImmediateRespawn` 闸门会误报失败（命令实际可能已送达）；
  preflight-op 闸门因改为「回读日志」不受影响（本轮 0 次重试）。
- 应对（本轮做法）：所有 mt 调用改为 `cmd.exe /c <bat> > <文件>` 形态；**未改产品/工具链代码**。

### 9.9 收尾

```
$ pwsh -NoProfile -File scripts/test/mt_cleanup.ps1 run --version 1.21.1 --purge-saves
MT_CLEANUP: 指定了 --version，保留 Gradle 守护进程
MT_CLEANUP_SAVES: PURGED 0
MT_CLEANUP: 已收停 0 个本流程进程（范围：1.21.1）
MT_CLEANUP_RESULT: OK
```

- `run/1.21.1/saves/` 实测为空（`ls | wc -l` = 0；`PURGED 0` 是因为前一条 `--phase stop --purge-saves` 已清过）。
- **无 1.21.1 客户端残留**（`Get-CimInstance ... | match 'net.minecraft.client.main.Main|devlaunch'` 为空）；Gradle 守护进程按规则保留。
- 临时探针已从两侧删除：`scripts/test/resources/kubejs/1.21.1/{server_scripts,client_scripts}/astral_ccr_*.js`、`astral_jade_probe.js`
  与 `run/1.21.1/kubejs/**` 同名副本，备份见 `temp/ccregress-20261017/probe-backup/`。
- 日志快照：`temp/ccregress-20261017/latest-before-nojade.log` = CC-RIGHTCLICK / CC-DBLCLICK 那一代（49 行 `AP_CCR:*`，
  含 `step=42/64/96`、两条 `INV_DUMP`）；`temp/ccregress-20261017/logs-nojade/latest.log` = 无 Jade 那一代。
  CC-FILTER / CC-OCCUPIED 的原始行未单独留存（下一次 `mt_launch` 会删除 `latest.log`），
  其逐字证据见各自 `case-CC-FILTER-c.log` / `case-CC-OCCUPIED-c.log` 的 `MT_ASSERT_LOG` 命中片段。

### 9.10 证据索引（`temp/`，不入库）

| 路径 | 内容 |
|---|---|
| `temp/ccregress-20261017/case-CC-FILTER-c.log` | CC-FILTER 25 条断言全文（PASS） |
| `temp/ccregress-20261017/case-CC-RIGHTCLICK-f.log` | CC-RIGHTCLICK 27 条断言全文（PASS） |
| `temp/ccregress-20261017/case-CC-DBLCLICK-f.log` | CC-DBLCLICK 24 条断言全文（PASS，5 张牌栈） |
| `temp/ccregress-20261017/case-CC-OCCUPIED-c.log` | CC-OCCUPIED 断言全文（PASS） |
| `temp/ccregress-20261017/case-CC-*-{a,b,d,e}.log` | 崩溃/窗口冲突导致的 `USER_TERMINATED` 现场（D4-3 / D4-4 证据） |
| `temp/ccregress-20261017/logs-nojade/{latest,debug}.log` | 无 Jade 启动的完整日志（Mod List / 世界进入 / mixin WARN） |
| `temp/ccregress-20261017/deps-{nojade,jade}.txt` | `runtimeClasspath` 依赖对照（0 处 vs `maven.modrinth:jade:eYz2YBGT`） |
| `temp/ccregress-20261017/launch_nogamerule.bat` / `launch3.log` | 带 `MT_ALLOW_NO_IMMEDIATE_RESPAWN=1` 的启动封装与输出 |
| `temp/ccregress-20261017/build.log` / `env2.log` / `final-stop.log` | 构建 / 环境 / 收尾 |
| `temp/ccregress-20261017/probe-backup/` | 三支临时探针原始副本（复跑时放回 + `mt_env.ps1 kubejs` + 冷启动） |
