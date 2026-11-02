package com.merlinkitsune.astral_dice.trade;

import net.minecraft.world.item.Item;

/**
 * 一条**静态**村民报价：{@code emeraldPrice} 颗绿宝石 → 1 个 {@code item}。
 *
 * <p>26.1.2 的交易是纯数据驱动的（{@code data/<ns>/villager_trade/…}），价格在数据包里是固定值，
 * 没法像 1.21.1 的 {@code VillagerTrades.ItemListing} 那样在运行时按卡面点数算价。因此
 * {@code CardTraderTrades} / {@code ChipTraderTrades} 把「等级 × 商品 × 定价」摊平成这个 record，
 * 由 {@code datagen/ModVillagerTradeProvider} 投影成 JSON —— 定价逻辑仍只有一份，数据只是它的产物。
 *
 * @param level              村民等级（1-5）
 * @param item               村民给出的物品
 * @param emeraldPrice       村民想要的绿宝石数量（兑换条目里恒为 1）
 * @param maxUses            补货前最多成交次数
 * @param xp                 每次成交给村民的经验
 * @param reputationDiscount 原版的声望折扣系数（0.05 = 每个声望点 5%）
 */
public record VillagerTradeEntry(int level, Item item, int emeraldPrice, int maxUses, int xp,
        float reputationDiscount) {
}
