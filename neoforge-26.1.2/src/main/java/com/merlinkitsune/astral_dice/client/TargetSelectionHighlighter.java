package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

import java.util.List;

/**
 * 目标选择器描边渲染（仅选择者本地可见）—— **Create 强力胶式实体棱柱边框**（26.1.2 移植版；
 * 语义基准 = 1.21.1 同名类）。
 *
 * <p>渲染口径（2026-09-17 用户裁决；只做边框，**不复刻胶面效果**：无棋盘纹 / 无簇高亮 / 无余辉）：
 * <ul>
 *   <li>通道 = 原版 {@code RenderTypes.entitySolid(BLANK_TEXTURE)}
 *       （26.1.2 的 {@code RenderType} 由原版集中构造：{@code RenderPipelines.ENTITY_SOLID} + Sampler0 纹理 +
 *       lightmap + overlay，**无混合（不透明）**）—— 等价于 1.21.1 侧用公开 API 手搓的
 *       {@code NEW_ENTITY + QUADS + 256 + false,false + ENTITY_SOLID shader + NO_TRANSPARENCY +
 *       LEQUAL_DEPTH_TEST + COLOR_DEPTH_WRITE + CULL + LIGHTMAP + OVERLAY}；
 *       纹理 = 16×16 不透明白色纹理 {@code assets/astral_dice/textures/special/blank.png}
 *       （颜色完全由顶点色给出）；</li>
 *   <li>每条边 = 一个棱柱，每棱柱 4 个侧面四边形（半宽 = 线宽/2，不含端面）；12 条边共 48 面；</li>
 *   <li>顶点：{@code FULL_BRIGHT} + {@code NO_OVERLAY}，UV 取 0,0，法线 (0,1,0)
 *       —— 恒亮光照下法线不参与着色，取值仅按口径固定；</li>
 *   <li>提交时机 = {@code SubmitCustomGeometryEvent}（26.1.2 两相渲染里「实体提交之后、实心特征绘制之前」，
 *       等价于 1.21.1 的 {@code RenderLevelStageEvent.Stage.AFTER_ENTITIES}）；</li>
 *   <li>线宽三档：命中目标 1/16、命中但不可选 1/24、半径内其它可选目标 1/64（取最近 ≤24 个）；</li>
 *   <li>框所套的盒 = **可见外框**（碰撞盒 ∪ {@link TargetOutlineCapture} 实测的模型外框）再外扩半线宽
 *       —— 不直接用碰撞盒。</li>
 * </ul>
 *
 * <p>配色保留旧口径：友方 {@code 0x55FF55} / 敌对 {@code 0xFF5555} / 中立 {@code 0xFFFF55}
 * （一律经 {@link TargetSelectionClient#highlightColor}，本类不重算）。
 *
 * <h2>26.1.2 与 1.21.1 的实现差异（全部为平台 API，语义零差异）</h2>
 * <ol>
 *   <li><b>渲染通道</b>：1.21.1 用 {@code RenderType.create(...)} + {@code RenderStateShard}
 *       （{@code RenderStateShard} 在 26.1.2 <b>已删除</b>，{@code RenderType} 也已<b>迁移</b>到
 *       {@code net.minecraft.client.renderer.rendertype} 包）⇒ 改为原版现成的
 *       {@code RenderTypes.entitySolid(Identifier)}；两者底层的 {@code RenderPipeline} 是同一个
 *       {@code RenderPipelines.ENTITY_SOLID}，顶点格式 / 混合 / 深度 / 剔除逐项等价（见类注释上方各条）。</li>
 *   <li><b>提交方式</b>：1.21.1 是「立即模式」——{@code mc.renderBuffers().bufferSource()}
 *       {@code getBuffer(PRISM)} 直接写顶点 + {@code endBatch(PRISM)}；26.1.2 改为两相渲染，
 *       {@code MultiBufferSource} 的立即写路径不参与本阶段 ⇒ 改为
 *       {@code SubmitNodeCollector#submitCustomGeometry(poseStack, renderType, (pose, buffer) -> …)}，
 *       由原版在自己的实心特征绘制阶段统一落盘。**形状与着色代码逐字不变**。</li>
 *   <li><b>姿态栈来源</b>：1.21.1 从 {@code RenderLevelStageEvent#getPoseStack()} 取；
 *       26.1.2 该事件的 {@code Stage} 枚举已删除、且不再提供相机（{@code getCamera()} 不存在）
 *       ⇒ 取自 {@code SubmitCustomGeometryEvent#getPoseStack()}，相机取
 *       {@code Minecraft#gameRenderer.getMainCamera().position()}。</li>
 *   <li><b>光照常量</b>：{@code LightTexture.FULL_BRIGHT} → {@code LightCoordsUtil.FULL_BRIGHT}（同为 15728880）。</li>
 * </ol>
 */
