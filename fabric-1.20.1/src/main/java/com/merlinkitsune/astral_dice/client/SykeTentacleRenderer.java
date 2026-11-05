package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.entity.SykeTentacleEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import com.merlinkitsune.starengine.geckolib.renderer.GeoEntityRenderer;

/**
 * Syke 触手渲染器（2026-10-29）：{@link GeoEntityRenderer} 的标准装配 ——
 * 几何 / 贴图 / 动画全部由 {@link SykeTentacleModel} 提供，本类不再注册任何原版模型层。
 *
 * <p>动画由实体的两条控制器驱动（{@code idle} 循环 + {@code action} 触发式），
 * 渲染器侧不需要覆写 {@code getTextureLocation}：{@code GeoEntityRenderer} 已按
 * {@code GeoModel#getTextureResource} 取贴图。
 */
public class SykeTentacleRenderer extends GeoEntityRenderer<SykeTentacleEntity> {

    public SykeTentacleRenderer(EntityRendererProvider.Context context) {
        super(context, new SykeTentacleModel());
    }
}
