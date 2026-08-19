package com.merlinkitsune.astral_dice.item.card;

import com.merlinkitsune.astral_dice.component.GameplayConstants;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 岿然不动(功能效果牌):获得"岿然不动"效果(防御力 +6/层,可叠 3 层,持续 2:00)。
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
        var existing = applyTo.getEffect(ModEffects.UNWAVERING);
        int newAmp = existing != null
                ? Math.min(existing.getAmplifier() + 1, GameplayConstants.MAX_EFFECT_STACKS - 1) : 0;
        int newDuration = existing != null ? Math.max(existing.getDuration(), 2400) : 2400;
        applyTo.addEffect(new MobEffectInstance(ModEffects.UNWAVERING, newDuration, newAmp, false, false, true));
    }
}