@EventBusSubscriber(modid = AstralDiceMod.MODID, value = Dist.CLIENT)
public final class TargetSelectionHighlighter {
    /** 16×16 纯白全不透明纹理（棱柱颜色 100% 由顶点色决定，纹理只是 shader 的必需采样源） */
    private static final Identifier BLANK_TEXTURE =
            Identifier.fromNamespaceAndPath(AstralDiceMod.MODID, "textures/special/blank.png");

    /** 线宽三档（半宽 = 线宽 / 2） */
    private static final float WIDTH_HIT = 1.0F / 16.0F;
    private static final float WIDTH_HIT_REJECTED = 1.0F / 24.0F;
    private static final float WIDTH_NEARBY = 1.0F / 64.0F;

    /**
     * 底部抬升余量（格）：下棱最低点抬到可见外框底面（≈ 脚底 / 地面方块顶面）之上，
     * 避免下棱柱嵌进地面方块并与之共面造成深度测试打架（2026-09-18 用户要求「外框底部向上抬升」）。
     */
    private static final double BOTTOM_LIFT = 0.02D;

    private TargetSelectionHighlighter() {
    }

    /**
     * 提交本帧的实体棱柱边框。
     *
     * <p><b>零成本早退</b>（顺序即成本顺序）：选择器未激活 → 无世界/无玩家 → 三种目标全空，
     * 三级早退都发生在取渲染通道之前，故选择器关闭时本方法只有几次静态字段读，
     * **不会**提前触发 {@code RenderTypes} 里任何 {@code RenderType} 的构造（通道在早退之后才惰性求值）。
     */
    @SubscribeEvent
    public static void onSubmitCustomGeometry(SubmitCustomGeometryEvent event) {
        if (!TargetSelectionClient.isActive()) {
            TargetOutlineCapture.clearIfIdle();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        LivingEntity target = TargetSelectionClient.currentTarget();
        LivingEntity rejected = TargetSelectionClient.rejectedTarget();
        List<LivingEntity> nearby = TargetSelectionClient.nearbyTargets();
        if (target == null && rejected == null && nearby.isEmpty()) {
            TargetOutlineCapture.clearIfIdle();
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        if (poseStack == null) return;
        SubmitNodeCollector collector = event.getSubmitNodeCollector();
        if (collector == null) return;
        // 惰性取通道：只在真的要画时才触发 RenderTypes 的对应构造（1.21.1 侧是类加载期静态字段）
        RenderType prism = RenderTypes.entitySolid(BLANK_TEXTURE);

        Vec3 cam = mc.gameRenderer.getMainCamera().position();
        // 26.1.2 的提交姿态栈是**单位阵**（LevelRenderer 每帧 new PoseStack()，实体由
        // EntityRenderDispatcher 自己 translate(实体坐标 − 相机坐标)），故这里照 1.21.1 的口径
        // 平移 −camPos，下面的顶点仍可直接写**世界坐标**。
        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        try {
            if (target != null && isPresent(mc, target)) {
                submitPrismBorder(collector, poseStack, prism, borderBox(target, WIDTH_HIT), WIDTH_HIT, colorOf(target));
            }
            if (rejected != null && rejected != target && isPresent(mc, rejected)) {
                submitPrismBorder(collector, poseStack, prism,
                        borderBox(rejected, WIDTH_HIT_REJECTED), WIDTH_HIT_REJECTED, colorOf(rejected));
            }
            for (LivingEntity other : nearby) {
                if (other == target || other == rejected) continue;
                if (!isPresent(mc, other)) continue;
                submitPrismBorder(collector, poseStack, prism,
                        borderBox(other, WIDTH_NEARBY), WIDTH_NEARBY, colorOf(other));
            }
        } finally {
            poseStack.popPose();
        }
    }

    /**
     * 把一个棱柱边框提交给原版收集器。
     *
     * <p>{@code submitCustomGeometry} 会在**提交当拍**快照 {@code poseStack.last()}，
     * 故调用方的 push/translate/pop 与之无竞争；几何与配色的计算全部延后到
     * {@code CustomGeometryRenderer} 回调里，形状代码与 1.21.1 逐字一致。
     */
    private static void submitPrismBorder(SubmitNodeCollector collector, PoseStack poseStack, RenderType prism,
                                          AABB box, float lineWidth, int color) {
        collector.submitCustomGeometry(poseStack, prism,
                (pose, buffer) -> emitPrismBorder(buffer, pose, box, lineWidth, color));
    }

    /**
     * 边框中心线所走的盒：**可见外框**（碰撞盒 ∪ 本帧实测模型外框，见 {@link TargetOutlineCapture}）
     * 再按半线宽外扩 —— 于是棱柱内表面正好贴在可见外框上（生物任何可见部位都不会穿过边框），
     * 外表面在可见外框之外半个线宽以上。**只外扩、绝不内缩**（旧实现直接用碰撞盒，导致
     * 僵尸双臂 / 蜘蛛八条腿 / 马颈头跑到边框之外，即本次修复的缺陷）。
     *
     * <p><b>底面单独抬升（2026-09-18 用户要求「外框底部向上抬升，避免嵌入地面造成闪烁」）</b>：
     * 站在地面上的实体，其可见外框底面 ≈ 脚底 ≈ 地面方块顶面；若照旧按半线宽外扩，下棱柱占据
     * {@code [minY-h, minY+h]}，下半段会**嵌进地面方块顶面并与之共面** ⇒ 深度测试打架（画面闪烁）。
     * 故下棱中心改为 {@code minY + h + BOTTOM_LIFT}：下棱最低点 = {@code minY + BOTTOM_LIFT}，严格位于
     * 脚底平面之上，整根下棱柱离地；左右 / 前后 / 顶部仍按半线宽外扩，贴肉关系逐条不变。
     */
    private static AABB borderBox(LivingEntity entity, float lineWidth) {
        double h = lineWidth / 2.0D;
        AABB outline = TargetOutlineCapture.outlineOf(entity);
        // 兜底：盒高小于 BOTTOM_LIFT 时把下棱中心夹到上棱中心以内，避免盒被上下翻转
        // （普通实体盒高远大于 BOTTOM_LIFT，此分支不可达；t39 验证：阈值 0.0199999999999889，仅零高盒退化）
        double bottom = Math.min(outline.minY + h + BOTTOM_LIFT, outline.maxY + h);
        return new AABB(outline.minX - h, bottom, outline.minZ - h,
                outline.maxX + h, outline.maxY + h, outline.maxZ + h);
    }

    /** 实体仍在当前世界内（避免拿上一 tick 的引用渲染已移除实体） */
    private static boolean isPresent(Minecraft mc, LivingEntity entity) {
        return mc.level != null && mc.level.getEntity(entity.getId()) == entity;
    }

    private static int colorOf(LivingEntity entity) {
        return TargetSelectionClient.highlightColor(entity);
    }

    /**
     * 沿 AABB 的 12 条边各画一个方形棱柱（半宽 = lineWidth / 2）。
     *
     * <p>棱柱沿边的两端各外扩半宽：相邻棱柱在角点处互相补全，避免 12 个角上留下缺口。
     */
    private static void emitPrismBorder(VertexConsumer vc, PoseStack.Pose pose, AABB box, float lineWidth, int color) {
        float r = ((color >> 16) & 0xFF) / 255.0F;
        float g = ((color >> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;
        double h = lineWidth / 2.0D;
        double minX = box.minX, minY = box.minY, minZ = box.minZ;
        double maxX = box.maxX, maxY = box.maxY, maxZ = box.maxZ;

        // 4 条 X 轴棱（y ∈ {minY,maxY} × z ∈ {minZ,maxZ}）：侧面为 ±Y / ±Z
        for (double y : new double[]{minY, maxY}) {
            for (double z : new double[]{minZ, maxZ}) {
                emitBoxSides(vc, pose, minX - h, maxX + h, y - h, y + h, z - h, z + h, Axis.X, r, g, b);
            }
        }
        // 4 条 Y 轴棱（x ∈ {minX,maxX} × z ∈ {minZ,maxZ}）：侧面为 ±X / ±Z
        for (double x : new double[]{minX, maxX}) {
            for (double z : new double[]{minZ, maxZ}) {
                emitBoxSides(vc, pose, x - h, x + h, minY - h, maxY + h, z - h, z + h, Axis.Y, r, g, b);
            }
        }
        // 4 条 Z 轴棱（x ∈ {minX,maxX} × y ∈ {minY,maxY}）：侧面为 ±X / ±Y
        for (double x : new double[]{minX, maxX}) {
            for (double y : new double[]{minY, maxY}) {
                emitBoxSides(vc, pose, x - h, x + h, y - h, y + h, minZ - h, maxZ + h, Axis.Z, r, g, b);
            }
        }
    }

    /** 棱柱的轴向（该轴上不发端面） */
    private enum Axis { X, Y, Z }

    /**
     * 发一个轴对齐盒子的 4 个侧面（不含垂直于 {@code axis} 的两个端面）。
     *
     * <p>每个四边形都用 {@link #emitQuad} 的朝外方向判据保证顶点顺序为「从外侧看逆时针」
     * —— 通道开了剔除（{@code RenderPipeline.Builder#build()} 的 {@code cull.orElse(true)}），
     * 顺序写反会被整面剔除（静默不可见的视觉回归），故这里不做人工推断，改由叉积符号自动纠正。
     */
    private static void emitBoxSides(VertexConsumer vc, PoseStack.Pose pose,
                                     double x0, double x1, double y0, double y1, double z0, double z1,
                                     Axis axis, float r, float g, float b) {
        float fx0 = (float) x0, fx1 = (float) x1;
        float fy0 = (float) y0, fy1 = (float) y1;
        float fz0 = (float) z0, fz1 = (float) z1;

        if (axis != Axis.X) {
            // ±X 两面
            emitQuad(vc, pose, fx1, fy0, fz0, fx1, fy0, fz1, fx1, fy1, fz1, fx1, fy1, fz0, 1, 0, 0, r, g, b);
            emitQuad(vc, pose, fx0, fy0, fz0, fx0, fy0, fz1, fx0, fy1, fz1, fx0, fy1, fz0, -1, 0, 0, r, g, b);
        }
        if (axis != Axis.Y) {
            // ±Y 两面
            emitQuad(vc, pose, fx0, fy1, fz0, fx1, fy1, fz0, fx1, fy1, fz1, fx0, fy1, fz1, 0, 1, 0, r, g, b);
            emitQuad(vc, pose, fx0, fy0, fz0, fx1, fy0, fz0, fx1, fy0, fz1, fx0, fy0, fz1, 0, -1, 0, r, g, b);
        }
        if (axis != Axis.Z) {
            // ±Z 两面
            emitQuad(vc, pose, fx0, fy0, fz1, fx1, fy0, fz1, fx1, fy1, fz1, fx0, fy1, fz1, 0, 0, 1, r, g, b);
            emitQuad(vc, pose, fx0, fy0, fz0, fx1, fy0, fz0, fx1, fy1, fz0, fx0, fy1, fz0, 0, 0, -1, r, g, b);
        }
    }

    /**
     * 发一个四边形；顶点按 (a,b,c,d) 给，顺序不作为前提 —— 用叉积 (b-a)×(c-a) 与朝外方向
     * {@code (ox,oy,oz)} 的符号判定，必要时反转顺序，保证正面朝向观察者（GL_CCW + 剔除）。
     */
    private static void emitQuad(VertexConsumer vc, PoseStack.Pose pose,
                                 float ax, float ay, float az,
                                 float bx, float by, float bz,
                                 float cx, float cy, float cz,
                                 float dx, float dy, float dz,
                                 float ox, float oy, float oz,
                                 float r, float g, float b) {
        float e1x = bx - ax, e1y = by - ay, e1z = bz - az;
        float e2x = cx - ax, e2y = cy - ay, e2z = cz - az;
        float nx = e1y * e2z - e1z * e2y;
        float ny = e1z * e2x - e1x * e2z;
        float nz = e1x * e2y - e1y * e2x;
        if (nx * ox + ny * oy + nz * oz < 0.0F) {
            emitVertices(vc, pose, ax, ay, az, dx, dy, dz, cx, cy, cz, bx, by, bz, r, g, b);
        } else {
            emitVertices(vc, pose, ax, ay, az, bx, by, bz, cx, cy, cz, dx, dy, dz, r, g, b);
        }
    }

    private static void emitVertices(VertexConsumer vc, PoseStack.Pose pose,
                                     float ax, float ay, float az,
                                     float bx, float by, float bz,
                                     float cx, float cy, float cz,
                                     float dx, float dy, float dz,
                                     float r, float g, float b) {
        putVertex(vc, pose, ax, ay, az, r, g, b);
        putVertex(vc, pose, bx, by, bz, r, g, b);
        putVertex(vc, pose, cx, cy, cz, r, g, b);
        putVertex(vc, pose, dx, dy, dz, r, g, b);
    }

    /**
     * 写一个顶点。与 1.21.1 逐字一致（含 {@code setNormal(0,1,0)} 的固定法线口径）：
     * 本类提交的姿态栈只含平移（−camPos），法线变换对平移是恒等，故无需
     * {@code setNormal(pose, …)}；且顶点走 {@code FULL_BRIGHT} 恒亮 + lightmap，
     * 法线不参与着色，取值仅按口径固定。
     */
    private static void putVertex(VertexConsumer vc, PoseStack.Pose pose,
                                  float x, float y, float z, float r, float g, float b) {
        vc.addVertex(pose, x, y, z)
                .setColor(r, g, b, 1.0F)
                .setUv(0.0F, 0.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightCoordsUtil.FULL_BRIGHT)
                .setNormal(0.0F, 1.0F, 0.0F);
    }
}
