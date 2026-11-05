package com.merlinkitsune.astral_dice.effect;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * 清扫:扫地机立牌(jasmine)主动技能效果,持续 1 分钟。
 * 效果期间:获得迅捷(+20% 移动速度),并减少当前 30% 护甲值。
 *
 * 注意:1.21.1 的 addAttributeModifier 第二参是 ResourceLocation(不再是 1.20.1 的 UUID 字符串),
 * 两条修饰器分别为 astral_dice:jasmine_sweep_speed / astral_dice:jasmine_sweep_armor。
 */
public class JasmineSweepEffect extends MobEffect {
    public JasmineSweepEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x87CEEB);
        this.addAttributeModifier(Attributes.MOVEMENT_SPEED,
                ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "jasmine_sweep_speed"),
                0.2, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        this.addAttributeModifier(Attributes.ARMOR,
                ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "jasmine_sweep_armor"),
                -0.3, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }
}
