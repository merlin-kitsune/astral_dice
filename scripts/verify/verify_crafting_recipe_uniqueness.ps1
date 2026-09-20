# -*- coding: utf-8 -*-
<#
合成配方「网格唯一性」静态守门 —— 防止两条配方对同一个 3×3 摆放同时成立。

用法(仓库根目录执行;也可在任意目录执行,路径按脚本位置推导):
    pwsh -NoProfile -File scripts/verify/verify_crafting_recipe_uniqueness.ps1
    pwsh -NoProfile -File scripts/verify/verify_crafting_recipe_uniqueness.ps1 -LogDir temp

退出门槛:exit 0 = 全部子项目零重合;exit 1 = 存在重合(或 JSON 解析失败)。

为什么需要它(2026-09-27 实例):
    原版 `RecipeManager#getRecipeFor`(`RecipeManager.java:94-104`)取
    `byType(CRAFTING)` 中**第一条** `matches()` 命中的配方,而 `byType` 是
    `ImmutableMultimap`,其顺序来自 `SimpleJsonResourceReloadListener#prepare`
    的 `HashMap` 迭代序(`:32`)⇒ 网格相同、只有产出不同的两条配方里,
    **永远只有一条能被合成台产出**,另一条连 JEI 点了也只会给赢家。
    本仓曾同时存在两组:`zhao_sign`/`teru_sign`(GCG/RER/ZPZ)、
    `nardis_sign`/`padman_sign`(WYW/TET/TDT);蛟龙立牌 mamushi 照抄 fen
    时又险些引入第三组。⇒ 必须由静态守门兜住。

判据(按原版 `matches()` 语义推导,不是简单的文本比对):
    * shaped ↕ shaped:把 pattern 展开成 3×3 单元矩阵,去掉**四周全空的
      行/列**(原版按偏移量在合成网格内滑动匹配,故前导/尾随空行空列不参与
      判定),**内部空格仍参与**(该格必须为空)⇒ 两个裁剪后的矩阵逐格相同
      即视为重合。
    * 任意两条配方:比较「非空材料**多重集**」—— 无序配方(shapeless)只按
      材料多重集匹配,故`shaped` 与 `shapeless` 的多重集相同时也重合。
    * 材料等价只按**字面 id** 比较:`{"item":"X"}` / `{"tag":"T"}` /
      数组形式(1.21 的 ingredient 列表)分别归一为 item:/tag:/oneof:。
      ⚠️ **已知边界(不在本守门能力内)**:① 不同 tag 之间、tag 与其成员
      物品之间的**集合重叠**不展开比较;② 自定义配方类型(`astral_dice:dice_upgrade`
      等)挂在各自的 RecipeType 上,不与 `crafting` 竞争,故不参与判定;
      ③ 本守门只看**资源产物**(生成目录 + 手写目录),必须在 `runData` 之后
      跑才有意义(生成目录未刷新时会漏报)。
#>
[CmdletBinding()]
param(
    [string]$Root = '',
    [string]$LogDir = ''
)

$ErrorActionPreference = 'Stop'
$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
try { [Console]::OutputEncoding = $utf8NoBom } catch { }
$OutputEncoding = $utf8NoBom

function Write-Out {
    param([AllowEmptyString()][string]$Text = '')
    [Console]::Out.Write("$Text`n")
}
function Write-Err {
    param([AllowEmptyString()][string]$Text = '')
    [Console]::Error.Write("$Text`n")
}

if ([string]::IsNullOrWhiteSpace($Root)) {
    $Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
}
if (-not (Test-Path -LiteralPath $Root)) {
    Write-Err "RECIPE_UNIQ: ERROR root not found: $Root"
    exit 1
}
$Root = (Resolve-Path -LiteralPath $Root).Path

$lines = @()
foreach ($name in @('neoforge-1.21.1', 'forge-1.20.1', 'neoforge-26.1.2')) {
    $proj = Join-Path $Root $name
    if (Test-Path -LiteralPath $proj) { $lines += [pscustomobject]@{ Name = $name; Dir = $proj } }
}
if ($lines.Count -eq 0) {
    Write-Err "RECIPE_UNIQ: ERROR no subproject found under $Root"
    exit 1
}

