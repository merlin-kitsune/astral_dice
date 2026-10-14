<#
筹码商店守门「变异测试」（judge-mutation）—— 用于证明 verify_chip_shop_ui.ps1 的 G9..G27 判据不是假绿。

做法：对源码/资源逐条注入一处**语义变异**（去重 / 概率表 / 已拥有过滤 / 双击 / 第二购买入口 /
隐藏槽 mayPickup / 价格步长 / 价格上限 / 刷新周期 / 刷新上限钳制 / 物品栏已满判定取反 / 筹码格命中条件 /
确认守卫被削 / 语言键集不对称 / 悬空语言键 / 两枚图标互换字节），共 **37 条** = `$mutations` 35 条（文本变异）+ `$swaps` 2 条（图标字节互换）；
按其 `file` 归属配平：**蓝本线 neoforge-1.21.1 = 24 条**（M1..M15、M17..M24、外加 `$swaps` 的 M16）、
**移植线 forge-1.20.1 = 13 条**（M25..M37，条目里用 `line = 'forge'` 指定移植线），每条都要求守门脚本**判红**且失败信息命中预期关键字；
随后**字节级还原**并按 SHA256 复核文件已复原。

用法（仓库根目录）：
    pwsh -NoProfile -File scripts/verify/mutate_chip_shop_ui.ps1

退出码 0 = 全部变异都被抓到（守门有效）；1 = 有变异漏判（守门有洞）；
2 = harness 自身失败（锚点没命中 / 目标文件缺失 / 还原后字节不一致）—— 该条判据**根本没被注入**，
    与「漏判」分开计数、分开报告，禁止混进判红桶里。

⚠️ 多行锚点：脚本内 `$nl` 恒为 LF，而 forge 线源码/资源多为 CRLF ⇒ 锚点与替换串在比对前按
**目标文件自己的行尾**归一化（ConvertTo-FileEol）；不归一化时多行锚点会静默匹配不到（实测踩中过）。
⚠️ 2026-10-14：M17 的锚点随 G22 判据刷新同步更新 —— .112 起 chip_shop 配方为 `GPG/HKH/HHH`（`blank_chip` 由 S 位移到 K 位），
   旧的 `"BSB"` 锚点已不复存在（会以 MUT_HARNESS 报「未找到变异锚点」）。
#>
[CmdletBinding()]
param(
    [string]$Root = ''
)

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8
$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($Root)) { $Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot) }
$root = (Resolve-Path -LiteralPath $Root).Path
$java = Join-Path $root 'neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice'
$forge = Join-Path $root 'forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice'
$gate = Join-Path $root 'scripts/verify/verify_chip_shop_ui.ps1'
$log = Join-Path $root 'temp/t87/mutation.log'
$utf8 = New-Object System.Text.UTF8Encoding($false)

function Write-Out([string]$text) {
    [Console]::Out.Write($text)
    [Console]::Out.Write([char]10)
}

# 把锚点/替换串的行尾改成**目标文件自己的行尾**（脚本内 $nl 恒为 LF；forge 线文件多为 CRLF）。
# 只改锚点自身的换行，不改目标文件其余任何字节 —— 拼接结果与文件原有行尾一致。
function ConvertTo-FileEol([string]$s, [string]$eol) {
    $t = $s -replace "`r`n", "`n"
    if ($eol -eq "`r`n") { $t = $t -replace "`n", "`r`n" }
    return $t
}

$nl = [string][char]10

# M17 锚点（多行；here-string 逐字书写 pattern + key 整块，行尾由 ConvertTo-FileEol 按目标文件归一化）：
#   现状 = chip_shop 的 `GPG/HKH/HHH` + key{G,P,H,K}；变异 = 换成 card_shop 的 `TTT/BBB/MMM` + key{T,B,M}
#   ⇒ 两文件「图案 + 材料」签名全同 ⇒ 必被 G22 撞车组判红（原版单行锚点 `"BSB"` 自 .112 起已失效）。
$m17Find = @'
  "pattern": [
    "GPG",
    "HKH",
    "HHH"
  ],
  "key": {
    "G": {
      "item": "minecraft:gold_ingot"
    },
    "P": {
      "item": "astral_dice:star_plate"
    },
    "H": {
      "item": "minecraft:polished_blackstone_bricks"
    },
    "K": {
      "item": "astral_dice:blank_chip"
    }
  },
