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

        // ⚠️ 跳字**必须先于 hurt 发送**：法伤修饰器链（DamageEffectCardHandler）在 hurt 内会以
        // 「完整伤害（基础 + 法伤加成）」再发一次跳字，而客户端跳字是 Map<entityId, 数值> 的**单槽覆盖**
        // （见 starengine_lib 的 ClientDamageNumbers）⇒ **后到的包胜出**。若本行放在 hurt 之后，
        // 它会把链内那次带加成的正确数字**覆盖回基础值**，玩家永远看不到忍术飞镖 / 贯穿之铳的加成
        // （掉血本身是对的，只有显示错）—— 2026-09-24 用户实测「活体书页不吃标记伤害加成」即此。
        // 放在 hurt 之前 = 无加成时它就是最终值，有加成时被链内那次覆盖 ⇒ 两种情况都正确。
        com.merlinkitsune.astral_dice.network.ModNetwork.DamageNumberMessage.send(target, base, SPELL_DAMAGE_COLOR);

        DiceCombatEvents.aoeProcessing = true;
        try {
            target.hurt(ModDamageTypes.cardSpell(level, caster), (float) base);
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
