package com.merlinkitsune.astral_dice.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 铁处女（修女立牌 alana 主动）：0:30 内免疫一切伤害与负面效果，期间玩家半透明显示。
 */
public class AlanaIronMaidenEffect extends MobEffect {
    public AlanaIronMaidenEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xB0BEC5);
    }
}
