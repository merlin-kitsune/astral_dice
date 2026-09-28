package com.merlinkitsune.astral_dice.platform.fml;

import java.util.Optional;

import net.fabricmc.loader.api.FabricLoader;


/**
 * 已加载模组清单 shim(对齐 Forge 的 {@code fml.ModList})。
 *
 * <p>Fabric 侧由 {@link FabricLoader} 提供同样的信息;保留 Forge 的
 * {@code ModList.get().isLoaded(id)} 调用面可让 7 处调用点**零改动**。
 *
 * <p>{@link #getModContainerById(String)} 返回的 {@link IModInfo} 只是占位
 * (Forge 用它构造 {@code ModLoadingException});Fabric 侧的启动失败提示走
 * {@code platform.fml.ModLoadingException},不再强依赖真实元数据。
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

    /** 占位实现:仅用于满足 Forge 形状的调用点。 */
    public Optional<IModInfo> getModContainerById(String modId) {
        return Optional.empty();
    }

    private ModList() {
    }
}
