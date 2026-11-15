package com.merlinkitsune.astral_dice.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.merlinkitsune.astral_dice.event.LootInjectionHandler;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** 扫描 data/<namespace>/astral_dice/loot/*.json 并回填本次重载的已排队表。 */
public final class LootInjectionReloadListener extends SimpleJsonResourceReloadListener {

    public static final String DIRECTORY = "astral_dice/loot";
    private static final Logger LOGGER = LoggerFactory.getLogger(LootInjectionReloadListener.class);
    public LootInjectionReloadListener() {
        super(new Gson(), DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<String, LootRule> merged = new LinkedHashMap<>();
        int validFiles = 0;
        for (Map.Entry<ResourceLocation, JsonElement> entry : new TreeMap<>(files).entrySet()) {
            final ResourceLocation fileId = entry.getKey();
            try {
                JsonElement element = entry.getValue();
                if (element == null || !element.isJsonObject()) {
                    throw new IllegalArgumentException("文件内容不是 JSON 对象");
                }
                LootInjectionData parsed = LootInjectionData.parse(
                        element.getAsJsonObject(),
                        message -> LOGGER.warn("[astral_dice] 战利品数据 {} 的 {}", fileId, message));
                validFiles++;
                for (LootRule rule : parsed.rules()) {
                    LootRule previous = merged.putIfAbsent(rule.id(), rule);
                    if (previous != null) {
                        LOGGER.warn("[astral_dice] 战利品规则 id 重复，保留先出现的一条: {}", rule.id());
                    }
                }
            } catch (RuntimeException ex) {
                LOGGER.warn("[astral_dice] 跳过非法战利品数据 {}: {}", fileId, ex.getMessage());
            }
        }
        if (validFiles == 0) {
            AstralDiceDataStore.clearLoot();
            LOGGER.info("[astral_dice] 未找到有效的 data/*/astral_dice/loot/*.json，战利品注入停用（零规则）");
            return;
        }
        AstralDiceDataStore.replaceLoot(LootInjectionData.of(new ArrayList<>(merged.values())));
        List<LootInjectionHandler.PendingTable> pending = LootInjectionHandler.drainPending();
        LootInjectionHandler.injectPending(pending, AstralDiceDataStore.loot());
        LOGGER.info("[astral_dice] 战利品注入规则已加载 {} 条（来自 {} 个文件，回填 {} 张表）",
                merged.size(), validFiles, pending.size());
    }
}
