package com.merlinkitsune.astral_dice.api.data;

import java.util.List;

/**
 * 一件骰子的卡牌栏快照（只读）。
 *
 * @param starLevel      骰子**实际**星级（0-3；不是下界之星骰子那种「配置 3★」）
 * @param cardSlots      卡牌栏总格数（攻/防各一半）
 * @param attackSlots    攻击牌格数
 * @param usedAttackCost 已用攻击费用
 * @param maxAttackCost  攻击费用上限（由配置星级决定）
 * @param usedDefenseCost 已用防御费用
 * @param maxDefenseCost  防御费用上限
 * @param cards          已装配卡牌（保持写入顺序）
 */
public record DiceLoadout(int starLevel, int cardSlots, int attackSlots,
                          int usedAttackCost, int maxAttackCost,
                          int usedDefenseCost, int maxDefenseCost,
                          List<InstalledCard> cards) {

    public static final DiceLoadout EMPTY = new DiceLoadout(0, 0, 0, 0, 0, 0, 0, List.of());

    public DiceLoadout {
        cards = cards == null ? List.of() : List.copyOf(cards);
    }

    public boolean isEmpty() {
        return cards.isEmpty();
    }
}
