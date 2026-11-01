package com.merlinkitsune.astral_dice.mixin.client.ysm;

import com.merlinkitsune.astral_dice.client.render.ysm.AlphaVertexConsumer;
import com.merlinkitsune.astral_dice.client.render.ysm.YsmRenderContext;
import com.merlinkitsune.astral_dice.client.render.AlphaContext;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * YSM 兼容层的「顶点压暗」点。
 *
 * <p>YSM 的模型几何不走 {@code SubmitNodeCollector#submitModel}（全 jar 只有肩上鹦鹉那一个类用），
 * 而是走 {@code submitCustomGeometry(PoseStack, RenderType, CustomGeometryRenderer)}
 * —— 后者拿到的是一个「自己往 {@link com.mojang.blaze3d.vertex.VertexConsumer} 里写顶点」的回调，
 * 正好是拦 alpha 的位置。
 *
 * <p>用 {@code @ModifyVariable(argsOnly = true, index = 3)} 换掉第三个参数（实例方法槽位：
 * 0=this, 1=PoseStack, 2=RenderType, 3=CustomGeometryRenderer），而不是
 * {@code @Inject(HEAD, cancellable) + 重新调用}：后者会再次进入本注入点，需要额外的重入保护。
 *
 * <p>窗口未生效（alpha ≥ 1.0）时原样返回，零开销。
 */
@Mixin(SubmitNodeCollection.class)
public abstract class YsmGeometryMixin {

    @ModifyVariable(method = "submitCustomGeometry", at = @At("HEAD"), argsOnly = true, index = 3)
    private SubmitNodeCollector.CustomGeometryRenderer astral$wrapGeometryRenderer(
            SubmitNodeCollector.CustomGeometryRenderer renderer) {
        // 两套窗口取较小值：原版路径（SignTranslucencyMixin.AlphaContext）与 YSM 窗口互不叠加，
        // 因此 0.5×0.5=0.25 的二次相乘不会发生。
        float alpha = Math.min(YsmRenderContext.current(), AlphaContext.current());
        if (alpha >= 1.0F || renderer == null) {
            return renderer;
        }
        return (pose, vertexConsumer) -> renderer.render(pose, new AlphaVertexConsumer(vertexConsumer, alpha));
    }
}
