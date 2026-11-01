package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.compat.curios.CuriosApi;
import com.merlinkitsune.astral_dice.platform.event.EventPriority;
import com.merlinkitsune.astral_dice.platform.event.SubscribeEvent;
import com.merlinkitsune.astral_dice.platform.event.entity.living.LivingDropsEvent;
import com.merlinkitsune.astral_dice.platform.event.entity.player.PlayerEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 本模组饰品（骰子 / 筹码 / 立牌）**死亡不掉落**（2026-10-21 用户裁决）。
 *
 * <p>做法：死亡掉落事件里把「玩家当前装备在饰品槽里的物品」按数量从掉落物中摘出来暂存，
 * 重生克隆时放回新玩家物品栏。比全局 keepInventory 更窄：只保本模组饰品，不影响背包其它物品；
 * 也不依赖 Curios 自身的 keepCurios 配置。
 *
 * <p>边界：死亡到重生之间玩家退出服务器（极端情况）会丢暂存 —— 暂存表是进程内内存。
 */
// ⚠️ fabric-1.20.1 注册：本线没有 {@code @Mod.EventBusSubscriber} 这回事 —— 注解不参与任何注册。
// 必须由主 Agent 在 AstralDiceMod.registerListeners 里显式登记：
//     LoaderBus.INSTANCE.register(CurioDeathKeepHandler.class);
// 漏这一行 ⇒ 死亡时饰品照常掉落，重生也不会放回（功能静默失效，不报错）。
public final class CurioDeathKeepHandler {

    private CurioDeathKeepHandler() {
    }

    private static final Map<UUID, List<ItemStack>> STASH = new ConcurrentHashMap<>();

    /**
     * 收集玩家当前**实际装备**的全部饰品栈（对应 forge 蓝本的 {@code CurioSlotUtil.allEquipped}）。
     *
     * <p>平台差异：forge 蓝本把该 helper 收在 {@code item/CurioSlotUtil} 上；fabric-1.20.1 线的门面是
     * {@code compat/curios/CuriosApi}（不是 {@code top.theillusivec4.curios}），本类内联一份等价实现，
     * 语义与 {@code CurioSlotUtil.hasAnyCurioMatching} 的槽位遍历完全一致（遍历 map 的所有 slotId，
     * 再遍历该 handler 的每一个槽位，只取非空栈，返回的是槽位内的实例本身而非副本）。
     * TODO-PLATFORM: 建议后续下沉为 {@code CurioSlotUtil.allEquipped(LivingEntity)} 供其它批次复用。
     */
    private static List<ItemStack> allEquipped(Player player) {
        List<ItemStack> out = new ArrayList<>();
        var curios = CuriosApi.getCuriosInventory(player);
        if (curios.isEmpty()) return out;
        var curiosMap = curios.get().getCurios();
        for (var key : curiosMap.keySet()) {
            var stacks = curiosMap.get(key).getStacks();
            for (int i = 0; i < stacks.getSlots(); i++) {
                var s = stacks.getStackInSlot(i);
                if (!s.isEmpty()) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        List<ItemStack> equipped = allEquipped(player);
        if (equipped.isEmpty()) return;

        // 按物品计数（不按实例）：只摘掉与装备数量相等的那几份，背包里的备用份照常掉落
        Map<Item, Integer> wanted = new HashMap<>();
        for (ItemStack stack : equipped) {
            wanted.merge(stack.getItem(), stack.getCount(), Integer::sum);
        }

        List<ItemStack> kept = new ArrayList<>();
        Iterator<ItemEntity> it = event.getDrops().iterator();
        while (it.hasNext()) {
            ItemStack drop = it.next().getItem();
            Integer left = wanted.get(drop.getItem());
            if (left == null || left <= 0) continue;
            int take = Math.min(left, drop.getCount());
            ItemStack copy = drop.copy();
            copy.setCount(take);
            kept.add(copy);
            wanted.put(drop.getItem(), left - take);
            if (take >= drop.getCount()) {
                it.remove();
            } else {
                drop.setCount(drop.getCount() - take);
            }
        }
        if (!kept.isEmpty()) STASH.put(player.getUUID(), kept);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) return;
        Player player = event.getEntity();   // 1.20.1 两线同形：Clone#getEntity() 已经是 Player（本线垫片 PlayerEvent:48）
        if (player.level().isClientSide()) return;
        List<ItemStack> kept = STASH.remove(player.getUUID());
        if (kept == null || kept.isEmpty()) return;
        for (ItemStack stack : kept) {
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
    }
}
