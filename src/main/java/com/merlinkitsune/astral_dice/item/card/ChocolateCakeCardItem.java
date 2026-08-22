package com.merlinkitsune.astral_dice.item.card;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 巧克力蛋糕(治疗效果牌):使用后恢复自身最大生命值 20% 的血量。
 * 治疗类效果牌:使用后触发大当家立牌被动"养精蓄锐 +1 层"。
 */
public class ChocolateCakeCardItem extends BaseEffectCardItem {
    /** 恢复比例 */
    public static final float HEAL_RATIO = 0.2f;

    public ChocolateCakeCardItem(Properties properties) {
        super(properties);
    }

    @Override
    protected boolean isHealingCard() {
        return true;
    }

    @Override
    protected void applyEffect(Level level, Player user, LivingEntity applyTo, ItemStack stack) {
        applyTo.heal(Math.max(1, (int) (applyTo.getMaxHealth() * HEAL_RATIO)));
    }
}