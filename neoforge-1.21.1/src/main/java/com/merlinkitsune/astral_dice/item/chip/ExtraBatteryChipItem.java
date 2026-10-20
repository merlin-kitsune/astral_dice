package com.merlinkitsune.astral_dice.item.chip;

import com.merlinkitsune.astral_dice.item.CurioSlotUtil;
import net.minecraft.world.entity.player.Player;

/**
 * 额外电池筹码（史诗 / 无流派）。
 *
 * <p><b>装备时：立牌主动技能冷却时间 -50%</b>（用户需求 2026-10-20）。
 *
 * <p>挂点 = {@link com.merlinkitsune.astral_dice.item.ChargeManager#signCooldownTicks(Player, long)}
 * —— 本仓所有「立牌主动开始冷却」的入口最终都取这条（{@code WeirdDiceHandler#signCooldownTicks} 与
 * 各立牌覆写的 {@code activeCooldownBaseTicks} 全部经过它），因此在**取值处**折算一次即可覆盖
 * 全部立牌，且与诡异骰子的 -50% 正交叠加（各自折算一次）。
 *
 * <p><b>不需要「卸下时还原」</b>：本加成是**纯查询**（{@link #isEquipped} 现算），不写任何
 * attachment、也不改写已入账的 {@code sign_active_max_cooldown} /
 * {@code sign_active_cooldown_end} ⇒ 卸下后下一次开冷却自然回到原值；已在跑的倒计时按本仓既有
 * 口径（「已在进行的倒计时不受影响，只在开始冷却时取值」）继续跑完。
 */
public class ExtraBatteryChipItem extends BaseChipItem {

    /** 冷却倍率：-50% ⇒ ×0.5。 */
    public static final double COOLDOWN_MULTIPLIER = 0.5D;

    public ExtraBatteryChipItem(Properties properties) {
        super(properties);
    }

    /**
     * 是否**功能生效**地佩戴了本筹码：{@code chip} 槽装着本筹码，且骰子槽仍有骰子
     * （与 {@code BookmarkChipItem#isEquipped} 同一「未佩戴骰子 ⇒ 本件功能一律不生效」口径）。
     */
    public static boolean isEquipped(Player player) {
        if (player == null) return false;
        if (!CurioSlotUtil.hasDiceEquipped(player)) return false;
        return CurioSlotUtil.hasAnyCurioMatching(player,
                stack -> stack.getItem() instanceof ExtraBatteryChipItem);
    }

    /**
     * 把立牌主动冷却按 {@link #COOLDOWN_MULTIPLIER} 折算。
     *
     * <p>{@code ticks <= 1} 原样返回（与 {@code ChargeManager#capByCharge} 同一「无冷却 / 1t 哨兵」
     * 口径，避免把 0 或 1 折成 0 后被误读成「无冷却」）。
     */
    public static long applyCooldownReduction(Player player, long ticks) {
        if (ticks <= 1L) return ticks;
        if (!isEquipped(player)) return ticks;
        return Math.max(1L, (long) (ticks * COOLDOWN_MULTIPLIER));
    }
}
