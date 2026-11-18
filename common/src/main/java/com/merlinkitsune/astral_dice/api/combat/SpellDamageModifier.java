package com.merlinkitsune.astral_dice.api.combat;

/**
 * 法伤加成修饰器（B2 战斗注册口）：卡牌 / 筹码 / 立牌对法伤的作用统一实现本接口。
 *
 * <p>三个钩子按顺序生效：{@link #isActive} → {@link #apply}（按注册顺序累积加算）→
 * {@link #onHit}（作用域判定通过且激活时必调，适合"造成法伤后施加标记"这类行为）。
 */
public interface SpellDamageModifier {

    /** 是否参与本次结算（效果牌=效果存在；筹码/立牌=佩戴）。 */
    default boolean isActive(SpellDamageView view) {
        return true;
    }

    /**
     * 对本次法伤的加算。
     *
     * @param view  本次法伤只读视图
     * @param bonus 当前总加成（前序修饰器累积后）
     * @return 修改后的总加成
     */
    double apply(SpellDamageView view, double bonus);

    /** 命中副作用钩子（加成是否大于 0 都会调用）。 */
    default void onHit(SpellDamageView view, double bonus) {
    }
}
