#Requires -Version 7.0
<#
.SYNOPSIS
    mt_report — 证据收集与报告生成（阶段 R）。

.DESCRIPTION
    产物:
      reports/<run_id>/<version>/report.md    单版本报告（含明确的通过/失败结论）
      reports/<run_id>/SUMMARY.md             总览（1.21.1 与 1.20.1 各自结论 + 综合结论）

.EXAMPLE
    pwsh -File scripts/test/mt_report.ps1 mark    --version 1.21.1 --phase env --result PASS
    pwsh -File scripts/test/mt_report.ps1 mark    --version 1.21.1 --case TC1 --result PASS
    pwsh -File scripts/test/mt_report.ps1 collect --version 1.21.1 --verdict PASS
    pwsh -File scripts/test/mt_report.ps1 summary

.NOTES
    迁移前源文件 scripts/test/mt_report.py（该原件已在 92fbeaf「工具链收敛为纯 pwsh」删除，取回：`git show 92fbeaf^:scripts/test/mt_report.py`）。

    `.mt_run_state.json`（cases/.mt_run_state.json）是阶段/条目状态的**跨语言契约文件**：
    python 版与 pwsh 版会互相读写，键名与结构必须一致（`phases.<name>.result/ts`、
    `cases.<name>` = 结果字符串）。

    文案偏差：报告里的「复跑命令」由 `bash scripts/test/mt.sh …`（旧 bash 版脚本已在 92fbeaf 删除）改为
    `pwsh -File scripts/test/mt.ps1 …`（同一入口的新名字）。
#>

$ErrorActionPreference = 'Stop'

$script:LibDir = Join-Path $PSScriptRoot 'lib'
Import-Module (Join-Path $script:LibDir 'Mt.Phase.psm1')
Import-Module (Join-Path $script:LibDir 'Mt.Paths.psm1')
Import-Module (Join-Path $script:LibDir 'Mt.Proc.psm1')

Initialize-MtConsole

$script:TestDir = Get-MtTestDir
$script:StateFile = Join-Path (Join-Path $script:TestDir 'cases') '.mt_run_state.json'
$script:SnapshotFile = Join-Path (Join-Path $script:TestDir 'cases') '.mt_snapshot.json'
# 报告里的「分支」取**运行时的实际分支**（2026-09-17 用户裁决移除「测试分支白名单」后，
# 报告不得再把 multi-dev-next 等分支的运行记成发布线 `multi-1.20.1-1.21.1`）；读不到时用该兜底值。
$script:BranchNameFallback = 'unknown'
$script:TAG_UTF8 = [System.Text.UTF8Encoding]::new($false, $false)

