package com.merlinkitsune.astral_dice.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 「仙酿」：金鲤立牌 souri 的【仙酿】投掷物被拾取后施加的标记效果。
 *
 * <p>**本效果不带任何原版属性修饰符** —— 攻击力 {@code +4} 由
 * {@code combat/DiceCombatModifiers} 的实时谓词提供；迅捷（{@code MobEffects.MOVEMENT_SPEED}
 * 等级 II、2:00）与「立即回复 6 点生命值」在拾取当刻由
 * {@code item/sign/SouriSignManager#onDrinkPicked} 一次性结算。
 *
 * <p>硬口径（用户需求）：**一次只能拾取一个** —— 拾取方身上已有本效果或
 * {@link ModEffects#GOURD_DRINK} 时，{@code entity/SouriDrinkEntity} 拒绝拾取。
 *
 * <p>图标 = {@code images/仙酿.png}（实装路径 {@code textures/mob_effect/immortal_brew.png}）。
 */
public class ImmortalBrewEffect extends MobEffect {

    /** 持续时间 2:00 = 2400 tick（用户需求冻结值） */
    public static final int DURATION_TICKS = 2400;

    /** 骰战攻击力加成（单层、固定值） */
    public static final double ATTACK_BONUS = 4.0;

    /** 拾取当刻立即回复的生命值 */
    public static final float PICKUP_HEAL = 6.0F;

    /** 迅捷等级（amplifier 1 = 迅捷 II） */
    public static final int SPEED_AMPLIFIER = 1;

    public ImmortalBrewEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xC9A227);
    }
}
