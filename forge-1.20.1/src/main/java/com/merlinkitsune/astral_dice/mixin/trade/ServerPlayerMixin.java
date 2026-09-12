package com.merlinkitsune.astral_dice.mixin.trade;

import com.merlinkitsune.astral_dice.trade.EmeraldDiceTrade;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 在向客户端发送村民报价时开启绿宝石骰子交易上下文,使客户端显示星币费用。 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {

    @Inject(method = "sendMerchantOffers", at = @At("HEAD"))
    private void astralDice$beginSwap(int containerId, MerchantOffers offers, int level, int xp,
                                      boolean showProgress, boolean canRestock, CallbackInfo ci) {
        EmeraldDiceTrade.begin((ServerPlayer) (Object) this);
    }

    @Inject(method = "sendMerchantOffers", at = @At("RETURN"))
    private void astralDice$endSwap(int containerId, MerchantOffers offers, int level, int xp,
                                    boolean showProgress, boolean canRestock, CallbackInfo ci) {
        EmeraldDiceTrade.end();
    }
}
