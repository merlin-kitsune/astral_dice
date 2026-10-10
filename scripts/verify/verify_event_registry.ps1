# 事件注册表静态守门（2026-10-10 事件体系重写）
# 判据：① 注册的 id 都在 AstralEventIds 里有常量；② id 路径唯一；③ 三语 lang 均有 event.astral_dice.<path>.name；
#       ④ 图标 assets/astral_dice/textures/gui/event/<path>.png 存在（investigation_stage 另查 4 张阶级图）；
#       ⑤ 事件键字面量不得散落在注册表之外（warn）。
param(
    [string]$Root = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path,
    [string]$Line = "neoforge-1.21.1"
)

$ErrorActionPreference = "Stop"
$src = Join-Path $Root "$Line/src/main/java/com/merlinkitsune/astral_dice"
$res = Join-Path $Root "$Line/src/main/resources"
$idsFile = Join-Path $src "event/AstralEventIds.java"
$regFile = Join-Path $src "event/AstralEventRegistry.java"
$fail = 0

if (-not (Test-Path $idsFile) -or -not (Test-Path $regFile)) {
    Write-Host "EVENT_REG: FAIL 缺少源文件（$idsFile / $regFile）"
    exit 1
}

$constPattern = 'public static final ResourceLocation (\w+) = id\("([^"]+)"\)'
$constMap = @{}
foreach ($m in [regex]::Matches((Get-Content $idsFile -Raw), $constPattern)) {
    $constMap[$m.Groups[1].Value] = $m.Groups[2].Value
}

$refPattern = 'AstralEventIds\.(\w+)'
$registered = @()
foreach ($m in [regex]::Matches((Get-Content $regFile -Raw), $refPattern)) {
    $name = $m.Groups[1].Value
    if ($name -eq "id") { continue }
    if ($registered -notcontains $name) { $registered += $name }
}
if ($registered.Count -eq 0) { Write-Host "EVENT_REG: FAIL 注册表中未找到任何 AstralEventIds 引用"; exit 1 }

$paths = @()
foreach ($c in $registered) {
    if (-not $constMap.ContainsKey($c)) { Write-Host "EVENT_REG: FAIL 常量 $c 未在 AstralEventIds 中定义"; $fail++; continue }
    $path = $constMap[$c]
    if ($paths -contains $path) { Write-Host "EVENT_REG: FAIL id 路径重复: $path"; $fail++ }
    $paths += $path
    foreach ($lang in @("zh_cn","en_us","ja_jp")) {
        $langFile = Join-Path $res "assets/astral_dice/lang/$lang.json"
        $keyPattern = 'event\.astral_dice\.' + [regex]::Escape($path) + '\.name'
        if (-not (Select-String -Path $langFile -Pattern $keyPattern -Quiet)) {
            Write-Host "EVENT_REG: FAIL 缺少三语键 event.astral_dice.$path.name（$lang）"; $fail++
        }
    }
    $png = Join-Path $res "assets/astral_dice/textures/gui/event/$path.png"
    if (-not (Test-Path $png)) { Write-Host "EVENT_REG: FAIL 缺少图标 textures/gui/event/$path.png"; $fail++ }
}

foreach ($t in @("investigation_stage_1","investigation_stage_2","investigation_stage_3","investigation_stage_revelation")) {
    $png = Join-Path $res "assets/astral_dice/textures/gui/event/$t.png"
    if (-not (Test-Path $png)) { Write-Host "EVENT_REG: FAIL 缺少阶级图标 textures/gui/event/$t.png"; $fail++ }
}

$stray = Get-ChildItem -Path $src -Recurse -Filter *.java -ErrorAction SilentlyContinue |
    Select-String -Pattern 'event\.astral_dice\.' -ErrorAction SilentlyContinue |
    Where-Object { $_.Path -notmatch "AstralEventRegistry\.java$" }
foreach ($s in $stray) { Write-Host ("EVENT_REG: WARN 事件键字面量散落 " + $s.Path.Replace($Root, "") + ":" + $s.LineNumber) }

foreach ($k in $constMap.Keys) {
    if ($registered -notcontains $k) { Write-Host "EVENT_REG: WARN 常量 $k（$($constMap[$k])）已定义但未注册" }
}

if ($fail -gt 0) { Write-Host "EVENT_REG: FAIL failures=$fail"; exit 1 }
Write-Host ("EVENT_REG: OK line=$Line events=" + $registered.Count + " ids=" + $constMap.Count + " icons=ok langs=3")
exit 0
