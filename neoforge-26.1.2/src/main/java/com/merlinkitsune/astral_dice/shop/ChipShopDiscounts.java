package com.merlinkitsune.astral_dice.shop;

import com.merlinkitsune.astral_dice.component.ModAttachments;
import net.minecraft.world.entity.LivingEntity;

/**
 * 筹码商店的**限时单价折扣**（少主立牌「双赢」被动）。
 *
 * <p>口径：对目标使用主动技能后，**双方各自**获得一次「筹码单价 -5 星币（最低 5）」与「一次刷新次数」。
 * 折扣是短时段 buff，存在 attachment 上（{@link ModAttachments#CHIP_DISCOUNT_AMOUNT} /
 * {@link ModAttachments#CHIP_DISCOUNT_UNTIL}），**不进** {@link ChipShopState} —— 后者是玩家存档的
 * 商店运行态，且折扣需要能挂在附属注册的类玩家生物身上（{@code PlayerLikeTargets}）。
 *
 * <p>叠加语义：**只刷新、不叠加** —— 已有折扣时取两者面额的较大值，并把截止时刻刷新为
 * {@code now + DURATION_TICKS}；折扣期间再次获得不会变成 -10。
 *
 * <p>⚠️ 报价与服务端扣款必须走**同一个函数**（{@link #effectivePrice}），否则会出现
 * 「界面显示 5、实际扣 10」这类不可回滚的错账。客户端只读菜单 DataSlot 同步下来的价格。
 */
public final class ChipShopDiscounts {

    /** 每次授予的折扣面额（星币）。 */
    public static final int DISCOUNT = 5;

    /** 折扣后的单价下限。 */
    public static final int MIN_PRICE = 5;

    /** 折扣持续时长 = 5:00（6000 tick）。 */
    public static final long DURATION_TICKS = 6000L;

    private ChipShopDiscounts() {
    }

    /** 当前生效的折扣面额（0 = 无折扣；过期即视为 0，不主动清附件）。 */
    public static int remaining(LivingEntity living) {
        if (living == null || living.level() == null) return 0;
        long until = ModAttachments.getChipDiscountUntil(living);
        if (until <= 0L) return 0;
        if (living.level().getGameTime() >= until) return 0;
        return Math.max(0, ModAttachments.getChipDiscountAmount(living));
    }

    /** 授予/刷新折扣（只刷新不叠加）。 */
    public static void grant(LivingEntity living, int amount, long durationTicks) {
        if (living == null || living.level() == null) return;
        if (amount <= 0 || durationTicks <= 0L) return;
        int best = Math.max(remaining(living), amount);
        ModAttachments.setChipDiscountAmount(living, best);
        ModAttachments.setChipDiscountUntil(living, living.level().getGameTime() + durationTicks);
    }

    /** 按面额折算单价：{@code max(MIN_PRICE, base - discount)}。 */
    public static int effectivePrice(int basePrice, int discount) {
        return Math.max(MIN_PRICE, basePrice - Math.max(0, discount));
    }

    /** 按持有者当前折扣折算单价。 */
    public static int effectivePrice(LivingEntity living, int basePrice) {
        return effectivePrice(basePrice, remaining(living));
    }
}
