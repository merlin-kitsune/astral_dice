package com.merlinkitsune.astral_dice.combat;

import com.merlinkitsune.astral_dice.component.WeaponEnhancement;
import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * 一次骰神赐福攻击的上下文:由 {@code DiceCombatEvents.onLivingDamagePre} 在攻击链路上构建,
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
    /** 攻击者是否佩戴玻璃骰子(战斗牌点数始终取最大值) */
    public final boolean attackerCardsMax;

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
    /** 防御方(目标玩家)是否佩戴玻璃骰子(防御牌点数始终取最大值;由防御结算流程写入) */
    public boolean targetCardsMax;
    /**
     * 鹰眼瞄具:本次攻击**命中前**目标已有的标记层数(由瞄具攻击修饰器在 {@code MarkManager.apply}
     * **之前**写入;0 = 无加成)。加伤本身走额外加伤通道(见 {@code DiceCombatModifiers} 的同件修饰器)。
     */
    public int eagleScopeMarkLevel;

    /**
     * 本次求值是否为**显示口径**（HUD 攻击力条等纯展示用途）。
     *
     * <p>客户端上唯一会跑攻击修饰器链的用途就是显示 ⇒ {@link DiceCombatModifiers#attackPowerBase}
     * 在客户端求值时置 true，修饰器据此不再跳过客户端分支（2026-10-25 用户裁决：
     * 所有攻击力都要显示，除手持武器本身与骰战中的随机数值加成）。
     * 真实战斗上下文由服务端构建，本字段恒为 false。
     */
    public boolean displaySnapshot;

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
        this.attackerCardsMax = diceStack != null && !diceStack.isEmpty() && diceStack.is(ModItems.GLASS_DICE.get());
    }
}
