package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.entity.SykeTentacleEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * Syke 触手的 GeckoLib 模型（2026-10-29）：只负责把「实体 → 三个资源路径」映射出来。
 *
 * <p>三个路径与 {@code models/sykestentacle/} 下的源文件一一对应，命名空间统一取
 * {@link AstralDiceMod#MODID}：
 * <ul>
 *   <li>{@code geo/sykestentacle.geo.json} ← {@code models/main.geo.json}</li>
 *   <li>{@code textures/entity/sykestentacle.png} ← {@code textures/texture.png}</li>
 *   <li>{@code animations/sykestentacle.animation.json} ← {@code animations/default.animation.json}</li>
 * </ul>
 *
 * <p>注意：这里**没有** {@code LAYER_LOCATION} / {@code createBodyLayer()} —— GeckoLib 的几何来自
 * 上面的 json，不经过原版 {@code EntityRenderersEvent.RegisterLayerDefinitions}；把它加进
 * {@code registerLayerDefinitions} 会是无效注册。
 *
 * <p>1.20.1 的 {@link ResourceLocation} 没有 {@code fromNamespaceAndPath} 工厂（那是 1.21 的写法），
 * 故本线用 {@code new ResourceLocation(String, String)} 构造。
 */
public class SykeTentacleModel extends GeoModel<SykeTentacleEntity> {

    private static final ResourceLocation MODEL_LOCATION =
            new ResourceLocation(AstralDiceMod.MODID, "geo/sykestentacle.geo.json");

    private static final ResourceLocation TEXTURE_LOCATION =
            new ResourceLocation(AstralDiceMod.MODID, "textures/entity/sykestentacle.png");

    private static final ResourceLocation ANIMATION_LOCATION =
            new ResourceLocation(AstralDiceMod.MODID, "animations/sykestentacle.animation.json");

    @Override
    public ResourceLocation getModelResource(SykeTentacleEntity animatable) {
        return MODEL_LOCATION;
    }

    @Override
    public ResourceLocation getTextureResource(SykeTentacleEntity animatable) {
        return TEXTURE_LOCATION;
    }

    @Override
    public ResourceLocation getAnimationResource(SykeTentacleEntity animatable) {
        return ANIMATION_LOCATION;
    }
}
