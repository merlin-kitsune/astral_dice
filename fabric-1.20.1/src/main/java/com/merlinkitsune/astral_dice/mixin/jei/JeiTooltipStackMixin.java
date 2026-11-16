package com.merlinkitsune.astral_dice.mixin.jei;

import com.merlinkitsune.astral_dice.client.ModernUITooltipCompat;
import com.merlinkitsune.astral_dice.platform.client.TooltipFrameColors;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.world.item.ItemStack;

import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * JEI(1.20.1/fabric)与「按稀有度给提示框上边框色」之间的桥。
 *
 * <h2>为什么挂 {@code JeiTooltip#draw} 而不是 JEI 侧任何 {@code renderTooltip}</h2>
 * <p>本移植在 1.20.1 上踩了两次 {@code VerifyError: Bad type on operand stack}，同一个病根：
 * JEI 15.74 的若干方法会 **{@code astore_0} 复用 this 槽**（换成 List / ClientTooltipPositioner 等），
 * 而 Mixin 注入时会按 LVT 声明类型重算栈帧 ⇒ 验证器直接拒绝，连 {@code @At("HEAD")} 也连带触发。
 * <ul>
 *   <li>{@code mezz.jei.fabric.platform.RenderHelper#renderTooltip(GuiGraphics, List, II, Font, ItemStack)}：
 *       {@code astore_0} ← {@code DefaultTooltipPositioner.INSTANCE}
 *       （首版崩溃点，{@code crash-2026-11-07_17.41.44-client.txt}）</li>
 *   <li>{@code mezz.jei.common.gui.JeiTooltip#renderTooltip(IPlatformRenderHelper, ...)}：
 *       {@code aload 7; aload_0; getfield lines; astore_0} —— this 槽被换成 {@code List}（第二版崩溃点）</li>
 * </ul>
 * <p>{@code JeiTooltip#draw(GuiGraphics, int, int, ITypedIngredient, IIngredientRenderer, IIngredientManager,
 * ClientTooltipPositioner)} 是**没有** slot-0 复用、且同时持有 {@code GuiGraphics} 与
 * {@code ITypedIngredient} 的最近一层公共方法（javap 逐方法扫 {@code astore_0} 确认），物品栈由
 * {@code ITypedIngredient#getItemStack()} 取；它内部再分派到 6 参 / 7 参两个平台重载，
 * 所以一对 HEAD/RETURN 覆盖全部绘制路径。
 *
 * <h2>为什么要同时驱动 {@link ModernUITooltipCompat}</h2>
 * <p>Modern UI 3.12.0.1 fabric 版以 {@code mixinPriority: 1} 在 {@code GuiGraphics#renderTooltipInternal}
 * 的 HEAD {@code cancel()} 自绘，本模组默认优先级的同类注入永不执行；而它的
 * {@code TooltipRenderer#drawTooltip} 首段按**物品栈实例**缓存（{@code if (itemStack != mLastSeenItem)}）
 * ⇒ JEI 从不给 {@code GuiGraphics#modernUI_MC$tooltipStack} 赋值，字段恒为 {@code ItemStack.EMPTY} 单例，
 * 颜色永不重算。所以这里成对调 {@link ModernUITooltipCompat#beginRender(ItemStack)}（写
 * {@code TooltipRenderer.sStrokeColor} 四槽、临时关 {@code sAdaptiveColors}）与
 * {@link ModernUITooltipCompat#enterTooltipStack(Object, ItemStack)}（写栈字段，绕开实例缓存）。
 *
 * <p>全部 {@code require = 0}；JEI 缺席时不加载（{@code astral_dice.jei.mixins.json} 是
 * {@code required: false}），签名漂移时降级为「JEI 里不染色」。
 */
@Pseudo
@Mixin(targets = "mezz.jei.common.gui.JeiTooltip")
abstract class JeiTooltipStackMixin {

    private static final String DRAW =
            "draw(Lnet/minecraft/client/gui/GuiGraphics;IILmezz/jei/api/ingredients/ITypedIngredient;Lmezz/jei/api/ingredients/IIngredientRenderer;Lmezz/jei/api/runtime/IIngredientManager;Lnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;)V";

    @Inject(method = DRAW, at = @At("HEAD"), require = 0)
    private void astralDice$pushStack(GuiGraphics graphics, int mouseX, int mouseY, ITypedIngredient<?> ingredient,
                                      IIngredientRenderer<?> renderer, IIngredientManager manager,
                                      ClientTooltipPositioner positioner, CallbackInfo ci) {
        ItemStack stack = ingredient == null ? ItemStack.EMPTY : ingredient.getItemStack().orElse(ItemStack.EMPTY);
        TooltipFrameColors.pushStack(stack);
        ModernUITooltipCompat.beginRender(stack);
        ModernUITooltipCompat.enterTooltipStack(graphics, stack);
    }

    @Inject(method = DRAW, at = @At("RETURN"), require = 0)
    private void astralDice$popStack(GuiGraphics graphics, int mouseX, int mouseY, ITypedIngredient<?> ingredient,
                                     IIngredientRenderer<?> renderer, IIngredientManager manager,
                                     ClientTooltipPositioner positioner, CallbackInfo ci) {
        ModernUITooltipCompat.exitTooltipStack(graphics);
        ModernUITooltipCompat.endRender();
        TooltipFrameColors.clear();
    }
}
