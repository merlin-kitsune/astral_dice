#!/usr/bin/env pwsh
# -*- coding: utf-8 -*-
<#
Tooltip 染色规则审计（规则见 docs/tooltip-color-rules.md）。

只读审计，不修改任何文件。检查项：

  R1  非时间数值未着黄（§e）
  R2  时间未着蓝（§9）      —— 形态 M:SS 与 N 秒 / Ns / N seconds
  R3  「<效果名> (<效果时间>)」未整段同色
  R1b 数值/时间的前后符号被留在染色区之外（+ - × ÷ ★ ~ 等）
  R5  行级 withStyle 与行内规则冲突的易错点（仅提示，不判失败）

例外（不报错）：§c 红色条目、行级语义色、§r 复位码、§f 按键提示行、
列表序号与标签序号（1. / 第一 / Curse 1 / T4）。

用法：
    pwsh -File scripts/audit/tooltip_color_audit.ps1 [-Root .]

退出码：0 = 无违规；1 = 存在违规。

—— PowerShell 移植版:1:1 对应 scripts/audit/tooltip_color_audit.py(原 .py 保留不删)。
#>
[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [string]$Root = '.'
)

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8
$ErrorActionPreference = 'Stop'

$SUBPROJECTS = @('neoforge-1.21.1', 'forge-1.20.1')
$LANGS = @('zh_cn.json', 'en_us.json')

# 时间形态：M:SS（两位秒）、N 秒、Ns / N sec / N seconds
$TIME_RE = '(\d+:\d{2}|\d+\s*秒|\d+\s*(?:seconds?|secs?|s)\b)'
# 取值符号（含在染色区内才算合规）
$SIGN_CHARS = '+-×÷★~'

$PAIR_RE = '^\s*"([^"]+)"\s*:\s*"((?:[^"\\]|\\.)*)"(,?)\s*$'

# ---------------------------------------------------------------------------
# 输出:统一经 [Console]::Out 写显式 LF（不得 CRLF;与 Mt.Phase 的 Write-MtLine 同契约)
#       Python print() 在 Windows 上把 "\n" 按 os.linesep 翻译成 CRLF 的行为）
# ---------------------------------------------------------------------------
function Write-Out([string]$text) {
    [Console]::Out.Write($text)
    [Console]::Out.Write("`n")
}

function ConvertTo-PyStr {
    param($Value)
    if ($null -eq $Value) { return 'None' }
    if ($Value -is [bool]) { if ($Value) { return 'True' } else { return 'False' } }
    return [string]$Value
}

