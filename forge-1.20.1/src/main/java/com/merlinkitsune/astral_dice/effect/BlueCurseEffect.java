package com.merlinkitsune.astral_dice.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * 青之诅咒:负面效果。
 * 效果期间护甲值 -20%(最终护甲值向下取整),盔甲韧性归 0。
 * 暂未配置任何触发条件。
 */
public class BlueCurseEffect extends MobEffect {
    public BlueCurseEffect() {
        super(MobEffectCategory.HARMFUL, 0x1E90FF);
        this.addAttributeModifier(Attributes.ARMOR,
                JasmineSweepEffect.modifierId("blue_curse_armor").toString(),
                -0.2, AttributeModifier.Operation.MULTIPLY_TOTAL);
        this.addAttributeModifier(Attributes.ARMOR_TOUGHNESS,
                JasmineSweepEffect.modifierId("blue_curse_toughness").toString(),
                -1.0, AttributeModifier.Operation.MULTIPLY_TOTAL);
    }
}
