package com.merlinkitsune.astral_dice.combat;

import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.damage.ModDamageTypes;
import com.merlinkitsune.astral_dice.item.MarkManager;
import com.merlinkitsune.astral_dice.item.card.EffectCardPeriod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

public final class LivingPageImpact {
    public static final int BASE_DAMAGE = 2;

    // ⚠️ 原有的 SPELL_DAMAGE_COLOR 常量已于 2026-09-26 迁走:跳数字配色现在**唯一**由
    // combat/DamageNumberAggregator(SPELL_COLOR / ATTACK_COLOR)持有,避免两处真理。

    private LivingPageImpact() {
    }

    public static void resolve(ServerLevel level, ServerPlayer caster, LivingEntity target) {
        if (level == null || caster == null || target == null || !target.isAlive()) return;

        int markBefore = MarkManager.getLevel(target);

        int base = SpellDamageRegistry.livingPageImpactDamage(caster);
        if (base < 1) base = 1;

        // ⚠️ 2026-09-26 起本处**不再自行发包** —— 跳数字统一由 combat/DamageNumberAggregator 在
        // LivingDamageEvent.Post 上取「实际扣血量(伤害管线终值)」,并与同 tick 的法伤加成
        // (astral_dice:true_damage,见 event/DamageEffectCardHandler)合并成一个**总值**后一次性下发。
        // 旧实现是「先发 base、再指望被链内那次带加成的包覆盖」(客户端按 entityId 单槽、后到覆盖先到),
        // 正确性挂在两处发包的先后顺序上;现在由聚合器结构性保证。
        DiceCombatEvents.aoeProcessing = true;
        try {
            DamageSource spellSource = ModDamageTypes.cardSpell(level, caster);
            // 组别:astral_dice:card_spell 即使不登记也会走**类型回落**判为法伤类(绿),
            // 此处显式登记只为让调用点的意图可读。
            DamageNumberAggregator.tag(target, spellSource, DamageNumberAggregator.Group.SPELL);
            target.hurt(spellSource, (float) base);
        } finally {
            DiceCombatEvents.aoeProcessing = false;
        }

        MarkManager.apply(target);

        ModAttachments.setRinPages(caster, ModAttachments.getRinPages(caster) + 1);

        if (markBefore >= 3) {
            EffectCardPeriod.grantLivingPageCycleBonus(caster);
        }
    }
}
