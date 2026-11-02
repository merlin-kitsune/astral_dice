package com.merlinkitsune.astral_dice.mixin.client;

import com.merlinkitsune.astral_dice.client.ClientAlpha;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 把"不透明族"的实体 RenderType，在半透明窗口内改写成半透明族。
 *
 * <p>为什么必须改：cutout / solid 族<b>没有开启混合</b>，片段 alpha 会被直接丢弃，
 * 只把 RenderSystem.setShaderColor 的 alpha 压到 0.5 只会让模型"变暗"而不透出背景。
 * 只有当 RenderType 属于 translucent 族（开混合）时 alpha 才真正生效。</p>
 *
 * <p>写法约束：</p>
 * <ul>
 *   <li>每个工厂都有多个重载，{@code method} 必须写<b>全描述符</b>，否则 Mixin 报
 *       "Multiple candidates"；</li>
 *   <li>注入目标全是<b>原版成员</b>（不是第三方模组的类），因此<b>不要</b>写
 *       {@code remap = false} —— forge 1.20.1 线要靠 refmap 重映射成 SRG；</li>
 *   <li>只在 {@link ClientAlpha#isActive()} 为真时改写；栈空（YSM 缺席 / 无效果）时
 *       直接放行，行为与改动前逐字节一致。</li>
 * </ul>
 *
 * <p>两处<b>有意</b>的取舍（不是 bug）：① {@code entityCutout} / {@code entitySolid} 原为 CULL + 深度写，
 * 映射到 {@code entityTranslucent}（NO_CULL）后会开始画背面 → 半透明下重叠处颜色加重 —— 与 YSM 自身
 * 的选择（YSM 就走 {@code entityTranslucent}）对齐，属有意取舍；② {@code entityCutoutNoCullZOffset}
 * 映射到 {@code entityTranslucent} 会丢掉 Z 偏移 —— 原版唯一使用者 {@code SkullBlockRenderer} 不在本次
 * 半透明窗口内，影响面窄，故不为它单独造 RenderType。</p>
 */
@Mixin(RenderType.class)
public abstract class RenderTypeAlphaMixin {

    @Inject(method = "entityCutout(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/RenderType;",
            at = @At("HEAD"), cancellable = true)
    private static void astral$entityCutout(ResourceLocation texture, CallbackInfoReturnable<RenderType> cir) {
        if (ClientAlpha.isActive()) {
            cir.setReturnValue(RenderType.entityTranslucent(texture));
        }
    }

    @Inject(method = "entityCutoutNoCull(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/RenderType;",
            at = @At("HEAD"), cancellable = true)
    private static void astral$entityCutoutNoCull(ResourceLocation texture, CallbackInfoReturnable<RenderType> cir) {
        if (ClientAlpha.isActive()) {
            cir.setReturnValue(RenderType.entityTranslucent(texture));
        }
    }

    @Inject(method = "entityCutoutNoCull(Lnet/minecraft/resources/ResourceLocation;Z)Lnet/minecraft/client/renderer/RenderType;",
            at = @At("HEAD"), cancellable = true)
    private static void astral$entityCutoutNoCullOutline(ResourceLocation texture, boolean affectsOutline,
                                                         CallbackInfoReturnable<RenderType> cir) {
        if (ClientAlpha.isActive()) {
            cir.setReturnValue(RenderType.entityTranslucent(texture, affectsOutline));
        }
    }

    @Inject(method = "entityCutoutNoCullZOffset(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/RenderType;",
            at = @At("HEAD"), cancellable = true)
    private static void astral$entityCutoutNoCullZOffset(ResourceLocation texture, CallbackInfoReturnable<RenderType> cir) {
        if (ClientAlpha.isActive()) {
            cir.setReturnValue(RenderType.entityTranslucent(texture));
        }
    }

    @Inject(method = "entityCutoutNoCullZOffset(Lnet/minecraft/resources/ResourceLocation;Z)Lnet/minecraft/client/renderer/RenderType;",
            at = @At("HEAD"), cancellable = true)
    private static void astral$entityCutoutNoCullZOffsetOutline(ResourceLocation texture, boolean affectsOutline,
                                                                CallbackInfoReturnable<RenderType> cir) {
        if (ClientAlpha.isActive()) {
            cir.setReturnValue(RenderType.entityTranslucent(texture, affectsOutline));
        }
    }

    @Inject(method = "entitySolid(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/RenderType;",
            at = @At("HEAD"), cancellable = true)
    private static void astral$entitySolid(ResourceLocation texture, CallbackInfoReturnable<RenderType> cir) {
        if (ClientAlpha.isActive()) {
            cir.setReturnValue(RenderType.entityTranslucent(texture));
        }
    }
}
