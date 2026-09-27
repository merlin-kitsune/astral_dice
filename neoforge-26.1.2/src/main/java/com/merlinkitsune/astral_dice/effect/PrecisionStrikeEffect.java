package com.merlinkitsune.astral_dice.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * 「精准打击」(机械师立牌 megas 的轨道轰炸**层数真值效果**)。
 *
 * <p><b>语义</b>：每层使该目标受到的「轨道轰炸」伤害 +1，**永久持续直到目标死亡**
 * （时长 {@link #DURATION_TICKS} = {@link Integer#MAX_VALUE}；原版效果随实体死亡自然移除，
 * 故「直到死亡」由原版生命周期保证，无需额外清理）。
 *
 * <p><b>层数 == amplifier + 1</b>（与「厄运」「推理时间」等同款口径）。层数来源 = 目标被
 * 「轨道轰炸」命中的**次数**（每命中 1 次 +1 层，由 {@code combat/OrbitalBombardmentManager}
 * 在落地结算时调用 {@link #addStacks}）；伤害加成由同一执行器在结算时读 {@link #getStacks} 叠加。
 *
 * <p><b>作用对象是 {@link LivingEntity}（怪物）而非 Player</b>。层数**只增不减**（永久到死亡），
 * 故叠层直接 {@code addEffect} 施加更高 amplifier 即可 —— 原版 {@code MobEffectInstance#update}
 * 会接受更高的 amplifier（与 {@code MarkManager.apply} 施加「标记」同款机制，不需要「先移除再施加」）。
 *
 * <p>图标 = {@code images/精准打击.png}（实装路径 {@code textures/mob_effect/precision_strike.png}）。
 */
public class PrecisionStrikeEffect extends MobEffect {
    /** 效果时长（无限；「直到目标死亡」由原版实体生命周期保证）。 */
    public static final int DURATION_TICKS = Integer.MAX_VALUE;

    public PrecisionStrikeEffect() {
        super(MobEffectCategory.HARMFUL, 0xFFD700);
    }

    /** 当前「精准打击」层数（无效果为 0）。 */
    public static int getStacks(LivingEntity entity) {
        if (entity == null) return 0;
        MobEffectInstance instance = entity.getEffect(ModEffects.PRECISION_STRIKE);
        return instance != null ? instance.getAmplifier() + 1 : 0;
    }

    /**
     * 给目标叠加 {@code amount} 层「精准打击」。层数只增不减，直接施加更高 amplifier 即可。
     */
    public static void addStacks(LivingEntity entity, int amount) {
        if (entity == null || entity.level().isClientSide() || amount <= 0) return;
        int target = getStacks(entity) + amount;
        entity.addEffect(new MobEffectInstance(ModEffects.PRECISION_STRIKE,
                DURATION_TICKS, target - 1, false, false, true));
    }
}
