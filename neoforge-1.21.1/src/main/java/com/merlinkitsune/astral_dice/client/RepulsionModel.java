package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.entity.RepulsionEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

/**
 * 强化拒止实体模型（几何来源：仓库根 {@code models/强化拒止 - Converted.bbmodel}，2026-10-18）。
 *
 * <p><b>几何（bbmodel 原文）</b>：两个 cube，贴图 64×64。
 * <ul>
 *   <li>cube A（blade）：{@code from (1,0,-0.5) → to (25,2,0.5)}、{@code origin (1,0,-0.5)}、
 *       {@code rotation (0,0,90)}、box UV 北面 {@code 1,1..25,3} ⇒ {@code texOffs(0,0)}；</li>
 *   <li>cube B（head）：{@code from (0,22.5,-1) → to (12,34.5,1)}、{@code origin (0,22.5,-1)}、
 *       {@code rotation (0,0,45)}、{@code uv_offset (0,3)}、北面 {@code 2,5..14,17} ⇒
 *       {@code texOffs(0,3)}。</li>
 * </ul>
 *
 * <p><b>坐标系换算</b>：bbmodel 是 **+Y 向上**、原点是地面；Minecraft 的实体模型是 **+Y 向下**，
 * 且 **模型 y = 24 才是地面**（{@code LivingEntityRenderer} 的 {@code translate(0,-1.501,0)} ×
 * 16 px = 24 px 基准）。因此：
 * <ul>
 *   <li>位置：{@code y_mc = 24 − y_bb} ⇒ 枢轴 y 由 bbmodel 的 {@code origin} 换算
 *       （A：24−0 = 24；B：24−22.5 = 1.5）；</li>
 *   <li>cube 本地坐标 y 取負（bbmodel 的 {@code 0..2} 变 {@code -2..0}、
 *       {@code 0..12} 变 {@code -12..0}）；</li>
 *   <li>旋转：**Y 轴被翻转 ⇒ 绕 Z 的角度要取反**。推导（局部坐标已按 y 取反，记
 *       {@code M = diag(1,-1)}）：{@code M·R(θ) = R(−θ)·M} ⇒ bbmodel 的 +90° / +45°
 *       在模型里写 **−90° / −45°**。数值自检：blade 的角点 (24,−2) 经 R(−90°) 得 (−2,−24)
 *       ⇒ 枢轴 y=24 减 24 = **0 = 模型最高处**，即刀刃从地面向上立起（若符号写反会钻进地下）。</li>
 * </ul>
 *
 * <p><b>UV（与作者一致）</b>：MC 1.21.1 的盒子 UV 布局为
 * {@code north = (u+d, v+d) .. (u+d+w, v+d+h)}（见 {@code ModelPart.Cube} 构造器源码，
 * {@code f5 = texCoordU + dimensionZ}、{@code f11 = texCoordV + dimensionZ}）。
 * <ul>
 *   <li>A：w=24, h=2, d=1 ⇒ {@code texOffs(0,0)} 的北面 = (1,1)..(25,3) ✓ 与作者一致；</li>
 *   <li>B：w=12, h=12, d=2 ⇒ {@code texOffs(0,3)} 的北面 = (2,5)..(14,17) ✓ 与作者一致。
 *       ⚠️ 冻结设计稿写的是 {@code texOffs(0,4)}（其推算用了 {@code v+1}），而 1.21.1 的
 *       实际布局是 {@code v+d}（d=2）⇒ (0,4) 会让盒子 UV 整体下移 1 像素、**对不上作者的面 UV**。
 *       本实现按「与作者一致」这一目的取 (0,3)（= bbmodel 自带的 {@code uv_offset}），
 *       详见 {@code docs/features/repulsion-card-spec.md} §6。</li>
 * </ul>
 *
 * <p><b>贴图</b>：bbmodel 的 {@code textures[0].source} 是 128×128 PNG，而它的 UV 画布是 64×64
 * （{@code uv_width/uv_height} = 64）⇒ 按 2:1 最近邻降采样成 64×64 写入
 * {@code textures/entity/repulsion.png}，使 UV 网格与像素一一对应。
 *
 * <p><b>⚠️ 未实机验证</b>：
 * <ul>
 *   <li>{@code LivingEntityRenderer} 会施加 {@code scale(-1,-1,1)} ⇒ 模型在 X 上被镜像；
 *       本几何在 X 方向近似对称（刀刃只占 2 px 宽、菱形头本身对称），故不影响造型，
 *       但面向北 / 南的贴图会有水平镜像（静态无法判定观感）；</li>
 *   <li>Z 旋转符号按上式推导，未在游戏内目视核对。</li>
 * </ul>
 */
public class RepulsionModel extends EntityModel<RepulsionEntity> {

    /** 模型层 id：{@code astral_dice:repulsion#main}。 */
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "repulsion"), "main");

    /** bbmodel 的 +90°（绕 Z）在 Y 翻转后取反。 */
    private static final float BLADE_TWIST = (float) Math.toRadians(90.0D);

    /** bbmodel 的 +45°（绕 Z）在 Y 翻转后取反。 */
    private static final float HEAD_TWIST = (float) Math.toRadians(45.0D);

    private final ModelPart root;

    public RepulsionModel(ModelPart root) {
        super();
        this.root = root;
    }

    /**
     * 几何定义：一根 24×2×1 的刀刃（绕 Z −90° ⇒ 竖直立起）+ 一个 12×12×2 的菱形头（绕 Z −45°）。
     *
     * <p>blade：pose 枢轴 {@code (1, 24, -0.5)}、绕 Z −90°、cube {@code (0,-2,0)} 尺寸 {@code (24,2,1)}、UV {@code (0,0)}；
     * head：pose 枢轴 {@code (0, 1.5, -1)}、绕 Z −45°、cube {@code (0,-12,0)} 尺寸 {@code (12,12,2)}、UV {@code (0,3)}。
     */
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild("blade",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(0.0F, -2.0F, 0.0F, 24.0F, 2.0F, 1.0F),
                PartPose.offsetAndRotation(1.0F, 24.0F, -0.5F, 0.0F, 0.0F, -BLADE_TWIST));

        root.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 3)
                        .addBox(0.0F, -12.0F, 0.0F, 12.0F, 12.0F, 2.0F),
                PartPose.offsetAndRotation(0.0F, 1.5F, -1.0F, 0.0F, 0.0F, -HEAD_TWIST));

        return LayerDefinition.create(mesh, 64, 64);
    }

    /** 强化拒止没有动画（不摆臂、不走动），姿态完全由 static 的 PartPose 决定。 */
    @Override
    public void setupAnim(RepulsionEntity entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight,
                               int packedOverlay, int color) {
        this.root.render(poseStack, buffer, packedLight, packedOverlay, color);
    }
}
