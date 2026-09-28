package com.merlinkitsune.astral_dice.platform.fml;

/**
 * 逻辑侧 shim(对齐 Forge 的 {@code fml.LogicalSide})。
 * Fabric 侧没有这个概念,但事件类({@code TickEvent} / {@code PlayerInteractEvent})的构造函数
 * 与字段用到它;值语义与 Forge 完全一致。
 */
public enum LogicalSide {
    CLIENT,
    SERVER;

    public boolean isClient() {
        return this == CLIENT;
    }

    public boolean isServer() {
        return this == SERVER;
    }
}
