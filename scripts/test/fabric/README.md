# fabric-1.20.1 线测试台（`scripts/test/fabric/`）

> 自包含的第四条线测试台。**不修改、不依赖** `scripts/test/mt*.ps1` + `lib/Mt.*.psm1` + `cases/*.json`
> 那一套生产线资产（那套是 NeoForge 1.21.1 / Forge 1.20.1 / NeoForge 26.1.2 三线的口径）。
> 一律 PowerShell 7（`pwsh`）；禁用 Windows PowerShell 5.1（见 `AGENTS.md`「命令执行规范」）。

---

## 1. 为什么**不能**复用 `mt.ps1`（逐项差异）

| 维度 | 生产线（Forge/NeoForge） | fabric-1.20.1 线 | 后果：为什么生产线脚本用不了 |
|---|---|---|---|
| 事件注册 | `@Mod.EventBusSubscriber` 自动注册 | **自建反射总线** `platform/event/LoaderBus`；须在 `AstralDiceMod.registerListeners()` 显式登记（`platform/event/LoaderBus.java:103-161`、`AstralDiceMod.java:51,91`） | 没有「注册失败」这种可 grep 的 Forge 报错形态；必须用 fabric 专属的派发计数报告 |
| 事件派发 | Forge 原生 EventBus | **四路**：FAPI 回调（`platform/FabricBridges.java:107-121`）+ Puzzles Lib（`platform/PuzzlesBridges`）+ 你自写 mixin（`mixin/bridge/**`）+ 前置库事件 | 「桥装了但事件从不触发」是**静默**失效；生产线没有对应护栏 |
| 启动入口 | `@Mod` 构造器 | `fabric.mod.json` 的 `main` / `client` / `fabric-datagen` entrypoint（`src/main/resources/fabric.mod.json:14-24`） | 启动期事实的锚点不同 |
| 运行目录 | 仓库根 `run/<版本>`（gradle `gameDirectory = rootProject.file('run/1.21.1')`） | **子项目内** `fabric-1.20.1/run/{client,server}`（Loom `runDir 'run/client'` 相对**子项目**解析，`build.gradle:172-182`）；datagen → `run/datagen` | 所有路径推导都不一样 |
| 构建 | `gradlew :forge-1.20.1:build` | `gradlew :fabric-1.20.1:build` | 任务名不同 |
| 依赖 | Curios / Mixin Booster | Trinkets 3.7.2（+ Accessories 可选）+ Puzzles Lib 8.1.33 + Forge Config API Port + Fabric API + Cardinal Components（`build.gradle:65-158`、`gradle.properties`） | `mt_env.ps1` 装的前置清单完全不适用 |
| 探测手段 | KubeJS 探针 + `/astralparty` 管理员命令 | 同样 KubeJS（已在 `modRuntimeOnly`），**外加** fabric 专属 `LoaderBus#dispatchReport()` | 生产线没有 dispatchReport，抓不到「0 次派发」 |
| 判定读数 | `AP_<tag>_<KEY>:` 聊天通道机器行 | 本台沿用 `AP_` 前缀族，但读数源改为：加载器日志 + 派发统计 + 开包条目 + （RCON/KubeJS 注入回显） | 断言对象不同 |
| 收停 | `mt_cleanup.ps1` 按 gradle 选择器 + run 目录匹配 | Loom dev-run 入口主类 `net.fabricmc.devlaunchinjector.Main` + 命令行含 `fabric-1.20.1`（实测 `dev-launch-injector-0.2.1+build.8.jar` 内 `net/fabricmc/devlaunchinjector/Main.class`，其属性名实测为 `fabric.dli.config` / `fabric.dli.env` / `fabric.dli.main`） | 判据不同 |
| 输入注入 | Win32 `PostMessage` 键鼠（`mt_inject.ps1` + `Mt.Win32.psm1`，依赖 en-US 键盘布局与窗口线程输入法） | 本台面向**服务端命令**：RCON（默认）+ KubeJS 队列脚本 | 客户端 GUI 键鼠注入本台 v1 未覆盖（缺口，见 §8） |
| 批量编排闸门 | `mt.ps1` / `mt_case.ps1` / `mt_watchdog.ps1`（三处 + watchdog） | `ft.ps1` / `ft_case.ps1` / `ft_launch.ps1 --side both` | 闸门维度不同：fabric 单版本 ⇒「三线顺序」与 `watchdog -Action stop` **N/A** |

