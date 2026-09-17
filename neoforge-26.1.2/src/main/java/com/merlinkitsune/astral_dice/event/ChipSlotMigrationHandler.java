package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.item.dice.DiceCurioItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

/**
 * 筹码栏「自管迁移」：绕开 Curios 15 登录迁移的尺寸缺陷，保证重登后筹码仍在栏内。
 *
 * <h2>上游缺陷（Curios 26.1.2 分支 = 15.0.0，`CurioInventory#loadInventoryConfiguration`）</h2>
 * 该方法先用数据包重建默认栏位，再逐槽搬移旧内容；**搬移循环的上界**取
 * {@code curioStacksHandler.getSlots()}，而 {@code CurioStacksHandler#getSlots()} → {@code update()}
 * 的首行是 {@code if (this.dataLoaded)}，{@code setDataLoaded()} 又只在**整个方法末尾**才调用
 * ⇒ 循环期间读到的是**构造函数里的原始 `stackHandler` 尺寸**（= 数据包 base size），
 * 与刚 {@code copyModifiers()} 复制过来的修饰符**无关**。本模组筹码栏的数据包尺寸是
 * {@code 0}（尺寸完全由骰子的槽位修饰符给出）⇒ 搬移上界为 0 ⇒ **一次都不搬**，
 * 旧内容全部进 {@code invalidStacks}，随后被 {@code handleInvalidStacks()} 交还玩家背包。
 * 骰子/立牌栏 base ≥ 1，故只有筹码栏中招。
 *
 * <h2>本模组的应对（不依赖上游修复）</h2>
 * <ul>
 *   <li>{@link EventPriority#HIGHEST}（**先于** Curios 的同事件处理器）：把筹码栏内容
 *       **快照并清空** ⇒ Curios 的搬移看不到任何待搬出的物品（既不搬、也不进
 *       {@code invalidStacks}，因此不会「掉出」也不会重复发放）；</li>
 *   <li>{@link EventPriority#LOWEST}（**后于** Curios）：按当前佩戴的骰子重算筹码栏尺寸，
 *       再把快照按槽位放回；放不下的（尺寸变小、或物品已不再通过校验）交还玩家背包 ——
 *       与 Curios 自身的 {@code invalidStacks} 语义一致，**绝不静默丢弃**。</li>
 * </ul>
 *
 * <h2>安全性</h2>
 * <ul>
 *   <li><b>不会复制物品</b>：快照在清空**之前**取（同一同步调用内），恢复时按槽位**覆盖写**；
 *       若两个处理器之间发生异常／进程被杀（窗口是同一个方法调用内的微秒级），下一次登录的
 *       {@code HIGHEST} 会先把**残留快照**交还玩家背包再重新快照槽内容 ⇒ 既不丢也不复制。</li>
 *   <li><b>只碰筹码栏</b>：其它栏位（骰子/立牌）完全交给 Curios，不介入。</li>
 *   <li><b>异常隔离</b>：整段 try/catch，出问题只记日志并对残留做交还，绝不让登录失败。</li>
 * </ul>
 *
 * <p>上游修好后（搬移上界改为「复制修饰符后的尺寸」）本类仍然正确：快照/恢复对空槽是幂等的。
 */
