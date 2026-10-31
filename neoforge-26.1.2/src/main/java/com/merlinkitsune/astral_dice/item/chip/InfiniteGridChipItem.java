package com.merlinkitsune.astral_dice.item.chip;

import com.merlinkitsune.starengine.combat.PartyRelations;
import com.merlinkitsune.astral_dice.effect.ChargeEffect;
import com.merlinkitsune.astral_dice.item.CurioSlotUtil;
import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.List;

/**
 * 无线输电杆筹码(史诗 / 充能)。
 *
 * <ul>
 *   <li>效果牌出牌数 +1(固定来源,注册于 {@code EffectCardPeriod} 的固定出牌数来源,
 *       与大背包 / 忍术飞镖同款:佩戴即提供);</li>
 *   <li><b>每次获得充能时</b>,使自身周围 {@value #BROADCAST_RANGE} 格内的**其他**友方玩家各获得
 *       {@value #BROADCAST_STACKS} 点充能。</li>
 * </ul>
 *
 * <p><b>队伍口径</b>:装备者**有队伍** ⇒ 只发给半径内**同队**玩家;装备者**无队伍** ⇒ 发给半径内
 * **所有**其他玩家(与「银行卡-用不完 / 大碗炖肉」等友方发放口径一致,队伍判定统一走
 * {@link PartyRelations#hasTeam} / {@link PartyRelations#isSameTeam};半径过滤自算
 * {@code distanceToSqr <= 64})。
 *
 * <p><b>触发点</b>= {@code item/ChargeManager#addStacks}(获得充能的统一入口)在**层数实际增加**时回调
 * {@link #onChargeGained},与「消耗充能 → {@code EmpowerManager#onChargeConsumed}」互为对称。
 * 一次 {@code addStacks} 调用无论加几层都只发放一次。
 *
 * <p><b>防递归</b>:广播给他人时 ① 走 {@link ChargeEffect#addStacks} 直通道(不经 {@code ChargeManager}
 * ⇒ 不会再次进入本钩子)、② 另加 reentrancy 守卫 {@link #broadcasting}(纵深防御:即便将来有人
 * 把直通道改回 {@code ChargeManager},也不会"我加你、你加我"地链式广播)。
 */
public class InfiniteGridChipItem extends BaseChipItem {
    /** 广播半径(格),按三维距离判定。 */
    public static final int BROADCAST_RANGE = 8;

    /** 每次「获得充能」时向范围内友方玩家发放的充能层数。 */
    public static final int BROADCAST_STACKS = 1;

    /** 广播链重入守卫(见类注释「防递归」)。 */
    private static boolean broadcasting = false;

    public InfiniteGridChipItem(Properties properties) {
        super(properties);
    }

    /** 玩家是否佩戴本筹码(总闸门:未佩戴骰子 ⇒ 本件功能一律不生效)。 */
    public static boolean isEquipped(Player player) {
        if (player == null) return false;
        if (!CurioSlotUtil.hasDiceEquipped(player)) return false;
        var curios = CuriosApi.getCuriosInventory(player);
        return curios.isPresent()
                && curios.get().findFirstCurio(s -> s.is(ModItems.INFINITE_GRID_CHIP.get())).isPresent();
    }

    /**
     * 「获得充能」回调(由 {@code ChargeManager#addStacks} 在**层数实际增加**时调用)。
     *
     * @param gained 本次实际增加的层数(≥ 1;一次调用只发放一次,与层数多少无关)
     */
    public static void onChargeGained(Player player, int gained) {
        if (gained <= 0) return;
        if (player == null || player.level().isClientSide()) return;
        if (broadcasting) return;
        if (!isEquipped(player)) return;
        if (!(player.level() instanceof ServerLevel serverLevel)) return;
        List<ServerPlayer> targets = collectTargets(serverLevel, player);
        if (targets.isEmpty()) return;
        broadcasting = true;
        try {
            for (ServerPlayer target : targets) {
                // 直通道(ChargeEffect):不经 ChargeManager ⇒ 不会再次触发本钩子(见类注释「防递归」)
                ChargeEffect.addStacks(target, BROADCAST_STACKS);
            }
        } finally {
            broadcasting = false;
        }
    }

    /** 半径内「其他友方玩家」:有队伍 ⇒ 仅同队;无队伍 ⇒ 范围内所有其他玩家。 */
    private static List<ServerPlayer> collectTargets(ServerLevel level, Player self) {
        double rangeSqr = (double) BROADCAST_RANGE * BROADCAST_RANGE;
        boolean selfHasTeam = PartyRelations.hasTeam(self);
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer other : level.players()) {
            if (other == self) continue;
            if (other.distanceToSqr(self) > rangeSqr) continue;
            if (selfHasTeam && !PartyRelations.isSameTeam(self, other)) continue;
            out.add(other);
        }
        return out;
    }
}
