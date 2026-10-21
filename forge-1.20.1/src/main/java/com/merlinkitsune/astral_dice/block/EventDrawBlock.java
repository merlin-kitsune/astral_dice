package com.merlinkitsune.astral_dice.block;

import com.merlinkitsune.astral_dice.audio.ModSounds;
import com.merlinkitsune.astral_dice.audio.SoundPlayback;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.network.ModNetwork;
import com.merlinkitsune.starengine.component.GameplayConstants;
import com.merlinkitsune.starengine.economy.StarEngineEconomy;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
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
 * <p>交互入口 = 1.20.1 的六参 {@code use}(多一个 InteractionHand,与卡牌商店 / 筹码商店方块一致);
 * 逻辑仅在服务端执行,客户端只回 {@code sidedSuccess(true)} 挥手。
 */
public class EventDrawBlock extends Block {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventDrawBlock.class);

    /** 购买单价(星币)。 */
    private static final int PRICE_STAR_COINS = 5;

    /** 购买冷却时长:6000 ticks = 5:00。 */
    private static final long COOLDOWN_TICKS = 6000L;

    /**
     * 空白筹码**投入间隔**:2400 ticks = 2:00(对齐筹码商店 {@code ChipShopBlock#BLANK_INSERT_COOLDOWN_TICKS})。
     * 同一玩家未到点 ⇒ 红字拒绝且**不消耗**空白筹码、不清冷却。
     */
    private static final long BLANK_INSERT_COOLDOWN_TICKS = 2400L;

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
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                            BlockHitResult hitResult) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.is(ModItems.BLANK_CHIP.get())) {
            // 投入空白筹码 ⇒ 重置抽奖盒（与筹码商店 ChipShopBlock#use 同口径：消耗 1 枚、清空批次状态）
            // 投入间隔(2026-10-21):距上次投入不足 BLANK_INSERT_COOLDOWN_TICKS(2400t = 2:00)⇒
            // 红字拒绝且**不消耗**筹码(提示键复用筹码商店同款的 chip_shop_blank_cooldown)。
            if (level.isClientSide()) {
                return InteractionResult.sidedSuccess(true);
            }
            long now = level.getGameTime();
            long last = ModAttachments.getEventDrawerBlankInsertAt(player);
            if (last != 0L && now - last < BLANK_INSERT_COOLDOWN_TICKS) {
                actionBar(player, ChatFormatting.RED, "msg.astral_dice.chip_shop_blank_cooldown");
                LOGGER.info("AP_EVENT_DRAWER: player={}:blank_insert=denied:cooldown:remain={}",
                        player.getName().getString(), BLANK_INSERT_COOLDOWN_TICKS - (now - last));
                return InteractionResult.sidedSuccess(false);
            }
            stack.shrink(1);
            ModAttachments.setEventDrawerCooldownUntil(player, 0L);
            ModAttachments.setEventDrawerBlankInsertAt(player, now);
            actionBar(player, ChatFormatting.GOLD, "msg.astral_dice.event_drawer_blank_insert");
            LOGGER.info("AP_EVENT_DRAWER: player={}:blank_insert=ok:reset=cooldown",
                    player.getName().getString());
            return InteractionResult.sidedSuccess(false);
        }
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
            // 购买成功音效：一律走 SoundPlayback（禁止裸写 level.playSound）
            if (player instanceof ServerPlayer sp) {
                SoundPlayback.playTo(sp, ModSounds.WALLET_DEPOSIT.get());
            }
            LOGGER.info("AP_EVENT_DRAWER: player={}:buy=ok:bal={}->{}", name, before,
                    StarEngineEconomy.getBalance(player));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** actionbar 提示(红字拒绝 / 金色成功);非服务端玩家时静默跳过。 */
    private static void actionBar(Player player, ChatFormatting color, String key, Object... args) {
        if (player instanceof ServerPlayer serverPlayer) {
            ModNetwork.sendToPlayer(serverPlayer, new ModNetwork.ActionBarMessage(
                    Component.translatable(key, args).withStyle(color),
                    GameplayConstants.ACTIONBAR_DURATION_TICKS));
        }
    }
}
