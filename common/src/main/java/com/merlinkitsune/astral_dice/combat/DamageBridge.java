package com.merlinkitsune.astral_dice.combat;

import com.merlinkitsune.starengine.api.combat.damage.DamageContext;
import com.merlinkitsune.starengine.api.combat.damage.DamageHooks;
import com.merlinkitsune.starengine.api.combat.damage.DamagePhase;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * 把平台伤害事件桥接到 StarEngine 的伤害钩子链。
 *
 * <p><b>DEATH 阶段取消语义</b>:取消只会阻止平台继续处理死亡(掉落、死亡消息等),
 * <b>不会</b>恢复已经扣掉的生命值,也不会让实体回到存活状态。若监听器希望目标存活,
 * 必须在 {@code POST} 阶段把生命值补回,或在 {@code INCOMING}/{@code AFTER_ARMOR}
 * 阶段改写伤害使目标不致死。
 */
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
