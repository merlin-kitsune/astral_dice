#!/usr/bin/env pwsh
#Requires -Version 7.0
<#
.SYNOPSIS
    mt_watchdog — 长流程的**独立看门狗**（B6 ⑥-3）：盯「有没有进展」，停滞即报警/收停。

.DESCRIPTION
    存在的理由（2026-09-15 实测）：本轮有**三位执行者卡死**在「启动客户端 + 跑用例」——
    表现形式都是「脚本还活着，但再也没有任何输出」。工具链本身没有任何"无进展"判定，
    于是只能靠人盯着。本脚本把这件事做成可独立运行的一道闸门。

    判据是**进展信号**（任一变化即重置停滞计时）：

      · scripts/test/cases/.mt_run_state.json     条目/阶段结果（每条用例都会写）
      · scripts/test/cases/.mt_active_run         运行 id
      · 当前活动报告目录（reports/<run_id>/）下的任何文件
      · run/<版本>/logs/latest.log                大小 + mtime
      · run/<版本>/runclient_launch.log           大小 + mtime
      · run/<版本>/logs/kubejs/server.log         大小 + mtime

    每隔 -PollSeconds 打一行心跳（`MT_WATCHDOG: ALIVE …`），让外层（人或代理）**看得见存活**；
    超过 -StallSeconds 无进展 ⇒ `MT_WATCHDOG: STALL` + 最后一个进展信号 + `latest.log` /
    `kubejs/server.log` 的**尾部若干行**（诊断用），然后：

      -Action report（默认）→ 只报告，退出码 **42**（= 检出停滞，与 FAIL/ERROR 都不同）
      -Action stop          → 先报告，再调 `mt.ps1 --phase stop --force` 收停，退出码 42

    **安全**：收停**只**通过 `mt.ps1 --phase stop --force`（唯一收停实现，按进程标记只杀
    本流程的客户端与 Gradle 守护）。本脚本**绝不**自己 taskkill 任何 java 进程。

.EXAMPLE
    # 包住一次全流程（推荐：另开一个终端/作业跑它）
    pwsh -NoProfile -File scripts/test/mt_watchdog.ps1 -Version 1.21.1 -StallSeconds 360
    # 检出停滞就自动收停
    pwsh -NoProfile -File scripts/test/mt_watchdog.ps1 -Version 1.20.1 -Action stop

.NOTES
    退出码：0 = 观察窗内始终有进展（未检出停滞）；42 = 检出停滞；2 = 参数/引擎错误。
#>

$ErrorActionPreference = 'Stop'

$script:LibDir = Join-Path $PSScriptRoot 'lib'
Import-Module (Join-Path $script:LibDir 'Mt.Phase.psm1')
Import-Module (Join-Path $script:LibDir 'Mt.Paths.psm1')
Import-Module (Join-Path $script:LibDir 'Mt.Proc.psm1')

Initialize-MtConsole

$script:ExitStall = 42
$script:TestDir = Get-MtTestDir
$script:MaxWatchSeconds = 0     # 0 = 一直看（由调用方/人决定何时停）

