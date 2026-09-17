<#
Mt.Paths.psm1 — 版本→路径映射（PowerShell 侧唯一路径来源）。

迁移前源文件：scripts/test/lib/mt_paths.py（python 侧）+ scripts/test/lib/paths.sh（bash 侧）——两者均已在 92fbeaf 删除（取回：`git show 92fbeaf^:<路径>`）。
版本表以本模块为唯一来源：新增 MC 版本时只需改这一处。

## 命名约定（有意为之）

Python 侧的 `Paths` 对象属性是 snake_case（run_dir / latest_log / …），本模块**沿用同名
snake_case 属性**，目的是让 python → powershell 的逐行对照不需要任何名字映射，
降低 4813 行移植的抄写错误率。函数名与参数则遵循 PowerShell 惯例（Get-MtPaths / -Version）。

## 对象形态

Get-MtPaths 返回一个**纯数据 PSCustomObject**（不含 ScriptMethod）。原因是 PowerShell 的
ScriptMethod/ScriptProperty 会闭包捕获创建时的作用域，而函数局部变量在函数返回后即失效，
把「行为」挂在对象上会引入难查的作用域 bug。行为一律以模块函数提供：
Get-MtProcessMarkers / Get-MtShots / Add-MtShot / Get-MtCurrentShots。
#>

# ⚠️ 这里**不得**用 -Force：
#    -Force 会「先卸载再重载」被引入的模块；若调用方脚本此前已 Import-Module 过 Mt.Conf，
#    这次卸载会把已经注入调用方作用域的函数一起摘掉（实测：随后初始化 Mt.Phase 时报
#    「术语 … 不会被识别为 cmdlet」，而 Import-Module 本身却无任何报错）。
#    入口脚本各自跑在全新 pwsh 里，普通 Import-Module 已足够（幂等）。
Import-Module (Join-Path $PSScriptRoot 'Mt.Conf.psm1')

# ── 版本表（与 paths.sh / mt_paths.py 必须一致）──────────────────────────
# 26.1.2 = 第三条线(multi-26.1.2-neoforge 分支新增;MC 26.1.2 / NeoForge 26.1.2.x / Java 25)。
# 顺序有意保持「1.21.1 在前」:mt.ps1 的门控流程以 1.21.1 → 1.20.1 的既有顺序为准,
# 新版本追加在末尾,不改变原有咬合关系。
$script:VERSIONS = @('1.21.1', '1.20.1', '26.1.2')
$script:LOADER = @{ '1.21.1' = 'neoforge'; '1.20.1' = 'forge'; '26.1.2' = 'neoforge' }
$script:SUBPROJECT = @{ '1.21.1' = 'neoforge-1.21.1'; '1.20.1' = 'forge-1.20.1'; '26.1.2' = 'neoforge-26.1.2' }
$script:WORLD_NAME = 'testworld'
$script:SHOTS_MANIFEST = '.mt_shots.json'

# ⚠️ 用 .NET 的 GetDirectoryName 而不是 `Split-Path -LiteralPath X -Parent`：
#    后者在 PowerShell 7 里是**参数集冲突**（-Parent 与 -LiteralPath 不属于同一集），
#    且 -Path 会把路径里的 [ ] 当通配符。
$script:LIB_DIR = $PSScriptRoot
$script:TEST_DIR = [System.IO.Path]::GetDirectoryName($script:LIB_DIR)
$script:ROOT = [System.IO.Path]::GetDirectoryName([System.IO.Path]::GetDirectoryName($script:TEST_DIR))

$script:CONF_FILE = Join-Path $script:TEST_DIR 'mt.conf'
$script:RUNS_FILE = Join-Path (Join-Path $script:TEST_DIR 'cases') '.mt_active_run'

# 机器本地配置缺失时的内置默认值（与 mt_paths.py 的 _DEFAULT_PACK_MODS 一致）
$script:DEFAULT_PACK_MODS = @{
    '1.21.1' = 'D:\.minecraft\versions\狐の航空学 Voxy Edition\mods'
    '1.20.1' = 'D:\.minecraft\versions\1.20.1 模组测试\mods'
    '26.1.2' = 'D:\.minecraft\versions\26.1.2-NeoForge_26.1.2.109\mods'
}

