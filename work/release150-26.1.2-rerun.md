# astral_dice 1.5.0 / neoforge-26.1.2 发布实机验证复跑报告

- 轮次：发布验证复跑（准备 stamp 20261102-040635 之后接续）
- 测试台版本线：neoforge-26.1.2（quickplay 单人客户端）
- 用例：`scripts/test/cases/RELEASE150-26.1.2.json`（31 steps / 27 asserts）
- 本轮运行窗口：2026-11-02 04:30:53（起进程）～ 2026-11-02 04:39:48（最后一次用例动作），随后 04:44 前后收停
- 报告生成时间：2026-11-02T04:5x+08:00

## 0. 判定摘要

| 项 | 结果 |
|---|---|
| 用例机读判定 | **MT_CASE_RESULT: RELEASE150-26.1.2 = FAIL** |
| 断言统计 | 24 PASS + 1 FAIL + 2 BLOCK（共 27） |
| A 类（Missing sound for event: astral_dice:event_*） | **0 命中 —— 上一轮 8 条已归零** |
| B 类（Couldn't parse data file 'astral_dice:astral_guide'） | **0 命中 —— 已消失** |
| 硬门（err: 5xx / native lib / must be registered / Unsatisfied runtime environment） | 全 0 |
| 生效版本 | 1.5.0+neoforge_26.1.2（latest.log 9 处，`1.5.0-rc.` 命中 0） |
| 本模组 ERROR/WARN | **1 条 WARN（不满足任务书“必须为 0”）**，见 §11 |
| 截图证据 | 三个 tag 命名 PNG 均在窗口内落盘且为有效 PNG，但测试台六档签名全败、`.mt_shots.json` 无本轮条目 ⇒ 断言 57/58 判 BLOCK，不判 PASS |
| 收尾 | stop --force OK、无 run/26.1.2 残留 java、25565/25575/25585 均无 LISTEN、mods 已复原为准备前 17 条目原状 |

**结论**：A/B 两类问题均已实证修复；用例未全绿的原因是 1 条证据源缺陷（断言 32，测试台 `win=whole` 读到 0 字节）+ 2 条截图签名不可用（断言 57/58，BLOCK），**不是**模组功能回归。此外本模组自身仍产出 1 条 WARN（Waystone 软兼容注册失败），按任务书口径如实记为不达标项。

## 1. 准备结果复核（逐项实读，全部通过）

| 复核项 | 期望 | 实读 | 判定 |
|---|---|---|---|
| mods 条目数 | 97 | 97，全部 `.jar` | PASS |
| astral_dice 字节数 | 2,877,947 | 2,877,947 B | PASS |
| astral_dice sha256 | b422d57d…878d | `b422d57d2103219522eef792822da1656a1f9e197f91c6b0d8e82d381ec4878d` | PASS |
| jar 内 sounds.json 顶层键 | 23 含 8 条 `event_*` | 23 键，`event_*` 恰 8 条 | PASS |

`event_*` 八条（逐一列出）：`event_bonus`、`event_damage`、`event_fade_in`、`event_fade_out`、`event_heal`、`event_poisoning`、`event_tick`、`event_transport`。

同 jar 还存在 `assets/astral_dice/items/astral_guide.json` 与 `data/astral_dice/patchouli_books/astral_guide/book.json`（B 类修复所依赖的两件）。

轮转确认（准备阶段产物在位）：

- `run/26.1.2/.tmp/prod-run.log.prev-20261102-040635`
- `run/26.1.2/prodgame/logs/latest.log.prev-20261102-040635`（1,830 行）
- `run/26.1.2/screenshots/{abyss_reagent_tooltip,chip_shop_ui,release150_guide}.png.prev-20261102-040635`
- `run/26.1.2/screenshots/.mt_shots.json.bak-before-rerun-20261102-040635`

## 2. 起跑前置检查与端口门

### 2.1 执行前无本方 java

起跑前 `JAVA_WITH_RUN2612=0`。当时另有 5 个无关 java 进程（其中 fabric-1.20.1 的 runServer 由 pid 37252 devlaunchinjector + 29924 gradle wrapper 组成），属他人工作，**全程未干预**。

### 2.2 端口门（任务书口径）与实测偏差

- **04:21 按任务书口径实测**：`Get-NetTCPConnection -State Listen` 过滤 25565 / 25575 / 25585 → 三者均 0 行（`NO_LISTEN_25565_25575_25585`），判定门开、立即起跑。
- **起跑后复验发现该口径在本机是假阴性**：04:31:19 AutoJS 已明确打出 `MCP endpoint listening on http://127.0.0.1:25585/mcp`，而同一时刻 `Get-NetTCPConnection -State Listen -LocalPort 25585` 仍返回 0 行；`netstat -ano` 也只有一条本方客户端连到 127.0.0.1:25585 的 `TIME_WAIT`（TCP 栈把监听套接字以其他状态/另一命名空间呈现），**始终没有 LISTEN 行**。
- 因此本轮的**实际就绪门**改为 AutoJS status 端点（`mcp__autojs__status`：server available / client available, world loaded），任务书口径只用于“无冲突快速预检”。前置 agent 当初误判 25565/25575/25585 被占用，与本条同根因。

## 3. 启动过程：两次失败与最小修复

本轮共三次起进程，前两次为环境装配问题（非模组缺陷），全部留证并已回滚：

### 3.1 失败 1 — pid 28972（04:22:03 起，04:22:05 被 FML 拒绝）

日志：`run/26.1.2/.tmp/prod-run.log.fail1-launch-20261102-0429`。原文：

