package com.merlinkitsune.astral_dice.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 摸鱼（大肥鱼立牌 deepseek 被动）：期间免疫伤害与负面效果、不被怪物索敌、半透明显示。
 *
 * <p>由 {@code DeepseekSignItem} 在「位置 10 秒未变」时刷新；玩家一旦移动立即移除。
 */
public class DeepseekFishingEffect extends MobEffect {
    public DeepseekFishingEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x4FC3F7);
    }
}
