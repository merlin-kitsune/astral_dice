# -*- coding: utf-8 -*-
<#
筹码配方规范 — 公共数据与逻辑(计划书与校验脚本共用)。

筹码配方分两档,**互不越界**:规则 1/2/5 只作用于**非进阶筹码**(基础/流派/新补);
任何「统一/生成」工作都不得改写进阶筹码,进阶筹码永远走规则 3/4 的通用升级模板。
(与 AGENTS.md「筹码一览 → 筹码配方规范」一致)

规则(定稿,含 2026-09-11 进阶筹码回滚):
  1. 非进阶筹码:第三行 = 品质物品(稀有=星币 / 史诗=星盘 / 传奇=黄金星盘)
  2. 非进阶筹码:第二行中间 = 空白筹码;第1/3位按流派(治愈=再生试剂/星光=星币尘
     /标记=标记涂料/充能=导电线材),无流派筹码则放与图标相关的材料
  3. 进阶筹码:一律走「通用升级」模板,**不**复用基础版图案(见 UPGRADE_TEMPLATE)
       蓝->紫(EPIC)     LGL/GTG/PPP  青金石 / 金锭 / 上一等级 / 星盘
       紫->金(UNCOMMON) RDR/DTD/GGG  红石  / 钻石 / 上一等级 / 黄金星盘
     (ADRENALINE_HIGH 原版字母写作 RZR/ZOZ/PPP,材料集合与紫->金模板完全一致)
  4. 进阶筹码第二行中间 = 上一等级筹码,unlockedBy 也指向上一等级筹码
  5. 非进阶缺失配方按上述生成,空位按作用与图标补材并保持左右对称;
     排除 星币/星盘/黄金星盘/空白筹码/空白立牌

⚠️ 本表是「当前期望值」,同时被计划书(docs/chip-recipe-plan.md)与校验脚本
  (pwsh -File scripts/verify/verify_chip_recipes.ps1)引用。改动前先确认双版本 ModRecipeProvider.java
  的真实内容,勿仅凭此表推断。

—— PowerShell 移植版:1:1 对应 scripts/verify/chip_common.py(原 .py 保留不删),
   运行期完全不再依赖 python。字典一律用 Ordinal 比较器(等价 Python dict 的大小写敏感)。
#>

# ============================================================================
# 路径(对应 Python 的 ROOT / NEO / FORGE / RES)
# ============================================================================
$ROOT = [System.IO.Path]::GetDirectoryName([System.IO.Path]::GetDirectoryName($PSScriptRoot))
$NEO = [System.IO.Path]::Combine($ROOT, 'neoforge-1.21.1', 'src', 'main', 'java', 'com', 'merlinkitsune', 'astral_dice')
$FORGE = [System.IO.Path]::Combine($ROOT, 'forge-1.20.1', 'src', 'main', 'java', 'com', 'merlinkitsune', 'astral_dice')
$RES = [System.IO.Path]::Combine($ROOT, 'neoforge-1.21.1', 'src', 'main', 'resources')

# ============================================================================
# 本地工具
# ============================================================================
# Python 的 io.open(...).read() 默认做通用换行翻译(\r\n / \r -> \n);本函数等价。
function Read-PyText {
    param([string]$Path)
    $t = [System.IO.File]::ReadAllText($Path, [System.Text.Encoding]::UTF8)
    $t = $t -replace "`r`n", "`n"
    return ($t -replace "`r", "`n")
}

# Python 的 io.open(..., newline="").read() 不做换行翻译;本函数等价。
function Read-PyTextRaw {
    param([string]$Path)
    return [System.IO.File]::ReadAllText($Path, [System.Text.Encoding]::UTF8)
}

# 建立「大小写敏感 + 保持插入顺序」的字典,等价 Python dict 语义。
function New-PyMap {
    return [System.Collections.Specialized.OrderedDictionary]::new([System.StringComparer]::Ordinal)
}

# 以 [ordered]@{...} 字面量构造 Ordinal 比较器的 OrderedDictionary(保持字面量书写顺序)。
function New-PyMapFrom {
    param([System.Collections.Specialized.OrderedDictionary]$Pairs)
    $d = New-PyMap
    foreach ($e in $Pairs.GetEnumerator()) { $d[$e.Key] = $e.Value }
    return ,$d
}

function Test-PyMapKey {
    param($Map, $Key)
    if ($null -eq $Map -or $null -eq $Key) { return $false }
    return [bool]$Map.Contains($Key)
}

# ============================================================================
# 从工程源码重算筹码表(对应 Python 模块级语句)
# ============================================================================
$items_src = Read-PyText ([System.IO.Path]::Combine($NEO, 'item', 'ModItems.java'))
$tabs_src = Read-PyText ([System.IO.Path]::Combine($NEO, 'init', 'ModCreativeTabs.java'))
$zh_json = Read-PyText ([System.IO.Path]::Combine($RES, 'assets', 'astral_dice', 'lang', 'zh_cn.json'))
$zh = ConvertFrom-Json -InputObject $zh_json -AsHashtable

