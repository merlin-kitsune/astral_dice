package com.merlinkitsune.astral_dice.combat;

import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.damage.ModDamageTypes;
import com.merlinkitsune.astral_dice.item.MarkManager;
import com.merlinkitsune.astral_dice.item.card.EffectCardPeriod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public final class LivingPageImpact {
    public static final int BASE_DAMAGE = 2;

    public static final int SPELL_DAMAGE_COLOR = 0x7CFC00;

    private LivingPageImpact() {
    }

    public static void resolve(ServerLevel level, ServerPlayer caster, LivingEntity target) {
        if (level == null || caster == null || target == null || !target.isAlive()) return;

        int markBefore = MarkManager.getLevel(target);

        int base = SpellDamageRegistry.livingPageImpactDamage(caster);
        if (base < 1) base = 1;

        DiceCombatEvents.aoeProcessing = true;
        try {
            target.hurt(ModDamageTypes.cardSpell(level, caster), (float) base);
        } finally {
            DiceCombatEvents.aoeProcessing = false;
        }

        com.merlinkitsune.astral_dice.network.ModNetwork.DamageNumberMessage.send(target, base, SPELL_DAMAGE_COLOR);

        MarkManager.apply(target);

        ModAttachments.setRinPages(caster, ModAttachments.getRinPages(caster) + 1);

        if (markBefore >= 3) {
            EffectCardPeriod.grantLivingPageCycleBonus(caster);
        }
    }
}
