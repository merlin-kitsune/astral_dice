package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.data.LootInjectionData;
import com.merlinkitsune.astral_dice.data.LootRule;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootTableReference;
import net.minecraft.world.level.storage.loot.predicates.LootItemKilledByPlayerCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 战利品注入的唯一通道（2026-11-15 收敛）：数据包规则 + 原版 {@link LootTable}。 */
@Mod.EventBusSubscriber(modid = com.merlinkitsune.astral_dice.AstralDiceMod.MODID)
public final class LootInjectionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(LootInjectionHandler.class);
    private static final String VANILLA_NAMESPACE = "minecraft";
    private static final List<String> CHEST_KINDS = List.of(LootRule.KIND_CHEST);
    private static final List<String> ENTITY_KINDS = List.of(LootRule.KIND_ENTITY, LootRule.KIND_MONSTER);
    private static final List<PendingTable> PENDING = Collections.synchronizedList(new ArrayList<>());
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private LootInjectionHandler() {
    }

    @SubscribeEvent
    public static void onLootTableLoad(LootTableLoadEvent event) {
        ResourceLocation name = event.getName();
        if (name == null || !VANILLA_NAMESPACE.equals(name.getNamespace())) {
            return;
        }
        if (kindsFor(name).isEmpty()) {
            return;
        }
        enqueue(name, event.getTable());
    }

    public static void enqueue(ResourceLocation id, LootTable table) {
        if (id != null && table != null) {
            PENDING.add(new PendingTable(id, table));
        }
    }

    public static List<PendingTable> drainPending() {
        synchronized (PENDING) {
            if (PENDING.isEmpty()) {
                return List.of();
            }
            List<PendingTable> drained = new ArrayList<>(PENDING);
            PENDING.clear();
            return drained;
        }
    }

    public static void injectPending(List<PendingTable> pending, LootInjectionData data) {
        if (pending == null || pending.isEmpty() || data == null || data.isEmpty()) {
            return;
        }
        for (PendingTable entry : pending) {
            try {
                List<String> kinds = kindsFor(entry.id());
                if (kinds.isEmpty()) {
                    continue;
                }
                boolean monster = isMonsterTable(entry.id());
                for (String kind : kinds) {
                    LootRule rule = data.bestRule(kind, entry.id().toString(), monster);
                    if (rule != null) {
                        inject(entry.table(), rule);
                    }
                }
            } catch (RuntimeException ex) {
                LOGGER.warn("[astral_dice] 战利品注入失败 {}: {}", entry.id(), ex.getMessage());
            }
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
        ResourceLocation entityId = new ResourceLocation(tableId.getNamespace(), path.substring("entities/".length()));
        var type = BuiltInRegistries.ENTITY_TYPE.get(entityId);
        return type != null && type.getCategory() == MobCategory.MONSTER;
    }

    private static void inject(LootTable table, LootRule rule) {
        String poolName = com.merlinkitsune.astral_dice.AstralDiceMod.MODID + ":" + rule.id();
        if (table.getPool(poolName) != null) {
            return;
        }
        ResourceLocation target = ResourceLocation.tryParse(rule.table());
        if (target == null) {
            warnOnce(rule.id(), "战利品规则 {} 的目标表 id 非法: {}，已跳过", rule.id(), rule.table());
            return;
        }
        String expectedPrefix = LootRule.KIND_CHEST.equals(rule.targetKind()) ? "chests/" : "entities/";
        if (!target.getPath().startsWith(expectedPrefix)) {
            warnOnce(rule.id(), "战利品规则 {} 的目标表 {} 与 target_kind={} 不匹配，已跳过", rule.id(), target, rule.targetKind());
            return;
        }
        LootPool.Builder pool = LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1))
                .add(LootTableReference.lootTableReference(target))
                .name(poolName);
        if (rule.requirePlayerKill()) {
            pool.when(LootItemKilledByPlayerCondition.killedByPlayer());
        }
        table.addPool(pool.build());
    }

    private static void warnOnce(String key, String format, Object... args) {
        if (WARNED.add(key)) {
            LOGGER.warn("[astral_dice] " + format, args);
        }
    }

    public static final class PendingTable {
        private final ResourceLocation id;
        private final LootTable table;

        private PendingTable(ResourceLocation id, LootTable table) {
            this.id = id;
            this.table = table;
        }

        public ResourceLocation id() {
            return this.id;
        }

        public LootTable table() {
            return this.table;
        }
    }
}
