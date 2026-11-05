# dev-next 测试台 bash 化：裁决记录与移植契约

> 状态：**执行中**（2026-11-02，DSH）。本文件是 `docs/features/devnext-bash-testbed-gap.md（已删除 2026-11-05）`（缺口盘点）的续篇，只记「已定的事 / 契约数字 / 谁在做什么」，不重复盘点。
> 事实源：`scripts/test/**` 与 `scripts/test/win/lib/phases.sh` 实读；行号会漂，引用时以实读为准。

## 1 已定的口径

| 项 | 裁决 | 依据 |
|---|---|---|
| 目标 | **pwsh 测试资产全删**，全部迁到 bash + python（Windows 走 MSYS2） | 用户 m00042 / m00458 / m00426 |
| 迁移顺序 | 先补齐 bash 原生实现，再删 pwsh（保证 P0 测试台全程可用） | 本会话 |
| native 启动通道 | 不重写 user32；改走 AutoJS 通道（能力走 HTTP，不走窗口注入） | 用户「全删」口径 |
| 机器行 | `MT_CASE_RESULT: <case> = PASS|FAIL|ERROR|BLOCKED|TIMEOUT` 与退出码 0/1/2/11/12 **逐字不变** | 迁移纪律 |
| 能力缺失 | 一律 BLOCKED(11)，**不得**假绿 | 迁移纪律 |
| 版本 | 四线已 bump 到 `26.4-SNAPSHOT.6` | 用户 m00426 |

## 2 共享契约（迁移时必须逐字对齐）

### 2.1 退出码
PASS 0 / FAIL 1 / ERROR 2 / PREFLIGHT 10 / BLOCKED 11 / TIMEOUT 12。
pwsh 侧定义：`scripts/test/lib/Mt.Phase.psm1:29-36`；bash 侧同名常量：`scripts/test/win/lib/phases.sh:41-46`。

### 2.2 机器行格式（Mt.Phase.psm1:19-25，bash 同映射 phases.sh:228-238）
- `===== MT_PHASE: <name> =====`
- `MT_<NAME>: OK (<elapsed>) — <detail>`（stdout）/ `FAIL` / `BLOCKED` / `ERROR`（stderr）
- `MT_INFO: …` / `MT_WARN: …`(stderr) / 裸 `MT_ERROR: <text>`(stderr)
- 耗时格式：`<secs>s` 或 `<m>m<ss>s`

### 2.3 用例执行器 mt_case.ps1 的封闭表（`mt_case.ps1:214-243`）
- 子命令：`run` / `run-dir` / `validate`；参数 `--version --case --dir --channel autojs|native --allow-auto --case-timeout`
- 用例 JSON 必填：`case_id / title / version / steps`；`channel ∈ {autojs,kubejs}` 默认 kubejs；`side ∈ {client,server}`
- op 封闭表：`inject_key / inject_mouse / inject_command / kubejs_reload / wait / screenshot / assert / eval_js / await_state / mcp_status / jar_stats / note`
- 断言类型：`log / absent / crash / kubejs / mixin / vision / eval / state / jar / bytecode / bytecode_absent`
- 分桶：USER_TERMINATED→2、FAIL→1、BLOCKED→11、TIMEOUT→12、ERROR→2、其余→0；空目录 → `MT_CASE: 无条目文件` rc=11

### 2.4 断言执行口径（`Invoke-MtCaseAssert` mt_case.ps1:1737 起）
| type | 参数 | 判定 |
|---|---|---|
| jar | `from`（默认指向 `op=jar_stats` 的 save_as）+ `models_min/recipes_min/trinkets_min/entries_min/signs_min`(≥)、`bountiful_max/embed_max`(≤)、`embed_lib` | 无该键 → FAIL `jar-state-missing`；逐项比 ≥/≤ |
| bytecode / bytecode_absent | `pattern`（正则，必填）对 jar 内条目全名 match | 无产物 jar → FAIL `no-jar`；命中≥1 / 零命中 |
| vision | `image` + `question` | 四条硬约束（登记/世代内/存在/≥20480B/晚于用例窗口起点）缺一即 BLOCKED；全通过返回 `DELEGATED: VISION_PATH=<abs>` |

**`VISION_PATH` 在仓库内只有生产者、无消费者** ⇒ 视觉判定在测试台之外，python 侧保留 `DELEGATED` 契约、判定本身记 BLOCKED 是正确降级。

### 2.5 AutoJS HTTP 形态（`Mt.AutoJs.psm1`）
- `POST http://127.0.0.1:25585/mcp`，JSON-RPC 2.0；头 `Content-Type: application/json` + `Accept: application/json, text/event-stream`
- 握手三步：`initialize`(id=1, 协议 `2025-06-18`) → `notifications/initialized` → `tools/call`(id=2)
- 工具：`status{}` / `eval_js{code,side,timeout_ms}` / `kubejs_log{side,kind,limit}` / `reload_scripts{side,timeout_ms}`；`side ∈ auto/server/client`
- 返回：`result.content[].text` 是 JSON 串（`ok/side/elapsedMs/type/value`），不足退 `structuredContent`
- 单请求超时 15000ms；仅 HTTP 4xx 重握手重试一次；端点不可用 ⇒ 调用方记 BLOCKED 不记 FAIL
- bash 侧已有半套实现可复用：`scripts/test/win/lib/common.sh:425-569`（含 `mt_aj_probe` 完整握手，**全程不落临时文件**）

