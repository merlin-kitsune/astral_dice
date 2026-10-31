package com.merlinkitsune.astral_dice.trade;

import com.merlinkitsune.starengine.item.CuriosCompat;
import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/**
 * 绿宝石骰子交易能力(1.20.1):佩戴绿宝石骰子时,村民交易中的绿宝石费用按 1:1 改为星币支付(等值替换)。
 *
 * <p>实现方式:在村民交易的关键路径(发送报价 / 报价匹配 / 自动放入 / 成交)上,用 {@link #CONTEXT}
 * 线程局部变量携带当前交易玩家;{@code MerchantOfferMixin} 在上下文中把绿宝石费用替换为星币(1:1)。
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
        return CuriosCompat.getCuriosInventory(player)
                .map(inv -> inv.findFirstCurio(s -> s.is(ModItems.EMERALD_DICE.get())).isPresent())
                .orElse(false);
    }

    public static boolean isEmerald(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(Items.EMERALD);
    }

    /** 绿宝石费用 → 星币费用(1:1),数量保持不变(至少 1)。非绿宝石费用原样返回。 */
    public static ItemStack transform(ItemStack stack) {
        if (!isEmerald(stack)) return stack;
        return new ItemStack(ModItems.STAR_COIN.get(), Math.max(1, stack.getCount()));
    }

    /**
     * 为「发送给客户端的报价」构造一份费用已替换的副本(星币费用,1:1)。
     *
     * <p>为什么必须改数据而不是只改取值:1.20.1 的数据包({@code ClientboundMerchantOffersPacket})
     * 直接持有村民本体的报价列表,而网络编码被投递到 Netty 事件循环后才执行(见 {@code Connection.sendPacket}
     * 的 {@code eventLoop().execute(...)} 分支),那时交换上下文已经关闭 —— 基于 {@link #isSwapActive()}
     * 的 {@code getBaseCostA()} 覆写影响不到客户端载荷,客户端会一直显示绿宝石费用。
     * 本方法经 NBT 往返复制报价(保留 uses/maxUses/rewardExp/xp/priceMultiplier/specialPrice/demand),
     * 并把绿宝石费用替换为星币;村民本体报价不受影响。
     *
     * @return 费用已替换的副本;若报价表里没有任何绿宝石费用则返回 {@code null}
     */
    public static MerchantOffers transformOffers(MerchantOffers offers) {
        if (offers == null || offers.isEmpty()) return null;
        MerchantOffers copy = new MerchantOffers();
        boolean changed = false;
        for (MerchantOffer offer : offers) {
            CompoundTag tag = offer.createTag();
            boolean offerChanged = false;
            ItemStack costA = offer.getBaseCostA();
            if (isEmerald(costA)) {
                tag.put("buy", transform(costA).save(new CompoundTag()));
                offerChanged = true;
            }
            ItemStack costB = offer.getCostB();
            if (isEmerald(costB)) {
                tag.put("buyB", transform(costB).save(new CompoundTag()));
                offerChanged = true;
            }
            if (offerChanged) {
                copy.add(new MerchantOffer(tag));
                changed = true;
            } else {
                copy.add(offer);
            }
        }
        return changed ? copy : null;
    }

    /**
     * 这一笔报价是否是「绿宝石 → 星币」兑换。
     *
     * <p>1.21.1 用 {@code offer.getBaseCostA()}（那边它**未被 mixin 改写**，只有
     * getItemCostA/getCostA/satisfiedBy 被换掉）；1.20.1 的费用替换只能覆写 getBaseCostA 本身，
     * 因此这里改读**原始字段**（见 {@link #rawCostA(MerchantOffer)}）。
     */
    public static boolean isEmeraldToStarCoin(MerchantOffer offer) {
        if (offer == null) return false;
        if (!offer.getResult().is(ModItems.STAR_COIN.get())) return false;
        ItemStack cost = rawCostA(offer);
        return !cost.isEmpty() && cost.is(Items.EMERALD);
    }

    /**
     * 取报价的原始费用 A（不受 {@code MerchantOfferMixin} 的取值覆写影响）。
     *
     * <p>优先走软接口 {@link MerchantOfferRawAccess}（由 MerchantOfferMixin 注入到 MerchantOffer，零分配）；
     * 软接口不可用（例如 mixin 被门禁关闭）时退回 NBT 快照 —— 1.20.1 的 createTag() 直接读原字段
     * （MerchantOffer.java:171 compoundtag.put("buy", this.baseCostA.save(new CompoundTag()));），
     * 因此兜底路径同样拿得到原值，不会静默退化成错误判据。
     */
    public static ItemStack rawCostA(MerchantOffer offer) {
        if (offer == null) return ItemStack.EMPTY;
        if (offer instanceof MerchantOfferRawAccess raw) {
            return raw.astralDice$rawCostA();
        }
        return ItemStack.of(offer.createTag().getCompound("buy"));
    }

    /**
     * 按交易玩家过滤报价列表：佩戴绿宝石骰子时把「绿宝石 → 星币」兑换条目剔除。
     *
     * <p>为什么必须过滤：报价表是**村民级共享**的，而 MerchantOfferMixin 会把该玩家看到的绿宝石费用
     * 换成星币(1:1) —— 兑换条目留着就退化成「用星币买星币」的空循环，仍应剔除。
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
