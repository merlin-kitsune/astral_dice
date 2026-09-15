#Requires -Version 7.0
<#
.SYNOPSIS
    mt_launch — 启动 runClient 并等待进入世界（阶段 L），并在进入世界后执行「测试前清场」。

.DESCRIPTION
    就绪判据（沿用既有约定）: 基础等待 30s，随后轮询 ModernFix 加载完成日志
      "Total time to load game and open world was"
    每 15s 复检一次，180s 上限；出现崩溃报告或进程退出即判失败。

.EXAMPLE
    pwsh -File scripts/test/mt_launch.ps1 --version 1.21.1
    pwsh -File scripts/test/mt_launch.ps1 --version 1.20.1 --no-preclean   # 跳过清场（仅特殊取证）

.NOTES
    对应源文件（迁移前）：scripts/test/mt_launch.sh。

    **关键点：客户端必须在脚本退出后继续运行**（launch → cases 是分步执行的）。
    因此启动走 `cmd.exe /c gradlew.bat … > 日志`（由 cmd 持有日志文件句柄），
    而**不是**管道重定向 —— 管道读端在父 pwsh 退出后关闭，客户端写 stdout 会直接崩。
    Start-MtProcessToFile 内部用 Start-Process -RedirectStandardOutput <文件>，
    它把真实文件句柄复制进子进程（已实测子进程能跨父进程退出继续写）。

    stderr 落在 `runclient_launch.log.err`（bash 是 `2>&1` 单文件合并）。

    有意的文案偏差：世界缺失时的提示原为 `mt.sh --phase env`，此处指向 `mt.ps1`。

    **测试前清场（2026-09-15 用户裁决后强制；规则全文见 AGENTS.md「测试前清场」）**：
    进入世界后自动注入 `/kill @e[type=!player,distance=..128]`（连发两次，注入通道偶发
    丢失见 TESTING-SPEC §10-17），清掉残留靶 / 散落物 / 常驻敌对生物 —— 它们会污染
    **世界级差值**读数：`self`（施放者 HP 原始差值，不分伤害来源）与 `bolt_delta`
    （全局雷击生成计数器差值）。`--no-preclean` 仅用于必须保留世界实体的特殊取证。
#>

$ErrorActionPreference = 'Stop'

$script:LibDir = Join-Path $PSScriptRoot 'lib'
Import-Module (Join-Path $script:LibDir 'Mt.Phase.psm1')
Import-Module (Join-Path $script:LibDir 'Mt.Paths.psm1')
Import-Module (Join-Path $script:LibDir 'Mt.Proc.psm1')

Initialize-MtConsole

