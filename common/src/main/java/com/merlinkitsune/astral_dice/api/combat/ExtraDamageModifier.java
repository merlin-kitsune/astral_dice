package com.merlinkitsune.astral_dice.api.combat;

/**
 * 【额外加伤】修饰器（B2 战斗注册口）：命中落地后按独立伤害类型单独结算的加伤点数。
 *
 * <p>与 {@link AttackPowerModifier} **分离**：这里的点数不得并进"攻击力"，否则会污染依赖
 * 攻击力快照的效果（例：教主立牌降神的狐光攻击基数）。附属文案请一律称"攻击伤害"。
 */
@FunctionalInterface
public interface ExtraDamageModifier {

    /**
     * @param view 本次骰战只读视图
     * @return 本次命中应额外结算的点数；0 或负值表示不适用（内部按 max(0, …) 累加）
     */
    int extraDamage(DiceBattleView view);
}
