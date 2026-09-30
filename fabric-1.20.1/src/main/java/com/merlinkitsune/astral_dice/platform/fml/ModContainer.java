package com.merlinkitsune.astral_dice.platform.fml;

/**
 * 模组容器 shim(对齐 Forge 的 {@code net.minecraftforge.fml.ModContainer} 的最小面)。
 *
 * <p>只保留 {@link #getModInfo()}:调用点形如
 * {@code ModList.get().getModContainerById(id).map(container -> container.getModInfo())}。
 */
public final class ModContainer {

    private final IModInfo modInfo;

    ModContainer(IModInfo modInfo) {
        this.modInfo = modInfo;
    }

    public IModInfo getModInfo() {
        return modInfo;
    }
}
