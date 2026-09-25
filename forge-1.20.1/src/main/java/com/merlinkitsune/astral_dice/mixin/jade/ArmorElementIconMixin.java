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
 * （Forge 1.20.1 线）。
 *
 * <h2>1.20 的画法与 neo 两线完全不同</h2>
 * 11.13.3 的 {@code ArmorElement#render}（源码
 * {@code Jade-120-forge/src/main/java/snownee/jade/impl/ui/ArmorElement.java:40-73}）走
 * **UV 图集**而不是 sprite：
 * <pre>
 * DisplayHelper.renderIcon(guiGraphics, x + xOffset, y, 8, 8, IconUI.ARMOR);         // 满
 * DisplayHelper.renderIcon(guiGraphics, x + xOffset, y, 8, 8, IconUI.HALF_ARMOR);    // 半
 * DisplayHelper.renderIcon(guiGraphics, x + xOffset, y, 8, 8, IconUI.EMPTY_ARMOR);   // 第三态
 * </pre>
 * {@code IconUI} 是硬编码 UV 的枚举（{@code ARMOR(34,9,9,9)} / {@code HALF_ARMOR(25,9,9,9)} /
 * {@code EMPTY_ARMOR(16,9,9,9)}，全部指向 {@code textures/gui/icons.png}）；UV 无法指向我们自己的贴图
 * ⇒ 只能整句替换成对自有贴图的 blit。替换后**三态齐活**：满/半用本模组盾形图，第三态用
 * {@code CombatPowerHud.DEFENSE_EMPTY}（与满图标逐像素同形、内芯 {@code #3D3D3D} 深灰）。
 *
 * <h2>透明度</h2>
 * 原 {@code DisplayHelper#renderIcon} 的第一件事就是
 * {@code RenderSystem.setShaderColor(1,1,1, OverlayRenderer.alpha)}（源码同文件 130 行），
 * 我们整句替换后必须自己补上这一步，否则 tooltip 淡入淡出时图标不会跟着变淡。
 *
 * <h2>尺寸</h2>
 * 1.20 的图标位固定 8×8（{@code renderIcon(..., 8, 8, ...)}）、步距 8、行高 10；我们的贴图是 9×9
 * ⇒ 按 {@code width=height=8}、{@code textureWidth=textureHeight=9} 绘制（轻微等比缩小），
 * 与原版此处「9×9 源区画进 8×8」的取法同构。
 *
 * <h2>回落</h2>
 * {@code IconUI} 里还有心形/经验球等其它常量（同一 {@code renderIcon} 也会服务血量行）
 * ⇒ 非护甲三态一律回落 {@code DisplayHelper.renderIcon}，行为与原来逐字节相同。
 *
 * <h2>为什么必须显式写 {@code remap = false}</h2>
 * 本注入器的 {@code method} 与 {@code @At} 目标<strong>两者都是 Jade 自己的成员</strong>
 * （{@code ArmorElement.render} / {@code DisplayHelper.renderIcon}），而 Jade 不做混淆 ⇒
 * MDG Legacy 的 {@code namedToIntermediate.tsrg} 里没有任何一条能命中它们。
 * <p>Mixin AP 的判定见 {@code org.spongepowered.tools.obfuscation.struct.InjectorRemap}
 * 的类头注释（原文）：<em>“we will generally want to raise an error if remap=true but we
 * don't find a mapping for the injector. However it may be the case that remap is true because
 * some of the @At's need remapping. This state struct is used to log the original error, but
 * suppress it if any @At annotations are remapped.”</em> ⇒ 也就是说：
 * <ul>
 *   <li>{@code ArmorValueProviderMixin} 能编译，是因为它的 {@code @At} 指向
 *       {@code LivingEntity#getArmorValue}（原版方法，**能**重映射）⇒ 重映射成功会调
 *       {@code InjectorRemap#notifyRemapped()}，把「找不到 {@code appendTooltip} 映射」这条
 *       挂起错误 {@code clearMessage()} 掉；</li>
 *   <li>本类两个目标**都**不可重映射 ⇒ {@code remappedCount == 0}，编译收尾时
 *       {@code dispatchPendingMessages} 把挂起的错误真正抛出，报
 *       {@code Unable to locate obfuscation mapping for @Redirect target render} 并 **BUILD FAILED**。</li>
 * </ul>
 * 显式 {@code remap = false} 让 AP 一开始就不进入重映射流程（{@code InjectorRemap.shouldRemap()}
 * 直接为 false），既不再索要映射、也不再产生那条 {@code @At} 的 “Unable to locate method mapping”
 * 噪声警告。
 * <p><strong>它不影响两件事</strong>：① 本注入器的字符串本来就**不需要**任何翻译
 * （Jade 成员不混淆；描述符里的 {@code net/minecraft/client/gui/GuiGraphics} 在 1.20 生产命名空间里
 * 也是 Mojang 类名，只有<b>成员名</b>才被 SRG 化）；② 处理器方法体内的原版调用
 * （{@code GuiGraphics#blit}、{@code RenderSystem#*}）由 MDG Legacy 的产物 SRG 重写负责 ——
 * 这一点已用同类既有 mixin 的产物字节码实证（{@code GuiMixin} 的方法体里
 * {@code MobEffectInstance#getEffect} 落成 {@code m_19544_}），与注解上的 {@code remap} 无关。
 */
@Mixin(targets = "snownee.jade.impl.ui.ArmorElement")
public abstract class ArmorElementIconMixin {

    @Redirect(
            // 目标方法：ArmorElement#render(GuiGraphics, float x, float y, float maxX, float maxY)
            method = "render(Lnet/minecraft/client/gui/GuiGraphics;FFFF)V",
            // 重定向对 DisplayHelper.renderIcon 的调用（其形参里含 Jade 的 IconUI，无法绕开类型引用）
            at = @At(value = "INVOKE",
                    target = "Lsnownee/jade/overlay/DisplayHelper;"
                            + "renderIcon(Lnet/minecraft/client/gui/GuiGraphics;FFIILsnownee/jade/overlay/IconUI;)V"),
            // 见类头「为什么必须显式写 remap = false」：两个目标都是 Jade 自身成员，无可重映射项。
            remap = false)
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
