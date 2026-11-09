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
 * 筹码商店的**无界面**服务面（1.20.1 移植）：询价 / 购买 / 发刷新 / 发折扣。
 *
 * <p>与 {@link ChipShopMenu} 的关系同 1.21.1：菜单仍是玩家 UI 外壳，价格与扣款口径同源
 * （{@link ChipShopDiscounts} + {@link PlayerLikeWallet}）。购买后不重抽候选。
 */
public final class ChipShopService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChipShopService.class);

    public enum Result { OK, INVALID, NO_OFFER, NO_WALLET, NO_COINS, DELIVERY_FAILED }

    public record Quote(int basePrice, int discount, int price, long balance, boolean affordable) {
    }

    private ChipShopService() {
    }

    public static Quote quote(LivingEntity holder) {
        ChipShopState state = ChipShopState.get(holder);
        int base = state.price();
        int price = ChipShopDiscounts.effectivePrice(holder, base);
        PlayerLikeWallet wallet = PlayerLikeWallets.resolve(holder);
        long balance = wallet == null ? 0L : wallet.balance(holder);
        return new Quote(base, ChipShopDiscounts.remaining(holder), price, balance,
                wallet != null && wallet.isAvailable() && balance >= price);
    }

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
            boolean refunded = wallet.deposit(holder, price);
            LOGGER.info("AP_CHIP_SHOP_SERVICE: delivery_failed holder={}:index={}:price={}:refunded={}",
                    holder.getName().getString(), offerIndex, price, refunded);
            return Result.DELIVERY_FAILED;
        }
        ChipShopState.store(holder, state.withPurchased(entry.category()));
        LOGGER.info("AP_CHIP_SHOP_SERVICE: holder={}:index={}:price={}:delivered={}",
                holder.getName().getString(), offerIndex, price, true);
        return Result.OK;
    }

    public static boolean grantRefresh(LivingEntity holder) {
        if (holder == null || holder.level().isClientSide()) return false;
        ChipShopState state = ChipShopState.get(holder);
        ChipShopState next = state.withRefreshGranted();
        if (next == state) return false;
        ChipShopState.store(holder, next);
        return true;
    }

    /**
     * 只为「候选播种」的服务面入口：注册生物没有界面，但也需要一批候选才能买。
     *
     * <p>等价于玩家进店时的免费补抽（{@code ChipShopOpener#ensureOffers}），幂等。
     */
    public static boolean prepareOffers(LivingEntity holder) {
        if (holder == null || holder.level().isClientSide()) return false;
        ChipShopOpener.ensureOffers(holder);
        return !ChipShopState.get(holder).entries().isEmpty();
    }

    public static void grantDiscount(LivingEntity holder, int amount, long durationTicks) {
        if (holder == null || holder.level().isClientSide()) return;
        ChipShopDiscounts.grant(holder, amount, durationTicks);
    }

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
