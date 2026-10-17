# 选卡界面 + Jade 商店冷却倒计时 · 实机验证报告（forge-1.20.1）

- **线 / 加载器 / MC**：`forge-1.20.1` / Forge 47.4.10 / Minecraft 1.20.1（Java 17）
- **日期**：2026-10-17（实机时段 18:35–18:50）
- **分支 / HEAD**：`multi-dev-next` / `f7ee13e0`（工作区版本 `2.0.0-SNAPSHOT.128+forge_1.20.1`）
- **用例正本**：`docs/features/card-choice-screen-spec.md` §9（8 条） · `docs/features/jade-shop-cooldown-spec.md` §5（5 条）
- **证据目录（不入库）**：`temp/cc-forge-20261017/`（`latest.log` / `kubejs_client.log` / `kubejs_server.log` / `shots/`）
- **报告口径**：只测 forge-1.20.1 一线；发现缺陷只取证、未修改任何产品源码。

## 0. 结论摘要

| # | 用例（spec 条目） | 判定 | 关键证据 |
|---|---|---|---|
| C1 | 双击放入 / 双击取回（§9-1） | **PASS**（附偏差 D1） | `AP_CCJ:scen=1:evt=s1:done:verdict=[dbl_put=1,dbl_take=1]`（latest.log:3862） |
| C2 | 拖动放入 / 拖回物品栏（§9-2、§9-7 前半） | **PASS** | `verdict=[drag_in=1,drag_out=1]`（latest.log:6967） |
| C3 | 类型过滤：效果牌拖入战斗会话（§9-3） | **拒绝 PASS / 原地还原 FAIL**（D2） | `drag_released:...:carried=3`（6089）；`verdict=[filter_drag_not_in_slot=1,filter_drag_restored=0,filter_dbl_not_in_slot=1]`（6096） |
| C4 | 未满无按钮 + 放满出现按钮（§9-4） | **PASS** | `verdict=[no_btn_early=1,btn_full_render=1]`（7448、14932）；`btnpx=diff=1118/1122` |
| C5 | 放满确认：关闭 + 各消耗 1 张 + 回调（§9-5） | **PASS** | `AP_CARD_CHOICE: confirm:1500:...:picked=3`（7833）；`invMed=3`（7874） |
| C6 | Esc 零消耗、无回调（§9-6） | **PASS** | `abandon:1600:player=Dev:consumed=0`（8914）；关闭后 `invMed=6`（8917） |
| C7 | 拖到已占用（不同物品）格 ⇒ 原路还原（§9-7 后半） | **PASS** | `verdict=[...occ_restore=1]`（6967） |
| C8 | 开界面死亡掉牌 + 重生后可再开（§9-8） | **PASS** | `death_cleanup:1800`（9270）+ `abandon:...consumed=0`（9271）+ `AP_D8_GROUND:n=1:items=[...x2]`（9691）+ `open:1900`（9342） |
| J1 | 卡牌店补货倒计时逐秒递减、到 0 消失（§5-1） | **PASS** | `补货倒计时 0:52→0:51→0:50`（10467/10468/10499）；`n=3→n=2`（10842） |
| J2 | 筹码店可进入倒计时 + 空白筹码清零即消失（§5-2） | **PASS** | `可进入倒计时 4:48→4:47`（11265）；`CHIP_SHOP_BLANK_INSERT`（11533）→ `n=2`（11534） |
| J3 | 抽奖盒可进入倒计时（§5-3） | **PASS** | `可进入倒计时 4:50→4:49`（11896/11897） |
| J4 | 无冷却时三块均无该行（§5-4） | **PASS** | `卡牌商店|` / `筹码商店|` / `抽奖盒|`（12340 / 12471 / 12602） |
| J5 | 未装 Jade 正常启动（§5-5） | **NOT EXECUTED**（构建无开关，见 §6-D5） | 静态证据：`mods.toml` 无 jade 依赖；`astral_dice.jade.mixins.json: "required": false` |

**综合**：§9 的 8 条用例中 **7 条 PASS、1 条部分失败（C3「原地还原」）**；§5 的 4 条可执行项 **全部 PASS**，第 5 条因构建配置无「不装 Jade」开关而**未执行**（未视为失败）。

## 1. 环境与前置

### 1.1 构建与部署