$script:CONF = Get-MtConf -Path $script:CONF_FILE

function Get-MtVersions { $script:VERSIONS }
function Get-MtTestDir { $script:TEST_DIR }
function Get-MtRoot { $script:ROOT }
function Get-MtConfFile { $script:CONF_FILE }
function Get-MtRunsFile { $script:RUNS_FILE }
function Get-MtConf { $script:CONF }
function Get-MtWorldName { $script:WORLD_NAME }

function Assert-MtVersion {
    <#
    .SYNOPSIS
        版本合法性校验。合法返回 $true；否则向 stderr 打印与 paths.sh 的 mt_resolve
        完全相同的 `MT_ERROR: 未知版本 …（可选：…）` 并返回 $false。
    .NOTES
        刻意不用 [ValidateSet]：那会产生 PowerShell 自己的参数绑定报错文本与退出码，
        与 bash 版的 `MT_ERROR: … ; return 2` 不等价。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][AllowEmptyString()][string]$Version)

    if ($script:VERSIONS -contains $Version) { return $true }
    [Console]::Error.Write(("MT_ERROR: 未知版本 {0}（可选：{1}）`n" -f $Version, ($script:VERSIONS -join ' ')))
    return $false
}

function Get-MtPaths {
    <#
    .SYNOPSIS
        返回某个版本的完整路径集（字段名与 python Paths 对象一致）。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Version)

    if ($script:VERSIONS -notcontains $Version) {
        throw "未知版本 $Version（可选：$($script:VERSIONS -join ' / ')）"
    }

    $root = $script:ROOT
    $runDir = Join-Path (Join-Path $root 'run') $Version
    $logsDir = Join-Path $runDir 'logs'
    $shopsDir = Join-Path $runDir 'screenshots'

    # 26.1.2 用独立配置键 MT_PACK_MODS_NEOFORGE_26_1_2(不改动既有两键的语义)
    $confKey = switch ($Version) {
        '1.21.1' { 'MT_PACK_MODS_NEOFORGE' }
        '26.1.2' { 'MT_PACK_MODS_NEOFORGE_26_1_2' }
        default  { 'MT_PACK_MODS_FORGE' }
    }
    $rawPack = if ($script:CONF.ContainsKey($confKey)) { $script:CONF[$confKey] } else { $script:DEFAULT_PACK_MODS[$Version] }
    # pathlib 在 Windows 上会把 '/' 归一成 '\'；这里复刻该归一化
    $packModsDir = $rawPack -replace '/', '\'

    [pscustomobject]@{
        version         = $Version
        loader          = $script:LOADER[$Version]
        subproject      = $script:SUBPROJECT[$Version]
        root            = $root
        run_dir         = $runDir
        mods_dir        = Join-Path $runDir 'mods'
        saves_dir       = Join-Path $runDir 'saves'
        logs_dir        = $logsDir
        latest_log      = Join-Path $logsDir 'latest.log'
        debug_log       = Join-Path $logsDir 'debug.log'
        kubejs_log      = Join-Path (Join-Path $logsDir 'kubejs') 'server.log'
        # 服务端权威通道：由 run/<版本>/kubejs/server_scripts/astral_bugfix_probe.js 追加写
        # （工作目录 = run_dir）。独立于客户端渲染/聊天与 SLF4J 配置，因此
        # 「客户端卡死 / logger 被过滤」都不影响断言取证（source=probe）。
        probe_log       = Join-Path $runDir 'astral_probe.log'
        # 纯离线用例的读数通道：由 scripts/test/mt_loadergate.ps1 写（不经过游戏客户端）。
        # 现存唯一使用者是 LOADER-GATE-FORGE（1.20.1 FML 依赖排序门槛）。
        loadergate_log  = Join-Path $logsDir 'loadergate.log'
        crash_dir       = Join-Path $runDir 'crash-reports'
        shot_dir        = $shopsDir
        client_world    = Join-Path (Join-Path $runDir 'saves') $script:WORLD_NAME
        server_world    = Join-Path $runDir $script:WORLD_NAME
        task_build      = ":$($script:SUBPROJECT[$Version]):build"
        task_client     = ":$($script:SUBPROJECT[$Version]):runClient"
        task_server     = ":$($script:SUBPROJECT[$Version]):runServer"
        task_gametest   = ":$($script:SUBPROJECT[$Version]):runGameTestServer"
        pack_mods_dir   = $packModsDir
    }
}

