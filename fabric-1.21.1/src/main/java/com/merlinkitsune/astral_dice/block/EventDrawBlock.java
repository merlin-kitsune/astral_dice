package com.merlinkitsune.astral_dice.block;

import com.merlinkitsune.astral_dice.audio.SoundPlayback;
import com.merlinkitsune.astral_dice.audio.ModSounds;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.network.ModNetwork;
import com.merlinkitsune.starengine.component.GameplayConstants;
import com.merlinkitsune.starengine.economy.StarEngineEconomy;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.slf4j.Logger;

/**
 * 事件抽屉方块（注册 id {@code astral_dice:event_drawer}）：花 5 星币抽一张活动券。
 *
 * <p>两种右键语义：
 * <ul>
 *   <li><b>手持空白筹码</b> ⇒ 消耗 1 张，把抽屉冷却清零（2400 tick 塞入冷却，跨维度/重登持久）；</li>
 *   <li><b>手持其它物品</b> ⇒ 扣 5 星币，给一张 {@code astral_dice:event_ticket}，进入 6000 tick 冷却。</li>
 * </ul>
 *
 * <p>2026-11-01 移植自 forge-1.20.1 同路径源码（1:1）。
 *
 * <h2>fabric 等价实现（状态面接线，2026-11-01 B0-TAIL）</h2>
 * <p>方块本体、经济扣款（前置库 starengine 的 {@link StarEngineEconomy}）、actionbar 网络包、音效在 fabric 线
 * **全部可用且与 forge 同一套实现**；状态面原先收敛为 seam，现全部接上真实实现：
 * <ol>
 *   <li>{@code ModAttachments} 的 EventDrawer 访问器
 *       （{@code getEventDrawerCooldownUntil} / {@code setEventDrawerCooldownUntil} /
 *       {@code getEventDrawerBlankInsertAt} / {@code setEventDrawerBlankInsertAt}）——
 *       见 {@link #getCooldownUntil(Player)} 等四个私有 seam，与 forge 同键名、同一 sync 口径
 *       （cooldown 同步、blank_insert_at 不同步）；</li>
 *   <li>{@code ModItems.EVENT_TICKET}（forge {@code item/ModItems.java:455}）—— 见 {@link #giveEventTicket(Player)}。</li>
 * </ol>
 * <p>日志口径与 forge 逐字一致（{@code AP_EVENT_DRAWER:} 前缀是测试台的机器判据行，不要改）。
 */
public class EventDrawBlock extends Block {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 抽奖价格（星币）。 */
    private static final int PRICE_STAR_COINS = 5;

    /** 抽奖后的冷却（tick）：6000 = 5 分钟。 */
    private static final long COOLDOWN_TICKS = 6000L;

    /** 塞入空白筹码的冷却（tick）：2400 = 2 分钟。 */
    private static final long BLANK_INSERT_COOLDOWN_TICKS = 2400L;

    public EventDrawBlock(Properties properties) {
        super(properties);
    }

