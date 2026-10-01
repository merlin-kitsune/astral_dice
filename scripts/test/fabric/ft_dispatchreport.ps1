#Requires -Version 7.0
<#
.SYNOPSIS
    ft_dispatchreport — 抽取并格式化 LoaderBus#dispatchReport()，**把 0 次派发的事件类单独列出**。

.DESCRIPTION
    为什么这是 fabric 线**最重要的一条回归护栏**（本线专属，三条生产线没有对应物）：
      fabric 侧没有 Forge 的 `@Mod.EventBusSubscriber` 自动注册，事件靠「自建 LoaderBus +
      FAPI 回调 + Puzzles Lib + 自写 mixin」四路桥接（platform/FabricBridges、
      platform/PuzzlesBridges、platform/client/FabricClientBridges、mixin/bridge/**）。
      一旦某条桥漏接（例如 `FabricBridges.install()` 根本没被调用 —— 本项目 2026-09-29 真踩过），
      事件会**静默永不触发**：编译过、启动过、无任何报错，只在玩法上表现为「某个立牌/筹码没反应」。
      `LoaderBus#dispatchReport()` 是唯一能把这种静默失效变成**可断言读数**的手段：
      它列出**每个已注册事件类的派发次数，0 次的也列**（依据 platform/event/LoaderBus.java:67-89）。

    硬判据（依据 LoaderBus.java:62-66 的注释口径）：`ServerTickEvent` 必须为正数 ——
    它由 FAPI 的 tick 回调驱动，走的是与所有其它事件**同一条 post 路径**
    ⇒ tick 有计数就说明「桥是通的」；其余为 0 通常只是「本次没有触发该交互」（无玩家、无伤害等），
    属正常，但**必须显式列出**以便与「桥漏接」区分。

.PARAMETER Side
    server | client（默认 server）。客户端侧报告由 platform/client/FabricClientBridges 打印。

.PARAMETER File
    直接指定日志文件（默认 run/<side>/logs/latest.log）。

.PARAMETER Source
    last（默认）| first | all —— 一次会话里通常有两份报告：「开局 600 tick」与「关服」。
    默认取 last（关服那份更完整；强杀进程时则只剩 600 tick 那份）。

.PARAMETER Window
    whole（默认整文件，启动期事实）| case | launch —— 与 ft_assert 同一套窗口。

.PARAMETER NoRequireTick
    不把「ServerTickEvent 为正数」当硬判据（对照/排查用）。

.EXAMPLE
    pwsh -NoProfile -File scripts/test/fabric/ft_dispatchreport.ps1
    pwsh -NoProfile -File scripts/test/fabric/ft_dispatchreport.ps1 --side client
    pwsh -NoProfile -File scripts/test/fabric/ft_dispatchreport.ps1 --source all

.NOTES
    输出（stdout，全部机器可读）：
      AP_FAB_DISPATCH: source=… fired=<n> idle=<m>
      AP_FAB_DISPATCH_FIRED: <SimpleName>=<count>        （每类一行）
      AP_FAB_DISPATCH_ZERO: <SimpleName>                 （每类一行 —— 回归护栏主体）
      AP_FAB_DISPATCH_ZERO_LIST: <a> <b> …               （单行汇总，便于 diff）
      AP_FAB_DISPATCH_TICK: ServerTickEvent=<n> ok=<true|false>
      AP_FAB_DISPATCH_RESULT: OK|FAIL
    退出码：0 = 有报告且（默认）tick 为正；1 = FAIL（无报告 / tick=0）；2 = 参数错误。
#>

$ErrorActionPreference = 'Stop'

$script:FtLibDir = Join-Path $PSScriptRoot 'lib'
Import-Module (Join-Path $script:FtLibDir 'Ft.Common.psm1') -Force

# 复用 ft_assert.ps1 的解析函数（dot-source 时其 CLI 分支被 `InvocationName -ne '.'` 守卫挡下）
. (Join-Path $PSScriptRoot 'ft_assert.ps1')

Initialize-FtConsole

# ══ 参数 ══════════════════════════════════════════════════════════════════
$Side = 'server'
$FileArg = ''
$Source = 'last'
$Window = 'whole'
$RequireTick = $true

$i = 0
while ($i -lt $args.Count) {
    $tok = [string]$args[$i]
    $key = Get-FtArgKey -Token $tok
    if ($key -eq 'side') {
        if ($i + 1 -ge $args.Count) { Write-FtErrorLine '缺少 --side 的值'; exit $FT_EXIT_ERROR }
        $Side = ([string]$args[$i + 1]).ToLowerInvariant(); $i += 2
    } elseif ($key -eq 'file') {
        if ($i + 1 -ge $args.Count) { Write-FtErrorLine '缺少 --file 的值'; exit $FT_EXIT_ERROR }
        $FileArg = [string]$args[$i + 1]; $i += 2
    } elseif ($key -eq 'source') {
        if ($i + 1 -ge $args.Count) { Write-FtErrorLine '缺少 --source 的值'; exit $FT_EXIT_ERROR }
        $Source = ([string]$args[$i + 1]).ToLowerInvariant(); $i += 2
    } elseif ($key -eq 'window') {
        if ($i + 1 -ge $args.Count) { Write-FtErrorLine '缺少 --window 的值'; exit $FT_EXIT_ERROR }
        $Window = ([string]$args[$i + 1]).ToLowerInvariant(); $i += 2
    } elseif ($key -eq 'norequiretick') {
        $RequireTick = $false; $i++
    } elseif ($key -eq 'allowauto') {
        $i++
    } elseif ($key -eq 'h' -or $key -eq 'help') {
        Write-FtLine '用法: ft_dispatchreport.ps1 [--side server|client] [--file <log>] [--source last|first|all]'
        Write-FtLine '                              [--window whole|case|launch] [--no-require-tick]'
        exit $FT_EXIT_PASS
    } else {
        Write-FtErrorLine "未知参数 $tok"; exit $FT_EXIT_ERROR
    }
}

if ($Source -notin @('last', 'first', 'all')) {
    Write-FtErrorLine "非法 --source '$Source'（只接受 last | first | all）"; exit $FT_EXIT_ERROR
}
if ($Window -notin @('whole', 'case', 'launch')) {
    Write-FtErrorLine "非法 --window '$Window'（只接受 whole | case | launch）"; exit $FT_EXIT_ERROR
}

if ($FileArg) {
    $text = Read-FtLogText -Path $FileArg
    Write-FtLine ("AP_FAB_DISPATCH_SOURCE: file={0}" -f $FileArg)
} else {
    [void](Assert-FtSide -Side $Side)
    $text = Get-FtLogWindow -Side $Side -Window $Window
    Write-FtLine ("AP_FAB_DISPATCH_SOURCE: file={0} window={1}" -f (Get-FtLogPath -Side $Side), $Window)
}

$rep = Get-FtDispatchReport -Text $text -Source $Source
if ($null -eq $rep) {
    Write-FtLine 'AP_FAB_DISPATCH: FAIL (reason=no-report)'
    Write-FtLine 'AP_FAB_DISPATCH_RESULT: FAIL'
    Write-FtFail 'DISPATCH' '日志里找不到 `事件派发统计` 报告块（FabricBridges.installEarly() 未执行？服务端未跑到 600 tick？见 run/server/logs/latest.log）'
    exit $FT_EXIT_FAIL
}

if ($Source -eq 'all') {
    # 模式 all：逐份报告都打一遍（带序号）
    $idx = 0
    foreach ($b in @($rep)) {
        $idx++
        Write-FtLine ("AP_FAB_DISPATCH: source=#{0} fired={1} idle={2} header={3}" -f $idx, $b.FiredCount, $b.IdleCount, $b.Header.Trim())
        foreach ($k in @($b.Fired.Keys | Sort-Object)) { Write-FtLine ("AP_FAB_DISPATCH_FIRED: {0}={1}" -f $k, $b.Fired[$k]) }
        foreach ($n in @($b.Idle)) { Write-FtLine ("AP_FAB_DISPATCH_ZERO: {0}" -f $n) }
        Write-FtLine ("AP_FAB_DISPATCH_ZERO_LIST: {0}" -f (@($b.Idle) -join ' '))
    }
    Write-FtLine 'AP_FAB_DISPATCH_RESULT: OK'
    Write-FtOk 'DISPATCH' ("共 {0} 份报告" -f @($rep).Count)
    exit $FT_EXIT_PASS
}

Write-FtLine ("AP_FAB_DISPATCH: source={0} fired={1} idle={2}" -f $Source, $rep.FiredCount, $rep.IdleCount)
Write-FtLine ("AP_FAB_DISPATCH_HEADER: {0}" -f $rep.Header.Trim())

Write-FtLine "-- 已派发（正数）--"
foreach ($k in @($rep.Fired.Keys | Sort-Object)) {
    Write-FtLine ("AP_FAB_DISPATCH_FIRED: {0}={1}" -f $k, $rep.Fired[$k])
}

Write-FtLine "-- 未派发（0 次；**本线最重要的回归护栏**）--"
foreach ($n in @($rep.Idle)) {
    Write-FtLine ("AP_FAB_DISPATCH_ZERO: {0}" -f $n)
}
Write-FtLine ("AP_FAB_DISPATCH_ZERO_LIST: {0}" -f (@($rep.Idle) -join ' '))
Write-FtLine ("AP_FAB_DISPATCH_ZERO_COUNT: {0}" -f $rep.IdleCount)

$tick = 0
if ($rep.Fired.ContainsKey('ServerTickEvent')) { $tick = [int]$rep.Fired['ServerTickEvent'] }
$tickOk = ($tick -gt 0)
Write-FtLine ("AP_FAB_DISPATCH_TICK: ServerTickEvent={0} ok={1}" -f $tick, $tickOk.ToString().ToLowerInvariant())

if ($RequireTick -and -not $tickOk) {
    Write-FtLine 'AP_FAB_DISPATCH_RESULT: FAIL'
    Write-FtFail 'DISPATCH' ('ServerTickEvent=' + $tick + ' —— 桥未被接通（FAPI tick 回调 → LoaderBus.post 这条路径没走通）')
    exit $FT_EXIT_FAIL
}

Write-FtLine 'AP_FAB_DISPATCH_RESULT: OK'
Write-FtOk 'DISPATCH' ("fired={0} idle={1} ServerTickEvent={2}" -f $rep.FiredCount, $rep.IdleCount, $tick)
exit $FT_EXIT_PASS
