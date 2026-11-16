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
import net.minecraft.tags.FluidTags;
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
 *       （见其类头：左列每帧复位为 49；右列复位为 59 这个**上限基准**，本类的 overlay 再按
 *       本帧原版实况取值并写回累加量）。</li>
 *   <li><b>攻击条的基准按「原版此刻是否占气泡行」动态取</b>：本线的原版气泡行固定画在
 *       {@code height-49} 且不参与本模组的累加 ⇒ 原版画气泡时攻击条让到 {@code height-59}
 *       （在气泡之上，不盖住气泡）；原版不画气泡时那一行是空的，攻击条贴回 {@code height-49}
 *       （紧贴饱食度上方一行，与 forge / neoforge 三线一致）。判定与原版
 *       {@code Gui#renderPlayerHealth} 的「是否画气泡行」条件逐字等价，见 {@link #airRowShown}。</li>
 * </ol>
 *
 * <h2>坐标来源：{@link ForgeGui} 的高度累加量（禁止硬编码屏幕常量）</h2>
 * <ul>
 *   <li><b>防御条</b>：{@code y = height - gui.leftHeight}（起点 49 = 原版护甲条那一行），
 *       画完**无条件** {@code leftHeight += 10}（原版的 {@code += 10} 随护甲绘制一起被取消）。</li>
 *   <li><b>攻击条</b>：基准 {@code B} 由「原版这一帧是否占气泡行」决定 —— 占
 *       （{@code height-49} 是气泡行）⇒ {@code B = 59}；不占 ⇒ {@code B = 49}（紧贴饱食度上方一行）。
 *       {@code y = height - B}，画完把 {@code B + 10} 写回 {@code gui.rightHeight}（保持「已占高度」
 *       累加量语义）⇒ 后续新增的 overlay 会被排到本条**上面**。</li>
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
        return ResourceLocation.fromNamespaceAndPath("astral_dice", "textures/gui/" + name + ".png");
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
        // 基准 = 原版这一帧右列**已经占掉**的高度(从屏幕底边算)。原版右列只可能占两处:
        //   饱食度行恒在 height-39;气泡行(仅当原版决定画时)在 height-49。
        // ⇒ 画气泡 ⇒ 基准 59(攻击条落在气泡行之上,不遮气泡);
        //    不画气泡 ⇒ 基准 49(紧贴饱食度上方一行,与 forge / neoforge 三线一致)。
        int base = airRowShown(player) ? ForgeGui.RESET_RIGHT : ForgeGui.RESET_RIGHT_NO_AIR;
        int y = height - base;
        drawRow(guiGraphics, ATTACK_FULL, ATTACK_HALF, width / 2 + 91, y, attack, true);
        // 本行占掉一行 ⇒ 把本帧真实基准 + 行高写回累加量(后续 overlay 从这里继续往上排)。
        gui.rightHeight = base + ROW_HEIGHT;
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
     * 原版这一帧是否会画**气泡行**（{@code Gui#renderPlayerHealth} 的 {@code "air"} 段）。
     *
     * <p>条件与 1.20.1 原版**逐字等价**（不是「有氧气值」之类的近似条件）：
     * <pre>
     *   int l5 = player.getMaxAirSupply();
     *   int i6 = Math.min(player.getAirSupply(), l5);
     *   if (player.isEyeInFluid(FluidTags.WATER) || i6 &lt; l5) { ... }
     * </pre>
     *
     * <p>取证（Mojang 映射，非记忆）：
     * <ul>
     *   <li>反编译源码 {@code forge-1.20.1/build/moddev/artifacts/forge-1.20.1-47.4.10-sources.jar}
     *       → {@code net/minecraft/client/gui/Gui.java:933-936}（原文见上）；</li>
     *   <li>字节码（本线实际运行的那份映射）
     *       {@code .gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-*...jar}
     *       → {@code net.minecraft.client.gui.Gui#renderPlayerHealth}：offset 770-782
     *       {@code getMaxAirSupply} / {@code Math.min(getAirSupply(), l5)}、offset 787-801
     *       {@code isEyeInFluid(FluidTags.WATER)} + {@code if_icmpge}。</li>
     * </ul>
     *
     * <p>气泡行的 y 也一并核过：{@code l2 = (height - 39) - 10}（offset 313-317）；饱食度行被画时
     * {@code l2 -= 10}（offset 752 {@code iinc 18, -10}）；随后
     * {@code l2 -= (getVisibleVehicleHeartRows(k5) - 1) * 10}（offset 804-822，其中
     * {@code getVisibleVehicleHeartRows(x) = ceil(x / 10.0)}）。非骑乘时 {@code k5 = 0} ⇒
     * {@code rows - 1 = -1} ⇒ 又把上面那 10 加回来，净结果仍是 {@code height-49} —— 与
     * 「气泡行占 height-49、攻击条必须让开该行」的前提一致。
     */
    private static boolean airRowShown(LocalPlayer player) {
        int maxAir = player.getMaxAirSupply();
        int air = Math.min(player.getAirSupply(), maxAir);
        return player.isEyeInFluid(FluidTags.WATER) || air < maxAir;
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
