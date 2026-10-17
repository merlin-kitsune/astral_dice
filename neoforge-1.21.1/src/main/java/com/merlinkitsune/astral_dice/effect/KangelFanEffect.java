package com.merlinkitsune.astral_dice.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * 「粉丝」:**目标标记** —— 超天酱立牌(kangel)被动「小天使请安」施加在
 * **被攻击/攻击者**身上的效果。
 *
 * <h3>语义(2026-10-17 起只此一种)</h3>
 * 佩戴者近战命中、或佩戴者(及其一心同体的地雷女队友)受到敌对攻击时,由
 * {@code KangelSignItem#applyFanTo} 施加到**攻击方/被攻击目标**身上,
 * {@code amplifier = 0}、**无限时长**({@code MobEffectInstance.INFINITE_DURATION} ⇒ 目标死亡才自然消失)。
 *
 * <p>⚠️ 拆 id 前本效果**同时**充当佩戴者自身的粉丝数指示器 —— 那会让「玩家身上有本效果」
 * 混淆成「她是粉丝」,导致超天酱互殴误判 −1、被标记者的自身镜像清掉别人的标记。
 * 指示器现已独立为 {@link KangelFanIndicatorEffect}({@code kangel_fan_indicator}),
 * **本效果是「是否粉丝」的唯一真值**,任何计数/显示/战斗判据都不得再往它上面挂第二层语义。
 *
 * <p>效果本身**不提供任何属性修饰器、不产生周期反噬** —— 它只是一个可丢弃、可重建的
 * 状态载体(计数真值在附件 {@code ModAttachments#KANGEL_FAN_COUNT} / {@code KANGEL_FAN_TARGETS}),
 * 其「减伤 1 点」的判定落在 {@code event/ChipDamageHandler} 的固定点数减法段
 * (读取本效果是否仍在**攻击者**身上,而非读任何附件)。
 *
 * <p>配色:{@link MobEffectCategory#HARMFUL}(对目标而言是负面标记;裁决 #5)。
 * 图标 = {@code images/粉丝.png}(实装路径 {@code textures/mob_effect/kangel_fan.png},
 * 逐字节复制)。
 */
public class KangelFanEffect extends MobEffect {
    /** 无限时长(标记不随时间衰减;目标死亡自然消失) */
    public static final int DURATION_TICKS = MobEffectInstance.INFINITE_DURATION;

    public KangelFanEffect() {
        super(MobEffectCategory.HARMFUL, 0xFF8FB1);
    }

    /**
     * 给 {@code target} 挂上「粉丝」标记(**唯一施加点**;已带标记则什么都不做)。
     *
     * <p>⚠️ 只认本效果 = **目标标记**;佩戴者的粉丝数显示走 {@link KangelFanIndicatorEffect}。
     */
    public static void applyMark(LivingEntity target) {
        if (target == null || target.level().isClientSide()) return;
        if (has(target)) return;
        target.addEffect(new MobEffectInstance(ModEffects.KANGEL_FAN,
                DURATION_TICKS, 0, false, false, true));
    }

    /** 目标是否带有「粉丝」标记({@code ChipDamageHandler} 的 -1 固定点数减法判据) */
    public static boolean has(LivingEntity target) {
        return target != null && target.hasEffect(ModEffects.KANGEL_FAN);
    }
}
