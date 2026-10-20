<#
.SYNOPSIS
    立牌注册 / 标签一致性守门（2026-10-21 起）。

.DESCRIPTION
    1.21.1 起立牌要能装备，必须同时出现在三处：
      - `item/ModItems.java` 的 registerItem("..._sign")
      - `data/astral_dice/tags/item(s)/signs.json`
      - `data/curios/tags/item(s)/stand.json`（blank_sign 按设计**不**入此表）

    2026-10-21 事故：jill / dorothy / alana / deepseek 四张立牌只注册了物品、
    漏了两个标签 ⇒ 游戏内无法装备。本守门用于在构建前发现同类漏标。

    退出码：0 = 一致；1 = 有缺失/多余；2 = 无法判定（文件缺失）。
#>
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if (-not (Test-Path (Join-Path $root 'settings.gradle'))) { $root = (Get-Location).Path }

$LINES = @(
    @{ Key = 'neoforge-1.21.1'; Tags = 'item' },
    @{ Key = 'forge-1.20.1';    Tags = 'items' },
    @{ Key = 'neoforge-26.1.2'; Tags = 'item' },
    @{ Key = 'fabric-1.20.1';   Tags = 'items' }
)
$EXEMPT_STAND = @('blank_sign')   # 空白立牌是合成基底，不进可装备表

$fail = 0; $undecidable = 0
foreach ($line in $LINES) {
    $proj = $line.Key; $tags = $line.Tags
    $items = Join-Path $root "$proj/src/main/java/com/merlinkitsune/astral_dice/item/ModItems.java"
    $signs = Join-Path $root "$proj/src/main/resources/data/astral_dice/tags/$tags/signs.json"
    $stand = Join-Path $root "$proj/src/main/resources/data/curios/tags/$tags/stand.json"
    foreach ($p in @($items, $signs, $stand)) {
        if (-not (Test-Path -LiteralPath $p)) { Write-Output "SIGN_REG: UNDECIDABLE [$proj] 缺少 $p"; $undecidable++ }
    }
    if ($undecidable -gt 0) { continue }

    $registered = [regex]::Matches([IO.File]::ReadAllText($items), 'registerItem\("([a-z0-9_]+_sign)"') |
        ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique
    $signTag = @(Get-Content -Raw -LiteralPath $signs | ConvertFrom-Json).values | ForEach-Object { ($_ -replace '^astral_dice:', '') }
    $standTag = @(Get-Content -Raw -LiteralPath $stand | ConvertFrom-Json).values | ForEach-Object { ($_ -replace '^astral_dice:', '') }

    $missingSigns = @($registered | Where-Object { $signTag -notcontains $_ })
    $missingStand = @($registered | Where-Object { $EXEMPT_STAND -notcontains $_ -and $standTag -notcontains $_ })
    $extra = @(($signTag + $standTag) | Sort-Object -Unique | Where-Object { $registered -notcontains $_ })

    if ($missingSigns.Count -gt 0 -or $missingStand.Count -gt 0 -or $extra.Count -gt 0) {
        $fail++
        Write-Output "SIGN_REG: FAIL [$proj] registered=$($registered.Count) signs=$($signTag.Count) stand=$($standTag.Count)"
        if ($missingSigns.Count) { Write-Output ("   缺 signs.json : " + ($missingSigns -join ', ')) }
        if ($missingStand.Count) { Write-Output ("   缺 stand.json : " + ($missingStand -join ', ')) }
        if ($extra.Count)        { Write-Output ("   标签内未注册: " + ($extra -join ', ')) }
    } else {
        Write-Output "SIGN_REG: OK [$proj] registered=$($registered.Count) signs=$($signTag.Count) stand=$($standTag.Count)"
    }
}
if ($undecidable -gt 0) { Write-Output "SIGN_REG: UNDECIDABLE（检查面缺失）"; exit 2 }
if ($fail -gt 0) { Write-Output "SIGN_REG: FAIL（$fail 条线不一致）"; exit 1 }
Write-Output "SIGN_REG: ALL OK"
exit 0
