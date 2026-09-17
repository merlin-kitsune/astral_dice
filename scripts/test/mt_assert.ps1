#Requires -Version 7.0
<#
.SYNOPSIS
    mt_assert — 断言引擎（阶段 C 的判定臂）。

.DESCRIPTION
    判定原则：所有日志断言只针对**窗口起点之后的增量区间**求值。
    旧流程是对整个 latest.log 做 grep，历史运行留下的同名标记会让断言假通过；
    本模块用字节游标把断言锚定到本次运行产生的日志，使结论可复算。

    ## B7：断言窗口 = 「自本用例起」（不再是「自 launch 起」）

    `offsets` 的语义已改为**当前窗口起点**，由两类调用方写入：

      · `mt_launch.ps1` 收尾  → `snapshot --window launch`：写 `offsets` **并冻结** `launch_offsets`；
      · `mt_case.ps1` 每条用例开始 → `snapshot --window case`：只改写 `offsets`（+ crash 基线），
        **不动** `launch_offsets`。

    于是 `log` / `absent` 默认只看「本用例开始之后」的日志（B6 已实证：共用 launch 窗口会让
    不带 tag 唯一标识的标记被前序用例"喂饱"⇒ 假 PASS，实测 `APDUMP|LOCKRAW|` 命中数随用例递增
    14→28→…→126）。需要看**整轮**的断言（Mixin 应用、渲染栈加载等启动期事实）显式声明窗口：

      --window case    自本用例起（默认；`offsets`）
      --window launch  自 launch 起（`launch_offsets`；缺失时退化为 `offsets` 并告警）
      --window whole   整文件（等价旧 `--no-snapshot`）
      --no-snapshot    仅为兼容保留 = --window whole

    `mixin` 子命令的默认窗口是 **launch**（Mixin 应用发生在启动期；若跟随 case 窗口会退化成
    「恒真」，属于弱化断言 —— 故保持既有语义不变）。

    子命令:
      snapshot --version V [--window launch|case]
                                          记录窗口起点（日志字节游标 / crash 基线 / run id）
      log      --version V --pattern RE [--window case|launch|whole]
                                          窗口内是否存在标记
      absent   --version V --pattern RE [--window case|launch|whole]
                                          窗口内不得出现某标记
      crash    --version V                窗口内无崩溃报告
      kubejs   --version V                KubeJS server.log 为 0 errors
      mixin    --version V [--window ...] 窗口内无 Mixin 应用失败（默认 launch）

    ## B8（t22）：窗口起点是**锚定的**，跨 log4j 零点日切仍然有效

    `offsets` 只记「文件少了多少字节」是不够的：`logs/latest.log` 的 log4j filePattern 带日期
    （`logs/%d{yyyy-MM-dd}-%i.log.gz`）⇒ **跨零点会把活动文件日切**（更名 + 新建一个更小的
    latest.log）。此时旧偏移在新文件上越界，旧实现 `if ($Offset -gt $size) { $Offset = 0 }`
    **静默**改成「整读新文件」：窗口前半段（用例开始 → 零点）整段丢失 ⇒ `log` 假 FAIL、
    `absent` 漏判（假 PASS）；偏移未越界时更糟 —— 会拿**另一个文件**的同一字节号切片。

    现在 `snapshot` 除了 `offsets` 还写 `anchors`（键 = 日志文件名，值 = 窗口起点的**文件身份**：
    `ctime_ms` + 头部 64 KiB 的 `prefix_sha` + `len`/`mtime_ms`），`launch` 窗口对应
    `launch_anchors`（与 `launch_offsets` 同样只在 launch 时冻结、case 快照不动它）。
    读取时 `Read-MtLogWindow` 按身份把窗口跨轮转拼回来：

      · 同一文件（创建时间 + 头部指纹相符）→ 与旧实现**逐字节同义**；
      · 锚点文件已日切 → 只从**身份匹配**的那一段的同一偏移处接着拼，起点之前的内容绝不并入
        （并入 = 窗口前移 = 跨用例串读）；
      · 锚点文件已不在（重登/重启删除了 latest.log）→ **不猜**：退回既有语义并 WARN 报出；
      · 无锚点的旧快照 → 与旧实现逐字节同义（向后兼容）。

    通道差异：latest 家族的日切由该机制覆盖；`debug.log` 的 filePattern 是 `debug-%i`（**无日期**），
    只在客户端**启动时**轮转、不跨零点日切 —— 故 1.20.1 侧（模组清单读 debug.log）不存在同类问题，
    重启后 debug.log 的窗口起点本就该是「新文件的开头」，无需也不应跨该轮转拼接。

