package com.merlinkitsune.astral_dice.block;

import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.network.ActionBarPayload;
import com.merlinkitsune.starengine.component.GameplayConstants;
import com.merlinkitsune.starengine.economy.StarEngineEconomy;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 抽奖盒(2026-10-10 事件体系重写 · 用户需求②「玩家使用抽事件的方块(独立注册)」)。
 *
 * <p><b>2026-10-14 改动注记</b>:原行为(右键从公共事件池随机抽一条事件并以 {@code BLOCK_USE} 触发器免费触发)
 * <b>已废弃</b>,改为<b>售卖机</b> —— 右键花 {@value #PRICE_STAR_COINS} 星币购买 1 张奇怪的问号
 * ({@code event_ticket}),每 {@value #COOLDOWN_TICKS} ticks(5:00)限购一次;余额不足或冷却中 ⇒
 * ActionBar 红字拒绝、不给物品。
 *
 * <p>交互入口 = 1.21.1 的 {@code useWithoutItem}(与卡牌商店 / 筹码商店方块一致);逻辑仅在服务端执行,
 * 客户端只回 {@code sidedSuccess(true)} 挥手。
 */
public class EventDrawBlock extends Block {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventDrawBlock.class);

    /** 购买单价(星币)。 */
    private static final int PRICE_STAR_COINS = 5;

    /** 购买冷却时长:6000 ticks = 5:00。 */
    private static final long COOLDOWN_TICKS = 6000L;

    /**
     * 购买单价的只读访问器（星币）。
     *
     * <p>Jade 的花费行与右键扣费共用此常量，避免两处各写一个数字导致显示与实际扣费漂移。
     */
    public static int priceStarCoins() {
        return PRICE_STAR_COINS;
    }

    public EventDrawBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                              BlockHitResult hitResult) {
        if (!level.isClientSide) {
            String name = player.getName().getString();
            long now = level.getGameTime();
            long until = ModAttachments.getEventDrawerCooldownUntil(player);
            if (now < until) {
                actionBar(player, ChatFormatting.RED, "msg.astral_dice.event_drawer_cooldown");
                LOGGER.info("AP_EVENT_DRAWER: player={}:denied=cooldown:remain={}", name, until - now);
                return InteractionResult.sidedSuccess(false);
            }
            long before = StarEngineEconomy.isAvailable() ? StarEngineEconomy.getBalance(player) : -1L;
            if (!StarEngineEconomy.isAvailable() || !StarEngineEconomy.hasBalance(player, PRICE_STAR_COINS)) {
                actionBar(player, ChatFormatting.RED, "msg.astral_dice.event_drawer_no_coin", PRICE_STAR_COINS);
                LOGGER.info("AP_EVENT_DRAWER: player={}:denied=insufficient:bal={}", name, before);
                return InteractionResult.sidedSuccess(false);
            }
            if (!StarEngineEconomy.withdraw(player, PRICE_STAR_COINS)) {
                // 防竞态:余额判定与扣款之间被第三方改动 ⇒ 与余额不足同口径拒绝
                actionBar(player, ChatFormatting.RED, "msg.astral_dice.event_drawer_no_coin", PRICE_STAR_COINS);
                LOGGER.info("AP_EVENT_DRAWER: player={}:denied=insufficient:bal={}", name, before);
                return InteractionResult.sidedSuccess(false);
            }
            ItemStack ticket = new ItemStack(ModItems.EVENT_TICKET.get());
            if (!player.getInventory().add(ticket)) {
                player.drop(ticket, false);
            }
            ModAttachments.setEventDrawerCooldownUntil(player, now + COOLDOWN_TICKS);
            actionBar(player, ChatFormatting.GOLD, "msg.astral_dice.event_drawer_bought", ticket.getHoverName());
            LOGGER.info("AP_EVENT_DRAWER: player={}:buy=ok:bal={}->{}", name, before,
                    StarEngineEconomy.getBalance(player));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** actionbar 提示(红字拒绝 / 金色成功);非服务端玩家时静默跳过。 */
    private static void actionBar(Player player, ChatFormatting color, String key, Object... args) {
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, new ActionBarPayload(
                    Component.translatable(key, args).withStyle(color),
                    GameplayConstants.ACTIONBAR_DURATION_TICKS));
        }
    }
}
