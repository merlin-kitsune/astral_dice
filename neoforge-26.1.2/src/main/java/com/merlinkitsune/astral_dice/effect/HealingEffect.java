package com.merlinkitsune.astral_dice.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * "治愈"效果:显示玩家当前的治愈点数(效果等级 = 当前治愈点,时长 = 骰神赐福剩余时间;
 * 无赐福时以固定时长持续刷新保持常显)。由治愈体系 {@link com.merlinkitsune.astral_dice.item.HealingManager}
 * 维护;治愈点数为 0 时效果自动结束。
 */
public class HealingEffect extends MobEffect {
    public HealingEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xFF69B4);
    }
}
