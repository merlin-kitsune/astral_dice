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
 * <p><b>几何（bbmodel 原文）</b>：两个 cube，贴图 128×128（UV 画布 64，见文末「贴图密度」）。
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
 *   <li>cube 本地坐标 y 取負（bbmodel 的 {@code 0..2} 变 {@code -2..0}；head 经上述 90° 修正后取 {@code 0..12}，顶点集合不变）；</li>
 *   <li>旋转：**绕 Z 的角度不能只按「取反」推导**。局部坐标已按 y 取反（记 {@code M = diag(1,-1)}：
 *       {@code M·R(θ) = R(−θ)·M}）只能保证**轮廓**正确（正/背面软件渲染 IoU = 1.000 / 0.991）；
 *       贴图朝向还要叠加 {@code LivingEntityRenderer} 渲染期的 {@code scale(-1,-1,1)}
 *       （= 绕 Z 转 180°，det = +1，**不是镜像**）⇒ 旧写法 blade −90° 恰好正确，而 head 的 −45°
 *       会让牌面图案相对 bbmodel **整体歪 90°**（2026-10-18 实机复现），正确值是 **−135°**。</li>
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
 * <p><b>贴图</b>：bbmodel 的 {@code textures[0].source} 是 128×128 PNG，UV 画布 64×64
 * （{@code uv_width/uv_height} = 64）⇒ 运行时直接用这张 128×128 原图写
 * {@code textures/entity/repulsion.png}（同一 UV 矩形 ⇒ 两倍纹素密度，见文末）；但北面块 (4,10)-(28,34) 已替换为「南面块旋转 90° 顺时针」，两个面因而显示同一张正立图案。
 *
 * <p><b>实机核对（2026-10-18，neoforge-1.21.1 dev 客户端）</b>：
 * <ul>
 *   <li>轮廓 / 尺寸 / 位置与 bbmodel 一致（正、背面软件渲染 IoU = 1.000 / 0.991）；</li>
 *   <li>首版 head 用 −45°（bbmodel +45° 取反）时**牌面图案相对 bbmodel 歪 90°**（实机截图可见）；改为 −135° 后轮廓完全不变 ——
 *       但「−135° 已与 bbmodel 对齐」这个结论**在二轮被用户判定「颠倒」推翻**（当时的软件渲染器绕 pivot 转了绝对坐标、屏幕 x 符号也错，图本身不可信）。</li>
 *   <li>Z 旋转只改贴图朝向、不动轮廓的原因：12×12 正方形绕自身中心转 90° 映射到自身，
 *       故 {@code addBox} 的 y 偏移同步由 −12 改 0，8 个顶点与旧实现**逐点相同**。</li>
 *   <li><b>二轮定量（2026-10-18，用户截图）</b>：把实机牌面按菱形四角反算回贴图网格、再与两个面的矩形做三分类匹配 ⇒ 实机牌面 == 北面块旋转 90°（一致率 1.000），相对 Blockbench 正视外观**差 180°**。
 *       ⇒ 几何完全不动（θ 仍 −135°），只把运行时贴图的**北面 UV 块**换成「南面块旋转 90° 顺时针」：两面同图案、正立。</li>
 *   <li><b>贴图密度（128 贴图 + 64 UV 画布）</b>：MC 的 UV 归一化基准是 {@code LayerDefinition.create(mesh, texW, texH)}
 *       声明的 64，而不是 PNG 的像素尺寸；因此把 {@code repulsion.png} 换成 bbmodel 内嵌的 128×128 原图后，
 *       同一 UV 矩形采到的是**两倍纹素密度**的同一区域（{@code (2,5)..(14,17)} 由 12×12 纹素变成 24×24），
 *       布局与形状完全不变、无需改 {@code texOffs}/{@code texW}。若把 {@code texW} 也改成 128，
 *       盒式 UV 的 {@code u+d / u+d+w} 递推仍按**方块尺寸**（12/12/2）算，矩形会缩到中间半幅而错位。</li>
 * </ul>
 */
public class RepulsionModel extends EntityModel<RepulsionEntity> {

    /** 模型层 id：{@code astral_dice:repulsion#main}。 */
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "repulsion"), "main");

    /** bbmodel 的 +90°（绕 Z）⇒ 模型侧 −90°（该角度取值恰好与「取反」一致，见类注释）。 */
    private static final float BLADE_TWIST = (float) Math.toRadians(90.0D);

    /** bbmodel 的 +45°（绕 Z）⇒ 模型侧 −135°（比「取反」多 90°：叠加渲染期 scale(-1,-1,1) 后北面贴图才与 bbmodel 同向）。 */
    private static final float HEAD_TWIST = (float) Math.toRadians(135.0D);

    private final ModelPart root;

    public RepulsionModel(ModelPart root) {
        super();
        this.root = root;
    }

    /**
     * 几何定义：一根 24×2×1 的刀刃（绕 Z −90° ⇒ 竖直立起）+ 一个 12×12×2 的菱形头（绕 Z −135°）。
     *
     * <p>blade：pose 枢轴 {@code (1, 24, -0.5)}、绕 Z −90°、cube {@code (0,-2,0)} 尺寸 {@code (24,2,1)}、UV {@code (0,0)}；
     * head：pose 枢轴 {@code (0, 1.5, -1)}、绕 Z −135°、cube {@code (0,0,0)} 尺寸 {@code (12,12,2)}、UV {@code (0,3)}。
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
                        .addBox(0.0F, 0.0F, 0.0F, 12.0F, 12.0F, 2.0F),
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
