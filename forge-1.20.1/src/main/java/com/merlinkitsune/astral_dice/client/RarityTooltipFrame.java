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
 * 本模组**稀有度 → 提示框边框染色**（客户端）。
 *
 * <h2>规则：文字与边框严格同色（唯一例外 = 奇特）</h2>
 * <p>物品名那一行的颜色由原版链路（{@code Rarity#getStyleModifier()}）自动套用，而**原版 tooltip 的边框颜色
 * 与稀有度无关**（{@code TooltipRenderUtil} 里写死 {@code BORDER_COLOR_TOP/BOTTOM}）⇒ 边框必须由本类把
 * {@link Rarity#frameColor(long)} 写进平台事件。本类对**全部 5 档**生效（不只是奇特）。
 *
 * <h2>为什么不用 Mixin</h2>
 * <p>tooltip **每帧重绘**（{@code AbstractContainerScreen#renderTooltip} 每帧调用），
 * {@code RenderTooltipEvent.Color} 因而**每帧都发** ⇒ 奇特按当前时刻取色即得「流动彩虹」，不需要碰原版绘制代码。
 * 该事件只给两个颜色（起/止）：原版绘制是「上横线 = 起始色、下横线 = 结束色、左右竖线 = 两者竖直渐变」
 * （{@code TooltipRenderUtil#renderFrameGradient}）⇒ 非彩虹档两色相同，整圈边框就是那一个颜色。
 *
 * <h2>只管本模组档位</h2>
 * <p>{@link AstralRarities#tierOf(net.minecraft.world.item.Rarity)} 返回 {@code null} 时（= 原版物品、
 * 原版自带四档、其它模组的物品）**一律不动**，保持原版边框。
 *
 * <h2>装了第三方 tooltip 模组会怎样</h2>
 * <p>它们常常**自己画**整个提示框（本仓实测：整合包里的 Tooltip Overhaul 就完全不走
 * {@code TooltipRenderUtil}）⇒ 本事件被忽略，而且自定义稀有度会落进那家的「未知档位」兜底色（实测 = 金色）。
 * 要让边框在那里也同色，得对那家做数据对接：本仓已内置
 * {@code assets/astral_dice/tooltipoverhaul/custom_frames.json}（按 {@code Rarity#name()} 匹配、直接给颜色）。
 *
 * <h2>26.1.2 为什么没有这个类</h2>
 * <p>该线 tooltip 边框已改为**九宫格贴图**（{@code TooltipRenderUtil} 用 {@code tooltip/background}
 * + {@code tooltip/frame} 精灵；事件是 {@code RenderTooltipEvent.Texture#setTexture}）⇒ **没有颜色接口**，
 * 按档位变色必须自带贴图帧（见 AGENTS 稀有度段的登记）。
 *
 * <p>⚠️ 本类只在客户端加载（{@code value = Dist.CLIENT}）且位于 {@code client/} 包内；
 * 双端加载类不得引用它。
 */
@Mod.EventBusSubscriber(modid = AstralDiceMod.MODID, value = Dist.CLIENT)
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
        if (tier == null) {
            return;
        }
        int color = tier.frameColor(Util.getMillis());
        event.setBorderStart(color);
        event.setBorderEnd(color);
    }
}
