package com.merlinkitsune.astral_dice.item;

import com.merlinkitsune.astral_dice.component.GameplayConstants;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import top.theillusivec4.curios.api.CuriosApi;
import com.merlinkitsune.astral_dice.resource.PlayerResource;
import com.merlinkitsune.astral_dice.resource.PlayerResourceRegistry;

/**
 * "治愈"点数管理器(玩家级共享资源,与具体饰品解耦)。
 *
 * <p>治愈点数由两部分构成:
 * <ul>
 *   <li><b>基础点</b>:由装备的医疗箱筹码实时提供(紧急治疗 +1、完备治疗 +3,可叠加),卸下立即移除;</li>
 *   <li><b>结算点(动态)</b>:由史莱姆立牌被动/主动等获取,每个周期倒计时结束后减半(向下取整)。</li>
 * </ul>
 * 总治愈点 = 基础点 - 已消耗基础点 + 结算点;治愈点为 0 时不运行计数器。
 *
 * <p>消耗(spend)按总点数扣除:优先扣结算点(同步收缩,下限 0),不足部分消耗基础点
 * (附件 healing_base_consumed 记录);每个周期结算时结算点减半、已消耗基础点清零
 * ("下一轮治愈触发时再次增加基础点")。
 *
 * <p>周期流程:
 * <ol>
 *   <li>从 0 获得治愈点 → 立即回血(总点 × 2,对应 MC 1♥/层)并启用倒计时;</li>
 *   <li>已有治愈点再获得 → 增加点数并重置倒计时;</li>
 *   <li>倒计时结束(周期结算)→ 回血(总点 × 2),结算点减半(向下取整)、基础点恢复,
 *       新总点 = 基础点 + 减半后剩余;仍 &gt; 0 则进入下一周期,否则结束。</li>
 * </ol>
 */
public final class HealingManager {
    /** 紧急医疗箱基础点加成 */
    public static final int MEDKIT_EMERGENCY_BASE = 1;
    /** 完备医疗箱基础点加成 */
    public static final int MEDKIT_COMPLETE_BASE = 3;

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

    // ── 基础点(下限):由医疗箱实时提供,卸下立即移除 ────────────────────────────

    public static int getBasePoints(Player player) {
        var curios = CuriosApi.getCuriosInventory(player);
        if (curios.isEmpty()) return 0;
        var inventory = curios.get();
        int base = 0;
        if (inventory.findFirstCurio(s -> s.is(ModItems.MEDKIT_EMERGENCY_CHIP.get())).isPresent()) {
            base += MEDKIT_EMERGENCY_BASE;
        }
        if (inventory.findFirstCurio(s -> s.is(ModItems.MEDKIT_COMPLETE_CHIP.get())).isPresent()) {
            base += MEDKIT_COMPLETE_BASE;
        }
        return base;
    }

    // ── 总治愈点 = 基础点 - 已消耗基础点 + 结算点 ──────────────────────────────

    /** 总治愈点(基础点 - 已消耗 + 结算点),供显示/回血/美工刀增伤使用;恒 ≥ 0 */
    public static int getPoints(Player player) {
        return Math.max(0, getBasePoints(player) - getBaseConsumed(player)) + getSettlePoints(player);
    }

    /** 结算点(动态部分,存储于附件,恒 ≥ 0) */
    public static int getSettlePoints(Player player) {
        return ModAttachments.getHealingPoints(player);
    }

    /** 已消耗的基础点(按总点扣减时超出结算点的部分;每个周期结算时清零,即"下一轮治愈触发时再次增加基础点") */
    public static int getBaseConsumed(Player player) {
        int base = getBasePoints(player);
        return Math.min(ModAttachments.getHealingBaseConsumed(player), base);
    }

