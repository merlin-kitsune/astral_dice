<#
.SYNOPSIS
    资源完整性守门：核对「物品注册表 → 模型 → 物品模型定义」三步链是否**逐条闭合**。

.DESCRIPTION
    「紫黑格子」有两种成因，且**从现象上无法区分**：
      ① 贴图缺失（`textures/item/<id>.png` 不在 jar 里）；
      ② **模型缺失**（`models/item/<id>.json` 或 26.1.2 的 `items/<id>.json` 不在 jar 里）——
        此时贴图明明在，但物品没有模型 ⇒ 渲染成紫黑格。
    2026-09-22 用户实报的正是第②种：26.1.2 的 `ModItemModelProvider` 是**逐行硬编码清单**，
    第 4 代新增的 12 个物品从未登记 ⇒ datagen 不产出模型与物品模型定义 ⇒ 12 个物品全变紫黑格子。
    （同类风险：`ModRecipeProvider` 也是硬编码清单，同批漏了 8 个配方 ⇒ 物品合不出来。）

    本守门把「注册了什么」与「打包了什么」做集合比对，任一条缺失即红——这样**新增物品时
    忘记同步 provider** 会在 CI/本地立刻暴露，而不是等到实机看见紫黑格子才发现。

    检查项（每线独立）：
      1. `ModItems.java` 里 `registerItem("id", …)` / `register("id", …)` 的全部 id；
      2. jar 内 `assets/astral_dice/models/item/<id>.json` 覆盖全部 id（三线都要求）；
      3. jar 内 `assets/astral_dice/items/<id>.json` 覆盖全部 id
         （**仅 26.1.2**：1.21.4+ 的物品模型定义机制，缺失同样表现为无模型）；
      4. jar 内 `assets/astral_dice/textures/item/<id>.png` 覆盖全部 id
         （部分物品共用贴图或有替代渲染时不强制，缺失只报 WARN 不判失败）。

    只读，不修改任何工程文件。
    退出码：0 = 全部通过；1 = 存在致命偏差（模型/物品模型定义缺失）；用法错误 = 2。

.PARAMETER Version
    要检查的版本线：`1.21.1` / `1.20.1` / `26.1.2` / `all`（默认 all）。
    目录名约定：`1.21.1` → `neoforge-1.21.1`；`1.20.1` → `forge-1.20.1`；`26.1.2` → `neoforge-26.1.2`。

.PARAMETER Root
    仓库根目录（默认当前目录）。

.EXAMPLE
    pwsh -File scripts/verify/verify_resource_integrity.ps1
    pwsh -File scripts/verify/verify_resource_integrity.ps1 -Version 26.1.2

