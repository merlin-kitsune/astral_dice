#!/usr/bin/env pwsh
<#
mt_ime.ps1 — 目标窗口输入语言（键盘布局）管理。

对应源文件：scripts/test/mt_ime.py（1:1 移植；python 原件保留，迁移期用于逐字节比对）。

## 为什么需要它

注入通道按**美式扫描码表**投递（见 mt_inject.ps1 的 SCAN/VK）。旧流程把「系统输入法
必须是 英语(美国)」写成硬前置，要靠人手动切 —— 这既脆弱（忘记切就整轮失败），
又会在流程中途给人增加一步操作。

本脚本把这件事变成程序化、可验证的动作：**只切目标窗口所在线程的输入语言**，
不触碰用户的系统默认输入法与全局设置。

## 关键性质

- `GetKeyboardLayout` / `ActivateKeyboardLayout` 作用于**线程**，不是全局；
- 因此切完只影响被测游戏客户端，用户在别处的输入法不受影响（「不与现有功能冲突」）；
- 线程随进程退出而销毁，所以**无需恢复**：游戏进程结束，改动自然消失；
- 不会调用 `SystemParametersInfo(SPI_SETDEFAULTINPUTLANG)` 之类的全局接口。

## 用法

  mt_ime.ps1 list                          # 已加载布局 + en-US 可用性
  mt_ime.ps1 status -Version 1.21.1        # 目标窗口线程当前布局
  mt_ime.ps1 ensure -Version 1.21.1        # 切到 en-US（幂等）
  mt_ime.ps1 selftest                      # 跨进程自检（建临时窗口验证切换真实生效）

## 退出码（与 python 版一致）

  list     : 0 = en-US 可用；2 = 不可用
  status   : 0 = 已是 en-US；1 = 不是；2 = 未找到窗口
  ensure   : 0 = 成功；1 = 失败
  selftest : 0 = 全通过；1 = 有项不符
  _hostwin : 0 正常；2 = 注册/建窗失败

## 与 python 版的差异（有意为之，逐条）

1. **非 Windows 分支已删除**。本仓库测试链已收敛到「仅 Windows」（Mt.Proc.psm1 的
   POSIX 分支同样被移除），且本脚本依赖只存在于 Windows 的 user32 —— 在非 Windows 上
   连模块都导入不了。python 版那三段 `sys.platform != "win32"` 判断随之失效。
2. **参数错误走裸 `MT_ERROR:` + 退出码 2**，与 python 的 argparse（usage + exit 2）
   退出码一致；文本形态按迁移约定统一为 `MT_ERROR: …`（python 这侧没有 MT_ERROR，
   它打的是 argparse 的 usage）。合法参数路径的输出与 python **逐字节相同**。
3. **参数解析不用 `param()`，改用手写 `$args` 循环**（与 mt.ps1 / mt_env.ps1 /
   mt_cleanup.ps1 / mt_stop.ps1 一致）。原因是 `param()` 有两个硬伤：
     · python 的 **kebab 长选项**（`--list-latest` / `--no-esc` / `--hold-ms`）在
       PowerShell 参数名里不合法（不能含连字符）→ 绑定器直接拒绝；
     · 未知参数在 `param()` 下是绑定器报错（**exit 1**），而本仓约定是
       `MT_ERROR: 未知参数 <x>` + **exit 2**（与 python argparse 一致）。
   归一化规则：去掉前导 `-` 后**删除全部连字符**再小写比较 ⇒ `--hold-ms`、
   `-HoldMs`、`--hold_ms` 三种拼写等价，两种调用风格都能吃下。
#>

$ErrorActionPreference = 'Stop'

$script:MtLibDir = Join-Path $PSScriptRoot 'lib'
Import-Module (Join-Path $script:MtLibDir 'Mt.Phase.psm1') -Force
Import-Module (Join-Path $script:MtLibDir 'Mt.Paths.psm1') -Force
Import-Module (Join-Path $script:MtLibDir 'Mt.Proc.psm1') -Force
Import-Module (Join-Path $script:MtLibDir 'Mt.Win32.psm1') -Force

