#!/usr/bin/env pwsh
# -*- coding: utf-8 -*-
<#
Tooltip 染色规则审计（规则见 docs/tooltip-color-rules.md）。

只读审计，不修改任何文件。检查项：

  R1  非时间数值未着黄（§e）
  R2  时间未着蓝（§9）      —— 形态 M:SS 与 N 秒 / Ns / N seconds
  R3  「<效果名> (<效果时间>)」未整段同色
  R0  行内回落码 ≠ 本行底色码（§r 复位为无颜色=白、§7 复位为灰，都不是「恢复本行底色」）。
      行底色自 ModTooltipHandler.java 解析（tt("key"...).withStyle(ChatFormatting.X)）。
      仅当回落码后仍有文本（含 \n 之后的文本）时检查；值串末尾的回落码不判违规。
  R4  值内含 %%（字面百分号）却走 Component.translatable(...)：原版 TranslatableContents.decomposeTemplate
      会把 %% 拆成独立的无样式 TEXT_PERCENT 片段，落在高亮区内的 % 会掉成行底色。必须走 tt(...)。
  R1b 数值/时间的前后符号被留在染色区之外（+ - × ÷ ★ ~ 等）

例外（不报错）：§c 红色条目、行级语义色、§f 按键提示行、
列表序号与标签序号（1. / 第一 / Curse 1 / T4）。

用法：
    pwsh -File scripts/audit/tooltip_color_audit.ps1 [-Root .]

退出码：0 = 无违规；1 = 存在违规；2 = 无法判定（被审根下 lang 文件缺失 ⇒ 覆盖度不足，不得当作通过）。
        ⚠️ 2 由文件末尾的覆盖度守卫给出：$missing -gt 0 的判定先于 $checked -eq 0（两者都 exit 2）。

—— PowerShell 移植版:1:1 对应 scripts/audit/tooltip_color_audit.py（原 .py 已在 92fbeaf 删除；取回：`git show 92fbeaf^:scripts/audit/tooltip_color_audit.py`）。
#>
[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [string]$Root = '.'
)

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8
$ErrorActionPreference = 'Stop'

$SUBPROJECTS = @('neoforge-1.21.1', 'forge-1.20.1', 'neoforge-26.1.2', 'fabric-1.20.1')
# --- 覆盖矩阵声明（2026-10-06 校准；勿删）---
# 线：4 线全覆盖（原只 2 线，漏 neoforge-26.1.2 / fabric-1.20.1）
# 语：zh_cn / en_us / ja_jp 三语全覆盖（2026-10-06 Stage 2 纳入 ja_jp）
# ja_jp 口径（2026-10-06 逐条裁定；证据 temp/t62-stage2/）——
#   · R1 在日文上不具判别力：日文正文用「阿拉伯数字 + 量词/助词」计数
#     （1 回振り / 1 スタック / 2 倍 / ★レベル），与「要着黄的数值」同形。
#     ⇒ 仅当命中后 1 字为 CJK/假名，且（token 为 ★ 或 token 以空白结尾）时豁免；
#        数字紧贴 % 或紧贴 CJK 等真数值形态仍会判红（负控见 temp/t62-stage2/）。
#   · R3 原取「名字捕获串首字符」色码；CJK 无空格时捕获串会越过色码边界、吞到句内
#     （例 §e+2§7、§9移動速度上昇 (0:10)）⇒ 改取「紧邻 ( 的末字符」色码。
#   · R2/R0 无需豁免：ja_jp 的 R2 命中 36 处已由 Stage 1 修为 §9；
#     R0 命中 16 处（4 key × 4 线）为真违规，已同批修为 §r → §7。
#   · 残余已知局限：R1 对「未着色数字 + 空白 + CJK 词」形态不再报红（与日文散文计数
#     不可区分）；名字为混合色（首字符违规/末字符合规）时不再报 R3。
$LANGS = @('zh_cn.json', 'en_us.json', 'ja_jp.json')

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

