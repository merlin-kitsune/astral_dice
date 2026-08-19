package com.merlinkitsune.astral_dice.item.card;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 巧克力蛋糕(治疗效果牌):使用后恢复 4 点生命值。
 * 治疗类效果牌:使用后触发大当家立牌被动"养精蓄锐 +1 层"。
 */
public class ChocolateCakeCardItem extends BaseEffectCardItem {
    /** 恢复的生命值 */
    public static final int HEAL_AMOUNT = 4;

    public ChocolateCakeCardItem(Properties properties) {
        super(properties);
    }

    @Override
    protected boolean isHealingCard() {
        return true;
    }

    @Override
    protected void applyEffect(Level level, Player user, LivingEntity applyTo, ItemStack stack) {
        applyTo.heal(HEAL_AMOUNT);
    }
}
