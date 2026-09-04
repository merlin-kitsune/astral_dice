package com.merlinkitsune.astral_dice.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * 青之诅咒:负面效果。
 * 效果期间护甲值 -20%(最终护甲值向下取整),盔甲韧性 -100%(归 0)。
 * 暂未配置任何触发条件。
 *
 * 注意:1.20.1 的 addAttributeModifier(Attribute, String, ...) 要求 UUID 字符串
 * (内部 UUID.fromString),不能像 1.21.1 那样传 ResourceLocation 路径;此处使用
 * 与语义名绑定的确定性 UUID(astral_dice:blue_curse_armor/toughness 的 UUIDv5)。
 */
public class BlueCurseEffect extends MobEffect {
    public BlueCurseEffect() {
        super(MobEffectCategory.HARMFUL, 0x1E90FF);
        this.addAttributeModifier(Attributes.ARMOR, "35d97c55-f446-5313-b56e-a200101bd0dd",
                -0.2, AttributeModifier.Operation.MULTIPLY_TOTAL);
        this.addAttributeModifier(Attributes.ARMOR_TOUGHNESS, "fa11733c-38c7-5f3d-aabb-967339705ba0",
                -1.0, AttributeModifier.Operation.MULTIPLY_TOTAL);
    }
}
