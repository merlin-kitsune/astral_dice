package com.merlinkitsune.astral_dice.api.combat;

import java.util.Locale;

/**
 * 属性钩子的结算阶段。钩子返回**新的数值**，多个钩子按优先级从大到小链式执行。
 *
 * <p>约定：BASE 用于替换初始值；ADDITIVE 写成 {@code value + n}；
 * MULTIPLICATIVE 写成 {@code value * m}；FINAL 用于最终数值的最后修正。
 * 阶段本身不强制运算，只决定执行顺序。
 */
public enum CombatStatStage {
    /** 初始值：在公式/内置修饰器之前。 */
    BASE,
    /** 加算：内置修饰器之后。 */
    ADDITIVE,
    /** 乘算：加算之后。 */
    MULTIPLICATIVE,
    /** 最终值：进入骰战解析器之前。 */
    FINAL;

    /** 小写 id。 */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** 按大小写不敏感的 id 查找；找不到返回 {@code null}。 */
    public static CombatStatStage byId(String id) {
        if (id == null) {
            return null;
        }
        for (CombatStatStage stage : values()) {
            if (stage.name().equalsIgnoreCase(id)) {
                return stage;
            }
        }
        return null;
    }
}
