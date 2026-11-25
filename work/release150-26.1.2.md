# 1.5.0 发布实机验收报告 — astral_dice · neoforge-26.1.2 线

- 验收对象：`neoforge-26.1.2/build/libs/astral_dice-1.5.0+neoforge_26.1.2.jar`
  （2,875,237 B，sha256 `DD22295C7C4036AE0CB95A2CC5B861DE30F5CBBF4E7A8859211830874DAC53A1`）
- 工作树：`D:\MCProject\astral_dice_multiloader`（分支 `multi-main`，HEAD `fd990c15`，验收期间未提交任何改动）
- 运行实例：`run/26.1.2/prodgame`（生产形态：raw java 启动，非 Gradle run）
- 用例：`scripts/test/cases/RELEASE150-26.1.2.json`（31 steps / 27 asserts）
- 验收窗口：2026-11-02 03:33（启动）→ 03:45:19（用例决定性运行结束）→ 03:52 前置收停

---

## 0. 判定

**最终判定：FAIL**（依据《判定口径》第 6 条：`ERROR/WARN 归因 … 本模组相关问题必须为 0 才算 PASS`）。

功能面与启动面全部达标，未达标的是**日志洁净度**这一条，且缺口全部可定位、可复现：

| 口径 | 项 | 结果 | 依据 |
|---|---|---|---|
| 1 | 启动日志四类禁词（`err: 5x` / `Failed to load native lib` / `must be registered` / `Unsatisfied runtime environment`） | PASS（各 0 次） | §6.1 |
| 2 | 版本钉死 `1.5.0+neoforge_26.1.2`，无 rc/beta 作为生效版本 | PASS（命 7 处；唯一 rc 出现是存档记录的**旧**版本，见 §6.2） | §6.1 |
| 3 | 命名核验 `abyss_reagent_chip`＝`深渊试剂`，`试剂-深渊` 命中 0 | PASS | §6.1 |
| 4 | 玩家数据机器可读探针行 | PASS（原文见 §4） | §4 |
| 5 | 截图须为本次运行新文件 | PASS（3 张均为本次新建，mtime/字节/sha256 见 §5）；**画面内容由 lead 肉眼定案，本报告不声称 vision PASS** | §5 |
| 6 | ERROR/WARN 归因，本模组相关问题必须为 0 | **FAIL**：本模组相关 9 条（8×`Missing sound for event: astral_dice:event_*` WARN ＋ 1×`astral_dice:astral_guide` 数据解析 ERROR），另 2 条 Jade mixin WARN 属设计内（`required=false`） | §6.2 |
| 7 | 收尾 `mt.ps1 --phase stop --force`、无残留、端口释放、mods 复原 | PASS | §8 |

用例层面：**25/25 判定性断言 PASS，2 条 vision 按设计 DELEGATED（不计 PASS）**，`MT_CASE_RESULT: RELEASE150-26.1.2 = PASS`。

**升级为 PASS 的条件**（二者任一）：① lead 对 8 条事件音效声明缺失与 `astral_guide` 数据解析 ERROR 作出「非阻断」裁决；② 按 §9 的建议补齐 `sounds.json` 8 条并在无 Patchouli 环境下消除该数据 ERROR 后复跑本用例。

---

## 1. 通道与命令原文

通道：**autojs**（KubeJS 实机 eval 通道，端点 `http://127.0.0.1:25585/mcp`，用例 `channel: "autojs"`）。用例自带 `CASE_CHANNEL: RELEASE150-26.1.2 channel=autojs`。

实际执行的命令序列（原文，pwsh 7，均以绝对路径 `.ps1` 包装执行）：

```text
# 0) 端口预检（一次全表 + 逐端口判定）
netstat -ano | findstr LISTENING

# 1) harness 路径接线（先看计划，再 -Apply；幂等）
pwsh -NoProfile -File D:\MCProject\astral_dice_multiloader\run\26.1.2\.tmp\prep-harness-paths.ps1
pwsh -NoProfile -File D:\MCProject\astral_dice_multiloader\run\26.1.2\.tmp\prep-harness-paths.ps1 -Apply

# 2) 产物换入（rc.7 → 1.5.0 GA；换完核 sha256）
pwsh -NoProfile -File D:\MCProject\astral_dice_multiloader\run\26.1.2\.tmp\_probe_swap_jar.ps1

# 3) 生产形态启动（先 dry-run 看 ARG|，再实启动）
python run/26.1.2/.tmp/prodlaunch.py --dry-run
python run/26.1.2/.tmp/prodlaunch.py --quickplay testworld --log run/26.1.2/.tmp/prod-run.log

# 4) 用例 validate（改动后必须仍 OK）
pwsh -NoProfile -File scripts/test/mt_case.ps1 validate --case scripts/test/cases/RELEASE150-26.1.2.json

# 5) 用例执行（决定性运行；见下方关于包装脚本的说明）
pwsh -NoProfile -File scripts/test/mt_case.ps1 run --version 26.1.2 --case scripts/test/cases/RELEASE150-26.1.2.json

# 6) 收尾（不带 --purge-saves）
pwsh -NoProfile -File scripts/test/mt.ps1 --version 26.1.2 --phase stop --force
```