```
Failed to start FML: net.neoforged.fml.ModLoadingException: Loading errors encountered:
	- Mod kubejs requires rhino 2101.2.8-build.91 or above / Currently, rhino is not installed
	- Mod mru requires neoforge 26.1.2.114 or above / Currently, neoforge is 26.1.2.109
	- Mod autojs requires rhino 2101 or above / Currently, rhino is not installed
```

根因两条：

1. 97 件组装**漏注入 rhino**（原 17 jar 环境一直带 `rhino-2101.2.8-build.91.jar`，980,936 B）。
2. 玩家实例的 `mru-1.0.43+26.1.2-neoforge.jar` 在其 `META-INF/neoforge.mods.toml` 写死 `neoforge` 依赖区间 `[26.1.2.114,)`；测试台 `D:\.minecraft\versions\26.1.2-NeoForge 模组测试` 的 libraries 只有 26.1.2.109（`D:\.minecraft\libraries\net\neoforged\neoforge` 下仅 26.1.2.109 与 26.1.2.*-beta），无 .114。

级联核查：扫描全部 97 个 jar 的 dependencies 段，依赖 `mru` 的只有 `sounds-2.6.0+26.1.2-neoforge.jar`（required，`[1.0.41,)`），所以**不能只移 mru**，必须成对移出。

处置（可逆、留证）：

- 从 `run/26.1.2/.tmp/_prod-mods-orig-20261102-040635` 复制回 `rhino-2101.2.8-build.91.jar`（980,936 B，sha256 `1d53d962aa4dac54bcbb4f998c1845980cf194318f1d6b1580ea6ac15cce5ef3`）。
- 移出到 `run/26.1.2/.tmp/_rerun-20261102/mods-excluded/`：`mru-1.0.43+26.1.2-neoforge.jar`（153,443 B，sha256 `f59e3bb77f18a2833323cb4d1709303e99d74e12884d54fa4c80607931ee258a`）、`sounds-2.6.0+26.1.2-neoforge.jar`（8,113,546 B，sha256 `186acf63bc17777bc51b9d5fba592bc49e30315a5dfdaa1532426bd16c8e1be4`），并写 `mods-excluded/EXCLUSION-RECORD.txt` 记录理由与哈希。
- 此步后 `MODS_ENTRIES=96`。

### 3.2 失败 2 — pid 18212（04:27:07 崩溃）

日志：`run/26.1.2/.tmp/prod-run.log.fail2-crash-20261102-0430`；崩溃报告 `run/26.1.2/prodgame/crash-reports/crash-2026-11-02_04.27.07-client.txt`（Crash Report UUID `d4d1cb20-16cd-4f4b-999c-68f0b7439e33`）。原文：

```
net.neoforged.fml.ModLoadingException: Loading errors encountered:
	- KubeJS (kubejs) encountered an error while dispatching the net.neoforged.neoforge.registries.RegisterEvent event
	  java.lang.NoClassDefFoundError: dev/latvian/mods/betteradvancedtooltips/BATIcons
```

根因：注入的 KubeJS 8.0.6（来自 dev 目录）有 4 个类**硬引用** BAT 包 —— `dev/latvian/mods/kubejs/client/KubeJSErrorScreen$Entry.class`、`command/KubeJSCommands.class`、`plugin/builtin/wrapper/TextIcons.class`、`server/ChangesForChat.class`，而 95 件玩家实例集不含 BAT。

处置：从 `_prod-mods-orig-20261102-040635` 复制回 `better-advanced-tooltips-2601.1.0-build.10.jar`（38,178 B，sha256 `7d0ec46407c525f6136c66d44089120b5cb6a288bb50d3450d37e05f0f7732a1`；其 neoforge 要求 `[21.5.75,)` 满足）⇒ `MODS_ENTRIES` 回到 97。两个失败进程均由本方 `Stop-Process` 清理（均为本方进程）。

> 提示（给后续轮次）：该测试台把“dev 目录的 KubeJS”注进“玩家实例 95 jar”是脆弱组合 —— 注入件与玩家集之间缺 `rhino` 与 `better-advanced-tooltips` 两个隐式依赖，组装时必须补，否则必然 FML 拒绝或 RegisterEvent 崩溃。

### 3.3 成功启动 — pid 29988

启动方式：`run/26.1.2/.tmp/_rerun-20261102/launch2.ps1` 包装 `run/26.1.2/.tmp/prodlaunch.py --quickplay testworld --log run/26.1.2/.tmp/prod-run.log`；启动前预热校验输出：`classpath entries 102 / missing 0 / unresolved placeholders none / argv entries 48`。

就绪证据（`run/26.1.2/prodgame/logs/latest.log`）：

- 04:31:06 modloading worker 启动
- 04:31:12 `Astral Dice mod loaded.`
- 04:31:19 `MCP endpoint listening on http://127.0.0.1:25585/mcp`（伴随 `WARN No token configured`，属 AutoJS 配置项，非模组）
- 04:31:21 `Starting integrated minecraft server version 26.1.2`，随后 testworld 三维度 `Saving chunks` ⇒ 已进入世界
- `mcp__autojs__status`：server registered/available（`server running with 1 player(s) online`）、client available（`client running, world loaded`）
## 4. 用例执行：validate 与 MT_CHANNEL 原文

先 `validate`：`MT_VALIDATE: OK — RELEASE150-26.1.2`。

实际共跑 5 次（结果完全一致；前四次因 pwsh 二次编码 / Tee 重定向问题未落盘干净，第五次改为“捕获 stdout 再落盘”后取得正式抄本）：