# ── 进展信号 ──────────────────────────────────────────────────────────────
function Get-MtWatchSignal {
    <#
    .SYNOPSIS
        采集一次进展信号；返回 @(签名, 最后一个非空信号的「文件@时间」文本)。

    .NOTES
        签名 = 各项的「存在性 + 大小 + mtime(秒)」拼接。任何一项变化都会改变签名 ⇒
        重置停滞计时。刻意**不用**哈希（日志会一直变，哈希比大小+mtime 贵且无增益）。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Version)

    $p = Get-MtPaths -Version $Version
    $items = [ordered]@{
        'cases/.mt_run_state.json' = Join-Path (Join-Path $script:TestDir 'cases') '.mt_run_state.json'
        'cases/.mt_active_run'     = Join-Path (Join-Path $script:TestDir 'cases') '.mt_active_run'
        'logs/latest.log'          = $p.latest_log
        'runclient_launch.log'     = (Join-Path $p.run_dir 'runclient_launch.log')
        'kubejs/server.log'        = $p.kubejs_log
    }

    # 当前活动报告目录（reports/<run_id>/）下的全部文件
    $runId = ''
    try {
        $runIdFile = Join-Path (Join-Path $script:TestDir 'cases') '.mt_active_run'
        if (Test-Path -LiteralPath $runIdFile -PathType Leaf) {
            $runId = (Get-Content -LiteralPath $runIdFile -Raw -Encoding UTF8).Trim()
        }
    } catch { $runId = '' }
    if ($runId) {
        $reportDir = Join-Path (Join-Path (Join-Path $script:TestDir 'reports') $runId) $Version
        if (Test-Path -LiteralPath $reportDir -PathType Container) {
            foreach ($f in @(Get-ChildItem -LiteralPath $reportDir -Recurse -File -ErrorAction SilentlyContinue | Sort-Object FullName)) {
                $rel = $f.FullName.Substring($reportDir.Length).TrimStart('\', '/')
                $items["reports/$runId/$Version/$rel"] = $f.FullName
            }
        }
    }

    $sig = [System.Text.StringBuilder]::new()
    $lastName = '（无）'
    $lastTime = [DateTime]::MinValue
    foreach ($k in @($items.Keys)) {
        $path = [string]$items[$k]
        $state = 'missing'
        if (Test-Path -LiteralPath $path -PathType Leaf) {
            try {
                $fi = Get-Item -LiteralPath $path
                $state = ("{0}:{1}" -f $fi.Length, $fi.LastWriteTimeUtc.ToString('yyyyMMddHHmmss'))
                if ($fi.LastWriteTime -gt $lastTime) {
                    $lastTime = $fi.LastWriteTime
                    $lastName = ("{0}@{1}" -f $k, $fi.LastWriteTime.ToString('HH:mm:ss'))
                }
            } catch { $state = 'error' }
        }
        [void]$sig.Append("$k=$state;")
    }
    return , @($sig.ToString(), $lastName)
}

function Write-MtWatchTail {
    <#
    .SYNOPSIS
        打印某文件最后 N 行（诊断用；缺文件只提示不报错）。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Label, [Parameter(Mandatory)][string]$Path, [int]$Lines = 15)

    Write-MtLine ("--- $Label ($Path) 末 $Lines 行 ---")
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        Write-MtLine '    （文件不存在）'
        return
    }
    try {
        foreach ($ln in @(Get-Content -LiteralPath $Path -Tail $Lines -ErrorAction Stop)) {
            Write-MtLine ("    {0}" -f ([string]$ln).TrimEnd())
        }
    } catch {
        Write-MtLine ("    （读取失败：{0}）" -f $_.Exception.Message)
    }
}

# ── 入口 ══════════════════════════════════════════════════════════════════
$Version = ''
$StallSeconds = 360
$PollSeconds = 10
$Action = 'report'
$MaxSeconds = 0

$i = 0
while ($i -lt $args.Count) {
    $tok = [string]$args[$i]
    # 归一化：去前导 `-` 后**删掉全部内部连字符**再小写 ⇒ `-StallSeconds` / `--stall-seconds`
    # 两种拼法都吃下（与 mt_inject.ps1 的归一化规则一致 —— 本仓脚本参数拼法不统一，
    # 只按一种写必然有一种调不通，实测踩过）。
    $key = $tok.TrimStart('-').ToLowerInvariant().Replace('-', '')
    if ($key -eq 'version') {
        if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --version 的值'; exit $MT_EXIT_ERROR }
        $Version = [string]$args[$i + 1]; $i += 2
    } elseif ($key -eq 'stallseconds') {
        if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --stall-seconds 的值'; exit $MT_EXIT_ERROR }
        if ([string]$args[$i + 1] -notmatch '^\d+$') { Write-MtErrorLine '--stall-seconds 需要非负整数'; exit $MT_EXIT_ERROR }
        $StallSeconds = [int]$args[$i + 1]; $i += 2
    } elseif ($key -eq 'pollseconds') {
        if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --poll-seconds 的值'; exit $MT_EXIT_ERROR }
        if ([string]$args[$i + 1] -notmatch '^\d+$') { Write-MtErrorLine '--poll-seconds 需要非负整数'; exit $MT_EXIT_ERROR }
        $PollSeconds = [int]$args[$i + 1]; $i += 2
    } elseif ($key -eq 'action') {
        if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --action 的值'; exit $MT_EXIT_ERROR }
        $Action = ([string]$args[$i + 1]).ToLowerInvariant(); $i += 2
    } elseif ($key -eq 'maxseconds') {
        if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --max-seconds 的值'; exit $MT_EXIT_ERROR }
        if ([string]$args[$i + 1] -notmatch '^\d+$') { Write-MtErrorLine '--max-seconds 需要非负整数'; exit $MT_EXIT_ERROR }
        $MaxSeconds = [int]$args[$i + 1]; $i += 2
    } else {
        Write-MtErrorLine "未知参数 $tok"; exit $MT_EXIT_ERROR
    }
}

