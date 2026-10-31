package com.merlinkitsune.astral_dice.client.render;

// 注意：本类**不加** @OnlyIn(Dist.CLIENT)。NeoForge 26.1 已移除 @OnlyIn 的运行时成员剥离，
// 加载期会弹「模组使用了 @OnlyIn 注释」警告并阻断进入主菜单；纯度由调用点保证
// ——引用本类的 ModClientEvents 带 @EventBusSubscriber(value = Dist.CLIENT)，专用服务端不加载。

import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.resources.Identifier;

/**
 * 卡牌商人 / 筹码商人村民的「摊位」附加几何。
 *
 * <p>来源：{@code models/cardshop_villager.bbmodel}（Blockbench 源）。该模型与 vanilla
 * {@code VillagerModel} 的 9 个盒完全一致，多出来的只有两件：
 * <ul>
 *   <li>{@code arms} 的第三个元素 {@code [-4,16,-2] -> [4,20,2]}：横在两臂之间的柜台面板（UV 40,38）；</li>
 *   <li>{@code cube} 元素 {@code [-4,32.5,-7.25] -> [4,32.5,-4.25]}，{@code rotation=[-20,0,0]}，
 *       {@code origin=[-3,32.5,-6.25]}：零厚度的遮阳棚平板（UV 29,52）。</li>
 * </ul>
 * vanilla 渲染器没有这两个部件，故用本模型在 {@link TraderStallLayer} 里补画。
 *
 * <p>坐标换算：Blockbench 的 y 向上、以脚为原点；vanilla 的 {@code ModelPart} y 向下、
 * 以「髋部（body 顶端）」为原点，换算关系为 {@code vanillaY = 24 - bbY}；x/z 同值。
 */
public class TraderStallModel extends EntityModel<VillagerRenderState> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(Identifier.fromNamespaceAndPath(AstralDiceMod.MODID, "trader_stall"), "main");

    /** BB 的 {@code arms} 柜台面板：bb (-4,16,-2)-(4,20,2) ⇒ vanilla (-4,4,-2)-(4,8,2)。 */
    private static final float COUNTER_U = 40.0F;
    private static final float COUNTER_V = 38.0F;
    /** BB 的 {@code cube} 遮阳棚：零厚度、-20° 前倾，{@code origin=[-3,32.5,-6.25]} ⇒ vanilla (-3,-8.5,-6.25)。
     * <p>角度符号：BB 的 +Y 向上、MC 的 +Y 向下，BB 的 -20°（前缘下沉）对应 MC 的 +20°。 */
    private static final float AWNING_U = 29.0F;
    private static final float AWNING_V = 52.0F;

    private final ModelPart head;

    public TraderStallModel(ModelPart root) {
        super(root);
        this.head = root.getChild("head");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild(
                "counter",
                CubeListBuilder.create()
                        .texOffs((int) COUNTER_U, (int) COUNTER_V)
                        .addBox(-4.0F, 4.0F, -2.0F, 8.0F, 4.0F, 4.0F),
                PartPose.ZERO);

        // 遮阳棚挂在 head 下（BB 的 outliner 里两者同组），随头部俯仰/偏航一起转。
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
        head.addOrReplaceChild(
                "awning",
                CubeListBuilder.create()
                        .texOffs((int) AWNING_U, (int) AWNING_V)
                        .addBox(-1.0F, 0.0F, -1.0F, 8.0F, 0.0F, 3.0F),
                PartPose.offsetAndRotation(-3.0F, -8.5F, -6.25F, (float) Math.toRadians(20.0), 0.0F, 0.0F));

        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(VillagerRenderState state) {
        super.setupAnim(state);
        this.head.yRot = state.yRot * (float) (Math.PI / 180.0);
        this.head.xRot = state.xRot * (float) (Math.PI / 180.0);
    }
}
