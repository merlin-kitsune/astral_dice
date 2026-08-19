package com.merlinkitsune.astral_dice.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 嘬一口:吸血鬼立牌(papara)主动技能效果,持续 3 分钟。
 */
public class PaparaBiteEffect extends MobEffect {
    public PaparaBiteEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xDC143C);
    }
}