function Get-MtProcessMarkers {
    <#
    .SYNOPSIS
        返回命令行中标识「该进程属于本版本的本流程」的标记（与 python 的
        Paths.process_markers 同义）。

    .NOTES
        刻意**不使用裸子项目名**（如 neoforge-1.21.1）：那同时是仓库内的目录名，任何只是
        引用了该目录的进程（IDE 语言服务器、索引任务）都会被误命中，进而被 mt_stop 误杀。
        改为要求 gradle 任务选择器（两侧带冒号）或 run 目录。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][psobject]$Paths)

    return @(":$($Paths.subproject):", "run\$($Paths.version)", [string]$Paths.run_dir)
}

# ── 截图世代（AGENTS「截图识别」：仅认当前/最新世代）────────────────────

function Get-MtShotsManifest {
    [CmdletBinding()]
    param([Parameter(Mandatory)][psobject]$Paths)

    return (Join-Path $Paths.shot_dir $script:SHOTS_MANIFEST)
}

function Get-MtShots {
    <#
    .SYNOPSIS
        读取当前世代截图清单；无清单或损坏时返回 @{}（与 python read_shots 同义）。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][psobject]$Paths)

    $manifest = Get-MtShotsManifest -Paths $Paths
    if (-not (Test-Path -LiteralPath $manifest -PathType Leaf)) { return @{} }
    try {
        return (Get-Content -LiteralPath $manifest -Raw -Encoding UTF8 | ConvertFrom-Json -AsHashtable)
    } catch {
        return @{}
    }
}

