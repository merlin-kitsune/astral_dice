#Requires -Version 7.0
<#
.SYNOPSIS
    mt_launch — 启动 runClient 并等待进入世界（阶段 L）。

.DESCRIPTION
    就绪判据（沿用既有约定）: 基础等待 30s，随后轮询 ModernFix 加载完成日志
      "Total time to load game and open world was"
    每 15s 复检一次，180s 上限；出现崩溃报告或进程退出即判失败。

.EXAMPLE
    pwsh -File scripts/test/mt_launch.ps1 --version 1.21.1

.NOTES
    对应源文件（迁移前）：scripts/test/mt_launch.sh。

    **关键点：客户端必须在脚本退出后继续运行**（launch → cases 是分步执行的）。
    因此启动走 `cmd.exe /c gradlew.bat … > 日志`（由 cmd 持有日志文件句柄），
    而**不是**管道重定向 —— 管道读端在父 pwsh 退出后关闭，客户端写 stdout 会直接崩。
    Start-MtProcessToFile 内部用 Start-Process -RedirectStandardOutput <文件>，
    它把真实文件句柄复制进子进程（已实测子进程能跨父进程退出继续写）。

    stderr 落在 `runclient_launch.log.err`（bash 是 `2>&1` 单文件合并）。

    有意的文案偏差：世界缺失时的提示原为 `mt.sh --phase env`，此处指向 `mt.ps1`。
#>

$ErrorActionPreference = 'Stop'

$script:LibDir = Join-Path $PSScriptRoot 'lib'
Import-Module (Join-Path $script:LibDir 'Mt.Phase.psm1')
Import-Module (Join-Path $script:LibDir 'Mt.Paths.psm1')
Import-Module (Join-Path $script:LibDir 'Mt.Proc.psm1')

Initialize-MtConsole

if ($MyInvocation.InvocationName -ne '.') {

    $Version = ''

    $i = 0
    while ($i -lt $args.Count) {
        $tok = [string]$args[$i]
        $key = $tok.TrimStart('-').ToLowerInvariant()
        if ($key -eq 'version') {
            if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --version 的值'; exit $MT_EXIT_ERROR }
            $Version = [string]$args[$i + 1]
            $i += 2
        } else {
            Write-MtErrorLine "未知参数 $tok"; exit $MT_EXIT_ERROR
        }
    }

    if (-not $Version) { Write-MtErrorLine '必须指定 --version'; exit $MT_EXIT_ERROR }
    if (-not (Assert-MtVersion -Version $Version)) { exit $MT_EXIT_ERROR }

    $p = Get-MtPaths -Version $Version
    $root = Get-MtRoot
    $testDir = $PSScriptRoot
    $psExe = (Get-Process -Id $PID).Path
    $world = Get-MtWorldName

    Start-MtPhase "launch ($Version)"

    if (-not (Test-Path -LiteralPath (Join-Path $p.client_world 'level.dat') -PathType Leaf)) {
        Write-MtBlocked 'launch' '测试世界不存在，请先执行 pwsh -File scripts/test/mt.ps1 --phase env'
        exit $MT_EXIT_BLOCKED
    }

    if (Test-Path -LiteralPath $p.latest_log -PathType Leaf) {
        Remove-Item -LiteralPath $p.latest_log -Force -ErrorAction SilentlyContinue
    }
    # 服务端权威通道（KubeJS 探针追加写）必须每次运行清零，否则上一轮标记会污染本轮断言
    if (Test-Path -LiteralPath $p.probe_log -PathType Leaf) {
        Remove-Item -LiteralPath $p.probe_log -Force -ErrorAction SilentlyContinue
    }
    if (Test-Path -LiteralPath $p.crash_dir) {
        Remove-Item -LiteralPath $p.crash_dir -Recurse -Force -ErrorAction SilentlyContinue
    }
    if (-not (Test-Path -LiteralPath $p.shot_dir)) {
        [void](New-Item -ItemType Directory -Force -Path $p.shot_dir)
    }

    $launchLog = Join-Path $p.run_dir 'runclient_launch.log'

    Write-MtInfo "启动 runClient(quickplay=$world)"

    $gradlew = Join-Path $root 'gradlew.bat'
    if (-not (Test-Path -LiteralPath $gradlew -PathType Leaf)) { $gradlew = Join-Path $root 'gradlew' }

    $started = $null
    try {
        $started = Start-MtProcessToFile -FilePath 'cmd.exe' `
            -ArgumentList @('/c', $gradlew, $p.task_client, "-Pquickplay=$world", '--console=plain') `
            -LogPath $launchLog -WorkingDirectory $root -MergeStderr
    } catch {
        $started = $null
    }
    if ($null -eq $started) {
        Write-MtError 'launch' "无法启动 runClient（$gradlew）"
        exit $MT_EXIT_ERROR
    }

    # 基础等待
    Start-Sleep -Seconds 30

    $deadline = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds() + 180
    $entered = $false
    while ([DateTimeOffset]::UtcNow.ToUnixTimeSeconds() -lt $deadline) {
        $latest = Read-MtSharedText -Path $p.latest_log
        if ($latest.Contains('Total time to load game and open world was')) { $entered = $true; break }

        # 崩溃报告出现即失败（启动期大量良性 Exception 不应中止）
        $crashes = @()
        if (Test-Path -LiteralPath $p.crash_dir -PathType Container) {
            $crashes = @(Get-ChildItem -LiteralPath $p.crash_dir -File -Filter '*.txt')
        }
        if ($crashes.Count -gt 0) {
            Write-MtError 'launch' '检测到崩溃报告'
            exit $MT_EXIT_ERROR
        }

        if ($started.Process.HasExited) {
            Write-MtError 'launch' 'runClient 进程已退出，启动失败'
            foreach ($ln in (Get-Content -LiteralPath $launchLog -Tail 30 -ErrorAction SilentlyContinue)) {
                Write-MtErrLine ([string]$ln)
            }
            exit $MT_EXIT_ERROR
        }

        Start-Sleep -Seconds 15
    }

    if (-not $entered) {
        Write-MtBlocked 'launch' '未在时限内进入世界'
        exit $MT_EXIT_BLOCKED
    }

    $latest = Read-MtSharedText -Path $p.latest_log
    # 兼容性信号（1.21.1: Sodium/Iris；1.20.1: Embeddium/Oculus）
    if ($Version -eq '1.21.1') {
        if ($latest.Contains('Sodium')) { Write-MtInfo 'SODIUM_LOADED=true' } else { Write-MtWarn 'SODIUM_LOADED=false' }
        if ($latest.Contains('Iris')) { Write-MtInfo 'IRIS_LOADED=true' } else { Write-MtWarn 'IRIS_LOADED=false' }
    } else {
        # -qi：bash 侧是大小写不敏感匹配
        if ($latest -imatch 'Embeddium') { Write-MtInfo 'EMBEDDIUM_LOADED=true' }
        else { Write-MtInfo 'EMBEDDIUM_LOADED=false(dev run 预期)' }
        if ($latest -imatch 'Oculus') { Write-MtInfo 'OCULUS_LOADED=true' }
        else { Write-MtInfo 'OCULUS_LOADED=false(dev run 预期)' }
    }

    # KubeJS 脚本健康（进入世界后第一步）
    & $psExe -NoProfile -File (Join-Path $testDir 'mt_assert.ps1') kubejs --version $Version
    if ($LASTEXITCODE -ne 0) { Write-MtWarn 'KubeJS server.log 非 0 errors' }

    Write-MtOk 'LAUNCH' "已进入世界（quickplay=$world）"
    exit $MT_EXIT_PASS
}
