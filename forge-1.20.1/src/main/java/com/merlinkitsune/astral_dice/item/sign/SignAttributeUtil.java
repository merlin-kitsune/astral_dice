package com.merlinkitsune.astral_dice.item.sign;

import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 立牌属性修饰符工具（1.20.1）：1.20.1 的 {@link AttributeModifier} 用 UUID 作 id，
 * 这里按 key 派生**稳定 UUID**，语义与 1.21.1 侧（ResourceLocation id）等价：
 * 同一 key 重复调用只改数值，不会叠加出多个修饰符。
 */
public final class SignAttributeUtil {
    private SignAttributeUtil() {
    }

    private static UUID idOf(String key) {
        return UUID.nameUUIDFromBytes(("astral_dice:" + key).getBytes(StandardCharsets.UTF_8));
    }

    public static void apply(Player player, Attribute attribute, String key,
                             double amount, AttributeModifier.Operation operation) {
        if (player == null || player.level().isClientSide()) return;
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;
        UUID id = idOf(key);
        AttributeModifier existing = instance.getModifier(id);
        if (amount == 0.0D) {
            if (existing != null) instance.removeModifier(id);
            return;
        }
        if (existing == null || existing.getAmount() != amount || existing.getOperation() != operation) {
            if (existing != null) instance.removeModifier(id);
            instance.addTransientModifier(new AttributeModifier(id, key, amount, operation));
        }
    }

    public static void remove(Player player, Attribute attribute, String key) {
        if (player == null) return;
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;
        UUID id = idOf(key);
        if (instance.getModifier(id) != null) instance.removeModifier(id);
    }
}