.NOTES
    迁移前源文件 scripts/test/mt_assert.py（该原件已在 92fbeaf「工具链收敛为纯 pwsh」删除，取回：`git show 92fbeaf^:scripts/test/mt_assert.py`）。

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
# t22：窗口的跨轮转重建（`Read-MtLogWindow` / `Get-MtLogAnchor`）与轮转识别口径同源实现放在 Mt.Proc。
Import-Module (Join-Path $script:LibDir 'Mt.Proc.psm1')

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
# loadergate —— **纯离线**用例（LOADER-GATE-FORGE）的读数通道：由
#    scripts/test/mt_loadergate.ps1 直接写 run/<版本>/logs/loadergate.log，
#    不经过游戏客户端（该用例不需要 runClient）。断言一律配 scope=whole。
$script:LogSources = [ordered]@{ 'latest' = 'latest_log'; 'debug' = 'debug_log'; 'loadergate' = 'loadergate_log' }

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

# ── B7 窗口选择 ───────────────────────────────────────────────────────────
function Get-MtWindowTag {
    <#
    .SYNOPSIS
        窗口标签后缀：只在**非普通情形**（跨轮转重拼 / 窗口不可重建）时附加到断言标签上。

    .NOTES
        普通情形（`intact` / `from-zero` / `legacy`）保持旧标签形状不变；`reassembled+1`、
        `unreconstructable` 必须能从断言输出里一眼看见 —— 否则「跨零点时窗口被重拼过」这件事
        在取证文本里就消失了。
    #>
    [CmdletBinding()]
    param([string]$Info)

    if ($Info -like 'reassembled*' -or $Info -eq 'unreconstructable') { return "; $Info" }
    return ''
}

