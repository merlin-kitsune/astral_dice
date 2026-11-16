package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.entity.SouriDrinkEntity;
import com.merlinkitsune.starengine.geckolib.constant.dataticket.DataTicket;
import com.merlinkitsune.starengine.geckolib.model.GeoModel;
import com.merlinkitsune.starengine.geckolib.renderer.base.GeoRenderState;
import net.minecraft.resources.Identifier;

/**
 * 金鲤漂浮饮品的 GeckoLib 模型：按实体变体切换两套几何 / 贴图
 * （{@code winegourd} = 葫芦饮，{@code winegourdbetter} = 仙酿）。
 *
 * <p><b>26.1.2 线 = GeckoLib 5</b>（javap 实证 {@code com.merlinkitsune.starengine.geckolib}）：
 * <ul>
 *   <li>{@code getModelResource}/{@code getTextureResource} 的参数是
 *       {@link GeoRenderState}（渲染态），不再收实体实例；只有 {@code getAnimationResource(T)} 仍收实体；</li>
 *   <li>返回类型是 {@link Identifier}（26.1.2 已无 {@code ResourceLocation}）；</li>
 *   <li>变体是**实体态数据**，通过 {@link #addAdditionalStateData} 写进渲染态的数据票
 *       {@link #VARIANT}（v5 没有"把实体塞进渲染态"的口子）。</li>
 * </ul>
 *
 * <p><b>v5 资源根换了</b>：几何/动画必须放在 {@code assets/<ns>/geckolib/models/} 与
 * {@code assets/<ns>/geckolib/animations/} 下，标识符剥掉目录前缀与 {@code .geo|.animation.json}
 * 后缀 ⇒ {@code astral_dice:winegourd} / {@code astral_dice:winegourdbetter} /
 * {@code astral_dice:souridrink}；贴图路径 v5 未变（{@code textures/entity/winegourd.png}）。
 */
public class SouriDrinkModel extends GeoModel<SouriDrinkEntity> {

    /** 渲染态里的变体数据票（值 = {@link SouriDrinkEntity.Variant#ordinal()}） */
    private static final DataTicket<Integer> VARIANT =
            DataTicket.create("astral_dice_souri_drink_variant", Integer.class);

    private static final Identifier GOURD_GEO =
            Identifier.fromNamespaceAndPath(AstralDiceMod.MODID, "winegourd");
    private static final Identifier BREW_GEO =
            Identifier.fromNamespaceAndPath(AstralDiceMod.MODID, "winegourdbetter");
    private static final Identifier GOURD_TEX =
            Identifier.fromNamespaceAndPath(AstralDiceMod.MODID, "textures/entity/winegourd.png");
    private static final Identifier BREW_TEX =
            Identifier.fromNamespaceAndPath(AstralDiceMod.MODID, "textures/entity/winegourdbetter.png");
    private static final Identifier ANIM =
            Identifier.fromNamespaceAndPath(AstralDiceMod.MODID, "souridrink");

    @Override
    public void addAdditionalStateData(SouriDrinkEntity animatable, Object relatedObject, GeoRenderState renderState) {
        renderState.addGeckolibData(VARIANT, animatable.getVariant().ordinal());
    }

    @Override
    public Identifier getModelResource(GeoRenderState renderState) {
        return renderState.getOrDefaultGeckolibData(VARIANT, 0) == SouriDrinkEntity.Variant.BREW.ordinal()
                ? BREW_GEO : GOURD_GEO;
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        return renderState.getOrDefaultGeckolibData(VARIANT, 0) == SouriDrinkEntity.Variant.BREW.ordinal()
                ? BREW_TEX : GOURD_TEX;
    }

    @Override
    public Identifier getAnimationResource(SouriDrinkEntity animatable) {
        return ANIM;
    }
}
