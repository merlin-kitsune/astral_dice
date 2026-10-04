// Fabric Loader 0.19.x / Fabric API 0.92.12 / MC 1.20.1
package com.merlinkitsune.astral_dice.client;

import java.lang.reflect.Array;
import java.lang.reflect.Field;

import com.merlinkitsune.astral_dice.config.ModCommonConfig;
import com.merlinkitsune.starenginelib.item.AstralRarities;
import com.merlinkitsune.starenginelib.item.Rarity;

import net.minecraft.Util;
import net.minecraft.world.item.ItemStack;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 第三方提示框模组 <b>Modern UI</b>（{@code 现代化 UI}）接管提示框时的**边框染色兼容层**（Fabric 线）。
 *
 * <h2>症状（2026-10-04 用户实报）</h2>
 * <p>客户端装了 Modern UI 并启用「现代提示框」后，本模组「按稀有度给提示框上边框色」的观感**完全失效**。
 *
 * <h2>根因链（对实物 jar {@code ModernUI-Fabric-1.20.1-3.12.0.1-universal.jar}
 * （Modrinth {@code modern-ui}；sha1 {@code 90b40fcb82e885fcb8f73992dc51473682db29d8}）反汇编，逐条可复现）</h2>
 * <p>⚠️ <b>本线的机制与两条 P0 线不同</b> —— P0（NeoForge / Forge）上 Modern UI 走**平台事件**
 * （{@code UIManagerForge} 的 {@code @SubscribeEvent} HIGH/LOW 自绘 + 取消）；而 Fabric 无 Forge 事件总线，
 * 它改为**注入原版类**：
 * <ol>
 *   <li>{@code fabric.mod.json} 的 {@code mixins} 声明 {@code mixins.modernui-fabric.json}，其 {@code client}
 *       列表含 <b>{@code MixinGuiGraphics}</b>（目标 {@code net.minecraft.client.gui.GuiGraphics}）；</li>
 *   <li>{@code MixinGuiGraphics} 的四处注入（javap 实证）：
 *       <pre>
 *   {@code @Inject(method="renderTooltip(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;II)V", at=HEAD)}  preRenderTooltip    // 记住当前物品栈
 *   {@code @Inject(… 同一方法 …, at=TAIL)}                                                                              postRenderTooltip  // 清掉
 *   {@code @Inject(method="renderTooltip(Lnet/minecraft/client/gui/Font;Ljava/util/List;Ljava/util/Optional;II)V", at=INVOKE)} onRenderTooltip → ci.cancel()
 *   {@code @Inject(method="renderTooltipInternal", at=HEAD)}                                                              onRenderTooltip → ci.cancel()</pre>
 *       后两处的方法体都以 {@code getstatic icyllis/modernui/mc/TooltipRenderer.sTooltip:Z} 开头，命中即
 *       {@code CallbackInfo.cancel()}（并自行绘制整个提示框）；</li>
 *   <li>⇒ 现代提示框开启时，原版 {@code TooltipRenderUtil#renderTooltipBackground} **根本不会被调用**
 *       —— 本模组写进 {@code TooltipFrameColors}（消费方是 {@code TooltipRenderUtilColorMixin}）
 *       的边框色**整体空转**，{@code client/RarityTooltipFrame} 一行都不会被执行到；</li>
 *   <li>Modern UI 自己的边框色来自 {@code TooltipRenderer} 的静态槽 —— 实测**与 P0 同名同型**（javap）：
 *       <pre>
 *   public static final    int[]   sStrokeColor;    // 玩家配置 colorStroke 的 4 色（逐元素可写，引用是 final）
 *   public static volatile boolean sAdaptiveColors; // 玩家配置 adaptiveColors
 *   public static volatile boolean sTooltip;        // 玩家配置 tooltip.enable</pre>
 *       绘制链同样为 {@code sStrokeColor} → {@code computeWorkingColor(ItemStack)} → {@code updateBorderColor()}
 *       → {@code chooseBorderColor()}。⚠️ 同名同型只是**实证结果**，本层仍按「结构校验后再启用」处理。</li>
 * </ol>
 *
 * <h2>⚠️ 为什么只写 {@code sStrokeColor} 还不够（必须同时临时关掉 {@code sAdaptiveColors}）</h2>
 * <p>与 P0 完全同理：{@code sAdaptiveColors=true} 时 {@code computeWorkingColor} **不读** {@code sStrokeColor}
 * 的 RGB（只借它的 alpha），颜色改从**物品名的逐字格式化色**推导并做 HSV 压缩
 * （{@code v = clamp(v, 0.2, 0.85)}）⇒ 两条不可接受的损失：① 不是本档位的精确色（档位色明度 1.0 会被压暗，
 * 与物品名及原生路径不一致）；② 彩虹档拿不到色环 ⇒ 无法流动。
 *
 * <h2>介入窗口（Fabric 无事件优先级，改用 Mixin 注入 —— 语义与 P0 等价）</h2>
 * <ul>
 *   <li><b>写</b>：{@code GuiGraphics#renderTooltip(Font, ItemStack, int, int)} 的 {@code HEAD}
 *       （由 {@code mixin/bridge/ClientTooltipBridgeMixin} 调用 {@link #beginRender(ItemStack)}）——
 *       该方法是原版**唯一带 ItemStack 的入口**，Modern UI 自己也只在这个方法范围内知道物品栈
 *       （它的 {@code preRenderTooltip} 同样挂在这个 HEAD），而它的**实际绘制发生在本方法内层**（内层
 *       {@code renderTooltip} / {@code renderTooltipInternal} 被取消前已画完）⇒ 我们的写入必然早于绘制；</li>
 *   <li><b>还</b>：同一方法的 {@code RETURN}（{@link #endRender()}）。⚠️ Modern UI 取消的是**内层**方法，
 *       外层必然正常返回 ⇒ 还原必然执行。这是本线对 P0「{@code LOWEST} + {@code receiveCanceled = true}」的等价物
 *       —— Fabric 侧既没有事件优先级、也没有「已取消仍派发」的标志，所以**不能**照抄 P0 的事件写法。</li>
 * </ul>
 * <p>另在 {@link #beginRender(ItemStack)} 开头先做一次 {@link #restore()}（幂等）—— 双保险：即便某一帧因他处异常
 * 没能走到 {@code RETURN}，下一帧也会自动自愈，绝不把玩家配置长期改坏。
 *
 * <h2>颜色口径（与 {@code RarityTooltipFrame} 保持一致）</h2>
 * <ul>
 *   <li><b>史诗 / 传奇 / 巅峰</b>：四槽同色 = {@link Rarity#frameColor(long)} ⇒ **整圈单色**；</li>
 *   <li><b>奇特</b>：四槽各差 1/4 圈（{@link Rarity#hsvToRgb} + {@link Rarity#rainbowHue}）⇒
 *       Modern UI 会在相邻两槽之间逐帧插值 ⇒ **流动彩虹**；⚠️ 本线 {@code RarityTooltipFrame} 走的是原版两色渐变
 *       （{@code rainbowBorderStart/End}），而 Modern UI 的边框是**四角槽**，故此处按四槽铺满整圈（与 P0 同款）。</li>
 *   <li><b>稀有</b>：**不干预**（与 {@code RarityTooltipFrame} / 库 {@code Rarity} 的既有裁决一致：
 *       稀有档随原版、本模组不写色）。⚠️ 这是 Modern UI 下唯一「仍不着色」的档位，
 *       若要一并接管，把 {@link #isOwningTier} 里的 {@code Rarity.RARE} 判断去掉即可。</li>
 * </ul>
 * <p>写入时**保留玩家配置的 alpha**（{@code 槽的新值 = 原槽 & 0xFF000000 | 本档 RGB}）。
 *
 * <h2>边界与安全</h2>
 * <ul>
 *   <li>**不硬依赖 Modern UI**：目标类用字符串名探测，缺席走 debug（绝大多数玩家没装）；</li>
 *   <li>**只碰三个公开静态成员**：{@code sStrokeColor}（逐元素）、{@code sAdaptiveColors}、{@code sTooltip}（只读）；</li>
 *   <li>**结构校验后再启用**：{@code sStrokeColor} 必须是长度 ≥4 的 {@code int[]}、
 *       {@code sAdaptiveColors} 必须是 {@code boolean}，否则整层停用；</li>
 *   <li>**fail-safe**：任何一次探测/写入失败都**永久停用**本层并打一条 warn，绝不重试、绝不抛出
 *       （tooltip 渲染在渲染线程上，抛异常 = 崩客户端）；</li>
 *   <li>**两个逃生口**：配置项 {@code modernui_tooltip_frame_compat=false}，以及系统属性
 *       {@code -Dastral_dice.modernUITooltipCompat=false}（比配置更早、更硬）；</li>
 *   <li>{@code sTooltip == false}（玩家没开现代提示框）⇒ 本层直接返回，走原版路径，
 *       {@code RarityTooltipFrame} 照旧生效。</li>
 * </ul>
 *
 * <p>⚠️ 本类只在客户端加载（仅由 {@code mixin/bridge/ClientTooltipBridgeMixin} 这一**客户端 mixin** 调用），
 * 且位于 {@code client/} 包内；双端加载类不得引用它。类本身不引用任何客户端专属类型。
 * 完整取证链与复现命令见 {@code scripts/test/TESTING-SPEC.md} 与技能 {@code mc-thirdparty-tooltip-frame}。
 */
public final class ModernUITooltipCompat {

    /** 本层的开关系统属性；写 {@code false} 即整体停用（默认启用）。 */
    public static final String ENABLED_PROPERTY = "astral_dice.modernUITooltipCompat";

    /** 目标类（第三方，**不做编译期依赖**）。 */
    private static final String TOOLTIP_RENDERER = "icyllis.modernui.mc.TooltipRenderer";

    /** Modern UI 的边框由**四个角槽**组成，逐帧在相邻两槽之间插值。 */
    private static final int STROKE_SLOTS = 4;

    private static final Logger LOGGER = LoggerFactory.getLogger(ModernUITooltipCompat.class);

    /** 探测只做一次（含「类缺席」「结构不符」「已停用」三种结论）。 */
    private static boolean probed;
    /** 非 null = 本层可用；写入失败时置回 null 即等于永久停用。 */
    private static Field strokeColorField;
    /** Modern UI 的「自适应取色」开关（缺席或类型不符时为 null ⇒ 不改写它）。 */
    private static Field adaptiveColorsField;
    /** Modern UI 的「现代提示框」总开关（缺席时为 null ⇒ 视为开启）。 */
    private static Field modernTooltipField;

    /** 写入前备份的四个描边槽（原值，含玩家配置的 alpha）。 */
    private static final int[] SAVED_STROKE = new int[STROKE_SLOTS];
    /** 写入前 {@code sAdaptiveColors} 的取值（仅当它为 true 时我们才改，故还原时写回 true 即可）。 */
    private static boolean savedAdaptive;
    /** 本帧是否处于「已写入、待还原」状态。 */
    private static boolean pinned;
    /** 「自适应取色还原失败」的告警只打一条（重试本身不受影响）。 */
    private static boolean adaptiveRestoreWarned;

    private ModernUITooltipCompat() {
    }

    /**
     * 写：由 {@code ClientTooltipBridgeMixin} 在
     * {@code GuiGraphics#renderTooltip(Font, ItemStack, int, int)} 的 {@code HEAD} 调用
     * —— 必须早于 Modern UI 的实际绘制（它在同一方法的内层绘制）。
     */
    public static void beginRender(ItemStack stack) {
        restore(); // 自愈：上一轮若未还原，先还原（幂等）
        if (stack == null || stack.isEmpty()) {
            return;
        }
        if (!isUsable() || !enabled() || !modernTooltipOn()) {
            return;
        }
        Rarity tier = AstralRarities.tierOf(stack.getRarity());
        if (!isOwningTier(tier)) {
            return; // 非本模组档位、或按既有裁决不干预的稀有档 ⇒ 一行都不写，绝不越界
        }
        pin(strokeColors(tier, Util.getMillis()));
    }

    /**
     * 还：由 {@code ClientTooltipBridgeMixin} 在同一方法的 {@code RETURN} 调用。
     * 此时 Modern UI 已经画完（它取消的是内层方法，外层必然返回）。
     */
    public static void endRender() {
        restore();
    }

    /**
     * 本模组要接管的档位 —— 与 {@code client/RarityTooltipFrame} 同一口径：
     * {@code null}（原版/其它模组物品）与 {@link Rarity#RARE} 都不接管。
     */
    private static boolean isOwningTier(Rarity tier) {
        return tier != null && tier != Rarity.RARE;
    }

    /** 本档位对应的四个描边槽颜色（不带 alpha；alpha 由 {@link #pin} 从玩家配置继承）。 */
    private static int[] strokeColors(Rarity tier, long millis) {
        int[] out = new int[STROKE_SLOTS];
        if (!tier.isRainbow()) {
            int color = tier.frameColor(millis) & 0x00FFFFFF;
            for (int i = 0; i < STROKE_SLOTS; i++) {
                out[i] = color;
            }
            return out;
        }
        // 彩虹：四槽首尾相接铺满整圈（各差 1/4 圈）。Modern UI 会在相邻两槽之间插值，
        // 且相邻两槽本身也相邻于色环 ⇒ 得到连续流动的整圈彩虹。
        float base = Rarity.rainbowHue(millis);
        float saturation = Rarity.rainbowSaturation();
        float brightness = Rarity.rainbowBrightness();
        for (int i = 0; i < STROKE_SLOTS; i++) {
            out[i] = Rarity.hsvToRgb(base + (float) i / (float) STROKE_SLOTS, saturation, brightness);
        }
        return out;
    }

    /** 把本档四色写进 Modern UI 的描边槽（保留玩家配置的 alpha），并按需临时关掉自适应取色。 */
    private static void pin(int[] colors) {
        try {
            int[] slots = (int[]) strokeColorField.get(null);
            if (slots == null || slots.length < STROKE_SLOTS) {
                return;
            }
            for (int i = 0; i < STROKE_SLOTS; i++) {
                SAVED_STROKE[i] = slots[i];
                slots[i] = (slots[i] & 0xFF000000) | (colors[i] & 0x00FFFFFF);
            }
            savedAdaptive = adaptiveColorsField != null && adaptiveColorsField.getBoolean(null);
            if (savedAdaptive) {
                // 自适应模式下 computeWorkingColor 会用自己的名字取色覆盖我们写的 RGB，必须临时让位。
                adaptiveColorsField.setBoolean(null, false);
            }
            pinned = true;
        } catch (Throwable failure) {
            disable(failure);
        }
    }

    /** 还原玩家配置（幂等；未写入过时为空操作）。 */
    private static void restore() {
        // 先还「自适应」——它影响面最大，且**不依赖 `pinned`**：万一某次写入只成功了一半，
        // 这里会在下一帧继续重试直到成功，绝不把玩家的开关永久留在 false。
        if (savedAdaptive && adaptiveColorsField != null) {
            try {
                adaptiveColorsField.setBoolean(null, true);
                savedAdaptive = false;
                adaptiveRestoreWarned = false;
            } catch (Throwable failure) {
                if (!adaptiveRestoreWarned) {
                    adaptiveRestoreWarned = true;
                    LOGGER.warn("[Astral Dice] Modern UI 自适应取色开关还原失败（将持续重试）: {}", failure.toString());
                }
            }
        }
        if (!pinned) {
            return;
        }
        pinned = false;
        if (strokeColorField != null) {
            try {
                int[] slots = (int[]) strokeColorField.get(null);
                if (slots != null && slots.length >= STROKE_SLOTS) {
                    System.arraycopy(SAVED_STROKE, 0, slots, 0, STROKE_SLOTS);
                }
            } catch (Throwable failure) {
                LOGGER.warn("[Astral Dice] Modern UI 提示框描边色还原失败: {}", failure.toString());
            }
        }
    }

    /** 本层是否已就绪（探测通过）。 */
    private static boolean isUsable() {
        if (!probed) {
            probe();
        }
        return strokeColorField != null;
    }

    /** 配置项（游戏内开关）+ 系统属性（硬逃生口）双重放行。配置尚未加载时按默认 true 处理。 */
    private static boolean enabled() {
        if ("false".equalsIgnoreCase(System.getProperty(ENABLED_PROPERTY, "true"))) {
            return false;
        }
        try {
            return ModCommonConfig.MODERNUI_TOOLTIP_FRAME_COMPAT.get();
        } catch (Throwable notLoadedYet) {
            return true;
        }
    }

    /** Modern UI 是否正在接管提示框绘制；字段缺席时按「是」处理（此时我们的写入无害）。 */
    private static boolean modernTooltipOn() {
        Field field = modernTooltipField;
        if (field == null) {
            return true;
        }
        try {
            return field.getBoolean(null);
        } catch (Throwable failure) {
            return true;
        }
    }

    private static void probe() {
        probed = true;
        if ("false".equalsIgnoreCase(System.getProperty(ENABLED_PROPERTY, "true"))) {
            LOGGER.info("[Astral Dice] Modern UI 提示框边框兼容已被 -D{}=false 关闭。", ENABLED_PROPERTY);
            return;
        }
        try {
            Class<?> renderer = Class.forName(TOOLTIP_RENDERER);
            Field stroke = renderer.getField("sStrokeColor");
            // 结构校验：版本漂移后字段语义可能变，不符即整层停用（宁可不生效，也不乱写别人的状态）。
            if (!stroke.getType().isArray() || stroke.getType().getComponentType() != int.class) {
                LOGGER.warn("[Astral Dice] Modern UI 的 sStrokeColor 不是 int[]，兼容层未启用。");
                return;
            }
            Object value = stroke.get(null);
            if (value == null || Array.getLength(value) < STROKE_SLOTS) {
                LOGGER.warn("[Astral Dice] Modern UI 的 sStrokeColor 槽位不足 {} 个，兼容层未启用。", STROKE_SLOTS);
                return;
            }
            strokeColorField = stroke;
            adaptiveColorsField = booleanFieldOrNull(renderer, "sAdaptiveColors");
            modernTooltipField = booleanFieldOrNull(renderer, "sTooltip");
            LOGGER.info("[Astral Dice] Modern UI 提示框边框兼容已启用（本模组档位写入 sStrokeColor"
                    + "，自适应取色仅在渲染本模组物品时临时让位并立即还原）。");
        } catch (ClassNotFoundException absent) {
            // 未安装 Modern UI ⇒ 无可兼容，且这是绝大多数玩家的正常状态，不给 info/warn 噪音。
            LOGGER.debug("[Astral Dice] 未检测到 Modern UI，提示框边框兼容无需启用。");
        } catch (Throwable failure) {
            LOGGER.warn("[Astral Dice] Modern UI 提示框边框兼容未启用（接口不匹配？）: {}", failure.toString());
        }
    }

    private static Field booleanFieldOrNull(Class<?> owner, String name) {
        try {
            Field field = owner.getField(name);
            return field.getType() == boolean.class ? field : null;
        } catch (NoSuchFieldException absent) {
            return null;
        }
    }

    /** 永久停用本层（绝不重试、绝不抛出）并打一条 warn。 */
    private static void disable(Throwable failure) {
        strokeColorField = null;
        adaptiveColorsField = null;
        pinned = false;
        LOGGER.warn("[Astral Dice] Modern UI 提示框边框兼容写入失败、已停用该兼容层: {}", failure.toString());
    }
}
