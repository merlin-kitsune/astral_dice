#!/usr/bin/env pwsh
# -*- coding: utf-8 -*-
<#
筹码「配方 / 获取途径」守门校验（只读）。

在 mc-recipe-manual-audit（物品→配方→手册三方对齐）之上补一层**获取途径可达性**：
配方存在 ≠ 做得出。本脚本对 astral_dice:chips 全部成员做**依赖传递闭包**，
并顺带核对 curios 装备槽 / 创造栏 / 双版本对等。

可达性定义（迭代至不动点）：
  种子 = 有战利品表或赏金池来源 / 有代码发放（LootInjectionHandler 等）/ 配方不含任何本模组物品
  迭代 = 配方（任一）的全部本模组材料均已可达 → 该物品可达
  标签材料（如 #astral_dice:dice_t3）展开为其成员

退出码：0 = 无致命问题（手册缺口仅告警）；1 = 存在缺配方/不可达/悬空引用/槽缺项/对等性偏差；2 = 运行错误。

用法：
  pwsh -File scripts/verify/verify_chip_acquisition.ps1 [-Root <仓库根>]

—— PowerShell 移植版:1:1 对应 scripts/verify/verify_chip_acquisition.py(原 .py 保留不删)。
#>
[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [string]$Root = '.'
)

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8
$ErrorActionPreference = 'Stop'

$ROOT = [System.IO.Path]::GetFullPath($Root)
$NS = 'astral_dice'
# 有意排除 curios 槽的纯基底材料（非 ICurioItem，本就不该装备）
$SLOT_EXEMPT = @('blank_chip', 'blank_sign')

# ---------------------------------------------------------------------------
# 输出:统一经 [Console]::Out 写显式 LF（不得 CRLF;与 Mt.Phase 的 Write-MtLine 同契约)
#       Python print() 在 Windows 上把 "\n" 按 os.linesep 翻译成 CRLF 的行为）
# ---------------------------------------------------------------------------
function Write-Out([string]$text) {
    [Console]::Out.Write($text)
    [Console]::Out.Write("`n")
}

# ---------------------------------------------------------------------------
# Python 语义的小工具（大小写敏感的 dict / set,与 Python 一致）
# ---------------------------------------------------------------------------
function New-Map {
    return , ([System.Collections.Specialized.OrderedDictionary]::new([System.StringComparer]::Ordinal))
}

