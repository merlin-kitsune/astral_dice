package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.entity.BarricadeEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * 路障渲染器（2026-10-17；2026-10-17 移植到 forge-1.20.1）：{@link LivingEntityRenderer} 的标准装配 ——
 * 模型从 {@link BarricadeModel#LAYER_LOCATION} 烘焙，贴图取自
 * {@code assets/astral_dice/textures/entity/barricade.png}（由 bbmodel 的
 * {@code textures[0].source} base64 解出，64×64）。
 *
 * <p>阴影半径 0.5：路障宽约 1.25 格，阴影略窄于碰撞方块；位移/朝向一律交给
 * {@code LivingEntityRenderer} 的默认实现（{@code yBodyRot} 由实体放置时写死为 180° 的倍数）。
 *
 * <p><b>1.20.1 平台差异</b>：贴图 id 用 1.20.1 的两参 {@code new ResourceLocation(MODID, path)}
 * （本线没有 {@code fromNamespaceAndPath}）；其余（{@code EntityRendererProvider.Context#bakeLayer}、
 * {@code LivingEntityRenderer} 三参构造、{@code getTextureLocation} 覆写点）两线同形。
 */
public class BarricadeRenderer extends LivingEntityRenderer<BarricadeEntity, BarricadeModel> {

    public static final ResourceLocation TEXTURE = new ResourceLocation(
            AstralDiceMod.MODID, "textures/entity/barricade.png");

    public BarricadeRenderer(EntityRendererProvider.Context context) {
        super(context, new BarricadeModel(context.bakeLayer(BarricadeModel.LAYER_LOCATION)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(BarricadeEntity entity) {
        return TEXTURE;
    }
}
