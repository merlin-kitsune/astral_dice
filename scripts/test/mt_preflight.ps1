#Requires -Version 7.0
<#
.SYNOPSIS
    mt_preflight — 前置检查（阶段 P）。

.DESCRIPTION
    在启动任何游戏进程之前把「环境不满足」与「功能缺陷」分开：
    本模块失败一律 exit 10（PREFLIGHT），绝不触达游戏，避免环境问题被误判为测试失败。

.EXAMPLE
    pwsh -File scripts/test/mt_preflight.ps1 --version 1.21.1
    pwsh -File scripts/test/mt_preflight.ps1 --all

.NOTES
    迁移前源文件 scripts/test/mt_preflight.py（该原件已在 92fbeaf「工具链收敛为纯 pwsh」删除，取回：`git show 92fbeaf^:scripts/test/mt_preflight.py`）。

    依赖 `lib/Mt.Win32.psm1`（输入语言的 Win32 平台层；python 侧对应 mt_ime 的
    `langid_of_thread` / `en_us_available` / `describe` / `KLID_EN_US`）。

    有意偏差：
      1. 提示文案里的入口由 `mt.sh` 换成 `mt.ps1`（同一入口的新名字）；
      2. python 的 `sys.platform != "win32"` 分支（pgrep 版遗留进程检查）随
         「测试链仅 Windows」一并删除 —— 与 Mt.Proc.psm1 / mt_ime.ps1 的同类取舍一致；
      3. 自动收停走**点源** mt_cleanup.ps1 后直接调 `Invoke-MtRunCleanup`（等价于 python 的
         `import mt_cleanup; mt_cleanup.run_cleanup(quiet=True)`，仍是同一份实现，不复制逻辑）。
#>

$ErrorActionPreference = 'Stop'

$script:LibDir = Join-Path $PSScriptRoot 'lib'
Import-Module (Join-Path $script:LibDir 'Mt.Phase.psm1')
Import-Module (Join-Path $script:LibDir 'Mt.Paths.psm1')
Import-Module (Join-Path $script:LibDir 'Mt.Proc.psm1')
Import-Module (Join-Path $script:LibDir 'Mt.Win32.psm1')

Initialize-MtConsole

$script:ExitPreflight = 10
# 分支**不再是前置门槛**(2026-09-17 用户裁决:「移除二重验证白名单，允许该分支执行」)。
# 本流程在任何分支上都可运行;这里保留一份「发布线分支」清单,只用于回显与告警标注,
# **不参与任何判定**(判定恒为放行,见 Get-MtPreflightBranch)。
$script:ReleaseLineBranches = @('multi-1.20.1-1.21.1')
# 失败取证标记：D4-2（2026-10-17）起按**线**分文件（`.mt_keep_alive-<版本>`），
# 旧的无后缀文件仍被识别（兼容历史现场），但它无法区分版本 ⇒ 对所有线都生效。
$script:KeepAliveLegacy = Join-Path (Join-Path (Get-MtTestDir) 'cases') '.mt_keep_alive'
$script:KLID_EN_US = '00000409'

# ── 分支（信息性：回显 + 告警，不拦停）────────────────────────────────────
function Get-MtPreflightBranch {
    <#
    .SYNOPSIS
        回显当前分支（信息性检查，不再拦停）。返回 @(bool, detail)。

    .NOTES
        2026-09-17 用户裁决「移除二重验证白名单，允许该分支执行」：分支检查由**强断言**改为
        **回显 + 告警** —— 任何分支（发布线 / 开发分支 / detached HEAD）都放行，第一项恒为 $true，
        输出里只如实标注实际分支名；发布线分支直接回显，其它分支追加 WARN 说明。
        连分支名都读不出来时同样不拦停（非 git 目录照常可跑），只把原因写进说明。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Root)

    $r = Invoke-MtProcessFull -FilePath 'git' `
        -ArgumentList @('-C', $Root, 'rev-parse', '--abbrev-ref', 'HEAD') -TimeoutSec 60
    if ($r.ExitCode -ne 0) {
        return , @($true, "无法读取分支（$($r.StdErr.Trim())）—— 信息性检查，不拦停")
    }
    $branch = $r.StdOut.Trim()
    if (-not $branch) {
        return , @($true, '分支名读出为空 —— 信息性检查，不拦停')
    }
    if ($script:ReleaseLineBranches -contains $branch) {
        return , @($true, "$branch（发布线分支）")
    }
    return , @($true, ("$branch —— WARN: 非发布线分支（发布线 $($script:ReleaseLineBranches -join ' / ')）；" +
                '按 2026-09-17 用户裁决放行，不拦停，仅告警'))
}

# ── 输入语言 ──────────────────────────────────────────────────────────────
function Test-MtPreflightIme {
    <#
    .SYNOPSIS
        输入语言前置。返回 @(bool, detail)。

    .NOTES
        硬要求只有一条：**en-US 布局在本机可用**（已安装且能加载）。因为注入按美式扫描码
        表投递，且注入前会把**目标窗口所在线程**自动切到 en-US（见 mt_ime.ps1）。
        系统当前输入法**不再要求**是 en-US —— 那是旧的真实按键注入时代留下的人肉前置。
    #>
    [CmdletBinding()]
    param()

    $cur = Get-MtLangIdOfThread -ThreadId 0
    if (-not (Test-MtEnUsLayoutAvailable)) {
        return , @($false, 'en-US 键盘布局不可用 —— 请在系统设置安装「英语(美国)」键盘（注入按美式扫描码投递，并在注入前自动切换目标窗口）')
    }
    return , @($true, ("en-US 布局可用（KLID {0}）；当前系统输入法为 {1} —— 无需手动切换，注入前自动切目标窗口" -f `
                $script:KLID_EN_US, (Get-MtLayoutDescription -LangId $cur)))
}

