package com.merlinkitsune.astral_dice.platform.fml.loading;

import java.nio.file.Path;
import java.util.function.Supplier;

import net.fabricmc.loader.api.FabricLoader;

/**
 * 运行目录 shim(对齐 Forge 的 {@code fml.loading.FMLPaths})。
 * Fabric 侧由 {@link FabricLoader#getConfigDir()} / {@link FabricLoader#getGameDir()} 提供。
 */
public final class FMLPaths {

    public static final Supplier<Path> CONFIGDIR = () -> FabricLoader.getInstance().getConfigDir();
    public static final Supplier<Path> GAMEDIR = () -> FabricLoader.getInstance().getGameDir();

    private FMLPaths() {
    }
}
