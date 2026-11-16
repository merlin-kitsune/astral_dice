package com.merlinkitsune.astral_dice.api.combat;

import java.util.Objects;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * 一次属性钩子调用的上下文。
 *
 * @param attacker 攻击方
 * @param target   防御方
 * @param source   本次伤害来源；HUD / 预览路径可能为 {@code null}
 * @param display  是否为显示用途；为 {@code true} 时公共注册表不会执行钩子
 */
public record CombatStatContext(LivingEntity attacker, LivingEntity target, DamageSource source, boolean display) {

    public CombatStatContext {
        Objects.requireNonNull(attacker, "attacker");
        Objects.requireNonNull(target, "target");
    }
}
