package com.merlinkitsune.astral_dice.api.client;

/**
 * 一条附属 HUD 图层。
 *
 * <p>渲染器类型在各线不一致：1.20.1 / 1.21.1 是 {@code GuiGraphics}（配
 * {@code DeltaTracker}），26.1.2 是 {@code GuiGraphicsExtractor}。common 不能引用任何
 * 客户端类（专用服务器也会加载 common），因此这里收成一个版本中立的 {@code Object}：
 * 实现方在五线各自的适配器里强转成本线的类型。
 *
 * <p>只在客户端调用。注册请用 {@link AstralHudLayers#register(String, AstralHudLayer)}，
 * 必须在客户端初始化完成前登记（模组构造期 / client setup 早期）。
 */
@FunctionalInterface
public interface AstralHudLayer {

    /**
     * @param graphics    本线渲染器（1.20.1 / 1.21.1 强转 {@code GuiGraphics}；
     *                    26.1.2 强转 {@code GuiGraphicsExtractor}）
     * @param partialTick 帧间插值系数（0..1）
     */
    void render(Object graphics, float partialTick);
}
