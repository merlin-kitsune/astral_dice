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
 * <p><b>计数口径（用户裁决 = 按玩家）</b>：每个玩家在**每个存档**里各享受一次。标记存在玩家自带的
 * 持久化数据（{@code Entity#getPersistentData()}，落盘在玩家 {@code .dat} 的 {@code NeoForgeData} 段），
 * 不注册任何注册表条目，对存档的侵入面只有「多一个 NBT 子键」。玩家数据本身按存档存放
 * ⇒ 换存档后自然重新计数，对应「刚创建自身存档之后」。
 *
 * <p><b>为什么不做成战利品池（GlobalLootModifier）</b>：本模组已有 {@code add_table} 那套注入，
 * 但它在**战利品表生成期**往结果列表里塞物品；而原版 {@code LootTable#fill}（三线共享同一份逻辑，
 * 已逐行核对 1.20.1 / 1.21.1 / 26.1.2 的 {@code forge/neoforge-*-sources.jar}）在
 * 「结果列表长度 &gt; 容器空位数」时会把**打乱顺序后排到后面的**整叠直接丢弃：
 * <pre>
 *   this.shuffleAndSplitItems(list, emptySlots.size(), random);   // 末尾 Util.shuffle(stacks, random)
 *   for (ItemStack stack : list) {
 *       if (slots.isEmpty()) { LOGGER.warn("Tried to over-fill a container"); return; }
 *       ...
 *   }
 * </pre>
 * 顺序已被 {@code Util.shuffle} 打乱 ⇒ 装了很多模组、箱子被塞爆时，塞进去的骰子会被随机抽签抽掉，
 * 「必定出现」不成立。因此这里改为**容器侧注入**：在玩家真正打开箱子时，先按原版口径
 * {@code unpack(player)} 补齐战利品，再**直接写入**一个槽位 —— 优先随机空槽，整箱装满时按用户要求
 * 「强行顶掉一个随机物品」。写入的骰子不参与任何抽签 ⇒ 必定到手。
 *
 * <p><b>为什么必须显式 {@code unpack}</b>：{@code RandomizableContainerBlockEntity#getItem/setItem}
 * 都会先调用 {@code unpackLootTable(null)}（该类源码 1.21.1 为 L50/L59/L68/L77/L86）⇒ 若不尽早用
 * **带玩家的**口径补货，我方写入动作会触发一次 {@code player == null} 的补货（缺
 * {@code LootContextParams.THIS_ENTITY}、也不触发 {@code GENERATE_LOOT} 进度准则），与原版
 * （{@code createMenu} 里带玩家补货）行为不一致。
 *
 * <p><b>触发面</b>：方块容器（{@link RandomizableContainer}，含箱子/陷阱箱/木桶/潜影盒等）走
 * {@link PlayerInteractEvent.RightClickBlock}；实体容器（{@link ContainerEntity}，即运输矿车）走
 * {@link PlayerInteractEvent.EntityInteract}。**仅在战利品表 id 落在 {@code chests/} 前缀下时生效**
 * （结构与模组包的战利品箱口径一致；玩家自放的箱子没有战利品表 ⇒ 天然排除），并排除
 * {@code chests/jungle_temple_dispenser}（那是发射器，不是箱子）。
 *
 * <p><b>已知边界（刻意如此，勿当缺陷）</b>：
 * <ul>
 *   <li>玩家的「第一个箱子」若是**别人已经开过**的箱子（战利品表已被清空）⇒ 不计数、不发骰子，
 *       顺延到下一个「未开启」的战利品箱。共享容器 + 按玩家计数，这是唯一自洽的取法。</li>
 *   <li>箱子被方块压住 / 猫坐在箱子上时原版不会打开，这里同样跳过（不抢先消费标记）。</li>
 *   <li>自动化开启（漏斗/比较器/其它模组直接读容器）不属于「玩家开箱」，不触发。</li>
 * </ul>
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
        if (!isChestLootTable(vehicle.getLootTable())) return;

        vehicle.unpackChestVehicleLootTable(player);
        if (placeDice(vehicle, event.getLevel())) markDone(player);
    }

    /**
     * 死亡重生 / 跨维度克隆：玩家持久化数据**不随实体克隆复制**（NeoForge 的 {@code NeoForgeData} 段
     * 同理）⇒ 不搬过去就等于「死一次 → 再开箱」能刷第二次。
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
        String path = table.location().getPath();
        return path.startsWith(CHEST_TABLE_PREFIX) && !EXCLUDED_TABLES.contains(path);
    }

    private static boolean isDone(Player player) {
        return player.getPersistentData().getCompound(ROOT_KEY).getBoolean(FLAG_KEY);
    }

    private static void markDone(Player player) {
        CompoundTag persistent = player.getPersistentData();
        // ⚠️ getCompound 在缺键时返回的是**新实例**（不会挂回父标签）⇒ 改完必须 put 回去
        CompoundTag root = persistent.getCompound(ROOT_KEY);
        root.putBoolean(FLAG_KEY, true);
        persistent.put(ROOT_KEY, root);
    }
}
