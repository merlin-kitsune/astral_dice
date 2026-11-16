package com.merlinkitsune.astral_dice.compat.playerlike;

import com.merlinkitsune.astral_dice.target.PlayerLikeTargets;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * 能力门面：把「这个实体能不能做玩家那件事」收敛到一处。
 *
 * <p>三种实体：
 * <ul>
 *   <li><b>原版玩家</b>：全部能力恒真；</li>
 *   <li><b>已注册的类玩家生物</b>（{@link PlayerLikeTargets#register}）：能力位由注册方的
 *       {@link PlayerLikeTargets.Handler} 决定（默认全关 ⇒ 只具备「被选为目标 / 收卡」）；</li>
 *   <li><b>未注册实体</b>：全部为假，任何路径都退回原有的玩家专有行为。</li>
 * </ul>
 */
public final class PlayerLikeAbilities {

    private PlayerLikeAbilities() {
    }

    public static boolean canEquipSigns(LivingEntity entity) {
        return entity != null && PlayerLikeTargets.supports(entity, PlayerLikeTargets.Capability.SIGNS);
    }

    public static boolean canEquipChips(LivingEntity entity) {
        return entity != null && PlayerLikeTargets.supports(entity, PlayerLikeTargets.Capability.CHIPS);
    }

    public static boolean canUseShop(LivingEntity entity) {
        return entity != null && PlayerLikeTargets.supports(entity, PlayerLikeTargets.Capability.SHOP);
    }

    /** 是否有可用的星币账户（玩家恒有）。 */
    public static boolean hasWallet(LivingEntity entity) {
        PlayerLikeWallet wallet = PlayerLikeWallets.resolve(entity);
        return wallet != null && wallet.isAvailable();
    }

    public static PlayerLikeWallet wallet(LivingEntity entity) {
        return PlayerLikeWallets.resolve(entity);
    }

    /** 发一张卡（玩家走唯一漏斗，注册生物走 handler）。 */
    public static boolean giveCard(ServerPlayer giver, LivingEntity receiver, ItemStack card) {
        return PlayerLikeTargets.giveCard(giver, receiver, card);
    }

    /** 把一件物品交给该实体（筹码/奖励）；返回是否已交付，{@code false} 时调用方负责掉地。 */
    public static boolean giveItem(LivingEntity receiver, ItemStack stack) {
        return receiver != null && PlayerLikeTargets.giveItem(receiver, stack);
    
}
    /**
     * 统一交付入口（2026-11-09 B4）：注册生物走 {@link PlayerLikeTargets#giveItem}，
     * 失败再兜底掉地；玩家请用各自的背包 API。返回 {@code true} 表示物品已离开我们手上。
     */
    public static boolean deliverLikePlayer(net.minecraft.world.entity.LivingEntity receiver,
                                            net.minecraft.world.item.ItemStack stack) {
        if (receiver == null || stack == null || stack.isEmpty()) return false;
        if (giveItem(receiver, stack)) return true;
        if (receiver.level() == null || receiver.level().isClientSide()) return false;
        receiver.spawnAtLocation(stack);
        return true;
    }
}
