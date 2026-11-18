package com.merlinkitsune.astral_dice.api.combat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * 一次骰神赐福攻击的**只读**视图（B2 战斗注册口）。
 *
 * <p>内部上下文 {@code combat.DiceCombatContext} 是各加载器线自备的类型，还挂着
 * {@code LivingDamageEvent.Pre} 之类的加载器事件，{@code common} 无法引用；门面因此只对外
 * 交付本记录里的值。修饰器据此计算，**不能**回写内部上下文（内部那些"结果写入区"字段
 * 对附属不可见）。
 *
 * @param attacker          攻击者（玩家，或经 {@code registerPlayerLike} 注册的类玩家生物）
 * @param target            本次攻击的受击实体
 * @param baseDice          本次攻击的基础骰点（1d6，已含护法与上班族修正）
 * @param diceStack         攻击者骰子；没有骰子时为 {@link ItemStack#EMPTY}
 * @param starLevel         骰子星级（{@code enhancement.starLevel()}）；无骰子为 0
 * @param triggeredBlessing 本次攻击是否触发骰神赐福
 * @param misakiBurst       护法立牌是否处于爆发状态
 * @param misakiStar        护法立牌星级
 * @param misakiStacks      护法立牌层数
 */
public record DiceBattleView(
        LivingEntity attacker,
        LivingEntity target,
        int baseDice,
        ItemStack diceStack,
        int starLevel,
        boolean triggeredBlessing,
        boolean misakiBurst,
        int misakiStar,
        int misakiStacks) {

    public DiceBattleView {
        diceStack = diceStack == null ? ItemStack.EMPTY : diceStack;
    }
}
