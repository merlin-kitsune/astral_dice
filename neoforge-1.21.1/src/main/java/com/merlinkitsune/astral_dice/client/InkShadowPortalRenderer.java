package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.entity.InkShadowPortalEntity;
import com.merlinkitsune.starengine.geckolib.renderer.GeoEntityRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * 暗影之门渲染器：GeckoLib 模型（{@code inkeshadoweffect}）+ 激活态的黑色漩涡公告板。
 *
 * <p>漩涡不是模型的一部分（geo 里没有漩涡面），而是在模型中心另画一片朝向相机的四边形，
 * 贴图为 {@code textures/entity/inkshadow_vortex.png}（64×64 黑色螺旋、alpha 透明），
 * 绕自身法线缓慢自转（{@link #VORTEX_PERIOD_TICKS}）。
 */
public class InkShadowPortalRenderer extends GeoEntityRenderer<InkShadowPortalEntity> {

    /** 模型整体缩放（待用户验收调整） */
    public static final float MODEL_SCALE = 1.6F;

    /** 模型整体抬升（待用户验收调整） */
    public static final double MODEL_Y_OFFSET = 0.05D;

    /**
     * 未配对起始门的目标透明度（待用户验收调整）。
     * 说明：GeckoLib 的渲染层没有逐顶点 alpha 钩子，{@code entityTranslucent} 也只影响混合方式；
     * 这里保留常量作为设计值，实际以「缩小到 95%」近似弱化（差异已写入交付报告）。
     */
    public static final float PENDING_ALPHA = 0.45F;

    /** 漩涡半径（格，待用户验收调整） */
    public static final float VORTEX_RADIUS = 0.42F;

    /** 漩涡自转一周所需 tick（待用户验收调整） */
    public static final int VORTEX_PERIOD_TICKS = 90;

    /** 漩涡整体不透明度（待用户验收调整） */
    public static final float VORTEX_ALPHA = 0.75F;

    /** 未配对门大约缩到 95%，作为 0.45 alpha 的降级表达 */
    private static final float PENDING_RENDER_SCALE = 0.95F;

    private static final ResourceLocation VORTEX_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "textures/entity/inkshadow_vortex.png");

    public InkShadowPortalRenderer(EntityRendererProvider.Context context) {
        super(context, new InkShadowPortalModel());
        withScale(MODEL_SCALE);
    }

    @Override
    public RenderType getRenderType(InkShadowPortalEntity animatable, ResourceLocation texture,
                                    MultiBufferSource bufferSource, float partialTick) {
        // 半透明层：漩涡需要混合；门本体贴图本身不透明，视觉上不受影响
        return RenderType.entityTranslucent(texture);
    }

    @Override
    public void render(InkShadowPortalEntity animatable, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();
        poseStack.translate(0.0D, MODEL_Y_OFFSET, 0.0D);
        if (!animatable.isActive()) {
            poseStack.scale(PENDING_RENDER_SCALE, PENDING_RENDER_SCALE, PENDING_RENDER_SCALE);
        }
        super.render(animatable, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        if (animatable.isActive()) {
            renderVortex(animatable, poseStack, bufferSource, packedLight, partialTick);
        }
        poseStack.popPose();
    }

    /** 朝向相机的黑色漩涡：绕 Y 轴对齐视线，再绕自身法线按时间自转。 */
    private void renderVortex(InkShadowPortalEntity entity, PoseStack poseStack, MultiBufferSource bufferSource,
                              int packedLight, float partialTick) {
        double dx = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().x - entity.getX();
        double dz = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().z - entity.getZ();
        float faceYaw = (float) Math.toDegrees(Math.atan2(dx, dz));
        float spin = (entity.tickCount + partialTick) * 360.0F / VORTEX_PERIOD_TICKS;

        poseStack.pushPose();
        // 门体模型高约 0.6 格（0.37 × 1.6）：漩涡放在中段
        poseStack.translate(0.0D, VORTEX_RADIUS * 0.7D, 0.0D);
        poseStack.mulPose(Axis.YP.rotationDegrees(faceYaw));
        poseStack.mulPose(Axis.ZP.rotationDegrees(spin));
        VertexConsumer vc = bufferSource.getBuffer(RenderType.entityTranslucent(VORTEX_TEXTURE));
        PoseStack.Pose pose = poseStack.last();
        float r = VORTEX_RADIUS;
        // 正反两面各画一次：不依赖渲染层的 cull 设置（两次之间留 0.002 的深度差避免 z-fighting）
        quad(vc, pose, r, 0.0F, packedLight);
        quad(vc, pose, r, -0.002F, packedLight);
        poseStack.popPose();
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose pose, float r, float z, int packedLight) {
        // 近黑色底 + 螺旋贴图；颜色常量取 0xFF1A1020 的 RGB
        float red = 0x1A / 255.0F;
        float green = 0x10 / 255.0F;
        float blue = 0x20 / 255.0F;
        vertex(vc, pose, -r, -r, z, 0.0F, 0.0F, red, green, blue, packedLight);
        vertex(vc, pose, r, -r, z, 1.0F, 0.0F, red, green, blue, packedLight);
        vertex(vc, pose, r, r, z, 1.0F, 1.0F, red, green, blue, packedLight);
        vertex(vc, pose, -r, r, z, 0.0F, 1.0F, red, green, blue, packedLight);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z,
                               float u, float v, float red, float green, float blue, int packedLight) {
        vc.addVertex(pose, x, y, z)
                .setColor(red, green, blue, VORTEX_ALPHA)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(0.0F, 0.0F, 1.0F);
    }
}
