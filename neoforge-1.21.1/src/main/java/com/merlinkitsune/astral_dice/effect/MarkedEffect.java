package com.merlinkitsune.astral_dice.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

public class MarkedEffect extends MobEffect {
    public MarkedEffect() {
        super(MobEffectCategory.HARMFUL, 0xAA0000);
    }
}
