package com.merlinkitsune.astral_dice.block;

import com.merlinkitsune.astral_dice.shop.ChipShopOpener;
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
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 筹码商店方块（放置后右键开店；判据正本 {@code docs/chip-shop-logic-plan.md} §5「方块入口」）。
 *
 * <p><b>形状与朝向</b>：满格碰撞盒（模型是装饰件、高过 17/16，贴合无意义 —— 与 1.21.1 蓝本一致）
 * + 四向水平朝向（{@code FACING}：north/east/south/west 由 blockstate 的模型旋转给出）。
 *
 * <p><b>平台差异（forge-1.20.1）</b>：1.21.1 有方块编解码体系（{@code MapCodec<ChipShopBlock> CODEC} +
 * {@code codec()} 覆写）与 {@code useWithoutItem} 交互覆写点；**1.20.1 两样都没有** ⇒ 本线不写 codec，
 * 交互覆写六参 {@code use(...)} 再委托到同签名的 {@code useWithoutItem(...)}（见
 * {@code docs/compat-1.20.1-forge.md} 第十七节第 1 条）。
 */
public class ChipShopBlock extends HorizontalDirectionalBlock {

    private static final VoxelShape SHAPE = Shapes.box(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);

    public ChipShopBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    // 平台差异：1.20.1 里本方法族是 public（1.21.1 起改成 protected）—— 照抄本线 block/CardShopBlock 的可见性
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /**
     * 方块交互（1.20.1 的**六参** {@code use}）：本方法只做委托，逻辑本体在 {@link #useWithoutItem}。
     *
     * <p><b>平台差异（forge-1.20.1）</b>：1.21.1 的交互入口是 {@code useWithoutItem}（1.21 原版新增），
     * 1.20.1 尚无该方法 ⇒ 按本线既有 {@code block/CardShopBlock} 的写法覆写六参 {@code use(...)}，
     * 再委托到同名同签名的 {@code useWithoutItem(...)}（1.20.1 里它是**普通方法**、不是覆写点，
     * 因此**不是死代码**：{@code use} 真的调用它）。两线开店语义完全一致（都调 {@code ChipShopOpener.open(player)}）。
     */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hitResult) {
        return useWithoutItem(state, level, pos, player, hitResult);
    }

    /** 开店本体（服务端权威：{@link ChipShopOpener#open(Player)} 内部先判「是否客户端」再校验/补抽/开店）。 */
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            ChipShopOpener.open(player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
