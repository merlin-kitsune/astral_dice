package com.merlinkitsune.astral_dice.platform;

/**
 * Astral Dice 的平台抽象:主项目 {@code common} 源码根里唯一允许出现的"平台身份"概念。
 *
 * <p>每个子项目(加载器线)在 {@code AstralDiceMod} 入口的初始化早期注册一份实现,
 * 之后 {@code common} 里的代码只通过 {@link AstralDicePlatforms} 取平台身份,
 * 不直接 import 任何加载器类(NeoForge / Forge / Fabric 的 API 一律不下沉到这里)。
 *
 * <p>实现方不得在 {@link #id()} / {@link #install()} 里 import 加载器类:
 * 该类会被五个子项目以同一份源码编译,任何加载器专属符号都会把其他线拖挂。
 */
public interface AstralDicePlatform {

    /** 平台标识,五个实现分别返回 {@code neoforge} / {@code forge} / {@code fabric}。 */
    String id();

    /**
     * 平台装配钩子。{@link AstralDicePlatforms#install(AstralDicePlatform)} 设置完持有者后立即调用。
     *
     * <p>当前阶段为空实现(骨架):后续阶段若需要平台侧装配,在这里做,
     * 仍然不得把加载器类泄漏到调用方签名里。
     */
    default void install() {
    }
}
