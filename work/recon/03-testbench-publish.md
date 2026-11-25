# 03 · 实机测试取证 + 发布推送 —— 真实可用状态取证报告

- 取证时间：2026-11-02 01:37–02:05 +0800（Asia/Hong_Kong）
- 工作树：D:\MCProject\astral_dice_multiloader　分支 multi-main　HEAD fd990c15（2026-11-01 14:42:49 +0800）
- 方式：**纯只读**。未运行 gradlew、未启动游戏、未杀进程、未做任何 git 写操作。唯一写入 = 本文件。
- 纪律：每条结论带「文件:行号」或命令原文；取不到写「无法判定」。
- 说明：取证期间实机进程状态有变化（早先快照 2 个 fabric 客户端 → 复核时 1 个），下文以 **01:4x 复核快照**为准。

---

# A. 实机测试台（Windows 原生 PowerShell 7）

## A0. 先厘清「哪个入口是真的」——本仓有两层台子（本次最重要发现）

| 层 | 文件 | 状态 | 适用范围 |
|---|---|---|---|
| **pwsh 主台** | **scripts/test/mt.ps1**（998 行） | 在用；全流程**默认排除 fabric** | 1.21.1 / 1.20.1 / 26.1.2 |
| **bash 编排层** | **scripts/test/win/mt.sh**（15,375 B）+ **live.sh**（16,569 B，2026-11-02 00:20 刚改） | 与 pwsh 同一套开关/阶段/退出码（**scripts/test/win/mt.sh:5-6**）；cases/report/preflight 仍委托 pwsh 并打 MT_DEPRECATED（**:7-8**） | 四线；**fabric-1.20.1 的唯一入口** |

- pwsh 全流程排除 fabric 的理由：**scripts/test/mt.ps1:97-104**（$pwshFlowExcludes）—— mt_env/mt_loadergate 未按 fabric 适配会假 BLOCKED。
- bash 层分工说明：「2026-11-01 更新：bash 反向编排 pwsh」—— **scripts/test/win/README.md:169**。
- 结论：**非 fabric 线用 mt.ps1；fabric-1.20.1 用 scripts/test/win/mt.sh（或批次编排 live.sh）**。两条不要混用。

## A1. mt.ps1 用法（含可直接复跑的命令）

**帮助行为（实测，坑）**：pwsh -NoProfile -File ...\mt.ps1 -? **不打印用法块**，只打印 [CmdletBinding()] 语法后 exit 0（pwsh 把 -? 当公共参数吃掉）。要读用法必须用 --help / -h：实现见 **scripts/test/mt.ps1:643-650**（Get-Content … -Skip 1 -First 25）。

用法块原文 = **scripts/test/mt.ps1:2-26**，关键两行：

- **scripts/test/mt.ps1:15** → 阶段: preflight | build | env | launch | cases | report | stop（内部另有 summary / cleanup / timeout-stop）
- **scripts/test/mt.ps1:11-13** → --version <V>：全流程分支同样尊重该参数，指定单版本只跑该版本且**不做跨版本门控**

### A1.1 --version 可选值（事实源 = scripts/test/lib/Mt.Paths.psm1:41-43）

| --version | 子项目 | 整合包 mods 源（Mt.Paths.psm1:63-68） |
|---|---|---|
| 1.21.1 | neoforge-1.21.1 | D:\.minecraft\versions\狐の航空学 Voxy Edition\mods |
| 1.20.1 | forge-1.20.1 | …\1.20.1-Forge 模组测试\mods |
| 26.1.2 | neoforge-26.1.2 | …\26.1.2-NeoForge 模组测试\mods |
| fabric-1.20.1 | fabric-1.20.1 | …\1.20.1-Fabric 模组测试\mods（双 run 根 run/client 与 run/server） |

### A1.2 --phase 可选值

preflight | build | env | launch | cases | report | stop。语义：

- --phase stop 走独立分支 **scripts/test/mt.ps1:813-820**，透传 --force / --keep-daemon / --purge-saves 给 mt_stop.ps1。
- --phase cases 预算自适应：单条 = 单条硬上限 + 90s；全目录 = 用例数 × 单条上限 + 90s（**scripts/test/mt.ps1:512-538**）。
- 单阶段可被 MT_<阶段>_TIMEOUT_SEC 覆写（**scripts/test/mt.ps1:71-87**）。

### A1.3 如何指定用例：**只有 --case，没有 --cases / --suite**

- 参数表 **scripts/test/mt.ps1:601-630** 里用例相关键只有 case（**:605** → Kind='Value'; Key='CasePath'），不存在 cases / suite 键；未知参数 → MT_ERROR: 未知参数 <x> + exit 2（**:638-639**）。
- --case 的值**既可以是路径也可以是裸用例名**，解析规则 **scripts/test/mt_case.ps1:2175-2197**（Resolve-MtCasePath）：
  绝对路径 → TEST_DIR 相对 → 补 .json 后到 TEST_DIR/cases 下找 → 最后按 cwd 解释。
  ⇒ --case ACTIONBAR-DIAG-1.21.1 与 --case scripts/test/cases/ACTIONBAR-DIAG-1.21.1.json 等价。
- 全目录（= 该线全部用例）：--phase cases **不带** --case，按 *-<version>.json 计数（**scripts/test/mt.ps1:527-530**）。该形态被批量闸门禁用（见 A1.6）。
- 另有**离线校验**子命令（不起游戏、不写 run 目录）：**scripts/test/mt_case.ps1:15** → pwsh -NoProfile -File scripts/test/mt_case.ps1 validate --case cases/xxx.json。

### A1.4 如何出截图

截图是**用例里的一个步骤**（不是 mt.ps1 开关）：op: screenshot，实现 **scripts/test/mt_case.ps1:2099-2134**。

- 通道 autojs：Invoke-MtChannelShot → run/<ver>/screenshots/<tag>.png
- 通道 native：调 mt_capture.ps1 capture --version <v> --tag <tag> --mode <f2|window>（crop:true 追加 --crop-to-window）
- 手工单独抓图：pwsh -NoProfile -File D:\MCProject\astral_dice_multiloader\scripts\test\mt_capture.ps1 capture -Version 1.21.1 -Tag mytag -Mode window
- 模式语义 **scripts/test/mt_capture.ps1:12-14**：f2 = 注入 F2 让游戏自己落盘；window = .NET System.Drawing 整屏抓（默认不裁剪，-CropToWindow 才裁）；DPI 必须 PER_MONITOR_AWARE_V2，否则 3840×2160 会抓成 2560×1440。
- ⚠️ **mt_case.ps1:2099-2134** 注释明写：screenshot 步骤自身约 1.9s 前置开销，瞬态画面要 mode=window，否则「注入 F2 → 等游戏落盘」的 3×10s 重试会错过瞬态窗口。

