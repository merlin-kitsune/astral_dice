package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * 目标选择器边框的「可见外框」实测（26.1.2 移植版；语义基准 = 1.21.1 同名类）。
 *
 * <h2>与 1.21.1 基准的关系（逐条）</h2>
 * 1.21.1 侧在 {@code RenderLivingEvent.Post} 里**自己重放**底座变换
 * （{@code translate(插值坐标)} → {@code scale(getScale())} → {@code mulPose(YP, 180-bodyRot)}
 * → {@code scale(-1,-1,1)} → {@code translate(0,-1.501,0)}）再让模型画进坐标探针。
 *
 * <p>26.1.2 侧改为由 {@code mixin/client/LivingEntityOutlineCaptureMixin} 在
 * {@code LivingEntityRenderer#submit} 的 {@code popPose()} 之前调用 {@link #onSubmit}：
 * 此时 {@code poseStack} **就是原版自己算好的最终模型变换**（含 {@code setupRotations} 与各子类覆写的
 * {@code scale()}），因此本类**不再重放任何变换、不再硬编码任何偏移**（1.21.1 侧的
 * {@code Mth.lerp}/{@code Mth.rotLerp}/{@code -1.501} 全部删除）。语义等价且更准：
 * 1.21.1 覆盖不到的子类 {@code scale()}/{@code setupRotations()} 分支在这里天然被覆盖。
 *
 * <p>逐条保持不变的语义（与 1.21.1 同名方法**逐字或逐分支等价**）：
 * <ul>
 *   <li>{@link #outlineOf} = {@code collision.minmax(measured)}，{@code measured == null} 时退化为碰撞盒
 *       ⇒ **绝不内缩**（<b>并集</b>而非替换）；</li>
 *   <li>{@code IdentityHashMap} 按**实体对象身份**缓存；</li>
 *   <li>容量保险 {@code MAX_ENTRIES = 64}：超限且要写入新实体时整体清空（连读数状态一并清）；</li>
 *   <li>{@link #clearIfIdle()}：选择器未激活时清空缓存与读数计数；</li>
 *   <li>读数去重：同一实体最快每 {@code LOG_INTERVAL_TICKS = 20} tick 一条、会话上限 {@code MAX_LOG_LINES = 80}
 *       （触顶补一行显式提示）、体型签名（类型/缩放/幼年）变化立即打印；</li>
 *   <li>只认「可见」部位：走 {@code Model#renderToBuffer}（内部 {@code ModelPart#render} 会检查
 *       {@code visible}/{@code skipDraw}），**禁止**改用会忽略可见性的部位遍历 API。</li>
 * </ul>
 *
 * <h2>坐标系（为什么 {@code + camera.position()} 无偏移误差）</h2>
 * 26.1.2 的实体提交在「**世界坐标 − 相机位置**」的平移空间里进行：{@code LevelRenderer} 每帧新建
 * 单位 {@code PoseStack}，实体基座只做 {@code translate(entityPos - cameraPos)}。探针收到的顶点
 * 因此是 {@code p_cam = p_world − camPos}。本类按 {@code measured.move(camPos)} 还原：
 * <pre>
 * move(vec) 在 26.1.2 的实现 = new AABB(minX+vec.x, …, maxZ+vec.z)（AABB.java:230）—— **纯平移**，
 * 无任何缩放/取整/裁剪；且该式对 min/max 两个角同加一个常量，盒的尺寸不变。
 * 又因 camPos 在 LevelRenderer 构建姿态与 CameraRenderState 之间**不变**（同一帧的 main camera，
 * 不在两者之间移动），故 p_world = p_cam + camPos **逐位相等**（浮点加法同序、无额外运算），偏移误差为 0。
 * </pre>
 *
 * <p><b>本批边界</b>：B3 只产出「可见外框 + 读数」，**无消费者也算完成**（描边属 B4、HUD 属 B5）。
 */
public final class TargetOutlineCapture {
    private static final Logger LOGGER = LoggerFactory.getLogger(TargetOutlineCapture.class);

    /**
     * entity 身份槽位：由 {@code RegisterRenderStateModifiersEvent} 的处理器在**渲染状态提取阶段**写入。
     *
     * <p>{@code ContextKey} 的包是 {@code net.minecraft.util.context}（不是 {@code …renderer.state}）；
     * {@code EntityRenderState} 继承 NeoForge 的 {@code BaseRenderState}（{@code get/setRenderData}）
     * 且 {@code RenderStateExtensions#onUpdateEntityRenderState} 每次提取先 {@code resetRenderData()}
     * ⇒ 每帧自清、**不会静态泄漏**上一帧的实体。
     */
    public static final ContextKey<LivingEntity> ENTITY_KEY =
            new ContextKey<>(Identifier.fromNamespaceAndPath(AstralDiceMod.MODID, "outline_entity"));

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
    /** 一次性「钩子已生效」INFO 标记（见 {@link #onSubmit}；供用例断言，不刷屏） */
    private static boolean hookActiveLogged;

    private TargetOutlineCapture() {
    }

    /**
     * 由 {@code LivingEntityOutlineCaptureMixin} 在 {@code LivingEntityRenderer#submit} 的
     * {@code poseStack.popPose()} **之前**调用。
     *
     * <p><b>零成本早退</b>（顺序即成本顺序）：选择器未激活 → 不追踪该实体 → 无模型，三级早退，
     * 使选择器关闭时本方法只有几次字段读 + 一个 {@code isTracked} 判定。
     *
     * @param state     本帧活体渲染状态（实体身份从 {@link #ENTITY_KEY} 取）
     * @param poseStack 原版算好的最终模型变换（相机相对空间，纯平移）
     * @param model     目标渲染器的模型（mixin 以 {@code @Shadow} 取 {@code protected M model}）
     */
    public static void onSubmit(LivingEntityRenderState state, PoseStack poseStack, EntityModel<?> model) {
        if (!TargetSelectionClient.isActive()) return;
        if (state == null || poseStack == null || model == null) return;
        LivingEntity entity = state.getRenderData(ENTITY_KEY);
        if (entity == null || !TargetSelectionClient.isTracked(entity)) return;

        // 与 ModelFeatureRenderer#renderModel 同款：原版只在 shouldRenderLayers(state) && !layers.isEmpty()
        // 时才调 setupAnim，故我们必须自己调一次（幂等：内部即 resetPose + 各部位 setInitialPose）。
        setupAnim(model, state);

        GeometryProbe probe = new GeometryProbe();
        model.renderToBuffer(poseStack, probe, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, -1);
        AABB local = probe.toBox();
        if (local == null) return;

        Vec3 cameraPos = Minecraft.getInstance().gameRenderer.getMainCamera().position();
        store(entity, local.move(cameraPos), state);
    }

    /**
     * 调 {@code Model#setupAnim(S)}：模型侧只需「按 state 摆好姿势」，与渲染器泛型无关，
     * 故用 raw {@code Model} + 未检查转型（{@code Model#setupAnim} 在 26.1.2 是 {@code public void}，
     * {@code Model.java:54}）。
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void setupAnim(EntityModel<?> model, LivingEntityRenderState state) {
        ((Model) model).setupAnim(state);
    }

    /** 写入实测外框（含容量保险）并触发读数 */
    private static void store(LivingEntity entity, AABB box, LivingEntityRenderState state) {
        if (!hookActiveLogged) {
            // 一次性 INFO：让「mixin 真的命中且真的进了捕获路径」可被用例断言
            // （与「mixin 完全没生效」区分开；见 B3 报告「加载期自检」一节）
            hookActiveLogged = true;
            LOGGER.info("[Astral Dice][TargetSelectBounds] capture hook active "
                    + "(LivingEntityRenderer#submit popPose hook + EntityRenderState entity slot)");
        }
        if (CAPTURED.size() >= MAX_ENTRIES && !CAPTURED.containsKey(entity)) {
            CAPTURED.clear();
            LOGGED.clear();
        }
        CAPTURED.put(entity, box);
        logOnce(entity, box, state);
    }

    /**
     * 该实体本帧的可见外框 = **碰撞盒 ∪ 实测模型外框**（取并集 ⇒ 任何情况下都不小于碰撞盒，绝不内缩）；
     * 没有实测数据（未渲染 / 被取消 / 不在追踪集）时退化为碰撞盒。
     *
     * <p>与 1.21.1 基准同名方法**逐字一致**。
     */
    public static AABB outlineOf(LivingEntity entity) {
        AABB collision = entity.getBoundingBox();
        AABB measured = CAPTURED.get(entity);
        return measured == null ? collision : collision.minmax(measured);
    }

    /** 会话结束清空缓存（由后续批次的描边层在无目标可画时调用；本批无消费者也保留此语义） */
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
     * 读数：所用外框 vs 碰撞盒 vs 实测模型外框 + 缩放/幼年（成对打印，便于日志取证判定）。
     *
     * <p>去重规则（与 1.21.1 基准逐字一致）：同一实体「首次测量」或「体型签名（类型/缩放/幼年）变化」
     * 立即打印；其余情况最快每 {@link #LOG_INTERVAL_TICKS} tick 一条，且本次会话总数不超过
     * {@link #MAX_LOG_LINES}（触顶补一行显式提示）—— 模型动画会让外框每帧微动，
     * 不能按「值变了就打印」，否则就是每帧刷屏。
     *
     * <p>数据源差异（本批唯一的结构性改写，语义等价）：1.21.1 读实体字段
     * （{@code entity.getType()/getScale()/isBaby()/tickCount}）；26.1.2 的 mixin 侧只有 render state，
     * 故改读 {@code state.entityType}（{@code EntityRenderState.java:23}）、{@code state.scale}
     * （{@code LivingEntityRenderState.java:20}，由 {@code LivingEntityRenderer} 用 {@code entity.getScale()} 写入）、
     * {@code state.isBaby}（同文件 {@code :25}）。三者与实体字段**同源**，签名等价。
     * {@code tickCount} 用 {@code state.ageInTicks} 的整型部分替代（同为每 tick +1 的年龄计数，
     * 仅用于「20 tick 节流」这一近似判据）。
     */
    private static void logOnce(LivingEntity entity, AABB measured, LivingEntityRenderState state) {
        String type = state.entityType == null ? "unknown" : state.entityType.toShortString();
        String signature = type
                + "|" + String.format("%.2f", state.scale)
                + "|" + state.isBaby;
        long tick = (long) state.ageInTicks;
        LogState previous = LOGGED.get(entity);
        boolean typeChanged = previous != null && !previous.signature.equals(signature);
        if (previous != null && !typeChanged && (tick - previous.tick) < LOG_INTERVAL_TICKS) {
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
        LOGGED.put(entity, new LogState(tick, signature));
        AABB collision = entity.getBoundingBox();
        AABB used = collision.minmax(measured);
        LOGGER.debug("[Astral Dice][TargetSelectBounds] entity={} scale={} baby={} collision={} model={} used={}",
                type, String.format("%.3f", state.scale), state.isBaby,
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
     * <p>{@code ModelPart.Cube#compile} 先把顶点乘上部位/模型变换，再调 11 参
     * {@code addVertex(x,y,z,color,u,v,overlay,light,nx,ny,nz)}（26.1.2 {@code ModelPart.java:342}），
     * 该重载的默认实现最终落到 {@link #addVertex(float, float, float)}
     * （26.1.2 {@code com/mojang/blaze3d/vertex/VertexConsumer.java:32-39}），
     * 故这里收到的已是**变换后的模型坐标**（结合注入点的 poseStack ⇒ 相机相对世界坐标）。
     *
     * <p><b>26.1.2 与 1.21.1 的接口差异</b>：{@code VertexConsumer} 现在继承 NeoForge 的
     * {@code IVertexConsumerExtension}（其成员全为 {@code default}，无需实现），且抽象方法比 1.21.1
     * **多两个**：{@code setColor(int)} 与 {@code setLineWidth(float)}。共 8 个抽象方法：
     * {@code addVertex(float,float,float)}、{@code setColor(int,int,int,int)}、{@code setColor(int)}、
     * {@code setUv(float,float)}、{@code setUv1(int,int)}、{@code setUv2(int,int)}、
     * {@code setNormal(float,float,float)}、{@code setLineWidth(float)}
     * （26.1.2 {@code VertexConsumer.java:16-30}）。{@code setLight(int)}/{@code setOverlay(int)}/
     * {@code setColor(float×4)} 是 {@code default}，无需覆写。
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

        /** 26.1.2 新增的抽象重载（1.21.1 没有） */
        @Override
        public VertexConsumer setColor(int color) {
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

        /** 26.1.2 新增的抽象重载（1.21.1 没有） */
        @Override
        public VertexConsumer setLineWidth(float width) {
            return this;
        }

        private AABB toBox() {
            if (minX > maxX || minY > maxY || minZ > maxZ) return null;
            return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        }
    }
}