    /**
     * 总治愈点上限 = max(10, 玩家最大生命值 ÷ 2)。
     * 即 MC 中 ♥ 的数量(20 HP → 10 点);下限固定 10,避免神秘遗物+ 中佩戴七咒之戒死亡
     * 导致生命值上限丢失时治愈点数上限过低。
     */
    public static int getCap(Player player) {
        return Math.max(10, (int) player.getMaxHealth() / 2);
    }

    // ── 增加治愈点(进入"获得治愈"流程) ─────────────────────────────────────────

    /**
     * 获得治愈点(加到结算点)。若之前总点为 0(刚获得治愈)→ 立即回血并启用倒计时;
     * 若已有治愈点 → 增加点数并重置倒计时。返回增加后的总治愈点。
     */
    public static int add(Player player, int amount) {
        if (player.level().isClientSide()) return getPoints(player);
        int before = getPoints(player);
        int base = getBasePoints(player);
        int cap = getCap(player);
        // 上限作用于总点:结算点 ≤ 上限 - 有效基础点(基础点 - 已消耗)
        int maxSettle = Math.max(0, cap - (base - getBaseConsumed(player)));
        int newSettle = Math.min(getSettlePoints(player) + amount, maxSettle);
        ModAttachments.setHealingPoints(player, newSettle);
        int after = base - getBaseConsumed(player) + newSettle;
        if (before <= 0 && after > 0) {
            // 从 0 获得:立即回血(总点 × 2)并启用倒计时
            player.heal(after * 2);
            startCountdown(player);
        } else if (after > 0) {
            // 已有治愈点:重置倒计时
            startCountdown(player);
        }
        updateEffect(player);
        return after;
    }

    /**
     * 消耗治愈点(按总点数扣除):优先扣结算点(同步收缩,下限 0),
     * 不足部分消耗基础点(已消耗基础点附件记录,上限 = 当前基础点),返回实际消耗的量。
     */
    public static int spend(Player player, int amount) {
        if (player.level().isClientSide()) return 0;
        int total = getPoints(player);
        int spent = Math.min(total, amount);
        if (spent > 0) {
            // 结算点同步收缩(恒 ≥ 0)
            int fromSettle = Math.min(getSettlePoints(player), spent);
            ModAttachments.setHealingPoints(player, getSettlePoints(player) - fromSettle);
            // 超出结算点的部分消耗基础点
            int excess = spent - fromSettle;
            if (excess > 0) {
                int consumed = Math.min(getBaseConsumed(player) + excess, getBasePoints(player));
                ModAttachments.setHealingBaseConsumed(player, consumed);
            }
            updateEffect(player);
        }
        return spent;
    }

    /** 清零治愈点并移除"治愈"效果(死亡时调用;基础点由装备状态实时决定) */
    public static void clear(Player player) {
        if (player.level().isClientSide()) return;
        ModAttachments.setHealingPoints(player, 0);
        ModAttachments.setHealingCountdownEnd(player, 0);
        player.removeEffect(ModEffects.HEALING);
    }

    /**
     * 医疗箱装备/卸下时调用:基础点由装备状态实时决定,本方法处理"从无治愈变为有治愈"
     * (立即回血并启动倒计时)与"治愈点归 0"(结束效果)两种边界。
     * 卸下增加固定治愈点的筹码时,其增加的点数立即扣除:基础点实时回落使总点同步减少,
     * 已消耗基础点收缩至不超过新基础点。
     */
    public static void onBasePointsChanged(Player player) {
        if (player.level().isClientSide()) return;
        int base = getBasePoints(player);
        // 卸下筹码:已消耗基础点不能超过新基础点,超出部分立即扣除
        if (getBaseConsumed(player) > base) {
            ModAttachments.setHealingBaseConsumed(player, base);
        }
        int total = getPoints(player);
        if (total <= 0) {
            ModAttachments.setHealingCountdownEnd(player, 0);
            updateEffect(player);
            return;
        }
        long end = ModAttachments.getHealingCountdownEnd(player);
        if (end <= 0) {
            // 有治愈点但倒计时未启用:视为刚获得治愈 → 立即回血并启用
            player.heal(total * 2);
            startCountdown(player);
        }
        updateEffect(player);
    }