关于第 5 步的包装：用例第 19 步需要把鼠标移到格子做 tooltip 取证，而 26.1.2 手感坐标只受 **OS 前台窗口**驱动（详见 §7.2 F3），因此实际运行经过一层包装脚本 `run/26.1.2/.tmp/_run_case2.ps1`：先对客户端进程 `SetForegroundWindow(pid.MainWindowHandle)`，再原样调用上面第 5 步的同一条命令。用例本身未因此获得任何特权。

---

## 2. 前置核对（全部实测，非引用文档）

### 2.1 端口

`netstat -ano | findstr LISTENING` 全表 10 条 LISTENING；逐端口判定 **25565 / 25585 / 25575 全部 FREE**（三个端口均无 LISTENING 行）。全程未 kill 任何非本流程进程。

### 2.2 harness 路径接线（Junction）

计划态与 `work/n26-test-prep.md` 一致（`[logs] rename-then-junction`、`[crash-reports] rename-then-junction+mkdir-target`），`-Apply` 结果：

```text
rename  run/26.1.2/logs            -> logs.dev-20261102-033242
junction run/26.1.2/logs           -> run/26.1.2/prodgame/logs
mkdir   run/26.1.2/prodgame/crash-reports
rename  run/26.1.2/crash-reports   -> crash-reports.dev-20261102-033242
junction run/26.1.2/crash-reports  -> run/26.1.2/prodgame/crash-reports
```

复核：两处 `LinkType = Junction`、`Attributes = Directory, ReparsePoint`；`run/26.1.2/logs/latest.log` 解析到 `prodgame/logs`（`realpath` 已确认）。**不接的话第 19 条 `type=log` 与 `type=crash` 会读到陈旧日志/崩溃目录**——本轮已接，因此断言读的是本次运行日志。

### 2.3 产物换入与 sha256

```text
源   neoforge-26.1.2/build/libs/astral_dice-1.5.0+neoforge_26.1.2.jar
     sha256 DD22295C7C4036AE0CB95A2CC5B861DE30F5CBBF4E7A8859211830874DAC53A1   2,875,237 B
旧   run/26.1.2/prodgame/mods/astral_dice-1.5.0-rc.7+neoforge_26.1.2.jar
     2,875,013 B  -> 改名 astral_dice-1.5.0-rc.7+neoforge_26.1.2.jar.bak-20261102-033248
新   复制入 run/26.1.2/prodgame/mods/astral_dice-1.5.0+neoforge_26.1.2.jar
     sha256 实测与源一致 DD22295C…53A1，2,875,237 B
```

`prodgame/mods` 共 17 项，其中 `astral_dice*` 三条：GA jar（2,875,237）＋ `….rc.7….jar.bak-20261102-033248`（2,875,013，**扩展名非 .jar，FML 不加载**）＋ `….rc.7….jar.compat-on.bak`（2,875,164，跑前既有，未动）。

### 2.4 启动器 dry-run 与实启动

```text
prodlaunch.py --dry-run : classpath 102 entries / missing 0 / unresolved placeholders none /
                          argv 48 / mainClass net.neoforged.fml.startup.Client /
                          gamedir F:\...\run\26.1.2\prodgame / assetsidx 30  → DRY-RUN ok
prodlaunch.py --quickplay testworld --log run/26.1.2/.tmp/prod-run.log
                       : started pid 36888
```

`prod-run.log` 终态 137,562 B。

### 2.5 就绪证据（`run/26.1.2/prodgame/logs/latest.log`）

```text
:390  [03:33:11.896] Loaded 1658 advancements
:411  [03:33:12.794] Time elapsed: 587 ms
:412  [03:33:12.967] DevTester[local:E:8ffec5a5] logged in with entity id 7 at (-7.5, -60.0, 8.5)
:427  [03:33:13.605] ModernFix: Total time to load game and open world was 14.126394 seconds
```

### 2.6 截图基线

运行前 `run/26.1.2/screenshots` 共 1,186 个文件（清单存 `run/26.1.2/.tmp/_shots_pre_list.txt`），`.mt_shots.json` 为 `run_id=20260926-230116` 的 12 条历史记录、**不含**本轮三个 tag。三个 tag 对应 PNG 在跑前均已删除（`PRE=False`），因此本轮出现的同名 PNG 必为本次生成（另见 §5 的 mtime/sha256 与清单 diff）。

---

## 3. 用例与 27 条断言

### 3.1 validate

