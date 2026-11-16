package com.merlinkitsune.astral_dice.api.combat;

/**
 * 属性钩子：接收当前数值并返回新数值。
 *
 * <p>实现应保持快速、无阻塞；异常会被注册表捕获并记录，随后继续执行后续钩子。
 */
@FunctionalInterface
public interface CombatStatHook {

    /** @return 替换后的数值 */
    double apply(CombatStatContext context, double value);
}
