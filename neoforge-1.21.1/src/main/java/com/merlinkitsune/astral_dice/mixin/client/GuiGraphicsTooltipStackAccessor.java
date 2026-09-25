package com.merlinkitsune.astral_dice.mixin.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 {@code GuiGraphics#tooltipStack}（当前正在渲染 tooltip 的物品栈）—— 供
 * {@code TooltipBorderMixin} 在 {@code TooltipRenderUtil#renderTooltipBackground} 的 RETURN 处自绘边框时取用。
 *
 * <p>该字段是原版在 {@code renderTooltipInternal} 执行期间设置的「当前物品」（原版自身也读它），
 * 用于判断「这件物品的稀有度是不是本模组需要自定义边框的档位」。
 *
 * <p>⚠️ 仅客户端加载（{@code client/} 包 + mixins.json 的 client 段）；1.21.1 NeoForge 编译/运行同为
 * Mojmap ⇒ 字段名 {@code tooltipStack} 无需 refmap。
 */
@Mixin(GuiGraphics.class)
public interface GuiGraphicsTooltipStackAccessor {
    @Accessor("tooltipStack")
    ItemStack astral_dice$getTooltipStack();
}
