package com.merlinkitsune.astral_dice.combat;

/**
 * 法伤(远程/魔法伤害)加成修饰器:卡牌/筹码/立牌等对法伤的作用统一实现本接口,
 * 通过 {@link SpellDamageRegistry#registerModifier} 注册后由主结算链路聚合,无需修改主流程。
 */
public interface SpellDamageModifier {

    /**
     * 是否参与本次结算(效果牌=效果存在;筹码/立牌=佩戴)。
     *
     * @return true 时 apply/onHit 将被调用
     */
    default boolean isActive(SpellDamageContext ctx) {
        return true;
    }

    /**
     * 对本次法伤加成的加算(按注册顺序累积)。
     *
     * @param ctx   本次法伤上下文
     * @param bonus 当前总加成(前序修饰器累积后)
     * @return 修改后的总加成
     */
    double apply(SpellDamageContext ctx, double bonus);

    /**
     * 命中副作用钩子:在作用域判定通过且本修饰器激活时调用(无论加成是否大于 0)。
     * 用于"造成远程/魔法伤害后施加标记"、"定向爆破对周围目标造成同样伤害"等行为。
     *
     * @param ctx   本次法伤上下文
     * @param bonus 本次结算的总加成(apply 完成后)
     */
    default void onHit(SpellDamageContext ctx, double bonus) {
    }
}