```text
pwsh -NoProfile -File scripts/test/mt_case.ps1 validate --case scripts/test/cases/RELEASE150-26.1.2.json
MT_VALIDATE: OK — RELEASE150-26.1.2      (exit 0，改动前后各跑一次均为 OK)
```

### 3.2 决定性运行

`latest.log` 同一游戏会话里存在**三段** `[REL150]` 输出，务必按时间区分：

| 时段 | 性质 | 关键差异 |
|---|---|---|
| 03:36:29 | 首次全量运行（**改动前**，FAIL） | `pos=101,107,101`、`chip … count=1`、`hover=214,120`、无 `screen=` 行 |
| 03:44:27 | 中间态运行 | 已应用修复，`pos=100,108,100`、`count=3` |
| **03:45:02 → 03:45:06** | **决定性运行（PASS）** | 本报告所有断言证据均取此段；3 张 PNG 时间戳 03:45:03/04/06 落在其内 |

决定性运行的持久化证据：`scripts/test/cases/.mt_run_state.json` → `"RELEASE150-26.1.2": "PASS"`；`scripts/test/cases/.mt_progress.json` → `{"phase":"cases","case":"RELEASE150-26.1.2","step_index":58,"step_total":58,"op":"done","detail":"PASS","ts":1793562319}`；`scripts/test/cases/.mt_snapshot.json` → `versions.26.1.2.autojs.cases.RELEASE150-26.1.2`（`eval_steps: 8, eval_failed: 0`，8 条 eval 值原文）。

> 说明（不含糊）：决定性运行的逐断言控制台表未落盘（包装脚本未 tee 到文件），因此**逐条证据由我独立复核生成**：`run/26.1.2/.tmp/_recheck2.py` → `run/26.1.2/.tmp/assert_recheck.txt`，按用例 JSON 原样重放 27 条断言（log 正则逐行匹配 `latest.log`、eval 按 harness 的 JSON 引号归一化后匹配 `.mt_snapshot.json` 存值、jar 按 `Get-MtCaseJarStats` 同口径统计、crash 统计 `prodgame/crash-reports`）。复核结果与 harness 的 PASS 一致。

### 3.3 27 条断言逐条状态

（`latest.log` 行号均指**决定性运行**段落；`evals` 值取自 `.mt_snapshot.json`，已按 harness 规则做 JSON 引号归一化）

| # | 类型 | 断言（要点） | 状态 | 证据 |
|---|---|---|---|---|
| 1 | log·whole | `astral_dice-1\.5\.0\+neoforge_26\.1\.2\.jar` | PASS | `latest.log:54` `- astral_dice (jar(mods/astral_dice-1.5.0+neoforge_26.1.2.jar))` |
| 2 | log | `[REL150] ver=26.1.2 gamemode=creative` | PASS | :1557 |
| 3 | log | `… pos=100,-?[0-9]+,100` | PASS | :1557 `pos=100,108,100` |
| 4 | log | `… hp=20` | PASS | :1557 |
| 5 | log | `… item=astral_dice:abyss_reagent_chip` | PASS | :1557 |
| 6 | log | `… xp=30` | PASS | :1557 |
| 7 | log | `… slotted=[1-9][0-9]*` | PASS | :1557 `slotted=6` |
| 8 | log | `… chips=3` | PASS | :1557（3 个 count=1 的筹码堆求和） |
| 9 | log | `… modver=1\.5\.0\+` | PASS | :1557 `modver=1.5.0+neoforge_26.1.2` |
| 10 | log | `chip id=… count=3 name=(深渊试剂｜Abyss Reagent)` | PASS | :1595 `count=3 name=深渊试剂 tipn=6 path=screen` |
| 11 | log | `tip i=0 text=.+` | PASS | :1596 `text=深渊试剂` |
| 12 | log | `guide key=astral_dice.guide.entry.abyss_reagent_chip.1 text=.+` | PASS | :1602（另 :1588 为源码行回显） |
| 13 | log | `inventory open=1` | PASS | :1644 `open=1 hover=133,172 creative=1 glfw=1` |
| 14 | log | `CHIP_SHOP_OPEN player=` | PASS | :1704 `player=DevTester offers=electric_sword_chip+scope_chip+bank_card_high_chip` |
| 15 | log | `screen=\S*ChipShopScreen` | PASS | :1714 `screen=com.merlinkitsune.astral_dice.screen.ChipShopScreen` |
| 16 | log | `sys id=astral_dice:dice ok=1` | PASS | :1826 `name=骰子` |
| 17 | log | `sys id=astral_dice:attack_card_medium ok=1` | PASS | :1827 `name=攻击(中)` |
| 18 | log | `sys id=astral_dice:abyss_reagent_chip ok=1` | PASS | :1828 `name=深渊试剂` |
| 19 | log | `sys id=astral_dice:jill_sign ok=1` | PASS | :1829 `name=调酒师立牌` |
| 20 | eval·state | 综合正则（版本/模式/坐标/血量/物品） | PASS | 归一化值 `[REL150] ver=26.1.2 … pos=100,108,100 … item=astral_dice:abyss_reagent_chip` |
| 21 | eval·chip | `^"?(深渊试剂｜Abyss Reagent)"?$` | PASS | 归一化值 `"深渊试剂"` |
| 22 | eval·screen | `ChipShopScreen"?$` | PASS | 归一化值 `"com.merlinkitsune.astral_dice.screen.ChipShopScreen"` |
| 23 | absent | 命令错配话术族（`Unknown or incomplete command`／`No player was found`／`没有与选择器匹配的实体` 等） | PASS | 命中 0 |
| 24 | jar | entries≥1700 / models≥120 / signs≥25 | PASS | `entries=2452 models=158 signs=32`（`recipes` 按 harness 的复数目录口径统计为 0；26.1.2 实际是单数目录 `data/astral_dice/recipe/`，共 124 条，未被断言） |
| 25 | crash | 本次运行无新增崩溃 | PASS | `prodgame/crash-reports` 文件数 0 |
| 26 | vision | `abyss_reagent_tooltip` | **DELEGATED** | 需外部视觉模型；PNG 见 §5，**不计 PASS** |
| 27 | vision | `chip_shop_ui` | **DELEGATED** | 同上，**不计 PASS** |

