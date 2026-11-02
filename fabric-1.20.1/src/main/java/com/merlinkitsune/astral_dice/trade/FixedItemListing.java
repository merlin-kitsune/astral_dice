package com.merlinkitsune.astral_dice.trade;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * 「固定报价」的 {@link VillagerTrades.ItemListing} 实现 —— Forge 侧
 * {@code BasicItemListing}（5 参构造）在 Fabric 上的等价物。
 *
 * <p><b>为什么必须自写</b>：Forge 的 {@code BasicItemListing} 属加载器 API，Fabric 没有；
 * 而原版可用的现成 listing 类（{@code VillagerTrades.ItemsForEmeralds} /
 * {@code EmeraldForItems} / …）**全部是包私有类**（javap：
 * {@code class net.minecraft.world.entity.npc.VillagerTrades$ItemsForEmeralds}，无 {@code public}）
 * ⇒ 模组代码无法引用。
 *
 * <p><b>语义逐字对齐</b> Forge {@code BasicItemListing(ItemStack price, ItemStack forSale,
 * int maxTrades, int xp, float priceMult)} 与 NeoForge 同名类：每次
 * {@link #getOffer(Entity, RandomSource)} 现造一对**新** {@link ItemStack}（报价会被消耗/累积 demand，
 * 共享栈会串数据），再交给 {@code new MerchantOffer(price, forSale, maxUses, villagerXp, priceMultiplier)}。
 * 本类把「栈」换成「物品 + 数量」，避免静态常量被 {@code ItemStack} 的可变状态污染。
 *
 * <p>⚠️ {@code priceMultiplier = 0.0F} 是有意为之：非 0 时卖完后原版 demand 累积会把 1 绿宝石抬到 2
 * （见 {@code CardTraderTrades} 的兑换条目注释）。
 */
public record FixedItemListing(Item costItem, int costCount,
                               Item resultItem, int resultCount,
                               int maxUses, int villagerXp, float priceMultiplier)
        implements VillagerTrades.ItemListing {

    @Override
    public MerchantOffer getOffer(Entity trader, RandomSource random) {
        return new MerchantOffer(new ItemStack(this.costItem(), this.costCount()),
                new ItemStack(this.resultItem(), this.resultCount()),
                this.maxUses(), this.villagerXp(), this.priceMultiplier());
    }
}
