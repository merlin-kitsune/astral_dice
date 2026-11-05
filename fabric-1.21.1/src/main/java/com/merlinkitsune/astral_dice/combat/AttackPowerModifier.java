package com.merlinkitsune.astral_dice.combat;

/**
 * 骰神赐福【攻击力】修饰器:按注册顺序依次作用于攻击力数值。
 * 附属内容(新立牌/筹码/效果/联动)实现本接口并通过
 * {@link DiceCombatModifiers#registerAttackModifier} 注册即可影响攻击力,无需修改主流程。
 * 幂等约定:修饰器应只基于上下文与传入的攻击力计算,不修改上下文以外的状态
 * (需要向后续流程传递结果时写入上下文的非 final 字段)。
 */
@FunctionalInterface
public interface AttackPowerModifier {

    /**
     * @param ctx  本次骰战上下文(只读输入 + 结果写入区)
     * @param attackPower 当前攻击力(前序修饰器/基础值结算后)
     * @return 修改后的攻击力
     */
    double apply(DiceCombatContext ctx, double attackPower);
}
