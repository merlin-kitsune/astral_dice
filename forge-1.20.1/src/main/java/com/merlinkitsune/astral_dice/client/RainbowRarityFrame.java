package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.starenginelib.item.AstralRarities;
import com.merlinkitsune.starenginelib.item.Rarity;
import net.minecraft.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 「奇特」稀有度的**彩虹提示框边框**（客户端）。
 *
 * <h2>为什么需要这个类</h2>
 * <p>原版 tooltip 的边框颜色**与稀有度无关**（{@code TooltipRenderUtil} 里写死
 * {@code BORDER_COLOR_TOP/BOTTOM}），而 {@code Rarity#getStyleModifier()} 只作用于物品名那一行
 * —— 所以「边框按档位变色」必须由本类把颜色塞进平台的渲染事件。
 *
 * <h2>为什么不用 Mixin</h2>
 * <p>tooltip **每帧重绘**（{@code AbstractContainerScreen#renderTooltip} 每帧调用），
 * {@code RenderTooltipEvent.Color} 因而**每帧都发**。所以只要每次按当前时间算色，
 * 就成了「颜色沿色环流动」的动画 —— 不需要碰原版的绘制代码。
 *
 * <h2>能改到什么粒度</h2>
 * <p>该事件只给两个颜色（起 / 止）。1.21.1 的边框绘制是「上横线 = 起始色、下横线 = 结束色、
 * 左右竖线 = 起始色→结束色的竖直渐变」（{@code TooltipRenderUtil#renderFrameGradient}），
 * 故这里能得到的是**两色流动渐变**；若要「七色同屏」的彩虹条，得 Mixin 掉那条渐变线的绘制。
 *
 * <h2>26.1.2 为什么没有这个类</h2>
 * <p>该线的 tooltip 边框已改为**九宫格贴图**（{@code TooltipRenderUtil} 用
 * {@code tooltip/background} + {@code tooltip/frame} 精灵，事件是 {@code RenderTooltipEvent.Texture}），
 * **没有颜色接口** ⇒ 彩虹边框必须自带贴图，属另一条实现路径（见 AGENTS 稀有度段的登记）。
 *
 * <p>⚠️ 本类只在客户端加载（{@code value = Dist.CLIENT}），且位于 {@code client/} 包内；
 * 双端加载类不得引用它。
 */
@Mod.EventBusSubscriber(modid = AstralDiceMod.MODID, value = Dist.CLIENT)
public final class RainbowRarityFrame {
    private RainbowRarityFrame() {
    }

    @SubscribeEvent
    public static void onTooltipColor(RenderTooltipEvent.Color event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || stack.getRarity() != AstralRarities.bizarre()) {
            return;
        }
        long now = Util.getMillis();
        event.setBorderStart(Rarity.BIZARRE.rainbowBorderStart(now));
        event.setBorderEnd(Rarity.BIZARRE.rainbowBorderEnd(now));
    }
}
