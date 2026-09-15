#!/usr/bin/env pwsh
#Requires -Version 7.0
<#
.SYNOPSIS
    mt_watchdog — 长流程的**独立看门狗**：盯「有没有**实际**进展」，停滞即报警/收停。

.DESCRIPTION
    存在的理由（2026-09-15 实测）：本轮有**三位执行者卡死**在「启动客户端 + 跑用例」——
    表现形式都是「脚本还活着，但再也没有任何输出」。工具链本身没有任何"无进展"判定，
    于是只能靠人盯着。

    ⚠️ **2026-09-16 判据重写（必须理解，否则会重蹈覆辙）**：旧判据把
    `run/<版本>/runclient_launch.log` / `logs/latest.log` 的**大小 + mtime** 当作进展信号。
    实测事故证明它是**假阳性制造机**：游戏完全空闲时，ModernFix 仍会**每 60 秒**往控制台
    吐一条 `[Worker-ResourceReload-N/DEBUG] … shutdown`，文件大小与 mtime 一直在变 ⇒
    看门狗一路报 `ALIVE（有进展）`，而真实情况是 cases 阶段**零输出空转了 7 分 45 秒**。

    现判据分三层（任一层变化即重置停滞计时）：

      ① **进度信标** `cases/.mt_progress.json` —— mt.ps1 / mt_case.ps1 在**每次阶段、每条
         用例、每个步骤开跑前**写入「阶段 / 版本 / 用例 / 第几步 / 什么 op / 开始时刻」。
         这是最强判据：它一变就说明流程真的往前走了；它不变就一直报同一行卡点。
      ② 状态文件：`cases/.mt_run_state.json`、`cases/.mt_snapshot.json`、`cases/.mt_active_run`、
         当前活动报告目录下的任何文件。
      ③ **语义标记**（从 `logs/latest.log` 与 `runclient_launch.log` 的**尾部**扫，命中的行拼成签名）：
         `[CHAT]`、`AP_*:` 读数、`MT_*` 驱动进展行（`MT_INJECT_CMD:` / `MT_SNAPSHOT:` / `MT_LAUNCH:` …）、
         `Saving and pausing`（注入归一化的必要副作用）、`logged in/out`、`joined/left the game`、
         `KubeJS Server/`、`已将截图保存为`。
         **刻意排除 DEBUG 噪声与「文件 mtime」本身** —— 那正是旧判据被骗的地方。
         ⚠️ `MT_*` 那一族**必须**保留：launch 阶段游戏加载时 `latest.log` 会长时间只有
         ModernFix 的 DEBUG 行，真正证明"在干活"的就是 `runclient_launch.log` 里的 MT_ 行
         （2026-09-16 实测：漏掉它们会把一次正常的 47 s 启动误判成停滞、进而误杀客户端）。

    每隔 -PollSeconds 打一行心跳，**并带上当前卡点**（阶段/用例/第几步/最后语义标记的年龄），
    让外层（人或代理）一眼看出"它现在到底在干什么、卡在哪"：

      MT_WATCHDOG: ALIVE t=120s step=cases GUIDE-…-1.21.1 3/12 inject_command | marker=AP_G1_GUIDE age=8s client=alive

    · 停滞达 -WarnSeconds ⇒ 打一行 `MT_WATCHDOG: WARN`（**提前预警**，不退出，附卡点 + 尾部）；
    · 停滞达 -StallSeconds ⇒ `MT_WATCHDOG: STALL` + 完整取证（进度信标原文、客户端/守护进程
      存活与 PID、三个日志的尾部），然后按 -Action 处理：
        -Action report（默认）→ 只报告，退出码 **42**
        -Action stop          → 先报告，再调 `mt.ps1 --phase stop --force` 收停，退出码 42
      ⚠️ **只有客户端阶段（launch / cases）才会自动收停**：preflight / build / env / report / stop
      阶段本来就没有客户端，且 `mt.ps1` 对每个阶段都有硬预算 —— 在那里收停只会误杀一次正常的构建。
    · **没有驱动进程**（`mt.ps1` / `mt_case.ps1` … 都不在）**且**客户端不在、再无进展达
      -IdleExitSeconds ⇒ 认为「被观察的运行已结束」，打印一行并**退出 0**
      （旧版此时会以一个莫名其妙的退出码结束，让人误判成故障）。只看客户端是不够的：
      build/env 阶段本来就没有客户端，会被误判成"运行已结束"。

    **安全**：收停**只**通过 `mt.ps1 --phase stop --force`（唯一收停实现，按进程标记只杀
    本流程的客户端与 Gradle 守护）。本脚本**绝不**自己 taskkill 任何 java 进程。