```
===== MT_PHASE: build (1.20.1) =====
MT_BUILD: OK (13s) — 输出含 BUILD SUCCESSFUL 且产物存在（时间戳未变）：astral_dice-2.0.0-SNAPSHOT.128+forge_1.20.1.jar
pushToDevRun: pushed astral_dice-2.0.0-SNAPSHOT.128+forge_1.20.1.jar -> ...\run\1.20.1\mods
```

- 版本自检：`run/1.20.1/mods/astral_dice-2.0.0-SNAPSHOT.128+forge_1.20.1.jar`（mtime 18:07 本轮构建）。
- 启动闸门全绿：`EMBEDDIUM/OCULUS/FERRITECORE/SLIMEGUARD/COLLECTIVE/CARPET = true`、`NOAI_ENFORCED=true`、`SHADERPACK_LOADED=true`。

### 1.2 首次启动失败（环境原因，非产品缺陷）

第一次 `--phase launch` 在 `MT_preflight-op` 报 ERROR（`未取到 AP_OP_PERM 读数（注入返回码 /）`），随后客户端被收停。

取证：

- `run/1.20.1/hs_err_pid3796.log`（18:20）——`EXCEPTION_ACCESS_VIOLATION at pc=... Problematic frame: C [glfw.dll+0xf8ab]`；
  同一签名在 `hs_err_pid30148.log`（2026-10-08）也出现过，属本机既有 GLFW 原生崩溃。
- 该次崩溃发生在 **另一条线（neoforge-1.21.1）的第二个客户端同时启动** 的时刻（对方 `mt_launch` 日志 `mt_launch_20261017-181956.log` 与之同秒），两个 runClient 争用同一 GPU/GL 上下文。
- 复跑（无第二个客户端）后 `MT_preflight-op` 仍误报一次，原因是两次注入被合并成一条聊天消息（`<Dev> t/astralprobe opprobe/astralprobe opprobe`），随后手工单次注入即成功：

```
[CHAT] AP_OP_PERM:has2=1:level=4:src=cmdsource+profile:dump=rc=1
```

> 结论：工具链本身可用；失败源于「同一时刻两个客户端 + 世界刚加载时注入通道丢键」。本报告后续所有读数均在**单客户端独占**下取得。

### 1.3 并发约束（如实登记）

`mt_launch.ps1` 的防撞预检是**全机**判定（不分版本）：只要存在任一 MC 客户端就 `BLOCKED`。因此本线与 1.21.1 线**无法真正并行**；本轮先后经历 3 次等待/重试（17:20–18:35）才取得独占窗口。

## 2. 方法与探针（已全部删除）

- 服务端探针 `astral_ccj_probe.js`（临时）：`/astralcc open <slots> <type> <token>` 走产品 `CardChoiceManager.start(...)`；
  `cd shop|chip|event <end>` 走产品 setter / `ChipShopState.store` 写冷却截止时刻（与生产写路径同源，`AttachedDataKey#set` 对 synced 键立即下发 S2C）；
  `setup`/`aim` 摆三块商店并 tp 到固定位姿；`sess`/`inv`/`ground` 只读取证。
- 客户端探针 `astral_ccj_client.js`（临时）：按 `token/100%10` 取场景号，逐 tick 调
  `CardChoiceScreen#mouseClicked/mouseDragged/mouseReleased/keyPressed`（真实输入入口）驱动手势；
  Jade 侧每 5 tick 读 `WailaTickHandler.instance().tooltipRenderer.getTooltip().getMessage()`
  （= Jade 实际渲染的行文本），仅在准星命中三个商店方块时落行。
- 拖动一律用**跨 4 tick**（按下 → 升格 → 落到目标 → 松手）的真实时序；单 tick 连发会与 `getCarried()` 的 S2C 更新竞态，
  产生非产品行为（对比数据见 §5-D2 备注）。
- 探针在收尾已从 `scripts/test/resources/kubejs/1.20.1/**` 与 `run/1.20.1/kubejs/**` 两侧删除（见 §7）。

## 3. §9 选卡界面逐条证据

所有引用的行号均为 `temp/cc-forge-20261017/latest.log`（= 本轮 `run/1.20.1/logs/latest.log` 的副本）。

### C1 双击放入 / 双击取回（§9-1）— PASS（附偏差 D1）

