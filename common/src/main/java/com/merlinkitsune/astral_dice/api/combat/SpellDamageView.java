package com.merlinkitsune.astral_dice.api.combat;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * 一次法伤（远程 / 魔法伤害）结算的**只读**视图（B2 战斗注册口）。
 *
 * <p>内部上下文 {@code combat.SpellDamageContext} 挂着 {@code LivingIncomingDamageEvent} 与
 * curios 查询，属于各加载器线自备类型；门面只交付本记录里的值，附属不能直接改事件。
 *
 * @param attacker     施法者（内部当前只提供玩家）
 * @param target       伤害目标
 * @param source       伤害来源
 * @param directEntity 直接伤害实体（弹射物 / 施法者等）
 */
public record SpellDamageView(Player attacker, LivingEntity target, DamageSource source, Entity directEntity) {
}
