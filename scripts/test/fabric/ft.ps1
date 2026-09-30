#Requires -Version 7.0
<#
.SYNOPSIS
    ft.ps1 — fabric-1.20.1 线测试台的**阶段编排入口**（含批量编排闸门）。

.DESCRIPTION
    与生产线 `scripts/test/mt.ps1` 同构的入口，但只服务**一条线**（fabric-1.20.1），
    且把 TESTING-RULES-OVERVIEW.md §3.1「禁用批量编排」的三处拦截在 fabric 侧原样落地：

      🚫 无 --phase                    → 拦（会 build→env→launch→case→report 一路自动跑完）
      🚫 --phase case 且无 --case      → 拦（走目录 = 自动跑完该线全部用例）
      🚫 --phase report                → 拦（把自动跑出来的结果收集成报告）
      ✅ build / env / launch / stop   → 放行（把运行环境弄起来/收停所需的基础设施）
      ✅ --phase case --case <X>       → 放行（手动驱动的一次执行）

    明确 **N/A** 的两项（fabric 单版本，不存在对应维度；若将来引入须同样受闸门约束）：
      · 「--phase <p> 无 --version（对三线顺序批量执行）」——本线只有一条；
      · 「watchdog -Action stop（无人值守停滞收停）」——本台不提供 watchdog。

    拦下时：stderr 首行 `MT_AUTO_DISABLED: …`，退出码 **2**。
    临放行：追加 `--allow-auto`，或设环境变量 `MT_ALLOW_AUTO=1`。

.PARAMETER Phase
    build | env | launch | stop | case | report。

.PARAMETER Case
    `--phase case` 必填：单条用例 JSON 路径。

.EXAMPLE
    pwsh -NoProfile -File scripts/test/fabric/ft.ps1 --phase build
    pwsh -NoProfile -File scripts/test/fabric/ft.ps1 --phase env --side server --enable-rcon
    pwsh -NoProfile -File scripts/test/fabric/ft.ps1 --phase launch --side server
    pwsh -NoProfile -File scripts/test/fabric/ft.ps1 --phase case --case scripts/test/fabric/cases/FAB-DISPATCH-BASIC.json
    pwsh -NoProfile -File scripts/test/fabric/ft.ps1 --phase stop --side server

.NOTES
    退出码：透传子脚本退出码；闸门拦截为 2。
#>

$ErrorActionPreference = 'Stop'

$script:FtLibDir = Join-Path $PSScriptRoot 'lib'
Import-Module (Join-Path $script:FtLibDir 'Ft.Common.psm1') -Force

Initialize-FtConsole

function Invoke-FtChild {
    <#
    .SYNOPSIS
        跑一个同级子脚本，字节透传其输出，只返回退出码。
    .NOTES
        实现 = Ft.Common 的 Invoke-FtChildProcess（Start-Process + 字节转发）。
        为什么不用 `& pwsh -File …`：见 Ft.Common.psm1 内该函数的注释（会被管道吞掉输出、
        且 `>` 重定向会二次解码把中文变成乱码 —— 两者都实测踩过）。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Script, [string[]]$ScriptArgs = @())

    return (Invoke-FtChildProcess -ScriptPath (Join-Path $PSScriptRoot $Script) -ScriptArgs $ScriptArgs)
}

# ══ 参数 ══════════════════════════════════════════════════════════════════
$Phase = ''
$CasePath = ''
$ArgList = @($args)
$passThrough = New-Object System.Collections.Generic.List[string]

$i = 0
while ($i -lt $args.Count) {
    $tok = [string]$args[$i]
    $key = Get-FtArgKey -Token $tok
    if ($key -eq 'phase') {
        if ($i + 1 -ge $args.Count) { Write-FtErrorLine '缺少 --phase 的值'; exit $FT_EXIT_ERROR }
        $Phase = ([string]$args[$i + 1]).ToLowerInvariant(); $i += 2
        continue
    }
    if ($key -eq 'case') {
        if ($i + 1 -ge $args.Count) { Write-FtErrorLine '缺少 --case 的值'; exit $FT_EXIT_ERROR }
        $CasePath = [string]$args[$i + 1]; $passThrough.Add($tok); $passThrough.Add([string]$args[$i + 1]); $i += 2
        continue
    }
    if ($key -eq 'allowauto') {
        # 只吃下来用于放行判定，不往子脚本透传
        $i++
        continue
    }
    if ($key -eq 'h' -or $key -eq 'help') {
        Write-FtLine '用法: ft.ps1 --phase <build|env|launch|stop|case|report> [子脚本参数…] [--allow-auto]'
        exit $FT_EXIT_PASS
    }
    $passThrough.Add($tok)
    $i++
}

