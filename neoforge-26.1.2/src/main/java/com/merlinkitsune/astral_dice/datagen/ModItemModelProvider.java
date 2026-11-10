package com.merlinkitsune.astral_dice.datagen;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.block.ModBlocks;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.resources.Identifier;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.data.PackOutput;
import net.minecraft.world.item.Item;

/**
 * 物品模型数据生成。
 *
 * <p><b>26.1.2 迁移说明</b>:1.21.1 用的 {@code net.neoforged.neoforge.client.model.generators.ItemModelProvider}
 * 在 26.1.2 已**被删除**(该包只剩模型 builder),1.21.4 起原版自带
 * {@code net.minecraft.client.data.models.ModelProvider} + {@code ItemModelGenerators}。
 * 因此本类改为继承原版 {@code ModelProvider} 并覆写
 * {@code registerModels(BlockModelGenerators, ItemModelGenerators)};原先的 {@code itemModels.generateFlatItem(item)}
 * 等价于 {@code itemModels.generateFlatItem(item, ModelTemplates.FLAT_ITEM)}
 * —— 注意生成物是 **1.21.4+ 的物品模型定义**({@code assets/astral_dice/items/<id>.json}),
 * 这在新版本是必需的(旧版只有 {@code models/item/<id>.json})。
 * {@code ExistingFileHelper} 也已删除,原版 provider 不再需要它。
 */
public class ModItemModelProvider extends ModelProvider {

    public ModItemModelProvider(PackOutput output) {
        super(output, AstralDiceMod.MODID);
    }

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        registerBlockStates(blockModels);
        registerBlockItemModels(blockModels);