function Get-MtReportBranchName {
    <#
    .SYNOPSIS
        当前实际分支名（报告元数据用）。返回 [string]；任何失败都回落 $script:BranchNameFallback。

    .NOTES
        分支在这里**不是判定**，只作报告标注 —— 白名单强断言已于 2026-09-17 按用户裁决移除
        （见 mt_preflight.ps1 的 Get-MtPreflightBranch）。git 缺失 / 非 git 目录 / detached HEAD
        等情况下返回兜底值 'unknown'，不得让报告生成失败。
    #>
    [CmdletBinding()]
    param()

    try {
        $r = Invoke-MtProcessFull -FilePath 'git' `
            -ArgumentList @('-C', (Get-MtRoot), 'rev-parse', '--abbrev-ref', 'HEAD') -TimeoutSec 30
        if ($r.ExitCode -eq 0) {
            $name = ([string]$r.StdOut).Trim()
            if ($name) { return $name }
        }
    } catch { }
    return $script:BranchNameFallback
}

function Get-MtReportState {
    [CmdletBinding()]
    param()

    if (Test-Path -LiteralPath $script:StateFile -PathType Leaf) {
        try {
            $obj = ([System.IO.File]::ReadAllText($script:StateFile, $script:TAG_UTF8)) |
                ConvertFrom-Json -AsHashtable -ErrorAction Stop
            if ($null -ne $obj) { return $obj }
        } catch { }
    }
    return [ordered]@{
        run_id   = Get-MtActiveRunId
        started  = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() / 1000.0
        versions = [ordered]@{}
    }
}

function Save-MtReportState {
    [CmdletBinding()]
    param([Parameter(Mandatory)]$State)

    $dir = [System.IO.Path]::GetDirectoryName($script:StateFile)
    if (-not (Test-Path -LiteralPath $dir)) { [void](New-Item -ItemType Directory -Force -Path $dir) }
    [System.IO.File]::WriteAllText($script:StateFile, (ConvertTo-MtJson -InputObject $State), $script:TAG_UTF8)
}

function Get-MtNowStamp {
    <#
    .SYNOPSIS
        python `time.strftime('%Y-%m-%d %H:%M:%S')` 的等价物（本地时间、24 小时制、零填充）。
    #>
    [CmdletBinding()]
    param()

    return (Get-Date).ToString('yyyy-MM-dd HH:mm:ss', [cultureinfo]::InvariantCulture)
}

function Get-MtReportSnapshot {
    <#
    .SYNOPSIS
        读断言快照（缺文件/损坏时返回空表）；供增量区间与崩溃基线使用。
    #>
    [CmdletBinding()]
    param()

    if (-not (Test-Path -LiteralPath $script:SnapshotFile -PathType Leaf)) { return [ordered]@{} }
    try {
        $obj = ([System.IO.File]::ReadAllText($script:SnapshotFile, $script:TAG_UTF8)) |
            ConvertFrom-Json -AsHashtable -ErrorAction Stop
        if ($null -eq $obj) { return [ordered]@{} }
        return $obj
    } catch {
        return [ordered]@{}
    }
}

# ── mark ══════════════════════════════════════════════════════════════════
function Invoke-MtReportMark {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Version,
        [string]$Phase = '',
        [string]$CaseName = '',
        [Parameter(Mandatory)][string]$Result
    )

    $state = Get-MtReportState
    if (-not $state.Contains('versions') -or $null -eq $state['versions']) {
        $state['versions'] = [ordered]@{}
    }
    if (-not $state['versions'].Contains($Version)) {
        $state['versions'][$Version] = [ordered]@{ phases = [ordered]@{}; cases = [ordered]@{} }
    }
    $v = $state['versions'][$Version]
    if (-not $v.Contains('phases') -or $null -eq $v['phases']) { $v['phases'] = [ordered]@{} }
    if (-not $v.Contains('cases') -or $null -eq $v['cases']) { $v['cases'] = [ordered]@{} }

    if ($Phase) {
        $v['phases'][$Phase] = [ordered]@{
            result = $Result
            ts     = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() / 1000.0
        }
    }
    if ($CaseName) { $v['cases'][$CaseName] = $Result }
    Save-MtReportState -State $state

    $what = if ($Phase) { "phase=$Phase" } else { "case=$CaseName" }
    Write-MtLine "MT_MARK: $Version $what → $Result"
    return 0
}

# ── collect ═══════════════════════════════════════════════════════════════
function Copy-MtEvidence {
    <#
    .SYNOPSIS
        把本次运行的证据文件复制到报告目录；返回已复制条目清单。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][psobject]$Paths, [Parameter(Mandatory)][string]$Dest)

    if (-not (Test-Path -LiteralPath $Dest)) { [void](New-Item -ItemType Directory -Force -Path $Dest) }
    $copied = @()

    $sources = @(
        @{ Src = $Paths.latest_log; Name = 'latest.log' }
        @{ Src = $Paths.debug_log; Name = 'debug.log' }
        @{ Src = $Paths.kubejs_log; Name = 'kubejs_server.log' }
        @{ Src = (Join-Path $Paths.run_dir 'runclient_launch.log'); Name = 'runclient_launch.log' }
    )
    foreach ($s in $sources) {
        if (-not (Test-Path -LiteralPath $s.Src -PathType Leaf)) { continue }
        try {
            Copy-Item -LiteralPath $s.Src -Destination (Join-Path $Dest $s.Name) -Force
            $copied += $s.Name
        } catch { }
    }

    if (Test-Path -LiteralPath $Paths.crash_dir -PathType Container) {
        foreach ($f in @(Get-ChildItem -LiteralPath $Paths.crash_dir -File -Filter '*.txt')) {
            Copy-Item -LiteralPath $f.FullName -Destination (Join-Path $Dest "crash_$($f.Name)") -Force
            $copied += "crash_$($f.Name)"
        }
    }

    $runId = Get-MtActiveRunId
    $shots = @(Get-MtCurrentShots -Paths $Paths -RunId $runId)
    if ($shots.Count -gt 0) {
        $sd = Join-Path $Dest 'screenshots'
        if (-not (Test-Path -LiteralPath $sd)) { [void](New-Item -ItemType Directory -Force -Path $sd) }
        foreach ($s in $shots) {
            Copy-Item -LiteralPath $s.FullName -Destination (Join-Path $sd $s.Name) -Force
        }
        $copied += "screenshots/$($shots.Count) 张（当前世代）"
    }
    return , $copied
}

function Get-MtReportDelta {
    <#
    .SYNOPSIS
        返回 (增量文本, 起始偏移)。与 mt_assert **同源**，保证报告与断言看到同一区间。

    .NOTES
        偏移固定取快照里 `debug.log` 一项（python 原实现如此），再在
        debug_log / latest_log 之间选实际存在的那个来读。

        B7：优先取 `launch_offsets`（自 launch 起）。`offsets` 已被改写成**最后一条用例**的
        窗口起点（每条用例开始时刷新），若沿用它，报告的关键标记摘要会只剩最后一条用例 —— 
        collect 是**整轮**的取证，必须看整轮。旧快照无该键时回落到 `offsets`。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][psobject]$Paths)

    $offset = 0
    try {
        $snap = Get-MtReportSnapshot
        if ($snap.Contains('versions')) {
            $versions = $snap['versions']
            if ($null -ne $versions -and $versions.Contains($Paths.version)) {
                $entry = $versions[$Paths.version]
                $key = if ($entry.Contains('launch_offsets') -and $null -ne $entry['launch_offsets']) { 'launch_offsets' } else { 'offsets' }
                if ($entry.Contains($key) -and $null -ne $entry[$key] -and $entry[$key].Contains('debug.log')) {
                    $offset = [long]$entry[$key]['debug.log']
                }
            }
        }
    } catch {
        $offset = 0
    }

    $log = if (Test-Path -LiteralPath $Paths.debug_log -PathType Leaf) { $Paths.debug_log } else { $Paths.latest_log }
    if (-not (Test-Path -LiteralPath $log -PathType Leaf)) { return , @('', 0) }

    $size = (Get-Item -LiteralPath $log).Length
    if ($offset -gt $size) { $offset = 0 }

    $fs = [System.IO.File]::Open($log, [System.IO.FileMode]::Open,
        [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
    try {
        [void]$fs.Seek($offset, [System.IO.SeekOrigin]::Begin)
        $ms = [System.IO.MemoryStream]::new()
        try {
            $fs.CopyTo($ms)
            # python 文本模式会做通用换行翻译（CRLF→LF），这里必须一致
            $text = $script:TAG_UTF8.GetString($ms.ToArray()).Replace("`r`n", "`n").Replace("`r", "`n")
            return , @($text, $offset)
        } finally { $ms.Dispose() }
    } finally { $fs.Dispose() }
}

