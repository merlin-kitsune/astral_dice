package com.merlinkitsune.astral_dice.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.TreeMap;

/** 扫描 data/<namespace>/astral_dice/anvil/*.json 并落到 {@link AstralDiceDataStore}（Fabric）。 */
public final class FabricAnvilUpgradeReloadListener extends SimpleJsonResourceReloadListener
        implements IdentifiableResourceReloadListener {

    public static final String DIRECTORY = "astral_dice/anvil";
    private static final ResourceLocation FABRIC_ID = new ResourceLocation("astral_dice", "anvil_upgrade");
    private static final Logger LOGGER = LoggerFactory.getLogger(FabricAnvilUpgradeReloadListener.class);

    public FabricAnvilUpgradeReloadListener() {
        super(new Gson(), DIRECTORY);
    }

    @Override
    public ResourceLocation getFabricId() {
        return FABRIC_ID;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resourceManager, ProfilerFiller profiler) {
        AnvilUpgradeData chosen = null;
        ResourceLocation chosenId = null;
        for (Map.Entry<ResourceLocation, JsonElement> entry : new TreeMap<>(files).entrySet()) {
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