.EXAMPLE
    # 包住一次全流程（推荐：另开一个作业跑它）
    pwsh -NoProfile -File scripts/test/mt_watchdog.ps1 -Version 1.21.1 -StallSeconds 180 -WarnSeconds 90
    # 检出停滞就自动收停
    pwsh -NoProfile -File scripts/test/mt_watchdog.ps1 -Version 1.20.1 -Action stop

.NOTES
    退出码：0 = 观察窗内始终有进展 / 目标已结束；42 = 检出停滞；2 = 参数/引擎错误。
    `-StallSeconds` 默认由 360 收紧为 **180**：配合「每阶段/每步都写进度信标」，180 秒
    足以判定卡死，而旧值 360 秒意味着一次事故最少白等 6 分钟。
#>

$ErrorActionPreference = 'Stop'

$script:LibDir = Join-Path $PSScriptRoot 'lib'
Import-Module (Join-Path $script:LibDir 'Mt.Phase.psm1')
Import-Module (Join-Path $script:LibDir 'Mt.Paths.psm1')
Import-Module (Join-Path $script:LibDir 'Mt.Proc.psm1')

Initialize-MtConsole

$script:ExitStall = 42
$script:TestDir = Get-MtTestDir

# 语义标记白名单（大小写不敏感）。**只**认这些 —— 加入任何 DEBUG/周期性噪声都会让本脚本
# 退化成旧版的「mtime 判据」，即"永远有进展"。
# `MT_*` 一族是**驱动脚本自己的进展行**（`MT_INJECT_CMD:` / `MT_SNAPSHOT:` / `MT_LAUNCH:` /
# `MT_KUBEJS:` …），它们写在 `run/<版本>/runclient_launch.log` 里 —— launch 阶段游戏加载时
# `latest.log` 会长时间只有 ModernFix 的 DEBUG 噪声，而真正证明"在干活"的正是这些 MT_ 行
# （2026-09-16 实测：漏掉它们会把一次正常的 47s 启动误判成停滞）。
$script:MarkerPattern = '\[CHAT\]|AP_[A-Za-z0-9_]+:|\bMT_[A-Z][A-Za-z0-9_]*|' +
'Saving and pausing|logged in|logged out|joined the game|left the game|KubeJS Server/|已将截图保存为|screenshot'

function Get-MtWatchMarkers {
    <#
    .SYNOPSIS
        从若干日志文件尾部取「语义标记」签名（命中行拼接；无文件/未命中返回空串）。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string[]]$Path, [int]$Lines = 80)

    $hits = @()
    foreach ($p in $Path) {
        if (-not $p) { continue }
        if (-not (Test-Path -LiteralPath $p -PathType Leaf)) { continue }
        try {
            $tap = @(Get-Content -LiteralPath $p -Tail $Lines -ErrorAction Stop)
        } catch {
            continue
        }
        foreach ($ln in $tap) {
            $s = [string]$ln
            if ($s -match $script:MarkerPattern) { $hits += $s.Trim() }
        }
    }
    return ($hits -join "`n")
}

function Get-MtDriverProcs {
    <#
    .SYNOPSIS
        正在驱动测试的 pwsh 进程（mt.ps1 / mt_*.ps1，排除本看门狗自身）。

    .NOTES
        用途：区分「被观察的运行真的结束了」与「当前阶段本来就没有客户端」——
        build/env 阶段**没有客户端**是正常的，旧逻辑会把它们当成"运行已结束"而提前退出。
    #>
    [CmdletBinding()]
    param()

    $pat = 'mt\.ps1|mt_case\.ps1|mt_env\.ps1|mt_launch\.ps1|mt_build\.ps1|mt_report\.ps1|' +
    'mt_cleanup\.ps1|mt_stop\.ps1|mt_assert\.ps1|mt_inject\.ps1|mt_capture\.ps1|mt_preflight\.ps1'
    $out = @()
    try {
        $out = @(Get-CimInstance Win32_Process -Filter "Name='pwsh.exe'" -ErrorAction Stop |
            Where-Object { $_.ProcessId -ne $PID -and ([string]$_.CommandLine) -match $pat })
    } catch { $out = @() }
    return $out
}

