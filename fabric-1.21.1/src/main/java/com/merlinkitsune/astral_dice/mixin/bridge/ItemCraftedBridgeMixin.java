package com.merlinkitsune.astral_dice.mixin.bridge;

import com.merlinkitsune.astral_dice.platform.event.LoaderBus;
import com.merlinkitsune.astral_dice.platform.event.entity.player.PlayerEvent;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code PlayerEvent.ItemCraftedEvent} 桥 —— <b>Puzzles Lib 没有这一项，必须自写</b>
 * （其 {@code PlayerEvents} 只有 BREAK_SPEED / COPY / RESPAWN / START_TRACKING /
 * STOP_TRACKING / LOGGED_IN / LOGGED_OUT / AFTER_CHANGE_DIMENSION / ITEM_PICKUP，无 craft）。
 *
 * <h2>为什么注入 {@code ResultSlot#onTake}</h2>
 * <p>1.20.1 里「玩家取出合成结果」这一步就是 {@code ResultSlot#onTake(Player, ItemStack)}。
 * {@code CraftingContainer} 的两条使用路径（{@code CraftingMenu} 的工作台与
 * {@code InventoryMenu} 的 2x2 背包格）用的都是 {@link ResultSlot}，而熔炉/交易用的是
 * {@code FurnaceResultSlot} / {@code MerchantResultSlot}（直接继承 {@code Slot}，
 * **不是** {@code ResultSlot}）⇒ 不会把烧炼、交易误报成合成。
 *
 * <p>{@code craftMatrix} 用 {@code @Shadow} 取真实字段（1.20.1 为
 * {@code private final CraftingContainer craftSlots}），而不是图省事传 {@code null}
 * —— 事件契约里 {@code getInventory()} 应当可用。
 *
 * <p>⚠️ 与 Forge 一样**双端都会触发**（{@code onTake} 在客户端本地预测里同样会跑），
 * 端判定交给消费方（与 Forge 侧同一约定）。
 */
@Mixin(ResultSlot.class)
public abstract class ItemCraftedBridgeMixin {

    @Shadow
    @Final
    private CraftingContainer craftSlots;

    @Inject(method = "onTake", at = @At("HEAD"))
    private void astralDice$bridgeItemCrafted(Player player, ItemStack stack, CallbackInfo ci) {
        LoaderBus.INSTANCE.post(new PlayerEvent.ItemCraftedEvent(player, stack, this.craftSlots));
    }
}