汇总：`PASS 25 / DELEGATED 2 / FAIL 0`（`MT_CHANNEL: delegated_count=2`）。

---

## 4. 玩家数据探针原文（整行抄录）

决定性运行（`latest.log:1557`，`autojs-eval#23`，2026-11-02 03:45:02.119）：

```text
[REL150] ver=26.1.2 gamemode=creative pos=100,108,100 hp=20 food=20 xp=30 arm=0 slotted=6 chips=3 item=astral_dice:abyss_reagent_chip dim=minecraft:overworld modver=1.5.0+neoforge_26.1.2
```

配套客户端探针（同一运行）：

```text
:1595 [REL150] chip id=astral_dice:abyss_reagent_chip count=3 name=深渊试剂 tipn=6 path=screen
:1596 [REL150] tip i=0 text=深渊试剂
:1597 [REL150] tip i=1 text=栏位: 筹码
:1599 [REL150] tip i=3 text=装备时治愈层数 +2。触发治愈回血时：获得实际治疗量 × 2 的伤害吸收
:1601 [REL150] tip i=5 text=当前治愈：0 / 32
:1602 [REL150] guide key=astral_dice.guide.entry.abyss_reagent_chip.1 text=装备时治愈层数 +2；触发治愈回血时，获得「实际治疗量 ×2」的伤害吸收。
:1644 [REL150] inventory open=1 hover=133,172 creative=1 glfw=1
:1704 [astral_dice] CHIP_SHOP_OPEN player=DevTester offers=electric_sword_chip+scope_chip+bank_card_high_chip purchased=0 …
:1714 [REL150] screen=com.merlinkitsune.astral_dice.screen.ChipShopScreen
:1826 [REL150] sys id=astral_dice:dice ok=1 name=骰子
:1827 [REL150] sys id=astral_dice:attack_card_medium ok=1 name=攻击(中)
:1828 [REL150] sys id=astral_dice:abyss_reagent_chip ok=1 name=深渊试剂
:1829 [REL150] sys id=astral_dice:jill_sign ok=1 name=调酒师立牌
```

---

## 5. 截图清单（本次运行新生成）

