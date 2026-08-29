package com.merlinkitsune.astral_dice.item.card;

import com.merlinkitsune.astral_dice.component.GameplayConstants;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 狂暴(功能效果牌):获得"狂暴"效果(攻击力 +3/层,受到任意伤害 +1/层,可叠 3 层,持续 3:00)。
 * 允许对其他玩家/生物使用(下蹲+右键对面前玩家,或直接点击实体)。
 */
public class BerserkCardItem extends BaseEffectCardItem {
    public BerserkCardItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean canUseOnOtherPlayers() {
        return true;
    }

    @Override
    protected boolean countsForCopy() {
        return true;
    }

    @Override
    protected String cardTypeId() {
        return "berserk";
    }

    @Override
    protected void applyEffect(Level level, Player user, LivingEntity applyTo, ItemStack stack) {
        var existing = applyTo.getEffect(ModEffects.BERSERK.get());
        int newAmp = existing != null
                ? Math.min(existing.getAmplifier() + 1, GameplayConstants.MAX_EFFECT_STACKS - 1) : 0;
        int newDuration = existing != null ? Math.max(existing.getDuration(), 3600) : 3600;
        applyTo.addEffect(new MobEffectInstance(ModEffects.BERSERK.get(), newDuration, newAmp, false, false, true));
    }
}