function ConvertTo-PyRepr {
    param($Value)
    if ($null -eq $Value) { return 'None' }
    if ($Value -is [bool]) { if ($Value) { return 'True' } else { return 'False' } }
    if ($Value -is [string]) {
        return "'" + $Value.Replace('\', '\\').Replace("'", "\'").Replace("`n", '\n').Replace("`r", '\r') + "'"
    }
    if ($Value -is [System.Collections.IDictionary]) {
        $parts = @()
        foreach ($e in $Value.GetEnumerator()) { $parts += ((ConvertTo-PyRepr $e.Key) + ': ' + (ConvertTo-PyRepr $e.Value)) }
        return '{' + ($parts -join ', ') + '}'
    }
    if ($Value -is [System.Collections.IEnumerable]) {
        $parts = @()
        foreach ($x in $Value) { $parts += (ConvertTo-PyRepr $x) }
        return '[' + ($parts -join ', ') + ']'
    }
    return [string]$Value
}

# Python 的 io.open(path, "r", encoding="utf-8").read()（含通用换行翻译）
function Read-PyText {
    param([string]$Path)
    $t = [System.IO.File]::ReadAllText($Path, [System.Text.Encoding]::UTF8)
    $t = $t -replace "`r`n", "`n"
    return ($t -replace "`r", "`n")
}

function Get-Pairs {
    param([string]$Path)
    $out = @()
    foreach ($m in [regex]::Matches((Read-PyText $Path), $PAIR_RE, [System.Text.RegularExpressions.RegexOptions]::Multiline)) {
        $out += , @($m.Groups[1].Value, $m.Groups[2].Value)
    }
    return , $out
}

function Clear-WithCodes {
    <#
    返回 (去码文本, 与每个字符位置对应的颜色码)。
    #>
    param([string]$value)
    $value = $value.Replace('\n', "`n")
    $sb = New-Object System.Text.StringBuilder
    $codes = New-Object System.Collections.Generic.List[object]
    $code = $null
    $i = 0
    $n = $value.Length
    while ($i -lt $n) {
        if (($value[$i] -ceq '§') -and ($i + 1 -lt $n)) {
            $code = $value.Substring($i, 2)
            $i += 2
            continue
        }
        [void]$sb.Append($value[$i])
        $codes.Add($code)
        $i += 1
    }
    return , @($sb.ToString(), $codes)
}

function Test-ExemptRun {
    <#
    §c 红色条目、列表序号 / 标签序号等豁免内容。
    #>
    param([string]$text, $code, [int]$pos)
    if ($code -ceq '§c') { return $true }
    $after = ''
    if ($pos -lt $text.Length) {
        $len = [Math]::Min(4, $text.Length - $pos)
        $after = $text.Substring($pos, $len)
    }
    $bs = [Math]::Max(0, $pos - 10)
    $before = ''
    if ($pos -gt $bs) { $before = $text.Substring($bs, $pos - $bs) }
    if ([regex]::Match($after, '^\d+\.\s').Success) { return $true }
    if ($before.TrimEnd().EndsWith('第')) { return $true }
    if ([regex]::IsMatch($before, 'Curse\s+$')) { return $true }
    if ([regex]::IsMatch($before, '(?:^|[\s（(])T$')) { return $true }
    return $false
}

function Invoke-AuditLang {
    param([string]$path)
    $problems = New-Object System.Collections.Generic.List[object]
    foreach ($pair in (Get-Pairs $path)) {
        $key = $pair[0]
        $value = $pair[1]
        if ((-not $key.Contains('tooltip')) -and (-not $key.StartsWith('effect.'))) {
            continue
        }
        $cc = Clear-WithCodes $value
        $text = $cc[0]
        $codes = $cc[1]
        $timeMatches = @([regex]::Matches($text, $TIME_RE))

        # R2：时间未着蓝
        foreach ($m in $timeMatches) {
            $code = $codes[$m.Index]
            if ($code -ceq '§9') { continue }
            if (Test-ExemptRun $text $code $m.Index) { continue }
            $problems.Add(@('R2', $key, $code, $m.Value))
        }

        # R1：非时间数值未着黄
        foreach ($m in [regex]::Matches($text, '[+\-]?\d+(?:\.\d+)?\s*%?|★')) {
            $inTime = $false
            foreach ($t in $timeMatches) {
                if (($t.Index -le $m.Index) -and ($m.Index -lt ($t.Index + $t.Length))) { $inTime = $true; break }
            }
            if ($inTime) { continue }
            $code = $codes[$m.Index]
            if (($code -ceq '§e') -or ($code -ceq '§9')) { continue }
            if (Test-ExemptRun $text $code $m.Index) { continue }
            $problems.Add(@('R1', $key, $code, $m.Value))
        }

        # R3：「名 (时间)」整段同色（排除 "For(2:00)" 这类时长范围前缀）
        foreach ($m in [regex]::Matches($text, '([^\s(（:：]+)\s*[(（](\d+:\d{2})[)）]')) {
            if ($m.Groups[1].Value.ToLowerInvariant() -ceq 'for') { continue }
            $namePos = $m.Groups[1].Index
            $timePos = $m.Groups[2].Index
            if (($codes[$namePos] -cne $codes[$timePos]) -or (-not (@('§9', '§c') -ccontains $codes[$namePos]))) {
                $problems.Add(@('R3', $key, $codes[$namePos], $m.Value))
            }
        }

        # R1b：取值符号被留在染色区之外（在原始文本上检查）
        foreach ($m in [regex]::Matches($value, '[%+×÷~]§[e9]|\-[%+×÷~]?§[e9]')) {
            $problems.Add(@('R1b', $key, '符号外置', $m.Value))
        }
    }
    return , $problems
}


# ---------------------------------------------------------------------------
# main
# ---------------------------------------------------------------------------
$total = 0
foreach ($sub in $SUBPROJECTS) {
    foreach ($lang in $LANGS) {
        $path = [System.IO.Path]::Combine($Root, $sub, 'src/main/resources/assets/astral_dice/lang', $lang)
        if (-not [System.IO.File]::Exists($path)) { continue }
        $problems = Invoke-AuditLang $path
        if ($problems.Count -gt 0) {
            Write-Out ('--- ' + $sub + '/' + $lang + ': ' + $problems.Count + ' 处')
            foreach ($p in $problems) {
                Write-Out ('    [' + $p[0] + '] ' + $p[1] + '  code=' + (ConvertTo-PyStr $p[2]) + '  token=' + (ConvertTo-PyRepr $p[3]))
            }
        }
        $total += $problems.Count
    }
}

Write-Out ''
$verdict = 'FAIL（' + $total + ' 处违规）'
if ($total -eq 0) { $verdict = 'PASS（无违规）' }
Write-Out ('tooltip 染色审计： ' + $verdict)
[Console]::Out.Flush()
if ($total -eq 0) { exit 0 } else { exit 1 }
