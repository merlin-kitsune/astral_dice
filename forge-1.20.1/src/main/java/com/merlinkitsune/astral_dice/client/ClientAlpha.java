package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.effect.ModEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * 渲染线程专用的 50% 透明度窗口（与 26.1.2 的 AlphaContext 同构）。
 *
 * <p>本类<b>不能</b>放在 {@code ...astral_dice.mixin.*} 包里：那是 Mixin 的专属包，
 * 包内类不允许被其它类直接引用（会抛 IllegalClassLoadError，"is in a defined mixin
 * package and cannot be referenced directly"，游戏直接起不来）。</p>
 *
 * <p>透明度只能有<b>一路来源</b>：本类只登记"当前该画多透"，真正落地由调用方决定
 * 走 shader color（RenderSystem.setShaderColor）。顶点 alpha 包装与 shader color
 * 同时开启会得到 0.5 x 0.5 = 0.25，禁止两路并存。</p>
 */
public final class ClientAlpha {
    /** 带效果玩家的目标不透明度。 */
    public static final float TRANSLUCENT_ALPHA = 0.5F;

    /** 渲染线程栈。 */
    private static final java.util.ArrayDeque<Float> STACK = new java.util.ArrayDeque<>();

    /** 防御性上限：渲染中途抛异常导致 pop 丢失时，不让栈无限增长。 */
    private static final int MAX_DEPTH = 64;

    private ClientAlpha() {
    }

    /** 当前实体是否是"带半透明效果、且应被渲染成半透明"的玩家。 */
    public static boolean isTranslucentPlayer(Entity entity) {
        return entity instanceof Player player
                && (player.hasEffect(ModEffects.ALANA_IRON_MAIDEN.get())
                 || player.hasEffect(ModEffects.DEEPSEEK_FISHING.get()));
    }

    public static void push(float alpha) {
        if (STACK.size() >= MAX_DEPTH) {
            STACK.clear();
        }
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

    public static boolean isActive() {
        return current() < 1.0F;
    }
}
