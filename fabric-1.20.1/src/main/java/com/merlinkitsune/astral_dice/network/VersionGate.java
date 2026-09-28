package com.merlinkitsune.astral_dice.network;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import java.io.InputStream;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 版本互通门槛(Version Gate)——长期规则,详见 AGENTS.md「版本互通门槛(Version Gate)」。
 *
 * <p>规则:只有 <b>major.minor(二号位)相同</b>的客户端与服务端才允许互联。</p>
 * <ul>
 *   <li>{@code 1.2.x} ↔ {@code 1.2.y}:放行;</li>
 *   <li>{@code 1.1.x} ↔ {@code 1.2.0}:拒绝。</li>
 * </ul>
 *
 * <p>判据自动派生(禁止硬编码 "1.2"):构建期由 {@code build.gradle} 的
 * {@code generateModMetadata} 任务把 {@code gradle.properties} 的 {@code mod_version}
 * 展开进资源 {@code astral_dice_version.properties}(随 jar 发布,开发运行期同样在类路径上),
 * 运行期读取后截取前两段数字:{@code 1.2.0+forge_1.20.1} → {@code "1.2"}。
 * 将来升级二号位(1.2 → 1.3)只需改 {@code gradle.properties},本类与网络代码无需改动。</p>
 *
 * <p>实现机制(Forge 原生,两处):</p>
 * <ol>
 *   <li><b>硬门槛(握手阶段拒绝)</b>:{@code NetworkRegistry.newSimpleChannel(...)} 的
 *       {@code networkProtocolVersion} 用本互通号,{@code clientAcceptedVersions} /
 *       {@code serverAcceptedVersions} 用 {@link #accepts(String)}。FML 在登录握手时经
 *       {@code HandshakeMessages.S2CModList} / {@code C2SModListReply} 交换各通道版本
 *       ({@code NetworkRegistry.buildChannelVersions()}),两端各自用谓词校验
 *       ({@code validateClientChannels} / {@code validateServerChannels});不匹配则服务端
 *       回 {@code S2CChannelMismatchData} 并断开、客户端置 {@code FML_MOD_MISMATCH_DATA},
 *       双方显示 Forge 的 {@code ModMismatchDisconnectedScreen}(列出模组名与两端版本)。</li>
 *   <li><b>服务器列表「兼容」标记(非门槛)</b>:{@code IExtensionPoint.DisplayTest} 扩展点,
 *       用同一判据判定多人生服列表图标——Forge 默认的 {@code MATCH_VERSION} 要求<b>完整版本号</b>
 *       完全相同,比本规则更严(1.2.0 与 1.2.1 会被标成不兼容),故显式注册本判据取代之。</li>
 * </ol>
 */
public final class VersionGate {
    /** 构建期由 generateModMetadata 从 mod_version 展开生成的资源。 */
    private static final String VERSION_RESOURCE = "/astral_dice_version.properties";
    private static final String VERSION_KEY = "mod_version";
    /** 两个来源都取不到时的保底值(同一构建的所有端一致,但会退化为「永远互通」,故同时打印错误日志)。 */
    private static final String UNRESOLVED = "unresolved";
    private static final Logger LOGGER = LoggerFactory.getLogger(AstralDiceMod.MODID + "-version-gate");

    /** 本地完整版本号,如 {@code 1.2.0+forge_1.20.1}。 */
    private static final String FULL_VERSION = resolveFullVersion();
    /** 网络互通号(major.minor),如 {@code 1.2}。 */
    private static final String INTEROP_VERSION = majorMinor(FULL_VERSION);

    static {
        LOGGER.info("[Astral Dice] 版本互通门槛(Version Gate):mod_version={} → 网络互通号={};"
                + "仅二号位(major.minor)相同才允许客户端与服务端互联。", FULL_VERSION, INTEROP_VERSION);
    }

    private VersionGate() {
    }

    /** 网络互通号(major.minor)。同值才允许互联。 */
    public static String interopVersion() {
        return INTEROP_VERSION;
    }

    /** 本地完整版本号(仅用于日志/诊断)。 */
    public static String fullVersion() {
        return FULL_VERSION;
    }

    /** 远端版本号(互通号或完整版本号均可)是否与本端同属一个 major.minor。 */
    public static boolean accepts(String remoteVersion) {
        return remoteVersion != null && INTEROP_VERSION.equals(majorMinor(remoteVersion));
    }

    /**
     * 取版本号的 major.minor 段。
     *
     * <p>{@code 1.2.0+forge_1.20.1} → {@code 1.2};{@code 1.1.3} → {@code 1.1};
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
            return net.minecraftforge.fml.ModList.get().getModContainerById(AstralDiceMod.MODID)
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
