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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.List;

/**
 * 目标选择器描边渲染（仅选择者本地可见）—— **Create 强力胶式实体棱柱边框**。
 *
 * <p>渲染口径（2026-09-17 用户裁决；只做边框，**不复刻胶面效果**：无棋盘纹 / 无簇高亮 / 无余辉）：
 * <ul>
 *   <li>RenderType 由公开 API 构造（{@code RenderType.create}）：
 *       {@code DefaultVertexFormat.NEW_ENTITY} + {@code VertexFormat.Mode.QUADS} + 缓冲 256 +
 *       {@code false,false}；shader {@code RENDERTYPE_ENTITY_SOLID_SHADER}；半透明关闭
 *       （{@code NO_TRANSPARENCY}）；{@code LEQUAL_DEPTH_TEST} + {@code COLOR_DEPTH_WRITE} +
 *       {@code CULL} + {@code LIGHTMAP} + {@code OVERLAY}；纹理 = 新增 16×16 不透明白色纹理
 *       {@code assets/astral_dice/textures/special/blank.png}（颜色完全由顶点色给出）；</li>
 *   <li>每条边 = 一个棱柱，每棱柱 4 个侧面四边形（半宽 = 线宽/2，不含端面）；12 条边共 48 面；</li>
 *   <li>顶点：{@code FULL_BRIGHT} + {@code NO_OVERLAY}，UV 取 0,0，法线 (0,1,0)
 *       —— 恒亮光照下法线不参与着色，取值仅按口径固定；</li>
 *   <li>渲染 stage = {@code AFTER_ENTITIES}（Sodium 不整体替换 {@code LevelRenderer.renderLevel}、
 *       Iris 保留事件点，与既有验证一致）；</li>
 *   <li>线宽三档：命中目标 1/16、命中但不可选 1/24、半径内其它可选目标 1/64（取最近 ≤24 个）；</li>
 *   <li>框所套的盒 = **可见外框**（碰撞盒 ∪ {@link TargetOutlineCapture} 实测的模型外框）再外扩半线宽
 *       —— 不直接用碰撞盒（1.21.1 无覆盖模型外框的原版 API，详见 {@link TargetOutlineCapture} 的根因说明）。</li>
 * </ul>
 *
 * <p>配色保留旧口径：友方 {@code 0x55FF55} / 敌对 {@code 0xFF5555} / 中立 {@code 0xFFFF55}。
 */
@EventBusSubscriber(modid = AstralDiceMod.MODID, value = Dist.CLIENT)
public final class TargetSelectionHighlighter {
    /** 16×16 纯白全不透明纹理（棱柱颜色 100% 由顶点色决定，纹理只是 shader 的必需采样源） */
    private static final ResourceLocation BLANK_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "textures/special/blank.png");

    /** 线宽三档（半宽 = 线宽 / 2） */
    private static final float WIDTH_HIT = 1.0F / 16.0F;
    private static final float WIDTH_HIT_REJECTED = 1.0F / 24.0F;
    private static final float WIDTH_NEARBY = 1.0F / 64.0F;

    /** 实体棱柱描边通道（公开 API 构造；`endBatch(PRISM)` 收尾） */
    private static final RenderType PRISM = RenderType.create(
            "astral_dice_target_prism",
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            256,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_SOLID_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(BLANK_TEXTURE, false, false))
                    .setTransparencyState(RenderStateShard.NO_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                    .setCullState(RenderStateShard.CULL)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setOverlayState(RenderStateShard.OVERLAY)
                    .createCompositeState(false));

    private TargetSelectionHighlighter() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        PoseStack poseStack = event.getPoseStack();
        if (poseStack == null) return;

        LivingEntity target = TargetSelectionClient.currentTarget();
        LivingEntity rejected = TargetSelectionClient.rejectedTarget();
        List<LivingEntity> nearby = TargetSelectionClient.nearbyTargets();
        if (target == null && rejected == null && nearby.isEmpty()) {
            TargetOutlineCapture.clearIfIdle();
            return;
        }

        poseStack.pushPose();
        Vec3 cam = event.getCamera().getPosition();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        PoseStack.Pose pose = poseStack.last();

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(PRISM);

        if (target != null && isPresent(mc, target)) {
            emitPrismBorder(consumer, pose, borderBox(target, WIDTH_HIT), WIDTH_HIT, colorOf(target));
        }
        if (rejected != null && rejected != target && isPresent(mc, rejected)) {
            emitPrismBorder(consumer, pose, borderBox(rejected, WIDTH_HIT_REJECTED), WIDTH_HIT_REJECTED, colorOf(rejected));
        }
        for (LivingEntity other : nearby) {
            if (other == target || other == rejected) continue;
            if (!isPresent(mc, other)) continue;
            emitPrismBorder(consumer, pose, borderBox(other, WIDTH_NEARBY), WIDTH_NEARBY, colorOf(other));
        }

        buffers.endBatch(PRISM);
        poseStack.popPose();
    }

    /**
     * 边框中心线所走的盒：**可见外框**（碰撞盒 ∪ 本帧实测模型外框，见 {@link TargetOutlineCapture}）
     * 再按半线宽外扩 —— 于是棱柱内表面正好贴在可见外框上（生物任何可见部位都不会穿过边框），
     * 外表面在可见外框之外半个线宽以上。**只外扩、绝不内缩**（旧实现直接用碰撞盒，导致
     * 僵尸双臂 / 蜘蛛八条腿 / 马颈头跑到边框之外，即本次修复的缺陷）。
     */
    private static AABB borderBox(LivingEntity entity, float lineWidth) {
        return TargetOutlineCapture.outlineOf(entity).inflate(lineWidth / 2.0D);
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
     * —— RenderType 开了 {@code CULL}，顺序写反会被整面剔除（静默不可见的视觉回归），
     * 故这里不做人工推断，改由叉积符号自动纠正。
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
     * {@code (ox,oy,oz)} 的符号判定，必要时反转顺序，保证正面朝向观察者（GL_CCW + CULL）。
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

    private static void putVertex(VertexConsumer vc, PoseStack.Pose pose,
                                  float x, float y, float z, float r, float g, float b) {
        vc.addVertex(pose, x, y, z)
                .setColor(r, g, b, 1.0F)
                .setUv(0.0F, 0.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(0.0F, 1.0F, 0.0F);
    }
}
