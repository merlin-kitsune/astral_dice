package com.merlinkitsune.astral_dice.item;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * 本模组的数据标签常量。
 *
 * <p>每个常量都与 {@code data/astral_dice/tags/items/<路径>.json} 一一对应；命名空间必须是本模组 id，
 * 否则读到的是另一个（空的）标签。
 *
 * <p>平台差异：1.20.1 的标签目录是**复数** {@code tags/items/}（1.21.1 是单数 {@code tags/item/}），
 * 标签 id 本身（{@code astral_dice:is_exclusive}）两线一致。Fabric 1.20.1 与 Forge 1.20.1 同为复数目录，
 * 故资源路径逐字相同。
 */
public final class ModTags {

    private ModTags() {
    }

    /** 物品标签（对应 data/astral_dice/tags/items/）。 */
    public static final class Items {

        private Items() {
        }

        /**
         * 专属牌（7 张，见 data/astral_dice/tags/items/is_exclusive.json）。
         *
         * <p>商店货架与村民交易一律不得出现专属牌 —— 判据见
         * {@link com.merlinkitsune.astral_dice.shop.CardTradeFilter#isSellable(net.minecraft.world.item.ItemStack)}。
         *
         * <p>⚠️ Fabric 1.20.1 用 {@code TagKey.create(Registries.ITEM, …)}：原版 1.20.1 的 {@code ItemTags}
         * 只有静态常量、**没有** {@code create(ResourceLocation)} 工厂（javap 实测；更方便的
         * {@code ItemTags.create} 是 1.21 才有的写法），故这里显式走 {@code TagKey.create}。
         */
        public static final TagKey<Item> IS_EXCLUSIVE =
                TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "is_exclusive"));
    }
}
