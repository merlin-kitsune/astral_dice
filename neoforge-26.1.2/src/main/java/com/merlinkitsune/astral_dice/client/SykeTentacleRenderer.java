package com.merlinkitsune.astral_dice.client;

import com.geckolib.renderer.GeoEntityRenderer;
import com.merlinkitsune.astral_dice.entity.SykeTentacleEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * Syke 触手渲染器（26.1.2 线，GeckoLib 5.5.2）：{@link GeoEntityRenderer} 的标准装配 ——
 * 几何 / 贴图 / 动画全部由 {@link SykeTentacleModel} 提供，本类不再注册任何原版模型层。
 *
 * <p><b>v5 与 v4 的签名差异</b>（javap 实证）：{@code GeoEntityRenderer} 在 v5 有两个类型参数
 * {@code <T extends Entity & GeoAnimatable, R extends EntityRenderState>}（v4 只有一个），
 * 渲染状态类必须显式给出。这里取 {@link LivingEntityRenderState}，依据是该类
 * {@code createRenderState(T, Void)} 的字节码：实体是 {@code LivingEntity} 时 new 的正是
 * {@code net.minecraft.client.renderer.entity.state.LivingEntityRenderState}。
 *
 * <p>动画由实体的两条控制器驱动（{@code idle} 循环 + {@code action} 触发式），
 * 渲染器侧不需要覆写 {@code getTextureLocation}：{@code GeoEntityRenderer} 已按
 * {@code GeoModel#getTextureResource} 取贴图。
 */
public class SykeTentacleRenderer extends GeoEntityRenderer<SykeTentacleEntity, LivingEntityRenderState> {

    public SykeTentacleRenderer(EntityRendererProvider.Context context) {
        super(context, new SykeTentacleModel());
    }
}