**从第一性看**：生产线测试台的每一个共享函数（`Mt.Paths` 的版本→子项目映射、`Mt.Phase` 的三线门控、
`mt_env` 的前置清单、`mt_launch` 的窗口/输入法/禁 AI 闸门、`mt_case` 的进度信标）都把「三线 + Forge 语义」
写死在参数面与路径面上。复用等于把错误的假设带进来，比另写一套更贵。

---

## 2. 文件清单与职责

| 文件 | 职责 | 关键约定 |
|---|---|---|
| `lib/Ft.Common.psm1` | 共享原语：路径派生（全部 `$PSScriptRoot` 绝对路径）、统一输出、退出码、**批量编排闸门**、日志窗口读取、jar 开包统计、字节透传子进程 | 退出码与 `scripts/test/lib/Mt.Phase.psm1:29-36` 逐值一致 |
| `ft.ps1` | 阶段编排入口（`--phase build\|env\|launch\|stop\|case\|report`）+ 闸门（无 `--phase` / `case` 无 `--case` / `report`） | 逐字节透传子脚本输出；只返回退出码 |
| `ft_build.ps1` | `gradlew :fabric-1.20.1:build` + **开包核对**（models/item、recipes、bountiful=0、trinkets、`META-INF/jars`） | 读数行 `AP_FAB_*`；核对失败 = FAIL(1)，跑不起来 = ERROR(2) |
| `ft_env.ps1` | 运行环境装配/校验：目录骨架、`eula.txt`、前置可得性（Loom 缓存 / `mods` 双查）、可选装 RCON、装 KubeJS 观察者 | `--mode verify`（默认，只读）/ `install` |
| `ft_launch.ps1` | 后台起 `:fabric-1.20.1:runServer\|runClient` + 就绪轮询（字节游标，只认本次新写入的日志）+ 冻结 launch 窗口 | 就绪标记：服务端 `Done (…)`/客户端 `Sound engine started` |
| `ft_stop.ps1` | 状态文件 PID 树 + CIM 扫描 `devlaunchinjector` 进程，只杀本线；可选 `--stop-daemon` / `--purge-saves` | 绝不 taskkill 无关 java |
| `ft_inject.ps1` | 手动命令注入：`--channel rcon`（默认）/ `kubejs` | 手动粒度 = 命令级，一次一条 |
| `ft_assert.ps1` | 断言引擎：`snapshot` / `log` / `absent` / `case`；窗口 `case`(默认)`\|launch\|whole` | 可 dot-source（`InvocationName` 守卫）供其它脚本复用 |
| `ft_dispatchreport.ps1` | 抽取 `LoaderBus#dispatchReport()`，**把 0 次派发的事件类单独列出** | 硬判据：`ServerTickEvent > 0` |
| `ft_cmd_watcher.js` | KubeJS 服务端观察者（队列 `→ server.runCommand`），供 `ft_inject --channel kubejs` | 需冷启动或 `/kubejs reload server_scripts` |
| `cases/FAB-BOOT-EMBED.json` | ① 启动到 `Done` + 内嵌库自检 | 只需一次已完成的启动 |
| `cases/FAB-DISPATCH-BASIC.json` | ② 派发报告里基础事件为正数 | 需服务端跑过 600 tick |
| `cases/FAB-JAR-ASSETS.json` | ③ 产物资源完整性（**不需要游戏**） | 只需 `ft_build` |
| `cases/FAB-INJECT-ROUNDTRIP.json` | ④（附加）注入往返 | 需活着且装了观察者的服务端 |
| `event_bridge_probe.js` | **既有文件，原样保留**：人工取证脚本（造僵尸/伤害/效果来区分「桥没接」与「确实没发生」） | 用户手动放进 `run/server/kubejs/server_scripts/` |
| `.syntax-check.txt` | 全部 `.ps1/.psm1` 的 PowerShell 语法解析自检结果（见 §7） | 每次改动后重跑（命令见 §7） |

---

## 3. 用法

