package com.merlinkitsune.astral_dice.shop;

import com.merlinkitsune.astral_dice.component.ModAttachments;
import net.minecraft.world.entity.LivingEntity;

/**
 * 筹码商店的限时折扣（少主立牌「双赢」被动）。
 *
 * <p>口径（与 1.21.1 线一致）：单价 {@code max(MIN_PRICE, 基础价 - 折扣)}；折扣**只刷新不叠加**；
 * 到期按世界时间判定。
 */
public final class ChipShopDiscounts {

    public static final int DISCOUNT = 5;
    public static final int MIN_PRICE = 5;
    public static final long DURATION_TICKS = 6000L;

    private ChipShopDiscounts() {
    }

    /** 当前生效的折扣额度（未生效返回 0）。 */
    public static int remaining(LivingEntity holder) {
        if (holder == null) return 0;
        long until = ModAttachments.getChipDiscountUntil(holder);
        if (until <= 0L || holder.level().getGameTime() >= until) return 0;
        return Math.max(0, ModAttachments.getChipDiscountAmount(holder));
    }

    /** 授予 / 刷新折扣（不叠加：同一时刻只保留一份）。 */
    public static void grant(LivingEntity holder, int amount, long durationTicks) {
        if (holder == null || amount <= 0) return;
        ModAttachments.setChipDiscountAmount(holder, amount);
        ModAttachments.setChipDiscountUntil(holder, holder.level().getGameTime() + Math.max(1L, durationTicks));
    }

    /** 把基础价按折扣折算，绝不低于 {@link #MIN_PRICE}。 */
    public static int effectivePrice(int basePrice, int discount) {
        return Math.max(MIN_PRICE, basePrice - Math.max(0, discount));
    }

    public static int effectivePrice(LivingEntity holder, int basePrice) {
        return effectivePrice(basePrice, remaining(holder));
    }
}
