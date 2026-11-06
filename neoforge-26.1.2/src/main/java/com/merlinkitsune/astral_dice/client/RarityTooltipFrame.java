package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.starengine.item.AstralRarities;
import com.merlinkitsune.starengine.item.Rarity;

import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;

/**
 * 本模组**稀有度到提示框边框染色**(客户端,26.1.2 线)。
 *
 * <h2>为什么这一线与 1.21.1 的写法不同</h2>
 * <p>26.1.2 的原版把 tooltip 背景/边框换成了**九宫格 sprite**:
 * {@code TooltipRenderUtil.extractTooltipBackground} 只做两次
 * {@code blitSprite(minecraft:tooltip/background)} 与 {@code blitSprite(minecraft:tooltip/frame)},
 * **没有任何颜色参数**;颜色被烘焙进贴图({@code frame.png} 的像素就是
 * {@code (80,0,255,80)} / {@code (40,0,127,80)} 两档紫),因此既不能给 sprite 做乘法 tint
 * (紫底乘任何色只会更暗),NeoForge 26.1.2 也删掉了 {@code RenderTooltipEvent.Color}。
 *
 * <h2>做法:换一整对贴图,几何仍用原版</h2>
 * <p>{@link RenderTooltipEvent.Texture#setTexture(Identifier)} 的语义是:给定 id 会被加
 * {@code tooltip/} 前缀、{@code _background} / {@code _frame} 后缀,再交给原版按九宫格绘制。
 * 所以本模组自带同形状的贴图即可,**圆角、九宫格拉伸、边距全部沿用原版**,不自己画填充矩形
 * (2026-11-07 之前的自绘方案画出来是与 sprite 圆角不吻合的直角方框,已废弃)。
 * <p>贴图位置: {@code assets/astral_dice/textures/gui/sprites/tooltip/} 下的
 * {@code epic|legendary|pinnacle|rainbow00..09}_frame.png} 与同名 {@code _background.png}。
 * 生成口径:读原版 {@code frame.png},**只把非透明像素的 RGB 换成档位色、alpha 提到 255**
 * (对齐 1.20.1 / 1.21.1 的不透明框观感),形状与圆角 1:1 保留;背景直接复制原版
 * {@code background.png}。GUI sprite 图集({@code atlases/gui.json} 的 directory 源)按命名空间扫目录,
 * 本模组自己的 {@code assets/astral_dice/...} 能被解析。
 *
 * <h2>取色规则(与 1.20.1/1.21.1 两线逐字一致)</h2>
 * <ul>
 *   <li>稀有档: 不干预(原版 sprite 保留);</li>
 *   <li>史诗/传奇/巅峰: 整圈单色 = {@link Rarity#rgb()}(贴图里烘焙的就是这个 rgb);</li>
 *   <li>奇特: 10 张预烘焙相位贴图按 {@link Util#getMillis()} 轮播,
 *       每张是 {@link Rarity#rainbowBorderStart(long)} 到 {@link Rarity#rainbowBorderEnd(long)} 的竖直渐变。</li>
 * </ul>
 * tooltip 每帧重绘、事件每帧都发,故彩虹档按帧取相位即得动画,无需自持状态。
 *
 * <p>本类只在客户端加载(位于 {@code client/} 包,且 {@code value = Dist.CLIENT});双端加载类不得引用它。
 */
@EventBusSubscriber(modid = AstralDiceMod.MODID, value = Dist.CLIENT)
public final class RarityTooltipFrame {

    /** 彩虹档的预烘焙相位贴图(每张相隔 1/10 个 {@link Rarity#RAINBOW_CYCLE_MILLIS} 周期)。 */
    private static final String[] RAINBOW_STYLES = {
            "rainbow00", "rainbow01", "rainbow02", "rainbow03", "rainbow04",
            "rainbow05", "rainbow06", "rainbow07", "rainbow08", "rainbow09"
    };

    private RarityTooltipFrame() {
    }

    /**
     * 26.1.2 的 tooltip 贴图事件: 在 {@code GuiGraphicsExtractor#tooltip} 里、
     * 调用 {@code TooltipRenderUtil#extractTooltipBackground} 的**前一行**触发
     * ({@code ClientHooks#onRenderTooltipTexture}),因此这里设的贴图一定会被紧随其后的绘制用到。
     */
    @SubscribeEvent
    public static void onTooltipTexture(RenderTooltipEvent.Texture event) {
        // 物品用 TIP 组件自带样式时尊重它,不覆盖。
        if (event.getOriginalTexture() != null) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) {
            return;
        }
        Rarity tier = AstralRarities.tierOf(stack.getRarity());
        String style = styleOf(tier, Util.getMillis());
        if (style != null) {
            event.setTexture(Identifier.fromNamespaceAndPath(AstralDiceMod.MODID, style));
        }
    }

    /** 档位 → 贴图名(不带命名空间);null = 保持原版。 */
    private static String styleOf(Rarity tier, long millis) {
        if (tier == null || tier == Rarity.RARE) {
            return null;
        }
        if (tier == Rarity.EPIC) {
            return "epic";
        }
        if (tier == Rarity.LEGENDARY) {
            return "legendary";
        }
        if (tier == Rarity.PINNACLE) {
            return "pinnacle";
        }
        if (tier.isRainbow()) {
            long frameMillis = Math.floorMod(millis, Rarity.RAINBOW_CYCLE_MILLIS);
            long phase = frameMillis * RAINBOW_STYLES.length / Rarity.RAINBOW_CYCLE_MILLIS;
            return RAINBOW_STYLES[(int) Math.min(phase, RAINBOW_STYLES.length - 1L)];
        }
        return null;
    }
}