function Add-MtShot {
    <#
    .SYNOPSIS
        把一张截图登记进当前世代清单（与 python record_shot 同义）。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][psobject]$Paths,
        [Parameter(Mandatory)][string]$FilePath,
        [Parameter(Mandatory)][string]$Tag,
        [Parameter(Mandatory)][string]$RunId
    )

    $data = Get-MtShots -Paths $Paths
    if (-not $data.ContainsKey('run_id') -or $data['run_id'] -ne $RunId) {
        $data = @{ run_id = $RunId; shots = @() }
    }
    $shots = @($data['shots']) + ,([ordered]@{
            file = [System.IO.Path]::GetFileName($FilePath)
            tag  = $Tag
            ts   = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() / 1000.0
        })
    $data['shots'] = $shots

    $dir = $Paths.shot_dir
    if (-not (Test-Path -LiteralPath $dir)) { [void](New-Item -ItemType Directory -Force -Path $dir) }
    Set-Content -LiteralPath (Get-MtShotsManifest -Paths $Paths) -Encoding utf8NoBOM -NoNewline `
        -Value (ConvertTo-MtJson -InputObject $data)
}

function Get-MtCurrentShots {
    <#
    .SYNOPSIS
        只返回当前世代的截图；无清单时回落到「比运行开始更新」的文件
        （与 python current_shots 同义）。

    .NOTES
        这是「仅识别当前（最新）生成的截图」的唯一实现入口 —— 任何断言都不得直接
        glob 截图目录，否则会误认上一世代的残留证据。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][psobject]$Paths,
        [Parameter(Mandatory)][string]$RunId
    )

    $data = Get-MtShots -Paths $Paths
    if ($data.ContainsKey('run_id') -and $data['run_id'] -eq $RunId) {
        # ⚠️ 必须按文件名**去重**：python 侧是 `names = {s["file"] for s in shots}`（set）。
        #    清单里同一个文件被登记两次时（本机 run/1.20.1 的清单里
        #    bolt_<时间戳>.png 这类同世代截图就有 2 条），逐条 append 会多返回一份，
        #    表现为 `mt_capture list --version 1.20.1` 比 python 多一行（实测 DIFF，firstDiff=139）。
        $seen = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
        $out = @()
        foreach ($s in @($data['shots'])) {
            $name = [string]$s['file']
            if (-not $seen.Add($name)) { continue }
            $f = Join-Path $Paths.shot_dir $name
            if (Test-Path -LiteralPath $f -PathType Leaf) { $out += (Get-Item -LiteralPath $f) }
        }
        if ($out.Count -gt 0) { return @($out | Sort-Object LastWriteTime) }
    }

    # 回落：以运行开始时间为界，取最新连续一批
    $start = Get-MtRunStartTs -RunId $RunId
    if (-not (Test-Path -LiteralPath $Paths.shot_dir)) { return @() }
    $fresh = @(Get-ChildItem -LiteralPath $Paths.shot_dir -Filter '*.png' -File |
            Where-Object { $_.LastWriteTime.ToFileTime() / 10000000.0 - 11644473600 -ge $start })
    return @($fresh | Sort-Object LastWriteTime)
}

# ── 运行标识 ─────────────────────────────────────────────────────────────

function New-MtRunId {
    return (Get-Date -Format 'yyyyMMdd-HHmmss')
}

function Get-MtActiveRunId {
    <#
    .SYNOPSIS
        读取当前运行 id；不存在则新建并落盘（供各阶段共享）。
    .NOTES
        该文件是**粘性**的：只写不删，下一次运行沿用同一 id；孤儿 id（reports/<id>/ 不存在）
        说明上次运行中途夭折。
    #>
    [CmdletBinding()]
    param()

    if (Test-Path -LiteralPath $script:RUNS_FILE -PathType Leaf) {
        $txt = (Get-Content -LiteralPath $script:RUNS_FILE -Raw -Encoding UTF8).Trim()
        if ($txt) { return $txt }
    }
    $rid = New-MtRunId
    $dir = [System.IO.Path]::GetDirectoryName($script:RUNS_FILE)
    if (-not (Test-Path -LiteralPath $dir)) { [void](New-Item -ItemType Directory -Force -Path $dir) }
    Set-Content -LiteralPath $script:RUNS_FILE -Value $rid -Encoding utf8NoBOM -NoNewline
    return $rid
}

function Get-MtRunStartTs {
    <#
    .SYNOPSIS
        由 run id（yyyymmdd-HHMMSS）还原运行开始的**本地时间** unix 秒（与 python
        time.mktime(time.strptime(...)) 同义）；解析失败或**越界**返回 0.0。

    .NOTES
        「越界」这条是刻意对齐 python 侧的：Windows 上 time.mktime 对早于本地 epoch 的
        时间抛 OverflowError，而 python 原实现（2026-09-12 迁移期修好后）把它折叠为 0.0。
        本函数用「算出来是负数 → 0.0」复刻该边界，保证 19700101-000000 这类输入两侧一致。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][AllowEmptyString()][string]$RunId)

    try {
        $dt = [datetime]::ParseExact($RunId, 'yyyyMMdd-HHmmss', [cultureinfo]::InvariantCulture)
        $ts = [double]([DateTimeOffset]::new($dt).ToUnixTimeSeconds())
        if ($ts -lt 0) { return 0.0 }   # 对齐 mktime 的越界行为
        return $ts
    } catch {
        return 0.0
    }
}

function Get-MtReportsDir {
    <#
    .SYNOPSIS
        返回（并按需创建）某次运行的报告目录。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$RunId)

    $d = Join-Path (Join-Path $script:TEST_DIR 'reports') $RunId
    if (-not (Test-Path -LiteralPath $d)) { [void](New-Item -ItemType Directory -Force -Path $d) }
    return $d
}

function ConvertTo-MtJson {
    <#
    .SYNOPSIS
        序列化为与 python `json.dumps(obj, ensure_ascii=False, indent=2)` 形态一致的 JSON。

    .NOTES
        PowerShell 的 ConvertTo-Json 缩进宽度随版本变化（且会把中文转义），因此这里
        不用它做最终落盘。实现方式：先用 ConvertTo-Json 生成，再统一缩进为 2 空格、
        还原中文（-EscapeHandling 无法完全控制），最后补齐 LF。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)]$InputObject)

    $json = $InputObject | ConvertTo-Json -Depth 20
    if ($null -eq $json) { $json = 'null' }

    # ConvertTo-Json 的缩进是 2 或 4 空格；统一压成 2 空格
    $lines = $json -split "`r?`n"
    $out = foreach ($ln in $lines) {
        $m = [regex]::Match($ln, '^( +)(.*)$')
        if ($m.Success) {
            $depth = [int][Math]::Floor($m.Groups[1].Value.Length / 2)
            (' ' * (2 * $depth)) + $m.Groups[2].Value
        } else {
            $ln
        }
    }
    return (($out -join "`n"))
}

