#Requires -Version 7.0
<#
.SYNOPSIS
    mt.ps1 — 模组测试唯一入口（pwsh 工具链）。

.DESCRIPTION
    测试顺序（硬性）:
      前置检查 → 1.21.1 全流程 → 判定通过后才执行 → 1.20.1 全流程 → 总览
      两个版本都给出独立的通过/失败结论；1.21.1 不通过时 1.20.1 记为「因门控未执行」。

    `--version <V>`:
      全流程分支**同样尊重该参数** —— 指定单版本时只跑该版本，且**不做跨版本门控**
      （门控的前提是「两版本顺序执行」）。不带 `--version` 时才是「两版本顺序 + 门控」。

    阶段: preflight | build | env | launch | cases | report | stop

.EXAMPLE
    pwsh -File scripts/test/mt.ps1                                  # 全流程（两版本，带门控）
    pwsh -File scripts/test/mt.ps1 --version 1.21.1                 # 全流程（只跑 1.21.1，无门控）
    pwsh -File scripts/test/mt.ps1 --version 1.20.1                 # 全流程（只跑 1.20.1，无门控）
    pwsh -File scripts/test/mt.ps1 --version 1.21.1 --phase build   # 单阶段
    pwsh -File scripts/test/mt.ps1 --version 1.21.1 --case cases/X.json
    pwsh -File scripts/test/mt.ps1 --version 1.21.1 --new ember_chip
    pwsh -File scripts/test/mt.ps1 --phase stop

.NOTES
    迁移前源文件 scripts/test/mt.sh（该原件已在 92fbeaf「工具链收敛为纯 pwsh」删除，取回：`git show 92fbeaf^:scripts/test/mt.sh`）。

    退出清理（避免进程泄漏）:
      全流程会在退出前自动收停本流程进程与 Gradle 守护（正常结束 / 失败 / Ctrl-C
      三条路径都覆盖），实现见 mt_cleanup.ps1。单阶段模式**默认不清理**，因为
      launch → cases 是分步进行的，需要让客户端在两步之间保持运行；如需强制，
      加 --cleanup-on-exit，或用 --no-cleanup-on-exit 关闭全流程的自动清理。
      若条目失败且 on_fail=keep_game_running，会落 .mt_keep_alive 标记，
      此时自动清理只提示不杀进程，保留现场供取证（取证完用 --phase stop --force）。

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
      · **每一类子进程都有硬上限**：preflight 120s / build 300s / env 240s / launch 180s /
        report 90s / stop 150s / cleanup 180s（表见 `$script:PhaseBudgets`）；
        cases 阶段**自适应**：单条 = 单条硬上限 + 90s，全目录 = 90s × 用例数 + 90s
        （固定值要么误杀 17 条全量跑、要么等于不限，故按用例数推导）。
        单阶段可用 `MT_<阶段>_TIMEOUT_SEC`（如 `MT_ENV_TIMEOUT_SEC=600`）覆写。
      · 等待期间每 10s 一行 `MT_WAIT:` 心跳（已等待 / 硬上限 / 子进程 CPU 或日志字节数）；
        到点即终止**我们自己那个**子进程并打印卡点，绝不 taskkill 进程树。
      · launch 走脱离式，除硬上限外还有「日志 90s 零增长 ⇒ `CHILD: STALL` 放弃等待」——
        进程存活 ≠ 有进展（实测：游戏空闲时仍有每月一分钟一条的 ModernFix DEBUG 噪声，
        旧监视器就是被它骗过，见 TESTING-SPEC §12）。
      · 单条用例硬超时 `--case-timeout` / `MT_CASE_TIMEOUT_SEC`（默认 180 s，见 mt_case.ps1）
        ⇒ 该条记 **TIMEOUT** 并继续跑下一条；
      · 全局 `--run-timeout <秒>`（**默认 2700s**；旧默认「0 = 不限」已废除 —— 那正是
        「7 分 45 秒静默空转、人只能干等」能发生的前提）⇒ 超时走 `--phase stop --force`
        收停、报告写 TIMEOUT、退出码 12（与 FAIL=1 区分）；
      · 长流程包裹用 `mt_watchdog.ps1`（独立脚本，见 TESTING-SPEC §12；判据已从「日志
        mtime」改为**语义标记 + 进度信标**）；阶段/用例/步骤级进度写在
        `cases/.mt_progress.json`，监视器与事后取证共读这一个文件即可定位卡点。

    文案偏差：前置失败提示里的 `mt.sh --phase stop` 改为 `mt.ps1`（同一入口的新名字）。

    行为修复（唯一一处非 1:1 移植）：env 阶段的种子包开关由「硬编码 1.20.1 种子包存在性、
    两版本共用」改为「按版本各查 testworld-seed-<版本>.zip」，见 Invoke-MtRunPhase 内注释。
    1.20.1 行为与原版一致；1.21.1 不再被塞入 1.20.1 的 --seed（原版必然 MT_WORLD: BLOCKED）。
