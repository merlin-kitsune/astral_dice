#Requires -Version 7.0
<#
.SYNOPSIS
    ft_launch — 启动 fabric-1.20.1 线的客户端/服务端（fabric 测试台的 L 阶段）。

.DESCRIPTION
    启动方式：`gradlew :fabric-1.20.1:runServer` / `runClient`（依据 = fabric-1.20.1/build.gradle:172-182
    的 `runs { client { client(); runDir 'run/client' } server { server(); runDir 'run/server' } }`；
    Loom 的 `runDir` 相对**子项目**解析 ⇒ 实际运行目录 = `fabric-1.20.1/run/{client,server}`）。

    就绪判定（**只认启动之后新写入的日志**，用字节游标锚定，不认历史行）：
      服务端：`Done (0.307s)! For help, type "help"`（实测 run/server/logs/latest.log:213）
      客户端：`Sound engine started`（实测 run/client/logs/latest.log:372）

    与生产线 mt_launch.ps1 的差异（本台 v1 明确不做，见 README）：
      · 不做「清场」（`/kill @e`）与「禁用生物 AI」硬闸门 —— 需要时经 ft_inject 的 rcon 通道手动下发；
      · 不做兼容栈/模组来源/可写性的 preflight —— 由 ft_env.ps1 承担前置判定；
      · 不做输入法切换（无 Win32 注入通道）。

.PARAMETER Side
    client | server。**必须指定单一 side**：`--side both`（顺序启动两条实例）属「批量编排」，被闸门拦下。

.PARAMETER Fg
    前台执行（直接等 gradlew 返回，返回其退出码）。默认后台：起进程 + 轮询就绪 + 写状态文件。

.PARAMETER Timeout
    就绪等待上限秒数（默认 300）。

.PARAMETER NoWait
    后台模式下不等待就绪，起进程即返回。

.PARAMETER AllowAuto
    放行批量编排闸门。

.EXAMPLE
    pwsh -NoProfile -File scripts/test/fabric/ft_launch.ps1 --side server
    pwsh -NoProfile -File scripts/test/fabric/ft_launch.ps1 --side client --timeout 420
    pwsh -NoProfile -File scripts/test/fabric/ft_launch.ps1 --side server --fg

.NOTES
    状态文件：`scripts/test/fabric/.ft_launch_state.json`（供 ft_stop.ps1 精确收停）。
    输出（stdout）：AP_FAB_LAUNCH: OK|FAIL|BLOCKED …；退出码 0 / 12(超时) / 2(ERROR)。
#>

$ErrorActionPreference = 'Stop'

$script:FtLibDir = Join-Path $PSScriptRoot 'lib'
Import-Module (Join-Path $script:FtLibDir 'Ft.Common.psm1') -Force

Initialize-FtConsole

# ══ 参数 ══════════════════════════════════════════════════════════════════
$Side = ''
$Fg = $false
$TimeoutSec = 300
$NoWait = $false
$ArgList = @($args)

$i = 0
while ($i -lt $args.Count) {
    $tok = [string]$args[$i]
    $key = Get-FtArgKey -Token $tok
    if ($key -eq 'side') {
        if ($i + 1 -ge $args.Count) { Write-FtErrorLine '缺少 --side 的值'; exit $FT_EXIT_ERROR }
        $Side = ([string]$args[$i + 1]).ToLowerInvariant(); $i += 2
    } elseif ($key -eq 'fg' -or $key -eq 'foreground') {
        $Fg = $true; $i++
    } elseif ($key -eq 'nowait') {
        $NoWait = $true; $i++
    } elseif ($key -eq 'timeout') {
        if ($i + 1 -ge $args.Count) { Write-FtErrorLine '缺少 --timeout 的值'; exit $FT_EXIT_ERROR }
        if ([string]$args[$i + 1] -notmatch '^\d+$') { Write-FtErrorLine '--timeout 需要非负整数（秒）'; exit $FT_EXIT_ERROR }
        $TimeoutSec = [int]$args[$i + 1]; $i += 2
    } elseif ($key -eq 'allowauto') {
        $i++
    } elseif ($key -eq 'h' -or $key -eq 'help') {
        Write-FtLine '用法: ft_launch.ps1 --side client|server [--fg] [--no-wait] [--timeout N]'
        exit $FT_EXIT_PASS
    } else {
        Write-FtErrorLine "未知参数 $tok"; exit $FT_EXIT_ERROR
    }
}

if (-not $Side) { Write-FtErrorLine '必须指定 --side client|server（不提供默认值：隐式双启动＝批量编排）'; exit $FT_EXIT_ERROR }
if ($Side -ne 'client' -and $Side -ne 'server' -and $Side -ne 'both') {
    Write-FtErrorLine "非法 --side '$Side'（只接受 client | server）"; exit $FT_EXIT_ERROR
}

# 🚫 闸门：--side both（顺序启动 client+server 两条实例）＝ fabric 侧的「批量编排」等价物
if ($Side -eq 'both') {
    Assert-FtAutoGate -What '--side both（会顺序启动 client 与 server 两条运行实例）' -ArgList $ArgList
    # 走到这里只可能是在 --allow-auto 下被放行 —— 本台仍不支持：两条实例共用 gradle daemon，
# 就绪判定与收停都会互相干扰；要两条就分两次单 side 调用。
    Write-FtErrorLine '--side both 未实现（即使放行也不支持）：请分两次单 side 调用。'
    exit $FT_EXIT_ERROR
}
[void](Assert-FtSide -Side $Side)

$root = Get-FtRepoRoot
$sub = Get-FtSubprojectRoot
$sideDir = Get-FtSideDir -Side $Side
$logPath = Get-FtLogPath -Side $Side
$gradlewBat = Join-Path $root 'gradlew.bat'