# ⚠️ 必须第一件事：不设 UTF-8 输出编码时中文会按本机码页(936/GBK)写出，与 python 版不等
Initialize-MtConsole

$script:MtImeSelfPath = $PSCommandPath
$script:Utf8NoBom = [System.Text.UTF8Encoding]::new($false)

# ══ 布局查询 ══════════════════════════════════════════════════════════════

function Invoke-MtImeList {
    <#
    .SYNOPSIS
        列出已加载布局 + en-US 可用性（对应 python cmd_list）。
    #>
    [CmdletBinding()]
    param()

    $layouts = @(Get-MtKeyboardLayouts)
    Write-MtLine ('已加载布局 {0} 个：' -f $layouts.Count)
    foreach ($l in $layouts) {
        $mark = ''
        if ($l.LangId -eq 0x0409) { $mark = '   ← en-US' }
        Write-MtLine ('    HKL=0x{0:X8}  LANGID=0x{1:X4}  KLID={2}{3}' -f $l.Hkl, $l.LangId, $l.Tag, $mark)
    }
    $ok = Test-MtEnUsLayoutAvailable
    Write-MtLine ''
    if ($ok) {
        Write-MtLine 'en-US(00000409) 可用：是'
        return 0
    }
    Write-MtLine 'en-US(00000409) 可用：否 —— 需在系统设置安装'
    return 2
}

# ══ 状态查询 ══════════════════════════════════════════════════════════════

