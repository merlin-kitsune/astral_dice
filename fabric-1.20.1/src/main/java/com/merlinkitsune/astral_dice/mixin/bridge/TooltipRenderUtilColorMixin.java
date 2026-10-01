package com.merlinkitsune.astral_dice.mixin.bridge;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.merlinkitsune.astral_dice.platform.client.TooltipFrameColors;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * {@code TooltipRenderUtil} 侧的 tooltip 边框染色 —— 把绘制原语的**颜色实参**替换为事件解析出的颜色。
 *
 * <h2>⭐ 为什么用 {@link WrapOperation} 而不是 {@code @ModifyConstant}（2026-09-29 事故 KI-F14）</h2>
 * <p>原实现用 3 个 {@code @ModifyConstant} 直接改写 {@code renderTooltipBackground} 里的三个 ldc
 * 常量（{@code 0xF0100010} / {@code 0x505000FF} / {@code 0x5028007F}）。而
 * <b>Architectury API 的 {@code architectury.mixins.json:client.MixinTooltipRenderUtil}
 * 改的正是同一批常量</b>（那是它「让模组自定义 tooltip 颜色」的 API）。
 *
 * <p>Mixin 对 {@code @ModifyConstant} 是**排他**语义：同一个 ldc 只能被一个 modifier 改写
 * ⇒ 后到者被 skip（只打一条 WARN），而它自己 {@code injectors.defaultRequire = 1}
 * ⇒ 立刻抛 {@code InjectionError: ... failed injection check, (0/1) succeeded}。
 * 两者 priority 同为 1000、本模组先应用 ⇒ <b>architectury 必失败、游戏必崩</b>。实测（生产整合包）：
 * <pre>
 *   @ModifyConstant conflict. Skipping architectury...MixinTooltipRenderUtil
 *     -&gt;modifyTooltipBackgroundColor(I)I with priority 1000,
 *     already redirected by astral_dice...TooltipRenderUtilColorMixin
 *     -&gt;astralDice$backgroundColor(I)I with priority 1000
 *   → Caused by: InjectionError: Critical injection failure: ... Scanned 0 target(s).
 * </pre>
 *
 * <p>⚠️ 崩点极隐蔽：目标类 {@code class_8002}（{@code TooltipRenderUtil}）**只在首次渲染 tooltip 时**
 * 才被类加载 ⇒ 能过主菜单、**进世界 / 悬停 GUI 时才崩**；而 dev 的 {@code run/client/mods} 里
 * 没有 architectury ⇒ **dev 永远绿**（又一个「生产独有」缺陷）。
 *
 * <p>{@link WrapOperation} 包裹的是**调用指令**（{@code INVOKESTATIC renderHorizontalLine} 等），
 * 而 {@code @ModifyConstant} 改的是 **ldc 指令** —— 两者作用于**不同字节码位置**
 * ⇒ **可与任何 {@code @ModifyConstant} 注入器链式共存**（MixinExtras 的注入器本就是为
 * 「多模组对同一调用点各自注入」设计的）。这也与本项目 {@code LivingHurtBridgeMixin} 的既有选择一致。
 *
 * <h2>覆盖的调用点（javap 实证，1.20.1）</h2>
 * <pre>
 *   renderHorizontalLine(g, x,   y-1, w, z, BACKGROUND)      ×2  ← 上/下边框线
 *   renderRectangle     (g, x,   y,   w, h, z, BACKGROUND)   ×1  ← 背景填充
 *   renderVerticalLine  (g, x∓1, y,   h, z, BACKGROUND)      ×2  ← 左/右边框线
 *   renderFrameGradient (g, x,   y+1, w, h, z, TOP, BOTTOM)  ×1  ← 边框竖向渐变
 * </pre>
 * 背景常量（{@code -267386864} = {@code 0xF0100010}）是前 5 处的**末位实参**；边框上下色是最后
 * 一处的第 6/7 参。四个 wrapper 覆盖全部 6 个调用点。
 *
 * <p>⚠️ 目标描述符写的是 **Mojang 名**，由 Loom 在 {@code remapJar} 阶段重映射为 intermediary
 * （与 {@code LivingHurtBridgeMixin} 同口径；含描述符的目标同样会被正确重映射）。这些是**目标类的
 * private static 方法**，重映射同样覆盖。
 *
 * <p>若 {@link TooltipFrameColors} 未写入颜色（非本模组的物品、或订阅者未改色），
 * 四个 wrapper 各自把原值原样传回 ⇒ **完全等价于原版行为**。
 *
 * <p>⭐ 每帧重绘 ⇒ 本 mixin 每帧执行一次；奇特档的「流动渐变」正是靠这个逐帧取色实现的
 * （{@code RarityTooltipFrame} 用 {@code Util.getMillis()} 算相位），不存在需要额外缓存的动画状态。
 */
@Mixin(TooltipRenderUtil.class)
public abstract class TooltipRenderUtilColorMixin {

    /** 背景填充 / 上边框线 / 下边框线 / 左边框线 / 右边框线 —— 末位实参是 {@code BACKGROUND_COLOR}。 */
    @WrapOperation(
            method = "renderTooltipBackground",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/inventory/tooltip/TooltipRenderUtil;renderHorizontalLine(Lnet/minecraft/client/gui/GuiGraphics;IIIII)V"))
    private static void astralDice$background$horizontal(GuiGraphics g, int x, int y, int w, int z, int color,
                                                         Operation<Void> original) {
        original.call(g, x, y, w, z, TooltipFrameColors.background(color));
    }

    @WrapOperation(
            method = "renderTooltipBackground",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/inventory/tooltip/TooltipRenderUtil;renderRectangle(Lnet/minecraft/client/gui/GuiGraphics;IIIIII)V"))
    private static void astralDice$background$rectangle(GuiGraphics g, int x, int y, int w, int h, int z, int color,
                                                        Operation<Void> original) {
        original.call(g, x, y, w, h, z, TooltipFrameColors.background(color));
    }

    @WrapOperation(
            method = "renderTooltipBackground",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/inventory/tooltip/TooltipRenderUtil;renderVerticalLine(Lnet/minecraft/client/gui/GuiGraphics;IIIII)V"))
    private static void astralDice$background$vertical(GuiGraphics g, int x, int y, int h, int z, int color,
                                                       Operation<Void> original) {
        original.call(g, x, y, h, z, TooltipFrameColors.background(color));
    }

    /** 边框竖向渐变 —— 第 6/7 参是 {@code BORDER_COLOR_TOP} / {@code BORDER_COLOR_BOTTOM}。 */
    @WrapOperation(
            method = "renderTooltipBackground",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/inventory/tooltip/TooltipRenderUtil;renderFrameGradient(Lnet/minecraft/client/gui/GuiGraphics;IIIIIII)V"))
    private static void astralDice$borderGradient(GuiGraphics g, int x, int y, int w, int h, int z, int top, int bottom,
                                                  Operation<Void> original) {
        original.call(g, x, y, w, h, z, TooltipFrameColors.borderStart(top), TooltipFrameColors.borderEnd(bottom));
    }
}
