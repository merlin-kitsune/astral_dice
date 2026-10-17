package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.entity.BarricadeEntity;
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
 * 路障实体模型（几何来源：仓库根 {@code models/路障 - Converted.bbmodel}，2026-10-17）。
 *
 * <p><b>坐标系换算（最易错的一步）</b>：bbmodel 是 **+Y 向上**、原点是路障底面；
 * Minecraft 的实体模型是 **+Y 向下**，且 **模型 y = 24 才是地面**（渲染器在
 * {@code LivingEntityRenderer#render} 里 {@code scale(-1,-1,1)} + {@code translate(0,-1.501,0)}）。
 * 因此：
 * <ul>
 *   <li>位置：{@code y_mc = 24 − y_bb} ⇒ 枢轴 y 由 bbmodel 的 {@code origin} 换算
 *       （A：24−0 = 24；B：24−0.4 = 23.6），方块局部 y 写成 {@code −14 .. 0}；</li>
 *   <li>旋转：**Y 轴被翻转 ⇒ 绕 X 的角度要取反**（bbmodel 的 −22.5° 在这里写 +22.5°，
 *       bbmodel 的 +22.5° 写 −22.5°），否则两块板会朝外张成「V」形而不是向内的「Λ」形 ——
 *       与 bbmodel 里看到的形状相反。</li>
 * </ul>
 *
 * <p><b>UV</b>：bbmodel 用盒子 UV（box_uv），A 占据贴图 0..42 × 0..15、B 占据 0..42 × 15..30，
 * 与 {@code texOffs(0,0)} / {@code texOffs(0,15)} 的盒子展开完全对应（20×14×1 的盒子）。
 */
public class BarricadeModel extends EntityModel<BarricadeEntity> {

    /** 模型层 id：{@code astral_dice:barricade#main}。 */
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "barricade"), "main");

    /** 22.5°（弧度）。 */
    private static final float TILT = (float) Math.toRadians(22.5D);

    private final ModelPart root;

    public BarricadeModel(ModelPart root) {
        super();
        this.root = root;
    }

    /**
     * 几何定义：两块 20×14×1 的板。
     *
     * <p>A：pose 枢轴 {@code (0, 24, 5)}、绕 X +22.5°、cube {@code (-10.01, -14, 0)} 尺寸 {@code (20,14,1)}、UV {@code (0,0)}；
     * B：pose 枢轴 {@code (0, 23.6, -6)}、绕 X −22.5°、cube {@code (-10, -14, 0)} 尺寸 {@code (20,14,1)}、UV {@code (0,15)}。
     */
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild("panel_a",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-10.01F, -14.0F, 0.0F, 20.0F, 14.0F, 1.0F),
                PartPose.offsetAndRotation(0.0F, 24.0F, 5.0F, TILT, 0.0F, 0.0F));

        root.addOrReplaceChild("panel_b",
                CubeListBuilder.create().texOffs(0, 15)
                        .addBox(-10.0F, -14.0F, 0.0F, 20.0F, 14.0F, 1.0F),
                PartPose.offsetAndRotation(0.0F, 23.6F, -6.0F, -TILT, 0.0F, 0.0F));

        return LayerDefinition.create(mesh, 64, 64);
    }

    /** 路障没有动画（不摆臂、不走动），姿态完全由 static 的 PartPose 决定。 */
    @Override
    public void setupAnim(BarricadeEntity entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight,
                               int packedOverlay, int color) {
        this.root.render(poseStack, buffer, packedLight, packedOverlay, color);
    }
}
