#!/usr/bin/env pwsh
#Requires -Version 7.0
# -*- coding: utf-8 -*-
<#
.SYNOPSIS
    语言文件同步检查(zh_cn.json <-> en_us.json) —— tools/check_lang_sync.py 的 1:1 PowerShell 移植。

.DESCRIPTION
    用法:
        pwsh -NoProfile -File tools/check_lang_sync.ps1 [-LangDir <assets/astral_dice/lang>]

    规则:
    - zh_cn.json 与 en_us.json 的 key 集合必须完全一致(新增/删除 key 必须同步两侧)。
    - 每个对应 key 的结构标记(%%/%s/%d 等占位符数量、换行数量)差异仅打印警告,
      不导致失败(中英措辞可不同,但结构应尽量一致)。
    退出码:0 = 通过;1 = key 不一致、JSON 读取/解析失败,或未捕获异常(与 CPython 相同)。

    跨平台:只做文本/JSON 处理,不使用任何 Win32 API / 注册表 / CIM;路径一律经 Join-Path。
    输出:统一经 Write-Stdout / Write-Stderr([Console]::Out / [Console]::Error,显式 LF,
    UTF-8 无 BOM),不使用 Write-Output / Write-Host。
#>
[CmdletBinding()]
param(
    [string]$LangDir = 'src/main/resources/assets/astral_dice/lang'
)

# ---------------------------------------------------------------------------
# 输出通道:UTF-8(无 BOM)+ 显式 LF
# ---------------------------------------------------------------------------
$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
try { [Console]::OutputEncoding = $utf8NoBom } catch { }
try { [Console]::InputEncoding = $utf8NoBom } catch { }
$OutputEncoding = $utf8NoBom

function Write-Stdout {
    param([Parameter(Mandatory = $true)][AllowEmptyString()][string]$Text)
    [Console]::Out.Write("$Text`n")
}

function Write-Stderr {
    param([Parameter(Mandatory = $true)][AllowEmptyString()][string]$Text)
    [Console]::Error.Write("$Text`n")
}

# ---------------------------------------------------------------------------
# Python 语义辅助函数
# ---------------------------------------------------------------------------

# Python 的 text[start:stop](越界自动钳制,不做负索引)
function Get-PySlice {
    param([string]$Text, [int]$Start, [int]$Stop)
    if ($Start -lt 0) { $Start = 0 }
    if ($Stop -gt $Text.Length) { $Stop = $Text.Length }
    if ($Start -ge $Stop) { return '' }
    return $Text.Substring($Start, $Stop - $Start)
}

# text[max(0, pos-Back) : pos+Forward] —— 按 Unicode 码位计数(与 Python 一致,即使含非 BMP 字符)
function Get-PyCodePointWindow {
    param([string]$Text, [int]$Index, [int]$Back, [int]$Forward)
    $s = $Index
    $n = 0
    while ($s -gt 0 -and $n -lt $Back) {
        $s--
        if ($s -gt 0 -and [char]::IsLowSurrogate($Text[$s]) -and [char]::IsHighSurrogate($Text[$s - 1])) { $s-- }
        $n++
    }
    $e = $Index
    $n = 0
    while ($e -lt $Text.Length -and $n -lt $Forward) {
        if ([char]::IsHighSurrogate($Text[$e]) -and ($e + 1) -lt $Text.Length -and [char]::IsLowSurrogate($Text[$e + 1])) { $e += 2 } else { $e++ }
        $n++
    }
    return $Text.Substring($s, $e - $s)
}

