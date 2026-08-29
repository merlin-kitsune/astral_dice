package com.merlinkitsune.astral_dice.combat;

/**
 * 骰神赐福【防御力】修饰器:按注册顺序依次作用于防御力数值。
 * 附属内容(新立牌/筹码/效果/联动)实现本接口并通过
 * {@link DiceCombatModifiers#registerDefenseModifier} 注册即可影响防御力,无需修改主流程。
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
