package com.merlinkitsune.astral_dice.platform.fml;

import java.util.Optional;

import net.fabricmc.loader.api.FabricLoader;

/**
 * 已加载模组清单 shim(对齐 Forge 的 {@code fml.ModList})。
 *
 * <p>Fabric 侧由 {@link FabricLoader} 提供同样的信息;保留 Forge 的
 * {@code ModList.get().isLoaded(id)} / {@code getModContainerById(id)} 调用面可让 7 处调用点
 * **零改动**。
 *
 * <p>⚠️ {@link #getModContainerById(String)} **返回真实数据**(不是占位):
 * {@code VersionGate} 靠它读本模组版本号做握手互通,占位会让互通号恒为 UNRESOLVED。
 */
public final class ModList {

    private static final ModList INSTANCE = new ModList();

    public static ModList get() {
        return INSTANCE;
    }

    /** 某 modId 是否已加载。 */
    public boolean isLoaded(String modId) {
        return modId != null && FabricLoader.getInstance().isModLoaded(modId);
    }

    /** 取模组容器(含元数据);未加载时为 empty。 */
    public Optional<ModContainer> getModContainerById(String modId) {
        if (modId == null) {
            return Optional.empty();
        }
        return FabricLoader.getInstance().getModContainer(modId).map(container -> {
            var metadata = container.getMetadata();
            return new ModContainer(new IModInfo() {
                @Override
                public String getModId() {
                    return metadata.getId();
                }

                @Override
                public String getVersion() {
                    return metadata.getVersion().getFriendlyString();
                }
            });
        });
    }

    private ModList() {
    }
}
