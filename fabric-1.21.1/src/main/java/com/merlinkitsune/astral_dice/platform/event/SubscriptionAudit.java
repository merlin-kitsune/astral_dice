package com.merlinkitsune.astral_dice.platform.event;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

/**
 * 「订阅类忘了登记」的专职探测器。
 *
 * <h2>它补的是什么盲区</h2>
 * {@link LoaderBus#dispatchReport()} 只能列出<b>已经注册过</b>的事件类 ——
 * 若某个类带 {@code @SubscribeEvent} 却从未被 {@code LoaderBus.register(...)} 调用，
 * 那条链路既不会出现在「已派发」也不会出现在「未派发」里，等于<b>看不见</b>。
 * 本模组在 2026-09-29 恰好栽在这上面（{@code AstralDiceMod.registerListeners()} 漏登记本类
 * ⇒ 兼容性校验 / 网络注册 / 卡牌注册表三者全部从未执行），因此改为在启动时**扫描注解**取证。
 *
 * <h2>类枚举走三个来源（合并去重）</h2>
 * <ol>
 *   <li>{@code FabricLoader#getModContainer(modId).getRootPaths()} —— 语义最正，但 dev 下
 *       可能只给出资源目录（实测 2026-09-29：dev 里它没覆盖到 classes 输出 ⇒ 扫到 0 个类）；</li>
 *   <li>{@code ClassLoader#getResources(packagePath)} —— dev 下给出 {@code file:} 目录、
 *       生产下给出 {@code jar:} 条目，覆盖最广；</li>
 *   <li>本类自身的 {@code CodeSource} —— 兜底（dev = classes 目录；生产 = 模组 jar）。</li>
 * </ol>
 * 三者取并集，因此任一路径失效都不会让审计「静默通过」。
 *
 * <h2>为什么默认只告警、不抛异常</h2>
 * 「带注解但未登记」未必是缺陷（客户端类在服务端本就不该登记），故默认 WARN + 列清单。
 * 需要把它当闸门用的场景（测试台断言）可加 JVM 参数
 * {@code -Dastral_dice.strictBusAudit=true}，此时改为抛 {@link IllegalStateException}（fail-loud）。
 *
 * <p>⚠️ 若一个带注解的类都没扫到，会打 WARN 明确说「审计未生效」并**附带三个来源的诊断** ——
 * 否则会变成另一种假绿。
 */
public final class SubscriptionAudit {

    private static final Logger LOGGER = LoggerFactory.getLogger("AstralDice");

    /** 打开后「未登记」升级为致命错误（供测试台断言）。 */
    private static final String STRICT_PROPERTY = "astral_dice.strictBusAudit";

    /** 本模组根包（扫描范围）。 */
    private static final String SCAN_PACKAGE = "com.merlinkitsune.astral_dice";
    private static final String SCAN_PATH = SCAN_PACKAGE.replace('.', '/');

    /** 客户端专属包：**服务端**扫描时必须排除 —— 它们由 {@code AstralDiceClient} 在客户端登记。 */
    private static final List<String> CLIENT_ONLY_PACKAGES = List.of(
            SCAN_PACKAGE + ".client.",
            SCAN_PACKAGE + ".platform.client.");
    private static final List<String> CLIENT_ONLY_TYPES = List.of(
            SCAN_PACKAGE + ".AstralDiceClient");

    /** 不参与运行时事件总线的包（报了也是噪声）。 */
    private static final List<String> NON_RUNTIME_PACKAGES = List.of(
            SCAN_PACKAGE + ".mixin.",
            SCAN_PACKAGE + ".datagen.");

    private SubscriptionAudit() {
    }

    /** 扫描结果；{@code sources} 是类枚举诊断（仅在未生效时打出来）。 */
    public record Report(int discovered, int scanned, List<String> unregistered, String sources) {
    }

    /**
     * 服务端视角审计（在 {@code AstralDiceMod#onInitialize} 末尾调用）：
     * 排除客户端专属类，因为它们此时尚未登记（客户端入口晚于本入口）。
     */
    public static void verifyServerSide() {
        verify(false);
    }

    /**
     * 客户端视角审计（在 {@code AstralDiceClient#onInitializeClient} 末尾调用）：
     * 此时服务端与客户端两批登记都已完成，故**不**排除客户端包 —— 这是最完整的一次审计。
     */
    public static void verifyClientSide() {
        verify(true);
    }

