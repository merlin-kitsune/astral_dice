package com.merlinkitsune.astral_dice.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 破绽(枪匠立牌 Moses 主动施加给普通敌对生物):
 * 持续 2:00;拥有破绽的敌对目标与枪匠玩家交战时骰点只能为 0,
 * 会被枪匠闪避,闪避后自动反击。
 */
public class MosesBrokenEffect extends MobEffect {
    /** 破绽持续时间:2:00 = 2400 tick */
    public static final int DURATION_TICKS = 20 * 120;

    public MosesBrokenEffect() {
        super(MobEffectCategory.HARMFUL, 0x4A5A6A);
    }
}
