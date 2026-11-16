# Step 2：AutoJS 输入与截图通道（2026-11-05）

> 承接 [autojs-command-channel-step1.md](autojs-command-channel-step1.md)。Step 1 把 {BT}inject_command{BT} 从键鼠注入改成 AutoJS 客户端 {BT}sendCommand{BT}；Step 2 把剩下的 {BT}inject_key{BT} / {BT}inject_mouse{BT} / {BT}screenshot{BT} 也挪到 AutoJS，于是整套用例在**不占键鼠、不需要窗口焦点**的前提下可跑。

## 1. 为什么必须做这一步

2026-11-05 的 1.20.1 批次里，10 条用例只有 1 条真正执行了命令：另外 9 条的日志起点一直停在 {BT}503195B{BT}，键鼠注入把命令送进了别人的窗口/别的进程（{BT}MT_INJECT: ERROR — 1.20.1 客户端未在运行{BT} 之后连客户端都没了）。这条实测直接支撑了「命令与输入都不该经过系统键鼠」的判断。

全仓口径（{BT}temp/opstat471.py{BT} 统计，316 个用例）：

| 原语 | 用例数 | 步数 |
|---|---|---|
| {BT}inject_command{BT} | 269 | 5494 |
| {BT}inject_key{BT} | 18 | 65 |
| {BT}inject_mouse{BT} | 3 | 4 |
| {BT}screenshot{BT} | 51 | 131（原先恒 {BT}BLOCKED{BT}） |

## 2. 实现（全部在 {BT}scripts/test/lib/mt_case.py{BT}）

通道选择优先级对所有三个原语一致：步级 {BT}via{BT} > 环境 {BT}MT_INJECT_MODE{BT} > 默认 {BT}autojs{BT}。

| 原语 | AutoJS 侧实现 | 返回的状态字串 |
|---|---|---|
| {BT}inject_key e{BT} | 先 {BT}mc.setScreen(null){BT} 复位残留界面，再 {BT}KeyMapping.click(options.keyInventory){BT} | {BT}clicked{BT} / {BT}clicked-reset{BT} |
| {BT}inject_key escape{BT} | 有界面：{BT}keyPressed(256,0,0){BT} → {BT}onClose(){BT} → {BT}setScreen(null){BT} 三级回落；无界面：{BT}pauseGame(true){BT} | {BT}screen-esc{BT} / {BT}screen-close{BT} / {BT}screen-null{BT} / {BT}paused{BT} |
| {BT}inject_key f1{BT} | {BT}options.hideGui = !options.hideGui{BT} | {BT}hud-on{BT} / {BT}hud-off{BT} |
| {BT}inject_key 1..9{BT} | {BT}KeyMapping.click(options.keyHotbarSlots[i]){BT} | {BT}hotbar-N{BT} |
| {BT}inject_key rclick{BT} | 服务端 {BT}p.pick(4.5,0,false){BT}：命中方块 → {BT}AutoJS.place(x,y,z,face){BT}；否则 {BT}AutoJS.use(){BT} | {BT}place{BT} / {BT}place-miss{BT} / {BT}use{BT} / {BT}use-noeffect{BT} |
| {BT}inject_key cancel{BT} | 服务端发 {BT}ClientboundContainerClosePacket{BT} + {BT}doCloseContainer(){BT} | {BT}closed{BT} / {BT}no-container{BT} |
| {BT}inject_mouse{BT} | 有界面 → {BT}Screen#mouseClicked{BT}（窗口中心换算 GUI 坐标）；无界面 → 左键攻击 / 右键世界右键 | {BT}click{BT} / {BT}swing{BT} / {BT}use{BT} / {BT}use-noeffect{BT} |
| {BT}screenshot{BT} | 客户端 {BT}Screenshot.grab{BT}（4 参优先，失败退 3 参），python 轮询出图 ≤3s → 改名 {BT}<tag>_<run_id>.png{BT} → {BT}mt_capture.record_shot{BT} | {BT}OK{BT} / {BT}BLOCKED{BT} |

翻译表外的一律回退 native 并打 {BT}MT_CASE_WARN{BT}（例：{BT}k{BT} = iris 光影开关），所以历史用例不用逐条改也能跑。

## 3. 四个实测坑（写探针/改原语必读）

