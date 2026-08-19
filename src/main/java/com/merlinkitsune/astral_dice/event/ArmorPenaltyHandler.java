package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * 护甲 -30%(取整) 惩罚处理:作为扫地机立牌(jasmine)事件"reduce_armor"的效果,持续 60 秒。
 */
public final class ArmorPenaltyHandler {
    public static final ResourceLocation ARMOR_PENALTY_MODIFIER =
            ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "jasmine_armor_penalty");

    private ArmorPenaltyHandler() {
    }

    // 基于当前(不含惩罚的)护甲施加 -30%(取整)
    public static void apply(Player player) {
        AttributeInstance attr = player.getAttribute(Attributes.ARMOR);
        if (attr == null) return;
        attr.removeModifier(ARMOR_PENALTY_MODIFIER);
        int currentArmor = player.getArmorValue();
        int penalty = (int) Math.round(currentArmor * 0.3);
        if (penalty <= 0) return;
        attr.addOrUpdateTransientModifier(
                new AttributeModifier(ARMOR_PENALTY_MODIFIER, -penalty, AttributeModifier.Operation.ADD_VALUE));
    }

    public static void remove(Player player) {
        AttributeInstance attr = player.getAttribute(Attributes.ARMOR);
        if (attr != null) {
            attr.removeModifier(ARMOR_PENALTY_MODIFIER);
        }
        ModAttachments.setArmorPenaltyEnd(player, 0L);
    }

    // 到期自动移除
    public static void tick(Player player) {
        long end = ModAttachments.getArmorPenaltyEnd(player);
        if (end > 0 && player.level().getGameTime() >= end) {
            remove(player);
        }
    }
}
