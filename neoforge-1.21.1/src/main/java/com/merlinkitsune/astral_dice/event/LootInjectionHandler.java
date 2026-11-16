package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.data.LootInjectionData;
import com.merlinkitsune.astral_dice.data.LootRule;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.NestedLootTable;
import net.minecraft.world.level.storage.loot.predicates.LootItemKilledByPlayerCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 战利品注入的**唯一通道**（2026-11-15 收敛）：数据包规则 + 原版 {@link LootTable}。
 *
 * <p>旧的三条通道（{@code onLivingDrops} 的星盘/星币代码掉落、{@code onLootTableLoad} 的四池硬编码、
 * {@code neoforge:add_table} 的 GLM 清单）全部下线，改由
 * {@code data/<namespace>/astral_dice/loot/*.json} 的规则驱动。
 *
 * <p>⚠️ **时序是这里最容易错的地方**：NeoForge 在
 * {@code ReloadableServerRegistries.reload} 的反序列化过程中派发 {@link LootTableLoadEvent}，
 * 而 {@code AddReloadListenerEvent} 要等这次战利品注册表重载完成之后才派发。
 * 因此这里**不能**在 {@link LootTableLoadEvent} 里读数据包（读到的是上一代，首次启动还是空快照），
 * 只能把表对象排进待处理队列；由 {@link #drainPending()} 在 {@code AddReloadListenerEvent} 时交给
 * {@code data/LootInjectionReloadListener}，等它的 {@code apply} 解析完 JSON 后再回填 pool。
 * 表对象在反序列化后仍可变，补 pool 不影响已经完成的校验。
 *
 * <p>匹配口径：
 * <ol>
 *   <li>只处理 {@code minecraft} 命名空间的原版表；{@code astral_dice} 自己的表**永不注入**（防递归）；</li>
 *   <li>按表的路径分通道：{@code chests/*} 只吃 {@code target_kind=chest}，
 *       {@code entities/*} 吃 {@code entity} 与 {@code monster}，其它路径不注入；</li>
 *   <li>**每个通道**取覆盖当前表、priority 最高的那条规则（星盘 100 与星币 100 因此可以同时落到同一只怪身上）；</li>
 *   <li>{@code monster_only=true} 的宽泛规则只在实体表对应 {@link MobCategory#MONSTER} 时命中；</li>
 *   <li>注入形态 = 一个池，池里一个 {@link NestedLootTable#lootTableReference} 指向规则的目标表，
 *       池名 {@code astral_dice:<rule.id>}，已存在即跳过（同一张表被重复处理时幂等）。</li>
 * </ol>
 *
 * <p>⚠️ 不在装载期校验「目标表是否存在」：{@link LootTableLoadEvent} 发生在 LOOT_TABLE 注册表**反序列化过程中**，
 * 此时注册表内容可能是上一代（{@code /reload} 新增的表会被误判为缺失）。缺失表在滚动时由原版解析为
 * {@code LootTable.EMPTY}（等价于不掉落），不会崩。这里只做**结构性**校验（命名空间 + 路径前缀与 target_kind 匹配）。
 */
@EventBusSubscriber(modid = com.merlinkitsune.astral_dice.AstralDiceMod.MODID)
public final class LootInjectionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(LootInjectionHandler.class);

    /** 只处理原版表；自有命名空间（{@code astral_dice}）的表一律跳过，防递归注入。 */
    private static final String VANILLA_NAMESPACE = "minecraft";

    private static final List<String> CHEST_KINDS = List.of(LootRule.KIND_CHEST);
    private static final List<String> ENTITY_KINDS = List.of(LootRule.KIND_ENTITY, LootRule.KIND_MONSTER);

    /** 本次重载中已经反序列化、等待数据包 apply 后回填 pool 的原版表。 */
    private static final List<PendingTable> PENDING = Collections.synchronizedList(new ArrayList<>());

    /** 结构性问题只警告一次（{@code /reload} 会反复触发同一张表的装载）。 */
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

    /** 把本次重载已经反序列化的表排进队列；真正的注入在数据包 apply 之后。 */
    public static void enqueue(ResourceLocation id, LootTable table) {
        if (id == null || table == null) {
            return;
        }
        PENDING.add(new PendingTable(id, table));
    }

    /** 取走本次重载排队的所有表（调用后队列清空，避免下一次重载重复处理旧对象）。 */
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

    /**
     * 数据包 apply 完成后，对本次重载排队的表逐个注入。
     *
     * <p>由 {@code data/LootInjectionReloadListener#apply} 在
     * {@code AstralDiceDataStore.replaceLoot(...)} 之后调用；不在这里读数据包。
     */
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

    /** 表的路径 ⇒ 可用的规则通道；非箱子/实体表返回空（不注入）。 */
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

    /** 实体表 id（{@code minecraft:entities/zombie}）对应实体是否为 Monster。 */
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
            warnOnce(rule.id(),
                    "战利品规则 {} 的目标表 {} 与 target_kind={} 不匹配（路径应以 {} 开头），已跳过",
                    rule.id(), target, rule.targetKind(), expectedPrefix);
            return;
        }
        LootPool.Builder pool = LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1))
                .add(NestedLootTable.lootTableReference(ResourceKey.create(Registries.LOOT_TABLE, target)))
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

    /** 待注入的一张原版表；跨 reload listener 传递，不能再从注册表反查。 */
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
