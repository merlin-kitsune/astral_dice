#Requires -Version 7.0
<#
.SYNOPSIS
    mt_assert — 断言引擎（阶段 C 的判定臂）。

.DESCRIPTION
    判定原则：所有日志断言只针对**快照点之后的增量区间**求值。
    旧流程是对整个 latest.log 做 grep，历史运行留下的同名标记会让断言假通过；
    本模块用字节游标把断言锚定到本次运行产生的日志，使结论可复算。

    子命令:
      snapshot --version V                记录基线（日志字节游标 / crash 基线 / run id）
      log      --version V --pattern RE   增量区间内是否存在标记
      absent   --version V --pattern RE   增量区间内不得出现某标记
      crash    --version V                增量区间内无崩溃报告
      kubejs   --version V                KubeJS server.log 为 0 errors
      mixin    --version V                增量区间内无 Mixin 应用失败

.NOTES
    对应源文件（迁移前）：scripts/test/mt_assert.py。

    `.mt_snapshot.json` 是**跨语言契约文件**：迁移期 python 版与 pwsh 版会互相读对方写的
    快照，因此 JSON 的**语义**（键名、结构、offsets 键为日志文件名）必须一致；字节形态
    （缩进/浮点写法）不要求相同 —— `ts` 本就每次运行都不同。

    偏差说明：
      - `--source probe` 在 python 侧是 LOG_SOURCES 里没有的键（KeyError → traceback，rc 1），
        这里显式报错并退出 1（不打印 .NET 栈）；
      - 日志按 UTF-8 解码：python 用 errors="ignore"（丢弃非法字节），这里用替换字符
        U+FFFD（`.NET UTF8Encoding($false,$false)`）—— 只影响二进制垃圾，不影响标记匹配；
      - 非法正则的报错文案是 .NET 原文（python 是 re.error 文案）。
#>

$ErrorActionPreference = 'Stop'

$script:LibDir = Join-Path $PSScriptRoot 'lib'
Import-Module (Join-Path $script:LibDir 'Mt.Phase.psm1')
Import-Module (Join-Path $script:LibDir 'Mt.Paths.psm1')

Initialize-MtConsole

$script:SnapshotFile = Join-Path (Join-Path (Get-MtTestDir) 'cases') '.mt_snapshot.json'

# latest —— 客户端游戏日志。**探针标记的权威通道**：命令输出经 ctx.source.sendFailure、
#            tick 回调经 ServerPlayer#sendSystemMessage，两条路都由 ChatComponent 落到
#            `[CHAT] AP_...`；mt_launch 每次运行前删除该文件，故天然是「本轮增量」。
# debug  —— 客户端调试日志（Mixin 应用 / 渲染栈加载证据）。
#
# ⚠️ 已移除的 `probe` 通道（run/<版本>/astral_probe.log）：该文件从未生成 —— KubeJS 的
#    Java 类过滤器拒绝 java.io，FileWriter 构造失败又被 try/catch 静默吞掉，表现为
#    「服务端权威通道不存在」，把测试链故障伪装成修复无效。不要再加回来。
$script:LogSources = [ordered]@{ 'latest' = 'latest_log'; 'debug' = 'debug_log' }

$script:TAG_UTF8 = [System.Text.UTF8Encoding]::new($false, $false)

# ── 快照 ──────────────────────────────────────────────────────────────────
function Get-MtSnapshot {
    <#
    .SYNOPSIS
        读快照文件；不存在或损坏时返回空 OrderedDictionary（与 python load_snapshot 同义）。
    #>
    [CmdletBinding()]
    param()

    if (-not (Test-Path -LiteralPath $script:SnapshotFile -PathType Leaf)) {
        return [ordered]@{}
    }
    try {
        $txt = [System.IO.File]::ReadAllText($script:SnapshotFile, $script:TAG_UTF8)
        $obj = $txt | ConvertFrom-Json -AsHashtable -ErrorAction Stop
        if ($null -eq $obj) { return [ordered]@{} }
        return $obj
    } catch {
        return [ordered]@{}
    }
}

