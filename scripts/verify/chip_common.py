# -*- coding: utf-8 -*-
"""筹码配方规范 — 公共数据与逻辑(计划书与校验脚本共用)。

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
  (scripts/verify/verify_chip_recipes.py)引用。改动前先确认双版本 ModRecipeProvider.java
  的真实内容,勿仅凭此表推断。
"""
import re, json, io, os

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
NEO = os.path.join(ROOT, "neoforge-1.21.1", "src", "main", "java", "com", "merlinkitsune", "astral_dice")
FORGE = os.path.join(ROOT, "forge-1.20.1", "src", "main", "java", "com", "merlinkitsune", "astral_dice")
RES = os.path.join(ROOT, "neoforge-1.21.1", "src", "main", "resources")

items_src = io.open(os.path.join(NEO, "item", "ModItems.java"), encoding="utf-8").read()
tabs_src = io.open(os.path.join(NEO, "init", "ModCreativeTabs.java"), encoding="utf-8").read()
zh = json.load(io.open(os.path.join(RES, "assets", "astral_dice", "lang", "zh_cn.json"), encoding="utf-8"))

chips = {}
for _m in re.finditer(r'public static final DeferredItem<Item>\s+(\w+)\s*=\s*registerItem\("([^"]+)"\s*,\s*\(\)\s*->\s*new\s+\w+\((.*?)\)\);', items_src, re.S):
    if "chip" not in _m.group(2) or _m.group(2).startswith("blank_chip"):
        continue
    _rm = re.search(r'rarity\(Rarity\.(\w+)\)', _m.group(3))
    chips[_m.group(1)] = {"id": _m.group(2), "rarity": _rm.group(1) if _rm else "COMMON"}

stream_of = {}
for _key, _name in [("星光类", "星光"), ("治愈类", "治愈"), ("标记类", "标记"), ("充能类", "充能"), ("无流派", "无流派")]:
    _mm = re.search(r'// === %s ===(.*?)(?=// === |\}\)\.build\(\))' % _key, tabs_src, re.S)
    if _mm:
        for _cm in re.finditer(r'output\.accept\(ModItems\.(\w+)\.get\(\)\)', _mm.group(1)):
            stream_of[_cm.group(1)] = _name

RARITY_ITEM = {"RARE": "C", "EPIC": "P", "UNCOMMON": "G"}
RARITY_CN = {"RARE": "稀有", "EPIC": "史诗", "UNCOMMON": "传奇", "COMMON": "—"}

# 进阶筹码「通用升级」模板(与 e096513 原版一致,2026-09-11 回滚后生效)
# 行1/行2两侧按品质固定,行2中位 = 上一等级筹码,行3 = 本品质物品
UPGRADE_TEMPLATE = {
    "EPIC":     [["MC:LAPIS_LAZULI", "MC:GOLD_INGOT", "MC:LAPIS_LAZULI"],
                 ["MC:GOLD_INGOT", None, "MC:GOLD_INGOT"]],      # 蓝->紫
    "UNCOMMON": [["MC:REDSTONE", "MC:DIAMOND", "MC:REDSTONE"],
                 ["MC:DIAMOND", None, "MC:DIAMOND"]],           # 紫->金
}

