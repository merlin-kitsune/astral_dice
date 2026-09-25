// NeoForge 21.1.x / MC 1.21.1
package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.starenginelib.item.AstralRarities;
import com.merlinkitsune.starenginelib.item.Rarity;
import net.minecraft.Util;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;

/**
 * 本模组**稀有度 → 提示框边框染色**（客户端）。
 *
 * <h2>规则（2026-09-25 二次裁决）</h2>
 * <ul>
 *   <li><b>稀有 / 史诗</b>：完全随原版 —— 不干预边框（原版紫蓝渐变），物品名由本档 Style 上色，
 *       与原版 RARE / EPIC 物品观感一致；</li>
 *   <li><b>传奇 / 巅峰</b>：边框 = {@link Rarity#frameColor(long)}（= {@link Rarity#rgb()}，单色）；</li>
 *   <li><b>奇特</b>：边框 = {@link Rarity#rainbowBorderStart(long)} → {@link Rarity#rainbowBorderEnd(long)}
 *       的两色流动渐变。</li>
 * </ul>
 *
 * <h2>为什么不用 Mixin</h2>
 * <p>tooltip **每帧重绘**（{@code AbstractContainerScreen#renderTooltip} 每帧调用），
 * {@code RenderTooltipEvent.Color} 因而**每帧都发** ⇒ 按当前时刻取色即得流动动画，无需碰原版绘制代码。
 * 该事件只给两个颜色（起/止）：原版绘制是「上横线 = 起始色、下横线 = 结束色、左右竖线 = 两者竖直渐变」
 * （{@code TooltipRenderUtil#renderFrameGradient}）⇒ 非彩虹档两色相同 = 整圈单色；彩虹档两色不同 = 竖直渐变。
 *
 * <h2>只管本模组档位</h2>
 * <p>{@link AstralRarities#tierOf(net.minecraft.world.item.Rarity)} 返回 {@code null}（原版物品、其它模组物品）
 * 时一律不动；返回本模组稀有/史诗时也不动（随原版）。
 *
 * <p>⚠️ 本类只在客户端加载（{@code value = Dist.CLIENT}）且位于 {@code client/} 包内；双端加载类不得引用它。
 */
@EventBusSubscriber(modid = AstralDiceMod.MODID, value = Dist.CLIENT)
public final class RarityTooltipFrame {
    private RarityTooltipFrame() {
    }

    @SubscribeEvent
    public static void onTooltipColor(RenderTooltipEvent.Color event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) {
            return;
        }
        Rarity tier = AstralRarities.tierOf(stack.getRarity());
        if (tier == null || tier == Rarity.RARE || tier == Rarity.EPIC) {
            return; // 稀有/史诗随原版，原版/其它模组档位也不动
        }
        long now = Util.getMillis();
        if (tier.isRainbow()) {
            event.setBorderStart(tier.rainbowBorderStart(now));
            event.setBorderEnd(tier.rainbowBorderEnd(now));
        } else {
            int color = tier.frameColor(now);
            event.setBorderStart(color);
            event.setBorderEnd(color);
        }
    }
}