# ---------------------------------------------------------------------------
# 行底色映射：从两个子项目的 ModTooltipHandler.java 解析
#   tt("key"...).withStyle(ChatFormatting.X)          -> R0 用（本行底色码）
#   Component.translatable("key"...).withStyle(...)   -> R0 + R4 用
# 同一 key 出现多种底色（或解析不到）时视为不确定，R0 跳过该项。
# ---------------------------------------------------------------------------
$COLOR_CODE = @{
    'BLACK' = '§0'; 'DARK_BLUE' = '§1'; 'DARK_GREEN' = '§2'; 'DARK_AQUA' = '§3';
    'DARK_RED' = '§4'; 'DARK_PURPLE' = '§5'; 'GOLD' = '§6'; 'GRAY' = '§7';
    'DARK_GRAY' = '§8'; 'BLUE' = '§9'; 'GREEN' = '§a'; 'AQUA' = '§b';
    'RED' = '§c'; 'LIGHT_PURPLE' = '§d'; 'YELLOW' = '§e'; 'WHITE' = '§f'
}

function Get-TooltipColorContext {
    param([string]$Root)
    $codes = @{}
    $translatable = New-Object System.Collections.Generic.HashSet[string]
    foreach ($sub in $SUBPROJECTS) {
        $path = [System.IO.Path]::Combine($Root, $sub, 'src/main/java/com/merlinkitsune/astral_dice/event/ModTooltipHandler.java')
        if (-not [System.IO.File]::Exists($path)) { continue }
        $src = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
        $calls = @(
            @{ re = 'tt\(\s*"([^"]+)"'; trans = $false },
            @{ re = 'Component\.translatable\(\s*"([^"]+)"'; trans = $true }
        )
        foreach ($call in $calls) {
            foreach ($m in [regex]::Matches($src, $call.re)) {
                $key = $m.Groups[1].Value
                if ($call.trans) { [void]$translatable.Add($key) }
                $len = [Math]::Min(600, $src.Length - $m.Index - $m.Length)
                if ($len -le 0) { continue }
                $tail = $src.Substring($m.Index + $m.Length, $len)
                $cm = [regex]::Match($tail, '\.withStyle\(\s*ChatFormatting\.([A-Z_]+)')
                if (-not $cm.Success) { continue }
                $code = $COLOR_CODE[$cm.Groups[1].Value]
                if ($null -eq $code) { continue }
                if (-not $codes.ContainsKey($key)) {
                    $codes[$key] = $code
                } elseif ($codes[$key] -cne $code) {
                    $codes[$key] = $null      # 同 key 多底色 → 不确定
                }
            }
        }
    }
    return , @($codes, $translatable)
}

function Test-FallbackCode {
    <#
    规则 0：行内回落码（§7 / §r）必须等于本行底色码。
    §r 的实际渲染色是「无颜色 = 白」（StringDecomposer.java:114，defaultStyle = Style.EMPTY），
    §7 的实际渲染色是灰；两者都不等于本行底色时判违规。
    仅当回落码之后仍有文本（含 \n 之后的文本）时才检查。
    #>
    param([string]$value, [string]$baseCode)
    $out = New-Object System.Collections.Generic.List[string]
    foreach ($m in [regex]::Matches($value, '§[7r]')) {
        $rest = $value.Substring($m.Index + 2)
        if ($rest.Trim().Length -eq 0) { continue }          # 值串末尾 → 不影响显示
        $actual = $m.Value
        if ($actual -ceq '§r') { $actual = '§f' }            # §r 渲染为白
        if ($actual -cne $baseCode) { $out.Add($m.Value) }
    }
    return , $out
}