```
3857 AP_CCJ:scen=1:evt=s1:after_put:...:filled=3:complete=1:...:s0=attack_card_medium:s1=...:s2=...:invMed=3:totMed=6:put_ok=1:put_filled=3
3860 AP_CCJ:scen=1:evt=s1:after_take:...:filled=2:...:s0=empty:s1=...:s2=...:invMed=4:totMed=6:take_ok=1
3862 AP_CCJ:scen=1:evt=s1:done:verdict=[dbl_put=1,dbl_take=1]
```

- 双击物品栏卡牌 → 卡槽出现卡牌（`dbl_put=1`）；双击卡框 0 → 该卡回到物品栏（`s0=empty`，`totMed` 不变）。
- **偏差 D1**：一次双击把 3 张分别放进 3 个卡槽（`put_filled=3`），与 §9-1 写的「双击一次 → 卡槽出现 **1 张**」不符；
  机制为 `CardChoiceMenu#quickMoveStack` 的 `moveItemStackTo(stack, 0, slotCount, false)`（虚拟槽 `getMaxStackSize()=1`，
  于是一次 QUICK_MOVE 依次填满全部空槽）。属**行为与 spec 文本不一致**，未见崩溃或数据异常。

### C2 拖动放入 / 拖回物品栏（§9-2、§9-7 前半）— PASS

```
6967 AP_CCJ:scen=2:evt=s2:done:verdict=[drag_in=1,drag_out=1,blank_restore=1,occ_restore=1]
```

四个阶段逐一核对（每次拖动后光标携带物均被清空 / 由探针 settle 归位）：

| 阶段 | 动作 | 结果 |
|---|---|---|
| A | 物品栏 → 卡框 0 | `filled=1`、`carried=empty`（1 张入槽，余量归位） |
| B | 卡框 0 → 空物品栏格 | `s0=empty`、`invMed` 回到基线 |
| C | 物品栏 → 屏幕空白处 | 原路还原（`invMed=6`、卡槽为空、`carried=0`） |
| D | 物品栏 → 被 `minecraft:stone` 占用的格子 | 原路还原、**不换位**（`stone` 仍在原格、`invMed=6`） |

### C3 类型过滤（§9-3）— 拒绝 PASS / **原地还原 FAIL**

```
6089 AP_CCJ:scen=3:evt=drag_released:s3_filter:ret=1:active=1:carried=3:...:carried=effect_card_berserkx3:...:invEff=0
6096 AP_CCJ:scen=3:evt=s3:done:verdict=[filter_drag_not_in_slot=1,filter_drag_restored=0,filter_dbl_not_in_slot=1]
```

- **拒绝**正确：效果牌没有进入任何一个战斗卡槽（`not_in_slot=1`）。
- **原地还原失败**：松手后牌仍挂在光标上（`carried=effect_card_berserkx3`、`invEff=0`），没有自动回到原格；
  探针不得不额外点一次空物品栏格把它放下（`evt=s3:settleA:settle:...:slot=5`）。
- 复现两次（单 tick 连发 18:39、跨 4 tick 真实时序 18:41），结果一致 ⇒ 非探针时序偶然。
- 对照组：同一套「跨 4 tick」拖动在**落空 / 占用格**两种拒绝下都能正确还原（C2 的 `blank_restore=1/occ_restore=1`，`carried=0`）
  ⇒ 问题只出现在「落点是**卡槽**且类型不符」这条分支。
- 双击效果牌：也不进入卡槽（`filter_dbl_not_in_slot=1`），但第一次点击被交给原版 `PICKUP`，牌同样停在光标上（设计使然，非缺陷）。

### C4 未满无按钮 / 放满出现按钮（§9-4）— PASS

```
7448  AP_CCJ:scen=4:evt=s4:done:verdict=[no_btn_early=1,btn_full_render=1]      ← 首轮
14932 AP_CCJ:scen=4:evt=s4:done:verdict=[no_btn_early=1,btn_full_render=1]      ← 复跑（含截图）
14900 ...:s4:full:...:filled=3:complete=1:...:btnpx=diff=927/1122:btn_render=1:rect=908,520,1010,564
```

- 1/3 时点击确认按钮区域：界面不关、`filled` 仍为 1、无 `confirm`/`abandon`（`no_btn_early=1`）。
- 像素判据：按钮矩形（GUI (62,90,51,22)，scale=2 ⇒ `908,520,1010,564`）在 1/3 与 3/3 两帧的差异像素
  `927/1122`（另一轮 `1118/1122`）⇒ 按钮确实只在放满后渲染。