# ---------------------------------------------------------------------------
# 材料归一化:{"item":X} -> item:X ;{"tag":T} -> tag:T ;数组 -> oneof:[...] ;
# 其它形态(药水等) -> json:<紧凑序列化>
# ---------------------------------------------------------------------------
function Get-IngredientKey {
    param($Value)
    if ($null -eq $Value) { return '' }
    if ($Value -is [System.Array]) {
        $inner = @($Value | ForEach-Object { Get-IngredientKey $_ } | Where-Object { $_ -ne '' } | Sort-Object -Unique)
        return 'oneof:[' + ($inner -join '+') + ']'
    }
    if ($Value -is [string]) { return "raw:$Value" }
    $props = @($Value.PSObject.Properties | ForEach-Object { $_.Name })
    if ($props -contains 'item') { return "item:$($Value.item)" }
    if ($props -contains 'tag') { return "tag:$($Value.tag)" }
    return 'json:' + (($Value | ConvertTo-Json -Depth 8 -Compress) -replace '\s+', '')
}

# ---------------------------------------------------------------------------
# shaped 展开为 3×3 单元矩阵,再裁剪四周全空的行/列
# ---------------------------------------------------------------------------
function Get-ShapedCells {
    param($Json)
    $pat = @($Json.pattern)
    $h = $pat.Count
    $w = 0
    foreach ($row in $pat) { if ($row.Length -gt $w) { $w = $row.Length } }
    $grid = New-Object 'object[,]' 3, 3
    for ($r = 0; $r -lt 3; $r++) {
        for ($c = 0; $c -lt 3; $c++) {
            $grid[$r, $c] = '.'
        }
    }
    for ($r = 0; $r -lt $h; $r++) {
        $row = [string]$pat[$r]
        for ($c = 0; $c -lt $row.Length; $c++) {
            $ch = [string]$row[$c]
            if ($ch -eq ' ') { continue }
            $keyProp = $Json.key.PSObject.Properties | Where-Object { $_.Name -ceq $ch } | Select-Object -First 1
            if ($null -eq $keyProp) { $grid[$r, $c] = "missing:$ch" } else { $grid[$r, $c] = (Get-IngredientKey $keyProp.Value) }
        }
    }
    # 裁剪:上下左右全空的行/列
    $rowsWithContent = @()
    for ($r = 0; $r -lt 3; $r++) {
        for ($c = 0; $c -lt 3; $c++) { if ($grid[$r, $c] -ne '.') { $rowsWithContent += $r; break } }
    }
    $colsWithContent = @()
    for ($c = 0; $c -lt 3; $c++) {
        for ($r = 0; $r -lt 3; $r++) { if ($grid[$r, $c] -ne '.') { $colsWithContent += $c; break } }
    }
    if ($rowsWithContent.Count -eq 0 -or $colsWithContent.Count -eq 0) { return @{ Cells = @(); W = 0; H = 0 } }
    $r0 = ($rowsWithContent | Measure-Object -Minimum).Minimum
    $r1 = ($rowsWithContent | Measure-Object -Maximum).Maximum
    $c0 = ($colsWithContent | Measure-Object -Minimum).Minimum
    $c1 = ($colsWithContent | Measure-Object -Maximum).Maximum
    $cellList = @()
    $multi = @()
    for ($r = $r0; $r -le $r1; $r++) {
        for ($c = $c0; $c -le $c1; $c++) {
            $cellList += $grid[$r, $c]
            if ($grid[$r, $c] -ne '.') { $multi += $grid[$r, $c] }
        }
    }
    return @{ Cells = $cellList; W = ($c1 - $c0 + 1); H = ($r1 - $r0 + 1); Multi = $multi }
}

function Get-MultisetKey {
    param([string[]]$Items)
    $sorted = @($Items | Sort-Object)
    return 'MULTI|' + ($sorted -join ';')
}

function Read-Recipes {
    param([string]$ProjectDir)
    $found = @{}
    $warn = @()
    $dirs = @(
        'src/generated/resources/data/astral_dice/recipe',
        'src/generated/resources/data/astral_dice/recipes',
        'src/main/resources/data/astral_dice/recipe',
        'src/main/resources/data/astral_dice/recipes'
    )
    foreach ($rel in $dirs) {
        $d = Join-Path $ProjectDir $rel
        if (-not (Test-Path -LiteralPath $d)) { continue }
        foreach ($f in (Get-ChildItem -LiteralPath $d -Filter *.json -File)) {
            $id = $f.BaseName
            if ($found.ContainsKey($id)) {
                $warn += "duplicate id '$id' in '$($found[$id].Source)' and '$rel' (资源管理器只保留先加载的一份)"
                continue
            }
            $json = $null
            try { $json = Get-Content -LiteralPath $f.FullName -Raw -Encoding UTF8 | ConvertFrom-Json }
            catch { $warn += "JSON parse failed: $rel/$($f.Name) — $($_.Exception.Message)"; continue }
            $found[$id] = [pscustomobject]@{
                Id     = $id
                Type   = [string]$json.type
                Json   = $json
                Source = $rel
            }
        }
    }
    return [pscustomobject]@{ Recipes = $found; Warn = $warn }
}

