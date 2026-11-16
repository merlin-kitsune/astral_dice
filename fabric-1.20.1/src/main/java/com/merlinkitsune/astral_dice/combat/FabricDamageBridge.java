package com.merlinkitsune.astral_dice.combat;

import com.merlinkitsune.starengine.api.combat.damage.DamageContext;
import com.merlinkitsune.starengine.api.combat.damage.DamagePhase;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

/** Fabric 1.20.1 伤害桥：ALLOW_DAMAGE / ALLOW_DEATH；该线 API 没有 AFTER_DAMAGE。 */
public final class FabricDamageBridge {

    private FabricDamageBridge() {
    }

    public static void register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            DamageContext context = DamageBridge.fire(DamagePhase.INCOMING, entity, source, amount);
            return !context.isCanceled();
        });
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            DamageContext context = DamageBridge.fire(DamagePhase.DEATH, entity, source, amount);
            return !context.isCanceled();
        });
    }
}
