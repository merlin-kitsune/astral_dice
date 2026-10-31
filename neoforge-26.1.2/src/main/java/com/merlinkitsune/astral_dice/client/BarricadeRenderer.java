package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.entity.BarricadeEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/**
 * 路障渲染器（2026-10-17）：{@link LivingEntityRenderer} 的标准装配 ——
 * 模型从 {@link BarricadeModel#LAYER_LOCATION} 烘焙，贴图取自
 * {@code assets/astral_dice/textures/entity/barricade.png}（由 bbmodel 的
 * {@code textures[0].source} base64 解出，64×64）。
 *
 * <p>阴影半径 0.5：路障宽约 1.25 格，阴影略窄于碰撞方块；位移/朝向一律交给
 * {@code LivingEntityRenderer} 的默认实现（{@code yBodyRot} 由实体放置时写死为 180° 的倍数）。
 */
public class BarricadeRenderer extends LivingEntityRenderer<BarricadeEntity, LivingEntityRenderState, BarricadeModel> {

    public static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
            AstralDiceMod.MODID, "textures/entity/barricade.png");

    public BarricadeRenderer(EntityRendererProvider.Context context) {
        super(context, new BarricadeModel(context.bakeLayer(BarricadeModel.LAYER_LOCATION)), 0.5F);
    }

    @Override
    public LivingEntityRenderState createRenderState() {
        // 26.1.2：实体渲染改为「先抽取状态、再拿状态渲染」，渲染器必须给出自己的状态对象。
        // 本实体没有任何额外渲染状态，直接用原版 LivingEntityRenderState。
        return new LivingEntityRenderState();
    }

    @Override
    public void extractRenderState(BarricadeEntity entity, LivingEntityRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
    }

    @Override
    public Identifier getTextureLocation(LivingEntityRenderState state) {
        return TEXTURE;
    }

    /**
     * 不显示头顶名条（2026-10-18）：本实体不走 {@code MobRenderer}，而是直接继承
     * {@link LivingEntityRenderer}；它的 {@code shouldShowName} 只判「距离 + 不在载具里」，
     * <b>不查 {@code customName}</b> —— 原版那条「只有自定义名才画名条」的门禁写在
     * {@code MobRenderer#shouldShowName} 里。所以不覆写的话，立牌类实体头顶会一直画出
     * 实体名（形如「强化拒止」）。路障 / 强化拒止都不是生物，一律不显示名条。
     */
    @Override
    protected boolean shouldShowName(BarricadeEntity entity, double distanceToCameraSq) {
        return false;
    }
}
