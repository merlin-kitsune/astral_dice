package com.merlinkitsune.astral_dice.client.render;

// 注意：本类**不加** @OnlyIn(Dist.CLIENT)。NeoForge 26.1 已移除 @OnlyIn 的运行时成员剥离，
// 加载期会弹「模组使用了 @OnlyIn 注释」警告并阻断进入主菜单；纯度由调用点保证
// ——引用本类的 ModClientEvents 带 @EventBusSubscriber(value = Dist.CLIENT)，专用服务端不加载。

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.VillagerRenderer;

/**
 * 在 vanilla {@link VillagerRenderer} 之上挂 {@link TraderStallLayer}。
 *
 * <p>为什么替换整个渲染器而不是给原版渲染器 {@code addLayer}：{@code LivingEntityRenderer#addLayer}
 * 是实例方法，拿不到原版已注册的 {@code VillagerRenderer} 实例；NeoForge 只提供
 * {@code EntityRenderersEvent.RegisterRenderers#registerEntityRenderer}，故注册本子类覆盖原版。
 * 行为与原版一致（本类只多一层，且该层对非本模组职业直接返回）。
 */
public class TraderStallRenderer extends VillagerRenderer {
    public TraderStallRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.addLayer(new TraderStallLayer(this, new TraderStallModel(context.bakeLayer(TraderStallModel.LAYER))));
    }
}