V = {
 "REDSTONE_LAMP": "红石灯", "YELLOW_STAINED_GLASS": "黄色染色玻璃", "IRON_INGOT": "铁锭", "GOLD_INGOT": "金锭",
 "GOLD_BLOCK": "金块", "LAPIS_LAZULI": "青金石", "GOLDEN_APPLE": "金苹果", "IRON_SWORD": "铁剑", "DIAMOND": "钻石",
 "SHIELD": "盾牌", "SPONGE": "海绵", "LEATHER": "皮革", "BLUE_ICE": "蓝冰", "LEATHER_BOOTS": "皮革靴子",
 "GLASS_PANE": "玻璃板", "LEATHER_HELMET": "皮革头盔", "COOKIE": "曲奇", "EGG": "鸡蛋", "MILK_BUCKET": "奶桶",
 "SUGAR": "糖", "CARVED_PUMPKIN": "雕刻南瓜", "AMETHYST_SHARD": "紫水晶碎片", "COPPER_INGOT": "铜锭",
 "OAK_BUTTON": "橡木按钮", "NETHER_WART": "下界疣", "TARGET": "标靶", "SLIME_BALL": "粘液球",
 "FERMENTED_SPIDER_EYE": "发酵蛛眼", "ENCHANTED_GOLDEN_APPLE": "附魔金苹果", "WITHER_ROSE": "凋零玫瑰",
 "NETHER_STAR": "下界之星", "WITHER_SKELETON_SKULL": "凋灵骷髅头", "DIAMOND_SWORD": "钻石剑",
 "POPPED_CHORUS_FRUIT": "爆裂紫颂果", "CRYING_OBSIDIAN": "哭泣的黑曜石", "GOLDEN_SWORD": "金剑",
 "NETHERITE_INGOT": "下界合金锭", "CONDUIT": "潮涌核心", "ECHO_SHARD": "回响碎片", "SPECTRAL_ARROW": "光灵箭",
 "WRITABLE_BOOK": "书与笔", "EXPERIENCE_BOTTLE": "附魔瓶", "REDSTONE_BLOCK": "红石块", "REDSTONE": "红石粉",
 "REDSTONE_TORCH": "红石火把", "ENDER_PEARL": "末影珍珠", "FLINT": "燧石", "PISTON": "活塞",
 "LIGHTNING_ROD": "避雷针", "COMPARATOR": "红石比较器", "MACE": "重锤", "ANVIL": "铁砧", "PAPER": "纸", "INK_SAC": "墨囊",
 "FEATHER": "羽毛", "STRING": "线", "PORKCHOP": "生猪排", "BRICK": "红砖", "CLOCK": "时钟",
 "GRINDSTONE": "砂轮", "BOWL": "碗", "COOKED_BEEF": "牛排", "WHITE_WOOL": "白色羊毛", "IRON_BLOCK": "铁块",
 "BAMBOO": "竹子", "END_CRYSTAL": "末地水晶", "DRAGON_HEAD": "龙首",
}
SYM = {"C": "星币", "P": "星盘", "G": "黄金星盘", "B": "空白筹码", "W": "导电线材",
       "R": "再生试剂", "D": "星币尘", "M": "标记涂料"}
SYM_CONST = {"C": "STAR_COIN", "P": "STAR_PLATE", "G": "GOLDEN_STAR_PLATE", "B": "BLANK_CHIP",
             "W": "CONDUCTIVE_WIRE", "R": "REGENERATION_REAGENT", "D": "STAR_COIN_DUST", "M": "MARK_PAINT"}

# ---- 平台差异:1.20.1 无 MC 1.21 新增物品时的等价替代(与 docs/compat-1.20.1-forge.md 一致) ----
PLATFORM_OVERRIDE = {"forge": {"MC:MACE": "MC:ANVIL"}}
PLATFORM_NOTE = {"forge": {"MC:ANVIL": " // 1.20.1 无重锤(1.21 新增),以铁砧替代"}}


def _plat(g, dialect):
    ov = PLATFORM_OVERRIDE.get(dialect, {})
    return [[ov.get(t, t) for t in row] for row in g]


def cn(const):
    if const == "DICE":
        return "骰子"
    if const == "ORBITAL_STRIKE_CARD":
        return "轨道炮(卡牌)"
    if const == "STAR_COIN_BAG":
        return "星币袋"
    if const not in chips:
        return const
    return zh.get("item.astral_dice." + chips[const]["id"], chips[const]["id"])


def resolve(tok):
    if tok in SYM:
        return SYM[tok]
    if tok.startswith("MC:"):
        return V.get(tok[3:], tok[3:])
    if tok.startswith("MOD:"):
        return cn(tok[4:])
    if tok == "POTION_REGEN":
        return "再生药水"
    if tok == "POTION_HEAL":
        return "治疗药水"
    return tok