function Save-MtSnapshot {
    [CmdletBinding()]
    param([Parameter(Mandatory)]$Snapshot)

    $dir = [System.IO.Path]::GetDirectoryName($script:SnapshotFile)
    if (-not (Test-Path -LiteralPath $dir)) { [void](New-Item -ItemType Directory -Force -Path $dir) }
    Set-Content -LiteralPath $script:SnapshotFile -Value (ConvertTo-MtJson -InputObject $Snapshot) `
        -Encoding utf8NoBOM -NoNewline
}

function Get-MtSnapFor {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Version)

    $snap = Get-MtSnapshot
    if (-not $snap.Contains('versions')) { return [ordered]@{} }
    $versions = $snap['versions']
    if ($null -eq $versions -or -not $versions.Contains($Version)) { return [ordered]@{} }
    return $versions[$Version]
}

# ── 增量读取 ──────────────────────────────────────────────────────────────
function ConvertTo-MtUniversalNewlines {
    <#
    .SYNOPSIS
        复刻 python 文本模式的**通用换行**翻译（newline=None）：`\r\n` 与孤立 `\r` 一律折成 `\n`。

    .NOTES
        这不是可选项：python 的 `log.open("r", encoding="utf-8", errors="ignore")` 与
        `read_text()` 都会做这层翻译。不做的话 PS 侧文本里每个行尾都多一个 `\r`
        —— 实测用 pattern `.` 数命中次数时出现 8611 vs 8557 的差异（多出的正是 CR），
        更危险的是 `$`/`\Z` 锚定的正则会因此**静默不匹配**。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][AllowEmptyString()][string]$Text)

    return $Text.Replace("`r`n", "`n").Replace("`r", "`n")
}

function Read-MtLogDelta {
    <#
    .SYNOPSIS
        读取 offset 之后的新增内容；文件被轮转（变小）则整读（与 python _read_delta 同义）。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$LogPath, [long]$Offset = 0)

    if (-not (Test-Path -LiteralPath $LogPath -PathType Leaf)) { return '' }
    $size = (Get-Item -LiteralPath $LogPath).Length
    if ($Offset -gt $size) { $Offset = 0 }   # launch 阶段清零过日志 → 从 0 读

    $fs = [System.IO.File]::Open($LogPath, [System.IO.FileMode]::Open,
        [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
    try {
        [void]$fs.Seek($Offset, [System.IO.SeekOrigin]::Begin)
        $ms = [System.IO.MemoryStream]::new()
        try {
            $fs.CopyTo($ms)
            return (ConvertTo-MtUniversalNewlines -Text $script:TAG_UTF8.GetString($ms.ToArray()))
        } finally { $ms.Dispose() }
    } finally { $fs.Dispose() }
}

function Get-MtLogPath {
    [CmdletBinding()]
    param([Parameter(Mandatory)][psobject]$Paths, [Parameter(Mandatory)][string]$Source)

    if (-not $script:LogSources.Contains($Source)) {
        Write-MtErrLine "MT_ASSERT: ERROR — 未知断言通道 $Source"
        exit 2
    }
    # 动态属性取法（等价 python 的 getattr(p, LOG_SOURCES[source])）
    return [string]$Paths.($script:LogSources[$Source])
}

function Test-MtLogMissing {
    <#
    .SYNOPSIS
        日志文件不存在属**环境问题**（游戏没起来/跑错目录），不是断言失败。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Source, [Parameter(Mandatory)][string]$LogPath)

    if (Test-Path -LiteralPath $LogPath -PathType Leaf) { return $false }
    Write-MtErrLine "MT_ASSERT_LOG: ERROR — 断言通道缺失($Source`:$LogPath);游戏未启动或日志目录不对"
    return $true
}

function Show-MtHits {
    <#
    .SYNOPSIS
        打印命中示例行（最多 limit 条，每条截断 160 字符）。

    .NOTES
        必须**复用已编译的正则对象**，不要用字符串重新拼 `.*{pattern}.*` 再编译：早期实现
        正是后者，一旦 pattern 带内联标志（如用例里的 `(?i)oculus`），标志就不在表达式开头
        → Python 3.11+ 抛 re.error，异常未捕获 → 进程带 traceback 退出（stdout 已打印 PASS）
        → 表现为「断言打印 PASS 却被上层判成 FAIL」（2026-09-12 真机实测）。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][regex]$Pattern,
        [Parameter(Mandatory)][AllowEmptyString()][string]$Text,
        [int]$Limit = 3
    )

    $shown = 0
    foreach ($ln in @($Text -split "`n")) {
        $line = $ln.TrimEnd("`r")
        if (-not $Pattern.IsMatch($line)) { continue }
        $s = $line.Trim()
        if ($s.Length -gt 160) { $s = $s.Substring(0, 160) }
        Write-MtLine "    $s"
        $shown++
        if ($shown -ge $Limit) { break }
    }
}