# ── 遗留进程 ──────────────────────────────────────────────────────────────
function Get-MtPreflightKeepAlivePath {
    <#
    .SYNOPSIS
        某条线的失败取证标记路径（与 mt_case / mt_cleanup 同一命名）。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Version)

    return (Join-Path (Join-Path (Get-MtTestDir) 'cases') ('.mt_keep_alive-' + $Version))
}

function Get-MtPreflightKeepAliveReason {
    <#
    .SYNOPSIS
        读取本线失败取证标记（优先分线文件，其次兼容旧的无后缀文件）；无标记返回 $null。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Version)

    foreach ($p in @((Get-MtPreflightKeepAlivePath -Version $Version), $script:KeepAliveLegacy)) {
        if (-not (Test-Path -LiteralPath $p -PathType Leaf)) { continue }
        $why = '（未记录原因）'
        try {
            $t = (Get-Content -LiteralPath $p -Raw -Encoding UTF8).Trim()
            if ($t) { $why = $t }
        } catch { $why = '（读取标记失败）' }
        return ("$p ：$why")
    }
    return $null
}

function Get-MtPreflightPreview {
    <#
    .SYNOPSIS
        命令行预览（160 字符，避免把整条 gradle 命令行灌进失败详情）。
    #>
    [CmdletBinding()]
    param([AllowEmptyString()][string]$Text)

    if (-not $Text) { return '' }
    if ($Text.Length -le 160) { return $Text }
    return $Text.Substring(0, 160)
}

function Get-MtPreflightProcessTable {
    <#
    .SYNOPSIS
        本次前置检查使用的进程表（Pid + CommandLine）。

    .NOTES
        **无游戏自测钩子**：设置 `MT_PREFLIGHT_TEST_PROCS`（JSON 数组，元素
        `{"pid":1234,"command_line":"..."}`）时**替换**真实进程表，用于在不起游戏/不真跑构建的
        前提下验证「本线构建包装器存在 ⇒ FAIL」「不存在 ⇒ OK」两个分支。生产环境不设置该变量。
        设置但 JSON 非法时告警并回退真实扫描（绝不让测试钩子静默改变生产判定）。
    #>
    [CmdletBinding()]
    param()

    $raw = $env:MT_PREFLIGHT_TEST_PROCS
    if ($null -ne $raw -and $raw.Trim() -ne '') {
        try {
            $out = @()
            foreach ($f in @($raw | ConvertFrom-Json)) {
                $cmd = ''
                if ($null -ne $f.PSObject.Properties['command_line']) { $cmd = [string]$f.command_line }
                elseif ($null -ne $f.PSObject.Properties['cmdline']) { $cmd = [string]$f.cmdline }
                $out += [pscustomobject]@{ Pid = [int]$f.pid; CommandLine = $cmd }
            }
            return $out
        } catch {
            Write-MtWarn ("MT_PREFLIGHT_TEST_PROCS 解析失败（回退真实进程扫描）：" + $_.Exception.Message)
        }
    }

    return @(Get-JavaProcesses | ForEach-Object {
            [pscustomobject]@{ Pid = [int]$_.Pid; CommandLine = [string]$_.CommandLine }
        })
}