- run1 04:34:40–04:35:15（FAIL，同判定）
- run2 04:35:53–04:36:39（Tee 被 GBK 双重编码污染，恢复件有丢字）
- run3 / run4（相对路径未落盘 / `Out-File` 得 0 字节）
- **run5 正式抄本：`run/26.1.2/.tmp/_rerun-20261102/case-run5.log`（16,330 字符，UTF-8 正常，退出码 1）**

### 4.1 MT_CHANNEL（run5 原文，命令通道 11 条全部 ok）

```
MT_CHANNEL: mode=autojs
MT_CHANNEL: op=command channel=autojs status=ok detail=cmd_via=autojs_command cmd=/gamemode creative
MT_CHANNEL: op=command channel=autojs status=ok detail=cmd_via=autojs_command cmd=/clear @a
MT_CHANNEL: op=command channel=autojs status=ok detail=cmd_via=autojs_command cmd=/effect clear @a
MT_CHANNEL: op=command channel=autojs status=ok detail=cmd_via=autojs_command cmd=/tp @a 100 120 100 0 0
MT_CHANNEL: op=command channel=autojs status=ok detail=cmd_via=autojs_command cmd=/time set 1000
MT_CHANNEL: op=command channel=autojs status=ok detail=cmd_via=autojs_command cmd=/xp set @a 30 levels
MT_CHANNEL: op=command channel=autojs status=ok detail=cmd_via=autojs_command cmd=/effect give @a minecraft:speed 600 1 true
MT_CHANNEL: op=command channel=autojs status=ok detail=cmd_via=autojs_command cmd=/give @a astral_dice:abyss_reagent_chip 3
MT_CHANNEL: op=command channel=autojs status=ok detail=cmd_via=autojs_command cmd=/give @a astral_dice:dice 1
MT_CHANNEL: op=command channel=autojs status=ok detail=cmd_via=autojs_command cmd=/give @a astral_dice:attack_card_medium 1
MT_CHANNEL: op=command channel=autojs status=ok detail=cmd_via=autojs_command cmd=/give @a astral_dice:jill_sign 1
MT_CHANNEL: op=shot channel=delegated status=delegated detail=abyss_reagent_tooltip — 各档签名均未产出 abyss_reagent_tooltip.png：T1 not_called: InternalError: Can't find method net.minecraft.client.Screenshot.grab(java.io.File,string,com.mojang.blaze3d.pipeline.MainTarget,adapter1). (autojs-eval#53); T2 called_no_file: ; T3 not_called: Screenshot.m_92289_ 不存在（非 1.20.1…
MT_CHANNEL: op=shot channel=delegated status=delegated detail=chip_shop_ui — 各档签名均未产出 chip_shop_ui.png：T1 not_called: InternalError: Can't find method net.minecraft.client.Screenshot.grab(java.io.File,string,com.mojang.blaze3d.pipeline.MainTarget,adapter1). (autojs-eval#53); T2 called_no_file: ; T3 not_called: Screenshot.m_92289_ 不存在（非 1.20.1 SRG 运行时）; T4 call…
MT_CHANNEL: op=shot channel=delegated status=delegated detail=release150_guide — 各档签名均未产出 release150_guide.png：T1 not_called: InternalError: Can't find method net.minecraft.client.Screenshot.grab(java.io.File,string,com.mojang.blaze3d.pipeline.MainTarget,adapter1). (autojs-eval#53); T2 called_no_file: ; T3 not_called: Screenshot.m_92289_ 不存在（非 1.20.1 SRG 运行时）;…
MT_CHANNEL: delegated_count=3
MT_CASE_RESULT: RELEASE150-26.1.2 = FAIL
```

`op=shot` 三条 T4 及之后档位原文在抄本中被截断（`T4 call…`），未做二次运行补全，按截断如实留档。

## 5. 27 条断言逐条状态（测试台流水号 32–58）

