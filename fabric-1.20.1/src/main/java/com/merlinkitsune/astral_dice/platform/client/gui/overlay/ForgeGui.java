package com.merlinkitsune.astral_dice.platform.client.gui.overlay;

import net.minecraft.util.Mth;

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
 * <h2>Fabric 侧的差异:左列种子必须每帧复算,不是常量</h2>
 * 1.20.1 原版 {@code Gui} **没有** {@code leftHeight}/{@code rightHeight} —— 它的布局是硬编码的
 * (血量/饱食在 {@code height-39},护甲行在 {@code height-49}…{@code height-67},**气泡行只在原版
 * 决定画时才占 {@code height-49}**),且**在 Fabric 上仍由原版自己绘制**,本模组无法介入其累加。
 * <p>故 {@code FabricClientBridges} 在**每帧 HUD 回调前**自己写两列的复位值,以反映
 * 「原版本帧已经画掉的那几行」:
 * <ul>
 *   <li><b>左列 = 按原版心排数复算的种子(非常量)</b>,见 {@link #leftSeed}:
 *       原版护甲行 {@code k2 = (height-39) - (心排数-1)*行距 - 10}({@code Gui.java:872})
 *       ⇒ 距底偏移 = {@code 39 + (心排数-1)*行距 + 10},防御条补的正是这一行
 *       (原版护甲图标已由 {@code mixin.client.ArmorBarCancelMixin} 取消绘制)。
 *       <p>⚠️ 旧实现把左列写死为 {@code 49}(= 单排心时的取值),**两排心(吸收心)时会压住第二排心**:
 *       原版吸收心画在 {@code k1 - 1*行距} = {@code height-49},与写死的防御条同 y。
 *       吸收心多一排 ⇒ 种子 49 变 <b>59</b>,防御条上移到 {@code height-59}(正是原版护甲行所在);</li>
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
 * </ul>
 *
 * <p>⚠️ 若将来有覆盖层要用 gui 的辅助方法,必须在此补齐并在 {@code FabricClientBridges} 接线时
 * 提供真实数据 —— 否则会 NPE。
 */
public final class ForgeGui {
    public static final ForgeGui INSTANCE = new ForgeGui();

    /** 左列已占用高度(防御条读它后**无条件**自增 10)。 */
    public int leftHeight;

    /** 右列已占用高度(攻击条每帧把「本帧真实基准 + 10」写回,供后续 overlay 接着往上排)。 */
    public int rightHeight;

    /**
     * 原版左列基线:血量行的 y 是 {@code screenHeight - 39}({@code Gui.java:867})。
     * 左列种子从这里往上叠加「心排数 × 行距」,见 {@link #leftSeed}。
     */
    private static final int VANILLA_HEALTH_BASE = 39;

    /**
     * 原版行高上限:护甲行在血量行之上 {@code 10} 像素({@code Gui.java:872} 末尾的 {@code - 10})。
     */
    private static final int VANILLA_ROW_HEIGHT = 10;

    /**
     * 左列每帧种子 = 原版护甲行距屏幕底边的偏移,**按本帧心排数复算(不是常量)**。
     *
     * <p>与 1.20.1 {@code Gui#renderPlayerHealth} **逐字等价**。原文取证(Mojang 映射反编译源码
     * {@code forge-1.20.1/build/moddev/artifacts/forge-1.20.1-47.4.10-sources.jar} →
     * {@code net/minecraft/client/gui/Gui.java};括号内的「偏移」取自 fabric 线**实际运行**的那份
     * 合并 jar —— {@code fabric-1.20.1/build/loom-cache/argFiles/runClient} 的 classpath 条目
     * {@code .gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-83c93f4af0/
     * 1.20.1-loom.mappings.1_20_1.layered+hash.2198-v2/minecraft-merged-83c93f4af0-....jar}
     * (sha256 {@code d9128e827b7c385c...},与 {@code minecraft-merged-0b0f9d549f} 逐字节相同),
     * 由 {@code javap -p -c -l net.minecraft.client.gui.Gui} 逐条读出):
     * <pre>
     *   :861  int k = this.displayHealth;
     *   :867  int k1 = this.screenHeight - 39;                                  // 偏移 229-236
     *   :868  float f = Math.max((float)player.getAttributeValue(Attributes.MAX_HEALTH),
     *                            (float)Math.max(k, i));                        // 偏移 238-256
     *   :869  int l1 = Mth.ceil(player.getAbsorptionAmount());                  // 偏移 258-265
     *   :870  int i2 = Mth.ceil((f + (float)l1) / 2.0F / 10.0F);   // 心排数      // 偏移 267-282
     *   :871  int j2 = Math.max(10 - (i2 - 2), 3);                 // 行距        // 偏移 284-295
     *   :872  int k2 = k1 - (i2 - 1) * j2 - 10;                    // 护甲行      // 偏移 297-310
     * </pre>
     * 移项即 {@code 距底偏移 = 39 + (心排数-1) * 行距 + 10}。
     *
     * <p><b>吸收心为什么让它 +10</b>:{@code Gui.java:973-981} 的心排数含吸收心
     * ({@code j = ceil(maxHealth/2)}、{@code k = ceil(absorption/2)}、{@code i1 = j+k-1} 起倒排、
     * {@code i2 = y - i1/10 * height});吸收 I(4 点) ⇒ 心排数 1 变 2 ⇒ 吸收心画在
     * {@code k1 - 1*10} = {@code height-49},护甲行随之从 {@code height-49} 退到 {@code height-59}。
     *
     * <p><b>低血抖动不参与</b>:{@code Gui.java:982-984} 的 {@code random.nextInt(2)} 只改**心图标的 y**
     * (字节码偏移 108-130),不改心排数,故不进本式。
     *
     * @param maxHealthAttribute {@code getAttributeValue(Attributes.MAX_HEALTH)}(原版 :868 转 float)
     * @param displayHealth      原版 :861 的 {@code displayHealth};⚠️ 本线桩件**读不到**它:
     *                           {@code Gui.displayHealth} 是 {@code protected int}({@code Gui.java:139})
     *                           且 :861 直接读该字段,1.20.1 无任何 getter(全 jar 检索
     *                           {@code getDisplayHealth} 为 0 处),桩件又不在
     *                           {@code net.minecraft.client.gui} 包内 ⇒ 调用方传
     *                           {@code Mth.ceil(getHealth())} 代替。差异只在「最大生命被下调到当前生命
     *                           以下、且 1 秒内刚掉过血」这一窗口出现,且要跨过 20 点界才改变心排数。
     * @param health             原版 :843 的 {@code i = Mth.ceil(player.getHealth())}
     * @param absorption         {@code player.getAbsorptionAmount()}(原版 :869 再 ceil)
     * @return 左列距屏幕底偏移:单排心 49 / 两排心 59 / 三排心 67(行距 9)
     */
    public static int leftSeed(float maxHealthAttribute, int displayHealth, int health, float absorption) {
        float f = Math.max(maxHealthAttribute, (float) Math.max(displayHealth, health));
        int absorb = Mth.ceil(absorption);
        int rows = Mth.ceil((f + (float) absorb) / 2.0F / 10.0F);
        int rowHeight = Math.max(10 - (rows - 2), 3);
        return VANILLA_HEALTH_BASE + (rows - 1) * rowHeight + VANILLA_ROW_HEIGHT;
    }

    /**
     * 右列每帧起点(**原版画气泡行时**):原版已画「饱食度行」(height-39)与「气泡行」(height-49)
     * ⇒ 攻击条取 height-59 以避开气泡。见类头。
     */
    public static final int RESET_RIGHT = 59;

    /**
     * 右列每帧起点(**原版不画气泡行时**):原版只画了「饱食度行」(height-39)
     * ⇒ 攻击条取 height-49,紧贴饱食度上方一行,与 forge-1.20.1 / neoforge 两线一致。见类头。
     */
    public static final int RESET_RIGHT_NO_AIR = 49;

    private ForgeGui() {
    }
}
