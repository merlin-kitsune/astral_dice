package com.merlinkitsune.astral_dice.datagen;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.conditions.IConditionBuilder;

import java.util.concurrent.CompletableFuture;
import com.merlinkitsune.astral_dice.component.WeaponEnhancement;

public class ModRecipeProvider extends RecipeProvider implements IConditionBuilder {
    // 通用标签 c:bricks(砖块,对怪板砖配方使用)
    private static final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> BRICKS_TAG =
            net.minecraft.tags.ItemTags.create(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("c", "bricks"));

    public ModRecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    @Override
    protected void buildRecipes(RecipeOutput output) {
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, ModItems.DICE.get())
                .pattern("RRR")
                .pattern("RQR")
                .pattern("RRR")
                .define('R', Items.REDSTONE)
                .define('Q', Items.QUARTZ_BLOCK)
                .unlockedBy("has_quartz_block", has(Items.QUARTZ_BLOCK))
                .save(output);

        // 攻击(中):1 铁剑 + 1 星币
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.ATTACK_CARD_MEDIUM.get())
                .requires(Items.IRON_SWORD)
                .requires(ModItems.STAR_COIN.get())
                .unlockedBy("has_star_coin", has(ModItems.STAR_COIN.get()))
                .save(output);

        // 防御(中):1 盾牌 + 1 星币
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.DEFENSE_CARD_MEDIUM.get())
                .requires(Items.SHIELD)
                .requires(ModItems.STAR_COIN.get())
                .unlockedBy("has_star_coin", has(ModItems.STAR_COIN.get()))
                .save(output);

        // 暗影突袭:1 铁剑 + 1 星币 + 1 铁锭
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.ATTACK_CARD_SHADOW_STRIKE.get())
                .requires(Items.IRON_SWORD)
                .requires(ModItems.STAR_COIN.get())
                .requires(Items.IRON_INGOT)
                .unlockedBy("has_star_coin", has(ModItems.STAR_COIN.get()))
                .save(output);

        // 名刀嘎呜切:1 钻石 + 3 星币
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.ATTACK_CARD_MEITO.get())
                .requires(Items.DIAMOND)
                .requires(ModItems.STAR_COIN.get(), 3)
                .unlockedBy("has_star_coin", has(ModItems.STAR_COIN.get()))
                .save(output);

        // 蓄力:1 红石块 + 3 星盘
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.ATTACK_CARD_CHARGE.get())
                .requires(Items.REDSTONE_BLOCK)
                .requires(ModItems.STAR_PLATE.get(), 3)
                .unlockedBy("has_star_plate", has(ModItems.STAR_PLATE.get()))
                .save(output);

        // 岿然不动:1 金锭 + 1 盾牌 + 1 星币
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.EFFECT_CARD_UNWAVERING.get())
                .requires(Items.GOLD_INGOT)
                .requires(Items.SHIELD)
                .requires(ModItems.STAR_COIN.get())
                .unlockedBy("has_star_coin", has(ModItems.STAR_COIN.get()))
                .save(output);

        // 以毒攻毒:1 谜之炖菜 + 1 红色蘑菇 + 1 兔子脚 + 1 星盘
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.EFFECT_CARD_FIGHT_POISON_WITH_POISON.get())
                .requires(Items.SUSPICIOUS_STEW)
                .requires(Items.RED_MUSHROOM)
                .requires(Items.RABBIT_FOOT)
                .requires(ModItems.STAR_PLATE.get())
                .unlockedBy("has_star_plate", has(ModItems.STAR_PLATE.get()))
                .save(output);

        // 狂暴:1 火药 + 1 星币
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.EFFECT_CARD_BERSERK.get())
                .requires(Items.GUNPOWDER)
                .requires(ModItems.STAR_COIN.get())
                .unlockedBy("has_star_coin", has(ModItems.STAR_COIN.get()))
                .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.BLANK_SIGN.get())
                .pattern("SSS")
                .pattern("SCS")
                .pattern("SSS")
                .define('S', ModItems.STAR_COIN.get())
                .define('C', Items.CLAY_BALL)
                .unlockedBy("has_star_coin", has(ModItems.STAR_COIN.get()))
                .save(output);

        // === 立牌(均含空白立牌;配方难度按角色强度分级,强力立牌加入骰子/稀有材料) ===
        // T1 弱:史莱姆立牌(史莱姆球×4 + 星币×3 + 黄金骰子×1,有序,空白立牌置中,黄金骰子置中下)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.LULU_SIGN.get())
                .pattern("SZS")
                .pattern("ZCZ")
                .pattern("SDS")
                .define('S', Items.SLIME_BALL)
                .define('Z', ModItems.STAR_COIN.get())
                .define('C', ModItems.BLANK_SIGN.get())
                .define('D', ModItems.GOLDEN_DICE.get())
                .unlockedBy("has_blank_sign", has(ModItems.BLANK_SIGN.get()))
                .save(output);

        // T1-2 中:经商立牌(3 绿宝石 + 4 星币 + 1 骰子,有序,空白立牌置中,骰子置中下)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.PARUNAN_SIGN.get())
                .pattern("SES")
                .pattern("ECE")
                .pattern("SDS")
                .define('S', ModItems.STAR_COIN.get())
                .define('E', Items.EMERALD)
                .define('C', ModItems.BLANK_SIGN.get())
                .define('D', ModItems.DICE.get())
                .unlockedBy("has_blank_sign", has(ModItems.BLANK_SIGN.get()))
                .save(output);

        // T2 中:扫地机立牌(4 甘蔗 + 3 铁锭 + 1 骰子,有序,空白立牌置中,骰子置中下)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.JASMINE_SIGN.get())
                .pattern("SIS")
                .pattern("IEI")
                .pattern("SDS")
                .define('E', ModItems.BLANK_SIGN.get())
                .define('I', Items.IRON_INGOT)
                .define('S', Items.SUGAR_CANE)
                .define('D', ModItems.DICE.get())
                .unlockedBy("has_blank_sign", has(ModItems.BLANK_SIGN.get()))
                .save(output);

        // T2 中:看板娘立牌(3 白色染料 + 2 蓝色染料 + 骰子×1 + 星币×2,有序,空白立牌置中,骰子置中下)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.MIMI_SIGN.get())
                .pattern("WWW")
                .pattern("SES")
                .pattern("BDB")
                .define('E', ModItems.BLANK_SIGN.get())
                .define('W', Items.WHITE_DYE)
                .define('B', Items.BLUE_DYE)
                .define('S', ModItems.STAR_COIN.get())
                .define('D', ModItems.DICE.get())
                .unlockedBy("has_blank_sign", has(ModItems.BLANK_SIGN.get()))
                .save(output);

        // T2 中:忍者立牌(黄金骰子 + 羽毛×2 + 回响碎片×1 + 黑色染料×2 + 发射器×2,有序,空白立牌置中,骰子置中下)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.KOMACHI_SIGN.get())
                .pattern("FIF")
                .pattern("BEB")
                .pattern("LDL")
                .define('E', ModItems.BLANK_SIGN.get())
                .define('F', Items.FEATHER)
                .define('I', Items.ECHO_SHARD)
                .define('B', Items.BLACK_DYE)
                .define('L', Items.DISPENSER)
                .define('D', ModItems.DIAMOND_DICE.get())
                .unlockedBy("has_blank_sign", has(ModItems.BLANK_SIGN.get()))
                .save(output);

        // T2 中:大侦探立牌(黄金骰子 + 红石粉 + 金锭×2 + 星币×2 + 星盘×2,有序,空白立牌置中,骰子置中下,红石粉置中上)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.FANNY_SIGN.get())
                .pattern("SRS")
                .pattern("GEG")
                .pattern("PDP")
                .define('E', ModItems.BLANK_SIGN.get())
                .define('R', Items.REDSTONE)
                .define('S', ModItems.STAR_COIN.get())
                .define('G', Items.GOLD_INGOT)
                .define('P', ModItems.STAR_PLATE.get())
                .define('D', ModItems.GOLDEN_DICE.get())
                .unlockedBy("has_blank_sign", has(ModItems.BLANK_SIGN.get()))
                .save(output);

        // T3 强:调查员立牌(钻石骰子 + 书与笔×2 + 钟 + 星币×2 + 黄金星盘×2,有序,空白立牌置中,骰子置中下)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.RIN_SIGN.get())
                .pattern("SCS")
                .pattern("BEB")
                .pattern("PDP")
                .define('E', ModItems.BLANK_SIGN.get())
                .define('C', Items.CLOCK)
                .define('S', ModItems.STAR_COIN.get())
                .define('B', Items.WRITABLE_BOOK)
                .define('P', ModItems.GOLDEN_STAR_PLATE.get())
                .define('D', ModItems.DIAMOND_DICE.get())
                .unlockedBy("has_blank_sign", has(ModItems.BLANK_SIGN.get()))
                .save(output);

        // T3 强:占星师立牌(黄金骰子 + 海晶砂砾×2 + 望远镜 + 金锭×4,有序,空白立牌置中,骰子置中下)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.HAIQING_SIGN.get())
                .pattern("GCG")
                .pattern("LEL")
                .pattern("GDG")
                .define('E', ModItems.BLANK_SIGN.get())
                .define('C', Items.SPYGLASS)
                .define('L', Items.PRISMARINE_CRYSTALS)
                .define('G', Items.GOLD_INGOT)
                .define('D', ModItems.DIAMOND_DICE.get())
                .unlockedBy("has_blank_sign", has(ModItems.BLANK_SIGN.get()))
                .save(output);

        // T3 强:吸血鬼立牌(黄金骰子 + 红石块×2 + 骨块×2 + 黑色染料×2 + 星盘×1,有序,空白立牌置中,骰子置中下)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.PAPARA_SIGN.get())
                .pattern("RPR")
                .pattern("BEB")
                .pattern("KDK")
                .define('E', ModItems.BLANK_SIGN.get())
                .define('R', Items.REDSTONE_BLOCK)
                .define('P', ModItems.STAR_PLATE.get())
                .define('B', Items.BLACK_DYE)
                .define('K', Items.BONE_BLOCK)
                .define('D', ModItems.GOLDEN_DICE.get())
                .unlockedBy("has_blank_sign", has(ModItems.BLANK_SIGN.get()))
                .save(output);

        // T3 强:上班族立牌(黄金骰子 + 凋灵骷髅头×2 + 黄色染料×1 + 陶瓦×4,有序,空白立牌置中,骰子置中下)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.PADMAN_SIGN.get())
                .pattern("WYW")
                .pattern("TET")
                .pattern("TDT")
                .define('E', ModItems.BLANK_SIGN.get())
                .define('W', Items.WITHER_SKELETON_SKULL)
                .define('Y', Items.YELLOW_DYE)
                .define('T', Items.TERRACOTTA)
                .define('D', ModItems.GOLDEN_DICE.get())
                .unlockedBy("has_blank_sign", has(ModItems.BLANK_SIGN.get()))
                .save(output);

        // T4 极强:护法立牌(钻石骰子 + 铁剑×2 + 红色染料×2 + 青色染料×2 + 黄金星盘×1,有序,空白立牌置中,骰子置中下)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.MISAKI_SIGN.get())
                .pattern("RPR")
                .pattern("IEI")
                .pattern("CDC")
                .define('E', ModItems.BLANK_SIGN.get())
                .define('R', Items.RED_DYE)
                .define('P', ModItems.GOLDEN_STAR_PLATE.get())
                .define('I', Items.IRON_SWORD)
                .define('C', Items.CYAN_DYE)
                .define('D', ModItems.DIAMOND_DICE.get())
                .unlockedBy("has_blank_sign", has(ModItems.BLANK_SIGN.get()))
                .save(output);

        // T4 极强:秘密侦探立牌(下界合金骰子 + 望远镜×2 + 信标 + 黄金星盘×2 + 金苹果×2,有序,空白立牌置中,骰子置中下)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.BONNIE_SIGN.get())
                .pattern("PCP")
                .pattern("TET")
                .pattern("ADA")
                .define('E', ModItems.BLANK_SIGN.get())
                .define('P', ModItems.GOLDEN_STAR_PLATE.get())
                .define('C', Items.BEACON)
                .define('T', Items.SPYGLASS)
                .define('A', Items.GOLDEN_APPLE)
                .define('D', ModItems.NETHERITE_DICE.get())
                .unlockedBy("has_blank_sign", has(ModItems.BLANK_SIGN.get()))
                .save(output);

        // 王之力:1 钻石剑 + 1 星币
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.EFFECT_CARD_KING_POWER.get())
                .requires(Items.DIAMOND_SWORD)
                .requires(ModItems.STAR_COIN.get())
                .unlockedBy("has_star_coin", has(ModItems.STAR_COIN.get()))
                .save(output);

        // 美工刀-初级:上排 铁剑·金苹果·铁剑｜中排 再生试剂·空白筹码·再生试剂｜下排 星盘·星盘·星盘 [史诗·治愈]
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.CUTTER_CHIP.get())
                .pattern("XLX")
                .pattern("RBR")
                .pattern("PPP")
                .define('X', Items.IRON_SWORD)
                .define('L', Items.GOLDEN_APPLE)
                .define('R', ModItems.REGENERATION_REAGENT.get())
                .define('B', ModItems.BLANK_CHIP.get())
                .define('P', ModItems.STAR_PLATE.get())
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // 手电筒-强光:上排 红石灯·黄色染色玻璃·红石灯｜中排 星币尘·空白筹码·星币尘｜下排 星盘·星盘·星盘 [史诗·星光]
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.FLASHLIGHT_CHIP.get())
                .pattern("XLX")
                .pattern("DBD")
                .pattern("PPP")
                .define('X', Items.REDSTONE_LAMP)
                .define('L', Items.YELLOW_STAINED_GLASS)
                .define('D', ModItems.STAR_COIN_DUST.get())
                .define('B', ModItems.BLANK_CHIP.get())
                .define('P', ModItems.STAR_PLATE.get())
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.ATTACK_CARD_LARGE.get())
                .requires(ModItems.ATTACK_CARD_MEDIUM.get())
                .requires(ModItems.ATTACK_CARD_MEDIUM.get())
                .requires(ModItems.STAR_COIN.get(), 2)
                .unlockedBy("has_card_medium", has(ModItems.ATTACK_CARD_MEDIUM.get()))
                .save(output, ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "attack_card_large_from_medium"));

        // 攻击(特大):2 攻击(大) + 3 星币
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.ATTACK_CARD_EPIC.get())
                .requires(ModItems.ATTACK_CARD_LARGE.get())
                .requires(ModItems.ATTACK_CARD_LARGE.get())
                .requires(ModItems.STAR_COIN.get(), 3)
                .unlockedBy("has_card_large", has(ModItems.ATTACK_CARD_LARGE.get()))
                .save(output, ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "attack_card_epic_from_large"));

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.DEFENSE_CARD_LARGE.get())
                .requires(ModItems.DEFENSE_CARD_MEDIUM.get())
                .requires(ModItems.DEFENSE_CARD_MEDIUM.get())
                .requires(ModItems.STAR_COIN.get(), 2)
                .unlockedBy("has_defense_medium", has(ModItems.DEFENSE_CARD_MEDIUM.get()))
                .save(output, ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "defense_card_large_from_medium"));

        // 防御(特大):2 防御(大) + 3 星币
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.DEFENSE_CARD_EPIC.get())
                .requires(ModItems.DEFENSE_CARD_LARGE.get())
                .requires(ModItems.DEFENSE_CARD_LARGE.get())
                .requires(ModItems.STAR_COIN.get(), 3)
                .unlockedBy("has_defense_large", has(ModItems.DEFENSE_CARD_LARGE.get()))
                .save(output, ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "defense_card_epic_from_large"));

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.BLANK_CHIP.get(), 2)
                .pattern("SBS")
                .pattern("SCS")
                .pattern("SSS")
                .define('S', ModItems.STAR_COIN.get())
                .define('B', ModItems.BLANK_CHIP.get())
                .define('C', Items.IRON_BLOCK)
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output, ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "blank_chip_duplicate"));

        // 普通瞄具:上排 紫水晶碎片·铜锭·紫水晶碎片｜中排 标记涂料·空白筹码·标记涂料｜下排 星盘·星盘·星盘 [史诗·标记]
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.SCOPE_CHIP.get())
                .pattern("XLX")
                .pattern("MBM")
                .pattern("PPP")
                .define('X', Items.AMETHYST_SHARD)
                .define('L', Items.COPPER_INGOT)
                .define('M', ModItems.MARK_PAINT.get())
                .define('B', ModItems.BLANK_CHIP.get())
                .define('P', ModItems.STAR_PLATE.get())
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // 鹰眼瞄具:紫->金(通用升级)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.EAGLE_SCOPE_CHIP.get())
                .pattern("RDR")
                .pattern("DTD")
                .pattern("GGG")
                .define('T', ModItems.SCOPE_CHIP.get())
                .define('R', Items.REDSTONE)
                .define('D', Items.DIAMOND)
                .define('G', ModItems.GOLDEN_STAR_PLATE.get())
                .unlockedBy("has_scope_chip", has(ModItems.SCOPE_CHIP.get()))
                .save(output);

        // 医疗箱-紧急治疗:上排 粘液球·粘液球·粘液球｜中排 再生试剂·空白筹码·再生试剂｜下排 星币·星币·星币 [稀有·治愈]
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.MEDKIT_EMERGENCY_CHIP.get())
                .pattern("XXX")
                .pattern("RBR")
                .pattern("CCC")
                .define('X', Items.SLIME_BALL)
                .define('R', ModItems.REGENERATION_REAGENT.get())
                .define('B', ModItems.BLANK_CHIP.get())
                .define('C', ModItems.STAR_COIN.get())
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // 医疗箱-完备:紫->金(通用升级)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.MEDKIT_COMPLETE_CHIP.get())
                .pattern("RDR")
                .pattern("DTD")
                .pattern("GGG")
                .define('T', ModItems.MEDKIT_EMERGENCY_CHIP.get())
                .define('R', Items.REDSTONE)
                .define('D', Items.DIAMOND)
                .define('G', ModItems.GOLDEN_STAR_PLATE.get())
                .unlockedBy("has_medkit_emergency", has(ModItems.MEDKIT_EMERGENCY_CHIP.get()))
                .save(output);

        // 维生素药丸:上排 发酵蛛眼·发酵蛛眼·发酵蛛眼｜中排 再生试剂·空白筹码·再生试剂｜下排 星盘·星盘·星盘 [史诗·治愈]
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.VITAMIN_PILL_CHIP.get())
                .pattern("XXX")
                .pattern("RBR")
                .pattern("PPP")
                .define('X', Items.FERMENTED_SPIDER_EYE)
                .define('R', ModItems.REGENERATION_REAGENT.get())
                .define('B', ModItems.BLANK_CHIP.get())
                .define('P', ModItems.STAR_PLATE.get())
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // 标靶:上排 标靶·标靶·标靶｜中排 标记涂料·空白筹码·标记涂料｜下排 星币·星币·星币 [稀有·标记]
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.TARGET_CHIP.get())
                .pattern("XXX")
                .pattern("MBM")
                .pattern("CCC")
                .define('X', Items.TARGET)
                .define('M', ModItems.MARK_PAINT.get())
                .define('B', ModItems.BLANK_CHIP.get())
                .define('C', ModItems.STAR_COIN.get())
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.GOLDEN_STAR_PLATE.get())
                .pattern("PPP")
                .pattern("CCC")
                .pattern("CCC")
                .define('P', ModItems.STAR_PLATE.get())
                .define('C', ModItems.STAR_COIN.get())
                .unlockedBy("has_star_plate", has(ModItems.STAR_PLATE.get()))
                .save(output, ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "golden_star_plate_from_plates"));

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.GOLDEN_STAR_PLATE.get())
                .requires(ModItems.STAR_PLATE.get())
                .requires(Items.NETHER_STAR, 2)
                .unlockedBy("has_star_plate", has(ModItems.STAR_PLATE.get()))
                .save(output, ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "golden_star_plate_from_nether_star"));

        // 八面骰:上排 金锭·骰子·金锭｜中排 星币尘·空白筹码·星币尘｜下排 星币·星币·星币 [稀有·星光]
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.EIGHT_SIDED_DICE.get())
                .pattern("XLX")
                .pattern("DBD")
                .pattern("CCC")
                .define('X', Items.GOLD_INGOT)
                .define('L', ModItems.DICE.get())
                .define('D', ModItems.STAR_COIN_DUST.get())
                .define('B', ModItems.BLANK_CHIP.get())
                .define('C', ModItems.STAR_COIN.get())
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // 袋装星币:9 枚星币打包成 1 袋
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.STAR_COIN_BAG.get())
                .pattern("CCC")
                .pattern("CCC")
                .pattern("CCC")
                .define('C', ModItems.STAR_COIN.get())
                .unlockedBy("has_star_coin", has(ModItems.STAR_COIN.get()))
                .save(output);

        // 袋装星币:1 袋拆解回 9 枚星币
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.STAR_COIN.get(), 9)
                .requires(ModItems.STAR_COIN_BAG.get())
                .unlockedBy("has_star_coin_bag", has(ModItems.STAR_COIN_BAG.get()))
                .save(output, ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "star_coin_from_bag"));

        // 《恋的规则书》:1 本书 + 1 星币,产出 patchouli:guide_book(手写配方见
        // src/main/resources/data/astral_dice/recipe/astral_guide.json,因 Patchouli 书籍物品
        // 在数据生成阶段尚未注册,无法在此动态引用)

        // === 骰子升级 ===
        // 注:黄金骰子/钻石骰子/下界合金骰子使用自定义配方类型 astral_dice:dice_upgrade
        //     (升级时继承骰子 WeaponEnhancement 配置),配方定义在
        //     src/main/resources/data/astral_dice/recipe/(golden_dice.json / diamond_dice.json / netherite_dice.json),此处不生成。

        // === 伤害效果牌 ===
        // 对怪激光(+4):1 纸 + 1 红石粉 + 1 星币
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.MONSTER_LASER_CARD.get())
                .requires(Items.PAPER)
                .requires(Items.REDSTONE)
                .requires(ModItems.STAR_COIN.get())
                .unlockedBy("has_star_coin", has(ModItems.STAR_COIN.get()))
                .save(output);

        // 对怪板砖(+6):1 纸 + 1 tag=c:bricks + 1 星币
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.MONSTER_BRICK_CARD.get())
                .requires(Items.PAPER)
                .requires(net.minecraft.world.item.crafting.Ingredient.of(BRICKS_TAG))
                .requires(ModItems.STAR_COIN.get())
                .unlockedBy("has_bricks", has(BRICKS_TAG))
                .save(output);

        // 轨道炮(+8):1 纸 + 1 望远镜 + 1 星盘
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.ORBITAL_STRIKE_CARD.get())
                .requires(Items.PAPER)
                .requires(Items.SPYGLASS)
                .requires(ModItems.STAR_PLATE.get())
                .unlockedBy("has_star_plate", has(ModItems.STAR_PLATE.get()))
                .save(output);

        // 定向爆破(+5 AOE):1 望远镜 + 2 TNT + 1 星盘(无序)
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.DIRECTIONAL_BLAST_CARD.get())
                .requires(Items.SPYGLASS)
                .requires(Items.TNT, 2)
                .requires(ModItems.STAR_PLATE.get())
                .unlockedBy("has_star_plate", has(ModItems.STAR_PLATE.get()))
                .save(output);

        // === 筹码(均含空白筹码) ===
        // 美工刀-锋利:紫->金(通用升级)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.CUTTER_BLADE_CHIP.get())
                .pattern("RDR")
                .pattern("DTD")
                .pattern("GGG")
                .define('T', ModItems.CUTTER_CHIP.get())
                .define('R', Items.REDSTONE)
                .define('D', Items.DIAMOND)
                .define('G', ModItems.GOLDEN_STAR_PLATE.get())
                .unlockedBy("has_cutter_chip", has(ModItems.CUTTER_CHIP.get()))
                .save(output);

        // 标记喷罐:上排 下界疣·青金石·下界疣｜中排 标记涂料·空白筹码·标记涂料｜下排 星币·星币·星币 [稀有·标记]
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.MARKER_SPRAYER_CHIP.get())
                .pattern("XLX")
                .pattern("MBM")
                .pattern("CCC")
                .define('X', Items.NETHER_WART)
                .define('L', Items.LAPIS_LAZULI)
                .define('M', ModItems.MARK_PAINT.get())
                .define('B', ModItems.BLANK_CHIP.get())
                .define('C', ModItems.STAR_COIN.get())
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // 魔法秘典:上排 书与笔·回响碎片·书与笔｜中排 附魔瓶·空白筹码·附魔瓶｜下排 星盘·星盘·星盘 [史诗·无流派]
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.MAGIC_TOME_CHIP.get())
                .pattern("XLX")
                .pattern("GBG")
                .pattern("PPP")
                .define('X', Items.WRITABLE_BOOK)
                .define('L', Items.ECHO_SHARD)
                .define('G', Items.EXPERIENCE_BOTTLE)
                .define('B', ModItems.BLANK_CHIP.get())
                .define('P', ModItems.STAR_PLATE.get())
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // 大背包:上排 皮革·皮革·皮革｜中排 铁锭·空白筹码·铁锭｜下排 星盘·星盘·星盘 [史诗·无流派]
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.BIG_BACKPACK_CHIP.get())
                .pattern("XXX")
                .pattern("LBL")
                .pattern("PPP")
                .define('X', Items.LEATHER)
                .define('L', Items.IRON_INGOT)
                .define('B', ModItems.BLANK_CHIP.get())
                .define('P', ModItems.STAR_PLATE.get())
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // 忍术飞镖:上排 下界合金锭·红石块·下界合金锭｜中排 标记涂料·空白筹码·标记涂料｜下排 黄金星盘·黄金星盘·黄金星盘 [传奇·标记]
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.NINJA_STAR_CHIP.get())
                .pattern("XLX")
                .pattern("MBM")
                .pattern("GGG")
                .define('X', Items.NETHERITE_INGOT)
                .define('L', Items.REDSTONE_BLOCK)
                .define('M', ModItems.MARK_PAINT.get())
                .define('B', ModItems.BLANK_CHIP.get())
                .define('G', ModItems.GOLDEN_STAR_PLATE.get())
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // 手持风扇-小:上排 羽毛·竹子·羽毛｜中排 标记涂料·空白筹码·标记涂料｜下排 星币·星币·星币 [稀有·标记]
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.HAND_FAN_SMALL_CHIP.get())
                .pattern("XLX")
                .pattern("MBM")
                .pattern("CCC")
                .define('X', Items.FEATHER)
                .define('L', Items.BAMBOO)
                .define('M', ModItems.MARK_PAINT.get())
                .define('B', ModItems.BLANK_CHIP.get())
                .define('C', ModItems.STAR_COIN.get())
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // 手持风扇-大:通用蓝->紫升级配方(手持风扇-小 + 青金石 + 金锭 + 星盘)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.HAND_FAN_BIG_CHIP.get())
                .pattern("LGL")
                .pattern("GTG")
                .pattern("PPP")
                .define('L', Items.LAPIS_LAZULI)
                .define('G', Items.GOLD_INGOT)
                .define('T', ModItems.HAND_FAN_SMALL_CHIP.get())
                .define('P', ModItems.STAR_PLATE.get())
                .unlockedBy("has_hand_fan_small", has(ModItems.HAND_FAN_SMALL_CHIP.get()))
                .save(output);

        // === 新筹码(全部为 shape:空白筹码居中,mod 物品在中轴,原版材料在四角) ===
        // ATM机:上排 金锭·金锭·金锭｜中排 星币尘·空白筹码·星币尘｜下排 星币·星币·星币 [稀有·星光]
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.ATM.get())
                .pattern("XXX")
                .pattern("DBD")
                .pattern("CCC")
                .define('X', Items.GOLD_INGOT)
                .define('D', ModItems.STAR_COIN_DUST.get())
                .define('B', ModItems.BLANK_CHIP.get())
                .define('C', ModItems.STAR_COIN.get())
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // 银行卡-余额少:上排 金锭·金块·金锭｜中排 星币尘·空白筹码·星币尘｜下排 星币·星币·星币 [稀有·星光]
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.BANK_CARD_LOW.get())
                .pattern("XLX")
                .pattern("DBD")
                .pattern("CCC")
                .define('X', Items.GOLD_INGOT)
                .define('L', Items.GOLD_BLOCK)
                .define('D', ModItems.STAR_COIN_DUST.get())
                .define('B', ModItems.BLANK_CHIP.get())
                .define('C', ModItems.STAR_COIN.get())
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // 银行卡-余额多:蓝->紫(通用升级:青金石/金锭/星盘)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.BANK_CARD_HIGH.get())
                .pattern("LGL")
                .pattern("GTG")
                .pattern("PPP")
                .define('T', ModItems.BANK_CARD_LOW.get())
                .define('L', Items.LAPIS_LAZULI)
                .define('G', Items.GOLD_INGOT)
                .define('P', ModItems.STAR_PLATE.get())
                .unlockedBy("has_bank_card_low", has(ModItems.BANK_CARD_LOW.get()))
                .save(output);

        // 银行卡-用不完:紫->金(通用升级:红石/钻石/黄金星盘)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.BANK_CARD_UNLIMITED.get())
                .pattern("RDR")
                .pattern("DTD")
                .pattern("GGG")
                .define('T', ModItems.BANK_CARD_HIGH.get())
                .define('R', Items.REDSTONE)
                .define('D', Items.DIAMOND)
                .define('G', ModItems.GOLDEN_STAR_PLATE.get())
                .unlockedBy("has_bank_card_high", has(ModItems.BANK_CARD_HIGH.get()))
                .save(output);

        // 拳击手套-初级:中央空白筹码 + 中轴星币 + 铁锭/皮革四角
        // 拳击手套-初级:3 星币 + 3 海绵 + 2 皮革,空白筹码居中
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.BOXING_GLOVES_LOW.get())
                .pattern("SSS")
                .pattern("LBL")
                .pattern("CCC")
                .define('S', Items.SPONGE)
                .define('L', Items.LEATHER)
                .define('B', ModItems.BLANK_CHIP.get())
                .define('C', ModItems.STAR_COIN.get())
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // 拳击手套-中级:蓝->紫(通用升级)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.BOXING_GLOVES_MEDIUM.get())
                .pattern("LGL")
                .pattern("GTG")
                .pattern("PPP")
                .define('T', ModItems.BOXING_GLOVES_LOW.get())
                .define('L', Items.LAPIS_LAZULI)
                .define('G', Items.GOLD_INGOT)
                .define('P', ModItems.STAR_PLATE.get())
                .unlockedBy("has_boxing_gloves_low", has(ModItems.BOXING_GLOVES_LOW.get()))
                .save(output);

        // 拳击手套-高级:紫->金(通用升级)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.BOXING_GLOVES_HIGH.get())
                .pattern("RDR")
                .pattern("DTD")
                .pattern("GGG")
                .define('T', ModItems.BOXING_GLOVES_MEDIUM.get())
                .define('R', Items.REDSTONE)
                .define('D', Items.DIAMOND)
                .define('G', ModItems.GOLDEN_STAR_PLATE.get())
                .unlockedBy("has_boxing_gloves_medium", has(ModItems.BOXING_GLOVES_MEDIUM.get()))
                .save(output);

        // 速度轮滑-初级:中央空白筹码 + 中轴星币 + 皮革/铁锭四角
        // 速度轮滑:3 星币 + 1 皮革靴子 + 2 蓝冰 + 2 铁锭,空白筹码居中
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.SPEED_SKATES_LOW.get())
                .pattern("ULU")
                .pattern("IBI")
                .pattern("CCC")
                .define('U', Items.BLUE_ICE)
                .define('L', Items.LEATHER_BOOTS)
                .define('I', Items.IRON_INGOT)
                .define('B', ModItems.BLANK_CHIP.get())
                .define('C', ModItems.STAR_COIN.get())
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // 速度轮滑-中级:蓝->紫(通用升级)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.SPEED_SKATES_MEDIUM.get())
                .pattern("LGL")
                .pattern("GTG")
                .pattern("PPP")
                .define('T', ModItems.SPEED_SKATES_LOW.get())
                .define('L', Items.LAPIS_LAZULI)
                .define('G', Items.GOLD_INGOT)
                .define('P', ModItems.STAR_PLATE.get())
                .unlockedBy("has_speed_skates_low", has(ModItems.SPEED_SKATES_LOW.get()))
                .save(output);

        // 速度轮滑-高级:紫->金(通用升级)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.SPEED_SKATES_HIGH.get())
                .pattern("RDR")
                .pattern("DTD")
                .pattern("GGG")
                .define('T', ModItems.SPEED_SKATES_MEDIUM.get())
                .define('R', Items.REDSTONE)
                .define('D', Items.DIAMOND)
                .define('G', ModItems.GOLDEN_STAR_PLATE.get())
                .unlockedBy("has_speed_skates_medium", has(ModItems.SPEED_SKATES_MEDIUM.get()))
                .save(output);

        // 摩托头盔-一般:中央空白筹码 + 中轴星币 + 铁锭/皮革
        // 摩托头盔-一般:3 星币 + 2 玻璃板 + 1 皮革头盔 + 2 铁锭,空白筹码居中
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.MOTO_HELMET_LOW.get())
                .pattern("GLG")
                .pattern("IBI")
                .pattern("CCC")
                .define('G', Items.GLASS_PANE)
                .define('L', Items.LEATHER_HELMET)
                .define('I', Items.IRON_INGOT)
                .define('B', ModItems.BLANK_CHIP.get())
                .define('C', ModItems.STAR_COIN.get())
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // 摩托头盔-中级:蓝->紫(通用升级)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.MOTO_HELMET_MEDIUM.get())
                .pattern("LGL")
                .pattern("GTG")
                .pattern("PPP")
                .define('T', ModItems.MOTO_HELMET_LOW.get())
                .define('L', Items.LAPIS_LAZULI)
                .define('G', Items.GOLD_INGOT)
                .define('P', ModItems.STAR_PLATE.get())
                .unlockedBy("has_moto_helmet_low", has(ModItems.MOTO_HELMET_LOW.get()))
                .save(output);

        // 摩托头盔-高级:紫->金(通用升级)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.MOTO_HELMET_HIGH.get())
                .pattern("RDR")
                .pattern("DTD")
                .pattern("GGG")
                .define('T', ModItems.MOTO_HELMET_MEDIUM.get())
                .define('R', Items.REDSTONE)
                .define('D', Items.DIAMOND)
                .define('G', ModItems.GOLDEN_STAR_PLATE.get())
                .unlockedBy("has_moto_helmet_medium", has(ModItems.MOTO_HELMET_MEDIUM.get()))
                .save(output);

        // 夹心饼干-一般:中央空白筹码 + 中轴星币 + 面包/糖四角
        // 夹心饼干-一般:3 星币 + 2 曲奇 + 鸡蛋 + 2 奶桶,空白筹码居中
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.SANDWICH_LOW.get())
                .pattern("KEK")
                .pattern("MBM")
                .pattern("CCC")
                .define('K', Items.COOKIE)
                .define('E', Items.EGG)
                .define('M', Items.MILK_BUCKET)
                .define('B', ModItems.BLANK_CHIP.get())
                .define('C', ModItems.STAR_COIN.get())
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // 夹心饼干-可口:蓝->紫(通用升级)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.SANDWICH_MEDIUM.get())
                .pattern("LGL")
                .pattern("GTG")
                .pattern("PPP")
                .define('T', ModItems.SANDWICH_LOW.get())
                .define('L', Items.LAPIS_LAZULI)
                .define('G', Items.GOLD_INGOT)
                .define('P', ModItems.STAR_PLATE.get())
                .unlockedBy("has_sandwich_low", has(ModItems.SANDWICH_LOW.get()))
                .save(output);

        // 夹心饼干-美味:紫->金(通用升级)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.SANDWICH_HIGH.get())
                .pattern("RDR")
                .pattern("DTD")
                .pattern("GGG")
                .define('T', ModItems.SANDWICH_MEDIUM.get())
                .define('R', Items.REDSTONE)
                .define('D', Items.DIAMOND)
                .define('G', ModItems.GOLDEN_STAR_PLATE.get())
                .unlockedBy("has_sandwich_medium", has(ModItems.SANDWICH_MEDIUM.get()))
                .save(output);

        // 魔法箭袋:上排 光灵箭·回响碎片·光灵箭｜中排 标记涂料·空白筹码·标记涂料｜下排 星盘·星盘·星盘 [史诗·标记]
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.MAGIC_QUIVER.get())
                .pattern("XLX")
                .pattern("MBM")
                .pattern("PPP")
                .define('X', Items.SPECTRAL_ARROW)
                .define('L', Items.ECHO_SHARD)
                .define('M', ModItems.MARK_PAINT.get())
                .define('B', ModItems.BLANK_CHIP.get())
                .define('P', ModItems.STAR_PLATE.get())
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // 缓冲盾牌:上排 钻石·盾牌·钻石｜中排 再生试剂·空白筹码·再生试剂｜下排 星币·星币·星币 [稀有·治愈]
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.BUFFER_SHIELD.get())
                .pattern("XLX")
                .pattern("RBR")
                .pattern("CCC")
                .define('X', Items.DIAMOND)
                .define('L', Items.SHIELD)
                .define('R', ModItems.REGENERATION_REAGENT.get())
                .define('B', ModItems.BLANK_CHIP.get())
                .define('C', ModItems.STAR_COIN.get())
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // 星币锤:上排 星币袋·重锤·星币袋｜中排 星币尘·空白筹码·星币尘｜下排 黄金星盘·黄金星盘·黄金星盘 [传奇·星光]
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.STAR_COIN_HAMMER.get())
                .pattern("SXS")
                .pattern("DBD")
                .pattern("GGG")
                .define('S', ModItems.STAR_COIN_BAG.get())
                .define('X', Items.MACE)
                .define('D', ModItems.STAR_COIN_DUST.get())
                .define('B', ModItems.BLANK_CHIP.get())
                .define('G', ModItems.GOLDEN_STAR_PLATE.get())
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // 诅咒之剑:金剑上排 + 哭泣黑曜石中轴 + 空白筹码居中 + 星币下排
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.CURSED_SWORD.get())
                .pattern("JMJ")
                .pattern("ZCZ")
                .pattern("BBB")
                .define('J', Items.GOLDEN_SWORD)
                .define('M', Items.CRYING_OBSIDIAN)
                .define('Z', Items.POPPED_CHORUS_FRUIT)
                .define('C', ModItems.BLANK_CHIP.get())
                .define('B', ModItems.STAR_COIN.get())
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // 复仇之戟:凋灵骷髅头上排 + 钻石剑中轴 + 空白筹码居中 + 星盘下排
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.REVENGE_HALBERD.get())
                .pattern("DJD")
                .pattern("JCJ")
                .pattern("PPP")
                .define('D', Items.WITHER_SKELETON_SKULL)
                .define('J', Items.DIAMOND_SWORD)
                .define('C', ModItems.BLANK_CHIP.get())
                .define('P', ModItems.STAR_PLATE.get())
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // 贯穿之铳:下界合金锭上排 + 潮涌核心/回响碎片中轴 + 空白筹码居中 + 黄金星盘下排
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.PIERCING_GUN.get())
                .pattern("XSX")
                .pattern("HCH")
                .pattern("PPP")
                .define('X', Items.NETHERITE_INGOT)
                .define('S', Items.CONDUIT)
                .define('H', Items.ECHO_SHARD)
                .define('C', ModItems.BLANK_CHIP.get())
                .define('P', ModItems.GOLDEN_STAR_PLATE.get())
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // 可口糖果:上排 糖·曲奇·糖｜中排 再生试剂·空白筹码·再生试剂｜下排 星盘·星盘·星盘 [史诗·治愈]
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.CANDY_CHIP.get())
                .pattern("XLX")
                .pattern("RBR")
                .pattern("PPP")
                .define('X', Items.SUGAR)
                .define('L', Items.COOKIE)
                .define('R', ModItems.REGENERATION_REAGENT.get())
                .define('B', ModItems.BLANK_CHIP.get())
                .define('P', ModItems.STAR_PLATE.get())
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // 友情徽章:上排 治疗药水·附魔金苹果·治疗药水｜中排 再生试剂·空白筹码·再生试剂｜下排 星盘·星盘·星盘 [史诗·治愈]
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.FRIENDSHIP_BADGE.get())
                .pattern("ZXZ")
                .pattern("RBR")
                .pattern("PPP")
                .define('Z', net.neoforged.neoforge.common.crafting.DataComponentIngredient.of(
                        true, net.minecraft.core.component.DataComponents.POTION_CONTENTS,
                        new net.minecraft.world.item.alchemy.PotionContents(net.minecraft.world.item.alchemy.Potions.HEALING),
                        Items.POTION))
                .define('X', Items.ENCHANTED_GOLDEN_APPLE)
                .define('R', ModItems.REGENERATION_REAGENT.get())
                .define('B', ModItems.BLANK_CHIP.get())
                .define('P', ModItems.STAR_PLATE.get())
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // 探天卫星:红石块上排 + 轨道炮/空白筹码中轴 + 黄金星盘下排
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.SATELLITE_CHIP.get())
                .pattern("RGR")
                .pattern("GCG")
                .pattern("PPP")
                .define('R', Items.REDSTONE_BLOCK)
                .define('G', ModItems.ORBITAL_STRIKE_CARD.get())
                .define('C', ModItems.BLANK_CHIP.get())
                .define('P', ModItems.GOLDEN_STAR_PLATE.get())
                .unlockedBy("has_orbital_strike", has(ModItems.ORBITAL_STRIKE_CARD.get()))
                .save(output);

        // 肾上腺素-一般:再生药水 + 下界之星 + 凋零玫瑰 + 空白筹码 + 星盘(史诗)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.ADRENALINE_LOW.get())
                .pattern("ZXZ")
                .pattern("DCD")
                .pattern("PPP")
                .define('Z', net.neoforged.neoforge.common.crafting.DataComponentIngredient.of(
                        true, net.minecraft.core.component.DataComponents.POTION_CONTENTS,
                        new net.minecraft.world.item.alchemy.PotionContents(net.minecraft.world.item.alchemy.Potions.REGENERATION),
                        Items.POTION))
                .define('X', Items.NETHER_STAR)
                .define('D', Items.WITHER_ROSE)
                .define('C', ModItems.BLANK_CHIP.get())
                .define('P', ModItems.STAR_PLATE.get())
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // 肾上腺素-高效:紫→金升级配方 RZR/ZOZ/PPP
        // (R=红石粉,Z=钻石,O=史诗品质筹码(本例肾上腺素-一般),P=黄金星盘;遵循通用紫-金配方)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.ADRENALINE_HIGH.get())
                .pattern("RZR")
                .pattern("ZOZ")
                .pattern("PPP")
                .define('R', Items.REDSTONE)
                .define('Z', Items.DIAMOND)
                .define('O', ModItems.ADRENALINE_LOW.get())
                .define('P', ModItems.GOLDEN_STAR_PLATE.get())
                .unlockedBy("has_adrenaline_low", has(ModItems.ADRENALINE_LOW.get()))
                .save(output);

        // === 充能流派筹码(1.2.0):图案按各筹码图标视觉判读,末行为基底材料行 ===
        // 跃迁引擎:末影珍珠外圈 + 导电线材中轴(居中空白筹码) + 星币下排
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.WARP_ENGINE_CHIP.get())
                .pattern("XXX")
                .pattern("WCW")
                .pattern("BBB")
                .define('X', Items.ENDER_PEARL)
                .define('W', ModItems.CONDUCTIVE_WIRE.get())
                .define('C', ModItems.BLANK_CHIP.get())
                .define('B', ModItems.STAR_COIN.get())
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // 能量回收器:燧石/活塞外圈 + 导电线材中轴(居中空白筹码) + 星币下排
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.ENERGY_RECYCLER.get())
                .pattern("SHS")
                .pattern("LCL")
                .pattern("BBB")
                .define('S', Items.FLINT)
                .define('H', Items.PISTON)
                .define('L', ModItems.CONDUCTIVE_WIRE.get())
                .define('C', ModItems.BLANK_CHIP.get())
                .define('B', ModItems.STAR_COIN.get())
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // 电流剑:红石/钻石剑外圈 + 导电线材中轴(居中空白筹码) + 星币下排
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.ELECTRIC_SWORD.get())
                .pattern("HDH")
                .pattern("LCL")
                .pattern("BBB")
                .define('H', Items.REDSTONE)
                .define('D', Items.DIAMOND_SWORD)
                .define('L', ModItems.CONDUCTIVE_WIRE.get())
                .define('C', ModItems.BLANK_CHIP.get())
                .define('B', ModItems.STAR_COIN.get())
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // 高级外设:红石火把/回响碎片外圈 + 导电线材中轴(居中空白筹码) + 星盘下排(紫)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.ADVANCED_PERIPHERALS.get())
                .pattern("TET")
                .pattern("LCL")
                .pattern("PPP")
                .define('T', Items.REDSTONE_TORCH)
                .define('E', Items.ECHO_SHARD)
                .define('L', ModItems.CONDUCTIVE_WIRE.get())
                .define('C', ModItems.BLANK_CHIP.get())
                .define('P', ModItems.STAR_PLATE.get())
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // 永动机:金块/下界之星外圈 + 导电线材中轴(居中空白筹码) + 黄金星盘下排(金)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.PERPETUAL_MOTION.get())
                .pattern("BNB")
                .pattern("LCL")
                .pattern("GGG")
                .define('B', Items.GOLD_BLOCK)
                .define('N', Items.NETHER_STAR)
                .define('L', ModItems.CONDUCTIVE_WIRE.get())
                .define('C', ModItems.BLANK_CHIP.get())
                .define('G', ModItems.GOLDEN_STAR_PLATE.get())
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // 电流核心:红石比较器外圈 + 避雷针中轴 / 导电线材外圈 + 空白筹码居中 / 星盘下排(紫)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.CURRENT_CORE_CHIP.get())
                .pattern("UEU")
                .pattern("WCW")
                .pattern("PPP")
                .define('U', Items.COMPARATOR)
                .define('E', Items.LIGHTNING_ROD)
                .define('W', ModItems.CONDUCTIVE_WIRE.get())
                .define('C', ModItems.BLANK_CHIP.get())
                .define('P', ModItems.STAR_PLATE.get())
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // === 1.2.0 补齐的筹码配方(按规则生成:品质底行 + 第二行中间空白筹码 + 流派材料) ===
        // --- 充能类 ---
        // 电击手套:上排 红石粉·皮革·红石粉｜中排 导电线材·空白筹码·导电线材｜下排 星盘·星盘·星盘 [史诗·充能]
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.ELECTRIC_GLOVE_CHIP.get())
                .pattern("XLX")
                .pattern("WBW")
                .pattern("PPP")
                .define('X', Items.REDSTONE)
                .define('L', Items.LEATHER)
                .define('W', ModItems.CONDUCTIVE_WIRE.get())
                .define('B', ModItems.BLANK_CHIP.get())
                .define('P', ModItems.STAR_PLATE.get())
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // 安全气囊:上排 皮革·白色羊毛·皮革｜中排 导电线材·空白筹码·导电线材｜下排 星盘·星盘·星盘 [史诗·充能]
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.AIRBAG_CHIP.get())
                .pattern("XLX")
                .pattern("WBW")
                .pattern("PPP")
                .define('X', Items.LEATHER)
                .define('L', Items.WHITE_WOOL)
                .define('W', ModItems.CONDUCTIVE_WIRE.get())
                .define('B', ModItems.BLANK_CHIP.get())
                .define('P', ModItems.STAR_PLATE.get())
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // 电磁炮:上排 红石块·末地水晶·红石块｜中排 导电线材·空白筹码·导电线材｜下排 黄金星盘·黄金星盘·黄金星盘 [传奇·充能]
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.RAILGUN_CHIP.get())
                .pattern("RCR")
                .pattern("WBW")
                .pattern("GGG")
                .define('R', Items.REDSTONE_BLOCK)
                .define('C', Items.END_CRYSTAL)
                .define('W', ModItems.CONDUCTIVE_WIRE.get())
                .define('B', ModItems.BLANK_CHIP.get())
                .define('G', ModItems.GOLDEN_STAR_PLATE.get())
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // 原初核心:上排 回响碎片·龙首·回响碎片｜中排 导电线材·空白筹码·导电线材｜下排 黄金星盘·黄金星盘·黄金星盘 [传奇·充能]
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.PRIMORDIAL_CORE_CHIP.get())
                .pattern("XHX")
                .pattern("WBW")
                .pattern("GGG")
                .define('X', Items.ECHO_SHARD)
                .define('H', Items.DRAGON_HEAD)
                .define('W', ModItems.CONDUCTIVE_WIRE.get())
                .define('B', ModItems.BLANK_CHIP.get())
                .define('G', ModItems.GOLDEN_STAR_PLATE.get())
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // --- 治愈类 ---
        // 大碗炖肉:上排 碗·牛排·碗｜中排 再生试剂·空白筹码·再生试剂｜下排 黄金星盘·黄金星盘·黄金星盘 [传奇·治愈]
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.BIG_BOWL_STEW_CHIP.get())
                .pattern("XLX")
                .pattern("RBR")
                .pattern("GGG")
                .define('X', Items.BOWL)
                .define('L', Items.COOKED_BEEF)
                .define('R', ModItems.REGENERATION_REAGENT.get())
                .define('B', ModItems.BLANK_CHIP.get())
                .define('G', ModItems.GOLDEN_STAR_PLATE.get())
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // --- 无流派类 ---
        // 书签:上排 纸·皮革·纸｜中排 线·空白筹码·线｜下排 星币·星币·星币 [稀有·无流派]
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.BOOKMARK_CHIP.get())
                .pattern("XLX")
                .pattern("GBG")
                .pattern("CCC")
                .define('X', Items.PAPER)
                .define('L', Items.LEATHER)
                .define('G', Items.STRING)
                .define('B', ModItems.BLANK_CHIP.get())
                .define('C', ModItems.STAR_COIN.get())
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // 会员推荐信:上排 纸·墨囊·纸｜中排 羽毛·空白筹码·羽毛｜下排 星币·星币·星币 [稀有·无流派]
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.MEMBER_RECOMMENDATION_CHIP.get())
                .pattern("XLX")
                .pattern("GBG")
                .pattern("CCC")
                .define('X', Items.PAPER)
                .define('L', Items.INK_SAC)
                .define('G', Items.FEATHER)
                .define('B', ModItems.BLANK_CHIP.get())
                .define('C', ModItems.STAR_COIN.get())
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // 小猪存钱罐:上排 金锭·生猪排·金锭｜中排 红砖·空白筹码·红砖｜下排 星币·星币·星币 [稀有·无流派]
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.PIGGY_BANK_CHIP.get())
                .pattern("XLX")
                .pattern("GBG")
                .pattern("CCC")
                .define('X', Items.GOLD_INGOT)
                .define('L', Items.PORKCHOP)
                .define('G', Items.BRICK)
                .define('B', ModItems.BLANK_CHIP.get())
                .define('C', ModItems.STAR_COIN.get())
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // 智能手表:上排 金锭·时钟·金锭｜中排 红石粉·空白筹码·红石粉｜下排 星盘·星盘·星盘 [史诗·无流派]
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.SMART_WATCH_CHIP.get())
                .pattern("XLX")
                .pattern("GBG")
                .pattern("PPP")
                .define('X', Items.GOLD_INGOT)
                .define('L', Items.CLOCK)
                .define('G', Items.REDSTONE)
                .define('B', ModItems.BLANK_CHIP.get())
                .define('P', ModItems.STAR_PLATE.get())
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // 磨刀石:上排 下界合金锭·砂轮·下界合金锭｜中排 燧石·空白筹码·燧石｜下排 星盘·星盘·星盘 [史诗·无流派]
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.WHETSTONE_CHIP.get())
                .pattern("XLX")
                .pattern("GBG")
                .pattern("PPP")
                .define('X', Items.NETHERITE_INGOT)
                .define('L', Items.GRINDSTONE)
                .define('G', Items.FLINT)
                .define('B', ModItems.BLANK_CHIP.get())
                .define('P', ModItems.STAR_PLATE.get())
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // === 新效果牌(治疗/互动;shape:星币居中,mod 物品在中轴) ===
        // 巧克力蛋糕:1 可可豆 + 1 鸡蛋 + 1 糖 + 1 星币(无序)
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.CHOCOLATE_CAKE.get())
                .requires(Items.COCOA_BEANS)
                .requires(Items.EGG)
                .requires(Items.SUGAR)
                .requires(ModItems.STAR_COIN.get())
                .unlockedBy("has_star_coin", has(ModItems.STAR_COIN.get()))
                .save(output);

        // 汉堡:1 面包 + 1 牛排 + 2 星币(无序)
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.HAMBURGER.get())
                .requires(Items.BREAD)
                .requires(Items.COOKED_BEEF)
                .requires(ModItems.STAR_COIN.get(), 2)
                .unlockedBy("has_star_coin", has(ModItems.STAR_COIN.get()))
                .save(output);

        // 奢华大餐:1 金胡萝卜 + 1 闪烁的西瓜片 + 2 星币(无序)
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.LUXURY_FEAST.get())
                .requires(Items.GOLDEN_CARROT)
                .requires(Items.GLISTERING_MELON_SLICE)
                .requires(ModItems.STAR_COIN.get(), 2)
                .unlockedBy("has_star_coin", has(ModItems.STAR_COIN.get()))
                .save(output);

        // 你有我有:2 绿宝石 + 2 星币(无序)
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.YOU_HAVE_I_HAVE.get())
                .requires(Items.EMERALD, 2)
                .requires(ModItems.STAR_COIN.get(), 2)
                .unlockedBy("has_star_coin", has(ModItems.STAR_COIN.get()))
                .save(output);

        // 加急加快:1 荧石粉 + 2 下界石英 + 1 星盘(无序)
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.EXPRESS_DELIVERY.get())
                .requires(Items.GLOWSTONE_DUST)
                .requires(Items.QUARTZ, 2)
                .requires(ModItems.STAR_PLATE.get())
                .unlockedBy("has_star_plate", has(ModItems.STAR_PLATE.get()))
                .save(output);

        // 大当家立牌:空白立牌 + 钻石骰子 + 红石块×4 + 金块×1 + 黄金星盘×2(有序,空白立牌置中,骰子置中下)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.FEN_SIGN.get())
                .pattern("RPR")
                .pattern("RER")
                .pattern("GDG")
                .define('E', ModItems.BLANK_SIGN.get())
                .define('R', Items.REDSTONE_BLOCK)
                .define('P', Items.GOLD_BLOCK)
                .define('G', ModItems.GOLDEN_STAR_PLATE.get())
                .define('D', ModItems.DIAMOND_DICE.get())
                .unlockedBy("has_blank_sign", has(ModItems.BLANK_SIGN.get()))
                .save(output);

        // 骇客立牌:空白立牌 + 钻石骰子 + 幽匿块/混凝土 + 星盘(有序,空白立牌置中,骰子置中下)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.NANCY_LU_SIGN.get())
                .pattern("YFY")
                .pattern("BLW")
                .pattern("PZP")
                .define('L', ModItems.BLANK_SIGN.get())
                .define('Y', Items.SCULK)
                .define('F', Items.PINK_CONCRETE)
                .define('B', Items.WHITE_CONCRETE)
                .define('W', Items.BLACK_CONCRETE)
                .define('P', ModItems.STAR_PLATE.get())
                .define('Z', ModItems.DIAMOND_DICE.get())
                .unlockedBy("has_blank_sign", has(ModItems.BLANK_SIGN.get()))
                .save(output);

        // 枪匠立牌(Moses):GPG/TCT/PZP(G=弩,P=星盘,Z=钻石骰子,C=空白立牌,T=红石块)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.MOSES_SIGN.get())
                .pattern("GPG")
                .pattern("TCT")
                .pattern("PZP")
                .define('G', Items.CROSSBOW)
                .define('P', ModItems.STAR_PLATE.get())
                .define('Z', ModItems.DIAMOND_DICE.get())
                .define('C', ModItems.BLANK_SIGN.get())
                .define('T', Items.REDSTONE_BLOCK)
                .unlockedBy("has_blank_sign", has(ModItems.BLANK_SIGN.get()))
                .save(output);

        // 肉弹战车立牌(Pandaman):WHW/HCH/BDB(W=白色混凝土,H=黑色混凝土,C=空白立牌,B=星币,D=骰子)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.PANDAMAN_SIGN.get())
                .pattern("WHW")
                .pattern("HCH")
                .pattern("BDB")
                .define('W', Items.WHITE_CONCRETE)
                .define('H', Items.BLACK_CONCRETE)
                .define('C', ModItems.BLANK_SIGN.get())
                .define('B', ModItems.STAR_COIN.get())
                .define('D', ModItems.DICE.get())
                .unlockedBy("has_blank_sign", has(ModItems.BLANK_SIGN.get()))
                .save(output);

        // 游戏大师立牌(ren,稀有):PSP/SES/PDP(P=纸,S=星币,E=空白立牌,D=基础骰子);
        // 空白立牌居中、骰子中下且仅 1 个、对称填充 → 基础骰子档 = 稀有(RARE)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.REN_SIGN.get())
                .pattern("PSP")
                .pattern("SES")
                .pattern("PDP")
                .define('P', Items.PAPER)
                .define('S', ModItems.STAR_COIN.get())
                .define('E', ModItems.BLANK_SIGN.get())
                .define('D', ModItems.DICE.get())
                .unlockedBy("has_blank_sign", has(ModItems.BLANK_SIGN.get()))
                .save(output);

        // === 合成材料(1.2.0) ===
        // 再生试剂:红石粉 + 粘液球 + 金西瓜片 + 蜂蜜瓶(无序)
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.REGENERATION_REAGENT.get())
                .requires(Items.REDSTONE)
                .requires(Items.SLIME_BALL)
                .requires(Items.GLISTERING_MELON_SLICE)
                .requires(Items.HONEY_BOTTLE)
                .unlockedBy("has_glistering_melon_slice", has(Items.GLISTERING_MELON_SLICE))
                .save(output);

        // 导电线材:DLD/DLD/RRR(D=钻石,L=线,R=红石粉)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.CONDUCTIVE_WIRE.get())
                .pattern("DLD")
                .pattern("DLD")
                .pattern("RRR")
                .define('D', Items.DIAMOND)
                .define('L', Items.STRING)
                .define('R', Items.REDSTONE)
                .unlockedBy("has_diamond", has(Items.DIAMOND))
                .save(output);

        // 星币尘:星币 + 下界石英 + 荧石粉(无序)
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.STAR_COIN_DUST.get())
                .requires(ModItems.STAR_COIN.get())
                .requires(Items.QUARTZ)
                .requires(Items.GLOWSTONE_DUST)
                .unlockedBy("has_star_coin", has(ModItems.STAR_COIN.get()))
                .save(output);

        // 标记涂料:空/Y/空, I/R/I, C/G/C(Y=岩浆膏,I=铁锭,R=红石粉,C=铜锭,G=金锭)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.MARK_PAINT.get())
                .pattern(" Y ")
                .pattern("IRI")
                .pattern("CGC")
                .define('Y', Items.MAGMA_CREAM)
                .define('I', Items.IRON_INGOT)
                .define('R', Items.REDSTONE)
                .define('C', Items.COPPER_INGOT)
                .define('G', Items.GOLD_INGOT)
                .unlockedBy("has_magma_cream", has(Items.MAGMA_CREAM))
                .save(output);

        // 风水师立牌(zhao,传奇):GCG/RER/ZPZ(G=金锭,C=指南针,E=空白立牌,R=红石块,
        // Z=钻石骰子,P=黄金星盘);与「大当家立牌」同为"钻石骰子 + 黄金星盘"档 → 传奇(UNCOMMON)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.ZHAO_SIGN.get())
                .pattern("GCG")
                .pattern("RER")
                .pattern("ZPZ")
                .define('G', Items.GOLD_INGOT)
                .define('C', Items.COMPASS)
                .define('E', ModItems.BLANK_SIGN.get())
                .define('R', Items.REDSTONE_BLOCK)
                .define('Z', ModItems.DIAMOND_DICE.get())
                .define('P', ModItems.GOLDEN_STAR_PLATE.get())
                .unlockedBy("has_blank_sign", has(ModItems.BLANK_SIGN.get()))
                .save(output);
        // 教主立牌(teru,传奇):GCG/LEL/ZPZ(G=金锭,C=指南针,E=空白立牌,L=荧石粉,
        // Z=钻石骰子,P=黄金星盘);与「风水师立牌」同档（钻石骰子 + 黄金星盘）→ 传奇(UNCOMMON)
        // 但**材料须与 zhao 区分**：zhao 保持 GCG/RER/ZPZ(红石块×2)，本立牌填充材料改用荧石粉×2
        // （主动「降神」/被动「狐光」取「光」意）——两条配方形状+材料完全相同会让其中一条永远合不出来。
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.TERU_SIGN.get())
                .pattern("GCG")
                .pattern("LEL")
                .pattern("ZPZ")
                .define('G', Items.GOLD_INGOT)
                .define('C', Items.COMPASS)
                .define('E', ModItems.BLANK_SIGN.get())
                .define('L', Items.GLOWSTONE_DUST)
                .define('Z', ModItems.DIAMOND_DICE.get())
                .define('P', ModItems.GOLDEN_STAR_PLATE.get())
                .unlockedBy("has_blank_sign", has(ModItems.BLANK_SIGN.get()))
                .save(output);
        // 绿洲女王立牌(nardis,稀有):CYC/TET/TDT(C=仙人掌,Y=黄色染料,T=陶瓦,
        // E=空白立牌,D=黄金骰子);「黄金骰子(无星盘)」档,与上班族立牌 padman(:229-239)同档
        // 但**材料须与 padman 区分**：padman 保持 WYW/TET/TDT(凋灵骷髅头×2)，本立牌填充材料改用
        // 仙人掌×2（绿洲主题）——两条配方形状+材料完全相同会让其中一条永远合不出来。
        // 立牌置中、骰子固定中下,无占位洞 → 稀有(RARE)。
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.NARDIS_SIGN.get())
                .pattern("CYC")
                .pattern("TET")
                .pattern("TDT")
                .define('C', Items.CACTUS)
                .define('Y', Items.YELLOW_DYE)
                .define('T', Items.TERRACOTTA)
                .define('E', ModItems.BLANK_SIGN.get())
                .define('D', ModItems.GOLDEN_DICE.get())
                .unlockedBy("has_blank_sign", has(ModItems.BLANK_SIGN.get()))
                .save(output);
        // 蛟龙立牌(mamushi,传奇):SPS/SES/GDG(S=海晶碎片,P=金块,E=空白立牌,G=黄金星盘×2,
        // D=钻石骰子);「钻石骰子 + 黄金星盘」档,与大当家立牌 fen 同档（本文件 :1190-1201）
        // 但**材料须与 fen 区分**——原样照抄会让两条配方的形状+材料完全相同（原版合成台按第一条匹配
        // 返回结果 ⇒ 其中一条永远合不出来），故填充材料由红石块×4 改为海晶碎片×4（蛟龙＝水属）；
        // 立牌置中、骰子固定中下,无占位洞 → 传奇(UNCOMMON)。
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.MAMUSHI_SIGN.get())
                .pattern("SPS")
                .pattern("SES")
                .pattern("GDG")
                .define('E', ModItems.BLANK_SIGN.get())
                .define('S', Items.PRISMARINE_SHARD)
                .define('P', Items.GOLD_BLOCK)
                .define('G', ModItems.GOLDEN_STAR_PLATE.get())
                .define('D', ModItems.DIAMOND_DICE.get())
                .unlockedBy("has_blank_sign", has(ModItems.BLANK_SIGN.get()))
                .save(output);
        // 符卡-福 / 符卡-祸:**无配方**(专属牌,仅由风水师立牌的被动「福祸相倚」与主动「白泽赐福」
        // 及「心意相连」发放,与活体书页/命运的指引等专属牌同一口径:不进随机池、不进合成表)。
    }
}
