package com.merlinkitsune.astral_dice.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 「葫芦饮」：金鲤立牌 souri 的【葫芦饮】投掷物被拾取后施加的标记效果。
 *
 * <p>**本效果不带任何原版属性修饰符** —— 攻击力 {@code +3} 由
 * {@code combat/DiceCombatModifiers} 的实时谓词提供（按效果存在与否求和），
 * 故效果被移除 / 到期 / 卸下时不需要摘除任何 AttributeModifier。
 *
 * <p>硬口径（用户需求）：**一次只能拾取一个** —— 拾取方身上已有本效果或
 * {@link ModEffects#IMMORTAL_BREW} 时，{@code entity/SouriDrinkEntity} 拒绝拾取。
 *
 * <p>图标 = {@code images/葫芦饮.png}（实装路径 {@code textures/mob_effect/gourd_drink.png}）。
 */
public class GourdDrinkEffect extends MobEffect {

    /** 持续时间 1:00 = 1200 tick（用户需求冻结值） */
    public static final int DURATION_TICKS = 1200;

    /** 骰战攻击力加成（单层、固定值） */
    public static final double ATTACK_BONUS = 3.0;

    public GourdDrinkEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xE0A63C);
    }
}