@EventBusSubscriber(modid = AstralDiceMod.MODID)
public final class ChipSlotMigrationHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChipSlotMigrationHandler.class);
    /** 筹码栏槽位 id（与 data/astral_dice/curios/slots/chip.json 同名）。 */
    private static final String CHIP = "chip";

    /** player -> 迁移期间被取出的筹码栏内容（功能槽 + 装饰槽）。 */
    private static final Map<UUID, Snapshot> PENDING = new ConcurrentHashMap<>();

    private ChipSlotMigrationHandler() {
    }

    /** 迁移前：快照 + 清空（Curios 因此看不到任何需要搬出的物品）。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDatapackSyncBefore(OnDatapackSyncEvent event) {
        forEachSyncedPlayer(event, ChipSlotMigrationHandler::stashAndClear);
    }

    /** 迁移后：按骰子重算尺寸 + 放回快照。 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDatapackSyncAfter(OnDatapackSyncEvent event) {
        forEachSyncedPlayer(event, ChipSlotMigrationHandler::restore);
    }

    // ══ 迁移前 ══════════════════════════════════════════════════════════════

    private static void stashAndClear(ServerPlayer player) {
        try {
            // 上一次迁移若在半途被打断，残留快照必须先交还玩家，绝不静默丢弃。
            Snapshot leftover = PENDING.remove(player.getUUID());
            if (leftover != null && !leftover.isEmpty()) {
                LOGGER.warn("[Astral Dice] 检测到上一次筹码栏迁移的残留快照，先交还玩家：{}", leftover.describe());
                leftover.giveAll(player);
            }

            ICurioStacksHandler handler = chipHandler(player);
            if (handler == null) {
                return;
            }
            IDynamicStackHandler stacks = handler.getStacks();
            IDynamicStackHandler cosmetics = handler.getCosmeticStacks();

            List<ItemStack> stackSnapshot = new ArrayList<>();
            List<ItemStack> cosmeticSnapshot = new ArrayList<>();
            boolean any = false;

            for (int i = 0; i < stacks.getSlots(); i++) {
                ItemStack stack = stacks.getStackInSlot(i);
                stackSnapshot.add(stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
                if (!stack.isEmpty()) {
                    any = true;
                    stacks.setStackInSlot(i, ItemStack.EMPTY);
                }
            }
            for (int i = 0; i < cosmetics.getSlots(); i++) {
                ItemStack stack = cosmetics.getStackInSlot(i);
                cosmeticSnapshot.add(stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
                if (!stack.isEmpty()) {
                    any = true;
                    cosmetics.setStackInSlot(i, ItemStack.EMPTY);
                }
            }
            if (any) {
                PENDING.put(player.getUUID(), new Snapshot(stackSnapshot, cosmeticSnapshot));
            }
        } catch (Throwable t) {
            LOGGER.error("[Astral Dice] 筹码栏迁移前快照失败（本次登录的筹码可能按 Curios 原行为被移出）", t);
        }
    }

    // ══ 迁移后 ══════════════════════════════════════════════════════════════

    private static void restore(ServerPlayer player) {
        Snapshot snapshot = PENDING.remove(player.getUUID());
        if (snapshot == null || snapshot.isEmpty()) {
            return;
        }
        try {
            // 先把尺寸调成「按当前骰子应有的值」，再放回物品：否则空栏位放不下任何东西。
            DiceCurioItem.refreshChipSlotCount(player);

            ICurioStacksHandler handler = chipHandler(player);
            if (handler == null) {
                LOGGER.warn("[Astral Dice] 迁移后找不到筹码栏，快照交还玩家：{}", snapshot.describe());
                snapshot.giveAll(player);
                return;
            }
            snapshot.restoreInto(player, handler);
            LOGGER.debug("[Astral Dice] 筹码栏迁移完成：{}", snapshot.describe());
        } catch (Throwable t) {
            LOGGER.error("[Astral Dice] 筹码栏迁移后恢复失败，快照交还玩家以免丢失：" + snapshot.describe(), t);
            try {
                snapshot.giveAll(player);
            } catch (Throwable t2) {
                LOGGER.error("[Astral Dice] 快照交还玩家同样失败（物品可能丢失）", t2);
            }
        }
    }

    // ══ 工具 ════════════════════════════════════════════════════════════════

    private static ICurioStacksHandler chipHandler(ServerPlayer player) {
        ICuriosItemHandler inventory = CuriosApi.getCuriosInventory(player).orElse(null);
        if (inventory == null) {
            return null;
        }
        return inventory.getStacksHandler(CHIP).orElse(null);
    }

    private static void forEachSyncedPlayer(OnDatapackSyncEvent event,
                                            java.util.function.Consumer<ServerPlayer> action) {
        try {
            if (event.getPlayer() != null) {
                action.accept(event.getPlayer());
                return;
            }
            if (event.getPlayerList() != null) {
                for (ServerPlayer player : event.getPlayerList().getPlayers()) {
                    action.accept(player);
                }
            }
        } catch (Throwable t) {
            LOGGER.error("[Astral Dice] 遍历数据包同步玩家失败", t);
        }
    }

    /** 迁移期间被取出的筹码栏内容。 */
    private record Snapshot(List<ItemStack> stacks, List<ItemStack> cosmetics) {

        boolean isEmpty() {
            return !hasAny(stacks) && !hasAny(cosmetics);
        }

        private static boolean hasAny(List<ItemStack> list) {
            for (ItemStack stack : list) {
                if (stack != null && !stack.isEmpty()) {
                    return true;
                }
            }
            return false;
        }

        /** 按槽位放回；放不下或不通过校验的交给玩家背包（绝不丢弃）。 */
        void restoreInto(ServerPlayer player, ICurioStacksHandler handler) {
            IDynamicStackHandler target = handler.getStacks();
            IDynamicStackHandler targetCosmetics = handler.getCosmeticStacks();
            List<ItemStack> leftover = new ArrayList<>();
            putBack(player, target, stacks, leftover);
            putBack(player, targetCosmetics, cosmetics, leftover);
            for (ItemStack stack : leftover) {
                give(player, stack);
            }
        }

        private static void putBack(ServerPlayer player, IDynamicStackHandler target,
                                    List<ItemStack> source, List<ItemStack> leftover) {
            for (int i = 0; i < source.size(); i++) {
                ItemStack stack = source.get(i);
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                if (i < target.getSlots() && target.getStackInSlot(i).isEmpty()
                        && target.isItemValid(i, stack)) {
                    target.setStackInSlot(i, stack);
                } else {
                    leftover.add(stack);
                }
            }
        }

        void giveAll(ServerPlayer player) {
            for (ItemStack stack : stacks) {
                give(player, stack);
            }
            for (ItemStack stack : cosmetics) {
                give(player, stack);
            }
        }

        private static void give(ServerPlayer player, ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                return;
            }
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }

        String describe() {
            StringBuilder sb = new StringBuilder("stacks=[");
            for (int i = 0; i < stacks.size(); i++) {
                ItemStack stack = stacks.get(i);
                if (stack != null && !stack.isEmpty()) {
                    sb.append(i).append(':')
                            .append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()))
                            .append('x').append(stack.getCount()).append(',');
                }
            }
            sb.append("] cosmetics=[");
            for (int i = 0; i < cosmetics.size(); i++) {
                ItemStack stack = cosmetics.get(i);
                if (stack != null && !stack.isEmpty()) {
                    sb.append(i).append(':')
                            .append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()))
                            .append('x').append(stack.getCount()).append(',');
                }
            }
            return sb.append(']').toString();
        }
    }
}
