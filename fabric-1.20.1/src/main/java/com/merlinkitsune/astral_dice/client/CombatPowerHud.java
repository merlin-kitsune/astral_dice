package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.combat.DiceCombatModifiers;
import com.merlinkitsune.astral_dice.platform.client.gui.overlay.ForgeGui;
import com.merlinkitsune.astral_dice.platform.client.gui.overlay.IGuiOverlay;
import com.merlinkitsune.astral_dice.platform.client.gui.overlay.VanillaGuiOverlay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * 战斗数值 HUD(**Fabric 1.20.1 客户端**)：**防御力条**替换原版护甲条，**攻击力条**固定在饱食度上方。
 *
 * <h2>数值口径</h2>
 * 直接复用结算口径：防御力 = {@link DiceCombatModifiers#defensePowerOf}，攻击力 =
 * {@link DiceCombatModifiers#attackPowerDisplayOf}（**手持武器不参与显示**，用户需求；
 * 与战斗口径 {@code attackPowerOf} 的差异见该方法注释）。两者下限为 2 / 1，恒 &gt; 0 ⇒ **两条 bar 永远显示**。
 *
 * <h2>平台差异（与 forge-1.20.1 逐字等价的部分 + 本线特有的三处）</h2>
 * <ol>
 *   <li><b>没有 {@code @Mod.EventBusSubscriber}</b>（Fabric 无该机制）⇒ 本类只被
 *       {@code client/ModClientEvents#registerGuiOverlays} 引用（注册两个 overlay）；
 *       Forge 侧的 {@code onRenderGuiOverlayPre}（取消 {@code ARMOR_LEVEL} / OAB 图层）
 *       在本线由 mixin {@code mixin.client.ArmorBarCancelMixin} 承担（取消原版护甲绘制）。</li>
 *   <li><b>图层顺序不由 anchor 决定</b>：本线的 {@code RegisterGuiOverlaysEvent} 只是把条目
 *       按调用顺序收进一个 List，{@code FabricClientBridges} 在 {@code HudRenderCallback} 里
 *       **按注册顺序**逐条渲染（anchor 仅作记录）。故「防御条占护甲行、攻击条在饱食度上方」
 *       不依赖 {@code registerAbove} 的插入语义，而依赖 {@link ForgeGui} 的两列高度累加量
 *       （见其类头：每帧复位为 49 / 59，本类的 overlay **先读后增**）。</li>
 *   <li><b>攻击条比 Forge 高一行</b>：本线的原版空气条固定画在 {@code height-49} 且不参与
 *       累加，攻击条取 {@code height-59} 以避开它（Forge 侧则是把空气条一并推走）。</li>
 * </ol>
 *
 * <h2>坐标来源：{@link ForgeGui} 的高度累加量（禁止硬编码屏幕常量）</h2>
 * <ul>
 *   <li><b>防御条</b>：{@code y = height - gui.leftHeight}（起点 49 = 原版护甲条那一行），
 *       画完**无条件** {@code leftHeight += 10}（原版的 {@code += 10} 随护甲绘制一起被取消）。</li>
 *   <li><b>攻击条</b>：{@code y = height - gui.rightHeight}（起点 59 = 饱食度与空气行之上的那一行），
 *       画完 {@code rightHeight += 10}。用累加量而非屏幕常量 ⇒ 后续新增的 overlay 会被排到本条**上面**。</li>
 * </ul>
 *
 * <h2>图标与超过 20 点的退化</h2>
 * 一行最多 10 个图标 = 20 点。{@code ≤ 20} 时第 k 个图标按 {@code k*2+1 < v ⇒ 满}、
 * {@code == v ⇒ 半}、{@code > v ⇒ 不画}（**空态保持透明**）。{@code > 20} 时照 Jade 的口径退化为
 * 「**1 个满图标 + 数字**」。
 *
 * <h2>性能</h2>
 * 用「玩家 tick 变了才重算」缓存（{@code Entity.tickCount} 是 public 字段），
 * 每游戏刻至多一次，渲染路径只读缓存的两个 int。
 */
public final class CombatPowerHud {

    /**
     * 三态防御图标。**public**：供将来 Jade 兼容复用同一套贴图。
     *
     * <p>{@code defense_empty} 是「外形与满图标一致、内芯换成深灰 {@code #3D3D3D}」的**第三态**
     * （照原版 {@code icons.png} 里 {@code EMPTY_ARMOR} 的派生规则生成）。HUD 的空态按裁决
     * **保持透明**、不画，所以这一张目前只对 Jade 的图标位有意义。
     */
    public static final ResourceLocation DEFENSE_FULL = texture("defense_full");
    public static final ResourceLocation DEFENSE_HALF = texture("defense_half");
    public static final ResourceLocation DEFENSE_EMPTY = texture("defense_empty");

    private static final ResourceLocation ATTACK_FULL = texture("attack_full");
    private static final ResourceLocation ATTACK_HALF = texture("attack_half");

    /** 图标边长与步距：与原版护甲/饱食图标一致（9 像素图标、8 像素步距）。 */
    private static final int ICON_SIZE = 9;
    private static final int ICON_STEP = 8;
    /** 一行最多 10 个图标；每图标 2 点 ⇒ 一行 = 20 点。 */
    private static final int MAX_ICONS = 10;
    private static final int POINTS_PER_ICON = 2;
    /** 行高：与原版各段自增的 10 一致。 */
    private static final int ROW_HEIGHT = 10;
    /** 数字颜色：纯白带阴影（沿用本线既有的 {@code 0xFFFFFF} 口径）。 */
    private static final int TEXT_COLOR = 0xFFFFFF;

    private static ResourceLocation texture(String name) {
        return new ResourceLocation("astral_dice", "textures/gui/" + name + ".png");
    }

    // === 数值缓存：每游戏刻至多算一次 ===
    // 注意：字段必须声明在 overlay 实例**之前**——静态初始化表达式里引用「声明在后的字段」
    // 属非法前向引用（方法调用不受此限，故 renderTarget/refreshValues/drawRow 可后置）。

    private static LocalPlayer cachedPlayer;
    private static int cachedTick = Integer.MIN_VALUE;
    private static int defense;
    private static int attack;

    // === overlay 实例（供 ModClientEvents#registerGuiOverlays 注册） ===

    public static final IGuiOverlay DEFENSE_OVERLAY = (gui, guiGraphics, partialTick, width, height) -> {
        LocalPlayer player = renderTarget();
        if (player == null) return;
        refreshValues(player);
        int y = height - gui.leftHeight;
        drawRow(guiGraphics, DEFENSE_FULL, DEFENSE_HALF, width / 2 - 91, y, defense, false);
        // 防御条恒显 ⇒ 无条件占一行（原版 renderArmor 的 += 10 已随护甲绘制被取消）
        gui.leftHeight += ROW_HEIGHT;
    };

    public static final IGuiOverlay ATTACK_OVERLAY = (gui, guiGraphics, partialTick, width, height) -> {
        LocalPlayer player = renderTarget();
        if (player == null) return;
        // 骑乘时原版把右列那一行让给载具血量 ⇒ 攻击条同进同退，否则会抢掉载具血量的行位。
        Entity vehicle = player.getVehicle();
        if (vehicle instanceof LivingEntity mount && mount.showVehicleHealth()) return;
        refreshValues(player);
        int y = height - gui.rightHeight;
        drawRow(guiGraphics, ATTACK_FULL, ATTACK_HALF, width / 2 + 91, y, attack, true);
        gui.rightHeight += ROW_HEIGHT;
    };

    private CombatPowerHud() {
    }

    /**
     * 取本次渲染的玩家；不该画 HUD 时返回 {@code null}。
     *
     * <p>门控逐条对齐原版生存元素：{@code hideGui}（F1，用户裁决「遵循隐藏规则」）、
     * {@code canHurtPlayer()}（原版各生存 overlay 的 {@code shouldDrawSurvivalElements()} 条件）、
     * 相机玩家非空。模组 overlay 不被原版条件包裹，必须自行守。
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
     *                     {@code width/2 - 91}），{@code true} 时为**右端**
     *                     （原版饱食度的 {@code width/2 + 91}）
     * @param rightAligned 是否按饱食度那样**从右往左**排（第 0 个图标在最右）
     * @param points       当前数值（点）；{@code ≤ 20} 画图标，{@code > 20} 退化为「1 图标 + 数字」
     */
    private static void drawRow(GuiGraphics guiGraphics, ResourceLocation full, ResourceLocation half,
                                int anchorX, int y, int points, boolean rightAligned) {
        if (points > MAX_ICONS * POINTS_PER_ICON) {
            blit(guiGraphics, full, iconX(anchorX, 0, rightAligned), y);
            Font font = Minecraft.getInstance().font;
            String text = Integer.toString(points);
            int textX = rightAligned
                    ? anchorX - ICON_SIZE - font.width(text) - 4
                    : anchorX + ICON_SIZE + 2;
            guiGraphics.drawString(font, text, textX, y + 1, TEXT_COLOR, true);
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

    /** 用本模组自己的 9x9 贴图整张绘制（不走 sprite 图集，与本仓其它 HUD 一致）。 */
    private static void blit(GuiGraphics guiGraphics, ResourceLocation texture, int x, int y) {
        guiGraphics.blit(texture, x, y, 0.0F, 0.0F, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
    }
}
