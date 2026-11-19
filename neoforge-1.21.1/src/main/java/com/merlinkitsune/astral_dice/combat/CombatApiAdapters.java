package com.merlinkitsune.astral_dice.combat;

import com.merlinkitsune.starengine.api.astral.combat.DiceBattleView;
import com.merlinkitsune.starengine.api.astral.combat.SpellDamageView;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * B2 战斗注册口的每线适配器：把门面 {@code api.combat} 的只读视图与函数式接口，
 * 适配到本线的内部上下文类型（{@link DiceCombatContext}、{@link SpellDamageContext}）。
 *
 * <p>这两个内部类型每个加载器线各有一份（挂着各自的加载器事件），{@code common} 无法引用，
 * 所以适配器必须每线一份。五份内容一致、只依赖五线同名同签名的内部接口——改动请同时改五份。
 *
 * <p>注册时机：与内部注册相同，只在初始化阶段（内容冻结前）注册。
 */
public final class CombatApiAdapters {

    private CombatApiAdapters() {
    }

    public static void registerAttackModifier(com.merlinkitsune.starengine.api.astral.combat.AttackPowerModifier modifier) {
        DiceCombatModifiers.registerAttackModifier((ctx, attackPower) -> modifier.apply(view(ctx), attackPower));
    }

    public static void registerDefenseModifier(com.merlinkitsune.starengine.api.astral.combat.DefensePowerModifier modifier) {
        DiceCombatModifiers.registerDefenseModifier((ctx, defensePower) -> modifier.apply(view(ctx), defensePower));
    }

    public static void registerExtraDamageModifier(com.merlinkitsune.starengine.api.astral.combat.ExtraDamageModifier modifier) {
        DiceCombatModifiers.registerExtraDamageModifier(ctx -> modifier.extraDamage(view(ctx)));
    }

    public static void registerVictimDamageModifier(com.merlinkitsune.starengine.api.astral.combat.VictimDamageModifier modifier) {
        DiceCombatModifiers.registerVictimDamageModifier((victim, source) -> modifier.multiplier(victim, source));
    }

    public static void registerDiceCombatFactor(com.merlinkitsune.starengine.api.astral.combat.DiceCombatFactor factor) {
        DiceCombatEvents.registerDiceCombatFactor((attacker, target, damage) -> factor.modify(attacker, target, damage));
    }

    public static void registerSpellDamageMatcher(com.merlinkitsune.starengine.api.astral.combat.SpellDamageMatcher matcher) {
        SpellDamageRegistry.registerMatcher((source, direct) -> matcher.matches(source, direct));
    }

    public static void registerSpellDamageModifier(com.merlinkitsune.starengine.api.astral.combat.SpellDamageModifier modifier) {
        SpellDamageRegistry.registerModifier(new SpellDamageModifier() {
            @Override
            public boolean isActive(SpellDamageContext ctx) {
                return modifier.isActive(spellView(ctx));
            }

            @Override
            public double apply(SpellDamageContext ctx, double bonus) {
                return modifier.apply(spellView(ctx), bonus);
            }

            @Override
            public void onHit(SpellDamageContext ctx, double bonus) {
                modifier.onHit(spellView(ctx), bonus);
            }
        });
    }

    private static DiceBattleView view(DiceCombatContext ctx) {
        int starLevel = ctx.enhancement == null ? 0 : ctx.enhancement.starLevel();
        return new DiceBattleView(ctx.attacker, ctx.target, ctx.baseDice, ctx.diceStack, starLevel,
                ctx.triggeredBlessing, ctx.misakiBurst, ctx.misakiStar, ctx.misakiStacks);
    }

    private static SpellDamageView spellView(SpellDamageContext ctx) {
        return new SpellDamageView(ctx.attacker, ctx.target, ctx.source, ctx.directEntity);
    }
}
