package com.merlinkitsune.astral_dice.network;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import java.io.InputStream;
import java.util.Locale;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 版本互通门槛(Version Gate)——长期规则,详见 AGENTS.md「版本互通门槛(Version Gate)」。
 *
 * <p>规则(2026-10-19 口径):</p>
 * <ul>
 *   <li><b>正式版</b>(版本串不含 {@code SNAPSHOT}):只有 <b>major.minor(二号位)相同</b>才互联
 *       —— {@code 1.2.x} ↔ {@code 1.2.y} 放行,{@code 1.1.x} ↔ {@code 1.2.0} 拒绝;</li>
 *   <li><b>快照版</b>(任一端版本串含 {@code SNAPSHOT}):必须<b>两端都是 SNAPSHOT 且完整版本串相同</b>
 *       才放行 —— 快照与任何其它构建(正式版、另一个快照)一律不互通。</li>
 * </ul>
 * <p>互通号因此有两态:正式版取 {@code major.minor}(如 {@code 1.2});快照取
 * {@code SNAPSHOT:<完整版本串>}(如 {@code SNAPSHOT:2.0.0-SNAPSHOT.132+neoforge_1.21.1})。
 * 快照互通号自带加载器后缀,故同一 mod 的两条加载器线之间互不相通(二进制不同,符合预期)。</p>
 *
 * <p>判据自动派生(禁止硬编码 "1.2"):构建期由 {@code build.gradle} 的
 * {@code generateModMetadata} 任务把 {@code gradle.properties} 的 {@code mod_version}
 * 展开进资源 {@code astral_dice_version.properties}(随 jar 发布,开发运行期同样在类路径上),
 * 运行期读取后截取前两段数字:{@code 1.2.0+neoforge_1.21.1} → {@code "1.2"}。
 * 将来升级二号位(1.2 → 1.3)只需改 {@code gradle.properties},本类与网络代码无需改动。</p>
 *
 * <p>实现机制(NeoForge 原生):该互通号直接作为载荷注册器的网络版本号交给
 * {@code RegisterPayloadHandlersEvent#registrar(String)}。NeoForge 在配置阶段做通道协商,
 * 逐通道用 {@code String.equals} 比较两端版本号({@code NetworkComponentNegotiator#validateComponent}),
 * 不等即协商失败:服务端先下发 {@code ModdedNetworkSetupFailedPayload}(含失配原因),
 * 随后断开连接;客户端据此显示「模组版本不匹配」屏幕(标题/表格见
 * {@code net.neoforged.neoforge.client.gui.ModMismatchDisconnectedScreen})。
 * 因此 1.1.X 客户端在<b>握手(配置)阶段</b>就被拒绝,无法进入游戏。</p>
 */
public final class VersionGate {
    /** 构建期由 generateModMetadata 从 mod_version 展开生成的资源。 */
    private static final String VERSION_RESOURCE = "/astral_dice_version.properties";
    private static final String VERSION_KEY = "mod_version";
    /** 两个来源都取不到时的保底值(同一构建的所有端一致,但会退化为「永远互通」,故同时打印错误日志)。 */
    private static final String UNRESOLVED = "unresolved";
    private static final Logger LOGGER = LoggerFactory.getLogger(AstralDiceMod.MODID + "-version-gate");

    /** 本地完整版本号,如 {@code 1.2.0+neoforge_1.21.1}。 */
    private static final String FULL_VERSION = resolveFullVersion();
    /** 快照互通号前缀:含 SNAPSHOT 的构建用完整版本串作互通号,不会与正式版或别的快照混淆。 */
    private static final String SNAPSHOT_PREFIX = "SNAPSHOT:";
    /** 网络互通号:正式版 {@code major.minor}(如 {@code 1.2});快照 {@code SNAPSHOT:<完整版本串>}。 */
    private static final String INTEROP_VERSION = interopNumberOf(FULL_VERSION);

    static {
        LOGGER.info("[Astral Dice] 版本互通门槛(Version Gate):mod_version={} → 网络互通号={};"
                + "正式版按二号位(major.minor)互通,含 SNAPSHOT 的构建只与完整版本串相同的快照互通。",
                FULL_VERSION, INTEROP_VERSION);
    }

    private VersionGate() {
    }

    /** 网络互通号(正式版 {@code major.minor};快照 {@code SNAPSHOT:<完整版本串>})。同值才允许互联。 */
    public static String interopVersion() {
        return INTEROP_VERSION;
    }

    /** 本地完整版本号(仅用于日志/诊断)。 */
    public static String fullVersion() {
        return FULL_VERSION;
    }

    /**
     * 远端版本号(互通号或完整版本号均可)是否与本端互通。
     *
     * <p>正式版比 {@code major.minor};任一端含 {@code SNAPSHOT} 时,两端必须都是 SNAPSHOT 且
     * 完整版本串相同(即互通号 {@code SNAPSHOT:<完整版本串>} 相等)。</p>
     */
    public static boolean accepts(String remoteVersion) {
        if (remoteVersion == null) {
            return false;
        }
        if (isSnapshot(FULL_VERSION) || isSnapshot(remoteVersion)) {
            return interopNumberOf(FULL_VERSION).equals(interopNumberOf(remoteVersion));
        }
        return INTEROP_VERSION.equals(majorMinor(remoteVersion));
    }

    /** 版本串是否含 {@code SNAPSHOT}(大小写不敏感)。 */
    private static boolean isSnapshot(String version) {
        return version != null && version.toUpperCase(Locale.ROOT).contains("SNAPSHOT");
    }

    /**
     * 取互通号:正式版 → {@code major.minor};含 {@code SNAPSHOT} → {@code SNAPSHOT:<完整版本串>}。
     *
     * <p><b>幂等</b>:入参若已是 {@code SNAPSHOT:} 开头的互通号则原样(仅规范化前缀大小写)返回 ——
     * 握手时远端送来的是对方互通号而非完整版本号,不幂等就会把前缀叠两次而<b>永远判不等</b>。</p>
     */
    private static String interopNumberOf(String version) {
        if (version == null) {
            return "";
        }
        String trimmed = version.trim();
        if (trimmed.regionMatches(true, 0, SNAPSHOT_PREFIX, 0, SNAPSHOT_PREFIX.length())) {
            return SNAPSHOT_PREFIX + trimmed.substring(SNAPSHOT_PREFIX.length());
        }
        return isSnapshot(trimmed) ? SNAPSHOT_PREFIX + trimmed : majorMinor(trimmed);
    }

    /**
     * 取版本号的 major.minor 段。
     *
     * <p>{@code 1.2.0+neoforge_1.21.1} → {@code 1.2};{@code 1.1.3} → {@code 1.1};
     * {@code 2.0.0-SNAPSHOT.2} → {@code 2.0};{@code 1} → {@code 1.0}。
     * 完全解析不出主版本号时<b>原样返回</b>——这样不同版本串仍然是不同的互通号,不会误放行。</p>
     */
    public static String majorMinor(String version) {
        if (version == null) {
            return "";
        }
        String trimmed = version.trim();
        int cut = trimmed.length();
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == '-' || c == '+' || c == ' ') {
                cut = i;
                break;
            }
        }
        String core = trimmed.substring(0, cut);
        String[] parts = core.split("\\.");
        String major = leadingDigits(parts.length > 0 ? parts[0] : "");
        if (major.isEmpty()) {
            return trimmed;
        }
        String minor = leadingDigits(parts.length > 1 ? parts[1] : "");
        return major + "." + (minor.isEmpty() ? "0" : minor);
    }

    private static String leadingDigits(String value) {
        int i = 0;
        while (i < value.length() && value.charAt(i) >= '0' && value.charAt(i) <= '9') {
            i++;
        }
        return value.substring(0, i);
    }

    /**
     * 解析本地 mod_version。
     *
     * <p>首选构建期生成的资源(与加载器是否已初始化无关,dev 与发布包行为完全一致);
     * 兜底读加载器元数据({@code ModList} 的 {@code IModInfo#getVersion()},即 mods.toml 的 version)。</p>
     */
    private static String resolveFullVersion() {
        String fromResource = readVersionResource();
        if (fromResource != null && !fromResource.isBlank()) {
            return fromResource.trim();
        }
        try {
            return net.neoforged.fml.ModList.get().getModContainerById(AstralDiceMod.MODID)
                    .map(container -> container.getModInfo().getVersion().toString())
                    .filter(value -> !value.isBlank())
                    .orElseGet(() -> {
                        LOGGER.error("[Astral Dice] 版本互通门槛:加载器元数据中没有 {} 的版本号,互通号退化为 {}",
                                AstralDiceMod.MODID, UNRESOLVED);
                        return UNRESOLVED;
                    });
        } catch (Throwable t) {
            LOGGER.error("[Astral Dice] 版本互通门槛:读取加载器元数据失败,互通号退化为 " + UNRESOLVED, t);
            return UNRESOLVED;
        }
    }

    private static String readVersionResource() {
        try (InputStream in = VersionGate.class.getResourceAsStream(VERSION_RESOURCE)) {
            if (in == null) {
                LOGGER.warn("[Astral Dice] 版本互通门槛:类路径上找不到 {},改用加载器元数据解析版本号", VERSION_RESOURCE);
                return null;
            }
            Properties properties = new Properties();
            properties.load(in);
            return properties.getProperty(VERSION_KEY);
        } catch (Exception e) {
            LOGGER.warn("[Astral Dice] 版本互通门槛:读取 {} 失败({}),改用加载器元数据解析版本号",
                    VERSION_RESOURCE, e.toString());
            return null;
        }
    }
}
