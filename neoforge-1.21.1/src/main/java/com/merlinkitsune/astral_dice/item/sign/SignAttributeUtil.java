package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;

/**
 * 立牌属性修饰符工具：固定 id ⇒ 重复调用只改数值，不会叠加出多个修饰符。
 *
 * <p>语义与 {@code LinglingSignManager} 的同名私有实现一致（那边是 private，故此处另立公共工具，
 * 新立牌统一走这里，避免四份复制漂移）。
 */
public final class SignAttributeUtil {
    private SignAttributeUtil() {
    }

    public static void apply(Player player, Holder<Attribute> attribute, String key,
                             double amount, AttributeModifier.Operation operation) {
        if (player == null || player.level().isClientSide()) return;
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;
        ResourceLocation id = modifierId(key);
        AttributeModifier existing = instance.getModifier(id);
        if (amount == 0.0D) {
            if (existing != null) instance.removeModifier(id);
            return;
        }
        if (existing == null || existing.amount() != amount || existing.operation() != operation) {
            instance.removeModifier(id);
            instance.addTransientModifier(new AttributeModifier(id, amount, operation));
        }
    }

    public static void remove(Player player, Holder<Attribute> attribute, String key) {
        if (player == null) return;
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;
        ResourceLocation id = modifierId(key);
        if (instance.getModifier(id) != null) instance.removeModifier(id);
    }

    private static ResourceLocation modifierId(String key) {
        return ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, key);
    }
}
