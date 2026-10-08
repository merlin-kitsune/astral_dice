package com.merlinkitsune.astral_dice.block;

import com.merlinkitsune.astral_dice.shop.CardShopOpener;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 卡牌商店方块：交互（1.20.1 = {@code use(BlockState, Level, BlockPos, Player, InteractionHand, BlockHitResult)}）
 * 打开商店界面。
 *
 * <p>平台差异：1.21.1 覆写的是 {@code useWithoutItem(state, level, pos, player, hitResult)}；
 * 1.20.1 尚无该方法，交互入口只有六参的 {@code use(...)}（多一个 {@code InteractionHand} 参数）。
 */
public class CardShopBlock extends Block {

    public CardShopBlock(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            CardShopOpener.open(player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
