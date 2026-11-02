package com.merlinkitsune.astral_dice.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 调酒（调酒师立牌 jill）状态指示器：被调制的目标获得的可视标记，2:00。
 *
 * <p>纯标记效果：不加属性、不改伤害，只用于 HUD 图标与倒计时（与教主「降神」同法）。
 */
public class JillMixedEffect extends MobEffect {
    public JillMixedEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xFF9AD1);
    }
}
