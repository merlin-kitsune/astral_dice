package com.merlinkitsune.astral_dice.platform;

import com.merlinkitsune.astral_dice.api.combat.AttackPowerModifier;
import com.merlinkitsune.astral_dice.api.combat.DefensePowerModifier;
import com.merlinkitsune.astral_dice.api.combat.DiceCombatFactor;
import com.merlinkitsune.astral_dice.api.combat.ExtraDamageModifier;
import com.merlinkitsune.astral_dice.api.combat.SpellDamageMatcher;
import com.merlinkitsune.astral_dice.api.combat.SpellDamageModifier;
import com.merlinkitsune.astral_dice.api.combat.VictimDamageModifier;
import com.merlinkitsune.astral_dice.api.playerlike.PlayerLikeHandler;
import com.merlinkitsune.astral_dice.combat.CombatApiAdapters;
import com.merlinkitsune.astral_dice.api.playerlike.PlayerLikeTargetsAdapter;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.item.sign.BaseSignItem;
import com.merlinkitsune.astral_dice.target.PlayerLikeTargets;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** forge 平台实现（2026-11-18 门面桥接：类玩家注册 / 立牌激活 / 骰子祝福）。 */
public final class ForgeAstralDicePlatform implements AstralDicePlatform {

    @Override
    public String id() {
        return "forge";
    }

    @Override
    public void registerPlayerLike(String namespace, String path, PlayerLikeHandler handler) {
        PlayerLikeTargets.register(new ResourceLocation(namespace, path), new PlayerLikeTargetsAdapter(handler));
    }

    @Override
    public boolean activateSign(ItemStack stack, LivingEntity holder) {
        if (stack == null || holder == null) {
            return false;
        }
        if (!(stack.getItem() instanceof BaseSignItem)) {
            return false;
        }
        return BaseSignItem.activateFor(holder);
    }

    @Override
    public boolean hasDiceBlessing(LivingEntity entity) {
        return entity != null && entity.hasEffect(ModEffects.DICE_BLESSING.get());
    }

    // ------------------------------------------------------ B2 战斗注册口

    @Override
    public void registerAttackModifier(AttackPowerModifier modifier) {
        CombatApiAdapters.registerAttackModifier(modifier);
    }

    @Override
    public void registerDefenseModifier(DefensePowerModifier modifier) {
        CombatApiAdapters.registerDefenseModifier(modifier);
    }

    @Override
    public void registerExtraDamageModifier(ExtraDamageModifier modifier) {
        CombatApiAdapters.registerExtraDamageModifier(modifier);
    }

    @Override
    public void registerVictimDamageModifier(VictimDamageModifier modifier) {
        CombatApiAdapters.registerVictimDamageModifier(modifier);
    }

    @Override
    public void registerDiceCombatFactor(DiceCombatFactor modifier) {
        CombatApiAdapters.registerDiceCombatFactor(modifier);
    }

    @Override
    public void registerSpellDamageMatcher(SpellDamageMatcher modifier) {
        CombatApiAdapters.registerSpellDamageMatcher(modifier);
    }

    @Override
    public void registerSpellDamageModifier(SpellDamageModifier modifier) {
        CombatApiAdapters.registerSpellDamageModifier(modifier);
    }
}

