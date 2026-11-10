package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.entity.InkShadowPortalEntity;
import com.merlinkitsune.starengine.geckolib.model.GeoModel;
import net.minecraft.resources.ResourceLocation;

/** 立牌「暗影之门」的几何/贴图/动画资源绑定（资源由 Blockbench 导出，勿在代码里改）。 */
public class InkShadowPortalModel extends GeoModel<InkShadowPortalEntity> {

    @Override
    public ResourceLocation getModelResource(InkShadowPortalEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "geo/inkshadoweffect_ink128.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(InkShadowPortalEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "textures/entity/inkshadoweffect_ink128.png");
    }

    @Override
    public ResourceLocation getAnimationResource(InkShadowPortalEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "animations/inkshadoweffect.animation.json");
    }
}