```powershell
# ── 基础设施（放行清单：把运行环境弄起来 / 收停）────────────────────────────
pwsh -NoProfile -File scripts/test/fabric/ft_build.ps1                  # 构建 + 开包核对
pwsh -NoProfile -File scripts/test/fabric/ft_env.ps1 --side both        # 前置/目录校验（只读）
pwsh -NoProfile -File scripts/test/fabric/ft_env.ps1 --side server --enable-rcon --install-watcher
pwsh -NoProfile -File scripts/test/fabric/ft_launch.ps1 --side server    # 起服务端并等就绪
pwsh -NoProfile -File scripts/test/fabric/ft_launch.ps1 --side client --timeout 420

# ── 手动读数 / 手动注入（逐条、边发边看）──────────────────────────────────
pwsh -NoProfile -File scripts/test/fabric/ft_dispatchreport.ps1 --side server
pwsh -NoProfile -File scripts/test/fabric/ft_assert.ps1 snapshot --side server --window case
pwsh -NoProfile -File scripts/test/fabric/ft_assert.ps1 log --pattern '事件派发统计' --window whole
pwsh -NoProfile -File scripts/test/fabric/ft_assert.ps1 absent --pattern '\[ft-inject\] ERR'
pwsh -NoProfile -File scripts/test/fabric/ft_inject.ps1 cmd --command "/give @s astral_dice:star_coin 3"
pwsh -NoProfile -File scripts/test/fabric/ft_inject.ps1 cmd --command "/time set day" --channel kubejs

# ── 单条用例（放行）─────────────────────────────────────────────────────
pwsh -NoProfile -File scripts/test/fabric/ft_case.ps1 list
pwsh -NoProfile -File scripts/test/fabric/ft_case.ps1 validate --all
pwsh -NoProfile -File scripts/test/fabric/ft_case.ps1 run --case scripts/test/fabric/cases/FAB-JAR-ASSETS.json
pwsh -NoProfile -File scripts/test/fabric/ft.ps1 --phase case --case scripts/test/fabric/cases/FAB-DISPATCH-BASIC.json

# ── 收尾 ───────────────────────────────────────────────────────────────
pwsh -NoProfile -File scripts/test/fabric/ft_stop.ps1 --side server --purge-saves
```

推荐顺序（与 §1 的差异对应）：`ft_build` → `ft_env --side both` → `ft_env --side server --enable-rcon --install-watcher`
→ `ft_launch --side server`（冷启动让观察者生效）→ 手动 `ft_inject`/`ft_assert` → 逐条 `ft_case run --case`
→ `ft_stop`。

---

## 4. 批量编排闸门（与生产线同构）

规则来源：`scripts/test/TESTING-RULES-OVERVIEW.md` §3.1（2026-09-27 用户裁决「禁用自动测试，改为纯手动下达命令」）。
实现：`lib/Ft.Common.psm1` 的 `Assert-FtAutoGate` —— 拦截时 **stderr 首行 `MT_AUTO_DISABLED: …`、退出码 2**；
临放行开关 **`--allow-auto`** 或环境变量 **`MT_ALLOW_AUTO=1`**（与生产线同形）。

| fabric 侧被拦下的能力 | 拦在哪一处 | 对应生产线的哪一项 |
|---|---|---|
| 全流程（`ft.ps1` 无 `--phase`） | `ft.ps1` | `mt.ps1` 无 `--phase` |
| `--phase case` 无 `--case`（走目录批量） | `ft.ps1` | `mt.ps1 --phase cases` 无 `--case` |
| `--phase report`（收集自动跑出来的结果） | `ft.ps1` | `mt.ps1 --phase report` |
| `ft_case.ps1 run` 无 `--case`、`ft_case.ps1 run-dir` | `ft_case.ps1` | `mt_case.ps1 run-dir` |
| `ft_launch.ps1 --side both`（顺序起两条实例） | `ft_launch.ps1` | `mt.ps1 --phase <p>` 无 `--version`（三线顺序） |
| —（N/A） | — | `mt_watchdog.ps1 -Action stop`：本台不提供 watchdog |
| —（N/A） | — | 三线顺序：fabric 只有一条线，无此维度 |

