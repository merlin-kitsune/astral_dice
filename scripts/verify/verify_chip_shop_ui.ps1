<#
筹码商店界面守门（静态判据 G1..G6；编译 G7 与资源守门 G8 另行执行）
用法（仓库根目录执行）：
    pwsh -NoProfile -File scripts/verify/verify_chip_shop_ui.ps1            # 两线全跑
    pwsh -NoProfile -File scripts/verify/verify_chip_shop_ui.ps1 neo        # 只查 neoforge-1.21.1
    pwsh -NoProfile -File scripts/verify/verify_chip_shop_ui.ps1 forge      # 只查 forge-1.20.1
退出码 0 = 全绿；1 = 有缺失/不一致。判据正本：docs/chip-shop-ui-spec.md §8。
说明：本脚本是「筹码商店界面」这批的**先写后实现**判据（TDD 的 RED 侧）——
      首次运行必然失败（界面尚未实现），实现到全绿即通过。
#>
[CmdletBinding()]
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$Arguments
)

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8
$ErrorActionPreference = 'Stop'

function Write-Out([string]$text) {
    [Console]::Out.Write($text)
    [Console]::Out.Write([char]10)
}

$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$lines = @{ 'neo' = 'neoforge-1.21.1'; 'forge' = 'forge-1.20.1' }
$mtVersions = @{ 'neo' = '1.21.1'; 'forge' = '1.20.1' }
$targets = @()
$arg0 = ''
if ($Arguments -and $Arguments.Count -gt 0) { $arg0 = $Arguments[0].ToLower() }
if ($arg0 -eq 'neo') { $targets = @('neo') }
elseif ($arg0 -eq 'forge') { $targets = @('forge') }
else { $targets = @('neo', 'forge') }

$fail = @()
function Fail([string]$msg) { $script:fail += $msg; Write-Out ('CHIPUI_FAIL: ' + $msg) }

$texNames = @(
    'chipshop_bg.png', 'chipshop_frame.png', 'chipshop_frame_1.png', 'chipshop_frame_2.png',
    'chipshop_inventory.png', 'chipshop_confirm.png', 'chipshop_confirm_push.png',
    'chipshop_refresh.png', 'chipshop_refresh_push.png',
    'chipshop_quality_1.png', 'chipshop_quality_2.png', 'chipshop_quality_3.png', 'chipshop_select.png'
)

$layoutTokens = @(
    'GUI_WIDTH = 284', 'GUI_HEIGHT = 183',
    'STRIP_X = 34', 'STRIP_Y = 6', 'STRIP_W = 216', 'STRIP_H = 78',
    'CELL_X = {3, 45, 88, 131, 174}',
    'BUTTON_SIZE = 24', 'CONFIRM_X = 11', 'CONFIRM_Y = 36', 'REFRESH_X = 181', 'REFRESH_Y = 36',
    'QUALITY_SIZE = 28', 'QUALITY_X = {51, 94, 137}', 'QUALITY_Y = 31',
    'CHIP_ICON_SIZE = 16', 'CHIP_ICON_OFF = 6',
    'LABEL_W = 42', 'LABEL_H = 15', 'LABEL_Y = 61', 'LABEL_X = {1, 44, 87, 130, 172}',
    'PANEL_X = 56', 'PANEL_Y = 89', 'PANEL_W = 172', 'PANEL_H = 86',
    'SLOT_X0 = 61', 'SLOT_Y0 = 94', 'SLOT_PITCH = 18', 'SLOT_COLS = 9', 'SLOT_ROWS = 3', 'HOTBAR_Y = 152'
)

$langKeys = @(
    'gui.astral_dice.chip_shop',
    'gui.astral_dice.chip_shop.cost',
    'gui.astral_dice.chip_shop.refresh_counter',
    'gui.astral_dice.chip_shop.empty'
)

