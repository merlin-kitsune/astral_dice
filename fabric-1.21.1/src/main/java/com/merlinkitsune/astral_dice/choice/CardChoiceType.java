package com.merlinkitsune.astral_dice.choice;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * 「选卡界面」允许放入的卡牌类型（用户裁决 2026-10-17；1:1 移植自 forge-1.20.1 同名文件）。
 *
 * <p>判定口径 = 具体物品标签（{@code data/astral_dice/tags/item/combat_cards.json} /
 * {@code effect_cards.json}）：
 * <ul>
 *   <li>{@link #COMBAT}：仅战斗牌；</li>
 *   <li>{@link #EFFECT}：仅效果牌；</li>
 *   <li>{@link #ANY}：任意卡牌（战斗牌 + 效果牌，含专属牌）。</li>
 * </ul>
 *
 * <p><b>平台差异（fabric-1.20.1）</b>：forge 线的 COMBAT/EFFECT 两支委托给
 * {@code ModItems#isCombatCard}/{@code ModItems#isEffectCard}；本线的
 * {@code ModItems.COMBAT_CARDS_TAG}/{@code EFFECT_CARDS_TAG} 是 <b>private</b> 字段且
 * {@code ModItems} 未提供这两个包装方法 ⇒ 本类**就地**持有一对同 id 的 {@link TagKey}
 * （{@code astral_dice:combat_cards} / {@code astral_dice:effect_cards}，与 ModItems 内字段同源），
 * {@link #ANY} 仍走 {@link ModItems#isCardItem(ItemStack)}。判定结果与 forge 线逐项等价；
 * 若后续把两个包装方法补进 ModItems，本类可直接换回委托（改动仅此 4 行）。
 *
 * <p>网络口径 = {@link #ordinal()}（追加常量时不得插队，只能往末尾加）。
 */
public enum CardChoiceType {
    COMBAT,
    EFFECT,
    ANY;

    /** 与 {@code item/ModItems} 的私有字段同 id（标签由数据包提供，这里只做键）。 */
    private static final TagKey<Item> COMBAT_CARDS_TAG =
            TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "combat_cards"));
    private static final TagKey<Item> EFFECT_CARDS_TAG =
            TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "effect_cards"));

    /** 该类型是否接受这个物品栈（只比物品本体，不看数量）。 */
    public boolean matches(ItemStack stack) {
        return switch (this) {
            case COMBAT -> stack.is(COMBAT_CARDS_TAG);
            case EFFECT -> stack.is(EFFECT_CARDS_TAG);
            case ANY -> ModItems.isCardItem(stack);
        };
    }

    /**
     * 标题 lang 键：`gui.astral_dice.card_choice.title_<single|triple>_<combat|effect|any>`，
     * 覆盖 1×{combat,effect,any} + 3×{combat,effect,any} 共 6 键（三语各一份）。
     */
    public String titleKey(int slotCount) {
        return "gui.astral_dice.card_choice.title_" + (slotCount == 1 ? "single_" : "triple_")
                + name().toLowerCase(Locale.ROOT);
    }

    /**
     * 标题 lang 键（**变长下限**）：{@code minSlots < slotCount} 时用
     * `..._variable_<type>`（「选择 1~3 张」），否则回落 {@link #titleKey(int)}。
     *
     * <p>2026-11-08 新增：少主立牌「强者怜悯」是「1~3 张任选」，标题不能写成「选择三张」。
     */
    public String titleKey(int slotCount, int minSlots) {
        if (minSlots < slotCount) {
            return "gui.astral_dice.card_choice.title_variable_" + name().toLowerCase(Locale.ROOT);
        }
        return titleKey(slotCount);
    }

    /** 网络解码；越界一律回落 {@link #ANY}（服务端仍会做槽数/会话校验）。 */
    public static CardChoiceType byId(int id) {
        CardChoiceType[] values = values();
        return id >= 0 && id < values.length ? values[id] : ANY;
    }
}
