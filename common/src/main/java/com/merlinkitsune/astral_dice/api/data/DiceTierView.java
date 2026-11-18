package com.merlinkitsune.astral_dice.api.data;

import java.util.function.IntUnaryOperator;

/**
 * 一个已注册骰子阶层的**只读视图**：附属注册可玩骰子后拿到的稳定句柄。
 *
 * <p>为什么不是内部类型：内部的 {@code item.dice.DiceTier} 带着注册期才需要的
 * {@code Supplier<Item>}，而且与 StarEngine 的同名类分属两个包，
 * 附属按返回类型接很容易接错。这里只留下对外语义：
 * id、卡牌栏格数、按星级取筹码格数的函数。
 *
 * @param id        骰子 id（去掉命名空间的 path，如 {@code nether_star_dice}）
 * @param cardSlots 卡牌栏格数
 * @param chipSlots 星级 → 筹码格数（**绝对格数**，不是每级增量）
 */
public record DiceTierView(String id, int cardSlots, IntUnaryOperator chipSlots) {

    public DiceTierView {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (cardSlots < 0) {
            throw new IllegalArgumentException("cardSlots must be >= 0");
        }
        if (chipSlots == null) {
            throw new IllegalArgumentException("chipSlots must not be null");
        }
    }

    /** 该星级下的筹码格数。 */
    public int chipSlotsAt(int starLevel) {
        return chipSlots.applyAsInt(starLevel);
    }
}
