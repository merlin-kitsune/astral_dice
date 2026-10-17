package com.merlinkitsune.astral_dice.choice;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.network.CardChoiceCloseMessage;
import com.merlinkitsune.astral_dice.network.ModNetwork;
import com.merlinkitsune.astral_dice.screen.CardChoiceMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * 「选卡界面」的服务端权威（用户裁决 2026-10-17；1.20.1 移植，蓝本 = {@code event/EventChoiceManager}）。
 *
 * <h2>调用者接入方式（唯一入口）</h2>
 * <pre>{@code
 * int token = CardChoiceManager.start(serverPlayer, 3, CardChoiceType.COMBAT, picked -> {
 *     // picked = 确认时服务端实际消耗掉的卡牌（每张 count=1），已通过类型/会话校验
 *     // 这里同步执行（服务端 tick 线程），可直接继续后续结算
 * });
 * }</pre>
 * 两步式等价写法：{@code start(player, slots, type, token, handler)} 自带 token，或先 open 再用
 * {@link #registerHandler(ServerPlayer, int, Consumer)} 补挂回调。
 *
 * <p>与 1.21.1 的差异只有三处：① 打开菜单走 {@link NetworkHooks#openScreen}（1.20.1 的
 * {@code ServerPlayer#openMenu} 不带 extra data）② 关包走 {@link ModNetwork#sendToPlayer}
 * ③ 消耗用 {@code ItemStack#shrink(1)}（1.21.1 是 {@code consume(1, player)}）。语义完全相同。
 */
@Mod.EventBusSubscriber(modid = AstralDiceMod.MODID)
public final class CardChoiceManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(CardChoiceManager.class);

    /** 单卡 / 三卡两种布局；本组件只支持 1 或 3（用户裁决）。 */
    public static final int SLOTS_SINGLE = 1;
    public static final int SLOTS_TRIPLE = 3;
    public static final int MIN_SLOTS = 1;
    public static final int MAX_SLOTS = 3;

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private CardChoiceManager() {
    }

    /** 一次未决的选择（每个玩家最多一个）。 */
    private static final class Session {
        final int token;
        final int slotCount;
        final CardChoiceType type;
        Consumer<List<ItemStack>> handler;
        /** 已确认/已作废：此后不再接受确认与重复回调。 */
        boolean resolved;

        Session(int token, int slotCount, CardChoiceType type, Consumer<List<ItemStack>> handler) {
            this.token = token;
            this.slotCount = slotCount;
            this.type = type;
            this.handler = handler;
        }
    }

    /**
     * 打开选卡界面（自动分配 token）。
     *
     * @return 会话 token；&lt;0 = 被拒（已有未结算会话 / 参数非法）
     */
    public static int start(ServerPlayer player, int slotCount, CardChoiceType type,
                            Consumer<List<ItemStack>> handler) {
        int token = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
        return start(player, slotCount, type, token, handler) ? token : -1;
    }

    /** 打开选卡界面（调用者自带 token）；true = 已建会话并下发界面。 */
    public static boolean start(ServerPlayer player, int slotCount, CardChoiceType type, int token,
                                Consumer<List<ItemStack>> handler) {
        if (player == null || type == null || handler == null || token <= 0) {
            return false;
        }
        if (slotCount != SLOTS_SINGLE && slotCount != SLOTS_TRIPLE) {
            LOGGER.warn("AP_CARD_CHOICE: reject:reason=bad_slot_count:player={}:slots={}",
                    player.getGameProfile().getName(), slotCount);
            return false;
        }
        Session existing = SESSIONS.get(player.getUUID());
        if (existing != null && !existing.resolved) {
            LOGGER.warn("AP_CARD_CHOICE: reject:reason=already_open:player={}:token={}",
                    player.getGameProfile().getName(), existing.token);
            return false;
        }
        Session session = new Session(token, slotCount, type, handler);
        SESSIONS.put(player.getUUID(), session);

        NetworkHooks.openScreen(player, new SimpleMenuProvider(
                        (containerId, inventory, p) -> new CardChoiceMenu(containerId, inventory, token, slotCount, type),
                        Component.translatable(type.titleKey(slotCount))),
                buf -> {
                    buf.writeVarInt(token);
                    buf.writeVarInt(slotCount);
                    buf.writeVarInt(type.ordinal());
                });
        LOGGER.info("AP_CARD_CHOICE: open:{}:player={}:slots={}:type={}",
                token, player.getGameProfile().getName(), slotCount, type);
        return true;
    }

    /** 两步式接入：给「已经打开但还没挂回调」的会话补/换回调。 */
    public static boolean registerHandler(ServerPlayer player, int token, Consumer<List<ItemStack>> handler) {
        Session session = player == null ? null : SESSIONS.get(player.getUUID());
        if (session == null || session.resolved || session.token != token || handler == null) {
            return false;
        }
        session.handler = handler;
        return true;
    }

    /** 当前是否有未结算会话（供调用者防重复触发）。 */
    public static boolean hasOpenSession(ServerPlayer player) {
        Session session = player == null ? null : SESSIONS.get(player.getUUID());
        return session != null && !session.resolved;
    }

    /** C2S 确认入口（服务端权威；token / 菜单 / 槽位三重校验，重复包被丢弃）。 */
    public static void confirm(ServerPlayer player, int token) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || session.resolved || session.token != token) {
            LOGGER.warn("AP_CARD_CHOICE: confirm_ignored:reason=no_session:player={}:token={}",
                    player.getGameProfile().getName(), token);
            return;
        }
        if (!(player.containerMenu instanceof CardChoiceMenu menu) || menu.token() != token) {
            LOGGER.warn("AP_CARD_CHOICE: confirm_ignored:reason=menu_mismatch:player={}:token={}",
                    player.getGameProfile().getName(), token);
            return;
        }
        List<ItemStack> picked = menu.consumeSelection(player);
        if (picked.isEmpty()) {
            LOGGER.info("AP_CARD_CHOICE: confirm_ignored:reason=incomplete:player={}:token={}:filled={}",
                    player.getGameProfile().getName(), token, menu.filledSlots());
            return;
        }
        session.resolved = true;
        SESSIONS.remove(player.getUUID(), session);
        LOGGER.info("AP_CARD_CHOICE: confirm:{}:player={}:slots={}:type={}:picked={}",
                token, player.getGameProfile().getName(), session.slotCount, session.type, picked.size());
        ModNetwork.sendToPlayer(player, new CardChoiceCloseMessage(token));
        player.closeContainer();
        try {
            session.handler.accept(List.copyOf(picked));
        } catch (RuntimeException exception) {
            LOGGER.error("AP_CARD_CHOICE: handler_failed:token={}", token, exception);
        }
    }

    /** 会话作废（菜单关闭 / Esc / 掉线）：零消耗、零回调。 */
    public static void abandon(ServerPlayer player, int token) {
        if (player == null) {
            return;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || session.token != token) {
            return;
        }
        session.resolved = true;
        SESSIONS.remove(player.getUUID(), session);
        LOGGER.info("AP_CARD_CHOICE: abandon:{}:player={}:consumed=0", token,
                player.getGameProfile().getName());
    }

    /** 掉线兜底。 */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Session session = SESSIONS.remove(event.getEntity().getUUID());
        if (session != null) {
            session.resolved = true;
            LOGGER.info("AP_CARD_CHOICE: abandon:{}:player={}:reason=logout:consumed=0",
                    session.token, event.getEntity().getGameProfile().getName());
        }
    }
}