function Get-MtProgressFile {
    <#
    .SYNOPSIS
        进度信标文件路径（`cases/.mt_progress.json`）。

    .NOTES
        为什么放在 `cases/`：它与 `.mt_run_state.json` / `.mt_snapshot.json` 同级，
        都属于「跨脚本契约文件」。`cases/` 下的**点开头**文件不会被 run-dir 当作用例
        （见 mt_case.ps1 的 Get-MtCaseFiles 点文件过滤），故不会污染用例发现。
    #>
    [CmdletBinding()]
    param()

    return (Join-Path (Join-Path $script:TEST_DIR 'cases') '.mt_progress.json')
}

function Set-MtProgress {
    <#
    .SYNOPSIS
        写「此刻正在做什么」的信标（原子写：临时文件 + 覆盖改名，读方永不看到半截 JSON）。

    .NOTES
        存在的理由（2026-09-16 用户要求「严格控制等待时间并完善监视器」）：
        实测过一次「客户端一切正常、但 cases 阶段 7 分 45 秒零输出」的事故 —— 事后**无法**
        从日志判定它卡在哪一步（该阶段输出在被 `Select-Object` 吞掉的管道里）。本信标把
        「阶段 / 用例 / 第几步 / 什么 op / 开始时刻」落到磁盘 ⇒ 监视器与事后取证都能直接读到。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Phase,
        [string]$Version = '',
        [string]$Case = '',
        [int]$StepIndex = 0,
        [int]$StepTotal = 0,
        [string]$Op = '',
        [string]$Detail = ''
    )

    $f = Get-MtProgressFile
    $dir = [System.IO.Path]::GetDirectoryName($f)
    if (-not (Test-Path -LiteralPath $dir)) { [void](New-Item -ItemType Directory -Force -Path $dir) }

    $obj = [ordered]@{
        ts         = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
        pid        = $PID
        phase      = $Phase
        version    = $Version
        case       = $Case
        step_index = $StepIndex
        step_total = $StepTotal
        op         = $Op
        detail     = $Detail
    }

    $tmp = "$f.tmp"
    try {
        $enc = [System.Text.UTF8Encoding]::new($false)
        [System.IO.File]::WriteAllText($tmp, (ConvertTo-MtJson -InputObject $obj), $enc)
        Move-Item -LiteralPath $tmp -Destination $f -Force
    } catch {
        # 信标是诊断设施，写不进去绝不能影响测试本身
    }
}

function Get-MtProgress {
    <#
    .SYNOPSIS
        读进度信标；返回 hashtable，缺失/损坏时返回 $null（调用方自己决定怎么报）。
    #>
    [CmdletBinding()]
    param()

    $f = Get-MtProgressFile
    if (-not (Test-Path -LiteralPath $f -PathType Leaf)) { return $null }
    try {
        $raw = [System.IO.File]::ReadAllText($f, [System.Text.UTF8Encoding]::new($false))
        if (-not $raw.Trim()) { return $null }
        return ($raw | ConvertFrom-Json -AsHashtable -ErrorAction Stop)
    } catch {
        return $null
    }
}

Export-ModuleMember -Function @(
    'Get-MtVersions', 'Get-MtTestDir', 'Get-MtRoot', 'Get-MtConfFile', 'Get-MtRunsFile',
    'Get-MtConf', 'Get-MtWorldName', 'Assert-MtVersion', 'Get-MtPaths',
    'Get-MtProcessMarkers', 'Get-MtShotsManifest', 'Get-MtShots', 'Add-MtShot',
    'Get-MtCurrentShots', 'New-MtRunId', 'Get-MtActiveRunId', 'Get-MtRunStartTs',
    'Get-MtReportsDir', 'ConvertTo-MtJson',
    'Get-MtProgressFile', 'Set-MtProgress', 'Get-MtProgress'
)
