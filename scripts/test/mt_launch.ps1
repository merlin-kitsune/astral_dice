#Requires -Version 7.0
<#
.SYNOPSIS
    mt_launch — 启动 runClient 并等待进入世界（阶段 L），并在进入世界后执行「测试前清场」。

.DESCRIPTION
    就绪判据（**版本相关**，基础等待 30s 后每 15s 复检一次，180s 上限）:
      1.21.1 / 1.20.1 —— `Total time to load game and open world was`
      26.1.2          —— `logged in with entity id` **且** `Loaded <N> advancements`
                         （上游已删除前者那行；详见循环内的注释）
    出现崩溃报告或进程退出即判失败。

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

    **窗口搬移规则已全局移除（2026-09-17 用户裁决：「全局移除移动游戏窗口到第二屏幕的规则」）**：
    此前 launch 默认把客户端搬到显示器 #1（第二屏）并把客户区设成 1920x1080（由 `--monitor` /
    `--size` 控制）。现在**不再**做任何搬移或改尺寸 —— 窗口位置与大小由系统与游戏自身决定；
    这两个参数、搬移代码块与 `Mt.Win32.psm1` 的导入一并删除，日志里不再有 `MT_WINDOW:` 读数。
#>

$ErrorActionPreference = 'Stop'

$script:LibDir = Join-Path $PSScriptRoot 'lib'
Import-Module (Join-Path $script:LibDir 'Mt.Phase.psm1')
Import-Module (Join-Path $script:LibDir 'Mt.Paths.psm1')
Import-Module (Join-Path $script:LibDir 'Mt.Proc.psm1')
# 注：本文件**不再**导入 Mt.Win32.psm1 —— 它此前只服务于「把客户端搬到第二显示器」的规则，
# 该规则已于 2026-09-17 按用户裁决全局移除（窗口位置/尺寸由系统与游戏自身决定）。

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

    if ($env:MT_ASSETS_ABSENT -eq '1') {
        Write-MtWarn 'PREFLIGHT_OP: SKIPPED — MT_ASSETS_ABSENT=1（2026-09-20 测试资产清零期降级，S2）：探针已删除，跳过 opprobe / APDUMP 闸门；该模式下任何输出不构成验收证据'
        return [pscustomobject]@{ Code = 'OK'; Detail = 'skipped (MT_ASSETS_ABSENT=1)' }
    }

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
    # ⚠️ `--monitor` / `--size` 与「把窗口搬到第二显示器并设 1920x1080」的规则已于 2026-09-17
    # **全局移除**（用户裁决）；客户端窗口位置/尺寸改由系统与游戏自身决定，本脚本不再干预。

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
            # ⚠️ 2026-09-16：MC 26.1.2（NeoForge 26.1.2.x）的客户端入口是
            #    net.neoforged.fml.startup.Client，漏了它同样会漏检 26.1.2 客户端。
            ($c.Contains('net.minecraft.client.main.Main') -or $c.Contains('bootstraplauncher') -or
             $c.Contains('net.neoforged.fml.startup.Client'))
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
    # ③ debug.log 也要清零（2026-09-17 新增）：**1.20.1 的「模组是否已加载」判据只存在于
    #    `logs/debug.log`**（Forge 不打印 NeoForge 那种 `显示名 版本 (modId)` 清单行，实测把
    #    已装载的史莱姆压制模组判成缺失、硬失败）。留着上一轮的 debug.log 会让三个模组闸门
    #    把**本轮没装**的模组判成已装载 —— 与 latest.log 的假阳性是同一类。
    #    即使删不掉也不阻塞：Forge 的 log4j2 配置带 `OnStartupTriggeringPolicy`，启动时会把旧
    #    debug.log 卷走（`debug-%i.log.gz`），故本文件读完必然是**本轮**内容。
    $dbgLogPath = Join-Path $p.logs_dir 'debug.log'
    if (Test-Path -LiteralPath $dbgLogPath -PathType Leaf) {
        Remove-Item -LiteralPath $dbgLogPath -Force -ErrorAction SilentlyContinue
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

    # ── 全局测试规则「禁止游戏失焦打开 ESC 菜单」（2026-09-17 用户裁决）────────────────
    # 每次冷启动前强制写 `options.txt` 的 `pauseOnLostFocus:false`。**必须在启动之前**：
    # 游戏退出时会重写该文件，运行期写会被覆盖。失焦暂停会让后台注入（mt_inject）与截图
    # （mt_capture）全部失效，并且暂停菜单会顶在画面上 —— 历史上正是为了关它才在流程里塞进
    # 多余的 Esc 按键；本规则生效后那些 Esc 一律删除（见 mt_inject 的 ESC_SKIP 闸门）。
    # 查询/手改入口：`mt_env.ps1 debug --version <V> --pause-lock status|on|off`。
    [void](Set-MtPauseOnLostFocus -Paths $p -Enabled $false)
    Write-MtInfo 'PAUSE_LOCK: on — 失焦不再打开 ESC 暂停菜单（pauseOnLostFocus=false，已在本次冷启动前写入）'

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

    # 基础等待：2026-09-17 用户裁决「严格控制游戏进程捕获时长」⇒ **取消固定 30s 空等**，
    # 改为立即开始 3s 轮询（读日志是纯文件读，成本可忽略），硬上限仍 180s。
    # 为什么可以这样改：固定 30s 的唯一目的是「等 ModernFix 的 `Total time to load…` 行有机会出现」，
    # 而轮询本身就等价且更快 —— 实测就绪用时 47s，其中整整 30s 是这段空等。
    $readyBudget = 180
    if ($env:MT_LAUNCH_READY_TIMEOUT_SEC) {
        $rb = 0
        if ([int]::TryParse($env:MT_LAUNCH_READY_TIMEOUT_SEC, [ref]$rb) -and $rb -ge 30) { $readyBudget = $rb }
    }
    Write-MtInfo ("READY_WAIT: budget={0}s（轮询间隔 3s；不再有固定空等）" -f $readyBudget)

    $deadline = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds() + $readyBudget
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
        # 就绪判据（**版本相关**）：
        #   1.21.1 / 1.20.1 —— 原版客户端进入世界后打印
        #     `Total time to load game and open world was`（沿用既有口径）。
        #   26.1.2（2026-09-16 实测）—— 该行**已从上游移除**：整个 latest.log 里搜不到
        #     `load game` / `open world` 任何形式。改用两条**英文原版**标记同时成立：
        #       ① `logged in with entity id`（服务端已把玩家实体放进世界，PlayerList）
        #       ② `Loaded <N> advancements`（客户端已收到世界数据，AdvancementTree）
        #     ⚠️ 不要用 `加入了游戏` 之类**本地化**文案（本机客户端是 zh_cn，换语言即失效），
        #        也不要用 `Loaded ` 这种过宽的串（启动期的 `Loaded 0 entity animations` 会误命中）。
        $ready = $false
        if ($logFresh) {
            if ($Version -eq '26.1.2') {
                $ready = $latest.Contains('logged in with entity id') -and ($latest -match 'Loaded \d+ advancements')
            } else {
                $ready = $latest.Contains('Total time to load game and open world was')
            }
        }
        if ($ready) { $entered = $true; break }

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

        Start-Sleep -Seconds 3
    }

    if (-not $entered) {
        Write-MtBlocked 'launch' ("未在时限内进入世界（readiness 预算 {0}s）" -f $readyBudget)
        exit $MT_EXIT_BLOCKED
    }
    $readyElapsed = [int]((Get-Date) - $launchStartedAt).TotalSeconds
    Write-MtInfo ("MT_TIMING: world-ready={0}s（预算 {1}s；不含后续 KubeJS/OP 闸门与清场）" -f $readyElapsed, $readyBudget)

    # 就绪标记可能「先满足、后崩溃」（2026-09-17 实测）：26.1.2 开启光影时客户端在
    # 世界渲染首帧崩 `Missing sampler Sampler1`（2026-09-17 复现；**同日已定性为 Sodium 0.9.2 引起**：
    # 降到整合包同款 0.9.1 后光影正常，见 mt_env 的 Install-MtRenderStack .NOTES），而 `logged in with entity id` 早已写入日志
    # ⇒ 若不在标记满足后再查一次崩溃报告，就会带着「已就绪」的假象继续搬窗口/注入，
    # 下游只会报出一串与真因无关的「客户端未在运行 / 注入失败」。
    $postCrashes = @()
    if (Test-Path -LiteralPath $p.crash_dir -PathType Container) {
        $postCrashes = @(Get-ChildItem -LiteralPath $p.crash_dir -File -Filter '*.txt' |
            Where-Object { $_.LastWriteTime -gt $launchStartedAt })
    }
    if ($postCrashes.Count -gt 0) {
        Write-MtError 'launch' ("进入世界后立即崩溃，见 crash-reports/{0}" -f $postCrashes[0].Name)
        exit $MT_EXIT_ERROR
    }

    # ── 窗口搬移规则已**全局移除**（2026-09-17 用户裁决：「全局移除移动游戏窗口到第二屏幕的规则」）──
    # 历史：本步骤默认把客户端搬到显示器 #1（第二屏）并把客户区调成 1920x1080（`--monitor` / `--size`）。
    # 现在**不再**做任何搬移/改尺寸：客户端窗口由它自己决定位置与大小（单屏/多屏都保持系统给的位置）。
    # 同时删除了 `--monitor` / `--size` 两个参数与 `Mt.Win32` 的导入（本文件不再需要窗口搬运能力）。
    # 需要截图/注入的工具各自按「当前前台窗口」工作，不依赖这个搬移结果；故移除后流程不受影响。

    # ── 跨零点日切兜底（2026-09-18 t20）────────────────────────────────────────────
    # 只读 latest.log 会在「跨零点冷启动」时假阴性：latest.log 被 log4j 按日期日切后，会话启动期写下的
    # 「已加载模组清单」（`Mod List:` 与括号 modId 行）整块留在 `logs/<yyyy-MM-dd>-<n>.log.gz` 里
    # （实测 `run/1.21.1/logs/2026-09-17-1.log.gz`，mtime 00:00:00，内含 23:59:53 的 `Mod List:` 与
    # `Superflat World No Slimes 3.5 (superflatworldnoslimes)`），而新 latest.log 从 00:00:01 起写。
    # 环境/装载类判据改读 `$latestSession`（= latest.log + **本次启动之后**被日切出去的同会话片段，
    # 由 `Read-MtLogWithRotation` 按 mtime ≥ $launchStartedAt 挑选 ⇒ 不会串到上一会话）。
    # ⚠️ **就绪判据不在此列**：`$latest`（第 368 行那次读取）必须只认本次会话写进 latest.log 的
    #    「进入世界」行，回退到轮转日志会把上一会话的进入世界行当成本轮就绪。
    # 判据强度不变：下面用的仍是原来的正则/匹配口径，只是文本多了「同会话被切走的那一段」。
    $latestSession = Read-MtLogWithRotation -Path $p.latest_log -LogsDir $p.logs_dir -Since $launchStartedAt
    # 下方「环境/装载」类读数一律改用本会话文本（含日切片段）：覆盖
    # `Test-MtModLoaded`（史莱姆压制硬闸门 + 优化类模组）与 Sodium/Iris/KubeJS/Rhino/Embeddium/Oculus/
    # `Using shaderpack` 全部读数 —— 只此一行，各判据的匹配口径（含**带括号 modId**）一字未改。
    $latest = $latestSession

    # ── 「模组是否已加载」的统一判据（三条线日志格式**不同**，2026-09-17 实测校准）────────────
    # NeoForge（1.21.1 / 26.1.2）：latest.log 里有已加载模组清单行 `显示名 版本 (modId)`
    #   ⇒ 判据 = **括号里的 modId**。
    # Forge（1.20.1）：**没有那一行**（实测 latest.log 里 `Collective` 只有一行 modloading-worker
    #   日志，其余全无）。Forge 的模组清单只出现在 `logs/debug.log` / 启动控制台里的
    #   `Found valid mod file <file> with {modId,…} mods - versions {…}` ⇒ 判据 = **花括号里的 modId**。
    #   ⚠️ 2026-09-17 踩坑：1.20.1 沿用 `\(modId\)` 判据 ⇒ 史莱姆压制闸门硬失败
    #   （`未检测到「Superflat World No Slimes」模组`），而 debug.log 里它明明已装载。
    # 两种格式都**只认清单行**、不认裸名字：存档 `level.dat` 记着上次带着这些模组跑过时，加载器
    #   会打印 `<modId> (version X -> MISSING)` —— 只搜名字会把「缺失」误判成「已加载」（踩过）。
    $loadedModIds = @()
    if ($Version -eq '1.20.1') {
        # 两个来源取并集：debug.log（Forge 权威清单）+ 启动控制台日志（同一批行，冗余兜底）。
        $scanTargets = @((Join-Path $p.logs_dir 'debug.log'), (Join-Path $p.run_dir 'runclient_launch.log'))
        $ids = New-Object System.Collections.Generic.HashSet[string]
        foreach ($t in $scanTargets) {
            if (-not (Test-Path -LiteralPath $t -PathType Leaf)) { continue }
            foreach ($m in (Select-String -LiteralPath $t -Pattern 'Found valid mod file .* with \{([^}]*)\} mods' -AllMatches)) {
                foreach ($g in $m.Matches) {
                    foreach ($id in ($g.Groups[1].Value -split ',')) {
                        $id = $id.Trim()
                        if ($id) { [void]$ids.Add($id) }
                    }
                }
            }
        }
        $loadedModIds = @($ids)
    }
    function Test-MtModLoaded {
        param([Parameter(Mandatory)][string] $ModId)
        if ($Version -eq '1.20.1') { return ($loadedModIds -contains $ModId) }
        return [bool]($latest -imatch ('\(' + [regex]::Escape($ModId) + '\)'))
    }

    # 兼容性信号（1.21.1: Sodium/Iris；1.20.1: Embeddium/Oculus）
    if ($Version -eq '1.21.1') {
        if ($latest.Contains('Sodium')) { Write-MtInfo 'SODIUM_LOADED=true' } else { Write-MtWarn 'SODIUM_LOADED=false' }
        if ($latest.Contains('Iris')) { Write-MtInfo 'IRIS_LOADED=true' } else { Write-MtWarn 'IRIS_LOADED=false' }
    } elseif ($Version -eq '26.1.2') {
        # 26.1.2 dev run 自 2026-09-17 起也带渲染栈（Sodium + Iris + Complementary Unbound 光影，
        # 见 mt_env 的 Install-MtRenderStack）：既报探针宿主(KubeJS/Rhino)是否装载（本版本测试链的硬前提），
        # 也报渲染栈/光影是否真的加载。
        if ($latest.Contains('KubeJS')) { Write-MtInfo 'KUBEJS_LOADED=true' } else { Write-MtWarn 'KUBEJS_LOADED=false' }
        if ($latest -imatch 'Rhino') { Write-MtInfo 'RHINO_LOADED=true' } else { Write-MtWarn 'RHINO_LOADED=false' }
        if ($latest -imatch 'Sodium') { Write-MtInfo 'SODIUM_LOADED=true' } else { Write-MtWarn 'SODIUM_LOADED=false' }
        if ($latest -imatch 'Iris') { Write-MtInfo 'IRIS_LOADED=true' } else { Write-MtWarn 'IRIS_LOADED=false' }
        # 优化类模组（2026-09-17 用户要求：ImmediatelyFast + ModernFix 兼容性验证）。判据统一走
        # `Test-MtModLoaded`（**版本相关**：NeoForge 读 latest.log 的 `(modId)` 清单行，Forge 读
        # debug.log 的 `{modId}` 清单行），只认**清单行**、不认裸名字，避免把存档里「MISSING」的
        # 旧模组记录误判成已加载（2026-09-17 在史莱姆压制闸门上踩过这个坑）。
        # 这两条**不**做硬失败（与 Sodium/Iris 一致）：它们是兼容性验证对象，缺装载时给出 WARN 即可，
        # 但要看得见 —— 否则「验证」会静默地什么都没验证。
        if (Test-MtModLoaded 'immediatelyfast') { Write-MtInfo 'IMMEDIATELYFAST_LOADED=true' } else { Write-MtWarn 'IMMEDIATELYFAST_LOADED=false(优化模组兼容性验证未生效)' }
        if (Test-MtModLoaded 'modernfix') { Write-MtInfo 'MODERNFIX_LOADED=true' } else { Write-MtWarn 'MODERNFIX_LOADED=false(优化模组兼容性验证未生效)' }
        if (Test-MtModLoaded 'ferritecore') { Write-MtInfo 'FERRITECORE_LOADED=true' } else { Write-MtWarn 'FERRITECORE_LOADED=false(优化模组兼容性验证未生效)' }
        # 光影状态：以 config/iris.properties 为准（而不是「日志里有没有出现过 shaderpack 字样」）。
        # 默认 enableShaders=false —— 26.1.2 上启用光影会崩（见 mt_env 的 Install-MtRenderStack 注释），
        # 故默认不启用是**正常状态**，不该报 WARN；启用时才用日志确认包真的加载成功。
        $irisCfg = Join-Path (Join-Path $p.run_dir 'config') 'iris.properties'
        $irisEnabled = $false
        $irisPack = ''
        if (Test-Path -LiteralPath $irisCfg -PathType Leaf) {
            foreach ($ln in (Get-Content -LiteralPath $irisCfg)) {
                if ($ln -match '^\s*enableShaders\s*=\s*(.+?)\s*$') { $irisEnabled = ($Matches[1] -ieq 'true') }
                if ($ln -match '^\s*shaderPack\s*=\s*(.+?)\s*$') { $irisPack = $Matches[1] }
            }
        }
        if (-not $irisEnabled) {
            Write-MtInfo ("SHADERS=disabled(iris.properties){0}" -f $(if ($irisPack) { " pack=$irisPack" } else { '' }))
            Write-MtInfo 'SHADERPACK_LOADED=n/a(光影未启用)'
        } elseif ($latest -imatch '(?i)Using shaderpack:\s*\S+') {
            Write-MtInfo 'SHADERS=enabled'
            Write-MtInfo 'SHADERPACK_LOADED=true'
        } else {
            Write-MtInfo 'SHADERS=enabled'
            Write-MtWarn 'SHADERPACK_LOADED=false(已启用光影但日志中没有 Using shaderpack 行)'
        }
    } else {
        # -qi：bash 侧是大小写不敏感匹配
        if ($latest -imatch 'Embeddium') { Write-MtInfo 'EMBEDDIUM_LOADED=true' }
        else { Write-MtInfo 'EMBEDDIUM_LOADED=false(dev run 预期)' }
        if ($latest -imatch 'Oculus') { Write-MtInfo 'OCULUS_LOADED=true' }
        else { Write-MtInfo 'OCULUS_LOADED=false(dev run 预期)' }
    }

    # ── 光影读数（**1.20.1 / 1.21.1**；26.1.2 已在上面的分支里按 `config/iris.properties` 判定）──────
    # 用户硬性要求（2026-09-17）：光影包必须存在且**默认启用**。判据取 Iris/Oculus 的
    # `Using shaderpack: <包名>` 行。⚠️ 该行**出现在进入世界之后**（1.20.1 实测：启动第 12s 先打印
    # 「Shaders are disabled because no valid shaderpack is selected」，进世界后第 36s 才
    # 「Using shaderpack: ComplementaryUnbound_r5.9.3.zip」）⇒ 必须在本闸门（已进世界）读 latest.log，
    # 放到启动早期判定必然误报。不做硬失败（与 Sodium/Iris/优化类模组同口径）：缺该行给 WARN，但要看得见。
    if ($Version -ne '26.1.2') {
        if ($latest -imatch '(?i)Using shaderpack:\s*(\S+)') {
            Write-MtInfo ("SHADERS=enabled pack={0}" -f $Matches[1])
            Write-MtInfo 'SHADERPACK_LOADED=true'
        } else {
            Write-MtWarn ("SHADERPACK_LOADED=false(未见 `Using shaderpack:` 行；确认 run\{0}\shaderpacks 里有包且已在光影加载器配置里启用)" -f $Version)
        }
    }

    # ── 优化类模组 + 超平坦史莱姆压制：**三条线统一**（2026-09-17 用户要求）──────────────
    # 用户两轮原话：「1.21.1 环境缺少没有史莱姆的超平坦世界模组，这是必需的模组，没有会使史莱姆
    # 干扰测试，补全该模组然后重新运行 1.21.1 测试」+「所有测试环境增加 ImmediatelyFast、FerriteCore
    # 模组，用于优化模组兼容性测试」。
    if ($Version -ne '26.1.2') {
        # 26.1.2 的三条读数已在上面的分支里打印（同口径），此处只补另两条线。
        # 判据取**已加载模组列表行**里的括号 modId（`(immediatelyfast)` / `(ferritecore)`），
        # 避免把存档里 `… -> MISSING` 的旧记录误判成已加载（2026-09-17 踩过这个坑）。
        # 与 Sodium/Iris 一致**不做硬失败**：它们是被验证对象，缺装载给 WARN 但要看得见。
        if (Test-MtModLoaded 'immediatelyfast') { Write-MtInfo 'IMMEDIATELYFAST_LOADED=true' } else { Write-MtWarn 'IMMEDIATELYFAST_LOADED=false(优化模组兼容性验证未生效)' }
        if (Test-MtModLoaded 'ferritecore') { Write-MtInfo 'FERRITECORE_LOADED=true' } else { Write-MtWarn 'FERRITECORE_LOADED=false(优化模组兼容性验证未生效)' }
        if ($Version -eq '1.21.1') {
            if (Test-MtModLoaded 'modernfix') { Write-MtInfo 'MODERNFIX_LOADED=true' } else { Write-MtWarn 'MODERNFIX_LOADED=false(优化模组兼容性验证未生效)' }
        }
    }

    # 超平坦世界史莱姆压制（2026-09-17 用户硬性要求，**三条线统一**）：测试环境**必须**装载，
    # 否则超平坦世界 y<40 的史莱姆区块会持续刷怪、污染实体类读数。缺装载 ⇒ 硬失败（ERROR），
    # 而不是带着会被史莱姆污染的现场继续跑用例。
    # 装载判据 = `Test-MtModLoaded`（**版本相关**，见函数上方注释）：NeoForge 匹配 latest.log 里
    # 「已加载模组列表」行的 `显示名 版本 (modId)`，即**括号里的 modId**
    # （`(superflatworldnoslimes)` / `(collective)`）；Forge 1.20.1 改用 debug.log 的
    # `Found valid mod file … with {modId} mods`，即**花括号里的 modId** —— Forge **没有**括号清单行，
    # 2026-09-17 沿用括号判据导致本闸门把**已装载**的模组判成缺失、硬失败（实测踩坑）。
    # ⚠️ 两个格式都只认清单行、不能只搜 `superflatworldnoslimes`：存档 `level.dat` 记着上次带着
    #    这些模组跑过，缺少时加载器会打印 `<modId> (version X -> MISSING)` —— 只搜名字会把
    #    「**缺失**」误判成「已加载」，闸门形同虚设（实测那次把已移出的对照实验误报成
    #    SLIMEGUARD_LOADED=true）。
    # 来源（三条线不同，见 mt_env 的 $script:SlimeGuardByVersion）：26.1.2 / 1.21.1 由 mt_env 从
    # Modrinth Maven 下载；**1.20.1 由 forge-1.20.1/build.gradle 的 modImplementation 提供**
    # （再往 run/1.20.1/mods 放一份会被 FML 判重复模组）。
    $slimeGuard = (Test-MtModLoaded 'superflatworldnoslimes')
    $collective = (Test-MtModLoaded 'collective')
    if (-not $slimeGuard) {
        # 唯一的例外：**故意**测「没有该模组时史莱姆会不会干扰」的对照实验。
        # 必须是显式开关，且会留下 WARN 痕迹 —— 默认永远是硬失败。
        if ($env:MT_ALLOW_NO_SLIMEGUARD -eq '1') {
            Write-MtWarn 'SLIMEGUARD_LOADED=false — 已按 MT_ALLOW_NO_SLIMEGUARD=1 显式放行（对照实验用；此时超平坦世界的史莱姆**不受压制**）'
        } else {
            Write-MtErrLine ("MT_LAUNCH: ERROR — 未检测到「Superflat World No Slimes」模组（测试环境硬性要求：超平坦世界的史莱姆会干扰测试流程）；先执行 pwsh -File scripts/test/mt_env.ps1 mods --version {0}（如确需对照实验，设 MT_ALLOW_NO_SLIMEGUARD=1）" -f $Version)
            exit $MT_EXIT_ERROR
        }
    } else {
        Write-MtInfo 'SLIMEGUARD_LOADED=true'
        if ($collective) { Write-MtInfo 'COLLECTIVE_LOADED=true' } else { Write-MtWarn 'COLLECTIVE_LOADED=false(Collective 前置缺失？史莱姆压制可能未生效)' }
    }

    # ── 禁用生物 AI 硬闸门（2026-09-18 用户裁决「测试流程未禁用生物 AI，这是严重失误」）────
    # 规则：测试环境**必须**禁用生物 AI。此前只有「进入世界后清场一次 + 探针靶子自设 noAi」，
    # 自然刷新的生物仍带 AI —— 会主动接近/攻击/推挤玩家、投掷弹射物、踩压力板、引爆苦力怕，
    # 污染「世界级差值」读数（`self` 施放者 HP、`bolt_delta` 全局雷击计数、实体计数），
    # 甚至把玩家打死 ⇒ 用例随机失败或拿到假读数。这是**流程缺陷**，不是被测行为。
    # 实施：`scripts/test/resources/kubejs/<ver>/server_scripts/astral_test_noai.js`
    #   —— 由 `mt_env` 的 kubejs 子命令同步（env 阶段自动做），每 2 tick 横扫玩家周围 128 格内
    #   的 Mob 强制 `setNoAi(true)`，并在 `EntityEvents.spawned` 上即时生效；前 30 秒每 5 秒
    #   回报一行 `AP_NOAI:mobs=<n>:noai=<n>:radius=<r>:forced=<n>:total=<n>:tick=<t>`。
    # 判据：latest.log 里读到 `AP_NOAI:` 行 **且** `mobs == noai`（该半径内不存在仍带 AI 的 Mob）；
    #   取不到读数（脚本未同步 / 未生效 / 取不到 server）一律**硬失败**，绝不带着会被 AI 污染的
    #   现场继续跑用例。唯一例外：显式设 `MT_ALLOW_MOB_AI=1`（对照实验用，留 WARN 痕迹）。
    # ⚠️ 本闸门**只**禁 AI、不禁刷怪：史莱姆由上面的 SLIMEGUARD 闸门负责，且**禁止**用
    #    `/gamerule doMobSpawning false` 代替 —— 那会让 `/astralprobe slimecheck` 的对照读数恒为 0。
    if ($env:MT_ASSETS_ABSENT -eq '1') {
        Write-MtWarn 'NOAI_ENFORCED=SKIPPED — MT_ASSETS_ABSENT=1（2026-09-20 测试资产清零期降级，S2）：astral_test_noai.js 已删除，跳过禁AI硬闸门；world-level 读数有被自然刷怪 AI 污染的风险'
    } elseif ($env:MT_ALLOW_MOB_AI -eq '1') {
        Write-MtWarn 'NOAI_ENFORCED=false — 已按 MT_ALLOW_MOB_AI=1 显式放行（此时自然刷新的生物仍带 AI，world-level 差值读数有被污染的风险）'
    } else {
        $noAiLine = ''
        $noAiOk = $false
        $noAiDeadline = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds() + 30
        while ([DateTimeOffset]::UtcNow.ToUnixTimeSeconds() -lt $noAiDeadline) {
            $latest = Read-MtSharedText -Path $p.latest_log
            $noAiLine = ''
            foreach ($ln in ($latest -split "`r?`n")) {
                if ($ln -match 'AP_NOAI:ERR:') { $noAiLine = $ln.Trim(); break }
                $mNoAi = [regex]::Match($ln, 'AP_NOAI:mobs=(\d+):noai=(\d+):radius=(\d+):forced=(\d+)')
                if ($mNoAi.Success) {
                    $noAiLine = $mNoAi.Value
                    $noAiOk = ([int]$mNoAi.Groups[1].Value -eq [int]$mNoAi.Groups[2].Value)
                    break
                }
            }
            if ($noAiLine -ne '') { break }
            Start-Sleep -Seconds 1
        }
        if ($noAiOk) {
            Write-MtInfo ("NOAI_ENFORCED=true — {0}" -f $noAiLine)
        } else {
            $why = if ($noAiLine -match 'AP_NOAI:ERR:') { "脚本报错：$noAiLine" }
                   elseif ($noAiLine -ne '') { "读到 $noAiLine（仍有 Mob 带 AI）" }
                   else { '30s 内未读到 AP_NOAI 行（脚本未同步 / 未生效）' }
            Write-MtErrLine ("MT_LAUNCH: ERROR — 未确认「生物 AI 已禁用」（测试环境硬性要求）：{0}；先执行 pwsh -File scripts/test/mt_env.ps1 kubejs --version {1} 同步脚本后**冷启动**（如确需带 AI 的对照实验，设 MT_ALLOW_MOB_AI=1）" -f $why, $Version)
            exit $MT_EXIT_ERROR
        }
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
    # 清场生效证据（2026-09-20 S7：双语判据 + 回显）：原版反馈「杀死了N个实体 / 未找到实体」
    # （中文 locale）或「Killed N entities / No entity was found」（英文 locale）。此前只按注入
    # 返回码判 OK/WARN，命令未被执行时无法自证；此处补读反馈行，读不到仅 WARN 不硬失败。
    $precleanEv = 'not-found'
    $evText = ''
    try { $evText = Read-MtSharedText -Path $p.latest_log } catch { $evText = '' }
    if ($evText -match '杀死了(\d+)个实体') { $precleanEv = "zh:killed=$($Matches[1])" }
    elseif ($evText -match '未找到实体') { $precleanEv = 'zh:none' }
    elseif ($evText -match 'Killed (\d+) entities') { $precleanEv = "en:killed=$($Matches[1])" }
    elseif ($evText -match 'No entity was found') { $precleanEv = 'en:none' }
    if ($precleanEv -eq 'not-found') {
        Write-MtWarn ("PRECLEAN_EVIDENCE: not-found — 未读到清场原版反馈（双语判据均未命中，注入返回码 {0}）；清场是否真执行无法自证" -f ($precleanRc -join '/'))
    } else {
        Write-MtInfo ("PRECLEAN_EVIDENCE: {0}" -f $precleanEv)
    }
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