| 流水 | 断言要点 | 状态 | 取证要点 |
|---|---|---|---|
| 32 | `log(scope=whole)` 匹配 `/astral_dice-1\.5\.0\+neoforge_26\.1\.2\.jar/` | **FAIL(SOURCE_ECHO)** | 原文：`命中 2 行但无一是平台输出行（回显/续行/平台行内的 eval 源码载荷都不构成证据）（latest:latest.log@0B(win=whole)）L228 [echo] - astral_dice (jar(mods/astral_dice-1.5.0+neoforge_26.1.2.jar))`；实测 latest.log **L228 存在平台原文** `- astral_dice (jar(mods/astral_dice-1.5.0+neoforge_26.1.2.jar))`，故实质事实成立、失败源于 `win=whole` 证据源读到 0 字节（测试台取证缺陷） |
| 33 | `ver=26.1.2 gamemode=creative` | PASS | run5 窗口平台行（绝对 L8518） |
| 34 | `pos=100,-?[0-9]+,100` | PASS | L8518 `pos=100,109,100` |
| 35 | `hp=20` | PASS | L8518 |
| 36 | `item=astral_dice:abyss_reagent_chip` | PASS | L8518 |
| 37 | `xp=30` | PASS | L8518 |
| 38 | `slotted=[1-9][0-9]*` | PASS | L8518 `slotted=6` |
| 39 | `chips=3` | PASS | L8518 |
| 40 | `modver=1.5.0+` | PASS | L8518 `modver=1.5.0+neoforge_26.1.2` |
| 41 | chip `count=3 name=(深渊试剂|Abyss Reagent)` | PASS | L8556 `count=3 name=深渊试剂` |
| 42 | `tip i=0 text=.+` | PASS | L8557 `tip i=0 text=深渊试剂` |
| 43 | `guide key=astral_dice.guide.entry.abyss_reagent_chip.1 text=.+` | PASS | L8563（命中 2 行、其中平台行 1 行） |
| 44 | `inventory open=1` | PASS | L8605 `inventory open=1 hover=133,172 creative=1 glfw=1` |
| 45 | `CHIP_SHOP_OPEN player=` | PASS | 平台行（窗口内 L692，绝对 L9163 附近） |
| 46 | `screen=\S*ChipShopScreen` | PASS | L9172 `screen=com.merlinkitsune.astral_dice.screen.ChipShopScreen` |
| 47 | `sys id=astral_dice:dice ok=1` | PASS | L10279 |
| 48 | `sys id=astral_dice:attack_card_medium ok=1` | PASS | L10280 |
| 49 | `sys id=astral_dice:abyss_reagent_chip ok=1` | PASS | L10281 |
| 50 | `sys id=astral_dice:jill_sign ok=1` | PASS | L10282 |
| 51 | eval `from=state` 正则（pos + item 同现） | PASS | 实得 `"[REL150] ver=26.1.2 gamemode=creative pos=100,109,100 hp=20 foo…` |
| 52 | eval `from=chip` 正则 `^"?(深渊试剂|Abyss Reagent)"?$` | PASS | 实得 `"深渊试剂"` |
| 53 | eval `from=screen` 正则 `ChipShopScreen"?$` | PASS | 实得 `"com.merlinkitsune.astral_dice.screen.ChipShopScreen"` |
| 54 | 无“命令失败/无玩家”类文案 | PASS | `MT_ASSERT_ABSENT: PASS`（`win=case`，579,914 B） |
| 55 | jar 资产下限 | PASS | `models=158>=120 entries=2452>=1700 signs=32>=25` |
| 56 | 无新增崩溃 | PASS | `本次运行无新增崩溃报告（基线 1 个）` |
| 57 | 截图 `abyss_reagent_tooltip` | **BLOCK** | 六档签名全败，见 §7.2 |
| 58 | 截图 `chip_shop_ui` | **BLOCK** | 同上 |

> 注意：33–56 的“窗口内行号”（L48/L86/L79/L135/L692/L702/L1809…）是**用例窗口切片内的相对行号**（该切片 `win=case` 为 579,914 B）；绝对行号见 §6。

## 6. 探针原文（[REL150]，run5 窗口 04:39:19–04:39:48，绝对行号）

`run/26.1.2/prodgame/logs/latest.log` 最终 684,198 B / 10,283 行；下述行为**本轮**（04:39 段）末次出现，非历史轮次：

```
L8518 [0211月2026 04:39:19.700] [Server thread/INFO] [KubeJS Server/]: autojs-eval#23: [REL150] ver=26.1.2 gamemode=creative pos=100,109,100 hp=20 food=20 xp=30 arm=0 slotted=6 chips=3 item=astral_dice:abyss_reagent_chip dim=minecraft:overworld modver=1.5.0+neoforge_26.1.2
L8556 [0211月2026 04:39:19.782] [Render thread/INFO] [KubeJS Client/]: autojs-eval#28: [REL150] chip id=astral_dice:abyss_reagent_chip count=3 name=深渊试剂 tipn=6 path=screen
L8557 [0211月2026 04:39:19.782] [Render thread/INFO] [KubeJS Client/]: autojs-eval#29: [REL150] tip i=0 text=深渊试剂
L8558 [REL150] tip i=1 text=栏位: 筹码
L8559 [REL150] tip i=2 text=            （空行）
L8560 [REL150] tip i=3 text=装备时治愈层数 +2。触发治愈回血时：获得实际治疗量 × 2 的伤害吸收
L8561 [REL150] tip i=4 text=            （空行）
L8562 [0211月2026 04:39:19.783] [Render thread/INFO] [KubeJS Client/]: autojs-eval#29: [REL150] tip i=5 text=当前治愈：0 / 32
L8563 [0211月2026 04:39:19.783] [Render thread/INFO] [KubeJS Client/]: autojs-eval#31: [REL150] guide key=astral_dice.guide.entry.abyss_reagent_chip.1 text=装备时治愈层数 +2；触发治愈回血时，获得「实际治疗量 ×2」的伤害吸收。
L8605 [0211月2026 04:39:19.851] [Render thread/INFO] [KubeJS Client/]: autojs-eval#36: [REL150] inventory open=1 hover=133,172 creative=1 glfw=1
L9148 [0211月2026 04:39:29.319] [Render thread/INFO] [KubeJS Client/]: autojs-eval#1: [REL150] screen_closed=1
L9163 [0211月2026 04:39:29.336] [Server thread/INFO] [KubeJS Server/]: autojs-eval#8: [REL150] shop_open invoked=1 show=reset
L9172 [0211月2026 04:39:30.785] [Render thread/INFO] [KubeJS Client/]: autojs-eval#5: [REL150] screen=com.merlinkitsune.astral_dice.screen.ChipShopScreen
L10279 [0211月2026 04:39:48.801] [Server thread/INFO] [KubeJS Server/]: autojs-eval#9: [REL150] sys id=astral_dice:dice ok=1 name=骰子
L10280 [0211月2026 04:39:48.802] [Server thread/INFO] [KubeJS Server/]: autojs-eval#9: [REL150] sys id=astral_dice:attack_card_medium ok=1 name=攻击(中)
L10281 [0211月2026 04:39:48.802] [Server thread/INFO] [KubeJS Server/]: autojs-eval#9: [REL150] sys id=astral_dice:abyss_reagent_chip ok=1 name=深渊试剂
L10282 [0211月2026 04:39:48.802] [Server thread/INFO] [KubeJS Server/]: autojs-eval#9: [REL150] sys id=astral_dice:jill_sign ok=1 name=调酒师立牌
```

