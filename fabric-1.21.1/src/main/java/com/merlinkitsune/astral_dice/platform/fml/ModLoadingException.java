package com.merlinkitsune.astral_dice.platform.fml;

/**
 * 装载期异常(对齐 Forge 的 {@code fml.ModLoadingException})。
 *
 * <p>本模组用它承载「不兼容模组黑名单」的拒绝理由:在通用初始化阶段抛出 ⇒
 * 游戏停在加载错误界面并显示 {@link #getMessage()} 原文(文案由消费方构造,不含 % 与 {})。
 * 与 Forge 的差异(已登记):Forge 会把该异常汇聚进 {@code LoadingFailedException} 并逐条上屏;
 * Fabric 侧由 loader 直接把异常文本显示在崩溃/加载失败界面,可见性等价。
 */
public class ModLoadingException extends RuntimeException {

    private final transient IModInfo modInfo;

    public ModLoadingException(IModInfo modInfo, ModLoadingStage stage, String message, Throwable cause) {
        super(message, cause);
        this.modInfo = modInfo;
    }

    public IModInfo getModInfo() {
        return modInfo;
    }
}
