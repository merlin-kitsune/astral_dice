package com.merlinkitsune.astral_dice.mixin.bridge;

import com.merlinkitsune.astral_dice.platform.client.TooltipFrameColors;

import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * {@code TooltipRenderUtil} 侧的 tooltip 边框染色 —— 把三个**默认颜色常量**替换为事件解析出的颜色。
 *
 * <p>1.20.1 的原版把 tooltip 背景与边框绘制收进了
 * {@code TooltipRenderUtil#renderTooltipBackground},颜色是该类的三个 private static final
 * 常量(javap 实证常量池 ldc 值):
 * <pre>
 *   BACKGROUND_COLOR      = 0xF0100010   (在 renderTooltipBackground 内被 ldc 4 次)
 *   BORDER_COLOR_TOP      = 0x505000FF   (作为 renderFrameGradient 的第 6 参)
 *   BORDER_COLOR_BOTTOM   = 0x5028007F   (作为 renderFrameGradient 的第 7 参)
 * </pre>
 * ⇒ 用 {@code @ModifyConstant} 逐个改写,比 {@code @WrapOperation} 包裹
 * {@code renderHorizontalLine / renderRectangle / renderVerticalLine / renderFrameGradient}
 * 四组调用**更少注入点、也更不容易被原版小版本改动打断**(常量值是渲染契约的一部分)。
 *
 * <p>若 {@link TooltipFrameColors} 未写入颜色(非本模组的物品、或订阅者未改色),
 * 三个方法各自返回传入的 fallback ⇒ **完全等价于原版行为**。
 *
 * <p>⭐ 每帧重绘 ⇒ 本 mixin 每帧执行一次;奇特档的「流动渐变」正是靠这个
 * 逐帧取色实现的({@code RarityTooltipFrame} 用 {@code Util.getMillis()} 算相位),
 * 不存在需要额外缓存的动画状态。
 */
@Mixin(TooltipRenderUtil.class)
public abstract class TooltipRenderUtilColorMixin {

    @ModifyConstant(
            method = "renderTooltipBackground",
            constant = @Constant(intValue = 0xF0100010))
    private static int astralDice$backgroundColor(int original) {
        return TooltipFrameColors.background(original);
    }

    @ModifyConstant(
            method = "renderTooltipBackground",
            constant = @Constant(intValue = 0x505000FF))
    private static int astralDice$borderStartColor(int original) {
        return TooltipFrameColors.borderStart(original);
    }

    @ModifyConstant(
            method = "renderTooltipBackground",
            constant = @Constant(intValue = 0x5028007F))
    private static int astralDice$borderEndColor(int original) {
        return TooltipFrameColors.borderEnd(original);
    }
}
