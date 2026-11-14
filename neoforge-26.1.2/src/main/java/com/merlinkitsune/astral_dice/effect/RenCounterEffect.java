package com.merlinkitsune.astral_dice.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 「反击」:<b>10 秒反击状态</b>的 HUD 载体。
 *
 * <p>状态本体与唯一权威在 {@code item/CounterStateManager}:近战命中且攻击者不是玩家/女仆时
 * 激活,固定 200 tick;状态期间每次符合条件的近战命中都免费反击,不再消耗可激活次数。
 * 可激活次数在附件 {@code ren_counter_charges},**不再**镜像成图标。
 *
 * <p>图标 = {@code images/反击.png}(实装路径 {@code textures/mob_effect/ren_counter.png})。
 * 无属性修饰符、无粒子({@code visible=false},但 {@code showIcon=true})。
 */
public class RenCounterEffect extends MobEffect {
    public RenCounterEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xFF7A2E);
    }
}