探针侧的一条失败记录（与断言 12 无冲突，但需留档）：

```
L9725 [0211月2026 04:39:39.125] [Render thread/INFO] [KubeJS Client/]: autojs-eval#5: [REL150] guide_open=ERR:InternalError: Failed to load Java class 'net.minecraft.resources.ResourceLocation': Class could not be found!
```

含义：用例 step 27（经 PatchouliAPI 打开 `astral_dice:astral_guide`）在本轮**仍未能成功开书**，失败在测试脚本取类阶段（KubeJS Client 走 AutoJS 内核时 `net.minecraft.resources.ResourceLocation` 不可见），非模组侧异常。`release150_guide.png` 因此**不能**当作“手册页已打开”的证据；断言 43（guide key/text）之所以 PASS，靠的是 L8563 直接读语言键文本，不依赖开书成功。

## 7. 截图证据

### 7.1 落盘事实（本轮窗口内，均为有效 PNG）

目录：`run/26.1.2/prodgame/screenshots/`（时间同时给 UTC 与本地 UTC+8）：

| 文件 | 字节 | mtime (UTC) | mtime (本地) | sha256 | 尺寸 |
|---|---|---|---|---|---|
| `abyss_reagent_tooltip.png` | 151,303 | 2026-11-01T20:39:20.938Z | 2026-11-02 04:39:20 | `8a9c2277d141526384712f32a56abb0edc96ed7eca92aa8dbefb55b7d845fbc2` | 854x480 |
| `chip_shop_ui.png` | 129,916 | 2026-11-01T20:39:30.874Z | 2026-11-02 04:39:30 | `d3ff680371a67cd6c723a364dd578deeae6245b0dc397c3d35265208b2e6df9e` | 854x480 |
| `release150_guide.png` | 129,591 | 2026-11-01T20:39:40.647Z | 2026-11-02 04:39:40 | `3fabcfba6a24c7ecdd9d769c489c769ad7dfad24e4701e179f8f32c4c79e6ada` | 854x480 |

辅助事实：

- 三个文件 PNG 魔数均为 `89504e470d0a1a0a`，尺寸 854x480，字节数均 >20 KB，mtime 全部落在用例窗口（04:39:19–04:39:48）内。
- 三者 sha256 均**不同于**轮转备份 `*.png.prev-20261102-040635`（分别 `300581bfdde6714e…` / `de0ebcdb578910c8…` / `124882558347864d…`），内容为本轮新产，不是旧图复制。
- 同窗口内游戏自身还产出 15 张时间戳截图（`2026-11-02_04.34.51` ～ `2026-11-02_04.39.44`，99–195 KB），说明屏幕抓取链路本身可用。
- `.mt_shots.json`（`run/26.1.2/screenshots/.mt_shots.json`）仍为 12 条历史条目（run_id `20260926-230116`），**没有**本轮三个 tag 的新条目；`prodgame/screenshots/` 下不存在 `.mt_shots.json`。按任务书要求未手工登记 tag。

### 7.2 为什么断言 57/58 仍判 BLOCK（六档签名原文）

测试台对三张截图依次尝试六档签名，全部失败，按其判定规则降级为 `channel=delegated status=delegated` 并让 vision 断言 BLOCK。抄出原文（T1/T2/T3 完整，T4 在抄本中被截断）：

```
T1 not_called: InternalError: Can't find method net.minecraft.client.Screenshot.grab(java.io.File,string,com.mojang.blaze3d.pipeline.MainTarget,adapter1). (autojs-eval#53)
T2 called_no_file: 
T3 not_called: Screenshot.m_92289_ 不存在（非 1.20.1 SRG 运行时）
T4 call…（抄本截断）
```

一致性说明：`T2 called_no_file` 与“文件确实存在”并存，指向**取图调用与落盘/查找路径不一致**（调用走了一条能写文件的路，检测却按另一路径/另一时机判定），属测试台侧问题；本轮**未**修改 `scripts/test/**`，也**未**为凑判据做任何补拍或改名。

### 7.3 vision 断言（不自判）

- 断言 57（`abyss_reagent_tooltip`）：**DELEGATED**，图路径 `run/26.1.2/prodgame/screenshots/abyss_reagent_tooltip.png`
- 断言 58（`chip_shop_ui`）：**DELEGATED**，图路径 `run/26.1.2/prodgame/screenshots/chip_shop_ui.png`
- 补充留档（非断言）：`run/26.1.2/prodgame/screenshots/release150_guide.png`

> 按任务书：两条 vision 的内容判读交由外部执行，本报告不给出 PASS/FAIL 结论。

## 8. A 类归零取证（Missing sound for event）

检索文本：本轮 `run/26.1.2/prodgame/logs/latest.log`（684,198 B / 10,283 行，单轮窗口 04:31–04:39）。

| 检索式 | 命中数 |
|---|---|
| `Missing sound for event: astral_dice:event_` | **0** |
| `Missing sound for event:`（放宽全模组） | **0** |

