package com.merlinkitsune.astral_dice.mixin.client;

import com.merlinkitsune.astral_dice.client.render.TranslucentRenderState;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 立牌半透明渲染（26.1.2 两段式实现）。
 *
 * <p>带「铁处女」{@link ModEffects#ALANA_IRON_MAIDEN} 或「摸鱼」{@link ModEffects#DEEPSEEK_FISHING}
 * 的玩家，整个实体以 50% 不透明度绘制。
 *
 * <p><b>为什么不能照抄 1.21.1</b>：1.21.1 是在 {@code LivingEntityRenderer#render(...)} 里
 * push 一个 alpha 并调 {@code RenderSystem#setShaderColor(1,1,1,alpha)}。26.1.2 把渲染拆成
 * {@code extractRenderState → submit(state, PoseStack, SubmitNodeCollector, CameraRenderState)}，
 * 既没有 {@code render}，也没有 {@code setShaderColor}；透明度改为随
 * {@code submitNodeCollector.submitModel(..., color, ...)} 的 ARGB 顶点色走。
 *
 * <p>因此这里分三处落点：
 * <ol>
 *   <li>{@code extractRenderState} 末尾：把「是否半透明」写进 render state
 *       （{@link TranslucentRenderState}），因为 {@code submit} 只拿得到 state、拿不到 Entity；</li>
 *   <li>{@code getRenderType}：命中时改用 {@code RenderTypes.entityTranslucent(texture)}，
 *       否则原版的 {@code entityCutoutNoCull} 会做 alpha 测试、半透明直接看不见；</li>
 *   <li>{@code submit}：HEAD/TAIL 维护 {@link AlphaContext} 栈，并把模型顶点色
 *       （原版 {@code ARGB.multiply(baseColor, getModelTint(state))}）的 alpha 乘上 0.5。</li>
 * </ol>
 */
@Mixin(LivingEntityRenderer.class)
public abstract class SignTranslucencyMixin<T extends LivingEntity, S extends LivingEntityRenderState,
        M extends EntityModel<? super S>> {

    /** 该实体是否应半透明（两名玩家都可能是本地/远端玩家，故按实体自身判定）。 */
    @Unique
    private static boolean astral$isTranslucent(LivingEntity entity) {
        return entity instanceof Player player
                && (player.hasEffect(ModEffects.ALANA_IRON_MAIDEN)
                    || player.hasEffect(ModEffects.DEEPSEEK_FISHING));
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void astral$markTranslucent(T entity, S state, float partialTicks, CallbackInfo ci) {
        ((TranslucentRenderState) state).astral$setTranslucent(astral$isTranslucent(entity));
    }

    @Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
    private void astral$forceTranslucent(S state, boolean isBodyVisible, boolean forceTransparent,
                                         boolean appearGlowing, CallbackInfoReturnable<RenderType> cir) {
        // 只在「身体可见」时接管：隐身 / 透视 / 发光描边等分支保持原版语义
        if (isBodyVisible && !forceTransparent && ((TranslucentRenderState) state).astral$isTranslucent()) {
            cir.setReturnValue(RenderTypes.entityTranslucent(astral$texture(state)));
        }
    }

    @Unique
    @SuppressWarnings("unchecked")
    private Identifier astral$texture(S state) {
        return ((LivingEntityRenderer<T, S, M>) (Object) this).getTextureLocation(state);
    }

    @Inject(method = "submit", at = @At("HEAD"))
    private void astral$alphaOn(S state, PoseStack poseStack, SubmitNodeCollector collector,
                                CameraRenderState camera, CallbackInfo ci) {
        AlphaContext.push(((TranslucentRenderState) state).astral$isTranslucent() ? 0.5F : 1.0F);
    }

    @Inject(method = "submit", at = @At("TAIL"))
    private void astral$alphaOff(S state, PoseStack poseStack, SubmitNodeCollector collector,
                                 CameraRenderState camera, CallbackInfo ci) {
        AlphaContext.pop();
    }

    /**
     * 把模型顶点色的 alpha 乘上当前层透明度。
     *
     * <p>原版对应行是 {@code int tintedColor = ARGB.multiply(baseColor, this.getModelTint(state));}，
     * 也是 26.1.2 唯一把颜色送进 {@code submitModel} 的位置。
     */
    @Redirect(method = "submit", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/ARGB;multiply(II)I"))
    private int astral$applyAlpha(int baseColor, int modelTint) {
        int color = ARGB.multiply(baseColor, modelTint);
        float alpha = AlphaContext.current();
        return alpha >= 1.0F ? color : ARGB.multiplyAlpha(color, alpha);
    }

    /** 渲染期间共享的透明度栈（渲染线程单线程使用；HEAD push / TAIL pop）。 */
    public static final class AlphaContext {

        private static final java.util.ArrayDeque<Float> STACK = new java.util.ArrayDeque<>();

        private AlphaContext() {
        }

        static void push(float alpha) {
            STACK.push(alpha);
        }

        static void pop() {
            if (!STACK.isEmpty()) {
                STACK.pop();
            }
        }

        static float current() {
            return STACK.isEmpty() ? 1.0F : STACK.peek();
        }
    }
}