if (-not $Version) { Write-MtErrorLine '必须指定 --version'; exit $MT_EXIT_ERROR }
if (-not (Assert-MtVersion -Version $Version)) { exit $MT_EXIT_ERROR }
if ($Action -ne 'report' -and $Action -ne 'stop') {
    Write-MtErrorLine ("非法 --action 值 {0}（可选：report stop）" -f $Action); exit $MT_EXIT_ERROR
}
if ($PollSeconds -lt 1) { $PollSeconds = 1 }

$p = Get-MtPaths -Version $Version
$started = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$lastChange = $started

$sigPair = Get-MtWatchSignal -Version $Version
$lastSig = [string]$sigPair[0]
$lastSignalText = [string]$sigPair[1]

Start-MtPhase "watchdog ($Version)"
Write-MtInfo ("WATCHDOG: 启动 version={0} stall={1}s poll={2}s action={3}" -f $Version, $StallSeconds, $PollSeconds, $Action)
Write-MtInfo ("WATCHDOG: 初始进展信号 {0}" -f $lastSignalText)

while ($true) {
    Start-Sleep -Seconds $PollSeconds
    if ($MaxSeconds -gt 0 -and ([DateTimeOffset]::UtcNow.ToUnixTimeSeconds() - $started) -ge $MaxSeconds) {
        Write-MtInfo ("WATCHDOG: 观察窗 {0}s 到期，未检出停滞 —— 退出 0" -f $MaxSeconds)
        exit 0
    }

    $sigPair = Get-MtWatchSignal -Version $Version
    $sig = [string]$sigPair[0]
    $sigText = [string]$sigPair[1]
    $now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $elapsed = $now - $started

    if ($sig -ne $lastSig) {
        $lastSig = $sig
        $lastSignalText = $sigText
        $lastChange = $now
        Write-MtLine ("MT_WATCHDOG: ALIVE t={0}s last_signal={1}（有进展）" -f $elapsed, $sigText)
        continue
    }

    $stalled = $now - $lastChange
    Write-MtLine ("MT_WATCHDOG: ALIVE t={0}s last_signal={1}（停滞 {2}s）" -f $elapsed, $lastSignalText, $stalled)

    if ($stalled -lt $StallSeconds) { continue }

    # ── 检出停滞 ─────────────────────────────────────────────────────────
    Write-MtErrLine ''
    Write-MtErrLine ("MT_WATCHDOG: STALL — 已 {0}s 无任何进展（阈值 {1}s）" -f $stalled, $StallSeconds)
    Write-MtErrLine ("MT_WATCHDOG: STALL last_signal={0}" -f $lastSignalText)
    Write-MtLine ''
    Write-MtLine '===== 诊断尾部 ====='
    Write-MtWatchTail -Label 'latest.log' -Path $p.latest_log -Lines 20
    Write-MtWatchTail -Label 'kubejs/server.log' -Path $p.kubejs_log -Lines 20
    Write-MtWatchTail -Label 'runclient_launch.log' -Path (Join-Path $p.run_dir 'runclient_launch.log') -Lines 12
    Write-MtLine '===================='

    if ($Action -eq 'stop') {
        Write-MtErrLine 'MT_WATCHDOG: 按 -Action stop 调 mt.ps1 --phase stop --force 收停（唯一收停实现）'
        $psExe = (Get-Process -Id $PID).Path
        $rc = Invoke-MtProcessFull -FilePath $psExe -ArgumentList @(
            '-NoProfile', '-File', (Join-Path $script:TestDir 'mt.ps1'), '--phase', 'stop', '--force'
        ) -TimeoutSec 300
        foreach ($ln in @(([string]$rc.StdOut) -split "`n")) {
            if (("" + $ln).Trim()) { Write-MtLine ([string]$ln) }
        }
        if (([string]$rc.StdErr).Trim()) { Write-MtErrLine (([string]$rc.StdErr).Trim()) }
    } else {
        Write-MtErrLine 'MT_WATCHDOG: 仅报告（-Action report）；如需自动收停请用 -Action stop'
    }
    exit $script:ExitStall
}
