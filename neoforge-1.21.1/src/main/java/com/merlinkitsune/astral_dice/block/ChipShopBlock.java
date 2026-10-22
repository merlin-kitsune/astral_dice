package com.merlinkitsune.astral_dice.block;

import com.mojang.serialization.MapCodec;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.network.ActionBarPayload;
import com.merlinkitsune.astral_dice.shop.ChipShopOpener;
import com.merlinkitsune.astral_dice.shop.ChipShopState;
import com.merlinkitsune.starengine.component.GameplayConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 筹码商店方块：空手 / 非空白筹码右键（1.21.1 = {@code useWithoutItem}）打开筹码商店界面；
 * 手持**空白筹码**右键（1.21.1 = {@link #useItemOn}）则投入一枚（需求 5）。
 *
 * <p>结构与 {@link CardShopBlock} 同式（四向 FACING + codec + 形状覆写），差别只在打开的界面不同
 * —— 这里走 {@link ChipShopOpener#open(Player)}，它自己会处理「池已全拥有 / 物品栏已满 / 购买冷却中」三种拒绝。
 *
 * <p><b>形状</b>：模型（{@code models/block/chip_shop.json}）底座铺满 0..16 × 0..13 × 0..16，
 * 其上的装饰件最高到 **17/16**（超出方块上表面）、最后一件到 z=16.25 ⇒ 无法用「贴合包围盒」的盒子表达
 * （而超出上表面的那 1/16 像素本就看不见碰撞差异）。故此处直接用**满格盒**：既不会漏掉底座，
 * 也不会凭空多出「看得见碰不到的幽灵几何」——那正是 CardShopBlock 当初要贴合 14.5/16 的理由，
 * 在本模型上不适用（满格盒仍然只到 16/16，超出的装饰属于视觉溢出，见模型 elements[4]）。
 */
public class ChipShopBlock extends HorizontalDirectionalBlock {

    public static final MapCodec<ChipShopBlock> CODEC = simpleCodec(ChipShopBlock::new);

    private static final Logger LOGGER = LoggerFactory.getLogger(ChipShopBlock.class);

    /** 空白筹码**投入**冷却时长：2400 ticks = 2:00（需求 5） */
    private static final long BLANK_INSERT_COOLDOWN_TICKS = 2400L;

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

    /**
     * 手持**空白筹码**右键方块 ⇒ 投入一枚（需求 5）。
     *
     * <p>非空白筹码一律回退 {@code super.useItemOn}（{@code PASS_TO_DEFAULT_BLOCK_INTERACTION}）
     * ⇒ 交给默认方块交互（即 {@link #useWithoutItem} 开店），保持原有右键行为不受影响。
     *
     * <p>空白筹码分支（仅服务端落盘，客户端只预测挥手）：
     * ① 距上次投入不足 {@value #BLANK_INSERT_COOLDOWN_TICKS} ticks（2:00）⇒ 红字拒绝、**不消耗**筹码；
     * ② 否则消耗 1 枚、刷新次数 +1（封顶）、清零购买冷却，并给一条金色成功提示。**不自动开店**。
     * ⚠️ 与抽奖盒语义**不同**（2026-10-22 用户裁决）：本处 = 刷新批次 +1；抽奖盒 = 只清购买冷却（无刷新机制）。
     *
     * <p>⚠️ {@code lastBlankInsertAt == 0} 是「从未投入」哨兵 ⇒ 首次投入不参与冷却判定
     * （否则世界开局 gameTime &lt; 2400 时首次投入会被误拒）。
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!stack.is(ModItems.BLANK_CHIP.get())) {
            return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
        }
        if (level.isClientSide()) {
            return ItemInteractionResult.sidedSuccess(true);
        }
        long now = level.getGameTime();
        ChipShopState shopState = ChipShopState.get(player);
        long last = shopState.lastBlankInsertAt();
        if (last != 0L && now - last < BLANK_INSERT_COOLDOWN_TICKS) {
            actionBar(player, ChatFormatting.RED, "msg.astral_dice.chip_shop_blank_cooldown");
            LOGGER.info("[astral_dice] CHIP_SHOP_BLANK_DENIED player={} reason=blank_cooldown last={} now={}",
                    player.getName().getString(), last, now);
            return ItemInteractionResult.sidedSuccess(false);
        }
        stack.shrink(1);
        ChipShopState.store(player, shopState.withBlankInsert(now, shopState.refreshesLeft() + 1, 0L));
        actionBar(player, ChatFormatting.GOLD, "msg.astral_dice.chip_shop_blank_insert");
        LOGGER.info("[astral_dice] CHIP_SHOP_BLANK_INSERT player={} refreshes={} now={}",
                player.getName().getString(), ChipShopState.get(player).refreshesLeft(), now);
        return ItemInteractionResult.sidedSuccess(false);
    }

    /** actionbar 提示（红字拒绝 / 金色成功）；非服务端玩家时静默跳过 */
    private static void actionBar(Player player, ChatFormatting color, String key) {
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, new ActionBarPayload(
                    Component.translatable(key).withStyle(color),
                    GameplayConstants.ACTIONBAR_DURATION_TICKS));
        }
    }
}
