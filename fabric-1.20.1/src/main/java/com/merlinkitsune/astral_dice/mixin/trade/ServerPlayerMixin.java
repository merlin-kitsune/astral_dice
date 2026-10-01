package com.merlinkitsune.astral_dice.mixin.trade;

import com.merlinkitsune.astral_dice.trade.EmeraldDiceTrade;
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在向客户端发送村民报价时,把「费用已是星币」的报价副本交给数据包,使客户端显示星币费用。
 *
 * <p>1.20.1 的数据包直接持有本体报价列表(不复制),而网络编码发生在 Netty 事件循环上 ——
 * 那时交换上下文已关闭,只靠 {@code MerchantOfferMixin} 的取值覆写无法影响载荷。
 * 因此这里直接构造并发送替换过费用的数据包(本体报价不变),再取消原版发送。
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {

    @Inject(method = "sendMerchantOffers", at = @At("HEAD"), cancellable = true)
    private void astralDice$sendTransformedOffers(int containerId, MerchantOffers offers, int level, int xp,
                                                  boolean showProgress, boolean canRestock, CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (!EmeraldDiceTrade.hasEmeraldDice(self)) return;
        MerchantOffers transformed = EmeraldDiceTrade.transformOffers(offers);
        if (transformed == null) return;
        self.connection.send(new ClientboundMerchantOffersPacket(
                containerId, transformed, level, xp, showProgress, canRestock));
        ci.cancel();
    }
}
