package com.merlinkitsune.astral_dice.effect;

import com.merlinkitsune.astral_dice.event.ModEffectRemoval;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;

/**
 * 弱点识破(枪匠立牌 Moses):玩家增益,层数 = amplifier + 1。
 * 每层:攻击力 +1、防御力 +1、骰点最低数 +1;骰神赐福结束后减少 1 层。
 */
public class WeaknessRevealEffect extends MobEffect {
    /** 最大层数 */
    public static final int MAX_STACKS = 4;
    /** 效果时长(无限,卸下立牌/清空时移除) */
    public static final int DURATION_TICKS = Integer.MAX_VALUE;

    public WeaknessRevealEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xC8A415);
    }

    /** 增加层数(最多 MAX_STACKS);未装备时调用方自行判断。 */
    public static void addStacks(Player player, int stacks) {
        if (player == null || player.level().isClientSide()) return;
        if (stacks <= 0) return;
        int total = Math.min(MAX_STACKS, getStacks(player) + stacks);
        player.addEffect(new MobEffectInstance(ModEffects.WEAKNESS_REVEAL.get(),
                DURATION_TICKS, total - 1, false, true, true));
    }

    /** 当前层数(无效果为 0) */
    public static int getStacks(Player player) {
        if (player == null) return 0;
        MobEffectInstance instance = player.getEffect(ModEffects.WEAKNESS_REVEAL.get());
        return instance != null ? instance.getAmplifier() + 1 : 0;
    }

    /**
     * 减少 1 层;归零时移除效果。
     *
     * <p><b>必须先移除旧实例、再写入更低层数</b>:原版 {@code MobEffectInstance#update} 只接受
     * <b>更高的 amplifier</b>,直接 {@code addEffect} 一个更低层数的实例会被忽略(只进
     * {@code hiddenEffect}),层数永不下降——此前赐福结束时调用本方法,层数实际不减。
     */
    public static void consumeOne(Player player) {
        if (player == null || player.level().isClientSide()) return;
        MobEffectInstance instance = player.getEffect(ModEffects.WEAKNESS_REVEAL.get());
        if (instance == null) return;
        int remaining = instance.getAmplifier();
        ModEffectRemoval.remove(player, ModEffects.WEAKNESS_REVEAL.get());
        if (remaining > 0) {
            player.addEffect(new MobEffectInstance(ModEffects.WEAKNESS_REVEAL.get(),
                    DURATION_TICKS, remaining - 1, false, true, true));
        }
    }

    /** 清空弱点识破 */
    public static void removeAll(Player player) {
        if (player == null || player.level().isClientSide()) return;
        ModEffectRemoval.remove(player, ModEffects.WEAKNESS_REVEAL.get());
    }
}
