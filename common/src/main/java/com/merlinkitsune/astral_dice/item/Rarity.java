package com.merlinkitsune.astral_dice.item;

import java.util.function.UnaryOperator;

import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/**
 * 本模组（含消费方 mod）的**稀有度等级**定义 —— 等级、常量名、序列化名与**颜色码的唯一权威**。
 *
 * <h2>这些等级是怎么"存在"的</h2>
 * <p>它们不是独立枚举，而是被**扩展进原版 {@code net.minecraft.world.item.Rarity}**：
 * <ul>
 *   <li><b>NeoForge（1.21.1 / 26.1.2）</b>：消费方 mod 在自己的 {@code neoforge.mods.toml} 里声明
 *       {@code enumExtensions="META-INF/enumextensions.json"}，该 json 的 5 条 entry 通过
 *       {@code AstralRarities} 的 {@link net.neoforged.fml.common.asm.enumextension.EnumProxy} 字段
 *       取得**序列化名**与**染色函数**；FML 的 {@code RuntimeEnumExtender} 在 {@code Rarity} 类加载时注入常量；</li>
 *   <li><b>Forge（1.20.1）</b>：{@code Rarity} 实现 {@code IExtensibleEnum}，由 {@code AstralRarities}
 *       的静态初始化调用 {@code Rarity.create(name, styleModifier)} 登记。</li>
 * </ul>
 *
 * <h2>颜色</h2>
 * <p>颜色在**扩展时**以 Style 变换函数的形式交给原版枚举常量（{@link #styleModifier()}），此后由原版
 * tooltip 链路（{@code ItemStack#getTooltipLines} → {@code Rarity#getStyleModifier()}）自动套用 ——
 * 因此本类即是**染色权威**：改色只需改这里的常量，无需任何 tooltip 侧代码。
 *
 * <p>**提示框边框**的策略（2026-09-25 用户二次裁决：放弃自绘顺时针彩虹，改回两色渐变）：
 * <ul>
 *   <li><b>稀有 / 史诗</b>：完全随原版 —— 消费方**不干预**边框（原版边框与稀有度无关的紫蓝渐变），
 *       物品名仍由本档 Style 上色 ⇒ 与原版 RARE / EPIC 物品观感一致；</li>
 *   <li><b>传奇 / 巅峰</b>：消费方把 {@link #frameColor(long)}（= {@link #rgb()}）写进边框起止色
 *       ⇒ 整圈单色；</li>
 *   <li><b>奇特</b>：消费方把 {@link #rainbowBorderStart(long)} / {@link #rainbowBorderEnd(long)}
 *       写进边框起/止色 ⇒ 原版「上横线 = 起始色、下横线 = 结束色、左右竖线 = 两者竖直渐变」的两色流动渐变。</li>
 * </ul>
 *
 * <p>⚠️ 原版自带的 {@code COMMON/UNCOMMON/RARE/EPIC} 在本模组内**不再用于分档**（仅 {@code COMMON} 保留作"普通"）。
 *
 * @see com.merlinkitsune.astral_dice.item.AstralRarities
 */
public enum Rarity {
    /** 稀有 —— **原版 RARE 的配色**（水蓝，{@code ChatFormatting.AQUA} = {@code #55FFFF}）。 */
    RARE("ASTRAL_DICE_RARE", "astral_dice:rare", 0x55FFFF),
    /** 史诗 —— **原版 EPIC 的配色**（粉紫，{@code ChatFormatting.LIGHT_PURPLE} = {@code #FF55FF}）。 */
    EPIC("ASTRAL_DICE_EPIC", "astral_dice:epic", 0xFF55FF),
    /** 传奇 —— 金。 */
    LEGENDARY("ASTRAL_DICE_LEGENDARY", "astral_dice:legendary", 0xFFC24B),
    /** 巅峰 —— 亮红。 */
    PINNACLE("ASTRAL_DICE_PINNACLE", "astral_dice:pinnacle", 0xFF4D4D),
    /**
     * 奇特 —— **彩虹（流动）边框 + 亮红文字**。
     *
     * <p>⚠️ 本档的文字色（{@link #rgb()}）= **亮红**，与巅峰 {@link #PINNACLE} 同色（2026-09-25 用户裁决：
     * 奇特文字改亮红、边框不动）；流动彩虹只在**提示框边框**上由消费方逐帧调用
     * {@link #rainbowBorderStart(long)} / {@link #rainbowBorderEnd(long)} 取得（见 {@link #isRainbow()}）。
     */
    BIZARRE("ASTRAL_DICE_BIZARRE", "astral_dice:bizarre", 0xFF4D4D);