'@
$m17Repl = @'
  "pattern": [
    "TTT",
    "BBB",
    "MMM"
  ],
  "key": {
    "T": {
      "item": "minecraft:smooth_stone_slab"
    },
    "B": {
      "item": "astral_dice:star_coin"
    },
    "M": {
      "tag": "minecraft:logs"
    }
  },
'@

$mutations = @(
    @{ name = 'M1 池去重被取消（pool.remove 注释掉）'; file = "$java/shop/ChipShopRoller.java"
       find = 'pool.remove(chosen);'; repl = '// pool.remove(chosen);'; expect = '去重/缺口' },
    @{ name = 'M2 概率表改一位（0★ 的 60% → 61%）'; file = "$java/shop/ChipShopCatalog.java"
       find = '{0.60, 0.37, 0.03}'; repl = '{0.61, 0.37, 0.03}'; expect = '概率/权重常量' },
    @{ name = 'M3 已拥有过滤被去掉（needsReroll 不看已拥有）'; file = "$java/shop/ChipShopState.java"
       find = 'if (ChipShopRoller.isOwned(player, entry)) {'; repl = 'if (false) {'; expect = 'needsReroll 未按已拥有判定' },
    @{ name = 'M4 确认按钮改双击（引入 DOUBLE_CLICK_MS）'; file = "$java/screen/ChipShopScreen.java"
       find = 'private static final int PRESS_TICKS'; repl = 'private static final int DOUBLE_CLICK_MS = 250;' + $nl + '    private static final int PRESS_TICKS'; expect = '被禁的拖拽/双击标识符' },
    @{ name = 'M5 出现第二购买入口（mouseClicked 末尾也调确认）'; file = "$java/screen/ChipShopScreen.java"
       find = '        // ── ④ 其余一律交回原版'; repl = '        onConfirmPressed();' + $nl + '        // ── ④ 其余一律交回原版'; expect = 'onConfirmPressed() 出现次数应为 2' },
    @{ name = 'M6 隐藏槽 mayPickup 改 true（候选可被拿走）'; file = "$java/shop/ChipShopMenu.java"
       find = 'public boolean mayPickup(Player player) {'; repl = 'public boolean mayPickup(Player player) { return true; } /*MUT*/ private boolean unusedMayPickup(Player player) {'; expect = 'mayPickup=false' },
    @{ name = 'M7 价格步长 5 → 1'; file = "$java/shop/ChipShopCatalog.java"
       find = 'PRICE_STEP = 5'; repl = 'PRICE_STEP = 1'; expect = '价格公式/常量不符' },
    @{ name = 'M8 价格上限 100 → 50'; file = "$java/shop/ChipShopCatalog.java"
       find = 'PRICE_MAX = 100'; repl = 'PRICE_MAX = 50'; expect = '价格公式/常量不符' },
    @{ name = 'M9 刷新恢复周期 36000 → 18000'; file = "$java/shop/ChipShopCatalog.java"
       find = 'REFRESH_REGEN_TICKS = 36000'; repl = 'REFRESH_REGEN_TICKS = 18000'; expect = '刷新常量不符' },
    # ---- 以下为 2026-10-12 code_review 复核后补的「语义/条件表达式」变异（原 9 条只动常量与 token，覆盖不到逻辑面） ----
    @{ name = 'M10 刷新上限钳制被改（withRefreshes 的 Math.min → Math.max）'; file = "$java/shop/ChipShopState.java"
       find = 'Math.min(ChipShopCatalog.REFRESH_MAX, left)'; repl = 'Math.max(ChipShopCatalog.REFRESH_MAX, left)'; expect = '刷新次数未夹在 0..REFRESH_MAX' },
    @{ name = 'M11 物品栏已满判定被取反（hasNoRoom 的 isEmpty → !isEmpty）'; file = "$java/shop/ChipShopOpener.java"
       find = 'if (player.getInventory().getItem(i).isEmpty()) {'; repl = 'if (!player.getInventory().getItem(i).isEmpty()) {'; expect = '物品栏已满判定被改' },
    @{ name = 'M12 第 0 号筹码格永远选不中（chip >= 0 → chip > 0）'; file = "$java/screen/ChipShopScreen.java"
       find = 'if (chip >= 0) {'; repl = 'if (chip > 0) {'; expect = '筹码格命中判定被改' },
    @{ name = 'M13 空候选位也发购买包（确认守卫被削成 chip < 0）'; file = "$java/screen/ChipShopScreen.java"
       find = 'chip < 0 || ChipShopClientState.offer(chip).isEmpty()'; repl = 'chip < 0'; expect = '确认按钮缺' },
    @{ name = 'M14 语言键集不对称（仅 en_us 多加一条无关键）'; file = "$root/neoforge-1.21.1/src/main/resources/assets/astral_dice/lang/en_us.json"
       find = '"gui.astral_dice.chip_shop.empty"'; repl = '"zzz_probe_key": "x",' + $nl + '  "gui.astral_dice.chip_shop.empty"'; expect = '语言键集不对称' },
    @{ name = 'M15 Java 侧语言键写错（悬空键：no_refresh → no_refresh_x）'; file = "$java/shop/ChipShopMenu.java"
       find = 'msg.astral_dice.chip_shop_no_refresh'; repl = 'msg.astral_dice.chip_shop_no_refresh_x'; expect = '悬空语言键' },
    # ---- 2026-10-12 功能审查「守门过松」清单 → G21b/G22..G27 补强后追加的变异（证明新判据真的会红） ----
    @{ name = 'M17 配方撞车回归（chip_shop 改成与 card_shop 同图案同材料 TTT/BBB/MMM）'; file = "$root/neoforge-1.21.1/src/main/resources/data/astral_dice/recipe/chip_shop.json"
       find = $m17Find; repl = $m17Repl; expect = 'shaped 配方撞车' },
    @{ name = 'M18 关店被挪出 handleBuy（挪到同类新方法里）'; file = "$java/shop/ChipShopMenu.java"
       find = '        buyer.closeContainer();' + $nl + '    }'; repl = '    }' + $nl + $nl + '    private void mutatedClose(ServerPlayer buyer) { buyer.closeContainer(); }'; expect = '.closeContainer(); 必须落在 handleBuy 方法体内' },
    @{ name = 'M19 标签框命中被删掉（chipIndexAt 只剩筹码格）'; file = "$java/screen/ChipShopScreen.java"
       find = 'isHoveringStrip(mouseX, mouseY, LABEL_X[cell], LABEL_Y, LABEL_W, LABEL_H)'; repl = 'false'; expect = '命中面（筹码格 ∪ 标签框）' },
    @{ name = 'M20 方块朝向丢掉 getOpposite()（朝向与放置者相反）'; file = "$java/block/ChipShopBlock.java"
       find = 'context.getHorizontalDirection().getOpposite()'; repl = 'context.getHorizontalDirection()'; expect = 'ChipShopBlock 朝向/交互语义缺失' },
    @{ name = 'M21 拒绝码重复（DENY_NO_REFRESH 1 → 2，与物品栏已满撞码）'; file = "$java/shop/ChipShopMenu.java"
       find = 'DENY_NO_REFRESH = 1'; repl = 'DENY_NO_REFRESH = 2'; expect = '拒绝码常量缺' },
    @{ name = 'M22 星等来源被写死（不再读骰子星阶）'; file = "$java/shop/ChipShopRoller.java"
       find = 'CardShopShelf.currentStarLevel(player)'; repl = '3'; expect = 'starLevel 未复用' },
    @{ name = 'M23 注入折扣路径（§6 Q13：筹码商店无任何折扣）'; file = "$java/shop/ChipShopCatalog.java"
       find = 'public static final int OFFER_COUNT = 3;'; repl = 'public static final int OFFER_COUNT = 3;' + $nl + '    public static final double discount = 1.0;'; expect = '出现折扣路径标识符' },
    @{ name = 'M24 概率表行序对调（2★ 与 3★ 两行互换）'; file = "$java/shop/ChipShopCatalog.java"
       find = '            {0.40, 0.45, 0.15},' + $nl + '            {0.25, 0.50, 0.25},'; repl = '            {0.25, 0.50, 0.25},' + $nl + '            {0.40, 0.45, 0.15},'; expect = '概率表行序' },
    # ---- 2026-10-12 forge 移植后追加：证明 G21b 的**按线分支**两侧都真的会红（否则新分支是死判据） ----
    @{ name = 'M25 移植线方块塞入 1.21.1 专有字面（假 codec 常量蒙守门）'; line = 'forge'; file = "$forge/block/ChipShopBlock.java"
       find = '    private static final VoxelShape SHAPE'; repl = '    static final String MUT = "simpleCodec(ChipShopBlock::new);";' + $nl + '    private static final VoxelShape SHAPE'; expect = '本线不应有的平台专有字面' },
    @{ name = 'M26 移植线丢掉六参 use 入口（方块右键不再开店）'; line = 'forge'; file = "$forge/block/ChipShopBlock.java"
       find = '    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,'; repl = '    public InteractionResult useMuted(BlockState state, Level level, BlockPos pos, Player player,'; expect = 'ChipShopBlock 朝向/交互语义缺失' },
    # ---- 2026-10-12 forge 移植线**独立功能审查（code_review）**指出「24/26 条变异只作用在蓝本线」后补：把同一批判据按移植线的文件重打一遍 ----
    @{ name = 'M27 移植线池去重被取消（pool.remove 注释掉）'; line = 'forge'; file = "$forge/shop/ChipShopRoller.java"
       find = 'pool.remove(chosen);'; repl = '// pool.remove(chosen);'; expect = '去重/缺口' },
    @{ name = 'M28 移植线概率表改一位（0★ 的 60% → 61%）'; line = 'forge'; file = "$forge/shop/ChipShopCatalog.java"
       find = '{0.60, 0.37, 0.03}'; repl = '{0.61, 0.37, 0.03}'; expect = '概率/权重常量' },
    @{ name = 'M29 移植线已拥有过滤被去掉（needsReroll 不看已拥有）'; line = 'forge'; file = "$forge/shop/ChipShopState.java"
       find = 'if (ChipShopRoller.isOwned(player, entry)) {'; repl = 'if (false) {'; expect = 'needsReroll 未按已拥有判定' },
    @{ name = 'M30 移植线隐藏槽 mayPickup 改 true（候选可被拿走）'; line = 'forge'; file = "$forge/shop/ChipShopMenu.java"
       find = 'public boolean mayPickup(Player player) {'; repl = 'public boolean mayPickup(Player player) { return true; } /*MUT*/ private boolean unusedMayPickup(Player player) {'; expect = 'mayPickup=false' },
    @{ name = 'M31 移植线价格步长 5 → 1'; line = 'forge'; file = "$forge/shop/ChipShopCatalog.java"
       find = 'PRICE_STEP = 5'; repl = 'PRICE_STEP = 1'; expect = '价格公式/常量不符' },
    @{ name = 'M32 移植线刷新恢复周期 36000 → 18000'; line = 'forge'; file = "$forge/shop/ChipShopCatalog.java"
       find = 'REFRESH_REGEN_TICKS = 36000'; repl = 'REFRESH_REGEN_TICKS = 18000'; expect = '刷新常量不符' },
    @{ name = 'M33 移植线刷新上限钳制被改（withRefreshes 的 Math.min → Math.max）'; line = 'forge'; file = "$forge/shop/ChipShopState.java"
       find = 'Math.min(ChipShopCatalog.REFRESH_MAX, left)'; repl = 'Math.max(ChipShopCatalog.REFRESH_MAX, left)'; expect = '刷新次数未夹在 0..REFRESH_MAX' },
    @{ name = 'M34 移植线关店被挪出 handleBuy（closeContainer 调用被注释掉）'; line = 'forge'; file = "$forge/shop/ChipShopMenu.java"
       find = 'buyer.closeContainer();'; repl = '// buyer.closeContainer();'; expect = '.closeContainer(); 必须落在 handleBuy 方法体内' },
    @{ name = 'M35 移植线第 0 号筹码格永远选不中（chip >= 0 → chip > 0）'; line = 'forge'; file = "$forge/screen/ChipShopScreen.java"
       find = 'if (chip >= 0) {'; repl = 'if (chip > 0) {'; expect = '筹码格命中判定被改' },
    # 多行锚点 + 移植线 CRLF 文件：既补强「关店必须落在 handleBuy 方法体内」这条判据在移植线的覆盖，
    # 又同时验证 harness 的**行尾归一化**（不归一化时这条锚点会静默匹配不到 ⇒ MUT_HARNESS）
    @{ name = 'M37 移植线关店被挪出 handleBuy（多行锚点，CRLF 文件）'; line = 'forge'; file = "$forge/shop/ChipShopMenu.java"
       find = '        buyer.closeContainer();' + $nl + '    }'; repl = '    }' + $nl + $nl + '    private void mutatedClose(ServerPlayer buyer) { buyer.closeContainer(); }'; expect = '.closeContainer(); 必须落在 handleBuy 方法体内' }
)

