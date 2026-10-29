package com.merlinkitsune.astral_dice.client;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.entity.SykeTentacleEntity;
import net.minecraft.resources.Identifier;

/**
 * Syke 触手的 GeckoLib 模型（26.1.2 线，GeckoLib 5.5.2）：只负责把「渲染态 → 三个资源路径」映射出来。
 *
 * <p>三个路径与 {@code models/sykestentacle/} 下的源文件一一对应，命名空间统一取
 * {@link AstralDiceMod#MODID}：
 * <ul>
 *   <li>{@code geo/sykestentacle.geo.json} ← {@code models/main.geo.json}</li>
 *   <li>{@code textures/entity/sykestentacle.png} ← {@code textures/texture.png}</li>
 *   <li>{@code animations/sykestentacle.animation.json} ← {@code animations/default.animation.json}</li>
 * </ul>
 *
 * <p><b>v5 与 v4 的签名差异</b>（javap 实证，实装 {@code geckolib-xfVfPcoC.jar}）：
 * {@code getModelResource}/{@code getTextureResource} 的参数从实体实例改成了
 * {@code com.geckolib.renderer.base.GeoRenderState}，只有 {@code getAnimationResource(T)} 仍收实体；
 * 三个方法的返回类型都是 {@link Identifier}（26.1.2 的 {@code ResourceLocation} 已改名为 {@code Identifier}）。
 *
 * <p>注意：这里**没有** {@code LAYER_LOCATION} / {@code createBodyLayer()} —— GeckoLib 的几何来自
 * 上面的 json，不经过原版 {@code EntityRenderersEvent.RegisterLayerDefinitions}；把它加进
 * {@code registerLayerDefinitions} 会是无效注册。
 */
public class SykeTentacleModel extends GeoModel<SykeTentacleEntity> {

    private static final Identifier MODEL_LOCATION = Identifier.fromNamespaceAndPath(
            AstralDiceMod.MODID, "geo/sykestentacle.geo.json");

    private static final Identifier TEXTURE_LOCATION = Identifier.fromNamespaceAndPath(
            AstralDiceMod.MODID, "textures/entity/sykestentacle.png");

    private static final Identifier ANIMATION_LOCATION = Identifier.fromNamespaceAndPath(
            AstralDiceMod.MODID, "animations/sykestentacle.animation.json");

    @Override
    public Identifier getModelResource(GeoRenderState renderState) {
        return MODEL_LOCATION;
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        return TEXTURE_LOCATION;
    }

    @Override
    public Identifier getAnimationResource(SykeTentacleEntity animatable) {
        return ANIMATION_LOCATION;
    }
}
