package com.merlinkitsune.astral_dice.effect;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * 岿然不动:效果期间每层护甲值 +8(ADD_VALUE,对应骰战防御力 +4,按"防御 = 2 + 护甲÷2"折算),
 * 由骰战防御修饰器迁移而来——真实护甲与骰战均正确生效,避免双重计算。
 *
 * 修饰器随 amplifier 线性放大(1.21.1/NeoForge):使用 MobEffect#addAttributeModifier 的 curve 重载,
 * curve 的入参就是 amplifier,故 amplifier 0 仍为 +8、每层 +8(8.0 × (amplifier + 1));
 * 叠层上限由 UnwaveringCardItem 控制(amplifier 最大 MAX_EFFECT_STACKS - 1,即 3 层 / 护甲 +24)。
 */
public class UnwaveringEffect extends MobEffect {
    public UnwaveringEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x4A90D9);
        this.addAttributeModifier(Attributes.ARMOR,
                ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "unwavering_armor"),
                AttributeModifier.Operation.ADD_VALUE,
                amp -> 8.0 * (amp + 1));
    }
}
