package com.merlinkitsune.astral_dice.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.merlinkitsune.astral_dice.shop.CardShopCatalog;
import com.merlinkitsune.astral_dice.shop.ChipShopCatalog;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/** 扫描 data/<namespace>/astral_dice/shop/*.json 并应用到商店池。 */
public final class ShopDataReloadListener extends SimpleJsonResourceReloadListener {

    public static final String DIRECTORY = "astral_dice/shop";
    private static final Logger LOGGER = LoggerFactory.getLogger(ShopDataReloadListener.class);

    public ShopDataReloadListener() {
        super(new Gson(), DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<String, ShopData.Chip> chips = new LinkedHashMap<>();
        Map<String, ShopData.Card> cards = new LinkedHashMap<>();
        int validFiles = 0;
        for (Map.Entry<ResourceLocation, JsonElement> entry : new TreeMap<>(files).entrySet()) {
            final ResourceLocation fileId = entry.getKey();
            try {
                JsonElement element = entry.getValue();
                if (element == null || !element.isJsonObject()) {
                    throw new IllegalArgumentException("文件内容不是 JSON 对象");
                }
                ShopData parsed = ShopData.parse(element.getAsJsonObject());
                validFiles++;
                for (ShopData.Chip chip : parsed.chips()) {
                    chips.putIfAbsent(chip.id(), chip);
                }
                for (ShopData.Card card : parsed.cards()) {
                    cards.putIfAbsent(card.item(), card);
                }
            } catch (RuntimeException ex) {
                LOGGER.warn("[astral_dice] 跳过非法商店数据 {}: {}", fileId, ex.getMessage());
            }
        }
        if (validFiles == 0) {
            AstralDiceDataStore.clearShop();
            ChipShopCatalog.applyData(ShopData.empty());
            CardShopCatalog.applyData(ShopData.empty());
            LOGGER.info("[astral_dice] 未找到有效的 data/*/astral_dice/shop/*.json，商店使用内置池");
            return;
        }
        ShopData data = ShopData.of(new ArrayList<>(chips.values()), new ArrayList<>(cards.values()));
        AstralDiceDataStore.replaceShop(data);
        ChipShopCatalog.applyData(data);
        CardShopCatalog.applyData(data);
        LOGGER.info("[astral_dice] 商店数据已加载 {}（来自 {} 个文件）", data, validFiles);
    }
}
