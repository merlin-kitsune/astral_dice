package com.merlinkitsune.astral_dice.init;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.merlinkitsune.astral_dice.AstralDiceMod;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingException;
import net.minecraftforge.fml.ModLoadingStage;
import net.minecraftforge.forgespi.language.IModInfo;

/**
 * Mixin 运行时门控（**仅 forge-1.20.1**）：保证「Mixin 运行时一定有人提供」，同时不再把
 * Mixin Booster 写成 FML 硬依赖（见下）。
 *
 * <p><b>为什么需要它</b>：1.20.1 的 Forge 自身不含 Mixin —— 本模组的 18 个 Mixin
 * （{@code astral_dice.mixins.json}：14 个 common + 4 个 client）必须由外部运行时承载：
 * <ul>
 *   <li><b>Mixin Booster</b>（独立前置，0.1.3+）：纯 ModLauncher 服务 jar，自带
 *       {@code IModLocator} 读 jar 根 {@code mixinbooster_version.txt} 来注册 {@code mixinbooster} 这个 mod 条目；</li>
 *   <li><b>Sinytra Connector</b>（整合包常见，modId {@code connectormod}）：自带同一套 Mixin 运行时
 *       （内嵌 {@code fabric-mixin.jar} + {@code io.github.steelwoolmc.mixintransmog} 服务）。</li>
 * </ul>
 *
 * <p><b>二者互斥的既成事实（2026-09-29 实机定位，BMC4 玩家报告）</b>：Connector 在场时，
 * Mixin Booster 会在自己的 {@code MixinTransformationService} 里<b>主动关闭自己</b>，实机原文：
 * <pre>
 * [mixin-booster/]: Disabling Mixin Booster in favor of Connector
 * </pre>
 * 于是 {@code mixinbooster} 这个 mod 条目**根本不会注册**。此前 {@code mods.toml} 把它写成
 * {@code mandatory=true}，FML 依赖排序阶段即以
 * <pre>
 * Missing or unsupported mandatory dependencies:
 *   Mod ID: 'mixinbooster', Requested by: 'astral_dice', Expected range: '[0.1.3,)', Actual version: '[MISSING]'
 * </pre>
 * 硬拒启动 —— <b>玩家即使正确安装了 Mixin Booster 也进不去游戏</b>（这就是「装了前置仍报缺失」的根因）。
 *
 * <p><b>本门控的口径</b>：
 * <ol>
 *   <li>{@code mixinbooster} 已加载 ⇒ 放行（Booster 提供 Mixin）；</li>
 *   <li>否则 {@code connectormod}（或兼容别名 {@code connector}）已加载 ⇒ 放行（Connector 提供 Mixin）；</li>
 *   <li>两者皆无 ⇒ 抛 {@link ModLoadingException} 拒绝启动并上屏说明。</li>
 * </ol>
 * 第 3 条是刻意保留的：缺运行时的时候本模组**不报错、全部 Mixin 静默失效**，
 * 那才是最糟的状态（物品/容器/交易等 18 处守卫悄悄不生效）。拒绝启动的承诺与改动前一致，
 * 只是判定点从「FML 依赖排序」挪到了「本模组 common setup」。
 *
 * <p><b>为什么判定点选 COMMON_SETUP</b>：{@code ModList} 在此阶段已完整可用（构造阶段虽也能读到
 * {@code ModList}，但本模组既有约定是「构造阶段只做注册」，且 {@link ModCompatibilityCheck} 同处调用，
 * 保持一致）；异常上屏链路与 {@code ModCompatibilityCheck} 完全相同 ——
 * {@code ModContainer.buildTransitionHandler} 记入 future ⇒
 * {@code ModList.completableFutureFromExceptionList} 挂到 suppressed ⇒
 * {@code ModLoader.waitForTransition} 组成 {@code LoadingFailedException} 逐条上屏
 * （{@code LoadingErrorScreen} 渲染 {@code ModLoadingException#formatToString()}）。
 * ⚠️ 提示文案里**不要出现 {@code %} 与 {@code {}}**（会被当格式化占位符），也不要写进语言文件。
 *
 * <p><b>为什么不用声明式</b>：FML 的 {@code [[dependencies]]} 只能表达单项 mandatory/optional，
 * **无法表达「A 或 B」**；NeoForge 的 {@code type="incompatible"} 在 Forge 1.20.1 也不支持。
 * 故只能在运行时判定。
 */
public final class MixinRuntimeGate {

    /** Mixin Booster 的 modId（其 ModLocator 从 jar 根 mixinbooster_version.txt 注册）。 */
    public static final String MIXIN_BOOSTER_MODID = "mixinbooster";

    /**
     * Sinytra Connector 的 modId：1.20.1 版本注册为 {@code connectormod}（其 JarJar 内嵌
     * {@code Connector-*-mod.jar} 的 mods.toml 即此值）；{@code connector} 为兼容别名，防止上游改名后门控失效。
     */
    public static final List<String> CONNECTOR_MODIDS = List.of("connectormod", "connector");

