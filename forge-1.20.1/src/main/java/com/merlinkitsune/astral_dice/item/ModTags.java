package com.merlinkitsune.astral_dice.item;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * 本模组的数据标签常量。
 *
 * <p>每个常量都与 {@code data/astral_dice/tags/items/<路径>.json} 一一对应；命名空间必须是本模组 id，
 * 否则读到的是另一个（空的）标签。
 *
 * <p>平台差异：1.20.1 的标签目录是**复数** {@code tags/items/}（1.21.1 是单数 {@code tags/item/}），
 * 标签 id 本身（{@code astral_dice:is_exclusive}）两线一致。
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
         */
        public static final TagKey<Item> IS_EXCLUSIVE =
                ItemTags.create(new ResourceLocation(AstralDiceMod.MODID, "is_exclusive"));

        /**
         * 专属筹码（6 枚：诅咒之剑 / 复仇之戟 / 贯穿之铳 / 石中剑-精良 / 石中剑-传说 / 完美树枝）。
         *
         * <p>目前只作为数据标签存在，尚无任何消费方；后续玩法设计再接入。
         */
        public static final TagKey<Item> EXCLUSIVE_CHIPS =
                ItemTags.create(new ResourceLocation(AstralDiceMod.MODID, "exclusive_chips"));

        /**
         * 筹码流派标签：{@code School.STARLIGHT}（11 枚）。
         *
         * <p>收录 {@code ChipShopCatalog.POOL} 中该流派的全部筹码，顺序与 POOL 一致。
         *
         * <p>目前只作为数据标签存在，尚无任何消费方；后续研究系统接入时再使用。
         */
        public static final TagKey<Item> SCHOOL_STARLIGHT =
                ItemTags.create(new ResourceLocation(AstralDiceMod.MODID, "school_starlight"));

        /**
         * 筹码流派标签：{@code School.HEALING}（10 枚）。
         *
         * <p>收录 {@code ChipShopCatalog.POOL} 中该流派的全部筹码，顺序与 POOL 一致。
         *
         * <p>目前只作为数据标签存在，尚无任何消费方；后续研究系统接入时再使用。
         */
        public static final TagKey<Item> SCHOOL_HEALING =
                ItemTags.create(new ResourceLocation(AstralDiceMod.MODID, "school_healing"));

        /**
         * 筹码流派标签：{@code School.MARK}（9 枚）。
         *
         * <p>收录 {@code ChipShopCatalog.POOL} 中该流派的全部筹码，顺序与 POOL 一致。
         *
         * <p>目前只作为数据标签存在，尚无任何消费方；后续研究系统接入时再使用。
         */
        public static final TagKey<Item> SCHOOL_MARK =
                ItemTags.create(new ResourceLocation(AstralDiceMod.MODID, "school_mark"));

        /**
         * 筹码流派标签：{@code School.CHARGE}（11 枚）。
         *
         * <p>收录 {@code ChipShopCatalog.POOL} 中该流派的全部筹码，顺序与 POOL 一致。
         *
         * <p>目前只作为数据标签存在，尚无任何消费方；后续研究系统接入时再使用。
         */
        public static final TagKey<Item> SCHOOL_CHARGE =
                ItemTags.create(new ResourceLocation(AstralDiceMod.MODID, "school_charge"));
    }
}
