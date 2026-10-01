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
 * <h2>规则（2026-10-01 三次修订）</h2>
 * <ul>
 *   <li><b>稀有</b>：完全随原版 —— 不干预边框（原版紫蓝渐变），物品名由本档 Style 上色；</li>
 *   <li><b>史诗 / 传奇 / 巅峰</b>：边框 = {@link Rarity#frameColor(long)}（= {@link Rarity#rgb()}，整圈单色）
 *       ⇒ **史诗 = 原版 EPIC 配色 {@code #FF55FF}**（2026-10-01 用户裁决：史诗不再「随原版」）；</li>
 *   <li><b>奇特</b>：边框 = {@link Rarity#rainbowBorderStart(long)} → {@link Rarity#rainbowBorderEnd(long)}
 *       的两色流动渐变。</li>
 * </ul>
 *
 * <h2>⚠️ 史诗为什么必须「显式写色」而不是「不动」（2026-10-01 实测根因）</h2>
 * <p>实测整合包「狐の航空学 Voxy Edition」：**本模组没有写色的 tooltip 会沿用上一次的边框色**。
 * 判据（三条独立证据）：
 * <ol>
 *   <li>与本模组无关的物品 `oritech:adamant_block`，提示框边框实测为**纯色 `#FFC24B`**
 *       —— 正是本模组**传奇档的档位色**，且「整圈纯色」正是本类的绘制风格（原版是紫蓝渐变）；</li>
 *   <li>全包 **491 个 jar 递归展开（含 JarJar 内嵌）**扫描 `0x00FFC24B`：命中的类**只有本模组内嵌的
 *       `com/merlinkitsune/starenginelib/item/Rarity.class`** ⇒ 这个颜色只可能来自我们；</li>
 *   <li>本模组物品（`astral_dice:ren_sign`，史诗）名字已是正确的淡紫、边框却是金色 ⇒ 同为残留。</li>
 * </ol>
 * 即：**我们上一次写下的边框色被某个第三方缓存，并施加到下一个「没人设色」的 tooltip 上**。
 * 反向结论就是本类的设计准则 —— **凡是「不写色」的档位都会成为别人缓存的受害者；只要我们自己写色，
 * 该 tooltip 就一定用我们的值**（事件值直送原版绘制：`ClientHooks.onRenderTooltipColor` → 事件 →
 * `TooltipRenderUtil.renderTooltipBackground(..., event.getBorderStart(), event.getBorderEnd())`）。
 * 史诗档因此从「随原版」改为「显式写入档位色」。
 *
 * <p>⚠️ **稀有档目前仍不写色**，按同一机理它同样可能被残留污染。若要一并改为显式写色
 * （稀有档位色 = 原版 RARE 配色 {@code #55FFFF}），把守卫里的 {@code tier == Rarity.RARE} 去掉即可
 * —— 已与用户确认过「只改史诗」，故此处保留原状并登记该风险。
 *
 * <h2>为什么不用 Mixin</h2>
 * <p>tooltip **每帧重绘**（{@code AbstractContainerScreen#renderTooltip} 每帧调用），
 * {@code RenderTooltipEvent.Color} 因而**每帧都发** ⇒ 按当前时刻取色即得流动动画，无需碰原版绘制代码。
 * 该事件只给两个颜色（起/止）：原版绘制是「上横线 = 起始色、下横线 = 结束色、左右竖线 = 两者竖直渐变」
 * （{@code TooltipRenderUtil#renderFrameGradient}）⇒ 非彩虹档两色相同 = 整圈单色；彩虹档两色不同 = 竖直渐变。
 *
 * <h2>只管本模组档位</h2>
 * <p>{@link AstralRarities#tierOf(net.minecraft.world.item.Rarity)} 返回 {@code null}（原版物品、其它模组物品）
 * 时一律不动（那些 tooltip 的颜色归它们自己或原版管，本模组不越界）；返回本模组档位时
 * **除稀有档外一律显式写色**（稀有档暂按原版不干预，见上）。
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
        if (tier == null || tier == Rarity.RARE) {
            return; // 非本模组档位不动；稀有档暂按原版不干预（见类头 ⚠️）
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
