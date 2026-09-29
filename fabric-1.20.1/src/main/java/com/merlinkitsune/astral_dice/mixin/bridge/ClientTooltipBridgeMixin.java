package com.merlinkitsune.astral_dice.mixin.bridge;

import java.util.List;

import com.merlinkitsune.astral_dice.platform.client.TooltipFrameColors;
import com.merlinkitsune.astral_dice.platform.client.event.RenderTooltipEvent;
import com.merlinkitsune.astral_dice.platform.event.LoaderBus;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code GuiGraphics} 侧的 tooltip 桥 —— 为 {@link RenderTooltipEvent.Color} 补齐派发源。
 *
 * <p>2026-09-29 补:此前该事件无任何派发源 ⇒ {@code client/RarityTooltipFrame}
 * (传奇/巅峰档的单色边框 + 奇特档的彩虹流动渐变)**永不生效**,所有本模组物品的
 * 提示框都退回原版紫蓝渐变。
 *
 * <h2>两步接线</h2>
 * <ol>
 *   <li>{@code renderTooltip(Font, ItemStack, int, int)} —— 原版**唯一**带 ItemStack 的入口,
 *       在此压入「当前物品栈」(退出时清理);</li>
 *   <li>{@code renderTooltipInternal(...)} —— 位置/字体/组件都在这里,在此派发
 *       {@code RenderTooltipEvent.Color} 并把结果颜色写入 {@link TooltipFrameColors},
 *       供 {@code TooltipRenderUtil} 侧读取。</li>
 * </ol>
 *
 * <p>⚠️ 颜色常量取自 {@code TooltipRenderUtil} 的 private static final 字段
 * (javap 实证:`BACKGROUND_COLOR=0xF0100010` / `BORDER_COLOR_TOP=0x505000FF` /
 * `BORDER_COLOR_BOTTOM=0x5028007F`)。这里只把它们作为**事件默认值**传入,
 * 真正的替换发生在 {@code TooltipRenderUtilColorMixin}(同一个值的权威来源)。
 */
@Mixin(GuiGraphics.class)
public abstract class ClientTooltipBridgeMixin {

    /** 原版 tooltip 背景色(实证自 {@code TooltipRenderUtil.BACKGROUND_COLOR})。 */
    private static final int ASTRAL_DICE$DEFAULT_BACKGROUND = 0xF0100010;
    /** 原版边框起始色(实证自 {@code TooltipRenderUtil.BORDER_COLOR_TOP})。 */
    private static final int ASTRAL_DICE$DEFAULT_BORDER_TOP = 0x505000FF;
    /** 原版边框结束色(实证自 {@code TooltipRenderUtil.BORDER_COLOR_BOTTOM})。 */
    private static final int ASTRAL_DICE$DEFAULT_BORDER_BOTTOM = 0x5028007F;

    @Inject(
            method = "renderTooltip(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;II)V",
            at = @At("HEAD"))
    private void astralDice$pushTooltipStack(Font font, ItemStack stack, int x, int y, CallbackInfo ci) {
        TooltipFrameColors.pushStack(stack);
    }

    @Inject(
            method = "renderTooltip(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;II)V",
            at = @At("RETURN"))
    private void astralDice$popTooltipStack(Font font, ItemStack stack, int x, int y, CallbackInfo ci) {
        TooltipFrameColors.clear();
    }

    @Inject(method = "renderTooltipInternal", at = @At("HEAD"))
    private void astralDice$dispatchTooltipColor(Font font, List<ClientTooltipComponent> components,
                                                 int x, int y, ClientTooltipPositioner positioner,
                                                 CallbackInfo ci) {
        ItemStack stack = TooltipFrameColors.stack();
        if (stack == null || stack.isEmpty()) {
            return;
        }
        RenderTooltipEvent.Color event = new RenderTooltipEvent.Color(
                stack, (GuiGraphics) (Object) this, x, y, font,
                ASTRAL_DICE$DEFAULT_BACKGROUND,
                ASTRAL_DICE$DEFAULT_BORDER_TOP,
                ASTRAL_DICE$DEFAULT_BORDER_BOTTOM,
                components);
        LoaderBus.INSTANCE.post(event);
        TooltipFrameColors.setColors(
                event.getBackgroundStart(), event.getBorderStart(), event.getBorderEnd());
    }
}
