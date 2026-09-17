package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * 目标选择器边框的「可见外框」实测（2026-09-18 t16 缺陷修复）。
 *
 * <h2>为什么要它（根因，代码级证据）</h2>
 * 原实现直接用 **碰撞盒** {@code entity.getBoundingBox()}（见 {@link TargetSelectionHighlighter} 旧版各调用点），
 * 而 1.21.1 里**没有任何**能覆盖生物可见模型外框的原版 API：
 * <ul>
 *   <li>{@code Entity#getBoundingBoxForCulling()}（Entity.java:3030-3032）= {@code getBoundingBox()} 碰撞盒；</li>
 *   <li>{@code LivingEntity#getBoundingBoxForCulling()}（LivingEntity.java:3631-3637）只在戴龙首时固定 +0.5，其余同碰撞盒；</li>
 *   <li>{@code EntityRenderer#shouldRender} 用的剔除盒（EntityRenderer.java:58）也是碰撞盒 + 固定 0.5；
 *       1.21.1 的 {@code EntityRenderer}/{@code LivingEntityRenderer} 里**没有**基于模型的包围盒方法
 *       （{@code LivingEntityRenderer} 只暴露模型本身：{@code getModel()}，LivingEntityRenderer.java:47-50）；</li>
 *   <li>碰撞盒尺寸来自 {@code EntityDimensions}（多数模型大于它：僵尸 0.6 宽而双臂张到约 1.0；
 *       蜘蛛 1.4 宽而八条腿张到约 2.0；马颈/头高于其碰撞盒）。</li>
 * </ul>
 * ⇒ 只按碰撞盒画框必然「小于生物」。本类改为**实测可见外框**：在实体自己的渲染回调
 * {@link RenderLivingEvent.Post} 里，把该实体**当前姿态**的模型画进一个「只记坐标、不碰 GL」的
 * {@link VertexConsumer} 探针，取所有顶点变换后的世界坐标 min/max —— 得到的就是本帧真实可见外框，
 * 天然包含：模型几何（腿/臂/角）、部位动画旋转、幼年体的模型内缩放（{@code AgeableListModel#renderToBuffer}
 * 的 {@code young} 分支，AgeableListModel.java:53-74，其 {@code model.young} 由
 * {@code LivingEntityRenderer#render}:58 设置）、以及 {@code SCALE} 属性（{@code getScale()}，
 * LivingEntity.java:552-555，由 {@code LivingEntityRenderer#render}:97-98 应用）。
 *
 * <h2>世界变换怎么来的</h2>
 * {@code RenderLivingEvent.Post} 在 {@code poseStack.popPose()} 之后派发（LivingEntityRenderer.java:137-139），
 * 故事件里的 PoseStack 已不含模型变换；本类按 {@code LivingEntityRenderer#render} 的原样管道**重建**底座变换
 * （见 {@link #measure}，逐条对应 :97-103），再让模型自己往探针里画 —— 不猜尺寸、不加固定偏移。
 *
 * <h2>已知未覆盖（不影响本缺陷的验收口径，如实登记）</h2>
 * 渲染器各自覆写的 {@code scale()}/{@code setupRotations()} 分支（如充能苦力怕的 2× 脉冲、死亡/睡眠/旋转攻击姿态、
 * 1.21.1 唯一的 {@code CreeperRenderer#scale} 等）无法在本类重放；这类实体退化为「碰撞盒 ∪ 实测外框」，
 * 仍不小于碰撞盒（绝不内缩）。未渲染（视锥外/被其它模组取消渲染）的实体同样退化到碰撞盒。
 */
@EventBusSubscriber(modid = AstralDiceMod.MODID, value = Dist.CLIENT)
public final class TargetOutlineCapture {
    private static final Logger LOGGER = LoggerFactory.getLogger(TargetOutlineCapture.class);

    /** 实体 → 本帧实测的可见外框（世界坐标）；IdentityHashMap：按实体对象身份取键 */
    private static final Map<LivingEntity, AABB> CAPTURED = new IdentityHashMap<>();
    /** 读数去重状态：同一实体的读数最快每 {@link #LOG_INTERVAL_TICKS} tick 一条，且总数有上限 */
    private static final Map<LivingEntity, LogState> LOGGED = new IdentityHashMap<>();
    /** 同一实体的读数最小间隔（tick）：模型动画会让外框每帧微动，不设间隔就会变成每帧刷屏 */
    private static final int LOG_INTERVAL_TICKS = 20;
    /** 本次会话读数行上限（只服务取证，不刷屏；触顶时补一行显式提示） */
    private static final int MAX_LOG_LINES = 80;
    /** 缓存上限（会话结束会清空，这里再加一道保险，绝不无界增长） */
    private static final int MAX_ENTRIES = 64;

    private static long loggedLines;
    private static boolean logCapReported;

    private TargetOutlineCapture() {
    }

    @SubscribeEvent
    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?> event) {
        LivingEntity entity = event.getEntity();
        if (!TargetSelectionClient.isTracked(entity)) return;
        AABB measured = measure(event.getRenderer(), entity, event.getPartialTick());
        if (measured == null) return;
        if (CAPTURED.size() >= MAX_ENTRIES && !CAPTURED.containsKey(entity)) {
            CAPTURED.clear();
            LOGGED.clear();
        }
        CAPTURED.put(entity, measured);
        logOnce(entity, measured);
    }

    /**
     * 该实体本帧的可见外框 = **碰撞盒 ∪ 实测模型外框**（取并集 ⇒ 任何情况下都不小于碰撞盒，绝不内缩）；
     * 没有实测数据（未渲染 / 非活体渲染器）时退化为碰撞盒。
     */
    public static AABB outlineOf(LivingEntity entity) {
        AABB collision = entity.getBoundingBox();
        AABB measured = CAPTURED.get(entity);
        return measured == null ? collision : collision.minmax(measured);
    }

    /** 会话结束清空缓存（由 {@link TargetSelectionHighlighter} 在无目标可画时调用） */
    public static void clearIfIdle() {
        if (TargetSelectionClient.isActive()) return;
        if (!CAPTURED.isEmpty() || !LOGGED.isEmpty() || loggedLines != 0 || logCapReported) {
            CAPTURED.clear();
            LOGGED.clear();
            loggedLines = 0;
            logCapReported = false;
        }
    }

    /**
     * 把模型按该实体当前姿态画进坐标探针，返回世界坐标下的可见外框。
     *
     * <p>底座变换逐条对应 {@code LivingEntityRenderer#render}（1.21.1）：
     * <ol>
     *   <li>实体插值坐标位移 —— {@code EntityRenderDispatcher#render} 的 {@code poseStack.translate(d2,d3,d0)}；</li>
     *   <li>{@code poseStack.scale(getScale())} —— :97-98（SCALE 属性）；</li>
     *   <li>{@code setupRotations} 的默认 {@code mulPose(Axis.YP, 180 - yBodyRot)} —— :181-188；</li>
     *   <li>{@code scale(-1,-1,1)} 的镜像 —— :101；</li>
     *   <li>{@code translate(0,-1.501,0)}（模型空间 y=24 落到脚底）—— :103。</li>
     * </ol>
     * 模型内部（幼年体缩放、部位旋转、可见性）由模型自己的 {@code renderToBuffer} 完成，本类不重放、不猜测。
     */
    private static AABB measure(LivingEntityRenderer<?, ?> renderer, LivingEntity entity, float partialTick) {
        if (renderer == null) return null;
        EntityModel<?> model = renderer.getModel();
        if (model == null) return null;

        float bodyRot = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        float scale = entity.getScale();

        PoseStack pose = new PoseStack();
        pose.translate(
                Mth.lerp(partialTick, entity.xo, entity.getX()),
                Mth.lerp(partialTick, entity.yo, entity.getY()),
                Mth.lerp(partialTick, entity.zo, entity.getZ()));
        pose.scale(scale, scale, scale);
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - bodyRot));
        pose.scale(-1.0F, -1.0F, 1.0F);
        pose.translate(0.0F, -1.501F, 0.0F);

        GeometryProbe probe = new GeometryProbe();
        model.renderToBuffer(pose, probe, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, -1);
        return probe.toBox();
    }

    /**
     * 读数：所用外框 vs 碰撞盒 vs 实测模型外框 + 缩放/幼年（成对打印，便于日志取证判定）。
     *
     * <p>去重规则：同一实体「首次测量」或「体型签名（类型/缩放/幼年）变化」立即打印；
     * 其余情况最快每 {@link #LOG_INTERVAL_TICKS} tick 一条，且本次会话总数不超过 {@link #MAX_LOG_LINES}
     * （触顶补一行显式提示）—— 模型动画会让外框每帧微动，不能按「值变了就打印」，否则就是每帧刷屏。
     */
    private static void logOnce(LivingEntity entity, AABB measured) {
        String signature = entity.getType().toShortString()
                + "|" + String.format("%.2f", entity.getScale())
                + "|" + entity.isBaby();
        LogState previous = LOGGED.get(entity);
        boolean typeChanged = previous != null && !previous.signature.equals(signature);
        if (previous != null && !typeChanged
                && (entity.tickCount - previous.tick) < LOG_INTERVAL_TICKS) {
            return;
        }
        if (loggedLines >= MAX_LOG_LINES) {
            if (!logCapReported) {
                logCapReported = true;
                LOGGER.debug("[Astral Dice][TargetSelectBounds] log cap reached ({} lines) — 后续读数不再打印（会话结束自动复位）", MAX_LOG_LINES);
            }
            return;
        }
        loggedLines++;
        LOGGED.put(entity, new LogState(entity.tickCount, signature));
        AABB collision = entity.getBoundingBox();
        AABB used = collision.minmax(measured);
        LOGGER.debug("[Astral Dice][TargetSelectBounds] entity={} scale={} baby={} collision={} model={} used={}",
                entity.getType().toShortString(), String.format("%.3f", entity.getScale()), entity.isBaby(),
                boxText(collision), boxText(measured), boxText(used));
    }

    /** 读数去重状态（记录上次打印的 tick 与体型签名） */
    private record LogState(long tick, String signature) {
    }

    private static String boxText(AABB box) {
        return String.format("[%.3f,%.3f,%.3f -> %.3f,%.3f,%.3f]",
                box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    /**
     * 只记录顶点坐标的 {@link VertexConsumer}：不碰任何 GL 状态、不建缓冲。
     *
     * <p>{@code ModelPart.Cube#compile} 先把顶点乘上部位/模型变换，再调 9 参
     * {@code addVertex(x,y,z,color,u,v,overlay,light,nx,ny,nz)}（ModelPart.java:352-366），
     * 该重载的默认实现最终落到 {@link #addVertex(float, float, float)}（VertexConsumer.java:28-41），
     * 故这里收到的已是**世界坐标**。
     */
    private static final class GeometryProbe implements VertexConsumer {
        private double minX = Double.MAX_VALUE;
        private double minY = Double.MAX_VALUE;
        private double minZ = Double.MAX_VALUE;
        private double maxX = -Double.MAX_VALUE;
        private double maxY = -Double.MAX_VALUE;
        private double maxZ = -Double.MAX_VALUE;

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            if (Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z)) {
                if (x < minX) minX = x;
                if (y < minY) minY = y;
                if (z < minZ) minZ = z;
                if (x > maxX) maxX = x;
                if (y > maxY) maxY = y;
                if (z > maxZ) maxZ = z;
            }
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float normalX, float normalY, float normalZ) {
            return this;
        }

        private AABB toBox() {
            if (minX > maxX || minY > maxY || minZ > maxZ) return null;
            return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        }
    }
}
