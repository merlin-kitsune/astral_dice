package com.merlinkitsune.astral_dice.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
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
 * 卡牌商店方块（注册 id {@code astral_dice:card_shop}）：右键打开卡牌商店界面。
 *
 * <p>2026-11-01 移植自 forge-1.20.1 同路径源码（1:1）。
 *
 * <h2>fabric 等价实现（差异说明）</h2>
 * <p>1.20.1 的两侧 API 完全相同，方块本体无平台差异；**打开界面**这一步也与 forge 逐字一致：
 * {@link #openShop(Player)} 直接调 {@code shop.CardShopOpener#open(Player)}（B1-CARDSHOP 批次已移植该入口）。
 * 菜单实例经由 {@code ModMenuTypes.CARD_SHOP} 注册的 {@code MenuType} 由原版 openMenu 走网络同步，
 * 与原版容器菜单同一条通道 ⇒ fabric 侧无额外 payload。
 *
 * <p>碰撞箱高度 14.5/16 而非满格：上方留出视觉空隙，配合 {@code noOcclusion} 让方块顶面贴图透出。
 */
public class CardShopBlock extends HorizontalDirectionalBlock {

    /** 14.5/16 高：比满格矮一点，贴合卡牌商店模型的视觉高度。 */
    private static final VoxelShape SHAPE = Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, 14.5D / 16.0D, 1.0D);

    public CardShopBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    /** 朝向：玩家放置时面朝玩家（与水平朝向方块一致的口径）。 */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                        CollisionContext context) {
        return SHAPE;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            openShop(player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    /**
     * 打开卡牌商店界面（forge-1.20.1 原样调用）。
     *
     * <p>方块交互是「每批至多一次」的封堵口径 ⇒ 走 {@code open(player)}（内部转
     * {@code open(player, false)}）；立牌主动技能「网络购物」走 {@code open(player, true)}，两者互斥。
     */
    private static void openShop(Player player) {
        com.merlinkitsune.astral_dice.shop.CardShopOpener.open(player);
    }
}
