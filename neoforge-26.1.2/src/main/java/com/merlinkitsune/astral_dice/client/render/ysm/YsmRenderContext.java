package com.merlinkitsune.astral_dice.client.render.ysm;

import java.util.ArrayDeque;

/**
 * YSM(Yes Steve Model) 渲染兼容层：透明度窗口 + YSM 在场/渲染器判定。
 *
 * <p><b>为什么需要这一层</b>：本模组的立牌半透明走的是「原版接缝」三处落点
 * （{@code LivingEntityRenderer#extractRenderState / getRenderType / submit}），而 YSM 的
 * 实体渲染器直接重写 {@code submit} 且**不调用 super**，同时在
 * {@code EntityRenderDispatcher#submit} 上用 {@code @WrapWithCondition} 取消了原版
 * {@code EntityRenderer#submit} 的调用、改由它自己下发几何。
 * 于是本模组对 YSM 渲染的玩家实体完全失效（{@code getRenderType} 根本不在它的执行路径上）。
 *
 * <p><b>本层的做法</b>：只在 YSM 渲染器接管的那一次 {@code EntityRenderDispatcher#submit}
 * 期间开一个 alpha 窗口，窗口内：
 * <ol>
 *   <li>{@code RenderTypes#entityCutout} 被改成 {@code entityTranslucent}（YSM 缺省用 alpha 测试管线，
 *       没有 blend state，顶点 alpha 写了也不会半透明）；</li>
 *   <li>{@code SubmitNodeCollection#submitCustomGeometry} 的 {@code CustomGeometryRenderer}
 *       被包一层，逐顶点乘 alpha（YSM 走的是 {@code submitCustomGeometry}，不是 {@code submitModel}）。</li>
 * </ol>
 *
 * <p><b>零影响保证</b>：窗口的 alpha 只有当「state 命中半透明标记」且「该实体的渲染器属于 YSM 包」
 * 时才是 0.5，其余一律 1.0；{@code isActive()} 为 false 时两个接缝全部原样返回。
 * 这样即使 YSM 缺席、或本层被 overall 启用，也不会把 alpha 乘两遍
 * （原版路径的 0.5 → 0.25 bug 由这里的窗口与 {@code SignTranslucencyMixin#AlphaContext} 相互独立来避免）。
 *
 * <p>渲染线程单线程使用，窗口用 {@link ArrayDeque} 做栈，HEAD push / RETURN pop 成对。
 */
public final class YsmRenderContext {

    /** YSM 的 modId。 */
    public static final String YSM_MODID = "yes_steve_model";

    /** YSM 的根包名（其渲染器/模型类全部在混淆后的子包下，这里只按包前缀识别，不引用任何 YSM 类型）。 */
    public static final String YSM_PACKAGE_PREFIX = "com.elfmcys.yesstevemodel";

    /** 半透明的目标不透明度，与 {@code SignTranslucencyMixin} 保持一致。 */
    public static final float TRANSLUCENT_ALPHA = 0.5F;

    private static final ArrayDeque<Float> STACK = new ArrayDeque<>();

    private static volatile Boolean ysmPresent;

    private YsmRenderContext() {
    }

    /**
     * YSM 是否在场。
     *
     * <p>先问 {@code ModList}（mod 加载后期一定可用）；拿不到就按类存在性探测；
     * 仍然判定不了时**返回 true**（失败开放）：真正的缺席会让窗口恒为 1.0，接缝全部惰性，
     * 而误判为「缺席」会让兼容层静默失效 —— 前者安全、后者是隐性 bug。
     */
    public static boolean isYsmPresent() {
        Boolean cached = ysmPresent;
        if (cached != null) {
            return cached;
        }
        boolean present = probeYsm();
        ysmPresent = present;
        return present;
    }

    private static boolean probeYsm() {
        try {
            net.neoforged.fml.ModList modList = net.neoforged.fml.ModList.get();
            if (modList != null) {
                return modList.isLoaded(YSM_MODID);
            }
        } catch (Throwable ignored) {
            // ModList 尚未初始化（极早期 / 非 FML 环境）：退到类探测
        }
        try {
            Class.forName(YSM_PACKAGE_PREFIX + ".YesSteveModel", false, YsmRenderContext.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException notFound) {
            return false;
        } catch (Throwable other) {
            return true;
        }
    }

    /** 该渲染器是否由 YSM 提供（按类名包前缀判定，避免编译期引用混淆类）。 */
    public static boolean isYsmRenderer(Object renderer) {
        return renderer != null && renderer.getClass().getName().startsWith(YSM_PACKAGE_PREFIX + ".");
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

    /** 窗口是否生效（当前需要压暗）。接缝里第一步就查它，false 时零改动。 */
    public static boolean isActive() {
        return current() < 1.0F;
    }
}
