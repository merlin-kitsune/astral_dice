#Requires -Version 7.0
<#
.SYNOPSIS
    mt.ps1 — 模组测试唯一入口（pwsh 工具链）。

.DESCRIPTION
    测试顺序（硬性）:
      前置检查 → 1.21.1 全流程 → 判定通过后才执行 → 1.20.1 全流程 → 总览
      三个版本各自给出独立的通过/失败结论；1.21.1 不通过时，其后的版本按门控不再执行。

    `--version <V>`:
      全流程分支**同样尊重该参数** —— 指定单版本时只跑该版本，且**不做跨版本门控**
      （门控的前提是「多版本顺序执行」）。不带 `--version` 时才是「三版本顺序 + 门控」。

    阶段: preflight | build | env | launch | cases | report | stop

.EXAMPLE
    pwsh -File scripts/test/mt.ps1                                  # 全流程（三版本顺序，带门控）
    pwsh -File scripts/test/mt.ps1 --version 1.21.1                 # 全流程（只跑 1.21.1，无门控）
    pwsh -File scripts/test/mt.ps1 --version 1.20.1                 # 全流程（只跑 1.20.1，无门控）
    pwsh -File scripts/test/mt.ps1 --version 1.21.1 --phase build   # 单阶段
    pwsh -File scripts/test/mt.ps1 --version 1.21.1 --case cases/X.json
    pwsh -File scripts/test/mt.ps1 --version 1.21.1 --new ember_chip
    pwsh -File scripts/test/mt.ps1 --phase stop
    pwsh -File scripts/test/mt.ps1 --version 1.21.1 --phase stop --purge-saves   # 收尾：收停 + 清旧存档
    pwsh -File scripts/test/mt.ps1 --version fabric-1.20.1 --side server --phase launch  # fabric 双 run 目录

.NOTES
    迁移前源文件 scripts/test/mt.sh（该原件已在 92fbeaf「工具链收敛为纯 pwsh」删除，取回：`git show 92fbeaf^:scripts/test/mt.sh`）。

    ── 本文件的计算契约（pwsh 7 重写后不变）────────────────────────────────
    退出码（`lib/Mt.Phase.psm1` 导出）:
      PASS=0 / FAIL=1 / ERROR=2 / PREFLIGHT=10 / BLOCKED=11 / TIMEOUT=12
    机器行（外部脚本与报告读这些行，逐字节保持）:
      MT_INFO: / MT_WARN: / MT_ERROR: / MT_PHASE: / MT_<NAME>: OK|FAIL|BLOCKED|ERROR
      MT_AUTO_DISABLED: / MT_RUN: / MT_VERSION_VERDICT: / MT_LAUNCH: / MT_CLEANUP_RESULT:
      LAUNCH_BUDGET: / CASES_BUDGET: / RUN_TIMEOUT: / DETACHED: / WAIT: / CHILD:
    阶段集合: preflight | build | env | launch | cases | report | stop（+ 内部 summary / cleanup / timeout-stop）

    退出清理（避免进程泄漏）:
      多版本全流程（不带 --version）会在退出前自动收停全部本流程进程与 Gradle 守护
      （正常结束 / 失败 / Ctrl-C 三条路径都覆盖），实现见 mt_cleanup.ps1。
      `--version <V>` 单线全流程只收停**本线**并**保留** Gradle 守护（守护不归属单版本，
      停掉会破坏其它线的构建热态）。单阶段模式**默认不清理**，因为
      launch → cases 是分步进行的，需要让客户端在两步之间保持运行；如需强制，
      加 --cleanup-on-exit，或用 --no-cleanup-on-exit 关闭全流程的自动清理。
      若条目失败且 on_fail=keep_game_running，会落按线隔离的 `.mt_keep_alive-<版本>` 标记
      （兼容旧的无后缀 `.mt_keep_alive`，它对所有线生效），
      此时自动清理只提示不杀进程，保留现场供取证（取证完用 --phase stop --force）。

    **测试任务收尾（2026-09-17 用户规则，必须遵守）**：测试任务完成后要**关闭测试端并清理
    旧存档数据**，不让游戏进程与旧世界长期驻留 ——
      · 全流程：退出清理**已自动**做（`mt_cleanup.ps1 run --quiet --purge-saves`）；
      · 分步执行（`--phase launch` / `--phase cases` 等）：最后一次读数后**必须**执行
        `pwsh -File scripts/test/mt.ps1 --version <V> --phase stop --purge-saves`；
      · `--phase stop` 默认**不**清存档（重登类用例 CHIP-RELOG 要跨 stop 保留存档），
        只有显式 `--purge-saves` 才清；`.mt_keep_alive-<版本>`（或兼容的无后缀旧标记）
        存在时两者都只提示、保留现场。

    子进程启动方式（B6 ⑥ 修订）: **一律不用 `Start-Process -Wait`**。它等的是**整棵进程
    树**（本机最小复现：子进程 0.4s 退出、父进程 12.4s 才返回 = 孙进程 ping 的时长），而
    `mt_build` 会让 `gradlew` 新起 Gradle 守护、`mt_launch` 会留下游戏客户端 ⇒ 用 `-Wait`
    必然永久阻塞（`launch` 那条已在本文件 A1 记录；`build` 那条在 B6 ⑥ 实测定位）。
    现在非脱离式子进程统一「`-PassThru` 轮询 `HasExited`」+ `$TimeoutSec` 上限，且输出
    仍是**直接继承父控制台句柄**（不经 PowerShell 的解码/再编码），与旧行为一致。

    **A1 例外（2026-09-15 B2）**：`launch` 阶段改用**脱离式**启动（`-WindowStyle Hidden`
    + 输出落文件句柄 + 按 `MT_LAUNCH: <终态>` 标记轮询），因为 launch 会把 Minecraft
    客户端作为后代留下，必须让启动方**立即返回**（详见 docs/batch3/B1-in-game-results.md ①）。

    超时与「不允许长时间等待」（B6 ⑥；2026-09-16 用户裁决后**收紧为严格预算**）:
      · **每一类子进程都有硬上限**：preflight 120s / build 300s / env 240s /
        launch **联动预算**（锁等待 + 就绪预算 + 余量，默认 600+180+120=900s）/
        report 90s / stop 150s / cleanup 180s（表见 `$script:PhaseBudgets`）；
        cases 阶段**自适应**：单条 = 单条硬上限 + 90s，全目录 = 90s × 用例数 + 90s
        （固定值要么误杀 17 条全量跑、要么等于不限，故按用例数推导）。
        单阶段可用 `MT_<阶段>_TIMEOUT_SEC`（如 `MT_ENV_TIMEOUT_SEC=600`）覆写。
      · 等待期间每 10s 一行 `MT_WAIT:` 心跳（已等待 / 硬上限 / 子进程 CPU 或日志字节数）；
        到点即终止**我们自己那个**子进程并打印卡点，绝不 taskkill 进程树。
      · launch 走脱离式，除硬上限外还有「日志 90s 零增长 ⇒ `CHILD: STALL` 放弃等待」——
        进程存活 ≠ 有进展（实测：游戏空闲时仍有每月一分钟一条的 ModernFix DEBUG 噪声，
        旧监视器就是被它骗过，见 TESTING-SPEC §12）。
      · 单条用例硬超时 `--case-timeout` / `MT_CASE_TIMEOUT_SEC`（默认 600 s，见 mt_case.ps1）
        ⇒ 该条记 **TIMEOUT** 并继续跑下一条；
      · 全局 `--run-timeout <秒>`（**默认 2700s**；旧默认「0 = 不限」已废除 —— 那正是
        「7 分 45 秒静默空转、人只能干等」能发生的前提）⇒ 超时走 `--phase stop --force`
        收停、报告写 TIMEOUT、退出码 12（与 FAIL=1 区分）；
      · 汇总折码（t34，规则 14 收严版）：`mt_report summary` 的「无效运行（MT_USER_TERMINATED）」
        中断码 `MT_EXIT_ERROR`(2) **原样传出**，不再折成 `MT_EXIT_FAIL`(1) ⇒ 端到端退出码可区分
        「无效运行(2) / 产品断言失败(1) / 超时(12) / 前置不足(11) / 全通过(0)」；
      · 长流程包裹用 `mt_watchdog.ps1`（独立脚本，见 TESTING-SPEC §12；判据已从「日志
        mtime」改为**语义标记 + 进度信标**）；阶段/用例/步骤级进度写在
        `cases/.mt_progress.json`，监视器与事后取证共读这一个文件即可定位卡点。

    ── 2026-10-31 pwsh 7 重写（windows-channel-decoupling-spec §2）──────────
    行为契约零变化，只换实现形态：
      · `[CmdletBinding()]` + `[Parameter(ValueFromRemainingArguments)]$ArgList` + 查表
        `$script:ArgSpec` 取代散落的 `$args[$i]`；未知参数 / 缺值 / 非法的**文案与退出码
        逐字节不变**。
      · `Set-StrictMode -Version Latest`、类型化参数、`-LiteralPath`、`Join-Path`、
        结构化 try/catch；输出仍只走 `Write-MtLine` / `Write-MtOk` / `Write-MtWarn` /
        `Write-MtErrLine` 等模块辅助函数，**不使用 Write-Host**。
      · **零 Win32 耦合**：本文件不导入任何 Win32 辅助模块、不动态编译 C# 类型；
        导入链只有 `Mt.Phase` / `Mt.Paths` / `Mt.Proc`（spec §4 的判据）。
      · 新增 `--channel autojs|native`（默认 `autojs`，环境变量 `MT_CHANNEL` 兜底），
        透传给 `mt_preflight.ps1` / `mt_launch.ps1` / `mt_case.ps1`（子进程参数名同为
        `--channel`）。见 `$script:ChannelArgs` 的透传规则注释。

    文案偏差：前置失败提示里的 `mt.sh --phase stop` 改为 `mt.ps1`（同一入口的新名字）。

    沿用原件既有的 2026-09-12 种子包修复（非本次引入）：env 阶段的 `--seed` 按版本各查
    `testworld-seed-<版本>.zip`，不是多版本共用同一个开关；见 Invoke-MtRunPhase 内注释。
