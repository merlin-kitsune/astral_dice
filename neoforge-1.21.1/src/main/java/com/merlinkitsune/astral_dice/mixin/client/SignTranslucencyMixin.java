package com.merlinkitsune.astral_dice.mixin.client;

import com.merlinkitsune.astral_dice.effect.ModEffects;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 立牌半透明渲染（1/2）：带「铁处女」或「摸鱼」的玩家强制走 {@code entityTranslucent} 渲染类型，
 * 并在渲染期间打开 {@link AlphaContext} 的 50% 透明度标记（由 {@link ModelPartAlphaMixin} 落到每个部位）。
 */
@Mixin(LivingEntityRenderer.class)
public abstract class SignTranslucencyMixin<T extends LivingEntity> {

    private boolean astral$isTranslucent(LivingEntity entity) {
        return entity instanceof Player player
                && (player.hasEffect(ModEffects.ALANA_IRON_MAIDEN)
                    || player.hasEffect(ModEffects.DEEPSEEK_FISHING));
    }

    @Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
    private void astral$forceTranslucent(T entity, boolean bodyVisible, boolean translucent, boolean glowing,
                                         CallbackInfoReturnable<RenderType> cir) {
        if (astral$isTranslucent(entity)) {
            cir.setReturnValue(RenderType.entityTranslucent(
                    ((EntityRendererInvoker<T>) (Object) this).astral$texture(entity)));
        }
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void astral$alphaOn(T entity, float entityYaw, float partialTick,
                                com.mojang.blaze3d.vertex.PoseStack poseStack,
                                net.minecraft.client.renderer.MultiBufferSource buffer, int packedLight,
                                CallbackInfo ci) {
        // 每次进入 render 都按实体自身状态推入并设置颜色:即使上一次 render 异常未弹出,
        // 也不会把 0.5 泄漏到其它实体(每个实体都在 HEAD 自设颜色)。
        float alpha = astral$isTranslucent(entity) ? 0.5F : 1.0F;
        AlphaContext.push(alpha);
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void astral$alphaOff(T entity, float entityYaw, float partialTick,
                                 com.mojang.blaze3d.vertex.PoseStack poseStack,
                                 net.minecraft.client.renderer.MultiBufferSource buffer, int packedLight,
                                 CallbackInfo ci) {
        try {
            AlphaContext.pop();
        } finally {
            // 恢复到栈顶(嵌套 render 时回到上一层;空栈回落 1.0)。
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, AlphaContext.current());
        }
    }

    /** 渲染期间共享的透明度栈（单线程渲染线程使用；HEAD push / TAIL pop 恢复上一层）。 */
    public static final class AlphaContext {
        private static final java.util.ArrayDeque<Float> STACK = new java.util.ArrayDeque<>();

        private AlphaContext() {
        }

        static void push(float alpha) {
            STACK.push(alpha);
        }

        static void pop() {
            if (!STACK.isEmpty()) STACK.pop();
        }

        static float current() {
            return STACK.isEmpty() ? 1.0F : STACK.peek();
        }
    }
}
