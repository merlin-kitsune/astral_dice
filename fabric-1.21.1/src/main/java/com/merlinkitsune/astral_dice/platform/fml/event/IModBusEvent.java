package com.merlinkitsune.astral_dice.platform.fml.event;

/**
 * 「mod 生命周期总线事件」标记(对齐 Forge 的 {@code IModBusEvent})。
 *
 * <p>Fabric 侧没有 mod 总线,所有事件都走 {@code LoaderBus};该接口保留只为让
 * 从 Forge sources 转译过来的事件类(如 {@code RenderLevelStageEvent.RegisterStageEvent})
 * 能原样编译。
 */
public interface IModBusEvent {
}