function Get-MtLogMarkers {
    <#
    .SYNOPSIS
        只列增量区间内的关键标记（不复用上一世代内容）；最多取最后 60 条。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][psobject]$Paths)

    $delta = Get-MtReportDelta -Paths $Paths
    $text = [string]$delta[0]
    $offset = [long]$delta[1]
    $keys = @('Astral Dice', 'TargetSelection', 'TS_COUNT', 'TS_PRESENT', 'SLOTCHECK')

    $hits = @()
    foreach ($ln in @($text -split "`n")) {
        $line = $ln.TrimEnd("`r")
        $hit = $false
        foreach ($k in $keys) { if ($line.Contains($k)) { $hit = $true; break } }
        if (-not $hit) { continue }
        $s = $line.Trim()
        if ($s.Length -gt 200) { $s = $s.Substring(0, 200) }
        $hits += $s
    }
    if ($hits.Count -gt 60) { $hits = @($hits[($hits.Count - 60)..($hits.Count - 1)]) }
    return , @($hits, $offset)
}

function Get-MtCrashSplit {
    <#
    .SYNOPSIS
        区分「基线（本次运行之前就存在）」与「本次新增」崩溃报告；返回 (old, new)。

    .NOTES
        判据是双重的：既看快照基线，也看文件是否晚于本次运行开始时间。只靠基线不够 ——
        独立执行 collect（没有快照点）时，上一世代的旧崩溃报告会被误报成「本次新增」，
        那是会误导结论的假证据。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][psobject]$Paths)

    $old = @(); $new = @()
    if (-not (Test-Path -LiteralPath $Paths.crash_dir -PathType Container)) { return , @($old, $new) }

    $baseline = @()
    try {
        $snap = Get-MtReportSnapshot
        if ($snap.Contains('versions')) {
            $versions = $snap['versions']
            if ($null -ne $versions -and $versions.Contains($Paths.version)) {
                $entry = $versions[$Paths.version]
                if ($entry.Contains('crash_baseline') -and $null -ne $entry['crash_baseline']) {
                    $baseline = @($entry['crash_baseline'])
                }
            }
        }
    } catch { $baseline = @() }

    $runStart = Get-MtRunStartTs -RunId (Get-MtActiveRunId)
    $names = @(Get-ChildItem -LiteralPath $Paths.crash_dir -File -Filter '*.txt' |
        ForEach-Object { $_.Name } | Sort-Object)
    foreach ($name in $names) {
        $fresh = $false
        try {
            $mtime = [DateTimeOffset]::new((Get-Item -LiteralPath (Join-Path $Paths.crash_dir $name)).LastWriteTimeUtc).ToUnixTimeSeconds()
            $fresh = ($mtime -ge $runStart)
        } catch { $fresh = $false }

        if ($fresh -and ($baseline -notcontains $name)) { $new += $name } else { $old += $name }
    }
    return , @($old, $new)
}

