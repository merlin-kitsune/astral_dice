package com.merlinkitsune.astral_dice.item.card;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import com.merlinkitsune.astral_dice.item.chip.FriendshipBadgeChipItem;

/**
 * 奢华大餐(治疗效果牌):可以对自身和其他玩家使用(对目标玩家下蹲右键,或下蹲+右键面前玩家)。
 * 使用后:治疗自身及周围 6 格范围内同队玩家(若自身无队伍则治疗范围内所有玩家,无队伍玩家也默认被治疗),
 * 各恢复自身最大生命值 30% 的血量。
 * 治疗类效果牌:使用后触发大当家立牌被动"养精蓄锐 +1 层"。
 */
public class LuxuryFeastCardItem extends BaseEffectCardItem {
    /** 恢复比例 */
    public static final float HEAL_RATIO = 0.3f;
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
        int heal = Math.max(1, (int) (user.getMaxHealth() * HEAL_RATIO));
        AABB aabb = applyTo.getBoundingBox().inflate(RANGE);
        var nearby = level.getEntitiesOfClass(Player.class, aabb, p -> p.isAlive());
        for (Player p : nearby) {
            if (p == user || user.getTeam() == null || p.getTeam() == null || p.getTeam() == user.getTeam()) {
                p.heal(heal);
                // 友情徽章:对友方玩家施加治疗时,双方各获得 2 点治愈
                if (p != user) {
                    FriendshipBadgeChipItem.onHealApplied(user, p);
                }
            }
        }
    }
}