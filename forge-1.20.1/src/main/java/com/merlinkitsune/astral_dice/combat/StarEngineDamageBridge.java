package com.merlinkitsune.astral_dice.combat;

import com.merlinkitsune.starengine.api.combat.damage.DamageContext;
import com.merlinkitsune.starengine.api.combat.damage.DamagePhase;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Forge 1.20.1 伤害链桥：Hurt/护甲后/死亡；该线没有原生 Post 事件。 */
@Mod.EventBusSubscriber(modid = com.merlinkitsune.astral_dice.AstralDiceMod.MODID)
public final class StarEngineDamageBridge {

    private StarEngineDamageBridge() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void incoming(LivingHurtEvent event) {
        float before = event.getAmount();
        DamageContext context = DamageBridge.fire(DamagePhase.INCOMING, event.getEntity(), event.getSource(), before);
        if (context.isCanceled()) {
            event.setCanceled(true);
        } else if (context.amount() != before) {
            event.setAmount(context.amount());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void afterArmor(LivingDamageEvent event) {
        float before = event.getAmount();
        DamageContext context = DamageBridge.fire(DamagePhase.AFTER_ARMOR, event.getEntity(), event.getSource(), before);
        if (context.isCanceled()) {
            event.setCanceled(true);
        } else if (context.amount() != before) {
            event.setAmount(context.amount());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void death(LivingDeathEvent event) {
        DamageContext context = DamageBridge.fire(DamagePhase.DEATH, event.getEntity(), event.getSource(), 0.0F);
        if (context.isCanceled()) {
            event.setCanceled(true);
        }
    }
}