    /** 供日志/测试台断言的稳定标记（形如 {@code MIXIN-GATE: provider=connectormod}）。 */
    private static final String MARKER = "MIXIN-GATE: provider=";

    private static final Logger LOGGER = LoggerFactory.getLogger("astral_dice/MixinRuntimeGate");

    private MixinRuntimeGate() {
    }

    /**
     * 在 **common setup**（{@code FMLCommonSetupEvent} 处理器内、{@code enqueueWork} 之前）调用。
     *
     * @throws ModLoadingException 既没有 Mixin Booster、也没有 Sinytra Connector 时抛出；
     *                             异常携带的即为给玩家的提示文本
     */
    public static void verifyOrThrow() {
        final boolean booster = ModList.get().isLoaded(MIXIN_BOOSTER_MODID);
        final String connector = CONNECTOR_MODIDS.stream()
                .filter(id -> ModList.get().isLoaded(id))
                .findFirst()
                .orElse(null);

        if (booster || connector != null) {
            // Connector 在场时 Booster 会自我禁用 ⇒ 实际提供方按 Connector 记（便于事后排查）
            final String provider = (connector != null) ? connector : MIXIN_BOOSTER_MODID;
            LOGGER.info("[星之骰戏] {} {}", MARKER, provider);
            if (connector != null && booster) {
                LOGGER.info("[星之骰戏] 检测到 Mixin Booster 与 Sinytra Connector 并存：Booster 会主动让位，"
                        + "本模组的 Mixin 由 Connector 提供的运行时承载（两者行为等价，无需移除任何一个）");
            }
            probeMixinSubsystem();
            return;
        }

        LOGGER.error("[星之骰戏] {} none（既无 Mixin Booster 也无 Sinytra Connector）", MARKER);
        throw new ModLoadingException(selfModInfo(), ModLoadingStage.COMMON_SETUP, buildMessage(), null);
    }

    /**
     * 只为诊断：确认 Sponge Mixin 子系统确实已就绪。
     *
     * <p>⚠️ <b>刻意只记日志、不参与拒绝判定</b>：Mixin 的类位于 ModLauncher 的转换层，
     * 反射能否命中随加载器/环境而变，用它做硬门槛会引入「明明能跑却被拒」的假阳性。
     * 真正的拒绝判据只有「Booster 或 Connector 是否加载」这一条。
     */
    private static void probeMixinSubsystem() {
        try {
            final Class<?> env = Class.forName("org.spongepowered.asm.mixin.MixinEnvironment");
            final Object current = env.getMethod("getCurrentEnvironment").invoke(null);
            final Object version = env.getMethod("getVersion").invoke(null);
            LOGGER.info("[星之骰戏] Mixin 子系统就绪：environment={} version={}", current != null, version);
        } catch (Throwable t) {
            LOGGER.warn("[星之骰戏] Mixin 子系统自检未完成（不影响加载，仅记录）：{}: {}",
                    t.getClass().getSimpleName(), t.getMessage());
        }
    }

    private static IModInfo selfModInfo() {
        return ModList.get().getModContainerById(AstralDiceMod.MODID)
                .map(container -> container.getModInfo())
                .orElse(null);
    }

    /** 拼装给玩家看的提示：说清「缺什么」「为什么必须要有」「两种装法分别怎么做」。 */
    private static String buildMessage() {
        return "[星之骰戏] 未检测到可用的 Mixin 运行时，已拒绝启动。\n\n"
                + "Minecraft 1.20.1 的 Forge 自身不含 Mixin 运行时，本模组的 18 处 Mixin "
                + "（物品使用守卫、容器防转移、村民交易、猪灵中立、客户端界面与渲染等）"
                + "必须由下列前置之一提供；两者都不存在时，这些 Mixin 会**静默失效**"
                + "（游戏能进，但相关机制悄悄不生效），因此本模组选择拒绝启动。\n\n"
                + "下列两种方式任选其一即可：\n\n"
                + "【一】单独安装 Mixin Booster（最省事，推荐）\n"
                + "  从 Modrinth 搜索 Mixin Booster，下载 0.1.3 或更高版本的 jar"
                + "（文件名形如 mixin-booster-0.1.3+1.20.1.jar），放进 mods 目录后重新启动。\n\n"
                + "【二】使用 Sinytra Connector（整合包里已经带了 Connector 时，什么都不用装）\n"
                + "  Sinytra Connector 自带同一套 Mixin 运行时，本模组检测到它就会直接使用，无需再装 Mixin Booster。\n"
                + "  注意：Connector 在场时 Mixin Booster 会自动让位（日志会打印 "
                + "\"Disabling Mixin Booster in favor of Connector\"），这是正常现象。\n\n"
                + "如果你**已经**安装了 Mixin Booster 却看到这条提示，请确认：\n"
                + "  - jar 是否真的放在了本整合包自己的 mods 目录里（不是别的实例的目录）；\n"
                + "  - 文件名是否为 mixin-booster-*.jar（不要改扩展名、不要放进子文件夹）；\n"
                + "  - 是否被启动器的「禁用」开关关掉了。";
    }
}
