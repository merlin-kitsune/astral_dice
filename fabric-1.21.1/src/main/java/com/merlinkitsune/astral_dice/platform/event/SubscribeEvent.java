package com.merlinkitsune.astral_dice.platform.event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 订阅标记(对齐 Forge 的 {@code @SubscribeEvent})。
 *
 * <p>{@code receiveCanceled} 语义与 Forge 一致:{@code false}(默认)时,已取消的事件**不再**
 * 派发给该监听器。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SubscribeEvent {
    EventPriority priority() default EventPriority.NORMAL;

    boolean receiveCanceled() default false;
}