- 视觉：`temp/cc-forge-20261017/shots/cardchoice-1of3-20261017.png`（无按钮）与 `cardchoice-full-20261017.png`（有按钮）。

### C5 放满确认（§9-5）— PASS

```
7830 AP_CCJ:scen=5:evt=s5:picked_state:...:filled=3:complete=1:...:tot=6
7833 AP_CARD_CHOICE: confirm:1500:player=Dev:slots=3:type=COMBAT:picked=3
7874 AP_CCJ:evt=closed:scen=5:invMed=3:invEff=3:verdict=[fill3=1]
```

- 界面关闭、各槽消耗 1 张（`totMed 6 → 3`：3 张留在物品栏、3 张被消耗）、服务端日志 `picked=3` 与 spec 判据一致。
- 附加观察（探针侧问题，非产品）：`AP_CARD_CHOICE: handler_failed:token=1500`（7834）——
  `CardChoiceManager` 确实调用了回调，异常来自探针回调内部
  `WrappedException: IllegalAccessException: ... cannot access a member of class java.util.ImmutableCollections$ListN`
  （`picked.get(i)`，即 AGENTS 踩坑 19 的 1.20.1 Rhino 限制）⇒ **回调内容未取到机器读数**，仅由 `picked=3` + 消耗量佐证。

### C6 Esc 零消耗（§9-6）— PASS

```
8913 (before_esc) filled=2:...:s0=medium:s1=medium:s2=empty:invMed=4:totMed=6
8914 AP_CARD_CHOICE: abandon:1600:player=Dev:consumed=0
8917 AP_CCJ:evt=closed:scen=6:invMed=6:invEff=3:verdict=[]
```

- Esc 走 `Screen#keyPressed(256)` → `onClose()` → `player.closeContainer()` 的真实链路；
- 2 张全部退回（`invMed 4 → 6`）、`consumed=0`、无 `confirm` 行、无回调 ⇒ 零消耗、零回调。

### C7 拖到已占用格原路还原（§9-7 后半）— PASS

见 C2 阶段 D：`occ_restore=1`，且 `stone` 仍在该格（未被交换）。

### C8 开着界面死亡（§9-8）— PASS

```
9268 (before_death) filled=2:...:invMed=4:totMed=6
9270 AP_CARD_CHOICE: death_cleanup:1800:player=Dev
9271 AP_CARD_CHOICE: abandon:1800:player=Dev:consumed=0
9691 AP_D8_GROUND:r=64:n=1:items=[astral_dice:attack_card_mediumx2]:ex=-    ← 死亡点 1 个 ItemEntity，内含 2 张卡
9342 AP_CARD_CHOICE: open:1900:player=Dev:slots=3:type=COMBAT              ← 重生后重开成功
9345 AP_CCJ:scen=9:evt=s9:opened:...:post_death_open=1
```

- 界面随死亡关闭、2 张卡**掉在死亡点**（可拾回）、无回调、日志出现 `death_cleanup` + `abandon:...consumed=0`；
- 重生后立即再次 `CardChoiceManager.start(...)` **成功**，无 `reject:reason=already_open`。

## 4. §5 Jade 倒计时逐条证据

读数 = Jade 自身的 `Tooltip#getMessage()`（即悬浮框实际渲染的文本），`n=` 为行数。

### J1 卡牌商店「补货倒计时」逐秒递减 + 到 0 消失（§5-1）— PASS

```
10467 AP_CCJ:evt=jade:look=astral_dice:card_shop:n=3:tip=卡牌商店|补货倒计时 0:52|
10468 AP_CCJ:evt=jade:look=astral_dice:card_shop:n=3:tip=卡牌商店|补货倒计时 0:51|
10499 AP_CCJ:evt=jade:look=astral_dice:card_shop:n=3:tip=卡牌商店|补货倒计时 0:50|
...   （逐秒 -1，一直降到 0:31）
10842 AP_CCJ:evt=jade:look=astral_dice:card_shop:n=2:tip=卡牌商店|          ← 到期后该行消失
```

