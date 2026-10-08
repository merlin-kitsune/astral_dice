package com.merlinkitsune.astral_dice.compat.jei;

import com.merlinkitsune.astral_dice.shop.CardShopCatalog;
import com.merlinkitsune.astral_dice.shop.CardTradeFilter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.Item;

/**
 * JEI 侧的可售卡清单。
 *
 * <p><b>唯一判据</b>：直接遍历 {@link CardShopCatalog#entries()}（22 条抽池），
 * 逐条套用 {@link CardTradeFilter#isSellable(Item)} —— 与商店货架、村民交易共用同一套
 * 排除规则（astral_dice:is_exclusive 专属标签、巅峰 pinnacle、奇特 bizarre）。
 * 这里不另写任何过滤条件，避免 JEI 展示与实机货架口径漂移。
 *
 * <p>本类只在 JEI 插件被加载时（{@code @JeiPlugin}）被引用，JEI 缺席时不会被加载。
 */
public final class CardShopJeiRecipes {

    private CardShopJeiRecipes() {
    }

    /**
     * 返回卡牌商店抽池中所有可售的卡牌，顺序与 {@link CardShopCatalog#entries()} 一致。
     *
     * @return 可售卡牌物品清单（副本，调用方可自由修改）
     */
    public static List<Item> sellableCards() {
        List<Item> cards = new ArrayList<>();
        for (CardShopCatalog.PoolEntry entry : CardShopCatalog.entries()) {
            Item item = entry.item().get();
            if (CardTradeFilter.isSellable(item)) {
                cards.add(item);
            }
        }
        return cards;
    }
}
