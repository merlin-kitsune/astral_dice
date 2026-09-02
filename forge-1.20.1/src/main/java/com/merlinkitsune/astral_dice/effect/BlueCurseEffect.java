package com.merlinkitsune.astral_dice.effect;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 青之诅咒:负面效果。
 * 效果期间护甲值 -20%(最终护甲值向下取整),盔甲韧性 -100%(归 0)。
 * 暂未配置任何触发条件。
 * 1.20.1 的 addAttributeModifier(String) 参数是 UUID 字符串(非 1.21 的 ResourceLocation),
 * 必须传确定性 UUID,否则注册时抛 "Invalid UUID string"。
 */
public class BlueCurseEffect extends MobEffect {
    public BlueCurseEffect() {
        super(MobEffectCategory.HARMFUL, 0x1E90FF);
        this.addAttributeModifier(Attributes.ARMOR,
                UUID.nameUUIDFromBytes((AstralDiceMod.MODID + ":blue_curse_armor").getBytes(StandardCharsets.UTF_8)).toString(),
                -0.2, AttributeModifier.Operation.MULTIPLY_TOTAL);
        this.addAttributeModifier(Attributes.ARMOR_TOUGHNESS,
                UUID.nameUUIDFromBytes((AstralDiceMod.MODID + ":blue_curse_toughness").getBytes(StandardCharsets.UTF_8)).toString(),
                -1.0, AttributeModifier.Operation.MULTIPLY_TOTAL);
    }
}