#>

[CmdletBinding()]
param(
    # 全部命令行开关都按字符串原样收下，交给下面的查表解析器处理（保持 `--x` 与 `-x` 两种写法都能认）。
    [Parameter(ValueFromRemainingArguments = $true)]
    [AllowEmptyCollection()]
    [string[]]$ArgList = @()
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# ══ 模块导入（唯一导入链：Mt.Phase / Mt.Paths / Mt.Proc）══════════════════
# 零 Win32 耦合：本文件不导入 Win32 辅助模块、不动态编译 C# 类型（通道解耦验收判据）。
$script:LibDir = Join-Path $PSScriptRoot 'lib'
Import-Module (Join-Path $script:LibDir 'Mt.Phase.psm1')
Import-Module (Join-Path $script:LibDir 'Mt.Paths.psm1')
Import-Module (Join-Path $script:LibDir 'Mt.Proc.psm1')

Initialize-MtConsole

$script:TestDir = $PSScriptRoot
$script:PsExe = (Get-Process -Id $PID).Path
$script:MtDoCleanup = 0
$script:MtCleanupDone = 0

# ══ 严格等待预算（2026-09-16 用户裁决：**不允许长时间等待**）══════════════════
# 背景（实测事故）：一次「launch OK 之后 cases 阶段零输出空转 7 分 45 秒」的运行里，
# 子进程预算是 600/900 秒、全局预算是「0 = 不限」，且**等待期间一行输出都没有** ⇒
# 人只能干等，事后也无法从日志判定卡在哪一步。
# 现在的规则：
#   ① 每一类子进程都有**硬上限**（下表），到点即终止并打印卡点诊断；
#   ② 等待期间每 $script:HeartbeatSeconds 秒打一行 `MT_WAIT:` 心跳（含已等待/上限/子进程 CPU）；
#   ③ launch 走脱离式，额外用「日志是否还在增长」判 STALL（`$script:LaunchNoProgressSec`）；
#   ④ 每次进入阶段/用例/步骤都写进度信标 `cases/.mt_progress.json`（监视器与事后取证共读）。
# 覆写：环境变量 `MT_<阶段>_TIMEOUT_SEC`（如 `MT_CASES_TIMEOUT_SEC=600`），或 `--run-timeout`（全局）。
$script:PhaseBudgets = [ordered]@{
    preflight = 120
    gen       = 120
    build     = 300
    env       = 240
    # launch 的实际预算 = 锁等待 + 就绪预算 + 余量（见 Get-MtLaunchBudget）；
    # 这里的 180 只是「就绪预算基线」，不再直接作为整个 launch 阶段的硬上限。
    launch    = 180
    cases     = 300
    report    = 90
    stop      = 150
    cleanup   = 180
}
$script:ChildTimeoutSec = 300       # 未列名阶段的兜底上限
$script:HeartbeatSeconds = 10       # 心跳间隔
$script:LaunchNoProgressSec = 90    # launch 脱离式：日志 90s 零增长即判 STALL（放弃等待，不杀进程）

# ⚠️ 逐字节保留旧实现的「非负整数」正则：单引号里的 `\\d` 在 .NET 正则中表示
#    「字面反斜杠 + 字母 d」，因此 `'600' -match '^\\d+$'` 恒为 False（实测）。
#    后果（**现状即如此，本批不改**）：`MT_<阶段>_TIMEOUT_SEC` / `MT_RUN_TIMEOUT_SEC` /
#    `MT_CASE_TIMEOUT_SEC` 三处环境变量覆写与 `--run-timeout` / `--case-timeout` 两个命令行
#    开关当前都不生效（命令行开关会直接报「需要非负整数（秒）」并 exit 2）。
#    本批契约是「行为零变化」，故不顺手修正；要修只需把 `\\d` 改成 `\d`（一处常量即可全生效）。
$script:MtNonNegativeIntPattern = '^\\d+$'

function Get-MtPhaseBudget {
    <#
    .SYNOPSIS
        取某阶段的硬超时（秒）；`MT_<阶段>_TIMEOUT_SEC` 可覆写。

    .NOTES
        2026-10-17 返工（预算冲突）：launch 不再用固定 180s。并行启动时 mt_launch 可能先在
        全机启动锁上排队（默认最多 600s），之后才进入 180s 就绪等待 —— 固定 180s 会让
        mt.ps1 在**正常排队**期间误报 `CHILD: TIMEOUT`。现在默认值 = 锁等待 + 就绪 + 余量，
        三个分量与 mt_launch 读同一来源（Mt.Proc 的 Get-MtLaunchBudget）。
        `MT_LAUNCH_TIMEOUT_SEC` 仍可显式覆写（运维要缩短时限时的逃生口）。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][AllowEmptyString()][string]$PhaseName)

    [int]$budget = $script:ChildTimeoutSec
    if ($PhaseName -and $script:PhaseBudgets.Contains($PhaseName)) {
        $budget = [int]$script:PhaseBudgets[$PhaseName]
    }
    if ($PhaseName -eq 'launch') {
        $budget = [int](Get-MtLaunchBudget).TotalSec
    }
    if ($PhaseName) {
        $envName = 'MT_' + (($PhaseName -replace '[^A-Za-z0-9]', '_').ToUpperInvariant()) + '_TIMEOUT_SEC'
        $raw = [string][Environment]::GetEnvironmentVariable($envName)
        if ($raw -and $raw -match $script:MtNonNegativeIntPattern) { $budget = [int]$raw }
    }
    return $budget
}

