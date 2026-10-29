package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.network.ActionBarPayload;
import com.merlinkitsune.starengine.component.GameplayConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 事件动画窗（2026-10-29 用户裁决 D4 / D5）的<b>服务端权威状态</b>。
 *
 * <p>语义：事件卡动画开始（{@link EventCardService#showThenRun}）⇒ {@link #begin} 给每个受影响玩家
 * 记下「动画中，直到 gameTime + {@link EventCardService#DELAY_TICKS}」；窗内禁止一切会打开选择界面 /
 * 目标选择器的主动技能（立牌）与效果牌（{@code EventChoiceManager#start}、
 * {@code BaseSignItem#performSkill}、{@code BaseEffectCardItem} 的三处入口、
 * {@code TargetSelectionManager#start}），窗一结束即可用（判定是 {@code now < until} 的纯比较）。
 *
 * <p>为什么状态必须在服务端：客户端 {@code EventCardOverlay} 只按墙钟推算动画相位，
 * 服务端拿不到；而「窗内不允许起新会话」是服务端权威判定。{@link EventCardService} 的结算队列
 * 既不记玩家、也只有一条 action，故动画窗状态统一落在这里。
 *
 * <p>时间口径与 {@link GlobalEventGate} 完全一致：一律取 {@code player.serverLevel().getGameTime()}；
 * 整段状态在 {@code ServerStoppingEvent} 时清空（{@link #reset}），避免换存档 / 重进后残留时间戳误锁。
 * 线程安全同样照该线既有风格：{@code ConcurrentHashMap} + 全静态 {@code synchronized} 方法。
 */
@EventBusSubscriber(modid = AstralDiceMod.MODID)
public final class EventAnimationLock {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventAnimationLock.class);

    /** 动画窗拒绝提示的 lang 键（三语齐：zh_cn / en_us / ja_jp）。 */
    public static final String NOTICE_KEY = "msg.astral_dice.event_animating";

    /** 同一玩家的拒绝提示最小间隔（tick）：照 {@link GlobalEventGate#notifyBlocked} 的 1 秒节流口径。 */
    private static final long NOTICE_INTERVAL_TICKS = 20L;

    /** 玩家 UUID → 动画结束刻（game time）。 */
    private static final Map<UUID, Long> ANIM_UNTIL = new ConcurrentHashMap<>();

    /** 提示节流表：玩家 UUID → 上次提示的 game time。 */
    private static final Map<UUID, Long> LAST_NOTICE_AT = new ConcurrentHashMap<>();

    private EventAnimationLock() {
    }

    /**
     * 事件动画开始：该玩家在随后 {@code ticks} 内处于动画窗（生产调用只传 {@link EventCardService#DELAY_TICKS}）。
     *
     * @param player 受该次动画影响的玩家（服务端）
     * @param ticks  窗口长度（tick）；{@code <= 0} 视为无窗，不写状态
     */
    public static synchronized void begin(ServerPlayer player, int ticks) {
        if (ticks <= 0) return;
        long until = player.serverLevel().getGameTime() + ticks;
        ANIM_UNTIL.put(player.getUUID(), until);
        LOGGER.info("AP_EVENT_ANIM: begin:{}:{}", player.getGameProfile().getName(), until);
    }

    /** 该玩家此刻是否仍在动画窗内；过期条目顺手移除（不返回永久锁死的时间戳）。 */
    public static synchronized boolean isAnimating(ServerPlayer player) {
        Long until = ANIM_UNTIL.get(player.getUUID());
        if (until == null) return false;
        long now = player.serverLevel().getGameTime();
        if (now < until) return true;
        ANIM_UNTIL.remove(player.getUUID(), until);
        return false;
    }

    /** 玩家登出 / 死亡时清掉该玩家的窗口状态（UUID 复用时不该继承旧窗口）。 */
    public static synchronized void clear(ServerPlayer player) {
        boolean dirty = ANIM_UNTIL.remove(player.getUUID()) != null;
        LAST_NOTICE_AT.remove(player.getUUID());
        if (dirty) LOGGER.info("AP_EVENT_ANIM: clear:{}", player.getGameProfile().getName());
    }

    /** 服务器停止 / 切换存档时清空全部静态状态（照 {@link GlobalEventGate#reset} 的既有写法）。 */
    public static synchronized void reset() {
        boolean dirty = !ANIM_UNTIL.isEmpty() || !LAST_NOTICE_AT.isEmpty();
        ANIM_UNTIL.clear();
        LAST_NOTICE_AT.clear();
        if (dirty) LOGGER.info("AP_EVENT_ANIM: reset");
    }

    /**
     * 动画窗拒绝提示（调用方**已**判定 {@link #isAnimating} 为 true）：机器行 + red actionbar。
     *
     * <p>按玩家节流（每 {@link #NOTICE_INTERVAL_TICKS}t 至多一条）：{@code tickHeldSelector} 每个 tick
     * 都会尝试自动开局，不节流会刷屏；节流后与 {@link GlobalEventGate#notifyBlocked} 的行为一致。
     *
     * @param player 被拒绝的玩家
     * @param action 动作标签（机器行用：sign_active_skill / effect_card_selector / target_select_start /
     *               event_choice_start / event_draw）
     */
    public static synchronized void notifyBlocked(ServerPlayer player, String action) {
        long now = player.serverLevel().getGameTime();
        UUID id = player.getUUID();
        Long last = LAST_NOTICE_AT.get(id);
        if (last != null && now - last < NOTICE_INTERVAL_TICKS) return;
        LAST_NOTICE_AT.put(id, now);
        LOGGER.info("AP_EVENT_ANIM: block:{}:{}", player.getGameProfile().getName(), action);
        PacketDistributor.sendToPlayer(player, new ActionBarPayload(
                Component.translatable(NOTICE_KEY).withStyle(ChatFormatting.RED),
                GameplayConstants.ACTIONBAR_DURATION_TICKS));
    }

    /** 玩家登出 ⇒ 清窗口（与 {@code EventChoiceManager} 的登出强制结算互不影响）。 */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) clear(player);
    }

    /** 玩家死亡 ⇒ 清窗口。 */
    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) clear(player);
    }

    /** 服务器停止钩子：清空窗口与节流表。 */
    @SubscribeEvent
    public static void onServerStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        reset();
    }
}