## 3 phases.sh 的委托点（改接时必须逐处替换）

| 行号 | 现调用 | 改接目标 |
|---|---|---|
| :95-97 | 内联 `pwsh -Command` 取 runId（`Mt.Paths.psm1 Get-MtActiveRunId`） | bash 直接读 `scripts/test/cases/.mt_active_run`，缺失则时间戳 + WARN |
| :306-307 | `mt_launch.ps1 --version <v> --channel native` | 删除 native 分支 ⇒ 只留 gradle/production 通道 |
| :377,:385 | `mt_case.ps1 run|run-dir …` | `python scripts/test/lib/mt_case.py run …` |
| :403,:409 | `mt_report.ps1 collect` | `python scripts/test/mt_report.py collect` |
| :419-420 | `mt_report.ps1 mark` | `python scripts/test/mt_report.py mark` |
| :427-428 | `mt_report.ps1 summary` | `python scripts/test/mt_report.py summary` |
| :440,:446 | `mt_preflight.ps1` | `python scripts/test/mt_preflight.py` |

## 4 会让 bash 立刻坏、必须同批改的位置

| 位置 | 现状 | 删后表现 |
|---|---|---|
| `scripts/test/linux/env.sh:101` | 必存在清单含 `scripts/test/mt.ps1` | Linux env 阶段 `log_fail repo` |
| `scripts/test/win/env.sh:76-77,:83` | 同上，CHECK 文案写「mt.ps1 齐全」 | `FAIL repo` |
| `scripts/test/win/mt.sh:196-197` | 提示串教用户跑 `pwsh -File scripts/test/mt_{inject,assert}.ps1` | 误导（不报错） |
| `.github/workflows/build.yml:113/:116/:119/:125` | `pwsh -File tools/check_lang_sync.ps1` | CI 红；改指 `python tools/check_lang_sync.py` |
| `.github/workflows/build.yml:140` | `pwsh -File scripts/verify/verify_chip_acquisition.ps1` | CI 红；需 python 版 |

## 5 判据（删完自证，不得用 --dry-run）

`--dry-run` 分支全部在 `mt_phase_pwsh_file` **之前** return（phases.sh:300-305 / :379-384 / :404-408 / :441-445），所以 dry-run 全绿**不能**证明委托可用。真判据：

1. `for f in scripts/test/win/*.sh scripts/test/win/lib/*.sh scripts/test/linux/*.sh; do bash -n "$f"; done` 全绿
2. `bash scripts/test/win/mt.sh --version 1.21.1 --phase preflight` ⇒ rc 应为 0 或 10，**不得**是 2
3. **反向判据**：`MT_PWSH_EXE=/nonexistent/pwsh bash scripts/test/win/mt.sh --version 1.21.1 --phase preflight` ⇒ 必须 rc=2（证明无静默降级）
4. grep 门禁：`grep -rn '\.ps1\|\.psm1' scripts/test/win/ --include=*.sh | wc -l` ⇒ 0（现状 71）；`scripts/test/linux/` ⇒ 0（现状 26）
5. `python scripts/test/lib/mt_case.py validate --cases-dir scripts/test/cases` ⇒ `errors=0`
6. 用例 JSON 不会失效：`.ps1` 只出现在 `text`(51) / `title`(13) / `machine_judge_none`(4)，可执行字段 0 处

## 6 在办事项（子代理分工）

| 范围 | 产出 | 状态 |
|---|---|---|
| G1 用例引擎 | `scripts/test/lib/mt_case.py` + `mt_autojs.py` 补 `jar_stats` / `jar` / `bytecode` / `bytecode_absent` / `inject_command` 语义，validate 收敛 errors=0 | 子代理进行中 |
| G2/G3 preflight + report | `scripts/test/mt_preflight.py` / `mt_report.py`（历史原件 + ps1 现 CLI 补齐） | 子代理进行中 |
| 改接与删除 | phases.sh 7 处委托点、env.sh/linux env.sh 存在性检查、CI 指 python、62 个 pwsh 删除 | 待 G1–G3 验收后 |

## 7 待用户裁决

1. `vision` 17 处记 BLOCKED（契约上仍产 `VISION_PATH`）——是否接受。
2. fabric 的 `ft_inject.ps1`（手工逐条注入）与 `ft_dispatchreport.ps1`（dispatchReport 抽取）无 bash 等价物：直接删，还是用 python 重写。

## 8 P2 落地记录（2026-11-02 夜，全部已实测）