function Invoke-MtReportCollect {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Version, [string]$Verdict = '')

    $p = Get-MtPaths -Version $Version
    $runId = Get-MtActiveRunId
    $state = Get-MtReportState
    $vs = [ordered]@{}
    if ($state.Contains('versions') -and $null -ne $state['versions'] -and $state['versions'].Contains($Version)) {
        $vs = $state['versions'][$Version]
    }

    $dest = Join-Path (Join-Path (Join-Path $script:TestDir 'reports') $runId) $Version
    $copied = Copy-MtEvidence -Paths $p -Dest $dest

    $markerPair = Get-MtLogMarkers -Paths $p
    $markers = @($markerPair[0]); $offset = [long]$markerPair[1]

    $crashPair = Get-MtCrashSplit -Paths $p
    $baselineCrashes = @($crashPair[0]); $newCrashes = @($crashPair[1])

    $phases = if ($vs.Contains('phases') -and $null -ne $vs['phases']) { $vs['phases'] } else { [ordered]@{} }
    $cases = if ($vs.Contains('cases') -and $null -ne $vs['cases']) { $vs['cases'] } else { [ordered]@{} }

    $verdict = $Verdict
    if (-not $verdict) {
        $allPass = ($cases.Count -gt 0)
        foreach ($k in $cases.Keys) { if ($cases[$k] -ne 'PASS') { $allPass = $false } }
        $verdict = if ($allPass) { 'PASS' } else { 'FAIL' }
    }

    $lines = @(
        "# 自动化测试报告 — $Version",
        '',
        "- **运行**: ``$runId``",
        "- **版本/加载器**: $Version / $($p.loader)（子项目 ``$($p.subproject)``）",
        "- **分支**: $(Get-MtReportBranchName)",
        "- **环境**: ``$($p.run_dir)``（dev 本体，quickplay=$([System.IO.Path]::GetFileName($p.client_world))）",
        "- **生成时间**: $(Get-MtNowStamp)",
        '',
        '## 结论',
        '',
        "**$Version`: $(if ($verdict -eq 'PASS') { '✅ PASS（通过）' } else { '❌ FAIL（未通过）' })**",
        '',
        '## 阶段结果',
        '',
        '| 阶段 | 结果 |',
        '|---|---|'
    )
    foreach ($name in @('preflight', 'build', 'env', 'launch', 'cases', 'report')) {
        $r = '未执行'
        if ($phases.Contains($name) -and $null -ne $phases[$name] -and $phases[$name].Contains('result')) {
            $r = [string]$phases[$name]['result']
        }
        $lines += "| $name | $r |"
    }

    $lines += @('', '## 条目结果', '', '| 条目 | 结果 |', '|---|---|')
    if ($cases.Count -gt 0) {
        foreach ($k in $cases.Keys) { $lines += "| $k | $($cases[$k]) |" }
    } else {
        $lines += '| （无） | — |'
    }

    $lines += @('', '## 关键日志标记', '',
        "（区间：$([System.IO.Path]::GetFileName($p.debug_log))@${offset}B 之后，即本次运行新增部分）", '', '```')
    $lines += if ($markers.Count -gt 0) { $markers } else { @('（无）') }
    $lines += @('```', '', '## 崩溃 / 异常', '')
    if ($newCrashes.Count -gt 0) {
        $lines += "- **本次新增崩溃报告 $($newCrashes.Count) 个**：$($newCrashes -join ', ')"
    } else {
        $lines += '- 本次新增崩溃报告: 无'
    }
    if ($baselineCrashes.Count -gt 0) {
        $lines += "- 基线（本次运行之前已存在，不计入判定）$($baselineCrashes.Count) 个：$($baselineCrashes -join ', ')"
    }
    $lines += "- kubejs server.log: $(if (Test-Path -LiteralPath $p.kubejs_log -PathType Leaf) { '存在' } else { '缺失' })"

    $lines += @('', '## 证据', '')
    $lines += if ($copied.Count -gt 0) { @($copied | ForEach-Object { "- ``$_``" }) } else { @('- （无）') }

    $lines += @('', '## 复跑命令', '', '```bash')
    # 文案偏差：入口已由 mt.sh 换成 mt.ps1（同一入口的新名字）
    $lines += "pwsh -File scripts/test/mt.ps1 --version $Version --case cases/<条目>.json"
    $lines += '```'

    $reportPath = Join-Path $dest 'report.md'
    [System.IO.File]::WriteAllText($reportPath, (($lines -join "`n") + "`n"), $script:TAG_UTF8)
    Write-MtLine "MT_REPORT: OK — $reportPath（结论 $verdict）"
    return 0
}

