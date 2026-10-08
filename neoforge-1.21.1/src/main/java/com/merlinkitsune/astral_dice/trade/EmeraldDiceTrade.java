package com.merlinkitsune.astral_dice.trade;

import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * 绿宝石骰子交易能力:佩戴绿宝石骰子时,村民交易中的绿宝石费用改为星币支付,并享受 20% 折扣。
 *
 * <p>实现方式:在村民交易的关键路径(发送报价 / 报价匹配 / 自动放入 / 成交)上,用 {@link #CONTEXT}
 * 线程局部变量携带当前交易玩家;{@code MerchantOfferMixin} 在上下文中把绿宝石费用替换为星币并打折。
 */
public final class EmeraldDiceTrade {

    private static final ThreadLocal<Player> CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<Integer> DEPTH = new ThreadLocal<>();

    private EmeraldDiceTrade() {
    }

    public static void begin(Player player) {
        if (!hasEmeraldDice(player)) return;
        if (CONTEXT.get() == null) {
            CONTEXT.set(player);
            DEPTH.set(0);
        }
        DEPTH.set(DEPTH.get() + 1);
    }

    public static void end() {
        if (CONTEXT.get() == null) return;
        int depth = DEPTH.get() - 1;
        if (depth <= 0) {
            CONTEXT.remove();
            DEPTH.remove();
        } else {
            DEPTH.set(depth);
        }
    }

    public static boolean isSwapActive() {
        return CONTEXT.get() != null;
    }

    public static boolean hasEmeraldDice(Player player) {
        if (player == null || player.level().isClientSide()) return false;
        return CuriosApi.getCuriosInventory(player)
                .map(inv -> inv.findFirstCurio(s -> s.is(ModItems.EMERALD_DICE.get())).isPresent())
                .orElse(false);
    }

    public static boolean isEmerald(ItemCost cost) {
        return cost != null && cost.item() != null && cost.item().value() == Items.EMERALD;
    }

    /** 绿宝石费用 → 星币费用(1:1),数量按 20% 折扣(至少 1)。非绿宝石费用原样返回。 */
    public static ItemCost transform(ItemCost cost) {
        if (!isEmerald(cost)) return cost;
        return new ItemCost(ModItems.STAR_COIN.get(), discount(cost.count()));
    }

    /** 20% 折扣(向下取整,至少 1)。 */
    public static int discount(int count) {
        return Math.max(1, (int) Math.floor(count * 0.8));
    }

    /**
     * 这一笔报价是否是「绿宝石 → 星币」兑换。
     *
     * <p>判据用 {@link MerchantOffer#getBaseCostA()}（**未被本包的 mixin 改写**的原始费用）：
     * getItemCostA/getCostA/satisfiedBy 都会被 MerchantOfferMixin 换成星币，唯有 getBaseCostA 保持原值。
     */
    public static boolean isEmeraldToStarCoin(MerchantOffer offer) {
        if (offer == null) return false;
        ItemStack cost = offer.getBaseCostA();
        if (cost.isEmpty() || !cost.is(Items.EMERALD)) return false;
        return offer.getResult().is(ModItems.STAR_COIN.get());
    }

    /**
     * 按交易玩家过滤报价列表：佩戴绿宝石骰子时把「绿宝石 → 星币」兑换条目剔除。
     *
     * <p>为什么必须过滤：报价表是**村民级共享**的，而 MerchantOfferMixin 会把该玩家看到的绿宝石费用
     * 换成星币并打折 —— 兑换条目留着就变成「用星币买星币」，可以凭空刷币。
     *
     * <p>返回值语义：null = 不需要过滤（调用方保留原列表，零行为差异）；否则返回一个**只包住原报价实例**
     * 的新列表（禁止深拷贝 —— 拷贝会脱离 maxUses/demand 的共享状态；深拷贝只由发包时的
     * ClientboundMerchantOffersPacket 做）。
     */
    public static MerchantOffers filteredFor(Player player, MerchantOffers offers) {
        if (!hasEmeraldDice(player) || offers == null || offers.isEmpty()) return null;
        boolean hit = false;
        for (MerchantOffer offer : offers) {
            if (isEmeraldToStarCoin(offer)) {
                hit = true;
                break;
            }
        }
        if (!hit) return null;
        MerchantOffers filtered = new MerchantOffers();
        for (MerchantOffer offer : offers) {
            if (!isEmeraldToStarCoin(offer)) filtered.add(offer);
        }
        return filtered;
    }

    /** 以村民当前交易玩家为口径的过滤（调用方不必自己取 player）。 */
    public static MerchantOffers filteredFor(Merchant merchant) {
        if (merchant == null) return null;
        return filteredFor(merchant.getTradingPlayer(), merchant.getOffers());
    }

    /**
     * 成交后立即把报价重发给当前交易玩家,使客户端的「职业经验 / 职业等级 / 报价列表」即时刷新。
     *
     * <p>原版只在开界面、补货与升级时重发报价({@code Villager.resendOffersToTradingPlayer}),
     * 普通成交不会重发 —— 客户端那条经验条读的是数据包里的 {@code villagerXp}(见
     * {@code ClientPacketListener.handleMerchantOffers} → {@code MerchantMenu.setXp}),
     * 因此成交后不重发就一直是旧值。佩戴绿宝石骰子交易时补一次重发。
     */
    public static void resendOffers(ServerPlayer player, Merchant merchant) {
        if (player == null || merchant == null) return;
        if (!hasEmeraldDice(player)) return;
        MerchantOffers offers = merchant.getOffers();
        if (offers == null || offers.isEmpty()) return;
        int level = merchant instanceof Villager villager ? villager.getVillagerData().getLevel() : 1;
        player.sendMerchantOffers(player.containerMenu.containerId, offers, level,
                merchant.getVillagerXp(), merchant.showProgressBar(), merchant.canRestock());
    }
}
