package com.merlinkitsune.astral_dice.item;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * 本模组的数据标签常量。
 *
 * <p>每个常量都与 {@code data/astral_dice/tags/item/<路径>.json} 一一对应；命名空间必须是本模组 id，
 * 否则读到的是另一个（空的）标签。
 */
public final class ModTags {

    private ModTags() {
    }

    /** 物品标签（对应 data/astral_dice/tags/item/）。 */
    public static final class Items {

        private Items() {
        }

        /**
         * 专属牌（6 张，见 data/astral_dice/tags/item/is_exclusive.json）。
         *
         * <p>商店货架与村民交易一律不得出现专属牌 —— 判据见
         * {@link com.merlinkitsune.astral_dice.shop.CardTradeFilter#isSellable(net.minecraft.world.item.ItemStack)}。
         */
        public static final TagKey<Item> IS_EXCLUSIVE =
                ItemTags.create(ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "is_exclusive"));
    }
}