# 二进制变异：两枚筹码图标互换字节（只比对文件是否存在的判据抓不到，逐字节对账才抓得到）
$iconsDir = Join-Path $root 'neoforge-1.21.1/src/main/resources/assets/astral_dice/textures/gui/chip_shop/chips'
$forgeIconsDir = Join-Path $root 'forge-1.20.1/src/main/resources/assets/astral_dice/textures/gui/chip_shop/chips'
$swaps = @(
    @{ name = 'M16 两枚筹码图标互换字节（adrenaline_high ↔ adrenaline_low）'
       a = (Join-Path $iconsDir 'adrenaline_high_chip.png'); b = (Join-Path $iconsDir 'adrenaline_low_chip.png'); expect = '筹码图标不合格' },
    @{ name = 'M36 移植线两枚筹码图标互换字节（adrenaline_high ↔ adrenaline_low）'; line = 'forge'
       a = (Join-Path $forgeIconsDir 'adrenaline_high_chip.png'); b = (Join-Path $forgeIconsDir 'adrenaline_low_chip.png'); expect = '筹码图标不合格' }
)

Write-Out 'MUT: 筹码商店守门变异测试（每条变异都必须被判红）'
Write-Out ('MUT_ROOT: ' + $root)

$caught = 0
$escaped = @()   # 守门**漏判**：变异注入了、判据没红
$harness = @()   # harness 自身失败：变异**根本没注入**（锚点/文件/还原）⇒ 该条判据未被验证，不得当判红
foreach ($m in $mutations) {
    $path = $m.file
    if (-not (Test-Path -LiteralPath $path)) { Write-Out ('MUT_HARNESS: ' + $m.name + ' —— 目标文件不存在 ' + $path); $harness += $m.name; continue }
    $bytes = [System.IO.File]::ReadAllBytes($path)
    $shaBefore = (Get-FileHash -Algorithm SHA256 -LiteralPath $path).Hash
    $text = $utf8.GetString($bytes)
    $eol = $nl
    if ($text.Contains([string][char]13 + [string][char]10)) { $eol = [string][char]13 + [string][char]10 }
    $find = ConvertTo-FileEol $m.find $eol
    $replM = ConvertTo-FileEol $m.repl $eol
    $idx = $text.IndexOf($find)
    if ($idx -lt 0) { Write-Out ('MUT_HARNESS: ' + $m.name + ' —— 未找到变异锚点（按文件行尾归一化后仍无命中）'); $harness += $m.name; continue }
    if ($text.IndexOf($find, $idx + 1) -ge 0 -and $find -ne '{0.60, 0.37, 0.03}') {
        # 锚点不唯一会让「变异」变得含糊，直接拒绝（概率表条目是唯一被允许出现两次的锚点，且只改第一处）
        Write-Out ('MUT_HARNESS: ' + $m.name + ' —— 变异锚点不唯一'); $harness += $m.name; continue
    }
    $mutated = $text.Substring(0, $idx) + $replM + $text.Substring($idx + $find.Length)
    try {
        [System.IO.File]::WriteAllText($path, $mutated, $utf8)
        $tgt = 'neo'
        if ($m.ContainsKey('line')) { $tgt = $m.line }
        & pwsh -NoProfile -File $gate $tgt *> $log
        $code = $LASTEXITCODE
        $out = Get-Content -LiteralPath $log -Raw
        $hit = $out.Contains($m.expect)
        if ($code -ne 0 -and $hit) {
            $caught++
            Write-Out ('MUT: ' + $m.name + ' => RED(ok) expect=' + $m.expect)
        } else {
            $escaped += $m.name
            Write-Out ('MUT: ' + $m.name + ' => 漏判！exit=' + $code + ' expect=' + $m.expect + ' hit=' + $hit)
        }
    } finally {
        [System.IO.File]::WriteAllBytes($path, $bytes)
        $shaAfter = (Get-FileHash -Algorithm SHA256 -LiteralPath $path).Hash
        if ($shaAfter -ne $shaBefore) { Write-Out ('MUT_HARNESS: ' + $m.name + ' —— 还原后哈希不一致（' + $shaBefore + ' vs ' + $shaAfter + '）'); $harness += $m.name }
        else { Write-Out ('MUT_RESTORE: ' + (Split-Path -Leaf $path) + ' 已字节还原 ' + $shaAfter.Substring(0, 12)) }
    }
}

