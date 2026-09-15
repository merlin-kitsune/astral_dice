#!/usr/bin/env pwsh
#Requires -Version 7.0
<#
.SYNOPSIS
    mt_fire — 「点火」入口：把耗时的测试阶段**脱离调用方的 stdio** 启动，调用方立即返回。

.DESCRIPTION
    **为什么必须有这个脚本（2026-09-16 实测根因，最重要的一条）**

    症状：`mt.ps1 --phase launch` 明明在 48 秒内就打出 `MT_LAUNCH: OK`（脱离式子日志为证），
    但**调用方**（代理工具调用、后台作业、被捕获的管道）要等到 **240 秒被强杀**才返回，
    甚至"永远不返回"。连续三次事故（含一次被记成「launch 后 cases 空转 7 分 45 秒」）都是它。

    机理：`launch` 会留下**长驻孙进程**（Gradle 守护 `GradleDaemon` 与 Minecraft 客户端），
    它们**继承**了调用方 stdout 的写端句柄。调用方的 `pwsh` 早已退出，但读端在等 EOF ——
    而只要还有一个进程持有那个写端，EOF 就永远不来 ⇒ 调用方看起来"卡死"。
    这与被测产品的行为无关，纯粹是 Windows 句柄继承 + 管道语义。

    做法：本脚本用 `Start-Process` 启动 `mt.ps1`，并把它（及其全部后代）的
    stdout/stderr **重定向到文件**、`-WindowStyle Hidden`，于是**没有任何人**持有调用方的管道；
    本脚本打印 pid 与日志路径后**立即返回**。

    之后由调用方**轮询状态**（推荐顺序，全部是廉价文件读）：
      1. `scripts/test/cases/.mt_progress.json` —— 进度信标（阶段/用例/第几步/什么 op）
      2. `MT_FIRE` 打印的 `.out` 日志（阶段自身的输出）
      3. `run/<版本>/logs/latest.log` —— 游戏侧语义标记
    需要等待时可加 `--wait <秒>`：本脚本会在**硬上限内**打心跳并按时返回（绝不无限等）。

.PARAMETER Version
    1.21.1 / 1.20.1

.PARAMETER Phase
    preflight | build | env | launch | cases | report | stop

.PARAMETER Case
    仅 `cases` 阶段使用；单条用例 json 路径（可省，省则跑整个目录）

.PARAMETER Wait
    启动后最多等待多少秒再返回（默认 0 = 立即返回）。等待期间的进展判据与 mt_watchdog 一致：
    进度信标 + 日志语义标记；到点即返回，不做任何收停动作。

.EXAMPLE
    # 点火后立即返回（推荐；随后自己轮询）
    pwsh -NoProfile -File scripts/test/mt_fire.ps1 --version 1.21.1 --phase launch
    # 点火并最多等 120 秒
    pwsh -NoProfile -File scripts/test/mt_fire.ps1 --version 1.21.1 --phase cases --case cases/X.json --wait 120

.NOTES
    退出码：0 = 已成功点火（进程已启动）；2 = 参数错误；3 = 等待期结束但进程仍在跑（信息性，不是失败）。
    **不要**用本脚本去收停；收停仍走 `mt.ps1 --phase stop --force`。
#>

$ErrorActionPreference = 'Stop'

$script:LibDir = Join-Path $PSScriptRoot 'lib'
Import-Module (Join-Path $script:LibDir 'Mt.Phase.psm1')
Import-Module (Join-Path $script:LibDir 'Mt.Paths.psm1')
Import-Module (Join-Path $script:LibDir 'Mt.Proc.psm1')

Initialize-MtConsole

$Version = ''
$Phase = ''
$CasePath = ''
$WaitSeconds = 0

$i = 0
while ($i -lt $args.Count) {
    $tok = [string]$args[$i]
    $key = $tok.TrimStart('-').ToLowerInvariant().Replace('-', '')
    if ($key -eq 'version') {
        if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --version 的值'; exit $MT_EXIT_ERROR }
        $Version = [string]$args[$i + 1]; $i += 2
    } elseif ($key -eq 'phase') {
        if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --phase 的值'; exit $MT_EXIT_ERROR }
        $Phase = [string]$args[$i + 1]; $i += 2
    } elseif ($key -eq 'case') {
        if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --case 的值'; exit $MT_EXIT_ERROR }
        $CasePath = [string]$args[$i + 1]; $i += 2
    } elseif ($key -eq 'wait') {
        if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --wait 的值'; exit $MT_EXIT_ERROR }
        if ([string]$args[$i + 1] -notmatch '^\d+$') { Write-MtErrorLine '--wait 需要非负整数（秒）'; exit $MT_EXIT_ERROR }
        $WaitSeconds = [int]$args[$i + 1]; $i += 2
    } else {
        Write-MtErrorLine "未知参数 $tok"; exit $MT_EXIT_ERROR
    }
}