# ---- 目标配方表(行1 / 行2 / 行3) ----
GRID = {
 "WARP_ENGINE_CHIP":    [["MC:ENDER_PEARL"] * 3, ["W", "B", "W"], ["C"] * 3],
 "ENERGY_RECYCLER":     [["MC:FLINT", "MC:PISTON", "MC:FLINT"], ["W", "B", "W"], ["C"] * 3],
 "ELECTRIC_SWORD":      [["MC:REDSTONE", "MC:DIAMOND_SWORD", "MC:REDSTONE"], ["W", "B", "W"], ["C"] * 3],
 "ADVANCED_PERIPHERALS": [["MC:REDSTONE_TORCH", "MC:ECHO_SHARD", "MC:REDSTONE_TORCH"], ["W", "B", "W"], ["P"] * 3],
 "CURRENT_CORE_CHIP":   [["MC:COMPARATOR", "MC:LIGHTNING_ROD", "MC:COMPARATOR"], ["W", "B", "W"], ["P"] * 3],
 "PERPETUAL_MOTION":    [["MC:GOLD_BLOCK", "MC:NETHER_STAR", "MC:GOLD_BLOCK"], ["W", "B", "W"], ["G"] * 3],
 # 充能类 · 新补
 "ELECTRIC_GLOVE_CHIP": [["MC:REDSTONE", "MC:LEATHER", "MC:REDSTONE"], ["W", "B", "W"], ["P"] * 3],
 "AIRBAG_CHIP":         [["MC:LEATHER", "MC:WHITE_WOOL", "MC:LEATHER"], ["W", "B", "W"], ["P"] * 3],
 "RAILGUN_CHIP":        [["MC:REDSTONE_BLOCK", "MC:END_CRYSTAL", "MC:REDSTONE_BLOCK"], ["W", "B", "W"], ["G"] * 3],
 "PRIMORDIAL_CORE_CHIP": [["MC:ECHO_SHARD", "MC:DRAGON_HEAD", "MC:ECHO_SHARD"], ["W", "B", "W"], ["G"] * 3],
 # 星光类
 "FLASHLIGHT_CHIP":     [["MC:REDSTONE_LAMP", "MC:YELLOW_STAINED_GLASS", "MC:REDSTONE_LAMP"], ["D", "B", "D"], ["P"] * 3],
 "EIGHT_SIDED_DICE":    [["MC:GOLD_INGOT", "MOD:DICE", "MC:GOLD_INGOT"], ["D", "B", "D"], ["C"] * 3],
 "ATM":                 [["MC:GOLD_INGOT"] * 3, ["D", "B", "D"], ["C"] * 3],
 "BANK_CARD_LOW":       [["MC:GOLD_INGOT", "MC:GOLD_BLOCK", "MC:GOLD_INGOT"], ["D", "B", "D"], ["C"] * 3],
 "STAR_COIN_HAMMER":    [["MOD:STAR_COIN_BAG", "MC:MACE", "MOD:STAR_COIN_BAG"], ["D", "B", "D"], ["G"] * 3],
 # 治愈类
 "MEDKIT_EMERGENCY_CHIP": [["MC:SLIME_BALL"] * 3, ["R", "B", "R"], ["C"] * 3],
 "VITAMIN_PILL_CHIP":   [["MC:FERMENTED_SPIDER_EYE"] * 3, ["R", "B", "R"], ["P"] * 3],
 "CUTTER_CHIP":         [["MC:IRON_SWORD", "MC:GOLDEN_APPLE", "MC:IRON_SWORD"], ["R", "B", "R"], ["P"] * 3],
 "BUFFER_SHIELD":       [["MC:DIAMOND", "MC:SHIELD", "MC:DIAMOND"], ["R", "B", "R"], ["C"] * 3],
 "CANDY_CHIP":          [["MC:SUGAR", "MC:COOKIE", "MC:SUGAR"], ["R", "B", "R"], ["P"] * 3],
 "FRIENDSHIP_BADGE":    [["POTION_HEAL", "MC:ENCHANTED_GOLDEN_APPLE", "POTION_HEAL"], ["R", "B", "R"], ["P"] * 3],
 "BIG_BOWL_STEW_CHIP":  [["MC:BOWL", "MC:COOKED_BEEF", "MC:BOWL"], ["R", "B", "R"], ["G"] * 3],
 # 标记类
 "SCOPE_CHIP":          [["MC:AMETHYST_SHARD", "MC:COPPER_INGOT", "MC:AMETHYST_SHARD"], ["M", "B", "M"], ["P"] * 3],
 "TARGET_CHIP":         [["MC:TARGET"] * 3, ["M", "B", "M"], ["C"] * 3],
 "MARKER_SPRAYER_CHIP": [["MC:NETHER_WART", "MC:LAPIS_LAZULI", "MC:NETHER_WART"], ["M", "B", "M"], ["C"] * 3],
 "NINJA_STAR_CHIP":     [["MC:NETHERITE_INGOT", "MC:REDSTONE_BLOCK", "MC:NETHERITE_INGOT"], ["M", "B", "M"], ["G"] * 3],
 "HAND_FAN_SMALL_CHIP": [["MC:FEATHER", "MC:BAMBOO", "MC:FEATHER"], ["M", "B", "M"], ["C"] * 3],
 "MAGIC_QUIVER":        [["MC:SPECTRAL_ARROW", "MC:ECHO_SHARD", "MC:SPECTRAL_ARROW"], ["M", "B", "M"], ["P"] * 3],
 # 无流派(基底)
 "MAGIC_TOME_CHIP":     [["MC:WRITABLE_BOOK", "MC:ECHO_SHARD", "MC:WRITABLE_BOOK"], ["MC:EXPERIENCE_BOTTLE", "B", "MC:EXPERIENCE_BOTTLE"], ["P"] * 3],
 "BIG_BACKPACK_CHIP":   [["MC:LEATHER"] * 3, ["MC:IRON_INGOT", "B", "MC:IRON_INGOT"], ["P"] * 3],
 "BOXING_GLOVES_LOW":   [["MC:SPONGE"] * 3, ["MC:LEATHER", "B", "MC:LEATHER"], ["C"] * 3],
 "SPEED_SKATES_LOW":    [["MC:BLUE_ICE", "MC:LEATHER_BOOTS", "MC:BLUE_ICE"], ["MC:IRON_INGOT", "B", "MC:IRON_INGOT"], ["C"] * 3],
 "MOTO_HELMET_LOW":     [["MC:GLASS_PANE", "MC:LEATHER_HELMET", "MC:GLASS_PANE"], ["MC:IRON_INGOT", "B", "MC:IRON_INGOT"], ["C"] * 3],
 "SANDWICH_LOW":        [["MC:COOKIE", "MC:EGG", "MC:COOKIE"], ["MC:MILK_BUCKET", "B", "MC:MILK_BUCKET"], ["C"] * 3],
 "ADRENALINE_LOW":      [["POTION_REGEN", "MC:NETHER_STAR", "POTION_REGEN"], ["MC:WITHER_ROSE", "B", "MC:WITHER_ROSE"], ["P"] * 3],
 "SATELLITE_CHIP":      [["MC:REDSTONE_BLOCK", "MOD:ORBITAL_STRIKE_CARD", "MC:REDSTONE_BLOCK"], ["MOD:ORBITAL_STRIKE_CARD", "B", "MOD:ORBITAL_STRIKE_CARD"], ["G"] * 3],
 "CURSED_SWORD":        [["MC:GOLDEN_SWORD", "MC:CRYING_OBSIDIAN", "MC:GOLDEN_SWORD"], ["MC:POPPED_CHORUS_FRUIT", "B", "MC:POPPED_CHORUS_FRUIT"], ["C"] * 3],
 "REVENGE_HALBERD":     [["MC:WITHER_SKELETON_SKULL", "MC:DIAMOND_SWORD", "MC:WITHER_SKELETON_SKULL"], ["MC:DIAMOND_SWORD", "B", "MC:DIAMOND_SWORD"], ["P"] * 3],
 "PIERCING_GUN":        [["MC:NETHERITE_INGOT", "MC:CONDUIT", "MC:NETHERITE_INGOT"], ["MC:ECHO_SHARD", "B", "MC:ECHO_SHARD"], ["G"] * 3],
 # 无流派 · 新补
 "BOOKMARK_CHIP":       [["MC:PAPER", "MC:LEATHER", "MC:PAPER"], ["MC:STRING", "B", "MC:STRING"], ["C"] * 3],
 "MEMBER_RECOMMENDATION_CHIP": [["MC:PAPER", "MC:INK_SAC", "MC:PAPER"], ["MC:FEATHER", "B", "MC:FEATHER"], ["C"] * 3],
 "PIGGY_BANK_CHIP":     [["MC:GOLD_INGOT", "MC:PORKCHOP", "MC:GOLD_INGOT"], ["MC:BRICK", "B", "MC:BRICK"], ["C"] * 3],
 "SMART_WATCH_CHIP":    [["MC:GOLD_INGOT", "MC:CLOCK", "MC:GOLD_INGOT"], ["MC:REDSTONE", "B", "MC:REDSTONE"], ["P"] * 3],
 "WHETSTONE_CHIP":      [["MC:NETHERITE_INGOT", "MC:GRINDSTONE", "MC:NETHERITE_INGOT"], ["MC:FLINT", "B", "MC:FLINT"], ["P"] * 3],
}