**放行**：`--phase build / env / launch / stop`、`--phase case --case <X>`、`ft_case.ps1 run --case <X>`、
`ft_case.ps1 validate`、`ft_case.ps1 list`、以及手动读数/注入类（`ft_inject` / `ft_assert` / `ft_dispatchreport`）。

---

## 5. 用例 JSON 格式（fabric 侧自定）

```json
{
  "case_id": "FAB-XXX",                     // 必填
  "title": "一句话说明",                      // 必填
  "side": "server",                          // 可选，默认 server
  "window": "case",                          // 可选，默认 case（case|launch|whole）
  "on_fail": "keep_game_running",            // 仅为与生产线用例形似；本台 v1 未实现该行为
  "steps": [
    { "op": "note",           "text": "…" },
    { "op": "inject_command", "command": "/time set day", "channel": "rcon" },
    { "op": "wait",           "ms": 1500 },
    { "op": "snapshot",       "window": "case" }
  ],
  "asserts": [
    { "type": "log",    "pattern": "…", "window": "whole" },
    { "type": "absent", "pattern": "…" },
    { "type": "jar",    "models_min": 137, "recipes_min": 118, "bountiful_max": 0, "trinkets_min": 8, "embed_lib": true },
    { "type": "dispatch_tick" },
    { "type": "dispatch_fired", "events": ["ServerTickEvent"] },
    { "type": "dispatch_zero_absent", "events": ["ServerTickEvent"] },
    { "type": "crash" }
  ]
}
```

* `op` 全集：`note` / `wait` / `snapshot` / `inject_command`（`command` 或 `text`；`channel` 默认 `rcon`；`side` 取用例级）。
* `assert.type` 全集见上；`log`/`absent` 的 `window` 可逐条覆盖用例级默认值。
* 用例级 `window: "whole"` 用于**启动期事实**（如 mods 清单打印在 launch 窗口起点之前）；写成 `case` 会必然假 FAIL。
* 校验：`pwsh -File scripts/test/fabric/ft_case.ps1 validate --all`（schema 检查，不碰游戏）。
* 注入失败（命令缺失 / 通道不可用）⇒ 用例**显式 ERROR 中止**，不静默降级（沿用生产线 §5 的读数纪律）。

---

## 6. 机器可读读数行（前缀汇总）

| 前缀 | 出现脚本 | 含义 |
|---|---|---|
| `AP_FAB_JAR` / `_MODELS` / `_RECIPES` / `_BOUNTIFUL` / `_TRINKETS` / `_EMBED` / `_EMBED_LIB` / `_BUILD` | `ft_build` | 开包核对读数（jar vs 磁盘源对照） |
| `AP_FAB_ENV_DIR` / `_SUBDIRS` / `_EULA` / `_SURFACE` / `_MODS_LIST` / `_REQ` / `_RCON` / `_WATCHER` / `_INSTALL` / `AP_FAB_ENV` | `ft_env` | 环境实况与前置判定 |
| `AP_FAB_LAUNCH_TASK` / `_PID` / `_READY` / `AP_FAB_LAUNCH` | `ft_launch` | 启动与就绪 |
| `AP_FAB_STOP_STATE` / `_KILL` / `_DAEMON` / `_PURGE` / `_KILLED` / `AP_FAB_STOP` | `ft_stop` | 收停 |
| `AP_FAB_INJECT_TARGET` / `AP_FAB_INJECT` / `AP_FAB_INJECT_RESP` | `ft_inject` | 注入与回显 |
| `AP_FAB_SNAPSHOT` / `AP_FAB_ASSERT` / `AP_FAB_ASSERT_CASE` | `ft_assert` | 快照与逐条断言 |
| `AP_FAB_DISPATCH` / `_FIRED` / `_ZERO` / `_ZERO_LIST` / `_ZERO_COUNT` / `_TICK` / `_RESULT` | `ft_dispatchreport` | 派发统计（**`_ZERO` 是护栏主体**） |
| `AP_FAB_CASE_BEGIN` / `_ENTRY` / `_VALIDATE` / `_VALIDATE_SUMMARY` / `AP_FAB_CASE` | `ft_case` | 用例执行/校验 |
| `AP_FAB_PHASE_BEGIN` / `AP_FAB_PHASE` / `AP_FAB_REPORT` | `ft.ps1` | 阶段编排 |
| `MT_FAB_<NAME>: OK/FAIL/ERROR/BLOCKED` | 全部 | 结论行（stderr 走 FAIL/ERROR，stdout 走 OK） |
| `MT_FAB_NOTE[n]` / `MT_FAB_INFO` / `MT_FAB_WARN` | 全部 | 过程行 |