1. **{BT}cancel{BT} 不能用 {BT}closeContainer(){BT}**。Rhino 的 Java 包装器解析不到它：反射 {BT}p.getClass().getMethods(){BT} 里明明有 {BT}closeContainer{BT}，调用却抛 {BT}TypeError: Cannot find function closeContainer{BT}；{BT}doCloseContainer(){BT} 可调。最终按 {BT}ServerPlayer#closeContainer{BT} 的两步原样复刻。
2. **26.x 的 {BT}Screen{BT} 没有 {BT}keyPressed(int,int,int){BT}**，{BT}AbstractRecipeBookScreen.keyPressed(num…{BT} 直接 {BT}EvaluatorException{BT}。所以 ESC 必须做成三级回落链，不能只写一个调用。
3. **残留界面会污染下一条用例**：上一条失败留下 Screen，下一条首个 {BT}e{BT} 就读到 {BT}screen-open{BT}。现在 {BT}run(){BT} 起点有 {BT}_prepare_ui(){BT}，{BT}e{BT} 自己也会先复位（{BT}clicked-reset{BT}）。
4. **空手对空气右键不是失败**：原版 {BT}useItem{BT} 返回 {BT}FAIL{BT}，但 {BT}RightClickItem{BT} 事件已经派发。返回 {BT}use-noeffect{BT} 判 PASS + {BT}MT_CASE_WARN{BT}；{BT}place-miss{BT} / {BT}use-miss{BT} / {BT}no-screen{BT} 仍判 ERROR，堵住假绿。

另外两个脚手架坑：{BT}mt_capture.py{BT} 在 {BT}scripts/test/{BT}（不在 {BT}lib/{BT}），{BT}mt_case.py{BT} 里要显式 {BT}sys.path.append{BT}，否则 {BT}ModuleNotFoundError{BT}；截图是 {BT}RenderSystem.recordRenderCall{BT} 异步落盘，必须轮询等文件出现，抓不到判 BLOCKED 而不是空文件。

## 4. 实机验证（2026-11-05 23:0x–23:1x，串行）

| 线 | 用例 | 结果 |
|---|---|---|
| 1.20.1 | WALLET-GAMEMODE-1.20.1 | PASS（{BT}e{BT} / {BT}escape{BT} / 3 张截图） |
| 1.20.1 | CHIP-LOGIC-1.20.1 | PASS |
| 1.20.1 | DEBT-JADE-1.20.1 | PASS（含 Step 2 之后回归复跑） |
| 1.20.1 | LOOT-FIRST-1.20.1 | 输入全 PASS，判据失败 → 见 §5 |
| 1.21.1 | WALLET-GAMEMODE-1.21.1 | PASS |
| 1.21.1 | HUD-COMBAT-1.21.1 | PASS（{BT}f1{BT} ×2 + 6 张截图） |
| 1.21.1 | CC-ESC / TARGETSEL-PROMPT | 输入通道 OK，判据缺 → 见 §5 |
| 26.1.2 | WALLET-GAMEMODE-26.1.2 | PASS（{BT}clicked-reset{BT} 实证 {BT}_prepare_ui{BT}） |
| 26.1.2 | WALLET-VISION-26.1.2 | PASS（{BT}k{BT} 两次自动回退 native） |
| 26.1.2 | MODERNUI / LOOT-FIRST | 环境/探针缺 → 见 §5 |

截图实证（{BT}read_image{BT} 看过）：{BT}run/1.20.1/screenshots/wallet-gm-survival_20260921-191522.png{BT}（创造背包 + 钱包 789）、{BT}run/26.1.2/screenshots/wallet-shaders-on_20260921-191522.png{BT}（26.1.2 创造栏 + 钱包 18）。

## 5. 被判为「与本轮无关的既有失败」（证据齐，不是 Step 2 回归）

- **LOOT-FIRST 全线**：首步 {BT}/data remove entity @s ForgeData.astral_dice.first_loot_chest_done{BT} 被原版拒绝（聊天回「无法修改玩家数据」，vanilla {BT}DataCommands{BT} 不允许改玩家数据），标志位永远清不掉 ⇒ 用例隔离缺陷，只有重建世界才能跑。
- **PMFIX-TIP-1.20.1 / -1.21.1**：期望「4 点**法术**伤害」，产品现为「4 点**魔法**伤害」（文案漂移）。
- **RELEASE150-1.20.1**：断言 {BT}1.5.0+forge_1.20.1{BT}，开发线是 {BT}26.4-SNAPSHOT.6{BT}（发版线用例，不适用）。
- **DAMAGE-NUMBER-1.20.1**：颜色探针行 {BT}AP_DNUM:evt=tick…FF5555{BT} 未出现。
- **CC-ESC-1.21.1**：{BT}step=50:phase=two_of_three{BT} 来自 1.21.1 的 {BT}/astralcc{BT} 探针，该探针链仍缺（既有登记）。
- **TARGETSEL-PROMPT-1.21.1**：{BT}debug.log{BT} 里 {BT}TargetSelectPrompt{BT} 0 命中 ⇒ 选择态没进入，与右键通道无关。
- **MODERNUI-26.1.2**：{BT}run/26.1.2/mods{BT} 里没有 Modern UI。**LOOT-FIRST-26.1.2**：该线没有 {BT}/astralprobe{BT}（聊天回 {BT}<--[此处]{BT}）。

## 6. 仍开放的（Step 3 前置）

1. Step 3 = 全量回归 + 两通道判定差异逐条裁决（哪些用例只能真输入、哪些可以从 {BT}via: native{BT} 转正）。
2. {BT}vision{BT} 断言仍恒 BLOCKED：截图有了，语义判定通道还没有（需要 OCR/模型侧判定）。
3. 上面 §5 六类既有失败各自需要单独修复或用例隔离改造。