function Get-MtPreflightGradleWrapperProcs {
    <#
    .SYNOPSIS
        本线「正在进行的 Gradle 包装器」进程：命令行含 `gradle-wrapper.jar` 且含
        `:<当前子项目>:`。返回 {Pid, CommandLine}。

    .NOTES
        2026-10-17 返工（恢复构建守卫，只读版）：
          · 旧 guard 是「发现任何 gradle 进程即 FAIL」—— 两条线并行时会因为**另一条线**的
            正常构建而互相阻塞，已删除；
          · 新 guard 只看**本线子项目选择器**（`:neoforge-1.21.1:` / `:forge-1.20.1:` …），
            因此另一条线的构建不再误伤；
          · **build 与 run 家族一律 FAIL**（`mt_build` 的 `:sub:build`、`mt_launch`/`mt_env` 的
            `:sub:runClient` / `:sub:runServer` 都算）⇒ **只报告不杀**：正在跑的构建被收停会毁
            产物，正在跑的 run 包装器也不该由前置检查擅自杀掉（要停请显式 `--phase stop`）；
          · 只有 `gradle-wrapper.jar` + 本线选择器才算；Gradle 守护（无 wrapper jar）不阻塞。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][psobject]$Paths,
        [object[]]$Processes
    )

    if ($null -eq $Processes) { $Processes = @(Get-MtPreflightProcessTable) }
    $selector = ':' + [string]$Paths.subproject + ':'
    $out = @()
    foreach ($p in @($Processes)) {
        $cmd = [string]$p.CommandLine
        if (-not $cmd.Contains('gradle-wrapper.jar')) { continue }
        if (-not $cmd.Contains($selector)) { continue }
        $out += [pscustomobject]@{ Pid = [int]$p.Pid; CommandLine = $cmd }
    }
    return $out
}

function Get-MtPreflightLeftoverScan {
    <#
    .SYNOPSIS
        扫描 java 进程：本线的（客户端/服务端/run 包装器）与其它 Minecraft 客户端。
        返回 @(ours, others)。

    .NOTES
        成员判定统一走 Mt.Proc 的 `Test-MtPipelineProcess`（= 窗口标题含本版本号 **或**
        命令行带本版本证据），与 mt_cleanup 的收停判据同一来源。**不能**只用命令行 marker：
        1.20.1 DevLaunch 的 `Win32_Process.CommandLine` 可能只剩一个类名（真实参数在 args 文件里），
        那样会让本线遗留客户端既检不出也杀不掉。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][psobject]$Paths,
        [object[]]$Processes
    )

    if ($null -eq $Processes) { $Processes = @(Get-MtPreflightProcessTable) }
    $ours = @(); $others = @()
    foreach ($p in @($Processes)) {
        $cmd = [string]$p.CommandLine
        if (Test-MtPipelineProcess -Paths $Paths -ProcessId ([int]$p.Pid) -CommandLine $cmd) { $ours += [int]$p.Pid }
        elseif ($cmd.ToLowerInvariant().Contains('minecraft')) { $others += [int]$p.Pid }
    }
    return , @($ours, $others)
}

