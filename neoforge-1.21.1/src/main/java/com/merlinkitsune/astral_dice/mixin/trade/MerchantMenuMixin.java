package com.merlinkitsune.astral_dice.mixin.trade;

import com.merlinkitsune.astral_dice.trade.EmeraldDiceTrade;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.Merchant;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 在自动放入交易物品时开启绿宝石骰子交易上下文(星币替代)。 */
@Mixin(MerchantMenu.class)
public abstract class MerchantMenuMixin {

    @Shadow @Final private Merchant trader;

    @Inject(method = "tryMoveItems", at = @At("HEAD"))
    private void astralDice$beginSwap(int selectedMerchantRecipe, CallbackInfo ci) {
        if (this.trader != null) {
            EmeraldDiceTrade.begin(this.trader.getTradingPlayer());
        }
    }

    @Inject(method = "tryMoveItems", at = @At("RETURN"))
    private void astralDice$endSwap(int selectedMerchantRecipe, CallbackInfo ci) {
        EmeraldDiceTrade.end();
    }
}
