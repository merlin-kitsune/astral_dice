package com.merlinkitsune.astral_dice.platform.event;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 最小可用事件总线(Forge 语义子集)。
 *
 * <h2>语义对齐点(必须保持)</h2>
 * <ol>
 *   <li><b>优先级排序</b>:{@link EventPriority#HIGHEST} 最先、{@link EventPriority#LOWEST} 最后;
 *       同优先级按注册顺序(稳定排序)。本模组伤害链依赖此项。</li>
 *   <li><b>继承派发</b>:注册在父类事件(如 {@code LivingEvent})上的监听器,子类事件
 *       (如 {@code LivingDeathEvent})派发时同样被调用。</li>
 *   <li><b>取消语义</b>:事件被取消后,`receiveCanceled = false`(默认)的监听器**不再**收到;
 *       已注册的 `receiveCanceled = true` 监听器仍收到。</li>
 *   <li><b>取消不中断派发</b>:与 Forge 一致 —— 取消只影响后续监听器的收取资格,派发流程继续
 *       (因此高优先级监听器的取消不会打断低优先级监听器的清理工作)。</li>
 * </ol>
 *
 * <p>⚠️ 与 Forge 的差异(已登记):不支持 {@code GenericEvent} 的按类型参数派发、
 * 不支持 {@code IEventBus#post} 的返回值语义之外的 Bus 特性。本模组零使用。
 */
public final class LoaderBus implements IEventBus {

    private static final class Listener {
        final EventPriority priority;
        final boolean receiveCanceled;
        final Class<?> eventType;
        final Consumer<Object> invoker;
        final long order;

        Listener(EventPriority priority, boolean receiveCanceled, Class<?> eventType,
                 Consumer<Object> invoker, long order) {
            this.priority = priority;
            this.receiveCanceled = receiveCanceled;
            this.eventType = eventType;
            this.invoker = invoker;
            this.order = order;
        }
    }

    /** 全局总线实例(mod 主总线)。 */
    public static final LoaderBus INSTANCE = new LoaderBus();

    private final Map<Class<?>, List<Listener>> listeners = new HashMap<>();

    /** 事件类 → 已派发次数(诊断用,见 {@link #dispatchReport()})。 */
    private final Map<Class<?>, java.util.concurrent.atomic.AtomicInteger> dispatchCounts =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 派发计数报告(诊断用)。
     *
     * <p>列出**每一个已注册监听器的事件类**及其派发次数 —— 0 次的一并列出,因为
     * 「某事件从未派发」正是本类问题的表现形式(桥没接、注入点写错、或注册漏了)。
     * 判据:至少 {@code ServerTickEvent} 必须是正数(它由 FAPI 的 tick 回调驱动,
     * 走的与所有其它事件**同一条 {@code post} 路径**)⇒ tick 有计数就说明桥是通的。
     */
    public String dispatchReport() {
        java.util.Set<Class<?>> all = new java.util.TreeSet<>(java.util.Comparator.comparing(Class::getSimpleName));
        all.addAll(listeners.keySet());
        all.addAll(dispatchCounts.keySet());

        StringBuilder fired = new StringBuilder();
        StringBuilder idle = new StringBuilder();
        int firedCount = 0;
        int idleCount = 0;
        for (Class<?> type : all) {
            java.util.concurrent.atomic.AtomicInteger counter = dispatchCounts.get(type);
            int n = counter == null ? 0 : counter.get();
            if (n == 0) {
                idleCount++;
                idle.append(type.getSimpleName()).append(' ');
            } else {
                firedCount++;
                fired.append(type.getSimpleName()).append('=').append(n).append(' ');
            }
        }
        return "\n  [已派发 " + firedCount + " 类] " + fired
                + "\n  [未派发 " + idleCount + " 类] " + idle;
    }
    private long counter = 0L;
    private boolean dispatching = false;
    private final List<Runnable> pendingAdds = new ArrayList<>();

    private void add(Listener listener) {
        if (dispatching) {
            pendingAdds.add(() -> listeners.computeIfAbsent(listener.eventType, k -> new ArrayList<>()).add(listener));
            return;
        }
        listeners.computeIfAbsent(listener.eventType, k -> new ArrayList<>()).add(listener);
    }

    @Override
    public void register(Object target) {
        if (target == null) {
            return;
        }
        if (target instanceof Class<?> cls) {
            register(cls);
            return;
        }
        scan(target.getClass(), target);
    }

    @Override
    public void register(Class<?> target) {
        if (target == null) {
            return;
        }
        scan(target, null);
    }

    private void scan(Class<?> owner, Object instance) {
        for (Method m : owner.getDeclaredMethods()) {
            SubscribeEvent ann = m.getAnnotation(SubscribeEvent.class);
            if (ann == null || m.getParameterCount() != 1) {
                continue;
            }
            Class<?> param = m.getParameterTypes()[0];
            // 放宽:前置库的事件基类(platform.LoaderEvent)在 Fabric 侧是空类,不是本包 Event 的子类。
            // 只要监听的参数类型能表示「一个事件对象」就允许注册。
            boolean isStatic = Modifier.isStatic(m.getModifiers());
            if (!isStatic && instance == null) {
                continue;
            }
            if (!m.canAccess(isStatic ? null : instance)) {
                m.setAccessible(true);
            }
            m.setAccessible(true);
            add(new Listener(ann.priority(), ann.receiveCanceled(), param, event -> {
                try {
                    m.invoke(isStatic ? null : instance, event);
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException("event listener not accessible: " + m, e);
                } catch (InvocationTargetException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof RuntimeException re) {
                        throw re;
                    }
                    if (cause instanceof Error err) {
                        throw err;
                    }
                    throw new RuntimeException(cause);
                }
            }, counter++));
        }
        // 父类里声明的静态处理器同样要注册(Forge 的 @EventBusSubscriber 只扫本类,这里放宽是安全的)
        Class<?> parent = owner.getSuperclass();
        if (parent != null && parent != Object.class && instance == null) {
            scan(parent, null);
        }
    }

    @Override
    public <T extends Event> void addListener(EventPriority priority, boolean receiveCanceled,
                                             Class<T> type, Consumer<T> consumer) {
        add(new Listener(priority, receiveCanceled, type,
                event -> consumer.accept(type.cast(event)), counter++));
    }

    @Override
    public boolean post(Event event) {
        return postEvent(event);
    }

    /**
     * 派发任意事件对象。
     *
     * <p>对 {@link Event} 子类应用取消语义;对前置库的事件类型
     * ({@code com.merlinkitsune.starenginelib.event.SignActiveTriggeredEvent} 等,
     * 其基类在 Fabric 侧是空类)只做普通派发 —— 语义等价于 Forge 侧该事件不可取消。
     */
    public boolean postEvent(Object event) {
        // 派发计数(诊断):让「桥装了但事件从不触发」这类**静默失效**可被断言 ——
        // 本项目已经踩过一次「FabricBridges.install() 从未被调用」的坑(见 AstralDiceMod)。
        dispatchCounts.computeIfAbsent(event.getClass(), k -> new java.util.concurrent.atomic.AtomicInteger())
                .incrementAndGet();
        List<Listener> chain = collect(event.getClass());
        if (chain.isEmpty()) {
            return event instanceof Event e && e.isCanceled();
        }
        dispatching = true;
        try {
            for (Listener l : chain) {
                if (event instanceof Event e && e.isCanceled() && !l.receiveCanceled) {
                    continue;
                }
                l.invoker.accept(event);
            }
        } finally {
            dispatching = false;
            if (!pendingAdds.isEmpty()) {
                List<Runnable> flush = new ArrayList<>(pendingAdds);
                pendingAdds.clear();
                flush.forEach(Runnable::run);
            }
        }
        return event instanceof Event e && e.isCanceled();
    }

    /** 精确类 + 全部父类上的监听器,按 优先级 → 注册序 排序。 */
    private List<Listener> collect(Class<?> eventClass) {
        List<Listener> out = new ArrayList<>();
        Class<?> c = eventClass;
        while (c != null && c != Object.class) {
            List<Listener> l = listeners.get(c);
            if (l != null) {
                out.addAll(l);
            }
            c = c.getSuperclass();
        }
        out.sort(Comparator.comparingInt((Listener l) -> l.priority.ordinal())
                .thenComparingLong(l -> l.order));
        return out;
    }
}