function Test-MtPreflightLeftover {
    <#
    .SYNOPSIS
        遗留进程检查（**按线隔离**）：只扫本版本的进程，**先自动清理**，
        清不掉才硬阻塞。返回 @(bool, detail)。

    .NOTES
        2026-10-17（D4-2）口径：**独占粒度从「整机」改为「按线」**。两条线可以并行跑会话，
        真正的物理约束只有「客户端**启动**不能并发」（glfw.dll 原生崩溃）—— 那由 mt_launch 的
        全机短时启动锁负责，不在本阶段。
          本线遗留（dev 客户端 / Gradle 任务）→ **只收停本线**，否则同一 run 目录/日志会被争用；
          其它线客户端（或用户自己开的整合包）→ 仅提示，不阻塞（各线 run 目录、日志与窗口
            标题均按版本隔离，注入通道也按版本锁定窗口）。

        为什么在这里兜底：退出路径在 Windows 上并不可靠（任务管理器强杀、控制台直接关窗
        都不经过任何钩子），只靠退出钩子就等于把「进程泄漏」交给运气。因此再加一道**下次
        启动时生效**的兜底：检出的本流程遗留直接收停，让泄漏最多影响一次运行的干净度。

        唯一例外：本线的 `.mt_keep_alive-<版本>`（或兼容的无后缀 `.mt_keep_alive`）存在时
        **不自动清理**（那是失败时故意留的现场），改为 FAIL 并提示用 `--phase stop --force` 释放。

        2026-10-17 返工（只读构建守卫）：先查**本线 `:sub:` 选择器**的 Gradle 包装器进程
        （build 与 run 家族都算）⇒ 直接 FAIL，**只报告不杀**（构建中途收停会毁产物）。
        **不**恢复「任意 gradle 进程都阻塞」的旧口径：其它线 / Gradle 守护 / 无 wrapper 的 java
        进程都不受影响。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Version)

    $p = Get-MtPaths -Version $Version
    $procs = @(Get-MtPreflightProcessTable)
    $blocking = @(Get-MtPreflightGradleWrapperProcs -Paths $p -Processes $procs)
    if ($blocking.Count -gt 0) {
        $pidsText = (($blocking | ForEach-Object { $_.Pid }) -join ', ')
        $cmdText = (($blocking | Select-Object -First 2 | ForEach-Object { Get-MtPreflightPreview $_.CommandLine }) -join ' | ')
        return , @($false, ("本线（$Version）有正在进行的 Gradle 包装器进程 PID=$pidsText（$cmdText）—— " +
                    '本线 build/run 包装器在跑，**只报告不杀**；等它结束，或先执行 ' +
                    "pwsh -File scripts/test/mt.ps1 --version $Version --phase stop 收停本线测试进程"))
    }
    $scan = Get-MtPreflightLeftoverScan -Paths $p -Processes $procs
    $ours = @($scan[0]); $others = @($scan[1])

    if ($ours.Count -gt 0) {
        $reason = Get-MtPreflightKeepAliveReason -Version $Version
        if ($reason) {
            return , @($false, ("本线遗留 PID=$($ours -join ',')，但存在失败取证标记（$reason）——按设计保留现场；" +
                        "取证完成后执行 pwsh -File scripts/test/mt.ps1 --version $Version --phase stop --force"))
        }

        $stale = @($ours)
        Write-MtLine "  [ .. ] 遗留进程($Version): 检出 $($stale.Count) 个本线遗留进程（PID=$($stale -join ',')），自动收停中…"
        # 与 python 的 `import mt_cleanup; mt_cleanup.run_cleanup(quiet=True)` 等价：点源同一份实现。
        # ⚠️ 必须带 -Versions @($Version)：无版本参数会收停**全部线**，破坏并行隔离。
        . (Join-Path $PSScriptRoot 'mt_cleanup.ps1')
        [void](Invoke-MtRunCleanup -Versions @($Version) -Quiet $true)

        $scan2 = Get-MtPreflightLeftoverScan -Paths $p
        $ours = @($scan2[0]); $others = @($scan2[1])
        if ($ours.Count -gt 0) {
            return , @($false, ("自动收停后仍有本线遗留 PID=$($ours -join ',')，" +
                        "请手工执行 pwsh -File scripts/test/mt.ps1 --version $Version --phase stop 并检查权限"))
        }
        return , @($true, ("已自动收停 $($stale.Count) 个本线遗留进程（PID=$($stale -join ',')）——" +
                    '退出钩子失效时的兜底，不影响本次结论'))
    }

    if ($others.Count -gt 0) {
        return , @($true, ("无本线（$Version）遗留；另有 $($others.Count) 个其它 Minecraft 客户端在运行" +
                    "（PID=$($others -join ',')）——按线隔离放行，仅占用显存"))
    }
    return , @($true, "无本线（$Version）遗留进程")
}

# ── AutoJS（脚本上下文 MCP 宿主）─────────────────────────────────────────
function Get-MtAutoJsProvenanceHashes {
    <#
    .SYNOPSIS
        从 autojs/PROVENANCE.md 解析「jar 文件名 → SHA256」（只认 64 位十六进制）。

    .NOTES
        PROVENANCE 是**追加式**的（其它投放步骤各写各的小节），所以这里不假设表格列序：
        只要同一行里出现 文件名 + 64 位哈希就收下。解析不到就返回空表 —— 由调用方判 FAIL，
        不在这里编造「看起来对」的哈希。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Path)

    $map = @{}
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return $map }
    foreach ($line in @(Get-Content -LiteralPath $Path -Encoding utf8)) {
        $m = [regex]::Match($line, '([A-Za-z0-9._-]+\.jar).*?(?<![0-9a-fA-F])([0-9a-fA-F]{64})(?![0-9a-fA-F])')
        if ($m.Success) { $map[$m.Groups[1].Value] = $m.Groups[2].Value.ToUpperInvariant() }
    }
    return $map
}

