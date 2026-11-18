package com.merlinkitsune.astral_dice.api.combat;

/**
 * 骰神赐福【攻击力】修饰器（B2 战斗注册口）：按注册顺序依次作用于攻击力数值。
 *
 * <p>附属内容（新立牌 / 筹码 / 效果 / 联动）实现本接口并用
 * {@code AstralDiceApi.registerAttackModifier} 注册，即可影响攻击力，无需改主流程。
 *
 * <p>幂等约定：修饰器应只基于视图与传入的攻击力计算，不得缓存状态，也不得修改世界。
 */
@FunctionalInterface
public interface AttackPowerModifier {

    /**
     * @param view        本次骰战只读视图
     * @param attackPower 当前攻击力（前序修饰器 / 基础值结算后）
     * @return 修改后的攻击力
     */
    double apply(DiceBattleView view, double attackPower);
}