#>

$ErrorActionPreference = 'Stop'

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
    launch    = 180
    cases     = 300
    report    = 90
    stop      = 150
    cleanup   = 180
}
$script:ChildTimeoutSec = 300       # 未列名阶段的兜底上限
$script:HeartbeatSeconds = 10       # 心跳间隔
$script:LaunchNoProgressSec = 90    # launch 脱离式：日志 90s 零增长即判 STALL（放弃等待，不杀进程）

function Get-MtPhaseBudget {
    <#
    .SYNOPSIS
        取某阶段的硬超时（秒）；`MT_<阶段>_TIMEOUT_SEC` 可覆写。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][AllowEmptyString()][string]$PhaseName)

    $v = $script:ChildTimeoutSec
    if ($PhaseName -and $script:PhaseBudgets.Contains($PhaseName)) { $v = [int]$script:PhaseBudgets[$PhaseName] }
    if ($PhaseName) {
        $envName = 'MT_' + (($PhaseName -replace '[^A-Za-z0-9]', '_').ToUpperInvariant()) + '_TIMEOUT_SEC'
        $raw = [Environment]::GetEnvironmentVariable($envName)
        if ($raw -and $raw -match '^\d+$') { $v = [int]$raw }
    }
    return [int]$v
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
        依赖同级 devtools 目录。其余阶段保持 `-Wait` 语义不变。
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

    $argv = @('-NoProfile', '-File', (Join-Path $script:TestDir $Script)) + $ScriptArgs

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
        $proc = Start-Process -FilePath $script:PsExe `
            -ArgumentList (ConvertTo-MtStartArgs -ArgumentList $argv) `
            -NoNewWindow -PassThru
        $sw = [System.Diagnostics.Stopwatch]::StartNew()
        $lastBeat = 0.0
        while ((-not $proc.HasExited) -and ($sw.Elapsed.TotalSeconds -lt $TimeoutSec)) {
            Start-Sleep -Milliseconds 500
            if (($sw.Elapsed.TotalSeconds - $lastBeat) -ge $script:HeartbeatSeconds) {
                $lastBeat = $sw.Elapsed.TotalSeconds
                $cpu = 0.0
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
    $lastBeat = 0.0
    $lastSize = [long]-1
    $lastGrowth = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
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
        $size = [long]0
        try { $size = (Get-Item -LiteralPath $outLog -ErrorAction Stop).Length } catch { $size = [long]0 }
        $now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
        if ($size -ne $lastSize) { $lastSize = $size; $lastGrowth = $now }
        $idle = $now - $lastGrowth

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
    #>
    [CmdletBinding()]
    param()

    if ($script:MtCleanupDone -eq 1) { return 0 }
    $script:MtCleanupDone = 1
    if ($script:MtDoCleanup -ne 1) { return 0 }

    Write-MtLine ''
    Start-MtPhase 'cleanup (auto)'

    $argv = @('-NoProfile', '-File', (Join-Path $script:TestDir 'mt_cleanup.ps1'), 'run', '--quiet')
    $r = Invoke-MtProcessFull -FilePath $script:PsExe -ArgumentList $argv -TimeoutSec (Get-MtPhaseBudget 'cleanup')

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
        # resources/testworld-seed-1.20.1.zip 的存在性并把它作为两个版本共用的 --seed 开关，
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
        return (Invoke-MtChild -Script 'mt_launch.ps1' -ScriptArgs @('--version', $PhaseVersion) -Detached `
                -TimeoutSec (Get-MtPhaseBudget 'launch') -NoProgressSec $script:LaunchNoProgressSec)
    }
    if ($PhaseName -eq 'cases') {
        # cases 阶段是**预算自适应**的：单条 = 单条硬上限 + 90s 余量；全目录 = 90s × 用例数 + 90s。
        # 为什么不用固定值：固定 300s 会把 17 条用例的正常全量跑**误杀**，而固定 3600s 又等于不限。
        $perCase = if ($script:CaseTimeoutSec -gt 0) { $script:CaseTimeoutSec } else { 180 }
        $caseArgs = @('--version', $PhaseVersion, '--case-timeout', "$perCase")
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
        $budget = 90 + (90 * $n)
        Write-MtInfo ("CASES_BUDGET: {0} 条用例 × 90s + 90s = {1}s（单条硬上限 {2}s；覆写 MT_CASES_TIMEOUT_SEC）" -f $n, $budget, $perCase)
        return (Invoke-MtChild -Script 'mt_case.ps1' -ScriptArgs (@('run-dir') + $caseArgs) -TimeoutSec $budget)
    }
    if ($PhaseName -eq 'report') {
        return (Invoke-MtChild -Script 'mt_report.ps1' -ScriptArgs @('collect', '--version', $PhaseVersion) `
                -TimeoutSec (Get-MtPhaseBudget 'report'))
    }
    Write-MtErrorLine "未知阶段 $PhaseName"
    return $MT_EXIT_ERROR
}

