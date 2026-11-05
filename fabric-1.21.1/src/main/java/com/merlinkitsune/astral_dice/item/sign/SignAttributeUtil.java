package com.merlinkitsune.astral_dice.item.sign;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;

import java.util.Locale;

/**
 * 立牌属性修饰符工具(1.21.1):1.21.1 的 {@link AttributeModifier} 以 {@link ResourceLocation} 作 id
 * (1.20.1 是 UUID),这里按 key 派生**稳定 ResourceLocation**,语义与 1.20.1 侧(UUID id)等价:
 * 同一 key 重复调用只改数值,不会叠加出多个修饰符。
 *
 * <p>属性形参一律用 {@link Holder}{@code <Attribute>}:1.21.1 起 {@code Attributes.XXX} 常量本身
 * 就是 {@code Holder<Attribute>}(1.20.1 是裸 {@code Attribute}),{@code LivingEntity#getAttribute}
 * 也按 Holder 取实例 ⇒ 调用方直接传 {@code Attributes.ATTACK_DAMAGE} 即可,无需包装。
 *
 * <p>Fabric 侧无平台差异(纯原版 API)⇒ 逐字照搬,{@code isClientSide} 在 1.21.1 的 fabric
 * 线同样是**方法** {@code level().isClientSide()},与本文件写法一致。
 */
public final class SignAttributeUtil {
    private SignAttributeUtil() {
    }

    /** key ⇒ 稳定 id:命名空间固定 {@code astral_dice},path 经字符集清洗(合法 key 原样即是)。 */
    private static ResourceLocation idOf(String key) {
        return ResourceLocation.fromNamespaceAndPath("astral_dice", sanitizePath(key));
    }

    /**
     * 小写化后只保留 {@code [a-z0-9/._-]}(即 {@link ResourceLocation#validPathChar(char)} 认可的字符),
     * 其余一律替换成 {@code _}:1.21.1 的 id 走 ResourceLocation 校验,非法字符会直接抛异常,
     * 而 1.20.1 的 UUID 派生对任意字符串都成立,故这里必须显式清洗。
     */
    private static String sanitizePath(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            sb.append(ResourceLocation.validPathChar(c) ? c : '_');
        }
        return sb.length() == 0 ? "_" : sb.toString();
    }

    public static void apply(Player player, Holder<Attribute> attribute, String key,
                             double amount, AttributeModifier.Operation operation) {
        if (player == null || player.level().isClientSide()) return;
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;
        ResourceLocation id = idOf(key);
        AttributeModifier existing = instance.getModifier(id);
        if (amount == 0.0D) {
            if (existing != null) instance.removeModifier(id);
            return;
        }
        if (existing == null || existing.amount() != amount || existing.operation() != operation) {
            if (existing != null) instance.removeModifier(id);
            instance.addTransientModifier(new AttributeModifier(id, amount, operation));
        }
    }

    public static void remove(Player player, Holder<Attribute> attribute, String key) {
        if (player == null) return;
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;
        ResourceLocation id = idOf(key);
        if (instance.getModifier(id) != null) instance.removeModifier(id);
    }
}
