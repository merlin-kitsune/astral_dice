package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.entity.SouriDrinkEntity;
import com.merlinkitsune.starengine.geckolib.model.GeoModel;
import net.minecraft.resources.ResourceLocation;

/**
 * 金鲤漂浮饮品的 GeckoLib 模型：按实体变体切换两套几何 / 贴图
 * （{@code winegourd} = 葫芦饮，{@code winegourdbetter} = 仙酿）。
 *
 * <p><b>1.20.1 平台差异</b>：本线**只用二参构造** {@code new ResourceLocation(ns, path)}；
 * {@code ResourceLocation.fromNamespaceAndPath(...)} 是 1.21 的方法，Forge 直到 47.4.0 才回填，
 * 本线自 2026-11-05 起锁在 47.1.47（见 {@code client/SykeTentacleModel} 同款说明）。
 */
public class SouriDrinkModel extends GeoModel<SouriDrinkEntity> {

    private static final ResourceLocation GOURD_GEO =
            new ResourceLocation(AstralDiceMod.MODID, "geo/winegourd.geo.json");
    private static final ResourceLocation BREW_GEO =
            new ResourceLocation(AstralDiceMod.MODID, "geo/winegourdbetter.geo.json");
    private static final ResourceLocation GOURD_TEX =
            new ResourceLocation(AstralDiceMod.MODID, "textures/entity/winegourd.png");
    private static final ResourceLocation BREW_TEX =
            new ResourceLocation(AstralDiceMod.MODID, "textures/entity/winegourdbetter.png");
    private static final ResourceLocation ANIM =
            new ResourceLocation(AstralDiceMod.MODID, "animations/souridrink.animation.json");

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
