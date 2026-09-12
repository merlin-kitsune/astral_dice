#Requires -Version 7.0
<#
.SYNOPSIS
    mt.ps1 — 模组测试唯一入口（pwsh 工具链）。

.DESCRIPTION
    测试顺序（硬性）:
      前置检查 → 1.21.1 全流程 → 判定通过后才执行 → 1.20.1 全流程 → 总览
      两个版本都给出独立的通过/失败结论；1.21.1 不通过时 1.20.1 记为「因门控未执行」。

    阶段: preflight | build | env | launch | cases | report | stop

.EXAMPLE
    pwsh -File scripts/test/mt.ps1                                  # 全流程（两版本，带门控）
    pwsh -File scripts/test/mt.ps1 --version 1.21.1 --phase build   # 单阶段
    pwsh -File scripts/test/mt.ps1 --version 1.21.1 --case cases/X.json
    pwsh -File scripts/test/mt.ps1 --version 1.21.1 --new ember_chip
    pwsh -File scripts/test/mt.ps1 --phase stop

.NOTES
    对应源文件（迁移前）：scripts/test/mt.sh。

    退出清理（避免进程泄漏）:
      全流程会在退出前自动收停本流程进程与 Gradle 守护（正常结束 / 失败 / Ctrl-C
      三条路径都覆盖），实现见 mt_cleanup.ps1。单阶段模式**默认不清理**，因为
      launch → cases 是分步进行的，需要让客户端在两步之间保持运行；如需强制，
      加 --cleanup-on-exit，或用 --no-cleanup-on-exit 关闭全流程的自动清理。
      若条目失败且 on_fail=keep_game_running，会落 .mt_keep_alive 标记，
      此时自动清理只提示不杀进程，保留现场供取证（取证完用 --phase stop --force）。

    子进程一律用 Start-Process -NoNewWindow -PassThru -Wait 启动：子进程**直接继承
    父进程的控制台句柄**，输出不经 PowerShell 的解码/再编码，字节原样透传
    （`& pwsh …` 的调用运算符会让原生输出过一遍 PS 的编码层）。

    文案偏差：前置失败提示里的 `mt.sh --phase stop` 改为 `mt.ps1`（同一入口的新名字）。
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

