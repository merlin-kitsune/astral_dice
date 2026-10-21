package com.merlinkitsune.astral_dice.item.chip;

import com.merlinkitsune.astral_dice.item.CurioSlotUtil;
import net.minecraft.world.entity.player.Player;

/**
 * 额外电池筹码（史诗 / 无流派）。
 *
 * <p><b>装备时：立牌主动技能冷却时间 -50%</b>（用户需求 2026-10-20）。
 *
 * <p><b>作用域硬约束（2026-10-21 用户裁决）</b>：本筹码的 -50% <b>只允许</b>作用于
 * 「立牌主动技能冷却」这一个量。唯一入口 = {@link #applySignActiveCooldownReduction(Player, long)}，
 * 唯一调用方 = {@link com.merlinkitsune.astral_dice.item.ChargeManager#signCooldownTicks(Player, long)}
 * —— 本仓所有「立牌主动开始冷却」的入口最终都取这条（{@code WeirdDiceHandler#signCooldownTicks} 与
 * 各立牌覆写的 {@code activeCooldownBaseTicks} 全部经过它），因此在**取值处**折算一次即可覆盖全部立牌。
 *
 * <p><b>禁止</b>把本方法接到下列任何冷却上（这些冷却必须保持字面原值，一字不变）：
 * <ul>
 *   <li>抽奖盒：{@code EventDrawBlock#COOLDOWN_TICKS} / {@code event_drawer_cooldown_until}；</li>
 *   <li>筹码商店：{@code ChipShopState#cooldownUntil}、{@code ChipShopMenu#BUY_COOLDOWN_TICKS}、
 *       {@code ChipShopBlock#BLANK_INSERT_COOLDOWN_TICKS}；</li>
 *   <li>卡牌商店转账冷却，以及任何其它 {@code *CooldownUntil} / {@code *CooldownEnd} / 效果牌冷却。</li>
 * </ul>
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
     * 把**立牌主动技能冷却**按 {@link #COOLDOWN_MULTIPLIER} 折算。
     *
     * <p><b>只允许</b>被「立牌主动冷却取值路径」调用 —— 即
     * {@link com.merlinkitsune.astral_dice.item.ChargeManager#signCooldownTicks(Player, long)}
     * 及其上游（{@code WeirdDiceHandler#signCooldownTicks}、各立牌 {@code activeCooldownBaseTicks}）。
     * <b>禁止</b>用于抽奖盒、筹码商店、效果牌，或任何其它 {@code *CooldownUntil} /
     * {@code *CooldownEnd} —— 那些冷却的写入路径必须保持字面常量，不经过本方法。
     *
     * <p>{@code ticks <= 1} 原样返回（与 {@code ChargeManager#capByCharge} 同一「无冷却 / 1t 哨兵」
     * 口径，避免把 0 或 1 折成 0 后被误读成「无冷却」）。
     */
    public static long applySignActiveCooldownReduction(Player player, long ticks) {
        if (ticks <= 1L) return ticks;
        if (!isEquipped(player)) return ticks;
        return Math.max(1L, (long) (ticks * COOLDOWN_MULTIPLIER));
    }
}