if (-not (Test-Path -LiteralPath $gradlewBat -PathType Leaf)) {
    Write-FtErrorLine "找不到 gradlew.bat：$gradlewBat"
    Write-FtLine 'AP_FAB_LAUNCH: FAIL (reason=no-gradlew)'
    exit $FT_EXIT_ERROR
}
if (-not (Test-Path -LiteralPath $sideDir -PathType Container)) {
    [void](New-Item -ItemType Directory -Force -Path $sideDir)
}

# 启动前：清空 KubeJS 注入队列（观察者重启后内存态归零，残留行会被重放）
$queue = Join-Path $sideDir 'kubejs\.ft_cmd_queue.txt'
if (Test-Path -LiteralPath $queue -PathType Leaf) {
    [System.IO.File]::WriteAllText($queue, '', [System.Text.UTF8Encoding]::new($false))
}

$task = ":fabric-1.20.1:run$($Side.Substring(0,1).ToUpperInvariant())$($Side.Substring(1))"
$readyPattern = if ($Side -eq 'server') { 'Done \(\d+(\.\d+)?s\)! For help' } else { 'Sound engine started' }

Write-FtLine ("AP_FAB_LAUNCH_TASK: side={0} task={1} runDir={2}" -f $Side, $task, $sideDir)

# ══ 前台 ══════════════════════════════════════════════════════════════════
if ($Fg) {
    & cmd.exe /c $gradlewBat $task --console=plain
    $rc = $LASTEXITCODE
    Write-FtLine ("AP_FAB_LAUNCH: FINISHED (rc={0})" -f $rc)
    if ($rc -eq 0) { exit $FT_EXIT_PASS } else { exit $FT_EXIT_ERROR }
}

# ══ 后台 ══════════════════════════════════════════════════════════════════
$tempDir = Join-Path $root 'temp'
if (-not (Test-Path -LiteralPath $tempDir -PathType Container)) { [void](New-Item -ItemType Directory -Force -Path $tempDir) }
$stamp = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$outLog = Join-Path $tempDir "ft_launch_${Side}_${stamp}.out.log"
$errLog = Join-Path $tempDir "ft_launch_${Side}_${stamp}.err.log"

# 就绪游标：只认「本次启动之后新写入」的日志字节（历史行不算，避免假就绪）
$preLen = 0
if (Test-Path -LiteralPath $logPath -PathType Leaf) { $preLen = (Get-Item -LiteralPath $logPath).Length }

$proc = Start-Process -FilePath 'cmd.exe' `
    -ArgumentList @('/c', $gradlewBat, $task, '--console=plain') `
    -WorkingDirectory $root -NoNewWindow -PassThru `
    -RedirectStandardOutput $outLog -RedirectStandardError $errLog

$state = @{
    side      = $Side
    pid       = $proc.Id
    startedAt = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    outLog    = $outLog
    errLog    = $errLog
    runDir    = $sideDir
    task      = $task
}
$statePath = Join-Path (Get-FtSelfDir) '.ft_launch_state.json'
($state | ConvertTo-Json) | Set-Content -LiteralPath $statePath -Encoding utf8
Write-FtLine ("AP_FAB_LAUNCH_PID: side={0} pid={1} state={2}" -f $Side, $proc.Id, $statePath)

if ($NoWait) {
    Write-FtLine ("AP_FAB_LAUNCH: SPAWNED (pid={0}, no-wait)" -f $proc.Id)
    exit $FT_EXIT_PASS
}

$deadline = [DateTimeOffset]::UtcNow.AddSeconds($TimeoutSec)
$ready = $false
$exited = $false
while ([DateTimeOffset]::UtcNow -lt $deadline) {
    if ($proc.HasExited) { $exited = $true; break }

    if (Test-Path -LiteralPath $logPath -PathType Leaf) {
        $len = (Get-Item -LiteralPath $logPath).Length
        if ($len -gt $preLen) {
            $bytes = [System.IO.File]::ReadAllBytes($logPath)
            $enc = [System.Text.UTF8Encoding]::new($false, $false)
            $tail = $enc.GetString($bytes, [int]$preLen, $bytes.Length - [int]$preLen)
            if ($tail -match $readyPattern) { $ready = $true; break }
        }
    }
    Start-Sleep -Milliseconds 1000
}

if ($ready) {
    $off = Save-FtSnapshot -Side $Side -Window 'launch'
    Write-FtLine ("AP_FAB_LAUNCH_READY: side={0} pattern={1} cursor={2}" -f $Side, $readyPattern, $off)
    Write-FtLine 'AP_FAB_LAUNCH: OK'
    Write-FtOk 'LAUNCH' ("side=$Side pid=$($proc.Id) 就绪（窗口起点已冻结）")
    exit $FT_EXIT_PASS
}

if ($exited) {
    $tail = ''
    if (Test-Path -LiteralPath $outLog -PathType Leaf) {
        $t = Read-FtLogText -Path $outLog
        $tail = (@(($t -split "`r?`n") | Select-Object -Last 12) -join ' | ')
    }
    Write-FtLine ("AP_FAB_LAUNCH: FAIL (reason=exited rc={0})" -f $proc.ExitCode)
    Write-FtError 'LAUNCH' ("进程在就绪前退出（rc=$($proc.ExitCode)）：$tail")
    exit $FT_EXIT_ERROR
}

Write-FtLine ("AP_FAB_LAUNCH: FAIL (reason=timeout timeout={0}s)" -f $TimeoutSec)
Write-FtError 'LAUNCH' ("{0}s 内未见就绪标记 /{1}/（还在跑，可继续用 ft_assert 观察；收停用 ft_stop）" -f $TimeoutSec, $readyPattern)
exit $FT_EXIT_TIMEOUT
