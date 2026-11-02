package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * 「激光 / 轨道炮」光束的**客户端世界空间渲染器**（1.21.1 / NeoForge）。
 *
 * <h2>为什么不用粒子</h2>
 * 旧实现每 tick 在 {@code [head, head + 96]} 的竖直区间撒 84 枚发光尘，
 * 观感是「一根糊起来的尘柱」而不是光线。现在服务端每 tick 只下发一条
 * {@link com.merlinkitsune.astral_dice.network.BeamFxPayload}（两端点 + 颜色 + 线宽 + ttl），
 * 客户端在 {@code Stage.AFTER_ENTITIES} 用**交叉四边形**（billboard，面向相机 ± 垂直方向各一片）
 * 画出真实光线：激光 = 极细红线，轨道炮 = 金色信标光柱。
 *
 * <h2>生命周期</h2>
 * 条目按「from / to / 颜色 / 线宽」**内容去重**：服务端每 tick 重发同一条，客户端只把
 * {@code expireTick} 往后推（ttl 3 tick），因此不会累积；服务端停止下发后 3 tick 内自然过期。
 */
@EventBusSubscriber(modid = AstralDiceMod.MODID, value = Dist.CLIENT)
public final class BeamFxRenderer {

    /** 激光贴图：纯白 1×1（{@code textures/special/blank.png}），靠顶点色上色。 */
    private static final Identifier LASER_TEXTURE =
            Identifier.fromNamespaceAndPath(AstralDiceMod.MODID, "textures/special/blank.png");
    /** 轨道炮光柱贴图：原版信标光束（随原版资源加载，无需拷贝进本模组）。 */
    private static final Identifier BEACON_TEXTURE =
            Identifier.fromNamespaceAndPath("minecraft", "textures/entity/beacon_beam.png");

    /** 轨道炮颜色（与 {@code combat/DamageCardFxScheduler.BEACON_COLOR} 同值）：用它选贴图。 */
    private static final int BEACON_COLOR = 0xFFCF4A;
    /** 信标贴图纵向平铺周期（格）：原版光柱每 8 格重复一次纹理。 */
    private static final double BEACON_V_TILE = 8.0D;

