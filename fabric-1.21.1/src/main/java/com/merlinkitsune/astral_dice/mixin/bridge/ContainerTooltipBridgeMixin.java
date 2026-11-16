package com.merlinkitsune.astral_dice.mixin.bridge;

import com.merlinkitsune.astral_dice.client.ModernUITooltipCompat;
import com.merlinkitsune.astral_dice.platform.client.TooltipFrameColors;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 容器界面悬停 tooltip 的**物品栈**来源(2026-11-07 新增)。
 *
 * <h2>补的是哪条断链</h2>
 * <p>本线的 {@code ClientTooltipBridgeMixin} 只在
 * {@code GuiGraphics#renderTooltip(Font, ItemStack, int, int)} 上压栈 —— 那是**唯一**带
 * {@code ItemStack} 的重载。但容器界面走的是另一条路:
 * <pre>
 *   AbstractContainerScreen#renderTooltip(GuiGraphics, int, int)     &lt;- 有 hoveredSlot
 *     +- GuiGraphics#renderTooltip(Font, List, Optional, int, int)   &lt;- **已经没有物品栈**
 * </pre>
 * 两条 fabric 线(1.20.1 / 1.21.1)的该方法是**同一个形状**:第 57 条指令
 * {@code invokevirtual renderTooltip:(Lnet/minecraft/client/gui/Font;Ljava/util/List;Ljava/util/Optional;II)V}
 * (javap 实证),物品栈在调用点就被丢掉了 —— 原版 1.21.1 甚至没有 6 参带栈重载,
 * {@code tooltipStack} 字段是 NeoForge/Forge 的 patch 才有的。
 * 故背包/商店/任意容器里悬停本模组物品时,{@code TooltipFrameColors.stack()} 恒 null,
 * 事件不派发,边框永远是原版紫蓝渐变。本 mixin 在**仍有 hoveredSlot 的那一层**补上压栈/清栈。
 *
 * <h2>为什么是 HEAD/RETURN 而不是包住 5 参重载</h2>
 * <p>5 参重载({@code Font, List, Optional, int, int})也被**非物品**路径复用(纯文本 tooltip),
 * 在那里压栈会拿到别人的空栈甚至残留。挂在容器层能拿到真实的 {@code hoveredSlot.getItem()},
 * 且 RETURN 一定执行(含 {@code hasItem()} 为 false 的早退分支),清理是幂等的。
 */
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerTooltipBridgeMixin {

    @Shadow
    public Slot hoveredSlot;

    @Inject(
            method = "renderTooltip(Lnet/minecraft/client/gui/GuiGraphics;II)V",
            at = @At("HEAD"))
    private void astralDice$pushHoveredStack(GuiGraphics graphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (this.hoveredSlot == null || !this.hoveredSlot.hasItem()) {
            return;
        }
        ItemStack stack = this.hoveredSlot.getItem();
        TooltipFrameColors.pushStack(stack);
        ModernUITooltipCompat.beginRender(stack);
    }

    @Inject(
            method = "renderTooltip(Lnet/minecraft/client/gui/GuiGraphics;II)V",
            at = @At("RETURN"))
    private void astralDice$popHoveredStack(GuiGraphics graphics, int mouseX, int mouseY, CallbackInfo ci) {
        ModernUITooltipCompat.endRender();
        TooltipFrameColors.clear();
    }
}
