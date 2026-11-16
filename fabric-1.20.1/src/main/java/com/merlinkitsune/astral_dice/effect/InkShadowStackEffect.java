package com.merlinkitsune.astral_dice.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 小猎手立牌被动「双生之影」的 HUD 载体效果（与 1.21.1 线等价）。
 *
 * <p>本效果<b>只负责显示</b>：真正的层数真值在附件
 * {@code ModAttachments.INKSHADOW_STACKS / INKSHADOW_PAIR_EXPIRIES}，攻击力加成由
 * {@code InkShadowSignManager.refreshStacks} 以 {@code AttackDamage} 修饰符施加；效果本身
 * 不参与任何伤害计算，amplifier = 层数 - 1，duration 跟随最晚到期的那一对门。
 *
 * <p>图标 = 立牌物品贴图（{@code textures/mob_effect/inkshadow_stack.png}，与 1.21.1 线同一张图）。
 */
public class InkShadowStackEffect extends MobEffect {

    public InkShadowStackEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x5A2E8A);
    }
}
