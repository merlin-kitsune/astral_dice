package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.combat.DiceCombatModifiers;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.GuiLayer;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * 战斗数值 HUD（NeoForge 26.1.2 客户端）：**防御力条**替换原版护甲条，**攻击力条**固定在饱食度正上方。
 *
 * <h2>数值口径</h2>
 * 直接复用结算口径：防御力 = {@link DiceCombatModifiers#defensePowerOf}，攻击力 =
 * {@link DiceCombatModifiers#attackPowerDisplayOf}（**手持武器不参与显示**，用户需求；
 * 与战斗口径 {@code attackPowerOf} 的差异见该方法注释）。两者下限为 2 / 1，恒 &gt; 0 ⇒ **两条 bar 永远显示**。
 *
 * <h2>替换原版护甲条（含 OAB）</h2>
 * 取消走 {@link RenderGuiLayerEvent.Pre}：命中 {@link VanillaGuiLayers#ARMOR_LEVEL} 或
 * {@code overloadedarmorbar:overloadedarmorbar} 时 {@code setCanceled(true)}。26.1.2 的
 * {@code Pre} 是 {@code ICancellableEvent}（neoforge 源 {@code client/event/RenderGuiLayerEvent.java}），
 * 取消后该图层不渲染、对应 {@code Post} 也不派发。
 *
 * <h2>坐标来源：原版自己的高度累加量（禁止硬编码）</h2>
 * {@code Gui.leftHeight}/{@code rightHeight} 是 {@code public int}
 * （MC 源 {@code client/gui/Gui.java:200-204}）；{@code Gui.extractRenderState} 每帧先重置为 39
 * （同文件 :217-218）再逐段自增。
 * <ul>
 *   <li><b>防御条</b>：{@code y = guiHeight - leftHeight}，等价于原版护甲行
 *       （{@code extractArmorLevel} 传 {@code guiHeight - leftHeight + 10}，{@code extractArmor}
 *       内部再 {@code -10} 抵消）；画完**无条件** {@code leftHeight += 10}（原版仅 armor&gt;0 时占行）。</li>
 *   <li><b>攻击条</b>：注册在 {@code FOOD_LEVEL} **之上**（该锚点在扁平化后的图层表里紧跟
 *       {@code FOOD_LEVEL}，早于 {@code VEHICLE_HEALTH}/{@code AIR_LEVEL}），故
 *       {@code y = guiHeight - rightHeight} 正好是饱食度正上方一行；画完 {@code rightHeight += 10}。
 *       用原版累加量而非屏幕常量 ⇒ 其它模组的 bar 只会被排到本条**上面**，本条不会被顶开。</li>
 * </ul>
 *
 * <h2>图标与超过 20 点的退化</h2>
 * 一行最多 10 个图标 = 20 点。{@code ≤ 20} 时第 k 个图标按 {@code k*2+1 < v ⇒ 满}、
 * {@code == v ⇒ 半}、{@code > v ⇒ 不画}（**空态保持透明**）。{@code > 20} 时照 Jade 的口径退化为
 * 「**1 个满图标 + 数字**」（Jade 1.26.1 线 {@code ArmorElement} 自身的 >20 退化即「数值文本」形态，
 * 本仓三线统一为「图标 + 数字」以对齐 1.20.1 分支的既有形态）。
 *
 * <h2>与 M1（1.21.1）/ F2（forge 1.20.1）的差异</h2>
 * 本类为 26.1.2 专用写法：**没有 {@code GuiGraphics}**，一律用 {@code GuiGraphicsExtractor} +
 * {@code RenderPipelines.GUI_TEXTURED} + {@code Identifier}；图层接口是 {@code GuiLayer}
 * 而非 {@code LayeredDraw.Layer}；{@code text(...)} 的颜色**必须是 ARGB**
 * （alpha=0 会被整段丢弃，与本仓 26.1.2 钱包控件的实测结论一致）。三份**不得互相照抄**。
 *
 * <h2>性能</h2>
 * 同另两线：用「玩家 tick 变了才重算」缓存（{@code Entity.tickCount} 是 public 字段），
 * 每游戏刻至多一次，渲染路径只读缓存的两个 int。
 */
@EventBusSubscriber(modid = AstralDiceMod.MODID, value = Dist.CLIENT)
public final class CombatPowerHud {

    /** 图层 id（注册在 {@code ModClientEvents#registerGuiLayers}）。 */
    public static final Identifier DEFENSE_LAYER_ID =
            Identifier.fromNamespaceAndPath(AstralDiceMod.MODID, "defense_bar");
    public static final Identifier ATTACK_LAYER_ID =
            Identifier.fromNamespaceAndPath(AstralDiceMod.MODID, "attack_bar");

    /** OAB（护甲上限突破）的图层 id：它同样占用护甲条那一行。 */
    private static final Identifier OVERLOADED_ARMOR_BAR_LAYER =
            Identifier.fromNamespaceAndPath("overloadedarmorbar", "overloadedarmorbar");

    /**
     * 三态防御图标。**public**：Jade 兼容 Mixin（{@code mixin.jade}）复用同一套贴图。
     *
     * <p>{@code defense_empty} 是「外形与满图标一致、内芯换成深灰 {@code #3D3D3D}」的**第三态**
     * （照原版 {@code hud/armor_empty} 的派生规则生成）。HUD 的空态按裁决**保持透明**、不画，
     * 所以这一张只有 Jade 的图标位（先铺底色再叠满/半图标）用得到。
     */
    public static final Identifier DEFENSE_FULL = texture("defense_full");
    public static final Identifier DEFENSE_HALF = texture("defense_half");
    public static final Identifier DEFENSE_EMPTY = texture("defense_empty");

    private static final Identifier ATTACK_FULL = texture("attack_full");
    private static final Identifier ATTACK_HALF = texture("attack_half");

    /** 图标边长与步距：与原版护甲/饱食图标一致（9 像素图标、8 像素步距）。 */
    private static final int ICON_SIZE = 9;
    private static final int ICON_STEP = 8;
    /** 一行最多 10 个图标；每图标 2 点 ⇒ 一行 = 20 点。 */
    private static final int MAX_ICONS = 10;
    private static final int POINTS_PER_ICON = 2;
    /** 行高：与原版各段自增的 10 一致。 */
    private static final int ROW_HEIGHT = 10;
    /** 数字颜色：纯白带阴影，**必须 ARGB**（本版本 alpha=0 会被丢弃）。 */
    private static final int TEXT_COLOR = 0xFFFFFFFF;

    private static Identifier texture(String name) {
        return Identifier.fromNamespaceAndPath(AstralDiceMod.MODID, "textures/gui/" + name + ".png");
    }

    // === 图层实例（供 ModClientEvents 注册） ===

    public static final GuiLayer DEFENSE_LAYER = (guiGraphics, deltaTracker) -> renderDefense(guiGraphics);
    public static final GuiLayer ATTACK_LAYER = (guiGraphics, deltaTracker) -> renderAttack(guiGraphics);

    // === 数值缓存：每游戏刻至多算一次 ===

    private static LocalPlayer cachedPlayer;
    private static int cachedTick = Integer.MIN_VALUE;
    private static int defense;
    private static int attack;

    private CombatPowerHud() {
    }

    /**
     * 取消原版护甲条 + OAB 的护甲条，让防御力条独占「护甲条那一行」。
     *
     * <p>本方法对本帧**每一个**图层都会被调用一次，故只做两次相等比较（常数开销）。
     */
    @SubscribeEvent
    public static void onRenderGuiLayerPre(RenderGuiLayerEvent.Pre event) {
        Identifier name = event.getName();
        if (VanillaGuiLayers.ARMOR_LEVEL.equals(name) || OVERLOADED_ARMOR_BAR_LAYER.equals(name)) {
            event.setCanceled(true);
        }
    }

    private static void renderDefense(GuiGraphicsExtractor guiGraphics) {
        LocalPlayer player = renderTarget();
        if (player == null) return;
        refreshValues(player);
        Gui gui = Minecraft.getInstance().gui;
        int y = guiGraphics.guiHeight() - gui.leftHeight;
        drawRow(guiGraphics, DEFENSE_FULL, DEFENSE_HALF, guiGraphics.guiWidth() / 2 - 91, y, defense, false);
        // 防御条恒显 ⇒ 无条件占一行（原版仅 armor>0 时占，见类头）
        gui.leftHeight += ROW_HEIGHT;
    }

    private static void renderAttack(GuiGraphicsExtractor guiGraphics) {
        LocalPlayer player = renderTarget();
        if (player == null) return;
        // 骑乘时原版把右列那一行让给载具血量（extractFoodLevel 与 extractVehicleHealth 共用
        // getVehicleMaxHearts 判定）⇒ 攻击条同进同退，否则会抢掉载具血量的行位。
        Entity vehicle = player.getVehicle();
        if (vehicle instanceof LivingEntity mount && mount.showVehicleHealth()) return;
        refreshValues(player);
        Gui gui = Minecraft.getInstance().gui;
        int y = guiGraphics.guiHeight() - gui.rightHeight;
        drawRow(guiGraphics, ATTACK_FULL, ATTACK_HALF, guiGraphics.guiWidth() / 2 + 91, y, attack, true);
        gui.rightHeight += ROW_HEIGHT;
    }

    /**
     * 取本次渲染的玩家；不该画 HUD 时返回 {@code null}。
     *
     * <p>门控逐条对齐原版生存元素：本版本原版层被 {@code survivalVisible}
     * （{@code canHurtPlayer() && !hideGui}，MC 源 {@code Gui.java:225-226}）包裹，
     * 而**模组层不被包裹** ⇒ 必须自行守。相机玩家非空为第三道。
     */
    private static LocalPlayer renderTarget() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return null;
        if (mc.gameMode == null || !mc.gameMode.canHurtPlayer()) return null;
        return mc.getCameraEntity() instanceof LocalPlayer local ? local : null;
    }

    private static void refreshValues(LocalPlayer player) {
        if (player == cachedPlayer && player.tickCount == cachedTick) return;
        cachedPlayer = player;
        cachedTick = player.tickCount;
        defense = DiceCombatModifiers.defensePowerOf(player);
        attack = DiceCombatModifiers.attackPowerDisplayOf(player);
    }

    /**
     * 画一行战斗数值图标。
     *
     * @param anchorX      行锚点：{@code rightAligned=false} 时为**左端**（原版护甲/血量的
     *                     {@code guiWidth/2 - 91}），{@code true} 时为**右端**
     *                     （原版饱食度的 {@code guiWidth/2 + 91}）
     * @param rightAligned 是否按饱食度那样**从右往左**排（第 0 个图标在最右）
     * @param points       当前数值（点）；{@code ≤ 20} 画图标，{@code > 20} 退化为「1 图标 + 数字」
     */
    private static void drawRow(GuiGraphicsExtractor guiGraphics, Identifier full, Identifier half,
                                int anchorX, int y, int points, boolean rightAligned) {
        if (points > MAX_ICONS * POINTS_PER_ICON) {
            blit(guiGraphics, full, iconX(anchorX, 0, rightAligned), y);
            Font font = Minecraft.getInstance().font;
            String text = Integer.toString(points);
            int textX = rightAligned
                    ? anchorX - ICON_SIZE - font.width(text) - 4
                    : anchorX + ICON_SIZE + 2;
            guiGraphics.text(font, text, textX, y + 1, TEXT_COLOR, true);
            return;
        }
        for (int k = 0; k < MAX_ICONS; k++) {
            int index = k * POINTS_PER_ICON + 1;
            if (index > points) continue; // 空态：保持透明，什么都不画
            blit(guiGraphics, index == points ? half : full, iconX(anchorX, k, rightAligned), y);
        }
    }

    /** 第 {@code k} 个图标的左端 x：左对齐 = 锚点 + k*8；右对齐 = 锚点 - k*8 - 9（与饱食度同式）。 */
    private static int iconX(int anchorX, int k, boolean rightAligned) {
        return rightAligned ? anchorX - k * ICON_STEP - ICON_SIZE : anchorX + k * ICON_STEP;
    }

    /**
     * 用本模组自己的 9x9 贴图整张绘制。
     *
     * <p>26.1.2 只能用**带 {@code RenderPipeline} 的重载**（语义 = {@code x, y, u, v, w, h, texW, texH}）；
     * 另有一个只给 {@code Identifier} 的重载，其四个 int 是**绝对终点坐标**、四个 float 是**已归一化 UV**，
     * 语义完全不同（本仓 26.1.2 钱包控件已实测踩坑并登记）。
     */
    private static void blit(GuiGraphicsExtractor guiGraphics, Identifier texture, int x, int y) {
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y,
                0.0F, 0.0F, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
    }
}
