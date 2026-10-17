package com.merlinkitsune.astral_dice.choice;

import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * 「选卡界面」允许放入的卡牌类型（用户裁决 2026-10-17）。
 *
 * <p>判定口径 = 具体物品标签（{@code data/astral_dice/tags/item/combat_cards.json} /
 * {@code effect_cards.json}，见 {@link ModItems#isCombatCard}/{@link ModItems#isEffectCard}）：
 * <ul>
 *   <li>{@link #COMBAT}：仅战斗牌；</li>
 *   <li>{@link #EFFECT}：仅效果牌；</li>
 *   <li>{@link #ANY}：任意卡牌（战斗牌 + 效果牌，含专属牌）。</li>
 * </ul>
 *
 * <p>网络口径 = {@link #ordinal()}（追加常量时不得插队，只能往末尾加）。
 */
public enum CardChoiceType {
    COMBAT,
    EFFECT,
    ANY;

    /** 该类型是否接受这个物品栈（只比物品本体，不看数量）。 */
    public boolean matches(ItemStack stack) {
        return switch (this) {
            case COMBAT -> ModItems.isCombatCard(stack);
            case EFFECT -> ModItems.isEffectCard(stack);
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

    /** 网络解码；越界一律回落 {@link #ANY}（服务端仍会做槽数/会话校验）。 */
    public static CardChoiceType byId(int id) {
        CardChoiceType[] values = values();
        return id >= 0 && id < values.length ? values[id] : ANY;
    }
}
