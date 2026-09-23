# -*- coding: utf-8 -*-
<#
⚠️ 定位：**1.2.0 冻结期一次性验收工具 —— 不属常规守门清单**（2026-09-23 用户裁决「按第 2 路修复」，
见 `scripts/test/TESTING-SPEC.md` §9）。**不要**把它接进「每批必绿」的流水线或收尾清单。

  原因：它的对照件 `docs/1.2.0-content.json` 按设计是「**1.2.0 到底新增了什么**」的**冻结快照**
  （`start_commit = 68dbd59` = 1.2.0-rc1 bump 提交；采集脚本已于 2026-09-15 随 `temp/` 清理删除，
  且 `docs/` 被 .gitignore 排除 ⇒ 干净克隆下本脚本无法运行）。工程一旦推进到新版本，本脚本
  **必然报偏差**，例如 1.3.0 开发期实测 15 项：
    · 闭集 1 项 —— 1.3.0 的 13 个新物品不在库里（两枚飞星 / 七个新立牌 / 风水师两张符卡 / 蛟龙两张战斗牌）；
    · 计数 8 项 —— 汇总标签与 curios 槽位基线陈旧（chips 62≠60、signs 24≠17、chip 槽 61≠59、stand 槽 23≠16，× 双版本）；
    · 效果 6 项 —— 1.2.0 有、1.3.0 已移除的 `moses_ready`（「待命：破绽」效果，现改为消息键
      `msg.astral_dice.moses_ready`）仍被库里登记为 effect ⇒ 报「未注册 + 缺 lang 键」。
  ⇒ **这类偏差不是回归**，不得据此判定某次改动有问题。

  何时运行：仅在「**重建某个版本的内容库快照并校验它**」时手动运行。届时必须一并把本脚本的
  `$START_COMMIT` / `$BASE_TAG_COUNTS` / `$BASE_SLOT_COUNTS` / `$NEW_EFFECTS` / `$VERS`
  切到目标版本（另需重写采集脚本——原件已删）。

内容库一致性校验：docs/1.2.0-content.json ↔ 工程实际文件。

独立重算工程事实（不依赖任何生成脚本的中间产物），逐项比对内容库：
  1. 新增物品闭集（git 差集 vs 内容库 items 键集）
  2. 逐物品：ModItems 注册 / 汇总标签 / curios 槽位 / 配方 / 手册条目（双版本）
  3. 新增效果：ModEffects 注册 + effect.<ns>.<id> lang 键（双版本 × 中英）
  4. 计数基线：chips / signs / dices / materials / curios 三槽
  5. 装备槽守门：非空白基底的装备类物品必须全部在对应 curios 槽标签中

退出码：0 = 全部通过；1 = 存在偏差。
只读操作，不修改任何工程文件。

用法: pwsh -File scripts/verify/verify_content_library.ps1 [-Root .]

—— PowerShell 移植版:1:1 对应 scripts/verify/verify_content_library.py（原 .py 已在 92fbeaf 删除；取回：`git show 92fbeaf^:scripts/verify/verify_content_library.py`）。
#>
[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [string]$Root = '.'
)

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8
$ErrorActionPreference = 'Stop'

$NS = 'astral_dice'
$START_COMMIT = '68dbd59'
$VERS = @(
    @('neoforge-1.21.1', 'item', 'recipe'),
    @('forge-1.20.1', 'items', 'recipes')
)
$SUMMARY_TAGS = @('chips', 'signs', 'dices', 'materials')
$DICE_TIERS = @('dice_t0', 'dice_t1', 'dice_t2', 'dice_t3', 'dice_t4')
$BASE_SLOT_COUNTS = [ordered]@{ 'chip' = 59; 'dice' = 13; 'stand' = 16 }
$BASE_TAG_COUNTS = [ordered]@{ 'chips' = 60; 'signs' = 17; 'dices' = 13; 'materials' = 14 }
$BLANK_ITEMS = @('blank_chip', 'blank_sign')
$NEW_EFFECTS = @('charge', 'empower', 'weakness_reveal', 'moses_broken', 'moses_ready', 'pandaman_taunt')

