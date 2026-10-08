package com.merlinkitsune.astral_dice.mixin.trade;

import com.merlinkitsune.astral_dice.trade.EmeraldDiceTrade;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 在报价匹配(updateSellItem)时开启绿宝石骰子交易上下文。 */
@Mixin(MerchantContainer.class)
public abstract class MerchantContainerMixin {

    @Shadow @Final private Merchant merchant;

    @Inject(method = "updateSellItem", at = @At("HEAD"))
    private void astralDice$beginSwap(CallbackInfo ci) {
        if (this.merchant != null) {
            EmeraldDiceTrade.begin(this.merchant.getTradingPlayer());
        }
    }

    @Inject(method = "updateSellItem", at = @At("RETURN"))
    private void astralDice$endSwap(CallbackInfo ci) {
        EmeraldDiceTrade.end();
    }

    /** 同理：结果槽的报价匹配（updateSellItem）也用过滤视图。 */
    @Redirect(method = "updateSellItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/npc/Merchant;getOffers()Lnet/minecraft/world/entity/npc/MerchantOffers;"))
    private MerchantOffers astralDice$filterEmeraldExchange(Merchant merchant) {
        MerchantOffers offers = merchant.getOffers();
        MerchantOffers filtered = EmeraldDiceTrade.filteredFor(merchant.getTradingPlayer(), offers);
        return filtered != null ? filtered : offers;
    }
}
