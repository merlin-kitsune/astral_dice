package com.merlinkitsune.astral_dice.item;

import com.merlinkitsune.astral_dice.component.GameplayConstants;
import com.merlinkitsune.astral_dice.effect.ChargeEffect;
import net.minecraft.world.entity.player.Player;

/**
 * 充能流派管理器(玩家级资源)。
 *
 * <p>层数存储于 {@link ChargeEffect}(amplifier = 层数-1,上限见
 * {@link GameplayConstants#CHARGE_MAX_STACKS})。
 * 流派固定效果(与层数多少无关):拥有至少 1 层充能时,
 * 护甲值 +10%(由 {@link ChargeEffect} 的属性修饰器按最终护甲值计算)、
 * 立牌主动/效果牌冷却时间 -20%。
 */
public final class ChargeManager {
    private ChargeManager() {
    }

    public static boolean hasCharge(Player player) {
        return ChargeEffect.getStacks(player) > 0;
    }

    public static int getStacks(Player player) {
        return ChargeEffect.getStacks(player);
    }

    /** 增加充能层数,返回增加后的层数 */
    public static int addStacks(Player player, int stacks) {
        return ChargeEffect.addStacks(player, stacks);
    }

    /** 减少 1 层 */
    public static void consumeOne(Player player) {
        ChargeEffect.consumeOne(player);
    }

    /** 清空充能 */
    public static void removeAll(Player player) {
        ChargeEffect.removeAll(player);
    }

    /** 返回经过充能减冷却后的 tick 数(拥有充能时 -20%,最少 1 tick) */
    public static long cooldownTicks(Player player, long baseTicks) {
        if (baseTicks <= 1 || !hasCharge(player)) return baseTicks;
        double reduced = baseTicks * (1.0 - GameplayConstants.CHARGE_COOLDOWN_REDUCTION);
        return Math.max(1, (long) Math.ceil(reduced));
    }
}
