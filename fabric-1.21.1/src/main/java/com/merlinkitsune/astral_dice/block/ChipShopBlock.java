package com.merlinkitsune.astral_dice.block;

import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.network.ModNetwork;
import com.merlinkitsune.astral_dice.shop.ChipShopOpener;
import com.merlinkitsune.astral_dice.shop.ChipShopState;
import com.merlinkitsune.starengine.component.GameplayConstants;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
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
import org.slf4j.Logger;

/**
 * 筹码商店方块（注册 id {@code astral_dice:chip_shop}）。
 *
 * <p>两种右键语义：
 * <ul>
 *   <li><b>手持空白筹码</b> ⇒ 消耗 1 张，给商店 +1 次刷新额度（2400 tick 冷却，跨维度/重登持久）；</li>
 *   <li><b>手持其它物品</b> ⇒ 打开筹码商店界面。</li>
 * </ul>
 *
 * <p>2026-11-01 移植自 forge-1.20.1 同路径源码（1:1）。
 *
 * <h2>fabric 接线（B1-CHIPSHOP 已落地）</h2>
 * <p>方块本体与状态逻辑无平台差异，两处依赖均已接上 fabric 线的 {@code shop} 包：
 * <ol>
 *   <li>开店 ⇒ {@link ChipShopOpener#open(Player)}（{@link #openShop(Player)}）；</li>
 *   <li>玩家持久状态 ⇒ {@link ChipShopState#get(Player)} / {@link ChipShopState#store(Player, ChipShopState)}
 *       （{@link #lastBlankInsertAt(Player)} / {@link #refreshesLeft(Player)} /
 *       {@link #storeBlankInsert(Player, long)} 三个私有 seam），语义与 forge 逐字一致。</li>
 * </ol>
 * <p>塞入空白筹码的**冷却判据用 world gameTime 差值**（非 tickCount），与 forge 逐字一致。
 */
public class ChipShopBlock extends HorizontalDirectionalBlock {

    /**
     * 1.21.1 必填的方块 codec（1.20.1 没有这个抽象方法）：区块/结构序列化与 BlockState 网络同步都经它。
     */
    public static final MapCodec<ChipShopBlock> CODEC = simpleCodec(ChipShopBlock::new);

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 塞入空白筹码的冷却（tick）：2400 = 2 分钟。 */
    private static final long BLANK_INSERT_COOLDOWN_TICKS = 2400L;

    private static final VoxelShape SHAPE = Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);

    public ChipShopBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** 1.21.1 契约：把父类的 {@code codec()} 窄化成自己的 codec。 */
    @Override
    public MapCodec<ChipShopBlock> codec() {
        return CODEC;
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
    public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                           Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!stack.is(ModItems.BLANK_CHIP.get())) {
            // 1.21.1 把方块交互拆成 useItemOn(手持物品) + useWithoutItem(默认交互)：
            // 非空白筹码 ⇒ 交回默认交互，引擎随后调用下面的 useWithoutItem，与 1.20.1 的显式转发等价。
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide()) {
            return ItemInteractionResult.sidedSuccess(true);
        }
        long now = level.getGameTime();
        long last = lastBlankInsertAt(player);
        if (last != 0L && now - last < BLANK_INSERT_COOLDOWN_TICKS) {
            actionBar(player, ChatFormatting.RED, "msg.astral_dice.chip_shop_blank_cooldown");
            LOGGER.info("[astral_dice] CHIP_SHOP_BLANK_DENIED player={} reason=blank_cooldown last={} now={}",
                    player.getName().getString(), last, now);
            return ItemInteractionResult.sidedSuccess(false);
        }
        stack.shrink(1);
        storeBlankInsert(player, now);
        actionBar(player, ChatFormatting.GOLD, "msg.astral_dice.chip_shop_blank_insert");
        LOGGER.info("[astral_dice] CHIP_SHOP_BLANK_INSERT player={} refreshes={} now={}",
                player.getName().getString(), refreshesLeft(player), now);
        return ItemInteractionResult.sidedSuccess(false);
    }

    /** 非空白筹码路径：打开商店界面（1.21.1 的默认方块交互钩子）。 */
    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                            BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            openShop(player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    /** actionbar 提示（与 forge 同口径：走 {@code ModNetwork.ActionBarMessage}，时长取前置库常量）。 */
    private static void actionBar(Player player, ChatFormatting color, String key) {
        if (player instanceof ServerPlayer serverPlayer) {
            ModNetwork.sendToPlayer(serverPlayer, new ModNetwork.ActionBarMessage(
                    Component.translatable(key).withStyle(color), GameplayConstants.ACTIONBAR_DURATION_TICKS));
        }
    }

    /**
     * 打开筹码商店界面（forge 同源：{@code ChipShopOpener.open(player)}）。
     *
     * <p>三项拒绝（全拥有 / 物品栏已满 / 冷却中）与候选首掷都在 {@link ChipShopOpener} 内部处理，
     * 本类不重复判定，也不吞异常。
     */
    private static void openShop(Player player) {
        ChipShopOpener.open(player);
    }

    /** 玩家「上次塞入空白筹码的 gameTime」（0 = 从未塞过）。 */
    private static long lastBlankInsertAt(Player player) {
        return ChipShopState.get(player).lastBlankInsertAt();
    }

    /** 玩家当前的刷新额度。 */
    private static int refreshesLeft(Player player) {
        return ChipShopState.get(player).refreshesLeft();
    }

    /**
     * 记录本次塞入：刷新额度 +1（clamp 到 {@code REFRESH_MAX}），并把「下次免费刷新时刻」清 0。
     *
     * <p>与 forge 逐字等价：{@code store(player, state.withBlankInsert(now, state.refreshesLeft() + 1, 0L))}。
     */
    private static void storeBlankInsert(Player player, long now) {
        ChipShopState state = ChipShopState.get(player);
        ChipShopState.store(player, state.withBlankInsert(now, state.refreshesLeft() + 1, 0L));
    }
}
