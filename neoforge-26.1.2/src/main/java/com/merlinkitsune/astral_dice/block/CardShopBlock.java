package com.merlinkitsune.astral_dice.block;

import com.mojang.serialization.MapCodec;
import com.merlinkitsune.astral_dice.shop.CardShopOpener;
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
 * 卡牌商店方块：交互（1.21.1 = {@code useWithoutItem}）打开商店界面。
 *
 * <p>外观来自 Blockbench 模型 card_shop（包围盒 0..16 × 0..14.5 × 0..16，最高 14.5/16 格），
 * 不是满方块 ⇒ 必须覆写形状，并以 {@code noOcclusion()} 注册（见 ModBlocks），
 * 否则台面之上会残留隐形碰撞、相邻面被错误剔除。
 */
public class CardShopBlock extends HorizontalDirectionalBlock {

    public static final MapCodec<CardShopBlock> CODEC = simpleCodec(CardShopBlock::new);

    /**
     * 选中与碰撞共用的整盒形状。
     *
     * <p>高度取模型包围盒顶面 14.5/16：台面本体只到 14/16，但其上还叠着收银机/牌堆等小方块
     * （最高 14.5/16）——只包到 14/16 会让这些小件沦为「看得见、碰不到」的幽灵几何，
     * 包满一格又会凭空多出 1.5/16 格的隐形墙，故严格贴合包围盒。
     */
    private static final VoxelShape SHAPE = Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, 14.5D / 16.0D, 1.0D);

    public CardShopBlock(Properties properties) {
        super(properties);
        // 默认朝向朝北（放置时由 getStateForPlacement 覆盖）
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    public MapCodec<CardShopBlock> codec() {
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

    /** 选中/描边形状：与外观包围盒一致（1.21.1 里本方法是 protected） */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** 碰撞形状：与选中形状同源，玩家踩上台面时的高度与视觉吻合 */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            CardShopOpener.open(player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