视觉：`temp/cc-forge-20261017/shots/jade-card-cd_20260921-191522.png`（三行：卡牌商店 / 补货倒计时 0:16 / Astral Dice）。

### J2 筹码商店「可进入倒计时」+ 空白筹码清零（§5-2）— PASS

```
11265 AP_CCJ:evt=jade:look=astral_dice:chip_shop:n=3:tip=筹码商店|可进入倒计时 4:48|
11266 ...（逐秒递减 4:47 / 4:46 / 4:45 / 4:44 / 4:43）
11533 [astral_dice] CHIP_SHOP_BLANK_INSERT player=Dev refreshes=4 now=12517
11534 AP_CCJ:evt=jade:look=astral_dice:chip_shop:n=2:tip=筹码商店|        ← 投入空白筹码后该行立即消失
```

（空白筹码经真实右键交互触发：`/item replace entity @s weapon.mainhand with astral_dice:blank_chip` → `mt_inject mouse -Button right`。）

### J3 抽奖盒「可进入倒计时」（§5-3）— PASS

```
11896 AP_CCJ:evt=jade:look=astral_dice:event_drawer:n=3:tip=抽奖盒|可进入倒计时 4:50|
11897 AP_CCJ:evt=jade:look=astral_dice:event_drawer:n=3:tip=抽奖盒|可进入倒计时 4:49|
```

### J4 无冷却时三块都没有该行（§5-4）— PASS

清零三项冷却后依次看向三块：

```
12340 AP_CCJ:evt=jade:look=astral_dice:card_shop:n=2:tip=卡牌商店|
12471 AP_CCJ:evt=jade:look=astral_dice:chip_shop:n=2:tip=筹码商店|
12602 AP_CCJ:evt=jade:look=astral_dice:event_drawer:n=2:tip=抽奖盒|
```

视觉对照：`shots/jade-card-shop_20260921-191522.png`（无倒计时行，2 行）。

## 5. 偏差与缺陷（只取证，未修）

### D1（行为 vs spec 文本）一次双击填满全部空卡槽

- 现象：3 槽会话下，一次双击 = 3 张牌分别进入 3 个卡槽（`put_filled=3`）。
- 位置：`forge-1.20.1/.../screen/CardChoiceMenu.java#quickMoveStack` → `moveItemStackTo(stack, 0, slotCount, false)`。
- 影响：与 `card-choice-screen-spec.md` §9-1「双击一次 → 卡槽出现 1 张」不符；对玩家是「一下子填满 3 格」。
- 判定：**未造成数据错误**（`totMed` 恒定、可双击取回），登记为 spec/实现口径差异，等待用户裁决是否属于预期（原版 shift-click 语义）。

### D2（缺陷候选，P2）类型不符的拖动落点：拒绝生效但**不原地还原**

- 现象：把效果牌拖到「战斗牌」会话的卡槽并松手 → 未进入卡槽（正确），但卡牌**留在光标上**（`carried=effect_card_berserkx3`、`invEff=0`），
  需要玩家再点一次物品栏格才归位。
- 复现：18:39（单 tick 连发）与 18:41（跨 4 tick 真实时序）两次一致；`mouseReleased` 返回 `true`、窗口 `active=1`（排除输入被吞）。
- 对照：同样时序下「落空」「落到被不同物品占用的格子」都能正确还原（`carried=0`）⇒ 失败集中在
  `CardChoiceScreen#finishDrag` 的「落点是卡槽且 `type.matches` 为假」分支。
- 影响面：玩家操作多一步、且容易把牌误落到别处（不丢失）。

### D3（探针侧，非产品）回调内容无法机器读取

- `handler_failed:token=1500` 的堆栈是 `Rhino MemberBox cannot access ... ImmutableCollections$ListN`，
  由探针回调里的 `picked.get(i)` 触发（产品传的是 `List.copyOf(...)`）。改用 `iterator()` 的修法需**冷启动**才生效（本轮未重启）。
- 因此「回调拿到的 3 个 ItemStack」只有 `picked=3` + 消耗量两条间接证据，**没有逐 id 对账**。

### D4（环境）并发启动第二个客户端触发 GLFW 原生崩溃

见 §1.2；`hs_err_pid3796.log`（18:20，`glfw.dll+0xf8ab`）。与产品无关，但会让 `--phase launch` 的 preflight 误报。

### D5（未执行）「未安装 Jade 正常启动」

