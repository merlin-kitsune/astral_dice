package com.merlinkitsune.astral_dice.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 出牌计数效果(标记类):佩戴忍者立牌/魔法秘典且已使用效果牌开始计数时显示,
 * 等级 = 当前计数(第几张效果牌),计数归 0 时移除。维护在各自计数逻辑中。
 */
public class CounterEffect extends MobEffect {
    public CounterEffect(int color) {
        super(MobEffectCategory.BENEFICIAL, color);
    }
}
