package com.merlinkitsune.astral_dice.mixin.trade;

import com.merlinkitsune.astral_dice.trade.EmeraldDiceTrade;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 在成交(onTake)时开启绿宝石骰子交易上下文。 */
@Mixin(MerchantResultSlot.class)
public abstract class MerchantResultSlotMixin {

    @Inject(method = "onTake", at = @At("HEAD"))
    private void astralDice$beginSwap(Player player, ItemStack stack, CallbackInfo ci) {
        EmeraldDiceTrade.begin(player);
    }

    @Inject(method = "onTake", at = @At("RETURN"))
    private void astralDice$endSwap(Player player, ItemStack stack, CallbackInfo ci) {
        EmeraldDiceTrade.end();
    }
}
