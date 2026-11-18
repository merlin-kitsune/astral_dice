package com.merlinkitsune.astral_dice.api.combat;

/**
 * 骰神赐福【防御力】修饰器（B2 战斗注册口）：按注册顺序依次作用于防御力数值。
 *
 * <p>防御力规范（与内部一致）：本表**只服务于战斗防御牌**（只有防御牌数值是区间变动）；
 * 效果牌 / 立牌 / 筹码提供的防御力一律折算为真实护甲，不走这个通道。
 */
@FunctionalInterface
public interface DefensePowerModifier {

    /**
     * @param view         本次骰战只读视图（防御侧的被评估者是 {@link DiceBattleView#target()}）
     * @param defensePower 当前防御力（前序修饰器 / 基础值结算后）
     * @return 修改后的防御力
     */
    double apply(DiceBattleView view, double defensePower);
}
