package com.merlinkitsune.astral_dice.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * "治愈"效果:显示玩家当前的治愈点数(效果等级 = 总治愈点=基础点+结算点,时长 = 距本次周期结算的剩余时间)。
 * 由史莱姆立牌/医疗箱筹码/美工刀筹码等治愈来源维护;治愈点数为 0 时效果自动结束。
 */
public class HealingEffect extends MobEffect {
    public HealingEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xFF69B4);
    }
}
