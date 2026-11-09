package com.merlinkitsune.astral_dice.shop;

import com.merlinkitsune.astral_dice.compat.playerlike.PlayerLikeWallet;
import com.merlinkitsune.astral_dice.compat.playerlike.PlayerLikeWallets;
import com.merlinkitsune.astral_dice.target.PlayerLikeTargets;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 筹码商店的**无界面**服务面：给附属注册的类玩家生物（以及将来任何非 GUI 场景）提供
 * 「询价 / 购买 / 发刷新 / 发折扣」四件事。
 *
 * <p>与 {@link ChipShopMenu} 的关系：菜单仍是玩家 UI 外壳（DataSlot 显示、关店、创造模式吞栈语义），
 * 两者的**价格口径与扣款口径同源**（{@link ChipShopDiscounts#effectivePrice} + {@link PlayerLikeWallet}）。
 *
 * <p>⚠️ 购买后**不**做「重抽候选」：那是 UI 生命周期（换一批 offer 需要 {@code ChipShopCatalog.rollOffers}）。
 * 服务面只推进「购买次数 +1」，调用方需要在合适的时机调
 * {@link ChipShopState#withOffers(List)} + {@code store} 完成换批。
 */
public final class ChipShopService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChipShopService.class);

    /** 购买结果。 */
    public enum Result {
        /** 成交。 */
        OK,
        /** 该实体没有商店状态（不是玩家 / 未注册）。 */
        INVALID,
        /** 索引越界或该格无候选。 */
        NO_OFFER,
        /** 没有可用钱包。 */
        NO_WALLET,
        /** 星币不足。 */
        NO_COINS,
        /** 扣款成功但物品既进不了容器也掉不出来（几乎不发生）。 */
        DELIVERY_FAILED
    }

    /** 询价结果。 */
    public record Quote(int basePrice, int discount, int price, long balance, boolean affordable) {
    }

    private ChipShopService() {
    }

    /** 当前成交单价与余额（供 UI / 附属显示；纯读）。 */
    public static Quote quote(LivingEntity holder) {
        ChipShopState state = ChipShopState.get(holder);
        int base = state.price();
        int price = ChipShopDiscounts.effectivePrice(holder, base);
        PlayerLikeWallet wallet = PlayerLikeWallets.resolve(holder);
        long balance = wallet == null ? 0L : wallet.balance(holder);
        return new Quote(base, ChipShopDiscounts.remaining(holder), price, balance, wallet != null && wallet.isAvailable() && balance >= price);
    }

    /** 购买第 {@code offerIndex} 格候选。 */
    public static Result purchase(LivingEntity holder, int offerIndex) {
        if (holder == null || holder.level().isClientSide()) return Result.INVALID;
        ChipShopState state = ChipShopState.get(holder);
        List<ChipShopCatalog.Entry> entries = state.entries();
        if (offerIndex < 0 || offerIndex >= entries.size()) return Result.NO_OFFER;
        ChipShopCatalog.Entry entry = entries.get(offerIndex);
        PlayerLikeWallet wallet = PlayerLikeWallets.resolve(holder);
        if (wallet == null || !wallet.isAvailable()) return Result.NO_WALLET;
        int price = ChipShopDiscounts.effectivePrice(holder, state.price());
        if (!wallet.withdraw(holder, price)) return Result.NO_COINS;
        ItemStack bought = entry.stack().copy();
        boolean delivered = deliver(holder, bought);
        if (!delivered) {
            // 2026-11-09（TLMDice 汇报）：扣款与交付必须同生共死 —— 交付失败一律退款、且**不**推进购买次数。
            boolean refunded = wallet.deposit(holder, price);
            LOGGER.warn("AP_CHIP_SHOP_SERVICE: delivery_failed holder={}:index={}:price={}:refunded={}",
                    holder.getName().getString(), offerIndex, price, refunded);
            return Result.DELIVERY_FAILED;
        }
        ChipShopState.store(holder, state.withPurchased(entry.category()));
        LOGGER.info("AP_CHIP_SHOP_SERVICE: holder={}:index={}:price={}:delivered=true",
                holder.getName().getString(), offerIndex, price);
        return Result.OK;
    }

    /** 无界面「进店」：三项前置校验 + 进店补抽（不开菜单）。返回 {@code false} 表示被拒。 */
    public static boolean prepareOffers(LivingEntity holder) {
        if (holder == null || holder.level().isClientSide()) return false;
        ChipShopOpener.ensureOffers(holder);
        return true;
    }

    /** 直接授予一次刷新（受 {@code REFRESH_MAX} 封顶，与 UI 同一 store）。 */
    public static boolean grantRefresh(LivingEntity holder) {
        if (holder == null || holder.level().isClientSide()) return false;
        ChipShopState state = ChipShopState.get(holder);
        ChipShopState next = state.withRefreshGranted();
        if (next == state) return false;
        ChipShopState.store(holder, next);
        return true;
    }

    /** 授予限时折扣（只刷新数值/到期时间，不叠加 - 语义见 {@link ChipShopDiscounts}）。 */
    public static void grantDiscount(LivingEntity holder, int amount, long durationTicks) {
        if (holder == null || holder.level().isClientSide()) return;
        ChipShopDiscounts.grant(holder, amount, durationTicks);
    }

    /** 交付：玩家走物品栏（满则掉地）；注册生物先问 handler，再兜底掉地。 */
    private static boolean deliver(LivingEntity holder, ItemStack stack) {
        if (stack.isEmpty()) return true;
        if (holder instanceof Player player) {
            if (player.getInventory().add(stack)) return true;
            return player.drop(stack, false) != null;
        }
        if (PlayerLikeTargets.giveItem(holder, stack)) return true;
        return holder.spawnAtLocation(stack) != null;
    }
}
