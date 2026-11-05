package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.entity.SykeTentacleEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import com.merlinkitsune.starengine.geckolib.renderer.GeoEntityRenderer;

/**
 * Syke 触手渲染器（2026-10-29；2026-10-29 由 neoforge-1.21.1 蓝本移植到本线）：
 * {@link GeoEntityRenderer} 的标准装配 —— 几何 / 贴图 / 动画全部由 {@link SykeTentacleModel} 提供，
 * 本类不再注册任何原版模型层。
 *
 * <p>动画由实体的两条控制器驱动（{@code idle} 循环 + {@code action} 触发式），
 * 渲染器侧不需要覆写 {@code getTextureLocation}：{@code GeoEntityRenderer} 已按
 * {@code GeoModel#getTextureResource} 取贴图。
 *
 * <p><b>1.20.1 平台差异</b>：{@code GeoEntityRenderer} 的构造器签名
 * {@code (EntityRendererProvider.Context, GeoModel<T>)} 两线同形（本线 GeckoLib 4.8.4 经 javap 核对），
 * 故本类与蓝本逐字一致；注册点见 {@code client/ModClientEvents#registerEntityRenderers}。
 */
public class SykeTentacleRenderer extends GeoEntityRenderer<SykeTentacleEntity> {

    public SykeTentacleRenderer(EntityRendererProvider.Context context) {
        super(context, new SykeTentacleModel());
    }
}
