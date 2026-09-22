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

    ## B9（t22）：`versions` 只承载**本轮**的标记（轮次世代）

    症状与根因：`SUMMARY.md` / `report.md` 的条目聚合把 `.mt_run_state.json` 里**所有**历史标记
    都算作本次结果 ⇒ ① 工具链**故意造的负例**（如结构校验反例 `NEG-A-missing-no-esc: ERROR`）
    ② 上一轮/上一版本（1.20.1）的旧标记，都会让「本次全 PASS」的跑批在 `SUMMARY.md` 里显示 ❌、
    退出码非 0（实测：`report.md` ✅ PASS 而 `SUMMARY.md` ❌）。根因是那份状态文件没有「本次」的概念 ——
    `.mt_active_run` 是**粘性**的（只写不删，供各阶段共享），从来没有轮换过。

    判据（**只把不属于本次的标记排除，绝不放宽任何失败**）：
      · `mark` 是唯一写入 `versions` 的入口；**开新一轮 = 把上一轮的 `versions` 归档进 `history`
        再清空**（归档保留审计痕迹，形状与旧契约一致，读取方无需改）；
      · 开新一轮只在三个精确时机发生：① 状态文件里没有世代标记（旧口径 / 换版首跑 —— 历史标记
        无法归属，只能开新轮）；② 上一轮已被 `summary` 结算（`closed_at > 0`）；③ 本版本在本轮
        已经记过 `preflight`（一轮只会在开头记一次 preflight ⇒ 说明上一轮夭折或同版本被重跑）；
      · 本轮**进行中**绝不开新轮 ⇒ 同一轮里的真实失败一条都不会丢（`summary` 仍按
        `PASS/SKIP` 之外即计入 `bad` 的原判据给出 ❌ 与非 0 退出码）。

    文案偏差：报告里的「复跑命令」由 `bash scripts/test/mt.sh …`（旧 bash 版脚本已在 92fbeaf 删除）改为
    `pwsh -File scripts/test/mt.ps1 …`（同一入口的新名字）。

    ## 规则 14（收严版，2026-09-19 用户裁决）：`USER_TERMINATED` = **第三类「无效运行」**

    原文：「被用户主动终止的游戏进程一律视为作废。此次测试产生的任何结果都必须视为无效。
    需要重新执行测试进行验证。」

    · 判定：本轮 `versions.<V>.cases.<条目>` 任一为 `USER_TERMINATED`（`mt_case.ps1` 的唯一写入口
      `mark --case … --result USER_TERMINATED`）⇒ 该次运行标「无效运行（MT_USER_TERMINATED）」；
      报告层**覆盖**调用方传入的 `--verdict`（编排侧只能折出 PASS/TIMEOUT/FAIL 三种，无法表达第三类）。
    · 处置：**不计入通过率**、**不作为缺陷依据**、不与产品 `❌ FAIL` 混淆；`report.md` 的条目表与
      `SUMMARY.md` 的版本结论**逐条如实并列**全部结果串（含并存的真实失败串）供审计。
    · 退出码：沿用**既有**非 0 中断码 `MT_EXIT_ERROR(2)`（与用例级 `USER_TERMINATED` 同一数值，不新造）。
    · 收集阶段命中时会**提前返回**（早于生物 AI 审计的 ERROR 归因）——否则用户杀进程导致的心跳缺失
      会被误读成「读数被生物 AI 污染」，与该规则冲突。
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

# ── t22（B9）：轮次世代 ────────────────────────────────────────────────────
# 归档保留几轮（只影响审计痕迹，不影响判定）。
$script:MtRoundHistoryMax = 10

function Get-MtRoundMarkCount {
    <#
    .SYNOPSIS
        数一批 `versions` 里的标记条数（阶段 + 条目），只用于报告文本。
    #>
    [CmdletBinding()]
    param($Versions)

    $n = 0
    if ($null -eq $Versions) { return 0 }
    if ($Versions -is [System.Collections.IDictionary]) {
        foreach ($v in $Versions.Keys) {
            $vs = $Versions[$v]
            if ($null -eq $vs -or -not ($vs -is [System.Collections.IDictionary])) { continue }
            if ($vs.Contains('phases') -and $null -ne $vs['phases']) { $n += @($vs['phases'].Keys).Count }
            if ($vs.Contains('cases') -and $null -ne $vs['cases']) { $n += @($vs['cases'].Keys).Count }
        }
    }
    return $n
}