# 进阶关系: 进阶筹码 -> 上一等级基础筹码
UPGRADE = {
 "BOXING_GLOVES_MEDIUM": "BOXING_GLOVES_LOW", "BOXING_GLOVES_HIGH": "BOXING_GLOVES_MEDIUM",
 "SPEED_SKATES_MEDIUM": "SPEED_SKATES_LOW", "SPEED_SKATES_HIGH": "SPEED_SKATES_MEDIUM",
 "MOTO_HELMET_MEDIUM": "MOTO_HELMET_LOW", "MOTO_HELMET_HIGH": "MOTO_HELMET_MEDIUM",
 "SANDWICH_MEDIUM": "SANDWICH_LOW", "SANDWICH_HIGH": "SANDWICH_MEDIUM",
 "HAND_FAN_BIG_CHIP": "HAND_FAN_SMALL_CHIP",
 "BANK_CARD_HIGH": "BANK_CARD_LOW", "BANK_CARD_UNLIMITED": "BANK_CARD_HIGH",
 "CUTTER_BLADE_CHIP": "CUTTER_CHIP", "EAGLE_SCOPE_CHIP": "SCOPE_CHIP",
 "MEDKIT_COMPLETE_CHIP": "MEDKIT_EMERGENCY_CHIP", "ADRENALINE_HIGH": "ADRENALINE_LOW",
}

