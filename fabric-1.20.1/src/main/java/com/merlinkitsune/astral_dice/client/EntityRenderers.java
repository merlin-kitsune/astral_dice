package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.entity.ModEntityTypes;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

/**
 * 实体渲染器 / 实体模型层的**注册**（fabric 线独有；2026-11-01 新建）。
 *
 * <p><b>为什么需要这个类</b>：forge / neoforge 两线把渲染器登记写在
 * {@code EntityRenderersEvent.RegisterRenderers}（{@code @EventBusSubscriber(value = Dist.CLIENT, bus = MOD)}）里，
 * 由平台自动调用；<b>Fabric 没有事件总线</b>，{@code EntityRendererRegistry} 与
 * {@code EntityModelLayerRegistry} 都是**命令式注册表**，必须在 client entrypoint
 * （{@code ClientModInitializer#onInitializeClient}）里主动调用一次。
 *
 * <p><b>调用位置（Lead 接线）</b>：{@code AstralDiceClient#onInitializeClient()} 末尾调用一次
 * {@code com.merlinkitsune.astral_dice.client.EntityRenderers.bootstrapClient();}
 * 本类只提供静态入口，不修改 {@code AstralDiceClient}。
 *
 * <p><b>重复调用会崩</b>（已按 fabric-rendering-v1 3.0.10 源码核实）：
 * {@code EntityModelLayerRegistry.registerModelLayer} 内部是 {@code Map.putIfAbsent} +
 * 冲突时抛 {@code IllegalArgumentException}（"Cannot replace registration for entity model layer ..."）；
 * {@code EntityRendererRegistry.register} 同样是单次登记。因此本方法**只能调用一次**，
 * 不要放进任何会被重复执行的入口。
 *
 * <p>模型层 id 用的是 {@code ModelLayerLocation(new ResourceLocation(MODID, "barricade"|"repulsion"), "main")}
 * （见 {@link BarricadeModel#LAYER_LOCATION} / {@link RepulsionModel#LAYER_LOCATION}），
 * 与 forge 线逐字一致 —— 烘焙入口 {@code EntityRendererProvider.Context#bakeLayer} 走同一套 id。
 */
public final class EntityRenderers {

    private EntityRenderers() {
    }

    /** 一次性登记 2 个自有实体的模型层与渲染器（客户端启动时调用且仅一次）。 */
    public static void bootstrapClient() {
        // 路障：模型层必须**先于**渲染器登记 —— LivingEntityRenderer 的构造器里就会 bakeLayer，
        // 渲染器真正被实例化时（实体首次进入视野）模型层必须已经在表里。
        EntityModelLayerRegistry.registerModelLayer(BarricadeModel.LAYER_LOCATION,
                BarricadeModel::createBodyLayer);
        EntityRendererRegistry.register(ModEntityTypes.BARRICADE.get(), BarricadeRenderer::new);

        EntityModelLayerRegistry.registerModelLayer(RepulsionModel.LAYER_LOCATION,
                RepulsionModel::createBodyLayer);
        EntityRendererRegistry.register(ModEntityTypes.REPULSION.get(), RepulsionRenderer::new);

        // 金鲤立牌(souri)放置的饮品实体:GeckoLib 渲染器自行按 variant 选模型/贴图,无需模型层登记。
        EntityRendererRegistry.register(ModEntityTypes.SOURI_DRINK.get(), SouriDrinkRenderer::new);
    }
}
