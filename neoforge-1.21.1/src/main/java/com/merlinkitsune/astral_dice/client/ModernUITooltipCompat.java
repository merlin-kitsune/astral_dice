// NeoForge 21.1.x / MC 1.21.1
package com.merlinkitsune.astral_dice.client;

import java.lang.reflect.Array;
import java.lang.reflect.Field;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.config.ModCommonConfig;
import com.merlinkitsune.starenginelib.item.AstralRarities;
import com.merlinkitsune.starenginelib.item.Rarity;

import net.minecraft.Util;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 第三方提示框模组 <b>Modern UI</b>（{@code 现代化 UI}）接管提示框时的**边框染色兼容层**。
 *
 * <h2>症状（2026-10-04 用户实报）</h2>
 * <p>客户端装了 Modern UI 并启用「现代提示框」后，本模组「按稀有度给提示框上边框色」的观感**完全失效**。
 *
 * <h2>根因链（对 {@code ModernUI-NeoForge-1.21.1-3.12.0.2} 的实物 jar 反汇编，逐条可复现）</h2>
 * <ol>
 *   <li><b>它不用 Mixin 改 {@code GuiGraphics}</b> —— 它的两个 mixin 配置
 *       （{@code mixins.modernui-neoforge.json} / {@code mixins.modernui-textmc.json}）里
 *       **没有任何 tooltip / {@code GuiGraphics} 注入**；</li>
 *   <li>它改为**订阅平台事件**：{@code icyllis.modernui.mc.neoforge.UIManagerForge} 上
 *       <pre>
 *   {@code @SubscribeEvent(priority = EventPriority.HIGH)}  void onRenderTooltipH(Pre e) → drawExtTooltip(...)  // 自己画
 *   {@code @SubscribeEvent(priority = EventPriority.LOW)}   void onRenderTooltipL(Pre e) → e.setCanceled(true)   // 掐掉原版</pre>
 *       两个方法体都以 {@code if (!TooltipRenderer.sTooltip) return;} 开头；</li>
 *   <li>⇒ 现代提示框开启时，{@code GuiGraphics#renderTooltipInternal} 在**第一条语句**就被取消，
 *       {@code RenderTooltipEvent.Color} **永不派发** ⇒ 本模组的 {@code client/RarityTooltipFrame}
 *       （它把颜色写进该事件的 borderStart/End）**整体空转**，一行代码都不会被执行到；</li>
 *   <li>Modern UI 自己的边框色来自 {@code TooltipRenderer} 的四个静态槽：
 *       <pre>
 *   public static final    int[]   sStrokeColor;    // 玩家配置 colorStroke 的 4 色（逐元素可写，引用是 final）
 *   public static volatile boolean sAdaptiveColors; // 玩家配置 adaptiveColors
 *   public static volatile boolean sTooltip;        // 玩家配置 tooltip.enable</pre>
 *       绘制链 = {@code sStrokeColor} → {@code computeWorkingColor()} → {@code mWorkStrokeColor}
 *       → {@code updateBorderColor()} → {@code mActiveStrokeColor} → {@code chooseBorderColor()}。</li>
 * </ol>
 *
 * <h2>⚠️ 为什么只写 {@code sStrokeColor} 还不够（必须同时临时关掉 {@code sAdaptiveColors}）</h2>
 * <p>{@code computeWorkingColor(ItemStack)} 的 {@code sAdaptiveColors=true} 分支**不读**
 * {@code sStrokeColor} 的 RGB（只借它的 alpha：{@code mWorkStrokeColor[i] = sStrokeColor[i] & 0xFF000000 |
 * 物品名取色(i)}），颜色改从**物品名的逐字格式化色**推导，并做一步 HSV 压缩
 * （{@code s = min(s, 0.9)}、{@code v = clamp(v, 0.2, 0.85)}）：
 * <pre>
 *   某个物品名只解析出 1 种颜色时：n == 1 ⇒ 连调 3 次 adjustColor 填槽 1 / 2 / 3，连同槽 0 共 4 槽都有色；
 *   物品名有多色时：按 lerp / 相邻槽插值补齐（n > 4 且开启圆角时改走 mUseSpectrum）。</pre>
 * ⇒ 自适应模式**能**上色，但对本模组有两条不可接受的损失：
 * <ol>
 *   <li><b>不是本档位的精确色</b>：我们的档位色明度都是 1.0（例：传奇 {@code #FFC24B}），
 *       会被 {@code v = clamp(v, 0.2, 0.85)} **压暗**（实测约 (217,179,64)）⇒ 与物品名（精确档位色）
 *       以及原生路径 {@code RarityTooltipFrame#frameColor} 写的色**不一致**；</li>
 *   <li><b>彩虹档无法流动</b>：物品名只有**一个**基准色（{@code Rarity#styleModifier()} 只能给
 *       {@link Rarity#rgb()}），自适应路径拿不到色环 ⇒ 奇特档拿不到「整圈流动彩虹」。</li>
 * </ol>
 * <p>所以兼容层在本模组物品的 tooltip 期间**一并把 {@code sAdaptiveColors} 置 false**
 * —— 此时 {@code computeWorkingColor} 走的是 {@code System.arraycopy(sStrokeColor → mWorkStrokeColor, 4)}，
 * 我们写入的四色**原样生效**（与原生路径同色，彩虹档也回来了）。
 *
 * <h2>介入点与优先级（这是本类唯一「讲究」的地方）</h2>
 * <ul>
 *   <li><b>写</b>：{@code EventPriority.HIGHEST} —— 必须早于 Modern UI 的 {@code HIGH}
 *       （它在那一个处理器里就把 tooltip 画完）；NeoForge/Forge 的 {@code HIGHEST} 早于 {@code HIGH}；</li>
 *   <li><b>还</b>：{@code EventPriority.LOWEST} —— 晚于 Modern UI 的 {@code LOW}（取消）。
 *       ⚠️ **必须带 {@code receiveCanceled = true}**：{@code RenderTooltipEvent.Pre} 是可取消事件，
 *       而 NeoForge bus 8 / Forge eventbus 6 对「已取消」的事件会**跳过未声明该标志的处理器**
 *       （`SubscribeEventListener#invoke` / `ASMEventHandler#invoke` 都判 `receiveCanceled()`）
 *       —— 现代提示框开启时 Modern UI 恰好在 {@code LOW} 把它取消，少了这个标志我们的还原**当帧就不会执行**。
 *       加上之后语义才真正成立：{@code sStrokeColor} / {@code sAdaptiveColors} 只在**这一次事件派发**期间是脏的，
 *       而 {@code computeWorkingColor} 只在 {@code drawTooltip} 内同步执行一次（且仅在「悬停物品变了」时）。</li>
 * </ul>
 * <p>另在 {@code onTooltipPre} 开头先做一次 {@link #restore()}（幂等）—— 双保险：即便某一帧因他处异常
 * 没能走到 {@code LOWEST}，下一帧也会自动自愈，绝不把玩家配置长期改坏。
 *
 * <h2>颜色口径（与 {@code RarityTooltipFrame} 保持一致）</h2>
 * <ul>
 *   <li><b>传奇 / 巅峰 / 史诗</b>：四槽同色 = {@link Rarity#frameColor(long)} ⇒ **整圈单色**；</li>
 *   <li><b>奇特</b>：四槽各差 1/4 圈（{@link Rarity#hsvToRgb} + {@link Rarity#rainbowHue}）⇒
 *       Modern UI 会在相邻两槽之间逐帧插值（受其 {@code borderCycleTime} 驱动）⇒ **流动彩虹**；
 *       ⚠️ 这正是必须关掉 {@code adaptiveColors} 的另一理由：自适应模式下「一个名字色」产不出彩虹；</li>
 *   <li><b>稀有</b>：**不干预**（与 {@code RarityTooltipFrame} / 库 {@code Rarity} 的既有裁决一致：
 *       稀有档随原版、本模组不写色）。⚠️ 这是 Modern UI 下唯一「仍不着色」的档位，
 *       若要一并接管，把 {@link #isOwningTier} 里的 {@code Rarity.RARE} 判断去掉即可。</li>
 * </ul>
 * <p>写入时**保留玩家配置的 alpha**（{@code 槽的新值 = 原槽 & 0xFF000000 | 本档 RGB}），
 * 与 Modern UI 自适应路径自己的做法一致 —— 玩家若把边框调成半透明，我们不动他的透明度。
 *
 * <h2>边界与安全</h2>
 * <ul>
 *   <li>**不硬依赖 Modern UI**：目标类用字符串名探测，缺席走 debug（绝大多数玩家没装）；</li>
 *   <li>**只碰三个公开静态成员**：{@code sStrokeColor}（逐元素）、{@code sAdaptiveColors}、{@code sTooltip}（只读）；
 *       私有字段（{@code mWorkStrokeColor} 等）**一概不碰**；</li>
 *   <li>**结构校验后再启用**：{@code sStrokeColor} 必须是长度 ≥4 的 {@code int[]}、
 *       {@code sAdaptiveColors} 必须是 {@code boolean}，否则整层停用（防版本漂移后误写）；</li>
 *   <li>**fail-safe**：任何一次探测/写入失败都**永久停用**本层并打一条 warn，绝不重试、绝不抛出
 *       （tooltip 渲染在渲染线程上，抛异常 = 崩客户端）；</li>
 *   <li>**两个逃生口**：配置项 {@code modernui_tooltip_frame_compat=false}（游戏内/整合包作者可改），
 *       以及系统属性 {@code -Dastral_dice.modernUITooltipCompat=false}（比配置更早、更硬）；</li>
 *   <li>{@code sTooltip == false}（玩家没开现代提示框）⇒ 本层直接返回，走原版路径，
 *       {@code RarityTooltipFrame} 照旧生效。</li>
 * </ul>
 *
 * <p>⚠️ 本类只在客户端加载（{@code value = Dist.CLIENT}）且位于 {@code client/} 包内；双端加载类不得引用它。
 * 完整取证链与复现命令见 {@code scripts/test/TESTING-SPEC.md} 与技能 {@code mc-thirdparty-tooltip-frame}。
 */
@EventBusSubscriber(modid = AstralDiceMod.MODID, value = Dist.CLIENT)
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

    /** 写：必须早于 Modern UI 的 {@code HIGH} 处理器。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onTooltipPre(RenderTooltipEvent.Pre event) {
        restore(); // 自愈：上一轮若未还原，先还原（幂等）
        if (!isUsable() || !enabled() || !modernTooltipOn()) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) {
            return;
        }
        Rarity tier = AstralRarities.tierOf(stack.getRarity());
        if (!isOwningTier(tier)) {
            return; // 非本模组档位、或按既有裁决不干预的稀有档 ⇒ 一行都不写，绝不越界
        }
        pin(strokeColors(tier, Util.getMillis()));
    }

    /**
     * 还：晚于 Modern UI 的 {@code LOW}（它在那一步取消原版渲染）。此时它已经画完了。
     *
     * <p>⚠️ {@code receiveCanceled = true} **不可省**：Modern UI 正是在 {@code LOW} 取消本事件，
     * 而事件总线对已取消的事件会跳过未声明该标志的处理器（⇒ 少了它就当帧不会还原）。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onTooltipPost(RenderTooltipEvent.Pre event) {
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
        // 且相邻两槽本身也相邻于色环 ⇒ 得到连续流动的整圈彩虹（原生路径是两色 1/3 圈渐变）。
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