目录：`D:\MCProject\astral_dice_multiloader\run\26.1.2\screenshots\`（`Mt.Paths.psm1` 固定截图根）。
目录文件数：跑前 1,186 → 现 1,190（新增 = 3 张 PNG ＋ 1 个 `.mt_shots.json.bak-before-release150-run`；无删除）。

| tag | 绝对路径 | 字节 | mtime (UTC) | sha256（前 16 位） |
|---|---|---|---|---|
| abyss_reagent_tooltip | `D:\MCProject\astral_dice_multiloader\run\26.1.2\screenshots\abyss_reagent_tooltip.png` | 157,975 | 2026-11-01T19:45:03.242Z | `300581BFDDE6714E` |
| chip_shop_ui | `D:\MCProject\astral_dice_multiloader\run\26.1.2\screenshots\chip_shop_ui.png` | 131,150 | 2026-11-01T19:45:04.875Z | `DE0EBCDB578910C8` |
| release150_guide | `D:\MCProject\astral_dice_multiloader\run\26.1.2\screenshots\release150_guide.png` | 109,430 | 2026-11-01T19:45:06.474Z | `124882558347864D` |

三张 PNG 的 mtime 均落在决定性运行窗口（03:45:02–03:45:06 本地 = 19:45:02–19:45:06 UTC）内，且与用例三步截图 op 的顺序一致。

登记：`run/26.1.2/screenshots/.mt_shots.json`（`run_id` 沿用粘滞值 `20260926-230116`，与 `scripts/test/cases/.mt_active_run` 一致）新增三条：

```json
{"file":"abyss_reagent_tooltip.png","tag":"abyss_reagent_tooltip","ts":1793562303.232}
{"file":"chip_shop_ui.png","tag":"chip_shop_ui","ts":1793562304.925}
{"file":"release150_guide.png","tag":"release150_guide","ts":1793562306.543}
```

为便于 lead 查看，三张 PNG 已复制到 `work/release150-26.1.2-shots/`（字节数一致）。

**关于 vision：本报告不判定画面内容。** 两条 vision 断言由 harness 判 DELEGATED 并输出：

```text
VISION_PATH=D:\MCProject\astral_dice_multiloader\run\26.1.2\screenshots\abyss_reagent_tooltip.png | 画面里是否看得到物品提示框（tooltip）或背包界面？只回答「看到了」或「没看到」，并用一句话说明画面主体。
VISION_PATH=D:\MCProject\astral_dice_multiloader\run\26.1.2\screenshots\chip_shop_ui.png | 画面是否是筹码商店/容器界面（而不是普通世界画面）？只回答「是商店」或「不是商店」。
```

补充（供 lead 判图时的上下文，非视觉结论）：本轮为 tooltip 截图专门解决了「26.1.2 手感坐标只受 OS 前台窗口驱动」的问题（§7.2 F3），探针脚本在设置光标后重开背包并抓图，同一次运行中 `inventory open=1 hover=133,172 creative=1 glfw=1` 记录到光标已落在创造背包 hotbar 第 1 格的芯片上；提示框文本本身已由 `tip i=0..5` 与 `guide key=…` 两族日志独立证明。

---

## 6. 日志归类

日志：`run/26.1.2/prodgame/logs/latest.log`（终态 148,358 B / 1,829 行；含三次用例运行输出，见 §3.2）。

### 6.1 口径 1–3 计数

| 判据 | 目标 | 实测 |
|---|---|---|
| `err: 5x` | 0 | **0** |
| `Failed to load native lib` | 0 | **0** |
| `must be registered` | 0 | **0** |
| `Unsatisfied runtime environment` | 0 | **0** |
| `1.5.0+neoforge_26.1.2` | 必须出现 | **7 处**（:25 模组列表、:54 jar 路径、:270 版本互通门槛、:368 版本差异、:638/:1237/:1557 探针行） |
| `试剂-深渊` | 0 | **0** |
| beta 作为生效版本 | 无 | 无（`beta` 0 次） |
| `rc` 字样 | 不得作为生效版本 | 仅 `:368` 一处，且是**存档记录的旧版本**：`astral_dice (version 1.5.0-rc.7+neoforge_26.1.2 -> 1.5.0+neoforge_26.1.2)`；生效版本为 `:25` 的 `Astral Dice 1.5.0+neoforge_26.1.2` 与 `:54` 的 jar 条目（换装前世界由 rc.7 打开过，属预期提示） |

命名核验：`abyss_reagent_chip` 的显示名在服务端与客户端两路均为 `深渊试剂`（`:1508`、`:1595`、`:1596`）。

### 6.2 astral_dice 相关 ERROR/WARN（逐条归因）

全日志 ERROR/WARN 共 **51** 行，其中带 `astral_dice` 的 12 行：

| 行 | 级别 | 内容（摘要） | 归属判定 |
|---|---|---|---|
| :94 | WARN | `@Mixin target snownee.jade.addon.vanilla.EntityHealthAndArmorProvider$Client was not found astral_dice.jade.mixins.json:ArmorValueProviderMixin from mod astral_dice` | 本模组**配置**，Jade 未安装时的预期降级（该 mixin 配置 `required=false`）→ 非缺陷 |
| :96 | WARN | `@Mixin target snownee.jade.overlay.DisplayHelper was not found astral_dice.jade.mixins.json:DisplayHelperArmorIconMixin from mod astral_dice` | 同上 → 非缺陷 |
| :334–341 | WARN ×8 | `Missing sound for event: astral_dice:event_fade_in / event_fade_out / event_heal / event_damage / event_poisoning / event_transport / event_bonus / event_tick` | **本模组资源缺陷**：`ModSounds` 注册了 8 个 `SoundEvent`，jar 内也有对应 `.ogg`，但 `assets/astral_dice/sounds.json` 没有这 8 条声明（该文件 15 键、0 条 `event_*`；另三线为 23 键、含这 8 条）→ 8 条事件音效静默无声 |
| :383 | WARN | `KubeRecipe.java#95: Failed to parse recipe 'astral_dice:astral_guide[minecraft:crafting_shapeless]'! … No component with type: 'patchouli:book'` | **本模组数据**：`astral_guide` 配方产物用 `patchouli:book` 组件，而 `patchouli` 在 `neoforge.mods.toml` 里是 `type="optional" type`，本实例未装 Patchouli |
| :388 | ERROR | `Couldn't parse data file 'astral_dice:astral_guide': … No component with type: 'patchouli:book' … missed input: {"patchouli:book":"astral_dice:astral_guide"}` | 同上（同一次解析的 ERROR 面）；四线都带同一份配方，属**跨线一致**的可选前置缺失面 |
| :1449/:1769 | INFO | `[REL150] guide_open=ERR:InternalError: Failed to load Java class 'vazkii.patchouli.api.PatchouliAPI'` | 用例内探针在无 Patchouli 环境下的既定返回（用例按设计容忍），非 ERROR 级 |

