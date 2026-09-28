package com.merlinkitsune.astral_dice.platform.event;

import com.merlinkitsune.astral_dice.platform.event.HasResult;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 标记事件携带结果(对齐 Forge 的 {@code @HasResult})。 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface HasResult {
}
