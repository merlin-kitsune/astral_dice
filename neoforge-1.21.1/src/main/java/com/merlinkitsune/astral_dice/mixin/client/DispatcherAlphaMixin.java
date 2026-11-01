package com.merlinkitsune.astral_dice.mixin.client;

import com.merlinkitsune.astral_dice.client.ClientAlpha;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 半透明窗口的<b>开窗点</b>：开在 {@code EntityRenderDispatcher#render} 上，不是
 * {@code LivingEntityRenderer} 上。
 *
 * <p>原因：YSM（yes_steve_model）从 dispatcher 的调用点劫持渲染 —— 它用
 * WrapWithCondition 取消对 {@code EntityRenderer#render} 的调用，再走自己的渲染类。
 * 因此挂在 renderer 层的注入点对 YSM 玩家是死代码；而 dispatcher 的
 * {@code render} 方法本身一定会被执行，开在这里才两条路径都覆盖。</p>
 *
 * <p>这里只做两件事：压栈 + 设 shader color。RenderType 的重定向在
 * {@link RenderTypeAlphaMixin} 里做，两者合起来才构成"半透明"。</p>
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class DispatcherAlphaMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void astral$alphaOn(Entity entity, double x, double y, double z, float entityYaw,
                                float partialTick, PoseStack poseStack, MultiBufferSource bufferSource,
                                int packedLight, CallbackInfo ci) {
        float alpha = ClientAlpha.isTranslucentPlayer(entity) ? ClientAlpha.TRANSLUCENT_ALPHA : 1.0F;
        ClientAlpha.push(alpha);
        if (alpha < 1.0F) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void astral$alphaOff(Entity entity, double x, double y, double z, float entityYaw,
                                 float partialTick, PoseStack poseStack, MultiBufferSource bufferSource,
                                 int packedLight, CallbackInfo ci) {
        // 没有开过窗就一个字节都不写：YSM 缺席 / 无效果时必须与改动前逐像素一致，
        // 不能顺手把默认 shader color 再写一遍。
        boolean wasActive = ClientAlpha.isActive();
        ClientAlpha.pop();
        if (wasActive) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, ClientAlpha.current());
        }
    }
}
