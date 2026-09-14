# -*- coding: utf-8 -*-
<#
筹码配方统一规范 — 三档校验(源码 / 生成资源 / jar)。

用法(仓库根目录执行):
    pwsh -File scripts/verify/verify_chip_recipes.ps1              # 三档 × 双版本全跑
    pwsh -File scripts/verify/verify_chip_recipes.ps1 java neo      # 只校验 neo 源码
    pwsh -File scripts/verify/verify_chip_recipes.ps1 gen           # 双版本生成资源
    pwsh -File scripts/verify/verify_chip_recipes.ps1 jar forge     # 只校验 forge jar

三档各管什么(必须都跑过才算真正验证,不能只看 dataGen 日志的 written: N):
  java  — 解析 ModRecipeProvider.java,与目标配方逐格比对(及时发现漏改/写错)
  gen   — 解析 dataGen 产物 json,确认「Provider 改对了」且「生成真的落到了文件」
  jar   — 开 jar 读 data/.../recipe{,s}/*.json,确认打包产物里就是新配方

退出码 0 = 全绿;1 = 有不一致。

要点(踩过的坑):
  * 符号->材料 item id 必须独立构造,不能从 chips 表取(材料不是筹码)。
  * 药水原料:neo 为无 item 键的 components ingredient;forge 为带 type+nbt 的
    partial_nbt 且含 item=potion —— 两者都归约为 POTION_EQUIV 再比对。
  * 1.20.1 无 1.21 新增物品须走 ChipCommon 的 PLATFORM_OVERRIDE(如 MACE->ANVIL),
    并同步登记到 docs/compat-1.20.1-forge.md,不得强行统一成 1.21 物品。

—— PowerShell 移植版:1:1 对应 scripts/verify/verify_chip_recipes.py(原 .py 保留不删)。
#>
[CmdletBinding()]
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$Arguments
)

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

# ---------------------------------------------------------------------------
# 输出:统一经 [Console]::Out 写显式换行,不经 Write-Output / Write-Host
#        (换行恒为 LF —— 本仓标准输出契约
#         Python 侧 print() 的 CRLF 属解释器副产物,比对器会折行归一)
# ---------------------------------------------------------------------------
function Write-Out([string]$text) {
    [Console]::Out.Write($text)
    [Console]::Out.Write("`n")
}

# ---------------------------------------------------------------------------
# Python repr 的最小复刻(仅用于不一致时的诊断输出)
# ---------------------------------------------------------------------------
function New-PyTuple {
    param([object[]]$Items)
    return [pscustomobject]@{ __pytuple = $Items }
}

function ConvertTo-PyRepr {
    param($Value)
    if ($null -eq $Value) { return 'None' }
    if ($Value -is [bool]) { if ($Value) { return 'True' } else { return 'False' } }
    if ($Value -is [string]) {
        return "'" + $Value.Replace('\', '\\').Replace("'", "\'").Replace("`n", '\n').Replace("`r", '\r') + "'"
    }
    if (($Value -is [psobject]) -and ($Value.PSObject.Properties.Name -contains '__pytuple')) {
        $parts = @()
        foreach ($x in @($Value.__pytuple)) { $parts += (ConvertTo-PyRepr $x) }
        if ($parts.Count -eq 1) { return '(' + $parts[0] + ',)' }
        return '(' + ($parts -join ', ') + ')'
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

# Python 的 [(r, c, exp, got), ...] 形态
function ConvertTo-DiffRepr {
    param($d)
    $parts = @()
    foreach ($t in $d) {
        $parts += (ConvertTo-PyRepr $t)
    }
    return '[' + ($parts -join ', ') + ']'
}

# ---------------------------------------------------------------------------
# 载入公共数据(对应 Python 的 import chip_common as CC)
# ---------------------------------------------------------------------------
Import-Module (Join-Path $PSScriptRoot 'ChipCommon.psm1') -Force -PassThru | Out-Null

$SUB = @{ 'neo' = 'neoforge-1.21.1'; 'forge' = 'forge-1.20.1' }
$RECDIR = @{ 'neo' = 'recipe'; 'forge' = 'recipes' }
# 产物 jar 名从各子项目 gradle.properties 的 mod_version 派生
# (禁止硬编码版本号:mod_version 已自带 `+neoforge_1.21.1` / `+forge_1.20.1` 后缀,
#  旧实现写死 $VER = '1.2.0',版本升到 1.2.1 后本档一直在找不存在的 1.2.0 jar 而误报失败)
$MODVER = @{}
$JAR = @{}
foreach ($k in $SUB.Keys) {
    $propsPath = Join-Path $ROOT "$($SUB[$k])\gradle.properties"
    $verLine = Get-Content $propsPath | Where-Object { $_ -match '^\s*mod_version\s*=' } | Select-Object -First 1
    if (-not $verLine) { throw "未在 $propsPath 中找到 mod_version" }
    $MODVER[$k] = ($verLine -split '=', 2)[1].Trim()
    $JAR[$k] = "astral_dice-$($MODVER[$k]).jar"
}

# 非筹码的模组常量 -> 物品 id
$EXTRA_MOD = @{ 'DICE' = 'dice'; 'STAR_COIN_BAG' = 'star_coin_bag'; 'ORBITAL_STRIKE_CARD' = 'effect_card_orbital_strike' }
# 符号 -> 材料物品 id(材料不在 chips 表内,直接由 ModItems 常量名推导)
$SYM2ITEM = @{}
foreach ($e in $SYM_CONST.GetEnumerator()) { $SYM2ITEM[$e.Key] = 'astral_dice:' + $e.Value.ToLower() }


function expected_cell {
    param($tok)
    if (($tok -is [string]) -and $tok.StartsWith('MC:')) {
        return 'minecraft:' + $tok.Substring(3).ToLower()
    }
    if (($tok -is [string]) -and $tok.StartsWith('MOD:')) {
        $name = $tok.Substring(4)
        if ($EXTRA_MOD.ContainsKey($name)) {
            return 'astral_dice:' + $EXTRA_MOD[$name]
        }
        if ($chips.Contains($name)) {
            return 'astral_dice:' + $chips[$name]['id']
        }
        return $null
    }
    if (($tok -is [string]) -and $tok.StartsWith('POTION')) {
        return 'POTION_EQUIV'
    }
    if ($null -ne $tok -and $SYM2ITEM.ContainsKey([string]$tok)) {
        return $SYM2ITEM[[string]$tok]
    }
    return $null
}


function expected {
    param($dialect, $const)
    $g = _plat (target $const) $dialect
    $out = @()
    foreach ($row in $g) {
        $line = @()
        foreach ($t in $row) { $line += (expected_cell $t) }
        $out += , $line
    }
    return , $out
}


function diff3 {
    param($exp, $got)
    $bad = @()
    $shapeBad = $false
    if (@($got).Count -ne 3) {
        $shapeBad = $true
    }
    else {
        foreach ($r in $got) { if (@($r).Count -ne 3) { $shapeBad = $true } }
    }
    if ($shapeBad) {
        return , @((New-PyTuple @('shape', ('非 3x3: ' + (ConvertTo-PyRepr $got)))))
    }
    for ($r = 0; $r -lt 3; $r++) {
        for ($c = 0; $c -lt 3; $c++) {
            if ($exp[$r][$c] -cne $got[$r][$c]) {
                $bad += , (New-PyTuple @($r, $c, $exp[$r][$c], $got[$r][$c]))
            }
        }
    }
    return , $bad
}


# ---- 档 1:源码 ----
function check_java {
    param($dialect)
    $path = [System.IO.Path]::Combine($ROOT, $SUB[$dialect], 'src', 'main', 'java', 'com',
        'merlinkitsune', 'astral_dice', 'datagen', 'ModRecipeProvider.java')
    $parsed = parse_file $path
    return , @($parsed, $path)
}


# ---- 档 2:生成资源 ----
function jar_json {
    <#
    返回 {relative_name: raw} 供 gen / jar 两档共用
    #>
    param($dialect)
    $d = [System.IO.Path]::Combine($ROOT, $SUB[$dialect], 'src', 'generated', 'resources', 'data',
        'astral_dice', $RECDIR[$dialect])
    $out = @{}
    foreach ($f in [System.IO.Directory]::GetFiles($d)) {
        if ($f.EndsWith('.json')) {
            $out[[System.IO.Path]::GetFileName($f)] = (Read-PyText $f)
        }
    }
    return , $out
}


function check_jar {
    param($dialect)
    $p = [System.IO.Path]::Combine($ROOT, 'build', 'libs', $JAR[$dialect])
    $zip = [System.IO.Compression.ZipFile]::OpenRead($p)
    try {
        $pre = 'data/astral_dice/' + $RECDIR[$dialect] + '/'
        $out = @{}
        foreach ($e in $zip.Entries) {
            $n = $e.FullName
            if ($n.StartsWith($pre) -and $n.EndsWith('.json')) {
                $stream = $e.Open()
                try {
                    $sr = New-Object System.IO.StreamReader($stream, [System.Text.Encoding]::UTF8)
                    try { $out[$n.Substring($pre.Length)] = $sr.ReadToEnd() } finally { $sr.Dispose() }
                }
                finally { $stream.Dispose() }
            }
        }
        return , $out
    }
    finally {
        $zip.Dispose()
    }
}


function cells_from_json {
    param($raw)
    $j = ConvertFrom-Json -InputObject $raw -AsHashtable
    $pat = @()
    if ($j.Contains('pattern')) { $pat = @($j['pattern']) }
    $key = $null
    if ($j.Contains('key')) { $key = $j['key'] }
    $out = @()
    foreach ($row in $pat) {
        $line = @()
        foreach ($ch in ([string]$row).ToCharArray()) {
            if ($ch -ceq ' ') {
                $line += $null
                continue
            }
            $cell = @{}
            if (($key -is [System.Collections.IDictionary]) -and $key.Contains([string]$ch)) {
                $cell = $key[[string]$ch]
            }
            # 药水:neo 无 item 键;forge 带 type+nbt 且 item=potion
            if ($cell -is [System.Collections.IDictionary]) {
                $hasItem = $cell.Contains('item')
                $item = $null
                if ($hasItem) { $item = $cell['item'] }
                $hasType = $cell.Contains('type')
                if ($hasItem -and -not ($hasType -and ($item -ceq 'minecraft:potion'))) {
                    $line += $item
                }
                else {
                    $line += 'POTION_EQUIV'
                }
            }
            elseif ($cell -is [string]) {
                if ($cell.Contains('item')) { $line += $cell } else { $line += 'POTION_EQUIV' }
            }
            else {
                if (@($cell) -contains 'item') { $line += $null } else { $line += 'POTION_EQUIV' }
            }
        }
        $out += , $line
    }
    return , $out
}


function run {
    param($dialect, $modes)
    Write-Out ('== ' + $dialect + ' (' + $SUB[$dialect] + ') ==')
    $total = $chips.Count
    $failures = 0
    if ($modes -ccontains 'java') {
        $r = check_java $dialect
        $parsed = $r[0]
        $path = $r[1]
        $ok = 0
        $bad = @()
        $miss = @()
        foreach ($const in $chips.Keys) {
            if (-not $parsed.Contains($const)) {
                $miss += $const
                continue
            }
            # 源码档在 token 空间比对(parse_file 还原出的就是目标表的 token 形式)
            $exp = _plat (target $const) $dialect
            $d = diff3 $exp $parsed[$const]
            if (@($d).Count -gt 0) { $bad += , @($const, $d) } else { $ok += 1 }
        }
        $failures += $bad.Count + $miss.Count
        Write-Out ('  [java] ' + [System.IO.Path]::GetRelativePath($ROOT, $path))
        Write-Out ('  [java] 筹码 ' + $total + ' | 一致 ' + $ok + ' | 不一致 ' + $bad.Count + ' | 缺配方 ' + $miss.Count)
        $lim = [Math]::Min(10, $bad.Count)
        for ($i = 0; $i -lt $lim; $i++) {
            Write-Out ('        ✗ ' + $bad[$i][0] + ' ' + (ConvertTo-DiffRepr $bad[$i][1]))
        }
        if ($miss.Count -gt 0) {
            Write-Out ('        缺: ' + (ConvertTo-PyRepr $miss))
        }
    }
    foreach ($mode in @('gen', 'jar')) {
        if (-not ($modes -ccontains $mode)) {
            continue
        }
        $src = $null
        if ($mode -eq 'gen') { $src = jar_json $dialect } else { $src = check_jar $dialect }
        $ok = 0
        $bad = @()
        $miss = @()
        foreach ($const in $chips.Keys) {
            $info = $chips[$const]
            $name = $info['id'] + '.json'
            if (-not $src.ContainsKey($name)) {
                $miss += $const
                continue
            }
            $d = diff3 (expected $dialect $const) (cells_from_json $src[$name])
            if (@($d).Count -gt 0) { $bad += , @($const, $d) } else { $ok += 1 }
        }
        $failures += $bad.Count + $miss.Count
        Write-Out ('  [' + $mode + '] 配方文件 ' + $src.Count + ' | 筹码 ' + $total + ' | 一致 ' + $ok + ' | 不一致 ' + $bad.Count + ' | 缺文件 ' + $miss.Count)
        $lim = [Math]::Min(10, $bad.Count)
        for ($i = 0; $i -lt $lim; $i++) {
            Write-Out ('        ✗ ' + $bad[$i][0] + ' ' + (ConvertTo-DiffRepr $bad[$i][1]))
        }
        if ($miss.Count -gt 0) {
            Write-Out ('        缺: ' + (ConvertTo-PyRepr $miss))
        }
    }
    Write-Out ''
    return $failures
}


# ---------------------------------------------------------------------------
# main
# ---------------------------------------------------------------------------
$modes = @($Arguments | Where-Object { $_ -cin @('java', 'gen', 'jar') })
if ($modes.Count -eq 0) { $modes = @('java', 'gen', 'jar') }
$dialects = @($Arguments | Where-Object { $_ -cin @('neo', 'forge') })
if ($dialects.Count -eq 0) { $dialects = @('neo', 'forge') }
$fails = 0
foreach ($d in $dialects) {
    $fails += (run $d $modes)
}
if ($fails) {
    Write-Out ('RESULT: ' + $fails + ' 处不一致')
    $code = 1
}
else {
    Write-Out 'RESULT: ALL OK'
    $code = 0
}
[Console]::Out.Flush()
exit $code
