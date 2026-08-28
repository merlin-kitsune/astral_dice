package com.merlinkitsune.astral_dice.item.card;

import com.merlinkitsune.astral_dice.event.EffectTimerGuard;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 加急加快(效果牌):可以对自身和其他玩家使用(对目标玩家下蹲右键,或下蹲+右键面前玩家)。
 * 使用后:使目标获得 迅捷 II 1:00。
 */
public class ExpressDeliveryCardItem extends BaseEffectCardItem {
    /** 迅捷效果时长(tick) */
    public static final int DURATION_TICKS = 1200;

    public ExpressDeliveryCardItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean canUseOnOtherPlayers() {
        return true;
    }

    @Override
    protected void applyEffect(Level level, Player user, LivingEntity applyTo, ItemStack stack) {
        EffectTimerGuard.apply(applyTo, new MobEffectInstance(MobEffects.MOVEMENT_SPEED, DURATION_TICKS, 1, false, true));
    }
}