    private static void verify(boolean clientEnvironment) {
        Report report = scan(clientEnvironment);
        if (report.scanned() == 0) {
            LOGGER.warn("[Astral Dice] 事件订阅审计**未生效**:共枚举到 {} 个类,其中 0 个带 @SubscribeEvent"
                            + "(classpath 枚举失败?) —— 来源诊断: {}",
                    report.discovered(), report.sources());
            return;
        }
        if (report.unregistered().isEmpty()) {
            LOGGER.info("[Astral Dice] 事件订阅审计通过:{} 个订阅类全部已登记(共枚举 {} 个类)",
                    report.scanned(), report.discovered());
            return;
        }
        String message = "[Astral Dice] 事件订阅审计:发现 " + report.unregistered().size()
                + " 个带 @SubscribeEvent 却**未登记**的类,其处理器永不触发 → " + report.unregistered();
        if (Boolean.getBoolean(STRICT_PROPERTY)) {
            throw new IllegalStateException(message + "（由 -D" + STRICT_PROPERTY + "=true 升级为致命）");
        }
        LOGGER.warn(message);
    }

    /** 扫描并用当前已登记的类名做差集。 */
    public static Report scan(boolean clientEnvironment) {
        Set<String> registered = new HashSet<>();
        for (Class<?> cls : LoaderBus.INSTANCE.registeredOwners()) {
            registered.add(cls.getName());
        }
        Collected collected = collect();
        ClassLoader loader = SubscriptionAudit.class.getClassLoader();
        int scanned = 0;
        List<String> unregistered = new ArrayList<>();
        for (String name : collected.names()) {
            if (isExcluded(name, clientEnvironment)) {
                continue;
            }
            Class<?> cls;
            try {
                cls = Class.forName(name, false, loader);
            } catch (Throwable ignored) {
                // 类加载失败 = 依赖缺席（典型是服务端遇到客户端专有类）⇒ 不作为缺陷
                continue;
            }
            if (!hasSubscribeEventMethod(cls)) {
                continue;
            }
            scanned++;
            if (!registered.contains(name)) {
                unregistered.add(name);
            }
        }
        unregistered.sort(Comparator.naturalOrder());
        return new Report(collected.names().size(), scanned, unregistered, collected.sources());
    }

    private static boolean isExcluded(String className, boolean clientEnvironment) {
        for (String prefix : NON_RUNTIME_PACKAGES) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        if (!clientEnvironment) {
            for (String prefix : CLIENT_ONLY_PACKAGES) {
                if (className.startsWith(prefix)) {
                    return true;
                }
            }
            return CLIENT_ONLY_TYPES.contains(className);
        }
        return false;
    }

    private static boolean hasSubscribeEventMethod(Class<?> cls) {
        if (cls.isInterface() || cls.isAnnotation() || cls.isEnum() || cls.isSynthetic()) {
            return false;
        }
        try {
            for (Method method : cls.getDeclaredMethods()) {
                if (method.isAnnotationPresent(SubscribeEvent.class) && method.getParameterCount() == 1) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
            // getDeclaredMethods 会解析方法签名,遇到缺失类型会抛 ⇒ 视为「无法判定」
            return false;
        }
        return false;
    }

    // ─────────────────────────── 类枚举（三来源） ───────────────────────────

    private record Collected(Set<String> names, String sources) {
    }

    private static Collected collect() {
        Set<String> names = new TreeSet<>();
        List<String> diagnostics = new ArrayList<>();

        // 来源 1：Fabric 的模组根路径
        ModContainer container = FabricLoader.getInstance().getModContainer("astral_dice").orElse(null);
        if (container == null) {
            diagnostics.add("modContainer=absent");
        } else {
            List<Path> roots = container.getRootPaths();
            diagnostics.add("rootPaths=" + roots);
            for (Path root : roots) {
                collectFromRoot(names, root);
            }
        }

        // 来源 2：ClassLoader 资源（dev=file: 目录 / 生产=jar: 条目）
        collectFromClassLoader(names, diagnostics);

        // 来源 3：本类自身的 CodeSource（dev=classes 目录 / 生产=模组 jar）
        collectFromCodeSource(names, diagnostics);

        return new Collected(names, diagnostics.isEmpty() ? "(none)" : String.join(" | ", diagnostics));
    }

    /** 从「类根」收集：目录走 {@code Files.walk}，jar 走条目遍历。 */
    private static void collectFromRoot(Set<String> names, Path root) {
        try {
            if (Files.isDirectory(root)) {
                Path base = root.resolve(SCAN_PATH);
                if (!Files.isDirectory(base)) {
                    return;
                }
                try (Stream<Path> stream = Files.walk(base)) {
                    stream.filter(Files::isRegularFile)
                            .filter(path -> path.toString().endsWith(".class"))
                            .forEach(path -> addClassName(names,
                                    base.relativize(path).toString().replace('\\', '/')));
                }
            } else if (root.toString().endsWith(".jar") && Files.isRegularFile(root)) {
                collectFromJar(names, root);
            }
        } catch (IOException ignored) {
            // 单个根读失败不致命,继续下一个来源
        }
    }

    private static void collectFromJar(Set<String> names, Path jarPath) {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            collectFromJarEntries(names, jar);
        } catch (IOException ignored) {
            // 同上
        }
    }