function Get-MtWatchSignal {
    <#
    .SYNOPSIS
        采集一次进展信号；返回 @(签名, 给心跳用的「当前卡点」文本, 客户端/守护进程存活信息)。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Version)

    $p = Get-MtPaths -Version $Version
    $items = [ordered]@{
        'cases/.mt_progress.json'  = Get-MtProgressFile
        'cases/.mt_run_state.json' = Join-Path (Join-Path $script:TestDir 'cases') '.mt_run_state.json'
        'cases/.mt_snapshot.json'  = Join-Path (Join-Path $script:TestDir 'cases') '.mt_snapshot.json'
        'cases/.mt_active_run'     = Join-Path (Join-Path $script:TestDir 'cases') '.mt_active_run'
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

    # ① 进度信标：内容整体入签名（它每次变化都意味着「流程往前走了」）
    $prog = Get-MtProgress
    $progText = '（无进度信标）'
    if ($prog) {
        $progText = ("{0} {1}" -f $prog['phase'], $(if ($prog['case']) { "$($prog['case']) $($prog['step_index'])/$($prog['step_total']) $($prog['op'])" } else { $prog['version'] }))
        [void]$sig.Append("progress=$($prog['ts']):$($prog['phase']):$($prog['case']):$($prog['step_index']):$($prog['op']);")
    }

    # ② 状态文件 / 报告文件（存在性 + 大小 + mtime）
    foreach ($k in @($items.Keys)) {
        $path = [string]$items[$k]
        $state = 'missing'
        if (Test-Path -LiteralPath $path -PathType Leaf) {
            try {
                $fi = Get-Item -LiteralPath $path
                $state = ("{0}:{1}" -f $fi.Length, $fi.LastWriteTimeUtc.ToString('yyyyMMddHHmmss'))
            } catch { $state = 'error' }
        }
        [void]$sig.Append("$k=$state;")
    }

    # ③ 语义标记（latest.log + runclient_launch.log 的尾部；DEBUG 噪声天然被白名单挡在外面）
    $markers = Get-MtWatchMarkers -Path @($p.latest_log, (Join-Path $p.run_dir 'runclient_launch.log'))
    [void]$sig.Append("markers=$([int]$markers.Length):$markers")

    # 存活信息（只用于显示，不进签名 —— 进程活着不是进展）
    $aliveClient = 0
    try {
        $markerSet = @(Get-MtProcessMarkers -Paths $p)
        foreach ($jp in @(Get-JavaProcesses)) {
            $cmd = [string]$jp.CommandLine
            foreach ($m in $markerSet) { if ($m -and $cmd.Contains([string]$m)) { $aliveClient++; break } }
        }
    } catch { $aliveClient = -1 }

    $drivers = @(Get-MtDriverProcs)
    $phase = if ($prog) { [string]$prog['phase'] } else { '' }

    $lastMarker = '（无）'
    if ($markers) {
        $ls = @($markers -split "`n" | Where-Object { $_.Trim() })
        if ($ls.Count -gt 0) { $lastMarker = $ls[-1] }
        if ($lastMarker.Length -gt 60) { $lastMarker = $lastMarker.Substring(0, 60) }
    }

    return , @($sig.ToString(), $progText, $lastMarker, $aliveClient, [int]$drivers.Count, $phase)
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

function Write-MtWatchForensics {
    <#
    .SYNOPSIS
        卡点取证：进度信标原文 + 存活进程 + 三个日志尾部。WARN 与 STALL 共用。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Version, [int]$TailLines = 15)

    $p = Get-MtPaths -Version $Version

    $prog = Get-MtProgress
    if ($prog) {
        $age = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds() - [long]$prog['ts']
        Write-MtLine ("MT_WATCHDOG: 进度信标 — 阶段={0} 版本={1} 用例={2} 步骤={3}/{4} op={5} detail={6}（{7}s 前写入）" -f `
                $prog['phase'], $prog['version'], $prog['case'], $prog['step_index'], $prog['step_total'], $prog['op'], $prog['detail'], $age)
    } else {
        Write-MtLine 'MT_WATCHDOG: 进度信标缺失（cases/.mt_progress.json）—— 该阶段可能不是 mt.ps1 发起，或流程尚未开始'
    }

    $ppl = @()
    try {
        $markerSet = @(Get-MtProcessMarkers -Paths $p)
        foreach ($jp in @(Get-JavaProcesses)) {
            $cmd = [string]$jp.CommandLine
            foreach ($m in $markerSet) { if ($m -and $cmd.Contains([string]$m)) { $ppl += $jp; break } }
        }
    } catch { }
    if ($ppl.Count -gt 0) {
        Write-MtLine ("MT_WATCHDOG: 客户端存活 pid={0}" -f (($ppl | ForEach-Object { $_.Pid }) -join ','))
    } else {
        Write-MtLine 'MT_WATCHDOG: 客户端**未**在运行'
    }

    Write-MtWatchTail -Label 'latest.log' -Path $p.latest_log -Lines $TailLines
    Write-MtWatchTail -Label 'kubejs/server.log' -Path $p.kubejs_log -Lines $TailLines
    Write-MtWatchTail -Label 'runclient_launch.log' -Path (Join-Path $p.run_dir 'runclient_launch.log') -Lines 12
}

# ── 入口 ══════════════════════════════════════════════════════════════════
$Version = ''
$StallSeconds = 180
$WarnSeconds = 0          # 0 = 自动取 min(90, Stall/2)
$PollSeconds = 10
$IdleExitSeconds = 60
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
    } elseif ($key -eq 'warnseconds') {
        if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --warn-seconds 的值'; exit $MT_EXIT_ERROR }
        if ([string]$args[$i + 1] -notmatch '^\d+$') { Write-MtErrorLine '--warn-seconds 需要非负整数'; exit $MT_EXIT_ERROR }
        $WarnSeconds = [int]$args[$i + 1]; $i += 2
    } elseif ($key -eq 'idleexitseconds') {
        if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --idle-exit-seconds 的值'; exit $MT_EXIT_ERROR }
        if ([string]$args[$i + 1] -notmatch '^\d+$') { Write-MtErrorLine '--idle-exit-seconds 需要非负整数'; exit $MT_EXIT_ERROR }
        $IdleExitSeconds = [int]$args[$i + 1]; $i += 2
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
if ($WarnSeconds -le 0) {
    $WarnSeconds = [int][Math]::Min(90, [Math]::Max(30, [Math]::Floor($StallSeconds / 2)))
}
if ($WarnSeconds -gt $StallSeconds) { $WarnSeconds = $StallSeconds }

$p = Get-MtPaths -Version $Version
$started = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$lastChange = $started
$warned = $false
$phaseWarned = $false
$idleSince = 0

$sigPair = Get-MtWatchSignal -Version $Version
$lastSig = [string]$sigPair[0]
$lastProg = [string]$sigPair[1]

Start-MtPhase "watchdog ($Version)"
Write-MtInfo ("WATCHDOG: 启动 version={0} stall={1}s warn={2}s poll={3}s idle-exit={4}s action={5}" -f `
        $Version, $StallSeconds, $WarnSeconds, $PollSeconds, $IdleExitSeconds, $Action)
Write-MtInfo ("WATCHDOG: 判据 = 进度信标 cases/.mt_progress.json + 状态/报告文件 + latest.log 的**语义标记**（不再用日志 mtime；DEBUG 周期噪声不算进展）")
Write-MtInfo ("WATCHDOG: 初始卡点 {0}" -f $lastProg)

while ($true) {
    Start-Sleep -Seconds $PollSeconds
    $now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $elapsed = $now - $started

    if ($MaxSeconds -gt 0 -and $elapsed -ge $MaxSeconds) {
        Write-MtInfo ("WATCHDOG: 观察窗 {0}s 到期，未检出停滞 —— 退出 0" -f $MaxSeconds)
        exit 0
    }

    $sigPair = Get-MtWatchSignal -Version $Version
    $sig = [string]$sigPair[0]
    $progText = [string]$sigPair[1]
    $lastMarker = [string]$sigPair[2]
    $aliveClient = [int]$sigPair[3]
    $drivers = [int]$sigPair[4]
    $phase = [string]$sigPair[5]

    if ($sig -ne $lastSig) {
        $lastSig = $sig
        $lastProg = $progText
        $lastChange = $now
        $warned = $false
        $phaseWarned = $false
        $idleSince = 0
        Write-MtLine ("MT_WATCHDOG: ALIVE t={0}s step={1} | marker={2} | client={3} driver={4}（有进展）" -f `
                $elapsed, $progText, $lastMarker, $(if ($aliveClient -gt 0) { 'alive' } elseif ($aliveClient -eq 0) { 'none' } else { '?' }), $drivers)
        continue
    }

    $stalled = $now - $lastChange

    # 目标已结束：**没有驱动进程**、客户端也不在，且再无进展 ⇒ 干净收尾。
    # 为什么必须同时看「驱动进程」：build/env 阶段本来就没有客户端（那是正常的），
    # 只看客户端会把它们误判成"运行已结束"而提前退出（2026-09-16 修）。
    if ($aliveClient -le 0 -and $drivers -eq 0) {
        if ($idleSince -eq 0) { $idleSince = $now }
        if (($now - $idleSince) -ge $IdleExitSeconds) {
            Write-MtInfo ("WATCHDOG: 驱动进程与客户端均已退出、且 {0}s 无新进展 —— 认为运行已结束，退出 0" -f ($now - $idleSince))
            exit 0
        }
    } else {
        $idleSince = 0
    }

    Write-MtLine ("MT_WATCHDOG: ALIVE t={0}s step={1} | marker={2} | client={3} driver={4}（停滞 {5}s）" -f `
            $elapsed, $lastProg, $lastMarker, $(if ($aliveClient -gt 0) { 'alive' } elseif ($aliveClient -eq 0) { 'none' } else { '?' }), $drivers, $stalled)

    if (-not $warned -and $stalled -ge $WarnSeconds) {
        $warned = $true
        Write-MtWarn ''
        Write-MtWarn ("MT_WATCHDOG: WARN — 已 {0}s 无进展（预警阈值 {1}s，收停阈值 {2}s）" -f $stalled, $WarnSeconds, $StallSeconds)
        Write-MtWatchForensics -Version $Version -TailLines 8
        Write-MtWarn ''
    }

    if ($stalled -lt $StallSeconds) { continue }

    # 只有**客户端阶段**（launch / cases）的停滞才自动收停：preflight / build / env / report /
    # stop 阶段本来就没有客户端，且 mt.ps1 对每个阶段都有硬预算 ⇒ 在这里收停只会把一次
    # 正常的 3 分钟构建误杀（2026-09-16 修）。
    $stallActionable = (@('launch', 'cases') -contains $phase) -or (($phase -eq '') -and ($aliveClient -gt 0))
    if (-not $stallActionable) {
        if (-not $phaseWarned) {
            $phaseWarned = $true
            Write-MtWarn ("MT_WATCHDOG: 停滞已达 {0}s，但当前阶段 '{1}' 不是客户端阶段 —— 交给 mt.ps1 的硬预算处理，**不自动收停**" -f $stalled, $phase)
        }
        continue
    }

    # ── 检出停滞 ─────────────────────────────────────────────────────────
    Write-MtErrLine ''
    Write-MtErrLine ("MT_WATCHDOG: STALL — 已 {0}s 无任何进展（阈值 {1}s）" -f $stalled, $StallSeconds)
    Write-MtErrLine ("MT_WATCHDOG: STALL 卡点={0} 最后语义标记={1}" -f $lastProg, $lastMarker)
    Write-MtLine ''
    Write-MtLine '===== 诊断取证 ====='
    Write-MtWatchForensics -Version $Version -TailLines 20
    Write-MtLine '===================='

    if ($Action -eq 'stop') {
        Write-MtErrLine 'MT_WATCHDOG: 按 -Action stop 调 mt.ps1 --phase stop --force 收停（唯一收停实现）'
        $psExe = (Get-Process -Id $PID).Path
        $rc = Invoke-MtProcessFull -FilePath $psExe -ArgumentList @(
            '-NoProfile', '-File', (Join-Path $script:TestDir 'mt.ps1'), '--phase', 'stop', '--force'
        ) -TimeoutSec 180
        foreach ($ln in @(([string]$rc.StdOut) -split "`n")) {
            if (("" + $ln).Trim()) { Write-MtLine ([string]$ln) }
        }
        if (([string]$rc.StdErr).Trim()) { Write-MtErrLine (([string]$rc.StdErr).Trim()) }
    } else {
        Write-MtErrLine 'MT_WATCHDOG: 仅报告（-Action report）；如需自动收停请用 -Action stop'
    }
    exit $script:ExitStall
}
