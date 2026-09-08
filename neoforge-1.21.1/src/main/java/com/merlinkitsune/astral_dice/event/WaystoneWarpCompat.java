package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.item.chip.WarpEngineChipItem;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Waystones 传送可选联动(仅 NeoForge,反射注册)。
 *
 * <p>Waystones 事件经 Balm 的 fireEvent 最终会转发到 NeoForge 事件总线,
 * 因此这里在 {@code waystones} 模组加载时通过反射向 NeoForge.EVENT_BUS 注册:
 * <ul>
 *   <li>{@code WaystoneTeleportEvent$Pre}:标记正在进行的 Waystone 传送,避免随后的
 *       跨维度 {@code EntityTravelToDimensionEvent} 提前给跃迁引擎结算一次;</li>
 *   <li>{@code WaystoneTeleportEntityEvent$Post}:实际传送成功后结算跃迁引擎;</li>
 *   <li>{@code WaystoneTeleportEvent$Complete}:清理 Pre 标记(无论成功/失败)。</li>
 * </ul>
 */
public final class WaystoneWarpCompat {
    private static final Logger LOGGER = LoggerFactory.getLogger(WaystoneWarpCompat.class);

    /** 标记最长保留时长:若 Waystone 传送被取消/失败而未触发 Post,标记过期后自动失效 */
    private static final long PENDING_TTL_TICKS = 200;

    /** player UUID -> 标记过期 gameTime */
    private static final Map<UUID, Long> PENDING = new HashMap<>();
    private static boolean registered = false;

    private WaystoneWarpCompat() {
    }

    /** 在 Waystones 模组加载后调用(仅在 NeoForge 主类中调用)。 */
    public static void init() {
        if (registered || !ModList.get().isLoaded("waystones")) {
            return;
        }
        try {
            registerWaystoneTeleportEvent("net.blay09.mods.waystones.api.event.WaystoneTeleportEvent$Pre",
                    WaystoneWarpCompat::onWaystonePre);
            registerWaystoneTeleportEvent("net.blay09.mods.waystones.api.event.WaystoneTeleportEntityEvent$Post",
                    WaystoneWarpCompat::onWaystoneEntityPost);
            registerWaystoneTeleportEvent("net.blay09.mods.waystones.api.event.WaystoneTeleportEvent$Complete",
                    WaystoneWarpCompat::onWaystoneComplete);
            registered = true;
            LOGGER.info("[Astral Dice] Waystone teleport integration enabled for 跃迁引擎.");
        } catch (Throwable t) {
            LOGGER.warn("[Astral Dice] Failed to register Waystone teleport integration: {}", t.toString());
        }
    }

    /** 供 EntityTravelToDimensionEvent 消费 Waystone 跨维度标记;返回 true 表示本次传送由 Waystone 处理 */
    public static boolean isPendingAndClear(Player player) {
        if (player == null || player.level().isClientSide()) return false;
        UUID uuid = player.getUUID();
        Long until = PENDING.get(uuid);
        if (until == null) return false;
        PENDING.remove(uuid);
        if (until < player.level().getGameTime()) {
            prune(player.level().getGameTime());
            return false;
        }
        return true;
    }

    private static void markPending(Player player) {
        long now = player.level().getGameTime();
        prune(now);
        PENDING.put(player.getUUID(), now + PENDING_TTL_TICKS);
    }

    private static void clearPending(Player player) {
        if (player != null) {
            PENDING.remove(player.getUUID());
        }
    }

    private static void prune(long now) {
        PENDING.entrySet().removeIf(e -> e.getValue() < now);
    }

    private static void registerWaystoneTeleportEvent(String className, Consumer<Object> handler) throws Exception {
        Class<?> eventClass = Class.forName(className);
        Class<?> busClass = Class.forName("net.neoforged.neoforge.common.NeoForge");
        Object bus = busClass.getField("EVENT_BUS").get(null);
        Method addListener = bus.getClass().getMethod("addListener", Class.class, Consumer.class);
        addListener.invoke(bus, eventClass, handler);
    }

    private static void onWaystonePre(Object event) {
        try {
            Object context = invoke(event, "getContext");
            if (context == null) return;
            Object entity = invoke(context, "getEntity");
            if (entity instanceof Player player) {
                markPending(player);
            }
        } catch (ReflectiveOperationException ignored) {
            // 不同版本 API 若缺少访问器则放弃标记,后续最多只会多结算一次,不会崩溃
        }
    }

    private static void onWaystoneEntityPost(Object event) {
        try {
            Object entity = invoke(event, "getTeleportedEntity");
            if (!(entity instanceof Player player)) return;
            Object result = invoke(event, "getTeleportResult");
            if (result != null && Boolean.TRUE.equals(invoke(result, "isSuccessful"))) {
                clearPending(player);
                WarpEngineChipItem.onPortalOrWaystoneTeleport(player);
            } else {
                clearPending(player);
            }
        } catch (ReflectiveOperationException ignored) {
            // 同上:版本不兼容时不结算,避免反射失败干扰正常游戏
        }
    }

    private static void onWaystoneComplete(Object event) {
        try {
            Object context = invoke(event, "getContext");
            if (context == null) return;
            Object entity = invoke(context, "getEntity");
            if (entity instanceof Player player) {
                clearPending(player);
            }
        } catch (ReflectiveOperationException ignored) {
            // 清理失败也无需处理;过期机制会兜底
        }
    }

    private static Object invoke(Object target, String methodName) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(methodName);
        return method.invoke(target);
    }
}
