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
 * <p>⚠️ 仅客户端加载（{@code client/} 包 + mixins.json 的 client 段）；Forge 1.20.1 走 refmap
 * 把 Mojmap 字段名 {@code tooltipStack} 映射为生产 SRG 名（由 {@code mixin { config(...) }} 的 AP 生成）。
 */
@Mixin(GuiGraphics.class)
public interface GuiGraphicsTooltipStackAccessor {
    /**
     * {@code remap = false}：{@code tooltipStack} 是 Forge 通过 Mixin 给原版 {@code GuiGraphics} 注入的字段
     * （1.20.1 原始 SRG jar 里没有它，标准 Mojmap→SRG 映射表也不含它）⇒ refmap 无法映射、编译期会报
     * 「Unable to locate obfuscation mapping」警告，但运行时该字段名**保持原样 {@code tooltipStack}**，
     * 故这里显式声明不参与混淆，避免 refmap 缺条目导致运行时按原名反射失败。
     */
    @Accessor(value = "tooltipStack", remap = false)
    ItemStack astral_dice$getTooltipStack();
}
