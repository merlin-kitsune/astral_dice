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
    迁移前源文件 scripts/test/mt_launch.sh（该原件已在 92fbeaf「工具链收敛为纯 pwsh」删除，取回：`git show 92fbeaf^:scripts/test/mt_launch.sh`）。

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

function Sync-MtProbeScripts {
    <#
    .SYNOPSIS
        A4（2026-09-15 B2）：把 `scripts/test/resources/kubejs/<版本>/server_scripts/*.js`
        部署到 `run/<版本>/kubejs/server_scripts/`，并逐文件核对 SHA256。

    .DESCRIPTION
        为什么必须自动化（B1 实测根因，`docs/batch3/B1-in-game-results.md` ⑥-4）：
        TESTING-SPEC §6 规定探针靠**手工**复制，而 `mt_env` 只管 mods/world、从不刷新
        `run/<版本>/kubejs/`。于是 2026-09-15 18:19 的全流程用的仍是 14:54 的旧探针
        （源 16:33 已更新，SHA256 不同）⇒ `KOMACHI-EXTRA-PLAY-1.21.1` 在旧探针下 34/41 FAIL、
        换上新探针后 41/41 PASS。**用旧探针跑出的 FAIL 是假 FAIL**，必须由工具链自己消除。

        设计：与源**逐字节比对**（不是看时间戳），不同才复制；复制后再复核一次哈希。
        每个文件打一行 `MT_INFO: PROBE_DEPLOY: <名字> <哈希前12位> <状态>`，便于日志取证。
        缺失源目录只告警不中断（例如 1.20.1 侧的探针集合并非 1.21.1 的完全镜像）。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Version,
        [Parameter(Mandatory)][string]$RunDir,
        [Parameter(Mandatory)][string]$TestDir
    )

    $srcDir = Join-Path (Join-Path (Join-Path $TestDir 'resources') 'kubejs') `
        (Join-Path $Version 'server_scripts')
    $dstDir = Join-Path (Join-Path $RunDir 'kubejs') 'server_scripts'

    if (-not (Test-Path -LiteralPath $srcDir -PathType Container)) {
        Write-MtWarn "PROBE_DEPLOY: 源目录不存在，跳过（$srcDir）"
        return
    }
    if (-not (Test-Path -LiteralPath $dstDir)) {
        [void](New-Item -ItemType Directory -Force -Path $dstDir)
    }

    $files = @(Get-ChildItem -LiteralPath $srcDir -File -Filter '*.js' | Sort-Object Name)
    if ($files.Count -eq 0) {
        Write-MtWarn "PROBE_DEPLOY: 源目录无 *.js，跳过（$srcDir）"
        return
    }

    $copied = 0
    $sameCount = 0
    $bad = @()
    foreach ($f in $files) {
        $dst = Join-Path $dstDir $f.Name
        $srcHash = (Get-FileHash -LiteralPath $f.FullName -Algorithm SHA256).Hash
        $state = 'copied'
        if (Test-Path -LiteralPath $dst -PathType Leaf) {
            $dstHash0 = (Get-FileHash -LiteralPath $dst -Algorithm SHA256).Hash
            if ($dstHash0 -eq $srcHash) {
                $state = 'up-to-date'
                $sameCount++
                Write-MtInfo ("PROBE_DEPLOY: {0} {1} {2}" -f $f.Name, $srcHash.Substring(0, 12), $state)
                continue
            }
        }
        Copy-Item -LiteralPath $f.FullName -Destination $dst -Force
        $copied++
        $dstHash1 = (Get-FileHash -LiteralPath $dst -Algorithm SHA256).Hash
        if ($dstHash1 -ne $srcHash) {
            $bad += $f.Name
            $state = 'MISMATCH'
        }
        Write-MtInfo ("PROBE_DEPLOY: {0} {1} {2}" -f $f.Name, $srcHash.Substring(0, 12), $state)
    }

    if ($bad.Count -gt 0) {
        Write-MtWarn ("PROBE_DEPLOY: {0} 个文件复制后哈希不一致（{1}）—— 本轮的 AP_ 读数不可信" -f `
                $bad.Count, ($bad -join ', '))
        return
    }
    Write-MtInfo ("PROBE_DEPLOY: OK — {0} 个脚本（复制 {1} / 已是最新 {2}）" -f $files.Count, $copied, $sameCount)
}