NEW_CHIPS = ["ELECTRIC_GLOVE_CHIP", "AIRBAG_CHIP", "RAILGUN_CHIP", "PRIMORDIAL_CORE_CHIP",
             "BIG_BOWL_STEW_CHIP", "BOOKMARK_CHIP", "MEMBER_RECOMMENDATION_CHIP",
             "PIGGY_BANK_CHIP", "SMART_WATCH_CHIP", "WHETSTONE_CHIP"]


def target(const):
    """期望配方。进阶筹码走「通用升级」模板;其余取 GRID。"""
    if const in UPGRADE:
        base = UPGRADE[const]
        rarity = chips[const]["rarity"]
        tpl = UPGRADE_TEMPLATE[rarity]
        g = [row[:] for row in tpl]
        g[1][1] = "MOD:" + base
        g.append([RARITY_ITEM[rarity]] * 3)
        return g
    return [row[:] for row in GRID[const]]


def flat(g):
    return " / ".join("·".join(resolve(t) for t in row) for row in g)


def sig(g):
    """配方唯一签名:图案 + 成分(用于冲突检测)"""
    return "|".join("".join(t for t in row) for row in g)


# ---- 解析现有配方 ----
def parse_file(path):
    src = io.open(path, encoding="utf-8", newline="").read()
    out = {}
    for m in re.finditer(r'ShapedRecipeBuilder\.shaped\(RecipeCategory\.MISC,\s*ModItems\.(\w+)\.get\(\)(?:,\s*\d+)?\)(.*?)\.save\(output[^;]*\);', src, re.S):
        const, body = m.group(1), m.group(2)
        if const not in chips:
            continue
        pats = re.findall(r'\.pattern\("([^"]*)"\)', body)
        if len(pats) != 3:
            continue
        defines = {}
        for p in re.split(r'\.define\(', body)[1:]:
            km = re.match(r"\s*'(.)'\s*,\s*(.*)", p, re.S)
            if not km:
                continue
            key, val = km.group(1), re.split(r'\.(?:define|unlockedBy)\(', km.group(2))[0]
            if "Potions.HEALING" in val:
                defines[key] = "POTION_HEAL"
            elif "Potions.REGENERATION" in val or "minecraft:regeneration" in val:
                defines[key] = "POTION_REGEN"
            else:
                modm = re.findall(r'ModItems\.(\w+)', val)
                vanm = re.findall(r'Items\.(\w+)', val)
                if modm:
                    c = modm[-1]
                    rev = {v: k for k, v in SYM_CONST.items()}
                    defines[key] = rev.get(c, "MOD:" + c)
                elif vanm:
                    defines[key] = "MC:" + vanm[-1]
                else:
                    defines[key] = "?"
        out[const] = [[defines.get(ch, "-") for ch in row] for row in pats]
    return out


