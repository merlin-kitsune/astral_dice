package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.network.ModNetwork;
import com.merlinkitsune.starengine.component.GameplayConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 全员事件（公共 / 事件卡触发的事件）的<b>全局硬间隔闸门</b>（2026-10-21）。
 *
 * <p>语义：① 上一个事件仍在进行 ⇒ 拒绝；② 事件结束后再过 {@link #GAP_TICKS}t（1 秒）才允许下一个；
 * ③ 「进行中 + 结束后 1 秒」整段窗口都算不可开新事件。
 *
 * <p>落点：由 {@link AstralEventDispatcher} 在<b>事件卡使用入口</b>（{@code ITEM_USE}）统一拦一次 ——
 * 不在每个事件效果里各拦一次。闸门只拦 {@code ITEM_USE}；{@code ACTIVE_SKILL}（大侦探）**不占用也不受约束**
 * （2026-10-29 用户裁决 D1）。击杀驱动的 {@code INVESTIGATION_KILL} 与外部 {@code CUSTOM} 同样不占用、
 * 也不受此闸门约束。
 *
 * <p>状态载体：静态字段（服务端全局单例），game time 口径来自 {@code player.serverLevel().getGameTime()}；
 * {@code ServerStoppingEvent} 时整体清空，避免换存档 / 重进后残留时间戳误锁。
 */
@Mod.EventBusSubscriber(modid = AstralDiceMod.MODID)
public final class GlobalEventGate {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalEventGate.class);

    /** 间隔窗口（tick）：事件结束后的硬间隔 1 秒。 */
    public static final long GAP_TICKS = 20L;

    /** 同一玩家的拒绝提示最小间隔（tick）：≥20t 只提示一次（节流）。 */
    private static final long NOTICE_INTERVAL_TICKS = 20L;

    /** 拒绝提示的 lang 键（三语齐：zh_cn / en_us / ja_jp）。 */
    public static final String NOTICE_KEY = "msg.astral_dice.event_global_cooldown";

    /** 上一事件的**结束刻**（game time；已含 {@link EventCardService#DELAY_TICKS} 弹卡延迟）。0 = 无事件。 */
    private static long eventEndsAtGameTime = 0L;

    /** 闸门放行刻 = {@link #eventEndsAtGameTime} + {@link #GAP_TICKS}。now < 此值 ⇒ 拒绝。 */
    private static long gateUntilGameTime = 0L;

    /** 提示节流表：玩家 UUID → 上次提示的 game time。服务器停止时清空。 */
    private static final Map<UUID, Long> LAST_NOTICE_AT = new HashMap<>();

    private GlobalEventGate() {
    }

    /**
     * <b>只读</b>判定：此刻是否允许开新事件（拒绝/允许都不写任何状态、不提示、不消耗）。
     *
     * <p>用途（2026-10-21）：需要「先判后消耗」的调用方 —— 例如 {@code EventChoiceManager}
     * 必须先确认闸门放行、再移除玩家的事件选择会话；若直接用 {@link #tryAcquire} 预判，
     * 一旦放行就会写掉一次占位（双获取）。
     *
     * <p>⚠️ 本方法<b>只</b>比较 {@code now} 与 {@link #gateUntilGameTime}；{@code durationTicks}
     * 与 {@link #tryAcquire} 同签名（真正占用时才需要它），不参与判定。
     *
     * <p>时间口径与 {@link #tryAcquire} 完全一致：{@code player.serverLevel().getGameTime()}。
     *
     * @param player        触发者（必须为服务端玩家；调用方在服务端入口处调用）
     * @param durationTicks 事件的 {@code defaultDurationTicks}（本方法不使用，仅为与 tryAcquire 对齐）
     * @return true = 此刻可开新事件
     */
    public static synchronized boolean canStart(ServerPlayer player, long durationTicks) {
        return isOpenAt(player.serverLevel().getGameTime());
    }

    /**
     * 尝试占用全局事件闸门（允许 ⇒ 推进状态并返回 true；拒绝 ⇒ 返回 false 并（节流地）提示玩家）。
     *
     * <p>时间口径**只取一个来源**：{@code player.serverLevel().getGameTime()}。各维度的 gameTime
     * 由同一个 server tick 推进（非主世界走 {@code DerivedLevelData} 委托主世界数据），
     * 因此跨维度比较不会错位；仍统一从「触发者的服务端关卡」取，避免同一处混用两种来源。
     *
     * <p>2026-10-21 重构：拒绝判定先走只读的 {@link #canStart}（同一 {@code now} 口径），
     * 提示抽到 {@link #notifyBlocked}；语义与提示文案**逐字不变**。
     *
     * @param player        触发者（必须为服务端玩家；调用方在服务端入口处调用）
     * @param durationTicks 事件的 {@code defaultDurationTicks}（≤0 ⇒ 即刻结束，仍占满 GAP_TICKS）
     */
    public static synchronized boolean tryAcquire(ServerPlayer player, long durationTicks) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        if (!isOpenAt(now)) {
            notifyBlocked(player, durationTicks);
            LOGGER.info("AP_EVENT_GATE: denied:player={}:remain={}:gate_until={}",
                    player.getUUID(), gateUntilGameTime - now, gateUntilGameTime);
            return false;
        }
        long duration = Math.max(0L, durationTicks);
        // 事件真正开始/结束都在「弹卡 → 结算」那一刻：DELAY_TICKS 必须计入占用窗口，
        // 否则事件进行中的最后 38t 会露出真空期（可被连续触发）。
        eventEndsAtGameTime = now + EventCardService.DELAY_TICKS + duration;
        gateUntilGameTime = eventEndsAtGameTime + GAP_TICKS;
        LOGGER.info("AP_EVENT_GATE: acquired:duration={}:event_end={}:gate_until={}",
                duration, eventEndsAtGameTime, gateUntilGameTime);
        return true;
    }

    /** 闸门放行刻（game time；诊断/测试用）。 */
    public static synchronized long gateUntilGameTime() {
        return gateUntilGameTime;
    }

    /** 服务器停止/切换存档时清空全部静态状态（否则重进后残留时间戳会误锁）。 */
    public static synchronized void reset() {
        boolean dirty = gateUntilGameTime != 0L || !LAST_NOTICE_AT.isEmpty();
        eventEndsAtGameTime = 0L;
        gateUntilGameTime = 0L;
        LAST_NOTICE_AT.clear();
        if (dirty) LOGGER.info("AP_EVENT_GATE: reset");
    }

    /**
     * 拒绝提示（按玩家节流：≥{@link #NOTICE_INTERVAL_TICKS} 才再提示一次）。
     *
     * <p>公开给「先判后消耗」的调用方复用（例如选择会话被闸门拒绝时）；
     * 文案与 {@link #tryAcquire} 拒绝路径**逐字一致**：只报剩余秒数，不含时长。
     *
     * @param player        被提示的玩家
     * @param durationTicks 与 {@link #tryAcquire} 同签名（提示只用剩余秒数，本参数不参与文案）
     */
    public static synchronized void notifyBlocked(ServerPlayer player, long durationTicks) {
        long now = player.serverLevel().getGameTime();
        UUID id = player.getUUID();
        Long last = LAST_NOTICE_AT.get(id);
        if (last != null && now - last < NOTICE_INTERVAL_TICKS) return;
        LAST_NOTICE_AT.put(id, now);
        double remainSeconds = Math.max(0L, gateUntilGameTime - now) / 20.0D;
        Component notice = Component.translatable(NOTICE_KEY,
                String.format(java.util.Locale.ROOT, "%.1f", remainSeconds));
        ModNetwork.sendToPlayer(player, new ModNetwork.ActionBarMessage(
                notice, GameplayConstants.ACTIONBAR_DURATION_TICKS));
    }

    /** 只读判定本体（调用方必须已持有本类锁或处于同步方法内）：{@code now} 已达放行刻 ⇒ 可开。 */
    private static boolean isOpenAt(long now) {
        return now >= gateUntilGameTime;
    }

    /** 服务器停止钩子：清空闸门与节流表。 */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        reset();
    }
}