function Start-MtReportRound {
    <#
    .SYNOPSIS
        开新一轮测试：把上一轮的 `versions` **归档**进 `history`，清空本轮 `versions` 并盖上新世代号。

    .NOTES
        **不销毁任何历史**（归档而非删除）：`history` 里保留 `gen/started/closed_at/versions`，
        事后审计仍能看到「上一轮跑过什么、哪些条目 FAIL」。判定只读 `versions` ⇒ 归档即排除。

        为什么用「归档 + 清空」而不是「给每条标记盖世代号」：`cases.<name>` 的值必须是**结果字符串**
        （跨语言契约的既有形状，见文件头），改成对象会破坏契约；而轮次边界只有三个（见文件头），
        在边界上整块搬移最不容易出错，也不会漏掉任何一条本该计入的标记。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)]$State, [Parameter(Mandatory)][string]$Reason)

    $hist = @()
    # ⚠️ 不能写成 `$hist = if (…) { @($State['history']) } else { @() }`：if-**表达式**的输出走管道，
    #     单元素数组会被拆成那一个元素（实测：`history` 只有 1 条时 `$hist` 变成那个哈希表本身，
    #     `.Count` 变成它的键数 5）。`@()` 赋值不会发生这种拆解。
    if ($State.Contains('history') -and $null -ne $State['history']) { $hist = @($State['history']) }
    $archivedMarks = 0
    $archivedGen = if ($State.Contains('gen')) { [string]$State['gen'] } else { '' }

    if ($State.Contains('versions') -and $null -ne $State['versions'] -and @($State['versions'].Keys).Count -gt 0) {
        $archivedMarks = Get-MtRoundMarkCount -Versions $State['versions']
        $hist += [ordered]@{
            gen       = $archivedGen
            started   = $(if ($State.Contains('gen_started')) { $State['gen_started'] } else { $State['started'] })
            closed_at = $(if ($State.Contains('closed_at')) { $State['closed_at'] } else { 0 })
            reason    = $Reason
            versions  = $State['versions']
        }
        if ($hist.Count -gt $script:MtRoundHistoryMax) {
            $hist = @($hist[($hist.Count - $script:MtRoundHistoryMax)..($hist.Count - 1)])
        }
    }

    $State['history'] = $hist
    $State['versions'] = [ordered]@{}
    $State['gen'] = (Get-Date -Format 'yyyyMMdd-HHmmss')
    $State['gen_started'] = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $State['closed_at'] = 0
    if (-not $State.Contains('started')) { $State['started'] = $State['gen_started'] }
    if (-not $State.Contains('run_id')) { $State['run_id'] = Get-MtActiveRunId }

    return [pscustomobject]@{
        Gen           = [string]$State['gen']
        ArchivedGen   = $archivedGen
        ArchivedMarks = $archivedMarks
        HistoryCount  = $hist.Count
    }
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

    # ── t22（B9）：先判定是否需要开新一轮（必须在写入本条标记之前）─────────────
    $rotateReason = ''
    if (-not $state.Contains('gen')) {
        $rotateReason = '状态文件无世代标记（旧口径 / 换版首跑）⇒ 历史标记无法归属，开新一轮'
    } elseif ([double]$(if ($state.Contains('closed_at')) { $state['closed_at'] } else { 0 }) -gt 0) {
        $rotateReason = ("上一轮已由 summary 结算（closed_at={0}）⇒ 开新一轮" -f $state['closed_at'])
    } elseif ($Phase -eq 'preflight' -and $state['versions'].Contains($Version)) {
        $pv = $state['versions'][$Version]
        if ($null -ne $pv -and $pv.Contains('phases') -and $null -ne $pv['phases'] -and $pv['phases'].Contains('preflight')) {
            $rotateReason = '本版本的 preflight 在本轮已记过一次（上一轮夭折 / 同版本被重跑）⇒ 开新一轮'
        }
    }
    if ($rotateReason) {
        $round = Start-MtReportRound -State $state -Reason $rotateReason
        Write-MtLine ("MT_ROUND: 新一轮 {0} — {1}；已归档历史轮次 {2} 个 / {3} 条标记（**不计入**本次判定）" -f `
                $round.Gen, $rotateReason, $round.HistoryCount, $round.ArchivedMarks)
    }
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
        返回 (增量文本, 起始偏移, 窗口种类, 锚定说明)。与 mt_assert **同源**，保证报告与断言看到同一区间。

    .NOTES
        偏移固定取快照里 `debug.log` 一项（python 原实现如此），再在
        debug_log / latest_log 之间选实际存在的那个来读。

        B7：优先取 `launch_offsets`（自 launch 起）。`offsets` 已被改写成**最后一条用例**的
        窗口起点（每条用例开始时刷新），若沿用它，报告的关键标记摘要会只剩最后一条用例 —— 
        collect 是**整轮**的取证，必须看整轮。旧快照无该键时回落到 `offsets`。

        B8（t22）：窗口起点带**锚点**（`launch_anchors` / `anchors`），读取走
        `Read-MtLogWindow` —— 与断言层逐字节同一实现，跨零点日切时报告摘要不会读到
        「换了个文件」的错位区间。旧快照没有锚点 ⇒ 与既有实现逐字符同义。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][psobject]$Paths)

    $offset = 0
    $anchor = $null
    try {
        $snap = Get-MtReportSnapshot
        if ($snap.Contains('versions')) {
            $versions = $snap['versions']
            if ($null -ne $versions -and $versions.Contains($Paths.version)) {
                $entry = $versions[$Paths.version]
                $key = if ($entry.Contains('launch_offsets') -and $null -ne $entry['launch_offsets']) { 'launch_offsets' } else { 'offsets' }
                $akey = if ($key -eq 'launch_offsets') { 'launch_anchors' } else { 'anchors' }
                if ($entry.Contains($key) -and $null -ne $entry[$key] -and $entry[$key].Contains('debug.log')) {
                    $offset = [long]$entry[$key]['debug.log']
                    if ($entry.Contains($akey) -and $null -ne $entry[$akey] -and $entry[$akey].Contains('debug.log')) {
                        $anchor = $entry[$akey]['debug.log']
                    }
                }
            }
        }
    } catch {
        $offset = 0
        $anchor = $null
    }

    $log = if (Test-Path -LiteralPath $Paths.debug_log -PathType Leaf) { $Paths.debug_log } else { $Paths.latest_log }
    if (-not (Test-Path -LiteralPath $log -PathType Leaf)) { return , @('', [long]0, 'missing', '') }

    # 偏移与锚点都取自 `debug.log`：若实际读的是 latest.log（debug.log 缺失时的回落），锚点不适用
    # —— 传 $null 即保持既有语义（越界则整读当前文件），不要拿别的文件的身份去重拼。
    $anchorForRead = if ([System.IO.Path]::GetFileName($log) -eq 'debug.log') { $anchor } else { $null }
    $w = Read-MtLogWindow -Path $log -Offset $offset -Anchor $anchorForRead -LogsDir $Paths.logs_dir
    return , @([string]$w['Text'], [long]$w['Offset'], [string]$w['Kind'], [string]$w['Note'])
}

function Get-MtLogMarkers {
    <#
    .SYNOPSIS
        只列增量区间内的关键标记（不复用上一世代内容）；最多取最后 60 条。

    .NOTES
        返回 (命中行, 起始偏移, 窗口种类, 锚定说明)；后两项只用于把「窗口是怎么来的」写进报告。
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
    return , @($hits, $offset, [string]$delta[2], [string]$delta[3])
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

function Get-MtNoAiAudit {
    <#
    .SYNOPSIS
        收尾审计「生物 AI 已禁用」硬性要求（2026-09-18 用户裁决）：整轮 latest.log 的 AP_NOAI 心跳与正面对照行。

    .NOTES
        `mt_launch` 的硬闸门只在**刚进世界那 30 秒**取样 —— 那时世界通常是白天且刚清过场（mobs 常为 0），
        晚刷的生物漏过去就查不出来。这里改为对**整轮**做收尾审计：
          · 任一心跳出现 `mobs != noai` ⇒ 该半径内仍有带 AI 的 Mob，审计 FAIL；
          · 任何 `AP_NOAI:ERR:` ⇒ 脚本运行期出错，审计 FAIL；
          · 一行心跳都没有 ⇒ 脚本未同步/未生效，审计 FAIL；
          · `AP_NOAI_FORCED:` 行 = **正面对照证据**（真的停住过带 AI 的自然刷怪）。没有它只说明
            「本轮没遇到带 AI 的生物」，属 WARN，**不得**当成机制生效的证据。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][psobject]$Paths)

    $beats = @(); $forced = @(); $errs = @()
    $path = [string]$Paths.latest_log
    $text = ''
    if (Test-Path -LiteralPath $path -PathType Leaf) {
        try {
            $fs = [System.IO.File]::Open($path, [System.IO.FileMode]::Open,
                [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
            try {
                $sr = [System.IO.StreamReader]::new($fs, [System.Text.UTF8Encoding]::new($false, $false))
                $text = $sr.ReadToEnd()
                $sr.Dispose()
            } finally { $fs.Dispose() }
        } catch { $text = '' }
    }

    foreach ($ln in ($text -split "`r?`n")) {
        if ($ln -match 'AP_NOAI:ERR:') { $errs += $ln.Trim(); continue }
        $m = [regex]::Match($ln, 'AP_NOAI:mobs=(\d+):noai=(\d+):radius=(\d+):forced=(\d+):total=(\d+)')
        if ($m.Success) {
            $beats += [pscustomobject]@{
                mobs = [int]$m.Groups[1].Value; noai = [int]$m.Groups[2].Value
                forced = [int]$m.Groups[4].Value; total = [int]$m.Groups[5].Value
            }
            continue
        }
        $mf = [regex]::Match($ln, 'AP_NOAI_FORCED:new=(\d+):mobs=(\d+):noai=(\d+):total=(\d+)')
        if ($mf.Success) {
            $forced += [pscustomobject]@{
                new = [int]$mf.Groups[1].Value; mobs = [int]$mf.Groups[2].Value
                noai = [int]$mf.Groups[3].Value; total = [int]$mf.Groups[4].Value
            }
        }
    }
    $bad = @($beats | Where-Object { $_.mobs -ne $_.noai })
    $bad += @($forced | Where-Object { $_.mobs -ne $_.noai })
    return , @($beats, $forced, $errs, $bad)
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
    $markerWindow = [string]$markerPair[2]
    $markerNote = [string]$markerPair[3]

    $crashPair = Get-MtCrashSplit -Paths $p
    $baselineCrashes = @($crashPair[0]); $newCrashes = @($crashPair[1])

    $noaiPair = Get-MtNoAiAudit -Paths $p
    $noaiBeats = @($noaiPair[0]); $noaiForced = @($noaiPair[1])
    $noaiErrs = @($noaiPair[2]); $noaiBad = @($noaiPair[3])
    $noaiWithMobs = @($noaiBeats | Where-Object { $_.mobs -gt 0 })
    $noaiTotal = if ($noaiBeats.Count -gt 0) { [int]$noaiBeats[$noaiBeats.Count - 1].total } else { 0 }

    $phases = if ($vs.Contains('phases') -and $null -ne $vs['phases']) { $vs['phases'] } else { [ordered]@{} }
    $cases = if ($vs.Contains('cases') -and $null -ne $vs['cases']) { $vs['cases'] } else { [ordered]@{} }

    $verdict = $Verdict
    # 记下**调用方传入的** verdict（PowerShell 变量名不区分大小写 ⇒ `$verdict` 与形参 `$Verdict` 是同一个
    # 变量；下面一旦把 $verdict 覆写成 'INVALID'，形参里的原始值就没了 —— 标记行要报告的是"调用方要求什么"）。
    $verdictArg = [string]$Verdict
    # ── 规则 14（收严版，2026-09-19 用户裁决）：无效运行（第三类）────────────────────────────
    # 原文：「被用户主动终止的游戏进程一律视为作废。此次测试产生的任何结果都必须视为无效。
    #         需要重新执行测试进行验证。」
    # 因此 `USER_TERMINATED` **既不是产品 PASS 也不是产品 FAIL**：它必须被当**第三类**处置 ——
    # 整轮标「无效运行」、不计入通过率、不作为缺陷依据、不与 `❌ FAIL` 混淆；
    # 退出码沿用**既有**非 0 中断码 MT_EXIT_ERROR(2)（与 mt_case.ps1 用例级 `USER_TERMINATED`
    # 的 switch 分支同一数值，不新造数值）。
    # 判据只读**既有**状态源：本轮 `versions.<V>.cases.<条目>` 的结论串（`mark --case` 是本轮唯一
    # 写入口），外加防御性地看一眼阶段结论串（当前无写入方会把阶段记为 USER_TERMINATED，
    # 故对现有行为零影响；保留它是为「客户端在首个用例之前就被杀」这类将来场景兜底）。
    $userTerminatedCases = @($cases.Keys | Where-Object { [string]$cases[$_] -eq 'USER_TERMINATED' })
    $userTerminatedPhases = @($phases.Keys | Where-Object {
            $phases[$_] -and $phases[$_].Contains('result') -and [string]$phases[$_]['result'] -eq 'USER_TERMINATED'
        })
    $invalidRun = ($userTerminatedCases.Count -gt 0 -or $userTerminatedPhases.Count -gt 0)
    if ($invalidRun) {
        # 无效运行**优先于**调用方传入的 `--verdict`：编排侧（mt.ps1:690）只能把 cases 阶段退出码
        # 折成 PASS/TIMEOUT/FAIL 三种，无法表达「无效运行」这一第三类 ⇒ 报告层必须自己收口。
        $verdict = 'INVALID'
    } elseif (-not $verdict) {
        $allPass = ($cases.Count -gt 0)
        foreach ($k in $cases.Keys) { if ($cases[$k] -ne 'PASS') { $allPass = $false } }
        $verdict = if ($allPass) { 'PASS' } else { 'FAIL' }
    }

    # t22（B9）：本轮世代与归档的历史轮次（历史只作审计，**不进判定**）
    $roundGen = if ($state.Contains('gen')) { [string]$state['gen'] } else { '（旧口径，未标世代）' }
    $roundHist = @()
    if ($state.Contains('history') -and $null -ne $state['history']) { $roundHist = @($state['history']) }
    $roundHistMarks = 0
    foreach ($h in $roundHist) { $roundHistMarks += (Get-MtRoundMarkCount -Versions $h['versions']) }

    $lines = @(
        "# 自动化测试报告 — $Version",
        '',
        "- **运行**: ``$runId``",
        "- **本轮世代**: ``$roundGen``（条目聚合只统计本轮标记）",
        "- **历史轮次（不计入判定）**: 已归档 $($roundHist.Count) 个 / $roundHistMarks 条标记",
        "- **版本/加载器**: $Version / $($p.loader)（子项目 ``$($p.subproject)``）",
        "- **分支**: $(Get-MtReportBranchName)",
        "- **环境**: ``$($p.run_dir)``（dev 本体，quickplay=$([System.IO.Path]::GetFileName($p.client_world))）",
        "- **生成时间**: $(Get-MtNowStamp)",
        '',
        '## 结论',
        '',
        "**$Version`: $(if ($verdict -eq 'INVALID') { '⛔ 无效运行（MT_USER_TERMINATED）' } elseif ($verdict -eq 'PASS') { '✅ PASS（通过）' } else { '❌ FAIL（未通过）' })**",
        ''
    )

    # 规则 14：无效运行必须**显式**写明「结果全部无效、需重新执行」，并说明不并入 ❌ / 不计通过率 /
    # 不作缺陷依据；条目表（下面那节）仍逐条保留全部结果串供审计。
    if ($invalidRun) {
        $utCaseNames = if ($userTerminatedCases.Count -gt 0) { @($userTerminatedCases | Sort-Object) -join ', ' } else { '（阶段级：' + (@($userTerminatedPhases | Sort-Object) -join ', ') + '）' }
        $lines += @(
            '> ### ⛔ 无效运行（MT_USER_TERMINATED）',
            '> **本次测试结果全部无效，需重新执行测试验证。**',
            '> 依据（规则 14 收严版）：「被用户主动终止的游戏进程一律视为作废。此次测试产生的任何结果都必须视为无效。需要重新执行测试进行验证。」',
            "> 触发条目：``$utCaseNames``（结论 ``USER_TERMINATED`` —— 疑似用户手动终止，判定见 ``mt_case.ps1`` 的 ``MT_USER_TERMINATED``）。",
            '> 处置：**不计入通过率**、**不作为缺陷依据**，也不与产品 `❌ FAIL` 混淆；下方条目表逐条如实保留全部结果串（含其它失败串）供审计。',
            "> 退出码：沿用既有非 0 中断码 ``MT_EXIT_ERROR($MT_EXIT_ERROR)``（与用例级 ``USER_TERMINATED`` 同一数值，未新造）。",
            ''
        )
    }

    $lines += @(
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
        "（区间：$([System.IO.Path]::GetFileName($p.debug_log))@${offset}B 之后，即本次运行新增部分；窗口 $markerWindow）", '', '```')
    $lines += if ($markers.Count -gt 0) { $markers } else { @('（无）') }
    $lines += @('```', '')
    if ($markerNote) { $lines += @("- **窗口锚定**: $markerNote", '') }
    $lines += @('## 崩溃 / 异常', '')
    if ($newCrashes.Count -gt 0) {
        $lines += "- **本次新增崩溃报告 $($newCrashes.Count) 个**：$($newCrashes -join ', ')"
    } else {
        $lines += '- 本次新增崩溃报告: 无'
    }
    if ($baselineCrashes.Count -gt 0) {
        $lines += "- 基线（本次运行之前已存在，不计入判定）$($baselineCrashes.Count) 个：$($baselineCrashes -join ', ')"
    }
    $lines += "- kubejs server.log: $(if (Test-Path -LiteralPath $p.kubejs_log -PathType Leaf) { '存在' } else { '缺失' })"

    $lines += @('', '## 生物 AI 禁用审计（测试环境硬性要求）', '')
    if ($noaiBeats.Count -eq 0) {
        $lines += '- ❌ **未读到任何 `AP_NOAI:` 心跳** —— 脚本未同步 / 未生效（`mt_launch` 的进入世界闸门本应已拦下）'
    } else {
        $lines += "- 心跳 $($noaiBeats.Count) 行；其中 ``mobs>0`` 的 $($noaiWithMobs.Count) 行（半径内确实有生物）"
        if ($noaiForced.Count -gt 0) {
            $lines += "- **正面对照 ✅**：实际停住过带 AI 的自然刷怪 $($noaiForced.Count) 次，累计 ``total=$noaiTotal``"
        } else {
            $lines += '- **正面对照 ⚠️ 缺失**：本轮读数里 ``mobs`` 全为 0，未遇到带 AI 的生物 —— 只能证明「现场干净」，**不能**当作机制生效的证据'
        }
        $lines += "- 心跳/对照行中 ``mobs != noai`` 的次数：$($noaiBad.Count)$(if ($noaiBad.Count -gt 0) { ' ❌（该半径内仍有带 AI 的 Mob）' } else { ' ✅' })"
        if ($noaiErrs.Count -gt 0) {
            $lines += "- ❌ ``AP_NOAI:ERR`` 报错 $($noaiErrs.Count) 行，最后一行：``$($noaiErrs[$noaiErrs.Count - 1])``"
        }
    }

    $lines += @('', '## 证据', '')
    $lines += if ($copied.Count -gt 0) { @($copied | ForEach-Object { "- ``$_``" }) } else { @('- （无）') }

    $lines += @('', '## 复跑命令', '', '```bash')
    # 文案偏差：入口已由 mt.sh 换成 mt.ps1（同一入口的新名字）
    $lines += "pwsh -File scripts/test/mt.ps1 --version $Version --case cases/<条目>.json"
    $lines += '```'

    $reportPath = Join-Path $dest 'report.md'
    [System.IO.File]::WriteAllText($reportPath, (($lines -join "`n") + "`n"), $script:TAG_UTF8)
    Write-MtLine "MT_REPORT: OK — $reportPath（结论 $verdict）"

    # 规则 14：无效运行在此**提前收口**（早于生物 AI 审计的 ERROR 归因）——否则用户杀进程导致的
    # 心跳缺失会被误读成「读数被生物 AI 污染」（错误归因，且与「不作缺陷依据」冲突）。
    # 退出码沿用既有 MT_EXIT_ERROR(2)；审计结论一并作废并显式标注。
    if ($invalidRun) {
        Write-MtLine ("MT_INVALID_RUN: version={0} result=INVALID reason=USER_TERMINATED cases={1} verdict_arg={2} exit={3}" -f `
                $Version, $utCaseNames, $(if ($verdictArg) { $verdictArg } else { '(none)' }), $MT_EXIT_ERROR)
        Write-MtErrLine ('MT_INVALID_RUN: 本次测试结果全部无效，需重新执行测试验证 —— 无效运行（MT_USER_TERMINATED）：' + `
                "不计入通过率、不作为缺陷依据、不与产品 FAIL 混淆（version=$Version）")
        Write-MtLine "MT_NOAI_AUDIT: SKIPPED — 本轮为无效运行（MT_USER_TERMINATED）；生物 AI 审计结论一并作废，不作为缺陷依据"
        return $MT_EXIT_ERROR
    }

    # 生物 AI 禁用审计：整轮都不能出现带 AI 的 Mob（2026-09-18 硬性要求），未达标一律 ERROR。
    # 报告已先落盘（证据保留），随后以非 0 退出码把这一轮判为不合格。
    if ($noaiBeats.Count -eq 0 -or $noaiBad.Count -gt 0 -or $noaiErrs.Count -gt 0) {
        Write-MtErrorLine ("MT_REPORT: ERROR — 生物 AI 禁用审计未通过（测试环境硬性要求）：心跳 {0} 行 / 失配 {1} 次 / AP_NOAI:ERR {2} 行；本轮读数可能已被生物 AI 污染" -f $noaiBeats.Count, $noaiBad.Count, $noaiErrs.Count)
        return $MT_EXIT_ERROR
    }
    if ($noaiForced.Count -gt 0) {
        Write-MtLine "MT_NOAI_AUDIT: PASS — heartbeats=$($noaiBeats.Count) withMobs=$($noaiWithMobs.Count) mismatch=0 正面对照=forced $($noaiForced.Count) 次（total=$noaiTotal）"
    } else {
        Write-MtWarn "MT_NOAI_AUDIT: PASS(无正面对照) — heartbeats=$($noaiBeats.Count) mismatch=0，但本轮 mobs 全为 0：只证明现场干净，未实测触发过强制逻辑"
    }
    return 0
}