对照：上一轮同一检索式命中 8 条（对应 `event_bonus/event_damage/event_fade_in/event_fade_out/event_heal/event_poisoning/event_tick/event_transport` 全缺）。本轮 8 条 sound event 已随 jar 内 `assets/astral_dice/sounds.json`（23 键含 8 条 `event_*`）生效而全部消失；装进测试台的 jar sha256 已复核为 `b422d57d…878d`，与准备阶段一致。

## 9. B 类归零取证（astral_guide 数据文件解析失败）

| 检索式 | 命中数 |
|---|---|
| `Couldn't parse data file 'astral_dice:astral_guide'` | **0** |

该条上一轮为“缺 `patchouli:book` 组件”导致的数据文件解析失败；本轮未再出现任何 `astral_dice:astral_guide` 相关 parse 错误（放宽到 `Couldn't parse data file 'astral_dice` 前缀同样 0 命中）。

## 10. 硬门与版本核对

| 硬门 | 命中数 |
|---|---|
| `err: 5xx` | 0 |
| `Failed to load native lib` | 0 |
| `must be registered` | 0 |
| `Unsatisfied runtime environment` | 0 |
| YSM `err: 54` | 0 |
| 全日志 `试剂-深渊` | **0** |

版本核对：`1.5.0+neoforge_26.1.2` 在 latest.log 共 **9 处**，代表性行：

- L38 mod 列表：`Astral Dice 1.5.0+neoforge_26.1.2 (astral_dice)`
- L228 jar 归属：`- astral_dice (jar(mods/astral_dice-1.5.0+neoforge_26.1.2.jar))`
- L346 嵌套：`- starengine (jar(mods/astral_dice-1.5.0+neoforge_26.1.2.jar > starengine-neoforge-26.1.2-1.1.0.jar))`
- L717 版本互通门槛：`[Astral Dice] 版本互通门槛(Version Gate):mod_version=1.5.0+neoforge_26.1.2 → 网络互通号=1.5;…`
- 探针行 L8518（`modver=1.5.0+neoforge_26.1.2`）等

`1.5.0-rc.` 形式：latest.log **0 命中**（无需指行号）。仅文件系统层仍留有准备阶段之前的历史 `.bak`（`astral_dice-1.5.0-rc.7+neoforge_26.1.2.jar.bak-20261102-033248` 与 `.compat-on.bak`），属复原目录里的历史件，不是本轮生效版本。

## 11. 日志归类：本模组 1 条 WARN（不达标项）

按任务书“本模组相关 ERROR/WARN 必须为 0”口径逐条过滤 `com.merlinkitsune.astral_dice` / `[Astral Dice]` 的 WARN/ERROR：**命中 1 条**。

```
L508 [0211月2026 04:31:06.287] [modloading-worker-0/WARN] [com.merlinkitsune.astral_dice.event.WaystoneWarpCompat/]: [Astral Dice] Failed to register Waystone teleport integration: java.lang.ClassNotFoundException: net.blay09.mods.waystones.api.event.WaystoneTeleportEvent$Pre
```

归属与影响判定：

- 该 WARN 来自本模组的 Waystone 软兼容注册器，触发原因是在**未安装 Waystones** 的实例里直接 `Class.forName` 失败；功能上是“可选兼容不可用”，不影响本模组主体（本轮 27 条功能断言除证据源问题外全绿）。
- 但按任务书口径（本模组 WARN/ERROR 必须为 0），本条**如实记为不达标**，不以“环境缺少可选依赖”解释过去。
- 上一轮日志（`latest.log.prev-20261102-040635`，1,830 行）中同一条 0 命中，故这是本轮**新出现/新可见**的一条，不是沿用历史。
- 建议（不在本轮修改范围内，未改源码）：软兼容注册失败应下调为 DEBUG 或首启动一次性提示，避免在无 Waystones 的整合包里每次启动刷 WARN。

本模组其余日志均为 INFO（如 L666 `Astral Dice mod loaded.`、L667 `May the god of the dice be with you!`、L713 Jade 插件加载、L1353 `Iceberg 提示框颜色缓存缓解已启用`）。

## 12. 第三方 WARN/ERROR 归属（不含本模组）

全日志 WARN/ERROR 合计 **236 行 / 35 个来源**，剔除本模组 1 条后 235 行，按来源归组（前 20）：

| 次数 | 级别 | 来源 | 样例（截断） |
|---|---|---|---|
| 84 | WARN | `patchouli` | Could not locate JEI keybindings, lookups in books may not work |
| 42 | WARN | `mixin` | Reference map 'kuma_api.refmap.json' … could not be read（dev 环境可忽略） |
| 17 | WARN | `LanguageProviderLoader` | Mod jar(c2me-neoforge-mc26.1.2-0.4.0-alpha.0.100.jar) 语言合并提示 |
| 11 | ERROR | `euphoria_patcher` | EuphoriaPatcher: === SHADER NOT FOUND === |
| 10 | ERROR | `ftblibrary.icon.EntityIconLoader` | Entity alexsmobs:lobster is using legacy invisible texture |
| 10 | WARN | `ModelManager` | Missing texture references in model functionalstorage:block/lock |
| 8 | WARN | `ModernFix` | Option 'mixin.perf.release_protochunks' overriden (by mods [c2me]) |
| 6 | WARN | `Iris` | Found fine program match for neoforge:pipeline/entity_smooth_cutout_cull |
| 6 | WARN | `RecipeManager` | Recipe functionalstorage:compacting/amethyst can't be placed due to empty ingredients |
| 5 | WARN | `VersionChecker` | Failed to process update information |
| 4 | WARN | `KubeJS` | FTBQuestsKubeJSPlugin does not have required mod 'ftbquests' loaded, skipping |
| 3 | WARN | `euphoria_patcher` | No shaders with expected name pattern found… |
| 3 | WARN | `ServerLifecycleHooks` | alexsmobs:stradpole 生物分类不一致 |
| 2 | WARN | `Sodium-Workarounds` | NVIDIA_THREADED_OPTIMIZATIONS_BROKEN |
| 2 | WARN | `JarSelector` | Attempted to select a dependency jar … resourcefulconfig |
| 2 | WARN | `DeferredWorkQueue/LOADING` | Mod 'xaeroworldmap' took 1.089 s to run a deferred task |
| 2 | WARN | `GlProgram` | neoforge:pipeline/item_cutout_unlit shader … sampler Sampler2 |
| 2 | WARN | `AtlasManager` | Duplicate sprite rechiseled:item/chisel |
| 1 | WARN | `CrashAssistantJarInJarHelper` | Closed old CrashAssistantApp process… |
| 1 | WARN | `Sodium` | Force-disabling mixin 'features.render.world.sky.LevelRendererMixin' |

