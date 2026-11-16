package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.entity.SouriDrinkEntity;
import com.merlinkitsune.starengine.geckolib.renderer.GeoEntityRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/**
 * 金鲤漂浮饮品渲染器：在 {@link GeoEntityRenderer} 之上施加
 * 「像原版掉落物一样旋转 + 上下浮动」的视觉变换（不改实体碰撞箱，也不写动画文件）。
 */
public class SouriDrinkRenderer extends GeoEntityRenderer<SouriDrinkEntity> {

    public SouriDrinkRenderer(EntityRendererProvider.Context context) {
        super(context, new SouriDrinkModel());
    }

    @Override
    public void render(SouriDrinkEntity animatable, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();
        double bob = Math.sin((animatable.tickCount + partialTick) * 0.1D) * 0.08D + 0.12D;
        poseStack.translate(0.0D, bob, 0.0D);
        poseStack.mulPose(Axis.YP.rotationDegrees((animatable.tickCount + partialTick) * 2.5F));
        // 自发光：饮品在暗处也保持全亮（用户需求「10 级自发光」= 不被环境光压暗）
        super.render(animatable, entityYaw, partialTick, poseStack, bufferSource, 0xF000F0);
        poseStack.popPose();
    }
}
