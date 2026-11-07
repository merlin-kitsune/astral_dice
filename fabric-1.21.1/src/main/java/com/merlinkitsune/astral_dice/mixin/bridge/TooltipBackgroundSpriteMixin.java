package com.merlinkitsune.astral_dice.mixin.bridge;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.client.RarityTooltipFrame;
import com.merlinkitsune.astral_dice.platform.client.TooltipFrameColors;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * tooltip 背景换成**九宫格贴图**（与 26.1.2 线同款观感）—— 本模组档位专属。
 *
 * <h2>为什么不是「改颜色」而是「换整套背景」</h2>
 * <p>1.21.1 原版 {@code TooltipRenderUtil#renderTooltipBackground} 是用 {@code fill} /
 * {@code fillGradient} 逐条画的矩形 + 边框线（内 3px、外再 1px 线）⇒ 只能**染成单色**，
 * 观感是「轴对齐直角双线框」。26.1.2 线改用官方 GUI sprite 通道：一对 100x100 九宫格贴图
 * （{@code _background} / {@code _frame}，{@code gui.scaling.type=nine_slice, border=10}），
 * 圆角 + 单像素描边 + 颜色烘焙在贴图里。1.21.1 **同样支持**九宫格 GUI sprite
 * （{@code GuiSpriteScaling.NineSlice} + {@link GuiGraphics#blitSprite(ResourceLocation,int,int,int,int,int)}）
 * ⇒ 本 mixin 在 {@code HEAD} 处整段接管，贴图与 26.1.2 逐字节同源。
 *
 * <h2>几何（与 26.1.2 逐像素对齐）</h2>
 * <p>26.1.2 的 {@code TooltipRenderUtil.extractTooltipBackground} 取
 * {@code x0 = x - PADDING(3) - MARGIN(9)}、{@code paddedWidth = w + 2*(3+9)}；贴图内圈描边本身占
 * 10px 边距 ⇒ 视觉上边框刚好落在文字外侧。这里用同一组常量，保证两线相对文字的位置一致。
 *
 * <h2>不越界</h2>
 * <p>{@link RarityTooltipFrame#styleOf} 只对本模组的史诗 / 传奇 / 巅峰 / 奇特返回非 null；
 * 原版与其它模组物品（含稀有档）返回 null ⇒ 本方法直接返回、原版绘制照旧，画质零变化。
 * 颜色通路（{@code TooltipRenderUtilColorMixin}）因此只在「无贴图」时兜底，二者不冲突。
 */
@Mixin(TooltipRenderUtil.class)
public abstract class TooltipBackgroundSpriteMixin {

    /** 原版 1.21.1 的文字内边距（{@code TooltipRenderUtil.PADDING}）。 */
    private static final int PADDING = 3;
    /** 26.1.2 贴图外圈的九宫格边距（sprite 内 10px 描边的可视部分）。 */
    private static final int MARGIN = 9;

    @Inject(
            method = "renderTooltipBackground(Lnet/minecraft/client/gui/GuiGraphics;IIIII)V",
            at = @At("HEAD"),
            cancellable = true)
    private static void astralDice$spriteBackground(GuiGraphics guiGraphics, int x, int y, int width, int height,
                                                    int z, CallbackInfo ci) {
        String style = RarityTooltipFrame.styleOf(TooltipFrameColors.stack(), Util.getMillis());
        if (style == null) {
            return;
        }
        int inset = PADDING + MARGIN;
        int left = x - inset;
        int top = y - inset;
        int w = width + inset * 2;
        int h = height + inset * 2;
        guiGraphics.blitSprite(sprite(style + "_background"), left, top, z, w, h);
        guiGraphics.blitSprite(sprite(style + "_frame"), left, top, z, w, h);
        ci.cancel();
    }

    private static ResourceLocation sprite(String path) {
        return ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "tooltip/" + path);
    }
}
