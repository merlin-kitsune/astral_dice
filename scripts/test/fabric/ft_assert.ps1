#Requires -Version 7.0
<#
.SYNOPSIS
    ft_assert — fabric-1.20.1 线的日志断言引擎（fabric 测试台的判定臂）。

.DESCRIPTION
    子命令：
      snapshot --side S [--window case|launch]   记录窗口起点（日志字节长度）
      log      --pattern RE [--side S] [--window ...]   窗口内**必须**命中
      absent   --pattern RE [--side S] [--window ...]   窗口内**必须不**命中
      case     --case <cases/*.json>                    跑一条用例的全部断言
      help

    窗口语义（与生产线 mt_assert.ps1 同口径，但实现是简化版）：
      whole   整文件（历史行也算）
      case    自最近一次 `snapshot --window case` 起（**默认**）
      launch  自最近一次 `snapshot --window launch` 起
    为什么必须有窗口：整文件 grep 会让历史运行留下的同名标记造成**假 PASS**
    （生产线的实测记录：命中数随用例递增 14→28→…→126）。
    ⚠️ 已知简化：生产线用「文件身份锚点（ctime + 头部指纹）」处理 log4j 跨零点日切；
    本台只记字节长度，长度回退时 WARN 并退化为整文件读取（见 lib/Ft.Common.psm1 的 Save-FtSnapshot 注释）。

    断言类型（case JSON 的 asserts[].type）：
      log                  正则必须命中（spec.pattern）
      absent               正则必须不命中（spec.pattern）
      crash                日志里不得出现崩溃标记（spec.pattern 可选，默认 'Crash report|Exception in thread "Render thread"'）
      dispatch_tick        LoaderBus 派发报告里 ServerTickEvent 必须为正数
      dispatch_fired       spec.events 列出的每个事件类都必须出现在「已派发」清单
      dispatch_zero_absent spec.events 列出的每个事件类都**不得**出现在「未派发」清单
      jar                  开包核对（spec.models_min / recipes_min / bountiful_max / trinkets_min / embed_lib）

    输出：AP_FAB_ASSERT: type=... ok=... 读数行 + MT_FAB_ASSERT: OK|FAIL；退出码 0 / 1 / 2。

.EXAMPLE
    pwsh -NoProfile -File scripts/test/fabric/ft_assert.ps1 snapshot --side server --window launch
    pwsh -NoProfile -File scripts/test/fabric/ft_assert.ps1 log --pattern 'Done \(\d' --window launch
    pwsh -NoProfile -File scripts/test/fabric/ft_assert.ps1 absent --pattern 'FT_INJECT_ERR'
    pwsh -NoProfile -File scripts/test/fabric/ft_assert.ps1 case --case scripts/test/fabric/cases/FAB-DISPATCH-BASIC.json

.NOTES
    本文件既可**直接执行**（走 CLI），也可被 `. `（dot-source）以复用函数
    （ft_case.ps1 / ft_dispatchreport.ps1 两者都这么做）—— 入口用
    `$MyInvocation.InvocationName -ne '.'` 守卫，与生产线脚本同法。
#>

$ErrorActionPreference = 'Stop'

$script:FtLibDir = Join-Path $PSScriptRoot 'lib'
Import-Module (Join-Path $script:FtLibDir 'Ft.Common.psm1') -Force

# ══ 派发报告解析（ft_dispatchreport.ps1 也复用本函数）═════════════════════
function Get-FtDispatchReport {
    <#
    .SYNOPSIS
        从日志文本中抽取 LoaderBus#dispatchReport() 的输出块。
    .DESCRIPTION
        依据 = platform/event/LoaderBus.java:87-88 的两行格式
          `\n  [已派发 N 类] A=1 B=2 `
          `\n  [未派发 M 类] X Y `
        以及 platform/FabricBridges.java:87,99-100 的打印前缀 `[Astral Dice] 事件派发统计(…)。
        实测样例（run/server/logs/latest.log:230-232）：
          230: … [Astral Dice] 事件派发统计(开局 600 tick)
          231:   [已派发 10 类] Added=1 … ServerTickEvent=599
          232:   [未派发 26 类] AnvilUpdateEvent … 
        续行**不带** log4j 时间戳前缀（整条是同一句多行文本）。
    .PARAMETER Source
        first | last | all（默认 last：关服那份比 600 tick 那份更完整）
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][AllowEmptyString()][string]$Text,
        [ValidateSet('first', 'last', 'all')][string]$Source = 'last'
    )

    $lines = @($Text -split "`r?`n")
    $blocks = New-Object System.Collections.Generic.List[object]

    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -notmatch '事件派发统计') { continue }
        $header = $lines[$i]
        $firedLine = ''
        $idleLine = ''
        for ($k = $i + 1; $k -lt [Math]::Min($i + 6, $lines.Count); $k++) {
            if (-not $firedLine -and $lines[$k] -match '\[已派发\s+\d+\s*类\]') { $firedLine = $lines[$k]; continue }
            if (-not $idleLine -and $lines[$k] -match '\[未派发\s+\d+\s*类\]') { $idleLine = $lines[$k] }
            if ($firedLine -and $idleLine) { break }
        }
        if (-not $firedLine -and -not $idleLine) { continue }

        $fired = @{}
        $idle = New-Object System.Collections.Generic.List[string]
        $firedCount = -1
        $idleCount = -1

        if ($firedLine) {
            $m = [regex]::Match($firedLine, '\[已派发\s+(\d+)\s*类\]\s*(.*)$')
            if ($m.Success) {
                $firedCount = [int]$m.Groups[1].Value
                foreach ($tok in @($m.Groups[2].Value -split '\s+')) {
                    $tm = [regex]::Match($tok, '^([A-Za-z_][A-Za-z0-9_$]*)=(\d+)$')
                    if ($tm.Success) { $fired[$tm.Groups[1].Value] = [int]$tm.Groups[2].Value }
                }
            }
        }
        if ($idleLine) {
            $m = [regex]::Match($idleLine, '\[未派发\s+(\d+)\s*类\]\s*(.*)$')
            if ($m.Success) {
                $idleCount = [int]$m.Groups[1].Value
                foreach ($tok in @($m.Groups[2].Value -split '\s+')) {
                    if ($tok) { $idle.Add($tok) }
                }
            }
        }

        $blocks.Add([pscustomobject]@{
            Header     = $header
            Fired      = $fired
            Idle       = @($idle)
            FiredCount = $firedCount
            IdleCount  = $idleCount
            LineIndex  = $i
        })
    }

    if ($blocks.Count -eq 0) { return $null }
    if ($Source -eq 'all') { return @($blocks) }
    if ($Source -eq 'first') { return $blocks[0] }
    return $blocks[$blocks.Count - 1]
}

# ══ 单条断言求值 ══════════════════════════════════════════════════════════
function Invoke-FtSpecAssert {
    <#
    .SYNOPSIS
        求值一条用例断言规格，返回 AP_FAB_ASSERT 读数对象。
    .OUTPUTS
        [pscustomobject]@{ Type; Target; Ok(bool); Detail; Line(string) }
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][object]$Spec,
        [string]$DefaultSide = 'server',
        [string]$DefaultWindow = 'case'
    )

    $type = [string]$Spec.type
    if (-not $type) { $type = 'log' }
    $side = $DefaultSide
    if ($Spec.side) { $side = [string]$Spec.side }
    $window = $DefaultWindow
    if ($Spec.window) { $window = [string]$Spec.window }
    [void](Assert-FtSide -Side $side)
    $text = Get-FtLogWindow -Side $side -Window $window

    switch ($type) {
        'log' {
            $pat = [string]$Spec.pattern
            $hits = Test-FtLogPattern -Text $text -Pattern $pat
            $ok = ($hits -gt 0)
            return New-FtAssertResult -Type 'log' -Target $pat -Ok $ok -Detail ("side={0} window={1} hits={2}" -f $side, $window, $hits)
        }
        'absent' {
            $pat = [string]$Spec.pattern
            $hits = Test-FtLogPattern -Text $text -Pattern $pat
            $ok = ($hits -eq 0)
            return New-FtAssertResult -Type 'absent' -Target $pat -Ok $ok -Detail ("side={0} window={1} hits={2}" -f $side, $window, $hits)
        }
        'crash' {
            $pat = 'Crash report|Exception in thread "Render thread"|A fatal error has been detected'
            if ($Spec.pattern) { $pat = [string]$Spec.pattern }
            $hits = Test-FtLogPattern -Text $text -Pattern $pat
            $ok = ($hits -eq 0)
            return New-FtAssertResult -Type 'crash' -Target $pat -Ok $ok -Detail ("side={0} window={1} hits={2}" -f $side, $window, $hits)
        }
        'dispatch_tick' {
            $rep = Get-FtDispatchReport -Text $text -Source 'last'
            if ($null -eq $rep) {
                return New-FtAssertResult -Type 'dispatch_tick' -Target 'ServerTickEvent' -Ok $false -Detail ("side={0} window={1} 未找到派发报告" -f $side, $window)
            }
            $n = 0
            if ($rep.Fired.ContainsKey('ServerTickEvent')) { $n = [int]$rep.Fired['ServerTickEvent'] }
            $ok = ($n -gt 0)
            return New-FtAssertResult -Type 'dispatch_tick' -Target 'ServerTickEvent' -Ok $ok -Detail ("side={0} window={1} ServerTickEvent={2}" -f $side, $window, $n)
        }
        'dispatch_fired' {
            $rep = Get-FtDispatchReport -Text $text -Source 'last'
            if ($null -eq $rep) {
                return New-FtAssertResult -Type 'dispatch_fired' -Target '(-)' -Ok $false -Detail ("side={0} 未找到派发报告" -f $side)
            }
            $missing = New-Object System.Collections.Generic.List[string]
            foreach ($e in @($Spec.events)) {
                if (-not $rep.Fired.ContainsKey([string]$e)) { $missing.Add([string]$e) }
            }
            $ok = ($missing.Count -eq 0)
            $target = (@($Spec.events) -join ',')
            $detail = "fired=$($rep.FiredCount) missing=$(if ($missing.Count -eq 0) { 'none' } else { $missing -join ',' })"
            return New-FtAssertResult -Type 'dispatch_fired' -Target $target -Ok $ok -Detail $detail
        }
        'dispatch_zero_absent' {
            $rep = Get-FtDispatchReport -Text $text -Source 'last'
            if ($null -eq $rep) {
                return New-FtAssertResult -Type 'dispatch_zero_absent' -Target '(-)' -Ok $false -Detail ("side={0} 未找到派发报告" -f $side)
            }
            $bad = New-Object System.Collections.Generic.List[string]
            foreach ($e in @($Spec.events)) {
                if (@($rep.Idle) -contains [string]$e) { $bad.Add([string]$e) }
            }
            $ok = ($bad.Count -eq 0)
            $target = (@($Spec.events) -join ',')
            $detail = "idle=$($rep.IdleCount) zero_events=$(if ($bad.Count -eq 0) { 'none' } else { $bad -join ',' })"
            return New-FtAssertResult -Type 'dispatch_zero_absent' -Target $target -Ok $ok -Detail $detail
        }
        'jar' {
            $jar = ''
            if ($Spec.jar) { $jar = [string]$Spec.jar } else { $jar = Get-FtProductJar }
            if (-not $jar -or -not (Test-Path -LiteralPath $jar -PathType Leaf)) {
                return New-FtAssertResult -Type 'jar' -Target '(product jar)' -Ok $false -Detail 'jar 不存在（先跑 ft_build.ps1）'
            }
            $st = Get-FtJarStats -JarPath $jar
            $fails = New-Object System.Collections.Generic.List[string]
            if ($null -ne $Spec.PSObject.Properties['models_min'] -and $st.Models -lt [int]$Spec.models_min) { $fails.Add("models=$($st.Models)<$($Spec.models_min)") }
            if ($null -ne $Spec.PSObject.Properties['recipes_min'] -and $st.Recipes -lt [int]$Spec.recipes_min) { $fails.Add("recipes=$($st.Recipes)<$($Spec.recipes_min)") }
            if ($null -ne $Spec.PSObject.Properties['bountiful_max'] -and $st.Bountiful -gt [int]$Spec.bountiful_max) { $fails.Add("bountiful=$($st.Bountiful)>$($Spec.bountiful_max)") }
            if ($null -ne $Spec.PSObject.Properties['trinkets_min'] -and $st.Trinkets -lt [int]$Spec.trinkets_min) { $fails.Add("trinkets=$($st.Trinkets)<$($Spec.trinkets_min)") }
            if ($null -ne $Spec.PSObject.Properties['embed_lib'] -and [bool]$Spec.embed_lib) {
                $has = $false
                foreach ($n in $st.EmbedJars) { if ($n -like 'starengine_lib*') { $has = $true } }
                if (-not $has) { $fails.Add('embed_lib=false') }
            }
            $ok = ($fails.Count -eq 0)
            $detail = "jar=$($st.JarName) models=$($st.Models) recipes=$($st.Recipes) trinkets=$($st.Trinkets) bountiful=$($st.Bountiful) embed=$($st.EmbedJars.Count)"
            if (-not $ok) { $detail += " fails=$($fails -join ';')" }
            return New-FtAssertResult -Type 'jar' -Target $st.JarName -Ok $ok -Detail $detail
        }
        default {
            return New-FtAssertResult -Type $type -Target '(-)' -Ok $false -Detail "未知断言类型 '$type'"
        }
    }
}

function New-FtAssertResult {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Type,
        [Parameter(Mandatory)][string]$Target,
        [Parameter(Mandatory)][bool]$Ok,
        [Parameter(Mandatory)][AllowEmptyString()][string]$Detail
    )
    $line = ("AP_FAB_ASSERT: type={0} target={1} ok={2} {3}" -f $Type, $Target, $Ok.ToString().ToLowerInvariant(), $Detail)
    return [pscustomobject]@{ Type = $Type; Target = $Target; Ok = $Ok; Detail = $Detail; Line = $line }
}

# ══ CLI ═══════════════════════════════════════════════════════════════════
if ($MyInvocation.InvocationName -ne '.') {

    Initialize-FtConsole

    $Sub = ''
    $Pattern = ''
    $Side = 'server'
    $Window = 'case'
    $CasePath = ''
    $ArgList = @($args)
    $subIdx = -1

    # 子命令 = 第一个非 '-' 开头的 token
    for ($j = 0; $j -lt $args.Count; $j++) {
        if (-not ([string]$args[$j]).StartsWith('-')) { $Sub = ([string]$args[$j]).ToLowerInvariant(); $subIdx = $j; break }
    }
    if (-not $Sub) { $Sub = 'help' }

    $i = 0
    while ($i -lt $args.Count) {
        if ($i -eq $subIdx) { $i++; continue }
        $tok = [string]$args[$i]
        $key = Get-FtArgKey -Token $tok
        if ($key -eq 'pattern') {
            if ($i + 1 -ge $args.Count) { Write-FtErrorLine '缺少 --pattern 的值'; exit $FT_EXIT_ERROR }
            $Pattern = [string]$args[$i + 1]; $i += 2
        } elseif ($key -eq 'side') {
            if ($i + 1 -ge $args.Count) { Write-FtErrorLine '缺少 --side 的值'; exit $FT_EXIT_ERROR }
            $Side = ([string]$args[$i + 1]).ToLowerInvariant(); $i += 2
        } elseif ($key -eq 'window') {
            if ($i + 1 -ge $args.Count) { Write-FtErrorLine '缺少 --window 的值'; exit $FT_EXIT_ERROR }
            $Window = ([string]$args[$i + 1]).ToLowerInvariant(); $i += 2
        } elseif ($key -eq 'case') {
            if ($i + 1 -ge $args.Count) { Write-FtErrorLine '缺少 --case 的值'; exit $FT_EXIT_ERROR }
            $CasePath = [string]$args[$i + 1]; $i += 2
        } elseif ($key -eq 'nosnapshot') {
            $Window = 'whole'; $i++
        } elseif ($key -eq 'allowauto') {
            $i++
        } else {
            Write-FtErrorLine "未知参数 $tok"; exit $FT_EXIT_ERROR
        }
    }

    if ($Sub -eq 'help') {
        Write-FtLine '用法: ft_assert.ps1 <snapshot|log|absent|case> [选项]'
        Write-FtLine '  snapshot --side S [--window case|launch]'
        Write-FtLine '  log|absent --pattern RE [--side S] [--window case|launch|whole]'
        Write-FtLine '  case --case <cases/*.json> [--side S] [--window case|launch|whole]'
        exit $FT_EXIT_PASS
    }

    if ($Window -notin @('case', 'launch', 'whole')) {
        Write-FtErrorLine "非法 --window '$Window'（只接受 case | launch | whole）"
        exit $FT_EXIT_ERROR
    }

    if ($Sub -eq 'snapshot') {
        [void](Assert-FtSide -Side $Side)
        if ($Window -eq 'whole') { $Window = 'case' }
        $off = Save-FtSnapshot -Side $Side -Window $Window
        Write-FtLine ("AP_FAB_SNAPSHOT: side={0} window={1} cursor={2}" -f $Side, $Window, $off)
        Write-FtOk 'SNAPSHOT' ("side=$Side window=$Window cursor=$off")
        exit $FT_EXIT_PASS
    }

    if ($Sub -eq 'log' -or $Sub -eq 'absent') {
        if (-not $Pattern) { Write-FtErrorLine "--pattern 必填"; exit $FT_EXIT_ERROR }
        $spec = [pscustomobject]@{ type = $Sub; pattern = $Pattern; side = $Side; window = $Window }
        $r = Invoke-FtSpecAssert -Spec $spec -DefaultSide $Side -DefaultWindow $Window
        Write-FtLine $r.Line
        if ($r.Ok) { Write-FtOk 'ASSERT' $r.Detail; exit $FT_EXIT_PASS }
        Write-FtFail 'ASSERT' $r.Detail
        exit $FT_EXIT_FAIL
    }

    if ($Sub -eq 'case') {
        if (-not $CasePath) { Write-FtErrorLine '--case 必填'; exit $FT_EXIT_ERROR }
        if (-not (Test-Path -LiteralPath $CasePath -PathType Leaf)) {
            Write-FtErrorLine "用例文件不存在：$CasePath"; exit $FT_EXIT_ERROR
        }
        $case = Get-Content -LiteralPath $CasePath -Raw -Encoding UTF8 | ConvertFrom-Json
        $caseSide = $Side
        if ($case.side) { $caseSide = [string]$case.side }
        $fails = 0
        $total = 0
        foreach ($a in @($case.asserts)) {
            $total++
            $r = Invoke-FtSpecAssert -Spec $a -DefaultSide $caseSide -DefaultWindow $Window
            Write-FtLine $r.Line
            if (-not $r.Ok) { $fails++ }
        }
        Write-FtLine ("AP_FAB_ASSERT_CASE: case={0} asserts={1} fails={2}" -f $case.case_id, $total, $fails)
        if ($fails -eq 0) { Write-FtOk 'ASSERT' ("case=$($case.case_id) 全部 $total 条断言通过"); exit $FT_EXIT_PASS }
        Write-FtFail 'ASSERT' ("case=$($case.case_id) $fails/$total 条断言失败")
        exit $FT_EXIT_FAIL
    }

    Write-FtErrorLine "未知子命令 '$Sub'（只接受 snapshot | log | absent | case）"
    exit $FT_EXIT_ERROR
}
