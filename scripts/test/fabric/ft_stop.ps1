#Requires -Version 7.0
<#
.SYNOPSIS
    ft_stop — 收停 fabric-1.20.1 线的运行实例与（可选）Gradle 守护。

.DESCRIPTION
    只杀**本线自己**的进程，绝不 taskkill 其它 java（比如用户自己的 Minecraft / IDE 语言服务器）。
    两级判据：
      ① 状态文件 `scripts/test/fabric/.ft_launch_state.json`（ft_launch 记录）里的 PID，整棵进程树收停；
      ② CIM 扫描：`java.exe` 且命令行含 `devlaunchinjector`（Loom dev-run 的入口主类，
         依据 = 实测 gradle 缓存 `net.fabricmc:dev-launch-injector:0.2.1+build.8` 内的
         `net/fabricmc/devlaunchinjector/Main.class`；该 class 内的系统属性名实测为
         `fabric.dli.config` / `fabric.dli.env` / `fabric.dli.main`）且命令行含 `fabric-1.20.1`。
         —— 显式**排除** Gradle 守护（`GradleDaemon`）：它是热态缓存，留下来能显著加快下次构建。

.PARAMETER Side
    client | server | all（默认 all）。

.PARAMETER StopDaemon
    额外执行 `gradlew --stop`（停 Gradle 守护；之后下次构建会重新冷启）。

.PARAMETER PurgeSaves
    收停后清理测试存档：服务端 `run/server/world`、客户端 `run/client/saves`。
    ⚠️ 破坏性操作，默认**关闭**（两阶段/重登类取证需要跨收停保留存档）。

.EXAMPLE
    pwsh -NoProfile -File scripts/test/fabric/ft_stop.ps1 --side server
    pwsh -NoProfile -File scripts/test/fabric/ft_stop.ps1 --side all --stop-daemon
    pwsh -NoProfile -File scripts/test/fabric/ft_stop.ps1 --side server --purge-saves

.NOTES
    机器可读：AP_FAB_STOP: OK|PARTIAL …；退出码 0 / 1（仍有残留） / 2（参数错误）。
    已知坑（照抄生产线的教训）：`-Wait` / WaitForExit 会等**整棵进程树**，gradle wrapper 有长命后代
    ⇒ 这里一律「杀完再验」而不是「等它退」。
#>

$ErrorActionPreference = 'Stop'

$script:FtLibDir = Join-Path $PSScriptRoot 'lib'
Import-Module (Join-Path $script:FtLibDir 'Ft.Common.psm1') -Force

Initialize-FtConsole

# ══ 参数 ══════════════════════════════════════════════════════════════════
$Side = 'all'
$StopDaemon = $false
$PurgeSaves = $false
$ArgList = @($args)

$i = 0
while ($i -lt $args.Count) {
    $tok = [string]$args[$i]
    $key = Get-FtArgKey -Token $tok
    if ($key -eq 'side') {
        if ($i + 1 -ge $args.Count) { Write-FtErrorLine '缺少 --side 的值'; exit $FT_EXIT_ERROR }
        $Side = ([string]$args[$i + 1]).ToLowerInvariant(); $i += 2
    } elseif ($key -eq 'all') {
        $Side = 'all'; $i++
    } elseif ($key -eq 'stopdaemon') {
        $StopDaemon = $true; $i++
    } elseif ($key -eq 'keepdaemon') {
        # 显式保留守护（默认行为）；接受该拼写以对齐生产线的 --keep-daemon
        $StopDaemon = $false; $i++
    } elseif ($key -eq 'purgesaves') {
        $PurgeSaves = $true; $i++
    } elseif ($key -eq 'allowauto') {
        $i++
    } elseif ($key -eq 'h' -or $key -eq 'help') {
        Write-FtLine '用法: ft_stop.ps1 [--side client|server|all] [--stop-daemon] [--purge-saves]'
        exit $FT_EXIT_PASS
    } else {
        Write-FtErrorLine "未知参数 $tok"; exit $FT_EXIT_ERROR
    }
}

if ($Side -ne 'client' -and $Side -ne 'server' -and $Side -ne 'all') {
    Write-FtErrorLine "非法 --side '$Side'（只接受 client | server | all）"
    exit $FT_EXIT_ERROR
}

$root = Get-FtRepoRoot
$targets = if ($Side -eq 'all') { @('client', 'server') } else { @($Side) }
$killedPids = New-Object System.Collections.Generic.List[int]

# ══ ① 状态文件里的 PID（整棵进程树）═══════════════════════════════════════
$statePath = Join-Path (Get-FtSelfDir) '.ft_launch_state.json'
if (Test-Path -LiteralPath $statePath -PathType Leaf) {
    try {
        $state = Get-Content -LiteralPath $statePath -Raw -Encoding UTF8 | ConvertFrom-Json
        $pid0 = [int]$state.pid
        $side0 = [string]$state.side
        if ($pid0 -gt 0 -and ($Side -eq 'all' -or $side0 -eq $Side)) {
            $p = Get-Process -Id $pid0 -ErrorAction SilentlyContinue
            if ($p) {
                Write-FtLine ("AP_FAB_STOP_KILL: target=launcher pid={0} side={1} tree=true" -f $pid0, $side0)
                & taskkill.exe /T /F /PID $pid0 2>$null | Out-Null
                $killedPids.Add($pid0)
            } else {
                Write-FtLine ("AP_FAB_STOP_KILL: target=launcher pid={0} side={1} tree=false reason=not-running" -f $pid0, $side0)
            }
        }
    } catch {
        Write-FtWarn "状态文件解析失败（$statePath）：$($_.Exception.Message)"
    }
    Remove-Item -LiteralPath $statePath -Force -ErrorAction SilentlyContinue
} else {
    Write-FtLine 'AP_FAB_STOP_STATE: none'
}

