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