function Get-MtWindowOffset {
    <#
    .SYNOPSIS
        按窗口种类取某日志文件的起始字节偏移**与该起点的文件锚点**。

    .NOTES
        case   → `offsets` + `anchors`（自本用例起，B7 的默认语义）
        launch → `launch_offsets` + `launch_anchors`（自 launch 起；旧快照没有该键时**退化并告警**，
                 绝不静默把 launch 断言变成 case 断言 —— 那会凭空制造假 FAIL）
        whole  → 0（整文件；无锚点）

        返回 (偏移, 实际生效的窗口名, 退化告警文本或空串, 锚点或 $null)。
        锚点（t22/B8）与偏移**成对**返回：没有锚点的偏移在跨零点日切后无法重建窗口，
        调用方必须把它一路传给 `Read-MtLogWindow`（不要再自己拼路径去读）。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]$Entry,
        [Parameter(Mandatory)][string]$LogName,
        [Parameter(Mandatory)][string]$Window
    )

    if ($Window -eq 'whole') { return , @([long]0, 'whole', '', $null) }

    $key = if ($Window -eq 'launch') { 'launch_offsets' } else { 'offsets' }
    $akey = if ($Window -eq 'launch') { 'launch_anchors' } else { 'anchors' }
    $degraded = ''
    if (-not $Entry.Contains($key) -or $null -eq $Entry[$key]) {
        if ($Window -eq 'launch' -and $Entry.Contains('offsets')) {
            $key = 'offsets'
            $akey = 'anchors'
            $degraded = 'launch_offsets 缺失（快照早于 B7 / 本条走的是 --phase cases 单步路线）⇒ 退化为当前窗口'
        } else {
            return , @([long]0, $Window, '', $null)
        }
    }

    $anchor = $null
    if ($Entry.Contains($akey) -and $null -ne $Entry[$akey] -and $Entry[$akey].Contains($LogName)) {
        $anchor = $Entry[$akey][$LogName]
    }

    $offsets = $Entry[$key]
    if ($offsets.Contains($LogName)) { return , @([long]$offsets[$LogName], $Window, $degraded, $anchor) }
    return , @([long]0, $Window, $degraded, $anchor)
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
        读取窗口起点之后的新增内容；窗口起点由**锚点**定位，跨 log4j 零点日切仍有效（t22/B8）。

    .NOTES
        实现落在 `lib/Mt.Proc.psm1` 的 `Read-MtLogWindow`（与 t20 的轮转识别口径同源：同一套
        latest 家族枚举 `Get-MtRotatedLatestLogs`）。本函数只负责：
          · 把锚点一路传下去；
          · 把「窗口是怎么来的」如实打出来（`reassembled` 出信息行、`unreconstructable` 出 WARN）
            —— 判据本身一字未改，也不存在「读不到就跳过断言」的分支。

        返回 (文本, 窗口种类标签, 实际起点)。**不再自己 Seek**：旧实现（按字节偏移直接 Seek
        当前文件）在日切后越界时会静默改成整读新文件，窗口前半段丢失 ⇒ 假 FAIL / 漏判。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$LogPath,
        [long]$Offset = 0,
        $Anchor = $null
    )

    $logsDir = [System.IO.Path]::GetDirectoryName($LogPath)
    $w = Read-MtLogWindow -Path $LogPath -Offset $Offset -Anchor $Anchor -LogsDir $logsDir
    $kind = [string]$w['Kind']
    $info = if ([int]$w['Rotated'] -gt 0) { "$kind+$($w['Rotated'])" } else { $kind }
    if ($kind -eq 'reassembled') {
        Write-MtLine ("MT_ASSERT_WINDOW: {0}" -f [string]$w['Note'])
    } elseif ($kind -eq 'unreconstructable') {
        Write-MtWarn ("MT_ASSERT_WINDOW: WARN — {0}" -f [string]$w['Note'])
    }
    return , @([string]$w['Text'], $info, [long]$w['Offset'])
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
    <#
    .SYNOPSIS
        写窗口起点。`--window launch`（launch 阶段收尾）额外把同一组偏移冻结成 `launch_offsets`；
        `--window case`（每条用例开始）只改写 `offsets`，**保留** `launch_offsets`。

    .NOTES
        B7：为什么必须分两个键 —— 断言窗口收窄到「自本用例起」之后，`mixin` 子命令与
        `mt_report` 的增量摘要仍需要「自 launch 起」的偏移。若只有一个 `offsets`，
        每次用例刷新都会把 launch 基线冲掉 ⇒ 启动期断言退化成恒真（弱化）。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Version,
        [string]$Window = 'launch'
    )

    $p = Get-MtPaths -Version $Version
    $runId = Get-MtActiveRunId
    $snap = Get-MtSnapshot
    $sameRun = $snap.Contains('run_id') -and $snap['run_id'] -eq $runId
    if (-not $sameRun) {
        $snap = [ordered]@{ run_id = $runId; versions = [ordered]@{} }
    }
    if (-not $snap.Contains('versions') -or $null -eq $snap['versions']) {
        $snap['versions'] = [ordered]@{}
    }

    $offsets = [ordered]@{}
    $anchors = [ordered]@{}
    foreach ($log in @($p.latest_log, $p.debug_log, $p.kubejs_log, $p.probe_log)) {
        $name = [System.IO.Path]::GetFileName($log)
        $size = 0
        try {
            if (Test-Path -LiteralPath $log -PathType Leaf) { $size = (Get-Item -LiteralPath $log).Length }
        } catch { $size = 0 }
        $offsets[$name] = [long]$size
        # t22（B8）：窗口起点必须同时记住**是哪个文件**。日切后「偏移越界」才有证据可用来重建窗口
        # （见 Read-MtLogWindow 的 NOTES）；只记字节数时无法区分「日切」与「文件被换掉」。
        $anchors[$name] = Get-MtLogAnchor -Path $log
    }

    $crashNames = @()
    if (Test-Path -LiteralPath $p.crash_dir -PathType Container) {
        $crashNames = @(Get-ChildItem -LiteralPath $p.crash_dir -File -Filter '*.txt' |
            ForEach-Object { $_.Name } | Sort-Object)
    }

    $prev = if ($sameRun -and $snap['versions'].Contains($Version)) { $snap['versions'][$Version] } else { $null }

    # launch 基线：launch 窗口写死；case 窗口沿用上一份（没有则退化为当前偏移并告警）
    # t22：`launch_anchors` 与 `launch_offsets` 严格同源同步 —— 两把钥匙少一把，launch 窗口就退化了。
    $launchOffsets = $null
    $launchAnchors = $null
    $launchWarn = ''
    if ($Window -ne 'case') {
        $launchOffsets = $offsets
        $launchAnchors = $anchors
    } elseif ($null -ne $prev -and $prev.Contains('launch_offsets') -and $null -ne $prev['launch_offsets']) {
        $launchOffsets = $prev['launch_offsets']
        $launchAnchors = if ($prev.Contains('launch_anchors')) { $prev['launch_anchors'] } else { $null }
    } else {
        $launchOffsets = $offsets
        $launchAnchors = $anchors
        $launchWarn = 'WARN — 无 launch 基线（--phase cases 单步路线 / 快照早于 B7），launch 窗口退化为当前起点'
    }

    $entry = [ordered]@{
        offsets        = $offsets
        launch_offsets = $launchOffsets
        anchors        = $anchors
        launch_anchors = $launchAnchors
        window         = $Window
        ts             = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() / 1000.0
        crash_baseline = @($crashNames)
        mixin_errors   = @()
    }
    $snap['versions'][$Version] = $entry

    Save-MtSnapshot -Snapshot $snap

    $latest = if ($offsets.Contains('latest.log')) { $offsets['latest.log'] } else { 0 }
    $debug = if ($offsets.Contains('debug.log')) { $offsets['debug.log'] } else { 0 }
    $probe = if ($offsets.Contains('astral_probe.log')) { $offsets['astral_probe.log'] } else { 0 }
    $anchorCount = @($anchors.Keys | Where-Object { $null -ne $anchors[$_] }).Count
    Write-MtLine "MT_SNAPSHOT: OK — $Version run=$runId window=$Window latest=${latest}B debug=${debug}B probe=${probe}B anchors=$anchorCount"
    if ($launchWarn) { Write-MtWarn "MT_SNAPSHOT: $launchWarn" }
    return 0
}

