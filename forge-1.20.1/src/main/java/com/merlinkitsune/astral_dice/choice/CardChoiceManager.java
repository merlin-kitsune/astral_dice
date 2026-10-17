package com.merlinkitsune.astral_dice.choice;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.network.CardChoiceCloseMessage;
import com.merlinkitsune.astral_dice.network.ModNetwork;
import com.merlinkitsune.astral_dice.screen.CardChoiceMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
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

        try {
            NetworkHooks.openScreen(player, new SimpleMenuProvider(
                            (containerId, inventory, p) -> new CardChoiceMenu(containerId, inventory, token, slotCount, type),
                            Component.translatable(type.titleKey(slotCount))),
                    buf -> {
                        buf.writeVarInt(token);
                        buf.writeVarInt(slotCount);
                        buf.writeVarInt(type.ordinal());
                    });
        } catch (RuntimeException exception) {
            // 打开途中抛异常（createMenu 构造失败 / extra data 编码失败等）：会话已入 map，
            // 不清理就会让该玩家此后**恒**被判 already_open ⇒ 必须回滚会话并返回失败。
            session.resolved = true;
            SESSIONS.remove(player.getUUID(), session);
            LOGGER.warn("AP_CARD_CHOICE: reject:reason=open_failed:player={}:token={}",
                    player.getGameProfile().getName(), token, exception);
            return false;
        }
        // 1.20.1 的 NetworkHooks#openScreen 返回 void（1.21.1 的 ServerPlayer#openMenu 返回 OptionalInt），
        // 无法直接读到「是否成功打开」⇒ 改为打开后回读 player.containerMenu：不是本 token 的
        // CardChoiceMenu 即视为失败。**观众（spectator）不在此列**：provider 恒非 null
        //（{@code SimpleMenuProvider} 的 lambda 直接 new，不存在 createMenu 返回 null 的分支），
        // 观众同样能打开界面、回读到本 token 的菜单；其点击是**服务端丢弃**
        //（{@code ServerGamePacketListenerImpl#handleContainerClick} 对 spectator 只 sendAllDataToRemote）⇒
        // 界面会停住到玩家手动关闭，见 docs/features/card-choice-screen-spec.md §6 / §8。
        if (!(player.containerMenu instanceof CardChoiceMenu menu) || menu.token() != token) {
            SESSIONS.remove(player.getUUID(), session);
            session.resolved = true;
            LOGGER.warn("AP_CARD_CHOICE: reject:reason=open_failed:player={}:token={}:menu={}",
                    player.getGameProfile().getName(), token,
                    player.containerMenu == null ? "null" : player.containerMenu.getClass().getSimpleName());
            return false;
        }
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

    /**
     * 死亡兜底（2026-10-17，审查 P2）：{@code PlayerList#respawn} 直接替换 {@link ServerPlayer}，
     * 旧菜单的 {@code removed()} **不会被调用** ⇒ 卡槽内卡牌丢失、本 map 残留同 UUID 的未结算会话
     * （重生后 {@link #start} 恒返回 already_open）。
     *
     * <p>死亡瞬间主动 {@code closeContainer()}：走 {@link CardChoiceMenu#removed} 的**死亡掉落**分支
     * （卡牌落在死亡点）并顺带 {@code abandon} 清会话；随后再按 UUID 兜底移除一次。
     * 与掉线兜底 {@link #onPlayerLogout} 互不冲突：先到者清会话，后到者读到 null 直接返回。
     *
     * <p>⚠️ 派发次数：1.20.1 的 {@code ServerPlayer#die} **不调 {@code super.die}**（整段死亡处理内联在
     * {@code ServerPlayer} 自己的 {@code die} 里），Forge 的 patch 只在该方法头部注入一次
     * {@code ForgeHooks.onLivingDeath} ⇒ 玩家死亡只会派发**一次**本事件。
     * 本方法仍保持幂等（重复进来时会话已不在 map 里，直接返回），以防上游事件口径变化。
     *
     * <p>优先级 {@link EventPriority#LOWEST}：排在「保命 / 复活」类监听之后，事件被取消时完全不动。
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