foreach ($s in $swaps) {
    $pa = $s.a; $pb = $s.b
    if (-not (Test-Path -LiteralPath $pa) -or -not (Test-Path -LiteralPath $pb)) {
        Write-Out ('MUT_HARNESS: ' + $s.name + ' —— 目标图标不存在'); $harness += $s.name; continue
    }
    $ba = [System.IO.File]::ReadAllBytes($pa); $bb = [System.IO.File]::ReadAllBytes($pb)
    $ha = (Get-FileHash -Algorithm SHA256 -LiteralPath $pa).Hash; $hb = (Get-FileHash -Algorithm SHA256 -LiteralPath $pb).Hash
    try {
        [System.IO.File]::WriteAllBytes($pa, $bb)
        [System.IO.File]::WriteAllBytes($pb, $ba)
        $tgt = 'neo'
        if ($s.ContainsKey('line')) { $tgt = $s.line }
        & pwsh -NoProfile -File $gate $tgt *> $log
        $code = $LASTEXITCODE
        $out = Get-Content -LiteralPath $log -Raw
        $hit = $out.Contains($s.expect)
        if ($code -ne 0 -and $hit) {
            $caught++
            Write-Out ('MUT: ' + $s.name + ' => RED(ok) expect=' + $s.expect)
        } else {
            $escaped += $s.name
            Write-Out ('MUT: ' + $s.name + ' => 漏判！exit=' + $code + ' expect=' + $s.expect + ' hit=' + $hit)
        }
    } finally {
        [System.IO.File]::WriteAllBytes($pa, $ba)
        [System.IO.File]::WriteAllBytes($pb, $bb)
        $ha2 = (Get-FileHash -Algorithm SHA256 -LiteralPath $pa).Hash; $hb2 = (Get-FileHash -Algorithm SHA256 -LiteralPath $pb).Hash
        if ($ha2 -ne $ha -or $hb2 -ne $hb) {
            Write-Out ('MUT_HARNESS: ' + $s.name + ' —— 还原后哈希不一致'); $harness += $s.name
        } else {
            Write-Out ('MUT_RESTORE: ' + (Split-Path -Leaf $pa) + ' + ' + (Split-Path -Leaf $pb) + ' 已字节还原')
        }
    }
}

Write-Out ('MUT_TOTAL: ' + ($mutations.Count + $swaps.Count) + '，判红 ' + $caught)
if ($escaped.Count -gt 0) { Write-Out ('MUT_ESCAPED: ' + ($escaped -join ' | ')) }
if ($harness.Count -gt 0) {
    Write-Out ('MUT_HARNESS_FAIL: ' + ($harness -join ' | '))
    Write-Out 'MUT_RESULT: FAIL(harness)'
    exit 2
}
if ($escaped.Count -eq 0) { Write-Out 'MUT_RESULT: OK'; exit 0 }
Write-Out 'MUT_RESULT: FAIL'
exit 1
