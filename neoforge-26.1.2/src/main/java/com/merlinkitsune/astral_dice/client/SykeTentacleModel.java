package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.starengine.geckolib.model.GeoModel;
import com.merlinkitsune.starengine.geckolib.renderer.base.GeoRenderState;
import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.entity.SykeTentacleEntity;
import net.minecraft.resources.Identifier;

/**
 * Syke 触手的 GeckoLib 模型（26.1.2 线，GeckoLib 5.5.2）：只负责把「渲染态 → 三个资源路径」映射出来。
 *
 * <p><b>v5 的资源根换了</b>（源码实证 {@code cache/GeckoLibResources.java}：{@code ANIMATIONS_PATH =
 * starengine:geckolib/animations}、{@code MODELS_PATH = starengine:geckolib/models}）：
 * 模型/动画必须放在 {@code assets/<ns>/geckolib/models/} 与 {@code assets/<ns>/geckolib/animations/}
 * 下，标识符则是剥掉 {@code geckolib/models|animations} 前缀与 {@code .geo|.animation.json} 后缀之后的
 * {@code astral_dice:sykestentacle}（v4 的 {@code geo/…}、{@code animations/…} 目录 v5 根本不扫）。
 * <ul>
 *   <li>{@code geckolib/models/sykestentacle.geo.json} ← {@code models/main.geo.json}</li>
 *   <li>{@code textures/entity/sykestentacle.png} ← {@code textures/texture.png}（贴图路径 v5 未变）</li>
 *   <li>{@code geckolib/animations/sykestentacle.animation.json} ← {@code animations/default.animation.json}</li>
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
            AstralDiceMod.MODID, "sykestentacle");

    private static final Identifier TEXTURE_LOCATION = Identifier.fromNamespaceAndPath(
            AstralDiceMod.MODID, "textures/entity/sykestentacle.png");

    private static final Identifier ANIMATION_LOCATION = Identifier.fromNamespaceAndPath(
            AstralDiceMod.MODID, "sykestentacle");

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
