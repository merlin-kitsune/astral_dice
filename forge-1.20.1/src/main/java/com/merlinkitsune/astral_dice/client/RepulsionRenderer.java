package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.entity.RepulsionEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * 强化拒止渲染器（2026-10-18；2026-10-18 移植到 forge-1.20.1）：{@link LivingEntityRenderer}
 * 的标准装配 —— 模型从 {@link RepulsionModel#LAYER_LOCATION} 烘焙，贴图取自
 * {@code assets/astral_dice/textures/entity/repulsion.png}（由 bbmodel 的
 * {@code textures[0].source} 解码后按 UV 画布 2:1 降采样为 64×64）。
 *
 * <p>阴影半径 0.6：菱形头宽约 1.06 格，取略小于实体宽（1.6）的值；位移 / 朝向一律交给
 * {@code LivingEntityRenderer} 的默认实现（{@code yBodyRot} 由放置时写入 90° 的倍数）。
 *
 * <p><b>1.20.1 平台差异</b>：贴图 id 用 1.20.1 的两参 {@code new ResourceLocation(MODID, path)}
 * （本线没有 {@code fromNamespaceAndPath}）；其余（{@code EntityRendererProvider.Context#bakeLayer}、
 * {@code LivingEntityRenderer} 三参构造、{@code getTextureLocation} 覆写点）两线同形。
 * 同线先例：{@code client/BarricadeRenderer}。
 */
public class RepulsionRenderer extends LivingEntityRenderer<RepulsionEntity, RepulsionModel> {

    public static final ResourceLocation TEXTURE = new ResourceLocation(
            AstralDiceMod.MODID, "textures/entity/repulsion.png");

    public RepulsionRenderer(EntityRendererProvider.Context context) {
        super(context, new RepulsionModel(context.bakeLayer(RepulsionModel.LAYER_LOCATION)), 0.6F);
    }

    @Override
    public ResourceLocation getTextureLocation(RepulsionEntity entity) {
        return TEXTURE;
    }
}
