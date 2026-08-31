package com.merlinkitsune.astral_dice.effect;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * 岿然不动:效果期间护甲值 +8(ADD_VALUE,对应骰战防御力 +4,按"防御 = 2 + 护甲÷2"折算),
 * 由骰战防御修饰器迁移而来——真实护甲与骰战均正确生效,避免双重计算。
 */
public class UnwaveringEffect extends MobEffect {
    public UnwaveringEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x4A90D9);
        this.addAttributeModifier(Attributes.ARMOR,
                ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "unwavering_armor"),
                8.0, AttributeModifier.Operation.ADD_VALUE);
    }
}
