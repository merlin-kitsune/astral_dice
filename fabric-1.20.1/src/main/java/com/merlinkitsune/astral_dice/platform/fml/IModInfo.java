package com.merlinkitsune.astral_dice.platform.fml;

/**
 * 模组元数据占位(对齐 Forge 的 {@code forgespi.language.IModInfo})。
 *
 * <p>本模组只用它作为 {@code ModLoadingException} 的构造参数(用于把「不兼容模组黑名单」
 * 的拒绝理由挂到启动失败界面上)。Fabric 侧该异常直接携带文本,不需要真实元数据 ⇒ 本接口
 * 只保留 {@link #getModId()} 以便日志可读。
 */
public interface IModInfo {
    String getModId();
}
