<#
筹码商店守门（静态判据 G1..G6 界面骨架 + G9..G27 业务逻辑；编译 G7 与资源守门 G8 另行执行）
用法（仓库根目录执行）：
    pwsh -NoProfile -File scripts/verify/verify_chip_shop_ui.ps1            # 两线全跑
    pwsh -NoProfile -File scripts/verify/verify_chip_shop_ui.ps1 neo        # 只查 neoforge-1.21.1
    pwsh -NoProfile -File scripts/verify/verify_chip_shop_ui.ps1 forge      # 只查 forge-1.20.1
退出码 0 = 全绿；1 = 有缺失/不一致。判据正本：docs/chip-shop-ui-spec.md §8。
说明：本脚本是「筹码商店」的判据（G1..G6 界面骨架批 + G9..G27 业务逻辑批，判据正本 = docs/chip-shop-logic-plan.md §9）。
      · G1..G6 = .86 界面骨架批的判据；
      · G9..G21 = 逻辑批的判据；G21b..G27 = 2026-10-12 复核后补强的语义锚定判据
        （越线字面 / shaped 配方撞车 / DataSlot 与拒绝码 / 星等来源 / 无折扣 / handleBuy 关店 / 命中面 / 概率表行序 / 价格公式）。
      **两线均已落地**（forge-1.20.1 的 T7 移植已完成，2026-10-12）⇒ 两线跑本脚本都应全绿，
      任一条 FAIL 即为回归（「forge 默认必然 FAIL 的 RED 侧」口径已作废）。
      **2026-10-14 判据刷新（3 处，均已取证为「判据字面过期」而非产品回归）**：
        ① G21 ModItems：改「整条注册语句锚定」（兼容两线各异的注册入口），不再写死 1.21.1 的 registerSimpleBlockItem 字面；
        ② G21b neo 侧越线字面：收窄为「1.20.1 形态的六参 use 声明头」（原字面会被本线合法的七参 useItemOn 续行误命中）；
        ③ G22 chip_shop 配方：改判 pattern GPG/HKH/HHH + key K = astral_dice:blank_chip（.112 起 blank_chip 由 S 位移到 K 位）。
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

# 读源码（文件不存在时返回空串，避免 $ErrorActionPreference='Stop' 下中断）
function Read-Src([string]$p) { if (Test-Path -LiteralPath $p) { return (Get-Content -LiteralPath $p -Raw) } return '' }

