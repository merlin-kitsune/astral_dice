package com.merlinkitsune.astral_dice.choice;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.network.CardChoiceClosePayload;
import com.merlinkitsune.astral_dice.screen.CardChoiceMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * 「选卡界面」的服务端权威（用户裁决 2026-10-17；蓝本 = {@code event/EventChoiceManager}）。
 *
 * <h2>调用者接入方式（唯一入口）</h2>
 * <pre>{@code
 * int token = CardChoiceManager.start(serverPlayer, 3, CardChoiceType.COMBAT, picked -> {
 *     // picked = 确认时服务端实际消耗掉的卡牌（每张 count=1），已通过类型/会话校验
 *     // 这里同步执行（服务端 tick 线程），可直接继续后续结算
 * });
 * }</pre>
 * 「先注册后打开」的两步式等价写法：
 * <pre>{@code
 * CardChoiceManager.start(serverPlayer, 1, CardChoiceType.ANY, handler);
 * // 若调用者需要在打开前就拿到 token：改用 start(player, slots, type, token, handler) 重载
 * CardChoiceManager.registerHandler(serverPlayer, token, handler);
 * }</pre>
 *
 * <h2>会话规则</h2>
 * <ul>
 *   <li>每个玩家同时只能有**一个**未结算会话（重复 start 直接拒绝，不覆盖旧会话）；</li>
 *   <li>确认：token 一致 + 当前打开的正是本 token 的菜单 + 槽位已放满 ⇒ 服务端逐张消耗
 *       （1.21.1 = {@code ItemStack#consume(1, player)}），关闭菜单后回调调用者；</li>
 *   <li>Esc / 关闭界面：菜单 {@code removed} 只把卡牌退回物品栏，**零消耗、零回调**；</li>
 *   <li>不做超时强制选择（用户明确要求本批不加班超时）。</li>
 * </ul>
 */
@EventBusSubscriber(modid = AstralDiceMod.MODID)
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
     * @return 会话 token；&lt;0 = 被拒（已有未结算会话 / 参数非法 / 打开失败）
     */
    public static int start(ServerPlayer player, int slotCount, CardChoiceType type,
                            Consumer<List<ItemStack>> handler) {
        int token = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
        return start(player, slotCount, type, token, handler) ? token : -1;
    }

    /**
     * 打开选卡界面（调用者自带 token，便于把「界面 token」与调用者自己的状态机对齐）。
     *
     * @return true = 已建会话并下发界面
     */
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

        var opened = player.openMenu(new SimpleMenuProvider(
                        (containerId, inventory, p) -> new CardChoiceMenu(containerId, inventory, token, slotCount, type),
                        Component.translatable(type.titleKey(slotCount))),
                buf -> {
                    buf.writeVarInt(token);
                    buf.writeVarInt(slotCount);
                    buf.writeVarInt(type.ordinal());
                });
        if (opened.isEmpty()) {
            SESSIONS.remove(player.getUUID(), session);
            LOGGER.warn("AP_CARD_CHOICE: reject:reason=open_failed:player={}:token={}",
                    player.getGameProfile().getName(), token);
            return false;
        }
        LOGGER.info("AP_CARD_CHOICE: open:{}:player={}:slots={}:type={}:container={}",
                token, player.getGameProfile().getName(), slotCount, type, opened.getAsInt());
        return true;
    }

    /**
     * 两步式接入：给「已经打开但还没挂回调」的会话补/换回调。
     *
     * @return true = 会话存在且尚未结算
     */
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

    /**
     * C2S 确认入口（服务端权威；token / 菜单 / 槽位三重校验，重复包被丢弃）。
     */
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
            // 没放满 / 类型不符：保留界面与全部卡牌，零消耗
            LOGGER.info("AP_CARD_CHOICE: confirm_ignored:reason=incomplete:player={}:token={}:filled={}",
                    player.getGameProfile().getName(), token, menu.filledSlots());
            return;
        }
        session.resolved = true;
        SESSIONS.remove(player.getUUID(), session);
        LOGGER.info("AP_CARD_CHOICE: confirm:{}:player={}:slots={}:type={}:picked={}",
                token, player.getGameProfile().getName(), session.slotCount, session.type, picked.size());
        // token 作用域关闭（比只依赖 containerId 更稳；客户端找不到同 token 界面时是 no-op）
        PacketDistributor.sendToPlayer(player, new CardChoiceClosePayload(token));
        // 关菜单会触发 CardChoiceMenu#removed：只退回**未选中的余量**，已消耗的 1 张/槽不再回来
        player.closeContainer();
        try {
            session.handler.accept(List.copyOf(picked));
        } catch (RuntimeException exception) {
            LOGGER.error("AP_CARD_CHOICE: handler_failed:token={}", token, exception);
        }
    }

    /**
     * 会话作废（菜单关闭 / Esc / 掉线）：零消耗、零回调。
     *
     * <p>由 {@link CardChoiceMenu#removed} 调用；已确认的会话不会走到这里（它在 closeContainer 之前
     * 就已经从 map 里移除）。
     */
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

    /**
     * 死亡兜底（2026-10-17，审查 P2）：{@code PlayerList#respawn} 直接替换 {@link ServerPlayer}，
     * 旧菜单的 {@code removed()} **不会被调用** ⇒ 卡槽内卡牌丢失、本 map 残留同 UUID 的未结算会话
     * （重生后 {@link #start} 恒返回 already_open）。
     *
     * <p>死亡瞬间主动 {@code closeContainer()}：走 {@link CardChoiceMenu#removed} 的**死亡掉落**分支
     * （卡牌落在死亡点）并顺带 {@code abandon} 清会话；随后再按 UUID 兜底移除一次
     * （容器已不是本会话时也能把残留清干净）。与掉线兜底 {@link #onPlayerLogout} 互不冲突：
     * 先到者清会话，后到者读到 null 直接返回。
     *
     * <p>优先级 {@link EventPriority#LOWEST}：排在所有「保命 / 复活」类监听（不死图腾、末影骰子等）之后，
     * 事件被取消（玩家其实没死）时完全不动。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.isCanceled()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }
        LOGGER.info("AP_CARD_CHOICE: death_cleanup:{}:player={}", session.token,
                player.getGameProfile().getName());
        if (player.containerMenu instanceof CardChoiceMenu menu && menu.token() == session.token) {
            // 先关菜单：removed() 里 dying=true ⇒ 卡牌直接掉在死亡点，并 abandon 清会话
            player.closeContainer();
        }
        SESSIONS.remove(player.getUUID(), session);
        session.resolved = true;
    }

    /** 掉线兜底：菜单通常已被服务端关闭，这里只清理可能残留的会话。 */
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