function Test-MtPreflightAutoJs {
    <#
    .SYNOPSIS
        静态检查 AutoJS 通道的三件事：vendor jar 在位且哈希对得上 PROVENANCE、端点已配置、
        以及该线的 run/<版本>/mods 是否已投放（缺 ⇒ WARN，不阻塞）。

    .OUTPUTS
        @{ State = 'OK'|'WARN'|'FAIL'; Detail = <摘要> }（同时打印 MT_PREFLIGHT_AUTOJS 机器行）。

    .NOTES
        · 只做**静态**检查，不连网、不连端口 —— 端点是否真活着由 launch 阶段的就绪闸门判。
        · 第三条是 WARN 而非 FAIL：jar 只在冷启动前才必须落位（`mt_env mods` 会投），
          前置阶段就 FAIL 会把「还没装」和「真的坏了」混为一谈。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Root)

    $lines_out = [System.Collections.Generic.List[string]]::new()
    $issues = @()
    $warns = @()

    # 1) vendor jar + SHA256（对 PROVENANCE）
    $vendorDir = Join-Path (Join-Path (Get-MtTestDir) 'resources') 'mods/autojs'
    $provPath = Join-Path $vendorDir 'PROVENANCE.md'
    $provMap = Get-MtAutoJsProvenanceHashes -Path $provPath
    $wanted = @(
        [pscustomobject]@{ Version = '1.21.1'; File = 'AutoJS-neoforge-1.21.1-1.0.0.jar' },
        [pscustomobject]@{ Version = '26.1.2'; File = 'AutoJS-neoforge-26.1-1.0.0.jar' }
    )
    $vendorOk = 0
    foreach ($spec in $wanted) {
        $jar = Join-Path $vendorDir $spec.File
        if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) {
            $issues += ('vendor jar 缺失：“{0}”' -f $spec.File)
            continue
        }
        if (-not $provMap.ContainsKey($spec.File)) {
            $issues += ('PROVENANCE 未登记 “{0}”（无法复核哈希）' -f $spec.File)
            continue
        }
        $real = (Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash.ToUpperInvariant()
        if ($real -ne $provMap[$spec.File]) {
            $issues += ('{0} 哈希不符（实际 {1}，PROVENANCE {2}）' -f $spec.File, $real.Substring(0, 16), $provMap[$spec.File].Substring(0, 16))
            continue
        }
        $vendorOk++
    }

    # 2) 端点配置
    $conf = Get-MtConf
    $endpoint = ''
    if ($null -ne $conf -and $conf.ContainsKey('MT_AUTOJS_ENDPOINT')) { $endpoint = ([string]$conf['MT_AUTOJS_ENDPOINT']).Trim() }
    if (-not $endpoint) {
        $issues += 'mt.conf 未配置 MT_AUTOJS_ENDPOINT'
    } else {
        $okUrl = $false
        try {
            $u = [System.Uri]::new($endpoint)
            $okUrl = ($u.IsAbsoluteUri -and ($u.Scheme -eq 'http' -or $u.Scheme -eq 'https') -and $u.Host)
        } catch {
            $okUrl = $false
        }
        if (-not $okUrl) { $issues += ('MT_AUTOJS_ENDPOINT 不是合法 http(s) URL：{0}' -f $endpoint) }
    }

    # 3) 该线 run/<版本>/mods 是否已投放（WARN，不阻塞）
    foreach ($spec in $wanted) {
        $p = Get-MtPaths -Version $spec.Version
        $present = @(Get-ChildItem -LiteralPath $p.mods_dir -File -Filter 'AutoJS-*.jar' -ErrorAction SilentlyContinue)
        if ($present.Count -eq 0) {
            $warns += ('{0} 的 mods 里还没有 autojs jar（冷启动前执行 pwsh -File scripts/test/mt_env.ps1 mods --version {0}）' -f $spec.Version)
        } elseif ($present.Count -gt 1) {
            $issues += ('{0} 的 mods 里有 {1} 个 autojs jar（同族多份 ⇒ FML Duplicate mods）：{2}' -f $spec.Version, $present.Count, (($present | ForEach-Object { $_.Name }) -join ', '))
        }
    }

    $state = 'OK'
    if ($issues.Count -gt 0) { $state = 'FAIL' } elseif ($warns.Count -gt 0) { $state = 'WARN' }

    $detail = ('端点 {0}；vendor {1}/{2} 校验通过' -f $(if ($endpoint) { $endpoint } else { '(未配置)' }), $vendorOk, $wanted.Count)
    if ($issues.Count -gt 0) { $detail += '；' + ($issues -join '；') }
    if ($warns.Count -gt 0) { $detail += '；' + ($warns -join '；') }

    Write-MtLine ('MT_PREFLIGHT_AUTOJS: {0} — {1}' -f $state, $detail)
    return @{ State = $state; Detail = $detail }
}

