package com.merlinkitsune.astral_dice.event;

/**
 * 事件效果:作用于事件上下文中收集到的所有目标。
 */
@FunctionalInterface
public interface EventEffect {
    void apply(EventContext context);
}