---

## 7. 实测 vs 仅静态校验（诚实区分）

### 7.1 语法解析自检（全部 10 个 `.ps1/.psm1`，`$errs.Count` = 0）

自检命令（把结果落盘后读取，因为本会话的 PowerShell 工具不回显 stdout）：

```powershell
pwsh -NoProfile -Command "$errs=$null; [void][System.Management.Automation.Language.Parser]::ParseFile('<路径>',[ref]$null,[ref]$errs); $errs.Count"
```

结果（同时落盘在 `scripts/test/fabric/.syntax-check.txt`，可随时重生成；该文件被 `.gitignore` 忽略，
属本地证据，末尾还附了最后两条复验调用（`validate --all`、`FAB-JAR-ASSETS`）的原始输出。`PSVersion=7.6.6`）：

| 文件 | `$errs.Count` |
|---|---|
| `ft.ps1` | 0 |
| `ft_build.ps1` | 0 |
| `ft_env.ps1` | 0 |
| `ft_launch.ps1` | 0 |
| `ft_stop.ps1` | 0 |
| `ft_inject.ps1` | 0 |
| `ft_assert.ps1` | 0 |
| `ft_dispatchreport.ps1` | 0 |
| `ft_case.ps1` | 0 |
| `lib/Ft.Common.psm1` | 0 |

> 首轮 `ft_env.ps1` 曾报 3 处 `变量引用无效。“:”后没有有效的变量名称字符`（`"$s: …"` 被解析成
> `$s:` 作用域限定）—— 已改为 `"${s}: …"`，复检 0 错误。

### 7.2 **实际跑过**的清单（30 个调用，含退出码）

用 `Start-Process -RedirectStandardOutput/-RedirectStandardError` 逐条跑（刻意**不用** `2>` 原生重定向：
它会把子进程输出二次解码，中文变乱码）。