function Invoke-MtAssertLog {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Version,
        [Parameter(Mandatory)][string]$PatternText,
        [Parameter(Mandatory)][string]$Source,
        [string]$Window = 'case'
    )

    $p = Get-MtPaths -Version $Version
    if ($Window -eq 'whole') {
        Write-MtWarn '窗口 = whole：对全文件求值（可能被历史标记污染）'
    }
    $log = Get-MtLogPath -Paths $p -Source $Source
    if (Test-MtLogMissing -Source $Source -LogPath $log) { return 2 }

    $entry = Get-MtSnapFor -Version $Version
    $name = [System.IO.Path]::GetFileName($log)
    $wo = Get-MtWindowOffset -Entry $entry -LogName $name -Window $Window
    $offset = [long]$wo[0]
    $winUsed = [string]$wo[1]
    if ([string]$wo[2]) { Write-MtWarn ("MT_ASSERT_LOG: {0}" -f [string]$wo[2]) }

    $delta = Read-MtLogDelta -LogPath $log -Offset $offset -Anchor $wo[3]
    $text = [string]$delta[0]
    $winInfo = [string]$delta[1]
    $offsetUsed = [long]$delta[2]
    $pat = New-MtRegex -Pattern $PatternText
    $hits = $pat.Matches($text).Count
    $label = "$Source`:$name@${offsetUsed}B(win=$winUsed$(Get-MtWindowTag -Info $winInfo))"

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
        反向断言：窗口内不得出现某标记（用于「旧机制无残留」类 TC）。

    .NOTES
        通道缺失时返回 ERROR 而不是 PASS —— 否则「文件不存在」会被静默判成「标记未出现」，
        把环境故障伪装成通过。

        B7 窗口语义：默认 `case` = **本用例窗口内**未出现。窗口只是"变短"，判据本身没变
        （仍然必须在窗口内**实际读到过日志文本**且未命中）：起始偏移由本用例开始时写入，
        覆盖本用例自身的全部动作，故不会退化成「永远成立」。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Version,
        [Parameter(Mandatory)][string]$PatternText,
        [Parameter(Mandatory)][string]$Source,
        [string]$Window = 'case'
    )

    $p = Get-MtPaths -Version $Version
    $log = Get-MtLogPath -Paths $p -Source $Source
    if (Test-MtLogMissing -Source $Source -LogPath $log) { return 2 }

    $entry = Get-MtSnapFor -Version $Version
    $name = [System.IO.Path]::GetFileName($log)
    $wo = Get-MtWindowOffset -Entry $entry -LogName $name -Window $Window
    $offset = [long]$wo[0]
    $winUsed = [string]$wo[1]
    if ([string]$wo[2]) { Write-MtWarn ("MT_ASSERT_ABSENT: {0}" -f [string]$wo[2]) }

    $delta = Read-MtLogDelta -LogPath $log -Offset $offset -Anchor $wo[3]
    $text = [string]$delta[0]
    $winInfo = [string]$delta[1]
    $offsetUsed = [long]$delta[2]
    $pat = New-MtRegex -Pattern $PatternText
    $label = "$Source`:$name@${offsetUsed}B(win=$winUsed$(Get-MtWindowTag -Info $winInfo))"

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
        窗口内不得出现 Mixin 应用失败（渲染栈兼容性的硬信号）。

    .NOTES
        B7：默认窗口是 **launch**（不是 case）。Mixin 应用发生在启动期，若跟随「自本用例起」
        的窗口，对绝大多数用例都会变成「窗口内无 Mixin 失败」= 恒真 ⇒ **弱化断言**。
        故这里沿用旧语义（自 launch 起读 debug.log 增量），由 `launch_offsets` 承载。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Version, [string]$Window = 'launch')

    $p = Get-MtPaths -Version $Version
    $log = if (Test-Path -LiteralPath $p.debug_log -PathType Leaf) { $p.debug_log } else { $p.latest_log }
    $entry = Get-MtSnapFor -Version $Version
    $name = [System.IO.Path]::GetFileName($log)
    $wo = Get-MtWindowOffset -Entry $entry -LogName $name -Window $Window
    $offset = [long]$wo[0]
    $winUsed = [string]$wo[1]
    if ([string]$wo[2]) { Write-MtWarn ("MT_ASSERT_MIXIN: {0}" -f [string]$wo[2]) }

    $delta = Read-MtLogDelta -LogPath $log -Offset $offset -Anchor $wo[3]
    $text = [string]$delta[0]
    $winInfo = [string]$delta[1]
    $offsetUsed = [long]$delta[2]
    $pat = [regex]::new('Mixin apply failed|Mixin apply error|Failed to apply mixin')
    if ($pat.IsMatch($text)) {
        Write-MtLine 'MT_ASSERT_MIXIN: FAIL — 检测到 Mixin 应用失败（兼容模组栈异常）'
        return 1
    }
    Write-MtLine "MT_ASSERT_MIXIN: PASS — $name@${offsetUsed}B(win=$winUsed$(Get-MtWindowTag -Info $winInfo)) 窗口内无 Mixin 失败"
    return 0
}

