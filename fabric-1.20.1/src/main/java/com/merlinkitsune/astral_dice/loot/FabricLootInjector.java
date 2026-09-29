package com.merlinkitsune.astral_dice.loot;

import java.util.Map;
import java.util.Set;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.item.ModItems;

import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.LootTableReference;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 战利品注入的 **Fabric 1.20.1** 实现。
 *
 * <h2>它替代了什么(Forge 侧的两条通道)</h2>
 * <ol>
 *   <li><b>Global Loot Modifier</b>({@code astral_dice:add_table} 序列化器 +
 *       {@code data/astral_dice/loot_modifiers/*.json} 13 份 +
 *       {@code data/forge/loot_modifiers/global_loot_modifiers.json}) —— Fabric 的 FML 没有
 *       GLM 概念,那套注册表({@code forge:global_loot_modifier_serializers})根本不存在。</li>
 *   <li><b>{@code LootTableLoadEvent}</b>({@code event/LootInjectionHandler} 的箱表四个池)
 *       —— Fabric 没有该事件。</li>
 * </ol>
 * 两者在 Fabric 侧统一由 {@code LootTableEvents.MODIFY} 承担。⚠️ 该回调的签名是
 * <b>5 个参数</b>({@code (ResourceManager, LootManager, Identifier, LootTable.Builder, LootTableSource)}),
 * 不是 3 个 —— 本仓 {@code porting/} 里的旧速查表写错过,已订正。
 *
 * <h2>为什么不需要「幂等去重」</h2>
 * Forge 侧改的是**已加载的 LootTable 实例**,重载会再改一次 ⇒ 必须靠
 * {@code table.getPool("astral_dice:xxx") != null} 早退。Fabric 的 MODIFY 每次重载都作用在
 * **新建的 {@code LootTable.Builder}** 上 ⇒ 天然幂等,不需要(也**没有**)这个查询能力。
 *
 * <h2>自有命名空间早退(项目红灯条款)</h2>
 * 自有表 {@code astral_dice:chests/star_plate} 的 path 同样是 {@code chests/star_plate},
 * 会被前缀判据命中。Forge 侧因此出现过「GLM 100% 挂表 + 本 handler 再注入」的双通道重复
 * (概率 1−(1−p)² 放大)。Fabric 侧虽然不再有 GLM,但**判据必须继续保留**:本类对
 * {@code astral_dice} 命名空间一律早退。
 */
public final class FabricLootInjector {

    private static final ResourceLocation CHEST_STAR_PLATE = tableId("chests/star_plate");
    /**
     * 原版箱表里「挂 {@code astral_dice:chests/star_plate} 子表(5% 星盘)」的那批表。
     * 逐字取自 Forge 侧 GLM {@code star_plate_all_chests.json} 的 43 条
     * {@code forge:loot_table_id} 条件 —— **不做前缀近似**,以保证与 Forge 线的概率口径逐表一致。
     */
    private static final Set<String> STAR_PLATE_CHEST_TABLES = Set.of(
            "chests/abandoned_mineshaft",
            "chests/ancient_city",
            "chests/ancient_city_ice_box",
            "chests/bastion_bridge",
            "chests/bastion_hoglin_stable",
            "chests/bastion_other",
            "chests/bastion_treasure",
            "chests/buried_treasure",
            "chests/desert_pyramid",
            "chests/end_city_treasure",
            "chests/igloo_chest",
            "chests/jungle_temple",
            "chests/jungle_temple_dispenser",
            "chests/nether_bridge",
            "chests/pillager_outpost",
            "chests/ruined_portal",
            "chests/shipwreck_map",
            "chests/shipwreck_supply",
            "chests/shipwreck_treasure",
            "chests/simple_dungeon",
            "chests/spawn_bonus_chest",
            "chests/stronghold_corridor",
            "chests/stronghold_crossing",
            "chests/stronghold_library",
            "chests/underwater_ruin_big",
            "chests/underwater_ruin_small",
            "chests/village/village_armorer",
            "chests/village/village_butcher",
            "chests/village/village_cartographer",
            "chests/village/village_desert_house",
            "chests/village/village_fisher",
            "chests/village/village_fletcher",
            "chests/village/village_mason",
            "chests/village/village_plains_house",
            "chests/village/village_savanna_house",
            "chests/village/village_shepherd",
            "chests/village/village_snowy_house",
            "chests/village/village_taiga_house",
            "chests/village/village_tannery",
            "chests/village/village_temple",
            "chests/village/village_toolsmith",
            "chests/village/village_weaponsmith",
            "chests/woodland_mansion"
    );

    /**
     * 原版实体表 ⇒ 挂哪张星盘子表(键 = {@code minecraft:entities/<name>} 的 name)。
     * 逐字取自 Forge 侧 12 份实体 GLM JSON:10 个普通怪物 → {@code entities/star_plate}(1%),
     * 末影龙/凋灵 → {@code entities/star_plate_boss}(100%)。
     */
    private static final Map<String, String> STAR_PLATE_ENTITY_TABLES = Map.ofEntries(
            Map.entry("creeper", "entities/star_plate"),
            Map.entry("drowned", "entities/star_plate"),
            Map.entry("ender_dragon", "entities/star_plate_boss"),
            Map.entry("enderman", "entities/star_plate"),
            Map.entry("evoker", "entities/star_plate"),
            Map.entry("ravager", "entities/star_plate"),
            Map.entry("skeleton", "entities/star_plate"),
            Map.entry("spider", "entities/star_plate"),
            Map.entry("vindicator", "entities/star_plate"),
            Map.entry("witch", "entities/star_plate"),
            Map.entry("wither", "entities/star_plate_boss"),
            Map.entry("zombie", "entities/star_plate")
    );

    /**
     * 本模组自有战利品表的 id。
     *
     * <p>⚠️ **1.20.1 没有 {@code Registries.LOOT_TABLE}** —— 战利品表不是注册表条目,
     * 而是数据包加载的数据文件,引用一律走 {@link ResourceLocation}
     * (这与 1.21 的 {@code ResourceKey<LootTable>} 写法不同,是本线最容易误写的一处)。
     */
    private static ResourceLocation tableId(String path) {
        return new ResourceLocation(AstralDiceMod.MODID, path);
    }

    /** 在 mod 初始化阶段注册(幂等:MODIFY 只 register 一次)。 */
    public static void register() {
        LootTableEvents.MODIFY.register((resourceManager, lootManager, id, tableBuilder, source) -> {
            if (!"minecraft".equals(id.getNamespace())) {
                // 自有命名空间(以及任何第三方)一律不注入 —— 见类注释的「自有命名空间早退」
                return;
            }
            String path = id.getPath();

            // ── ① GLM star_plate_all_chests 的等价物:把星盘子表挂到指定箱表 ──
            if (STAR_PLATE_CHEST_TABLES.contains(path)) {
                tableBuilder.pool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1))
                        .add(LootTableReference.lootTableReference(CHEST_STAR_PLATE))
                        .build());
            }

            // ── ② GLM star_plate_<mob> 的等价物:把星盘子表挂到指定实体表 ──
            if (path.startsWith("entities/")) {
                String entity = path.substring("entities/".length());
                String sub = STAR_PLATE_ENTITY_TABLES.get(entity);
                if (sub != null) {
                    tableBuilder.pool(LootPool.lootPool()
                            .setRolls(ConstantValue.exactly(1))
                            .add(LootTableReference.lootTableReference(tableId(sub)))
                            .build());
                }
            }

            // ── ③ LootTableLoadEvent 的等价物:箱表四个池 ──
            if (!path.startsWith("chests/")) {
                return;
            }
            boolean isBuriedTreasure = path.equals("chests/buried_treasure");
            boolean isEndCity = path.equals("chests/end_city_treasure");

            // 星币:所有箱 5%(1-2 枚),末地城 9%
            tableBuilder.pool(LootPool.lootPool()
                    .setRolls(ConstantValue.exactly(1))
                    .when(LootItemRandomChanceCondition.randomChance(isEndCity ? 0.09F : 0.05F))
                    .add(LootItem.lootTableItem(ModItems.STAR_COIN.get())
                            .apply(SetItemCountFunction.setCount(UniformGenerator.between(1, 2))))
                    .build());

            // 空白筹码:埋藏的宝藏 100% 必出,其余箱 3%
            if (isBuriedTreasure) {
                tableBuilder.pool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1))
                        .add(LootItem.lootTableItem(ModItems.BLANK_CHIP.get()))
                        .build());
            } else {
                tableBuilder.pool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1))
                        .when(LootItemRandomChanceCondition.randomChance(0.03F))
                        .add(LootItem.lootTableItem(ModItems.BLANK_CHIP.get()))
                        .build());
            }

            // 星盘:所有箱 1%,末地城 5%
            tableBuilder.pool(LootPool.lootPool()
                    .setRolls(ConstantValue.exactly(1))
                    .when(LootItemRandomChanceCondition.randomChance(isEndCity ? 0.05F : 0.01F))
                    .add(LootItem.lootTableItem(ModItems.STAR_PLATE.get()))
                    .build());

            // 玻璃骰子:末地城 5%,其余箱 2%
            tableBuilder.pool(LootPool.lootPool()
                    .setRolls(ConstantValue.exactly(1))
                    .when(LootItemRandomChanceCondition.randomChance(isEndCity ? 0.05F : 0.02F))
                    .add(LootItem.lootTableItem(ModItems.GLASS_DICE.get()))
                    .build());
        });
    }

    /** 供跨线对照用:本模组声明的「必掉星盘」实体表条目数(测试台可断言)。 */
    public static int entityTableCount() {
        return STAR_PLATE_ENTITY_TABLES.size();
    }

    /** 供跨线对照用:挂 5% 星盘子表的箱表条目数(测试台可断言 = 43)。 */
    public static int chestTableCount() {
        return STAR_PLATE_CHEST_TABLES.size();
    }

    private FabricLootInjector() {
    }
}
