package com.merlinkitsune.astral_dice.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 爆发:护法立牌(misaki)主动技能效果,持续 60 秒。
 */
public class MisakiBurstEffect extends MobEffect {
    public MisakiBurstEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xFF8C00);
    }
}
