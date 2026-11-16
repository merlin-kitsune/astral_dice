package com.merlinkitsune.astral_dice.platform.api.distmarker;

/**
 * 运行侧 shim(对齐 Forge 的 {@code api.distmarker.Dist})。
 * Fabric 侧用 {@code FabricLoader#getEnvironmentType()} 判定,本枚举只保留 Forge 的取值名。
 */
public enum Dist {
    CLIENT,
    DEDICATED_SERVER
}
