package com.merlinkitsune.astral_dice.effect;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * 岿然不动:效果期间每层护甲值 +8(ADD_VALUE,对应骰战防御力 +2.4,按"防御 = 2 + 护甲×0.30 + 0.85×韧性"折算),
 * 由骰战防御修饰器迁移而来——真实护甲与骰战均正确生效,避免双重计算。
 *
 * 注意:1.21.1 的 addAttributeModifier(Holder<Attribute>, ResourceLocation, double, Operation)
 * 第 2 参是修饰器 id(ResourceLocation),不再是 1.20.1 的 UUID 字符串;此处用语义名
 * astral_dice:unwavering_armor(1.20.1 线取同一语义名的 UUIDv5 = a941d5ed-605a-55c2-834c-4cf3ba28dab0)。
 *
 * 修饰器随 amplifier 线性放大:1.21.1 由 MobEffect.AttributeTemplate 在施加时按
 * amount × (amplifier + 1) 取值(已无 getAttributeModifierValue 这个覆写点),故 8.0 即"每层护甲 +8":
 * amplifier 0 → +8、1 → +16、2 → +24(上限由 UnwaveringCardItem 控制,最多 3 层)。
 */
public class UnwaveringEffect extends MobEffect {
    public UnwaveringEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x4A90D9);
        this.addAttributeModifier(Attributes.ARMOR,
                ResourceLocation.fromNamespaceAndPath("astral_dice", "unwavering_armor"),
                8.0, AttributeModifier.Operation.ADD_VALUE);
    }
}
