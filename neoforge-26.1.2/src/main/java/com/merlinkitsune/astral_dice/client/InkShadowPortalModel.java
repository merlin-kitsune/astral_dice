package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.entity.InkShadowPortalEntity;
import com.merlinkitsune.starengine.geckolib.model.GeoModel;
import com.merlinkitsune.starengine.geckolib.renderer.base.GeoRenderState;
import net.minecraft.resources.Identifier;

/**
 * 立牌「暗影之门」的几何/贴图/动画资源绑定（资源由 Blockbench 导出，勿在代码里改）。
 *
 * <p><b>26.1.2 线 = GeckoLib 5</b>（javap 实证，与 26 线 {@code SykeTentacleModel} 同形）：
 * <ul>
 *   <li>三个抽象方法的返回类型是 {@link Identifier}（26.x 用 {@code Identifier} 取代
 *       {@code ResourceLocation}）；</li>
 *   <li>{@code getModelResource}/{@code getTextureResource} 的参数是 {@link GeoRenderState}
 *       （渲染态），不再是实体；动画资源仍按实体取；</li>
 *   <li>v5 的资源根改为 {@code assets/astral_dice/geckolib/models/<id>.geo.json} 与
 *       {@code geckolib/animations/<id>.animation.json}，标识符<b>不带</b>路径与后缀
 *       （1.21.1 的 {@code geo/x.geo.json}、{@code animations/x.animation.json} 写法在 v5 不扫描）；
 *       贴图仍走 {@code textures/entity/<name>.png}。</li>
 * </ul>
 */
public class InkShadowPortalModel extends GeoModel<InkShadowPortalEntity> {

    private static final Identifier MODEL = Identifier.fromNamespaceAndPath(AstralDiceMod.MODID, "inkshadoweffect_ink128");
    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(AstralDiceMod.MODID, "textures/entity/inkshadoweffect_ink128.png");
    private static final Identifier ANIMATION = Identifier.fromNamespaceAndPath(AstralDiceMod.MODID, "inkshadoweffect");

    @Override
    public Identifier getModelResource(GeoRenderState renderState) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(InkShadowPortalEntity animatable) {
        return ANIMATION;
    }
}
