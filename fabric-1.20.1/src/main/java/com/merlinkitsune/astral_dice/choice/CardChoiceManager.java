package com.merlinkitsune.astral_dice.choice;

import com.merlinkitsune.astral_dice.network.CardChoiceCloseMessage;
import com.merlinkitsune.astral_dice.network.ModNetwork;
import com.merlinkitsune.astral_dice.platform.event.EventPriority;
import com.merlinkitsune.astral_dice.platform.event.SubscribeEvent;
import com.merlinkitsune.astral_dice.platform.event.entity.living.LivingDeathEvent;
import com.merlinkitsune.astral_dice.platform.event.entity.player.PlayerEvent;
import com.merlinkitsune.astral_dice.screen.CardChoiceMenu;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * 「选卡界面」的服务端权威（用户裁决 2026-10-17；1:1 移植自 forge-1.20.1 同名文件）。
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
 * <h2>平台差异（fabric-1.20.1，与 forge 线逐条对应）</h2>
 * <ol>
 *   <li><b>打开菜单</b>：forge 用 {@code NetworkHooks#openScreen(player, provider, buf -> ...)} 附带 extra data；
 *       本线用 {@code player.openMenu(factory)} + {@link ExtendedScreenHandlerFactory}
 *       （Fabric Screen Handler API v1，随 fabric-api 0.92.12 提供）：
 *       {@code writeScreenOpeningData} 写入的三个 VarInt 与 forge 的 extra data <b>同序同义</b>
 *       （token / slotCount / type.ordinal()），客户端 {@code CardChoiceMenu(int, Inventory, FriendlyByteBuf)}
 *       构造器因此可以与 forge 逐字一致 ⇒ <b>两张界面文件字节级相同、零改动</b>。</li>
 *   <li><b>关包</b>：同一份 {@link ModNetwork#sendToPlayer}（本线内部走 SimpleChannel）。</li>
 *   <li><b>事件订阅</b>：forge 的 {@code @Mod.EventBusSubscriber} 自动注册在本线不存在 ⇒ 必须在
 *       {@code AstralDiceMod#registerListeners()} 里 <b>显式</b>
 *       {@code LoaderBus.INSTANCE.register(CardChoiceManager.class)}（与本类同批注册的还有
 *       {@code shop.CardShopCycleTicker}）；漏登记 = 死亡/掉线兜底静默失效（会残留未结算会话）。</li>
 *   <li>消耗仍用 {@code ItemStack#shrink(1)}（1.20.1 两线一致）。</li>
 * </ol>
 */
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
     * 本线的「带 extra data 的打开菜单」载体：替换 forge 的 {@code NetworkHooks#openScreen(player, provider, writer)}。
     *
     * <p>三个 VarInt 的顺序/语义必须与 {@code CardChoiceMenu(int, Inventory, FriendlyByteBuf)} 的读取顺序严格一致
     * （token → slotCount → type.ordinal()），否则客户端会错位解码。
     */
    private static final class ChoiceMenuFactory implements ExtendedScreenHandlerFactory {
        private final int token;
        private final int slotCount;
        private final CardChoiceType type;

        ChoiceMenuFactory(int token, int slotCount, CardChoiceType type) {
            this.token = token;
            this.slotCount = slotCount;
            this.type = type;
        }

        @Override
        public void writeScreenOpeningData(ServerPlayer player, FriendlyByteBuf buf) {
            buf.writeVarInt(token);
            buf.writeVarInt(slotCount);
            buf.writeVarInt(type.ordinal());
        }

        @Override
        public Component getDisplayName() {
            return Component.translatable(type.titleKey(slotCount));
        }

        @Override
        public CardChoiceMenu createMenu(int containerId, Inventory inventory, Player player) {
            return new CardChoiceMenu(containerId, inventory, token, slotCount, type);
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
            // forge 这里是 NetworkHooks.openScreen(player, new SimpleMenuProvider(...), buf -> {...})；
            // 本线把「provider + extra data」合并成 ChoiceMenuFactory（同序写入三个 VarInt）。
            player.openMenu(new ChoiceMenuFactory(token, slotCount, type));
        } catch (RuntimeException exception) {
            // 打开途中抛异常（createMenu 构造失败 / extra data 编码失败等）：会话已入 map，
            // 不清理就会让该玩家此后**恒**被判 already_open ⇒ 必须回滚会话并返回失败。
            session.resolved = true;
            SESSIONS.remove(player.getUUID(), session);
            LOGGER.warn("AP_CARD_CHOICE: reject:reason=open_failed:player={}:token={}",
                    player.getGameProfile().getName(), token, exception);
            return false;
        }
        // openMenu 返回 OptionalInt，但我们仍然**回读 player.containerMenu** 而不是信任返回值：
        // 这与 forge 线的判据完全一致（两边都要求「当前菜单确实是本 token 的 CardChoiceMenu」），
        // 且能同时覆盖「provider 被包装 / 客户端未装屏幕 / 打开被服务端别处顶掉」等情况。
        // **观众（spectator）不在此列**：provider 恒非 null（ChoiceMenuFactory 直接 new，
        // 不存在 createMenu 返回 null 的分支），观众同样能打开界面、回读到本 token 的菜单；
        // 其点击是**服务端丢弃**（handleContainerClick 对 spectator 只 sendAllDataToRemote）
        // ⇒ 界面会停住到玩家手动关闭，见 docs/features/card-choice-screen-spec.md §6 / §8。
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
     * <p>派发次数：本线的 {@code LivingDeathEvent} 由平台层在 {@code LivingEntity#die} 的 HEAD 处
     * 经 {@code LivingDeathCallback} 派发（等价 forge 的 {@code ForgeHooks.onLivingDeath}），
     * 玩家死亡只会派发**一次**。本方法仍保持幂等（重复进来时会话已不在 map 里，直接返回），
     * 以防上游事件口径变化。
     *
     * <p>优先级 {@link EventPriority#LOWEST}：排在「保命 / 复活」类监听之后，事件被取消时完全不动。
     *
     * <p><b>本线注意</b>：本类不再有 {@code @Mod.EventBusSubscriber}，靠
     * {@code AstralDiceMod#registerListeners()} 里的显式 {@code LoaderBus.INSTANCE.register(CardChoiceManager.class)}
     * 生效；本方法签名里出现的事件类型必须在 {@code LoaderBus} 的派发面内（已具备）。
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
