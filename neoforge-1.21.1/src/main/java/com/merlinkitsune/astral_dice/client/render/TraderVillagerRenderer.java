package com.merlinkitsune.astral_dice.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.VillagerRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.Villager;

/**
 * 卡牌 / 筹码商人的村民渲染器：只把「本模组职业」换成 {@link TraderVillagerModel}（无帽），
 * 其余职业与幼年村民仍走原版模型。层、贴图、缩放全部继承 {@code VillagerRenderer}，没有新增层。
 *
 * <p>为什么不直接改原版模型：原版渲染器对所有村民共用同一个 {@code VillagerModel}，
 * 直接隐藏 {@code hat} 会把全世界的村民帽子一起摘掉。
 */
public class TraderVillagerRenderer extends VillagerRenderer {
    private final VillagerModel<Villager> vanillaModel;
    private final VillagerModel<Villager> traderModel;

    public TraderVillagerRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.vanillaModel = this.model;
        this.traderModel = new TraderVillagerModel(context.bakeLayer(ModelLayers.VILLAGER));
    }

    @Override
    public void render(Villager entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        VillagerModel<Villager> previous = this.model;
        if (isCardTrader(entity)) {
            this.model = this.traderModel;
        }
        try {
            super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
        } finally {
            this.model = previous;
        }
    }

    /**
     * 只有「卡牌商人」用无帽模型：它的职业贴图把草帽画在 {@code hat} 盒上，而 Blockbench 源里没有帽子。
     * 「筹码商人」的墨镜同样画在 {@code hat} 盒上，所以它必须继续用原版模型，否则墨镜会一起消失。
     */
    private static boolean isCardTrader(Villager villager) {
        ResourceLocation id = BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession());
        return id != null
                && AstralDiceMod.MODID.equals(id.getNamespace())
                && "card_trader".equals(id.getPath());
    }
}
