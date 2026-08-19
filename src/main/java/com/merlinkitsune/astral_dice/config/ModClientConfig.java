package com.merlinkitsune.astral_dice.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 客户端配置。
 * 配置版本号:每次新增配置选项时推进(备份旧文件并继承旧值)。
 */
public final class ModClientConfig {
    // 当前配置版本:新增配置选项时必须 +1(v2:移除目标头顶效果图标相关配置,该功能已迁移至独立模组)
    public static final int CONFIG_VERSION = 2;

    public static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("配置文件版本号:新增配置项时推进(自动备份旧文件并继承旧值)")
                .define("config_version", CONFIG_VERSION);
        SPEC = builder.build();
    }

    private ModClientConfig() {
    }
}
