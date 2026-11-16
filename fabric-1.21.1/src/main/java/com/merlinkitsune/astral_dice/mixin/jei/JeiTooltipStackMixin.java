package com.merlinkitsune.astral_dice.mixin.jei;

import java.util.List;

import com.merlinkitsune.astral_dice.client.ModernUITooltipCompat;
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
 * JEI(fabric 版)与「按稀有度给提示框上边框色」之间的桥。
 *
 * <h2>为什么需要这个桥(2026-11-07 源码级取证)</h2>
 * <p>JEI 的 fabric 实现 {@code mezz.jei.fabric.platform.RenderHelper#renderTooltip(...)}
 * 只做两步:把 {@code Either<FormattedText, TooltipComponent>} 转成
 * {@code List<ClientTooltipComponent>},然后直接调
 * {@code GuiGraphics#renderTooltipInternal(Font, List, int, int, ClientTooltipPositioner)}
 * (javap 实证:第 20 条指令 {@code invokevirtual class_332.method_51435},
 * 与 1.21.1 {@code mappings.tiny} 中 {@code method_51435 = renderTooltipInternal} 一致)。
 * <b>物品栈在调用点就被丢掉了</b> —— fabric 原版没有 NeoForge 那种
 * 「{@code renderComponentTooltipFromElements(Font, List, int, int, ItemStack)}」带栈入口
 * (NeoForge 的 JEI 正是调那条,所以 P0 基准两线(neo21/forge)的 JEI 里染色天然生效)。
 *
 * <p>本桥因此在 JEI 的入口处自己压栈:HEAD 写 {@link TooltipFrameColors#pushStack(ItemStack)},
 * RETURN 清掉。这样下游无论走哪条绘制路径,都能读到「当前 tooltip 是哪个物品」。
 *
 * <h2>为什么要同时驱动 {@link ModernUITooltipCompat}(2026-11-07 实测)</h2>
 * <p>装了 Modern UI 并开启「现代提示框」时,它用 {@code mixins.modernui-fabric.json} 的
 * {@code mixinPriority = 1} 在 {@code GuiGraphics#renderTooltipInternal} 的 <b>HEAD</b>
 * {@code CallbackInfo.cancel()},改为自己绘制({@code UIManager#drawExtTooltip})。
 * 取消会让同一注入点后面的 handler 全部不执行 —— 本模组混入该方法的 handler 优先级是默认 1000,
 * 于是 {@code RenderTooltipEvent.Color} 派发与九宫格描边**一次都不会跑**
 * (实测整场会话 0 次),JEI 面板里的 tooltip 就退回 Modern UI 的默认描边色。
 * 故本桥在这里直接调 {@link ModernUITooltipCompat#beginRender(ItemStack)} /
 * {@link ModernUITooltipCompat#endRender()} —— 它写的是 Modern UI 自己的
 * {@code TooltipRenderer.sStrokeColor} 并临时关掉 {@code sAdaptiveColors},
 * 属于「在它的绘制之前改它的颜色来源」,与 P0 基准两线事件层的时序语义等价。
 *
 * <p>全部注入点都是 {@code require = 0}:JEI 缺席时本 mixin 根本不加载
 * ({@code astral_dice.jei.mixins.json} 是 {@code required: false}),
 * JEI 版本漂移导致签名不匹配时也只降级为「JEI 里不染色」,不让客户端启动失败。
 */
@Pseudo
@Mixin(targets = "mezz.jei.fabric.platform.RenderHelper")
abstract class JeiTooltipStackMixin {

    @Inject(
            method = "renderTooltip(Lnet/minecraft/client/gui/GuiGraphics;Ljava/util/List;IILnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("HEAD"), require = 0)
    private void astralDice$pushStack(GuiGraphics guiGraphics, List<?> tooltip, int x, int y, Font font,
                                      ItemStack stack, CallbackInfo ci) {
        TooltipFrameColors.pushStack(stack);
        ModernUITooltipCompat.beginRender(stack);
        ModernUITooltipCompat.enterTooltipStack(guiGraphics, stack);
    }

    @Inject(
            method = "renderTooltip(Lnet/minecraft/client/gui/GuiGraphics;Ljava/util/List;IILnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("RETURN"), require = 0)
    private void astralDice$popStack(GuiGraphics guiGraphics, List<?> tooltip, int x, int y, Font font,
                                     ItemStack stack, CallbackInfo ci) {
        ModernUITooltipCompat.exitTooltipStack(guiGraphics);
        ModernUITooltipCompat.endRender();
        TooltipFrameColors.clear();
    }

    @Inject(
            method = "renderTooltip(Lnet/minecraft/client/gui/GuiGraphics;Ljava/util/List;IILnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;)V",
            at = @At("HEAD"), require = 0)
    private void astralDice$pushStackPositioned(GuiGraphics guiGraphics, List<?> tooltip, int x, int y, Font font,
                                                ItemStack stack, ClientTooltipPositioner positioner, CallbackInfo ci) {
        TooltipFrameColors.pushStack(stack);
        ModernUITooltipCompat.beginRender(stack);
        ModernUITooltipCompat.enterTooltipStack(guiGraphics, stack);
    }

    @Inject(
            method = "renderTooltip(Lnet/minecraft/client/gui/GuiGraphics;Ljava/util/List;IILnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;)V",
            at = @At("RETURN"), require = 0)
    private void astralDice$popStackPositioned(GuiGraphics guiGraphics, List<?> tooltip, int x, int y, Font font,
                                               ItemStack stack, ClientTooltipPositioner positioner, CallbackInfo ci) {
        ModernUITooltipCompat.exitTooltipStack(guiGraphics);
        ModernUITooltipCompat.endRender();
        TooltipFrameColors.clear();
    }
}
