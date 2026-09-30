package com.merlinkitsune.astral_dice.combat;

/**
 * 骰神赐福【防御力】修饰器:按注册顺序依次作用于防御力数值。
 * 防御力规范:骰战防御修饰器**仅保留战斗防御牌**(只有防御牌数值是区间变动,由 CardRegistry 掷骰);
 * 效果牌/立牌/筹码的防御力一律折算为真实护甲(见 {@link DiceCombatModifiers#setDefenseArmorBonus}),不在此注册。
 */
@FunctionalInterface
public interface DefensePowerModifier {

    /**
     * @param ctx  本次骰战上下文(注意:防御侧输入为 {@code ctx.target})
     * @param defensePower 当前防御力(前序修饰器/基础值结算后)
     * @return 修改后的防御力
     */
    double apply(DiceCombatContext ctx, double defensePower);
}