计数口径落地：**本模组相关缺陷类 = 9 条**（8 音效 + 1 数据解析 ERROR）；设计内降级 = 2 条（Jade mixin）。据此第 6 条口径不满足。

证据补强（jar 实物核对）：
- `assets/astral_dice/sounds.json` 键数 15，`event_*` 命中 0；`assets/astral_dice/sounds/event_*.ogg` 8 个文件确实存在。
- `com/merlinkitsune/astral_dice/audio/ModSounds.java:106-113` 注册 `EVENT_FADE_IN … EVENT_TICK` 8 条；`neoforge-1.21.1`、`forge-1.20.1`、`fabric-1.20.1` 三线的 `sounds.json` 均为 23 键且含这 8 条。
- 历史定位：26.1.2 线的 `ModSounds` 事件常量由移植提交 `ee6493ba`（`wip(26.1.2): 移植 1.5.0-rc.7 结构层`）带入，而该线 `sounds.json` 是更早的 15 键版本（`git log -S event_fade_in` 对 26.1.2 的该文件为 0 命中）⇒ 移植时漏了 `sounds.json` 的这 8 条声明，属本线独有回归，非本次发布引入。

### 6.3 第三方 / 环境类（不归本模组）

| 行 | 级别 | 归属 |
|---|---|---|
| :367–368 | WARN | NeoForge `CommonHooks`：`The following mods have version differences that were not resolved`（存档记录 rc.7 → 现 1.5.0；NeoForge 机制提示） |
| :371 | WARN | AutoJS MCP `No token configured`（本地测试通道，非发布相关） |
| :372/:392/:426/:427 | WARN | ModernFix 启动/数据包/进世界耗时（性能自述） |
| :380 | WARN | modernfix `Static binding violation: PRIVATE @Overwrite … visibility will be upgraded` |
| :393 | ERROR | 存档数据 `minecraft:game_rules` 残留 `jade:max_position_deviation`（世界曾装 Jade；**Jade 的世界数据**，非本模组） |
| :449 | WARN | Iris `[Iris Update Check] Failed to get update info!`（无网络/更新检查） |
| 其余 ~30 行 | WARN | Sodium 线程化优化 workaround、Iris 对未安装模组的 mixin 目标缺失、curios/iris refmap 读取提示、ModernUI 签名/字体提示、neoforge pipeline sampler、JarJar source-select、rhino deprecation、KubeJS ArchitecturyIntegration skip、Minecraft/Realms `Failed to fetch user properties` 等 —— 均为第三方或环境网络类 |

---

## 7. 改动清单（用例与工具链，全部最小化）

### 7.1 工具链缺陷修复：`scripts/test/lib/Mt.Channel.psm1` 的 `op:screenshot`（**必要改动**）

- 备份：`scripts/test/lib/Mt.Channel.psm1.bak-before-release150-run`（43,100 B）
- 改动后：44,868 B / sha256 `290F139F9B12689B12F9154933DFEAFDD0C143288BF37413D04ED525CD08657E`（CRLF 保留，`[Parser]::ParseFile` 0 错误）
- 实测根因（与任务书假设不同，按实测结论修改）：
  1. KubeJS 26.1.2 移除了 `java` 包访问器，旧 JS 里 `new java.io.File(...)` 直接抛 `ReferenceError: "java" is not defined`（不是 `java()` 的 LegacyError）；
  2. `Screenshot.grab(File, String, RenderTarget, int, Consumer)` 的**第一个参数是 gameDir**，原实现传的是 `run/26.1.2/screenshots`，落在真实签名下会写成 `screenshots/screenshots/xxx.png`；
  3. 写盘发生在 `Util.ioPool()`，`grab` 之后立刻 `exists()` 通常为 false ⇒ 旧实现即使抛错也会"报成功"。
- 修法（未引入新依赖）：JS 改为 `Java.loadClass('java.io.File'|'net.minecraft.client.Screenshot'|'java.util.function.Consumer')`（带旧式包名 fallback），第一参改传 gameDir（`Split-Path -Parent $shotDir`），抓图后轮询最多 10 s 等文件出现；**只有真实落到磁盘的文件才走 `Add-MtShot` 登记**，抓图前先删同 tag 旧 PNG，避免旧证据冒充。
- 验证：探针 tag `release150_probe_channel` 端到端成功（PNG 213,777 B ＋ manifest 登记），随后清理探针产物并回滚 manifest。三次正式截图（§5）即由此路径产出。