| # | 调用 | 退出码 | 结论 |
|---|---|---|---|
| 01 | `ft_build.ps1 --jar <产物>` | 0 | `AP_FAB_MODELS: jar=138 src=137 ok=true`、`recipes jar=132 src=118 ok=true`、`bountiful jar=0 ok=true`、`trinkets jar=15 src=8 ok=true`、`EMBED_LIB present=true` |
| 02 | `ft_env.ps1 --side server --mode verify` | 0 | 8 项前置全 `present=true source=loom`；`AP_FAB_ENV_RCON: enabled=false`（并给出 WARN） |
| 03 | `ft_env.ps1 --side client --mode verify` | 0 | 同上（客户端侧） |
| 04 | `ft_dispatchreport.ps1 --side server` | 0 | `fired=10 idle=26 ServerTickEvent=599`，26 个 0 次事件逐条列出 |
| 05 | `ft_assert.ps1 log --pattern 'Done \(\d' --window whole` | 0 | `hits=1` |
| 06 | `ft_assert.ps1 absent --pattern <不存在>` | 0 | `hits=0` |
| 07 | `ft_assert.ps1 log --pattern <不存在>`（**期望 FAIL**） | 1 | `ok=false`，stderr `MT_FAB_ASSERT: FAIL` ✔ 语义正确 |
| 08 | `ft_assert.ps1 snapshot --side server --window case` | 0 | `cursor=19523` |
| 09 | `ft_case.ps1 list` | 0 | 4 条用例 |
| 10 | `ft_case.ps1 validate --all` | 0 | `total=4 bad=0` |
| 11 | `ft_case.ps1 run --case FAB-DISPATCH-BASIC.json` | 0 | 8/8 断言通过 |
| 12 | `ft_case.ps1 run --case FAB-BOOT-EMBED.json` | 0 | 10/10 断言通过 |
| 13 | `ft_case.ps1 run --case FAB-JAR-ASSETS.json` | 0 | 1/1 断言通过 |
| 14 | `ft_stop.ps1 --side server`（无实例在跑） | 0 | `AP_FAB_STOP: OK`（0 个进程，无残留） |
| 15 | `ft.ps1`（无 `--phase`）| **2** | stderr 首行 `MT_AUTO_DISABLED: 全流程（无 --phase…）` ✔ |
| 16 | `ft.ps1 --phase case`（无 `--case`）| **2** | `MT_AUTO_DISABLED: --phase case 未指定 --case…` ✔ |
| 17 | `ft.ps1 --phase report` | **2** | `MT_AUTO_DISABLED: --phase report…` ✔ |
| 18 | `ft_case.ps1 run-dir` | **2** | `MT_AUTO_DISABLED: run-dir…` ✔ |
| 19 | `ft_launch.ps1 --side both` | **2** | `MT_AUTO_DISABLED: --side both…` ✔ |
| 20 | `ft_inject.ps1 cmd --command "/say hi"`（无服务端）| 2 | `AP_FAB_INJECT: FAIL (channel=rcon reason=connect-or-auth)` + ERROR（真实错误路径）|
| 21 | `ft_inject.ps1 cmd … --channel kubejs`（无观察者）| 2 | `reason=watcher-missing` + 指引 ✔ |
| 22 | `ft_build.ps1 --help` | 0 | 用法 |
| 23 | `ft.ps1 --phase report --allow-auto` | 0 | 临放行生效：产出 `reports/<ts>/report.md` ✔ |
| 24 | `ft.ps1 --phase build --jar <产物>` | 0 | 阶段透传 ✔（**修 bug 前此处打印的是 `rc=System.Object[]`**，见下） |
| 25 | `ft_case.ps1 run-dir --allow-auto` | 2 | 临放行生效：不再打 `MT_AUTO_DISABLED`，改打「即使放行也未实现」✔ |
| 26 | `MT_ALLOW_AUTO=1 pwsh … ft.ps1 --phase report` | 0 | 环境变量开关生效 ✔ |
| 27 | `ft_assert.ps1 frobnicate` | 2 | `MT_ERROR: 未知子命令 'frobnicate'` ✔ |
| 28 | `ft_assert.ps1 case --case FAB-JAR-ASSETS.json` | 0 | 直接跑用例断言 ✔ |
| 29 | `ft.ps1 --phase env --side server --mode verify` | 0 | 阶段透传 ✔ |
| 30 | `ft_case.ps1 run --case FAB-INJECT-ROUNDTRIP.json`（无服务端）| 2 | 注入步骤**显式 ERROR 中止**（`reason=inject-failed step=2 rc=2`）✔ 不静默降级；同时证明带空格的命令参数透传正确 |

**实测中修掉的两个真实缺陷**（都是这套代码自己的，不是既有生产线的）：

1. **子进程输出被管道吞掉 ⇒ 退出码变成数组**：`ft.ps1` 原用 `& pwsh -File …; return $LASTEXITCODE`，
   而 `&` 会把子进程 stdout 收进当前管道 ⇒ `ft.ps1 --phase build` 打印 `AP_FAB_PHASE: build rc=System.Object[]`。
   修法 = 新增 `lib/Ft.Common.psm1#Invoke-FtChildProcess`（`Start-Process` + 临时文件 + 原始字节转发），
   `ft.ps1` 与 `ft_case.ps1` 的注入调用都改走它。
2. **`&` + `2>` 会二次解码 ⇒ 中文乱码**：实测子进程 UTF-8 的 stderr 经 PowerShell 原生命令重定向后
   在文件里变成 `涓枃` 一类乱码（同一进程写文件直接读字节则完全正确：`e6 b6 93…` vs `e4 b8 ad…`）。
   同一条 `Invoke-FtChildProcess` 一并解决（字节转发不经过 PowerShell 的文本层）。

另外修掉 1 处**判定误报**：`ft_env.ps1` 的 fabric-api 正则写成
`fabric-api-fabric-api-\d`，而 Loom 缓存实际命名是 `<group>-<artifact>-<hash8>-<version>.jar`
⇒ 首轮实测把 `fabric-api` 判成 `present=false source=absent`（**假 FAIL**）。已改为
`fabric-api-fabric-api-[0-9a-f]{8}-\d`，复跑 `present=true source=loom`。

### 7.3 **只做了静态校验、没有实跑**的清单（必须在真机上补验）

