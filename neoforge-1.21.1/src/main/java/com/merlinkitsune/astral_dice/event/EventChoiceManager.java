package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.event.api.AstralEvent;
import com.merlinkitsune.astral_dice.event.api.AstralEventTrigger;
import com.merlinkitsune.astral_dice.network.EventChoiceClosePayload;
import com.merlinkitsune.astral_dice.network.EventChoiceStartPayload;
import com.merlinkitsune.astral_dice.network.EventChoiceTickPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 大侦探立牌主动技能：<b>事件选择会话</b>的服务端权威（用户裁决 2026-10-10）。
 *
 * <p>流程：立牌主动技能（{@code FannySignItem#handleUse}）→ {@link #start(ServerPlayer)} 从现有事件池
 * {@link AstralEventRegistry#publicPool(Player)} <b>独立抽 2 个选项</b>（允许相同、不做正/负/中性平衡）
 * → 下发 {@link EventChoiceStartPayload} 打开选择界面 → 玩家 15 秒内选一个并点确认
 * → {@link #confirm(ServerPlayer, int, int)}：等待 1 秒 → 关界面
 * → 随后进入**既有事件触发流程**（{@link AstralEventDispatcher#trigger} →
 * {@code EventCardService#showThenRun}：弹卡 → 1.4s 动画 + 0.5s → 结算/音效）。
 *
 * <p><b>强制触发第一个选项</b>（不改走确认路径、不播音效）：15 秒超时未选 / 玩家死亡 / 玩家登出。
 *
 * <p>⚠️ 主动技能音效**在按下技能键（使用立牌）那一刻就播**（2026-10-11 用户裁决，与其它立牌一致）；
 * 本类在玩家确认时**不再**补播，避免同一声音叠放。
 */
@EventBusSubscriber(modid = AstralDiceMod.MODID)
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
        // 2026-10-29 用户裁决 D4：事件动画窗内禁止打开任何选择界面（大侦探立牌主动技能也不例外）。
        // 拒绝理由与其它入口同源：EventAnimationLock 管机器行 + actionbar（1 秒节流）。
        if (EventAnimationLock.isAnimating(player)) {
            EventAnimationLock.notifyBlocked(player, "event_choice_start");
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
        // 2026-10-29 P1 修复：候选池改按**该来源可派发**口径构造（publicPool(Player, source)），
        // 而不是只按「队内有秘密侦探」——候选里不允许出现本次派发必然失败的事件。
        List<AstralEvent> pool = AstralEventRegistry.publicPool(player, AstralEventTrigger.ACTIVE_SKILL);
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

        PacketDistributor.sendToPlayer(player, new EventChoiceStartPayload(token, first.id(), second.id(),
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
        // ③ 2026-10-29 用户裁决 D1（真解绑）：大侦探立牌主动技能全流程**不查也不占** GlobalEventGate ——
        //    原「canStart 只读预判 + notifyBlocked」整段删除。副作用（已接受）：侦探事件生效期间
        //    不占用全局事件窗口，可能与另一条计时事件在时间上重叠；节流改由技能自身冷却承担
        //    （BaseSignItem#performSkill 的玩家级冷却 + 锁定态）。
        //    顺序仍然只有一条：SESSIONS.remove → 发 Close → trigger（trigger 对 ACTIVE_SKILL 不再登记闸门）。
        //    OPTION_COUNT 固定为 2：索引 0/1 直接对应 firstEvent/secondEvent。
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
        PacketDistributor.sendToPlayer(player, new EventChoiceClosePayload(token));
        AstralEventDispatcher.trigger(player, session.chosen(), AstralEventTrigger.ACTIVE_SKILL);
    }

    /** 强制触发第一个选项（超时 / 死亡 / 登出）：不播主动技能音效，直接关界面并走事件触发流程。 */
    private static void forceFirst(Player player, Session session, String reason) {
        if (session.resolving >= 0) return;   // 玩家已确认 ⇒ 尊重其选择（会话由确认路径收尾）
        // 2026-10-29 P2 修复：`instanceof ServerPlayer` 的类型判断提到 `SESSIONS.remove` **之前** ——
        // 早退时不删会话、不置 resolving。原顺序（先删会话、再判类型）一旦在类型判断处早退，
        // 会话已消失、resolving 已置 0，这次强制结算永久丢失且无法重试；幂等语义不变
        // （resolving >= 0 与 remove 的 CAS 仍是两道闸门，日志行逐字不变）。
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        // ③ 2026-10-29 用户裁决 D1（真解绑）：全局闸门预判整段删除 —— 强制结算因此**必定**落盘，
        //    「remaining <= 0 却 return、会话还留着」的路径不再存在（见 tick 的到点分支）。
        //    副作用同 confirm：侦探事件不占用全局事件窗口，可能与另一条计时事件重叠。
        if (!SESSIONS.remove(player.getUUID(), session)) return;
        session.resolving = 0;
        PacketDistributor.sendToPlayer(serverPlayer, new EventChoiceClosePayload(session.token));
        // 2026-10-29 P1 修复：候选集是**打开界面那一刻**的快照，15 秒窗口内队内侦探可能已卸下立牌 /
        // 离队，于是首选项到派发时已稳定失败（AP_EVENT_NO_DETECTIVE）—— 结算就白丢了。这里按
        // **同一判据**（AstralEventRegistry#dispatchableFor）复核一次：首选项不可派发 ⇒ 改派屏上的
        // 另一个选项；两个都不可派发才交回派发器按既有口径拒绝。不放宽任何派发条件（不伪造 tier、
        // 不跨来源、不触发界面上没出现过的事件）。
        ResourceLocation forced = firstDispatchable(serverPlayer, session);
        LOGGER.info("AP_EVENT_CHOICE: force:{}:pick=0:reason={}:event={}",
                session.token, reason, forced);
        if (!forced.equals(session.firstEvent)) {
            LOGGER.info("AP_EVENT_CHOICE: force_fallback:{}:from={}:to={}",
                    session.token, session.firstEvent, forced);
        }
        AstralEventDispatcher.trigger(serverPlayer, forced, AstralEventTrigger.ACTIVE_SKILL);
    }

    /**
     * 强制结算实际派发的事件：优先首选项；首选项此刻已不可派发时退回**同一屏上的**第二个选项。
     * 两者都不可派发 ⇒ 仍返回首选项（由派发器打既有 {@code AP_EVENT_NO_DETECTIVE} 机器行）。
     */
    private static ResourceLocation firstDispatchable(ServerPlayer player, Session session) {
        if (isDispatchable(player, session.firstEvent)) return session.firstEvent;
        if (isDispatchable(player, session.secondEvent)) return session.secondEvent;
        return session.firstEvent;
    }

    /** 与候选池 / 派发器同源的可派发判定（来源固定为 {@link AstralEventTrigger#ACTIVE_SKILL}）。 */
    private static boolean isDispatchable(ServerPlayer player, ResourceLocation eventId) {
        return AstralEventRegistry.get(eventId)
                .map(e -> AstralEventRegistry.dispatchableFor(player, e, AstralEventTrigger.ACTIVE_SKILL))
                .orElse(false);
    }

    /**
     * 客户端退出意图（2026-10-29 用户裁决 D1/D2）：<b>退出即结算</b> —— 玩家没选就退出了界面
     * （ESC 两段式后的退出 / 被别的界面顶掉 / 换维度），一律按「强制第一个选项」结算，
     * 而不是把这次主动技能白丢掉。
     *
     * <p>2026-10-29 用户裁决：任何形式的退出都强制触发第一个事件，故本方法改名并改为结算语义
     * （原 {@code cancel} 只清会话 + 发 Close、不触发事件）；实现上直接转发 {@link #forceFirst}。
     *
     * <p>幂等：会话不存在 / token 不匹配 / 已结算（{@code resolving >= 0}）一律只记机器行后忽略 ——
     * 退出包与服务端超时/死亡/登出的强制结算可能在同一 tick 竞态，迟到的退出包到这里时会话已经不在；
     * 客户端在「已确认」或「服务端关屏」状态下关界面也不再发包（见 {@code EventChoiceScreen}）。
     *
     * @param player 退出者
     * @param token  会话 token（必须与当前会话一致）
     * @param reason 机器行用的原因（客户端 ESC = {@code "esc"}，换维度 = {@code "dimension"}；原样透传）
     */
    public static void exitWithoutChoice(ServerPlayer player, int token, String reason) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || session.token != token || session.resolving >= 0) {
            LOGGER.info("AP_EVENT_CHOICE: cancel_ignored:{}:player={}:reason={}",
                    token, player.getGameProfile().getName(), reason);
            return;
        }
        forceFirst(player, session, reason);
    }

    /**
     * 服务端强制清场（2026-10-29 用户裁决 D5）：忽略 token，把该玩家**已打开**的会话直接作废。
     * 供 {@code EventCardService#showThenRun} 在动画窗开始时调用 —— 不触发事件、不结算。
     */
    public static void cancelFor(ServerPlayer player, String reason) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || session.resolving >= 0) return;
        SESSIONS.remove(player.getUUID(), session);
        PacketDistributor.sendToPlayer(player, new EventChoiceClosePayload(session.token));
        LOGGER.info("AP_EVENT_CHOICE: cancel:{}:{}", session.token, reason);
    }

    /**
     * 15 秒等待期：① 向客户端同步**服务端权威**剩余 tick（倒计时唯一同源）；② 到期强制第一个选项。
     *
     * <p>2026-10-11 用户裁决「倒计时必须与 {@link #CHOICE_TIMEOUT_TICKS} 同源（以服务端剩余 tick 为准，
     * 别各算一套）」：客户端不再自行递减，改由本方法在**剩余秒数变化**时下发
     * {@link EventChoiceTickPayload}（≤16 包/会话）；这样即使玩家卡顿/掉帧，显示的也是服务端真实剩余秒数。
     */
    static void tick(Player player) {
        if (player.level().isClientSide()) return;
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || session.resolving >= 0) return;
        long remaining = session.expireTick - player.level().getGameTime();
        if (remaining <= 0L) {
            // 2026-10-29 用户裁决 D1：forceFirst 不再有闸门拒绝分支 ⇒ 到点必定 remove + Close + trigger，
            // 不会出现「remaining <= 0 却 return、会话仍在」的悬挂态。
            forceFirst(player, session, "timeout");
            return;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        int seconds = (int) ((remaining + 19L) / 20L);   // 向上取整：开局 299t ⇒ 15s
        if (seconds == session.syncedSecond) return;
        session.syncedSecond = seconds;
        PacketDistributor.sendToPlayer(serverPlayer,
                new EventChoiceTickPayload(session.token, (int) remaining));
        LOGGER.info("AP_EVENT_CHOICE: countdown:{}:remaining={}:sec={}",
                session.token, remaining, seconds);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        tick(event.getEntity());
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

    /**
     * 玩家换维度（传送门 / 跨维度 {@code /tp}）⇒ 强制第一个选项（2026-10-29 用户裁决 D1：
     * 「任何形式的退出」包含换维度）。客户端被 {@code DimensionTransitionScreen} 顶掉时不一定来得及发
     * 退出包，故服务端必须有这条钩子；与客户端 exit 意图靠 {@link #forceFirst} 的幂等去重。
     */
    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) return;
        exitWithoutChoice(player, session.token, "dimension");
    }
}
