package com.merlinkitsune.astral_dice.api.combat;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;

/**
 * 法伤作用域判定器（B2 战斗注册口）：白名单 matcher，命中即视为"远程 / 魔法伤害"。
 *
 * <p>内置作用域已覆盖原版弹射物与魔法、新生魔艺 / 诡厄巫法 / Iron 的法术与魔法书等；
 * 附属模组的自定义法术伤害类型请在这里登记，军火类伤害的排除仍由配置项决定。
 */
@FunctionalInterface
public interface SpellDamageMatcher {

    /**
     * @param source      伤害来源
     * @param directEntity 直接伤害实体
     * @return 本次伤害是否属于法伤作用域
     */
    boolean matches(DamageSource source, Entity directEntity);
}
