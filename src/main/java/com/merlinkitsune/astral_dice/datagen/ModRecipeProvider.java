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
                .pattern(" I ")
                .pattern("ICI")
                .pattern(" I ")
                .define('I', Items.IRON_INGOT)
                .define('C', ModItems.STAR_COIN.get())
                .unlockedBy("has_star_coin", has(ModItems.STAR_COIN.get()))
                .save(output);

        // 攻击(中):1 铁剑 + 1 骰子
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.ATTACK_CARD_MEDIUM.get())
                .requires(Items.IRON_SWORD)
                .requires(ModItems.DICE.get())
                .unlockedBy("has_dice", has(ModItems.DICE.get()))
                .save(output);

        // 防御(中):1 铁锭 + 1 骰子
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.DEFENSE_CARD_MEDIUM.get())
                .requires(Items.IRON_INGOT)
                .requires(ModItems.DICE.get())
                .unlockedBy("has_dice", has(ModItems.DICE.get()))
                .save(output);

        // 暗影突袭:1 墨囊 + 2 星币
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.ATTACK_CARD_SHADOW_STRIKE.get())
                .requires(Items.INK_SAC)
                .requires(ModItems.STAR_COIN.get(), 2)
                .unlockedBy("has_star_coin", has(ModItems.STAR_COIN.get()))
                .save(output);

        // 名刀嘎呜切:1 钻石 + 4 星币(有序,十字排布,钻石置中)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.ATTACK_CARD_MEITO.get())
                .pattern(" S ")
                .pattern("SDS")
                .pattern(" S ")
                .define('S', ModItems.STAR_COIN.get())
                .define('D', Items.DIAMOND)
                .unlockedBy("has_star_coin", has(ModItems.STAR_COIN.get()))
                .save(output);

        // 蓄力:1 红石块 + 4 星盘(有序,十字排布,红石块置中)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.ATTACK_CARD_CHARGE.get())
                .pattern(" S ")
                .pattern("SRS")
                .pattern(" S ")
                .define('S', ModItems.STAR_PLATE.get())
                .define('R', Items.REDSTONE_BLOCK)
                .unlockedBy("has_star_plate", has(ModItems.STAR_PLATE.get()))
                .save(output);

        // 岿然不动:1 金锭 + 2 星币
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.EFFECT_CARD_UNWAVERING.get())
                .requires(Items.GOLD_INGOT)
                .requires(ModItems.STAR_COIN.get(), 2)
                .unlockedBy("has_star_coin", has(ModItems.STAR_COIN.get()))
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

        // T1-2 中:经商立牌(金锭 + 绿宝石 + 星币,经济系)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.PARUNAN_SIGN.get())
                .pattern("SGS")
                .pattern("ECE")
                .pattern("SGS")
                .define('S', ModItems.STAR_COIN.get())
                .define('G', Items.GOLD_INGOT)
                .define('C', ModItems.BLANK_SIGN.get())
                .define('E', Items.EMERALD)
                .unlockedBy("has_blank_sign", has(ModItems.BLANK_SIGN.get()))
                .save(output);

        // T2 中:扫地机立牌(铁锭×4 + 甘蔗×2 + 骰子×2,有序,空白立牌置中)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.JASMINE_SIGN.get())
                .pattern("SIS")
                .pattern("DED")
                .pattern("III")
                .define('E', ModItems.BLANK_SIGN.get())
                .define('I', Items.IRON_INGOT)
                .define('S', Items.SUGAR_CANE)
                .define('D', ModItems.DICE.get())
                .unlockedBy("has_blank_sign", has(ModItems.BLANK_SIGN.get()))
                .save(output);

        // T2 中:看板立牌(金胡萝卜×5 + 骰子×1 + 星币×2,有序,空白立牌置中,骰子置中下)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.MIMI_SIGN.get())
                .pattern("CCC")
                .pattern("SES")
                .pattern("CDC")
                .define('E', ModItems.BLANK_SIGN.get())
                .define('C', Items.GOLDEN_CARROT)
                .define('S', ModItems.STAR_COIN.get())
                .define('D', ModItems.DICE.get())
                .unlockedBy("has_blank_sign", has(ModItems.BLANK_SIGN.get()))
                .save(output);

        // T2 中:忍者立牌(黄金骰子 + 羽毛×3 + 黑色染料×2 + 发射器×2,有序,空白立牌置中,骰子置中下)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.KOMACHI_SIGN.get())
                .pattern("FFF")
                .pattern("BEB")
                .pattern("LDL")
                .define('E', ModItems.BLANK_SIGN.get())
                .define('F', Items.FEATHER)
                .define('B', Items.BLACK_DYE)
                .define('L', Items.DISPENSER)
                .define('D', ModItems.GOLDEN_DICE.get())
                .unlockedBy("has_blank_sign", has(ModItems.BLANK_SIGN.get()))
                .save(output);

        // T2 中:大侦探立牌(黄金骰子 + 望远镜 + 金锭×2 + 星币×2 + 星盘×2,有序,空白立牌置中,骰子置中下,望远镜置中上)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.FANNY_SIGN.get())
                .pattern("STS")
                .pattern("GEG")
                .pattern("PDP")
                .define('E', ModItems.BLANK_SIGN.get())
                .define('T', Items.SPYGLASS)
                .define('S', ModItems.STAR_COIN.get())
                .define('G', Items.GOLD_INGOT)
                .define('P', ModItems.STAR_PLATE.get())
                .define('D', ModItems.GOLDEN_DICE.get())
                .unlockedBy("has_blank_sign", has(ModItems.BLANK_SIGN.get()))
                .save(output);

        // T3 强:调查员立牌(钻石骰子 + 书×2 + 钟 + 星币×2 + 星盘×2,有序,空白立牌置中,骰子置中下)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.RIN_SIGN.get())
                .pattern("SCS")
                .pattern("BEB")
                .pattern("PDP")
                .define('E', ModItems.BLANK_SIGN.get())
                .define('C', Items.CLOCK)
                .define('S', ModItems.STAR_COIN.get())
                .define('B', Items.BOOK)
                .define('P', ModItems.STAR_PLATE.get())
                .define('D', ModItems.DIAMOND_DICE.get())
                .unlockedBy("has_blank_sign", has(ModItems.BLANK_SIGN.get()))
                .save(output);

        // T3 强:占星师立牌(黄金骰子 + 青金石×2 + 时钟 + 金锭×4,有序,空白立牌置中,骰子置中下)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.HAIQING_SIGN.get())
                .pattern("GCG")
                .pattern("LEL")
                .pattern("GDG")
                .define('E', ModItems.BLANK_SIGN.get())
                .define('C', Items.CLOCK)
                .define('L', Items.LAPIS_LAZULI)
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

        // T3 强:上班族立牌(黄金骰子 + 凋灵骷髅头×2 + 陶瓦×5,有序,空白立牌置中,骰子置中下)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.PADMAN_SIGN.get())
                .pattern("TTT")
                .pattern("WEW")
                .pattern("TDT")
                .define('E', ModItems.BLANK_SIGN.get())
                .define('T', Items.TERRACOTTA)
                .define('W', Items.WITHER_SKELETON_SKULL)
                .define('D', ModItems.GOLDEN_DICE.get())
                .unlockedBy("has_blank_sign", has(ModItems.BLANK_SIGN.get()))
                .save(output);

        // T4 极强:护法立牌(钻石骰子 + 铁剑×2 + 红色染料×2 + 青色染料×2 + 星盘×1,有序,空白立牌置中,骰子置中下)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.MISAKI_SIGN.get())
                .pattern("RPR")
                .pattern("IEI")
                .pattern("CDC")
                .define('E', ModItems.BLANK_SIGN.get())
                .define('R', Items.RED_DYE)
                .define('P', ModItems.STAR_PLATE.get())
                .define('I', Items.IRON_SWORD)
                .define('C', Items.CYAN_DYE)
                .define('D', ModItems.DIAMOND_DICE.get())
                .unlockedBy("has_blank_sign", has(ModItems.BLANK_SIGN.get()))
                .save(output);

        // T4 极强:秘密侦探立牌(下界合金骰子 + 望远镜×2 + 指南针 + 星盘×2 + 金苹果×2,有序,空白立牌置中,骰子置中下)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.BONNIE_SIGN.get())
                .pattern("PCP")
                .pattern("TET")
                .pattern("ADA")
                .define('E', ModItems.BLANK_SIGN.get())
                .define('P', ModItems.STAR_PLATE.get())
                .define('C', Items.COMPASS)
                .define('T', Items.SPYGLASS)
                .define('A', Items.GOLDEN_APPLE)
                .define('D', ModItems.NETHERITE_DICE.get())
                .unlockedBy("has_blank_sign", has(ModItems.BLANK_SIGN.get()))
                .save(output);

        // 王之力:1 钻石剑 + 2 星币
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.EFFECT_CARD_KING_POWER.get())
                .requires(Items.DIAMOND_SWORD)
                .requires(ModItems.STAR_COIN.get(), 2)
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

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.FLASHLIGHT_CHIP.get())
                .pattern("L L")
                .pattern(" B ")
                .pattern("IPI")
                .define('L', Items.REDSTONE_LAMP)
                .define('I', Items.IRON_INGOT)
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

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.EAGLE_SCOPE_CHIP.get())
                .pattern("GPG")
                .pattern("GTG")
                .pattern("GSG")
                .define('G', Items.GOLD_INGOT)
                .define('P', ModItems.GOLDEN_STAR_PLATE.get())
                .define('T', Items.TINTED_GLASS)
                .define('S', ModItems.SCOPE_CHIP.get())
                .unlockedBy("has_scope_chip", has(ModItems.SCOPE_CHIP.get()))
                .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.MEDKIT_EMERGENCY_CHIP.get())
                .pattern("III")
                .pattern("IBI")
                .pattern("D G")
                .define('I', Items.IRON_INGOT)
                .define('B', ModItems.BLANK_CHIP.get())
                .define('D', Items.OXEYE_DAISY)
                .define('G', Items.GOLDEN_APPLE)
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.MEDKIT_COMPLETE_CHIP.get())
                .pattern("HPH")
                .pattern("IEI")
                .pattern("SMS")
                .define('H', Items.HONEY_BOTTLE)
                .define('P', ModItems.GOLDEN_STAR_PLATE.get())
                .define('I', Items.IRON_INGOT)
                .define('E', Items.ENCHANTED_GOLDEN_APPLE)
                .define('S', ModItems.STAR_COIN.get())
                .define('M', ModItems.MEDKIT_EMERGENCY_CHIP.get())
                .unlockedBy("has_medkit_emergency", has(ModItems.MEDKIT_EMERGENCY_CHIP.get()))
                .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.TARGET_CHIP.get())
                .pattern("TBT")
                .pattern("BCB")
                .pattern("TBT")
                .define('T', Items.TARGET)
                .define('B', Items.BAMBOO_BLOCK)
                .define('C', ModItems.BLANK_CHIP.get())
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

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.EIGHT_SIDED_DICE.get())
                .pattern("GGG")
                .pattern("DB ")
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
        // 注:黄金骰子/钻石骰子使用自定义配方类型 astral_dice:dice_upgrade(升级时继承骰子 WeaponEnhancement 配置),
        //     配方定义在 src/main/resources/data/astral_dice/recipe/(golden_dice.json / diamond_dice.json),此处不生成。

        // 合金骰子:锻造台(锻造模板 + 钻石骰子 + 下界合金锭)升级
        net.minecraft.data.recipes.SmithingTransformRecipeBuilder.smithing(
                        net.minecraft.world.item.crafting.Ingredient.of(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE),
                        net.minecraft.world.item.crafting.Ingredient.of(ModItems.DIAMOND_DICE.get()),
                        net.minecraft.world.item.crafting.Ingredient.of(Items.NETHERITE_INGOT),
                        RecipeCategory.TOOLS, ModItems.NETHERITE_DICE.get())
                .unlocks("has_diamond_dice", has(ModItems.DIAMOND_DICE.get()))
                .save(output, ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "netherite_dice_smithing"));

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

        // 轨道炮(+8):1 纸 + 1 TNT + 2 星币
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.ORBITAL_STRIKE_CARD.get())
                .requires(Items.PAPER)
                .requires(Items.TNT)
                .requires(ModItems.STAR_COIN.get(), 2)
                .unlockedBy("has_star_coin", has(ModItems.STAR_COIN.get()))
                .save(output);

        // 定向爆破(+5 AOE):1 纸 + 2 火药 + 1 燧石 + 3 星币(有序,对称三角排布)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.DIRECTIONAL_BLAST_CARD.get())
                .pattern(" P ")
                .pattern("GSG")
                .pattern("SFS")
                .define('P', Items.PAPER)
                .define('G', Items.GUNPOWDER)
                .define('S', ModItems.STAR_COIN.get())
                .define('F', Items.FLINT)
                .unlockedBy("has_star_coin", has(ModItems.STAR_COIN.get()))
                .save(output);

        // === 筹码(均含空白筹码) ===
        // 美工刀-锋利:美工刀 + 钻石×4 + 星盘(升级)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.CUTTER_BLADE_CHIP.get())
                .pattern(" D ")
                .pattern("DCD")
                .pattern(" P ")
                .define('D', Items.DIAMOND)
                .define('C', ModItems.CUTTER_CHIP.get())
                .define('P', ModItems.STAR_PLATE.get())
                .unlockedBy("has_cutter_chip", has(ModItems.CUTTER_CHIP.get()))
                .save(output);

        // 标记喷灌:空白筹码 + 喷溅药水 + 青金石 + 4 星币
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.MARKER_SPRAYER_CHIP.get())
                .requires(ModItems.BLANK_CHIP.get())
                .requires(Items.SPLASH_POTION)
                .requires(Items.LAPIS_LAZULI)
                .requires(ModItems.STAR_COIN.get(), 4)
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // 魔法秘典:空白筹码 + 书 + 青金石×2 + 4 星币
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.MAGIC_TOME_CHIP.get())
                .requires(ModItems.BLANK_CHIP.get())
                .requires(Items.BOOK)
                .requires(Items.LAPIS_LAZULI, 2)
                .requires(ModItems.STAR_COIN.get(), 4)
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // 大背包:空白筹码 + 皮革×4 + 铁锭×2 + 3 星币
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.BIG_BACKPACK_CHIP.get())
                .requires(ModItems.BLANK_CHIP.get())
                .requires(Items.LEATHER, 4)
                .requires(Items.IRON_INGOT, 2)
                .requires(ModItems.STAR_COIN.get(), 3)
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // 忍术飞镖:空白筹码 + 铁锭×2 + 箭 + 4 星币
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.NINJA_STAR_CHIP.get())
                .requires(ModItems.BLANK_CHIP.get())
                .requires(Items.IRON_INGOT, 2)
                .requires(Items.ARROW)
                .requires(ModItems.STAR_COIN.get(), 4)
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // 手持风扇-大:空白筹码 + 羽毛×2 + 竹子×2 + 4 星币
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.HAND_FAN_BIG_CHIP.get())
                .requires(ModItems.BLANK_CHIP.get())
                .requires(Items.FEATHER, 2)
                .requires(Items.BAMBOO, 2)
                .requires(ModItems.STAR_COIN.get(), 4)
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // === 新筹码(全部为 shape:空白筹码居中,mod 物品在中轴,原版材料在四角) ===
        // ATM机:中央空白筹码 + 中轴金锭 + 四角星币
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.ATM.get())
                .pattern("GSG")
                .pattern("SES")
                .pattern("GSG")
                .define('E', ModItems.BLANK_CHIP.get())
                .define('S', ModItems.STAR_COIN.get())
                .define('G', Items.GOLD_INGOT)
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // 银行卡-余额少:中央空白筹码 + 中轴星币 + 四角纸
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.BANK_CARD_LOW.get())
                .pattern("P P")
                .pattern("SES")
                .pattern("P P")
                .define('E', ModItems.BLANK_CHIP.get())
                .define('S', ModItems.STAR_COIN.get())
                .define('P', Items.PAPER)
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // 银行卡-余额多:中央余额少 + 中轴星币 + 四角纸(升级)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.BANK_CARD_HIGH.get())
                .pattern("P P")
                .pattern("SES")
                .pattern("P P")
                .define('E', ModItems.BANK_CARD_LOW.get())
                .define('S', ModItems.STAR_COIN.get())
                .define('P', Items.PAPER)
                .unlockedBy("has_bank_card_low", has(ModItems.BANK_CARD_LOW.get()))
                .save(output);

        // 银行卡-用不完:中央空白筹码 + 中轴星币/绿宝石交替
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.BANK_CARD_UNLIMITED.get())
                .pattern("MSM")
                .pattern("SES")
                .pattern("MSM")
                .define('E', ModItems.BLANK_CHIP.get())
                .define('S', ModItems.STAR_COIN.get())
                .define('M', Items.EMERALD)
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // 拳击手套-初级:中央空白筹码 + 中轴星币 + 铁锭/皮革四角
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.BOXING_GLOVES_LOW.get())
                .pattern("LIL")
                .pattern("SES")
                .pattern("LIL")
                .define('E', ModItems.BLANK_CHIP.get())
                .define('S', ModItems.STAR_COIN.get())
                .define('I', Items.IRON_INGOT)
                .define('L', Items.LEATHER)
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // 拳击手套-中级:中央初级 + 中轴星币 + 铁锭上下(升级)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.BOXING_GLOVES_MEDIUM.get())
                .pattern(" I ")
                .pattern("SES")
                .pattern("I I")
                .define('E', ModItems.BOXING_GLOVES_LOW.get())
                .define('S', ModItems.STAR_COIN.get())
                .define('I', Items.IRON_INGOT)
                .unlockedBy("has_boxing_gloves_low", has(ModItems.BOXING_GLOVES_LOW.get()))
                .save(output);

        // 拳击手套-高级:中央中级 + 中轴星币 + 钻石上下(升级)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.BOXING_GLOVES_HIGH.get())
                .pattern(" D ")
                .pattern("SES")
                .pattern("D D")
                .define('E', ModItems.BOXING_GLOVES_MEDIUM.get())
                .define('S', ModItems.STAR_COIN.get())
                .define('D', Items.DIAMOND)
                .unlockedBy("has_boxing_gloves_medium", has(ModItems.BOXING_GLOVES_MEDIUM.get()))
                .save(output);

        // 速度轮滑-初级:中央空白筹码 + 中轴星币 + 皮革/铁锭四角
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.SPEED_SKATES_LOW.get())
                .pattern("L L")
                .pattern("SES")
                .pattern("I I")
                .define('E', ModItems.BLANK_CHIP.get())
                .define('S', ModItems.STAR_COIN.get())
                .define('L', Items.LEATHER)
                .define('I', Items.IRON_INGOT)
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // 速度轮滑-中级:中央初级 + 中轴星币 + 金锭上下(升级)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.SPEED_SKATES_MEDIUM.get())
                .pattern(" G ")
                .pattern("SES")
                .pattern("G G")
                .define('E', ModItems.SPEED_SKATES_LOW.get())
                .define('S', ModItems.STAR_COIN.get())
                .define('G', Items.GOLD_INGOT)
                .unlockedBy("has_speed_skates_low", has(ModItems.SPEED_SKATES_LOW.get()))
                .save(output);

        // 速度轮滑-高级:中央中级 + 中轴星币 + 钻石上下(升级)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.SPEED_SKATES_HIGH.get())
                .pattern(" D ")
                .pattern("SES")
                .pattern("D D")
                .define('E', ModItems.SPEED_SKATES_MEDIUM.get())
                .define('S', ModItems.STAR_COIN.get())
                .define('D', Items.DIAMOND)
                .unlockedBy("has_speed_skates_medium", has(ModItems.SPEED_SKATES_MEDIUM.get()))
                .save(output);

        // 摩托头盔-一般:中央空白筹码 + 中轴星币 + 铁锭/皮革
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.MOTO_HELMET_LOW.get())
                .pattern("III")
                .pattern("SES")
                .pattern("L L")
                .define('E', ModItems.BLANK_CHIP.get())
                .define('S', ModItems.STAR_COIN.get())
                .define('I', Items.IRON_INGOT)
                .define('L', Items.LEATHER)
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // 摩托头盔-中级:中央一般 + 中轴星币 + 金锭上下(升级)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.MOTO_HELMET_MEDIUM.get())
                .pattern(" G ")
                .pattern("SES")
                .pattern("G G")
                .define('E', ModItems.MOTO_HELMET_LOW.get())
                .define('S', ModItems.STAR_COIN.get())
                .define('G', Items.GOLD_INGOT)
                .unlockedBy("has_moto_helmet_low", has(ModItems.MOTO_HELMET_LOW.get()))
                .save(output);

        // 摩托头盔-高级:中央中级 + 中轴星币 + 钻石(升级)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.MOTO_HELMET_HIGH.get())
                .pattern("D D")
                .pattern("SES")
                .pattern(" D ")
                .define('E', ModItems.MOTO_HELMET_MEDIUM.get())
                .define('S', ModItems.STAR_COIN.get())
                .define('D', Items.DIAMOND)
                .unlockedBy("has_moto_helmet_medium", has(ModItems.MOTO_HELMET_MEDIUM.get()))
                .save(output);

        // 夹心饼干-一般:中央空白筹码 + 中轴星币 + 面包/糖四角
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.SANDWICH_LOW.get())
                .pattern("BUB")
                .pattern("SES")
                .pattern("BUB")
                .define('E', ModItems.BLANK_CHIP.get())
                .define('S', ModItems.STAR_COIN.get())
                .define('B', Items.BREAD)
                .define('U', Items.SUGAR)
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // 夹心饼干-可口:中央一般 + 中轴星币 + 蛋糕上下(升级)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.SANDWICH_MEDIUM.get())
                .pattern(" C ")
                .pattern("SES")
                .pattern("C C")
                .define('E', ModItems.SANDWICH_LOW.get())
                .define('S', ModItems.STAR_COIN.get())
                .define('C', Items.CAKE)
                .unlockedBy("has_sandwich_low", has(ModItems.SANDWICH_LOW.get()))
                .save(output);

        // 夹心饼干-美味:中央可口 + 中轴星币 + 金苹果(升级)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.SANDWICH_HIGH.get())
                .pattern("A A")
                .pattern("SES")
                .pattern(" A ")
                .define('E', ModItems.SANDWICH_MEDIUM.get())
                .define('S', ModItems.STAR_COIN.get())
                .define('A', Items.GOLDEN_APPLE)
                .unlockedBy("has_sandwich_medium", has(ModItems.SANDWICH_MEDIUM.get()))
                .save(output);

        // 魔法箭袋:中央空白筹码 + 中轴星币 + 羽毛/箭四角
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.MAGIC_QUIVER.get())
                .pattern("FAF")
                .pattern("SES")
                .pattern("AFA")
                .define('E', ModItems.BLANK_CHIP.get())
                .define('S', ModItems.STAR_COIN.get())
                .define('A', Items.ARROW)
                .define('F', Items.FEATHER)
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // 缓冲盾牌:中央空白筹码 + 中轴星币 + 铁锭/盾牌
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.BUFFER_SHIELD.get())
                .pattern(" I ")
                .pattern("SES")
                .pattern("HIH")
                .define('E', ModItems.BLANK_CHIP.get())
                .define('S', ModItems.STAR_COIN.get())
                .define('I', Items.IRON_INGOT)
                .define('H', Items.SHIELD)
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // 星币锤:中央空白筹码 + 中轴星盘/星币 + 金锭四角
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.STAR_COIN_HAMMER.get())
                .pattern("GSG")
                .pattern("PEP")
                .pattern("GSG")
                .define('E', ModItems.BLANK_CHIP.get())
                .define('S', ModItems.STAR_COIN.get())
                .define('P', ModItems.STAR_PLATE.get())
                .define('G', Items.GOLD_INGOT)
                .unlockedBy("has_blank_chip", has(ModItems.BLANK_CHIP.get()))
                .save(output);

        // === 新效果牌(治疗/互动;shape:星币居中,mod 物品在中轴) ===
        // 巧克力蛋糕:中央星币 + 中轴可可豆 + 纸/糖
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.CHOCOLATE_CAKE.get())
                .pattern("CPC")
                .pattern("PEP")
                .pattern("CUC")
                .define('E', ModItems.STAR_COIN.get())
                .define('C', Items.COCOA_BEANS)
                .define('P', Items.PAPER)
                .define('U', Items.SUGAR)
                .unlockedBy("has_star_coin", has(ModItems.STAR_COIN.get()))
                .save(output);

        // 汉堡:中央巧克力蛋糕 + 中轴星币 + 牛排/面包四角(升级)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.HAMBURGER.get())
                .pattern("BDB")
                .pattern("SES")
                .pattern("BDB")
                .define('E', ModItems.CHOCOLATE_CAKE.get())
                .define('S', ModItems.STAR_COIN.get())
                .define('B', Items.COOKED_BEEF)
                .define('D', Items.BREAD)
                .unlockedBy("has_chocolate_cake", has(ModItems.CHOCOLATE_CAKE.get()))
                .save(output);

        // 奢华大餐:中央汉堡 + 中轴星币 + 蛋糕/金胡萝卜四角(升级)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.LUXURY_FEAST.get())
                .pattern("CGC")
                .pattern("SES")
                .pattern("GCG")
                .define('E', ModItems.HAMBURGER.get())
                .define('S', ModItems.STAR_COIN.get())
                .define('C', Items.CAKE)
                .define('G', Items.GOLDEN_CARROT)
                .unlockedBy("has_hamburger", has(ModItems.HAMBURGER.get()))
                .save(output);

        // 你有我有:中央星币 + 中轴绿宝石 + 金锭四角
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.YOU_HAVE_I_HAVE.get())
                .pattern("GMG")
                .pattern("MEM")
                .pattern("GMG")
                .define('E', ModItems.STAR_COIN.get())
                .define('M', Items.EMERALD)
                .define('G', Items.GOLD_INGOT)
                .unlockedBy("has_star_coin", has(ModItems.STAR_COIN.get()))
                .save(output);

        // 加急加快:中央星币 + 中轴红石 + 糖四角
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.EXPRESS_DELIVERY.get())
                .pattern("URU")
                .pattern("RER")
                .pattern("URU")
                .define('E', ModItems.STAR_COIN.get())
                .define('R', Items.REDSTONE)
                .define('U', Items.SUGAR)
                .unlockedBy("has_star_coin", has(ModItems.STAR_COIN.get()))
                .save(output);

        // 大当家立牌:空白立牌 + 钻石骰子 + 红石块×4 + 星盘×1 + 金锭×2(有序,空白立牌置中,骰子置中下)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.FEN_SIGN.get())
                .pattern("RPR")
                .pattern("RER")
                .pattern("GDG")
                .define('E', ModItems.BLANK_SIGN.get())
                .define('R', Items.REDSTONE_BLOCK)
                .define('P', ModItems.STAR_PLATE.get())
                .define('G', Items.GOLD_INGOT)
                .define('D', ModItems.DIAMOND_DICE.get())
                .unlockedBy("has_blank_sign", has(ModItems.BLANK_SIGN.get()))
                .save(output);
    }
}