- `mt_launch`/`build.gradle` 没有「不装 Jade」的开关：`forge-1.20.1/build.gradle:280` 是无条件
  `modImplementation "maven.modrinth:jade:xJQHCmWJ"`，dev 运行必然加载 Jade ⇒ 本轮**无法**构造缺席环境（不改动构建脚本的前提下）。
- 静态证据（支持「缺席安全」）：
  - `forge-1.20.1/src/main/templates/META-INF/mods.toml` **没有** jade 依赖项（仅第 62 行登记 mixin 配置）；
  - `astral_dice.jade.mixins.json`：`"required": false`（配合 `MixinRuntimeGate` 门控）；
  - 产物 486 个 class 中仅 4 个引用 `snownee/jade`：`compat/jade/{AstralDiceJadePlugin,ShopCooldownClientProvider}`（只由 Jade 反射加载）
    与 `mixin/jade/{ArmorValueProviderMixin,ArmorElementIconMixin}`（`required:false` 门控）。

## 6. 未执行 / 欠账

| 项 | 原因 | 触发条件 |
|---|---|---|
| §5-5 未装 Jade 启动 | 构建脚本无开关（D5） | 需要用户决定是否临时去掉 Jade 依赖后单独冷启动一轮 |
| C5 回调逐 id 对账 | 探针回调 Rhino 限制（D3） | 下次冷启动后复查（改 `iterator()` 即可） |
| 1.21.1 / 26.1.2 / fabric 线 | 本批只测 forge-1.20.1 | 由其它批次/代理负责 |

## 7. 收尾

- 临时探针已删除（两侧）：
  - `scripts/test/resources/kubejs/1.20.1/server_scripts/astral_ccj_probe.js`（removed）
  - `scripts/test/resources/kubejs/1.20.1/client_scripts/astral_ccj_client.js`（removed）
  - `run/1.20.1/kubejs/server_scripts/astral_ccj_probe.js`、`run/1.20.1/kubejs/client_scripts/astral_ccj_client.js`（removed）
- 收停 + 清档：

```
MT_CLEANUP: 指定了 --version，保留 Gradle 守护进程
MT_CLEANUP_SAVES: PURGED 0
MT_CLEANUP: 已收停 0 个本流程进程（范围：1.20.1）
MT_CLEANUP_RESULT: OK
```

（`PURGED 0` 是因为前一条 `mt.ps1 --phase stop --purge-saves` 已先完成清档；`run/1.20.1/saves` 现已不存在。
 本流程 java 客户端残留数 = 0；Gradle 守护按规则保留。）

- 未执行 `git commit` / `git push`。

### `git status --short -uall` 原文

```
?? %SystemDrive%/ProgramData/Microsoft/Windows/Caches/cversions.2.db
?? %SystemDrive%/ProgramData/Microsoft/Windows/Caches/{1FFBA86B-CAE4-4136-B1CE-4114B4F0E200}.2.ver0x0000000000000001.db
?? %SystemDrive%/ProgramData/Microsoft/Windows/Caches/{6AF0698E-D558-4F6E-9B3C-3716689AF493}.2.ver0x0000000000000001.db
?? %SystemDrive%/ProgramData/Microsoft/Windows/Caches/{DDF571F2-BE98-426D-8288-1A9A39C3FDA2}.2.ver0x0000000000000001.db
?? docs/reports/card-choice-jade-2026-10-17-2.0.0-SNAPSHOT.128.md
?? docs/reports/card-choice-jade-forge-1.20.1-2026-10-17-2.0.0-SNAPSHOT.128.md
?? "models/强化拒止 - Converted.bbmodel"
?? "models/路障 - Converted.bbmodel"
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
```

> 说明：上表中 `%SystemDrive%` 4 行与 `models/*.bbmodel` 2 行是既有未跟踪残留（按规则不动）；
> `docs/reports/card-choice-jade-2026-10-17-2.0.0-SNAPSHOT.128.md` 与 11 个 `*-1.21.1.json` 属**并行的 1.21.1 代理**产出，本线未触碰；
> `docs/reports/card-choice-jade-forge-1.20.1-2026-10-17-2.0.0-SNAPSHOT.128.md` 即本报告（未提交）。
> 本线自身的探针/用例文件**已全部删除**，上表无 `astral_ccj_*` 残留。