    // ── 倒计时与周期结算 ───────────────────────────────────────────────────────

    /** 启用/重置倒计时(从现在起一个完整周期) */
    private static void startCountdown(Player player) {
        long now = player.level().getGameTime();
        ModAttachments.setHealingCountdownEnd(player, now + GameplayConstants.HEALING_CYCLE_TICKS);
    }

    /**
     * 每 tick 驱动(由统一事件调用):
     * <ul>
     *   <li>总点 ≤ 0 → 清除倒计时并结束效果;</li>
     *   <li>有治愈点但倒计时未启用(如重连/重载/装备医疗箱后)→ 立即回血并启用;</li>
     *   <li>倒计时结束 → 回血(总点 × 2)、结算点减半(向下取整),新总点 = 基础点 + 剩余,
     *       仍 &gt; 0 则进入下一周期,否则结束。</li>
     * </ul>
     */
    public static void tick(Player player) {
        if (player.level().isClientSide()) return;
        long now = player.level().getGameTime();
        int total = getPoints(player);
        long end = ModAttachments.getHealingCountdownEnd(player);

        // 上限动态跟随玩家当前最大生命值:最大生命降低时,现有治愈点数收缩到新上限
        int base = getBasePoints(player);
        int consumed = getBaseConsumed(player);
        int cap = getCap(player);
        int maxSettle = Math.max(0, cap - (base - consumed));
        if (getSettlePoints(player) > maxSettle) {
            ModAttachments.setHealingPoints(player, maxSettle);
            total = base - consumed + maxSettle;
        }

        if (total <= 0) {
            if (end != 0) {
                ModAttachments.setHealingCountdownEnd(player, 0);
            }
            updateEffect(player);
            return;
        }

        if (end <= 0) {
            // 有治愈点但倒计时未启用(装备医疗箱/重连/重载后):视为刚获得治愈 → 立即回血并启用
            player.heal(total * 2);
            startCountdown(player);
            updateEffect(player);
            return;
        }

        if (now >= end) {
            // 周期结算:触发治愈(回血 总点×2),结算点减半(向下取整);
            // 已消耗基础点清零——"下一轮治愈触发时再次增加基础点",新总点 = 基础点 + 剩余
            player.heal(total * 2);
            int newSettle = getSettlePoints(player) / 2;
            ModAttachments.setHealingPoints(player, newSettle);
            ModAttachments.setHealingBaseConsumed(player, 0);
            int after = base + newSettle;
            if (after <= 0) {
                ModAttachments.setHealingCountdownEnd(player, 0);
            } else {
                startCountdown(player);
            }
        }
        // 刷新效果(等级 = 总点,时长 = 倒计时剩余)
        updateEffect(player);
    }

    // ── 效果显示 ───────────────────────────────────────────────────────────────

    /**
     * 刷新"治愈"效果:等级 = 当前总治愈点(层数),时长 = 距倒计时结束的剩余 tick;
     * 无治愈点时移除效果。
     */
    public static void updateEffect(Player player) {
        if (player.level().isClientSide()) return;
        int total = getPoints(player);
        if (total <= 0) {
            player.removeEffect(ModEffects.HEALING);
            return;
        }
        long end = ModAttachments.getHealingCountdownEnd(player);
        long now = player.level().getGameTime();
        int remain;
        if (end <= 0) {
            remain = GameplayConstants.HEALING_CYCLE_TICKS;
        } else {
            remain = (int) (end - now);
            if (remain <= 0) remain = 1;
        }
        // amplifier = 层数 - 1(1 层显示 I 级);visible=true 使效果在 HUD 正常显示
        player.addEffect(new MobEffectInstance(ModEffects.HEALING, remain, total - 1, false, true, true));
    }
}
