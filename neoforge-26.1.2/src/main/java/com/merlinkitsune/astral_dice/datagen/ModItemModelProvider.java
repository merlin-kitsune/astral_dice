package com.merlinkitsune.astral_dice.datagen;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.item.ModItems;
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
 * {@code registerModels(BlockModelGenerators, ItemModelGenerators)};原先的 {@code basicItem(item)}
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
        basicItem(itemModels, ModItems.DICE.get());
        basicItem(itemModels, ModItems.GOLDEN_DICE.get());
        basicItem(itemModels, ModItems.GLASS_DICE.get());
        basicItem(itemModels, ModItems.NETHERRACK_DICE.get());
        basicItem(itemModels, ModItems.DIAMOND_DICE.get());
        basicItem(itemModels, ModItems.NETHERITE_DICE.get());
        basicItem(itemModels, ModItems.EMERALD_DICE.get());
        basicItem(itemModels, ModItems.OBSIDIAN_DICE.get());
        basicItem(itemModels, ModItems.WEIRD_DICE.get());
        basicItem(itemModels, ModItems.CRIMSON_DICE.get());
        basicItem(itemModels, ModItems.AMETHYST_DICE.get());
        basicItem(itemModels, ModItems.ENDER_DICE.get());
        basicItem(itemModels, ModItems.NETHER_STAR_DICE.get());
        basicItem(itemModels, ModItems.ATTACK_CARD_MEDIUM.get());
        basicItem(itemModels, ModItems.ATTACK_CARD_LARGE.get());
        basicItem(itemModels, ModItems.ATTACK_CARD_EPIC.get());
        basicItem(itemModels, ModItems.ATTACK_CARD_SHADOW_STRIKE.get());
        basicItem(itemModels, ModItems.ATTACK_CARD_MEITO.get());
        basicItem(itemModels, ModItems.ATTACK_CARD_CHARGE.get());
        basicItem(itemModels, ModItems.ATTACK_CARD_FULL_POWER.get());
        basicItem(itemModels, ModItems.DEFENSE_CARD_MEDIUM.get());
        basicItem(itemModels, ModItems.DEFENSE_CARD_LARGE.get());
        basicItem(itemModels, ModItems.DEFENSE_CARD_EPIC.get());
        basicItem(itemModels, ModItems.EFFECT_CARD_KING_POWER.get());
        basicItem(itemModels, ModItems.EFFECT_CARD_BERSERK.get());
        basicItem(itemModels, ModItems.EFFECT_CARD_UNWAVERING.get());
        basicItem(itemModels, ModItems.EFFECT_CARD_FIGHT_POISON_WITH_POISON.get());
        basicItem(itemModels, ModItems.STAR_COIN.get());
        basicItem(itemModels, ModItems.BLANK_SIGN.get());
        basicItem(itemModels, ModItems.PARUNAN_SIGN.get());
        basicItem(itemModels, ModItems.JASMINE_SIGN.get());
        basicItem(itemModels, ModItems.MISAKI_SIGN.get());
        basicItem(itemModels, ModItems.MIMI_SIGN.get());
        basicItem(itemModels, ModItems.LULU_SIGN.get());
        basicItem(itemModels, ModItems.KOMACHI_SIGN.get());
        basicItem(itemModels, ModItems.FLASHLIGHT_CHIP.get());
        basicItem(itemModels, ModItems.CUTTER_CHIP.get());
        basicItem(itemModels, ModItems.CUTTER_BLADE_CHIP.get());
        // 注意:1.21.1 的 provider 里 STAR_COIN 被登记了两次(见 1.21.1 同名文件),
        // 那里的 NeoForge ItemModelProvider 会静默覆盖;26.1.2 改用原版 ModelProvider,
        // 重复登记会直接抛 IllegalStateException: Duplicate model definition,
        // 故此处只保留第 60 行那一处。
        basicItem(itemModels, ModItems.STAR_COIN_BAG.get());
        basicItem(itemModels, ModItems.MONSTER_LASER_CARD.get());
        basicItem(itemModels, ModItems.MONSTER_BRICK_CARD.get());
        basicItem(itemModels, ModItems.ORBITAL_STRIKE_CARD.get());
        basicItem(itemModels, ModItems.DIRECTIONAL_BLAST_CARD.get());
        basicItem(itemModels, ModItems.BLANK_CHIP.get());
        basicItem(itemModels, ModItems.SCOPE_CHIP.get());
        basicItem(itemModels, ModItems.EAGLE_SCOPE_CHIP.get());
        basicItem(itemModels, ModItems.MEDKIT_EMERGENCY_CHIP.get());
        basicItem(itemModels, ModItems.MEDKIT_COMPLETE_CHIP.get());
        basicItem(itemModels, ModItems.VITAMIN_PILL_CHIP.get());
        basicItem(itemModels, ModItems.TARGET_CHIP.get());
        basicItem(itemModels, ModItems.MARKER_SPRAYER_CHIP.get());
        basicItem(itemModels, ModItems.MAGIC_TOME_CHIP.get());
        basicItem(itemModels, ModItems.BIG_BACKPACK_CHIP.get());
        basicItem(itemModels, ModItems.NINJA_STAR_CHIP.get());
        basicItem(itemModels, ModItems.HAND_FAN_SMALL_CHIP.get());
        basicItem(itemModels, ModItems.HAND_FAN_BIG_CHIP.get());
        basicItem(itemModels, ModItems.STAR_PLATE.get());
        basicItem(itemModels, ModItems.GOLDEN_STAR_PLATE.get());
        basicItem(itemModels, ModItems.REGENERATION_REAGENT.get());
        basicItem(itemModels, ModItems.CONDUCTIVE_WIRE.get());
        basicItem(itemModels, ModItems.STAR_COIN_DUST.get());
        basicItem(itemModels, ModItems.MARK_PAINT.get());
        basicItem(itemModels, ModItems.EIGHT_SIDED_DICE.get());
        basicItem(itemModels, ModItems.PADMAN_SIGN.get());
        basicItem(itemModels, ModItems.FANNY_SIGN.get());
        basicItem(itemModels, ModItems.RIN_SIGN.get());
        basicItem(itemModels, ModItems.LIVING_PAGE.get());
        basicItem(itemModels, ModItems.HAIQING_SIGN.get());
        basicItem(itemModels, ModItems.FATE_GUIDANCE_CARD.get());
        basicItem(itemModels, ModItems.PAPARA_SIGN.get());
        basicItem(itemModels, ModItems.BONNIE_SIGN.get());
        basicItem(itemModels, ModItems.FEN_SIGN.get());
        basicItem(itemModels, ModItems.NANCY_LU_SIGN.get());
        basicItem(itemModels, ModItems.MOSES_SIGN.get());
        basicItem(itemModels, ModItems.PANDAMAN_SIGN.get());
        basicItem(itemModels, ModItems.REN_SIGN.get());
        basicItem(itemModels, ModItems.CHOCOLATE_CAKE.get());
        basicItem(itemModels, ModItems.HAMBURGER.get());
        basicItem(itemModels, ModItems.LUXURY_FEAST.get());
        basicItem(itemModels, ModItems.YOU_HAVE_I_HAVE.get());
        basicItem(itemModels, ModItems.EXPRESS_DELIVERY.get());
        basicItem(itemModels, ModItems.ATM.get());
        basicItem(itemModels, ModItems.BANK_CARD_LOW.get());
        basicItem(itemModels, ModItems.BANK_CARD_HIGH.get());
        basicItem(itemModels, ModItems.BANK_CARD_UNLIMITED.get());
        basicItem(itemModels, ModItems.BOXING_GLOVES_LOW.get());
        basicItem(itemModels, ModItems.BOXING_GLOVES_MEDIUM.get());
        basicItem(itemModels, ModItems.BOXING_GLOVES_HIGH.get());
        basicItem(itemModels, ModItems.SPEED_SKATES_LOW.get());
        basicItem(itemModels, ModItems.SPEED_SKATES_MEDIUM.get());
        basicItem(itemModels, ModItems.SPEED_SKATES_HIGH.get());
        basicItem(itemModels, ModItems.MOTO_HELMET_LOW.get());
        basicItem(itemModels, ModItems.MOTO_HELMET_MEDIUM.get());
        basicItem(itemModels, ModItems.MOTO_HELMET_HIGH.get());
        basicItem(itemModels, ModItems.SANDWICH_LOW.get());
        basicItem(itemModels, ModItems.SANDWICH_MEDIUM.get());
        basicItem(itemModels, ModItems.SANDWICH_HIGH.get());
        basicItem(itemModels, ModItems.ADRENALINE_LOW.get());
        basicItem(itemModels, ModItems.ADRENALINE_HIGH.get());
        basicItem(itemModels, ModItems.MAGIC_QUIVER.get());
        basicItem(itemModels, ModItems.BUFFER_SHIELD.get());
        basicItem(itemModels, ModItems.STAR_COIN_HAMMER.get());
        basicItem(itemModels, ModItems.CURSED_SWORD.get());
        basicItem(itemModels, ModItems.REVENGE_HALBERD.get());
        basicItem(itemModels, ModItems.PIERCING_GUN.get());
        basicItem(itemModels, ModItems.CANDY_CHIP.get());
        basicItem(itemModels, ModItems.FRIENDSHIP_BADGE.get());
        basicItem(itemModels, ModItems.SATELLITE_CHIP.get());
        basicItem(itemModels, ModItems.WARP_ENGINE_CHIP.get());
        basicItem(itemModels, ModItems.ENERGY_RECYCLER.get());
        basicItem(itemModels, ModItems.ELECTRIC_SWORD.get());
        basicItem(itemModels, ModItems.PERPETUAL_MOTION.get());
        basicItem(itemModels, ModItems.CURRENT_CORE_CHIP.get());
        basicItem(itemModels, ModItems.ADVANCED_PERIPHERALS.get());
        basicItem(itemModels, ModItems.BIG_BOWL_STEW_CHIP.get());
        basicItem(itemModels, ModItems.MEMBER_RECOMMENDATION_CHIP.get());
        basicItem(itemModels, ModItems.BOOKMARK_CHIP.get());
        basicItem(itemModels, ModItems.PIGGY_BANK_CHIP.get());
        basicItem(itemModels, ModItems.SMART_WATCH_CHIP.get());
        basicItem(itemModels, ModItems.ELECTRIC_GLOVE_CHIP.get());
        basicItem(itemModels, ModItems.AIRBAG_CHIP.get());
        basicItem(itemModels, ModItems.RAILGUN_CHIP.get());
        basicItem(itemModels, ModItems.PRIMORDIAL_CORE_CHIP.get());
        basicItem(itemModels, ModItems.WHETSTONE_CHIP.get());
    }

    /** 与 1.21.1 的 {@code ItemModelProvider#basicItem(Item)} 等价的最小平铺物品模型。 */
    private static void basicItem(ItemModelGenerators itemModels, Item item) {
        itemModels.generateFlatItem(item, ModelTemplates.FLAT_ITEM);
    }
}