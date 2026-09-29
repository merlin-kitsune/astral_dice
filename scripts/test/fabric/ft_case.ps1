#Requires -Version 7.0
<#
.SYNOPSIS
    ft_case — fabric-1.20.1 线的**单条**用例执行器（含批量编排闸门）。

.DESCRIPTION
    子命令：
      run     --case <cases/*.json>   跑一条用例（**放行**：手动驱动的一次执行）
      run-dir                         跑 cases/ 下全部用例（🚫 **被闸门拦下**：整目录批量）
      list                            列出 cases/ 下的用例及其断言数
      validate --case <json> | --all  校验用例 JSON（schema 检查，不碰游戏）
      help

    用例 schema（fabric 侧自定，字段说明见 README「用例格式」）：
      {
        "case_id": "FAB-XXX",           // 必填
        "title":   "一句话说明",          // 必填
        "side":    "server"|"client",   // 可选（默认 server）
        "window":  "case"|"launch"|"whole",  // 可选（默认 case）
        "steps":   [ … ],               // 可选
        "asserts": [ … ]                // 必填
      }
    step.op：inject_command（command/text/channel/no_esc）| wait（ms）| note（text）| snapshot（window）
    assert.type：log | absent | crash | dispatch_tick | dispatch_fired | dispatch_zero_absent | jar
    （类型与字段定义见 ft_assert.ps1 的 Invoke-FtSpecAssert）

    ⚠️ 与生产线 mt_case.ps1 的差异：不写「运行报告/进度信标」、不做逐条超时预算 ——
    那些服务于「无人值守批量跑」，而现行规则禁用的正是批量；单条用例由调用者手动发起，
    失败现场直接留在日志里（`on_fail` 字段本台不实现）。

.EXAMPLE
    pwsh -NoProfile -File scripts/test/fabric/ft_case.ps1 run --case scripts/test/fabric/cases/FAB-BOOT-EMBED.json
    pwsh -NoProfile -File scripts/test/fabric/ft_case.ps1 list
    pwsh -NoProfile -File scripts/test/fabric/ft_case.ps1 validate --all

.NOTES
    退出码：0 = PASS；1 = 断言失败；2 = ERROR（参数/用例非法、注入失败、闸门拦截）；12 = 超时（未实现，保留）。
#>

$ErrorActionPreference = 'Stop'

$script:FtLibDir = Join-Path $PSScriptRoot 'lib'
Import-Module (Join-Path $script:FtLibDir 'Ft.Common.psm1') -Force

# 复用断言引擎（dot-source 时其 CLI 分支被 InvocationName 守卫挡下）
. (Join-Path $PSScriptRoot 'ft_assert.ps1')

Initialize-FtConsole

$script:CasesDir = Join-Path (Get-FtSelfDir) 'cases'

function Get-FtCaseFiles {
    [CmdletBinding()]
    param()
    if (-not (Test-Path -LiteralPath $script:CasesDir -PathType Container)) { return @() }
    return @(Get-ChildItem -LiteralPath $script:CasesDir -File -Filter '*.json' | Sort-Object -Property Name)
}

function Test-FtCaseSpec {
    <#
    .SYNOPSIS
        校验一条用例 JSON；返回问题字符串数组（空数组 = 合法）。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][object]$Case, [string]$Path = '')

    $errs = New-Object System.Collections.Generic.List[string]
    if (-not $Case.case_id) { $errs.Add('缺 case_id') }
    if (-not $Case.title) { $errs.Add('缺 title') }
    if ($Case.side -and $Case.side -notin @('server', 'client')) { $errs.Add("side 非法：$($Case.side)") }
    if ($Case.window -and $Case.window -notin @('case', 'launch', 'whole')) { $errs.Add("window 非法：$($Case.window)") }

    $ops = @('inject_command', 'wait', 'note', 'snapshot')
    foreach ($s in @($Case.steps)) {
        if (-not $s.op) { $errs.Add('steps 里有一项缺 op'); continue }
        if ([string]$s.op -notin $ops) { $errs.Add("未知 step.op：$($s.op)") }
        if ($s.op -eq 'wait' -and -not $s.ms) { $errs.Add('wait 缺 ms') }
        if ($s.op -eq 'inject_command' -and -not ($s.command -or $s.text)) { $errs.Add('inject_command 缺 command/text') }
    }

    $atypes = @('log', 'absent', 'crash', 'dispatch_tick', 'dispatch_fired', 'dispatch_zero_absent', 'jar')
    if (-not $Case.asserts -or @($Case.asserts).Count -eq 0) { $errs.Add('asserts 为空') }
    foreach ($a in @($Case.asserts)) {
        if (-not $a.type) { $errs.Add('asserts 里有一项缺 type'); continue }
        if ([string]$a.type -notin $atypes) { $errs.Add("未知 assert.type：$($a.type)") }
        if (($a.type -eq 'log' -or $a.type -eq 'absent') -and -not $a.pattern) { $errs.Add("$($a.type) 缺 pattern") }
        if (($a.type -eq 'dispatch_fired' -or $a.type -eq 'dispatch_zero_absent') -and -not $a.events) { $errs.Add("$($a.type) 缺 events") }
    }
    return @($errs)
}

function Invoke-FtCase {
    <#
    .SYNOPSIS
        执行一条用例：逐步跑 steps（含命令注入）→ 跑 asserts → 汇总。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Path)

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        Write-FtErrorLine "用例文件不存在：$Path"
        return $FT_EXIT_ERROR
    }
    $case = Get-Content -LiteralPath $Path -Raw -Encoding UTF8 | ConvertFrom-Json
    $errs = Test-FtCaseSpec -Case $case -Path $Path
    if ($errs.Count -gt 0) {
        foreach ($e in $errs) { Write-FtErrLine "  · $e" }
        Write-FtErrorLine "用例非法：$Path"
        return $FT_EXIT_ERROR
    }

    $side = 'server'
    if ($case.side) { $side = [string]$case.side }
    $window = 'case'
    if ($case.window) { $window = [string]$case.window }

    Write-FtLine ("AP_FAB_CASE_BEGIN: case={0} side={1} window={2} steps={3} asserts={4}" -f `
            $case.case_id, $side, $window, @($case.steps).Count, @($case.asserts).Count)

    # 用例开始时打一个 case 窗口起点（与生产线 mt_case 的 `snapshot --window case` 同义）
    if ($window -eq 'case') { [void](Save-FtSnapshot -Side $side -Window 'case') }

    $stepNo = 0
    foreach ($s in @($case.steps)) {
        $stepNo++
        $op = [string]$s.op
        switch ($op) {
            'note' {
                Write-FtLine ("MT_FAB_NOTE[{0}]: {1}" -f $stepNo, ([string]$s.text -replace "`r?`n", ' / '))
            }
            'wait' {
                $ms = [int]$s.ms
                Write-FtLine ("MT_FAB_INFO: step[{0}] wait {1}ms" -f $stepNo, $ms)
                Start-Sleep -Milliseconds $ms
            }
            'snapshot' {
                $w = 'case'
                if ($s.window) { $w = [string]$s.window }
                $off = Save-FtSnapshot -Side $side -Window $w
                Write-FtLine ("MT_FAB_INFO: step[{0}] snapshot window={1} cursor={2}" -f $stepNo, $w, $off)
            }
            'inject_command' {
                $cmdText = [string]$s.command
                if (-not $cmdText) { $cmdText = [string]$s.text }
                $ch = 'rcon'
                if ($s.channel) { $ch = [string]$s.channel }
                Write-FtLine ("MT_FAB_INFO: step[{0}] inject (channel={1}) {2}" -f $stepNo, $ch, $cmdText)
                # 走 Invoke-FtChildProcess（Start-Process + 字节转发）而不是 `& pwsh -File …`：
                # 后者会把子进程 stdout 收进本函数的返回值 ⇒ `$rc = Invoke-FtCase …` 变成数组、
                # `exit $rc` 直接炸（实测 ft.ps1 打印过 rc=System.Object[]）。
                $rc = Invoke-FtChildProcess -ScriptPath (Join-Path $PSScriptRoot 'ft_inject.ps1') `
                    -ScriptArgs @('cmd', '--command', $cmdText, '--channel', $ch, '--side', $side)
                if ($rc -ne 0) {
                    Write-FtLine ("AP_FAB_CASE: ERROR (case={0} reason=inject-failed step={1} rc={2})" -f $case.case_id, $stepNo, $rc)
                    Write-FtError 'CASE' ("注入失败（step $stepNo, rc=$rc）—— 用例中止（命令缺失必须显式失败，绝不静默降级）")
                    return $FT_EXIT_ERROR
                }
            }
            default {
                Write-FtErrorLine "未知 step.op：$op"
                return $FT_EXIT_ERROR
            }
        }
    }

    $total = 0
    $fails = 0
    foreach ($a in @($case.asserts)) {
        $total++
        $r = Invoke-FtSpecAssert -Spec $a -DefaultSide $side -DefaultWindow $window
        Write-FtLine $r.Line
        if (-not $r.Ok) { $fails++ }
    }

    Write-FtLine ("AP_FAB_CASE: case={0} asserts={1} fails={2}" -f $case.case_id, $total, $fails)
    if ($fails -eq 0) {
        Write-FtOk 'CASE' ("$($case.case_id) PASS（$total 条断言）")
        return $FT_EXIT_PASS
    }
    Write-FtFail 'CASE' ("$($case.case_id) FAIL（$fails/$total 条断言不满足）")
    return $FT_EXIT_FAIL
}

# ══ CLI ═══════════════════════════════════════════════════════════════════
$Sub = ''
$CasePath = ''
$ValidateAll = $false
$ArgList = @($args)
$subIdx = -1
for ($j = 0; $j -lt $args.Count; $j++) {
    if (-not ([string]$args[$j]).StartsWith('-')) { $Sub = ([string]$args[$j]).ToLowerInvariant(); $subIdx = $j; break }
}
if (-not $Sub) { $Sub = 'help' }

$i = 0
while ($i -lt $args.Count) {
    if ($i -eq $subIdx) { $i++; continue }
    $tok = [string]$args[$i]
    $key = Get-FtArgKey -Token $tok
    if ($key -eq 'case') {
        if ($i + 1 -ge $args.Count) { Write-FtErrorLine '缺少 --case 的值'; exit $FT_EXIT_ERROR }
        $CasePath = [string]$args[$i + 1]; $i += 2
    } elseif ($key -eq 'all') {
        $ValidateAll = $true; $i++
    } elseif ($key -eq 'allowauto') {
        $i++
    } elseif ($key -eq 'h' -or $key -eq 'help') {
        Write-FtLine '用法: ft_case.ps1 <run|run-dir|list|validate> [--case <json>] [--all]'
        exit $FT_EXIT_PASS
    } else {
        Write-FtErrorLine "未知参数 $tok"; exit $FT_EXIT_ERROR
    }
}

switch ($Sub) {
    'help' {
        Write-FtLine '用法: ft_case.ps1 <run|run-dir|list|validate> [--case <json>] [--all]'
        Write-FtLine '  run     --case <json>    单条用例（放行）'
        Write-FtLine '  run-dir                  整目录批量（🚫 被闸门拦下）'
        Write-FtLine '  list                     列出用例'
        Write-FtLine '  validate --case <json> | --all'
        exit $FT_EXIT_PASS
    }
    'run-dir' {
        # 🚫 闸门：整目录批量（对应 TESTING-RULES-OVERVIEW.md §3.1 的 `mt_case.ps1 run-dir`）
        Assert-FtAutoGate -What 'run-dir（自动跑完 cases/ 下全部用例）' -ArgList $ArgList
        Write-FtErrorLine 'run-dir 即使放行也未实现：现行范式是逐条 `run --case <X>`。'
        exit $FT_EXIT_ERROR
    }
    'run' {
        if (-not $CasePath) {
            # 🚫 闸门：run 未指定 --case（等价于走目录 = 批量）
            Assert-FtAutoGate -What 'run 未指定 --case（会退化为整目录批量）' -ArgList $ArgList
            Write-FtErrorLine 'run 必须显式给出 --case <json>。'
            exit $FT_EXIT_ERROR
        }
        $rc = Invoke-FtCase -Path $CasePath
        exit $rc
    }
    'list' {
        $files = Get-FtCaseFiles
        if ($files.Count -eq 0) { Write-FtLine 'AP_FAB_CASES: count=0'; exit $FT_EXIT_PASS }
        foreach ($f in $files) {
            try {
                $c = Get-Content -LiteralPath $f.FullName -Raw -Encoding UTF8 | ConvertFrom-Json
                Write-FtLine ("AP_FAB_CASE_ENTRY: id={0} side={1} asserts={2} steps={3} path={4}" -f `
                        $c.case_id, $(if ($c.side) { $c.side } else { 'server' }), @($c.asserts).Count, @($c.steps).Count, $f.FullName)
            } catch {
                Write-FtLine ("AP_FAB_CASE_ENTRY: id=? json_error={0} path={1}" -f $_.Exception.Message, $f.FullName)
            }
        }
        Write-FtLine ("AP_FAB_CASES: count={0}" -f $files.Count)
        exit $FT_EXIT_PASS
    }
    'validate' {
        $targets = @()
        if ($ValidateAll) { $targets = @(Get-FtCaseFiles | ForEach-Object { $_.FullName }) }
        elseif ($CasePath) { $targets = @($CasePath) }
        else { Write-FtErrorLine 'validate 需要 --case <json> 或 --all'; exit $FT_EXIT_ERROR }

        if ($targets.Count -eq 0) { Write-FtErrorLine '没有可校验的用例'; exit $FT_EXIT_ERROR }
        $bad = 0
        foreach ($t in $targets) {
            if (-not (Test-Path -LiteralPath $t -PathType Leaf)) {
                Write-FtLine ("AP_FAB_CASE_VALIDATE: path={0} ok=false reason=missing" -f $t); $bad++; continue
            }
            try {
                $c = Get-Content -LiteralPath $t -Raw -Encoding UTF8 | ConvertFrom-Json
            } catch {
                Write-FtLine ("AP_FAB_CASE_VALIDATE: path={0} ok=false reason=json" -f $t); $bad++; continue
            }
            $errs = Test-FtCaseSpec -Case $c -Path $t
            if ($errs.Count -eq 0) {
                Write-FtLine ("AP_FAB_CASE_VALIDATE: id={0} ok=true path={1}" -f $c.case_id, $t)
            } else {
                $bad++
                Write-FtLine ("AP_FAB_CASE_VALIDATE: id={0} ok=false problems={1} path={2}" -f $c.case_id, ($errs -join ';'), $t)
            }
        }
        Write-FtLine ("AP_FAB_CASE_VALIDATE_SUMMARY: total={0} bad={1}" -f $targets.Count, $bad)
        if ($bad -eq 0) { Write-FtOk 'CASE_VALIDATE' "$($targets.Count) 条用例 schema 合法"; exit $FT_EXIT_PASS }
        Write-FtFail 'CASE_VALIDATE' "$bad/$($targets.Count) 条用例不合法"
        exit $FT_EXIT_FAIL
    }
    default {
        Write-FtErrorLine "未知子命令 '$Sub'（只接受 run | run-dir | list | validate）"
        exit $FT_EXIT_ERROR
    }
}
