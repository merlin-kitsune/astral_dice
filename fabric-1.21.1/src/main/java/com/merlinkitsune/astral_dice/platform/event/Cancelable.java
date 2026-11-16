package com.merlinkitsune.astral_dice.platform.event;

import com.merlinkitsune.astral_dice.platform.event.Cancelable;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 标记事件可被取消(对齐 Forge 的 {@code @Cancelable})。 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Cancelable {
}
