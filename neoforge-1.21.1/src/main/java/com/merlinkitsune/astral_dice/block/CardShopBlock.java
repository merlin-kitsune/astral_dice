package com.merlinkitsune.astral_dice.block;

import com.merlinkitsune.astral_dice.shop.CardShopOpener;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** 卡牌商店方块：交互（1.21.1 = {@code useWithoutItem}）打开商店界面 */
public class CardShopBlock extends Block {

    public CardShopBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            CardShopOpener.open(player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