# ══ ② CIM 扫描本线的 dev-run java 进程 ═════════════════════════════════════
$leftover = New-Object System.Collections.Generic.List[string]
try {
    $cands = @(Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction Stop)
} catch {
    Write-FtWarn "无法枚举 java 进程（CIM 不可用）：$($_.Exception.Message)"
    $cands = @()
}

foreach ($c in $cands) {
    $cl = [string]$c.CommandLine
    if (-not $cl) { continue }
    if ($cl -match 'GradleDaemon') { continue }                 # 守护不动（除非 --stop-daemon）
    if ($cl -notmatch 'devlaunchinjector') { continue }          # 只认 Loom dev-run 入口
    if ($cl -notmatch 'fabric-1\.20\.1') { continue }            # 只认本子项目

    $envSide = ''
    $m = [regex]::Match($cl, 'fabric\.dli\.env=([A-Za-z]+)')
    if ($m.Success) { $envSide = $m.Groups[1].Value.ToLowerInvariant() }
    $sideMatch = ($Side -eq 'all') -or ($envSide -eq $Side)
    if (-not $sideMatch) {
        # dli.env 缺失时退化为「命令行里出现 run\<side>」判据
        if ($Side -ne 'all' -and $cl -notmatch ('run[\\/]' + $Side)) { continue }
    }

    Write-FtLine ("AP_FAB_STOP_KILL: target=game pid={0} side={1} tree=true" -f $c.ProcessId, $(if ($envSide) { $envSide } else { 'unknown' }))
    & taskkill.exe /T /F /PID $c.ProcessId 2>$null | Out-Null
    $killedPids.Add([int]$c.ProcessId)
}

Start-Sleep -Milliseconds 1500

# 收停后复核（只针对本线判据，避免把无关 java 算成残留）
try {
    $after = @(Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -and $_.CommandLine -match 'devlaunchinjector' -and $_.CommandLine -match 'fabric-1\.20\.1' })
    foreach ($a in $after) { $leftover.Add("pid=$($a.ProcessId)") }
} catch { }

# ══ ③ Gradle 守护（可选）═══════════════════════════════════════════════════
if ($StopDaemon) {
    $gradlewBat = Join-Path $root 'gradlew.bat'
    if (Test-Path -LiteralPath $gradlewBat -PathType Leaf) {
        & cmd.exe /c $gradlewBat '--stop' 2>$null | Out-Null
        Write-FtLine 'AP_FAB_STOP_DAEMON: stopped=true'
    } else {
        Write-FtLine 'AP_FAB_STOP_DAEMON: stopped=false reason=no-gradlew'
    }
} else {
    Write-FtLine 'AP_FAB_STOP_DAEMON: stopped=false reason=keep-daemon'
}

# ══ ④ 清档（可选，破坏性）═════════════════════════════════════════════════
if ($PurgeSaves) {
    foreach ($t in $targets) {
        $dirs = @()
        if ($t -eq 'server') { $dirs += (Join-Path (Get-FtSideDir -Side 'server') 'world') }
        if ($t -eq 'client') { $dirs += (Join-Path (Get-FtSideDir -Side 'client') 'saves') }
        foreach ($d in $dirs) {
            if (Test-Path -LiteralPath $d -PathType Container) {
                Remove-Item -LiteralPath $d -Recurse -Force -ErrorAction SilentlyContinue
                $gone = -not (Test-Path -LiteralPath $d)
                Write-FtLine ("AP_FAB_STOP_PURGE: path={0} removed={1}" -f $d, $gone.ToString().ToLowerInvariant())
                Write-FtWarn "已删除测试存档：$d"
            } else {
                Write-FtLine ("AP_FAB_STOP_PURGE: path={0} removed=absent" -f $d)
            }
        }
    }
}

# ══ 汇总 ══════════════════════════════════════════════════════════════════
Write-FtLine ("AP_FAB_STOP_KILLED: count={0} pids={1}" -f $killedPids.Count, (($killedPids | ForEach-Object { $_ }) -join '|'))

if ($leftover.Count -gt 0) {
    Write-FtLine ("AP_FAB_STOP: PARTIAL leftover={0}" -f ($leftover -join ','))
    Write-FtWarn ("收停后仍有本线 java 残留：{0}" -f ($leftover -join ', '))
    exit $FT_EXIT_FAIL
}

Write-FtLine 'AP_FAB_STOP: OK'
Write-FtOk 'STOP' ("side=$Side 已收停 {0} 个进程，无残留" -f $killedPids.Count)
exit $FT_EXIT_PASS
