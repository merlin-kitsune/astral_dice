package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * 「激光 / 轨道炮」光束的**客户端世界空间渲染器**（1.20.1 / Forge）。
 *
 * <h2>为什么不用粒子</h2>
 * 旧实现每 tick 在 {@code [head, head + 96]} 的竖直区间撒 {@code BEAM_PARTICLES} 枚发光尘，
 * 观感是「一根糊起来的尘柱」而不是光线。现在服务端每 tick 只下发一条
 * {@code network.BeamFxPayload}（两端点 + 颜色 + 线宽 + ttl），客户端在
 * {@code Stage.AFTER_ENTITIES} 用**交叉四边形**（billboard，面向相机 ± 垂直方向各一片）
 * 画出真实光线：激光 = 极细红线，轨道炮 = 金色信标光柱。
 *
 * <h2>生命周期</h2>
 * 条目按「from / to / 颜色 / 线宽」**内容去重**：服务端每 tick 重发同一条，客户端只把
 * {@code expireTick} 往后推（ttl 3 tick），因此不会累积；服务端停止下发后 3 tick 内自然过期。
 */
@Mod.EventBusSubscriber(modid = AstralDiceMod.MODID, value = Dist.CLIENT)
public final class BeamFxRenderer {

    /** 激光贴图：纯白 1×1（{@code textures/special/blank.png}），靠顶点色上色。 */
    private static final ResourceLocation LASER_TEXTURE =
            new ResourceLocation(AstralDiceMod.MODID, "textures/special/blank.png");
    /** 轨道炮光柱贴图：原版信标光束（随原版资源加载，无需拷贝进本模组）。 */
    private static final ResourceLocation BEACON_TEXTURE =
            new ResourceLocation("minecraft", "textures/entity/beacon_beam.png");

    /** 轨道炮颜色（与 {@code combat/DamageCardFxScheduler.BEACON_COLOR} 同值）：用它选贴图。 */
    private static final int BEACON_COLOR = 0xFFCF4A;
    /** 信标贴图纵向平铺周期（格）：原版光柱每 8 格重复一次纹理。 */
    private static final double BEACON_V_TILE = 8.0D;

    private static final RenderType LASER = createBeam("astral_dice_laser_beam", LASER_TEXTURE);
    private static final RenderType BEACON = createBeam("astral_dice_orbital_beam", BEACON_TEXTURE);

    /** 待绘制光束（expireTick = 收到时的游戏刻 + ttl）。 */
    private static final List<Beam> BEAMS = new ArrayList<>();

    private BeamFxRenderer() {
    }

    /**
     * {@code RenderStateShard} 的「只读再导出」子类（与 {@code client/RenShieldRenderer} 同一手法）：
     * 1.20.1 的着色器 / 透明 / 深度 / 剔除 / 光照 / 覆盖等常量都是 {@code protected static final}，
     * 本类不在 {@code net.minecraft.client.renderer} 包内 ⇒ 靠子类按简单名访问继承的 protected 静态成员。
     */
    private static final class States extends RenderStateShard {
        private States() {
            super("astral_dice_beam_states", () -> {
            }, () -> {
            });
        }

        static final ShaderStateShard SHADER_ENTITY_TRANSLUCENT = RENDERTYPE_ENTITY_TRANSLUCENT_SHADER;
        static final TransparencyStateShard TRANSPARENCY_ADDITIVE = ADDITIVE_TRANSPARENCY;
        static final DepthTestStateShard DEPTH_TEST_LEQUAL = LEQUAL_DEPTH_TEST;
        static final WriteMaskStateShard WRITE_MASK_COLOR = COLOR_WRITE;
        static final CullStateShard CULL_NONE = NO_CULL;
        static final LightmapStateShard LIGHTMAP_ON = LIGHTMAP;
        static final OverlayStateShard OVERLAY_ON = OVERLAY;

        static TextureStateShard beamTexture(ResourceLocation location) {
            return new TextureStateShard(location, false, false);
        }
    }

    private static RenderType createBeam(String name, ResourceLocation texture) {
        // 状态与 client/RenShieldRenderer 的 createDome 同一套(半透明实体着色器 + 不写深度)，
        // 只多两条:不剔除(交叉四边形从背面也要可见) + 指定本光束自己的贴图。
        return RenderType.create(
                name,
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                256,
                false,
                true,
                RenderType.CompositeState.builder()
                        .setShaderState(States.SHADER_ENTITY_TRANSLUCENT)
                        .setTextureState(States.beamTexture(texture))
                        .setTransparencyState(States.TRANSPARENCY_ADDITIVE)
                        .setDepthTestState(States.DEPTH_TEST_LEQUAL)
                        .setWriteMaskState(States.WRITE_MASK_COLOR)
                        .setCullState(States.CULL_NONE)
                        .setLightmapState(States.LIGHTMAP_ON)
                        .setOverlayState(States.OVERLAY_ON)
                        .createCompositeState(false));
    }

    /**
     * 入列一段光束（由 {@code network.BeamFxPayload} 在客户端主线程调用）。
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
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long now = mc.level.getGameTime();
        List<Beam> beams;
        synchronized (BEAMS) {
            BEAMS.removeIf(beam -> beam.expireTick < now);
            // 列表为空 ⇒ 直接返回：不取 BufferSource、不碰任何 GL 状态
            if (BEAMS.isEmpty()) return;
            beams = new ArrayList<>(BEAMS);
        }
        PoseStack poseStack = event.getPoseStack();
        if (poseStack == null) return;

        Vec3 cam = event.getCamera().getPosition();
        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        PoseStack.Pose pose = poseStack.last();

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        boolean laserUsed = false;
        boolean beaconUsed = false;
        for (Beam beam : beams) {
            if (beam.color == BEACON_COLOR) {
                emitBeam(buffers.getBuffer(BEACON), pose, beam, cam, true);
                beaconUsed = true;
            } else {
                emitBeam(buffers.getBuffer(LASER), pose, beam, cam, false);
                laserUsed = true;
            }
        }
        // 只对真正用过的批次 endBatch（没用过的批次调用会白白刷一次空批）
        if (laserUsed) buffers.endBatch(LASER);
        if (beaconUsed) buffers.endBatch(BEACON);
        poseStack.popPose();
    }

    /**
     * 画一条光束：以 {@code axis} 为轴，取「轴 × 视线」与其垂直方向各铺一片四边形，两片交叉成十字。
     * 任意视角下总有一片近乎正对相机 ⇒ 观感是一条有厚度的光线而不会消失。
     */
    private static void emitBeam(VertexConsumer vc, PoseStack.Pose pose, Beam beam, Vec3 cam, boolean beacon) {
        Vec3 from = new Vec3(beam.fromX, beam.fromY, beam.fromZ);
        Vec3 to = new Vec3(beam.toX, beam.toY, beam.toZ);
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
        vc.vertex(pose.pose(), (float) p.x, (float) p.y, (float) p.z)
                .color(red, green, blue, alpha)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal((float) normal.x, (float) normal.y, (float) normal.z)
                .endVertex();
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
    }
}
