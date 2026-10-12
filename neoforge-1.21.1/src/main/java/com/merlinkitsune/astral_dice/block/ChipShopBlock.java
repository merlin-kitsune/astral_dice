package com.merlinkitsune.astral_dice.block;

import com.mojang.serialization.MapCodec;
import com.merlinkitsune.astral_dice.shop.ChipShopOpener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 筹码商店方块：交互（1.21.1 = {@code useWithoutItem}）打开筹码商店界面。
 *
 * <p>结构与 {@link CardShopBlock} 同式（四向 FACING + codec + 形状覆写），差别只在打开的界面不同
 * —— 这里走 {@link ChipShopOpener#open(Player)}，它自己会处理「池已全拥有 / 物品栏已满」两种拒绝。
 *
 * <p><b>形状</b>：模型（{@code models/block/chip_shop.json}）底座铺满 0..16 × 0..13 × 0..16，
 * 其上的装饰件最高到 **17/16**（超出方块上表面）、最后一件到 z=16.25 ⇒ 无法用「贴合包围盒」的盒子表达
 * （而超出上表面的那 1/16 像素本就看不见碰撞差异）。故此处直接用**满格盒**：既不会漏掉底座，
 * 也不会凭空多出「看得见碰不到的幽灵几何」——那正是 CardShopBlock 当初要贴合 14.5/16 的理由，
 * 在本模型上不适用（满格盒仍然只到 16/16，超出的装饰属于视觉溢出，见模型 elements[4]）。
 */
public class ChipShopBlock extends HorizontalDirectionalBlock {

    public static final MapCodec<ChipShopBlock> CODEC = simpleCodec(ChipShopBlock::new);

    /** 选中与碰撞共用的形状：满格盒（理由见类注释——模型装饰件高过方块上表面，贴合无意义） */
    private static final VoxelShape SHAPE = Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);

    public ChipShopBlock(Properties properties) {
        super(properties);
        // 默认朝向朝北（放置时由 getStateForPlacement 覆盖）
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    public MapCodec<ChipShopBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** 选中/描边形状（1.21.1 里本方法是 protected） */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** 碰撞形状：与选中形状同源 */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            ChipShopOpener.open(player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