$errors = New-Object System.Collections.Generic.List[string]
$warnings = New-Object System.Collections.Generic.List[string]

# ---------------------------------------------------------------------------
# 输出:统一经 [Console]::Out 写显式 LF（不得 CRLF;与 Mt.Phase 的 Write-MtLine 同契约)
#       Python print() 在 Windows 上把 "\n" 按 os.linesep 翻译成 CRLF 的行为）
# ---------------------------------------------------------------------------
function Write-Out([string]$text) {
    [Console]::Out.Write($text)
    [Console]::Out.Write("`n")
}

function New-Map {
    return , ([System.Collections.Specialized.OrderedDictionary]::new([System.StringComparer]::Ordinal))
}

function New-StrSet {
    return , ([System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal))
}

function ConvertTo-StrSet {
    param($Values)
    $s = New-StrSet
    if ($null -ne $Values) { foreach ($v in $Values) { [void]$s.Add([string]$v) } }
    return , $s
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

function Set-Equals {
    param($a, $b)
    return $a.SetEquals($b)
}

function Get-SetDiff {
    param($a, $b)
    $d = [System.Collections.Generic.HashSet[string]]::new($a, [System.StringComparer]::Ordinal)
    $d.ExceptWith($b)
    return , $d
}

# ---------------------------------------------------------------------------
function Read-PyJson {
    param([string]$Path)
    $t = [System.IO.File]::ReadAllText($Path, [System.Text.Encoding]::UTF8)
    return (ConvertFrom-Json -InputObject $t -AsHashtable)
}

function Read-PyText {
    param([string]$Path)
    $t = [System.IO.File]::ReadAllText($Path, [System.Text.Encoding]::UTF8)
    $t = $t -replace "`r`n", "`n"
    return ($t -replace "`r", "`n")
}

function Invoke-Git {
    param([string]$RepoRoot, [string[]]$GitArgs)
    $lines = & git -C $RepoRoot @GitArgs 2>$null
    if ($null -eq $lines) { return '' }
    return ($lines -join "`n")
}

function Add-Err {
    param([string]$msg)
    $errors.Add($msg)
}

function Add-Warn {
    param([string]$msg)
    $warnings.Add($msg)
}

function Get-NewIdsFromGit {
    <#
    1.2.0 新增注册 id = HEAD 的 id 集合 − 起始提交的 id 集合。

    注意：不能按 diff 的 `+` 行统计 —— Java 常量名重写（如 LIVING_BOOK_PAGE →
    LIVING_PAGE，注册 id 不变）同样会产生 `+` 行，会误报为新增。
    #>
    param([string]$root)
    $rel = 'neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/item/ModItems.java'
    $old = Invoke-Git $root @('show', "${START_COMMIT}^:${rel}")
    $new = Invoke-Git $root @('show', "HEAD:${rel}")
    if ((-not $old.Trim()) -or (-not $new.Trim())) {
        Add-Warn "git 读取失败（提交 ${START_COMMIT} 是否可达？）"
        return , (New-StrSet)
    }
    $pat = 'registerItem\("(\w+)"'
    $nset = New-StrSet
    foreach ($m in [regex]::Matches($new, $pat)) { [void]$nset.Add($m.Groups[1].Value) }
    $oset = New-StrSet
    foreach ($m in [regex]::Matches($old, $pat)) { [void]$oset.Add($m.Groups[1].Value) }
    $nset.ExceptWith($oset)
    return , $nset
}

function Get-ModItems {
    param([string]$root, [string]$sub)
    $p = [System.IO.Path]::Combine($root, $sub, 'src/main/java/com/merlinkitsune/astral_dice/item/ModItems.java')
    $s = New-StrSet
    foreach ($m in [regex]::Matches((Read-PyText $p), 'registerItem\("(\w+)"')) { [void]$s.Add($m.Groups[1].Value) }
    return , $s
}

function Get-TagValues {
    param([string]$root, [string]$sub, [string]$tagdir, [string]$name)
    $p = [System.IO.Path]::Combine($root, $sub, 'src/main/resources/data', $NS, 'tags', $tagdir, ($name + '.json'))
    if (-not [System.IO.File]::Exists($p)) { return $null }
    $out = @()
    foreach ($v in @((Read-PyJson $p)['values'])) {
        $parts = ([string]$v) -split ':'
        $out += $parts[$parts.Count - 1]
    }
    return , $out
}

function Get-SlotValues {
    param([string]$root, [string]$sub, [string]$tagdir, [string]$name)
    $p = [System.IO.Path]::Combine($root, $sub, 'src/main/resources/data/curios/tags', $tagdir, ($name + '.json'))
    if (-not [System.IO.File]::Exists($p)) { return $null }
    $out = @()
    foreach ($v in @((Read-PyJson $p)['values'])) {
        $parts = ([string]$v) -split ':'
        $out += $parts[$parts.Count - 1]
    }
    return , $out
}

function Get-RecipeResults {
    param([string]$root, [string]$sub, [string]$recipedir)
    $out = New-StrSet
    foreach ($r in @('src/generated/resources', 'src/main/resources')) {
        $base = [System.IO.Path]::Combine($root, $sub, ($r -replace '/', [System.IO.Path]::DirectorySeparatorChar), 'data', $NS, $recipedir)
        if (-not [System.IO.Directory]::Exists($base)) { continue }
        foreach ($f in [System.IO.Directory]::GetFiles($base)) {
            if (-not $f.EndsWith('.json')) { continue }
            $d = Read-PyJson $f
            $res = @{}
            if ($d.Contains('result')) { $res = $d['result'] }
            $rid = ''
            if ($res -is [System.Collections.IDictionary]) {
                if ($res.Contains('id') -and $res['id']) { $rid = [string]$res['id'] }
                elseif ($res.Contains('item') -and $res['item']) { $rid = [string]$res['item'] }
            }
            elseif ($null -eq $res) { $rid = 'None' }
            else { $rid = [string]$res }
            if ($rid) {
                $parts = $rid -split ':'
                [void]$out.Add($parts[$parts.Count - 1])
            }
        }
    }
    return , $out
}

function Get-ManualIds {
    param([string]$root, [string]$sub)
    $base = [System.IO.Path]::Combine($root, $sub, 'src/main/resources/assets', $NS, 'patchouli_books/astral_guide/en_us/entries')
    $out = New-StrSet
    foreach ($cat in [System.IO.Directory]::GetDirectories($base)) {
        foreach ($f in [System.IO.Directory]::GetFiles($cat)) {
            if ($f.EndsWith('.json')) { [void]$out.Add([System.IO.Path]::GetFileNameWithoutExtension($f)) }
        }
    }
    return , $out
}

function Get-ModEffects {
    param([string]$root, [string]$sub)
    $p = [System.IO.Path]::Combine($root, $sub, 'src/main/java/com/merlinkitsune/astral_dice/effect/ModEffects.java')
    if (-not [System.IO.File]::Exists($p)) { return $null }
    $s = New-StrSet
    foreach ($m in [regex]::Matches((Read-PyText $p), 'EFFECTS\.register\("(\w+)"')) { [void]$s.Add($m.Groups[1].Value) }
    return , $s
}


# ---------------------------------------------------------------------------
# main
# ---------------------------------------------------------------------------
$root = $Root
Write-Out '[NOTE] 本脚本是 1.2.0 冻结期一次性验收工具，**不属常规守门清单**；'
Write-Out '       它对照的是 1.2.0 的冻结快照，工程推进后出现偏差属预期、不代表回归（见脚本头部与 TESTING-SPEC.md §9）。'
Write-Out ''
$out_json = [System.IO.Path]::Combine($root, 'docs/1.2.0-content.json')
if (-not [System.IO.File]::Exists($out_json)) {
    Write-Out '!! 缺少 docs/1.2.0-content.json（内容库快照，被 .gitignore 排除，其生成脚本已于 2026-09-15 随 temp/ 清理移除）—— 请先从备份恢复该文件，详见 scripts/test/TESTING-SPEC.md §11'
    [Console]::Out.Flush()
    exit 1
}
$lib = Read-PyJson $out_json
$lib_items = ConvertTo-StrSet @($lib['items'].Keys)

# ---- 1. 新增物品闭集 ----
$git_new = Get-NewIdsFromGit $root
if ($git_new.Count -gt 0) {
    if (-not (Set-Equals $git_new $lib_items)) {
        Add-Err ('[闭集] git 新增 id 与内容库 items 不一致：git-only=' + (ConvertTo-PyRepr (Sort-Ordinal (Get-SetDiff $git_new $lib_items))) + ' lib-only=' + (ConvertTo-PyRepr (Sort-Ordinal (Get-SetDiff $lib_items $git_new))))
    }
    else {
        Write-Out ('[OK] 闭集：' + $lib_items.Count + ' 个新增物品与 git 差集一致')
    }
}

# ---- 2. 逐物品（双版本） ----
foreach ($ver in $VERS) {
    $sub = $ver[0]
    $tagdir = $ver[1]
    $recipedir = $ver[2]
    $reg = Get-ModItems $root $sub
    $recipes = Get-RecipeResults $root $sub $recipedir
    $manual = Get-ManualIds $root $sub
    $summary_maps = New-Map
    foreach ($t in ($SUMMARY_TAGS + $DICE_TIERS)) {
        $v = Get-TagValues $root $sub $tagdir $t
        if ($null -eq $v) { $v = @() }
        $summary_maps[$t] = $v
    }
    $slot_maps = New-Map
    foreach ($s in $BASE_SLOT_COUNTS.Keys) {
        $v = Get-SlotValues $root $sub $tagdir $s
        if ($null -eq $v) { $v = @() }
        $slot_maps[$s] = $v
    }
    $missing_reg = Sort-Ordinal (Get-SetDiff $lib_items $reg)
    if (@($missing_reg).Count -gt 0) {
        Add-Err ('[' + $sub + '] 内容库物品未在 ModItems 注册：' + (ConvertTo-PyRepr $missing_reg))
    }
    foreach ($kv in $lib['items'].GetEnumerator()) {
        $iid = $kv.Key
        $e = $kv.Value
        if (-not $reg.Contains($iid)) { continue }
        # 汇总标签
        $want = ConvertTo-StrSet @($e['tags'])
        $got = New-StrSet
        foreach ($t in $summary_maps.Keys) { if (@($summary_maps[$t]) -contains $iid) { [void]$got.Add($t) } }
        if (-not (Set-Equals $want $got)) {
            Add-Err ('[' + $sub + '] ' + $iid + ' 汇总标签不一致：内容库=' + (ConvertTo-PyRepr (Sort-Ordinal $want)) + ' 实际=' + (ConvertTo-PyRepr (Sort-Ordinal $got)))
        }
        # 槽位
        $wslot = ConvertTo-StrSet @($e['slots'])
        $gslot = New-StrSet
        foreach ($s in $slot_maps.Keys) { if (@($slot_maps[$s]) -contains $iid) { [void]$gslot.Add($s) } }
        if (-not (Set-Equals $wslot $gslot)) {
            Add-Err ('[' + $sub + '] ' + $iid + ' 槽位不一致：内容库=' + (ConvertTo-PyRepr (Sort-Ordinal $wslot)) + ' 实际=' + (ConvertTo-PyRepr (Sort-Ordinal $gslot)))
        }
        # 配方 / 手册
        if (-not $recipes.Contains($iid)) {
            Add-Err ('[' + $sub + '] ' + $iid + ' 无配方')
        }
        if (-not $manual.Contains($iid)) {
            Add-Err ('[' + $sub + '] ' + $iid + ' 无手册条目')
        }
    }
}
Write-Out ('[OK] 逐物品检查完成：' + $lib_items.Count + ' × ' + $VERS.Count + ' 版本（注册/标签/槽位/配方/手册）')

# ---- 3. 计数基线 ----
foreach ($ver in $VERS) {
    $sub = $ver[0]
    $tagdir = $ver[1]
    foreach ($kv in $BASE_TAG_COUNTS.GetEnumerator()) {
        $t = $kv.Key
        $baseCount = $kv.Value
        $v = Get-TagValues $root $sub $tagdir $t
        if ($null -eq $v) {
            Add-Err ('[' + $sub + '] 缺少汇总标签 ' + $t)
        }
        elseif (@($v).Count -ne $baseCount) {
            Add-Err ('[' + $sub + '] 汇总标签 ' + $t + ' 计数 ' + @($v).Count + ' ≠ 基线 ' + $baseCount)
        }
    }
    foreach ($kv in $BASE_SLOT_COUNTS.GetEnumerator()) {
        $s = $kv.Key
        $baseCount = $kv.Value
        $v = Get-SlotValues $root $sub $tagdir $s
        if ($null -eq $v) {
            Add-Err ('[' + $sub + '] 缺少 curios 槽标签 ' + $s)
        }
        elseif (@($v).Count -ne $baseCount) {
            Add-Err ('[' + $sub + '] curios 槽 ' + $s + ' 计数 ' + @($v).Count + ' ≠ 基线 ' + $baseCount)
        }
    }
}
Write-Out ('[OK] 计数基线：汇总标签 ' + (ConvertTo-PyRepr $BASE_TAG_COUNTS) + ' / curios 槽 ' + (ConvertTo-PyRepr $BASE_SLOT_COUNTS))

# ---- 4. 装备槽守门（非空白基底装备物品必须入槽） ----
foreach ($ver in $VERS) {
    $sub = $ver[0]
    $tagdir = $ver[1]
    foreach ($pair in @(@('chip', 'chips'), @('stand', 'signs'), @('dice', 'dices'))) {
        $slot = $pair[0]
        $summary = $pair[1]
        $sg = ConvertTo-StrSet (Get-TagValues $root $sub $tagdir $summary)
        $sl = ConvertTo-StrSet (Get-SlotValues $root $sub $tagdir $slot)
        # (sg - BLANK_ITEMS) - sl
        $t1 = $sg
        foreach ($b in $BLANK_ITEMS) { [void]$t1.Remove($b) }
        $t1.ExceptWith($sl)
        $gap = Sort-Ordinal $t1
        if (@($gap).Count -gt 0) {
            Add-Err ('[' + $sub + '] ' + $summary + ' 中 ' + (ConvertTo-PyRepr $gap) + ' 未进 curios:' + $slot + ' 槽标签（装备槽守门）')
        }
    }
}
Write-Out '[OK] 装备槽守门（空白基底材料已排除）'

# ---- 5. 新增效果 ----
foreach ($ver in $VERS) {
    $sub = $ver[0]
    $eff = Get-ModEffects $root $sub
    if ($null -eq $eff) {
        Add-Warn ('[' + $sub + '] 未找到 ModEffects.java')
        continue
    }
    $miss = @()
    foreach ($e in $NEW_EFFECTS) { if (-not $eff.Contains($e)) { $miss += $e } }
    if ($miss.Count -gt 0) {
        Add-Err ('[' + $sub + '] 新增效果未注册：' + (ConvertTo-PyRepr $miss))
    }
    foreach ($lg in @('zh_cn', 'en_us')) {
        $z = Read-PyJson ([System.IO.Path]::Combine($root, $sub, 'src/main/resources/assets', $NS, 'lang', ($lg + '.json')))
        $nolang = @()
        foreach ($e in $NEW_EFFECTS) { if (-not $z.ContainsKey("effect.${NS}.${e}")) { $nolang += $e } }
        if ($nolang.Count -gt 0) {
            Add-Err ('[' + $sub + '/' + $lg + '] 效果缺 lang 键 effect.' + $NS + '.*：' + (ConvertTo-PyRepr $nolang))
        }
    }
}
Write-Out ('[OK] 新增效果 ' + $NEW_EFFECTS.Count + ' 个：ModEffects 注册 + 双版本×中英 lang 键')

# ---- 6. 双版本对等（标签/槽位/手册） ----
foreach ($t in ($SUMMARY_TAGS + $DICE_TIERS)) {
    $a = ConvertTo-StrSet (Get-TagValues $root $VERS[0][0] $VERS[0][1] $t)
    $b = ConvertTo-StrSet (Get-TagValues $root $VERS[1][0] $VERS[1][1] $t)
    if (-not (Set-Equals $a $b)) {
        Add-Err ('[对等] 汇总标签 ' + $t + ' 双版本不一致：neo-only=' + (ConvertTo-PyRepr (Sort-Ordinal (Get-SetDiff $a $b))) + ' forge-only=' + (ConvertTo-PyRepr (Sort-Ordinal (Get-SetDiff $b $a))))
    }
}
foreach ($s in $BASE_SLOT_COUNTS.Keys) {
    $a = ConvertTo-StrSet (Get-SlotValues $root $VERS[0][0] $VERS[0][1] $s)
    $b = ConvertTo-StrSet (Get-SlotValues $root $VERS[1][0] $VERS[1][1] $s)
    if (-not (Set-Equals $a $b)) {
        Add-Err ('[对等] curios 槽 ' + $s + ' 双版本不一致：neo-only=' + (ConvertTo-PyRepr (Sort-Ordinal (Get-SetDiff $a $b))) + ' forge-only=' + (ConvertTo-PyRepr (Sort-Ordinal (Get-SetDiff $b $a))))
    }
}
$ma = Get-ManualIds $root $VERS[0][0]
$mb = Get-ManualIds $root $VERS[1][0]
if (-not (Set-Equals $ma $mb)) {
    Add-Err ('[对等] 手册条目双版本不一致：neo-only=' + (ConvertTo-PyRepr (Sort-Ordinal (Get-SetDiff $ma $mb))) + ' forge-only=' + (ConvertTo-PyRepr (Sort-Ordinal (Get-SetDiff $mb $ma))))
}
Write-Out '[OK] 双版本对等：汇总标签 / curios 槽 / 手册条目'

# ---- 汇总输出 ----
Write-Out ''
foreach ($w in $warnings) { Write-Out ('WARN ' + $w) }
if ($errors.Count -gt 0) {
    Write-Out ('FAIL —— ' + $errors.Count + ' 项偏差：')
    foreach ($e in $errors) { Write-Out ('  - ' + $e) }
    Write-Out ''
    Write-Out '（提示：本脚本对照的是 **1.2.0 冻结快照**，工程已推进时出现偏差属预期 —— 见脚本头部与 TESTING-SPEC.md §9。'
    Write-Out '  要判定「是否真的漏登记」，须先确认内容库快照是否已按当前版本重建；本脚本不参与常规门禁。）'
    [Console]::Out.Flush()
    exit 1
}
Write-Out ('ALL OK —— 内容库与工程实际一致（' + $lib_items.Count + ' 物品 / ' + $NEW_EFFECTS.Count + ' 效果 / 双版本）')
[Console]::Out.Flush()
exit 0