if ($MyInvocation.InvocationName -ne '.') {

    $Version = ''
    $NoPreclean = $false

    $i = 0
    while ($i -lt $args.Count) {
        $tok = [string]$args[$i]
        $key = $tok.TrimStart('-').ToLowerInvariant()
        if ($key -eq 'version') {
            if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --version 的值'; exit $MT_EXIT_ERROR }
            $Version = [string]$args[$i + 1]
            $i += 2
        } elseif ($key -eq 'no-preclean') {
            $NoPreclean = $true
            $i += 1
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

    # ── 防撞预检（2026-09-15 实测后的硬性口径）─────────────────────────────────
    # ① 全机存在**任何** Minecraft 客户端（不分版本）即拒绝启动：跨版本/他人客户端
    #    同时开着会让本阶段的「等待日志出现」直接读到**别人写的** latest.log，
    #    而窗口/注入目标也会撞车（实测：01:17 那次启动就是这样误判成 OK 的）。
    $existing = @(Get-JavaProcesses | Where-Object {
            $c = [string]$_.CommandLine
            $t = ''
            try { $t = [string](Get-Process -Id $_.Pid -ErrorAction SilentlyContinue).MainWindowTitle } catch { $t = '' }
            # ⚠️ 1.20.1 的 DevLaunch 客户端命令行**不含** net.minecraft.client.main.Main
            #    （走 cpw.mods.bootstraplauncher.BootstrapLauncher），只认 Main 会漏检 →
            #    跨版本撞车照旧发生。这里同时认两种入口 + 「MC 客户端窗口标题」。
            ($c.Contains('net.minecraft.client.main.Main') -or $c.Contains('bootstraplauncher'))
        })
    if ($existing.Count -gt 0) {
        $pidsText = ($existing | ForEach-Object { $_.Pid }) -join ', '
        Write-MtBlocked 'launch' ("全机已存在 Minecraft 客户端进程(PID=$pidsText)，拒绝启动以免读到他人日志/抢占注入目标；先收停：pwsh -File scripts/test/mt.ps1 --phase stop --force")
        exit $MT_EXIT_BLOCKED
    }

    # ② latest.log 存在却删不掉 ⇒ 一定有别的进程正在写它（句柄被占）→ 硬失败。
    #    旧实现用 -ErrorAction SilentlyContinue 吞掉失败，随后必然读到上一轮/他人的
    #    「Total time to load game and open world was」→ **假阳性 MT_LAUNCH: OK**。
    if (Test-Path -LiteralPath $p.latest_log -PathType Leaf) {
        try {
            Remove-Item -LiteralPath $p.latest_log -Force -ErrorAction Stop
        } catch {
            Write-MtBlocked 'launch' ("无法清空 $($p.latest_log)（有其它进程正在写它）：$($_.Exception.Message)")
            exit $MT_EXIT_BLOCKED
        }
        if (Test-Path -LiteralPath $p.latest_log -PathType Leaf) {
            Write-MtBlocked 'launch' "无法清空 $($p.latest_log)（文件仍存在）：拒绝启动以免读到非本轮日志"
            exit $MT_EXIT_BLOCKED
        }
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

    # 本次启动时刻：接受「已进入世界」标记前，必须证明那行日志是**本次启动之后**写的
    # （旧 latest.log 已被删除 + 预检无其它客户端，构成第二重保险）。
    $launchStartedAt = Get-Date

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
    $logFresh = $false
    while ([DateTimeOffset]::UtcNow.ToUnixTimeSeconds() -lt $deadline) {
        $latest = Read-MtSharedText -Path $p.latest_log
        # 归属校验：latest.log 必须是**本次启动之后**创建的；否则那行「已进入世界」可能是
        # 别人/上一轮写的（2026-09-15 实测到的假阳性根因）。
        $logFresh = $false
        if (Test-Path -LiteralPath $p.latest_log -PathType Leaf) {
            try { $logFresh = ((Get-Item -LiteralPath $p.latest_log).CreationTime -gt $launchStartedAt) } catch { $logFresh = $false }
        }
        if ($logFresh -and $latest.Contains('Total time to load game and open world was')) { $entered = $true; break }

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

    # ── 测试前清场（2026-09-15 用户裁决后强制；规则见 AGENTS.md「测试前清场」）──────────
    # 清掉玩家 128 格内的非玩家实体：残留靶 / 散落物 / 常驻敌对生物。它们会污染
    # **世界级差值**读数 —— `self`（施放者 HP 原始差值，不分伤害来源）与 `bolt_delta`
    # （全局雷击生成计数器差值）。实测 1.20.1 testworld 一只常驻蜘蛛使一次 read 内
    # `php` 掉 6.0，一度被误判成「雷击打到自己」（TESTING-SPEC §8.2-1）。
    # 注入通道偶发丢失（§10-17），故与收尾命令同口径：连发两次，中间等 600ms。
    if ($NoPreclean) {
        Write-MtWarn 'PRECLEAN: SKIPPED (--no-preclean)'
    } else {
        Start-Sleep -Milliseconds 1500   # 让 quickplay 的界面彻底退到游戏内再开聊天栏
        $precleanCmd = '/kill @e[type=!player,distance=..128]'
        $precleanRc = @()
        foreach ($precleanAttempt in 1..2) {
            & $psExe -NoProfile -File (Join-Path $testDir 'mt_inject.ps1') cmd --command $precleanCmd --version $Version
            $precleanRc += $LASTEXITCODE
            Start-Sleep -Milliseconds 600
        }
        if (@($precleanRc | Where-Object { $_ -ne 0 }).Count -eq 0) {
            Write-MtInfo ("PRECLEAN: OK — {0} ×2" -f $precleanCmd)
        } else {
            Write-MtWarn ("PRECLEAN: WARN — 注入返回码 {0}（命令可能未送达，读数有被世界残留污染的风险）" -f ($precleanRc -join '/'))
        }
    }

    Write-MtOk 'LAUNCH' "已进入世界（quickplay=$world）"
    exit $MT_EXIT_PASS
}