### A1.5 报告落在哪个目录

- reports/<run_id>/<version>/report.md 与 reports/<run_id>/SUMMARY.md —— **scripts/test/mt_report.ps1:6-8**；证据复制 Copy-MtEvidence **mt_report.ps1:294-340**。
- 实测目录形状（scripts/test/reports/20260926-230116/）：<version>/ 下有 report.md、latest.log、debug.log、kubejs_server.log、runclient_launch.log、screenshots/*.png。
- 现存 run 只有 3 个：20260926-230116、20260926-megas、20260927-megas-fix（+ _template.md）。**最近一次完整报告是 2026-09-26/27，之后没有新报告落盘。**

### A1.6 如何 stop 并清理：--purge-saves 到底做什么

原文（**scripts/test/mt.ps1:51-58** 头注释）：

- 收尾必须关测试端并清旧存档；--phase stop **默认不清存档**（CHIP-RELOG 这类「重登」用例要跨 stop 保留存档），**只有显式 --purge-saves 才清**。
- .mt_keep_alive-<版本>（兼容旧的无后缀名）存在 ⇒ 只提示、不杀进程、保留现场。
- 全流程退出时的清理由 mt_cleanup.ps1 run --quiet --purge-saves 自动完成（**:53**）；单版本全流程只收停本线，**保留 Gradle 守护**（**:43-44**）。

⚠️ **自动批量闸门（2026-09-27 用户规则「禁用自动测试，纯手动下达命令」）**：**scripts/test/mt.ps1:768-807** 拦四类 —— ① 无 --phase 的全流程；② --phase cases 不带 --case；③ --phase report；④ 无 --version 的单阶段。放行 build/env/launch/stop 与「单条 --phase cases --case」。临时放行 --allow-auto（或 MT_ALLOW_AUTO=1），报错原文 MT_AUTO_DISABLED: … 已被禁用。
⇒ **推论：--phase report 单独跑必然被闸门挡；报告只能由「带 --allow-auto 的全流程」产出。**

### A1.7 可直接照抄（pwsh 主台，非 fabric）

```powershell
# 0) 读用法（唯一正确方式；-? 无效）
pwsh -NoProfile -File D:\MCProject\astral_dice_multiloader\scripts\test\mt.ps1 --help

# 1) 前置检查（最安全、不启动游戏）
pwsh -NoProfile -File D:\MCProject\astral_dice_multiloader\scripts\test\mt.ps1 --version 1.21.1 --phase preflight

# 2) 构建（会跑 gradlew，属重操作）
pwsh -NoProfile -File D:\MCProject\astral_dice_multiloader\scripts\test\mt.ps1 --version 1.21.1 --phase build

# 3) 环境装配（放 jar 进 run/<ver>/mods、拉探针模组）
pwsh -NoProfile -File D:\MCProject\astral_dice_multiloader\scripts\test\mt.ps1 --version 1.21.1 --phase env

# 4) 启动（起 dev 客户端；--side 仅 fabric 有效）
pwsh -NoProfile -File D:\MCProject\astral_dice_multiloader\scripts\test\mt.ps1 --version 1.21.1 --phase launch

# 5) 单条用例（裸名或路径均可）
pwsh -NoProfile -File D:\MCProject\astral_dice_multiloader\scripts\test\mt.ps1 --version 1.21.1 --phase cases --case ACTIONBAR-DIAG-1.21.1

# 6) 只做离线校验（不起游戏）
pwsh -NoProfile -File D:\MCProject\astral_dice_multiloader\scripts\test\mt_case.ps1 validate --case D:\MCProject\astral_dice_multiloader\scripts\test\cases\ACTIONBAR-DIAG-1.21.1.json

# 7) 收停（默认保留存档）
pwsh -NoProfile -File D:\MCProject\astral_dice_multiloader\scripts\test\mt.ps1 --version 1.21.1 --phase stop

# 8) 收停 + 清旧存档 + 强杀
pwsh -NoProfile -File D:\MCProject\astral_dice_multiloader\scripts\test\mt.ps1 --version 1.21.1 --phase stop --purge-saves --force

# 9) 出报告的唯一途径：全流程 + 显式放行（会真跑全流程，慎用）
pwsh -NoProfile -File D:\MCProject\astral_dice_multiloader\scripts\test\mt.ps1 --version 1.21.1 --allow-auto
```

fabric-1.20.1 专线（bash 层）：

```bash
cd /d/MCProject/astral_dice_multiloader
bash scripts/test/win/mt.sh --version fabric-1.20.1 --side server --phase launch
bash scripts/test/win/mt.sh --version fabric-1.20.1 --side client --phase launch --enable-autojs   # 显式挂 AutoJS 测试模组
bash scripts/test/win/live.sh --batch LT0 --dry-run        # 批次只打印将执行什么
bash scripts/test/win/live.sh --batch LT2 --allow-auto     # 真跑（非 dry-run 必须 --allow-auto）
bash scripts/test/win/mt.sh --version fabric-1.20.1 --side server --phase stop --purge-saves
```

---

## A2. 用例目录结构与三个代表用例

- 位置：**D:\MCProject\astral_dice_multiloader\scripts\test\cases**
- **280 个 JSON，平铺、不按线分目录**；线靠文件名后缀区分。
- 命名规则 <FEATURE>-<MC 版本>.json，部分带批号（FIXBATCH-* / CC-* / CHIP-*）；fabric 线后缀 -fabric-1.20.1，26.1.2 后缀 -26.1.2。
- 数量分布（ls | grep -c -- -<ver>.json）：1.21.1 = **121**，1.20.1 = **105**（含 22 条同时属 fabric 双计），26.1.2 = **53**，fabric-1.20.1 = **22**。

### ① scripts/test/cases/ACTIONBAR-DIAG-1.21.1.json（150 行，产物最全的形态）

- **:2** case_id / **:3** title / **:4** version / **:5-8** target{type:feature, ref:target-select-actionbar-prompts} / **:9** on_fail:keep_game_running
- **玩家数据摆放**（= 台子上「模拟玩家数据」的真实做法，用命令摆而非注入存档）：**:16-18** /gamemode creative、**:25-28** /time set midnight、**:34-37** /tp @s ~ ~ ~ 0 0、**:43-45** /astral_dice targetselect living
- **截图步骤 4 个**：**:52**、**:61**、**:70**、**:79**（op:screenshot）
- **断言 13 条**：**:86/92/98/104/110/116/122/128** 共 8 条 type:log、**:134** type:absent、**:139** type:kubejs、**:142** type:crash、**:145** type:vision（对截图提问）
- **:13** note 记录两轮实测：截图步骤开销 1.9s vs 瞬态提示 2.0s（差 0.3s/0.5s 错过）⇒ 判定权威在 debug 通道，截图只作补充

### ② scripts/test/cases/AUTOJS-SMOKE-1.21.1.json（79 行，AutoJS 通道形态）

- **:2** case_id / **:5** "channel": "autojs"（唯一显式声明通道的写法）/ **:10** on_fail:continue
- **:13-14** note：JS 代码一律写成 IIFE (function(){…})()，AutoJS 只求值最后一个表达式，顶层 return 抛 invalid return
- 步骤：**:17-20** mcp_status(save_as:st, 15000ms) → **:22-27** eval_js(side:server, save_as:reg) → **:29-34** eval_js(side:client) → **:36-41** await_state(side:server, 60000ms, interval 500ms)
- **:44** note：expect 支持标量 / min / max / gte / lte / regex / set / contains；mcp_status 的 save_as 键名大写开头（Ok），expect 必须写 Ok（区分大小写）

### ③ scripts/test/cases/BOOT-EMBED-fabric-1.20.1.json（65 行，fabric 双 run 根形态）

- **:2** case_id / **:5** "side": "server"（fabric 双 run 根必需）/ **:6** on_fail:keep_game_running
- **:8-11** op:jar_stats(save_as:jar) —— **离线读 build/libs 产物条目**，不依赖游戏
- 断言：**:23-26** log Loading \d+ mods:（scope whole）、**:28-31** - astral_dice \S+、**:33-36** - starengine \S+、**:38-41** Done \(\d+(\.\d+)?s\)! For help、**:43-45** absent HARD_DEP_INCOMPATIBLE|Mod resolution failed|Incompatible mod set
- **:14** note 解释 window=whole 的理由（mods 清单在 launch 窗口起点之前打印）并引用 fabric.mod.json:36、build.gradle:85-89（Loom include(...)）

**步骤/断言原语全集**（写用例照这里查）：**scripts/test/mt_case.ps1:192-221**（op 表：inject_key / inject_mouse / inject_command / kubejs_reload / wait / screenshot / assert / eval_js / await_state / mcp_status / jar_stats / note）；断言 type 全集 **:226** = log, absent, crash, kubejs, mixin, vision, eval, state, jar, bytecode, bytecode_absent；用例级 channel 只接受 autojs|kubejs（**:232**，缺省 kubejs）。

---

## A3. 「模拟玩家数据 + 截图」的真实产物路径与文件名模式

### 3.1 截图

| 通道 | 落盘位置 | 文件名模式 | 事实源 |
|---|---|---|---|
| autojs | run/<ver>/screenshots/ | <tag>.png（tag 由用例步骤给） | **mt_case.ps1:2099-2134** |
| native / f2 | run/<ver>/screenshots/ | **游戏自命名** YYYY-MM-DD_HH.MM.SS.png | 实测 reports/20260926-230116/1.21.1/screenshots/2026-10-22_00.27.17.png 等 20+ 张 |
| native / window | 同上 | 同 f2（系统抓屏后由台子保存） | **mt_capture.ps1:12-14** |
| 报告证据副本 | scripts/test/reports/<run_id>/<ver>/screenshots/ | 原名复制 | **mt_report.ps1:294-340** |

- **tag → 文件 映射表**：<截图目录>/.mt_shots.json（**mt_capture.ps1:8**），写入 Add-MtShot（**Mt.Paths.psm1:308-336**，字段 file/tag/ts；run_id 不匹配则整表重置）。断言**必须**经 Get-MtCurrentShots(run_id) 取图（**mt_capture.ps1:9**），不得直接 glob 目录。
- 世代管理：**mt_capture.ps1:16-19** → mt_capture.ps1 prune -Keep 3（只保留最近 3 代）。

### 3.2 玩家数据：**报告里没有玩家位置/背包/属性快照**

三路证据（实测 grep）：

1. grep "玩家|inventory|背包" 在 scripts/test/*.ps1 与 scripts/test/lib/*.psm1 的命中全部是环境准备或规则：**mt_env.ps1:309-315**（Carpet bot /player <name> spawn）、**mt_env.ps1:756-757**（死亡不掉落 + 背包一致性）、**mt_env.ps1:834/907**（minecraft:keep_inventory 闸门）。
2. **scripts/test/mt_case.ps1** 内**没有任何** player / inventory / pos / teleport 读数原语（op 表 **:192-221** 已列全）。
3. .mt_snapshot.json 存的是**日志偏移**，不是玩家数据：**scripts/test/mt_report.ps1:74-76**。

⇒ 玩家状态只能靠用例自己用 inject_command 摆出来（A2① 即实例），再靠探针日志断言。**若任务需要「玩家数据快照」，台子里没有现成能力，需要新增用例步骤或探针。**

---

## A4. 当前是否有游戏实例在跑（2026-11-02 01:4x 复核）

命令（可复跑）：

```powershell
pwsh -NoProfile -Command 'Get-CimInstance Win32_Process | Where-Object { $_.Name -like "java*" } | ForEach-Object { "{0} ppid={1} {2} :: {3}" -f $_.ProcessId, $_.ParentProcessId, $_.CreationDate, $_.CommandLine }'
```

输出 6 个 java 进程：

| PID | PPID | 启动 | 判定 | 判定依据（命令行命中词） |
|---|---|---|---|---|
| 27676 | 29064 | 01:24:11 | **Gradle daemon** | org.gradle.launcher.daemon.bootstrap.GradleDaemon 9.5.1 |
| 38300 | 2156 | 01:29:12 | gradlew 启动器 | gradle-wrapper.jar + :fabric-1.20.1:runServer |
| 40152 | 27676 | 01:29:15 | **MC 服务端**（fabric） | fabric.dli.env=server + fabric.devlaunchinjector.Main + nogui |
| 22832 | 41516 | 01:40:44 | gradlew 启动器 | :fabric-1.20.1:runClient + -PquickplayMultiplayer + -PdevUsername=AstralTester |
| 16912 | 22832 | 01:40:45 | **Gradle daemon** | GradleDaemon（9.5.1） |
| 30836 | 16912 | 01:40:48 | **MC 客户端**（fabric） | fabric.dli.env=client + --quickPlayMultiplayer 127.0.0.1:25565 + --username AstralTester |

**结论**：当前 = 1 个 fabric 服务端 + 1 个 fabric 客户端 + 2 个 Gradle 守护 + 2 个 gradlew 启动器，**全部属 fabric-1.20.1 线**；1.21.1 / 1.20.1 / 26.1.2 三条线当前**没有**任何客户端或服务端在跑。

判定方法（下次照抄）：

- 含 GradleDaemon ⇒ 守护（不是游戏，别动）
- 含 gradle-wrapper.jar 且带 :<线>:runXxx ⇒ gradlew 启动器（父进程）
- 含 fabric.dli.env= / fabric.devlaunchinjector / --quickPlayMultiplayer / nogui ⇒ **Minecraft 实体**（服务端 vs 客户端看 dli.env）

旁证：scripts/test/win/logs/ 最新两个文件 fabric-1.20.1-20261102-012912.log、-012728.log —— **时间戳与 38300 的 01:29:12 精确对上**，说明这轮 fabric 实例是 scripts/test/win/mt.sh … --phase launch 起的，不是手工 gradlew。

⚠️ 早先（01:36–01:38）快照里曾有 **2 个** KnotClient（PID 8236 / 25368，ppid 29100 / 25520），复核时只剩 1 个 ⇒ 上一轮多开的客户端已自行退出，当前无残留。取证全程未杀任何进程。

---

## A5. 各线 run 目录状态 + jar 同名同哈希比对 + kubejs 探针残留

### 5.1 run/<线>/mods 清单

**run/1.21.1/mods（20 项）**：AutoJS-neoforge-1.21.1-1.0.0.jar（+ .bak-20261022）、ImmediatelyFast-NeoForge-1.6.14+1.21.1、[农夫乐事] FarmersDelight-1.21.1-1.3.4、[神秘遗物+] enigmaticlegacyplus-1.21.1-1.1.2、architectury-13.0.11-neoforge、astral_dice-1.5.0-rc.7+neoforge_1.21.1.jar、caelus-neoforge-7.0.1+1.21.1、collective-1.21.1-8.39、enigmaticdelicacy-1.21.1-1.0.0、ferritecore-7.0.3-neoforge、iris-neoforge-1.8.14-beta.1+mc1.21.1、kubejs-neoforge-2101.7.2-build.377、modernfix-neoforge-5.27.27+mc1.21.1、neoforge-carpet-1.21.1-1.0.8+v251027、rhino-2101.2.8-build.91、sodium-neoforge-0.8.13+mc1.21.1、starengine-neoforge-1.21.1-1.1.0、superflatworldnoslimes-1.21.1-3.5、ysm-2.6.5-neoforge+mc1.21.1-release

**run/1.20.1/mods（7 项）**：AutoJS-forge-1.20.1-1.0.0-dev.jar、AutoJS-forge-1.20.1-1.0.0.jar.bak-20261022b、.bak-20261022c、.reobf-20261022、astral_dice-1.5.0-rc.7+forge_1.20.1.jar、starengine-forge-1.20.1-1.1.0.jar、ysm-2.6.5-forge+mc1.20.1-release.jar

**run/26.1.2/mods（16 项）**：AutoJS-neoforge-26.1-1.0.0.jar、ModernUI-NeoForge-26.1.2-3.13.0.5-universal、[是，史蒂夫模型] ysm-2.6.5-neoforge+mc26.1-hotfix、astral_dice-1.5.0-rc.7+neoforge_26.1.2.jar（另有 .old-20261101-2240.bak、.old-20261101.jar.bak 两个残留）、better-advanced-tooltips-2601.1.0-build.10、collective-26.1.2-8.32、ferritecore-9.0.0-neoforge、iris-neoforge-1.11.4+mc26.1.2、kubejs-neoforge-26.1.2-8.0.6、modernfix-neoforge-5.27.22+mc26.1.2、rhino-2101.2.8-build.91、sodium-neoforge-0.9.1+mc26.1.2、superflatworldnoslimes-26.1.2-3.6

**fabric-1.20.1/run/{client,server}/mods（各 7 个 astral_embedded-*.jar）**：cloth-basic-math / com_github_llamalad7_mixinextras / fiber / io_wispforest_endec / endec_gson / endec_netty / puzzlesaccessapi。**没有 astral_dice jar、没有 AutoJS jar**（fabric 线 AutoJS 是条件依赖：**fabric-1.20.1/build.gradle:182-190** 只在 -PautojsTestMod=1 时挂 modRuntimeOnly；默认走 RCON 25575 —— **scripts/test/win/README.md:219-220, 245**）。

### 5.2 jar 与 build/libs 最新产物比对（sha256 前 16 位 + 字节数）

| 线 | run/ 侧 | build/libs 侧 | 结论 |
|---|---|---|---|
| 1.21.1 | astral_dice-1.5.0-rc.7+neoforge_1.21.1.jar = c42d0aa73ae403ea · 2,611,872 B · 23:46:32 | 同名 = c42d0aa73ae403ea · 2,611,872 B · 23:43:45 | **一致** |
| 1.20.1 | astral_dice-1.5.0-rc.7+forge_1.20.1.jar = 9c85e57eb9699c10 · 2,630,753 B · 23:46:32 | 同名 = 6b0509368165907f · 2,660,152 B · 23:43:54 | **同名不同哈希，差 29,400 B** |
| 26.1.2 | astral_dice-1.5.0-rc.7+neoforge_26.1.2.jar = a48d099b37ff37fc · 2,875,210 B · 23:31:44 | 同名 = 同哈希同大小 | **一致** |
| fabric | run/{client,server} 无 astral jar | astral_dice-1.5.0-rc.7+fabric_1.20.1.jar · 3,027,461 B · 23:16 | 产物在，**未部署进 run**（fabric 部署模型是 Loom include，见 BOOT-EMBED **:14**） |

1.20.1 差异的**未定原因**：run 目录旁有 .reobf-20261022 残留，且该线生产 jar 是 SRG/重打包产物 —— 合理怀疑是 reobf/部署重打包导致，但**本次未取证到具体重打包步骤，标记待核实，不下结论**（遵守「禁止凭文件名/大小推断」纪律）。

build/libs 存量 17 个 jar：fabric 1、forge 1、neoforge-1.21.1 **2**（rc.6 + rc.7）、neoforge-26.1.2 **14**（1.2.1-beta → 1.5.0-rc.7 历史堆积，发布前建议清理，本次不动）。

### 5.3 kubejs 探针残留清单

| 线 | client_scripts | server_scripts | 其它 |
|---|---|---|---|
| run/1.21.1/kubejs | **8**：astral_client_diag.js、astral_dnum_diag.js、astral_hud_diag.js、astral_kame_client.js、astral_pmfix_client.js、astral_rarity_probe.js、astral_shop_client.js、astral_wallet_balance.js | **13**：astral_batch3_probe.js、astral_bugfix_probe.js、astral_dice_curios_check.js、astral_dice_curio_watch.js、astral_dice_heal_test.js、astral_dice_target_select_check.js、astral_fixbatch_probe.js、astral_hostile_probe.js、astral_kame_probe.js、astral_kame_pvp_probe.js、astral_shop_probe.js、astral_shop_vis.js、astral_test_noai.js | config/client.json、config/common.json、config/web_server.json、README.txt |
| run/1.20.1/kubejs | **5**：astral_dnum_diag、astral_kame_client、astral_pmfix_client、astral_shop_client、astral_wallet_balance | **12**：bugfix_probe、curios_check、target_select_check、dst_probe、fixbatch_probe、functional_test、hostile_probe、kame_probe、kame_pvp_probe、shop_probe、shop_vis、test_noai | config/*.properties |
| run/26.1.2/kubejs | **2**：astral_wallet_vision.js、zz_prop_probe.js | **6**：bugfix_probe、curios_check、curio_watch、heal_test、target_select_check、test_noai | config 3 个 |
| fabric-1.20.1/run/server/kubejs | — | **3**：astral_catchup_probe.js、astral_fabric_curio_probe.js、astral_gs_probe.js | startup_scripts/example.js；client 侧无 kubejs 目录 |

**结论**：三条非 fabric 线都带着一整批历史探针脚本（1.21.1 共 21 个、1.20.1 共 17 个、26.1.2 共 8 个），属测试资产而非产品内容；**这些脚本不会被打进 jar，但会让任何 log 断言在这些行上假绿**（与 scripts/test/win/live.sh:10-12「启动前轮转 latest.log」同一类风险）。本次只登记，不清理。

---

## A6. AutoJS 通道：端口、启动方式、探测步骤

### 6.1 端口与配置（事实源）

- 端点：**Streamable HTTP，POST http://127.0.0.1:25585/mcp**，默认 bind 127.0.0.1；GET /health 探活、GET / 摘要；**无 SSE**（GET /mcp → 405）—— **docs/features/autojs-test-harness-spec.md:16**。
- 端口覆盖序：-Dautojs.port= > AUTOJS_PORT > config/autojs.json（0 = 系统分配）—— 同上 **:16**。
- 各线配置文件实读：**run/1.21.1/config/autojs.json** = {enabled:true, unrestricted:true, bindAddress:"127.0.0.1", port:25585, enableServerSide:true, enableClientSide:true, token:"", evalTimeoutMs:5000, maxResultChars:100000, logEval:true}（1.20.1 / 26.1.2 同款存在）。
- 台子侧默认端点常量：**scripts/test/mt_case.ps1:239** $script:AutoJsDefaultEndpoint = 'http://127.0.0.1:25585/mcp'。
- 冲突纪律：**同一时刻只能一条线占用 25585**，否则第二个实例 BindException —— **docs/agent/AGENTS-dsh-linux.md:262-274**。

### 6.2 怎么启动

1. 该线 run/<ver>/mods 里要有 AutoJS 模组 jar —— 1.21.1 / 1.20.1 / 26.1.2 **已就位**（见 A5.1）；fabric 线**默认没有**，需 --enable-autojs（**scripts/test/win/mt.sh:82-83, 136-153**；README:219-220）。
2. 起实机：mt.ps1 --version <线> --phase launch（或 fabric 的 bash scripts/test/win/mt.sh … --phase launch --enable-autojs）。模组随游戏启动自动监听 25585。
3. 端点就绪后再跑 channel:autojs 的用例（如 AUTOJS-SMOKE-1.21.1.json）。

### 6.3 怎么探测（三步官方口径）

**docs/features/autojs-test-harness-spec.md:39**：先 TCP 连 127.0.0.1:25585（MT_AUTOJS_TIMEOUT_MS 默认 15000，轮询 500ms）→ 再 initialize → 再 tools/call status；三步任一失败即「端点未就绪」。

可照抄：

```powershell
# 1) TCP 探活（最快）
pwsh -NoProfile -Command "Test-NetConnection 127.0.0.1 -Port 25585 -InformationLevel Quiet"

# 2) HTTP 探活（README:191 写法，--noproxy 必需，否则会被 7897 代理接管）
curl --noproxy '*' -fsS -m 3 http://127.0.0.1:25585/health

# 3) 谁在听这个端口
netstat -ano | findstr 25585
```

### 6.4 当前实测状态：**端点未监听**

- Test-NetConnection 127.0.0.1 -Port 25585 -InformationLevel Quiet ⇒ **False**（输出 TCP connect to (127.0.0.1 : 25585) failed）
- netstat -ano | findstr 25585 ⇒ **零命中**
- 与 A4 一致：当前跑的是 fabric 线且**没带** --enable-autojs ⇒ 按设计根本没有 AutoJS 端点。README:191 也把 000 记为「未启动游戏，属预期」。

### 6.5 MCP 注册现状（本机 DSH）

- **已注册**：**C:\Users\xmace\.dsh\profiles\desktop\cordis.patch.yml:204-221** 有 id: mcp-autojs / serverName: autojs / url: http://127.0.0.1:25585/mcp，并注释端口覆盖序（**:213-214**）。本会话工具面里确实有 autojs server 与 autojs 技能。
- **未注册**：profiles/web/cordis.patch.yml（18 行）里 grep autojs 零命中 —— 走 web profile 就没有 autojs。
- 注册步骤正本：**docs/features/autojs-test-harness-spec.md:35**（mcp-autojs、streamable-http、failOnStartupError: true + reconnect，仿 blockbench 条）。
- ⇒ 端点没起时 MCP 侧表现为 disconnected / 调用失败，属**环境未就绪（BLOCKED）**，不是用例失败（**docs/agent/AGENTS-full.md:2349**：端点没起记 BLOCKED；mixin/crash/禁AI审计/vision 四类判据不得用 eval 取代）。

---

# B. 发布推送

## B7. CurseForge 上传：用法、凭据、项目与路由规则

文件：**tools/curseforge_upload.py**（1,445 行）+ **tools/curseforge.json**（106 行）。

### 7.1 用法与凭据

- 子命令（**tools/curseforge_upload.py:1-104** 头注释）：push（上传 jar → 建立 relations → 可选 update-info）/ update-info（改 summary/description/categoryId）/ 旧式无子命令（--jar 上传、--update-file 补元数据）。
- **默认 dry-run**：**:12-14**；写请求唯一判定点 write_enabled（**:841-847**）—— 给 --dry-run ⇒ 不发；给 --apply / --yes ⇒ 发；都不给 ⇒ **不发**（默认安全）。
- **token 来源（按优先级）**：--token > 环境变量 **CURSEFORGE_TOKEN** > <仓库根>/.curseforge/token（**:39-42**；解析实现 **:227-235**）。
  实读：**.curseforge/token 存在，36 B**（2026-10-20），versions.json 781,840 B（2026-10-28）⇒ **能直接跑真上传**。
- 元数据 PUT /v1/mods/{id} 用 x-api-key：取 --api-key / **CURSEFORGE_API_KEY**，缺失回退旧 token（**:46-49、:821-828**）。
- --dry-run 允许无 token（resolve_token_safe，**:831-838**）。
- 代理：--proxy 默认取 args.proxy 或 HTTPS_PROXY / https_proxy（**:863**）。Cloudflare 403 时自动改 curl 回退（**:249-251、:382、:416、:446、:473**）。

### 7.2 项目 id 与「文件名即路由」规则

**tools/curseforge.json** 事实源：

| 键 | 值 |
|---|---|
| 本体项目 | projectId: **1662159**、slug: astral-dice、gameId: 432、site: minecraft、apiBase: https://minecraft.curseforge.com/api |
| 前置库项目 | lib.projectId: **1755415**（StarEngine） |
| 游戏版本四件套 | **Client + Server + MC 版本 + 加载器**（**curseforge_upload.py:116** 头注释） |

- **jar 文件名解析（唯一路由依据，不看目录）**：JAR_RE = ^astral_dice-(?P<ver>[^+]+)\+(?P<loader>[a-z]+)_(?P<mc>.+)\.jar$、LIB_JAR_RE = ^starengine-(?P<loader>neoforge|forge|fabric)-(?P<mc>\d+\.\d+(?:\.\d+)?)-(?P<ver>.+)\.jar$（**:185-187**；parse_jar **:530-540**、is_lib_jar **:543-544**）。
- **渠道派生**（不看 json 的 channel 字段）：infer_release_type（**:547-553**）—— 版本号含 -alpha ⇒ alpha；含 -beta / -rc / -pre ⇒ beta；否则 release。
- **线匹配**：find_line（**:571-585**）按 key 或 loader+mc 唯一匹配 curseforge.json 的 lines。
- **库产物自动改道（关键）**：make_ctx（**:850-860**）—— 若本次全部 jar 都是 starengine-* 且未显式 --project-id ⇒ 自动改用 lib.projectId（1755415）并把 no_relations 置真（库文件不挂 Curios/Mixin Booster 关系）。
- curseforge.json 的 _note 明说：脚本只读 projectId 与 slug，加载器/MC/版本一律从 jar 名解析。

### 7.3 「四线 8 个产物」怎么映射

**名义拓扑（8 件 = 4 本体 + 4 库）**：

| # | 产物 | loader / mc | 目标项目 | 渠道 | relations |
|---|---|---|---|---|---|
| 1 | astral_dice-<ver>+neoforge_1.21.1.jar | neoforge / 1.21.1 | **1662159** | release（按版本后缀） | curios 309927、starengine 1755415 |
| 2 | astral_dice-<ver>+forge_1.20.1.jar | forge / 1.20.1 | **1662159** | release | curios、mixinbooster 980731、starengine |
| 3 | astral_dice-<ver>+neoforge_26.1.2.jar | neoforge / 26.1.2 | **1662159** | beta（该线版本号恒带 -beta/-rc） | curios |
| 4 | astral_dice-<ver>+fabric_1.20.1.jar | fabric / 1.20.1 | **1662159** | alpha | fabric-api 306612、puzzles-lib 495476、trinkets 341284(required)、accessories 938917(optional) |
| 5–8 | starengine-neoforge-1.21.1-<ver>.jar / -forge-1.20.1- / -neoforge-26.1.2- / -fabric-1.20.1- | 同四线 | **1755415**（自动改道） | release | 自动 no_relations |

**但「名义 8 件」现在不是实际投放口径，两层红线压着**：

1. **2026-10-25 用户裁决**：默认只发布/上传 **P0 两线**（neoforge-1.21.1 + forge-1.20.1）；P1 两线（26.1.2 / fabric）产物视为**不可用**，禁止上传、禁止建 Release/tag、禁止作附件 —— **.github/workflows/build.yml:12-15**；**docs/agent/AGENTS-full.md:3** 红线区「发布投放红线」。
2. **库 jar 不在本仓 build/libs**：本仓 17 个 jar 全是 astral_dice-*（ls */build/libs/*.jar 无 starengine），库 jar 来自另一个仓 **merlin-kitsune/StarEngine**（build.yml:60-108）；release/starengine-lib/ 只剩 1.0.6 / 1.0.12 旧目录。

**历史实况（真实投放轨迹）**—— **docs/handover-1.5.0-main.md:32** 与 **:399-414**：

- **rc.1 一次传 6 个**：本体 P0 两件 beta（neo 9320026 / forge 9320028）+ 库四平台 release（9320029 / 9320030 / 9320031 / 9320032）；且库那次**必须** --project-id 1755415 --no-relations，否则 find_line 会把 astral 的 curios/mixinbooster 挂到库文件上（**:413**）。
- **rc.5 起只传本体 P0 两件**：rc.5 = 9349843 / 9349844、rc.6 = 9361839 / 9361840、rc.7 = 9363589 / 9363591（全 beta）。

### 7.4 可直接照抄

```bash
cd /d/MCProject/astral_dice_multiloader

# 1) 空跑（不写、不需要 token；先看路由与 gameVersions 对不对）
python tools/curseforge_upload.py push --dry-run \
  --jar neoforge-1.21.1/build/libs/astral_dice-1.5.0-rc.7+neoforge_1.21.1.jar \
  --jar forge-1.20.1/build/libs/astral_dice-1.5.0-rc.7+forge_1.20.1.jar

# 2) 真传（P0 两件；token 走环境变量或 .curseforge/token）
python tools/curseforge_upload.py push --apply --proxy http://127.0.0.1:7897 \
  --changelog release/1.5.0-rc.7/PLAYER_CHANGELOG.md \
  --jar neoforge-1.21.1/build/libs/astral_dice-1.5.0-rc.7+neoforge_1.21.1.jar \
  --jar forge-1.20.1/build/libs/astral_dice-1.5.0-rc.7+forge_1.20.1.jar

# 3) 库四平台（仅当用户点名要求时；注意 --project-id + --no-relations）
python tools/curseforge_upload.py push --apply --proxy http://127.0.0.1:7897 \
  --project-id 1755415 --no-relations \
  --jar <StarEngine 仓>/neoforge-1.21.1/build/libs/starengine-neoforge-1.21.1-1.1.0.jar
```

本机 python = **Python 3.12.16**（python 与 python3 都可用）。

---

## B8. git 远端与代理

```bash
git -C /d/MCProject/astral_dice_multiloader remote -v
git -C /d/MCProject/astral_dice_multiloader config --get http.proxy
git -C /d/MCProject/astral_dice_multiloader config --get https.proxy
```

实测输出：

- **origin https://github.com/merlin-kitsune/astral_dice (fetch) / (push)** —— HTTPS 远端，不是 SSH
- **http.proxy = https.proxy = http://127.0.0.1:7897**
- 分支 **multi-main**；HEAD **fd990c15**「fix(26.1.2): 补齐收尾验证发现的混入缺口，并把立牌半透明改成 26.1.2 两段式实现」（2026-11-01 14:42:49 +0800）

代理端口监听状态：

```powershell
netstat -ano | findstr 7897
pwsh -NoProfile -Command "Test-NetConnection 127.0.0.1 -Port 7897 -InformationLevel Quiet"
```

- netstat 命中：**TCP 127.0.0.1:7897 127.0.0.1:0 LISTENING 21668**、UDP 0.0.0.0:7897、UDP [::]:7897；进程 **21668 = verge-mihomo.exe**（clash-verge-rev 的 mihomo 内核，Services 会话）。
- Test-NetConnection ⇒ **True**。
- 结论：**代理可用，7897 在听**；git push / GitHub API / CurseForge curl 回退都能走它。

---

## B9. GitHub Release 相关脚本与 tag 命名规则

### 9.1 没有独立发布脚本 —— 发布 100% 在 workflow 里

- scripts/ 下只有 audit / dev / devtools / maintenance / temp / test / verify 等目录（+ __tok.gradle、__tr），**无 release / publish 脚本**。
- 全仓唯一 workflow：**.github/workflows/build.yml**（318 行）。ref/ 下的 workflow 属第三方参考克隆，不是本仓链路。
- tag / Release / 附件三件事全在 build.yml：

| 环节 | 行号 | 行为 |
|---|---|---|
| 触发 | **:3-23** | push multi-main / multi-dev-next / multi-26.1.2-neoforge、tags [0-9]*、pull_request、workflow_dispatch |
| 读版本 | **:182-205** | VERSION 取 neoforge-1.21.1/gradle.properties 的 mod_version；BASE 取 VERSION 的「+」之前部分（**只剥 +后缀、保留 -rc**，等价于 shell 里 cut -d+ -f1）；正则守门 ^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z][0-9A-Za-z.-]*)?$，不匹配 ⇒ skip_tag=1 + ::warning::；含 - ⇒ is_prerelease=1 |
| 建 tag | **:207-226** | 仅 push multi-main 且 skip_tag=0；git tag -a "$TAG" -m "Release $TAG" + git push origin "$TAG"；已存在则跳过 |
| Release 正文 | **:255-282** | release/<tag>/PLAYER_CHANGELOG_ZH.md + 分隔线 + PLAYER_CHANGELOG.md 拼临时文件 --notes-file；两份都没有 ⇒ 退回附件清单 + ::warning:: |
| 附件 | **:251-252** | neoforge-1.21.1/build/libs/astral_dice-*.jar + forge-1.20.1/build/libs/astral_dice-*.jar（**只有 P0 两线**） |
| 预发布标记 | **:285-289** | prerelease ⇒ --prerelease --latest=false；正式 ⇒ --latest --prerelease=false |
| 幂等/冻结 | **:294-314** | 已存在 Release ⇒ gh release edit 刷正文，附件**只补缺失名**（grep -Fxq 比对已有 asset 名）；**2026-10-30 裁决去掉 --clobber**；不存在才 gh release create |

CI 构建前守门（**:110-164**）：四线 check_lang_sync.ps1、python tools/check_changelog_parity.py、pwsh -File scripts/verify/verify_chip_acquisition.ps1、7 个纯标准库 python 静态守门（verify_event_choice_exit / verify_event_choice_first_event_dispatchable(_forge) / verify_event_timer_stack(_forge) / verify_dice_combat_attrless_guard(_p1)），**硬红、不 continue-on-error**；随后 ./gradlew build（**:166-170**）。前置库：checkout merlin-kitsune/StarEngine ref **16b48c413d92bcd830d97c0a80b0df4e0c47ba6c**（= 库 1.1.0 正式版，**:99-104**）→ ./gradlew publishToMavenLocal（**:106-108**）；该 SHA 必须与四线 starengine_version 同提交更新（**:102** 注释）。

### 9.2 tag 命名规则在哪些文档里被规定

| 文档:行 | 内容 |
|---|---|
| **docs/agent/AGENTS-full.md:3**（红线区长行末尾「② tag 与 Release 命名红线」） | tag **只按 mod 版本**（1.5.0 / 1.5.0-rc.1 / 1.5.0-pre.6）；**禁止**平台前缀（fabric-… / neoforge-…）、**禁止** v 前缀、**禁止** +加载器 后缀；同一版本多平台 jar 进**同一个** Release；带 -rc/-pre/-alpha/-beta 一律 prerelease、不占 latest。同段还有「发布投放红线（2026-10-25）」= 默认只发 P0 两线 |
| **docs/handover-1.5.0-main.md:1267**（第 12 条） | 同规则 + 落地清单（build.yml 只剥 +后缀、守门正则、新增 is_prerelease、Create Git tag 删掉「含 - 就跳过」分支、Release 按 is_prerelease 传 --prerelease） |
| **docs/handover-1.5.0-main.md:555** | tag **重指**实践：本机安全网禁 git push 删除 ref / 强推，改用 git push --force-with-lease=refs/tags/<tag>:<old> origin refs/tags/<tag> |
| **docs/agent/AGENTS-full.md:654** | ⚠️ **旧口径**（tag 无后缀、tag 推送触发 CI 发三个 jar、fabric 走自己的 fabric-<版本> pre-release、--clobber 刷新同一 Release）⇒ 已被 2026-10-25 / 10-30 裁决覆盖，**不得当现状引用** |

---

## B10. MC 百科（MCmod）版本日志：脚本与历史产物

搜索口径：grep -rn "mcbbs|mcmod|百科" tools/ docs/ scripts/ release/。

**结论：没有任何生成脚本；只有手工维护的 HTML 产物，且最新版缺。**

1. **无脚本**：**docs/handover-1.5.0-main.md:473** 记录 PLAYER_CHANGELOG_MCMOD.html = 41,192 B / 275 行 / LF-only；**:474** 明写「**仓库里没有 MCMOD 生成脚本**（grep mcmod|MCMOD 在 tools/*.py 命中 0），该文件自 rc.1 起是**手工维护**的 mcmod.cn 语法（[h1=]/[h2=]/[h3=] + 内联 HTML），且正文与 ZH 版并非逐句对应（手工压缩版）」。
2. **历史产物 6 个**（find release -iname *MCMOD*）：release/1.5.0-rc.1/ … release/1.5.0-rc.6/ 各一份 PLAYER_CHANGELOG_MCMOD.html。
3. **最新版缺失**：**release/1.5.0-rc.7/ 只有 PLAYER_CHANGELOG.md + PLAYER_CHANGELOG_ZH.md，没有 MCMOD.html** ⇒ 若本次要发 rc.7 到 MC百科，需要手工补一份。
4. 其余 mcmod/mcbbs 命中都是**参考资料链接**，与发布链路无关：**docs/neoforge-1.21.1-knowledge-base.md:101/381/969/995/1029/1035/1038/1039/1040/1052**（MC百科 class/post 链接、MCBBS Wiki 教程）。tools/ 与 scripts/ 下**零命中**。

---

# C. 汇总：可直接照抄的命令清单

## C1. 只读体检（随时可跑，不启动进程）

```powershell
pwsh -NoProfile -File D:\MCProject\astral_dice_multiloader\scripts\test\mt.ps1 --help
pwsh -NoProfile -File D:\MCProject\astral_dice_multiloader\scripts\test\mt.ps1 --version 1.21.1 --phase preflight
pwsh -NoProfile -Command "Get-CimInstance Win32_Process | Where-Object { $_.Name -like 'java*' } | Select-Object ProcessId,ParentProcessId,CreationDate,CommandLine | Format-List"
pwsh -NoProfile -Command "Test-NetConnection 127.0.0.1 -Port 25585 -InformationLevel Quiet"
pwsh -NoProfile -Command "Test-NetConnection 127.0.0.1 -Port 7897 -InformationLevel Quiet"
```

## C2. 实机取证（会启游戏 —— 必须用户明确下达）

```powershell
pwsh -NoProfile -File D:\MCProject\astral_dice_multiloader\scripts\test\mt.ps1 --version 1.21.1 --phase env
pwsh -NoProfile -File D:\MCProject\astral_dice_multiloader\scripts\test\mt.ps1 --version 1.21.1 --phase launch
pwsh -NoProfile -File D:\MCProject\astral_dice_multiloader\scripts\test\mt.ps1 --version 1.21.1 --phase cases --case ACTIONBAR-DIAG-1.21.1
pwsh -NoProfile -File D:\MCProject\astral_dice_multiloader\scripts\test\mt.ps1 --version 1.21.1 --phase stop
```

## C3. fabric 线

```bash
cd /d/MCProject/astral_dice_multiloader
bash scripts/test/win/mt.sh --version fabric-1.20.1 --side server --phase launch
bash scripts/test/win/mt.sh --version fabric-1.20.1 --side client --phase launch --enable-autojs
bash scripts/test/win/live.sh --batch LT0 --dry-run
bash scripts/test/win/mt.sh --version fabric-1.20.1 --side server --phase stop --purge-saves
```

## C4. 发布

```bash
cd /d/MCProject/astral_dice_multiloader
git -C /d/MCProject/astral_dice_multiloader remote -v
git -C /d/MCProject/astral_dice_multiloader config --get http.proxy
python tools/curseforge_upload.py push --dry-run \
  --jar neoforge-1.21.1/build/libs/astral_dice-1.5.0-rc.7+neoforge_1.21.1.jar \
  --jar forge-1.20.1/build/libs/astral_dice-1.5.0-rc.7+forge_1.20.1.jar
# 真发（用户点名后）：见 B7.4
```

---

# D. 状态判定与风险（取不到证据的一律列这里）

| 项 | 状态 | 说明 |
|---|---|---|
| pwsh 主台 1.21.1 / 1.20.1 / 26.1.2 | **可用（有历史实绩）** | 最近报告 scripts/test/reports/20260926-*；三条线 run/mods 齐备 |
| pwsh 主台 fabric | **不可用（设计排除）** | **mt.ps1:97-104** 排除；走 bash 层 |
| bash 层 fabric 线 | **正在使用** | 当前 1 服务端 + 1 客户端在跑，日志时间戳对得上 |
| AutoJS 端点 | **未就绪** | 25585 未监听；当前 fabric 实例未带 --enable-autojs；非 fabric 线 jar 已就位，起 game 即可 |
| CurseForge 真上传 | **凭据就绪** | .curseforge/token 36 B 存在；代理 7897 在听；--dry-run 可先验路由 |
| GitHub Release | **workflow 就绪，无独立脚本** | 只发 P0 两线；tag 规则见 B9.2 |
| MC百科日志 | **缺 rc.7 产物、无脚本** | 只有 rc.1–rc.6 的 6 份 HTML，手工维护 |
| 1.20.1 run jar 与 build/libs 不同哈希 | **无法判定** | 差 29,400 B，怀疑 reobf/重打包，未取证到具体步骤；不据此下结论 |
| 「玩家数据快照」能力 | **不存在** | 台子只能靠 inject_command 摆状态 + 日志断言；报告里无位置/背包/属性快照 |
| cases 与 run 侧探针的对应关系 | **未逐条核** | 本次只做结构/抽样取证；280 条用例与 21+17+8 个探针脚本的全量比对未做 |

## D1. 额外发现（不在原问题里，但影响照抄）

1. **mt.ps1 -? 是陷阱**：只有 --help / -h 会打用法块（**mt.ps1:643-650**）。
2. **--phase report 单独跑必被闸门拦**（**mt.ps1:768-807**）⇒ 想拿新报告只能 --allow-auto 跑全流程；这解释了「为什么最近报告停在 2026-09-27」。
3. **scripts/test/win/live.sh 是 2026-11-02 00:20 刚改的新层**（LT0–LT7 批次编排），它**故意不在脚本里起游戏**（**:7-9**：沙箱启动会因 Low IL 删不掉旧 remap 缓存 AccessDenied，启动动作交回用户终端），只等 RCON 就绪 —— 与「实机必须用户明确下达」的红线一致。
4. **ref/ 下的 workflow 与本仓无关**（第三方参考克隆），统计 workflow 数量时别误算。
5. **fabric 线读数默认走 RCON 25575**（**scripts/test/win/README.md:245**），AutoJS 只是可选通道 ⇒ 该线「实机取证」与其它三线的通道口径不同，写用例时必须显式带 channel。
