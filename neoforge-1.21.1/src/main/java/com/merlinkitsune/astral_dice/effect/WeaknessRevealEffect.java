package com.merlinkitsune.astral_dice.effect;

import com.merlinkitsune.starenginelib.event.ModEffectRemoval;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;

/**
 * 弱点识破(枪匠立牌 Moses):玩家增益,层数 = amplifier + 1。
 * 每层:攻击力 +1、防御力 +1、骰点最低数 +1;**每分钟(效果自然到期)减少 1 层**。
 */
public class WeaknessRevealEffect extends MobEffect {
    /** 最大层数 */
    public static final int MAX_STACKS = 4;
    /**
     * 效果时长(2026-09-30 用户裁决):由「无限」改为 **1 分钟** —— 效果自然到期即减少 1 层并重置本时长,
     * 与骰神赐福的起止**解耦**。到期链见 {@code combat/DiceCombatEvents#onPassiveStackExpired}。
     * <p>⚠️ 卸下立牌 / 清空仍走 {@link #removeAll}(层数归零时不重新施加)。
     */
    public static final int DURATION_TICKS = 20 * 60;

    public WeaknessRevealEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xC8A415);
    }

    /** 增加层数(最多 MAX_STACKS);未装备时调用方自行判断。 */
    public static void addStacks(Player player, int stacks) {
        if (player == null || player.level().isClientSide()) return;
        if (stacks <= 0) return;
        int total = Math.min(MAX_STACKS, getStacks(player) + stacks);
        player.addEffect(new MobEffectInstance(ModEffects.WEAKNESS_REVEAL,
                DURATION_TICKS, total - 1, false, true, true));
    }

    /** 当前层数(无效果为 0) */
    public static int getStacks(Player player) {
        if (player == null) return 0;
        MobEffectInstance instance = player.getEffect(ModEffects.WEAKNESS_REVEAL);
        return instance != null ? instance.getAmplifier() + 1 : 0;
    }

    /**
     * 「自然到期」后的减 1 层(由 {@code combat/DiceCombatEvents#onPassiveStackDecay} 在到期的
     * <b>下一 tick</b> 调用,见该处注释):层数真值 = 效果实例本身,故此处直接把到期前的
     * {@code amplifier} 减 1 后重施加(并重置 1 分钟计时);归零时不重新施加。
     *
     * <p><b>必须先移除旧实例、再写入更低层数</b>:原版 {@code MobEffectInstance#update} 只接受
     * <b>更高的 amplifier</b>,直接 {@code addEffect} 一个更低层数的实例会被忽略(只进
     * {@code hiddenEffect}),层数永不下降。
     *
     * @param expiredAmplifier 到期前的 {@code amplifier}(当时层数 − 1)
     */
    public static void applyDecayed(Player player, int expiredAmplifier) {
        if (player == null || player.level().isClientSide()) return;
        ModEffectRemoval.remove(player, ModEffects.WEAKNESS_REVEAL);
        if (expiredAmplifier > 0) {
            player.addEffect(new MobEffectInstance(ModEffects.WEAKNESS_REVEAL,
                    DURATION_TICKS, expiredAmplifier - 1, false, true, true));
        }
    }

    /** 清空弱点识破 */
    public static void removeAll(Player player) {
        if (player == null || player.level().isClientSide()) return;
        ModEffectRemoval.remove(player, ModEffects.WEAKNESS_REVEAL);
    }
}
