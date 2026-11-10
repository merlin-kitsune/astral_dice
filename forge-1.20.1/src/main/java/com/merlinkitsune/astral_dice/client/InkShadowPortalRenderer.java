package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.entity.InkShadowPortalEntity;
import com.merlinkitsune.starengine.geckolib.renderer.GeoEntityRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * 暗影之门渲染器：GeckoLib 模型（{@code inkeshadoweffect}）+ <b>平铺在地面的门面</b>。
 *
 * <h2>地面门面（2026-11-10 用户验收改版，与 1.21.1 同形）</h2>
 * 旧版是朝向相机的竖立漩涡公告板；用户要求「黑色背景 + 平铺地面 + 末地传送门样式，且不要超出模型外框」，
 * 因此改为一片贴在门底（{@link #SURFACE_Y}）的水平四边形：
 * <ul>
 *   <li>贴图 {@code textures/entity/inkshadow_portal_surface.png}：纯黑底 + 紫色星点（自带黑色背景，
 *       不再需要额外的黑板），整体慢速绕 Y 轴自转（{@link #SURFACE_SPIN_TICKS}）来产生流动感；</li>
 *   <li>半径 {@link #SURFACE_RADIUS} 控制在内框范围（模型本体 x 向半宽 ≈ 0.95 格）；</li>
 *   <li><b>只在成对激活（{@code isActive()}）时渲染</b>：未配对的起始门不显示门面贴图。</li>
 * </ul>
 *
 * <p>模型本身贴地：geo 最低点 -0.9945（单位）× 1.6 / 16 ≈ -0.0995 格，故整体抬升
 * {@link #GROUND_LIFT} 让底边正好落在地面方块上。
 *
 * <p><b>1.20.1 平台差异</b>：{@code GeoEntityRenderer} 没有 1.21 的 {@code getRenderType(entity, texture,
 * bufferSource, partialTick)} 钩子，渲染层由 {@code GeoModel#getRenderType(entity, texture)} 决定；
 * 该默认值本就是 {@code entityCutoutNoCull}（=1.21 线在这里的显式选择），故本类不再覆写，只在
 * {@link InkShadowPortalModel} 里留了显式覆写点。{@link VertexConsumer} 仍是 1.20.1 的
 * {@code vertex(pose,...).color(...).uv(...).overlayCoords(...).uv2(...).normal(...).endVertex()} 写法。
 */
public class InkShadowPortalRenderer extends GeoEntityRenderer<InkShadowPortalEntity> {

    /** 模型整体缩放（与 1.21.1 一致） */
    public static final float MODEL_SCALE = 1.6F;

    /**
     * 模型抬升：抵消 geo 最低点 -0.9945 单位（×1.6/16 ≈ -0.0995 格），使门底贴地；
     * 额外多抬 2 cm，让模型里那两层<b>零厚度地面贴片</b>（LiedInner 约 6 mm、LiedOuter 约 2 mm）
     * 离开方块顶面，避免在远处与草地共面导致 z-fighting 闪斑。
     */
    public static final double GROUND_LIFT = 0.12D;

    /** 地面门面离方块顶面的高度（高于模型所有薄片，避免与门框 z-fighting；模型薄片最高约 0.0075 格） */
    public static final float SURFACE_Y = 0.03F;

    /** 地面门面半径（格）：模型 x 向半宽 ≈ 0.593 × 1.6 ≈ 0.95，取 0.62 保证不超出门框 */
    public static final float SURFACE_RADIUS = 0.62F;

    /** 地面门面绕 Y 轴自转一周所需 tick（贴图本身是静态星图，靠慢转产生流动感） */
    public static final int SURFACE_SPIN_TICKS = 200;

    /** 未配对门大约缩到 95%（1.20.1 的 GeckoLib 渲染器没有整模 alpha 钩子，用缩放做「不可用」的弱化提示） */
    private static final float PENDING_RENDER_SCALE = 0.95F;

    private static final ResourceLocation SURFACE_TEXTURE =
            new ResourceLocation(AstralDiceMod.MODID, "textures/entity/inkshadow_portal_surface.png");

    public InkShadowPortalRenderer(EntityRendererProvider.Context context) {
        super(context, new InkShadowPortalModel());
        withScale(MODEL_SCALE);
    }

    @Override
    public void render(InkShadowPortalEntity animatable, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();
        poseStack.translate(0.0D, GROUND_LIFT, 0.0D);
        if (!animatable.isActive()) {
            poseStack.scale(PENDING_RENDER_SCALE, PENDING_RENDER_SCALE, PENDING_RENDER_SCALE);
        }
        super.render(animatable, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        if (animatable.isActive()) {
            // 门面只是「贴在模型内框上方」的半透明自发光片：模型走 cutout 已写深度，
            // 门面（y=SURFACE_Y，高于模型所有薄片）走正常深度测试即可。
            renderSurface(animatable, poseStack, bufferSource, packedLight, partialTick);
        }
        poseStack.popPose();
    }

    /** 贴地门面：黑色星盘，只在成对激活时出现。 */
    private void renderSurface(InkShadowPortalEntity entity, PoseStack poseStack, MultiBufferSource bufferSource,
                               int packedLight, float partialTick) {
        float spin = (entity.tickCount + partialTick) * 360.0F / SURFACE_SPIN_TICKS;
        poseStack.pushPose();
        poseStack.translate(0.0D, SURFACE_Y, 0.0D);
        poseStack.mulPose(Axis.YP.rotationDegrees(spin));
        // 自发光：门面不受方块光照影响，夜里/洞穴里也是亮的（末地传送门样式的观感）
        VertexConsumer vc = bufferSource.getBuffer(RenderType.entityTranslucentEmissive(SURFACE_TEXTURE));
        PoseStack.Pose pose = poseStack.last();
        float r = SURFACE_RADIUS;
        // 正反两面各画一次：从下方仰视也能看到，且不依赖渲染层的 cull 设置
        quad(vc, pose, r, 0.0F, LightTexture.FULL_BRIGHT);
        quad(vc, pose, r, -0.001F, LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose pose, float r, float y, int packedLight) {
        vertex(vc, pose, -r, y, -r, 0.0F, 0.0F, packedLight);
        vertex(vc, pose, -r, y, r, 0.0F, 1.0F, packedLight);
        vertex(vc, pose, r, y, r, 1.0F, 1.0F, packedLight);
        vertex(vc, pose, r, y, -r, 1.0F, 0.0F, packedLight);
    }

    /** 1.20.1 顶点写法（1.21 的 {@code addVertex/setColor/setUv/...} 在本线不存在）。 */
    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z,
                               float u, float v, int packedLight) {
        vc.vertex(pose.pose(), x, y, z)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(packedLight)
                .normal(0.0F, 1.0F, 0.0F)
                .endVertex();
    }
}
