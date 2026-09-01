package com.merlinkitsune.astral_dice.item;
import com.merlinkitsune.astral_dice.item.CuriosCompat;

import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.component.GameplayConstants;

import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.event.ModEffectRemoval;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * "治愈"点数管理器(玩家级共享资源,与具体饰品解耦)。
 *
 * <p>治愈体系由独立的 30 秒治愈计时器驱动({@link GameplayConstants#HEALING_TIMER_TICKS}):
 * <ul>
 *   <li><b>触发骰神赐福</b> → 先增加医疗箱筹码的治愈点(紧急 +1、完备 +3,受上限),
 *       再获得 当前治愈点×2 的治疗量(回血,不扣点),并启动/重置 30 秒计时器;</li>
 *   <li><b>治愈计时器到期</b> → 治愈点减半(向下取整);若仍处于骰神赐福且剩余点数 &gt; 0
 *       则再次回血并重置计时器,否则等待下次触发;</li>
 *   <li><b>骰神赐福结束</b> → 仅清除赐福周期标记(减半统一由计时器到期处理)。</li>
 * </ul>
 * 治愈点为单一数值池(附件 healing_points),由史莱姆立牌被动/主动、缓冲盾牌、
 * 医疗箱(赐福触发时)等来源增加;治愈点为 0 时不显示效果。
 *
 * <p>执行优先级:触发赐福时的回血结算由 {@link #onBlessingTriggered} 统一在
 * 事件块末尾调用,晚于所有影响治愈点数量的效果(史莱姆受击 +1、缓冲盾牌 +2 等
 * 在伤害事件更早处已执行;医疗箱加点在本方法内先于回血完成)。
 */
public final class HealingManager {
    /** 治愈点上限(固定 32 点,不再随最大生命值变化) */
    public static final int HEALING_POINT_CAP = 32;
    /** 紧急医疗箱触发骰神赐福时增加的治愈点 */
    public static final int MEDKIT_EMERGENCY_POINTS = 1;
    /** 完备医疗箱触发骰神赐福时增加的治愈点 */
    public static final int MEDKIT_COMPLETE_POINTS = 3;

    private HealingManager() {
    }

    // ── 点数读取 ──────────────────────────────────────────────────────────────

    /** 当前治愈点(单一数值池,恒 ≥ 0),供显示/回血/美工刀增伤使用 */
    public static int getPoints(Player player) {
        return ModAttachments.getHealingPoints(player);
    }

    /**
     * 治愈点上限 = 固定 32 点(不再随最大生命值变化)。
     */
    public static int getCap(Player player) {
        return HEALING_POINT_CAP;
    }

    // ── 点数增减 ─────────────────────────────────────────────────────────────

    /**
     * 获得治愈点(纯加点,受上限;不立即回血——回血统一在触发骰神赐福时结算)。
     * 返回增加后的总治愈点。
     */
    public static int add(Player player, int amount) {
        if (player.level().isClientSide()) return getPoints(player);
        int cap = getCap(player);
        int newPoints = Math.min(getPoints(player) + amount, cap);
        ModAttachments.setHealingPoints(player, newPoints);
        updateEffect(player);
        return newPoints;
    }

    /**
     * 消耗治愈点(按总点扣除,下限 0),返回实际消耗的量。
     */
    public static int spend(Player player, int amount) {
        if (player.level().isClientSide()) return 0;
        int total = getPoints(player);
        int spent = Math.min(total, amount);
        if (spent > 0) {
            ModAttachments.setHealingPoints(player, total - spent);
            updateEffect(player);
        }
        return spent;
    }

    /** 清零治愈点并移除"治愈"效果(死亡时调用) */
    public static void clear(Player player) {
        if (player.level().isClientSide()) return;
        ModAttachments.setHealingPoints(player, 0);
        ModAttachments.setHealingPrevBlessing(player, false);
        ModAttachments.setHealingTimerEnd(player, 0);
        ModEffectRemoval.remove(player, ModEffects.HEALING.get());
    }

    // ── 治愈计时器/骰神赐福结算 ──────────────────────────────────────────────

    /**
     * 触发骰神赐福时调用:
     * 1. 先追加所有筹码提供的初始治愈点;
     * 2. 再按当前治愈点×2 回血;
     * 3. 启动/重置 30 秒治愈计时器。
     */
    public static void onBlessingTriggered(Player player) {
        if (player.level().isClientSide()) return;
        addChipPoints(player);
        triggerHealing(player);
        ModAttachments.setHealingPrevBlessing(player, true);
        updateEffect(player);
    }

    /**
     * 骰神赐福结束时调用:仅清除赐福周期标记。
     * 治愈点减半统一由独立计时器到期处理。
     */
    public static void onBlessingEnded(Player player) {
        if (player.level().isClientSide()) return;
        ModAttachments.setHealingPrevBlessing(player, false);
        updateEffect(player);
    }

    /**
     * 治愈计时器到期:
     * 1. 治愈点减半;
     * 2. 若仍处于骰神赐福且治愈点 > 0,再次回血并重置计时器;
     * 3. 否则保留减半后的治愈点,等待下次触发。
     */
    public static void onTimerEnded(Player player) {
        if (player.level().isClientSide()) return;
        int total = getPoints(player);
        int half = total / 2;
        ModAttachments.setHealingPoints(player, half);
        if (player.hasEffect(ModEffects.DICE_BLESSING.get()) && half > 0) {
            triggerHealing(player);
        } else {
            ModAttachments.setHealingTimerEnd(player, 0);
        }
        updateEffect(player);
    }

    /** 按当前治愈点×2 回血,并启动/重置 30 秒治愈计时器 */
    private static void triggerHealing(Player player) {
        int total = getPoints(player);
        if (total > 0) {
            player.heal(total * 2);
        }
        ModAttachments.setHealingTimerEnd(player,
                player.level().getGameTime() + GameplayConstants.HEALING_TIMER_TICKS);
    }

    /** 追加所有筹码提供的初始治愈点(仅在触发治愈效果条件时调用) */
    private static void addChipPoints(Player player) {
        addMedkitPoints(player);
    }

    /** 装备的医疗箱筹码触发赐福加点(紧急 +1、完备 +3,可叠加,受上限) */
    private static void addMedkitPoints(Player player) {
        var curios = CuriosCompat.getCuriosInventory(player);
        if (curios.isEmpty()) return;
        var inventory = curios.get();
        int points = 0;
        if (inventory.findFirstCurio(s -> s.is(ModItems.MEDKIT_EMERGENCY_CHIP.get())).isPresent()) {
            points += MEDKIT_EMERGENCY_POINTS;
        }
        if (inventory.findFirstCurio(s -> s.is(ModItems.MEDKIT_COMPLETE_CHIP.get())).isPresent()) {
            points += MEDKIT_COMPLETE_POINTS;
        }
        if (points > 0) {
            add(player, points);
        }
    }

    // ── 每 tick 驱动:上限收缩 + 赐福结束边沿检测 + 效果刷新 ────────────────────

    /**
     * 每 tick 调用(由统一事件驱动,服务端):
     * <ul>
     *   <li>上限动态跟随玩家当前最大生命值:最大生命降低时,现有治愈点收缩到新上限;</li>
     *   <li>赐福结束边沿检测:上一周期有赐福且当前无赐福 → {@link #onBlessingEnded}
     *       (治愈点减半);</li>
     *   <li>刷新"治愈"效果显示。</li>
     * </ul>
     */
    public static void tick(Player player) {
        if (player.level().isClientSide()) return;

        // 上限收缩(最大生命降低时)
        int total = getPoints(player);
        int cap = getCap(player);
        if (total > cap) {
            ModAttachments.setHealingPoints(player, cap);
            total = cap;
        }

        // 治愈独立计时器到期处理
        long timerEnd = ModAttachments.getHealingTimerEnd(player);
        if (timerEnd > 0 && player.level().getGameTime() >= timerEnd) {
            onTimerEnded(player);
        }

        // 赐福结束标记清理(不再减半,减半由计时器处理)
        boolean hasBlessing = player.hasEffect(ModEffects.DICE_BLESSING.get());
        boolean prevBlessing = ModAttachments.isHealingPrevBlessing(player);
        if (prevBlessing && !hasBlessing) {
            onBlessingEnded(player);
        } else if (!prevBlessing && hasBlessing) {
            // 仅在上升沿写入一次,避免每 tick 重写附件触发同步
            ModAttachments.setHealingPrevBlessing(player, true);
        }
        updateEffect(player);
    }

    // ── 效果显示 ───────────────────────────────────────────────────────────────

    /**
     * 刷新"治愈"效果:等级 = 当前治愈点(层数);时长 = 骰神赐福剩余 tick(赐福中)
     * 或治愈计时器剩余时间。无治愈点,或计时结束且未再次触发赐福时移除效果。
     */
    public static void updateEffect(Player player) {
        if (player.level().isClientSide()) return;
        int total = getPoints(player);
        if (total <= 0) {
            ModEffectRemoval.remove(player, ModEffects.HEALING.get());
            return;
        }
        long now = player.level().getGameTime();
        long timerEnd = ModAttachments.getHealingTimerEnd(player);
        MobEffectInstance blessing = player.getEffect(ModEffects.DICE_BLESSING.get());
        // 仅在有骰神赐福或治愈计时器仍在运行时显示效果图标;
        // 计时结束且未再次触发赐福时移除图标,避免残留。
        if (blessing == null && timerEnd <= now) {
            ModEffectRemoval.remove(player, ModEffects.HEALING.get());
            return;
        }
        int remain;
        if (blessing != null) {
            remain = Math.max(1, blessing.getDuration());
        } else {
            remain = (int) Math.max(1, timerEnd - now);
        }
        // 效果已存在且层级一致、剩余时长充足时不重复施加,避免每 tick 触发效果更新/同步包
        MobEffectInstance existing = player.getEffect(ModEffects.HEALING.get());
        if (existing != null && existing.getAmplifier() == total - 1 && existing.getDuration() > 20) {
            return;
        }
        // amplifier = 层数 - 1(1 层显示 I 级);visible=true 使效果在 HUD 正常显示
        player.addEffect(new MobEffectInstance(ModEffects.HEALING.get(), remain, total - 1, false, false, true));
    }
}
