package com.merlinkitsune.astral_dice.combat;

import com.merlinkitsune.starengine.api.combat.damage.DamageContext;
import com.merlinkitsune.starengine.api.combat.damage.DamageHooks;
import com.merlinkitsune.starengine.api.combat.damage.DamagePhase;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** 把平台伤害事件桥接到 StarEngine 的伤害钩子链。 */
public final class DamageBridge {

    private DamageBridge() {
    }

    /** 触发一个阶段；返回的上下文同时携带 amount 改写与 cancel 状态。 */
    public static DamageContext fire(DamagePhase phase, LivingEntity entity, DamageSource source, float amount) {
        DamageContext context = new DamageContext(entity, source, phase, amount);
        DamageHooks.fire(phase, context);
        return context;
    }
}
