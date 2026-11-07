package com.merlinkitsune.astral_dice.mixin.jei;

import java.util.List;

import com.merlinkitsune.astral_dice.platform.client.TooltipFrameColors;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * JEI 侧 tooltip 的**物品栈桥**(客户端,可选依赖)。
 *
 * <h2>为什么必须单独给 JEI 补一个桥(2026-11-07 实测根因)</h2>
 * <p>本线的染色链路是: {@code GuiGraphics#renderTooltip(Font, ItemStack, int, int)} 的 HEAD
 * 把当前物品栈压进 {@link TooltipFrameColors}, 再由 {@code renderTooltipInternal} 的 HEAD 派发
 * {@code RenderTooltipEvent.Color} 并缓存颜色, 最后 {@code TooltipRenderUtil} 侧换成九宫格贴图。
 * <p>而 JEI 的物品提示框**不走那条带 ItemStack 的原版入口**: javap 实证
 * {@code mezz/jei/fabric/platform/RenderHelper#renderTooltip(GuiGraphics, List, int, int, Font, ItemStack)}
 * 先 {@code createClientTooltipComponents(...)} 再直接调
 * {@code GuiGraphics#renderTooltipInternal(Font, List, int, int, TooltipPositioner)}
 * (生产名 {@code class_332.method_51435})。这条路径上**没有任何地方带物品栈**
 * ⇒ 本模组的 push 不发生 ⇒ 边框退回原版紫蓝渐变(JEI 里「不染色」的唯一原因)。
 *
 * <h2>做法</h2>
 * <p>在 JEI 那个**自己带 ItemStack 参数**的方法上, 用 HEAD 压栈 / RETURN 清栈,
 * 让紧随其后的 {@code renderTooltipInternal} 照常读到物品栈。JEI 是可选依赖:
 * 本 mixin 放在独立配置 {@code astral_dice.jei.mixins.json}({@code required:false})里,
 * 并用 {@link Pseudo} + {@code require = 0} 保证 JEI 缺席时静默跳过、不崩启动。
 *
 * <p>⚠️ 不要试图在 {@code renderTooltipInternal} 里「按悬停槽位找物品」替代本桥: JEI 的物品列表
 * 不属于任何 {@code AbstractContainerScreen} 的槽位, 拿不到栈。
 */
@Pseudo
@Mixin(targets = "mezz.jei.fabric.platform.RenderHelper")
public abstract class JeiTooltipStackMixin {

    /** JEI 6 参入口(无位置器)。 */
    @Inject(
            method = "renderTooltip(Lnet/minecraft/client/gui/GuiGraphics;Ljava/util/List;IILnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("HEAD"), require = 0)
    private void astralDice$pushStack(GuiGraphics guiGraphics, List<?> tooltip, int x, int y, Font font,
                                      ItemStack stack, CallbackInfo ci) {
        TooltipFrameColors.pushStack(stack);
    }

    @Inject(
            method = "renderTooltip(Lnet/minecraft/client/gui/GuiGraphics;Ljava/util/List;IILnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("RETURN"), require = 0)
    private void astralDice$popStack(GuiGraphics guiGraphics, List<?> tooltip, int x, int y, Font font,
                                     ItemStack stack, CallbackInfo ci) {
        TooltipFrameColors.clear();
    }

    /** JEI 7 参入口(带位置器)。 */
    @Inject(
            method = "renderTooltip(Lnet/minecraft/client/gui/GuiGraphics;Ljava/util/List;IILnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;)V",
            at = @At("HEAD"), require = 0)
    private void astralDice$pushStackPositioned(GuiGraphics guiGraphics, List<?> tooltip, int x, int y, Font font,
                                                ItemStack stack, ClientTooltipPositioner positioner, CallbackInfo ci) {
        TooltipFrameColors.pushStack(stack);
    }

    @Inject(
            method = "renderTooltip(Lnet/minecraft/client/gui/GuiGraphics;Ljava/util/List;IILnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;)V",
            at = @At("RETURN"), require = 0)
    private void astralDice$popStackPositioned(GuiGraphics guiGraphics, List<?> tooltip, int x, int y, Font font,
                                               ItemStack stack, ClientTooltipPositioner positioner, CallbackInfo ci) {
        TooltipFrameColors.clear();
    }
}