    private final String constantName;
    private final String serializedName;
    private final int rgb;

    Rarity(String constantName, String serializedName, int rgb) {
        this.constantName = constantName;
        this.serializedName = serializedName;
        this.rgb = rgb;
    }

    /**
     * 扩展后原版 {@code Rarity} 的**枚举常量名**（= {@code Rarity#name()}；探针/日志/崩溃报告里看到的就是它）。
     * NeoForge 侧它同时是 {@code enumextensions.json} 的 {@code name} 字段值（FML 注入的字段名）。
     */
    public String constantName() {
        return this.constantName;
    }

    /**
     * **序列化名**（= 扩展后 {@code Rarity#getSerializedName()}）。
     *
     * <p>⚠️ NeoForge 对它有**硬性格式要求**（{@code RuntimeEnumExtender#validateNameParameter}）：必须形如
     * {@code <声明该 enumextensions.json 的 modId>:<名字>} —— **冒号分隔**；当前 modId 为 {@code astral_dice}
     * ⇒ 形如 {@code astral_dice:rare}（写 {@code astral_dice_rare} 会在 {@code Rarity} 类加载时抛
     * {@code IllegalArgumentException: Name parameter must be prefixed by mod ID}，游戏直接起不来）。
     *
     * <p>注意这与 {@code enumextensions.json} 里 {@code name} 字段（**注入进枚举的字段名**，要求以 modId 小写前缀开头、
     * 形如 {@code ASTRAL_DICE_RARE}）是**两条不同的规则**，别混。
     */
    public String serializedName() {
        return this.serializedName;
    }

    /** 颜色码（0xRRGGBB）。 */
    public int rgb() {
        return this.rgb;
    }

    /** 颜色码对应的 {@link TextColor}。 */
    public TextColor textColor() {
        return TextColor.fromRgb(this.rgb);
    }

    /** 染色：本项目**唯一**的颜色写入点。 */
    public Style apply(Style style) {
        return style.withColor(this.textColor());
    }

    /** 交给原版枚举常量的 Style 变换函数（扩展时传入；原版 tooltip 链路会调用它）。 */
    public UnaryOperator<Style> styleModifier() {
        return this::apply;
    }

    /**
     * 该档位的**提示框边框色**（ARGB，全不透明）—— 消费方把「起/止」两色都设成它即得**整圈单色**。
     * 2026-09-25 起**只有传奇 / 巅峰**这样用（值 = {@link #rgb()} ⇒ 与物品名同色）；稀有 / 史诗随原版
     * 不干预边框；奇特走 {@link #rainbowBorderStart(long)} / {@link #rainbowBorderEnd(long)} 的两色渐变，
     * **不经过本方法**（本方法对彩虹档的回退仅作防御，消费方不会走到）。
     *
     * <p>⚠️ 原版 tooltip 的边框颜色**与稀有度无关**（1.21.1/1.20.1 = `TooltipRenderUtil` 里写死的紫蓝渐变，
     * 26.1.2 = `tooltip/frame` 九宫格贴图），所以任何自定义边框都必须由消费方把颜色写进平台事件
     * （本仓消费方见 AGENTS 稀有度段）；装了第三方 tooltip 模组时还要对那家做数据对接。
     *
     * @param millis 当前时刻（毫秒）—— 只有彩虹档用它，其余档位忽略
     */
    public int frameColor(long millis) {
        return this.isRainbow() ? this.rainbowBorderStart(millis) : (0xFF000000 | this.rgb);
    }

    /** 彩虹一整个色环走完的周期（毫秒）—— 边框流动速度的**唯一权威**。 */
    public static final long RAINBOW_CYCLE_MILLIS = 3000L;

