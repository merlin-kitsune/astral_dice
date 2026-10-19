package com.merlinkitsune.astral_dice.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 温暖（多萝西立牌 dorothy 被动）：层数 = {@code amplifier + 1}，最多 5 层。
 *
 * <p>层数唯一事实源 = 本效果的 amplifier；防御力 +1/层的属性修饰符由
 * {@code DorothySignItem#refreshWarmthModifier} 按 amplifier 刷写。
 */
public class DorothyWarmthEffect extends MobEffect {
    public static final int MAX_STACKS = 5;

    public DorothyWarmthEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xFFB74D);
    }
}
