package com.merlinkitsune.astral_dice.platform.network;

import java.util.function.Predicate;
import java.util.function.Supplier;

import net.minecraft.resources.ResourceLocation;

/**
 * 通道工厂(对齐 Forge 的 {@code network.NetworkRegistry})。
 *
 * <p>Forge 的三个参数由 FML 登录握手驱动:{@code versionSupplier} 的值在登录阶段双向交换,
 * 两端的谓词各判一次,任一端不通过即断开连接。
 *
 * <p>Fabric 没有这套机制 ⇒ 三个参数**原样交给** {@link SimpleChannel} 保存(旧实现直接丢弃,
 * 使版本门成为死代码):版本串作为握手包上线上报的值,两个谓词用于启动自检;
 * 真正的运行时判定由本模组的显式握手包承担
 * ({@code com.merlinkitsune.astral_dice.network.VersionHandshake},协议 id 28/29,
 * 两端各调一次 {@code VersionGate.accepts})。
 * 保留参数形态是为了让 {@code ModNetwork} 的声明面与 forge 逐字一致。
 */
public final class NetworkRegistry {

    public static SimpleChannel newSimpleChannel(ResourceLocation name,
                                                 Supplier<String> versionSupplier,
                                                 Predicate<String> clientAcceptedVersions,
                                                 Predicate<String> serverAcceptedVersions) {
        return new SimpleChannel(name, versionSupplier, clientAcceptedVersions, serverAcceptedVersions);
    }

    private NetworkRegistry() {
    }
}