function Invoke-MtChild {
    <#
    .SYNOPSIS
        启动一个 mt_*.ps1 子进程并返回其退出码（输出直通，不经 PS 编码层）。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Script, [string[]]$ScriptArgs = @())

    $argv = @('-NoProfile', '-File', (Join-Path $script:TestDir $Script)) + $ScriptArgs
    $proc = Start-Process -FilePath $script:PsExe `
        -ArgumentList (ConvertTo-MtStartArgs -ArgumentList $argv) `
        -NoNewWindow -PassThru -Wait
    return $proc.ExitCode
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
    $r = Invoke-MtProcessFull -FilePath $script:PsExe -ArgumentList $argv -TimeoutSec 600

    foreach ($ln in (Get-MtCleanupDisplayLines -Text $r.StdOut)) { Write-MtLine $ln }

    if ($r.StdOut.Contains('MT_CLEANUP_RESULT: SKIP')) {
        Write-MtWarn 'CLEANUP' '按失败取证标记保留了游戏现场（未收停）—— 取证完用 --phase stop --force 释放'
        return 0
    }
    if ($r.ExitCode -eq 0) {
        Write-MtOk 'CLEANUP' '退出清理完成，无本流程残留进程'
        return 0
    }
    Write-MtWarn 'CLEANUP' '退出清理后仍有残留，详见上方 mt_cleanup 输出'
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
        [string]$CasePath = '',
        [string[]]$SeedArgs = @()
    )

    if ($PhaseName -eq 'build') {
        return (Invoke-MtChild -Script 'mt_build.ps1' -ScriptArgs @('--version', $PhaseVersion))
    }
    if ($PhaseName -eq 'env') {
        $rc = Invoke-MtChild -Script 'mt_env.ps1' -ScriptArgs @('mods', '--version', $PhaseVersion)
        if ($rc -ne 0) { return $rc }
        return (Invoke-MtChild -Script 'mt_env.ps1' -ScriptArgs (@('world', '--version', $PhaseVersion) + $SeedArgs))
    }
    if ($PhaseName -eq 'launch') {
        return (Invoke-MtChild -Script 'mt_launch.ps1' -ScriptArgs @('--version', $PhaseVersion))
    }
    if ($PhaseName -eq 'cases') {
        if ($CasePath) {
            return (Invoke-MtChild -Script 'mt_case.ps1' -ScriptArgs @('run', '--version', $PhaseVersion, '--case', $CasePath))
        }
        return (Invoke-MtChild -Script 'mt_case.ps1' -ScriptArgs @('run-dir', '--version', $PhaseVersion))
    }
    if ($PhaseName -eq 'report') {
        return (Invoke-MtChild -Script 'mt_report.ps1' -ScriptArgs @('collect', '--version', $PhaseVersion))
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
    $seedArgs = @()
    if ($Phase -eq 'env') {
        $seedZip = Join-Path (Join-Path $script:TestDir 'resources') 'testworld-seed-1.20.1.zip'
        if (Test-Path -LiteralPath $seedZip -PathType Leaf) { $seedArgs = @('--seed') }
    }

    if ($Phase) {
        if (-not $Version) {
            # 无 --version 时对两个版本顺序执行该阶段
            $rc = 0
            foreach ($v in @(Get-MtVersions)) {
                if (-not (Assert-MtVersion -Version $v)) { exit $MT_EXIT_ERROR }
                $one = Invoke-MtRunPhase -PhaseVersion $v -PhaseName $Phase -CasePath $CasePath -SeedArgs $seedArgs
                if ($one -ne 0) { $rc = $one }
            }
            exit $rc
        }
        if (-not (Assert-MtVersion -Version $Version)) { exit $MT_EXIT_ERROR }
        exit (Invoke-MtRunPhase -PhaseVersion $Version -PhaseName $Phase -CasePath $CasePath -SeedArgs $seedArgs)
    }

    # ── 全流程 ──────────────────────────────────────────────────────────
    # 全流程默认开启退出清理（可用 --no-cleanup-on-exit 关闭）。
    if ($CleanupMode -eq '') { $script:MtDoCleanup = 1 } else { $script:MtDoCleanup = [int]$CleanupMode }

    Write-MtLine ''
    Write-MtLine "########## MT RUN $runId ##########"

    Start-MtPhase 'preflight'
    $rc = Invoke-MtChild -Script 'mt_preflight.ps1' -ScriptArgs @('--all')
    if ($rc -ne 0) {
        Write-MtErrLine 'MT_RUN: ABORT（前置失败）'
        Write-MtErrLine '提示: 如需清理前置检查发现的残留进程，执行 pwsh -File scripts/test/mt.ps1 --phase stop'
        exit $MT_EXIT_PREFLIGHT
    }
    [void](Invoke-MtChild -Script 'mt_report.ps1' -ScriptArgs @('mark', '--version', '1.21.1', '--phase', 'preflight', '--result', 'PASS'))
    [void](Invoke-MtChild -Script 'mt_report.ps1' -ScriptArgs @('mark', '--version', '1.20.1', '--phase', 'preflight', '--result', 'PASS'))

    $overall = $MT_EXIT_PASS
    $gateOpen = $true

    foreach ($v in @(Get-MtVersions)) {
        if (-not (Assert-MtVersion -Version $v)) { exit $MT_EXIT_ERROR }
        Write-MtLine ''
        Write-MtLine "########## MT VERSION: $v ##########"

        if ($v -eq '1.20.1' -and -not $gateOpen) {
            Write-MtBlocked "version-$v" '1.21.1 未通过，按测试顺序门控不执行 1.20.1'
            [void](Invoke-MtChild -Script 'mt_report.ps1' -ScriptArgs @('mark', '--version', $v, '--phase', 'cases', '--result', 'GATED'))
            break
        }

        $vrc = 0
        Start-MtPhase 'build'
        $vrc = Invoke-MtRunPhase -PhaseVersion $v -PhaseName 'build'
        $r = if ($vrc -eq 0) { 'PASS' } else { 'FAIL' }
        [void](Invoke-MtChild -Script 'mt_report.ps1' -ScriptArgs @('mark', '--version', $v, '--phase', 'build', '--result', $r))

        if ($vrc -eq 0) {
            Start-MtPhase 'env'
            $vrc = Invoke-MtRunPhase -PhaseVersion $v -PhaseName 'env' -SeedArgs $seedArgs
            $r = if ($vrc -eq 0) { 'PASS' } else { 'FAIL' }
            [void](Invoke-MtChild -Script 'mt_report.ps1' -ScriptArgs @('mark', '--version', $v, '--phase', 'env', '--result', $r))
        }

        if ($vrc -eq 0) {
            Start-MtPhase 'launch'
            $vrc = Invoke-MtRunPhase -PhaseVersion $v -PhaseName 'launch'
            $r = if ($vrc -eq 0) { 'PASS' } else { 'FAIL' }
            [void](Invoke-MtChild -Script 'mt_report.ps1' -ScriptArgs @('mark', '--version', $v, '--phase', 'launch', '--result', $r))
            # 快照点：此后所有日志断言只看增量区间
            if ($vrc -eq 0) {
                [void](Invoke-MtChild -Script 'mt_assert.ps1' -ScriptArgs @('snapshot', '--version', $v))
            }
        }

        if ($vrc -eq 0) {
            Start-MtPhase 'cases'
            $vrc = Invoke-MtRunPhase -PhaseVersion $v -PhaseName 'cases' -CasePath $CasePath
            $r = if ($vrc -eq 0) { 'PASS' } else { 'FAIL' }
            [void](Invoke-MtChild -Script 'mt_report.ps1' -ScriptArgs @('mark', '--version', $v, '--phase', 'cases', '--result', $r))
        }

        Start-MtPhase 'report'
        $verdict = if ($vrc -eq 0) { 'PASS' } else { 'FAIL' }
        [void](Invoke-MtChild -Script 'mt_report.ps1' -ScriptArgs @('collect', '--version', $v, '--verdict', $verdict))
        [void](Invoke-MtChild -Script 'mt_stop.ps1' -ScriptArgs @('--version', $v))

        if ($vrc -eq 0) {
            Write-MtLine "MT_VERSION_VERDICT: $v = PASS"
        } else {
            Write-MtLine "MT_VERSION_VERDICT: $v = FAIL (exit=$vrc)"
            $overall = $MT_EXIT_FAIL
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
    Write-MtLine 'MT_RUN: PASS — 两版本均通过'
} else {
    Write-MtLine "MT_RUN: FAIL — 见 reports/$runId/SUMMARY.md"
}
exit $overall