# ══ 参数 ══════════════════════════════════════════════════════════════════
$Version = ''
$Phase = ''
$CasePath = ''
$GenNew = ''
$GenFeature = ''
$GenSpec = ''
$CleanupMode = ''          # '' = 按场景默认；1 = 强制开启；0 = 强制关闭
$StopForce = $false
$StopKeepDaemon = $false
# B6 ⑥-2：全流程全局超时（秒）。**2026-09-16 起默认 2700s（45 分钟）而不是「0 = 不限」**——
# 「不限」正是那次「launch 之后 cases 空转 7 分 45 秒、人只能干等」能发生的前提。
# 覆写：环境变量 MT_RUN_TIMEOUT_SEC 或 --run-timeout <秒>。
$RunTimeoutSec = 2700
if ($env:MT_RUN_TIMEOUT_SEC -and $env:MT_RUN_TIMEOUT_SEC -match '^\d+$') { $RunTimeoutSec = [int]$env:MT_RUN_TIMEOUT_SEC }
# 单条用例硬超时（透传给 mt_case 的 --case-timeout；0 = 用 mt_case 自己的默认 180s）
$script:CaseTimeoutSec = 0
if ($env:MT_CASE_TIMEOUT_SEC -and $env:MT_CASE_TIMEOUT_SEC -match '^\d+$') { $script:CaseTimeoutSec = [int]$env:MT_CASE_TIMEOUT_SEC }