function Invoke-MtReportSummary {
    [CmdletBinding()]
    param()

    $runId = Get-MtActiveRunId
    $state = Get-MtReportState
    $dest = Join-Path (Join-Path $script:TestDir 'reports') $runId
    if (-not (Test-Path -LiteralPath $dest)) { [void](New-Item -ItemType Directory -Force -Path $dest) }

    $lines = @(
        '# 自动化测试总览',
        '',
        "- **运行**: ``$runId``",
        "- **分支**: $(Get-MtReportBranchName)",
        "- **生成时间**: $(Get-MtNowStamp)",
        '',
        '> 测试顺序：先 1.21.1，通过后才执行 1.20.1。两个版本都给出独立结论。',
        '> `mt.ps1 --version <V>` 指定单版本时只跑该版本、不做跨版本门控，总览也只列实际执行过的版本。',
        '',
        '## 版本结论',
        '',
        '| 顺序 | 版本 | 结论 | 条目 |',
        '|---|---|---|---|'
    )

    # 单版本运行（mt.ps1 --version <V>）时，状态文件里只会有那一个版本；
    # 此时**不能**把未执行的另一个版本判成「未执行 → 总览 FAIL」（否则 --version 单跑必然自判失败）。
    $summaryVersions = @(Get-MtVersions)
    if ($state.Contains('versions') -and $null -ne $state['versions']) {
        $present = @($state['versions'].Keys)
        if ($present.Count -gt 0 -and $present.Count -lt $summaryVersions.Count) {
            $summaryVersions = @($summaryVersions | Where-Object { $present -contains $_ })
        }
    }

    $overallPass = $true
    $idx = 0
    foreach ($v in $summaryVersions) {
        $idx++
        $cases = [ordered]@{}
        if ($state.Contains('versions') -and $null -ne $state['versions'] -and $state['versions'].Contains($v)) {
            $vs = $state['versions'][$v]
            if ($vs.Contains('cases') -and $null -ne $vs['cases']) { $cases = $vs['cases'] }
        }
        if ($cases.Count -eq 0) {
            $verdict = '未执行'
            $overallPass = $false
        } else {
            # ⑤⑥（B6）：把结果**连名字一起**列出来，并把 TIMEOUT 单列 —— 超时是独立结论，
            # 不是"断言不满足"（FAIL），也不是"跑不起来"（ERROR）。
            $bad = @()
            $timeouts = @()
            foreach ($k in $cases.Keys) {
                $res = [string]$cases[$k]
                if (@('PASS', 'SKIP') -notcontains $res) {
                    $bad += ("{0}={1}" -f $k, $res)
                    if ($res -eq 'TIMEOUT') { $timeouts += $k }
                }
            }
            if ($bad.Count -eq 0) {
                $verdict = '✅ PASS'
            } elseif ($timeouts.Count -gt 0 -and $timeouts.Count -eq $bad.Count) {
                $verdict = "⏱ TIMEOUT（$($bad -join ', ')）"
            } else {
                $verdict = "❌ FAIL（$($bad -join ', ')）"
            }
            if ($bad.Count -gt 0) { $overallPass = $false }
        }
        $lines += "| $idx | $v | $verdict | $($cases.Count) |"
    }

    $lines += @('', '## 综合结论', '')
    $lines += "**$(if ($overallPass) { '✅ 全部通过' } else { '❌ 未全部通过' })**"
    $lines += @('', '各版本详细报告见对应子目录 `report.md`。')

    $summaryPath = Join-Path $dest 'SUMMARY.md'
    [System.IO.File]::WriteAllText($summaryPath, (($lines -join "`n") + "`n"), $script:TAG_UTF8)
    Write-MtLine "MT_REPORT: OK — $summaryPath"
    if ($overallPass) { return 0 }
    return 1
}