### 7.2 用例最小改动（7 处；**未删除、未放宽任何断言**）

备份：`scripts/test/cases/RELEASE150-26.1.2.json.bak-before-release150-run`（15,389 B）。
改动后：16,215 B / sha256 `010D68E81D73E3CFB1ECAE5D35E98A03BD8F0A377973B9E13EF8C8B85FE970A7`；仍 31 steps / 27 asserts；`mt_case.ps1 validate` → `MT_VALIDATE: OK`（改动前后各一次）。

| 编号 | 位置 | 原问题 | 最小改动 |
|---|---|---|---|
| F1 | step 17（server eval） | `AutoJS.state()` 的 pos 是方块中心（`100.5`），`Math.round` 得到 `101`，与断言 `pos=100,…` 冲突 | x/y/z 改用 `Math.floor`（只改取整函数，字段与格式不变） |
| F2 | step 18（client eval） | 「筹码数量」取的是 `new ItemStack(item).getCount()`＝1，而实际是 3 个 count=1 的不可堆叠栈 | 改为遍历玩家背包按物品 id 求和（得到真实 3），tooltip 取找到的那一栈 |
| F3 | step 19（client eval） | ① `screen.getClass()` 在 KubeJS Rhino 不可用（`TypeError: Cannot find function getClass`）；② 创造模式背包是 `CreativeModeInventoryScreen`（195×136），坐标几何与普通背包（176×166）不同；③ 26.1.2 的 hover 由 `AbstractContainerScreen.extractContents` 用**真实光标**坐标计算，`mouseMoved()` 不改变 hover，而 `GLFW.glfwSetCursorPos` 只对 OS 前台窗口生效 | 改按模式算几何（creative 用 195×136、槽位 y＝topPos+120；否则 176×166、y＝top+151），并调用 `GLFW.glfwShowWindow/glfwFocusWindow/glfwSetCursorPos(x*scale, y*scale)`（外置包装脚本先把窗口置前台）；日志行增加 `creative=`/`glfw=` 两个诊断字段（断言 #13 的 `inventory open=1` 前缀不变） |
| F4 | assert #20（eval·state） | eval 存值被 JSON 引号包裹（`"…"`），原正则从 `^` 起点匹配失败 | 正则加可选引号前缀 `^"?\[REL150\]…`（与 1.20.1 线同类缺陷同一处理口径） |
| F5 | step 25（client eval） | `s.getClass().getName()` 不可用 → 整步报错，无 `screen=` 行 | 改为 `String(s).replace(/@[0-9a-f]+$/, '')` 取裸 FQN（`String(javaObj)` 在 KubeJS 26.1.2 可用） |
| F6 | assert #21（eval·chip） | 同上，JSON 引号 | 正则改 `^"?(深渊试剂｜Abyss Reagent)"?$` |
| F7 | assert #22（eval·screen） | 同上，JSON 引号 | 正则由 `ChipShopScreen$` 改 `ChipShopScreen"?$` |

说明：F4/F6/F7 是**引号容忍**（匹配面等价收紧后的写法），断言语义、被检内容强度均未降低；F1/F2/F5 修的是探针取值错误（原取值本身不可满足断言）；F3 修的是 26.1.2 平台事实导致的手感/几何不成立。7 处均已逐条在 §3.3 表中以决定性运行证据闭环。

---

## 8. 收尾与现场状态

```text
pwsh -NoProfile -File scripts/test/mt.ps1 --version 26.1.2 --phase stop --force
MT_CLEANUP: 指定了 --version，保留 Gradle 守护进程
MT_CLEANUP: 已按 --force 清除失败取证标记 …\.mt_keep_alive-26.1.2
MT_CLEANUP_SAVES: KEPT（未指定 --purge-saves）
MT_CLEANUP: 已收停 1 个本流程进程（范围：26.1.2）
MT_CLEANUP_RESULT: OK
MT_STOP: OK (1s) — 收停完成，无本流程残留进程
```

收尾后核对：

| 项 | 结果 |
|---|---|
| 本流程 java 进程（命令行含 `prodgame`/`26.1.2`） | **0**（另有 4 个 java 属于 Gradle 守护进程 / fabric loom / 其他工作流，非本流程，未碰） |
| 端口 25565 / 25585 / 25575 | 全部 **未监听**（`netstat` 复核） |
| `prodgame/crash-reports` | 0 个文件 |
| `run/26.1.2/logs` `run/26.1.2/crash-reports` | 仍为指向 `prodgame` 的 Junction（prep 设计如此，保留以便后续验证） |
| `prodgame/mods` | 17 项；**保留换入的 GA jar**（`astral_dice-1.5.0+neoforge_26.1.2.jar`, 2,875,237 B, sha256 `DD22295C…53A1`），rc.7 保留为 `.jar.bak-20261102-033248`（不加载）；保留原因：这是本次被验收的发布物，lead 后续复核/复跑无需再换装；如需完全复原可把 `.bak` 改回 `.jar` 并移出 GA jar |
| 世界存档 | 未 purge（`prodgame/saves/testworld` 保留，玩家数据仍在其中） |

