package com.merlinkitsune.astral_dice.trade;

import com.merlinkitsune.astral_dice.compat.curios.CuriosApi;
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

/**
 * 绿宝石骰子交易能力:佩戴绿宝石骰子时,村民交易中的绿宝石费用按 1:1 改为星币支付(等值替换)。
 *
 * <p>实现方式(1.21.1):在村民交易的关键路径(发送报价 / 报价匹配 / 自动放入 / 成交)上,用
 * {@link #CONTEXT} 线程局部变量携带当前交易玩家;{@code MerchantOfferMixin} 在上下文中把绿宝石
 * {@link ItemCost} 替换为星币 {@link ItemCost}。
 *
 * <p>1.21.1 与 1.20.1 的差别:报价费用从 {@code ItemStack} 改成了 {@link ItemCost}(record,含
 * {@code item() / count() / components()}),且 {@code MerchantOffer} 的 NBT 读写({@code createTag()} /
 * {@code new MerchantOffer(CompoundTag)})随 1.20.5 的 ItemStack 组件化一并删除 —— 报价只走 Codec。
 * 因此:取值路径靠 mixin 覆写 {@code getItemCostA / getItemCostB / getCostA / getCostB / satisfiedBy},
 * 发给客户端的载荷靠 {@code MerchantOffer#copy()} 的复制构造注入({@link #transformOffers(MerchantOffers)})。
 */
public final class EmeraldDiceTrade {

    private static final ThreadLocal<Player> CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<Integer> DEPTH = new ThreadLocal<>();
    /**
     * 一次性闸门:让 {@link #transformOffers(MerchantOffers)} 主动取的那份副本也能被复制构造注入改写
     * (语义见 {@link #shouldTransformCopy()})。
     */
    private static final ThreadLocal<Boolean> FORCE_COPY_TRANSFORM = new ThreadLocal<>();

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

    /**
     * 复制构造注入的判定口径:交换上下文活跃({@link #isSwapActive()}),**或**
     * {@link #transformOffers(MerchantOffers)} 正在主动取副本。
     *
     * <p>为什么需要第二个条件:fabric 线的 {@code mixin/trade/ServerPlayerMixin} 在
     * {@code sendMerchantOffers} 的 HEAD 注入里自己构造数据包(见该文件注释),它**不**处在
     * {@link #begin(Player)} / {@link #end()} 窗口内,但又要复用本方法拿到「费用已替换的副本」。
     * 取副本期间临时打开闸门,语义仍是「这份副本按当前交易玩家换价」,村民本体报价不受影响。
     */
    public static boolean shouldTransformCopy() {
        return isSwapActive() || Boolean.TRUE.equals(FORCE_COPY_TRANSFORM.get());
    }

    public static boolean hasEmeraldDice(Player player) {
        if (player == null || player.level().isClientSide()) return false;
        return CuriosApi.getCuriosInventory(player)
                .map(inv -> inv.findFirstCurio(s -> s.is(ModItems.EMERALD_DICE.get())).isPresent())
                .orElse(false);
    }

    public static boolean isEmerald(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(Items.EMERALD);
    }

    /** 该费用项是否是绿宝石(1.21.1 的费用项已从 {@code ItemStack} 改为 {@link ItemCost})。 */
    public static boolean isEmerald(ItemCost cost) {
        return cost != null && cost.item() != null && cost.item().value() == Items.EMERALD;
    }

    /**
     * 绿宝石费用 → 星币费用(1:1),数量保持不变(至少 1)。非绿宝石费用原样返回。
     *
     * <p>对齐 forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/trade/EmeraldDiceTrade.java:67
     * (2026-10-29 用户裁决:0.8 折扣 → 1:1)。
     *
     * <p>⚠️ 1.21.1 的 {@link ItemCost} 除 item/count 外还带 {@code components()}(组件谓词);
     * 换成星币后原有的组件谓词不再适用,故只保留 item/count。
     */
    public static ItemCost transform(ItemCost cost) {
        if (!isEmerald(cost)) return cost;
        return new ItemCost(ModItems.STAR_COIN.get(), Math.max(1, cost.count()));
    }

