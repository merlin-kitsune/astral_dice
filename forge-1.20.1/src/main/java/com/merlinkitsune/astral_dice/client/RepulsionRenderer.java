package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.entity.RepulsionEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * 强化拒止渲染器（2026-10-18；2026-10-18 移植到 forge-1.20.1）：{@link LivingEntityRenderer}
 * 的标准装配 —— 模型从 {@link RepulsionModel#LAYER_LOCATION} 烘焙，贴图取自
 * {@code assets/astral_dice/textures/entity/repulsion.png}（= bbmodel 的 {@code textures[0].source}
 * 128×128 原图；北面 UV 块已按「南面块旋转 90° 顺时针」正立化，见 {@link RepulsionModel}）。
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

    /**
     * 不显示头顶名条（2026-10-18）：本实体不走 {@code MobRenderer}，而是直接继承
     * {@link LivingEntityRenderer}；它的 {@code shouldShowName} 只判「距离 + 不在载具里」，
     * <b>不查 {@code customName}</b> —— 原版那条「只有自定义名才画名条」的门禁写在
     * {@code MobRenderer#shouldShowName} 里。所以不覆写的话，立牌类实体头顶会一直画出
     * 实体名（形如「强化拒止」）。路障 / 强化拒止都不是生物，一律不显示名条。
     */
    @Override
    protected boolean shouldShowName(RepulsionEntity entity) {
        return false;
    }
}