说明：`euphoria_patcher` 的 SHADER NOT FOUND 与 `EntityIconLoader` 的 legacy texture 是 ERROR 级但均属第三方资源/兼容提示；无一条指向 `com.merlinkitsune.astral_dice`。YSM 在本形态下 `err: 54` 为 0，与任务书预期一致。

## 13. 崩溃报告

`run/26.1.2/prodgame/crash-reports/` 仅有 1 个文件：

- `crash-2026-11-02_04.27.07-client.txt`（52,464 B，2026-11-01T20:27:08Z）—— 生成于**失败 2**（BAT 缺失，见 §3.2），已在起跑前计入基线。

本轮成功运行（pid 29988）期间**未新增**任何崩溃报告，与断言 56 `本次运行无新增崩溃报告（基线 1 个）` 一致。

## 14. 收尾与复原

### 14.1 收停

命令：`pwsh -NoProfile -File scripts/test/mt.ps1 --version 26.1.2 --phase stop --force`（**未加** `--purge-saves`）。输出原文：

```
===== MT_PHASE: stop =====
MT_CLEANUP: 指定了 --version，保留 Gradle 守护进程
MT_CLEANUP: 已按 --force 清除失败取证标记 D:\MCProject\astral_dice_multiloader\scripts\test\cases\.mt_keep_alive-26.1.2
MT_CLEANUP_SAVES: KEPT（未指定 --purge-saves）
MT_CLEANUP: 已收停 1 个本流程进程（范围：26.1.2）
MT_CLEANUP_RESULT: OK
MT_STOP: OK (1s) — 收停完成，无本流程残留进程
```

### 14.2 收停后状态核验（`run/26.1.2/.tmp/_rerun-20261102/poststop.ps1`）

```
JAVA_TOTAL=6
JAVA_WITH_RUN2612=0
LISTEN_25585=0 LISTEN_25565=0 LISTEN_25575=0
```

- 残留 java 6 个均为**既有他人工作**（pid 37020/5088 Zulu-21 编译进程、29924 gradle wrapper、37252 fabric devlaunchinjector、28820/1568 Zulu-25），命令行均不含 `run/26.1.2`。
- 三个端口均无 LISTEN（25565/25575/25585）。
- AutoJS 端点复验：`mcp__autojs__status` → `DOWN: fetch failed`（进程已退，端点自然关闭），与端口释放互证。
- 未 kill 任何非本方进程（本轮全部进程操作仅针对 pid 28972 / 18212 / 29988）。

### 14.3 mods 复原

1. 把本轮组装目录整体移出（不删除）：`run/26.1.2/prodgame/mods`（97 条目）→ `run/26.1.2/.tmp/_rerun-20261102/mods-rerun-assembled-20261102-0450`（97 条目，保留为本轮证据）。
2. 把准备阶段 rename 保留的原目录还原：`run/26.1.2/.tmp/_prod-mods-orig-20261102-040635` → `run/26.1.2/prodgame/mods`。
3. 复原后实读：**17 条目**，其中：
   - `astral_dice-1.5.0+neoforge_26.1.2.jar` **2,875,237 B**，sha256 `dd22295c7c4036ae0cb95a2cc5b861de30f5cbbf4e7a8859211830874dac53a1`（即**修复前**那版 sounds.json，会重现 8 条 missing sound）
   - `astral_dice-1.5.0-rc.7+neoforge_26.1.2.jar.bak-20261102-033248`、`astral_dice-1.5.0-rc.7+neoforge_26.1.2.jar.compat-on.bak`（历史 `.bak`，非 jar 生效件）
   - `AutoJS-neoforge-26.1-1.0.0.jar`、`kubejs-neoforge-26.1.2-8.0.6.jar`、`rhino-2101.2.8-build.91.jar`、`better-advanced-tooltips-2601.1.0-build.10.jar`
   - `collective-26.1.2-8.32.jar`、`curios-dRnRThvD.jar`、`ferritecore-9.0.0-neoforge.jar`、`iris-neoforge-1.11.4+mc26.1.2.jar`、`modernfix-neoforge-5.27.22+mc26.1.2.jar`、`ModernUI-NeoForge-26.1.2-3.13.0.5-universal.jar`、`sodium-neoforge-0.9.1+mc26.1.2.jar`、`starengine-neoforge-26.1.2-1.1.0.jar`、`superflatworldnoslimes-26.1.2-3.6.jar`、`[是，史蒂夫模型] ysm-2.6.5-neoforge+mc26.1-hotfix.jar`

