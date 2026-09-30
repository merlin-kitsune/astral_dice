#Requires -Version 7.0
<#
.SYNOPSIS
    ft_launch — 启动 fabric-1.20.1 线的客户端/服务端（fabric 测试台的 L 阶段）。

.DESCRIPTION
    启动方式：`gradlew :fabric-1.20.1:runServer` / `runClient`（依据 = fabric-1.20.1/build.gradle:172-182
    的 `runs { client { client(); runDir 'run/client' } server { server(); runDir 'run/server' } }`；
    Loom 的 `runDir` 相对**子项目**解析 ⇒ 实际运行目录 = `fabric-1.20.1/run/{client,server}`）。

    就绪判定（**只认本次启动之后新写入的日志**，用「文件身份锚点」判定，不认历史行）：
      服务端：`Done (0.307s)! For help, type "help"`（实测 run/server/logs/latest.log:213）
      客户端：`Sound engine started`（实测 run/client/logs/debug.log:4686）
      ⚠️ 锚点 = (创建时间, 头部指纹, 长度)，**不是**字节偏移 —— 因为 latest.log 每次冷启动
        都会被 log4j 轮转成 `YYYY-MM-DD-N.log.gz` 并新建，用旧长度做游标会**每次启动都假超时**
        （实测 2026-09-29，见 lib/Ft.Common.psm1#Get-FtLogAnchor 的说明）。

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

.PARAMETER GradleArg
    （可重复）透传给 gradlew 的额外参数。用于本机 dev 环境的**已知冲突**：
      · `--gradle-arg -PtestSodium=false`
        Sodium 要求 LWJGL 3.3.1，而本工程 dev 环境装的是 3.3.2-snapshot ⇒ 不带这个开关，
        客户端会在启动期硬失败（`Installed version: 3.3.2-snapshot`）。该开关是工程 build.gradle
        里**既有的**测试开关（注释写明用于「只为跑一次 GUI 验证」的场景）；跑 tooltip / 渲染类
        验证时排除 Sodium 反而更干净（不被第三方渲染模组干扰）。
      · `--gradle-arg -Pquickplay=<世界名>`（客户端）直接进入指定单机世界。

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
$GradleArgs = New-Object System.Collections.Generic.List[string]
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
    } elseif ($key -eq 'gradlearg') {
        if ($i + 1 -ge $args.Count) { Write-FtErrorLine '缺少 --gradle-arg 的值'; exit $FT_EXIT_ERROR }
        $GradleArgs.Add([string]$args[$i + 1]); $i += 2
    } elseif ($key -eq 'allowauto') {
        $i++
    } elseif ($key -eq 'h' -or $key -eq 'help') {
        Write-FtLine '用法: ft_launch.ps1 --side client|server [--fg] [--no-wait] [--timeout N] [--gradle-arg <x>]…'
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

$task = ":fabric-1.20.1:run$($Side.Substring(0,1).ToUpperInvariant())$($Side.Substring(1))"
$readyPattern = if ($Side -eq 'server') { 'Done \(\d+(\.\d+)?s\)! For help' } else { 'Sound engine started' }

Write-FtLine ("AP_FAB_LAUNCH_TASK: side={0} task={1} runDir={2} gradleArgs={3}" -f $Side, $task, $sideDir, (@($GradleArgs) -join ' '))

# ══ 前台 ══════════════════════════════════════════════════════════════════
if ($Fg) {
    & cmd.exe /c $gradlewBat $task --console=plain @($GradleArgs)
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

# 就绪游标：只认「本次启动之后新写入」的日志（历史行不算，避免假就绪）。
#
# ⚠️ 这里必须用「文件身份锚点」而不是「字节长度」，否则**每一次启动都会假超时**。
#    Minecraft 的 log4j 带 OnStartupTriggeringPolicy ⇒ 每次冷启动都把 latest.log
#    改名成 `YYYY-MM-DD-N.log.gz` 再新建一个空文件（实测 2026-09-29 18:05:55，
#    服务端 9 秒就打出 `Done (…)`，但用旧长度的实现干等 240 秒后报 TIMEOUT）。
#    锚点 = (创建时间, 头部指纹, 长度)：身份变了 ⇒ 整个新文件都是「本次新增」。
$preAnchor = Get-FtLogAnchor -Path $logPath

$proc = Start-Process -FilePath 'cmd.exe' `
    -ArgumentList (@('/c', $gradlewBat, $task, '--console=plain') + @($GradleArgs)) `
    -WorkingDirectory $root -NoNewWindow -PassThru `
    -RedirectStandardOutput $outLog -RedirectStandardError $errLog

$state = @{
    side       = $Side
    pid        = $proc.Id
    startedAt  = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    outLog     = $outLog
    errLog     = $errLog
    runDir     = $sideDir
    task       = $task
    gradleArgs = @($GradleArgs)
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

    $tail = Get-FtLogWindowFromAnchor -Path $logPath -Anchor $preAnchor -Label "就绪窗口($Side)"
    if ($tail -and ($tail -match $readyPattern)) { $ready = $true; break }
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

# ⚠️ 超时 ≠ 游戏没起来。这里做一次**反向自检**（防「假超时」再次静默发生）：
#    若整份 latest.log 里其实已经出现了就绪标记，说明游戏早就绪、是**本台的窗口逻辑**没抓到
#    （例如日志被轮转、锚点语义退化）。这时必须报 ready-but-undetected 这个**不同的结论**，
#    绝不能报成「游戏 240 秒没起来」—— 那会把工具缺陷伪装成被测对象的问题。
$wholeText = Read-FtLogText -Path $logPath
if ($wholeText -and ($wholeText -match $readyPattern)) {
    $curAnchor = Get-FtLogAnchor -Path $logPath
    Write-FtLine ("AP_FAB_LAUNCH: FAIL (reason=ready-but-undetected timeout={0}s)" -f $TimeoutSec)
    Write-FtLine ("AP_FAB_LAUNCH_ANCHOR: preLen={0} preCtime={1} preHead={2} curLen={3} curCtime={4} curHead={5}" -f `
            $preAnchor.Length, $preAnchor.CreationTicks, $preAnchor.HeadHash,
        $curAnchor.Length, $curAnchor.CreationTicks, $curAnchor.HeadHash)
    Write-FtError 'LAUNCH' ("日志里**已有**就绪标记 /{0}/，但窗口未捕获 ⇒ 本台窗口/锚点逻辑失效（不是游戏没起来）。请附上一行的 ANCHOR 读数登记缺陷。" -f $readyPattern)
    exit $FT_EXIT_ERROR
}

Write-FtLine ("AP_FAB_LAUNCH: FAIL (reason=timeout timeout={0}s)" -f $TimeoutSec)
Write-FtError 'LAUNCH' ("{0}s 内未见就绪标记 /{1}/（还在跑，可继续用 ft_assert 观察；收停用 ft_stop）" -f $TimeoutSec, $readyPattern)
exit $FT_EXIT_TIMEOUT
