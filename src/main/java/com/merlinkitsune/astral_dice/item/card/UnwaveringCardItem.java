package com.merlinkitsune.astral_dice.item.card;

import com.merlinkitsune.astral_dice.event.EffectTimerGuard;

import com.merlinkitsune.astral_dice.effect.ModEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 岿然不动(功能效果牌):使用后防御力 +2,并获得 抗性提升 II,持续 1:00。
 */
public class UnwaveringCardItem extends BaseEffectCardItem {
    public UnwaveringCardItem(Properties properties) {
        super(properties);
    }

    @Override
    protected boolean countsForCopy() {
        return true;
    }

    @Override
    protected String cardTypeId() {
        return "unwavering";
    }

    @Override
    protected void applyEffect(Level level, Player user, LivingEntity applyTo, ItemStack stack) {
        applyTo.addEffect(new MobEffectInstance(ModEffects.UNWAVERING, 1200, 0, false, false, true));
        EffectTimerGuard.apply(applyTo, new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 1200, 1, false, true));
    }
}
