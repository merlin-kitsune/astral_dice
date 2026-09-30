package com.merlinkitsune.astral_dice.datagen;

import com.merlinkitsune.astral_dice.item.ModItems;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricModelProvider;
import net.minecraft.data.models.BlockModelGenerators;
import net.minecraft.data.models.ItemModelGenerators;
import net.minecraft.data.models.model.ModelTemplates;

/**
 * 物品模型 provider(Fabric 侧)。
 *
 * <p>对应 forge 线的 {@code ModItemModelProvider#basicItem(item)} —— 两者的产出**逐字节等价**:
 * 都是 {@code assets/<modid>/models/item/<name>.json},内容为
 * {@code {"parent": "item/generated", "textures": {"layer0": "<modid>:item/<name>"}}}。
 * <p>本模组**没有方块** ⇒ {@link #generateBlockStateModels} 空实现。
 * <p>⚠️ 生成的资源在 {@code src/generated/resources} —— 该目录必须由
 * {@code build.gradle} 的 {@code sourceSets.main.resources.srcDir(...)} 纳入资源集,
 * 否则模型不会进产物(见 KNOWN-ISSUES KI-F4)。
 */
public class ModItemModelProvider extends FabricModelProvider {
    public ModItemModelProvider(FabricDataOutput output) {
        super(output);
    }

    @Override
    public void generateBlockStateModels(BlockModelGenerators generators) {
        // 本模组无方块模型(全部是物品)。
    }

    @Override
    public void generateItemModels(ItemModelGenerators generators) {
        generators.generateFlatItem(ModItems.DICE.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.GOLDEN_DICE.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.GLASS_DICE.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.DIAMOND_DICE.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.NETHERITE_DICE.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.EMERALD_DICE.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.OBSIDIAN_DICE.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.NETHERRACK_DICE.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.WEIRD_DICE.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.CRIMSON_DICE.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.AMETHYST_DICE.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.ENDER_DICE.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.NETHER_STAR_DICE.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.ATTACK_CARD_MEDIUM.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.ATTACK_CARD_LARGE.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.ATTACK_CARD_EPIC.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.ATTACK_CARD_SHADOW_STRIKE.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.ATTACK_CARD_MEITO.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.ATTACK_CARD_CHARGE.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.ATTACK_CARD_FULL_POWER.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.ATTACK_CARD_BITE.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.ATTACK_CARD_DRAGON_ROAR.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.DEFENSE_CARD_MEDIUM.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.DEFENSE_CARD_LARGE.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.DEFENSE_CARD_EPIC.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.EFFECT_CARD_KING_POWER.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.EFFECT_CARD_BERSERK.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.EFFECT_CARD_UNWAVERING.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.EFFECT_CARD_FIGHT_POISON_WITH_POISON.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.BLANK_SIGN.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.PARUNAN_SIGN.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.JASMINE_SIGN.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.MISAKI_SIGN.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.MIMI_SIGN.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.LULU_SIGN.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.KOMACHI_SIGN.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.FLASHLIGHT_CHIP.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.CUTTER_CHIP.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.CUTTER_BLADE_CHIP.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.STAR_COIN.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.STAR_COIN_BAG.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.MONSTER_LASER_CARD.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.MONSTER_BRICK_CARD.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.ORBITAL_STRIKE_CARD.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.DIRECTIONAL_BLAST_CARD.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.BLANK_CHIP.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.SCOPE_CHIP.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.EAGLE_SCOPE_CHIP.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.MEDKIT_EMERGENCY_CHIP.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.MEDKIT_COMPLETE_CHIP.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.VITAMIN_PILL_CHIP.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.TARGET_CHIP.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.MARKER_SPRAYER_CHIP.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.MAGIC_TOME_CHIP.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.BIG_BACKPACK_CHIP.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.NINJA_STAR_CHIP.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.HAND_FAN_SMALL_CHIP.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.HAND_FAN_BIG_CHIP.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.STAR_PLATE.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.GOLDEN_STAR_PLATE.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.REGENERATION_REAGENT.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.CONDUCTIVE_WIRE.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.STAR_COIN_DUST.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.MARK_PAINT.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.EIGHT_SIDED_DICE.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.PADMAN_SIGN.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.FANNY_SIGN.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.RIN_SIGN.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.LIVING_PAGE.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.HAIQING_SIGN.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.FATE_GUIDANCE_CARD.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.PAPARA_SIGN.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.BONNIE_SIGN.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.FEN_SIGN.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.NANCY_LU_SIGN.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.MOSES_SIGN.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.PANDAMAN_SIGN.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.REN_SIGN.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.ZHAO_SIGN.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.TERU_SIGN.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.NARDIS_SIGN.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.MAMUSHI_SIGN.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.SHERRY_SIGN.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.HANNA_SIGN.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.MEGAS_SIGN.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.FU_CARD.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.HUO_CARD.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.CHOCOLATE_CAKE.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.HAMBURGER.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.LUXURY_FEAST.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.YOU_HAVE_I_HAVE.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.EXPRESS_DELIVERY.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.ATM.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.BANK_CARD_LOW.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.BANK_CARD_HIGH.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.BANK_CARD_UNLIMITED.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.BOXING_GLOVES_LOW.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.BOXING_GLOVES_MEDIUM.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.BOXING_GLOVES_HIGH.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.SPEED_SKATES_LOW.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.SPEED_SKATES_MEDIUM.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.SPEED_SKATES_HIGH.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.MOTO_HELMET_LOW.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.MOTO_HELMET_MEDIUM.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.MOTO_HELMET_HIGH.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.SANDWICH_LOW.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.SANDWICH_MEDIUM.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.SANDWICH_HIGH.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.ADRENALINE_LOW.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.ADRENALINE_HIGH.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.MAGIC_QUIVER.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.BUFFER_SHIELD.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.STAR_COIN_HAMMER.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.CURSED_SWORD.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.REVENGE_HALBERD.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.PIERCING_GUN.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.CANDY_CHIP.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.FRIENDSHIP_BADGE.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.SATELLITE_CHIP.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.WARP_ENGINE_CHIP.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.ENERGY_RECYCLER.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.ELECTRIC_SWORD.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.PERPETUAL_MOTION.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.CURRENT_CORE_CHIP.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.ADVANCED_PERIPHERALS.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.BIG_BOWL_STEW_CHIP.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.MEMBER_RECOMMENDATION_CHIP.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.BOOKMARK_CHIP.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.PIGGY_BANK_CHIP.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.SMART_WATCH_CHIP.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.ELECTRIC_GLOVE_CHIP.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.AIRBAG_CHIP.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.RAILGUN_CHIP.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.PRIMORDIAL_CORE_CHIP.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.WHETSTONE_CHIP.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.PURPLE_SHOOTING_STAR_CHIP.get(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ModItems.GOLDEN_SHOOTING_STAR_CHIP.get(), ModelTemplates.FLAT_ITEM);
    }
}
