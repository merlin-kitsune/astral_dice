package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 事件卡「动画窗口」锁（服务端权威；用户裁决 2026-10-29 D4/D5）。
 *
 * <p>窗口 = {@link EventCardService#DELAY_TICKS}（弹卡 → 结算的 1.9s），由
 * {@link EventCardService#showThenRun} 在**弹卡那一 tick**为每个受影响玩家起锁。窗口期内：
 * <ul>
 *   <li>窗内禁止一切会打开选择界面 / 目标选择器的主动技能（立牌）与效果牌（{@code EventChoiceManager#start}、
 *       {@code BaseSignItem#performSkill} 的选择器分支、{@code BaseEffectCardItem} 的三处入口、
 *       {@code TargetSelectionManager#start}），窗一结束即可用（判定是 {@code now < until} 的纯比较）；</li>
 *   <li>玩家此刻已打开的选择器 / 事件选择会话被强制取消（不触发、不结算，见 showThenRun）。</li>
 * </ul>
 *
 * <p>为什么用「锁」而不是在各入口各写一份计时：这个窗口是**观察者可见的屏幕动画**时长，
 * 客户端 EventCardOverlay 的 1900ms 与 {@link EventCardService#DELAY_TICKS} 同源；一旦两处各算一套，
 * 就会出现「卡已经消失、界面已经打开」或反过来的漂移。
 *
 * <p>状态口径与 {@link GlobalEventGate} 完全一致：静态 Map、`synchronized` 互斥、
 * {@code gameTime} 绝对值（跨维度/跨 tick 稳定）、服务器停止时清空。
 */
@Mod.EventBusSubscriber(modid = AstralDiceMod.MODID)
public final class EventAnimationLock {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventAnimationLock.class);

    /** 动画窗口期内的拒绝提示 lang 键（三语同源；各调用点**禁止**自写字面量）。 */
    public static final String NOTICE_KEY = "msg.astral_dice.event_animating";

    /** 玩家 UUID → 窗口结束时刻（{@code level.getGameTime()} 口径；到期即删，不靠外部清理）。 */
    private static final Map<UUID, Long> ANIM_UNTIL = new ConcurrentHashMap<>();

    private EventAnimationLock() {
    }

    /**
     * 为玩家开启一个动画窗口（覆盖式：已有窗口未结束时取**新的**结束刻）。
     *
     * @param ticks 窗口时长；非正数视为无效（不建锁，避免「负时长」被误当成已过期）
     */
    public static synchronized void begin(ServerPlayer player, int ticks) {
        if (player == null || ticks <= 0) return;
        long until = player.serverLevel().getGameTime() + (long) ticks;
        ANIM_UNTIL.put(player.getUUID(), until);
        LOGGER.info("AP_EVENT_ANIM: begin:{}:{}", player.getGameProfile().getName(), until);
    }

    /**
     * 该玩家此刻是否处于动画窗口内（唯一判定入口；到期项在这里顺手删除，不必另起清理任务）。
     *
     * <p>机器行 `AP_EVENT_ANIM: block:{player}:{action}` 由**调用点**打印（各入口的 action 不同）。
     */
    public static synchronized boolean isAnimating(ServerPlayer player) {
        if (player == null) return false;
        UUID id = player.getUUID();
        Long until = ANIM_UNTIL.get(id);
        if (until == null) return false;
        if (player.serverLevel().getGameTime() >= until) {
            ANIM_UNTIL.remove(id);
            return false;
        }
        return true;
    }

    /** 清掉单个玩家的窗口（登出 / 死亡：留着也没有意义，且会让重登后的头 2 秒白锁）。 */
    public static synchronized void clear(ServerPlayer player) {
        if (player == null) return;
        if (ANIM_UNTIL.remove(player.getUUID()) != null) {
            LOGGER.info("AP_EVENT_ANIM: clear:{}", player.getGameProfile().getName());
        }
    }

    /** 服务器停止/切换存档时清空全部静态状态（否则重进后残留时间戳会误锁；口径同 GlobalEventGate#reset）。 */
    public static synchronized void reset() {
        boolean dirty = !ANIM_UNTIL.isEmpty();
        ANIM_UNTIL.clear();
        if (dirty) LOGGER.info("AP_EVENT_ANIM: reset");
    }

    /** 登出 / 死亡 ⇒ 清掉该玩家的窗口。 */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) clear(player);
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) clear(player);
    }

    /** 服务器停止钩子：清空锁表。 */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        reset();
    }
}
