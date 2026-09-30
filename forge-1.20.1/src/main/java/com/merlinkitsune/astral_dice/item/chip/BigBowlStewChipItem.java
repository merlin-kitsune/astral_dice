package com.merlinkitsune.astral_dice.item.chip;

import com.merlinkitsune.starenginelib.event.EventTargetCollector;
import com.merlinkitsune.starenginelib.item.CuriosCompat;
import com.merlinkitsune.astral_dice.item.HealingManager;
import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import com.merlinkitsune.astral_dice.combat.PartyRelations;

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
    /** 赐福结束后给予的治愈点数(2026-09-28 用户裁决:1 → 2) */
    public static final int HEALING_POINTS = 2;
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
     * 骰神赐福结束时调用:使**自身和** {@link #RANGE} 格范围内所有友方玩家各获得
     * {@link #HEALING_POINTS} 层治愈、恢复 {@link #HEAL_AMOUNT} 点生命值。
     *
     * <p>2026-09-28 用户裁决:移除对**友方生物**的治疗(生物不在作用范围内)。
     */
    public static void onBlessingEnd(Player player) {
        if (player.level().isClientSide()) return;
        if (!isEquipped(player)) return;
        if (player.isDeadOrDying()) return;
        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        double rangeSqr = RANGE * RANGE;
        java.util.List<Player> allies = PartyRelations.collectTeamPlayers(player);
        for (ServerPlayer sp : serverLevel.players()) {
            // 自身无条件在列(用户口径:「使自身和 16 格范围内所有友方玩家」)
            if (sp != player && !allies.contains(sp)) continue;
            if (sp.distanceToSqr(player) > rangeSqr) continue;
            HealingManager.add(sp, HEALING_POINTS);
            sp.heal(HEAL_AMOUNT);
        }

    }

}
