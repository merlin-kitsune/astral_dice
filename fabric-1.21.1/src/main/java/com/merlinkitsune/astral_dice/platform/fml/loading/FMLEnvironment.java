package com.merlinkitsune.astral_dice.platform.fml.loading;

import com.merlinkitsune.astral_dice.platform.api.distmarker.Dist;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

/**
 * 运行环境 shim(对齐 Forge 的 {@code fml.loading.FMLEnvironment})。
 *
 * <p>本模组只用 {@code FMLEnvironment.dist} 做「运行期才决定要不要走客户端分支」的判定
 * (见 {@code ModTooltipHandler#signKeyName}:把客户端调用收进 client 包,避免服务端解析到客户端类型)。
 */
public final class FMLEnvironment {

    public static final Dist dist =
            FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT
                    ? Dist.CLIENT : Dist.DEDICATED_SERVER;

    private FMLEnvironment() {
    }
}