| 项 | 为什么没跑 | 补验方式 |
|---|---|---|
| `ft_build.ps1` 的**完整构建**（不带 `--jar`） | 会跑 `gradlew :fabric-1.20.1:build`（分钟级 + 写产物目录） | `pwsh -NoProfile -File scripts/test/fabric/ft_build.ps1` |
| `ft_launch.ps1` 真正起客户端/服务端 | 会拉起游戏进程与 gradle 守护 | `--side server`（先，快）/ `--side client` |
| `ft_stop.ps1` 对**真在跑**的实例收停（CIM 路径只跑到「枚举为空」） | 同上 | 起实例后 `--side server`，再看 `AP_FAB_STOP: OK/PARTIAL` |
| `ft_env.ps1 --mode install`（拷前置进 `mods`） | 会写运行目录（且与 gradle classpath 重复投放有风险，脚本已 WARN） | 仅在「直接 java 启动」路径下用 |
| `ft_env.ps1 --enable-rcon`（改 `server.properties`） | 会改运行环境配置 | 需要 RCON 通道时再执行，然后**重启**服务端 |
| `ft_inject.ps1` 的 **RCON 成功路径**（认证 + 命令 + 回显解析） | 本机没有跑着的 RCON 服务端（`enable-rcon=false`） | `--enable-rcon` → 重启 → `ft_inject cmd --command "/say hi"`，看 `AP_FAB_INJECT_RESP:` |
| `ft_inject.ps1 --channel kubejs` 的成功路径 + `ft_cmd_watcher.js` | 需冷启动服务端让 KubeJS 加载观察者；且脚本里的 `Java.loadClass('…JsonIO')` / `java.nio.file.Paths` / `Files.writeString` 三处 Java 互操作**未在 KubeJS 的 ClassFilter 下冒烟**（依据来自 `javap`，见脚本头注释） | `ft_env --install-watcher` → 冷启动 → 跑 `cases/FAB-INJECT-ROUNDTRIP.json`；若被 ClassFilter 拒绝，日志会打 `[ft-inject] ERR`，脚本会显式失败（不会静默） |
| `--fg` 前台启动模式 | 同「真正起游戏」 | `ft_launch --side server --fg` |
| 客户端侧 `Sound engine started` 就绪标记 | 需要起客户端 | `ft_launch --side client` |

**没有任何一项结论是我「猜」的**：所有路径/类名/方法名/日志前缀都来自 §9 的依据索引，或来自
`javap`／开包／实跑读数。

---

## 8. 已知覆盖缺口与限制（如实标注）

1. **客户端 GUI 键鼠注入未覆盖**：生产线的 `mt_inject.ps1`（Win32 `PostMessage` + en-US 键盘布局 + 窗口线程
   输入法切换）未移植；fabric 台 v1 的注入只面向**服务端命令**（RCON / KubeJS 队列）。客户端的
   渲染/输入类缺陷本台**抓不到**。
2. **客户端派发报告未断言**：`ft_dispatchreport.ps1 --side client` 已实现读取，但没有对应用例
   （需要起客户端 + 走到会派发的客户端事件）。
3. **无 watchdog / 无停滞自动收停**：按现行规则这是刻意缺省（无人值守长流程已被闸门禁用）。
4. **断言窗口是简化版**：生产线 `mt_assert.ps1` 用「文件身份锚点（ctime + 头部 64 KiB 指纹）」处理
   log4j 跨零点日切；本台只记字节长度，长度回退时 WARN 并退化为整文件读取（见 `lib/Ft.Common.psm1`
   的 `Save-FtSnapshot` 注释）。
5. **`on_fail: keep_game_running` 未实现**（生产线的「保留现场」机制）；失败时实例照常留着，由人工决定收停。
6. **未做「清场 `/kill @e`」与「禁用生物 AI」硬闸门**（生产线 §2 强制项）：fabric 侧需要时经
   `ft_inject` 的 RCON 通道手动下发 `/kill @e[type=!player,distance=..128]`。
7. **`run/fabric-1.20.1/mods`（`build.gradle:248,313-326` 的 `pushToDevRun` 目标）与 Loom dev-run 的
   classpath 是两套投放面**，本台的 `ft_env` 只校验后者（`loom-cache/remapped_working`）+ `run/<side>/mods`。

