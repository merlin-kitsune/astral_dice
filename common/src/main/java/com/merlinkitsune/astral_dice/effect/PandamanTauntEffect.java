package com.merlinkitsune.astral_dice.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 嘲讽(肉弹战车立牌 pandaman 主动):使敌对目标只能攻击对其施加嘲讽的玩家。
 */
public class PandamanTauntEffect extends MobEffect {
    public PandamanTauntEffect() {
        super(MobEffectCategory.HARMFUL, 0xB22222);
    }
}
