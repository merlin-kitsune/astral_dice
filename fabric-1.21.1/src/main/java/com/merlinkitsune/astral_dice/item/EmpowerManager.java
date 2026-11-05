package com.merlinkitsune.astral_dice.item;

import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.effect.EmpowerEffect;
import com.merlinkitsune.astral_dice.item.chip.PrimordialCoreChipItem;
import net.minecraft.world.entity.player.Player;

/**
 * 「赋能」资源管理器(玩家级)。
 *
 * <p>层数存储于 {@link EmpowerEffect}(amplifier = 层数-1,上限见
 * {@link EmpowerEffect#MAX_STACKS})。
 *
 * <ul>
 *   <li>来源:佩戴「原初核心」筹码时,每消耗 1 层充能转化为 1 层赋能
 *       (经 {@link ChargeManager#consume} 统一挂钩);</li>
 *   <li>计时器:每 0:30 减少 1 层,剩余层数为 1 时直接归 0
 *       (consumeOne 在 amplifier 为 0 时直接移除效果)。</li>
 * </ul>
 *
 * 计时器仅服务端运行,由 {@code PlayerTickEvents} 每 20 tick 驱动一次。
 */
public final class EmpowerManager {
    /** 每 0:30 减少 1 层(与 {@link EmpowerEffect#DECAY_INTERVAL_TICKS} 同源) */
    public static final int DECAY_INTERVAL_TICKS = EmpowerEffect.DECAY_INTERVAL_TICKS;

    private EmpowerManager() {
    }

    public static boolean hasEmpower(Player player) {
        return EmpowerEffect.getStacks(player) > 0;
    }

    public static int getStacks(Player player) {
        return EmpowerEffect.getStacks(player);
    }

    /** 增加赋能层数,并把递减计时器重新起算(获得后 30 秒开始递减) */
    public static void addStacks(Player player, int stacks) {
        if (player == null || player.level().isClientSide()) return;
        if (stacks <= 0) return;
        int total = EmpowerEffect.addStacks(player, stacks);
        if (total > 0) {
            ModAttachments.setEmpowerDecayAt(player, player.level().getGameTime() + DECAY_INTERVAL_TICKS);
        }
    }

    /**
     * 充能被消耗时调用(由 {@link ChargeManager#consume} 统一挂钩):
     * 佩戴「原初核心」时,按消耗层数获得等量赋能。
     */
    public static void onChargeConsumed(Player player, int amount) {
        if (player == null || player.level().isClientSide() || amount <= 0) return;
        if (!PrimordialCoreChipItem.isEquipped(player)) return;
        addStacks(player, amount);
    }

    /** 每 20 tick 驱动:每 0:30 减少 1 层;剩余层数为 1 时直接归 0 */
    public static void tick(Player player) {
        if (player == null || player.level().isClientSide()) return;
        int stacks = EmpowerEffect.getStacks(player);
        if (stacks <= 0) {
            if (ModAttachments.getEmpowerDecayAt(player) != 0) {
                ModAttachments.setEmpowerDecayAt(player, 0);
            }
            return;
        }
        long now = player.level().getGameTime();
        long next = ModAttachments.getEmpowerDecayAt(player);
        if (next <= 0) {
            // 层数存在但计时器缺失(如热重载):重新起算,并刷新效果时长让面板倒计时跟上
            ModAttachments.setEmpowerDecayAt(player, now + DECAY_INTERVAL_TICKS);
            EmpowerEffect.refresh(player);
            return;
        }
        // 注意:此处**不可**每秒刷新效果时长 —— 面板倒计时由客户端自行递减实例时长,
        // 每秒重写会把倒计时重置成恒显 0:31(看起来"永远不动")。
        // 时长(620)> 递减间隔(600)已足够保证递减先于到期发生。
        if (now < next) return;
        // 递减 1 层:剩余层数为 1 时 consumeOne 内部直接移除效果(归 0)
        EmpowerEffect.consumeOne(player);
        int remain = EmpowerEffect.getStacks(player);
        ModAttachments.setEmpowerDecayAt(player, remain > 0 ? now + DECAY_INTERVAL_TICKS : 0);
    }

    /** 清空赋能并停止计时器 */
    public static void removeAll(Player player) {
        if (player == null || player.level().isClientSide()) return;
        EmpowerEffect.removeAll(player);
        ModAttachments.setEmpowerDecayAt(player, 0);
    }
}