function New-MtRegex {
    <#
    .SYNOPSIS
        编译正则；失败时按 python 的 `非法正则` 分支报错退出（rc 2）。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Pattern)

    try {
        return [regex]::new($Pattern)
    } catch {
        Write-MtErrLine "MT_ASSERT: ERROR — 非法正则 $Pattern：$($_.Exception.Message)"
        exit 2
    }
}

# ── 子命令 ════════════════════════════════════════════════════════════════
function Invoke-MtAssertSnapshot {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Version)

    $p = Get-MtPaths -Version $Version
    $runId = Get-MtActiveRunId
    $snap = Get-MtSnapshot
    if (-not $snap.Contains('run_id') -or $snap['run_id'] -ne $runId) {
        $snap = [ordered]@{ run_id = $runId; versions = [ordered]@{} }
    }
    if (-not $snap.Contains('versions') -or $null -eq $snap['versions']) {
        $snap['versions'] = [ordered]@{}
    }

    $offsets = [ordered]@{}
    foreach ($log in @($p.latest_log, $p.debug_log, $p.kubejs_log, $p.probe_log)) {
        $name = [System.IO.Path]::GetFileName($log)
        $size = 0
        try {
            if (Test-Path -LiteralPath $log -PathType Leaf) { $size = (Get-Item -LiteralPath $log).Length }
        } catch { $size = 0 }
        $offsets[$name] = [long]$size
    }

    $crashNames = @()
    if (Test-Path -LiteralPath $p.crash_dir -PathType Container) {
        $crashNames = @(Get-ChildItem -LiteralPath $p.crash_dir -File -Filter '*.txt' |
            ForEach-Object { $_.Name } | Sort-Object)
    }

    $snap['versions'][$Version] = [ordered]@{
        offsets        = $offsets
        ts             = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() / 1000.0
        crash_baseline = @($crashNames)
        mixin_errors   = @()
    }

    Save-MtSnapshot -Snapshot $snap

    $latest = if ($offsets.Contains('latest.log')) { $offsets['latest.log'] } else { 0 }
    $debug = if ($offsets.Contains('debug.log')) { $offsets['debug.log'] } else { 0 }
    $probe = if ($offsets.Contains('astral_probe.log')) { $offsets['astral_probe.log'] } else { 0 }
    Write-MtLine "MT_SNAPSHOT: OK — $Version run=$runId latest=${latest}B debug=${debug}B probe=${probe}B"
    return 0
}

function Invoke-MtAssertLog {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Version,
        [Parameter(Mandatory)][string]$PatternText,
        [Parameter(Mandatory)][string]$Source,
        [bool]$SinceSnapshot = $true
    )

    $p = Get-MtPaths -Version $Version
    if (-not $SinceSnapshot) {
        Write-MtWarn '未指定 --since-snapshot，将对全文件求值（可能被历史标记污染）'
    }
    $log = Get-MtLogPath -Paths $p -Source $Source
    if (Test-MtLogMissing -Source $Source -LogPath $log) { return 2 }

    $offset = 0
    if ($SinceSnapshot) {
        $entry = Get-MtSnapFor -Version $Version
        if ($entry.Contains('offsets')) {
            $name = [System.IO.Path]::GetFileName($log)
            if ($entry['offsets'].Contains($name)) { $offset = [long]$entry['offsets'][$name] }
        }
    }

    $text = Read-MtLogDelta -LogPath $log -Offset $offset
    $pat = New-MtRegex -Pattern $PatternText
    $hits = $pat.Matches($text).Count
    $label = "$Source`:$([System.IO.Path]::GetFileName($log))@${offset}B"

    if ($hits -gt 0) {
        Write-MtLine "MT_ASSERT_LOG: PASS — /$PatternText/ 命中 $hits 次（$label）"
        Show-MtHits -Pattern $pat -Text $text
        return 0
    }
    Write-MtLine "MT_ASSERT_LOG: FAIL — /$PatternText/ 未命中（$label）"
    return 1
}

