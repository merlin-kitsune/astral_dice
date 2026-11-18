package com.merlinkitsune.astral_dice.api.combat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * 骰战最终伤害因子（B2 战斗注册口）：作用于骰神赐福**最终伤害**的乘算修饰器。
 *
 * <p>按注册顺序依次作用于最终伤害；因子自行判断生效条件，不生效时原样返回 {@code damage}。
 * 该通道只覆盖骰战结算，不覆盖普通伤害。
 */
@FunctionalInterface
public interface DiceCombatFactor {

    /**
     * @param attacker 攻击者（内部当前只提供玩家；类玩家宿主走各自路径）
     * @param target   受击实体
     * @param damage   当前最终伤害
     * @return 修改后的伤害
     */
    double modify(Player attacker, LivingEntity target, double damage);
}
