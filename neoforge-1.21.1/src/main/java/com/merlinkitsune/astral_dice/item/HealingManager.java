package com.merlinkitsune.astral_dice.item;

import com.merlinkitsune.astral_dice.component.ModAttachments;

import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.starenginelib.event.ModEffectRemoval;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * "治愈"点数管理器(玩家级共享资源,与具体饰品解耦)。
 *
 * <p><b>治愈计时器(2026-09-30 用户裁决起与骰神赐福彻底解绑)</b>:由独立的 1:00 计时器驱动
 * ({@link #HEALING_TIMER_SECONDS} 秒一轮):
 * <ul>
 *   <li><b>触发</b>(骰神赐福触发,不再是唯一入口) → 先追加医疗箱筹码的治愈点(紧急 +1、完备 +3,受上限),
 *       再按 当前治愈点 ×2 回血(回血在点数结算之后),并启动/重置计时器;</li>
 *   <li><b>每 1:00 结算</b> → 治愈点减半(向下取整) → <b>再</b>追加医疗箱点数 → 结算后点数 &gt; 0
 *       则按 点数 ×2 回血并重起计时器;点数归 0 则计时器清零,{@link #updateEffect} 随之移除效果。</li>
 * </ul>
 * 例:4 点 + 完备医疗箱(3 点) ⇒ 减半 2 → +3 = 5 ⇒ 回血 10 点 ⇒ 开始下一轮 1:00 计时。
 *
 * <p>治愈点为单一数值池(附件 healing_points),由史莱姆立牌被动/主动、缓冲盾牌、
 * 医疗箱等来源增加;点数 &gt; 0 即显示「治愈」效果(计时器未在跑时显示 ∞),归 0 即移除。
 *
 * <p>执行优先级:触发时的回血结算由 {@link #onBlessingTriggered} 统一在
 * 事件块末尾调用,晚于所有影响治愈点数量的效果(史莱姆受击 +1、缓冲盾牌 +2 等
 * 在伤害事件更早处已执行;医疗箱加点在本方法内先于回血完成)。
 */
public final class HealingManager {
    /** 治愈点上限(固定 32 点,不再随最大生命值变化) */
    public static final int HEALING_POINT_CAP = 32;
    /** 治愈计时器周期(秒):2026-09-30 用户裁决 30 → 60,且与骰神赐福解绑 */
    public static final int HEALING_TIMER_SECONDS = 60;
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
        ModAttachments.setHealingTimerEnd(player, 0);
        ModEffectRemoval.remove(player, ModEffects.HEALING);
    }

    // ── 治愈计时器/骰神赐福结算 ──────────────────────────────────────────────

    /**
     * 触发骰神赐福时调用:
     * 1. 先追加所有筹码提供的初始治愈点;
     * 2. 再按当前治愈点×2 回血;
     * 3. 启动/重置 30 秒治愈计时器。
     */
    /**
     * 触发骰神赐福时调用(治愈体系的**触发点之一**,不再是唯一触发点):
     * 1. 先追加所有筹码提供的治愈点(医疗箱);
     * 2. 再按当前治愈点 ×2 回血;
     * 3. 启动/重置 {@link #HEALING_TIMER_SECONDS} 治愈计时器。
     */
    public static void onBlessingTriggered(Player player) {
        if (player.level().isClientSide()) return;
        addChipPoints(player);
        triggerHealing(player);
        updateEffect(player);
    }


    /**
     * 治愈计时器到期(每 {@link #HEALING_TIMER_SECONDS} 一次,**与骰神赐福无关**):
     * 1. 治愈点减半(向下取整);
     * 2. 追加医疗箱筹码点数(**在减半之后、回血之前** —— 2026-09-30 用户裁决的顺序);
     * 3. 结算后点数 > 0 ⇒ 按 点数 ×2 回血并重起计时器;点数归 0 ⇒ 计时器清零,
     *    {@link #updateEffect} 随即移除「治愈」效果。
     */
    public static void onTimerEnded(Player player) {
        if (player.level().isClientSide()) return;
        int total = getPoints(player);
        ModAttachments.setHealingPoints(player, total / 2);
        addChipPoints(player);
        if (getPoints(player) > 0) {
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
                player.level().getGameTime() + (long) HEALING_TIMER_SECONDS * 20L);
    }

    /** 追加所有筹码提供的初始治愈点(仅在触发治愈效果条件时调用) */
    private static void addChipPoints(Player player) {
        addMedkitPoints(player);
    }

    /** 装备的医疗箱筹码触发赐福加点(紧急 +1、完备 +3,可叠加,受上限) */
    private static void addMedkitPoints(Player player) {
        var curios = CuriosApi.getCuriosInventory(player);
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

        // 2026-09-30:治愈体系与骰神赐福解绑 ⇒ 不再做赐福边沿检测,
        // 减半统一由独立的 1:00 计时器到期处理(onTimerEnded)。
        updateEffect(player);
    }

    // ── 效果显示 ───────────────────────────────────────────────────────────────

    /**
     * 刷新「治愈」效果:等级 = 当前治愈点(层数),时长 = 治愈计时器剩余 tick;
     * 治愈计时器未在跑时用原版「真·无限时长」(显示 ∞)。
     *
     * <p>2026-09-30 用户裁决:与骰神赐福**解绑** —— 只要治愈点 &gt; 0 就显示
     * (不再要求赐福生效或计时器在跑);点数归 0 即移除。
     */
    public static void updateEffect(Player player) {
        if (player.level().isClientSide()) return;
        int total = getPoints(player);
        if (total <= 0) {
            ModEffectRemoval.remove(player, ModEffects.HEALING);
            return;
        }
        long now = player.level().getGameTime();
        long timerEnd = ModAttachments.getHealingTimerEnd(player);
        int remain = timerEnd > now
                ? (int) (timerEnd - now)
                : MobEffectInstance.INFINITE_DURATION;
        // 效果已存在且层级一致、时长充足时不重复施加,避免每 tick 触发效果更新/同步包。
        // ⚠️ 无限时长用 INFINITE_DURATION(-1)判定,不能与普通剩余 tick 比大小。
        MobEffectInstance existing = player.getEffect(ModEffects.HEALING);
        if (existing != null && existing.getAmplifier() == total - 1) {
            int d = existing.getDuration();
            boolean sameInfinite = d == MobEffectInstance.INFINITE_DURATION
                    && remain == MobEffectInstance.INFINITE_DURATION;
            if (sameInfinite || (d > 20 && remain != MobEffectInstance.INFINITE_DURATION)) return;
        }
        // 层级下降(治愈点被减半/消耗)时必须先移除旧实例:原版 MobEffectInstance#update 只接受
        // 更高的 amplifier,直接 addEffect 低层实例会被忽略(只进 hiddenEffect),HUD 等级会停在旧值。
        if (existing != null && existing.getAmplifier() > total - 1) {
            ModEffectRemoval.remove(player, ModEffects.HEALING);
        }
        player.addEffect(new MobEffectInstance(ModEffects.HEALING, remain, total - 1, false, false, true));
    }
}
