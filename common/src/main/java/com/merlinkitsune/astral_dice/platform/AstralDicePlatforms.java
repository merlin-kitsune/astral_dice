package com.merlinkitsune.astral_dice.platform;

import com.merlinkitsune.starengine.api.astral.platform.AstralDicePlatform;

/**
 * 平台实现的全局持有者(主项目 {@code common} 源码根)。
 *
 * <p>写侧只有一个入口 {@link #install(AstralDicePlatform)},由各加载器线的
 * {@code AstralDiceMod} 入口在初始化早期调用一次;读侧 {@link #get()} / {@link #id()}
 * 供 {@code common} 里的门面使用。字段是 {@code volatile} 且只在 set 之后发布,
 * 不做加锁 —— 现有调用点全部落在模组初始化期(单线程)。
 */
public final class AstralDicePlatforms {

    /** 尚未安装时的返回值,调用方据此判定"平台未装配"。 */
    public static final String UNINSTALLED = "uninstalled";

    private static volatile AstralDicePlatform platform;

    private AstralDicePlatforms() {
    }

    /**
     * 安装平台实现:先做 null 检查,再赋值,最后调用 {@link AstralDicePlatform#install()}。
     *
     * <p>允许重复安装(后装覆盖先装),便于测试台按线切换;不保留历史实现。
     *
     * @throws NullPointerException {@code platform} 为 {@code null}
     */
    public static void install(AstralDicePlatform platform) {
        if (platform == null) {
            throw new NullPointerException("AstralDicePlatform must not be null");
        }
        AstralDicePlatforms.platform = platform;
        platform.install();
    }

    /** 当前已安装的实现;未安装时返回 {@code null}。 */
    public static AstralDicePlatform get() {
        return platform;
    }

    /** 当前平台 id;未安装时返回 {@link #UNINSTALLED}({@code "uninstalled"})。 */
    public static String id() {
        AstralDicePlatform current = platform;
        return current == null ? UNINSTALLED : current.id();
    }
}
