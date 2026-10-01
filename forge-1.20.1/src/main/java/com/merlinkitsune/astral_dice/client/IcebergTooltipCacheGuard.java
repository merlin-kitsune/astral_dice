// Forge 47.4.x / MC 1.20.1
package com.merlinkitsune.astral_dice.client;

import java.lang.reflect.Field;

import com.merlinkitsune.astral_dice.AstralDiceMod;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 第三方提示框模组「边框颜色残留上一个物品」的**针对性缓解** —— Iceberg 全局颜色缓存复位。
 *
 * <h2>症状（2026-10-01 用户实报，附截图）</h2>
 * <p>① 与本模组无关的物品（`oritech:adamant_block`）提示框边框变成**纯色 `#FFC24B`**
 * （＝本模组**传奇档**的档位色）；② 本模组的**史诗**物品名字色正确、边框却是金色。
 * 用户原话：「**epic 物品会残留上次指向物品的颜色**」—— 关键词是「残留」：这不是配色写错，
 * 而是**状态泄漏**（上一个 tooltip 的颜色漏到了下一个）。
 *
 * <h2>根因（Iceberg，字节码实证）</h2>
 * <p>接管边框的是 **Iceberg**（`[冰山]`，由「进度牌匾」带入，**不需要装 LegendaryTooltips**）。
 * 它的颜色缓存是一个**全进程唯一**的静态字段：
 * <pre>
 *   // com/anthonyhilyard/iceberg/util/Tooltips（javap -p 实证）
 *   public static Tooltips$TooltipColors currentColors;
 *   public static final Tooltips$TooltipColors DEFAULT_COLORS;
 * </pre>
 * 在**报障当时那个包**（`狐の航空学 Voxy Edition`，1.21.1）里装的是 Iceberg **1.3.2**：
 * {@code currentColors} 只在**绘制过程中**被写（`com/anthonyhilyard/iceberg/mixin/TooltipRenderUtilMixin`
 * 拦截 {@code TooltipRenderUtil} 的五个绘制原语：传入色 ≠ 原版哨兵色 ⇒ 把该色 `putstatic` **缓存**；
 * 传入色 ＝ 哨兵色 ⇒ **改用缓存色绘制并 `cancel` 掉原版**），**没有任何逐 tooltip 复位**
 * ⇒ **只要上一次有人写过自定义色，之后所有「保持原版色」的 tooltip 都会继承它**。
 *
 * <p>⚠️ 本模组是这条缺陷的**放大器**而不是唯一受害者：本模组「不写色」的档位（当时的稀有/史诗）
 * 恰好就是「保持原版色」的那一类 ⇒ 必然被残留污染。本模组的自保手段是**自己写色**
 * （见 {@code client/RarityTooltipFrame}）；但**其它模组的物品、原版物品**仍会中招 ——
 * 那不是本模组能靠「写自己的色」解决的，必须**动 Iceberg 的状态**，即本类。
 *
 * <p>⚠️ ⚠️ **免责取证：报障当时那个包里的 Tooltip Overhaul 是 `.jar.disabled`**
 * （当日 13:0x 的目录列表与日志均可证），所以当时真正绘制边框的是 Iceberg；
 * 但**该状态随时可能被玩家改回**（当天 15:02 的日志里 TO 又已被加载）
 * ⇒ 本缓解**对两种状态都无害**：若 TO 接管，它自己画整个提示框、Iceberg 的绘制原语根本不会被执行，
 * 本类的复位**退化为 no-op**（既不产生效果，也不破坏 TO 的帧）。
 *
 * <h2>缓解做法 ＝ 每个 tooltip 开始前把缓存复位</h2>
 * <p>在**每次 tooltip 渲染的最开头**把 {@code Tooltips.currentColors} 反射写回
 * {@code Tooltips.DEFAULT_COLORS}（**厂商自己的默认常量**，不是我们发明的颜色）。语义：
 * <ul>
 *   <li>本模组/其它模组**主动设色**的 tooltip：复位发生在设色之前 ⇒ 颜色在自己的绘制过程中被缓存
 *       并立即生效，**行为完全不变**；</li>
 *   <li>**没设色**的 tooltip：复位后缓存＝该版本的默认值 ⇒ 不再继承上一个 tooltip 的颜色。</li>
 * </ul>
 *
 * <h2>⚠️ 必须先判断「这家是不是已经自己管着颜色」（Iceberg 两代架构不同）</h2>
 * <p>Iceberg 的颜色机制在两大版本之间**换过一次**，本类必须同时对两者成立：
 * <pre>
 *   1.3.2（报障包，1.21.1）              1.4.1.1（26.1.2 测试包）
 *   ───────────────────────────────     ──────────────────────────────────────────────
 *   谁写 currentColors：                 谁写 currentColors：
 *     只有绘制原语 mixin（缓存＋回灌）     它自己的工具提示前置钩子
 *                                          neoforge/mixin/GuiGraphicsMixin#preRenderTooltipForge
 *   逐 tooltip 复位：没有                 逐 tooltip 复位：**有**（每个 tooltip 开头写一次）
 *   DEFAULT_COLORS 取值：                  DEFAULT_COLORS 取值：
 *     原版四色 0xF0100010/0xF0100010/        **纯白**（TextColor.fromRgb(-1) ×4）
 *     0x505000FF/0x5028007F
 *   本类行为：**复位**（本类存在的理由）    本类行为：**跳过**（硬复位会把它刚设好的颜色抹成白色）
 * </pre>
 * <p>判据 = 反射探测 {@code Tooltips} 上是否存在 1.4.1.1 引入的两个**公开**标志
 * {@code gradientBackground} / {@code gradientBorder}（1.3.2 上没有这两个字段）：
 * <ul>
 *   <li>字段**不存在** ⇒ 老一代架构 ⇒ **每次都复位**（默认值恰好就是原版四色，复位＝回到原版）；</li>
 *   <li>字段存在且**任一为 true** ⇒ 本 tooltip 的颜色由 Iceberg 自己按
 *       {@code RenderTooltipEvents.ColorExt} 设置（例如 LegendaryTooltips）⇒ **跳过本次复位**，
 *       绝不覆盖别家的颜色；</li>
 *   <li>字段存在且都为 false ⇒ Iceberg 在该 tooltip 开头**已把缓存写回它自己的默认值**
 *       （纯白，但此时两个标志为 false ⇒ 它根本不会用这组颜色绘制）⇒ 我们再写一次是**等值 no-op**。</li>
 * </ul>
 * ⇒ 本类在**两代架构上都不改变别家的既有行为**，只在「没人管颜色」的那一代修掉残留。
 *
 * <h2>为什么复位点是「tooltip 渲染开头」而不是「每帧开头」</h2>
 * <p>缓存的写入发生在**绘制过程中**；若在帧末复位，反而会让同一次绘制的后半段读到已复位的值
 * ⇒ 必须卡在**同一次 tooltip 渲染之前**。各线的落点：
 * <ul>
 *   <li>NeoForge / Forge：本类的 {@code RenderTooltipEvent.Pre} 订阅 —— 它在
 *       {@code GuiGraphics#renderTooltipInternal} 的**第一条语句**触发
 *       （1.21.1 源码 `GuiGraphics.java:1495`），早于 `RenderTooltipEvent.Color`（同文件 `:1516`）
 *       与真正的绘制（`:1517`）；</li>
 *   <li>26.1.2：同一事件，落点 {@code GuiGraphicsExtractor#tooltip}（源码 `:1148`）——
 *       ⚠️ 注意 Iceberg 1.4.1.1 的**方法头**注入在它之前，这正是「必须跳过被管理态」的原因；</li>
 *   <li>Fabric 1.20.1：本线没有该事件的派发源，改由 {@code ClientTooltipBridgeMixin} 在
 *       {@code GuiGraphics#renderTooltipInternal} 的 `HEAD` 直接调用 {@link #reset()}。</li>
 * </ul>
 *
 * <h2>⚠️ 边界与安全</h2>
 * <ul>
 *   <li>**不硬依赖 Iceberg**：目标类用字符串名探测，缺席时静默（`ClassNotFoundException` 走 debug 级，
 *       不给绝大多数玩家制造日志噪音）。</li>
 *   <li>**只写 `currentColors` 一个字段**，且写的是厂商自己的 `DEFAULT_COLORS` 常量；
 *       Iceberg 的 {@code gradientBackground} / {@code gradientBorder} 标志**只读不写**。</li>
 *   <li>**fail-safe**：任何一次探测/写入失败都**永久停用**本缓解并打一条 warn，绝不重试、绝不抛出
 *       （tooltip 渲染在渲染线程上，抛异常 = 崩客户端）。</li>
 *   <li>逃生口：{@code -Dastral_dice.icebergTooltipGuard=false} 可整体关闭
 *       （与本仓 {@code astral_dice.strictBusAudit} 同一套系统属性惯例）。</li>
 * </ul>
 *
 * <p>⚠️ 本类只在客户端加载且位于 {@code client/} 包内；双端加载类不得引用它。
 * 反汇编取证与完整排查链见 `scripts/test/TESTING-SPEC.md`、`KNOWN-ISSUES.md` KI-D2
 * 与技能 `mc-thirdparty-tooltip-frame`。
 */
@Mod.EventBusSubscriber(modid = AstralDiceMod.MODID, value = Dist.CLIENT)
public final class IcebergTooltipCacheGuard {

    /** 本缓解的开关系统属性；写 `false` 即整体停用（默认启用）。 */
    public static final String ENABLED_PROPERTY = "astral_dice.icebergTooltipGuard";

    /** 目标类（第三方，**不做编译期依赖**）。 */
    private static final String ICEBERG_TOOLTIPS = "com.anthonyhilyard.iceberg.util.Tooltips";

    private static final Logger LOGGER = LoggerFactory.getLogger(IcebergTooltipCacheGuard.class);

    /** 探测只做一次（含「类缺席」与「已停用」两种结论）。 */
    private static boolean probed;
    /** 非 null = 缓解可用；写入失败时置回 null 即等于永久停用。 */
    private static Field currentColorsField;
    /** Iceberg 自己的默认颜色常量（{@code Tooltips.DEFAULT_COLORS}）。 */
    private static Object defaultColors;
    /** 1.4.1.1 才有：该 tooltip 是否由 Iceberg 自己按 ColorExt 管色（true ⇒ 我们不插手）。 */
    private static Field gradientBackgroundField;
    /** 同 {@link #gradientBackgroundField}，边框侧。 */
    private static Field gradientBorderField;

    private IcebergTooltipCacheGuard() {
    }

    @SubscribeEvent
    public static void onRenderTooltipPre(RenderTooltipEvent.Pre event) {
        reset();
    }

    /** 把 Iceberg 的全局颜色缓存复位到它自己的默认值。未装 Iceberg、已停用、或该 tooltip 正由 Iceberg 管色时为空操作。 */
    public static void reset() {
        if (!probed) {
            probe();
        }
        Field field = currentColorsField;
        if (field == null) {
            return;
        }
        try {
            if (icebergManagesColors()) {
                return; // 新一代 Iceberg 正按 tooltip 管色（如 LegendaryTooltips 的 ColorExt）⇒ 绝不覆盖
            }
            field.set(null, defaultColors);
        } catch (Throwable failure) {
            currentColorsField = null; // 永久停用，绝不反复抛
            LOGGER.warn("[Astral Dice] Iceberg 提示框颜色缓存复位失败、已停用该缓解: {}", failure.toString());
        }
    }

    /**
     * 该 tooltip 的颜色是否已由 Iceberg 自己接管（1.4.1.1 起的能力）。
     *
     * <p>1.3.2 一类老架构**没有**这两个字段 ⇒ 恒返回 {@code false}（我们负责复位）。
     * 新架构下这两个标志由 {@code preRenderTooltipForge} 在每个 tooltip 开头设置：
     * 命中 {@code RenderTooltipEvents.ColorExt} 才为 true（此时颜色是别家给的，不能动）；
     * 未命中则为 false 且 Iceberg 已把缓存写回它自己的默认值（我们再写一次是等值操作）。
     */
    private static boolean icebergManagesColors() throws IllegalAccessException {
        if (gradientBackgroundField == null || gradientBorderField == null) {
            return false;
        }
        return gradientBackgroundField.getBoolean(null) || gradientBorderField.getBoolean(null);
    }

    private static void probe() {
        probed = true;
        if ("false".equalsIgnoreCase(System.getProperty(ENABLED_PROPERTY, "true"))) {
            LOGGER.info("[Astral Dice] Iceberg 提示框颜色缓存缓解已被 -D{}=false 关闭。", ENABLED_PROPERTY);
            return;
        }
        try {
            Class<?> tooltips = Class.forName(ICEBERG_TOOLTIPS);
            // currentColors / DEFAULT_COLORS 都是 public（javap -p 实证），无需 setAccessible。
            Object defaults = tooltips.getField("DEFAULT_COLORS").get(null);
            if (defaults == null) {
                LOGGER.warn("[Astral Dice] Iceberg 的 DEFAULT_COLORS 为 null，缓解未启用。");
                return;
            }
            Field field = tooltips.getField("currentColors");
            field.set(null, defaults); // 先同步一次，避免本次启动的首个 tooltip 就吃到脏缓存
            defaultColors = defaults;
            currentColorsField = field;

            // 可选：1.4.1.1 起的两个「Iceberg 自己管色」标志（1.3.2 上没有）。
            Field background = null;
            Field border = null;
            try {
                background = tooltips.getField("gradientBackground");
                border = tooltips.getField("gradientBorder");
            } catch (NoSuchFieldException oldArchitecture) {
                // 老一代架构：没有这两个字段 ⇒ 每个 tooltip 都要我们复位（本类存在的理由）。
            }
            gradientBackgroundField = background;
            gradientBorderField = border;

            if (background != null) {
                LOGGER.info("[Astral Dice] Iceberg 提示框颜色缓存缓解已启用（新一代 Iceberg 自己管色的 tooltip 会被跳过）。");
            } else {
                LOGGER.info("[Astral Dice] Iceberg 提示框颜色缓存缓解已启用（老一代 Iceberg：每个 tooltip 渲染前复位 currentColors）。");
            }
        } catch (ClassNotFoundException absent) {
            // 未安装 Iceberg ⇒ 无可缓解，且这是绝大多数玩家的正常状态，不给 info/warn 噪音。
            LOGGER.debug("[Astral Dice] 未检测到 Iceberg，提示框颜色缓存缓解无需启用。");
        } catch (Throwable failure) {
            LOGGER.warn("[Astral Dice] Iceberg 提示框颜色缓存缓解未启用（接口不匹配？）: {}", failure.toString());
        }
    }
}
