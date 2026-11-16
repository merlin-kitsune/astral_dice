package com.merlinkitsune.astral_dice.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.util.Map;
import java.util.TreeMap;

/** 扫描 data/<namespace>/astral_dice/anvil/*.json 并落到 {@link AstralDiceDataStore}（NeoForge 26.1.2）。 */
public final class AnvilUpgradeReloadListener extends SimplePreparableReloadListener<Map<Identifier, JsonElement>> {

    public static final String DIRECTORY = "astral_dice/anvil";
    private static final Logger LOGGER = LoggerFactory.getLogger(AnvilUpgradeReloadListener.class);

    @Override
    protected Map<Identifier, JsonElement> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<Identifier, JsonElement> result = new TreeMap<>();
        for (Map.Entry<Identifier, Resource> entry : resourceManager
                .listResources(DIRECTORY, id -> id.getPath().endsWith(".json")).entrySet()) {
            try (Reader reader = entry.getValue().openAsReader()) {
                result.put(entry.getKey(), JsonParser.parseReader(reader));
            } catch (RuntimeException | java.io.IOException ex) {
                LOGGER.warn("[astral_dice] 跳过无法读取的铁砧数据 {}: {}", entry.getKey(), ex.getMessage());
            }
        }
        return result;
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> files, ResourceManager resourceManager, ProfilerFiller profiler) {
        AnvilUpgradeData chosen = null;
        Identifier chosenId = null;
        for (Map.Entry<Identifier, JsonElement> entry : new TreeMap<>(files).entrySet()) {
            try {
                JsonElement element = entry.getValue();
                if (element == null || !element.isJsonObject()) {
                    throw new IllegalArgumentException("文件内容不是 JSON 对象");
                }
                AnvilUpgradeData parsed = AnvilUpgradeData.parse(element.getAsJsonObject());
                if (chosen != null) {
                    LOGGER.warn("[astral_dice] 存在多份铁砧数据，按资源 id 字典序只采用最后一份: 忽略 {} 采用 {}", chosenId, entry.getKey());
                }
                chosen = parsed;
                chosenId = entry.getKey();
            } catch (RuntimeException ex) {
                LOGGER.warn("[astral_dice] 跳过非法铁砧数据 {}: {}", entry.getKey(), ex.getMessage());
            }
        }
        if (chosen == null) {
            AstralDiceDataStore.clearAnvil();
            LOGGER.info("[astral_dice] 未找到有效的 data/*/astral_dice/anvil/*.json，星愿铁砧回退内置兜底数值");
            return;
        }
        AstralDiceDataStore.replaceAnvil(chosen);
        LOGGER.info("[astral_dice] 铁砧数据已加载 {} -> {}", chosenId, chosen);
    }
}
