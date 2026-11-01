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
 * <p>注册通道按事件的实物形态二选一(见 {@link #registerWaystoneTeleportEvent(String[], Consumer)}):
 * 1.21.1 的 {@code $Pre} 是老形态,走 {@code NeoForge.EVENT_BUS};26.1.2 的
 * {@code $Before/$Complete} 是 Balm 自定义事件,走它们自己的静态 {@code EVENT} 映射器:
 * <ul>
 *   <li>{@code WaystoneTeleportEvent$Pre}(Waystones 2.x 起改名为 {@code $Before}):标记正在进行
 *       的 Waystone 传送,避免随后的跨维度 {@code EntityTravelToDimensionEvent} 提前给跃迁引擎
 *       结算一次;实际类名按 {@link #TELEPORT_PRE_EVENT_CANDIDATES} 逐个探测;</li>
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

    /**
     * 「传送开始」事件的候选类名,按 Waystones 各代 API 的实际形态排序。
     *
     * <p>取证(实物 jar):{@code waystones-neoforge-26.1.2-26.1.2.16.jar} 的
     * {@code net/blay09/mods/waystones/api/event/} 下只有
     * {@code WaystoneTeleportEvent$Before/$Prepare/$After/$Complete},<b>没有</b> {@code $Pre};
     * 而 {@code waystones-neoforge-1.21.1-21.1.46.jar} 只有 {@code $Pre/$Prepare/$Post/$Complete}。
     * {@code $Before} 与 {@code $Pre} 同为「传送开始前、可取消」事件(两者都提供
     * {@code getContext()}),因此作为等效替代。
     */
    private static final String[] TELEPORT_PRE_EVENT_CANDIDATES = {
            "net.blay09.mods.waystones.api.event.WaystoneTeleportEvent$Pre",
            "net.blay09.mods.waystones.api.event.WaystoneTeleportEvent$Before",
            "net.blay09.mods.waystones.api.event.WaystoneTeleportEvent$Prepare"
    };

    private WaystoneWarpCompat() {
    }

    /** 在 Waystones 模组加载后调用(仅在 NeoForge 主类中调用)。 */
    public static void init() {
        if (registered || !ModList.get().isLoaded("waystones")) {
            return;
        }
        try {
            registerWaystoneTeleportEvent(TELEPORT_PRE_EVENT_CANDIDATES,
                    WaystoneWarpCompat::onWaystonePre);
            registerWaystoneTeleportEvent("net.blay09.mods.waystones.api.event.WaystoneTeleportEntityEvent$Post",
                    WaystoneWarpCompat::onWaystoneEntityPost);
            registerWaystoneTeleportEvent("net.blay09.mods.waystones.api.event.WaystoneTeleportEvent$Complete",
                    WaystoneWarpCompat::onWaystoneComplete);
            registered = true;
            LOGGER.info("[Astral Dice] Waystone teleport integration enabled for 跃迁引擎.");
        } catch (ClassNotFoundException absent) {
            // 类名不存在 = 该 Waystones 版本没有对应 API(未装 / 2.x 改名),属预期软兼容缺席,不是错误
            LOGGER.debug("[Astral Dice] Waystone teleport integration not available (API absent): {}",
                    absent.toString());
        } catch (Throwable t) {
            if (isPlatformUnregistrable(t)) {
                // 平台侧无法注册:Waystones 2.x(Balm 自定义事件)的抽象父类链以 Object 为父,
                // NeoForge bus 8.x 的 EventBus.validateAbstractChain 直接拒绝注册
                // ("Abstract event class ... has a non-abstract superclass class java.lang.Object")。
                // 这不是本模组的缺陷,也不是玩家可见故障,只是该平台上「跃迁引擎 × 指路石」联动缺席。
                LOGGER.debug("[Astral Dice] Waystone teleport integration not registrable on this platform: {}",
                        t.toString());
            } else {
                LOGGER.warn("[Astral Dice] Failed to register Waystone teleport integration: {}", t.toString(), t);
            }
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

    /**
     * 判定「注册失败」是否属于平台机制拒绝(而非本模组 bug)。
     *
     * <p>取证:26.1.2 实机 WARN 的 caused-by 为
     * {@code java.lang.IllegalArgumentException: Abstract event class net.blay09.mods.waystones.api.event.WaystoneTeleportEvent has a non-abstract superclass class java.lang.Object. The superclass must be made abstract.},
     * 抛出点 {@code net.neoforged.bus.EventBus.validateAbstractChain(EventBus.java:301)}。
     */
    private static boolean isPlatformUnregistrable(Throwable t) {
        Throwable cursor = t;
        while (cursor != null) {
            if (cursor instanceof IllegalArgumentException
                    && String.valueOf(cursor.getMessage()).contains("has a non-abstract superclass")) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private static void registerWaystoneTeleportEvent(String className, Consumer<Object> handler) throws Exception {
        registerWaystoneTeleportEvent(new String[]{className}, handler);
    }

    /**
     * 按候选顺序注册监听:第一个能被 {@code Class.forName} 加载的类胜出(跨 Waystones 版本的软兼容)。
     *
     * <p>注册通道有两条,按「该事件在实物 jar 里的真实形态」选择:
     * <ol>
     *   <li><b>Balm 自定义事件</b>(静态字段 {@code EVENT},类型
     *       {@code net.blay09.mods.balm.platform.event.EventMapper}):直接
     *       {@code EVENT.register(Consumer)}。Waystones 26.1.2.16 的
     *       {@code WaystoneTeleportEvent$Before/$Complete} 与
     *       {@code WaystoneTeleportEntityEvent$Post} 都是这一形态。</li>
     *   <li><b>NeoForge 事件总线</b>(老形态,如 1.21.1.46 的 {@code WaystoneTeleportEvent$Pre}
     *       没有 {@code EVENT} 字段):{@code NeoForge.EVENT_BUS.addListener(Class, Consumer)}。</li>
     * </ol>
     * 先看有没有 {@code EVENT} 再决定,而不是「总线失败再回退」——因为 Balm 事件的抽象父类链以
     * {@code Object} 为父,总线会抛
     * {@code IllegalArgumentException: Abstract event class ... has a non-abstract superclass},
     * 失败路径既慢又会污染日志。
     */
    private static void registerWaystoneTeleportEvent(String[] candidateClassNames, Consumer<Object> handler)
            throws Exception {
        Class<?> eventClass = resolveFirstPresentClass(candidateClassNames);
        Object eventMapper = findBalmEventMapper(eventClass);
        if (eventMapper != null) {
            registerOnBalmMapper(eventMapper, handler);
            return;
        }
        Class<?> busClass = Class.forName("net.neoforged.neoforge.common.NeoForge");
        Object bus = busClass.getField("EVENT_BUS").get(null);
        Method addListener = bus.getClass().getMethod("addListener", Class.class, Consumer.class);
        addListener.invoke(bus, eventClass, handler);
    }

    /**
     * 若该事件类带 Balm 的静态 {@code EVENT} 映射器则返回它,否则返回 {@code null}。
     *
     * <p>只按「字段名 + 实现的接口名」判定,不硬引用 Balm 的类(未装 Balm 的版本照样能走总线通道)。
     */
    private static Object findBalmEventMapper(Class<?> eventClass) {
        try {
            java.lang.reflect.Field eventField = eventClass.getField("EVENT");
            Object mapper = eventField.get(null);
            if (mapper == null) return null;
            for (Class<?> iface : mapper.getClass().getInterfaces()) {
                if ("net.blay09.mods.balm.platform.event.EventMapper".equals(iface.getName())) {
                    return mapper;
                }
            }
            // 实现类可能把接口放在父类上,再沿类层次扫一遍
            for (Class<?> cursor = mapper.getClass(); cursor != null; cursor = cursor.getSuperclass()) {
                for (Class<?> iface : cursor.getInterfaces()) {
                    if ("net.blay09.mods.balm.platform.event.EventMapper".equals(iface.getName())) {
                        return mapper;
                    }
                }
            }
        } catch (NoSuchFieldException | IllegalAccessException | RuntimeException ignored) {
            // 没有 EVENT 字段 = 老形态事件,走总线
        }
        return null;
    }

    /** {@code EventMapper.register(TCallback)} 单参重载;TCallback 擦除后就是 {@code Object}。 */
    private static void registerOnBalmMapper(Object eventMapper, Consumer<Object> handler) throws Exception {
        Method register = null;
        for (Method candidate : eventMapper.getClass().getMethods()) {
            if (!"register".equals(candidate.getName())) continue;
            Class<?>[] params = candidate.getParameterTypes();
            if (params.length == 1 && params[0].isAssignableFrom(handler.getClass())) {
                register = candidate;
                break;
            }
            if (params.length == 1 && params[0] == Object.class) {
                register = candidate;
                break;
            }
        }
        if (register == null) {
            throw new NoSuchMethodException("Balm EventMapper.register(callback) not found on "
                    + eventMapper.getClass().getName());
        }
        register.invoke(eventMapper, handler);
    }

    /**
     * 逐个尝试候选类名,返回第一个真实存在的类。
     *
     * <p>只有 {@link ClassNotFoundException}(= 本版本没有这个类)会继续试下一个;其它异常
     * ({@link LinkageError}、静态初始化失败等)属于真实故障,立即抛给调用方按 WARN 记录。
     */
    private static Class<?> resolveFirstPresentClass(String[] candidateClassNames) throws Exception {
        ClassNotFoundException lastAbsent = null;
        for (String candidateClassName : candidateClassNames) {
            try {
                return Class.forName(candidateClassName);
            } catch (ClassNotFoundException absent) {
                lastAbsent = absent;
            }
        }
        if (lastAbsent != null) {
            throw lastAbsent;
        }
        throw new ClassNotFoundException("empty candidate list for a Waystone teleport event");
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
