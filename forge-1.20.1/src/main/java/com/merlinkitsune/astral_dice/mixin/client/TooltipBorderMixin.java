package com.merlinkitsune.astral_dice.mixin.client;

import com.merlinkitsune.astral_dice.client.RarityBorderRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.world.item.ItemStack;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 奇特（彩虹）/ 传奇 / 巅峰的**提示框边框自绘**注入点（客户端）。
 *
 * <p>包住 {@code GuiGraphics#renderTooltipInternal} 里对
 * {@code TooltipRenderUtil#renderTooltipBackground(10 参)} 的调用：先照常执行原版
 * （背景 + 边框照画，{@code RenderTooltipEvent.Color} 语义不变），随后交
 * {@link RarityBorderRenderer#drawIfCustom} 按档位叠加 —— 非本模组档位零改动，
 * 本模组「传奇/巅峰/奇特」的三种边框（金 / 亮红 / 顺时针流动彩虹）在同层覆盖。
 *
 * <p>为什么包这个调用而不是监听 {@code RenderTooltipEvent.Color}：该事件只有「顶/底」两色，
 * 原版绘制是竖直渐变 —— **画不出沿边框顺时针环绕**；包调用点则能拿到精确几何（x/y/w/h/z），
 * 并保证自绘像素与原版边框同层（同 z 后写者胜）。
 *
 * <p>⚠️ 本类只在客户端加载（{@code mixin/client/} 包 + mixins.json 的 client 段）；
 * {@code tooltipStack} 在 {@code renderTooltipInternal} 执行期间是当前物品（原版自身就读它）。
 */
@Mixin(GuiGraphics.class)
public abstract class TooltipBorderMixin {
    @Shadow
    private ItemStack tooltipStack;

    @WrapOperation(method = "renderTooltipInternal", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/inventory/tooltip/TooltipRenderUtil;renderTooltipBackground(Lnet/minecraft/client/gui/GuiGraphics;IIIIIIIII)V"))
    private void astral_dice$drawCustomFrame(GuiGraphics graphics, int x, int y, int width, int height, int z,
                                             int backgroundTop, int backgroundBottom, int borderTop, int borderBottom,
                                             Operation<Void> original) {
        original.call(graphics, x, y, width, height, z, backgroundTop, backgroundBottom, borderTop, borderBottom);
        RarityBorderRenderer.drawIfCustom(graphics, this.tooltipStack, x, y, width, height, z);
    }
}
