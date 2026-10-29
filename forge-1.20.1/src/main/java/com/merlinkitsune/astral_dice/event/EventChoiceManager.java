package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.event.api.AstralEvent;
import com.merlinkitsune.astral_dice.event.api.AstralEventTrigger;
import com.merlinkitsune.astral_dice.network.EventChoiceCloseMessage;
import com.merlinkitsune.astral_dice.network.EventChoiceStartMessage;
import com.merlinkitsune.astral_dice.network.EventChoiceTickMessage;
import com.merlinkitsune.astral_dice.network.ModNetwork;
import com.merlinkitsune.starengine.component.GameplayConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 大侦探立牌主动技能：<b>事件选择会话</b>的服务端权威（用户裁决 2026-10-10；1.20.1 移植）。
 *
 * <p>流程：立牌主动技能（{@code FannySignItem#handleUse}）→ {@link #start(ServerPlayer)} 从现有事件池
 * {@link AstralEventRegistry#publicPool(Player)} <b>独立抽 2 个选项</b>（允许相同、不做正/负/中性平衡）
 * → 下发 {@link EventChoiceStartMessage} 打开选择界面 → 玩家 15 秒内选一个并点确认
 * → {@link #confirm(ServerPlayer, int, int)}：等待 1 秒 → 关界面
 * → 随后进入**既有事件触发流程**（{@link AstralEventDispatcher#trigger} →
 * {@code EventCardService#showThenRun}：弹卡 → 1.4s 动画 + 0.5s → 结算/音效）。
 *
 * <p><b>强制触发第一个选项</b>（不改走确认路径、不播音效）：15 秒超时未选 / 玩家死亡 / 玩家登出。
 *
 * <p>⚠️ 主动技能音效**在按下技能键（使用立牌）那一刻就播**（2026-10-11 用户裁决，与其它立牌一致）；
 * 本类在玩家确认时**不再**补播，避免同一声音叠放。
 *
 * <p>平台差异（1.20.1）：tick 用 {@link TickEvent.PlayerTickEvent} 且必须判
 * {@code phase == Phase.END}（否则每个玩家每 tick 会在 START/END 各跑一次）；死亡 = Forge 的
 * {@link LivingDeathEvent}；登出 = {@link PlayerEvent.PlayerLoggedOutEvent}；
 * 下发走 {@link ModNetwork#sendToPlayer(ServerPlayer, Object)}（既有 SimpleChannel 通道，消息 id 见 ModNetwork）。
 */
@Mod.EventBusSubscriber(modid = AstralDiceMod.MODID)
public final class EventChoiceManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventChoiceManager.class);

    /** 选项个数（固定 2 个）。 */
    public static final int OPTION_COUNT = 2;

    /** 等待期 = 15 秒（用户裁决；**不复用**库里 {@code GameplayConstants.SKILL_WAIT_SECONDS}=30，那是目标选择器的）。 */
    public static final int CHOICE_TIMEOUT_TICKS = 15 * 20;

    /** 确认后「等待 1 秒 → 关闭界面 → 再进入事件触发流程」里的那 1 秒（20 ticks）。 */

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private EventChoiceManager() {
    }

    /** 一次未决的选择（每个玩家最多一个）。 */
    private static final class Session {
        final int token;
        final ResourceLocation firstEvent;
        final ResourceLocation secondEvent;
        /** 超时时刻（{@code level.getGameTime()} 口径）。 */
        final long expireTick;
        /** 已定选项索引：-1 = 未定；>=0 = 已确认/已被强制（此后不再接受新的确认与超时结算）。 */
        int resolving = -1;
        /**
         * 最近一次**已下发**给客户端的剩余秒数（-1 = 还没发过）。
         *
         * <p>只在「秒数变化」时下发倒计时包（15 秒等待期共约 16 包，而不是每 tick 一包）；
         * 首次 tick 时 remaining≈299 ⇒ seconds=15，客户端开局即显示 {@code 0:15}。
         */
        int syncedSecond = -1;

        Session(int token, ResourceLocation firstEvent, ResourceLocation secondEvent, long expireTick) {
            this.token = token;
            this.firstEvent = firstEvent;
            this.secondEvent = secondEvent;
            this.expireTick = expireTick;
        }

        ResourceLocation chosen() {
            return resolving == 0 ? firstEvent : secondEvent;
        }
    }

    /**
     * 立牌主动技能入口：抽 2 个事件选项并打开选择界面。
     *
     * @return true = 已开会话并下发界面；false = 事件池为空，或同玩家已有**未结算**会话
     *         （后者不重复开会话、不重复结算）
     */
    public static boolean start(ServerPlayer player) {
        // 2026-10-29 用户裁决 D4（兜底）：事件卡动画窗口期内不得开选择界面 —— 否则界面会盖在弹卡动画之上，
        // 玩家在动画尚未结算时确认，等于让「事件的选择」抢在「事件的效果」之前生效。
        // 正常路径已由 BaseSignItem#performSkill 的同一判定拦下并提示，这里只兜「将来新增的调用方」。
        if (EventAnimationLock.isAnimating(player)) {
            LOGGER.info("AP_EVENT_ANIM: block:{}:{}", player.getGameProfile().getName(), "event_choice_start");
            ModNetwork.sendToPlayer(player, new ModNetwork.ActionBarMessage(
                    Component.translatable(EventAnimationLock.NOTICE_KEY).withStyle(ChatFormatting.RED),
                    GameplayConstants.ACTIONBAR_DURATION_TICKS));
            return false;
        }
        // 闸门（2026-10-11 code_review 非阻塞项）：同玩家**已有未结算会话**时拒绝再次开启。
        // 否则二次按键会直接覆盖 SESSIONS 里的旧会话（旧会话的倒计时/强制结算从此失联），
        // 且客户端会连开两个选择界面。返回 false ⇒ 本次主动技能未开会话、不结算。
        Session existing = SESSIONS.get(player.getUUID());
        if (existing != null && existing.resolving < 0) {
            LOGGER.warn("AP_EVENT_CHOICE: already_open:player={}", player.getGameProfile().getName());
            return false;
        }
        List<AstralEvent> pool = AstralEventRegistry.publicPool(player);
        if (pool.isEmpty()) {
            LOGGER.warn("AP_EVENT_CHOICE: pool_empty:player={}", player.getGameProfile().getName());
            return false;
        }
        // ⚠️ 两次**独立**抽取：用户裁决明确「两个选项允许相同、允许任意正/负/中性组合」⇒ 不去重、不洗牌平衡。
        AstralEvent first = pool.get(player.getRandom().nextInt(pool.size()));
        AstralEvent second = pool.get(player.getRandom().nextInt(pool.size()));

        int token = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
        Session session = new Session(token, first.id(), second.id(),
                player.level().getGameTime() + CHOICE_TIMEOUT_TICKS);
        SESSIONS.put(player.getUUID(), session);

        ModNetwork.sendToPlayer(player, new EventChoiceStartMessage(token, first.id(), second.id(),
                first.defaultDurationTicks(), second.defaultDurationTicks(), CHOICE_TIMEOUT_TICKS));
        LOGGER.info("AP_EVENT_CHOICE: open:{}:opt0={}:opt1={}:expire={}",
                token, first.id(), second.id(), session.expireTick);
        return true;
    }

    /** C2S 确认入口（服务端权威；token 与「尚未结算」双重校验，重复包被丢弃）。 */
    public static void confirm(ServerPlayer player, int token, int optionIndex) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || session.token != token || session.resolving >= 0) return;
        if (optionIndex < 0 || optionIndex >= OPTION_COUNT) return;
        // 2026-10-29 用户裁决 D1：此处**不再**查询全局事件闸门（原 canStart / notifyBlocked 预判已删除）。
        // 理由：选择会话是「选中即生效」的主动技能，若被上一个事件的窗口拒掉，玩家看到的是
        // 「界面已选、点了确认却毫无反应」，且会话还被保留 15 秒 —— 比直接拒绝更糟。
        // 可接受副作用：本主动技能触发的事件**不占用**全服事件窗口，可与一个计时中的事件重叠；
        // 占用窗口的只剩「事件卡」入口（见 AstralEventDispatcher ⑤ 段），这类主动技能的节流靠技能自身冷却。
        // OPTION_COUNT 固定为 2：索引 0/1 直接对应 firstEvent/secondEvent。
        session.resolving = optionIndex;
        // ① 主动技能音效**不在此处重复播放**：2026-10-11 用户裁决改回「按技能键（使用立牌）瞬间即播」，
        //    由 FannySignItem#handleUse 经 BaseSignItem#performSkill 播放；确认时再播会叠一次。
        LOGGER.info("AP_EVENT_CHOICE: confirm:{}:pick={}:event={}", token, optionIndex, session.chosen());

        ServerLevel level = player.serverLevel();
        // ② 确认后的**唯一**顺序（2026-10-11 口径，已不再含「确认时播音效」那一步）：
        //    等 1 秒 → 关界面 → 触发。主动技能音效已在**按下技能键那一刻**由
        //    FannySignItem#handleUse → BaseSignItem#performSkill 播出，此处不重复。
        //    关闭包先发（客户端先关掉界面），同一 tick 内紧接着走既有事件触发流程
        //    （弹卡 → 1.4s 动画 + 0.5s → 结算/音效）。
        // 用户裁决 2026-10-11：**移除确认后的 1 秒等待** —— 关界面与事件触发都在本 tick 立即执行；
        // 主动技能音效只在按下技能键那一刻播（BaseSignItem#performSkill），确认时不再有任何音效。
        SESSIONS.remove(player.getUUID(), session);
        ModNetwork.sendToPlayer(player, new EventChoiceCloseMessage(token));
        AstralEventDispatcher.trigger(player, session.chosen(), AstralEventTrigger.ACTIVE_SKILL);
    }

    /**
     * C2S 取消入口（用户裁决 2026-10-29 D2：ESC 关闭界面 = 放弃这次主动技能）。
     *
     * <p>token 必须与会话一致且**尚未结算**；命中即丢弃会话、发关闭包（客户端据此关界面）、
     * 触发**零个**事件、也不再下发倒计时。
     *
     * <p><b>关于冷却（2026-10-29 复核口径，与 neo 线同一措辞）</b>：ESC 取消只丢弃会话、不触发事件；
     * 技能冷却 / 锁定由 {@code BaseSignItem#performSkill} 在**按下技能键那一刻**已写入，不因取消而回退
     * （大侦探非选择器类，走的就是这条路径；选择器类技能的冷却则在确认后的 apply 里才写入）。
     *
     * <p>幂等：会话不存在 / token 不匹配 / 已结算（{@code resolving >= 0}）一律只记机器行后忽略 ——
     * ESC 与服务端超时强制结算可能在同一 tick 竞态，迟到的取消包到这里时会话已经不在。
     */
    public static void cancel(ServerPlayer player, int token, String reason) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || session.token != token || session.resolving >= 0) {
            LOGGER.info("AP_EVENT_CHOICE: cancel_ignored:{}:player={}:reason={}",
                    token, player.getGameProfile().getName(), reason);
            return;
        }
        SESSIONS.remove(player.getUUID(), session);
        ModNetwork.sendToPlayer(player, new EventChoiceCloseMessage(token));
        LOGGER.info("AP_EVENT_CHOICE: cancel:{}:{}", token, reason);
    }

    /**
     * 强制取消（不看 token；用户裁决 2026-10-29 D5：事件卡动画开始那一刻，玩家已开着的选择界面必须让位）。
     *
     * <p>与 {@link #cancel} 的区别只在入口：本方法由服务端内部场景（动画起锁）调用，那里只知道「该玩家」
     * 而不知道会话 token。已结算（resolving >= 0）的会话不碰 —— 它的收尾由确认路径负责。
     */
    public static void cancelFor(ServerPlayer player, String reason) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || session.resolving >= 0) return;
        SESSIONS.remove(player.getUUID(), session);
        ModNetwork.sendToPlayer(player, new EventChoiceCloseMessage(session.token));
        LOGGER.info("AP_EVENT_CHOICE: cancel:{}:{}", session.token, reason);
    }

    /** 强制触发第一个选项（超时 / 死亡 / 登出）：不播主动技能音效，直接关界面并走事件触发流程。 */
    private static void forceFirst(Player player, Session session, String reason) {
        if (session.resolving >= 0) return;   // 玩家已确认 ⇒ 尊重其选择（会话由确认路径收尾）
        // 2026-10-29 用户裁决 D1：强制结算（超时 / 死亡 / 登出）同样**不再**看全局闸门（口径同 confirm）。
        // 旧实现会在闸门拒绝时保留会话，导致「已超时但会话仍活着」—— 玩家随后被 already_open 永久拦住。
        if (!SESSIONS.remove(player.getUUID(), session)) return;
        session.resolving = 0;
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        ModNetwork.sendToPlayer(serverPlayer, new EventChoiceCloseMessage(session.token));
        LOGGER.info("AP_EVENT_CHOICE: force:{}:pick=0:reason={}:event={}",
                session.token, reason, session.firstEvent);
        AstralEventDispatcher.trigger(serverPlayer, session.firstEvent, AstralEventTrigger.ACTIVE_SKILL);
    }

    /**
     * 15 秒等待期：① 向客户端同步**服务端权威**剩余 tick（倒计时唯一同源）；② 到期强制第一个选项。
     *
     * <p>2026-10-11 用户裁决「倒计时必须与 {@link #CHOICE_TIMEOUT_TICKS} 同源（以服务端剩余 tick 为准，
     * 别各算一套）」：客户端不再自行递减，改由本方法在**剩余秒数变化**时下发
     * {@link EventChoiceTickMessage}（≤16 包/会话）；这样即使玩家卡顿/掉帧，显示的也是服务端真实剩余秒数。
     */
    static void tick(Player player) {
        if (player.level().isClientSide()) return;
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || session.resolving >= 0) return;
        long remaining = session.expireTick - player.level().getGameTime();
        if (remaining <= 0L) {
            forceFirst(player, session, "timeout");
            // 兜底（2026-10-29 D2/D5 同批）：过期判据成立之后**不得**再让会话留在表里。
            // forceFirst 只在「已结算 / remove 竞态失败」两种情况不清会话，后者会让本分支每 tick 空转，
            // 且玩家再按技能键会被 already_open 永久拦住（旧实现的「已超时但会话仍在」正是这种死角）。
            if (SESSIONS.remove(player.getUUID(), session)) {
                session.resolving = 0;
                LOGGER.warn("AP_EVENT_CHOICE: expire_dropped:{}:reason=timeout", session.token);
            }
            return;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        int seconds = (int) ((remaining + 19L) / 20L);   // 向上取整：开局 299t ⇒ 15s
        if (seconds == session.syncedSecond) return;
        session.syncedSecond = seconds;
        ModNetwork.sendToPlayer(serverPlayer, new EventChoiceTickMessage(session.token, (int) remaining));
        LOGGER.info("AP_EVENT_CHOICE: countdown:{}:remaining={}:sec={}",
                session.token, remaining, seconds);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        // 平台差异：1.20.1 的 PlayerTickEvent 每 tick 派发 START 与 END 两次 ⇒ 只认 END，避免重复结算。
        if (event.phase != TickEvent.Phase.END) return;
        tick(event.player);
    }

    /** 玩家死亡 ⇒ 强制第一个选项。 */
    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) return;
        forceFirst(player, session, "death");
    }

    /** 玩家登出（退出游戏/服务器）⇒ 强制第一个选项。 */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Session session = SESSIONS.get(event.getEntity().getUUID());
        if (session == null) return;
        forceFirst(event.getEntity(), session, "logout");
    }
}
