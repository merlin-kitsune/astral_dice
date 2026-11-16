package com.merlinkitsune.astral_dice.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import com.merlinkitsune.astral_dice.event.DamageEffectCardHandler;

/**
 * 远程/魔法伤害加成效果(标记类):伤害效果牌使用后获得的加成状态,
 * 实际数值结算在 DamageEffectCardHandler;效果结束触发待定冷却。
 */
public class RangedBoostEffect extends MobEffect {
    public RangedBoostEffect(int color) {
        super(MobEffectCategory.BENEFICIAL, color);
    }
}
