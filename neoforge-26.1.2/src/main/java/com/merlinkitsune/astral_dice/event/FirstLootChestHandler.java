package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.item.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.storage.loot.LootTable;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 「进入一个新世界后，玩家开的第一个战利品箱子必定包含 1 个基础骰子」（2026-09-27 用户需求）。
 *
 * <p>本类与 {@code neoforge-1.21.1} 的同名类**功能逐字等价**，只差 26.1.2 的平台 API。完整设计理由
 * （为什么不做成战利品池、为什么必须显式补货、触发面与已知边界）写在 1.21.1 侧同名类的类注释里。
 *
 * <p><b>本线（NeoForge 26.1.2）与 1.21.1 的 API 差异（已逐条核 {@code minecraft-patched-26.1.2.109-sources.jar}）</b>：
 * <ol>
 *   <li>{@code ResourceKey} 的取值方法由 {@code location()} 改名为 {@code identifier()}，
 *       且该类型已由 {@code ResourceLocation} 改名为 {@code Identifier}（本类只取 {@code getPath()}，
 *       故无 import 变化）；</li>
 *   <li><b>{@link ContainerEntity} 的战利品访问器改名</b>：{@code getLootTable()} → {@code getContainerLootTable()}
 *       （{@code setLootTable} → {@code setContainerLootTable}）；{@code unpackChestVehicleLootTable(Player)} 不变；</li>
 *   <li>{@link CompoundTag} 的取值改为 {@code *Or} 系列：{@code getCompound(String)} → {@code getCompoundOrEmpty(String)}、
 *       {@code getBoolean(String)} → {@code getBooleanOr(String, boolean)}。</li>
 * </ol>
 */
@EventBusSubscriber(modid = AstralDiceMod.MODID)
public final class FirstLootChestHandler {

    /** 玩家持久化数据里的根键（= 模组 id，避免与其它模组撞键）。 */
    private static final String ROOT_KEY = AstralDiceMod.MODID;
    /** 「已发过骰子」标记。 */
    private static final String FLAG_KEY = "first_loot_chest_done";
    /** 只认「战利品箱子」类战利品表的 id 前缀。 */
    private static final String CHEST_TABLE_PREFIX = "chests/";
    /** 排除项：丛林神庙发射器不是箱子（且发射器里的东西会被射出）。 */
    private static final Set<String> EXCLUDED_TABLES = Set.of("chests/jungle_temple_dispenser");

    private FirstLootChestHandler() {
    }

    // ═══════════════════════════ 触发面 ═══════════════════════════

    /** 方块容器（箱子 / 木桶 / 潜影盒 / 漏斗…）：玩家右键的**那一瞬间**（早于原版 createMenu 补货）。 */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.isSpectator()) return;
        if (isDone(player)) return;

        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof RandomizableContainer self)) return;
        // 箱子被方块压住 / 猫坐在箱子上 ⇒ 原版不会打开（ChestBlock#isChestBlockedAt），别抢先消费标记
        if (be instanceof ChestBlockEntity && ChestBlock.isChestBlockedAt(level, pos)) return;

        RandomizableContainer target = isChestLootTable(self.getLootTable())
                ? self
                : partnerChestWithLoot(level, pos);
        if (target == null) return;

        target.unpackLootTable(player);
        if (placeDice(target, level)) markDone(player);
    }

    /** 实体容器（运输矿车）：原版同样在 createMenu 里补货，这里提前到右键时刻。 */
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.isSpectator()) return;
        if (isDone(player)) return;
        if (!(event.getTarget() instanceof ContainerEntity vehicle)) return;
        if (!isChestLootTable(vehicle.getContainerLootTable())) return;

        vehicle.unpackChestVehicleLootTable(player);
        if (placeDice(vehicle, event.getLevel())) markDone(player);
    }

    /**
     * 死亡重生 / 跨维度克隆：玩家持久化数据**不随实体克隆复制**（NeoForge 的 {@code NeoForgeData} 段同理）
     * ⇒ 不搬过去就等于「死一次 → 再开箱」能刷第二次。
     */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (isDone(event.getOriginal())) markDone(event.getEntity());
    }

    // ═══════════════════════════ 实现 ═══════════════════════════

    /**
     * 大箱子的战利品表可能挂在**另一半**上（原版 {@code MENU_PROVIDER_COMBINER} 会把两半都补货）
     * ⇒ 需要时返回带表的那一半。判据用 {@code ChestBlock.TYPE != SINGLE}：相邻的独立箱子必为
     * {@code SINGLE}，故不会误判成一对。
     */
    private static RandomizableContainer partnerChestWithLoot(Level level, BlockPos pos) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos neighbour = pos.relative(dir);
            BlockState state = level.getBlockState(neighbour);
            if (!(state.getBlock() instanceof ChestBlock)) continue;
            if (state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) continue;
            if (level.getBlockEntity(neighbour) instanceof RandomizableContainer partner
                    && isChestLootTable(partner.getLootTable())) {
                return partner;
            }
        }
        return null;
    }

    /**
     * 写入 1 个骰子：优先**随机空槽**（避免永远落在 0 号槽）；整箱已装满时按用户要求
     * 「强行顶掉一个随机物品」—— {@code Container#setItem} 直接覆盖，被覆盖的那一份即被丢弃。
     *
     * @return 是否已成功写入
     */
    private static boolean placeDice(Container container, Level level) {
        int size = container.getContainerSize();
        if (size <= 0) return false;
        List<Integer> empty = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            if (container.getItem(i).isEmpty()) empty.add(i);
        }
        int slot = empty.isEmpty()
                ? level.getRandom().nextInt(size)
                : empty.get(level.getRandom().nextInt(empty.size()));
        container.setItem(slot, new ItemStack(ModItems.DICE.get()));
        container.setChanged();
        return true;
    }

    /** 判据：战利品表 id 在 {@code chests/} 下，且不是被排除的发射器表。 */
    private static boolean isChestLootTable(ResourceKey<LootTable> table) {
        if (table == null) return false;
        String path = table.identifier().getPath();
        return path.startsWith(CHEST_TABLE_PREFIX) && !EXCLUDED_TABLES.contains(path);
    }

    private static boolean isDone(Player player) {
        return player.getPersistentData().getCompoundOrEmpty(ROOT_KEY).getBooleanOr(FLAG_KEY, false);
    }

    private static void markDone(Player player) {
        CompoundTag persistent = player.getPersistentData();
        // ⚠️ getCompoundOrEmpty 在缺键时返回的是**新实例**（不会挂回父标签）⇒ 改完必须 put 回去
        CompoundTag root = persistent.getCompoundOrEmpty(ROOT_KEY);
        root.putBoolean(FLAG_KEY, true);
        persistent.put(ROOT_KEY, root);
    }
}