        itemModels.generateFlatItem(ModItems.DICE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.GOLDEN_DICE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.SYKES_SIGN.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.GLASS_DICE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.NETHERRACK_DICE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.DIAMOND_DICE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.NETHERITE_DICE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.EMERALD_DICE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.OBSIDIAN_DICE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.WEIRD_DICE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.CRIMSON_DICE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.AMETHYST_DICE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.ENDER_DICE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.NETHER_STAR_DICE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.ATTACK_CARD_MEDIUM.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.ATTACK_CARD_LARGE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.ATTACK_CARD_EPIC.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.ATTACK_CARD_SHADOW_STRIKE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.ATTACK_CARD_MEITO.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.ATTACK_CARD_CHARGE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.ATTACK_CARD_FULL_POWER.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.DEFENSE_CARD_MEDIUM.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.DEFENSE_CARD_LARGE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.DEFENSE_CARD_EPIC.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.EFFECT_CARD_KING_POWER.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.EFFECT_CARD_BERSERK.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.EFFECT_CARD_UNWAVERING.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.EFFECT_CARD_FIGHT_POISON_WITH_POISON.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.EFFECT_CARD_BARRICADE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.EFFECT_CARD_REPULSION.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.REPULSION.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.STAR_COIN.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.BLANK_SIGN.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.PARUNAN_SIGN.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.JASMINE_SIGN.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.MISAKI_SIGN.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.MIMI_SIGN.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.LULU_SIGN.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.KOMACHI_SIGN.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.FLASHLIGHT_CHIP.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.CUTTER_CHIP.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.CUTTER_BLADE_CHIP.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.STAR_COIN_BAG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.MONSTER_LASER_CARD.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.MONSTER_BRICK_CARD.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.ORBITAL_STRIKE_CARD.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.DIRECTIONAL_BLAST_CARD.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.BLANK_CHIP.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.SCOPE_CHIP.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.EAGLE_SCOPE_CHIP.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.MEDKIT_EMERGENCY_CHIP.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.MEDKIT_COMPLETE_CHIP.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.VITAMIN_PILL_CHIP.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.TARGET_CHIP.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.MARKER_SPRAYER_CHIP.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.MAGIC_TOME_CHIP.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.BIG_BACKPACK_CHIP.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.NINJA_STAR_CHIP.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.HAND_FAN_SMALL_CHIP.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.HAND_FAN_BIG_CHIP.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.STAR_PLATE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.GOLDEN_STAR_PLATE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.REGENERATION_REAGENT.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.CONDUCTIVE_WIRE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.STAR_COIN_DUST.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.MARK_PAINT.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.EIGHT_SIDED_DICE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.PADMAN_SIGN.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.FANNY_SIGN.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.RIN_SIGN.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.LIVING_PAGE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.HAIQING_SIGN.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.FATE_GUIDANCE_CARD.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.PAPARA_SIGN.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.BONNIE_SIGN.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.FEN_SIGN.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.NANCY_LU_SIGN.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.MOSES_SIGN.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.PANDAMAN_SIGN.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.REN_SIGN.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.ZHAO_SIGN.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.TERU_SIGN.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.NARDIS_SIGN.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.MAMUSHI_SIGN.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.SOURI_SIGN.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.SHERRY_SIGN.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.HANNA_SIGN.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.KANGEL_SIGN.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.AME_SIGN.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.MEGAS_SIGN.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.LINGLING_SIGN.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.JILL_SIGN.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.DOROTHY_SIGN.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.ALANA_SIGN.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.DEEPSEEK_SIGN.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.FU_CARD.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.HUO_CARD.get(), ModelTemplates.FLAT_ITEM);
        // 蛟龙立牌(mamushi)专属战斗牌(2026-09-27)
        itemModels.generateFlatItem(ModItems.ATTACK_CARD_BITE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.ATTACK_CARD_DRAGON_ROAR.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.CHOCOLATE_CAKE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.HAMBURGER.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.LUXURY_FEAST.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.YOU_HAVE_I_HAVE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.EXPRESS_DELIVERY.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.ATM.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.BANK_CARD_LOW.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.BANK_CARD_HIGH.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.BANK_CARD_UNLIMITED.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.BOXING_GLOVES_LOW.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.BOXING_GLOVES_MEDIUM.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.BOXING_GLOVES_HIGH.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.SPEED_SKATES_LOW.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.SPEED_SKATES_MEDIUM.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.SPEED_SKATES_HIGH.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.MOTO_HELMET_LOW.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.MOTO_HELMET_MEDIUM.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.MOTO_HELMET_HIGH.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.SANDWICH_LOW.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.SANDWICH_MEDIUM.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.SANDWICH_HIGH.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.ADRENALINE_LOW.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.ADRENALINE_HIGH.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.MAGIC_QUIVER.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.BUFFER_SHIELD.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.STAR_COIN_HAMMER.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.CURSED_SWORD.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.REVENGE_HALBERD.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.PIERCING_GUN.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.CANDY_CHIP.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.FRIENDSHIP_BADGE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.SATELLITE_CHIP.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.WARP_ENGINE_CHIP.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.ENERGY_RECYCLER.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.ELECTRIC_SWORD.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.PERPETUAL_MOTION.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.CURRENT_CORE_CHIP.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.ADVANCED_PERIPHERALS.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.BIG_BOWL_STEW_CHIP.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.MEMBER_RECOMMENDATION_CHIP.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.BOOKMARK_CHIP.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.PIGGY_BANK_CHIP.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.SMART_WATCH_CHIP.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.ELECTRIC_GLOVE_CHIP.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.AIRBAG_CHIP.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.RAILGUN_CHIP.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.PRIMORDIAL_CORE_CHIP.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.WHETSTONE_CHIP.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.EXTRA_BATTERY_CHIP.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.PURPLE_SHOOTING_STAR_CHIP.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.GOLDEN_SHOOTING_STAR_CHIP.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.FLASHLIGHT_BURST_CHIP.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.FORTUNE_STICK_CHIP.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.ABYSS_REAGENT_CHIP.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.INFINITE_GRID_CHIP.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.SENSOR_CHIP.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.EVENT_TICKET.get(), ModelTemplates.FLAT_ITEM);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(AstralDiceMod.MODID, path);
    }

    /**
     * 方块状态：26.1.2 的 ModelProvider 会对注册表里每个方块做「是否生成了 blockstate」校验，
     * 缺一个就抛 Missing blockstate definitions。这里把 5 个方块指向仓库中手写的自定义模型，
     * 不生成新模型（这些模型是 Blockbench 产出的，不是 trivial cube）。
     */
    private void registerBlockStates(BlockModelGenerators blockModels) {
        blockModels.blockStateOutput.accept(
                MultiVariantGenerator.dispatch(ModBlocks.CARD_SHOP.get(), BlockModelGenerators.plainVariant(id("block/card_shop")))
                        .with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
        blockModels.blockStateOutput.accept(
                MultiVariantGenerator.dispatch(ModBlocks.CHIP_SHOP.get(), BlockModelGenerators.plainVariant(id("block/chip_shop")))
                        .with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
        blockModels.blockStateOutput.accept(
                BlockModelGenerators.createSimpleBlock(ModBlocks.EVENT_DRAWER.get(), BlockModelGenerators.plainVariant(id("block/event_drawer"))));
        blockModels.blockStateOutput.accept(
                BlockModelGenerators.createSimpleBlock(ModBlocks.BARRICADE_COLLISION.get(), BlockModelGenerators.plainVariant(id("block/barricade_collision"))));
        blockModels.blockStateOutput.accept(
                BlockModelGenerators.createSimpleBlock(ModBlocks.ABYSS_TENTACLE_COLLISION.get(), BlockModelGenerators.plainVariant(id("block/abyss_tentacle_collision"))));
    }

    /** 方块物品的 item 模型：父模型直接指向方块模型（与手写 models/item/*.json 一致）。 */
    private void registerBlockItemModels(BlockModelGenerators blockModels) {
        blockModels.registerSimpleItemModel(ModBlocks.CARD_SHOP.get(), id("block/card_shop"));
        blockModels.registerSimpleItemModel(ModBlocks.CHIP_SHOP.get(), id("block/chip_shop"));
        blockModels.registerSimpleItemModel(ModBlocks.EVENT_DRAWER.get(), id("block/event_drawer"));
    }
}