$forbidden = @('mouseDragged', 'DOUBLE_CLICK_MS', 'DRAG_THRESHOLD', 'dragIndex')

Write-Out 'CHIPUI: 筹码商店界面守门（G1..G6 静态判据）'
Write-Out ('CHIPUI_ROOT: ' + $root)

foreach ($key in $targets) {
    $line = $lines[$key]
    $lineDir = Join-Path $root $line
    Write-Out ('--- ' + $line + ' ---')

    if (-not (Test-Path $lineDir)) { Fail ($line + ': 子项目目录不存在'); continue }

    # G1 素材
    $texDir = Join-Path $lineDir 'src/main/resources/assets/astral_dice/textures/gui/shop'
    $ok = 0
    foreach ($n in $texNames) {
        $src = Join-Path $root ('images/shop/' + $n)
        $dst = Join-Path $texDir $n
        if (-not (Test-Path $src)) { Fail ($line + ': 源素材缺失 images/shop/' + $n); continue }
        if (-not (Test-Path $dst)) { Fail ($line + ': 素材未入库 ' + $n); continue }
        $hs = (Get-FileHash -Algorithm SHA256 -LiteralPath $src).Hash
        $hd = (Get-FileHash -Algorithm SHA256 -LiteralPath $dst).Hash
        if ($hs -ne $hd) { Fail ($line + ': 素材与源不一致 ' + $n) } else { $ok++ }
    }
    $refDst = Join-Path $texDir 'chip_shop_GUI.png'
    if (Test-Path $refDst) { Fail ($line + ': 参考图 chip_shop_GUI.png 不得入 jar') }
    Write-Out ('CHIPUI_ASSETS: ' + $ok + '/' + $texNames.Count + ' 逐字节一致')

    # G2/G3/G4 屏幕类
    $screenPath = Join-Path $lineDir 'src/main/java/com/merlinkitsune/astral_dice/screen/ChipShopScreen.java'
    if (-not (Test-Path $screenPath)) {
        Fail ($line + ': 缺少 screen/ChipShopScreen.java')
    } else {
        $src = Get-Content -LiteralPath $screenPath -Raw
        $missing = @()
        foreach ($t in $layoutTokens) { if ($src.IndexOf($t) -lt 0) { $missing += $t } }
        if ($missing.Count -gt 0) { Fail ($line + ': 布局常量缺失 ' + ($missing -join ' | ')) }
        Write-Out ('CHIPUI_LAYOUT: ' + ($layoutTokens.Count - $missing.Count) + '/' + $layoutTokens.Count + ' 常量命中')

        # 判据：13 张贴图名都要有常量定义；除白名单外，每个贴图常量都必须「可被绘制路径取到」：
        #   直接出现在 tex(...) 实参里（含三元表达式），或出现在「被 tex(...) 调用的字符串返回助手」方法体内。
        $regOnly = @('TEX_STRIP_FRAME')   # 素材登记、不入绘制路径（规范 §2/§3：本批外层用 frame_2）
        $defs = @{}
        foreach ($m in [regex]::Matches($src, 'private static final String (TEX_[A-Za-z0-9_]+)\s*=\s*"([^"]+)"')) {
            $defs[$m.Groups[1].Value] = $m.Groups[2].Value
        }
        $missingDefs = @()
        foreach ($n in $texNames) { if (-not ($defs.Values -contains $n)) { $missingDefs += $n } }
        if ($missingDefs.Count -gt 0) { Fail ($line + ': 缺少贴图常量定义 ' + ($missingDefs -join ' | ')) }

        # 1) tex(...) 实参文本（括号配平）
        $argText = ''
        $helperNames = @()
        foreach ($m in [regex]::Matches($src, 'tex\(')) {
            $i = $m.Index + $m.Length
            $depth = 1
            $startArg = $i
            while ($i -lt $src.Length -and $depth -gt 0) {
                $ch = $src[$i]
                if ($ch -eq '(') { $depth++ } elseif ($ch -eq ')') { $depth-- }
                $i++
            }
            $arg = $src.Substring($startArg, [Math]::Max(0, $i - 1 - $startArg))
            $argText += $arg + ' '
            foreach ($h in [regex]::Matches($arg, '([A-Za-z_][A-Za-z0-9_]*)\s*\(')) { $helperNames += $h.Groups[1].Value }
        }
        $used = @{}
        foreach ($m in [regex]::Matches($argText, '(TEX_[A-Za-z0-9_]+)')) { $used[$m.Groups[1].Value] = $true }
        # 2) 被 tex(...) 调用的字符串返回助手方法体（例如 qualityTex(frame)）
        foreach ($h in ($helperNames | Select-Object -Unique)) {
            $mm = [regex]::Match($src, 'private static String ' + [regex]::Escape($h) + '\([^)]*\)\s*\{(?<b>.*?)\n    \}', 'Singleline')
            if (-not $mm.Success) { continue }
            foreach ($m in [regex]::Matches($mm.Groups['b'].Value, '(TEX_[A-Za-z0-9_]+)')) { $used[$m.Groups[1].Value] = $true }
        }
        $unref = @()
        foreach ($k in $defs.Keys) {
            if ($used.ContainsKey($k)) { continue }
            if ($regOnly -contains $k) { continue }
            $unref += ($k + '(' + $defs[$k] + ')')
        }
        if ($unref.Count -gt 0) { Fail ($line + ': 贴图常量取不到绘制路径（tex(...) 实参或其字符串助手方法体内均未出现）' + ($unref -join ' | ')) }
        if ($src.IndexOf('chip_shop_GUI') -ge 0) { Fail ($line + ': 源码不得引用参考图 chip_shop_GUI') }

        $bad = @()
        foreach ($f in $forbidden) { if ($src.IndexOf($f) -ge 0) { $bad += $f } }
        if ($bad.Count -gt 0) { Fail ($line + ': 出现被禁的拖拽/双击标识符 ' + ($bad -join ' | ')) }
        $occur = ([regex]::Matches($src, [regex]::Escape('onConfirmPressed()'))).Count
        if ($occur -ne 2) { Fail ($line + ': onConfirmPressed() 出现次数应为 2（声明 + 确认分支各一次），实测 ' + $occur) }
        # 调用点位置：必须在 mouseClicked 内、且在「确认按钮命中」之后、「筹码格命中」之前
        $bodyMatch = [regex]::Match($src, 'public boolean mouseClicked\(double mouseX, double mouseY, int button\)\s*\{(?<b>.*?)\n    \}', 'Singleline')
        if (-not $bodyMatch.Success) {
            Fail ($line + ': 未找到 mouseClicked 方法体（无法校验调用点位置）')
        } else {
            $body = $bodyMatch.Groups['b'].Value
            $callIdx = $body.IndexOf('onConfirmPressed();')
            $confirmIdx = $body.IndexOf('CONFIRM_X')
            $chipIdx = $body.IndexOf('chipIndexAt(')
            if ($callIdx -lt 0 -or $confirmIdx -lt 0 -or $chipIdx -lt 0 -or $callIdx -lt $confirmIdx -or $callIdx -gt $chipIdx) {
                Fail ($line + ': onConfirmPressed() 调用点不在确认按钮分支内（call=' + $callIdx + ' confirm=' + $confirmIdx + ' chip=' + $chipIdx + '）')
            }
        }
        if ($src.IndexOf('buyRequestCount') -lt 0) { Fail ($line + ': 缺少只读探针 buyRequestCount') }
        Write-Out ('CHIPUI_INTERACT: 禁双击/拖拽标识符 ' + ($forbidden.Count - $bad.Count) + '/' + $forbidden.Count + ' 通过；onConfirmPressed 调用次数 ' + $occur)
    }

    # G5 文案
    $langDir = Join-Path $lineDir 'src/main/resources/assets/astral_dice/lang'
    if (-not (Test-Path $langDir)) {
        Fail ($line + ': 缺少 lang 目录')
    } else {
        $langFiles = Get-ChildItem -LiteralPath $langDir -Filter '*.json' -File
        if ($langFiles.Count -lt 3) { Fail ($line + ': lang 文件少于 3 个（实测 ' + $langFiles.Count + '）') }
        foreach ($lf in $langFiles) {
            $t = Get-Content -LiteralPath $lf.FullName -Raw
            $miss = @()
            foreach ($k in $langKeys) { if ($t.IndexOf(('"' + $k + '"')) -lt 0) { $miss += $k } }
            if ($miss.Count -gt 0) { Fail ($line + ': ' + $lf.Name + ' 缺文案键 ' + ($miss -join ' | ')) }
        }
        $langBad = ($fail | Where-Object { $_ -like ($line + ': *缺文案键*') }).Count
        Write-Out ('CHIPUI_LANG: ' + $langFiles.Count + ' 个语言文件，缺键文件数 ' + $langBad)
    }

    # G6 接线
    $menuTypes = Join-Path $lineDir 'src/main/java/com/merlinkitsune/astral_dice/screen/ModMenuTypes.java'
    if (-not (Test-Path $menuTypes)) { Fail ($line + ': 缺少 ModMenuTypes.java') }
    else {
        $mt = Get-Content -LiteralPath $menuTypes -Raw
        if ($mt.IndexOf('chip_shop') -lt 0) { Fail ($line + ': ModMenuTypes 未注册 chip_shop') }
        else { Write-Out 'CHIPUI_MENUTYPE: chip_shop 已注册' }
    }
    $menuPath = Join-Path $lineDir 'src/main/java/com/merlinkitsune/astral_dice/shop/ChipShopMenu.java'
    if (-not (Test-Path $menuPath)) { Fail ($line + ': 缺少 shop/ChipShopMenu.java') }
    else {
        $ms = Get-Content -LiteralPath $menuPath -Raw
        $slotCount = ([regex]::Matches($ms, [regex]::Escape('addSlot('))).Count
        if ($slotCount -ne 36) { Fail ($line + ': ChipShopMenu 玩家槽应为 36 个（9x3 + 快捷栏 9），实测 ' + $slotCount) }
        else { Write-Out 'CHIPUI_SLOTS: 36 个玩家槽' }
    }
    $bind = @()
    foreach ($f in (Get-ChildItem -LiteralPath (Join-Path $lineDir 'src/main/java') -Recurse -Filter '*.java' -File)) {
        foreach ($ln in (Get-Content -LiteralPath $f.FullName)) {
            $code = ($ln -split '//')[0]
            if ($code.IndexOf('CHIP_SHOP') -ge 0 -and $code.IndexOf('ChipShopScreen::new') -ge 0) { $bind += $f; break }
        }
    }
    if (-not $bind) { Fail ($line + ': 客户端未把 ChipShopScreen 绑到 chip_shop 菜单（需同一行同时出现 CHIP_SHOP 与 ChipShopScreen::new，且不得在注释里）') }
    else { Write-Out ('CHIPUI_BIND: ' + $bind[0].Name + ' 绑定 ChipShopScreen（同行含 CHIP_SHOP）') }

    # 探针类引用守门（既有能力，顺带提示）
    Write-Out ('CHIPUI_NEXT: pwsh -NoProfile -File scripts/test/mt_build.ps1 --version ' + $mtVersions[$key] + ' --timeout 300')
}

Write-Out ('CHIPUI_TOTAL_FAIL: ' + $fail.Count)
if ($fail.Count -eq 0) { Write-Out 'CHIPUI_RESULT: OK'; exit 0 }
Write-Out 'CHIPUI_RESULT: FAIL'; exit 1
