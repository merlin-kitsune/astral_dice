package com.merlinkitsune.astral_dice.mixin.client;

import com.merlinkitsune.astral_dice.client.RarityBorderRenderer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.client.gui.Font;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/**
 * 奇特（彩虹）/ 传奇 / 巅峰的**提示框边框自绘**注入点（客户端 · 26.1.2 线）。
 *
 * <p>包住 {@code GuiGraphicsExtractor#tooltip(7 参)} 里对
 * {@code TooltipRenderUtil#extractTooltipBackground} 的调用：先照常执行原版（背景 + 框贴图照画），
 * 随后交 {@link RarityBorderRenderer#drawIfCustom} 按档位用逐像素 {@code fill} 叠加 ——
 * 非本模组档位零改动，本模组「传奇/巅峰/奇特」的三种边框（金 / 亮红 / 顺时针流动彩虹）覆盖贴图线。
 *
 * <p>本线没有 {@code RenderTooltipEvent.Color}（边框已贴图化、无颜色接口），这是**唯一**的按档位
 * 改边框手段；几何（x/y/w/h）直接来自被包调用的实参，物品则用 {@code @Local(argsOnly)} 从
 * {@code tooltip(...)} 的方法参数取（字段 {@code tooltipStack} 在 deferred 渲染下不可靠）。
 *
 * <p>⚠️ 本类只在客户端加载（{@code mixin/client/} 包 + mixins.json 的 client 段）。
 */
@Mixin(GuiGraphicsExtractor.class)
public abstract class TooltipBorderMixin {

    @WrapOperation(method = "tooltip(Lnet/minecraft/client/gui/Font;Ljava/util/List;IILnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;Lnet/minecraft/resources/Identifier;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/inventory/tooltip/TooltipRenderUtil;extractTooltipBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIIILnet/minecraft/resources/Identifier;)V"))
    private void astral_dice$drawCustomFrame(GuiGraphicsExtractor graphics, int x, int y, int width, int height, Identifier style,
                                             Operation<Void> original,
                                             @Local(argsOnly = true) ItemStack tooltipStack) {
        original.call(graphics, x, y, width, height, style);
        RarityBorderRenderer.drawIfCustom(graphics, tooltipStack, x, y, width, height);
    }
}
