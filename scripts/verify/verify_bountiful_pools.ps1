# -*- coding: utf-8 -*-
<#
Bountiful 赏金联动一致性校验（只读守门）。

独立重算「按规则应进入赏金池的物品集合」，与双版本四份池文件逐项比对：
  1. 双版本 ModItems 注册物品 id 与品质完全一致；
  2. astral_objs = 非「不进池档」骰子 + 货币(star_coin / star_coin_bag / star_plate / golden_star_plate)；
  3. astral_rews = astral_objs ∪ 卡牌(全部，⚠️ 专属牌除外，见 §4) ∪ 非「不进池档」筹码 ∪ 非「不进池档」立牌
     （**不进池 = 巅峰（ASTRAL_DICE_PINNACLE）+ 奇特（ASTRAL_DICE_BIZARRE）**：这两档的骰子/筹码/立牌/**卡牌**不进任何池,
      二者都**没有**数据层 rarity 对应值;⚠️ 2026-09-25 用户裁决「加入传奇物品，排除巅峰和奇特」后 **传奇档已进池**
      (其数据层 rarity = `LEGENDARY`);⚠️ 2026-09-26 用户裁决「巅峰/奇特档的卡牌也排除」——此前卡牌不受档位限制，
      现在卡牌与骰子/筹码/立牌一样受 $EXCLUDED_TIERS 约束(如「全力攻击」改巅峰后即自动从 rews 移出)）；
     「卡牌」判据 = **与生产线同一判据**：id 前缀(attack_card_/defense_card_/effect_card_)
     **∪ 本模组卡牌标签**(`data/<ns>/tags/{item|items}/{combat_cards,effect_cards}.json`，即 `ModItems.isCardItem`)。
     ⚠️ **2026-09-26 修正（消除失明区）**：此前只用 id 前缀 ⇒ 不以前缀命名的卡牌
     （`fu_card` / `huo_card`，实为 `effect_cards` 标签成员）被归入 materials
     ⇒ **闸门对「这两张卡是否入池」完全失明**（实测：加入池前后分类都是 cards 25 / materials 8，条数不变）。
     该盲区正是「守门看不见规则违反」的实例：分类判据与生产线不一致时，闸门给出的是**假绿**。
  4. 集合相等（0 缺失 / 0 多余），且数据层 rarity 与物品品质映射一致
     （RARE→RARE、EPIC→EPIC、LEGENDARY→LEGENDARY；⚠️ 巅峰/奇特档**无**数据层对应值 ⇒ 这两档物品入池即报错）；
  5. 双版本四份文件逐字节一致（md5）；
  6. 文件格式：UTF-8 / CRLF / Tab 缩进 / 末尾换行；
  7. 价值平衡式：objs 顶值(1 条, amount.max×unitWorth) ≥ rews 顶值(2 条之和) × 0.9
     （Bountiful 加载告警阈值，违反会刷不出匹配赏金）。

退出码：0 = 全部通过；1 = 存在致命偏差。只读，不修改任何工程文件。

用法: pwsh -File scripts/verify/verify_bountiful_pools.ps1 [-Root .]

—— PowerShell 移植版:1:1 对应 scripts/verify/verify_bountiful_pools.py（原 .py 已在 92fbeaf 删除；取回：`git show 92fbeaf^:scripts/verify/verify_bountiful_pools.py`）。
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
$VERSIONS = @('neoforge-1.21.1', 'forge-1.20.1')
$MODITEMS_REL = 'src/main/java/com/merlinkitsune/astral_dice/item/ModItems.java'
$POOL_REL = 'src/main/resources/data/bountiful/bounty_pools/bountiful/'
$DECREE_REL = 'src/main/resources/data/bountiful/bounty_decrees/bountiful/astral.json'

$REG = 'registerItem\("([a-z0-9_]+)"'
# ⚠️ 2026-09-25 稀有度改造:调用语法 = .rarity(AstralRarities.z()),z ∈ {rare,epic,legendary,pinnacle};
# 捕获后统一大写(见 Get-ParsedItems),故 $EXCLUDED_TIERS / $RARITY_MAP 的键为大写档名。
$RAR = 'rarity\(AstralRarities\.([a-z]+)\(\)\)'

$DICE = @('dice', 'golden_dice', 'glass_dice', 'netherrack_dice', 'diamond_dice',
    'emerald_dice', 'obsidian_dice', 'weird_dice', 'amethyst_dice',
    'netherite_dice', 'crimson_dice', 'ender_dice', 'nether_star_dice')
$MONEY = @('star_coin', 'star_coin_bag', 'star_plate', 'golden_star_plate')
# ⚠️ 2026-09-25 用户裁决「加入传奇物品，排除巅峰和奇特」:「不进池」= **巅峰 + 奇特**两档
#   (此前是「传奇 + 巅峰 + 奇特」三档全排除;传奇已放开 ⇒ 其数据层 rarity = LEGENDARY 照常映射)。
#   巅峰(PINNACLE) 与 奇特(BIZARRE) 都**没有**数据层 rarity 对应值(见 $RARITY_MAP) ⇒ 不得进入任何池。
#   判据集中在 $EXCLUDED_TIERS,勿在别处另写档位比较。
$EXCLUDED_TIERS = @('PINNACLE', 'BIZARRE')
# ⚠️ 刻意**不含** PINNACLE/BIZARRE:这两档没有数据层对应值 ⇒ 若有其物品入池,$want 取到 $null,
#    与池内任何 rarity 都不等 ⇒ 当场报错(fail-loud,与「不进池」的口径一致)。
$RARITY_MAP = [ordered]@{ 'COMMON' = 'COMMON'; 'RARE' = 'RARE'; 'EPIC' = 'EPIC'; 'LEGENDARY' = 'LEGENDARY' }

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

function Sort-Ordinal {
    param($Values)
    $arr = @($Values)
    [Array]::Sort($arr, [System.StringComparer]::Ordinal)
    return , $arr
}

function Get-SetDiff {
    param($a, $b)
    $d = [System.Collections.Generic.HashSet[string]]::new($a, [System.StringComparer]::Ordinal)
    $d.ExceptWith($b)
    return , $d
}

function Add-Err {
    param([string]$msg)
    $errors.Add($msg)
}

function Add-Warn {
    param([string]$msg)
    $warnings.Add($msg)
}

# Python 的 io.open(path, "r", encoding="utf-8", newline="").read() — 不做换行翻译
function Read-RawText {
    param([string]$Path)
    return [System.IO.File]::ReadAllText($Path, [System.Text.Encoding]::UTF8)
}

function Get-Md5Hex {
    param([string]$Path)
    $bytes = [System.IO.File]::ReadAllBytes($Path)
    $hash = [System.Security.Cryptography.MD5]::HashData($bytes)
    return [System.Convert]::ToHexString($hash).ToLowerInvariant()
}

# ---------------------------------------------------------------------------
function Get-ParsedItems {
    param([string]$path)
    $text = Read-RawText $path
    $hits = [regex]::Matches($text, $REG)
    $out = New-Map
    for ($i = 0; $i -lt $hits.Count; $i++) {
        $start = $hits[$i].Index + $hits[$i].Length
        $end = $text.Length
        if ($i + 1 -lt $hits.Count) { $end = $hits[$i + 1].Index }
        $seg = $text.Substring($start, $end - $start)
        $r = [regex]::Match($seg, $RAR)
        # 注意:PowerShell 变量名大小写不敏感,本地名不得写成 $rar(会与 $RAR 冲突)
        $rarityText = 'COMMON'
        if ($r.Success) { $rarityText = $r.Groups[1].Value.ToUpperInvariant() }
        $out[$hits[$i].Groups[1].Value] = $rarityText
    }
    return , $out
}


# 卡牌标签目录名按线不同(1.21.1: tags/item;1.20.1: tags/items) —— 与 compat 差异表一致
$TAG_DIR = [ordered]@{ 'neoforge-1.21.1' = 'item'; 'forge-1.20.1' = 'items' }
$CARD_TAGS = @('combat_cards.json', 'effect_cards.json')
# 专属效果牌标签(与 CARD_TAGS 同目录/同线差异)。用户 2026-09-26 裁决:
# **专属效果牌严禁经立牌以外的任何途径获得 ⇒ 不得进入任何赏金池**。
$EXCLUSIVE_TAG = 'is_exclusive.json'
# 「专属牌却在池内」的**既有**条目白名单:**已按用户 2026-09-26 裁决清空** ——
# 用户裁定 `effect_card_living_page` / `effect_card_fate_guidance` 与符卡-福/祸同属专属效果牌,
# **一并清出**(见规格 §14.8)。故此处不再有任何例外:任何专属 id 出现在池内一律**致命偏差**。
# (该变量保留为空数组,便于将来确需登记例外时显式写入并在评审中可见。)
$LEGACY_POOL_EXCEPTIONS = @()

# 读取本模组「卡牌标签」里的物品 id(裸 id,去掉命名空间前缀)。
# 判据与生产线完全一致:ModItems.isCardItem = COMBAT_CARDS_TAG ∪ EFFECT_CARDS_TAG。
# 为何必须读标签:id 前缀规则(attack_card_/defense_card_/effect_card_)看不见 fu_card/huo_card
# 这类不以前缀命名的卡牌,会把它们当成材料 ⇒ 闸门对「它们是否入池」失明(2026-09-26 实测)。
# 标签文件缺失时只 WARN 并退回前缀判据(不得静默把卡牌当材料)。
function Get-TaggedCards {
    param([string]$rootPrefix, [string]$ver, [string[]]$files)
    if (-not $files) { $files = $CARD_TAGS }
    $set = New-StrSet
    $sub = $TAG_DIR[$ver]
    if (-not $sub) { $sub = 'item' }
    foreach ($f in $files) {
        $p = $rootPrefix + $ver + '/src/main/resources/data/' + $NS + '/tags/' + $sub + '/' + $f
        if (-not (Test-Path -LiteralPath $p)) {
            Add-Warn ('标签缺失(该线相关集合退回 id 前缀判据): ' + $ver + '/tags/' + $sub + '/' + $f)
            continue
        }
        $d = ConvertFrom-Json -InputObject (Read-RawText $p) -AsHashtable
        if (-not $d.Contains('values')) { continue }
        foreach ($v in @($d['values'])) {
            if ($v -isnot [string]) { continue }
            $id = [string]$v
            if ($id.Contains(':')) { $id = $id.Substring($id.IndexOf(':') + 1) }
            [void]$set.Add($id)
        }
    }
    return , $set
}


function Get-Classified {
    param($items, $tagCards)
    $c = New-Map
    foreach ($k in @('dice', 'money', 'cards', 'signs', 'chips', 'materials')) { $c[$k] = New-StrSet }
    foreach ($k in $items.Keys) {
        # 卡牌判据 = id 前缀 ∪ 卡牌标签成员(单行计算,避免跨行 elseif 的解析歧义)
        $isCard = $k.StartsWith('attack_card_') -or $k.StartsWith('defense_card_') -or $k.StartsWith('effect_card_')
        if ((-not $isCard) -and ($null -ne $tagCards)) { $isCard = $tagCards.Contains($k) }
        if ($DICE -contains $k) { [void]$c['dice'].Add($k) }
        elseif ($MONEY -contains $k) { [void]$c['money'].Add($k) }
        elseif ($isCard) { [void]$c['cards'].Add($k) }
        elseif ($k.EndsWith('_sign') -and $k -cne 'blank_sign') { [void]$c['signs'].Add($k) }
        elseif ($k.EndsWith('_chip') -and $k -cne 'blank_chip') { [void]$c['chips'].Add($k) }
        else { [void]$c['materials'].Add($k) }
    }
    return , $c
}


function Get-Expected {
    param($items, $c, $exclusive)
    $objs = New-StrSet
    foreach ($k in $c['dice']) { if ($EXCLUDED_TIERS -cnotcontains $items[$k]) { [void]$objs.Add($k) } }
    foreach ($k in $c['money']) { [void]$objs.Add($k) }
    $rews = [System.Collections.Generic.HashSet[string]]::new($objs, [System.StringComparer]::Ordinal)
    foreach ($k in $c['cards']) { if ($EXCLUDED_TIERS -cnotcontains $items[$k]) { [void]$rews.Add($k) } }
    foreach ($k in $c['signs']) { if ($EXCLUDED_TIERS -cnotcontains $items[$k]) { [void]$rews.Add($k) } }
    foreach ($k in $c['chips']) { if ($EXCLUDED_TIERS -cnotcontains $items[$k]) { [void]$rews.Add($k) } }
    # 专属效果牌从**所有**池的期望集合中剔除(用户 2026-09-26 裁决:严禁经立牌以外的任何途径获得)。
    # 剔除后,若这些 id 仍出现在池文件里,下面的 extra 差集就会把它们逐个报出来 —— 这正是我们要的强制力。
    if ($null -ne $exclusive) {
        foreach ($k in $exclusive) { [void]$objs.Remove($k); [void]$rews.Remove($k) }
    }
    return , @($objs, $rews)
}


function Get-Pool {
    <#
    返回 (content_id → 条目 dict) 与原始文本。
    #>
    param([string]$path)
    $raw = Read-RawText $path
    $d = ConvertFrom-Json -InputObject $raw -AsHashtable
    $content = New-Map
    foreach ($v in $d['content'].Values) {
        $parts = ([string]$v['content']) -split ':', 2
        $content[$parts[1]] = $v
    }
    return , @($content, $raw)
}


# ---------------------------------------------------------------------------
# main
# ---------------------------------------------------------------------------
$root = $Root
$root = $root.TrimEnd([char[]]@('/', '\'))
$pre = ''
if ($root -cnotin @('.', '')) { $pre = $root + '/' }

# 1) 双版本物品清单一致
$items_by_ver = New-Map
foreach ($ver in $VERSIONS) {
    $p = $pre + $ver + '/' + $MODITEMS_REL
    $items_by_ver[$ver] = Get-ParsedItems $p
}
$a = $items_by_ver[$VERSIONS[0]]
$b = $items_by_ver[$VERSIONS[1]]
$sameItems = $true
if ($a.Count -ne $b.Count) { $sameItems = $false }
else {
    foreach ($k in $a.Keys) {
        if (-not $b.Contains($k)) { $sameItems = $false; break }
        if ($b[$k] -cne $a[$k]) { $sameItems = $false; break }
    }
}
if (-not $sameItems) {
    $only_a = Sort-Ordinal (Get-SetDiff (ConvertTo-StrSet @($a.Keys)) (ConvertTo-StrSet @($b.Keys)))
    $only_b = Sort-Ordinal (Get-SetDiff (ConvertTo-StrSet @($b.Keys)) (ConvertTo-StrSet @($a.Keys)))
    $diffRar = @()
    foreach ($k in $a.Keys) {
        if ($b.Contains($k) -and ($a[$k] -cne $b[$k])) { $diffRar += $k }
    }
    $diffRar = Sort-Ordinal $diffRar
    Add-Err ('双版本 ModItems 不一致：仅 ' + $VERSIONS[0] + '=' + (ConvertTo-PyRepr $only_a) + '；品质差异=' + (ConvertTo-PyRepr $diffRar))
    Add-Err ('双版本 ModItems 不一致：仅 ' + $VERSIONS[1] + '=' + (ConvertTo-PyRepr $only_b))
}
$items = $a
$tagCards = Get-TaggedCards $pre $VERSIONS[0]
$tagCardsOther = Get-TaggedCards $pre $VERSIONS[1]
$tagOnlyA = Sort-Ordinal (Get-SetDiff $tagCards $tagCardsOther)
$tagOnlyB = Sort-Ordinal (Get-SetDiff $tagCardsOther $tagCards)
if ((@($tagOnlyA).Count -gt 0) -or (@($tagOnlyB).Count -gt 0)) {
    Add-Warn ('双版本卡牌标签不一致：仅 ' + $VERSIONS[0] + '=' + (ConvertTo-PyRepr $tagOnlyA) + '；仅 ' + $VERSIONS[1] + '=' + (ConvertTo-PyRepr $tagOnlyB))
}
$c = Get-Classified $items $tagCards
$exclusive = Get-TaggedCards $pre $VERSIONS[0] @($EXCLUSIVE_TAG)
$exclusiveOther = Get-TaggedCards $pre $VERSIONS[1] @($EXCLUSIVE_TAG)
$exclOnlyA = Sort-Ordinal (Get-SetDiff $exclusive $exclusiveOther)
$exclOnlyB = Sort-Ordinal (Get-SetDiff $exclusiveOther $exclusive)
if ((@($exclOnlyA).Count -gt 0) -or (@($exclOnlyB).Count -gt 0)) {
    Add-Warn ('双版本专属牌标签不一致：仅 ' + $VERSIONS[0] + '=' + (ConvertTo-PyRepr $exclOnlyA) + '；仅 ' + $VERSIONS[1] + '=' + (ConvertTo-PyRepr $exclOnlyB))
}
Write-Out ('专属效果牌(禁止进入任何赏金池): ' + $exclusive.Count + ' 项')
$ex = Get-Expected $items $c $exclusive
$eo = $ex[0]
$er = $ex[1]
$counts = New-Map
foreach ($k in $c.Keys) { $counts[$k] = $c[$k].Count }
Write-Out ('物品分类: ' + (ConvertTo-PyRepr $counts) + ' 总 ' + $items.Count)
Write-Out ('规则应含: objs ' + $eo.Count + ' / rews ' + $er.Count)
$excl = @()
foreach ($k in $c['chips']) { if ($EXCLUDED_TIERS -ccontains $items[$k]) { $excl += $k } }
foreach ($k in $c['signs']) { if ($EXCLUDED_TIERS -ccontains $items[$k]) { $excl += $k } }
foreach ($k in $c['dice']) { if ($EXCLUDED_TIERS -ccontains $items[$k]) { $excl += $k } }
foreach ($k in $c['cards']) { if ($EXCLUDED_TIERS -ccontains $items[$k]) { $excl += $k } }
$excl = Sort-Ordinal $excl
Write-Out ('按规则排除(巅峰/奇特档 筹码/立牌/骰子/卡牌): ' + $excl.Count + ' 项')

# 2/3/4) 逐版本逐池比对
foreach ($ver in $VERSIONS) {
    foreach ($pair in @(@('astral_objs.json', $eo), @('astral_rews.json', $er))) {
        $pool = $pair[0]
        $exp = $pair[1]
        $path = $pre + $ver + '/' + $POOL_REL + $pool
        $pr = Get-Pool $path
        $content = $pr[0]
        $actual = ConvertTo-StrSet @($content.Keys)
        $miss = Sort-Ordinal (Get-SetDiff $exp $actual)
        $extraAll = Sort-Ordinal (Get-SetDiff $actual $exp)
        # 把「既有专属牌仍在池内」与「本批/新引入的违规」拆开:前者登记为具名 WARN(待用户裁决),
        # 后者照旧致命 —— 两者都不得静默。
        $extraLegacy = @()
        $extra = @()
        foreach ($k in $extraAll) {
            if ($LEGACY_POOL_EXCEPTIONS -contains $k) { $extraLegacy += $k } else { $extra += $k }
        }
        $tag = $ver + '/' + $pool
        if (@($miss).Count -gt 0) {
            Add-Err ($tag + ' 缺失条目(' + @($miss).Count + '): ' + (ConvertTo-PyRepr $miss))
        }
        if (@($extra).Count -gt 0) {
            Add-Err ($tag + ' 多余/应排除条目(' + @($extra).Count + '): ' + (ConvertTo-PyRepr $extra))
        }
        if (@($extraLegacy).Count -gt 0) {
            Add-Warn ($tag + ' 专属效果牌仍在池内(既有状态,待用户裁决)(' + @($extraLegacy).Count + '): ' + (ConvertTo-PyRepr $extraLegacy))
        }
        if ((@($miss).Count -eq 0) -and (@($extra).Count -eq 0)) {
            Write-Out ('OK  ' + $tag.PadRight(40) + ' 条目 ' + $actual.Count + ' 与规则一致')
        }

        # 数据层 rarity 映射（缺省字段 = COMMON，Bountiful 默认）
        $badR = @()
        foreach ($kv in $content.GetEnumerator()) {
            $k = $kv.Key
            $v = $kv.Value
            if (-not $items.Contains($k)) { continue }
            $vr = 'COMMON'
            if ($v.Contains('rarity')) { $vr = $v['rarity'] }
            $want = $null
            if ($RARITY_MAP.Contains($items[$k])) { $want = $RARITY_MAP[$items[$k]] }
            if ($vr -cne $want) {
                $rawRar = $null
                if ($v.Contains('rarity')) { $rawRar = $v['rarity'] }
                $badR += , (New-PyTuple @($k, $rawRar, $want))
            }
        }
        if ($badR.Count -gt 0) {
            $lim = [Math]::Min(8, $badR.Count)
            $slice = @()
            for ($i = 0; $i -lt $lim; $i++) { $slice += , $badR[$i] }
            Add-Err ($tag + ' rarity 映射错误: ' + (ConvertTo-PyRepr $slice))
        }

        # 格式
        $rawb = [System.IO.File]::ReadAllBytes($path)
        $s = [System.Text.Encoding]::Latin1.GetString($rawb)
        $crlfCount = ([regex]::Matches($s, "`r`n")).Count
        $lfCount = ([regex]::Matches($s, "`n")).Count
        if ($crlfCount -ne $lfCount) {
            Add-Err ($tag + ' 换行符非纯 CRLF')
        }
        if (-not $s.EndsWith("`r`n")) {
            Add-Err ($tag + ' 末尾缺换行')
        }
        if ($s.Contains("`r`n    ") -or $s.Contains("`r`n  ")) {
            Add-Err ($tag + ' 存在空格缩进(应为 Tab)')
        }
    }
}

# 5) 双版本逐字节一致
foreach ($pool in @('astral_objs.json', 'astral_rews.json', 'astral.json')) {
    $paths = @()
    foreach ($v in $VERSIONS) {
        if ($pool -ceq 'astral.json') { $paths += ($pre + $v + '/' + $DECREE_REL) }
        else { $paths += ($pre + $v + '/' + $POOL_REL + $pool) }
    }
    $digests = @()
    $missing = $false
    foreach ($p in $paths) {
        if (-not [System.IO.File]::Exists($p)) {
            Add-Err ("双版本文件缺失: [Errno 2] No such file or directory: '" + $p + "'")
            $missing = $true
            break
        }
        $digests += (Get-Md5Hex $p)
    }
    if ($missing) { continue }
    if ((ConvertTo-StrSet $digests).Count -ne 1) {
        Add-Err ('双版本不一致: ' + $pool + ' → ' + (ConvertTo-PyRepr $digests))
    }
    else {
        Write-Out ('OK  双版本一致 ' + $pool.PadRight(24) + ' md5 ' + $digests[0])
    }
}

# 6) 价值平衡式（逐版本，取 neoforge 结果展示）
foreach ($ver in $VERSIONS) {
    $objs = (Get-Pool ($pre + $ver + '/' + $POOL_REL + 'astral_objs.json'))[0]
    $rews = (Get-Pool ($pre + $ver + '/' + $POOL_REL + 'astral_rews.json'))[0]
    $objsTopD = 0.0
    foreach ($v in $objs.Values) {
        $x = [double]$v['amount']['max'] * [double]$v['unitWorth']
        if ($x -gt $objsTopD) { $objsTopD = $x }
    }
    $vals = @()
    foreach ($v in $rews.Values) { $vals += ([double]$v['amount']['max'] * [double]$v['unitWorth']) }
    [Array]::Sort($vals)
    $top2 = @()
    if ($vals.Count -ge 1) { $top2 += $vals[$vals.Count - 1] }
    if ($vals.Count -ge 2) { $top2 += $vals[$vals.Count - 2] }
    $sumTop2D = 0.0
    foreach ($x in $top2) { $sumTop2D += $x }
    $need = $sumTop2D * 0.9
    $ok = $objsTopD -ge $need
    $head = 'FAIL'
    $verdict = 'FAIL'
    if ($ok) { $head = 'OK  '; $verdict = 'PASS' }
    $dispObjs = [long][Math]::Truncate($objsTopD)
    $dispSum = [long][Math]::Truncate($sumTop2D)
    $dispNeed = [long][Math]::Round($need, 0, [System.MidpointRounding]::ToEven)
    Write-Out ($head + ' 价值平衡: objs 顶值 ' + $dispObjs + ' ≥ rews 顶值2和 ' + $dispSum + ' × 0.9 = ' + $dispNeed + ' → ' + $verdict)
    if (-not $ok) {
        Add-Err ($ver + ' 价值平衡式不成立')
    }
}

# 7) decree 引用
foreach ($ver in $VERSIONS) {
    $p = $pre + $ver + '/' + $DECREE_REL
    try {
        $d = ConvertFrom-Json -InputObject (Read-RawText $p) -AsHashtable
        $objOk = ($d.Contains('objectives') -and (@($d['objectives']).Count -eq 1) -and ($d['objectives'][0] -ceq 'astral_objs'))
        $rewOk = ($d.Contains('rewards') -and (@($d['rewards']).Count -eq 1) -and ($d['rewards'][0] -ceq 'astral_rews'))
        if ((-not $objOk) -or (-not $rewOk)) {
            Add-Err ($ver + ' decree 引用异常: ' + (ConvertTo-PyRepr $d))
        }
        else {
            Write-Out ('OK  ' + $ver + ' decree 引用 astral_objs/astral_rews')
        }
    }
    catch [System.IO.IOException] {
        Add-Err ($ver + " decree 缺失: [Errno 2] No such file or directory: '" + $p + "'")
    }
}

Write-Out ''
foreach ($w in $warnings) { Write-Out ('WARN ' + $w) }
if ($errors.Count -gt 0) {
    foreach ($e in $errors) { Write-Out ('FAIL ' + $e) }
    Write-Out ''
    Write-Out ('结果: ' + $errors.Count + ' 项致命偏差')
    [Console]::Out.Flush()
    exit 1
}
$suffix = ''
if ($warnings.Count -gt 0) { $suffix = '（' + $warnings.Count + ' 条提示）' }
Write-Out ('结果: ALL OK' + $suffix)
[Console]::Out.Flush()
exit 0
