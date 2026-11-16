package com.merlinkitsune.astral_dice.compat.jei;

import net.minecraft.world.item.Item;

/**
 * JEI「卡牌商店」类别的单条记录：一张可在商店货架上出售的卡牌。
 *
 * <p>这里只保存 {@link Item} 而不保存 {@link net.minecraft.world.item.ItemStack}：
 * 物品是注册期单例、用引用相等判断，作为 record 组件可以稳定地比较与去重；
 * 需要画槽时再就地 {@code new ItemStack(card)} 即可。
 *
 * <p>注意：本记录只表示「这张卡可以从商店买到」，不含价格 ——
 * 真实售价由 CardShopMenu#priceOf(int) 按货架格逐格决定（基础 3 / 打折 1），
 * 类别里只用 CardShopCatalog.BASE_PRICE 做统一说明。
 *
 * <p>1.20.1 移植说明：与 neoforge-1.21.1 的同名文件逐字等价，未做任何平台改写。
 *
 * @param card 该记录对应的卡牌物品
 */
public record CardShopRecipe(Item card) {
}