# ---- Java 代码生成 ----
PREF = {"B": "B", "C": "C", "P": "P", "G": "G", "W": "W", "R": "R", "D": "D", "M": "M",
        "MOD:ORBITAL_STRIKE_CARD": "O", "MOD:STAR_COIN_BAG": "S"}
POOL = list("XLGHSPTIDEFJKMNOQRUVYZ")


def _letters(g, const):
    order = []
    for row in g:
        for t in row:
            if t not in order:
                order.append(t)
    center = g[1][1]
    out, used = {}, set()
    for t in order:
        want = None
        if t == center and const in UPGRADE:
            want = "T"
        elif t.startswith("POTION"):
            want = "Z"
        elif t in SYM:
            want = t
        elif t in PREF:
            want = PREF[t]
        if want is None or want in used:
            for c in POOL:
                if c not in used:
                    want = c
                    break
        out[t] = want
        used.add(want)
    return out


def _potion_define(letter, kind, dialect):
    ind = "                "
    if dialect == "neo":
        potion = "REGENERATION" if kind == "REGEN" else "HEALING"
        return ("%s.define('%s', net.neoforged.neoforge.common.crafting.DataComponentIngredient.of(\n"
                "%s        true, net.minecraft.core.component.DataComponents.POTION_CONTENTS,\n"
                "%s        new net.minecraft.world.item.alchemy.PotionContents(net.minecraft.world.item.alchemy.Potions.%s),\n"
                "%s        Items.POTION))" % (ind, letter, ind, ind, potion, ind))
    if kind == "REGEN":
        return ("%s.define('%s', net.minecraftforge.common.crafting.PartialNBTIngredient.of(Items.POTION,\n"
                "%s        potionTag(\"minecraft:regeneration\")))" % (ind, letter, ind))
    return ("%s.define('%s', net.minecraftforge.common.crafting.StrictNBTIngredient.of(\n"
            "%s        net.minecraft.world.item.alchemy.PotionUtils.setPotion(\n"
            "%s                new net.minecraft.world.item.ItemStack(Items.POTION),\n"
            "%s                net.minecraft.world.item.alchemy.Potions.HEALING)))" % (ind, letter, ind, ind, ind))


def emit_block(const, dialect):
    """生成完整 ShapedRecipeBuilder 代码块(含前置注释行)"""
    g = _plat(target(const), dialect)
    letters = _letters(g, const)
    ind = "                "
    save = ".save(output);" if dialect == "neo" else ".save(output::accept);"
    lines = []
    comment = "%s// %s:上排 %s｜中排 %s｜下排 %s" % (
        "        ", cn(const),
        "·".join(resolve(t) for t in g[0]),
        "·".join(resolve(t) for t in g[1]),
        "·".join(resolve(t) for t in g[2]))
    if const in UPGRADE:
        comment += "(进阶自 %s)" % cn(UPGRADE[const])
    comment += " [%s·%s]" % (RARITY_CN[chips[const]["rarity"]], stream_of.get(const, "?"))
    lines.append(comment)
    lines.append("        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.%s.get())" % const)
    for row in g:
        lines.append('%s.pattern("%s")' % (ind, "".join(letters[t] for t in row)))
    for t, lt in letters.items():
        if t.startswith("MOD:"):
            lines.append("%s.define('%s', ModItems.%s.get())" % (ind, lt, t[4:]))
        elif t.startswith("MC:"):
            note = PLATFORM_NOTE.get(dialect, {}).get(t, "")
            lines.append("%s.define('%s', Items.%s)%s" % (ind, lt, t[3:], note))
        elif t == "POTION_REGEN":
            lines.append(_potion_define(lt, "REGEN", dialect))
        elif t == "POTION_HEAL":
            lines.append(_potion_define(lt, "HEAL", dialect))
        else:
            lines.append("%s.define('%s', ModItems.%s.get())" % (ind, lt, SYM_CONST[t]))
    if const in UPGRADE:
        base = UPGRADE[const]
        lines.append('%s.unlockedBy("has_%s", has(ModItems.%s.get()))' % (ind, base.lower(), base))
    else:
        lines.append('%s.unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))' % ind)
    lines.append("%s%s" % (ind, save))
    return "\n".join(lines)
