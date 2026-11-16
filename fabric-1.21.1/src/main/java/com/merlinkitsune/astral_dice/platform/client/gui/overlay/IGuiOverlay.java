package com.merlinkitsune.astral_dice.platform.client.gui.overlay;

import net.minecraft.client.gui.GuiGraphics;

/**
 * HUD 覆盖层(对齐 Forge 的 {@code IGuiOverlay})。
 *
 * <p>Fabric 侧由 `AstralDiceClient` 在 {@code HudRenderCallback} 里逐帧调用。
 * {@code partialTick} 在 1.20.1 的 Fabric HUD 回调里**不可得**
 * ({@code HudRenderCallback} 只给 {@code GuiGraphics} 与 {@code tickDelta},后者即 partialTick),
 * 故本参数由调用方传入(实测 {@code HudRenderCallback} 的 {@code tickDelta} 就是它)。
 */
public interface IGuiOverlay {
    void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int width, int height);
}
