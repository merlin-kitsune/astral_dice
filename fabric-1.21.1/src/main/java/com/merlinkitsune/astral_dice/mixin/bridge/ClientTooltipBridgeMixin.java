package com.merlinkitsune.astral_dice.mixin.bridge;

import java.util.List;

import com.merlinkitsune.astral_dice.client.IcebergTooltipCacheGuard;
import com.merlinkitsune.astral_dice.client.ModernUITooltipCompat;
import com.merlinkitsune.astral_dice.platform.client.TooltipFrameColors;
import com.merlinkitsune.astral_dice.platform.client.event.RenderTooltipEvent;
import com.merlinkitsune.astral_dice.platform.event.LoaderBus;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code GuiGraphics} 侧的 tooltip 桥 —— 为 {@link RenderTooltipEvent.Color} 补齐派发源。
 *
 * <p>2026-09-29 补:此前该事件无任何派发源 ⇒ {@code client/RarityTooltipFrame}
 * (传奇/巅峰档的单色边框 + 奇特档的彩虹流动渐变)**永不生效**,所有本模组物品的
 * 提示框都退回原版紫蓝渐变。
 *
 * <h2>三步接线</h2>
 * <ol>
 *   <li>{@code renderTooltip(Font, ItemStack, int, int)} —— 原版**唯一**带 ItemStack 的入口,
 *       在此压入「当前物品栈」(退出时清理);</li>
 *   <li>{@code renderTooltipInternal(...)} 的 HEAD —— **先**给 Iceberg 的全局颜色缓存复位
 *       (见 {@link IcebergTooltipCacheGuard}),**再**派发
 *       {@code RenderTooltipEvent.Color} 并把结果颜色写入 {@link TooltipFrameColors},
 *       供 {@code TooltipRenderUtil} 侧读取。</li>
 * </ol>
 *
 * <p>⚠️ **本线没有 {@code RenderTooltipEvent.Pre} 的派发源**(该平台事件类在本线只为
 * {@code Color} 备了洞),而 Iceberg 缓存必须在「同一次 tooltip 绘制之前」复位 ⇒ 借用本
 * {@code HEAD} 注入作为复位点。顺序**必须**是「复位 → 派发 Color」:反过来的话本模组自己写下的
 * 颜色会在派发后立刻被复位掉。另三线用各自的 {@code RenderTooltipEvent.Pre}(它在
 * {@code GuiGraphics#renderTooltipInternal} 的第一条语句触发),与本注入位置**语义等价**。
 *
 * <p>⚠️ 颜色常量取自 {@code TooltipRenderUtil} 的 private static final 字段
 * (javap 实证:`BACKGROUND_COLOR=0xF0100010` / `BORDER_COLOR_TOP=0x505000FF` /
 * `BORDER_COLOR_BOTTOM=0x5028007F`)。这里只把它们作为**事件默认值**传入,
 * 真正的替换发生在 {@code TooltipRenderUtilColorMixin}(同一个值的权威来源)。
 */
@Mixin(GuiGraphics.class)
public abstract class ClientTooltipBridgeMixin {

    /** 原版 tooltip 背景色(实证自 {@code TooltipRenderUtil.BACKGROUND_COLOR})。 */
    private static final int ASTRAL_DICE$DEFAULT_BACKGROUND = 0xF0100010;
    /** 原版边框起始色(实证自 {@code TooltipRenderUtil.BORDER_COLOR_TOP})。 */
    private static final int ASTRAL_DICE$DEFAULT_BORDER_TOP = 0x505000FF;
    /** 原版边框结束色(实证自 {@code TooltipRenderUtil.BORDER_COLOR_BOTTOM})。 */
    private static final int ASTRAL_DICE$DEFAULT_BORDER_BOTTOM = 0x5028007F;

    @Inject(
            method = "renderTooltip(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;II)V",
            at = @At("HEAD"))
    private void astralDice$pushTooltipStack(Font font, ItemStack stack, int x, int y, CallbackInfo ci) {
        TooltipFrameColors.pushStack(stack);
        // Modern UI(第三方提示框模组)兼容:把本模组档位色写入它的描边槽(未装则空操作)。
        // ⚠️ **必须写在这里** —— Modern UI 会在本方法的内层自绘并取消原版绘制,
        //    写晚了(例如写进 renderTooltipInternal)在它接管时根本不会被执行。
        ModernUITooltipCompat.beginRender(stack);
    }

    @Inject(
            method = "renderTooltip(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;II)V",
            at = @At("RETURN"))
    private void astralDice$popTooltipStack(Font font, ItemStack stack, int x, int y, CallbackInfo ci) {
        // 先还原 Modern UI 的描边配置(它已在本方法内画完;它取消的是内层方法,外层必然返回),
        // 再清理共用颜色槽。顺序不可颠倒。
        ModernUITooltipCompat.endRender();
        TooltipFrameColors.clear();
    }

    @Inject(method = "renderTooltipInternal", at = @At("HEAD"))
    private void astralDice$dispatchTooltipColor(Font font, List<ClientTooltipComponent> components,
                                                 int x, int y, ClientTooltipPositioner positioner,
                                                 CallbackInfo ci) {
        // ① 先复位 Iceberg 的全局颜色缓存（本轮新增；对未装 Iceberg 的环境是空操作）。
        //    必须在派发 Color **之前**：否则本次 tooltip 自己写下的颜色会被复位掉。
        IcebergTooltipCacheGuard.reset();
        // ② 再派发 Color。⚠️ 复位与派发都**不能**放进下面的 stack 判空之后 —— 非物品 tooltip
        //    （stack 为 null）同样会吃到 Iceberg 的残留，而那正是本轮要修的「其它 tooltip 污染」。
        ItemStack stack = TooltipFrameColors.stack();
        if (stack == null || stack.isEmpty()) {
            return;
        }
        RenderTooltipEvent.Color event = new RenderTooltipEvent.Color(
                stack, (GuiGraphics) (Object) this, x, y, font,
                ASTRAL_DICE$DEFAULT_BACKGROUND,
                ASTRAL_DICE$DEFAULT_BORDER_TOP,
                ASTRAL_DICE$DEFAULT_BORDER_BOTTOM,
                components);
        LoaderBus.INSTANCE.post(event);
        TooltipFrameColors.setColors(
                event.getBackgroundStart(), event.getBorderStart(), event.getBorderEnd());
    }
}
