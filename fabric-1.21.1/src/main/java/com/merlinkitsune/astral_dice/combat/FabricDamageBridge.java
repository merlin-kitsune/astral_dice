package com.merlinkitsune.astral_dice.combat;

import com.merlinkitsune.starengine.api.combat.damage.DamageContext;
import com.merlinkitsune.starengine.api.combat.damage.DamagePhase;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

/** Fabric 1.21.1 伤害桥：ALLOW_DAMAGE / AFTER_DAMAGE / ALLOW_DEATH。 */
public final class FabricDamageBridge {

    private FabricDamageBridge() {
    }

    public static void register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            DamageContext context = DamageBridge.fire(DamagePhase.INCOMING, entity, source, amount);
            return !context.isCanceled();
        });
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) ->
                DamageBridge.fire(DamagePhase.POST, entity, source, damageTaken));
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            DamageContext context = DamageBridge.fire(DamagePhase.DEATH, entity, source, amount);
            return !context.isCanceled();
        });
    }
}
