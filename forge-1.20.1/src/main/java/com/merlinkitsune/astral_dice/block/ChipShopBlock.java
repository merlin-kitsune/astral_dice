package com.merlinkitsune.astral_dice.block;

import com.merlinkitsune.astral_dice.shop.ChipShopOpener;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.network.ModNetwork;
import com.merlinkitsune.astral_dice.shop.ChipShopState;
import com.merlinkitsune.starenginelib.component.GameplayConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 筹码商店方块（放置后右键开店；判据正本 {@code docs/chip-shop-logic-plan.md} §5「方块入口」）。
 * 手持**空白筹码**右键则投入一枚（需求 5）。
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

    private static final Logger LOGGER = LoggerFactory.getLogger(ChipShopBlock.class);

    /** 空白筹码**投入**冷却时长：2400 ticks = 2:00（需求 5） */
    private static final long BLANK_INSERT_COOLDOWN_TICKS = 2400L;

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
     * 方块交互（1.20.1 的**六参** {@code use}）：空手 / 非空白筹码右键 ⇒ 委托 {@link #useWithoutItem}（开店）；
     * 手持**空白筹码**右键 ⇒ 投入一枚（需求 5，逻辑照 1.21.1 蓝本的 {@code useItemOn}，签名改为本线的
     * 六参 {@code use(...)}、返回 {@link InteractionResult}）。
     *
     * <p><b>平台差异（forge-1.20.1）</b>：1.21.1 的交互入口是 {@code useWithoutItem}（1.21 原版新增）+
     * 物品交互 {@code useItemOn}；1.20.1 两样都没有 ⇒ 由本六参 {@code use(...)} 一肩挑：非空白筹码
     * 委托 {@link #useWithoutItem}（开店，与改动前逐字一致），空白筹码走投入分支。
     *
     * <p>空白筹码分支（仅服务端落盘，客户端只预测挥手）：
     * ① 距上次投入不足 {@value #BLANK_INSERT_COOLDOWN_TICKS} ticks（2:00）⇒ 红字拒绝、**不消耗**筹码；
     * ② 否则消耗 1 枚、刷新次数 +1（封顶）、清零购买冷却，并给一条金色成功提示。**不自动开店**。
     *
     * <p>⚠️ {@code lastBlankInsertAt == 0} 是「从未投入」哨兵 ⇒ 首次投入不参与冷却判定
     * （否则世界开局 gameTime &lt; 2400 时首次投入会被误拒）。
     */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hitResult) {
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(ModItems.BLANK_CHIP.get())) {
            return useWithoutItem(state, level, pos, player, hitResult);
        }
        if (level.isClientSide()) {
            return InteractionResult.sidedSuccess(true);
        }
        long now = level.getGameTime();
        ChipShopState shopState = ChipShopState.get(player);
        long last = shopState.lastBlankInsertAt();
        if (last != 0L && now - last < BLANK_INSERT_COOLDOWN_TICKS) {
            actionBar(player, ChatFormatting.RED, "msg.astral_dice.chip_shop_blank_cooldown");
            LOGGER.info("[astral_dice] CHIP_SHOP_BLANK_DENIED player={} reason=blank_cooldown last={} now={}",
                    player.getName().getString(), last, now);
            return InteractionResult.sidedSuccess(false);
        }
        stack.shrink(1);
        ChipShopState.store(player, shopState.withBlankInsert(now, shopState.refreshesLeft() + 1, 0L));
        actionBar(player, ChatFormatting.GOLD, "msg.astral_dice.chip_shop_blank_insert");
        LOGGER.info("[astral_dice] CHIP_SHOP_BLANK_INSERT player={} refreshes={} now={}",
                player.getName().getString(), ChipShopState.get(player).refreshesLeft(), now);
        return InteractionResult.sidedSuccess(false);
    }

    /** actionbar 提示（红字拒绝 / 金色成功）；非服务端玩家时静默跳过 */
    private static void actionBar(Player player, ChatFormatting color, String key) {
        if (player instanceof ServerPlayer serverPlayer) {
            ModNetwork.sendToPlayer(serverPlayer, new ModNetwork.ActionBarMessage(
                    Component.translatable(key).withStyle(color),
                    GameplayConstants.ACTIONBAR_DURATION_TICKS));
        }
    }

    /** 开店本体（服务端权威：{@link ChipShopOpener#open(Player)} 内部先判「是否客户端」再校验/补抽/开店）。 */
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            ChipShopOpener.open(player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
