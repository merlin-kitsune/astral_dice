package com.merlinkitsune.astral_dice.platform.network;

import java.util.function.Predicate;
import java.util.function.Supplier;

import net.minecraft.resources.ResourceLocation;

/**
 * 通道工厂(对齐 Forge 的 {@code network.NetworkRegistry})。
 *
 * <p>Forge 的三个谓词参数(客户端/服务端接受的远端版本)由 FML 登录握手驱动;
 * Fabric 侧没有该机制 ⇒ 这些谓词**在此只做保留**,真正的版本互通判定由本模组的
 * 显式握手包承担(见 {@code VersionGate} 与 {@code network/ModNetwork} 的调用点)。
 * 保留参数是为了让 {@code ModNetwork} 的声明面零改动。
 */
public final class NetworkRegistry {

    public static SimpleChannel newSimpleChannel(ResourceLocation name,
                                                 Supplier<String> versionSupplier,
                                                 Predicate<String> clientAcceptedVersions,
                                                 Predicate<String> serverAcceptedVersions) {
        return new SimpleChannel(name);
    }

    private NetworkRegistry() {
    }
}
