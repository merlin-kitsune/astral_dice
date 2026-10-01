package com.merlinkitsune.astral_dice.platform.client.gui.overlay;

/**
 * Forge 的 HUD 包装对象占位(对齐 Forge 的 {@code client.gui.overlay.ForgeGui})。
 *
 * <p>本模组的覆盖层**只用 {@code GuiGraphics} 与 {@code Minecraft}**,
 * 唯一例外是**战斗数值 HUD**({@code client/CombatPowerHud})——它需要 Forge 的
 * 高度累加量来定位(见下),故这里补齐这两个字段。
 *
 * <h2>leftHeight / rightHeight 的语义(与 Forge 对齐)</h2>
 * Forge 的 {@code ForgeGui#render} 在开头把两列重置为 **39**,随后各元素**先读后加**:
 * <pre>
 *   renderHealth : top = height - leftHeight;  leftHeight += 10     // 血量占 1 行 → 39→49
 *   renderArmor  : top = height - leftHeight;  leftHeight += 10     // 护甲 → 49→59
 *   renderFood   : top = height - rightHeight; rightHeight += 10    // 饱食 → 39→49
 *   renderAir    : top = height - rightHeight; rightHeight += 10    // 空气 → 49→59
 * </pre>
 *
 * <h2>Fabric 侧的差异(为什么起点不是 39)</h2>
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
 * </ul>
 *
 * <p>⚠️ 若将来有覆盖层要用 gui 的辅助方法,必须在此补齐并在 {@code FabricClientBridges} 接线时
 * 提供真实数据 —— 否则会 NPE。
 */
public final class ForgeGui {
    public static final ForgeGui INSTANCE = new ForgeGui();

    /** 左列已占用高度(防御条读它后**无条件**自增 10)。 */
    public int leftHeight;

    /** 右列已占用高度(攻击条读它后**无条件**自增 10)。 */
    public int rightHeight;

    /**
     * 左列每帧起点:原版已画「血量行」(height-39) ⇒ 防御条(顶替护甲条)取 height-49。
     * @see ForgeGui 类头
     */
    public static final int RESET_LEFT = 49;

    /**
     * 右列每帧起点:原版已画「饱食度行」(height-39)与「空气行」(height-49)
     * ⇒ 攻击条取 height-59 以避开空气条。**与 Forge 有意差一行**,见类头。
     */
    public static final int RESET_RIGHT = 59;

    private ForgeGui() {
    }
}
