package com.merlinkitsune.astral_dice.platform.client.gui.overlay;

/**
 * Forge 的 HUD 包装对象占位(对齐 Forge 的 {@code client.gui.overlay.ForgeGui})。
 *
 * <p>本模组的 3 个覆盖层**都不解引用** gui 参数(只用 {@code GuiGraphics} 与 {@code Minecraft}),
 * 因此这里只需提供一个可传入的实例。若将来有覆盖层要用 gui 的辅助方法,
 * 必须在此补齐并在 {@code AstralDiceClient} 接线时提供真实数据 —— 否则会 NPE。
 */
public final class ForgeGui {
    public static final ForgeGui INSTANCE = new ForgeGui();

    private ForgeGui() {
    }
}
