package com.merlinkitsune.astral_dice.init;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.item.ModItems;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister
            .create(Registries.CREATIVE_MODE_TAB, AstralDiceMod.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> DICE_TAB = CREATIVE_TABS
            .register("dice_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + AstralDiceMod.MODID))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> ModItems.DICE.get().getDefaultInstance())
            .displayItems((params, output) -> {
                // 材料（最前端）
                output.accept(ModItems.STAR_COIN.get());
                output.accept(ModItems.STAR_COIN_BAG.get());
                output.accept(ModItems.STAR_PLATE.get());
                output.accept(ModItems.GOLDEN_STAR_PLATE.get());
                output.accept(ModItems.BLANK_SIGN.get());
                output.accept(ModItems.BLANK_CHIP.get());
                // 骰子
                output.accept(ModItems.DICE.get());
                output.accept(ModItems.GOLDEN_DICE.get());
                output.accept(ModItems.DIAMOND_DICE.get());
                output.accept(ModItems.NETHERITE_DICE.get());
                // 攻击牌
                output.accept(ModItems.ATTACK_CARD_MEDIUM.get());
                output.accept(ModItems.ATTACK_CARD_LARGE.get());
                output.accept(ModItems.ATTACK_CARD_EPIC.get());
                output.accept(ModItems.ATTACK_CARD_SHADOW_STRIKE.get());
                output.accept(ModItems.ATTACK_CARD_MEITO.get());
                output.accept(ModItems.ATTACK_CARD_CHARGE.get());
                output.accept(ModItems.ATTACK_CARD_FULL_POWER.get());
                // 防御牌
                output.accept(ModItems.DEFENSE_CARD_MEDIUM.get());
                output.accept(ModItems.DEFENSE_CARD_LARGE.get());
                output.accept(ModItems.DEFENSE_CARD_EPIC.get());
                // 功能效果牌（狂暴在王之力前）
                output.accept(ModItems.EFFECT_CARD_BERSERK.get());
                output.accept(ModItems.EFFECT_CARD_KING_POWER.get());
                output.accept(ModItems.EFFECT_CARD_UNWAVERING.get());
                output.accept(ModItems.EFFECT_CARD_FIGHT_POISON_WITH_POISON.get());
                // 新效果牌（在岿然不动之后）
                output.accept(ModItems.CHOCOLATE_CAKE.get());
                output.accept(ModItems.HAMBURGER.get());
                output.accept(ModItems.LUXURY_FEAST.get());
                output.accept(ModItems.YOU_HAVE_I_HAVE.get());
                output.accept(ModItems.EXPRESS_DELIVERY.get());
                output.accept(ModItems.FATE_GUIDANCE_CARD.get());
                // 伤害效果牌（活体书页在定向爆破之后）
                output.accept(ModItems.MONSTER_LASER_CARD.get());
                output.accept(ModItems.MONSTER_BRICK_CARD.get());
                output.accept(ModItems.ORBITAL_STRIKE_CARD.get());
                output.accept(ModItems.DIRECTIONAL_BLAST_CARD.get());
                output.accept(ModItems.LIVING_BOOK_PAGE.get());
                // 立牌
                output.accept(ModItems.PARUNAN_SIGN.get());
                output.accept(ModItems.JASMINE_SIGN.get());
                output.accept(ModItems.MISAKI_SIGN.get());
                output.accept(ModItems.MIMI_SIGN.get());
                output.accept(ModItems.LULU_SIGN.get());
                output.accept(ModItems.KOMACHI_SIGN.get());
                output.accept(ModItems.PADMAN_SIGN.get());
                output.accept(ModItems.FANNY_SIGN.get());
                output.accept(ModItems.RIN_SIGN.get());
                output.accept(ModItems.HAIQING_SIGN.get());
                output.accept(ModItems.PAPARA_SIGN.get());
                output.accept(ModItems.BONNIE_SIGN.get());
                output.accept(ModItems.FEN_SIGN.get());
                // 筹码
                output.accept(ModItems.FLASHLIGHT_CHIP.get());
                output.accept(ModItems.CUTTER_CHIP.get());
                output.accept(ModItems.CUTTER_BLADE_CHIP.get());
                output.accept(ModItems.SCOPE_CHIP.get());
                output.accept(ModItems.EAGLE_SCOPE_CHIP.get());
                output.accept(ModItems.MEDKIT_EMERGENCY_CHIP.get());
                output.accept(ModItems.MEDKIT_COMPLETE_CHIP.get());
                output.accept(ModItems.VITAMIN_PILL_CHIP.get());
                output.accept(ModItems.TARGET_CHIP.get());
                output.accept(ModItems.MARKER_SPRAYER_CHIP.get());
                output.accept(ModItems.EIGHT_SIDED_DICE.get());
                output.accept(ModItems.MAGIC_TOME_CHIP.get());
                output.accept(ModItems.BIG_BACKPACK_CHIP.get());
                output.accept(ModItems.NINJA_STAR_CHIP.get());
                output.accept(ModItems.HAND_FAN_SMALL_CHIP.get());
                output.accept(ModItems.HAND_FAN_BIG_CHIP.get());
                output.accept(ModItems.ATM.get());
                output.accept(ModItems.BANK_CARD_LOW.get());
                output.accept(ModItems.BANK_CARD_HIGH.get());
                output.accept(ModItems.BANK_CARD_UNLIMITED.get());
                output.accept(ModItems.BOXING_GLOVES_LOW.get());
                output.accept(ModItems.BOXING_GLOVES_MEDIUM.get());
                output.accept(ModItems.BOXING_GLOVES_HIGH.get());
                output.accept(ModItems.SPEED_SKATES_LOW.get());
                output.accept(ModItems.SPEED_SKATES_MEDIUM.get());
                output.accept(ModItems.SPEED_SKATES_HIGH.get());
                output.accept(ModItems.MOTO_HELMET_LOW.get());
                output.accept(ModItems.MOTO_HELMET_MEDIUM.get());
                output.accept(ModItems.MOTO_HELMET_HIGH.get());
                output.accept(ModItems.SANDWICH_LOW.get());
                output.accept(ModItems.SANDWICH_MEDIUM.get());
                output.accept(ModItems.SANDWICH_HIGH.get());
                output.accept(ModItems.MAGIC_QUIVER.get());
                output.accept(ModItems.BUFFER_SHIELD.get());
                output.accept(ModItems.STAR_COIN_HAMMER.get());
                output.accept(ModItems.CURSED_SWORD.get());
                output.accept(ModItems.REVENGE_HALBERD.get());
                output.accept(ModItems.PIERCING_GUN.get());
                output.accept(ModItems.CANDY_CHIP.get());
                output.accept(ModItems.FRIENDSHIP_BADGE.get());
                output.accept(ModItems.SATELLITE_CHIP.get());
            }).build());
}
