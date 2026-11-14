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

        /**
         * 骰子分级标签（实验性升星开关「分级经验」的依据；开关关闭时无消费方）。
         *
         * <p>T0=基础骰；T1=黄金/玻璃/下界岩；T2=钻石/绿宝石/黑曜石/诡异/紫晶；
         * T3=下界合金/绯红/末影；T4=下界之星 —— 13 枚骰子全部分级、无遗漏。
         * 经验表见 {@link com.merlinkitsune.astral_dice.item.DiceStarUpgrade#xpCostFor(net.minecraft.world.item.ItemStack)}。
         */
        public static final TagKey<Item> DICE_T0 =
                ItemTags.create(new ResourceLocation(AstralDiceMod.MODID, "dice_t0"));

        /** 骰子分级标签 T1（黄金 / 玻璃 / 下界岩）。 */
        public static final TagKey<Item> DICE_T1 =
                ItemTags.create(new ResourceLocation(AstralDiceMod.MODID, "dice_t1"));

        /** 骰子分级标签 T2（钻石 / 绿宝石 / 黑曜石 / 诡异 / 紫晶）。 */
        public static final TagKey<Item> DICE_T2 =
                ItemTags.create(new ResourceLocation(AstralDiceMod.MODID, "dice_t2"));

        /** 骰子分级标签 T3（下界合金 / 绯红 / 末影）。 */
        public static final TagKey<Item> DICE_T3 =
                ItemTags.create(new ResourceLocation(AstralDiceMod.MODID, "dice_t3"));

        /** 骰子分级标签 T4（下界之星）。 */
        public static final TagKey<Item> DICE_T4 =
                ItemTags.create(new ResourceLocation(AstralDiceMod.MODID, "dice_t4"));

        /**
         * 星愿铁砧材料槽可接受的物品（对应 data/astral_dice/tags/item/astral_anvil_materials.json）。
         *
         * <p>⚠️ 目前只作为数据标签常量存在：JSON 由资源批次落地，本批不新建 / 不改任何 tag JSON。
         */
        public static final TagKey<Item> ASTRAL_ANVIL_MATERIALS =
                ItemTags.create(new ResourceLocation(AstralDiceMod.MODID, "astral_anvil_materials"));
    }
}
