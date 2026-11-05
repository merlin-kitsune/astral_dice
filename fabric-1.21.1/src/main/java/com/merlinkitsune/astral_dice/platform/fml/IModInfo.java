package com.merlinkitsune.astral_dice.platform.fml;

/**
 * 模组元数据 shim(对齐 Forge 的 {@code net.minecraftforge.forgespi.language.IModInfo})。
 *
 * <p>本模组只用到三处:① 作为 {@code ModLoadingException} 的构造参数(把「不兼容模组黑名单」
 * 的拒绝理由挂到启动失败界面上);② {@code VersionGate} 读取本模组自身的版本号用于握手互通。
 * Fabric 侧这些信息由 {@code FabricLoader#getModContainer} 直接提供,故本接口是**有真实数据**的
 * 轻量映射,而不是空壳。
 *
 * <p>{@link #getVersion()} 返回字符串:调用点写的是 {@code getVersion().toString()},
 * 对 {@link String} 恒等,故不需要再造一个 Version 类型。
 */
public interface IModInfo {
    String getModId();

    String getVersion();
}
