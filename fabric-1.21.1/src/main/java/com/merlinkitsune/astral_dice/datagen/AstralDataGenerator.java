package com.merlinkitsune.astral_dice.datagen;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;

/**
 * 《星之骰戏》Fabric 1.20.1 的数据生成入口。
 *
 * <p>对应 forge 线的 {@code DataGenerators}（那里靠 {@code @Mod.EventBusSubscriber} +
 * {@code GatherDataEvent} 注册 provider）。Fabric 没有事件总线，改用
 * {@code fabric.mod.json} 的 {@code fabric-datagen} entrypoint + {@link DataGeneratorEntrypoint}。
 *
 * <h2>怎么跑</h2>
 * <pre>./gradlew :fabric-1.20.1:runDatagen</pre>
 * 产物写入 {@code fabric-1.20.1/src/generated/resources}（由 {@code build.gradle} 的
 * datagen run 的 {@code -Dfabric-api.datagen.output-dir} 指定），
 * 再由 {@code sourceSets.main.resources.srcDir('src/generated/resources')} 纳入产物 jar。
 *
 * <h2>与 forge 线的对等性</h2>
 * 两个 provider 都是**逐条照搬** forge 线的实现（配方语义逐字保留，仅两处 NBT 药水材料
 * 因平台能力受限而放宽 —— 见 KNOWN-ISSUES KI-F1）。
 * ⇒ 本线重新生成后，{@code src/generated/resources} 的 137 个模型 + 118 个配方 + 118 个进度
 * 应与 forge 线**除那两个配方外完全一致**；出现其它差异即为漂移，需逐条核对。
 *
 * <p>⚠️ 本类在**生产环境不会被加载**（Fabric Loader 只在 datagen 模式解析
 * {@code fabric-datagen} entrypoint），但它会进产物 jar —— 这是官方模板的标准做法。
 */
public class AstralDataGenerator implements DataGeneratorEntrypoint {

    @Override
    public void onInitializeDataGenerator(FabricDataGenerator generator) {
        FabricDataGenerator.Pack pack = generator.createPack();
        // 客户端资源:物品模型
        pack.addProvider(ModItemModelProvider::new);
        // 服务端数据:配方 + 配方进度
        pack.addProvider(ModRecipeProvider::new);
    }
}