    /**
     * 为「发送给客户端的报价」构造一份费用已替换的副本(星币费用,1:1)。
     *
     * <p>为什么必须改数据而不是只改取值:1.21.1 的 {@code ClientboundMerchantOffersPacket} 构造时会
     * **同步**执行 {@code offers.copy()}(javap:构造器里 {@code invokevirtual MerchantOffers.copy()})。
     * 而真正的网络编码被投递到 Netty 事件循环后才执行(见 {@code Connection.sendPacket} 的
     * {@code eventLoop().execute(...)} 分支),那时交换上下文已经关闭 —— 基于 {@link #isSwapActive()}
     * 的 {@code getItemCostA()} 覆写影响不到客户端载荷,客户端会一直显示绿宝石费用。
     *
     * <p>1.20.1 用 NBT 往返({@code createTag()} / {@code new MerchantOffer(tag)})复制报价,该 API 在
     * 1.21.1 已删;这里改为 {@code MerchantOffer#copy()},把「副本费用换成星币」交给
     * {@code MerchantOfferMixin} 的复制构造注入完成 —— 调用期间打开 {@link #FORCE_COPY_TRANSFORM},
     * 注入的条件 {@link #shouldTransformCopy()} 才会对这次拷贝成立。村民本体报价不受影响。
     *
     * @return 费用已替换的副本;若报价表里没有任何绿宝石费用则返回 {@code null}
     */
    public static MerchantOffers transformOffers(MerchantOffers offers) {
        if (offers == null || offers.isEmpty()) return null;
        MerchantOffers copy = new MerchantOffers();
        boolean changed = false;
        for (MerchantOffer offer : offers) {
            if (!containsEmeraldCost(offer)) {
                // 无绿宝石费用的报价保持原实例(共享 uses/demand 状态,零分配)
                copy.add(offer);
                continue;
            }
            MerchantOffer cloned;
            FORCE_COPY_TRANSFORM.set(Boolean.TRUE);
            try {
                cloned = offer.copy();
            } finally {
                FORCE_COPY_TRANSFORM.remove();
            }
            copy.add(cloned);
            changed = true;
        }
        return changed ? copy : null;
    }

    /**
     * 这一笔报价的两项费用里是否有绿宝石。
     *
     * <p>{@code getBaseCostA()} 在 1.21.1 **未被 mixin 覆写**(只换 getItemCostA/getCostA/satisfiedBy),
     * 读到的就是原始费用 A。费用 B 在交换上下文活跃时可能已经是星币——那笔报价本来就会被换价,
     * 这里判否只是跳过额外拷贝,不改变结果。
     */
    private static boolean containsEmeraldCost(MerchantOffer offer) {
        return isEmerald(offer.getBaseCostA()) || isEmerald(offer.getCostB());
    }

    /**
     * 这一笔报价是否是「绿宝石 → 星币」兑换。
     *
     * <p>1.21.1 读 {@code offer.getBaseCostA()}:它**未被 mixin 改写**,本身就是原始费用 A;
     * 1.20.1 之所以要绕到 {@link #rawCostA(MerchantOffer)} 的软接口,是因为那边连 getBaseCostA
     * 都被覆写了。软接口保留仅为兼容既有调用点(见该方法注释)。
     */
    public static boolean isEmeraldToStarCoin(MerchantOffer offer) {
        if (offer == null) return false;
        if (!offer.getResult().is(ModItems.STAR_COIN.get())) return false;
        ItemStack cost = rawCostA(offer);
        return !cost.isEmpty() && cost.is(Items.EMERALD);
    }

    /**
     * 取报价的原始费用 A(不受 {@code MerchantOfferMixin} 的取值覆写影响)。
     *
     * <p>优先走软接口 {@link MerchantOfferRawAccess}(由 MerchantOfferMixin 注入到 MerchantOffer,零分配);
     * 软接口不可用(例如 mixin 被门禁关闭)时退回 {@link MerchantOffer#getBaseCostA()} —— 1.21.1
     * 该方法本就未被覆写,两条路径拿到同一个值(1.20.1 那条 NBT 快照兜底已随 {@code createTag()} 删除)。
     */
    public static ItemStack rawCostA(MerchantOffer offer) {
        if (offer == null) return ItemStack.EMPTY;
        if (offer instanceof MerchantOfferRawAccess raw) {
            return raw.astralDice$rawCostA();
        }
        return offer.getBaseCostA();
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
