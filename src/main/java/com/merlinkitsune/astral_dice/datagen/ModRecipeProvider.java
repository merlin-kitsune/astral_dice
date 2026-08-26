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

        // T2 中:看板立牌(3 白色染料 + 2 蓝色染料 + 骰子×1 + 星币×2,有序,空白立牌置中,骰子置中下)
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
                .define('D', ModItems.GOLDEN_DICE.get())
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
                .define('D', ModItems.GOLDEN_DICE.get())
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

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.CUTTER_CHIP.get())
                .pattern(" G ")
                .pattern("IBI")
                .pattern(" P ")
                .define('I', Items.IRON_SWORD)
                .define('G', Items.GOLDEN_APPLE)
                .define('B', ModItems.BLANK_CHIP.get())
                .define('P', ModItems.STAR_PLATE.get())
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // 手电筒-强光:空白筹码居中,星盘在下排,增加黄色染色玻璃
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.FLASHLIGHT_CHIP.get())
                .pattern("LYL")
                .pattern(" B ")
                .pattern("IPI")
                .define('L', Items.REDSTONE_LAMP)
                .define('Y', Items.YELLOW_STAINED_GLASS)
                .define('B', ModItems.BLANK_CHIP.get())
                .define('I', Items.IRON_INGOT)
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

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.SCOPE_CHIP.get())
                .pattern("APA")
                .pattern("CBC")
                .pattern("IWI")
                .define('A', Items.AMETHYST_SHARD)
                .define('P', ModItems.STAR_PLATE.get())
                .define('C', Items.COPPER_INGOT)
                .define('B', ModItems.BLANK_CHIP.get())
                .define('I', Items.IRON_INGOT)
                .define('W', Items.OAK_BUTTON)
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

        // 医疗箱-紧急治疗:5 粘液球 + 3 星币,空白筹码居中
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.MEDKIT_EMERGENCY_CHIP.get())
                .pattern("SSS")
                .pattern("SBS")
                .pattern("CCC")
                .define('S', Items.SLIME_BALL)
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

        // 维生素药丸:发酵蛛眼上排 + 红石中轴 + 空白筹码居中 + 星盘下排
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.VITAMIN_PILL_CHIP.get())
                .pattern("FFF")
                .pattern("RCR")
                .pattern("PPP")
                .define('F', Items.FERMENTED_SPIDER_EYE)
                .define('R', Items.REDSTONE)
                .define('C', ModItems.BLANK_CHIP.get())
                .define('P', ModItems.STAR_PLATE.get())
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // 标靶:5 原版标靶 + 3 星币,空白筹码居中
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.TARGET_CHIP.get())
                .pattern("TTT")
                .pattern("TBT")
                .pattern("CCC")
                .define('T', Items.TARGET)
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

        // 八面骰:补 1 骰子,空白筹码居中,星币在下排
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.EIGHT_SIDED_DICE.get())
                .pattern("GDG")
                .pattern("GBG")
                .pattern("SSS")
                .define('G', Items.GOLD_INGOT)
                .define('D', ModItems.DICE.get())
                .define('B', ModItems.BLANK_CHIP.get())
                .define('S', ModItems.STAR_COIN.get())
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

        // 标记喷灌:3 星币 + 2 青金石 + 3 下界疣,空白筹码居中
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.MARKER_SPRAYER_CHIP.get())
                .pattern("NNN")
                .pattern("LBL")
                .pattern("CCC")
                .define('N', Items.NETHER_WART)
                .define('L', Items.LAPIS_LAZULI)
                .define('B', ModItems.BLANK_CHIP.get())
                .define('C', ModItems.STAR_COIN.get())
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // 魔法秘典:2 书与笔 + 1 回响碎片 + 2 附魔瓶 + 3 星币,空白筹码居中
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.MAGIC_TOME_CHIP.get())
                .pattern("QRQ")
                .pattern("EBE")
                .pattern("CCC")
                .define('Q', Items.WRITABLE_BOOK)
                .define('R', Items.ECHO_SHARD)
                .define('E', Items.EXPERIENCE_BOTTLE)
                .define('B', ModItems.BLANK_CHIP.get())
                .define('C', ModItems.STAR_COIN.get())
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // 大背包:3 皮革 + 2 铁锭 + 3 星币,空白筹码居中
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.BIG_BACKPACK_CHIP.get())
                .pattern("LLL")
                .pattern("IBI")
                .pattern("CCC")
                .define('L', Items.LEATHER)
                .define('I', Items.IRON_INGOT)
                .define('B', ModItems.BLANK_CHIP.get())
                .define('C', ModItems.STAR_COIN.get())
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // 忍术飞镖:1 星盘 + 2 黄金星盘 + 1 红石块 + 2 下界合金锭 + 2 发光箭,空白筹码居中
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.NINJA_STAR_CHIP.get())
                .pattern("NLN")
                .pattern("ABA")
                .pattern("GPG")
                .define('N', Items.NETHERITE_INGOT)
                .define('L', Items.REDSTONE_BLOCK)
                .define('A', Items.SPECTRAL_ARROW)
                .define('B', ModItems.BLANK_CHIP.get())
                .define('G', ModItems.GOLDEN_STAR_PLATE.get())
                .define('P', ModItems.STAR_PLATE.get())
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // 手持风扇-小:羽毛上排 + 竹子中轴 + 空白筹码居中 + 星币下排
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.HAND_FAN_SMALL_CHIP.get())
                .pattern("YYY")
                .pattern("ZCZ")
                .pattern("BBB")
                .define('Y', Items.FEATHER)
                .define('Z', Items.BAMBOO)
                .define('C', ModItems.BLANK_CHIP.get())
                .define('B', ModItems.STAR_COIN.get())
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
        // ATM机:中央空白筹码 + 中轴金锭 + 四角星币
        // ATM机:5 金锭 + 3 星币,空白筹码居中
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.ATM.get())
                .pattern("GGG")
                .pattern("GBG")
                .pattern("CCC")
                .define('G', Items.GOLD_INGOT)
                .define('B', ModItems.BLANK_CHIP.get())
                .define('C', ModItems.STAR_COIN.get())
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // 银行卡-余额少:中央空白筹码 + 中轴星币 + 四角纸
        // 银行卡-余额少:3 星币 + 2 金块 + 3 金锭,空白筹码居中
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.BANK_CARD_LOW.get())
                .pattern("GGG")
                .pattern("KBK")
                .pattern("CCC")
                .define('G', Items.GOLD_INGOT)
                .define('K', Items.GOLD_BLOCK)
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

        // 魔法箭袋:中央空白筹码 + 中轴星币 + 羽毛/箭四角
        // 魔法箭袋:3 星盘 + 2 发光箭 + 1 回响碎片 + 2 书与笔,空白筹码居中
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.MAGIC_QUIVER.get())
                .pattern("ARA")
                .pattern("QBQ")
                .pattern("PPP")
                .define('A', Items.SPECTRAL_ARROW)
                .define('R', Items.ECHO_SHARD)
                .define('Q', Items.WRITABLE_BOOK)
                .define('B', ModItems.BLANK_CHIP.get())
                .define('P', ModItems.STAR_PLATE.get())
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // 缓冲盾牌:中央空白筹码 + 中轴星币 + 铁锭/盾牌
        // 缓冲盾牌:3 星币 + 2 盾牌 + 3 钻石,空白筹码居中
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.BUFFER_SHIELD.get())
                .pattern("DDD")
                .pattern("HBH")
                .pattern("CCC")
                .define('D', Items.DIAMOND)
                .define('H', Items.SHIELD)
                .define('B', ModItems.BLANK_CHIP.get())
                .define('C', ModItems.STAR_COIN.get())
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // 星币锤:中央空白筹码 + 中轴星盘/星币 + 金锭四角
        // 星币锤:3 黄金星盘 + 2 星币袋 + 1 重锤 + 2 下界合金锭,空白筹码居中
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.STAR_COIN_HAMMER.get())
                .pattern("SMS")
                .pattern("NBN")
                .pattern("GGG")
                .define('S', ModItems.STAR_COIN_BAG.get())
                .define('M', Items.MACE)
                .define('N', Items.NETHERITE_INGOT)
                .define('B', ModItems.BLANK_CHIP.get())
                .define('G', ModItems.GOLDEN_STAR_PLATE.get())
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // 诅咒之剑:金剑上排 + 谜之炖菜/爆裂紫颂果中轴 + 空白筹码居中 + 星币下排
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.CURSED_SWORD.get())
                .pattern("JMJ")
                .pattern("ZCZ")
                .pattern("BBB")
                .define('J', Items.GOLDEN_SWORD)
                .define('M', Items.SUSPICIOUS_STEW)
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

        // 贯穿之铳:下界合金碎片上排 + 潮涌核心/回响碎片中轴 + 空白筹码居中 + 黄金星盘下排
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.PIERCING_GUN.get())
                .pattern("XSX")
                .pattern("HCH")
                .pattern("PPP")
                .define('X', Items.NETHERITE_SCRAP)
                .define('S', Items.CONDUIT)
                .define('H', Items.ECHO_SHARD)
                .define('C', ModItems.BLANK_CHIP.get())
                .define('P', ModItems.GOLDEN_STAR_PLATE.get())
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // 可口糖果:糖上排 + 曲奇/雕刻南瓜中轴 + 空白筹码居中 + 星盘下排
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.CANDY_CHIP.get())
                .pattern("TQT")
                .pattern("NCN")
                .pattern("PPP")
                .define('T', Items.SUGAR)
                .define('Q', Items.COOKIE)
                .define('N', Items.CARVED_PUMPKIN)
                .define('C', ModItems.BLANK_CHIP.get())
                .define('P', ModItems.STAR_PLATE.get())
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // 友情徽章:治疗药水上排 + 附魔金苹果/紫水晶中轴 + 空白筹码居中 + 星盘下排
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.FRIENDSHIP_BADGE.get())
                .pattern("YJY")
                .pattern("ZCZ")
                .pattern("PPP")
                .define('Y', Items.POTION)
                .define('J', Items.ENCHANTED_GOLDEN_APPLE)
                .define('Z', Items.AMETHYST_SHARD)
                .define('C', ModItems.BLANK_CHIP.get())
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
                .define('B', Items.BLACK_CONCRETE)
                .define('W', Items.WHITE_CONCRETE)
                .define('P', ModItems.STAR_PLATE.get())
                .define('Z', ModItems.DIAMOND_DICE.get())
                .unlockedBy("has_blank_sign", has(ModItems.BLANK_SIGN.get()))
                .save(output);
    }
}
