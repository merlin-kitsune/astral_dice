package com.merlinkitsune.astral_dice.platform.client.gui.overlay;

import net.minecraft.resources.ResourceLocation;

/**
 * 原版 HUD 元素锚点(对齐 Forge 的 {@code VanillaGuiOverlay},只保留本模组用到的成员)。
 *
 * <p>本模组用 {@code CROSSHAIR} 与 {@code AIR_LEVEL} 作为 `registerAbove` 的锚点。
 * ⚠️ Fabric 侧没有「相对原版元素排序」的 HUD 机制,本实现只保留 id 以维持调用面,
 * 实际渲染顺序 = 注册顺序且在原版 HUD 之后(见 {@code AstralDiceClient} 的 HUD 回调)。
 */
public enum VanillaGuiOverlay {
    CROSSHAIR("crosshair"),
    AIR_LEVEL("air_level");

    private final ResourceLocation id;

    VanillaGuiOverlay(String path) {
        this.id = new ResourceLocation("minecraft", path);
    }

    public ResourceLocation id() {
        return id;
    }
}
