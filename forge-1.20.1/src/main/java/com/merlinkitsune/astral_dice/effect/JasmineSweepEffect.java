package com.merlinkitsune.astral_dice.effect;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 清扫:扫地机立牌(jasmine)主动技能效果,持续 1 分钟。
 * 效果期间:获得迅捷(+20% 移动速度),并减少当前 30% 护甲值。
 * 1.20.1 的 addAttributeModifier(String) 参数是 UUID 字符串(非 1.21 的 ResourceLocation),
 * 必须传确定性 UUID,否则注册时抛 "Invalid UUID string"。
 */
public class JasmineSweepEffect extends MobEffect {
    public JasmineSweepEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x87CEEB);
        this.addAttributeModifier(Attributes.MOVEMENT_SPEED,
                UUID.nameUUIDFromBytes((AstralDiceMod.MODID + ":jasmine_sweep_speed").getBytes(StandardCharsets.UTF_8)).toString(),
                0.2, AttributeModifier.Operation.MULTIPLY_TOTAL);
        this.addAttributeModifier(Attributes.ARMOR,
                UUID.nameUUIDFromBytes((AstralDiceMod.MODID + ":jasmine_sweep_armor").getBytes(StandardCharsets.UTF_8)).toString(),
                -0.3, AttributeModifier.Operation.MULTIPLY_TOTAL);
    }
}
