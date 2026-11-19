package com.merlinkitsune.astral_dice.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 复仇之戟:负面/诅咒效果触发攻击/防御加成时的标记效果(图标 = 复仇之戟自身图标)。
 * 纯标记效果,无每 tick 逻辑;显示/移除由 RevengeHalberdChipItem 每 tick 驱动。
 */
public class RevengeHalberdEffect extends MobEffect {
    public RevengeHalberdEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xB22222);
    }
}
