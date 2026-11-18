package com.merlinkitsune.astral_dice.api.playerlike;

import com.merlinkitsune.astral_dice.target.PlayerLikeTargets;
import java.util.Objects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * 把门面的 {@link PlayerLikeHandler} 桥接成内部 {@code PlayerLikeTargets.Handler}。
 *
 * <p>仅供各线平台实现调用（{@code AstralDicePlatform#registerPlayerLike}），附属模组不要直接使用。
 */
public final class PlayerLikeTargetsAdapter implements PlayerLikeTargets.Handler {

    private final PlayerLikeHandler delegate;

    public PlayerLikeTargetsAdapter(PlayerLikeHandler delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public boolean matches(LivingEntity entity) {
        return delegate.matches(entity);
    }

    @Override
    public boolean giveCard(ServerPlayer giver, LivingEntity receiver, ItemStack card) {
        return delegate.giveCard(giver, receiver, card);
    }

    @Override
    public boolean giveItem(LivingEntity receiver, ItemStack stack) {
        return delegate.giveItem(receiver, stack);
    }

    @Override
    public boolean supportsSigns() {
        return delegate.supports(PlayerLikeCapability.SIGNS);
    }

    @Override
    public boolean supportsChips() {
        return delegate.supports(PlayerLikeCapability.CHIPS);
    }
}
