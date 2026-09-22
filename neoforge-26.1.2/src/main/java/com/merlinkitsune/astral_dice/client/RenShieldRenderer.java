package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.effect.ModEffects;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 鼠鼠护盾(游戏大师立牌 ren)的**第三方可见**能量球护盾渲染(26.1.2 移植版;语义基准 = 1.21.1 同名类)。
 *
 * <h2>条件源为什么是 mob effect</h2>
 * 原版会把 {@code MobEffectInstance} 同步给**所有能看到该实体的客户端**(效果粒子对他人可见就是这条路径)
 * ⇒ 直接以 {@code entity.hasEffect(REN_SHIELD)} 作为渲染条件,天然就是「别人也能看见」;
 * 1.20.1 的玩家附件同步只发给本人(见 forge 侧同名类注释),**不能**用作此条件。
 *
 * <h2>观感(2026-09-25 两轮用户反馈:①更大更深更亮 ②必须完全覆盖玩家 + 球体 + 动态纹理)</h2>
 * <ul>
 *   <li><b>完整球体</b>:θ ∈ [0, π] 的闭合球面(不再是上半球),球心 = 脚底 + 身高的
 *       {@link #CENTER_HEIGHT_FACTOR}(= 身体中心),半径 {@link #RADIUS} = 1.2 格
 *       ⇒ 玩家半高 0.9 / 半宽 0.3 都落在球内,**完全包住玩家**;</li>
 *   <li><b>动态纹理</b>:球面 UV 采样 {@code textures/special/ren_shield.png}(六边能量单元图案,
 *       由 {@code tools/gen_ren_shield_texture.py} 生成、可平铺),UV 以
 *       {@link #UV_SCROLL_U}/{@link #UV_SCROLL_V} 每 tick 平移 ⇒ 图案在球面上**持续流动**;
 *       图案本身是周期函数,故 U 方向环绕无接缝;</li>
 *   <li><b>发光</b>:本体之外再画一层半径 ×{@link #GLOW_RADIUS_FACTOR} 的**加色混合**外壳
 *       ({@link #GLOW} 通道,{@code BlendFunction.ADDITIVE}),叠出外缘辉光;两层都用
 *       {@code LightCoordsUtil.FULL_BRIGHT} ⇒ 不受世界光照影响;</li>
 *   <li><b>动态涌动</b>:一条**高斯能量带**沿极角从下极涌到上极({@link #SURGE_SPEED} 圈/tick),
 *       叠加整体呼吸脉动({@link #PULSE_SPEED})、赤道亮环与沿经度的正弦扭曲({@link #WOBBLE_*}),
 *       每帧重算顶点色 ⇒ 观感是流动的能量;相位按实体 id 错开({@link #PHASE_ENTITY_STRIDE}),
 *       多个护盾不会完全同步。</li>
 * </ul>
 *
 * <h2>几何与深度</h2>
 * 两层通道都不写深度({@code DepthStencilState(LESS_THAN_OR_EQUAL, false)},等价于 1.21.1 的
 * {@code COLOR_WRITE} 写掩码),球面 + 背面剔除 ⇒ 每像素最多一个正面片,不需要写深度也不会自遮挡;
 * 球体埋进地面的部分由地形深度测试自然遮住。
 *
 * <h2>第一人称</h2>
 * 「第一人称 且 视线实体就是自己」时跳过:**相机在球内,外壳会铺满整屏**;第三人称(F5)
 * 照常可见。非本地玩家一律照画 ⇒ 其余玩家看得到你的护盾。
 *
 * <h2>性能</h2>
 * 只在本地玩家 ≤ {@link #MAX_DISTANCE} 格内、且带该效果的玩家上工作;无护盾时整段不 push/不提交。
 *
 * <h2>26.1.2 与 1.21.1 的实现差异（逐条取证；几何与配色代码逐字不变）</h2>
 * <ol>
 *   <li><b>渲染通道必须自定义 {@code RenderPipeline}</b>:1.21.1 用 {@code RenderType.create(...)} +
 *       {@code RenderStateShard} 手搓两个通道;26.1.2 这两者都<b>已删除</b>
 *       ({@code client/renderer/RenderStateShard.java} / {@code client/renderer/RenderType.java} 在
 *       26.1.2 反编译源里<b>不存在</b>,{@code RenderType} 迁到
 *       {@code net.minecraft.client.renderer.rendertype}),改由「{@code RenderPipeline} + {@code RenderSetup}」
 *       表达。本仓库既有移植文件 {@code client/TargetSelectionHighlighter.java:57-61} 记录了同一结论。</li>
 *   <li><b>为什么不能直接用现成通道</b>(逐条对比 26.1.2 反编译源
 *       {@code client/renderer/RenderPipelines.java}):
 *       原版 {@code ENTITY_TRANSLUCENT}(:252-261)与 NeoForge 的
 *       {@code NeoForgeRenderPipelines.ENTITY_TRANSLUCENT_CULL} 都是**写深度**的
 *       ({@code ENTITY_SNIPPET} 带 {@code DepthStencilState.DEFAULT = (LESS_THAN_OR_EQUAL, writeDepth=true)},
 *       {@code com/mojang/blaze3d/pipeline/DepthStencilState.java:9});而定稿口径要求「只写颜色」
 *       ——若写深度,先提交的外层辉光壳(半径 ×1.06)会挡住后提交的本体球面(LEQUAL 直接失败),
 *       画面只剩辉光壳。{@code ENTITY_TRANSLUCENT_EMISSIVE}(:262-272)虽写深度为 false,但
 *       {@code withCull(false)}(关背面剔除)且是 {@code TRANSLUCENT} 混合;
 *       原版唯一的加色实体通道 {@code ENERGY_SWIRL}(:308-321)同样是 {@code withCull(false)}、
 *       且带 {@code NO_OVERLAY}/{@code APPLY_TEXTURE_MATRIX} 着色器 define、
 *       顶点格式为 {@code DefaultVertexFormat.ENTITY} ⇒ 三者都与 1.21.1 基准不符。
 *       ⇒ 本类按 NeoForge 官方写法({@code NeoForgeRenderPipelines.java:52-60},「同 SNIPPET + 换混合/剔除」
 *       的 {@code ENTITY_TRANSLUCENT_CULL} 就是同一手法)注册两个自有 pipeline:
 *       同 {@code RenderPipelines.ENTITY_SNIPPET}、{@code ALPHA_CUTOUT 0.1}、{@code Sampler1}、
 *       **不调 {@code withCull}**(默认即剔除,见 {@code client/TargetSelectionHighlighter.java:250} 记录的
 *       {@code cull.orElse(true)}),只把混合换成本体 {@code TRANSLUCENT} / 外壳 {@code ADDITIVE},
 *       并把深度写关掉。{@code ALPHA_CUTOUT 0.1} 与 1.21.1 <b>逐字等价</b>:1.21.1 的
 *       {@code shaders/core/rendertype_entity_translucent.fsh} 里 `if (color.a < 0.1) discard;`
 *       是**硬编码**的(无 {@code #ifdef}),26.1.2 的 {@code shaders/core/entity.fsh} 把它做成
 *       {@code #ifdef ALPHA_CUTOUT} ⇒ 打开该 define 才是同一行为。</li>
 *   <li><b>提交方式</b>:1.21.1 是「立即模式」——{@code mc.renderBuffers().bufferSource()}
 *       {@code getBuffer(DOME)} 写顶点 + {@code endBatch(DOME)};26.1.2 改为两相渲染,
 *       {@code MultiBufferSource} 的立即写路径不参与本阶段 ⇒ 改为
 *       {@code SubmitNodeCollector#submitCustomGeometry(poseStack, renderType, (pose, buffer) -> …)},
 *       由原版在自己的特征绘制阶段统一落盘(与 {@code client/TargetSelectionHighlighter.java:157-161} 同形)。
 *       同层内多个玩家的四边形仍进**同一个** {@code VertexConsumer}({@code CustomFeatureRenderer} 对每个
 *       RenderType 取一次 buffer),与基准「一层一个 buffer」等价。</li>
 *   <li><b>提交时机</b>:{@code RenderLevelStageEvent.Stage.AFTER_ENTITIES} 在 26.1.2 已随
 *       {@code RenderLevelStageEvent} 一并消失 ⇒ 改用 {@code SubmitCustomGeometryEvent}
 *       (事件文档:fires between particle submission and rendering of opaque submits)。</li>
 *   <li><b>姿态栈与相机</b>:1.21.1 从 {@code RenderLevelStageEvent#getPoseStack()/getCamera()} 取;
 *       26.1.2 两者都不存在 ⇒ 姿态栈取 {@code SubmitCustomGeometryEvent#getPoseStack()}、
 *       相机取 {@code Minecraft#gameRenderer.getMainCamera().position()},并照基准口径在提交前
 *       {@code translate(-camPos)} 后直接写世界坐标(26.1.2 的提交姿态栈是单位阵,见
 *       {@code TargetSelectionHighlighter.java:126-128})。</li>
 *   <li><b>光照常量</b>:{@code LightTexture.FULL_BRIGHT} → {@code LightCoordsUtil.FULL_BRIGHT}。</li>
 *   <li><b>部分 tick</b>:{@code RenderLevelStageEvent#getPartialTick().getGameTimeDeltaPartialTick(false)}
 *       → {@code Minecraft#getDeltaTracker().getGameTimeDeltaPartialTick(false)}
 *       ({@code client/DeltaTracker.java:14,32,109};26.1.2 的 {@code SubmitCustomGeometryEvent} 不提供
 *       部分 tick)。</li>
 *   <li><b>⚠️ 无法等价项(两层绘制次序)</b>:1.21.1 用显式 {@code endBatch} 保证「先辉光壳、后本体球面」;
 *       26.1.2 的自定义几何由 {@code client/renderer/feature/CustomFeatureRenderer.java:53-60}
 *       按 **RenderType 分桶({@code HashMap})** 存放、{@code :35-44} 逐桶迭代 ⇒
 *       两个 RenderType 之间的先后次序<b>不受提交次序控制</b>,而 {@code RenderType} 未覆写
 *       {@code hashCode/equals}({@code rendertype/RenderType.java} 全文无这两个方法)⇒ 同帧内
 *       两层的合成次序既非基准次序、也不保证跨启动稳定。两层各自的几何、混合函数、深度/写掩码、
 *       顶点色与纹理均与基准逐字一致,差异仅在「哪一层更靠近观察者的合成结果」。
 *       若要求像素级定序,只能把两层并成同一个 RenderType(外壳退化为普通半透明混合、亮度下降),
 *       属主动降级 —— 本移植不采用,交由主代理裁决(见交付报告「未同步项/无法等价项」)。</li>
 * </ol>
 */
@EventBusSubscriber(modid = AstralDiceMod.MODID, value = Dist.CLIENT)
public final class RenShieldRenderer {
    /** 球面图案贴图(六边能量单元;生成器见 tools/gen_ren_shield_texture.py) */
    private static final Identifier SHIELD_TEXTURE =
            Identifier.fromNamespaceAndPath(AstralDiceMod.MODID, "textures/special/ren_shield.png");

    /**
     * 球体本体 pipeline:实体着色器 + 半透明混合 + LEQUAL 深度测试 + **不写深度**、**开背面剔除**。
     * 球面开背面剔除 ⇒ 每个像素最多只有一个正面片,故不需要写入深度也不会自遮挡。
     */
    private static final RenderPipeline DOME_PIPELINE = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(AstralDiceMod.MODID, "pipeline/ren_shield"))
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withSampler("Sampler1")
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
            .build();

    /** 外层发光壳 pipeline:同一着色器 + **加色混合**(只加亮、不加暗)⇒ 观感是自发光而不是半透明壳 */
    private static final RenderPipeline GLOW_PIPELINE = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(AstralDiceMod.MODID, "pipeline/ren_shield_glow"))
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withSampler("Sampler1")
            .withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
            .build();

    /**
     * 球体本体通道。{@code bufferSize} 与基准同为 256(1.21.1 的 {@code RenderType.create(..., 256, …)});
     * lightmap + overlay 与基准的 {@code LIGHTMAP}/{@code OVERLAY} 状态逐项对应。
     */
    private static final RenderType DOME = createDome("astral_dice_ren_shield", DOME_PIPELINE);

    /** 外层加色辉光壳通道 */
    private static final RenderType GLOW = createDome("astral_dice_ren_shield_glow", GLOW_PIPELINE);

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
     * 注册两个自有 pipeline(模组事件总线;26.1.2 的 {@code @EventBusSubscriber} 只剩 {@code value()/modid()},
     * 总线按事件类型自动判定 —— {@code IModBusEvent} 走模组总线,见 26.1.2 既有
     * {@code client/OutlineCaptureRenderStateModifier.java:27-31,64,75-83} 对
     * {@code RegisterRenderStateModifiersEvent} 的同形订阅)。
     */
    @SubscribeEvent
    public static void onRegisterRenderPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(DOME_PIPELINE);
        event.registerPipeline(GLOW_PIPELINE);
    }

    private static RenderType createDome(String name, RenderPipeline pipeline) {
        return RenderType.create(name,
                RenderSetup.builder(pipeline)
                        .withTexture("Sampler0", SHIELD_TEXTURE)
                        .useLightmap()
                        .useOverlay()
                        .bufferSize(256)
                        .createRenderSetup());
    }

    @SubscribeEvent
    public static void onSubmitCustomGeometry(SubmitCustomGeometryEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        PoseStack poseStack = event.getPoseStack();
        if (poseStack == null) return;
        SubmitNodeCollector collector = event.getSubmitNodeCollector();
        if (collector == null) return;

        List<Player> shielded = new ArrayList<>();
        for (Player player : mc.level.players()) {
            if (!player.hasEffect(ModEffects.REN_SHIELD)) continue;
            // 防拿上一 tick 的已移除实体
            if (mc.level.getEntity(player.getId()) != player) continue;
            if (player.distanceToSqr(mc.player) > MAX_DISTANCE * MAX_DISTANCE) continue;
            if (isFirstPersonSelf(mc, player)) continue;
            shielded.add(player);
        }
        if (shielded.isEmpty()) return;

        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);

        poseStack.pushPose();
        Vec3 cam = mc.gameRenderer.getMainCamera().position();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        try {
            // 先提交外层加色辉光壳、再提交本体:两层都不写深度,合成次序见类注释「无法等价项」
            for (Player player : shielded) {
                collector.submitCustomGeometry(poseStack, GLOW,
                        (pose, buffer) -> emitSphere(buffer, pose, player, RADIUS * GLOW_RADIUS_FACTOR, true, partialTick));
            }
            for (Player player : shielded) {
                collector.submitCustomGeometry(poseStack, DOME,
                        (pose, buffer) -> emitSphere(buffer, pose, player, RADIUS, false, partialTick));
            }
        } finally {
            poseStack.popPose();
        }
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
     * 发一个四边形。顶点顺序按叉积自动纠正:开剔除时顺序写反会让整面**静默不可见**
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
        vc.addVertex(pose, (float) p[0], (float) p[1], (float) p[2])
                .setColor(r, g, b, clamp01(a))
                .setUv(u * UV_TILES_U + scrollU, v * UV_TILES_V + scrollV)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightCoordsUtil.FULL_BRIGHT)
                .setNormal(nx, ny, nz);
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