# ── 入口 ══════════════════════════════════════════════════════════════════
if ($MyInvocation.InvocationName -ne '.') {

    $Cmd = ''
    $Version = ''
    $PatternText = ''
    $Source = 'latest'
    $Window = ''            # '' = 按子命令取默认（log/absent=case，mixin=launch）

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
        } elseif ($key -eq 'window') {
            if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --window 的值'; exit $MT_EXIT_ERROR }
            $Window = ([string]$args[$i + 1]).ToLowerInvariant(); $i += 2
        } elseif ($key -eq 'since-snapshot') {
            $Window = 'case'; $i++
        } elseif ($key -eq 'no-snapshot') {
            $Window = 'whole'; $i++
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
    if ($Window -and (@('case', 'launch', 'whole') -notcontains $Window)) {
        Write-MtErrorLine "--window 只接受 case / launch / whole（收到 $Window）"
        exit $MT_EXIT_ERROR
    }

    switch ($Cmd) {
        'snapshot' { exit (Invoke-MtAssertSnapshot -Version $Version -Window $(if ($Window) { $Window } else { 'launch' })) }
        'log' { exit (Invoke-MtAssertLog -Version $Version -PatternText $PatternText -Source $Source -Window $(if ($Window) { $Window } else { 'case' })) }
        'absent' { exit (Invoke-MtAssertAbsent -Version $Version -PatternText $PatternText -Source $Source -Window $(if ($Window) { $Window } else { 'case' })) }
        'crash' { exit (Invoke-MtAssertCrash -Version $Version) }
        'kubejs' { exit (Invoke-MtAssertKubejs -Version $Version) }
        'mixin' { exit (Invoke-MtAssertMixin -Version $Version -Window $(if ($Window) { $Window } else { 'launch' })) }
    }
    exit $MT_EXIT_ERROR
}
