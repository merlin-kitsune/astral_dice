<#
Mt.Phase.psm1 — 阶段标记、计时、退出码与统一输出契约。

对应源文件：scripts/test/lib/phase.sh（1:1 移植；输出的字节形态必须与 bash/python 版一致）。

## 输出纪律（Tier-1 等价门的前提）

Windows 上 [Console]::Out 是 StreamWriter，其 NewLine 为 CRLF；`Write-Output` /
`Write-Host` 都会走这层翻译，导致 stdout 与 python/bash 版**逐字节不等**。
因此本模块把所有输出收敛到两个原语：

    Write-MtLine    → [Console]::Out.Write("$text`n")      stdout，显式 LF
    Write-MtErrLine → [Console]::Error.Write("$text`n")    stderr，显式 LF

**任何 mt_* 脚本都不得直接 Write-Output / Write-Host / echo 面向观众的文本。**

## 输出行格式（与 phase.sh 逐字一致）

    (空行)===== MT_PHASE: <name> =====
    MT_<NAME>: OK (<elapsed>) — <detail>        stdout
    MT_<NAME>: FAIL (<elapsed>) — <reason>      stderr
    MT_<NAME>: BLOCKED (<elapsed>) — <reason>   stderr
    MT_<NAME>: ERROR (<elapsed>) — <reason>     stderr
    MT_INFO: <text>                             stdout
    MT_WARN: <text>                             stderr
#>

# ── 退出码（与 AGENTS「失败处理」表一致；phase.sh 同名同值）──────────────
$MT_EXIT_PASS = 0
$MT_EXIT_FAIL = 1
$MT_EXIT_ERROR = 2
$MT_EXIT_PREFLIGHT = 10
$MT_EXIT_BLOCKED = 11

$script:MtPhaseStart = 0

function Initialize-MtConsole {
    <#
    .SYNOPSIS
        统一控制台 I/O 编码为 UTF-8（无 BOM）。

    .NOTES
        必须由**每个入口脚本的第一行逻辑**调用。原因：Windows 上 [Console]::Out 默认
        用 OEM/ANSI 码页（本机 936/GBK）编码，直接 `[Console]::Out.Write("中文")` 会写出
        GBK 字节；而 .mt 的调用方（DSH/终端/CI）按 UTF-8 解码 → 中文全部变乱码。
        同时把 Out 的 NewLine 交给 Write-MtLine 自己控制（显式 `n），不依赖平台默认。
    #>
    [CmdletBinding()]
    param()

    $utf8 = [System.Text.UTF8Encoding]::new($false)   # 不写 BOM、不抛异常
    try { [Console]::OutputEncoding = $utf8 } catch { }
    try { [Console]::InputEncoding = $utf8 } catch { }
    try { $global:OutputEncoding = $utf8 } catch { }
    try { $global:PSDefaultParameterValues['Out-File:Encoding'] = 'utf8' } catch { }
}

function Write-MtLine {
    <#
    .SYNOPSIS
        向 stdout 写一行（显式 LF，不做 CRLF 翻译）。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][AllowEmptyString()][string]$Text)

    [Console]::Out.Write($Text + "`n")
}

function Write-MtErrLine {
    <#
    .SYNOPSIS
        向 stderr 写一行（显式 LF）。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][AllowEmptyString()][string]$Text)

    [Console]::Error.Write($Text + "`n")
}

function Start-MtPhase {
    <#
    .SYNOPSIS
        开始一个阶段：重置计时并打印阶段头（含前导空行，与 phase.sh 一致）。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Name)

    $script:MtPhaseStart = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    Write-MtLine ''
    Write-MtLine "===== MT_PHASE: $Name ====="
}

function Get-MtPhaseElapsed {
    <#
    .SYNOPSIS
        返回距 Start-MtPhase 的耗时，格式与 phase.sh 的 mt_phase_elapsed 相同：
        不足 60 秒 → "<秒>s"；否则 → "<分>m<秒,两位>s"。
    #>
    [CmdletBinding()]
    param()

    $now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $secs = [int]($now - $script:MtPhaseStart)
    if ($secs -ge 60) {
        return ('{0}m{1:d2}s' -f [int][Math]::Floor($secs / 60), ($secs % 60))
    }
    return "${secs}s"
}

function Write-MtOk {
    <#
    .SYNOPSIS
        MT_<NAME>: OK (<elapsed>) — <detail>（stdout；detail 省略时不带 " — "）。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Name,
        [string]$Detail
    )

    $suffix = ''
    if ($PSBoundParameters.ContainsKey('Detail') -and $Detail) { $suffix = " — $Detail" }
    Write-MtLine ("MT_{0}: OK ({1}){2}" -f $Name, (Get-MtPhaseElapsed), $suffix)
}

function Write-MtFail {
    <#
    .SYNOPSIS
        MT_<NAME>: FAIL (<elapsed>) — <reason>（stderr）。
    .NOTES
        bash 版的 mt_fail 还会 return 一个退出码；当前所有脚本都未消费该返回值，
        因此这里只打印，退出码由调用方显式 exit。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Name,
        [string]$Reason
    )

    $suffix = ''
    if ($PSBoundParameters.ContainsKey('Reason') -and $Reason) { $suffix = " — $Reason" }
    Write-MtErrLine ("MT_{0}: FAIL ({1}){2}" -f $Name, (Get-MtPhaseElapsed), $suffix)
}

function Write-MtBlocked {
    <#
    .SYNOPSIS
        MT_<NAME>: BLOCKED (<elapsed>) — <reason>（stderr；reason 恒有）。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][string]$Reason
    )

    Write-MtErrLine ("MT_{0}: BLOCKED ({1}) — {2}" -f $Name, (Get-MtPhaseElapsed), $Reason)
}

function Write-MtError {
    <#
    .SYNOPSIS
        MT_<NAME>: ERROR (<elapsed>) — <reason>（stderr；reason 恒有）。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][string]$Reason
    )

    Write-MtErrLine ("MT_{0}: ERROR ({1}) — {2}" -f $Name, (Get-MtPhaseElapsed), $Reason)
}

function Write-MtInfo {
    <#
    .SYNOPSIS
        MT_INFO: <text>（stdout）。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][AllowEmptyString()][string]$Text)

    Write-MtLine "MT_INFO: $Text"
}

function Write-MtWarn {
    <#
    .SYNOPSIS
        MT_WARN: <text>（stderr）。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][AllowEmptyString()][string]$Text)

    Write-MtErrLine "MT_WARN: $Text"
}

function Write-MtErrorLine {
    <#
    .SYNOPSIS
        裸 `MT_ERROR: <text>`（stderr）。这是各脚本参数校验分支直接 echo 的那一行，
        不是 Write-MtError 的 MT_<name>: ERROR 形态 —— 两者不可混用。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][AllowEmptyString()][string]$Text)

    Write-MtErrLine "MT_ERROR: $Text"
}

Export-ModuleMember -Function @(
    'Initialize-MtConsole',
    'Write-MtLine', 'Write-MtErrLine',
    'Start-MtPhase', 'Get-MtPhaseElapsed',
    'Write-MtOk', 'Write-MtFail', 'Write-MtBlocked', 'Write-MtError',
    'Write-MtInfo', 'Write-MtWarn', 'Write-MtErrorLine'
) -Variable @(
    'MT_EXIT_PASS', 'MT_EXIT_FAIL', 'MT_EXIT_ERROR', 'MT_EXIT_PREFLIGHT', 'MT_EXIT_BLOCKED'
)
