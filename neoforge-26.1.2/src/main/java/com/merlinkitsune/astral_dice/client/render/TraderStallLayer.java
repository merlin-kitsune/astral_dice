package com.merlinkitsune.astral_dice.client.render;

// 注意：本类**不加** @OnlyIn(Dist.CLIENT)。NeoForge 26.1 已移除 @OnlyIn 的运行时成员剥离，
// 加载期会弹「模组使用了 @OnlyIn 注释」警告并阻断进入主菜单；纯度由调用点保证
// ——引用本类的 ModClientEvents 带 @EventBusSubscriber(value = Dist.CLIENT)，专用服务端不加载。

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import org.jspecify.annotations.Nullable;

/**
 * 给「卡牌商人 / 筹码商人」村民补画 Blockbench 模型里多出来的两件几何（柜台面板 + 遮阳棚）。
 *
 * <p>vanilla {@code VillagerRenderer} 只会画 {@code VillagerModel} 的 9 个盒，bbmodel 里的柜台与棚子
 * 没有对应部件；本层用 {@link TraderStallModel} 单独提交一次，贴图沿用各职业的
 * {@code textures/entity/villager/profession/<职业>.png}（与 bbmodel 内嵌贴图逐字节相同）。
 *
 * <p>只对本模组的职业生效；其它职业（含原版无职业村民）直接 return，不影响原版外观。
 */
public class TraderStallLayer extends RenderLayer<VillagerRenderState, VillagerModel> {
    private static final String TEXTURE_ROOT = "textures/entity/villager/profession/";

    private final TraderStallModel model;

    public TraderStallLayer(RenderLayerParent<VillagerRenderState, VillagerModel> renderer, TraderStallModel model) {
        super(renderer);
        this.model = model;
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords,
                       VillagerRenderState state, float yRot, float xRot) {
        if (state.isInvisible || state.isBaby) {
            return;
        }
        Identifier texture = professionTexture(state.getVillagerData());
        if (texture == null) {
            return;
        }
        renderColoredCutoutModel(this.model, texture, poseStack, submitNodeCollector, lightCoords, state, -1, 4);
    }

    /** 本模组职业 ⇒ 对应职业贴图；其余一律 null。 */
    private static @Nullable Identifier professionTexture(@Nullable VillagerData data) {
        if (data == null) {
            return null;
        }
        ResourceKey<VillagerProfession> key = data.profession().unwrapKey().orElse(null);
        if (key == null || !AstralDiceMod.MODID.equals(key.identifier().getNamespace())) {
            return null;
        }
        return key.identifier().withPath(path -> TEXTURE_ROOT + path + ".png");
    }
}