**关于“两个测试注入 jar 已随复原移除”的更正**：准备前的原目录**本身就已经含** `kubejs-neoforge-26.1.2-8.0.6.jar` 与 `AutoJS-neoforge-26.1-1.0.0.jar`（还含 `rhino` 与 `better-advanced-tooltips`）。因此复原后这两个注入 jar **仍在**（这是准备前原状），随复原移出的是**本轮组装出来的那套 97 件**（已整体移到 `mods-rerun-assembled-20261102-0450`，含本轮补回的 rhino/BAT 与排除的 mru/sounds 的位置）。

## 15. 与任务书口径的偏差 / 未达成项（逐条说明）

| # | 任务书预期 | 实际 | 说明 |
|---|---|---|---|
| 1 | 端口门用 `Get-NetTCPConnection -State Listen` 过滤 25585 | 该口径在本机对 AutoJS 监听**假阴性** | 门开时确实 0 行（不影响起跑），但监听建立后仍为 0 行；改用 AutoJS status 作就绪门，并在此留档（前置 agent 误判由此而来） |
| 2 | 97 件 = 玩家 95 + 注入 2 直接可起 | 起跑前需补 `rhino` 与 `better-advanced-tooltips`，并排除 `mru` + `sounds` | 实际受测集合 = 玩家 95 − `mru` − `sounds` + `rhino` + `better-advanced-tooltips` = 97；排除件与哈希见 §3.1 `EXCLUSION-RECORD.txt` |
| 3 | 断言 1（jar 行 log 断言）应 PASS | FAIL(SOURCE_ECHO)，`win=whole` 证据源 0 字节 | 平台事实存在（L228），失败是取证窗口缺陷；未改测试台、未改判 |
| 4 | 截图应产出并支撑 2 条 vision | 断言 57/58 BLOCK（六档签名全败），但三个 PNG 实际落盘 | 不补拍、不改名、不手工登记 tag；vision 仅 DELEGATED |
| 5 | 本模组 ERROR/WARN 必须 0 | **1 条 WARN（L508，Waystone 软兼容）** | 如实记不达标，见 §11 |
| 6 | step 27 打开帕秋莉手册作为留档 | 开书失败（脚本侧取类失败，L9725） | 不作 PASS 判据，只留档说明 `release150_guide.png` 不是手册页证据 |
| 7 | 复原后“注入 jar 已移除” | 复原后 17 条目**仍含** kubejs/AutoJS | 见 §14.3 更正 |

## 16. 证据清单（全部为本轮新产或本轮实读）

| 用途 | 路径 |
|---|---|
| 正式用例抄本（run5） | `run/26.1.2/.tmp/_rerun-20261102/case-run5.log` |
| 成功运行日志（launcher 侧） | `run/26.1.2/.tmp/prod-run.log` |
| 失败 1 日志 | `run/26.1.2/.tmp/prod-run.log.fail1-launch-20261102-0429` |
| 失败 2 日志 | `run/26.1.2/.tmp/prod-run.log.fail2-crash-20261102-0430` |
| 游戏侧日志（取证主源） | `run/26.1.2/prodgame/logs/latest.log`（684,198 B / 10,283 行） |
| 上一轮日志（A/版本对照） | `run/26.1.2/prodgame/logs/latest.log.prev-20261102-040635`（1,830 行） |
| 崩溃报告（基线 1 个） | `run/26.1.2/prodgame/crash-reports/crash-2026-11-02_04.27.07-client.txt` |
| 三张新图 | `run/26.1.2/prodgame/screenshots/{abyss_reagent_tooltip,chip_shop_ui,release150_guide}.png` |
| 截图登记表（未新增条目） | `run/26.1.2/screenshots/.mt_shots.json` |
| 本轮组装目录（97 件，已移出） | `run/26.1.2/.tmp/_rerun-20261102/mods-rerun-assembled-20261102-0450/` |
| 排除件与理由 | `run/26.1.2/.tmp/_rerun-20261102/mods-excluded/`（含 `EXCLUSION-RECORD.txt`） |
| 启动/核验脚本 | `run/26.1.2/.tmp/_rerun-20261102/launch2.ps1`、`poststop.ps1` |
| 复原后的原状 mods | `run/26.1.2/prodgame/mods`（17 条目，旧 astral 2,875,237 B） |

## 17. 结论

1. **A 类（8 条 missing sound）已实证归零**，与 jar 修复一致（sha256 复核通过）。
2. **B 类（astral_guide 数据文件解析失败）已消失**，0 命中。
3. 硬门四项全 0、生效版本正确（`1.5.0+neoforge_26.1.2`，无 rc 形式）、显示名与探针全部符合预期，功能断言 24/27 PASS。
4. 用例机读结论仍为 **FAIL**，但失败项均为**测试台侧证据通路**问题（`win=whole` 读到 0 字节、截图六档签名全败），未发现任何模组功能回归；两条 vision 交由外部判读。
5. **唯一与模组本体相关的遗留问题**：`WaystoneWarpCompat` 在无 Waystones 环境下每次启动刷 1 条 WARN（L508），按任务书口径记为不达标，建议后续版本降级为 DEBUG。
6. 收尾干净：stop OK、无 `run/26.1.2` 残留 java、端口全释放、mods 已复原为准备前 17 条目原状（复原目录仍含 kubejs/AutoJS，属原状本身）。

