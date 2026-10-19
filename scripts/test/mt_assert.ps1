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

    ## t39：窗口**缺内容**时未命中 = 「不可判（INCONCLUSIVE）」，不再记产品 FAIL

    实测背景（2026-09-19 跨零点运行）：`latest.log` 在 `00:00:00` 日切为
    `2026-09-19-1.log.gz`，随后 6 条断言被记成 FAIL。逐字节复核后**结论分两层**，两层都必须守住：

      1. **`reassembled` 窗口本身是可证连续的**（库已经把身份段从窗口起点拼回来了）⇒ 此时未命中
         就是**真实结论**（读数不在窗口里 / 值不符），**必须**仍然记 FAIL —— 「跨零点」不是免罪符，
         否则会掩盖真实缺陷。重拼能力**已经存在**（B8/t22），不需要再实现一遍。
      2. 但**确证缺内容**的窗口上，未命中**不是**产品结论：本版把这种情形单列为
         `MT_ASSERT_INCONCLUSIVE`（非 FAIL、退出码 0、stderr 具名 WARN、stdout 打印不判理由），
         三种确证缺口（判定实现 = `Get-MtWindowFidelity`）：

           · `unreconstructable` —— 锚点文件身份找不到（重登/重启删了 `latest.log`、或轮转件被清理）⇒
             库退回「整读当前文件」，窗口起点之前的内容**确定丢失**；
           · `anchor-overrun` —— 请求起点 > 当前文件长度且身份未变（原地被截断）⇒ 起点被 clamp 到 0，
             起点之前的内容**确定丢失**；
           · `gap-after-anchor（ROTATED）` —— 锚点段与当前文件之间**另有更晚的轮转段未并入**
             （库在 `Note` 里显式写「未并入」，或经 latest 家族枚举复核发现）⇒ 那一段内容**确定缺失**。

        `absent` / `mixin` 这两种**否定式**判据在不可判窗口上仍不给强结论：命中（发现不该出现的东西）
        **仍是可信证据**（记 FAIL）；未命中只报 `INCONCLUSIVE(弱通过)` 并留 WARN，不冒充「已验证干净」。

        ⚠️ 适用范围：只覆盖 **latest 家族**（带日期 filePattern）。`debug-1/-2/-3.log.gz` 属 `debug-%i`
        家族（无日期、只在启动时轮转），**不参与**窗口重拼（见上方「通道差异」）；`crash` 通道是
        crash-reports **目录清单**、`kubejs` 通道整读 `server.log`，两者都**不走**窗口读取 ⇒ 无同类边界。
        判定与离线自证见 `temp/t90/t39-verify.ps1`（含「完整重拼窗口上未命中仍 FAIL」的反向回归）。

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
# kubejs_client —— KubeJS **客户端**脚本日志（logs/kubejs/client.log）。客户端侧状态
#            （ActionBarManager 的私有 message、TargetSelectionClient.isActive() 等）
#            **只能**从这里读：客户端状态没有服务端通道，聊天/截图又会被聊天行遮挡与
#            「缺字形占位符」污染（2026-09-22 实测）。2026-09-22 新增。
#
# ⚠️ 已移除的 `probe` 通道（run/<版本>/astral_probe.log）：该文件从未生成 —— KubeJS 的
#    Java 类过滤器拒绝 java.io，FileWriter 构造失败又被 try/catch 静默吞掉，表现为
#    「服务端权威通道不存在」，把测试链故障伪装成修复无效。不要再加回来。
$script:LogSources = [ordered]@{ 'latest' = 'latest_log'; 'debug' = 'debug_log'; 'kubejs_client' = 'kubejs_client_log' }

$script:TAG_UTF8 = [System.Text.UTF8Encoding]::new($false, $false)

# ── AutoJS 通道（2026-10-18；需求书 §3/§4）─────────────────────────────────
# eval / state 断言的证据不由日志产生，而是「谁来求值」：本文件只做**比较**，值由调用方
# （mt_case.ps1 的 eval_js / 内联 code）给出；也可以在这里用内联 --code 自己求值。
# 结构化记录文件与 mt_case.ps1 同路径（跨脚本契约）：mt_report 只读它，绝不重跑游戏。
$script:AutoJsRecordFile = Join-Path (Join-Path (Get-MtTestDir) 'cases') '.mt_autojs.json'
$script:AutoJsDefaultEndpoint = 'http://127.0.0.1:25585/mcp'
# expect 操作符集合（单键对象即操作符；其它单键对象按「嵌套期望」逐键递归）。
$script:ExpectOperators = @('min', 'max', 'gte', 'lte', 'regex', 'set', 'contains')
$script:ExpectEchoMax = 400

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

function Get-MtAssertFidelity {
    <#
    .SYNOPSIS
        t39：从 `Read-MtLogDelta` 的返回值里取「窗口保真度」；缺第 4 项（旧形状）时按**可证连续**处理。

    .NOTES
        兼容分支只为「调用方拿到 3 元组」的极端情况兜底 —— 默认 `Inconclusive = $false`
        （宁可记 FAIL 也不误免，与 `Test-MtStopInFlight` 的保守方向相反：那里宁可不判用户终止，
        这里宁可不放过未命中）。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)]$Delta)

    if ($Delta.Count -ge 4 -and $null -ne $Delta[3] -and $Delta[3] -is [System.Collections.IDictionary]) {
        return $Delta[3]
    }
    return @{ Inconclusive = $false; Reason = '' }
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