# ── 兼容栈 / 可写性 ───────────────────────────────────────────────────────
function Test-MtPreflightCompatStack {
    <#
    .SYNOPSIS
        兼容模组来源可用性。返回 @(bool, detail)。

    .NOTES
        1.21.1：Sodium/Iris/ModernFix 从整合包复制到 dev run，因此必须存在源目录。
        1.20.1：dev run 不使用渲染模组（refmap 在 mojmap 下无法解析），只校验生产环境就绪。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Version)

    $p = Get-MtPaths -Version $Version
    if (-not (Test-Path -LiteralPath $p.pack_mods_dir -PathType Container)) {
        if ($Version -eq '1.20.1') {
            return , @($true, "生产环境目录不存在（仅影响兼容性人工验证）：$($p.pack_mods_dir)")
        }
        return , @($false, "整合包 mods 目录不存在：$($p.pack_mods_dir)")
    }

    if ($Version -eq '1.21.1') {
        $found = [ordered]@{ 'sodium' = $false; 'iris' = $false; 'modernfix' = $false }
        foreach ($f in @(Get-ChildItem -LiteralPath $p.pack_mods_dir -File -Filter '*.jar')) {
            $low = $f.Name.ToLowerInvariant()
            foreach ($pat in @($found.Keys)) {
                if ($low.Contains($pat)) { $found[$pat] = $true }
            }
        }
        $missing = @()
        foreach ($k in @($found.Keys)) { if (-not $found[$k]) { $missing += $k } }
        if ($missing.Count -gt 0) {
            return , @($false, "整合包缺少兼容模组：$($missing -join ', ')")
        }
    }
    return , @($true, [string]$p.pack_mods_dir)
}

function Test-MtPreflightWritable {
    <#
    .SYNOPSIS
        run 目录可写（世界重建与日志写入的前提）。返回 @(bool, detail)。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Version)

    $p = Get-MtPaths -Version $Version
    try {
        if (-not (Test-Path -LiteralPath $p.run_dir)) {
            [void](New-Item -ItemType Directory -Force -Path $p.run_dir)
        }
        $probe = Join-Path $p.run_dir '.mt_write_probe'
        [System.IO.File]::WriteAllText($probe, 'ok', [System.Text.UTF8Encoding]::new($false))
        Remove-Item -LiteralPath $probe -Force
    } catch {
        return , @($false, "run 目录不可写：$($_.Exception.Message)")
    }
    return , @($true, [string]$p.run_dir)
}

# ── 前置库配对（run/<版本>/mods 的库 jar 与本线引脚一致）──────────────────
function Get-MtGradleProperty {
    <#
    .SYNOPSIS
        读取某子项目 gradle.properties 的单个键（键不存在返回 $null）。仅供本文件的前置检查使用。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$File,
        [Parameter(Mandatory)][string]$Key
    )
    if (-not (Test-Path -LiteralPath $File -PathType Leaf)) { return $null }
    $m = Select-String -LiteralPath $File -Pattern ('^\s*' + [regex]::Escape($Key) + '\s*=(.*)$') | Select-Object -First 1
    if (-not $m) { return $null }
    return $m.Matches[0].Groups[1].Value.Trim()
}

function Test-MtPreflightLibPairing {
    <#
    .SYNOPSIS
        run/<版本>/mods 内的 starengine 库 jar 必须与本线 gradle.properties 的
        starengine_version **一致**（缺失可接受）。返回 @(bool, detail)。

    .NOTES
        2026-09-19 实测：库版本由 `.10` 提到 `.11` 后，`run/<版本>/mods` 仍留着旧的 `.10` 库 jar，
        于是 `run/Start-<版本>.bat` 的前置检查一律拒绝启动（「库 jar 名与本线 starengine_version
        不一致」）—— 而工具链自身没有这道闸门，自动化测试照常运行，环境因此处于
        「人手启动被拒、自动化照跑」的不一致状态。本检查与 `Start-*.bat` **同口径**，
        把不一致暴露在最前面并给出可照做的修复命令（唯一修复入口 = Start-SelfTest 的成对部署）。

        缺 jar **不**判失败：dev 运行的库由 Gradle `implementation` 放进 runtimeClasspath
        （见 `scripts/devtools/Start-SelfTest.ps1` 的说明），`run/mods` 里那份只是让人手启动路径自足。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Version)

    $p = Get-MtPaths -Version $Version
    $props = Join-Path (Join-Path $p.root $p.subproject) 'gradle.properties'
    $pin = Get-MtGradleProperty -File $props -Key 'starengine_version'
    if (-not $pin) {
        return , @($false, "$props 里读不到 starengine_version")
    }
    if (-not (Test-Path -LiteralPath $p.mods_dir -PathType Container)) {
        return , @($true, "无 mods 目录（库由 Gradle runtimeClasspath 提供 $pin）")
    }
    $libs = @(Get-ChildItem -LiteralPath $p.mods_dir -Filter 'starengine-*.jar' -File -ErrorAction SilentlyContinue)
    if ($libs.Count -eq 0) {
        return , @($true, "mods 内无库 jar（由 Gradle runtimeClasspath 提供 $pin）")
    }
    if ($libs.Count -gt 1) {
        return , @($false, "mods 内 starengine-*.jar 有 $($libs.Count) 份（应恰 1 份）：$(($libs | ForEach-Object { $_.Name }) -join ' / ')")
    }
    $name = [string]$libs[0].Name
    if ($name -notlike "*-$pin.jar") {
        return , @($false, "库 jar 与本线引脚不一致：实际 $name ；期望 *-$pin.jar ⟹ 照做：pwsh -NoProfile -File scripts/devtools/Start-SelfTest.ps1 -Version $Version")
    }
    return , @($true, "$name（与 starengine_version=$pin 一致）")
}

