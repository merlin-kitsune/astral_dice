package com.merlinkitsune.astral_dice.mixin.client.ysm;

import com.merlinkitsune.astral_dice.client.render.ysm.YsmRenderContext;
import com.merlinkitsune.astral_dice.client.render.AlphaContext;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * YSM 兼容层的「管线换挡」点。
 *
 * <p>YSM 的模型渲染器按模型元数据里的逐贴图 flag 二选一：自家
 * {@code entity_translucent_ysm} 或原版 {@code RenderTypes#entityCutout(Identifier)}；
 * 而 {@code entityCutout} 走的是 {@code ENTITY_CUTOUT} 管线 —— <b>没有 blend state</b>
 * （只有 {@code ALPHA_CUTOUT} 着色器定义做 alpha 测试），顶点 alpha 写进去也不会半透明。
 * 所以窗口生效期间把 cutout 换成 {@code entityTranslucent}，让 blend 真正发生；
 * 顶点 alpha 由 {@link YsmGeometryMixin} 压到 0.5。
 *
 * <p>为什么在这里而不是在 YSM 那个选管线的混淆方法上：{@code RenderType} 没有公开的贴图 getter，
 * 拿到 cutout 实例后无法反推 {@link Identifier}；而在 {@code entityCutout} 的 HEAD 上，贴图 id 就在手上。
 * 这也是本层唯一不碰 YSM 混淆名的做法 —— YSM 升版改名不会影响这里。
 *
 * <p>只在 alpha 窗口生效时接管；窗口外原样返回（原版路径由
 * {@code SignTranslucencyMixin#getRenderType} 自己换管线）。
 */
@Mixin(RenderTypes.class)
public abstract class YsmRenderTypeMixin {

    @Inject(method = "entityCutout(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;",
            at = @At("HEAD"), cancellable = true)
    private static void astral$ysmCutoutToTranslucent(Identifier texture, CallbackInfoReturnable<RenderType> cir) {
        if (YsmRenderContext.isActive() || AlphaContext.current() < 1.0F) {
            cir.setReturnValue(RenderTypes.entityTranslucent(texture));
        }
    }

    @Inject(method = "entityCutout(Lnet/minecraft/resources/Identifier;Z)Lnet/minecraft/client/renderer/rendertype/RenderType;",
            at = @At("HEAD"), cancellable = true)
    private static void astral$ysmCutoutToTranslucentFlag(Identifier texture, boolean flag,
                                                         CallbackInfoReturnable<RenderType> cir) {
        if (YsmRenderContext.isActive() || AlphaContext.current() < 1.0F) {
            cir.setReturnValue(RenderTypes.entityTranslucent(texture, flag));
        }
    }
}