function Invoke-MtReportSummary {
    [CmdletBinding()]
    param()

    $runId = Get-MtActiveRunId
    $state = Get-MtReportState
    $dest = Join-Path (Join-Path $script:TestDir 'reports') $runId
    if (-not (Test-Path -LiteralPath $dest)) { [void](New-Item -ItemType Directory -Force -Path $dest) }

    # t22（B9）：本轮世代与归档的历史轮次 —— 历史轮次只作审计，**不进判定**（否则故意造的负例
    # 与上一轮的旧标记会让「本次全 PASS」的跑批显示 ❌、退出码非 0；实测 2026-09-18 t17）。
    $roundGen = if ($state.Contains('gen')) { [string]$state['gen'] } else { '（旧口径，未标世代）' }
    $roundHist = @()
    # ⚠️ 不写 `$roundHist = if (…) { @(…) } else { @() }`：if-表达式的输出走管道，单元素数组会被拆成
    #     那一个元素（`history` 只 1 条时 `$roundHist` 变成那个哈希表，`.Count` 成了它的键数）。
    if ($state.Contains('history') -and $null -ne $state['history']) { $roundHist = @($state['history']) }
    $roundHistMarks = 0
    foreach ($h in $roundHist) { $roundHistMarks += (Get-MtRoundMarkCount -Versions $h['versions']) }
    $roundHistDetail = ''
    if ($roundHist.Count -gt 0) {
        $parts = @()
        foreach ($h in $roundHist) {
            $n = Get-MtRoundMarkCount -Versions $h['versions']
            $g = if ([string]$h['gen']) { [string]$h['gen'] } else { '（未标世代）' }
            $parts += ("{0}={1}" -f $g, $n)
        }
        $roundHistDetail = $parts -join ', '
    }

    $lines = @(
        '# 自动化测试总览',
        '',
        "- **运行**: ``$runId``",
        "- **本轮世代**: ``$roundGen``（**只统计本轮标记**）",
        "- **历史轮次（不计入判定）**: 已归档 $($roundHist.Count) 个 / $roundHistMarks 条标记$(if ($roundHistDetail) { "：$roundHistDetail" })",
        "- **分支**: $(Get-MtReportBranchName)",
        "- **生成时间**: $(Get-MtNowStamp)",
        '',
        '> 测试顺序：先 1.21.1，通过后才执行 1.20.1。两个版本都给出独立结论。',
        '> `mt.ps1 --version <V>` 指定单版本时只跑该版本、不做跨版本门控，总览也只列实际执行过的版本。',
        '> 条目判定口径（规则 14 收严版）：`PASS`/`SKIP` 之外（FAIL/TIMEOUT/BLOCKED/ERROR/GATED）一律计入 ❌ 并让退出码非 0；',
        '> 但 `USER_TERMINATED` 是**第三类 —— 无效运行**：该次运行的全部结果无效（不计入通过率、不作为缺陷依据），不与 ❌ FAIL 混淆，退出码沿用既有中断码 MT_EXIT_ERROR(2)。',
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
    # 规则 14（收严版）：只要**任一**版本本轮出现 `USER_TERMINATED`，整轮即为**无效运行**
    # （$overallInvalid）—— 该次运行的全部结果无效，不计通过率、不作缺陷依据。
    # `$anyRealFailure` 只用于在综合结论里如实提示「无效运行之外还并存真实失败串」，不改变判定级别。
    $overallInvalid = $false
    $invalidVersions = @()
    $anyRealFailure = $false
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
            $ut = @()
            foreach ($k in $cases.Keys) {
                $res = [string]$cases[$k]
                if (@('PASS', 'SKIP') -notcontains $res) {
                    $bad += ("{0}={1}" -f $k, $res)
                    if ($res -eq 'TIMEOUT') { $timeouts += $k }
                    if ($res -eq 'USER_TERMINATED') { $ut += $k }
                }
            }
            if ($ut.Count -gt 0) {
                # 规则 14：本版本本次运行＝**无效运行**（第三类）。条目表**如实并列全部结果串**
                # （$bad 里既有 USER_TERMINATED 也可能有其它失败串），既不判 PASS 也不判产品 FAIL。
                $verdict = "⛔ 无效运行（MT_USER_TERMINATED：$($bad -join ', ')）"
                $overallInvalid = $true
                $invalidVersions += $v
                if (@($bad | Where-Object { $_ -notlike '*=USER_TERMINATED' }).Count -gt 0) { $anyRealFailure = $true }
            } elseif ($bad.Count -eq 0) {
                $verdict = '✅ PASS'
            } elseif ($timeouts.Count -gt 0 -and $timeouts.Count -eq $bad.Count) {
                $verdict = "⏱ TIMEOUT（$($bad -join ', ')）"
            } else {
                $verdict = "❌ FAIL（$($bad -join ', ')）"
            }
            if ($bad.Count -gt 0 -and $ut.Count -eq 0) { $overallPass = $false }
        }
        $lines += "| $idx | $v | $verdict | $($cases.Count) |"
    }

    $lines += @('', '## 综合结论', '')
    if ($overallInvalid) {
        $lines += '**⛔ 无效运行（MT_USER_TERMINATED）—— 本次测试结果全部无效，需重新执行测试验证**'
        $lines += ''
        $lines += "> 涉及版本：$($invalidVersions -join ', ')。依据（规则 14 收严版）：「被用户主动终止的游戏进程一律视为作废。此次测试产生的任何结果都必须视为无效。需要重新执行测试进行验证。」"
        $lines += '> **不计入通过率**、**不作为缺陷依据**、不与产品 `❌ FAIL` 混淆；各版本 `report.md` 的条目表已逐条如实保留全部结果串（含其它失败串）供重新执行时对照。'
        if ($anyRealFailure) {
            $lines += '> 注：本轮**另有真实失败串**（见上方版本结论与各版本 `report.md` 条目表）——整轮仍按无效运行处置；这些失败项待重新执行后复验，**当前不作为缺陷依据**。'
        }
    } else {
        $lines += "**$(if ($overallPass) { '✅ 全部通过' } else { '❌ 未全部通过' })**"
    }
    $lines += @('', '各版本详细报告见对应子目录 `report.md`。')

    $summaryPath = Join-Path $dest 'SUMMARY.md'
    [System.IO.File]::WriteAllText($summaryPath, (($lines -join "`n") + "`n"), $script:TAG_UTF8)
    Write-MtLine "MT_REPORT: OK — $summaryPath"
    Write-MtLine ("MT_ROUND: 本轮 {0} 结算（{1}）；汇总只统计本轮标记" -f `
            $roundGen, $(if ($overallInvalid) { '无效运行（MT_USER_TERMINATED）' } elseif ($overallPass) { '全部通过' } else { '未全部通过' }))
    if ($overallInvalid) {
        Write-MtLine ("MT_INVALID_RUN: 本轮 result=INVALID reason=USER_TERMINATED versions={0} exit={1}" -f ($invalidVersions -join ','), $MT_EXIT_ERROR)
        Write-MtErrLine "MT_INVALID_RUN: 本次测试结果全部无效，需重新执行测试验证 —— 无效运行（MT_USER_TERMINATED）：不计入通过率、不作为缺陷依据、不与产品 FAIL 混淆"
    }
    # t22（B9）：结算本轮 —— 下一条标记（任何阶段/条目）由此判定为「新一轮」的起点。
    # 放在**判定之后**：即使这里写盘失败，也已经返回了正确的退出码（不因状态文件影响结论）。
    $state['closed_at'] = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    try { Save-MtReportState -State $state } catch { Write-MtWarn "MT_ROUND: 结算标记写盘失败（$($_.Exception.Message)）；下一轮仍会按世代/结算判据开新轮" }
    # 规则 14：无效运行返回**既有**非 0 中断码 MT_EXIT_ERROR(2)（与用例级 USER_TERMINATED 一致），
    # 不与「未全部通过」的既有返回码 1 混同。
    if ($overallInvalid) { return $MT_EXIT_ERROR }
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
