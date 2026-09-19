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
# 分支口径（2026-09-20 S3 与 -next 工作树统一；依据 -next SPEC 所载 2026-09-17 用户裁决「移除二重验证白名单」）：
# 发布线 = multi-1.20.1-1.21.1；非发布线分支 WARN 放行（回显分支名）。原白名单含已并入主线的 multi-26.1.2-neoforge。
$script:ReleaseLineBranches = @('multi-1.20.1-1.21.1')
$script:KeepAlive = Join-Path (Join-Path (Get-MtTestDir) 'cases') '.mt_keep_alive'
$script:KLID_EN_US = '00000409'

# ── 分支 ──────────────────────────────────────────────────────────────────
function Test-MtPreflightBranch {
    <#
    .SYNOPSIS
        分支口径：发布线 multi-1.20.1-1.21.1 ⇒ OK；其它分支 ⇒ WARN 放行（回显分支名）。返回 @(bool, detail)。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Root)

    $r = Invoke-MtProcessFull -FilePath 'git' `
        -ArgumentList @('-C', $Root, 'rev-parse', '--abbrev-ref', 'HEAD') -TimeoutSec 60
    if ($r.ExitCode -ne 0) {
        return , @($false, "无法读取分支：$($r.StdErr.Trim())")
    }
    $branch = $r.StdOut.Trim()
    if ($script:ReleaseLineBranches -contains $branch) {
        return , @($true, "$branch（发布线分支）")
    }
    return , @($true, ("$branch —— WARN: 非发布线分支（发布线 {0}）" -f ($script:ReleaseLineBranches -join ' / ')))
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
function Get-MtOwnProcessMarkers {
    <#
    .SYNOPSIS
        本流程进程的命令行特征（判据与 mt_env kill / mt_cleanup 共用同一来源）。
    #>
    [CmdletBinding()]
    param()

    $marks = @()
    foreach ($v in @(Get-MtVersions)) {
        $marks += @(Get-MtProcessMarkers -Paths (Get-MtPaths -Version $v))
    }
    return , $marks
}

function Get-MtPreflightLeftoverScan {
    <#
    .SYNOPSIS
        扫描 java 进程：本流程的（命中标记）与其它 Minecraft 客户端。返回 @(ours, others)。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string[]]$Marks)

    $ours = @(); $others = @()
    foreach ($p in Get-JavaProcesses) {
        $cmd = [string]$p.CommandLine
        $hit = $false
        foreach ($m in $Marks) { if ($m -and $cmd.Contains([string]$m)) { $hit = $true; break } }
        if ($hit) { $ours += [int]$p.Pid }
        elseif ($cmd.ToLowerInvariant().Contains('minecraft')) { $others += [int]$p.Pid }
    }
    return , @($ours, $others)
}

function Test-MtPreflightLeftover {
    <#
    .SYNOPSIS
        遗留进程检查：本流程自己的进程**先自动清理**，清不掉才硬阻塞。返回 @(bool, detail)。

    .NOTES
        刻意区分两类进程 —— 早前的实现把两者混为一谈，会在用户只是开着自己的整合包客户端
        时误报阻塞：
          本流程遗留（dev 客户端 / Gradle 任务）→ **自动收停**，否则独占 runClient 的前提
            不成立，且注入可能落到旧进程上；
          其它 Minecraft 客户端（用户自己开的整合包）→ 仅提示，不阻塞：注入通道已按版本
            锁定窗口（见 mt_inject 的窗口查找），不会投错；只提示显存/焦点占用。

        为什么在这里兜底：退出路径在 Windows 上并不可靠（任务管理器强杀、控制台直接关窗
        都不经过任何钩子），只靠退出钩子就等于把「进程泄漏」交给运气。因此再加一道**下次
        启动时生效**的兜底：检出的本流程遗留直接收停，让泄漏最多影响一次运行的干净度。

        唯一例外：`.mt_keep_alive` 取证标记存在时**不自动清理**（那是失败时故意留的现场），
        改为 FAIL 并提示用 `--phase stop --force` 显式释放。
    #>
    [CmdletBinding()]
    param()

    $marks = Get-MtOwnProcessMarkers
    $scan = Get-MtPreflightLeftoverScan -Marks $marks
    $ours = @($scan[0]); $others = @($scan[1])

    $gradlePids = @()
    try {
        $gradlePids = @(Get-Process -Name gradle -ErrorAction SilentlyContinue | ForEach-Object { [int]$_.Id })
    } catch { $gradlePids = @() }

    if ($ours.Count -gt 0) {
        if (Test-Path -LiteralPath $script:KeepAlive -PathType Leaf) {
            $why = '（未记录原因）'
            try {
                $t = (Get-Content -LiteralPath $script:KeepAlive -Raw -Encoding UTF8).Trim()
                if ($t) { $why = $t }
            } catch { $why = '（读取标记失败）' }
            return , @($false, ("本流程遗留 PID=$($ours -join ',')，但存在失败取证标记（$why）——按设计保留现场；" +
                        '取证完成后执行 pwsh -File scripts/test/mt.ps1 --phase stop --force'))
        }

        $stale = @($ours)
        Write-MtLine "  [ .. ] 遗留进程: 检出 $($stale.Count) 个本流程遗留进程（PID=$($stale -join ',')），自动收停中…"
        # 与 python 的 `import mt_cleanup; mt_cleanup.run_cleanup(quiet=True)` 等价：点源同一份实现
        . (Join-Path $PSScriptRoot 'mt_cleanup.ps1')
        [void](Invoke-MtRunCleanup -Quiet $true)

        $scan2 = Get-MtPreflightLeftoverScan -Marks $marks
        $ours = @($scan2[0]); $others = @($scan2[1])
        if ($ours.Count -gt 0) {
            return , @($false, ("自动收停后仍有本流程遗留 PID=$($ours -join ',')，" +
                        '请手工执行 pwsh -File scripts/test/mt.ps1 --phase stop 并检查权限'))
        }
        return , @($true, ("已自动收停 $($stale.Count) 个本流程遗留进程（PID=$($stale -join ',')）——" +
                    '退出钩子失效时的兜底，不影响本次结论'))
    }

    if ($gradlePids.Count -gt 0) {
        return , @($false, ("存在 gradle 包装器进程 PID=$($gradlePids -join ',')，" +
                    '请先 pwsh -File scripts/test/mt.ps1 --phase stop'))
    }

    if ($others.Count -gt 0) {
        return , @($true, ("无本流程遗留；另有 $($others.Count) 个其它 Minecraft 客户端在运行" +
                    "（PID=$($others -join ',')）——不阻塞，仅占用显存"))
    }
    return , @($true, '无遗留进程')
}

# ── MCP 二进制 ────────────────────────────────────────────────────────────
function Test-MtPreflightMcpBinary {
    <#
    .SYNOPSIS
        注入通道所用的本地 MCP 二进制（computer-control-mcp）存在。返回 @(bool, detail)。
    #>
    [CmdletBinding()]
    param()

    $conf = Get-MtConf
    $exe = ''
    if ($null -ne $conf -and $conf.ContainsKey('MT_MCP_BIN')) { $exe = [string]$conf['MT_MCP_BIN'] }
    if (-not $exe) { return , @($true, '未配置 MT_MCP_BIN（跳过）') }

    # pathlib 在 Windows 上会把 '/' 归一成 '\'；报错文案要复刻这一点
    $shown = $exe -replace '/', '\'
    if (-not (Test-Path -LiteralPath $exe -PathType Leaf)) {
        return , @($false, "MCP 二进制缺失：$shown")
    }
    return , @($true, [System.IO.Path]::GetFileName($exe))
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
    $r = Invoke-MtProcessFull -FilePath 'pwsh' `
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
    $checks.Add([pscustomobject]@{ Name = '分支'; Pair = (Test-MtPreflightBranch -Root $root) })
    $checks.Add([pscustomobject]@{ Name = '输入法'; Pair = (Test-MtPreflightIme) })
    $checks.Add([pscustomobject]@{ Name = '遗留进程'; Pair = (Test-MtPreflightLeftover) })
    $checks.Add([pscustomobject]@{ Name = 'MCP 二进制'; Pair = (Test-MtPreflightMcpBinary) })
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
        $checks.Add([pscustomobject]@{ Name = "兼容栈 $v"; Pair = (Test-MtPreflightCompatStack -Version $v) })
        $checks.Add([pscustomobject]@{ Name = "run 可写 $v"; Pair = (Test-MtPreflightWritable -Version $v) })
    }

    $failed = @()
    foreach ($c in $checks) {
        $ok = [bool]$c.Pair[0]
        $detail = [string]$c.Pair[1]
        $mark = if ($ok) { 'OK  ' } else { 'FAIL' }
        Write-MtLine "  [$mark] $($c.Name): $detail"
        if (-not $ok) { $failed += "$($c.Name)（$detail）" }
    }

    if ($failed.Count -gt 0) {
        Write-MtErrLine ''
        Write-MtErrLine "MT_PREFLIGHT: FAIL — $($failed.Count) 项不满足：$($failed -join '; ')"
        Write-MtErrLine '修复后重跑本阶段；前置失败不触达游戏，不作为功能缺陷。'
        return $script:ExitPreflight
    }
    Write-MtLine ''
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
