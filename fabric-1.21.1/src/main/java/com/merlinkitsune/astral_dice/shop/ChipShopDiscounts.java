package com.merlinkitsune.astral_dice.shop;

import com.merlinkitsune.astral_dice.component.ModAttachments;
import net.minecraft.world.entity.LivingEntity;

/**
 * 筹码商店「限时折扣」（2026-11-08 少主立牌「强者怜悯」B 面）。
 *
 * <p>语义：一次授予 = 单价 {@link #DISCOUNT} 的减免，有效期 {@link #DURATION_TICKS}；多次授予取
 * 「更大额度 + 更晚到期」合并（不叠加、不延长到两倍），由
 * {@link ModAttachments#CHIP_DISCOUNT_AMOUNT} / {@link ModAttachments#CHIP_DISCOUNT_UNTIL} 两键承载。
 *
 * <p>⚠️ 折扣**刻意不进** {@link ChipShopState}（后者是玩家存档运行态，改字段会牵动存档迁移）；
 * 成交价在 {@code ChipShopMenu} 的 DataSlot / {@code price()} / {@code handleBuy} 三处同源现算。
 *
 * <p>宿主是 {@link LivingEntity} 而非 {@link net.minecraft.world.entity.player.Player}：
 * 类玩家生物（附属模组注册）同样持有折扣（{@code PlayerLikeTargets} 口径）。
 */
public final class ChipShopDiscounts {

    /** 每次授予的单价减免（筹码）。 */
    public static final int DISCOUNT = 5;
    /** 折后单价下限。 */
    public static final int MIN_PRICE = 5;
    /** 折扣有效期（tick）：5 分钟。 */
    public static final long DURATION_TICKS = 6000L;

    private ChipShopDiscounts() {
    }

    /** 当前**未被折扣净价**的折扣额度；过期或无折扣返回 0。 */
    public static int remaining(LivingEntity living) {
        if (living == null || living.level() == null) return 0;
        long until = ModAttachments.getChipDiscountUntil(living);
        if (until <= 0L) return 0;
        if (living.level().getGameTime() >= until) return 0;
        return Math.max(0, ModAttachments.getChipDiscountAmount(living));
    }

    /** 授予（或续授）一次折扣：额度取大、到期取晚。 */
    public static void grant(LivingEntity living, int amount, long durationTicks) {
        if (living == null || living.level() == null) return;
        if (amount <= 0 || durationTicks <= 0) return;
        int best = Math.max(remaining(living), amount);
        ModAttachments.setChipDiscountAmount(living, best);
        ModAttachments.setChipDiscountUntil(living, living.level().getGameTime() + durationTicks);
    }

    /** 折后价（纯函数，便于两端同源核算）。 */
    public static int effectivePrice(int basePrice, int discount) {
        return Math.max(MIN_PRICE, basePrice - Math.max(0, discount));
    }

    /** 折后价（读取实体当前折扣）。 */
    public static int effectivePrice(LivingEntity living, int basePrice) {
        return effectivePrice(basePrice, remaining(living));
    }
}
