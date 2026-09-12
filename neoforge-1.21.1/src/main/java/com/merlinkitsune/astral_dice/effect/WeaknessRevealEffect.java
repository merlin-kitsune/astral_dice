package com.merlinkitsune.astral_dice.effect;

import com.merlinkitsune.starengine.event.ModEffectRemoval;
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
        player.addEffect(new MobEffectInstance(ModEffects.WEAKNESS_REVEAL,
                DURATION_TICKS, total - 1, false, true, true));
    }

    /** 当前层数(无效果为 0) */
    public static int getStacks(Player player) {
        if (player == null) return 0;
        MobEffectInstance instance = player.getEffect(ModEffects.WEAKNESS_REVEAL);
        return instance != null ? instance.getAmplifier() + 1 : 0;
    }

    /** 减少 1 层;归零时移除效果 */
    public static void consumeOne(Player player) {
        if (player == null || player.level().isClientSide()) return;
        MobEffectInstance instance = player.getEffect(ModEffects.WEAKNESS_REVEAL);
        if (instance == null) return;
        int remaining = instance.getAmplifier();
        if (remaining <= 0) {
            ModEffectRemoval.remove(player, ModEffects.WEAKNESS_REVEAL);
        } else {
            player.addEffect(new MobEffectInstance(ModEffects.WEAKNESS_REVEAL,
                    DURATION_TICKS, remaining - 1, false, true, true));
        }
    }

    /** 清空弱点识破 */
    public static void removeAll(Player player) {
        if (player == null || player.level().isClientSide()) return;
        ModEffectRemoval.remove(player, ModEffects.WEAKNESS_REVEAL);
    }
}