function Invoke-MtChild {
    <#
    .SYNOPSIS
        启动一个 mt_*.ps1 子进程并返回其退出码（输出直通，不经 PS 编码层）。

    .PARAMETER Detached
        **脱离式**执行（A1，2026-09-15 B2）：`-WindowStyle Hidden` + stdout/stderr 落
        **文件句柄**，本进程立即返回，随后按日志里的终态标记轮询。

    .NOTES
        为什么 launch 必须脱离（B1 实测根因，docs/batch3/B1-in-game-results.md ①）：
        PowerShell 的 `Start-Process -Wait` 等的是**整棵进程树**，而 mt_launch 以
        `-NoNewWindow` 起 `cmd → gradlew → 客户端`，客户端是同一控制台的后代 ⇒
        `-Wait` 会一直阻塞到**客户端退出**才返回（本机最小复现：子进程 0.4s 退出、
        父进程 12.4s 才返回 = 孙进程 ping 的时长）。后果是 launch 阶段 PASS 被记在
        客户端死后，cases 阶段拿不到活客户端 ⇒ 上一轮「全流程 cases 全线 ERROR」。
        实测同一坑也让「分步 --phase launch」不可能留下活客户端。

        做法复刻已验证的 `scripts/devtools/Start-MtDetached.ps1`（`-WindowStyle Hidden`
        + 重定向落文件 + 调用方按标记轮询），只是把它内联进 mt.ps1，避免测试工具链
        依赖同级 devtools 目录。其余阶段保持「只等自己那个子进程」的语义不变。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Script,
        [string[]]$ScriptArgs = @(),
        [switch]$Detached,
        [string]$PhaseMarker = '',
        [int]$TimeoutSec = 300,
        [int]$NoProgressSec = 0
    )

    [string[]]$argv = @('-NoProfile', '-File', (Join-Path $script:TestDir $Script)) + $ScriptArgs

    if (-not $Detached) {
        # ⚠️ **禁止**用 `Start-Process … -Wait`（2026-09-15 B6 ⑥ 实测根因，三位执行者卡死于此）：
        #    `-Wait` 等的是**整棵进程树**，而 `mt_build.ps1` 在需要时会让 `gradlew` **新起一个
        #    Gradle 守护**（`gradlew` wrapper 的后代）⇒ 构建早已打印 `MT_BUILD: OK` 并退出，
        #    父进程却一直阻塞到守护退出（实测：`--phase stop --force` 杀掉守护之后，下一次
        #    全流程**必然**卡在 build 阶段 —— 最后一行输出停在 `MT_BUILD: OK`，找不到任何
        #    mt_env/mt_report 子进程，而 Gradle 守护仍活着 ⇒ 永不返回）。
        #    这里改为**只等我们自己那个子进程**（轮询 `HasExited`，不跟踪后代），并加
        #    `$TimeoutSec` 上限；超时只终止该子进程自身（**绝不** taskkill 进程树，
        #    以免误伤 Minecraft 客户端或用户其它 java 程序）。
        # 注意：Start-Process 不解析数组引号，含空格的参数必须由 ConvertTo-MtStartArgs 补引号。
        $proc = Start-Process -FilePath $script:PsExe `
            -ArgumentList (ConvertTo-MtStartArgs -ArgumentList $argv) `
            -NoNewWindow -PassThru
        $sw = [System.Diagnostics.Stopwatch]::StartNew()
        [double]$lastBeat = 0.0
        while ((-not $proc.HasExited) -and ($sw.Elapsed.TotalSeconds -lt $TimeoutSec)) {
            Start-Sleep -Milliseconds 500
            if (($sw.Elapsed.TotalSeconds - $lastBeat) -ge $script:HeartbeatSeconds) {
                $lastBeat = $sw.Elapsed.TotalSeconds
                [double]$cpu = 0.0
                try { $cpu = $proc.TotalProcessorTime.TotalSeconds } catch { }
                Write-MtInfo ("WAIT: {0} 已等待 {1:N0}s / 硬上限 {2}s（子进程 CPU {3:N1}s，pid={4}）" -f `
                        $Script, $sw.Elapsed.TotalSeconds, $TimeoutSec, $cpu, $proc.Id)
            }
        }
        if (-not $proc.HasExited) {
            # 到点即止：不再等（用户的硬性要求）。只终止**我们自己那个**子进程，绝不动进程树。
            Write-MtErrLine ("CHILD: TIMEOUT — {0} 超过硬上限 {1}s 未退出（pid={2}）；只终止该子进程，不动进程树" -f `
                    $Script, $TimeoutSec, $proc.Id)
            try { Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue } catch { }
            $prog = Get-MtProgress
            if ($prog) {
                Write-MtErrLine ("CHILD: 卡点 — 阶段={0} 版本={1} 用例={2} 步骤={3}/{4} op={5}" -f `
                        $prog['phase'], $prog['version'], $prog['case'], $prog['step_index'], $prog['step_total'], $prog['op'])
            }
            return $MT_EXIT_TIMEOUT
        }
        return $proc.ExitCode
    }

    # ── 脱离式（launch 专用）────────────────────────────────────────────
    $logDir = Join-Path (Join-Path (Get-MtRoot) 'temp') 'mt_detached'
    if (-not (Test-Path -LiteralPath $logDir)) { [void](New-Item -ItemType Directory -Force -Path $logDir) }
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $outLog = Join-Path $logDir ("{0}_{1}.log" -f ([System.IO.Path]::GetFileNameWithoutExtension($Script)), $stamp)
    $errLog = "$outLog.err"

    $proc = Start-Process -FilePath $script:PsExe `
        -ArgumentList (ConvertTo-MtStartArgs -ArgumentList $argv) `
        -PassThru -WindowStyle Hidden -WorkingDirectory (Get-MtRoot) `
        -RedirectStandardOutput $outLog -RedirectStandardError $errLog

    Write-MtInfo ("DETACHED: {0} pid={1} log={2}" -f $Script, $proc.Id, $outLog)

    # 终态标记：mt_launch 的三条出口都是 MT_LAUNCH: <OK|FAIL|ERROR|BLOCKED>
    $marker = if ($PhaseMarker) { $PhaseMarker } else { 'MT_LAUNCH: ' }
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    [double]$lastBeat = 0.0
    [long]$lastSize = -1
    [long]$lastGrowth = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    while ($sw.Elapsed.TotalSeconds -lt $TimeoutSec) {
        $text = ''
        try { $text = Read-MtSharedText -Path $outLog } catch { $text = '' }
        $errText = ''
        try { $errText = Read-MtSharedText -Path $errLog } catch { $errText = '' }
        $all = $text + "`n" + $errText

        if ($all.Contains("$marker" + 'OK')) { return $MT_EXIT_PASS }
        if ($all.Contains("$marker" + 'BLOCKED')) { return $MT_EXIT_BLOCKED }
        if ($all.Contains("$marker" + 'FAIL')) { return $MT_EXIT_FAIL }
        if ($all.Contains("$marker" + 'ERROR')) { return $MT_EXIT_ERROR }

        if ($proc.HasExited) {
            # 进程已退出但没打出终态标记 ⇒ 子脚本自身崩了：把日志尾部透传出来再判 ERROR
            foreach ($ln in @($all -split "`n" | Where-Object { $_.Trim() -ne '' } | Select-Object -Last 20)) {
                Write-MtErrLine ([string]$ln)
            }
            return $MT_EXIT_ERROR
        }

        # 「日志是否还在增长」是脱离式子进程唯一可靠的干活证据（进程存活 ≠ 有进展）
        [long]$size = 0
        try { $size = (Get-Item -LiteralPath $outLog -ErrorAction Stop).Length } catch { $size = [long]0 }
        [long]$now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
        if ($size -ne $lastSize) { $lastSize = $size; $lastGrowth = $now }
        [long]$idle = $now - $lastGrowth

        if ($NoProgressSec -gt 0 -and $idle -ge $NoProgressSec) {
            Write-MtErrLine ("CHILD: STALL — {0} 的日志已 {1}s 无增长（无进展阈值 {2}s，硬上限 {3}s）⇒ 放弃等待终态标记" -f `
                    $Script, $idle, $NoProgressSec, $TimeoutSec)
            Write-MtErrLine ("CHILD: STALL log={0}（pid={1} 未终止 —— 由 --phase stop 收停）" -f $outLog, $proc.Id)
            foreach ($ln in @($all -split "`n" | Where-Object { $_.Trim() -ne '' } | Select-Object -Last 10)) {
                Write-MtErrLine ([string]$ln)
            }
            return $MT_EXIT_TIMEOUT
        }

        if (($sw.Elapsed.TotalSeconds - $lastBeat) -ge $script:HeartbeatSeconds) {
            $lastBeat = $sw.Elapsed.TotalSeconds
            Write-MtInfo ("WAIT: {0}（脱离式）已等待 {1:N0}s / 硬上限 {2}s；日志 {3} 字节，最后增长于 {4}s 前" -f `
                    $Script, $sw.Elapsed.TotalSeconds, $TimeoutSec, $size, $idle)
        }
        Start-Sleep -Seconds 3
    }

    Write-MtErrLine ("CHILD: TIMEOUT — 等待 {0} 的终态标记超过硬上限 {1}s（log={2}）；进程未终止，由 --phase stop 收停" -f `
            $Script, $TimeoutSec, $outLog)
    return $MT_EXIT_TIMEOUT
}

function Test-MtRunBudgetExceeded {
    <#
    .SYNOPSIS
        B6 ⑥-2：全局运行预算是否已耗尽（`$Deadline -le 0` = 不限时）。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][long]$Deadline)

    if ($Deadline -le 0) { return $false }
    return ([DateTimeOffset]::UtcNow.ToUnixTimeSeconds() -ge $Deadline)
}

function Invoke-MtRunTimeoutStop {
    <#
    .SYNOPSIS
        B6 ⑥-2：全局超时的**收停 + 记账**。退出码 12（`MT_EXIT_TIMEOUT`），与 FAIL(1) 区分。

    .NOTES
        收停只走 `mt_stop.ps1 --force`（唯一收停实现，按进程标记只杀本流程的客户端与守护），
        **绝不**自己 taskkill 任意 java。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][AllowEmptyString()][string]$Version)

    Write-MtErrLine ''
    Write-MtErrLine 'MT_RUN: TIMEOUT — 已超过 --run-timeout；按纪律执行 --phase stop --force 收停'
    Start-MtPhase 'timeout-stop'
    if ($Version) {
        [void](Invoke-MtChild -Script 'mt_stop.ps1' -ScriptArgs @('--version', $Version, '--force'))
        [void](Invoke-MtChild -Script 'mt_report.ps1' -ScriptArgs @('mark', '--version', $Version, '--phase', 'cases', '--result', 'TIMEOUT'))
        [void](Invoke-MtChild -Script 'mt_report.ps1' -ScriptArgs @('collect', '--version', $Version, '--verdict', 'TIMEOUT'))
    } else {
        [void](Invoke-MtChild -Script 'mt_stop.ps1' -ScriptArgs @('--all', '--force'))
    }
    [void](Invoke-MtChild -Script 'mt_report.ps1' -ScriptArgs @('summary'))
    exit $MT_EXIT_TIMEOUT
}

function Get-MtCleanupDisplayLines {
    <#
    .SYNOPSIS
        从 mt_cleanup 的完整输出里取「给人看的行」（剔除机器可读结论行）。
    #>
    [CmdletBinding()]
    param([AllowEmptyString()][string]$Text)

    $out = @()
    $lines = @($Text -split "`n")
    if ($lines.Count -gt 0 -and $lines[-1] -eq '') { $lines = @($lines[0..($lines.Count - 2)]) }
    foreach ($ln in $lines) {
        $s = $ln.TrimEnd("`r")
        if ($s.StartsWith('MT_CLEANUP_RESULT:')) { continue }
        $out += $s
    }
    return , $out
}

function Invoke-MtAutoCleanup {
    <#
    .SYNOPSIS
        退出清理（唯一实现见 mt_cleanup.ps1）。

    .NOTES
        幂等：靠 $script:MtCleanupDone 保证「正常路径显式调用 + finally 兜底」只真正跑一次。
        D4-2（2026-10-17）：`-Versions` 非空时把收停/清档**限定到本线**（mt_cleanup 的
        `--version` 语义），否则单版本全流程退出会把另一条线正在跑的客户端一起杀掉。
    #>
    [CmdletBinding()]
    param([string[]]$Versions = @())

    if ($script:MtCleanupDone -eq 1) { return 0 }
    $script:MtCleanupDone = 1
    if ($script:MtDoCleanup -ne 1) { return 0 }

    Write-MtLine ''
    Start-MtPhase 'cleanup (auto)'

    [string[]]$cleanupArgv = @('-NoProfile', '-File', (Join-Path $script:TestDir 'mt_cleanup.ps1'), 'run', '--quiet', '--purge-saves')
    foreach ($cv in @($Versions)) {
        if ($cv) { $cleanupArgv += @('--version', [string]$cv) }
    }
    $r = Invoke-MtProcessFull -FilePath $script:PsExe -ArgumentList $cleanupArgv -TimeoutSec (Get-MtPhaseBudget 'cleanup')

    foreach ($ln in (Get-MtCleanupDisplayLines -Text $r.StdOut)) { Write-MtLine $ln }

    if ($r.StdOut.Contains('MT_CLEANUP_RESULT: SKIP')) {
        Write-MtWarn 'CLEANUP: 按失败取证标记保留了游戏现场（未收停）—— 取证完用 --phase stop --force 释放'
        return 0
    }
    if ($r.ExitCode -eq 0) {
        Write-MtOk 'CLEANUP' '退出清理完成，无本流程残留进程'
        return 0
    }
    Write-MtWarn 'CLEANUP: 退出清理后仍有残留，详见上方 mt_cleanup 输出'
    return 1
}

function Invoke-MtRunPhase {
    <#
    .SYNOPSIS
        执行单个阶段（对应 bash 的 run_phase）；返回退出码。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$PhaseVersion,
        [Parameter(Mandatory)][string]$PhaseName,
        [string]$CasePath = ''
    )

    [void](Set-MtProgress -Phase $PhaseName -Version $PhaseVersion)

    if ($PhaseName -eq 'build') {
        return (Invoke-MtChild -Script 'mt_build.ps1' -ScriptArgs @('--version', $PhaseVersion) `
                -TimeoutSec (Get-MtPhaseBudget 'build'))
    }

    if ($PhaseName -eq 'env') {
        $envBudget = Get-MtPhaseBudget 'env'
        $rc = Invoke-MtChild -Script 'mt_env.ps1' -ScriptArgs @('mods', '--version', $PhaseVersion) -TimeoutSec $envBudget
        if ($rc -ne 0) { return $rc }
        # 缺陷修复（2026-09-12）：种子包按**版本**判定。bash 原件硬编码
        # resources/testworld-seed-1.20.1.zip 的存在性并把它作为多版本共用的 --seed 开关，
        # 而 mt_env 的 seed 恢复是按版本找 testworld-seed-<版本>.zip —— 只要 1.20.1 种子包在位，
        # 1.21.1 的 env 阶段必然 MT_WORLD: BLOCKED（exit 11）而全流程在此中断。
        # 现改为各版本各自查自己的种子包：1.20.1 行为不变（有包 → --seed），1.21.1 无包 → 生成世界。
        $seedArgs = @()
        $seedZip = Join-Path (Join-Path $script:TestDir 'resources') "testworld-seed-$PhaseVersion.zip"
        if (Test-Path -LiteralPath $seedZip -PathType Leaf) { $seedArgs = @('--seed') }
        return (Invoke-MtChild -Script 'mt_env.ps1' -ScriptArgs (@('world', '--version', $PhaseVersion) + $seedArgs) `
                -TimeoutSec $envBudget)
    }

    if ($PhaseName -eq 'launch') {
        # 2026-09-16：launch **之前强制**同步探针脚本（安装路径见 mt_env.ps1 的 Sync-MtEnvKubejs）。
        # 为什么放在 launch 而不是只放在 env：单阶段 `--phase launch`（重登类用例的标准跑法）
        # 会跳过 env ⇒ 若模板已更新而 run 目录还是旧探针，所有探针断言都会**静默**失败
        # （游戏侧没有那条命令，断言只会读不到读数）——实测事故形态，必须结构性消除。
        # 客户端在 launch 时冷启动并重新加载 KubeJS，因此此处同步一定生效。
        $krc = Invoke-MtChild -Script 'mt_env.ps1' -ScriptArgs @('kubejs', '--version', $PhaseVersion) -TimeoutSec 60
        if ($krc -ne 0) {
            Write-MtErrLine 'MT_LAUNCH: BLOCKED — 探针脚本同步失败（mt_env.ps1 kubejs）'
            return $MT_EXIT_BLOCKED
        }
        # A1（B2）：launch **必须**脱离执行，否则 -Wait 会等整棵进程树（客户端）
        # 直到它退出才返回 ⇒ 全流程与「分步 --phase launch」都拿不到活客户端。
        # 2026-09-16：再加「日志零增长即 STALL」的判定 —— 进程活着不等于有进展。
        # 2026-10-17 返工（预算冲突）：launch 预算联动「锁等待 + 就绪 + 余量」，
        # 否则并行启动排队（最多 600s）会被固定 180s 误杀成 TIMEOUT。
        $launchBudget = Get-MtLaunchBudget
        $launchBudgetSec = Get-MtPhaseBudget 'launch'
        Write-MtInfo ("LAUNCH_BUDGET: {0}s = 锁等待 {1}s + 就绪 {2}s + 余量 {3}s（实际硬上限 {4}s）" -f `
                $launchBudget.TotalSec, $launchBudget.LockSec, $launchBudget.ReadySec,
                $launchBudget.MarginSec, $launchBudgetSec)
        if ($launchBudgetSec -lt ($launchBudget.LockSec + $launchBudget.ReadySec)) {
            Write-MtWarn ("LAUNCH_BUDGET: MT_LAUNCH_TIMEOUT_SEC={0}s 小于「锁等待+就绪」{1}s —— 排队时可能误报 TIMEOUT" -f `
                    $launchBudgetSec, ($launchBudget.LockSec + $launchBudget.ReadySec))
        }
        return (Invoke-MtChild -Script 'mt_launch.ps1' -ScriptArgs (@('--version', $PhaseVersion) + $script:ChannelArgs) -Detached `
                -TimeoutSec $launchBudgetSec -NoProgressSec $script:LaunchNoProgressSec)
    }

    if ($PhaseName -eq 'cases') {
        # cases 阶段是**预算自适应**的：单条 = 单条硬上限 + 90s 余量；全目录 = 90s × 用例数 + 90s。
        # 为什么不用固定值：固定 300s 会把 17 条用例的正常全量跑**误杀**，而固定 3600s 又等于不限。
        $perCase = if ($script:CaseTimeoutSec -gt 0) { $script:CaseTimeoutSec } else { 600 }
        [string[]]$caseArgs = @('--version', $PhaseVersion, '--case-timeout', "$perCase") + $script:ChannelArgs
        # 2026-10-18 修：父入口此前不转发 --allow-auto，导致再传 --allow-auto / MT_ALLOW_AUTO=1
        # 在 run-dir 模式下仍被 mt_case.ps1 的批量闸门拒绝（“已被禁用”）。此处按脚本级放行标志补传。
        if ($script:Opt.AllowAuto) { $caseArgs += '--allow-auto' }
        if ($CasePath) {
            $budget = $perCase + 90
            Write-MtInfo ("CASES_BUDGET: 单条用例 硬上限 {0}s + 余量 90s = {1}s（每步另有用例剩余预算兜底）" -f $perCase, $budget)
            return (Invoke-MtChild -Script 'mt_case.ps1' -ScriptArgs (@('run', '--case', $CasePath) + $caseArgs) `
                    -TimeoutSec $budget)
        }
        $n = 0
        try {
            $caseDir = Join-Path $script:TestDir 'cases'
            $n = @(Get-ChildItem -LiteralPath $caseDir -File -ErrorAction SilentlyContinue |
                Where-Object { (-not $_.Name.StartsWith('.')) -and ($_.Name -like "*-$PhaseVersion.json") }).Count
        } catch { $n = 0 }
        if ($n -le 0) { $n = 20 }
        # ⚠️ 2026-09-22 修：此前这里**硬编码** `90 + 90*n`，但提示文案却写着「覆写 MT_CASES_TIMEOUT_SEC」
        #    ⇒ 环境变量被无视，整目录跑**必然**在 90*n+90 秒处被截断（实测 11 条 × 90 + 90 = 1080s，
        #    而按用例步数估算需要 ≈ 1700s ⇒ 后半程用例从未被执行，却看不出是「被截断」还是「失败」）。
        #    该公式的前提是「单条用例 ≤ 90s」，而用例规模早已从 ~20 步涨到 105~164 步 ⇒ 前提失效。
        #    现在：显式设了 MT_CASES_TIMEOUT_SEC 就用它；否则按「单条硬上限 × 条数 + 90s 余量」推导，
        #    与 `$script:CaseTimeoutSec` 保持同一口径（不再假设 90s/条）。
        $budget = 90 + ([Math]::Max(90, $perCase) * $n)
        if ($env:MT_CASES_TIMEOUT_SEC) {
            $override = 0
            if ([int]::TryParse($env:MT_CASES_TIMEOUT_SEC, [ref]$override) -and $override -gt 0) { $budget = $override }
        }
        Write-MtInfo ("CASES_BUDGET: {0} 条用例 × 单条上限 {1}s + 90s = {2}s（覆写 MT_CASES_TIMEOUT_SEC）" -f $n, $perCase, $budget)
        return (Invoke-MtChild -Script 'mt_case.ps1' -ScriptArgs (@('run-dir') + $caseArgs) -TimeoutSec $budget)
    }

    if ($PhaseName -eq 'report') {
        return (Invoke-MtChild -Script 'mt_report.ps1' -ScriptArgs @('collect', '--version', $PhaseVersion) `
                -TimeoutSec (Get-MtPhaseBudget 'report'))
    }

    Write-MtErrorLine "未知阶段 $PhaseName"
    return $MT_EXIT_ERROR
}

# ══ 参数默认值与解析状态 ══════════════════════════════════════════════════
# 所有选项统一落在 $script:Opt（[ordered] 便于自证「表里有什么就是支持什么」）。
# 解析循环只写这张表，不散写变量；解析完再一次性绑定到局部变量供分支使用。
$script:Opt = [ordered]@{
    Version        = ''
    Phase          = ''
    CasePath       = ''
    GenNew         = ''
    GenFeature     = ''
    GenSpec        = ''
    Channel        = ''
    # fabric-1.20.1 专属（双 run 目录）：解析后归一成 client|server，并 export 成 MT_SIDE 供
    # Mt.Paths.psm1 的三级解析（显式 -Side > MT_SIDE > 'client'）与各子脚本继承。
    Side           = ''
    # 🚫 批量编排闸门（2026-09-27 用户规则：禁用自动测试，改为纯手动下达命令）
    #   `AllowAuto` = 显式放行（--allow-auto / MT_ALLOW_AUTO=1）；闸门拦四处，见主分支注释。
    AllowAuto      = $false
    CleanupMode    = ''      # '' = 按场景默认；1 = 强制开启；0 = 强制关闭
    StopForce      = $false
    StopKeepDaemon = $false
    # stop 阶段是否顺带清理测试存档（旧存档数据）。默认 **否**：两阶段/重登类用例（CHIP-RELOG）要跨
    # stop 保留存档；测试任务**收尾**时显式加 --purge-saves（全流程退出清理则默认就清，见
    # Invoke-MtAutoCleanup 的 --purge-saves）。
    StopPurgeSaves = $false
    # B6 ⑥-2：全流程全局超时（秒）。**2026-09-16 起默认 2700s（45 分钟）而不是「0 = 不限」**——
    # 「不限」正是那次「launch 之后 cases 空转 7 分 45 秒、人只能干等」能发生的前提。
    # 覆写：环境变量 MT_RUN_TIMEOUT_SEC 或 --run-timeout <秒>（两者当前均受上述正则怪癖影响，见该常量注释）。
    RunTimeoutSec  = 2700
    # 单条用例硬超时（透传给 mt_case 的 --case-timeout；0 = 用 mt_case 自己的默认 600s）
    CaseTimeoutSec = 0
}

if ($env:MT_ALLOW_AUTO -and $env:MT_ALLOW_AUTO -match '^(1|true|yes|on)$') { $script:Opt.AllowAuto = $true }
if ($env:MT_RUN_TIMEOUT_SEC -and $env:MT_RUN_TIMEOUT_SEC -match $script:MtNonNegativeIntPattern) {
    $script:Opt.RunTimeoutSec = [int]$env:MT_RUN_TIMEOUT_SEC
}
if ($env:MT_CASE_TIMEOUT_SEC -and $env:MT_CASE_TIMEOUT_SEC -match $script:MtNonNegativeIntPattern) {
    $script:Opt.CaseTimeoutSec = [int]$env:MT_CASE_TIMEOUT_SEC
}

# ══ 参数查表（--开关 → 解析行为）══════════════════════════════════════════
# Kind:  Value  = 需要一个值（缺少 → Missing 文案 + 退出码 2；Pattern 不匹配 → Invalid 文案 + 退出码 2）
#        Switch = 无值开关，把 Key 置为 SetValue
#        Help   = 打印用法块后退出 0
# Key/SetValue 指向 $script:Opt 的键；键名只此一处，解析循环里不再出现开关名。
$script:ArgSpec = [ordered]@{
    # ── 取值类 ───────────────────────────────────────────────────────────
    'version'            = [pscustomobject]@{ Kind = 'Value'; Key = 'Version'; Missing = '缺少 --version 的值' }
    'phase'              = [pscustomobject]@{ Kind = 'Value'; Key = 'Phase'; Missing = '缺少 --phase 的值' }
    'case'               = [pscustomobject]@{ Kind = 'Value'; Key = 'CasePath'; Missing = '缺少 --case 的值' }
    'new'                = [pscustomobject]@{ Kind = 'Value'; Key = 'GenNew'; Missing = '缺少 --new 的值' }
    'feature'            = [pscustomobject]@{ Kind = 'Value'; Key = 'GenFeature'; Missing = '缺少 --feature 的值' }
    'spec'               = [pscustomobject]@{ Kind = 'Value'; Key = 'GenSpec'; Missing = '缺少 --spec 的值' }
    'channel'            = [pscustomobject]@{ Kind = 'Value'; Key = 'Channel'; Missing = '缺少 --channel 的值' }
    'side'               = [pscustomobject]@{ Kind = 'Value'; Key = 'Side'; Missing = '缺少 --side 的值' }
    'run-timeout'        = [pscustomobject]@{
        Kind = 'Value'; Key = 'RunTimeoutSec'; Missing = '缺少 --run-timeout 的值'
        Pattern = $script:MtNonNegativeIntPattern; Invalid = '--run-timeout 需要非负整数（秒）'
    }
    'case-timeout'       = [pscustomobject]@{
        Kind = 'Value'; Key = 'CaseTimeoutSec'; Missing = '缺少 --case-timeout 的值'
        Pattern = $script:MtNonNegativeIntPattern; Invalid = '--case-timeout 需要非负整数（秒）'
    }
    # ── 开关类 ───────────────────────────────────────────────────────────
    'cleanup-on-exit'    = [pscustomobject]@{ Kind = 'Switch'; Key = 'CleanupMode'; SetValue = '1' }
    'no-cleanup-on-exit' = [pscustomobject]@{ Kind = 'Switch'; Key = 'CleanupMode'; SetValue = '0' }
    'force'              = [pscustomobject]@{ Kind = 'Switch'; Key = 'StopForce'; SetValue = $true }
    'keep-daemon'        = [pscustomobject]@{ Kind = 'Switch'; Key = 'StopKeepDaemon'; SetValue = $true }
    'purge-saves'        = [pscustomobject]@{ Kind = 'Switch'; Key = 'StopPurgeSaves'; SetValue = $true }
    'keep-saves'         = [pscustomobject]@{ Kind = 'Switch'; Key = 'StopPurgeSaves'; SetValue = $false }
    'allow-auto'         = [pscustomobject]@{ Kind = 'Switch'; Key = 'AllowAuto'; SetValue = $true }
    # ── 帮助（-h / --help；与 bash 版 sed -n '2,25p' 等价）──────────────
    'h'                  = [pscustomobject]@{ Kind = 'Help' }
    'help'               = [pscustomobject]@{ Kind = 'Help' }
}

# ══ 参数解析（表驱动）══════════════════════════════════════════════════════
$i = 0
while ($i -lt $ArgList.Count) {
    $tok = [string]$ArgList[$i]
    # 与旧实现同一归一化：去掉全部前导 `-` 再小写 ⇒ `--version` / `-Version` / `--version` 同一键。
    $key = $tok.TrimStart('-').ToLowerInvariant()
    if (-not $script:ArgSpec.Contains($key)) {
        Write-MtErrorLine "未知参数 $tok"; exit $MT_EXIT_ERROR
    }
    $spec = $script:ArgSpec[$key]
    switch ([string]$spec.Kind) {
        'Help' {
            # 与旧实现一致：遇到 -h/--help **立刻**打印用法并退出 0（其后的参数不再解析）。
            # 窗口 = 文件第 2..26 行（原 `sed -n '2,23p'` 于 2026-09-17 放宽到 24 行，正好到 .EXAMPLE 结束；
            # 2026-11-01 因新增一条 fabric --side 示例再 +1 行，仍以 .EXAMPLE 末行为界）。
            foreach ($ln in @(Get-Content -LiteralPath $PSCommandPath | Select-Object -Skip 1 -First 25)) {
                Write-MtLine ([string]$ln)
            }
            exit $MT_EXIT_PASS
        }
        'Switch' {
            $script:Opt[$spec.Key] = $spec.SetValue
        }
        'Value' {
            if (($i + 1) -ge $ArgList.Count) {
                Write-MtErrorLine $spec.Missing; exit $MT_EXIT_ERROR
            }
            $raw = [string]$ArgList[$i + 1]
            if ((@($spec.PSObject.Properties.Name) -contains 'Pattern') -and ($raw -notmatch $spec.Pattern)) {
                Write-MtErrorLine $spec.Invalid; exit $MT_EXIT_ERROR
            }
            $script:Opt[$spec.Key] = $raw
            $i++
        }
        default {
            Write-MtErrorLine "未知参数 $tok"; exit $MT_EXIT_ERROR
        }
    }
    $i++
}

# ── 绑定到局部变量（解析完成；后续分支只读局部变量）──────────────────────
$Version = [string]$script:Opt.Version
$Phase = [string]$script:Opt.Phase
$CasePath = [string]$script:Opt.CasePath
$GenNew = [string]$script:Opt.GenNew
$GenFeature = [string]$script:Opt.GenFeature
$GenSpec = [string]$script:Opt.GenSpec
$AllowAuto = [bool]$script:Opt.AllowAuto
$CleanupMode = [string]$script:Opt.CleanupMode
$StopForce = [bool]$script:Opt.StopForce
$StopKeepDaemon = [bool]$script:Opt.StopKeepDaemon
$StopPurgeSaves = [bool]$script:Opt.StopPurgeSaves
$RunTimeoutSec = [int]$script:Opt.RunTimeoutSec
$script:CaseTimeoutSec = [int]$script:Opt.CaseTimeoutSec

# ══ 通道解耦（windows-channel-decoupling-spec §2）══════════════════════════
# 解析顺序：显式 --channel > 环境变量 MT_CHANNEL > 默认 autojs（与 lib/Mt.Channel.psm1 的
# Get-MtChannelMode 同一口径；本文件不导入该模块 —— 通道层零耦合只允许 Phase/Paths/Proc，
# 且此处只做「取值校验 + 透传」，不需要任何通道实现）。
$Channel = [string]$script:Opt.Channel
$channelRaw = $Channel
if (-not $channelRaw) { $channelRaw = [string]$env:MT_CHANNEL }
if (-not $channelRaw) {
    $Channel = 'autojs'
} else {
    $Channel = $channelRaw.Trim().ToLowerInvariant()
    if ($Channel -ne 'autojs' -and $Channel -ne 'native') {
        Write-MtErrorLine ("--channel 需要 autojs 或 native（收到：{0}）" -f $channelRaw)
        exit $MT_EXIT_ERROR
    }
}

# 透传给 mt_preflight / mt_launch / mt_case 的参数片段（子进程参数名同为 --channel）。
# ⚠️ 默认值 autojs 与三个子脚本各自的默认值**完全相同** ⇒ 省略参数即等价，
#    因此在子脚本尚未实现 --channel 的过渡期，默认路径不会被"未知参数"打挂；
#    native 走后端不同，必须显式透传（子脚本必须支持 --channel，见 spec §3）。
$script:ChannelArgs = [string[]]@()
if ($Channel -ne 'autojs') {
    $script:ChannelArgs = @('--channel', $Channel)
    Write-MtInfo ("CHANNEL: mode={0}（透传给 mt_preflight / mt_launch / mt_case）" -f $Channel)
}

# ══ fabric-1.20.1 的 side 选择（2026-11-01 登记）════════════════════════════
# 语义与 bash 编排层逐条一致（scripts/test/win/mt.sh:136-158）：
#   ① 只有 fabric-1.20.1 有**双 run 目录**（<子项目>/run/client 与 /run/server），
#      另三线是单目录 run/<版本> ⇒ 对它们传 --side 一律报错（避免「以为切了侧」的静默偏差）；
#   ② 取值只接受 client / server（大小写不敏感，归一成小写后 export）；
#   ③ 不传 ⇒ 不设 MT_SIDE ⇒ Mt.Paths.Resolve-MtSide 落到默认 'client'（与 bash 的
#      MT_FAB_DEFAULT_SIDE 一致）。
# 为什么用环境变量而不改 $script:ChannelArgs：side 由 Mt.Paths.psm1 在**每个进程内**统一解析
# （全仓 Get-MtPaths 的 40 处调用点都只传 -Version），环境变量能让父进程自己的 Get-MtPaths 与
# 全部子进程（mt_preflight / mt_launch / mt_case / mt_assert …）看到同一个 side，且不新增参数面。
$Side = ([string]$script:Opt.Side).Trim().ToLowerInvariant()
if ($Side) {
    if ($Version -ne 'fabric-1.20.1') {
        Write-MtErrorLine ("--side 只对 fabric-1.20.1 有意义（收到 --version {0}；其它三线是单 run 目录 run/<版本>）" -f $(if ($Version) { $Version } else { '<未指定>' }))
        exit $MT_EXIT_ERROR
    }
    if ($Side -ne 'client' -and $Side -ne 'server') {
        Write-MtErrorLine ("--side 需要 client 或 server（收到：{0}）" -f [string]$script:Opt.Side)
        exit $MT_EXIT_ERROR
    }
    $env:MT_SIDE = $Side
    Write-MtInfo ("SIDE: {0}（fabric-1.20.1 运行目录 {1}/run/{0}）" -f $Side, 'fabric-1.20.1')
}

# 显式开关优先于任何场景默认（放在分支之前，stop / 生成条目等早退路径同样生效）
if ($CleanupMode -ne '') { $script:MtDoCleanup = [int]$CleanupMode }

$overall = $MT_EXIT_PASS
$gateOpen = $true
$runId = Get-MtActiveRunId
# D4-2：退出清理的收停/清档范围 —— 单版本运行只动本线；多线顺序全流程（无 --version）
# 保持旧行为（全部版本 + 停 Gradle 守护，见 mt_cleanup 的 hasVersions 分支）。
$cleanupVersions = if ($Version) { @($Version) } else { @() }

try {
    # ── 需要生成条目时先交给子技能 ──────────────────────────────────────
    if ($GenNew -or $GenFeature -or $GenSpec) {
        if (-not $Version) { Write-MtErrorLine '生成条目需同时指定 --version'; exit $MT_EXIT_ERROR }
        if ($GenNew) {
            $rc = Invoke-MtChild -Script 'mt_gen_case.ps1' -ScriptArgs @('--version', $Version, '--new', $GenNew)
            if ($rc -ne 0) { exit $MT_EXIT_ERROR }
        }
        if ($GenFeature) {
            $rc = Invoke-MtChild -Script 'mt_gen_case.ps1' -ScriptArgs @('--version', $Version, '--feature', $GenFeature)
            if ($rc -ne 0) { exit $MT_EXIT_ERROR }
        }
        if ($GenSpec) {
            $rc = Invoke-MtChild -Script 'mt_gen_case.ps1' -ScriptArgs @('--version', $Version, '--spec', $GenSpec)
            if ($rc -ne 0) { exit $MT_EXIT_ERROR }
        }
        exit $MT_EXIT_PASS
    }

    # ══ 🚫 批量编排闸门（2026-09-27 用户规则：禁用自动测试，改为纯手动下达命令）══════
    # ⚠️ **必须放在 `stop` / 单阶段分支之前**（实测踩过，2026-09-27）：闸门初次写在单阶段
    #    分支**之后**，而单阶段分支以 `exit` 收尾 ⇒ `--phase report` 直接绕过闸门跑完了。
    #    闸门必须覆盖**全部**早退路径，故前移到此处（参数解析完、生成条目分支之后）。
    #
    # 为什么只拦这四处（而不是"一律禁"）：手动测试同样需要 build / env / launch 把客户端
    # 弄起来、需要 stop 收停 —— 把它们一起禁掉等于把测试台废掉，不符合"改为手动"的本意。
    # 真正要禁的是**「无人干预地自动跑完一批」**这一行为，即：
    #   ① 无 --phase 的全流程（三线顺序 + 1.21.1→1.20.1 门控 + 自动跑 cases + 自动 report）
    #   ② --phase cases 且**未**指定 --case（走 run-dir，自动跑完 cases/ 下该线全部用例）
    #   ③ --phase report（收集自动跑出来的结果）
    #   ④ 无 --version 的单阶段（对所有版本顺序执行该阶段 = 批量）
    # 单条 `--phase cases --case <某例>` 是"手动驱动的一次执行"，**放行**。
    if (-not $AllowAuto) {
        $autoBlocked = $false
        $autoWhat = ''
        if (-not $Phase) {
            $autoBlocked = $true
            $autoWhat = '全流程（无 --phase；会三线顺序自动跑完 build→env→launch→cases→report）'
        } elseif ($Phase -eq 'cases' -and -not $CasePath) {
            $autoBlocked = $true
            $autoWhat = '--phase cases 未指定 --case（走 run-dir，自动跑完该线全部用例）'
        } elseif ($Phase -eq 'report') {
            $autoBlocked = $true
            $autoWhat = '--phase report（收集自动跑出来的结果）'
        } elseif (-not $Version) {
            $autoBlocked = $true
            $autoWhat = ('--phase {0} 未指定 --version（会对三线顺序批量执行）' -f $Phase)
        }
        if ($autoBlocked) {
            Write-MtErrLine ('MT_AUTO_DISABLED: {0} —— 已被禁用。' -f $autoWhat)
            Write-MtErrLine '  当前规则：「纯手动下达命令」——逐条注入、逐条看读数、逐步决策。'
            Write-MtErrLine '  放行的阶段：build / env / launch / stop（把客户端弄起来/收停所需的基础设施）'
            Write-MtErrLine '  单条执行：pwsh -File scripts/test/mt.ps1 --version <ver> --phase cases --case scripts/test/cases/<CASE>.json'
            Write-MtErrLine '  直接注入：pwsh -File scripts/test/mt_inject.ps1 cmd --command "/astralprobe ..."'
            Write-MtErrLine '  手动读数：pwsh -File scripts/test/mt_assert.ps1 log --version <ver> --pattern "<正则>"'
            Write-MtErrLine '  临时放行：追加 --allow-auto（或设 MT_ALLOW_AUTO=1）'
            exit $MT_EXIT_ERROR
        }
    }

    # ── stop 阶段独立可用 ───────────────────────────────────────────────
    # --force / --keep-daemon / --purge-saves 在此阶段透传给 mt_cleanup（本身不参与阶段编排）。
    # 测试任务**收尾**用：`--phase stop --purge-saves`（收停测试端 + 清掉旧存档，
    # 避免游戏进程与旧世界长期驻留）。中途的 stop（重登/两阶段用例）**不要**加该开关。
    if ($Phase -eq 'stop') {
        [string[]]$stopArgs = @()
        if ($Version) { $stopArgs = @('--version', $Version) } else { $stopArgs = @('--all') }
        if ($StopForce) { $stopArgs += '--force' }
        if ($StopKeepDaemon) { $stopArgs += '--keep-daemon' }
        if ($StopPurgeSaves) { $stopArgs += '--purge-saves' }
        exit (Invoke-MtChild -Script 'mt_stop.ps1' -ScriptArgs $stopArgs)
    }

    # ── 单阶段模式 ──────────────────────────────────────────────────────
    # 单阶段默认不清理（launch → cases 需分步执行、客户端要活着）；显式开关已在上面生效。
    if ($Phase) {
        if (-not $Version) {
            # 无 --version 时对所有版本（Get-MtVersions 的三条线）顺序执行该阶段
            $rc = 0
            foreach ($v in @(Get-MtVersions)) {
                if (-not (Assert-MtVersion -Version $v)) { exit $MT_EXIT_ERROR }
                $one = Invoke-MtRunPhase -PhaseVersion $v -PhaseName $Phase -CasePath $CasePath
                if ($one -ne 0) { $rc = $one }
            }
            exit $rc
        }
        if (-not (Assert-MtVersion -Version $Version)) { exit $MT_EXIT_ERROR }
        exit (Invoke-MtRunPhase -PhaseVersion $Version -PhaseName $Phase -CasePath $CasePath)
    }

    # ── 全流程 ──────────────────────────────────────────────────────────
    # 全流程默认开启退出清理（可用 --no-cleanup-on-exit 关闭）。
    if ($CleanupMode -eq '') { $script:MtDoCleanup = 1 } else { $script:MtDoCleanup = [int]$CleanupMode }

    Write-MtLine ''
    Write-MtLine "########## MT RUN $runId ##########"

    # ⑤ 修复（2026-09-15 B6）:`--version` 在**全流程**分支同样生效。
    # 旧实现无条件 `foreach ($v in @(Get-MtVersions))`,并把 1.20.1 挂在 `$gateOpen` 上 ⇒
    # `mt.ps1 --version 1.20.1` 会**先跑 1.21.1**（无视用户指定的版本),1.21.1 一旦不通过,
    # 1.20.1 立刻被记成 `GATED` 而**根本没跑** —— 与 `--version` 的语义完全相反。
    # 现在:指定单版本 ⇒ 只跑该版本、不做跨版本门控（门控的前提是"多版本顺序执行",
    # 单版本时不存在"上一个版本");未指定 ⇒ 沿用三版本顺序 + 门控的原行为。
    # 2026-11-01（fabric 登记裁决）：**pwsh 侧默认多线全流程排除 fabric-1.20.1**。
    # 理由：mt_env.ps1 / mt_loadergate.ps1 尚未按 fabric 适配（稳态模组期望表仍是 1.21.1/1.20.1/26.1.2
    # 三条 NeoForge/Forge 口径，对 fabric 会给出假 BLOCKED —— 实测 `--version fabric-1.20.1 --side server --phase env`
    # 打出 `MT_MODS: BLOCKED — 整合包缺少 *sodium-neoforge*0.8.13*.jar, *iris-neoforge*…`）。
    # fabric 的阶段实现以 bash 侧 scripts/test/win/lib/fabric.sh 为准（原生实现、不委托 pwsh）。
    # ⇒ fabric 全流程入口：bash scripts/test/win/mt.sh --version fabric-1.20.1 --side server --allow-auto
    # 单版本 `--version fabric-1.20.1` 的 pwsh 调用仍允许（登记已生效），只是阶段实现以 bash 为权威。
    $pwshFlowExcludes = @('fabric-1.20.1')
    $flowVersions = if ($Version) { @($Version) } else { @(Get-MtVersions | Where-Object { $pwshFlowExcludes -notcontains $_ }) }
    $multiVersion = -not [bool]$Version

    # B6 ⑥-2：全局运行预算（只对全流程生效；单阶段模式不适用）
    [long]$runDeadline = if ($RunTimeoutSec -gt 0) { [DateTimeOffset]::UtcNow.ToUnixTimeSeconds() + $RunTimeoutSec } else { [long]0 }
    if ($RunTimeoutSec -gt 0) {
        Write-MtInfo ("RUN_TIMEOUT: {0}s（超时即 --phase stop --force 收停，退出码 {1}）" -f $RunTimeoutSec, $MT_EXIT_TIMEOUT)
    }

    Start-MtPhase 'preflight'
    [void](Set-MtProgress -Phase 'preflight' -Version ($flowVersions -join ','))
    # D4-2（2026-10-17）：**只检查本次真正要跑的线**。旧实现无条件 `--all` ⇒
    # `mt.ps1 --version 1.20.1` 也会去扫 1.21.1 的遗留进程并自动收停，两条线无法并行
    # （实测双线排队约 1 小时）。多线顺序全流程（无 --version）仍用 --all。
    [string[]]$preflightArgs = if ($Version) { @('--version', $Version) } else { @('--all') }
    $preflightArgs += $script:ChannelArgs
    $rc = Invoke-MtChild -Script 'mt_preflight.ps1' -ScriptArgs $preflightArgs -TimeoutSec (Get-MtPhaseBudget 'preflight')
    if ($rc -ne 0) {
        Write-MtErrLine 'MT_RUN: ABORT（前置失败）'
        $stopHint = if ($Version) { "--version $Version --phase stop" } else { '--phase stop' }
        Write-MtErrLine ("提示: 如需清理前置检查发现的残留进程，执行 pwsh -File scripts/test/mt.ps1 $stopHint")
        exit $MT_EXIT_PREFLIGHT
    }
    foreach ($pv in $flowVersions) {
        [void](Invoke-MtChild -Script 'mt_report.ps1' -ScriptArgs @('mark', '--version', $pv, '--phase', 'preflight', '--result', 'PASS'))
    }

    $overall = $MT_EXIT_PASS
    $gateOpen = $true

    foreach ($v in $flowVersions) {
        if (-not (Assert-MtVersion -Version $v)) { exit $MT_EXIT_ERROR }
        # B6 ⑥-2：进入每个版本、以及每跑完一个阶段都核一次全局预算
        if (Test-MtRunBudgetExceeded -Deadline $runDeadline) { Invoke-MtRunTimeoutStop -Version $v }
        Write-MtLine ''
        Write-MtLine "########## MT VERSION: $v ##########"

        if ($multiVersion -and $v -eq '1.20.1' -and -not $gateOpen) {
            # 首条线（1.21.1）未通过 ⇒ 自本条线起一律不再执行；**逐条**登记 GATED（含 26.1.2）。
            # 旧实现只标记本条线后即 break ⇒ 总览里第三条线缺项（2026-09-24 修，跳过语义不变）。
            # 注：1.20.1 自身失败**不**门控 26.1.2（后者由 1.21.1 迁移而来），该语义保持不变。
            $gateMark = $false
            foreach ($gv in $flowVersions) {
                if ($gv -eq $v) { $gateMark = $true }
                if (-not $gateMark) { continue }
                Write-MtBlocked "version-$gv" '1.21.1 未通过，按测试顺序门控不执行'
                [void](Invoke-MtChild -Script 'mt_report.ps1' -ScriptArgs @('mark', '--version', $gv, '--phase', 'cases', '--result', 'GATED'))
            }
            break
        }

        $vrc = 0
        Start-MtPhase 'build'
        $vrc = Invoke-MtRunPhase -PhaseVersion $v -PhaseName 'build'
        # BLOCKED 单列：B6 ④ 的 OP / dump 前置闸门返回 MT_EXIT_BLOCKED(11)，它是前置欠缺，
        # 不是产品缺陷 —— 报告里必须看得出来
        $r = if ($vrc -eq 0) { 'PASS' } elseif ($vrc -eq $MT_EXIT_BLOCKED) { 'BLOCKED' } elseif ($vrc -eq $MT_EXIT_TIMEOUT) { 'TIMEOUT' } else { 'FAIL' }
        [void](Invoke-MtChild -Script 'mt_report.ps1' -ScriptArgs @('mark', '--version', $v, '--phase', 'build', '--result', $r))

        if ($vrc -eq 0) {
            Start-MtPhase 'env'
            $vrc = Invoke-MtRunPhase -PhaseVersion $v -PhaseName 'env'
            $r = if ($vrc -eq 0) { 'PASS' } elseif ($vrc -eq $MT_EXIT_BLOCKED) { 'BLOCKED' } elseif ($vrc -eq $MT_EXIT_TIMEOUT) { 'TIMEOUT' } else { 'FAIL' }
            [void](Invoke-MtChild -Script 'mt_report.ps1' -ScriptArgs @('mark', '--version', $v, '--phase', 'env', '--result', $r))
        }

        if ($vrc -eq 0) {
            Start-MtPhase 'launch'
            $vrc = Invoke-MtRunPhase -PhaseVersion $v -PhaseName 'launch'
            $r = if ($vrc -eq 0) { 'PASS' } elseif ($vrc -eq $MT_EXIT_BLOCKED) { 'BLOCKED' } elseif ($vrc -eq $MT_EXIT_TIMEOUT) { 'TIMEOUT' } else { 'FAIL' }
            [void](Invoke-MtChild -Script 'mt_report.ps1' -ScriptArgs @('mark', '--version', $v, '--phase', 'launch', '--result', $r))
            # launch 基线：此后 `mixin` 断言与报告摘要读 `launch_offsets`；
            # 每条用例自己再写 `--window case` 的 `offsets`（B7，见 mt_assert.ps1 文件头）
            # （A2 起 mt_launch 收尾已自行写快照，覆盖单阶段路线；这里再写一次是幂等的保险）
            if ($vrc -eq 0) {
                [void](Invoke-MtChild -Script 'mt_assert.ps1' -ScriptArgs @('snapshot', '--window', 'launch', '--version', $v))
            }
        }

        if ($vrc -eq 0) {
            Start-MtPhase 'cases'
            $vrc = Invoke-MtRunPhase -PhaseVersion $v -PhaseName 'cases' -CasePath $CasePath
            # BLOCKED 单列：B6 ④ 的 OP / dump 前置闸门返回 MT_EXIT_BLOCKED(11)，它是前置欠缺，
            # 不是产品缺陷 —— 报告里必须看得出来。TIMEOUT(12) 同理单列（B6 ⑥-1）。
            $r = if ($vrc -eq 0) { 'PASS' } elseif ($vrc -eq $MT_EXIT_BLOCKED) { 'BLOCKED' }
            elseif ($vrc -eq $MT_EXIT_TIMEOUT) { 'TIMEOUT' } else { 'FAIL' }
            [void](Invoke-MtChild -Script 'mt_report.ps1' -ScriptArgs @('mark', '--version', $v, '--phase', 'cases', '--result', $r))
        }

        # B6 ⑥-2：cases 阶段可能跑很久，跑完立刻核一次全局预算
        if (Test-MtRunBudgetExceeded -Deadline $runDeadline) { Invoke-MtRunTimeoutStop -Version $v }

        Start-MtPhase 'report'
        $verdict = if ($vrc -eq 0) { 'PASS' } elseif ($vrc -eq $MT_EXIT_TIMEOUT) { 'TIMEOUT' } else { 'FAIL' }
        [void](Invoke-MtChild -Script 'mt_report.ps1' -ScriptArgs @('collect', '--version', $v, '--verdict', $verdict))
        [void](Invoke-MtChild -Script 'mt_stop.ps1' -ScriptArgs @('--version', $v))

        if ($vrc -eq 0) {
            Write-MtLine "MT_VERSION_VERDICT: $v = PASS"
        } else {
            $label = if ($vrc -eq $MT_EXIT_TIMEOUT) { 'TIMEOUT' } else { 'FAIL' }
            Write-MtLine "MT_VERSION_VERDICT: $v = $label (exit=$vrc)"
            $overall = if ($vrc -eq $MT_EXIT_TIMEOUT) { $MT_EXIT_TIMEOUT } else { $MT_EXIT_FAIL }
            $gateOpen = $false      # 关闭门控：1.20.1 不再执行
        }
    }

    Start-MtPhase 'summary'
    $summaryRc = Invoke-MtChild -Script 'mt_report.ps1' -ScriptArgs @('summary')
    # t34（规则 14 收严版）折码修复：`mt_report summary` 在「无效运行（MT_USER_TERMINATED）」上返回
    # `MT_EXIT_ERROR`(2) —— 该中断码必须**原样传出**，不再像以前那样折成 `MT_EXIT_FAIL`(1)；
    # 否则端到端退出码分不清「被用户终止的无效运行」与「产品断言失败」（TESTING-SPEC §12.3 第 5 条）。
    # 语义保持：任何非 0 汇总码仍保证端到端非 0（FAIL(1) 语义不变），只是把中断码单列出来。
    # 无效运行优先于 FAIL：即使某版本已判 FAIL，只要汇总结论是「无效运行」就以中断码收尾
    # （规则 14：该次运行的全部结果无效；真实失败串已在 SUMMARY.md 里如实并列，供重新执行时复验）。
    if ($summaryRc -ne 0) {
        $overall = if ($summaryRc -eq $MT_EXIT_ERROR) { $MT_EXIT_ERROR } else { $MT_EXIT_FAIL }
    }

    # 退出清理走在这里（早于最终判定行），finally 只是兜底（Ctrl-C / 异常退出）
    [void](Invoke-MtAutoCleanup -Versions $cleanupVersions)
} finally {
    [void](Invoke-MtAutoCleanup -Versions $cleanupVersions)
}

Write-MtLine ''
if ($overall -eq 0) {
    if ($multiVersion) { Write-MtLine 'MT_RUN: PASS — 三版本均通过' }
    else { Write-MtLine "MT_RUN: PASS — $Version 通过（--version 指定单版本，未执行跨版本门控）" }
} elseif ($overall -eq $MT_EXIT_TIMEOUT) {
    Write-MtLine "MT_RUN: TIMEOUT — 见 reports/$runId/SUMMARY.md（超时与 FAIL 是两种结论）"
} elseif ($overall -eq $MT_EXIT_ERROR) {
    # t34：无效运行（规则 14 收严版）—— 与产品 FAIL 是两种结论，退出码 2 ≠ 1
    Write-MtLine "MT_RUN: 无效运行（MT_USER_TERMINATED）— 本次测试结果全部无效，需重新执行测试验证（与 FAIL 是两种结论）"
    Write-MtLine "MT_RUN: INVALID reason=USER_TERMINATED exit=$MT_EXIT_ERROR — 见 reports/$runId/SUMMARY.md"
} else {
    Write-MtLine "MT_RUN: FAIL — 见 reports/$runId/SUMMARY.md"
}
exit $overall
