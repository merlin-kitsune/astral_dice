package com.merlinkitsune.astral_dice.mixin.fixes;

import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 补丁存活日志的 slf4j 接入点（非 Mixin 类，不会被合并进任何目标类；也不给
 * {@code LivingEntity} 增加任何字段/方法）。
 *
 * <p>{@link DamageStackSanitizer} 刻意只依赖 JDK 以便纯 Java 单测；这里单独承担
 * 「把一次性存活日志接到 slf4j」的职责，使其出现在 {@code logs/latest.log}
 * （dev run 的 log4j2 配置：Root → File appender {@code logs/latest.log}，文件级别默认 info），
 * 从而可以用 <code>grep neoforge_fixes logs/latest.log</code> 判定注入器是否真的生效。
 *
 * <p>未安装 slf4j 时不会走到这里：单测直接使用 {@link DamageStackSanitizer} 的默认
 * {@code System.out} 落点。
 */
final class NeoForgeFixesLog {

    /** 日志器名与补丁配置名对齐（{@code astral_dice.neoforge_fixes} ←→ 配置 astral_dice.neoforge_fixes.mixins.json）。 */
    private static final Logger LOGGER = LoggerFactory.getLogger("astral_dice.neoforge_fixes");

    /** 单例 sink，避免每次 {@link #install()} 新建 lambda。 */
    private static final Consumer<String> SINK = message -> LOGGER.info(message);

    private NeoForgeFixesLog() {
    }

    /** 把补丁存活日志接到 slf4j（幂等；由 Mixin 的 HEAD 注入器在每次 hurt 进入时调用，成本为一次引用比较）。 */
    static void install() {
        DamageStackSanitizer.setLogSink(SINK);
    }
}
