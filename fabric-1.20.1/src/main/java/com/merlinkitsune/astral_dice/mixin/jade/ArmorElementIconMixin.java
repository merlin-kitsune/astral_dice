package com.merlinkitsune.astral_dice.mixin.jade;

import com.merlinkitsune.astral_dice.client.CombatPowerHud;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import snownee.jade.overlay.DisplayHelper;
import snownee.jade.overlay.IconUI;
import snownee.jade.overlay.OverlayRenderer;

/**
 * 把 Jade 护甲条的**图标源**从原版 {@code icons.png} 里的胸甲 UV 换成本模组的盾形防御图标
 * （Fabric 1.20.1 线）。
 *
 * <h2>画法与 neo 两线不同、与 forge 线**相同**</h2>
 * 11.13.3 的 {@code ArmorElement#render} 走 **UV 图集**而不是 sprite。Jade-Fabric 生产 jar 反编译实证：
 * <pre>
 * 25: getstatic     IconUI.ARMOR
 * 28: invokestatic  DisplayHelper.renderIcon:(Lnet/minecraft/class_332;FFIILsnownee/jade/overlay/IconUI;)V
 * …（ARMOR / HALF_ARMOR / EMPTY_ARMOR 三态各一处，另有 ARMOR 复用）
 * </pre>
 * {@code IconUI} 是硬编码 UV 的枚举（全部指向 {@code textures/gui/icons.png}）；UV 无法指向我们自己的贴图
 * ⇒ 只能整句替换成对自有贴图的 blit。替换后**三态齐活**：满/半用本模组盾形图，第三态用
 * {@code CombatPowerHud.DEFENSE_EMPTY}（与满图标逐像素同形、内芯 {@code #3D3D3D} 深灰）。
 *
 * <h2>透明度</h2>
 * 原 {@code DisplayHelper#renderIcon} 的第一件事就是
 * {@code RenderSystem.setShaderColor(1,1,1, OverlayRenderer.alpha)}（已核 {@code OverlayRenderer.alpha}
 * 为 {@code public static float}），我们整句替换后必须自己补上这一步，否则 tooltip 淡入淡出时图标不会跟着变淡。
 *
 * <h2>尺寸</h2>
 * 1.20 的图标位固定 8×8、步距 8、行高 10；我们的贴图是 9×9
 * ⇒ 按 {@code width=height=8}、{@code textureWidth=textureHeight=9} 绘制（轻微等比缩小），
 * 与原版此处「9×9 源区画进 8×8」的取法同构。
 *
 * <h2>回落</h2>
 * {@code IconUI} 里还有心形/经验球等其它常量（同一 {@code renderIcon} 也会服务血量行）
 * ⇒ 非护甲三态一律回落 {@code DisplayHelper.renderIcon}，行为与原来逐字节相同。
 *
 * <h2>与 forge 线的两处平台差异（**不要互抄**）</h2>
 * <ol>
 *   <li><b>{@code method} 必须写**纯方法名</b>（{@code "render"}）</b>：
 *       Loom 的 {@code remapJar} **只重映射纯方法名形式**的 {@code method}，带描述符者**原样透传**；
 *       而本方法的描述符里含**原版类** {@code GuiGraphics}（intermediary 下是 {@code class_332}）
 *       ⇒ 若照抄 forge 的 {@code "render(Lnet/minecraft/client/gui/GuiGraphics;FFFF)V"}，
 *       该字符串会被原样保留、生产环境**永远匹配不到**目标方法 ⇒ 注入静默失败。
 *       重载歧义交给 Mixin 按 handler 签名自动筛选。</li>
 *   <li><b>**不要**写 {@code remap = false}</b>：forge 线上两个目标都是 Jade 成员、无可重映射项，
 *       故必须显式关掉重映射以绕开 AP 门禁；但本线的 {@code @At} target 描述符里含
 *       {@code GuiGraphics}，**必须**经重映射变成 {@code class_332} 才能命中。</li>
 * </ol>
 * <p>处理器写 {@code static} 是安全的：本线 Mixin 0.8.7 的 {@code checkTargetModifiers(target, false)}
 * 只拦「非 static 处理器打 static 方法」。
 */
@Mixin(targets = "snownee.jade.impl.ui.ArmorElement")
public abstract class ArmorElementIconMixin {

    @Redirect(
            // ⚠️ 纯方法名 —— 描述符含原版 GuiGraphics，而 Loom 只重映射纯方法名（详见类头）。
            method = "render",
            // 重定向对 DisplayHelper.renderIcon 的调用（其形参里含 Jade 的 IconUI，无法绕开类型引用）
            at = @At(value = "INVOKE",
                    target = "Lsnownee/jade/overlay/DisplayHelper;"
                            + "renderIcon(Lnet/minecraft/client/gui/GuiGraphics;FFIILsnownee/jade/overlay/IconUI;)V"))
    private static void astralDice$defenseIcons(GuiGraphics guiGraphics, float x, float y,
                                                int sx, int sy, IconUI icon) {
        ResourceLocation ours = astralDice$defenseIcon(icon);
        if (ours == null) {
            DisplayHelper.renderIcon(guiGraphics, x, y, sx, sy, icon);
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // 与原 DisplayHelper#renderIcon 同一透明度口径（tooltip 淡入淡出）
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, OverlayRenderer.alpha);
        guiGraphics.blit(ours, (int) x, (int) y, 0.0F, 0.0F, sx, sy, 9, 9);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** 护甲三态 {@code IconUI} → 本模组三态贴图；其余图标返回 {@code null} 表示不接管。 */
    private static ResourceLocation astralDice$defenseIcon(IconUI icon) {
        if (icon == IconUI.ARMOR) {
            return CombatPowerHud.DEFENSE_FULL;
        }
        if (icon == IconUI.HALF_ARMOR) {
            return CombatPowerHud.DEFENSE_HALF;
        }
        if (icon == IconUI.EMPTY_ARMOR) {
            return CombatPowerHud.DEFENSE_EMPTY;
        }
        return null;
    }
}
