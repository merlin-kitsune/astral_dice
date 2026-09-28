package com.merlinkitsune.astral_dice.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * 清扫:扫地机立牌(jasmine)主动技能效果,持续 1 分钟。
 * 效果期间:获得迅捷(+20% 移动速度),并减少当前 30% 护甲值。
 *
 * 注意:1.20.1 的 addAttributeModifier(Attribute, String, ...) 要求 UUID 字符串
 * (内部 UUID.fromString),不能像 1.21.1 那样传 ResourceLocation 路径;此处使用
 * 与语义名绑定的确定性 UUID(astral_dice:jasmine_sweep_speed/armor 的 UUIDv5)。
 */
public class JasmineSweepEffect extends MobEffect {
    public JasmineSweepEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x87CEEB);
        this.addAttributeModifier(Attributes.MOVEMENT_SPEED, "82e0baa6-d739-530c-9a5a-4819a5602069",
                0.2, AttributeModifier.Operation.MULTIPLY_TOTAL);
        this.addAttributeModifier(Attributes.ARMOR, "99ea571d-1b0d-5670-83bb-78d7593b25f0",
                -0.3, AttributeModifier.Operation.MULTIPLY_TOTAL);
    }
}