    private static void collectFromJarEntries(Set<String> names, JarFile jar) {
        String prefix = SCAN_PATH + "/";
        jar.stream()
                .map(ZipEntry::getName)
                .filter(name -> name.startsWith(prefix) && name.endsWith(".class"))
                .forEach(name -> addClassName(names, name.substring(prefix.length())));
    }

    private static void collectFromClassLoader(Set<String> names, List<String> diagnostics) {
        ClassLoader loader = SubscriptionAudit.class.getClassLoader();
        Enumeration<URL> roots;
        try {
            roots = loader.getResources(SCAN_PATH);
        } catch (IOException e) {
            diagnostics.add("classLoader=io-error(" + e.getClass().getSimpleName() + ")");
            return;
        }
        int hit = 0;
        while (roots.hasMoreElements()) {
            URL url = roots.nextElement();
            hit++;
            String protocol = url.getProtocol();
            try {
                if ("file".equals(protocol)) {
                    Path base = Paths.get(url.toURI());
                    if (Files.isDirectory(base)) {
                        try (Stream<Path> stream = Files.walk(base)) {
                            stream.filter(Files::isRegularFile)
                                    .filter(path -> path.toString().endsWith(".class"))
                                    .forEach(path -> addClassName(names,
                                            base.relativize(path).toString().replace('\\', '/')));
                        }
                    }
                } else if ("jar".equals(protocol)) {
                    JarURLConnection connection = (JarURLConnection) url.openConnection();
                    // 不复用 JVM 的缓存句柄,避免影响后续对同一 jar 的读取
                    connection.setUseCaches(false);
                    try (JarFile jar = connection.getJarFile()) {
                        collectFromJarEntries(names, jar);
                    }
                } else {
                    diagnostics.add("classLoader[" + hit + "]=" + protocol + "(unsupported)");
                }
            } catch (Exception e) {
                diagnostics.add("classLoader[" + hit + "]=" + protocol + "-error(" + e.getClass().getSimpleName() + ")");
            }
        }
        diagnostics.add("classLoaderResources=" + hit);
    }

    private static void collectFromCodeSource(Set<String> names, List<String> diagnostics) {
        try {
            var source = SubscriptionAudit.class.getProtectionDomain().getCodeSource();
            if (source == null || source.getLocation() == null) {
                diagnostics.add("codeSource=null");
                return;
            }
            Path location = Paths.get(source.getLocation().toURI());
            diagnostics.add("codeSource=" + location.getFileName());
            collectFromRoot(names, location);
        } catch (Throwable t) {
            diagnostics.add("codeSource=" + t.getClass().getSimpleName());
        }
    }

    private static void addClassName(Set<String> names, String relativePath) {
        String path = stripClassSuffix(relativePath);
        if (path.endsWith("module-info") || path.endsWith("package-info")) {
            return;
        }
        names.add(SCAN_PACKAGE + '.' + path.replace('/', '.'));
    }

    private static String stripClassSuffix(String path) {
        return path.endsWith(".class") ? path.substring(0, path.length() - ".class".length()) : path;
    }

    /** 当前是否客户端环境（供调用方决定审计入口）。 */
    public static boolean isClientEnvironment() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
    }
}