$chips = New-PyMap
$RE_ITEMS = 'public static final DeferredItem<Item>\s+(\w+)\s*=\s*registerItem\("([^"]+)"\s*,\s*\(\)\s*->\s*new\s+\w+\((.*?)\)\);'
foreach ($_m in [regex]::Matches($items_src, $RE_ITEMS, [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
    $_id = $_m.Groups[2].Value
    if (-not $_id.Contains('chip') -or $_id.StartsWith('blank_chip')) {
        continue
    }
    $_rm = [regex]::Match($_m.Groups[3].Value, 'rarity\(Rarity\.(\w+)\)')
    $chips[$_m.Groups[1].Value] = @{ id = $_id; rarity = $(if ($_rm.Success) { $_rm.Groups[1].Value } else { 'COMMON' }) }
}

$stream_of = New-PyMap
foreach ($_pair in @(@('星光类', '星光'), @('治愈类', '治愈'), @('标记类', '标记'), @('充能类', '充能'), @('无流派', '无流派'))) {
    $_key = $_pair[0]
    $_name = $_pair[1]
    $_mm = [regex]::Match($tabs_src, ('// === ' + $_key + ' ===(.*?)(?=// === |\}\)\.build\(\))'), [System.Text.RegularExpressions.RegexOptions]::Singleline)
    if ($_mm.Success) {
        foreach ($_cm in [regex]::Matches($_mm.Groups[1].Value, 'output\.accept\(ModItems\.(\w+)\.get\(\)\)')) {
            $stream_of[$_cm.Groups[1].Value] = $_name
        }
    }
}

$RARITY_ITEM = New-PyMapFrom ([ordered]@{
    'RARE'     = 'C'
    'EPIC'     = 'P'
    'UNCOMMON' = 'G'
})
$RARITY_CN = New-PyMapFrom ([ordered]@{
    'RARE'     = '稀有'
    'EPIC'     = '史诗'
    'UNCOMMON' = '传奇'
    'COMMON'   = '—'
})

# 进阶筹码「通用升级」模板(与 e096513 原版一致,2026-09-11 回滚后生效)
# 行1/行2两侧按品质固定,行2中位 = 上一等级筹码,行3 = 本品质物品
$UPGRADE_TEMPLATE = New-PyMapFrom ([ordered]@{
    'EPIC'     = @(
        @('MC:LAPIS_LAZULI', 'MC:GOLD_INGOT', 'MC:LAPIS_LAZULI'),
        @('MC:GOLD_INGOT', $null, 'MC:GOLD_INGOT')
    )      # 蓝->紫
    'UNCOMMON' = @(
        @('MC:REDSTONE', 'MC:DIAMOND', 'MC:REDSTONE'),
        @('MC:DIAMOND', $null, 'MC:DIAMOND')
    )      # 紫->金
})

$V = New-PyMapFrom ([ordered]@{
    'REDSTONE_LAMP'            = '红石灯'; 'YELLOW_STAINED_GLASS' = '黄色染色玻璃'; 'IRON_INGOT' = '铁锭'; 'GOLD_INGOT' = '金锭'
    'GOLD_BLOCK'               = '金块'; 'LAPIS_LAZULI' = '青金石'; 'GOLDEN_APPLE' = '金苹果'; 'IRON_SWORD' = '铁剑'; 'DIAMOND' = '钻石'
    'SHIELD'                   = '盾牌'; 'SPONGE' = '海绵'; 'LEATHER' = '皮革'; 'BLUE_ICE' = '蓝冰'; 'LEATHER_BOOTS' = '皮革靴子'
    'GLASS_PANE'               = '玻璃板'; 'LEATHER_HELMET' = '皮革头盔'; 'COOKIE' = '曲奇'; 'EGG' = '鸡蛋'; 'MILK_BUCKET' = '奶桶'
    'SUGAR'                    = '糖'; 'CARVED_PUMPKIN' = '雕刻南瓜'; 'AMETHYST_SHARD' = '紫水晶碎片'; 'COPPER_INGOT' = '铜锭'
    'OAK_BUTTON'               = '橡木按钮'; 'NETHER_WART' = '下界疣'; 'TARGET' = '标靶'; 'SLIME_BALL' = '粘液球'
    'FERMENTED_SPIDER_EYE'     = '发酵蛛眼'; 'ENCHANTED_GOLDEN_APPLE' = '附魔金苹果'; 'WITHER_ROSE' = '凋零玫瑰'
    'NETHER_STAR'              = '下界之星'; 'WITHER_SKELETON_SKULL' = '凋灵骷髅头'; 'DIAMOND_SWORD' = '钻石剑'
    'POPPED_CHORUS_FRUIT'      = '爆裂紫颂果'; 'CRYING_OBSIDIAN' = '哭泣的黑曜石'; 'GOLDEN_SWORD' = '金剑'
    'NETHERITE_INGOT'          = '下界合金锭'; 'CONDUIT' = '潮涌核心'; 'ECHO_SHARD' = '回响碎片'; 'SPECTRAL_ARROW' = '光灵箭'
    'WRITABLE_BOOK'            = '书与笔'; 'EXPERIENCE_BOTTLE' = '附魔瓶'; 'REDSTONE_BLOCK' = '红石块'; 'REDSTONE' = '红石粉'
    'REDSTONE_TORCH'           = '红石火把'; 'ENDER_PEARL' = '末影珍珠'; 'FLINT' = '燧石'; 'PISTON' = '活塞'
    'LIGHTNING_ROD'            = '避雷针'; 'COMPARATOR' = '红石比较器'; 'MACE' = '重锤'; 'ANVIL' = '铁砧'; 'PAPER' = '纸'; 'INK_SAC' = '墨囊'
    'FEATHER'                  = '羽毛'; 'STRING' = '线'; 'PORKCHOP' = '生猪排'; 'BRICK' = '红砖'; 'CLOCK' = '时钟'
    'GRINDSTONE'               = '砂轮'; 'BOWL' = '碗'; 'COOKED_BEEF' = '牛排'; 'WHITE_WOOL' = '白色羊毛'; 'IRON_BLOCK' = '铁块'
    'BAMBOO'                   = '竹子'; 'END_CRYSTAL' = '末地水晶'; 'DRAGON_HEAD' = '龙首'
})
$SYM = New-PyMapFrom ([ordered]@{
    'C' = '星币'; 'P' = '星盘'; 'G' = '黄金星盘'; 'B' = '空白筹码'; 'W' = '导电线材'
    'R' = '再生试剂'; 'D' = '星币尘'; 'M' = '标记涂料'
})
$SYM_CONST = New-PyMapFrom ([ordered]@{
    'C' = 'STAR_COIN'; 'P' = 'STAR_PLATE'; 'G' = 'GOLDEN_STAR_PLATE'; 'B' = 'BLANK_CHIP'
    'W' = 'CONDUCTIVE_WIRE'; 'R' = 'REGENERATION_REAGENT'; 'D' = 'STAR_COIN_DUST'; 'M' = 'MARK_PAINT'
})

# ---- 平台差异:1.20.1 无 MC 1.21 新增物品时的等价替代(与 docs/compat-1.20.1-forge.md 一致) ----
$PLATFORM_OVERRIDE = New-PyMapFrom ([ordered]@{ 'forge' = (New-PyMapFrom ([ordered]@{ 'MC:MACE' = 'MC:ANVIL' })) })
$PLATFORM_NOTE = New-PyMapFrom ([ordered]@{ 'forge' = (New-PyMapFrom ([ordered]@{ 'MC:ANVIL' = ' // 1.20.1 无重锤(1.21 新增),以铁砧替代' })) })


function _plat {
    param($g, $dialect)
    $ov = $(if (Test-PyMapKey $PLATFORM_OVERRIDE $dialect) { $PLATFORM_OVERRIDE[$dialect] } else { New-PyMap })
    $out = @()
    foreach ($row in $g) {
        $newRow = @()
        foreach ($t in $row) {
            if ((Test-PyMapKey $ov $t)) { $newRow += $ov[$t] } else { $newRow += $t }
        }
        $out += , $newRow
    }
    return , $out
}


function cn {
    param([string]$const)
    if ($const -eq 'DICE') {
        return '骰子'
    }
    if ($const -eq 'ORBITAL_STRIKE_CARD') {
        return '轨道炮(卡牌)'
    }
    if ($const -eq 'STAR_COIN_BAG') {
        return '星币袋'
    }
    if (-not (Test-PyMapKey $chips $const)) {
        return $const
    }
    $k = 'item.astral_dice.' + $chips[$const]['id']
    if ($zh.ContainsKey($k)) { return $zh[$k] }
    return $chips[$const]['id']
}


function resolve {
    param($tok)
    if (Test-PyMapKey $SYM $tok) {
        return $SYM[$tok]
    }
    if ($tok -is [string] -and $tok.StartsWith('MC:')) {
        $k = $tok.Substring(3)
        if (Test-PyMapKey $V $k) { return $V[$k] }
        return $k
    }
    if ($tok -is [string] -and $tok.StartsWith('MOD:')) {
        return (cn $tok.Substring(4))
    }
    if ($tok -eq 'POTION_REGEN') {
        return '再生药水'
    }
    if ($tok -eq 'POTION_HEAL') {
        return '治疗药水'
    }
    return $tok
}


# ---- 目标配方表(行1 / 行2 / 行3) ----
$GRID = New-PyMapFrom ([ordered]@{
    'WARP_ENGINE_CHIP'            = @(@('MC:ENDER_PEARL', 'MC:ENDER_PEARL', 'MC:ENDER_PEARL'), @('W', 'B', 'W'), @('C', 'C', 'C'))
    'ENERGY_RECYCLER'             = @(@('MC:FLINT', 'MC:PISTON', 'MC:FLINT'), @('W', 'B', 'W'), @('C', 'C', 'C'))
    'ELECTRIC_SWORD'              = @(@('MC:REDSTONE', 'MC:DIAMOND_SWORD', 'MC:REDSTONE'), @('W', 'B', 'W'), @('C', 'C', 'C'))
    'ADVANCED_PERIPHERALS'        = @(@('MC:REDSTONE_TORCH', 'MC:ECHO_SHARD', 'MC:REDSTONE_TORCH'), @('W', 'B', 'W'), @('P', 'P', 'P'))
    'CURRENT_CORE_CHIP'           = @(@('MC:COMPARATOR', 'MC:LIGHTNING_ROD', 'MC:COMPARATOR'), @('W', 'B', 'W'), @('P', 'P', 'P'))
    'PERPETUAL_MOTION'            = @(@('MC:GOLD_BLOCK', 'MC:NETHER_STAR', 'MC:GOLD_BLOCK'), @('W', 'B', 'W'), @('G', 'G', 'G'))
    # 充能类 · 新补
    'ELECTRIC_GLOVE_CHIP'         = @(@('MC:REDSTONE', 'MC:LEATHER', 'MC:REDSTONE'), @('W', 'B', 'W'), @('P', 'P', 'P'))
    'AIRBAG_CHIP'                 = @(@('MC:LEATHER', 'MC:WHITE_WOOL', 'MC:LEATHER'), @('W', 'B', 'W'), @('P', 'P', 'P'))
    'RAILGUN_CHIP'                = @(@('MC:REDSTONE_BLOCK', 'MC:END_CRYSTAL', 'MC:REDSTONE_BLOCK'), @('W', 'B', 'W'), @('G', 'G', 'G'))
    'PRIMORDIAL_CORE_CHIP'        = @(@('MC:ECHO_SHARD', 'MC:DRAGON_HEAD', 'MC:ECHO_SHARD'), @('W', 'B', 'W'), @('G', 'G', 'G'))
    # 星光类
    'FLASHLIGHT_CHIP'             = @(@('MC:REDSTONE_LAMP', 'MC:YELLOW_STAINED_GLASS', 'MC:REDSTONE_LAMP'), @('D', 'B', 'D'), @('P', 'P', 'P'))
    'EIGHT_SIDED_DICE'            = @(@('MC:GOLD_INGOT', 'MOD:DICE', 'MC:GOLD_INGOT'), @('D', 'B', 'D'), @('C', 'C', 'C'))
    'ATM'                         = @(@('MC:GOLD_INGOT', 'MC:GOLD_INGOT', 'MC:GOLD_INGOT'), @('D', 'B', 'D'), @('C', 'C', 'C'))
    'BANK_CARD_LOW'               = @(@('MC:GOLD_INGOT', 'MC:GOLD_BLOCK', 'MC:GOLD_INGOT'), @('D', 'B', 'D'), @('C', 'C', 'C'))
    'STAR_COIN_HAMMER'            = @(@('MOD:STAR_COIN_BAG', 'MC:MACE', 'MOD:STAR_COIN_BAG'), @('D', 'B', 'D'), @('G', 'G', 'G'))
    # 治愈类
    'MEDKIT_EMERGENCY_CHIP'       = @(@('MC:SLIME_BALL', 'MC:SLIME_BALL', 'MC:SLIME_BALL'), @('R', 'B', 'R'), @('C', 'C', 'C'))
    'VITAMIN_PILL_CHIP'           = @(@('MC:FERMENTED_SPIDER_EYE', 'MC:FERMENTED_SPIDER_EYE', 'MC:FERMENTED_SPIDER_EYE'), @('R', 'B', 'R'), @('P', 'P', 'P'))
    'CUTTER_CHIP'                 = @(@('MC:IRON_SWORD', 'MC:GOLDEN_APPLE', 'MC:IRON_SWORD'), @('R', 'B', 'R'), @('P', 'P', 'P'))
    'BUFFER_SHIELD'               = @(@('MC:DIAMOND', 'MC:SHIELD', 'MC:DIAMOND'), @('R', 'B', 'R'), @('C', 'C', 'C'))
    'CANDY_CHIP'                  = @(@('MC:SUGAR', 'MC:COOKIE', 'MC:SUGAR'), @('R', 'B', 'R'), @('P', 'P', 'P'))
    'FRIENDSHIP_BADGE'            = @(@('POTION_HEAL', 'MC:ENCHANTED_GOLDEN_APPLE', 'POTION_HEAL'), @('R', 'B', 'R'), @('P', 'P', 'P'))
    'BIG_BOWL_STEW_CHIP'          = @(@('MC:BOWL', 'MC:COOKED_BEEF', 'MC:BOWL'), @('R', 'B', 'R'), @('G', 'G', 'G'))
    # 标记类
    'SCOPE_CHIP'                  = @(@('MC:AMETHYST_SHARD', 'MC:COPPER_INGOT', 'MC:AMETHYST_SHARD'), @('M', 'B', 'M'), @('P', 'P', 'P'))
    'TARGET_CHIP'                 = @(@('MC:TARGET', 'MC:TARGET', 'MC:TARGET'), @('M', 'B', 'M'), @('C', 'C', 'C'))
    'MARKER_SPRAYER_CHIP'         = @(@('MC:NETHER_WART', 'MC:LAPIS_LAZULI', 'MC:NETHER_WART'), @('M', 'B', 'M'), @('C', 'C', 'C'))
    'NINJA_STAR_CHIP'             = @(@('MC:NETHERITE_INGOT', 'MC:REDSTONE_BLOCK', 'MC:NETHERITE_INGOT'), @('M', 'B', 'M'), @('G', 'G', 'G'))
    'HAND_FAN_SMALL_CHIP'         = @(@('MC:FEATHER', 'MC:BAMBOO', 'MC:FEATHER'), @('M', 'B', 'M'), @('C', 'C', 'C'))
    'MAGIC_QUIVER'                = @(@('MC:SPECTRAL_ARROW', 'MC:ECHO_SHARD', 'MC:SPECTRAL_ARROW'), @('M', 'B', 'M'), @('P', 'P', 'P'))
    # 无流派(基底)
    'MAGIC_TOME_CHIP'             = @(@('MC:WRITABLE_BOOK', 'MC:ECHO_SHARD', 'MC:WRITABLE_BOOK'), @('MC:EXPERIENCE_BOTTLE', 'B', 'MC:EXPERIENCE_BOTTLE'), @('P', 'P', 'P'))
    'BIG_BACKPACK_CHIP'           = @(@('MC:LEATHER', 'MC:LEATHER', 'MC:LEATHER'), @('MC:IRON_INGOT', 'B', 'MC:IRON_INGOT'), @('P', 'P', 'P'))
    'BOXING_GLOVES_LOW'           = @(@('MC:SPONGE', 'MC:SPONGE', 'MC:SPONGE'), @('MC:LEATHER', 'B', 'MC:LEATHER'), @('C', 'C', 'C'))
    'SPEED_SKATES_LOW'            = @(@('MC:BLUE_ICE', 'MC:LEATHER_BOOTS', 'MC:BLUE_ICE'), @('MC:IRON_INGOT', 'B', 'MC:IRON_INGOT'), @('C', 'C', 'C'))
    'MOTO_HELMET_LOW'             = @(@('MC:GLASS_PANE', 'MC:LEATHER_HELMET', 'MC:GLASS_PANE'), @('MC:IRON_INGOT', 'B', 'MC:IRON_INGOT'), @('C', 'C', 'C'))
    'SANDWICH_LOW'                = @(@('MC:COOKIE', 'MC:EGG', 'MC:COOKIE'), @('MC:MILK_BUCKET', 'B', 'MC:MILK_BUCKET'), @('C', 'C', 'C'))
    'ADRENALINE_LOW'              = @(@('POTION_REGEN', 'MC:NETHER_STAR', 'POTION_REGEN'), @('MC:WITHER_ROSE', 'B', 'MC:WITHER_ROSE'), @('P', 'P', 'P'))
    'SATELLITE_CHIP'              = @(@('MC:REDSTONE_BLOCK', 'MOD:ORBITAL_STRIKE_CARD', 'MC:REDSTONE_BLOCK'), @('MOD:ORBITAL_STRIKE_CARD', 'B', 'MOD:ORBITAL_STRIKE_CARD'), @('G', 'G', 'G'))
    'CURSED_SWORD'                = @(@('MC:GOLDEN_SWORD', 'MC:CRYING_OBSIDIAN', 'MC:GOLDEN_SWORD'), @('MC:POPPED_CHORUS_FRUIT', 'B', 'MC:POPPED_CHORUS_FRUIT'), @('C', 'C', 'C'))
    'REVENGE_HALBERD'             = @(@('MC:WITHER_SKELETON_SKULL', 'MC:DIAMOND_SWORD', 'MC:WITHER_SKELETON_SKULL'), @('MC:DIAMOND_SWORD', 'B', 'MC:DIAMOND_SWORD'), @('P', 'P', 'P'))
    'PIERCING_GUN'                = @(@('MC:NETHERITE_INGOT', 'MC:CONDUIT', 'MC:NETHERITE_INGOT'), @('MC:ECHO_SHARD', 'B', 'MC:ECHO_SHARD'), @('G', 'G', 'G'))
    # 无流派 · 新补
    'BOOKMARK_CHIP'               = @(@('MC:PAPER', 'MC:LEATHER', 'MC:PAPER'), @('MC:STRING', 'B', 'MC:STRING'), @('C', 'C', 'C'))
    'MEMBER_RECOMMENDATION_CHIP'  = @(@('MC:PAPER', 'MC:INK_SAC', 'MC:PAPER'), @('MC:FEATHER', 'B', 'MC:FEATHER'), @('C', 'C', 'C'))
    'PIGGY_BANK_CHIP'             = @(@('MC:GOLD_INGOT', 'MC:PORKCHOP', 'MC:GOLD_INGOT'), @('MC:BRICK', 'B', 'MC:BRICK'), @('C', 'C', 'C'))
    'SMART_WATCH_CHIP'            = @(@('MC:GOLD_INGOT', 'MC:CLOCK', 'MC:GOLD_INGOT'), @('MC:REDSTONE', 'B', 'MC:REDSTONE'), @('P', 'P', 'P'))
    'WHETSTONE_CHIP'              = @(@('MC:NETHERITE_INGOT', 'MC:GRINDSTONE', 'MC:NETHERITE_INGOT'), @('MC:FLINT', 'B', 'MC:FLINT'), @('P', 'P', 'P'))
})

# 进阶关系: 进阶筹码 -> 上一等级基础筹码
$UPGRADE = New-PyMapFrom ([ordered]@{
    'BOXING_GLOVES_MEDIUM'   = 'BOXING_GLOVES_LOW'; 'BOXING_GLOVES_HIGH' = 'BOXING_GLOVES_MEDIUM'
    'SPEED_SKATES_MEDIUM'    = 'SPEED_SKATES_LOW'; 'SPEED_SKATES_HIGH' = 'SPEED_SKATES_MEDIUM'
    'MOTO_HELMET_MEDIUM'     = 'MOTO_HELMET_LOW'; 'MOTO_HELMET_HIGH' = 'MOTO_HELMET_MEDIUM'
    'SANDWICH_MEDIUM'        = 'SANDWICH_LOW'; 'SANDWICH_HIGH' = 'SANDWICH_MEDIUM'
    'HAND_FAN_BIG_CHIP'      = 'HAND_FAN_SMALL_CHIP'
    'BANK_CARD_HIGH'         = 'BANK_CARD_LOW'; 'BANK_CARD_UNLIMITED' = 'BANK_CARD_HIGH'
    'CUTTER_BLADE_CHIP'      = 'CUTTER_CHIP'; 'EAGLE_SCOPE_CHIP' = 'SCOPE_CHIP'
    'MEDKIT_COMPLETE_CHIP'   = 'MEDKIT_EMERGENCY_CHIP'; 'ADRENALINE_HIGH' = 'ADRENALINE_LOW'
})

$NEW_CHIPS = @('ELECTRIC_GLOVE_CHIP', 'AIRBAG_CHIP', 'RAILGUN_CHIP', 'PRIMORDIAL_CORE_CHIP',
    'BIG_BOWL_STEW_CHIP', 'BOOKMARK_CHIP', 'MEMBER_RECOMMENDATION_CHIP',
    'PIGGY_BANK_CHIP', 'SMART_WATCH_CHIP', 'WHETSTONE_CHIP')


function target {
    <#
    期望配方。进阶筹码走「通用升级」模板;其余取 GRID。
    #>
    param([string]$const)
    if (Test-PyMapKey $UPGRADE $const) {
        $base = $UPGRADE[$const]
        $rarity = $chips[$const]['rarity']
        $tpl = $UPGRADE_TEMPLATE[$rarity]
        $g = @()
        foreach ($row in $tpl) {
            $copy = @()
            foreach ($t in $row) { $copy += $t }
            $g += , $copy
        }
        $g[1][1] = 'MOD:' + $base
        $g += , @($RARITY_ITEM[$rarity], $RARITY_ITEM[$rarity], $RARITY_ITEM[$rarity])
        return , $g
    }
    $out = @()
    foreach ($row in $GRID[$const]) {
        $copy = @()
        foreach ($t in $row) { $copy += $t }
        $out += , $copy
    }
    return , $out
}


function flat {
    param($g)
    $rows = @()
    foreach ($row in $g) {
        $cells = @()
        foreach ($t in $row) { $cells += (resolve $t) }
        $rows += ($cells -join '·')
    }
    return ($rows -join ' / ')
}


function sig {
    <#
    配方唯一签名:图案 + 成分(用于冲突检测)
    #>
    param($g)
    $rows = @()
    foreach ($row in $g) {
        $s = ''
        foreach ($t in $row) { $s += $t }
        $rows += $s
    }
    return ($rows -join '|')
}


# ---- 解析现有配方 ----
function parse_file {
    param([string]$Path)
    $src = Read-PyTextRaw $Path
    $out = New-PyMap
    $RE = 'ShapedRecipeBuilder\.shaped\(RecipeCategory\.MISC,\s*ModItems\.(\w+)\.get\(\)(?:,\s*\d+)?\)(.*?)\.save\(output[^;]*\);'
    foreach ($m in [regex]::Matches($src, $RE, [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
        $const = $m.Groups[1].Value
        $body = $m.Groups[2].Value
        if (-not (Test-PyMapKey $chips $const)) {
            continue
        }
        $pats = @()
        foreach ($pm in [regex]::Matches($body, '\.pattern\("([^"]*)"\)')) { $pats += $pm.Groups[1].Value }
        if ($pats.Count -ne 3) {
            continue
        }
        $defines = @{}
        $parts = [regex]::Split($body, '\.define\(')
        for ($i = 1; $i -lt $parts.Count; $i++) {
            $p = $parts[$i]
            $km = [regex]::Match($p, "^\s*'(.)'\s*,\s*(.*)", [System.Text.RegularExpressions.RegexOptions]::Singleline)
            if (-not $km.Success) {
                continue
            }
            $key = $km.Groups[1].Value
            $val = ([regex]::Split($km.Groups[2].Value, '\.(?:define|unlockedBy)\('))[0]
            if ($val.Contains('Potions.HEALING')) {
                $defines[$key] = 'POTION_HEAL'
            }
            elseif ($val.Contains('Potions.REGENERATION') -or $val.Contains('minecraft:regeneration')) {
                $defines[$key] = 'POTION_REGEN'
            }
            else {
                $modm = @()
                foreach ($x in [regex]::Matches($val, 'ModItems\.(\w+)')) { $modm += $x.Groups[1].Value }
                $vanm = @()
                foreach ($x in [regex]::Matches($val, 'Items\.(\w+)')) { $vanm += $x.Groups[1].Value }
                if ($modm.Count -gt 0) {
                    $c = $modm[$modm.Count - 1]
                    $rev = @{}
                    foreach ($e in $SYM_CONST.GetEnumerator()) { $rev[$e.Value] = $e.Key }
                    if ($rev.ContainsKey($c)) { $defines[$key] = $rev[$c] } else { $defines[$key] = 'MOD:' + $c }
                }
                elseif ($vanm.Count -gt 0) {
                    $defines[$key] = 'MC:' + $vanm[$vanm.Count - 1]
                }
                else {
                    $defines[$key] = '?'
                }
            }
        }
        $grid = @()
        foreach ($row in $pats) {
            $line = @()
            foreach ($ch in $row.ToCharArray()) {
                if ($defines.ContainsKey([string]$ch)) { $line += $defines[[string]$ch] } else { $line += '-' }
            }
            $grid += , $line
        }
        $out[$const] = $grid
    }
    return , $out
}


# ---- Java 代码生成 ----
$PREF = New-PyMapFrom ([ordered]@{
    'B' = 'B'; 'C' = 'C'; 'P' = 'P'; 'G' = 'G'; 'W' = 'W'; 'R' = 'R'; 'D' = 'D'; 'M' = 'M'
    'MOD:ORBITAL_STRIKE_CARD' = 'O'; 'MOD:STAR_COIN_BAG' = 'S'
})
$POOL = 'XLGHSPTIDEFJKMNOQRUVYZ'.ToCharArray()


function _letters {
    param($g, [string]$const)
    $order = @()
    foreach ($row in $g) {
        foreach ($t in $row) {
            if (-not ($order -contains $t)) { $order += $t }
        }
    }
    $center = $g[1][1]
    $out = New-PyMap
    $used = New-Object System.Collections.Generic.HashSet[string]
    foreach ($t in $order) {
        $want = $null
        if ($t -eq $center -and (Test-PyMapKey $UPGRADE $const)) {
            $want = 'T'
        }
        elseif ($t -is [string] -and $t.StartsWith('POTION')) {
            $want = 'Z'
        }
        elseif (Test-PyMapKey $SYM $t) {
            $want = $t
        }
        elseif (Test-PyMapKey $PREF $t) {
            $want = $PREF[$t]
        }
        if ($null -eq $want -or $used.Contains([string]$want)) {
            foreach ($c in $POOL) {
                if (-not $used.Contains([string]$c)) { $want = [string]$c; break }
            }
        }
        $out[$t] = $want
        [void]$used.Add([string]$want)
    }
    return , $out
}


function _potion_define {
    param([string]$letter, [string]$kind, [string]$dialect)
    $ind = '                '
    if ($dialect -eq 'neo') {
        $potion = $(if ($kind -eq 'REGEN') { 'REGENERATION' } else { 'HEALING' })
        return ($ind + ".define('" + $letter + "', net.neoforged.neoforge.common.crafting.DataComponentIngredient.of(`n" +
            $ind + "        true, net.minecraft.core.component.DataComponents.POTION_CONTENTS,`n" +
            $ind + "        new net.minecraft.world.item.alchemy.PotionContents(net.minecraft.world.item.alchemy.Potions." + $potion + "),`n" +
            $ind + "        Items.POTION))")
    }
    if ($kind -eq 'REGEN') {
        return ($ind + ".define('" + $letter + "', net.minecraftforge.common.crafting.PartialNBTIngredient.of(Items.POTION,`n" +
            $ind + "        potionTag(`"minecraft:regeneration`")))")
    }
    return ($ind + ".define('" + $letter + "', net.minecraftforge.common.crafting.StrictNBTIngredient.of(`n" +
        $ind + "        net.minecraft.world.item.alchemy.PotionUtils.setPotion(`n" +
        $ind + "                new net.minecraft.world.item.ItemStack(Items.POTION),`n" +
        $ind + "                net.minecraft.world.item.alchemy.Potions.HEALING)))")
}


function emit_block {
    <#
    生成完整 ShapedRecipeBuilder 代码块(含前置注释行)
    #>
    param([string]$const, [string]$dialect)
    $g = _plat (target $const) $dialect
    $letters = _letters $g $const
    $ind = '                '
    $save = $(if ($dialect -eq 'neo') { '.save(output);' } else { '.save(output::accept);' })
    $lines = @()
    $comment = ('        ' + '// ' + (cn $const) + ':上排 ' +
        (@($g[0] | ForEach-Object { resolve $_ }) -join '·') + '｜中排 ' +
        (@($g[1] | ForEach-Object { resolve $_ }) -join '·') + '｜下排 ' +
        (@($g[2] | ForEach-Object { resolve $_ }) -join '·'))
    if (Test-PyMapKey $UPGRADE $const) {
        $comment += ('(进阶自 ' + (cn $UPGRADE[$const]) + ')')
    }
    $comment += (' [' + $RARITY_CN[$chips[$const]['rarity']] + '·' + $(if (Test-PyMapKey $stream_of $const) { $stream_of[$const] } else { '?' }) + ']')
    $lines += $comment
    $lines += ('        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.' + $const + '.get())')
    foreach ($row in $g) {
        $pat = ''
        foreach ($t in $row) { $pat += $letters[$t] }
        $lines += ($ind + '.pattern("' + $pat + '")')
    }
    foreach ($e in $letters.GetEnumerator()) {
        $t = $e.Key
        $lt = $e.Value
        if ($t -is [string] -and $t.StartsWith('MOD:')) {
            $lines += ($ind + ".define('" + $lt + "', ModItems." + $t.Substring(4) + ".get())")
        }
        elseif ($t -is [string] -and $t.StartsWith('MC:')) {
            $note = ''
            if ((Test-PyMapKey $PLATFORM_NOTE $dialect) -and (Test-PyMapKey $PLATFORM_NOTE[$dialect] $t)) { $note = $PLATFORM_NOTE[$dialect][$t] }
            $lines += ($ind + ".define('" + $lt + "', Items." + $t.Substring(3) + ")" + $note)
        }
        elseif ($t -eq 'POTION_REGEN') {
            $lines += (_potion_define $lt 'REGEN' $dialect)
        }
        elseif ($t -eq 'POTION_HEAL') {
            $lines += (_potion_define $lt 'HEAL' $dialect)
        }
        else {
            $lines += ($ind + ".define('" + $lt + "', ModItems." + $SYM_CONST[$t] + ".get())")
        }
    }
    if (Test-PyMapKey $UPGRADE $const) {
        $base = $UPGRADE[$const]
        $lines += ($ind + '.unlockedBy("has_' + $base.ToLower() + '", has(ModItems.' + $base + '.get()))')
    }
    else {
        $lines += ($ind + '.unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))')
    }
    $lines += ($ind + $save)
    return ($lines -join "`n")
}


Export-ModuleMember -Function `
    _plat, cn, resolve, target, flat, sig, parse_file, _letters, _potion_define, emit_block, `
    Read-PyText, Read-PyTextRaw, New-PyMap, New-PyMapFrom, Test-PyMapKey `
    -Variable `
    ROOT, NEO, FORGE, RES, chips, stream_of, RARITY_ITEM, RARITY_CN, UPGRADE_TEMPLATE, V, SYM, SYM_CONST, `
    PLATFORM_OVERRIDE, PLATFORM_NOTE, GRID, UPGRADE, NEW_CHIPS, PREF, POOL