---

## 9. 遗留风险与建议

1. **本模组相关日志 9 条（口径 6 的失败项）**
   - A 类（8 条，建议发布前修）：26.1.2 线 `assets/astral_dice/sounds.json` 缺 `event_fade_in/fade_out/heal/damage/poisoning/transport/bonus/tick` 8 条声明。修法：从 `neoforge-1.21.1` 同名文件补齐这 8 条（键名与 `ModSounds` 常量一致，`.ogg` 已在 jar 内），复跑本用例即可消除。
   - B 类（1 条 ERROR，建议评估）：`data/astral_dice/recipe/astral_guide.json` 的产物组件 `patchouli:book` 在未装 Patchouli（可选前置）时导致 `Couldn't parse data file`。四线同构；若要彻底消除，需要条件化该数据文件（或接受"未装 Patchouli 时该配方不可用且报一条 ERROR"）。
2. **两条 vision 仍为 DELEGATED**：`abyss_reagent_tooltip.png` / `chip_shop_ui.png`（另有无断言对应的 `release150_guide.png`）需 lead 亲自看图定案；本报告不对画面内容作 PASS 结论。
3. **Patchouli 未在测试实例内**：`prodgame/mods` 无 Patchouli，指南书 UI / 配方实体化均未验证（用例 step 27 返回 `ERR: Failed to load Java class 'vazkii.patchouli.api.PatchouliAPI'`，按用例设计被容忍，assert #12 由 `Component.translatable` 线覆盖）。若要覆盖真实指南书界面，需往实例加 Patchouli 后单独加用例。
4. **Jade 未在测试实例内**：两条 jade mixin WARN 与存档里 `jade:max_position_deviation` 的 ERROR 都是"Jade 缺装/世界残留"面，属环境；但说明 prodgame 实例与正式发布的推荐环境（含 Jade/Patchouli）存在差异。
5. **存档内的版本记录**仍是 `1.5.0-rc.7`（`:368` 的 NeoForge 提示来源）：这是 `level.dat` 记录的上次加载版本，不是生效版本；下一次保存/重进后会刷新。不是缺陷，但会持续产生 1 条 WARN。
6. **`.mt_shots.json` 的 `run_id` 是粘滞的 `20260926-230116`**（`scripts/test/cases/.mt_active_run` 既有值）：本轮截图以 tag 解析，harness 判 DELEGATED 时能正确定位 PNG；但如果后续要求"按 run 隔离截图"，需要先滚动该 id。
7. **`latest.log` 含改动前的失败运行片段**（03:36，`pos=101,107,101`）：任何复核都必须按时间戳取 03:45 段，否则会把修复前的输出当成证据。

---

## 10. 关键证据路径

| 用途 | 路径 |
|---|---|
| 生产形态运行日志 | `run/26.1.2/prodgame/logs/latest.log`（148,358 B）、`run/26.1.2/.tmp/prod-run.log`（137,562 B） |
| 用例状态（持久化） | `scripts/test/cases/.mt_run_state.json`、`.mt_progress.json`、`.mt_snapshot.json` |
| 27 条断言独立复核 | `run/26.1.2/.tmp/assert_recheck.txt`（由 `run/26.1.2/.tmp/_recheck2.py` 生成） |
| 截图 | `run/26.1.2/screenshots/{abyss_reagent_tooltip,chip_shop_ui,release150_guide}.png` ＋ `.mt_shots.json`；副本在 `work/release150-26.1.2-shots/` |
| 用例与工具备份 | `scripts/test/cases/RELEASE150-26.1.2.json.bak-before-release150-run`、`scripts/test/lib/Mt.Channel.psm1.bak-before-release150-run` |
| 失败运行留档 | `run/26.1.2/.tmp/case-run-release150-run1.log`（16,157 B，改动前 FAIL） |
| harness 路径接线备份 | `run/26.1.2/logs.dev-20261102-033242`、`run/26.1.2/crash-reports.dev-20261102-033242` |

---

## 11. 未做的事（明确边界）

- 未 `git add` / `commit` / `push` / `tag`；未改任何源码、`assets/**/lang/**`；未碰另三条线的目录。
- 未对画面内容作视觉判定（vision 保持 DELEGATED）。
- 未删除或放宽用例任何断言；未改动 `work/n26-test-prep.md` 与 `work/release150-test-runbook.md`。