function Invoke-MtImeStatus {
    <#
    .SYNOPSIS
        打印目标窗口线程当前输入语言（对应 python cmd_status）。
    #>
    [CmdletBinding()]
    param([string]$Version, [long]$Hwnd)

    $h = $Hwnd
    if (-not $h) { $h = Find-MtMinecraftWindow -Version $Version }
    if (-not $h) {
        # ⚠️ python 这里是 print(..., file=sys.stderr) 的 `MT_IME: ERROR — …`，
        #    既不是裸 MT_ERROR，也不是模块的 MT_IME: ERROR (<elapsed>) 形态 —— 只能直写。
        Write-MtErrLine 'MT_IME: ERROR — 未找到目标窗口'
        return 2
    }
    $tid = Get-MtThreadOfWindow -Hwnd $h
    $langid = Get-MtLangIdOfThread -ThreadId $tid
    Write-MtLine ('MT_IME: hwnd={0} tid={1} langid=0x{2:X4}（{3}）' -f `
            $h, $tid, $langid, (Get-MtLayoutDescription -LangId $langid))
    if ($langid -eq 0x0409) { return 0 }
    return 1
}

# ══ 切换 ══════════════════════════════════════════════════════════════════

function Invoke-MtImeEnsureForWindow {
    <#
    .SYNOPSIS
        注入前置：确保目标窗口线程为 en-US（对应 python ensure_for_window）。
    .OUTPUTS
        PSCustomObject：Ok / Message。
    #>
    [CmdletBinding()]
    param([string]$Version, [long]$Hwnd)

    $h = $Hwnd
    if (-not $h) { $h = Find-MtMinecraftWindow -Version $Version }
    if (-not $h) {
        return [pscustomobject]@{ Ok = $false; Message = '未找到目标窗口（或存在多个候选客户端）' }
    }
    return (Set-MtWindowUs -Hwnd $h)
}

function Invoke-MtImeEnsure {
    <#
    .SYNOPSIS
        切到 en-US（幂等），打印 MT_IME_ENSURE 行（对应 python cmd_ensure）。
    #>
    [CmdletBinding()]
    param([string]$Version, [long]$Hwnd)

    $r = Invoke-MtImeEnsureForWindow -Version $Version -Hwnd $Hwnd
    $tag = if ($r.Ok) { 'OK' } else { 'FAIL' }
    Write-MtLine ('MT_IME_ENSURE: {0} — {1}' -f $tag, $r.Message)
    if ($r.Ok) { return 0 }
    return 1
}

# ══ 自检：跨进程验证切换真的生效 ══════════════════════════════════════════

function Invoke-MtImeHostWin {
    <#
    .SYNOPSIS
        自检用的宿主进程：建一个真实窗口并持续汇报自身线程的输入语言
        （对应 python _hostwin，消息泵与窗口类注册必须完整保留）。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Report)

    $className = 'mtImeSelfTestWnd'
    $hinst = Get-MtModuleHandle
    if (-not (Register-MtMessageClass -ClassName $className -HInstance $hinst)) {
        Write-MtLine 'REGISTER_FAIL'
        return 2
    }

    $hwnd = New-MtMessageWindow -ClassName $className -Title 'mt-ime-selftest' `
        -Width 200 -Height 200 -HInstance $hinst
    if (-not $hwnd) {
        Write-MtLine 'CREATE_FAIL'
        return 2
    }

    $tid = Get-MtThreadOfWindow -Hwnd $hwnd
    # python: report.write_text("", encoding="utf-8") —— 报告文件截断重来
    [System.IO.File]::WriteAllText($Report, '', $script:Utf8NoBom)
    Write-MtLine ('HWND={0} TID={1}' -f $hwnd, $tid)

    $deadline = [DateTime]::UtcNow.AddSeconds(40)
    $seen = @()
    while ([DateTime]::UtcNow -lt $deadline) {
        [void](Invoke-MtPumpMessages)
        $langid = Get-MtLangIdOfThread -ThreadId $tid
        if ($seen.Count -eq 0 -or $seen[-1] -ne $langid) {
            $seen += $langid
            # python: time.time() 的 '%.3f'（UTC 纪元秒 + 毫秒，恒用 '.' 作小数点）
            $ts = ([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() / 1000.0).ToString('0.000', [cultureinfo]::InvariantCulture)
            [System.IO.File]::AppendAllText($Report, ('{0} 0x{1:X4}' -f $ts, $langid) + "`n", $script:Utf8NoBom)
        }
        Start-Sleep -Milliseconds 50
    }
    return 0
}

function ConvertTo-MtPyRepr {
    <#
    .SYNOPSIS
        复刻 python repr(str) 的最简形态，只为对齐 `宿主窗口未就绪（{line!r}）` 那一行。
    .NOTES
        实际只会遇到空串（子进程 EOF 后 python 版的行变量必为空串）。
    #>
    [CmdletBinding()]
    param([AllowEmptyString()][string]$Text)

    if ($null -eq $Text) { $Text = '' }
    $esc = $Text.Replace('\', '\\').Replace("'", "\'").Replace("`n", '\n').Replace("`r", '\r').Replace("`t", '\t')
    return "'$esc'"
}

function Invoke-MtImeSelfTest {
    <#
    .SYNOPSIS
        跨进程自检：子进程建真窗口 → 父进程切语言 → 核对 ↔ 父线程不受影响
        （对应 python _selftest）。
    .NOTES
        输出**不是**逐字节可比的：hwnd/tid 与报告里的观测时间戳天然每次不同。
        可比的是这一行的判定结果：`MT_IME_SELFTEST: ALL PASS` / `N 项不符`。
    #>
    [CmdletBinding()]
    param()

    # python: Path(__file__).resolve().parent / ".." / "temp" / "_ime_selftest_report.txt"
    # ⇒ scripts/temp/_ime_selftest_report.txt（不是仓库根的 temp/！）
    $report = [System.IO.Path]::GetFullPath((Join-Path (Join-Path $PSScriptRoot '..\temp') '_ime_selftest_report.txt'))
    $reportDir = [System.IO.Path]::GetDirectoryName($report)
    if (-not (Test-Path -LiteralPath $reportDir)) {
        [void](New-Item -ItemType Directory -Force -Path $reportDir)
    }

    $myLangidBefore = Get-MtLangIdOfThread -ThreadId (Get-MtCurrentThreadId)

    $pwshExe = Join-Path $PSHOME 'pwsh.exe'
    if (-not (Test-Path -LiteralPath $pwshExe)) { $pwshExe = 'pwsh' }
    $psi = [System.Diagnostics.ProcessStartInfo]::new()
    $psi.FileName = $pwshExe
    foreach ($a in @('-NoProfile', '-File', $script:MtImeSelfPath, '_hostwin', '-Report', $report)) {
        [void]$psi.ArgumentList.Add([string]$a)
    }
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true
    $psi.StandardOutputEncoding = $script:Utf8NoBom

    $proc = [System.Diagnostics.Process]::Start($psi)
    try {
        $line = ''
        $deadline = [DateTime]::UtcNow.AddSeconds(20)
        while ([DateTime]::UtcNow -lt $deadline) {
            $raw = $proc.StandardOutput.ReadLine()
            if ($null -eq $raw) {
                # EOF：python 版此时会空转到 deadline（读到 ''），最终 line='' —— 等价地直接收束
                $line = ''
                break
            }
            $line = $raw.Trim()
            if ($line.StartsWith('HWND=')) { break }
        }
        if (-not $line.StartsWith('HWND=')) {
            Write-MtLine ('MT_IME_SELFTEST: FAIL — 宿主窗口未就绪（{0}）' -f (ConvertTo-MtPyRepr $line))
            return 1
        }

        $parts = @{}
        foreach ($kv in ($line -split '\s+')) {
            $i = $kv.IndexOf('=')
            if ($i -gt 0) { $parts[$kv.Substring(0, $i)] = $kv.Substring($i + 1) }
        }
        $hwnd = [long]$parts['HWND']
        $tid = [long]$parts['TID']
        Write-MtLine ('  宿主窗口 hwnd={0} tid={1}' -f $hwnd, $tid)
        $before = Get-MtLangIdOfThread -ThreadId $tid
        Write-MtLine ('  切换前：0x{0:X4}（{1}）' -f $before, (Get-MtLayoutDescription -LangId $before))

        $r = Set-MtWindowUs -Hwnd $hwnd
        $tag = if ($r.Ok) { 'OK' } else { 'FAIL' }
        Write-MtLine ('  切换调用：{0} — {1}' -f $tag, $r.Message)
        $after = Get-MtLangIdOfThread -ThreadId $tid
        Write-MtLine ('  切换后：0x{0:X4}（{1}）' -f $after, (Get-MtLayoutDescription -LangId $after))

        Start-Sleep -Milliseconds 400
        $seq = @()
        if (Test-Path -LiteralPath $report -PathType Leaf) {
            $txt = [System.IO.File]::ReadAllText($report, $script:Utf8NoBom).Trim()
            if ($txt) { $seq = @($txt -split "`r?`n") }
        }

        $myLangidAfter = Get-MtLangIdOfThread -ThreadId (Get-MtCurrentThreadId)
        Write-MtLine ('  本进程线程语言：0x{0:X4} → 0x{1:X4}（应保持不变）' -f $myLangidBefore, $myLangidAfter)

        $hostSaw = $false
        foreach ($s in $seq) { if ($s.Contains('0x0409')) { $hostSaw = $true; break } }

        $checks = @(
            [pscustomobject]@{ Name = '目标线程变为 en-US'; Pass = ($after -eq 0x0409) },
            [pscustomobject]@{ Name = '宿主进程自行观测到变化（跨进程生效）'; Pass = $hostSaw },
            [pscustomobject]@{ Name = '本进程输入语言未被改动（不与现有功能冲突）'; Pass = ($myLangidBefore -eq $myLangidAfter) }
        )
        $bad = 0
        Write-MtLine ''
        foreach ($c in $checks) {
            $mark = if ($c.Pass) { 'PASS' } else { 'FAIL' }
            Write-MtLine ('  [{0}] {1}' -f $mark, $c.Name)
            if (-not $c.Pass) { $bad++ }
        }
        if ($seq.Count -gt 0) {
            # python: print(f"...{seq}") —— list 的 repr：['a', 'b']
            $items = (($seq | ForEach-Object { "'$_'" }) -join ', ')
            Write-MtLine ('  宿主进程观测序列：[{0}]' -f $items)
        }
        Write-MtLine ''
        if ($bad -eq 0) {
            Write-MtLine 'MT_IME_SELFTEST: ALL PASS'
            return 0
        }
        Write-MtLine ('MT_IME_SELFTEST: {0} 项不符' -f $bad)
        return 1
    } finally {
        try { if (-not $proc.HasExited) { $proc.Kill() } } catch { }
        try { [void]$proc.WaitForExit(5000) } catch { }
    }
}

# ══ 入口：参数解析（本仓入口脚本统一约定：手写 $args 循环，不用 param()）═══

function ConvertTo-MtArgLong {
    <#
    .SYNOPSIS
        解析整数型选项值（非整数时报 `MT_ERROR:` 并退出 2，与 argparse 一致）。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Raw, [Parameter(Mandatory)][string]$Name)

    $v = [long]0
    if (-not [long]::TryParse($Raw, [ref]$v)) {
        Write-MtErrorLine "参数 $Name 需要整数，收到 $Raw"
        exit $MT_EXIT_ERROR
    }
    return $v
}

$Command = ''
$Version = ''
$Hwnd = [long]0
$Report = ''

$i = 0
while ($i -lt $args.Count) {
    $tok = [string]$args[$i]
    if (-not $tok.StartsWith('-')) {
        if (-not $Command) { $Command = $tok; $i++; continue }
        Write-MtErrorLine "未知参数 $tok"; exit $MT_EXIT_ERROR
    }
    $optName = $tok.TrimStart('-').ToLowerInvariant().Replace('-', '')
    if ($optName -eq 'version') {
        if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --version 的值'; exit $MT_EXIT_ERROR }
        $Version = [string]$args[$i + 1]; $i += 2
    } elseif ($optName -eq 'hwnd') {
        if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --hwnd 的值'; exit $MT_EXIT_ERROR }
        $Hwnd = ConvertTo-MtArgLong -Raw ([string]$args[$i + 1]) -Name '--hwnd'; $i += 2
    } elseif ($optName -eq 'report') {
        if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --report 的值'; exit $MT_EXIT_ERROR }
        $Report = [string]$args[$i + 1]; $i += 2
    } else {
        Write-MtErrorLine "未知参数 $tok"; exit $MT_EXIT_ERROR
    }
}

$cmdName = if ($null -eq $Command) { '' } else { $Command.Trim() }
if (-not $cmdName) {
    Write-MtErrorLine '缺少子命令（可选：list status ensure selftest _hostwin）'
    exit $MT_EXIT_ERROR
}

# python: --version 走 argparse choices；这里手工校验并把退出码对齐为 2
if ($Version -and -not (Assert-MtVersion -Version $Version)) { exit $MT_EXIT_ERROR }

switch ($cmdName) {
    'list' {
        $rc = Invoke-MtImeList
        exit $rc
    }
    'status' {
        $rc = Invoke-MtImeStatus -Version $Version -Hwnd $Hwnd
        exit $rc
    }
    'ensure' {
        $rc = Invoke-MtImeEnsure -Version $Version -Hwnd $Hwnd
        exit $rc
    }
    'selftest' {
        $rc = Invoke-MtImeSelfTest
        exit $rc
    }
    '_hostwin' {
        if (-not $Report) {
            Write-MtErrorLine '缺少必填参数 --report'
            exit $MT_EXIT_ERROR
        }
        $rc = Invoke-MtImeHostWin -Report $Report
        exit $rc
    }
    default {
        Write-MtErrorLine ('未知子命令 {0}（可选：list status ensure selftest _hostwin）' -f $cmdName)
        exit $MT_EXIT_ERROR
    }
}