function New-StrSet {
    return , ([System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal))
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

function Sort-Ordinal {
    param($Values)
    $arr = @($Values)
    [Array]::Sort($arr, [System.StringComparer]::Ordinal)
    return , $arr
}

# 递归收集文件（等价 Python 的 os.walk / glob ** 语义:目录不可读时静默跳过）
function Get-FilesRecursive {
    param([string]$Base, [string]$Suffix)
    if (-not [System.IO.Directory]::Exists($Base)) { return }
    $out = New-Object System.Collections.Generic.List[string]
    $stack = New-Object System.Collections.Generic.Stack[string]
    $stack.Push($Base)
    while ($stack.Count -gt 0) {
        $d = $stack.Pop()
        $files = @()
        try { $files = [System.IO.Directory]::EnumerateFiles($d) } catch { $files = @() }
        foreach ($f in $files) {
            if ($f.EndsWith($Suffix)) { $out.Add($f) }
        }
        $dirs = @()
        try { $dirs = [System.IO.Directory]::EnumerateDirectories($d) } catch { $dirs = @() }
        foreach ($sd in $dirs) { $stack.Push($sd) }
    }
    foreach ($x in $out) { $x }
}

function Get-JsonFiles {
    param([string]$Base)
    Get-FilesRecursive $Base '.json'
}

function Read-Json {
    param([string]$Path)
    try {
        $txt = [System.IO.File]::ReadAllText($Path, [System.Text.Encoding]::UTF8)
        return (ConvertFrom-Json -InputObject $txt -AsHashtable)
    }
    catch {
        return $null
    }
}

# 从 tag json 的 values 项取出字符串（对应 Python 的 v if isinstance(v, str) else v.get("id", "")）
function Get-TagValueString {
    param($v)
    if ($v -is [string]) { return $v }
    if ($v -is [System.Collections.IDictionary]) {
        if ($v.Contains('id')) { return [string]$v['id'] }
        return ''
    }
    return [string]$v
}

function Get-IsDir {
    param([string]$Path)
    return [System.IO.Directory]::Exists($Path)
}

# ---------------------------------------------------------------------------
# 工程事实重算（对应 Python 的同名函数）
# ---------------------------------------------------------------------------
function Get-ModItems {
    param([string]$sub)
    $base = [System.IO.Path]::Combine($ROOT, $sub, 'src', 'main', 'java')
    $src = @(Get-FilesRecursive $base 'ModItems.java')
    if ($src.Count -eq 0) {
        throw "${sub}: 找不到 ModItems.java"
    }
    $txt = [System.IO.File]::ReadAllText($src[0], [System.Text.Encoding]::UTF8)
    $c2i = New-Map
    $i2c = New-Map
    foreach ($m in [regex]::Matches($txt, '\b(\w+)\s*=\s*registerItem\(\s*"([a-z0-9_]+)"')) {
        $c = $m.Groups[1].Value
        $i = $m.Groups[2].Value
        if (-not $c2i.Contains($c)) { $c2i[$c] = $i }
        if (-not $i2c.Contains($i)) { $i2c[$i] = $c }
    }
    return , @($c2i, $i2c)
}


function Get-TagMembers {
    <#
    rel 形如 'tags/item/chips.json'；1.20.1 自动尝试 'tags/items/'。
    #>
    param([string]$sub, [string]$rel, [string]$ns = $NS)
    $base = [System.IO.Path]::Combine($ROOT, $sub, 'src', 'main', 'resources', 'data', $ns)
    foreach ($cand in @($rel, $rel.Replace('tags/item/', 'tags/items/'))) {
        $p = [System.IO.Path]::Combine($base, $cand)
        if ([System.IO.File]::Exists($p)) {
            $j = Read-Json $p
            $vals = @()
            if (($j -is [System.Collections.IDictionary]) -and $j.Contains('values')) { $vals = @($j['values']) }
            $set = New-StrSet
            foreach ($v in $vals) { [void]$set.Add((Get-TagValueString $v)) }
            return , $set
        }
    }
    return , (New-StrSet)
}


function Get-AllTags {
    <#
    {tag_name: set(id)}，覆盖 tags/item(s)/**。
    #>
    param([string]$sub, [string]$ns = $NS)
    $out = New-Map
    foreach ($tdir in @('tags/item', 'tags/items')) {
        $base = [System.IO.Path]::Combine($ROOT, $sub, 'src', 'main', 'resources', 'data', $ns, $tdir)
        if (-not (Get-IsDir $base)) { continue }
        foreach ($f in (Get-JsonFiles $base)) {
            $rel = [System.IO.Path]::GetRelativePath($base, $f)
            $name = $rel.Substring(0, $rel.Length - 5).Replace('\', '/')
            $j = Read-Json $f
            if (-not ($j -is [System.Collections.IDictionary])) { continue }
            if (-not $j.Contains('values')) { continue }
            foreach ($v in @($j['values'])) {
                $s = Get-TagValueString $v
                if ($s.StartsWith($NS + ':')) {
                    if (-not $out.Contains($name)) { $out[$name] = New-StrSet }
                    $parts = $s -split ':'
                    [void]$out[$name].Add($parts[$parts.Count - 1])
                }
            }
        }
    }
    return , $out
}


function Get-Recipes {
    <#
    {result_id: [(file, type, json)]}
    #>
    param([string]$sub)
    $out = New-Map
    foreach ($d in @('src/generated/resources/data', 'src/main/resources/data')) {
        foreach ($rdir in @('recipe', 'recipes')) {
            $base = [System.IO.Path]::Combine($ROOT, $sub, ($d -replace '/', [System.IO.Path]::DirectorySeparatorChar), $NS, $rdir)
            if (-not (Get-IsDir $base)) { continue }
            $names = @()
            foreach ($f in [System.IO.Directory]::GetFiles($base)) { $names += [System.IO.Path]::GetFileName($f) }
            $names = Sort-Ordinal $names
            foreach ($fn in $names) {
                if (-not $fn.EndsWith('.json')) { continue }
                $j = Read-Json ([System.IO.Path]::Combine($base, $fn))
                if (-not ($j -is [System.Collections.IDictionary])) { continue }
                $r = $null
                if ($j.Contains('result')) { $r = $j['result'] }
                $rid = $null
                if ($r -is [System.Collections.IDictionary]) {
                    if ($r.Contains('id') -and $r['id']) { $rid = $r['id'] }
                    elseif ($r.Contains('item')) { $rid = $r['item'] }
                }
                else {
                    $rid = $r
                }
                if ($rid) {
                    $parts = ([string]$rid) -split ':'
                    $key = $parts[$parts.Count - 1]
                    if (-not $out.Contains($key)) { $out[$key] = New-Object System.Collections.Generic.List[object] }
                    $type = '?'
                    if ($j.Contains('type') -and $null -ne $j['type']) { $type = $j['type'] }
                    $out[$key].Add(@($fn, $type, $j))
                }
            }
        }
    }
    return , $out
}


function Get-Ingredients {
    param($j)
    $mods = New-StrSet
    $tgs = New-StrSet

    $walk = {
        param($o)
        if ($o -is [string]) {
            if ($o.StartsWith('#' + $NS + ':')) {
                $p = ($o.Substring(1)) -split ':'
                [void]$tgs.Add($p[$p.Count - 1])
            }
            elseif ($o.StartsWith($NS + ':')) {
                $p = $o -split ':'
                [void]$mods.Add($p[$p.Count - 1])
            }
        }
        elseif ($o -is [System.Collections.IDictionary]) {
            foreach ($k in @('item', 'id')) {
                if ($o.Contains($k) -and ($o[$k] -is [string])) { & $walk $o[$k] }
            }
            $t = $null
            if ($o.Contains('tag') -and ($o['tag'] -is [string])) { $t = $o['tag'] }
            if ($null -ne $t) {
                if ($t.StartsWith('#' + $NS + ':')) {
                    $p = ($t.Substring(1)) -split ':'
                    [void]$tgs.Add($p[$p.Count - 1])
                }
                elseif ($t.StartsWith($NS + ':')) {
                    $p = $t -split ':'
                    [void]$tgs.Add($p[$p.Count - 1])
                }
            }
            foreach ($k in @('items', 'ingredients', 'components')) {
                if ($o.Contains($k)) { & $walk $o[$k] }
            }
        }
        elseif ($o -is [System.Collections.IEnumerable]) {
            foreach ($x in $o) { & $walk $x }
        }
    }

    foreach ($k in @('key', 'ingredients')) {
        if ($j.Contains($k)) { & $walk $j[$k] }
    }
    return , @($mods, $tgs)
}


function Get-LootSources {
    param([string]$sub)
    $hits = New-StrSet
    $bases = @(
        [System.IO.Path]::Combine($ROOT, $sub, 'src', 'main', 'resources', 'data', $NS, 'loot_table'),
        [System.IO.Path]::Combine($ROOT, $sub, 'src', 'main', 'resources', 'data', $NS, 'loot_tables'),
        [System.IO.Path]::Combine($ROOT, $sub, 'src', 'main', 'resources', 'data', 'bountiful')
    )
    foreach ($base in $bases) {
        if (-not (Get-IsDir $base)) { continue }
        foreach ($f in (Get-JsonFiles $base)) {
            $t = [System.IO.File]::ReadAllText($f, [System.Text.Encoding]::UTF8)
            foreach ($m in [regex]::Matches($t, ('"' + $NS + ':([a-z0-9_]+)"'))) {
                [void]$hits.Add($m.Groups[1].Value)
            }
        }
    }
    return , $hits
}


function Get-CodeGives {
    param([string]$sub, $i2c)
    $ids = New-StrSet
    foreach ($f in (Get-FilesRecursive ([System.IO.Path]::Combine($ROOT, $sub, 'src', 'main', 'java')) '.java')) {
        $t = [System.IO.File]::ReadAllText($f, [System.Text.Encoding]::UTF8)
        foreach ($m in [regex]::Matches($t, 'ModItems\.([A-Z0-9_]+)\.get\(\)')) {
            $c = $m.Groups[1].Value
            if ($i2c.Contains($c)) { [void]$ids.Add($i2c[$c]) } else { [void]$ids.Add($c) }
        }
    }
    return , $ids
}


function Get-ManualCrafting {
    <#
    {item_id: 该手册条目是否有 crafting 页}
    #>
    param([string]$sub)
    $base = [System.IO.Path]::Combine($ROOT, $sub, 'src', 'main', 'resources', 'assets', $NS,
        'patchouli_books', 'astral_guide', 'en_us', 'entries')
    $res = New-Map
    foreach ($f in (Get-JsonFiles $base)) {
        $iid = [System.IO.Path]::GetFileNameWithoutExtension($f)
        $j = Read-Json $f
        $has = $false
        if (($j -is [System.Collections.IDictionary]) -and $j.Contains('pages')) {
            foreach ($p in @($j['pages'])) {
                $ty = ''
                if (($p -is [System.Collections.IDictionary]) -and $p.Contains('type')) { $ty = [string]$p['type'] }
                if ($ty.Contains('crafting')) { $has = $true; break }
            }
        }
        $res[$iid] = $has
    }
    return , $res
}


function Get-Closure {
    param([string]$sub, $i2c, $rec, $tags, $loot, $gives)
    $reach = New-StrSet
    foreach ($iid in $i2c.Keys) {
        if ($loot.Contains($iid) -or $gives.Contains($iid)) { [void]$reach.Add($iid) }
    }
    foreach ($iid in $rec.Keys) {
        foreach ($r in $rec[$iid]) {
            $ing = Get-Ingredients $r[2]
            $need = New-StrSet
            foreach ($m in $ing[0]) { [void]$need.Add($m) }
            foreach ($t in $ing[1]) {
                if ($tags.Contains($t)) { foreach ($x in $tags[$t]) { [void]$need.Add($x) } }
            }
            if ($need.Count -eq 0) {
                [void]$reach.Add($iid)
                break
            }
        }
    }
    while ($true) {
        $grew = $false
        foreach ($iid in $rec.Keys) {
            if ($reach.Contains($iid)) { continue }
            foreach ($r in $rec[$iid]) {
                $ing = Get-Ingredients $r[2]
                $need = New-StrSet
                foreach ($m in $ing[0]) { [void]$need.Add($m) }
                foreach ($t in $ing[1]) {
                    if ($tags.Contains($t)) { foreach ($x in $tags[$t]) { [void]$need.Add($x) } }
                }
                if ($need.IsSubsetOf($reach)) {
                    [void]$reach.Add($iid)
                    $grew = $true
                    break
                }
            }
        }
        if (-not $grew) {
            return , $reach
        }
    }
}


# ---------------------------------------------------------------------------
# main
# ---------------------------------------------------------------------------
function Main {
    $fatal = New-Object System.Collections.Generic.List[string]
    $warn = New-Object System.Collections.Generic.List[string]
    Write-Out ('=' * 78)
    Write-Out '筹码「配方 / 获取途径」守门校验'
    Write-Out ('=' * 78)
    $summary = New-Map

    $subs = @()
    foreach ($d in @('neoforge-1.21.1', 'forge-1.20.1')) {
        if (Get-IsDir ([System.IO.Path]::Combine($ROOT, $d, 'src/main/java'))) { $subs += $d }
    }

    foreach ($sub in $subs) {
        $mi = Get-ModItems $sub
        $c2i = $mi[0]
        $i2c = $mi[1]
        $chipList = @()
        foreach ($x in (Get-TagMembers $sub 'tags/item/chips.json')) {
            $p = $x -split ':'
            $chipList += $p[$p.Count - 1]
        }
        $chipList = Sort-Ordinal $chipList
        $rec = Get-Recipes $sub
        $tags = Get-AllTags $sub
        $loot = Get-LootSources $sub
        $gives = Get-CodeGives $sub $i2c
        $reach = Get-Closure $sub $i2c $rec $tags $loot $gives
        $slot = New-StrSet
        foreach ($x in (Get-TagMembers $sub 'tags/item/chip.json' 'curios')) {
            $p = $x -split ':'
            [void]$slot.Add($p[$p.Count - 1])
        }
        $craft = Get-ManualCrafting $sub
        $creative = New-StrSet
        $ct = @(Get-FilesRecursive ([System.IO.Path]::Combine($ROOT, $sub, 'src', 'main', 'java')) 'ModCreativeTabs.java')
        if ($ct.Count -gt 0) {
            $txt = [System.IO.File]::ReadAllText($ct[0], [System.Text.Encoding]::UTF8)
            foreach ($m in [regex]::Matches($txt, 'output\.accept\(ModItems\.([A-Z0-9_]+)\.get\(\)\)')) {
                [void]$creative.Add($m.Groups[1].Value)
            }
        }
        $creativeIds = New-StrSet
        foreach ($c in $creative) {
            if ($c2i.Contains($c)) { [void]$creativeIds.Add($c2i[$c]) } else { [void]$creativeIds.Add($c) }
        }

        $no_rec = @()
        $unreach = @()
        $dangling = New-Map
        $no_slot = @()
        $no_tab = @()
        $no_page = @()
        foreach ($cid in $chipList) {
            $rlist = $null
            if ($rec.Contains($cid)) { $rlist = $rec[$cid] }
            if ($null -eq $rlist -or $rlist.Count -eq 0) {
                $no_rec += $cid
            }
            else {
                foreach ($r in $rlist) {
                    $ing = Get-Ingredients $r[2]
                    $need = New-StrSet
                    foreach ($m in $ing[0]) { [void]$need.Add($m) }
                    foreach ($t in $ing[1]) {
                        if ($tags.Contains($t)) { foreach ($x in $tags[$t]) { [void]$need.Add($x) } }
                    }
                    foreach ($m in (Sort-Ordinal $need)) {
                        if (-not $i2c.Contains($m)) {
                            if (-not $dangling.Contains($cid)) { $dangling[$cid] = New-Object System.Collections.Generic.List[string] }
                            $dangling[$cid].Add($m)
                        }
                    }
                }
                if (-not $reach.Contains($cid)) { $unreach += $cid }
            }
            if ((-not $slot.Contains($cid)) -and (-not ($SLOT_EXEMPT -contains $cid))) { $no_slot += $cid }
            if (-not $creativeIds.Contains($cid)) { $no_tab += $cid }
            if ($craft.Contains($cid) -and ($craft[$cid] -eq $false)) { $no_page += $cid }
        }

        Write-Out ''
        Write-Out ('### ' + $sub)
        Write-Out ('  筹码 ' + $chipList.Count + ' | 有配方 ' + ($chipList.Count - @($no_rec).Count) + ' | 传递可达 ' + ($chipList.Count - @($unreach).Count))
        Write-Out ('  curios:chip 槽 ' + $slot.Count + ' | 创造栏覆盖 ' + ($chipList.Count - @($no_tab).Count) + '/' + $chipList.Count)

        $checks = @(
            [pscustomobject]@{ label = '❌ 缺配方'; lst = $no_rec; isFatal = $true; isMap = $false }
            [pscustomobject]@{ label = '❌ 传递不可达'; lst = $unreach; isFatal = $true; isMap = $false }
            [pscustomobject]@{ label = '❌ 配方引用未注册物品'; lst = $dangling; isFatal = $true; isMap = $true }
            [pscustomobject]@{ label = '❌ curios:chip 槽缺项'; lst = $no_slot; isFatal = $true; isMap = $false }
            [pscustomobject]@{ label = '❌ 未进创造栏'; lst = $no_tab; isFatal = $true; isMap = $false }
            [pscustomobject]@{ label = '⚠️ 手册未展示 crafting 页'; lst = $no_page; isFatal = $false; isMap = $false }
        )
        foreach ($chk in $checks) {
            $cnt = 0
            if ($chk.isMap) { $cnt = $chk.lst.Count } else { $cnt = @($chk.lst).Count }
            if ($cnt -gt 0) {
                $msg = '[' + $sub + '] ' + $chk.label + ': ' + (ConvertTo-PyRepr $chk.lst)
                if ($chk.isFatal) { $fatal.Add($msg) } else { $warn.Add($msg) }
                Write-Out ('    ' + $chk.label + ' ' + $cnt + ': ' + (ConvertTo-PyRepr $chk.lst))
            }
        }
        if ((@($no_rec).Count -eq 0) -and (@($unreach).Count -eq 0) -and ($dangling.Count -eq 0) -and
            (@($no_slot).Count -eq 0) -and (@($no_tab).Count -eq 0)) {
            Write-Out '    ✅ 无致命问题'
        }
        $summary[$sub] = @{ chips = $chipList; no_rec = $no_rec; unreach = $unreach; no_page = $no_page }
    }

    if ($summary.Count -eq 2) {
        $keys = @($summary.Keys)
        Write-Out ''
        Write-Out ('=' * 78)
        Write-Out '双版本对等性'
        Write-Out ('=' * 78)
        foreach ($key in @('chips', 'no_rec', 'unreach', 'no_page')) {
            $sa = New-StrSet
            foreach ($x in $summary[$keys[0]][$key]) { [void]$sa.Add($x) }
            $sb = New-StrSet
            foreach ($x in $summary[$keys[1]][$key]) { [void]$sb.Add($x) }
            $eq = $sa.SetEquals($sb)
            $flag = '❌'
            if ($eq) { $flag = '✅' }
            $diff = [System.Collections.Generic.HashSet[string]]::new($sa, [System.StringComparer]::Ordinal)
            $diff.SymmetricExceptWith($sb)
            $diffList = Sort-Ordinal $diff
            $eqText = 'False'
            if ($eq) { $eqText = 'True' }
            Write-Out ('  ' + $flag + ' ' + $key + ': 一致=' + $eqText + ' 差异=' + (ConvertTo-PyRepr $diffList))
            if (-not $eq) {
                $fatal.Add('[parity] ' + $key + ' 双版本不一致: ' + (ConvertTo-PyRepr $diffList))
            }
        }
    }

    Write-Out ''
    Write-Out ('=' * 78)
    if ($fatal.Count -gt 0) {
        Write-Out 'RESULT: FAIL'
        foreach ($f in $fatal) { Write-Out ('  ' + $f) }
        return 1
    }
    Write-Out 'RESULT: PASS（致命项 0）'
    foreach ($w in $warn) { Write-Out ('  ' + $w) }
    return 0
}


try {
    $code = Main
    [Console]::Out.Flush()
    exit $code
}
catch {
    Write-Out ('ERROR: ' + $_.Exception.Message)
    [Console]::Out.Flush()
    exit 2
}
