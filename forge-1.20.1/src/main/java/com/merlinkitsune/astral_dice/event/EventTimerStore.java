package com.merlinkitsune.astral_dice.event;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 事件计时器存储(2026-10-10 事件体系重写)。
 *
 * <p>2026-10-10 用户裁决:计时器<b>每事件 × 每玩家独立</b>,重复触发<b>覆盖(重置)</b> ——
 * 因此这里是「玩家 → (事件 id → 到期刻)」两层的覆盖式写入,不做剩余时间叠加。
 *
 * <p><b>惰性到期检测</b>:不新增 server tick 钩子,任何一次查询({@link #remaining}/{@link #isActive})
 * 发现已过期时,就地移除并打一次机器行 {@code AP_EVENT_TIMER: expired:<id>}。
 */
public final class EventTimerStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventTimerStore.class);

    /** 玩家 → (事件 → 到期刻)。仅服务端使用。 */
    private static final Map<UUID, Map<ResourceLocation, Long>> EXPIRY = new HashMap<>();

    private EventTimerStore() {
    }

    /**
     * 覆盖式开始(或重置)一个计时器。
     *
     * @return 到期刻(game time)
     */
    public static long start(Player player, ResourceLocation eventId, int ticks) {
        long end = player.level().getGameTime() + Math.max(1, ticks);
        EXPIRY.computeIfAbsent(player.getUUID(), k -> new HashMap<>()).put(eventId, end);
        LOGGER.info("AP_EVENT_TIMER: start:{}:ticks={}:end={}", eventId, ticks, end);
        return end;
    }

    /** 剩余刻数(无计时器或已过期 ⇒ 0;过期时顺带到期处理)。 */
    public static long remaining(Player player, ResourceLocation eventId) {
        Map<ResourceLocation, Long> perPlayer = EXPIRY.get(player.getUUID());
        if (perPlayer == null) {
            return 0L;
        }
        Long end = perPlayer.get(eventId);
        if (end == null) {
            return 0L;
        }
        long left = end - player.level().getGameTime();
        if (left <= 0L) {
            perPlayer.remove(eventId);
            if (perPlayer.isEmpty()) {
                EXPIRY.remove(player.getUUID());
            }
            LOGGER.info("AP_EVENT_TIMER: expired:{}", eventId);
            return 0L;
        }
        return left;
    }

    /** 该玩家身上该事件是否有进行中的计时器。 */
    public static boolean isActive(Player player, ResourceLocation eventId) {
        return remaining(player, eventId) > 0L;
    }

    /** 清除某玩家某事件的计时器(不影响到期机器行)。 */
    public static void clear(Player player, ResourceLocation eventId) {
        Map<ResourceLocation, Long> perPlayer = EXPIRY.get(player.getUUID());
        if (perPlayer == null) {
            return;
        }
        perPlayer.remove(eventId);
        if (perPlayer.isEmpty()) {
            EXPIRY.remove(player.getUUID());
        }
    }

    /** 玩家离开时清理,避免内存泄漏。 */
    public static void forget(UUID uuid) {
        EXPIRY.remove(uuid);
    }

    /** 当前登记中的计时器条数(仅诊断/测试用)。 */
    public static int activeCount() {
        Set<ResourceLocation> all = new HashSet<>();
        for (Map<ResourceLocation, Long> perPlayer : EXPIRY.values()) {
            all.addAll(perPlayer.keySet());
        }
        return all.size();
    }
}