function Get-MtWindowFidelity {
    <#
    .SYNOPSIS
        t39：判定「本断言窗口是否**确证缺内容**」⇒ 缺内容时未命中**不得**记产品 FAIL，改记「不可判」。

    .NOTES
        只认三种**可证**缺口（不做任何推测），任何一种成立即 `Inconclusive = $true`：

          · `unreconstructable`：锚点文件身份没找到 ⇒ 库退回「整读当前文件」，起点之前的内容确定丢失；
          · `anchor-overrun`：请求起点 > 当前文件长度且身份未变 ⇒ 库把起点 clamp 到 0，起点之前确定丢失；
          · `gap-after-anchor（ROTATED）`：`reassembled` 之后仍有**更晚的轮转段未并入** ——
            库在 `Note` 里显式写「未并入」，或经 latest 家族枚举（`Get-MtRotatedLatestLogs`，
            同一唯一口径）复核发现锚点时间之后仍有候选轮转件。

        ⚠️ **反向纪律（不得滥用，t39 取证依据）**：`reassembled` 且**无**更晚轮转段时，窗口
        = 身份段[起点..] + 当前文件，是**可证连续**的完整窗口 ⇒ 返回 `Inconclusive = $false`，
        未命中仍记 FAIL。实测：本轮 1.21.1/ZHAO-BLESSING 的窗口起点 `132900B` **小于**该用例首个标记
        `AP_B1_GIVE` 的字节位 `134816B` ⇒ 全部读数都在窗口内，6 条 FAIL 属**值不符**而非窗口缺陷，
        不能用「跨零点」豁免。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]$W,
        [long]$RequestedOffset = 0,
        [string]$LogsDir = '',
        $Anchor = $null
    )

    $kind = [string]$W['Kind']
    if ($kind -eq 'unreconstructable') {
        return @{ Inconclusive = $true; Reason = 'unreconstructable（锚点文件身份丢失，窗口起点之前的内容确定丢失）' }
    }
    if ($RequestedOffset -gt 0 -and [long]$W['Offset'] -eq 0 -and @('intact', 'legacy') -contains $kind) {
        return @{ Inconclusive = $true; Reason = "anchor-overrun（请求起点 ${RequestedOffset}B > 当前文件长度，起点被 clamp 到 0 ⇒ 起点之前的内容确定丢失）" }
    }
    if ($kind -eq 'reassembled') {
        $gapByNote = ([string]$W['Note'] -like '*未并入*')
        $gapByEnum = $false
        if ($LogsDir -and $null -ne $Anchor -and $Anchor -is [System.Collections.IDictionary] -and $Anchor.Contains('mtime_ms')) {
            # 锚点文件的 mtime 之后仍有 latest 家族轮转件 ⇒ 锚点段与当前文件之间夹了未并入的段
            $since = ([DateTimeOffset]::FromUnixTimeMilliseconds([long]$Anchor['mtime_ms'] + 2000)).UtcDateTime.ToLocalTime()
            $later = @((Get-MtRotatedLatestLogs -LogsDir $LogsDir -Since $since -MaxRotated 16))
            $gapByEnum = ($later.Count -gt 0)
        }
        if ($gapByNote -or $gapByEnum) {
            return @{ Inconclusive = $true; Reason = 'gap-after-anchor（ROTATED：锚点段与当前文件之间另有更晚的轮转段未并入 ⇒ 窗口可能缺段）' }
        }
        return @{ Inconclusive = $false; Reason = '' }   # 可证连续：窗口完整，未命中 = 真实结论
    }
    return @{ Inconclusive = $false; Reason = '' }
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

        返回 (文本, 窗口种类标签, 实际起点, **窗口保真度**)。第 4 项由 `Get-MtWindowFidelity` 给出
        （t39）：只有**确证缺内容**的窗口才置 `Inconclusive = $true`，其未命中由调用方改记
        `MT_ASSERT_INCONCLUSIVE`（非 FAIL）；可证连续的窗口一律 `$false`（未命中仍记 FAIL）。
        **不再自己 Seek**：旧实现（按字节偏移直接 Seek
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
    $fid = Get-MtWindowFidelity -W $w -RequestedOffset $Offset -LogsDir $logsDir -Anchor $Anchor
    return , @([string]$w['Text'], $info, [long]$w['Offset'], $fid)
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

    # ── AutoJS 通道快照（2026-10-18）─────────────────────────────────────────
    # 端点 / protocolVersion / status 摘要来自 cases/.mt_autojs.json 的版本段
    # （mt_launch.ps1 的「AutoJS 就绪闸门」写入；用例期 eval 记录也挂在同一文件）。
    # 这是**新增键**：既有字段（offsets / anchors / window / ts / crash_baseline …）含义不动。
    # 没有记录时沿用同 run 的上一份，再没有就是 $null（legacy / 未装 autojs 的线）。
    $autoJsSnap = $null
    try {
        if (Test-Path -LiteralPath $script:AutoJsRecordFile -PathType Leaf) {
            $recText = [System.IO.File]::ReadAllText($script:AutoJsRecordFile, $script:TAG_UTF8)
            $recObj = ConvertFrom-MtAssertJsonText -Text $recText
            if ($recObj.Ok -and ($recObj.Value -is [System.Collections.IDictionary])) {
                $vs = Get-MtAssertField -Map $recObj.Value -Key 'versions'
                if (($vs -is [System.Collections.IDictionary]) -and (Test-MtAssertHasKey -Map $vs -Key $Version)) {
                    $autoJsSnap = Get-MtAssertField -Map $vs -Key $Version
                }
            }
        }
    } catch { $autoJsSnap = $null }
    if (($null -eq $autoJsSnap) -and ($null -ne $prev) -and ($prev -is [System.Collections.IDictionary]) -and (Test-MtAssertHasKey -Map $prev -Key 'autojs')) {
        $autoJsSnap = Get-MtAssertField -Map $prev -Key 'autojs'
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
        autojs         = $autoJsSnap
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
    $fid = Get-MtAssertFidelity -Delta $delta
    $pat = New-MtRegex -Pattern $PatternText
    $hits = $pat.Matches($text).Count
    $label = "$Source`:$name@${offsetUsed}B(win=$winUsed$(Get-MtWindowTag -Info $winInfo))"

    if ($hits -gt 0) {
        Write-MtLine "MT_ASSERT_LOG: PASS — /$PatternText/ 命中 $hits 次（$label）"
        Show-MtHits -Pattern $pat -Text $text
        return 0
    }
    if ($fid.Inconclusive) {
        # t39：窗口**确证缺内容** ⇒ 未命中不是产品结论，记「不可判（非 FAIL）」
        Write-MtLine "MT_ASSERT_LOG: INCONCLUSIVE — /$PatternText/ 未命中，但**窗口不可判**（$($fid.Reason)；$label）"
        Write-MtWarn "MT_ASSERT_INCONCLUSIVE: WARN — 窗口不可判（$($fid.Reason)）⇒ 本条**不计 FAIL**；该断言需重新执行测试验证"
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
    $fid = Get-MtAssertFidelity -Delta $delta
    $pat = New-MtRegex -Pattern $PatternText
    $label = "$Source`:$name@${offsetUsed}B(win=$winUsed$(Get-MtWindowTag -Info $winInfo))"

    if ($pat.IsMatch($text)) {
        # 命中 = **可信证据**（窗口里确有不该出现的东西）⇒ 即使窗口不可判也仍记 FAIL
        Write-MtLine "MT_ASSERT_ABSENT: FAIL — 不应出现的 /$PatternText/ 出现了（$label）"
        Show-MtHits -Pattern $pat -Text $text
        return 1
    }
    if ($fid.Inconclusive) {
        # t39：否定式判据在缺内容的窗口上只能给**弱结论**（不冒充「已验证干净」，但也不记 FAIL）
        Write-MtLine "MT_ASSERT_ABSENT: INCONCLUSIVE(弱通过) — /$PatternText/ 未出现，但**窗口不可判**（$($fid.Reason)；$label）"
        Write-MtWarn "MT_ASSERT_INCONCLUSIVE: WARN — 窗口不可判（$($fid.Reason)）⇒ ABSENT 的「未出现」为**弱结论**，需重新执行测试验证"
        return 0
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
    $fid = Get-MtAssertFidelity -Delta $delta
    $pat = [regex]::new('Mixin apply failed|Mixin apply error|Failed to apply mixin')
    if ($pat.IsMatch($text)) {
        Write-MtLine 'MT_ASSERT_MIXIN: FAIL — 检测到 Mixin 应用失败（兼容模组栈异常）'
        return 1
    }
    if ($fid.Inconclusive) {
        # t39：否定式判据在缺内容的窗口上只能给弱结论（窗口读的是 latest 通道，debug 家族不参与重拼）
        Write-MtLine "MT_ASSERT_MIXIN: INCONCLUSIVE(弱通过) — $name@${offsetUsed}B(win=$winUsed$(Get-MtWindowTag -Info $winInfo)) **窗口不可判**（$($fid.Reason)）⇒ 无 Mixin 失败为弱结论"
        Write-MtWarn "MT_ASSERT_INCONCLUSIVE: WARN — 窗口不可判（$($fid.Reason)）⇒ MIXIN 判据为弱结论，需重新执行测试验证"
        return 0
    }
    Write-MtLine "MT_ASSERT_MIXIN: PASS — $name@${offsetUsed}B(win=$winUsed$(Get-MtWindowTag -Info $winInfo)) 窗口内无 Mixin 失败"
    return 0
}

# ── AutoJS 通道：expect 比较引擎（2026-10-18；需求书 §4/§5）═════════════════
# 设计边界：
#   ① 本引擎**不认识**日志/通道，只做「期望 vs 实际」的纯函数比较 ⇒ 可离线单测。
#   ② 期望里出现的键必须全部命中；期望里没有的键不参与判定，但要在证据里回显（Extras）。
#   ③ 判据不足（缺 expect）与判定不符（FAIL）必须分开：前者是**用例写错**（ERROR），
#      后者才是**被测行为不符**（FAIL）—— 混在一起会把工具链故障显示成产品缺陷。
#   ④ 端点不可用一律 BLOCKED(11)，不是 FAIL。

function Get-MtAssertField {
    <#
    .SYNOPSIS
        从 Hashtable / OrderedDictionary / PSCustomObject 取字段（缺键返回 Default）。
    .NOTES
        键名比较用字符串比较（JSON 反序列化在 PS7 下给 Hashtable，-AsHashtable 时键是 string；
        但用例内的手写对象可能是 PSCustomObject，两种都要能吃）。
    #>
    [CmdletBinding()]
    param($Map, [Parameter(Mandatory)][string]$Key, $Default = $null)

    if ($null -eq $Map) { return $Default }
    if ($Map -is [System.Collections.IDictionary]) {
        foreach ($k in $Map.Keys) { if ([string]::Equals([string]$k, $Key, [System.StringComparison]::Ordinal)) { return $Map[$k] } }
        return $Default
    }
    $prop = $Map.PSObject.Properties[$Key]
    if ($null -ne $prop) { return $prop.Value }
    return $Default
}

function Test-MtAssertHasKey {
    [CmdletBinding()]
    param($Map, [Parameter(Mandatory)][string]$Key)

    if ($null -eq $Map) { return $false }
    if ($Map -is [System.Collections.IDictionary]) {
        foreach ($k in $Map.Keys) { if ([string]::Equals([string]$k, $Key, [System.StringComparison]::Ordinal)) { return $true } }
        return $false
    }
    return ($null -ne $Map.PSObject.Properties[$Key])
}

function ConvertTo-MtAssertPlain {
    <#
    .SYNOPSIS
        把任意 JSON 反序列化结果归一成 [ordered] / object[] / 标量（比较引擎的唯一输入形态）。
    #>
    [CmdletBinding()]
    param($Value)

    if ($null -eq $Value) { return $null }
    if ($Value -is [System.Collections.IDictionary]) {
        $d = [ordered]@{}
        foreach ($k in @($Value.Keys)) { $d[[string]$k] = ConvertTo-MtAssertPlain -Value $Value[$k] }
        return $d
    }
    if ($Value -is [string] -or $Value -is [ValueType]) { return $Value }
    if ($Value -is [System.Collections.IEnumerable]) {
        $list = New-Object System.Collections.ArrayList
        foreach ($item in $Value) { [void]$list.Add((ConvertTo-MtAssertPlain -Value $item)) }
        return , $list.ToArray()
    }
    $d = [ordered]@{}
    foreach ($prop in $Value.PSObject.Properties) { $d[$prop.Name] = ConvertTo-MtAssertPlain -Value $prop.Value }
    return $d
}

function ConvertTo-MtAssertJson {
    <#
    .SYNOPSIS
        compact JSON 回显（Max>0 时超长截断；给证据行用，避免刷屏）。
    #>
    [CmdletBinding()]
    param($Value, [int]$Max = 0)

    $plain = ConvertTo-MtAssertPlain -Value $Value
    $json = ConvertTo-Json -InputObject $plain -Depth 20 -Compress
    if ($null -eq $json) { $json = 'null' }
    $json = [string]$json
    if ($Max -gt 0 -and $json.Length -gt $Max) {
        $len = $json.Length
        return ($json.Substring(0, $Max) + ('...（截断，原长 {0}）' -f $len))
    }
    return $json
}

function Format-MtExpectValue {
    [CmdletBinding()]
    param($Value)

    if ($null -eq $Value) { return 'null' }
    return (ConvertTo-MtAssertJson -Value $Value -Max $script:ExpectEchoMax)
}

function Get-MtExpectKind {
    <#
    .SYNOPSIS
        期望/实际值的形态：object / array / scalar。
    #>
    [CmdletBinding()]
    param($Expected)

    if ($null -eq $Expected) { return 'scalar' }
    if ($Expected -is [System.Collections.IDictionary]) { return 'object' }
    if ($Expected -is [string] -or $Expected -is [ValueType]) { return 'scalar' }
    if ($Expected -is [System.Collections.IEnumerable]) { return 'array' }
    return 'object'
}

function ConvertTo-MtExpectNumber {
    <#
    .SYNOPSIS
        数值归一（数值类型直取；数值字符串按不变文化解析；其它返回 $null）。
    #>
    [CmdletBinding()]
    param($Value)

    if ($null -eq $Value) { return $null }
    if ($Value -is [bool]) { return $null }
    if ($Value -is [double] -or $Value -is [single] -or $Value -is [decimal] -or
        $Value -is [int] -or $Value -is [long] -or $Value -is [int16] -or $Value -is [byte] -or
        $Value -is [uint32] -or $Value -is [uint64] -or $Value -is [int64]) {
        return [double]$Value
    }
    if ($Value -is [string]) {
        $d = 0.0
        if ([double]::TryParse($Value, [System.Globalization.NumberStyles]::Any, [System.Globalization.CultureInfo]::InvariantCulture, [ref]$d)) { return $d }
    }
    return $null
}

function Test-MtExpectScalarEqual {
    <#
    .SYNOPSIS
        标量相等：布尔/数字/字符串归一后比较；字符串不区分大小写。
    .NOTES
        数字用相对容差 1e-9 比较（JSON 里 1 与 1.0 必须视为相等，而 double 直比会受解析影响）。
    #>
    [CmdletBinding()]
    param($Expected, $Actual)

    if ($null -eq $Expected) { return ($null -eq $Actual) }
    if ($Expected -is [bool]) {
        if ($Actual -is [bool]) { return ($Expected -eq $Actual) }
        $n = ConvertTo-MtExpectNumber -Value $Actual
        if ($null -ne $n) { return ($Expected -eq ($n -ne 0)) }
        if ($Actual -is [string]) {
            $s = $Actual.Trim()
            if ([string]::Equals($s, 'true', [System.StringComparison]::OrdinalIgnoreCase)) { return $Expected }
            if ([string]::Equals($s, 'false', [System.StringComparison]::OrdinalIgnoreCase)) { return (-not $Expected) }
        }
        return $false
    }
    $en = ConvertTo-MtExpectNumber -Value $Expected
    if ($null -ne $en) {
        $an = ConvertTo-MtExpectNumber -Value $Actual
        if ($null -eq $an) { return $false }
        $tol = 1e-9 * [Math]::Max(1.0, [Math]::Abs($en))
        return ([Math]::Abs($en - $an) -le $tol)
    }
    $aText = ''
    if ($Actual -is [bool]) { if ($Actual) { $aText = 'true' } else { $aText = 'false' } }
    elseif ($null -eq $Actual) { $aText = '' }
    elseif ($Actual -is [string]) { $aText = $Actual }
    elseif ($Actual -is [System.Collections.IDictionary]) { $aText = (Format-MtExpectValue -Value $Actual) }
    elseif ((Get-MtExpectKind -Expected $Actual) -eq 'array') { $aText = (Format-MtExpectValue -Value $Actual) }
    else { $aText = [string]$Actual }
    return [string]::Equals([string]$Expected, $aText, [System.StringComparison]::OrdinalIgnoreCase)
}

function Test-MtExpectOperator {
    <#
    .SYNOPSIS
        操作符判据：min / max / gte / lte / regex / set / contains。返回失配串数组（空 = 命中）。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Name, $Operand, $Actual, [Parameter(Mandatory)][string]$Path)

    $out = @()
    $opText = ('{' + $Name + ':' + (Format-MtExpectValue -Value $Operand) + '}')
    $mismatch = { param($Kind) '路径 {0} 期望 {1} 实际 {2}（{3}）' -f $Path, $opText, (Format-MtExpectValue -Value $Actual), $Kind }

    if ($Name -eq 'min' -or $Name -eq 'max' -or $Name -eq 'gte' -or $Name -eq 'lte') {
        $need = ConvertTo-MtExpectNumber -Value $Operand
        if ($null -eq $need) { return @((& $mismatch ('判据不合法：{0} 需要数字' -f $Name))) }
        $have = ConvertTo-MtExpectNumber -Value $Actual
        if ($null -eq $have) { return @((& $mismatch '非数值')) }
        $ok = $false
        if ($Name -eq 'min' -or $Name -eq 'gte') { $ok = ($have -ge $need) } else { $ok = ($have -le $need) }
        if (-not $ok) { $out += (& $mismatch '数值越界') }
        return $out
    }
    if ($Name -eq 'regex') {
        if (-not ($Operand -is [string])) { return @((& $mismatch '判据不合法：regex 需要字符串')) }
        $subject = ''
        if ($Actual -is [string]) { $subject = $Actual }
        elseif ($null -eq $Actual) { $subject = '' }
        elseif ((Get-MtExpectKind -Expected $Actual) -eq 'scalar' -and -not ($Actual -is [bool])) { $subject = [string]$Actual }
        else { return @((& $mismatch '类型不符：regex 只能作用于字符串')) }
        $rx = $null
        try { $rx = [regex]::new([string]$Operand) } catch { return @((& $mismatch ('判据不合法：正则非法 ' + $_.Exception.Message))) }
        if (-not $rx.IsMatch($subject)) { $out += (& $mismatch '正则不匹配') }
        return $out
    }
    # set / contains 都要操作数是数组
    if (-not (($Operand -is [System.Collections.IEnumerable]) -and -not ($Operand -is [string]) -and -not ($Operand -is [System.Collections.IDictionary]))) {
        return @((& $mismatch ('判据不合法：{0} 需要数组' -f $Name)))
    }
    if (-not (($Actual -is [System.Collections.IEnumerable]) -and -not ($Actual -is [string]) -and -not ($Actual -is [System.Collections.IDictionary]))) {
        return @((& $mismatch '类型不符：需要数组'))
    }
    # 注意：ConvertTo-MtAssertPlain 对数组走「return , $arr」（防递归展开）。
    # 因此不能写 @(函数调用)：那会得到「装着整个数组的单元素数组」，集合判据会整片假失败。
    # 必须先落到变量再 @()。
    $wantRaw = ConvertTo-MtAssertPlain -Value $Operand
    $want = @($wantRaw)
    $haveRaw = ConvertTo-MtAssertPlain -Value $Actual
    $haveItems = @($haveRaw)
    if ($Name -eq 'set') {
        if ($want.Count -ne $haveItems.Count) {
            $out += (& $mismatch ('集合元素数不符（期望 {0} 实际 {1}）' -f $want.Count, $haveItems.Count))
        }
        $pool = New-Object System.Collections.ArrayList
        foreach ($h in $haveItems) { [void]$pool.Add($h) }
        foreach ($w in $want) {
            $hit = -1
            for ($i = 0; $i -lt $pool.Count; $i++) {
                if (Test-MtExpectScalarEqual -Expected $w -Actual $pool[$i]) { $hit = $i; break }
            }
            if ($hit -ge 0) { [void]$pool.RemoveAt($hit) }
            else { $out += (& $mismatch ('集合缺元素 ' + (Format-MtExpectValue -Value $w))) }
        }
        return $out
    }
    foreach ($w in $want) {
        $found = $false
        foreach ($h in $haveItems) {
            if (Test-MtExpectScalarEqual -Expected $w -Actual $h) { $found = $true; break }
        }
        if (-not $found) { $out += (& $mismatch ('缺少子集元素 ' + (Format-MtExpectValue -Value $w))) }
    }
    return $out
}

function Test-MtExpectNode {
    <#
    .SYNOPSIS
        逐键递归比较；返回失配串数组（空 = 命中）。报文固定三段：路径 + 期望 + 实际。
    #>
    [CmdletBinding()]
    param($Expected, $Actual, [string]$Path = '<root>', [bool]$Present = $true)

    $out = @()
    if (-not $Present) {
        return @(('路径 {0} 期望 {1} 实际 <缺失>（缺失键）' -f $Path, (Format-MtExpectValue -Value $Expected)))
    }
    $kind = Get-MtExpectKind -Expected $Expected
    if ($kind -eq 'object') {
        $keys = @($Expected.Keys)
        if ($keys.Count -eq 1 -and ($script:ExpectOperators -contains [string]$keys[0])) {
            $opName = [string]$keys[0]
            return (Test-MtExpectOperator -Name $opName -Operand $Expected[$keys[0]] -Actual $Actual -Path $Path)
        }
        $actualKind = Get-MtExpectKind -Expected $Actual
        if ($actualKind -ne 'object') {
            return @(('路径 {0} 期望 <对象> 实际 {1}（类型不符）' -f $Path, (Format-MtExpectValue -Value $Actual)))
        }
        foreach ($k in $keys) {
            $kName = [string]$k
            $subPath = $Path
            if ($Path -eq '<root>') { $subPath = $kName } else { $subPath = $Path + '.' + $kName }
            if (Test-MtAssertHasKey -Map $Actual -Key $kName) {
                $out += @(Test-MtExpectNode -Expected $Expected[$k] -Actual (Get-MtAssertField -Map $Actual -Key $kName) -Path $subPath -Present $true)
            } else {
                $out += @(Test-MtExpectNode -Expected $Expected[$k] -Actual $null -Path $subPath -Present $false)
            }
        }
        return $out
    }
    if ($kind -eq 'array') {
        $actualKind = Get-MtExpectKind -Expected $Actual
        if ($actualKind -ne 'array') {
            return @(('路径 {0} 期望 <数组> 实际 {1}（类型不符）' -f $Path, (Format-MtExpectValue -Value $Actual)))
        }
        $wantItems = @($Expected)
        $haveItems = @($Actual)
        if ($wantItems.Count -ne $haveItems.Count) {
            $out += ('路径 {0} 期望 {1} 实际 {2}（长度不符）' -f $Path, (Format-MtExpectValue -Value $wantItems), (Format-MtExpectValue -Value $haveItems))
        }
        $n = [Math]::Min($wantItems.Count, $haveItems.Count)
        for ($i = 0; $i -lt $n; $i++) {
            $out += @(Test-MtExpectNode -Expected $wantItems[$i] -Actual $haveItems[$i] -Path ($Path + '[' + $i + ']') -Present $true)
        }
        return $out
    }
    if (-not (Test-MtExpectScalarEqual -Expected $Expected -Actual $Actual)) {
        $out += ('路径 {0} 期望 {1} 实际 {2}（标量不等）' -f $Path, (Format-MtExpectValue -Value $Expected), (Format-MtExpectValue -Value $Actual))
    }
    return $out
}

function Get-MtExpectExtras {
    <#
    .SYNOPSIS
        收集「实际里有、期望里没写」的键路径（不参与判定，只回显证据）。
    #>
    [CmdletBinding()]
    param($Expected, $Actual, [string]$Path = '<root>')

    $out = @()
    if ((Get-MtExpectKind -Expected $Expected) -ne 'object') { return $out }
    $keys = @($Expected.Keys)
    if ($keys.Count -eq 1 -and ($script:ExpectOperators -contains [string]$keys[0])) { return $out }
    if ((Get-MtExpectKind -Expected $Actual) -ne 'object') { return $out }
    foreach ($k in @($Actual.Keys)) {
        $kName = [string]$k
        $subPath = $Path
        if ($Path -eq '<root>') { $subPath = $kName } else { $subPath = $Path + '.' + $kName }
        if (-not (Test-MtAssertHasKey -Map $Expected -Key $kName)) { $out += $subPath; continue }
        $out += @(Get-MtExpectExtras -Expected (Get-MtAssertField -Map $Expected -Key $kName) -Actual (Get-MtAssertField -Map $Actual -Key $kName) -Path $subPath)
    }
    return $out
}

function Test-MtExpectMatch {
    <#
    .SYNOPSIS
        expect 引擎入口：@{ Ok; Mismatches; Extras }。
    #>
    [CmdletBinding()]
    param($Expected, $Actual)

    $plainExp = ConvertTo-MtAssertPlain -Value $Expected
    $plainAct = ConvertTo-MtAssertPlain -Value $Actual
    $mism = @(Test-MtExpectNode -Expected $plainExp -Actual $plainAct -Path '<root>' -Present $true)
    $extra = @(Get-MtExpectExtras -Expected $plainExp -Actual $plainAct -Path '<root>')
    return @{ Ok = ($mism.Count -eq 0); Mismatches = $mism; Extras = $extra }
}

function ConvertFrom-MtAssertJsonText {
    <#
    .SYNOPSIS
        解析 JSON 文本 → @{ Ok; Value; Err }（不抛异常；用于 --value-json / --expect）。
    #>
    [CmdletBinding()]
    param([AllowEmptyString()][string]$Text)

    try {
        $v = $Text | ConvertFrom-Json -AsHashtable -ErrorAction Stop
        return @{ Ok = $true; Value = $v; Err = '' }
    } catch {
        return @{ Ok = $false; Value = $null; Err = $_.Exception.Message }
    }
}

function Import-MtAssertAutoJsModule {
    <#
    .SYNOPSIS
        按契约导入 Mt.AutoJs.psm1（缺失/失败返回 $false，不抛）。
    .NOTES
        先看会话里是否**已经**有 Invoke-MtAutoJsEval：离线自测时 temp/t2 的桩模块会先导入，
        此时不能再 —— 也不该 —— 去 Import-Module 真实模块把它顶掉。
    #>
    [CmdletBinding()]
    param()

    if ($null -ne $script:AssertAutoJsModuleReady) { return [bool]$script:AssertAutoJsModuleReady }
    $script:AssertAutoJsModuleReady = $false
    if (Get-Command Invoke-MtAutoJsEval -ErrorAction SilentlyContinue) {
        $script:AssertAutoJsModuleReady = $true
        return $true
    }
    $mod = Join-Path $script:LibDir 'Mt.AutoJs.psm1'
    if (-not (Test-Path -LiteralPath $mod -PathType Leaf)) { return $false }
    try { Import-Module $mod -Force -ErrorAction Stop; $script:AssertAutoJsModuleReady = $true } catch { $script:AssertAutoJsModuleReady = $false }
    return [bool]$script:AssertAutoJsModuleReady
}

function Test-MtAssertAutoJsEndpoint {
    <#
    .SYNOPSIS
        AutoJS 端点可用性：@{ Ok; Reason; Status; Url }（端点没起 = BLOCKED 的依据）。
    #>
    [CmdletBinding()]
    param([int]$TimeoutMs = 0)

    if ($TimeoutMs -le 0) {
        $TimeoutMs = 15000
        $raw = [string]$env:MT_AUTOJS_TIMEOUT_MS
        $n = 0
        if ($raw -and [int]::TryParse($raw, [ref]$n) -and $n -gt 0) { $TimeoutMs = $n }
    }
    if (-not (Import-MtAssertAutoJsModule)) {
        return @{ Ok = $false; Reason = 'AutoJS 模块不可用（scripts/test/lib/Mt.AutoJs.psm1 缺失或导入失败）'; Status = $null; Url = '' }
    }
    $url = $script:AutoJsDefaultEndpoint
    try {
        $conf = Get-MtConf
        if ($conf -is [System.Collections.IDictionary] -and (Test-MtAssertHasKey -Map $conf -Key 'MT_AUTOJS_ENDPOINT')) {
            $cand = [string](Get-MtAssertField -Map $conf -Key 'MT_AUTOJS_ENDPOINT')
            if ($cand) { $url = $cand }
        }
    } catch { }
    $res = $null
    try { $res = Test-MtAutoJsEndpoint -EndpointUrl $url -TimeoutMs $TimeoutMs } catch {
        return @{ Ok = $false; Reason = ('Test-MtAutoJsEndpoint 抛异常：' + $_.Exception.Message); Status = $null; Url = $url }
    }
    $ok = [bool](Get-MtAssertField -Map $res -Key 'Ok' -Default $false)
    $reason = [string](Get-MtAssertField -Map $res -Key 'Reason' -Default '')
    if (-not $ok -and -not $reason) { $reason = '未知原因' }
    return @{ Ok = $ok; Reason = $reason; Status = (Get-MtAssertField -Map $res -Key 'Status') ; Url = $url }
}

function Invoke-MtAssertEval {
    <#
    .SYNOPSIS
        eval / state 断言：取到值 → 用 expect 引擎判定 → 打 MT_ASSERT_EVAL / MT_ASSERT_STATE 结论行。
    .NOTES
        退出码语义与既有断言一致：0=PASS，1=FAIL（判据不符），2=ERROR（用例/判据写错），11=BLOCKED（端点不可用）。
        state 在缺 expect 时的语义 = **只打印证据**（PASS + 「无判据」标注），绝不假装判定过。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Version,
        [string]$Kind = 'eval',
        [string]$ValueJson = '',
        [string]$FromFile = '',
        [string]$Code = '',
        [string]$Side = 'auto',
        [string]$ExpectJson = '',
        [string]$Label = ''
    )

    if (@('eval', 'state') -notcontains $Kind) { $Kind = 'eval' }
    $tag = 'MT_ASSERT_' + $Kind.ToUpperInvariant()
    $lab = ''
    if ($Label) { $lab = '（' + $Label + '）' }

    $value = $null
    $haveValue = $false
    if ($FromFile) {
        if (-not (Test-Path -LiteralPath $FromFile -PathType Leaf)) {
            Write-MtErrLine ($tag + ': ERROR — 值文件不存在：' + $FromFile)
            return 2
        }
        $text = [System.IO.File]::ReadAllText($FromFile, $script:TAG_UTF8)
        $parsed = ConvertFrom-MtAssertJsonText -Text $text
        if (-not $parsed.Ok) {
            Write-MtErrLine ($tag + ': ERROR — 值文件 JSON 不合法：' + $parsed.Err)
            return 2
        }
        $value = $parsed.Value
        $haveValue = $true
    } elseif ($ValueJson) {
        $parsed = ConvertFrom-MtAssertJsonText -Text $ValueJson
        if (-not $parsed.Ok) {
            Write-MtErrLine ($tag + ': ERROR — --value-json 不合法：' + $parsed.Err)
            return 2
        }
        $value = $parsed.Value
        $haveValue = $true
    } elseif ($Code) {
        $ep = Test-MtAssertAutoJsEndpoint
        if (-not $ep.Ok) {
            Write-MtLine ($tag + ': BLOCKED — AutoJS 端点不可用（' + $ep.Reason + '）=> 求值不可用，判定 BLOCKED（不是 FAIL）' + $lab)
            return 11
        }
        $sideArg = $Side
        if (-not $sideArg) { $sideArg = 'auto' }
        $r = $null
        try { $r = Invoke-MtAutoJsEval -Code $Code -Side $sideArg } catch {
            Write-MtErrLine ($tag + ': ERROR — 调用 Invoke-MtAutoJsEval 抛异常：' + $_.Exception.Message)
            return 2
        }
        if (-not [bool](Get-MtAssertField -Map $r -Key 'Ok' -Default $false)) {
            $err = [string](Get-MtAssertField -Map $r -Key 'Error' -Default '')
            if (-not $err) { $err = '脚本抛错（无 error 文本）' }
            Write-MtLine ($tag + ': FAIL — 内联 code 求值失败：' + $err + $lab)
            return 1
        }
        $value = ConvertTo-MtAssertPlain -Value (Get-MtAssertField -Map $r -Key 'Value')
        $haveValue = $true
    }
    if (-not $haveValue) {
        Write-MtErrLine ($tag + ': ERROR — 需要 --value-json / --from-file / --code 之一（没有可判定的值）')
        return 2
    }

    $evidence = ConvertTo-MtAssertJson -Value $value -Max $script:ExpectEchoMax
    if (-not $ExpectJson) {
        if ($Kind -eq 'state') {
            Write-MtLine ($tag + ': PASS — 证据 ' + $evidence + '（无判据）' + $lab)
            return 0
        }
        Write-MtErrLine ($tag + ': ERROR — eval 断言缺少 expect（判据缺失不等于通过）')
        return 2
    }
    $exp = ConvertFrom-MtAssertJsonText -Text $ExpectJson
    if (-not $exp.Ok) {
        Write-MtErrLine ($tag + ': ERROR — expect JSON 不合法：' + $exp.Err)
        return 2
    }
    $m = Test-MtExpectMatch -Expected $exp.Value -Actual $value
    if (-not $m.Ok) {
        $text = @($m.Mismatches) -join ' | '
        Write-MtLine ($tag + ': FAIL — ' + $text + '（实际值 ' + $evidence + '）' + $lab)
        return 1
    }
    $extraText = ''
    if (@($m.Extras).Count -gt 0) { $extraText = '；期望未覆盖的键已回显：' + (@($m.Extras) -join ', ') }
    Write-MtLine ($tag + ': PASS — 期望 ' + (ConvertTo-MtAssertJson -Value $exp.Value -Max $script:ExpectEchoMax) + ' 实际 ' + $evidence + $extraText + $lab)
    return 0
}

