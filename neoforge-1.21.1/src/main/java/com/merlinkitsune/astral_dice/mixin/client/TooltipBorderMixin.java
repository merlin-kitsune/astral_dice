package com.merlinkitsune.astral_dice.mixin.client;

import com.merlinkitsune.astral_dice.client.RarityBorderRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 奇特（彩虹）/ 传奇 / 巅峰的**提示框边框自绘**注入点（客户端）。
 *
 * <p>⚠️ 为什么是 {@code @Inject} 到 {@code TooltipRenderUtil#renderTooltipBackground} 的 RETURN，
 * 而不是 {@code @WrapOperation} 包 {@code GuiGraphics#renderTooltipInternal} 里的调用：
 * 生产环境（1.21.1 NeoForge Mojmap / 1.20.1 Forge SRG）里，那段调用被编译进
 * {@code drawManaged(() -> TooltipRenderUtil.renderTooltipBackground(...))} 的 **lambda 合成方法**，
 * {@code @WrapOperation} 按 {@code renderTooltipInternal} + target 描述符去扫会
 * **"Scanned 0 target(s)"**（lambda 内是 {@code INVOKESTATIC} 且是 6 参重载，不是源码里看到的 10 参）。
 *
 * <p>改在 {@code renderTooltipBackground}（6 参，原版稳定签名）执行完**之后**叠加自绘：
 * 该方法画完背景与边框才返回 ⇒ 此时同 z 层（400）后写者胜，非本模组档位零改动。
 * 几何（x/y/width/height/z）直接来自方法参数（无需 {@code @Local}、不依赖 LocalVariableTable），
 * 物品栈经 {@link GuiGraphicsTooltipStackAccessor} 取。
 *
 * <p>⚠️ 本类只在客户端加载（{@code mixin/client/} 包 + mixins.json 的 client 段）。
 */
@Mixin(TooltipRenderUtil.class)
public abstract class TooltipBorderMixin {
    @Inject(method = "renderTooltipBackground(Lnet/minecraft/client/gui/GuiGraphics;IIIII)V", at = @At("RETURN"))
    private static void astral_dice$drawCustomFrame(GuiGraphics guiGraphics, int x, int y, int width, int height, int z,
                                                    CallbackInfo ci) {
        ItemStack stack = ((GuiGraphicsTooltipStackAccessor) (Object) guiGraphics).astral_dice$getTooltipStack();
        RarityBorderRenderer.drawIfCustom(guiGraphics, stack, x, y, width, height, z);
    }
}
