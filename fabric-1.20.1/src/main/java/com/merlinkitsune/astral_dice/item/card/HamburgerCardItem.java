package com.merlinkitsune.astral_dice.item.card;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 汉堡(治疗效果牌):使用后恢复自身最大生命值 40% 的血量。
 * 治疗类效果牌:使用后触发大当家立牌被动"养精蓄锐 +1 层"。
 */
public class HamburgerCardItem extends BaseEffectCardItem {
    /** 恢复比例 */
    public static final float HEAL_RATIO = 0.4f;

    public HamburgerCardItem(Properties properties) {
        super(properties);
    }
    @Override
    protected String cardTypeId() {
        return "hamburger";
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