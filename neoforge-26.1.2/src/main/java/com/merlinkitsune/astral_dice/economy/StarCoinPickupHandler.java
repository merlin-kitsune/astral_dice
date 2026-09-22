package com.merlinkitsune.astral_dice.economy;

import com.merlinkitsune.astral_dice.AstralDiceMod;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.util.TriState;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;

/**
 * 拾取钩子（NeoForge）：世界里被玩家拾取的**星币与星币袋**默认直接折算入钱包。
 *
 * <p>触发条件：钱包启用 **且** 「获得星币时直接入钱包」打开
 * （{@link StarCoinCurrency#isAutoDepositEnabled()}）；否则完全不干预，保持原版拾取行为。
 *
 * <p>顺序上先尝试入账、**成功才**阻止原版拾取并回收物品实体 —— 若账本不可用（吸收失败），
 * 物品照常进物品栏，绝不出现「实体被删但钱没到账」的丢失窗口。
 *
 * <p>该事件只在逻辑服务端触发（见 NeoForge 事件文档），因此不需要额外的分侧守卫。
 */
@EventBusSubscriber(modid = AstralDiceMod.MODID)
public final class StarCoinPickupHandler {

    private StarCoinPickupHandler() {
    }

    @SubscribeEvent
    public static void onItemPickup(ItemEntityPickupEvent.Pre event) {
        if (!StarCoinCurrency.isAutoDepositEnabled()) return;
        Player player = event.getPlayer();
        ItemEntity itemEntity = event.getItemEntity();
        if (player == null || itemEntity == null) return;
        ItemStack stack = itemEntity.getItem();
        if (!StarCoinCurrency.isCurrency(stack)) return;
        if (!StarCoinCurrency.tryAbsorbIntoWallet(player, stack)) return;
        event.setCanPickup(TriState.FALSE);
        itemEntity.discard();
    }
}
