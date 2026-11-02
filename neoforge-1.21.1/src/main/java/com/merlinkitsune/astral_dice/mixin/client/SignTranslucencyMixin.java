package com.merlinkitsune.astral_dice.mixin.client;

import com.merlinkitsune.astral_dice.client.ClientAlpha;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 立牌半透明渲染（1/2）：带「铁处女」或「摸鱼」的玩家强制走 {@code entityTranslucent} 渲染类型，
 * <p>vanilla 渲染器路径：本类在 renderer 层换 RenderType 并压 alpha 栈；
 * 但 YSM 从 EntityRenderDispatcher 的调用点劫持渲染、根本不执行 renderer.render，
 * 那条路径由 DispatcherAlphaMixin + RenderTypeAlphaMixin 在 dispatcher 层覆盖。
 * 两条路径共享同一份 alpha 状态（ClientAlpha），不各自维护。</p>
 */
@Mixin(LivingEntityRenderer.class)
public abstract class SignTranslucencyMixin<T extends LivingEntity> {

    /** 委托 {@link ClientAlpha#isTranslucentPlayer(net.minecraft.world.entity.Entity)}：判定只保留一份，
     *  避免 vanilla 路径与 YSM 路径各写一份、将来新增第三个效果时静默分叉。 */
    private boolean astral$isTranslucent(LivingEntity entity) {
        return ClientAlpha.isTranslucentPlayer(entity);
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
        float alpha = astral$isTranslucent(entity) ? ClientAlpha.TRANSLUCENT_ALPHA : 1.0F;
        ClientAlpha.push(alpha);
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
    }

    /**
     * 必须用 {@code RETURN}，不能用 {@code TAIL}：
     * {@code LivingEntityRenderer#render} 的第一句就是平台事件闸门（neo 的 {@code RenderLivingEvent.Pre}
     * 被取消 / forge 的 {@code MinecraftForge.EVENT_BUS.post} 返回 true）命中即直接 {@code return}，
     * javap 实测该方法有 2 个返回点（闸门早退 + 正常末尾）。{@code TAIL} 只在方法最后一个返回指令前触发，
     * 早退路径不触发 → 少一次 pop → 0.5 永久留在栈底 → {@code ClientAlpha.isActive()} 恒真 →
     * {@link RenderTypeAlphaMixin} 的全局改写被永久打开、整局不自愈。{@code RETURN} 在每个返回指令前
     * 都触发，与本包 {@link DispatcherAlphaMixin} 的写法一致。
     */
    @Inject(method = "render", at = @At("RETURN"))
    private void astral$alphaOff(T entity, float entityYaw, float partialTick,
                                 com.mojang.blaze3d.vertex.PoseStack poseStack,
                                 net.minecraft.client.renderer.MultiBufferSource buffer, int packedLight,
                                 CallbackInfo ci) {
        try {
            ClientAlpha.pop();
        } finally {
            // 恢复到栈顶(嵌套 render 时回到上一层;空栈回落 1.0)。
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, ClientAlpha.current());
        }
    }
}
