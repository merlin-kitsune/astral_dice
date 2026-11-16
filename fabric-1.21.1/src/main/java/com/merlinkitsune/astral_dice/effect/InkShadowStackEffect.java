package com.merlinkitsune.astral_dice.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 「双生之影」：小猎手立牌 inkshadow 的被动层数指示器。
 *
 * <p>层数真值在玩家附件 {@code ModAttachments#INKSHADOW_STACKS}（攻击加成由
 * {@code InkShadowSignManager#refreshStacks} 直接挂 ATTACK_DAMAGE 修饰符），本效果只负责
 * HUD 图标与层数显示（{@code amplifier = 层数 - 1}），持续时间 = 层数剩余时间。
 * 图标用立牌自身贴图（用户 2026-11-10 要求：被动使用小猎手自己立牌图标作为状态指示器）。
 */
public class InkShadowStackEffect extends MobEffect {

    public InkShadowStackEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x5A2E8A);
    }
}
