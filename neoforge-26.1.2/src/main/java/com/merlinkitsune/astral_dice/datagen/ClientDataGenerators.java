package com.merlinkitsune.astral_dice.datagen;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * 客户端资源数据生成入口(物品模型定义 {@code assets/astral_dice/items/*.json})。
 *
 * <p><b>26.1.2 迁移说明(与 1.21.1 的关键差异)</b>:26.1.x 的 NeoForge 把数据生成拆成
 * 两种运行类型与两个事件 —— {@code clientData}(主类 {@code net.neoforged.fml.startup.DataClient})
 * 只派发 {@link GatherDataEvent.Client},而 {@code serverData}({@code DataServer})只派发
 * {@link GatherDataEvent.Server}。{@code GatherDataEvent} 本身已变为 <b>abstract</b>,
 * 旧的「一个事件 + {@code includeClient()/includeServer()}」写法在 26.1.2 会直接加载失败
 * ({@code Cannot register listeners for abstract class GatherDataEvent})。
 *
 * <p>因为物品模型依赖客户端类({@code net.minecraft.client.data.models.ModelProvider}),
 * 本类必须以 {@code value = Dist.CLIENT} 限定 —— 服务端数据生成运行(serverData)的 classpath
 * 不含客户端类,不加限定会在加载本类时因找不到客户端父类而崩溃。
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = AstralDiceMod.MODID)
public class ClientDataGenerators {

    @SubscribeEvent
    public static void gatherClientData(GatherDataEvent.Client event) {
        event.addProvider(new ModItemModelProvider(event.getGenerator().getPackOutput()));
    }
}