function Invoke-MtLaunchOpPreflight {
    <#
    .SYNOPSIS
        B6 ④（2026-09-15）：**只读** OP 前置闸门 + `/astralparty dump` 可用性闸门。

    .DESCRIPTION
        为什么落在 launch 而不是 mt_preflight：`hasPermissions(2)` 只能由**游戏内玩家**给出，
        而 `mt_preflight` 的契约是「在处理任何游戏进程之前跑、绝不触达游戏」。进入世界之后、
        用例之前这一段就是 cases 阶段的前置闸门，语义与 mt_preflight 完全一致：

          前置不足  ⇒ **BLOCKED**（不是 FAIL —— 它不是产品缺陷）；
          探针不可用 / dump 不可用 ⇒ **ERROR**（工具链或产品资产缺失，绝不静默降级）。

        判据与 `/astralparty` **完全相同**：探针在玩家命令源上调
        `CommandSourceStack#hasPermission(2)`（1.21.1 `CommandSourceStack.java:390` /
        1.20.1 `:174`），并把数值级（`MinecraftServer#getProfilePermissions`）与来源一并打印，
        实测值落在 `latest.log` 的 `AP_OP_PERM:has2=<0|1>:level=<n>:src=<…>:dump=<rc>`。

        红线：**不得**为让测试通过而降低 `requires` 门槛 / 加测试专用开关 / 绕过 OP 走客户端
        旁路（审计 §4.6 / §5.4 红线 4、8）。前置拿不到时的正确做法是把环境修好，或按本节记 BLOCKED。

        注入两次（与 preclean 同口径）：注入通道偶发丢失；只发一次若丢了会得到"没有读数"的空跑，
        这里会把它记成 ERROR 而**不是**静默通过。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Version,
        [Parameter(Mandatory)][string]$PsExe,
        [Parameter(Mandatory)][string]$TestDir,
        [Parameter(Mandatory)][psobject]$Paths
    )

    $rcs = @()
    for ($attempt = 1; $attempt -le 2; $attempt++) {
        & $PsExe -NoProfile -File (Join-Path $TestDir 'mt_inject.ps1') cmd `
            --command '/astralprobe opprobe' --version $Version
        $rcs += $LASTEXITCODE
        Start-Sleep -Milliseconds 800
    }

    $text = ''
    try { $text = Read-MtSharedText -Path $Paths.latest_log } catch { $text = '' }

    $m = [regex]::Match($text, 'AP_OP_PERM:has2=(-?\d+):level=(-?\d+):src=([^:\s]+):dump=([^\s]*)')
    if (-not $m.Success) {
        return [pscustomobject]@{
            Code   = 'ERROR'
            Detail = ('未取到 AP_OP_PERM 读数（注入返回码 {0}）—— 探针未加载 / KubeJS 脚本未生效 / ' +
                '`/astralprobe opprobe` 不存在，属工具链故障而非产品缺陷' -f ($rcs -join '/'))
        }
    }

    $has2 = [int]$m.Groups[1].Value
    $level = [int]$m.Groups[2].Value
    $src = [string]$m.Groups[3].Value
    $dumpRc = [string]$m.Groups[4].Value

    Write-MtInfo ("PREFLIGHT_OP: hasPermissions(2)={0} level={1} src={2} required=2" -f $has2, $level, $src)

    if ($has2 -ne 1) {
        return [pscustomobject]@{
            Code   = 'BLOCKED'
            Detail = ('实测 hasPermissions(2)={0}（权限级 level={1}，来源 {2}）< 2 —— `/astralparty` ' +
                '与 dump 均需 OP 级 2；这是**环境前置欠缺**，不是产品缺陷。请确认测试世界的 level.dat ' +
                'Data.allowCommands=1（mt_env world 会强制写入），单人 quickplay 集成服应得到 level=4' -f `
                    $has2, $level, $src)
        }
    }

    # dump 可用性的判据**不能**用 performPrefixedCommand 的返回值：1.21.1 的 Rhino 下它返回
    # `undefined`（命令其实执行了），1.20.1 才返回 rc=1。稳定契约是**机器格式行本身** ——
    # `AstralPartyCommand` 的 LOGGER.info 必然把 `APDUMP|<组>|` 写进 latest.log。
    $dumpRows = ([regex]::Matches($text, 'APDUMP\|LOCKRAW\|')).Count
    Write-MtInfo ("ASTRALPARTY_DUMP: rc={0} lockraw_rows={1}" -f $dumpRc, $dumpRows)
    if ($dumpRows -lt 1) {
        return [pscustomobject]@{
            Code   = 'ERROR'
            Detail = ('未在 latest.log 里找到任何 `APDUMP|LOCKRAW|` 行（`/astralparty dump` 返回值 {0}）' +
                ' —— 本批测试资产的只读断言锚定 APDUMP| 原始值行，命令缺失/失败必须显式失败而' +
                '**不得静默降级**。请核对 run/<版本>/mods 里的 jar 是否为含 /astralparty 的构建，' +
                '以及 KubeJS 侧探针是否最新' -f $dumpRc)
        }
    }

    return [pscustomobject]@{ Code = 'OK'; Detail = ("has2={0} level={1} src={2} dump={3}" -f $has2, $level, $src, $dumpRc) }
}

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

    # A4（B2）：探针脚本必须在**冷启动之前**刷新 —— 用旧探针跑出的 FAIL 是假 FAIL。
    # 放在这里（而不是 mt_env）的理由：① KubeJS 只在启动时加载 server_scripts，
    # 部署必须紧邻 Start-MtProcessToFile；② `--phase launch` 单阶段同样会走到这里，
    # 分步路线（B1 实际采用的路线）因此自动获得新探针。
    Sync-MtProbeScripts -Version $Version -RunDir $p.run_dir -TestDir $testDir

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

    # ── B6 ④：cases 阶段的前置闸门（只读 OP 断言 + /astralparty dump 可用性）──────────────
    # 前置不足 ⇒ BLOCKED；探针/dump 不可用 ⇒ ERROR。两者都在用例之前收口，绝不静默降级。
    # 刻意**不**新开阶段头：阶段仍是 launch，`MT_LAUNCH: OK (elapsed)` 的计时口径不变。
    $opInfo = Invoke-MtLaunchOpPreflight -Version $Version -PsExe $psExe -TestDir $testDir -Paths $p
    if ($opInfo.Code -eq 'BLOCKED') {
        Write-MtBlocked 'preflight-op' $opInfo.Detail
        exit $MT_EXIT_BLOCKED
    }
    if ($opInfo.Code -eq 'ERROR') {
        Write-MtError 'preflight-op' $opInfo.Detail
        exit $MT_EXIT_ERROR
    }
    Write-MtInfo ("PREFLIGHT_OP: OK — {0}" -f $opInfo.Detail)

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

    # ── A2（B2）：单阶段 launch 也必须建立快照基线 ─────────────────────────────
    # 背景（B1 实测，docs/batch3/B1-in-game-results.md ⑥-2）：此前**只有全流程**在
    # mt.ps1:330 调 `mt_assert snapshot`；`--phase launch` 单阶段不写快照，分步路线会
    # 静默沿用上一轮的日志字节偏移，把确实存在的 AP_ 行判成「未命中」→ **假 FAIL**
    # （实测：第一轮 RAILGUN-PET-EXCLUDE 因此 26/26 里的 tame 行未命中，复跑才 PASS）。
    # 现在快照动作放进 launch 自己的收尾：全流程与分步路线共用同一处，调用方不必再手工补
    # （覆盖 preclean 之后的所有行 —— preclean 是 launch 自己的动作，不属于任何用例的增量）。
    #
    # B7：这里必须显式写 `--window launch` —— 它将同一组偏移**冻结**成 `launch_offsets`，
    # 供 `mixin` 断言与 `mt_report` 的整轮摘要读取；此后每条用例的 `--window case` 只改写
    # `offsets`，不会冲掉这份 launch 基线。
    & $psExe -NoProfile -File (Join-Path $testDir 'mt_assert.ps1') snapshot --window launch --version $Version
    if ($LASTEXITCODE -ne 0) { Write-MtWarn 'SNAPSHOT: 基线写入失败（用例断言可能落在陈旧偏移上）' }

    Write-MtOk 'LAUNCH' "已进入世界（quickplay=$world）"
    exit $MT_EXIT_PASS
}
