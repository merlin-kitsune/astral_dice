package com.merlinkitsune.astral_dice.datagen;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * 服务端数据生成入口(配方 {@code data/astral_dice/recipe/*.json})。
 *
 * <p><b>26.1.2 迁移说明</b>:26.1.x 起数据生成拆分为 {@code clientData}/{@code serverData} 两种运行类型,
 * 分别派发 {@link GatherDataEvent.Client} 与 {@link GatherDataEvent.Server};配方属于
 * {@code PackType.SERVER_DATA},必须在 {@link GatherDataEvent.Server} 中注册,任务为
 * {@code :neoforge-26.1.2:runServerData}(客户端资源见 {@link ClientDataGenerators})。
 */
@EventBusSubscriber(modid = AstralDiceMod.MODID)
public class ServerDataGenerators {

    @SubscribeEvent
    public static void gatherServerData(GatherDataEvent.Server event) {
        event.addProvider(new ModRecipeProvider.Runner(event.getGenerator().getPackOutput(), event.getLookupProvider()));
    }
}
