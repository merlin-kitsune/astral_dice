package com.merlinkitsune.astral_dice.datagen;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.data.PackOutput;
import net.minecraftforge.client.model.generators.ItemModelProvider;
import net.minecraftforge.common.data.ExistingFileHelper;

public class ModItemModelProvider extends ItemModelProvider {
    public ModItemModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, AstralDiceMod.MODID, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        basicItem(ModItems.DICE.get());
        basicItem(ModItems.GOLDEN_DICE.get());
        basicItem(ModItems.DIAMOND_DICE.get());
        basicItem(ModItems.NETHERITE_DICE.get());
        basicItem(ModItems.ATTACK_CARD_MEDIUM.get());
        basicItem(ModItems.ATTACK_CARD_LARGE.get());
        basicItem(ModItems.ATTACK_CARD_EPIC.get());
        basicItem(ModItems.ATTACK_CARD_SHADOW_STRIKE.get());
        basicItem(ModItems.ATTACK_CARD_MEITO.get());
        basicItem(ModItems.ATTACK_CARD_CHARGE.get());
        basicItem(ModItems.ATTACK_CARD_FULL_POWER.get());
        basicItem(ModItems.DEFENSE_CARD_MEDIUM.get());
        basicItem(ModItems.DEFENSE_CARD_LARGE.get());
        basicItem(ModItems.DEFENSE_CARD_EPIC.get());
        basicItem(ModItems.EFFECT_CARD_KING_POWER.get());
        basicItem(ModItems.EFFECT_CARD_BERSERK.get());
        basicItem(ModItems.EFFECT_CARD_UNWAVERING.get());
        basicItem(ModItems.EFFECT_CARD_FIGHT_POISON_WITH_POISON.get());
        basicItem(ModItems.BLANK_SIGN.get());
        basicItem(ModItems.PARUNAN_SIGN.get());
        basicItem(ModItems.JASMINE_SIGN.get());
        basicItem(ModItems.MISAKI_SIGN.get());
        basicItem(ModItems.MIMI_SIGN.get());
        basicItem(ModItems.LULU_SIGN.get());
        basicItem(ModItems.KOMACHI_SIGN.get());
        basicItem(ModItems.FLASHLIGHT_CHIP.get());
        basicItem(ModItems.CUTTER_CHIP.get());
        basicItem(ModItems.CUTTER_BLADE_CHIP.get());
        basicItem(ModItems.STAR_COIN.get());
        basicItem(ModItems.STAR_COIN_BAG.get());
        basicItem(ModItems.MONSTER_LASER_CARD.get());
        basicItem(ModItems.MONSTER_BRICK_CARD.get());
        basicItem(ModItems.ORBITAL_STRIKE_CARD.get());
        basicItem(ModItems.DIRECTIONAL_BLAST_CARD.get());
        basicItem(ModItems.BLANK_CHIP.get());
        basicItem(ModItems.SCOPE_CHIP.get());
        basicItem(ModItems.EAGLE_SCOPE_CHIP.get());
        basicItem(ModItems.MEDKIT_EMERGENCY_CHIP.get());
        basicItem(ModItems.MEDKIT_COMPLETE_CHIP.get());
        basicItem(ModItems.VITAMIN_PILL_CHIP.get());
        basicItem(ModItems.TARGET_CHIP.get());
        basicItem(ModItems.MARKER_SPRAYER_CHIP.get());
        basicItem(ModItems.MAGIC_TOME_CHIP.get());
        basicItem(ModItems.BIG_BACKPACK_CHIP.get());
        basicItem(ModItems.NINJA_STAR_CHIP.get());
        basicItem(ModItems.HAND_FAN_SMALL_CHIP.get());
        basicItem(ModItems.HAND_FAN_BIG_CHIP.get());
        basicItem(ModItems.STAR_PLATE.get());
        basicItem(ModItems.GOLDEN_STAR_PLATE.get());
        basicItem(ModItems.EIGHT_SIDED_DICE.get());
        basicItem(ModItems.PADMAN_SIGN.get());
        basicItem(ModItems.FANNY_SIGN.get());
        basicItem(ModItems.RIN_SIGN.get());
        basicItem(ModItems.LIVING_BOOK_PAGE.get());
        basicItem(ModItems.HAIQING_SIGN.get());
        basicItem(ModItems.FATE_GUIDANCE_CARD.get());
        basicItem(ModItems.PAPARA_SIGN.get());
        basicItem(ModItems.BONNIE_SIGN.get());
        basicItem(ModItems.FEN_SIGN.get());
        basicItem(ModItems.NANCY_LU_SIGN.get());
        basicItem(ModItems.CHOCOLATE_CAKE.get());
        basicItem(ModItems.HAMBURGER.get());
        basicItem(ModItems.LUXURY_FEAST.get());
        basicItem(ModItems.YOU_HAVE_I_HAVE.get());
        basicItem(ModItems.EXPRESS_DELIVERY.get());
        basicItem(ModItems.ATM.get());
        basicItem(ModItems.BANK_CARD_LOW.get());
        basicItem(ModItems.BANK_CARD_HIGH.get());
        basicItem(ModItems.BANK_CARD_UNLIMITED.get());
        basicItem(ModItems.BOXING_GLOVES_LOW.get());
        basicItem(ModItems.BOXING_GLOVES_MEDIUM.get());
        basicItem(ModItems.BOXING_GLOVES_HIGH.get());
        basicItem(ModItems.SPEED_SKATES_LOW.get());
        basicItem(ModItems.SPEED_SKATES_MEDIUM.get());
        basicItem(ModItems.SPEED_SKATES_HIGH.get());
        basicItem(ModItems.MOTO_HELMET_LOW.get());
        basicItem(ModItems.MOTO_HELMET_MEDIUM.get());
        basicItem(ModItems.MOTO_HELMET_HIGH.get());
        basicItem(ModItems.SANDWICH_LOW.get());
        basicItem(ModItems.SANDWICH_MEDIUM.get());
        basicItem(ModItems.SANDWICH_HIGH.get());
        basicItem(ModItems.MAGIC_QUIVER.get());
        basicItem(ModItems.BUFFER_SHIELD.get());
        basicItem(ModItems.STAR_COIN_HAMMER.get());
        basicItem(ModItems.CURSED_SWORD.get());
        basicItem(ModItems.REVENGE_HALBERD.get());
        basicItem(ModItems.PIERCING_GUN.get());
        basicItem(ModItems.CANDY_CHIP.get());
        basicItem(ModItems.FRIENDSHIP_BADGE.get());
        basicItem(ModItems.SATELLITE_CHIP.get());
    }
}
