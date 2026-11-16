package com.merlinkitsune.astral_dice.trade;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * 读取 {@link MerchantOffer} **原始费用 A** 的软接口（duck interface）。
 *
 * <p>为什么需要它：1.21.1 的 {@code MerchantOffer#getBaseCostA()} 返回未被改写的原始费用
 * （费用替换只作用于 getItemCostA/getCostA/satisfiedBy），因此 1.21.1 的
 * {@link EmeraldDiceTrade#isEmeraldToStarCoin(MerchantOffer)} 直接读它即可；但 **1.20.1 没有 ItemCost，
 * 费用替换只能覆写 {@code getBaseCostA()} 本身**（见 {@code mixin/trade/MerchantOfferMixin}），
 * 于是「互换上下文激活时」getBaseCostA 会返回星币 —— 兑换条目就再也认不出来了。
 *
 * <p>本接口由 {@code MerchantOfferMixin} 实现（Mixin 的接口注入会把本接口加到
 * {@code MerchantOffer} 上），调用方用 {@code offer instanceof MerchantOfferRawAccess} 取用；
 * {@link EmeraldDiceTrade#rawCostA(MerchantOffer)} 另有一条 NBT 快照兜底路径，因此即使软接口
 * 未生效也不会静默降级成错误判据。
 */
public interface MerchantOfferRawAccess {

    /** 返回报价的原始费用 A（未经任何取值覆写）。 */
    ItemStack astralDice$rawCostA();
}
