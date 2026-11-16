package com.merlinkitsune.astral_dice.platform;

/**
 * Fabric 1.20.1 线的 Astral Dice 平台实现(id = {@code fabric})。
 *
 * <p>只实现 {@link AstralDicePlatform#id()}:<b>不 import 任何加载器类</b>,
 * 也不持有加载器侧状态 —— 平台差异装配留到后续阶段按需在 {@code install()} 里做。
 */
public final class FabricAstralDicePlatform implements AstralDicePlatform {

    @Override
    public String id() {
        return "fabric";
    }
}
