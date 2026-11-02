package com.merlinkitsune.astral_dice.client.render;

/**
 * 渲染期间的透明度栈。
 *
 * <p>由 {@code SignTranslucencyMixin#submit} 的 HEAD/TAIL 成对 push/pop，原版路径的
 * {@code ARGB.multiply} 重定向与 YSM 兼容层的顶点压暗都读它。
 *
 * <p><b>为什么单独成类</b>：不能放在 {@code com.merlinkitsune.astral_dice.mixin.*} 包里 ——
 * 那是 mixin 的专属包，包内类不允许被其它类直接引用（会抛
 * {@code IllegalClassLoadError: … is in a defined mixin package … and cannot be referenced directly}）。
 */
public final class AlphaContext {

    private static final java.util.ArrayDeque<Float> STACK = new java.util.ArrayDeque<>();

    private AlphaContext() {
    }

    public static void push(float alpha) {
        STACK.push(alpha);
    }

    public static void pop() {
        if (!STACK.isEmpty()) {
            STACK.pop();
        }
    }

    public static float current() {
        return STACK.isEmpty() ? 1.0F : STACK.peek();
    }
}
