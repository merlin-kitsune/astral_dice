package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.combat.DiceCombatModifiers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 战斗数值 HUD（Forge 1.20.1 客户端）：**防御力条**替换原版护甲条，**攻击力条**固定在饱食度正上方。
 *
 * <h2>数值口径</h2>
 * 直接复用结算口径：防御力 = {@link DiceCombatModifiers#defensePowerOf}，攻击力 =
 * {@link DiceCombatModifiers#attackPowerDisplayOf}（**手持武器不参与显示**，用户需求；
 * 与战斗口径 {@code attackPowerOf} 的差异见该方法注释）。两者下限为 2 / 1，恒 &gt; 0 ⇒ **两条 bar 永远显示**。
 *
 * <h2>替换原版护甲条（含 OAB）</h2>
 * 取消走 {@link RenderGuiOverlayEvent.Pre}：{@code event.getOverlay().id()} 命中
 * {@link VanillaGuiOverlay#ARMOR_LEVEL} 或 {@code overloadedarmorbar:overloadedarmorbar}
 * 时 {@code setCanceled(true)}。取消后 Forge 的 overlay 派发会**跳过整个 overlay**
 * （{@code ForgeGui.render} 的 {@code if (pre(entry, guiGraphics)) return;}），
 * 于是 {@code ForgeGui#renderArmor} 里的 {@code leftHeight += 10} 也一并被跳过
 * ⇒ 必须由本类补回（见下）。
 *
 * <h2>坐标来源：原版自己的高度累加量（禁止硬编码）</h2>
 * 1.20.1 的**普通 {@code Gui} 没有** {@code leftHeight}/{@code rightHeight}；
 * 它们在 {@link ForgeGui} 上，且是 {@code public int}
 * （authored 源 {@code client/gui/overlay/ForgeGui.java:60-61}，在 {@code ForgeGui#render} 开头重置为 39）。
 * <ul>
 *   <li><b>防御条</b>：{@code y = height - gui.leftHeight}。Forge 的 {@code renderArmor} 就是
 *       {@code top = height - leftHeight}，本类补位保持同一行；画完**无条件**
 *       {@code leftHeight += 10}（原版的 {@code += 10} 在 {@code renderArmor} 里，已被取消）。</li>
 *   <li><b>攻击条</b>：注册在 {@code FOOD_LEVEL} **之上**（Forge 的
 *       {@code RegisterGuiOverlaysEvent#registerAbove} 插到锚点之后一位，故落在
 *       {@code FOOD_LEVEL} 与 {@code AIR_LEVEL} 之间）；Forge 的 {@code renderFood} 是
 *       {@code top = height - rightHeight} 后立刻 {@code rightHeight += 10}，故此处
 *       {@code y = height - rightHeight} 正好是饱食度正上方一行。用的是原版累加量而非屏幕常量
 *       ⇒ 其它模组的 bar 只会被排到本条**上面**，本条不会被顶开。</li>
 * </ul>
 *
 * <h2>图标与超过 20 点的退化</h2>
 * 一行最多 10 个图标 = 20 点。{@code ≤ 20} 时第 k 个图标按 {@code k*2+1 < v ⇒ 满}、
 * {@code == v ⇒ 半}、{@code > v ⇒ 不画}（**空态保持透明**）。{@code > 20} 时照 Jade 的口径退化为
 * 「**1 个满图标 + 数字**」（Jade 1.20-forge 分支 {@code ArmorElement.render} 的
 * {@code renderIcon(IconUI.ARMOR)} + {@code "  " + dfCommas.format(armor)} 即此形态）。
 *
 * <h2>与 M1（1.21.1）/ M4（26.1.2）的差异</h2>
 * 本类是 forge 专用写法：{@code IGuiOverlay} + {@code ForgeGui} + {@code RenderGuiOverlayEvent}，
 * 图层注册在 {@code ModClientEvents#registerGuiOverlays}（mod 总线）；本类自身的
 * {@code @Mod.EventBusSubscriber} **不写 bus**（默认 {@code Bus.FORGE} = 游戏总线），
 * 与 1.21.1/26.1.2 侧 {@code @EventBusSubscriber(...) @SubscribeEvent} 的写法对等。
 *
 * <h2>性能</h2>
 * 同 1.21.1 侧：用「玩家 tick 变了才重算」缓存（{@code Entity.tickCount} 是 public 字段），
 * 每游戏刻至多一次，渲染路径只读缓存的两个 int。
 */
@Mod.EventBusSubscriber(modid = AstralDiceMod.MODID, value = Dist.CLIENT)
public final class CombatPowerHud {

    /** OAB（护甲上限突破）的图层 id：它同样占用护甲条那一行。 */
    private static final ResourceLocation OVERLOADED_ARMOR_BAR_LAYER =
            new ResourceLocation("overloadedarmorbar", "overloadedarmorbar");

    /**
     * 三态防御图标。**public**：Jade 兼容 Mixin（{@code mixin.jade}）复用同一套贴图。
     *
     * <p>{@code defense_empty} 是「外形与满图标一致、内芯换成深灰 {@code #3D3D3D}」的**第三态**
     * （照原版 {@code icons.png} 里 {@code EMPTY_ARMOR} 的派生规则生成）。HUD 的空态按裁决
     * **保持透明**、不画，所以这一张只有 Jade 的图标位（先铺底色再叠满/半图标）用得到。
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
        return new ResourceLocation(AstralDiceMod.MODID, "textures/gui/" + name + ".png");
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
        // 防御条恒显 ⇒ 无条件占一行（原版 renderArmor 的 += 10 已随 overlay 取消被跳过）
        gui.leftHeight += ROW_HEIGHT;
    };

    public static final IGuiOverlay ATTACK_OVERLAY = (gui, guiGraphics, partialTick, width, height) -> {
        LocalPlayer player = renderTarget();
        if (player == null) return;
        // 骑乘时原版把右列那一行让给载具血量（VanillaGuiOverlay#FOOD_LEVEL 的 isMounted 判定）
        // ⇒ 攻击条同进同退，否则会抢掉载具血量的行位。
        Entity vehicle = player.getVehicle();
        if (vehicle instanceof LivingEntity mount && mount.showVehicleHealth()) return;
        refreshValues(player);
        int y = height - gui.rightHeight;
        drawRow(guiGraphics, ATTACK_FULL, ATTACK_HALF, width / 2 + 91, y, attack, true);
        gui.rightHeight += ROW_HEIGHT;
    };

    private CombatPowerHud() {
    }

    /** 取消原版护甲条 + OAB 的护甲条，让防御力条独占「护甲条那一行」。 */
    @SubscribeEvent
    public static void onRenderGuiOverlayPre(RenderGuiOverlayEvent.Pre event) {
        ResourceLocation id = event.getOverlay().id();
        if (VanillaGuiOverlay.ARMOR_LEVEL.id().equals(id) || OVERLOADED_ARMOR_BAR_LAYER.equals(id)) {
            event.setCanceled(true);
        }
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
