package com.merlinkitsune.astral_dice.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * 岿然不动:效果期间护甲值 +8(ADD_VALUE,对应骰战防御力 +4,按"防御 = 2 + 护甲÷2"折算),
 * 由骰战防御修饰器迁移而来——真实护甲与骰战均正确生效,避免双重计算。
 *
 * 注意:1.20.1 的 addAttributeModifier(Attribute, String, ...) 要求 UUID 字符串
 * (内部 UUID.fromString),不能像 1.21.1 那样传 ResourceLocation 路径;此处使用
 * 与语义名绑定的确定性 UUID(astral_dice:unwavering_armor 的 UUIDv5)。
 */
public class UnwaveringEffect extends MobEffect {
    public UnwaveringEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x4A90D9);
        this.addAttributeModifier(Attributes.ARMOR, "a941d5ed-605a-55c2-834c-4cf3ba28dab0",
                8.0, AttributeModifier.Operation.ADDITION);
    }
}
