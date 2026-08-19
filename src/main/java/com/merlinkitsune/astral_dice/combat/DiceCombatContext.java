package com.merlinkitsune.astral_dice.combat;

import com.merlinkitsune.astral_dice.component.WeaponEnhancement;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import com.merlinkitsune.astral_dice.event.ModEventHandlers;

/**
 * 一次骰神赐福攻击的上下文:由 {@code ModEventHandlers.onLivingDamagePre} 在攻击链路上构建,
 * 供 {@link AttackPowerModifier} / {@link DefensePowerModifier} 读取与写入。
 * 修饰器只应读取 final 字段(只读输入)并修改自身负责的攻击力/防御力值;
 * 需要向后续流程传递结果时写入非 final 字段(如 attackCardSum/hasFullPower)。
 */
public class DiceCombatContext {

    /** 攻击者(玩家) */
    public final Player attacker;
    /** 本次攻击的目标(受击实体) */
    public final LivingEntity target;
    /** 触发本次计算的伤害事件 */
    public final LivingDamageEvent.Pre event;
    /** 本次攻击的基础骰点(1d6,已含护法爆发/上班族修正) */
    public final int baseDice;
    /** 攻击者骰子(可能存在) */
    public final ItemStack diceStack;
    /** 攻击者骰子的卡牌强化(非 null,由调用方保证) */
    public final WeaponEnhancement enhancement;
    /** 本次攻击是否触发骰神赐福 */
    public final boolean triggeredBlessing;
    /** 护法立牌(misaki)是否处于爆发状态 */
    public final boolean misakiBurst;
    /** 护法立牌(misaki)星级 */
    public final int misakiStar;
    /** 护法立牌(misaki)层数 */
    public final int misakiStacks;

    // === 修饰器写入区(供后续流程/主方法使用) ===

    /** 目标骰子的卡牌强化(防御卡掷骰用;目标无骰子时为 null) */
    public WeaponEnhancement targetEnhancement;
    /** 攻击卡掷骰总和(攻击卡修饰器写入) */
    public int attackCardSum;
    /** 防御卡掷骰总和(防御卡修饰器写入) */
    public int defenseCardSum;
    /** 是否装备了暗影突袭(修饰器写入) */
    public boolean hasShadowStrike;
    /** 是否装备了全力攻击(修饰器写入) */
    public boolean hasFullPower;
    /** 上班族立牌(padman):攻击骰点为 6 时忽略除防御卡外的全部防御(修饰器写入,主流程读取) */
    public boolean padmanDefBypass;

    public DiceCombatContext(Player attacker, LivingEntity target, LivingDamageEvent.Pre event,
                             int baseDice, ItemStack diceStack, WeaponEnhancement enhancement,
                             boolean triggeredBlessing, boolean misakiBurst,
                             int misakiStar, int misakiStacks) {
        this.attacker = attacker;
        this.target = target;
        this.event = event;
        this.baseDice = baseDice;
        this.diceStack = diceStack;
        this.enhancement = enhancement;
        this.triggeredBlessing = triggeredBlessing;
        this.misakiBurst = misakiBurst;
        this.misakiStar = misakiStar;
        this.misakiStacks = misakiStacks;
    }
}
