package com.merlinkitsune.astral_dice.economy;

import com.merlinkitsune.astral_dice.AstralDiceMod;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 拾取钩子（Forge 1.20.1）：世界里被玩家拾取的**星币与星币袋**默认直接折算入钱包。
 *
 * <p>语义与 1.21.1 侧 {@code StarCoinPickupHandler} 逐字等价，平台差异只有事件本身：
 * 1.20.1 用 {@link EntityItemPickupEvent}（{@code @Cancelable}，取消即不拾取），
 * 1.21.1 用 {@code ItemEntityPickupEvent.Pre}（{@code setCanPickup(TriState)}）。
 *
 * <p>触发条件：钱包启用 **且** 「获得星币时直接入钱包」打开；否则完全不干预。
 * 顺序上先尝试入账、**成功才**取消原版拾取并回收物品实体 —— 若账本不可用，物品照常进物品栏，
 * 绝不出现「实体被删但钱没到账」的丢失窗口。
 *
 * <p>该事件由原版在逻辑服务端的碰撞拾取路径里触发，无需额外分侧守卫。
 */
@Mod.EventBusSubscriber(modid = AstralDiceMod.MODID)
public final class StarCoinPickupHandler {

    private StarCoinPickupHandler() {
    }

    @SubscribeEvent
    public static void onItemPickup(EntityItemPickupEvent event) {
        if (!StarCoinCurrency.isAutoDepositEnabled()) return;
        Player player = event.getEntity();
        ItemEntity itemEntity = event.getItem();
        if (player == null || itemEntity == null) return;
        ItemStack stack = itemEntity.getItem();
        if (!StarCoinCurrency.isCurrency(stack)) return;
        if (!StarCoinCurrency.tryAbsorbIntoWallet(player, stack)) return;
        event.setCanceled(true);
        itemEntity.discard();
    }
}
