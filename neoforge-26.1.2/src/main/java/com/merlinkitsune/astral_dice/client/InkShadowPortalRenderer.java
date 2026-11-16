package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.entity.InkShadowPortalEntity;
import com.merlinkitsune.starengine.geckolib.constant.dataticket.DataTicket;
import com.merlinkitsune.starengine.geckolib.renderer.GeoEntityRenderer;
import com.merlinkitsune.starengine.geckolib.renderer.base.GeoRenderState;
import com.merlinkitsune.starengine.geckolib.renderer.base.RenderPassInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;

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
 *
 * <h2>26.1.2 线（GeckoLib 5）迁移要点（javap 实证，与 26 线 {@code SouriDrinkRenderer} 同形）</h2>
 * <ul>
 *   <li>{@code GeoEntityRenderer} 在 v5 有<b>两个</b>类型参数
 *       {@code <T extends Entity & GeoAnimatable, R extends EntityRenderState>}；本实体是
 *       {@code Entity} ⇒ {@code R = EntityRenderState}；</li>
 *   <li>v5 <b>没有</b>可覆写 {@code render(PoseStack, MultiBufferSource, int)}（26.x 走
 *       {@code submit(R, PoseStack, SubmitNodeCollector, CameraRenderState)}）⇒ 原先包在
 *       {@code super.render} 外层的「抬升 + 未配对缩放」改到 {@link #adjustRenderPose}，
 *       对 {@code RenderPassInfo#poseStack()} 施加；</li>
 *   <li>原先 {@code super.render(...)} 之后画的贴地门面改到 {@link #postRenderPass}
 *       —— 字节码实证 {@code performRenderPass} 在 {@code postRenderPass} 之前已
 *       {@code poseStack.popPose()}，故此处回到实体外层姿态（不再带 withScale 的 1.6 倍），
 *       与 1.21.1 里 {@code renderSurface} 用的坐标系一致（门面半径因此不需按 1.6 换算）；</li>
 *   <li>自定义四边形用 {@code SubmitNodeCollector#submitCustomGeometry(PoseStack, RenderType,
 *       CustomGeometryRenderer)} 提交，回调里拿到的 {@code PoseStack.Pose} 已含提交时的变换；</li>
 *   <li>{@code RenderType} 静态工厂迁到 {@code RenderTypes}（{@code RenderType.create} 已删），
 *       {@code LightTexture.FULL_BRIGHT} 改为 {@link LightCoordsUtil#FULL_BRIGHT}；</li>
 *   <li>渲染期需要的「是否已配对」状态无法在 v5 渲染回调里直接拿到实体 ⇒ 在
 *       {@link #extractRenderState} 里以 {@link DataTicket} 写进渲染态，再由
 *       {@link #adjustRenderPose}/{@link #postRenderPass} 读取。</li>
 * </ul>
 */
public class InkShadowPortalRenderer extends GeoEntityRenderer<InkShadowPortalEntity, EntityRenderState> {

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

    /** 渲染态数据票：本帧渲染的这扇门是否已成对激活（门面只对激活门渲染）。 */
    private static final DataTicket<Boolean> ACTIVE =
            DataTicket.create("astral_dice_inkshadow_portal_active", Boolean.class);

    private static final Identifier SURFACE_TEXTURE =
            Identifier.fromNamespaceAndPath(AstralDiceMod.MODID, "textures/entity/inkshadow_portal_surface.png");

    public InkShadowPortalRenderer(EntityRendererProvider.Context context) {
        super(context, new InkShadowPortalModel());
        withScale(MODEL_SCALE);
    }

    /** 把「是否已配对」写进渲染态（v5 的渲染回调里已拿不到实体）。 */
    @Override
    public void extractRenderState(InkShadowPortalEntity entity, EntityRenderState renderState, float partialTick) {
        super.extractRenderState(entity, renderState, partialTick);
        ((GeoRenderState) renderState).addGeckolibData(ACTIVE, entity.isActive());
    }

    /**
     * 模型走<b>不透明 cutout</b> 渲染（2026-11-10 光影闪修）。
     *
     * <p>原先用 {@code entityTranslucent}：门环是大量<b>零厚度薄片</b>且彼此只差几毫米高度，
     * 半透明渲染不写深度、只能靠每帧的距离排序决定先后；开启 Iris/Complementary 光影后排序在
     * 延迟管线里不稳定 ⇒ 门环与门面交替压盖，表现为闪烁（肉眼像忽隐忽现的暗色薄片）。
     * 改 cutout 后模型进入不透明通道、正常写深度，排序问题消失；贴图里 0&lt;a&lt;1 的边缘会按
     * 阈值裁掉（32×32 像素贴图视觉上无可感差异）。PENDING 态不再依赖整体 alpha（用缩放表达）。
     *
     * <p>v5 签名：{@code getRenderType(R renderState, Identifier texture)}（无 MultiBufferSource /
     * partialTick）；{@code RenderTypes.entityCutout(texture)} 即旧 {@code entityCutoutNoCull}
     * （保留不剔除背面：门环是零厚度薄片，很多面向只有单侧 UV，剔了会漏面）。
     */
    @Override
    public RenderType getRenderType(EntityRenderState renderState, Identifier texture) {
        return RenderTypes.entityCutout(texture);
    }

    /** 抬升贴地 + 未配对缩放（替代 1.21.1 里包住 {@code super.render} 的那层 pose 变换）。 */
    @Override
    @SuppressWarnings("rawtypes")
    public void adjustRenderPose(RenderPassInfo passInfo) {
        super.adjustRenderPose(passInfo);
        // 注：v5 的 RenderPassInfo<R extends GeoRenderState> 与
        // GeoEntityRenderer<T, R extends EntityRenderState> 边界不一致（GeoRenderState 由 mixin
        // 注入到 EntityRenderState），这里沿用 26 线既有做法（SouriDrinkRenderer）用原始类型绕过。
        passInfo.poseStack().translate(0.0D, GROUND_LIFT, 0.0D);
        if (!Boolean.TRUE.equals(passInfo.getOrDefaultGeckolibData(ACTIVE, Boolean.FALSE))) {
            passInfo.poseStack().scale(PENDING_RENDER_SCALE, PENDING_RENDER_SCALE, PENDING_RENDER_SCALE);
        }
    }

    /**
     * 贴地门面：黑色星盘，只在成对激活时出现。
     *
     * <p>v5 里 {@code postRenderPass} 在 {@code performRenderPass} 的 {@code popPose()} 之后调用，
     * 因此坐标系 = 实体外层（无 withScale 的 1.6 倍）；抬升需把 {@link #GROUND_LIFT} 补回来。
     */
    @Override
    @SuppressWarnings("rawtypes")
    public void postRenderPass(RenderPassInfo passInfo, SubmitNodeCollector collector) {
        super.postRenderPass(passInfo, collector);
        if (!Boolean.TRUE.equals(passInfo.getOrDefaultGeckolibData(ACTIVE, Boolean.FALSE))) return;
        // 年龄含 partialTick（GeoRenderState#getAnimatableAge），等价 1.21.1 的 tickCount + partialTick
        double age = ((GeoRenderState) passInfo.renderState()).getAnimatableAge();
        float spin = (float) age * 360.0F / SURFACE_SPIN_TICKS;
        PoseStack poseStack = passInfo.poseStack();
        poseStack.pushPose();
        poseStack.translate(0.0D, GROUND_LIFT + SURFACE_Y, 0.0D);
        poseStack.mulPose(Axis.YP.rotationDegrees(spin));
        // 自发光：门面不受方块光照影响，夜里/洞穴里也是亮的（末地传送门样式的观感）。
        // 正反两面各画一次：从下方仰视也能看到，且不依赖渲染层的 cull 设置。
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucentEmissive(SURFACE_TEXTURE),
                (pose, vc) -> {
                    quad(vc, pose, SURFACE_RADIUS, 0.0F, LightCoordsUtil.FULL_BRIGHT);
                    quad(vc, pose, SURFACE_RADIUS, -0.001F, LightCoordsUtil.FULL_BRIGHT);
                });
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