$ShowHelp = $false
$i = 0
while ($i -lt $args.Count) {
    $tok = [string]$args[$i]
    $key = $tok.TrimStart('-').ToLowerInvariant()
    if ($key -eq 'version') {
        if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --version 的值'; exit $MT_EXIT_ERROR }
        $Version = [string]$args[$i + 1]; $i += 2
    } elseif ($key -eq 'phase') {
        if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --phase 的值'; exit $MT_EXIT_ERROR }
        $Phase = [string]$args[$i + 1]; $i += 2
    } elseif ($key -eq 'case') {
        if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --case 的值'; exit $MT_EXIT_ERROR }
        $CasePath = [string]$args[$i + 1]; $i += 2
    } elseif ($key -eq 'new') {
        if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --new 的值'; exit $MT_EXIT_ERROR }
        $GenNew = [string]$args[$i + 1]; $i += 2
    } elseif ($key -eq 'feature') {
        if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --feature 的值'; exit $MT_EXIT_ERROR }
        $GenFeature = [string]$args[$i + 1]; $i += 2
    } elseif ($key -eq 'spec') {
        if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --spec 的值'; exit $MT_EXIT_ERROR }
        $GenSpec = [string]$args[$i + 1]; $i += 2
    } elseif ($key -eq 'cleanup-on-exit') {
        $CleanupMode = '1'; $i++
    } elseif ($key -eq 'no-cleanup-on-exit') {
        $CleanupMode = '0'; $i++
    } elseif ($key -eq 'run-timeout') {
        if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --run-timeout 的值'; exit $MT_EXIT_ERROR }
        if ([string]$args[$i + 1] -notmatch '^\d+$') { Write-MtErrorLine '--run-timeout 需要非负整数（秒）'; exit $MT_EXIT_ERROR }
        $RunTimeoutSec = [int]$args[$i + 1]; $i += 2
    } elseif ($key -eq 'case-timeout') {
        if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --case-timeout 的值'; exit $MT_EXIT_ERROR }
        if ([string]$args[$i + 1] -notmatch '^\d+$') { Write-MtErrorLine '--case-timeout 需要非负整数（秒）'; exit $MT_EXIT_ERROR }
        $script:CaseTimeoutSec = [int]$args[$i + 1]; $i += 2
    } elseif ($key -eq 'force') {
        $StopForce = $true; $i++
    } elseif ($key -eq 'keep-daemon') {
        $StopKeepDaemon = $true; $i++
    } elseif ($key -eq 'h' -or $key -eq 'help') {
        $ShowHelp = $true; $i++
    } else {
        Write-MtErrorLine "未知参数 $tok"; exit $MT_EXIT_ERROR
    }
}

if ($ShowHelp) {
    # 对应 bash 的 `sed -n '2,23p' "$0"`
    foreach ($ln in @(Get-Content -LiteralPath $PSCommandPath | Select-Object -Skip 1 -First 22)) {
        Write-MtLine ([string]$ln)
    }
    exit 0
}

# 显式开关优先于任何场景默认（放在分支之前，stop / 生成条目等早退路径同样生效）
if ($CleanupMode -ne '') { $script:MtDoCleanup = [int]$CleanupMode }