    /** 边框两色在色环上的间隔（1/3 圈 = 120°）。 */
    private static final float RAINBOW_SPREAD = 1.0F / 3.0F;

    /** 彩虹的饱和度 / 明度：取高饱和高亮，保证在深色 tooltip 背景上仍然醒目。 */
    private static final float RAINBOW_SATURATION = 0.85F;
    private static final float RAINBOW_BRIGHTNESS = 1.0F;

    /** 彩虹的**饱和度**（0..1）—— 供消费方自绘彩虹边框时与 {@link #hsvToRgb(float, float, float)} 配套使用。 */
    public static float rainbowSaturation() {
        return RAINBOW_SATURATION;
    }

    /** 彩虹的**明度**（0..1）—— 供消费方自绘彩虹边框时与 {@link #hsvToRgb(float, float, float)} 配套使用。 */
    public static float rainbowBrightness() {
        return RAINBOW_BRIGHTNESS;
    }

    /**
     * 该等级是否以**彩虹（流动）**呈现 —— 当前仅 {@link #BIZARRE}。
     *
     * <p>⚠️ 这里的「彩虹」**不是**单一颜色，故 {@link #styleModifier()} 只能给出 {@link #rgb()} 那一个
     * 基准色（用于物品名那一行、以及快捷栏切换提示等无法逐帧上色的位置）；真正流动的色环由
     * 消费方在**客户端**逐帧渲染时调用 {@link #rainbowBorderStart(long)} / {@link #rainbowBorderEnd(long)}
     * 取得（原版 tooltip 每帧重绘 ⇒ 事件每帧都发，无需 Mixin）。
     */
    public boolean isRainbow() {
        return this == BIZARRE;
    }

    /** HSV → 0xRRGGBB（不含 alpha；h 可为任意实数，按小数部分取模）。 */
    public static int hsvToRgb(float hue, float saturation, float value) {
        float h = (hue - (float) Math.floor(hue)) * 6.0F;
        float s = Math.max(0.0F, Math.min(1.0F, saturation));
        float v = Math.max(0.0F, Math.min(1.0F, value));
        int i = (int) h;
        float f = h - i;
        float p = v * (1.0F - s);
        float q = v * (1.0F - s * f);
        float t = v * (1.0F - s * (1.0F - f));
        float r;
        float g;
        float b;
        switch (i % 6) {
            case 0:
                r = v; g = t; b = p;
                break;
            case 1:
                r = q; g = v; b = p;
                break;
            case 2:
                r = p; g = v; b = t;
                break;
            case 3:
                r = p; g = q; b = v;
                break;
            case 4:
                r = t; g = p; b = v;
                break;
            default:
                r = v; g = p; b = q;
                break;
        }
        return (Math.round(r * 255.0F) << 16) | (Math.round(g * 255.0F) << 8) | Math.round(b * 255.0F);
    }

    /** 给定时刻在色环上的相位（0..1）—— 负数安全（取模后再归一）。 */
    public static float rainbowHue(long millis) {
        long m = millis % RAINBOW_CYCLE_MILLIS;
        if (m < 0L) {
            m += RAINBOW_CYCLE_MILLIS;
        }
        return (float) m / (float) RAINBOW_CYCLE_MILLIS;
    }

    /**
     * 彩虹边框的**起始色**（ARGB，全不透明）—— 原版 tooltip 里对应「上横线」与「左右竖线渐变的顶端」。
     */
    public int rainbowBorderStart(long millis) {
        return 0xFF000000 | hsvToRgb(rainbowHue(millis), RAINBOW_SATURATION, RAINBOW_BRIGHTNESS);
    }

    /**
     * 彩虹边框的**结束色**（ARGB）—— 与起始色在色环上相差 1/3 圈；原版 tooltip 的左右竖线就是
     * 「起始色 → 结束色」的竖直渐变，故这两个值一动，整圈边框的颜色就跟着流动。
     */
    public int rainbowBorderEnd(long millis) {
        float h = rainbowHue(millis) + RAINBOW_SPREAD;
        if (h >= 1.0F) {
            h -= 1.0F;
        }
        return 0xFF000000 | hsvToRgb(h, RAINBOW_SATURATION, RAINBOW_BRIGHTNESS);
    }
}