function Invoke-MtAssertAbsent {
    <#
    .SYNOPSIS
        反向断言：增量区间内不得出现某标记（用于「旧机制无残留」类 TC）。

    .NOTES
        通道缺失时返回 ERROR 而不是 PASS —— 否则「文件不存在」会被静默判成「标记未出现」，
        把环境故障伪装成通过。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Version,
        [Parameter(Mandatory)][string]$PatternText,
        [Parameter(Mandatory)][string]$Source
    )

    $p = Get-MtPaths -Version $Version
    $log = Get-MtLogPath -Paths $p -Source $Source
    if (Test-MtLogMissing -Source $Source -LogPath $log) { return 2 }

    $offset = 0
    $entry = Get-MtSnapFor -Version $Version
    if ($entry.Contains('offsets')) {
        $name = [System.IO.Path]::GetFileName($log)
        if ($entry['offsets'].Contains($name)) { $offset = [long]$entry['offsets'][$name] }
    }

    $text = Read-MtLogDelta -LogPath $log -Offset $offset
    $pat = New-MtRegex -Pattern $PatternText
    $label = "$Source`:$([System.IO.Path]::GetFileName($log))@${offset}B"

    if ($pat.IsMatch($text)) {
        Write-MtLine "MT_ASSERT_ABSENT: FAIL — 不应出现的 /$PatternText/ 出现了"
        Show-MtHits -Pattern $pat -Text $text
        return 1
    }
    Write-MtLine "MT_ASSERT_ABSENT: PASS — /$PatternText/ 未出现（$label）"
    return 0
}

function Invoke-MtAssertCrash {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Version)

    $p = Get-MtPaths -Version $Version
    $entry = Get-MtSnapFor -Version $Version
    $baseline = @()
    if ($entry.Contains('crash_baseline') -and $null -ne $entry['crash_baseline']) {
        $baseline = @($entry['crash_baseline'])
    }

    if (-not (Test-Path -LiteralPath $p.crash_dir -PathType Container)) {
        Write-MtLine 'MT_ASSERT_CRASH: PASS — 无 crash-reports 目录'
        return 0
    }

    $new = @(Get-ChildItem -LiteralPath $p.crash_dir -File -Filter '*.txt' |
        ForEach-Object { $_.Name } | Where-Object { $baseline -notcontains $_ } | Sort-Object)
    if ($new.Count -gt 0) {
        Write-MtLine "MT_ASSERT_CRASH: FAIL — 新增崩溃报告 $($new -join ', ')"
        return 1
    }
    Write-MtLine "MT_ASSERT_CRASH: PASS — 本次运行无新增崩溃报告（基线 $($baseline.Count) 个）"
    return 0
}

function Invoke-MtAssertKubejs {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Version)

    $p = Get-MtPaths -Version $Version
    if (-not (Test-Path -LiteralPath $p.kubejs_log -PathType Leaf)) {
        Write-MtLine 'MT_ASSERT_KUBEJS: BLOCKED — 未找到 KubeJS server.log（KubeJS 未加载或尚未进入世界）'
        return 11
    }
    $text = ConvertTo-MtUniversalNewlines -Text ([System.IO.File]::ReadAllText($p.kubejs_log, $script:TAG_UTF8))
    $errs = @()
    foreach ($ln in @($text -split "`n")) {
        $line = $ln.TrimEnd("`r")
        if ([regex]::IsMatch($line, '\bERROR\b|\bException\b')) { $errs += $line }
    }
    if ($errs.Count -gt 0) {
        Write-MtLine "MT_ASSERT_KUBEJS: FAIL — server.log 含 $($errs.Count) 条 error"
        foreach ($ln in @($errs | Select-Object -First 5)) {
            $s = ([string]$ln).Trim()
            if ($s.Length -gt 160) { $s = $s.Substring(0, 160) }
            Write-MtLine "    $s"
        }
        return 1
    }
    Write-MtLine 'MT_ASSERT_KUBEJS: PASS — server.log 0 errors'
    return 0
}

