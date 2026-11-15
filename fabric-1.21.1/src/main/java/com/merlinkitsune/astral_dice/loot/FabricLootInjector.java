package com.merlinkitsune.astral_dice.loot;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.merlinkitsune.astral_dice.data.LootInjectionData;
import com.merlinkitsune.astral_dice.data.LootRule;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.NestedLootTable;
import net.minecraft.world.level.storage.loot.predicates.LootItemKilledByPlayerCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Fabric 1.21.1 战利品注入：数据包规则 + {@link LootTableEvents#MODIFY}。 */
public final class FabricLootInjector {

    private static final Logger LOGGER = LoggerFactory.getLogger(FabricLootInjector.class);
    private static final String VANILLA_NAMESPACE = "minecraft";
    private static final List<String> CHEST_KINDS = List.of(LootRule.KIND_CHEST);
    private static final List<String> ENTITY_KINDS = List.of(LootRule.KIND_ENTITY, LootRule.KIND_MONSTER);
    private static final String DEFAULT_RULES = "/data/astral_dice/astral_dice/loot/injection.json";

    private static MinecraftServer server;
    private static ResourceManager cachedManager;
    private static LootInjectionData cachedRules = LootInjectionData.empty();
    private static boolean classpathLoaded;

    private FabricLootInjector() {
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(s -> server = s);
        LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
            ResourceLocation id = key.location();
            if (!VANILLA_NAMESPACE.equals(id.getNamespace())) {
                return;
            }
            List<String> kinds = kindsFor(id);
            if (kinds.isEmpty()) {
                return;
            }
            LootInjectionData data = rules();
            if (data.isEmpty()) {
                return;
            }
            boolean monster = isMonsterTable(id);
            for (String kind : kinds) {
                LootRule rule = data.bestRule(kind, id.toString(), monster);
                if (rule != null) {
                    inject(tableBuilder, rule);
                }
            }
        });
    }

    private static synchronized LootInjectionData rules() {
        if (server != null) {
            ResourceManager manager = server.getResourceManager();
            if (manager == cachedManager) {
                return cachedRules;
            }
            cachedManager = manager;
            cachedRules = parseManager(manager);
            return cachedRules;
        }
        if (!classpathLoaded) {
            cachedRules = parseClasspath();
            classpathLoaded = true;
        }
        return cachedRules;
    }

    private static LootInjectionData parseManager(ResourceManager resourceManager) {
        Map<String, LootRule> merged = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, Resource> entry : new TreeMap<>(resourceManager
                .listResources("astral_dice/loot", path -> path.getPath().endsWith(".json"))).entrySet()) {
            final ResourceLocation fileId = entry.getKey();
            try (Reader reader = entry.getValue().openAsReader()) {
                merge(merged, JsonParser.parseReader(reader), fileId.toString());
            } catch (RuntimeException | java.io.IOException ex) {
                LOGGER.warn("[astral_dice] 跳过非法战利品数据 {}: {}", fileId, ex.getMessage());
            }
        }
        LOGGER.info("[astral_dice] 战利品注入规则已从数据包加载 {} 条", merged.size());
        return LootInjectionData.of(new ArrayList<>(merged.values()));
    }

    private static LootInjectionData parseClasspath() {
        Map<String, LootRule> merged = new LinkedHashMap<>();
        try (InputStream in = FabricLootInjector.class.getResourceAsStream(DEFAULT_RULES)) {
            if (in != null) {
                merge(merged, JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)), "builtin");
            }
        } catch (RuntimeException | java.io.IOException ex) {
            LOGGER.warn("[astral_dice] 读取内置战利品规则失败: {}", ex.getMessage());
        }
        LOGGER.info("[astral_dice] 战利品注入规则已从内置数据加载 {} 条", merged.size());
        return LootInjectionData.of(new ArrayList<>(merged.values()));
    }

    private static void merge(Map<String, LootRule> merged, JsonElement element, String source) {
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException(source + " 不是 JSON 对象");
        }
        LootInjectionData parsed = LootInjectionData.parse(element.getAsJsonObject(),
                message -> LOGGER.warn("[astral_dice] 战利品数据 {} 的 {}", source, message));
        for (LootRule rule : parsed.rules()) {
            merged.putIfAbsent(rule.id(), rule);
        }
    }

    private static List<String> kindsFor(ResourceLocation tableId) {
        String path = tableId.getPath();
        if (path.startsWith("chests/")) {
            return CHEST_KINDS;
        }
        if (path.startsWith("entities/")) {
            return ENTITY_KINDS;
        }
        return List.of();
    }

    private static boolean isMonsterTable(ResourceLocation tableId) {
        String path = tableId.getPath();
        if (!path.startsWith("entities/")) {
            return false;
        }
        ResourceLocation entityId = ResourceLocation.fromNamespaceAndPath(
                tableId.getNamespace(), path.substring("entities/".length()));
        return BuiltInRegistries.ENTITY_TYPE.getOptional(entityId)
                .map(type -> type.getCategory() == MobCategory.MONSTER)
                .orElse(false);
    }

    private static void inject(LootTable.Builder tableBuilder, LootRule rule) {
        ResourceLocation target = ResourceLocation.tryParse(rule.table());
        if (target == null) {
            return;
        }
        String expectedPrefix = LootRule.KIND_CHEST.equals(rule.targetKind()) ? "chests/" : "entities/";
        if (!target.getPath().startsWith(expectedPrefix)) {
            return;
        }
        LootPool.Builder pool = LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1))
                .add(NestedLootTable.lootTableReference(ResourceKey.create(Registries.LOOT_TABLE, target)));
        if (rule.requirePlayerKill()) {
            pool.when(LootItemKilledByPlayerCondition.killedByPlayer());
        }
        tableBuilder.withPool(pool);
    }
}
