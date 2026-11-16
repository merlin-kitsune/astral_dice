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
 * <h2>地面门面（2026-11-10 用户验收改版）</h2>
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
 */
public class InkShadowPortalRenderer extends GeoEntityRenderer<InkShadowPortalEntity> {

    /** 模型整体缩放（待用户验收调整） */
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

    /** 未配对门大约缩到 95%（半透明降级表达） */
    private static final float PENDING_RENDER_SCALE = 0.95F;

    private static final ResourceLocation SURFACE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "textures/entity/inkshadow_portal_surface.png");

    public InkShadowPortalRenderer(EntityRendererProvider.Context context) {
        super(context, new InkShadowPortalModel());
        withScale(MODEL_SCALE);
    }

    /**
     * 模型走<b>不透明 cutout</b> 渲染（2026-11-10 光影闪修）。
     *
     * <p>原先用 {@code entityTranslucent}：门环是大量<b>零厚度薄片</b>且彼此只差几毫米高度，
     * 半透明渲染不写深度、只能靠每帧的距离排序决定先后；开启 Iris/Complementary 光影后排序在
     * 延迟管线里不稳定 ⇒ 门环与门面交替压盖，表现为闪烁（肉眼像忽隐忽现的暗色薄片）。
     * 改 cutout 后模型进入不透明通道、正常写深度，排序问题消失；贴图里 0&lt;a&lt;1 的边缘会按
     * 阈值裁掉（32×32 像素贴图视觉上无可感差异）。PENDING 态不再依赖整体 alpha（用缩放表达）。
     */
    @Override
    public RenderType getRenderType(InkShadowPortalEntity animatable, ResourceLocation texture,
                                    MultiBufferSource bufferSource, float partialTick) {
        // 保留不剔除背面：门环本身是零厚度薄片，很多面向只有单侧 UV，剔了会漏面。
        // 眼睛闪烁的真正来源是「黄色眼睛所在的中间大平板」与两片侧翼只差 0.015 单位（约 0.15 mm）
        // 几乎共面 → z-fighting，已在 geo 里把两片侧翼后移 0.15 单位解决。
        return RenderType.entityCutoutNoCull(texture);
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
            // 门面只是「贴在模型内框上方」的半透明自发光片：模型改 cutout 后已写深度，
            // 门面（y=SURFACE_Y，高于模型所有薄片）走正常深度测试即可，不需要再动全局渲染状态。
            // 旧版在这里 endBatch() + disableDepthTest()，在 Iris 延迟管线里会打乱通道顺序，是闪烁源之一。
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

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z,
                               float u, float v, int packedLight) {
        vc.addVertex(pose, x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(0.0F, 1.0F, 0.0F);
    }
}
