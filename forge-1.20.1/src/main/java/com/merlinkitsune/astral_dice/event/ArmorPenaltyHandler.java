package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 护甲 -30%(取整) 惩罚处理:作为扫地机立牌(jasmine)事件"reduce_armor"的效果,持续 60 秒。
 * 1.20.1 的 AttributeModifier 以 UUID 标识(由修饰器名稳定派生,对应 1.21 的 ResourceLocation id)。
 */
public final class ArmorPenaltyHandler {
    public static final UUID ARMOR_PENALTY_MODIFIER = UUID.nameUUIDFromBytes(
            (AstralDiceMod.MODID + ":jasmine_armor_penalty").getBytes(StandardCharsets.UTF_8));

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
        attr.addTransientModifier(
                new AttributeModifier(ARMOR_PENALTY_MODIFIER, "jasmine_armor_penalty", -penalty,
                        AttributeModifier.Operation.ADDITION));
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
