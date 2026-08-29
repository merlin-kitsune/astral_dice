package com.merlinkitsune.astral_dice.effect;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.UUID;

/**
 * 清扫:扫地机立牌(jasmine)主动技能效果,持续 1 分钟。
 * 效果期间:获得迅捷(+20% 移动速度),并减少当前 30% 护甲值。
 * 1.20.1 属性修饰器用 UUID 字符串注册(由修饰器名稳定派生),对应 1.21 的 ResourceLocation id。
 */
public class JasmineSweepEffect extends MobEffect {
    public JasmineSweepEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x87CEEB);
        this.addAttributeModifier(Attributes.MOVEMENT_SPEED,
                modifierId("jasmine_sweep_speed").toString(),
                0.2, AttributeModifier.Operation.MULTIPLY_TOTAL);
        this.addAttributeModifier(Attributes.ARMOR,
                modifierId("jasmine_sweep_armor").toString(),
                -0.3, AttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    static UUID modifierId(String name) {
        return UUID.nameUUIDFromBytes((AstralDiceMod.MODID + ":" + name).getBytes());
    }
}
