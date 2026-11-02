package com.merlinkitsune.astral_dice.compat.jei;

import net.minecraft.world.item.Item;

/**
 * JEI「筹码商店」类别的单条记录：一枚可在商店货架上出售的筹码。
 *
 * <p>与 {@link CardShopRecipe} 同构：只保存 {@link Item} 而不保存
 * {@link net.minecraft.world.item.ItemStack} —— 物品是注册期单例、用引用相等判断，
 * 作为 record 组件可以稳定地比较与去重；需要画槽时再就地 {@code new ItemStack(chip)} 即可。
 *
 * <p>注意：本记录只表示「这枚筹码可以从商店买到」，不含价格 ——
 * 真实售价由 {@code ChipShopMenu} 按当前累计购买次数逐格计算
 * （{@link com.merlinkitsune.astral_dice.shop.ChipShopCatalog#priceOf(int)}：
 * 基础 {@code PRICE_INITIAL}、每次购买 {@code PRICE_STEP}、上限 {@code PRICE_MAX}），
 * 类别里只用这三个常量做统一说明。
 *
 * @param chip 该记录对应的筹码物品
 */
public record ChipShopRecipe(Item chip) {
}