    /** 抽奖价格（供 UI / 语言文件占位用）。 */
    public static int priceStarCoins() {
        return PRICE_STAR_COINS;
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                           Player player, InteractionHand hand, BlockHitResult hitResult) {
        boolean blankChip = stack.is(ModItems.BLANK_CHIP.get());
        if (blankChip) {
            if (level.isClientSide()) {
                return ItemInteractionResult.sidedSuccess(true);
            }
            long now = level.getGameTime();
            long last = getBlankInsertAt(player);
            if (last != 0L && now - last < BLANK_INSERT_COOLDOWN_TICKS) {
                actionBar(player, ChatFormatting.RED, "msg.astral_dice.chip_shop_blank_cooldown");
                LOGGER.info("AP_EVENT_DRAWER: player={}:blank_insert=denied:cooldown:remain={}",
                        player.getName().getString(), BLANK_INSERT_COOLDOWN_TICKS - (now - last));
                return ItemInteractionResult.sidedSuccess(false);
            }
            stack.shrink(1);
            setCooldownUntil(player, 0L);
            setBlankInsertAt(player, now);
            actionBar(player, ChatFormatting.GOLD, "msg.astral_dice.event_drawer_blank_insert");
            LOGGER.info("AP_EVENT_DRAWER: player={}:blank_insert=ok:reset=cooldown",
                    player.getName().getString());
            return ItemInteractionResult.sidedSuccess(false);
        }
        if (level.isClientSide()) {
            return ItemInteractionResult.sidedSuccess(true);
        }
        long now = level.getGameTime();
        long until = getCooldownUntil(player);
        if (now < until) {
            actionBar(player, ChatFormatting.RED, "msg.astral_dice.event_drawer_cooldown");
            LOGGER.info("AP_EVENT_DRAWER: player={}:denied=cooldown:remain={}",
                    player.getName().getString(), until - now);
            return ItemInteractionResult.sidedSuccess(false);
        }
        long before = StarEngineEconomy.isAvailable() ? StarEngineEconomy.getBalance(player) : -1L;
        if (!StarEngineEconomy.isAvailable() || !StarEngineEconomy.hasBalance(player, PRICE_STAR_COINS)) {
            actionBar(player, ChatFormatting.RED, "msg.astral_dice.event_drawer_no_coin", PRICE_STAR_COINS);
            LOGGER.info("AP_EVENT_DRAWER: player={}:denied=insufficient:bal={}",
                    player.getName().getString(), before);
            return ItemInteractionResult.sidedSuccess(false);
        }
        if (!StarEngineEconomy.withdraw(player, PRICE_STAR_COINS)) {
            // 防竞态:余额判定与扣款之间被第三方改动 ⇒ 与余额不足同口径拒绝（日志行与 forge 逐字一致）
            actionBar(player, ChatFormatting.RED, "msg.astral_dice.event_drawer_no_coin", PRICE_STAR_COINS);
            LOGGER.info("AP_EVENT_DRAWER: player={}:denied=insufficient:bal={}",
                    player.getName().getString(), before);
            return ItemInteractionResult.sidedSuccess(false);
        }
        giveEventTicket(player);
        setCooldownUntil(player, now + COOLDOWN_TICKS);
        if (player instanceof ServerPlayer serverPlayer) {
            SoundPlayback.playTo(serverPlayer, ModSounds.WALLET_DEPOSIT.get());
        }
        LOGGER.info("AP_EVENT_DRAWER: player={}:buy=ok:bal={}->{}",
                player.getName().getString(), before, StarEngineEconomy.getBalance(player));
        return ItemInteractionResult.sidedSuccess(false);
    }

    /** actionbar 提示（与 forge 同口径：{@code ModNetwork.ActionBarMessage} + 前置库时长常量）。 */
    private static void actionBar(Player player, ChatFormatting color, String key, Object... args) {
        if (player instanceof ServerPlayer serverPlayer) {
            ModNetwork.sendToPlayer(serverPlayer, new ModNetwork.ActionBarMessage(
                    Component.translatable(key, args).withStyle(color), GameplayConstants.ACTIONBAR_DURATION_TICKS));
        }
    }

    /**
     * 发放一张活动券（进背包，背包满则原地掉落）。
     *
     * <p>与 forge {@code block/EventDrawBlock.java:113} 逐字一致：
     * {@code new ItemStack(ModItems.EVENT_TICKET.get())} + {@code player.getInventory().add(...)}，
     * 加不进背包就在原地 {@code player.drop(..., false)}。
     */
    private static void giveEventTicket(Player player) {
        ItemStack ticket = new ItemStack(ModItems.EVENT_TICKET.get());
        if (!player.getInventory().add(ticket)) {
            player.drop(ticket, false);
        }
    }

    /** 抽奖冷却结束时刻（转发 {@link ModAttachments#getEventDrawerCooldownUntil(Player)}）。 */
    private static long getCooldownUntil(Player player) {
        return ModAttachments.getEventDrawerCooldownUntil(player);
    }

    /** 写入抽奖冷却结束时刻（转发 {@link ModAttachments#setEventDrawerCooldownUntil(Player, long)}）。 */
    private static void setCooldownUntil(Player player, long value) {
        ModAttachments.setEventDrawerCooldownUntil(player, value);
    }

    /** 上次投入空白筹码的时刻（转发 {@link ModAttachments#getEventDrawerBlankInsertAt(Player)}，0 = 从未投入）。 */
    private static long getBlankInsertAt(Player player) {
        return ModAttachments.getEventDrawerBlankInsertAt(player);
    }

    /** 写入投入空白筹码的时刻（转发 {@link ModAttachments#setEventDrawerBlankInsertAt(Player, long)}）。 */
    private static void setBlankInsertAt(Player player, long value) {
        ModAttachments.setEventDrawerBlankInsertAt(player, value);
    }
}