    /**
     * 激光 pipeline：实体着色器 + **加色混合** + LEQUAL 深度测试 + **不写深度**、**关背面剔除**。
     *
     * <p>26.1.2 迁移：1.21.1 的 {@code RenderType.CompositeState} + {@code RenderStateShard.*} 在 26.1.2
     * 已整体删除（{@code client/renderer/RenderStateShard.java} 不存在），通道改由
     * 「{@code RenderPipeline} + {@code RenderType.create(name, RenderSetup)}」描述；
     * 本类照 {@code client/RenShieldRenderer} 的同形写法（同一线已迁完的样板）。
     */
    private static final RenderPipeline LASER_PIPELINE = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(AstralDiceMod.MODID, "pipeline/laser_beam"))
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withSampler("Sampler1")
            .withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
            .withCull(false)
            .build();

    /** 轨道炮光柱 pipeline：与激光同一套状态（信标光柱是加色光柱，也不剔除、不写深度）。 */
    private static final RenderPipeline BEACON_PIPELINE = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(AstralDiceMod.MODID, "pipeline/orbital_beam"))
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withSampler("Sampler1")
            .withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
            .withCull(false)
            .build();

    private static final RenderType LASER = createBeam("astral_dice_laser_beam", LASER_PIPELINE, LASER_TEXTURE);
    private static final RenderType BEACON = createBeam("astral_dice_orbital_beam", BEACON_PIPELINE, BEACON_TEXTURE);

    /** 待绘制光束（expireTick = 收到时的游戏刻 + ttl）。 */
    private static final List<Beam> BEAMS = new ArrayList<>();

    private BeamFxRenderer() {
    }

    private static RenderType createBeam(String name, RenderPipeline pipeline, Identifier texture) {
        // 贴图绑在 Sampler0 上（原 TextureStateShard 的等价物），lightmap + overlay 与原状态逐项对应。
        return RenderType.create(name,
                RenderSetup.builder(pipeline)
                        .withTexture("Sampler0", texture)
                        .useLightmap()
                        .useOverlay()
                        .bufferSize(256)
                        .createRenderSetup());
    }

    /** 注册两个自有 pipeline（模组事件总线；与 {@code client/RenShieldRenderer} 同一做法）。 */
    @SubscribeEvent
    public static void onRegisterRenderPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(LASER_PIPELINE);
        event.registerPipeline(BEACON_PIPELINE);
    }

    /**
     * 入列一段光束（由 {@code network/BeamFxPayload} 在客户端主线程调用）。
     *
     * <p>按内容去重：同一条光束被服务端每 tick 重发时只续期，不新增条目。
     */
    public static void add(double fromX, double fromY, double fromZ,
                           double toX, double toY, double toZ,
                           int colorArgb, float widthBlocks, int ttlTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long expireTick = mc.level.getGameTime() + Math.max(1, ttlTicks);
        synchronized (BEAMS) {
            for (Beam beam : BEAMS) {
                if (beam.sameAs(fromX, fromY, fromZ, toX, toY, toZ, colorArgb, widthBlocks)) {
                    beam.expireTick = expireTick;
                    return;
                }
            }
            BEAMS.add(new Beam(fromX, fromY, fromZ, toX, toY, toZ, colorArgb, widthBlocks, expireTick));
        }
    }

    @SubscribeEvent
    public static void onSubmitCustomGeometry(SubmitCustomGeometryEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        PoseStack poseStack = event.getPoseStack();
        if (poseStack == null) return;
        SubmitNodeCollector collector = event.getSubmitNodeCollector();
        if (collector == null) return;
        long now = mc.level.getGameTime();
        List<Beam> beams;
        synchronized (BEAMS) {
            BEAMS.removeIf(beam -> beam.expireTick < now);
            // 列表为空 ⇒ 直接返回：不提交任何自定义几何
            if (BEAMS.isEmpty()) return;
            beams = new ArrayList<>(BEAMS);
        }

        Vec3 cam = mc.gameRenderer.getMainCamera().position();
        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        try {
            for (Beam beam : beams) {
                boolean beacon = beam.color == BEACON_COLOR;
                collector.submitCustomGeometry(poseStack, beacon ? BEACON : LASER,
                        (pose, buffer) -> emitBeam(buffer, pose, beam, cam, beacon));
            }
        } finally {
            poseStack.popPose();
        }
    }

    /**
     * 画一条光束：以 {@code axis} 为轴，取「轴 × 视线」与其垂直方向各铺一片四边形，两片交叉成十字。
     * 任意视角下总有一片近乎正对相机 ⇒ 观感是一条有厚度的光线而不会消失。
     */
    private static void emitBeam(VertexConsumer vc, PoseStack.Pose pose, Beam beam, Vec3 cam, boolean beacon) {
        Vec3 from = beam.from();
        Vec3 to = beam.to();
        Vec3 delta = to.subtract(from);
        double len = delta.length();
        // 两端点重合：静默跳过（不画退化的四边形）
        if (len < 1.0E-4D) return;
        Vec3 axis = delta.scale(1.0D / len);
        Vec3 mid = from.add(delta.scale(0.5D));
        Vec3 view = cam.subtract(mid);
        Vec3 side1 = axis.cross(view);
        if (side1.lengthSqr() < 1.0E-16D) {
            // 视线与轴平行（顺着光线看）⇒ 换一个参照方向，避免叉积退化成零向量
            side1 = axis.cross(new Vec3(0.0D, 1.0D, 0.0D));
            if (side1.lengthSqr() < 1.0E-16D) side1 = axis.cross(new Vec3(1.0D, 0.0D, 0.0D));
        }
        side1 = side1.normalize().scale(beam.width * 0.5D);
        Vec3 side2 = axis.cross(side1).normalize().scale(beam.width * 0.5D);

        float alpha = ((beam.color >>> 24) & 0xFF) / 255.0F;
        // 本仓颜色常量是 0xRRGGBB（alpha 位为 0）⇒ 不能让它把整条光线画成全透明
        if (alpha <= 0.01F) alpha = 1.0F;
        float red = ((beam.color >> 16) & 0xFF) / 255.0F;
        float green = ((beam.color >> 8) & 0xFF) / 255.0F;
        float blue = (beam.color & 0xFF) / 255.0F;
        // 信标贴图沿长度方向平铺；纯白激光贴图直接 0..1
        float v1 = beacon ? (float) (len / BEACON_V_TILE) : 1.0F;

        quad(vc, pose, from, to, side1, red, green, blue, alpha, v1, axis);
        quad(vc, pose, from, to, side2, red, green, blue, alpha, v1, axis);
    }

    /** 一片四边形：{@code a/b} 两端各向 ±side 展开半宽。 */
    private static void quad(VertexConsumer vc, PoseStack.Pose pose, Vec3 a, Vec3 b, Vec3 side,
                             float red, float green, float blue, float alpha, float v1, Vec3 normal) {
        Vec3 a0 = a.subtract(side);
        Vec3 a1 = a.add(side);
        Vec3 b0 = b.subtract(side);
        Vec3 b1 = b.add(side);
        vertex(vc, pose, a0, red, green, blue, alpha, 0.0F, 0.0F, normal);
        vertex(vc, pose, a1, red, green, blue, alpha, 1.0F, 0.0F, normal);
        vertex(vc, pose, b1, red, green, blue, alpha, 1.0F, v1, normal);
        vertex(vc, pose, b0, red, green, blue, alpha, 0.0F, v1, normal);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, Vec3 p,
                               float red, float green, float blue, float alpha,
                               float u, float v, Vec3 normal) {
        vc.addVertex(pose, (float) p.x, (float) p.y, (float) p.z)
                .setColor(red, green, blue, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightCoordsUtil.FULL_BRIGHT)
                .setNormal((float) normal.x, (float) normal.y, (float) normal.z);
    }

    /** 一条待绘制光束（坐标已在世界空间冻结）。 */
    private static final class Beam {
        private final double fromX;
        private final double fromY;
        private final double fromZ;
        private final double toX;
        private final double toY;
        private final double toZ;
        private final int color;
        private final float width;
        private long expireTick;

        private Beam(double fromX, double fromY, double fromZ,
                     double toX, double toY, double toZ,
                     int color, float width, long expireTick) {
            this.fromX = fromX;
            this.fromY = fromY;
            this.fromZ = fromZ;
            this.toX = toX;
            this.toY = toY;
            this.toZ = toZ;
            this.color = color;
            this.width = width;
            this.expireTick = expireTick;
        }

        private boolean sameAs(double fromX, double fromY, double fromZ,
                               double toX, double toY, double toZ,
                               int otherColor, float otherWidth) {
            return color == otherColor && width == otherWidth
                    && Double.compare(this.fromX, fromX) == 0 && Double.compare(this.fromY, fromY) == 0
                    && Double.compare(this.fromZ, fromZ) == 0 && Double.compare(this.toX, toX) == 0
                    && Double.compare(this.toY, toY) == 0 && Double.compare(this.toZ, toZ) == 0;
        }

        private Vec3 from() {
            return new Vec3(fromX, fromY, fromZ);
        }

        private Vec3 to() {
            return new Vec3(toX, toY, toZ);
        }
    }
}
