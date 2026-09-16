package com.merlinkitsune.astral_dice.datagen;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.minecraft.data.DataGenerator;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * 数据生成入口。
 *
 * <p><b>26.1.2 迁移说明</b>:①{@code @EventBusSubscriber} 在 FML 11 只剩 {@code value()}/{@code modid()},
 * 不再有 {@code bus = Bus.MOD}(mod bus 与 game bus 已统一);②{@code GatherDataEvent} 不再提供
 * {@code includeClient()}/{@code includeServer()} 与 {@code getExistingFileHelper()},
 * provider 一律经 {@code event.addProvider(...)} 注册。
 */
@EventBusSubscriber(modid = AstralDiceMod.MODID)
public class DataGenerators {

    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();

        // 客户端资源:物品模型定义(assets/astral_dice/items/*.json)
        event.addProvider(new ModItemModelProvider(generator.getPackOutput()));

        // 服务端数据:配方(经 Runner 包装,才能作为 DataProvider 被 addProvider 接受)
        event.addProvider(new ModRecipeProvider.Runner(generator.getPackOutput(), event.getLookupProvider()));
    }
}