# ══ 闸门 ═════════════════════════════════════════════════════════════════
if (-not $Phase) {
    Assert-FtAutoGate -What '全流程（无 --phase；会 build→env→launch→case→report 顺序自动跑完）' -ArgList $ArgList
    Write-FtErrorLine '请显式指定 --phase（build|env|launch|stop|case|report）。'
    exit $FT_EXIT_ERROR
}
if ($Phase -eq 'case' -and -not $CasePath) {
    Assert-FtAutoGate -What '--phase case 未指定 --case（走目录，自动跑完该线全部用例）' -ArgList $ArgList
    Write-FtErrorLine '--phase case 必须带 --case <cases/*.json>。'
    exit $FT_EXIT_ERROR
}
if ($Phase -eq 'report') {
    Assert-FtAutoGate -What '--phase report（收集自动跑出来的结果成报告）' -ArgList $ArgList
}

# ══ 分发 ═════════════════════════════════════════════════════════════════
$phaseNames = @('build', 'env', 'launch', 'stop', 'case', 'report')
if ($Phase -notin $phaseNames) {
    Write-FtErrorLine "未知阶段 '$Phase'（只接受 $($phaseNames -join ' | ')）"
    exit $FT_EXIT_ERROR
}

Write-FtLine ("AP_FAB_PHASE_BEGIN: phase={0}" -f $Phase)

switch ($Phase) {
    'build'  { $rc = Invoke-FtChild -Script 'ft_build.ps1'  -ScriptArgs @($passThrough) }
    'env'    { $rc = Invoke-FtChild -Script 'ft_env.ps1'    -ScriptArgs @($passThrough) }
    'launch' { $rc = Invoke-FtChild -Script 'ft_launch.ps1' -ScriptArgs @($passThrough) }
    'stop'   { $rc = Invoke-FtChild -Script 'ft_stop.ps1'   -ScriptArgs @($passThrough) }
    'case'   { $rc = Invoke-FtChild -Script 'ft_case.ps1'   -ScriptArgs (@('run') + @($passThrough)) }
    'report' {
        # 仅在 --allow-auto 下可达（默认被闸门拦下）。
        $ts = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
        $dir = Join-Path (Join-Path (Get-FtSelfDir) 'reports') "$ts"
        [void](New-Item -ItemType Directory -Force -Path $dir)
        $lines = New-Object System.Collections.Generic.List[string]
        $lines.Add("# fabric-1.20.1 测试台报告（$ts）")
        $lines.Add('')
        foreach ($side in @('server', 'client')) {
            $log = Get-FtLogPath -Side $side
            $lines.Add("## $side")
            $lines.Add('')
            $lines.Add("- 日志：$log")
            if (Test-Path -LiteralPath $log -PathType Leaf) {
                $txt = Read-FtLogText -Path $log
                $ap = @($txt -split "`r?`n" | Where-Object { $_ -match 'AP_FAB_' })
                $lines.Add("- AP_FAB_ 读数行：$($ap.Count) 条")
                $lines.Add('')
                $lines.Add('```')
                foreach ($l in $ap) { $lines.Add($l.Trim()) }
                $lines.Add('```')
                # 派发报告的 0 次清单（本线最重要的护栏）也一并落盘
                $lines.Add('')
                $lines.Add('派发统计（原始块）：')
                $lines.Add('```')
                foreach ($l in @($txt -split "`r?`n" | Where-Object { $_ -match '事件派发统计|\[已派发|\[未派发' })) { $lines.Add($l.Trim()) }
                $lines.Add('```')
            } else {
                $lines.Add('- 日志不存在')
            }
            $lines.Add('')
        }
        $md = Join-Path $dir 'report.md'
        [System.IO.File]::WriteAllText($md, ($lines -join "`n"), [System.Text.UTF8Encoding]::new($false))
        Write-FtLine ("AP_FAB_REPORT: dir={0} file={1}" -f $dir, $md)
        Write-FtOk 'REPORT' $md
        $rc = $FT_EXIT_PASS
    }
}

Write-FtLine ("AP_FAB_PHASE: {0} rc={1}" -f $Phase, $rc)
exit $rc
