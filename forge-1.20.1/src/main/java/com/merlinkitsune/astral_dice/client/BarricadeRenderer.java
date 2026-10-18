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

    /**
     * 不显示头顶名条（2026-10-18）：本实体不走 {@code MobRenderer}，而是直接继承
     * {@link LivingEntityRenderer}；它的 {@code shouldShowName} 只判「距离 + 不在载具里」，
     * <b>不查 {@code customName}</b> —— 原版那条「只有自定义名才画名条」的门禁写在
     * {@code MobRenderer#shouldShowName} 里。所以不覆写的话，立牌类实体头顶会一直画出
     * 实体名（形如「强化拒止」）。路障 / 强化拒止都不是生物，一律不显示名条。
     */
    @Override
    protected boolean shouldShowName(BarricadeEntity entity) {
        return false;
    }
}
