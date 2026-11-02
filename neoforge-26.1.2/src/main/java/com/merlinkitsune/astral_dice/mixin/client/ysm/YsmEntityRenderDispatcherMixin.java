package com.merlinkitsune.astral_dice.mixin.client.ysm;

import com.merlinkitsune.astral_dice.client.render.TranslucentRenderState;
import com.merlinkitsune.astral_dice.client.render.ysm.YsmRenderContext;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * YSM 兼容层的「开窗」点。
 *
 * <p>只在 {@code EntityRenderDispatcher#submit} 这一个原版入口上开 alpha 窗口 —— 这是
 * <b>YSM 也必经</b>的唯一接缝（YSM 在它上面用 {@code @WrapWithCondition} 取消了原版
 * {@code EntityRenderer#submit} 的调用，但外层方法本身仍然执行）。
 *
 * <p>窗口的 alpha 只有三个条件同时成立才是 0.5：
 * <ol>
 *   <li>该 render state 命中本模组的半透明标记（{@link TranslucentRenderState}，由
 *       {@code SignTranslucencyMixin} 在 {@code extractRenderState} 写入）；</li>
 *   <li>YSM 在场（{@link YsmRenderContext#isYsmPresent()}）；</li>
 *   <li>这次提交用的渲染器属于 YSM 包（{@link YsmRenderContext#isYsmRenderer(Object)}）——
 *       非 YSM 实体走原版 {@code LivingEntityRenderer#submit}，由既有
 *       {@code SignTranslucencyMixin#AlphaContext} 处理，两边窗口互不相交，不会把 alpha 乘两遍。</li>
 * </ol>
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class YsmEntityRenderDispatcherMixin {

    @Inject(method = "submit", at = @At("HEAD"))
    private void astral$ysmPushAlpha(EntityRenderState state, CameraRenderState camera,
                                     double camX, double camY, double camZ,
                                     PoseStack poseStack, SubmitNodeCollector collector,
                                     CallbackInfo ci) {
        float alpha = 1.0F;
        if (YsmRenderContext.isYsmPresent()
                && state instanceof TranslucentRenderState translucent
                && translucent.astral$isTranslucent()) {
            // 26.1.2 实测：YSM 不替换渲染器类，玩家仍由原版 AvatarRenderer 绘制，
            // 它只在内部换掉 model 并自己下发几何 ⇒ 不能再以「渲染器属于 YSM 包」为前提。
            // 二次相乘由 YsmGeometryMixin/YsmRenderTypeMixin 的 min(...) 兜住。
            alpha = YsmRenderContext.TRANSLUCENT_ALPHA;
        }
        YsmRenderContext.push(alpha);
    }

    @Inject(method = "submit", at = @At("RETURN"))
    private void astral$ysmPopAlpha(EntityRenderState state, CameraRenderState camera,
                                    double camX, double camY, double camZ,
                                    PoseStack poseStack, SubmitNodeCollector collector,
                                    CallbackInfo ci) {
        YsmRenderContext.pop();
    }
}
