package com.merlinkitsune.astral_dice.mixin.trade;

import com.merlinkitsune.astral_dice.trade.EmeraldDiceTrade;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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

    /**
     * 报价索引过滤：`tryMoveItems(选中索引)` 会用**本方法返回的列表**去索引报价，
     * 若不在这里一并过滤，佩戴绿宝石骰子的玩家在「列表少了一条」的客户端上会点 A 成交 B。
     *
     * <p>返回 null 表示无需过滤（保持原行为）。
     */
    @Inject(method = "getOffers", at = @At("HEAD"), cancellable = true)
    private void astralDice$filterEmeraldExchange(CallbackInfoReturnable<MerchantOffers> cir) {
        MerchantOffers filtered = EmeraldDiceTrade.filteredFor(this.trader);
        if (filtered != null) {
            cir.setReturnValue(filtered);
        }
    }
}
