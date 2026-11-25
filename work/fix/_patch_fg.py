# -*- coding: utf-8 -*-
import io, sys

FG  = r"D:/MCProject/astral_dice_multiloader/fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/platform/client/gui/overlay/ForgeGui.java"
FCB = r"D:/MCProject/astral_dice_multiloader/fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/platform/client/FabricClientBridges.java"

def patch(p, pairs):
    with io.open(p, "r", encoding="utf-8", newline="") as f:
        raw = f.read()
    t = raw.replace("\r\n", "\n")
    for i, (old, new) in enumerate(pairs):
        n = t.count(old)
        if n != 1:
            print("!! ANCHOR %d count=%d in %s" % (i, n, p)); sys.exit(2)
        t = t.replace(old, new)
    with io.open(p, "w", encoding="utf-8", newline="") as f:
        f.write(t.replace("\n", "\r\n"))
    print("PATCHED %s (was_crlf=%s, anchors=%d)" % (p, "\r\n" in raw, len(pairs)))

fg_pairs = [
(""" * <h2>Fabric 侧的差异(为什么起点不是 39)</h2>
 * 1.20.1 原版 {@code Gui} **没有** {@code leftHeight}/{@code rightHeight} —— 它的布局是硬编码的
 * (血量/饱食在 {@code height-39},护甲/空气在 {@code height-49}),且**在 Fabric 上仍由原版自己绘制**,
 * 本模组无法介入其累加。
 * <p>故 {@code FabricClientBridges} 在**每帧 HUD 回调前**把两列复位为
 * {@link #RESET_LEFT} / {@link #RESET_RIGHT},以反映「原版已经画掉的那几行」:
 * <ul>
 *   <li>{@link #RESET_LEFT} = <b>49</b> ⇒ 防御条落在 {@code height-49},正好顶替**原版护甲条**那一行
 *       (原版护甲图标已由 {@code mixin.client.ArmorBarCancelMixin} 取消绘制);</li>
 *   <li>{@link #RESET_RIGHT} = <b>59</b> ⇒ 攻击条落在 {@code height-59}。
 *       <p>⚠️ **与 Forge 有意差一行**:Forge 上攻击条接在 {@code FOOD_LEVEL} 之后,
 *       Forge 的 overlay 派发会把空气条一并推走(它也在 {@code rightHeight} 序列里);
 *       而 Fabric 的原版空气条**不参与**本模组的累加、固定画在 {@code height-49},
 *       若攻击条也用 49 就会与水下时的空气条同排。取 59 可避开,代价是无空气时比 Forge 高一行。</li>
 * </ul>""",
 """ * <h2>Fabric 侧的差异(为什么起点不是 39)</h2>
 * 1.20.1 原版 {@code Gui} **没有** {@code leftHeight}/{@code rightHeight} —— 它的布局是硬编码的
 * (血量/饱食在 {@code height-39},护甲在 {@code height-49},**气泡行只在原版决定画时才占
 * {@code height-49}**),且**在 Fabric 上仍由原版自己绘制**,本模组无法介入其累加。
 * <p>故 {@code FabricClientBridges} 在**每帧 HUD 回调前**把两列复位为
 * {@link #RESET_LEFT} / {@link #RESET_RIGHT},以反映「原版已经画掉的那几行」:
 * <ul>
 *   <li>{@link #RESET_LEFT} = <b>49</b> ⇒ 防御条落在 {@code height-49},正好顶替**原版护甲条**那一行
 *       (原版护甲图标已由 {@code mixin.client.ArmorBarCancelMixin} 取消绘制);</li>
 *   <li>{@link #RESET_RIGHT} = <b>59</b> ⇒ 攻击条落在 {@code height-59}。这是**原版会画气泡行**时的
 *       取值:气泡占着 {@code height-49},攻击条必须让到它上面一行,否则会盖住气泡。
 *       <p>Forge 侧等价现象:Forge 的 overlay 派发会把空气条一并推走(它也在 {@code rightHeight}
 *       序列里);而 Fabric 的原版气泡行**不参与**本模组的累加、固定画在 {@code height-49},
 *       只能由本模组让位。</li>
 *   <li>{@link #RESET_RIGHT_NO_AIR} = <b>49</b> ⇒ 攻击条落在 {@code height-49},紧贴饱食度上方一行 ——
 *       **原版不画气泡行**时的取值(那一行空着,没有东西可让),与 forge-1.20.1 / neoforge 两线一致。
 *       <p>⚠️ 选 49 还是 59 **不再无条件取 59**:由 {@code client/CombatPowerHud#airRowShown}
 *       按原版 {@code Gui#renderPlayerHealth} 的「是否画气泡行」条件**逐字等价**判定(取证见该
 *       方法 javadoc)。旧实况恒取 59 ⇒ 陆地上永远空掉一整行,攻击条比另三线高一行。</li>
 * </ul>"""),

("""    /** 右列已占用高度(攻击条读它后**无条件**自增 10)。 */
    public int rightHeight;""",
 """    /** 右列已占用高度(攻击条每帧把「本帧真实基准 + 10」写回,供后续 overlay 接着往上排)。 */
    public int rightHeight;"""),

("""    /**
     * 右列每帧起点:原版已画「饱食度行」(height-39)与「空气行」(height-49)
     * ⇒ 攻击条取 height-59 以避开空气条。**与 Forge 有意差一行**,见类头。
     */
    public static final int RESET_RIGHT = 59;""",
 """    /**
     * 右列每帧起点(**原版画气泡行时**):原版已画「饱食度行」(height-39)与「气泡行」(height-49)
     * ⇒ 攻击条取 height-59 以避开气泡。见类头。
     */
    public static final int RESET_RIGHT = 59;

    /**
     * 右列每帧起点(**原版不画气泡行时**):原版只画了「饱食度行」(height-39)
     * ⇒ 攻击条取 height-49,紧贴饱食度上方一行,与 forge-1.20.1 / neoforge 两线一致。见类头。
     */
    public static final int RESET_RIGHT_NO_AIR = 49;"""),
]
patch(FG, fg_pairs)

fcb_pairs = [
("""            // 每帧复位两列高度累加量(对齐 Forge 的 ForgeGui#render 开头把二者置 39 的做法):
            // 原版已画掉「血量行」与「饱食行/空气行」,故起点不是 39 —— 见 ForgeGui 类头。
            // ⚠️ 本模组的战斗数值 HUD(client/CombatPowerHud)依赖它定位,且会**先读后增**;
            //    复位必须发生在其 render 之前。""",
 """            // 每帧复位两列高度累加量(对齐 Forge 的 ForgeGui#render 开头把二者置 39 的做法):
            // 左列 = 原版已画「血量行」⇒ 49;右列 = 「饱食行 + 气泡行」都算上的上限 ⇒ 59。
            // ⚠️ 右列的真实基准由战斗数值 HUD(client/CombatPowerHud#airRowShown)按**本帧原版是否
            //    画气泡行**再定(49 或 59),它会把 base + 行高写回 rightHeight;故这里的复位必须
            //    发生在它的 render 之前。详见 ForgeGui 类头。"""),
]
patch(FCB, fcb_pairs)