| 项 | 结论 | 判据 |
|---|---|---|
| `phases.sh` 委托点 | cases / report(mark,summary,collect) / preflight 全部改 `python -u`，新增 `mt_phase_py_file()` 与旧的 `mt_phase_pwsh_file` 同构 | `grep -rn 'pwsh -[A-Za-z]* -File' scripts/test/win/` = 0 |
| 死代码清理 | `mt_phase_deprecated` / `mt_phase_pwsh_exe` / `mt_phase_pwsh_file` 三函数整段删除（491→439 行），机器行去掉 `MT_DEPRECATED:` | `bash -n` 通过；两个函数名 grep = 0 |
| run-id | 不再 `Import-Module Mt.Paths.psm1`：纯 bash 读 `scripts/test/cases/.mt_active_run`，形如 `YYYYMMDD-HHMMSS` 直接用，否则告警退回本地时间戳 | 实测 runid=20260921-191522（与文件一致） |
| native 通道 | `launch --channel native` 不再委托 pwsh，改为两行原因 + `LAUNCH: BLOCKED` + `return 11` | 实测 rc=11、`MT_LAUNCH: BLOCKED (0s) — 阶段返回 11` |
| PowerShell 解释器 | `mt_pwsh_exe()` 三级回退：pwsh → `C:/Program Files/PowerShell/7/pwsh.exe` → `%SystemRoot%/System32/WindowsPowerShell/v1.0/powershell.exe`（5.1），`MT_PWSH_EXE` 可钉死 | 5.1 实测 PSVersion=5.1.26100.9818，CIM 进程总数=329 |
| `env.sh` 仓库自检 | 必需文件清单由 `scripts/test/mt.ps1` 改 `scripts/test/win/mt.sh`；pwsh 检查改报「PowerShell 内核 7.6.9（非测试资产）」 | `CHECK: pwsh=OK`；整体 `ENV_RESULT: WARN — 0 项阻塞，2 项提示（18 项通过）` |
| CI | `.github/workflows/build.yml` 五处守门全部改 python：`python tools/check_lang_sync.py --line <line>`（×4；`--line` 可重复，**不是** `-LangDir`）+ `python scripts/verify/verify_chip_acquisition.py`；`setup-python` 上移并去重 | `grep -n pwsh .github/` = 0；`setup-python` 出现 1 次 |

**判据口径修订**：原定「`scripts/test/win/` 里 `.ps1`/`.psm1` 出现次数 = 0」不可达也不该达 —— 剩余 43 处（linux 侧 5 处）全是注释里的历史行号引用（「对齐 mt.ps1:783-790」这类），保留有溯源价值。改为 **0 可执行引用**：`pwsh -File`、`mt_phase_pwsh_file`、`mt_phase_deprecated` 三类命中数归 0。

**残余依赖（不是测试资产）**：CIM 进程命令行判线仍要 PowerShell 解释器（bash 无等价物），口径见 `docs/features/devnext-pwsh-retirement-register.md` §3；若要彻底去掉，需 python + ctypes 读 PEB 或引入 psutil（本机当前**没有** psutil）。

## 9. 实机回归（2026-11-02 深夜）暴露并修掉的五个测试台缺陷

首轮「全线快速实机」按新 bash 流程跑，四线各暴露一类**测试台自身**问题（非产品缺陷），逐条落盘：

| # | 症状 | 根因 | 修法 |
|---|---|---|---|
| 1 | kubejs 通道用例恒 BLOCKED：「native 注入通道随 pwsh 资产删除，python 侧无等价实现」 | `scripts/test/lib/mt_case.py` 的 `inject_key`/`inject_mouse`/`inject_command` 仍按旧口径 raise Blocked，而 `scripts/test/mt_inject.py`（ctypes 直调 user32.SendInput）早已存在 ⇒ **有实现没接线** | `mt_case.py` 新增 `_native_inject()`：`[sys.executable, mt_inject.py, <子命令>] + argv + --version <线>`；rc 0 PASS / rc 2 Blocked / 其它记 FAIL |
| 2 | 接线后 cases 阶段直接崩 `UnicodeEncodeError: 'gbk' codec can't encode '⇒'` | `say()` 的 `print()` 在 GBK 控制台遇不可编码字符 | 新增 `safe_print()`：捕获 UnicodeEncodeError 后按 `sys.stdout.encoding` 用 `replace` 重编码 |
| 3 | `AP_H1_HT:…(:\n|$)` 这类以 `$` 结尾的断言**恒 FAIL**，而单跑 `mt_assert.py log` 却 PASS | `mt_case.read_log()` 保留原字节，Windows `latest.log` 是 CRLF ⇒ 行尾 `\r` 让 `$` 失配（`mt_assert.py` 自己做了归一，所以两边不同） | `log`/`absent` 分支统一 `text.replace("\r\n","\n").replace("\r","\n")`；另加「未命中时 4×0.4s 重读」抗日志落盘延迟 |
| 4 | 1.20.1 启动 `FileNotFoundException: logs\latest.log (拒绝访问)` | Gradle 自动供应的 `~/.gradle/jdks/eclipse_adoptium-17-amd64-windows.2` 带 `Mandatory Label\Low Mandatory Level`（沙箱残留）⇒ 其 java 进程是 Low IL，写不动 Medium 的 run 目录 | 已改用 `temp/p0/jdk17/jdk-17.0.20.1+1`；测试台新增 `MT_GRADLE_PRE_ARGS`（参数必须落在任务名**之前**，Gradle 只从命令行前段取 `-D`） |
| 5 | fabric 启动 `NoClassDefFoundError: com/eliotlash/mclib/math/IValue` → GeckoLib `JsonUtil` 初始化失败 | GeckoLib fabric 变体把 mclib 作为 **nested jar**（`META-INF/jars/mclib-20.jar`）内嵌，而 Loom 重映射会剥掉 `fabric.mod.json` 的 `jars` 声明 ⇒ dev 环境缺 mclib | **仓库内已有专用工具**：`python tools/loom_embedded_jars.py --apply`（写入 `run/{server,client}/mods`，dry-run 会打出 `NEED mclib-20.jar id=com_eliotlash_mclib_mclib <- geckolib-…jar`）。P1 线新增 GeckoLib 之后必须重跑一次 |

