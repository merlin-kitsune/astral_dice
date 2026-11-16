package com.merlinkitsune.astral_dice.mixin.trade;

import com.merlinkitsune.astral_dice.trade.EmeraldDiceTrade;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 成交(onTake)期间开启绿宝石骰子交易上下文,并在成交后立即重发报价刷新客户端经验/等级。 */
@Mixin(MerchantResultSlot.class)
public abstract class MerchantResultSlotMixin {

    @Shadow @Final private Merchant merchant;

    @Inject(method = "onTake", at = @At("HEAD"))
    private void astralDice$beginSwap(Player player, ItemStack stack, CallbackInfo ci) {
        EmeraldDiceTrade.begin(player);
    }

    @Inject(method = "onTake", at = @At("RETURN"))
    private void astralDice$endSwap(Player player, ItemStack stack, CallbackInfo ci) {
        EmeraldDiceTrade.end();
        // 原版成交后不重发报价 → 客户端经验条/等级停在旧值;佩戴绿宝石骰子时补一次重发
        if (player instanceof ServerPlayer serverPlayer) {
            EmeraldDiceTrade.resendOffers(serverPlayer, this.merchant);
        }
    }
}