if (-not $Version) { Write-MtErrorLine '必须指定 --version'; exit $MT_EXIT_ERROR }
if (-not $Phase) { Write-MtErrorLine '必须指定 --phase'; exit $MT_EXIT_ERROR }
if (-not (Assert-MtVersion -Version $Version)) { exit $MT_EXIT_ERROR }

$root = Get-MtRoot
$logDir = Join-Path (Join-Path $root 'temp') 'mtlogs'
if (-not (Test-Path -LiteralPath $logDir)) { [void](New-Item -ItemType Directory -Force -Path $logDir) }

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$outLog = Join-Path $logDir ("fire_{0}_{1}_{2}.out" -f $Phase, $Version, $stamp)
$errLog = "$outLog.err"

$argv = @('-NoProfile', '-File', (Join-Path $PSScriptRoot 'mt.ps1'), '--version', $Version, '--phase', $Phase)
if ($CasePath) { $argv += @('--case', $CasePath) }

$psExe = (Get-Process -Id $PID).Path
$proc = Start-Process -FilePath $psExe -ArgumentList (ConvertTo-MtStartArgs -ArgumentList $argv) `
    -PassThru -WindowStyle Hidden -WorkingDirectory $root `
    -RedirectStandardOutput $outLog -RedirectStandardError $errLog

Write-MtLine ''
Write-MtLine ("MT_FIRE: pid={0} phase={1} version={2} log={3}" -f $proc.Id, $Phase, $Version, $outLog)
Write-MtLine ("MT_FIRE: 已脱离调用方 stdio（避免长驻孙进程持有调用方管道 ⇒ 调用方永不返回）")

if ($WaitSeconds -le 0) {
    Write-MtLine 'MT_FIRE: 立即返回（--wait 0）—— 请自行轮询 cases/.mt_progress.json 与该日志'
    exit 0
}

# ── 可选的有限等待（带上限、带心跳，绝不无限等）────────────────────────────
$p = Get-MtPaths -Version $Version
$pattern = '\[CHAT\]|AP_[A-Za-z0-9_]+:|\bMT_[A-Z][A-Za-z0-9_]*|Saving and pausing|KubeJS Server/'
$started = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$lastBeat = 0
$lastSig = ''

while ($true) {
    $now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $elapsed = $now - $started
    if ($elapsed -ge $WaitSeconds) {
        Write-MtLine ("MT_FIRE: 等待窗 {0}s 到期（进程 pid={1} {2}）—— 返回，不做收停" -f `
                $WaitSeconds, $proc.Id, $(if ($proc.HasExited) { "已退出 rc=$($proc.ExitCode)" } else { '仍在运行' }))
        if ($proc.HasExited) { exit $proc.ExitCode }
        exit 3
    }

    if ($proc.HasExited) {
        Write-MtLine ("MT_FIRE: 进程已退出 rc={0}（耗时 {1}s）" -f $proc.ExitCode, $elapsed)
        exit $proc.ExitCode
    }

    if (($elapsed - $lastBeat) -ge 10) {
        $lastBeat = $elapsed
        $prog = Get-MtProgress
        $stepText = if ($prog) { "{0} {1} {2}/{3} {4}" -f $prog['phase'], $prog['case'], $prog['step_index'], $prog['step_total'], $prog['op'] } else { '（无信标）' }
        # 语义标记（从阶段日志与游戏日志各取尾部）
        $sig = ''
        foreach ($f in @($outLog, $p.latest_log)) {
            if (-not (Test-Path -LiteralPath $f -PathType Leaf)) { continue }
            try {
                foreach ($ln in @(Get-Content -LiteralPath $f -Tail 40 -ErrorAction Stop)) {
                    if (([string]$ln) -match $pattern) { $sig += ([string]$ln).Trim() + "`n" }
                }
            } catch { }
        }
        if ($sig -ne $lastSig) { $lastSig = $sig }
        Write-MtLine ("MT_FIRE: 等待 {0}s/{1}s step={2} log={3}B" -f `
                $elapsed, $WaitSeconds, $stepText, $(if (Test-Path -LiteralPath $outLog) { (Get-Item -LiteralPath $outLog).Length } else { 0 }))
    }
    Start-Sleep -Milliseconds 1000
}
