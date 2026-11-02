# dev-next 测试台 bash 化：裁决记录与移植契约

> 状态：**执行中**（2026-11-02，DSH）。本文件是 `docs/features/devnext-bash-testbed-gap.md`（缺口盘点）的续篇，只记「已定的事 / 契约数字 / 谁在做什么」，不重复盘点。
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
