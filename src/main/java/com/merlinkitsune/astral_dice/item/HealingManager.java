package com.merlinkitsune.astral_dice.item;

import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.component.GameplayConstants;

import com.merlinkitsune.astral_dice.effect.ModEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * "治愈"点数管理器(玩家级共享资源,与具体饰品解耦)。
 *
 * <p>治愈体系无独立计时器,完全遵循"骰神赐福"效果的生命周期:
 * <ul>
 *   <li><b>触发骰神赐福</b> → 先增加医疗箱筹码的治愈点(紧急 +1、完备 +3,受上限),
 *       再获得 当前治愈点×2 的治疗量(回血,不扣点);</li>
 *   <li><b>骰神赐福结束</b> → 治愈点减半(向下取整)。</li>
 * </ul>
 * 治愈点为单一数值池(附件 healing_points),由史莱姆立牌被动/主动、缓冲盾牌、
 * 医疗箱(赐福触发时)等来源增加;治愈点为 0 时不显示效果。
 *
 * <p>执行优先级:触发赐福时的回血结算由 {@link #onBlessingTriggered} 统一在
 * 事件块末尾调用,晚于所有影响治愈点数量的效果(史莱姆受击 +1、缓冲盾牌 +2 等
 * 在伤害事件更早处已执行;医疗箱加点在本方法内先于回血完成)。
 */
public final class HealingManager {
    /** 紧急医疗箱装备时立即恢复的生命值(1 治愈单位 = 2 点血量) */
    public static final int MEDKIT_EMERGENCY_HEAL = 2;
    /** 完备医疗箱装备时立即恢复的生命值(1 治愈单位 = 2 点血量) */
    public static final int MEDKIT_COMPLETE_HEAL = 6;
    /** 紧急医疗箱触发骰神赐福时增加的治愈点 */
    public static final int MEDKIT_EMERGENCY_POINTS = 1;
    /** 完备医疗箱触发骰神赐福时增加的治愈点 */
    public static final int MEDKIT_COMPLETE_POINTS = 3;
    /** 无骰神赐福时治愈效果的常显时长(tick,5 分钟;持续刷新维持显示) */
    public static final int IDLE_EFFECT_TICKS = 6000;

    // 流派注册实现(供 PlayerResourceRegistry 注册;筹码/立牌可通过注册表按类型调用)
    public static final com.merlinkitsune.astral_dice.resource.PlayerResource RESOURCE =
            new com.merlinkitsune.astral_dice.resource.PlayerResource() {
                @Override
                public int get(net.minecraft.world.entity.player.Player player) {
                    return HealingManager.getPoints(player);
                }

                @Override
                public int getCap(net.minecraft.world.entity.player.Player player) {
                    return HealingManager.getCap(player);
                }

                @Override
                public int add(net.minecraft.world.entity.player.Player player, int amount) {
                    return HealingManager.add(player, amount);
                }

                @Override
                public int spend(net.minecraft.world.entity.player.Player player, int amount) {
                    return HealingManager.spend(player, amount);
                }

                @Override
                public void clear(net.minecraft.world.entity.player.Player player) {
                    HealingManager.clear(player);
                }
            };

    private HealingManager() {
    }

    // ── 点数读取 ──────────────────────────────────────────────────────────────

    /** 当前治愈点(单一数值池,恒 ≥ 0),供显示/回血/美工刀增伤使用 */
    public static int getPoints(Player player) {
        return ModAttachments.getHealingPoints(player);
    }

    /**
     * 治愈点上限 = max(10, 玩家最大生命值 ÷ 2)。
     * 即 MC 中 ♥ 的数量(20 HP → 10 点);下限固定 10,避免神秘遗物+ 中佩戴七咒之戒死亡
     * 导致生命值上限丢失时治愈点数上限过低。
     */
    public static int getCap(Player player) {
        return Math.max(10, (int) player.getMaxHealth() / 2);
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
        player.removeEffect(ModEffects.HEALING);
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
        if (player.hasEffect(ModEffects.DICE_BLESSING) && half > 0) {
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
    public static void onMedkitEquipped(Player player, int heal) {
        if (player.level().isClientSide()) return;
        if (heal > 0) {
            player.heal(heal);
        }
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

        // 赐福结束标记清理(不再减半,减半由计时器处理)
        boolean hasBlessing = player.hasEffect(ModEffects.DICE_BLESSING);
        if (ModAttachments.isHealingPrevBlessing(player) && !hasBlessing) {
            onBlessingEnded(player);
        } else if (hasBlessing) {
            ModAttachments.setHealingPrevBlessing(player, true);
        }
        updateEffect(player);
    }

    // ── 效果显示 ───────────────────────────────────────────────────────────────

    /**
     * 刷新"治愈"效果:等级 = 当前治愈点(层数);时长 = 骰神赐福剩余 tick(赐福中)
     * 或固定常显时长(无赐福时 5 分钟,每 tick 刷新维持显示)。无治愈点时移除效果。
     */
    public static void updateEffect(Player player) {
        if (player.level().isClientSide()) return;
        int total = getPoints(player);
        if (total <= 0) {
            player.removeEffect(ModEffects.HEALING);
            return;
        }
        int remain = IDLE_EFFECT_TICKS;
        MobEffectInstance blessing = player.getEffect(ModEffects.DICE_BLESSING);
        if (blessing != null) {
            remain = Math.max(1, blessing.getDuration());
        }
        // amplifier = 层数 - 1(1 层显示 I 级);visible=true 使效果在 HUD 正常显示
        player.addEffect(new MobEffectInstance(ModEffects.HEALING, remain, total - 1, false, true, true));
    }
}