# ── 模组来源（统一口径闸门）──────────────────────────────────────────────
function Test-MtPreflightModSources {
    <#
    .SYNOPSIS
        模组依赖来源统一口径（AGENTS.md「模组依赖添加规则(统一口径,1.20.1 + 1.21.1)」）。返回 @(bool, detail)。

    .NOTES
        唯一实现在 `tools/check_mod_sources.ps1`（阶段 P 与 TESTING-SPEC §9 静态守门共用同一脚本，
        不得在此另写一份判定逻辑）。判定对象是 `forge-1.20.1` / `neoforge-1.21.1` 的 build.gradle：
        两个来源仓库是否都声明、模组坐标是否只用 curse.maven / maven.modrinth、本地 jar 兜底是否实际命中。
        官方 maven 的模组依赖（mezz.jei / dev.architectury）以「例外」回显，不算失败。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Root)

    $gate = Join-Path (Join-Path $Root 'tools') 'check_mod_sources.ps1'
    if (-not (Test-Path -LiteralPath $gate -PathType Leaf)) {
        return , @($false, "缺少 $gate")
    }
    # ⚠️ 不能按名字写 'pwsh'（假设它在 PATH 里）：也可能是全路径启动的绿色版/Store 版。
    #    取**本进程自身**的可执行文件路径最稳
    #    （与 mt_launch / mt_stop / mt_case 的 `$psExe = (Get-Process -Id $PID).Path` 同一口径）。
    $selfPwsh = (Get-Process -Id $PID).Path
    $r = Invoke-MtProcessFull -FilePath $selfPwsh `
        -ArgumentList @('-NoProfile', '-File', $gate, '-Root', $Root) -TimeoutSec 120
    $verdict = ($r.StdOut -split "`r?`n") | Where-Object { $_ -match 'MOD_SOURCE_GATE:' } | Select-Object -Last 1
    if (-not $verdict) {
        return , @($false, "无结论行（exit=$($r.ExitCode)；$($r.StdErr.Trim())）")
    }
    return , @(($r.ExitCode -eq 0), $verdict.Trim())
}

# ── 汇总 ══════════════════════════════════════════════════════════════════
function Invoke-MtPreflightAll {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string[]]$Versions)

    $root = (Get-MtPaths -Version $Versions[0]).root
    $gradlewExists = Test-Path -LiteralPath (Join-Path $root 'gradlew') -PathType Leaf

    $checks = [System.Collections.Generic.List[object]]::new()
    $checks.Add([pscustomobject]@{ Name = '分支'; Pair = (Get-MtPreflightBranch -Root $root) })
    $checks.Add([pscustomobject]@{ Name = '输入法'; Pair = (Test-MtPreflightIme) })
    # AutoJS 通道（2026-10-17 批次）：State 可为 WARN —— 见 Test-MtPreflightAutoJs 的 .NOTES
    $autoJsState = Test-MtPreflightAutoJs -Root $root
    $checks.Add([pscustomobject]@{ Name = 'AutoJS 通道'; State = [string]$autoJsState.State; Detail = [string]$autoJsState.Detail })
    $checks.Add([pscustomobject]@{ Name = '模组来源'; Pair = (Test-MtPreflightModSources -Root $root) })
    # 注意两点（PowerShell 的两个坑，都踩过）：
    #   1. 哈希表字面量里不能直接写 `Pair = , @(…)` —— 解析器报「参数列表中缺少参数」；
    #   2. **直接赋值**时的 `, @(a,b)` 会造出「外层 1 元素数组包着内层 2 元素数组」，
    #      于是 `Pair[1]` 取到 $null（表现为「Gradle 包装器: 」后面空的）。
    #      函数 `return , @(a,b)` 没有这个问题（输出流会把外层那 1 个元素摊开），
    #      但直接赋值必须**不带**那个逗号。
    $gradlewDetail = if ($gradlewExists) { 'gradlew 存在' } else { '缺少 gradlew' }
    $gradlewPair = @($gradlewExists, $gradlewDetail)
    $checks.Add([pscustomobject]@{ Name = 'Gradle 包装器'; Pair = $gradlewPair })
    foreach ($v in $Versions) {
        # D4-2：遗留进程检查**按线**执行（只扫/只收停本版本），两条线因此可并行。
        $checks.Add([pscustomobject]@{ Name = "遗留进程 $v"; Pair = (Test-MtPreflightLeftover -Version $v) })
        $checks.Add([pscustomobject]@{ Name = "兼容栈 $v"; Pair = (Test-MtPreflightCompatStack -Version $v) })
        $checks.Add([pscustomobject]@{ Name = "run 可写 $v"; Pair = (Test-MtPreflightWritable -Version $v) })
        $checks.Add([pscustomobject]@{ Name = "前置库配对 $v"; Pair = (Test-MtPreflightLibPairing -Version $v) })
    }

    $failed = @()
    $warned = @()
    foreach ($c in $checks) {
        # 两类条目并存：老的 @{Name;Pair} 与新的 @{Name;State;Detail}（WARN 只能由后者表达）
        $hasState = ($null -ne $c.PSObject.Properties['State'])
        if ($hasState) {
            $mark = [string]$c.State
            if ($mark -notin @('OK', 'WARN', 'FAIL')) { $mark = 'FAIL' }
            $detail = [string]$c.Detail
            $ok = ($mark -eq 'OK')
            $warn = ($mark -eq 'WARN')
        } else {
            $ok = [bool]$c.Pair[0]
            $detail = [string]$c.Pair[1]
            $mark = if ($ok) { 'OK  ' } else { 'FAIL' }
            $warn = $false
        }
        $markDisp = if ($ok) { 'OK  ' } else { $mark }
        Write-MtLine "  [$markDisp] $($c.Name): $detail"
        if ($warn) { $warned += "$($c.Name)（$detail）" }
        elseif (-not $ok) { $failed += "$($c.Name)（$detail）" }
    }

    if ($failed.Count -gt 0) {
        Write-MtErrLine ''
        Write-MtErrLine "MT_PREFLIGHT: FAIL — $($failed.Count) 项不满足：$($failed -join '; ')"
        Write-MtErrLine '修复后重跑本阶段；前置失败不触达游戏，不作为功能缺陷。'
        return $script:ExitPreflight
    }
    Write-MtLine ''
    if ($warned.Count -gt 0) {
        Write-MtLine ("MT_PREFLIGHT: WARN — {0} 项为提示（不阻塞）：{1}" -f $warned.Count, ($warned -join '; '))
    }
    Write-MtLine "MT_PREFLIGHT: OK — $($checks.Count) 项全部满足（$($Versions -join ', ')）"
    return 0
}

# ── 入口 ══════════════════════════════════════════════════════════════════
if ($MyInvocation.InvocationName -ne '.') {

    $Version = ''
    $All = $false

    $i = 0
    while ($i -lt $args.Count) {
        $tok = [string]$args[$i]
        $key = $tok.TrimStart('-').ToLowerInvariant()
        if ($key -eq 'version') {
            if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --version 的值'; exit $MT_EXIT_ERROR }
            $Version = [string]$args[$i + 1]; $i += 2
        } elseif ($key -eq 'all') {
            $All = $true; $i++
        } elseif ($key -eq 'json') {
            # JSON 模式在 python 侧同样只是走 run_all（结构化输出未实现），保持一致
            $i++
        } else {
            Write-MtErrorLine "未知参数 $tok"; exit $MT_EXIT_ERROR
        }
    }

    $versions = if ($All -or -not $Version) { @(Get-MtVersions) } else { @($Version) }
    foreach ($v in $versions) {
        if (-not (Assert-MtVersion -Version $v)) { exit $MT_EXIT_ERROR }
    }

    exit (Invoke-MtPreflightAll -Versions $versions)
}
