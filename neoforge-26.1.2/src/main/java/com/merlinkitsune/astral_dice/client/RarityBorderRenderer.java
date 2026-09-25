package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.starenginelib.item.AstralRarities;
import com.merlinkitsune.starenginelib.item.Rarity;
import net.minecraft.util.Util;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

/**
 * 本模组稀有度 → **提示框边框自绘**（客户端）—— 2026-09-25 边框策略修订后的**唯一落点**（26.1.2 线）。
 *
 * <h2>策略（用户裁决，取代早前「文字与边框必须同色」）</h2>
 * <ul>
 *   <li><b>稀有 / 史诗</b>：**完全不干预** —— 原版贴图边框保留，只染物品名 ⇒
 *       与原版稀有/史诗物品观感一致；</li>
 *   <li><b>传奇 / 巅峰</b>：把 {@link Rarity#frameColor(long)}（= 档位色）画成**单色边框**
 *       （覆盖原版贴图边框；本线没有 {@code RenderTooltipEvent.Color} 颜色接口，自绘是唯一手段）；</li>
 *   <li><b>奇特</b>：**顺时针流动彩虹** —— 沿边框周长铺满一整圈色环，并随时间沿顺时针方向平移。</li>
 * </ul>
 *
 * <h2>26.1.2 的几何（与另两线不同）</h2>
 * <p>本线边框 = {@code tooltip/frame} **九宫格贴图**（{@code TooltipRenderUtil#extractTooltipBackground}，
 * 贴图 100×100、nine-slice border=10、四角缺口）。按贴图像素映射解出的边框线：
 * 上横线 {@code y-3, x-2..x+w+1}、右竖线 {@code x+w+2, y-2..y+h+1}、
 * 下横线 {@code y+h+2, x-2..x+w+1}、左竖线 {@code x-3, y-2..y+h+1}
 * （四个外角各缺 1px，与贴图一致）。本类由 {@code mixin/client/TooltipBorderMixin} 在贴图画完**之后**
 * 用逐像素 {@code fill} 覆盖同一圈 ⇒ 非本模组档位零改动。
 *
 * <h2>顺时针的取色式</h2>
 * <p>把边框一整圈展开成长度 {@code P} 的数轴（{@code dist} = 从「上横线左端」起、沿顺时针
 * 上→右→下→左 走过的像素数），每像素色相 = {@code rainbowHue(now) − dist / P}：
 * 时间推进 ⇒ 等色线沿 {@code +dist}（顺时针）平移。饱和度/明度取库的
 * {@link Rarity#rainbowSaturation()} / {@link Rarity#rainbowBrightness()}，与库保持同一组参数。
 *
 * <p>⚠️ 本类只在客户端加载（位于 {@code client/} 包内）；装了第三方 tooltip 模组（自己接管整个提示框）
 * 的环境里本类同样被忽略 —— 那家走本仓内置的 {@code assets/astral_dice/tooltipoverhaul/custom_frames.json}。
 */
public final class RarityBorderRenderer {
    private RarityBorderRenderer() {
    }

    /** 单像素取色：{@code dist} = 距「上横线左端」的顺时针像素数，{@code perimeter} = 一整圈像素数。 */
    private interface PixelColor {
        int colorAt(long dist, long perimeter);
    }

    /**
     * 若该物品的档位需要自定义边框（传奇 / 巅峰 / 奇特），沿提示框边框画一圈；否则什么都不画。
     *
     * @param x      tooltip **内容区**左上 x（原版 {@code extractTooltipBackground} 的同名参数）
     * @param y      tooltip 内容区左上 y
     * @param width  内容区宽
     * @param height 内容区高
     */
    public static void drawIfCustom(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y, int width, int height) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        Rarity tier = AstralRarities.tierOf(stack.getRarity());
        if (tier == null) {
            return;
        }
        long now = Util.getMillis();
        if (tier.isRainbow()) {
            float base = Rarity.rainbowHue(now);
            drawFrame(graphics, x, y, width, height,
                    (dist, perimeter) -> 0xFF000000 | Rarity.hsvToRgb(
                            base - (float) dist / (float) perimeter,
                            Rarity.rainbowSaturation(), Rarity.rainbowBrightness()));
        } else if (tier == Rarity.LEGENDARY || tier == Rarity.PINNACLE) {
            int color = tier.frameColor(now);
            drawFrame(graphics, x, y, width, height, (dist, perimeter) -> color);
        }
    }

    /**
     * 沿本线贴图边框几何画一圈（逐像素，顺时针序；四角缺口与 {@code tooltip/frame} 贴图一致）。
     */
    private static void drawFrame(GuiGraphicsExtractor graphics, int x, int y, int width, int height, PixelColor color) {
        int topLen = width + 4;
        int sideLen = height + 4;
        long perimeter = 2L * (topLen + sideLen);
        long[] dist = {0L};
        for (int px = x - 2; px <= x + width + 1; px++) {                       // 上：左 → 右
            plot(graphics, px, y - 3, color.colorAt(dist[0]++, perimeter));
        }
        for (int py = y - 2; py <= y + height + 1; py++) {                      // 右：上 → 下
            plot(graphics, x + width + 2, py, color.colorAt(dist[0]++, perimeter));
        }
        for (int px = x + width + 1; px >= x - 2; px--) {                       // 下：右 → 左
            plot(graphics, px, y + height + 2, color.colorAt(dist[0]++, perimeter));
        }
        for (int py = y + height + 1; py >= y - 2; py--) {                      // 左：下 → 上
            plot(graphics, x - 3, py, color.colorAt(dist[0]++, perimeter));
        }
    }

    private static void plot(GuiGraphicsExtractor graphics, int px, int py, int argb) {
        graphics.fill(px, py, px + 1, py + 1, argb);
    }
}