**操作提示**：`run/` 与 `fabric-1.20.1/run/` 都在 `.gitignore` 里，上述第 5 条的产物不随仓库走 ⇒ 换机器 / 清缓存后要重新 `--apply`。

## 10 fabric-1.20.1 测试环境与另三线对齐（Sodium + Iris + 光影，2026-11-03）

**用户口径**（m02714）：「为 fabric 1.20.1 补充测试环境缺失的模组（与其他线对齐，包括光影）」。

### 10.1 缺口盘点（实读）

| 线 | run/mods 关键件 | 光影包 |
|---|---|---|
| neoforge-1.21.1 | sodium 0.8.13 + iris 1.8.14-beta.1 + immediatelyfast 1.6.14 + kubejs/rhino + carpet + modernfix + ferritecore + collective + superflat | ✅ ComplementaryReimagined_r5.9.3 |
| neoforge-26.1.2 | sodium 0.9.2 + iris 1.11.4 + kubejs/rhino + ferritecore + collective + superflat | ✅ 同上 |
| forge-1.20.1 | 社区优化栈（无 Sodium/Iris，1.20.1 Forge 侧无对应产品） | ✅ 同上 |
| **fabric-1.20.1（改前）** | 只有 8 个 `astral_embedded-*` + endec/gson/netty/fiber/puzzlesaccessapi | ❌ **无 shaderpacks/** |

根因不是「没写依赖」而是「被开关关掉」：`fabric-1.20.1/build.gradle` 早有 sodium/indium，被
`if (providers.gradleProperty('testSodium').getOrElse('true') != 'false')` 包住，而测试台长期用
`MT_TEST_SODIUM=false` 透传 `-PtestSodium=false`——因为带着 Sodium 启动必崩（见 10.3 第 1 条）。

### 10.2 落地清单

1. `fabric-1.20.1/build.gradle`：在 kubejs/rhino 之后新增「渲染栈对齐」块 ——
   `iris:s5eFLITc`（1.7.6+mc1.20.1，硬依赖 sodium ⇒ 与 testSodium 同进同出）、
   `immediatelyfast:iwYUrQJO`（1.5.5）、`superflat-world-no-slimes:I6QPoepF`（1.20.1-3.7）、
   `collective:SIqDV2gB`（1.20.1-8.73，superflat 的硬依赖）。
2. `fabric-1.20.1/build.gradle` 末尾新增 **LWJGL 钉版**块：启用 Sodium 时把 `org.lwjgl:*` 的
   `*runtimeClasspath` 解析版本钉到 **3.3.1**。
3. 光影包投放改为**可复现**：新增 `tools/fetch_shaderpacks.py`（纯标准库）
   —— `python tools/fetch_shaderpacks.py --run-dir fabric-1.20.1/run/client`，
   校验 zip 内含 `shaders/`、已存在即幂等秒退；机器行 `SHADERPACK: OK|NEED|FAIL` +
   `SHADERPACK_RESULT: OK|FAIL`。下载源 = Modrinth project `HVnmMxH1`（Complementary
   Shaders - Reimagined）version `Bqen1mJX` = r5.9.3，**实测 553,397 B 且 sha256 与既有三线文件全等**
   （`fed6c879e732cf7f…`）。
4. `fabric-1.20.1/run/client/config/iris.properties`：`enableShaders=true`、
   `shaderPack=ComplementaryReimagined_r5.9.3.zip`、`maxShadowRenderDistance=32`、`colorSpace=SRGB`
   （照抄 `run/1.21.1/config/iris.properties` 口径）。

### 10.3 带 Sodium 启动踩出来的三层坑（按顺序，缺一层都起不来）

| # | 症状（日志原文） | 根因 | 修法 |
|---|---|---|---|
| 1 | `(Sodium-EarlyDriverScanner) The game failed to start because the currently active LWJGL version is not compatible` / `Installed version: 3.3.2-snapshot` / `Required version: 3.3.1` | Sodium 0.5.x 只认 LWJGL **3.3.1**（= 1.20.1 自带），而 Loom 的 dev runtime 带 **3.3.2-snapshot**；文件里旧的注释把方向写反了（写「需要 3.3.2」），照写的钉版反而必崩 | 钉版值改 **3.3.1**，并订正那段历史注释 |
| 2 | `(ImmediatelyFast) Failed to initialize Iris compatibility …` → `NoClassDefFoundError: net/lenni0451/reflect/accessor/FieldAccessor`，进程死在 `Window.<init>` | ImmediatelyFast 1.5.x 的 Iris 兼容层要 `net.lenni0451:Reflect`，而 Iris **1.8.x 才内嵌**它；1.20.1 的 Iris 最高 1.7.6 ⇒ 没人提供 | `python tools/loom_embedded_jars.py --side client --apply`（会从 IF 的 nested jar 挖出 `Reflect-1.6.2.jar`，版本与 IF 自带一致；**不要**手写坐标，会抢类） |
| 3 | `(Iris) loadExternalShaderpack` → `NoClassDefFoundError: org/anarres/cpp/PreprocessorListener` | Iris 1.7.6 把 `jcpp-1.4.14` / `antlr4-runtime-4.13.1` / `glsl-transformer-2.0.1` 作为 nested jar 内嵌，Loom dev 下同样拿不到 | 同上，一条 `--apply` 一起补齐（本次 `written=12`） |

### 10.4 验收读数（2026-11-03 00:12–00:14 实跑）

- `MT_LAUNCH: OK (25s)`、`LAUNCH: WORLD_READY`、`game_pid=38240`、AutoJS `health=ok`。
- 加载栈实测：`sodium 0.5.13+mc1.20.1`、`iris 1.7.6+mc1.20.1`、`immediatelyfast 1.5.5+1.20.4`、
  `collective 8.73`、`superflatworldnoslimes 3.7`、`architectury 9.2.14`、`kubejs`、`rhino`、
  `fabric-api 0.92.12`，共 95 个 mod。
- 光影生效证据：`[Render thread/INFO] (Iris) Using shaderpack: ComplementaryReimagined_r5.9.3.zip`
  + `Iris.createPipeline` / `IrisRenderingPipeline.<init>` 实际执行（`CustomUniforms` 的告警是该光影包
  自定义 uniform 在 Iris 1.7.6 下的已知静默降级，不影响管线建立）。
- 用例回归（带渲染栈）：`BOOT-READY-fabric-1.20.1` = **PASS**、`JAR-ASSETS-fabric-1.20.1` = **PASS**
  （`jar=astral_dice-26.4-SNAPSHOT.6+fabric_1.20.1.jar models=158 recipes=125 embed=0`）。
- 收停：`MT_STOP: OK (0s)`，四线范围无残留 java。

### 10.5 换机 / 清缓存后的最小重建顺序

```bash
python tools/loom_embedded_jars.py --side client --apply      # mclib / jcpp / antlr / glsl-transformer / Reflect …
python tools/fetch_shaderpacks.py --run-dir fabric-1.20.1/run/client
bash scripts/test/win/mt.sh --version fabric-1.20.1 --side client --phase launch   # 不再需要 MT_TEST_SODIUM=false
```

### 10.6 仍存的能力缺口

- ~~`mt_capture.py capture --mode f2` 在 fabric 上定位不到游戏窗口~~ —— **2026-11-03 下午已修**（见 §11.3）。
- ~~fabric 仍没有测试世界（`NO_WORLD`）~~ —— **2026-11-03 下午已修**（见 §11.1/§11.2，`testworld` 现为 `minecraft:flat`）。

## 11 fabric-1.20.1 测试环境第二轮补齐（2026-11-03 下午，全部实跑验证）

### 11.1 `mt_env world` 在 fabric 上生成的是 noise 世界（分侧 run 目录错位）

- 症状：`python scripts/test/mt_env.py world --version fabric-1.20.1 --timeout 900` 起服务端后 12 分钟不判定完成，
  且落盘世界是 `minecraft:noise`（违反本线超平坦基准）。
- 根因：`scripts/test/mt_env.py cmd_world()` 用 `mc.get_paths(version)` 的单一 `run_dir`，
  而 `scripts/test/lib/mt_compat.py:186 run_root()` 对 fabric **按 side 分目录**
  （`fabric-1.20.1/run/client` vs `run/server`），`get_paths(version)` 默认 side=client
  ⇒ `server.properties`（含 `level-type=flat` / `level-name=testworld`）写进了 client 目录，
  服务端仍按自己的 `run/server/server.properties`（`level-type=minecraft:normal`）生成。
- 修法：`cmd_world` 对 fabric 显式取 `srv = mc.get_paths(version, "server")`，写属性、盯
  `srv.latest_log` 的 `Done (`、判崩溃报告、停进程、搬存档（`srv.server_world` → `paths.client_world`）
  全部走服务端目录；另加「停进程后最多 24×2s 轮询确认退净 + 搬档前删 `session.lock` + 3 次重试 +
  成功后 rmtree 源目录」，因为 runServer 会持有 `testworld/session.lock`（实测 `WinError 33`）。

### 11.2 fabric 服务端进程杀不掉（stop 变成 no-op）

- 症状：世界生成结束后 `runServer` 的 java 仍在（2.7 GB），`tasklist` 有残留、存档被锁。
- 根因：`mt_compat.test_pipeline_process()` 的判据只认
  「gradle-wrapper.jar + `:fabric-1.20.1:run`」或「`net.neoforged.devlaunch.Main`」；
  fabric dev 服务端的命令行是 `net.fabricmc.devlaunchinjector.Main` + `.gradle/loom-cache/projects/.../launch.cfg`，
  两条都不命中 ⇒ `stop_version_processes` 对该进程永远返回 0。
- 修法：`process_markers()` 对 fabric 追加 `fabric.dli.env=<side>` 标记（side 收窄，不跨侧误杀），
  且 `test_pipeline_process()` 认 `net.fabricmc.devlaunchinjector.Main`。

### 11.3 fabric 窗口定位（截图取证）

- 根因：`mt_capture.find_window()` 的回落分支要求 `version` 整串出现在窗口标题里，
  而 fabric 线的窗口标题是 `Minecraft* 1.20.1`、`version` 是 `fabric-1.20.1` ⇒ 永不命中，
  于是回落整屏抓图（抓到桌面）。
- 修法：回落判据改用「版本尾段」（`fabric-1.20.1` → `1.20.1`），仍只在**唯一候选**时接受。
- 验收：`python scripts/test/mt_capture.py capture --version fabric-1.20.1 --tag mods-verify --mode f2`
  ⇒ `MT_CAPTURE: OK — 2026-11-03_13.45.21.png … attempt=1`（1920×1080，画面确为游戏内超平坦世界）。

### 11.4 测试环境模组补齐（用户 2026-11-03 要求）

`fabric-1.20.1/build.gradle` 新增（全部 `modRuntimeOnly`，不进发货产物）：

| 模组 | 坐标（Modrinth version id） | 备注 |
|---|---|---|
| ModMenu 7.2.2 | `maven.modrinth:modmenu:lEkperf6` | 无声明依赖 |
| Carpet-Fixes 1.20-1.16.1 | `maven.modrinth:carpet-fixes:NBCnBGZj` | 硬依赖 `carpet`（已在） |
| Debugify 1.20.1+2.0 | `maven.modrinth:debugify:6XDJoCks` | 硬依赖 `yacl` |
| YACL 3.1.0+1.20 | `maven.modrinth:yacl:CgwTUAR2` | 按 Debugify 声明值钉版（不追 3.6.x） |
| conditional-mixin 0.6.5 | `com.github.Fallen-Breath.conditional-mixin:conditional-mixin-fabric:0.6.5` | Carpet-Fixes 的 `conditional-mixin: "*"` 硬依赖；不在 Modrinth，走 `https://maven.fallenbreath.me/jitpack`（repositories 已加） |

三处坑（都实测踩过）：

1. **conditional-mixin 的聚合坐标不能用**：`com.github.Fallen-Breath:conditional-mixin:0.6.5` 的 POM 同时依赖
   common/fabric/forge/neoforge 四个子件，Gradle 按 variant 选中 `conditional-mixin-neoforge`
   （class 版本 65 = Java 21）⇒ 本线 JDK17 报
   `only compatible with JVM runtime version 21 or newer`。必须写 **fabric 子工件**（class 版本 52，自带 `fabric.mod.json`）。
2. **Debugify 缺 yacl 会在 Loader Resolution 阶段硬拒**（`mod resolution failed`），不是运行时才炸。
3. **Carpet-Fixes 缺 conditional-mixin 症状**：`模组 'Carpet Fixes' (carpet-fixes) 1.20-1.16.1 需要 conditional-mixin 的 任意版本，但没有安装它`。

Indium（用户点名要求的 Sodium ↔ Fabric Rendering API 兼容层）**本就在** `build.gradle:168`（testSodium 块内），
本轮在启动日志里复验到 `indium 1.0.36+mc1.20.1` 实际加载。

### 11.5 验收读数（2026-11-03 13:43–13:46 实跑）

- 世界：`python temp/p0/world_gen_probe.py` ⇒ 四线全 FLAT，`WORLD_GEN_PROBE_RESULT: OK`（fabric 由 NOISE 转 FLAT）。
- 启动：`bash scripts/test/win/launch.sh --version fabric-1.20.1 --side client --quickplay testworld --enable-autojs`
  ⇒ `LAUNCH: READY` + `LAUNCH: WORLD_READY`（AutoJS client available=true，game_pid=22024）。
- mod 清单：`Loading 104 mods`，其中 `carpet 1.4.112+v230608` / `carpet-fixes 1.20-1.16.1` /
  `conditional-mixin 0.6.5` / `debugify 1.20.1+2.0` / `indium 1.0.36+mc1.20.1` / `modmenu 7.2.2`
  （仅剩 EMI/REI/amecs 之类**未安装**的可选 mixin 目标 ClassNotFound 警告，属正常）。
- 用例：`BOOT-READY-fabric-1.20.1 = OK`、`JAR-ASSETS-fabric-1.20.1 = OK`（注意 `--case` 要传**完整相对路径**，
  只传用例名会被当文件名 ⇒ `No such file or directory`）。
- 收停：`WIN_STOP: OK — 四线范围内无本 repo 的 Minecraft 客户端`。

## 12 四线 AutoJS / KubeJS / 测试台 mod 全量追新（2026-11-03 夜，全部实机验证）

用户裁决（原话）：「先更新所有测试台AutoJS、KubeJS版本」；「测试台其他mod，除26.1.2的sodium保持0.9.2版本外，其他所有mod（包括sodium）都更新到最新公开版本」。

### 12.1 引脚改动表

| 线 | 改动 |
|---|---|
| neoforge-1.21.1 | `neo_version` 21.1.235 → **21.1.265**（JEI 19.70 要求 `neoforge [21.1.238,)`）；`kubejs_version` build.372 → **build.381**；JEI 从 Curse 8512040 → `maven.modrinth:jei:1vC6z8wV`（19.70.0.469）；新增 `maven.modrinth:mezzconfig:sL7Ghaz1` |
| forge-1.20.1 | JEI Curse 8778011 → `maven.modrinth:jei:6E4ps2ER`（15.74.0.234）；新增 `mezzconfig:toOvLp8Y`；collective → `w5c5YvXl`（8.75）；superflat → `I6QPoepF`（3.7）；modernfix `OvpPdk44` → `OM5gX4LT`（5.27.91） |
| neoforge-26.1.2 | `neo_version` 26.1.2.109 → **26.1.2.126**（KubeJS 8.0.8 要求 `neoforge 26.1.2.110+`）；`kubejs_version` 8.0.6 → **8.0.8**；JEI → `maven.modrinth:jei:60HrmNPh`（29.53.0.115）；新增 `mezzconfig:5mEMrzgT`；jade `5jKeKXwB` → `Pr1h4azo`（26.1.12） |
| fabric-1.20.1 | JEI `pQiaGCAN` → `bTwbXhIB`（15.74.0.234，`fabric.mod.json` 的 jei 区间同步 `>=15.74.0`）；collective → `w5c5YvXl`；yacl `CgwTUAR2` → `dvS5DjUA`（3.6.6）；accessories `A7wwzJ0e` → `mee7rWXz`（beta.49，**解除原「1.20.1 已停更」钉死**）；puzzles-lib `N8gFdljq` → `QSW0oJPl`（8.1.36） |

**JEI 的新硬前置 mezz_config**：19.70 / 15.74 / 29.53 三个变体的 `mods.toml` / `neoforge.mods.toml` 都写了
`mezz_config [0.6.10,1.0.0) required`，缺失即 Loader 硬拒 ⇒ 三条线各补一个 `mezzconfig` 引脚
（1.20.1-fabric 原本就有 `mezzconfig:0.6.10`，未动）。
**26.1.2 的 sodium 保持 0.9.2**（用户点名），其余 sodium（1.21.1 = 0.8.13、fabric = 0.5.13）本身已是最新。

### 12.2 AutoJS：从新上游重编 + 保持 groupId

- 上游 `F:/MCProject/AutoJS`（分支 `fabric-1.21.1` @ `67b585e`）已把包根 `cc.sighs.autojs` → `com.sighs.autojs`；
  本仓沿用既定口径「**Maven groupId 仍写 `cc.sighs.autojs`**，只重编内容」，artifact version 仍 `1.0.0`。
- target 引脚：neoforge-1.21.1 kubejs 379→**381**、rhino `2101.2.7-build.81` → **`2101.2.8-build.91`**；
  neoforge-26.1 kubejs 8.0.6→**8.0.8**；forge/fabric-1.20.1 rhino `2001.2.2-build.17` → **`2001.2.3-build.10`**。
- 构建（targets **不是** Gradle 子项目，必须 `-p`）：
  `cd /f/MCProject/AutoJS && sh ./gradlew -p targets/<t> build -x test -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7897`
  ⇒ 四个 target 全 BUILD SUCCESSFUL。
- 发布：`PYTHONIOENCODING=utf-8 python scripts/test/tools/publish_autojs_maven.py --force`
  ⇒ `MT_AUTOJS_MAVEN_RESULT: OK published=2 skipped=1 failed=0`；
  产物 `AutoJS-neoforge-1.21.1-1.0.0.jar`（163,776 B）、`AutoJS-neoforge-26.1-1.0.0.jar`（164,801 B）复制进对应 `run/<ver>/mods`，
  forge/fabric 走 mavenLocal + `-PautojsTestMod=1`。
- 同坐标同版本覆盖 ⇒ 消费方若已有缓存需 `--refresh-dependencies`；本次 `~/.gradle/caches/.../cc.sighs.autojs` 本就不存在，
  首次实机启动即取到新件（实测 forge dev jar 内含 `com/sighs` 86 个 class、`cc/sighs` 0 个）。

### 12.3 fabric 专属坑：yacl 3.6.6 的 quilt-parsers 内嵌库

- 现象：`Could not execute entrypoint stage 'client' … yet_another_config_lib_v3` /
  `NoClassDefFoundError: org/quiltmc/parsers/json/gson/GsonWriter`。
- 根因同 §10.3 那类：Loom 重映射第三方 mod 时会删掉 `fabric.mod.json` 的 `jars` 声明但保留 `META-INF/jars/*.jar`
  ⇒ dev 运行期拿不到 `org_quiltmc_parsers_gson/json`。3.1.0 时代这两件不在依赖链上，故旧版无此问题。
- 修法：`python tools/loom_embedded_jars.py --apply --clean --side client`（--clean 先按清单撤掉旧投放，避免重复 id）；
  本次 `need=20 skip_dup=70 written=20`。
- **重复 id 陷阱**：`--clean` 只撤 `astral_embedded-*.jar`，早期手工投放的 `endec-0.1.8.jar / fiber / gson / netty /
  puzzlesaccessapi-fabric` 会与新的 `astral_embedded-<id>.jar` **撞 id**（Fabric Loader 直接拒绝启动）
  ⇒ 这 5 个旧件已挪到 `temp/quarantine-embedded/`。投放后务必按 `fabric.mod.json` id 聚合自查一次。
- 顺带确认：Debugify 2.0 在 yacl **3.6.6** 下正常加载（原注释担心的跨 minor API 漂移未发生）。

### 12.4 四个实测坑（都踩到了）

1. **补丁脚本「报 OK 但没落盘」**：`temp/_patch_modpins2.py` 对 `neoforge-26.1.2/gradle.properties` 与
   `fabric-1.20.1/gradle.properties` 静默未写入（`git diff --stat` 里根本没有这两个文件）。
   ⇒ 改完**必须用 `grep -nE '^key='` 逐项回读**，不得相信脚本自己的 "anchor hits 1"。
   被漏掉的 4 组（neo_version / kubejs_version / accessories×3 / puzzleslib×3 / jei_version）本轮已用 `edit` 补上。
2. **Configuration cache 让旧 loader 继续生效**：改 `neo_version` 后 `compileJava` 只用 14s（`1 up-to-date`），
   `runClient` 仍去扫 `build/moddev/artifacts/minecraft-patched-26.1.2.109.jar` ⇒ 启动报
   `Mod kubejs requires neoforge 26.1.2.110 or above`。修法：`./gradlew :neoforge-26.1.2:compileJava
   --refresh-dependencies --no-configuration-cache`（3m45s，之后 `artifacts/` 里出现 `…-26.1.2.126*.jar`）。
3. **AutoJS API 形态**：`AutoJS.level` 是方法（`AutoJS.level()`）、`AutoJS.players()` 返回数组、
   `AutoJS.findEntities(...)` 返回的是**字符串**（不是实体对象）；取实体用 `AutoJS.level().getEntity(id)`。
   服务端/客户端读控制器走 `e.getAnimatableInstanceCache().getManagerForId(id).getAnimationControllers()`（v4 API）。
4. **`Java.loadClass('int')` 不合法**（Class could not be found）——反射取原语参数时别这么写。

### 12.5 验收读数（2026-11-03 20:37–20:55，四线各一次实机）

| 线 | 读数（`ModList` / `FabricLoader` 实读） |
|---|---|
| 1.21.1 | neoforge **21.1.265**、kubejs **2101.7.2-build.381**、rhino 2101.2.8-build.91、jei **19.70.0.469**、mezz_config 0.6.10、collective 8.75、superflat 3.7、immediatelyfast 1.6.15 |
| 26.1.2 | neoforge **26.1.2.126**、kubejs **26.1.2-8.0.8**、jei **29.53.0.115**、mezz_config 0.6.10、jade 26.1.12、collective 8.75、superflat 3.7、modernfix 5.27.25、BAT build.11、sodium **0.9.2（保持）** |
| 1.20.1 forge | forge 47.4.10、jei **15.74.0.234**、mezz_config 0.6.10、collective 8.75、superflat 3.7、modernfix **5.27.91**、kubejs build.26、rhino 2001.2.3-build.10 |
| fabric-1.20.1 | jei **15.74.0.234**、collective 8.75、yacl **3.6.6**、accessories **beta.49**、puzzleslib **8.1.36**、indium 1.0.36、sodium 0.5.13、iris 1.7.6、debugify 2.0、modmenu 7.2.2 |

功能面同样复验（GeckoLib vendor 未回退）：
- 26.1.2：summon `astral_dice:sykestentacle` + 攻击 ⇒ 截图 `run/26.1.2/screenshots/2026-11-03_20.48.47.png`（近处攻击姿态、远处 idle）。
- 1.20.1 forge：client `action = TRANSITIONING | trig=true`；截图 `run/1.20.1/screenshots/2026-11-03_20.50.51.png`。
- fabric-1.20.1：client `idle RUNNING animation.tentacleidle` / `action RUNNING animation.tentacleattack trig=true`；
  截图 `fabric-1.20.1/run/client/screenshots/2026-11-03_20.54.27.png`。
- 四线 `python scripts/test/mt_preflight.py --version <线>` ⇒ `MT_PREFLIGHT: OK — 9 项全部满足`。

### 12.6 未做 / 待裁决

- `starengine` 四平台仍是 `26.4-SNAPSHOT.1`（本轮未动库）。
- 本轮所有改动（四线 pin、AutoJS 工具与 vendor jar、`tools/loom_embedded_jars.py` 投放物）**未 commit / 未 push**。
- fabric-1.21.1 线按用户指示继续搁置。

## 13 options.txt 的两个致命缺省（2026-11-05，fabric-1.21.1 首启踩出，五线通用）

测试台会给 `run/<线>/options.txt` 做三件事：强制窗口、禁失焦暂停、把潜行/冲刺绑定改回期望值。三个写入函数都在「文件不存在或只有几行」时会**新建一个空壳文件**（`_read_lines(path) or []` 之后追加），于是踩出两个坑：

| 坑 | 现象 | 机制 | 落盘修法 |
|---|---|---|---|
| **缺 `onboardAccessibility:false`** | 客户端卡在「辅助功能引导」页（`AccessibilityOnboardingScreen`），`--quickPlaySingleplayer` **静默不执行**；`LAUNCH: WORLD_READY` 照样打绿（marker `Sound engine started` 是启动期日志），AutoJS 读 `player=false / dim=none` | 1.20.2+ 首次启动引导页由 `onboardAccessibility` 控制，缺省即「未完成引导」 | `scripts/test/mt_env.py` 的 `set_fullscreen_disabled()` 现在会一并补写 `onboardAccessibility:false` |
| **NeoForge 修饰键绑定格式** | `Failed to load options` / `NumberFormatException: For input string: "o:CONTROL"`（栈 `Options.processOptions → InputConstants.getKey`），整份 options 被放弃加载（窗口/暂停/绑定三项设置同时失效） | neo 线把带修饰键的绑定写成 `key_key.jei.toggleOverlay:key.keyboard.o:CONTROL`；原版/fabric 解析第二个冒号后段时做 `Integer.parseInt` | `set_fullscreen_disabled()` 现在会丢弃「`key_` 开头且含 ≥2 个冒号」的行（游戏内以默认绑定重建）；跨线复制 options.txt 时必须先过滤 |

> 判据（写用例/人工核查都用这一条）：**进世界的证据只能来自 AutoJS 读 `Minecraft.getInstance().player != null && level != null`**，不能只看 `LAUNCH: WORLD_READY`。相关验收读数见 `docs/features/fabric-1211-live-test-plan.md` §10。

