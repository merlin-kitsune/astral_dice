package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.entity.InkShadowPortalEntity;
import com.merlinkitsune.starengine.geckolib.model.GeoModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * 【传送门】的 GeckoLib 模型：几何 {@code geo/inkshadoweffect.geo.json}、贴图
 * {@code textures/entity/inkshadoweffect.png}、循环动画 {@code animation.inkshadoweffect.idle}
 * （四根骨骼绕 Y 轴自转，4.0s 一循环；与 1.21.1 线同一份 geo/动画/贴图，含 64×64 贴图空间与
 * {@code .png.mcmeta} 的 {@code blur:false}）。
 *
 * <p><b>渲染层</b>：1.20.1 的 GeckoLib 4.x 把渲染层放在模型侧（{@code GeoModel#getRenderType(entity,
 * texture)}），1.21 线放在渲染器侧；这里显式返回 {@code entityCutoutNoCull}，与 1.21 线一致——
 * 门环是大量零厚度薄片，半透明渲染不写深度、在 Iris/Complementary 光影的延迟管线里排序不稳定会闪烁。
 *
 * <p><b>1.20.1 平台差异</b>：只用二参构造 {@code new ResourceLocation(ns, path)}；
 * {@code ResourceLocation.fromNamespaceAndPath(...)} 是 1.21 的方法（同 {@code SouriDrinkModel} 口径）。
 */
public class InkShadowPortalModel extends GeoModel<InkShadowPortalEntity> {

    private static final ResourceLocation GEO =
            new ResourceLocation(AstralDiceMod.MODID, "geo/inkshadoweffect_ink128.geo.json");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(AstralDiceMod.MODID, "textures/entity/inkshadoweffect_ink128.png");
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

    @Override
    public RenderType getRenderType(InkShadowPortalEntity animatable, ResourceLocation texture) {
        // 保留不剔除背面：门环本身是零厚度薄片，很多面向只有单侧 UV，剔了会漏面。
        return RenderType.entityCutoutNoCull(texture);
    }
}