---

## 9. 依据索引（脚本注释里也逐条写了，此处汇总）

| 事实 | 依据 |
|---|---|
| fabric 子项目 / 运行目录 / datagen 目录 | `fabric-1.20.1/build.gradle:1-2, 172-182, 189-200` |
| `src/generated/resources` 必须进产物（真实坑） | `fabric-1.20.1/build.gradle:27-43` 的注释与 `sourceSets.main.resources` |
| `META-INF/jars` 内嵌库口径 | `fabric-1.20.1/build.gradle:85-89, 378-407` |
| 前置清单 | `fabric-1.20.1/build.gradle:65-158`、`fabric-1.20.1/gradle.properties`、`src/main/resources/fabric.mod.json:29-40` |
| 产物分发三落点与白名单 | `fabric-1.20.1/build.gradle:226-416` |
| 退出码表 | `scripts/test/lib/Mt.Phase.psm1:29-36` |
| 闸门口径 | `scripts/test/TESTING-RULES-OVERVIEW.md` §3.1、§3.2、§10 |
| `LoaderBus#dispatchReport()` 格式 | `fabric-1.20.1/src/main/java/.../platform/event/LoaderBus.java:67-89` |
| 派发统计打印点（关服 / 开局 600 tick） | `.../platform/FabricBridges.java:81-104` |
| tick → 事件派发路径 | `.../platform/FabricBridges.java:107-137` |
| 桥接覆盖表（哪些事件由谁派发） | `.../platform/FabricBridges.java:45-73` |
| 必须装桥否则全不派发（真实事故） | `.../AstralDiceMod.java:47-56` |
| entrypoint | `fabric-1.20.1/src/main/resources/fabric.mod.json:14-24` |
| dev-run 主类与 `fabric.dli.*` 属性 | 实测 `net.fabricmc:dev-launch-injector:0.2.1+build.8` 内 `net/fabricmc/devlaunchinjector/Main.class`（jar 列目录 + 常量池 `grep`） |
| KubeJS `server.runCommand` | `javap dev.latvian.mods.kubejs.core.MinecraftServerKJS` → `kjs$runCommand(String):int`、`kjs$runCommandSilent(String):int` |
| KubeJS `Java.loadClass` | `javap dev.latvian.mods.kubejs.bindings.JavaWrapper#loadClass(String):Object` |
| KubeJS `JsonIO.readString(Path)` | `javap dev.latvian.mods.kubejs.util.JsonIO#readString(java.nio.file.Path):String` |
| `ServerEvents.tick` + `console.info` 可用 | `run/server/kubejs/server_scripts/event_bridge_probe.js` 实测执行（`run/server/logs/latest.log:222-229`） |
| 服务端就绪标记 `Done (…)!` | 实测 `run/server/logs/latest.log:213` |
| 客户端就绪标记 `Sound engine started` | 实测 `run/client/logs/latest.log:372` |
| RCON 键存在 | 实测 `run/server/server.properties:4,33,41`（`rcon.port=25575` / `enable-rcon=false` / `rcon.password=`） |
| 日志实测样例（派发报告 / 内嵌库） | `run/server/logs/latest.log:70`（`- starengine_lib 1.0.7`）、`:230-232`（派发统计） |

---

## 10. 与既有资产的关系（只读边界）

* **未改动**任何生产线文件：`src/` 源码、`scripts/test/mt*.ps1`、`scripts/test/lib/*.psm1`、
  `scripts/test/cases/*.json`、`scripts/test/TESTING-SPEC.md`、`TESTING-RULES-OVERVIEW.md` 全部原样。
* `scripts/test/fabric/event_bridge_probe.js` **原样保留**（它是既有人工取证脚本，本台不接管它）。
* 本台运行时只写三类位置：`scripts/test/fabric/`（`.ft_offsets.json` / `.ft_launch_state.json` /
  `reports/`，见 `.gitignore`）、仓库根 `temp/`（子进程日志）、以及 fabric 的运行目录
  （`fabric-1.20.1/run/**`，仅 `ft_env --mode install/--enable-rcon`、`ft_launch`、`ft_stop --purge-saves`
  会写；默认的 `verify` / 读数类脚本**只读**）。
