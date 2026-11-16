package com.merlinkitsune.astral_dice.platform.client;

import net.minecraft.world.item.ItemStack;

/**
 * tooltip 边框染色的**跨方法传参上下文**(客户端)。
 *
 * <h2>为什么需要它</h2>
 * Forge 的 {@code RenderTooltipEvent.Color} 是**单一方法内**的事件:物品栈、位置、字体、
 * 颜色常量都在 {@code GuiGraphics#renderTooltipInternal} 的同一作用域里。
 * 而 1.20.1 的原版把绘制拆成了两层:
 * <pre>
 *   GuiGraphics#renderTooltip(Font, ItemStack, int, int)      ← 这里**有** ItemStack
 *     └─ GuiGraphics#renderTooltipInternal(Font, List, int, int, Positioner)   ← 这里**有**位置/字体
 *          └─ TooltipRenderUtil#renderTooltipBackground(...)  ← 颜色常量在这里(private static final)
 * </pre>
 * 三者不在同一方法内 ⇒ 必须用 {@link ThreadLocal} 把「当前正在绘制的物品栈」与
 * 「事件解析出的颜色」串起来。tooltip 绘制全程在渲染线程,且逐层同步调用,
 * 用 ThreadLocal 是安全且无锁的。
 *
 * <h2>生命周期(必须成对)</h2>
 * {@code pushStack} → ({@code renderTooltipInternal} 派发事件并 {@code setColors})
 * → {@code TooltipRenderUtil} 读色 → {@code clear}。
 * ⚠️ {@code clear} 挂在 {@code renderTooltip(Font, ItemStack, int, int)} 的 RETURN 上:
 * 即使中途抛异常也会经 finally 语义之外的 RETURN 注入点清理不掉 —— 因此 {@code pushStack}
 * 会**主动清掉上一次的颜色**(防止残留污染下一个 tooltip)。
 */
public final class TooltipFrameColors {

    /** 当前正在绘制的物品栈;null = 本次绘制与我们无关。 */
    private static final ThreadLocal<ItemStack> STACK = new ThreadLocal<>();

    /** [background, borderStart, borderEnd];null = 尚未派发(用原版默认色)。 */
    private static final ThreadLocal<int[]> COLORS = new ThreadLocal<>();

    private TooltipFrameColors() {
    }

    /** 开始绘制某个物品的 tooltip(每次 push 都丢弃上一轮的颜色缓存)。 */
    public static void pushStack(ItemStack stack) {
        STACK.set(stack);
        COLORS.remove();
    }

    /** 本次 tooltip 绘制结束。 */
    public static void clear() {
        STACK.remove();
        COLORS.remove();
    }

    /** 当前物品栈(未开始绘制时为 null)。 */
    public static ItemStack stack() {
        return STACK.get();
    }

    /** 事件被派发后写入最终颜色。 */
    public static void setColors(int background, int borderStart, int borderEnd) {
        COLORS.set(new int[] {background, borderStart, borderEnd});
    }

    public static int background(int fallback) {
        int[] c = COLORS.get();
        return c == null ? fallback : c[0];
    }

    public static int borderStart(int fallback) {
        int[] c = COLORS.get();
        return c == null ? fallback : c[1];
    }

    public static int borderEnd(int fallback) {
        int[] c = COLORS.get();
        return c == null ? fallback : c[2];
    }
}
