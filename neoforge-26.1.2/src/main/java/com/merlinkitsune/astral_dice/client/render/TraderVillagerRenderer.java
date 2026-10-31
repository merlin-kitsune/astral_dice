// 不加 @OnlyIn(Dist.CLIENT)：同上，纯度由 ModClientEvents 的 @EventBusSubscriber(value = Dist.CLIENT) 保证。
package com.merlinkitsune.astral_dice.client.render;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.CrossedArmsItemLayer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.VillagerProfessionLayer;
import net.minecraft.client.renderer.entity.state.HoldingEntityRenderState;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

/**
 * 原版村民渲染器（层、贴图、状态提取都照抄 {@code VillagerRenderer}），只在 {@code submit} 里按职业换模型：
 * 本模组的 {@code card_trader} / {@code chip_trader} 用 {@link TraderVillagerModel}（帽子与帽沿关闭），
 * 其余职业与幼年村民仍然用原版模型。
 *
 * <p>为什么不直接继承 {@code VillagerRenderer}：它的父类 {@code AgeableMobRenderer.submit(...)}
 * 每帧都会执行 {@code this.model = state.isBaby ? this.babyModel : this.adultModel;}，
 * 子类在里面改 {@code model} 会被这一行原地覆盖（实测无效）。这里改继承 {@code MobRenderer}，
 * 保留同样的父类能力，同时在上层自行决定 {@code model}。
 */
public class TraderVillagerRenderer extends MobRenderer<Villager, VillagerRenderState, VillagerModel> {
    private static final Identifier VILLAGER_BASE_LOCATION = Identifier.withDefaultNamespace("textures/entity/villager/villager.png");
    private static final Identifier VILLAGER_BABY_LOCATION = Identifier.withDefaultNamespace("textures/entity/villager/villager_baby.png");
    private static final CustomHeadLayer.Transforms CUSTOM_HEAD_TRANSFORMS = new CustomHeadLayer.Transforms(-0.1171875F, -0.07421875F, 1.0F);

    private final VillagerModel vanillaModel;
    private final VillagerModel babyModel;
    private final TraderVillagerModel traderModel;

    public TraderVillagerRenderer(EntityRendererProvider.Context context) {
        super(context, new VillagerModel(context.bakeLayer(ModelLayers.VILLAGER)), 0.5F);
        this.vanillaModel = this.model;
        this.babyModel = new VillagerModel(context.bakeLayer(ModelLayers.VILLAGER_BABY));
        this.traderModel = new TraderVillagerModel(context.bakeLayer(ModelLayers.VILLAGER));
        this.addLayer(new CustomHeadLayer<>(this, context.getModelSet(), context.getPlayerSkinRenderCache(), CUSTOM_HEAD_TRANSFORMS));
        this.addLayer(
            new VillagerProfessionLayer<>(
                this,
                context.getResourceManager(),
                "villager",
                new VillagerModel(context.bakeLayer(ModelLayers.VILLAGER_NO_HAT)),
                new VillagerModel(context.bakeLayer(ModelLayers.VILLAGER_BABY_NO_HAT))
            )
        );
        this.addLayer(new CrossedArmsItemLayer<>(this));
    }

    @Override
    public void submit(VillagerRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
                       CameraRenderState camera) {
        VillagerModel previous = this.model;
        this.model = state.isBaby ? this.babyModel : (isCardTrader(state.getVillagerData()) ? this.traderModel : this.vanillaModel);
        try {
            super.submit(state, poseStack, submitNodeCollector, camera);
        } finally {
            this.model = previous;
        }
    }

    /**
     * 只有「卡牌商人」用无帽模型：它的职业贴图把草帽画在 {@code hat} 盒上，而 Blockbench 源里没有帽子。
     * 「筹码商人」的墨镜同样画在 {@code hat} 盒上，所以它必须继续用原版模型，否则墨镜会一起消失。
     */
    private static boolean isCardTrader(@Nullable VillagerData data) {
        if (data == null) {
            return false;
        }
        ResourceKey<VillagerProfession> key = data.profession().unwrapKey().orElse(null);
        return key != null
                && AstralDiceMod.MODID.equals(key.identifier().getNamespace())
                && "card_trader".equals(key.identifier().getPath());
    }

    @Override
    public Identifier getTextureLocation(VillagerRenderState state) {
        return state.isBaby ? VILLAGER_BABY_LOCATION : VILLAGER_BASE_LOCATION;
    }

    @Override
    protected float getShadowRadius(VillagerRenderState state) {
        float radius = super.getShadowRadius(state);
        return state.isBaby ? radius * 0.5F : radius;
    }

    @Override
    public VillagerRenderState createRenderState() {
        return new VillagerRenderState();
    }

    @Override
    public void extractRenderState(Villager entity, VillagerRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        HoldingEntityRenderState.extractHoldingEntityRenderState(entity, state, this.itemModelResolver);
        state.isUnhappy = entity.getUnhappyCounter() > 0;
        state.villagerData = entity.getVillagerData();
    }
}