# Python 的 repr(str)(用于 OSError 文案里的文件名,CPython 对文件名用 %R)
function Get-PyStrRepr {
    param([string]$Value)
    $sb = [System.Text.StringBuilder]::new()
    [void]$sb.Append("'")
    foreach ($ch in $Value.ToCharArray()) {
        if ($ch -eq '\') { [void]$sb.Append('\\') }
        elseif ($ch -eq "'") { [void]$sb.Append("\'") }
        elseif ($ch -eq "`t") { [void]$sb.Append('\t') }
        elseif ($ch -eq "`n") { [void]$sb.Append('\n') }
        elseif ($ch -eq "`r") { [void]$sb.Append('\r') }
        elseif ([int]$ch -lt 32 -or [int]$ch -eq 127) { [void]$sb.Append('\x' + ([int]$ch).ToString('x2')) }
        else { [void]$sb.Append($ch) }
    }
    [void]$sb.Append("'")
    return $sb.ToString()
}

# Python 的 repr(tuple of str):元素间 ", ",单元素带尾逗号
function Get-PyStrTupleRepr {
    param([string[]]$Items)
    $list = @($Items)
    if ($list.Count -eq 0) { return '()' }
    $parts = @(foreach ($it in $list) { Get-PyStrRepr ([string]$it) })
    $body = $parts -join ', '
    if ($list.Count -eq 1) { return "($body,)" }
    return "($body)"
}

# 结构标记 = (占位符元组, 换行数, 颜色码数) 的 Python repr
function Get-StructureMarkerRepr {
    param([string]$Text)
    $placeholders = [string[]]@([regex]::Matches($Text, '%%|%[sdbfxoeg]') | ForEach-Object { $_.Value })
    $newlines = $Text.Length - $Text.Replace("`n", '').Length
    $colorCodes = [regex]::Matches($Text, '\u00a7[0-9a-fk-or]|\u00a7.').Count
    return '(' + (Get-PyStrTupleRepr $placeholders) + ', ' + $newlines + ', ' + $colorCodes + ')'
}

# Python JSONDecodeError 的 "line L column C (char N)" 位置串
function Get-PyPositionText {
    param([string]$Text, [int]$CharIndex)
    if ($CharIndex -gt $Text.Length) { $CharIndex = $Text.Length }
    if ($CharIndex -lt 0) { $CharIndex = 0 }
    $line = 1
    $lineStart = 0
    for ($i = 0; $i -lt $CharIndex; $i++) {
        if ($Text[$i] -eq "`n") { $line++; $lineStart = $i + 1 }
    }
    $col = $CharIndex - $lineStart + 1
    return "line $line column $col (char $CharIndex)"
}

# 把 System.Text.Json 的 (0 基行号, 该行内 0 基 UTF-8 字节偏移) 换算为字符下标
function Get-CharIndexFromLineByte {
    param([string]$Text, [int]$LineNumber, [int]$BytePositionInLine)
    $line = 0
    $i = 0
    while ($i -lt $Text.Length -and $line -lt $LineNumber) {
        if ($Text[$i] -eq "`n") { $line++ }
        $i++
    }
    $byteSum = 0
    while ($i -lt $Text.Length -and $Text[$i] -ne "`n") {
        $cp = [int]$Text[$i]
        if ([char]::IsHighSurrogate($Text[$i]) -and ($i + 1) -lt $Text.Length -and [char]::IsLowSurrogate($Text[$i + 1])) {
            $cp = [char]::ConvertToUtf32($Text[$i], $Text[$i + 1])
        }
        $len = if ($cp -lt 0x80) { 1 } elseif ($cp -lt 0x800) { 2 } elseif ($cp -lt 0x10000) { 3 } else { 4 }
        if (($byteSum + $len) -gt $BytePositionInLine) { break }
        $byteSum += $len
        if ($cp -ge 0x10000) { $i += 2 } else { $i++ }
    }
    return $i
}

# Python UnicodeDecodeError 文案:'utf-8' codec can't decode byte 0xNN in position N: <reason>
function Get-Utf8DecodeErrorText {
    param([byte[]]$Bytes, [int]$Index)
    if ($Index -lt 0 -or $Index -ge $Bytes.Length) { $Index = 0 }
    $b = [int]$Bytes[$Index]
    $cont = {
        param([int]$at, [int]$need)
        for ($k = 1; $k -le $need; $k++) {
            if (($at + $k) -ge $Bytes.Length) { return 'unexpected end of data' }
            $cb = [int]$Bytes[$at + $k]
            if ($cb -lt 0x80 -or $cb -gt 0xBF) { return 'invalid continuation byte' }
        }
        return 'invalid start byte'
    }
    if ($b -lt 0xC2 -or $b -gt 0xF4) {
        # 0x80-0xC1(含过长编码)与 0xF5-0xFF 都不是合法起始字节
        $reason = 'invalid start byte'
    }
    elseif ($b -le 0xDF) { $reason = & $cont $Index 1 }
    elseif ($b -le 0xEF) { $reason = & $cont $Index 2 }
    else { $reason = & $cont $Index 3 }
    return "'utf-8' codec can't decode byte 0x" + $b.ToString('x2') + " in position $Index" + ": $reason"
}

# 取异常链里第一个 JSON 解析异常(否则返回最外层)
function Get-JsonException {
    param([System.Exception]$Ex)
    $cur = $Ex
    while ($null -ne $cur) {
        if ($cur.GetType().FullName -like '*Json*') { return $cur }
        $cur = $cur.InnerException
    }
    return $Ex
}

# 把 System.Text.Json 的解析异常折算成 CPython json 的报错文案
# (CPython 的 JSONDecodeError 文案是解析器私有细节,.NET 解析器不可能完全同文;此处按
#  "报错短语 + line/column/char 位置" 折算,覆盖常见畸形输入;未识别的形态退化为 .NET 原文)
function Get-PythonJsonErrorText {
    param([string]$Text, [System.Exception]$Ex)

    # 空输入 / 纯空白:Python 跳过空白后到达 EOF,报 "Expecting value"
    $onlyWs = $true
    foreach ($ch in $Text.ToCharArray()) {
        if ($ch -ne ' ' -and $ch -ne "`t" -and $ch -ne "`n" -and $ch -ne "`r") { $onlyWs = $false; break }
    }
    if ($onlyWs) {
        return 'Expecting value: ' + (Get-PyPositionText $Text $Text.Length)
    }

    $jsonEx = Get-JsonException $Ex
    $msg = ''
    if ($null -ne $jsonEx -and $null -ne $jsonEx.Message) { $msg = [string]$jsonEx.Message }

    $phrase = $null
    $backOne = $false
    if ($msg -match 'does not contain any JSON tokens') {
        $phrase = 'Expecting value'
    }
    elseif ($msg -match 'is an invalid JSON literal') {
        # CPython 报错位置是字面量的第一个字符,.NET 是紧随其后的下标
        $phrase = 'Expecting value'
        $backOne = $true
    }
    elseif ($msg -match 'invalid start of a value') {
        $phrase = 'Expecting value'
    }
    elseif ($msg -match 'invalid start of a property name') {
        $phrase = 'Expecting property name enclosed in double quotes'
    }
    elseif ($msg -match 'invalid after a property name') {
        $phrase = "Expecting ':' delimiter"
    }
    elseif ($msg -match 'contains a trailing comma') {
        $phrase = 'Expecting property name enclosed in double quotes'
    }
    elseif ($msg -match 'invalid after a single JSON value|Expected depth to be zero') {
        $phrase = 'Extra data'
    }
    elseif ($msg -match 'invalid end of a number|invalid after a value') {
        $phrase = "Expecting ',' delimiter"
    }

    $charIndex = -1
    if ($jsonEx.PSObject.Properties['LineNumber'] -and $jsonEx.PSObject.Properties['BytePositionInLine']) {
        $lineNo = $jsonEx.LineNumber
        $bytePos = $jsonEx.BytePositionInLine
        if ($null -ne $lineNo -and $null -ne $bytePos) {
            $charIndex = Get-CharIndexFromLineByte $Text ([int]$lineNo) ([int]$bytePos)
            if ($backOne -and $charIndex -gt 0) { $charIndex-- }
        }
    }

    if ($null -ne $phrase -and $charIndex -ge 0) {
        return $phrase + ': ' + (Get-PyPositionText $Text $charIndex)
    }

    # 无法折算:退化为 .NET 的原始信息(已知与 CPython 文案不同)
    $plain = $msg -replace '\s*LineNumber:\s*\d+\s*\|\s*BytePositionInLine:\s*\d+\.?\s*$', ''
    return $plain.Trim()
}

# ---------------------------------------------------------------------------
# 读取 + 解析结果对象
#   Kind = 'ok'      Keys/Map 可用
#   Kind = 'failure' 对应 CPython 被捕获的异常(OSError / JSONDecodeError)→ stdout 打 [FAIL]
#   Kind = 'fatal'   对应 CPython 未捕获的异常(UnicodeDecodeError / TypeError)→ 无 stdout,退出码 1
# ---------------------------------------------------------------------------
function New-LangOk {
    param([string[]]$Keys, $Map, [bool]$IsObject, [string]$SubscriptError = '')
    return [pscustomobject]@{ Ok = $true; Kind = 'ok'; Message = ''; Keys = $Keys; Map = $Map; IsObject = $IsObject; SubscriptError = $SubscriptError }
}

function New-LangReadFailure {
    param([string]$Message)
    return [pscustomobject]@{ Ok = $false; Kind = 'failure'; Message = $Message; Keys = $null; Map = $null; IsObject = $false; SubscriptError = '' }
}

function New-LangFatal {
    param([string]$Message)
    return [pscustomobject]@{ Ok = $false; Kind = 'fatal'; Message = $Message; Keys = $null; Map = $null; IsObject = $false; SubscriptError = '' }
}

# 等价于 json.loads(path.read_text(encoding="utf-8"))
function Read-LangJson {
    param([string]$Path)

    if (-not [System.IO.File]::Exists($Path)) {
        $shown = Get-PyStrRepr $Path
        if ([System.IO.Directory]::Exists($Path)) {
            return (New-LangReadFailure "[Errno 21] Is a directory: $shown")
        }
        return (New-LangReadFailure "[Errno 2] No such file or directory: $shown")
    }

    try {
        $bytes = [System.IO.File]::ReadAllBytes($Path)
    } catch [System.UnauthorizedAccessException] {
        return (New-LangReadFailure "[Errno 13] Permission denied: $(Get-PyStrRepr $Path)")
    } catch [System.IO.IOException] {
        return (New-LangReadFailure "[Errno 13] Permission denied: $(Get-PyStrRepr $Path)")
    }

    # 严格 UTF-8 解码(与 read_text(encoding="utf-8") 一致:不吞 BOM、遇非法字节抛错)
    $text = $null
    try {
        $text = ([System.Text.UTF8Encoding]::new($false, $true)).GetString($bytes)
    } catch [System.Text.DecoderFallbackException] {
        # CPython: UnicodeDecodeError 不被 except (OSError, JSONDecodeError) 捕获 → 未捕获异常
        return (New-LangFatal ('UnicodeDecodeError: ' + (Get-Utf8DecodeErrorText $bytes ([int]$_.Exception.Index))))
    }

    # 注意:必须用序数判断 —— StartsWith(string) 是区域性比较,会忽略 U+FEFF
    if ($text.Length -gt 0 -and [int]$text[0] -eq 0xFEFF) {
        return (New-LangReadFailure 'Unexpected UTF-8 BOM (decode using utf-8-sig): line 1 column 1 (char 0)')
    }

    # 严格解析:JsonDocument 默认禁止尾逗号与注释,与 CPython json 一致
    # (ConvertFrom-Json 会接受尾逗号/注释,不能用于此处)
    $doc = $null
    try {
        $doc = [System.Text.Json.JsonDocument]::Parse($text)
    } catch {
        return (New-LangReadFailure (Get-PythonJsonErrorText $text $_.Exception))
    }

    try {
        $root = $doc.RootElement

        if ($root.ValueKind -eq [System.Text.Json.JsonValueKind]::Object) {
            $map = [System.Collections.Hashtable]::new([System.StringComparer]::Ordinal)
            foreach ($prop in $root.EnumerateObject()) {
                $val = $null
                if ($prop.Value.ValueKind -eq [System.Text.Json.JsonValueKind]::String) { $val = $prop.Value.GetString() }
                $map[$prop.Name] = $val
            }
            return (New-LangOk ([string[]]@($map.Keys)) $map $true)
        }

        if ($root.ValueKind -eq [System.Text.Json.JsonValueKind]::Array) {
            $keys = [System.Collections.Generic.List[string]]::new()
            foreach ($item in $root.EnumerateArray()) {
                switch ($item.ValueKind) {
                    ([System.Text.Json.JsonValueKind]::String) { $keys.Add([string]$item.GetString()) }
                    ([System.Text.Json.JsonValueKind]::Number) { $keys.Add($item.GetRawText()) }
                    ([System.Text.Json.JsonValueKind]::True) { $keys.Add('True') }
                    ([System.Text.Json.JsonValueKind]::False) { $keys.Add('False') }
                    ([System.Text.Json.JsonValueKind]::Null) { $keys.Add('None') }
                    default { return (New-LangFatal "TypeError: unhashable type: '" + $item.ValueKind.ToString().ToLower() + "'") }
                }
            }
            return (New-LangOk ([string[]]$keys.ToArray()) $null $false 'TypeError: list indices must be integers or slices, not str')
        }

        if ($root.ValueKind -eq [System.Text.Json.JsonValueKind]::String) {
            # CPython: set("abc") == {'a','b','c'}(然后取值阶段必然 TypeError)
            $chars = [string[]]@($root.GetString().ToCharArray() | ForEach-Object { [string]$_ })
            return (New-LangOk $chars $null $false "TypeError: string indices must be integers, not 'str'")
        }

        # Number / True / False / Null:CPython 的 set(...) 直接 TypeError(object is not iterable)
        $typeName = switch ($root.ValueKind) {
            ([System.Text.Json.JsonValueKind]::Number) { 'int' }
            ([System.Text.Json.JsonValueKind]::True) { 'bool' }
            ([System.Text.Json.JsonValueKind]::False) { 'bool' }
            default { 'NoneType' }
        }
        return (New-LangFatal "TypeError: '" + $typeName + "' object is not iterable")
    } finally {
        if ($null -ne $doc) { $doc.Dispose() }
    }
}

function New-OrdinalSet {
    param([string[]]$Items)
    $set = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    foreach ($item in @($Items)) { [void]$set.Add([string]$item) }
    # 必须用逗号包裹:否则 PowerShell 会把集合展开成流,单元素时退化为标量
    return , $set
}

function Sort-Ordinal {
    param([string[]]$Items)
    $arr = [string[]]@($Items)
    [System.Array]::Sort($arr, [System.StringComparer]::Ordinal)
    return , $arr
}

# ---------------------------------------------------------------------------
# 主流程(与 check_lang_sync.py 的 main() 一一对应)
# ---------------------------------------------------------------------------

$zhPath = Join-Path $LangDir 'zh_cn.json'
$enPath = Join-Path $LangDir 'en_us.json'

$zh = Read-LangJson $zhPath
if (-not $zh.Ok) {
    if ($zh.Kind -eq 'fatal') {
        # CPython 未捕获异常:stdout 为空,异常打到 stderr,退出码 1
        Write-Stderr $zh.Message
        exit 1
    }
    Write-Stdout "[FAIL] 无法读取/解析语言文件: $($zh.Message)"
    exit 1
}
$en = Read-LangJson $enPath
if (-not $en.Ok) {
    if ($en.Kind -eq 'fatal') {
        Write-Stderr $en.Message
        exit 1
    }
    Write-Stdout "[FAIL] 无法读取/解析语言文件: $($en.Message)"
    exit 1
}

$zhSet = New-OrdinalSet $zh.Keys
$enSet = New-OrdinalSet $en.Keys

$missingInEn = Sort-Ordinal ([string[]]@($zhSet | Where-Object { -not $enSet.Contains($_) }))
$extraInEn = Sort-Ordinal ([string[]]@($enSet | Where-Object { -not $zhSet.Contains($_) }))

$errors = 0
if ($missingInEn.Count -gt 0) {
    $errors++
    Write-Stdout '[FAIL] 以下 key 存在于 zh_cn.json 但缺失于 en_us.json(请在 en_us.json 补充对应英文):'
    foreach ($key in $missingInEn) { Write-Stdout ('  - ' + $key) }
}
if ($extraInEn.Count -gt 0) {
    $errors++
    Write-Stdout '[FAIL] 以下 key 存在于 en_us.json 但缺失于 zh_cn.json(请同步删除或补回中文):'
    foreach ($key in $extraInEn) { Write-Stdout ('  - ' + $key) }
}

$common = Sort-Ordinal ([string[]]@($zhSet | Where-Object { $enSet.Contains($_) }))

# 极退化输入:根节点不是对象时,CPython 会在 zh[key] / en[key] 上抛 TypeError(未捕获)
if ($common.Count -gt 0 -and (-not $zh.IsObject -or -not $en.IsObject)) {
    $which = if (-not $zh.IsObject) { $zh } else { $en }
    Write-Stderr $which.SubscriptError
    exit 1
}

foreach ($key in $common) {
    $zhVal = $null
    $enVal = $null
    if ($zh.IsObject) { $zhVal = $zh.Map[$key] }
    if ($en.IsObject) { $enVal = $en.Map[$key] }
    if (-not ($zhVal -is [string]) -or -not ($enVal -is [string])) {
        continue
    }

    # 未转义的字面百分号检查:单 %(非 %% 且非合法说明符)经 I18n.get/String.format
    # 会抛异常并显示 "Format error: ..."(如帕秋莉手册文本),必须写成 %%。
    $pairs = @(
        [pscustomobject]@{ Name = 'zh'; Text = $zhVal },
        [pscustomobject]@{ Name = 'en'; Text = $enVal }
    )
    foreach ($pair in $pairs) {
        $text = $pair.Text
        foreach ($m in [regex]::Matches($text, '%')) {
            $pos = $m.Index
            # %% 转义对(当前 % 是 %% 的第一个或第二个字符)跳过
            if ((Get-PySlice $text $pos ($pos + 2)) -eq '%%' -or ($pos -gt 0 -and $text[$pos - 1] -eq '%')) {
                continue
            }
            $seg = Get-PySlice $text $pos ($pos + 4)
            if ([regex]::IsMatch($seg, '^%([sdbfxoeg]|\d+\$[sdbfxoeg])')) {
                continue
            }
            $window = Get-PyCodePointWindow $text $pos 12 12
            Write-Stdout "[WARN] ${key}($($pair.Name)): 含未转义字面百分号(应写 %%,否则 I18n.get/String.format 显示 Format Error): ...${window}..."
        }
    }

    $zm = Get-StructureMarkerRepr $zhVal
    $em = Get-StructureMarkerRepr $enVal
    if ($zm -ne $em) {
        Write-Stdout "[WARN] ${key}: 结构标记不一致(占位符/换行/颜色码) zh=$zm en=$em"
    }
}

if ($errors) {
    Write-Stdout "`n语言文件未同步:请把 zh_cn.json 的手动修改同步至 en_us.json(同一 key 中英对应)后再提交。"
    exit 1
}
Write-Stdout "OK: zh_cn.json($($zhSet.Count) keys) 与 en_us.json($($enSet.Count) keys) key 完全一致。"
exit 0
