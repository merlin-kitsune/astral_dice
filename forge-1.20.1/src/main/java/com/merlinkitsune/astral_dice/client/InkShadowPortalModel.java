package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.entity.InkShadowPortalEntity;
import com.merlinkitsune.starengine.geckolib.model.GeoModel;
import net.minecraft.resources.ResourceLocation;

/**
 * 【传送门】的 GeckoLib 模型：几何 {@code geo/inkshadoweffect.geo.json}、贴图
 * {@code textures/entity/inkshadoweffect.png}、循环动画 {@code animation.inkshadoweffect.idle}
 * （四根骨骼绕 Y 轴自转，2.0s 一循环）。
 *
 * <p><b>1.20.1 平台差异</b>：只用二参构造 {@code new ResourceLocation(ns, path)}；
 * {@code ResourceLocation.fromNamespaceAndPath(...)} 是 1.21 的方法（同 {@code SouriDrinkModel} 口径）。
 */
public class InkShadowPortalModel extends GeoModel<InkShadowPortalEntity> {

    private static final ResourceLocation GEO =
            new ResourceLocation(AstralDiceMod.MODID, "geo/inkshadoweffect.geo.json");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(AstralDiceMod.MODID, "textures/entity/inkshadoweffect.png");
    private static final ResourceLocation ANIM =
            new ResourceLocation(AstralDiceMod.MODID, "animations/inkshadoweffect.animation.json");

    @Override
    public ResourceLocation getModelResource(InkShadowPortalEntity animatable) {
        return GEO;
    }

    @Override
    public ResourceLocation getTextureResource(InkShadowPortalEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(InkShadowPortalEntity animatable) {
        return ANIM;
    }
}