$totalProblems = 0
$report = @()

foreach ($line in $lines) {
    $read = Read-Recipes -ProjectDir $line.Dir
    foreach ($w in $read.Warn) { $report += "  [WARN] $($line.Name): $w" }

    $shaped = @()
    $shapeless = @()
    foreach ($r in $read.Recipes.Values) {
        switch -Regex ($r.Type) {
            'crafting_shaped$' {
                $cells = Get-ShapedCells -Json $r.Json
                if ($cells.W -eq 0) {
                    $report += "  [WARN] $($line.Name): $($r.Id) 是空网格(无任何材料),已跳过"
                    continue
                }
                $shaped += [pscustomobject]@{
                    Id       = $r.Id
                    GridKey  = "SHAPED|$($cells.W)x$($cells.H)|" + ($cells.Cells -join ',')
                    MultiKey = (Get-MultisetKey -Items $cells.Multi)
                    Multi    = (@($cells.Multi | Sort-Object) -join '+')
                }
            }
            'crafting_shapeless$' {
                $multi = @()
                $ing = $r.Json.ingredients
                if ($null -ne $ing) {
                    foreach ($one in @($ing)) { $k = Get-IngredientKey $one; if ($k -ne '') { $multi += $k } }
                }
                if ($multi.Count -eq 0) {
                    $report += "  [WARN] $($line.Name): $($r.Id) 是 shapeless 但 ingredients 为空,已跳过"
                    continue
                }
                $shapeless += [pscustomobject]@{
                    Id       = $r.Id
                    GridKey  = (Get-MultisetKey -Items $multi)
                    MultiKey = (Get-MultisetKey -Items $multi)
                    Multi    = (@($multi | Sort-Object) -join '+')
                }
            }
        }
    }

    # 同类型重合
    $problems = @()
    foreach ($group in ($shaped | Group-Object GridKey | Where-Object { $_.Count -gt 1 })) {
        $problems += [pscustomobject]@{
            Kind = 'shaped/shaped'
            Ids  = (@($group.Group | ForEach-Object { $_.Id }) | Sort-Object) -join ' | '
            Key  = (@($group.Group)[0].Multi)
        }
    }
    foreach ($group in ($shapeless | Group-Object GridKey | Where-Object { $_.Count -gt 1 })) {
        $problems += [pscustomobject]@{
            Kind = 'shapeless/shapeless'
            Ids  = (@($group.Group | ForEach-Object { $_.Id }) | Sort-Object) -join ' | '
            Key  = (@($group.Group)[0].Multi)
        }
    }
    # 跨类型:shaped 与 shapeless 的材料多重集相同时,同一个摆放会同时命中两条
    foreach ($s in $shaped) {
        foreach ($l in $shapeless) {
            if ($s.MultiKey -eq $l.MultiKey) {
                $problems += [pscustomobject]@{
                    Kind = 'shaped/shapeless'
                    Ids  = (@($s.Id, $l.Id) | Sort-Object) -join ' | '
                    Key  = $s.Multi
                }
            }
        }
    }

    if ($problems.Count -gt 0) {
        $totalProblems += $problems.Count
        $report += "  [FAIL] $($line.Name): 重合 $($problems.Count) 组"
        foreach ($p in $problems) {
            $report += "         - $($p.Kind): $($p.Ids)  材料={$($p.Key)}"
        }
    }
    $report += ("RECIPE_UNIQ|{0}|shaped={1}|shapeless={2}|collisions={3}" -f $line.Name, $shaped.Count, $shapeless.Count, $problems.Count)
}

$report += ("RECIPE_UNIQ: TOTAL collisions=$totalProblems lines=$($lines.Count)")

foreach ($l in $report) { Write-Out $l }
if (-not [string]::IsNullOrWhiteSpace($LogDir)) {
    $abs = Join-Path $Root $LogDir
    if (-not (Test-Path -LiteralPath $abs)) { New-Item -ItemType Directory -Force -Path $abs | Out-Null }
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $outFile = Join-Path $abs "recipe_uniqueness_$stamp.txt"
    [System.IO.File]::WriteAllText($outFile, (($report -join "`n") + "`n"), $utf8NoBom)
    Write-Out "RECIPE_UNIQ: log=$outFile"
}

if ($totalProblems -gt 0) {
    Write-Err "RECIPE_UNIQ: FAIL collisions=$totalProblems"
    exit 1
}
Write-Out 'RECIPE_UNIQ: OK'
exit 0
