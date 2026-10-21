package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.item.CurioSlotUtil;
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

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * 本模组饰品（骰子 / 筹码 / 立牌）**死亡不掉落**（2026-10-21 用户裁决）。
 *
 * <p>做法：死亡掉落事件里把「玩家当前装备在饰品槽里的物品」按数量从掉落物中摘出来暂存，
 * 重生克隆时放回新玩家物品栏。比全局 keepInventory 更窄：只保本模组饰品，不影响背包其它物品；
 * 也不依赖 Curios 自身的 keepCurios 配置。
 *
 * <p>边界：死亡到重生之间玩家退出服务器（极端情况）会丢暂存 —— 暂存表是进程内内存。
 */
@EventBusSubscriber(modid = AstralDiceMod.MODID)
public final class CurioDeathKeepHandler {

    private CurioDeathKeepHandler() {
    }

    private static final Map<UUID, List<ItemStack>> STASH = new ConcurrentHashMap<>();

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        List<ItemStack> equipped = CurioSlotUtil.allEquipped(player);
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
        if (!(event.getEntity() instanceof Player player)) return;
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