function Test-ExemptRun {    <#
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
    param([string]$path, $codesByKey, $translatableKeys)
    $problems = New-Object System.Collections.Generic.List[object]
    # ja_jp 排版差异开关（R1 豁免 / R3 名字取点），见文件头覆盖矩阵声明
    $isJa = $path.EndsWith('ja_jp.json')
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
            # ja_jp 排版豁免（仅 ja 生效）：日文正文用「数字 + 量词」计数，与数值同形
            if ($isJa) {
                $nxt = ''
                if (($m.Index + $m.Length) -lt $text.Length) { $nxt = $text.Substring($m.Index + $m.Length, 1) }
                $isCjk = ($nxt -cmatch '[\u3005\u3041-\u3096\u30A1-\u30FA\u4E00-\u9FFF\uFF66-\uFF9D]')
                if ($isCjk -and (($m.Value -ceq ([string][char]0x2605)) -or ($m.Value -match '\s$'))) { continue }
            }
            $code = $codes[$m.Index]
            if (($code -ceq '§e') -or ($code -ceq '§9')) { continue }
            if (Test-ExemptRun $text $code $m.Index) { continue }
            $problems.Add(@('R1', $key, $code, $m.Value))
        }

        # R3：「名 (时间)」整段同色（排除 "For(2:00)" 这类时长范围前缀）
        foreach ($m in [regex]::Matches($text, '([^\s(（:：]+)\s*[(（](\d+:\d{2})[)）]')) {
            if ($m.Groups[1].Value.ToLowerInvariant() -ceq 'for') { continue }
            # 取「紧邻 ( 的末字符」色码：CJK 无空格时捕获串会越过色码边界（原取首字符）
            $namePos = $m.Groups[1].Index + $m.Groups[1].Length - 1
            $timePos = $m.Groups[2].Index
            if (($codes[$namePos] -cne $codes[$timePos]) -or (-not (@('§9', '§c') -ccontains $codes[$namePos]))) {
                $problems.Add(@('R3', $key, $codes[$namePos], $m.Value))
            }
        }

        # R0：行内回落码 ≠ 本行底色码
        if ($codesByKey.ContainsKey($key) -and $null -ne $codesByKey[$key]) {
            foreach ($bad in (Test-FallbackCode $value $codesByKey[$key])) {
                $problems.Add(@('R0', $key, $bad, ('base=' + $codesByKey[$key])))
            }
        }

        # R4：值内含 %% 却走 Component.translatable(...)
        if ($value.Contains('%%') -and $translatableKeys.Contains($key)) {
            $problems.Add(@('R4', $key, 'translatable', '%% 需走 tt(...)'))
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
$checked = 0   # 实际审过的 lang 文件数
$missing = 0   # 不存在的 lang 文件数；>0 ⇒ 覆盖度不足（防「只审到 1/8 也判 PASS」的半坏假绿）
$ctx = Get-TooltipColorContext $Root
$codesByKey = $ctx[0]
$translatableKeys = $ctx[1]
foreach ($sub in $SUBPROJECTS) {
    foreach ($lang in $LANGS) {
        $path = [System.IO.Path]::Combine($Root, $sub, 'src/main/resources/assets/astral_dice/lang', $lang)
        if (-not [System.IO.File]::Exists($path)) { $missing++; continue }
        $checked++
        $problems = Invoke-AuditLang $path $codesByKey $translatableKeys
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
if ($missing -gt 0) {
    Write-Out ('tooltip 染色审计： ERROR（无法判定）—— 覆盖度不足：{0} 份 lang 文件不存在，实审仅 {1} 份（期望 {2} 线 x {3} 语）' -f $missing, $checked, $SUBPROJECTS.Count, $LANGS.Count)
    [Console]::Out.Flush()
    exit 2
}
if ($checked -eq 0) {
    Write-Out 'tooltip 染色审计： ERROR（无法判定）—— 未审到任何 lang 文件（ROOT 错或路径约定变化）'
    [Console]::Out.Flush()
    exit 2
}
Write-Out ('覆盖矩阵： ' + ($SUBPROJECTS -join ' / ') + '  x  ' + ($LANGS -join ', ') + '  (实审 lang 文件 ' + $checked + ' 个)')
$verdict = 'FAIL（' + $total + ' 处违规）'
if ($total -eq 0) { $verdict = 'PASS（无违规）' }
Write-Out ('tooltip 染色审计： ' + $verdict)
[Console]::Out.Flush()
if ($total -eq 0) { exit 0 } else { exit 1 }
