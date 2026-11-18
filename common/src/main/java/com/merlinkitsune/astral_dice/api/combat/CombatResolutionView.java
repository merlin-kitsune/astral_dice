package com.merlinkitsune.astral_dice.api.combat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * 一次骰战结算完成后的**只读**快照（B3 战斗结算回调）。
 *
 * <p>玩家攻击路径与注册生物近战路径都会在结算尾部各发一次；走 {@link CombatResolutionListeners}
 * 分发给 {@link CombatResolutionListener}。与 {@link DiceBattleView} 的区别：本记录是**结算之后**的
 * 结果读数（含额外加伤与落地量），不是修饰器的输入，回调里改不了已经落地的这一击。
 *
 * @param attacker      发起这次骰战的一方（玩家，或注册过的类玩家生物）
 * @param target        受击实体
 * @param finalDamage   写入伤害事件的终值（护甲/抗性/保护之前的口径）
 * @param landingDamage 落地红心量（终值减去被吸收量）
 * @param extraDamage   本击结算的额外加伤点数（独立伤害类型 astral_dice:extra_damage；没有则为 0）
 * @param diceStack     攻击方的骰子；没有骰子时为 {@link ItemStack#EMPTY}
 * @param starLevel     骰子星级；没有骰子或没有强化数据时为 0
 */
public record CombatResolutionView(
        LivingEntity attacker,
        LivingEntity target,
        double finalDamage,
        double landingDamage,
        int extraDamage,
        ItemStack diceStack,
        int starLevel) {

    public CombatResolutionView {
        diceStack = diceStack == null ? ItemStack.EMPTY : diceStack;
    }
}