$overall = $MT_EXIT_PASS
$gateOpen = $true
$runId = Get-MtActiveRunId

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

    # ── stop 阶段独立可用 ───────────────────────────────────────────────
    # --force / --keep-daemon 在此阶段透传给 mt_cleanup（本身不参与阶段编排）。
    if ($Phase -eq 'stop') {
        $stopArgs = @()
        if ($Version) { $stopArgs = @('--version', $Version) } else { $stopArgs = @('--all') }
        if ($StopForce) { $stopArgs += '--force' }
        if ($StopKeepDaemon) { $stopArgs += '--keep-daemon' }
        exit (Invoke-MtChild -Script 'mt_stop.ps1' -ScriptArgs $stopArgs)
    }

    # ── 单阶段模式 ──────────────────────────────────────────────────────
    # 单阶段默认不清理（launch → cases 需分步执行、客户端要活着）；显式开关已在上面生效。
    if ($Phase) {
        if (-not $Version) {
            # 无 --version 时对两个版本顺序执行该阶段
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
    # 现在:指定单版本 ⇒ 只跑该版本、不做跨版本门控（门控的前提是"两版本顺序执行",
    # 单版本时不存在"上一个版本");未指定 ⇒ 沿用两版本顺序 + 门控的原行为。
    $flowVersions = if ($Version) { @($Version) } else { @(Get-MtVersions) }
    $multiVersion = -not [bool]$Version

    # B6 ⑥-2：全局运行预算（只对全流程生效；单阶段模式不适用）
    $runDeadline = if ($RunTimeoutSec -gt 0) { [DateTimeOffset]::UtcNow.ToUnixTimeSeconds() + $RunTimeoutSec } else { [long]0 }
    if ($RunTimeoutSec -gt 0) {
        Write-MtInfo ("RUN_TIMEOUT: {0}s（超时即 --phase stop --force 收停，退出码 {1}）" -f $RunTimeoutSec, $MT_EXIT_TIMEOUT)
    }

    Start-MtPhase 'preflight'
    [void](Set-MtProgress -Phase 'preflight' -Version ($flowVersions -join ','))
    $rc = Invoke-MtChild -Script 'mt_preflight.ps1' -ScriptArgs @('--all') -TimeoutSec (Get-MtPhaseBudget 'preflight')
    if ($rc -ne 0) {
        Write-MtErrLine 'MT_RUN: ABORT（前置失败）'
        Write-MtErrLine '提示: 如需清理前置检查发现的残留进程，执行 pwsh -File scripts/test/mt.ps1 --phase stop'
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
            Write-MtBlocked "version-$v" '1.21.1 未通过，按测试顺序门控不执行 1.20.1'
            [void](Invoke-MtChild -Script 'mt_report.ps1' -ScriptArgs @('mark', '--version', $v, '--phase', 'cases', '--result', 'GATED'))
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
            # BLOCKED 单列：B6 ④ 的 OP / dump 前置闸门返回 MT_EXIT_BLOCKED(11)，它是前置欠缺，
            # 不是产品缺陷 —— 报告里必须看得出来
            $r = if ($vrc -eq 0) { 'PASS' } elseif ($vrc -eq $MT_EXIT_BLOCKED) { 'BLOCKED' } elseif ($vrc -eq $MT_EXIT_TIMEOUT) { 'TIMEOUT' } else { 'FAIL' }
            [void](Invoke-MtChild -Script 'mt_report.ps1' -ScriptArgs @('mark', '--version', $v, '--phase', 'env', '--result', $r))
        }

        if ($vrc -eq 0) {
            Start-MtPhase 'launch'
            $vrc = Invoke-MtRunPhase -PhaseVersion $v -PhaseName 'launch'
            # BLOCKED 单列：B6 ④ 的 OP / dump 前置闸门返回 MT_EXIT_BLOCKED(11)，它是前置欠缺，
            # 不是产品缺陷 —— 报告里必须看得出来
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
    if ($summaryRc -ne 0) { $overall = $MT_EXIT_FAIL }

    # 退出清理走在这里（早于最终判定行），finally 只是兜底（Ctrl-C / 异常退出）
    [void](Invoke-MtAutoCleanup)
} finally {
    [void](Invoke-MtAutoCleanup)
}

Write-MtLine ''
if ($overall -eq 0) {
    if ($multiVersion) { Write-MtLine 'MT_RUN: PASS — 两版本均通过' }
    else { Write-MtLine "MT_RUN: PASS — $Version 通过（--version 指定单版本，未执行跨版本门控）" }
} elseif ($overall -eq $MT_EXIT_TIMEOUT) {
    Write-MtLine "MT_RUN: TIMEOUT — 见 reports/$runId/SUMMARY.md（超时与 FAIL 是两种结论）"
} else {
    Write-MtLine "MT_RUN: FAIL — 见 reports/$runId/SUMMARY.md"
}
exit $overall
