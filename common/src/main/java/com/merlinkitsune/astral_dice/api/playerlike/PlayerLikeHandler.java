package com.merlinkitsune.astral_dice.api.playerlike;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * 类玩家生物处理器（门面层类型，替代直接实现内部的 {@code PlayerLikeTargets.Handler}）。
 *
 * <p>通过 {@code AstralDiceApi.registerPlayerLike(String, PlayerLikeHandler)} 注册。
 * 注册只影响"哪些生物被当成类玩家"，不会给它们发账户：钱包见
 * {@link PlayerLikeWallet} 与 {@code AstralDiceApi.registerPlayerLikeWallet}。
 */
public interface PlayerLikeHandler {

    /** 该实体是否由本处理器接管。 */
    boolean matches(LivingEntity entity);

    /** 把一张卡牌发给该实体；返回是否成功（失败不得留下半份物品）。 */
    boolean giveCard(ServerPlayer giver, LivingEntity receiver, ItemStack card);

    /** 发一件普通物品；默认不支持（调用方自行掉落）。 */
    default boolean giveItem(LivingEntity receiver, ItemStack stack) {
        return false;
    }

    /** 能力位；默认支持立牌与筹码，钱包由钱包注册表单独判定。 */
    default boolean supports(PlayerLikeCapability capability) {
        return capability == PlayerLikeCapability.SIGNS || capability == PlayerLikeCapability.CHIPS;
    }
}