function Invoke-MtAssertMixin {
    <#
    .SYNOPSIS
        增量区间内不得出现 Mixin 应用失败（渲染栈兼容性的硬信号）。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Version)

    $p = Get-MtPaths -Version $Version
    $log = if (Test-Path -LiteralPath $p.debug_log -PathType Leaf) { $p.debug_log } else { $p.latest_log }
    $offset = 0
    $entry = Get-MtSnapFor -Version $Version
    if ($entry.Contains('offsets')) {
        $name = [System.IO.Path]::GetFileName($log)
        if ($entry['offsets'].Contains($name)) { $offset = [long]$entry['offsets'][$name] }
    }

    $text = Read-MtLogDelta -LogPath $log -Offset $offset
    $pat = [regex]::new('Mixin apply failed|Mixin apply error|Failed to apply mixin')
    if ($pat.IsMatch($text)) {
        Write-MtLine 'MT_ASSERT_MIXIN: FAIL — 检测到 Mixin 应用失败（兼容模组栈异常）'
        return 1
    }
    Write-MtLine "MT_ASSERT_MIXIN: PASS — $([System.IO.Path]::GetFileName($log)) 增量区间无 Mixin 失败"
    return 0
}

# ── 入口 ══════════════════════════════════════════════════════════════════
if ($MyInvocation.InvocationName -ne '.') {

    $Cmd = ''
    $Version = ''
    $PatternText = ''
    $Source = 'latest'
    $SinceSnapshot = $true

    $i = 0
    while ($i -lt $args.Count) {
        $tok = [string]$args[$i]
        $key = $tok.TrimStart('-').ToLowerInvariant()
        if ($tok -notlike '-*') {
            if ($Cmd) { Write-MtErrorLine "未知参数 $tok"; exit $MT_EXIT_ERROR }
            $Cmd = $key
            $i++
        } elseif ($key -eq 'version') {
            if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --version 的值'; exit $MT_EXIT_ERROR }
            $Version = [string]$args[$i + 1]; $i += 2
        } elseif ($key -eq 'pattern') {
            if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --pattern 的值'; exit $MT_EXIT_ERROR }
            $PatternText = [string]$args[$i + 1]; $i += 2
        } elseif ($key -eq 'source') {
            if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --source 的值'; exit $MT_EXIT_ERROR }
            $Source = [string]$args[$i + 1]; $i += 2
        } elseif ($key -eq 'since-snapshot') {
            $SinceSnapshot = $true; $i++
        } elseif ($key -eq 'no-snapshot') {
            $SinceSnapshot = $false; $i++
        } else {
            Write-MtErrorLine "未知参数 $tok"; exit $MT_EXIT_ERROR
        }
    }

    if ($Cmd -notin @('snapshot', 'log', 'absent', 'crash', 'kubejs', 'mixin')) {
        Write-MtErrorLine '必须指定子命令 snapshot / log / absent / crash / kubejs / mixin'
        exit $MT_EXIT_ERROR
    }
    if (-not $Version) { Write-MtErrorLine '必须指定 --version'; exit $MT_EXIT_ERROR }
    if (-not (Assert-MtVersion -Version $Version)) { exit $MT_EXIT_ERROR }
    if ($Cmd -eq 'log' -and -not $PatternText) { Write-MtErrorLine 'log 子命令必须指定 --pattern'; exit $MT_EXIT_ERROR }
    if ($Cmd -eq 'absent' -and -not $PatternText) { Write-MtErrorLine 'absent 子命令必须指定 --pattern'; exit $MT_EXIT_ERROR }
    if (-not $script:LogSources.Contains($Source)) {
        Write-MtErrorLine "未知断言通道 $Source"
        exit $MT_EXIT_ERROR
    }

    switch ($Cmd) {
        'snapshot' { exit (Invoke-MtAssertSnapshot -Version $Version) }
        'log' { exit (Invoke-MtAssertLog -Version $Version -PatternText $PatternText -Source $Source -SinceSnapshot $SinceSnapshot) }
        'absent' { exit (Invoke-MtAssertAbsent -Version $Version -PatternText $PatternText -Source $Source) }
        'crash' { exit (Invoke-MtAssertCrash -Version $Version) }
        'kubejs' { exit (Invoke-MtAssertKubejs -Version $Version) }
        'mixin' { exit (Invoke-MtAssertMixin -Version $Version) }
    }
    exit $MT_EXIT_ERROR
}
