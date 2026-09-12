package com.merlinkitsune.astral_dice.item.chip;

import com.merlinkitsune.starenginelib.event.EventTargetCollector;
import com.merlinkitsune.starenginelib.item.CuriosCompat;
import com.merlinkitsune.astral_dice.item.HealingManager;
import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.monster.Strider;
import net.minecraft.world.entity.player.Player;

/**
 * 大碗炖肉筹码:骰神赐福效果结束后,使 16 格范围内所有友方目标获得 1 点治愈并恢复 2 点生命值
 * (由 {@link #onBlessingEnd} 在赐福结束时调用)。
 *
 * <p>友方判定:
 * <ul>
 *   <li>玩家:自身 + 队友(已加入队伍时 = 同队在线玩家;未加入任何队伍时 = 全服在线玩家,经
 *       {@link EventTargetCollector#collectTeamPlayers}),且距离不超过 {@link #RANGE} 格;
 *       玩家获得治愈点数并回血。</li>
 *   <li>非玩家友方(玩家驯服的宠物、可骑乘生物):同样在 {@link #RANGE} 格内则恢复 2 点生命值
 *       (治愈点数是玩家级资源,不适用于生物)。</li>
 * </ul>
 * 筹码拥有者已死亡(死亡清场)时不发放。
 */
public class BigBowlStewChipItem extends BaseChipItem {
    /** 作用范围(格) */
    public static final double RANGE = 16.0;
    /** 赐福结束后给予的治愈点数 */
    public static final int HEALING_POINTS = 1;
    /** 赐福结束后恢复的生命值(♥) */
    public static final float HEAL_AMOUNT = 2f;

    public BigBowlStewChipItem(Properties properties) {
        super(properties);
    }

    // 玩家是否佩戴本筹码
    public static boolean isEquipped(Player player) {
        if (player == null) return false;
        var curios = CuriosCompat.getCuriosInventory(player);
        return curios.isPresent() && curios.get().findFirstCurio(s -> s.is(ModItems.BIG_BOWL_STEW_CHIP.get())).isPresent();
    }

    /**
     * 骰神赐福结束时调用:范围内友方玩家 +{@link #HEALING_POINTS} 治愈、恢复 {@link #HEAL_AMOUNT} 生命值;
     * 范围内非玩家友方恢复 {@link #HEAL_AMOUNT} 生命值。
     */
    public static void onBlessingEnd(Player player) {
        if (player.level().isClientSide()) return;
        if (!isEquipped(player)) return;
        if (player.isDeadOrDying()) return;
        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        double rangeSqr = RANGE * RANGE;
        java.util.List<Player> allies = EventTargetCollector.collectTeamPlayers(player);
        for (ServerPlayer sp : serverLevel.players()) {
            if (sp != player && !allies.contains(sp)) continue;
            if (sp.distanceToSqr(player) > rangeSqr) continue;
            HealingManager.add(sp, HEALING_POINTS);
            sp.heal(HEAL_AMOUNT);
        }

        // 非玩家友方(驯服宠物/可骑乘生物):仅回血(治愈点数为玩家级资源)
        for (LivingEntity entity : serverLevel.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(RANGE),
                e -> !(e instanceof Player) && isFriendlyMob(e, player))) {
            entity.heal(HEAL_AMOUNT);
        }
    }

    // 判定非玩家友方目标:玩家驯服的宠物、可骑乘生物(与史莱姆立牌主动的治疗目标一致)
    private static boolean isFriendlyMob(LivingEntity entity, Player owner) {
        if (entity instanceof TamableAnimal tame && tame.isOwnedBy(owner)) return true;
        return entity instanceof AbstractHorse
                || entity instanceof Pig
                || entity instanceof Strider
                || entity instanceof Camel;
    }
}
