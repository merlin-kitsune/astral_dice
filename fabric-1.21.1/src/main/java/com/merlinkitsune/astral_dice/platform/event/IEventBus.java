package com.merlinkitsune.astral_dice.platform.event;

import java.util.function.Consumer;

/**
 * 事件总线接口(Fabric 侧自建) —— 形状对齐 Forge 的 {@code eventbus.api.IEventBus}
 * 中本模组实际用到的那部分。
 *
 * <p>调用面刻意与 Forge 一致,使 `@Mod.EventBusSubscriber` 的 63 个类与入口的
 * {@code bus.register(...)} 只需换 import。
 */
public interface IEventBus {

    /** 注册一个对象实例:扫描其 `@SubscribeEvent` 方法(实例方法与静态方法都支持)。 */
    void register(Object target);

    /** 注册一个类:只扫描其静态 `@SubscribeEvent` 方法(等价 Forge 的 class 注册)。 */
    void register(Class<?> target);

    /** 以 lambda 直接注册监听。 */
    <T extends Event> void addListener(EventPriority priority, boolean receiveCanceled, Class<T> type,
                                       Consumer<T> consumer);

    /** 派发事件;返回「是否已被取消」。 */
    boolean post(Event event);

    /** 派发任意事件对象(含前置库事件)。 */
    boolean postEvent(Object event);
}
