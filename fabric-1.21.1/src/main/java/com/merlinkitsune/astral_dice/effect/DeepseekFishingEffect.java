package com.merlinkitsune.astral_dice.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 摸鱼（大肥鱼立牌 deepseek 被动）：期间免疫伤害与负面效果、不被怪物索敌、半透明显示。
 *
 * <p>由 {@code DeepseekSignItem} 在「精确位置连续 10 秒未变」时授予，静止时逐 tick 滚动续期；
 * 获得后累计移动 100 tick（5 秒）才解除（中途停下不清零累计，仅转头不算移动）。
 */
public class DeepseekFishingEffect extends MobEffect {
    public DeepseekFishingEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x4FC3F7);
    }
}