.NOTES
    背景与实测证据见 `scripts/test/cases/TEX-26.1.2.json`（视觉取证用例）与
    `docs/compat-26.1.2-neoforge.md` §2.3（「物品模型新增 assets/<ns>/items/*.json，26.1.2 必需」）。
    jar 不存在时该线报 SKIP（先跑构建）。
#>
[CmdletBinding()]
param(
    [ValidateSet('1.21.1', '1.20.1', '26.1.2', 'all')]
    [string]$Version = 'all',
    [string]$Root = '.'
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem | Out-Null

$MODID = 'astral_dice'
$script:Fatal = 0

function Get-LineDir([string]$ver) {
    switch ($ver) {
        '1.21.1' { return 'neoforge-1.21.1' }
        '1.20.1' { return 'forge-1.20.1' }
        '26.1.2' { return 'neoforge-26.1.2' }
    }
}

function Get-RegisteredIds([string]$dir) {
    $f = Join-Path $dir (Join-Path 'src/main/java/com/merlinkitsune' (Join-Path $MODID 'item/ModItems.java'))
    if (-not (Test-Path -LiteralPath $f)) { return $null }
    $txt = Get-Content -LiteralPath $f -Raw
    $m = [regex]::Matches($txt, '(?:registerItem|register)\(\s*"([a-z0-9_]+)"')
    $ids = @()
    foreach ($x in $m) { $ids += $x.Groups[1].Value }
    return ($ids | Sort-Object -Unique)
}

function Get-Jar([string]$dir) {
    $libs = Join-Path $dir 'build/libs'
    if (-not (Test-Path -LiteralPath $libs)) { return $null }
    $c = @(Get-ChildItem -LiteralPath $libs -Filter "$MODID-*.jar" -File |
            Where-Object { $_.Name -notmatch 'sources|javadoc' } |
            Sort-Object LastWriteTime)
    if ($c.Count -eq 0) { return $null }
    return $c[-1]
}

function Test-Line([string]$ver) {
    $dir = Join-Path $Root (Get-LineDir $ver)
    Write-Output ''
    Write-Output ("=" * 92)
    Write-Output ("资源完整性 [$ver]  $dir")
    Write-Output ("=" * 92)

    $ids = Get-RegisteredIds $dir
    if ($null -eq $ids -or @($ids).Count -eq 0) {
        Write-Output '  [SKIP] 找不到 ModItems.java（路径约定变了？）'
        return
    }
    $jar = Get-Jar $dir
    if ($null -eq $jar) {
        Write-Output ("  [SKIP] 无 jar（先构建）；注册 id 数 = {0}" -f @($ids).Count)
        return
    }
    Write-Output ("  jar   = {0}" -f $jar.Name)
    Write-Output ("  注册物品 id = {0}" -f @($ids).Count)

    $zip = [System.IO.Compression.ZipFile]::OpenRead($jar.FullName)
    try {
        $names = @{}
        foreach ($e in $zip.Entries) { $names[$e.FullName] = $true }

        $checks = @(
            @{ Pref = "assets/$MODID/models/item/"; Ext = '.json'; Label = '物品模型'; Fatal = $true },
            @{ Pref = "assets/$MODID/textures/item/"; Ext = '.png'; Label = '物品贴图'; Fatal = $false }
        )
        # 26.1.2 额外要求 1.21.4+ 的物品模型定义
        if ($ver -eq '26.1.2') {
            $checks = @(
                @{ Pref = "assets/$MODID/models/item/"; Ext = '.json'; Label = '物品模型'; Fatal = $true },
                @{ Pref = "assets/$MODID/items/"; Ext = '.json'; Label = '物品模型定义(1.21.4+)'; Fatal = $true },
                @{ Pref = "assets/$MODID/textures/item/"; Ext = '.png'; Label = '物品贴图'; Fatal = $false }
            )
        }

        foreach ($c in $checks) {
            $miss = @()
            foreach ($id in $ids) {
                if (-not $names.ContainsKey($c.Pref + $id + $c.Ext)) { $miss += $id }
            }
            if ($miss.Count -eq 0) {
                Write-Output ("  [OK]   {0,-24} 覆盖 {1}/{1}" -f $c.Label, @($ids).Count)
            } else {
                $tag = if ($c.Fatal) { '[MISS]' } else { '[WARN]' }
                Write-Output ("  {0} {1,-24} 缺 {2}/{3}：" -f $tag, $c.Label, $miss.Count, @($ids).Count)
                foreach ($m in $miss) { Write-Output ("           - {0}" -f $m) }
                Write-Output '           ↑ 新增物品时须同步：datagen 的 ItemModelProvider 登记（26.1.2 还需重跑两段式 datagen）'
                if ($c.Fatal) { $script:Fatal = 1 }
            }
        }
    } finally {
        $zip.Dispose()
    }
}

$vers = if ($Version -eq 'all') { @('1.21.1', '1.20.1', '26.1.2') } else { @($Version) }
foreach ($v in $vers) { Test-Line $v }

Write-Output ''
if ($script:Fatal -eq 0) {
    Write-Output 'RESULT: PASS —— 模型链逐条闭合（紫黑格子成因②已排除）'
    exit 0
} else {
    Write-Output 'RESULT: FAIL —— 存在无模型物品（实机会渲染成紫黑格子）'
    exit 1
}
