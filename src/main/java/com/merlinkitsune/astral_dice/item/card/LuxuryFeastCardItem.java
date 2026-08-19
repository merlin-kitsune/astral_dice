package com.merlinkitsune.astral_dice.item.card;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * 奢华大餐(治疗效果牌):可以对自身和其他玩家使用(对目标玩家下蹲右键,或下蹲+右键面前玩家)。
 * 使用后:治疗目标及周围 6 格范围内所有玩家,各恢复 6 点生命值。
 * 治疗类效果牌:使用后触发大当家立牌被动"养精蓄锐 +1 层"。
 */
public class LuxuryFeastCardItem extends BaseEffectCardItem {
    /** 恢复的生命值 */
    public static final int HEAL_AMOUNT = 6;
    /** 治疗扩散半径(格) */
    public static final double RANGE = 6.0;

    public LuxuryFeastCardItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean canUseOnOtherPlayers() {
        return true;
    }

    @Override
    protected boolean isHealingCard() {
        return true;
    }

    @Override
    protected void applyEffect(Level level, Player user, LivingEntity applyTo, ItemStack stack) {
        // 治疗目标及其周围 6 格内所有玩家
        AABB aabb = applyTo.getBoundingBox().inflate(RANGE);
        var nearby = level.getEntitiesOfClass(Player.class, aabb, p -> p.isAlive());
        for (Player p : nearby) {
            p.heal(HEAL_AMOUNT);
        }
    }
}
