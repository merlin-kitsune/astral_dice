package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.effect.ModEffects;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * 鼠鼠护盾(游戏大师立牌 ren)的**第三方可见**能量球护盾渲染(1.20.1 / Forge)。
 *
 * <h2>条件源为什么是 mob effect</h2>
 * 原版会把 {@code MobEffectInstance} 同步给**所有能看到该实体的客户端**(效果粒子对他人可见就是这条路径)
 * ⇒ 直接以 {@code entity.hasEffect(REN_SHIELD)} 作为渲染条件,天然就是「别人也能看见」;
 * 1.20.1 的玩家附件同步只发给本人({@code ModNetwork.syncAttachment} → {@code sendToPlayer}),
 * **不能**用作此条件 —— 这是本类与 1.21.1 侧同名类唯一语义相关的差异点。
 *
 * <h2>观感(2026-09-25 两轮用户反馈:①更大更深更亮 ②必须完全覆盖玩家 + 球体 + 动态纹理)</h2>
 * <ul>
 *   <li><b>完整球体</b>:θ ∈ [0, π] 的闭合球面(不再是上半球),球心 = 脚底 + 身高的
 *       {@link #CENTER_HEIGHT_FACTOR}(= 身体中心),半径 {@link #RADIUS} = 1.2 格
 *       ⇒ 玩家半高 0.9 / 半宽 0.3 都落在球内,**完全包住玩家**;</li>
 *   <li><b>动态纹理</b>:球面 UV 采样 {@code textures/special/ren_shield.png}(六边能量单元图案,
 *       由 {@code tools/gen_ren_shield_texture.py} 生成、可平铺),UV 以
 *       {@link #UV_SCROLL_U}/{@link #UV_SCROLL_V} 每 tick 平移 ⇒ 图案在球面上**持续流动**;</li>
 *   <li><b>发光</b>:本体之外再画一层半径 ×{@link #GLOW_RADIUS_FACTOR} 的**加色混合**外壳
 *       ({@link #GLOW} 通道,{@code ADDITIVE_TRANSPARENCY}),叠出外缘辉光;两层都用
 *       {@code LightTexture.FULL_BRIGHT} ⇒ 不受世界光照影响;</li>
 *   <li><b>动态涌动</b>:一条**高斯能量带**沿极角从下极涌到上极({@link #SURGE_SPEED} 圈/tick),
 *       叠加整体呼吸脉动({@link #PULSE_SPEED})、赤道亮环与沿经度的正弦扭曲({@link #WOBBLE_*}),
 *       每帧重算顶点色 ⇒ 观感是流动的能量;相位按实体 id 错开({@link #PHASE_ENTITY_STRIDE})。</li>
 * </ul>
 *
 * <h2>几何与深度</h2>
 * 两层通道都是「只写颜色」({@code COLOR_WRITE}),球面 + 背面剔除 ⇒ 每像素最多一个正面片,
 * 不需要写深度也不会自遮挡;球体埋进地面的部分由地形深度测试自然遮住。
 *
 * <h2>第一人称</h2>
 * 「第一人称 且 视线实体就是自己」时跳过:**相机在球内,外壳会铺满整屏**;第三人称(F5)
 * 照常可见。非本地玩家一律照画 ⇒ 其余玩家看得到你的护盾。
 */
@Mod.EventBusSubscriber(modid = AstralDiceMod.MODID, value = Dist.CLIENT)
public final class RenShieldRenderer {
    /** 球面图案贴图(六边能量单元;生成器见 tools/gen_ren_shield_texture.py) */
    private static final ResourceLocation SHIELD_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "textures/special/ren_shield.png");

    /**
     * 球体本体通道:半透明实体着色器 + 半透明混合 + LEQUAL 深度测试 + **只写颜色**。
     * 球面开背面剔除 ⇒ 每个像素最多只有一个正面片,故不需要写入深度也不会自遮挡。
     */
    private static final RenderType DOME = createDome("astral_dice_ren_shield",
            States.TRANSPARENCY_TRANSLUCENT);

    /** 外层发光壳:同一着色器 + **加色混合**(只加亮、不加暗)⇒ 观感是自发光而不是半透明壳 */
    private static final RenderType GLOW = createDome("astral_dice_ren_shield_glow",
            States.TRANSPARENCY_ADDITIVE);

    /** 球半径(格):玩家半高 0.9、半宽 0.3 ⇒ 1.2 完整包住并留观感余量 */
    private static final double RADIUS = 1.2D;
    /** 球心高度 = 脚底 + 身高 × 本系数(0.5 = 身体中心) */
    private static final double CENTER_HEIGHT_FACTOR = 0.5D;
    /** 主体色:青蓝 */
    private static final float RED = 0.22F;
    private static final float GREEN = 0.58F;
    private static final float BLUE = 1.0F;
    /** 主体基础不透明度(首版 0.25 观感太淡) */
    private static final float ALPHA = 0.42F;
    /** 发光壳半径倍率与基础强度(加色混合,数值即「加多少亮度」) */
    private static final float GLOW_RADIUS_FACTOR = 1.06F;
    private static final float GLOW_ALPHA = 0.34F;
    /** 经纬细分数(四边形数 = LAT × LON);涌动带是逐顶点着色,细分太低会看出折线 */
    private static final int LAT_STEPS = 16;
    private static final int LON_STEPS = 32;
    /** 超过该距离的玩家不画(格) */
    private static final double MAX_DISTANCE = 64.0D;
    /** 贴图在球面上的平铺份数(U = 绕一圈、V = 从上极到下极) */
    private static final float UV_TILES_U = 3.0F;
    private static final float UV_TILES_V = 4.0F;
    /** 贴图滚动速度(UV / tick):U 向东、V 向北,一正一负 ⇒ 图案斜向流动 */
    private static final float UV_SCROLL_U = 0.010F;
    private static final float UV_SCROLL_V = -0.006F;
    /** 涌动带:每 tick 沿极角上行比例 / 高斯半宽(以半径为单位) */
    private static final float SURGE_SPEED = 0.016F;
    private static final float SURGE_WIDTH = 0.19F;
    /** 整体呼吸脉动的角速度 */
    private static final float PULSE_SPEED = 0.085F;
    /** 沿经度的正弦扭曲:振幅(极角比例)/ 波数 / 角速度 */
    private static final float WOBBLE_AMPLITUDE = 0.07F;
    private static final float WOBBLE_TURNS = 3.0F;
    private static final float WOBBLE_SPEED = 0.09F;
    /** 相位按实体 id 错开的步长(id 低位 × 本值 ⇒ 多护盾不同步) */
    private static final float PHASE_ENTITY_STRIDE = 13.0F;

    private RenShieldRenderer() {
    }

    /**
     * {@code RenderStateShard} 的「只读再导出」子类(与 {@code client/TargetSelectionHighlighter} 同一手法)。
     *
     * <p>1.20.1 的 {@code RENDERTYPE_ENTITY_TRANSLUCENT_SHADER} / {@code TRANSLUCENT_TRANSPARENCY} /
     * {@code ADDITIVE_TRANSPARENCY} / {@code LEQUAL_DEPTH_TEST} / {@code COLOR_WRITE} / {@code CULL} /
     * {@code LIGHTMAP} / {@code OVERLAY} 都是 {@code protected static final},而本类不在
     * {@code net.minecraft.client.renderer} 包内 —— 但**子类按简单名访问继承的 protected 静态成员是合法的**。
     * 这些常量不复制、不重建,仍是原版同一对象。
     */
    private static final class States extends RenderStateShard {
        private States() {
            super("astral_dice_ren_shield_states", () -> {
            }, () -> {
            });
        }

        static final ShaderStateShard SHADER_ENTITY_TRANSLUCENT = RENDERTYPE_ENTITY_TRANSLUCENT_SHADER;
        static final TransparencyStateShard TRANSPARENCY_TRANSLUCENT = TRANSLUCENT_TRANSPARENCY;
        static final TransparencyStateShard TRANSPARENCY_ADDITIVE = ADDITIVE_TRANSPARENCY;
        static final DepthTestStateShard DEPTH_TEST_LEQUAL = LEQUAL_DEPTH_TEST;
        static final WriteMaskStateShard WRITE_MASK_COLOR = COLOR_WRITE;
        static final CullStateShard CULL_BACK = CULL;
        static final LightmapStateShard LIGHTMAP_ON = LIGHTMAP;
        static final OverlayStateShard OVERLAY_ON = OVERLAY;

        static TextureStateShard shieldTexture(ResourceLocation location) {
            return new TextureStateShard(location, false, false);
        }
    }

    private static RenderType createDome(String name, RenderStateShard.TransparencyStateShard transparency) {
        return RenderType.create(
                name,
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                256,
                false,
                false,
                RenderType.CompositeState.builder()
                        .setShaderState(States.SHADER_ENTITY_TRANSLUCENT)
                        .setTextureState(States.shieldTexture(SHIELD_TEXTURE))
                        .setTransparencyState(transparency)
                        .setDepthTestState(States.DEPTH_TEST_LEQUAL)
                        .setWriteMaskState(States.WRITE_MASK_COLOR)
                        .setCullState(States.CULL_BACK)
                        .setLightmapState(States.LIGHTMAP_ON)
                        .setOverlayState(States.OVERLAY_ON)
                        .createCompositeState(false));
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        PoseStack poseStack = event.getPoseStack();
        if (poseStack == null) return;

        List<Player> shielded = new ArrayList<>();
        for (Player player : mc.level.players()) {
            if (!player.hasEffect(ModEffects.REN_SHIELD.get())) continue;
            // 防拿上一 tick 的已移除实体
            if (mc.level.getEntity(player.getId()) != player) continue;
            if (player.distanceToSqr(mc.player) > MAX_DISTANCE * MAX_DISTANCE) continue;
            if (isFirstPersonSelf(mc, player)) continue;
            shielded.add(player);
        }
        if (shielded.isEmpty()) return;

        float partialTick = event.getPartialTick();

        poseStack.pushPose();
        Vec3 cam = event.getCamera().getPosition();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        PoseStack.Pose pose = poseStack.last();

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        // 先铺外层加色辉光壳、再叠本体:两层都不写深度,绘制顺序只决定叠加次序
        VertexConsumer glowConsumer = buffers.getBuffer(GLOW);
        for (Player player : shielded) {
            emitSphere(glowConsumer, pose, player, RADIUS * GLOW_RADIUS_FACTOR, true, partialTick);
        }
        buffers.endBatch(GLOW);

        VertexConsumer bodyConsumer = buffers.getBuffer(DOME);
        for (Player player : shielded) {
            emitSphere(bodyConsumer, pose, player, RADIUS, false, partialTick);
        }
        buffers.endBatch(DOME);
        poseStack.popPose();
    }

    /** 第一人称下不画自己那一个:相机在球内,外壳会糊满整屏 */
    private static boolean isFirstPersonSelf(Minecraft mc, Player player) {
        return player == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
    }

    /**
     * 一次 tick 内固定不变的动画相位(同一护盾的所有顶点共用,避免逐顶点算 sin)。
     *
     * @param time  动画时间(含部分 tick 与按实体错开的相位偏移)
     * @param wave  涌动带当前所在极角比例(0 = 下极、1 = 上极)
     * @param pulse 呼吸脉动 0..1
     */
    private record Phase(float time, float wave, float pulse) {
        static Phase of(Player player, float partialTick) {
            float t = player.tickCount + partialTick + (player.getId() & 15) * PHASE_ENTITY_STRIDE;
            return new Phase(t, (t * SURGE_SPEED) % 1.0F,
                    0.5F + 0.5F * (float) Math.sin(t * PULSE_SPEED));
        }
    }

    /** 完整球面(θ ∈ [0, π],上极 → 下极;闭合球体把玩家整个包在里面) */
    private static void emitSphere(VertexConsumer vc, PoseStack.Pose pose, Player player,
                                   double radius, boolean shell, float partialTick) {
        double cx = player.getX();
        double cy = player.getY() + player.getBbHeight() * CENTER_HEIGHT_FACTOR;
        double cz = player.getZ();
        Phase phase = Phase.of(player, partialTick);
        float scrollU = (shell ? UV_SCROLL_U * 1.35F : UV_SCROLL_U) * phase.time();
        float scrollV = (shell ? UV_SCROLL_V * 1.35F : UV_SCROLL_V) * phase.time();
        for (int i = 0; i < LAT_STEPS; i++) {
            double theta0 = Math.PI * i / LAT_STEPS;
            double theta1 = Math.PI * (i + 1) / LAT_STEPS;
            for (int j = 0; j < LON_STEPS; j++) {
                double phi0 = 2.0D * Math.PI * j / LON_STEPS;
                double phi1 = 2.0D * Math.PI * (j + 1) / LON_STEPS;
                emitQuad(vc, pose, cx, cy, cz, radius, phase, shell, scrollU, scrollV,
                        point(cx, cy, cz, radius, theta0, phi0),
                        point(cx, cy, cz, radius, theta1, phi0),
                        point(cx, cy, cz, radius, theta1, phi1),
                        point(cx, cy, cz, radius, theta0, phi1));
            }
        }
    }

    private static double[] point(double cx, double cy, double cz, double radius, double theta, double phi) {
        double sin = Math.sin(theta);
        return new double[]{
                cx + radius * sin * Math.cos(phi),
                cy + radius * Math.cos(theta),
                cz + radius * sin * Math.sin(phi)};
    }

    /**
     * 发一个四边形。顶点顺序按叉积自动纠正:开 {@code CULL} 时顺序写反会让整面**静默不可见**
     * (既有棱柱渲染踩过同一个坑),故这里用「(p10-p00)×(p11-p00) 是否指向球外」判定朝向。
     */
    private static void emitQuad(VertexConsumer vc, PoseStack.Pose pose, double cx, double cy, double cz,
                                 double radius, Phase phase, boolean shell, float scrollU, float scrollV,
                                 double[] p00, double[] p10, double[] p11, double[] p01) {
        if (facesInward(cx, cy, cz, p00, p10, p11)) {
            emitVertex(vc, pose, cx, cy, cz, radius, phase, shell, scrollU, scrollV, p00);
            emitVertex(vc, pose, cx, cy, cz, radius, phase, shell, scrollU, scrollV, p01);
            emitVertex(vc, pose, cx, cy, cz, radius, phase, shell, scrollU, scrollV, p11);
            emitVertex(vc, pose, cx, cy, cz, radius, phase, shell, scrollU, scrollV, p10);
        } else {
            emitVertex(vc, pose, cx, cy, cz, radius, phase, shell, scrollU, scrollV, p00);
            emitVertex(vc, pose, cx, cy, cz, radius, phase, shell, scrollU, scrollV, p10);
            emitVertex(vc, pose, cx, cy, cz, radius, phase, shell, scrollU, scrollV, p11);
            emitVertex(vc, pose, cx, cy, cz, radius, phase, shell, scrollU, scrollV, p01);
        }
    }

    private static boolean facesInward(double cx, double cy, double cz,
                                       double[] a, double[] b, double[] c) {
        double ux = b[0] - a[0];
        double uy = b[1] - a[1];
        double uz = b[2] - a[2];
        double vx = c[0] - a[0];
        double vy = c[1] - a[1];
        double vz = c[2] - a[2];
        double nx = uy * vz - uz * vy;
        double ny = uz * vx - ux * vz;
        double nz = ux * vy - uy * vx;
        // 球心 → 面片 的方向即外法线方向
        return nx * (a[0] - cx) + ny * (a[1] - cy) + nz * (a[2] - cz) < 0.0D;
    }

    /**
     * 逐顶点着色:极角比例 {@code s}(0 = 下极、1 = 上极)决定涌动带权重,
     * 赤道附近加一圈亮环;UV 由球面坐标直接映射并叠加滚动偏移(动态纹理)。
     */
    private static void emitVertex(VertexConsumer vc, PoseStack.Pose pose, double cx, double cy, double cz,
                                   double radius, Phase phase, boolean shell, float scrollU, float scrollV,
                                   double[] p) {
        float nx = (float) ((p[0] - cx) / radius);
        float ny = (float) ((p[1] - cy) / radius);
        float nz = (float) ((p[2] - cz) / radius);
        float h = (float) ((p[1] - cy) / radius);
        float s = (h + 1.0F) * 0.5F;
        float phi = (float) Math.atan2(p[2] - cz, p[0] - cx);
        float u = (float) ((phi + Math.PI) / (2.0D * Math.PI));
        float v = (float) (Math.acos(clampSigned(h)) / Math.PI);
        float wobble = WOBBLE_AMPLITUDE * (float) Math.sin(phi * WOBBLE_TURNS + phase.time() * WOBBLE_SPEED);
        float band = surgeBand(s + wobble - phase.wave());
        float equator = (float) Math.exp(-(h * h) / 0.08D);
        float r;
        float g;
        float b;
        float a;
        if (shell) {
            float boost = 0.85F + 0.35F * band;
            r = RED * boost;
            g = GREEN * boost;
            b = BLUE * boost;
            a = GLOW_ALPHA * (0.55F + 0.45F * phase.pulse()) * (0.35F + 0.65F * band) * (0.7F + 0.3F * equator);
        } else {
            float boost = 1.0F + 0.45F * band + 0.10F * phase.pulse();
            r = Math.min(1.0F, RED * boost);
            g = Math.min(1.0F, GREEN * boost);
            b = Math.min(1.0F, BLUE * boost);
            a = ALPHA * (0.75F + 0.35F * phase.pulse()) * (1.0F + 0.5F * equator + 1.8F * band);
        }
        vc.vertex(pose.pose(), (float) p[0], (float) p[1], (float) p[2])
                .color(r, g, b, clamp01(a))
                .uv(u * UV_TILES_U + scrollU, v * UV_TILES_V + scrollV)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(nx, ny, nz)
                .endVertex();
    }

    /** 涌动带:以 {@code d = 0} 为中心的高斯权重(1 = 带心、→0 = 远处) */
    private static float surgeBand(float d) {
        return (float) Math.exp(-(d * d) / (2.0D * SURGE_WIDTH * SURGE_WIDTH));
    }

    private static float clamp01(float v) {
        return v < 0.0F ? 0.0F : (v > 1.0F ? 1.0F : v);
    }

    private static float clampSigned(float v) {
        return v < -1.0F ? -1.0F : (v > 1.0F ? 1.0F : v);
    }
}
