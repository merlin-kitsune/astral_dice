package com.merlinkitsune.astral_dice.api.combat;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * 受击侧（受伤方）伤害修饰器（B2 战斗注册口）：按「受伤方 + 伤害来源」给出**伤害倍率**。
 *
 * <p>与攻击 / 防御修饰器不同，本表作用于**任何来源**的伤害（近战 / 远程 / 怪物 / 摔落 /
 * 溺水 / 虚空……），与攻击方是否触发骰神赐福无关。
 *
 * <p>约定：实现必须是**实时谓词**——每次调用基于当前状态重新判定，不得缓存；
 * 条件不成立时倍率立即回到 1.0。
 */
@FunctionalInterface
public interface VictimDamageModifier {

    /**
     * @param victim 受伤方
     * @param source 本次伤害来源
     * @return 伤害倍率（1.0 = 不变，按注册顺序连乘）
     */
    double multiplier(LivingEntity victim, DamageSource source);
}