# 去掉 Java 注释（块注释 + 行注释；不碰字符串/字符字面量里的 // 与 /*）
# —— 必须去注释后再做「关键字存在性」判据，否则把代码注释掉同样能骗过守门（变异测试 M1 实证）
function Strip-Comments([string]$t) {
    if ([string]::IsNullOrEmpty($t)) { return '' }
    $sb = New-Object System.Text.StringBuilder
    $i = 0; $n = $t.Length
    $inStr = $false; $inChar = $false; $inLine = $false; $inBlock = $false
    while ($i -lt $n) {
        $c = $t[$i]
        $d = if ($i + 1 -lt $n) { $t[$i + 1] } else { [char]0 }
        if ($inLine) { if ($c -eq [char]10) { $inLine = $false; [void]$sb.Append($c) }; $i++; continue }
        if ($inBlock) { if ($c -eq '*' -and $d -eq '/') { $inBlock = $false; $i += 2; continue }; if ($c -eq [char]10) { [void]$sb.Append($c) }; $i++; continue }
        if ($inStr) {
            [void]$sb.Append($c)
            if ($c -eq '\') { if ($i + 1 -lt $n) { [void]$sb.Append($t[$i + 1]) }; $i += 2; continue }
            if ($c -eq '"') { $inStr = $false }
            $i++; continue
        }
        if ($inChar) {
            [void]$sb.Append($c)
            if ($c -eq '\') { if ($i + 1 -lt $n) { [void]$sb.Append($t[$i + 1]) }; $i += 2; continue }
            if ($c -eq "'") { $inChar = $false }
            $i++; continue
        }
        if ($c -eq '/' -and $d -eq '/') { $inLine = $true; $i += 2; continue }
        if ($c -eq '/' -and $d -eq '*') { $inBlock = $true; $i += 2; continue }
        if ($c -eq '"') { $inStr = $true; [void]$sb.Append($c); $i++; continue }
        if ($c -eq "'") { $inChar = $true; [void]$sb.Append($c); $i++; continue }
        [void]$sb.Append($c); $i++
    }
    return $sb.ToString()
}

# Java 源码（已去注释）—— G9..G21 的关键字判据一律用它
function Code-Src([string]$p) { return (Strip-Comments (Read-Src $p)) }

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
    'CHIP_ICON_SIZE = 24', 'CHIP_ICON_OFF = 2',
    'LABEL_W = 42', 'LABEL_H = 15', 'LABEL_Y = 61', 'LABEL_X = {1, 44, 87, 130, 172}',
    'PANEL_X = 56', 'PANEL_Y = 89', 'PANEL_W = 172', 'PANEL_H = 86',
    'SLOT_X0 = 61', 'SLOT_Y0 = 94', 'SLOT_PITCH = 18', 'SLOT_COLS = 9', 'SLOT_ROWS = 3', 'HOTBAR_Y = 152'
)

$langKeys = @(
    'gui.astral_dice.chip_shop',
    'gui.astral_dice.chip_shop.cost',
    'gui.astral_dice.chip_shop.refresh_counter',
    'gui.astral_dice.chip_shop.empty',
    # 逻辑批新增（计划表 §8）：方块名 + 5 条提示
    'block.astral_dice.chip_shop',
    'msg.astral_dice.chip_shop_no_coin',
    'msg.astral_dice.chip_shop_no_refresh',
    'msg.astral_dice.chip_shop_bought',
    'msg.astral_dice.chip_shop_owned_all',
    'msg.astral_dice.chip_shop_inventory_full'
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
        $src = Code-Src $screenPath
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
            $t = Read-Src $lf.FullName
            $miss = @()
            foreach ($k in $langKeys) { if ($t.IndexOf(('"' + $k + '"')) -lt 0) { $miss += $k } }
            if ($miss.Count -gt 0) { Fail ($line + ': ' + $lf.Name + ' 缺文案键 ' + ($miss -join ' | ')) }
        }
        $langBad = ($fail | Where-Object { $_ -like ($line + ': *缺文案键*') }).Count
        Write-Out ('CHIPUI_LANG: ' + $langFiles.Count + ' 个语言文件，缺键文件数 ' + $langBad)

        # ---- G5b 三语键集对称 + Java 引用键不得悬空 ----
        # 复核发现：只查「必备键是否存在」会被「三语各删一条、另加一条无关键」绕过；本组补上键集对称与悬空键两道。
        $langSets = @{}
        foreach ($lf in $langFiles) {
            try { $obj = (Read-Src $lf.FullName) | ConvertFrom-Json } catch { Fail ($line + ': ' + $lf.Name + ' 不是合法 JSON'); continue }
            $kinds = @{}; foreach ($pr in $obj.PSObject.Properties) { $kinds[$pr.Name] = $true }
            $langSets[$lf.Name] = $kinds
        }
        $langNames = @($langSets.Keys)
        if ($langNames.Count -ge 2) {
            $base = $langSets[$langNames[0]]
            foreach ($n in $langNames[1..($langNames.Count - 1)]) {
                $diff = @()
                foreach ($k in $base.Keys) { if (-not $langSets[$n].ContainsKey($k)) { $diff += ('缺 ' + $k) } }
                foreach ($k in $langSets[$n].Keys) { if (-not $base.ContainsKey($k)) { $diff += ('多 ' + $k) } }
                if ($diff.Count -gt 0) { Fail ($line + ': 语言键集不对称 ' + $langNames[0] + ' vs ' + $n + '：' + ($diff -join ' / ')) }
            }
        }
        $usedKeys = @{}
        foreach ($f in (Get-ChildItem -LiteralPath (Join-Path $lineDir 'src/main/java') -Recurse -Filter '*.java' -File)) {
            foreach ($m in [regex]::Matches((Code-Src $f.FullName), '"(?:gui|block|msg)\.astral_dice\.chip_shop[a-z0-9_.]*"')) {
                $usedKeys[$m.Value.Trim('"')] = $f.Name
            }
        }
        $dangling = @()
        foreach ($k in $usedKeys.Keys) {
            foreach ($lf in $langFiles) { if (-not $langSets[$lf.Name].ContainsKey($k)) { $dangling += ($lf.Name + ' 缺 ' + $k) } }
        }
        $dangling = @($dangling | Select-Object -Unique)
        if ($dangling.Count -gt 0) { Fail ($line + ': 悬空语言键（Java 引用但 lang 无）：' + ($dangling -join ' / ')) }
        Write-Out ('CHIPUI_LANGKEYS: 三语键集对称（' + $langSets[$langNames[0]].Count + ' 键）＋ Java 引用 ' + $usedKeys.Count + ' 个 chip_shop 键；悬空 ' + $dangling.Count + ' 项')
    }

    # G6 接线
    $menuTypes = Join-Path $lineDir 'src/main/java/com/merlinkitsune/astral_dice/screen/ModMenuTypes.java'
    if (-not (Test-Path $menuTypes)) { Fail ($line + ': 缺少 ModMenuTypes.java') }
    else {
        $mt = Code-Src $menuTypes
        if ($mt.IndexOf('chip_shop') -lt 0) { Fail ($line + ': ModMenuTypes 未注册 chip_shop') }
        else { Write-Out 'CHIPUI_MENUTYPE: chip_shop 已注册' }
    }
    $menuPath = Join-Path $lineDir 'src/main/java/com/merlinkitsune/astral_dice/shop/ChipShopMenu.java'
    if (-not (Test-Path $menuPath)) { Fail ($line + ': 缺少 shop/ChipShopMenu.java') }
    else {
        $ms = Code-Src $menuPath
        # 只数玩家槽（3 个筹码隐藏槽是 addSlot(new OfferSlot(...))，不计入）
        $slotCount = ([regex]::Matches($ms, [regex]::Escape('addSlot(new Slot(playerInventory,'))).Count
        if ($slotCount -ne 36) { Fail ($line + ': ChipShopMenu 玩家槽应为 36 个（9x3 + 快捷栏 9），实测 ' + $slotCount) }
        else { Write-Out 'CHIPUI_SLOTS: 36 个玩家槽' }
    }
    $bind = @()
    foreach ($f in (Get-ChildItem -LiteralPath (Join-Path $lineDir 'src/main/java') -Recurse -Filter '*.java' -File)) {
        foreach ($ln in ((Code-Src $f.FullName) -split "`n")) {
            if ($ln.IndexOf('CHIP_SHOP') -ge 0 -and $ln.IndexOf('ChipShopScreen::new') -ge 0) { $bind += $f; break }
        }
    }
    if (-not $bind) { Fail ($line + ': 客户端未把 ChipShopScreen 绑到 chip_shop 菜单（需同一行同时出现 CHIP_SHOP 与 ChipShopScreen::new，且不得在注释里）') }
    else { Write-Out ('CHIPUI_BIND: ' + $bind[0].Name + ' 绑定 ChipShopScreen（同行含 CHIP_SHOP）') }

    # ================= G9..G21 业务逻辑（判据正本 = docs/chip-shop-logic-plan.md §9） =================
    $javaDir = Join-Path $lineDir 'src/main/java/com/merlinkitsune/astral_dice'
    # ⚠️ 逻辑判据一律读「去注释」源码（Code-Src）：把语句注释掉必须同样判红（变异测试 M1 实证）；
    #    资源 JSON / 目录检查才读原文（Read-Src）。本文件内**不得**再出现裸 -Raw 做关键字判据。
    $catSrc = Code-Src (Join-Path $javaDir 'shop/ChipShopCatalog.java')
    $rollerSrc = Code-Src (Join-Path $javaDir 'shop/ChipShopRoller.java')
    $stateSrc = Code-Src (Join-Path $javaDir 'shop/ChipShopState.java')
    $openerSrc = Code-Src (Join-Path $javaDir 'shop/ChipShopOpener.java')
    $tickerSrc = Code-Src (Join-Path $javaDir 'shop/ChipShopRegenTicker.java')
    $csSrc = Code-Src (Join-Path $javaDir 'client/ChipShopClientState.java')
    $src = Code-Src $screenPath
    $ms = Code-Src $menuPath

    # ---- G9 池 61 条 + 图标 61/61（与计划表 §1 逐行对账 + 素材逐字节一致 + 32x32） ----
    $planPath = Join-Path $root 'docs/chip-shop-logic-plan.md'
    $planIconDir = Join-Path $root 'images/shop/unbord_chips'
    $planRows = @()
    if (-not (Test-Path -LiteralPath $planPath)) {
        Fail ($line + ': 缺少判据正本 docs/chip-shop-logic-plan.md')
    } else {
        # 图标列闭引号后允许跟补充说明（例：`能量回收.png`（别名：…））；品质列形如「稀有(quality_1)」，取括号前中文
        $rowPattern = '^\|\s*`([a-z0-9_]+)`\s*\|([^|]*)\|\s*`([^`]+\.png)`[^|]*\|([^|]*)\|([^|]*)\|([^|]*)\|'
        foreach ($pl in (Get-Content -LiteralPath $planPath)) {
            $pm = [regex]::Match($pl, $rowPattern)
            if ($pm.Success) {
                $planRows += [pscustomobject]@{
                    id = $pm.Groups[1].Value; name = $pm.Groups[2].Value.Trim()
                    icon = $pm.Groups[3].Value
                    quality = ($pm.Groups[4].Value -split '\(')[0].Trim()
                    school = $pm.Groups[5].Value.Trim(); cat = $pm.Groups[6].Value.Trim()
                }
            }
        }
    }
    $catRows = @()
    foreach ($cm in [regex]::Matches($catSrc, 'e\("([a-z0-9_]+)",\s*TIER_([A-Z]+),\s*School\.([A-Z]+),\s*Category\.([A-Z]+)\)')) {
        $catRows += [pscustomobject]@{ id = $cm.Groups[1].Value; tier = $cm.Groups[2].Value; school = $cm.Groups[3].Value; cat = $cm.Groups[4].Value }
    }
    if ($planRows.Count -ne 61) { Fail ($line + ': 计划表 §1 筹码行应为 61，实测 ' + $planRows.Count) }
    if ($catRows.Count -ne 61) { Fail ($line + ': ChipShopCatalog 池条目应为 61，实测 ' + $catRows.Count) }
    # 计划表品质列写中文（稀有/史诗/传奇）；池里是 TIER_RARE/EPIC/LEGENDARY 三档码 —— 两套映射都归到数字码
    $qualityMap = @{ '稀有' = 1; '史诗' = 2; '传奇' = 3 }
    $tierCodeMap = @{ 'RARE' = 1; 'EPIC' = 2; 'LEGENDARY' = 3 }
    $schoolMap = @{ '星光' = 'STARLIGHT'; '治愈' = 'HEALING'; '标记' = 'MARK'; '充能' = 'CHARGE'; '泛用' = 'GENERIC' }
    $catMap = @{ '攻击力' = 'ATTACK'; '强化' = 'REINFORCE'; '辅助' = 'SUPPORT'; '治疗' = 'HEAL'; '经济' = 'ECONOMY' }
    $cmp = 0; $mismatch = @()
    for ($i = 0; $i -lt [Math]::Min($planRows.Count, $catRows.Count); $i++) {
        $pr = $planRows[$i]; $cr = $catRows[$i]; $eq = $true
        if ($pr.id -ne $cr.id) { $eq = $false; $mismatch += ('行' + ($i + 1) + ' id：计划表 ' + $pr.id + ' vs 池 ' + $cr.id) }
        $wantTier = $qualityMap[$pr.quality]; $gotTier = $tierCodeMap[$cr.tier]
        if (-not $wantTier) { $eq = $false; $mismatch += ($pr.id + ' 计划表品质未识别：' + $pr.quality) }
        elseif (-not $gotTier) { $eq = $false; $mismatch += ($pr.id + ' 池品质码未识别：' + $cr.tier) }
        elseif ($wantTier -ne $gotTier) { $eq = $false; $mismatch += ($pr.id + ' 品质：计划表 ' + $wantTier + ' vs 池 ' + $gotTier) }
        $wantSchool = $schoolMap[$pr.school]
        if (-not $wantSchool) { $eq = $false; $mismatch += ($pr.id + ' 计划表流派未识别：' + $pr.school) }
        elseif ($wantSchool -ne $cr.school) { $eq = $false; $mismatch += ($pr.id + ' 流派：计划表 ' + $wantSchool + ' vs 池 ' + $cr.school) }
        $wantCat = $catMap[$pr.cat]
        if (-not $wantCat) { $eq = $false; $mismatch += ($pr.id + ' 计划表类别未识别：' + $pr.cat) }
        elseif ($wantCat -ne $cr.cat) { $eq = $false; $mismatch += ($pr.id + ' 类别：计划表 ' + $wantCat + ' vs 池 ' + $cr.cat) }
        if ($eq) { $cmp++ }
    }
    if ($mismatch.Count -gt 0) { Fail ($line + ': 池与计划表 §1 不一致 ' + ($mismatch -join ' | ')) }
    $chipTexDir = Join-Path $lineDir 'src/main/resources/assets/astral_dice/textures/gui/chip_shop/chips'
    $iconOk = 0; $iconBad = @()
    if (-not (Test-Path -LiteralPath $chipTexDir)) {
        Fail ($line + ': 缺少筹码图标目录 textures/gui/chip_shop/chips')
    } else {
        $iconFiles = @(Get-ChildItem -LiteralPath $chipTexDir -Filter '*.png' -File)
        if ($iconFiles.Count -ne 61) { Fail ($line + ': 筹码图标应为 61 张，实测 ' + $iconFiles.Count) }
        $planIds = @($planRows | ForEach-Object { $_.id })
        $extra = @($iconFiles | Where-Object { $planIds -notcontains $_.BaseName })
        if ($extra.Count -gt 0) { Fail ($line + ': 图标目录含计划表外文件 ' + (($extra | ForEach-Object { $_.Name }) -join ' | ')) }
        foreach ($pr in $planRows) {
            $dst = Join-Path $chipTexDir ($pr.id + '.png')
            $srcIcon = Join-Path $planIconDir $pr.icon
            if (-not (Test-Path -LiteralPath $dst)) { $iconBad += ($pr.id + '：图标缺失'); continue }
            if (-not (Test-Path -LiteralPath $srcIcon)) { $iconBad += ($pr.id + '：源素材缺失 ' + $pr.icon); continue }
            if ((Get-FileHash -Algorithm SHA256 -LiteralPath $srcIcon).Hash -ne (Get-FileHash -Algorithm SHA256 -LiteralPath $dst).Hash) { $iconBad += ($pr.id + '：与 ' + $pr.icon + ' 不一致'); continue }
            $bb = [System.IO.File]::ReadAllBytes($dst)
            $iw = ([int]$bb[16]) * 16777216 + ([int]$bb[17]) * 65536 + ([int]$bb[18]) * 256 + ([int]$bb[19])
            $ih = ([int]$bb[20]) * 16777216 + ([int]$bb[21]) * 65536 + ([int]$bb[22]) * 256 + ([int]$bb[23])
            if ($iw -ne 32 -or $ih -ne 32) { $iconBad += ($pr.id + '：尺寸 ' + $iw + 'x' + $ih + '（blit 源尺寸固定 32x32）'); continue }
            $iconOk++
        }
        if ($iconBad.Count -gt 0) { Fail ($line + ': 筹码图标不合格 ' + ($iconBad -join ' | ')) }
    }
    Write-Out ('CHIPUI_G9: 池 ' + $catRows.Count + ' 条（逐行一致 ' + $cmp + '/' + $planRows.Count + '）；图标 ' + $iconOk + '/61 逐字节一致 + 32x32')

    # ---- G10 类别 5 覆盖 / 每枚恰一类 / 品质码 ⊆ {1,2,3} ----
    $catBody = [regex]::Match($catSrc, 'enum Category\s*\{(?<b>.*?)\n    \}', 'Singleline')
    $catNames = @()
    if ($catBody.Success) { foreach ($cm in [regex]::Matches($catBody.Groups['b'].Value, '^\s*([A-Z][A-Z_]*)\s*[,;]?\s*$', 'Multiline')) { $catNames += $cm.Groups[1].Value } }
    if ($catNames.Count -ne 5) { Fail ($line + ': Category 枚举应为 5 项，实测 ' + $catNames.Count + '（' + ($catNames -join ',') + '）') }
    $usedCats = @($catRows | ForEach-Object { $_.cat } | Select-Object -Unique)
    if ($usedCats.Count -ne 5) { Fail ($line + ': 池覆盖类别应为 5 个，实测 ' + $usedCats.Count + '（' + ($usedCats -join ',') + '）') }
    $nCatRef = ([regex]::Matches($catSrc, 'Category\.[A-Z]')).Count
    $nSchoolRef = ([regex]::Matches($catSrc, 'School\.[A-Z]')).Count
    if ($nCatRef -ne 61 -or $nSchoolRef -ne 61) { Fail ($line + ': 池内 School./Category. 引用应各 61 次（每枚恰一类一流派），实测 ' + $nSchoolRef + '/' + $nCatRef) }
    foreach ($tc in @(@('TIER_RARE', 1), @('TIER_EPIC', 2), @('TIER_LEGENDARY', 3))) {
        if ($catSrc -notmatch ([regex]::Escape($tc[0]) + '\s*=\s*' + $tc[1] + '\s*;')) { Fail ($line + ': 品质码常量不符 ' + $tc[0] + ' = ' + $tc[1]) }
    }
    $badTier = @($catRows | Where-Object { -not $tierCodeMap.ContainsKey($_.tier) })
    if ($badTier.Count -gt 0) { Fail ($line + ': 池内品质码超出 {1,2,3}：' + (($badTier | ForEach-Object { $_.id + '/' + $_.tier }) -join ' | ')) }
    Write-Out ('CHIPUI_G10: 类别 ' + $catNames.Count + ' 项、池覆盖 ' + $usedCats.Count + ' 项；品质码 ⊆ {1,2,3}')

    $logicFailStart = $fail.Count

    # ---- G11 概率表逐字 + slot 衰减 ----
    $probMiss = @()
    foreach ($t in @('{0.60, 0.37, 0.03}', '{0.40, 0.45, 0.15}', '{0.25, 0.50, 0.25}')) { if ($catSrc.IndexOf($t) -lt 0) { $probMiss += $t } }
    $n6037 = ([regex]::Matches($catSrc, [regex]::Escape('{0.60, 0.37, 0.03}'))).Count
    if ($n6037 -ne 2) { $probMiss += ('0★/1★ 同表（60/37/3 应出现 2 份，实测 ' + $n6037 + '）') }
    foreach ($t in @('SCHOOL_ALPHA = 0.60', 'CATEGORY_ALPHA = 2.0', 'SLOT_DECAY = 0.25')) { if ($catSrc.IndexOf($t) -lt 0) { $probMiss += $t } }
    if ($probMiss.Count -gt 0) { Fail ($line + ': 概率/权重常量缺失或走样 ' + ($probMiss -join ' | ')) }
    if ($rollerSrc.IndexOf('ChipShopCatalog.QUALITY_PROB[star]') -lt 0) { Fail ($line + ': 抽取器未按星级取表 QUALITY_PROB[star]') }
    if ($rollerSrc.IndexOf('Math.max(0, Math.min(3,') -lt 0) { Fail ($line + ': 星级未夹在 0..3') }
    if ($catSrc.IndexOf('(CATEGORY_ALPHA - 1.0) * SLOT_DECAY + 1.0') -lt 0) { Fail ($line + ': slot1/2 衰减式不符 (factor-1)*SLOT_DECAY+1') }
    if ($catSrc.IndexOf('return CATEGORY_ALPHA;') -lt 0) { Fail ($line + ': slot0 全强度类别偏置缺失') }

    # ---- G12 去重与缺口顺序（池不够时 append 留空槽，不得覆盖） ----
    foreach ($t in @('if (pool.isEmpty()) {', 'picked.add(chosen);', 'pool.remove(chosen);')) {
        if ($rollerSrc.IndexOf($t) -lt 0) { Fail ($line + ': 抽取器缺「去重/缺口」语句 ' + $t) }
    }
    if ($rollerSrc.IndexOf('picked.set(') -ge 0) { Fail ($line + ': 抽取器不得 set 覆盖已抽中的槽（必须 append 保缺口顺序）') }

    # ---- G13 已拥有过滤 + 两种拒绝进入 ----
    foreach ($t in @('availablePool(', 'ownedChipItems(')) { if ($rollerSrc.IndexOf($t) -lt 0) { Fail ($line + ': 抽取器缺已拥有过滤 ' + $t) } }
    if ($stateSrc.IndexOf('needsReroll(Player player)') -lt 0) { Fail ($line + ': ChipShopState 缺 needsReroll') }
    if ($stateSrc.IndexOf('ChipShopRoller.isOwned(player, entry)') -lt 0) { Fail ($line + ': needsReroll 未按已拥有判定') }
    foreach ($t in @('ChipShopRoller.ownsEverything(player)', 'msg.astral_dice.chip_shop_owned_all')) { if ($openerSrc.IndexOf($t) -lt 0) { Fail ($line + ': 开店入口缺「已集齐」拒绝 ' + $t) } }
    foreach ($t in @('hasNoRoom(player)', 'msg.astral_dice.chip_shop_inventory_full')) { if ($openerSrc.IndexOf($t) -lt 0) { Fail ($line + ': 开店入口缺「物品栏已满」拒绝 ' + $t) } }
    # G13b 「物品栏已满」的**语义**：遍历 36 格，遇空格即返 false，扫完才返 true
    # （复核发现：只查标识符存在时，把 isEmpty() 取反即可让守门放行）
    $hnr = [regex]::Match($openerSrc, 'private static boolean hasNoRoom\(Player player\)\s*\{(?<b>.*?)\n    \}', 'Singleline')
    if (-not $hnr.Success) { Fail ($line + ': 未找到 hasNoRoom 方法体') }
    else {
        $hb = $hnr.Groups['b'].Value
        if ($hb -notmatch 'for \(int i = 0; i < INVENTORY_SLOTS; i\+\+\)') { Fail ($line + ': hasNoRoom 未遍历物品栏 36 格') }
        if ($hb -notmatch 'if \(player\.getInventory\(\)\.getItem\(i\)\.isEmpty\(\)\)\s*\{\s*return false;') { Fail ($line + ': hasNoRoom 的物品栏已满判定被改（须「遇空格即返 false」）') }
        if ($hb -notmatch 'return true;') { Fail ($line + ': hasNoRoom 全满时应返 true') }
    }
    $iOwn = $openerSrc.IndexOf('ownsEverything(player)'); $iRoom = $openerSrc.IndexOf('hasNoRoom(player)')
    $iEns = $openerSrc.IndexOf('ensureOffers(player)'); $iOpen = $openerSrc.IndexOf('openMenu(')
    if (-not ($iOwn -ge 0 -and $iRoom -gt $iOwn -and $iEns -gt $iRoom -and $iOpen -gt $iEns)) {
        Fail ($line + ': 开店顺序应为「已集齐 → 物品栏满 → 补抽 → openMenu」（实测 own=' + $iOwn + ' room=' + $iRoom + ' ens=' + $iEns + ' open=' + $iOpen + '）')
    }

    # ---- G14 无记录 / 异类别不得有偏置 ----
    foreach ($t in @('if (lastPick == null || candidate == null || candidate != lastPick) {', 'return 1.0;')) {
        if ($catSrc.IndexOf($t) -lt 0) { Fail ($line + ': categoryWeight 缺「无记录无偏置」语句 ' + $t) }
    }

    # ---- G15 两处命中（筹码格 ∪ 标签框）都只 select ----
    foreach ($t in @('LABEL_X[cell]', 'CELL_X[cell]')) { if ($src.IndexOf($t) -lt 0) { Fail ($line + ': 筹码格命中未扩到标签框（缺 ' + $t + '）') } }
    if ($bodyMatch.Success) {
        $mb = $bodyMatch.Groups['b'].Value
        $ci = $mb.IndexOf('chipIndexAt(')
        if ($ci -lt 0) { Fail ($line + ': mouseClicked 内未调用 chipIndexAt') }
        else {
            $after = $mb.Substring($ci)
            if ($after.IndexOf('sendToServer') -ge 0) { Fail ($line + ': 筹码格命中分支不得发包（只允许 select）') }
            if ($after.IndexOf('ChipShopClientState.select(') -lt 0) { Fail ($line + ': 筹码格命中分支缺 ChipShopClientState.select(...)') }
            # 条件表达式逐字锚定（复核发现：只查 token 顺序时，if (chip >= 0) 改成 chip = (chip == 0) ? -1 : chip; + if (chip > 0) 可让第 0 格永远选不中而守门放行）
            if ($mb.IndexOf('int chip = chipIndexAt(mx, my);') -lt 0) { Fail ($line + ': 筹码格命中未按 chipIndexAt(mx, my) 取索引') }
            if ($mb.IndexOf('if (chip >= 0) {') -lt 0) { Fail ($line + ': 筹码格命中判定被改（须逐字为 if (chip >= 0) {）') }
        }
    }

    # ---- G16 拒绝红闪 30t + 购买/刷新入口唯一 ----
    if ($csSrc.IndexOf('DENY_BLINK_TICKS = 30') -lt 0) { Fail ($line + ': 拒绝红闪应为 30 tick') }
    if ($csSrc.IndexOf('((denyTicks - 1) / 5) % 2 == 1') -lt 0) { Fail ($line + ': 红闪节奏应为 (denyTicks-1)/5%2==1（每 5 tick 翻转）') }
    $buySends = ([regex]::Matches($src, [regex]::Escape('new ChipShopBuyPayload('))).Count
    if ($buySends -ne 1) { Fail ($line + ': 购买入口必须唯一（new ChipShopBuyPayload 应恰 1 处），实测 ' + $buySends) }
    $refreshSends = ([regex]::Matches($src, [regex]::Escape('new ChipShopRefreshPayload('))).Count
    if ($refreshSends -ne 1) { Fail ($line + ': 刷新入口必须唯一（new ChipShopRefreshPayload 应恰 1 处），实测 ' + $refreshSends) }
    $cm2 = [regex]::Match($src, 'private void onConfirmPressed\(\)\s*\{(?<b>.*?)\n    \}', 'Singleline')
    if (-not $cm2.Success) { Fail ($line + ': 未找到 onConfirmPressed 方法体') }
    elseif ($cm2.Groups['b'].Value.IndexOf('new ChipShopBuyPayload(') -lt 0) { Fail ($line + ': onConfirmPressed 未发购买包') }
    if ($cm2.Success) {
        $cb = $cm2.Groups['b'].Value
        if ($cb.IndexOf('int chip = ChipShopClientState.selectedIndex();') -lt 0) { Fail ($line + ': 确认按钮未按当前选中索引取筹码') }
        # 守卫逐字锚定（复核发现：把 chip < 0 || offer(chip).isEmpty() 削成 chip < 0，空候选位也会发包）
        if ($cb.IndexOf('chip < 0 || ChipShopClientState.offer(chip).isEmpty()') -lt 0) { Fail ($line + ': 确认按钮缺「未选中或空候选即不发包」守卫（须逐字含 chip < 0 || ChipShopClientState.offer(chip).isEmpty()）') }
    }
    $rm2 = [regex]::Match($src, 'private void onRefreshPressed\(\)\s*\{(?<b>.*?)\n    \}', 'Singleline')
    if (-not $rm2.Success) { Fail ($line + ': 未找到 onRefreshPressed 方法体') }
    else {
        $rb = $rm2.Groups['b'].Value
        $iDenyR = $rb.IndexOf('notifyDeny'); $iSendR = $rb.IndexOf('new ChipShopRefreshPayload(')
        if ($iDenyR -lt 0) { Fail ($line + ': 刷新次数为 0 时应只红闪（未调用 notifyDeny）') }
        if ($iSendR -lt 0) { Fail ($line + ': onRefreshPressed 未发刷新包') }
        elseif ($iDenyR -ge 0 -and $iDenyR -lt $iSendR) {
            if ($rb.Substring($iDenyR, $iSendR - $iDenyR).IndexOf('return;') -lt 0) { Fail ($line + ': 刷新次数为 0 的拒绝分支缺 return（会继续发包）') }
        }
    }

    # ---- G16b 隐藏候选槽：不可放入 / 不可拿走 / 单格 1 枚（改 mayPickup=true 必须判红） ----
    $osm = [regex]::Match($ms, 'private static class OfferSlot extends Slot\s*\{(?<b>.*?)\n    \}', 'Singleline')
    if (-not $osm.Success) { Fail ($line + ': 未找到隐藏候选槽 OfferSlot 类') }
    else {
        $ob = $osm.Groups['b'].Value
        if ($ob -notmatch 'public boolean mayPlace\([^)]*\)\s*\{\s*return false;') { Fail ($line + ': 隐藏槽 mustmayPlace=false（候选不得被放入）') }
        if ($ob -notmatch 'public boolean mayPickup\([^)]*\)\s*\{\s*return false;') { Fail ($line + ': 隐藏槽 must mayPickup=false（候选不得被拿走）') }
        if ($ob -notmatch 'getMaxStackSize\(\)\s*\{\s*return 1;') { Fail ($line + ': 隐藏槽 getMaxStackSize 应为 1') }
    }

    # ---- G17 价格公式 + 边界（0→10 / 1→15 / 18→100 / 30→100） ----
    foreach ($t in @('PRICE_INITIAL = 10', 'PRICE_STEP = 5', 'PRICE_MAX = 100', 'Math.min(PRICE_MAX, PRICE_INITIAL + PRICE_STEP * count)', 'Math.max(0, purchasedCount)')) {
        if ($catSrc.IndexOf($t) -lt 0) { Fail ($line + ': 价格公式/常量不符 ' + $t) }
    }
    $priceCurve = @()
    foreach ($n in @(0, 1, 18, 30)) { $priceCurve += [Math]::Min(100, 10 + 5 * [Math]::Max(0, $n)) }
    if (($priceCurve -join ',') -ne '10,15,100,100') { Fail ($line + ': 价格曲线边界应为 0→10 / 1→15 / 18→100 / 30→100，实测 ' + ($priceCurve -join ',')) }
    # G17b 三格同价：价格只能来自状态（purchasedCount），菜单不得自行算价
    $priceCalls = ([regex]::Matches($stateSrc, [regex]::Escape('ChipShopCatalog.priceOf(purchasedCount)'))).Count
    if ($priceCalls -ne 1) { Fail ($line + ': 价格唯一来源应为 ChipShopState.price() 内的 ChipShopCatalog.priceOf(purchasedCount)（实测 ' + $priceCalls + ' 处）') }
    if ($ms.IndexOf('priceOf(') -ge 0) { Fail ($line + ': 菜单不得自行算价（三格同价只能来自状态）') }
    Write-Out ('CHIPUI_G17: 价格曲线 0→' + $priceCurve[0] + ' / 1→' + $priceCurve[1] + ' / 18→' + $priceCurve[2] + ' / 30→' + $priceCurve[3])

    # ---- G18 刷新常量 3/5/36000 + 首次消耗才起算 + 到上限不顺延 ----
    foreach ($t in @('REFRESH_INITIAL = 3', 'REFRESH_MAX = 5', 'REFRESH_REGEN_TICKS = 36000')) { if ($catSrc.IndexOf($t) -lt 0) { Fail ($line + ': 刷新常量不符 ' + $t) } }
    if ($ms.IndexOf('state.regenAnchor() == 0L ? now + ChipShopCatalog.REFRESH_REGEN_TICKS : state.regenAnchor()') -lt 0) { Fail ($line + ': 刷新锚点未按「首次消耗才起算」设置') }
    if ($ms.IndexOf('state.refreshesLeft() <= 0') -lt 0) { Fail ($line + ': 服务端未拦「刷新次数为 0」') }
    foreach ($t in @('state.regenAnchor()', 'ChipShopCatalog.REFRESH_MAX', 'ChipShopCatalog.REFRESH_REGEN_TICKS', 'continue;')) { if ($tickerSrc.IndexOf($t) -lt 0) { Fail ($line + ': 刷新恢复 ticker 缺 ' + $t) } }
    # G18b withRefreshes 上限钳制（复核发现：Math.min→Math.max 可让刷新次数超过上限而守门放行）
    $wr = [regex]::Match($stateSrc, 'public ChipShopState withRefreshes\(int left, long anchor\)\s*\{(?<b>.*?)\n    \}', 'Singleline')
    if (-not $wr.Success) { Fail ($line + ': 未找到 withRefreshes 方法体') }
    else {
        $wb = $wr.Groups['b'].Value
        if ($wb -notmatch 'Math\.max\(0,\s*Math\.min\(ChipShopCatalog\.REFRESH_MAX,\s*left\)\)') { Fail ($line + ': 刷新次数未夹在 0..REFRESH_MAX（须 Math.max(0, Math.min(ChipShopCatalog.REFRESH_MAX, left))）') }
        $nMax = ([regex]::Matches($wb, 'Math\.max\(')).Count
        if ($nMax -ne 1) { Fail ($line + ': withRefreshes 内 Math.max 应恰 1 处（实测 ' + $nMax + '）') }
    }
    $iCap = $tickerSrc.IndexOf('ChipShopCatalog.REFRESH_MAX'); $iStore = $tickerSrc.IndexOf('ChipShopState.store(')
    if ($iCap -lt 0 -or $iStore -lt 0 -or $iCap -gt $iStore) { Fail ($line + ': ticker 未在到上限时提前跳过（锚点会被顺延）') }

    # ---- G19 菜单构造不掷骰；重抽只允许 3 处（购买 / 刷新 / 进店补抽） ----
    $rollInMenu = ([regex]::Matches($ms, [regex]::Escape('ChipShopRoller.roll('))).Count
    if ($rollInMenu -ne 2) { Fail ($line + ': ChipShopMenu 内重抽调用应 2 处（购买 + 刷新），实测 ' + $rollInMenu) }
    $rollInOpener = ([regex]::Matches($openerSrc, [regex]::Escape('ChipShopRoller.roll('))).Count
    if ($rollInOpener -ne 1) { Fail ($line + ': ChipShopOpener 内进店补抽应 1 处，实测 ' + $rollInOpener) }
    $rollFiles = @()
    foreach ($f in (Get-ChildItem -LiteralPath (Join-Path $lineDir 'src/main/java') -Recurse -Filter '*.java' -File)) {
        if ((Code-Src $f.FullName).IndexOf('ChipShopRoller.roll(') -ge 0) { $rollFiles += $f.Name }
    }
    $extraRoll = @($rollFiles | Where-Object { $_ -ne 'ChipShopMenu.java' -and $_ -ne 'ChipShopOpener.java' })
    if ($extraRoll.Count -gt 0) { Fail ($line + ': 出现第三个重抽入口 ' + ($extraRoll -join ' | ')) }
    $iCtor = $ms.IndexOf('public ChipShopMenu('); $iBuyM = $ms.IndexOf('public void handleBuy(')
    if ($iCtor -lt 0 -or $iBuyM -le $iCtor) { Fail ($line + ': 无法定位菜单构造器 / handleBuy（' + $iCtor + '/' + $iBuyM + '）') }
    else {
        $ctor = $ms.Substring($iCtor, $iBuyM - $iCtor)
        if ($ctor.IndexOf('ChipShopRoller.roll(') -ge 0 -or $ctor.IndexOf('withOffers(') -ge 0) { Fail ($line + ': 菜单构造器不得掷骰/写候选（唯一补抽入口在 ChipShopOpener.ensureOffers）') }
    }

    # ---- G20 closeContainer 与扣款同分支 ----
    $closeCount = ([regex]::Matches($ms, [regex]::Escape('.closeContainer();'))).Count
    if ($closeCount -ne 1) { Fail ($line + ': 购买成功后自动关店应恰 1 处（.closeContainer();），实测 ' + $closeCount) }
    $iWd = $ms.IndexOf('StarEngineEconomy.withdraw('); $iClose = $ms.IndexOf('.closeContainer();')
    if ($iWd -lt 0) { Fail ($line + ': 购买未走 StarEngineEconomy.withdraw') }
    elseif ($iClose -ge 0 -and $iClose -lt $iWd) { Fail ($line + ': closeContainer() 必须在扣款之后（扣款=' + $iWd + ' 关店=' + $iClose + '）') }
    if ($ms.IndexOf('StarCoinBalanceSync.notifyChanged(') -lt 0) { Fail ($line + ': 扣款后未同步余额（notifyChanged）') }

    Write-Out ('CHIPUI_LOGIC: G11..G16b / G17..G20（含 G13b 语义锚定 / G17b 三格同价 / G18b 上限钳制）判据组新增失败 ' + ($fail.Count - $logicFailStart) + ' 条')

    # ---- G21 方块接入齐备 ----
    $blockPath = Join-Path $javaDir 'block/ChipShopBlock.java'
    if (-not (Test-Path -LiteralPath $blockPath)) { Fail ($line + ': 缺少 block/ChipShopBlock.java') }
    elseif ((Code-Src $blockPath).IndexOf('ChipShopOpener.open(player)') -lt 0) { Fail ($line + ': ChipShopBlock 未接 ChipShopOpener.open') }
    $modBlocksSrc = Code-Src (Join-Path $javaDir 'block/ModBlocks.java')
    if ($modBlocksSrc.IndexOf('register("chip_shop"') -lt 0 -or $modBlocksSrc.IndexOf('new ChipShopBlock(') -lt 0) { Fail ($line + ': ModBlocks 未注册 chip_shop') }
    # G21c ModItems：登记**写法**两线本就不同 —— 1.21.1 = ITEMS.register("chip_shop", …DeferredItem)、1.20.1 = registerItem("chip_shop", …RegistryObject)。
    # 旧判据写死 1.21.1 的 registerSimpleBlockItem 字面 ⇒ .116「三商店方块 stacksTo(1)」改写注册入口后两线**双双误报**（2026-10-14 取证：非产品回归）。
    # 新判据 = 锚定**整条注册语句**（字段名 + 注册入口 + "chip_shop" + 语句体），再在语句体内要求 BlockItem 形态 + ModBlocks.CHIP_SHOP：
    #   未登记 / 登记成普通 Item / 引用错方块 ⇒ 三种漏登记仍判红（反向能力不丢）。
    $miSrc = Code-Src (Join-Path $javaDir 'item/ModItems.java')
    $miReg = [regex]::Match($miSrc, 'CHIP_SHOP\s*=\s*(?<fn>[\w.]*register\w*)\s*\(\s*"chip_shop"\s*,(?<b>.*?);', 'Singleline')
    if (-not $miReg.Success) { Fail ($line + ': ModItems 未登记 chip_shop BlockItem') }
    else {
        $miFn = $miReg.Groups['fn'].Value; $miBody = $miReg.Groups['b'].Value
        # 「BlockItem 形态」= 直接 new BlockItem(...)，或用自建入口 registerSimpleBlockItem(名, ModBlocks.X)（其方法体就是 new BlockItem(block.get(), …)）
        if (-not ($miBody.IndexOf('new BlockItem(') -ge 0 -or $miFn.EndsWith('registerSimpleBlockItem'))) { Fail ($line + ': ModItems 的 chip_shop 未按 BlockItem 登记（方块物品形态缺失）') }
        elseif ($miBody.IndexOf('ModBlocks.CHIP_SHOP') -lt 0) { Fail ($line + ': ModItems 的 chip_shop BlockItem 未引用 ModBlocks.CHIP_SHOP') }
    }
    if ((Code-Src (Join-Path $javaDir 'init/ModCreativeTabs.java')).IndexOf('ModItems.CHIP_SHOP.get()') -lt 0) { Fail ($line + ': 创造栏未收录筹码商店') }
    $resDir = Join-Path $lineDir 'src/main/resources'
    $dataDir = Join-Path $resDir 'data/astral_dice'
    # 平台差异（2026-10-12 复核后修正）：1.20.1 只加载**复数**目录（recipes/ / loot_tables/），
    # 1.21.1 用**单数**（recipe/ / loot_table/）⇒ 守门必须读本线**实际生效**的那一份；
    # 旧版写死单数会逼移植线另存一份永不加载的副本，并让 G22 的撞车检查在 forge 线上空转。
    if ($line -eq 'forge-1.20.1') {
        $recipeDir = Join-Path $dataDir 'recipes'
        $lootDir = Join-Path $dataDir 'loot_tables/blocks'
        $recipeResult = '"item": "astral_dice:chip_shop"'
    } else {
        $recipeDir = Join-Path $dataDir 'recipe'
        $lootDir = Join-Path $dataDir 'loot_table/blocks'
        $recipeResult = '"id": "astral_dice:chip_shop"'
    }
    $bsSrc = Read-Src (Join-Path $resDir 'assets/astral_dice/blockstates/chip_shop.json')
    foreach ($t in @('"facing=north"', '"facing=east"', '"facing=south"', '"facing=west"', 'astral_dice:block/chip_shop')) { if ($bsSrc.IndexOf($t) -lt 0) { Fail ($line + ': blockstates/chip_shop.json 缺 ' + $t) } }
    foreach ($t in @('"y": 90', '"y": 180', '"y": 270')) { if ($bsSrc.IndexOf($t) -lt 0) { Fail ($line + ': blockstates/chip_shop.json 缺旋转 ' + $t) } }
    if ((Read-Src (Join-Path $resDir 'assets/astral_dice/models/item/chip_shop.json')).IndexOf('astral_dice:block/chip_shop') -lt 0) { Fail ($line + ': models/item/chip_shop.json 未继承方块模型') }
    if ((Read-Src (Join-Path $lootDir 'chip_shop.json')).IndexOf('astral_dice:chip_shop') -lt 0) { Fail ($line + ': 掉落表缺 chip_shop') }
    if ((Read-Src (Join-Path $recipeDir 'chip_shop.json')).IndexOf($recipeResult) -lt 0) { Fail ($line + ': 方块配方缺 chip_shop 产出') }
    Write-Out 'CHIPUI_G21: 方块接入（Block/ModBlocks/BlockItem/创造栏/blockstates/物品模型/掉落表/配方）已核'

    # ================= G21b..G27（2026-10-12 复核后补强：治「标识符存在即绿」与「判据过松」） =================
    # 本段全部使用「去注释源码」+ 方法体/顺序锚定；判据正本 = docs/chip-shop-logic-plan.md §9
    function Get-Body([string]$text, [string]$sig) {
        $i = $text.IndexOf($sig)
        if ($i -lt 0) { return '' }
        $j = $text.IndexOf('{', $i)
        if ($j -lt 0) { return '' }
        $depth = 0
        for ($k = $j; $k -lt $text.Length; $k++) {
            if ($text[$k] -eq '{') { $depth++ }
            elseif ($text[$k] -eq '}') { $depth--; if ($depth -eq 0) { return $text.Substring($j, $k - $j + 1) } }
        }
        return ''
    }
    function Assert-Order([string]$text, [string[]]$seq, [string]$label) {
        $prev = -1
        foreach ($t in $seq) {
            $ix = $text.IndexOf($t, $prev + 1)   # 前向扫描：同名 token 重复出现（如两处 break;）也能锚定顺序
            if ($ix -lt 0) { Fail ($script:line + ': ' + $label + ' 缺「' + $t + '」'); return $false }
            if ($ix -lt $prev) { Fail ($script:line + ': ' + $label + ' 顺序错误（「' + $t + '」早于前一项）'); return $false }
            $prev = $ix
        }
        return $true
    }

    # ---- G21b 方块朝向语义（此前只查字符串：删掉 getStateForPlacement 也能绿） ----
    # 2026-10-12 复核追加：**按线分支** —— 1.21.1 有方块编解码体系（codec 覆写）与 useWithoutItem 覆写点，
    # 1.20.1 两样都没有（交互走六参 use，本线 CardShopBlock 同形）。旧版对两线都要求 1.21.1 专有字面，
    # 逼得移植线只能靠「假字面/台账常量」蒙过守门 —— 判据必须查各线**真实**的语义形态。
    if (Test-Path -LiteralPath $blockPath) {
        $bsrc = Code-Src $blockPath
        $bneed = @(
            'extends HorizontalDirectionalBlock',
            'registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH))',
            'public BlockState getStateForPlacement(BlockPlaceContext context)',
            'context.getHorizontalDirection().getOpposite()',
            'createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)',
            'builder.add(FACING);',
            'ChipShopOpener.open(player)',
            'if (!level.isClientSide()) {',
            'return InteractionResult.sidedSuccess(level.isClientSide());'
        )
        if ($line -eq 'forge-1.20.1') {
            $bflavor = '六参 use / 无 codec 体系'
            $bneed += 'public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,'
            $bneed += 'InteractionHand hand, BlockHitResult hitResult)'
            # 反向判据：移植线**不得**出现 1.21.1 专有字面（含为蒙守门而造的台账常量）；
            # 2026-10-14 对称补强：连 1.21.1 的两个交互入口标识符一并禁（1.20.1 的 Block 无 useItemOn / ItemInteractionResult，出现即越线）。
            $bforbid = @('simpleCodec(ChipShopBlock::new)', 'MapCodec<ChipShopBlock>', 'useItemOn(', 'ItemInteractionResult')
        } else {
            $bflavor = 'codec 覆写 / useWithoutItem 覆写点'
            $bneed += 'simpleCodec(ChipShopBlock::new)'
            $bneed += 'protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult)'
            # 2026-10-14 收窄：原字面 `InteractionHand hand, BlockHitResult hitResult)` 会被本线**合法**的七参
            # useItemOn(…, Player player, InteractionHand hand, BlockHitResult hitResult) 续行（ChipShopBlock.java:115）误命中 ⇒ 改为只锚
            # **1.20.1 形态的六参 use 声明头**（本线不应有的旧签名）；拿台账常量蒙守门照样命中（判据读去注释源码）。
            $bforbid = @('InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,')
        }
        $bmiss = @($bneed | Where-Object { $bsrc.IndexOf($_) -lt 0 })
        if ($bmiss.Count -gt 0) { Fail ($line + ': ChipShopBlock 朝向/交互语义缺失 ' + ($bmiss -join ' | ')) }
        foreach ($bf in $bforbid) { if ($bsrc.IndexOf($bf) -ge 0) { Fail ($line + ': ChipShopBlock 出现本线不应有的平台专有字面「' + $bf + '」') } }
        Write-Out ('CHIPUI_G21b: 方块朝向语义（按线：' + $bflavor + '）缺失 ' + $bmiss.Count + ' 项、越线字面检查 ' + $bforbid.Count + ' 项命中 0'  )
    }

    # ---- G22 shaped 配方唯一性（同图案同材料 ⇒ 只命中一条，另一个方块静默不可合成） ----
    if ($line -eq 'forge-1.20.1') { $recipeDirs = @($recipeDir) }
    else { $recipeDirs = @($recipeDir, (Join-Path $lineDir 'src/generated/resources/data/astral_dice/recipe')) }
    $sigs = @{}
    $shaped = 0
    foreach ($rd in $recipeDirs) {
        if (-not (Test-Path -LiteralPath $rd)) { continue }
        foreach ($rf in (Get-ChildItem -LiteralPath $rd -Filter '*.json' -File)) {
            $jt = Read-Src $rf.FullName
            if ($jt.IndexOf('crafting_shaped') -lt 0) { continue }
            $j = $null
            try { $j = $jt | ConvertFrom-Json } catch { Fail ($line + ': 配方 JSON 解析失败 ' + $rf.Name); continue }
            $shaped++
            $used = @()
            foreach ($row in @($j.pattern)) { foreach ($ch in $row.ToCharArray()) { $u = [string]$ch; if ($u -ne ' ' -and ($used -notcontains $u)) { $used += $u } } }
            $ks = @()
            foreach ($pp in $j.key.PSObject.Properties) {
                if ($used -notcontains $pp.Name) { continue }   # 只算 pattern 里真正用到的键，否则多声明一个无用键就能绕过撞车判定
                $ing = 'x'
                if ($null -ne $pp.Value.item) { $ing = 'i:' + $pp.Value.item } elseif ($null -ne $pp.Value.tag) { $ing = 't:' + $pp.Value.tag }
                $ks += ($pp.Name + '=' + $ing)
            }
            $sig = ((@($j.pattern)) -join '/') + '|' + ((@($ks | Sort-Object)) -join ',')
            if (-not $sigs.ContainsKey($sig)) { $sigs[$sig] = @() }
            $sigs[$sig] = @($sigs[$sig]) + $rf.Name
        }
    }
    $dupGroups = @($sigs.GetEnumerator() | Where-Object { $_.Value.Count -gt 1 })
    foreach ($dg in $dupGroups) { Fail ($line + ': shaped 配方撞车（图案与材料完全相同只会命中一条，另一个产出静默不可合成）：' + ($dg.Value -join ' / ')) }
    $chipRc = Read-Src (Join-Path $recipeDir 'chip_shop.json')
    if ($chipRc -ne '') {
        if ($chipRc.IndexOf($recipeResult) -lt 0 -or $chipRc.IndexOf('"count": 1') -lt 0) { Fail ($line + ': chip_shop 配方产出应为 astral_dice:chip_shop ×1') }
        # 2026-10-14：.112 起 chip_shop 配方改为 pattern GPG/HKH/HHH（blank_chip 由 **S** 位移到 **K** 位）⇒ 旧判据要求字面 "S" 变成误报。
        # 新判据 = 解析 JSON 后逐字锚定「图案 GPG/HKH/HHH」+「key K = astral_dice:blank_chip」；
        # 「与 card_shop 的 BBB 中排不撞车」由上方 G22 撞车组（图案 + 材料全同签名）继续守，两组互补、不互相替代。
        $chipJson = $null
        try { $chipJson = $chipRc | ConvertFrom-Json } catch { $chipJson = $null }
        if ($null -eq $chipJson) { Fail ($line + ': chip_shop 配方 JSON 解析失败') }
        else {
            $chipPat = ((@($chipJson.pattern)) -join '/')
            $chipK = [string]$chipJson.key.K.item
            if ($chipPat -ne 'GPG/HKH/HHH') { Fail ($line + ': chip_shop 配方图案应为 GPG/HKH/HHH（与 card_shop 的 BBB 中排区分），实测 ' + $chipPat) }
            if ($chipK -ne 'astral_dice:blank_chip') { Fail ($line + ': chip_shop 配方 key K 应为 astral_dice:blank_chip，实测「' + $chipK + '」') }
        }
    }
    Write-Out ('CHIPUI_G22: shaped 配方 ' + $shaped + ' 条，同图案同材料重复组 ' + $dupGroups.Count)

    # ---- G23 DataSlot 布局（7 槽）与拒绝码（0/1/2） + 屏幕侧分派 ----
    $dsStart = $ms.IndexOf('addDataSlot(new DataSlot()')
    $dsEnd = $ms.IndexOf('if (!player.level().isClientSide()) {')
    if ($dsStart -lt 0 -or $dsEnd -le $dsStart) { Fail ($line + ': 无法定位 DataSlot 区块') }
    else {
        $dsRegion = $ms.Substring($dsStart, $dsEnd - $dsStart)
        [void](Assert-Order $dsRegion @('clientPrice', 'ChipShopState.get(player).price()', 'clientRefreshesLeft', 'ChipShopState.get(player).refreshesLeft()', 'ChipShopCatalog.REFRESH_MAX', 'clientDenyReason', 'clientTiers[index]') 'DataSlot 顺序（单价/剩余刷新/总刷新/拒绝原因/品质码）')
    }
    $dsCount = ([regex]::Matches($ms, [regex]::Escape('addDataSlot(new DataSlot()'))).Count
    if ($dsCount -ne 5) { Fail ($line + ': addDataSlot 站点应 5 处（4 显式 + 1 循环 ⇒ 共 7 槽），实测 ' + $dsCount) }
    if ($ms.IndexOf('for (int i = 0; i < OFFER_SLOTS; i++) {') -lt 0) { Fail ($line + ': 品质码 DataSlot 应由 OFFER_SLOTS 循环产生') }
    foreach ($t in @('DENY_NONE = -1', 'DENY_NO_COIN = 0', 'DENY_NO_REFRESH = 1', 'DENY_INVENTORY_FULL = 2')) { if ($ms.IndexOf($t) -lt 0) { Fail ($line + ': 拒绝码常量缺 ' + $t) } }
    if ($src.IndexOf('reason == ChipShopMenu.DENY_NO_COIN || reason == ChipShopMenu.DENY_INVENTORY_FULL') -lt 0) { Fail ($line + ': 花费文本闪红未按 0/2 两码判定') }
    if ($src.IndexOf('reason == ChipShopMenu.DENY_NO_REFRESH') -lt 0) { Fail ($line + ': N/N 文本闪红未按 1 号码判定') }

    # ---- G24 星等来源 ----
    if ($rollerSrc.IndexOf('Math.max(0, Math.min(3, CardShopShelf.currentStarLevel(player)))') -lt 0) { Fail ($line + ': starLevel 未复用 CardShopShelf.currentStarLevel 并夹 0..3') }
    if ($rollerSrc.IndexOf('ChipShopCatalog.QUALITY_PROB[star]') -lt 0) { Fail ($line + ': 抽取未按 star 取概率表 QUALITY_PROB[star]') }

    # ---- G25 无折扣路径（§6 Q13）+ 标题键只保留不绘制（§8 Q17） ----
    foreach ($cf in (Get-ChildItem -LiteralPath $javaDir -Recurse -Filter 'ChipShop*.java' -File)) {
        $cs2 = Code-Src $cf.FullName
        foreach ($ft in @('discount', 'promotion', 'MEMBER_RECOMMENDATION')) {
            if ($cs2.IndexOf($ft) -ge 0) { Fail ($line + ': ' + $cf.Name + ' 出现折扣路径标识符 ' + $ft + '（筹码商店无任何折扣路径）') }
        }
    }
    if ($src.IndexOf('"gui.astral_dice.chip_shop"') -ge 0) { Fail ($line + ': 屏幕绘制了标题键 gui.astral_dice.chip_shop（键保留但不绘制）') }
    foreach ($t in @('gui.astral_dice.chip_shop.cost', 'gui.astral_dice.chip_shop.refresh_counter', 'gui.astral_dice.chip_shop.empty')) { if ($src.IndexOf($t) -lt 0) { Fail ($line + ': 屏幕未使用 ' + $t) } }

    # ---- G26 语义锚定：categoryWeight 分支顺序 / roll 三处 break 与去重顺序 / 概率表行序 / 命中面 / handleBuy 关店 / 价格公式右括号 ----
    $cwBody = Get-Body $catSrc 'public static double categoryWeight('
    if ($cwBody -eq '') { Fail ($line + ': 无法定位 categoryWeight 方法体') }
    else { [void](Assert-Order $cwBody @('if (lastPick == null || candidate == null || candidate != lastPick) {', 'return 1.0;', 'if (slot <= 0) {', 'return CATEGORY_ALPHA;', 'return (CATEGORY_ALPHA - 1.0) * SLOT_DECAY + 1.0;') 'categoryWeight 分支顺序') }
    $rollBody = Get-Body $rollerSrc 'public static List<ChipShopCatalog.Entry> roll(Player player, RandomSource random,'
    if ($rollBody -eq '') { Fail ($line + ': 无法定位抽取器 roll 方法体') }
    else {
        [void](Assert-Order $rollBody @('if (pool.isEmpty()) {', 'break;', 'if (tier <= 0) {', 'break;', 'if (chosen == null) {', 'break;', 'picked.add(chosen);', 'pool.remove(chosen);') '抽取器三处 break 与去重顺序')
        if ($rollBody.IndexOf('picked.set(') -ge 0) { Fail ($line + ': 抽取器出现 picked.set(（按槽位占位会破坏去重/缺口顺序）') }
    }
    $probBody = Get-Body $catSrc 'QUALITY_PROB = {'
    if ($probBody -eq '') { Fail ($line + ': 无法定位 QUALITY_PROB 初始化块') }
    else { [void](Assert-Order $probBody @('{0.60, 0.37, 0.03}', '{0.60, 0.37, 0.03}', '{0.40, 0.45, 0.15}', '{0.25, 0.50, 0.25}') '概率表行序（0★/1★/2★/3★）') }
    $ciBody = Get-Body $src 'private int chipIndexAt(int mouseX, int mouseY)'
    if ($ciBody -eq '') { Fail ($line + ': 无法定位 chipIndexAt 方法体') }
    else { [void](Assert-Order $ciBody @('isHoveringStrip(mouseX, mouseY, CELL_X[cell], CELL_Y, CELL_W, CELL_H)', 'isHoveringStrip(mouseX, mouseY, LABEL_X[cell], LABEL_Y, LABEL_W, LABEL_H)', 'return chip;') '命中面（筹码格 ∪ 标签框）') }
    $hbBody = Get-Body $ms 'public void handleBuy('
    $hrBody = Get-Body $ms 'public void handleRefresh('
    if ($hbBody -eq '' -or $hrBody -eq '') { Fail ($line + ': 无法定位 handleBuy / handleRefresh 方法体') }
    else {
        if (([regex]::Matches($hbBody, [regex]::Escape('ChipShopRoller.roll('))).Count -ne 1) { Fail ($line + ': handleBuy 内重抽应恰 1 处') }
        if (([regex]::Matches($hrBody, [regex]::Escape('ChipShopRoller.roll('))).Count -ne 1) { Fail ($line + ': handleRefresh 内重抽应恰 1 处') }
        if (([regex]::Matches($hbBody, [regex]::Escape('.closeContainer();'))).Count -ne 1) { Fail ($line + ': .closeContainer(); 必须落在 handleBuy 方法体内且恰 1 处') }
        else {
            $iw = $hbBody.IndexOf('StarEngineEconomy.withdraw(')
            $ic = $hbBody.IndexOf('.closeContainer();')
            if ($iw -lt 0 -or $ic -lt $iw) { Fail ($line + ': handleBuy 内关店必须在扣款之后（扣款=' + $iw + ' 关店=' + $ic + '）') }
        }
    }
    if ($catSrc.IndexOf('Math.min(PRICE_MAX, PRICE_INITIAL + PRICE_STEP * count)') -lt 0) { Fail ($line + ': 价格公式未按 min(PRICE_MAX, PRICE_INITIAL + PRICE_STEP * count) 逐字实现（含右括号，须落在 ChipShopCatalog.priceOf）') }
    Write-Out 'CHIPUI_G23..G27: DataSlot/拒绝码、星等来源、无折扣无标题、语义锚定（categoryWeight/roll break/概率表行序/命中面/handleBuy 关店/价格公式）已核'
    # 探针类引用守门（既有能力，顺带提示）
    Write-Out ('CHIPUI_NEXT: pwsh -NoProfile -File scripts/test/mt_build.ps1 --version ' + $mtVersions[$key] + ' --timeout 300')
}

Write-Out ('CHIPUI_TOTAL_FAIL: ' + $fail.Count)
if ($fail.Count -eq 0) { Write-Out 'CHIPUI_RESULT: OK'; exit 0 }
Write-Out 'CHIPUI_RESULT: FAIL'; exit 1
