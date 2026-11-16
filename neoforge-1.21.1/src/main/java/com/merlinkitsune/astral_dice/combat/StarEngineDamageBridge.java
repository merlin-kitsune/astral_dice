package com.merlinkitsune.astral_dice.combat;

import com.merlinkitsune.starengine.api.combat.damage.DamageContext;
import com.merlinkitsune.starengine.api.combat.damage.DamagePhase;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/** NeoForge 1.21.1 伤害链桥：四个阶段都有原生事件。 */
@EventBusSubscriber(modid = com.merlinkitsune.astral_dice.AstralDiceMod.MODID)
public final class StarEngineDamageBridge {

    private StarEngineDamageBridge() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void incoming(LivingIncomingDamageEvent event) {
        float before = event.getAmount();
        DamageContext context = DamageBridge.fire(DamagePhase.INCOMING, event.getEntity(), event.getSource(), before);
        if (context.isCanceled()) {
            event.setCanceled(true);
        } else if (context.amount() != before) {
            event.setAmount(context.amount());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void afterArmor(LivingDamageEvent.Pre event) {
        float before = event.getNewDamage();
        DamageContext context = DamageBridge.fire(DamagePhase.AFTER_ARMOR, event.getEntity(), event.getSource(), before);
        if (context.isCanceled()) {
            event.setNewDamage(0.0F);
        } else if (context.amount() != before) {
            event.setNewDamage(context.amount());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void post(LivingDamageEvent.Post event) {
        DamageBridge.fire(DamagePhase.POST, event.getEntity(), event.getSource(), event.getNewDamage());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void death(LivingDeathEvent event) {
        DamageContext context = DamageBridge.fire(DamagePhase.DEATH, event.getEntity(), event.getSource(), 0.0F);
        if (context.isCanceled()) {
            event.setCanceled(true);
        }
    }
}
