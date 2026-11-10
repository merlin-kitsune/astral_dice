package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.entity.InkShadowPortalEntity;
import com.merlinkitsune.starengine.geckolib.renderer.GeoEntityRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * 【传送门】渲染器：GeckoLib 模型 + 门面中央的黑色漩涡面片。
 *
 * <h2>显示常量（第一版取值，均「待用户验收调整」）</h2>
 * <ul>
 *   <li>{@link #MODEL_SCALE} = 1.6：模型整体放大（素材几何约 1.19×0.37×0.94 格）；</li>
 *   <li>{@link #MODEL_Y_OFFSET} = 0.05：贴近地面又不穿地；</li>
 *   <li>{@link #PENDING_ALPHA} = 0.45：起始门半透明度（规格「起始门在终点门前无效、半透明」）；</li>
 *   <li>{@link #VORTEX_RADIUS} / {@link #VORTEX_PERIOD_TICKS} / {@link #VORTEX_ALPHA}：漩涡半径（格）、
 *       旋转一周所需 tick、透明度。</li>
 * </ul>
 *
 * <h2>与规格的偏差（已写入报告§4）</h2>
 * 规格 §3.4 写「{@code Axis.YP.rotationDegrees(tickCount*360f/VORTEX_PERIOD_TICKS)}」——Y 轴旋转会让
 * 面片随玩家视角变化而侧对甚至消失；这里改为「相机 billboard（始终面向玩家）+ 面内 Z 轴自旋」，
 * 自旋周期仍为 {@link #VORTEX_PERIOD_TICKS}。PENDING 门不画漩涡，且整模缩放 0.95 作为
 * 「不可用」的弱化提示（1.20.1 的 GeckoLib 渲染器没有整模 alpha 钩子，只能退化处理）。
 */
public class InkShadowPortalRenderer extends GeoEntityRenderer<InkShadowPortalEntity> {

    /** 模型整体缩放——待用户验收调整 */
    public static final float MODEL_SCALE = 1.6F;
    /** 模型贴地偏移——待用户验收调整 */
    public static final double MODEL_Y_OFFSET = 0.05D;
    /** 起始门半透明度（0~1）——待用户验收调整 */
    public static final float PENDING_ALPHA = 0.45F;
    /** 起始门整体缩放（无整模 alpha 钩子时的退化提示）——待用户验收调整 */
    public static final float PENDING_SCALE = 0.95F;
    /** 漩涡面片半径（格）——待用户验收调整 */
    public static final float VORTEX_RADIUS = 0.42F;
    /** 漩涡中心高度（实体脚底起算，格）——待用户验收调整 */
    public static final double VORTEX_Y = 0.30D;
    /** 漩涡旋转一周的 tick 数——待用户验收调整 */
    public static final float VORTEX_PERIOD_TICKS = 90.0F;
    /** 漩涡透明度（0~1）——待用户验收调整 */
    public static final float VORTEX_ALPHA = 0.75F;

    private static final ResourceLocation VORTEX_TEXTURE =
            new ResourceLocation("astral_dice", "textures/entity/inkshadow_vortex.png");
    /** 漩涡染色：近黑的紫（0xFF1A1020 的 RGB 部分） */
    private static final int VORTEX_RED = 0x1A;
    private static final int VORTEX_GREEN = 0x10;
    private static final int VORTEX_BLUE = 0x20;

    public InkShadowPortalRenderer(EntityRendererProvider.Context context) {
        super(context, new InkShadowPortalModel());
    }

    @Override
    public void render(InkShadowPortalEntity animatable, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();
        poseStack.translate(0.0D, MODEL_Y_OFFSET, 0.0D);
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
        if (!animatable.isActive()) {
            poseStack.scale(PENDING_SCALE, PENDING_SCALE, PENDING_SCALE);
        }
        // 自发光：传送门在暗处也保持全亮
        super.render(animatable, entityYaw, partialTick, poseStack, bufferSource, 0xF000F0);
        poseStack.popPose();

        if (animatable.isActive()) {
            renderVortex(animatable, partialTick, poseStack, bufferSource);
        }
    }

    /** 门面中央的独立旋转漩涡：双面 billboard 面片，避免被模型骨骼遮挡时完全不可见。 */
    private void renderVortex(InkShadowPortalEntity animatable, float partialTick,
                              PoseStack poseStack, MultiBufferSource bufferSource) {
        float spin = (animatable.tickCount + partialTick) * 360.0F / VORTEX_PERIOD_TICKS;
        int alpha = (int) (VORTEX_ALPHA * 255.0F) & 0xFF;
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityTranslucent(VORTEX_TEXTURE));

        poseStack.pushPose();
        poseStack.translate(0.0D, VORTEX_Y, 0.0D);
        poseStack.mulPose(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
        poseStack.mulPose(Axis.ZP.rotationDegrees(spin));
        Matrix4f pose = poseStack.last().pose();
        float r = VORTEX_RADIUS;

        // 正面
        vertex(consumer, pose, -r, -r, 0.0F, 1.0F, 0.0F, 1.0F, alpha);
        vertex(consumer, pose, r, -r, 1.0F, 1.0F, 0.0F, 1.0F, alpha);
        vertex(consumer, pose, r, r, 1.0F, 0.0F, 0.0F, 1.0F, alpha);
        vertex(consumer, pose, -r, r, 0.0F, 0.0F, 0.0F, 1.0F, alpha);
        // 背面（反向绕序 + 反向法线）
        vertex(consumer, pose, -r, r, 0.0F, 0.0F, 0.0F, -1.0F, alpha);
        vertex(consumer, pose, r, r, 1.0F, 0.0F, 0.0F, -1.0F, alpha);
        vertex(consumer, pose, r, -r, 1.0F, 1.0F, 0.0F, -1.0F, alpha);
        vertex(consumer, pose, -r, -r, 0.0F, 1.0F, 0.0F, -1.0F, alpha);

        poseStack.popPose();
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose, float x, float y,
                               float u, float v, float normalX, float normalY, int alpha) {
        consumer.vertex(pose, x, y, 0.0F)
                .color(VORTEX_RED, VORTEX_GREEN, VORTEX_BLUE, alpha)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(normalX, normalY, 0.0F)
                .endVertex();
    }
}
