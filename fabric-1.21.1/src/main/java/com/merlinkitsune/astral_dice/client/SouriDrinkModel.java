package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.entity.SouriDrinkEntity;
import com.merlinkitsune.starengine.geckolib.model.GeoModel;
import net.minecraft.resources.ResourceLocation;

/**
 * 金鲤漂浮饮品的 GeckoLib 模型：按实体变体切换两套几何 / 贴图
 * （{@code winegourd} = 葫芦饮，{@code winegourdbetter} = 仙酿）。
 */
public class SouriDrinkModel extends GeoModel<SouriDrinkEntity> {

    private static final ResourceLocation GOURD_GEO =
            ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "geo/winegourd.geo.json");
    private static final ResourceLocation BREW_GEO =
            ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "geo/winegourdbetter.geo.json");
    private static final ResourceLocation GOURD_TEX =
            ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "textures/entity/winegourd.png");
    private static final ResourceLocation BREW_TEX =
            ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "textures/entity/winegourdbetter.png");
    private static final ResourceLocation ANIM =
            ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "animations/souridrink.animation.json");

    @Override
    public ResourceLocation getModelResource(SouriDrinkEntity animatable) {
        return animatable.getVariant() == SouriDrinkEntity.Variant.BREW ? BREW_GEO : GOURD_GEO;
    }

    @Override
    public ResourceLocation getTextureResource(SouriDrinkEntity animatable) {
        return animatable.getVariant() == SouriDrinkEntity.Variant.BREW ? BREW_TEX : GOURD_TEX;
    }

    @Override
    public ResourceLocation getAnimationResource(SouriDrinkEntity animatable) {
        return ANIM;
    }
}