# ── 入口 ══════════════════════════════════════════════════════════════════
if ($MyInvocation.InvocationName -ne '.') {

    $Cmd = ''
    $Version = ''
    $PatternText = ''
    $Source = 'latest'
    $Window = ''            # '' = 按子命令取默认（log/absent=case，mixin=launch）
    # ── AutoJS 通道（eval / state）专用参数 ─────────────────────────────────
    $ValueJson = ''         # 调用方已求好的值（JSON 文本）
    $FromFile = ''          # 同上，但值太大时走临时文件（避免命令行长度限制）
    $Code = ''              # 内联 JS：本进程自己求值（端点不可用 => BLOCKED）
    $Side = 'auto'
    $ExpectJson = ''        # 判据（JSON 文本）；state 允许为空 = 只打印证据
    $AssertLabel = ''

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
        } elseif ($key -eq 'value-json') {
            if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --value-json 的值'; exit $MT_EXIT_ERROR }
            $ValueJson = [string]$args[$i + 1]; $i += 2
        } elseif ($key -eq 'from-file') {
            if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --from-file 的值'; exit $MT_EXIT_ERROR }
            $FromFile = [string]$args[$i + 1]; $i += 2
        } elseif ($key -eq 'code') {
            if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --code 的值'; exit $MT_EXIT_ERROR }
            $Code = [string]$args[$i + 1]; $i += 2
        } elseif ($key -eq 'side') {
            if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --side 的值'; exit $MT_EXIT_ERROR }
            $Side = ([string]$args[$i + 1]).ToLowerInvariant(); $i += 2
        } elseif ($key -eq 'expect') {
            if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --expect 的值'; exit $MT_EXIT_ERROR }
            $ExpectJson = [string]$args[$i + 1]; $i += 2
        } elseif ($key -eq 'label') {
            if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --label 的值'; exit $MT_EXIT_ERROR }
            $AssertLabel = [string]$args[$i + 1]; $i += 2
        } else {
            Write-MtErrorLine "未知参数 $tok"; exit $MT_EXIT_ERROR
        }
    }

    if ($Cmd -notin @('snapshot', 'log', 'absent', 'crash', 'kubejs', 'mixin', 'eval', 'state')) {
        Write-MtErrorLine '必须指定子命令 snapshot / log / absent / crash / kubejs / mixin / eval / state'
        exit $MT_EXIT_ERROR
    }
    if (($Cmd -eq 'eval' -or $Cmd -eq 'state') -and (@('auto', 'server', 'client') -notcontains $Side)) {
        Write-MtErrorLine "--side 只接受 auto / server / client（收到 $Side）"
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
        'eval' { exit (Invoke-MtAssertEval -Version $Version -Kind 'eval' -ValueJson $ValueJson -FromFile $FromFile -Code $Code -Side $Side -ExpectJson $ExpectJson -Label $AssertLabel) }
        'state' { exit (Invoke-MtAssertEval -Version $Version -Kind 'state' -ValueJson $ValueJson -FromFile $FromFile -Code $Code -Side $Side -ExpectJson $ExpectJson -Label $AssertLabel) }
    }
    exit $MT_EXIT_ERROR
}