# ── 入口 ══════════════════════════════════════════════════════════════════
if ($MyInvocation.InvocationName -ne '.') {

    $Cmd = ''
    $Version = ''
    $Phase = ''
    $CaseName = ''
    $Result = ''
    $Verdict = ''

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
        } elseif ($key -eq 'phase') {
            if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --phase 的值'; exit $MT_EXIT_ERROR }
            $Phase = [string]$args[$i + 1]; $i += 2
        } elseif ($key -eq 'case') {
            if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --case 的值'; exit $MT_EXIT_ERROR }
            $CaseName = [string]$args[$i + 1]; $i += 2
        } elseif ($key -eq 'result') {
            if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --result 的值'; exit $MT_EXIT_ERROR }
            $Result = [string]$args[$i + 1]; $i += 2
        } elseif ($key -eq 'verdict') {
            if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --verdict 的值'; exit $MT_EXIT_ERROR }
            $Verdict = [string]$args[$i + 1]; $i += 2
        } else {
            Write-MtErrorLine "未知参数 $tok"; exit $MT_EXIT_ERROR
        }
    }

    if ($Cmd -notin @('mark', 'collect', 'summary')) {
        Write-MtErrorLine '必须指定子命令 mark / collect / summary'
        exit $MT_EXIT_ERROR
    }
    if ($Cmd -ne 'summary') {
        if (-not $Version) { Write-MtErrorLine '必须指定 --version'; exit $MT_EXIT_ERROR }
        if (-not (Assert-MtVersion -Version $Version)) { exit $MT_EXIT_ERROR }
    }
    if ($Cmd -eq 'mark') {
        if (-not $Result) { Write-MtErrorLine 'mark 子命令必须指定 --result'; exit $MT_EXIT_ERROR }
        if (-not $Phase -and -not $CaseName) { Write-MtErrorLine 'mark 子命令必须指定 --phase 或 --case'; exit $MT_EXIT_ERROR }
    }

    switch ($Cmd) {
        'mark' { exit (Invoke-MtReportMark -Version $Version -Phase $Phase -CaseName $CaseName -Result $Result) }
        'collect' { exit (Invoke-MtReportCollect -Version $Version -Verdict $Verdict) }
        'summary' { exit (Invoke-MtReportSummary) }
    }
    exit $MT_EXIT_ERROR
}
