package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.item.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

import com.merlinkitsune.astral_dice.platform.event.entity.player.PlayerEvent;
import com.merlinkitsune.astral_dice.platform.event.entity.player.PlayerInteractEvent;
import com.merlinkitsune.astral_dice.platform.event.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 「进入一个新世界后，玩家开的第一个战利品箱子必定包含 1 个基础骰子」（2026-09-27 用户需求）。
 *
 * <p>本类与 {@code neoforge-1.21.1} 的同名类**功能逐字等价**，只差平台 API（逐条见下），
 * 且与 {@code neoforge-26.1.2} 同名类亦为同一语义。完整设计理由（为什么不做成战利品池、
 * 为什么必须显式补货、触发面与已知边界）写在 1.21.1 侧同名类的类注释里，此处不重复。
 *
 * <p><b>本线（Forge 1.20.1-47.4.10）与 NeoForge 线的 API 差异</b>：
 * <ol>
 *   <li>事件注解 {@code @Mod.EventBusSubscriber}（NeoForge 为 {@code @EventBusSubscriber}）；
 *       事件包名 {@code com.merlinkitsune.astral_dice.platform.event.*}（NeoForge 为 {@code net.neoforged.neoforge.event.*}）；</li>
 *   <li><b>本线没有 {@code net.minecraft.world.RandomizableContainer} 接口</b>（1.21 才引入，
 *       已核 {@code forge-1.20.1-47.4.10-sources.jar} 内无该类）⇒ 方块容器直接用具体类
 *       {@link RandomizableContainerBlockEntity}（其 {@code getLootTable()} / {@code unpackLootTable(Player)}
 *       均为 public）；</li>
 *   <li>战利品表 id 的类型是 {@link ResourceLocation}（1.21.1 为 {@code ResourceKey#location()}、
 *       26.1.2 为 {@code ResourceKey#identifier()}）；</li>
 *   <li><b>本线取「容器当前挂着的战利品表」只能走 NBT</b>：{@code RandomizableContainerBlockEntity}
 *       在 1.20.1 <b>没有任何 getter</b>（只有 {@code protected ResourceLocation lootTable} 字段，
 *       {@code getLootTable()} 是 1.21 随 {@code RandomizableContainer} 接口一起加的），故本线改用
 *       {@link #lootTableOf(BlockEntity)} 从 {@code saveWithoutMetadata()} 的 {@code "LootTable"} 键反读；
 *       NeoForge 两线直接用 {@code self.getLootTable()}。</li>
 *   <li>{@code NbtIo} 无关；本条只涉及 {@link CompoundTag} 的 {@code getBoolean} / {@code getCompound}
 *       （26.1.2 改名为 {@code getBooleanOr} / {@code getCompoundOrEmpty}）。</li>
 * </ol>
 */
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
        if (!(be instanceof RandomizableContainerBlockEntity self)) return;
        // 箱子被方块压住 / 猫坐在箱子上 ⇒ 原版不会打开（ChestBlock#isChestBlockedAt），别抢先消费标记
        if (be instanceof ChestBlockEntity && ChestBlock.isChestBlockedAt(level, pos)) return;

        RandomizableContainerBlockEntity target = isChestLootTable(lootTableOf(be))
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
        if (!isChestLootTable(vehicle.getLootTable())) return;

        vehicle.unpackChestVehicleLootTable(player);
        if (placeDice(vehicle, event.getLevel())) markDone(player);
    }

    /**
     * 死亡重生 / 跨维度克隆：玩家持久化数据**不随实体克隆复制**（Forge 的 {@code ForgeData} 段同理）
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
    private static RandomizableContainerBlockEntity partnerChestWithLoot(Level level, BlockPos pos) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos neighbour = pos.relative(dir);
            BlockState state = level.getBlockState(neighbour);
            if (!(state.getBlock() instanceof ChestBlock)) continue;
            if (state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) continue;
            if (level.getBlockEntity(neighbour) instanceof RandomizableContainerBlockEntity partner
                    && isChestLootTable(lootTableOf(partner))) {
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

    /**
     * 反读容器当前挂着的战利品表 id。**仅在 1.20.1 需要**：本线的
     * {@link RandomizableContainerBlockEntity} 没有 getter（见类注释第 2、3 条）。
     *
     * <p>原理：{@code RandomizableContainerBlockEntity#trySaveLootTable(CompoundTag)}（1.20.1 L55）会把
     * {@code lootTable} 用 {@code toString()} 原样写进 {@code "LootTable"} 键，而子类
     * {@code ChestBlockEntity#saveAdditional}（L78-84）在 {@code lootTable != null} 时**只**写这个键、
     * 不写物品列表（Barrel / ShulkerBox / Dispenser / Hopper 同构）⇒ 于是
     * 「{@code saveWithoutMetadata()} 里有非空 {@code "LootTable"} 字符串键」⇔ {@code lootTable != null}。
     *
     * <p>这条路只读字段、不碰 {@code getItem} ⇒ 不会顺带触发 {@code unpackLootTable(null)}，
     * 因此不会抢先清表（这正是本类要避免的，见类注释）。
     *
     * @return 战利品表 id；该容器没有战利品表时返回 {@code null}
     */
    private static ResourceLocation lootTableOf(BlockEntity blockEntity) {
        CompoundTag tag = blockEntity.saveWithoutMetadata();
        // 8 = NBT 字符串标签（CompoundTag.TAG_STRING）
        if (!tag.contains("LootTable", 8)) return null;
        return ResourceLocation.tryParse(tag.getString("LootTable"));
    }

    /** 判据：战利品表 id 在 {@code chests/} 下，且不是被排除的发射器表。 */
    private static boolean isChestLootTable(ResourceLocation table) {
        if (table == null) return false;
        String path = table.getPath();
        return path.startsWith(CHEST_TABLE_PREFIX) && !EXCLUDED_TABLES.contains(path);
    }

    /**
     * 是否已发放过首箱赠礼。
     *
     * <p>⚠️ **Fabric 1.20.1 平台差异**:Forge/NeoForge 侧这里读
     * {@code Player#getPersistentData()}(Forge 补丁方法);原版 1.20.1 的 {@code Entity}
     * **没有**该方法(javap 实证),故改用本模组的 FAPI 附件键
     * {@link com.merlinkitsune.astral_dice.component.ModAttachments#FIRST_LOOT_CHEST_GIVEN}
     * —— 与另 107 个键同一条持久化通道,语义(是否发放)与死亡保留口径一致。
     */
    private static boolean isDone(Player player) {
        return com.merlinkitsune.astral_dice.component.ModAttachments.FIRST_LOOT_CHEST_GIVEN.get(player);
    }

    private static void markDone(Player player) {
        com.merlinkitsune.astral_dice.component.ModAttachments.FIRST_LOOT_CHEST_GIVEN.set(player, true);
    }
}
